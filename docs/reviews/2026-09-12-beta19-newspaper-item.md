# beta.19 — rolled newspaper and pocket-gazette reading state

Date: 2026-09-12. Unreleased local development candidate, not a public/stable release.

## Artwork and scope

The approved C (Rolled edition) is now the default newspaper item. D (Pocket gazette)
is its reading appearance. The item retains the rolled curl, center paper band, dark
printed marks, folded corner, layered sheets and grayscale village/tower illustration.

Both packaged sprites are 128x128 RGBA with genuine transparent backgrounds. Full
1254px generated masters and the actual generation/correction prompts are preserved
in art/newspaper/README.md and its neighboring PNG files. The initial generated
exports contained opaque checkerboards; explicit image-generation background
extraction corrected this, and alpha-channel regressions reject a recurrence.
The existing preparation script performs nearest-neighbor sizing only.

These remain generated flat Minecraft item models, not new 3D meshes. First-person
transforms reduce obstruction and cover both hands. Fine print necessarily reduces
in a normal 16px GUI icon; preserving a larger source is not a claim that all fine
detail will remain legible at inventory size.

## Reading lifecycle

The server records the exact stack reference and source hand in the current
NewspaperMenu. Its synchronized menu data tells the local reader which hand is
reading; vanilla synchronized item-use state selects the model for held rendering.
A display-only open model icon appears in the portable reader masthead where it fits.

The change is an immediate sprite switch, not an interpolated unrolling animation.
No second registered newspaper item, inventory exchange, money operation, item-count
change or persistent item component is introduced. Display-only GUI copies are never
inserted into inventories. Closing the menu clears use state. Removing or replacing
the source invalidates it; an identical spare copy cannot inherit its server-side
reading authorization. The Desk browser does not unfold inventory newspapers.
Player-use state is transient rather than serialized into a saved newspaper.

Save format remains 37. Use matching beta.19 clients and servers; the newspaper menu
has an additional synchronized source-hand field. Recipes and creative content are
unchanged. No live Modrinth installation or world was modified.

## Validation

- All 89 common regression entrypoints passed, plus wrapper/version checks.
- Texture regressions verify both 128px textures, real alpha exteriors, distinct
  artwork, restrained ink/paper palette, both hand transforms and model branching.
- Long and compact handbook explanations were updated. Real-font checks cover all
  60 compact pages, long-form chapter ends, 80/120% text and topic search.
- Fabric and NeoForge server smoke suites passed. New native item tests cover both
  hands, exact-source selection, a second equal-data copy, close/idempotent cleanup,
  stale menus, removed/replaced source, browser isolation and unchanged item data.
- Native Fabric client checks passed at GUI scales 2 and 4: newspaper navigation,
  reader layout, unchanged recipe, both loaded item models and handbook guidance.
  The item-state scale-2 and front-page scale-4 captures were visually inspected.
- Fabric full build passed in 2m41s; NeoForge full build passed in 2m28s.
- Both candidate JAR source fingerprints match current source and each other.

An initial client fixture exposed a title-screen item-component initialization
assumption. The display-only icon now uses the existing safe preview-holder helper,
without binding or modifying global item registries. Final client checks passed.
Known Windows OSHI/Perflib diagnostics and Gradle/Fabric deprecation warnings remain
environment/upstream warnings, not suppressed production faults.

Manual multiplayer visual/pose testing and an in-world first-person screenshot were
not performed. Native use lifecycle tests and actual GUI model renders passed;
these are not a claim of exhaustive live-modpack compatibility.

## Artifacts

Version: 0.4.0-beta.19

Source SHA-256:
ee2c0fa634e17997b2b2d5dcb26325fc5f34e5094747274e903eb718092d172d

Fabric SHA-256:
3CC44E00515733A69EED40CBCE8CFEF375CA8A25980F5EABD3A5035C398E040A

NeoForge SHA-256:
B639B3771F9154D0042130713ED0F9B16496F26FBAD4F6A11B41A46E9E7641A2

Logs: build/beta19-common.log, beta19-client-final.log, beta19-fabric-build.log,
beta19-neoforge-build.log and beta19-{fabric,neoforge}-server-result.log.
Server detail logs: build/server-smoke/{fabric,neoforge}.log.
Native screenshots: build/beta19-newspaper-client2/screenshots/tes-reader-ci/.
Nearest-neighbor texture previews: build/newspaper-art/.
