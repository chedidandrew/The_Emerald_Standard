package com.chedidandrew.emeraldstandard.minecraft;

import com.chedidandrew.emeraldstandard.core.VillageArchitecture.BiomeDialect;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.EmptyBlockGetter;

/** Release-authoring pass only. Frozen historical assets never run this code. */
final class PreviewPlainsOverhang {
    private static final Map<String,BiomeArchitecturePreview.Plan> CACHE=new HashMap<>();
    private static final Map<String,Map<BlockPos,BlockState>> ADDITIONS=new HashMap<>();
    static synchronized BiomeArchitecturePreview.Plan apply(BiomeArchitecturePreview.Plan p) {
        if(p.sample().style()!=BiomeDialect.PLAINS) return p;
        return CACHE.computeIfAbsent(p.sample().id(),ignored->extend(p));
    }
    static Map<BlockPos,BlockState> additions(BiomeArchitecturePreview.Plan p) {
        return ADDITIONS.getOrDefault(p.sample().id(),Map.of());
    }
    static void alias(BiomeArchitecturePreview.Plan p,String id) {
        ADDITIONS.put(id,additions(p));
    }
    private static BiomeArchitecturePreview.Plan extend(BiomeArchitecturePreview.Plan p) {
        Set<BlockPos> roof=new HashSet<>();
        if(p.sample().id().startsWith("compact_")) {
            int minimum=p.sample().id().startsWith("compact_plains_")
                    &&PreviewCompactBuildings.spec(p.sample()).upper()?9:5;
            // Only the top surface of an explicitly authored roof column, never an upper
            // room wall, ceiling, gable infill, or furniture sharing the oak palette.
            Map<Long,Integer> tops=new HashMap<>();
            p.cells().forEach((at,state)-> {if(at.getY()>=minimum&&roofMaterial(state))
                tops.merge(BlockPos.asLong(at.getX(),0,at.getZ()),at.getY(),Math::max);});
            p.cells().forEach((at,state)-> {if(roofMaterial(state)&&Objects.equals(at.getY(),
                    tops.get(BlockPos.asLong(at.getX(),0,at.getZ())))) roof.add(at);});
        } else {
            String prefix="catalog_plains_";
            var source=p.sample().id().startsWith(prefix)
                    ?PlainsLegacyArchitecturePreview.source(p.sample().role(),p.sample().id().substring(prefix.length()))
                    :PlainsLegacyArchitecturePreview.source(p.sample().role());
            int x=source.cells().keySet().stream().mapToInt(BlockPos::getX).min().orElseThrow();
            int z=source.cells().keySet().stream().mapToInt(BlockPos::getZ).min().orElseThrow();
            source.roofCells().forEach(at->{BlockPos local=at.offset(-x,0,-z);
                if(local.getY()>=4&&roofMaterial(p.cells().get(local))) roof.add(local);});
        }
        Map<BlockPos,BlockState> added=new LinkedHashMap<>(additions(p));
        for(BlockPos at:roof.stream().sorted(Comparator.comparingLong(BlockPos::asLong)).toList()) {
            BlockState state=p.cells().get(at);
            List<Direction> edges=new ArrayList<>();
            for(Direction d:Direction.Plane.HORIZONTAL) {
                // Extend only exposed, flush wall ends. Already-projecting eaves keep their
                // existing one-block reach; never grow another ring on a previously added cell.
                if(p.cells().containsKey(at.relative(d))||!headroom(p,at.relative(d))||!wallBelow(p,roof,at)
                        ||wallBelow(p,roof,at.relative(d))||!course(p,roof,at,d)) continue;
                edges.add(d);added.putIfAbsent(at.relative(d),state);
            }
            for(Direction a:edges) for(Direction b:edges) if(a.getAxis()!=b.getAxis()) {
                BlockPos corner=at.relative(a).relative(b);
                if(!p.cells().containsKey(corner)&&headroom(p,corner)&&!wallBelow(p,roof,corner)) added.putIfAbsent(corner,state);
            }
        }
        Map<BlockPos,BlockState> cells=new LinkedHashMap<>(p.cells());cells.putAll(added);
        // A new eave can shade a previously uncovered market/work tile. Keep the same
        // night-time lighting admission and spacing rules as the reviewed rooms.
        if(!added.isEmpty()) PreviewWindowLighting.brightenRooms(p,cells).forEach(at->added.put(at,cells.get(at)));
        ADDITIONS.put(p.sample().id(),Map.copyOf(added));
        return new BiomeArchitecturePreview.Plan(p.sample(),Map.copyOf(cells),p.access(),p.entrance(),p.height());
    }
    private static boolean roofMaterial(BlockState s) {
        return s!=null&&(s.is(Blocks.OAK_STAIRS)||s.is(Blocks.OAK_SLAB)||s.is(Blocks.OAK_PLANKS));
    }
    private static boolean headroom(BiomeArchitecturePreview.Plan p,BlockPos at) {
        // Include uncovered upper balconies too: adding a canopy must not turn their
        // previously open standing space into a two-block-high covered passage.
        for(int down=1;down<=3;down++) {
            BlockPos floor=at.below(down);BlockState bearing=p.cells().get(floor);
            if(bearing==null||!bearing.isFaceSturdy(EmptyBlockGetter.INSTANCE,floor,Direction.UP)) continue;
            boolean clear=true;
            for(int up=1;up<down;up++) {
                BlockPos pos=floor.above(up);BlockState state=p.cells().get(pos);
                if(state!=null&&!state.getCollisionShape(EmptyBlockGetter.INSTANCE,pos).isEmpty()) {clear=false;break;}
            }
            if(clear) return false;
        }
        return true;
    }
    private static boolean wallBelow(BiomeArchitecturePreview.Plan p,Set<BlockPos> roof,BlockPos at) {
        for(int down=1;down<=3&&at.getY()-down>=1;down++) {
            BlockPos bearing=at.below(down);BlockState s=p.cells().get(bearing);
            if(s!=null&&!roof.contains(bearing)&&(s.is(Blocks.OAK_PLANKS)||s.is(Blocks.OAK_LOG)
                    ||s.is(Blocks.COBBLESTONE)||s.is(Blocks.STONE_BRICKS))) return true;
        }
        return false;
    }
    private static boolean course(BiomeArchitecturePreview.Plan p,Set<BlockPos> roof,BlockPos at,Direction out) {
        BlockState s=p.cells().get(at);
        if(s.getBlock() instanceof StairBlock&&out==s.getValue(StairBlock.FACING)) return false;
        // A real roof course, not a chair, bracket, isolated slab, or chimney cap.
        Direction run=out;
        if(s.getBlock() instanceof StairBlock&&out==s.getValue(StairBlock.FACING).getOpposite())
            run=out.getClockWise();
        int count=1;
        for(Direction d:List.of(run,run.getOpposite())) for(int n=1;n<=2;n++) {
            BlockPos next=at.relative(d,n);
            if(!roof.contains(next)||!s.equals(p.cells().get(next))) break;
            count++;
        }
        return count>=3;
    }
}
