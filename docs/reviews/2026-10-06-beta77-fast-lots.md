# Beta77 instant-development lot search

Completed and packaged for Fabric and NeoForge, with matching source identity
`0cf32c7a2687342a32436ae009f6699eb5828598e65aa791467a5684620cb02f`.

Instant development checks up to 32 candidate centers per village visit, uses a two-block
lot buffer, removes the blanket bank setback while retaining the actual bank reservation,
and advances an unsuccessful search out to 32 rings. Unplaced projects retry after
0.1–1 seconds. Half of the village visits prioritize searches; the remaining visits also
serve construction, finishing and walkways. The global cooperative healthy-tick allowance
is eight milliseconds; one indivisible survey can exceed it. Player placements, claims,
native structures, storage, liquids, and actual occupied/reserved lots remain protected.

Validation completed:

- All shared regressions, loader parity and pinned Gradle wrapper checks.
- Fabric and NeoForge assemblies and fast-lot native integration fixtures, including
  actual bank footprint rejection, claims, save/restart, vegetation and bridge-first safety.
- Native fresh/restarted client handbook checks: compact terrain/projects pages, every
  guided chapter and preferences. Relevant handbook prose was updated; four existing
  Survival recipes and creative-only eggs were reviewed and are unchanged.
- Both packaged candidates match current source. Each package contains the binary and
  sources JAR, passes metadata verification and includes SHA256SUMS.
- Both loaders verified all 10,875 current and historical frozen architecture assets.

Evidence is in `build/beta77-common.log`, `build/beta77-client-summary.log`,
`build/beta77-*-assemble.log`, `build/beta77-*-final-fast-lots.log`, and
`build/beta77-neoforge-architecture.log`. Staged release: `build/release-beta77`.

No installed profile or live world was changed. This is not a measurement of the reported
59-second search in the user's world; a new capture is needed to quantify that improvement.
The subsequent architecture and affected-tree requests are not part of beta77.
