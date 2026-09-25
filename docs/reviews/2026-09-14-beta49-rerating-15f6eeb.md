# Beta.49 reassessment: 2026-09-14

## Scope and identity

Andrew requested another overall rating after updating the mod. This review compares the current stabilization candidate with the previously reviewed beta.48, without changing gameplay or certifying a stable release.

- Reviewed source: `15f6eeb0c91cda5ab3f99d1ccd907255a0a5dad2`.
- Development branch: `codex/modular-village-architecture`, rechecked immediately before this documentation change.
- Packaged version: `0.4.0-beta.49`.
- Packaged source fingerprint: `de890e4d7cc58373679e807a12bb235952badea6520613d2ff1125ba36a5229a`.
- Economy format remains 40.
- Main remains `1ffc9bc06bef6f7203133f5c7dd0df666d9a57f3`, not the source evaluated here.
- Exact CI: [run #329](https://github.com/chedidandrew/The_Emerald_Standard/actions/runs/34906869489), run ID `34906869489`, attempt 1.

The review used live repository reads, a comparison of the beta.48 and beta.49 production source archives, final CI job results, the complete downloaded Fabric server/client logs, selected actual-client screenshots, and local execution of loader-neutral production code. This is not an every-line audit, a new interactive Minecraft play session, a full local regression run, a terrain-wide visual review, or a multiplayer performance certification.

## Rating

**8.5/10 overall as a development beta**, up from 8/10. The main increase is confidence earned by a complete passing runtime workflow and targeted implementation improvements, not a larger feature count.

| Area | Previous | Current |
| --- | --- | --- |
| Concept and feature cohesion | 9 | 9 |
| Banking/economy implementation | 8.5 | 8.5 |
| Districts and construction | 8.5 | 8.5 |
| Satirical news experience | 8 | 8.5 |
| Interface and onboarding | 8, provisional | 8.5, still subject to human usability review |
| Stable-release readiness | 6 | 7.5 |

These are engineering/editorial judgments, not a mathematical average, performance measurements or proof of economic/save safety. The district feature set was already strong; this pass makes its implementation and validation more convincing rather than adding another half-point merely for existing features.

## Exact CI evidence: all eight jobs passed

| Job | ID | Result |
| --- | --- | --- |
| Common regression tests | 104185474952 | Success |
| NeoForge build and package checks | 104186128433 | Success |
| Fabric build and package checks | 104186128477 | Success |
| Fabric client with Mod Menu | 104187632783 | Success |
| Fabric client without Mod Menu | 104187632791 | Success |
| NeoForge client | 104187632801 | Success |
| NeoForge dedicated server | 104187632815 | Success |
| Fabric dedicated server | 104187632843 | Success |

This replaces the beta.48 finding of three successful jobs and five failed runtime jobs for the new candidate. The old failure results remain valid history, not the current build status.

### Client failures are addressed rather than carried forward

The inspected Fabric client log records successful recipe animation/variant/hover/layout checks, reader navigation and persistence, settings reset/speed editing, dashboard text fit, and worker rendering/animation in both processes. The actual Mod Menu factory markers are required by the strict verifier in the with-Mod-Menu job.

`ClientSmokeSupport` now injects pointer movement through the installed native callback and allows queued input/render processing. `scripts/smoke-client.sh` invokes the strict log verifier after each process, requiring a successful first phase before attempting the restart phase. `verify-client-smoke-log.sh` still rejects unexpected errors and requires the success markers for each expected process. It no longer relies on a fixed obsolete handbook-page count; the actual renderer validates the pages.

Thus the earlier animation assertions and dependent preference-restart failure should not be repeated as current failures. This is not a claim that every possible reader/configuration interaction has been manually tested.

### Native server suite now completes

The Fabric server log reaches `The Emerald Standard Banker integration self-test passed`. It includes successful occupied-terrain/build-cell checks, actual settler arrival/restart, support-recovery fixtures, Bank construction, ownership/one-shot loot, walking, walkway connections, bridges, food/housing census, news, and unified spending.

`BankerIntegrationSelfTest` schedules fixtures over real ticks, permits ordinary catch-up ticks to retire scheduling debt, and uses explicit bounded readiness gates for native entity sections. The test setup's temporary chunk tickets are confined to disposable fixtures and cleaned up. The final catalog checks are divided into steps rather than treating an exhaustive catalog as one startup tick. The watchdog remains enabled.

This is a meaningful harness correction, not proof that ordinary construction runs as slowly as the intentionally exhaustive fixtures. Nor does successful fixture setup mean gameplay now force-loads those chunks.

## Implementation improvements confirmed

### Less unnecessary construction work

`VillageProsperityManager` extends frozen-template reuse to normal construction, with bounded 32-entry caches, and reuses relative geometry during plot search. Geometry keys retain rotation, mirroring, style and design identity. The cached geometry is not cached permission to overwrite terrain or property.

`AuthoredVillageStructures.planMaterials` resolves a road palette without constructing and validating an entire building solely to obtain its materials. The native equivalence fixture covers 15 palette/dialect combinations. The inspected runner reported approximately 322.938 ms for full geometry versus 0.072 ms for direct material lookup in that narrow diagnostic. Those numbers are not a whole-server speedup or ordinary gameplay latency measurement and should not be advertised as one.

Walkway selection now services already admitted searches/plans before admitting further backlog. Relevant nearby searches are retained, while inactive distant work can expire. The existing shared work limits remain in place.

### A concrete path-cache correction

`SurveyChunkRevisionMixin` invalidates the affected path classification when block state changes in a loaded chunk, including loaded chunks outside block-ticking range. Without that correction, a cached wall/open-space classification could outlive construction until another relevant update. The native fixture now verifies air-to-stone-to-air classification changes in a loaded non-ticking chunk.

This is stronger evidence than a generic claim of improved villager AI. It fixes a particular stale-state condition; it does not establish that all modded terrain/navigation scenarios work.

### Better performance evidence, not a blanket performance certification

Capture-local diagnostics distinguish template caches, site searches, walkway admission, housing surveys and actual scheduler progress. Optional JVM profiling is explicitly requested and does not silently change development mode. Inclusive nested timings and gap-adjusted TPS have documented limitations.

These additions make the next real-world comparison more useful. They do not substitute for a controlled post-change session with several mature villages, actual players and the same modpack/settings. Previously supplied beta.48 captures must not be presented as measured beta.49 performance.

### More varied news composition

`NewsNarrative.market` now selects among four complete paragraph arrangements and uses event-specific reactions from `NewsEventAngles`. Some variants open with the reaction or omit the longer outlook. The factual quotation notebook and outlet attribution remain separate inputs. Roundups also have additional opening/layout variants.

This is an improvement over rotating headlines above largely identical stories. Remaining editorial limitation: the system still uses a finite set of repeated paragraph blocks, and even the shorter sampled dispatches are relatively substantial. Truly brief 30-60-word reports between longer features would help casual reading. That proposed range is a design recommendation, not a current implementation claim. Distinct outlet voice and event specificity should take priority over simply multiplying interchangeable phrases.

## Local checks performed

The available runtime was OpenJDK 21.0.11. The loader-neutral core plus MarketDisplay and ConstructionWorkload compiled with `javac --release 21`; the distributed Java 25 Minecraft JAR was not recompiled or launched.

### Previously failing standalone economy helper

The updated, unmodified `EconomySelfTest` completed with exit status zero:

```text
PASS 100-year actual VILX basket smoke test, price CAGR 5.00%
```

It advances the actual economy and verifies that VILX agrees with the company basket instead of compounding the obsolete standalone market-factor proxy. The old expected CAGR band was removed because it did not represent that path. Production economy/index files in the inspected source-archive comparison were unchanged by this stabilization pass. This is a test-model alignment, not evidence that returns were tuned to make the test pass.

The observed 5.00% is one seed's price-only test result, not a promised return or a balance certification across all seeds and player strategies. The common CI job separately passed its full configured suite; that full suite was not rerun locally in this review.

### Controlled news-composition probe

A review-only Java class in the core package used fresh state seed 112. For each of the 17 non-NONE market events and days 1-32, it selected the production publisher and called `NewsNarrative.market` twice with identical inputs and a fixed three-line quotation fixture. It checked determinism and exact quote retention, then recorded first-paragraph variants and whitespace-delimited word counts.

Result:

```text
PASS 544 deterministic market dispatches; exact fixture quotes preserved; word range 130..242
```

Every tested event family produced three distinct opening paragraphs in that controlled sequence. Word counts include the notebook fixture. This does not mean 544 semantically unique stories, represent realistic event frequency, or test player-incident truthfulness. Representative redstone dispatches were read editorially; the entire corpus was not individually scored for humor.

## Visual inspection scope

Selected 1280x800 actual Fabric client fixture screenshots were opened directly: `handbook.png`, `market-vilx-scale-4.png`, `town-expansion-scale-4.png`, and `settings.png`. The inspected panels have readable main controls, a roomier handbook and a compact risk/index summary that fits the narrow market column. This is stronger evidence than source-only layout inspection.

The market fixture shows a server-quote loading placeholder; it is a controlled render fixture, not evidence of a broken live chart or of a completed client/server trading session. No fresh newspaper screenshot or real-world building gallery was inspected. A redundant hovered chapter tooltip can cover handbook body text in the selected capture, and the finance UI still contains dense accounting terminology. These are polish/usability opportunities, not newly reproduced gameplay failures.

## Remaining priorities before a stable release

1. Record a scoped multi-session human test on the exact candidate: discovery without commands, handbook/settings, modest banking/investment transactions, village work, saving and rejoining, plus a real multiplayer session. The manual matrix now names beta.49 but keeps unperformed evidence as Not run/Unverified.
2. Use the new diagnostics for comparable normal-mode measurements at increasing account and district sizes. Preserve strict ownership/occupancy/save barriers; do not trade away safety merely to raise throughput.
3. Retest adverse save/recovery and upgrade boundaries on backed-up fixtures. A green runtime workflow does not certify every interruption point between Minecraft and economy files. The README still documents a conservative canonical-Banker lifecycle lock scenario.
4. Improve casual-player presentation through progressive disclosure and shorter optional news briefs, rather than adding a large management interface or shrinking all text.

No new gameplay defect was reproduced by the limited local probes. This does not close every issue or imply the absence of undiscovered faults.

## Documentation and artifact verification

The README now correctly identifies beta.49 and economy format 40. The manual matrix identifies beta.49 without inventing human test passes. The previous version/save-format mismatch should not be repeated as unresolved.

Downloaded artifacts and SHA-256 checks:

- Fabric build artifact `10372873251`: `31c91f855648a927024f67d51057595e2e20728d717cf53292a039f4577514d0`.
- Fabric client/Mod Menu evidence `10373202181`: `db5b2900ef16fff5dd350c27de0436a713d79b873d31a64c709b0e2b7bb4d78a`.
- Fabric server evidence `10373212662`: `2bd05c4c273b28d3a43efb055ab9d20ee35d71f887088a63881ad0171be11343`.

All three archives matched GitHub's reported digests and passed archive integrity checks. The Fabric playable/source JARs matched their enclosed SHA256SUMS. Extracted EconomySelfTest, NewsNarrative and SurveyChunkRevisionMixin matched independent Git blob hashes fetched at the exact reviewed commit. NeoForge's success is based on the exact workflow job result, not a separately downloaded NeoForge binary or local launch.

## Handbook and change record

AGENTS.md, current candidate documentation, related source behavior and selected rendered handbook/settings screens were reviewed. This assessment changes no player-facing behavior, recipe, configuration, template, save or handbook text. No artificial handbook edits were made solely to accompany a documentation review.

This commit adds only this reassessment. It does not modify gameplay/tests, rerun CI, distribute a new JAR, merge main, publish a release or mark manual evidence passed. Historical review documents remain intact. The source commit and CI run above remain the authority for the evaluated candidate, independently of this documentation commit.
