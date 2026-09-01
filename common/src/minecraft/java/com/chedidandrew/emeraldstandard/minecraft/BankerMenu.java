package com.chedidandrew.emeraldstandard.minecraft;

import com.chedidandrew.emeraldstandard.core.EconomyEngine;
import com.chedidandrew.emeraldstandard.core.EconomyService;
import com.chedidandrew.emeraldstandard.core.EconomyState;
import java.util.List;
import java.util.Map;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Server-authoritative menu backing the casual Banker interface. */
public final class BankerMenu extends AbstractContainerMenu {
    public static final int TAB_OVERVIEW = 0;
    public static final int TAB_MARKET = 1;
    public static final int TAB_BANKING = 2;
    public static final int TAB_EXCHANGE = 3;

    public static final int BUTTON_ASSET_BASE = 10;
    public static final int BUTTON_RESOURCE_BASE = 30;
    public static final int BUTTON_AMOUNT_BASE = 60;
    public static final int BUTTON_CD_TERM_BASE = 70;
    public static final int BUTTON_LENDING_TERM_BASE = 80;

    public static final int ACTION_DEPOSIT = 100;
    public static final int ACTION_WITHDRAW = 101;
    public static final int ACTION_SAVINGS_DEPOSIT = 102;
    public static final int ACTION_SAVINGS_WITHDRAW = 103;
    public static final int ACTION_BUY = 104;
    public static final int ACTION_SELL_QUARTER = 105;
    public static final int ACTION_SELL_ALL = 106;
    public static final int ACTION_OPEN_CD = 107;
    public static final int ACTION_CLOSE_CD = 108;
    public static final int ACTION_FUND_LENDING = 109;
    public static final int ACTION_COLLECT_LENDING = 110;
    public static final int ACTION_EXCHANGE = 111;
    public static final int ACTION_RECOVER = 112;

    public static final int[] AMOUNT_PRESETS = {1, 5, 10, 32, 64, -1};
    public static final int[] TERMS = {30, 90, 180, 365};
    public static final List<String> RESOURCE_NAMES = BankInventory.exchangeResourceNames();
    public static final int HISTORY_POINTS = 60;

    private static final int DATA_DAY = 0;
    private static final int DATA_REGIME = 1;
    private static final int DATA_SELECTED_ASSET = 2;
    private static final int DATA_SELECTED_RESOURCE = 3;
    private static final int DATA_AMOUNT_PRESET = 4;
    private static final int DATA_CD_TERM = 5;
    private static final int DATA_LENDING_TERM = 6;
    private static final int DATA_STATUS = 7;
    private static final int DATA_STATUS_REVISION = 8;
    private static final int DATA_CATCH_UP = 9;
    private static final int DATA_PHYSICAL_EMERALDS = 10;
    private static final int DATA_RESOURCE_COUNT = 11;
    private static final int DATA_RESOURCE_UNIT_QUOTE_CENTI = 12;
    private static final int DATA_CASH_LOW = 13;
    private static final int DATA_CASH_HIGH = 14;
    private static final int DATA_SAVINGS_LOW = 15;
    private static final int DATA_SAVINGS_HIGH = 16;
    private static final int DATA_CD_LOW = 17;
    private static final int DATA_CD_HIGH = 18;
    private static final int DATA_LENDING_LOW = 19;
    private static final int DATA_LENDING_HIGH = 20;
    private static final int DATA_NET_WORTH_LOW = 21;
    private static final int DATA_NET_WORTH_HIGH = 22;
    private static final int DATA_CD_DAYS = 23;
    private static final int DATA_LENDING_DAYS = 24;
    private static final int DATA_CD_RATE_BPS = 25;
    private static final int DATA_LENDING_RATE_BPS = 26;
    private static final int DATA_LENDING_OUTCOME = 27;
    private static final int DATA_LENDING_RESOLVED = 28;
    private static final int DATA_PENDING_TRANSACTION = 29;
    private static final int DATA_SELECTED_PRICE_CENTI = 30;
    private static final int DATA_SELECTED_HOLDING_CENTI_LOW = 31;
    private static final int DATA_SELECTED_HOLDING_CENTI_HIGH = 32;
    private static final int DATA_SELECTED_SHARES_MICRO_LOW = 33;
    private static final int DATA_SELECTED_SHARES_MICRO_HIGH = 34;
    private static final int DATA_SAVINGS_RATE_BPS = 35;
    private static final int DATA_SELECTED_CHANGE_BPS = 36;
    private static final int DATA_CD_ACTIVE = 37;
    private static final int DATA_LENDING_ACTIVE = 38;
    private static final int DATA_ASSET_PRICE_BASE = 39;
    private static final int DATA_ASSET_HOLDING_BASE = DATA_ASSET_PRICE_BASE + 9;
    private static final int DATA_HISTORY_COUNT = DATA_ASSET_HOLDING_BASE + 9;
    private static final int DATA_HISTORY_BASE = DATA_HISTORY_COUNT + 1;
    public static final int DATA_COUNT = DATA_HISTORY_BASE + HISTORY_POINTS;

