package com.chedidandrew.emeraldstandard.core;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Open-ended cities made of bounded, independently materialized districts. */
public final class VillageExpansion {
    public enum Mode { AUTOMATIC, APPROVAL, PAUSED }
    public enum Reason { READY, PAUSED, APPROVAL, MATURING, STABILIZING, FOOD, MATERIALS, TREASURY, UPKEEP, CONSTRUCTION, DISABLED, CATCHING_UP }
    public static final double CHARTER_FOOD = 200, CHARTER_MATERIALS = 400, CHARTER_TREASURY = 80;
    private VillageExpansion() { }
    public static UUID rootId(EconomyState.VillageRecord v) { return v.cityId == null ? v.villageId : v.cityId; }

    static void prepareDay(EconomyState state) {
        Map<UUID, Integer> counts = new HashMap<>();
        for (var v : state.villages.values()) counts.merge(rootId(v), 1, Integer::sum);
        for (var v : state.villages.values()) {
            v.cityDistrictCount = counts.getOrDefault(rootId(v), 1);
            v.expansionWaitingForHome = false;
            v.cityUpkeepDeficit = false;
            var root = state.villages.get(rootId(v));
            if (root != null && v.cityId != null) v.expansionMode = root.expansionMode;
        }
        for (var v : state.villages.values()) {
            var root = state.villages.get(rootId(v));
            if (root == null) continue;
            if (v.cityId != null && v.districtFounding && v.projects.stream().noneMatch(
                    p -> (p.materializedComplete && p.type.housingGain() > 0)
                            || p.foundingRecoveryUsed || p.obstructionLoadedTicks >= 6_000))
                root.expansionWaitingForHome = true;
            root.cityUpkeepDeficit |= v.expansionUpkeepShortfalls >= 3;
        }
        for (var shadow : state.villageMarketShadows.values()) {
            if (shadow.counterfactualVillage == null) continue;
            var actual = state.villages.get(shadow.counterfactualVillage.villageId);
            if (actual != null) {
                shadow.counterfactualVillage.cityDistrictCount = actual.cityDistrictCount;
                shadow.counterfactualVillage.expansionMode = actual.expansionMode;
            }
        }
    }

    /** Share municipal supplies only; never touch account balances or earmarked fund principal. */
    static void shareSupplies(EconomyState state, boolean peaceful) {
        var districts = state.villages.values().stream().filter(v -> v.cityId != null)
                .sorted(java.util.Comparator.comparing(v -> v.villageId)).toList();
        for (int pass = 0; pass < 2; pass++) {
            for (int i = 0; i < districts.size(); i++) {
                var v = districts.get((int) ((i + state.economicDay % districts.size()) % districts.size()));
                var root = state.villages.get(v.cityId);
                if (root == null) continue;
                double food, materials, treasury;
                if (pass == 0) {
                    food = Math.min(Math.max(0, v.foodSupply - Math.max(400, economicFood(v) * 2)), Math.max(0, 20_000 - root.foodSupply));
                    materials = Math.min(Math.max(0, v.materialSupply - 600), Math.max(0, 20_000 - root.materialSupply));
                    treasury = Math.min(Math.max(0, v.treasury - Math.max(160, overhead(v, peaceful) * 12 + 20)), Math.max(0, 1_000_000 - root.treasury));
                } else {
                    food = -Math.min(Math.max(0, economicFood(v) - v.foodSupply), Math.max(0, root.foodSupply - economicFood(root)));
                    materials = -Math.min(Math.max(0, 80 - v.materialSupply), Math.max(0, root.materialSupply - 80));
                    treasury = -Math.min(Math.max(0, 20 + overhead(v, peaceful) * 12 - v.treasury),
                            Math.max(0, root.treasury - 20 - overhead(root, peaceful) * 12));
                }
                v.foodSupply -= food; root.foodSupply += food;
                v.materialSupply -= materials; root.materialSupply += materials;
                v.treasury -= treasury; root.treasury += treasury;
            }
        }
    }
    private static double economicFood(EconomyState.VillageRecord v) {
        return Math.max(40, VillageProsperityEngine.economicPopulation(v) * 5.0);
    }

