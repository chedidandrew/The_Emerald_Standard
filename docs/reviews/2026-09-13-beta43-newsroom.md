# Beta.43 newsroom implementation and validation

Implements the priorities in the supplied review of 6eaf7c3. This is a local
candidate on top of the pending beta.41 terrace and beta.42 walkway changes.
No player world, installed mod profile, or remote repository was changed.

## Reporting and editorial changes

- Exhausted headline pools rotate by the most recent publication of each
  candidate, including when a daily player bulletin moves to the archive tail.
- A publisher is selected once. Five recurring authors supply distinct financial,
  technology, freight, village-life and tabloid columns. The existing 17 event
  families retain developed narrative scenes; ordinary roundups and market
  continuations now have publisher-specific prose.
- Twenty-four authored letters, classifieds, advice pieces, advertisements and
  regular columns rotate without inventing player actions. A feature is issued
  every other economic day.
- Local observations carry a private crop-position, store-position or managed
  project/site identity. A double chest uses a canonical half position regardless
  of container/chunk iteration order. Replanting/restocking continuations require
  matching evidence. Added food is not declared to be the withdrawn food.
- Managed construction reporting follows actual recorded start, halfway,
  settled labor, completion, repair-needed and restored states. First observation
  seeds a baseline rather than inventing history. Paid labor is not physical
  completion. Restoration can link to the exact damaged project, not a village
  food or prosperity estimate.
- Market sequels retain their original source and publish only after a new
  change of at least three percentage points from their last comparison.
  Eligibility starts at two/seven days; quiet trackers expire after fourteen.
  Neither a new price nor a timer declares cargo delivered or a building repaired.
- The portable front page weighs current district relevance, age, importance,
  topic diversity and outlet diversity. A fresh light feature follows the lead
  and three briefs when enough stories are available. The full archive remains
  searchable and accessible.
- Notebook separates measured figures from the reading prose. Earlier report
  opens a retained, visible source. Links, privacy changes and existing stable
  editions work in both paper and Desk views. Header controls resize without
  overlapping each other or the illustration.
- Existing sepia illustrations remain; impersonal Nether/Observer features use
  matching freight/community artwork. No live AI service or new entity behavior
  is involved.

## Safety, compatibility and limits

The newspaper does not alter prices, balances, entities, inventories or structures.
Existing creative/spectator, property, cancellation, replant-window and net
container-click protections remain. Private subject identities are not sent in
the reader envelope. Hidden source articles do not yield clickable links.

Optional saved fields preserve current archives without requiring a reset.
Pre-stable-ID archives deliberately discard source links when their IDs are
reassigned. Old local trackers lack incident evidence and are retired rather than
producing speculative sequels.

Civic comparison is deliberately bounded: at most 16 villages and their latest
128 projects per economic day, six civic publications, 2,048 comparison entries.
It reads existing state, not chunks. This first civic expansion covers managed
physical project milestones, not invented residents, population reactions,
shortage resolution or bridge-completion events. Capacity limits can omit news.

Prose remains a finite authored catalog; it can repeat in a sufficiently long
world. Substantial stories are generally about 80–180 words, with shorter features,
rather than a minimum-length padding rule.

## Editorial review

The new regression produces `build/reports/newsroom-30-editions.md`: thirty
controlled editions, all 17 market-event families, five bylines, fifteen different
light pieces, actual house milestones and changed-price sequels. It is not a
capture of a player's world. Reading the sample exposed repeated follow-up
framing and duplicated civic milestone sentences; these were replaced with
outlet-specific follow-up framing and phase-specific civic context.

The three-buckets headline receives a literal liquidity/reserve column. The
46-villager automation headline receives its own supervision/workforce column.
Loss coverage stays restrained. Neither prose nor headings call themselves
satire or explain the mod's reporting system; those details stay in the handbook.

## Verification

- PASS complete `scripts/run-common-tests.sh`, including new newsroom coverage,
  persistence/journal tests, old-format ID migration, money non-mutation, privacy,
  headline pools of 1/2/4/12/32 entries, and a 36-roundup exhaustion regression.
- PASS dedicated Fabric and NeoForge news smoke runs with
  `-Dthe_emerald_standard.newsSmokeOnly=true`: native block hooks, canceled breaks,
  crop attribution/identity, real food clicks, double-chest identity, unloaded
  observation fairness, durable evidence, 256-article packet round trips,
  read-only menu slots and the actual newspaper recipe.
- PASS native Fabric news reader checks at GUI scales 2 and 4: forward/reverse
  article traversal, resize, edition/privacy refresh, Notebook toggle,
  source navigation and non-overlapping header controls.
- PASS native handbook rendering: all 63 compact pages fit the actual
  written-book font/line limit; guided chapters wrap and reach their ends at
  80%/120% reader text. Native recipe previews passed.
- PASS full Fabric and NeoForge builds, including native authored structure,
  Bank, packet, fence and settings verification. Both artifacts were reassembled
  after the final headline-specific publisher guard; the complete common suite
  passed again with that guard and its custom-headline regression.
- PASS current-source/package verification and `git diff --check`.

Final candidate fingerprints:

- Source SHA-256: `da8addee410c35de3519b19bf13e217f69c16cc676a406ee3183ac7123552321`
- Fabric JAR SHA-256: `33d56dbfd41d4b6faba7365786551958300c51d7a7df24814eb3d9dd668926e8`
- NeoForge JAR SHA-256: `dedb719de4999a88dec9234b2c601dc38f24ba0289a77534d8d1171d196ddc63`

Artifacts: `fabric/build/libs/the-emerald-standard-fabric-0.4.0-beta.43.jar`
and `neoforge/build/libs/the-emerald-standard-neoforge-0.4.0-beta.43.jar`.
Final native reader captures:
`build/client-smoke/beta43-c7c5a3ed26fa4df08637f1f9cf88795c/screenshots/tes-reader-ci/`.

The native runs are scoped news tests, not a claim that every unrelated gameplay
scenario was exercised. Disposable test directories are under `build/`.
The host emitted existing Windows performance-counter warnings; the successful
runs did not have fatal startup errors.

## Handbook accuracy review

Reviewed HandbookChapters, EmeraldHandbook, HandbookRecipes, the newspaper recipe,
and both localization formats. Updated the guided Newspaper and Player News
chapters for authors, factual notes, continuations, ranking, privacy, finite
content, compatibility and bounded civic coverage. Compact pages expose Notebook,
Earlier report and continued reading without overflowing. The adjacent pending
Bank-access compact page was shortened after the native font check caught an
overflow; its guided explanation and terrace/roadside distinctions remain.
No recipe, ingredient, Survival availability or spawn-egg behavior changed.
