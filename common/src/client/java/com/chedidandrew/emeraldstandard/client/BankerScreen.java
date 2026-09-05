package com.chedidandrew.emeraldstandard.client;

import com.chedidandrew.emeraldstandard.core.EconomyEngine;
import com.chedidandrew.emeraldstandard.core.EconomyService;
import com.chedidandrew.emeraldstandard.core.EconomyState;
import com.chedidandrew.emeraldstandard.core.VillageProsperityEngine;
import com.chedidandrew.emeraldstandard.minecraft.BankerAmountSelection;
import com.chedidandrew.emeraldstandard.minecraft.BankerMenu;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** A compact, casual-player-first bank and exchange dashboard. */
public final class BankerScreen extends AbstractContainerScreen<BankerMenu> {
    private static final int WIDTH = BankerScreenLayout.WIDTH;
    private static final int HEIGHT = BankerScreenLayout.HEIGHT;
    private static final int TAB_ACTIVITY = 6;
    private static final int BANK_VIEW_TRANSFERS = 0;
    private static final int BANK_VIEW_CDS = 1;
    private static final int BANK_VIEW_LOANS = 2;

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
    private int bankView = BANK_VIEW_TRANSFERS;
    private int seenStatusRevision;
    private int seenInteractiveState;
    private int statusDisplayTicks;
    private final List<AmountActionButton> amountActionButtons = new ArrayList<>();
    private EditBox amountField;
    private Button amountApplyButton;
    private Button amountCancelButton;
    private Button amountAllButton;
    private String amountDraft;
    private boolean amountDraftDirty;
    private float interfaceScale = 1.0F;
    private int tooltipMouseX;
    private int tooltipMouseY;

