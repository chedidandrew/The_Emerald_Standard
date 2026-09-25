# Beta.28: bounded text tooltips throughout mod screens

## Scope and evidence

The user reported a settings description drawn as a single screen-wide line.
Read-only inspection found beta.23 installed in the named Modrinth profile. That artifact
already uses Font.split for custom labels, and vanilla 26.2's widget Tooltip also normally
wraps at 170 GUI pixels. The screenshot does not identify which rendering path or modpack
interaction produced the line, so this change does not claim a proven third-party cause.

The mod now gives every text-bearing button, edit box and control a pre-wrapped component
through GuiTooltips.widget rather than passing an unbounded description to Tooltip.create.
Real line breaks are preserved in the widget message, as are style spans, blank paragraphs,
amounts and words. The original component remains the narrator hint. Vanilla retains mouse
delay, keyboard focus and tooltip positioning. Rebuilt widgets reflow for their GUI width.
All Banker, Settings, Handbook and Newspaper controls use this factory; a common regression
guard rejects direct Tooltip.create calls in any mod screen.

Custom text hovers continue to receive actual formatted lines, now bounded by the drawing
extractor's own GUI viewport instead of querying a separate window width. Their maximum is
240 GUI pixels with a 24-pixel viewport margin; widget messages also respect vanilla's
170-pixel limit. Short names such as Next remain a single short line.

This is scoped to TES text help. It does not patch other mods' tooltips or replace vanilla
item/image tooltip rendering. No values, financial calculations, world state, recipes or
configuration defaults change. No installed jar or live world was changed.

## Handbook review

Guided Help and recovery explains label/control hovers, wrapping and keyboard help while
retaining the warning that unsaved settings do not affect the world. The compact reading
page now mentions wrapped help. Existing chapters and recipes remain accurate.

## Native regression coverage

- All 34 editable setting descriptions at five widths (170 combinations), for both custom
  hover lines and hard-wrapped widget components.
- Paragraph breaks, bold/color spans, long unbroken tokens, intact text/financial values,
  native Tooltip output and short-label behavior.
- The exact monthly Fund spending-cap help, extracted and drawn through the actual
  settings label, input field and keyboard-focused field at requested GUI scales 2 and 4
  in a 1280x1000 window. The probe asserts real extracted lines rather than only testing
  a string formatter; screenshots provide visual review of placement.
- Both handbook forms retain native-font layout checks, including guided 80/120% text.

An initial test-only protected-access compilation issue was corrected. NeoForge also keeps
GuiGraphicsExtractor's render state private while Fabric widens it; the opt-in fixture uses
reflection for that field, without adding a production accessor or mixin.
## Final validation

- All 90 common regression entrypoints passed: build/beta28-common.log.
- Fabric full build passed: build/beta28-fabric-build.log. After the test-only
  cross-loader visibility adjustment, final Fabric source was compiled and native-client
  tested, then repackaged with jar/sourcesJar: build/beta28-fabric-package-final.log.
- NeoForge final full build passed: build/beta28-neoforge-build-final.log.
- Both final native UI runs passed: build/beta28-fabric-client.log and
  build/beta28-neoforge-client.log. All 61 compact pages and guided 80/120% real-font
  wrapping checks passed. Actual label, edit-box and keyboard tooltip extraction passed.
- Inspected native settings captures under build/beta28-fabric-tooltips and
  build/beta28-neoforge-tooltips, screenshots/tes-reader-ci/tooltip-*.png.
- Current-source packaged fingerprints and cross-loader parity passed.
  Dedicated-server gameplay suites were not rerun for this client-only change.
  The exact installed third-party modpack was not launched for this verification.

Shared source SHA-256:
`ecacf901706a08c696c04eab9217215a383eb4667b8f43a7ef7dc33284d32d43`

Fabric jar SHA-256:
`2d5c79f31a5d2c8b300167293b591946b1c29893cdfebb43f9f8b55d34d3f9ed`

NeoForge jar SHA-256:
`7881c3a77e970d69283b4d423abce6eb18421a6742809955965fcf8c7e08ef06`
