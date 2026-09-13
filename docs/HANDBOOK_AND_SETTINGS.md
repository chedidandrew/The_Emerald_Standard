# Handbook reader and configuration repair

## Creative catalog and ongoing handbook review

The Creative inventory now has a dedicated **The Emerald Standard** tab containing every
registered mod item, including the desk, handbook, fence and separate Banker/Builder eggs.
Eggs are intentionally creative-only. Manual caution fences can be crafted and recovered;
automatic site fences cannot be farmed for drops. The Crafting recipes chapter contains all
four usable-item recipes (including the newspaper) and the creative tools explanation.

The current handbook has 68 guided sections in 16 chapters, plus 61 compact fallback pages.
The beta.21 newspaper wording describes the broad printed page, retained rolled/open states
and the expected loss of fine detail in tiny inventory icons. Both reader forms were reviewed.
The beta.20 construction-damage section explains no-drop breaking, automatic finishing repairs,
occupancy limits, delayed storage access/loot, inventory preservation and ambiguous legacy repair records.
New compact pages were appended, preserving existing page links. The root AGENTS.md requires
reviewing and updating the handbook alongside future player-facing changes, including diagrams,
examples and safety limitations. Runtime catalog tests detect missing items, block items and
custom-mob eggs. Focused client mode: the_emerald_standard.clientCreativeOnly=true.

## Site-search handbook review (beta.17)

Building-project guidance and its compact page explain the Town search report, 10–30 second
unreserved-search cooldown, full-sweep/activation caveats and ordinary /emerald debug workflow.
Candidate evidence is observed, not guessed; no clearing or extra funding is prescribed merely
because a plan has no site. Recipes, section/page counts and construction safety remain unchanged.
See [validation](reviews/2026-09-12-beta17-site-search.md).

## Newspaper handbook review (beta.16)

The long-form newspaper section and compact fallback page now explain the continuous cover/Contents/story route, scrolling within long articles, full-view Next/Previous, reverse entry at the prior story's end, and bounded edition ends. Story numbers and visible text ranges replace misleading one-page-per-story labels. The same section describes the simple masthead and subtle GUI-native paper wear; recipes and the Desk archive browser remain unchanged. Native real-font checks still cover all 60 fallback pages and 67 guided sections. See [validation](reviews/2026-09-12-beta16-newspaper-reading.md).

## Live-market handbook review (beta.15)

Three guided Markets sections explain actual intraday quotes, Today/Yesterday, all eight ranges, normalized comparisons, truthful history availability and time-command behavior. Three compact fallback pages are appended, preserving existing links. The general economic-clock explanation now distinguishes 15-second quotes from daily settlements. Both loader client checks exercise the real font, compact 60-page budget and long-form scrolling; see the [validation record](reviews/2026-09-12-beta15-live-market.md).

## District terrain and desk controls

The Village growth chapter now includes a guided district-map section: loaded/cached surface
terrain, unknown checkers, coverage versus land claims, coordinate bounds, paging and navigation.
The desk crafting section explains crouch-placement, including offhand and failed placement.
Compact desk/terrain pages include the same control and coverage caveats within book limits.

## Optional Guard Villagers settings

World settings include the optional guard toggle,
Safety per guard and bonus cap. The handbook includes a guard section with
eligibility, examples and stale-observation behavior in Village growth. Hover the
Town summary to see observed guards and their Safety modifier. See
[Guard Villagers compatibility](GUARD_VILLAGERS.md). Historical validation counts
below describe the earlier builds, not this addition.

## District planning map

Open **Town → District map** at a desk linked to a village. North is negative Z.
The map shows the city's recorded district centers, Bank locations and project
footprints; hover for district membership, residents/housing, status, progress and
X/Z coordinates. Gold district centers identify the district served by this desk.
Banks are point markers, not surveyed footprints; built means recorded construction
completion, not a fresh building-integrity check.

Drag or use arrow keys to pan; scroll or use +/- to zoom. **Fit page** frames the
current areas and markers, **Focus** fits this desk's district coverage, and **< / >** pages
through saved sites (96 per page). Other pages' markers are not drawn. Unsited
projects are counted but cannot be located until a site is selected.

