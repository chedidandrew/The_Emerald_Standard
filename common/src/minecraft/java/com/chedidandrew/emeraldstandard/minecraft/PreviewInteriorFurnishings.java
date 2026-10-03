package com.chedidandrew.emeraldstandard.minecraft;

import com.chedidandrew.emeraldstandard.minecraft.BiomeArchitecturePreview.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/** Purposeful, additive room dressing for the opt-in review catalog, never production. */
final class PreviewInteriorFurnishings {
    record Edits(Map<BlockPos,BlockState> additions,Set<BlockPos> approaches,int rooms) { }
    private static final Comparator<BlockPos> ORDER=Comparator.comparingInt((BlockPos at)->at.getY())
            .thenComparingInt(BlockPos::getZ).thenComparingInt(BlockPos::getX);

    static Edits apply(Plan source,Map<BlockPos,BlockState> cells) {
        Map<BlockPos,BlockState> additions=new LinkedHashMap<>();
        Set<BlockPos> access=new HashSet<>(source.access()),reserved=new HashSet<>(access),views=new HashSet<>();
        reserved.add(source.entrance());reserved.add(source.reverseView());
        for(BlockPos door:PreviewDoorwayAudit.doors(cells)) {
            Direction face=cells.get(door).getValue(DoorBlock.FACING);
            reserved.add(door);reserved.add(door.relative(face));reserved.add(door.relative(face.getOpposite()));
        }
        cells.forEach((at,state)-> {
            if(state.getBlock() instanceof LadderBlock) {
                reserved.add(at);for(Direction side:Direction.Plane.HORIZONTAL) reserved.add(at.relative(side));
            }
            if(PreviewWindowLighting.glass(state)) for(Direction side:Direction.values())
                for(int distance=1;distance<=2;distance++) views.add(at.relative(side,distance));
        });
        Plan draft=with(source,cells,access);
        List<Set<BlockPos>> rooms=rooms(draft);
        int furnished=0,seed=source.sample().id().hashCode();
        List<Block> palette=workBlocks(source.sample().role());
        for(Set<BlockPos> room:rooms) {
            if(room.size()<9) continue;
            int existing=existingFunctions(cells,room);
            int wanted=Math.max(0,Math.min(6,Math.max(1,(room.size()+17)/24))-existing);
            int placed=0;
            var candidates=room.stream().sorted(Comparator.comparingInt((BlockPos at)->
                    Math.floorMod(seed+at.getX()*17+at.getZ()*31+at.getY()*13,97)).thenComparing(ORDER)).toList();
            for(BlockPos at:candidates) {
                if(placed>=wanted) break;
                if(reserved.contains(at)||!empty(cells,at)||!empty(cells,at.above())||views.contains(at)
                        ||!sturdy(cells,at.below(),Direction.UP)) continue;
                Direction wall=wall(cells,at);
                if(wall==null) continue;
                BlockPos approach=at.relative(wall.getOpposite());
                // Preserve two clear walking columns in front of a wall cabinet. Tiny
                // niches and one/two-wide passages receive no bulky room furniture.
                if(!room.contains(approach)||!room.contains(approach.relative(wall.getOpposite()))
                        ||!empty(cells,approach)||!empty(cells,approach.above())
                        ||additions.keySet().stream().anyMatch(other->other.getY()==at.getY()
                            &&Math.abs(other.getX()-at.getX())+Math.abs(other.getZ()-at.getZ())<3)) continue;
                Block block=palette.get(Math.floorMod(seed+placed+at.getY(),palette.size()));
                BlockState state=block.defaultBlockState();
                if(state.hasProperty(BlockStateProperties.HORIZONTAL_FACING))
                    state=state.setValue(BlockStateProperties.HORIZONTAL_FACING,wall.getOpposite());
                Map<BlockPos,BlockState> proposed=new LinkedHashMap<>();proposed.put(at,state);
                // A planted sideboard or a two-high book/storage cabinet, never a
                // shelf floating above a bed, doorway, ladder or floor opening.
                if(!views.contains(at.above())&&state.isFaceSturdy(EmptyBlockGetter.INSTANCE,at,Direction.UP)
                        &&Math.floorMod(seed+placed,3)==0)
                    proposed.put(at.above(),plant(source.sample()).defaultBlockState());
                else if(!views.contains(at.above())&&block==Blocks.BARREL&&Math.floorMod(seed+placed,3)==1)
                    proposed.put(at.above(),Blocks.BOOKSHELF.defaultBlockState());
                var next=new LinkedHashMap<>(cells);next.putAll(proposed);
                var required=new HashSet<>(access);required.add(approach);
                if(!safe(with(source,next,required))) {
                    // Carried decks can support a cabinet without a vertical pot
                    // bearing column. Keep the useful cabinet, not an unsafe topper.
                    proposed.remove(at.above());next=new LinkedHashMap<>(cells);next.putAll(proposed);
                    if(!safe(with(source,next,required))) continue;
                }
                cells.clear();cells.putAll(next);additions.putAll(proposed);access.add(approach);
                reserved.add(approach);placed++;
            }
            int rugs=rug(source,cells,room,reserved,additions);
            if(placed>0||rugs>0) furnished++;
        }
        return new Edits(Map.copyOf(additions),Set.copyOf(access),furnished);
    }
    static List<Set<BlockPos>> rooms(Plan p) {
        Map<Integer,Set<BlockPos>> exposed=new HashMap<>();
        Set<BlockPos> remaining=new HashSet<>();
        for(BlockPos feet:PreviewRoomLayout.reachable(p.cells(),p.entrance())) {
            BlockState state=p.cells().get(feet);
            if(state!=null&&(state.getBlock() instanceof DoorBlock||state.getBlock() instanceof LadderBlock)
                    ||!sturdy(p.cells(),feet.below(),Direction.UP)||!roof(p,feet)) continue;
            Set<BlockPos> outside=exposed.computeIfAbsent(feet.getY()+1,y->PreviewFacadePrograms.outside(p,y));
            if(!outside.contains(feet.above())) remaining.add(feet);
        }
        List<Set<BlockPos>> result=new ArrayList<>();
        while(!remaining.isEmpty()) {
            BlockPos start=remaining.stream().min(ORDER).orElseThrow();
            Set<BlockPos> room=new HashSet<>();ArrayDeque<BlockPos> queue=new ArrayDeque<>();queue.add(start);
            while(!queue.isEmpty()) {
                BlockPos at=queue.removeFirst();if(!remaining.remove(at)) continue;
                room.add(at);for(Direction side:Direction.Plane.HORIZONTAL) queue.add(at.relative(side));
            }
            result.add(Set.copyOf(room));
        }
        return List.copyOf(result);
    }
    private static boolean roof(Plan p,BlockPos feet) {
        for(int y=feet.getY()+3;y<p.height();y++)
            if(sturdy(p.cells(),feet.atY(y),Direction.DOWN)) return true;
        return false;
    }
    private static int existingFunctions(Map<BlockPos,BlockState> cells,Set<BlockPos> room) {
        Set<BlockPos> found=new HashSet<>();
        for(BlockPos at:room) for(Direction side:Direction.Plane.HORIZONTAL) {
            BlockPos next=at.relative(side);BlockState state=cells.get(next);
            if(state!=null&&functional(state)) found.add(next);
        }
        return found.size();
    }
    private static boolean functional(BlockState state) {
        if(state.getBlock() instanceof BedBlock) return false;
        return state.hasBlockEntity()||state.is(Blocks.CRAFTING_TABLE)||state.is(Blocks.BOOKSHELF)
                ||state.is(Blocks.FLETCHING_TABLE)||state.is(Blocks.CARTOGRAPHY_TABLE)
                ||state.is(Blocks.SMITHING_TABLE)||state.is(Blocks.COMPOSTER)||state.is(Blocks.HAY_BLOCK);
    }
    private static Direction wall(Map<BlockPos,BlockState> cells,BlockPos at) {
        for(Direction side:Direction.Plane.HORIZONTAL) {
            BlockPos back=at.relative(side);BlockState state=cells.get(back);
            if(state!=null&&!PreviewWindowLighting.glass(state)&&!state.hasBlockEntity()
                    &&Block.isShapeFullBlock(state.getCollisionShape(EmptyBlockGetter.INSTANCE,back))
                    &&sturdy(cells,back.above(),side.getOpposite())) return side;
        }
        return null;
    }
    private static int rug(Plan p,Map<BlockPos,BlockState> cells,Set<BlockPos> room,Set<BlockPos> reserved,
            Map<BlockPos,BlockState> additions) {
        if(room.size()<16||room.stream().anyMatch(at->cells.get(at)!=null&&cells.get(at).getBlock() instanceof CarpetBlock)) return 0;
        int sx=room.stream().mapToInt(BlockPos::getX).sum()/room.size();
        int sz=room.stream().mapToInt(BlockPos::getZ).sum()/room.size();
        var centers=room.stream().sorted(Comparator.comparingInt((BlockPos at)->Math.abs(at.getX()-sx)+Math.abs(at.getZ()-sz))
                .thenComparing(ORDER)).toList();
        Block carpet=switch(p.sample().style()) {
            case DESERT->Blocks.CARPET.brown();case SAVANNA->Blocks.CARPET.orange();
            case TAIGA->Blocks.CARPET.green();case SNOWY->Blocks.CARPET.blue();default->Blocks.CARPET.red();
        };
        for(BlockPos center:centers) {
            List<BlockPos> patch=new ArrayList<>();
            for(int x=-1;x<=1;x++) for(int z=0;z<=1;z++) patch.add(center.offset(x,0,z));
            if(patch.stream().anyMatch(at->!room.contains(at)||reserved.contains(at)||!empty(cells,at)
                    ||!sturdy(cells,at.below(),Direction.UP))) continue;
            for(BlockPos at:patch) {BlockState state=carpet.defaultBlockState();cells.put(at,state);additions.put(at,state);}
            return patch.size();
        }
        return 0;
    }
    private static List<Block> workBlocks(String role) {
        return switch(role) {
            case "HOUSE","COTTAGE"->List.of(Blocks.BARREL,Blocks.CRAFTING_TABLE,Blocks.BOOKSHELF,Blocks.SMOKER);
            case "INN"->List.of(Blocks.SMOKER,Blocks.BARREL,Blocks.BOOKSHELF,Blocks.CRAFTING_TABLE);
            case "GRANARY"->List.of(Blocks.BARREL,Blocks.HAY_BLOCK,Blocks.COMPOSTER);
            case "SMITHY"->List.of(Blocks.SMITHING_TABLE,Blocks.BARREL,Blocks.CRAFTING_TABLE,Blocks.FURNACE);
            case "MINE_ENTRANCE"->List.of(Blocks.CARTOGRAPHY_TABLE,Blocks.BARREL,Blocks.FURNACE);
            case "GUARD_POST"->List.of(Blocks.FLETCHING_TABLE,Blocks.BARREL,Blocks.CARTOGRAPHY_TABLE,Blocks.BOOKSHELF);
            case "BANK","EXCHANGE_HALL"->List.of(Blocks.BOOKSHELF,Blocks.CARTOGRAPHY_TABLE,Blocks.BARREL);
            default->List.of(Blocks.BARREL,Blocks.CRAFTING_TABLE,Blocks.BOOKSHELF);
        };
    }
    private static Block plant(Sample s) {
        return switch(s.style()) {
            case DESERT->Blocks.POTTED_CACTUS;case SAVANNA->Blocks.POTTED_ACACIA_SAPLING;
            case TAIGA->Blocks.POTTED_SPRUCE_SAPLING;case SNOWY->Blocks.POTTED_FERN;default->Blocks.POTTED_POPPY;
        };
    }
    private static boolean empty(Map<BlockPos,BlockState> cells,BlockPos at) {return !cells.containsKey(at)||cells.get(at).isAir();}
    private static boolean sturdy(Map<BlockPos,BlockState> cells,BlockPos at,Direction face) {
        return PreviewLadderSupport.sturdy(cells,at,face);
    }
    private static Plan with(Plan p,Map<BlockPos,BlockState> cells,Set<BlockPos> access) {
        return new Plan(p.sample(),Map.copyOf(cells),Set.copyOf(access),p.entrance(),p.height());
    }
    private static boolean safe(Plan p) {
        if(!PreviewRoomLayout.reachable(p.cells(),p.entrance()).containsAll(p.access())) return false;
        try {
            PreviewDoorwayAudit.validate(p);PreviewRoomLayout.validate(p);PreviewWindowLighting.validateViews(p);
            BiomeArchitecturePreview.validateFurnitureSupport(p);
            PreviewSeatingAudit.validate(p.cells(),PreviewSeatingAudit.lowStairs(p.cells()));
        } catch(IllegalStateException error) {return false;}
        return true;
    }
    static void validate(Plan p,Edits edits) {
        if(!p.access().containsAll(edits.approaches()))throw new IllegalStateException("Missing furnishing approaches");
        for(var entry:edits.additions().entrySet()) {
            BlockPos at=entry.getKey();
            if(!entry.getValue().equals(p.cells().get(at))||!sturdy(p.cells(),at.below(),Direction.UP))
                throw new IllegalStateException("Unsupported or missing room furnishing: "+p.sample().id()+" "+at
                        +" expected="+entry.getValue()+" actual="+p.cells().get(at)+" support="+p.cells().get(at.below()));
        }
    }
}
