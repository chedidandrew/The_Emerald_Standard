package com.chedidandrew.emeraldstandard.minecraft;

import com.chedidandrew.emeraldstandard.core.*;
import java.nio.file.Files;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Native terrain changes, receipts and durable handover in a disposable world only. */
final class ConstructionTerrainCompletionSelfTest {
    static void verify(ServerLevel level) {
        BlockPos soil=new BlockPos(3008,level.getMaxY()-48,3008), path=soil.east(), wall=soil.above();
        long key=99860;
        String job=ConstructionOwnership.bank(key,soil.asLong());
        Map<BlockPos,BlockState> before=new LinkedHashMap<>();
        var ledger=ConstructionOwnership.get(level);
        try {
            for(int x=-2;x<=3;x++) for(int z=-2;z<=2;z++) for(int y=-1;y<=3;y++) {
                BlockPos at=soil.offset(x,y,z);level.getChunk(at);before.put(at,level.getBlockState(at));
                level.setBlock(at,y==-1?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState(),18);
            }
            var directory=Files.createTempDirectory("tes-soil-handover-");
            var economy=new EconomyService();economy.start(directory,99860,0);economy.configureForcedVillageDevelopment(true);
            var plan=new BankConstruction(soil.asLong(),soil.above().asLong(),null,8,List.of(
                    new BankConstruction.Cell(soil.asLong(),"minecraft:grass_block[snowy=false]","minecraft:grass_block[snowy=false]"),
                    new BankConstruction.Cell(path.asLong(),"minecraft:grass_block[snowy=false]","minecraft:dirt_path"),
                    new BankConstruction.Cell(wall.asLong(),"minecraft:air","minecraft:oak_planks")));
            require(economy.reserveBankConstruction(key,plan),"frozen Bank reservation");
            ledger.begin(economy,job,null,key,soil.asLong(),true);
            // Natural pre-existing grass was adopted, not supplied. No receipt is required
            // to keep it as dirt; we do not replace any soil or grant drops/loot for it.
            level.setBlock(soil,Blocks.DIRT.defaultBlockState(),18);
            level.setBlock(path,Blocks.DIRT.defaultBlockState(),18);
            require(!VillageBankManager.bankConstructionCellMatches(level,path,Blocks.DIRT.defaultBlockState(),
                    Blocks.DIRT_PATH.defaultBlockState()),"foreign dirt is not an authored path");
            ledger.reserve(job,path,Blocks.DIRT_PATH.defaultBlockState());
            require(!ConstructionOwnership.settledPathGround(level,path,Blocks.DIRT.defaultBlockState(),
                    Blocks.DIRT_PATH.defaultBlockState()),"reservation alone is not supplied-path authority");
            ledger.claim(job,path,Blocks.DIRT_PATH.defaultBlockState(),false);
            require(ConstructionOwnership.owned(level,path,Blocks.GRASS_BLOCK.defaultBlockState())
                    &&Block.getDrops(Blocks.GRASS_BLOCK.defaultBlockState(),level,path,null).isEmpty(),
                    "supplied path soil stays no-drop after dirt regrows grass");
            ledger.claim(job,path,Blocks.DIRT.defaultBlockState(),false);
            require(ConstructionOwnership.owned(level,path,Blocks.GRASS_BLOCK.defaultBlockState()),"supplied stable dirt retains soil receipt");
            ledger.claim(job,path,Blocks.DIRT_PATH.defaultBlockState(),false);
            require(ConstructionOwnership.settledPathGround(level,path,Blocks.DIRT.defaultBlockState(),
                    Blocks.DIRT_PATH.defaultBlockState()),"builder-supplied covered path settles");
            require(!VillageBankManager.bankConstructionCellMatches(level,soil,Blocks.AIR.defaultBlockState(),
                    Blocks.GRASS_BLOCK.defaultBlockState()),"missing ground is never waived");
            require(!VillageBankManager.bankConstructionCellMatches(level,wall,Blocks.DIRT.defaultBlockState(),
                    Blocks.OAK_PLANKS.defaultBlockState()),"dirt cannot substitute for a wall");
            require(VillageProsperityManager.naturalSoilEquivalent(Blocks.GRASS_BLOCK.defaultBlockState(),
                    Blocks.DIRT.defaultBlockState()),"grass spreading onto dirt is accepted too");
            level.setBlock(wall,Blocks.COBBLESTONE.defaultBlockState(),18);
            var reload=new EconomyService();reload.start(directory,99860,0);reload.configureForcedVillageDevelopment(true);economy=reload;
            require(economy.pendingBankConstructionsSnapshot().get(key).cells().equals(plan.cells()),"soil recovery preserves saved intent");
            require(VillageBankManager.advanceBankConstruction(level,economy,key,plan)==0
                    &&economy.pendingBankConstructionsSnapshot().containsKey(key),"foreign wall blocks handover");
            var diagnostic=ConstructionDiagnostics.recent("bank:"+key,level.getGameTime());
            require(diagnostic!=null&&diagnostic.reason().contains(wall.toShortString())
                    &&diagnostic.reason().contains("minecraft:cobblestone")
                    &&diagnostic.reason().contains("minecraft:oak_planks"),"blocked Bank identifies actual and expected cell");
            level.setBlock(wall,Blocks.OAK_PLANKS.defaultBlockState(),18);
            VillageBankManager.advanceBankConstruction(level,economy,key,plan);
            require(economy.hasGeneratedBankRegion(key)&&!economy.pendingBankConstructionsSnapshot().containsKey(key),
                    "settled Bank completes without rewriting its soil");
            require(level.getBlockState(soil).is(Blocks.DIRT)&&level.getBlockState(path).is(Blocks.DIRT),"no endless soil/path replacement");
            var finished=new EconomyService();finished.start(directory,99860,0);
            require(finished.hasGeneratedBankRegion(key),"settled handover persists across restart");
            System.out.println("PASS terrain completion: adopted grass decay, supplied paths, no receipt/no ground/foreign wall rejection, cell diagnostics and durable Bank handover");
        } catch(Exception e) {throw new IllegalStateException("Terrain completion regression",e);}
        finally {ledger.finish(job);before.forEach((at,state)->level.setBlock(at,state,18));ConstructionDiagnostics.reset();}
    }
    private static void require(boolean value,String message) {if(!value)throw new IllegalStateException(message);}
}
