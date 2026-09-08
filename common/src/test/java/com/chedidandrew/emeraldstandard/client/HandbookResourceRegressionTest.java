package com.chedidandrew.emeraldstandard.client;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Guards handbook presentation, replacement crafting, recipe discovery, and localization. */
public final class HandbookResourceRegressionTest {
    private static final Pattern LANGUAGE_ENTRY = Pattern.compile(
            "\\\"((?:\\\\.|[^\\\"\\\\])+)\\\":\\\"((?:\\\\.|[^\\\"\\\\])*)\\\"");
    private static final Pattern TRANSLATION_FORMAT = Pattern.compile(
            "%(?:(\\d+)\\$)?([A-Za-z%]|$)");

    private HandbookResourceRegressionTest() {
    }

    public static void main(String[] args) throws IOException {
        Path root = args.length == 0 ? Path.of(".") : Path.of(args[0]);
        String itemDefinition = compact(root.resolve(
                "common/src/main/resources/assets/the_emerald_standard/items/handbook.json"));
        String recipe = compact(root.resolve(
                "common/src/main/resources/data/the_emerald_standard/recipe/handbook.json"));
        String unlock = compact(root.resolve(
                "common/src/main/resources/data/the_emerald_standard/advancement/recipes/misc/handbook.json"));
        String language = compact(root.resolve(
                "common/src/main/resources/assets/the_emerald_standard/lang/en_us.json"));
        String handbookSource = Files.readString(root.resolve(
                "common/src/minecraft/java/com/chedidandrew/emeraldstandard/minecraft/"
                        + "EmeraldHandbook.java"));

        check(itemDefinition.equals(
                        "{\"model\":{\"type\":\"minecraft:model\","
                                + "\"model\":\"minecraft:item/written_book\"}}"),
                "Handbook item definition must reuse the vanilla written-book model");

        check(recipe.contains("\"type\":\"minecraft:crafting_shapeless\""),
                "Handbook replacement must remain a shapeless crafting recipe");
        check(recipe.contains("\"category\":\"misc\""),
                "Handbook replacement must remain in the recipe book's miscellaneous category");
        check(recipe.contains(
                        "\"ingredients\":[\"minecraft:book\",\"minecraft:emerald\"]"),
                "Handbook replacement must require exactly one book and one emerald");
        check(recipe.contains(
                        "\"result\":{\"id\":\"the_emerald_standard:handbook\",\"count\":1}"),
                "Handbook recipe must return exactly one current handbook");
        check(recipe.contains("\"show_notification\":true"),
                "Handbook recipe should announce its recipe-book unlock");

        check(unlock.contains("\"parent\":\"minecraft:recipes/root\""),
                "Handbook recipe unlock must use the vanilla recipe root");
        check(unlock.contains("\"has_book\""),
                "Handbook recipe must become discoverable after acquiring a book");
        check(unlock.contains("\"has_emerald\""),
                "Handbook recipe must become discoverable after acquiring an emerald");
        check(unlock.contains("\"recipe\":\"the_emerald_standard:handbook\""),
                "Handbook recipe-unlocked criterion targets the wrong recipe");
        check(unlock.contains("\"recipes\":[\"the_emerald_standard:handbook\"]"),
                "Handbook advancement must grant the replacement recipe");
        check(unlock.contains(
                        "\"requirements\":[[\"has_the_recipe\",\"has_book\",\"has_emerald\"]]"),
                "A book or emerald should independently reveal the handbook recipe");

        check(hasKey(language, "item.the_emerald_standard.handbook"),
                "English localization is missing the handbook item name");
        check(hasKey(language, "message.the_emerald_standard.handbook_received"),
                "English localization is missing the handbook delivery message");
        check(hasKey(language, "message.the_emerald_standard.handbook_inventory_full"),
                "English localization is missing the full-inventory delivery message");
        requireSupportedTranslationFormats(language);
        requireHandbookLocalization(language);
        requireResolvableHandbookSprites(handbookSource);

        System.out.println("PASS handbook resources are authored, replaceable, and discoverable");
    }

    private static void requireSupportedTranslationFormats(String language) {
        Matcher entryMatcher = LANGUAGE_ENTRY.matcher(language);
        int entries = 0;
        while (entryMatcher.find()) {
            entries++;
            String key = entryMatcher.group(1);
            String value = entryMatcher.group(2);
            Matcher formatMatcher = TRANSLATION_FORMAT.matcher(value);
            int cursor = 0;
            while (formatMatcher.find(cursor)) {
                check(value.substring(cursor, formatMatcher.start()).indexOf('%') < 0,
                        "Unsupported or unescaped percent in translation: " + key);
                String token = value.substring(formatMatcher.start(), formatMatcher.end());
                String type = formatMatcher.group(2);
                check("s".equals(type) || ("%".equals(type) && "%%".equals(token)),
                        "Unsupported translation format " + token + " in: " + key);
                cursor = formatMatcher.end();
            }
            check(value.substring(cursor).indexOf('%') < 0,
                    "Unsupported or unescaped percent in translation: " + key);
        }
        check(entries >= 500, "Language format audit did not inspect the complete English file");
    }

