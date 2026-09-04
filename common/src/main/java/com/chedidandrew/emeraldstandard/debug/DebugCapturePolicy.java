package com.chedidandrew.emeraldstandard.debug;

import java.util.UUID;

/** Loader-neutral access and scope rules for diagnostic captures. */
public final class DebugCapturePolicy {
    private DebugCapturePolicy() {
    }

    public static boolean isOwner(UUID ownerId, UUID actorId) {
        return ownerId != null && ownerId.equals(actorId);
    }

    public static boolean isWatchedVillage(UUID watchedVillageId, UUID candidateVillageId) {
        return watchedVillageId != null && watchedVillageId.equals(candidateVillageId);
    }

    /** Reports whether an incident involved the initiating tester without exposing another UUID. */
    public static boolean isResponsibleTester(UUID ownerId, UUID responsiblePlayerId) {
        return isOwner(ownerId, responsiblePlayerId);
    }
}
