# Beta81 — living decoration recovery and world-close chat safety

Use the binary JAR for your loader. Sources JARs are for development only.
SHA256SUMS accompanies each loader pair. No installed profile or live save was changed.

The captured snowy Tannery was waiting on short grass that cannot survive on its
planned substrate. Nonproductive grass, ferns and flowers now get a safe best-effort
placement; unsupported or later-lost plants cannot freeze or restart construction.
Existing unfinished projects resume without changing their saved blueprint, operation
order, totals, rotation or bounds. Required floors, storage, lights and crops remain
required. Exact vanilla building geometry is not remodeled.

Approved plant-removal cells may also clear naturally regrown soft vegetation or snow
that replaced the original plant. This fixes the captured warehouse's vine-to-snow
terrain stall. Changed soil, walls, containers, fluids, player placements and claims
do not gain replacement permission.

The 26.2 integrated-server world-close path can update chat UI from its server thread.
The client now queues this refresh on the client thread without waiting for it, and
ignores callbacks belonging to a disconnected player. This addresses the reported
chat-list ConcurrentModificationException, not all third-party entity/chunk errors.

Includes beta80's connected three-wide entrance stairs and all earlier improvements.
Architecture remains revision 20; no new items or recipes. Both handbook forms updated.
See docs/reviews/2026-10-07-beta81-flora-and-shutdown.md for evidence and validation.
