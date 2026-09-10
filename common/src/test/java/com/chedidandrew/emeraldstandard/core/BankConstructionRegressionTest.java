package com.chedidandrew.emeraldstandard.core;

import java.nio.file.Files;
import java.util.List;

public final class BankConstructionRegressionTest {
    public static void main(String[] args) throws Exception {
        var plan = new BankConstruction(123, 456, null, 8,
                List.of(new BankConstruction.Cell(123, "minecraft:air", "minecraft:stone")));
        require(plan.equals(BankConstruction.decode(plan.encode())), "frozen state round trip");
        var prep = new SitePreparationPlan(List.of(new SitePreparationPlan.Cell(123, "minecraft:oak_log[axis=y]")));
        require(prep.equals(SitePreparationPlan.decode(prep.encode())), "preparation state round trip");
        var graded = new SitePreparationPlan(List.of(new SitePreparationPlan.Cell(456,
                "minecraft:air", "minecraft:stone_bricks")));
        require(graded.equals(SitePreparationPlan.decode(graded.encode())), "terrain finish round trip");
        var legacyBytes = new java.io.ByteArrayOutputStream();
        try (var out = new java.io.DataOutputStream(legacyBytes)) {
            out.writeInt(1); out.writeLong(123); out.writeUTF("minecraft:oak_log[axis=y]");
        }
        require(prep.equals(SitePreparationPlan.decode(java.util.Base64.getEncoder().encodeToString(
                legacyBytes.toByteArray()))), "format-22 clearance payload remains readable");
        require(SitePreparationPlan.decode(new SitePreparationPlan(List.of()).encode()).cells().isEmpty(),
                "empty preparation round trip");
        var state = EconomyState.fresh(1234, 0, 0);
        var owner = state.village(new java.util.UUID(41, 42));
        owner.projectSerial = 1;
        var project = new EconomyState.VillageProject();
        project.projectId = 1; project.type = VillageProsperityEngine.ProjectType.COTTAGE;
        project.totalBlocks = project.type.nominalBlocks(); project.sitePreparationPlan = prep;
        project.sitePreparationComplete = false; owner.projects.add(project);
        var prepDir = Files.createTempDirectory("tes-preparation-persistence-");
        state.save(prepDir.resolve("the_emerald_standard.properties"));
        var restoredPrep = new EconomyService(); restoredPrep.start(prepDir, 1234, 0);
        require(restoredPrep.villageSnapshot(owner.villageId).village().projects.getFirst().sitePreparationPlan.equals(prep),
                "project preparation survives save/restart/copy");
        try { SitePreparationPlan.decode("broken"); throw new AssertionError("bad preparation accepted"); }
        catch (IllegalArgumentException expected) { }
        var dir = Files.createTempDirectory("tes-bank-construction-");
        var service = new EconomyService(); service.startWithSeed(dir, 0, 0, 73);
        require(service.reserveBankConstruction(7, plan), "reservation saved before world mutation");
        require(!service.reserveBankConstruction(7, plan), "duplicate reservation rejected");
        require(service.snapshot().copy().pendingBankConstructions.get(7L).equals(plan), "snapshot preserves immutable plan");
        var reloaded = new EconomyService(); reloaded.startWithSeed(dir, 0, 0, 73);
        require(reloaded.pendingBankConstructionsSnapshot().get(7L).equals(plan), "restart restores pending plan");
        require(reloaded.markGeneratedBankRegion(7, 457L, null, 8), "unrelated marker fixture");
        require(reloaded.pendingBankConstructionsSnapshot().containsKey(7L), "wrong anchor cannot consume reservation");
        require(reloaded.markGeneratedBankRegion(7, 456L, null, 8), "completion committed");
        require(reloaded.pendingBankConstructionsSnapshot().isEmpty(), "marker and queue removal are one transaction");
        var finalLoad = new EconomyService(); finalLoad.startWithSeed(dir, 0, 0, 73);
        require(finalLoad.pendingBankConstructionsSnapshot().isEmpty() && finalLoad.hasGeneratedBankRegion(7), "completion survives restart");
        try { new BankConstruction(1, 2, null, 8, List.of(plan.cells().getFirst(), plan.cells().getFirst()));
            throw new AssertionError("duplicate position accepted");
        } catch (IllegalArgumentException expected) { }
        try { BankConstruction.decode("not base64"); throw new AssertionError("malformed data accepted");
        } catch (java.io.IOException expected) { }
        System.out.println("PASS BankConstructionRegressionTest: frozen plans, restart, duplicate rejection, atomic completion");
    }
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
