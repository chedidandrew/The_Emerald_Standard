package com.chedidandrew.emeraldstandard.minecraft;

import com.chedidandrew.emeraldstandard.core.*;
import java.util.*;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;

public final class VanillaVillageBuildingsSelfTest {
    public static void main(String[] args) throws Exception {
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();
        int count = 0; List<String> failures = new ArrayList<>();

        for (var entry : VanillaVillageBuildings.manifest()) {
            String path = "/data/minecraft/structure/" + entry.id().substring("minecraft:".length()) + ".nbt";
            try (var stream = net.minecraft.world.level.block.Blocks.class.getResourceAsStream(path)) {
                if (stream == null) throw new AssertionError("Missing default resource " + path);
                var plan = VanillaVillageBuildings.convert(entry, stream.readAllBytes());
                var restored = VanillaConstructionPlan.decode(plan.encode());
                if (!plan.hash().equals(restored.hash()) || !plan.cells().equals(restored.cells()))
                    throw new AssertionError("Saved cells changed");
                if (VanillaVillageBuildings.cells(plan).size() != plan.cells().size())
                    throw new AssertionError("Decoded cells changed");
                var village = new EconomyState.VillageRecord();
                village.architectureDialect = village.naturalVillageStyle = plan.style();
                var project = new EconomyState.VillageProject();
                project.type = switch(plan.role()) {
                    case "residence" -> VillageProsperityEngine.ProjectType.HOUSE;
                    case "food" -> VillageProsperityEngine.ProjectType.GRANARY;
                    case "craft" -> VillageProsperityEngine.ProjectType.SMITHY;
                    default -> VillageProsperityEngine.ProjectType.MARKET_SQUARE;
                };
                project.designSchema = VanillaConstructionPlan.SCHEMA; project.vanillaPlan = plan;
                for (int rotation=0;rotation<4;rotation++) {
                    project.designRotation = rotation;
                    var transformed = (List<?>)ConstructionSupportRecoverySelfTest.invoke("projectTemplate",
                            null,net.minecraft.core.BlockPos.ZERO,village,project);
                    if (transformed.size()!=plan.cells().size()) throw new AssertionError("Rotation lost cells");
                    for (var cell:transformed) {
                        int x=(int)ConstructionSupportRecoverySelfTest.field(cell,"dx");
                        int z=(int)ConstructionSupportRecoverySelfTest.field(cell,"dz");
                        if (x<0 || z<0 || x >= (rotation%2==0?plan.width():plan.depth())
                                || z >= (rotation%2==0?plan.depth():plan.width()))
                            throw new AssertionError("Rotation escaped its reserved bounds");
                    }
                }
                count++;
            } catch (Exception | AssertionError e) {
                failures.add(entry.id() + ": " + e);
            }
        }

        failures.forEach(System.out::println);
        System.out.println("Validated vanilla templates: " + count + "; failures: " + failures.size());
        if (!failures.isEmpty()) throw new AssertionError("Vanilla catalog admission failed");
    }
}
