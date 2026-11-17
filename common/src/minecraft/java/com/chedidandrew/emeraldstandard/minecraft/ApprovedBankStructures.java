package com.chedidandrew.emeraldstandard.minecraft;

import com.chedidandrew.emeraldstandard.core.VillageArchitecture.BiomeDialect;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/** Archived Banks retain their geometry; current approvals use independently audited frozen assets. */
final class ApprovedBankStructures {
    private static final Map<String,Plan> CACHE=new java.util.concurrent.ConcurrentHashMap<>();
    record Plan(Map<BlockPos,BlockState> cells,Set<BlockPos> air,BlockPos entrance,BlockPos walkwayExit,
            int minX,int maxX,int minZ,int maxZ,int height) { }
    static Plan plan(BiomeDialect style,int version) {
        return CACHE.computeIfAbsent(style.id()+":"+version,ignored->load(style,version));
    }
    static boolean approvedVersion(int version) {
        return identity(version)!=null;
    }
    static int currentVersion(int variant,int level) {
        return versionForRevision(com.chedidandrew.emeraldstandard.core.ApprovedArchitectureCatalog.REVISION,variant,level);
    }
    static int versionForRevision(int revision,int variant,int level) {
        if(revision<12||revision>com.chedidandrew.emeraldstandard.core.ApprovedArchitectureCatalog.REVISION||variant<0||variant>2||level<0||level>3||revision==12&&level!=0)
            throw new IllegalArgumentException("Unknown Bank fit");
        // The historical full-only sequence ends at v30 (revision 17). Continuing it
        // would collide with revision-13 outdoor fits v32/v33. Allocate newer full
        // forms in the unused gap after the previous revision's fit-3 group instead.
        return level==0?(revision<=17?13+(revision-12)*3:260+48*(revision-18))+variant
                :16+48*(revision-13)+16*level+variant;
    }
    private static Plan load(BiomeDialect style,int version) {
        var identity=identity(version);
        if(identity==null) throw new IllegalArgumentException("Unknown approved Bank version "+version);
        var data=ApprovedVillageStructures.data(templateId(style,version),identity[0],identity[2]);
        var source=data.cells();
        BlockPos desk=source.entrySet().stream().filter(e->BankerProfessionSupport.isExchangeDesk(e.getValue()))
                .map(Map.Entry::getKey).findFirst().orElseThrow();
        BlockPos offset=new BlockPos(6,1,8).subtract(desk);
        Map<BlockPos,BlockState> cells=new LinkedHashMap<>();source.forEach((at,state)->cells.put(at.offset(offset),state));
        Set<BlockPos> air=new HashSet<>();data.air().forEach(at->air.add(at.offset(offset)));
        if(cells.containsKey(new BlockPos(6,1,9))||cells.containsKey(new BlockPos(6,2,9)))
            throw new IllegalStateException("Blocked banker standing position");
        return new Plan(Collections.unmodifiableMap(cells),Set.copyOf(air),data.door().offset(offset),data.walkwayExit().offset(offset),
                cells.keySet().stream().mapToInt(BlockPos::getX).min().orElseThrow(),cells.keySet().stream().mapToInt(BlockPos::getX).max().orElseThrow(),
                cells.keySet().stream().mapToInt(BlockPos::getZ).min().orElseThrow(),cells.keySet().stream().mapToInt(BlockPos::getZ).max().orElseThrow(),data.identity().height());
    }
    private static int[] identity(int version) {
        for(int revision=12;revision<=com.chedidandrew.emeraldstandard.core.ApprovedArchitectureCatalog.REVISION;revision++) for(int variant=0;variant<3;variant++)
            for(int level=0;level<=(revision==12?0:3);level++)
                if(versionForRevision(revision,variant,level)==version) return new int[]{revision,variant,level};
        return null;
    }
    static String templateId(BiomeDialect style,int version) {
        var id=identity(version);if(id==null) throw new IllegalArgumentException("Unknown Bank version");
        return "biome_"+style.id()+"_"+List.of("standalone_bank","bank_kiosk","savings_branch").get(id[1]);
    }
}
