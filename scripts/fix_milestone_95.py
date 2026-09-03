#!/usr/bin/env python3
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
path = ROOT / "common/src/minecraft/java/com/chedidandrew/emeraldstandard/minecraft/VillageProsperityManager.java"
text = path.read_text()

replacement = '''    private static int workerPreference(
            Villager villager, VillageProsperityEngine.ProjectType projectType) {
        String profession = professionId(villager.getVillagerData().profession());
        boolean preferred = switch (projectType) {
            case MINE_ENTRANCE, SMITHY -> profession.contains("mason")
                    || profession.contains("toolsmith")
                    || profession.contains("weaponsmith")
                    || profession.contains("armorer");
            case WAREHOUSE, MARKET_SQUARE, EXCHANGE_HALL -> profession.contains("cartographer")
                    || profession.contains("librarian")
                    || profession.contains("cleric");
            case GRANARY -> profession.contains("farmer")
                    || profession.contains("fisherman")
                    || profession.contains("butcher");
            case GUARD_POST -> profession.contains("armorer")
                    || profession.contains("weaponsmith")
                    || profession.contains("toolsmith");
            case COTTAGE, HOUSE, INN -> profession.contains("none")
                    || profession.contains("nitwit")
                    || profession.contains("farmer");
        };
        return preferred ? 0 : 1;
    }
'''

pattern = re.compile(
    r"    private static int workerPreference\(.*?\n    \}\n\n    private static void spawnPendingSettler\(",
    re.DOTALL,
)
match = pattern.search(text)
if not match:
    raise SystemExit("Could not locate workerPreference method")
text = pattern.sub(replacement + "\n    private static void spawnPendingSettler(", text, count=1)
path.write_text(text)
print("Normalized worker preference coverage for all project types")
