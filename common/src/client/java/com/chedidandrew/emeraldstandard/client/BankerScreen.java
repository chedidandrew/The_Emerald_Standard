package com.chedidandrew.emeraldstandard.client;

import com.chedidandrew.emeraldstandard.core.EconomyEngine;
import com.chedidandrew.emeraldstandard.core.VillageDistrictMap;
import com.chedidandrew.emeraldstandard.core.EconomyService;
import com.chedidandrew.emeraldstandard.core.EconomyState;
import com.chedidandrew.emeraldstandard.core.VillageDashboardPolicy;
import com.chedidandrew.emeraldstandard.core.VillageProsperityEngine;
import com.chedidandrew.emeraldstandard.minecraft.BankerAmountSelection;
import com.chedidandrew.emeraldstandard.minecraft.BankerMenu;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** A compact, casual-player-first bank and exchange dashboard. */
public final class BankerScreen extends AbstractContainerScreen<BankerMenu> {
    private static final int WIDTH = BankerScreenLayout.WIDTH;
    private static final int HEIGHT = BankerScreenLayout.HEIGHT;
    private static final int TAB_ACTIVITY = 6;
    private static final int TAB_NEWS = 7;
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
    private boolean marketBrowser, watchlistLoaded;
    private List<Component> wrappedBriefingSource = List.of();
    private List<FormattedCharSequence> wrappedBriefingLines = List.of();
    private float wrappedBriefingScale = -1;
    private final InvestmentBrowser browser = new InvestmentBrowser();
    private int browserCompare = -1, briefingMode, briefingScroll;
    private EditBox browserSearch;
    private String browserNotice = "";
    private boolean expansionDetails;
    private boolean districtMap;
    private boolean mapDragging;
    private int fittedMapPage = -1;
    private final DistrictMapViewport mapViewport = new DistrictMapViewport();
    private final DistrictTerrainLayer mapTerrain = new DistrictTerrainLayer();
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
    private final int[] compareScroll={0,0};
    private int compareRange=0;
    private final java.util.Map<Integer,Button> quoteButtons=new java.util.HashMap<>();
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
        wrappedBriefingScale = -1;
        super.init();
        interfaceScale = BankerScreenScale.fit(width, height, WIDTH, HEIGHT);
        leftPos = BankerScreenScale.origin(width, WIDTH, interfaceScale);
        topPos = BankerScreenScale.origin(height, HEIGHT, interfaceScale);
        amountActionButtons.clear();
        browserSearch = null;
        quoteButtons.clear();
        amountField = null;
        amountApplyButton = null;
        amountCancelButton = null;
        amountAllButton = null;
        seenStatusRevision = menu.statusRevision();
        seenInteractiveState = interactiveState();
        int x = leftPos + BankerScreenLayout.TAB_X;
        int y = topPos + BankerScreenLayout.TAB_Y;
        Component[] tabs = {
                tr("tab.home_short"),
                tr("tab.market_short"),
                tr("tab.bank_short"),
                tr("tab.trade_short"),
                tr("tab.village_short"),
                tr("tab.fund_short"),
                tr("tab.activity_short"),
                tr("tab.news_short")
        };
        Component[] tabTooltips = {
                tr("tab.overview"),
                tr("tab.market"),
                tr("tab.banking"),
                tr("tab.exchange"),
                tr("tab.village"),
                tr("tab.fund"),
                tr("activity.title"),
                tr("news.title")
        };
        for (int index = 0; index < tabs.length; index++) {
            int selectedTab = index;
            Button tabButton = Button.builder(
                            tabs[index],
                            button -> selectTab(selectedTab))
                    .bounds(
                            x + index * BankerScreenLayout.TAB_STEP,
                            y,
                            BankerScreenLayout.TAB_WIDTH,
                            BankerScreenLayout.TAB_HEIGHT)
                    .tooltip(GuiTooltips.widget(tabTooltips[index]))
                    .build();
            // Selection is shown by the gold indicator drawn below the tab. Brackets
            // made longer names exceed their fixed button interiors at common GUI scales.
            addRenderableWidget(tabButton);
        }

        if (briefingMode != 0) { addBriefingButtons(); scaleWidgetsToInterface(); return; }
        if (tab == BankerMenu.TAB_MARKET && marketBrowser) { addBrowserButtons(); scaleWidgetsToInterface(); return; }
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
            case TAB_NEWS -> {
                addRenderableWidget(Button.builder(Component.literal("Read The Emerald Wire"),
                        b->sendMenuButton(BankerMenu.BUTTON_NEWSPAPER))
                        .bounds(leftPos+20,topPos+HEIGHT-34,230,20).build());
            }
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
        amountField.setTooltip(GuiTooltips.widget(tr("tooltip.amount_input")));
        addRenderableWidget(amountField);

