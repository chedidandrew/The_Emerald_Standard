package com.chedidandrew.emeraldstandard.core;

/**
 * Deterministic, loader-neutral presentation policy for the Village dashboard and local bulletin.
 *
 * <p>The policy deliberately describes only current facts already synchronized by the Banker menu.
 * It never invents a cause for an unrecorded change and has no authority over village state.</p>
 */
public final class VillageDashboardPolicy {
    /** A recent incident remains an urgent headline for the same window used by lifecycle logic. */
    public static final int RECENT_INCIDENT_DAYS =
            VillageProsperityEngine.INCIDENT_RECOVERY_DAYS;
    /** Population growth pauses below this safety score. */
    public static final double GROWTH_SAFETY_THRESHOLD =
            VillageProsperityEngine.GROWTH_SAFETY_THRESHOLD;
    /** Population growth pauses below this many stored food units per committed resident. */
    public static final double GROWTH_FOOD_PER_RESIDENT =
            VillageProsperityEngine.GROWTH_FOOD_PER_RESIDENT;
    /** A guard post becomes the simulation's first project priority below this safety score. */
    public static final double SECURITY_PROJECT_THRESHOLD =
            VillageProsperityEngine.SECURITY_PROJECT_THRESHOLD;

    private VillageDashboardPolicy() {
    }

    public enum BulletinKind {
        NO_VILLAGE,
        SIMULATION_PAUSED,
        RESTORATION,
        RECOVERY,
        INCIDENT,
        HARDSHIP,
        SECURITY,
        FOOD,
        HOUSING,
        PROJECT,
        DEVELOPMENT_PAUSED,
        PROSPERITY,
        STEADY
    }

    public enum ProsperityGuidance {
        FIND_VILLAGE,
        SIMULATION_DISABLED,
        RESTORE_WITH_FUND,
        RESTORE_UNAVAILABLE,
        TARGET_FOOD,
        FOOD_UNTARGETED,
        TARGET_HOUSING,
        HOUSING_UNTARGETED,
        SAFETY_BLOCKS_GROWTH,
        SPONSOR_PROJECT,
        SUPPORT_FOUNDATIONS,
        SUPPORT_FOUNDATIONS_UNTARGETED,
        MAINTAIN_FOUNDATIONS
    }

    public enum SafetyGuidance {
        FIND_VILLAGE,
        SIMULATION_DISABLED,
        PROTECT_RECOVERY,
        RECENT_INCIDENT,
        TARGET_SECURITY,
        SECURITY_UNTARGETED,
        MAINTAIN_SECURITY
    }

    public enum Tone {
        MUTED,
        CAUTION,
        DANGER,
        POSITIVE
    }

    public record Snapshot(
            boolean present,
            VillageProsperityEngine.Lifecycle lifecycle,
            int population,
            int housing,
            int developmentTier,
            double prosperity,
            double safety,
            double food,
            double materials,
            double treasury,
            VillageProsperityEngine.ProjectType projectType,
            double projectProgressPercent,
            boolean projectPlanning,
            int projectBacklog,
            double restorationFund,
            VillageProsperityEngine.IncidentCause incidentCause,
            int incidentAgeDays,
            double agricultureOutput,
            double miningOutput,
            double tradeOutput,
            boolean simulationEnabled,
            boolean visualProgressionEnabled,
            boolean fundAvailable,
            boolean targetedDonationsEnabled,
            boolean projectSponsorshipEnabled) {
        public Snapshot {
            lifecycle = lifecycle == null
                    ? VillageProsperityEngine.Lifecycle.ACTIVE : lifecycle;
            incidentCause = incidentCause == null
                    ? VillageProsperityEngine.IncidentCause.NONE : incidentCause;
            population = Math.max(0, population);
            housing = Math.max(0, housing);
            developmentTier = Math.max(0, developmentTier);
            prosperity = bounded(prosperity, 0.0, 100.0);
            safety = bounded(safety, 0.0, 100.0);
            food = nonnegative(food);
            materials = nonnegative(materials);
            treasury = nonnegative(treasury);
            projectProgressPercent = bounded(projectProgressPercent, 0.0, 100.0);
            projectBacklog = Math.max(0, projectBacklog);
            restorationFund = nonnegative(restorationFund);
            incidentAgeDays = Math.max(0, incidentAgeDays);
            agricultureOutput = nonnegative(agricultureOutput);
            miningOutput = nonnegative(miningOutput);
            tradeOutput = nonnegative(tradeOutput);
        }
    }

