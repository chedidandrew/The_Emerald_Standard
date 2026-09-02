package com.chedidandrew.emeraldstandard.core;

import java.util.Collection;
import java.util.Locale;
import java.util.UUID;

/** Loader-neutral village prosperity, development, and market-fundamentals simulation. */
public final class VillageProsperityEngine {
    public static final int MAX_ABSTRACT_POPULATION = 64;
    public static final int MAX_PROJECTS_PER_VILLAGE = 12;
    public static final int INCIDENT_HISTORY_LIMIT = 16;
    public static final int RESIDENT_HISTORY_LIMIT = 128;
    public static final double RESTORATION_EMERALD_TARGET = 25.0;

    private static final long GROWTH_SALT = 0x47524F575448L;
    private static final long PROJECT_SALT = 0x50524F4A454354L;
    private static final long LOCAL_SHOCK_SALT = 0x4C4F43414C53484FL;

    public enum Lifecycle {
        ACTIVE,
        THREATENED,
        DEVASTATED,
        EXTINCT,
        RECOVERING,
        ABANDONED
    }

    public enum IncidentCause {
        NONE,
        RAID,
        PILLAGER,
        HOSTILE,
        PLAYER,
        ENVIRONMENT,
        UNKNOWN
    }

    public enum ResidentStatus {
        ACTIVE,
        AWAY,
        INFECTED,
        EMIGRATED,
        DEAD
    }

    public enum ProjectType {
        COTTAGE(80.0, 8.0, 4, 180),
        WAREHOUSE(130.0, 18.0, 0, 260),
        MINE_ENTRANCE(160.0, 25.0, 0, 230);

        private final double materialCost;
        private final double treasuryCost;
        private final int housingGain;
        private final int nominalBlocks;

        ProjectType(double materialCost, double treasuryCost, int housingGain, int nominalBlocks) {
            this.materialCost = materialCost;
            this.treasuryCost = treasuryCost;
            this.housingGain = housingGain;
            this.nominalBlocks = nominalBlocks;
        }

        public double materialCost() {
            return materialCost;
        }

        public double treasuryCost() {
            return treasuryCost;
        }

        public int housingGain() {
            return housingGain;
        }

        public int nominalBlocks() {
            return nominalBlocks;
        }
    }

    /** Normalized village effects. Values are intentionally bounded to prevent market domination. */
    public record VillageFundamentals(
            double broad,
            double mining,
            double agriculture,
            double trade,
            double redstone,
            double alchemy,
            double transport,
            double security,
            int eligibleVillages) {
        public static VillageFundamentals neutral() {
            return new VillageFundamentals(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0);
        }
    }

    private VillageProsperityEngine() {
    }

    /** Advances one abstract village day. No blocks, chunks, entities, or pathfinding are touched. */
    public static void advanceOneDay(EconomyState.VillageRecord village, long worldSeed, long day) {
        advanceOneDay(village, worldSeed, day, true, false);
    }

    /** Advances one abstract village day with optional automatic extinction recovery. */
    public static void advanceOneDay(
            EconomyState.VillageRecord village, long worldSeed, long day, boolean automaticRecovery) {
        advanceOneDay(village, worldSeed, day, automaticRecovery, false);
    }

