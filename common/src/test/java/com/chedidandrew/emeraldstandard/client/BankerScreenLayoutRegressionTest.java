package com.chedidandrew.emeraldstandard.client;

/** Regression checks for the fixed 320x230 Banker screen geometry. */
public final class BankerScreenLayoutRegressionTest {
    private BankerScreenLayoutRegressionTest() {
    }

    public static void main(String[] args) {
        testTabStripFits();
        testHistoryControlDoesNotCoverCharts();
        testOverviewValuesStayOutsideChart();
        testMarketRowsDoNotCollide();
        testBankingRowsDoNotCollide();
        testFundHeaderDoesNotCollideWithControls();
        testActivityRowsStayAboveFooter();
        System.out.println("PASS Banker screen layout regression tests");
    }

    private static void testOverviewValuesStayOutsideChart() {
        BankerScreenLayout.Rect value = new BankerScreenLayout.Rect(
                BankerScreenLayout.OVERVIEW_VALUE_X,
                72,
                BankerScreenLayout.OVERVIEW_VALUE_WIDTH,
                BankerScreenLayout.TEXT_HEIGHT);
        BankerScreenLayout.Rect chart = new BankerScreenLayout.Rect(
                BankerScreenLayout.OVERVIEW_CHART_X,
                BankerScreenLayout.OVERVIEW_CHART_Y,
                BankerScreenLayout.OVERVIEW_CHART_WIDTH,
                BankerScreenLayout.OVERVIEW_CHART_HEIGHT);
        require(!value.overlaps(chart),
                "Overview values overlap the personal-history chart");
    }

    private static void testHistoryControlDoesNotCoverCharts() {
        BankerScreenLayout.Rect button = new BankerScreenLayout.Rect(
                BankerScreenLayout.HISTORY_BUTTON_X,
                BankerScreenLayout.HISTORY_BUTTON_Y,
                BankerScreenLayout.HISTORY_BUTTON_WIDTH,
                BankerScreenLayout.HISTORY_BUTTON_HEIGHT);
        BankerScreenLayout.Rect overviewChart = new BankerScreenLayout.Rect(
                BankerScreenLayout.OVERVIEW_CHART_X,
                BankerScreenLayout.OVERVIEW_CHART_Y,
                BankerScreenLayout.OVERVIEW_CHART_WIDTH,
                BankerScreenLayout.OVERVIEW_CHART_HEIGHT);
        BankerScreenLayout.Rect marketChart = new BankerScreenLayout.Rect(
                BankerScreenLayout.MARKET_CHART_X,
                BankerScreenLayout.MARKET_CHART_Y,
                BankerScreenLayout.MARKET_CHART_WIDTH,
                BankerScreenLayout.MARKET_CHART_HEIGHT);
        BankerScreenLayout.Rect marketTitle = new BankerScreenLayout.Rect(
                BankerScreenLayout.MARKET_TITLE_X,
                BankerScreenLayout.MARKET_TITLE_Y,
                BankerScreenLayout.MARKET_TITLE_WIDTH,
                BankerScreenLayout.TEXT_HEIGHT);
        require(!button.overlaps(overviewChart),
                "History-range button overlaps the overview chart");
        require(!button.overlaps(marketChart),
                "History-range button overlaps the market chart");
        require(!button.overlaps(marketTitle),
                "History-range button overlaps the selected market title");
    }

    private static void testTabStripFits() {
        BankerScreenLayout.Rect last = new BankerScreenLayout.Rect(
                BankerScreenLayout.TAB_X
                        + (BankerScreenLayout.TAB_COUNT - 1) * BankerScreenLayout.TAB_STEP,
                BankerScreenLayout.TAB_Y,
                BankerScreenLayout.TAB_WIDTH,
                BankerScreenLayout.TAB_HEIGHT);
        require(last.right() <= BankerScreenLayout.WIDTH - 10,
                "The seven-tab strip overflows the Banker screen");
    }

