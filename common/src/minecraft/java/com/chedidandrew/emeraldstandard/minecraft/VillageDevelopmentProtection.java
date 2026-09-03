package com.chedidandrew.emeraldstandard.minecraft;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/** Cooperative, loader-neutral placement veto for village and bank development. */
public final class VillageDevelopmentProtection {
    private static final List<PlacementGuard> GUARDS = new CopyOnWriteArrayList<>();

    private VillageDevelopmentProtection() {
    }

    /**
     * Registers a placement guard. Closing the returned handle unregisters that exact guard.
     *
     * <p>This deliberately has no mandatory claim-mod dependency: integrations can register a
     * guard from either loader when their protection API is available.</p>
     */
    public static AutoCloseable register(PlacementGuard guard) {
        if (guard == null) {
            throw new IllegalArgumentException("guard");
        }
        GUARDS.add(guard);
        return () -> GUARDS.remove(guard);
    }

    /**
     * Returns whether a placement may proceed. Guard failures deny the placement so an integration
     * error cannot accidentally modify protected land. {@code villageId} may be null for a bank
     * that has not yet been associated with a prosperity record.
     */
    public static boolean mayPlace(
            ServerLevel level,
            UUID villageId,
            long projectId,
            BlockPos position,
            BlockState existing,
            BlockState proposed) {
        if (level == null || position == null || existing == null || proposed == null) {
            return false;
        }
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
