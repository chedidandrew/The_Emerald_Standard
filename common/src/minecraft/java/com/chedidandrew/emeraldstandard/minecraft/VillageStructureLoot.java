package com.chedidandrew.emeraldstandard.minecraft;

import com.chedidandrew.emeraldstandard.core.VillageProsperityEngine.ProjectType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.storage.loot.LootTable;

/** One-shot loot on newly placed storage only. Never call from audits, repair or existing-cell paths. */
public final class VillageStructureLoot {
    private VillageStructureLoot() { }

    public static ResourceKey<LootTable> table(ProjectType type) {
        return key(switch (type) {
            case COTTAGE, HOUSE -> "residence";
            case INN -> "inn";
            case WAREHOUSE -> "warehouse";
            case MINE_ENTRANCE -> "mine";
            case MARKET_SQUARE -> "market";
            case SMITHY -> "smithy";
            case GRANARY -> "granary";
            case GUARD_POST -> "guard_post";
            case EXCHANGE_HALL -> "exchange_hall";
        });
    }

    public static ResourceKey<LootTable> key(String role) {
        return ResourceKey.create(Registries.LOOT_TABLE,
                Identifier.fromNamespaceAndPath("the_emerald_standard", "chests/village/" + role));
    }

    /** Caller must have just created this container in a protection-approved empty cell. */
    public static void assignNewStorage(ServerLevel level, BlockPos position, ResourceKey<LootTable> table) {
        var entity = level.getBlockEntity(position);
        if (!(entity instanceof ChestBlockEntity || entity instanceof BarrelBlockEntity)) return;
        assignFreshStorage((RandomizableContainerBlockEntity) entity, table, level.getSeed());
    }

    static void assignFreshStorage(RandomizableContainerBlockEntity storage,
            ResourceKey<LootTable> table, long worldSeed) {
        // Never unpack an existing deferred table while testing emptiness, or replace supplied contents.
        if (storage.getLootTable() != null || !storage.isEmpty()) return;
        long seed = worldSeed ^ storage.getBlockPos().asLong() ^ table.identifier().toString().hashCode();
        storage.setLootTable(table);
        storage.setLootTableSeed(seed == 0L ? 1L : seed);
        storage.setChanged();
    }
}
