# Beta75: one-block snowy roof eaves

## Scope

New snowy TES building and Bank plans project exposed flush spruce roof courses by one block, carrying existing supported snow layers onto the extensions. Preserve the lower stepped snowy profile, pitch/ridge height, main shell, furniture, beds and circulation. Do not double-grow already-projecting courses or overwrite occupied cells. Keep three clear blocks above exposed balconies. Shaded circulation uses the established supported night-lighting admission. Review envelopes include the new overhang and adapt surrounding yard extents to the larger envelope.

Freeze production geometry in architecture revision 19, including all three outdoor fits. Retain revision 18 as an independent historical descriptor set and leave earlier full/fitted catalogs untouched. Allocate Bank versions using the existing collision-free mapping. Exact vanilla imports, already-approved construction and finished buildings are unchanged; no live profiles or worlds are edited.

## Handbook and recipes

Reviewed HandbookChapters and EmeraldHandbook routing, both architecture descriptions, HandbookRecipes and HandbookScreen animation/cache behavior, and all four server recipes. Both handbook formats explain snowy one-block eaves and historical preservation; the long reader explains supported caps, occupied-edge/clearance exceptions and exact-vanilla preservation. No item, recipe, animation or creative-tab addition is necessary. Native client checks must include the changed compact architecture page, long reader and restart preferences.

## Implementation and edge cases

Compose coordinate-sensitive walls, room lighting and circulation at their original coordinates, then translate the unchanged core into a larger reservation before adding eaves. Preserve pre-eave provenance through standalone Bank aliases. This avoids rerolling beams, furniture, ladders or lamps when the envelope grows. Released low attics retain the established usability lighting policy, not the taller raw-review room admission.

Detect shallow stepped plank eaves using the long perpendicular roof course as well as gable runs. Protect occupied target cells, existing projections, and three blocks of route/balcony clearance. Roof blocks take precedence over newly proposed snow caps at joined pitches; check each added cap against the final native sturdy upper face. Original snow and original furnished cells are never overwritten. The independent regression requires changes in every snowy design, not a percentage threshold, and exercises a freshly imported already-projecting fixture rather than relying on cached idempotence.

## Verification

The final full and fitted exports pass (`build/beta75-export-full-release.log`, `build/beta75-export-fits-release.log`). The independent geometry proof passes for all 75 snowy designs and all four fits. Every entrance-relative furnished core cell, bed count, tier requirement and access point is retained; the small natural front ground apron can adapt to the larger reservation. All 1,200 other-biome assets are byte-identical. Current and historical production support, doors, beds, ladders, stage safety, Bank anchors and approved-review/release equality pass.

The first exports exposed insufficient standalone/catalog envelopes. Subsequent gates exposed coordinate-sensitive composition, low-attic light policy, side-eave detection, route headroom, joined-pitch cap support and Bank alias provenance. These were repaired in authoring and verified again; failed logs remain in `build/` rather than being replaced by successful claims. No production placement safety guard was relaxed.

Native construction fixtures also exposed the existing deliberate refusal to put optional potted plants on block-entity storage. The fixture now waives only a missing unowned COSMETIC flower pot above a barrel when the actual production support predicate refuses it; required blocks and every other cosmetic remain exact. This preserves the storage protection instead of changing it for roof testing.

The native fresh/restarted handbook client passes (`build/beta75-client-false.log`, `build/beta75-client-true.log`). Real book-font wrapping revealed that the old compact architecture page overflowed; the new seven-line summary fits 14 native lines, while the guided reader retains the detailed tier, Bank, yard and preservation explanations. Shared regressions, loader parity and pinned wrapper checksums pass (`build/beta75-common-release.log`).

The final full preview, support/usability census and Fabric assembly pass (`build/beta75-fabric-release.log`). After tightening the all-design coverage assertion, the independent snowy proof and current-source Fabric assembly pass again (`build/beta75-fabric-assemble-final.log`). Shared regressions also pass again against the final sources (`build/beta75-common-final.log`).

Both dedicated servers complete all 132 fixtures and emit the integration success marker (`build/beta75-fabric-approved-final.log`, `build/beta75-neoforge-approved-final.log` and their `-native.log` companions). Coverage includes all current Banks, historical approved plans, eight snowy rotation/mirror combinations, all reduced outdoor fits, interrupted construction/restart, native support and durable handover. These are disposable playerless worlds, not an interactive visual playtest. The harness terminates the Gradle/server process tree after the positive integration marker; the resulting termination footer is not a construction failure.

NeoForge assembly, both staged binary/source metadata checks, and all 10,875 exact packaged native asset hashes pass. `build/beta75-candidate-final.log` confirms both binaries match the same final canonical source fingerprint:

`a132a896d06785ae8230654b39da9d46030a2634648b5919512c960b1299ad84`

Staged files are under `build/release-beta75/`, with per-loader SHA256SUMS. Binary SHA-256:

- Fabric: `88cce6ad236f51a7b6a85a23c6932168f73dbd1fbdb88a77c9e7dbebe2ca075d`
- NeoForge: `36013bf724b1d968e43f33f1d8932f40808358032a5d719cdfb9ae7fa52cdf94`

No installed mod, user world, completed structure or active saved worksite was changed. No commit or push was made; unrelated existing worktree changes remain intact.
