package com.chedidandrew.emeraldstandard.minecraft;

import com.chedidandrew.emeraldstandard.core.EconomyState;
import com.chedidandrew.emeraldstandard.core.VillageProsperityEngine;
import java.util.Objects;
import java.util.UUID;

/** Exact, immutable terms authorized by the first click of a Fund contribution. */
public record FundConfirmationFingerprint(
        int donationDraft,
        EconomyState.ProsperityFundType type,
        EconomyState.DonationPurpose purpose,
        UUID villageId,
        VillageProsperityEngine.Lifecycle lifecycle,
        long sponsoredProjectId) {

    public FundConfirmationFingerprint {
        donationDraft = Math.max(0, donationDraft);
        type = Objects.requireNonNull(type, "type");
        purpose = Objects.requireNonNull(purpose, "purpose");
        villageId = Objects.requireNonNull(villageId, "villageId");
        lifecycle = Objects.requireNonNull(lifecycle, "lifecycle");
        if (type == EconomyState.ProsperityFundType.DIRECT_GRANT
                && (lifecycle == VillageProsperityEngine.Lifecycle.ABANDONED
                        || lifecycle == VillageProsperityEngine.Lifecycle.EXTINCT)) {
            purpose = EconomyState.DonationPurpose.RESTORATION;
        }
        sponsoredProjectId = type == EconomyState.ProsperityFundType.PROJECT_SPONSORSHIP
                ? Math.max(0L, sponsoredProjectId) : 0L;
    }
}
