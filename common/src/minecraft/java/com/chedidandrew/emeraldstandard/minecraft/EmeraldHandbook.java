package com.chedidandrew.emeraldstandard.minecraft;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.AtlasIds;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.objects.AtlasSprite;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.WrittenBookItem;
import net.minecraft.world.item.component.WrittenBookContent;

/** The authored, illustrated guide handed to each player on their first world join. */
public final class EmeraldHandbook {
    public static final Identifier HANDBOOK_ID = Identifier.fromNamespaceAndPath(
            BankerProfessionSupport.MOD_ID, "handbook");
    public static final ResourceKey<Item> HANDBOOK_ITEM_KEY =
            ResourceKey.create(Registries.ITEM, HANDBOOK_ID);
    public static final int PAGE_COUNT = 61;

    private static final String KEY = "book.the_emerald_standard.handbook.";
    private static final int CONTENTS_BANKING_PAGE = 3;
    private static final int CONTENTS_VILLAGE_PAGE = 4;
    private static final WrittenBookContent CONTENT = createContent();

    private EmeraldHandbook() {
    }

    /** Loader-neutral factory used by both Fabric and NeoForge registration. */
    public static Item createItem() {
        return new WrittenBookItem(new Item.Properties()
                .setId(HANDBOOK_ITEM_KEY)
                .stacksTo(1)
                .rarity(Rarity.UNCOMMON)
                .component(DataComponents.WRITTEN_BOOK_CONTENT, CONTENT));
    }

    /** Creates the registered guide stack, or an empty stack if registration failed. */
    public static ItemStack createStack() {
        Item item = BuiltInRegistries.ITEM.getValue(HANDBOOK_ID);
        if (item == null || item == Items.AIR) {
            return ItemStack.EMPTY;
        }
        return new ItemStack(item);
    }

