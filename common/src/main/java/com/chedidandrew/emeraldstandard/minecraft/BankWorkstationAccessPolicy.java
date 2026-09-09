package com.chedidandrew.emeraldstandard.minecraft;

/** Loader-neutral routing policy for lectern and Exchange Desk interactions. */
public final class BankWorkstationAccessPolicy {
    private BankWorkstationAccessPolicy() {
    }

    /**
     * Keeps player banking available without allowing an unsafe authored Bank's Banker to work.
     *
     * <p>A live Exchange Desk at an active but unsafe Bank falls back to ordinary personal-desk
     * access. The scoped Banker remains governed by the stricter building-integrity check. Retired
     * coordinates win over every other classification because a surviving workstation at an
     * abandoned authored Bank must not become personal access merely because its replacement was
     * built elsewhere.</p>
     */
    public static Decision decide(
            boolean bankWorkstation,
            boolean exchangeDesk,
            boolean activeBankCounter,
            boolean activeBankOperational,
            boolean retiredBankCounter) {
        if (!bankWorkstation) {
            return Decision.IGNORE;
        }
        if (retiredBankCounter) {
            return Decision.RETIRED_INERT;
        }
        if (activeBankCounter) {
            if (activeBankOperational) {
                return Decision.MANAGED;
            }
            return exchangeDesk
                    ? Decision.PERSONAL_UNSAFE_BANK
                    : Decision.UNSAFE_LEGACY_INERT;
        }
        return exchangeDesk ? Decision.PERSONAL : Decision.IGNORE;
    }

    public enum Decision {
        IGNORE,
        MANAGED,
        PERSONAL,
        PERSONAL_UNSAFE_BANK,
        UNSAFE_LEGACY_INERT,
        RETIRED_INERT;

        public boolean opensDashboard() {
            return this == MANAGED || this == PERSONAL || this == PERSONAL_UNSAFE_BANK;
        }

        public boolean warnsUnsafeBank() {
            return this == PERSONAL_UNSAFE_BANK || this == UNSAFE_LEGACY_INERT;
        }
    }
}
