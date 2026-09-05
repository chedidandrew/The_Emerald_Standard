package com.chedidandrew.emeraldstandard.client;

/** Regression checks for the fixed 320x230 Banker screen geometry. */
public final class BankerScreenLayoutRegressionTest {
    private BankerScreenLayoutRegressionTest() {
    }

    public static void main(String[] args) {
        testTabStripFits();
        require(BankerScreenLayout.TOOLTIP_WRAP_WIDTH <= BankerScreenLayout.WIDTH - 24,
                "Context tooltips can still render as screen-wide single lines");
        testHistoryControlDoesNotCoverCharts();
        testOverviewValuesStayOutsideChart();
        testOverviewSummaryRowsDoNotCollide();
        testVillageDetailRowsStayInsidePanels();
        testMarketSelectorDoesNotCoverChart();
        testMarketRowsDoNotCollide();
        testMarketHoverRegionsAreDistinct();
        testBankingRowsDoNotCollide();
        testSelectedAmountResolution();
        testFundHeaderDoesNotCollideWithControls();
        testContextualTooltipStates();
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
        require(BankerScreenLayout.OVERVIEW_VALUE_WIDTH >= 45,
                "Overview value column cannot show a compact 999.9K E balance");
    }

    private static void testVillageDetailRowsStayInsidePanels() {
        BankerScreenLayout.Rect leftPanel = new BankerScreenLayout.Rect(
                BankerScreenLayout.VILLAGE_LEFT_PANEL_X,
                BankerScreenLayout.VILLAGE_PANEL_Y,
                BankerScreenLayout.VILLAGE_LEFT_PANEL_WIDTH,
                BankerScreenLayout.VILLAGE_PANEL_HEIGHT);
        BankerScreenLayout.Rect rightPanel = new BankerScreenLayout.Rect(
                BankerScreenLayout.VILLAGE_RIGHT_PANEL_X,
                BankerScreenLayout.VILLAGE_PANEL_Y,
                BankerScreenLayout.VILLAGE_RIGHT_PANEL_WIDTH,
                BankerScreenLayout.VILLAGE_PANEL_HEIGHT);
        BankerScreenLayout.Rect leftSummary = new BankerScreenLayout.Rect(
                BankerScreenLayout.VILLAGE_LEFT_PANEL_X,
                BankerScreenLayout.VILLAGE_PANEL_Y,
                BankerScreenLayout.VILLAGE_LEFT_PANEL_WIDTH,
                BankerScreenLayout.VILLAGE_TOP_SECTION_HEIGHT);
        BankerScreenLayout.Rect rightSummary = new BankerScreenLayout.Rect(
                BankerScreenLayout.VILLAGE_RIGHT_PANEL_X,
                BankerScreenLayout.VILLAGE_PANEL_Y,
                BankerScreenLayout.VILLAGE_RIGHT_PANEL_WIDTH,
                BankerScreenLayout.VILLAGE_TOP_SECTION_HEIGHT);
        BankerScreenLayout.Rect mode = villageLeftRow(
                BankerScreenLayout.VILLAGE_PRIMARY_DETAIL_Y);
        BankerScreenLayout.Rect impact = villageLeftRow(
                BankerScreenLayout.VILLAGE_SECONDARY_DETAIL_Y);
        BankerScreenLayout.Rect restorationOrNews = villageRightRow(
                BankerScreenLayout.VILLAGE_PRIMARY_DETAIL_Y);
        BankerScreenLayout.Rect outputs = villageRightRow(
                BankerScreenLayout.VILLAGE_SECONDARY_DETAIL_Y);

        require(contains(leftPanel, mode) && contains(leftPanel, impact),
                "Village mode or impact text crosses the left panel border");
        require(contains(rightPanel, restorationOrNews) && contains(rightPanel, outputs),
                "Village restoration/news or outputs text crosses the right panel border");
        require(!leftSummary.overlaps(mode) && !mode.overlaps(impact),
                "Village summary/mode/impact hover regions overlap");
        require(!rightSummary.overlaps(restorationOrNews)
                        && !restorationOrNews.overlaps(outputs),
                "Village resource/restoration/output hover regions overlap");
        require(!leftPanel.overlaps(textRow(BankerScreenLayout.FOOTER_Y))
                        && !rightPanel.overlaps(textRow(BankerScreenLayout.FOOTER_Y)),
                "Village panels overlap the footer");
    }

