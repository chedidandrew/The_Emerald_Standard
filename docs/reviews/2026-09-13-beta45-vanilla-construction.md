# beta.45 vanilla construction integration

Local development candidate for Minecraft 26.2, preserving the existing pending
beta.41–44 work. This change implements vanilla building integration first;
optional modded-village expansion is intentionally not implemented. No user save,
installed Minecraft profile or Git remote was changed by this task.

## Implemented scope

- Runtime, hash-pinned catalog: 166 resources, comprising 151 ordinary buildings,
  agricultural structures and pens, plus 15 limited civic spaces, across all five
  default village families. Geometry is read from Minecraft, not redistributed.
- Natural structure identity and default primary-pool provenance gate eligibility.
  Imported plans remain the original village family beyond biome boundaries.
  Unknown families and replaced template resources are not silently imported.
- Need-based TES purpose selection precedes compatible, low-duplication vanilla
  choice. TES buildings remain in the mix. Actual bed counts replace TES housing
  estimates for imported houses. Imported service benefits require physical
  completion, and civic pieces do not grant market production.
- Format 39 saves immutable compressed block-state plans. Existing TES projects
  retain their schemas and ordered construction. Imported plans contain no
  template entities, container inventories, loot tables or arbitrary block-entity
  payloads. Cost requirements remain purpose-based, not per-block valuations.
- Native protected lot survey, saved preparation, construction ownership, shared
  work allowance, final checks and entrance/walkway pipeline are reused. Shallow
  below-ground air and foundations are included in protected excavation.

The separate underground shaft of plains_meeting_point_1 is not supported; that
one town-center well is deliberately excluded. plains_accessory_1, standalone
road/decor pieces and abandoned variants are not ordinary independent projects.
Optional trees/houses attached to a town-center pool are not recursively spawned.
A public square does not found a district or a second Bank.

## Problems found and corrected during verification

The native fixtures exposed default handover loot leaking into imported chests;
imported ownership claims now explicitly decline that starter-loot entitlement.
Below-grade farm foundations and cellar air now receive the correct survey depth.
Cave air is normalized to ordinary air. Buried dirt paths are frozen as dirt,
matching their stable native result beneath solid foundations. Imported fluids
are sequenced after their enclosing structure, and imported wall banners use
their actual wall support. These ordering changes are scoped to imported cells;
the legacy TES ordered-plan regressions remain intact.

Navigation fixtures use Minecraft's actual sleeping-approach distance rather
than requiring a path into the solid bed head. Reused, synchronous non-ticking
test sites clear their stale navigation cache between fixtures. Representative
villagers physically walk with normal AI/navigation, not teleportation.

## Validation

Run locally with JDK 25.0.3 on 2026-09-13:

- Both loaders' catalog validation: all 166 templates import, preserve their
  serialized cells and hashes, and retain bounds in all four rotations.
- Fabric dedicated-server vanilla smoke: all 166 construct using the production
  placement/preparation pipeline, survive native block checks, keep containers
  empty and persist completion across an economy-service restart. The final
  rerun also passed the interrupted-house normal/accelerated-mode cases.
- NeoForge dedicated-server vanilla smoke: the same 166 structures, plus
  interrupted representative houses restarted mid-construction while switching
  between normal and accelerated placement.
- Every imported bed has a navigable standing approach. One small house from
  each family also passes real villager walking. These tests use controlled,
  flat natural dirt sites with real protected shallow-foundation preparation.
- Both final full loader builds passed after the compact-page append: Fabric
  in 4m26s and NeoForge in 4m06s, including authored TES catalog, Bank, packet,
  fence/model, settings and vanilla import checks.
- The complete common regression script passed, including new plan persistence,
  strict-family selection, actual housing, both handbook forms, existing gameplay,
  construction/protection wiring, loader version parity and wrapper checksums.

The smoke script deliberately terminates its uniquely marked disposable server
after the success marker. Gradle's subsequent exit-143/daemon-disappeared shutdown
tail is not a failed smoke test; the harness exit status and markers are the
authoritative result. No installed game process is targeted.

## Handbook review and limitations

The guided Projects chapter explains the new choices, five-family boundary,
actual beds, purpose costs, no free entities/loot, frozen plans and exclusions.
A new compact page 64 covers vanilla buildings; page 24 retains its original
site-search and debug-mode troubleshooting. Existing lectern links are unchanged.
Regression checks cover both forms. No new player item or recipe was introduced.

Both final playable JARs were independently checked against the canonical source
manifest and contain the new compact page. Source SHA-256:
`aa4ad9310ab49730bb9b28837e7cffc358722d90ece2bb524b67445fb1b02d75`.

Before committing, trailing whitespace/extra EOF blank lines were removed from
four Java files. The hash above identifies the tested build inputs before that
whitespace-only cleanup; no executable logic changed.

Artifacts:

- Fabric: `the-emerald-standard-fabric-0.4.0-beta.45.jar` (16,225,741 bytes),
  SHA-256 `cf9deb35088631b8bf18fd2a9bd1ca8ca08f20f44045567c1b842bddc08c6574`.
- NeoForge: `the-emerald-standard-neoforge-0.4.0-beta.45.jar` (16,213,758 bytes),
  SHA-256 `10e664457a621455cbe4d8272510f2c297bf60596b9ac7bd93356466fcf13c93`.

This is automated functional coverage, not a shader-enabled visual playthrough,
a full terrain/gallery review, universal datapack compatibility or a sustained
multiplayer performance certification. Imported native fixtures isolate the
building body with a short entrance landing; they do not claim a new exhaustive
bridge/walkway terrain matrix. Existing protected finishing code is reused.
