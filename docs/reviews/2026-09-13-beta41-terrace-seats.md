# beta.41 — restore Bank terrace seats

## Scope

Restore only the Bank's six front-terrace stairs to the pre-beta.30 orientation:
the high backs face the front path (-Z) and the low seats open toward the Bank
(+Z). Bank blueprint v12 reuses the complete frozen v10 composition. All other
cells, materials, roof and entrance stairs, footprint and placement order stay
identical. The separate roadside-nook generator is unchanged: its benches still
open toward the paving, with their backs toward the grass (beta.36).

Saved v11 construction retains its serialized states. Completed v11 Banks use
the frozen v11 integrity plan; no automatic furniture edits or world migration.
Newly planned and gallery Banks receive v12. No player world/profile modified.

## Handbook review

Updated guided Bank access text and the compact/lectern Bank access page to
distinguish the terrace and roadside arrangements and explain saved layouts.
Reviewed HandbookChapters and EmeraldHandbook routes: the existing Help and
recovery / bank_access routes remain correct. No recipe or item changed.
Updated handbook and structure-version regression assertions together.

## Verification

PASS full common suite, including handbook, version routing and frozen history.
PASS focused Fabric and NeoForge native suites with
`JAVA_TOOL_OPTIONS=-Dthe_emerald_standard.benchSmokeOnly=true` and
`scripts/smoke-server.sh <loader>`. Both completed progressive Bank construction,
Bank access/navigation, ownership and Bank walkway regressions as well.
PASS Fabric build (4m26s) and NeoForge build (4m09s), including the full authored
catalog, Bank structure, reader, fence and packet checks. PASS candidate verifier:
packaged versions, source fingerprint and cross-loader parity. PASS git diff
whitespace checks; roadside runtime generator is byte-for-byte unchanged from HEAD.
The native terrace fixture compares all cells against v10 and the exact six-seat
delta against frozen v11 across five dialects; 360 rotation/mirror collision
checks and translated origins. The native roadside fixture checks 48 plans,
96 inward-facing seats, both sides/all directions, claims and saved reloads.
The focused suite also runs the progressive Bank-construction fixture.

No in-game screenshot review or full default native suite is claimed. The
previous broad-suite starter-cottage navigation failure is outside this change.
## Local artifacts

Version: `0.4.0-beta.41`; not installed or pushed by this change.

Source SHA-256:
`73f20d065be763d849898ebe6250603552ac05a9b3a493731012934ba457f489`

- Fabric: `fabric/build/libs/the-emerald-standard-fabric-0.4.0-beta.41.jar`
  SHA-256 `160ee63dfa84c7269fa35ab3f9c23e08b0ffd89495dd199f432a159e3fe5e30f`
- NeoForge: `neoforge/build/libs/the-emerald-standard-neoforge-0.4.0-beta.41.jar`
  SHA-256 `cdb448f32192e344e74a57c81a0c7d217c7d383217734e3f75b001f9bd6ae582`
