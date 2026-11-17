package com.chedidandrew.emeraldstandard.minecraft;

import com.chedidandrew.emeraldstandard.core.*;
import java.util.*;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;

public final class VanillaVillageBuildingsSelfTest {
    public static void main(String[] args) throws Exception {
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();
        int count = 0, layers = 0, cells = 0; List<String> failures = new ArrayList<>();
        int upperRooms=0;
        var sealedNativeAttics=new TreeSet<String>();

        for (var entry : VanillaVillageBuildings.manifest()) {
            String path = "/data/minecraft/structure/" + entry.id().substring("minecraft:".length()) + ".nbt";
            try (var stream = net.minecraft.world.level.block.Blocks.class.getResourceAsStream(path)) {
                if (stream == null) throw new AssertionError("Missing default resource " + path);
                byte[] source = stream.readAllBytes();
                var plan = VanillaVillageBuildings.convert(entry, source);
                var expected = nativeLayers(entry, source, plan);
                var actual = new HashMap<net.minecraft.core.BlockPos, net.minecraft.world.level.block.state.BlockState>();
                VanillaVillageBuildings.cells(plan).forEach(c -> actual.put(c.pos(), c.state()));
                if (!expected.equals(actual)) throw new AssertionError("Native layer/block-state mismatch");
                var doors=actual.entrySet().stream().filter(e->e.getValue().getBlock() instanceof net.minecraft.world.level.block.DoorBlock
                        &&e.getValue().getValue(net.minecraft.world.level.block.DoorBlock.HALF)==net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER).map(Map.Entry::getKey).toList();
                int lowestDoor=doors.stream().mapToInt(net.minecraft.core.BlockPos::getY).min().orElse(Integer.MAX_VALUE);
                var reached=new HashSet<net.minecraft.core.BlockPos>();
                for(var door:doors)if(door.getY()==lowestDoor)reached.addAll(PreviewRoomLayout.usableReachable(actual,door));
                for(var room:PreviewBuildingUsability.upperRooms(actual,plan.width(),plan.depth(),plan.height())) {
                    upperRooms++;
                    if(Collections.disjoint(room,reached))sealedNativeAttics.add(entry.id());
                }
                layers += expected.keySet().stream().map(net.minecraft.core.BlockPos::getY).distinct().count();
                cells += expected.size();
                var restored = VanillaConstructionPlan.decode(plan.encode());
                if (!plan.hash().equals(restored.hash()) || !plan.cells().equals(restored.cells()))
                    throw new AssertionError("Saved cells changed");
                if (VanillaVillageBuildings.cells(plan).size() != plan.cells().size())
                    throw new AssertionError("Decoded cells changed");
                var village = new EconomyState.VillageRecord();
                village.architectureDialect = village.naturalVillageStyle = plan.style();
                var project = new EconomyState.VillageProject();
                project.type = switch(plan.role()) {
                    case "residence" -> VillageProsperityEngine.ProjectType.HOUSE;
                    case "food" -> VillageProsperityEngine.ProjectType.GRANARY;
                    case "craft" -> VillageProsperityEngine.ProjectType.SMITHY;
                    default -> VillageProsperityEngine.ProjectType.MARKET_SQUARE;
                };
                project.designSchema = VanillaConstructionPlan.SCHEMA; project.vanillaPlan = plan;
                for (int rotation=0;rotation<4;rotation++) {
                    project.designRotation = rotation;
                    var transformed = (List<?>)ConstructionSupportRecoverySelfTest.invoke("projectTemplate",
                            null,net.minecraft.core.BlockPos.ZERO,village,project);
                    if (transformed.size()!=plan.cells().size()) throw new AssertionError("Rotation lost cells");
                    var rotated = new HashMap<net.minecraft.core.BlockPos, net.minecraft.world.level.block.state.BlockState>();
                    for (var cell:transformed) {
                        int x=(int)ConstructionSupportRecoverySelfTest.field(cell,"dx");
                        int y=(int)ConstructionSupportRecoverySelfTest.field(cell,"dy");
                        int z=(int)ConstructionSupportRecoverySelfTest.field(cell,"dz");
                        rotated.put(new net.minecraft.core.BlockPos(x,y,z),
                                (net.minecraft.world.level.block.state.BlockState)ConstructionSupportRecoverySelfTest.field(cell,"state"));
                        if (x<0 || z<0 || x >= (rotation%2==0?plan.width():plan.depth())
                                || z >= (rotation%2==0?plan.depth():plan.width()))
                            throw new AssertionError("Rotation escaped its reserved bounds");
                    }
                    var rotatedExpected = new HashMap<net.minecraft.core.BlockPos, net.minecraft.world.level.block.state.BlockState>();
                    int turns = rotation;
                    expected.forEach((p,s) -> rotatedExpected.put(rotate(p,plan.width(),plan.depth(),turns),s.rotate(rotation(turns))));
                    if (!rotatedExpected.equals(rotated)) throw new AssertionError("Rotated native layer/block-state mismatch " + rotation);
                }
                count++;
            } catch (Exception | AssertionError e) {
                failures.add(entry.id() + ": " + e);
            }
        }

        System.out.println("Audited vanilla upper rooms: "+upperRooms+"; original native geometry retained");
        var exactExceptions=Set.of("minecraft:village/plains/houses/plains_temple_4","minecraft:village/plains/houses/plains_weaponsmith_1");
        if(!sealedNativeAttics.equals(exactExceptions))throw new AssertionError("Native sealed upper-space audit changed: "+sealedNativeAttics);
        System.out.println("Vanilla-exact sealed upper-space exceptions (not repaired): "+sealedNativeAttics);
        failures.forEach(System.out::println);
        System.out.println("Validated vanilla templates: " + count + "; native layers: " + layers
                + "; exact cells: " + cells + "; four state-aware rotations each; failures: " + failures.size());
        if (!failures.isEmpty()) throw new AssertionError("Vanilla catalog admission failed");
    }

