package com.chedidandrew.emeraldstandard.core;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

public final class BankVillageIdentityRegressionTest {
    private static final long REAL = pack(1484, 71, 1180), BELL = pack(1444, 70, 1084);
    public static void main(String[] args) throws Exception {
        var dir = Files.createTempDirectory("tes-bank-identity-");
        var service = fixture(dir);
        var state = mutable(service);
        UUID owner = service.villageIdForBankRegion(11), ghost = service.villageIdForBankRegion(22);
        var real = state.villages.get(owner);
        var empty = state.villages.get(ghost);
        double treasury = real.treasury, materials = real.materialSupply, food = real.foodSupply;
        UUID donor = UUID.randomUUID();
        var purpose = EconomyState.DonationPurpose.FOOD;
        empty.prosperityFund.spendableMicro.put(purpose, 80 * EconomyState.MICRO);
        empty.prosperityFund.fastTrackSpendableMicro.put(purpose, 80 * EconomyState.MICRO);
        empty.prosperityFund.emergencyReserveMicro = 20 * EconomyState.MICRO;
        empty.prosperityFund.endowmentPrincipalMicro.put(purpose, 250 * EconomyState.MICRO);
        empty.prosperityFund.lifetimeReceivedMicro = 350 * EconomyState.MICRO;
        empty.prosperityFund.donorTotalsMicro.put(donor, 350 * EconomyState.MICRO);
        var receipt = new EconomyState.FundContribution();
        receipt.donorId = donor; receipt.amountMicro = 100 * EconomyState.MICRO; receipt.purpose = purpose;
        empty.prosperityFund.contributions.add(receipt);
        require(service.saveNowAt(0, 0), "save pre-repair snapshot");
        require(!service.associateBankRegionWithVillage(11, ghost, pack(1448, 69, 1096)),
                "nearest ghost must not steal existing Bank ownership");
        require(!service.reconcileEmptyBankBellVillage(ghost, 11, BELL + 1), "exact bell proof required");
        require(service.reconcileEmptyBankBellVillage(ghost, 11, BELL), service.lastError());
        var after = service.snapshot();
        require(after.villages.size() == 1 && after.generatedBankAnchors.size() == 2,
                "remove phantom record only, retain both buildings");
        require(owner.equals(after.bankRegionVillageIds.get(22L)), "second Bank redirects too");
        require(owner.equals(service.villageSnapshot(ghost).village().villageId), "stale identity reads redirect");
        require(service.nearestVillageSnapshot("minecraft:overworld", BELL, 160).village().villageId.equals(owner),
                "spatial index drops phantom");
        var repaired = after.villages.get(owner);
        require(repaired.treasury == treasury && repaired.foodSupply == food
                && repaired.materialSupply == materials && repaired.population == 18,
                "starter balances and population must not duplicate");
        require(repaired.prosperityFund.spendableMicro.get(purpose) == 80 * EconomyState.MICRO
                && repaired.prosperityFund.emergencyReserveMicro == 20 * EconomyState.MICRO
                && repaired.prosperityFund.endowmentPrincipalMicro.get(purpose) == 250 * EconomyState.MICRO
                && repaired.prosperityFund.contributions.size() == 1, "all unspent gift value and audit retained");
        require(!service.reconcileEmptyBankBellVillage(ghost, 11, BELL), "repair idempotent");
        var loaded = new EconomyService(); loaded.startWithSeed(dir, 44, 0, 0);
        require(loaded.snapshot().villages.size() == 1 && owner.equals(loaded.canonicalVillageId(ghost)),
                "redirect and source removal survive restart/journal replay");
        for (int gate = 0; gate < 10; gate++) {
            var sample = fixture(Files.createTempDirectory("tes-bank-identity-gate-"));
            var s = mutable(sample); UUID id = sample.villageIdForBankRegion(22);
            var v = s.villages.get(id);
            switch (gate) {
                case 0 -> v.population = 1;
                case 1 -> v.projectSerial = 1;
                case 2 -> v.restorationFund = 5;
                case 3 -> v.prosperityFund.lifetimeSpentMicro = 1;
                case 4 -> v.housingCapacity = 16;
                case 5 -> v.lastIncidentCause = VillageProsperityEngine.IncidentCause.PLAYER;
                case 6 -> v.cityId = sample.villageIdForBankRegion(11);
                case 7 -> v.residents.put(UUID.randomUUID(), new EconomyState.ResidentRecord());
                case 8 -> v.treasury = 100;
                case 9 -> v.organicTerritory = true;
            }
            require(!sample.reconcileEmptyBankBellVillage(id, 11, BELL), "protect independent/history-bearing village " + gate);
            require(s.villages.size() == 2 && id.equals(sample.villageIdForBankRegion(22)), "declined repair leaves state");
        }
        var failed = fixture(Files.createTempDirectory("tes-bank-identity-failure-"));
        var path = EconomyService.class.getDeclaredField("path"); path.setAccessible(true);
        Path validPath = (Path) path.get(failed);
        Path badParent = Files.createTempFile("tes-bank-identity-readonly-", ".txt");
        path.set(failed, badParent.resolve("impossible"));
        UUID failedGhost = failed.villageIdForBankRegion(22);
        require(!failed.reconcileEmptyBankBellVillage(failedGhost, 11, BELL), "I/O failure refuses repair");
        require(failed.snapshot().villages.size() == 2 && failed.snapshot().villageIdentityRedirects.isEmpty()
                && failedGhost.equals(failed.villageIdForBankRegion(22)), "I/O failure rolls back all identity changes");
        path.set(failed, validPath);
        require(failed.reconcileEmptyBankBellVillage(failedGhost, 11, BELL), "safe retry");
        System.out.println("PASS BankVillageIdentityRegressionTest: ownership, donations, redirects, reload, refusal gates and save rollback");
    }
    private static EconomyService fixture(Path dir) throws Exception {
        var service = new EconomyService(); service.startWithSeed(dir, 44, 0, 0);
        var owner = service.observeVillage(new EconomyService.VillageObservation(
                "minecraft:overworld", REAL, 11, 0, 18, 16, 0, false, List.of()));
        var ghost = service.observeVillage(new EconomyService.VillageObservation(
                "minecraft:overworld", BELL, 22, 0, 0, 0, 0, false, List.of()));
        require(service.markGeneratedBankRegion(11, pack(1448, 69, 1096), owner.village().villageId, 10), "owner Bank");
        require(service.markGeneratedBankRegion(22, pack(1448, 69, 1060), ghost.village().villageId, 10), "ghost Bank");
        return service;
    }
    private static EconomyState mutable(EconomyService service) throws Exception {
        var f = EconomyService.class.getDeclaredField("state"); f.setAccessible(true);
        return (EconomyState) f.get(service);
    }
    private static long pack(int x, int y, int z) {
        return ((long)x & 0x3FFFFFFL) << 38 | ((long)z & 0x3FFFFFFL) << 12 | y & 0xFFFL;
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
