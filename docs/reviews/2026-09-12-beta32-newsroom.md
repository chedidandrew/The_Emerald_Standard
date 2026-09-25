# Beta.32: developed, illustrated newspaper

## Scope

Reworked The Emerald Wire into an in-world publication. Seventeen market-event profiles,
directional roundups, seven local-action categories and developing follow-ups now form
multi-paragraph stories, generally 200–350 words. Authored leads, narrative order and outlet
voices vary deterministically. Tragic stories use restrained coverage and memorial art.

The newspaper no longer explains its tone or inserts reporting-system boilerplate.
Legacy display copies lose the old disclaimers and receive expanded prose; stored IDs,
dates, actors, quantities and historical comparisons remain unchanged. Rendering does
not recalculate old prices or invent missing pre-event baselines.

Four bundled editorial woodcuts appear on the cover and in both article readers.
They are illustrative drawings, not photographs of actual village events. Normal paper
views retain two columns; narrow windows switch to one. Pagination reserves space for
art, preserves all text and reverses through the same contents/story route. The browser
uses a compact illustrated heading without duplicating the article title in its body.

## Safety, limits and handbook review

All content is authored and finite; there is no runtime AI request, network image download,
terrain scan or economic RNG draw. Repetition remains possible in a long-running world.
Bodies are capped at 6,000 characters, entry envelopes at 10,000 and the archive at 256
reports. Four 64-report documents remain coherent and inaccessible as inventory items.

Names/coordinates are filtered server-side before transport, including older reports.
Local prose retains the observed action and quantity without guessing motives or declaring
all damage repaired. Follow-ups use saved pre-event prices and the Town food outlook;
the food outlook is not a physical inventory count. Reading changes no financial state.

Reviewed and updated the guided newspaper chapter and compact newspaper page. Reporting
rules, observation thresholds, privacy limits and safeguards remain in the handbook.
Recipe mappings were reviewed: the reusable Paper + Ink Sac recipe, four animated recipes,
item use states and 61-page compact handbook remain unchanged. Native checks cover them.

## Validation — 2026-09-12, JDK 25.0.3+9

- Full common regression suite passed, including the new developed-story/word-count,
  paragraph/tone, variation, legacy privacy and 256-long-entry tests. Existing deterministic
  save/reload, no-money-mutation, quotas and scheduled follow-ups also pass.
- Fabric and NeoForge Gradle builds passed, including native authored-structure regressions.
- Both dedicated-server smoke suites passed. The native newspaper fixture serializes and
  decodes all four full 64-report documents, checks exact long-text/art metadata roundtrips
  and bounds each tested document below 512 KiB. Existing interaction/evidence, read-only
  inventory and real recipe tests pass.
- Both final native clients passed at GUI scales 2/4: continuous long-story navigation,
  reverse/end/empty bounds, resize, edition acceptance and immediate privacy changes.
  All four art resources resolve. Guided handbook wrapping/search at 80%/120%, all 61
  compact pages and four recipes pass.
- Inspected real Minecraft screenshots of the cover, all four illustrated story types,
  searchable reader and narrow single-column layout. Adjusted large-scale columns,
  anonymous attribution grammar and duplicate browser headings during visual review.
- Packaged JAR checks passed for both loaders. Every editorial PNG in each JAR matches
  the source byte-for-byte; new narrative/illustration classes are present. Current-source
  fingerprints and both packaged versions match.
- Git diff whitespace check passed. The source tree remains uncommitted; this turn did not
  publish a release, push to GitHub, install a JAR or modify a live world.

Evidence: build/beta32-common-final.log; build/beta32-{fabric,neoforge}-build.log;
build/beta32-{fabric,neoforge}-server.log; build/beta32-{fabric,neoforge}-client-release.log.
Screenshots: build/beta32-{fabric,neoforge}-news-release/screenshots/tes-reader-ci/.

Windows OSHI performance-counter warnings and development-client Realms authentication
messages are host/upstream noise, not newspaper assertion failures. The opt-in synchronous
server test workload causes startup lag warnings; it is not an ordinary-play performance
measurement. A full human multiplayer/shader/modpack gameplay matrix is not certified.

## Candidate identity

Version: 0.4.0-beta.32 (unreleased).

Shared source SHA-256:
d9fa46e3929c4d8420bc64532344925ea8201d5d04d5c65843485ea0e8cc966f

Fabric playable JAR SHA-256:
907310d378570aa85b680c721374147406d38f37a7237a9fdf6abb29470442ed

NeoForge playable JAR SHA-256:
1d12b7b1fad73163b389027d3f691dab5d0f7d11fdb7f3d59c6c403fefca0287

Use matching client/server jars for the same loader. Native checks used disposable fixtures,
not the user's play world. Economy format remains 38; the article transport adds an
allowlisted illustration field while retaining parsing of earlier entry envelopes.

Artwork prompts and built-in generation provenance: [editorial art notes](../../art/newspaper/editorial/README.md).
