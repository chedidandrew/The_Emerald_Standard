package com.chedidandrew.emeraldstandard.client;

import com.chedidandrew.emeraldstandard.core.EconomyEngine;
import com.chedidandrew.emeraldstandard.core.EconomyState;
import com.chedidandrew.emeraldstandard.core.VillageExpansion;
import com.chedidandrew.emeraldstandard.minecraft.BankerMenu;
import java.lang.reflect.Field;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityEquipment;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;

/** Opt-in render fixtures. Uses the real dashboard without touching a player, server, or save. */
final class BankerClientChecks {
    private static final String PREFIX = "gui.the_emerald_standard.";
    private BankerClientChecks() { }

    static void verifyTooltipWrapping(Minecraft minecraft) {
        try {
            Class.forName("net.minecraft.client.multiplayer.ClientChunkCache");
            if(!com.chedidandrew.emeraldstandard.minecraft.mixin.BiomeSeedAccess.class
                    .isAssignableFrom(net.minecraft.world.level.biome.BiomeManager.class))
                throw new IllegalStateException("Client terrain seed accessor not applied");
        } catch(ClassNotFoundException e){throw new IllegalStateException(e);}
        for(int screenWidth:new int[]{180,320,640,1280}) {
            int max=Math.min(240,screenWidth-24);
            var shortLines=GuiTooltips.lines(minecraft.font,Component.literal("Day 126 00:54\n99.67 E"),screenWidth);
            if(shortLines.size()!=2)throw new IllegalStateException("Chart tooltip must have two real lines");
            Component transaction=Component.translatable(PREFIX+"tooltip.flow.buy","1,000.00 E","VILX","10.526316","9,000.00 E");
            var lines=GuiTooltips.lines(minecraft.font,transaction,screenWidth);
            if(lines.size()<2||lines.stream().anyMatch(line->minecraft.font.width(line)>max))
                throw new IllegalStateException("Financial tooltip exceeds adaptive wrap width");
        }
    }

    static void verifyTextFits(Minecraft minecraft) {
        verifyTooltipWrapping(minecraft);
        for (float scale : new float[] {0.75F, 1.0F, 1.2F}) {
            int width = BankerScreenScale.scaled(284, scale);
            for (var reason : VillageExpansion.Reason.values()) {
                checkLines(minecraft, "expansion.reason." + reason.name().toLowerCase(Locale.ROOT), width, 2);
            }
            for (var advice : com.chedidandrew.emeraldstandard.core.VillageUpkeepAdvice.Advice.values()) {
                checkLines(minecraft, "expansion.advice." + advice.name().toLowerCase(Locale.ROOT), width, 2);
            }
            for (var asset : EconomyEngine.ASSETS) {
                checkLines(minecraft, "market.type." + asset.type().name().toLowerCase(Locale.ROOT),
                        BankerScreenScale.scaled(BankerScreenLayout.MARKET_META_WIDTH, scale), 1);
                checkLines(minecraft, "market.behavior." + asset.ticker().toLowerCase(Locale.ROOT),
                        BankerScreenScale.scaled(BankerScreenLayout.MARKET_META_WIDTH, scale), 4);
            }
            for (String key : new String[] {"chart.history_building", "chart.history_next_day"})
                checkLines(minecraft, key, BankerScreenScale.scaled(188, scale), 1);
        }
    }

    private static void checkLines(Minecraft minecraft, String key, int width, int maximum) {
        Component value = Component.translatable(PREFIX + key);
        if (value.getString().equals(PREFIX + key) || minecraft.font.split(value, width).size() > maximum)
            throw new IllegalStateException("Dashboard text does not fit: " + key + " at width " + width);
    }

    static Screen fixture(boolean town, String ticker, boolean history) {
        return fixture(town ? BankerMenu.TAB_VILLAGE : BankerMenu.TAB_MARKET, ticker, history, false);
    }

    static Screen fundsFixture(int tabId) { return fixture(tabId, "VILX", false, true); }

    static Screen guardFixture() { return fixture(BankerMenu.TAB_VILLAGE, "VILX", false, false, true); }

    static Screen districtMapFixture(boolean hover) {
        return districtMapFixture(hover ? 1 : 0);
    }