        amountApplyButton = addRenderableWidget(Button.builder(
                        tr("amount.apply"), button -> applyTypedAmount())
                .bounds(
                        leftPos + BankerScreenLayout.AMOUNT_APPLY_X,
                        y,
                        BankerScreenLayout.AMOUNT_APPLY_WIDTH,
                        BankerScreenLayout.AMOUNT_CONTROL_HEIGHT)
                .tooltip(GuiTooltips.widget(tr("tooltip.amount_apply")))
                .build());
        amountCancelButton = addRenderableWidget(Button.builder(
                        tr("amount.cancel"), button -> cancelTypedAmount())
                .bounds(
                        leftPos + BankerScreenLayout.AMOUNT_CANCEL_X,
                        y,
                        BankerScreenLayout.AMOUNT_CANCEL_WIDTH,
                        BankerScreenLayout.AMOUNT_CONTROL_HEIGHT)
                .tooltip(GuiTooltips.widget(tr("tooltip.amount_cancel")))
                .build());
        Component allLabel = selectedLabel(tr("amount.all"), allAmountIsApplied());
        amountAllButton = addRenderableWidget(Button.builder(
                        allLabel, button -> selectAllAvailable())
                .bounds(
                        leftPos + BankerScreenLayout.AMOUNT_ALL_X,
                        y,
                        BankerScreenLayout.AMOUNT_ALL_WIDTH,
                        BankerScreenLayout.AMOUNT_CONTROL_HEIGHT)
                .tooltip(GuiTooltips.widget(tr("tooltip.amount_all")))
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
                    .tooltip(GuiTooltips.widget(tr("tooltip.manage_transfers")))
                    .build());
        }
        addHistoryRangeButton();
    }

    private java.nio.file.Path watchlistPath() {
        return minecraft.gameDirectory.toPath().resolve("config/the_emerald_standard-watchlist.properties");
    }

    private Button detailButton(String label, int x, int y, int width, Runnable action) {
        return addRenderableWidget(Button.builder(Component.literal(label), b -> action.run())
                .bounds(leftPos+x, topPos+y, width, 16).build());
    }

    private void selectTab(int selectedTab) {
        discardAmountDraft();
        if (districtMap) closeDistrictMap();
        marketBrowser = false;
        briefingMode = 0;
        briefingScroll = 0;
        sendMenuButton(BankerMenu.BUTTON_REPORT_CLOSE);
        tab = selectedTab;
        // Every Town entry starts at the overview, including revisits from its subpages.
        if (tab == BankerMenu.TAB_VILLAGE) expansionDetails = false;
        rebuildWidgets();
    }

    private void openBriefing(int mode) {
        discardAmountDraft();
        marketBrowser = false; briefingMode = mode; briefingScroll = 0;
        sendMenuButton(mode); rebuildWidgets();
    }

    private void addBriefingButtons() {
        detailButton("Back", 12, 207, 70, () -> {
            briefingMode = 0; sendMenuButton(BankerMenu.BUTTON_REPORT_CLOSE); rebuildWidgets();
        });
        detailButton("Up", 86, 207, 38, () -> { briefingScroll = Math.max(0, briefingScroll-6); });
        detailButton("Down", 128, 207, 48, () -> { briefingScroll += 6; });
        detailButton("Town", 180, 207, 60, () -> selectTab(BankerMenu.TAB_VILLAGE));
        detailButton("Fund", 244, 207, 64, () -> {
            briefingMode=0; tab=BankerMenu.TAB_FUND; sendMenuButton(BankerMenu.BUTTON_REPORT_CLOSE); rebuildWidgets();
        });
    }

    private void drawBriefing(GuiGraphicsExtractor graphics) {
        var pages = menu.briefingPages(briefingMode);
        if (wrappedBriefingScale != interfaceScale || !wrappedBriefingSource.equals(pages)) {
            List<FormattedCharSequence> lines = new ArrayList<>();
            if (pages.isEmpty()) lines.add(Component.literal("Waiting for the server report...").getVisualOrderText());
            for (Component paragraph : pages) {
                lines.addAll(font.split(paragraph, BankerScreenScale.scaled(284,interfaceScale)));
                lines.add(Component.empty().getVisualOrderText());
            }
            wrappedBriefingSource = List.copyOf(pages); wrappedBriefingScale = interfaceScale;
            wrappedBriefingLines = List.copyOf(lines);
        }
        var lines = wrappedBriefingLines;
        int step = (int)Math.ceil((font.lineHeight+3)/interfaceScale);
        int visible = Math.max(1, 120/step);
        briefingScroll = Math.max(0,Math.min(briefingScroll,Math.max(0,lines.size()-visible)));
        for(int i=0;i<visible && briefingScroll+i<lines.size();i++)
            drawNativeText(graphics,lines.get(briefingScroll+i),18,56+i*step,TEXT,false);
        drawTextWithin(graphics,Component.literal("Read-only | Up/Down | "+(briefingScroll+1)+" / "+Math.max(1,lines.size())),
                14,182,292,MUTED,false);
        drawTextWithin(graphics,Component.literal("Build "+com.chedidandrew.emeraldstandard.core.BuildIdentity.display()),
                14,194,292,MUTED,false);
    }

    private void addBrowserButtons() {
        if (!watchlistLoaded) {
            watchlistLoaded = true;
            try { browser.load(watchlistPath()); }
            catch (java.io.IOException e) { browserNotice = "Watchlist could not be loaded."; }
        }
        if (browserCompare >= 0 && browser.comparison >= 0) {
            detailButton("Range: "+MarketDisplay.LABELS[compareRange],12,185,176,()->{
                compareRange=(compareRange+1)%MarketDisplay.RANGES.length;rebuildWidgets();
            });
            if(compareRange==0)detailButton(menu.historyYesterday()?"Yesterday":"Today - Live",194,185,114,()->selectAndRefresh(BankerMenu.BUTTON_HISTORY_SESSION));
            detailButton("Back to listings",12,207,140,()->{browserCompare=-1;rebuildWidgets();});
            detailButton("Market",166,207,142,()->{marketBrowser=false;rebuildWidgets();});
            return;
        }
        browserSearch = new EditBox(font,leftPos+12,topPos+54,172,18,Component.literal("Search investments"));
        browserSearch.setMaxLength(80); browserSearch.setValue(browser.query);
        browserSearch.setHint(Component.literal("Search name, ticker, sector"));
        browserSearch.setResponder(value -> { browser.query=value; browser.page=0; rebuildWidgets(); });
        addRenderableWidget(browserSearch);
        detailButton("Type: "+friendly(browser.filter.name()),190,54,118,()->{
            browser.filter=InvestmentBrowser.Filter.values()[(browser.filter.ordinal()+1)%InvestmentBrowser.Filter.values().length];
            browser.page=0;rebuildWidgets();
        });
        detailButton(browser.favoritesOnly?"Favorites: On":"Favorites: All",12,76,96,()->{
            browser.favoritesOnly=!browser.favoritesOnly;browser.page=0;rebuildWidgets();
        });
        detailButton(browser.holdingsOnly?"Holdings only":"All holdings",112,76,94,()->{
            browser.holdingsOnly=!browser.holdingsOnly;browser.page=0;rebuildWidgets();
        });
        detailButton(browser.comparison<0?"Compare: choose":"Clear compare",210,76,98,()->{
            browser.comparison=-1;rebuildWidgets();
        });
        var matches=browser.matches(menu::ownsAsset);
        int maxPage=Math.max(0,(matches.size()-1)/5); browser.page=Math.max(0,Math.min(maxPage,browser.page));
        for(int row=0;row<5 && browser.page*5+row<matches.size();row++) {
            int index=matches.get(browser.page*5+row), y=98+row*18;
            var asset=EconomyEngine.ASSETS.get(index);
            Button quoteButton=detailButton((browser.favorites.contains(asset.ticker())?"* ":"")+asset.ticker()+" - "+asset.name(),12,y,205,()->{
                discardAmountDraft();marketBrowser=false;selectAndRefresh(BankerMenu.BUTTON_ASSET_BASE+index);
            });
            quoteButton.setTooltip(GuiTooltips.widget(Component.literal(asset.name()+" | "+asset.sector()+"\n")
                .append(assetTypeLabel(asset)).append(Component.literal(" | ")).append(riskLabel(asset))));
            quoteButtons.put(index,quoteButton);
            detailButton(browser.favorites.contains(asset.ticker())?"-*":"+*",222,y,32,()->{
                browser.toggle(index);
                try { browser.save(watchlistPath()); browserNotice="Watchlist saved locally."; }
                catch(java.io.IOException e) { browserNotice="Could not save watchlist; this visit still works."; }
                rebuildWidgets();
            }).setTooltip(GuiTooltips.widget(Component.literal("Toggle favorite")));
            detailButton(browser.comparison==index?"Pinned":"Compare",258,y,50,()->{
                if(browser.comparison<0)browser.comparison=index;
                else if(browser.comparison!=index){
                    browserCompare=index;compareScroll[0]=compareScroll[1]=0;
                    sendMenuButton(BankerMenu.BUTTON_COMPARE_LEFT+browser.comparison);
                    sendMenuButton(BankerMenu.BUTTON_COMPARE_RIGHT+browserCompare);
                }
                rebuildWidgets();
            }).setTooltip(GuiTooltips.widget(Component.literal("Choose two investments to compare")));
        }
        detailButton("Market",12,207,94,()->{marketBrowser=false;rebuildWidgets();});
        detailButton("<",112,207,42,()->{browser.page--;rebuildWidgets();}).active=browser.page>0;
        detailButton(">",158,207,42,()->{browser.page++;rebuildWidgets();}).active=browser.page<maxPage;
        detailButton("Clear search",206,207,102,()->{
            browser.query="";browser.filter=InvestmentBrowser.Filter.ALL;browser.favoritesOnly=false;browser.holdingsOnly=false;browser.page=0;rebuildWidgets();
        });
    }


    private com.chedidandrew.emeraldstandard.client.MarketDisplay.Quote dailyQuote(int index) {
        var snapshot=menu.marketDisplay();return snapshot==null?null:snapshot.quote(EconomyEngine.ASSETS.get(index).ticker());
    }
    private String dailyLabel(int index) {
        var q=dailyQuote(index);return q==null||!q.known()?"--":String.format(Locale.ROOT,"%+.2f%%",q.daily());
    }
    private int dailyColor(int index){var q=dailyQuote(index);return q==null||!q.known()||q.daily()==0?MUTED:q.daily()>0?POSITIVE:NEGATIVE;}
    private net.minecraft.ChatFormatting dailyFormat(int index){
        var q=dailyQuote(index);return q==null||!q.known()||q.daily()==0?net.minecraft.ChatFormatting.GRAY
                :q.daily()>0?net.minecraft.ChatFormatting.GREEN:net.minecraft.ChatFormatting.RED;
    }
    private void drawComparison(GuiGraphicsExtractor g) {
        int[] choices={browser.comparison,browserCompare};
        int step=(int)Math.ceil((font.lineHeight+2)/interfaceScale);
        int visible=Math.max(1,42/step);
        for(int c=0;c<2;c++) {
            int index=choices[c],x=14+c*152;var a=EconomyEngine.ASSETS.get(index);
            drawTextWithin(g,Component.literal(a.ticker()+" | "+a.name()),x,55,138,c==0?0xFF65BFFF:0xFFF4B65D,false);
            drawTextWithin(g,Component.literal(dailyLabel(index)+" today | "+money(menu.assetPrice(index))),x,55+step,138,dailyColor(index),false);
            Component detail=Component.literal("Details: ").append(assetTypeLabel(a)).append(" | ").append(riskLabel(a))
                .append("\n"+a.sector()+" | Holding: "+holdingLabel(index)+"\n")
                .append(tr("market.behavior."+a.ticker().toLowerCase(Locale.ROOT)))
                .append("\nQuotes are not forecasts. Scroll this side to read more.");
            var lines=font.split(detail,BankerScreenScale.scaled(132,interfaceScale));
            compareScroll[c]=Math.min(compareScroll[c],Math.max(0,lines.size()-visible));
            for(int i=0;i<visible&&i+compareScroll[c]<lines.size();i++)
                drawNativeText(g,lines.get(i+compareScroll[c]),x,78+i*step,TEXT,false);
            if(lines.size()>visible) {
                g.fill(x+136,78,x+137,120,0xFF456B5A);
                int y=78+(int)(34.0*compareScroll[c]/Math.max(1,lines.size()-visible));
                g.fill(x+135,y,x+138,y+8,GOLD);
            }
        }
        g.fill(12,125,308,181,PANEL_DARK);g.outline(12,125,296,56,0xFF456B5A);
        var snapshot=menu.marketDisplay();
        if(snapshot==null||!snapshot.left().equals(EconomyEngine.ASSETS.get(choices[0]).ticker())
                ||!snapshot.right().equals(EconomyEngine.ASSETS.get(choices[1]).ticker())) {
            drawTextWithin(g,Component.literal("Loading aligned price history..."),16,146,284,MUTED,false);return;
        }
        var curve=snapshot.curves().get(compareRange);
        if(curve.left().size()<2) {
            drawTextWithin(g,Component.literal("History starts when observed; waiting for the next quote."),16,146,284,MUTED,false);return;
        }
        double min=100,max=100;
        for(double v:curve.left()){min=Math.min(min,v);max=Math.max(max,v);}
        for(double v:curve.right()){min=Math.min(min,v);max=Math.max(max,v);}
        if(max-min<0.02){min-=0.01;max+=0.01;}
        int baseline=164-(int)(24*(100-min)/(max-min));
        g.fill(14,baseline,306,baseline+1,0xFF456B5A);
        plotComparison(g,curve.left(),curve.positions(),min,max,0xFF65BFFF,false);
        plotComparison(g,curve.right(),curve.positions(),min,max,0xFFF4B65D,true);
        drawTextWithin(g,Component.literal((compareRange==0?(snapshot.yesterday()?"Yesterday":"Today - Live")+" | ":"")+"Since Day "+curve.firstDay()+" | start = 100"),
                16,128,282,MUTED,false);
        drawTextWithin(g,Component.literal(snapshot.left()+" "+String.format(Locale.ROOT,"%+.2f%%",curve.left().getLast()-100)),
                14,170,142,0xFF65BFFF,false);
        drawTextWithin(g,Component.literal(snapshot.right()+" "+String.format(Locale.ROOT,"%+.2f%%",curve.right().getLast()-100)),
                166,170,138,0xFFF4B65D,false);
    }
    private void plotComparison(GuiGraphicsExtractor g,java.util.List<Double> points,java.util.List<Double> positions,double min,double max,int color,boolean dashed) {
        for(int i=1;i<points.size();i++) {
            int x0=15+(int)(positions.get(i-1)*289),x1=15+(int)(positions.get(i)*289);
            int y0=164-(int)(24*(points.get(i-1)-min)/(max-min)),y1=164-(int)(24*(points.get(i)-min)/(max-min));
            // Dashed second series remains distinguishable even when both curves coincide.
            int count=Math.max(Math.abs(x1-x0),Math.abs(y1-y0));
            for(int j=0;j<=count;j++)if(!dashed||((x0+j)/3)%2==0){
                int x=x0+(x1-x0)*j/Math.max(1,count),y=y0+(y1-y0)*j/Math.max(1,count);
                g.fill(x,y,x+1,y+1,color);
            }
        }
    }

    private String holdingLabel(int index) {
        return menu.ownsAsset(index) && menu.assetHoldingValue(index) < 0.01
                ? "<0.01 E" : money(menu.assetHoldingValue(index));
    }

    private void drawBrowser(GuiGraphicsExtractor graphics) {
        if (browserCompare >= 0 && browser.comparison >= 0) {
            drawComparison(graphics);
            return;
        }
        for(var entry:quoteButtons.entrySet()) {
            var a=EconomyEngine.ASSETS.get(entry.getKey());
            String label=(browser.favorites.contains(a.ticker())?"* ":"")+a.ticker()+" "+dailyLabel(entry.getKey())+" - "+a.name();
            entry.getValue().setMessage(Component.literal(label).withStyle(dailyFormat(entry.getKey())));
        }
        var matches=browser.matches(menu::ownsAsset);
        String status=matches.isEmpty()?"No matching investments. Clear search or filters."
                : matches.size()+" listings | Page "+(browser.page+1)+" / "+Math.max(1,(matches.size()+4)/5)
                    +(browser.comparison>=0?" | Compare: "+EconomyEngine.ASSETS.get(browser.comparison).ticker():"");
        drawTextWithin(graphics,Component.literal(browserNotice.isEmpty()?status:browserNotice),14,191,292,MUTED,false);
    }

    private void addMarketButtons() {
        detailButton("Browse / compare",12,BankerScreenLayout.MARKET_ACTION_Y,94,()->{
            discardAmountDraft();marketBrowser=true;browserCompare=-1;browserNotice="";rebuildWidgets();
        });
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
                .tooltip(GuiTooltips.widget(tr("tooltip.previous_asset",
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
                .tooltip(GuiTooltips.widget(tr("tooltip.market_asset",
                        selected.name(), selected.sector(), riskLabel(selected), assetTypeLabel(selected))))
                .build());
        addRenderableWidget(Button.builder(
                        Component.literal(">"),
                        button -> selectAndRefresh(BankerMenu.BUTTON_ASSET_BASE + next))
                .bounds(
                        leftPos + BankerScreenLayout.MARKET_NEXT_X,
                        topPos + BankerScreenLayout.MARKET_SELECTOR_Y,
                        BankerScreenLayout.MARKET_ARROW_WIDTH,
                        BankerScreenLayout.MARKET_SELECTOR_HEIGHT)
                .tooltip(GuiTooltips.widget(tr("tooltip.next_asset",
                        nextAsset.ticker(), nextAsset.name())))
                .build());
        int y = topPos + BankerScreenLayout.MARKET_ACTION_Y;
        boolean ready = transactionsAvailable();
        Button buy = addActionButton(
                tr("action.invest"), leftPos + 112, y, 58,
                BankerMenu.ACTION_BUY, marketBuyPreview());
        buy.active = ready && selectedWholeAmount(menu.spendingPower()) > 0
                && menu.selectedAssetPrice() > 0.0;
        trackAmountAction(buy);
        Button sellQuarter = addActionButton(
                tr("action.sell_quarter"), leftPos + 174, y, 62,
                BankerMenu.ACTION_SELL_QUARTER, marketSalePreview(0.25));
        sellQuarter.active = ready && menu.ownsAsset(menu.selectedAssetIndex());
        Button sellAll = addConfirmingActionButton(
                menu.confirmationAction() == BankerMenu.ACTION_SELL_ALL
                        ? tr("action.confirm")
                        : tr("action.sell_all"),
                leftPos + 240,
                y,
                68,
                BankerMenu.ACTION_SELL_ALL,
                marketSalePreview(1.0));
        sellAll.active = ready && menu.ownsAsset(menu.selectedAssetIndex());
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
                    .tooltip(GuiTooltips.widget(explanations[index]))
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
        toSavings.active = ready && selectedWholeAmount(menu.spendingPower()) > 0;
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
        openCd.active = ready && selectedWholeAmount(menu.spendingPower()) > 0
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
        fund.active = ready && selectedWholeAmount(menu.spendingPower()) > 0
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
                .tooltip(GuiTooltips.widget(tr("tooltip.previous_resource")))
                .build());
        addRenderableWidget(Button.builder(
                        Component.literal(">"),
                        button -> selectAndRefresh(BankerMenu.BUTTON_RESOURCE_BASE + next))
                .bounds(leftPos + 274, topPos + 76, 24, 20)
                .tooltip(GuiTooltips.widget(tr("tooltip.next_resource")))
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
        if (districtMap) { addDistrictMapButtons(); return; }
        if (!menu.hasVillage()) return;
        addRenderableWidget(Button.builder(tr(expansionDetails ? "expansion.back" : "expansion.title"),
                button -> { expansionDetails = !expansionDetails; rebuildWidgets(); })
                .bounds(leftPos + 12, topPos + (expansionDetails
                        ? BankerScreenLayout.EXPANSION_ACTION_Y : 174), 140, 12).build());
        if (!expansionDetails) {
            detailButton("What next? Progress report",12,198,296,()->openBriefing(BankerMenu.BUTTON_TOWN_REPORT));
            addRenderableWidget(Button.builder(tr("map.open"), button -> {
                districtMap = true; fittedMapPage = -1;
                sendMenuButton(BankerMenu.BUTTON_MAP_OPEN); rebuildWidgets();
            }).bounds(leftPos + 166, topPos + 174, 142, 12)
                    .tooltip(GuiTooltips.widget(tr("map.help"))).build());
            return;
        }
        for (int i = 0; i < 3; i++) {
            int mode = i;
            Button choice = Button.builder(tr("expansion.mode." +
                    com.chedidandrew.emeraldstandard.core.VillageExpansion.Mode.values()[i].name().toLowerCase(Locale.ROOT)),
                    button -> selectAndRefresh(BankerMenu.ACTION_EXPANSION_AUTOMATIC + mode))
                    .bounds(leftPos + 12 + i * 100, topPos + BankerScreenLayout.EXPANSION_MODE_Y, 96, 18)
                    .tooltip(GuiTooltips.widget(tr(menu.mayManageExpansion()
                            ? "expansion.funding" : "expansion.permissions"))).build();
            choice.active = menu.mayManageExpansion() && menu.expansionMode().ordinal() != i && transactionsAvailable();
            addRenderableWidget(choice);
        }
        Button approve = Button.builder(tr("expansion.approve"), button ->
                selectAndRefresh(BankerMenu.ACTION_EXPANSION_APPROVE_ONCE))
                .bounds(leftPos + 166, topPos + BankerScreenLayout.EXPANSION_ACTION_Y, 142, 12).build();
        approve.active = menu.mayManageExpansion() && menu.expansionMode() ==
                com.chedidandrew.emeraldstandard.core.VillageExpansion.Mode.APPROVAL && transactionsAvailable();
        addRenderableWidget(approve);
    }

    private void closeDistrictMap() {
        districtMap = false; mapDragging = false;
        mapTerrain.close();
        sendMenuButton(BankerMenu.BUTTON_MAP_CLOSE);
    }

    @Override
    public void removed() {
        mapTerrain.close();
        super.removed();
    }

    private void mapButton(String label, int x, int width, Runnable action) {
        addRenderableWidget(Button.builder(tr(label), button -> action.run())
                .bounds(leftPos + x, topPos + 213, width, 12)
                .tooltip(GuiTooltips.widget(tr(label + "_help"))).build());
    }

    private void addDistrictMapButtons() {
        mapButton("map.back", 12, 64, () -> { closeDistrictMap(); rebuildWidgets(); });
        mapButton("map.previous", 80, 23, () -> sendMenuButton(BankerMenu.BUTTON_MAP_PREVIOUS));
        mapButton("map.next", 107, 23, () -> sendMenuButton(BankerMenu.BUTTON_MAP_NEXT));
        mapButton("map.zoom_out", 134, 23, () -> mapViewport.zoom(0.8));
        mapButton("map.zoom_in", 161, 23, () -> mapViewport.zoom(1.25));
        mapButton("map.fit", 188, 55, () -> mapViewport.fit(menu.districtMap()));
        mapButton("map.home", 247, 61, () -> {
            var p = menu.districtMap();
            mapViewport.focus(p);
        });
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (districtMap && event.button() == 0 && DistrictMapViewport.contains(
                logicalMouseX(event.x()) - leftPos, logicalMouseY(event.y()) - topPos)) {
            mapDragging = true; return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
        if (districtMap && mapDragging) {
            mapViewport.pan(deltaX / interfaceScale, deltaY / interfaceScale); return true;
        }
        return super.mouseDragged(event, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (mapDragging) { mapDragging = false; return true; }
        return super.mouseReleased(event);
    }

    private static int mapColor(VillageDistrictMap.Marker m) {
        if (m.kind() == VillageDistrictMap.DISTRICT || m.kind() == VillageDistrictMap.TERRITORY)
            return m.status() == VillageDistrictMap.CURRENT ? GOLD : TEXT;
        if (m.kind() == VillageDistrictMap.BANK) return 0xFFCF9CFF;
        return switch (m.status()) {
            case VillageDistrictMap.BUILDING -> GOLD;
            case VillageDistrictMap.PLANNED -> 0xFF78BBF2;
            case VillageDistrictMap.BLOCKED -> NEGATIVE;
            default -> EMERALD;
        };
    }

    private Component mapMarkerTitle(VillageDistrictMap.Marker m) {
        if (m.kind() == VillageDistrictMap.DISTRICT) return tr("map.district", m.district());
        if (m.kind() == VillageDistrictMap.BANK) return tr("map.bank");
        var types = VillageProsperityEngine.ProjectType.values();
        return m.extra() >= 0 && m.extra() < types.length ? projectLabel(types[m.extra()]) : tr("map.site");
    }

    private void drawDistrictMap(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        var page = menu.districtMap();
        if (fittedMapPage != page.number() && page.total() > 0) {
            fittedMapPage = page.number(); mapViewport.fit(page);
        }
        graphics.pose().pushMatrix();
        graphics.pose().translate(leftPos, topPos);
        try {
            int x = DistrictMapViewport.X, y = DistrictMapViewport.Y;
            int right = x + DistrictMapViewport.WIDTH, bottom = y + DistrictMapViewport.HEIGHT;
            graphics.fill(x, y, right, bottom, PANEL_DARK);
            mapTerrain.draw(graphics, mapViewport);
            graphics.outline(x, y, right - x, bottom - y, 0xFF456B5A);
            double step = 16;
            while (step * mapViewport.scale() < 16) step *= 2;
            double pixels = step * mapViewport.scale();
            for (double gridX = x + ((mapViewport.x(0) - x) % pixels + pixels) % pixels;
                    gridX < right; gridX += pixels)
                graphics.fill((int) gridX, y + 1, (int) gridX + 1, bottom - 1, 0x4020342B);
            for (double gridY = y + ((mapViewport.y(0) - y) % pixels + pixels) % pixels;
                    gridY < bottom; gridY += pixels)
                graphics.fill(x + 1, (int) gridY, right - 1, (int) gridY + 1, 0x4020342B);
            VillageDistrictMap.Marker hovered = null;
            double nearest = Double.MAX_VALUE;
            // Survey areas beneath every landmark. Clipping must not create fake boundary edges.
            for (var m : page.markers()) {
                if(m.kind()==VillageDistrictMap.DISTRICT && m.extra()==-1) continue;
                if(m.kind()!=VillageDistrictMap.DISTRICT && m.kind()!=VillageDistrictMap.TERRITORY) continue;
                double lx = mapViewport.x(m.minX()), rx = mapViewport.x((double) m.maxX() + 1);
                double ty = mapViewport.y(m.minZ()), by = mapViewport.y((double) m.maxZ() + 1);
                if (rx < x || lx > right || by < y || ty > bottom) continue;
                int left = (int) Math.max(x + 1, lx), top = (int) Math.max(y + 1, ty);
                int endX = (int) Math.min(right - 1, rx), endY = (int) Math.min(bottom - 1, by);
                int color = mapColor(m);
                graphics.fill(left, top, endX, endY, (color & 0xFFFFFF) | 0x22000000);
                if(m.kind()==VillageDistrictMap.TERRITORY) {
                    if(m.extra()==1) graphics.fill(left,top,Math.max(left+1,endX),Math.max(top+1,endY),color);
                    continue;
                }
                if (lx >= x + 1) graphics.fill(left, top, left + 1, endY, color);
                if (rx <= right - 1) graphics.fill(endX - 1, top, endX, endY, color);
                if (ty >= y + 1) graphics.fill(left, top, endX, top + 1, color);
                if (by <= bottom - 1) graphics.fill(left, endY - 1, endX, endY, color);
                if (DistrictMapViewport.contains(mouseX, mouseY) && mouseX >= left && mouseX < endX
                        && mouseY >= top && mouseY < endY) {
                    double distance = Math.hypot(mouseX - mapViewport.x(m.x()), mouseY - mapViewport.y(m.z()));
                    if (distance < nearest) { hovered = m; nearest = distance; }
                }
            }
            // Any exact building/center hit takes precedence over the broader coverage tooltip.
            nearest = Double.MAX_VALUE;
            // Footprints first, then point landmarks. Never draw outside the map rectangle.
            for (int layer = VillageDistrictMap.PROJECT; layer >= VillageDistrictMap.DISTRICT; layer--) {
                for (var m : page.markers()) {
                    if (m.kind() != layer) continue;
                    double cx = mapViewport.x(m.x()), cy = mapViewport.y(m.z());
                    double radius = m.kind() == VillageDistrictMap.PROJECT ? 2 : 3;
                    boolean point = m.kind() != VillageDistrictMap.PROJECT;
                    double lx = point ? cx - radius : Math.min(cx - radius, mapViewport.x(m.minX()));
                    double ly = point ? cy - radius : Math.min(cy - radius, mapViewport.y(m.minZ()));
                    double rx = point ? cx + radius : Math.max(cx + radius, mapViewport.x((double) m.maxX() + 1));
                    double by = point ? cy + radius : Math.max(cy + radius, mapViewport.y((double) m.maxZ() + 1));
                    if (rx <= x + 1 || lx >= right - 1 || by <= y + 1 || ly >= bottom - 1) continue;
                    int left = (int) Math.max(x + 1, lx), top = (int) Math.max(y + 1, ly);
                    int width = Math.max(1, (int) Math.min(right - 1, rx) - left);
                    int height = Math.max(1, (int) Math.min(bottom - 1, by) - top);
                    int color = mapColor(m);
                    graphics.outline(left - 1, top - 1, width + 2, height + 2, 0xFF132219);
                    graphics.fill(left, top, left + width, top + height, (color & 0xFFFFFF) | 0x55000000);
                    graphics.outline(left, top, width, height, color);
                    if (m.kind() == VillageDistrictMap.DISTRICT
                            && cx >= x + 3 && cx < right - 28 && cy >= y + 12 && cy < bottom - 12)
                        drawTextWithin(graphics, Component.literal("D" + m.district()),
                                (int) cx + 4, (int) cy - 10, 24, color, true);
                    if (DistrictMapViewport.contains(mouseX, mouseY) && mouseX >= left - 2
                            && mouseX <= left + width + 2 && mouseY >= top - 2 && mouseY <= top + height + 2) {
                        double distance = Math.hypot(mouseX - cx, mouseY - cy);
                        if (distance <= nearest) { hovered = m; nearest = distance; }
                    }
                }
            }
            // The screen is tied to a nearby desk in the player's current dimension.
            if (minecraft.player != null && menu.hasVillage()) {
                int px = (int) Math.round(mapViewport.x(minecraft.player.getX()));
                int py = (int) Math.round(mapViewport.y(minecraft.player.getZ()));
                if (DistrictMapViewport.contains(px - 3, py - 3) && DistrictMapViewport.contains(px + 3, py + 3)) {
                    graphics.fill(px - 3, py, px + 4, py + 1, 0xFF36EBED);
                    graphics.fill(px, py - 3, px + 1, py + 4, 0xFF36EBED);
                }
            }
            drawTextWithin(graphics, tr("map.title"), 12, 55, 205, GOLD, false);
            graphics.fill(x + 2, y + 2, x + 128, y + 12, 0xD0132219);
            drawTextWithin(graphics, ExploredTerrainRuntime.error().isEmpty()?tr("map.terrain")
                    :Component.literal("Terrain cache unavailable"), x + 4, y + 3, 123, TEXT, false);
            graphics.fill(179, 75, 220, 87, 0xD0132219);
            drawTextWithin(graphics, tr("map.north"), 181, 77, 37, MUTED, true);
            drawTextWithin(graphics, tr("map.page", page.number() + 1, page.pages()), 230, 55, 78, TEXT, false);
            drawTextWithin(graphics, tr("map.count", page.districts()), 230, 76, 78, TEXT, false);
            String[] legend = {"current", "districts", "banks", "built", "building", "planned", "blocked", "player"};
            int[] colors = {GOLD, TEXT, 0xFFCF9CFF, EMERALD, GOLD, 0xFF78BBF2, NEGATIVE, 0xFF36EBED};
            for (int i = 0; i < legend.length; i++)
                drawTextWithin(graphics, tr("map.legend." + legend[i]), 230, 89 + i * 10, 78, colors[i], false);
            drawTextWithin(graphics, tr("map.unsited", page.unsited()), 230, 174, 78, MUTED, false);
            drawTextWithin(graphics, tr("map.grid", (long) step), 230, 184, 78, MUTED, false);
            drawTextWithin(graphics, tr("map.hint"), 12, 199, 296, MUTED, false);
            if (page.total() == 0)
                drawWrappedText(graphics, tr(menu.hasVillage() ? "map.loading" : "map.unavailable"),
                        30, 120, 170, 12, 3, MUTED);
            if (hovered != null) {
                var m = hovered;
                Component detail = m.kind() == VillageDistrictMap.DISTRICT
                        ? tr("map.residents", m.value(), m.extra())
                        : tr("map.status." + m.status());
                Component text = mapMarkerTitle(m).copy().append("\n")
                        .append(tr("map.district", m.district())).append(" | ").append(detail)
                        .append("\nX: " + (int) m.x() + "  Z: " + (int) m.z());
                if (m.kind() == VillageDistrictMap.PROJECT) text = text.copy().append("\n")
                        .append(tr("map.progress", m.value()));
                if (m.kind() == VillageDistrictMap.DISTRICT && m.extra()>=0) text = text.copy().append("\n")
                        .append(tr("map.coverage", (long) m.maxX() - m.minX() + 1, (long) m.maxZ() - m.minZ() + 1))
                        .append("\nX: " + m.minX() + " .. " + m.maxX() + "  Z: " + m.minZ() + " .. " + m.maxZ());
                GuiTooltips.show(graphics, font, text, tooltipMouseX, tooltipMouseY);
            }
        } finally { graphics.pose().popMatrix(); }
    }

    private void addFundButtons() {
        if (!menu.hasVillage()) {
            return;
        }
        detailButton("Preview",12,165,78,()->openBriefing(BankerMenu.BUTTON_FUND_PREVIEW));
        detailButton("Receipt",94,165,84,()->openBriefing(BankerMenu.BUTTON_FUND_RECEIPT));
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
                && menu.donationDraft() > 0 && menu.paymentPlan(menu.donationDraft()) != null
                && !(menu.fundTypeIndex() == 2 && menu.fundableProjectTypeOrdinal() < 0);
        trackAmountAction(contributionButton);

        Button typeButton = Button.builder(
                        tr("fund.type_button", fundTypeLabel()),
                        button -> selectAndRefresh(BankerMenu.BUTTON_FUND_TYPE))
                .bounds(leftPos + 12, topPos + BankerScreenLayout.FUND_CONTROL_Y, 140, 18)
                .tooltip(GuiTooltips.widget(fundTypeExplanation()))
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
                .tooltip(GuiTooltips.widget(fundPurposeTooltip(purposeState)))
                .build();
        purposeButton.active = purposeState
                == BankerScreenLayout.FundPurposeTooltipState.CYCLABLE;
        addRenderableWidget(purposeButton);
    }

    private void addHistoryRangeButton() {
        if(tab==BankerMenu.TAB_MARKET&&menu.historyRangeIndex()==0)
            addRenderableWidget(Button.builder(Component.literal(menu.historyYesterday()?"Yesterday":"Live today"),b->selectAndRefresh(BankerMenu.BUTTON_HISTORY_SESSION))
                .bounds(leftPos+205,topPos+48,59,12).tooltip(GuiTooltips.widget(Component.literal("Switch Today / Yesterday"))).build());
        addRenderableWidget(Button.builder(
                        Component.literal(historyRangeLabel()),
                        button -> selectAndRefresh(BankerMenu.BUTTON_HISTORY_RANGE))
                .bounds(
                        leftPos + BankerScreenLayout.HISTORY_BUTTON_X,
                        topPos + BankerScreenLayout.HISTORY_BUTTON_Y,
                        BankerScreenLayout.HISTORY_BUTTON_WIDTH,
                        BankerScreenLayout.HISTORY_BUTTON_HEIGHT)
                .tooltip(GuiTooltips.widget(tr("tooltip.history_range")))
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
                .tooltip(GuiTooltips.widget(tr("activity.filter_tooltip")))
                .build());

        Button newer = Button.builder(
                        Component.literal("▲"),
                        button -> sendMenuButton(BankerMenu.BUTTON_ACTIVITY_NEWER))
                .bounds(
                        leftPos + BankerScreenLayout.ACTIVITY_SCROLL_BUTTON_X,
                        topPos + BankerScreenLayout.ACTIVITY_SCROLL_UP_Y,
                        BankerScreenLayout.ACTIVITY_SCROLL_BUTTON_WIDTH,
                        BankerScreenLayout.ACTIVITY_SCROLL_BUTTON_HEIGHT)
                .tooltip(GuiTooltips.widget(tr("activity.newer")))
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
                .tooltip(GuiTooltips.widget(tr("activity.older")))
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
                .tooltip(GuiTooltips.widget(tooltip))
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
                .tooltip(GuiTooltips.widget(tooltip))
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
                .tooltip(GuiTooltips.widget(explanation))
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
                .tooltip(GuiTooltips.widget(explanation))
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
                Math.max(0L, (long) Math.floor(menu.spendingPower())));
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
                || (menu.hasVillage() && menu.fundAvailable() && menu.spendingPower() >= 1.0);
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
        if (briefingMode != 0 || (tab == BankerMenu.TAB_MARKET && marketBrowser)) return false;
        return tab == BankerMenu.TAB_OVERVIEW
                || tab == BankerMenu.TAB_MARKET
                || tab == BankerMenu.TAB_BANKING
                || tab == BankerMenu.TAB_EXCHANGE
                || tab == BankerMenu.TAB_FUND;
    }

    @Override
    protected void rebuildWidgets() {
        EditBox previousSearch = browserSearch, previousAmount = amountField;
        boolean searchFocused = previousSearch != null && previousSearch.isFocused();
        boolean amountFocused = previousAmount != null && previousAmount.isFocused();
        super.rebuildWidgets();
        browserSearch = retainEditingField(previousSearch, browserSearch, searchFocused);
        amountField = retainEditingField(previousAmount, amountField, amountFocused);
    }

    private EditBox retainEditingField(EditBox previous, EditBox replacement, boolean wasFocused) {
        if (!wasFocused || previous == null || replacement == null
                || !previous.getValue().equals(replacement.getValue())) return replacement;
        // Keep the native editor object: cursor, selection direction, IME and horizontal scroll
        // must survive refreshes. Restoring only the cursor selects/deletes the remaining suffix.
        removeWidget(replacement);
        previous.setX(replacement.getX()); previous.setY(replacement.getY());
        previous.setWidth(replacement.getWidth()); previous.setHeight(replacement.getHeight());
        addRenderableWidget(previous);
        setFocused(previous);
        return previous;
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
                if (tab==BankerMenu.TAB_FUND && (menu.statusCode()==14 || menu.statusCode()==17 || menu.statusCode()==18)) {
                    briefingMode=BankerMenu.BUTTON_FUND_RECEIPT;briefingScroll=0;
                }
            }
            seenStatusRevision = revision;
            seenInteractiveState = state;
            rebuildWidgets();
        }
    }

    private int interactiveState() {
        int result = 1;
        result = 31 * result + menu.statusRevision();
        result = 31 * result + (menu.historyYesterday()?1:0);
        result = 31 * result + menu.historyRangeIndex();
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
        result = 31 * result + (menu.ownsAsset(menu.selectedAssetIndex()) ? 1 : 0);
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
        result = 31 * result + menu.expansionMode().ordinal();
        result = 31 * result + (menu.mayManageExpansion() ? 1 : 0);
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
        if (districtMap) {
            switch (event.key()) {
                case InputConstants.KEY_ESCAPE -> { closeDistrictMap(); rebuildWidgets(); return true; }
                case InputConstants.KEY_LEFT -> { mapViewport.pan(20, 0); return true; }
                case InputConstants.KEY_RIGHT -> { mapViewport.pan(-20, 0); return true; }
                case InputConstants.KEY_UP -> { mapViewport.pan(0, 20); return true; }
                case InputConstants.KEY_DOWN -> { mapViewport.pan(0, -20); return true; }
                case InputConstants.KEY_HOME -> { mapViewport.fit(menu.districtMap()); return true; }
            }
        }
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
        if(briefingMode!=0 && verticalAmount!=0) { briefingScroll+=verticalAmount>0?-3:3;return true; }
        if(marketBrowser&&browserCompare>=0&&browser.comparison>=0&&tab==BankerMenu.TAB_MARKET&&verticalAmount!=0) {
            int col=logicalMouseX-leftPos<160?0:1;
            if(logicalMouseY-topPos>=77&&logicalMouseY-topPos<=123)
                compareScroll[col]=Math.max(0,compareScroll[col]+(verticalAmount>0?-3:3));
            return true;
        }
        if(marketBrowser && tab==BankerMenu.TAB_MARKET && verticalAmount!=0) {
            browser.page+=verticalAmount>0?-1:1;rebuildWidgets();return true;
        }
        if (districtMap && DistrictMapViewport.contains(logicalMouseX - leftPos, logicalMouseY - topPos)) {
            if (verticalAmount != 0) mapViewport.zoomAt(verticalAmount > 0 ? 1.25 : 0.8,
                    logicalMouseX(mouseX) - leftPos, logicalMouseY(mouseY) - topPos);
            return true;
        }
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

        if (briefingMode != 0 || (tab == BankerMenu.TAB_MARKET && marketBrowser)) return;
        if (tab == BankerMenu.TAB_OVERVIEW) {
            drawChart(graphics, menu.netWorthHistoryPointsCenti(),
                    menu.netWorthHistorySpanDays(),
                    x + BankerScreenLayout.OVERVIEW_CHART_X,
                    y + BankerScreenLayout.OVERVIEW_CHART_Y,
                    BankerScreenLayout.OVERVIEW_CHART_WIDTH,
                    BankerScreenLayout.OVERVIEW_CHART_HEIGHT,
                    logicalMouseX, logicalMouseY);
        } else if (tab == BankerMenu.TAB_MARKET) {
            drawInvestmentChart(graphics,
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
            graphics.outline(
                    x + BankerScreenLayout.EXCHANGE_PANEL_X,
                    y + BankerScreenLayout.EXCHANGE_PANEL_Y,
                    BankerScreenLayout.EXCHANGE_PANEL_WIDTH,
                    BankerScreenLayout.EXCHANGE_PANEL_HEIGHT,
                    0xFF456B5A);
            ItemStack resourceStack = menu.selectedResourceStack();
            if (!resourceStack.isEmpty()) {
                graphics.item(
                        resourceStack,
                        x + BankerScreenLayout.EXCHANGE_RESOURCE_ICON_X,
                        y + BankerScreenLayout.EXCHANGE_ICON_Y);
            }
            graphics.item(
                    new ItemStack(Items.EMERALD),
                    x + BankerScreenLayout.EXCHANGE_EMERALD_ICON_X,
                    y + BankerScreenLayout.EXCHANGE_ICON_Y);
            drawChart(graphics, menu.commodityHistoryPointsCenti(),
                    menu.commodityHistorySpanDays(),
                    x + BankerScreenLayout.EXCHANGE_CHART_X,
                    y + BankerScreenLayout.EXCHANGE_CHART_Y,
                    BankerScreenLayout.EXCHANGE_CHART_WIDTH,
                    BankerScreenLayout.EXCHANGE_CHART_HEIGHT,
                    logicalMouseX,
                    logicalMouseY);
        } else if (tab == BankerMenu.TAB_VILLAGE) {
            if (districtMap) {
                drawDistrictMap(graphics, logicalMouseX - x, logicalMouseY - y);
            } else if (expansionDetails && menu.hasVillage()) {
                graphics.outline(x + 10, y + 54, 298,
                        BankerScreenLayout.EXPANSION_PANEL_HEIGHT, 0xFF456B5A);
            } else {
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
            }
        } else if (tab == BankerMenu.TAB_FUND) {
            graphics.outline(x + 10, y + 54, 298, 97, 0xFF456B5A);
        } else if (tab == TAB_ACTIVITY) {
            graphics.outline(x + 10, y + 54, 298, 121, 0xFF456B5A);
            drawActivityScrollbar(graphics, x, y);
        } else if (tab == TAB_NEWS) {
            graphics.outline(
                    x + BankerScreenLayout.NEWS_PANEL_X,
                    y + BankerScreenLayout.NEWS_PANEL_Y,
                    BankerScreenLayout.NEWS_PANEL_WIDTH,
                    BankerScreenLayout.NEWS_PANEL_HEIGHT,
                    0xFF456B5A);
            graphics.fill(
                    x + BankerScreenLayout.NEWS_VISUAL_X,
                    y + BankerScreenLayout.NEWS_VISUAL_Y,
                    x + BankerScreenLayout.NEWS_VISUAL_X
                            + BankerScreenLayout.NEWS_VISUAL_SIZE,
                    y + BankerScreenLayout.NEWS_VISUAL_Y
                            + BankerScreenLayout.NEWS_VISUAL_SIZE,
                    PANEL_DARK);
            graphics.outline(
                    x + BankerScreenLayout.NEWS_VISUAL_X,
                    y + BankerScreenLayout.NEWS_VISUAL_Y,
                    BankerScreenLayout.NEWS_VISUAL_SIZE,
                    BankerScreenLayout.NEWS_VISUAL_SIZE,
                    0xFF456B5A);
            drawNewsIcon(graphics, x, y);
            graphics.fill(
                    x + BankerScreenLayout.NEWS_GUIDANCE_X,
                    y + BankerScreenLayout.NEWS_DIVIDER_Y,
                    x + BankerScreenLayout.NEWS_GUIDANCE_X
                            + BankerScreenLayout.NEWS_GUIDANCE_WIDTH,
                    y + BankerScreenLayout.NEWS_DIVIDER_Y + 1,
                    0xFF456B5A);
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

        if (briefingMode != 0) { drawBriefing(graphics); return; }
        if (tab == BankerMenu.TAB_MARKET && marketBrowser) { drawBrowser(graphics); return; }
        switch (tab) {
            case BankerMenu.TAB_OVERVIEW -> drawOverviewLabels(graphics);
            case BankerMenu.TAB_MARKET -> drawMarketLabels(graphics);
            case BankerMenu.TAB_BANKING -> drawBankingLabels(graphics);
            case BankerMenu.TAB_EXCHANGE -> drawExchangeLabels(graphics);
            case BankerMenu.TAB_VILLAGE -> drawVillageLabels(graphics);
            case BankerMenu.TAB_FUND -> drawFundLabels(graphics);
            case TAB_ACTIVITY -> drawActivityLabels(graphics);
            case TAB_NEWS -> drawNewsLabels(graphics);
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
        } else if (tab == BankerMenu.TAB_VILLAGE && expansionDetails && menu.hasVillage()) {
            footer = tr(menu.mayManageExpansion() ? "expansion.funding" : "expansion.permissions");
        }
        if (footer != null && !districtMap) {
            int footerY = tab == BankerMenu.TAB_VILLAGE && expansionDetails && menu.hasVillage()
                    ? BankerScreenLayout.EXPANSION_FOOTER_Y : BankerScreenLayout.FOOTER_Y;
            drawTextWithin(graphics, footer, 12, footerY,
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

    private static Component assetTypeLabel(EconomyEngine.Asset asset) {
        return tr("market.type." + asset.type().name().toLowerCase(Locale.ROOT));
    }

    private void drawMarketLabels(GuiGraphicsExtractor graphics) {
        EconomyEngine.Asset selected = menu.selectedAsset();
        drawTextWithin(graphics, assetTypeLabel(selected),
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
        drawWrappedText(graphics, tr("market.behavior." + selected.ticker().toLowerCase(Locale.ROOT)),
                BankerScreenLayout.MARKET_SELECTOR_LABEL_X, BankerScreenLayout.MARKET_BEHAVIOR_Y,
                BankerScreenLayout.MARKET_META_WIDTH, BankerScreenLayout.MARKET_BEHAVIOR_LINE_STEP,
                BankerScreenLayout.MARKET_BEHAVIOR_LINES, MUTED);

        drawTextWithin(graphics, Component.literal(dailyLabel(menu.selectedAssetIndex())+(menu.historyRangeIndex()==0?" today":" today | "+selected.name())),
                BankerScreenLayout.MARKET_TITLE_X,
                BankerScreenLayout.MARKET_TITLE_Y,
                menu.historyRangeIndex()==0?82:BankerScreenLayout.MARKET_TITLE_WIDTH,
                dailyColor(menu.selectedAssetIndex()),
                false);
        drawTextWithin(graphics,
                showingYesterday()?Component.literal("Now: "+money(menu.selectedAssetPrice())+" | "+dailyLabel(menu.selectedAssetIndex()))
                        :tr("market.price_change", money(menu.selectedAssetPrice()), signed(menu.selectedChangePercent())),
                BankerScreenLayout.MARKET_DETAIL_X,
                BankerScreenLayout.MARKET_PRICE_Y,
                BankerScreenLayout.MARKET_DETAIL_WIDTH,
                showingYesterday()?dailyColor(menu.selectedAssetIndex()):menu.selectedChangePercent() >= 0.0 ? POSITIVE : NEGATIVE,
                false);
        drawTextWithin(graphics,
                tr(selected.isCommodity() ? "market.holding_units" : "market.holding", selectedQuantity(false),
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
        drawBankBalanceColumn(graphics, tr("label.available_funds"), money(menu.spendingPower()), 16, 88);
        drawBankBalanceColumn(graphics, tr("banking.total_cds"), money(menu.cdValue()), 113, 88);
        drawBankBalanceColumn(graphics, tr("banking.positions_short"),
                menu.cdCount() + "/" + EconomyState.MAX_TERM_POSITIONS, 210, 92);
        drawNativeText(graphics, tr("banking.cd_term"), 14,
                BankerScreenLayout.BANKING_PRODUCT_CONTROL_Y + 5, MUTED, false);
        int amount = selectedWholeAmount(menu.spendingPower());
        drawTextWithin(graphics,
                tr("banking.new_cd_flow", money(amount)),
                14, BankerScreenLayout.BANKING_PRODUCT_DETAIL_Y, 292, MUTED, false);
        drawTextWithin(graphics,
                tr("banking.new_cd_after", money(menu.bankCashAfterSpending(amount)),
                        money(menu.cdValue() + amount)),
                14, BankerScreenLayout.BANKING_PRODUCT_PREVIEW_Y, 292,
                amount > 0 ? POSITIVE : MUTED, false);
    }

    private void drawLoanLabels(GuiGraphicsExtractor graphics) {
        drawBankBalanceColumn(graphics, tr("label.available_funds"), money(menu.spendingPower()), 16, 88);
        drawBankBalanceColumn(graphics, tr("banking.total_loans"),
                money(menu.lendingValue()), 113, 88);
        drawBankBalanceColumn(graphics, tr("banking.positions_short"),
                menu.lendingCount() + "/" + EconomyState.MAX_TERM_POSITIONS, 210, 92);
        drawNativeText(graphics, tr("banking.loan_term"), 14,
                BankerScreenLayout.BANKING_PRODUCT_CONTROL_Y + 5, MUTED, false);
        int amount = selectedWholeAmount(menu.spendingPower());
        drawTextWithin(graphics,
                tr("banking.new_loan_flow", money(amount)),
                14, BankerScreenLayout.BANKING_PRODUCT_DETAIL_Y, 292, MUTED, false);
        drawTextWithin(graphics,
                tr("banking.new_loan_after", money(menu.bankCashAfterSpending(amount)),
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
        drawNativeCenteredTextWithin(
                graphics,
                resource,
                160,
                69,
                BankerScreenLayout.EXCHANGE_TEXT_WIDTH,
                GOLD);
        drawNativeCenteredTextWithin(graphics,
                tr("exchange.owned", menu.selectedResourceCount()),
                160,
                83,
                BankerScreenLayout.EXCHANGE_TEXT_WIDTH,
                TEXT);
        drawNativeCenteredTextWithin(graphics,
                tr("exchange.quote", String.format(Locale.ROOT, "%.2f",
                        menu.selectedResourceUnitQuote())),
                160,
                96,
                BankerScreenLayout.EXCHANGE_TEXT_WIDTH,
                MUTED);
    }

    private void drawVillageLabels(GuiGraphicsExtractor graphics) {
        if (districtMap) return;
        if (expansionDetails && menu.hasVillage()) {
            drawTextWithin(graphics, tr("expansion.city", menu.cityDistricts()),
                    18, BankerScreenLayout.EXPANSION_TITLE_Y, 284, GOLD, false);
            drawWrappedText(graphics, tr("expansion.reason." + menu.expansionReason().name().toLowerCase(Locale.ROOT)),
                    18, BankerScreenLayout.EXPANSION_REASON_Y, 284,
                    BankerScreenLayout.EXPANSION_LINE_STEP, 2, TEXT);
            drawTextWithin(graphics, tr("expansion.upkeep", String.format(Locale.ROOT, "%.2f", menu.cityDailyUpkeep())),
                    18, BankerScreenLayout.EXPANSION_UPKEEP_Y, 284, TEXT, false);
            drawTextWithin(graphics, tr("expansion.lighting", menu.villageLightingCoverage()),
                    18, BankerScreenLayout.EXPANSION_LIGHTING_Y, 284, TEXT, false);
            drawTextWithin(graphics, tr("expansion.food_sources",
                    String.format(Locale.ROOT, "%.1f", menu.villageFoodSourceBonusPercent()),
                    String.format(Locale.ROOT, "%.1f", menu.villageCropUnits()),
                    String.format(Locale.ROOT, "%.1f", menu.villageLivestockUnits())),
                    18, BankerScreenLayout.EXPANSION_FOOD_Y, 284, TEXT, false);
            String advice = com.chedidandrew.emeraldstandard.core.VillageUpkeepAdvice.choose(
                    menu.expansionReason(), menu.villagePopulation(), menu.villageHousing(), menu.villageFood(),
                    menu.villageSafety(), menu.villageLightingCoverage()).name().toLowerCase(Locale.ROOT);
            drawWrappedText(graphics, tr("expansion.advice." + advice),
                    18, BankerScreenLayout.EXPANSION_ADVICE_Y, 284,
                    BankerScreenLayout.EXPANSION_LINE_STEP, 2, GOLD);
            return;
        }
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

    private void drawNewsLabels(GuiGraphicsExtractor graphics) {
        VillageDashboardPolicy.Snapshot snapshot = villageDashboardSnapshot();
        VillageDashboardPolicy.Assessment assessment =
                VillageDashboardPolicy.assess(snapshot);
        int guidanceLineStep = BankerScreenLayout.newsGuidanceLineStep(interfaceScale);
        int safetyY = BankerScreenLayout.newsSafetyY(interfaceScale);
        drawTextWithin(
                graphics,
                newsHeadline(snapshot, assessment.bulletin()),
                BankerScreenLayout.NEWS_HEADLINE_X,
                BankerScreenLayout.NEWS_HEADLINE_Y,
                BankerScreenLayout.NEWS_TEXT_WIDTH,
                newsColor(assessment.tone()),
                false);
        drawWrappedText(
                graphics,
                newsArticle(snapshot, assessment.bulletin()),
                BankerScreenLayout.NEWS_HEADLINE_X,
                BankerScreenLayout.NEWS_ARTICLE_Y,
                BankerScreenLayout.NEWS_TEXT_WIDTH,
                BankerScreenLayout.NEWS_ARTICLE_LINE_STEP,
                BankerScreenLayout.NEWS_ARTICLE_MAX_LINES,
                TEXT);
        drawWrappedText(
                graphics,
                prosperityGuidance(snapshot, assessment.prosperityGuidance()),
                BankerScreenLayout.NEWS_GUIDANCE_X,
                BankerScreenLayout.NEWS_PROSPERITY_Y,
                BankerScreenLayout.NEWS_GUIDANCE_WIDTH,
                guidanceLineStep,
                BankerScreenLayout.NEWS_GUIDANCE_MAX_LINES,
                assessment.prosperityGuidance()
                                == VillageDashboardPolicy.ProsperityGuidance
                                        .MAINTAIN_FOUNDATIONS
                        ? POSITIVE : GOLD);
        drawWrappedText(
                graphics,
                safetyGuidance(snapshot, assessment.safetyGuidance()),
                BankerScreenLayout.NEWS_GUIDANCE_X,
                safetyY,
                BankerScreenLayout.NEWS_GUIDANCE_WIDTH,
                guidanceLineStep,
                BankerScreenLayout.NEWS_GUIDANCE_MAX_LINES,
                assessment.safetyGuidance()
                                == VillageDashboardPolicy.SafetyGuidance.MAINTAIN_SECURITY
                        ? POSITIVE
                        : assessment.safetyGuidance()
                                        == VillageDashboardPolicy.SafetyGuidance.RECENT_INCIDENT
                                ? NEGATIVE : GOLD);
    }

    private void drawNewsIcon(
            GuiGraphicsExtractor graphics, int screenX, int screenY) {
        VillageDashboardPolicy.Snapshot snapshot = villageDashboardSnapshot();
        VillageDashboardPolicy.BulletinKind kind =
                VillageDashboardPolicy.assess(snapshot).bulletin();
        ItemStack icon = newsIcon(snapshot, kind);
        graphics.pose().pushMatrix();
        graphics.pose().translate(
                screenX + BankerScreenLayout.NEWS_ICON_X,
                screenY + BankerScreenLayout.NEWS_ICON_Y);
        graphics.pose().scale(
                BankerScreenLayout.NEWS_ICON_SCALE,
                BankerScreenLayout.NEWS_ICON_SCALE);
        graphics.item(icon, 0, 0);
        graphics.pose().popMatrix();
    }

    private VillageDashboardPolicy.Snapshot villageDashboardSnapshot() {
        return new VillageDashboardPolicy.Snapshot(
                menu.hasVillage(),
                menu.villageLifecycle(),
                menu.villagePopulation(),
                menu.villageHousing(),
                menu.villageTier(),
                menu.villageProsperity(),
                menu.villageSafety(),
                menu.villageFood(),
                menu.villageMaterials(),
                menu.villageTreasury(),
                villageProjectType(menu.villageProjectTypeOrdinal()),
                menu.villageProjectProgress(),
                menu.fundableProjectTypeOrdinal() >= 0,
                menu.villageProjectBacklog(),
                menu.villageRestorationFund(),
                menu.villageIncidentCause(),
                menu.villageIncidentAge(),
                menu.villageAgricultureOutput(),
                menu.villageMiningOutput(),
                menu.villageTradeOutput(),
                menu.villageSimulationEnabled(),
                menu.villageVisualProgressionEnabled(),
                menu.fundAvailable(),
                menu.fundTargetedDonationsEnabled(),
                menu.fundProjectSponsorshipEnabled());
    }

    private Component newsHeadline(
            VillageDashboardPolicy.Snapshot snapshot,
            VillageDashboardPolicy.BulletinKind kind) {
        return switch (kind) {
            case NO_VILLAGE -> tr("news.local.no_village.headline");
            case SIMULATION_PAUSED -> tr("news.local.simulation_paused.headline");
            case RESTORATION -> tr("news.local.restoration.headline");
            case RECOVERY -> tr("news.local.recovery.headline");
            case INCIDENT -> tr(
                    "news.local.incident.headline",
                    incidentLabel(snapshot.incidentCause()));
            case HARDSHIP -> tr("news.local.hardship.headline");
            case SECURITY -> tr("news.local.security.headline");
            case FOOD -> tr("news.local.food.headline");
            case HOUSING -> tr("news.local.housing.headline");
            case PROJECT -> tr(
                    "news.local.project.headline",
                    projectLabel(snapshot.projectType()));
            case DEVELOPMENT_PAUSED -> tr("news.local.development_paused.headline");
            case PROSPERITY -> tr("news.local.prosperity.headline");
            case STEADY -> tr("news.local.steady.headline");
        };
    }

    private Component newsArticle(
            VillageDashboardPolicy.Snapshot snapshot,
            VillageDashboardPolicy.BulletinKind kind) {
        return switch (kind) {
            case NO_VILLAGE -> tr("news.local.no_village.article");
            case SIMULATION_PAUSED -> tr("news.local.simulation_paused.article");
            case RESTORATION -> tr(
                    "news.local.restoration.article",
                    lifecycleLabel(snapshot.lifecycle()),
                    decimal(snapshot.restorationFund()),
                    decimal(VillageProsperityEngine.RESTORATION_EMERALD_TARGET));
            case RECOVERY -> tr(
                    "news.local.recovery.article",
                    snapshot.population(),
                    snapshot.housing(),
                    decimal(snapshot.prosperity()),
                    decimal(snapshot.safety()));
            case INCIDENT -> tr(
                    "news.local.incident.article",
                    incidentAgeLabel(snapshot.incidentAgeDays()),
                    decimal(snapshot.safety()),
                    lifecycleLabel(snapshot.lifecycle()));
            case HARDSHIP -> tr(
                    "news.local.hardship.article",
                    snapshot.population(),
                    decimal(snapshot.prosperity()),
                    decimal(snapshot.safety()));
            case SECURITY -> tr(
                    "news.local.security.article",
                    decimal(snapshot.safety()),
                    snapshot.population());
            case FOOD -> tr(
                    "news.local.food.article",
                    decimal(snapshot.food()),
                    decimal(snapshot.population()
                            * VillageDashboardPolicy.GROWTH_FOOD_PER_RESIDENT),
                    snapshot.population());
            case HOUSING -> tr(
                    "news.local.housing.article",
                    snapshot.population(),
                    snapshot.housing());
            case PROJECT -> tr(
                    snapshot.projectPlanning()
                            ? "news.local.project.article_planning"
                            : "news.local.project.article_building",
                    projectLabel(snapshot.projectType()),
                    decimal(snapshot.projectProgressPercent()),
                    snapshot.projectBacklog());
            case DEVELOPMENT_PAUSED -> tr(
                    snapshot.projectPlanning()
                            ? "news.local.development_paused.article_planning"
                            : "news.local.development_paused.article_building",
                    projectLabel(snapshot.projectType()),
                    decimal(snapshot.projectProgressPercent()));
            case PROSPERITY -> tr(
                    "news.local.prosperity.article",
                    snapshot.developmentTier(),
                    decimal(snapshot.prosperity()),
                    decimal(snapshot.agricultureOutput()),
                    decimal(snapshot.miningOutput()),
                    decimal(snapshot.tradeOutput()));
            case STEADY -> tr(
                    "news.local.steady.article",
                    decimal(snapshot.prosperity()),
                    decimal(snapshot.safety()),
                    snapshot.population(),
                    snapshot.housing());
        };
    }

    private Component prosperityGuidance(
            VillageDashboardPolicy.Snapshot snapshot,
            VillageDashboardPolicy.ProsperityGuidance guidance) {
        return switch (guidance) {
            case FIND_VILLAGE -> tr("news.tip.prosperity.find_village");
            case SIMULATION_DISABLED -> tr("news.tip.prosperity.simulation_disabled");
            case RESTORE_WITH_FUND -> tr(
                    "news.tip.prosperity.restore",
                    decimal(snapshot.restorationFund()),
                    decimal(VillageProsperityEngine.RESTORATION_EMERALD_TARGET));
            case RESTORE_UNAVAILABLE -> tr("news.tip.prosperity.restore_unavailable");
            case TARGET_FOOD -> tr("news.tip.prosperity.food_targeted");
            case FOOD_UNTARGETED -> tr("news.tip.prosperity.food_untargeted");
            case TARGET_HOUSING -> tr("news.tip.prosperity.housing_targeted");
            case HOUSING_UNTARGETED -> tr("news.tip.prosperity.housing_untargeted");
            case SAFETY_BLOCKS_GROWTH -> tr(
                    "news.tip.prosperity.safety",
                    decimal(VillageDashboardPolicy.GROWTH_SAFETY_THRESHOLD));
            case SPONSOR_PROJECT -> tr(
                    "news.tip.prosperity.sponsor",
                    projectLabel(snapshot.projectType()));
            case SUPPORT_FOUNDATIONS -> tr("news.tip.prosperity.foundations");
            case SUPPORT_FOUNDATIONS_UNTARGETED ->
                    tr("news.tip.prosperity.foundations_untargeted");
            case MAINTAIN_FOUNDATIONS -> tr(
                    "news.tip.prosperity.maintain",
                    decimal(VillageDashboardPolicy.GROWTH_SAFETY_THRESHOLD));
        };
    }

    private Component safetyGuidance(
            VillageDashboardPolicy.Snapshot snapshot,
            VillageDashboardPolicy.SafetyGuidance guidance) {
        return switch (guidance) {
            case FIND_VILLAGE -> tr("news.tip.safety.find_village");
            case SIMULATION_DISABLED -> tr("news.tip.safety.simulation_disabled");
            case PROTECT_RECOVERY -> tr("news.tip.safety.recovery");
            case RECENT_INCIDENT -> tr(
                    "news.tip.safety.incident",
                    incidentAgeLabel(snapshot.incidentAgeDays()));
            case TARGET_SECURITY -> tr("news.tip.safety.security_targeted");
            case SECURITY_UNTARGETED -> tr("news.tip.safety.security_untargeted");
            case MAINTAIN_SECURITY -> tr("news.tip.safety.maintain");
        };
    }

    private static ItemStack newsIcon(
            VillageDashboardPolicy.Snapshot snapshot,
            VillageDashboardPolicy.BulletinKind kind) {
        return switch (kind) {
            case NO_VILLAGE -> new ItemStack(Items.MAP);
            case SIMULATION_PAUSED -> new ItemStack(Items.CLOCK);
            case RESTORATION -> new ItemStack(Items.GOLDEN_APPLE);
            case RECOVERY, STEADY -> new ItemStack(Items.BELL);
            case INCIDENT -> new ItemStack(Items.IRON_SWORD);
            case HARDSHIP -> new ItemStack(Items.CRACKED_STONE_BRICKS);
            case SECURITY -> new ItemStack(Items.SHIELD);
            case FOOD -> new ItemStack(Items.BREAD);
            case HOUSING -> new ItemStack(Items.BED.red());
            case PROJECT -> projectIcon(snapshot.projectType());
            case DEVELOPMENT_PAUSED -> new ItemStack(Items.BARRIER);
            case PROSPERITY -> new ItemStack(Items.EMERALD);
        };
    }

    private static ItemStack projectIcon(VillageProsperityEngine.ProjectType type) {
        if (type == null) {
            return new ItemStack(Items.SCAFFOLDING);
        }
        return new ItemStack(switch (type) {
            case COTTAGE, HOUSE, INN -> Items.OAK_DOOR;
            case WAREHOUSE -> Items.CHEST;
            case MINE_ENTRANCE -> Items.IRON_PICKAXE;
            case MARKET_SQUARE -> Items.EMERALD;
            case SMITHY -> Items.ANVIL;
            case GRANARY -> Items.HAY_BLOCK;
            case GUARD_POST -> Items.SHIELD;
            case EXCHANGE_HALL -> Items.EMERALD_BLOCK;
        });
    }

    private static int newsColor(VillageDashboardPolicy.Tone tone) {
        return switch (tone) {
            case MUTED -> MUTED;
            case CAUTION -> GOLD;
            case DANGER -> NEGATIVE;
            case POSITIVE -> POSITIVE;
        };
    }

    private static VillageProsperityEngine.ProjectType villageProjectType(int ordinal) {
        return ordinal < 0 || ordinal >= VillageProsperityEngine.ProjectType.values().length
                ? null : VillageProsperityEngine.ProjectType.values()[ordinal];
    }

    private static Component projectLabel(VillageProsperityEngine.ProjectType type) {
        return type == null
                ? tr("village.project.none")
                : tr("village.project." + type.name().toLowerCase(Locale.ROOT));
    }

    private static Component lifecycleLabel(VillageProsperityEngine.Lifecycle lifecycle) {
        return tr("village.lifecycle." + lifecycle.name().toLowerCase(Locale.ROOT));
    }

    private static Component incidentLabel(VillageProsperityEngine.IncidentCause cause) {
        return tr("village.incident." + cause.name().toLowerCase(Locale.ROOT));
    }

    private static Component incidentAgeLabel(int days) {
        return days == 0 ? tr("village.news.today") : tr("village.news.days_ago", days);
    }

    private static String decimal(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
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
        drawTextWithin(graphics, tr("payment.available", money(menu.spendingPower())),
                16, BankerScreenLayout.FUND_NOTICE_Y + 13, 286, TEXT, false);
        drawTextWithin(graphics,
                tr("fund.cash_flow", money(menu.donationDraft()),
                        money(menu.bankCashAfterSpending(menu.donationDraft()))),
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

    private boolean showingYesterday() { return menu.historyRangeIndex()==0&&menu.historyYesterday(); }

    private String historyRangeLabel() {
        return new String[]{"1d","30d","90d","1y","3y","5y","10y","All"}[menu.historyRangeIndex()];
    }

    private void drawInvestmentChart(GuiGraphicsExtractor g,int x,int y,int width,int height,int mouseX,int mouseY) {
        g.fill(x,y,x+width,y+height,PANEL_DARK);g.outline(x,y,width,height,0xFF456B5A);
        var snapshot=menu.marketDisplay();
        if(snapshot==null||!snapshot.selected().equals(menu.selectedAsset().ticker())){
            drawTextWithin(g,Component.literal("Loading server quotes..."),x+4,y+20,width-8,MUTED,false);return;
        }
        var series=snapshot.series().get(menu.historyRangeIndex());var values=series.values();
        boolean intraday=menu.historyRangeIndex()==0;
        String available="History available since Day "+series.availableDay()+(series.availableSlot()>0?" (partial day)":"");
        if(values.isEmpty()){
            drawTextWithin(g,Component.literal("No recorded session yet"),x+4,y+15,width-8,MUTED,false);
            return;
        }
        double min=values.stream().mapToDouble(Double::doubleValue).min().orElse(0),max=values.stream().mapToDouble(Double::doubleValue).max().orElse(1);
        double pad=Math.max(max*0.00005,(max-min)*0.08);min-=pad;max+=pad;
        int bottom=y+height-14,top=y+13,plotHeight=Math.max(1,bottom-top);
        int baseline=bottom-(int)(plotHeight*(series.reference()-min)/(max-min));
        g.fill(x+2,baseline,x+width-2,baseline+1,0xFF456B5A);
        double change=values.getLast()/series.reference()-1;int color=change>0?POSITIVE:change<0?NEGATIVE:MUTED;
        for(int i=0;i<values.size();i++){
            int px=x+3+(int)Math.round(series.positions().get(i)*(width-7));
            int py=bottom-(int)Math.round(plotHeight*(values.get(i)-min)/(max-min));
            if(i==0)g.fill(px,py,px+2,py+2,color);
            else {
                int oldX=x+3+(int)Math.round(series.positions().get(i-1)*(width-7));
                int oldY=bottom-(int)Math.round(plotHeight*(values.get(i-1)-min)/(max-min));
                drawLine(g,oldX,oldY,px,py,color);
            }
        }
        String title=intraday?(snapshot.yesterday()?"Yesterday "+signed(change*100):"Today - Live"):historyRangeLabel();
        drawTextWithin(g,Component.literal(title+" | Day "+series.firstDay()),x+4,y+3,width-8,MUTED,false);
        drawTextWithin(g,Component.literal(intraday?(series.availableSlot()==0?"Previous close: ":"First observed: ")+money(series.reference()):available),x+4,y+height-10,width-8,MUTED,false);
        if(mouseX>=x&&mouseX<x+width&&mouseY>=y&&mouseY<y+height){
            double mouse=(mouseX-x-3.0)/Math.max(1,width-7);int nearest=0;
            for(int i=1;i<values.size();i++)if(Math.abs(series.positions().get(i)-mouse)<Math.abs(series.positions().get(nearest)-mouse))nearest=i;
            double offset=series.positions().get(nearest)*Math.max(1,series.lastDay()-series.firstDay());
            long pointDay=series.firstDay()+(long)Math.floor(offset);int minutes=(360+(int)Math.round((offset-Math.floor(offset))*1440))%1440;
            String stamp="Day "+pointDay+(intraday?String.format(Locale.ROOT," %02d:%02d",minutes/60,minutes%60):"");
            GuiTooltips.show(g,font,Component.literal(stamp+"\n"+money(values.get(nearest))),tooltipMouseX,tooltipMouseY);
        }
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
                    y + height / 2 - 12, MUTED);
            drawNativeCenteredText(graphics, tr("chart.history_next_day"), x + width / 2,
                    y + height / 2 + 2, MUTED);
            if (mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height) {
                GuiTooltips.show(graphics, font, tr("tooltip.history_empty"), tooltipMouseX, tooltipMouseY);
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
            drawLine(graphics, previousX, previousY, currentX, currentY,
                    points[points.length-1]>points[0]?POSITIVE:points[points.length-1]<points[0]?NEGATIVE:MUTED);
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
            GuiTooltips.show(graphics,
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
        if (districtMap || briefingMode != 0 || (tab == BankerMenu.TAB_MARKET && marketBrowser)) return;
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
                    screenX + BankerScreenLayout.EXCHANGE_PANEL_X,
                    screenY + BankerScreenLayout.EXCHANGE_PANEL_Y,
                    BankerScreenLayout.EXCHANGE_PANEL_WIDTH,
                    BankerScreenLayout.EXCHANGE_PANEL_HEIGHT,
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
                                        menu.villageSafety())).copy().append("\n").append(tr("village.guard_bonus",
                                                menu.observedGuards(), String.format(Locale.ROOT, "%.1f", menu.guardSafetyBonus()))));
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
            case TAB_NEWS -> {
                VillageDashboardPolicy.Snapshot snapshot = villageDashboardSnapshot();
                VillageDashboardPolicy.Assessment assessment =
                        VillageDashboardPolicy.assess(snapshot);
                int guidanceHeight =
                        BankerScreenLayout.newsGuidanceBlockHeight(interfaceScale);
                int safetyY = BankerScreenLayout.newsSafetyY(interfaceScale);
                tooltipWhenHovered(
                        graphics,
                        mouseX,
                        mouseY,
                        screenX + BankerScreenLayout.NEWS_VISUAL_X,
                        screenY + BankerScreenLayout.NEWS_VISUAL_Y,
                        BankerScreenLayout.NEWS_PANEL_WIDTH
                                - (BankerScreenLayout.NEWS_VISUAL_X
                                        - BankerScreenLayout.NEWS_PANEL_X) * 2,
                        BankerScreenLayout.NEWS_DIVIDER_Y
                                - BankerScreenLayout.NEWS_VISUAL_Y,
                        Component.empty()
                                .append(newsHeadline(snapshot, assessment.bulletin()))
                                .append(Component.literal("\n"))
                                .append(newsArticle(snapshot, assessment.bulletin())));
                tooltipWhenHovered(
                        graphics,
                        mouseX,
                        mouseY,
                        screenX + BankerScreenLayout.NEWS_GUIDANCE_X,
                        screenY + BankerScreenLayout.NEWS_PROSPERITY_Y,
                        BankerScreenLayout.NEWS_GUIDANCE_WIDTH,
                        guidanceHeight,
                        prosperityGuidance(snapshot, assessment.prosperityGuidance()));
                tooltipWhenHovered(
                        graphics,
                        mouseX,
                        mouseY,
                        screenX + BankerScreenLayout.NEWS_GUIDANCE_X,
                        screenY + safetyY,
                        BankerScreenLayout.NEWS_GUIDANCE_WIDTH,
                        guidanceHeight,
                        safetyGuidance(snapshot, assessment.safetyGuidance()));
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
                screenX + BankerScreenLayout.MARKET_TITLE_X,
                screenY + BankerScreenLayout.MARKET_TITLE_Y,
                BankerScreenLayout.MARKET_TITLE_WIDTH,
                BankerScreenLayout.TEXT_HEIGHT,
                tr("tooltip.market_name", selected.ticker(), selected.name()));

        tooltipWhenHovered(
                graphics, mouseX, mouseY,
                screenX + BankerScreenLayout.MARKET_DETAIL_X,
                screenY + BankerScreenLayout.MARKET_PRICE_Y,
                BankerScreenLayout.MARKET_DETAIL_WIDTH,
                BankerScreenLayout.TEXT_HEIGHT,
                showingYesterday()?Component.literal("Current executable quote: "+exactMoney(menu.selectedAssetPrice())+" | "+dailyLabel(menu.selectedAssetIndex())+" today\nThe graph above shows yesterday, not today\'s trading price.")
                        :tr("tooltip.market_price_change", exactMoney(menu.selectedAssetPrice()), signed(menu.selectedChangePercent())));
        tooltipWhenHovered(
                graphics, mouseX, mouseY,
                screenX + BankerScreenLayout.MARKET_DETAIL_X,
                screenY + BankerScreenLayout.MARKET_HOLDING_Y,
                BankerScreenLayout.MARKET_DETAIL_WIDTH,
                BankerScreenLayout.TEXT_HEIGHT,
                tr(selected.isCommodity() ? "tooltip.market_units_exact" : "tooltip.market_holding_exact",
                        selectedQuantity(true),
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
            GuiTooltips.show(graphics, font, tooltip, tooltipMouseX, tooltipMouseY);
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
        if (menu.selectedCustomAmount() > available) return 0;
        return (int) Math.min(
                EconomyService.MAX_WHOLE_EMERALD_TRANSACTION,
                BankerScreenLayout.resolvedWholeAmount(selectedAmountPreset(), available));
    }

    private int selectedInventoryAmount(double available) {
        if (menu.selectedCustomAmount() > available
                || menu.selectedCustomAmount() > EconomyService.MAX_INVENTORY_ITEM_TRANSACTION) return 0;
        return Math.min(
                EconomyService.MAX_INVENTORY_ITEM_TRANSACTION,
                BankerScreenLayout.resolvedWholeAmount(selectedAmountPreset(), available));
    }

    private Component paymentSources(int amount) {
        var plan = menu.paymentPlan(amount);
        if (plan == null) return tr("payment.rules");
        return tr("payment.sources", exactMoney(menu.spendingPower()), plan.inventoryEmeralds(),
                menu.physicalEmeralds() - plan.inventoryEmeralds(), exactMoney(menu.bankCashAfterSpending(amount)))
                .copy().append(Component.literal("\n")).append(tr("payment.rules"));
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
        int amount = selectedWholeAmount(menu.spendingPower());
        return tr("tooltip.flow.cash_to_savings", exactMoney(amount),
                exactMoney(menu.bankCashAfterSpending(amount)),
                exactMoney(menu.savings() + amount)).copy().append(Component.literal("\n")).append(paymentSources(amount));
    }

    private Component savingsToCashPreview() {
        int amount = selectedWholeAmount(menu.savings());
        return tr("tooltip.flow.savings_to_cash", exactMoney(amount),
                exactMoney(Math.max(0.0, menu.savings() - amount)),
                exactMoney(menu.cash() + amount));
    }

    private Component marketBuySummary() {
        if (menu.spendingPower() < 1.0) return tr("market.deposit_first");
        int amount = selectedWholeAmount(menu.spendingPower());
        return tr("market.buy_summary", money(amount),
                money(menu.bankCashAfterSpending(amount)));
    }

    private Component marketBuyPreview() {
        int amount = selectedWholeAmount(menu.spendingPower());
        double executionPrice = menu.selectedAssetPrice()
                * (1.0 + EconomyEngine.TRADE_SPREAD);
        double estimatedShares = executionPrice <= 0.0
                ? 0.0 : amount / executionPrice;
        return tr(menu.selectedAsset().isCommodity() ? "tooltip.flow.buy_units" : "tooltip.flow.buy", exactMoney(amount), menu.selectedAsset().ticker(),
                exactShares(estimatedShares),
                exactMoney(menu.bankCashAfterSpending(amount))).copy().append(Component.literal("\n")).append(paymentSources(amount));
    }

    private Component marketSalePreview(double fraction) {
        double clampedFraction = Math.max(0.0, Math.min(1.0, fraction));
        double shares = menu.selectedShares() * clampedFraction;
        double proceeds = menu.selectedHoldingValue()
                * clampedFraction
                * (1.0 - EconomyEngine.TRADE_SPREAD);
        return tr(menu.selectedAsset().isCommodity() ? "tooltip.flow.sell_units" : "tooltip.flow.sell",
                shares == 0 && menu.ownsAsset(menu.selectedAssetIndex()) ? "<0.000001" : exactShares(shares), menu.selectedAsset().ticker(),
                exactMoney(proceeds), exactMoney(menu.cash() + proceeds));
    }

    private Component openCdPreview() {
        int amount = selectedWholeAmount(menu.spendingPower());
        return tr("tooltip.flow.open_cd", exactMoney(amount), menu.selectedCdTerm(),
                exactMoney(menu.bankCashAfterSpending(amount)),
                exactMoney(menu.cdValue() + amount)).copy().append(Component.literal("\n")).append(paymentSources(amount));
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
        int amount = selectedWholeAmount(menu.spendingPower());
        return tr("tooltip.flow.fund_loan", exactMoney(amount), menu.selectedLendingTerm(),
                exactMoney(menu.bankCashAfterSpending(amount)),
                exactMoney(menu.lendingValue() + amount)).copy().append(Component.literal("\n")).append(paymentSources(amount));
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
                exactMoney(menu.bankCashAfterSpending(menu.donationDraft()))).copy().append(Component.literal("\n")).append(paymentSources(menu.donationDraft()));
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

    private void drawWrappedText(
            GuiGraphicsExtractor graphics,
            Component text,
            int x,
            int y,
            int maximumWidth,
            int lineStep,
            int maximumLines,
            int color) {
        List<FormattedCharSequence> lines = font.split(
                text, BankerScreenScale.scaled(maximumWidth, interfaceScale));
        int count = Math.min(Math.max(0, maximumLines), lines.size());
        for (int index = 0; index < count; index++) {
            drawNativeText(
                    graphics,
                    lines.get(index),
                    x,
                    y + index * lineStep,
                    color,
                    false);
        }
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

    private void drawNativeText(
            GuiGraphicsExtractor graphics,
            FormattedCharSequence text,
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

    private void drawNativeCenteredTextWithin(
            GuiGraphicsExtractor graphics,
            Component text,
            int centerX,
            int y,
            int maximumWidth,
            int color) {
        Component fitted = fit(
                text,
                BankerScreenScale.scaled(maximumWidth, interfaceScale));
        drawNativeCenteredText(graphics, fitted, centerX, y, color);
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

    private String selectedQuantity(boolean exact) {
        double shares = menu.selectedShares();
        if (menu.ownsAsset(menu.selectedAssetIndex()) && shares < (exact ? 0.0000005 : 0.00005))
            return exact ? "<0.000001" : "<0.0001";
        return exact ? exactShares(shares) : compactShares(shares);
    }

    private static String money(double value) {
        if (value > 0 && value < 0.01) return exactMoney(value);
        return compactNumber(value, false, 1_000.0) + " E";
    }

    private static String signed(double value) {
        return String.format(Locale.ROOT, "%+.2f%%", value);
    }

    private static String signedMoney(double value) {
        return compactNumber(value, true, 1_000.0) + " E";
    }

    private static String exactMoney(double value) {
        return String.format(Locale.ROOT, value > 0 && value < 0.01 ? "%.6f E" : "%,.2f E",
                Double.isFinite(value) ? value : 0.0);
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
        return tr("risk." + EconomyEngine.riskBand(asset));
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
            case -10 -> tr("status.numeric_limit");
            default -> null;
        };
    }
}
