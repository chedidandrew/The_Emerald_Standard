package com.chedidandrew.emeraldstandard.minecraft;

import com.chedidandrew.emeraldstandard.core.BankConstruction;
import com.chedidandrew.emeraldstandard.core.EconomyService;
import com.mojang.authlib.GameProfile;
import java.nio.file.Files;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/** Native neighbor rules and real protected construction handover; disposable smoke world only. */
final class ConstructionConnectionsSelfTest {
    static void verify(ServerLevel level) {
        BlockPos origin = new BlockPos(4008, level.getMaxY()-40, 4008);
        var before = new LinkedHashMap<BlockPos,BlockState>();
        ServerPlayer occupant = null;
        String job = null;
        var ownership = ConstructionOwnership.get(level);
        try {
            for (int x=-4;x<=8;x++) for (int z=-4;z<=4;z++) for(int y=-1;y<=3;y++) {
                BlockPos at=origin.offset(x,y,z); level.getChunk(at);
                before.put(at,level.getBlockState(at));
                level.setBlock(at,(y==-1?Blocks.STONE:Blocks.AIR).defaultBlockState(),18);
            }
            List<Block> fences=BuiltInRegistries.BLOCK.stream().filter(b->b instanceof FenceBlock
                    && BuiltInRegistries.BLOCK.getKey(b).getNamespace().equals("minecraft")).toList();
            require(fences.size()>=12,"all native fence families available");
            for(Block fence:fences) for(Direction direction:Direction.Plane.HORIZONTAL) {
                clear(level,origin);
                level.setBlock(origin.relative(direction),fence.defaultBlockState(),18);
                level.setBlock(origin,fence.defaultBlockState(),18);
                require(normalize(level,origin,fence.defaultBlockState()),"fence normalization rejected "+fence);
                BlockState connected=level.getBlockState(origin);
                for(Direction side:Direction.Plane.HORIZONTAL)
                    require(connected.getValue(switch(side) {case NORTH->FenceBlock.NORTH;case SOUTH->FenceBlock.SOUTH;
                            case EAST->FenceBlock.EAST;default->FenceBlock.WEST;})==(side==direction),
                            "native fence direction mismatch "+fence+"/"+direction+"/"+side);
                require(normalize(level,origin,fence.defaultBlockState())&&level.getBlockState(origin).equals(connected),
                        "connection pass is idempotent");
            }
            clear(level,origin);
            level.setBlock(origin.east(),Blocks.NETHER_BRICK_FENCE.defaultBlockState(),18);
            level.setBlock(origin.west(2),Blocks.OAK_FENCE.defaultBlockState(),18);
            level.setBlock(origin,Blocks.OAK_FENCE.defaultBlockState(),18);
            require(normalize(level,origin,Blocks.OAK_FENCE.defaultBlockState())
                    &&!level.getBlockState(origin).getValue(FenceBlock.EAST)
                    &&!level.getBlockState(origin).getValue(FenceBlock.WEST),"incompatible fences and air gaps remain separate");

            for(Block connector:List.of(Blocks.OAK_FENCE,Blocks.SPRUCE_FENCE,Blocks.COBBLESTONE_WALL,
                    Blocks.GLASS_PANE,Blocks.STAINED_GLASS_PANE.green(),Blocks.IRON_BARS)) {
                clear(level,origin);
                level.setBlock(origin.east(),Blocks.COBBLESTONE.defaultBlockState(),18);
                level.setBlock(origin.west(),connector.defaultBlockState(),18);
                level.setBlock(origin,connector.defaultBlockState().setValue(
                        net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED,true),18);
                BlockState expected=Block.updateFromNeighbourShapes(level.getBlockState(origin),level,origin);
                require(!expected.equals(level.getBlockState(origin)),"fixture actually begins disconnected "+connector);
                require(normalize(level,origin,connector.defaultBlockState())&&level.getBlockState(origin).equals(expected)
                        &&level.getBlockState(origin).getFluidState().isSource(),"native shape/water preservation "+connector);
            }
            for(Direction facing:Direction.Plane.HORIZONTAL) {
                clear(level,origin);
                BlockState gate=Blocks.OAK_FENCE_GATE.defaultBlockState().setValue(FenceGateBlock.FACING,facing)
                        .setValue(FenceGateBlock.OPEN,true).setValue(FenceGateBlock.POWERED,true);
                Direction lateral=facing.getClockWise();
                level.setBlock(origin.relative(lateral),Blocks.COBBLESTONE_WALL.defaultBlockState(),18);
                level.setBlock(origin,gate,18);
                require(normalize(level,origin,gate)&&level.getBlockState(origin).getValue(FenceGateBlock.IN_WALL)
                        &&level.getBlockState(origin).getValue(FenceGateBlock.OPEN)
                        &&level.getBlockState(origin).getValue(FenceGateBlock.POWERED)
                        &&level.getBlockState(origin).getValue(FenceGateBlock.FACING)==facing,"gate controls preserved "+facing);
                clear(level,origin);
                level.setBlock(origin.east(),gate,18);
                level.setBlock(origin,Blocks.OAK_FENCE.defaultBlockState(),18);
                require(normalize(level,origin,Blocks.OAK_FENCE.defaultBlockState())
                        &&level.getBlockState(origin).getValue(FenceBlock.EAST)==(facing.getAxis()==Direction.Axis.Z),
                        "fence joins only the matching gate axis "+facing);
            }

            clear(level,origin);
            BlockState fence=Blocks.OAK_FENCE.defaultBlockState();
            level.setBlock(origin.east(),Blocks.COBBLESTONE.defaultBlockState(),18);
            level.setBlock(origin,fence,18);
            try(var guard=VillageDevelopmentProtection.register(context->!context.position().equals(origin))) {
                require(!normalize(level,origin,fence)&&level.getBlockState(origin).equals(fence),"protection veto preserves fence");
            }
            DevelopmentLandProtection.placed(level,origin,fence);
            require(!normalize(level,origin,fence)&&level.getBlockState(origin).equals(fence),"recorded same-type player fence untouched");
            level.setBlock(origin,Blocks.AIR.defaultBlockState(),18);
            DevelopmentLandProtection.removed(level,origin); DevelopmentLandProtection.tick(level.getServer());
            require(!normalize(level,origin,fence)&&level.getBlockState(origin).isAir(),"deleted fence not regenerated");
            level.setBlock(origin,Blocks.STONE.defaultBlockState(),18);
            require(!normalize(level,origin,fence)&&level.getBlockState(origin).is(Blocks.STONE),"replacement remains untouched");
            level.setBlock(origin,fence,18);
            occupant=new ServerPlayer(level.getServer(),level,new GameProfile(UUID.randomUUID(),"FenceFixture"),ClientInformation.createDefault());
            occupant.setPos(origin.getX()+.9,origin.getY(),origin.getZ()+.5); level.players().add(occupant);
            require(!normalize(level,origin,fence)&&level.getBlockState(origin).equals(fence),"rail expansion waits for player");
            occupant.setPos(origin.getX()+7,origin.getY(),origin.getZ()+3);
            require(normalize(level,origin,fence)&&level.getBlockState(origin).getValue(FenceBlock.EAST),"safe retry connects fence");
            BlockPos unloaded=new BlockPos(160008,origin.getY(),160008);
            require(!level.hasChunk(unloaded.getX()>>4,unloaded.getZ()>>4),"unloaded fixture starts unloaded");
            require(!normalize(level,unloaded,fence)&&!level.hasChunk(unloaded.getX()>>4,unloaded.getZ()>>4),
                    "connection repair never force-loads a missing column");
            clear(level,origin);
            BlockState wall=Blocks.STONE_BRICK_WALL.defaultBlockState();
            for(int y=0;y<=1;y++) {
                level.setBlock(origin.above(y),wall,18);
                level.setBlock(origin.above(y).north(),Blocks.OAK_PLANKS.defaultBlockState(),18);
                level.setBlock(origin.above(y).west(),Blocks.POLISHED_ANDESITE.defaultBlockState(),18);
            }
            level.setBlock(origin.above(2),Blocks.OAK_PLANKS.defaultBlockState(),18);
            require(pass(level,origin,List.of(placement(wall,"STRUCTURE",0),placement(wall,"STRUCTURE",1)),false),
                    "stacked wall pass rejected");
            for(int y=0;y<=1;y++) require(level.getBlockState(origin.above(y)).equals(
                    Block.updateFromNeighbourShapes(level.getBlockState(origin.above(y)),level,origin.above(y))),
                    "stacked wall requires top-down shape settlement at "+y);

            // The actual ordinary finish pass must include supplied COSMETIC, not just STRUCTURE.
            clear(level,origin); level.setBlock(origin.east(),Blocks.COBBLESTONE.defaultBlockState(),18);
            level.setBlock(origin,fence,18);
            var economy=new EconomyService(); economy.start(Files.createTempDirectory("tes-fence-handover-"),962,0);
            var plan=new BankConstruction(origin.asLong(),origin.asLong(),null,8,
                    List.of(new BankConstruction.Cell(origin.asLong(),"minecraft:air","minecraft:oak_fence")));
            require(economy.reserveBankConstruction(962,plan),"connection fixture reservation");
            job=ConstructionOwnership.bank(962,origin.asLong()); ownership.begin(economy,job,null,962,origin.asLong(),true);
            Object cosmetic=placement(fence,"COSMETIC");
            require(pass(level,origin,cosmetic,false)&&level.getBlockState(origin).equals(fence),"unsupplied optional fence not adopted");
            ownership.claim(job,origin,fence,false);
            require(pass(level,origin,cosmetic,false)&&level.getBlockState(origin).getValue(FenceBlock.EAST),"supplied yard fence finalized");
            ownership.finish(job); job=null;
            level.setBlock(origin,fence,18);
            require(pass(level,origin,cosmetic,true)&&level.getBlockState(origin).getValue(FenceBlock.EAST),"completed yard shape refresh after receipts released");

            // Test the real Bank gate, not merely the low-level shape helper.
            level.setBlock(origin,Blocks.AIR.defaultBlockState(),18);
            VillageConstructionActivitySelfTest.prepareFences(level,economy,VillageConstructionActivity.bankTag(962,plan.origin()));
            require(VillageBankManager.advanceBankConstruction(level,economy,962,plan)==1,"progressive Bank places fence");
            level.setBlock(origin,fence,18); // Reproduce stale authored shape immediately before handover.
            try(var guard=VillageDevelopmentProtection.register(context->!(context.position().equals(origin)
                    &&context.existing().is(Blocks.OAK_FENCE)&&context.proposed().is(Blocks.OAK_FENCE)))) {
                VillageBankManager.advanceBankConstruction(level,economy,962,plan);
                require(!economy.hasGeneratedBankRegion(962)&&economy.pendingBankConstructionsSnapshot().containsKey(962L),
                        "Bank cannot hand over unresolved protected rail shape");
            }
            VillageBankManager.advanceBankConstruction(level,economy,962,plan);
            require(economy.hasGeneratedBankRegion(962)&&level.getBlockState(origin).getValue(FenceBlock.EAST),"Bank safely retries and hands over connected fence");
            System.out.println("PASS construction connections: "+fences.size()+" fence families, four axes, gates, walls, colored panes, water, gaps, protection, player occupancy, supplied yards and Bank handover");
        } catch(Exception error) { throw new IllegalStateException("Construction connection smoke failed",error); }
        finally {
            if(job!=null) ownership.finish(job);
            if(occupant!=null) {level.players().remove(occupant); occupant.discard();}
            for(Villager v:level.getEntitiesOfClass(Villager.class,new AABB(origin).inflate(14)))v.discard();
            before.forEach((at,state)->level.setBlock(at,state,18));
            DevelopmentLandProtection.removed(level,origin); DevelopmentLandProtection.tick(level.getServer());
            ConstructionDiagnostics.reset();
        }
    }
    private static boolean normalize(ServerLevel level,BlockPos at,BlockState state) {
        return VillageProsperityManager.normalizeModularConnectionState(level,at,state,null,962);
    }
    private static void clear(ServerLevel level,BlockPos origin) {
        for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++)for(int y=0;y<=2;y++)level.setBlock(origin.offset(x,y,z),Blocks.AIR.defaultBlockState(),18);
    }
    private static Object placement(BlockState fence,String role) throws Exception {
        return placement(fence,role,0);
    }
    @SuppressWarnings({"unchecked","rawtypes"})
    private static Object placement(BlockState fence,String role,int dy) throws Exception {
        Class<?> type=Class.forName(VillageProsperityManager.class.getName()+"$Placement");
        Class<?> roles=Class.forName(VillageProsperityManager.class.getName()+"$PlacementRole");
        var constructor=type.getDeclaredConstructor(int.class,int.class,int.class,BlockState.class,roles);
        constructor.setAccessible(true); return constructor.newInstance(0,dy,0,fence,Enum.valueOf((Class)roles,role));
    }
    private static boolean pass(ServerLevel level,BlockPos origin,Object placement,boolean completed) throws Exception {
        var method=VillageProsperityManager.class.getDeclaredMethod("normalizeAuthoredModularConnections",
                ServerLevel.class,BlockPos.class,UUID.class,long.class,List.class,boolean.class);
        method.setAccessible(true); return (boolean)method.invoke(null,level,origin,null,962L,
                placement instanceof List<?> list ? list : List.of(placement),completed);
    }
    private static void require(boolean ok,String message) {if(!ok)throw new IllegalStateException(message);}
}
