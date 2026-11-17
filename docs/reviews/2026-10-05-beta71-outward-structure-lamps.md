# Beta71: outward structure-decor street lamps

## Scope

New TES approvals use frozen architecture revision 17. Structure-decor street-lamp arms must point away from the nearest building facade, including when the pole has a gap from the wall. Walkway-generated street lamps are excluded. Nondirectional stone-plinth lights and roof/indoor pendants retain their designs. No player world or installed profile was edited. Historical assets, saved hashes, construction receipts and saved plans are preserved; this is not a retroactive cosmetic rebuild.

The explicit exact-vanilla exceptions plains_temple_4 and plains_weaponsmith_1 remain unchanged. Read-only comparison against the beta70 sources JAR confirmed WalkwayLighting.java, WalkwayConnections.java and VanillaVillageBuildings.java are byte-identical to that release.

## Implementation and edge cases

The release-only PreviewOutdoorLampOrientation pass runs after the revision-16 usability pass, before frozen export. It recognizes wood/masonry street posts with supported timber/slab arms, hanging chains and lanterns. Higher fence-column roof pendants without a masonry socket are excluded. An initial warehouse probe incorrectly classified a canopy pendant as a yard post; refinement of the socket check preserves that pendant and its workbench rather than excavating furniture. Failed probe logs remain available under build/beta71-lamp-*.log.

Candidate directions require a positive away projection from every equally nearest two-high facade. Tangential and inward arms are rejected. A blocked outward target can use a supported two- or three-block outward extension without overwriting obstacles or reserved access. Shared roof/eave bearings are retained. Missing facades and impossible safe outward placement fail the release audit explicitly instead of silently exporting a wall-facing fixture. Rotation and mirror transformations retain orientation. These export-time changes do not add runtime search work.

The catalog audit checks 164 recognized structure-decor lamps and corrects 49 designs beyond beta70. Synthetic fixtures cover four facade directions, wood and masonry, low and raised sockets, bottom-slab arms, facade gaps, blocked targets, preserved obstacles, idempotence and all eight rotation/mirror combinations. Frozen revision-16 versus revision-17 geometry comparisons allow changes only within old/new lamp footprints, including all three outdoor-fit forms. Interiors, upper-floor routes and unrelated architecture are not changed by this revision.

## Handbook and recipes

Reviewed HandbookChapters, EmeraldHandbook, HandbookRecipes, HandbookScreen and all four server recipes. No item, recipe or recipe animation changes are required. The guided Building identity section explains outward yard lamps, the walkway exception, supported extensions, historical preservation and vanilla exceptions. The compact/lectern page says "Yard lamps face out." Resource regressions were updated with both forms. Native font checks pass the changed compact page and all long-reader chapters, wrapping and search. Existing unrelated compact pages are not claimed repaired.

## Validation

Shared pure-Java regressions and both loader assemblies pass. Fabric's strict geometry and production-architecture verification pass: 408 usable upper rooms across 375 designs, 1,632 across full and three outdoor-fit forms, zero inaccessible detected rooms, native support, idempotence, exact review/release equality and lamp-footprint-only changes. Exported full and fit assets were generated in separate invocations to refresh the resource classpath.

Fabric and NeoForge each pass 121 native approved-server fixtures, including current and historical Banks, all eight revision-17 Plains cottage transforms, partial restart, durable handover, stale-mine completion, terrain completion and read-only structure identification. The smoke harness intentionally terminates its disposable server after the positive integration success marker, which can produce Gradle daemon-termination text; both harnesses return success. This is not a live-modpack performance or visual playtest.

Logs: build/beta71-common.log, build/beta71-fabric-verification.log, build/beta71-neoforge-assembly.log, build/beta71-{fabric,neoforge}-approved-harness.log, build/beta71-client-identity.log and build/beta71-{fabric,neoforge}-assets.log. The client log contains both "Updated compact building identity page fits native book bounds" and "updated handbook native compact and long reader checks passed."

Both binaries match canonical source SHA-256 38de7b567f3817576b1b6ed7ec96710cc66df4330f32b95055a1ed4436a11cc3 and include 7,875 exact SHA-256-verified architecture assets: 2,250 archived/current full assets and 5,625 outdoor-fit assets. Staged binary/source JAR pairs and checksums are in build/release-beta71/{fabric,neoforge}.
