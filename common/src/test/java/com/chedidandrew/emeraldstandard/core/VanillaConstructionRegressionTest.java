package com.chedidandrew.emeraldstandard.core;

import java.nio.file.Files;
import java.util.*;

public final class VanillaConstructionRegressionTest {
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static VanillaConstructionPlan plan(String style, int variant) {
        return new VanillaConstructionPlan("minecraft:village/" + style + "/houses/" + style + "_small_house_" + variant,
                style, "residence", 3, 3, 3, 1, List.of(
                new VanillaConstructionPlan.Cell(1, 0, 0, "minecraft:oak_planks"),
                new VanillaConstructionPlan.Cell(1, 1, 0, "minecraft:air"),
                new VanillaConstructionPlan.Cell(1, 2, 0, "minecraft:air")));
    }
    public static void main(String[] args) throws Exception {
        var plains = plan("plains", 1); var desert = plan("desert", 1);
        check(VanillaConstructionPlan.decode(plains.encode()).hash().equals(plains.hash()), "plan round trip");
        try { VanillaConstructionPlan.decode("garbage"); throw new AssertionError("accepted corruption"); }
        catch (IllegalArgumentException expected) {}
        try { plains.cells().clear(); throw new AssertionError("mutable plan"); }
        catch (UnsupportedOperationException expected) {}
        var state = EconomyState.fresh(77, 0, 0);
        var v = state.village(new UUID(123, 567));
        v.architectureCharacter = "agrarian"; v.architectureDialect = v.naturalVillageStyle = "plains";
        v.population = 20; v.developmentTier = 4; v.organicTerritory = true;
        VanillaBuildingCatalog.publish(List.of(plains, desert, plan("plains", 2)));
        int imported = 0, authored = 0;
        for (int i = 1; i < 100; i++) {
            var candidate = VanillaBuildingCatalog.choose(v, VillageProsperityEngine.ProjectType.COTTAGE, i);
            if (candidate == null) authored++;
            else { imported++; check(candidate.style().equals("plains"), "mixed village styles"); }
        }
        check(imported > 20 && authored > 20, "TES/vanilla variety");
        v.naturalVillageStyle = "";
        for (int i=1;i<50;i++) check(VanillaBuildingCatalog.choose(v,
                VillageProsperityEngine.ProjectType.HOUSE,i)==null,"unknown/modded village admitted");
        v.naturalVillageStyle = "plains";
        var p = new EconomyState.VillageProject();
        p.projectId = v.projectSerial = 1; p.type = VillageProsperityEngine.ProjectType.COTTAGE;
        p.designSchema = VanillaConstructionPlan.SCHEMA; p.vanillaPlan = plains;
        p.totalBlocks = plains.cells().size(); v.projects.add(p);
        check(p.copy().vanillaPlan == p.vanillaPlan, "copied complete cell list");
        int before = v.housingCapacity;
        VillageProsperityEngine.completeProject(v, p, 0, true);
        check(v.housingCapacity == before + 1, "used TES housing capacity instead of actual vanilla beds");
        check(!VillageProsperityEngine.isProjectOperational(p), "unbuilt imported housing became usable");
        var file = Files.createTempDirectory("tes-vanilla-persistence-").resolve("state.properties");
        state.save(file);
        VanillaBuildingCatalog.clear();
        var restarted = EconomyState.load(file,77,0,0);
        var restored = restarted.villages.get(v.villageId).projects.getFirst();
        check(restored.vanillaPlan.hash().equals(plains.hash()), "resource removal changed saved building");
        check(restarted.villages.get(v.villageId).naturalVillageStyle.equals("plains"), "lost village family");
        check(restored.housingGain()==1 && restored.totalBlocks==p.totalBlocks,"lost saved role/operations");
        p.materializedComplete = true;
        check(VillageProsperityEngine.isProjectOperational(p),"completed imported project unavailable");
        System.out.println("PASS vanilla plans: immutable/save/restart, strict family, TES mix, real beds, physical benefits, malformed data");
    }
}
