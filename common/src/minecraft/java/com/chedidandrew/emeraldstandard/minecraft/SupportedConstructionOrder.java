package com.chedidandrew.emeraldstandard.minecraft;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/** Frozen v1 presentation schedule; changes order, never authored cells, roles or block states. */
final class SupportedConstructionOrder {
    record Cell(BlockPos pos, BlockState state, int phase) { }
    record Sequence(List<Integer> indices, Set<Integer> disconnected) { }
    private SupportedConstructionOrder() { }

    static int phase(Cell cell) {
        var s=cell.state(); int y=cell.pos().getY();
        if (s.isAir()) return 0;
        if (s.getLightEmission()>0 || s.is(Blocks.IRON_CHAIN)) return 6;
        if (s.hasBlockEntity() || s.is(BlockTags.BEDS) || s.getBlock() instanceof FlowerPotBlock) return 5;
        if (y<=0) return 0;
        if (cell.phase()>=0) return cell.phase()==0 ? 1 : cell.phase(); // Upper floors are not ground foundations.
        if (s.is(BlockTags.LOGS)) return 1;
        if (s.is(BlockTags.DOORS) || s.getBlock() instanceof StainedGlassPaneBlock
                || s.is(Blocks.GLASS_PANE) || s.is(Blocks.GLASS)) return 4;
        if (s.getBlock() instanceof StairBlock || s.getBlock() instanceof SlabBlock) return y>2 ? 3 : 2;
        return 2;
    }
    private static boolean bearing(Cell cell) {
        return !cell.state().isAir() && phase(cell)<4;
    }
    static boolean anchor(Cell cell) {
        // Late authored supports still count after installation: a crate can carry its top rail,
        // and a hanging counterweight can connect through a chain. They do not get built early.
        var s=cell.state();
        return bearing(cell) || s.canOcclude() || s.getBlock() instanceof CrossCollisionBlock
                || s.getBlock() instanceof StairBlock || s.getBlock() instanceof SlabBlock || s.is(Blocks.IRON_CHAIN);
    }
    static List<BlockPos> contacts(Cell cell) {
        var neighbors=new ArrayList<BlockPos>();
        for(Direction d:Direction.values()) neighbors.add(cell.pos().relative(d));
        // Sloping stair/slab roof courses meet along an edge rather than sharing a voxel face.
        // Do not extend this allowance to floating floors, beams or arbitrary diagonal blocks.
        if(phase(cell)==3 && (cell.state().getBlock() instanceof StairBlock || cell.state().getBlock() instanceof SlabBlock))
            for(Direction d:Direction.Plane.HORIZONTAL) for(int dy:new int[]{-1,1}) neighbors.add(cell.pos().relative(d).above(dy));
        return neighbors;
    }
    static boolean supportedNow(net.minecraft.server.level.ServerLevel level, BlockPos origin, Cell cell,
            Map<BlockPos,List<Cell>> planned) {
        if(cell.state().isAir() || cell.pos().getY()<=0) return true;
        BlockPos required=attachment(cell);
        if(required!=null) return actualSupport(level,origin,required,planned,false);
        if(!bearing(cell)) return true; // Normal cosmetic/native survival checks remain authoritative.
        for(BlockPos p:contacts(cell)) if(actualSupport(level,origin,p,planned,true)) return true;
        return false;
    }
    private static boolean actualSupport(net.minecraft.server.level.ServerLevel level,BlockPos origin,BlockPos relative,
            Map<BlockPos,List<Cell>> planned,boolean structuralOnly) {
        List<Cell> expected=planned.get(relative);
        BlockPos world=origin.offset(relative);
        if(!level.hasChunk(world.getX()>>4,world.getZ()>>4)) return false;
        if(expected==null) return !structuralOnly && !level.getBlockState(world).getCollisionShape(level,world).isEmpty();
        var actual=level.getBlockState(world);
        return expected.stream().anyMatch(c->!c.state().isAir() && (!structuralOnly || anchor(c))
                && actual.is(c.state().getBlock()));
    }
    static String waitReason(net.minecraft.server.level.ServerLevel level, BlockPos origin, Cell cell,
            Map<BlockPos,List<Cell>> planned) {
        BlockPos attachment=attachment(cell);
        List<BlockPos> candidates=attachment==null ? contacts(cell) : List.of(attachment);
        List<String> missing=new ArrayList<>();
        for(BlockPos relative:candidates) {
            List<Cell> expected=planned.getOrDefault(relative,List.of()).stream()
                    .filter(c->!c.state().isAir() && (attachment!=null || anchor(c))).toList();
            if(expected.isEmpty() && attachment==null) continue;
            BlockPos world=origin.offset(relative);
            String actual=!level.hasChunk(world.getX()>>4,world.getZ()>>4) ? "unloaded"
                    : net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(level.getBlockState(world).getBlock()).toString();
            String wanted=expected.isEmpty() ? "attachment" : expected.stream()
                    .map(c->net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(c.state().getBlock()).toString())
                    .distinct().collect(java.util.stream.Collectors.joining("/"));
            missing.add(wanted+" at "+world.toShortString()+" (now "+actual+")");
            if(missing.size()==3) break;
        }
        return "Support needed for "+net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(cell.state().getBlock())
                +" at "+origin.offset(cell.pos()).toShortString()+": "
                +(missing.isEmpty() ? "no installed planned contact" : String.join("; ",missing));
    }

