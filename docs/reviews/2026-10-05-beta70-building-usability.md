# Beta70: building usability and saved-plan identification

## Scope and preservation

New TES approvals use architecture revision 16. Revision 12 through 15 assets and historical Bank mappings remain available; existing saved geometry, construction cursors, hashes, ownership and receipts are not rewritten. Corrections are explicit export-time changes, not a runtime heuristic applied to somebody's home. User worlds and the installed modpack are untouched.

The user explicitly chose exact vanilla geometry for plains_temple_4 and plains_weaponsmith_1 rather than adding ladders to their sealed upper spaces. Native imports remain exact, including these exceptions. These are intentionally not counted as corrected TES upper rooms.

## Geometry and audit

The independent room detector checks roofed upper spaces with native two-block clearance, floor bearing, a lower room, and a 2-by-2 occupiable area; shelves and narrow roof pockets are excluded. Reachability includes supported stairs/slabs, one-block steps with clearance, and wooden ladder hatches, but not closed iron hatches. Safe internal ladders or two-high internal timber doorways are added only if all existing declared access remains reachable. Furniture, block entities and intermediate frames are not excavated to make shafts. An additional assertion retains original room coordinates so a new light cannot conceal a lost route by changing the detector's output.

Matched beds are reversed as complete head/foot pairs when a nightstand lies beyond the old foot, preserving positions, color and valid facing. Chair correction recognizes unambiguous adjacent tables and lantern/flowerpot barrel nightstands: stair FACING denotes the high back, so the open seat faces the table. Architectural slabs and stairs are not blindly reversed. Freestanding wood and masonry lamp assemblies are turned away from two-high walls only into clear, non-reserved space; shared roof/eave bearings are retained.

Full assets and three outdoor-fit sets were exported in separate Gradle invocations, refreshing the full resource classpath before fit derivation. An initial same-invocation attempt correctly rejected stale full geometry rather than silently packaging inconsistent plans.

## Read-only identification

/tesstructure is available to ordinary players, using a 32-block loaded-only ray or the player's standing location on a miss. /tesstructure x y z accepts an explicit position. /emerald structure retains the existing administrator gate. The command reads a snapshot, checks inclusive three-dimensional bounds and dimension ownership, and reports every overlapping saved project rather than hiding a stale duplicate behind a completed building. Reports include native/template identity, project/village IDs, revision, outdoor fit, rotation/mirror, origin, progress and hash; Bank reports include the saved structure version and style. Chat reports can be copied.

Both explicit lookup and ray traversal use getChunkNow; unloaded terrain is never generated or force-loaded. Unknown historical Bank styles are not guessed. Naturally generated buildings without TES saved plans are explicitly identified as outside this lookup's coverage.

## Handbook and recipes

Reviewed HandbookChapters, English long reader and compact/lectern localization, EmeraldHandbook routing, HandbookRecipes, HandbookScreen and server recipe definitions. Added a guided Building identity section and compact page, describing the commands, read-only behavior, overlap reporting, unloaded chunks, revision boundary and exact vanilla exceptions. No new item or recipe is introduced. Updated resource and client regression checks together. The changed compact page is tested with the native font; unrelated pre-existing compact page overflow is not claimed fixed.

## Validation

Both loader full builds and the shared pure-Java regression suite passed. Native exact-import validation passes all 166 village templates, 1,153 layers and 83,682 exact block cells. The strict usability audit passes 408 upper rooms across 375 TES designs and 1,632 across their full and three outdoor-fit forms, with zero inaccessible detected rooms, native support and idempotence checks, and zero wall-facing recognized freestanding lamp assemblies. It records 297 changed designs, 699 new ladder cells and 584 reversed bed cells (292 pairs).

The full NeoForge native server suite passes 480 fixtures, preserving earlier terrain, stale-mine, construction ownership, forced lamp, bridge, loot and connection coverage. Expanded approved suites add all eight rotation/mirror combinations of the new Taiga upper-floor inn with partial restart and durable handover, plus command identification checks. An initial command fixture failed save validation because it mixed a legacy schema with blueprint metadata and completion without economic completion; the fixture is corrected to a valid saved blueprint. Production save validation is unchanged.

Final strict rerun passes both the original-room retention assertion and the 408/1,632-room audits. Fabric and NeoForge expanded approved suites each pass all 113 native fixtures, including lookup, current/historical Banks, all eight inn transforms, restart and handover. The complete Fabric native suite passes all 481 fixtures; full NeoForge passes 480 plus its separate 113-fixture run containing the new command. Logs are build/beta70-fabric-full-harness.log, build/beta70-neoforge-full-server.log and build/beta70-{fabric,neoforge}-approved-final-server.log. Intentional disposable-server shutdown after the positive success marker can produce Gradle termination text; the harness returns success only after that marker. Test workload catch-up warnings are not a live-modpack performance measurement.

The initial compact identity page measured 19 native lines including title/icons/navigation and failed. Its shorter six-line body now fits; the full guidance remains in the long reader. Final client log build/beta70-client-identity.log contains both the compact native-book success and all long-form chapter/font/search checks. The initial failed log is retained separately. Shared regressions pass again after the localization change; final assemble passes on both loaders.

Both staged binary/source pairs pass metadata/class verification. All 6,375 packaged native assets match their recorded SHA-256 identities, and both JARs match current canonical source SHA-256 194aa10d523b7289f572097de84d1f4206c9c2e6e379d2c1b8427fc5eda5f981. Staged artifacts and SHA256SUMS are in build/release-beta70/{fabric,neoforge}. A first metadata verifier invocation against build/libs rejected its accumulated historical releases as expected; verification was rerun against the isolated two-JAR release directories without deleting previous artifacts.