    private static void testContextualTooltipStates() {
        require(BankerScreenLayout.cdCloseTooltipState(false, 0)
                        == BankerScreenLayout.CdCloseTooltipState.UNAVAILABLE,
                "A missing CD must not advertise an early-close penalty");
        require(BankerScreenLayout.cdCloseTooltipState(true, 1)
                        == BankerScreenLayout.CdCloseTooltipState.EARLY,
                "An active CD must explain its early-close penalty");
        require(BankerScreenLayout.cdCloseTooltipState(true, 0)
                        == BankerScreenLayout.CdCloseTooltipState.MATURE,
                "A mature CD must explain its penalty-free close");

        require(BankerScreenLayout.fundPurposeTooltipState(true, true, false, false)
                        == BankerScreenLayout.FundPurposeTooltipState.CYCLABLE,
                "An enabled targeted grant must advertise purpose cycling");
        require(BankerScreenLayout.fundPurposeTooltipState(false, true, false, false)
                        == BankerScreenLayout.FundPurposeTooltipState.FUND_UNAVAILABLE,
                "An unavailable Fund must not advertise purpose cycling");
        require(BankerScreenLayout.fundPurposeTooltipState(true, false, false, false)
                        == BankerScreenLayout.FundPurposeTooltipState.TARGETING_DISABLED,
                "Disabled targeting must explain why purpose cannot cycle");
        require(BankerScreenLayout.fundPurposeTooltipState(true, true, true, false)
                        == BankerScreenLayout.FundPurposeTooltipState.RESTORATION_FIXED,
                "A restoration grant must identify its fixed purpose");
        require(BankerScreenLayout.fundPurposeTooltipState(true, true, false, true)
                        == BankerScreenLayout.FundPurposeTooltipState.PROJECT_FIXED,
                "A sponsorship must identify its fixed project purpose");
    }

    private static void testOverviewSummaryRowsDoNotCollide() {
        BankerScreenLayout.Rect performance = new BankerScreenLayout.Rect(
                BankerScreenLayout.OVERVIEW_PERFORMANCE_X,
                BankerScreenLayout.OVERVIEW_PERFORMANCE_Y,
                BankerScreenLayout.OVERVIEW_PERFORMANCE_WIDTH,
                BankerScreenLayout.TEXT_HEIGHT);
        BankerScreenLayout.Rect historyChange = new BankerScreenLayout.Rect(
                BankerScreenLayout.OVERVIEW_HISTORY_CHANGE_X,
                BankerScreenLayout.OVERVIEW_HISTORY_CHANGE_Y,
                BankerScreenLayout.OVERVIEW_HISTORY_CHANGE_WIDTH,
                BankerScreenLayout.TEXT_HEIGHT);
        BankerScreenLayout.Rect chart = new BankerScreenLayout.Rect(
                BankerScreenLayout.OVERVIEW_CHART_X,
                BankerScreenLayout.OVERVIEW_CHART_Y,
                BankerScreenLayout.OVERVIEW_CHART_WIDTH,
                BankerScreenLayout.OVERVIEW_CHART_HEIGHT);
        BankerScreenLayout.Rect actions = new BankerScreenLayout.Rect(12, 165, 296, 18);

        require(!performance.overlaps(historyChange),
                "Overview performance and history summaries overlap");
        require(!performance.overlaps(chart) && !historyChange.overlaps(chart),
                "Overview summaries overlap the history chart");
        require(!performance.overlaps(actions) && !historyChange.overlaps(actions),
                "Overview summaries overlap the action row");
    }

