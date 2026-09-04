package com.chedidandrew.emeraldstandard.core;

import java.util.Collection;
import java.util.Comparator;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Loader-neutral village prosperity, development, and market-fundamentals simulation. */
public final class VillageProsperityEngine {
    public static final int MAX_ABSTRACT_POPULATION = 64;
    public static final int MAX_PROJECTS_PER_VILLAGE = 12;
    public static final int INCIDENT_HISTORY_LIMIT = 16;
    public static final int RESIDENT_HISTORY_LIMIT = 128;
    public static final int MARKET_SHADOW_FORMULA_VERSION = 1;
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
        MINE_ENTRANCE(160.0, 25.0, 0, 230),
        HOUSE(120.0, 14.0, 6, 250),
        INN(190.0, 28.0, 8, 360),
        MARKET_SQUARE(175.0, 32.0, 0, 260),
        SMITHY(185.0, 30.0, 0, 285),
        GRANARY(145.0, 22.0, 0, 245),
        GUARD_POST(165.0, 26.0, 0, 240),
        EXCHANGE_HALL(280.0, 60.0, 0, 430);

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

    /**
     * Advances one day and optionally requires loaded-world materialization. In a visual world,
     * settlers and completed projects become economically authoritative only after their entity or
     * structure has actually appeared. Simulation-only worlds retain the fully abstract behavior.
     */
    public static void advanceOneDay(
            EconomyState.VillageRecord village,
            long worldSeed,
            long day,
            boolean automaticRecovery,
            boolean requirePhysicalWorld) {
        if (village == null || village.villageId == null) {
            return;
        }
        village.lastSimulatedDay = day;
        normalizeProjectAuthority(village, requirePhysicalWorld);

        if (village.population <= 0) {
            village.population = 0;
            village.agricultureOutput = 0.0;
            village.miningOutput = 0.0;
            village.tradeOutput = 0.0;
            village.redstoneOutput = 0.0;
            village.alchemyOutput = 0.0;
            village.transportOutput = 0.0;
            village.securityOutput = 0.0;
            advanceRecovery(village, day, automaticRecovery, requirePhysicalWorld);
            updateDevelopmentTier(village);
            return;
        }

        if (village.lifecycle == Lifecycle.EXTINCT || village.lifecycle == Lifecycle.ABANDONED) {
            village.lifecycle = Lifecycle.RECOVERING;
        }

        int cottages = completedProjects(village, ProjectType.COTTAGE);
        int houses = completedProjects(village, ProjectType.HOUSE);
        int inns = completedProjects(village, ProjectType.INN);
        int warehouses = completedProjects(village, ProjectType.WAREHOUSE);
        int mines = completedProjects(village, ProjectType.MINE_ENTRANCE);
        int markets = completedProjects(village, ProjectType.MARKET_SQUARE);
        int smithies = completedProjects(village, ProjectType.SMITHY);
        int granaries = completedProjects(village, ProjectType.GRANARY);
        int guardPosts = completedProjects(village, ProjectType.GUARD_POST);
        int exchanges = completedProjects(village, ProjectType.EXCHANGE_HALL);
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
        ProfessionMultipliers profession = professionMultipliers(village);

        village.agricultureOutput = population * 0.58 * productivity
                * (1.0 + 0.04 * village.developmentTier + 0.16 * granaries)
                * profession.agriculture();
        village.miningOutput = population * 0.22 * productivity
                * (1.0 + 0.32 * mines + 0.18 * smithies)
                * profession.mining();
        village.tradeOutput = population * 0.18 * productivity
                * (1.0 + 0.22 * warehouses + 0.20 * markets + 0.16 * inns + 0.22 * exchanges)
                * profession.trade();
        village.redstoneOutput = population * 0.018 * productivity
                * Math.max(0, village.developmentTier - 1)
                * profession.redstone();
        village.alchemyOutput = population * 0.015 * productivity
                * Math.max(0, village.developmentTier - 1)
                * profession.alchemy();
        village.transportOutput = population * 0.10 * productivity
                * (1.0 + 0.15 * warehouses + 0.12 * markets + 0.10 * exchanges)
                * profession.transport();
        village.securityOutput = population * 0.08
                * (1.0 + 0.35 * guardPosts)
                * clamp(0.5 + village.safety / 100.0, 0.4, 1.5)
                * profession.security();

        double foodUse = population * 0.46;
        double foodSpoilage = village.foodSupply * 0.0008;
        double infrastructureUpkeep = cottages * 0.03
                + houses * 0.05
                + inns * 0.08
                + warehouses * 0.08
                + mines * 0.10
                + markets * 0.07
                + smithies * 0.09
                + granaries * 0.05
                + guardPosts * 0.08
                + exchanges * 0.12;
        double materialUpkeep = (cottages + houses + inns + warehouses + mines + markets
                + smithies + granaries + guardPosts + exchanges) * 0.015;
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

        int effectiveHousing = effectiveHousingCapacity(village);
        double housingRatio = effectiveHousing <= 0
                ? 0.0
                : Math.min(1.25, effectiveHousing / (double) Math.max(1, village.population));
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
        advanceProjects(village, worldSeed, day, productivity, requirePhysicalWorld);
        maybeGrowPopulation(village, worldSeed, day, requirePhysicalWorld);
        maybeApproveProject(village, worldSeed, day);
        updateDevelopmentTier(village);
    }

