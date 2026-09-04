package com.chedidandrew.emeraldstandard.minecraft;

import com.chedidandrew.emeraldstandard.core.EconomyState;
import com.chedidandrew.emeraldstandard.core.VillageProsperityEngine;
import java.util.UUID;

/** Ensures a Fund confirmation cannot silently authorize changed contribution terms. */
public final class FundConfirmationFingerprintRegressionTest {
    private FundConfirmationFingerprintRegressionTest() {
    }

    public static void main(String[] args) {
        UUID village = UUID.fromString("4cf860da-2b0c-463e-a3a1-064bd4139264");
        FundConfirmationFingerprint expected = fingerprint(
                100,
                EconomyState.ProsperityFundType.PROJECT_SPONSORSHIP,
                EconomyState.DonationPurpose.HOUSING,
                village,
                7L);
        require(expected.equals(fingerprint(
                        100,
                        EconomyState.ProsperityFundType.PROJECT_SPONSORSHIP,
                        EconomyState.DonationPurpose.HOUSING,
                        village,
                        7L)),
                "Identical Fund terms did not match");
        requireDifferent(expected, fingerprint(101, expected.type(), expected.purpose(), village, 7L),
                "draft");
        requireDifferent(expected, fingerprint(100, EconomyState.ProsperityFundType.ENDOWMENT,
                expected.purpose(), village, 7L), "type");
        requireDifferent(expected, fingerprint(100, expected.type(),
                EconomyState.DonationPurpose.FOOD, village, 7L), "purpose");
        requireDifferent(expected, fingerprint(100, expected.type(), expected.purpose(),
                UUID.fromString("fb7ae1d4-68e8-4625-8d4a-a450f3932002"), 7L), "village");
        requireDifferent(expected, fingerprint(100, expected.type(), expected.purpose(),
                village, 8L), "sponsored project");

        FundConfirmationFingerprint grantA = fingerprint(
                25, EconomyState.ProsperityFundType.DIRECT_GRANT,
                EconomyState.DonationPurpose.GENERAL, village, 7L);
        FundConfirmationFingerprint grantB = fingerprint(
                25, EconomyState.ProsperityFundType.DIRECT_GRANT,
                EconomyState.DonationPurpose.GENERAL, village, 99L);
        require(grantA.equals(grantB),
                "An irrelevant project id changed a direct-grant confirmation");

        FundConfirmationFingerprint activeGrant = fingerprint(
                25, EconomyState.ProsperityFundType.DIRECT_GRANT,
                EconomyState.DonationPurpose.GENERAL, village,
                VillageProsperityEngine.Lifecycle.ACTIVE, 0L);
        FundConfirmationFingerprint abandonedGrant = fingerprint(
                25, EconomyState.ProsperityFundType.DIRECT_GRANT,
                EconomyState.DonationPurpose.GENERAL, village,
                VillageProsperityEngine.Lifecycle.ABANDONED, 0L);
        require(abandonedGrant.purpose() == EconomyState.DonationPurpose.RESTORATION,
                "An abandoned-village grant did not bind its effective restoration purpose");
        requireDifferent(activeGrant, abandonedGrant, "village lifecycle/effective purpose");
        System.out.println("PASS Fund confirmation fingerprint regression tests");
    }

    private static FundConfirmationFingerprint fingerprint(
            int amount,
            EconomyState.ProsperityFundType type,
            EconomyState.DonationPurpose purpose,
            UUID village,
            long projectId) {
        return fingerprint(
                amount, type, purpose, village,
                VillageProsperityEngine.Lifecycle.ACTIVE, projectId);
    }

    private static FundConfirmationFingerprint fingerprint(
            int amount,
            EconomyState.ProsperityFundType type,
            EconomyState.DonationPurpose purpose,
            UUID village,
            VillageProsperityEngine.Lifecycle lifecycle,
            long projectId) {
        return new FundConfirmationFingerprint(
                amount, type, purpose, village, lifecycle, projectId);
    }

    private static void requireDifferent(
            FundConfirmationFingerprint expected,
            FundConfirmationFingerprint changed,
            String field) {
        require(!expected.equals(changed), "Changed Fund " + field + " still matched");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