    /** Advances one day and optionally requires physical settlers before production resumes. */
    public static void advanceOneDay(
            EconomyState.VillageRecord village,
            long worldSeed,
            long day,
            boolean automaticRecovery,
            boolean requirePhysicalSettlers) {
        if (village == null || village.villageId == null) {
            return;
        }
        village.lastSimulatedDay = day;

        if (village.population <= 0) {
            village.population = 0;
            village.agricultureOutput = 0.0;
            village.miningOutput = 0.0;
            village.tradeOutput = 0.0;
            village.redstoneOutput = 0.0;
            village.alchemyOutput = 0.0;
            village.transportOutput = 0.0;
            village.securityOutput = 0.0;
            advanceRecovery(village, day, automaticRecovery, requirePhysicalSettlers);
            updateDevelopmentTier(village);
            return;
        }

        if (village.lifecycle == Lifecycle.EXTINCT || village.lifecycle == Lifecycle.ABANDONED) {
            village.lifecycle = Lifecycle.RECOVERING;
        }

        int cottages = completedProjects(village, ProjectType.COTTAGE);
        int warehouses = completedProjects(village, ProjectType.WAREHOUSE);
        int mines = completedProjects(village, ProjectType.MINE_ENTRANCE);
        double safetyFactor = clamp(0.25 + village.safety / 125.0, 0.25, 1.05);
        double prosperityFactor = clamp(0.55 + village.prosperity / 180.0, 0.55, 1.12);
        double lifecycleFactor = switch (village.lifecycle) {
            case ACTIVE -> 1.0;
            case RECOVERING -> 0.72;
            case THREATENED -> 0.55;
            case DEVASTATED -> 0.42;
            case EXTINCT, ABANDONED -> 0.0;
        };
        double productivity = safetyFactor * prosperityFactor * lifecycleFactor;
        double population = village.population;

        village.agricultureOutput = population * 0.58 * productivity
                * (1.0 + 0.04 * village.developmentTier);
        village.miningOutput = population * 0.22 * productivity
                * (1.0 + 0.32 * mines);
        village.tradeOutput = population * 0.18 * productivity
                * (1.0 + 0.22 * warehouses);
        village.redstoneOutput = population * 0.018 * productivity
                * Math.max(0, village.developmentTier - 1);
        village.alchemyOutput = population * 0.015 * productivity
                * Math.max(0, village.developmentTier - 1);
        village.transportOutput = population * 0.10 * productivity
                * (1.0 + 0.15 * warehouses);
        village.securityOutput = population * 0.08
                * clamp(0.5 + village.safety / 100.0, 0.4, 1.5);

        double foodUse = population * 0.46;
        double foodSpoilage = village.foodSupply * 0.0008;
        double infrastructureUpkeep = cottages * 0.03 + warehouses * 0.08 + mines * 0.10;
        double materialUpkeep = (cottages + warehouses + mines) * 0.015;
        village.foodSupply = clamp(
                village.foodSupply + village.agricultureOutput - foodUse - foodSpoilage,
                0.0,
                20_000.0);
        village.materialSupply = clamp(village.materialSupply
                        + village.miningOutput
                        + population * 0.04 * productivity
                        - materialUpkeep,
                0.0, 20_000.0);
        village.treasury = clamp(village.treasury
                        + village.tradeOutput * 0.055
                        + village.transportOutput * 0.015
                        - population * 0.008
                        - infrastructureUpkeep,
                0.0, 1_000_000.0);

        double localShock = unit(worldSeed, village.villageId, day, LOCAL_SHOCK_SALT);
        if (localShock < 0.0015) {
            village.agricultureOutput *= 0.72;
            village.foodSupply *= 0.985;
            village.prosperity = Math.max(0.0, village.prosperity - 1.5);
        } else if (localShock < 0.0030) {
            village.tradeOutput *= 0.74;
            village.treasury = Math.max(0.0, village.treasury - population * 0.04);
            village.prosperity = Math.max(0.0, village.prosperity - 0.8);
        } else if (localShock > 0.9985) {
            village.materialSupply = clamp(
                    village.materialSupply + population * 0.35, 0.0, 20_000.0);
            village.prosperity = Math.min(100.0, village.prosperity + 0.5);
        }
        if (village.foodSupply < population * 3.0) {
            village.prosperity = Math.max(0.0, village.prosperity - 0.7);
            village.safety = Math.max(0.0, village.safety - 0.15);
        }
        village.developmentPoints = clamp(
                village.developmentPoints + population * productivity * 0.045,
                0.0,
                1_000_000.0);

        double housingRatio = village.housingCapacity <= 0
                ? 0.0
                : Math.min(1.25, village.housingCapacity / (double) Math.max(1, village.population));
        double foodDays = village.foodSupply / Math.max(1.0, population * 0.46);
        double targetProsperity = 18.0
                + 28.0 * clamp(housingRatio, 0.0, 1.0)
                + 24.0 * clamp(foodDays / 30.0, 0.0, 1.0)
                + 22.0 * clamp(village.safety / 100.0, 0.0, 1.0)
                + Math.min(8.0, village.developmentTier * 1.6);
        village.prosperity = approach(village.prosperity, targetProsperity, 0.08);

        if (day - village.lastIncidentDay > 7L) {
            village.safety = clamp(village.safety + 0.035 + 0.005 * village.securityOutput,
                    0.0, 100.0);
        }

        updateLifecycle(village, day);
        advanceProjects(village, worldSeed, day, productivity);
        maybeGrowPopulation(village, worldSeed, day, requirePhysicalSettlers);
        maybeApproveProject(village, worldSeed, day);
        updateDevelopmentTier(village);
    }

