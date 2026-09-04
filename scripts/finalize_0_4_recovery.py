from pathlib import Path


def replace_once(path: str, old: str, new: str) -> None:
    file = Path(path)
    text = file.read_text(encoding="utf-8")
    if old not in text:
        raise SystemExit(f"Expected text was not found in {path}: {old[:140]!r}")
    file.write_text(text.replace(old, new, 1), encoding="utf-8")


def append_once(path: str, marker: str, addition: str) -> None:
    file = Path(path)
    text = file.read_text(encoding="utf-8")
    if addition.strip() in text:
        return
    if marker not in text:
        raise SystemExit(f"Append marker was not found in {path}: {marker[:140]!r}")
    file.write_text(text.replace(marker, marker + addition, 1), encoding="utf-8")


# Never place a block that can be converted directly into bank capital.
replace_once(
    "common/src/minecraft/java/com/chedidandrew/emeraldstandard/minecraft/VillageProsperityManager.java",
    "        placements.add(new Placement(6, 1, 7, Blocks.EMERALD_BLOCK.defaultBlockState()));",
    "        placements.add(new Placement(6, 1, 7, Blocks.BOOKSHELF.defaultBlockState()));",
)
replace_once(
    "common/src/minecraft/java/com/chedidandrew/emeraldstandard/minecraft/VillageProsperityManager.java",
    """                        || placement.state.is(Blocks.CARTOGRAPHY_TABLE)) {""",
    """                        || placement.state.is(Blocks.CARTOGRAPHY_TABLE)
                        || placement.state.is(Blocks.EMERALD_BLOCK)
                        || placement.state.is(Blocks.DIAMOND_BLOCK)
                        || placement.state.is(Blocks.GOLD_BLOCK)
                        || placement.state.is(Blocks.NETHERITE_BLOCK)) {""",
)
replace_once(
    "common/src/minecraft/java/com/chedidandrew/emeraldstandard/minecraft/VillageProsperityManager.java",
    '                                    + " contains an unintended villager workstation at "',
    '                                    + " contains an unintended workstation or currency block at "',
)

# Project enum expansion is a persistent-format change. Format 8 makes beta.4 reject the
# new catalog cleanly instead of parsing a format-7 file with unknown project identifiers.
replace_once(
    "common/src/main/java/com/chedidandrew/emeraldstandard/core/EconomyState.java",
    "    public static final int FORMAT_VERSION = 7;",
    "    public static final int FORMAT_VERSION = 8;",
)
replace_once(
    "common/src/minecraft/java/com/chedidandrew/emeraldstandard/minecraft/DebugFlightRecorder.java",
    '    public static final String MOD_VERSION = "0.3.0-beta.4";',
    '    public static final String MOD_VERSION = "0.4.0-beta.1";',
)

