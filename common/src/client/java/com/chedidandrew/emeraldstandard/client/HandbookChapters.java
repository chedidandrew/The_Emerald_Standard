package com.chedidandrew.emeraldstandard.client;

import java.util.List;
import net.minecraft.network.chat.Component;

/** Long-form reader chapters, independent of the compact legacy/lectern book pages. */
public final class HandbookChapters {
    public record Chapter(String name, List<String> sections) { }
    public static final String PREFIX = "book.the_emerald_standard.handbook.";
    public static final String READER_PREFIX = "guide.the_emerald_standard.handbook.";
    public static final List<Chapter> ALL = List.of(
            chapter("Getting started", "first_steps", "first_deposit", "amounts"),
            chapter("Risks and safety", "risk_compass"),
            chapter("Your account", "account", "money_routes"),
            chapter("Markets", "market", "live_market", "market_ranges", "market_clock", "investments", "specialists", "growth_targets"),
            chapter("Savings and CDs", "savings_cd"),
            chapter("Villager lending", "lending"),
            chapter("Resource trading", "trade", "trade_diamond_gold", "trade_netherite_emerald"),
            chapter("Village growth", "village", "town_scores", "guard_villagers", "town_outputs", "grow", "needs", "districts", "district_map"),
            chapter("Village recovery", "safety", "collapse"),
            chapter("Building projects", "projects", "planning_building", "construction_safety", "construction_crews", "terrain", "damage"),
            chapter("The Village Fund", "fund", "fund_types", "fund_general", "fund_food",
                    "fund_housing", "fund_security", "fund_infrastructure", "fund_trade",
                    "fund_restoration", "fund_numbers", "fund_example"),
            chapter("Activity and news", "activity_news", "time", "newspaper", "player_news"),
            chapter("Help and recovery", "recovery", "bank_access", "paused"),
            chapter("Crafting recipes", "recipe_desk", "recipe_book", "recipe_fence", "recipe_newspaper", "creative_content"),
            chapter("Finance glossary", "glossary_money", "glossary_rates_1", "glossary_rates_2",
                    "glossary_market_1", "glossary_market_2", "glossary_performance_1",
                    "glossary_performance_2"),
            chapter("Village glossary", "glossary_fund_1", "glossary_fund_2",
                    "glossary_village_1", "glossary_village_2"));
    private HandbookChapters() { }
    private static Chapter chapter(String name, String... sections) {
        return new Chapter(name, List.of(sections));
    }
    public static Component title(String section) {
        return Component.translatable(READER_PREFIX + section + ".title");
    }
    public static Component body(String section) {
        return Component.translatable(READER_PREFIX + section + ".body");
    }
    public static boolean matches(Chapter chapter, String query) {
        String q = query.strip().toLowerCase(java.util.Locale.ROOT);
        return q.isEmpty() || chapter.name().toLowerCase(java.util.Locale.ROOT).contains(q)
                || chapter.sections().stream().anyMatch(s ->
                        (title(s).getString() + " " + body(s).getString())
                                .toLowerCase(java.util.Locale.ROOT).contains(q));
    }
}
