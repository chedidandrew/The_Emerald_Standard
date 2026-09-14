# beta.47 — Continuous district map

## Report and cause

The older-world report `TES-debug-20260913-232808-C1126829.zip` showed intact
construction records but missing map squares. A read-only replay of the associated
saved economy reproduced 528 drawing elements: page one contained 343 territory
pieces, seven district centers, six Banks and only 28 project markers. Another
144 project markers were on page two. Panning did not change that page.

This was a display-selection problem, not lost construction records. The replay
reads the save in memory; it does not open the world in Minecraft or save it back.
The later on-disk snapshot contains more completed work than the original capture.

## Implementation

- No player-visible marker pages. The map opens focused on its district.
- A geographic camera request selects sites and territory together. Four logical
  pixels of request overscan include point markers touching the visible edge.
- Overview/Home fits the registered natural villages in the current dimension
  (or the selected legacy city's districts). Focus returns to the original district.
  Panning can reach registered natural villages beyond the former 2,048-block window.
- Detailed responses include every intersecting site, with current construction
  status, and the visible territory geometry. Terrain clipping includes a parcel
  halo so the camera edge does not become a fictitious district boundary.
- Wide views (half-width over 1,024 blocks), dense responses over 384 elements, or
  more than 2,048 candidate detail parcels use counted geographic summaries instead
  of truncating the response. Up to 12 by 8 summary groups cover every visible
  district. Counts refer to placed building sites, including unfinished sites;
  unplaced projects are listed separately. Banks remain distinct in detailed views.
- Camera requests use the existing native menu-button transport in an isolated
  numeric namespace. A complete validated X/Z/width sequence is required. The
  authoritative menu checks its player, distance, open state and current menu.
- Clients coalesce camera changes at five-tick intervals. The server queues the
  latest complete request and refreshes at most once per five ticks; a throttled
  final request is delivered instead of being lost. Idle maps refresh every
  100 ticks. Closed maps do no refresh work.
- A lightweight, shared index is refreshed at most every 100 ticks while requested,
  or when the economy state instance changes. It contains site markers, territory
  parcels and identity metadata, not frozen plans, inventories or financial history.
  The source-state identity is weakly referenced. Index size still scales with
  registered saved records; this is not a claim of constant-time work for infinite
  worlds. Outbound snapshots and rendered marker work remain bounded.
- Atomic revision-bracketed responses preserve the camera during refresh. No
  Minecraft chunk access, world migration, district rewrite, account charge,
  building placement or save mutation is introduced.

## Validation

- Full common regression suite: PASS, including version parity at beta.47.
- Final full loader builds plus packet tests: Fabric PASS (3m 53s), NeoForge PASS
  (3m 38s). Final Fabric client rerun also passes after the compact-text correction.
- Geographic regression: 401 sites reachable across successive detailed camera
  views; unknown owners, unrelated legacy villages, other dimensions, empty areas,
  repair states, immutable index refresh and world-border coordinates covered.
- Irregular-border regression: 180 sites along a long stair-step territory.
  Border overflow switches representation without dropping counts; every close
  site view contains its markers and borders. Clipping does not invent edges.
- Continent regression: 500 districts/sites represented in at most 96 groups;
  all district/site counts conserved, current identity retained, distant detail
  reachable without the old proximity window.
- Request regression: signed coordinates, world limits, partial/out-of-order/
  replayed/malformed sequences, camera bounds and financial-button isolation.
- Real Minecraft packet-codec tests pass for both loaders, including viewport
  requests and atomic responses across signed-short container-data transport.
- Map-only dedicated-server smoke passes on Fabric and NeoForge. Native menu
  checks cover focus, 100 rapid camera changes, eventual last-request delivery,
  empty areas, no distant chunk loads, partial/closed/stale/out-of-range requests,
  reopening, retired page controls and byte-identical saved economy data.
- Native terrain colors, transparent surface handling and Exchange Desk placement/
  item-consumption checks pass in both map-only server runs.
- Client fixtures pass on both loaders at GUI scales 2 and 4: detailed map,
  site/boundary hover, pan, scroll, zoom buttons, Focus, Overview and summary hover.
  Rendered images were inspected; summary mode now has its own legend and a
  non-truncated Overview heading.
- Final older-world replay: seven districts, 172 sites, six Banks, seven overview
  groups. All 172 exact saved footprints found in detailed views: zero missing,
  zero unexpectedly replaced by summaries. Save SHA-256 unchanged before/after.

### Validation scope and encountered issues

An initial attempt to run the broader Bank construction fixture stopped at its
existing `sleep grants a bounded extra allowance to the same active Bank`
assertion, before reaching the map checks. Construction behavior and that assertion
were not changed. The map-only entry point now uses a dedicated native menu fixture
without depending on construction timing. These results do not claim a passing
full, unscoped dedicated-server construction suite or a sustained multiplayer
performance soak.

The first client pass caught a 16-line compact book page. Its wording was shortened
to the native 14-line page budget without removing the required Bank-path, detour
and mine-rail guidance. A subsequent common check caught a missing old detour
phrase during that edit; it was restored, and the full suite rerun successfully.
Windows OSHI/Perflib diagnostic warnings are pre-existing platform warnings; the
client success markers and the scoped server harness outcomes were checked
explicitly rather than inferring success from Gradle exit status alone.

## Handbook accuracy review

Updated the guided `district_map` chapter text and the compact legacy/lectern
`terrain` page in the English language resource. The existing
`HandbookChapters` route and `EmeraldHandbook` page already consume these keys,
so no artificial routing changes were necessary. Guidance covers continuous
navigation, Focus/Overview, refresh delay, summary counts, zooming into individual
sites, locally saved explored terrain, unknown areas, no forced loads and unchanged
world/claim/construction safeguards. Regression assertions and native font/page
checks accompany the updates.

## Build identity

Version: `0.4.0-beta.47`.

Source SHA-256:
`c7c6b7061338fdcae4171751b186f6f6c28ec34835bdeefb53af5bf10fd4223c`.

Packaged artifacts match the source fingerprint above:

- Fabric: `the-emerald-standard-fabric-0.4.0-beta.47.jar`, 16,250,352 bytes;
  SHA-256 `83ccedcdc7accce9cf72c42b7b5b8d11bd139738ac8007c73f3793c1dec11f51`.
- NeoForge: `the-emerald-standard-neoforge-0.4.0-beta.47.jar`, 16,238,225 bytes;
  SHA-256 `b508953ba49b666f93d23689c40e707b01e9b183561ea42a5cd2ea2667964297`.

No user save, debug archive, private world snapshot or generated test-world data
is included in the commit. The mod was not installed into the user's game profile.