migration_test = "common/src/test/java/com/chedidandrew/emeraldstandard/core/JournalAndMigrationRegression.java"
replace_once(
    migration_test,
    """        testLegacyProjectMetadataDefaults(root.resolve("format-six-project-defaults"));
        testVillageMarketShadowPersistence(root.resolve("market-shadow"));""",
    """        testLegacyProjectMetadataDefaults(root.resolve("format-six-project-defaults"));
        testFormatSevenProjectCatalogMigration(root.resolve("format-seven-project-catalog"));
        testVillageMarketShadowPersistence(root.resolve("market-shadow"));""",
)
new_migration_method = """    private static void testFormatSevenProjectCatalogMigration(Path directory)
            throws Exception {
        Path save = directory.resolve("the_emerald_standard.properties");
        EconomyState state = EconomyState.fresh(901L, 0L, 0L);
        UUID villageId = UUID.fromString("00000000-0000-0000-0000-000000009501");
        EconomyState.VillageRecord village = state.village(villageId);
        EconomyState.VillageProject cottage = new EconomyState.VillageProject();
        cottage.projectId = 1L;
        cottage.type = VillageProsperityEngine.ProjectType.COTTAGE;
        cottage.approvedDay = 1L;
        cottage.totalBlocks = cottage.type.nominalBlocks();
        village.projects.add(cottage);
        village.projectSerial = 1L;
        state.save(save);

        Properties formatSeven = readProperties(save);
        formatSeven.setProperty("format", "7");
        refreshChecksum(formatSeven);
        writeProperties(save, formatSeven);

        EconomyState migrated = EconomyState.load(save, 999L, 0L, 0L);
        require(migrated.existingVillage(villageId) != null
                        && migrated.existingVillage(villageId).projects.size() == 1
                        && migrated.existingVillage(villageId).projects.getFirst().type
                                == VillageProsperityEngine.ProjectType.COTTAGE,
                "Format 7 project catalog did not load into format 8");

        EconomyState.VillageProject house = new EconomyState.VillageProject();
        house.projectId = 2L;
        house.type = VillageProsperityEngine.ProjectType.HOUSE;
        house.approvedDay = 2L;
        house.totalBlocks = house.type.nominalBlocks();
        migrated.existingVillage(villageId).projects.add(house);
        migrated.existingVillage(villageId).projectSerial = 2L;
        migrated.save(save);

        Properties upgraded = readProperties(save);
        require("8".equals(upgraded.getProperty("format")),
                "Format 7 save did not upgrade to format 8");
        EconomyState reloaded = EconomyState.load(save, 999L, 0L, 0L);
        require(reloaded.existingVillage(villageId).projects.stream()
                        .anyMatch(project -> project.type
                                == VillageProsperityEngine.ProjectType.HOUSE),
                "Expanded project identifier did not survive format 8 reload");
    }

"""
replace_once(
    migration_test,
    "    private static void testVillageMarketShadowPersistence(Path directory) throws Exception {",
    new_migration_method
    + "    private static void testVillageMarketShadowPersistence(Path directory) throws Exception {",
)
replace_once(
    migration_test,
    '                "Market-shadow save was not written in format 7");',
    '                "Market-shadow save was not written in the current format");',
)

# Repository documentation must describe the actual persistent compatibility boundary.
replace_once(
    "README.md",
    "- Save format 7 uses a required magic identifier, mandatory core fields, and SHA-256 checksums.",
    "- Save format 8 uses a required magic identifier, mandatory core fields, and SHA-256 checksums.",
)
replace_once(
    "README.md",
    "Beta.3 upgrades beta.1 and beta.2 format-6 worlds to format 7. Once beta.3 has saved a world, beta.1 and beta.2 deliberately reject that newer file instead of loading it and silently discarding the new safety state. Keep a pre-upgrade world backup if you may need to downgrade.",
    "The 0.4 beta upgrades beta.4 format-7 worlds to format 8 because the expanded persistent project catalog adds new project identifiers. Beta.4 deliberately rejects format 8 instead of parsing unknown projects or falling back to stale data. Keep a pre-upgrade world backup if you may need to downgrade.",
)
replace_once(
    "docs/ARCHITECTURE.md",
    "## Persistent data format 7\n\nFormat 7 includes:",
    "## Persistent data format 8\n\nFormat 8 includes:",
)
replace_once(
    "docs/ARCHITECTURE.md",
    "Beta.1 and beta.2 format-6 saves migrate forward to format 7. After beta.3 writes the upgraded save, beta.1 and beta.2 reject it as a future format instead of silently falling back to an older backup and stripping the new state. Downgrading therefore requires restoring a pre-upgrade world backup. Unsupported formats newer than 7 are likewise rejected without overwriting them.",
    "The 0.4 beta migrates beta.4 format-7 saves forward to format 8. The version boundary is required because the persistent Village Prosperity project catalog now contains identifiers that beta.4 does not understand. Beta.4 rejects format 8 as a future format without stale-backup fallback. Downgrading therefore requires restoring a pre-upgrade world backup. Unsupported formats newer than 8 are likewise rejected without overwriting them.",
)

