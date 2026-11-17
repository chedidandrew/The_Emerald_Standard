package com.chedidandrew.emeraldstandard.minecraft;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import com.chedidandrew.emeraldstandard.core.*;

/** Fresh optional yards only. Buildings stay rigid; connected props never bend column by column. */
final class OutdoorTerrainFitting {
    static long column(int x,int z) {return BlockPos.asLong(x,0,z);}
    static Map<Long,Integer> freeze(ServerLevel level,BlockPos origin,EconomyState.VillageProject p) {
        if(!ApprovedArchitectureCatalog.supportsFits(p.designTemplateRevision)||p.designOutdoorLevel==3)return Map.of();
        var full=ApprovedVillageStructures.data(p.designTemplateId,p.designTemplateRevision,p.designOutdoorLevel);
        var core=ApprovedVillageStructures.data(p.designTemplateId,p.designTemplateRevision,3);
        int cx=full.entrance().getX()-core.entrance().getX(),cz=full.entrance().getZ()-core.entrance().getZ();
        Set<Long> outside=new LinkedHashSet<>(),rigid=new HashSet<>();
        var cells=full.cells();
        for(var e:cells.entrySet()) {
            BlockPos at=e.getKey();
            if(at.getX()>=cx&&at.getX()<cx+core.identity().width()
                    &&at.getZ()>=cz&&at.getZ()<cz+core.identity().depth())continue;
            long col=column(at.getX(),at.getZ());outside.add(col);
            BlockState s=e.getValue();
            if((at.getY()>=0&&!flexible(s))||!s.getFluidState().isEmpty())rigid.add(col);
        }
        if(outside.size()>4096)return null;
        var survey=new VillageSitePreparation.Survey(level);
        Map<Long,Integer> shifts=new TreeMap<>();
        for(long col:outside) {
            BlockPos at=BlockPos.of(col),world=world(origin,at,full.identity().width(),full.identity().depth(),p);
            Integer ground=survey.surface(world.getX(),world.getZ());
            if(ground==null||Math.abs(ground-origin.getY())>4)return null;
            shifts.put(col,ground-origin.getY());
        }
        // A tent, cart, well, log pile or lamp assembly moves as a whole. Terraced paths,
        // individual flowers and fence posts follow the natural column; no rectangular pad.
        Set<Long> seen=new HashSet<>();
        for(long root:rigid) {
            if(!seen.add(root))continue;
            List<Long> group=new ArrayList<>();ArrayDeque<Long> queue=new ArrayDeque<>();queue.add(root);
            while(!queue.isEmpty()) {
                long col=queue.removeFirst();group.add(col);BlockPos at=BlockPos.of(col);
                for(Direction d:Direction.Plane.HORIZONTAL) {
                    long next=column(at.getX()+d.getStepX(),at.getZ()+d.getStepZ());
                    if(rigid.contains(next)&&seen.add(next))queue.add(next);
                }
            }
            int min=group.stream().mapToInt(shifts::get).min().orElse(0),max=group.stream().mapToInt(shifts::get).max().orElse(0);
            if(max-min>1)return null; // Trim optional decor instead of distorting furniture on a cliff.
            int shift=max;group.forEach(col->shifts.put(col,shift));
        }
        for(long col:outside) {
            BlockPos at=BlockPos.of(col);
            for(Direction d:Direction.Plane.HORIZONTAL) {
                long next=column(at.getX()+d.getStepX(),at.getZ()+d.getStepZ());
                if(outside.contains(next)&&Math.abs(shifts.get(col)-shifts.get(next))>1)return null;
            }
        }
        shifts.values().removeIf(shift->shift==0);
        return Map.copyOf(shifts);
    }
    private static boolean flexible(BlockState s) {
        return s.isAir()||VillageSitePreparation.vegetation(s)||s.getBlock() instanceof FenceBlock
                ||s.getBlock() instanceof FenceGateBlock||VillageProsperityManager.isNaturalProjectGround(s)
                ||s.is(Blocks.DIRT_PATH)||s.is(Blocks.FARMLAND);
    }
    private static BlockPos world(BlockPos origin,BlockPos at,int width,int depth,EconomyState.VillageProject p) {
        int x=p.designMirrored?width-1-at.getX():at.getX(),z=at.getZ();
        return origin.offset(switch(Math.floorMod(p.designRotation,4)) {
            case 1->new BlockPos(depth-1-z,0,x);case 2->new BlockPos(width-1-x,0,depth-1-z);
            case 3->new BlockPos(z,0,width-1-x);default->new BlockPos(x,0,z);
        });
    }
}