    private final Inventory inventory;
    private final EconomyService economy;
    private final ServerPlayer serverPlayer;
    private final ContainerData data;

    private int selectedAssetIndex;
    private int selectedResourceIndex;
    private int amountPresetIndex = 3;
    private int cdTermIndex = 1;
    private int lendingTermIndex = 2;
    private int statusCode = BankingOperations.READY;
    private int statusRevision;

    private long day;
    private int regimeOrdinal;
    private int catchUpDays;
    private int physicalEmeralds;
    private int selectedResourceCount;
    private int selectedResourceUnitQuoteCenti;
    private long cashMicro;
    private long savingsMicro;
    private long cdValueMicro;
    private long lendingValueMicro;
    private long netWorthCenti;
    private int cdDaysRemaining;
    private int lendingDaysRemaining;
    private int cdRateBps;
    private int lendingRateBps;
    private int lendingOutcome;
    private int lendingResolved;
    private int pendingTransaction;
    private int selectedPriceCenti;
    private long selectedHoldingCenti;
    private long selectedSharesMicro;
    private int savingsRateBps;
    private int selectedChangeBps;
    private int cdActive;
    private int lendingActive;
    private final int[] assetPricesCenti = new int[9];
    private final int[] assetHoldingsCenti = new int[9];
    private final int[] historyCenti = new int[HISTORY_POINTS];
    private int historyCount;

    /** Client-side constructor used by the menu registry. */
    public BankerMenu(int containerId, Inventory inventory) {
        this(containerId, inventory, null, null);
    }

    /** Server-side constructor used when a player interacts with a Banker. */
    public BankerMenu(
            int containerId,
            Inventory inventory,
            EconomyService economy,
            ServerPlayer player) {
        super(BankerMenus.type(), containerId);
        this.inventory = inventory;
        this.economy = economy;
        this.serverPlayer = player;
        this.data = economy == null
                ? new SimpleContainerData(DATA_COUNT)
                : new BankerContainerData(this);
        addDataSlots(this.data);
        refreshServerSnapshot();
    }

