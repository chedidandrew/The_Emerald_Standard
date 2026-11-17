# Beta76: clear modded vegetation in approved work areas

## Scope and safety

User-authorized plant removal, not a blanket overwrite of all modded content. Extend shared ground-cover classification using verified native VegetationBlock, GrowingPlantBlock, VineBlock, CactusBlock, SugarCaneBlock and BambooStalkBlock inheritance for non-vanilla blocks. Mature collision/render shapes do not turn a recognized plant into a building obstruction. Include optional Wilder Wild creeping moss in the existing plant tag for its non-plant-base implementation. Keep vanilla dense shrub behavior unchanged.

All existing consumers use the shared rule: surface/volume surveys, final preparation freeze, Banks, building materialization, paths, bridges and site presentation. Block replacement clears approved vegetation without item/entity-kill commands or forced chunk loads. Different modded regrowth can satisfy only an earlier approved vegetation-removal cell; changed soil, walls and unknown non-plants do not qualify. Caller protection checks still run immediately before writes. Keep recorded gardens, pending placements, planted trees, claims/no-build zones, active/finished lot reservations, crops, containers, potted flowers, fluids, persistent leaves and bounded trunk-and-canopy safety. No save-schema or frozen architecture change; existing uncompleted work uses the new shared classification on retry, but finished buildings are not regenerated.

## Handbook and recipes

Reviewed both handbook routing implementations, recipe display/cache/animation routing and all four server recipes. No new item, registration, creative-tab entry, recipe or animation is appropriate. Compact terrain help now says Plants clear; guided terrain help explains automatically recognized plant types, colliding Wilder Wild weeds, clear/replace behavior, regrowth and protection boundaries. Unknown custom plants can use the existing plant tags. Handbook mechanics regressions require both explanations; native terrain reader checks cover fresh/restarted preferences and real font bounds.

## Evidence

NeoForge's loaded disposable-world vegetation/protection fixture passes. Fabric with the actual installed Wilder Wild 4.3 and FrozenLib 3.0 binaries passes 1,476 plant states, including 124 collision-bearing states. Every eligible state is checked through shared site/bridge admission, ground height, final frozen removal, materializer replacement, native world replacement, bounded regrowth and claim/player provenance protection. Waterlogged/crop/protected states remain rejected. Logs: `build/beta76-fabric-vegetation{,-native}.log` and `build/beta76-neoforge-vegetation{,-native}.log`.

The first real-mod run failed before TES fixtures because FrozenLib reports missing configuration entries in a fresh profile. Its failed logs remain under `build/beta76-real-mods/`. Rerun copied only the two dependency JARs and their relevant configuration into a disposable fixture directory; did not suppress unexpected startup errors or edit installed configuration. Successful harness runs terminate only after the integration marker, so Gradle termination footers are not fixture failures.

Shared regressions, loader parity and pinned wrapper checksums pass (`build/beta76-common.log`). Fresh/restarted native terrain handbook, all reader chapter ends, real-font bounds and preferences pass (`build/beta76-client-summary.log` and `build/beta76-client-{false,true}.log`). Both loader assemblies pass; staged binary/source JAR metadata checks and 10,875 exact frozen architecture asset hashes pass (`build/beta76-{fabric,neoforge}-package.log`). No frozen geometry was exported or revised in this release.

`build/beta76-candidate.log` confirms both binaries match the final canonical source fingerprint `f221142914618ab6139684ddd75c41f685f6bceab6dd2039ca2833d71a40c152`. Staged binaries and source JARs are under `build/release-beta76/`, with per-loader SHA256SUMS. Binary SHA-256:

- Fabric: `9cbda9e13b70d03ad2250abddd46e25f5b2691e611b8ea9225c652692f78a3be`
- NeoForge: `9891532e8ba955a9492e67e698c10b80b643bc715819a5dd2010abdd45e1d872`

No user world, installed mod, entity or vegetation was edited directly, and no commit or push was made. Only disposable fixtures clear plants during verification; unrelated existing worktree changes remain intact.