    private static void advanceRecovery(
            EconomyState.VillageRecord village,
            long day,
            boolean automaticRecovery,
            boolean requirePhysicalSettlers) {
        if (!automaticRecovery) {
            return;
        }
        if (village.lifecycle == Lifecycle.ABANDONED) {
            if (!village.restorationFunded
                    || village.restorationFund < RESTORATION_EMERALD_TARGET
                    || day < village.recoveryEligibleDay) {
                return;
            }
        } else if (village.lifecycle != Lifecycle.EXTINCT || day < village.recoveryEligibleDay) {
            return;
        }

        if (requirePhysicalSettlers) {
            // Queue physical settlers first. Productive abstract population remains zero until
            // a loaded-world census observes the spawned residents.
            village.population = 0;
            village.pendingSettlers = Math.max(village.pendingSettlers, 2);
        } else {
            // Simulation-only mode has no physical representation to wait for.
            village.population = 2;
            village.pendingSettlers = 0;
        }
        village.housingCapacity = Math.max(village.housingCapacity, 4);
        village.foodSupply = Math.max(village.foodSupply, 60.0);
        village.materialSupply = Math.max(village.materialSupply, 20.0);
        village.safety = Math.max(village.safety, 42.0);
        village.prosperity = Math.max(village.prosperity, 24.0);
        village.lifecycle = Lifecycle.RECOVERING;
        village.restorationFunded = false;
        village.restorationFund = 0.0;
    }

    private static void updateLifecycle(EconomyState.VillageRecord village, long day) {
        if (village.population <= 0) {
            return;
        }
        long sinceIncident = day - village.lastIncidentDay;
        if (village.lifecycle == Lifecycle.RECOVERING) {
            if (village.population >= 4 && village.safety >= 50.0 && village.prosperity >= 35.0) {
                village.lifecycle = Lifecycle.ACTIVE;
            }
            return;
        }
        if (sinceIncident <= 7L || village.safety < 30.0) {
            village.lifecycle = Lifecycle.THREATENED;
        } else if (village.prosperity < 25.0 || village.population <= 2) {
            village.lifecycle = Lifecycle.DEVASTATED;
        } else {
            village.lifecycle = Lifecycle.ACTIVE;
        }
    }

    private static void maybeGrowPopulation(
            EconomyState.VillageRecord village,
            long worldSeed,
            long day,
            boolean requirePhysicalSettlers) {
        if (village.lifecycle != Lifecycle.ACTIVE && village.lifecycle != Lifecycle.RECOVERING) {
            return;
        }
        int committedPopulation = village.population
                + (requirePhysicalSettlers ? village.pendingSettlers : 0);
        if (committedPopulation >= MAX_ABSTRACT_POPULATION
                || committedPopulation >= village.housingCapacity
                || village.foodSupply < Math.max(1, village.population) * 10.0
                || village.safety < 45.0) {
            return;
        }
        double baseChance = 0.0014
                + 0.0012 * clamp(village.prosperity / 100.0, 0.0, 1.0)
                + 0.0006 * clamp((village.housingCapacity - committedPopulation) / 8.0, 0.0, 1.0);
        double draw = unit(worldSeed, village.villageId, day, GROWTH_SALT);
        if (draw < baseChance) {
            if (requirePhysicalSettlers) {
                // In visual worlds, a population increase becomes real only after a settler entity
                // is safely materialized and observed by a loaded-world census.
                village.pendingSettlers = Math.min(
                        MAX_ABSTRACT_POPULATION - village.population,
                        village.pendingSettlers + 1);
            } else {
                village.population++;
            }
            village.foodSupply = Math.max(0.0, village.foodSupply - 6.0);
        }
    }

