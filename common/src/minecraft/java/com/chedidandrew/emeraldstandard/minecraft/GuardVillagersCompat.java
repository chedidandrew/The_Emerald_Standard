package com.chedidandrew.emeraldstandard.minecraft;

import com.chedidandrew.emeraldstandard.core.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

/** Registry-only optional integration. No Guard Villagers classes, AI, inventory or NBT writes. */
public final class GuardVillagersCompat {
    public static final Identifier GUARD_ID = Identifier.fromNamespaceAndPath("guardvillagers", "guard");
    public static boolean available() { return BuiltInRegistries.ENTITY_TYPE.containsKey(GUARD_ID); }

    static boolean eligible(Mob guard) {
        if (!GUARD_ID.equals(BuiltInRegistries.ENTITY_TYPE.getKey(guard.getType()))
                || !guard.isAlive() || guard.isRemoved() || guard.isBaby() || guard.isNoAi() || guard.isPassenger()) return false;
        LivingEntity target = guard.getTarget();
        return !(target instanceof Player) && !(target instanceof AbstractVillager)
                && !(target instanceof IronGolem)
                && (target == null || !GUARD_ID.equals(BuiltInRegistries.ENTITY_TYPE.getKey(target.getType())));
    }

    static void observe(ServerLevel level, EconomyService economy, EconomyState.VillageRecord village,
                        EmeraldConfig config) {
        if (!config.guardVillagersEnabled() || !available()) {
            economy.observeVillageGuardIds(village.villageId, List.of(), 0, 0); return;
        }
        BlockPos center = BlockPos.of(village.centerPos);
        // Same loaded-area rule as the resident census. Partial scans are unknown, not zero guards.
        if (!VillageProsperityManager.censusChunksLoaded(level, center, 48)) return;
        List<UUID> guards = new ArrayList<>();
        int examined = 0;
        String dimension = level.dimension().identifier().toString();
        for (Mob guard : level.getEntitiesOfClass(Mob.class, new AABB(center).inflate(48, 24, 48),
                GuardVillagersCompat::eligible)) {
            if (guard.distanceToSqr(center.getX() + .5, center.getY() + .5, center.getZ() + .5) > 48 * 48) continue;
            // One deterministic nearest saved district; the same guard cannot cover two overlapping censuses.
            UUID owner = economy.nearestSecurityVillage(dimension, guard.blockPosition().asLong());
            if (!village.villageId.equals(owner)) continue;
            if (canPatrol(level, guard)) guards.add(guard.getUUID());
            // Failed mobility probes consume the budget too; a large cage must not cause
            // unlimited path checks merely because none of its guards qualify.
            if (++examined == VillageGuardSecurity.MAX_COUNT) break;
        }
        economy.observeVillageGuardIds(village.villageId, guards, config.guardSafetyPerGuard(), config.guardMaximumSafetyBonus());
    }

    /** Bounded read-only mobility probe. No navigation/AI changes and no unloaded chunk reads.
     * A guard must be able to leave a three-block radius, not merely stand in a sealed cell. */
    static boolean canPatrol(ServerLevel level, Mob guard) {
        BlockPos start = guard.blockPosition();
        ArrayDeque<BlockPos> pending = new ArrayDeque<>(); pending.add(start);
        Set<BlockPos> visited = new HashSet<>(); visited.add(start);
        int probes = 0;
        while (!pending.isEmpty() && probes < 96) {
            BlockPos from = pending.remove();
            for (var direction : net.minecraft.core.Direction.Plane.HORIZONTAL) {
                for (int dy : new int[] {0, 1, -1}) {
                    BlockPos next = from.relative(direction).above(dy);
                    if (Math.abs(next.getY() - start.getY()) > 2 || !visited.add(next)) continue;
                    if (++probes > 96) return false;
                    AABB box = guard.getBoundingBox().move(next.getX() + .5 - guard.getX(),
                            next.getY() - guard.getY(), next.getZ() + .5 - guard.getZ());
                    if (!level.hasChunk(((int)Math.floor(box.minX)) >> 4, ((int)Math.floor(box.minZ)) >> 4)
                            || !level.hasChunk(((int)Math.floor(box.maxX)) >> 4, ((int)Math.floor(box.maxZ)) >> 4)) continue;
                    if (!level.getBlockState(next.below()).isFaceSturdy(level, next.below(), net.minecraft.core.Direction.UP)
                            || !level.getFluidState(next).isEmpty() || !level.noCollision(guard, box)) continue;
                    // A one-block step must also clear the space above the lower cell.
                    if (dy > 0 && !level.noCollision(guard, guard.getBoundingBox().move(
                            from.getX()+.5-guard.getX(), next.getY()-guard.getY(), from.getZ()+.5-guard.getZ()))) continue;
                    int dx = next.getX() - start.getX(), dz = next.getZ() - start.getZ();
                    if (dx * dx + dz * dz >= 9) return true;
                    pending.add(next);
                    break;
                }
            }
        }
        return false;
    }
    private GuardVillagersCompat() {}
}