    private static void testMarketSelectorDoesNotCoverChart() {
        BankerScreenLayout.Rect chart = new BankerScreenLayout.Rect(
                BankerScreenLayout.MARKET_CHART_X,
                BankerScreenLayout.MARKET_CHART_Y,
                BankerScreenLayout.MARKET_CHART_WIDTH,
                BankerScreenLayout.MARKET_CHART_HEIGHT);
        BankerScreenLayout.Rect label = new BankerScreenLayout.Rect(
                BankerScreenLayout.MARKET_SELECTOR_LABEL_X,
                BankerScreenLayout.MARKET_SELECTOR_LABEL_Y,
                BankerScreenLayout.MARKET_META_WIDTH,
                BankerScreenLayout.TEXT_HEIGHT);
        BankerScreenLayout.Rect previous = new BankerScreenLayout.Rect(
                BankerScreenLayout.MARKET_PREVIOUS_X,
                BankerScreenLayout.MARKET_SELECTOR_Y,
                BankerScreenLayout.MARKET_ARROW_WIDTH,
                BankerScreenLayout.MARKET_SELECTOR_HEIGHT);
        BankerScreenLayout.Rect asset = new BankerScreenLayout.Rect(
                BankerScreenLayout.MARKET_ASSET_X,
                BankerScreenLayout.MARKET_SELECTOR_Y,
                BankerScreenLayout.MARKET_ASSET_WIDTH,
                BankerScreenLayout.MARKET_SELECTOR_HEIGHT);
        BankerScreenLayout.Rect next = new BankerScreenLayout.Rect(
                BankerScreenLayout.MARKET_NEXT_X,
                BankerScreenLayout.MARKET_SELECTOR_Y,
                BankerScreenLayout.MARKET_ARROW_WIDTH,
                BankerScreenLayout.MARKET_SELECTOR_HEIGHT);
        BankerScreenLayout.Rect sector = new BankerScreenLayout.Rect(
                BankerScreenLayout.MARKET_SELECTOR_LABEL_X,
                BankerScreenLayout.MARKET_SECTOR_Y,
                BankerScreenLayout.MARKET_META_WIDTH,
                BankerScreenLayout.TEXT_HEIGHT);
        BankerScreenLayout.Rect risk = new BankerScreenLayout.Rect(
                BankerScreenLayout.MARKET_SELECTOR_LABEL_X,
                BankerScreenLayout.MARKET_RISK_Y,
                BankerScreenLayout.MARKET_META_WIDTH,
                BankerScreenLayout.TEXT_HEIGHT);

        BankerScreenLayout.Rect[] selectorParts = {label, previous, asset, next, sector, risk};
        for (BankerScreenLayout.Rect part : selectorParts) {
            require(!part.overlaps(chart), "Market carousel overlaps the price chart");
        }
        require(!previous.overlaps(asset) && !asset.overlaps(next),
                "Market carousel buttons overlap each other");
        require(!label.overlaps(previous) && !sector.overlaps(risk),
                "Market carousel text rows overlap controls or each other");
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
        BankerScreenLayout.Rect indicator = new BankerScreenLayout.Rect(
                BankerScreenLayout.TAB_X,
                BankerScreenLayout.TAB_INDICATOR_Y,
                BankerScreenLayout.TAB_WIDTH,
                BankerScreenLayout.TAB_INDICATOR_HEIGHT);
        require(indicator.y() >= last.bottom()
                        && indicator.bottom() <= BankerScreenLayout.MARKET_TITLE_Y,
                "The selected-tab indicator overlaps a tab or page content");
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

    private static void testMarketHoverRegionsAreDistinct() {
        BankerScreenLayout.Rect screen = new BankerScreenLayout.Rect(
                0, 0, BankerScreenLayout.WIDTH, BankerScreenLayout.HEIGHT);
        BankerScreenLayout.Rect chart = new BankerScreenLayout.Rect(
                BankerScreenLayout.MARKET_CHART_X,
                BankerScreenLayout.MARKET_CHART_Y,
                BankerScreenLayout.MARKET_CHART_WIDTH,
                BankerScreenLayout.MARKET_CHART_HEIGHT);
        BankerScreenLayout.Rect selectorLabel = new BankerScreenLayout.Rect(
                BankerScreenLayout.MARKET_SELECTOR_LABEL_X,
                BankerScreenLayout.MARKET_SELECTOR_LABEL_Y,
                BankerScreenLayout.MARKET_META_WIDTH,
                BankerScreenLayout.TEXT_HEIGHT);
        BankerScreenLayout.Rect title = new BankerScreenLayout.Rect(
                BankerScreenLayout.MARKET_TITLE_X,
                BankerScreenLayout.MARKET_TITLE_Y,
                BankerScreenLayout.MARKET_TITLE_WIDTH,
                BankerScreenLayout.TEXT_HEIGHT);
        BankerScreenLayout.Rect sector = new BankerScreenLayout.Rect(
                BankerScreenLayout.MARKET_SELECTOR_LABEL_X,
                BankerScreenLayout.MARKET_SECTOR_Y,
                BankerScreenLayout.MARKET_META_WIDTH,
                BankerScreenLayout.TEXT_HEIGHT);
        BankerScreenLayout.Rect risk = new BankerScreenLayout.Rect(
                BankerScreenLayout.MARKET_SELECTOR_LABEL_X,
                BankerScreenLayout.MARKET_RISK_Y,
                BankerScreenLayout.MARKET_META_WIDTH,
                BankerScreenLayout.TEXT_HEIGHT);
        BankerScreenLayout.Rect bulletin = new BankerScreenLayout.Rect(
                BankerScreenLayout.MARKET_BULLETIN_X,
                BankerScreenLayout.FOOTER_Y,
                BankerScreenLayout.MARKET_BULLETIN_WIDTH,
                BankerScreenLayout.TEXT_HEIGHT);
        BankerScreenLayout.Rect[] hoverRegions = {
                selectorLabel,
                title,
                sector,
                risk,
                marketDetailRow(BankerScreenLayout.MARKET_PRICE_Y),
                marketDetailRow(BankerScreenLayout.MARKET_HOLDING_Y),
                marketDetailRow(BankerScreenLayout.MARKET_AVERAGE_Y),
                marketDetailRow(BankerScreenLayout.MARKET_BASIS_Y),
                bulletin
        };

        for (int index = 0; index < hoverRegions.length; index++) {
            BankerScreenLayout.Rect region = hoverRegions[index];
            require(contains(screen, region),
                    "Market hover region escapes the fixed screen");
            require(!region.overlaps(chart),
                    "Market text hover region overlaps the chart tooltip region");
            for (int other = index + 1; other < hoverRegions.length; other++) {
                require(!region.overlaps(hoverRegions[other]),
                        "Market text hover regions overlap each other");
            }
        }

        BankerScreenLayout.Rect historyButton = new BankerScreenLayout.Rect(
                BankerScreenLayout.HISTORY_BUTTON_X,
                BankerScreenLayout.HISTORY_BUTTON_Y,
                BankerScreenLayout.HISTORY_BUTTON_WIDTH,
                BankerScreenLayout.HISTORY_BUTTON_HEIGHT);
        BankerScreenLayout.Rect selectorButtons = new BankerScreenLayout.Rect(
                BankerScreenLayout.MARKET_PREVIOUS_X,
                BankerScreenLayout.MARKET_SELECTOR_Y,
                BankerScreenLayout.MARKET_NEXT_X
                        + BankerScreenLayout.MARKET_ARROW_WIDTH
                        - BankerScreenLayout.MARKET_PREVIOUS_X,
                BankerScreenLayout.MARKET_SELECTOR_HEIGHT);
        BankerScreenLayout.Rect actions = new BankerScreenLayout.Rect(
                112, BankerScreenLayout.MARKET_ACTION_Y, 196, 18);
        require(!title.overlaps(historyButton),
                "Market name hover region overlaps the history button");
        require(!selectorLabel.overlaps(selectorButtons)
                        && !sector.overlaps(selectorButtons)
                        && !risk.overlaps(selectorButtons),
                "Market selector text hover region overlaps carousel controls");
        require(!marketDetailRow(BankerScreenLayout.MARKET_BASIS_Y).overlaps(actions),
                "Market basis hover region overlaps the trade actions");
        require(!bulletin.overlaps(textRow(BankerScreenLayout.AMOUNT_LABEL_Y)),
                "Market bulletin hover region overlaps the amount footer");
    }

    private static void testBankingRowsDoNotCollide() {
        BankerScreenLayout.Rect subTabs = new BankerScreenLayout.Rect(
                BankerScreenLayout.BANKING_SUBTAB_X,
                BankerScreenLayout.BANKING_SUBTAB_Y,
                (BankerScreenLayout.BANKING_SUBTAB_COUNT - 1)
                                * BankerScreenLayout.BANKING_SUBTAB_STEP
                        + BankerScreenLayout.BANKING_SUBTAB_WIDTH,
                BankerScreenLayout.BANKING_SUBTAB_HEIGHT + 2);
        BankerScreenLayout.Rect balancePanel = new BankerScreenLayout.Rect(
                10,
                BankerScreenLayout.BANKING_BALANCE_PANEL_Y,
                298,
                BankerScreenLayout.BANKING_BALANCE_PANEL_HEIGHT);
        BankerScreenLayout.Rect primaryActions = new BankerScreenLayout.Rect(
                12, BankerScreenLayout.BANKING_PRIMARY_ACTION_Y, 296, 18);
        BankerScreenLayout.Rect secondaryActions = new BankerScreenLayout.Rect(
                12, BankerScreenLayout.BANKING_SECONDARY_ACTION_Y, 296, 18);
        BankerScreenLayout.Rect transferHelp = textRow(171);
        BankerScreenLayout.Rect productControl = new BankerScreenLayout.Rect(
                14, BankerScreenLayout.BANKING_PRODUCT_CONTROL_Y, 294, 18);
        BankerScreenLayout.Rect productActions = new BankerScreenLayout.Rect(
                12, BankerScreenLayout.BANKING_PRODUCT_ACTION_Y, 296, 18);
        BankerScreenLayout.Rect productDetail = textRow(
                BankerScreenLayout.BANKING_PRODUCT_DETAIL_Y);
        BankerScreenLayout.Rect productPreview = textRow(
                BankerScreenLayout.BANKING_PRODUCT_PREVIEW_Y);
        BankerScreenLayout.Rect footer = textRow(BankerScreenLayout.FOOTER_Y);

        require(subTabs.right() <= BankerScreenLayout.WIDTH - 12,
                "Banking sub-tabs overflow the fixed screen");
        require(!subTabs.overlaps(balancePanel),
                "Banking sub-tabs overlap the balance panel");
        require(!balancePanel.overlaps(primaryActions),
                "Transfer balances overlap primary actions");
        require(!primaryActions.overlaps(secondaryActions)
                        && !secondaryActions.overlaps(transferHelp)
                        && !transferHelp.overlaps(footer),
                "Transfer actions, guidance, or footer overlap");
        require(!balancePanel.overlaps(productControl)
                        && !productControl.overlaps(productActions)
                        && !productActions.overlaps(productDetail)
                        && !productDetail.overlaps(productPreview)
                        && !productPreview.overlaps(footer),
                "CD or villager-loan controls overlap their balance/preview rows");
    }

    private static void testSelectedAmountResolution() {
        require(BankerScreenLayout.resolvedWholeAmount(32, 100.0) == 32,
                "Fixed amount preset did not resolve against its source");
        require(BankerScreenLayout.resolvedWholeAmount(-1, 96.75) == 96,
                "All-available did not preserve a fractional remainder");
        require(BankerScreenLayout.resolvedWholeAmount(64, 12.9) == 12,
                "Preset did not clamp to the source's whole-emerald balance");
        require(BankerScreenLayout.resolvedWholeAmount(-1, 0.99) == 0,
                "Fractional-only source incorrectly enabled a whole-emerald action");
        require(BankerScreenLayout.resolvedWholeAmount(-1, Double.NaN) == 0,
                "Non-finite source produced an actionable preview");
    }

    private static void testActivityRowsStayAboveFooter() {
        BankerScreenLayout.Rect activityRows = new BankerScreenLayout.Rect(
                BankerScreenLayout.ACTIVITY_LIST_X,
                BankerScreenLayout.ACTIVITY_FIRST_ROW_Y,
                BankerScreenLayout.ACTIVITY_LIST_WIDTH,
                (BankerScreenLayout.ACTIVITY_ROWS - 1)
                                * BankerScreenLayout.ACTIVITY_ROW_STEP
                        + BankerScreenLayout.TEXT_HEIGHT);
        BankerScreenLayout.Rect newer = new BankerScreenLayout.Rect(
                BankerScreenLayout.ACTIVITY_SCROLL_BUTTON_X,
                BankerScreenLayout.ACTIVITY_SCROLL_UP_Y,
                BankerScreenLayout.ACTIVITY_SCROLL_BUTTON_WIDTH,
                BankerScreenLayout.ACTIVITY_SCROLL_BUTTON_HEIGHT);
        BankerScreenLayout.Rect older = new BankerScreenLayout.Rect(
                BankerScreenLayout.ACTIVITY_SCROLL_BUTTON_X,
                BankerScreenLayout.ACTIVITY_SCROLL_DOWN_Y,
                BankerScreenLayout.ACTIVITY_SCROLL_BUTTON_WIDTH,
                BankerScreenLayout.ACTIVITY_SCROLL_BUTTON_HEIGHT);
        BankerScreenLayout.Rect track = new BankerScreenLayout.Rect(
                BankerScreenLayout.ACTIVITY_SCROLL_TRACK_X,
                BankerScreenLayout.ACTIVITY_SCROLL_TRACK_Y,
                BankerScreenLayout.ACTIVITY_SCROLL_TRACK_WIDTH,
                BankerScreenLayout.ACTIVITY_SCROLL_TRACK_HEIGHT);
        BankerScreenLayout.Rect filter = new BankerScreenLayout.Rect(
                BankerScreenLayout.ACTIVITY_FILTER_X,
                BankerScreenLayout.ACTIVITY_FILTER_Y,
                BankerScreenLayout.ACTIVITY_FILTER_WIDTH,
                BankerScreenLayout.ACTIVITY_FILTER_HEIGHT);
        BankerScreenLayout.Rect ordering = new BankerScreenLayout.Rect(
                BankerScreenLayout.ACTIVITY_ORDER_X,
                BankerScreenLayout.ACTIVITY_ORDER_Y,
                BankerScreenLayout.ACTIVITY_ORDER_WIDTH,
                BankerScreenLayout.TEXT_HEIGHT);
        for (int index = 0; index < BankerScreenLayout.ACTIVITY_ROWS; index++) {
            BankerScreenLayout.Rect row = textRow(
                    BankerScreenLayout.ACTIVITY_FIRST_ROW_Y
                            + index * BankerScreenLayout.ACTIVITY_ROW_STEP);
            require(!row.overlaps(textRow(BankerScreenLayout.FOOTER_Y)),
                    "Activity row overlaps the footer");
        }
        require(!activityRows.overlaps(newer)
                        && !activityRows.overlaps(older)
                        && !activityRows.overlaps(track)
                        && !activityRows.overlaps(filter),
                "Activity scroll controls overlap a ledger entry");
        require(!filter.overlaps(ordering)
                        && !filter.overlaps(newer)
                        && !filter.overlaps(older)
                        && !ordering.overlaps(newer)
                        && !ordering.overlaps(older)
                        && !filter.overlaps(textRow(BankerScreenLayout.FOOTER_Y))
                        && !ordering.overlaps(textRow(BankerScreenLayout.FOOTER_Y)),
                "Activity filter or newest-first hint overlaps another control");
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
        BankerScreenLayout.Rect project = new BankerScreenLayout.Rect(
                16, BankerScreenLayout.FUND_PROJECT_Y, 286,
                BankerScreenLayout.TEXT_HEIGHT);
        BankerScreenLayout.Rect notice = new BankerScreenLayout.Rect(
                16, BankerScreenLayout.FUND_NOTICE_Y, 286,
                BankerScreenLayout.TEXT_HEIGHT);
        BankerScreenLayout.Rect action = new BankerScreenLayout.Rect(188, 165, 120, 18);
        require(!title.overlaps(donor),
                "Fund title overlaps donor recognition");
        require(!title.overlaps(controls) && !donor.overlaps(controls),
                "Fund header text overlaps Fund controls");
        require(!controls.overlaps(project) && !project.overlaps(notice),
                "Fund controls, project, and final-gift notice overlap");
        require(!notice.overlaps(action),
                "Fund final-gift notice overlaps the contribution action");
    }

    private static BankerScreenLayout.Rect textRow(int y) {
        return new BankerScreenLayout.Rect(12, y, 296, BankerScreenLayout.TEXT_HEIGHT);
    }

    private static BankerScreenLayout.Rect villageLeftRow(int y) {
        return new BankerScreenLayout.Rect(
                BankerScreenLayout.VILLAGE_LEFT_TEXT_X,
                y,
                BankerScreenLayout.VILLAGE_LEFT_TEXT_WIDTH,
                BankerScreenLayout.TEXT_HEIGHT);
    }

    private static BankerScreenLayout.Rect villageRightRow(int y) {
        return new BankerScreenLayout.Rect(
                BankerScreenLayout.VILLAGE_RIGHT_TEXT_X,
                y,
                BankerScreenLayout.VILLAGE_RIGHT_TEXT_WIDTH,
                BankerScreenLayout.TEXT_HEIGHT);
    }

    private static BankerScreenLayout.Rect marketDetailRow(int y) {
        return new BankerScreenLayout.Rect(
                BankerScreenLayout.MARKET_DETAIL_X,
                y,
                BankerScreenLayout.MARKET_DETAIL_WIDTH,
                BankerScreenLayout.TEXT_HEIGHT);
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
