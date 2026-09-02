from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]


def read(rel):
    return (ROOT / rel).read_text()


def write(rel, text):
    path = ROOT / rel
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text)


def add_import(rel, imp):
    text = read(rel)
    line = f"import {imp};\n"
    if line in text:
        return
    match = re.search(r"(package [^;]+;\n\n)(?=import )", text)
    if not match:
        raise RuntimeError(f"Cannot add import to {rel}")
    write(rel, text[:match.end()] + line + text[match.end():])


for rel in ("fabric/gradle.properties", "neoforge/gradle.properties"):
    text = read(rel).replace("mod_version=0.3.0-beta.2", "mod_version=0.3.0-beta.3")
    if "mod_version=0.3.0-beta.3" not in text:
        raise RuntimeError(f"Could not bump version in {rel}")
    write(rel, text)

write(
    "common/src/minecraft/java/com/chedidandrew/emeraldstandard/minecraft/VillageDevelopmentProtection.java",
    '''package com.chedidandrew.emeraldstandard.minecraft;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/** Cooperative veto API for settlement construction. */
public final class VillageDevelopmentProtection {
    private static final List<PlacementGuard> GUARDS = new CopyOnWriteArrayList<>();

    private VillageDevelopmentProtection() {
    }

    public static AutoCloseable register(PlacementGuard guard) {
        if (guard == null) {
            throw new IllegalArgumentException("guard");
        }
        GUARDS.add(guard);
        return () -> GUARDS.remove(guard);
    }

    public static boolean mayPlace(
            ServerLevel level,
            UUID villageId,
            long projectId,
            BlockPos position,
            BlockState existing,
            BlockState proposed) {
        PlacementContext context = new PlacementContext(
                level, villageId, projectId, position.immutable(), existing, proposed);
        for (PlacementGuard guard : GUARDS) {
            try {
                if (!guard.mayPlace(context)) {
                    return false;
                }
            } catch (RuntimeException ignored) {
                return false;
            }
        }
        return true;
    }

    @FunctionalInterface
    public interface PlacementGuard {
        boolean mayPlace(PlacementContext context);
    }

    public record PlacementContext(
            ServerLevel level,
            UUID villageId,
            long projectId,
            BlockPos position,
            BlockState existing,
            BlockState proposed) {
    }
}
''',
)

service = "common/src/main/java/com/chedidandrew/emeraldstandard/core/EconomyService.java"
add_import(service, "java.util.Collection")
text = read(service)
text = text.replace(
    "observeProgress(now, gameTicks, STARTUP_CATCH_UP_BATCH_DAYS)",
    "observeProgress(now, gameTicks, adaptiveCatchUpBatch(STARTUP_CATCH_UP_BATCH_DAYS))",
)
text = text.replace(
    "observeProgress(now, gameTicks, TICK_CATCH_UP_BATCH_DAYS)",
    "observeProgress(now, gameTicks, adaptiveCatchUpBatch(TICK_CATCH_UP_BATCH_DAYS))",
)
write(service, text)

text = read(service)
if "villageSnapshotsNear(" not in text:
    marker = "    public synchronized UUID villageIdForBankRegion(long regionKey) {\n"
    index = text.find(marker)
    if index < 0:
        raise RuntimeError("Village snapshot insertion marker missing")
    method = '''    /** Returns only settlements near one of the supplied loaded-player positions. */
    public synchronized List<VillageSnapshot> villageSnapshotsNear(
            String dimensionKey,
            Collection<Long> packedPositions,
            double maximumDistance) {
        if (state == null || packedPositions == null || packedPositions.isEmpty()) {
            return List.of();
        }
        double maximumDistanceSquared = maximumDistance * maximumDistance;
        VillageProsperityEngine.VillageFundamentals fundamentals =
                villageMarketIntegrationEnabled
                        ? state.villageFundamentals()
                        : VillageProsperityEngine.VillageFundamentals.neutral();
        List<VillageSnapshot> snapshots = new ArrayList<>();
        for (EconomyState.VillageRecord village : state.villages.values()) {
            if (!Objects.equals(village.dimensionKey, dimensionKey)) {
                continue;
            }
            boolean nearby = false;
            for (long packedPosition : packedPositions) {
                if (distanceSquared(village.centerPos, packedPosition) <= maximumDistanceSquared) {
                    nearby = true;
                    break;
                }
            }
            if (nearby) {
                snapshots.add(villageSnapshot(village, fundamentals));
            }
        }
        return List.copyOf(snapshots);
    }

'''
    write(service, text[:index] + method + text[index:])

