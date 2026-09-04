package com.chedidandrew.emeraldstandard.client;

/**
 * Loader-neutral geometry for the Banker screen.
 *
 * <p>Keeping the dense Market and Banking rows here makes their non-overlap guarantees
 * inexpensive to regression-test without launching a Minecraft client.</p>
 */
public final class BankerScreenLayout {
    public static final int WIDTH = 320;
    public static final int HEIGHT = 230;

    public static final int TAB_X = 10;
    public static final int TAB_Y = 27;
    public static final int TAB_STEP = 43;
    public static final int TAB_WIDTH = 42;
    public static final int TAB_HEIGHT = 18;
    public static final int TAB_COUNT = 7;

    public static final int HISTORY_BUTTON_X = 268;
    public static final int HISTORY_BUTTON_Y = 48;
    public static final int HISTORY_BUTTON_WIDTH = 40;
    public static final int HISTORY_BUTTON_HEIGHT = 12;

    public static final int OVERVIEW_CHART_X = 111;
    public static final int OVERVIEW_CHART_Y = 62;
    public static final int OVERVIEW_CHART_WIDTH = 196;
    public static final int OVERVIEW_CHART_HEIGHT = 83;
    public static final int OVERVIEW_VALUE_X = 74;
    public static final int OVERVIEW_VALUE_WIDTH = 33;

    public static final int MARKET_CHART_X = 111;
    public static final int MARKET_CHART_Y = 62;
    public static final int MARKET_CHART_WIDTH = 196;
    public static final int MARKET_CHART_HEIGHT = 58;
    public static final int MARKET_TITLE_X = 118;
    public static final int MARKET_TITLE_Y = 51;
    public static final int MARKET_TITLE_WIDTH = 145;
    public static final int MARKET_PRICE_Y = 123;
    public static final int MARKET_HOLDING_Y = 134;
    public static final int MARKET_AVERAGE_Y = 145;
    public static final int MARKET_BASIS_Y = 156;
    public static final int MARKET_ACTION_Y = 168;

    public static final int BANKING_PRIMARY_ACTION_Y = 103;
    public static final int BANKING_LENDING_ACTION_Y = 125;
    public static final int BANKING_CD_CONTROL_Y = 147;
    public static final int BANKING_LENDING_CONTROL_Y = 170;

    public static final int ACTIVITY_FIRST_ROW_Y = 83;
    public static final int ACTIVITY_ROW_STEP = 15;
    public static final int ACTIVITY_ROWS = 5;

    public static final int FUND_TITLE_X = 16;
    public static final int FUND_TITLE_WIDTH = 144;
    public static final int FUND_DONOR_X = 168;
    public static final int FUND_DONOR_Y = 59;
    public static final int FUND_DONOR_WIDTH = 140;
    public static final int FUND_CONTROL_Y = 101;

    public static final int FOOTER_Y = 188;
    public static final int AMOUNT_LABEL_Y = 198;
    public static final int AMOUNT_BUTTON_Y = 207;

    static final int TEXT_HEIGHT = 9;

    private BankerScreenLayout() {
    }

    /** A half-open rectangle, matching Minecraft's fill and widget bounds. */
    public record Rect(int x, int y, int width, int height) {
        public int right() {
            return x + width;
        }

        public int bottom() {
            return y + height;
        }

        public boolean overlaps(Rect other) {
            return x < other.right()
                    && right() > other.x
                    && y < other.bottom()
                    && bottom() > other.y;
        }
    }
}