    static Screen districtMapFixture(int hoverMode) {
        if (!Boolean.getBoolean("the_emerald_standard.clientSmoke")) throw new IllegalStateException("Smoke only");
        try {
            Inventory inventory = new Inventory(null, new EntityEquipment());
            BankerMenu menu = new BankerMenu(1, inventory);
            Field field = BankerMenu.class.getDeclaredField("data"); field.setAccessible(true);
            ContainerData data = (ContainerData) field.get(menu);
            set(data, "DATA_VILLAGE_PRESENT", 1);
            var markers = new java.util.ArrayList<com.chedidandrew.emeraldstandard.core.VillageDistrictMap.Marker>();
            var territoryState = new com.chedidandrew.emeraldstandard.core.EconomyState();
            for (int n = 1; n <= 3; n++) {
                var v = territoryState.village(new java.util.UUID(0,n));
                int x = -180+n*90, z = 120+n%2*40;
                v.centerPos = ((long)x&0x3ffffffL)<<38 | ((long)z&0x3ffffffL)<<12 | 64;
                v.organicTerritory = true;
            }
            for (var v : territoryState.villages.values())
                com.chedidandrew.emeraldstandard.core.VillageTerritory.seed(v, territoryState.villages.values());
            for (int district = 1; district <= 3; district++) {
                int cx = -180 + district * 90, cz = 120 + district % 2 * 40;
                markers.add(new com.chedidandrew.emeraldstandard.core.VillageDistrictMap.Marker(
                        cx - 64, cz - 64, cx + 64, cz + 64, 0, district, district == 1 ? 4 : 0, 18, -1, cx, cz));
                var v = territoryState.villages.get(new java.util.UUID(0,district));
                for (var r : com.chedidandrew.emeraldstandard.core.VillageTerritory.outlines(
                        com.chedidandrew.emeraldstandard.core.VillageTerritory.visible(v, territoryState.villages.values())))
                    markers.add(new com.chedidandrew.emeraldstandard.core.VillageDistrictMap.Marker(
                            r[0], r[1], r[2], r[3], 3, district, district == 1 ? 4 : 0, 0, r[4], cx, cz));
                markers.add(new com.chedidandrew.emeraldstandard.core.VillageDistrictMap.Marker(
                        cx + 18, cz - 20, cx + 18, cz - 20, 1, district, 0, -1, 0));
                var lots = v.territoryCells.stream().filter(c -> c != com.chedidandrew.emeraldstandard.core.VillageTerritory.key(cx>>4,cz>>4)
                        && c != com.chedidandrew.emeraldstandard.core.VillageTerritory.key((cx+18)>>4,(cz-20)>>4)).toList();
                for (int n = 0; n < 8; n++) {
                    long lot = lots.get(n * lots.size() / 8);
                    int x = com.chedidandrew.emeraldstandard.core.VillageTerritory.cx(lot)*16+2;
                    int z = com.chedidandrew.emeraldstandard.core.VillageTerritory.cz(lot)*16+2;
                    markers.add(new com.chedidandrew.emeraldstandard.core.VillageDistrictMap.Marker(
                            x, z, x + 10, z + 10, 2, district, n % 4, n % 4 == 0 ? 100 : 35, n % 5));
                }
            }
            var hoverSite = markers.stream().filter(m -> m.kind()==2 && m.district()==1).findFirst().orElseThrow();
            var page = new com.chedidandrew.emeraldstandard.core.VillageDistrictMap.Page(
                    0, markers.size(), 3, 2, -90, 160, markers);
            int[] encoded = com.chedidandrew.emeraldstandard.core.VillageDistrictMap.encode(page, 1);
            for (int i = 0; i < encoded.length; i++) data.set(BankerMenu.DATA_DISTRICT_MAP + i, encoded[i]);
            BankerScreen dashboard = new BankerScreen(menu, inventory, Component.translatable(PREFIX + "banker.title"));
            Field tab = BankerScreen.class.getDeclaredField("tab"); tab.setAccessible(true); tab.setInt(dashboard, BankerMenu.TAB_VILLAGE);
            Field map = BankerScreen.class.getDeclaredField("districtMap"); map.setAccessible(true); map.setBoolean(dashboard, true);
            Field viewportField = BankerScreen.class.getDeclaredField("mapViewport"); viewportField.setAccessible(true);
            DistrictMapViewport viewport = (DistrictMapViewport) viewportField.get(dashboard);
            Field terrainField = BankerScreen.class.getDeclaredField("mapTerrain"); terrainField.setAccessible(true);
            ((DistrictTerrainLayer) terrainField.get(dashboard)).fixture((x, z) -> {
                // Deterministic rendering fixture only. Native map colors are tested separately.
                if (x > 140) return 0;
                if (Math.abs(x - 28 * Math.sin(z / 42.0)) < 12) return 0xFF385EC4;
                if (Math.floorMod(z, 70) < 5) return 0xFFAA925C;
                return ((Math.floorDiv(x, 9) + Math.floorDiv(z, 9)) & 1) == 0 ? 0xFF587B38 : 0xFF6B9243;
            });
            return new Screen(Component.literal("District map render fixture")) {
                @Override public void removed() { dashboard.removed(); }
                private int hoverX() {
                    float scale = BankerScreenScale.fit(width, height, 320, 230);
                    return hoverMode > 0 ? (int) (BankerScreenScale.origin(width, 320, scale)
                            + viewport.x(hoverMode == 2 ? -90 : hoverSite.x()) * scale) : -100;
                }
                private int hoverY() {
                    float scale = BankerScreenScale.fit(width, height, 320, 230);
                    return hoverMode > 0 ? (int) (BankerScreenScale.origin(height, 230, scale)
                            + viewport.y(hoverMode == 2 ? 160 : hoverSite.z()) * scale) : -100;
                }
                @Override protected void init() {
                    dashboard.init(width, height);
                    ReaderClientChecks.press(dashboard, "+");
                    ReaderClientChecks.press(dashboard, "-");
                    ReaderClientChecks.press(dashboard, "Focus");
                    ReaderClientChecks.press(dashboard, "Fit page");
                    if (menu.districtMap().markers().size() != markers.size()) throw new IllegalStateException("Missing map markers");
                    // Exercise the same logical/pixel conversion used by drag and scroll at each GUI scale.
                    float scale = BankerScreenScale.fit(width, height, 320, 230);
                    double x = BankerScreenScale.origin(width, 320, scale) + 100 * scale;
                    double y = BankerScreenScale.origin(height, 230, scale) + 120 * scale;
                    var click = new net.minecraft.client.input.MouseButtonEvent(x, y,
                            new net.minecraft.client.input.MouseButtonInfo(0, 0));
                    if (!dashboard.mouseClicked(click, false) || !dashboard.mouseDragged(click, 12, 10)
                            || !dashboard.mouseReleased(click) || !dashboard.mouseScrolled(x, y, 0, 1))
                        throw new IllegalStateException("Map navigation was not handled");
                    ReaderClientChecks.press(dashboard, "Fit page");
                }
                @Override public void extractBackground(GuiGraphicsExtractor graphics, int x, int y, float delta) {
                    dashboard.extractBackground(graphics, hoverX(), hoverY(), delta);
                }
                @Override public void extractRenderState(GuiGraphicsExtractor graphics, int x, int y, float delta) {
                    dashboard.extractRenderState(graphics, hoverX(), hoverY(), delta);
                }
            };
        } catch (ReflectiveOperationException exception) { throw new IllegalStateException(exception); }
    }

