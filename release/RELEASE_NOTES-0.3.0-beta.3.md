# The Emerald Standard 0.3.0-beta.3

This prerelease closes the remaining high-priority safety, lifecycle, identity, and scaling gaps found in the Village Prosperity beta review.

## Highlights

- New Village Banks now require a completely flat, natural, dry, loaded site. Mud and thin snow are not accepted as structural support. Their floor is built above the surface, every planned target must be empty and free of block entities, and generation is recorded only after every resulting block state is verified.
- Banks use stable per-village keys and the settlement's persisted center for new sites while honoring existing beta/alpha region associations. A player's position only triggers discovery; it no longer shifts Bank keying or plot selection. This also prevents two distinct villages in one coarse 256-block region from accidentally sharing one bank identity.
- Village Banks and prosperity projects call the cooperative `VillageDevelopmentProtection.register(PlacementGuard)` extension point before placement. Guards fail closed on exceptions. A claim or protection mod must register an integration for its rules to participate.
- Obstructed prosperity projects use a persistent exponential retry delay. Verified obstruction lets an unstarted project choose another lot, while an unloaded boundary retains its reservation so a possibly written prefix is not orphaned. Partial deterministic template prefixes keep their origin and exact bounds for safe continuation.
- Project bounds, retry deadlines, and materialization failure counts now persist in save format 7. Beta.1/beta.2 format-6 saves upgrade safely, and format-5 bank anchors and accounts have dedicated migration coverage.
- Extinct or newly discovered empty settlements no longer materialize buildings or invent free recovery settlers. Zombie-villager deaths preserve incident attribution without decrementing productive population twice.
- The first player-caused casualty now persists the affected village's exact pre-damage state and market contribution. This is a full counterfactual rather than a static freeze: the no-player-damage branch advances and is re-priced on each enabled simulation day, and genuine non-player casualties are applied to it. Repeated player hits do not recapture it; cooldown and full recovery still gate release.
- Threatened or Devastated villages that still have survivors can enter Recovering after the seven-day stabilization window once safety and prosperity are viable, restoring a bounded route back to growth.
- Settlers require food, bed capacity, a safe collision-free spawn, and census confirmation before consuming their pending queue entry.
- Active resident professions provide small, sector-specific production bonuses capped at 12 percent. Nearby villagers show bounded look, swing, and particle cues while construction advances; these cues are visual theatre, not persistent custom AI.
- Village Prosperity census and materialization are dimension-aware, query only settlements near loaded players, rotate a global construction budget fairly, and scale offline catch-up batches to known account and settlement counts. Village Banks remain intentionally Overworld-only in this beta.
- The Village dashboard now shows the cause and age of the latest local incident when restoration status is not taking that space.
- Fabric and NeoForge now share main-hand interaction behavior, and both loader hooks record tracked zombie-villager deaths.
- A denied or failed bank build provides a safe fallback Banker without marking structure generation, so later scans may retry. A scan made incomplete by unloaded candidate chunks likewise provides temporary fallback access without permanently giving up on the structure. Rollback recognizes authored blocks whose pane or fence state changed through neighbor updates.
- Scoped bank identity now controls Banker replacement, Village dashboard lookup, and support/restoration routing end to end.

## No-debt guarantee

Players still cannot borrow emeralds or hold negative balances. Village support and villager-business lending never create a player debt obligation.

## Compatibility

- Minecraft 26.2
- Fabric Loader 0.19.3+ and Fabric API 0.158.0+26.2+
- NeoForge 26.2.0.72+
- Java 25

Beta.3 advances the persistent save to format 7. Existing beta.1 and beta.2 format-6 worlds upgrade automatically. Once beta.3 saves the world, beta.1 and beta.2 intentionally reject the newer format instead of silently falling back and stripping counterfactual or construction-safety state. Keep a pre-upgrade backup if you may need to downgrade.

## Known beta durability boundaries

- Physical project progress trusts its persisted deterministic prefix and completion flag. Blocks already counted are not scanned or rebuilt after a chunk rollback or later player removal.
- The economy-file bank marker and Minecraft's chunk save are not one atomic write. A crash after the marker persists but before the bank chunk does can leave no bank structure; beta.3 preserves access with an eligible fallback Banker instead of guessing block ownership and rebuilding over the site.

## Verification and beta caveat

Publication is gated on the common regression suites, both loader builds, packaged-JAR inspection, both dedicated-server startup checks, and both client bootstrap checks passing for the exact release commit. Automated checks do not replace hands-on testing of unusual terrain, claim-mod integrations, every GUI scale, long multiplayer construction sessions, or very large persistent worlds, so this release remains a prerelease.