append_once(
    "docs/TESTING.md",
    "- Genuine format-5 account/bank-anchor migration, beta.1/beta.2 format-6 upgrade, safe defaults for older project records, and rejection of future formats without stale-backup fallback\n",
    "- Format-7 to format-8 migration for the expanded Village Prosperity project catalog, including persistence of a new House project identifier\n",
)
replace_once(
    "docs/TESTING.md",
    "The beta.3 prerelease publisher requires a successful `main`-push `build.yml` run for the exact source commit, downloads rather than rebuilds that run's exact Fabric and NeoForge binary/source artifacts, stages them in a draft, verifies the complete public filename set and bytes, and records artifact IDs, workflow digests, and release-asset SHA-256 checksums.",
    "Any 0.4 beta prerelease publisher must require a successful `main`-push `build.yml` run for the exact source commit, download rather than rebuild that run's exact Fabric and NeoForge binary/source artifacts, verify the complete public filename set and bytes, and record artifact IDs, workflow digests, and release-asset SHA-256 checksums.",
)
replace_once(
    "docs/TESTING.md",
    "project-block reconciliation after chunk rollback, or cross-file bank-marker/chunk atomicity; beta.3 is intentionally published as a prerelease while that wider validation continues.",
    "project-block reconciliation after chunk rollback, or cross-file bank-marker/chunk atomicity; the 0.4 line remains a beta while that wider validation continues.",
)

append_once(
    "CHANGELOG.md",
    "- Replaced the Exchange Hall emerald block with decorative green glass and added a smoke-test ban on generated emerald, diamond, gold, and netherite blocks so village construction cannot mint investable currency.\n"
    if "decorative green glass" in Path("CHANGELOG.md").read_text(encoding="utf-8")
    else "- Removed unintended barrel, lectern, and cartography-table job sites from prosperity templates.\n",
    "- Removed the Exchange Hall emerald block and added a smoke-test ban on generated emerald, diamond, gold, and netherite blocks so village construction cannot mint investable currency.\n"
    "- Advanced persistent storage to format 8 so beta.4 cleanly rejects saves containing the expanded project catalog instead of attempting to parse unknown identifiers.\n"
    "- Updated diagnostic report metadata to identify `0.4.0-beta.1` correctly.\n",
)
append_once(
    "release/RECOVERY_AUDIT-0.4.0-beta.1.md",
    "- Replaced accidental barrel, lectern, and cartography-table job sites in non-industrial prosperity templates.\n",
    "- Removed the Exchange Hall emerald block and added validation that no prosperity template generates emerald, diamond, gold, or netherite blocks that could be converted into bank capital.\n"
    "- Advanced persistence to format 8 so beta.4 readers reject the expanded project catalog before attempting project deserialization or stale-backup recovery.\n"
    "- Updated the debug flight-recorder version label to `0.4.0-beta.1`.\n",
)
append_once(
    "release/RELEASE_NOTES-0.4.0-beta.1.md",
    "## Recovery hardening\n",
    "\nThe recovered candidate stores worlds as format 8. Existing beta.4 format-7 worlds migrate forward automatically, while beta.4 readers reject the upgraded file as a future format. Keep a pre-upgrade world backup before testing if a downgrade may be needed. The Exchange Hall no longer generates an emerald block, and live template validation rejects high-value currency blocks. Debug reports now identify the candidate as `0.4.0-beta.1`.\n",
)

# Reset build status because this hardening changes executable source after the previous green run.
Path("release/BUILD_STATUS.md").write_text(
    """# Build status for 0.4.0-beta.1

## Candidate status

- Baseline: verified `0.3.0-beta.4` main branch
- Candidate branch: `fix/recover-0.4.0-beta.1`
- Persistent format: 8
- Automated verification: pending for the final hardened source commit
- Manual test matrix: `docs/MANUAL_TEST_MATRIX-0.4.md`, not yet completed

## Final hardening included

- Safe recovery of the intended 10-project Village Prosperity update
- Minecraft 26.2 API correction
- Duplicate-template and unintended-workstation validation
- Ban on generated emerald, diamond, gold, and netherite blocks
- Format-7 to format-8 migration coverage for the expanded project catalog
- Correct `0.4.0-beta.1` debug-report metadata

This file is updated with the exact successful workflow run and merge commit before prerelease publication.
""",
    encoding="utf-8",
)
