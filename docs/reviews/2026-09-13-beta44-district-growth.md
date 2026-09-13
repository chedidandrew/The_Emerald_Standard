# beta.44 district/building review implementation

Unreleased local candidate, Minecraft 26.2 / Fabric and NeoForge.
Based on the supplied beta.31 review of 6eaf7c3; implemented against the current
worktree, preserving the pending beta.41 terrace, beta.42 walkway and beta.43
newsroom changes. No player world or installed profile was changed.

## Decisions and scope

### Ordinary construction admission

A shared server-thread controller now reserves no more than 64 planned operations
and four batches per construction pulse, with at most 16 operations per batch.
Banks, building projects, terrain preparation and queued entrances/paths use it,
including limited sleep catch-up. The time window is a cooperative four milliseconds.

This is not a hard tick-time guarantee. Minecraft block/neighbor updates, saving,
template construction, already-admitted preflight and existing surveys are not
preemptible. The first admitted batch may attempt one safe operation after a slow
preflight, otherwise cold templates could repeatedly lose their entire opportunity.
No async world mutation, force loading, stored idle-work burst or protection bypass
was introduced.

The preferred Bank/village family runs first, alternating when both have work.
Two cheap families can both advance in a pulse. At most two Bank sites and two
villages are selected; the village window advances by one, not its size. Advancing
by two and then selecting only the first under lag would starve half an even pool.
Within each village, unfinished construction and queued finishing work rotate.
The speed setting is now explicitly described as a per-site target.

### Completed history

Normal processing resolves spatial IDs first, then copies only its bounded village
slice rather than every nearby village. The per-completed-building full-village
copy is removed. Finishing uses a compact context containing the chosen project,
identity, origin and architectural palette, excluding residents, funds and other
projects. A disposable pending queue uses persisted cursors and flags. Older
finished trails receive one compatibility check per session. Completed work does
not continually regenerate templates through the former every-building loop.

Remaining limitations: selected village snapshots and transactional rollback still
copy village state; global lot exclusion collection and low-frequency integrity
audits remain. This removes the identified quadratic finishing behavior, not all
history-dependent costs.

### Placement and housing

Unreserved organic sites try five deterministic micro-sites per selected parcel,
including four four-block offsets. At most 24 interior and 24 frontier parcels
produce 240 candidates per sweep. One center is checked per work pulse, with up
to four orientations. Among safe orientations the preference considers entrance
steps, terrain edits and connection direction. All footprint ownership, protection,
reserved-lot and entrance safety checks remain.

Growing natural districts use a stable residential choice based on the project
serial, so waiting another day cannot reroll the proposal. Ordinary weights are
20 Cottage / 80 House. Mature districts can add 20 Inn weight when trade output
or a market/exchange supplies a demand signal, at least two ordinary residences
exist, and the existing Inn count is below the residential ratio limit.
The inn weight is not a simulated visitor count.

Old fixed-size districts retain their previous residential capacity balance:
changing their six-residence/twelve-project lifetime mix caused the existing
Peaceful progression regression to fail. New natural districts do not have that
small lifetime ceiling.

Deferred intentionally: automatic smaller-blueprint substitution, road/courtyard
joint layout planning, carrying inventories and a labor-agent simulation. No
reserved or half-built design is rerolled, and no worker path can block authoritative
construction.

### Presentation and advice

Actual successful placements provide a bounded cosmetic material/sound cue.
Timber and masonry use their native hit sounds. In the last five percent of
observed placement, workers occasionally lower their hammer and look over the
work. The cue expires and resets between world sessions; it does not grant building
permissions, spawn items or move stations every block.

Town advice presents a dominant recent observed reason, distinguishes automatic
retry from useful player action and labels pending final inspection only when
the recorded placement pass is complete. An unchanged 99 percent by itself is
not evidence of inspection, missing pillars or an occupied cell.
Player-edit and container protections remain intact. The guided and compact
handbooks and Settings help document the changed behavior.

## Validation

All checks below were run locally on 2026-09-13 with JDK 25.0.3.

- Complete `scripts/run-common-tests.sh`: PASS after the final handbook correction.
  The new regression exercises low-rate and overloaded family fairness, a cold
  preflight, hard reservation limits, 1,000 completed projects, deterministic
  off-center candidates, neighboring territory, housing proportions and truthful
  inspection/occupancy advice. Existing progression, persistence, news, bridge,
  architecture and handbook checks also pass.
- Fabric and NeoForge complete Gradle `build` tasks: PASS. Final resource and client
  adjustments were repackaged on both loaders with `assemble verifyReaderSettings`:
  PASS. The artifact verifier confirms both JARs match the current build inputs.
- Native dedicated servers, both loaders, with
  `-Dthe_emerald_standard.districtGrowthSmokeOnly=true`: PASS.
  Tests cover real Bank queue mode switching/fairness; material cue state;
  the exact historical 944/948 Cottage and 3603/4873 Inn completions;
  occupancy, support, storage/ownership safeguards; and reported mine/market
  walkway entrances. Logs: `build/server-smoke/fabric.log` and
  `build/server-smoke/neoforge.log`.
- Isolated native Fabric client with `clientSmoke=true` and `clientCrewsOnly=true`:
  PASS. All 63 compact handbook pages fit the native written-book limits;
  long-form wrapping passes at 80/120 text settings; crew/tool/clothing animation
  checks include the new inspection state. Inspected the actual frame-15 capture
  across biome workwear. Evidence:
  `build/client-smoke/beta44-final/logs/latest.log` and
  `build/client-smoke/beta44-final/screenshots/tes-reader-ci/crew-scale-2-frame-15.png`.
- `git diff --check`: PASS.

### Validation limits

The dedicated-server run is the scoped construction suite, not the full unscoped
opt-in integration workflow or a sustained multi-village gameplay stress test.
The older 180-second watchdog incident is not being declared resolved by these
results. These native fixtures themselves perform synchronous setup/testing and
can report a long startup tick; that is not a gameplay performance measurement.
The smoke harness intentionally terminates the server after its success marker,
which can leave a post-success Gradle daemon-disappearance message. Both harnesses
exited successfully. Known Windows Perflib/OSHI and offline development-client
authentication warnings were also present.

The full common suite's scale guards are isolated simulation/index/persistence
checks, not evidence of a particular in-game MSPT or hardware capacity. Longer
in-game testing remains appropriate before calling the mod large-world ready.

### Packaged candidate

Version: `0.4.0-beta.44`. No installation, commit or push was performed.

Shared source SHA-256:
`f764ed3faae023977aca67bcbdcf389a4f33b31be5c8e4c1c0f2c7fd34a62c27`

- Fabric: `fabric/build/libs/the-emerald-standard-fabric-0.4.0-beta.44.jar`
  SHA-256: `F4DC76E8C779F3BCA3B3C4734B4322394FAB758209F4AFEE7B50E461C4B1516F`
- NeoForge: `neoforge/build/libs/the-emerald-standard-neoforge-0.4.0-beta.44.jar`
  SHA-256: `8F4687B7984075D676C50A05AD230844B9458A3D3507C6699B63C0E78AF5D5B7`

Both candidate packages include the existing pending beta.41–43 work, preserved
through this change. Use a backup when testing an unreleased candidate.
