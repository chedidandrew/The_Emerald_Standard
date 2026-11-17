package com.chedidandrew.emeraldstandard.minecraft;

import com.chedidandrew.emeraldstandard.minecraft.BiomeArchitecturePreview.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.block.state.properties.Half;

/** Release-authoring only: a roof-bearing, concealed Bank signal fire, with scoped roof bearing edits. */
final class PreviewBankChimney {
    private static final Map<String,Plan> CACHE=new HashMap<>();
    private static final Map<String,Map<BlockPos,BlockState>> ADDITIONS=new HashMap<>();
    static synchronized Plan apply(Plan p) {
        if(!p.sample().role().equals("BANK")) return p;
        var added=additions(p);
        if(!added.isEmpty()&&added.entrySet().stream().allMatch(e->e.getValue().equals(p.cells().get(e.getKey()))))
            return p; // Includes a finished standalone Bank's catalog alias, not an unmodified input.
        return CACHE.computeIfAbsent(p.sample().id(),ignored->build(p));
    }
    static Map<BlockPos,BlockState> additions(Plan p) {return ADDITIONS.getOrDefault(p.sample().id(),Map.of());}
    static void alias(Plan p,String id) {ADDITIONS.put(id,additions(p));}
    static boolean bearingEdit(Plan p,BlockPos at,BlockState before) {
        BlockState added=additions(p).get(at);
        return added!=null&&added.equals(p.cells().get(at))&&(before.is(Blocks.SNOW)
                ||before.getBlock() instanceof SlabBlock&&before.getValue(SlabBlock.TYPE)==SlabType.BOTTOM
                &&added.equals(before.setValue(SlabBlock.TYPE,SlabType.DOUBLE).setValue(SlabBlock.WATERLOGGED,false)));
    }
    private static Plan build(Plan p) {
        Map<Long,BlockPos> tops=new HashMap<>();
        p.cells().forEach((at,state)->{if(!state.is(Blocks.SNOW)) tops.merge(BlockPos.asLong(at.getX(),0,at.getZ()),at,
                (a,b)->a.getY()>b.getY()?a:b);});
        List<BlockPos> candidates=new ArrayList<>();
        for(int x=1;x<p.sample().width()-1;x++) for(int z=p.entrance().getZ()+3;z<p.sample().depth()-1;z++) {
            int high=-1,low=Integer.MAX_VALUE;boolean safe=true;
            for(int dx=-1;dx<=1;dx++) for(int dz=-1;dz<=1;dz++) {
                BlockPos top=tops.get(BlockPos.asLong(x+dx,0,z+dz));
                if(top==null||top.getY()<4||!bearing(p.cells().get(top),top)
                        ||p.access().stream().anyMatch(at->at.getX()==top.getX()&&at.getZ()==top.getZ()&&at.getY()>top.getY())) {safe=false;break;}
                high=Math.max(high,top.getY());low=Math.min(low,top.getY());
            }
            if(safe&&high-low<=2) candidates.add(new BlockPos(x,high+1,z));
        }
        // Back half of the real roof, away from the arrival and the main ridge: never the yard.
        boolean snowyMargin=p.sample().id().equals("snowy_village_bank");
        int rear=p.sample().depth()-(snowyMargin?5:4),roofSide=snowyMargin?(p.sample().width()-2)/3+1:p.sample().width()/3;
        BlockPos base=candidates.stream().min(Comparator.comparingInt((BlockPos at)->
                Math.abs(at.getZ()-rear)*6+Math.abs(at.getX()-roofSide)*3+at.getY()).thenComparingLong(BlockPos::asLong))
                .orElseThrow(()->new IllegalStateException("No safe Bank chimney bearing "+p.sample().id()));
        Block masonry=switch(p.sample().style()) {
            case DESERT->Blocks.CUT_SANDSTONE;
            case SAVANNA->Blocks.BRICKS;
            case TAIGA->Blocks.COBBLESTONE;
            case SNOWY->Blocks.STONE_BRICKS;
            default->Blocks.BRICKS;
        };
        Map<BlockPos,BlockState> added=new LinkedHashMap<>();
        {
            BlockPos top=tops.get(BlockPos.asLong(base.getX(),0,base.getZ()));
            BlockState support=p.cells().get(top);
            // A bottom slab leaves a half-block gap below masonry at the next block coordinate.
            // Only the single shaft bearing is edited; retain the roof's underside.
            if(support.getBlock() instanceof SlabBlock&&support.getValue(SlabBlock.TYPE)==SlabType.BOTTOM)
                added.put(top,support.setValue(SlabBlock.TYPE,SlabType.DOUBLE).setValue(SlabBlock.WATERLOGGED,false));
            for(int y=top.getY()+1;y<=base.getY()+2;y++) added.put(new BlockPos(top.getX(),y,top.getZ()),masonry.defaultBlockState());
        }
        added.put(base.above(3),Blocks.HAY_BLOCK.defaultBlockState());
        added.put(base.above(4),Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT,true)
                .setValue(CampfireBlock.SIGNAL_FIRE,true).setValue(CampfireBlock.WATERLOGGED,false));
        // Solid spruce panels conceal the emitter; perforated regional trapdoors do not.
        Block shroud=Blocks.SPRUCE_TRAPDOOR;
        // Open trapdoors in adjacent cells put their vertical panels against the shaft,
        // not at the outer edge of a three-block tower. Conceal both hay and fire.
        // No horizontal lid: native smoke has a completely open outlet.
        for(Direction side:Direction.Plane.HORIZONTAL) for(int y=3;y<=4;y++)
            added.put(base.above(y).relative(side),shroud.defaultBlockState()
                    .setValue(TrapDoorBlock.FACING,side).setValue(TrapDoorBlock.OPEN,true)
                    .setValue(TrapDoorBlock.HALF,Half.BOTTOM).setValue(TrapDoorBlock.POWERED,false)
                    .setValue(TrapDoorBlock.WATERLOGGED,false));
        if(added.keySet().stream().anyMatch(at->p.cells().containsKey(at)&&!p.cells().get(at).is(Blocks.SNOW)
                &&!(p.cells().get(at).getBlock() instanceof SlabBlock&&p.cells().get(at).getValue(SlabBlock.TYPE)==SlabType.BOTTOM
                &&added.get(at).equals(p.cells().get(at).setValue(SlabBlock.TYPE,SlabType.DOUBLE).setValue(SlabBlock.WATERLOGGED,false)))))
            throw new IllegalStateException("Chimney replaced old geometry");
        Map<BlockPos,BlockState> cells=new LinkedHashMap<>(p.cells());cells.putAll(added);
        ADDITIONS.put(p.sample().id(),Map.copyOf(added));
        return new Plan(p.sample(),Map.copyOf(cells),p.access(),p.entrance(),Math.max(p.height(),base.getY()+5));
    }
    private static boolean bearing(BlockState s,BlockPos at) {
        return s!=null&&(s.getBlock() instanceof StairBlock||s.getBlock() instanceof SlabBlock
                ||s.isFaceSturdy(EmptyBlockGetter.INSTANCE,at,Direction.UP))
                &&!(s.getBlock() instanceof CampfireBlock)&&!s.is(Blocks.SNOW);
    }
}