    public record Assessment(
            BulletinKind bulletin,
            Tone tone,
            ProsperityGuidance prosperityGuidance,
            SafetyGuidance safetyGuidance) {
    }

    public static Assessment assess(Snapshot snapshot) {
        if (snapshot == null || !snapshot.present()) {
            return new Assessment(
                    BulletinKind.NO_VILLAGE,
                    Tone.MUTED,
                    ProsperityGuidance.FIND_VILLAGE,
                    SafetyGuidance.FIND_VILLAGE);
        }
        if (!snapshot.simulationEnabled()) {
            return new Assessment(
                    BulletinKind.SIMULATION_PAUSED,
                    Tone.MUTED,
                    ProsperityGuidance.SIMULATION_DISABLED,
                    SafetyGuidance.SIMULATION_DISABLED);
        }

        ProsperityGuidance prosperityGuidance = prosperityGuidance(snapshot);
        SafetyGuidance safetyGuidance = safetyGuidance(snapshot);
        if (needsRestoration(snapshot.lifecycle())) {
            return new Assessment(
                    BulletinKind.RESTORATION,
                    Tone.DANGER,
                    prosperityGuidance,
                    safetyGuidance);
        }
        if (snapshot.lifecycle() == VillageProsperityEngine.Lifecycle.RECOVERING) {
            return new Assessment(
                    BulletinKind.RECOVERY,
                    Tone.POSITIVE,
                    prosperityGuidance,
                    safetyGuidance);
        }
        if (hasRecentIncident(snapshot)) {
            return new Assessment(
                    BulletinKind.INCIDENT,
                    Tone.DANGER,
                    prosperityGuidance,
                    safetyGuidance);
        }
        if (snapshot.lifecycle() == VillageProsperityEngine.Lifecycle.DEVASTATED) {
            return new Assessment(
                    BulletinKind.HARDSHIP,
                    Tone.DANGER,
                    prosperityGuidance,
                    safetyGuidance);
        }
        if (snapshot.lifecycle() == VillageProsperityEngine.Lifecycle.THREATENED
                || snapshot.safety() < SECURITY_PROJECT_THRESHOLD) {
            return new Assessment(
                    BulletinKind.SECURITY,
                    Tone.DANGER,
                    prosperityGuidance,
                    safetyGuidance);
        }
        if (foodBlocksGrowth(snapshot)) {
            return new Assessment(
                    BulletinKind.FOOD,
                    Tone.CAUTION,
                    prosperityGuidance,
                    safetyGuidance);
        }
        if (housingBlocksGrowth(snapshot)) {
            return new Assessment(
                    BulletinKind.HOUSING,
                    Tone.CAUTION,
                    prosperityGuidance,
                    safetyGuidance);
        }
        if (snapshot.projectType() != null) {
            return new Assessment(
                    snapshot.visualProgressionEnabled()
                            ? BulletinKind.PROJECT
                            : BulletinKind.DEVELOPMENT_PAUSED,
                    Tone.CAUTION,
                    prosperityGuidance,
                    safetyGuidance);
        }
        if (snapshot.prosperity() >= 65.0 && snapshot.safety() >= 50.0) {
            return new Assessment(
                    BulletinKind.PROSPERITY,
                    Tone.POSITIVE,
                    prosperityGuidance,
                    safetyGuidance);
        }
        return new Assessment(
                BulletinKind.STEADY,
                Tone.MUTED,
                prosperityGuidance,
                safetyGuidance);
    }

