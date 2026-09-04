package com.chedidandrew.emeraldstandard.client;

import com.chedidandrew.emeraldstandard.core.EconomyEngine;
import com.chedidandrew.emeraldstandard.core.EconomyState;
import com.chedidandrew.emeraldstandard.core.VillageProsperityEngine;
import com.chedidandrew.emeraldstandard.minecraft.BankerMenu;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** A compact, casual-player-first bank and exchange dashboard. */
public final class BankerScreen extends AbstractContainerScreen<BankerMenu> {
    private static final int WIDTH = BankerScreenLayout.WIDTH;
    private static final int HEIGHT = BankerScreenLayout.HEIGHT;
    private static final int TAB_ACTIVITY = 6;

    private static final int PANEL = 0xFF16251F;
    private static final int PANEL_LIGHT = 0xFF20362D;
    private static final int PANEL_DARK = 0xFF0D1713;
    private static final int EMERALD = 0xFF42D38B;
    private static final int GOLD = 0xFFF2C14E;
    private static final int TEXT = 0xFFF3F6F4;
    private static final int MUTED = 0xFFA7B7AE;
    private static final int POSITIVE = 0xFF60D394;
    private static final int NEGATIVE = 0xFFFF6B6B;

    private int tab = BankerMenu.TAB_OVERVIEW;
    private int seenStatusRevision;
    private int seenInteractiveState;
    private int statusDisplayTicks;

