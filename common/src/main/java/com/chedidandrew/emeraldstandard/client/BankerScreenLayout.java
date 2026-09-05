package com.chedidandrew.emeraldstandard.client;

/**
 * Loader-neutral geometry for the Banker screen.
 *
 * <p>Keeping dense row geometry and small tooltip-state decisions here makes their UI
 * guarantees inexpensive to regression-test without launching a Minecraft client.</p>
 */
public final class BankerScreenLayout {
    public static final int WIDTH = 320;
    public static final int HEIGHT = 230;
    public static final int TOOLTIP_WRAP_WIDTH = 180;

    public static final int TAB_X = 10;
    public static final int TAB_Y = 27;
    public static final int TAB_STEP = 43;
    public static final int TAB_WIDTH = 42;
    public static final int TAB_HEIGHT = 18;
    public static final int TAB_COUNT = 7;
    public static final int TAB_INDICATOR_Y = TAB_Y + TAB_HEIGHT;
    public static final int TAB_INDICATOR_HEIGHT = 2;

    public static final int HISTORY_BUTTON_X = 268;
    public static final int HISTORY_BUTTON_Y = 48;
    public static final int HISTORY_BUTTON_WIDTH = 40;
    public static final int HISTORY_BUTTON_HEIGHT = 12;

    public static final int OVERVIEW_CHART_X = 111;
    public static final int OVERVIEW_CHART_Y = 62;
    public static final int OVERVIEW_CHART_WIDTH = 196;
    public static final int OVERVIEW_CHART_HEIGHT = 83;
    public static final int OVERVIEW_VALUE_X = 66;
    public static final int OVERVIEW_VALUE_WIDTH = 45;
    public static final int OVERVIEW_PERFORMANCE_X = 14;
    public static final int OVERVIEW_PERFORMANCE_Y = 149;
    public static final int OVERVIEW_PERFORMANCE_WIDTH = 93;
    public static final int OVERVIEW_HISTORY_CHANGE_X = 118;
    public static final int OVERVIEW_HISTORY_CHANGE_Y = 149;
    public static final int OVERVIEW_HISTORY_CHANGE_WIDTH = 189;
    public static final int OVERVIEW_CHART_TITLE_WIDTH = 145;

    public static final int MARKET_CHART_X = 111;
    public static final int MARKET_CHART_Y = 62;
    public static final int MARKET_CHART_WIDTH = 196;
    public static final int MARKET_CHART_HEIGHT = 58;
    public static final int MARKET_TITLE_X = 118;
    public static final int MARKET_TITLE_Y = 51;
    public static final int MARKET_TITLE_WIDTH = 145;
    public static final int MARKET_SELECTOR_LABEL_X = 14;
    public static final int MARKET_SELECTOR_LABEL_Y = 55;
    public static final int MARKET_PREVIOUS_X = 12;
    public static final int MARKET_ASSET_X = 36;
    public static final int MARKET_NEXT_X = 87;
    public static final int MARKET_SELECTOR_Y = 68;
    public static final int MARKET_ARROW_WIDTH = 22;
    public static final int MARKET_ASSET_WIDTH = 49;
    public static final int MARKET_SELECTOR_HEIGHT = 18;
    public static final int MARKET_SECTOR_Y = 92;
    public static final int MARKET_RISK_Y = 104;
    public static final int MARKET_META_WIDTH = 93;
    public static final int MARKET_DETAIL_X = 118;
    public static final int MARKET_DETAIL_WIDTH = 189;
    public static final int MARKET_PRICE_Y = 123;
    public static final int MARKET_HOLDING_Y = 134;
    public static final int MARKET_AVERAGE_Y = 145;
    public static final int MARKET_BASIS_Y = 156;
    public static final int MARKET_ACTION_Y = 168;
    public static final int MARKET_BULLETIN_X = 12;
    public static final int MARKET_BULLETIN_WIDTH = 296;

    public static final int BANKING_SUBTAB_Y = 54;
    public static final int BANKING_SUBTAB_HEIGHT = 18;
    public static final int BANKING_SUBTAB_X = 12;
    public static final int BANKING_SUBTAB_STEP = 99;
    public static final int BANKING_SUBTAB_WIDTH = 96;
    public static final int BANKING_SUBTAB_COUNT = 3;
    public static final int BANKING_BALANCE_PANEL_Y = 76;
    public static final int BANKING_BALANCE_PANEL_HEIGHT = 40;
    public static final int BANKING_PRIMARY_ACTION_Y = 123;
    public static final int BANKING_SECONDARY_ACTION_Y = 146;
    public static final int BANKING_PRODUCT_CONTROL_Y = 119;
    public static final int BANKING_PRODUCT_ACTION_Y = 143;
    public static final int BANKING_PRODUCT_DETAIL_Y = 166;
    public static final int BANKING_PRODUCT_PREVIEW_Y = 178;

