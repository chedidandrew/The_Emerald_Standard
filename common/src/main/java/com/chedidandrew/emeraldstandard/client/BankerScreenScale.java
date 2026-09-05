package com.chedidandrew.emeraldstandard.client;

/** Pure geometry for fitting the Banker dashboard to the current logical window size. */
public final class BankerScreenScale {
    public static final int MARGIN = 20;
    public static final float MIN_SCALE = 0.75F;
    public static final float MAX_SCALE = 1.40F;

    private BankerScreenScale() {
    }

    public static float fit(int screenWidth, int screenHeight, int contentWidth, int contentHeight) {
        if (screenWidth <= 0 || screenHeight <= 0 || contentWidth <= 0 || contentHeight <= 0) {
            return 1.0F;
        }
        float horizontal = Math.max(1, screenWidth - MARGIN) / (float) contentWidth;
        float vertical = Math.max(1, screenHeight - MARGIN) / (float) contentHeight;
        return Math.max(MIN_SCALE, Math.min(MAX_SCALE, Math.min(horizontal, vertical)));
    }

    public static int scaled(int value, float scale) {
        return Math.max(1, Math.round(value * scale));
    }

    public static int origin(int screenSize, int contentSize, float scale) {
        return Math.round((screenSize - contentSize * scale) / 2.0F);
    }

    public static double toLogical(double screenCoordinate, int origin, float scale) {
        return origin + (screenCoordinate - origin) / Math.max(MIN_SCALE, scale);
    }

    /** Logical span whose transformed size still covers a native-size label or hover target. */
    public static int logicalSpanForNativePixels(int pixels, float scale) {
        if (pixels <= 0) {
            return 0;
        }
        return Math.max(1, (int) Math.ceil(pixels / Math.max(MIN_SCALE, scale)));
    }
}
