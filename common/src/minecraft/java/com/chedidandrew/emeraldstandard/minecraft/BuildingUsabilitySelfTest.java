package com.chedidandrew.emeraldstandard.minecraft;

import java.util.*;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.core.*;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;

/** Independent audit: do not trust the generator's declared access targets. */
public final class BuildingUsabilitySelfTest {
    public static void main(String[] args) throws Exception {
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();
        var registration=AuthoredVillageStructuresSelfTest.class.getDeclaredMethod("registerExactDeskFixture");
        registration.setAccessible(true);registration.invoke(null);
        System.setProperty(StructureGallery.ENABLE_PROPERTY,"true");
        furnitureFixtures();
        outdoorLampFixtures();
        int rooms=0,missing=0,changed=0,ladderCells=0,bedCells=0,chairCells=0,outdoorLamps=0,lampDesigns=0;
        for(var sample:BiomeArchitectureCatalogPreview.samples()) {
            if(args.length>1&&!sample.id().equals(args[1]))continue;
            var before=BiomeArchitecturePreview.plan(sample);
            var usable=PreviewBuildingUsability.apply(before);
            var plan=PreviewOutdoorLampOrientation.apply(usable);
            outdoorLamps+=PreviewOutdoorLampOrientation.validate(plan.cells());
            if(!plan.cells().equals(usable.cells()))lampDesigns++;
            NativeStructureSupport.validate(plan.cells(),sample.id());
            var repeat=PreviewOutdoorLampOrientation.apply(PreviewBuildingUsability.apply(plan));
            if(!plan.cells().equals(repeat.cells())||!plan.access().equals(repeat.access())) throw new IllegalStateException("Not idempotent: "+sample.id());
            if(!before.cells().equals(plan.cells())) changed++;
            for(var cell:plan.cells().entrySet()) if(!cell.getValue().equals(before.cells().get(cell.getKey()))) {
                if(cell.getValue().getBlock() instanceof LadderBlock) ladderCells++;
                if(cell.getValue().getBlock() instanceof BedBlock) bedCells++;
                if(cell.getValue().getBlock() instanceof StairBlock) chairCells++;
            }
            var reach=PreviewRoomLayout.usableReachable(plan.cells(),plan.entrance());
            // A new light/hatch must not make a former room vanish from the detector and
            // thereby hide a lost route. Audit the original room coordinates as well.
            for(var originalRoom:PreviewBuildingUsability.upperRooms(before.cells(),sample.width(),sample.depth(),before.height()))
                if(Collections.disjoint(originalRoom,reach))throw new IllegalStateException("Original upper room lost access: "+sample.id()+" "+originalRoom);
            for(var room:PreviewBuildingUsability.upperRooms(plan.cells(),sample.width(),sample.depth(),plan.height())) {
                rooms++;
                if(Collections.disjoint(room,reach)) {
                    missing++;
                    System.out.println("INACCESSIBLE "+sample.id()+" y="+room.iterator().next().getY()+" size="+room.size());
                }
            }
        }
        System.out.println("Upper rooms="+rooms+" inaccessible="+missing);
        System.out.println("PASS outward structure-decor lamps="+outdoorLamps+" changed designs="+lampDesigns+"; walkway lighting excluded");
        System.out.println("Changed designs="+changed+" new ladder cells="+ladderCells+" reversed bed cells="+bedCells+" rotated stair cells="+chairCells+"; native support and idempotence passed");
        if(args.length>0&&args[0].equals("strict")&&missing>0) throw new IllegalStateException("Inaccessible upper rooms: "+missing);
        if(args.length>1)return;
        int frozenRooms=0;
        for(var entry:com.chedidandrew.emeraldstandard.core.ApprovedArchitectureCatalog.entries()) for(int fit=0;fit<=3;fit++) {
            var data=ApprovedVillageStructures.data(entry.id(),com.chedidandrew.emeraldstandard.core.ApprovedArchitectureCatalog.REVISION,fit);var cells=data.cells();
            var core=ApprovedVillageStructures.data(entry.id(),com.chedidandrew.emeraldstandard.core.ApprovedArchitectureCatalog.REVISION,3);
            PreviewOutdoorLampOrientation.validate(core.cells());
            // Keep the historical lamp-only v16 -> v17 scope gate independent of later Bank revisions.
            var lampData=ApprovedVillageStructures.data(entry.id(),17,fit);
            var lampCells=lampData.cells();
            var prior=ApprovedVillageStructures.data(entry.id(),16,fit);
            var offset=lampData.entrance().subtract(prior.entrance());var original=new HashMap<BlockPos,BlockState>();
            prior.cells().forEach((at,s)->original.put(at.offset(offset),s));
            var allowed=new HashSet<BlockPos>();
            lampFootprints(original,allowed);lampFootprints(lampCells,allowed);
            var union=new HashSet<>(original.keySet());union.addAll(lampCells.keySet());
            for(var at:union)if(!Objects.equals(original.get(at),lampCells.get(at))&&!allowed.contains(at))
                throw new IllegalStateException("Non-lamp frozen geometry changed: "+entry.id()+" fit="+fit+" "+at);
            int height=cells.keySet().stream().mapToInt(BlockPos::getY).max().orElse(0)+1;
            var reach=PreviewRoomLayout.usableReachable(cells,data.entrance());
            for(var room:PreviewBuildingUsability.upperRooms(cells,data.identity().width(),data.identity().depth(),height)) {
                frozenRooms++;
                if(Collections.disjoint(room,reach))throw new IllegalStateException("Frozen upper room unreachable: "+data.identity().id());
            }
            int awkward=wallFacingLamps(cells);
            if(awkward!=0)throw new IllegalStateException("Wall-facing freestanding lamps: "+data.identity().id()+" count="+awkward);
        }
        System.out.println("PASS frozen full/three outdoor-fit upper rooms="+frozenRooms+"; zero wall-facing freestanding lamp assemblies");
    }
    private static void lampFootprints(Map<BlockPos,BlockState> cells,Set<BlockPos> allowed) {
        for(var lamp:PreviewOutdoorLampOrientation.lamps(cells)) {
            allowed.add(lamp.lantern());allowed.add(lamp.lantern().above());allowed.add(lamp.post().above());
            for(var d:Direction.Plane.HORIZONTAL) for(int n=1;n<=3;n++) {
                var arm=lamp.post().relative(d,n);
                if(arm.equals(lamp.lantern()))for(int i=1;i<=n;i++)allowed.add(lamp.post().relative(d,i).above(2));
            }
        }
    }
    private static void outdoorLampFixtures() {
        for(var outward:Direction.Plane.HORIZONTAL) for(boolean masonry:List.of(false,true)) for(int y:List.of(2,5)) {
            var cells=new LinkedHashMap<BlockPos,BlockState>();var post=new BlockPos(0,y,0);var old=post.relative(outward.getOpposite());
            var wall=post.relative(outward.getOpposite(),4);
            cells.put(wall,Blocks.OAK_PLANKS.defaultBlockState());cells.put(wall.above(),Blocks.OAK_PLANKS.defaultBlockState());
            cells.put(post.below(2),Blocks.STONE.defaultBlockState());cells.put(post.below(),Blocks.STONE_BRICK_WALL.defaultBlockState());
            cells.put(post,(masonry?Blocks.SANDSTONE_WALL:Blocks.OAK_FENCE).defaultBlockState());
            cells.put(post.above(),Blocks.SPRUCE_STAIRS.defaultBlockState().setValue(StairBlock.FACING,outward.getOpposite()));
            cells.put(post.above(2),Blocks.OAK_LOG.defaultBlockState());
            cells.put(old,Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING,true));cells.put(old.above(),Blocks.IRON_CHAIN.defaultBlockState());
            cells.put(old.above(2),Blocks.OAK_SLAB.defaultBlockState()); // Bottom slab is a valid slim arm.
            var sample=new BiomeArchitecturePreview.Sample(com.chedidandrew.emeraldstandard.core.VillageArchitecture.BiomeDialect.PLAINS,"HOUSE","lamp_fixture",12,12);
            var source=new BiomeArchitecturePreview.Plan(sample,Map.copyOf(cells),Set.of(),BlockPos.ZERO,12);
            var corrected=PreviewOutdoorLampOrientation.apply(source);
            if(!corrected.cells().get(post.relative(outward)).is(Blocks.LANTERN)||!corrected.cells().get(wall).equals(cells.get(wall)))throw new IllegalStateException("Lamp did not face away across a facade gap");
            if(!corrected.cells().equals(PreviewOutdoorLampOrientation.apply(corrected).cells()))throw new IllegalStateException("Outward lamps not idempotent");
            cells.put(post.relative(outward),Blocks.BARREL.defaultBlockState());
            var extended=PreviewOutdoorLampOrientation.apply(new BiomeArchitecturePreview.Plan(sample,Map.copyOf(cells),Set.of(),BlockPos.ZERO,12));
            if(!extended.cells().get(post.relative(outward,2)).is(Blocks.LANTERN)||!extended.cells().get(post.relative(outward)).is(Blocks.BARREL))throw new IllegalStateException("Outward arm overwrote obstacle or rotated sideways");
            for(var rotation:Rotation.values()) for(var mirror:List.of(Mirror.NONE,Mirror.FRONT_BACK)) {
                var transformed=new HashMap<BlockPos,BlockState>();
                extended.cells().forEach((at,s)->{var m=mirror==Mirror.NONE?at:new BlockPos(-at.getX(),at.getY(),at.getZ());transformed.put(m.rotate(rotation),s.mirror(mirror).rotate(rotation));});
                if(PreviewOutdoorLampOrientation.validate(transformed)!=1)throw new IllegalStateException("Lamp transform lost assembly");
            }
        }
        System.out.println("PASS outdoor lamp fixtures: four facades, wood/masonry, raised sockets, bottom-slab arms, facade gaps, blocked extension, intact obstacles and all rotations/mirrors");
    }
    private static int wallFacingLamps(Map<BlockPos,BlockState> cells) {
        int count=0;
        for(var e:cells.entrySet()) {
            var at=e.getKey();var s=e.getValue();
            if(!(s.getBlock() instanceof LanternBlock)||!s.getValue(LanternBlock.HANGING))continue;
            for(var towardPost:Direction.Plane.HORIZONTAL) {
                var post=at.relative(towardPost);var p=cells.get(post);var knee=cells.get(post.above());var cap=cells.get(post.above(2));
                if(p==null||!(p.getBlock() instanceof FenceBlock||p.getBlock() instanceof WallBlock)||knee==null||!(knee.getBlock() instanceof StairBlock)
                        ||cap==null||!cap.isFaceSturdy(EmptyBlockGetter.INSTANCE,post.above(2),Direction.UP))continue;
                for(var d:Direction.Plane.HORIZONTAL) if(d!=towardPost) {
                    var wall=cells.get(at.relative(d));var high=cells.get(at.relative(d).above());
                    if(wall!=null&&high!=null&&wall.isFaceSturdy(EmptyBlockGetter.INSTANCE,at.relative(d),Direction.UP)
                            &&high.isFaceSturdy(EmptyBlockGetter.INSTANCE,at.relative(d).above(),Direction.UP)) {count++;break;}
                }
                break;
            }
        }
        return count;
    }
    private static void furnitureFixtures() throws Exception {
        var cells=new LinkedHashMap<BlockPos,BlockState>();var foot=new BlockPos(1,1,1);var head=foot.east();
        var bed=net.minecraft.core.registries.BuiltInRegistries.BLOCK.getValue(net.minecraft.resources.Identifier.parse("minecraft:white_bed")).defaultBlockState().setValue(BedBlock.FACING,Direction.EAST);
        cells.put(foot,bed.setValue(BedBlock.PART,net.minecraft.world.level.block.state.properties.BedPart.FOOT));
        cells.put(head,bed.setValue(BedBlock.PART,net.minecraft.world.level.block.state.properties.BedPart.HEAD));
        cells.put(foot.west(),Blocks.BARREL.defaultBlockState());
        var reverse=PreviewBuildingUsability.class.getDeclaredMethod("reverseBeds",Map.class);reverse.setAccessible(true);reverse.invoke(null,cells);
        if(cells.get(foot).getValue(BedBlock.PART)!=net.minecraft.world.level.block.state.properties.BedPart.HEAD
                ||cells.get(head).getValue(BedBlock.FACING)!=Direction.WEST)throw new IllegalStateException("Bed reversal broke the pair");
        cells.clear();var seat=new BlockPos(0,1,0);cells.put(seat,Blocks.SPRUCE_STAIRS.defaultBlockState().setValue(StairBlock.FACING,Direction.EAST));
        cells.put(seat.east(),Blocks.BARREL.defaultBlockState());cells.put(seat.east().above(),Blocks.LANTERN.defaultBlockState());
        if(PreviewSeatingAudit.corrected(cells,Set.of(seat)).get(seat).getValue(StairBlock.FACING)!=Direction.WEST)throw new IllegalStateException("Nightstand chair faces away from table");
        cells.clear();var lamp=new BlockPos(1,2,0);var post=lamp.east();
        cells.put(lamp,Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING,true));cells.put(lamp.above(),Blocks.IRON_CHAIN.defaultBlockState());
        cells.put(lamp.above(2),Blocks.SPRUCE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE,net.minecraft.world.level.block.state.properties.SlabType.TOP));
        cells.put(post,Blocks.OAK_FENCE.defaultBlockState());cells.put(post.above(),Blocks.SPRUCE_STAIRS.defaultBlockState().setValue(StairBlock.FACING,Direction.WEST));
        cells.put(post.above(2),Blocks.OAK_LOG.defaultBlockState());cells.put(lamp.west(),Blocks.OAK_PLANKS.defaultBlockState());cells.put(lamp.west().above(),Blocks.OAK_PLANKS.defaultBlockState());
        var turn=PreviewBuildingUsability.class.getDeclaredMethod("turnLamps",Map.class,Set.class);turn.setAccessible(true);turn.invoke(null,cells,Set.of());
        if(cells.containsKey(lamp)||!cells.get(post.east()).is(Blocks.LANTERN)||wallFacingLamps(cells)!=0)throw new IllegalStateException("Freestanding lamp not turned outward");
        System.out.println("PASS furniture fixtures: paired bed reversal, barrel-table chair and outward lamp/chain/arm");
        cells.clear();var entrance=new BlockPos(1,1,1);var landing=new BlockPos(2,6,2);
        for(int x=1;x<=3;x++) for(int z=1;z<=3;z++) {
            cells.put(new BlockPos(x,0,z),Blocks.OAK_PLANKS.defaultBlockState());
            cells.put(new BlockPos(x,5,z),Blocks.OAK_PLANKS.defaultBlockState());
            cells.put(new BlockPos(x,8,z),Blocks.OAK_PLANKS.defaultBlockState());
        }
        for(int y=1;y<=4;y++)cells.put(new BlockPos(1,y,2),Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING,Direction.NORTH));
        var hatch=new BlockPos(1,5,2);cells.put(hatch,Blocks.OAK_TRAPDOOR.defaultBlockState().setValue(TrapDoorBlock.FACING,Direction.NORTH));
        if(!PreviewRoomLayout.usableReachable(cells,entrance).contains(landing))throw new IllegalStateException("Wooden ladder hatch falsely blocks access");
        cells.put(hatch,Blocks.IRON_TRAPDOOR.defaultBlockState().setValue(TrapDoorBlock.FACING,Direction.NORTH));
        if(PreviewRoomLayout.usableReachable(cells,entrance).contains(landing))throw new IllegalStateException("Iron hatch falsely grants freely usable access");
        System.out.println("PASS upper access fixtures: openable wooden ladder hatch, iron hatch barrier and two-block headroom");
    }
}