    public static final int ACTIVITY_FIRST_ROW_Y = 83;
    public static final int ACTIVITY_ROW_STEP = 15;
    public static final int ACTIVITY_ROWS = 5;
    public static final int ACTIVITY_LIST_X = 16;
    public static final int ACTIVITY_LIST_WIDTH = 266;
    public static final int ACTIVITY_POSITION_X = 210;
    public static final int ACTIVITY_POSITION_WIDTH = 72;
    public static final int ACTIVITY_SCROLL_BUTTON_X = 286;
    public static final int ACTIVITY_SCROLL_BUTTON_WIDTH = 20;
    public static final int ACTIVITY_SCROLL_BUTTON_HEIGHT = 16;
    public static final int ACTIVITY_SCROLL_UP_Y = 81;
    public static final int ACTIVITY_SCROLL_DOWN_Y = 143;
    public static final int ACTIVITY_SCROLL_TRACK_X = 294;
    public static final int ACTIVITY_SCROLL_TRACK_Y = 100;
    public static final int ACTIVITY_SCROLL_TRACK_WIDTH = 4;
    public static final int ACTIVITY_SCROLL_TRACK_HEIGHT = 40;
    public static final int ACTIVITY_FILTER_X = 16;
    public static final int ACTIVITY_FILTER_Y = 157;
    public static final int ACTIVITY_FILTER_WIDTH = 188;
    public static final int ACTIVITY_FILTER_HEIGHT = 18;
    public static final int ACTIVITY_ORDER_X = 210;
    public static final int ACTIVITY_ORDER_Y = 162;
    public static final int ACTIVITY_ORDER_WIDTH = 72;

    public static final int FUND_TITLE_X = 16;
    public static final int FUND_TITLE_WIDTH = 144;
    public static final int FUND_DONOR_X = 168;
    public static final int FUND_DONOR_Y = 59;
    public static final int FUND_DONOR_WIDTH = 140;
    public static final int FUND_CONTROL_Y = 101;
    public static final int FUND_PROJECT_Y = 125;
    public static final int FUND_NOTICE_Y = 141;

    public static final int VILLAGE_LEFT_PANEL_X = 10;
    public static final int VILLAGE_LEFT_PANEL_WIDTH = 145;
    public static final int VILLAGE_RIGHT_PANEL_X = 165;
    public static final int VILLAGE_RIGHT_PANEL_WIDTH = 143;
    public static final int VILLAGE_PANEL_Y = 54;
    public static final int VILLAGE_PANEL_HEIGHT = 122;
    public static final int VILLAGE_TOP_SECTION_HEIGHT = 90;
    public static final int VILLAGE_LEFT_TEXT_X = 16;
    public static final int VILLAGE_LEFT_TEXT_WIDTH = 133;
    public static final int VILLAGE_RIGHT_TEXT_X = 171;
    public static final int VILLAGE_RIGHT_TEXT_WIDTH = 131;
    public static final int VILLAGE_PRIMARY_DETAIL_Y = 148;
    public static final int VILLAGE_SECONDARY_DETAIL_Y = 162;

    public static final int FOOTER_Y = 188;
    public static final int AMOUNT_LABEL_Y = 198;
    public static final int AMOUNT_BUTTON_Y = 207;
    public static final int AMOUNT_INPUT_X = 12;
    public static final int AMOUNT_INPUT_WIDTH = 118;
    public static final int AMOUNT_APPLY_X = 134;
    public static final int AMOUNT_APPLY_WIDTH = 52;
    public static final int AMOUNT_CANCEL_X = 190;
    public static final int AMOUNT_CANCEL_WIDTH = 58;
    public static final int AMOUNT_ALL_X = 252;
    public static final int AMOUNT_ALL_WIDTH = 56;
    public static final int AMOUNT_CONTROL_HEIGHT = 18;

    static final int TEXT_HEIGHT = 9;

    private BankerScreenLayout() {
    }

    public static CdCloseTooltipState cdCloseTooltipState(
            boolean hasCd, int daysRemaining) {
        if (!hasCd) {
            return CdCloseTooltipState.UNAVAILABLE;
        }
        return daysRemaining > 0
                ? CdCloseTooltipState.EARLY
                : CdCloseTooltipState.MATURE;
    }

    public static FundPurposeTooltipState fundPurposeTooltipState(
            boolean fundAvailable,
            boolean targetedDonationsEnabled,
            boolean restorationGrant,
            boolean projectSponsorship) {
        if (!fundAvailable) {
            return FundPurposeTooltipState.FUND_UNAVAILABLE;
        }
        if (!targetedDonationsEnabled) {
            return FundPurposeTooltipState.TARGETING_DISABLED;
        }
        if (restorationGrant) {
            return FundPurposeTooltipState.RESTORATION_FIXED;
        }
        if (projectSponsorship) {
            return FundPurposeTooltipState.PROJECT_FIXED;
        }
        return FundPurposeTooltipState.CYCLABLE;
    }

    /** Resolves a whole-emerald preset against the source balance used by an action. */
    public static int resolvedWholeAmount(int preset, double available) {
        if (!Double.isFinite(available) || available < 1.0) {
            return 0;
        }
        long wholeAvailable = Math.min(Integer.MAX_VALUE, (long) Math.floor(available));
        return (int) (preset < 0 ? wholeAvailable : Math.min(Math.max(0, preset), wholeAvailable));
    }

    public enum CdCloseTooltipState {
        UNAVAILABLE,
        EARLY,
        MATURE
    }

    public enum FundPurposeTooltipState {
        CYCLABLE,
        FUND_UNAVAILABLE,
        TARGETING_DISABLED,
        RESTORATION_FIXED,
        PROJECT_FIXED
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
