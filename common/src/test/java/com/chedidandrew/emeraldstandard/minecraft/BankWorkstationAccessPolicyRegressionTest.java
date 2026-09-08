package com.chedidandrew.emeraldstandard.minecraft;

/** Regression matrix for generated, personal, unsafe, and retired workstation access. */
public final class BankWorkstationAccessPolicyRegressionTest {
    private BankWorkstationAccessPolicyRegressionTest() {
    }

    public static void main(String[] args) {
        expect(
                BankWorkstationAccessPolicy.Decision.IGNORE,
                false, false, false, false, false,
                "A normal block was treated as banking access");
        expect(
                BankWorkstationAccessPolicy.Decision.PERSONAL,
                true, true, false, false, false,
                "A player Exchange Desk did not open personal banking");
        expect(
                BankWorkstationAccessPolicy.Decision.MANAGED,
                true, true, true, true, false,
                "An intact generated Exchange Desk lost managed access");
        expect(
                BankWorkstationAccessPolicy.Decision.MANAGED,
                true, false, true, true, false,
                "An intact scoped legacy lectern lost managed access");
        expect(
                BankWorkstationAccessPolicy.Decision.PERSONAL_UNSAFE_BANK,
                true, true, true, false, false,
                "An unsafe active Bank stranded its intact Exchange Desk");
        expect(
                BankWorkstationAccessPolicy.Decision.UNSAFE_LEGACY_INERT,
                true, false, true, false, false,
                "An unsafe legacy lectern fell through as personal access");
        expect(
                BankWorkstationAccessPolicy.Decision.RETIRED_INERT,
                true, true, false, false, true,
                "A retired generated Exchange Desk became personal access");
        expect(
                BankWorkstationAccessPolicy.Decision.RETIRED_INERT,
                true, true, true, true, true,
                "An active-map inconsistency overrode retired-site safety");
        expect(
                BankWorkstationAccessPolicy.Decision.IGNORE,
                true, false, false, false, false,
                "An arbitrary lectern became a Bank counter");
        System.out.println("PASS bank workstation access policy regression");
    }

    private static void expect(
            BankWorkstationAccessPolicy.Decision expected,
            boolean bankWorkstation,
            boolean exchangeDesk,
            boolean activeBankCounter,
            boolean activeBankOperational,
            boolean retiredBankCounter,
            String message) {
        BankWorkstationAccessPolicy.Decision actual = BankWorkstationAccessPolicy.decide(
                bankWorkstation,
                exchangeDesk,
                activeBankCounter,
                activeBankOperational,
                retiredBankCounter);
        if (actual != expected) {
            throw new AssertionError(message + ": expected " + expected + ", got " + actual);
        }
        if (actual.opensDashboard()
                != (actual == BankWorkstationAccessPolicy.Decision.MANAGED
                        || actual == BankWorkstationAccessPolicy.Decision.PERSONAL
                        || actual == BankWorkstationAccessPolicy.Decision.PERSONAL_UNSAFE_BANK)) {
            throw new AssertionError("Dashboard-open classification drifted for " + actual);
        }
    }
}
