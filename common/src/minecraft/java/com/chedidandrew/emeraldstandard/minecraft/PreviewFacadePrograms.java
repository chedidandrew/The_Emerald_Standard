package com.chedidandrew.emeraldstandard.minecraft;

import com.chedidandrew.emeraldstandard.minecraft.BiomeArchitecturePreview.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/** Review-only 360-degree elevations. Only exposed wall skins and outside air may change. */
final class PreviewFacadePrograms {
    record Audit(Map<BlockPos,BlockState> before, Set<BlockPos> skin, Set<BlockPos> added,
            Map<Direction,Integer> faces) { }
    record Composition(int width,int spacing,int phase,int frame,boolean planted) { }
    private static final Map<String,Audit> AUDITS=new HashMap<>();
    private static final Map<String,Set<BlockPos>> LANES=new HashMap<>();

    static Plan apply(Plan source) {
        Map<BlockPos,BlockState> cells=new LinkedHashMap<>(source.cells());
        Set<BlockPos> skin=new HashSet<>(),added=new HashSet<>();
        Map<Direction,Integer> faces=new EnumMap<>(Direction.class);
        Set<BlockPos> lanes=new HashSet<>();
        source.cells().forEach((at,state)-> { if(state.getBlock() instanceof DoorBlock)
            reserve(lanes,at,2); });
        source.access().forEach(at->reserve(lanes,at,1));
        LANES.put(source.sample().id(),lanes);
        Palette palette=new Builder(source.sample()).p;
        // Freeze exposure before decoration: a new bracket must not turn an interior into a facade.
        Set<BlockPos> outside=outside(source,2);
        for(Direction face:Direction.Plane.HORIZONTAL) {
            Composition composition=composition(source.sample(),face);
            Map<Integer,List<BlockPos>> lines=new TreeMap<>();
            for(var entry:source.cells().entrySet()) {
                BlockPos at=entry.getKey();
                if(at.getY()!=2||!wall(entry.getValue(),palette)) continue;
                if(!outside.contains(at.relative(face))||outside.contains(at.relative(face.getOpposite()))) continue;
                if(!wall(source.cells().get(at.below()),palette)||!wall(source.cells().get(at.above()),palette)) continue;
                int line=face.getAxis()==Direction.Axis.X?at.getX():at.getZ();
                lines.computeIfAbsent(line,k->new ArrayList<>()).add(at);
            }
            int count=0;
            for(List<BlockPos> line:lines.values()) {
                line.sort(Comparator.comparingInt(p->along(p,face)));
                List<BlockPos> run=new ArrayList<>();
                for(BlockPos at:line) {
                    if(!run.isEmpty()&&along(at,face)!=along(run.getLast(),face)+1) {
                        count+=elevation(source,cells,run,face,composition,palette,skin,added); run.clear();
                    }
                    run.add(at);
                }
                count+=elevation(source,cells,run,face,composition,palette,skin,added);
            }
            count+=openFrame(source,cells,outside,face,composition,palette,skin,added);
            count+=upperElevation(source,cells,face,composition,palette,skin);
            faces.put(face,count);
        }
        Plan result=new Plan(source.sample(),Map.copyOf(cells),source.access(),source.entrance(),source.height());
        AUDITS.put(source.sample().id(),new Audit(source.cells(),Set.copyOf(skin),Set.copyOf(added),Map.copyOf(faces)));
        validate(result);
        return result;
    }
    static void alias(Plan source,String id) { AUDITS.put(id,AUDITS.get(source.sample().id())); }
    static Audit audit(Plan p) { return AUDITS.get(p.sample().id()); }

