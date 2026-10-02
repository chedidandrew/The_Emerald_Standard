package com.chedidandrew.emeraldstandard.core;

import java.nio.file.Files;
import java.util.*;
import static com.chedidandrew.emeraldstandard.core.VillageProsperityEngine.ProjectType.*;

/** Every approval channel shares one config policy, independent of frozen project provenance. */
public final class VanillaOnlyBuildingsRegressionTest {
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static VanillaConstructionPlan plan(String style, String role, int beds, int variant) {
        return new VanillaConstructionPlan("minecraft:village/"+style+"/houses/"+role+"_"+variant,
                style, role, 3, 3, 3, beds, List.of(
                new VanillaConstructionPlan.Cell(1,0,0,"minecraft:oak_planks"),
                new VanillaConstructionPlan.Cell(1,1,0,"minecraft:air"),
                new VanillaConstructionPlan.Cell(1,2,0,"minecraft:air")));
    }
    private static EconomyState.VillageRecord village(String style) {
        var v = new EconomyState.VillageRecord(); v.villageId = UUID.nameUUIDFromBytes(style.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        v.naturalVillageStyle = v.architectureDialect = style; v.organicTerritory = true;
        v.vanillaOnlyBuildings = true; v.population = 12; v.housingCapacity = 12;
        v.prosperity = v.safety = 90; v.foodSupply = 10000; v.materialSupply = 10000;
        v.treasury = 10000; v.developmentPoints = 10000; return v;
    }
    public static void main(String[] args) throws Exception {
        var plans = new ArrayList<VanillaConstructionPlan>();
        for (String style : List.of("plains","desert","savanna","taiga","snowy")) {
            plans.add(plan(style,"residence",1,1)); plans.add(plan(style,"residence",3,2));
            plans.add(plan(style,"food",0,1)); plans.add(plan(style,"craft",0,1));
            plans.add(plan(style,"trade",0,1)); plans.add(plan(style,"civic",0,1));
        }
        VanillaBuildingCatalog.publish(plans);
        try {
            for (String style : List.of("plains","desert","savanna","taiga","snowy")) {
                var v = village(style);
                for (long serial=1; serial<=100; serial++)
                    check(VanillaBuildingCatalog.choose(v,HOUSE,serial) != null,"vanilla-only inherited mixed probability");
                // Security pressure must not monopolize an unsupported TES guard-post request.
                v.safety = 15;
                check(Set.of(COTTAGE,HOUSE).contains(VillageProsperityEngine.nextProjectPlan(v,77,0).type()),"unsupported need blocked housing");
                for (int i=0;i<8;i++) {
                    VillageProsperityEngine.approveInitialDistrictHome(v,77,i,true);
                    var p = v.projects.getLast();
                    check(p.projectId == i+1 && p.vanillaPlan != null && p.vanillaPlan.style().equals(style),"normal approval used TES/wrong style");
                    VillageProsperityEngine.completeProject(v,p,i,true); p.materializedComplete = true;
                    v.population = v.housingCapacity;
                }
                var forced = village(style);
                for (int i=0;i<15;i++) {
                    check(VillageProsperityEngine.forceDevelopment(forced,i),"forced vanilla expansion stalled");
                    var p = forced.projects.getLast(); p.materializedComplete = true;
                    check(p.vanillaPlan != null && p.vanillaPlan.style().equals(style),"forced approval used TES/wrong style");
                }
                check(forced.projects.stream().anyMatch(p -> p.vanillaPlan.role().equals("food"))
                        && forced.projects.stream().anyMatch(p -> p.vanillaPlan.role().equals("craft")),"forced catalog omitted functional roles");
                forced.population = 8; VillageProsperityEngine.updateDevelopmentTier(forced);
                check(forced.developmentTier == 2,"vanilla village cannot reach tier 2 without TES Warehouse");
                forced.population = 12; VillageProsperityEngine.updateDevelopmentTier(forced);
                check(forced.developmentTier == 3,"vanilla village cannot reach tier 3 without TES Mine");
                forced.population = 28; VillageProsperityEngine.updateDevelopmentTier(forced);
                check(forced.developmentTier == 5,"vanilla village cannot reach tier 5");
            }
            var funded = village("plains"); funded.materialSupply = funded.treasury = funded.developmentPoints = 0;
            funded.prosperityFund.spendableMicro.put(EconomyState.DonationPurpose.GENERAL,1000*EconomyState.MICRO);
            funded.prosperityFund.fastTrackSpendableMicro.put(EconomyState.DonationPurpose.GENERAL,1000*EconomyState.MICRO);
            check(VillageProsperityEngine.fastTrackNextProjectFromFund(funded,77,1,true)>0
                    && funded.projects.getFirst().vanillaPlan != null,"Fund approval bypassed vanilla-only");

            var blocked = village("plains"); blocked.architectureDialect = "desert";
            var before = blocked.copy();
            check(VanillaBuildingCatalog.waitingForCompatibleBuildings(blocked),"mismatched family has no waiting diagnosis");
            check(!VillageProsperityEngine.forceDevelopment(blocked,0),"wrong-style forced fallback");
            check(VillageProsperityEngine.fastTrackNextProjectFromFund(blocked,77,0,true)==0,"incompatible project charged Fund");
            VillageProsperityEngine.approveInitialDistrictHome(blocked,77,0,true);
            check(blocked.projects.isEmpty() && blocked.projectSerial==0 && blocked.treasury==before.treasury
                    && blocked.materialSupply==before.materialSupply && blocked.developmentPoints==before.developmentPoints,"unavailable project charged inputs");

            var bigOnly = village("plains"); bigOnly.population = bigOnly.housingCapacity = 4; bigOnly.developmentTier = 0;
            VanillaBuildingCatalog.publish(List.of(plan("plains","residence",3,2)));
            VillageProsperityEngine.approveInitialDistrictHome(bigOnly,77,0,true);
            check(bigOnly.projects.getFirst().type==HOUSE,"missing cottage did not try matching larger residence");
            VanillaBuildingCatalog.clear();
            var unavailable = village("plains");
            check(!VillageProsperityEngine.forceDevelopment(unavailable,0),"empty catalog generated TES instead");
            unavailable.naturalVillageStyle = "";
            check(VanillaBuildingCatalog.waitingForCompatibleBuildings(unavailable),"unknown village source allowed import");
            // Off restores the legacy mixed/TES catalog, even when imports are unavailable.
            unavailable.vanillaOnlyBuildings = false;
            check(VillageProsperityEngine.forceDevelopment(unavailable,0)
                    && unavailable.projects.getFirst().vanillaPlan==null,"Off failed to restore TES selection");

            VanillaBuildingCatalog.publish(plans);
            var state = EconomyState.fresh(77,0,0); state.configureVanillaOnlyVillageBuildings(true);
            var fresh = state.village(UUID.randomUUID()); check(fresh.vanillaOnlyBuildings,"new village lost runtime policy");
            check(state.copy().village(fresh.villageId).vanillaOnlyBuildings,"transaction copy lost policy");
            var root = village("plains");
            check(VillageExpansion.draft(root,1234,77,0,true,true).projects.getFirst().vanillaPlan!=null,"new district ignored policy/source");
            state.villages.put(root.villageId,root);
            check(VillageProsperityEngine.forceDevelopment(root,0),"saved vanilla fixture not created");
            var saved = root.projects.getFirst(); var hash = saved.vanillaPlan.hash();
            state.configureVanillaOnlyVillageBuildings(false);
            check(saved.vanillaPlan.hash().equals(hash),"toggle rewrote frozen plan");
            var file = Files.createTempDirectory("tes-vanilla-only-").resolve("the_emerald_standard.properties");
            state.save(file);
            var service = new EconomyService(); service.configureEconomicClock(false,30);
            service.configureVanillaOnlyVillageBuildings(true); service.start(file.getParent(),77,0);
            var loaded = service.snapshot().village(root.villageId);
            check(loaded.vanillaOnlyBuildings && loaded.projects.getFirst().vanillaPlan.hash().equals(hash),"startup failed to apply policy/preserve plan");
            service.configureVanillaOnlyVillageBuildings(false);
            check(!service.snapshot().village(root.villageId).vanillaOnlyBuildings,"disable did not apply to existing village");
            // Old TES work keeps its exact blueprint and can finish after enabling this option.
            state.configureVanillaOnlyVillageBuildings(true);
            state.villages.put(unavailable.villageId,unavailable);
            var old = unavailable.projects.getFirst(); var signature = old.designSignature;
            old.economicComplete = false; old.economicProgress = .5;
            state.configureVanillaOnlyVillageBuildings(true);
            check(old.designSignature==signature && old.vanillaPlan==null,"option rewrote TES project");
            VillageProsperityEngine.completeProject(unavailable,old,1,true);
            check(old.economicComplete,"old TES labor was paused");
            var abstractVillage = village("desert");
            VillageProsperityEngine.approveInitialDistrictHome(abstractVillage,77,0,false);
            var abstractProject = abstractVillage.projects.getFirst();
            VillageProsperityEngine.completeProject(abstractVillage,abstractProject,1,false);
            check(abstractProject.vanillaPlan!=null && !VillageProsperityEngine.isProjectOperational(abstractProject),"simulation-only bypassed imported physical completion");
            check(VillageProsperityEngine.effectiveHousingCapacity(abstractVillage)==12,"unbuilt imported housing became available when visuals were off");
            // Pending physical facilities do not unlock tiers just because labor was paid.
            var pending = village("taiga"); pending.population = 8;
            var food = new EconomyState.VillageProject(); food.type=GRANARY; food.vanillaPlan=plan("taiga","food",0,1);
            food.economicComplete=true; pending.projects.add(food);
            VillageProsperityEngine.updateDevelopmentTier(pending);
            check(pending.developmentTier==1,"unbuilt vanilla facility unlocked tier 2");
            food.abstractOnly = true; VillageProsperityEngine.updateDevelopmentTier(pending);
            check(pending.developmentTier==1,"visual pause unlocked unbuilt imported facility");
            // A catalog reset between old-district survey and commit must defer, not dereference
            // an empty starter draft or debit municipal capital.
            var legacyState = EconomyState.fresh(77,0,0); var legacy = village("snowy");
            legacy.organicTerritory = false; legacyState.villages.put(legacy.villageId,legacy);
            var legacyDir = Files.createTempDirectory("tes-vanilla-only-district-");
            legacyState.save(legacyDir.resolve("the_emerald_standard.properties"));
            var legacyService = new EconomyService(); legacyService.configureEconomicClock(false,30);
            legacyService.configureVanillaOnlyVillageBuildings(true); legacyService.configureForcedVillageDevelopment(true);
            legacyService.start(legacyDir,77,0);
            var draft = legacyService.draftVillageDistrict(legacy.villageId,1234);
            check(draft != null && draft.projects.getFirst().vanillaPlan != null,"legacy district did not honor vanilla-only");
            draft.projects.getFirst().originPos=1234; draft.projects.getFirst().designPlanHash="0".repeat(64);
            VanillaBuildingCatalog.clear();
            check(legacyService.draftVillageDistrict(legacy.villageId,1234)==null,"empty starter draft escaped to physical planner");
            check(!legacyService.commitVillageDistrict(legacy.villageId,0,draft),"catalog reset committed unsupported district");
            check(legacyService.snapshot().village(legacy.villageId).treasury==legacy.treasury,"failed district commit charged treasury");
        } finally { VanillaBuildingCatalog.clear(); }
        System.out.println("PASS vanilla-only buildings: five styles, normal/forced/Fund, immutable plans, reload, unknown catalogs, housing, tiers, simulation-only");
    }
}
