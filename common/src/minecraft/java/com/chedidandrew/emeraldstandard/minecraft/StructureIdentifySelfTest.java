package com.chedidandrew.emeraldstandard.minecraft;

import com.chedidandrew.emeraldstandard.core.*;
import com.mojang.brigadier.CommandDispatcher;
import java.nio.file.Files;
import java.util.*;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/** Isolated saved-plan lookup; does not place or repair any world block. */
final class StructureIdentifySelfTest {
    static void verify(ServerLevel level) {
        try {
            BlockPos at=new BlockPos(1808,level.getMaxY()-64,1808);
            level.getChunk(at); // Test setup, never the command.
            var state=EconomyState.fresh(70,0,0);
            var village=state.village(new UUID(70,70));
            village.dimensionKey=level.dimension().identifier().toString();
            village.centerPos=at.asLong();village.architectureDialect="plains";village.architectureCharacter="rustic";
            for(int i=1;i<=2;i++) {
                var p=new EconomyState.VillageProject();p.projectId=i;
                p.type=VillageProsperityEngine.ProjectType.COTTAGE;p.totalBlocks=100;
                p.materializedBlocks=i==1?100:47;p.materializedComplete=i==1;
                p.designTemplateId="biome_plains_cottage_hearth_01";p.designTemplateRevision=16;
                p.designSchema=VillageArchitecture.BLUEPRINT_SCHEMA;
                p.designPaletteId="balanced";p.designDressingId="prosperous";
                p.designSignature=VillageArchitecture.blueprintSignature(p.type,p.designTemplateId,16,p.designPaletteId,p.designDressingId,false);
                p.designPlanHashVersion=VillageArchitecture.BLUEPRINT_PLAN_HASH_VERSION;p.designPlanHash="a".repeat(64);
                p.economicComplete=true;p.economicProgress=1;
                p.trailAnchorSet=true;p.trailAnchorPos=at.west(2).asLong();p.trailTotalBlocks=1;
                p.originPos=at.asLong();p.boundsMinPos=at.asLong();p.boundsMaxPos=at.offset(8,8,8).asLong();
                village.projects.add(p);
            }
            village.projectSerial=2;
            var bankOrigin=at.east(64);level.getChunk(bankOrigin);
            state.pendingBankConstructions.put(70L,new BankConstruction(bankOrigin.asLong(),bankOrigin.offset(6,1,9).asLong(),village.villageId,
                    ApprovedBankStructures.currentVersion(0,0),List.of(new BankConstruction.Cell(bankOrigin.asLong(),"minecraft:air","minecraft:stone")),Set.of(),false,"plains"));
            var directory=Files.createTempDirectory("tes-identity-");
            var file=directory.resolve("the_emerald_standard.properties");state.save(file);
            var economy=new EconomyService();economy.start(directory,70,0);
            byte[] saved=Files.readAllBytes(file);var block=level.getBlockState(at);
            var dispatcher=new CommandDispatcher<CommandSourceStack>();
            dispatcher.register(StructureIdentifyCommand.command("tesstructure",economy));
            dispatcher.register(StructureIdentifyCommand.command("structure",economy));
            var source=level.getServer().createCommandSourceStack().withLevel(level).withSuppressedOutput();
            String coordinates=at.getX()+" "+at.getY()+" "+at.getZ();
            require(dispatcher.execute("tesstructure "+coordinates,source)==2,"Both complete and stale overlapping plans must be reported");
            require(dispatcher.execute("tesstructure "+(at.getX()+8)+" "+(at.getY()+8)+" "+(at.getZ()+8),source)==2,"Inclusive maximum bounds omitted");
            require(dispatcher.execute("structure "+coordinates,source)==2,"Alias mismatch");
            var observer=new net.minecraft.server.level.ServerPlayer(level.getServer(),level,
                    new com.mojang.authlib.GameProfile(new UUID(70,71),"IdentityFixture"),net.minecraft.server.level.ClientInformation.createDefault());
            observer.setPos(at.getX()+2.5,at.getY()+1,at.getZ()+2.5);
            require(dispatcher.execute("tesstructure",source.withEntity(observer).withPosition(observer.position()))==2,"Default player standing lookup failed");
            require(dispatcher.execute("tesstructure "+(bankOrigin.getX()+6)+" "+(bankOrigin.getY()+1)+" "+(bankOrigin.getZ()+9),source)==1,"Pending Bank without a generated marker was not identified");
            require(dispatcher.execute("tesstructure "+at.getX()+" "+(at.getY()+20)+" "+at.getZ(),source)==0,"Vertical bounds ignored");
            var remote=new BlockPos(29000000,at.getY(),29000000);
            require(!level.hasChunkAt(remote),"Remote fixture unexpectedly loaded");
            require(dispatcher.execute("tesstructure 29000000 "+at.getY()+" 29000000",source)==0&&!level.hasChunkAt(remote),"Lookup force-loaded a chunk");
            observer.setPos(remote.getX()+.5,remote.getY(),remote.getZ()+.5);
            require(dispatcher.execute("tesstructure",source.withEntity(observer).withPosition(observer.position()))==0
                    &&level.getChunkSource().getChunkNow(remote.getX()>>4,remote.getZ()>>4)==null
                    &&level.getChunkSource().getChunkNow(remote.getX()>>4,(remote.getZ()+32)>>4)==null,"Default ray lookup loaded chunks");
            require(Arrays.equals(saved,Files.readAllBytes(file))&&block.equals(level.getBlockState(at)),"Read-only command changed persisted state or world");
            var projects=economy.snapshot().villages.get(village.villageId).projects;
            require(projects.size()==2&&projects.get(1).materializedBlocks==47&&!projects.get(1).materializedComplete,"Lookup mutated stale construction");
            System.out.println("PASS structure identification: overlapping complete/stale saved plans, alias, inclusive/vertical bounds, unloaded target and read-only persistence/world");
        } catch(Exception error) {throw new IllegalStateException("Structure lookup smoke failed",error);}
    }
    private static void require(boolean value,String message) {if(!value)throw new IllegalStateException(message);}
}
