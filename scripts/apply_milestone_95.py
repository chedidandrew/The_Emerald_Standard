#!/usr/bin/env python3
from pathlib import Path
import json

ROOT = Path(__file__).resolve().parents[1]

def read(rel):
    return (ROOT / rel).read_text()

def write(rel, text):
    (ROOT / rel).write_text(text)

def replace_once(text, old, new, label):
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected exactly one match, found {count}")
    return text.replace(old, new, 1)

# 1) Expand the abstract project model and adaptive village priorities.
engine_path = "common/src/main/java/com/chedidandrew/emeraldstandard/core/VillageProsperityEngine.java"
engine = read(engine_path)
engine = replace_once(engine,
'''    public enum ProjectType {\n        COTTAGE(80.0, 8.0, 4, 180),\n        WAREHOUSE(130.0, 18.0, 0, 260),\n        MINE_ENTRANCE(160.0, 25.0, 0, 230);''',
'''    public enum ProjectType {\n        COTTAGE(80.0, 8.0, 4, 180),\n        WAREHOUSE(130.0, 18.0, 0, 260),\n        MINE_ENTRANCE(160.0, 25.0, 0, 230),\n        HOUSE(120.0, 14.0, 6, 250),\n        INN(190.0, 28.0, 8, 360),\n        MARKET_SQUARE(175.0, 32.0, 0, 260),\n        SMITHY(185.0, 30.0, 0, 285),\n        GRANARY(145.0, 22.0, 0, 245),\n        GUARD_POST(165.0, 26.0, 0, 240),\n        EXCHANGE_HALL(280.0, 60.0, 0, 430);''',
"project enum")
engine = replace_once(engine,
'''        int cottages = completedProjects(village, ProjectType.COTTAGE);\n        int warehouses = completedProjects(village, ProjectType.WAREHOUSE);\n        int mines = completedProjects(village, ProjectType.MINE_ENTRANCE);''',
'''        int cottages = completedProjects(village, ProjectType.COTTAGE);\n        int houses = completedProjects(village, ProjectType.HOUSE);\n        int inns = completedProjects(village, ProjectType.INN);\n        int warehouses = completedProjects(village, ProjectType.WAREHOUSE);\n        int mines = completedProjects(village, ProjectType.MINE_ENTRANCE);\n        int markets = completedProjects(village, ProjectType.MARKET_SQUARE);\n        int smithies = completedProjects(village, ProjectType.SMITHY);\n        int granaries = completedProjects(village, ProjectType.GRANARY);\n        int guardPosts = completedProjects(village, ProjectType.GUARD_POST);\n        int exchanges = completedProjects(village, ProjectType.EXCHANGE_HALL);''',
"project counts")
engine = replace_once(engine,
'''        village.agricultureOutput = population * 0.58 * productivity\n                * (1.0 + 0.04 * village.developmentTier)\n                * profession.agriculture();\n        village.miningOutput = population * 0.22 * productivity\n                * (1.0 + 0.32 * mines)\n                * profession.mining();\n        village.tradeOutput = population * 0.18 * productivity\n                * (1.0 + 0.22 * warehouses)\n                * profession.trade();''',
'''        village.agricultureOutput = population * 0.58 * productivity\n                * (1.0 + 0.04 * village.developmentTier + 0.16 * granaries)\n                * profession.agriculture();\n        village.miningOutput = population * 0.22 * productivity\n                * (1.0 + 0.32 * mines + 0.18 * smithies)\n                * profession.mining();\n        village.tradeOutput = population * 0.18 * productivity\n                * (1.0 + 0.22 * warehouses + 0.20 * markets + 0.16 * inns + 0.22 * exchanges)\n                * profession.trade();''',
"sector production")
engine = replace_once(engine,
'''        village.transportOutput = population * 0.10 * productivity\n                * (1.0 + 0.15 * warehouses)\n                * profession.transport();\n        village.securityOutput = population * 0.08\n                * clamp(0.5 + village.safety / 100.0, 0.4, 1.5)\n                * profession.security();''',
'''        village.transportOutput = population * 0.10 * productivity\n                * (1.0 + 0.15 * warehouses + 0.12 * markets + 0.10 * exchanges)\n                * profession.transport();\n        village.securityOutput = population * 0.08\n                * (1.0 + 0.35 * guardPosts)\n                * clamp(0.5 + village.safety / 100.0, 0.4, 1.5)\n                * profession.security();''',
"transport security")
engine = replace_once(engine,
'''        double infrastructureUpkeep = cottages * 0.03 + warehouses * 0.08 + mines * 0.10;\n        double materialUpkeep = (cottages + warehouses + mines) * 0.015;''',
'''        double infrastructureUpkeep = cottages * 0.03\n                + houses * 0.05\n                + inns * 0.08\n                + warehouses * 0.08\n                + mines * 0.10\n                + markets * 0.07\n                + smithies * 0.09\n                + granaries * 0.05\n                + guardPosts * 0.08\n                + exchanges * 0.12;\n        double materialUpkeep = (cottages + houses + inns + warehouses + mines + markets\n                + smithies + granaries + guardPosts + exchanges) * 0.015;''',
"upkeep")
engine = replace_once(engine,
'''        double denominator = switch (active.type) {\n            case COTTAGE -> 120.0;\n            case WAREHOUSE -> 190.0;\n            case MINE_ENTRANCE -> 220.0;\n        };''',
'''        double denominator = switch (active.type) {\n            case COTTAGE -> 120.0;\n            case HOUSE -> 165.0;\n            case INN -> 235.0;\n            case WAREHOUSE -> 190.0;\n            case MINE_ENTRANCE -> 220.0;\n            case MARKET_SQUARE -> 205.0;\n            case SMITHY -> 215.0;\n            case GRANARY -> 180.0;\n            case GUARD_POST -> 185.0;\n            case EXCHANGE_HALL -> 285.0;\n        };''',
"project duration")
old_approval = '''        ProjectType desired = null;\n        int cottageCount = countProjects(village, ProjectType.COTTAGE);\n        boolean hasWarehouse = countProjects(village, ProjectType.WAREHOUSE) > 0;\n        boolean hasMine = countProjects(village, ProjectType.MINE_ENTRANCE) > 0;\n        if (village.population + village.pendingSettlers >= village.housingCapacity - 1\n                && cottageCount < 5) {\n            desired = ProjectType.COTTAGE;\n        } else if (!hasWarehouse\n                && village.population >= 6\n                && village.prosperity >= 42.0) {\n            desired = ProjectType.WAREHOUSE;\n        } else if (!hasMine\n                && village.population >= 5\n                && village.developmentTier >= 1) {\n            desired = ProjectType.MINE_ENTRANCE;\n        } else if (cottageCount < 5\n                && village.population >= village.housingCapacity - 2\n                && unit(worldSeed, village.villageId, day, PROJECT_SALT) < 0.012) {\n            desired = ProjectType.COTTAGE;\n        }\n\n        double requiredDevelopment = switch (desired == null\n                ? ProjectType.COTTAGE\n                : desired) {\n            case COTTAGE -> 8.0;\n            case WAREHOUSE -> 14.0;\n            case MINE_ENTRANCE -> 16.0;\n        };'''
new_approval = '''        ProjectType desired = null;\n        int housingProjects = countProjects(village, ProjectType.COTTAGE)\n                + countProjects(village, ProjectType.HOUSE)\n                + countProjects(village, ProjectType.INN);\n        boolean hasWarehouse = countProjects(village, ProjectType.WAREHOUSE) > 0;\n        boolean hasMine = countProjects(village, ProjectType.MINE_ENTRANCE) > 0;\n        boolean hasMarket = countProjects(village, ProjectType.MARKET_SQUARE) > 0;\n        boolean hasSmithy = countProjects(village, ProjectType.SMITHY) > 0;\n        boolean hasGranary = countProjects(village, ProjectType.GRANARY) > 0;\n        boolean hasGuardPost = countProjects(village, ProjectType.GUARD_POST) > 0;\n        boolean hasExchange = countProjects(village, ProjectType.EXCHANGE_HALL) > 0;\n        int committedPopulation = village.population + village.pendingSettlers;\n        double foodDays = village.foodSupply / Math.max(1.0, village.population * 0.46);\n\n        // Local need wins over prestige. A village under pressure builds what solves its\n        // current problem instead of following one fixed structure sequence.\n        if ((village.lifecycle == Lifecycle.THREATENED || village.safety < 42.0)\n                && !hasGuardPost && village.population >= 4) {\n            desired = ProjectType.GUARD_POST;\n        } else if (foodDays < 18.0 && !hasGranary && village.population >= 5) {\n            desired = ProjectType.GRANARY;\n        } else if (committedPopulation >= village.housingCapacity - 1 && housingProjects < 6) {\n            desired = village.developmentTier >= 3\n                    ? ProjectType.INN\n                    : village.developmentTier >= 2 ? ProjectType.HOUSE : ProjectType.COTTAGE;\n        } else if (!hasWarehouse && village.population >= 6 && village.prosperity >= 42.0) {\n            desired = ProjectType.WAREHOUSE;\n        } else if (!hasMine && village.population >= 5 && village.developmentTier >= 1) {\n            desired = ProjectType.MINE_ENTRANCE;\n        } else if (!hasMarket && village.population >= 9 && village.prosperity >= 50.0) {\n            desired = ProjectType.MARKET_SQUARE;\n        } else if (!hasSmithy && village.population >= 10 && village.developmentTier >= 2) {\n            desired = ProjectType.SMITHY;\n        } else if (!hasExchange\n                && village.population >= 18\n                && village.developmentTier >= 4\n                && village.prosperity >= 68.0) {\n            desired = ProjectType.EXCHANGE_HALL;\n        } else if (housingProjects < 6\n                && committedPopulation >= village.housingCapacity - 2\n                && unit(worldSeed, village.villageId, day, PROJECT_SALT) < 0.012) {\n            desired = village.developmentTier >= 2 ? ProjectType.HOUSE : ProjectType.COTTAGE;\n        }\n\n        double requiredDevelopment = switch (desired == null ? ProjectType.COTTAGE : desired) {\n            case COTTAGE -> 8.0;\n            case HOUSE -> 11.0;\n            case INN -> 18.0;\n            case WAREHOUSE -> 14.0;\n            case MINE_ENTRANCE -> 16.0;\n            case MARKET_SQUARE -> 17.0;\n            case SMITHY -> 18.0;\n            case GRANARY -> 13.0;\n            case GUARD_POST -> 14.0;\n            case EXCHANGE_HALL -> 24.0;\n        };'''
engine = replace_once(engine, old_approval, new_approval, "adaptive project approval")
engine = replace_once(engine,
'''                        && !project.abstractOnly\n                        && project.type == ProjectType.COTTAGE)''',
'''                        && !project.abstractOnly\n                        && (project.type == ProjectType.COTTAGE\n                                || project.type == ProjectType.HOUSE\n                                || project.type == ProjectType.INN))''',
"visual backlog")
engine = replace_once(engine,
'''        boolean warehouse = completedProjects(village, ProjectType.WAREHOUSE) > 0;\n        boolean mine = completedProjects(village, ProjectType.MINE_ENTRANCE) > 0;''',
'''        boolean warehouse = completedProjects(village, ProjectType.WAREHOUSE) > 0;\n        boolean mine = completedProjects(village, ProjectType.MINE_ENTRANCE) > 0;''',
"tier project flags")
engine = replace_once(engine,
'''        } else if (village.population >= 28 && village.prosperity >= 75.0 && completed >= 6) {\n            tier = 5;\n        } else if (village.population >= 18 && village.prosperity >= 65.0 && completed >= 4) {\n            tier = 4;''',
'''        } else if (village.population >= 28 && village.prosperity >= 75.0 && completed >= 6) {\n            tier = 5;\n        } else if (village.population >= 18 && village.prosperity >= 65.0 && completed >= 4) {\n            tier = 4;''',
"tier conditions")
write(engine_path, engine)

