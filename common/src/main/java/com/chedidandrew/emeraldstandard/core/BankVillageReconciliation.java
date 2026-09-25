package com.chedidandrew.emeraldstandard.core;

import java.util.Comparator;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Conservative migration for empty records proven by the runtime to be authored Bank bells. */
final class BankVillageReconciliation {
    private BankVillageReconciliation() {}

    static boolean eligible(EconomyState state, EconomyState.VillageRecord ghost,
            EconomyState.VillageRecord owner, long bellPosition) {
        if (ghost == null || owner == null || ghost == owner
                || !Objects.equals(ghost.dimensionKey, owner.dimensionKey)
                || !"minecraft:overworld".equals(ghost.dimensionKey)
                || ghost.centerPos != bellPosition || ghost.discoveredDay < owner.discoveredDay
                || (owner.population == 0 && owner.residents.isEmpty())
                || ghost.population != 0 || ghost.observedPopulation != 0
                || ghost.housingCapacity > 4 || ghost.observedHousingCapacity > 4
                || ghost.pendingSettlers != 0 || ghost.developmentTier != 0
                || ghost.projectSerial != 0 || !ghost.projects.isEmpty()
                || !ghost.residents.isEmpty() || !ghost.incidents.isEmpty()
                || ghost.lastIncidentCause != VillageProsperityEngine.IncidentCause.NONE
                || ghost.collapseCount != 0 || ghost.hostileCasualties != 0
                || ghost.playerCasualties != 0 || ghost.environmentalCasualties != 0
                || ghost.cityId != null || ghost.districtFounding || ghost.expansionSerial != 0
                || ghost.restorationFund != 0 || ghost.restorationFunded
                || ghost.treasury > 8.000001 || ghost.materialSupply > 24.000001
                || ghost.foodSupply > 60.000001 || ghost.developmentPoints > 4.000001
                || state.villageMarketShadows.containsKey(ghost.villageId)
                || state.villageIdentityRedirects.containsValue(ghost.villageId)
                || state.villages.values().stream().anyMatch(v -> ghost.villageId.equals(v.cityId))
                || state.pendingBankConstructions.values().stream()
                        .anyMatch(p -> ghost.villageId.equals(p.villageId()))) return false;
        var fund = ghost.prosperityFund;
        // Spent gifts may already have become local infrastructure/resources. Do not guess.
        return fund.lifetimeSpentMicro == 0 && fund.spentTodayMicro == 0
                && fund.projectSponsorshipMicro.isEmpty()
                && fund.contributions.size() + owner.prosperityFund.contributions.size()
                        <= EconomyState.MAX_FUND_LEDGER_ENTRIES;
    }

    static void transferUnspentFund(EconomyState.ProsperityFund from, EconomyState.ProsperityFund to) {
        // Checked arithmetic fails the whole migration rather than clamping away donated value.
        add(from.spendableMicro, to.spendableMicro);
        add(from.fastTrackSpendableMicro, to.fastTrackSpendableMicro);
        add(from.endowmentPrincipalMicro, to.endowmentPrincipalMicro);
        add(from.donorTotalsMicro, to.donorTotalsMicro);
        to.emergencyReserveMicro = Math.addExact(to.emergencyReserveMicro, from.emergencyReserveMicro);
        to.lifetimeReceivedMicro = Math.addExact(to.lifetimeReceivedMicro, from.lifetimeReceivedMicro);
        from.contributions.forEach(c -> to.contributions.add(c.copy()));
        to.contributions.sort(Comparator.comparingLong(c -> c.day));
        // Maps loaded from older snapshots already have their provenance migration applied.
        to.fastTrackProvenanceKnown = true;
        // Never transfer a phantom's initial food, materials, treasury, housing or development.
        // Global donor totals/accounts are already recorded and must not be credited again.
    }

    private static <K> void add(Map<K, Long> from, Map<K, Long> to) {
        from.forEach((key, value) -> to.merge(key, value, Math::addExact));
    }
}