    private static void advanceProjects(
            EconomyState.VillageRecord village,
            long worldSeed,
            long day,
            double productivity) {
        EconomyState.VillageProject active = village.projects.stream()
                .filter(project -> !project.economicComplete)
                .findFirst()
                .orElse(null);
        if (active == null) {
            return;
        }
        double workforce = Math.max(1.0, village.population * 0.15 * productivity);
        double randomFactor = 0.85 + 0.30 * unit(
                worldSeed, village.villageId, day, PROJECT_SALT ^ active.projectId);
        double denominator = switch (active.type) {
            case COTTAGE -> 120.0;
            case WAREHOUSE -> 190.0;
            case MINE_ENTRANCE -> 220.0;
        };
        double developmentSpend = Math.min(village.developmentPoints, 0.75);
        village.developmentPoints -= developmentSpend;
        active.economicProgress = clamp(
                active.economicProgress
                        + workforce * randomFactor / denominator
                        + developmentSpend / denominator,
                0.0,
                1.0);
        if (active.economicProgress >= 1.0) {
            active.economicComplete = true;
            active.completedDay = day;
            village.housingCapacity += active.type.housingGain();
            compressVisualBacklog(village);
        }
    }

    private static void maybeApproveProject(
            EconomyState.VillageRecord village, long worldSeed, long day) {
        if (village.projects.size() >= MAX_PROJECTS_PER_VILLAGE
                || village.projects.stream().anyMatch(project -> !project.economicComplete)) {
            return;
        }

        ProjectType desired = null;
        int cottageCount = countProjects(village, ProjectType.COTTAGE);
        boolean hasWarehouse = countProjects(village, ProjectType.WAREHOUSE) > 0;
        boolean hasMine = countProjects(village, ProjectType.MINE_ENTRANCE) > 0;
        if (village.population + village.pendingSettlers >= village.housingCapacity - 1
                && cottageCount < 5) {
            desired = ProjectType.COTTAGE;
        } else if (!hasWarehouse
                && village.population >= 6
                && village.prosperity >= 42.0) {
            desired = ProjectType.WAREHOUSE;
        } else if (!hasMine
                && village.population >= 5
                && village.developmentTier >= 1) {
            desired = ProjectType.MINE_ENTRANCE;
        } else if (cottageCount < 5
                && village.population >= village.housingCapacity - 2
                && unit(worldSeed, village.villageId, day, PROJECT_SALT) < 0.012) {
            desired = ProjectType.COTTAGE;
        }

        double requiredDevelopment = switch (desired == null
                ? ProjectType.COTTAGE
                : desired) {
            case COTTAGE -> 8.0;
            case WAREHOUSE -> 14.0;
            case MINE_ENTRANCE -> 16.0;
        };
        if (desired == null
                || village.materialSupply < desired.materialCost()
                || village.treasury < desired.treasuryCost()
                || village.developmentPoints < requiredDevelopment) {
            return;
        }
        village.materialSupply -= desired.materialCost();
        village.treasury -= desired.treasuryCost();
        village.developmentPoints -= requiredDevelopment;
        EconomyState.VillageProject project = new EconomyState.VillageProject();
        project.projectId = ++village.projectSerial;
        project.type = desired;
        project.approvedDay = day;
        project.totalBlocks = desired.nominalBlocks();
        village.projects.add(project);
    }

    private static void compressVisualBacklog(EconomyState.VillageRecord village) {
        long backlog = village.projects.stream()
                .filter(project -> project.economicComplete
                        && !project.materializedComplete
                        && !project.abstractOnly)
                .count();
        if (backlog <= 6) {
            return;
        }
        village.projects.stream()
                .filter(project -> project.economicComplete
                        && !project.materializedComplete
                        && !project.abstractOnly
                        && project.type == ProjectType.COTTAGE)
                .findFirst()
                .ifPresent(project -> project.abstractOnly = true);
    }

