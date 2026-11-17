# Beta73: slim Bank smoke chimneys

## Scope and design

Follow the user's thin masonry stem and upright spruce-trapdoor reference. New Bank plans use frozen architecture revision 18. The three-block-square masonry tower is replaced by a single-block regional masonry shaft and a snug solid spruce-trapdoor collar. Two upright courses conceal both the signal hay and the native campfire; the top remains open. The panels' native shapes extend only three sixteenths of a block beyond each side of the shaft, yielding a 1.375-block collar. Perforated regional trapdoors are deliberately not used: they would reveal the emitter. Looking down into the open flue may reveal the fire, as in the supplied reference.

The roof location retains conservative release-time bearing checks. Only the single shaft's bottom slab is doubled or snow replaced. This is export-time geometry, with no new runtime placement/search algorithm. Every Bank style, kiosk/branch/civic variant and all four outdoor-fit forms retain the chimney in core construction rather than optional decor. Existing plans and completed structures retain their saved geometry; no live world, modpack, installed JAR, or earlier approved assets were edited. The exact-vanilla plains_temple_4 and plains_weaponsmith_1 exceptions stay unchanged.

## Coverage

Keep the historical v14-to-v15 full-sided chimney audit intact. Add an independent v17-to-v18 audit covering 60 Bank forms and 1,440 byte-identical non-Bank forms. Check native panel orientation/envelope, empty shaft neighbors and shroud corners, single full-block stem, lit non-waterlogged signal fire, immediate hay bearing, uncapped outlet, core-stage retention, native shape support, access/capacity preservation and chimney-scoped changes. Historical Bank and outdoor-fit routes remain allocated without reinterpreting old versions. Keep the historical v16-to-v17 lamp-only geometry gate independent of current geometry.

Dedicated-server checks additionally verify the native smoke predicate, open air above the fire, real campfire block entity and retained upright trapdoor facing/state after progressive construction and reload. Current Bank fixtures require exactly one signal marker before construction as well. Full/reduced exports must use the current revision consistently; removed the exporter’s hard-coded previous-revision comparison that caught the Bank changes while producing fits. Failed bootstrap/refresh logs are retained, not counted as passing validation.

The historical full-only version sequence would assign revision-18 full forms v31/v32/v33, colliding with archived revision-13 outdoor-fit v32/v33. The release audit caught that aliasing. Full forms from revision 18 onward instead occupy unused gaps following the preceding revision's fit-3 group: revision 18 uses v260/v261/v262. Fits use v272-v274, v288-v290 and v304-v306. Every earlier mapping is unchanged, and a uniqueness gate covers all 75 allocated Bank versions.

## Handbook and recipes

Reviewed HandbookChapters, EmeraldHandbook, HandbookRecipes, HandbookScreen and all four server recipe definitions. No new item, recipe, recipe animation, or creative-tab entry is needed for this geometry change. Guided Bank access now explains the single-block shaft, snug solid panels, open top, overhead visibility, upright-panel guidance, water and historical preservation. The compact/lectern Bank styles page says "Slim, hidden fires." Both descriptions have resource regression coverage; native client layout validation is required.

## Validation

Shared pure-Java regressions pass on the final source. Fabric strict usability passes 408 detected upper rooms and 1,632 frozen full/fit upper rooms with no inaccessible rooms, native support, idempotence and preserved outward structure lamps. Full production architecture checks pass every historical/current catalog, all fits, the 60 slim Banks, 1,440 byte-identical non-Bank forms, unchanged capacity/access, version uniqueness and exact review/release equality across 375 sites. Both loader assemblies pass.

Fabric and NeoForge each pass all 121 final native dedicated-server fixtures, including all 60 current Bank styles/variants/fits, partial restart, durable handover, current smoke emitters/shrouds, historical Bank paths and other approved construction checks. The disposable smoke harness intentionally terminates its server after the positive integration marker; resulting Gradle termination/daemon text is not an assertion failure. Both final harnesses return success. Native client checks pass the changed compact Bank styles page, long-reader real-font wrapping, chapter ends, text settings and topic search. This is not a live-modpack visual/playtest.

Final logs: build/beta73-common-final.log, build/beta73-fabric-verification.log (strict usability passed; initial chimney audit failed), build/beta73-fabric-release.log, build/beta73-neoforge-release.log, build/beta73-{fabric,neoforge}-approved-final.log, build/beta73-client-bankchimney.log, build/beta73-{fabric,neoforge}-assets.log and build/beta73-{fabric,neoforge}-staged.log. The initial strict stem gate was corrected to retain roof-level snow beside the shaft, without permitting extra masonry.

Both binary/source JAR pairs and checksum files are staged in build/release-beta73/{fabric,neoforge}. Each binary contains 9,375 exact SHA-256-verified native architecture assets: 2,625 full archived/current assets plus 6,750 outdoor-fit assets. Both match canonical source SHA-256 1bdb438cf55bdd01f27ea4ee15aa2bb60b91db21f1acfa17bb2bb6fcbdd71cc8. Fabric binary SHA-256: caeb03427d7477f39a498a82fa735455e246bdd95f8c7cc29f342775b467a4c0. NeoForge binary SHA-256: ab7516939209135f912be2d4ff53bd84dfba12a4d59d0139263c304160cee752.
