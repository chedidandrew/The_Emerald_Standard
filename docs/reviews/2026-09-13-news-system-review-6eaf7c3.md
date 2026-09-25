# News-only review: 2026-09-13

## Scope and identity

Andrew requested a rating of only the news system, judged against a relatable, satirical, Minecraft-native news network that is enjoyable to read.

Reviewed gameplay source: `6eaf7c33ca8fb5e1ad09323884b55667564864ad` on `codex/modular-village-architecture`. The downloaded build identifies itself as `0.4.0-beta.31`. At review time, main remained at `1ffc9bc06bef6f7203133f5c7dd0df666d9a57f3`; this review does not merge the branches.

Production sources and bundled templates were inspected from the source JAR in Fabric artifact `10309891092` from workflow `34731884109`, with important files fetched independently at the exact source SHA. The review included NewsWire, NewsEditorial, NewsRuntime, NewsEvidence, NewsTemplates, NewsReader, NewspaperMenu, NewspaperScreen, the existing Banker News tab, and the bundled template catalog.

This is a source/editorial review with controlled executions of the actual loader-neutral news code. It is not a Minecraft playthrough, a completed GUI assessment, a full test-suite certification, or an assessment of the rest of the mod. An inspected Fabric client evidence ZIP did not contain newspaper screenshots, so no visual approval is claimed.

## Subjective rating

**7/10 for the requested news-network experience.** The strongest part is the headline voice and grounding in recorded events. The weaker parts are full-article storytelling, durable outlet personality, meaningful continuing stories, and sustained variety. The score is an editorial judgment, not a measured player-satisfaction or code-coverage score.

The news currently reads more like financial reporting and village incident bulletins with satirical headlines than a fully characterized newsroom. The best next investment is better article bodies and event-specific follow-through, not merely more headlines or more outlets.

## Existing strengths to preserve

- Five existing outlets: The Emerald Ledger, The Redstone Wire, The Nether Post, The Overworld Observer, and The Daily Gravel.
- A bounded 256-article archive, category retention quotas, stable article IDs, searchable/filterable browser, significance/recency ranking, and explicit acceptance of incoming editions.
- Deliberately separate reporting and price formation: articles do not themselves change prices or punish players.
- Recorded player actions are distinct from intent or permission. Runtime ownership checks, crop observation/replanting checks, and privacy filtering should remain intact.
- Existing developing stories and follow-ups are real implementation, not missing features. They need better matching and writing rather than replacement with an unbounded system.
- The bundled JSON has 31 groups and 163 strings: 96 event headlines across 17 event families, 24 roundup headlines, 28 player-action headlines, and 15 outlet voice lines. That is 148 headlines, not 163 complete articles.

The strongest humor combines recognizable institutional behavior with Minecraft-specific consequences. Less effective passages repeatedly explain implementation boundaries or turn an article into player/coordinates/count fields.

## Three issues reproduced using the actual news core

The original source archive's core classes were compiled using local OpenJDK 21.0.11, adding its loader-neutral MarketDisplay and ConstructionWorkload dependencies. A separate review-only Java probe supplied the exact bundled template map and controlled inputs. The distributed Java 25 JAR was not modified or recompiled. The following are reproduced core outputs, not claims about occurrence frequency in ordinary gameplay.

### 1. Exhausted headline pools can get stuck on one headline

Location: `common/src/main/java/com/chedidandrew/emeraldstandard/core/NewsWire.java`, `choose`.

Fixture: `EconomyState.fresh(112, 0, 0)`, bundled templates loaded; 24 successive calls to `NewsWire.day` with `MarketEvent.NONE`; economic days 2, 4, ..., 48; before each call, save the price map and increase every price by 1 percent. This intentionally isolates upward roundup selection and does not simulate an ordinary stochastic playthrough.

Observed: the first 12 roundups used 12 distinct headlines. Roundups 13 through 24 all repeated the first headline. After exhausting the pool, the fallback scans archive entries oldest-first and returns the first matching wording. Republishing that wording does not remove its original archive entry, so this is not least-recently-used selection by last publication.

Recommended fix: track or derive each candidate's latest publication, choose the least recently used candidate by that latest timestamp/sequence, and avoid immediate repeats whenever the pool has an alternative. Track semantic joke/theme reuse separately from exact wording. Increasing the template pool alone delays the defect rather than fixes it.

### 2. A roundup's masthead and editorial voice can disagree

Locations: `NewsWire.day`, private `NewsWire.outlet`, and `NewsEditorial.outlet`.

In the same fixture, day 2 published under The Nether Post but appended the Redstone Wire technology voice. The publisher was selected with the family-specific random key, while the voice used the independent editor key. The fixture produced 23 mismatches among 24 roundups; that ratio is specific to this controlled seed and must not be described as a general live-game failure rate.

Recommended fix: resolve the publisher once and pass that exact outlet to every headline/body/voice/byline step. Add an invariant test covering both roundup directions and every outlet.

### 3. Structural damage follow-up reports food instead of the damaged structure

Locations: `NewsEditorial.localDevelopment` and `NewsEditorial.followups`.

Fixture: create a village with foodSupply 50 on economic day 1; submit a DAMAGE report for four structure blocks; advance only the fixture's economic day to 3 and invoke followups.

