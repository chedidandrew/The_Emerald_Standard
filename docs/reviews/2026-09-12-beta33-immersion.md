# beta.33 — immersive village recovery and reports

Status: local development candidate. Full builds, common regressions, native client/server and packaging checks passed. Not installed into a live profile or pushed.

## User-facing correction

An Extinct village does not require a 25 E donation. Its first collapse normally waits seven economic
days, its second recent collapse twenty. Applying 25 E can shorten the existing recovery date to three
days after application, never delay an earlier date. Abandoned villages require the funding as well
as time. Disabling automatic recovery pauses both. Returning settlers still need safe housing.

The local bulletin now distinguishes optional aid, required remaining funds, a funded recovery and
paused resettlement. Empty villages receive advice about preparing homes, not protecting nonexistent
survivors. The Fund preview and Town report use the same recovery wording. Reading the report makes
no payment and changes no village state.

## Scope

- Replace implementation jargon in local bulletins, Town/Fund status and receipt text with useful
  costs, needs, plain site advice and in-world explanations.
- Remove raw work counts, search cursors, tick ages, chunk status, census internals and build hashes
  from ordinary reports. Keep existing detailed construction observations and build identity in
  logs and /emerald debug.
- Add one server-owned menu value for automatic-recovery availability; clients do not assume their
  own local setting. Both loaders must use this candidate together.
- Keep contribution calculations, confirmation, allocation, account balances, recovery dates and
  actual construction rules unchanged.
- Reuse the existing display-only item preview helper for news icons so isolated title-screen
  renderer fixtures can exercise the real News panel without binding global item components.

## Handbook review

Updated guided Collapse and Restoration chapters with optional versus required aid, partial/funded
examples, timing, paused recovery and irreversible-gift cautions. Corrected compact Collapse page,
automatic-recovery setting help, Town report documentation and the debug build-identity location.
Reviewed chapter routing and recipes: no new item, recipe or chapter is needed. Existing recipe
discovery and all 61 compact pages remain part of native checks.

## Verification record

- Final Fabric build: PASS, 4m 4s (build/beta33-fabric-build-final.log).
- Final NeoForge build: PASS, 4m 17s (build/beta33-neoforge-build-final.log).

- Full common regression suite: PASS (build/beta33-common-tests-final.log), including actual
  recovery-engine behavior before/after eligibility, optional/required funding, disabled recovery,
  remaining-aid bounds, target shortening without delaying an earlier return, language and handbook.
- Fabric and NeoForge server smoke: PASS. Native Fund checks retain preview/payment agreement,
  monetary conservation and pre-top-up rejection; Town/Fund use the required/paused translation
  variants and do not expose raw implementation strings.
- Fabric and NeoForge client smoke: PASS (build/beta33-*-client-final.log). All 61 compact pages,
  long-form chapter ends, reader sizing, browser/report controls and 24 native captures per loader:
  six existing dashboard fixtures plus optional/required/funded/paused/no-Fund/awaiting-settlers
  cases, each at GUI scales 2 and 4. Article and advice line counts fit their actual panels.
- Visually inspected required and optional Fabric panels and funded/paused NeoForge panels at
  scale 4. No recovery text is clipped; empty-village guidance never invents survivors.
- Candidate verifier: PASS exact current source, version, required classes and cross-loader parity.
  Packaged JAR verifier: PASS both binaries and sources in an isolated staging directory; historical
  JARs were preserved.
- git diff --check: PASS.

Initial checks caught a missing test-only tab constant, a stale handbook wording assertion and
compact-page overflow, all corrected. Title-screen News rendering needed the existing unbound-item
preview helper; no global item registries are changed. A failed run left a reader-preference marker,
so final client runs used fresh isolated directories. Final logs contain normal host OSHI/performance
counter and development Realms warnings; server fixture work can produce startup overload warnings.
These are not a certification of live gameplay performance. No live save, user inventory or mod
profile was modified.

## Packaged identity

Version: 0.4.0-beta.33. Shared source SHA-256:
760be6e8e613c60cb637a495b5db86f32798883c8222138f4e6259149370731f

Fabric binary SHA-256:
42828394735b9791e6d84b9347739af7671c872f56e9f6c8387e22669ba898c9

NeoForge binary SHA-256:
89572897f3cd57184f219f47f5df04f5145e630416a2c3c54c9b803ef252667f

Binaries are in their loader's build/libs directory. Screenshots are in
build/beta33-fabric-ui-final/screenshots/tes-reader-ci and
build/beta33-neoforge-ui-final/screenshots/tes-reader-ci.