    private static Composition composition(Sample s,Direction face) {
        String id=s.id();
        int variant=1;
        if(id.matches(".*_\\d+$")) variant=Integer.parseInt(id.substring(id.lastIndexOf('_')+1));
        if(PreviewCompactBuildings.isCompact(s)) {
            var specs=PreviewCompactBuildings.samples(s.style());
            for(int i=0;i<specs.size();i++) if(specs.get(i).id().equals(id)) variant=i+1;
        }
        int side=switch(face) {case NORTH->0;case EAST->1;case SOUTH->2;default->3;};
        // Homes have grouped daylight bays; stores have narrow protected vents; civic halls
        // have broad framed glazing. Side/rear compositions deliberately differ from arrival.
        int width=switch(s.role()) {case "HOUSE","INN","BANK","EXCHANGE_HALL"->2;default->1;};
        if((variant+side)%3==0) width=1;
        int spacing=width+2+(variant+side)%3;
        int phase=(variant+side*2)%spacing;
        int frame=(variant+side)%4;
        boolean planted=s.role().equals("HOUSE")||s.role().equals("COTTAGE")||s.role().equals("INN");
        return new Composition(width,spacing,phase,frame,planted);
    }
    private static int elevation(Plan source,Map<BlockPos,BlockState> cells,List<BlockPos> run,
            Direction face,Composition c,Palette p,Set<BlockPos> skin,Set<BlockPos> added) {
        if(run.size()<3) return 0;
        int changes=0;
        for(int i=1;i<run.size()-1;i++) {
            BlockPos at=run.get(i);
            if(doorLane(source,at)) continue;
            // Never remove a ladder backing, fixture attachment, existing window or roof curb.
            if(i%c.spacing()==c.phase()) {
                for(int j=0;j<c.width()&&i+j<run.size()-1;j++) {
                    BlockPos window=run.get(i+j);
                    if(doorLane(source,window)||attached(source,window,face)) continue;
                    for(int y=2;y<=3;y++) {
                        BlockPos target=new BlockPos(window.getX(),y,window.getZ());
                        BlockState old=source.cells().get(target);
                        if(old!=null&&Block.isShapeFullBlock(old.getCollisionShape(EmptyBlockGetter.INSTANCE,target))) {
                            edit(cells,target,Blocks.GLASS.defaultBlockState(),skin); changes++;
                        }
                    }
                }
            } else if((i+c.phase())%c.spacing()==c.spacing()-1||run.size()<=5&&i==run.size()/2) {
                for(int y=1;y<=3;y++) {
                    BlockPos target=new BlockPos(at.getX(),y,at.getZ());
                    BlockState old=source.cells().get(target);
                    if(old!=null&&old.getBlock()!=Blocks.GLASS&&!(old.getBlock() instanceof IronBarsBlock)
                            &&Block.isShapeFullBlock(old.getCollisionShape(EmptyBlockGetter.INSTANCE,target))) {
                        edit(cells,target,frame(source.sample(),p,c.frame(),y,face),skin); changes++;
                    }
                }
                // Pilasters and eave brackets introduce real depth, not only a wall recolor.
                BlockPos out=at.relative(face);
                if(inBounds(source,out)&&emptyColumn(cells,out,0,3)&&!doorLane(source,out)) {
                    if(c.frame()%2==0) {
                        for(int y=0;y<=3;y++) {
                            BlockPos target=new BlockPos(out.getX(),y,out.getZ());
                            cells.put(target,frame(source.sample(),p,c.frame(),y,face)); added.add(target);
                        }
                    } else {
                        BlockPos cap=out.above();
                        cells.put(cap,frame(source.sample(),p,c.frame(),3,face)); added.add(cap);
                        cells.put(out,Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING,true)); added.add(out);
                    }
                    changes++;
                }
            }
        }
        // One grounded, deliberately asymmetric garden end, never a planter in a door lane.
        if(c.planted()&&run.size()>=6) {
            BlockPos out=run.get(c.frame()%2==0?1:run.size()-2).relative(face);
            if(inBounds(source,out)&&emptyColumn(cells,out,0,3)&&!doorLane(source,out)) {
                for(int y=0;y<=1;y++) {
                    BlockPos target=new BlockPos(out.getX(),y,out.getZ());
                    cells.put(target,p.trim().defaultBlockState()); added.add(target);
                }
                cells.put(out,plant(source.sample()).defaultBlockState()); added.add(out); changes++;
            }
        }
        return changes;
    }
    private static int openFrame(Plan source,Map<BlockPos,BlockState> cells,Set<BlockPos> outside,
            Direction face,Composition c,Palette p,Set<BlockPos> skin,Set<BlockPos> added) {
        int count=0;
        // Open market arcades, ore hoists and watch platforms need structural articulation,
        // not fake closed-room windows. Dress their existing outer columns instead.
        for(var entry:source.cells().entrySet()) {
            BlockPos at=entry.getKey();
            if(at.getY()!=2||!wall(entry.getValue(),p)
                    ||!outside.contains(at.relative(face))||!outside.contains(at.relative(face.getOpposite()))
                    ||doorLane(source,at)) continue;
            BlockState foot=source.cells().get(at.below()),top=source.cells().get(at.above());
            if(foot==null||top==null||!wall(foot,p)||!wall(top,p)
                    ||!Block.isShapeFullBlock(foot.getCollisionShape(EmptyBlockGetter.INSTANCE,at.below()))
                    ||!Block.isShapeFullBlock(top.getCollisionShape(EmptyBlockGetter.INSTANCE,at.above()))
                    ||!Block.isShapeFullBlock(entry.getValue().getCollisionShape(EmptyBlockGetter.INSTANCE,at))) continue;
            edit(cells,at.below(),p.trim().defaultBlockState(),skin);
            BlockPos out=at.relative(face);
            if(inBounds(source,out)&&emptyColumn(cells,out,0,3)&&!doorLane(source,out)) {
                cells.put(out.above(),frame(source.sample(),p,c.frame(),3,face));added.add(out.above());
                cells.put(out,Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING,true));added.add(out);
            }
            count++;
        }
        return count;
    }
    private static int upperElevation(Plan source,Map<BlockPos,BlockState> cells,Direction face,
            Composition c,Palette p,Set<BlockPos> skin) {
        int count=0;
        var floors=PreviewRoomLayout.layout(source).floors();
        for(int y=4;y<source.height()-1;y++) {
            Set<BlockPos> outside=outside(source,y);
            Map<Integer,List<BlockPos>> lines=new TreeMap<>();
            for(var entry:source.cells().entrySet()) {
                BlockPos at=entry.getKey();BlockState state=entry.getValue();
                if(at.getY()!=y||floors.contains(at)||!wall(state,p)||doorLane(source,at)
                        ||!outside.contains(at.relative(face))||outside.contains(at.relative(face.getOpposite()))
                        ||!full(source.cells(),at)||!full(source.cells(),at.below())||!full(source.cells(),at.above())) continue;
                // Taiga's actual roof courses remain bark-on horizontal spruce logs.
                if(source.sample().style()==com.chedidandrew.emeraldstandard.core.VillageArchitecture.BiomeDialect.TAIGA
                        &&state.is(Blocks.SPRUCE_LOG)) continue;
                int line=face.getAxis()==Direction.Axis.X?at.getX():at.getZ();
                lines.computeIfAbsent(line,k->new ArrayList<>()).add(at);
            }
            for(List<BlockPos> line:lines.values()) {
                line.sort(Comparator.comparingInt(at->along(at,face)));
                int middle=along(line.get(line.size()/2),face);
                for(BlockPos at:line) {
                    int position=along(at,face),distance=Math.abs(position-middle);
                    BlockState inner=source.cells().get(at.relative(face.getOpposite()));
                    // Honest clerestory/attic glazing only where there is space behind it.
                    if((y==5||y==6)&&distance<c.width()&&inner==null&&!attached(source,at,face)) {
                        edit(cells,at,Blocks.GLASS.defaultBlockState(),skin);count++;
                    } else if(y==4||position%c.spacing()==c.phase()||distance==y-4) {
                        BlockState frame=source.sample().style()==com.chedidandrew.emeraldstandard.core.VillageArchitecture.BiomeDialect.DESERT
                                ?frame(source.sample(),p,c.frame(),3,face):p.log().defaultBlockState();
                        if(frame.hasProperty(BlockStateProperties.AXIS)) frame=frame.setValue(BlockStateProperties.AXIS,
                                face.getAxis()==Direction.Axis.X?Direction.Axis.Z:Direction.Axis.X);
                        edit(cells,at,frame,skin);count++;
                    }
                }
            }
        }
        return count;
    }
    private static boolean full(Map<BlockPos,BlockState> cells,BlockPos at) {
        BlockState state=cells.get(at);
        return state!=null&&Block.isShapeFullBlock(state.getCollisionShape(EmptyBlockGetter.INSTANCE,at));
    }
    private static Block plant(Sample s) {
        return switch(s.style()) {
            case DESERT->Blocks.POTTED_CACTUS;case TAIGA->Blocks.POTTED_SPRUCE_SAPLING;
            case SAVANNA->Blocks.POTTED_ACACIA_SAPLING;case SNOWY->Blocks.POTTED_FERN;default->Blocks.POTTED_POPPY;
        };
    }
    private static BlockState frame(Sample s,Palette p,int style,int y,Direction face) {
        Block block=switch(s.style()) {
            case DESERT->y==1?Blocks.CUT_SANDSTONE:style%2==0?Blocks.CHISELED_SANDSTONE:Blocks.SMOOTH_SANDSTONE;
            case TAIGA->style%2==0?Blocks.COBBLESTONE:Blocks.SPRUCE_LOG;
            case SAVANNA->style%2==0?Blocks.ACACIA_LOG:Blocks.STRIPPED_ACACIA_LOG;
            case SNOWY->style%2==0?Blocks.COBBLESTONE:Blocks.SPRUCE_LOG;
            default->style%2==0?Blocks.OAK_LOG:Blocks.COBBLESTONE;
        };
        BlockState state=block.defaultBlockState();
        return y==3&&state.hasProperty(BlockStateProperties.AXIS)
                ?state.setValue(BlockStateProperties.AXIS,face.getAxis()):state;
    }
    private static boolean attached(Plan p,BlockPos at,Direction face) {
        for(int y=at.getY()-1;y<=at.getY()+1;y++) {
            BlockState inner=p.cells().get(new BlockPos(at.getX(),y,at.getZ()).relative(face.getOpposite()));
            if(inner!=null&&(inner.getBlock() instanceof LadderBlock||inner.getBlock() instanceof WallTorchBlock
                    ||inner.getBlock() instanceof LanternBlock||inner.is(Blocks.SMOKER)
                    ||inner.is(Blocks.FURNACE)||inner.is(Blocks.BLAST_FURNACE))) return true;
        }
        return false;
    }
    private static boolean doorLane(Plan p,BlockPos at) {
        return LANES.get(p.sample().id()).contains(new BlockPos(at.getX(),0,at.getZ()));
    }
    private static void reserve(Set<BlockPos> lanes,BlockPos at,int radius) {
        for(int x=-radius;x<=radius;x++) for(int z=-radius;z<=radius;z++)
            if(Math.abs(x)+Math.abs(z)<=radius) lanes.add(new BlockPos(at.getX()+x,0,at.getZ()+z));
    }
    private static void edit(Map<BlockPos,BlockState> cells,BlockPos at,BlockState state,Set<BlockPos> skin) {
        if(!state.equals(cells.put(at,state))) skin.add(at);
    }
    private static boolean emptyColumn(Map<BlockPos,BlockState> cells,BlockPos at,int bottom,int top) {
        for(int y=bottom;y<=top;y++) {
            BlockState state=cells.get(new BlockPos(at.getX(),y,at.getZ()));
            if(state!=null&&!state.isAir()) return false;
        }
        return true;
    }
    private static int along(BlockPos p,Direction face) { return face.getAxis()==Direction.Axis.X?p.getZ():p.getX(); }
    private static boolean inBounds(Plan p,BlockPos at) {
        return at.getX()>=0&&at.getZ()>=0&&at.getX()<p.sample().width()&&at.getZ()<p.sample().depth();
    }
    private static boolean wall(BlockState state,Palette p) {
        if(state==null) return false;
        String id=net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
        return state.is(p.wall())||state.is(p.log())||state.is(p.trim())
                ||state.is(Blocks.COBBLESTONE)||state.is(Blocks.OAK_PLANKS)||state.is(Blocks.SPRUCE_PLANKS)
                ||state.is(Blocks.ACACIA_PLANKS)||state.is(Blocks.GLASS)||state.is(Blocks.GLASS_PANE)
                ||state.is(Blocks.STONE)||state.is(Blocks.BRICKS)||state.is(Blocks.CALCITE)
                ||state.is(Blocks.ANDESITE)||state.is(Blocks.DIORITE)||state.is(Blocks.SMOOTH_STONE)
                ||state.is(Blocks.POLISHED_ANDESITE)||state.is(Blocks.POLISHED_DIORITE)||state.is(Blocks.POLISHED_GRANITE)
                ||id.endsWith("_bricks")||id.endsWith("_terracotta")||id.endsWith("_sandstone")
                ||id.endsWith("_planks")||state.getBlock() instanceof RotatedPillarBlock;
    }
    private static Set<BlockPos> outside(Plan p,int y) {
        Set<BlockPos> seen=new HashSet<>(); ArrayDeque<BlockPos> queue=new ArrayDeque<>();
        BlockPos start=new BlockPos(-1,y,-1); seen.add(start);queue.add(start);
        while(!queue.isEmpty()) {
            BlockPos next=queue.removeFirst();
            for(Direction step:Direction.Plane.HORIZONTAL) {
                BlockPos at=next.relative(step);
                if(at.getX() < -1||at.getZ() < -1||at.getX()>p.sample().width()||at.getZ()>p.sample().depth()) continue;
                BlockState state=p.cells().get(at);
                if((state==null||state.isAir())&&seen.add(at)) queue.add(at);
            }
        }
        return seen;
    }
    static void validate(Plan p) {
        Audit audit=AUDITS.get(p.sample().id());
        if(audit==null) throw new IllegalStateException("Missing facade audit: "+p.sample().id());
        for(var entry:audit.before().entrySet()) {
            if(!audit.skin().contains(entry.getKey())&&!Objects.equals(entry.getValue(),p.cells().get(entry.getKey())))
                throw new IllegalStateException("Facade changed protected interior/roof: "+entry.getKey());
        }
        for(BlockPos at:audit.skin()) {
            BlockState state=p.cells().get(at);
            if(state==null||!Block.isShapeFullBlock(state.getCollisionShape(EmptyBlockGetter.INSTANCE,at)))
                throw new IllegalStateException("Facade opened an unsealed wall: "+at);
        }
        for(BlockPos at:audit.added()) {
            BlockState state=p.cells().get(at);
            if(state==null||state.isAir()) throw new IllegalStateException("Missing facade member: "+at);
            if(state.is(Blocks.LANTERN)) {
                BlockState support=p.cells().get(at.above());
                if(support==null||!support.isFaceSturdy(EmptyBlockGetter.INSTANCE,at.above(),Direction.DOWN))
                    throw new IllegalStateException("Floating facade lantern: "+at);
            }
        }
        for(BlockPos at:p.cells().keySet()) if(!audit.before().containsKey(at)&&!audit.added().contains(at))
            throw new IllegalStateException("Unscoped exterior addition: "+at);
        PreviewDoorwayAudit.validate(p);
    }
    static void report(List<Sample> samples) {
        for(var style:com.chedidandrew.emeraldstandard.core.VillageArchitecture.BiomeDialect.values()) {
            int changed=0,skin=0,added=0,faces=0;
            for(Sample sample:samples) if(sample.style()==style) {
                var audit=audit(BiomeArchitecturePreview.plan(sample));
                if(!audit.skin().isEmpty()||!audit.added().isEmpty()) changed++;
                else System.out.println("FACADE RETAINED "+sample.id()+" (no safely exposed wall run)");
                skin+=audit.skin().size();added+=audit.added().size();
                faces+=audit.faces().values().stream().filter(n->n>0).count();
            }
            if(changed!=75||faces<250) throw new IllegalStateException("Incomplete facade coverage: "+style+" "+changed+" / "+faces);
            System.out.println("FACADE SCOPE "+style+": "+changed+" plans; "+faces+" elevations; "+skin+" wall-skin edits; "+added+" outside details; interiors and closed roof outlines retained");
        }
    }
}