text = read(service)
if "private long adaptiveCatchUpBatch(" not in text:
    marker = "    private boolean observeProgress(long now, long gameTicks, long maximumDaysToAdvance) {"
    index = text.find(marker)
    if index < 0:
        raise RuntimeError("observeProgress marker missing")
    method = '''    /** Keeps catch-up work bounded as persistent settlement count grows. */
    private long adaptiveCatchUpBatch(long requestedDays) {
        if (state == null || state.villages.isEmpty()) {
            return requestedDays;
        }
        long villageCount = Math.max(1L, state.villages.size());
        long budgetedDays = Math.max(1L, 24_000L / villageCount);
        return Math.min(requestedDays, budgetedDays);
    }

'''
    write(service, text[:index] + method + text[index:])

manager = "common/src/minecraft/java/com/chedidandrew/emeraldstandard/minecraft/VillageProsperityManager.java"
add_import(manager, "net.minecraft.world.InteractionHand")
add_import(manager, "net.minecraft.world.entity.projectile.Projectile")
text = read(manager)
if "PROJECT_RETRY_AFTER_TICK" not in text:
    text = text.replace(
        "    private static final Map<UUID, Long> LAST_SETTLER_TICK = new HashMap<>();\n",
        "    private static final Map<UUID, Long> LAST_SETTLER_TICK = new HashMap<>();\n"
        "    private static final Map<Long, Long> PROJECT_RETRY_AFTER_TICK = new HashMap<>();\n"
        "    private static final Map<UUID, Long> LAST_WORKER_VISUAL_TICK = new HashMap<>();\n"
        "    private static final long BLOCKED_PROJECT_RETRY_TICKS = 200L;\n",
    )
write(manager, text)

text = read(manager)
pattern = re.compile(
    r"\s*Entity killer = source == null \? null : source\.getEntity\(\);\n"
    r"(?:.|\n)*?VillageProsperityEngine\.IncidentCause cause = (?:.|\n)*?;\n"
    r"\s*String profession =",
    re.MULTILINE,
)
match = pattern.search(text)
if not match:
    raise RuntimeError("Death attribution block not found")
replacement = '''
        Entity killer = source == null ? null : source.getEntity();
        ServerPlayer responsible = responsiblePlayer(villager, source);
        UUID responsiblePlayer = responsible == null ? null : responsible.getUUID();
        VillageProsperityEngine.IncidentCause cause = responsible != null
                ? VillageProsperityEngine.IncidentCause.PLAYER
                : classifyCause(killer);
        String profession ='''
write(manager, text[:match.start()] + replacement + text[match.end():])

text = read(manager)
if "private static ServerPlayer responsiblePlayer(" not in text:
    marker = "    private static VillageProsperityEngine.IncidentCause classifyCause(Entity killer) {"
    index = text.find(marker)
    if index < 0:
        raise RuntimeError("classifyCause marker missing")
    helper = '''    /** Resolves direct, projectile-owner, and recent vanilla player attribution. */
    private static ServerPlayer responsiblePlayer(Villager victim, DamageSource source) {
        if (source != null) {
            Entity causing = source.getEntity();
            if (causing instanceof ServerPlayer player) {
                return player;
            }
            Entity direct = source.getDirectEntity();
            if (direct instanceof ServerPlayer player) {
                return player;
            }
            if (direct instanceof Projectile projectile
                    && projectile.getOwner() instanceof ServerPlayer player) {
                return player;
            }
        }
        return victim.getLastHurtByPlayer() instanceof ServerPlayer player ? player : null;
    }

'''
    write(manager, text[:index] + helper + text[index:])