    private static ProsperityGuidance prosperityGuidance(Snapshot snapshot) {
        if (!snapshot.simulationEnabled()) {
            return ProsperityGuidance.SIMULATION_DISABLED;
        }
        if (needsRestoration(snapshot.lifecycle())) {
            return snapshot.fundAvailable()
                    ? ProsperityGuidance.RESTORE_WITH_FUND
                    : ProsperityGuidance.RESTORE_UNAVAILABLE;
        }
        if (foodBlocksGrowth(snapshot)) {
            return snapshot.fundAvailable() && snapshot.targetedDonationsEnabled()
                    ? ProsperityGuidance.TARGET_FOOD
                    : ProsperityGuidance.FOOD_UNTARGETED;
        }
        if (housingBlocksGrowth(snapshot)) {
            return snapshot.fundAvailable() && snapshot.targetedDonationsEnabled()
                    ? ProsperityGuidance.TARGET_HOUSING
                    : ProsperityGuidance.HOUSING_UNTARGETED;
        }
        if (snapshot.safety() < GROWTH_SAFETY_THRESHOLD) {
            return ProsperityGuidance.SAFETY_BLOCKS_GROWTH;
        }
        if (snapshot.projectType() != null
                && snapshot.projectPlanning()
                && snapshot.fundAvailable()
                && snapshot.projectSponsorshipEnabled()) {
            return ProsperityGuidance.SPONSOR_PROJECT;
        }
        if (snapshot.prosperity() < 50.0 && snapshot.fundAvailable()) {
            return snapshot.targetedDonationsEnabled()
                    ? ProsperityGuidance.SUPPORT_FOUNDATIONS
                    : ProsperityGuidance.SUPPORT_FOUNDATIONS_UNTARGETED;
        }
        return ProsperityGuidance.MAINTAIN_FOUNDATIONS;
    }

    private static SafetyGuidance safetyGuidance(Snapshot snapshot) {
        if (!snapshot.simulationEnabled()) {
            return SafetyGuidance.SIMULATION_DISABLED;
        }
        if (needsRestoration(snapshot.lifecycle())
                || snapshot.lifecycle() == VillageProsperityEngine.Lifecycle.RECOVERING) {
            return SafetyGuidance.PROTECT_RECOVERY;
        }
        if (hasRecentIncident(snapshot)) {
            return SafetyGuidance.RECENT_INCIDENT;
        }
        if (snapshot.safety() < GROWTH_SAFETY_THRESHOLD) {
            return snapshot.fundAvailable() && snapshot.targetedDonationsEnabled()
                    ? SafetyGuidance.TARGET_SECURITY
                    : SafetyGuidance.SECURITY_UNTARGETED;
        }
        return SafetyGuidance.MAINTAIN_SECURITY;
    }

    private static boolean needsRestoration(VillageProsperityEngine.Lifecycle lifecycle) {
        return lifecycle == VillageProsperityEngine.Lifecycle.ABANDONED
                || lifecycle == VillageProsperityEngine.Lifecycle.EXTINCT;
    }

    private static boolean hasRecentIncident(Snapshot snapshot) {
        return snapshot.incidentCause() != VillageProsperityEngine.IncidentCause.NONE
                && snapshot.incidentAgeDays() <= RECENT_INCIDENT_DAYS;
    }

    private static boolean foodBlocksGrowth(Snapshot snapshot) {
        return snapshot.population() > 0
                && snapshot.food()
                        < snapshot.population() * GROWTH_FOOD_PER_RESIDENT;
    }

    private static boolean housingBlocksGrowth(Snapshot snapshot) {
        return snapshot.population() > 0 && snapshot.population() >= snapshot.housing();
    }

    private static double nonnegative(double value) {
        return Double.isFinite(value) ? Math.max(0.0, value) : 0.0;
    }

    private static double bounded(double value, double minimum, double maximum) {
        return Double.isFinite(value) ? Math.max(minimum, Math.min(maximum, value)) : minimum;
    }
}
