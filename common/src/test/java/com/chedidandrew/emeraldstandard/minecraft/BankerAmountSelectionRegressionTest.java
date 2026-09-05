package com.chedidandrew.emeraldstandard.minecraft;

import com.chedidandrew.emeraldstandard.client.BankerScreenLayout;

/** Regression checks for typed Banker amounts and their server-bound button ids. */
public final class BankerAmountSelectionRegressionTest {
    private BankerAmountSelectionRegressionTest() {
    }

    public static void main(String[] args) {
        testStrictParsing();
        testButtonEncoding();
        testStaleAvailabilityIsRecapped();
        testFooterControlsFit();
        System.out.println("PASS Banker typed-amount regression tests");
    }

    private static void testStrictParsing() {
        require(BankerAmountSelection.parseAppliedAmount("1") == 1,
                "Minimum typed amount was rejected");
        require(BankerAmountSelection.parseAppliedAmount("000032") == 32,
                "A harmless leading zero changed the typed amount");
        require(BankerAmountSelection.parseAppliedAmount("1000000") == 1_000_000,
                "Maximum typed amount was rejected");
        String[] invalid = {
            null, "", "0", "-1", "+1", "1.0", " 32", "32 ", "1,000", "1000001",
            "2147483647", "999999999999999999999999"
        };
        for (String text : invalid) {
            require(BankerAmountSelection.parseAppliedAmount(text) == 0,
                    "Malformed or overflowing amount was accepted: " + text);
        }
    }

    private static void testButtonEncoding() {
        int minimum = BankerAmountSelection.encodeButtonId(1);
        int maximum = BankerAmountSelection.encodeButtonId(1_000_000);
        int fundMinimum = BankerAmountSelection.encodeFundButtonId(1);
        int fundMaximum = BankerAmountSelection.encodeFundButtonId(1_000_000);
        require(BankerAmountSelection.decodeButtonId(minimum) == 1,
                "Minimum amount did not survive button encoding");
        require(BankerAmountSelection.decodeButtonId(maximum) == 1_000_000,
                "Maximum amount did not survive button encoding");
        require(BankerAmountSelection.decodeFundButtonId(fundMinimum) == 1
                        && BankerAmountSelection.decodeFundButtonId(fundMaximum) == 1_000_000,
                "A Fund contribution did not survive its button encoding");
        require(BankerAmountSelection.decodeButtonId(fundMinimum) == 0
                        && BankerAmountSelection.decodeButtonId(fundMaximum) == 0
                        && BankerAmountSelection.decodeFundButtonId(minimum) == 0
                        && BankerAmountSelection.decodeFundButtonId(maximum) == 0,
                "Ordinary and Fund exact amounts share a packet range");
        require(BankerAmountSelection.encodeButtonId(0) == -1
                        && BankerAmountSelection.encodeButtonId(1_000_001) == -1
                        && BankerAmountSelection.encodeFundButtonId(0) == -1
                        && BankerAmountSelection.encodeFundButtonId(1_000_001) == -1,
                "Out-of-range amount was encoded");
        require(BankerAmountSelection.decodeButtonId(BankerAmountSelection.BUTTON_BASE) == 0
                        && BankerAmountSelection.decodeButtonId(
                                BankerAmountSelection.BUTTON_BASE + 1_000_001) == 0
                        && BankerAmountSelection.decodeFundButtonId(
                                BankerAmountSelection.FUND_BUTTON_BASE) == 0
                        && BankerAmountSelection.decodeFundButtonId(
                                BankerAmountSelection.FUND_BUTTON_BASE + 1_000_001) == 0
                        && BankerAmountSelection.decodeButtonId(Integer.MAX_VALUE) == 0
                        && BankerAmountSelection.decodeFundButtonId(Integer.MAX_VALUE) == 0
                        && BankerAmountSelection.decodeButtonId(Integer.MIN_VALUE) == 0,
                "Malformed or overflowing custom button id was accepted");
    }

    private static void testStaleAvailabilityIsRecapped() {
        int selected = BankerAmountSelection.parseAppliedAmount("750000");
        require(BankerScreenLayout.resolvedWholeAmount(selected, 12.9) == 12,
                "A typed amount was not recapped against the current source balance");
        require(BankerScreenLayout.resolvedWholeAmount(selected, 0.99) == 0,
                "A stale typed amount enabled a fractional-only source");
    }

    private static void testFooterControlsFit() {
        BankerScreenLayout.Rect screen = new BankerScreenLayout.Rect(
                0, 0, BankerScreenLayout.WIDTH, BankerScreenLayout.HEIGHT);
        BankerScreenLayout.Rect label = new BankerScreenLayout.Rect(
                12,
                BankerScreenLayout.AMOUNT_LABEL_Y,
                BankerScreenLayout.WIDTH - 24,
                9);
        BankerScreenLayout.Rect[] controls = {
            new BankerScreenLayout.Rect(
                    BankerScreenLayout.AMOUNT_INPUT_X,
                    BankerScreenLayout.AMOUNT_BUTTON_Y,
                    BankerScreenLayout.AMOUNT_INPUT_WIDTH,
                    BankerScreenLayout.AMOUNT_CONTROL_HEIGHT),
            new BankerScreenLayout.Rect(
                    BankerScreenLayout.AMOUNT_APPLY_X,
                    BankerScreenLayout.AMOUNT_BUTTON_Y,
                    BankerScreenLayout.AMOUNT_APPLY_WIDTH,
                    BankerScreenLayout.AMOUNT_CONTROL_HEIGHT),
            new BankerScreenLayout.Rect(
                    BankerScreenLayout.AMOUNT_CANCEL_X,
                    BankerScreenLayout.AMOUNT_BUTTON_Y,
                    BankerScreenLayout.AMOUNT_CANCEL_WIDTH,
                    BankerScreenLayout.AMOUNT_CONTROL_HEIGHT),
            new BankerScreenLayout.Rect(
                    BankerScreenLayout.AMOUNT_ALL_X,
                    BankerScreenLayout.AMOUNT_BUTTON_Y,
                    BankerScreenLayout.AMOUNT_ALL_WIDTH,
                    BankerScreenLayout.AMOUNT_CONTROL_HEIGHT)
        };
        for (int index = 0; index < controls.length; index++) {
            BankerScreenLayout.Rect control = controls[index];
            require(contains(screen, control) && !label.overlaps(control),
                    "A typed-amount footer control escapes the screen or covers its label");
            for (int other = index + 1; other < controls.length; other++) {
                require(!control.overlaps(controls[other]),
                        "Typed-amount footer controls overlap");
            }
        }
    }

    private static boolean contains(
            BankerScreenLayout.Rect outer, BankerScreenLayout.Rect inner) {
        return inner.x() >= outer.x()
                && inner.y() >= outer.y()
                && inner.right() <= outer.right()
                && inner.bottom() <= outer.bottom();
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
