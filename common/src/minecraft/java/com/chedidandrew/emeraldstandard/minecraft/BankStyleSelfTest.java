package com.chedidandrew.emeraldstandard.minecraft;

import com.chedidandrew.emeraldstandard.core.*;
import java.util.*;
import java.nio.file.Files;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.nbt.NbtOps;

final class BankStyleSelfTest {
    static void verify(ServerLevel level) {
        try {
            var state=EconomyState.fresh(4321,System.currentTimeMillis(),0);
            var origin=new BlockPos(648,level.getMaxY()-48,648);
            for(var style:VillageArchitecture.BiomeDialect.values()) {
                UUID id=UUID.nameUUIDFromBytes(style.id().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                var village=state.village(id); village.villageId=id; village.dimensionKey="minecraft:overworld";
                village.centerPos=origin.asLong(); village.architectureDialect=style.id();
            }
            var dir=Files.createTempDirectory("tes-bank-styles-"); state.save(dir.resolve("the_emerald_standard.properties"));
            var economy=new EconomyService(); economy.start(dir,4321,0);
            for(var village:state.villages.values()) {
                var chosen=VillageBankManager.bankDialect(level,economy,village.villageId,origin);
                require(chosen.id().equals(village.architectureDialect),"plot biome must not override "+village.architectureDialect);
                var ledger=new BankStyleLedger(); ledger.remember(origin.asLong(),chosen.id());
                var saved=BankStyleLedger.CODEC.parse(NbtOps.INSTANCE,BankStyleLedger.CODEC.encodeStart(NbtOps.INSTANCE,ledger).getOrThrow()).getOrThrow();
                require(saved.style(origin.asLong())==chosen,"commissioned palette survives restart");
                require(saved.style(origin.east(100).asLong())==null,"old completed Bank has no inferred repaint style");
            }
            require(VillageBankManager.bankDialect(level,economy,null,origin)==VillageProsperityManager.biomeDialect(level,origin),"unknown village retains biome fallback");
            System.out.println("PASS Bank styles: five saved village identities beat plot biome, durable ledger, old Banks untouched and unknown-style fallback");
        } catch(Exception e) {throw new IllegalStateException("Bank style fixture",e);}
    }
    private static void require(boolean value,String message) {if(!value)throw new IllegalStateException(message);}
}
