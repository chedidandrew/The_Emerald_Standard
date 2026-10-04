package com.chedidandrew.emeraldstandard.minecraft;

import com.chedidandrew.emeraldstandard.core.VillageArchitecture.BiomeDialect;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/** Versions 13/14/15 freeze the civic Bank, kiosk and savings branch respectively. */
final class ApprovedBankStructures {
    private static final Map<String,Plan> CACHE=new java.util.concurrent.ConcurrentHashMap<>();
    record Plan(Map<BlockPos,BlockState> cells,Set<BlockPos> air,BlockPos entrance,BlockPos walkwayExit,
            int minX,int maxX,int minZ,int maxZ,int height) { }
    static Plan plan(BiomeDialect style,int version) {
        return CACHE.computeIfAbsent(style.id()+":"+version,ignored->load(style,version));
    }
    private static Plan load(BiomeDialect style,int version) {
        String variant=switch(version) { case 13->"standalone_bank";case 14->"bank_kiosk";case 15->"savings_branch";
            default->throw new IllegalArgumentException("Unknown approved Bank version "+version); };
        var data=ApprovedVillageStructures.data("biome_"+style.id()+"_"+variant);
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
}