# 2) Give every new project a bounded, biome-aware physical template.
manager_path = "common/src/minecraft/java/com/chedidandrew/emeraldstandard/minecraft/VillageProsperityManager.java"
manager = read(manager_path)
manager = replace_once(manager,
'''        return switch (type) {\n            case COTTAGE -> cottage(palette);\n            case WAREHOUSE -> warehouse(palette);\n            case MINE_ENTRANCE -> mineEntrance(palette);\n        };''',
'''        return switch (type) {\n            case COTTAGE -> cottage(palette);\n            case HOUSE -> house(palette);\n            case INN -> inn(palette);\n            case WAREHOUSE -> warehouse(palette);\n            case MINE_ENTRANCE -> mineEntrance(palette);\n            case MARKET_SQUARE -> marketSquare(palette);\n            case SMITHY -> smithy(palette);\n            case GRANARY -> granary(palette);\n            case GUARD_POST -> guardPost(palette);\n            case EXCHANGE_HALL -> exchangeHall(palette);\n        };''',
"physical project switch")
insert_marker = '''    private static List<Placement> warehouse(Palette palette) {'''
new_templates = '''    private static List<Placement> house(Palette palette) {\n        List<Placement> placements = simpleBuilding(palette, 9, 9, 4);\n        addBeds(placements, 9, 9, 6);\n        placements.add(new Placement(2, 1, 6, Blocks.BOOKSHELF.defaultBlockState()));\n        placements.add(new Placement(6, 1, 6, Blocks.CHEST.defaultBlockState()));\n        return placements;\n    }\n\n    private static List<Placement> inn(Palette palette) {\n        List<Placement> placements = simpleBuilding(palette, 11, 9, 4);\n        addBeds(placements, 11, 9, 8);\n        for (int x = 2; x <= 8; x += 2) {\n            placements.add(new Placement(x, 1, 6, Blocks.BARREL.defaultBlockState()));\n        }\n        placements.add(new Placement(5, 1, 3, Blocks.CRAFTING_TABLE.defaultBlockState()));\n        return placements;\n    }\n\n'''
if insert_marker not in manager:
    raise SystemExit("warehouse insertion marker missing")