    public static boolean isHandbook(ItemStack stack) {
        return stack != null
                && !stack.isEmpty()
                && HANDBOOK_ID.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()));
    }

    public static WrittenBookContent content() {
        return CONTENT;
    }

    private static WrittenBookContent createContent() {
        List<Filterable<Component>> pages = buildPages().stream()
                .map(Filterable::passThrough)
                .toList();
        return new WrittenBookContent(
                Filterable.passThrough("The Emerald Standard"),
                "Village Bankers' Guild",
                0,
                pages,
                true);
    }

    private static List<Component> buildPages() {
        List<Component> pages = new ArrayList<>(PAGE_COUNT);
        pages.add(coverPage());
        pages.add(riskKeyPage());
        pages.add(contentsPageOne());
        pages.add(contentsPageTwo());
        pages.add(sectionPage("first_steps", 5,
                blockIcon("exchange_desk_front", "block.the_emerald_standard.exchange_desk", "[Desk]"),
                itemIcon("emerald", "item.minecraft.emerald", "[E]")));
        pages.add(sectionPage("first_deposit", 6,
                itemIcon("emerald", "item.minecraft.emerald", "[E]"),
                itemIcon("written_book", "item.the_emerald_standard.handbook", "[Book]")));
        pages.add(sectionPage("amounts", 7,
                itemIcon("emerald", "item.minecraft.emerald", "[E]"),
                itemIcon("clock_00", "item.minecraft.clock", "[Clock]")));
        pages.add(riskCompassPage());
        pages.add(tabsPage());
        pages.add(sectionPage("account", 10,
                itemIcon("emerald", "item.minecraft.emerald", "[E]"),
                blockIcon("barrel_side", "block.minecraft.barrel", "[Storage]")));
        pages.add(sectionPage("market", 11,
                itemIcon("emerald", "item.minecraft.emerald", "[E]"),
                itemIcon("gold_ingot", "item.minecraft.gold_ingot", "[Gold]")));
        pages.add(sectionPage("investments", 12,
                itemIcon("redstone", "item.minecraft.redstone", "[R]"),
                itemIcon("ender_pearl", "item.minecraft.ender_pearl", "[Ender]"),
                itemIcon("potion", "item.minecraft.potion", "[Potion]"),
                itemIcon("minecart", "item.minecraft.minecart", "[Cart]")));
        pages.add(moneyRoutesPage());
        pages.add(sectionPage("savings_cd", 14,
                itemIcon("emerald", "item.minecraft.emerald", "[E]"),
                itemIcon("clock_00", "item.minecraft.clock", "[Clock]"),
                itemIcon("paper", "item.minecraft.paper", "[CD]")));
        pages.add(sectionPage("lending", 15,
                itemIcon("emerald", "item.minecraft.emerald", "[E]"),
                itemIcon("villager_spawn_egg", "item.minecraft.villager_spawn_egg", "[Villager]")));
        pages.add(sectionPage("trade", 16,
                itemIcon("diamond", "item.minecraft.diamond", "[D]"),
                itemIcon("gold_ingot", "item.minecraft.gold_ingot", "[G]"),
                itemIcon("netherite_ingot", "item.minecraft.netherite_ingot", "[N]"),
                itemIcon("emerald", "item.minecraft.emerald", "[E]")));
        pages.add(sectionPage("trade_diamond_gold", 17,
                itemIcon("diamond", "item.minecraft.diamond", "[D]"),
                blockIcon("diamond_ore", "block.minecraft.diamond_ore", "[Ore]"),
                itemIcon("gold_ingot", "item.minecraft.gold_ingot", "[G]"),
                itemIcon("raw_gold", "item.minecraft.raw_gold", "[Raw]")));
        pages.add(sectionPage("trade_netherite_emerald", 18,
                blockIcon("ancient_debris_side", "block.minecraft.ancient_debris", "[Debris]"),
                itemIcon("netherite_ingot", "item.minecraft.netherite_ingot", "[N]"),
                itemIcon("emerald", "item.minecraft.emerald", "[E]"),
                blockIcon("emerald_ore", "block.minecraft.emerald_ore", "[Ore]")));
        pages.add(sectionPage("village", 19,
                itemIcon("bell", "block.minecraft.bell", "[Bell]"),
                itemIcon("bread", "item.minecraft.bread", "[Food]"),
                blockIcon("white_bed_head_up", "block.minecraft.white_bed", "[Bed]")));
        pages.add(sectionPage("grow", 20,
                itemIcon("bread", "item.minecraft.bread", "[Food]"),
                blockIcon("white_bed_head_up", "block.minecraft.white_bed", "[Bed]"),
                itemIcon("iron_chestplate", "item.minecraft.iron_chestplate", "[Safety]")));
        pages.add(sectionPage("safety", 21,
                itemIcon("iron_chestplate", "item.minecraft.iron_chestplate", "[Armor]"),
                itemIcon("bell", "block.minecraft.bell", "[Bell]")));
        pages.add(sectionPage("needs", 22,
                itemIcon("bread", "item.minecraft.bread", "[Food]"),
                blockIcon("white_bed_head_up", "block.minecraft.white_bed", "[Bed]"),
                itemIcon("iron_chestplate", "item.minecraft.iron_chestplate", "[Safety]")));
        pages.add(sectionPage("collapse", 23,
                itemIcon("totem_of_undying", "item.minecraft.totem_of_undying", "[Recovery]"),
                itemIcon("emerald", "item.minecraft.emerald", "[E]")));
        pages.add(sectionPage("projects", 24,
                blockIcon("oak_planks", "block.minecraft.oak_planks", "[Wood]"),
                blockIcon("stone_bricks", "block.minecraft.stone_bricks", "[Stone]"),
                itemIcon("iron_ingot", "item.minecraft.iron_ingot", "[Iron]")));
        pages.add(sectionPage("planning_building", 25,
                itemIcon("paper", "item.minecraft.paper", "[Plan]"),
                blockIcon("bricks", "block.minecraft.bricks", "[Build]")));
        pages.add(sectionPage("terrain", 26,
                blockIcon("dirt_path_top", "block.minecraft.dirt_path", "[Path]"),
                blockIcon("oak_planks", "block.minecraft.oak_planks", "[Wood]"),
                blockIcon("stone_bricks", "block.minecraft.stone_bricks", "[Stone]")));
        pages.add(sectionPage("damage", 27,
                blockIcon("cracked_stone_bricks", "block.minecraft.cracked_stone_bricks", "[Damage]"),
                itemIcon("barrier", "item.minecraft.barrier", "[Stop]")));
        pages.add(sectionPage("fund", 28,
                itemIcon("emerald", "item.minecraft.emerald", "[E]"),
                blockIcon("emerald_block", "block.minecraft.emerald_block", "[Fund]"),
                blockIcon("barrel_side", "block.minecraft.barrel", "[Treasury]")));
        pages.add(sectionPage("activity_news", 29,
                itemIcon("book", "item.minecraft.book", "[Log]"),
                itemIcon("paper", "item.minecraft.paper", "[News]")));
        pages.add(sectionPage("time", 30,
                itemIcon("clock_00", "item.minecraft.clock", "[Clock]"),
                itemIcon("redstone", "item.minecraft.redstone", "[Debug]")));
        pages.add(sectionPage("recovery", 31,
                blockIcon("barrel_side", "block.minecraft.barrel", "[Inventory]"),
                itemIcon("emerald", "item.minecraft.emerald", "[E]")));
        pages.add(sectionPage("bank_access", 32,
                blockIcon("exchange_desk_front", "block.the_emerald_standard.exchange_desk", "[Desk]"),
                itemIcon("villager_spawn_egg", "item.minecraft.villager_spawn_egg", "[Banker]")));
        pages.add(sectionPage("paused", 33,
                itemIcon("clock_00", "item.minecraft.clock", "[Wait]"),
                itemIcon("barrier", "item.minecraft.barrier", "[Blocked]")));
        pages.add(exchangeDeskRecipePage());
        pages.add(handbookRecipePage());
        pages.add(sectionPage("glossary_money", 36,
                itemIcon("emerald", "item.minecraft.emerald", "[E]"),
                blockIcon("barrel_side", "block.minecraft.barrel", "[Cash]")));
        pages.add(sectionPage("glossary_rates_1", 37,
                itemIcon("paper", "item.minecraft.paper", "[Terms]"),
                itemIcon("clock_00", "item.minecraft.clock", "[Term]")));
        pages.add(sectionPage("glossary_rates_2", 38,
                itemIcon("paper", "item.minecraft.paper", "[Terms]"),
                itemIcon("emerald", "item.minecraft.emerald", "[Return]")));
        pages.add(sectionPage("glossary_market_1", 39,
                itemIcon("gold_ingot", "item.minecraft.gold_ingot", "[Asset]"),
                itemIcon("minecart", "item.minecraft.minecart", "[Ticker]")));
        pages.add(sectionPage("glossary_market_2", 40,
                itemIcon("gold_ingot", "item.minecraft.gold_ingot", "[Quote]"),
                itemIcon("redstone", "item.minecraft.redstone", "[Motion]")));
        pages.add(sectionPage("glossary_performance_1", 41,
                itemIcon("emerald", "item.minecraft.emerald", "[Value]"),
                itemIcon("paper", "item.minecraft.paper", "[Record]")));
        pages.add(sectionPage("glossary_performance_2", 42,
                itemIcon("emerald", "item.minecraft.emerald", "[Value]"),
                itemIcon("paper", "item.minecraft.paper", "[Record]")));
        pages.add(sectionPage("glossary_fund_1", 43,
                blockIcon("emerald_block", "block.minecraft.emerald_block", "[Fund]"),
                blockIcon("barrel_side", "block.minecraft.barrel", "[Reserve]")));
        pages.add(sectionPage("glossary_fund_2", 44,
                blockIcon("emerald_block", "block.minecraft.emerald_block", "[Fund]"),
                blockIcon("barrel_side", "block.minecraft.barrel", "[Reserve]")));
        pages.add(sectionPage("glossary_village_1", 45,
                itemIcon("bell", "block.minecraft.bell", "[Village]"),
                blockIcon("white_bed_head_up", "block.minecraft.white_bed", "[Housing]")));
        pages.add(sectionPage("glossary_village_2", 46,
                itemIcon("bell", "block.minecraft.bell", "[Village]"),
                blockIcon("bricks", "block.minecraft.bricks", "[Building]")));

        // Append, preserving every existing fallback page link.
        pages.add(sectionPage("specialists", 47,
                itemIcon("gold_ingot", "item.minecraft.gold_ingot", "[Gold]")));
        pages.add(sectionPage("creative_content", 48,
                itemIcon("villager_spawn_egg", "item.the_emerald_standard.banker_spawn_egg", "[Egg]")));
        pages.add(sectionPage("recipe_fence", 49,
                itemIcon("barrier", "block.the_emerald_standard.construction_fence", "[Fence]")));
        pages.add(sectionPage("construction_crews", 50,
                itemIcon("villager_spawn_egg", "item.the_emerald_standard.builder_spawn_egg", "[Crew]")));

        // The appended news/growth pages preserve all earlier page links.
            pages.add(sectionPage("growth_targets",51,itemIcon("emerald","item.minecraft.emerald","[Risk]")));
            pages.add(sectionPage("newspaper",52,itemIcon("paper","item.minecraft.paper","[News]")));
            pages.add(sectionPage("player_news",53,itemIcon("bread","item.minecraft.bread","[Local]")));
            pages.add(sectionPage("recipe_newspaper",54,itemIcon("paper","item.minecraft.paper","[Craft]")));
            pages.add(sectionPage("browser_help",55,itemIcon("emerald","item.minecraft.emerald","[Market]")));
            pages.add(sectionPage("town_report",56,itemIcon("paper","item.minecraft.paper","[Town]")));
            pages.add(sectionPage("fund_receipt",57,itemIcon("paper","item.minecraft.paper","[Fund]")));
        pages.add(sectionPage("live_market",58,itemIcon("clock_00","item.minecraft.clock","[Live]")));
        pages.add(sectionPage("market_ranges",59,itemIcon("paper","item.minecraft.paper","[Chart]")));
        pages.add(sectionPage("market_clock",60,itemIcon("clock_00","item.minecraft.clock","[Time]")));
        pages.add(sectionPage("construction_safety",61,blockIcon("bricks","block.minecraft.bricks","[Repair]")));
        if (pages.size() != PAGE_COUNT) {
            throw new IllegalStateException("Handbook page index drift: " + pages.size());
        }
        return List.copyOf(pages);
    }

    private static Component coverPage() {
        return title("cover")
                .append("\n\n")
                .append(iconRow(
                        itemIcon("emerald", "item.minecraft.emerald", "[E]"),
                        itemIcon("written_book", "item.the_emerald_standard.handbook", "[Book]"),
                        itemIcon("bell", "block.minecraft.bell", "[Village]")))
                .append("\n\n")
                .append(body("cover"))
                .append("\n\n")
                .append(pageLink("open_contents", CONTENTS_BANKING_PAGE));
    }

    private static Component riskKeyPage() {
        return title("how_to_read")
                .append("\n")
                .append(body("how_to_read"))
                .append("\n")
                .append(riskLine("safe", ChatFormatting.DARK_GREEN))
                .append("\n")
                .append(riskLine("locked", ChatFormatting.GOLD))
                .append("\n")
                .append(riskLine("risk", ChatFormatting.RED))
                .append("\n")
                .append(riskLine("gift", ChatFormatting.DARK_RED))
                .append("\n\n")
                .append(navigation(2));
    }

    private static Component riskCompassPage() {
        return title("risk_compass")
                .append("\n")
                .append(body("risk_compass"))
                .append("\n\n")
                .append(navigation(8));
    }

    private static Component contentsPageOne() {
        MutableComponent page = title("contents_banking").append("\n");
        addContentsLine(page, "first_steps", 5);
        addContentsLine(page, "risk", 8);
        addContentsLine(page, "dashboard", 9);
        addContentsLine(page, "market", 11);
        addContentsLine(page, "banking", 14);
        addContentsLine(page, "trade", 16);
        page.append("\n").append(pageLink("next_contents", CONTENTS_VILLAGE_PAGE));
        return page;
    }

    private static Component contentsPageTwo() {
        MutableComponent page = title("contents_village").append("\n");
        addContentsLine(page, "village", 19);
        addContentsLine(page, "projects", 24);
        addContentsLine(page, "fund", 28);
        addContentsLine(page, "news", 29);
        addContentsLine(page, "help", 30);
        addContentsLine(page, "crafting", 34);
        addContentsLine(page, "glossary", 36);
        page.append("\n").append(pageLink("previous_contents", CONTENTS_BANKING_PAGE));
        return page;
    }

    private static Component tabsPage() {
        MutableComponent page = title("tabs").append("\n");
        addContentsLine(page, "account", 10);
        addContentsLine(page, "market", 11);
        addContentsLine(page, "banking", 14);
        addContentsLine(page, "lending", 15);
        addContentsLine(page, "trade", 16);
        addContentsLine(page, "village", 19);
        addContentsLine(page, "fund", 28);
        addContentsLine(page, "activity_news", 29);
        page.append("\n").append(navigation(9));
        return page;
    }

    private static Component moneyRoutesPage() {
        return title("money_routes")
                .append("\n")
                .append(iconRow(
                        itemIcon("emerald", "item.minecraft.emerald", "[Items]"),
                        blockIcon("barrel_side", "block.minecraft.barrel", "[Cash]"),
                        itemIcon("paper", "item.minecraft.paper", "[Products]")))
                .append("\n")
                .append(body("money_routes"))
                .append("\n\n")
                .append(navigation(13));
    }

    private static Component exchangeDeskRecipePage() {
        MutableComponent page = title("recipe_desk").append("\n");
        page.append(recipeRow(null,
                itemIcon("emerald", "item.minecraft.emerald", "[E]"), null));
        page.append("\n").append(recipeRow(
                itemIcon("leather", "item.minecraft.leather", "[L]"),
                itemIcon("book", "item.minecraft.book", "[B]"),
                itemIcon("leather", "item.minecraft.leather", "[L]")));
        page.append("\n").append(recipeRow(
                blockIcon("oak_planks", "block.minecraft.oak_planks", "[P]"),
                blockIcon("oak_planks", "block.minecraft.oak_planks", "[P]"),
                blockIcon("oak_planks", "block.minecraft.oak_planks", "[P]")));
        page.append("\n").append(body("recipe_desk"));
        page.append("\n\n").append(navigation(34));
        return page;
    }

    private static Component handbookRecipePage() {
        return title("recipe_book")
                .append("\n\n")
                .append(iconRow(
                        itemIcon("book", "item.minecraft.book", "[B]"),
                        Component.literal("+").withStyle(ChatFormatting.DARK_GRAY),
                        itemIcon("emerald", "item.minecraft.emerald", "[E]"),
                        Component.literal("=").withStyle(ChatFormatting.DARK_GRAY),
                        itemIcon("written_book", "item.the_emerald_standard.handbook", "[Book]")))
                .append("\n\n")
                .append(body("recipe_book"))
                .append("\n\n")
                .append(navigation(35));
    }

    private static Component sectionPage(String pageKey, int pageNumber, Component... icons) {
        MutableComponent page = title(pageKey);
        if (icons.length > 0) {
            page.append("\n").append(iconRow(icons));
        }
        page.append("\n").append(body(pageKey));
        page.append("\n\n").append(navigation(pageNumber));
        return page;
    }

    private static MutableComponent title(String pageKey) {
        return Component.translatable(KEY + pageKey + ".title")
                .withStyle(ChatFormatting.DARK_GREEN, ChatFormatting.BOLD);
    }

    private static MutableComponent body(String pageKey) {
        return Component.translatable(KEY + pageKey + ".body")
                .withStyle(ChatFormatting.BLACK);
    }

    private static MutableComponent riskLine(String key, ChatFormatting color) {
        return Component.translatable(KEY + "risk." + key + ".label")
                .withStyle(color, ChatFormatting.BOLD)
                .append(Component.literal(" "))
                .append(Component.translatable(KEY + "risk." + key + ".body")
                        .withStyle(ChatFormatting.BLACK));
    }

    private static void addContentsLine(MutableComponent page, String label, int targetPage) {
        page.append(pageLink("toc." + label, targetPage)).append("\n");
    }

    private static MutableComponent navigation(int pageNumber) {
        int contentsPage = pageNumber <= 18
                ? CONTENTS_BANKING_PAGE
                : CONTENTS_VILLAGE_PAGE;
        MutableComponent navigation = pageLink("nav.contents", contentsPage);
        if (pageNumber > 1) {
            navigation.append("  ").append(pageLink("nav.previous", pageNumber - 1));
        }
        if (pageNumber < PAGE_COUNT) {
            navigation.append("  ").append(pageLink("nav.next", pageNumber + 1));
        }
        return navigation;
    }

    private static MutableComponent pageLink(String key, int targetPage) {
        return Component.translatable(KEY + key)
                .withStyle(style -> style
                        .withColor(ChatFormatting.DARK_GREEN)
                        .withUnderlined(true)
                        .withClickEvent(new ClickEvent.ChangePage(targetPage))
                        .withHoverEvent(new HoverEvent.ShowText(Component.translatable(
                                KEY + "nav.jump", targetPage))));
    }

    private static MutableComponent iconRow(Component... icons) {
        MutableComponent row = Component.empty();
        for (int index = 0; index < icons.length; index++) {
            if (index > 0) {
                row.append("  ");
            }
            row.append(icons[index]);
        }
        return row;
    }

    private static MutableComponent recipeRow(Component left, Component middle, Component right) {
        return iconRow(recipeCell(left), recipeCell(middle), recipeCell(right));
    }

    private static Component recipeCell(Component icon) {
        return icon == null
                ? Component.literal("□").withStyle(ChatFormatting.GRAY)
                : icon;
    }

    private static MutableComponent itemIcon(String sprite, String tooltipKey, String fallback) {
        return atlasIcon(
                AtlasIds.ITEMS,
                Identifier.withDefaultNamespace("item/" + sprite),
                Component.translatable(tooltipKey),
                fallback);
    }

    private static MutableComponent blockIcon(String sprite, String tooltipKey, String fallback) {
        Identifier spriteId = sprite.startsWith("exchange_desk")
                ? Identifier.fromNamespaceAndPath(
                        BankerProfessionSupport.MOD_ID, "block/" + sprite)
                : Identifier.withDefaultNamespace("block/" + sprite);
        return atlasIcon(
                AtlasIds.BLOCKS,
                spriteId,
                Component.translatable(tooltipKey),
                fallback);
    }

    private static MutableComponent atlasIcon(
            Identifier atlas,
            Identifier sprite,
            Component tooltip,
            String fallback) {
        return Component.object(
                        new AtlasSprite(atlas, sprite),
                        Component.literal(fallback).withStyle(ChatFormatting.DARK_GREEN))
                .withStyle(style -> style.withHoverEvent(new HoverEvent.ShowText(tooltip)));
    }
}