    public BankerScreen(BankerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, WIDTH, HEIGHT);
        this.titleLabelX = 12;
        this.titleLabelY = 10;
        this.inventoryLabelY = 10_000;
    }

    @Override
    protected void init() {
        super.init();
        interfaceScale = BankerScreenScale.fit(width, height, WIDTH, HEIGHT);
        leftPos = BankerScreenScale.origin(width, WIDTH, interfaceScale);
        topPos = BankerScreenScale.origin(height, HEIGHT, interfaceScale);
        amountActionButtons.clear();
        amountField = null;
        amountApplyButton = null;
        amountCancelButton = null;
        amountAllButton = null;
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
            Button tabButton = Button.builder(
                            tabs[index],
                            button -> {
                                discardAmountDraft();
                                tab = selectedTab;
                                rebuildWidgets();
                            })
                    .bounds(
                            x + index * BankerScreenLayout.TAB_STEP,
                            y,
                            BankerScreenLayout.TAB_WIDTH,
                            BankerScreenLayout.TAB_HEIGHT)
                    .tooltip(Tooltip.create(tabTooltips[index]))
                    .build();
            // Selection is shown by the gold indicator drawn below the tab. Brackets
            // made longer names exceed their fixed button interiors at common GUI scales.
            addRenderableWidget(tabButton);
        }

        if (pageUsesTransactionAmount()) {
            addAmountControls();
        }
        switch (tab) {
            case BankerMenu.TAB_OVERVIEW -> addOverviewButtons();
            case BankerMenu.TAB_MARKET -> addMarketButtons();
            case BankerMenu.TAB_BANKING -> addBankingButtons();
            case BankerMenu.TAB_EXCHANGE -> addExchangeButtons();
            case BankerMenu.TAB_VILLAGE -> addVillageButtons();
            case BankerMenu.TAB_FUND -> addFundButtons();
            case TAB_ACTIVITY -> addActivityButtons();
            default -> {
            }
        }
        scaleWidgetsToInterface();
    }

    private void scaleWidgetsToInterface() {
        if (Math.abs(interfaceScale - 1.0F) < 0.001F) {
            return;
        }
        for (var child : children()) {
            if (!(child instanceof AbstractWidget widget)) {
                continue;
            }
            int relativeX = widget.getX() - leftPos;
            int relativeY = widget.getY() - topPos;
            widget.setRectangle(
                    BankerScreenScale.scaled(widget.getWidth(), interfaceScale),
                    BankerScreenScale.scaled(widget.getHeight(), interfaceScale),
                    leftPos + Math.round(relativeX * interfaceScale),
                    topPos + Math.round(relativeY * interfaceScale));
        }
    }

    private double logicalMouseX(double mouseX) {
        return BankerScreenScale.toLogical(mouseX, leftPos, interfaceScale);
    }

    private double logicalMouseY(double mouseY) {
        return BankerScreenScale.toLogical(mouseY, topPos, interfaceScale);
    }

    private void addAmountControls() {
        int x = leftPos + BankerScreenLayout.AMOUNT_INPUT_X;
        int y = topPos + BankerScreenLayout.AMOUNT_BUTTON_Y;
        if (!amountDraftDirty || amountDraft == null) {
            amountDraft = appliedAmountText();
        }

        amountField = new EditBox(
                font,
                x,
                y,
                BankerScreenLayout.AMOUNT_INPUT_WIDTH,
                BankerScreenLayout.AMOUNT_CONTROL_HEIGHT,
                tr("amount.input_narration"));
        // Keep oversized pasted values visible and invalid instead of silently truncating
        // them into a different, potentially valid transaction amount.
        amountField.setMaxLength(32);
        amountField.setHint(tr("amount.input_hint"));
        amountField.setValue(amountDraft);
        amountField.setResponder(this::amountDraftChanged);
        amountField.setTooltip(Tooltip.create(tr("tooltip.amount_input")));
        addRenderableWidget(amountField);

        amountApplyButton = addRenderableWidget(Button.builder(
                        tr("amount.apply"), button -> applyTypedAmount())
                .bounds(
                        leftPos + BankerScreenLayout.AMOUNT_APPLY_X,
                        y,
                        BankerScreenLayout.AMOUNT_APPLY_WIDTH,
                        BankerScreenLayout.AMOUNT_CONTROL_HEIGHT)
                .tooltip(Tooltip.create(tr("tooltip.amount_apply")))
                .build());
        amountCancelButton = addRenderableWidget(Button.builder(
                        tr("amount.cancel"), button -> cancelTypedAmount())
                .bounds(
                        leftPos + BankerScreenLayout.AMOUNT_CANCEL_X,
                        y,
                        BankerScreenLayout.AMOUNT_CANCEL_WIDTH,
                        BankerScreenLayout.AMOUNT_CONTROL_HEIGHT)
                .tooltip(Tooltip.create(tr("tooltip.amount_cancel")))
                .build());
        Component allLabel = selectedLabel(tr("amount.all"), allAmountIsApplied());
        amountAllButton = addRenderableWidget(Button.builder(
                        allLabel, button -> selectAllAvailable())
                .bounds(
                        leftPos + BankerScreenLayout.AMOUNT_ALL_X,
                        y,
                        BankerScreenLayout.AMOUNT_ALL_WIDTH,
                        BankerScreenLayout.AMOUNT_CONTROL_HEIGHT)
                .tooltip(Tooltip.create(tr("tooltip.amount_all")))
                .build());
        updateAmountControlState();
    }

    private void addOverviewButtons() {
        int y = topPos + 165;
        boolean ready = transactionsAvailable();
        Button deposit = addActionButton(
                tr("action.deposit_items"), leftPos + 12, y, 92,
                BankerMenu.ACTION_DEPOSIT, depositPreview());
        deposit.active = ready && menu.physicalEmeralds() > 0;
        trackAmountAction(deposit);
        Button withdraw = addActionButton(
                tr("action.withdraw_cash"), leftPos + 108, y, 92,
                BankerMenu.ACTION_WITHDRAW, withdrawalPreview());
        withdraw.active = ready && selectedInventoryAmount(menu.cash()) > 0;
        trackAmountAction(withdraw);
        if (menu.hasPendingTransaction()) {
            Button recover = addActionButton(
                    tr("action.recover"), leftPos + 204, y, 104,
                    BankerMenu.ACTION_RECOVER, tr("tooltip.recover"));
            recover.active = menu.catchUpDays() == 0;
        } else {
            addRenderableWidget(Button.builder(
                            tr("action.manage_transfers"),
                            button -> {
                                tab = BankerMenu.TAB_BANKING;
                                bankView = BANK_VIEW_TRANSFERS;
                                rebuildWidgets();
                            })
                    .bounds(leftPos + 204, y, 104, 18)
                    .tooltip(Tooltip.create(tr("tooltip.manage_transfers")))
                    .build());
        }
        addHistoryRangeButton();
    }

    private void addMarketButtons() {
        int index = menu.selectedAssetIndex();
        int previous = (index + EconomyEngine.ASSETS.size() - 1)
                % EconomyEngine.ASSETS.size();
        int next = (index + 1) % EconomyEngine.ASSETS.size();
        EconomyEngine.Asset selected = menu.selectedAsset();
        EconomyEngine.Asset previousAsset = EconomyEngine.ASSETS.get(previous);
        EconomyEngine.Asset nextAsset = EconomyEngine.ASSETS.get(next);
        addRenderableWidget(Button.builder(
                        Component.literal("<"),
                        button -> selectAndRefresh(BankerMenu.BUTTON_ASSET_BASE + previous))
                .bounds(
                        leftPos + BankerScreenLayout.MARKET_PREVIOUS_X,
                        topPos + BankerScreenLayout.MARKET_SELECTOR_Y,
                        BankerScreenLayout.MARKET_ARROW_WIDTH,
                        BankerScreenLayout.MARKET_SELECTOR_HEIGHT)
                .tooltip(Tooltip.create(tr("tooltip.previous_asset",
                        previousAsset.ticker(), previousAsset.name())))
                .build());
        addRenderableWidget(Button.builder(
                        Component.literal(selected.ticker()),
                        button -> selectAndRefresh(BankerMenu.BUTTON_ASSET_BASE + next))
                .bounds(
                        leftPos + BankerScreenLayout.MARKET_ASSET_X,
                        topPos + BankerScreenLayout.MARKET_SELECTOR_Y,
                        BankerScreenLayout.MARKET_ASSET_WIDTH,
                        BankerScreenLayout.MARKET_SELECTOR_HEIGHT)
                .tooltip(Tooltip.create(tr("tooltip.market_asset",
                        selected.name(), selected.sector(), riskLabel(selected))))
                .build());
        addRenderableWidget(Button.builder(
                        Component.literal(">"),
                        button -> selectAndRefresh(BankerMenu.BUTTON_ASSET_BASE + next))
                .bounds(
                        leftPos + BankerScreenLayout.MARKET_NEXT_X,
                        topPos + BankerScreenLayout.MARKET_SELECTOR_Y,
                        BankerScreenLayout.MARKET_ARROW_WIDTH,
                        BankerScreenLayout.MARKET_SELECTOR_HEIGHT)
                .tooltip(Tooltip.create(tr("tooltip.next_asset",
                        nextAsset.ticker(), nextAsset.name())))
                .build());
        int y = topPos + BankerScreenLayout.MARKET_ACTION_Y;
        boolean ready = transactionsAvailable();
        Button buy = addActionButton(
                tr("action.invest"), leftPos + 112, y, 58,
                BankerMenu.ACTION_BUY, marketBuyPreview());
        buy.active = ready && selectedWholeAmount(menu.cash()) > 0
                && menu.selectedAssetPrice() > 0.0;
        trackAmountAction(buy);
        Button sellQuarter = addActionButton(
                tr("action.sell_quarter"), leftPos + 174, y, 62,
                BankerMenu.ACTION_SELL_QUARTER, marketSalePreview(0.25));
        sellQuarter.active = ready && menu.selectedShares() > 0.0;
        Button sellAll = addConfirmingActionButton(
                menu.confirmationAction() == BankerMenu.ACTION_SELL_ALL
                        ? tr("action.confirm")
                        : tr("action.sell_all"),
                leftPos + 240,
                y,
                68,
                BankerMenu.ACTION_SELL_ALL,
                marketSalePreview(1.0));
        sellAll.active = ready && menu.selectedShares() > 0.0;
        addHistoryRangeButton();
    }

    private void addBankingButtons() {
        addBankViewButtons();
        switch (bankView) {
            case BANK_VIEW_CDS -> addCdButtons();
            case BANK_VIEW_LOANS -> addLoanButtons();
            default -> addTransferButtons();
        }
    }

    private void addBankViewButtons() {
        Component[] labels = {
                tr("banking.view.transfers"),
                tr("banking.view.cds"),
                tr("banking.view.loans")
        };
        Component[] explanations = {
                tr("tooltip.banking_transfers"),
                tr("tooltip.banking_cds"),
                tr("tooltip.banking_loans")
        };
        for (int index = 0; index < labels.length; index++) {
            int selected = index;
            addRenderableWidget(Button.builder(
                            labels[index],
                            button -> {
                                bankView = selected;
                                rebuildWidgets();
                            })
                    .bounds(
                            leftPos + BankerScreenLayout.BANKING_SUBTAB_X
                                    + index * BankerScreenLayout.BANKING_SUBTAB_STEP,
                            topPos + BankerScreenLayout.BANKING_SUBTAB_Y,
                            BankerScreenLayout.BANKING_SUBTAB_WIDTH,
                            BankerScreenLayout.BANKING_SUBTAB_HEIGHT)
                    .tooltip(Tooltip.create(explanations[index]))
                    .build());
        }
    }

    private void addTransferButtons() {
        boolean ready = transactionsAvailable();
        int primaryY = topPos + BankerScreenLayout.BANKING_PRIMARY_ACTION_Y;
        Button deposit = addActionButton(
                tr("action.inventory_to_cash"), leftPos + 12, primaryY, 145,
                BankerMenu.ACTION_DEPOSIT, depositPreview());
        deposit.active = ready && menu.physicalEmeralds() > 0;
        trackAmountAction(deposit);
        Button withdraw = addActionButton(
                tr("action.cash_to_inventory"), leftPos + 163, primaryY, 145,
                BankerMenu.ACTION_WITHDRAW, withdrawalPreview());
        withdraw.active = ready && selectedInventoryAmount(menu.cash()) > 0;
        trackAmountAction(withdraw);

        int secondaryY = topPos + BankerScreenLayout.BANKING_SECONDARY_ACTION_Y;
        Button toSavings = addActionButton(
                tr("action.cash_to_savings"), leftPos + 12, secondaryY, 145,
                BankerMenu.ACTION_SAVINGS_DEPOSIT, cashToSavingsPreview());
        toSavings.active = ready && selectedWholeAmount(menu.cash()) > 0;
        trackAmountAction(toSavings);
        Button fromSavings = addActionButton(
                tr("action.savings_to_cash"), leftPos + 163, secondaryY, 145,
                BankerMenu.ACTION_SAVINGS_WITHDRAW, savingsToCashPreview());
        fromSavings.active = ready && selectedWholeAmount(menu.savings()) > 0;
        trackAmountAction(fromSavings);
    }

    private void addCdButtons() {
        boolean ready = transactionsAvailable();
        addTermCycleButton(true, BankerScreenLayout.BANKING_PRODUCT_CONTROL_Y);
        addPositionButton(true, BankerScreenLayout.BANKING_PRODUCT_CONTROL_Y);
        int y = topPos + BankerScreenLayout.BANKING_PRODUCT_ACTION_Y;
        Button openCd = addActionButton(
                tr("action.invest_new_cd"), leftPos + 12, y, 145,
                BankerMenu.ACTION_OPEN_CD, openCdPreview());
        openCd.active = ready && selectedWholeAmount(menu.cash()) > 0
                && menu.cdCount()
                        < EconomyState.MAX_TERM_POSITIONS;
        trackAmountAction(openCd);
        Button closeCd = addConfirmingActionButton(
                menu.confirmationAction() == BankerMenu.ACTION_CLOSE_CD
                        ? tr("action.confirm") : tr("action.close_cd_to_cash"),
                leftPos + 163,
                y,
                145,
                BankerMenu.ACTION_CLOSE_CD,
                Component.empty().append(closeCdTooltip())
                        .append(Component.literal("\n"))
                        .append(closeCdFlowPreview()));
        closeCd.active = ready && menu.hasCd();
    }

    private void addLoanButtons() {
        boolean ready = transactionsAvailable();
        addTermCycleButton(false, BankerScreenLayout.BANKING_PRODUCT_CONTROL_Y);
        addPositionButton(false, BankerScreenLayout.BANKING_PRODUCT_CONTROL_Y);
        int y = topPos + BankerScreenLayout.BANKING_PRODUCT_ACTION_Y;
        Button fund = addConfirmingActionButton(
                menu.confirmationAction() == BankerMenu.ACTION_FUND_LENDING
                        ? tr("action.confirm") : tr("action.fund_new_loan"),
                leftPos + 12,
                y,
                145,
                BankerMenu.ACTION_FUND_LENDING,
                fundLoanPreview());
        fund.active = ready && selectedWholeAmount(menu.cash()) > 0
                && menu.lendingCount()
                        < EconomyState.MAX_TERM_POSITIONS;
        trackAmountAction(fund);
        Button collect = addActionButton(
                tr("action.collect_to_cash"), leftPos + 163, y, 145,
                BankerMenu.ACTION_COLLECT_LENDING, collectLoanPreview());
        collect.active = ready && menu.lendingResolved() && menu.lendingDaysRemaining() == 0;
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
                .tooltip(Tooltip.create(tr("tooltip.previous_resource")))
                .build());
        addRenderableWidget(Button.builder(
                        Component.literal(">"),
                        button -> selectAndRefresh(BankerMenu.BUTTON_RESOURCE_BASE + next))
                .bounds(leftPos + 274, topPos + 76, 24, 20)
                .tooltip(Tooltip.create(tr("tooltip.next_resource")))
                .build());
        Button exchange = addActionButton(
                tr("action.exchange_to_cash"), leftPos + 112, topPos + 165, 96,
                BankerMenu.ACTION_EXCHANGE, exchangePreview());
        exchange.active = transactionsAvailable()
                && selectedInventoryAmount(menu.selectedResourceCount()) > 0
                && menu.selectedResourceUnitQuote() > 0.0;
        trackAmountAction(exchange);
        addHistoryRangeButton();
    }

    private void addVillageButtons() {
        // Contributions use a separate Fund page and a server-owned exact draft.
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
        Component contributionExplanation = switch (menu.fundTypeIndex()) {
            case 1 -> tr("tooltip.endowment");
            case 2 -> tr("tooltip.sponsor_project");
            default -> restoration
                    ? tr("tooltip.restore_village")
                    : tr("tooltip.support_village");
        };
        Button contributionButton = addConfirmingActionButton(
                label,
                leftPos + 188,
                topPos + 165,
                120,
                BankerMenu.ACTION_SUPPORT_VILLAGE,
                Component.empty().append(contributionExplanation)
                        .append(Component.literal("\n"))
                        .append(fundContributionPreview()));
        contributionButton.active = transactionsAvailable()
                && menu.fundAvailable()
                && menu.donationDraft() > 0
                && !(menu.fundTypeIndex() == 2 && menu.fundableProjectTypeOrdinal() < 0);
        trackAmountAction(contributionButton);

        Button typeButton = Button.builder(
                        tr("fund.type_button", fundTypeLabel()),
                        button -> selectAndRefresh(BankerMenu.BUTTON_FUND_TYPE))
                .bounds(leftPos + 12, topPos + BankerScreenLayout.FUND_CONTROL_Y, 140, 18)
                .tooltip(Tooltip.create(fundTypeExplanation()))
                .build();
        typeButton.active = menu.fundAvailable() && menu.availableFundTypeCount() > 1;
        addRenderableWidget(typeButton);
        BankerScreenLayout.FundPurposeTooltipState purposeState =
                BankerScreenLayout.fundPurposeTooltipState(
                        menu.fundAvailable(),
                        menu.fundTargetedDonationsEnabled(),
                        isRestorationGrant(),
                        menu.fundTypeIndex() == 2);
        Button purposeButton = Button.builder(
                        tr("fund.purpose_button", fundPurposeLabel()),
                        button -> selectAndRefresh(BankerMenu.BUTTON_FUND_PURPOSE))
                .bounds(leftPos + 168, topPos + BankerScreenLayout.FUND_CONTROL_Y, 140, 18)
                .tooltip(Tooltip.create(fundPurposeTooltip(purposeState)))
                .build();
        purposeButton.active = purposeState
                == BankerScreenLayout.FundPurposeTooltipState.CYCLABLE;
        addRenderableWidget(purposeButton);
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

    private void addActivityButtons() {
        Component filterName = tr("activity.filter."
                + menu.activityFilter().name().toLowerCase(Locale.ROOT));
        addRenderableWidget(Button.builder(
                        tr("activity.filter_button", filterName),
                        button -> sendMenuButton(BankerMenu.BUTTON_ACTIVITY_FILTER))
                .bounds(
                        leftPos + BankerScreenLayout.ACTIVITY_FILTER_X,
                        topPos + BankerScreenLayout.ACTIVITY_FILTER_Y,
                        BankerScreenLayout.ACTIVITY_FILTER_WIDTH,
                        BankerScreenLayout.ACTIVITY_FILTER_HEIGHT)
                .tooltip(Tooltip.create(tr("activity.filter_tooltip")))
                .build());

        Button newer = Button.builder(
                        Component.literal("▲"),
                        button -> sendMenuButton(BankerMenu.BUTTON_ACTIVITY_NEWER))
                .bounds(
                        leftPos + BankerScreenLayout.ACTIVITY_SCROLL_BUTTON_X,
                        topPos + BankerScreenLayout.ACTIVITY_SCROLL_UP_Y,
                        BankerScreenLayout.ACTIVITY_SCROLL_BUTTON_WIDTH,
                        BankerScreenLayout.ACTIVITY_SCROLL_BUTTON_HEIGHT)
                .tooltip(Tooltip.create(tr("activity.newer")))
                .build();
        newer.active = menu.canScrollActivityNewer();
        addRenderableWidget(newer);

        Button older = Button.builder(
                        Component.literal("▼"),
                        button -> sendMenuButton(BankerMenu.BUTTON_ACTIVITY_OLDER))
                .bounds(
                        leftPos + BankerScreenLayout.ACTIVITY_SCROLL_BUTTON_X,
                        topPos + BankerScreenLayout.ACTIVITY_SCROLL_DOWN_Y,
                        BankerScreenLayout.ACTIVITY_SCROLL_BUTTON_WIDTH,
                        BankerScreenLayout.ACTIVITY_SCROLL_BUTTON_HEIGHT)
                .tooltip(Tooltip.create(tr("activity.older")))
                .build();
        older.active = menu.canScrollActivityOlder();
        addRenderableWidget(older);
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

    private Button addActionButton(
            Component label, int x, int y, int width, int id, Component explanation) {
        Button widget = Button.builder(
                        label,
                        button -> sendMenuButton(id))
                .bounds(x, y, width, 18)
                .tooltip(Tooltip.create(explanation))
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

    private void amountDraftChanged(String value) {
        amountDraft = value;
        amountDraftDirty = !amountDraftMatchesSelection(value);
        updateAmountControlState();
    }

    private boolean amountDraftMatchesSelection(String value) {
        int selection = tab == BankerMenu.TAB_FUND
                ? menu.donationDraft() : menu.selectedRequestedAmount();
        if (tab == BankerMenu.TAB_FUND && selection == 0) {
            return value.isEmpty();
        }
        if (selection < 0) {
            return value.isEmpty();
        }
        int parsed = BankerAmountSelection.parseAppliedAmount(value);
        return parsed > 0 && parsed == selection;
    }

    private void applyTypedAmount() {
        int amount = BankerAmountSelection.parseAppliedAmount(amountDraft);
        if (amount == 0 || !amountSelectionAvailable()) {
            updateAmountControlState();
            return;
        }
        int buttonId = tab == BankerMenu.TAB_FUND
                ? BankerAmountSelection.encodeFundButtonId(amount)
                : BankerAmountSelection.encodeButtonId(amount);
        if (buttonId < 0 || !sendMenuButtonRaw(buttonId)) {
            updateAmountControlState();
            return;
        }
        amountDraft = Integer.toString(amount);
        amountDraftDirty = false;
        rebuildWidgets();
    }

    private void cancelTypedAmount() {
        discardAmountDraft();
        rebuildWidgets();
    }

    private void selectAllAvailable() {
        int buttonId = tab == BankerMenu.TAB_FUND
                ? BankerMenu.BUTTON_FUND_AMOUNT_ALL
                : BankerMenu.BUTTON_AMOUNT_BASE + BankerMenu.AMOUNT_PRESETS.length - 1;
        if (sendMenuButtonRaw(buttonId)) {
            amountDraft = appliedAmountText();
            amountDraftDirty = false;
            rebuildWidgets();
        }
    }

    private void discardAmountDraft() {
        amountDraft = appliedAmountText();
        amountDraftDirty = false;
    }

    private String appliedAmountText() {
        int selection = tab == BankerMenu.TAB_FUND
                ? menu.donationDraft() : menu.selectedRequestedAmount();
        return selection > 0 ? Integer.toString(selection) : "";
    }

    private boolean allAmountIsApplied() {
        if (amountDraftDirty) {
            return false;
        }
        if (tab != BankerMenu.TAB_FUND) {
            return menu.selectedRequestedAmount() < 0;
        }
        int available = (int) Math.min(
                EconomyService.MAX_WHOLE_EMERALD_TRANSACTION,
                Math.max(0L, (long) Math.floor(menu.cash())));
        return available > 0 && menu.donationDraft() == available;
    }

    private void updateAmountControlState() {
        int amount = BankerAmountSelection.parseAppliedAmount(amountDraft);
        boolean applicable = !amountDraftDirty;
        boolean selectable = amountSelectionAvailable();
        if (amountField != null) {
            amountField.setTextColor(amountDraftDirty && amount == 0 ? NEGATIVE : TEXT);
        }
        if (amountApplyButton != null) {
            amountApplyButton.active = amountDraftDirty && amount > 0 && selectable;
        }
        if (amountCancelButton != null) {
            amountCancelButton.active = amountDraftDirty;
        }
        if (amountAllButton != null) {
            amountAllButton.active = selectable;
        }
        for (AmountActionButton state : amountActionButtons) {
            state.button().active = state.available() && applicable;
        }
    }

    private boolean amountSelectionAvailable() {
        return tab != BankerMenu.TAB_FUND
                || (menu.hasVillage() && menu.fundAvailable() && menu.cash() >= 1.0);
    }

    private void trackAmountAction(Button button) {
        amountActionButtons.add(new AmountActionButton(button, button.active));
        updateAmountControlState();
    }

    private static boolean usesSelectedAmount(int id) {
        return id == BankerMenu.ACTION_DEPOSIT
                || id == BankerMenu.ACTION_WITHDRAW
                || id == BankerMenu.ACTION_SAVINGS_DEPOSIT
                || id == BankerMenu.ACTION_SAVINGS_WITHDRAW
                || id == BankerMenu.ACTION_BUY
                || id == BankerMenu.ACTION_OPEN_CD
                || id == BankerMenu.ACTION_FUND_LENDING
                || id == BankerMenu.ACTION_EXCHANGE
                || id == BankerMenu.ACTION_SUPPORT_VILLAGE;
    }

    private boolean pageUsesTransactionAmount() {
        return tab == BankerMenu.TAB_OVERVIEW
                || tab == BankerMenu.TAB_MARKET
                || tab == BankerMenu.TAB_BANKING
                || tab == BankerMenu.TAB_EXCHANGE
                || tab == BankerMenu.TAB_FUND;
    }

    @Override
    protected void rebuildWidgets() {
        boolean restoreAmountFocus = amountField != null && amountField.isFocused();
        int cursor = amountField == null ? 0 : amountField.getCursorPosition();
        super.rebuildWidgets();
        if (restoreAmountFocus && amountField != null) {
            setFocused(amountField);
            amountField.setCursorPosition(Math.min(cursor, amountField.getValue().length()));
        }
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
        result = 31 * result + Double.hashCode(menu.cash());
        result = 31 * result + Double.hashCode(menu.savings());
        result = 31 * result + Double.hashCode(menu.selectedShares());
        result = 31 * result + Double.hashCode(menu.selectedAssetPrice());
        result = 31 * result + Double.hashCode(menu.selectedHoldingValue());
        result = 31 * result + menu.selectedResourceCount();
        result = 31 * result + Double.hashCode(menu.selectedResourceUnitQuote());
        result = 31 * result + menu.donationDraft();
        result = 31 * result + menu.fundTypeIndex();
        result = 31 * result + menu.fundableProjectTypeOrdinal();
        result = 31 * result + menu.selectedCdPositionNumber();
        result = 31 * result + menu.selectedLendingPositionNumber();
        result = 31 * result + Double.hashCode(menu.selectedCdPositionValue());
        result = 31 * result + Double.hashCode(menu.selectedLendingPositionValue());
        result = 31 * result + Double.hashCode(menu.cdRate());
        result = 31 * result + Double.hashCode(menu.lendingRate());
        result = 31 * result + (menu.fundEnabled() ? 1 : 0);
        result = 31 * result + (menu.fundAvailable() ? 1 : 0);
        result = 31 * result + (menu.fundEndowmentsEnabled() ? 1 : 0);
        result = 31 * result + (menu.fundProjectSponsorshipEnabled() ? 1 : 0);
        result = 31 * result + (menu.fundTargetedDonationsEnabled() ? 1 : 0);
        result = 31 * result + menu.activityOffset();
        result = 31 * result + menu.activityPageCount();
        result = 31 * result + menu.activityTotalCount();
        result = 31 * result + menu.activityFilterIndex();
        return result;
    }

    private boolean transactionsAvailable() {
        return menu.catchUpDays() == 0 && !menu.hasPendingTransaction();
    }

    private void sendMenuButton(int id) {
        if (usesSelectedAmount(id) && amountDraftDirty) {
            return;
        }
        sendMenuButtonRaw(id);
    }

    private boolean sendMenuButtonRaw(int id) {
        if (minecraft.player == null || minecraft.gameMode == null) {
            return false;
        }
        if (menu.clickMenuButton(minecraft.player, id)) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (amountField != null && amountField.isFocused()) {
            if (event.key() == InputConstants.KEY_RETURN
                    || event.key() == InputConstants.KEY_NUMPADENTER) {
                applyTypedAmount();
                return true;
            }
            if (event.key() == InputConstants.KEY_ESCAPE && amountDraftDirty) {
                cancelTypedAmount();
                return true;
            }
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean mouseScrolled(
            double mouseX,
            double mouseY,
            double horizontalAmount,
            double verticalAmount) {
        double logicalMouseX = logicalMouseX(mouseX);
        double logicalMouseY = logicalMouseY(mouseY);
        boolean overActivity = tab == TAB_ACTIVITY
                && logicalMouseX >= leftPos + 10
                && logicalMouseX < leftPos + 308
                && logicalMouseY >= topPos + 54
                && logicalMouseY < topPos + 175;
        if (overActivity && verticalAmount != 0.0) {
            if (verticalAmount > 0.0 && menu.canScrollActivityNewer()) {
                sendMenuButton(BankerMenu.BUTTON_ACTIVITY_NEWER);
            } else if (verticalAmount < 0.0 && menu.canScrollActivityOlder()) {
                sendMenuButton(BankerMenu.BUTTON_ACTIVITY_OLDER);
            }
            return true;
        }
        return super.mouseScrolled(
                mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    protected boolean hasClickedOutside(
            double mouseX, double mouseY, int guiLeft, int guiTop) {
        int scaledWidth = BankerScreenScale.scaled(WIDTH, interfaceScale);
        int scaledHeight = BankerScreenScale.scaled(HEIGHT, interfaceScale);
        return mouseX < guiLeft
                || mouseY < guiTop
                || mouseX >= guiLeft + scaledWidth
                || mouseY >= guiTop + scaledHeight;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractBackground(graphics, mouseX, mouseY, a);
        tooltipMouseX = mouseX;
        tooltipMouseY = mouseY;
        int logicalMouseX = (int) Math.round(logicalMouseX(mouseX));
        int logicalMouseY = (int) Math.round(logicalMouseY(mouseY));
        graphics.pose().pushMatrix();
        graphics.pose().translate(leftPos, topPos);
        graphics.pose().scale(interfaceScale, interfaceScale);
        graphics.pose().translate(-leftPos, -topPos);
        try {
        int x = leftPos;
        int y = topPos;
        graphics.fill(x, y, x + imageWidth, y + imageHeight, PANEL_DARK);
        graphics.fill(x + 3, y + 3, x + imageWidth - 3, y + imageHeight - 3, PANEL);
        graphics.outline(x + 3, y + 3, imageWidth - 6, imageHeight - 6, GOLD);
        graphics.fill(x + 8, y + 48, x + imageWidth - 8, y + 196, PANEL_LIGHT);
        int selectedTabX = x + BankerScreenLayout.TAB_X
                + tab * BankerScreenLayout.TAB_STEP;
        graphics.fill(
                selectedTabX,
                y + BankerScreenLayout.TAB_INDICATOR_Y,
                selectedTabX + BankerScreenLayout.TAB_WIDTH,
                y + BankerScreenLayout.TAB_INDICATOR_Y
                        + BankerScreenLayout.TAB_INDICATOR_HEIGHT,
                GOLD);

        if (tab == BankerMenu.TAB_OVERVIEW) {
            drawChart(graphics, menu.netWorthHistoryPointsCenti(),
                    menu.netWorthHistorySpanDays(),
                    x + BankerScreenLayout.OVERVIEW_CHART_X,
                    y + BankerScreenLayout.OVERVIEW_CHART_Y,
                    BankerScreenLayout.OVERVIEW_CHART_WIDTH,
                    BankerScreenLayout.OVERVIEW_CHART_HEIGHT,
                    logicalMouseX, logicalMouseY);
        } else if (tab == BankerMenu.TAB_MARKET) {
            drawChart(graphics, menu.historyPointsCenti(),
                    menu.historySpanDays(),
                    x + BankerScreenLayout.MARKET_CHART_X,
                    y + BankerScreenLayout.MARKET_CHART_Y,
                    BankerScreenLayout.MARKET_CHART_WIDTH,
                    BankerScreenLayout.MARKET_CHART_HEIGHT,
                    logicalMouseX, logicalMouseY);
        } else if (tab == BankerMenu.TAB_BANKING) {
            int selectedViewX = x + BankerScreenLayout.BANKING_SUBTAB_X
                    + bankView * BankerScreenLayout.BANKING_SUBTAB_STEP;
            graphics.fill(
                    selectedViewX,
                    y + BankerScreenLayout.BANKING_SUBTAB_Y
                            + BankerScreenLayout.BANKING_SUBTAB_HEIGHT,
                    selectedViewX + BankerScreenLayout.BANKING_SUBTAB_WIDTH,
                    y + BankerScreenLayout.BANKING_SUBTAB_Y
                            + BankerScreenLayout.BANKING_SUBTAB_HEIGHT + 2,
                    GOLD);
            graphics.outline(
                    x + 10,
                    y + BankerScreenLayout.BANKING_BALANCE_PANEL_Y,
                    298,
                    BankerScreenLayout.BANKING_BALANCE_PANEL_HEIGHT,
                    0xFF456B5A);
        } else if (tab == BankerMenu.TAB_EXCHANGE) {
            graphics.outline(x + 52, y + 62, 216, 47, 0xFF456B5A);
            drawChart(graphics, menu.commodityHistoryPointsCenti(),
                    menu.commodityHistorySpanDays(),
                    x + 52, y + 116, 216, 45, logicalMouseX, logicalMouseY);
        } else if (tab == BankerMenu.TAB_VILLAGE) {
            graphics.outline(
                    x + BankerScreenLayout.VILLAGE_LEFT_PANEL_X,
                    y + BankerScreenLayout.VILLAGE_PANEL_Y,
                    BankerScreenLayout.VILLAGE_LEFT_PANEL_WIDTH,
                    BankerScreenLayout.VILLAGE_PANEL_HEIGHT,
                    0xFF456B5A);
            graphics.outline(
                    x + BankerScreenLayout.VILLAGE_RIGHT_PANEL_X,
                    y + BankerScreenLayout.VILLAGE_PANEL_Y,
                    BankerScreenLayout.VILLAGE_RIGHT_PANEL_WIDTH,
                    BankerScreenLayout.VILLAGE_PANEL_HEIGHT,
                    0xFF456B5A);
        } else if (tab == BankerMenu.TAB_FUND) {
            graphics.outline(x + 10, y + 54, 298, 97, 0xFF456B5A);
        } else if (tab == TAB_ACTIVITY) {
            graphics.outline(x + 10, y + 54, 298, 121, 0xFF456B5A);
            drawActivityScrollbar(graphics, x, y);
        }
        addContextTooltip(graphics, logicalMouseX, logicalMouseY, x, y);
        } finally {
            graphics.pose().popMatrix();
        }
    }

    private void drawActivityScrollbar(GuiGraphicsExtractor graphics, int screenX, int screenY) {
        int total = menu.activityTotalCount();
        if (total <= 0) {
            return;
        }
        int trackX = screenX + BankerScreenLayout.ACTIVITY_SCROLL_TRACK_X;
        int trackY = screenY + BankerScreenLayout.ACTIVITY_SCROLL_TRACK_Y;
        int trackHeight = BankerScreenLayout.ACTIVITY_SCROLL_TRACK_HEIGHT;
        graphics.fill(
                trackX,
                trackY,
                trackX + BankerScreenLayout.ACTIVITY_SCROLL_TRACK_WIDTH,
                trackY + trackHeight,
                0xFF29463A);
        int thumbHeight = Math.max(6, (int) Math.round(
                trackHeight * Math.min(BankerMenu.ACTIVITY_PAGE_SIZE, total) / (double) total));
        int maximumOffset = Math.max(0, total - BankerMenu.ACTIVITY_PAGE_SIZE);
        int travel = trackHeight - thumbHeight;
        int thumbY = trackY + (maximumOffset == 0
                ? 0 : (int) Math.round(menu.activityOffset() * travel / (double) maximumOffset));
        graphics.fill(
                trackX,
                thumbY,
                trackX + BankerScreenLayout.ACTIVITY_SCROLL_TRACK_WIDTH,
                thumbY + thumbHeight,
                EMERALD);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        graphics.pose().pushMatrix();
        graphics.pose().scale(interfaceScale, interfaceScale);
        try {
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

        if (pageUsesTransactionAmount()) {
            drawNativeText(graphics,
                    amountFooterLabel(),
                    12,
                    BankerScreenLayout.AMOUNT_LABEL_Y,
                    amountDraftDirty
                                    && BankerAmountSelection.parseAppliedAmount(amountDraft) == 0
                            ? NEGATIVE : MUTED,
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
            footer = marketBuySummary();
            footerColor = EMERALD;
        } else if (tab == BankerMenu.TAB_OVERVIEW) {
            footer = tr("overview.money_hint");
        } else if (tab == BankerMenu.TAB_EXCHANGE) {
            footer = exchangeSummary();
            footerColor = EMERALD;
        }
        if (footer != null) {
            drawTextWithin(graphics, footer, 12, BankerScreenLayout.FOOTER_Y,
                    296, footerColor, false);
        }
        } finally {
            graphics.pose().popMatrix();
        }
    }

    private void drawOverviewLabels(GuiGraphicsExtractor graphics) {
        drawNativeText(graphics, tr("overview.title"), 14, 56, TEXT, false);
        labelValue(graphics, tr("label.net_worth"), panelMoney(menu.netWorth()), 14, 72, GOLD);
        labelValue(graphics, tr("label.bank_cash"), panelMoney(menu.cash()), 14, 88, TEXT);
        labelValue(graphics, tr("label.savings"), panelMoney(menu.savings()), 14, 104, TEXT);
        labelValue(graphics, tr("label.invested"), panelMoney(investedValue()), 14, 120, TEXT);
        drawTextWithin(graphics,
                tr("overview.contributed_short", panelMoney(menu.totalContributions())),
                14, 136, 93, MUTED, false);
        drawTextWithin(graphics,
                tr("overview.performance_short",
                        signedMoney(menu.unrealizedGain() + menu.realizedGain())),
                BankerScreenLayout.OVERVIEW_PERFORMANCE_X,
                BankerScreenLayout.OVERVIEW_PERFORMANCE_Y,
                BankerScreenLayout.OVERVIEW_PERFORMANCE_WIDTH,
                menu.unrealizedGain() + menu.realizedGain() >= 0.0 ? POSITIVE : NEGATIVE,
                false);

        drawTextWithin(graphics, tr("overview.net_worth_history"),
                BankerScreenLayout.OVERVIEW_HISTORY_CHANGE_X, 50,
                BankerScreenLayout.OVERVIEW_CHART_TITLE_WIDTH, TEXT, false);
        drawTextWithin(graphics,
                tr("overview.history_change", signed(historyChange(
                        menu.netWorthHistoryPointsCenti()))),
                BankerScreenLayout.OVERVIEW_HISTORY_CHANGE_X,
                BankerScreenLayout.OVERVIEW_HISTORY_CHANGE_Y,
                BankerScreenLayout.OVERVIEW_HISTORY_CHANGE_WIDTH,
                historyChange(menu.netWorthHistoryPointsCenti()) >= 0.0 ? POSITIVE : NEGATIVE,
                false);
    }

    private void drawMarketLabels(GuiGraphicsExtractor graphics) {
        EconomyEngine.Asset selected = menu.selectedAsset();
        drawTextWithin(graphics, tr("market.choose"),
                BankerScreenLayout.MARKET_SELECTOR_LABEL_X,
                BankerScreenLayout.MARKET_SELECTOR_LABEL_Y,
                BankerScreenLayout.MARKET_META_WIDTH, TEXT, false);
        drawTextWithin(graphics, Component.literal(selected.sector()),
                BankerScreenLayout.MARKET_SELECTOR_LABEL_X,
                BankerScreenLayout.MARKET_SECTOR_Y,
                BankerScreenLayout.MARKET_META_WIDTH, TEXT, false);
        drawTextWithin(graphics, riskLabel(selected),
                BankerScreenLayout.MARKET_SELECTOR_LABEL_X,
                BankerScreenLayout.MARKET_RISK_Y,
                BankerScreenLayout.MARKET_META_WIDTH, MUTED, false);

        drawTextWithin(graphics, Component.literal(selected.name()),
                BankerScreenLayout.MARKET_TITLE_X,
                BankerScreenLayout.MARKET_TITLE_Y,
                BankerScreenLayout.MARKET_TITLE_WIDTH,
                TEXT,
                false);
        drawTextWithin(graphics,
                tr("market.price_change", money(menu.selectedAssetPrice()),
                        signed(menu.selectedChangePercent())),
                BankerScreenLayout.MARKET_DETAIL_X,
                BankerScreenLayout.MARKET_PRICE_Y,
                BankerScreenLayout.MARKET_DETAIL_WIDTH,
                menu.selectedChangePercent() >= 0.0 ? POSITIVE : NEGATIVE,
                false);
        drawTextWithin(graphics,
                tr("market.holding", compactShares(menu.selectedShares()),
                        money(menu.selectedHoldingValue()),
                        String.format(Locale.ROOT, "%.1f", menu.selectedAllocationPercent())),
                BankerScreenLayout.MARKET_DETAIL_X,
                BankerScreenLayout.MARKET_HOLDING_Y,
                BankerScreenLayout.MARKET_DETAIL_WIDTH,
                MUTED,
                false);
        drawTextWithin(graphics,
                tr("market.average_price", money(menu.selectedAveragePrice())),
                BankerScreenLayout.MARKET_DETAIL_X,
                BankerScreenLayout.MARKET_AVERAGE_Y,
                BankerScreenLayout.MARKET_DETAIL_WIDTH,
                MUTED,
                false);
        drawTextWithin(graphics,
                tr("market.total_basis", money(menu.totalCostBasis())),
                BankerScreenLayout.MARKET_DETAIL_X,
                BankerScreenLayout.MARKET_BASIS_Y,
                BankerScreenLayout.MARKET_DETAIL_WIDTH,
                MUTED,
                false);
    }

    private void drawBankingLabels(GuiGraphicsExtractor graphics) {
        switch (bankView) {
            case BANK_VIEW_CDS -> drawCdLabels(graphics);
            case BANK_VIEW_LOANS -> drawLoanLabels(graphics);
            default -> drawTransferLabels(graphics);
        }
    }

    private void drawTransferLabels(GuiGraphicsExtractor graphics) {
        drawBankBalanceColumn(graphics, tr("label.inventory_emeralds"),
                money(menu.physicalEmeralds()), 16, 88);
        drawBankBalanceColumn(graphics, tr("label.bank_cash"), money(menu.cash()), 113, 88);
        drawBankBalanceColumn(graphics,
                tr("banking.savings_with_rate",
                        String.format(Locale.ROOT, "%.2f", menu.savingsRate())),
                money(menu.savings()), 210, 92);
        drawTextWithin(graphics, tr("banking.withdraw_savings_help"), 14, 171, 292,
                MUTED, false);
    }

    private void drawCdLabels(GuiGraphicsExtractor graphics) {
        drawBankBalanceColumn(graphics, tr("label.bank_cash"), money(menu.cash()), 16, 88);
        drawBankBalanceColumn(graphics, tr("banking.total_cds"), money(menu.cdValue()), 113, 88);
        drawBankBalanceColumn(graphics, tr("banking.positions_short"),
                menu.cdCount() + "/" + EconomyState.MAX_TERM_POSITIONS, 210, 92);
        drawNativeText(graphics, tr("banking.cd_term"), 14,
                BankerScreenLayout.BANKING_PRODUCT_CONTROL_Y + 5, MUTED, false);
        int amount = selectedWholeAmount(menu.cash());
        drawTextWithin(graphics,
                tr("banking.new_cd_flow", money(amount)),
                14, BankerScreenLayout.BANKING_PRODUCT_DETAIL_Y, 292, MUTED, false);
        drawTextWithin(graphics,
                tr("banking.new_cd_after", money(Math.max(0.0, menu.cash() - amount)),
                        money(menu.cdValue() + amount)),
                14, BankerScreenLayout.BANKING_PRODUCT_PREVIEW_Y, 292,
                amount > 0 ? POSITIVE : MUTED, false);
    }

    private void drawLoanLabels(GuiGraphicsExtractor graphics) {
        drawBankBalanceColumn(graphics, tr("label.bank_cash"), money(menu.cash()), 16, 88);
        drawBankBalanceColumn(graphics, tr("banking.total_loans"),
                money(menu.lendingValue()), 113, 88);
        drawBankBalanceColumn(graphics, tr("banking.positions_short"),
                menu.lendingCount() + "/" + EconomyState.MAX_TERM_POSITIONS, 210, 92);
        drawNativeText(graphics, tr("banking.loan_term"), 14,
                BankerScreenLayout.BANKING_PRODUCT_CONTROL_Y + 5, MUTED, false);
        int amount = selectedWholeAmount(menu.cash());
        drawTextWithin(graphics,
                tr("banking.new_loan_flow", money(amount)),
                14, BankerScreenLayout.BANKING_PRODUCT_DETAIL_Y, 292, MUTED, false);
        drawTextWithin(graphics,
                tr("banking.new_loan_after", money(Math.max(0.0, menu.cash() - amount)),
                        money(menu.lendingValue() + amount)),
                14, BankerScreenLayout.BANKING_PRODUCT_PREVIEW_Y, 292,
                amount > 0 ? POSITIVE : MUTED, false);
    }

    private void drawBankBalanceColumn(
            GuiGraphicsExtractor graphics,
            Component label,
            String value,
            int x,
            int width) {
        drawTextWithin(graphics, label, x, 82, width, GOLD, false);
        drawTextWithin(graphics, Component.literal(value), x, 99, width, TEXT, false);
    }

    private void drawExchangeLabels(GuiGraphicsExtractor graphics) {
        Component resource = tr("resource." + menu.selectedResourceName());
        drawNativeCenteredText(graphics, resource, 160, 69, GOLD);
        drawNativeCenteredText(graphics,
                tr("exchange.owned", menu.selectedResourceCount()),
                160,
                83,
                TEXT);
        drawNativeCenteredText(graphics,
                tr("exchange.quote", String.format(Locale.ROOT, "%.2f",
                        menu.selectedResourceUnitQuote())),
                160,
                96,
                MUTED);
    }

    private void drawVillageLabels(GuiGraphicsExtractor graphics) {
        if (!menu.hasVillage()) {
            drawNativeCenteredText(graphics, tr("village.none"), 160, 92, MUTED);
            drawNativeCenteredText(graphics, tr("village.find_bank"), 160, 110, MUTED);
            return;
        }
        drawTextWithin(graphics, tr("village.community"),
                BankerScreenLayout.VILLAGE_LEFT_TEXT_X, 59,
                BankerScreenLayout.VILLAGE_LEFT_TEXT_WIDTH, GOLD, false);
        drawTextWithin(graphics,
                tr("village.status", tr("village.lifecycle."
                        + menu.villageLifecycle().name().toLowerCase(Locale.ROOT))),
                BankerScreenLayout.VILLAGE_LEFT_TEXT_X,
                75,
                BankerScreenLayout.VILLAGE_LEFT_TEXT_WIDTH,
                TEXT,
                false);
        drawTextWithin(graphics,
                tr("village.population", menu.villagePopulation(), menu.villageHousing()),
                BankerScreenLayout.VILLAGE_LEFT_TEXT_X,
                89,
                BankerScreenLayout.VILLAGE_LEFT_TEXT_WIDTH,
                TEXT,
                false);
        drawTextWithin(graphics,
                tr("village.tier", menu.villageTier()),
                BankerScreenLayout.VILLAGE_LEFT_TEXT_X,
                103,
                BankerScreenLayout.VILLAGE_LEFT_TEXT_WIDTH,
                TEXT,
                false);
        drawTextWithin(graphics,
                tr("village.prosperity", String.format(Locale.ROOT, "%.1f", menu.villageProsperity())),
                BankerScreenLayout.VILLAGE_LEFT_TEXT_X,
                117,
                BankerScreenLayout.VILLAGE_LEFT_TEXT_WIDTH,
                menu.villageProsperity() >= 50.0 ? POSITIVE : GOLD,
                false);
        drawTextWithin(graphics,
                tr("village.safety", String.format(Locale.ROOT, "%.1f", menu.villageSafety())),
                BankerScreenLayout.VILLAGE_LEFT_TEXT_X,
                131,
                BankerScreenLayout.VILLAGE_LEFT_TEXT_WIDTH,
                menu.villageSafety() >= 50.0 ? POSITIVE : NEGATIVE,
                false);

        drawTextWithin(graphics, tr("village.resources"),
                BankerScreenLayout.VILLAGE_RIGHT_TEXT_X, 59,
                BankerScreenLayout.VILLAGE_RIGHT_TEXT_WIDTH, GOLD, false);
        drawTextWithin(graphics,
                tr("village.food", String.format(Locale.ROOT, "%.1f", menu.villageFood())),
                BankerScreenLayout.VILLAGE_RIGHT_TEXT_X,
                75,
                BankerScreenLayout.VILLAGE_RIGHT_TEXT_WIDTH,
                TEXT,
                false);
        drawTextWithin(graphics,
                tr("village.materials", String.format(Locale.ROOT, "%.1f", menu.villageMaterials())),
                BankerScreenLayout.VILLAGE_RIGHT_TEXT_X,
                89,
                BankerScreenLayout.VILLAGE_RIGHT_TEXT_WIDTH,
                TEXT,
                false);
        drawTextWithin(graphics,
                tr("village.treasury", money(menu.villageTreasury())),
                BankerScreenLayout.VILLAGE_RIGHT_TEXT_X,
                103,
                BankerScreenLayout.VILLAGE_RIGHT_TEXT_WIDTH,
                TEXT,
                false);
        int projectOrdinal = menu.villageProjectTypeOrdinal();
        Component project = projectOrdinal < 0
                || projectOrdinal >= VillageProsperityEngine.ProjectType.values().length
                ? tr("village.project.none")
                : tr("village.project."
                        + VillageProsperityEngine.ProjectType.values()[projectOrdinal]
                                .name().toLowerCase(Locale.ROOT));
        drawTextWithin(graphics,
                tr("village.project", project),
                BankerScreenLayout.VILLAGE_RIGHT_TEXT_X,
                117,
                BankerScreenLayout.VILLAGE_RIGHT_TEXT_WIDTH,
                TEXT,
                false);
        drawTextWithin(graphics,
                villageProjectStageLabel(),
                BankerScreenLayout.VILLAGE_RIGHT_TEXT_X,
                131,
                BankerScreenLayout.VILLAGE_RIGHT_TEXT_WIDTH,
                MUTED,
                false);
        drawTextWithin(graphics, villageModeLabel(),
                BankerScreenLayout.VILLAGE_LEFT_TEXT_X,
                BankerScreenLayout.VILLAGE_PRIMARY_DETAIL_Y,
                BankerScreenLayout.VILLAGE_LEFT_TEXT_WIDTH, MUTED, false);
        double localImpactScore = villageLocalImpactScore();
        Component localImpact = villageLocalImpactLabel(localImpactScore);
        int impactColor = localImpactScore >= 0.10
                ? POSITIVE
                : localImpactScore >= -0.20 ? GOLD : NEGATIVE;
        drawTextWithin(graphics, localImpact,
                BankerScreenLayout.VILLAGE_LEFT_TEXT_X,
                BankerScreenLayout.VILLAGE_SECONDARY_DETAIL_Y,
                BankerScreenLayout.VILLAGE_LEFT_TEXT_WIDTH, impactColor, false);
        boolean restoration = villageNeedsRestoration();
        if (restoration) {
            drawTextWithin(graphics,
                    tr("village.restoration",
                            String.format(Locale.ROOT, "%.1f", menu.villageRestorationFund()),
                            String.format(Locale.ROOT, "%.0f", VillageProsperityEngine.RESTORATION_EMERALD_TARGET)),
                    BankerScreenLayout.VILLAGE_RIGHT_TEXT_X,
                    BankerScreenLayout.VILLAGE_PRIMARY_DETAIL_Y,
                    BankerScreenLayout.VILLAGE_RIGHT_TEXT_WIDTH,
                    GOLD,
                    false);
        } else if (menu.villageIncidentCause()
                != VillageProsperityEngine.IncidentCause.NONE) {
            Component age = villageIncidentAgeLabel();
            drawTextWithin(graphics,
                    tr("village.news",
                            tr("village.incident."
                                    + menu.villageIncidentCause()
                                            .name().toLowerCase(Locale.ROOT)),
                            age),
                    BankerScreenLayout.VILLAGE_RIGHT_TEXT_X,
                    BankerScreenLayout.VILLAGE_PRIMARY_DETAIL_Y,
                    BankerScreenLayout.VILLAGE_RIGHT_TEXT_WIDTH,
                    menu.villageIncidentCause() == VillageProsperityEngine.IncidentCause.PLAYER
                            ? NEGATIVE
                            : GOLD,
                    false);
        }
        drawTextWithin(graphics,
                tr("village.outputs_short",
                        String.format(Locale.ROOT, "%.1f", menu.villageAgricultureOutput()),
                        String.format(Locale.ROOT, "%.1f", menu.villageMiningOutput()),
                        String.format(Locale.ROOT, "%.1f", menu.villageTradeOutput())),
                BankerScreenLayout.VILLAGE_RIGHT_TEXT_X,
                BankerScreenLayout.VILLAGE_SECONDARY_DETAIL_Y,
                BankerScreenLayout.VILLAGE_RIGHT_TEXT_WIDTH,
                MUTED,
                false);
    }

    private void drawActivityLabels(GuiGraphicsExtractor graphics) {
        drawTextWithin(graphics, tr("activity.title"), 16, 59, 188, GOLD, false);
        drawTextWithin(
                graphics,
                tr("activity.position",
                        menu.activityFirstVisiblePosition(),
                        menu.activityLastVisiblePosition(),
                        menu.activityTotalCount()),
                BankerScreenLayout.ACTIVITY_POSITION_X,
                59,
                BankerScreenLayout.ACTIVITY_POSITION_WIDTH,
                MUTED,
                false);
        drawTextWithin(graphics,
                tr("activity.cash_flow", money(menu.totalContributions()),
                        money(menu.totalWithdrawals())),
                16, 70, 286, MUTED, false);
        if (menu.activityPageCount() == 0) {
            boolean filtered = menu.activityFilter() != BankerMenu.ActivityFilter.ALL;
            drawNativeCenteredText(graphics,
                    tr(filtered ? "activity.empty_filtered" : "activity.empty"),
                    160, 108, MUTED);
            drawNativeCenteredText(graphics,
                    tr(filtered ? "activity.empty_filtered_hint" : "activity.empty_hint"),
                    160, 124, MUTED);
            return;
        }
        for (int index = 0; index < menu.activityPageCount(); index++) {
            drawTextWithin(graphics,
                    activityEntry(index, false),
                    BankerScreenLayout.ACTIVITY_LIST_X,
                    BankerScreenLayout.ACTIVITY_FIRST_ROW_Y
                            + index * BankerScreenLayout.ACTIVITY_ROW_STEP,
                    BankerScreenLayout.ACTIVITY_LIST_WIDTH,
                    index == 0 ? TEXT : MUTED,
                    false);
        }
        drawTextWithin(
                graphics,
                tr("activity.newest_first"),
                BankerScreenLayout.ACTIVITY_ORDER_X,
                BankerScreenLayout.ACTIVITY_ORDER_Y,
                BankerScreenLayout.ACTIVITY_ORDER_WIDTH,
                MUTED,
                false);
    }

    private Component activityEntry(int index, boolean exactAmount) {
        BankerMenu.ActivityKind entryKind = menu.activityKind(index);
        Component subject = activityKindLabel(entryKind);
        String ticker = menu.activityAssetTicker(index);
        String resource = menu.activityExchangeResourceName(index);
        if (!ticker.isEmpty()) {
            subject = subject.copy().append(Component.literal(" " + ticker));
        } else if (entryKind == BankerMenu.ActivityKind.EXCHANGE && !resource.isEmpty()) {
            subject = tr("activity.exchange_subject",
                    menu.activityQuantity(index), tr("resource." + resource));
        } else if (entryKind == BankerMenu.ActivityKind.INTEREST
                && menu.activityQuantity(index) > 1) {
            subject = tr("activity.interest_subject", menu.activityQuantity(index));
        }
        EconomyState.DonationPurpose purpose = menu.activityFundPurpose(index);
        if (purpose != null) {
            subject = subject.copy()
                    .append(Component.literal(" "))
                    .append(tr("fund.purpose." + purpose.name().toLowerCase(Locale.ROOT)));
        }
        long referenceId = menu.activityReferenceId(index);
        boolean identifiesPosition = switch (entryKind) {
            case CD_OPEN, CD_CLOSE, LENDING_OPEN, LENDING_CLOSE, PROJECT_SPONSORSHIP -> true;
            default -> false;
        };
        if (identifiesPosition && referenceId > 0L) {
            subject = subject.copy()
                    .append(Component.literal(" "))
                    .append(tr("activity.reference", referenceId));
        }
        return tr("activity.entry", menu.activityDay(index), subject,
                exactAmount ? exactMoney(menu.activityAmount(index))
                        : money(menu.activityAmount(index)));
    }

    private void drawFundLabels(GuiGraphicsExtractor graphics) {
        if (!menu.hasVillage()) {
            drawNativeCenteredText(graphics, tr("village.none"), 160, 92, MUTED);
            drawNativeCenteredText(graphics, tr("village.find_bank"), 160, 110, MUTED);
            return;
        }
        drawTextWithin(graphics, tr("fund.title"),
                BankerScreenLayout.FUND_TITLE_X,
                BankerScreenLayout.FUND_DONOR_Y,
                BankerScreenLayout.FUND_TITLE_WIDTH,
                GOLD,
                false);
        drawTextWithin(graphics, tr("fund.spendable", money(menu.fundSpendable())),
                16, 73, 136, TEXT, false);
        drawTextWithin(graphics, tr("fund.endowment", money(menu.fundEndowment())),
                168, 73, 134, TEXT, false);
        drawTextWithin(graphics, tr("fund.reserve", money(menu.fundReserve())),
                16, 85, 136, MUTED, false);
        drawTextWithin(graphics, tr("fund.received", money(menu.fundLifetimeReceived())),
                168, 85, 134, MUTED, false);
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
                    16, 132, 286, NEGATIVE, false);
            return;
        }
        if (menu.fundTypeIndex() == 2) {
            drawTextWithin(graphics,
                    tr("fund.project_target", projectLabel(menu.fundableProjectTypeOrdinal())),
                    16, BankerScreenLayout.FUND_PROJECT_Y, 286, MUTED, false);
        }
        drawTextWithin(graphics,
                tr("fund.cash_flow", money(menu.donationDraft()),
                        money(Math.max(0.0, menu.cash() - menu.donationDraft()))),
                16, BankerScreenLayout.FUND_NOTICE_Y, 286,
                menu.donationDraft() > 0 ? POSITIVE : MUTED, false);
    }

    private Component fundTypeExplanation() {
        Component explanation = switch (menu.fundTypeIndex()) {
            case 1 -> tr("fund.explain.endowment");
            case 2 -> tr("fund.explain.sponsorship");
            default -> tr("fund.explain.grant");
        };
        return Component.empty()
                .append(explanation)
                .append(Component.literal(" "))
                .append(tr("fund.irreversible"))
                .append(Component.literal(" "))
                .append(tr("fund.no_debt"));
    }

    private Component closeCdTooltip() {
        return switch (BankerScreenLayout.cdCloseTooltipState(
                menu.hasCd(), menu.cdDaysRemaining())) {
            case UNAVAILABLE -> tr("tooltip.no_cd");
            case EARLY -> tr("tooltip.close_cd_early");
            case MATURE -> tr("tooltip.close_cd_mature");
        };
    }

    private Component fundPurposeTooltip(
            BankerScreenLayout.FundPurposeTooltipState state) {
        return switch (state) {
            case CYCLABLE -> tr("tooltip.fund_purpose", fundPurposeLabel());
            case FUND_UNAVAILABLE -> tr("tooltip.fund_unavailable");
            case TARGETING_DISABLED -> tr("tooltip.fund_purpose_targeting_disabled");
            case RESTORATION_FIXED -> tr("tooltip.fund_purpose_restoration_fixed");
            case PROJECT_FIXED -> tr("tooltip.fund_purpose_project_fixed");
        };
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
                && villageNeedsRestoration();
    }

    private boolean villageNeedsRestoration() {
        return menu.villageLifecycle() == VillageProsperityEngine.Lifecycle.ABANDONED
                || menu.villageLifecycle() == VillageProsperityEngine.Lifecycle.EXTINCT;
    }

    private Component villageModeLabel() {
        return menu.villageSimulationEnabled()
                ? menu.villageVisualProgressionEnabled()
                        ? tr("village.mode.full")
                        : tr("village.mode.simulation")
                : menu.villageVisualProgressionEnabled()
                        ? tr("village.mode.visual")
                        : tr("village.mode.off");
    }

    private double villageLocalImpactScore() {
        return VillageProsperityEngine.broadFundamentalScore(
                menu.villageProsperity(), menu.villageSafety(), menu.villageTier());
    }

    private Component villageLocalImpactLabel(double score) {
        return score >= 0.45
                ? tr("village.impact.strong")
                : score >= 0.10
                        ? tr("village.impact.positive")
                        : score >= -0.20
                                ? tr("village.impact.neutral")
                                : tr("village.impact.weak");
    }

    private Component villageIncidentAgeLabel() {
        return menu.villageIncidentAge() == 0
                ? tr("village.news.today")
                : tr("village.news.days_ago", menu.villageIncidentAge());
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

    private Component villageProjectStageLabel() {
        if (menu.villageProjectTypeOrdinal() < 0) {
            return tr("village.progress.none");
        }
        return tr(menu.fundableProjectTypeOrdinal() >= 0
                        ? "village.progress.planning"
                        : "village.progress.building",
                String.format(Locale.ROOT, "%.1f", menu.villageProjectProgress()));
    }

    private Component villageProjectStageDetail() {
        if (menu.villageProjectTypeOrdinal() < 0) {
            return tr("tooltip.village_project_none");
        }
        return tr(menu.fundableProjectTypeOrdinal() >= 0
                ? "tooltip.village_project_planning"
                : "tooltip.village_project_building");
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
            drawNativeCenteredText(graphics, tr("chart.history_building"), x + width / 2,
                    y + height / 2 - 4, MUTED);
            if (mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height) {
                graphics.setTooltipForNextFrame(
                        font, tr("tooltip.history_empty"), tooltipMouseX, tooltipMouseY);
            }
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
        drawNativeText(graphics, Component.literal(money(max / 100.0)),
                x + 4, y + 4, MUTED, false);
        drawNativeText(graphics, Component.literal(money(min / 100.0)),
                x + 4, y + height - 12, MUTED, false);
        drawNativeText(graphics, Component.literal(historyRangeLabel()),
                x + width - 34, y + height - 12,
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
                    tooltipMouseX,
                    tooltipMouseY);
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

    private void addContextTooltip(
            GuiGraphicsExtractor graphics,
            int mouseX,
            int mouseY,
            int screenX,
            int screenY) {
        switch (tab) {
            case BankerMenu.TAB_OVERVIEW -> {
                overviewValueTooltip(graphics, mouseX, mouseY, screenX, screenY,
                        tr("label.net_worth"), menu.netWorth(), 72);
                overviewValueTooltip(graphics, mouseX, mouseY, screenX, screenY,
                        tr("label.bank_cash"), menu.cash(), 88);
                overviewValueTooltip(graphics, mouseX, mouseY, screenX, screenY,
                        tr("label.savings"), menu.savings(), 104);
                overviewValueTooltip(graphics, mouseX, mouseY, screenX, screenY,
                        tr("label.invested"), investedValue(), 120);
                tooltipWhenHovered(
                        graphics, mouseX, mouseY,
                        screenX + BankerScreenLayout.OVERVIEW_PERFORMANCE_X,
                        screenY + BankerScreenLayout.OVERVIEW_PERFORMANCE_Y,
                        BankerScreenLayout.OVERVIEW_PERFORMANCE_WIDTH,
                        BankerScreenLayout.TEXT_HEIGHT,
                        tr("tooltip.overview_performance",
                                exactSignedMoney(menu.unrealizedGain()),
                                exactSignedMoney(menu.realizedGain())));
            }
            case BankerMenu.TAB_MARKET -> addMarketContextTooltips(
                    graphics, mouseX, mouseY, screenX, screenY);
            case BankerMenu.TAB_BANKING -> {
                tooltipWhenHovered(
                        graphics, mouseX, mouseY,
                        screenX + 10,
                        screenY + BankerScreenLayout.BANKING_BALANCE_PANEL_Y,
                        298,
                        BankerScreenLayout.BANKING_BALANCE_PANEL_HEIGHT,
                        switch (bankView) {
                            case BANK_VIEW_CDS -> tr("tooltip.banking_cds_balance");
                            case BANK_VIEW_LOANS -> tr("tooltip.banking_loans_balance");
                            default -> tr("tooltip.banking_transfer_balances");
                        });
            }
            case BankerMenu.TAB_EXCHANGE -> tooltipWhenHovered(
                    graphics, mouseX, mouseY,
                    screenX + 52,
                    screenY + 62,
                    216,
                    47,
                        exchangePreview());
            case BankerMenu.TAB_VILLAGE -> {
                if (!menu.hasVillage()) {
                    return;
                }
                tooltipWhenHovered(
                        graphics, mouseX, mouseY,
                        screenX + BankerScreenLayout.VILLAGE_LEFT_PANEL_X,
                        screenY + BankerScreenLayout.VILLAGE_PANEL_Y,
                        BankerScreenLayout.VILLAGE_LEFT_PANEL_WIDTH,
                        BankerScreenLayout.VILLAGE_TOP_SECTION_HEIGHT,
                        tr("tooltip.village_summary",
                                tr("village.lifecycle."
                                        + menu.villageLifecycle().name()
                                                .toLowerCase(Locale.ROOT)),
                                menu.villagePopulation(), menu.villageHousing(),
                                menu.villageTier(),
                                String.format(Locale.ROOT, "%.1f",
                                        menu.villageProsperity()),
                                String.format(Locale.ROOT, "%.1f",
                                        menu.villageSafety())));
                tooltipWhenHovered(
                        graphics, mouseX, mouseY,
                        screenX + BankerScreenLayout.VILLAGE_RIGHT_PANEL_X,
                        screenY + BankerScreenLayout.VILLAGE_PANEL_Y,
                        BankerScreenLayout.VILLAGE_RIGHT_PANEL_WIDTH,
                        BankerScreenLayout.VILLAGE_TOP_SECTION_HEIGHT,
                        tr("tooltip.village_resources",
                                String.format(Locale.ROOT, "%.1f", menu.villageFood()),
                                String.format(Locale.ROOT, "%.1f",
                                        menu.villageMaterials()),
                                money(menu.villageTreasury()),
                                projectLabel(menu.villageProjectTypeOrdinal()),
                                villageProjectStageLabel(),
                                menu.villageProjectBacklog(),
                                villageProjectStageDetail()));
                tooltipWhenHovered(
                        graphics, mouseX, mouseY,
                        screenX + BankerScreenLayout.VILLAGE_LEFT_TEXT_X,
                        screenY + BankerScreenLayout.VILLAGE_PRIMARY_DETAIL_Y,
                        BankerScreenLayout.VILLAGE_LEFT_TEXT_WIDTH,
                        BankerScreenLayout.TEXT_HEIGHT,
                        tr("tooltip.village_mode"));
                tooltipWhenHovered(
                        graphics, mouseX, mouseY,
                        screenX + BankerScreenLayout.VILLAGE_LEFT_TEXT_X,
                        screenY + BankerScreenLayout.VILLAGE_SECONDARY_DETAIL_Y,
                        BankerScreenLayout.VILLAGE_LEFT_TEXT_WIDTH,
                        BankerScreenLayout.TEXT_HEIGHT,
                        tr("tooltip.village_impact",
                                villageLocalImpactLabel(villageLocalImpactScore())));
                if (villageNeedsRestoration()) {
                    tooltipWhenHovered(
                            graphics, mouseX, mouseY,
                            screenX + BankerScreenLayout.VILLAGE_RIGHT_TEXT_X,
                            screenY + BankerScreenLayout.VILLAGE_PRIMARY_DETAIL_Y,
                            BankerScreenLayout.VILLAGE_RIGHT_TEXT_WIDTH,
                            BankerScreenLayout.TEXT_HEIGHT,
                            tr("tooltip.village_restoration_progress",
                                    String.format(Locale.ROOT, "%.1f",
                                            menu.villageRestorationFund()),
                                    String.format(Locale.ROOT, "%.0f",
                                            VillageProsperityEngine.RESTORATION_EMERALD_TARGET)));
                } else if (menu.villageIncidentCause()
                        != VillageProsperityEngine.IncidentCause.NONE) {
                    tooltipWhenHovered(
                            graphics, mouseX, mouseY,
                            screenX + BankerScreenLayout.VILLAGE_RIGHT_TEXT_X,
                            screenY + BankerScreenLayout.VILLAGE_PRIMARY_DETAIL_Y,
                            BankerScreenLayout.VILLAGE_RIGHT_TEXT_WIDTH,
                            BankerScreenLayout.TEXT_HEIGHT,
                            tr("tooltip.village_news",
                                    tr("village.incident."
                                            + menu.villageIncidentCause().name()
                                                    .toLowerCase(Locale.ROOT)),
                                    villageIncidentAgeLabel()));
                }
                tooltipWhenHovered(
                        graphics, mouseX, mouseY,
                        screenX + BankerScreenLayout.VILLAGE_RIGHT_TEXT_X,
                        screenY + BankerScreenLayout.VILLAGE_SECONDARY_DETAIL_Y,
                        BankerScreenLayout.VILLAGE_RIGHT_TEXT_WIDTH,
                        BankerScreenLayout.TEXT_HEIGHT,
                        tr("tooltip.village_outputs",
                                String.format(Locale.ROOT, "%.1f",
                                        menu.villageAgricultureOutput()),
                                String.format(Locale.ROOT, "%.1f",
                                        menu.villageMiningOutput()),
                                String.format(Locale.ROOT, "%.1f",
                                        menu.villageTradeOutput())));
            }
            case BankerMenu.TAB_FUND -> {
                if (!menu.hasVillage()) {
                    return;
                }
                tooltipWhenHovered(
                        graphics, mouseX, mouseY,
                        screenX + BankerScreenLayout.FUND_DONOR_X,
                        screenY + BankerScreenLayout.FUND_DONOR_Y,
                        BankerScreenLayout.FUND_DONOR_WIDTH,
                        BankerScreenLayout.TEXT_HEIGHT,
                        tr("fund.your_support", money(menu.donorLifetimeContribution()),
                                donorTitleLabel()));
                tooltipWhenHovered(
                        graphics, mouseX, mouseY,
                        screenX + 12,
                        screenY + 122,
                        294,
                        31,
                        menu.fundAvailable()
                                ? fundTypeExplanation()
                                : tr("tooltip.fund_unavailable"));
            }
            case TAB_ACTIVITY -> {
                for (int index = 0; index < menu.activityPageCount(); index++) {
                    tooltipWhenHovered(
                            graphics,
                            mouseX,
                            mouseY,
                            screenX + BankerScreenLayout.ACTIVITY_LIST_X,
                            screenY + BankerScreenLayout.ACTIVITY_FIRST_ROW_Y
                                    + index * BankerScreenLayout.ACTIVITY_ROW_STEP,
                            BankerScreenLayout.ACTIVITY_LIST_WIDTH,
                            BankerScreenLayout.TEXT_HEIGHT,
                            activityEntry(index, true));
                }
                if (menu.activityPageCount() > 0) {
                    tooltipWhenHovered(
                            graphics, mouseX, mouseY,
                            screenX + BankerScreenLayout.ACTIVITY_ORDER_X,
                            screenY + BankerScreenLayout.ACTIVITY_FILTER_Y,
                            BankerScreenLayout.ACTIVITY_ORDER_WIDTH,
                            BankerScreenLayout.ACTIVITY_FILTER_HEIGHT,
                            tr("tooltip.activity_retention"));
                }
            }
            default -> {
            }
        }
    }

    private void addMarketContextTooltips(
            GuiGraphicsExtractor graphics,
            int mouseX,
            int mouseY,
            int screenX,
            int screenY) {
        EconomyEngine.Asset selected = menu.selectedAsset();
        tooltipWhenHovered(
                graphics, mouseX, mouseY,
                screenX + BankerScreenLayout.MARKET_SELECTOR_LABEL_X,
                screenY + BankerScreenLayout.MARKET_SELECTOR_LABEL_Y,
                BankerScreenLayout.MARKET_META_WIDTH,
                BankerScreenLayout.TEXT_HEIGHT,
                tr("tooltip.market_selector"));
        tooltipWhenHovered(
                graphics, mouseX, mouseY,
                screenX + BankerScreenLayout.MARKET_TITLE_X,
                screenY + BankerScreenLayout.MARKET_TITLE_Y,
                BankerScreenLayout.MARKET_TITLE_WIDTH,
                BankerScreenLayout.TEXT_HEIGHT,
                tr("tooltip.market_name", selected.ticker(), selected.name()));
        tooltipWhenHovered(
                graphics, mouseX, mouseY,
                screenX + BankerScreenLayout.MARKET_SELECTOR_LABEL_X,
                screenY + BankerScreenLayout.MARKET_SECTOR_Y,
                BankerScreenLayout.MARKET_META_WIDTH,
                BankerScreenLayout.TEXT_HEIGHT,
                tr("tooltip.market_sector", selected.sector()));
        tooltipWhenHovered(
                graphics, mouseX, mouseY,
                screenX + BankerScreenLayout.MARKET_SELECTOR_LABEL_X,
                screenY + BankerScreenLayout.MARKET_RISK_Y,
                BankerScreenLayout.MARKET_META_WIDTH,
                BankerScreenLayout.TEXT_HEIGHT,
                tr("tooltip.market_risk", riskLabel(selected)));
        tooltipWhenHovered(
                graphics, mouseX, mouseY,
                screenX + BankerScreenLayout.MARKET_DETAIL_X,
                screenY + BankerScreenLayout.MARKET_PRICE_Y,
                BankerScreenLayout.MARKET_DETAIL_WIDTH,
                BankerScreenLayout.TEXT_HEIGHT,
                tr("tooltip.market_price_change",
                        exactMoney(menu.selectedAssetPrice()),
                        signed(menu.selectedChangePercent())));
        tooltipWhenHovered(
                graphics, mouseX, mouseY,
                screenX + BankerScreenLayout.MARKET_DETAIL_X,
                screenY + BankerScreenLayout.MARKET_HOLDING_Y,
                BankerScreenLayout.MARKET_DETAIL_WIDTH,
                BankerScreenLayout.TEXT_HEIGHT,
                tr("tooltip.market_holding_exact",
                        exactShares(menu.selectedShares()),
                        exactMoney(menu.selectedHoldingValue()),
                        exactPercent(menu.selectedAllocationPercent())));
        tooltipWhenHovered(
                graphics, mouseX, mouseY,
                screenX + BankerScreenLayout.MARKET_DETAIL_X,
                screenY + BankerScreenLayout.MARKET_AVERAGE_Y,
                BankerScreenLayout.MARKET_DETAIL_WIDTH,
                BankerScreenLayout.TEXT_HEIGHT,
                tr("tooltip.market_average_exact",
                        exactMoney(menu.selectedAveragePrice())));
        tooltipWhenHovered(
                graphics, mouseX, mouseY,
                screenX + BankerScreenLayout.MARKET_DETAIL_X,
                screenY + BankerScreenLayout.MARKET_BASIS_Y,
                BankerScreenLayout.MARKET_DETAIL_WIDTH,
                BankerScreenLayout.TEXT_HEIGHT,
                tr("tooltip.market_basis_exact", exactMoney(menu.totalCostBasis())));

        Component status = statusText(menu.statusCode());
        if (menu.catchUpDays() == 0
                && !menu.hasPendingTransaction()
                && (status == null || statusDisplayTicks <= 0)) {
            tooltipWhenHovered(
                    graphics, mouseX, mouseY,
                    screenX + BankerScreenLayout.MARKET_BULLETIN_X,
                    screenY + BankerScreenLayout.FOOTER_Y,
                    BankerScreenLayout.MARKET_BULLETIN_WIDTH,
                    BankerScreenLayout.TEXT_HEIGHT,
                    marketBulletin());
        }
    }

    private void tooltipWhenHovered(
            GuiGraphicsExtractor graphics,
            int mouseX,
            int mouseY,
            int x,
            int y,
            int width,
            int height,
            Component tooltip) {
        int logicalHeight = height == BankerScreenLayout.TEXT_HEIGHT
                ? BankerScreenScale.logicalSpanForNativePixels(height, interfaceScale)
                : height;
        if (mouseX >= x && mouseX < x + width
                && mouseY >= y && mouseY < y + logicalHeight) {
            graphics.setTooltipForNextFrame(
                    font.split(tooltip, BankerScreenLayout.TOOLTIP_WRAP_WIDTH),
                    tooltipMouseX,
                    tooltipMouseY);
        }
    }

    private void overviewValueTooltip(
            GuiGraphicsExtractor graphics,
            int mouseX,
            int mouseY,
            int screenX,
            int screenY,
            Component label,
            double value,
            int relativeY) {
        tooltipWhenHovered(
                graphics,
                mouseX,
                mouseY,
                screenX + BankerScreenLayout.OVERVIEW_VALUE_X,
                screenY + relativeY,
                BankerScreenLayout.OVERVIEW_VALUE_WIDTH,
                BankerScreenLayout.TEXT_HEIGHT,
                tr("tooltip.overview_value", label, exactMoney(value)));
    }

    private Component selectedAmountDisplay() {
        if (tab == BankerMenu.TAB_FUND) {
            return Component.literal(menu.donationDraft() + " E");
        }
        int preset = selectedAmountPreset();
        return preset < 0
                ? tr("amount.all_available")
                : Component.literal(preset + " E");
    }

    private Component amountFooterLabel() {
        if (amountDraftDirty) {
            int amount = BankerAmountSelection.parseAppliedAmount(amountDraft);
            return amount > 0
                    ? tr("label.amount_pending", Component.literal(amount + " E"))
                    : tr("label.amount_invalid");
        }
        if (tab == BankerMenu.TAB_FUND) {
            return tr("fund.draft", menu.donationDraft());
        }
        return tr("label.selected_amount", selectedAmountDisplay());
    }

    private int selectedAmountPreset() {
        return menu.selectedRequestedAmount();
    }

    private int selectedWholeAmount(double available) {
        return (int) Math.min(
                EconomyService.MAX_WHOLE_EMERALD_TRANSACTION,
                BankerScreenLayout.resolvedWholeAmount(selectedAmountPreset(), available));
    }

    private int selectedInventoryAmount(double available) {
        return Math.min(
                EconomyService.MAX_INVENTORY_ITEM_TRANSACTION,
                BankerScreenLayout.resolvedWholeAmount(selectedAmountPreset(), available));
    }

    private Component depositPreview() {
        int amount = selectedInventoryAmount(menu.physicalEmeralds());
        return tr("tooltip.flow.deposit", amount,
                menu.physicalEmeralds() - amount, exactMoney(menu.cash() + amount));
    }

    private Component withdrawalPreview() {
        int amount = selectedInventoryAmount(menu.cash());
        return tr("tooltip.flow.withdraw", amount,
                exactMoney(Math.max(0.0, menu.cash() - amount)),
                menu.physicalEmeralds() + amount);
    }

    private Component cashToSavingsPreview() {
        int amount = selectedWholeAmount(menu.cash());
        return tr("tooltip.flow.cash_to_savings", exactMoney(amount),
                exactMoney(Math.max(0.0, menu.cash() - amount)),
                exactMoney(menu.savings() + amount));
    }

    private Component savingsToCashPreview() {
        int amount = selectedWholeAmount(menu.savings());
        return tr("tooltip.flow.savings_to_cash", exactMoney(amount),
                exactMoney(Math.max(0.0, menu.savings() - amount)),
                exactMoney(menu.cash() + amount));
    }

    private Component marketBuySummary() {
        int amount = selectedWholeAmount(menu.cash());
        return tr("market.buy_summary", money(amount),
                money(Math.max(0.0, menu.cash() - amount)));
    }

    private Component marketBuyPreview() {
        int amount = selectedWholeAmount(menu.cash());
        double executionPrice = menu.selectedAssetPrice()
                * (1.0 + EconomyEngine.TRADE_SPREAD);
        double estimatedShares = executionPrice <= 0.0
                ? 0.0 : amount / executionPrice;
        return tr("tooltip.flow.buy", exactMoney(amount), menu.selectedAsset().ticker(),
                exactShares(estimatedShares),
                exactMoney(Math.max(0.0, menu.cash() - amount)));
    }

    private Component marketSalePreview(double fraction) {
        double clampedFraction = Math.max(0.0, Math.min(1.0, fraction));
        double shares = menu.selectedShares() * clampedFraction;
        double proceeds = menu.selectedHoldingValue()
                * clampedFraction
                * (1.0 - EconomyEngine.TRADE_SPREAD);
        return tr("tooltip.flow.sell", exactShares(shares), menu.selectedAsset().ticker(),
                exactMoney(proceeds), exactMoney(menu.cash() + proceeds));
    }

    private Component openCdPreview() {
        int amount = selectedWholeAmount(menu.cash());
        return tr("tooltip.flow.open_cd", exactMoney(amount), menu.selectedCdTerm(),
                exactMoney(Math.max(0.0, menu.cash() - amount)),
                exactMoney(menu.cdValue() + amount));
    }

    private Component closeCdFlowPreview() {
        if (!menu.hasCd()) {
            return tr("tooltip.flow.no_cd_selected");
        }
        if (menu.cdDaysRemaining() > 0) {
            return tr("tooltip.flow.close_cd_early", exactMoney(menu.cash()));
        }
        return tr("tooltip.flow.close_cd_mature", exactMoney(menu.selectedCdPositionValue()),
                exactMoney(menu.cash() + menu.selectedCdPositionValue()));
    }

    private Component fundLoanPreview() {
        int amount = selectedWholeAmount(menu.cash());
        return tr("tooltip.flow.fund_loan", exactMoney(amount), menu.selectedLendingTerm(),
                exactMoney(Math.max(0.0, menu.cash() - amount)),
                exactMoney(menu.lendingValue() + amount));
    }

    private Component collectLoanPreview() {
        if (!menu.hasLending()) {
            return tr("tooltip.flow.no_loan_selected");
        }
        if (!menu.lendingResolved() || menu.lendingDaysRemaining() > 0) {
            return tr("tooltip.flow.loan_not_ready", menu.lendingDaysRemaining());
        }
        return tr("tooltip.flow.collect_loan", exactMoney(menu.selectedLendingPositionValue()),
                exactMoney(menu.cash() + menu.selectedLendingPositionValue()));
    }

    private Component exchangeSummary() {
        int amount = selectedInventoryAmount(menu.selectedResourceCount());
        return tr("exchange.flow_summary", amount,
                tr("resource." + menu.selectedResourceName()),
                money(amount * menu.selectedResourceUnitQuote()));
    }

    private Component exchangePreview() {
        int amount = selectedInventoryAmount(menu.selectedResourceCount());
        double proceeds = amount * menu.selectedResourceUnitQuote();
        return tr("tooltip.flow.exchange", amount,
                tr("resource." + menu.selectedResourceName()), exactMoney(proceeds),
                menu.selectedResourceCount() - amount, exactMoney(menu.cash() + proceeds));
    }

    private Component fundContributionPreview() {
        return tr("tooltip.flow.fund_gift", exactMoney(menu.donationDraft()),
                exactMoney(Math.max(0.0, menu.cash() - menu.donationDraft())));
    }

    private void labelValue(
            GuiGraphicsExtractor graphics,
            Component label,
            String value,
            int x,
            int y,
            int valueColor) {
        drawNativeText(graphics, label, x, y, MUTED, false);
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
        drawNativeText(
                graphics,
                fit(text, BankerScreenScale.scaled(maximumWidth, interfaceScale)),
                x,
                y,
                color,
                shadow);
    }

    /**
     * Keeps custom labels at Minecraft's native font size while their anchors and
     * available space follow the responsive dashboard transform. Vanilla widgets
     * already draw their text at this size, so this prevents labels and buttons from
     * drifting to different apparent font scales without changing widget hitboxes.
     */
    private void drawNativeText(
            GuiGraphicsExtractor graphics,
            Component text,
            int x,
            int y,
            int color,
            boolean shadow) {
        if (Math.abs(interfaceScale - 1.0F) < 0.001F) {
            graphics.text(font, text, x, y, color, shadow);
            return;
        }
        float inverseScale = 1.0F / interfaceScale;
        graphics.pose().pushMatrix();
        graphics.pose().translate(x, y);
        graphics.pose().scale(inverseScale, inverseScale);
        graphics.pose().translate(-x, -y);
        graphics.text(font, text, x, y, color, shadow);
        graphics.pose().popMatrix();
    }

    private void drawNativeCenteredText(
            GuiGraphicsExtractor graphics,
            Component text,
            int centerX,
            int y,
            int color) {
        if (Math.abs(interfaceScale - 1.0F) < 0.001F) {
            graphics.centeredText(font, text, centerX, y, color);
            return;
        }
        float inverseScale = 1.0F / interfaceScale;
        graphics.pose().pushMatrix();
        graphics.pose().translate(centerX, y);
        graphics.pose().scale(inverseScale, inverseScale);
        graphics.pose().translate(-centerX, -y);
        graphics.centeredText(font, text, centerX, y, color);
        graphics.pose().popMatrix();
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

    private static String exactMoney(double value) {
        return String.format(Locale.ROOT, "%,.2f E", Double.isFinite(value) ? value : 0.0);
    }

    private static String exactSignedMoney(double value) {
        return String.format(Locale.ROOT, "%+,.2f E", Double.isFinite(value) ? value : 0.0);
    }

    private static String exactShares(double value) {
        double safe = Double.isFinite(value) && Math.abs(value) >= 0.0000005 ? value : 0.0;
        return String.format(Locale.ROOT, "%,.6f", safe);
    }

    private static String exactPercent(double value) {
        double safe = Double.isFinite(value) && Math.abs(value) >= 0.005 ? value : 0.0;
        return String.format(Locale.ROOT, "%.2f%%", safe);
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

    private static Component activityKindLabel(BankerMenu.ActivityKind kind) {
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

    private record AmountActionButton(Button button, boolean available) {
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
