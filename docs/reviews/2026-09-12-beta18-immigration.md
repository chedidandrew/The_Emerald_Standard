# beta.18 — immigration, district census and arrival safety

Date: 2026-09-12. Unreleased local development candidate; not a stable/public release.

## Implemented behavior

Healthy districts accumulate persisted immigration fractions each economic day, rather than using a tiny lottery. Perfect-condition examples are 1.18 approvals/day at eight committed residents and 3.18 at 48; Peaceful adds 15%, up to four/day. Recovery and upkeep strain slow progress. Housing, food for the proposed committed population, Safety 45+, lifecycle and pause gates remain. New approvals stop at eight queued settlers; larger legacy queues are preserved and must drain before more approvals. Full/ineligible districts do not accumulate a multi-day burst. Economic output remains capped at 64 residents/district.

Known UUIDs are observed across loaded entity data at every height, including tagged residents outside the developed rectangle. Unseen Active/Away records become Unverified, not emigrated or dead. Unloaded residents are never replaced by a visibility deficit. Confirmed HOME transfers checkpoint both district records together. Player eggs, breeding and curing are census events, not assumed consumption of queued immigrants.

Housing scans cover developed district chunks and registered home neighborhoods, with section skipping and a 2,048-cell round-robin budget reduced under load. Registered homes outside the rectangle are separate chunk neighborhoods, avoiding a huge empty rectangle. Complete loaded chunks replace cached observations; unloaded data remains unknown. Ownership is exclusive across overlaps. Arrivals recheck both bed halves, occupancy, POI claim, local access, safe footing/collision, current ownership and nearby exposed monsters. Bed candidates rotate so unavailable early entries cannot starve later homes.

The actual landing is checked against living monsters within 12 blocks using collision rays. Walls and underground terrain can shelter the landing. First discovery uses the same exposed-threat evaluation around observed residents, preventing isolated cave monsters from falsely marking a sheltered village as threatened. Minecraft 26.2 PathNavigationRegion was inspected locally: it obtains chunks with getChunkNow rather than force-loading them. A newly created villager is marked grounded only after footing is verified, allowing its initial path query before its first physics tick.

Arrival identity/home/queue consumption is journaled before entity insertion. Definite insertion failure cancels that exact claim; ambiguous exceptions/crashes preserve it as Unverified instead of replaying it. Minecraft entity saves and the economy file are separate persistence systems: this is conservative at-most-once recovery, not a claim of cross-file atomicity. A rare pre-insertion crash can leave an unverified reserved arrival that needs investigation rather than automatic replacement.

## Interface and handbook

Town and district-map counts can display observed/known physical residents above the economic cap. The Town progress report distinguishes loaded census, unverified records, queued arrivals, saved immigration fraction, bed survey and latest arrival outcome; it also reports the combined city total. /emerald debug includes corresponding fields.

Long-form growth and village chapters and the compact growth page were revised against the implementation. Recipes and creative-only egg rules did not change. Native real-font checks passed for all 60 compact pages, all long-form chapter ends, 80/120% text and topic search. Priority browser/comparison/report tests passed at GUI scales 2 and 4. The scale-4 report screenshot was visually inspected; it wraps and scrolls within its panel.

## Validation

- 89 common regression entry points passed, plus wrapper/version checks.
- New immigration regressions cover rate examples, group/queue caps, food and pause gates, duplicate/backward days, claim replay, saved fractions/homes, definite-failure rollback, unloaded residents/beds and exclusive transfers.
- Fabric and NeoForge dedicated-server smoke suites passed, including the new actual-world housing/threat checks and the constructed-home -> actual settler -> save/restart test.
- Native housing checks cover distant upper-floor homes, registered resident homes beyond the developed rectangle, overlapping owners, half-beds, reachable landings, and exposed/walled/underground monsters. The far non-ticking fixture supplies its real monster to the production LOS evaluator directly; it is not an end-to-end assertion of chunk entity visibility.
- Fabric and NeoForge full Gradle builds passed on the final server implementation. After a final text-only handbook clarification about preserving legacy queues, the handbook regression, native reader checks and both loader packaging tasks were repeated successfully.
- Both JARs contain the new implementation/native-test classes and updated handbook. Packaged source fingerprints match current source and each other.

An earlier Fabric smoke attempt failed the pre-existing CreativeContentSelfTest dispenser-data assertion. No spawn-egg production code was changed; subsequent and final smoke runs passed. Its intermittent cause remains unproven and should be tracked as test flakiness, not silently counted as a fixed production bug. Known Windows OSHI performance-counter warnings and unauthenticated development-client Realms warnings are unrelated.

## Artifacts

Version: 0.4.0-beta.18. Save format: 37. Matching clients/servers are recommended; back up the complete world before upgrading and do not downgrade a format-37 save.

Source SHA-256:
2e473954e2fec1c5186cbcb79c51b9355b7106f057ec529d7537b59996999d8f

Fabric JAR SHA-256:
D86AB96F3E6A3661C384397EA3AE9825EC3BA9E23E4743CB31745424BACCF4A2

NeoForge JAR SHA-256:
34594A2578C5FFEA304A109360EC156B86B9A57A2F0E895ED961FFA1E1153926

Logs: build/beta18-common.log, beta18-fabric-server.log, beta18-neoforge-server.log, beta18-client-final.log, beta18-fabric-build.log, beta18-neoforge-build.log, beta18-fabric-package.log and beta18-neoforge-package.log.
Final screenshots: build/beta18-fabric-priorities4/screenshots/tes-reader-ci/. The visually inspected scale-4 report is in the equivalent priorities2 capture; subsequent changes did not alter its layout.

No installed Modrinth JAR or live world was changed. Human multiplayer soak testing, a forced OS/process crash during the exact claim/entity-save gap, and testing the user's live modpack remain unperformed.
