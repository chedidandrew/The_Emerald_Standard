package com.chedidandrew.emeraldstandard.minecraft;

/** Loader-neutral decisions used by Village Bank site selection and fallback recovery. */
public final class VillageBankPlacementPolicy {
    private VillageBankPlacementPolicy() {
    }

    /**
     * A bank lot may clear replaceable plants and snow, but never solid blocks or block entities.
     */
    public static boolean acceptsVolumeCell(
            boolean air, boolean replaceable, boolean hasBlockEntity) {
        return !hasBlockEntity && (air || replaceable);
    }

    /** Only an explicitly persisted Banker-only fallback may start another structure build. */
    public static boolean shouldRetryPersistedFallback(boolean explicitlyPersisted) {
        return explicitlyPersisted;
    }

    /** Limits repeated terrain scans while a player remains in a village with no suitable lot. */
    public static boolean retryDue(long gameTime, Long previousAttempt, long intervalTicks) {
        if (previousAttempt == null || gameTime < previousAttempt) {
            return true;
        }
        return gameTime - previousAttempt >= Math.max(1L, intervalTicks);
    }

    /** Only a complete search with no candidate is a true terrain fallback. */
    public static boolean shouldPersistFallback(
            boolean searchComplete, boolean hadCandidates) {
        return searchComplete && !hadCandidates;
    }
}