    public static double overhead(EconomyState.VillageRecord v, boolean peaceful) {
        return Math.pow(Math.max(0, v.cityDistrictCount - 1), 1.18) * (peaceful ? .035 : .065);
    }

    static void finishDay(EconomyState.VillageRecord v, boolean peaceful) {
        double due = overhead(v, peaceful);
        boolean deficit = v.treasury < due;
        v.treasury = Math.max(0, v.treasury - due);
        v.expansionUpkeepShortfalls = deficit ? Math.min(30, v.expansionUpkeepShortfalls + 1) : 0;
        if (deficit) v.prosperity = Math.max(0, v.prosperity - 2.5);
        if (v.population > 0) v.districtFounding = false;
        boolean healthy = v.prosperity >= (peaceful ? 65 : 70) && v.safety >= 45
                && v.foodSupply >= Math.max(40, VillageProsperityEngine.economicPopulation(v) * 5.0) && !deficit;
        v.expansionHealthyDays = healthy ? Math.min(30, v.expansionHealthyDays + 1) : 0;
    }

    public static Reason reason(EconomyState.VillageRecord v, long day, boolean peaceful) {
        if (v.expansionMode == Mode.PAUSED) return Reason.PAUSED;
        if (v.cityUpkeepDeficit) return Reason.UPKEEP;
        if (v.developmentTier < 3 || v.expansionHealthyDays < (peaceful ? 2 : 3)) return Reason.MATURING;
        if (day - v.lastExpansionDay < (peaceful ? 3 : 6)) return Reason.STABILIZING;
        if (v.expansionWaitingForHome) return Reason.CONSTRUCTION;
        if (v.foodSupply < CHARTER_FOOD + VillageProsperityEngine.economicPopulation(v) * 5.0) return Reason.FOOD;
        if (v.materialSupply < CHARTER_MATERIALS + 40) return Reason.MATERIALS;
        if (v.treasury < CHARTER_TREASURY + 20) return Reason.TREASURY;
        double income = v.tradeOutput * .055 + v.transportOutput * .015;
        double nextUpkeep = Math.pow(Math.max(1, v.cityDistrictCount), 1.18) * (peaceful ? .035 : .065);
        if (v.treasury - CHARTER_TREASURY < 12 * Math.max(1, nextUpkeep - income)) return Reason.UPKEEP;
        if (v.expansionMode == Mode.APPROVAL && !v.expansionApproved) return Reason.APPROVAL;
        return Reason.READY;
    }

    public static EconomyState.VillageRecord draft(EconomyState.VillageRecord root,
            long center, long seed, long day, boolean physical) {
        if (root.expansionSerial == Long.MAX_VALUE) throw new IllegalStateException("District identity exhausted");
        var v = new EconomyState.VillageRecord();
        v.villageId = UUID.nameUUIDFromBytes((root.villageId + ":district:" + (root.expansionSerial + 1))
                .getBytes(StandardCharsets.UTF_8));
        v.cityId = root.villageId;
        v.dimensionKey = root.dimensionKey;
        v.centerPos = center;
        v.discoveredDay = day;
        v.lastSimulatedDay = day;
        v.lastCensusDay = day;
        v.lastIncidentDay = Math.max(0, day - 8);
        v.districtFounding = physical;
        v.pendingSettlers = physical ? 4 : 0;
        v.population = physical ? 0 : 4;
        v.foodSupply = CHARTER_FOOD;
        v.materialSupply = CHARTER_MATERIALS;
        v.treasury = CHARTER_TREASURY;
        v.developmentPoints = 24;
        v.prosperity = Math.min(85, root.prosperity);
        v.safety = root.safety;
        v.architectureCharacter = root.architectureCharacter;
        v.architectureDialect = root.architectureDialect;
        v.expansionMode = root.expansionMode;
        VillageProsperityEngine.approveInitialDistrictHome(v, seed, day, physical);
        return v;
    }
}
