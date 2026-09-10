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
        verifyLootReceipts();
        System.out.println("PASS BankConstructionRegressionTest: frozen plans, restart, duplicate rejection, atomic completion, durable loot receipts and legacy migration");
    }

    private static void verifyLootReceipts() throws Exception {
        var plan = new BankConstruction(10, 20, null, 8, List.of(
                new BankConstruction.Cell(10, "minecraft:air", "minecraft:chest[facing=north]"),
                new BankConstruction.Cell(11, "minecraft:air", "minecraft:barrel"),
                new BankConstruction.Cell(12, "minecraft:air", "minecraft:stone")));
        var dir = Files.createTempDirectory("tes-bank-loot-receipts-");
        var service = new EconomyService(); service.startWithSeed(dir, 0, 0, 73);
        require(service.reserveBankConstruction(9, plan), "loot fixture reserved");
        require(!service.markBankStorageHandled(9, plan, 12), "non-storage receipt rejected");
        require(service.markBankStorageHandled(9, plan, 10), "first chest receipt saved");
        require(!service.markBankStorageHandled(9, plan, 10), "stale plan cannot claim twice");
        var restarted = new EconomyService(); restarted.startWithSeed(dir, 0, 0, 73);
        var saved = restarted.pendingBankConstructionsSnapshot().get(9L);
        require(saved.handledStorage().equals(java.util.Set.of(10L)) && !saved.legacyLootSuppressed(),
                "receipt and new-plan provenance survive restart");
        require(saved.equals(BankConstruction.decode(saved.encode())), "receipt payload round trip");
        require(!restarted.markBankStorageHandled(9, plan, 10), "restart does not reopen issued loot");

        // Make only the fixture's expected save target unwritable: receipt must roll back.
        var file = dir.resolve("the_emerald_standard.properties");
        var backup = dir.resolve("receipt-test-original.properties");
        Files.move(file, backup); Files.createDirectory(file);
        Files.writeString(file.resolve("blocker"), "fixture");
        try {
            require(!restarted.markBankStorageHandled(9, plan, 11), "failed receipt save cannot authorize loot");
            require(!restarted.pendingBankConstructionsSnapshot().get(9L).handledStorage().contains(11L),
                    "failed receipt rolls back in-memory claim");
        } finally {
            Files.delete(file.resolve("blocker")); Files.delete(file); Files.move(backup, file);
        }
        require(restarted.markBankStorageHandled(9, plan, 11), "receipt can retry after storage recovers");

        var bytes = new java.io.ByteArrayOutputStream();
        try (var out = new java.io.DataOutputStream(bytes)) {
            out.writeLong(plan.origin()); out.writeLong(plan.bankerAnchor()); out.writeUTF("");
            out.writeInt(plan.version()); out.writeInt(plan.cells().size());
            for (var cell : plan.cells()) {
                out.writeLong(cell.position()); out.writeUTF(cell.before()); out.writeUTF(cell.after());
            }
        }
        var legacy = BankConstruction.decode(java.util.Base64.getEncoder().encodeToString(bytes.toByteArray()));
        require(legacy.legacyLootSuppressed() && legacy.handledStorage().isEmpty()
                && legacy.cells().equals(plan.cells()), "old plans keep geometry but cannot reroll unknown loot");
        require(BankConstruction.decode(legacy.withHandledStorage(10).encode()).legacyLootSuppressed(),
                "legacy suppression survives receipt updates and saves");
        try { plan.withHandledStorage(12); throw new AssertionError("non-storage position accepted"); }
        catch (IllegalArgumentException expected) { }
    }
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
