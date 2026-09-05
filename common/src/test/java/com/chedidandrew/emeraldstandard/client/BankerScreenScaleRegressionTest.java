package com.chedidandrew.emeraldstandard.client;

/** Boundary checks for adaptive Banker-screen geometry and inverse mouse mapping. */
public final class BankerScreenScaleRegressionTest {
    private BankerScreenScaleRegressionTest() {
    }

    public static void main(String[] args) {
        float large = BankerScreenScale.fit(1280, 720, 320, 230);
        check(close(large, BankerScreenScale.MAX_SCALE), "Large windows should use the capped scale");

        float compact = BankerScreenScale.fit(320, 240, 320, 230);
        check(compact < 1.0F && compact >= BankerScreenScale.MIN_SCALE,
                "Compact windows should shrink without becoming unusable");
        check(BankerScreenScale.scaled(320, compact) <= 320 - BankerScreenScale.MARGIN,
                "Compact width must fit its margin");
        check(BankerScreenScale.scaled(230, compact) <= 240 - BankerScreenScale.MARGIN,
                "Compact height must fit its margin");

        int origin = BankerScreenScale.origin(854, 320, 1.4F);
        check(origin == 203, "Scaled dashboard should remain horizontally centered");
        double actual = origin + 100.0 * 1.4;
        check(Math.abs(BankerScreenScale.toLogical(actual, origin, 1.4F) - (origin + 100.0)) < 0.001,
                "Mouse coordinates should invert the visual transform");
        check(BankerScreenScale.logicalSpanForNativePixels(9, compact) * compact >= 9.0F
                        && BankerScreenScale.logicalSpanForNativePixels(9, large) * large >= 9.0F,
                "Responsive label hover bounds must cover the native-size font");
        System.out.println("PASS responsive Banker screen geometry");
    }

    private static boolean close(float first, float second) {
        return Math.abs(first - second) < 0.0001F;
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