    private static void updateDevelopmentTier(EconomyState.VillageRecord village) {
        int completed = (int) village.projects.stream()
                .filter(project -> project.economicComplete)
                .count();
        boolean warehouse = completedProjects(village, ProjectType.WAREHOUSE) > 0;
        boolean mine = completedProjects(village, ProjectType.MINE_ENTRANCE) > 0;
        int tier;
        if (village.population <= 0) {
            tier = 0;
        } else if (village.population >= 28 && village.prosperity >= 75.0 && completed >= 6) {
            tier = 5;
        } else if (village.population >= 18 && village.prosperity >= 65.0 && completed >= 4) {
            tier = 4;
        } else if (village.population >= 12 && village.prosperity >= 55.0 && warehouse && mine) {
            tier = 3;
        } else if (village.population >= 8 && warehouse) {
            tier = 2;
        } else if (village.population >= 5 || completed > 0) {
            tier = 1;
        } else {
            tier = 0;
        }
        // Completed structures stay materialized, but the functional tier may decline.
        village.developmentTier = tier;
    }

    /**
     * Advances only the visible development queue from one loaded-world pulse. This mode is used
     * when abstract village simulation is disabled but visual progression remains enabled.
     */
    public static void advanceVisualOnlyPulse(
            EconomyState.VillageRecord village, long worldSeed, long day) {
        if (village == null) {
            return;
        }
        village.lastSimulatedDay = Math.max(village.lastSimulatedDay, day);
        if (village.population <= 0) {
            advanceRecovery(village, day, true, true);
            return;
        }
        village.developmentPoints = clamp(
                village.developmentPoints + Math.max(0.25, village.observedPopulation * 0.12),
                0.0,
                1_000_000.0);
        village.materialSupply = clamp(
                village.materialSupply + Math.max(0.5, village.observedPopulation * 0.16),
                0.0,
                20_000.0);
        village.treasury = clamp(
                village.treasury + Math.max(0.05, village.observedPopulation * 0.015),
                0.0,
                1_000_000.0);
        double productivity = clamp(0.45 + village.safety / 180.0, 0.45, 1.0);
        advanceProjects(village, worldSeed, day, productivity);
        maybeApproveProject(village, worldSeed, day);
        updateDevelopmentTier(village);
    }

    public static VillageFundamentals aggregateFundamentals(
            Collection<EconomyState.VillageRecord> villages, long day) {
        if (villages == null || villages.isEmpty()) {
            return VillageFundamentals.neutral();
        }
        double broad = 0.0;
        double mining = 0.0;
        double agriculture = 0.0;
        double trade = 0.0;
        double redstone = 0.0;
        double alchemy = 0.0;
        double transport = 0.0;
        double security = 0.0;
        double totalWeight = 0.0;
        int eligible = 0;

        for (EconomyState.VillageRecord village : villages) {
            if (village == null
                    || village.population <= 0
                    || day < village.marketSuppressedUntilDay
                    || village.lifecycle == Lifecycle.ABANDONED
                    || village.lifecycle == Lifecycle.EXTINCT) {
                continue;
            }
            double rawWeight = StrictMath.sqrt(Math.max(1.0, village.population));
            double weight = Math.min(6.0, rawWeight);
            double population = Math.max(1.0, village.population);
            double broadScore = clamp(
                    (village.prosperity - 50.0) / 50.0 * 0.55
                            + (village.safety - 55.0) / 45.0 * 0.35
                            + (village.developmentTier - 1.0) / 5.0 * 0.10,
                    -1.0,
                    1.0);
            broad += broadScore * weight;
            mining += outputScore(village.miningOutput / population, 0.18) * weight;
            agriculture += outputScore(village.agricultureOutput / population, 0.48) * weight;
            trade += outputScore(village.tradeOutput / population, 0.15) * weight;
            redstone += outputScore(village.redstoneOutput / population, 0.02) * weight;
            alchemy += outputScore(village.alchemyOutput / population, 0.015) * weight;
            transport += outputScore(village.transportOutput / population, 0.085) * weight;
            security += outputScore(village.securityOutput / population, 0.075) * weight;
            totalWeight += weight;
            eligible++;
        }
        if (totalWeight <= 0.0) {
            return VillageFundamentals.neutral();
        }
        return new VillageFundamentals(
                clamp(broad / totalWeight, -1.0, 1.0),
                clamp(mining / totalWeight, -1.0, 1.0),
                clamp(agriculture / totalWeight, -1.0, 1.0),
                clamp(trade / totalWeight, -1.0, 1.0),
                clamp(redstone / totalWeight, -1.0, 1.0),
                clamp(alchemy / totalWeight, -1.0, 1.0),
                clamp(transport / totalWeight, -1.0, 1.0),
                clamp(security / totalWeight, -1.0, 1.0),
                eligible);
    }

