# beta.11 — Town overview by default

Date: 2026-09-11. Local development candidate only; not installed, published or committed.

## Change

Selecting Town opens the village prosperity/local resources overview, as requested.
The report remains available through **What next? Progress report**. Its footer Town
shortcut also opens the overview. Top-level Town selection resets an expansion or map
subpage, closes its active map subscription/terrain resources and closes any report.
Report Back still returns to the page beneath it. Missing-village screens do not open a report.
The Exchange Desk itself still opens Home; this request only changes the default Town page.

No changes to prices, market simulation, history ranges, inventory, accounts, villages or
save format (33). The proposed live chart and longer ranges remain unimplemented.

## Validation

- Fabric and NeoForge assemble passed; logs: build/beta11-fabric-build.log and
  build/beta11-neoforge-build.log. Full economy/server suites were not rerun for this
  client navigation-only change.
- Focused HandbookResourceRegressionTest passed: 16 chapters, 64 sections, creative-only
  egg coverage, recipe resources and new long/compact overview-first navigation assertions.
  English localization also parsed successfully.
- Both native clients passed priority checks at GUI scales 2 and 4: Town entry from Home,
  rebuild preservation, explicit report opening, footer Town shortcut, report Back, return
  after expansion, return from map and absent/restored village data. Existing market browser,
  comparison, report scrolling and actual-font handbook checks also passed.
- Logs: build/beta11-fabric-town.log and build/beta11-neoforge-town.log.
- Inspected the actual Town screenshots at both interface scales. Layout remains the existing
  overview; long labels retain existing ellipsis behavior at large text sizes.
- git diff --check passed for changed tracked code/resources. Packaged production-source
  identity and matching cross-loader versions passed scripts/verify-candidate.ps1.
- Existing Fabric renderer/Gradle deprecation warnings, development Realms authentication
  and NeoForge translation-renaming warnings are unrelated to this navigation change.

## Handbook accuracy review

Updated the long Village explanation and compact Town progress page: overview first,
report explicitly selected, and Back/Town navigation. Reviewed City expansion, district map,
report limitations and recipe instructions against unchanged implementations. No recipe
changes were needed. Both native clients verified all 57 compact pages and long-form
wrapping at 80/120 text sizes.

## Artifacts

Version: 0.4.0-beta.11. Production source SHA-256:
55ced5080d9319d992e5ddb61c70219dfff4a074e50a533b81f09327a2fd8055

- Fabric: fabric/build/libs/the-emerald-standard-fabric-0.4.0-beta.11.jar
  SHA-256: 951C618FDBD84617A3C643DAC2AB3B208AA8B0F0C379BBEBFF560DB376510835
- NeoForge: neoforge/build/libs/the-emerald-standard-neoforge-0.4.0-beta.11.jar
  SHA-256: EBAD563F052F128718C1942D364D250E9974D9813C4322EC038E6A8F246C7694

Screenshots under screenshots/tes-reader-ci in these isolated build profiles:

- build/beta11-fabric-town-2dfbe8c0b3d0410782f6b7d921d1f202/
- build/beta11-neoforge-town-bf30abf22e5a4d0e836f0d0a4a423620/

No live profile/world was modified. Back up worlds before trying a development candidate.
