package com.chedidandrew.emeraldstandard.minecraft;

import com.chedidandrew.emeraldstandard.core.VillageArchitecture.BiomeDialect;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;

/** Release authoring only: extend flush snowy roof courses, never saved world geometry. */
final class PreviewSnowyOverhang {
    private record Input(BiomeArchitecturePreview.Plan plan,boolean relight) { }
    private static final Map<Input,BiomeArchitecturePreview.Plan> CACHE=new HashMap<>();
    private static final Map<BiomeArchitecturePreview.Plan,BiomeArchitecturePreview.Plan> BEFORE=new HashMap<>();
    private static final Map<BiomeArchitecturePreview.Plan,Map<BlockPos,BlockState>> ADDED=new HashMap<>();
    static BiomeArchitecturePreview.Plan before(BiomeArchitecturePreview.Plan p) {return BEFORE.getOrDefault(p,p);}
    static void alias(BiomeArchitecturePreview.Plan source,BiomeArchitecturePreview.Sample sample) {
        if(source.sample().style()!=BiomeDialect.SNOWY)return;
        var view=new BiomeArchitecturePreview.Plan(sample,source.cells(),source.access(),source.entrance(),source.height());
        var original=before(source);
        BEFORE.put(view,new BiomeArchitecturePreview.Plan(sample,original.cells(),original.access(),original.entrance(),original.height()));
        ADDED.put(view,additions(source));
    }
    static Map<BlockPos,BlockState> additions(BiomeArchitecturePreview.Plan p) {
        return ADDED.getOrDefault(p,Map.of());
    }
    static synchronized BiomeArchitecturePreview.Plan apply(BiomeArchitecturePreview.Plan p) {
        return apply(p,true);
    }
    static synchronized BiomeArchitecturePreview.Plan afterUsability(BiomeArchitecturePreview.Plan p) {
        // Usability has already composed its real usable-room lights, including low attics.
        // Do not run the taller review-room light admission on those compact spaces again.
        return apply(p,false);
    }
    private static BiomeArchitecturePreview.Plan apply(BiomeArchitecturePreview.Plan p,boolean relight) {
        if(p.sample().style()!=BiomeDialect.SNOWY)return p;
        if(BEFORE.containsKey(p))return p;
        return CACHE.computeIfAbsent(new Input(p,relight),ignored->extend(p,relight));
    }
    private static BiomeArchitecturePreview.Plan extend(BiomeArchitecturePreview.Plan p,boolean relight) {
        var routes=new HashSet<>(PreviewRoomLayout.usableReachable(p.cells(),p.entrance()));routes.addAll(p.access());
        int minimum=p.sample().id().startsWith("compact_")&&PreviewCompactBuildings.spec(p.sample()).upper()?9:5;
        Map<Long,Integer> tops=new HashMap<>();
        p.cells().forEach((at,state)-> {if(at.getY()>=minimum&&roofMaterial(state))
            tops.merge(BlockPos.asLong(at.getX(),0,at.getZ()),at.getY(),Math::max);});
        Set<BlockPos> roof=new HashSet<>();
        p.cells().forEach((at,state)-> {if(roofMaterial(state)&&Objects.equals(at.getY(),
            tops.get(BlockPos.asLong(at.getX(),0,at.getZ()))))roof.add(at);});
        Map<BlockPos,BlockState> added=new LinkedHashMap<>();
        for(BlockPos at:roof.stream().sorted(Comparator.comparingLong(BlockPos::asLong)).toList()) {
            BlockState state=p.cells().get(at);
            List<Direction> edges=new ArrayList<>();
            for(Direction out:Direction.Plane.HORIZONTAL) {
                BlockPos next=at.relative(out);
                if(p.cells().containsKey(next)||!routeClear(routes,next)||!headroom(p,next)||!wallBelow(p,roof,at)
                        ||wallBelow(p,roof,next)||!course(p,roof,at,out))continue;
                edges.add(out);putRoof(added,next,state);
                cap(p,added,at,next,state);
            }
            for(Direction a:edges)for(Direction b:edges)if(a.getAxis()!=b.getAxis()) {
                BlockPos corner=at.relative(a).relative(b);
                if(!p.cells().containsKey(corner)&&routeClear(routes,corner)&&headroom(p,corner)&&!wallBelow(p,roof,corner)) {
                    putRoof(added,corner,state);cap(p,added,at,corner,state);
                }
            }
        }
        Map<BlockPos,BlockState> cells=new LinkedHashMap<>(p.cells());cells.putAll(added);
        // At joined pitches an upper roof course can occupy a lower course's snow-cap
        // coordinate. Roofing wins; retain only caps supported by the final geometry.
        Set<BlockPos> unsupported=new HashSet<>();
        added.forEach((at,state)-> {
            BlockState bearing=cells.get(at.below());
            if(state.is(Blocks.SNOW)&&(bearing==null||!bearing.isFaceSturdy(EmptyBlockGetter.INSTANCE,at.below(),Direction.UP)))
                unsupported.add(at);
        });
        unsupported.forEach(cells::remove);
        if(relight&&!added.isEmpty())PreviewWindowLighting.brightenRooms(p,cells);
        Map<BlockPos,BlockState> all=new HashMap<>(cells);all.keySet().removeAll(p.cells().keySet());
        var result=new BiomeArchitecturePreview.Plan(p.sample(),Map.copyOf(cells),p.access(),p.entrance(),p.height());
        ADDED.put(result,Map.copyOf(all));BEFORE.put(result,p);return result;
    }
    private static void cap(BiomeArchitecturePreview.Plan p,Map<BlockPos,BlockState> added,
            BlockPos source,BlockPos target,BlockState roof) {
        BlockState snow=p.cells().get(source.above());
        if(snow!=null&&snow.is(Blocks.SNOW)&&!p.cells().containsKey(target.above())
                &&roof.isFaceSturdy(EmptyBlockGetter.INSTANCE,target,Direction.UP))
            added.putIfAbsent(target.above(),snow);
    }
    private static void putRoof(Map<BlockPos,BlockState> added,BlockPos at,BlockState state) {
        added.compute(at,(key,old)->old==null||old.is(Blocks.SNOW)?state:old);
    }
    private static boolean roofMaterial(BlockState state) {
        return state!=null&&(state.is(Blocks.SPRUCE_PLANKS)||state.is(Blocks.SPRUCE_STAIRS)||state.is(Blocks.SPRUCE_SLAB));
    }
    private static boolean wallBelow(BiomeArchitecturePreview.Plan p,Set<BlockPos> roof,BlockPos at) {
        for(int down=1;down<=3&&at.getY()-down>=1;down++) {
            BlockPos pos=at.below(down);BlockState s=p.cells().get(pos);
            if(s!=null&&!roof.contains(pos)&&(s.is(Blocks.SPRUCE_PLANKS)||s.is(Blocks.SPRUCE_LOG)
                    ||s.is(Blocks.STRIPPED_SPRUCE_LOG)||s.is(Blocks.COBBLESTONE)||s.is(Blocks.STONE_BRICKS)))return true;
        }
        return false;
    }
    private static boolean course(BiomeArchitecturePreview.Plan p,Set<BlockPos> roof,BlockPos at,Direction out) {
        BlockState state=p.cells().get(at);Direction run=out;
        if(state.getBlock() instanceof StairBlock) {
            if(out==state.getValue(StairBlock.FACING))return false;
            if(out==state.getValue(StairBlock.FACING).getOpposite())run=out.getClockWise();
        }
        if(length(p,roof,at,state,run)>=3)return true;
        // A shallow snowy gable has two plank columns per step. The long course
        // along its eave still identifies a roof, not a small decorative bracket.
        return !(state.getBlock() instanceof StairBlock)&&length(p,roof,at,state,out.getClockWise())>=3;
    }
    private static int length(BiomeArchitecturePreview.Plan p,Set<BlockPos> roof,BlockPos at,BlockState state,Direction run) {
        int count=1;
        for(Direction d:List.of(run,run.getOpposite()))for(int n=1;n<=2;n++) {
            BlockPos next=at.relative(d,n);
            if(!roof.contains(next)||!state.equals(p.cells().get(next)))break;
            count++;
        }
        return count;
    }
    private static boolean headroom(BiomeArchitecturePreview.Plan p,BlockPos at) {
        for(int down=1;down<=3;down++) {
            BlockPos floor=at.below(down);BlockState bearing=p.cells().get(floor);
            if(bearing==null||!bearing.isFaceSturdy(EmptyBlockGetter.INSTANCE,floor,Direction.UP))continue;
            boolean clear=true;
            for(int up=1;up<down;up++) {
                BlockPos pos=floor.above(up);BlockState state=p.cells().get(pos);
                if(state!=null&&!state.getCollisionShape(EmptyBlockGetter.INSTANCE,pos).isEmpty()) {clear=false;break;}
            }
            if(clear)return false;
        }
        return true;
    }
    private static boolean routeClear(Set<BlockPos> routes,BlockPos at) {
        return !routes.contains(at)&&!routes.contains(at.below())&&!routes.contains(at.below(2));
    }
}
