package com.chedidandrew.emeraldstandard.minecraft;

import com.chedidandrew.emeraldstandard.core.BankConstruction;
import com.chedidandrew.emeraldstandard.core.EconomyService;
import java.nio.file.Files;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/** Full authored Bank built one cell at a time in the disposable smoke world. */
final class BankConstructionSelfTest {
    static void verify(ServerLevel level) {
        BlockPos origin = new BlockPos(648, level.getMaxY() - 48, 648);
        Map<BlockPos, BlockState> before = new LinkedHashMap<>();
        try {
            var dir = Files.createTempDirectory("tes-progressive-bank-");
            var economy = new EconomyService(); economy.start(dir, 774, 0);
            for (int x = -4; x <= 17; x++) for (int z = -8; z <= 15; z++) {
                BlockPos ground = origin.offset(x, -1, z);
                level.getChunk(ground); // Isolated fixture setup only.
                set(level, before, ground, Blocks.GRASS_BLOCK.defaultBlockState());
                // Back-right bank wing cuts into a four-block rise; the north entrance stays level.
                if (x >= 9 && z >= 3) for (int y = 0; y <= 3; y++)
                    set(level, before, origin.offset(x, y, z), Blocks.DIRT.defaultBlockState());
            }
            BlockPos tree = origin.offset(4, 0, 7);
            for (int y = 0; y < 18; y++) set(level, before, tree.above(y), Blocks.OAK_LOG.defaultBlockState());
            set(level, before, tree.above(17).east(), Blocks.OAK_LOG.defaultBlockState()
                    .setValue(net.minecraft.world.level.block.RotatedPillarBlock.AXIS, net.minecraft.core.Direction.Axis.X));
            set(level, before, tree.above(18), Blocks.OAK_LEAVES.defaultBlockState());
            set(level, before, origin.offset(6, 0, 6), Blocks.SWEET_BERRY_BUSH.defaultBlockState());
            var bankWidth = VillageBankManager.class.getDeclaredField("BANK_WIDTH"); bankWidth.setAccessible(true);
            var bankDepth = VillageBankManager.class.getDeclaredField("BANK_DEPTH"); bankDepth.setAccessible(true);
            var choose = VillageBankManager.class.getDeclaredMethod("safeOrigin", ServerLevel.class, int.class, int.class);
            choose.setAccessible(true);
            require(origin.equals(choose.invoke(null, level, origin.getX() + bankWidth.getInt(null) / 2,
                    origin.getZ() + bankDepth.getInt(null) / 2)), "production Bank survey selects a level entrance in woodland/hillside");
            require(VillageBankManager.reserveProgressiveBank(level, economy, origin, null, 777), "full Bank reservation");
            BankConstruction plan = economy.pendingBankConstructionsSnapshot().get(777L);
            require(plan.cells().stream().anyMatch(c -> c.before().startsWith("minecraft:oak_log") && c.after().equals("minecraft:air")),
                    "Bank freezes tree removal instead of rejecting forest");
            require(plan.cells().stream().anyMatch(c -> c.before().startsWith("minecraft:dirt")),
                    "Bank freezes hillside cut instead of rejecting occupied natural ground");
            for (var cell : plan.cells()) before.putIfAbsent(BlockPos.of(cell.position()), level.getBlockState(BlockPos.of(cell.position())));
            var playerStorage = plan.cells().stream().filter(c -> c.after().startsWith("minecraft:chest")
                    || c.after().startsWith("minecraft:barrel")).findFirst().orElseThrow();
            BlockPos playerStoragePos = BlockPos.of(playerStorage.position());
            var playerStorageState = net.minecraft.commands.arguments.blocks.BlockStateParser.parseForBlock(
                    level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.BLOCK),
                    playerStorage.after(), false).blockState();
            set(level, before, playerStoragePos, playerStorageState);
            require(!economy.hasGeneratedBankRegion(777), "reservation does not instantly complete Bank");
            Map<BlockPos,BlockState> initial=new HashMap<>();
            for(var cell:plan.cells()) initial.put(BlockPos.of(cell.position()),level.getBlockState(BlockPos.of(cell.position())));
            require(VillageBankManager.advanceBankConstruction(level,economy,777,plan)==0
                    && initial.entrySet().stream().allMatch(e->level.getBlockState(e.getKey()).equals(e.getValue())),
                    "Bank cannot change even one authored cell before perimeter preparation");
            require(ConstructionDiagnostics.preparingFence("bank:777") && !ConstructionDiagnostics.waiting("bank:777"),
                    "fence preparation is not a paused Bank, so the preparation scheduler can proceed");
            VillageConstructionActivitySelfTest.prepareFences(level,economy,VillageConstructionActivity.bankTag(777,plan.origin()));
            require(VillageBankManager.advanceBankConstruction(level, economy, 777, plan) == 1, "one first block");
            var reload = new EconomyService(); reload.start(dir, 774, 0); economy = reload;
            require(plan.equals(economy.pendingBankConstructionsSnapshot().get(777L)), "partial Bank resumes frozen plan");

            BlockPos second = origin.offset(17, 1, 0);
            set(level, before, second, Blocks.AIR.defaultBlockState());
            set(level, before, second.above(), Blocks.AIR.defaultBlockState());
            var other = new BankConstruction(second.asLong(), second.asLong(), null, 8, List.of(
                    new BankConstruction.Cell(second.asLong(), "minecraft:air", "minecraft:stone"),
                    new BankConstruction.Cell(second.above().asLong(), "minecraft:air", "minecraft:stone")));
            require(economy.reserveBankConstruction(778, other), "simultaneous site reserved");
            VillageConstructionActivitySelfTest.prepareFences(level,economy,VillageConstructionActivity.bankTag(778,other.origin()));
            require(VillageBankManager.advanceBankConstruction(level, economy, 777, plan) == 1
                    && VillageBankManager.advanceBankConstruction(level, economy, 778, other) == 1,
                    "two simultaneous sites each get one block, not a shared budget");
            set(level, before, second.above(), Blocks.CHEST.defaultBlockState());
            require(VillageBankManager.advanceBankConstruction(level, economy, 778, other) == 0
                    && level.getBlockState(second.above()).is(Blocks.CHEST), "player storage blocks construction, never overwritten");
            int pulses = 0;
            // Drive the real placement path with the shared night scheduler, without changing
            // the disposable server's clock. Both managers observing the same wake-up is safe.
            try {
                var config = EmeraldConfig.current();
                ConstructionTimeRuntime.observe(13_000, 13_000, config);
                ConstructionTimeRuntime.allowance("bank:777", 13_000, config);
                ConstructionTimeRuntime.observe(13_001, 24_000, config);
                ConstructionTimeRuntime.observe(13_001, 24_000, config);
                ConstructionTimeRuntime.observe(13_010, 24_009, config);
                int allowance = ConstructionTimeRuntime.allowance("bank:777", 13_010, config);
                require(allowance == config.constructionAllowance(13_010) + 8,
                        "sleep grants a bounded extra allowance to the same active Bank");
                int changed = 0;
                for (int i = 0; i < allowance; i++) {
                    if (changed >= config.constructionAllowance(13_010) && !ConstructionTimeRuntime.hasTime()) break;
                    changed += VillageBankManager.advanceBankConstruction(level, economy, 777, plan);
                }
                require(changed > config.constructionAllowance(13_010) && changed <= allowance,
                        "waking produces real extra Bank blocks within its bounded allowance");
                System.out.println("PASS live sleep catch-up: " + changed + " Bank cells within " + allowance + " allowance");
            } finally { ConstructionTimeRuntime.reset(); }
            while (economy.pendingBankConstructionsSnapshot().containsKey(777L) && pulses++ < plan.cells().size() + 20) {
                int placed = VillageBankManager.advanceBankConstruction(level, economy, 777, plan);
                require(placed >= 0 && placed <= 1, "per-pulse placement cap");
            }
            require(!economy.pendingBankConstructionsSnapshot().containsKey(777L)
                    && economy.hasGeneratedBankRegion(777), "full Bank completes without unsupported-cell deadlock");
            require(pulses > 100, "Bank was visibly progressive, not a bulk spawn");
            VillageWalkingSelfTest.building(level, origin.offset(-5, -3, -5), origin.offset(16, 20, 20), "Bank");
            var untouched = (net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity)
                    level.getBlockEntity(playerStoragePos);
            require(untouched != null && untouched.getLootTable() == null && untouched.isEmpty(),
                    "an existing player container in a reserved cell must not acquire loot");
            var registry = level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.BLOCK);
            for (var cell : plan.cells()) {
                var expected = net.minecraft.commands.arguments.blocks.BlockStateParser
                        .parseForBlock(registry, cell.after(), false).blockState();
                require(level.getBlockState(BlockPos.of(cell.position())).is(expected.getBlock()),
                        "completion neighbor updates must preserve the authored cell at " + BlockPos.of(cell.position()));
            }
            BankVillageOwnershipSelfTest.verify(level, economy, origin);
            var completed = new EconomyService(); completed.start(dir, 774, 0);
            require(completed.hasGeneratedBankRegion(777)
                    && !completed.pendingBankConstructionsSnapshot().containsKey(777L), "completed marker survives restart");
            System.out.println("PASS BankConstructionSelfTest: wooded hillside Bank, frozen tree/cut work, one-cell pacing, concurrent sites, restart, protected storage, completion; "
                    + plan.cells().size() + " planned cells, " + pulses + " single-cell completion pulses after sleep batch");
        } catch (Exception ex) { throw new IllegalStateException("Progressive Bank runtime test failed", ex); }
        finally {
            VillageConstructionActivitySelfTest.cleanupCrews(level);
            level.getEntitiesOfClass(Villager.class, new AABB(origin).inflate(24),
                    v -> BankerAccess.isBankerForRegion(v, 777)).forEach(Villager::discard);
            before.forEach((pos, state) -> level.setBlock(pos, state, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE));
        }
    }
    private static void set(ServerLevel level, Map<BlockPos, BlockState> before, BlockPos pos, BlockState state) {
        before.putIfAbsent(pos.immutable(), level.getBlockState(pos));
        level.setBlock(pos, state, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
    }
    private static void require(boolean value, String message) { if (!value) throw new IllegalStateException(message); }
}
