package com.chedidandrew.emeraldstandard.client;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Guards the affordable replacement recipe, recipe-book discovery, and self-drop resources. */
public final class ExchangeDeskRecipeRegressionTest {
    private ExchangeDeskRecipeRegressionTest() {}

    public static void main(String[] args) throws IOException {
        Path root = args.length == 0 ? Path.of(".") : Path.of(args[0]);
        String recipe = compact(root.resolve(
                "common/src/main/resources/data/the_emerald_standard/recipe/exchange_desk.json"));
        String unlock = compact(root.resolve(
                "common/src/main/resources/data/the_emerald_standard/advancement/recipes/misc/exchange_desk.json"));
        String loot = compact(root.resolve(
                "common/src/main/resources/data/the_emerald_standard/loot_table/blocks/exchange_desk.json"));

        check(recipe.contains("\"type\":\"minecraft:crafting_shaped\""),
                "Exchange Desk must remain a shaped crafting-table recipe");
        check(recipe.contains("\"category\":\"misc\""),
                "Exchange Desk must remain in the recipe book's miscellaneous category");
        check(recipe.contains("\"pattern\":[\" E \",\"LBL\",\"PPP\"]"),
                "Exchange Desk recipe pattern changed unexpectedly");
        check(recipe.contains("\"E\":\"minecraft:emerald\""),
                "Exchange Desk recipe must use one thematic emerald");
        check(recipe.contains("\"L\":\"minecraft:leather\""),
                "Exchange Desk recipe must use renewable leather trim");
        check(recipe.contains("\"B\":\"minecraft:book\""),
                "Exchange Desk recipe must use one ledger book");
        check(recipe.contains("\"P\":\"#minecraft:planks\""),
                "Exchange Desk recipe must accept every vanilla plank family");
        check(recipe.contains("\"result\":{\"id\":\"the_emerald_standard:exchange_desk\",\"count\":1}"),
                "Exchange Desk recipe must return exactly one replaceable workstation");
        check(recipe.contains("\"show_notification\":true"),
                "Exchange Desk recipe should announce its recipe-book unlock");

        check(unlock.contains("\"parent\":\"minecraft:recipes/root\""),
                "Exchange Desk recipe unlock must use the vanilla recipe root");
        check(unlock.contains("\"has_emerald\""),
                "Exchange Desk recipe must become discoverable after acquiring an emerald");
        check(unlock.contains("\"has_book\""),
                "Exchange Desk recipe must become discoverable after acquiring a book");
        check(unlock.contains("\"recipe\":\"the_emerald_standard:exchange_desk\""),
                "Exchange Desk recipe-unlocked criterion targets the wrong recipe");
        check(unlock.contains("\"recipes\":[\"the_emerald_standard:exchange_desk\"]"),
                "Exchange Desk advancement must grant the replacement recipe");
        check(unlock.contains("\"requirements\":[[\"has_the_recipe\",\"has_emerald\",\"has_book\"]]"),
                "An emerald or book should independently reveal the Exchange Desk recipe");

        check(loot.contains("\"type\":\"minecraft:block\""),
                "Exchange Desk must keep a block loot table");
        check(loot.contains("\"name\":\"the_emerald_standard:exchange_desk\""),
                "A normally broken Exchange Desk must drop itself for direct replacement");
        check(loot.contains("\"condition\":\"minecraft:survives_explosion\""),
                "Exchange Desk explosion drops must retain vanilla survival behavior");

        System.out.println("PASS Exchange Desk is affordable, discoverable, and replaceable");
    }

    private static String compact(Path path) throws IOException {
        check(Files.isRegularFile(path), "Missing Exchange Desk resource: " + path);
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
        check(!inString, "Unterminated JSON string in Exchange Desk resource: " + path);
        return compact.toString();
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
