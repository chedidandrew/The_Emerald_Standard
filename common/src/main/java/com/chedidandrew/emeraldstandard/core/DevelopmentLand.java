package com.chedidandrew.emeraldstandard.core;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;

/** Per-world explicit exclusions and sparse evidence of player construction. No chunk loading. */
public final class DevelopmentLand {
    public record Zone(String name, UUID owner, String dimension, int minX, int minZ, int maxX, int maxZ) {
        public boolean overlaps(int x1, int z1, int x2, int z2) {
            return minX <= x2 && maxX >= x1 && minZ <= z2 && maxZ >= z1;
        }
    }
    private final DurableJournal journal;
    private final Map<String, Zone> zones = new LinkedHashMap<>();
    private final Map<String, Map<Long, Map<Long, String>>> placed = new HashMap<>();
    private int operations;
    public DevelopmentLand(Path data) throws IOException {
        journal = new DurableJournal(data.resolve("emerald-development-land.journal"));
        for (byte[] entry : journal.read()) apply(entry);
    }
    public List<Zone> zones() { return List.copyOf(zones.values()); }
    public boolean excluded(String dimension, int x1, int z1, int x2, int z2) {
        return zones.values().stream().anyMatch(z -> z.dimension.equals(dimension) && z.overlaps(x1,z1,x2,z2));
    }
    public void add(String name, UUID owner, String dimension, int x1, int z1, int x2, int z2) throws IOException {
        if (!name.matches("[a-zA-Z0-9_-]{1,48}")) throw new IllegalArgumentException("Use 1-48 letters, numbers, underscores or hyphens");
        if (zones.containsKey(name)) throw new IllegalArgumentException("That zone name already exists");
        if (Math.max(Math.max(Math.abs((long)x1),Math.abs((long)x2)), Math.max(Math.abs((long)z1),Math.abs((long)z2))) > 30_000_000)
            throw new IllegalArgumentException("Corners must be inside Minecraft's coordinate limit");
        commit("Z\t"+name+"\t"+owner+"\t"+dimension+"\t"+Math.min(x1,x2)+"\t"+Math.min(z1,z2)+"\t"+Math.max(x1,x2)+"\t"+Math.max(z1,z2));
    }
    public void remove(String name, UUID actor, boolean administrator) throws IOException {
        Zone zone = zones.get(name);
        if (zone == null) throw new IllegalArgumentException("No zone named " + name);
        if (!administrator && !zone.owner.equals(actor)) throw new IllegalArgumentException("Only the zone owner or an operator can remove it");
        commit("D\t"+name);
    }
    public void observe(String dimension, long position, String state) throws IOException {
        Map<Long,String> cells = chunk(dimension, position, false);
        if (Objects.equals(cells == null ? null : cells.get(position), state.isEmpty() ? null : state)) return;
        commit("P\t"+dimension+"\t"+position+"\t"+state);
    }
    public Map<Long,String> nearby(String dimension, int x, int z, int radius) {
        Map<Long,String> result = new HashMap<>(); var chunks = placed.get(dimension);
        if (chunks == null) return result;
        for (int cx = (x-radius)>>4; cx <= (x+radius)>>4; cx++)
            for (int cz = (z-radius)>>4; cz <= (z+radius)>>4; cz++)
                result.putAll(chunks.getOrDefault(chunkKey(cx,cz), Map.of()));
        return result;
    }
    private void commit(String line) throws IOException {
        byte[] bytes = line.getBytes(StandardCharsets.UTF_8); journal.append(bytes); apply(bytes);
        if(++operations%8192==0) compact();
    }
    private void compact() {
        List<byte[]> live=new ArrayList<>();
        zones.values().forEach(z->live.add(("Z\t"+z.name+"\t"+z.owner+"\t"+z.dimension+"\t"+z.minX+"\t"+z.minZ+"\t"+z.maxX+"\t"+z.maxZ).getBytes(StandardCharsets.UTF_8)));
        placed.forEach((dimension,chunks)->chunks.values().forEach(cells->cells.forEach((pos,state)->
                live.add(("P\t"+dimension+"\t"+pos+"\t"+state).getBytes(StandardCharsets.UTF_8)))));
        try { journal.replace(live); } catch(IOException ignored) { /* The original committed journal remains authoritative. */ }
    }
    private void apply(byte[] bytes) throws IOException {
        try {
            String[] p = new String(bytes, StandardCharsets.UTF_8).split("\t", -1);
            switch (p[0]) {
                case "Z" -> zones.put(p[1],new Zone(p[1],UUID.fromString(p[2]),p[3],Integer.parseInt(p[4]),Integer.parseInt(p[5]),Integer.parseInt(p[6]),Integer.parseInt(p[7])));
                case "D" -> zones.remove(p[1]);
                case "P" -> { long pos = Long.parseLong(p[2]); var cells = chunk(p[1],pos,true);
                    if (p[3].isEmpty()) cells.remove(pos); else cells.put(pos,p[3]); }
                default -> throw new IOException("Unknown development land record");
            }
        } catch (RuntimeException invalid) { throw new IOException("Invalid development land record",invalid); }
    }
    private Map<Long,String> chunk(String dimension, long pos, boolean create) {
        var chunks = create ? placed.computeIfAbsent(dimension,k->new HashMap<>()) : placed.get(dimension);
        if (chunks == null) return null;
        long key = chunkKey((int)(pos >> 38) >> 4, (int)(pos << 26 >> 38) >> 4);
        return create ? chunks.computeIfAbsent(key,k->new HashMap<>()) : chunks.get(key);
    }
    private static long chunkKey(int x,int z) { return ((long)x<<32) | (z & 0xffffffffL); }
}