The surface background uses native map colors from already-loaded client chunks;
checkers are unknown terrain. Coverage borders are developed survey rectangles,
not land claims or a vanilla-building census. The map never forces chunks to load
or alters which district receives gifts.
Only open maps refresh saved sites, at most once per 100 server ticks; page requests are
throttled to ten ticks. Snapshot memory and render/transport work are page-bounded;
collecting a page still visits saved city/site metadata. Very large cities are not
a promise of constant-time metadata lookup or unlimited memory.

The focused client smoke mode `the_emerald_standard.clientDistrictMapOnly=true`
checks real rendering and pan/zoom input at GUI scales 2 and 4 in a disposable
fixture, without opening a player save.

## Beta.5 addition: a guided player manual

The held handbook now has dedicated long-form localization rather than reusing the
short text designed for the legacy book's narrow pages. All 16 chapters have been
rewritten as guided prose: 55 sections and roughly 8,200 words. Existing handbook
items use the new reader content after updating the mod; no replacement item or
world migration is needed. Compact lectern/legacy pages remain compatible.

Getting started walks through a first village visit and an inventory-funded payment.
The Fund chapter explains all seven purposes, the three gift types, reserve and
spending rules, project-family matching, donor recognition, and worked examples.
Village growth explains Town's scores and units, the Prosperity weights, housing
census limits, tier thresholds, production versus stock changes, farms, lighting,
districts and upkeep. The remaining chapters explain account routes, products,
trade, construction, recovery, timing, news and terminology in context.

Numbers are described as current model rules or configurable defaults, not promises
that a gift instantly grants points or finishes a building. A regression checks the
seven routine Fund conversions against the economy, including that Security and
Housing do not directly add Safety or housing capacity. No economic rules changed.

Search still filters chapters, but choosing a result now scrolls to its matching
section, preferring heading matches. Searching "Security:" opens that purpose
inside the Fund chapter. Animated recipe cards remain and now include their fuller
explanatory prose below the preview.

See [expanded handbook validation](reviews/2026-09-11-expanded-handbook-validation.md).

## Beta.5 addition: animated crafting recipes

The **Crafting recipes** chapter shows native 3x3 crafting grids and output icons for the Exchange Desk, Starter Handbook and Construction Caution Fence. Ingredient alternatives cycle every 0.9 seconds; the desk cycles through matching plank types, and the shapeless handbook cycles through all 72 distinct two-slot arrangements. Mixed matching plank types also work. The bundled fence recipe has fixed ingredients and makes four barriers. Hover over a card to freeze the preview and over an item to read its tooltip. **Pause recipes / Resume recipes** provides a keyboard-accessible alternative.

Cards scale with the reader and available space, scroll with their section, and are clipped clear of navigation controls. Minecraft item models and textures are used, including resource-pack replacements. These are previews only: no crafting, item consumption, recipe unlocking, world changes, or server tick work occurs.

When in a world, the reader prefers synchronized, unlocked shaped/shapeless recipe displays for all three outputs and cycles through multiple known recipes if present. Before unlocking, it teaches the bundled JSON recipe using the world's ingredient tags, explicitly labeled **Default recipe (datapacks may change it)**. Minecraft does not send every locked datapack recipe to the client, so the reader does not claim those defaults describe all server overrides. Close and reopen the reader after recipes/tags change. Before joining a world, vanilla tag defaults and display-only item holders allow the Settings handbook to show previews without modifying registries. Lectern/vanilla book pages keep their text-only fallback.

The historical beta.4 implementation and validation notes below predate these recipe cards.

## Request and scope

Andrew reported that the starting book was too small, its text felt too large, and its presentation was messy. The Mod Menu config action also did not work. This change is based on development-branch source d21b2ff267e9c856e0bd0cc2ec31456d1f933b68 and advances both loaders to 0.4.0-beta.4. It does not merge the architecture branch into main or claim a stable release.

## Changes