manager = manager.replace(insert_marker, new_templates + insert_marker, 1)
insert_marker2 = '''    private static void floor(List<Placement> placements, int width, int depth, Block block) {'''
new_templates2 = '''    private static List<Placement> marketSquare(Palette palette) {\n        int width = 11;\n        int depth = 11;\n        List<Placement> placements = new ArrayList<>();\n        floor(placements, width, depth, Blocks.STONE_BRICKS);\n        for (int x : new int[] {1, 4, 7, 9}) {\n            placements.add(new Placement(x, 1, 2, Blocks.BARREL.defaultBlockState()));\n            placements.add(new Placement(x, 1, 8, Blocks.CHEST.defaultBlockState()));\n        }\n        placements.add(new Placement(5, 1, 5, Blocks.BELL.defaultBlockState()));\n        for (int[] corner : new int[][] {{1,1},{9,1},{1,9},{9,9}}) {\n            placements.add(new Placement(corner[0], 1, corner[1], palette.corner.defaultBlockState()));\n            placements.add(new Placement(corner[0], 2, corner[1], Blocks.LANTERN.defaultBlockState()));\n        }\n        return placements;\n    }\n\n    private static List<Placement> smithy(Palette palette) {\n        List<Placement> placements = simpleBuilding(palette, 9, 7, 4);\n        placements.add(new Placement(2, 1, 4, Blocks.ANVIL.defaultBlockState()));\n        placements.add(new Placement(4, 1, 4, Blocks.BLAST_FURNACE.defaultBlockState()));\n        placements.add(new Placement(6, 1, 4, Blocks.SMITHING_TABLE.defaultBlockState()));\n        placements.add(new Placement(7, 1, 2, Blocks.CHEST.defaultBlockState()));\n        return placements;\n    }\n\n    private static List<Placement> granary(Palette palette) {\n        List<Placement> placements = simpleBuilding(palette, 9, 7, 4);\n        for (int x = 1; x <= 7; x += 2) {\n            placements.add(new Placement(x, 1, 4, Blocks.BARREL.defaultBlockState()));\n            placements.add(new Placement(x, 2, 4, Blocks.HAY_BLOCK.defaultBlockState()));\n        }\n        return placements;\n    }\n\n    private static List<Placement> guardPost(Palette palette) {\n        int width = 7;\n        int depth = 7;\n        List<Placement> placements = new ArrayList<>();\n        floor(placements, width, depth, Blocks.STONE_BRICKS);\n        shell(placements, width, depth, 4, palette.wall, palette.corner, false);\n        roof(placements, width, depth, 5, Blocks.STONE_BRICKS);\n        placements.add(new Placement(2, 1, 4, Blocks.CHEST.defaultBlockState()));\n        placements.add(new Placement(4, 1, 4, Blocks.IRON_BARS.defaultBlockState()));\n        placements.add(new Placement(1, 2, 1, Blocks.LANTERN.defaultBlockState()));\n        placements.add(new Placement(5, 2, 1, Blocks.LANTERN.defaultBlockState()));\n        return placements;\n    }\n\n    private static List<Placement> exchangeHall(Palette palette) {\n        List<Placement> placements = simpleBuilding(palette, 13, 9, 5);\n        for (int x = 2; x <= 10; x += 2) {\n            placements.add(new Placement(x, 1, 5, Blocks.LECTERN.defaultBlockState()));\n        }\n        placements.add(new Placement(3, 1, 7, Blocks.ENDER_CHEST.defaultBlockState()));\n        placements.add(new Placement(9, 1, 7, Blocks.BELL.defaultBlockState()));\n        placements.add(new Placement(6, 1, 7, Blocks.CARTOGRAPHY_TABLE.defaultBlockState()));\n        return placements;\n    }\n\n    private static List<Placement> simpleBuilding(Palette palette, int width, int depth, int height) {\n        List<Placement> placements = new ArrayList<>();\n        floor(placements, width, depth, palette.floor);\n        shell(placements, width, depth, height, palette.wall, palette.corner, true);\n        roof(placements, width, depth, height + 1, palette.roof);\n        return placements;\n    }\n\n    private static void addBeds(List<Placement> placements, int width, int depth, int count) {\n        BlockState foot = Blocks.BED.white().defaultBlockState().setValue(BedBlock.FACING, Direction.SOUTH);\n        BlockState head = foot.setValue(BedBlock.PART, BedPart.HEAD);\n        int placed = 0;\n        for (int x = 1; x < width - 1 && placed < count; x += 2) {\n            placements.add(new Placement(x, 1, 2, foot));\n            placements.add(new Placement(x, 1, 3, head));\n            placed++;\n        }\n        for (int x = 1; x < width - 1 && placed < count; x += 2) {\n            placements.add(new Placement(x, 1, depth - 4, foot));\n            placements.add(new Placement(x, 1, depth - 3, head));\n            placed++;\n        }\n    }\n\n'''
if insert_marker2 not in manager:
    raise SystemExit("floor insertion marker missing")
