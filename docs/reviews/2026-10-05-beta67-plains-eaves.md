# Beta 67: one-block plains roof overhangs

## Scope and implementation

New TES plains plans project flush oak roof courses one block beyond their wall edges. Existing projecting eaves are retained, rather than extended again. The authoring pass adds cells only; it does not move walls, replace old roof cells, change pitch/ridge height, remove fixtures or alter beds and circulation. Compact upper-room walls, ceiling plates and furniture are excluded from the roof surface. Occupied cells, intersecting roof ranges and three-block standing clearance remain protected. A newly shaded work tile uses the existing native-supported, spaced lighting admission.

Revision 14 and its three outdoor-fit catalogs are exported into separate resources with measured bounding envelopes and SHA-256 identities. Production reads frozen assets, never the mutable authoring pass. Revisions 12/13 and revision-13 outdoor fits remain resolvable. Bank versions 19–21 and fitted versions 80–82, 96–98 and 112–114 select the new geometry; historical full/fitted Bank version decoding is unchanged. The save format remains 45. Vanilla-exact templates and live user worlds are untouched.

## Verification

The native catalog gate checks all three full releases, both sets of 1,125 nested outdoor fits, stage-native support, construction ordering, door/ladder/bed integrity, reserved bounds and Bank desk/entrance anchors. The narrow revision gate proves every old cell survives in revision 14, ridge elevations and bed counts stay unchanged, every added roof cell is within one block of its source course, and 300 other-biome full assets plus 900 other-biome fit assets remain byte-identical. Fixture negatives cover double growth and a balcony that must retain three clear blocks.

The first broad review exposed a two-block balcony passage created by an eave; the authoring pass now checks uncovered balconies as well as existing covered circulation. An initial lighting gate found a newly shaded market tile; the existing lighting algorithm supplies a supported, spaced fixture. Neither admission rule was weakened. Final catalog coverage adds 2,812 roof blocks and two lighting/support cells across 73 plains designs; the remaining two retain their existing geometry.

The production server fixtures include all eight rotation/mirror combinations of a new core-only plains building, every new outdoor fit, all three fitted plains Bank variants, historical plans, real saved hash/restart replay, native connection settling and usable doors. The first native run exposed the stale palette revision upper bound; it now derives from the production catalog revision, and the catalog gate covers material lookup for every frozen revision. Both dedicated-server targeted runs passed all 48 fixtures after this fix. Validation results and packaged identities are recorded below after the final runs.

## Handbook review

Both the long reader architecture chapter and compact architecture page explain one-block plains eaves and unchanged historical plans. HandbookChapters routing and EmeraldHandbook localization remain accurate. HandbookRecipes, HandbookScreen and server recipe definitions were reviewed: this geometry change introduces no recipe/item/block, so unrelated animated recipes are unchanged. Handbook regression checks cover both explanations.

No live-profile installation, world edits, manual visual playtest or modpack performance benchmark is claimed. Test a world copy and retain the previous JAR/backup. Existing approved buildings do not receive automatic roof alterations; use newly approved TES buildings with vanilla-only selection disabled.

## Final release results

- Common regression suite: passed (`build/beta67-common-tests-final.log`).
- Fabric full build: passed, 6m38s (`build/beta67-fabric-build-final.log`). Includes native current/archive admission, review/catalog equality, headroom/night-lighting checks, frozen legacy structures, vanilla structures, handbook/settings and fence models.
- NeoForge full build: passed, 5m10s (`build/beta67-neoforge-build-final.log`).
- Targeted dedicated-server tests: passed, 48 fixtures per loader (`build/server-smoke/beta67-fabric-roofs-harness.log` and `beta67-neoforge-roofs-harness.log`). Logs retain the final zero-remaining fixture and successful integration marker. The harness terminates only its marked test processes after success; a resulting `runServer FAILED`/daemon-disappeared tail is harness cleanup, not fixture failure.
- Historical compatibility: 1,878 archived catalog/asset files in v12/v13/v13-fit are byte-identical to the beta66 Fabric release.
- Packaged architecture: both loader JARs passed all 3,375 decompressed native SHA-256 identities and current canonical source identity.
- Canonical source SHA-256: `7f9eac799d3385463395eef8d9c2c45b4d52bf5ee0a4d30533efa077baa1634d`.
- Fabric binary SHA-256: `6d40d7f610572a3b3a76a96ef098025ea8bf364b5a675deec3502806edf5150d`.
- NeoForge binary SHA-256: `d6462053793caf4f7da15e05ed6776107f08525c757000563349d8b6be92bd0d`.

Release pairs are staged under `build/release-beta67/{fabric,neoforge}` with source JARs and checksum files; profile installation is left to the user.
