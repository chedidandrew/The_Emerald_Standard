# Five-priority upgrade — beta.9

## Town: understand the next step

Town opens the village overview with scores, city controls and the terrain-backed district
map. Choose **What next? Progress report** for the read-only, server-authored report; its Back
and Town controls return to the overview. Selecting Town also resets an open expansion/map subpage.
The report separates resident
growth checks, economic planning inputs, paid labor and physical construction. It includes
up to eight unfinished projects (repairs and active sites first; completed simulated-only plans excluded), the district's pending Bank location, and recent worksite
observations when available. These observations expire after 1,200 game ticks and include
their age; a saved obstruction flag is not presented as an exact live cause.

The report makes no new block/entity scans, loads no chunks and never approves work.
Requests are coalesced with a five-tick refresh floor and periodic five-second updates.
Text wrapping is cached until the report or UI scale changes. Transport is one bounded,
unobtainable read-only menu document, at most 64 paragraphs. Other districts remain on the map.
PASS is one threshold, not a guarantee of a new settler. No exact completion ETA is invented.

## Fund: preview, then verify the accepted transfer

Apply an amount, choose type/purpose, then Preview. The server describes cash/inventory
sources and the expected allocation to purpose capital, reserve, principal or project labor.
The reserve calculation is shared with the actual contribution implementation. Settings,
affordability and confirmation are checked again when paying; the preview is not an order.

A successful gift opens a visit-local receipt with its actual amount, purpose, sources and
credited allocation. Failed gifts do not create a new accepted receipt. Older gifts remain
in the bounded Activity ledger. Village-wide received/spent/remaining totals belong to all
donors and are not falsely attributed to this one gift. Crediting money is not placing blocks.
Inventory top-ups remain durably separate from the subsequent bank mutation.

## Market: find and compare investments

Browse / compare searches the catalog's names, tickers, sectors and types. Type, favorites
and holdings-only filters combine; ownership is not inferred from rounded value. Tiny holdings show <0.01 E. Clear search resets all filters. Five rows per page
keep controls legible at smaller GUI sizes. Choose two Compare buttons for side-by-side
type, risk, price, holdings and behavior tooltips. Selecting a listing returns to the
existing reviewed buy/sell workflow; there are no automatic orders.

Favorites are local preferences in config/the_emerald_standard-watchlist.properties.
Writes use an atomic replacement where supported. Unknown tickers are ignored; malformed
or oversized files show a recoverable notice. Favorites and comparison choices have no
financial authority. A legacy nine-listing server-data gate was corrected for all 22 assets.

## Repeatable measurement, not a lag-free promise

After running scripts/run-common-tests.sh, run:

```text
java -Xmx3G -XX:ActiveProcessorCount=4 -cp build/common-tests com.chedidandrew.emeraldstandard.core.EconomySoakBenchmark 1095 100,500,1000
```

The model-only harness measures economic-day p50/p95/p99/max durations, save/load costs and
file size. It runs abstract-progress and physical-backlog scenarios, checkpoints every
90 economic days, verifies monetary state on reload and compares independently continued
market paths. Archive bounds and finite/nonnegative supplies and quotes are checked.

The 2026-09-11 local run completed all six scenarios (three simulated years each) with
100/500/1,000 districts and 5/25/32 accounts. At 1,000 districts, day-work p95 was about
6.2–6.5 ms, but mature whole-state save p95 reached 2.64 seconds and load p95 3.27 seconds.
This is NOT live-server MSPT: no Minecraft entities, chunks, lighting, clients or rendering
are simulated. Concurrent local builds also affect wall-clock measurements. The model
retained a two-project physical backlog where no actual world could finish construction.

Large-save latency remains a real limitation. Do not weaken synchronous financial commits
to conceal it. A future incremental/sharded persistence design needs its own migration,
crash-recovery and conservation review. The human matrix still needs multi-hour multiplayer
runs with loaded/unloaded districts, autosaves, active entities and repeated travel.

## Documentation and artifact identity

Both loaders are beta.9 (newspaper appearance follow-up); save format stays 32. The long-form guide explains the new workflow,
and the compact/lectern book has 57 pages. Recipe definitions are unchanged and remain tested.
Older ownership, pacing, investment-calibration and receipt-recovery prose was corrected.

Each artifact embeds tes-build.properties with version and a SHA-256 fingerprint of
production source/assets and build definitions. The report footer shows its short form;
debug capture metadata includes the full hash. The shared build-input manifest defines canonical, normalized-path ordering for both loaders.
The verifier recomputes the current source fingerprint, compares both packaged identities against it,
checks loader version parity and rejects source changes during verification. This is source provenance, not a claim that different versions share
a compatible network protocol. Use matching client/server candidates.

Nothing in this upgrade installs a mod, edits a live world or publishes a release.
Keep a consistent whole-world backup and do not downgrade a newer save.


## beta.7 edge-case hardening

Preview, confirmation readiness and payment share donation eligibility. Mandatory Restoration
routing remains allowed with optional targeting off, but global donation/simulation switches
still block gifts. Missing sponsorship projects are rejected before inventory top-ups.
Search and amount editors retain their native selection state through widget refreshes.
New preview requests and accepted receipts clear stale text before replacement. All periodic
and requested report refreshes share the same five-tick minimum interval.
The holdings transport uses 64-bit cents plus a separate ownership flag. beta.8 additionally
uses 64-bit micro-emerald investment quotes. Clients and servers must match. Save format
remains 32, and older candidate JARs are not replaced.

## beta.8 investment and confirmation boundaries

Tiny holdings retain sale controls even below displayed share precision. No-value sales
keep the holding rather than deleting it. Trades with materially unrepresentable quantity
changes are rejected before inventory top-up; accepted sales use the actual shares removed.
Sub-cent and large current investment quotes no longer hit the old cent/int display limits.
CD-close, Sell All and lending confirmations bind the actual server target and terms; changes
require a fresh confirmation rather than silently acting on a replacement position.