text = read(manager)
old = '''        int remainingBudget = config.villageConstructionBlocksPerTick();
        for (EconomyService.VillageSnapshot snapshot : economy.villageSnapshots()) {
            EconomyState.VillageRecord village = snapshot.village();
            if (!"minecraft:overworld".equals(village.dimensionKey)
                    || !hasNearbyPlayer(level, village.centerPos, config.villageDevelopmentRadius())) {
                continue;
            }
'''
new = '''        int remainingBudget = config.villageConstructionBlocksPerTick();
        List<Long> playerPositions = level.getServer().getPlayerList().getPlayers().stream()
                .filter(player -> player.level() == level)
                .map(player -> player.blockPosition().asLong())
                .toList();
        for (EconomyService.VillageSnapshot snapshot : economy.villageSnapshotsNear(
                "minecraft:overworld", playerPositions, config.villageDevelopmentRadius())) {
            EconomyState.VillageRecord village = snapshot.village();
'''
if old in text:
    text = text.replace(old, new, 1)
elif "villageSnapshotsNear(" not in text:
    raise RuntimeError("Visual snapshot loop pattern missing")
write(manager, text)

text = read(manager)
needle = '''            EconomyState.VillageProject project = village.nextVisualProject();
            if (project == null) {
                continue;
            }
'''
if needle in text and "projectRetryKey" not in text:
    text = text.replace(
        needle,
        needle
        + '''            long projectRetryKey = projectRetryKey(village.villageId, project.projectId);
            if (gameTime < PROJECT_RETRY_AFTER_TICK.getOrDefault(projectRetryKey, Long.MIN_VALUE)) {
                continue;
            }
''',
        1,
    )
write(manager, text)

text = read(manager)
if "VillageDevelopmentProtection.mayPlace(" not in text:
    target = "if (!level.setBlock(target, placement.state, 3)) {"
    guarded = '''if (!VillageDevelopmentProtection.mayPlace(
                                level,
                                village.villageId,
                                project.projectId,
                                target,
                                current,
                                placement.state)
                            || !level.setBlock(target, placement.state, 3)) {'''
    if target not in text:
        raise RuntimeError("setBlock placement pattern missing")
    text = text.replace(target, guarded, 1)
write(manager, text)

text = read(manager)
needle = '''            economy.updateVillageProjectMaterialization(
                    village.villageId, project.projectId, index, complete, blocked);
'''
if needle in text and "PROJECT_RETRY_AFTER_TICK.put(projectRetryKey" not in text:
    text = text.replace(
        needle,
        needle
        + '''            if (blocked) {
                PROJECT_RETRY_AFTER_TICK.put(
                        projectRetryKey, gameTime + BLOCKED_PROJECT_RETRY_TICKS);
            } else if (complete) {
                PROJECT_RETRY_AFTER_TICK.remove(projectRetryKey);
            }
            showWorkerActivity(level, village, origin, gameTime);
''',
        1,
    )
write(manager, text)

text = read(manager)
if "private static void showWorkerActivity(" not in text:
    marker = "    private static void spawnPendingSettler("
    index = text.find(marker)
    if index < 0:
        raise RuntimeError("spawnPendingSettler marker missing")
    helper = '''    /** Displays bounded worker activity without making entity AI authoritative. */
    private static void showWorkerActivity(
            ServerLevel level,
            EconomyState.VillageRecord village,
            BlockPos projectOrigin,
            long gameTime) {
        long previous = LAST_WORKER_VISUAL_TICK.getOrDefault(
                village.villageId, Long.MIN_VALUE / 2L);
        if (gameTime - previous < 40L) {
            return;
        }
        AABB area = new AABB(projectOrigin).inflate(18.0, 8.0, 18.0);
        List<Villager> workers = level.getEntitiesOfClass(
                        Villager.class,
                        area,
                        villager -> villager.isAlive()
                                && village.villageId.equals(villageId(villager)))
                .stream()
                .sorted(Comparator.comparingDouble(villager ->
                        villager.distanceToSqr(
                                projectOrigin.getX() + 0.5,
                                projectOrigin.getY() + 1.0,
                                projectOrigin.getZ() + 0.5)))
                .limit(2)
                .toList();
        for (Villager worker : workers) {
            worker.getLookControl().setLookAt(
                    projectOrigin.getX() + 0.5,
                    projectOrigin.getY() + 1.0,
                    projectOrigin.getZ() + 0.5);
            worker.swing(InteractionHand.MAIN_HAND);
            level.sendParticles(
                    ParticleTypes.HAPPY_VILLAGER,
                    worker.getX(), worker.getY() + 1.1, worker.getZ(),
                    1, 0.15, 0.2, 0.15, 0.0);
        }
        if (!workers.isEmpty()) {
            LAST_WORKER_VISUAL_TICK.put(village.villageId, gameTime);
        }
    }

    private static long projectRetryKey(UUID villageId, long projectId) {
        return villageId.getMostSignificantBits()
                ^ Long.rotateLeft(villageId.getLeastSignificantBits(), 17)
                ^ Long.rotateLeft(projectId, 31);
    }

'''
    write(manager, text[:index] + helper + text[index:])

