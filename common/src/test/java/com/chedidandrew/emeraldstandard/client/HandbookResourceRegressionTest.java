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
            "\\\"((?:\\\\.|[^\\\"\\\\])++)\\\":\\\"((?:\\\\.|[^\\\"\\\\])*+)\\\"");
    private static final Pattern TRANSLATION_FORMAT = Pattern.compile(
            "%(?:(\\d+)\\$)?([A-Za-z%]|$)");

    private static void requireNewspaperAppearance(Path root,String language) throws IOException {
        Path assets=root.resolve("common/src/main/resources/assets/the_emerald_standard");
        Path client=root.resolve("common/src/client/java/com/chedidandrew/emeraldstandard/client");
        String preview=Files.readString(client.resolve("NewspaperClientChecks.java"));
        String nativeModel=Files.readString(client.resolve("NewspaperModelPreview.java"));
        check(preview.contains("NewspaperModelPreview.draw")&&!preview.contains("scale(6f,6f)"),
                "Inspection renders a native model, never enlarges the small GUI item atlas");
        check(nativeModel.contains("ItemDisplayEntityRenderState")&&nativeModel.contains("updateForTopItem")
                &&nativeModel.contains("g.entity(")&&!nativeModel.contains("g.blit("),
                "Inspection resolves the packaged model through Minecraft rather than substituting concept art");
        String definition=compact(assets.resolve("items/newspaper.json"));
        check(definition.contains("\"property\":\"minecraft:using_item\"")
                &&definition.contains("\"on_true\":{\"type\":\"minecraft:model\",\"model\":\"the_emerald_standard:item/newspaper_open\"}")
                &&definition.contains("\"on_false\":{\"type\":\"minecraft:model\",\"model\":\"the_emerald_standard:item/newspaper\"}"),
                "One item selects rolled/open models from synchronized use state");
        for(String name:new String[]{"newspaper","newspaper_open"}) {
            String model=compact(assets.resolve("models/item/"+name+".json"));
            check(model.contains("\"parent\":\"minecraft:item/generated\"")
                    &&model.contains("\"layer0\":\"the_emerald_standard:item/"+name+"\"")
                    &&model.contains("firstperson_lefthand")&&model.contains("firstperson_righthand"),
                    "Detailed flat sprite with both hand transforms");
            var sprite=javax.imageio.ImageIO.read(assets.resolve("textures/item/"+name+".png").toFile());
            check(sprite!=null&&sprite.getWidth()==256&&sprite.getHeight()==256&&sprite.getColorModel().hasAlpha(),
                    "Detailed 256px RGBA newspaper texture");
            int transparent=0,ink=0,paper=0;
            int minX=256,minY=256,maxX=-1,maxY=-1;
            for(int y=0;y<256;y++)for(int x=0;x<256;x++) {
                int pixel=sprite.getRGB(x,y),a=pixel>>>24,r=(pixel>>>16)&255,g=(pixel>>>8)&255,b=pixel&255;
                if(a==0){transparent++;continue;}
                if(a<128)continue;
                minX=Math.min(minX,x);minY=Math.min(minY,y);maxX=Math.max(maxX,x);maxY=Math.max(maxY,y);
                check(Math.max(r,Math.max(g,b))-Math.min(r,Math.min(g,b))<=35,"Restrained paper and charcoal palette");
                if(r<75&&g<75&&b<75)ink++;
                if(r>=130)paper++;
            }
            check(transparent>10000&&ink>600&&paper>7200,"Real transparent exterior, fine print and visible paper");
            check((sprite.getRGB(0,0)>>>24)==0&&(sprite.getRGB(255,255)>>>24)==0,"No baked checkerboard background");
            double aspect=(maxX-minX+1.0)/(maxY-minY+1.0);
            if(name.equals("newspaper_open"))check(aspect>0.76&&aspect<0.90,"Broad concept page, not a narrowed gazette");
            var master=javax.imageio.ImageIO.read(root.resolve("art/newspaper/"+
                    (name.equals("newspaper")?"rolled-master.png":"pocket-concept-master.png")).toFile());
            double scale=256.0/Math.max(master.getWidth(),master.getHeight());
            double left=(256-master.getWidth()*scale)/2,top=(256-master.getHeight()*scale)/2;
            for(int y=0;y<256;y++)for(int x=0;x<256;x++) {
                int sx=(int)Math.floor((x+0.5-left)/scale),sy=(int)Math.floor((y+0.5-top)/scale);
                int expected=sx<0||sy<0||sx>=master.getWidth()||sy>=master.getHeight()?0:master.getRGB(sx,sy);
                int actual=sprite.getRGB(x,y);
                check((expected>>>24)==0?(actual>>>24)==0:actual==expected,"Packaged texture must preserve uniform master sampling");
            }
        }
        check(Files.mismatch(assets.resolve("textures/item/newspaper.png"),assets.resolve("textures/item/newspaper_open.png"))>=0,
                "Rolled and pocket editions are distinct artwork");
        check(language.contains("rolled grey newspaper")&&language.contains("pocket gazette")
                &&language.contains("Rolled when closed.")&&language.contains("Open while reading."),
                "Both handbook forms explain the two appearance states");
        check(language.contains("every Contents sheet")&&language.contains("arriving at the end of the preceding report")
                &&language.contains("Faint paper fibres"),"Newspaper guided navigation and subtle paper guidance");
        check(language.contains("text, then stories.")&&language.contains("Previous: reverse."),"Compact newspaper route is current");
    }

    private HandbookResourceRegressionTest() {
    }

    public static void main(String[] args) throws IOException {
        Path root = args.length == 0 ? Path.of(".") : Path.of(args[0]);
        String benchGuide = Files.readString(root.resolve(
                "common/src/main/resources/assets/the_emerald_standard/lang/en_us.json"));
        check(benchGuide.contains("front terrace are decorative benches")
                        && benchGuide.contains("raised backs toward the building")
                        && benchGuide.contains("Existing completed Banks keep their original blocks"),
                "Handbook must explain Bank seating and the non-destructive update policy");
        String itemDefinition = compact(root.resolve(
                "common/src/main/resources/assets/the_emerald_standard/items/handbook.json"));
        String recipe = compact(root.resolve(
                "common/src/main/resources/data/the_emerald_standard/recipe/handbook.json"));
        String unlock = compact(root.resolve(
                "common/src/main/resources/data/the_emerald_standard/advancement/recipes/misc/handbook.json"));
        String language = compact(root.resolve(
                "common/src/main/resources/assets/the_emerald_standard/lang/en_us.json"));
        check(language.contains("Immigration now accumulates saved progress")
                        && language.contains("Unverified"),
                "Handbook must explain saved immigration and unknown residents");
        check(language.contains("New immigration approvals keep at most eight settlers")
                        && language.contains("All world heights are checked"),
                "Handbook must explain bounded arrivals and full-height housing");
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
        requireLongFormReader(root, language);
        check(language.contains("Browse / compare") && language.contains("server checks again")
                && language.contains("actual purpose, sources of payment") && language.contains("source fingerprint"),
                "Decision support and build identity must remain documented");
        check(language.contains("checks actual ownership") && language.contains("optional targeted donations are disabled")
                && language.contains("Completed simulated-only projects") && language.contains("clears the old document"),
                "Edge-case fixes must stay documented in guided handbook text");
        check(language.contains("Town opens the village overview")
                && language.contains("What next? Progress report")
                && language.contains("Town opens scores.")
                && !language.contains("Town now opens a live progress report"),
                "Long and compact handbooks must explain overview-first Town navigation");
        check(language.contains("15 real seconds")&&language.contains("Day 24 at dawn")&&language.contains("ten years of daily closes")
                &&language.contains("Yesterday: replay.")&&language.contains("Catch-up: no trades."),
                "Live charts, non-rewinding time commands and compact guidance must stay documented");
        check(language.contains("Retry: 10-30s.") && language.contains("See Town report.")
                && language.contains("last observed check") && language.contains("/emerald debug"),
                "Site-search retries and normal debug capture must stay documented in both handbooks");
        requireResolvableHandbookSprites(handbookSource);
        check(language.contains("VILX: 12 stocks.") && language.contains("saved simulated shares outstanding")
                && language.contains("Earlier chart points still show the old method")
                && language.contains("capitalization-weighted average")
                && !language.contains("original group of companies and a broad-economy component"),
                "VILX basket, migration and non-guaranteed targets must be explained in both handbooks");
        requireCreativeCatalog(root,language);
        requireNewspaperAppearance(root,language);
        check(language.contains("guide.the_emerald_standard.handbook.district_map.body")
                && language.contains("Checkered cells mean unknown or still-loading map data")
                && language.contains("saved locally") && language.contains("not total return or a forecast")
                && language.contains("Sneak + block:"), "map and desk instructions stay documented");
        for (String loader : new String[] {"fabric", "neoforge"}) {
            String entry = loader.equals("fabric") ? "EmeraldStandardFabric" : "EmeraldStandardNeoForge";
            String wiring = Files.readString(root.resolve(loader + "/src/main/java/com/chedidandrew/emeraldstandard/"
                    + loader + "/" + entry + ".java"));
            int placement = wiring.indexOf("ExchangeDeskInteraction.placingBlock");
            check(placement >= 0 && placement < wiring.indexOf("VillageBankManager.bankDeskAccess"),
                    "crouch placement must pass through before any desk access side effects on " + loader);
        }
        String terrain = Files.readString(root.resolve(
                "common/src/client/java/com/chedidandrew/emeraldstandard/client/DistrictTerrainLayer.java"));
        check(terrain.contains("ExploredTerrainRuntime.color") && !terrain.contains("getChunk("),
                "terrain rendering reads the shared cache without loading chunks");
        String runtime = Files.readString(root.resolve(
                "common/src/client/java/com/chedidandrew/emeraldstandard/client/ExploredTerrainRuntime.java"));
        check(runtime.contains("ChunkStatus.FULL,false") && runtime.contains("SAMPLES_PER_TICK=128")
                && runtime.contains("TICK_BUDGET_NS") && !runtime.contains("getChunkAt("),
                "exploration capture is budgeted and loaded-only");
        String dashboard = Files.readString(root.resolve(
                "common/src/client/java/com/chedidandrew/emeraldstandard/client/BankerScreen.java"));
        check(!dashboard.contains("setTooltipForNextFrame(") && dashboard.contains("GuiTooltips.show(")
                && !dashboard.contains("Sampled price history") && !dashboard.contains("market.type_help."),
                "dashboard hovers wrap and leave educational prose in the handbook");

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

    private static void requireCreativeCatalog(Path root,String language) throws IOException {
        String resources="common/src/main/resources/";
        for(String egg: new String[] {"banker_spawn_egg","builder_spawn_egg"}) {
            check(hasKey(language,"item.the_emerald_standard."+egg), "Missing egg name");
            check(compact(root.resolve(resources+"assets/the_emerald_standard/items/"+egg+".json"))
                    .contains("\"type\":\"minecraft:model\""), "Missing native egg item model");
            check(!Files.exists(root.resolve(resources+"data/the_emerald_standard/recipe/"+egg+".json")),
                    "Eggs must remain creative-only");
        }
        check(hasKey(language,"itemGroup.the_emerald_standard"), "Missing creative tab label");
        String fence=compact(root.resolve(resources+"data/the_emerald_standard/recipe/construction_fence.json"));
        check(fence.contains("\"pattern\":[\"SYS\",\"SBS\"]")
                && fence.contains("\"S\":\"minecraft:stick\"")
                && fence.contains("\"Y\":\"minecraft:yellow_dye\"")
                && fence.contains("\"B\":\"minecraft:black_dye\"")
                && fence.contains("\"result\":{\"id\":\"the_emerald_standard:construction_fence\",\"count\":4}"),
                "Documented fence recipe must match server data");
        check(language.contains("The two eggs are Creative tools, not Survival recipes.")
                && language.contains("Four sticks and the two dyes make four construction fences.")
                && language.contains("Those owned segments do not drop items"),
                "Handbook must explain egg restrictions, fence crafting and automatic loot protection");
        check(language.contains("Each worker keeps a separate, spaced position")
                && language.contains("After about ten seconds of continued paused, disabled or blocked work")
                && language.contains("unloaded workers keep their slots to prevent duplicates")
                && language.contains("Long pause: crew leaves."),
                "Long and compact handbooks must explain stable crews, departure and duplicate safeguards");
        check(language.contains("Biome workwear.") && language.contains("toolsmith-style leather apron")
                && language.contains("It is chosen once and saved") && language.contains("fore-and-aft")
                && !language.contains("hard hat, vest and hammer model"),
                "Both handbook forms describe native builder workwear and saved biome appearance");
        check(language.contains("Physical work follows a support-first sequence")
                && language.contains("saved sequence") && language.contains("Supports before lamps."),
                "Long and compact handbooks must explain sequencing, support dependencies and safe migration");
        check(language.contains("yellow post cap, dark foot and stepped black-and-yellow rails")
                && language.contains("straight runs, corners and junctions") && language.contains("Striped rails."),
                "Both handbook forms must describe the connected fence appearance");
        for (String loader : new String[] {"fabric", "neoforge"}) {
            String build = Files.readString(root.resolve(loader + "/build.gradle"));
            check(build.contains("dependsOn tasks.named('verifyConstructionFenceModels')")
                    && build.contains("minecraft.ConstructionFenceModelSelfTest"),
                    "Both builds must validate the actual fence model resources");
        }
        check(language.contains("Completed mod-created walkways gain matching street lamps")
                && language.contains("Arms face inward toward the walkway")
                && language.contains("Saved one-shot receipts") && language.contains("Paths gain lamps."),
                "Both handbook forms must explain walkway lamps, orientation and no regeneration");
        check(language.contains("Paths detour.") && language.contains("384-block endpoint-distance limit")
                && language.contains("arbitrary patch near the center") && language.contains("Connection receipts survive reloads")
                && language.contains("narrow passage is better than a wide dead end"),
                "Both handbook forms must explain loaded-only walkway connections, bounds and editing");
        String manager = Files.readString(root.resolve(
                "common/src/minecraft/java/com/chedidandrew/emeraldstandard/minecraft/VillageProsperityManager.java"));
        check(manager.contains("materializeOneWalkwayLamp(level, village, excludedProjectLots")
                && manager.contains("WalkwayLighting.advance(level")
                && manager.contains("project.trailMaterializedComplete"), "Completed path lighting must be wired");
        check(language.contains("Optional yard decorations are different from required structure")
                && language.contains("Unsafe decor skips.") && language.contains("expected nearby support"),
                "Both handbook forms must explain optional support recovery and diagnostics");
        check(manager.contains("placement.isCosmetic() && !ConstructionOwnership.owned(level,target,placement.state)")
                && manager.contains("SupportedConstructionOrder.waitReason"),
                "Support recovery must exclude required fixtures and preserve owned repair authority");
        System.out.println("PASS creative-only egg resources, fence recipe and handbook guidance");
    }

    private static void requireLongFormReader(Path root, String language) throws IOException {
        Path tooltipClient=root.resolve("common/src/client/java/com/chedidandrew/emeraldstandard/client");
        try(var screens=Files.list(tooltipClient)) {
            for(Path screen:screens.filter(p->p.getFileName().toString().endsWith("Screen.java")).toList())
                check(!Files.readString(screen).contains("Tooltip.create("),
                        "Screen must use the wrapped tooltip factory: "+screen.getFileName());
        }
        String tooltips=Files.readString(tooltipClient.resolve("GuiTooltips.java"));
        check(tooltips.contains("widgetText(") && tooltips.contains("font.getSplitter().splitLines(")
                && tooltips.contains("withStyle(style)") && tooltips.contains("g.guiWidth()"),
                "Tooltip wrapping must preserve styles and respect the drawing viewport");
        check(language.contains("Help text wraps.") && language.contains("keyboard-focused controls also show help"),
                "Both handbook forms must explain wrapped, keyboard-accessible help");
        String chapters = Files.readString(root.resolve(
                "common/src/client/java/com/chedidandrew/emeraldstandard/client/HandbookChapters.java"));
        java.util.Map<String, String> entries = new java.util.HashMap<>();
        Matcher entry = LANGUAGE_ENTRY.matcher(language);
        while (entry.find()) {
            check(entries.put(entry.group(1), entry.group(2)) == null,
                    "Duplicate language key: " + entry.group(1));
        }
        String prefix = "guide.the_emerald_standard.handbook.";
        check(chapters.contains("Component.translatable(READER_PREFIX + section + \".body\")"),
                "Reader must use its long-form text, not the tiny legacy book summaries");
        Matcher chapter = Pattern.compile("chapter\\(\"[^\"]+\"([^)]*)\\)").matcher(chapters);
        Set<String> sections = new java.util.HashSet<>();
        int chapterCount = 0;
        while (chapter.find()) {
            chapterCount++;
            Matcher section = Pattern.compile("\"([^\"]+)\"").matcher(chapter.group(1));
            int words = 0;
            while (section.find()) {
                String id = section.group(1);
                check(sections.add(id), "Reader section was duplicated: " + id);
                String title = entries.get(prefix + id + ".title");
                String body = entries.get(prefix + id + ".body");
                check(title != null && !title.isBlank(), "Missing reader heading: " + id);
                check(body != null && body.length() >= 250, "Reader section is only a stub: " + id);
                check(body.contains("\\n\\n"), "Reader section needs paragraphs: " + id);
                words += body.split("\\s+").length;
            }
            // Crafting has three large animated diagrams in addition to its prose.
            int minimum = chapter.group(1).contains("\"recipe_desk\"") ? 150 : 180;
            check(words >= minimum, "A reader chapter is still only a brief summary");
        }
        check(chapterCount == 16 && sections.size() == 68, "Long-form chapter coverage changed");
        check(entries.get(prefix+"construction_safety.body").contains("no normal block loot")
                && entries.get(prefix+"construction_safety.body").contains("finishing pass")
                && entries.get(prefix+"construction_safety.body").contains("Manual Repair"),
                "Construction ownership, automatic repair and legacy limits must be explained");
        check(!entries.get(prefix + "first_steps.body").matches("(?s).*\\b1[. ] Find.*"),
                "Getting started must guide the player, not restore the old numbered list");
        for (String purpose : new String[] {"general", "food", "housing", "security",
                "infrastructure", "trade", "restoration"}) {
            check(sections.contains("fund_" + purpose), "Missing Fund purpose explanation: " + purpose);
        }
        check(entries.get(prefix + "town_scores.body").contains("28")
                        && entries.get(prefix + "town_scores.body").contains("24")
                        && entries.get(prefix + "town_scores.body").contains("22"),
                "Prosperity weights must be explained");
        check(entries.get(prefix + "village.body").contains("18 / 16")
                        && entries.get(prefix + "town_outputs.body").contains("F / M / T"),
                "Town's housing and output examples must remain explained");
        System.out.println("PASS 16 long-form handbook chapters, 68 sections and all seven funding purposes");
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
        check(language.contains("numeric precision") && language.contains("confirm the updated action again")
                        && language.contains("Tiny holdings stay."),
                "Handbook must explain numeric limits, retained dust and changed confirmations");
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
            "glossary_village_1", "glossary_village_2", "specialists",
            "creative_content", "recipe_fence", "construction_crews",
            "growth_targets", "newspaper", "player_news", "recipe_newspaper",
            "browser_help", "town_report", "fund_receipt"
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
        if(path.getFileName().toString().equals("en_us.json")) {
            check(json.contains("Villager Commodity Index")&&json.contains("weights drift")
                    &&json.contains("each Details column scrolls independently")
                    &&json.contains("masthead, lead story")&&json.contains("Contents: jump."),
                    "Newspaper, commodity basket and comparison guide must stay current");
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