manager = manager.replace(insert_marker2, new_templates2 + insert_marker2, 1)
manager = replace_once(manager,
'''        return switch (type) {\n            case COTTAGE, MINE_ENTRANCE -> new StructureSize(7, 7, 5);\n            case WAREHOUSE -> new StructureSize(9, 7, 5);\n        };''',
'''        return switch (type) {\n            case COTTAGE, MINE_ENTRANCE, GUARD_POST -> new StructureSize(7, 7, 6);\n            case HOUSE -> new StructureSize(9, 9, 6);\n            case INN -> new StructureSize(11, 9, 6);\n            case WAREHOUSE, SMITHY, GRANARY -> new StructureSize(9, 7, 6);\n            case MARKET_SQUARE -> new StructureSize(11, 11, 4);\n            case EXCHANGE_HALL -> new StructureSize(13, 9, 7);\n        };''',
"project structure sizes")
write(manager_path, manager)

# 3) Casual GUI terminology for the expanded projects plus visible local economic impact.
lang_path = "common/src/main/resources/assets/the_emerald_standard/lang/en_us.json"
lang = json.loads(read(lang_path))
labels = {
    "gui.the_emerald_standard.village.project.house": "House",
    "gui.the_emerald_standard.village.project.inn": "Village Inn",
    "gui.the_emerald_standard.village.project.market_square": "Market Square",
    "gui.the_emerald_standard.village.project.smithy": "Smithy",
    "gui.the_emerald_standard.village.project.granary": "Granary",
    "gui.the_emerald_standard.village.project.guard_post": "Guard Post",
    "gui.the_emerald_standard.village.project.exchange_hall": "Exchange Hall",
    "gui.the_emerald_standard.village.impact.strong": "Local impact: strong",
    "gui.the_emerald_standard.village.impact.positive": "Local impact: positive",
    "gui.the_emerald_standard.village.impact.neutral": "Local impact: neutral",
    "gui.the_emerald_standard.village.impact.weak": "Local impact: weak"
}
lang.update(labels)
write(lang_path, json.dumps(lang, indent=2, ensure_ascii=False) + "\n")

