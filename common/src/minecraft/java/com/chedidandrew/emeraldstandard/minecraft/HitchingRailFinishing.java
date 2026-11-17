package com.chedidandrew.emeraldstandard.minecraft;

import com.mojang.serialization.Codec;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.saveddata.*;

/** One-shot, additive presentation finish for the historical inn hitch. Frozen plans stay intact. */
final class HitchingRailFinishing {
    record Extension(Map<BlockPos,BlockState> witnesses, Map<BlockPos,BlockState> caps) { }

    /** Exact two-end hitch signature, not a general repair of arbitrary bars or chains. */
    static List<Extension> extensions(Map<BlockPos,BlockState> plan) {
        List<Extension> result=new ArrayList<>();
        for(var cell:plan.entrySet()) {
            BlockPos hanger=cell.getKey();
            if(!cell.getValue().is(Blocks.IRON_BARS)||!is(plan,hanger.below(),Blocks.IRON_CHAIN)) continue;
            for(Direction inward:Direction.Plane.HORIZONTAL) {
                BlockPos post=hanger.relative(inward);
                BlockState fence=plan.get(post),cap=plan.get(post.above());
                if(fence==null||!(fence.getBlock() instanceof FenceBlock)||cap==null
                        ||!(cap.getBlock() instanceof SlabBlock)||cap.getValue(SlabBlock.TYPE)!=SlabType.BOTTOM
                        ||!is(plan,post.below(),fence.getBlock())) continue;
                // Only the positive axial end owns the pair. Handles rotations and mirrored yards.
                Direction along=inward.getAxis()==Direction.Axis.X?Direction.SOUTH:Direction.EAST;
                BlockPos other=hanger.relative(along,2),otherPost=post.relative(along,2);
                if(!is(plan,other,Blocks.IRON_BARS)||!is(plan,other.below(),Blocks.IRON_CHAIN)
                        ||!is(plan,otherPost,fence.getBlock())||!is(plan,otherPost.below(),fence.getBlock())
                        ||!cap.equals(plan.get(otherPost.above()))
                        ||!is(plan,post.relative(along),fence.getBlock())
                        ||!is(plan,post.relative(along).below(),Blocks.IRON_CHAIN)
                        ||plan.containsKey(hanger.above())||plan.containsKey(other.above())) continue;
                Map<BlockPos,BlockState> witnesses=new LinkedHashMap<>();
                for(BlockPos p:List.of(hanger,other,post,otherPost,post.relative(along))) {
                    witnesses.put(p,plan.get(p));witnesses.put(p.below(),plan.get(p.below()));
                }
                witnesses.put(post.above(),cap);witnesses.put(otherPost.above(),cap);
                // Each wooden post must still have its authored ground bearing.
                for(BlockPos p:List.of(post.below(2),otherPost.below(2))) {
                    BlockState ground=plan.get(p);
                    if(ground!=null) witnesses.put(p,ground);
                }
                if(witnesses.size()!=14) continue;
                result.add(new Extension(Map.copyOf(witnesses),Map.of(hanger.above(),cap,other.above(),cap)));
            }
        }
        return List.copyOf(result);
    }

    static void finish(ServerLevel level,UUID village,long project,long origin,Map<BlockPos,BlockState> plan) {
        var extensions=extensions(plan);
        if(extensions.isEmpty()) return;
        var ledger=level.getDataStorage().computeIfAbsent(Receipts.TYPE);
        for(Extension extension:extensions) {
            BlockPos first=extension.caps().keySet().stream().min(Comparator.comparingLong(BlockPos::asLong)).orElseThrow();
            String key=village+"/"+project+"/"+origin+"/"+first.asLong();
            if(ledger.done.contains(key)) continue;
            var all=new HashSet<>(extension.witnesses().keySet());all.addAll(extension.caps().keySet());
            if(all.stream().anyMatch(p->!loaded(level,p))) continue;
            boolean invalid=false;
            for(var witness:extension.witnesses().entrySet()) {
                var current=level.getBlockState(witness.getKey());
                if(!matches(current,witness.getValue())
                        ||DevelopmentLandProtection.recordedPlacement(level,witness.getKey(),current)
                        ||!VillageDevelopmentProtection.mayPlace(level,village,project,witness.getKey(),current,current)) {
                    invalid=true;break;
                }
            }
            for(var cap:extension.caps().entrySet()) {
                BlockPos at=cap.getKey();var current=level.getBlockState(at);
                if(!level.getWorldBorder().isWithinBounds(at)||!level.isInWorldBounds(at)
                        ||!current.isAir()||level.getBlockEntity(at)!=null||!level.getFluidState(at).isEmpty()
                        ||ConstructionOwnership.reserved(level,at)
                        ||!VillageDevelopmentProtection.mayPlace(level,village,project,at,current,cap.getValue())) invalid=true;
            }
            if(!invalid&&extension.caps().entrySet().stream().anyMatch(e->
                    !VillageConstructionOccupancy.mayChange(level,e.getKey(),level.getBlockState(e.getKey()),e.getValue()))) continue;
            // Record before writing. Harvesting these two inexpensive caps never regenerates them.
            ledger.done.add(key);ledger.setDirty();
            if(invalid) continue;
            extension.caps().forEach((at,state)->VillageConstructionOccupancy.setBlock(level,at,state,Block.UPDATE_ALL));
        }
    }
    private static boolean loaded(ServerLevel level,BlockPos at) {
        for(Direction side:Direction.Plane.HORIZONTAL) {
            BlockPos p=at.relative(side);if(!level.hasChunk(p.getX()>>4,p.getZ()>>4)) return false;
        }
        return level.hasChunk(at.getX()>>4,at.getZ()>>4);
    }
    private static boolean matches(BlockState current,BlockState expected) {
        if(current.getBlock() instanceof FenceBlock||current.getBlock() instanceof IronBarsBlock)
            return current.is(expected.getBlock())&&current.getFluidState().equals(expected.getFluidState());
        // Dirt paths may naturally settle under a furnished yard; do not rewrite their ground.
        return current.equals(expected)||expected.is(Blocks.DIRT_PATH)&&current.is(Blocks.DIRT);
    }
    private static boolean is(Map<BlockPos,BlockState> plan,BlockPos at,Block block) {
        return plan.get(at)!=null&&plan.get(at).is(block);
    }
    static final class Receipts extends SavedData {
        final Set<String> done=new HashSet<>();
        static final Codec<Receipts> CODEC=Codec.STRING.listOf().xmap(Receipts::new,r->new ArrayList<>(r.done));
        static final SavedDataType<Receipts> TYPE=new SavedDataType<>(
                Identifier.fromNamespaceAndPath("the_emerald_standard","hitching_rail_finishing"),
                Receipts::new,CODEC,DataFixTypes.LEVEL);
        Receipts() { }
        Receipts(List<String> keys) { done.addAll(keys); }
    }
}