    static Map<BlockPos,List<Cell>> supportPalette(List<Cell> cells) {
        // An append-only upgrade may replace a support later. Both authored stages are valid
        // while physically present; expecting only the final block can deadlock the earlier stage.
        Map<BlockPos,List<Cell>> result=new HashMap<>();
        for(var cell:cells) result.computeIfAbsent(cell.pos(),p->new ArrayList<>()).add(cell);
        result.replaceAll((p,values)->List.copyOf(values));
        return Map.copyOf(result);
    }
    private static BlockPos attachment(Cell c) {
        var s=c.state(); var p=c.pos();
        if (s.is(Blocks.IRON_CHAIN)) return p.above();
        if (s.getBlock() instanceof BellBlock) return switch(s.getValue(BellBlock.ATTACHMENT)) {
            case CEILING -> p.above();
            case FLOOR -> p.below();
            case SINGLE_WALL, DOUBLE_WALL -> p.relative(s.getValue(BellBlock.FACING).getOpposite());
        };
        if (s.getBlock() instanceof LanternBlock) return s.getValue(BlockStateProperties.HANGING) ? p.above() : p.below();
        if (s.getBlock() instanceof WallTorchBlock) return p.relative(s.getValue(BlockStateProperties.HORIZONTAL_FACING).getOpposite());
        if (s.getBlock() instanceof TorchBlock || s.getBlock() instanceof FlowerPotBlock
                || s.is(BlockTags.BEDS) || s.hasBlockEntity()) return p.below();
        return null;
    }

    static Sequence sequence(List<Cell> cells, List<Integer> cuts) {
        if (!com.chedidandrew.emeraldstandard.core.ConstructionOrderState.valid(cuts,cells.size()))
            throw new IllegalArgumentException("Invalid construction ranges");
        List<Integer> result=new ArrayList<>(cells.size()); Set<Integer> disconnected=new LinkedHashSet<>();
        int start=0;
        if (!cuts.isEmpty()) {
            start=cuts.getFirst(); for(int i=0;i<start;i++) result.add(i);
            for(int part=1;part<cuts.size();part++) {
                int end=cuts.get(part); orderRange(cells,start,end,result,disconnected); start=end;
            }
        }
        for(int i=start;i<cells.size();i++) result.add(i); // Unregistered future suffix remains canonical.
        return new Sequence(List.copyOf(result),Set.copyOf(disconnected));
    }

    private static void orderRange(List<Cell> cells,int from,int to,List<Integer> result,Set<Integer> disconnected) {
        Map<BlockPos,List<Integer>> at=new HashMap<>();
        Set<BlockPos> placed=new HashSet<>(), structural=new HashSet<>();
        for(int i=0;i<to;i++) {
            var c=cells.get(i); at.computeIfAbsent(c.pos(),p->new ArrayList<>()).add(i);
            if(i<from && !c.state().isAir()) { placed.add(c.pos()); if(anchor(c)) structural.add(c.pos()); }
        }
        Comparator<Integer> priority=Comparator.comparingInt((Integer i)->phase(cells.get(i)))
                .thenComparingInt(i->cells.get(i).state().is(Blocks.IRON_CHAIN) ? -cells.get(i).pos().getY() : cells.get(i).pos().getY())
                .thenComparingInt(i->i);
        PriorityQueue<Integer> ready=new PriorityQueue<>(priority);
        boolean[] done=new boolean[to],queued=new boolean[to]; Arrays.fill(done,0,from,true);
        for(int i=from;i<to;i++) offer(cells,i,from,to,at,placed,structural,done,queued,ready);
        int remaining=to-from;
        while(remaining>0) {
            if(ready.isEmpty()) {
                // Some frozen historical decorations have no authored contact. Keep their geometry
                // intact and postpone them; never invent support blocks or stall the entire save.
                Integer next=null;
                for(int i=from;i<to;i++) if(!done[i] && (next==null || priority.compare(i,next)<0)) {
                    boolean earlierPending=false;
                    for(int earlier:at.get(cells.get(i).pos())) {
                        if(earlier>=i) break;
                        if(!done[earlier]) {earlierPending=true;break;}
                    }
                    if(!earlierPending) next=i;
                }
                disconnected.add(next); ready.add(next); queued[next]=true;
            }
            int i=ready.remove(); if(done[i]) continue;
            var c=cells.get(i); done[i]=true; remaining--; result.add(i);
            if(!c.state().isAir()) { placed.add(c.pos()); if(anchor(c)) structural.add(c.pos()); }
            List<BlockPos> affected=new ArrayList<>(); affected.add(c.pos());
            for(Direction d:Direction.values()) affected.add(c.pos().relative(d));
            for(Direction d:Direction.Plane.HORIZONTAL) for(int dy:new int[]{-1,1}) affected.add(c.pos().relative(d).above(dy));
            for(var p:affected) for(int other:at.getOrDefault(p,List.of()))
                offer(cells,other,from,to,at,placed,structural,done,queued,ready);
        }
    }
    private static void offer(List<Cell> cells,int i,int from,int to,Map<BlockPos,List<Integer>> at,
            Set<BlockPos> placed,Set<BlockPos> structural,boolean[] done,boolean[] queued,PriorityQueue<Integer> ready) {
        if(i<from || i>=to || done[i] || queued[i]) return;
        var c=cells.get(i); var p=c.pos();
        for(int earlier:at.get(p)) { if(earlier>=i) break; if(!done[earlier]) return; }
        BlockPos support=attachment(c);
        boolean connected;
        if(support!=null) connected=placed.contains(support) || !at.containsKey(support);
        else if(c.state().isAir() || p.getY()<=0) connected=true;
        else {
            connected=false;
            for(BlockPos neighbor:contacts(c)) if(structural.contains(neighbor)) {connected=true;break;}
            if(!bearing(c) && !at.containsKey(p.below())) connected=true;
        }
        if(connected) { queued[i]=true; ready.add(i); }
    }
}