    private static void requireResolvableHandbookSprites(String handbookSource) {
        // This allowlist is checked against the Minecraft 26.2 atlas inputs. Logical items such as
        // chests, shields, beds, and the bare animated clock have no same-named item sprite;
        // AtlasSprite would display the missing-texture tile even though an ItemStack renders them.
        Set<String> itemSprites = Set.of(
                "barrier", "bell", "book", "bread", "clock_00", "diamond", "emerald",
                "ender_pearl", "gold_ingot", "iron_chestplate", "iron_ingot", "leather",
                "minecart", "netherite_ingot", "paper", "potion", "raw_gold", "redstone",
                "totem_of_undying", "villager_spawn_egg", "written_book");
        Set<String> blockSprites = Set.of(
                "ancient_debris_side", "barrel_side", "bricks", "cracked_stone_bricks",
                "diamond_ore", "dirt_path_top", "emerald_block", "emerald_ore",
                "exchange_desk_front", "oak_planks", "stone_bricks", "white_bed_head_up");

        Matcher itemMatcher = Pattern.compile("itemIcon\\(\\\"([^\\\"]+)\\\"")
                .matcher(handbookSource);
        int itemCalls = 0;
        while (itemMatcher.find()) {
            itemCalls++;
            check(itemSprites.contains(itemMatcher.group(1)),
                    "Handbook item icon is not verified in the Minecraft 26.2 item atlas: "
                            + itemMatcher.group(1));
        }
        Matcher blockMatcher = Pattern.compile("blockIcon\\(\\\"([^\\\"]+)\\\"")
                .matcher(handbookSource);
        int blockCalls = 0;
        while (blockMatcher.find()) {
            blockCalls++;
            check(blockSprites.contains(blockMatcher.group(1)),
                    "Handbook block icon is not verified in the Minecraft 26.2 block atlas: "
                            + blockMatcher.group(1));
        }

        check(itemCalls >= 40 && blockCalls >= 15,
                "Handbook atlas audit did not inspect the expected visual coverage");
    }

    private static boolean hasKey(String compactJson, String key) {
        return compactJson.contains("\"" + key + "\":");
    }

    private static void requireHandbookLocalization(String language) {
        String prefix = "book.the_emerald_standard.handbook.";
        String[] titledPages = {
            "cover", "how_to_read", "contents_banking", "contents_village",
            "first_steps", "first_deposit", "amounts", "risk_compass", "tabs",
            "account", "market", "investments", "money_routes", "savings_cd",
            "lending", "trade", "trade_diamond_gold", "trade_netherite_emerald",
            "village", "grow", "safety", "needs", "collapse", "projects",
            "planning_building", "terrain", "damage", "fund", "activity_news",
            "time", "recovery", "bank_access", "paused", "recipe_desk", "recipe_book",
            "glossary_money", "glossary_rates_1", "glossary_rates_2",
            "glossary_market_1", "glossary_market_2", "glossary_performance_1",
            "glossary_performance_2", "glossary_fund_1", "glossary_fund_2",
            "glossary_village_1", "glossary_village_2"
        };
        for (String page : titledPages) {
            check(hasKey(language, prefix + page + ".title"),
                    "English localization is missing handbook title: " + page);
            if (!page.equals("contents_banking")
                    && !page.equals("contents_village")
                    && !page.equals("tabs")) {
                check(hasKey(language, prefix + page + ".body"),
                        "English localization is missing handbook body: " + page);
            }
        }

        for (String risk : new String[] {"safe", "locked", "risk", "gift"}) {
            check(hasKey(language, prefix + "risk." + risk + ".label"),
                    "English localization is missing handbook risk label: " + risk);
            check(hasKey(language, prefix + "risk." + risk + ".body"),
                    "English localization is missing handbook risk definition: " + risk);
        }

        String[] links = {
            "open_contents", "next_contents", "previous_contents",
            "nav.contents", "nav.previous", "nav.next", "nav.jump",
            "toc.first_steps", "toc.risk", "toc.dashboard", "toc.account", "toc.market",
            "toc.banking", "toc.lending", "toc.trade", "toc.village", "toc.projects",
            "toc.fund", "toc.activity_news", "toc.news", "toc.help", "toc.crafting",
            "toc.glossary"
        };
        for (String link : links) {
            check(hasKey(language, prefix + link),
                    "English localization is missing handbook navigation: " + link);
        }
    }

    private static String compact(Path path) throws IOException {
        check(Files.isRegularFile(path), "Missing handbook resource: " + path);
        String json = Files.readString(path);
        StringBuilder compact = new StringBuilder(json.length());
        boolean inString = false;
        boolean escaped = false;
        for (int index = 0; index < json.length(); index++) {
            char character = json.charAt(index);
            if (inString) {
                compact.append(character);
                if (escaped) {
                    escaped = false;
                } else if (character == '\\') {
                    escaped = true;
                } else if (character == '"') {
                    inString = false;
                }
            } else if (character == '"') {
                inString = true;
                compact.append(character);
            } else if (!Character.isWhitespace(character)) {
                compact.append(character);
            }
        }
        check(!inString, "Unterminated JSON string in handbook resource: " + path);
        return compact.toString();
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
