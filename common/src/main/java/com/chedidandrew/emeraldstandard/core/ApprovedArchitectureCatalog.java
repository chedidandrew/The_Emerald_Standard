package com.chedidandrew.emeraldstandard.core;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Release manifest for immutable, approved native structures. No Minecraft or review dependency. */
public final class ApprovedArchitectureCatalog {
    public static final int REVISION=20;
    public static final String RESOURCE=resource(REVISION);
    public static String resource(int revision) {
        if(revision<12||revision>REVISION) throw new IllegalArgumentException("Unknown approved revision "+revision);
        return "/data/the_emerald_standard/architecture/v"+revision+"/";
    }
    public record Entry(String id,String role,String dialect,int width,int depth,int height,
            int minimumTier,int beds,String sha256) {
        VillageArchitecture.BlueprintDescriptor descriptor(int revision) {
            var scale=minimumTier<=1?VillageArchitecture.BlueprintScale.SMALL
                    :minimumTier==2?VillageArchitecture.BlueprintScale.MEDIUM
                    :minimumTier==3?VillageArchitecture.BlueprintScale.LARGE
                    :VillageArchitecture.BlueprintScale.LANDMARK;
            return new VillageArchitecture.BlueprintDescriptor(VillageProsperityEngine.ProjectType.valueOf(role),
                    id,revision,width,depth,height,true,scale,
                    List.of(VillageArchitecture.PALETTE_BALANCED),List.of(VillageArchitecture.DRESSING_PROSPEROUS));
        }
    }
    private static final Map<Integer,Map<String,Entry>> RELEASES=Map.of(12,load(12),13,load(13),14,load(14),15,load(15),16,load(16),17,load(17),18,load(18),19,load(19),20,load(20));
    private static final Map<String,Entry> ENTRIES=RELEASES.get(REVISION);
    public static final String FIT_RESOURCE = fitResource(REVISION);
    public static boolean supportsFits(int revision) { return revision>=13&&revision<=REVISION; }
    public static String fitResource(int revision) {
        if(!supportsFits(revision)) throw new IllegalArgumentException("Unsupported outdoor fit revision");
        return "/data/the_emerald_standard/architecture/v"+revision+"-fit/";
    }
    private static final Map<Integer,Map<String,Entry>> FITS=new java.util.concurrent.ConcurrentHashMap<>();
    public static Entry fittedEntry(String id,int revision,int level) {
        if(level==0) return entry(id,revision);
        if(!supportsFits(revision)||level<1||level>3) throw new IllegalArgumentException("Unsupported outdoor fit");
        var result=FITS.computeIfAbsent(revision,r->load(fitResource(r),1125)).get(id+"_fit"+level);
        if(result==null) throw new IllegalArgumentException("Missing outdoor fit "+id);
        return result;
    }
    private static Map<String,Entry> load(int revision) { return load(resource(revision),375); }
    private static Map<String,Entry> load(String resource,int expectedCount) {
        var result=new LinkedHashMap<String,Entry>();
        try(var stream=ApprovedArchitectureCatalog.class.getResourceAsStream(resource+"catalog.tsv")) {
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
        if(result.size()!=expectedCount) throw new IllegalStateException("Incomplete approved architecture release");
        return Collections.unmodifiableMap(result);
    }
    public static Entry entry(String id) { return ENTRIES.get(id); }
    public static Entry entry(String id,int revision) {
        var release=RELEASES.get(revision); return release==null?null:release.get(id);
    }
    public static List<Entry> entries() { return List.copyOf(ENTRIES.values()); }
    static List<VillageArchitecture.BlueprintDescriptor> descriptors() {
        return descriptors(REVISION);
    }
    static List<VillageArchitecture.BlueprintDescriptor> descriptors(int revision) {
        return RELEASES.get(revision).values().stream().filter(e->!e.role.equals("BANK"))
                .map(e->e.descriptor(revision)).toList();
    }
    public static boolean available(String id,int tier) {
        var entry=entry(id); return entry==null||entry.minimumTier<=Math.max(1,Math.min(5,tier));
    }
    public static boolean matchesDialect(String id,VillageArchitecture.BiomeDialect dialect) {
        var entry=entry(id); return entry==null||entry.dialect.equals(dialect.id());
    }
}