    /** Independent source-NBT oracle, including interior air, crops, doors and exterior details.
     * Only documented survival normalization (air/leaves/buried paths) is permitted. */
    private static Map<net.minecraft.core.BlockPos,net.minecraft.world.level.block.state.BlockState> nativeLayers(
            VanillaVillageBuildings.Entry entry, byte[] bytes, VanillaConstructionPlan plan) throws Exception {
        var root = net.minecraft.nbt.NbtIo.readCompressed(new java.io.ByteArrayInputStream(bytes),net.minecraft.nbt.NbtAccounter.create(4000000L));
        var size = root.getListOrEmpty("size");
        int w=size.getIntOr(0,0), h=size.getIntOr(1,0), d=size.getIntOr(2,0);
        var palette = new ArrayList<net.minecraft.world.level.block.state.BlockState>();
        for (var tag:root.getListOrEmpty("palette")) {
            var p=(net.minecraft.nbt.CompoundTag)tag;
            var props=p.getCompoundOrEmpty("Properties");
            String suffix=props.keySet().stream().sorted().map(k->k+"="+props.getStringOr(k,""))
                    .collect(java.util.stream.Collectors.joining(","));
            palette.add(parse(p.getStringOr("Name","")+(suffix.isEmpty()?"":"["+suffix+"]")));
        }
        var raw = new LinkedHashMap<net.minecraft.core.BlockPos,net.minecraft.world.level.block.state.BlockState>();
        net.minecraft.core.BlockPos entrance=null; net.minecraft.core.Direction facing=null;
        for(var tag:root.getListOrEmpty("blocks")) {
            var b=(net.minecraft.nbt.CompoundTag)tag; var pos=b.getListOrEmpty("pos");
            var p=new net.minecraft.core.BlockPos(pos.getIntOr(0,0),pos.getIntOr(1,0),pos.getIntOr(2,0));
            var s=palette.get(b.getIntOr("state",-1));
            if(s.is(net.minecraft.world.level.block.Blocks.JIGSAW)) {
                var nbt=b.getCompoundOrEmpty("nbt"); String name=nbt.getStringOr("name","");
                boolean front=entry.id().contains("/town_centers/")?name.equals("minecraft:street")
                        :name.equals("minecraft:building_entrance")||name.equals("minecraft:buidling_entrance");
                if(front&&entrance==null) {entrance=p;facing=s.getValue(net.minecraft.world.level.block.JigsawBlock.ORIENTATION).front();}
                s=parse(nbt.getStringOr("final_state","minecraft:air"));
            }
            if(!s.is(net.minecraft.world.level.block.Blocks.STRUCTURE_VOID)&&!s.is(net.minecraft.world.level.block.Blocks.STRUCTURE_BLOCK))raw.put(p,s);
        }
        if(entrance==null)throw new AssertionError("Missing native entrance");
        int turn=switch(facing) {case NORTH->0;case EAST->3;case SOUTH->2;case WEST->1;default->throw new AssertionError("Vertical entrance");};
        int shift=plan.width()/2-rotate(entrance,w,d,turn).getX(), floor=h-plan.height();
        var result = new HashMap<net.minecraft.core.BlockPos,net.minecraft.world.level.block.state.BlockState>();
        for(var e:raw.entrySet()) {
            var s=e.getValue().rotate(rotation(turn)); var above=raw.get(e.getKey().above());
            if(s.is(net.minecraft.world.level.block.Blocks.DIRT_PATH)&&above!=null&&above.isSolid()
                    &&!(above.getBlock() instanceof net.minecraft.world.level.block.FenceGateBlock))s=net.minecraft.world.level.block.Blocks.DIRT.defaultBlockState();
            if(s.isAir())s=net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
            if(s.getBlock() instanceof net.minecraft.world.level.block.LeavesBlock)s=s.setValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT,true);
            result.put(rotate(e.getKey(),w,d,turn).offset(shift,-floor,0),s);
        }
        return result;
    }
    private static net.minecraft.world.level.block.state.BlockState parse(String s) throws Exception {
        return net.minecraft.commands.arguments.blocks.BlockStateParser.parseForBlock(net.minecraft.core.registries.BuiltInRegistries.BLOCK,s,false).blockState();
    }
    private static net.minecraft.world.level.block.Rotation rotation(int turn) {
        return switch(turn) {
            case 1->net.minecraft.world.level.block.Rotation.CLOCKWISE_90;
            case 2->net.minecraft.world.level.block.Rotation.CLOCKWISE_180;
            case 3->net.minecraft.world.level.block.Rotation.COUNTERCLOCKWISE_90;
            default->net.minecraft.world.level.block.Rotation.NONE;
        };
    }
    private static net.minecraft.core.BlockPos rotate(net.minecraft.core.BlockPos p,int w,int d,int turn) {
        return switch(turn) {
            case 1->new net.minecraft.core.BlockPos(d-1-p.getZ(),p.getY(),p.getX());
            case 2->new net.minecraft.core.BlockPos(w-1-p.getX(),p.getY(),d-1-p.getZ());
            case 3->new net.minecraft.core.BlockPos(p.getZ(),p.getY(),w-1-p.getX());
            default->p;
        };
    }
}
