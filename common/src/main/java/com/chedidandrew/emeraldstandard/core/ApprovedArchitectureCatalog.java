package com.chedidandrew.emeraldstandard.core;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Release manifest for immutable, approved native structures. No Minecraft or review dependency. */
public final class ApprovedArchitectureCatalog {
    public static final int REVISION=12;
    public static final String RESOURCE="/data/the_emerald_standard/architecture/v12/";
    public record Entry(String id,String role,String dialect,int width,int depth,int height,
            int minimumTier,int beds,String sha256) {
        VillageArchitecture.BlueprintDescriptor descriptor() {
            var scale=minimumTier<=1?VillageArchitecture.BlueprintScale.SMALL
                    :minimumTier==2?VillageArchitecture.BlueprintScale.MEDIUM
                    :minimumTier==3?VillageArchitecture.BlueprintScale.LARGE
                    :VillageArchitecture.BlueprintScale.LANDMARK;
            return new VillageArchitecture.BlueprintDescriptor(VillageProsperityEngine.ProjectType.valueOf(role),
                    id,REVISION,width,depth,height,true,scale,
                    List.of(VillageArchitecture.PALETTE_BALANCED),List.of(VillageArchitecture.DRESSING_PROSPEROUS));
        }
    }
    private static final Map<String,Entry> ENTRIES=load();
    private static Map<String,Entry> load() {
        var result=new LinkedHashMap<String,Entry>();
        try(var stream=ApprovedArchitectureCatalog.class.getResourceAsStream(RESOURCE+"catalog.tsv")) {
            if(stream==null) throw new IllegalStateException("Missing approved architecture release manifest");
            try(var reader=new BufferedReader(new InputStreamReader(stream,StandardCharsets.UTF_8))) {
                for(String line;(line=reader.readLine())!=null;) {
                    if(line.isBlank()||line.startsWith("#")) continue;
                    String[] f=line.split("\t");
                    if(f.length!=9) throw new IllegalStateException("Malformed architecture manifest row");
                    Entry entry=new Entry(f[0],f[1],f[2],Integer.parseInt(f[3]),Integer.parseInt(f[4]),
                            Integer.parseInt(f[5]),Integer.parseInt(f[6]),Integer.parseInt(f[7]),f[8]);
                    if(entry.minimumTier<1||entry.minimumTier>5||entry.beds<0||!entry.sha256.matches("[0-9a-f]{64}")
                            ||result.putIfAbsent(entry.id,entry)!=null) throw new IllegalStateException("Invalid architecture identity "+entry.id);
                }
            }
        } catch(IOException e) { throw new IllegalStateException("Cannot read architecture manifest",e); }
        if(result.size()!=375) throw new IllegalStateException("Incomplete approved architecture release");
        return Collections.unmodifiableMap(result);
    }
    public static Entry entry(String id) { return ENTRIES.get(id); }
    public static List<Entry> entries() { return List.copyOf(ENTRIES.values()); }
    static List<VillageArchitecture.BlueprintDescriptor> descriptors() {
        return ENTRIES.values().stream().filter(e->!e.role.equals("BANK")).map(Entry::descriptor).toList();
    }
    public static boolean available(String id,int tier) {
        var entry=entry(id); return entry==null||entry.minimumTier<=Math.max(1,Math.min(5,tier));
    }
    public static boolean matchesDialect(String id,VillageArchitecture.BiomeDialect dialect) {
        var entry=entry(id); return entry==null||entry.dialect.equals(dialect.id());
    }
}
