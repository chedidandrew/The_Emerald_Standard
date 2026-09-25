# Beta.27: native builder workwear and hammer alignment

## Request and implementation

The previous renderer built modern hard hats and reflective vests from block-material textures.
Its hammer head extended along model X, across the builder, although the animated arm swings in
the Y/Z plane. This candidate rotates the head's long axis to Z, keeping the wooden handle seated
in the articulated fist. Walking, working, per-worker swing phases and pause/departure logic are
otherwise unchanged.

Builders now use Minecraft's native villager skin, biome/type clothes and toolsmith profession
apron textures on a villager-shaped model with independently animated arms. Exact native head,
nose, hat, coat, legs and upper-arm UV regions are retained; forearms use a cropped native
folded-arm region. Clothing draws at order 1 and the apron at order 2, matching vanilla's
coplanar overlay rules. Initial native previews caught an apron being hidden on some biomes
when both overlays used order 1; that was fixed before packaging.

The server selects one of the seven vanilla villager types from the loaded worksite biome
using Minecraft's own biome rules. The allowlisted string is synced to clients and saved as
BuilderClothing in entity NBT. Existing workers without that field adopt a loaded local style;
unassigned Creative workers use their spawn position. Valid saved outfits do not change when
walking across biome borders, being assigned again or reloading. Unknown/custom biome mappings
use vanilla's fallback; this is not an override of a modded biome's village architecture palette.
Invalid saved style strings are never interpolated into arbitrary texture paths.

No new raster skin atlas, item, recipe or profession is introduced. The existing villager and
block-texture resource paths remain resource-pack-aware. The apron is cosmetic, with no trading,
population, construction-speed or economic effect. Existing worker entities need not be replaced.
No live profile/world was modified or jar installed.

## Handbook review

The guided construction-crews and Creative-content chapters now describe native workwear,
saved biome selection, old-worker adoption and aligned hammering. The compact construction-crews
page now says "Biome workwear." Existing chapter routing, Creative-only egg restrictions and
recipes remain correct. Handbook resource regression coverage was extended.

## Verification scope

Native client previews use the real registered entity renderer, not concept art: all seven
outfits, a hammer side profile, idle pose, two swing phases and walking, at GUI scales 2 and 4.
Client assertions check resource availability, hammer long-axis geometry, tool attachment,
synchronized outfit/arm transforms, independent phases and paused-pose reset.
Server tests exercise local selection, all seven outfit NBT roundtrips, stable reassignment,
legacy/invalid migration and safe resource-path fallback alongside the existing crew tests.

## Final results

- All 90 common regression entrypoints passed: build/beta27-common.log.
- Both full packaged builds passed: build/beta27-fabric-build.log and
  build/beta27-neoforge-build.log.
- Both native dedicated-server suites passed, including the extended
  VillageConstructionActivitySelfTest outfit checks: build/beta27-fabric-server.log and
  build/beta27-neoforge-server.log. The disposable smoke harness stops only its own server
  after the integration-success marker; the subsequent Gradle task termination is expected.
- Final native Fabric client smoke passed: build/beta27-fabric-client-verified.log.
  All 61 compact/lectern pages fit native font bounds; all guided chapters passed real-font
  end-of-content/wrapping checks at 80% and 120% text. Crew assertions passed.
- Inspected actual idle, lowered/raised hammer and walking captures under
  build/beta27-fabric-crews-verified/screenshots/tes-reader-ci (GUI scales 2 and 4).
  All seven outfits retain visible sleeves/legs/aprons; the tool stays in the fist with
  its head in the strike plane. Preview only: the user's exact shader/resource-pack
  combination and a live multiplayer playthrough were not exercised.
- Candidate verifier passed current-source identity and cross-loader parity.
  No candidate was installed into a live profile.

Shared source SHA-256:
`f97f8ff2c2c8904a127f17d795af0c3fce868ccb138187fc51d30d067c9e21de`

Fabric jar SHA-256:
`d918d036608c322acdd714dc59960991cf14d49a5ccf3000a041134ee7c974bb`

NeoForge jar SHA-256:
`ace40ff24dcb73dd0d090b74ff50695c0b622ed152e885e5815e8221b9c7d1a9`