    /** Annual drift contribution. It is intentionally capped so world activity cannot guarantee returns. */
    public static double assetAnnualDrift(String ticker, VillageFundamentals fundamentals) {
        if (fundamentals == null || fundamentals.eligibleVillages() == 0) {
            return 0.0;
        }
        String normalized = ticker == null ? "" : ticker.toUpperCase(Locale.ROOT);
        double drift = switch (normalized) {
            case "VILX" -> 0.004 * fundamentals.broad();
            case "RSDN" -> 0.010 * fundamentals.redstone() + 0.002 * fundamentals.broad();
            case "DPMN" -> 0.011 * fundamentals.mining() + 0.001 * fundamentals.trade();
            case "NSPC" -> 0.009 * fundamentals.trade() + 0.002 * fundamentals.broad();
            case "ENDR" -> 0.008 * fundamentals.transport() + 0.003 * fundamentals.trade();
            case "GLDH" -> 0.010 * fundamentals.agriculture();
            case "POTN" -> 0.010 * fundamentals.alchemy() + 0.001 * fundamentals.trade();
            case "IRNG" -> 0.010 * fundamentals.security() - 0.002 * fundamentals.broad();
            case "MCRT" -> 0.010 * fundamentals.transport() + 0.002 * fundamentals.broad();
            default -> 0.0;
        };
        return clamp(drift, -0.012, 0.012);
    }

    /** Positive values represent additional supply and therefore downward commodity pressure. */
    public static double commodityAnnualSupplyPressure(
            String commodityId, VillageFundamentals fundamentals) {
        if (fundamentals == null || fundamentals.eligibleVillages() == 0) {
            return 0.0;
        }
        return switch (commodityId) {
            case "diamond" -> clamp(0.008 * fundamentals.mining(), -0.008, 0.008);
            case "gold" -> clamp(0.005 * fundamentals.mining() - 0.002 * fundamentals.trade(), -0.007, 0.007);
            case "netherite" -> clamp(0.004 * fundamentals.trade(), -0.006, 0.006);
            case "emerald_ore" -> clamp(0.004 * fundamentals.mining(), -0.005, 0.005);
            default -> 0.0;
        };
    }

    public static int recoveryDelayDays(EconomyState.VillageRecord village, IncidentCause cause) {
        if (cause == IncidentCause.PLAYER) {
            return Integer.MAX_VALUE;
        }
        return switch (Math.min(3, Math.max(1, village.collapseCount))) {
            case 1 -> 7;
            case 2 -> 20;
            default -> 60;
        };
    }

    private static int countProjects(EconomyState.VillageRecord village, ProjectType type) {
        return (int) village.projects.stream().filter(project -> project.type == type).count();
    }

    private static int completedProjects(EconomyState.VillageRecord village, ProjectType type) {
        return (int) village.projects.stream()
                .filter(project -> project.type == type && project.economicComplete)
                .count();
    }

    private static double outputScore(double actual, double baseline) {
        if (baseline <= 0.0) {
            return 0.0;
        }
        return clamp((actual / baseline - 1.0) / 1.5, -1.0, 1.0);
    }

    private static double approach(double current, double target, double speed) {
        return clamp(current + (target - current) * speed, 0.0, 100.0);
    }

    private static double unit(long seed, UUID villageId, long day, long salt) {
        long input = seed
                ^ villageId.getMostSignificantBits()
                ^ Long.rotateLeft(villageId.getLeastSignificantBits(), 17)
                ^ Long.rotateLeft(day, 29)
                ^ salt;
        return (mix64(input) >>> 11) * 0x1.0p-53;
    }

    private static long mix64(long value) {
        value ^= value >>> 30;
        value *= 0xBF58476D1CE4E5B9L;
        value ^= value >>> 27;
        value *= 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