    private static void testMarketRowsDoNotCollide() {
        BankerScreenLayout.Rect chart = new BankerScreenLayout.Rect(
                BankerScreenLayout.MARKET_CHART_X,
                BankerScreenLayout.MARKET_CHART_Y,
                BankerScreenLayout.MARKET_CHART_WIDTH,
                BankerScreenLayout.MARKET_CHART_HEIGHT);
        BankerScreenLayout.Rect[] rows = {
                textRow(BankerScreenLayout.MARKET_PRICE_Y),
                textRow(BankerScreenLayout.MARKET_HOLDING_Y),
                textRow(BankerScreenLayout.MARKET_AVERAGE_Y),
                textRow(BankerScreenLayout.MARKET_BASIS_Y)
        };
        for (int index = 0; index < rows.length; index++) {
            require(!chart.overlaps(rows[index]), "Market detail row overlaps the chart");
            for (int other = index + 1; other < rows.length; other++) {
                require(!rows[index].overlaps(rows[other]),
                        "Market detail rows overlap each other");
            }
        }
        BankerScreenLayout.Rect actions = new BankerScreenLayout.Rect(
                112, BankerScreenLayout.MARKET_ACTION_Y, 196, 18);
        require(!rows[rows.length - 1].overlaps(actions),
                "Market basis text overlaps the action row");
        require(!actions.overlaps(textRow(BankerScreenLayout.FOOTER_Y)),
                "Market action row overlaps the footer");
    }

    private static void testBankingRowsDoNotCollide() {
        BankerScreenLayout.Rect primaryActions = new BankerScreenLayout.Rect(
                12, BankerScreenLayout.BANKING_PRIMARY_ACTION_Y, 296, 18);
        BankerScreenLayout.Rect lendingActions = new BankerScreenLayout.Rect(
                166, BankerScreenLayout.BANKING_LENDING_ACTION_Y, 142, 18);
        BankerScreenLayout.Rect riskNotice = new BankerScreenLayout.Rect(
                14, BankerScreenLayout.BANKING_LENDING_ACTION_Y + 4, 145,
                BankerScreenLayout.TEXT_HEIGHT);
        BankerScreenLayout.Rect cdControls = new BankerScreenLayout.Rect(
                67, BankerScreenLayout.BANKING_CD_CONTROL_Y, 241, 18);
        BankerScreenLayout.Rect lendingControls = new BankerScreenLayout.Rect(
                67, BankerScreenLayout.BANKING_LENDING_CONTROL_Y, 241, 18);
        BankerScreenLayout.Rect footer = textRow(BankerScreenLayout.FOOTER_Y);

        require(!primaryActions.overlaps(lendingActions),
                "Primary and lending action rows overlap");
        require(!riskNotice.overlaps(lendingActions),
                "The lending risk notice overlaps lending actions");
        require(!lendingActions.overlaps(cdControls),
                "Lending actions overlap CD controls");
        require(!cdControls.overlaps(lendingControls),
                "CD and lending controls overlap");
        require(!lendingControls.overlaps(footer),
                "Lending controls overlap the footer");
    }

    private static void testActivityRowsStayAboveFooter() {
        for (int index = 0; index < BankerScreenLayout.ACTIVITY_ROWS; index++) {
            BankerScreenLayout.Rect row = textRow(
                    BankerScreenLayout.ACTIVITY_FIRST_ROW_Y
                            + index * BankerScreenLayout.ACTIVITY_ROW_STEP);
            require(!row.overlaps(textRow(BankerScreenLayout.FOOTER_Y)),
                    "Recent-activity row overlaps the footer");
        }
    }

    private static void testFundHeaderDoesNotCollideWithControls() {
        BankerScreenLayout.Rect title = new BankerScreenLayout.Rect(
                BankerScreenLayout.FUND_TITLE_X,
                BankerScreenLayout.FUND_DONOR_Y,
                BankerScreenLayout.FUND_TITLE_WIDTH,
                BankerScreenLayout.TEXT_HEIGHT);
        BankerScreenLayout.Rect donor = new BankerScreenLayout.Rect(
                BankerScreenLayout.FUND_DONOR_X,
                BankerScreenLayout.FUND_DONOR_Y,
                BankerScreenLayout.FUND_DONOR_WIDTH,
                BankerScreenLayout.TEXT_HEIGHT);
        BankerScreenLayout.Rect controls = new BankerScreenLayout.Rect(
                12, BankerScreenLayout.FUND_CONTROL_Y, 296, 18);
        require(!title.overlaps(donor),
                "Fund title overlaps donor recognition");
        require(!title.overlaps(controls) && !donor.overlaps(controls),
                "Fund header text overlaps Fund controls");
    }

    private static BankerScreenLayout.Rect textRow(int y) {
        return new BankerScreenLayout.Rect(12, y, 296, BankerScreenLayout.TEXT_HEIGHT);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
