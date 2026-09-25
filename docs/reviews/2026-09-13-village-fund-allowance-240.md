# Village Fund allowance default and label

## Scope

Set the public world-settings default to 240 emeralds per 30 economic days
(8 emeralds per economic day per village), and display the exact label
`Village Fund: monthly spending allowance`. The parser and Reset/new-world
defaults share one constant. The persisted key remains
`village_prosperity.max_monthly_treasury_spending`, so existing explicit values,
including 24 and custom allowances, are preserved. Missing keys use 240.

This changes routine Fund pacing only. No population scaling, new income,
construction-speed changes, fast-track changes, or plot-search fixes are included.
No user world or installed game profile was modified.

## Handbook review

Updated the guided `fund_types` explanation and compact/lectern `fund` page,
including the daily conversion and preservation of existing settings. Existing
`HandbookChapters` and `EmeraldHandbook` routes already reference these keys.
Recipe animations, recipes, and creative discovery are unaffected and unchanged.
Configuration documentation/examples and the changelog use the new default.

## Validation

- Full `scripts/run-common-tests.sh`: PASS, including new-world/default/Reset,
  missing-key, saved 24-E, custom 2400-E and exact daily-conversion checks.
- Fabric native client tooltip-only smoke: PASS. Checks the exact label and
  default field, label/field/keyboard tooltip wrapping at GUI scales 2 and 4,
  all 64 written-book pages and long-form handbook wrapping/search at 80/120 text.
  Visually inspected `tooltip-field-scale-2.png`: label, 240 field and wrapped
  help are readable and on-screen.
- NeoForge `compileJava`: PASS (existing deprecation warnings only).
- `git diff --check`: PASS.

The initial checks caught the separate Reset default and a compact handbook
page overflowing 14 lines; both were corrected before the successful reruns.
This was not a packaged release or a dedicated-server construction test.
