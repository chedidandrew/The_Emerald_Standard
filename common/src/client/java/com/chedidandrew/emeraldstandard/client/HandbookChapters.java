package com.chedidandrew.emeraldstandard.client;

import java.util.List;
import java.util.Set;
import net.minecraft.network.chat.Component;

/** Regroups the original guide without hard page breaks, inline navigation or repeated icons. */
public final class HandbookChapters {
    public record Chapter(String name, List<String> sections) { }
    public static final String PREFIX = "book.the_emerald_standard.handbook.";
    public static final List<Chapter> ALL = List.of(
            chapter("Getting started", "first_steps", "first_deposit", "amounts"),
            chapter("Risks and safety", "risk_compass"),
            chapter("Your account", "account", "money_routes"),
            chapter("Markets", "market", "investments"),
            chapter("Savings and CDs", "savings_cd"),
            chapter("Villager lending", "lending"),
            chapter("Resource trading", "trade", "trade_diamond_gold", "trade_netherite_emerald"),
            chapter("Village growth", "village", "grow", "needs"),
            chapter("Village recovery", "safety", "collapse"),
            chapter("Building projects", "projects", "planning_building", "terrain", "damage"),
            chapter("The Village Fund", "fund"),
            chapter("Activity and news", "activity_news", "time"),
            chapter("Help and recovery", "recovery", "bank_access", "paused"),
            chapter("Crafting recipes", "recipe_desk", "recipe_book"),
            chapter("Finance glossary", "glossary_money", "glossary_rates_1", "glossary_rates_2",
                    "glossary_market_1", "glossary_market_2", "glossary_performance_1",
                    "glossary_performance_2"),
            chapter("Village glossary", "glossary_fund_1", "glossary_fund_2",
                    "glossary_village_1", "glossary_village_2"));
    private static final Set<String> LISTS = Set.of("investments", "money_routes", "risk_compass",
            "projects", "needs", "safety", "glossary_money", "glossary_rates_1",
            "glossary_rates_2", "glossary_market_1", "glossary_market_2", "glossary_performance_1",
            "glossary_performance_2", "glossary_fund_1", "glossary_fund_2", "glossary_village_1",
            "glossary_village_2");
    private HandbookChapters() { }
    private static Chapter chapter(String name, String... sections) {
        return new Chapter(name, List.of(sections));
    }
    public static Component title(String section) {
        return Component.translatable(PREFIX + section + ".title");
    }
    public static Component body(String section) {
        // The old tiny-book instructions referred to a letter grid no longer shown in the reader.
        if (section.equals("recipe_desk")) return Component.literal(
                "Use a crafting table.\n\nTop row: empty, Emerald, empty.\n"
                + "Middle row: Leather, Book, Leather.\nBottom row: any three Planks.\n\n"
                + "Makes one Exchange Desk. Place it and right-click to open your account.");
        if (section.equals("recovery")) return Component.literal(
                "A pending journal means a transfer needs recovery. Stop moving the affected items "
                + "and keep a backup. Free space only as directed by an administrator. Reopen the "
                + "bank or use Account > Recover. If recovery keeps failing, stop banking and "
                + "report the error. Do not repeatedly transfer, drop or trade the affected items.");
        String text = Component.translatable(PREFIX + section + ".body").getString();
        if (!LISTS.contains(section)) {
            text = text.replace("\n\n", "\u0000").replace('\n', ' ')
                    .replace("\u0000", "\n\n");
        }
        return Component.literal(text);
    }
    public static boolean matches(Chapter chapter, String query) {
        String q = query.strip().toLowerCase(java.util.Locale.ROOT);
        return q.isEmpty() || chapter.name().toLowerCase(java.util.Locale.ROOT).contains(q)
                || chapter.sections().stream().anyMatch(s ->
                        (title(s).getString() + " " + body(s).getString())
                                .toLowerCase(java.util.Locale.ROOT).contains(q));
    }
}