- Keep the existing handbook item id, first-join delivery rules, replacement recipe, saved stacks, and written content. The registered item now opens a custom reader when held; other written books are unchanged. Lectern usage retains vanilla behavior.
- Replace the narrow held-book page with a responsive, wide reader, a chapter sidebar, search, previous/next controls, keyboard navigation, scrollable text, and an explicit Done button.
- Regroup the guide into 16 chapters. Remove repeated inline navigation and decorative object glyphs from this reader, reflow prose, retain section headings, and spell out the crafting grid. Do not delete the original 46-page content used by legacy book views.
- Default reader text to 90 percent and expose A-/A+ controls from 80 through 120 percent. Store the preference in config/the_emerald_standard-client.properties separately from world settings. Changing reader size does not change Minecraft's global GUI scale.
- Register an optional Fabric Mod Menu API entry point and a NeoForge config-screen extension. The same screen is accessible through Settings in the handbook, even without Mod Menu installed.
- Present all 27 existing world settings in a paged editor when the local integrated world is open. Boolean controls and integer fields keep edits until Apply; Done discards unapplied world edits. Reader-size changes save immediately.
- Main-menu and remote-server sessions do not pretend to edit a world's economy. They expose reader settings and explain the ownership boundary. Remote administrator configuration remains on the server; this patch does not introduce a remote config-write protocol.
- Validate the whole proposed configuration before writing. Reject unknown, empty, malformed, out-of-range, externally changed, or stale-world edits. Apply changes on the owning integrated server thread only after atomic file replacement, then show success or failure. Preserve every pre-existing config key, range, and default.
- Replace the client smoke helper's fixed eight-second readiness assumption with a bounded resource/language readiness check. Retain its real-font validation for the legacy book, and add actual reader/settings opening and screenshot capture.
- Add build-time file and geometry tests and separate Fabric client runs with and without optional Mod Menu. CI remains read-only, retains all earlier test jobs, and uploads screen evidence alongside logs.

## Using the update

Install only the JAR matching the loader, replacing the old mod file. Minecraft 26.2 and Java 25 remain required. Fabric still requires matching Fabric API; Mod Menu is optional. The compile-time Mod Menu API target is 20.0.1 for 26.2. No configuration-library dependency is added to the playable mod.

Use an existing or newly crafted handbook to open the new reader. A- makes its text smaller; A+ makes it larger. Search finds chapter titles and body text. Scroll the body or use Up/Down, Page Up/Page Down, Home/End; Left/Right change chapters. Tab navigates buttons and the search field. Escape returns to the previous screen.

In single player, open the world, then Mods > The Emerald Standard > Configure, or Handbook > Settings. Adjust world settings and press Apply. Starting book and discovery hint controls future eligible first-join delivery; disabling it does not delete an already received book or reset the one-time marker. Reader size works even on the main menu or a multiplayer server.

## Compatibility and boundaries

The economy format remains 18. Existing architecture, balances, trading, lending, and recovery logic are unchanged. The previously documented inventory-recovery failure scenario and synchronous whole-economy save cost are not fixed by this UI work. The custom reader warns against continuing ordinary item transfers during repeated unresolved recovery instead of asserting that every journal failure is safe.

The new surrounding interface labels are English in this patch; the guide's existing content uses its existing translation keys. Long content scrolls instead of being forced into a fixed book-page height. The reader does not modify other mods' books or global font settings.

## Validation record

For the subsequent Windows real-client checks, complete X11 cursor diagnosis, strengthened
two-process UI tests and exact-commit CI results, see the [final validation record](reviews/2026-09-09-beta4-final-validation.md).
The prototype results below are historical, not the final candidate's certification.

The new pure geometry/preferences/configuration suite passed in the review container against the implementation prototype. This checks default and persisted text size, bounds, all 27 settings, valid writes, invalid-value rejection without disk/runtime mutation, stale snapshots, wrong-world paths, and external-file conflicts. This is not a claim of a local Minecraft launch.

The committed build must pass its own common tests, both loader builds and package checks, both dedicated servers, and all three client variants before a verified JAR handoff. Client screenshots are generated only under the explicit CI smoke property in isolated temporary game directories. Hands-on multiplayer testing and the existing manual matrix remain unclaimed.

## Tooltip and map revision (beta.22)

The guided district-map section now explains persistent local exploration, last-seen
terrain, resolution and workload limits, world isolation, disk failure and cache reset.
The compact terrain page gives the essentials. Investment history explains sampling,
price versus total returns and the shorter chart hovers. Financial previews and
irreversible/penalty warnings remain at the point of action; repeated index definitions
and general simulation explanations no longer cover the dashboard on hover.
No handbook section, recipe or chapter was removed.

## Change history

Initial implementation: wide held-handbook reader, separate persistent text preference, optional loader config entry points, guarded integrated-world editor, readiness-gated client tests, screen captures, and beta.4 build identity. No existing world or installed player instance was modified while making this patch.