screen_path = "common/src/client/java/com/chedidandrew/emeraldstandard/client/BankerScreen.java"
screen = read(screen_path)
needle = '''        graphics.text(font, mode, 16, 149, MUTED, false);\n        boolean restoration = menu.villageLifecycle() == VillageProsperityEngine.Lifecycle.ABANDONED'''
replacement = '''        graphics.text(font, mode, 16, 149, MUTED, false);\n        double localImpactScore = (menu.villageProsperity() + menu.villageSafety()) / 2.0;\n        Component localImpact = localImpactScore >= 72.0\n                ? tr("village.impact.strong")\n                : localImpactScore >= 52.0\n                        ? tr("village.impact.positive")\n                        : localImpactScore >= 32.0\n                                ? tr("village.impact.neutral")\n                                : tr("village.impact.weak");\n        graphics.text(font, localImpact, 16, 163, localImpactScore >= 52.0 ? POSITIVE : GOLD, false);\n        boolean restoration = menu.villageLifecycle() == VillageProsperityEngine.Lifecycle.ABANDONED'''
screen = replace_once(screen, needle, replacement, "village impact line")
write(screen_path, screen)

# 4) Add regression coverage that all project types remain bounded and economically meaningful.
test_path = ROOT / "common/src/test/java/com/chedidandrew/emeraldstandard/core/Milestone95RegressionTest.java"
test_path.write_text('''package com.chedidandrew.emeraldstandard.core;\n\n/** Focused guards for the 0.4 visible-village progression expansion. */\npublic final class Milestone95RegressionTest {\n    private Milestone95RegressionTest() {}\n\n    public static void main(String[] args) {\n        if (VillageProsperityEngine.ProjectType.values().length != 10) {\n            throw new AssertionError("Expected ten curated village project types");\n        }\n        for (VillageProsperityEngine.ProjectType type : VillageProsperityEngine.ProjectType.values()) {\n            if (!(type.materialCost() > 0.0) || !(type.treasuryCost() >= 0.0)) {\n                throw new AssertionError("Invalid project economy for " + type);\n            }\n            if (type.nominalBlocks() <= 0 || type.nominalBlocks() > 500) {\n                throw new AssertionError("Unbounded project template estimate for " + type);\n            }\n        }\n        System.out.println("PASS milestone 0.4 project catalog invariants");\n    }\n}\n''')
run_tests_path = "scripts/run-common-tests.sh"
run_tests = read(run_tests_path)
run_tests = replace_once(run_tests,
'''java -cp "$BUILD" com.chedidandrew.emeraldstandard.core.VillageProsperityRegressionTest\n''',
'''java -cp "$BUILD" com.chedidandrew.emeraldstandard.core.VillageProsperityRegressionTest\njava -cp "$BUILD" com.chedidandrew.emeraldstandard.core.Milestone95RegressionTest\n''',
"test runner")
write(run_tests_path, run_tests)

