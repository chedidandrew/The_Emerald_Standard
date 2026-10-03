package com.chedidandrew.emeraldstandard.minecraft;

import com.chedidandrew.emeraldstandard.core.VillageArchitecture.BiomeDialect;
import com.chedidandrew.emeraldstandard.minecraft.BiomeArchitecturePreview.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.*;

/** Room composition and real climbable upper levels for the isolated art-review catalog. */
final class PreviewRoomLayout {
    record Layout(int partitions,int upperLevels,Set<BlockPos> floors,Set<BlockPos> upperTargets) { }
    private static final Map<String,Layout> LAYOUTS=new HashMap<>();
    static Layout layout(Plan p) { return LAYOUTS.getOrDefault(p.sample().id(),new Layout(0,0,Set.of(),Set.of())); }
    static void alias(Plan p,String id) { LAYOUTS.put(id,layout(p)); }
    static boolean floorBearing(Plan p,BlockPos at) {
        return layout(p).floors().contains(at)&&full(p.cells(),at);
    }
    static void validate(Plan p) {
        Layout layout=layout(p);
        if(layout.partitions()>0||layout.upperLevels()>0) {
            Set<BlockPos> reach=reachable(p.cells(),p.entrance());
            if(!reach.containsAll(p.access())||!reach.containsAll(layout.upperTargets()))
                throw new IllegalStateException(p.sample().id()+" inaccessible room or upper level");
        }
        // A floor plate must connect through full structural members to the ground floor.
        // Merely declaring an elevated slab/full-block island as a floor grants no bearing.
        if(layout.floors().isEmpty()) return;
        Set<BlockPos> anchored=new HashSet<>(); ArrayDeque<BlockPos> queue=new ArrayDeque<>();
        p.cells().keySet().stream().filter(at->at.getY()==0).forEach(queue::add);
        while(!queue.isEmpty()) {
            BlockPos at=queue.removeFirst(); BlockState state=p.cells().get(at);
            if(state==null||!Block.isShapeFullBlock(state.getCollisionShape(EmptyBlockGetter.INSTANCE,at))||!anchored.add(at)) continue;
            for(Direction d:Direction.values()) queue.add(at.relative(d));
        }
        if(!anchored.containsAll(layout.floors())) {
            var missing=new HashSet<>(layout.floors()); missing.removeAll(anchored);
            throw new IllegalStateException(p.sample().id()+" unanchored upper-floor plate: "+missing.stream()
                    .limit(8).map(at->at+"="+p.cells().get(at)).toList());
        }
    }
    static Plan apply(Plan source) {
        if(source.sample().id().startsWith("compact_")) return source;
        Map<BlockPos,BlockState> cells=new LinkedHashMap<>(source.cells());
        Set<BlockPos> access=new HashSet<>(source.access());
        Set<BlockPos> floors=new HashSet<>(),upperTargets=new HashSet<>();
        int partitions=0,levels=0;
        if(!source.sample().role().equals("MARKET_SQUARE")) {
            // Back sleeping rooms or work/storage offices: doors and lintels, not knee-high logs.
            List<Integer> bedRows=cells.entrySet().stream().filter(e->e.getValue().getBlock() instanceof BedBlock
                    &&e.getValue().getValue(BedBlock.PART)==BedPart.FOOT&&e.getKey().getY()==1)
                    .map(e->e.getKey().getZ()-2).distinct().sorted(Comparator.reverseOrder()).toList();
            List<Integer> rows=new ArrayList<>(bedRows);
            int back=backWall(source,cells);
            rows.addAll(List.of(back-4,back-6,source.entrance().getZ()+3));
            for(int row=back-3;row>source.entrance().getZ()+2;row--) if(!rows.contains(row)) rows.add(row);
            for(int row:rows) if(row>source.entrance().getZ()+1
                    &&partitions<2&&partition(source,cells,access,row)) partitions++;
            if(addAttic(source,cells,access,floors,upperTargets)) levels++;
        }
        if(partitions>0||levels>0) lightRooms(source,cells);
        LAYOUTS.put(source.sample().id(),new Layout(partitions,levels,Set.copyOf(floors),Set.copyOf(upperTargets)));
        Plan result=new Plan(source.sample(),Map.copyOf(cells),Set.copyOf(access),source.entrance(),source.height());
        PreviewDoorwayAudit.validate(result);
        validate(result);
        return result;
    }
    private static int backWall(Plan p,Map<BlockPos,BlockState> cells) {
        int x=p.entrance().getX();
        return cells.keySet().stream().filter(at->at.getY()==0&&at.getX()==x)
                .mapToInt(BlockPos::getZ).max().orElse(p.sample().depth()-1);
    }
    private static boolean partition(Plan p,Map<BlockPos,BlockState> cells,Set<BlockPos> access,int z) {
        int center=p.entrance().getX(),left=center,right=center;
        while(full(cells,new BlockPos(left-1,0,z))) left--;
        while(full(cells,new BlockPos(right+1,0,z))) right++;
        while(left<center&&!full(cells,new BlockPos(left,1,z))) left++;
        while(right>center&&!full(cells,new BlockPos(right,1,z))) right--;
        if(right-left<5||!full(cells,new BlockPos(left,1,z))||!full(cells,new BlockPos(right,1,z))) return false;
        // Do not carve furniture, beds, existing doors or a service approach to manufacture a room.
        for(int x=left+1;x<right;x++) for(int y=1;y<=2;y++) {
            BlockPos at=new BlockPos(x,y,z); BlockState state=cells.get(at);
            if(access.contains(at)||state!=null&&!(state.getBlock() instanceof CarpetBlock)
                    &&!state.isAir()&&!(state.getBlock() instanceof RotatedPillarBlock)) return false;
        }
        Map<BlockPos,BlockState> draft=new LinkedHashMap<>(cells);
        Block wall=p.sample().style()==BiomeDialect.DESERT?Blocks.SMOOTH_SANDSTONE
                :p.sample().style()==BiomeDialect.SAVANNA?Blocks.ACACIA_PLANKS
                :p.sample().style()==BiomeDialect.PLAINS?Blocks.OAK_PLANKS:Blocks.SPRUCE_PLANKS;
        for(int x=left+1;x<right;x++) for(int y=1;y<=3;y++) draft.put(new BlockPos(x,y,z),wall.defaultBlockState());
        Block door=new BiomeArchitecturePreview.Builder(p.sample()).p.door();
        BlockState lower=door.defaultBlockState().setValue(DoorBlock.FACING,Direction.NORTH);
        draft.put(new BlockPos(center,1,z),lower);
        draft.put(new BlockPos(center,2,z),lower.setValue(DoorBlock.HALF,DoubleBlockHalf.UPPER));
        if(!reachable(draft,p.entrance()).containsAll(access)) return false;
        var test=new Plan(p.sample(),Map.copyOf(draft),Set.copyOf(access),p.entrance(),p.height());
        try { PreviewDoorwayAudit.validate(test); }
        catch(IllegalStateException error) { return false; }
        cells.clear(); cells.putAll(draft); return true;
    }
    private static boolean addAttic(Plan p,Map<BlockPos,BlockState> cells,Set<BlockPos> access,
            Set<BlockPos> floors,Set<BlockPos> targets) {
        int back=backWall(p,cells),z1=back-1,z0=Math.max(p.entrance().getZ()+3,back-5);
        if(z1-z0<2) return false;
        for(int y:new int[]{4,5,6}) for(int x:new int[]{p.entrance().getX(),p.entrance().getX()-1,p.entrance().getX()+1}) {
            BlockPos ladder=new BlockPos(x,1,z1);
            if(!PreviewDoorwayAudit.clear(cells,ladder)||!full(cells,ladder.below())) continue;
            boolean bearing=true;
            for(int yy=1;yy<=y;yy++) bearing&=full(cells,new BlockPos(x,yy,back));
            if(!bearing) continue;
            int left=x,right=x;
            while(full(cells,new BlockPos(left-1,0,z0))) left--;
            while(full(cells,new BlockPos(right+1,0,z0))) right++;
            if(right-left<5) continue;
            boolean space=true;
            for(int z=z0;z<=z1;z++) for(int dx=-1;dx<=1;dx++)
                space&=PreviewDoorwayAudit.clear(cells,new BlockPos(x+dx,y+1,z))
                        &&covered(cells,new BlockPos(x+dx,y+1,z));
            if(!space) continue;
            // A full wall-to-wall floor plate is carried by the existing perimeter, not a
            // floating slab island. The ladder hatch is inside the weather envelope.
            Map<BlockPos,BlockState> draft=new LinkedHashMap<>(cells);
            Set<BlockPos> plate=new HashSet<>();
            Block floor=p.sample().style()==BiomeDialect.DESERT?Blocks.SMOOTH_SANDSTONE
                    :p.sample().style()==BiomeDialect.SAVANNA?Blocks.ACACIA_PLANKS
                    :p.sample().style()==BiomeDialect.PLAINS?Blocks.OAK_PLANKS:Blocks.SPRUCE_PLANKS;
            for(int xx=left;xx<=right;xx++) for(int z=z0;z<=back;z++) {
                BlockPos at=new BlockPos(xx,y,z);
                // Retain the authored roof curb, chimney and perimeter material.
                if(!draft.containsKey(at)||draft.get(at).isAir()) {
                    draft.put(at,floor.defaultBlockState()); plate.add(at);
                }
            }
            BlockState climbing=Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING,Direction.NORTH);
            for(int yy=1;yy<=y;yy++) draft.put(new BlockPos(x,yy,z1),climbing);
            plate.remove(new BlockPos(x,y,z1));
            BlockPos upper=new BlockPos(x,y+1,z0+1);
            BlockPos cabinet=new BlockPos(x-1,y+1,z0);
            draft.put(cabinet,Blocks.BARREL.defaultBlockState());
            draft.put(cabinet.above(),Blocks.LANTERN.defaultBlockState());
            draft.put(new BlockPos(x+1,y+1,z0),Blocks.BOOKSHELF.defaultBlockState());
            Set<BlockPos> required=new HashSet<>(access); required.add(ladder); required.add(upper);
            if(!reachable(draft,p.entrance()).containsAll(required)) continue;
            var test=new Plan(p.sample(),Map.copyOf(draft),Set.copyOf(required),p.entrance(),p.height());
            try { PreviewDoorwayAudit.validate(test); }
            catch(IllegalStateException error) { continue; }
            cells.clear(); cells.putAll(draft); access.addAll(required); floors.addAll(plate); targets.add(upper);
            return true;
        }
        return false;
    }
    private static boolean covered(Map<BlockPos,BlockState> cells,BlockPos feet) {
        for(int y=feet.getY()+2;y<=feet.getY()+12;y++) {
            BlockPos at=new BlockPos(feet.getX(),y,feet.getZ()); BlockState state=cells.get(at);
            if(state!=null&&!(state.getBlock() instanceof LanternBlock)&&!state.is(Blocks.IRON_CHAIN)
                    &&!state.getCollisionShape(EmptyBlockGetter.INSTANCE,at).isEmpty()) return true;
        }
        return false;
    }
    private static void lightRooms(Plan p,Map<BlockPos,BlockState> cells) {
        // Real roof-attached pendants on a regular room grid, never a solid lamp pedestal
        // in a circulation route. Partitions and floor plates change light propagation.
        Set<BlockPos> routes=reachable(cells,p.entrance());
        List<BlockPos> ordered=routes.stream().sorted(Comparator.comparingInt((BlockPos at)->at.getY())
                .thenComparingInt(BlockPos::getZ).thenComparingInt(BlockPos::getX)).toList();
        Set<BlockPos> fixtures=new HashSet<>();
        for(BlockPos feet:ordered) {
            if(fixtures.stream().anyMatch(at->Math.abs(at.getX()-feet.getX())+Math.abs(at.getZ()-feet.getZ())<=4
                    &&Math.abs(at.getY()-(feet.getY()+2))<=1)) continue;
            BlockPos lamp=feet.above(2);
            if(routes.contains(lamp)||routes.contains(lamp.below())) continue;
            if(cells.containsKey(lamp)&&!cells.get(lamp).isAir()) continue;
            int ceiling=-1;
            for(int y=lamp.getY()+1;y<=lamp.getY()+10;y++) {
                BlockPos at=new BlockPos(lamp.getX(),y,lamp.getZ()); BlockState state=cells.get(at);
                if(state!=null&&!state.isAir()) {
                    if(state.isFaceSturdy(EmptyBlockGetter.INSTANCE,at,Direction.DOWN)) ceiling=y;
                    break;
                }
            }
            if(ceiling<0) continue;
            for(int y=lamp.getY()+1;y<ceiling;y++) cells.put(new BlockPos(lamp.getX(),y,lamp.getZ()),Blocks.IRON_CHAIN.defaultBlockState());
            cells.put(lamp,Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING,true)); fixtures.add(lamp);
        }
    }
    static Set<BlockPos> reachable(Map<BlockPos,BlockState> cells,BlockPos start) {
        Set<BlockPos> seen=new HashSet<>(); ArrayDeque<BlockPos> queue=new ArrayDeque<>(); queue.add(start);
        while(!queue.isEmpty()) {
            BlockPos at=queue.removeFirst();
            if(!walkable(cells,at)||!seen.add(at)) continue;
            for(Direction d:Direction.Plane.HORIZONTAL) {
                queue.add(at.relative(d));
            }
            if(ladder(cells.get(at))) { queue.add(at.above()); queue.add(at.below()); }
            else if(ladder(cells.get(at.below()))) queue.add(at.below());
        }
        return Set.copyOf(seen);
    }
    private static boolean walkable(Map<BlockPos,BlockState> cells,BlockPos at) {
        if(at.getY()>1&&!ladder(cells.get(at))&&!covered(cells,at)) return false;
        return (full(cells,at.below())||ladder(cells.get(at))||ladder(cells.get(at.below())))
                &&PreviewDoorwayAudit.clear(cells,at);
    }
    private static boolean ladder(BlockState state) { return state!=null&&state.getBlock() instanceof LadderBlock; }
    private static boolean full(Map<BlockPos,BlockState> cells,BlockPos at) {
        BlockState state=cells.get(at);
        return state!=null&&state.isFaceSturdy(EmptyBlockGetter.INSTANCE,at,Direction.UP);
    }
    static void register(Plan p,int partitions,int upperLevels,Set<BlockPos> floors,Set<BlockPos> targets) {
        LAYOUTS.put(p.sample().id(),new Layout(partitions,upperLevels,Set.copyOf(floors),Set.copyOf(targets)));
    }
}
