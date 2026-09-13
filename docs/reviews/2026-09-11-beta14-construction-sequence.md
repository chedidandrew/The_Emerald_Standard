# Beta 14: support-first construction sequencing

Date: 2026-09-11

## Cause and behavior

The original authored order grouped cells by phase, then height. Some upper floors were
labeled FOUNDATION, and timber labeled FRAME could precede its masonry bearing. This let
upper pieces appear before the connections beneath them even when the completed design
was supported.

Normal construction now finishes approved terrain preparation and eligible caution fencing,
then prefers ground foundations/floor, frame, walls, roof, openings, furnishings and lighting.
A connection takes precedence over a phase label when necessary. Upper-storey floors are
not treated as ground foundations. Sloping stair/slab roof courses can meet along step edges;
that allowance does not apply to arbitrary diagonally floating beams or floors. Installed
crates, rails and chains can serve their authored support roles. Chains build top down,
and ceiling-mounted bells and hanging lamps use their upper attachments.

This is an execution-order change, not a redesign or a physical engineering simulation.
Canonical blueprint cells, hashes, roles and final layouts remain unchanged. The scheduler
does not introduce new supports, change funding or labor, or increase block throughput.
Creative/administrative instant construction and disabled worksite presentation remain
separate from the normal visible construction workflow. Intentional entrances, protected
positions and unsuitable fence cells are not sealed over.

## Persistence and protection

Economy format 35 stores bounded version-one construction sequence boundaries. Before any
newly ordered village-project block is placed, the sequence boundary is durably saved. A failed
save rolls back that authority. An older unfinished job preserves its entire already-processed
prefix; only its remaining range is reordered. Future appended upgrades get separate ranges,
so earlier cursor meanings do not change on restart or expansion. Completed older jobs are not
replayed. Integrity checks interpret the same saved sequence as construction.

Banks apply the schedule to their existing saved-intent cells. The original before/after
states and container receipts stay intact; a matching container is not a new loot grant.
Live support checks recognize both authored material stages of an append-only replacement,
but reject air or an unrelated replacement. No free repair, inventory refill, player-block
overwrite, forced chunk load or new demolition permission is introduced.

Already-floating blocks in an old processed prefix are not removed. Remaining connections can
finish around them. If a player removed a required support, existing blocked/manual-repair
rules apply. Historical disconnected details are deferred until connected work is exhausted,
rather than silently redesigned or permitted to prevent all completion. This fallback is not
a promise that every player-modified or modded layout will remain visually connected.

## Verification

- All 85 common regression programs passed: `build/beta14-common-final.log`.
- The construction persistence regression was recompiled and rerun after tightening its
  disposable failed-journal fixture cleanup. It covers genuine format-34 migration, preserved
  cursor, copy/journal/checkpoint replay, old full journal records, failed-save rollback,
  malformed/missing current-format metadata, append-only ranges and unchanged labor.
- A disconnected legacy replacement fixture also verifies that fallback scheduling cannot
  reverse two operations at the same position and thereby undo the final authored material.
- The handbook resource regression passed again after shortening the compact page.
- Native catalog sequencing checks cover 52 active masters in five dialects. The support
  model counted 5,180 disconnected structural starts in canonical phase order and zero in
  the new sequence. Exact cell permutations, deterministic replay, every synthetic legacy
  prefix and append-only upgrade boundaries passed. This is a native-data connectivity test,
  not a visual certification of every in-world construction stage.
- Both in-game client handbook checks passed after the first pass caught a one-line compact
  page overflow: `build/beta14-fabric-candidate-client.log` and
  `build/beta14-neoforge-candidate-client.log`. All 57 compact pages and long-form wrapping
  at 80%/120% are checked with Minecraft's real font. Existing crew render/animation checks
  also passed at GUI scales 2 and 4.

- Both full dedicated-server smoke runs passed with explicit integration markers and exit 0:
  `build/beta14-fabric-server-final.log`, `build/beta14-neoforge-server-final.log`. Native checks
  include original/upgraded support materials, missing/unrelated replacements, ceiling bells,
  real starter-home sequence persistence and restart, normal/forced construction, progressive
  Banks, fences, living-entity occupancy, container receipts, inventory conservation and news.
- A later client rerun reused the disposable profile after the long-form test had changed its
  zoom. Its expected-default assertion failed; compact layout still passed. The final packaging
  run uses fresh profiles and requires the explicit client-success marker, not Gradle exit alone.

- Final Fabric and NeoForge builds and fresh-profile native clients passed:
  `build/beta14-fabric-release-client.log`, `build/beta14-neoforge-release-client.log`.
  Both contain explicit crew/handbook success markers and returned exit 0. The final catalog
  gate includes the disconnected-replacement regression above.
- Candidate fingerprint/version parity and `git diff --check` passed.

Host OSHI/Perflib warnings and the disposable Fabric profile's Realms warning were non-fatal.
The dedicated-server harness intentionally terminates its tagged test process after success;
the resulting Gradle daemon-disappearance tail is not a startup test failure. The wrapper's
explicit PASS, native integration marker and exit 0 were checked on both loaders. No broader
human gameplay or shader-pack matrix is certified by these automated checks.

## Handbook accuracy review

Updated both the long-form Planning vs Building section and compact lectern/book page against
the actual ordering, dependency checks, migration, fence exceptions and player-edit protection.
The full guide explains that a paid economic plan is not a physically complete building, that
support dependencies can cross phase order, and that old floating work is not removed or
automatically repaired. Recipes, Creative-only eggs, costs, resident counts and throughput
remain unchanged. The construction guide, README and changelog point to this candidate.

## Upgrade and deployment

Back up the entire world before upgrading to format 35. Use matching client/server versions;
do not open converted saves with older binaries. This is an unreleased development candidate.
No live-world save, installed mod or user configuration was changed. No installation, commit,
push or publication was performed.

## Built artifacts

- Fabric: `fabric/build/libs/the-emerald-standard-fabric-0.4.0-beta.14.jar`
  - SHA-256: `838CEC994F5B6C69492126609682CABF4A70543ACD871A8D9B6E107C480394AF`
- NeoForge: `neoforge/build/libs/the-emerald-standard-neoforge-0.4.0-beta.14.jar`
  - SHA-256: `0945803C03D96C48B00D7F71E1CC839BBB72D59C53D23AE66A2DBF37A951606F`
- Production source SHA-256: `5e21c94f6f2cf7334861e0a2f80c46fd19402521298b36477067e54f3f025b80`

`scripts/verify-candidate.ps1` confirmed matching packaged versions, current-source fingerprints,
the required report class and cross-loader parity. The builds have not been installed.
