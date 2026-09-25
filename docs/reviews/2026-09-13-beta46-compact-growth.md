# beta.46 compact village growth and optional acceleration

## Scope and cause

The reported superflat chain reproduced with the previous production parcel
ordering: a controlled sequence of 60 clear 16-by-16 lots all expanded westward.
The old frontier used signed coordinate order and only checked 24 owned parcels
before expanding. Native placement then accepted the first safe candidate.

The new search ranks the complete owned territory before adjacent frontier
parcels. Ranking uses distance to the original village, adjacent owned parcels,
nearby completed buildings/known connection anchors, and stable hash tie-breaks.
It preserves five micro-sites per parcel and the existing four-rotation native
safety checks. Ranking is only a preference: it does not certify road clearance.

Existing reserved/completed buildings, palettes, ownership boundaries, protection,
terrain and loaded-chunk requirements remain authoritative. No completed building
is relocated or demolished. Legacy non-organic districts retain their old planner.

## Bounded and saved work

- Candidate coordinates are lazy; at most four metadata orders are cached.
- One candidate center receives native terrain/footprint checks per work pulse.
- Up to 128 candidate centers positively inside saved reserved lots may be skipped
  first without native terrain reads. Missing legacy bounds are not guessed.
- The full cursor covers at most 409,600 micro-sites at the existing 16,384-parcel
  storage ceiling. This is not a per-tick work budget or a performance guarantee
  for a maximum-size town.
- Format 40 persists the layout signature as well as the cursor/unloaded flag.
  Unchanged orders resume. Changed layout/connection metadata or an old unsigned
  search restarts only an unreserved search. Reserved construction plans do not change.
- The cache is cleared at world-session reset. Ordering survives cache eviction.

## Player wording and handbook review

The setting is now **Forced instant development**. Its tooltip and confirmation
describe optional accelerated play rather than DEBUG-only use. Configuration keys,
default-off behavior, permissions, explicit confirmation and irreversible-change
warnings remain unchanged. Acceleration still bypasses the documented economic
gates; disabling it neither restores terrain nor removes completed construction.

Reviewed/updated guided Growth, Districts, Projects, Construction crews, Recovery,
Paused settings and Bridges explanations, plus compact Growth and Projects pages.
Existing page count/navigation and recipes are unchanged; no item or block was
added. Handbook regressions cover both reading forms. Settings client checks cover
the label and confirmation text while retaining Cancel/Escape/Apply safeguards.

## Verification record

The repeated-growth fixture uses the actual production candidate ranking and
territory admission with synthetic flat lot-clearance checks; it is not a shader
playthrough or an end-to-end generated-city test.

Initial and final-source targeted passes:
- Twelve 60-building runs: four seeds at negative, zero and positive coordinates,
  including mixed footprints. Widths/depths ranged from 214 to 266 blocks; no
  one-direction chain. West/north counts ranged from 25 to 34 of 60.
- Full infill ordering, including a suitable late owned gap; neighboring villages
  with 45 additions each never share parcels; one blocked side still permits
  asymmetric growth.
- Maximum-territory lazy order, cache reuse/reset and stable signatures; positive
  reserved-lot skips and unknown/own-lot exclusions.
- Cursor 1,200 round-trip (above the old 256 limit), copy, unchanged/changed layout,
  unloaded-state reset and acceleration toggle.
- First loader builds passed. The first full common run found one stale assertion
  expecting the previous compact “Debug” label; it was updated to “Instant”.

Final verification on 2026-09-13 with JDK 25.0.3:
- Complete common regression suite passed, including handbook, persistence,
  construction/protection wiring, both loader versions and wrapper checksums.
- Final Fabric full build passed in 2m50s; NeoForge in 2m44s. Authored/TES and
  166-template vanilla catalog checks, Bank, packet, fence and reader settings passed.
- Both focused dedicated-server district-growth smoke runs passed: real mode
  dispatcher/partial Banks, fairness, support recovery, Cottage/Inn completion,
  living-entity/footing and inventory protections, and reported walkway entrances.
- These native checks do not claim an end-to-end multi-village visual playthrough.
  The shape fixtures remain the world-free repeated-growth tests described above.
  Settings client interaction assertions were updated and compiled; no new
  interactive client screenshot session was run.
- The smoke harness intentionally terminates its uniquely marked disposable server
  after the integration-success marker. Subsequent exit-143/daemon-disappeared
  tails are cleanup, not a test failure. Both harness commands returned zero.
- Final whitespace checks passed. No user game profile, save or remote was changed.

Both packaged JARs identify source SHA-256:
`f514d1ee9c397868b2ac2539c0658319445d74bfe66e813d828153a7d38b7448`.
The canonical source manifest was independently rehashed and matches both.

Artifacts:
- Fabric `the-emerald-standard-fabric-0.4.0-beta.46.jar`, 16,234,306 bytes;
  SHA-256 `da554304e811d005ebf060f047e347ffcc6839777617ae3ca92577a44e9941a0`.
- NeoForge `the-emerald-standard-neoforge-0.4.0-beta.46.jar`, 16,222,259 bytes;
  SHA-256 `8bb03e22c5d2e25097222acef915fc4d4cc36cfbfdebb8963fbb055875e10a16`.

Back up a whole existing world before updating. Format 40 saves should not be
downgraded to older builds. Already placed chains remain intact; new infill and
nearby compact expansion can improve them gradually where safe sites exist.
