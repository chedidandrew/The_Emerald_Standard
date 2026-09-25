package com.chedidandrew.emeraldstandard.minecraft;

import com.chedidandrew.emeraldstandard.core.VillageProsperityEngine.ProjectType;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

/** Real registry/loot/NBT checks in the opt-in isolated server smoke world; no world blocks are written. */
final class VillageStructureLootSelfTest {
    private VillageStructureLootSelfTest() { }

    static void verify(ServerLevel level) {
        Map<String, Set<String>> allowed = Map.of(
                "residence", Set.of("bread", "apple", "wheat_seeds", "stick", "string", "flower_pot"),
                "inn", Set.of("bread", "baked_potato", "cooked_cod", "bowl", "candle"),
                "warehouse", Set.of("oak_planks", "stick", "string", "leather", "coal"),
                "mine", Set.of("torch", "coal", "cobblestone", "raw_copper", "stone_pickaxe"),
                "market", Set.of("apple", "carrot", "bread", "paper", "leather"),
                "smithy", Set.of("coal", "iron_nugget", "copper_ingot", "iron_ingot", "stone_axe"),
                "granary", Set.of("wheat", "wheat_seeds", "carrot", "potato", "beetroot_seeds"),
                "guard_post", Set.of("arrow", "bread", "torch", "leather", "stone_sword"),
                "exchange_hall", Set.of("paper", "book", "ink_sac", "feather", "candle"),
                "bank", Set.of("paper", "ink_sac", "feather", "book", "candle"));
        Set<String> mapped = new HashSet<>();
        for (ProjectType type : ProjectType.values()) {
            mapped.add(VillageStructureLoot.table(type).identifier().getPath().replace("chests/village/", ""));
        }
        mapped.add("bank");
        require(mapped.equals(allowed.keySet()), "Every project and bank must have an intentional loot role");
        var params = new LootParams.Builder(level).withParameter(LootContextParams.ORIGIN, Vec3.ZERO)
                .create(LootContextParamSets.CHEST);
        for (var role : allowed.entrySet()) {
            var key = VillageStructureLoot.key(role.getKey());
            LootTable table = level.getServer().reloadableRegistries().getLootTable(key);
            require(table != LootTable.EMPTY, "Missing/invalid loot table: " + key);
            Set<String> seen = new HashSet<>();
            for (long seed = 1; seed <= 100; seed++) {
                var rolls = table.getRandomItems(params, seed);
                require(rolls.size() >= 2 && rolls.size() <= 4, "Expected 2-4 modest rolls: " + key);
                int total = 0;
                for (ItemStack stack : rolls) {
                    var item = BuiltInRegistries.ITEM.getKey(stack.getItem());
                    require(item.getNamespace().equals("minecraft") && role.getValue().contains(item.getPath()),
                            "Unexpected item for " + key + ": " + item);
                    require(stack.getCount() >= 1 && stack.getCount() <= 8 && !stack.isEnchanted(),
                            "Oversized/enchanted loot in " + key);
                    seen.add(item.getPath());
                    total += stack.getCount();
                }
                require(total >= 2 && total <= 32, "Excessive total loot in " + key);
            }
            require(seen.equals(role.getValue()), "A weighted entry never rolled: " + key);
        }
        verifyContainer(level, new ChestBlockEntity(BlockPos.ZERO, Blocks.CHEST.defaultBlockState()));
        verifyContainer(level, new BarrelBlockEntity(BlockPos.ZERO, Blocks.BARREL.defaultBlockState()));
        System.out.println("PASS village structure loot: 10 tables, 1000 rolls, chest/barrel NBT and one-shot contents");
    }

    private static void verifyContainer(ServerLevel level, RandomizableContainerBlockEntity storage) {
        var residence = VillageStructureLoot.key("residence");
        var bank = VillageStructureLoot.key("bank");
        VillageStructureLoot.assignFreshStorage(storage, residence, level.getSeed());
        require(residence.equals(storage.getLootTable()), "New storage did not receive deferred loot");
        long seed = storage.getLootTableSeed();
        require(seed != 0L, "Deferred loot needs a stable nonzero seed");
        VillageStructureLoot.assignFreshStorage(storage, bank, level.getSeed());
        require(residence.equals(storage.getLootTable()) && storage.getLootTableSeed() == seed,
                "Existing deferred loot was replaced/unpacked");
        storage = roundTrip(level, storage);
        require(residence.equals(storage.getLootTable()) && storage.getLootTableSeed() == seed,
                "Deferred loot identity/seed was lost on save");
        storage.setLevel(level);
        storage.getItem(0); // Vanilla rolls the table on first access.
        require(storage.getLootTable() == null && !storage.isEmpty(), "First access did not fill the container");
        RandomizableContainerBlockEntity loaded = roundTrip(level, storage);
        require(loaded.getLootTable() == null, "Opened storage regained its loot table");
        for (int slot = 0; slot < storage.getContainerSize(); slot++) {
            require(ItemStack.matches(storage.getItem(slot), loaded.getItem(slot)), "Loot changed after reload");
        }
        loaded.clearContent();
        loaded = roundTrip(level, loaded);
        require(loaded.isEmpty() && loaded.getLootTable() == null, "Loot refilled after being taken and reloaded");
        // The production hook never visits old emptied storage. Existing supplied contents are also protected.
        loaded.setItem(0, new ItemStack(Items.DIAMOND));
        VillageStructureLoot.assignFreshStorage(loaded, bank, level.getSeed());
        require(loaded.getLootTable() == null && loaded.getItem(0).is(Items.DIAMOND),
                "Supplied contents were overwritten");
    }

    private static RandomizableContainerBlockEntity roundTrip(ServerLevel level,
            RandomizableContainerBlockEntity storage) {
        return (RandomizableContainerBlockEntity) BlockEntity.loadStatic(storage.getBlockPos(),
                storage.getBlockState(), storage.saveWithFullMetadata(level.registryAccess()), level.registryAccess());
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
