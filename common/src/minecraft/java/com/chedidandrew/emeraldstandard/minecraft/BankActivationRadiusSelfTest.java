package com.chedidandrew.emeraldstandard.minecraft;

import com.chedidandrew.emeraldstandard.core.EconomyService;
import com.mojang.authlib.GameProfile;
import java.nio.file.Files;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/** Native activation checks using only disposable economy state and a temporary observer. */
final class BankActivationRadiusSelfTest {
    static void verify(ServerLevel level) {
        var player = new ServerPlayer(level.getServer(), level,
                new GameProfile(UUID.randomUUID(), "BankRadiusFixture"), ClientInformation.createDefault());
        BlockPos center = new BlockPos(-22000, 80, -22000);
        String dimension = level.dimension().identifier().toString();
        var economy = new EconomyService();
        try {
            economy.start(Files.createTempDirectory("tes-bank-radius-"), 777, 0);
            UUID first = UUID.randomUUID(), second = UUID.randomUUID();
            economy.observeNaturalVillage(first, new EconomyService.VillageObservation(
                    dimension, center.asLong(), 11, 0, 3, 5, 0, false, List.of()), Set.of());
            economy.observeNaturalVillage(second, new EconomyService.VillageObservation(
                    dimension, center.east(32).asLong(), 12, 0, 3, 5, 0, false, List.of()), Set.of());
            level.players().add(player);
            // The Bank plot is farther away, but its village center is inside the activation circle.
            BlockPos plot = center.east(100);
            player.setPos(center.getX() - 256, center.getY() + 150, center.getZ());
            require(VillageBankManager.activeBankVillages(level, economy, 256).size() == 1,
                    "first Bank eligible at exactly 256 blocks, above the village, outside old 192");
            require(VillageBankManager.bankWorkActive(level, economy, first, plot, 256),
                    "offset Bank construction uses the same center as initial activation");
            require(VillageBankManager.bankActivationAnchor(level, economy, first, plot).equals(center),
                    "fallback recovery also resolves the original village center");
            require(!VillageBankManager.bankWorkActive(level, economy, first, plot, 128),
                    "smaller setting immediately pauses existing Bank work");
            player.setPos(center.getX() - 257, center.getY(), center.getZ());
            require(VillageBankManager.activeBankVillages(level, economy, 256).isEmpty()
                    && !VillageBankManager.bankWorkActive(level, economy, first, plot, 256),
                    "outside boundary cannot start or advance");
            player.setPos(center.getX() - 192, center.getY(), center.getZ() - 192);
            require(VillageBankManager.activeBankVillages(level, economy, 256).isEmpty(),
                    "no diagonal square-range leak");
            player.setPos(center.getX(), center.getY(), center.getZ());
            require(VillageBankManager.activeBankVillages(level, economy, 256).size() == 2,
                    "one nearby Bank does not hide another natural village");
            require(VillageBankManager.bankWorkActive(level, economy, null, center, 48),
                    "legacy unassociated Bank can use its own saved anchor");
            require(economy.villageCenterPosition(first, "minecraft:the_nether") == null,
                    "activation cannot cross dimensions");
            level.players().remove(player);
            require(VillageBankManager.activeBankVillages(level, economy, 512).isEmpty()
                    && !VillageBankManager.bankWorkActive(level, economy, first, plot, 512),
                    "no player means no new Bank or resumed construction");
            System.out.println("PASS Bank activation: configured circle, village center, height, multiple villages, runtime radius changes, no players");
        } catch (Exception exception) {
            throw new IllegalStateException("Bank activation radius fixture failed", exception);
        } finally {
            level.players().remove(player);
            player.discard();
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
