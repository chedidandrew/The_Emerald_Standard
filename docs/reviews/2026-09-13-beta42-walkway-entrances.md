# beta.42 — mine and market walkway dead ends

## Evidence

The two user captures, beta.33 / 760be6e8e613 (054226-C0E9C035 and
061407-FDA7FC4D), show completed mine project 3, mine_adit_03, with no
connector cells and entrance rejection at (-200, -61, 257). Completed market
project 6, market_crossroads_03, repeatedly surveys without a finished route.
The other older projects (1, 2, 4, 5) have completed connectors.

Read-only inspection of the corresponding project properties supplied the
frozen mine's revision 10, timber_forward/restrained, rustic/plains,
mirrored, rotation 0, stage 0 and design seed 4846542165548330479.
Its 765-operation native template has cobblestone at relative (5, 0, -2)
and rail at (5, 1, -2), directly over the reported start reference (5, -1, -2).

## Confirmed bugs and fixes

1. The connector treated a collision-free entrance rail as blocked headroom.
   The native reproduction fails with precisely cobblestone + dry north/south
   rail and a null admitted surface. The fix recognizes a dry, collision-free
   rail over a recognized existing access surface with a sturdy top. Routing,
   snapshots, paving and final verification preserve both rail and support.
   It does not clear tracks, acquire supply receipts for existing rails, or
   waive headroom, protection, fluid or inventory checks.
2. Project destination selection required a completed building, but not a
   completed connector. Replaying the six original project origins/types/
   orientations picks the disconnected mine as the market's nearest goal.
   After the rail fix alone, the independent market regression still fails.
   Project connections now require a verified connected branch, as Banks
   already did. With none available they seek a real village road. An older
   stranded project may also join a newer connected building; self-targeting
   and unconnected destination cycles are excluded.

These are shared normal/instant-mode finishing paths, not debug-only behavior.
The captures do not include a full terrain snapshot; this fixes the demonstrated
logic failures, not a guarantee that every intervening world cell is available.

Existing failed connectors retry through the normal loaded-village queue.
No structure blueprint, old trail cursor, completed-connection state, player
world or mod profile is rewritten. Existing partial paving keeps its receipts.
Blocked terrain, unloaded chunks, removed player paving, claims and occupancy
remain safety boundaries. The beta.41 terrace-seat restoration is retained;
roadside bench generation is unchanged.

## Regression coverage

WalkwayEntranceSelfTest reconstructs the exact 765-cell mine and exercises
retained rails/supports, bounded paving, partial-plan reload, all four vanilla
rail blocks/rotations, blocked headroom, claims on the footing and rail,
waterlogged tracks, inventories, and disallowed natural support replacement.
It replays the market's original nearest-destination failure, village-road
fallback, newer connected destinations and the mine becoming eligible only
after its connection is verified.

The focused native suite uses
JAVA_TOOL_OPTIONS=-Dthe_emerald_standard.walkwayEntranceSmokeOnly=true with
scripts/smoke-server.sh <loader>. It also runs existing detour/property/
occupancy/grade/unloaded/reload checks, Bank construction and Bank walkways,
lamp placement and Infrastructure bridge integration.

Before-fix Fabric logs recorded:
- fabric-run.dmHAc7: exact rail entrance rejected.
- fabric-run.ez6Wja: market chooses disconnected mine (rail fix alone).

PASS full common suite, including handbook, frozen plans and runtime wiring.
PASS final focused Fabric and NeoForge native suites, including all added safety
cases, bank construction/walkways, lamp placement and bridge integration.
PASS Fabric build (4m 26s) and NeoForge build (4m 9s).
PASS packaged-source/version parity and git diff --check.

Artifacts: 0.4.0-beta.42.
- Shared source SHA-256: cd4e5dac8ecf087a0ab26a50d6fc1a795b656f849b4fe0847145bae4d6d6a95b
- Fabric JAR SHA-256: d26ab284972a2f66fa2440829285a94c59c129057e87ab5a0bba8d2710153695
- NeoForge JAR SHA-256: 758af40b2be9fba9b5d29e57776214af97b53c5212652bb544665255031f9b24

Built locally; not installed into a mod profile and not committed or pushed.
No player world was changed.

## Handbook review

Updated guided terrain/path guidance and the compact/lectern terrain page:
connected destinations, older-to-newer joining, and retained mine tracks.
Updated handbook regressions. Reviewed HandbookChapters and EmeraldHandbook:
existing terrain routes remain appropriate. No item, recipe or creative content
changes. No new diagnostic language added to ordinary player-facing UI.

No in-game screenshot review or full default native suite claimed. The previous
broad-suite starter-cottage workstation navigation failure remains outside scope.