Observed headline: `Follow-up: Village follow-up: checking the food outlook`. The body reports unchanged simulated food supply rather than anything about the structure. The tracker records FOOD and a food baseline even when the originating event is DAMAGE; subsequent help also matches by village rather than a specific incident.

Recommended fix: retain the event subject and relevant evidence in the story. Structural incidents need the specific structure/site and an integrity/repair status if available. Crop and pantry incidents need their own appropriate observations. Keep unrelated incidents separate within one village. If no new relevant observation exists, withhold the follow-up or say that repair status has not been verified. Never infer physical repair from an improving economy metric.

## Editorial improvements, in priority order

### A. Write articles, not only headlines

Current market bodies primarily combine an event description, three market statistics, a short outlet voice line, and boundary disclaimers. Player reports mainly show identity, coordinates, count, and permission/punishment disclaimers. This preserves honesty but limits immersion.

Introduce authored story structures: a concise factual opening, a recognizable Minecraft consequence, one character or institutional reaction, and a final comic turn. Target roughly 80-140 words for an optional lead feature and 25-50 words for briefs as initial design goals, not rigid quotas.

Keep factual records authoritative. Put scope, observed counts, market figures, and uncertainty in a compact Facts/Source panel. Clearly mark remote simulated market dispatches separately from verified local events. Mark invented flavor interviews as fictional editorial characterization, not actual quotes from a player or proof of an in-world NPC action. Do not remove safety logic merely to remove repetitive prose.

Prefer satire about hype, fees, bureaucracy, public relations, shortages, and inconvenient logistics over generic puns or dependence on real-world politicians and companies. Use Minecraft institutions and materials to carry the joke.

### B. Differentiate the five existing outlets throughout the article

- Emerald Ledger: respectable finance reporting, defensive analysts, overconfident forecasts, profit-first framing.
- Redstone Wire: technology enthusiasm, prototype hype, automation claims, neglected practical details.
- Nether Post: trade/foreign-correspondent perspective, customs, freight delays, lava and insurance bureaucracy.
- Overworld Observer: local residents, food, housing, community projects, practical consequences, dry civic humor.
- Daily Gravel: clearly marked absurd opinion and tabloid exaggeration, without turning invented allegations into verified facts.

Each outlet should have distinct headline syntax, lead structure, recurring correspondent/editorial cast, vocabulary, story priorities, and closing style. The current three bundled voice lines per outlet should not be the primary personality system. A useful editorial check: hide the masthead and see whether a reader can still identify the outlet.

### C. Cover ordinary life, not mainly markets and recorded player incidents

The core Kind set is dominated by markets, follow-ups, roundups, food movement, crops, structural removal, contributions, and violence. Add meaningful event adapters for other existing village systems only where reliable evidence exists: actual housing/project completion, production changes, migration, recurring shortages, and repair outcomes.

Make the world interesting even when no player is accused of anything and the market is quiet. Include clearly labeled fictional letters, classifieds, advice columns, advertisements, and opinion pieces that cannot be mistaken for observed world events. Favor bureaucracy and powerful institutions as comic targets, not victims of a village loss.

### D. Make continuing stories develop rather than merely recur

Market follow-ups currently measure a tracked price against a baseline at two and seven economic days. Preserve this data, but publish a new installment when there is a meaningful development. Link installments to the original article and show what changed.

Use event-specific states and recurring characters. A promised project, a funding milestone, observed construction, and verified completion can become a coherent civic story. Do not write a promised deadline, permit, repair, or official quote as fact unless the simulation actually records it. Avoid asserting that a price recovery proves a supply crisis ended.

### E. Add relevance and variation to the existing editor and reader

Keep the existing archive, search, category/outlet filtering, recency ranking, and stable reading snapshot. Improve ranking with the reader's relevant village, genuinely changed conditions, and topic diversity. The same market incident should not dominate the front page in multiple slightly different forms.

Consider one lead, a small number of meaningful briefs, and one short light feature per front-page edition. Quiet periods do not need manufactured disasters. Persist read markers/selected story by world or server if reading continuity is a goal; the inspected reader currently holds that state in the screen-owned in-memory model. Keep privacy changes immediate and server-authoritative.

## Suggested acceptance checks

1. More publications than every finite headline pool contains, including a long one-direction market sequence, without immediate repetition when alternatives exist.
2. Consistent masthead, body voice, byline, and theme for every outlet.
3. A damage story never uses a food recovery metric as its repair result; separate incidents in the same village remain separate.
4. No invented local destruction, permission judgment, player quote, fine, or market cause is emitted as verified fact.
5. Follow-ups add relevant information, link to the original, and do not repeat only because a timer elapsed.
6. Read a 30-issue generated corpus editorially: track repeated jokes, body skeletons, outlet recognition, mundane/positive coverage, and continuity. Exact headline uniqueness alone is insufficient.
7. Check newspaper navigation, article overflow, typography and readable hierarchy in actual clients. This review does not claim those visual checks passed.

## Change record

This commit adds only this review and reproduction record. It applies no gameplay, rendering, pricing, persistence, or template fixes; runs no merge; publishes no release; and marks no manual test as passed. Historical reviews remain unchanged. The exact reviewed gameplay SHA above remains the reference for every finding.
