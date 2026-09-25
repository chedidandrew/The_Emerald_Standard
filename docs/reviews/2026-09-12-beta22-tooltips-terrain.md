# Beta.22: concise tooltips and explored terrain

This candidate updates custom tooltip wrapping and map memory. Economy format is
unchanged at 37; explored terrain uses a separate, disposable client cache format v1.

## Behavior and handbook review

- All custom text hovers use adaptive wrapping; native item tooltips remain native.
  Live points show timestamp and price. Index definitions and repeated explanations
  move out of hovers; exact financial previews, penalties and useful status remain.
- Surface snapshots are captured while exploring, not just while the map is open.
  Previously sampled chunks remain visible after unloading, zooming and restarting.
  Cache RAM and worker queue are bounded; disk I/O never runs in the render thread.
- Guided map/history chapters and the compact terrain page explain last-seen data,
  pre-upgrade revisits, local storage, scope/isolation and disk-failure limitations.
  No recipes or handbook sections change.
- No installed Modrinth profile or player world is modified by this work.

## Validation

- Common suite: all 90 test entrypoints passed; `build/beta22-common-final.log`.
  Cache coverage includes persistence, negative coordinates, world/dimension isolation,
  read/write races, coalesced updates at disconnect, corrupt records, RAM eviction
  and capacity for the widest asynchronous map view.
- Fabric and NeoForge dedicated-server integration checks passed in disposable worlds.
  Client-only terrain mixins did not leak into dedicated-server initialization.
- Fabric and NeoForge native priority clients passed at GUI scales 2 and 4. Explicit
  newline and adaptive-width assertions cover chart points and financial previews;
  client chunk/seed mixin transformation is checked. Both long-form handbook wrapping
  and all 61 compact pages pass the real Minecraft font checks.
- Fabric district-map rendering/navigation checks passed at scales 2 and 4. The
  terrain/marker/legend fixture and live-chart hover screenshots were inspected.
  Chart hover is two lines; site hovers wrap without covering the whole screen.
- Intermediate handbook-overflow failures were corrected and rerun successfully;
  do not infer client success from Gradle exit status alone.

The cache is last-observed surface data, not a zero-overhead or unlimited-storage
promise. Its normal tick sampler is bounded; a fresh unsampled departing chunk needs
one final bounded tile read. No old-save chunk backfill or forced chunk loads occur.
Broader hands-on multiplayer gameplay, rapid dimension travel under disk failure,
and the user\'s exact modpack are not certified by these focused checks.

## Final artifacts

Both full loader builds passed after the final changes. The candidate verifier
confirmed matching versions and source fingerprints, and both JARs contain the
tooltip helper, terrain store/runtime and all three client hooks.

Source SHA-256: `1524abe8bea3effaa0b1d96df099110c623adcee7dd6524b61cbdfcaedbc47c3`

- Fabric: `5f25adcf3726e961c09a01ebc194694c491f6f33389e745c7a0f85e6a868265b`
- NeoForge: `f7fe358c6718222dfc91619e6b7cef91222be486eba8f26a8889922fafba7f58`

Logs: `build/beta22-fabric-build-final.log`,
`build/beta22-neoforge-build-final.log`, `build/beta22-common-final.log`,
`build/beta22-client-priorities.log`, `build/beta22-client-map.log`,
`build/beta22-neoforge-client.log`, and both `build/beta22-*-server.log` files.
Native screenshot fixtures are under each isolated client directory's
`screenshots/tes-reader-ci` folder. These are fixture captures, not the user's world.

Candidate built, not installed; no user worlds or installed mods changed.