    @Override
    public boolean clickMenuButton(Player player, int buttonId) {
        if (buttonId >= BUTTON_ASSET_BASE
                && buttonId < BUTTON_ASSET_BASE + EconomyEngine.ASSETS.size()) {
            selectedAssetIndex = buttonId - BUTTON_ASSET_BASE;
            setClientSelection(DATA_SELECTED_ASSET, selectedAssetIndex);
            refreshServerSnapshot();
            return true;
        }
        if (buttonId >= BUTTON_RESOURCE_BASE
                && buttonId < BUTTON_RESOURCE_BASE + RESOURCE_NAMES.size()) {
            selectedResourceIndex = buttonId - BUTTON_RESOURCE_BASE;
            setClientSelection(DATA_SELECTED_RESOURCE, selectedResourceIndex);
            refreshServerSnapshot();
            return true;
        }
        if (buttonId >= BUTTON_AMOUNT_BASE
                && buttonId < BUTTON_AMOUNT_BASE + AMOUNT_PRESETS.length) {
            amountPresetIndex = buttonId - BUTTON_AMOUNT_BASE;
            setClientSelection(DATA_AMOUNT_PRESET, amountPresetIndex);
            return true;
        }
        if (buttonId >= BUTTON_CD_TERM_BASE
                && buttonId < BUTTON_CD_TERM_BASE + TERMS.length) {
            cdTermIndex = buttonId - BUTTON_CD_TERM_BASE;
            setClientSelection(DATA_CD_TERM, cdTermIndex);
            return true;
        }
        if (buttonId >= BUTTON_LENDING_TERM_BASE
                && buttonId < BUTTON_LENDING_TERM_BASE + TERMS.length) {
            lendingTermIndex = buttonId - BUTTON_LENDING_TERM_BASE;
            setClientSelection(DATA_LENDING_TERM, lendingTermIndex);
            return true;
        }

        if (player.level().isClientSide()) {
            return isAction(buttonId);
        }
        if (serverPlayer == null || economy == null || player != serverPlayer) {
            return false;
        }

        int requested = selectedAmount();
        statusCode = switch (buttonId) {
            case ACTION_DEPOSIT -> BankingOperations.deposit(serverPlayer, economy, requested);
            case ACTION_WITHDRAW -> BankingOperations.withdraw(serverPlayer, economy, requested);
            case ACTION_SAVINGS_DEPOSIT -> BankingOperations.moveSavings(
                    serverPlayer, economy, requested, true);
            case ACTION_SAVINGS_WITHDRAW -> BankingOperations.moveSavings(
                    serverPlayer, economy, requested, false);
            case ACTION_BUY -> BankingOperations.buy(
                    serverPlayer, economy, selectedAsset().ticker(), requested);
            case ACTION_SELL_QUARTER -> BankingOperations.sellFraction(
                    serverPlayer, economy, selectedAsset().ticker(), 0.25);
            case ACTION_SELL_ALL -> BankingOperations.sellFraction(
                    serverPlayer, economy, selectedAsset().ticker(), 1.0);
            case ACTION_OPEN_CD -> BankingOperations.openCd(
                    serverPlayer, economy, requested, selectedCdTerm());
            case ACTION_CLOSE_CD -> BankingOperations.closeCd(serverPlayer, economy);
            case ACTION_FUND_LENDING -> BankingOperations.fundLending(
                    serverPlayer, economy, requested, selectedLendingTerm());
            case ACTION_COLLECT_LENDING -> BankingOperations.collectLending(
                    serverPlayer, economy);
            case ACTION_EXCHANGE -> BankingOperations.exchange(
                    serverPlayer,
                    economy,
                    selectedExchangeResource(),
                    requested);
            case ACTION_RECOVER -> BankingOperations.recover(serverPlayer, economy);
            default -> Integer.MIN_VALUE;
        };
        if (statusCode == Integer.MIN_VALUE) {
            return false;
        }
        statusRevision++;
        refreshServerSnapshot();
        broadcastChanges();
        return true;
    }