    public BankerScreen(BankerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, WIDTH, HEIGHT);
        this.titleLabelX = 12;
        this.titleLabelY = 10;
        this.inventoryLabelY = 10_000;
    }

    @Override
    protected void init() {
        super.init();
        seenStatusRevision = menu.statusRevision();
        seenInteractiveState = interactiveState();
        int x = leftPos + BankerScreenLayout.TAB_X;
        int y = topPos + BankerScreenLayout.TAB_Y;
        Component[] tabs = {
                tr("tab.home"),
                tr("tab.market"),
                tr("tab.bank"),
                tr("tab.trade"),
                tr("tab.village"),
                tr("tab.fund"),
                tr("tab.activity")
        };
        Component[] tabTooltips = {
                tr("tab.overview"),
                tr("tab.market"),
                tr("tab.banking"),
                tr("tab.exchange"),
                tr("tab.village"),
                tr("tab.fund"),
                tr("activity.title")
        };
        for (int index = 0; index < tabs.length; index++) {
            int selectedTab = index;
            addRenderableWidget(Button.builder(
                            selectedLabel(tabs[index], tab == index),
                            button -> {
                                tab = selectedTab;
                                rebuildWidgets();
                            })
                    .bounds(
                            x + index * BankerScreenLayout.TAB_STEP,
                            y,
                            BankerScreenLayout.TAB_WIDTH,
                            BankerScreenLayout.TAB_HEIGHT)
                    .tooltip(Tooltip.create(tabTooltips[index]))
                    .build());
        }

        if (tab == BankerMenu.TAB_FUND) {
            addDonationDraftButtons();
        } else if (tab != TAB_ACTIVITY) {
            addAmountButtons();
        }
        switch (tab) {
            case BankerMenu.TAB_OVERVIEW -> addOverviewButtons();
            case BankerMenu.TAB_MARKET -> addMarketButtons();
            case BankerMenu.TAB_BANKING -> addBankingButtons();
            case BankerMenu.TAB_EXCHANGE -> addExchangeButtons();
            case BankerMenu.TAB_VILLAGE -> addVillageButtons();
            case BankerMenu.TAB_FUND -> addFundButtons();
            case TAB_ACTIVITY -> {
            }
            default -> {
            }
        }
    }

    private void addAmountButtons() {
        int x = leftPos + 12;
        int y = topPos + BankerScreenLayout.AMOUNT_BUTTON_Y;
        for (int index = 0; index < BankerMenu.AMOUNT_PRESETS.length; index++) {
            int selected = index;
            int value = BankerMenu.AMOUNT_PRESETS[index];
            Component label = value < 0 ? tr("amount.all") : Component.literal(Integer.toString(value));
            if (menu.selectedAmountPresetIndex() == index) {
                label = selectedLabel(label, true);
            }
            addRenderableWidget(Button.builder(
                            label,
                            button -> selectAndRefresh(BankerMenu.BUTTON_AMOUNT_BASE + selected))
                    .bounds(x + index * 49, y, 45, 18)
                    .build());
        }
    }

    private void addOverviewButtons() {
        int y = topPos + 165;
        boolean ready = transactionsAvailable();
        Button deposit = addActionButton(
                tr("action.deposit"), leftPos + 12, y, 68, BankerMenu.ACTION_DEPOSIT);
        deposit.active = ready && menu.physicalEmeralds() > 0;
        Button withdraw = addActionButton(
                tr("action.withdraw"), leftPos + 84, y, 68, BankerMenu.ACTION_WITHDRAW);
        withdraw.active = ready && menu.cash() > 0.0;
        Button save = addActionButton(
                tr("action.save"), leftPos + 156, y, 68, BankerMenu.ACTION_SAVINGS_DEPOSIT);
        save.active = ready && menu.cash() > 0.0;
        Button recover = addActionButton(
                tr("action.recover"), leftPos + 228, y, 80, BankerMenu.ACTION_RECOVER);
        recover.active = menu.catchUpDays() == 0 && menu.hasPendingTransaction();
        addHistoryRangeButton();
    }

    private void addMarketButtons() {
        int startX = leftPos + 12;
        int startY = topPos + 53;
        for (int index = 0; index < EconomyEngine.ASSETS.size(); index++) {
            int selected = index;
            EconomyEngine.Asset asset = EconomyEngine.ASSETS.get(index);
            String label = menu.selectedAssetIndex() == index
                    ? "[" + asset.ticker() + "]"
                    : asset.ticker();
            addRenderableWidget(Button.builder(
                            Component.literal(label),
                            button -> selectAndRefresh(BankerMenu.BUTTON_ASSET_BASE + selected))
                    .bounds(startX + (index % 3) * 31, startY + (index / 3) * 22, 29, 18)
                    .build());
        }
        int y = topPos + BankerScreenLayout.MARKET_ACTION_Y;
        boolean ready = transactionsAvailable();
        Button buy = addActionButton(
                tr("action.buy"), leftPos + 112, y, 58, BankerMenu.ACTION_BUY);
        buy.active = ready && menu.cash() > 0.0 && menu.selectedAssetPrice() > 0.0;
        Button sellQuarter = addActionButton(
                tr("action.sell_quarter"), leftPos + 174, y, 62,
                BankerMenu.ACTION_SELL_QUARTER);
        sellQuarter.active = ready && menu.selectedShares() > 0.0;
        Button sellAll = addConfirmingActionButton(
                menu.confirmationAction() == BankerMenu.ACTION_SELL_ALL
                        ? tr("action.confirm")
                        : tr("action.sell_all"),
                leftPos + 240,
                y,
                68,
                BankerMenu.ACTION_SELL_ALL,
                tr("tooltip.sell_all"));
        sellAll.active = ready && menu.selectedShares() > 0.0;
        addHistoryRangeButton();
    }

    private void addBankingButtons() {
        int y = topPos + BankerScreenLayout.BANKING_PRIMARY_ACTION_Y;
        boolean ready = transactionsAvailable();
        Button toSavings = addActionButton(tr("action.to_savings"), leftPos + 12, y, 68,
                BankerMenu.ACTION_SAVINGS_DEPOSIT);
        toSavings.active = ready && menu.cash() > 0.0;
        Button fromSavings = addActionButton(tr("action.from_savings"), leftPos + 84, y, 78,
                BankerMenu.ACTION_SAVINGS_WITHDRAW);
        fromSavings.active = ready && menu.savings() > 0.0;

        Button openCd = addActionButton(
                tr("action.open_cd"), leftPos + 166, y, 68, BankerMenu.ACTION_OPEN_CD);
        openCd.active = ready && menu.cash() > 0.0
                && menu.cdCount()
                        < com.chedidandrew.emeraldstandard.core.EconomyState.MAX_TERM_POSITIONS;
        Button closeCd = addConfirmingActionButton(
                menu.confirmationAction() == BankerMenu.ACTION_CLOSE_CD
                        ? tr("action.confirm") : tr("action.close_cd"),
                leftPos + 238,
                y,
                70,
                BankerMenu.ACTION_CLOSE_CD,
                tr("tooltip.close_cd_early"));
        closeCd.active = ready && menu.hasCd();

        int productY = topPos + BankerScreenLayout.BANKING_LENDING_ACTION_Y;
        Button fund = addConfirmingActionButton(
                menu.confirmationAction() == BankerMenu.ACTION_FUND_LENDING
                        ? tr("action.confirm") : tr("action.fund"),
                leftPos + 166,
                productY,
                68,
                BankerMenu.ACTION_FUND_LENDING,
                tr("tooltip.lending_risk"));
        fund.active = ready && menu.cash() > 0.0
                && menu.lendingCount()
                        < com.chedidandrew.emeraldstandard.core.EconomyState.MAX_TERM_POSITIONS;
        Button collect = addActionButton(
                tr("action.collect"), leftPos + 238, productY, 70,
                BankerMenu.ACTION_COLLECT_LENDING);
        collect.active = ready && menu.lendingResolved() && menu.lendingDaysRemaining() == 0;

        addTermCycleButton(true, BankerScreenLayout.BANKING_CD_CONTROL_Y);
        addTermCycleButton(false, BankerScreenLayout.BANKING_LENDING_CONTROL_Y);
        addPositionButton(true, BankerScreenLayout.BANKING_CD_CONTROL_Y);
        addPositionButton(false, BankerScreenLayout.BANKING_LENDING_CONTROL_Y);
    }

    private void addExchangeButtons() {
        int index = menu.selectedResourceIndex();
        int previous = (index + BankerMenu.RESOURCE_NAMES.size() - 1)
                % BankerMenu.RESOURCE_NAMES.size();
        int next = (index + 1) % BankerMenu.RESOURCE_NAMES.size();
        addRenderableWidget(Button.builder(
                        Component.literal("<"),
                        button -> selectAndRefresh(BankerMenu.BUTTON_RESOURCE_BASE + previous))
                .bounds(leftPos + 22, topPos + 76, 24, 20)
                .build());
        addRenderableWidget(Button.builder(
                        Component.literal(">"),
                        button -> selectAndRefresh(BankerMenu.BUTTON_RESOURCE_BASE + next))
                .bounds(leftPos + 274, topPos + 76, 24, 20)
                .build());
        Button exchange = addActionButton(
                tr("action.exchange"), leftPos + 112, topPos + 165, 96,
                BankerMenu.ACTION_EXCHANGE);
        exchange.active = transactionsAvailable()
                && menu.selectedResourceCount() > 0
                && menu.selectedResourceUnitQuote() > 0.0;
        addHistoryRangeButton();
    }

    private void addVillageButtons() {
        // Contributions use a separate Fund page and a server-owned additive draft.
    }

    private void addFundButtons() {
        if (!menu.hasVillage()) {
            return;
        }
        boolean restoration = menu.villageLifecycle()
                == VillageProsperityEngine.Lifecycle.ABANDONED
                || menu.villageLifecycle() == VillageProsperityEngine.Lifecycle.EXTINCT;
        Component label = menu.confirmationAction() == BankerMenu.ACTION_SUPPORT_VILLAGE
                ? tr("action.confirm")
                : switch (menu.fundTypeIndex()) {
                    case 1 -> tr("action.create_endowment");
                    case 2 -> tr("action.sponsor_project");
                    default -> restoration
                            ? tr("action.restore_village") : tr("action.support_village");
                };
        Button contributionButton = addConfirmingActionButton(
                label,
                leftPos + 188,
                topPos + 165,
                120,
                BankerMenu.ACTION_SUPPORT_VILLAGE,
                switch (menu.fundTypeIndex()) {
                    case 1 -> tr("tooltip.endowment");
                    case 2 -> tr("tooltip.sponsor_project");
                    default -> restoration
                            ? tr("tooltip.restore_village")
                            : tr("tooltip.support_village");
                });
        contributionButton.active = transactionsAvailable()
                && menu.fundAvailable()
                && menu.donationDraft() > 0
                && !(menu.fundTypeIndex() == 2 && menu.fundableProjectTypeOrdinal() < 0);

        Button typeButton = Button.builder(
                        tr("fund.type_button", fundTypeLabel()),
                        button -> selectAndRefresh(BankerMenu.BUTTON_FUND_TYPE))
                .bounds(leftPos + 12, topPos + BankerScreenLayout.FUND_CONTROL_Y, 140, 18)
                .build();
        typeButton.active = menu.fundAvailable() && menu.availableFundTypeCount() > 1;
        addRenderableWidget(typeButton);
        Button purposeButton = Button.builder(
                        tr("fund.purpose_button", fundPurposeLabel()),
                        button -> selectAndRefresh(BankerMenu.BUTTON_FUND_PURPOSE))
                .bounds(leftPos + 168, topPos + BankerScreenLayout.FUND_CONTROL_Y, 140, 18)
                .build();
        purposeButton.active = menu.fundAvailable()
                && menu.fundTargetedDonationsEnabled()
                && !isRestorationGrant()
                && menu.fundTypeIndex() != 2;
        addRenderableWidget(purposeButton);
    }

    private void addDonationDraftButtons() {
        int x = leftPos + 12;
        int y = topPos + 207;
        int[] widths = {34, 34, 38, 38, 44, 42, 50};
        int offset = 0;
        for (int index = 0; index < BankerMenu.DONATION_AMOUNTS.length; index++) {
            int selected = index;
            int value = BankerMenu.DONATION_AMOUNTS[index];
            Component label = value < 0
                    ? tr("amount.all")
                    : value == 0 ? tr("amount.clear") : Component.literal("+" + value);
            Button draftButton = Button.builder(
                            label,
                            button -> sendMenuButton(
                                    BankerMenu.BUTTON_DONATION_AMOUNT_BASE + selected))
                    .bounds(x + offset, y, widths[index], 18)
                    .build();
            long availableWhole = Math.max(0L, (long) Math.floor(menu.cash()));
            draftButton.active = menu.fundAvailable()
                    && menu.hasVillage()
                    && (value == 0
                            ? menu.donationDraft() > 0
                            : availableWhole > menu.donationDraft());
            addRenderableWidget(draftButton);
            offset += widths[index] + 2;
        }
    }

    private void addHistoryRangeButton() {
        addRenderableWidget(Button.builder(
                        Component.literal(historyRangeLabel()),
                        button -> selectAndRefresh(BankerMenu.BUTTON_HISTORY_RANGE))
                .bounds(
                        leftPos + BankerScreenLayout.HISTORY_BUTTON_X,
                        topPos + BankerScreenLayout.HISTORY_BUTTON_Y,
                        BankerScreenLayout.HISTORY_BUTTON_WIDTH,
                        BankerScreenLayout.HISTORY_BUTTON_HEIGHT)
                .tooltip(Tooltip.create(tr("tooltip.history_range")))
                .build());
    }

    private void addTermCycleButton(boolean cd, int relativeY) {
        int selectedTerm = cd ? menu.selectedCdTerm() : menu.selectedLendingTerm();
        int selectedIndex = termIndex(selectedTerm);
        int nextIndex = (selectedIndex + 1) % BankerMenu.TERMS.length;
        double annualRate = cd
                ? EconomyEngine.cdAnnualRate(menu.regime(), selectedTerm)
                : EconomyEngine.villagerLoanAnnualYield(menu.regime(), selectedTerm);
        Component tooltip = cd
                ? tr("tooltip.cd_term", selectedTerm,
                        String.format(Locale.ROOT, "%.2f", annualRate * 100.0))
                : tr("tooltip.lending_term", selectedTerm,
                        String.format(Locale.ROOT, "%.2f", annualRate * 100.0),
                        String.format(Locale.ROOT, "%.1f",
                                EconomyEngine.estimatedLoanDefaultProbability(
                                        menu.regime(), selectedTerm) * 100.0));
        addRenderableWidget(Button.builder(
                        tr("banking.term_button", selectedTerm,
                                String.format(Locale.ROOT, "%.2f", annualRate * 100.0)),
                        button -> selectAndRefresh(
                                (cd ? BankerMenu.BUTTON_CD_TERM_BASE
                                        : BankerMenu.BUTTON_LENDING_TERM_BASE) + nextIndex))
                .bounds(leftPos + 67, topPos + relativeY, 65, 18)
                .tooltip(Tooltip.create(tooltip))
                .build());
    }

    private void addPositionButton(boolean cd, int relativeY) {
        int count = cd ? menu.cdCount() : menu.lendingCount();
        Component label;
        Component tooltip;
        if (count == 0) {
            label = tr(cd ? "banking.no_cd" : "banking.no_lending");
            tooltip = tr(cd ? "tooltip.no_cd" : "tooltip.no_lending");
        } else if (cd) {
            label = tr("banking.cd_position_button",
                    menu.selectedCdPositionNumber(), count,
                    money(menu.selectedCdPositionValue()), menu.cdDaysRemaining());
            tooltip = tr("tooltip.cd_position",
                    String.format(Locale.ROOT, "%.2f", menu.cdRate()),
                    menu.cdDaysRemaining());
        } else {
            Component state = menu.lendingResolved()
                    ? lendingOutcomeLabel(menu.lendingOutcome())
                    : tr("banking.loan_pending", menu.lendingDaysRemaining());
            label = tr("banking.loan_position_button",
                    menu.selectedLendingPositionNumber(), count,
                    money(menu.selectedLendingPositionValue()), state);
            tooltip = tr("tooltip.lending_position",
                    String.format(Locale.ROOT, "%.2f", menu.lendingRate()),
                    menu.lendingDaysRemaining(), state);
        }
        Button position = Button.builder(
                        fit(label, 164),
                        button -> selectAndRefresh(cd
                                ? BankerMenu.BUTTON_CD_POSITION
                                : BankerMenu.BUTTON_LENDING_POSITION))
                .bounds(leftPos + 136, topPos + relativeY, 172, 18)
                .tooltip(Tooltip.create(tooltip))
                .build();
        position.active = count > 1;
        addRenderableWidget(position);
    }

    private Button addActionButton(Component label, int x, int y, int width, int id) {
        Button widget = Button.builder(
                        label,
                        button -> sendMenuButton(id))
                .bounds(x, y, width, 18)
                .build();
        addRenderableWidget(widget);
        return widget;
    }

    private Button addConfirmingActionButton(
            Component label, int x, int y, int width, int id, Component explanation) {
        Button widget = Button.builder(
                        label,
                        button -> confirmOrSend(id))
                .bounds(x, y, width, 18)
                .tooltip(Tooltip.create(explanation))
                .build();
        addRenderableWidget(widget);
        return widget;
    }

    private void confirmOrSend(int id) {
        sendMenuButton(id);
    }

    private void selectAndRefresh(int id) {
        sendMenuButton(id);
        rebuildWidgets();
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        int revision = menu.statusRevision();
        int state = interactiveState();
        if (statusDisplayTicks > 0) {
            statusDisplayTicks--;
        }
        if (revision != seenStatusRevision || state != seenInteractiveState) {
            if (revision != seenStatusRevision) {
                statusDisplayTicks = 60;
            }
            seenStatusRevision = revision;
            seenInteractiveState = state;
            rebuildWidgets();
        }
    }

    private int interactiveState() {
        int result = 1;
        result = 31 * result + menu.statusRevision();
        result = 31 * result + menu.confirmationAction();
        result = 31 * result + menu.catchUpDays();
        result = 31 * result + menu.physicalEmeralds();
        result = 31 * result + menu.cdCount();
        result = 31 * result + menu.lendingCount();
        result = 31 * result + menu.cdDaysRemaining();
        result = 31 * result + menu.lendingDaysRemaining();
        result = 31 * result + (menu.lendingResolved() ? 1 : 0);
        result = 31 * result + menu.lendingOutcome().ordinal();
        result = 31 * result + (menu.hasPendingTransaction() ? 1 : 0);
        result = 31 * result + (menu.hasVillage() ? 1 : 0);
        result = 31 * result + menu.villageLifecycle().ordinal();
        result = 31 * result + (menu.cash() > 0.0 ? 1 : 0);
        result = 31 * result + (menu.savings() > 0.0 ? 1 : 0);
        result = 31 * result + (menu.selectedShares() > 0.0 ? 1 : 0);
        result = 31 * result + (menu.selectedResourceCount() > 0 ? 1 : 0);
        result = 31 * result + menu.donationDraft();
        result = 31 * result + menu.fundTypeIndex();
        result = 31 * result + menu.fundableProjectTypeOrdinal();
        result = 31 * result + menu.selectedCdPositionNumber();
        result = 31 * result + menu.selectedLendingPositionNumber();
        result = 31 * result + Double.hashCode(menu.selectedCdPositionValue());
        result = 31 * result + Double.hashCode(menu.selectedLendingPositionValue());
        result = 31 * result + Double.hashCode(menu.cdRate());
        result = 31 * result + Double.hashCode(menu.lendingRate());
        result = 31 * result + Double.hashCode(menu.cash());
        result = 31 * result + (menu.fundEnabled() ? 1 : 0);
        result = 31 * result + (menu.fundAvailable() ? 1 : 0);
        result = 31 * result + (menu.fundEndowmentsEnabled() ? 1 : 0);
        result = 31 * result + (menu.fundProjectSponsorshipEnabled() ? 1 : 0);
        result = 31 * result + (menu.fundTargetedDonationsEnabled() ? 1 : 0);
        return result;
    }

    private boolean transactionsAvailable() {
        return menu.catchUpDays() == 0 && !menu.hasPendingTransaction();
    }

    private void sendMenuButton(int id) {
        if (minecraft.player == null || minecraft.gameMode == null) {
            return;
        }
        if (menu.clickMenuButton(minecraft.player, id)) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractBackground(graphics, mouseX, mouseY, a);
        int x = leftPos;
        int y = topPos;
        graphics.fill(x, y, x + imageWidth, y + imageHeight, PANEL_DARK);
        graphics.fill(x + 3, y + 3, x + imageWidth - 3, y + imageHeight - 3, PANEL);
        graphics.outline(x + 3, y + 3, imageWidth - 6, imageHeight - 6, GOLD);
        graphics.fill(x + 8, y + 48, x + imageWidth - 8, y + 196, PANEL_LIGHT);

        if (tab == BankerMenu.TAB_OVERVIEW) {
            drawChart(graphics, menu.netWorthHistoryPointsCenti(),
                    menu.netWorthHistorySpanDays(),
                    x + BankerScreenLayout.OVERVIEW_CHART_X,
                    y + BankerScreenLayout.OVERVIEW_CHART_Y,
                    BankerScreenLayout.OVERVIEW_CHART_WIDTH,
                    BankerScreenLayout.OVERVIEW_CHART_HEIGHT,
                    mouseX, mouseY);
        } else if (tab == BankerMenu.TAB_MARKET) {
            drawChart(graphics, menu.historyPointsCenti(),
                    menu.historySpanDays(),
                    x + BankerScreenLayout.MARKET_CHART_X,
                    y + BankerScreenLayout.MARKET_CHART_Y,
                    BankerScreenLayout.MARKET_CHART_WIDTH,
                    BankerScreenLayout.MARKET_CHART_HEIGHT,
                    mouseX, mouseY);
        } else if (tab == BankerMenu.TAB_BANKING) {
            graphics.outline(x + 10, y + 54, 145, 42, 0xFF456B5A);
            graphics.outline(x + 165, y + 54, 143, 42, 0xFF456B5A);
        } else if (tab == BankerMenu.TAB_EXCHANGE) {
            graphics.outline(x + 52, y + 62, 216, 47, 0xFF456B5A);
            drawChart(graphics, menu.commodityHistoryPointsCenti(),
                    menu.commodityHistorySpanDays(),
                    x + 52, y + 116, 216, 45, mouseX, mouseY);
        } else if (tab == BankerMenu.TAB_VILLAGE) {
            graphics.outline(x + 10, y + 54, 145, 97, 0xFF456B5A);
            graphics.outline(x + 165, y + 54, 143, 97, 0xFF456B5A);
        } else if (tab == BankerMenu.TAB_FUND) {
            graphics.outline(x + 10, y + 54, 298, 97, 0xFF456B5A);
        } else if (tab == TAB_ACTIVITY) {
            graphics.outline(x + 10, y + 54, 298, 121, 0xFF456B5A);
        }
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        drawTextWithin(graphics, title, 12, 10, 114, GOLD, false);
        drawTextWithin(graphics,
                tr("header.day_regime", menu.economicDay(), friendly(menu.regime().name())),
                182,
                11,
                126,
                MUTED,
                false);
        drawTextWithin(graphics,
                tr("header.inventory", compactWhole(menu.physicalEmeralds())),
                130,
                11,
                48,
                EMERALD,
                false);

        switch (tab) {
            case BankerMenu.TAB_OVERVIEW -> drawOverviewLabels(graphics);
            case BankerMenu.TAB_MARKET -> drawMarketLabels(graphics);
            case BankerMenu.TAB_BANKING -> drawBankingLabels(graphics);
            case BankerMenu.TAB_EXCHANGE -> drawExchangeLabels(graphics);
            case BankerMenu.TAB_VILLAGE -> drawVillageLabels(graphics);
            case BankerMenu.TAB_FUND -> drawFundLabels(graphics);
            case TAB_ACTIVITY -> drawActivityLabels(graphics);
            default -> {
            }
        }

        if (tab != TAB_ACTIVITY) {
            graphics.text(font,
                    tab == BankerMenu.TAB_FUND
                            ? tr("fund.draft", menu.donationDraft())
                            : tr("label.amount"),
                    12,
                    BankerScreenLayout.AMOUNT_LABEL_Y,
                    MUTED,
                    false);
        }
        Component status = statusText(menu.statusCode());
        Component footer = null;
        int footerColor = MUTED;
        if (menu.catchUpDays() > 0) {
            footer = tr("notice.catch_up", menu.catchUpDays());
            footerColor = GOLD;
        } else if (menu.hasPendingTransaction()) {
            footer = tr("notice.recovery_pending");
            footerColor = GOLD;
        } else if (status != null && statusDisplayTicks > 0) {
            footer = status;
            footerColor = menu.statusCode() < 0
                    ? NEGATIVE
                    : menu.statusCode() == 16 ? GOLD : POSITIVE;
        } else if (tab == BankerMenu.TAB_MARKET) {
            footer = marketBulletin();
            footerColor = GOLD;
        }
        if (footer != null) {
            drawTextWithin(graphics, footer, 12, BankerScreenLayout.FOOTER_Y,
                    296, footerColor, false);
        }
    }

    private void drawOverviewLabels(GuiGraphicsExtractor graphics) {
        graphics.text(font, tr("overview.title"), 14, 56, TEXT, false);
        labelValue(graphics, tr("label.net_worth"), panelMoney(menu.netWorth()), 14, 72, GOLD);
        labelValue(graphics, tr("label.bank_cash"), panelMoney(menu.cash()), 14, 88, TEXT);
        labelValue(graphics, tr("label.savings"), panelMoney(menu.savings()), 14, 104, TEXT);
        labelValue(graphics, tr("label.invested"), panelMoney(investedValue()), 14, 120, TEXT);
        drawTextWithin(graphics,
                tr("overview.contributed_short", panelMoney(menu.totalContributions())),
                14, 136, 93, MUTED, false);
        graphics.text(font,
                tr("overview.performance", signedMoney(menu.unrealizedGain()),
                        signedMoney(menu.realizedGain())),
                14,
                149,
                menu.unrealizedGain() + menu.realizedGain() >= 0.0 ? POSITIVE : NEGATIVE,
                false);

        graphics.text(font, tr("overview.net_worth_history"), 118, 50, TEXT, false);
        graphics.text(font,
                tr("overview.history_change", signed(historyChange(
                        menu.netWorthHistoryPointsCenti()))),
                118,
                147,
                historyChange(menu.netWorthHistoryPointsCenti()) >= 0.0 ? POSITIVE : NEGATIVE,
                false);
    }

    private void drawMarketLabels(GuiGraphicsExtractor graphics) {
        EconomyEngine.Asset selected = menu.selectedAsset();
        graphics.text(font, tr("market.choose"), 14, 56, TEXT, false);
        graphics.text(font, selected.ticker(), 14, 126, GOLD, false);
        graphics.text(font, selected.sector(), 14, 138, TEXT, false);
        graphics.text(font, riskLabel(selected), 14, 150, MUTED, false);

        drawTextWithin(graphics, Component.literal(selected.name()),
                BankerScreenLayout.MARKET_TITLE_X,
                BankerScreenLayout.MARKET_TITLE_Y,
                BankerScreenLayout.MARKET_TITLE_WIDTH,
                TEXT,
                false);
        drawTextWithin(graphics,
                tr("market.price_change", money(menu.selectedAssetPrice()),
                        signed(menu.selectedChangePercent())),
                118,
                BankerScreenLayout.MARKET_PRICE_Y,
                189,
                menu.selectedChangePercent() >= 0.0 ? POSITIVE : NEGATIVE,
                false);
        drawTextWithin(graphics,
                tr("market.holding", compactShares(menu.selectedShares()),
                        money(menu.selectedHoldingValue()),
                        String.format(Locale.ROOT, "%.1f", menu.selectedAllocationPercent())),
                118,
                BankerScreenLayout.MARKET_HOLDING_Y,
                189,
                MUTED,
                false);
        drawTextWithin(graphics,
                tr("market.average_price", money(menu.selectedAveragePrice())),
                118,
                BankerScreenLayout.MARKET_AVERAGE_Y,
                189,
                MUTED,
                false);
        drawTextWithin(graphics,
                tr("market.total_basis", money(menu.totalCostBasis())),
                118,
                BankerScreenLayout.MARKET_BASIS_Y,
                189,
                MUTED,
                false);
    }

    private void drawBankingLabels(GuiGraphicsExtractor graphics) {
        graphics.text(font, tr("label.savings"), 16, 59, GOLD, false);
        graphics.text(font, money(menu.savings()), 16, 73, TEXT, false);
        graphics.text(font,
                tr("banking.current_rate", String.format(Locale.ROOT, "%.2f", menu.savingsRate())),
                16,
                85,
                MUTED,
                false);

        drawTextWithin(graphics, tr("banking.products"), 171, 59, 131, GOLD, false);
        Component cd = menu.hasCd()
                ? tr("banking.positions", menu.cdCount(),
                        com.chedidandrew.emeraldstandard.core.EconomyState.MAX_TERM_POSITIONS,
                        money(menu.cdValue()))
                : tr("banking.no_cd");
        drawTextWithin(graphics, cd, 171, 73, 131, TEXT, false);
        Component lending = menu.hasLending()
                ? tr("banking.lending_positions", menu.lendingCount(),
                        com.chedidandrew.emeraldstandard.core.EconomyState.MAX_TERM_POSITIONS,
                        money(menu.lendingValue()), menu.readyLendingCount())
                : tr("banking.no_lending");
        drawTextWithin(graphics, lending, 171, 85, 131, TEXT, false);

        drawTextWithin(graphics, tr("banking.no_debt_short"), 14,
                BankerScreenLayout.BANKING_LENDING_ACTION_Y + 4, 145, MUTED, false);
        graphics.text(font, tr("banking.cd_term"), 14,
                BankerScreenLayout.BANKING_CD_CONTROL_Y + 5, MUTED, false);
        graphics.text(font, tr("banking.loan_term"), 14,
                BankerScreenLayout.BANKING_LENDING_CONTROL_Y + 5, MUTED, false);
    }

    private void drawExchangeLabels(GuiGraphicsExtractor graphics) {
        Component resource = tr("resource." + menu.selectedResourceName());
        graphics.centeredText(font, resource, 160, 69, GOLD);
        graphics.centeredText(font,
                tr("exchange.owned", menu.selectedResourceCount()),
                160,
                83,
                TEXT);
        graphics.centeredText(font,
                tr("exchange.quote", String.format(Locale.ROOT, "%.2f",
                        menu.selectedResourceUnitQuote())),
                160,
                96,
                MUTED);
        graphics.centeredText(font,
                tr("exchange.instructions"),
                160,
                104,
                MUTED);
    }

    private void drawVillageLabels(GuiGraphicsExtractor graphics) {
        if (!menu.hasVillage()) {
            graphics.centeredText(font, tr("village.none"), 160, 92, MUTED);
            graphics.centeredText(font, tr("village.find_bank"), 160, 110, MUTED);
            return;
        }
        graphics.text(font, tr("village.community"), 16, 59, GOLD, false);
        graphics.text(font,
                tr("village.status", tr("village.lifecycle."
                        + menu.villageLifecycle().name().toLowerCase(Locale.ROOT))),
                16,
                75,
                TEXT,
                false);
        graphics.text(font,
                tr("village.population", menu.villagePopulation(), menu.villageHousing()),
                16,
                89,
                TEXT,
                false);
        graphics.text(font,
                tr("village.tier", menu.villageTier()),
                16,
                103,
                TEXT,
                false);
        graphics.text(font,
                tr("village.prosperity", String.format(Locale.ROOT, "%.1f", menu.villageProsperity())),
                16,
                117,
                menu.villageProsperity() >= 50.0 ? POSITIVE : GOLD,
                false);
        graphics.text(font,
                tr("village.safety", String.format(Locale.ROOT, "%.1f", menu.villageSafety())),
                16,
                131,
                menu.villageSafety() >= 50.0 ? POSITIVE : NEGATIVE,
                false);

        graphics.text(font, tr("village.resources"), 171, 59, GOLD, false);
        graphics.text(font,
                tr("village.food", String.format(Locale.ROOT, "%.1f", menu.villageFood())),
                171,
                75,
                TEXT,
                false);
        graphics.text(font,
                tr("village.materials", String.format(Locale.ROOT, "%.1f", menu.villageMaterials())),
                171,
                89,
                TEXT,
                false);
        graphics.text(font,
                tr("village.treasury", money(menu.villageTreasury())),
                171,
                103,
                TEXT,
                false);
        int projectOrdinal = menu.villageProjectTypeOrdinal();
        Component project = projectOrdinal < 0
                || projectOrdinal >= VillageProsperityEngine.ProjectType.values().length
                ? tr("village.project.none")
                : tr("village.project."
                        + VillageProsperityEngine.ProjectType.values()[projectOrdinal]
                                .name().toLowerCase(Locale.ROOT));
        graphics.text(font,
                tr("village.project", project),
                171,
                117,
                TEXT,
                false);
        graphics.text(font,
                tr("village.progress",
                        String.format(Locale.ROOT, "%.1f", menu.villageProjectProgress()),
                        menu.villageProjectBacklog()),
                171,
                131,
                MUTED,
                false);
        Component mode = menu.villageSimulationEnabled()
                ? menu.villageVisualProgressionEnabled()
                        ? tr("village.mode.full")
                        : tr("village.mode.simulation")
                : menu.villageVisualProgressionEnabled()
                        ? tr("village.mode.visual")
                        : tr("village.mode.off");
        graphics.text(font, mode, 16, 149, MUTED, false);
        double localImpactScore = VillageProsperityEngine.broadFundamentalScore(
                menu.villageProsperity(), menu.villageSafety(), menu.villageTier());
        Component localImpact = localImpactScore >= 0.45
                ? tr("village.impact.strong")
                : localImpactScore >= 0.10
                        ? tr("village.impact.positive")
                        : localImpactScore >= -0.20
                                ? tr("village.impact.neutral")
                                : tr("village.impact.weak");
        int impactColor = localImpactScore >= 0.10
                ? POSITIVE
                : localImpactScore >= -0.20 ? GOLD : NEGATIVE;
        graphics.text(font, localImpact, 16, 163, impactColor, false);
        boolean restoration = menu.villageLifecycle() == VillageProsperityEngine.Lifecycle.ABANDONED
                || menu.villageLifecycle() == VillageProsperityEngine.Lifecycle.EXTINCT;
        if (restoration) {
            graphics.text(font,
                    tr("village.restoration",
                            String.format(Locale.ROOT, "%.1f", menu.villageRestorationFund()),
                            String.format(Locale.ROOT, "%.0f", VillageProsperityEngine.RESTORATION_EMERALD_TARGET)),
                    171,
                    149,
                    GOLD,
                    false);
        } else if (menu.villageIncidentCause()
                != VillageProsperityEngine.IncidentCause.NONE) {
            Component age = menu.villageIncidentAge() == 0
                    ? tr("village.news.today")
                    : tr("village.news.days_ago", menu.villageIncidentAge());
            graphics.text(font,
                    tr("village.news",
                            tr("village.incident."
                                    + menu.villageIncidentCause()
                                            .name().toLowerCase(Locale.ROOT)),
                            age),
                    171,
                    149,
                    menu.villageIncidentCause() == VillageProsperityEngine.IncidentCause.PLAYER
                            ? NEGATIVE
                            : GOLD,
                    false);
        }
        graphics.text(font,
                tr("village.outputs",
                        menu.villageAgricultureOutput(),
                        menu.villageMiningOutput(),
                        menu.villageTradeOutput()),
                171,
                163,
                MUTED,
                false);
    }

    private void drawActivityLabels(GuiGraphicsExtractor graphics) {
        graphics.text(font, tr("activity.title"), 16, 59, GOLD, false);
        drawTextWithin(graphics,
                tr("activity.cash_flow", money(menu.totalContributions()),
                        money(menu.totalWithdrawals())),
                16, 70, 286, MUTED, false);
        if (menu.recentActivityCount() == 0) {
            graphics.centeredText(font, tr("activity.empty"), 160, 108, MUTED);
            graphics.centeredText(font, tr("activity.empty_hint"), 160, 124, MUTED);
            return;
        }
        for (int index = 0; index < menu.recentActivityCount(); index++) {
            Component kind = activityKindLabel(menu.recentActivityKind(index));
            String ticker = menu.recentActivityAssetTicker(index);
            Component subject = ticker.isEmpty()
                    ? kind
                    : kind.copy().append(Component.literal(" " + ticker));
            drawTextWithin(graphics,
                    tr("activity.entry", menu.recentActivityDay(index), subject,
                            money(menu.recentActivityAmount(index))),
                    16,
                    BankerScreenLayout.ACTIVITY_FIRST_ROW_Y
                            + index * BankerScreenLayout.ACTIVITY_ROW_STEP,
                    286,
                    index == 0 ? TEXT : MUTED,
                    false);
        }
        graphics.text(font, tr("activity.newest_first"), 16, 162, MUTED, false);
    }

    private void drawFundLabels(GuiGraphicsExtractor graphics) {
        if (!menu.hasVillage()) {
            graphics.centeredText(font, tr("village.none"), 160, 92, MUTED);
            graphics.centeredText(font, tr("village.find_bank"), 160, 110, MUTED);
            return;
        }
        drawTextWithin(graphics, tr("fund.title"),
                BankerScreenLayout.FUND_TITLE_X,
                BankerScreenLayout.FUND_DONOR_Y,
                BankerScreenLayout.FUND_TITLE_WIDTH,
                GOLD,
                false);
        graphics.text(font, tr("fund.spendable", money(menu.fundSpendable())),
                16, 73, TEXT, false);
        graphics.text(font, tr("fund.endowment", money(menu.fundEndowment())),
                168, 73, TEXT, false);
        graphics.text(font, tr("fund.reserve", money(menu.fundReserve())),
                16, 85, MUTED, false);
        graphics.text(font, tr("fund.received", money(menu.fundLifetimeReceived())),
                168, 85, MUTED, false);
        drawTextWithin(graphics,
                tr("fund.your_support", money(menu.donorLifetimeContribution()),
                        donorTitleLabel()),
                BankerScreenLayout.FUND_DONOR_X,
                BankerScreenLayout.FUND_DONOR_Y,
                BankerScreenLayout.FUND_DONOR_WIDTH,
                MUTED,
                false);
        if (!menu.fundAvailable()) {
            drawTextWithin(graphics,
                    tr(menu.fundEnabled()
                            ? "fund.unavailable_simulation" : "fund.unavailable_disabled"),
                    16, 126, 286, NEGATIVE, false);
            drawTextWithin(graphics, tr("fund.unavailable_hint"),
                    16, 141, 286, MUTED, false);
            return;
        }
        Component explanation = switch (menu.fundTypeIndex()) {
            case 1 -> tr("fund.explain.endowment");
            case 2 -> tr("fund.explain.sponsorship");
            default -> tr("fund.explain.grant");
        };
        if (menu.fundTypeIndex() == 2) {
            graphics.text(font,
                    tr("fund.project_target", projectLabel(menu.fundableProjectTypeOrdinal())),
                    16, 121, MUTED, false);
        }
        drawTextWithin(graphics, explanation, 16, 132, 286, TEXT, false);
        drawTextWithin(graphics, tr("fund.irreversible"), 16, 144, 286, MUTED, false);
        drawTextWithin(graphics, tr("fund.no_debt"), 16, 156, 286, MUTED, false);
    }

    private Component fundTypeLabel() {
        return switch (menu.fundTypeIndex()) {
            case 1 -> tr("fund.type.endowment");
            case 2 -> tr("fund.type.sponsorship");
            default -> tr("fund.type.direct_grant");
        };
    }

    private Component fundPurposeLabel() {
        if (isRestorationGrant()) {
            return tr("fund.purpose.restoration");
        }
        if (menu.fundTypeIndex() == 2) {
            return switch (projectPurpose(menu.fundableProjectTypeOrdinal())) {
                case HOUSING -> tr("fund.purpose.housing");
                case FOOD -> tr("fund.purpose.food");
                case SECURITY -> tr("fund.purpose.security");
                case TRADE -> tr("fund.purpose.trade");
                default -> tr("fund.purpose.infrastructure");
            };
        }
        return switch (menu.fundPurposeIndex()) {
            case 1 -> tr("fund.purpose.housing");
            case 2 -> tr("fund.purpose.food");
            case 3 -> tr("fund.purpose.infrastructure");
            case 4 -> tr("fund.purpose.security");
            case 5 -> tr("fund.purpose.trade");
            case 6 -> tr("fund.purpose.restoration");
            default -> tr("fund.purpose.general");
        };
    }

    private boolean isRestorationGrant() {
        return menu.fundTypeIndex() == EconomyState.ProsperityFundType.DIRECT_GRANT.ordinal()
                && (menu.villageLifecycle() == VillageProsperityEngine.Lifecycle.ABANDONED
                        || menu.villageLifecycle() == VillageProsperityEngine.Lifecycle.EXTINCT);
    }

    private static com.chedidandrew.emeraldstandard.core.EconomyState.DonationPurpose
            projectPurpose(int projectTypeOrdinal) {
        if (projectTypeOrdinal < 0
                || projectTypeOrdinal >= VillageProsperityEngine.ProjectType.values().length) {
            return com.chedidandrew.emeraldstandard.core.EconomyState.DonationPurpose
                    .INFRASTRUCTURE;
        }
        return switch (VillageProsperityEngine.ProjectType.values()[projectTypeOrdinal]) {
            case COTTAGE, HOUSE, INN ->
                    com.chedidandrew.emeraldstandard.core.EconomyState.DonationPurpose.HOUSING;
            case GRANARY ->
                    com.chedidandrew.emeraldstandard.core.EconomyState.DonationPurpose.FOOD;
            case GUARD_POST ->
                    com.chedidandrew.emeraldstandard.core.EconomyState.DonationPurpose.SECURITY;
            case MARKET_SQUARE, EXCHANGE_HALL ->
                    com.chedidandrew.emeraldstandard.core.EconomyState.DonationPurpose.TRADE;
            case WAREHOUSE, MINE_ENTRANCE, SMITHY ->
                    com.chedidandrew.emeraldstandard.core.EconomyState.DonationPurpose
                            .INFRASTRUCTURE;
        };
    }

    private Component projectLabel(int projectOrdinal) {
        return projectOrdinal < 0
                        || projectOrdinal >= VillageProsperityEngine.ProjectType.values().length
                ? tr("village.project.none")
                : tr("village.project."
                        + VillageProsperityEngine.ProjectType.values()[projectOrdinal]
                                .name().toLowerCase(Locale.ROOT));
    }

    private Component donorTitleLabel() {
        return tr("fund.donor_title."
                + menu.donorTitle().name().toLowerCase(Locale.ROOT));
    }

    private String historyRangeLabel() {
        return switch (menu.historyRangeIndex()) {
            case 0 -> "30d";
            case 1 -> "90d";
            case 2 -> "1y";
            default -> tr("chart.all").getString();
        };
    }

    private void drawChart(
            GuiGraphicsExtractor graphics,
            int[] points,
            int spanDays,
            int x,
            int y,
            int width,
            int height,
            int mouseX,
            int mouseY) {
        graphics.fill(x, y, x + width, y + height, PANEL_DARK);
        graphics.outline(x, y, width, height, 0xFF456B5A);
        if (points.length < 2) {
            graphics.centeredText(font, tr("chart.history_building"), x + width / 2,
                    y + height / 2 - 4, MUTED);
            return;
        }
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        for (int point : points) {
            min = Math.min(min, point);
            max = Math.max(max, point);
        }
        int last = points[points.length - 1];
        int minimumRange = Math.max(1, (int) Math.round(last * 0.10));
        if (max - min < minimumRange) {
            int center = (max + min) / 2;
            min = Math.max(0, center - minimumRange / 2);
            max = min + minimumRange;
        }
        if (max <= min) {
            max = min + 1;
        }
        graphics.fill(x + 1, y + height / 2, x + width - 1, y + height / 2 + 1, 0xFF29463A);
        graphics.text(font, money(max / 100.0), x + 4, y + 4, MUTED, false);
        graphics.text(font, money(min / 100.0), x + 4, y + height - 12, MUTED, false);
        graphics.text(font, historyRangeLabel(), x + width - 34, y + height - 12,
                MUTED, false);
        int previousX = x + 3;
        int previousY = chartY(points[0], min, max, y, height);
        for (int index = 1; index < points.length; index++) {
            int currentX = x + 3 + (int) Math.round(index * (width - 7.0) / (points.length - 1.0));
            int currentY = chartY(points[index], min, max, y, height);
            drawLine(graphics, previousX, previousY, currentX, currentY, EMERALD);
            previousX = currentX;
            previousY = currentY;
        }
        if (mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height) {
            int index = Math.max(0, Math.min(
                    points.length - 1,
                    (int) Math.round((mouseX - x - 3.0)
                            * (points.length - 1.0)
                            / Math.max(1.0, width - 7.0))));
            int daysAgo = points.length <= 1
                    ? 0
                    : (int) Math.round(
                            (points.length - 1 - index)
                                    * Math.max(0, spanDays)
                                    / (double) (points.length - 1));
            graphics.setTooltipForNextFrame(
                    font,
                    daysAgo == 0
                            ? tr("chart.tooltip_today", money(points[index] / 100.0))
                            : tr("chart.tooltip_ago", money(points[index] / 100.0), daysAgo),
                    mouseX,
                    mouseY);
        }
    }

    private static int chartY(int point, int min, int max, int y, int height) {
        double normalized = (point - min) / (double) (max - min);
        return y + height - 4 - (int) Math.round(normalized * (height - 8));
    }

    private static void drawLine(
            GuiGraphicsExtractor graphics,
            int x0,
            int y0,
            int x1,
            int y1,
            int color) {
        int dx = Math.abs(x1 - x0);
        int sx = x0 < x1 ? 1 : -1;
        int dy = -Math.abs(y1 - y0);
        int sy = y0 < y1 ? 1 : -1;
        int error = dx + dy;
        while (true) {
            graphics.fill(x0, y0, x0 + 2, y0 + 2, color);
            if (x0 == x1 && y0 == y1) {
                break;
            }
            int doubled = 2 * error;
            if (doubled >= dy) {
                error += dy;
                x0 += sx;
            }
            if (doubled <= dx) {
                error += dx;
                y0 += sy;
            }
        }
    }

    private void labelValue(
            GuiGraphicsExtractor graphics,
            Component label,
            String value,
            int x,
            int y,
            int valueColor) {
        graphics.text(font, label, x, y, MUTED, false);
        drawTextWithin(
                graphics,
                Component.literal(value),
                BankerScreenLayout.OVERVIEW_VALUE_X,
                y,
                BankerScreenLayout.OVERVIEW_VALUE_WIDTH,
                valueColor,
                false);
    }

    private void drawTextWithin(
            GuiGraphicsExtractor graphics,
            Component text,
            int x,
            int y,
            int maximumWidth,
            int color,
            boolean shadow) {
        graphics.text(font, fit(text, maximumWidth), x, y, color, shadow);
    }

    private Component fit(Component text, int maximumWidth) {
        if (maximumWidth <= 0 || font.width(text) <= maximumWidth) {
            return maximumWidth <= 0 ? Component.empty() : text;
        }
        String value = text.getString();
        String suffix = "...";
        int end = value.length();
        while (end > 0 && font.width(value.substring(0, end) + suffix) > maximumWidth) {
            end--;
        }
        return Component.literal(end == 0 ? suffix : value.substring(0, end).stripTrailing() + suffix);
    }

    private double investedValue() {
        double holdings = 0.0;
        for (int index = 0; index < EconomyEngine.ASSETS.size(); index++) {
            holdings += menu.assetHoldingValue(index);
        }
        return holdings + menu.cdValue() + menu.lendingValue();
    }

    private static String money(double value) {
        return compactNumber(value, false, 1_000.0) + " E";
    }

    private static String signed(double value) {
        return String.format(Locale.ROOT, "%+.2f%%", value);
    }

    private static String signedMoney(double value) {
        return compactNumber(value, true, 1_000.0) + " E";
    }

    private static String panelMoney(double value) {
        if (!Double.isFinite(value)) {
            return "0.0 E";
        }
        double absolute = Math.abs(value);
        if (absolute < 100.0) {
            return String.format(Locale.ROOT, "%.1f E", value);
        }
        if (absolute < 1_000.0) {
            return String.format(Locale.ROOT, "%.0f E", value);
        }
        return compactNumber(value, false, 1_000.0) + " E";
    }

    private static String compactShares(double value) {
        if (Double.isFinite(value) && Math.abs(value) < 1_000.0) {
            return String.format(Locale.ROOT, "%.4f", Math.abs(value) < 0.00005 ? 0.0 : value);
        }
        return compactNumber(value, false, 1_000.0);
    }

    private static String compactWhole(long value) {
        long safe = Math.max(0L, value);
        if (safe < 1_000L) {
            return Long.toString(safe);
        }
        String[] suffixes = {"K", "M", "B", "T"};
        double scaled = safe;
        int suffix = -1;
        while (scaled >= 1_000.0 && suffix + 1 < suffixes.length) {
            scaled /= 1_000.0;
            suffix++;
        }
        return String.format(Locale.ROOT, "%.1f%s", scaled, suffixes[Math.max(0, suffix)]);
    }

    private static String compactNumber(double value, boolean alwaysSign) {
        return compactNumber(value, alwaysSign, 1_000_000.0);
    }

    private static String compactNumber(double value, boolean alwaysSign, double compactAt) {
        if (!Double.isFinite(value)) {
            return alwaysSign ? "+0.00" : "0.00";
        }
        double normalized = Math.abs(value) < 0.0005 ? 0.0 : value;
        double absolute = Math.abs(normalized);
        String format = alwaysSign ? "%+.2f" : "%.2f";
        if (absolute < compactAt) {
            return String.format(Locale.ROOT, format, normalized);
        }
        String[] suffixes = {"K", "M", "B", "T", "Q"};
        double scaled = normalized;
        int suffix = -1;
        while (Math.abs(scaled) >= 1_000.0 && suffix + 1 < suffixes.length) {
            scaled /= 1_000.0;
            suffix++;
        }
        if (Math.abs(scaled) >= 1_000.0) {
            return String.format(Locale.ROOT, alwaysSign ? "%+.2e" : "%.2e", normalized);
        }
        return String.format(
                Locale.ROOT,
                alwaysSign ? "%+.1f%s" : "%.1f%s",
                scaled,
                suffixes[Math.max(0, suffix)]);
    }

    private static double historyChange(int[] points) {
        if (points == null || points.length < 2 || points[0] <= 0) {
            return 0.0;
        }
        return (points[points.length - 1] / (double) points[0] - 1.0) * 100.0;
    }

    private static String friendly(String value) {
        String text = value.toLowerCase(Locale.ROOT).replace('_', ' ');
        StringBuilder builder = new StringBuilder(text.length());
        boolean capitalize = true;
        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index);
            builder.append(capitalize ? Character.toUpperCase(character) : character);
            capitalize = character == ' ';
        }
        return builder.toString();
    }

    private static Component tr(String suffix, Object... arguments) {
        return Component.translatable("gui.the_emerald_standard." + suffix, arguments);
    }

    private static Component selectedLabel(Component label, boolean selected) {
        if (!selected) {
            return label;
        }
        return Component.literal("[").append(label.copy()).append("]");
    }

    private static Component riskLabel(EconomyEngine.Asset asset) {
        double risk = asset.beta() + asset.annualIdiosyncraticVolatility() * 2.0;
        if (risk < 1.0) return tr("risk.lower");
        if (risk < 1.45) return tr("risk.moderate");
        if (risk < 1.75) return tr("risk.high");
        return tr("risk.very_high");
    }

    private static int termIndex(int term) {
        for (int index = 0; index < BankerMenu.TERMS.length; index++) {
            if (BankerMenu.TERMS[index] == term) {
                return index;
            }
        }
        return 0;
    }

    private static Component lendingOutcomeLabel(EconomyEngine.LoanOutcome outcome) {
        return tr("banking.outcome."
                + outcome.name().toLowerCase(Locale.ROOT));
    }

    private static Component activityKindLabel(
            com.chedidandrew.emeraldstandard.core.EconomyState.PortfolioTransactionKind kind) {
        return tr("activity.kind." + kind.name().toLowerCase(Locale.ROOT));
    }

    private static Component regimeBulletin(EconomyEngine.Regime regime) {
        return tr("regime." + regime.name().toLowerCase(Locale.ROOT));
    }

    private Component marketBulletin() {
        EconomyEngine.MarketEvent event = menu.lastMarketEvent();
        if (event != EconomyEngine.MarketEvent.NONE && menu.marketEventAge() <= 180) {
            return tr("news.event", event.title(), menu.marketEventAge());
        }
        return tr("news.regime", regimeBulletin(menu.regime()));
    }

    private static Component statusText(int status) {
        return switch (status) {
            case 1 -> tr("status.deposited");
            case 2 -> tr("status.withdrew");
            case 3 -> tr("status.saved");
            case 4 -> tr("status.unsaved");
            case 5 -> tr("status.bought");
            case 6 -> tr("status.sold");
            case 7 -> tr("status.cd_opened");
            case 8 -> tr("status.cd_closed");
            case 9 -> tr("status.lending_funded");
            case 10 -> tr("status.lending_collected");
            case 11 -> tr("status.exchanged");
            case 12 -> tr("status.recovered");
            case 13 -> tr("status.recovery_pending");
            case 14 -> tr("status.village_funded");
            case 15 -> tr("status.village_restoration_ready");
            case 16 -> tr("status.confirm_required");
            case 17 -> tr("status.village_endowed");
            case 18 -> tr("status.village_project_sponsored");
            case -1 -> tr("status.busy");
            case -2 -> tr("status.insufficient");
            case -3 -> tr("status.inventory_full");
            case -4 -> tr("status.product_active");
            case -5 -> tr("status.not_ready");
            case -6 -> tr("status.persistence_failed");
            case -7 -> tr("status.unsupported");
            case -8 -> tr("status.no_village");
            case -9 -> tr("status.position_limit");
            default -> null;
        };
    }
}