    private static void advanceRecovery(
            EconomyState.VillageRecord village,
            long day,
            boolean automaticRecovery,
            boolean requirePhysicalWorld) {
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

        if (requirePhysicalWorld) {
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
        if ((village.lifecycle == Lifecycle.DEVASTATED
                        || village.lifecycle == Lifecycle.THREATENED)
                && village.population <= 2
                && sinceIncident > 7L
                && village.safety >= 35.0
                && village.prosperity >= 20.0) {
            // A settlement with living survivors must have a path back. RECOVERING enables
            // bounded population growth; fully extinct player-caused settlements still require
            // explicit restoration through the separate zero-population recovery rules.
            village.lifecycle = Lifecycle.RECOVERING;
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
            boolean requirePhysicalWorld) {
        if (village.lifecycle != Lifecycle.ACTIVE && village.lifecycle != Lifecycle.RECOVERING) {
            return;
        }
        int committedPopulation = village.population
                + (requirePhysicalWorld ? village.pendingSettlers : 0);
        int effectiveHousing = effectiveHousingCapacity(village);
        if (committedPopulation >= MAX_ABSTRACT_POPULATION
                || committedPopulation >= effectiveHousing
                || village.foodSupply < Math.max(1, village.population) * 10.0
                || village.safety < 45.0) {
            return;
        }
        double baseChance = 0.0014
                + 0.0012 * clamp(village.prosperity / 100.0, 0.0, 1.0)
                + 0.0006 * clamp((effectiveHousing - committedPopulation) / 8.0, 0.0, 1.0);
        double draw = unit(worldSeed, village.villageId, day, GROWTH_SALT);
        if (draw < baseChance) {
            if (requirePhysicalWorld) {
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
            double productivity,
            boolean requirePhysicalWorld) {
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
            case HOUSE -> 165.0;
            case INN -> 235.0;
            case WAREHOUSE -> 190.0;
            case MINE_ENTRANCE -> 220.0;
            case MARKET_SQUARE -> 205.0;
            case SMITHY -> 215.0;
            case GRANARY -> 180.0;
            case GUARD_POST -> 185.0;
            case EXCHANGE_HALL -> 285.0;
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
            // An abstract simulation has no block world to wait for. Marking the project as
            // abstract-only makes its effects available while ensuring it can never later enter
            // the physical construction queue. Visual worlds wait for materializedComplete.
            active.abstractOnly = !requirePhysicalWorld;
        }
    }

    private static void maybeApproveProject(
            EconomyState.VillageRecord village, long worldSeed, long day) {
        if (village.projects.size() >= MAX_PROJECTS_PER_VILLAGE
                || village.projects.stream().anyMatch(project -> !project.economicComplete)) {
            return;
        }

        ProjectType desired = null;
        int housingProjects = countProjects(village, ProjectType.COTTAGE)
                + countProjects(village, ProjectType.HOUSE)
                + countProjects(village, ProjectType.INN);
        boolean hasWarehouse = countProjects(village, ProjectType.WAREHOUSE) > 0;
        boolean hasMine = countProjects(village, ProjectType.MINE_ENTRANCE) > 0;
        boolean hasMarket = countProjects(village, ProjectType.MARKET_SQUARE) > 0;
        boolean hasSmithy = countProjects(village, ProjectType.SMITHY) > 0;
        boolean hasGranary = countProjects(village, ProjectType.GRANARY) > 0;
        boolean hasGuardPost = countProjects(village, ProjectType.GUARD_POST) > 0;
        boolean hasExchange = countProjects(village, ProjectType.EXCHANGE_HALL) > 0;
        int committedPopulation = village.population + village.pendingSettlers;
        int effectiveHousing = effectiveHousingCapacity(village);
        double foodDays = village.foodSupply / Math.max(1.0, village.population * 0.46);

        // Local need wins over prestige. A village under pressure builds what solves its
        // current problem instead of following one fixed structure sequence.
        if ((village.lifecycle == Lifecycle.THREATENED || village.safety < 42.0)
                && !hasGuardPost && village.population >= 4) {
            desired = ProjectType.GUARD_POST;
        } else if (foodDays < 18.0 && !hasGranary && village.population >= 5) {
            desired = ProjectType.GRANARY;
        } else if (committedPopulation >= effectiveHousing - 1 && housingProjects < 6) {
            desired = village.developmentTier >= 3
                    ? ProjectType.INN
                    : village.developmentTier >= 2 ? ProjectType.HOUSE : ProjectType.COTTAGE;
        } else if (!hasWarehouse && village.population >= 6 && village.prosperity >= 42.0) {
            desired = ProjectType.WAREHOUSE;
        } else if (!hasMine && village.population >= 5 && village.developmentTier >= 1) {
            desired = ProjectType.MINE_ENTRANCE;
        } else if (!hasMarket && village.population >= 9 && village.prosperity >= 50.0) {
            desired = ProjectType.MARKET_SQUARE;
        } else if (!hasSmithy && village.population >= 10 && village.developmentTier >= 2) {
            desired = ProjectType.SMITHY;
        } else if (!hasExchange
                && village.population >= 18
                && village.developmentTier >= 4
                && village.prosperity >= 68.0) {
            desired = ProjectType.EXCHANGE_HALL;
        } else if (housingProjects < 6
                && committedPopulation >= effectiveHousing - 2
                && unit(worldSeed, village.villageId, day, PROJECT_SALT) < 0.012) {
            desired = village.developmentTier >= 2 ? ProjectType.HOUSE : ProjectType.COTTAGE;
        }

        double requiredDevelopment = switch (desired == null ? ProjectType.COTTAGE : desired) {
            case COTTAGE -> 8.0;
            case HOUSE -> 11.0;
            case INN -> 18.0;
            case WAREHOUSE -> 14.0;
            case MINE_ENTRANCE -> 16.0;
            case MARKET_SQUARE -> 17.0;
            case SMITHY -> 18.0;
            case GRANARY -> 13.0;
            case GUARD_POST -> 14.0;
            case EXCHANGE_HALL -> 24.0;
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

    private static void updateDevelopmentTier(EconomyState.VillageRecord village) {
        int completed = (int) village.projects.stream()
                .filter(VillageProsperityEngine::isProjectOperational)
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
        advanceProjects(village, worldSeed, day, productivity, true);
        maybeApproveProject(village, worldSeed, day);
        updateDevelopmentTier(village);
    }

    public static VillageFundamentals aggregateFundamentals(
            Collection<EconomyState.VillageRecord> villages, long day) {
        return aggregateFundamentals(villages, Map.of(), day);
    }

    /**
     * Aggregates live villages while substituting evolving no-player-damage counterfactuals for
     * villages recovering from player-caused damage.
     */
    public static VillageFundamentals aggregateFundamentals(
            Collection<EconomyState.VillageRecord> villages,
            Map<UUID, EconomyState.VillageMarketShadow> marketShadows,
            long day) {
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

        // Properties do not preserve insertion order after a reload. Stable UUID ordering keeps
        // floating-point aggregation bit-for-bit deterministic across save round trips.
        for (EconomyState.VillageRecord village : villages.stream()
                .filter(candidate -> candidate != null && candidate.villageId != null)
                .sorted(Comparator.comparing(candidate -> candidate.villageId))
                .toList()) {
            EconomyState.VillageMarketShadow shadow = marketShadows == null
                    ? null
                    : marketShadows.get(village.villageId);
            double weight;
            double broadScore;
            double miningScore;
            double agricultureScore;
            double tradeScore;
            double redstoneScore;
            double alchemyScore;
            double transportScore;
            double securityScore;
            if (shadow != null && shadow.present) {
                if (!shadow.contributionEligible) {
                    continue;
                }
                weight = shadow.weight;
                broadScore = shadow.broad;
                miningScore = shadow.mining;
                agricultureScore = shadow.agriculture;
                tradeScore = shadow.trade;
                redstoneScore = shadow.redstone;
                alchemyScore = shadow.alchemy;
                transportScore = shadow.transport;
                securityScore = shadow.security;
            } else {
                if (!isMarketEligible(village, day)) {
                    continue;
                }
                MarketContribution contribution = marketContribution(village);
                weight = contribution.weight();
                broadScore = contribution.broad();
                miningScore = contribution.mining();
                agricultureScore = contribution.agriculture();
                tradeScore = contribution.trade();
                redstoneScore = contribution.redstone();
                alchemyScore = contribution.alchemy();
                transportScore = contribution.transport();
                securityScore = contribution.security();
            }
            broad += broadScore * weight;
            mining += miningScore * weight;
            agriculture += agricultureScore * weight;
            trade += tradeScore * weight;
            redstone += redstoneScore * weight;
            alchemy += alchemyScore * weight;
            transport += transportScore * weight;
            security += securityScore * weight;
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

    /** Returns whether a village would contribute to market fundamentals without a shadow. */
    static boolean isMarketEligible(EconomyState.VillageRecord village, long day) {
        return village != null
                && village.population > 0
                && day >= village.marketSuppressedUntilDay
                && village.lifecycle != Lifecycle.ABANDONED
                && village.lifecycle != Lifecycle.EXTINCT;
    }

    /** Captures an exact, weighted market contribution before player damage is applied. */
    static EconomyState.VillageMarketShadow captureMarketShadow(
            EconomyState.VillageRecord village, long day, long cooldownDays) {
        if (!isMarketEligible(village, day)) {
            return null;
        }
        MarketContribution contribution = marketContribution(village);
        EconomyState.VillageMarketShadow shadow = new EconomyState.VillageMarketShadow();
        shadow.present = true;
        shadow.formulaVersion = MARKET_SHADOW_FORMULA_VERSION;
        shadow.contributionEligible = true;
        shadow.capturedDay = day;
        shadow.minimumReleaseDay = day > Long.MAX_VALUE - Math.max(0L, cooldownDays)
                ? Long.MAX_VALUE
                : day + Math.max(0L, cooldownDays);
        shadow.recoveryPopulation = village.population;
        shadow.weight = contribution.weight();
        shadow.broad = contribution.broad();
        shadow.mining = contribution.mining();
        shadow.agriculture = contribution.agriculture();
        shadow.trade = contribution.trade();
        shadow.redstone = contribution.redstone();
        shadow.alchemy = contribution.alchemy();
        shadow.transport = contribution.transport();
        shadow.security = contribution.security();
        shadow.counterfactualVillage = village.copy();
        return shadow;
    }

    /** Advances and re-prices the no-player-damage village state held by an active shadow. */
    static void advanceMarketShadow(
            EconomyState.VillageMarketShadow shadow,
            long worldSeed,
            long day,
            boolean automaticRecovery) {
        if (shadow == null || !shadow.present || shadow.counterfactualVillage == null) {
            return;
        }
        advanceOneDay(
                shadow.counterfactualVillage,
                worldSeed,
                day,
                automaticRecovery,
                false);
        refreshMarketShadow(shadow, day);
    }

    /** Recalculates the contribution after a genuine non-player incident. */
    static void refreshMarketShadow(EconomyState.VillageMarketShadow shadow, long day) {
        if (shadow == null || shadow.counterfactualVillage == null) {
            return;
        }
        EconomyState.VillageRecord village = shadow.counterfactualVillage;
        shadow.contributionEligible = isMarketEligible(village, day);
        if (!shadow.contributionEligible) {
            return;
        }
        MarketContribution contribution = marketContribution(village);
        shadow.weight = contribution.weight();
        shadow.broad = contribution.broad();
        shadow.mining = contribution.mining();
        shadow.agriculture = contribution.agriculture();
        shadow.trade = contribution.trade();
        shadow.redstone = contribution.redstone();
        shadow.alchemy = contribution.alchemy();
        shadow.transport = contribution.transport();
        shadow.security = contribution.security();
    }

    /** Confirms that persisted cached scores exactly match their counterfactual source record. */
    static boolean isMarketShadowCurrent(
            EconomyState.VillageMarketShadow shadow, long day) {
        if (shadow == null || shadow.counterfactualVillage == null) {
            return false;
        }
        boolean eligible = isMarketEligible(shadow.counterfactualVillage, day);
        if (eligible != shadow.contributionEligible) {
            return false;
        }
        if (!eligible) {
            return true;
        }
        MarketContribution contribution = marketContribution(shadow.counterfactualVillage);
        return sameBits(shadow.weight, contribution.weight())
                && sameBits(shadow.broad, contribution.broad())
                && sameBits(shadow.mining, contribution.mining())
                && sameBits(shadow.agriculture, contribution.agriculture())
                && sameBits(shadow.trade, contribution.trade())
                && sameBits(shadow.redstone, contribution.redstone())
                && sameBits(shadow.alchemy, contribution.alchemy())
                && sameBits(shadow.transport, contribution.transport())
                && sameBits(shadow.security, contribution.security());
    }

    private static boolean sameBits(double first, double second) {
        return Double.doubleToLongBits(first) == Double.doubleToLongBits(second);
    }

    /** Qualitative broad village score used by the market and player-facing outlook. */
    public static double broadFundamentalScore(
            double prosperity, double safety, int developmentTier) {
        return clamp(
                (prosperity - 50.0) / 50.0 * 0.55
                        + (safety - 55.0) / 45.0 * 0.35
                        + (developmentTier - 1.0) / 5.0 * 0.10,
                -1.0,
                1.0);
    }

    private static MarketContribution marketContribution(EconomyState.VillageRecord village) {
        double population = Math.max(1.0, village.population);
        double weight = Math.min(6.0, StrictMath.sqrt(population));
        double broad = broadFundamentalScore(
                village.prosperity, village.safety, village.developmentTier);
        return new MarketContribution(
                weight,
                broad,
                outputScore(village.miningOutput / population, 0.18),
                outputScore(village.agricultureOutput / population, 0.48),
                outputScore(village.tradeOutput / population, 0.15),
                outputScore(village.redstoneOutput / population, 0.02),
                outputScore(village.alchemyOutput / population, 0.015),
                outputScore(village.transportOutput / population, 0.085),
                outputScore(village.securityOutput / population, 0.075));
    }

    private record MarketContribution(
            double weight,
            double broad,
            double mining,
            double agriculture,
            double trade,
            double redstone,
            double alchemy,
            double transport,
            double security) {}

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

    private static ProfessionMultipliers professionMultipliers(
            EconomyState.VillageRecord village) {
        double agriculture = 0.0;
        double mining = 0.0;
        double trade = 0.0;
        double redstone = 0.0;
        double alchemy = 0.0;
        double transport = 0.0;
        double security = 0.0;
        for (EconomyState.ResidentRecord resident : village.residents.values()) {
            if (resident == null || resident.status != ResidentStatus.ACTIVE) {
                continue;
            }
            String profession = resident.profession == null
                    ? ""
                    : resident.profession.toLowerCase(Locale.ROOT);
            int namespace = profession.lastIndexOf(':');
            if (namespace >= 0) {
                profession = profession.substring(namespace + 1);
            }
            switch (profession) {
                case "farmer", "fisherman", "shepherd", "butcher" -> agriculture += 1.0;
                case "armorer", "toolsmith", "weaponsmith", "mason" -> mining += 1.0;
                case "cartographer" -> {
                    trade += 0.7;
                    transport += 1.0;
                }
                case "fletcher", "leatherworker", "banker" -> trade += 1.0;
                case "librarian" -> {
                    trade += 0.4;
                    redstone += 1.0;
                }
                case "cleric" -> alchemy += 1.0;
                default -> {
                    // Unemployed, nitwit, and modded professions keep the calibrated baseline.
                }
            }
            if (profession.equals("armorer")
                    || profession.equals("weaponsmith")
                    || profession.equals("fletcher")) {
                security += 1.0;
            }
        }
        double population = Math.max(1.0, village.population);
        return new ProfessionMultipliers(
                sectorMultiplier(agriculture, population),
                sectorMultiplier(mining, population),
                sectorMultiplier(trade, population),
                sectorMultiplier(redstone, population),
                sectorMultiplier(alchemy, population),
                sectorMultiplier(transport, population),
                sectorMultiplier(security, population));
    }

    private static double sectorMultiplier(double relevantWorkers, double population) {
        // A fully specialized workforce is noticeable, but cannot add more than 12% output.
        return 1.0 + Math.min(0.12, 0.30 * Math.max(0.0, relevantWorkers) / population);
    }

    private record ProfessionMultipliers(
            double agriculture,
            double mining,
            double trade,
            double redstone,
            double alchemy,
            double transport,
            double security) {
    }

    private static int completedProjects(EconomyState.VillageRecord village, ProjectType type) {
        return (int) village.projects.stream()
                .filter(project -> project.type == type && isProjectOperational(project))
                .count();
    }

    /**
     * Returns the capacity that may actually support residents. The persisted capacity continues
     * to include promised housing from older saves, while unfinished physical projects are
     * subtracted until their authored structures have been verified in the loaded world.
     */
    public static int effectiveHousingCapacity(EconomyState.VillageRecord village) {
        if (village == null) {
            return 0;
        }
        long unavailable = village.projects.stream()
                .filter(project -> project != null
                        && project.economicComplete
                        && !project.abstractOnly
                        && !project.materializedComplete)
                .mapToLong(project -> Math.max(0, project.type.housingGain()))
                .sum();
        long effective = Math.max(0L, (long) village.housingCapacity - unavailable);
        return (int) Math.min(Integer.MAX_VALUE, effective);
    }

    /** A project affects housing, production, upkeep, tiers, and markets only when operational. */
    public static boolean isProjectOperational(EconomyState.VillageProject project) {
        return project != null
                && project.economicComplete
                && (project.abstractOnly || project.materializedComplete);
    }

    /**
     * Migrates completed backlog records when the world switches modes. Visual mode removes old
     * abstract-backlog shortcuts so every benefit again waits for a verified structure; disabling
     * visuals converts that queue to simulation-only authority without stranding the settlement.
     */
    private static void normalizeProjectAuthority(
            EconomyState.VillageRecord village, boolean requirePhysicalWorld) {
        for (EconomyState.VillageProject project : village.projects) {
            if (project != null && project.economicComplete && !project.materializedComplete) {
                project.abstractOnly = !requirePhysicalWorld;
            }
        }
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
