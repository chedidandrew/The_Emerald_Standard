package com.chedidandrew.emeraldstandard.client;

import com.chedidandrew.emeraldstandard.core.EconomyEngine;
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
    private static final int WIDTH = 320;
    private static final int HEIGHT = 250;

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
    private int confirmationAction = -1;
    private int confirmationExpiresAt;
    private int screenTicks;

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
        int x = leftPos + 10;
        int y = topPos + 27;
        String[] tabs = {"Overview", "Market", "Banking", "Exchange"};
        for (int index = 0; index < tabs.length; index++) {
            int selectedTab = index;
            addRenderableWidget(Button.builder(
                            Component.literal((tab == index ? "[" : "")
                                    + tabs[index]
                                    + (tab == index ? "]" : "")),
                            button -> {
                                tab = selectedTab;
                                rebuildWidgets();
                            })
                    .bounds(x + index * 75, y, 71, 18)
                    .build());
        }

        addAmountButtons();
        switch (tab) {
            case BankerMenu.TAB_OVERVIEW -> addOverviewButtons();
            case BankerMenu.TAB_MARKET -> addMarketButtons();
            case BankerMenu.TAB_BANKING -> addBankingButtons();
            case BankerMenu.TAB_EXCHANGE -> addExchangeButtons();
            default -> {
            }
        }
    }

    private void addAmountButtons() {
        int x = leftPos + 12;
        int y = topPos + 219;
        for (int index = 0; index < BankerMenu.AMOUNT_PRESETS.length; index++) {
            int selected = index;
            int value = BankerMenu.AMOUNT_PRESETS[index];
            String label = value < 0 ? "All" : Integer.toString(value);
            if (menu.selectedAmountPresetIndex() == index) {
                label = "[" + label + "]";
            }
            addRenderableWidget(Button.builder(
                            Component.literal(label),
                            button -> selectAndRefresh(BankerMenu.BUTTON_AMOUNT_BASE + selected))
                    .bounds(x + index * 49, y, 45, 18)
                    .build());
        }
    }

    private void addOverviewButtons() {
        int y = topPos + 169;
        addActionButton("Deposit", leftPos + 12, y, 68, BankerMenu.ACTION_DEPOSIT);
        addActionButton("Withdraw", leftPos + 84, y, 68, BankerMenu.ACTION_WITHDRAW);
        addActionButton("Save", leftPos + 156, y, 68, BankerMenu.ACTION_SAVINGS_DEPOSIT);
        addActionButton("Recover", leftPos + 228, y, 80, BankerMenu.ACTION_RECOVER);
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
        int y = topPos + 169;
        addActionButton("Buy", leftPos + 112, y, 58, BankerMenu.ACTION_BUY);
        addActionButton("Sell 25%", leftPos + 174, y, 62, BankerMenu.ACTION_SELL_QUARTER);
        addConfirmingActionButton(
                confirmationAction == BankerMenu.ACTION_SELL_ALL ? "Confirm" : "Sell All",
                leftPos + 240,
                y,
                68,
                BankerMenu.ACTION_SELL_ALL,
                "Sells the entire selected holding at the current bid price.");
    }

    private void addBankingButtons() {
        int y = topPos + 103;
        addActionButton("To Savings", leftPos + 12, y, 76, BankerMenu.ACTION_SAVINGS_DEPOSIT);
        addActionButton("From Savings", leftPos + 92, y, 86, BankerMenu.ACTION_SAVINGS_WITHDRAW);
        int cdAction = menu.hasCd() ? BankerMenu.ACTION_CLOSE_CD : BankerMenu.ACTION_OPEN_CD;
        String cdActionLabel = confirmationAction == cdAction
                ? "Confirm"
                : menu.hasCd() ? "Close CD" : "Open CD";
        if (menu.hasCd() && menu.cdDaysRemaining() > 0) {
            addConfirmingActionButton(
                    cdActionLabel,
                    leftPos + 182,
                    y,
                    60,
                    cdAction,
                    "Early closure returns principal minus a 1% penalty and forfeits accrued interest.");
        } else {
            addActionButton(cdActionLabel, leftPos + 182, y, 60, cdAction);
        }

        int lendingAction = menu.hasLending()
                ? BankerMenu.ACTION_COLLECT_LENDING
                : BankerMenu.ACTION_FUND_LENDING;
        String lendingActionLabel = confirmationAction == lendingAction
                ? "Confirm"
                : menu.hasLending() ? "Collect" : "Fund";
        if (!menu.hasLending()) {
            addConfirmingActionButton(
                    lendingActionLabel,
                    leftPos + 246,
                    y,
                    62,
                    lendingAction,
                    "Villager lending can lose principal, but it can never create player debt.");
        } else {
            addActionButton(lendingActionLabel, leftPos + 246, y, 62, lendingAction);
        }

        int termY = topPos + 143;
        for (int index = 0; index < BankerMenu.TERMS.length; index++) {
            int selected = index;
            String cdLabel = menu.selectedCdTerm() == BankerMenu.TERMS[index]
                    ? "[" + BankerMenu.TERMS[index] + "]"
                    : Integer.toString(BankerMenu.TERMS[index]);
            addRenderableWidget(Button.builder(
                            Component.literal(cdLabel),
                            button -> selectAndRefresh(BankerMenu.BUTTON_CD_TERM_BASE + selected))
                    .bounds(leftPos + 78 + index * 39, termY, 35, 18)
                    .tooltip(Tooltip.create(Component.literal(String.format(
                            Locale.ROOT,
                            "%d-day CD · %.2f%% locked annual rate · 1%% early-close penalty",
                            BankerMenu.TERMS[index],
                            EconomyEngine.cdAnnualRate(
                                    menu.regime(), BankerMenu.TERMS[index]) * 100.0))))
                    .build());

            String lendingLabel = menu.selectedLendingTerm() == BankerMenu.TERMS[index]
                    ? "[" + BankerMenu.TERMS[index] + "]"
                    : Integer.toString(BankerMenu.TERMS[index]);
            addRenderableWidget(Button.builder(
                            Component.literal(lendingLabel),
                            button -> selectAndRefresh(
                                    BankerMenu.BUTTON_LENDING_TERM_BASE + selected))
                    .bounds(leftPos + 78 + index * 39, termY + 23, 35, 18)
                    .tooltip(Tooltip.create(Component.literal(String.format(
                            Locale.ROOT,
                            "%d-day villager loan · %.2f%% yield · about %.1f%% opening default risk",
                            BankerMenu.TERMS[index],
                            EconomyEngine.villagerLoanAnnualYield(
                                    menu.regime(), BankerMenu.TERMS[index]) * 100.0,
                            EconomyEngine.estimatedLoanDefaultProbability(
                                    menu.regime(), BankerMenu.TERMS[index]) * 100.0))))
                    .build());
        }
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
        addActionButton("Exchange", leftPos + 112, topPos + 137, 96,
                BankerMenu.ACTION_EXCHANGE);
    }

    private void addActionButton(String label, int x, int y, int width, int id) {
        addRenderableWidget(Button.builder(
                        Component.literal(label),
                        button -> sendMenuButton(id))
                .bounds(x, y, width, 18)
                .build());
    }

    private void addConfirmingActionButton(
            String label, int x, int y, int width, int id, String explanation) {
        addRenderableWidget(Button.builder(
                        Component.literal(label),
                        button -> confirmOrSend(id))
                .bounds(x, y, width, 18)
                .tooltip(Tooltip.create(Component.literal(explanation)))
                .build());
    }

    private void confirmOrSend(int id) {
        if (confirmationAction == id && screenTicks <= confirmationExpiresAt) {
            confirmationAction = -1;
            sendMenuButton(id);
            return;
        }
        confirmationAction = id;
        confirmationExpiresAt = screenTicks + 100;
        rebuildWidgets();
    }

    private void selectAndRefresh(int id) {
        confirmationAction = -1;
        sendMenuButton(id);
        rebuildWidgets();
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        screenTicks++;
        if (confirmationAction >= 0 && screenTicks > confirmationExpiresAt) {
            confirmationAction = -1;
            rebuildWidgets();
        }
        int revision = menu.statusRevision();
        if (revision != seenStatusRevision) {
            seenStatusRevision = revision;
            confirmationAction = -1;
            rebuildWidgets();
        }
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
        graphics.fill(x + 8, y + 48, x + imageWidth - 8, y + 210, PANEL_LIGHT);

        if (tab == BankerMenu.TAB_OVERVIEW || tab == BankerMenu.TAB_MARKET) {
            drawChart(graphics, x + 111, y + 57, 196, 88, mouseX, mouseY);
        } else if (tab == BankerMenu.TAB_BANKING) {
            graphics.outline(x + 10, y + 54, 145, 42, 0xFF456B5A);
            graphics.outline(x + 165, y + 54, 143, 42, 0xFF456B5A);
        } else if (tab == BankerMenu.TAB_EXCHANGE) {
            graphics.outline(x + 52, y + 62, 216, 47, 0xFF456B5A);
            graphics.fill(x + 60, y + 118, x + 260, y + 119, 0xFF456B5A);
        }
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        graphics.text(font, title, 12, 10, GOLD, false);
        graphics.text(font,
                "Day " + menu.economicDay() + "  |  " + friendly(menu.regime().name()),
                182,
                11,
                MUTED,
                false);

        switch (tab) {
            case BankerMenu.TAB_OVERVIEW -> drawOverviewLabels(graphics);
            case BankerMenu.TAB_MARKET -> drawMarketLabels(graphics);
            case BankerMenu.TAB_BANKING -> drawBankingLabels(graphics);
            case BankerMenu.TAB_EXCHANGE -> drawExchangeLabels(graphics);
            default -> {
            }
        }

        graphics.text(font, "Amount", 12, 207, MUTED, false);
        String status = statusText(menu.statusCode());
        int statusColor = menu.statusCode() < 0 ? NEGATIVE : POSITIVE;
        if (!status.isBlank()) {
            graphics.text(font, status, 12, 198, statusColor, false);
        }
        if (menu.catchUpDays() > 0) {
            graphics.text(font,
                    "Market catch-up: " + menu.catchUpDays() + " day(s). Actions are paused.",
                    12,
                    187,
                    GOLD,
                    false);
        } else if (menu.hasPendingTransaction()) {
            graphics.text(font,
                    "A transaction is awaiting recovery.",
                    12,
                    187,
                    GOLD,
                    false);
        }
    }

    private void drawOverviewLabels(GuiGraphicsExtractor graphics) {
        graphics.text(font, "Your village finances", 14, 56, TEXT, false);
        labelValue(graphics, "Net worth", money(menu.netWorth()), 14, 72, GOLD);
        labelValue(graphics, "Bank cash", money(menu.cash()), 14, 88, TEXT);
        labelValue(graphics, "Savings", money(menu.savings()), 14, 104, TEXT);
        labelValue(graphics, "Invested", money(investedValue()), 14, 120, TEXT);
        graphics.text(font,
                "Inventory: " + menu.physicalEmeralds() + " emerald(s)",
                14,
                136,
                MUTED,
                false);

        EconomyEngine.Asset selected = menu.selectedAsset();
        graphics.text(font, selected.ticker() + "  " + selected.name(), 118, 56, TEXT, false);
        graphics.text(font,
                money(menu.selectedAssetPrice()) + "  |  " + signed(menu.selectedChangePercent()),
                118,
                147,
                menu.selectedChangePercent() >= 0.0 ? POSITIVE : NEGATIVE,
                false);
        graphics.text(font,
                marketBulletin(),
                14,
                157,
                MUTED,
                false);
    }

    private void drawMarketLabels(GuiGraphicsExtractor graphics) {
        EconomyEngine.Asset selected = menu.selectedAsset();
        graphics.text(font, "Choose an investment", 14, 56, TEXT, false);
        graphics.text(font, selected.ticker(), 14, 126, GOLD, false);
        graphics.text(font, selected.sector(), 14, 138, TEXT, false);
        graphics.text(font, riskLabel(selected), 14, 150, MUTED, false);

        graphics.text(font, selected.name(), 118, 56, TEXT, false);
        graphics.text(font,
                money(menu.selectedAssetPrice()) + "  |  " + signed(menu.selectedChangePercent()),
                118,
                147,
                menu.selectedChangePercent() >= 0.0 ? POSITIVE : NEGATIVE,
                false);
        graphics.text(font,
                String.format(Locale.ROOT,
                        "Holding %.4f shares (%s)",
                        menu.selectedShares(),
                        money(menu.selectedHoldingValue())),
                118,
                135,
                MUTED,
                false);
    }

    private void drawBankingLabels(GuiGraphicsExtractor graphics) {
        graphics.text(font, "Savings", 16, 59, GOLD, false);
        graphics.text(font, money(menu.savings()), 16, 73, TEXT, false);
        graphics.text(font,
                String.format(Locale.ROOT, "Current rate %.2f%%", menu.savingsRate()),
                16,
                85,
                MUTED,
                false);

        graphics.text(font, "Certificates and villager lending", 171, 59, GOLD, false);
        String cd = menu.hasCd()
                ? money(menu.cdValue()) + " at " + String.format(Locale.ROOT, "%.2f%%", menu.cdRate())
                : "No active CD";
        graphics.text(font, cd, 171, 73, TEXT, false);
        String lending = menu.hasLending()
                ? money(menu.lendingValue()) + " at "
                        + String.format(Locale.ROOT, "%.2f%%", menu.lendingRate())
                : "No active lending";
        graphics.text(font, lending, 171, 85, TEXT, false);

        graphics.text(font, "CD term", 14, 148, MUTED, false);
        graphics.text(font, "Lending term", 14, 171, MUTED, false);
        if (menu.hasCd()) {
            graphics.text(font,
                    menu.cdDaysRemaining() + " CD day(s) remaining",
                    184,
                    128,
                    MUTED,
                    false);
        }
        if (menu.hasLending()) {
            String detail = menu.lendingResolved()
                    ? friendly(menu.lendingOutcome().name())
                    : menu.lendingDaysRemaining() + " lending day(s) remaining";
            graphics.text(font, detail, 184, 140, MUTED, false);
        }
        graphics.text(font,
                "Lending can lose principal. You can never owe emeralds.",
                14,
                128,
                MUTED,
                false);
    }

    private void drawExchangeLabels(GuiGraphicsExtractor graphics) {
        String resource = friendly(menu.selectedResourceName());
        graphics.centeredText(font, resource, 160, 69, GOLD);
        graphics.centeredText(font,
                "Owned: " + menu.selectedResourceCount(),
                160,
                83,
                TEXT);
        graphics.centeredText(font,
                String.format(Locale.ROOT,
                        "Bank quote: %.2f emeralds each",
                        menu.selectedResourceUnitQuote()),
                160,
                96,
                MUTED);
        graphics.centeredText(font,
                "Choose an amount below, then exchange it into bank cash.",
                160,
                121,
                MUTED);
        graphics.centeredText(font,
                "Prices move with the villager commodity market.",
                160,
                159,
                MUTED);
    }

    private void drawChart(
            GuiGraphicsExtractor graphics,
            int x,
            int y,
            int width,
            int height,
            int mouseX,
            int mouseY) {
        graphics.fill(x, y, x + width, y + height, PANEL_DARK);
        graphics.outline(x, y, width, height, 0xFF456B5A);
        int[] points = menu.historyPointsCenti();
        if (points.length < 2) {
            graphics.centeredText(font, "History builds as the economy advances", x + width / 2,
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
        graphics.text(font, "up to 180d", x + width - 58, y + height - 12, MUTED, false);
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
            int daysAgo = points.length - 1 - index;
            graphics.setTooltipForNextFrame(
                    font,
                    Component.literal(money(points[index] / 100.0)
                            + (daysAgo == 0 ? " today" : " · " + daysAgo + " sampled point(s) ago")),
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
            String label,
            String value,
            int x,
            int y,
            int valueColor) {
        graphics.text(font, label, x, y, MUTED, false);
        graphics.text(font, value, x + 63, y, valueColor, false);
    }

    private double investedValue() {
        double holdings = 0.0;
        for (int index = 0; index < EconomyEngine.ASSETS.size(); index++) {
            holdings += menu.assetHoldingValue(index);
        }
        return holdings + menu.cdValue() + menu.lendingValue();
    }

    private static String money(double value) {
        return String.format(Locale.ROOT, "%.2f E", value);
    }

    private static String signed(double value) {
        return String.format(Locale.ROOT, "%+.2f%%", value);
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

    private static String riskLabel(EconomyEngine.Asset asset) {
        double risk = asset.beta() + asset.annualIdiosyncraticVolatility() * 2.0;
        if (risk < 1.0) return "Lower risk";
        if (risk < 1.45) return "Moderate risk";
        if (risk < 1.75) return "High risk";
        return "Very high risk";
    }

    private static String regimeBulletin(EconomyEngine.Regime regime) {
        return switch (regime) {
            case EXPANSION -> "Village commerce is expanding.";
            case BULL -> "Broad markets continue to climb.";
            case BOOM -> "Rapid growth is lifting risk.";
            case STAGNATION -> "Trade is moving sideways.";
            case RECESSION -> "Overworld demand has weakened.";
            case CRASH -> "Heavy selling grips the exchange.";
            case RECOVERY -> "Markets are rebuilding.";
        };
    }

    private String marketBulletin() {
        EconomyEngine.MarketEvent event = menu.lastMarketEvent();
        if (event != EconomyEngine.MarketEvent.NONE && menu.marketEventAge() <= 180) {
            return "News: " + event.title() + " (" + menu.marketEventAge() + "d ago)";
        }
        return "News: " + regimeBulletin(menu.regime());
    }

    private static String statusText(int status) {
        return switch (status) {
            case 1 -> "Emeralds deposited into bank cash.";
            case 2 -> "Emeralds withdrawn to your inventory.";
            case 3 -> "Moved bank cash into savings.";
            case 4 -> "Moved savings into bank cash.";
            case 5 -> "Investment purchased.";
            case 6 -> "Investment sold.";
            case 7 -> "Certificate of deposit opened.";
            case 8 -> "Certificate of deposit closed.";
            case 9 -> "Villager business lending funded.";
            case 10 -> "Villager lending proceeds collected.";
            case 11 -> "Resources exchanged into bank cash.";
            case 12 -> "Pending transaction recovered.";
            case 13 -> "Action completed. Recovery will finish after player data saves.";
            case -1 -> "The economy is busy or still catching up.";
            case -2 -> "Not enough emeralds, bank cash, shares, or resources.";
            case -3 -> "Your inventory has no room.";
            case -4 -> "Only one active product of this type is allowed in this alpha.";
            case -5 -> "That product is not ready to collect or close.";
            case -6 -> "The action could not be saved safely.";
            case -7 -> "That option is not supported.";
            default -> "";
        };
    }
}