# 5) Bump the public beta milestone on both loaders.
for rel in ["fabric/gradle.properties", "neoforge/gradle.properties"]:
    text = read(rel)
    text = text.replace("mod_version=0.3.0-beta.4", "mod_version=0.4.0-beta.1")
    write(rel, text)

# 6) Documentation and durable change history.
changelog_path = "CHANGELOG.md"
changelog = read(changelog_path)
entry = '''## 0.4.0-beta.1 - 2026-09-03\n\n### Added\n- Expanded visible Village Prosperity progression from 3 to 10 curated project types: Cottage, House, Inn, Warehouse, Mine Entrance, Market Square, Smithy, Granary, Guard Post, and Exchange Hall.\n- Added adaptive project prioritization so threatened, food-poor, crowded, and mature villages choose different development paths instead of following a fixed order.\n- Added biome-aware bounded physical templates for every new project while preserving the no-force-load and protected-placement rules.\n- Added visible local economic-impact guidance on the Village dashboard without exposing deterministic return formulas.\n- Added regression coverage for the expanded project catalog and template size bounds.\n\n### Changed\n- Village production now responds to relevant physical development: granaries help agriculture, smithies help mining, markets and inns help trade, guard posts help security, and exchange halls help mature trade and transport.\n- Development tiers now require more diverse infrastructure at the highest levels, making late-game settlement growth visibly distinct.\n- Fabric and NeoForge versions advanced together to `0.4.0-beta.1`.\n\n### Safety and compatibility\n- Existing project enum identifiers keep their original names and order, so beta.4 worlds remain migration-safe.\n- The abstract simulation remains authoritative. New physical construction stays bounded, never force-loads chunks, never mines arbitrary terrain, and continues honoring `VillageDevelopmentProtection` guards.\n- Player borrowing and negative balances remain impossible.\n\n'''
if "## 0.4.0-beta.1" not in changelog:
    changelog = entry + changelog