neo = "neoforge/src/main/java/com/chedidandrew/emeraldstandard/neoforge/EmeraldStandardNeoForge.java"
add_import(neo, "net.minecraft.world.InteractionHand")
text = read(neo)
text = text.replace(
    '''    public void onBlockInteract(PlayerInteractEvent.RightClickBlock event) {
        if (event.getEntity().level().isClientSide()
''',
    '''    public void onBlockInteract(PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand() != InteractionHand.MAIN_HAND
                || event.getEntity().level().isClientSide()
''',
)
text = text.replace(
    '''    public void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (!BankerAccess.isBanker(event.getTarget())) {
''',
    '''    public void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getHand() != InteractionHand.MAIN_HAND
                || !BankerAccess.isBanker(event.getTarget())) {
''',
)
write(neo, text)

test = "common/src/test/java/com/chedidandrew/emeraldstandard/core/VillageProsperityRegressionTest.java"
text = read(test)
if "testNearbySnapshotFiltering();" not in text:
    text = text.replace(
        "        testPersistenceAndStableIdentity();\n",
        "        testPersistenceAndStableIdentity();\n        testNearbySnapshotFiltering();\n",
    )
if "private static void testNearbySnapshotFiltering()" not in text:
    marker = "    private static EconomyState.VillageRecord village("
    index = text.find(marker)
    if index < 0:
        raise RuntimeError("Test helper marker missing")
    method = '''    private static void testNearbySnapshotFiltering() throws Exception {
        Path root = Files.createTempDirectory("emerald-village-nearby-");
        try {
            EconomyService service = new EconomyService();
            service.startWithSeed(root, 77L, 0L, 0L);
            service.observeVillage(new EconomyService.VillageObservation(
                    "minecraft:overworld", pack(0, 64, 0), 11L, 0L,
                    4, 6, 0, false, List.of()));
            service.observeVillage(new EconomyService.VillageObservation(
                    "minecraft:overworld", pack(500, 64, 500), 22L, 0L,
                    4, 6, 0, false, List.of()));
            List<EconomyService.VillageSnapshot> nearby = service.villageSnapshotsNear(
                    "minecraft:overworld", List.of(pack(10, 64, 10)), 96.0);
            require(nearby.size() == 1, "Nearby query copied unrelated villages");
        } finally {
            deleteTree(root);
        }
    }

'''
    text = text[:index] + method + text[index:]
write(test, text)

readme = read("README.md").replace("`0.3.0-beta.2`", "`0.3.0-beta.3`", 1)
if "### Beta.3 hardening" not in readme:
    marker = "\n## World configuration"
    index = readme.find(marker)
    section = '''

### Beta.3 hardening

- Claim and protection mods can veto prosperity-structure placements through a cooperative API.
- Blocked projects use retry backoff instead of repeatedly hammering protected positions.
- Physical development copies only settlements near loaded players.
- Catch-up batch size adapts to settlement count to reduce large-world stalls.
- Direct attacks, owned projectiles, and recent vanilla player attribution improve casualty classification.
- Up to two nearby residents show lightweight construction work cues without persistent custom AI.
- NeoForge ignores off-hand Banker interaction events, matching Fabric.
'''
    readme = readme[:index] + section + readme[index:]
write("README.md", readme)

