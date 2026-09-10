package com.chedidandrew.emeraldstandard.minecraft;

import java.util.*;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.*;
import net.minecraft.server.level.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;

/** Opt-in isolated live-server checks, including the real shared BlockItem mixin. */
final class DevelopmentProtectionSelfTest {
    static void verify(ServerLevel level) {
        BlockPos origin=new BlockPos(2600,level.getMaxY()-24,2600);
        var before=new LinkedHashMap<BlockPos,BlockState>(); UUID owner=UUID.randomUUID();
        var land=DevelopmentLandProtection.land(level.getServer());
        String name="smoke_"+owner.toString().substring(0,8);
        try {
            for(int x=-4;x<9;x++) for(int z=-4;z<5;z++) for(int y=-1;y<5;y++) {
                BlockPos pos=origin.offset(x,y,z); level.getChunk(pos);
                before.put(pos,level.getBlockState(pos)); level.setBlock(pos,(y==-1?Blocks.STONE:Blocks.AIR).defaultBlockState(),18);
            }
            land.add(name,owner,level.dimension().identifier().toString(),origin.getX()+2,origin.getZ()+2,origin.getX(),origin.getZ());
            require(!VillageDevelopmentProtection.mayPlace(level,owner,1,origin,Blocks.AIR.defaultBlockState(),Blocks.OAK_PLANKS.defaultBlockState()),"zone blocks building");
            require(DevelopmentLandProtection.excludes(level,origin.below(100),origin.below(100)),"zone protects below selected floor");
            require(DevelopmentLandProtection.excludes(level,origin.above(20),origin.above(20)),"zone protects above selected floor");
            require(!DevelopmentLandProtection.excludes(level,origin.west(),origin.west()),"zone boundary does not spill outside");
            require(!VillageDevelopmentProtection.mayPlace(level,owner,1,origin,Blocks.STONE.defaultBlockState(),Blocks.AIR.defaultBlockState()),"zone blocks excavation");
            land.remove(name,owner,false);
            String commandName=name+"_command";
            var dispatcher=level.getServer().getCommands().getDispatcher();
            var source=level.getServer().createCommandSourceStack().withLevel(level);
            require(dispatcher.execute("nobuild add "+commandName+" "+origin.getX()+" "+origin.getZ()+" "+(origin.getX()+2)+" "+(origin.getZ()+2),source)==1,"native two-corner command accepted");
            require(DevelopmentLandProtection.excludes(level,origin,origin),"native command creates protection");
            require(dispatcher.execute("nobuild remove "+commandName,source)==1,"native command removes protection");
            require(VillageDevelopmentProtection.mayPlace(level,owner,1,origin,Blocks.AIR.defaultBlockState(),Blocks.OAK_PLANKS.defaultBlockState()),"removal releases ordinary ground");
            require(!DevelopmentLandProtection.meaningful(Blocks.TORCH.defaultBlockState())
                    && !DevelopmentLandProtection.meaningful(Blocks.POPPY.defaultBlockState()),"lighting and flowers do not claim land");
            ServerPlayer player=new ServerPlayer(level.getServer(),level,new GameProfile(owner,"LandSmoke"),ClientInformation.createDefault());
            player.setPos(origin.getX()+7,origin.getY()+1,origin.getZ()+3);
            for(int x=0;x<4;x++) {
                BlockPos pos=origin.east(x);
                ItemStack item=new ItemStack(Items.OAK_LOG);
                var hit=new BlockHitResult(Vec3.atCenterOf(pos.below()).add(0,.5,0),Direction.UP,pos.below(),false);
                ((BlockItem)Items.OAK_LOG).place(new BlockPlaceContext(player,InteractionHand.MAIN_HAND,item,hit));
                require(level.getBlockState(pos).is(Blocks.OAK_LOG),"native player placement succeeded");
                DevelopmentLandProtection.tick(level.getServer());
                if(x==0) require(!DevelopmentLandProtection.playerBuild(level,pos),"isolated temporary block does not reserve surrounding lot");
            }
            require(DevelopmentLandProtection.playerBuild(level,origin),"actual player placement mixin records connected raw-log build");
            require(!VillageDevelopmentProtection.mayPlace(level,owner,1,origin,level.getBlockState(origin),Blocks.AIR.defaultBlockState()),"natural-looking player framing protected");
            for(int x=0;x<4;x++) level.setBlock(origin.east(x),Blocks.AIR.defaultBlockState(),18);
            require(!DevelopmentLandProtection.playerBuild(level,origin),"removed evidence no longer vetoes land");
            for(int x=0;x<4;x++) DevelopmentLandProtection.removed(level,origin.east(x));
            DevelopmentLandProtection.tick(level.getServer());
            require(land.nearby(level.dimension().identifier().toString(),origin.getX(),origin.getZ(),4).isEmpty(),"break observations prune stale evidence");
            for(int x=0;x<3;x++) level.setBlock(origin.offset(x,2,0),Blocks.OAK_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS,Direction.Axis.X),18);
            for(int x:new int[]{0,2}) for(int y=0;y<2;y++) level.setBlock(origin.offset(x,y,0),Blocks.OAK_LOG.defaultBlockState(),18);
            require(DevelopmentLandProtection.oldTimberFrame(level,origin),"older unrecorded timber doorway recognized");
            require(!new VillageSitePreparation.Survey(level).clearable(origin),"old raw-log doorway is not a tree to clear");
            System.out.println("PASS DevelopmentProtectionSelfTest: full-height zones, boundaries, excavation, removal, native player-placement tracking, torch/flower exceptions");
        } catch(Exception error) { throw new IllegalStateException("Development protection smoke failed",error); }
        finally {
            if(land.zones().stream().anyMatch(z->z.name().equals(name))) try { land.remove(name,owner,true); } catch(Exception ignored) { }
            before.forEach((pos,state)->level.setBlock(pos,state,18));
        }
    }
    private static void require(boolean condition,String message) { if(!condition) throw new IllegalStateException(message); }
}
