package com.chedidandrew.emeraldstandard.minecraft;

import com.chedidandrew.emeraldstandard.core.*;

/** Read-only eligibility shared by previews, confirmation and payment, before inventory moves. */
final class FundContributionChecks {
    private FundContributionChecks() {}
    record Decision(int status, EconomyState.DonationPurpose purpose, long projectId, String reason) {
        boolean allowed() { return status == BankingOperations.READY; }
    }
    static Decision assess(EmeraldConfig config, EconomyService.VillageSnapshot snapshot,
            EconomyState.ProsperityFundType type, EconomyState.DonationPurpose requested) {
        if (snapshot == null) return denied(BankingOperations.NO_VILLAGE, requested, "No associated village.");
        if (type == null || requested == null)
            return denied(BankingOperations.UNSUPPORTED, requested, "Choose a valid contribution type and purpose.");
        if (!snapshot.simulationEnabled() || !config.prosperityFundEnabled())
            return denied(BankingOperations.UNSUPPORTED, requested, "The village is not accepting contributions.");
        if (type == EconomyState.ProsperityFundType.ENDOWMENT && !config.prosperityFundEndowmentsEnabled())
            return denied(BankingOperations.UNSUPPORTED, requested, "The village is not accepting endowments.");
        if (type == EconomyState.ProsperityFundType.PROJECT_SPONSORSHIP) {
            if (!config.prosperityFundProjectSponsorshipEnabled())
                return denied(BankingOperations.UNSUPPORTED, requested, "The village is not accepting sponsorships.");
            var project = snapshot.village().projects.stream().filter(p -> !p.economicComplete).findFirst().orElse(null);
            return project == null
                    ? denied(BankingOperations.NOT_READY, requested, "No project needs further paid labor.")
                    : new Decision(BankingOperations.READY, EconomyState.donationPurposeForProject(project), project.projectId, "");
        }
        boolean restoration = type == EconomyState.ProsperityFundType.DIRECT_GRANT
                && (snapshot.village().lifecycle == VillageProsperityEngine.Lifecycle.ABANDONED
                    || snapshot.village().lifecycle == VillageProsperityEngine.Lifecycle.EXTINCT);
        // This is mandatory routing of an otherwise ordinary grant, not an optional targeted gift.
        if (restoration) return new Decision(BankingOperations.READY, EconomyState.DonationPurpose.RESTORATION, 0, "");
        if (requested != EconomyState.DonationPurpose.GENERAL && !config.prosperityFundTargetedDonationsEnabled())
            return denied(BankingOperations.UNSUPPORTED, requested, "Only General grants are being accepted for this settlement.");
        return new Decision(BankingOperations.READY, requested, 0, "");
    }
    private static Decision denied(int status, EconomyState.DonationPurpose purpose, String reason) {
        return new Decision(status, purpose, 0, reason);
    }
}