    @Override
    public void broadcastChanges() {
        refreshServerSnapshot();
        super.broadcastChanges();
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slot) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.isAlive() && !player.isRemoved();
    }

    public long economicDay() {
        return Integer.toUnsignedLong(data.get(DATA_DAY));
    }

    public EconomyEngine.Regime regime() {
        int ordinal = clampIndex(data.get(DATA_REGIME), EconomyEngine.Regime.values().length);
        return EconomyEngine.Regime.values()[ordinal];
    }

    public int selectedAssetIndex() {
        return clampIndex(data.get(DATA_SELECTED_ASSET), EconomyEngine.ASSETS.size());
    }

    public EconomyEngine.Asset selectedAsset() {
        return EconomyEngine.ASSETS.get(selectedAssetIndex());
    }

    public int selectedResourceIndex() {
        return clampIndex(data.get(DATA_SELECTED_RESOURCE), RESOURCE_NAMES.size());
    }

    public String selectedResourceName() {
        return RESOURCE_NAMES.get(selectedResourceIndex());
    }

    public int selectedAmountPresetIndex() {
        return clampIndex(data.get(DATA_AMOUNT_PRESET), AMOUNT_PRESETS.length);
    }

    public String selectedAmountLabel() {
        int value = AMOUNT_PRESETS[selectedAmountPresetIndex()];
        return value < 0 ? "All" : Integer.toString(value);
    }

    public int selectedCdTerm() {
        return TERMS[clampIndex(data.get(DATA_CD_TERM), TERMS.length)];
    }

    public int selectedLendingTerm() {
        return TERMS[clampIndex(data.get(DATA_LENDING_TERM), TERMS.length)];
    }

    public int statusCode() {
        return data.get(DATA_STATUS);
    }

    public int statusRevision() {
        return data.get(DATA_STATUS_REVISION);
    }

    public int catchUpDays() {
        return Math.max(0, data.get(DATA_CATCH_UP));
    }

    public int physicalEmeralds() {
        return Math.max(0, data.get(DATA_PHYSICAL_EMERALDS));
    }

    public int selectedResourceCount() {
        return Math.max(0, data.get(DATA_RESOURCE_COUNT));
    }

    public double selectedResourceUnitQuote() {
        return Math.max(0, data.get(DATA_RESOURCE_UNIT_QUOTE_CENTI)) / 100.0;
    }

    public double cash() {
        return readLong(DATA_CASH_LOW, DATA_CASH_HIGH) / (double) EconomyState.MICRO;
    }

    public double savings() {
        return readLong(DATA_SAVINGS_LOW, DATA_SAVINGS_HIGH) / (double) EconomyState.MICRO;
    }

    public double cdValue() {
        return readLong(DATA_CD_LOW, DATA_CD_HIGH) / (double) EconomyState.MICRO;
    }

    public double lendingValue() {
        return readLong(DATA_LENDING_LOW, DATA_LENDING_HIGH) / (double) EconomyState.MICRO;
    }

    public double netWorth() {
        return readLong(DATA_NET_WORTH_LOW, DATA_NET_WORTH_HIGH) / 100.0;
    }

    public int cdDaysRemaining() {
        return Math.max(0, data.get(DATA_CD_DAYS));
    }

    public int lendingDaysRemaining() {
        return Math.max(0, data.get(DATA_LENDING_DAYS));
    }

    public double cdRate() {
        return data.get(DATA_CD_RATE_BPS) / 100.0;
    }

    public double lendingRate() {
        return data.get(DATA_LENDING_RATE_BPS) / 100.0;
    }

    public double savingsRate() {
        return data.get(DATA_SAVINGS_RATE_BPS) / 100.0;
    }

    public EconomyEngine.LoanOutcome lendingOutcome() {
        int ordinal = clampIndex(data.get(DATA_LENDING_OUTCOME), EconomyEngine.LoanOutcome.values().length);
        return EconomyEngine.LoanOutcome.values()[ordinal];
    }

    public boolean lendingResolved() {
        return data.get(DATA_LENDING_RESOLVED) != 0;
    }

    public boolean hasPendingTransaction() {
        return data.get(DATA_PENDING_TRANSACTION) != 0;
    }

    public boolean hasCd() {
        return data.get(DATA_CD_ACTIVE) != 0;
    }

    public boolean hasLending() {
        return data.get(DATA_LENDING_ACTIVE) != 0;
    }

    public double selectedAssetPrice() {
        return Math.max(0, data.get(DATA_SELECTED_PRICE_CENTI)) / 100.0;
    }

    public double selectedHoldingValue() {
        return readLong(DATA_SELECTED_HOLDING_CENTI_LOW, DATA_SELECTED_HOLDING_CENTI_HIGH) / 100.0;
    }

    public double selectedShares() {
        return readLong(DATA_SELECTED_SHARES_MICRO_LOW, DATA_SELECTED_SHARES_MICRO_HIGH) / 1_000_000.0;
    }

    public double selectedChangePercent() {
        return data.get(DATA_SELECTED_CHANGE_BPS) / 100.0;
    }

    public double assetPrice(int index) {
        int safe = clampIndex(index, EconomyEngine.ASSETS.size());
        return Math.max(0, data.get(DATA_ASSET_PRICE_BASE + safe)) / 100.0;
    }

    public double assetHoldingValue(int index) {
        int safe = clampIndex(index, EconomyEngine.ASSETS.size());
        return Math.max(0, data.get(DATA_ASSET_HOLDING_BASE + safe)) / 100.0;
    }

    public int[] historyPointsCenti() {
        int count = Math.min(HISTORY_POINTS, Math.max(0, data.get(DATA_HISTORY_COUNT)));
        int[] points = new int[count];
        for (int index = 0; index < count; index++) {
            points[index] = Math.max(0, data.get(DATA_HISTORY_BASE + index));
        }
        return points;
    }

    private int selectedAmount() {
        int preset = AMOUNT_PRESETS[clampIndex(amountPresetIndex, AMOUNT_PRESETS.length)];
        if (preset > 0) {
            return preset;
        }
        return (int) EconomyService.MAX_WHOLE_EMERALD_TRANSACTION;
    }

    private int selectedCdTermServer() {
        return TERMS[clampIndex(cdTermIndex, TERMS.length)];
    }

    private int selectedLendingTermServer() {
        return TERMS[clampIndex(lendingTermIndex, TERMS.length)];
    }

    private BankInventory.ExchangeResource selectedExchangeResource() {
        String name = RESOURCE_NAMES.get(clampIndex(selectedResourceIndex, RESOURCE_NAMES.size()));
        return BankInventory.exchangeResource(name);
    }

    private static boolean isAction(int buttonId) {
        return buttonId >= ACTION_DEPOSIT && buttonId <= ACTION_RECOVER;
    }

    private void setClientSelection(int index, int value) {
        if (economy == null) {
            data.set(index, value);
        }
    }

    private void refreshServerSnapshot() {
        if (economy == null || serverPlayer == null) {
            return;
        }
        EconomyService.MarketSnapshot market = economy.marketSnapshot();
        EconomyService.PortfolioSnapshot portfolio =
                economy.portfolioSnapshot(serverPlayer.getUUID());
        if (market == null || portfolio == null) {
            return;
        }

        selectedAssetIndex = clampIndex(selectedAssetIndex, EconomyEngine.ASSETS.size());
        selectedResourceIndex = clampIndex(selectedResourceIndex, RESOURCE_NAMES.size());
        amountPresetIndex = clampIndex(amountPresetIndex, AMOUNT_PRESETS.length);
        cdTermIndex = clampIndex(cdTermIndex, TERMS.length);
        lendingTermIndex = clampIndex(lendingTermIndex, TERMS.length);

        EconomyState.Account account = portfolio.account();
        day = market.economicDay();
        regimeOrdinal = market.regime().ordinal();
        catchUpDays = saturatingInt(market.catchUpDaysRemaining());
        physicalEmeralds = BankInventory.countItems(serverPlayer, Items.EMERALD);
        cashMicro = account.cashMicro;
        savingsMicro = account.savingsMicro;
        cdValueMicro = account.cdValueMicro;
        lendingValueMicro = account.loanValueMicro;
        netWorthCenti = centi(portfolio.netWorth());
        cdDaysRemaining = account.hasCd()
                ? saturatingInt(Math.max(0L, account.cdMaturityDay - day))
                : 0;
        lendingDaysRemaining = account.hasLoan()
                ? saturatingInt(Math.max(0L, account.loanMaturityDay - day))
                : 0;
        cdRateBps = basisPoints(account.cdAnnualRate);
        lendingRateBps = basisPoints(account.loanAnnualRate);
        savingsRateBps = basisPoints(EconomyEngine.savingsAnnualRate(market.regime()));
        lendingOutcome = account.loanOutcome.ordinal();
        lendingResolved = account.loanResolved ? 1 : 0;
        pendingTransaction = portfolio.pendingTransaction() == null ? 0 : 1;
        cdActive = account.hasCd() ? 1 : 0;
        lendingActive = account.hasLoan() ? 1 : 0;

        for (int index = 0; index < EconomyEngine.ASSETS.size(); index++) {
            String ticker = EconomyEngine.ASSETS.get(index).ticker();
            double price = market.prices().getOrDefault(ticker, 0.0);
            assetPricesCenti[index] = centiInt(price);
            double shares = account.shares.getOrDefault(ticker, 0.0);
            assetHoldingsCenti[index] = centiInt(shares * price);
        }

        EconomyEngine.Asset selected = EconomyEngine.ASSETS.get(selectedAssetIndex);
        double selectedPrice = market.prices().getOrDefault(selected.ticker(), 0.0);
        double shares = account.shares.getOrDefault(selected.ticker(), 0.0);
        selectedPriceCenti = centiInt(selectedPrice);
        selectedHoldingCenti = centi(shares * selectedPrice);
        selectedSharesMicro = safeScaledLong(shares, 1_000_000.0);

        List<Double> history = market.priceHistory().getOrDefault(selected.ticker(), List.of());
        historyCount = Math.min(HISTORY_POINTS, history.size());
        java.util.Arrays.fill(historyCenti, 0);
        if (historyCount > 0) {
            int start = Math.max(0, history.size() - EconomyState.HISTORY_DAYS);
            int available = history.size() - start;
            for (int index = 0; index < historyCount; index++) {
                int source = historyCount == 1
                        ? history.size() - 1
                        : start + (int) Math.round(index * (available - 1.0) / (historyCount - 1.0));
                historyCenti[index] = centiInt(history.get(source));
            }
            double first = historyCenti[0] / 100.0;
            double last = historyCenti[historyCount - 1] / 100.0;
            selectedChangeBps = first > 0.0
                    ? saturatingInt(Math.round((last / first - 1.0) * 10_000.0))
                    : 0;
        } else {
            selectedChangeBps = 0;
        }

        BankInventory.ExchangeResource resource = selectedExchangeResource();
        selectedResourceCount = resource == null
                ? 0
                : BankInventory.countItems(serverPlayer, resource.item());
        long unitQuoteMicro = resource == null
                ? 0L
                : economy.quoteResourceValueMicro(resource.quoteId(), 1);
        selectedResourceUnitQuoteCenti = unitQuoteMicro <= 0L
                ? 0
                : saturatingInt(Math.round(unitQuoteMicro / 10_000.0));
    }

    private int dataValue(int index) {
        if (index == DATA_DAY) return (int) Math.min(Integer.MAX_VALUE, day);
        if (index == DATA_REGIME) return regimeOrdinal;
        if (index == DATA_SELECTED_ASSET) return selectedAssetIndex;
        if (index == DATA_SELECTED_RESOURCE) return selectedResourceIndex;
        if (index == DATA_AMOUNT_PRESET) return amountPresetIndex;
        if (index == DATA_CD_TERM) return cdTermIndex;
        if (index == DATA_LENDING_TERM) return lendingTermIndex;
        if (index == DATA_STATUS) return statusCode;
        if (index == DATA_STATUS_REVISION) return statusRevision;
        if (index == DATA_CATCH_UP) return catchUpDays;
        if (index == DATA_PHYSICAL_EMERALDS) return physicalEmeralds;
        if (index == DATA_RESOURCE_COUNT) return selectedResourceCount;
        if (index == DATA_RESOURCE_UNIT_QUOTE_CENTI) return selectedResourceUnitQuoteCenti;
        if (index == DATA_CASH_LOW) return low(cashMicro);
        if (index == DATA_CASH_HIGH) return high(cashMicro);
        if (index == DATA_SAVINGS_LOW) return low(savingsMicro);
        if (index == DATA_SAVINGS_HIGH) return high(savingsMicro);
        if (index == DATA_CD_LOW) return low(cdValueMicro);
        if (index == DATA_CD_HIGH) return high(cdValueMicro);
        if (index == DATA_LENDING_LOW) return low(lendingValueMicro);
        if (index == DATA_LENDING_HIGH) return high(lendingValueMicro);
        if (index == DATA_NET_WORTH_LOW) return low(netWorthCenti);
        if (index == DATA_NET_WORTH_HIGH) return high(netWorthCenti);
        if (index == DATA_CD_DAYS) return cdDaysRemaining;
        if (index == DATA_LENDING_DAYS) return lendingDaysRemaining;
        if (index == DATA_CD_RATE_BPS) return cdRateBps;
        if (index == DATA_LENDING_RATE_BPS) return lendingRateBps;
        if (index == DATA_LENDING_OUTCOME) return lendingOutcome;
        if (index == DATA_LENDING_RESOLVED) return lendingResolved;
        if (index == DATA_PENDING_TRANSACTION) return pendingTransaction;
        if (index == DATA_SELECTED_PRICE_CENTI) return selectedPriceCenti;
        if (index == DATA_SELECTED_HOLDING_CENTI_LOW) return low(selectedHoldingCenti);
        if (index == DATA_SELECTED_HOLDING_CENTI_HIGH) return high(selectedHoldingCenti);
        if (index == DATA_SELECTED_SHARES_MICRO_LOW) return low(selectedSharesMicro);
        if (index == DATA_SELECTED_SHARES_MICRO_HIGH) return high(selectedSharesMicro);
        if (index == DATA_SAVINGS_RATE_BPS) return savingsRateBps;
        if (index == DATA_SELECTED_CHANGE_BPS) return selectedChangeBps;
        if (index == DATA_CD_ACTIVE) return cdActive;
        if (index == DATA_LENDING_ACTIVE) return lendingActive;
        if (index >= DATA_ASSET_PRICE_BASE && index < DATA_ASSET_PRICE_BASE + 9) {
            return assetPricesCenti[index - DATA_ASSET_PRICE_BASE];
        }
        if (index >= DATA_ASSET_HOLDING_BASE && index < DATA_ASSET_HOLDING_BASE + 9) {
            return assetHoldingsCenti[index - DATA_ASSET_HOLDING_BASE];
        }
        if (index == DATA_HISTORY_COUNT) return historyCount;
        if (index >= DATA_HISTORY_BASE && index < DATA_HISTORY_BASE + HISTORY_POINTS) {
            return historyCenti[index - DATA_HISTORY_BASE];
        }
        return 0;
    }

    private long readLong(int lowIndex, int highIndex) {
        return ((long) data.get(highIndex) << 32) | (data.get(lowIndex) & 0xFFFFFFFFL);
    }

    private static int low(long value) {
        return (int) value;
    }

    private static int high(long value) {
        return (int) (value >>> 32);
    }

    private static int clampIndex(int index, int size) {
        return size <= 0 ? 0 : Math.max(0, Math.min(size - 1, index));
    }

    private static int basisPoints(double rate) {
        return saturatingInt(Math.round(rate * 10_000.0));
    }

    private static long centi(double value) {
        return safeScaledLong(value, 100.0);
    }

    private static int centiInt(double value) {
        return saturatingInt(Math.round(Math.max(0.0, value) * 100.0));
    }

    private static long safeScaledLong(double value, double scale) {
        double scaled = Math.max(0.0, value) * scale;
        if (!Double.isFinite(scaled) || scaled >= Long.MAX_VALUE) {
            return Long.MAX_VALUE;
        }
        return Math.round(scaled);
    }

    private static int saturatingInt(long value) {
        return value <= Integer.MIN_VALUE
                ? Integer.MIN_VALUE
                : value >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) value;
    }

    private static final class BankerContainerData implements ContainerData {
        private final BankerMenu menu;

        private BankerContainerData(BankerMenu menu) {
            this.menu = menu;
        }

        @Override
        public int get(int index) {
            return menu.dataValue(index);
        }

        @Override
        public void set(int index, int value) {
            // Server data is derived directly from the authoritative economy and menu selections.
        }

        @Override
        public int getCount() {
            return DATA_COUNT;
        }
    }
}
