package com.chedidandrew.emeraldstandard.minecraft;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import com.chedidandrew.emeraldstandard.minecraft.BiomeArchitecturePreview.*;

/** Enlarge review margins only after the existing coordinate-sensitive room composition. */
final class PreviewPlanTranslation {
    static Map<BlockPos,BlockState> cells(Map<BlockPos,BlockState> source,int dx,int dz) {
        var result=new LinkedHashMap<BlockPos,BlockState>();
        source.forEach((at,state)->result.put(at.offset(dx,0,dz),state));return Map.copyOf(result);
    }
    static Set<BlockPos> positions(Set<BlockPos> source,int dx,int dz) {
        var result=new HashSet<BlockPos>();source.forEach(at->result.add(at.offset(dx,0,dz)));return Set.copyOf(result);
    }
    static Plan apply(Plan source,Sample sample,int dx,int dz) {
        PreviewDoorwayAudit.translate(source,dx,dz);
        PreviewRoomLayout.translate(source,dx,dz);
        PreviewFacadePrograms.translate(source,dx,dz);
        return new Plan(sample,cells(source.cells(),dx,dz),positions(source.access(),dx,dz),
                source.entrance().offset(dx,0,dz),source.height());
    }
}
