package com.chedidandrew.emeraldstandard.minecraft;

import com.chedidandrew.emeraldstandard.core.EconomyService;

/**
 * Loader-neutral validation and button encoding for the Banker's exact amount field.
 *
 * <p>The client uses these helpers for immediate feedback, but the menu decodes and
 * validates the selection again on the server. Zero is reserved as the invalid/no
 * selection sentinel. Ordinary transactions and Prosperity Fund drafts use
 * deliberately disjoint packet ranges so a stale or forged amount cannot cross
 * from one workflow into the other.</p>
 */
public final class BankerAmountSelection {
    public static final int MIN_AMOUNT = 1;
    public static final int MAX_AMOUNT =
            (int) EconomyService.MAX_WHOLE_EMERALD_TRANSACTION;
    public static final int BUTTON_BASE = 1_500_000_000;
    public static final int FUND_BUTTON_BASE = 1_600_000_000;

    private BankerAmountSelection() {
    }

    /** Returns a validated whole-emerald amount, or zero when the text cannot be applied. */
    public static int parseAppliedAmount(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        int value = 0;
        for (int index = 0; index < text.length(); index++) {
            char digit = text.charAt(index);
            if (digit < '0' || digit > '9') {
                return 0;
            }
            value = value * 10 + (digit - '0');
            if (value > MAX_AMOUNT) {
                return 0;
            }
        }
        return value >= MIN_AMOUNT ? value : 0;
    }

    /** Encodes a validated amount for the vanilla inventory-button packet. */
    public static int encodeButtonId(int amount) {
        return encodeButtonId(amount, BUTTON_BASE);
    }

    /** Encodes a validated exact Prosperity Fund draft amount. */
    public static int encodeFundButtonId(int amount) {
        return encodeButtonId(amount, FUND_BUTTON_BASE);
    }

    /** Decodes and validates a custom-amount button id, returning zero when malformed. */
    public static int decodeButtonId(int buttonId) {
        return decodeButtonId(buttonId, BUTTON_BASE);
    }

    /** Decodes only the Prosperity Fund amount range. */
    public static int decodeFundButtonId(int buttonId) {
        return decodeButtonId(buttonId, FUND_BUTTON_BASE);
    }

    private static int encodeButtonId(int amount, int base) {
        if (amount < MIN_AMOUNT || amount > MAX_AMOUNT) {
            return -1;
        }
        return base + amount;
    }

    private static int decodeButtonId(int buttonId, int base) {
        long amount = (long) buttonId - base;
        return amount >= MIN_AMOUNT && amount <= MAX_AMOUNT ? (int) amount : 0;
    }
}