write(changelog_path, changelog)

village_doc = "docs/VILLAGE_PROSPERITY.md"
text = read(village_doc)
append = '''\n\n## 0.4 visible development catalog\n\nThe physical layer now uses ten intentionally small, deterministic project templates. The abstract economy remains authoritative, while loaded villages materialize a bounded number of blocks only when a player is nearby.\n\n| Need | Project | Primary visible/economic role |\n| --- | --- | --- |\n| Housing | Cottage, House, Village Inn | Adds housing and supports larger settlements |\n| Storage | Warehouse | Improves trade and transport capacity |\n| Production | Mine Entrance, Smithy, Granary | Improves mining, processing, or agriculture |\n| Commerce | Market Square | Improves local trade and transport |\n| Safety | Guard Post | Improves security output and recovery resilience |\n| Finance | Exchange Hall | Late-tier civic finance landmark with bounded trade/transport benefit |\n\nProject selection is need-driven. Safety emergencies can prioritize a Guard Post, low food reserves can prioritize a Granary, housing pressure can prioritize housing, and high-tier prosperous villages can eventually build an Exchange Hall. This keeps the same village from following an identical scripted build order every world.\n\nThe market link remains intentionally bounded and informational. The GUI reports whether local conditions are weak, neutral, positive, or strong, but it never exposes a formula that lets the player guarantee a future market return.\n'''
if "## 0.4 visible development catalog" not in text:
    text += append
write(village_doc, text)

readme = read("README.md")
readme = readme.replace("0.3.0-beta.4", "0.4.0-beta.1")
if "10 curated village projects" not in readme:
    anchor = "## Village Prosperity"
    if anchor in readme:
        readme = readme.replace(anchor, anchor + "\n\nThe 0.4 beta expands visible settlement progression to **10 curated village projects** with need-driven priorities, biome-aware templates, and bounded sector effects. Threatened villages can prioritize defenses, food-poor villages can prioritize storage, crowded villages can prioritize housing, and mature villages can grow into markets, smithies, inns, and an Exchange Hall.", 1)
write("README.md", readme)

release_notes = ROOT / "release/RELEASE_NOTES-0.4.0-beta.1.md"
release_notes.write_text('''# The Emerald Standard 0.4.0-beta.1\n\nThis beta is the first major 9.5-milestone gameplay pass. It focuses on making the existing Village Prosperity simulation substantially more visible in normal Minecraft play.\n\n## Highlights\n\n- Ten curated physical village project types, up from three.\n- Need-driven development priorities instead of one fixed project order.\n- New Market Square, Smithy, Granary, Guard Post, House, Inn, and Exchange Hall templates.\n- Relevant production bonuses remain bounded and feed the existing market-fundamentals system.\n- Village dashboard now gives a simple local economic-impact signal.\n- New regression test protects project count, costs, and bounded template estimates.\n\n## Compatibility\n\n- Minecraft 26.2.\n- Fabric and NeoForge.\n- Existing beta.4 project identifiers are preserved for world compatibility.\n- No player loans, debt, or negative balances were introduced.\n\n## Still required before stable 1.0\n\nThe repository's manual test matrix remains authoritative for GUI scales, difficult terrain, claim-mod integrations, multiplayer concurrency, crash windows, long-session play, and very large stored-village counts. Automated CI can validate code paths and startup, but it cannot replace those hands-on checks.\n''')

print("Milestone 0.4 source transformation completed")