changelog = read("CHANGELOG.md")
if "## 0.3.0-beta.3" not in changelog:
    marker = "All notable changes to The Emerald Standard are documented here.\n\n"
    section = '''## 0.3.0-beta.3 - 2026-09-02

### Added

- Cooperative `VillageDevelopmentProtection` placement-veto API.
- Lightweight resident work cues at active development sites.
- Nearby-settlement snapshot query and regression coverage.

### Changed

- Bumped both loaders to `0.3.0-beta.3`.
- Catch-up batches adapt to persistent settlement count.
- Visual materialization queries only settlements near loaded players.
- Blocked placements use retry backoff.
- Player attribution checks direct attackers, projectile ownership, and recent attacker memory.
- NeoForge Banker interaction requires the main hand.

### Fixed

- Reduced repeated placement attempts against protected positions.
- Added a loader-neutral integration seam for claim and protection mods.
- Reduced unnecessary settlement copies in large worlds.
- Reduced duplicate off-hand interaction risk on NeoForge.

'''
    changelog = changelog.replace(marker, marker + section, 1)
write("CHANGELOG.md", changelog)

prosperity = read("docs/VILLAGE_PROSPERITY.md")
if "## Beta.3 operational hardening" not in prosperity:
    prosperity += '''

## Beta.3 operational hardening

Visual development requests only settlements near loaded players. Blocked positions wait before retrying, and claim or protection integrations may register a `VillageDevelopmentProtection.PlacementGuard` without adding a mandatory dependency.

At most two nearby tagged residents perform brief look and arm-swing work cues. These cues are presentation only and never determine resources or completion. Catch-up batches also shrink automatically as persistent settlement count grows.
'''
write("docs/VILLAGE_PROSPERITY.md", prosperity)

architecture = read("docs/ARCHITECTURE.md")
if "### Development protection extension" not in architecture:
    architecture += '''

### Development protection extension

`VillageDevelopmentProtection` is a cooperative placement-veto API. Claim and protection integrations can reject proposed settlement placements. Guard exceptions fail closed. This complements loaded-chunk, natural-ground, empty-volume, block-entity, and successful-placement checks without mandatory dependencies.

Visual development uses `EconomyService.villageSnapshotsNear`, and economic catch-up adapts its daily batch to persistent settlement count.
'''
write("docs/ARCHITECTURE.md", architecture)

testing = read("docs/TESTING.md")
if "## Beta.3 protection and scale checks" not in testing:
    testing += '''

## Beta.3 protection and scale checks

- Register a placement guard that vetoes construction and verify progress stops.
- Clear the veto and verify the project retries after backoff.
- Confirm both loaders ignore off-hand Banker interactions.
- Confirm direct, owned-projectile, and recent-player-attributed deaths are player-caused.
- Profile development with many distant settlement records and confirm only nearby records are copied.
- Confirm no more than two residents perform visual work cues and no custom offline AI is created.
'''
write("docs/TESTING.md", testing)

write(
    "release/RELEASE_NOTES-0.3.0-beta.3.md",
    '''# The Emerald Standard 0.3.0-beta.3

This beta hardens Village Prosperity around protected-world compatibility, large-world pacing, casualty attribution, and lightweight visible resident activity.

## Highlights

- Cooperative placement-veto API for claim and protection integrations.
- Blocked-project retry backoff.
- Nearby-only settlement retrieval during physical development.
- Settlement-count-aware economic catch-up batches.
- Better player attribution for direct attacks, owned projectiles, and recent aggression.
- Lightweight resident work cues at active construction sites.
- Fabric and NeoForge main-hand interaction parity.

All beta.2 no-debt, persistence, physical-first recovery, safe-ground construction, infection, curing, emigration, market isolation, and functional-tier protections remain in place.

This remains a beta. Automated verification covers the shared simulation, persistence, both loader builds, packaged JARs, dedicated-server startup, and client bootstrap. Broad terrain, protection-mod, visual, and multiplayer soak testing remains valuable before 1.0.
''',
)

for rel in (
    ".github/workflows/export-worktree.yml",
    ".github/workflows/publish-alpha3.yml",
):
    path = ROOT / rel
    if path.exists():
        path.unlink()

print("beta3 hardening applied")
