# District map review

Town exposes a north-up terrain and saved-site map, independent of construction,
economy and Bank operating status. The selected desk's canonical village
is used for city membership, including the distant/unsafe-Bank fallback.

## Behavior and limits

- District centers and owned Banks are points over shaded district survey rectangles.
  Coverage starts 64 blocks out from the center and expands with developed projects,
  Bank bounds and field margins, using the food survey's shared calculation.
  These areas can overlap and are not claims or the narrower housing census.
  Recorded project bounds are
  footprints colored by completion, construction, planning or blocked/repair state.
- Map hover shows district, coordinates and either residents/housing or construction
  status/progress. This does not re-survey whether a completed building is intact.
- One page carries at most 96 markers. Centers precede Banks and projects. Page
  controls make all recorded sites accessible; markers on other pages are hidden.
- Explored terrain is captured even while the map is closed, at two-block surface
  resolution. Loaded chunks refresh round-robin with at most 128 samples per client
  tick and a soft 1.5 ms budget checked after each 64-sample tile. A fresh, unsampled
  departing chunk gets one final 64-sample capture; already sampled chunks do not
  add unload work. Chunk references are never retained after unload.
- One small dynamic texture and the view raster remain disposable. The shared
  store retains up to 16,384 tiles (4 MiB of raw colors plus bookkeeping) in memory,
  enough for a whole wide-zoom view without read/evict starvation. It uses one I/O worker
  with a 128-task queue, coalesced writes and nonblocking cache reads. Fixed region
  slots have per-tile checksums; a corrupt slot becomes unknown without losing
  neighboring tiles. Disk space grows with exploration (264 bytes per chunk slot,
  up to about 264 KiB per 32x32-chunk region, including unused earlier slots).
- Surface snapshots survive unloaded chunks, zoom changes, map reopening and restart.
  They are private to this installation, scoped by save path/server address,
  dimension and vanilla obfuscated seed. Same-address same-seed server world resets
  require clearing stale client cache data. No chunks are requested or generated;
  pre-feature visits require a revisit. Disk failure leaves bounded session data
  and a map warning, not a game-save mutation. Checkers mean unknown/pending data.
  Unknown/unsited projects have a count, not invented coordinates.
- Server page requests are gated by active-menu ownership and distance, throttled
  to ten ticks; automatic refresh is every 100 ticks only while the map is open.
  Frame revisions retain the last complete page during partial network delivery.
- Page output, rendering and marker allocation are bounded. Membership collection
  still scans saved village metadata; project enumeration visits the selected city's
  projects. This is not an unlimited-scale performance guarantee.

## Original saved-site map verification

- Common regression suite: 77 PASS markers, including separate neighbors, dimension
  filtering, negative coordinates, 404 map markers across pages, stale/oversized
  page numbers, repair status, read-only data, projection, zoom limits and partial
  transport rejection.
- Fabric and NeoForge dedicated-server integration smoke checks pass. Added real
  menu checks for assigned ownership through an unsafe distant Bank, non-operator
  access, repeated request throttling, no hidden refresh, no resource mutation and
  stale-menu rejection.
- Real signed-short Minecraft packet tests preserve full world-border coordinates
  and reject mixed page revisions.
- Focused Minecraft client checks exercise pan/zoom and render normal/hover states
  at GUI scales 2 and 4. Rendered screenshots were inspected; shortened sidebar and
  button labels prevent clipping at scale 4.

The user's live world and installed mod files were not modified.