    private static Screen fixture(int tabId, String ticker, boolean history, boolean inventoryFunds) {
        return fixture(tabId, ticker, history, inventoryFunds, false);
    }

    private static Screen fixture(int tabId, String ticker, boolean history, boolean inventoryFunds, boolean guards) {
        boolean town = tabId == BankerMenu.TAB_VILLAGE;
        if (!Boolean.getBoolean("the_emerald_standard.clientSmoke"))
            throw new IllegalStateException("Dashboard fixtures are smoke-only");
        try {
            Inventory inventory = new Inventory(null, new EntityEquipment());
            BankerMenu menu = new BankerMenu(1, inventory);
            Field dataField = BankerMenu.class.getDeclaredField("data");
            dataField.setAccessible(true);
            ContainerData data = (ContainerData) dataField.get(menu);
            set(data, "DATA_VILLAGE_PRESENT", 1);
            set(data, "DATA_VILLAGE_POPULATION", 8);
            set(data, "DATA_VILLAGE_HOUSING", 14);
            set(data, "DATA_VILLAGE_FOOD_CENTI", 4500);
            set(data, "DATA_VILLAGE_SAFETY_BPS", 7000);
            if (guards) {
                set(data, "DATA_GUARD_COUNT", 3);
                set(data, "DATA_GUARD_BONUS", 600);
                set(data, "DATA_VILLAGE_SAFETY_BPS", 7600);
                if (menu.observedGuards() != 3 || menu.guardSafetyBonus() != 6 || menu.villageSafety() != 76)
                    throw new IllegalStateException("Guard observation transport");
            }
            int expansion = index("DATA_EXPANSION");
            data.set(expansion + 1, VillageExpansion.Reason.MATURING.ordinal());
            data.set(expansion + 2, 1);
            data.set(expansion + 4, 60);
            data.set(expansion + 5, 1);
            data.set(expansion + 7, 4620);
            data.set(expansion + 8, 1350);
            data.set(expansion + 9, 2530);
            int selected = 0;
            for (int i = 0; i < EconomyEngine.ASSETS.size(); i++)
                if (EconomyEngine.ASSETS.get(i).ticker().equals(ticker)) selected = i;
            set(data, "DATA_SELECTED_ASSET", selected);
            set(data, "DATA_AMOUNT_PRESET", 3);
            if (inventoryFunds) {
                set(data, "DATA_PHYSICAL_EMERALDS", 320);
                set(data, "DATA_DONATION_DRAFT", 100);
                set(data, "DATA_VILLAGE_SIMULATION_ENABLED", 1);
                set(data, "DATA_FUND_FEATURE_FLAGS", 31);
                Field amount = BankerMenu.class.getDeclaredField("customAmount");
                amount.setAccessible(true); amount.setInt(menu, 100);
                if (menu.spendingPower() != 320 || menu.paymentPlan(100).inventoryEmeralds() != 100
                        || menu.cash() != 0 || menu.bankCashAfterSpending(100) != 0)
                    throw new IllegalStateException("Client mixed payment fixture calculation");
            }
            long quoteMicro = 100 * EconomyState.MICRO;
            data.set(index("DATA_ASSET_PRICE_BASE")+2*selected,(int)quoteMicro);
            data.set(index("DATA_ASSET_PRICE_BASE")+2*selected+1,(int)(quoteMicro>>>32));
            if (history) {
                // These are actual deterministic simulation samples, not a hand-drawn price line.
                EconomyState state = EconomyState.fresh(9823L, 0L, 0L);
                for (int i = 0; i < 365; i++) state.advanceOneDay();
                var prices = state.priceHistory.get(ticker);
                set(data, "DATA_DAY", 365);
                set(data, "DATA_REGIME", state.regime.ordinal());
                set(data, "DATA_HISTORY_COUNT", 60);
                set(data, "DATA_HISTORY_SPAN_DAYS", 365);
                set(data, "DATA_HISTORY_RANGE", 2);
                set(data, "DATA_CASH_LOW", (int) (256 * EconomyState.MICRO));
                for (int i = 0; i < 60; i++) data.set(index("DATA_HISTORY_BASE") + i,
                        (int) Math.round(prices.get(i * (prices.size() - 1) / 59) * 100));
                quoteMicro = Math.round(state.prices.get(ticker) * EconomyState.MICRO);
                data.set(index("DATA_ASSET_PRICE_BASE")+2*selected,(int)quoteMicro);
                data.set(index("DATA_ASSET_PRICE_BASE")+2*selected+1,(int)(quoteMicro>>>32));
                set(data, "DATA_SELECTED_CHANGE_BPS",
                        (int) Math.round((prices.getLast() / prices.getFirst() - 1.0) * 10000));
            }
            BankerScreen dashboard = new BankerScreen(menu, inventory, Component.translatable(PREFIX + "banker.title"));
            Field tab = BankerScreen.class.getDeclaredField("tab"); tab.setAccessible(true);
            tab.setInt(dashboard, tabId);
            Field details = BankerScreen.class.getDeclaredField("expansionDetails"); details.setAccessible(true);
            details.setBoolean(dashboard, town && !guards);
            // Suppress only the vanilla container lifecycle that requires a connected player.
            // Layout, widgets, labels and rendering are the unmodified production screen.
            return new Screen(Component.literal("Dashboard render fixture")) {
                private int hoverX() {
                    float scale = BankerScreenScale.fit(width, height, 320, 230);
                    return guards ? (int) (BankerScreenScale.origin(width, 320, scale) + 30 * scale) : -100;
                }
                private int hoverY() {
                    float scale = BankerScreenScale.fit(width, height, 320, 230);
                    return guards ? (int) (BankerScreenScale.origin(height, 230, scale) + 100 * scale) : -100;
                }
                @Override protected void init() {
                    dashboard.init(width, height);
                    if (inventoryFunds) {
                        String key = tabId == BankerMenu.TAB_FUND ? "action.support_village"
                                : tabId == BankerMenu.TAB_MARKET ? "action.invest" : "action.cash_to_savings";
                        String label = Component.translatable(PREFIX + key).getString();
                        boolean enabled = dashboard.children().stream().anyMatch(child ->
                                child instanceof net.minecraft.client.gui.components.Button button
                                        && button.getMessage().getString().equals(label) && button.active);
                        if (!enabled) throw new IllegalStateException("Inventory-only action disabled: " + label);
                    }
                }
                @Override public void extractBackground(GuiGraphicsExtractor graphics, int x, int y, float delta) {
                    dashboard.extractBackground(graphics, hoverX(), hoverY(), delta);
                }
                @Override public void extractRenderState(GuiGraphicsExtractor graphics, int x, int y, float delta) {
                    dashboard.extractRenderState(graphics, hoverX(), hoverY(), delta);
                }
            };
        } catch (ReflectiveOperationException exception) { throw new IllegalStateException(exception); }
    }

    private static int index(String name) throws ReflectiveOperationException {
        Field field = BankerMenu.class.getDeclaredField(name); field.setAccessible(true);
        return field.getInt(null);
    }
    private static void set(ContainerData data, String name, int value) throws ReflectiveOperationException {
        data.set(index(name), value);
    }
}
