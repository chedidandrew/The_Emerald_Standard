# Vegetation compatibility validation — beta.53

Shared vegetation classification now governs building terrain preparation, Bank/project
replacement checks, paths, bridge clearance and construction presentation. Datapacks can
extend six category tags without granting an exemption from world protections.

Verified the installed Wilder Flowers 1.1.1+26.1.2 Fabric JAR: its wildflowers tag does not
include `wilderflowers:clovers`. TES supplies that optional entry explicitly. The actual JAR
passed the disposable Fabric server fixture, not merely a mocked registry test.

Validation completed:

- Common regression suite, including handbook and source wiring checks.
- Focused Fabric server suite with the actual Wilder Flowers JAR.
- Focused NeoForge server suite with no vegetation mod installed.
- Full Fabric dedicated-server integration suite: all 98 fixtures passed. The harness
  terminates its disposable server after the success marker; the resulting Gradle daemon
  termination is cleanup, not a failed test. Heavy opt-in fixtures emit tick-lag warnings;
  this is not a sustained gameplay performance benchmark.
- Fabric and NeoForge beta.53 builds and their loader checks passed.
- Native tests for grass/flowers, player garden placements, planted-tree provenance,
  persistent leaves, crops, inventories, fluids, claims, isolated logs, oversized trees,
  timber doorway frames, negative-cache invalidation and reload-reset behavior.

The full server run initially revealed an existing fixture setup problem: the Bank
mode-switch test authored a plot on a chunk boundary without loading adjacent columns.
The fixture now loads its local halo explicitly; the separate remote Bank remains unloaded.
No production no-force-loading rule was weakened. The source-wiring regression was updated
to inspect the shared natural-ground predicate instead of expecting its old inline body.

Handbook review: updated the existing guided terrain chapter and compact terrain page;
their existing routes remain correct. Added regression assertions and a datapack author
guide in `docs/vegetation-compatibility.md`. Recipes and creative-only eggs are unchanged.

Limitations: old unrecorded gardens and arbitrary mod growth transformations cannot always
be distinguished from world-generated vegetation. Unknown blocks and ambiguous trees remain
obstacles. No live player save was edited. The reload cache helper was exercised directly;
both loaders booted the reload mixin, but a live datapack replacement was not visually tested.
