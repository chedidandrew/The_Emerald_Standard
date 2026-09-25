# Animated handbook recipe validation — 2026-09-10

Unreleased beta.5 working-tree update, limited to the custom handbook reader and its tests/documentation. Earlier working-tree changes are preserved. No user save, installed mod, recipe cost, recipe unlock or Git remote was modified.

## Behavior

- Exchange Desk and Starter Handbook receive native 3x3 crafting cards and output icons/counts.
- Ingredient choices advance every 900 ms. Matching plank types cycle; mixed matching planks remain valid. The shapeless book recipe enumerates all 72 distinct placements of its two ingredients.
- Hover freezes the previews and exposes native item tooltips. Pause/Resume is also a normal keyboard-accessible button.
- Cards honor reader text size, shrink to fit their wrapped heading and viewport, and scroll with the chapter. Body clipping keeps icons clear of the footer.
- The reader snapshots known server recipe displays when available. Otherwise the bundled JSON recipe is explicitly labeled as a default; in-world ingredient tags are used. Locked datapack overrides cannot be inferred from the client recipe book. Reopen after recipe/tag changes.
- Before world entry, display-only holders provide item models/names without mutating the global item registry, whose components are not yet bound in Minecraft 26.2.
- The lectern's vanilla book fallback remains text-only. The lectern in the request was treated as a visual cycling example, not an additional recipe to teach.

## Checks performed

- `scripts/run-common-tests.sh`: passed, including recipe timing, pause/resume, and exhaustive unique shapeless arrangements for one through four ingredients; existing recipe JSON regressions also pass.
- Full Fabric and NeoForge `build` tasks: passed. After the final client layout refinement, both loaders were recompiled by the client runs and their binary/source JARs regenerated with `assemble`.
- Both final clients: two independent processes each, with persisted reader preferences. All functional recipe, reader, settings, dashboard and construction-animation smoke assertions passed and each process reached controlled shutdown.
- Recipe captures use a 1280x1000 window and assert actual GUI scales 2 and 4, with reader text at 120%. Checks cover advancing frames, hover freeze, Pause/Resume widgets, both recipe sections, full-card bounds, scrolling and native item names. Screenshots were visually inspected on both loaders, including the two-line heading case.
- Both packaged JAR checks passed; the verifier now requires the new recipe classes.
- Modified tracked text files passed `git diff --check`.

The strict all-errors client log gate does **not** pass on this host: each launch logs OSHI's `Unable to locate English counter names in registry Perflib 009` during Minecraft system-report initialization. Functional assertions and shutdown still pass; no other ERROR/FATAL entry was found in the final runs. The strict gate was not weakened to hide this host issue. No claim is made that the user's full modpack, arbitrary third-party recipe displays or multiplayer datapack overrides were exercised.

## Evidence

- Common suite: `build/handbook-common-tests.log`.
- Full builds: `build/handbook-fabric-build.log`, `build/handbook-neoforge-build.log`.
- Final first-launch/restart logs: `build/handbook-fabric-releasecheck.log`, `build/handbook-neoforge-releasecheck.log`.
- Screenshots: `build/client-smoke/handbook-recipes-{fabric,neoforge}-releasecheck/screenshots/tes-reader-ci/recipes-*.png`.

## Test artifacts (SHA-256)

- `fabric/build/libs/the-emerald-standard-fabric-0.4.0-beta.5.jar`: `2a97d73f4ed1cbff51258fb2a36365b1122c6731e8ba29226584606394aba542`
- `neoforge/build/libs/the-emerald-standard-neoforge-0.4.0-beta.5.jar`: `9b0159280df3afba7bb253d47c6d6a075592b0b52adbeb0fa245e5e01d4722c1`

Install only the binary matching the loader, replacing the older TES JAR rather than installing both versions. These are local test artifacts, not a published release.
