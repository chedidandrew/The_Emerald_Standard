# Bank village ownership repair — 2026-09-11

## Diagnosis and behavior

The generated Bank already retained its correct village owner. A failed strict building comparison made its Exchange Desk open at the clicked position without the Bank key. The dashboard then selected the closest village, including an accidental empty record created at the Bank's own bell. Bank ownership and permission to operate are now separate: restricted personal access retains the Bank key and Town/Fund select its original village.

Both loader interaction handlers forward this context. Existing owned regions cannot be reassigned by observation/marker updates. Ordinary personal desks outside managed counters continue to use nearby villages.

## Discovery and conservative migration

Authored Bank bells, including reserved construction and retired-site bell cells, are excluded from settlement-center discovery. Bankers are excluded from the discovery census; custom builders are not Villagers. Known settlements are counted around their saved center with complete loaded-chunk coverage. Unloaded coverage defers the observation without force-loading terrain or recording zero residents.

Each discovery pass examines at most eight locally loaded bell candidates and performs at most one durable reconciliation. Exact bell-cell evidence is required, not proximity. A candidate with any population/resident history, incidents, development, expanded housing, city links, restoration/spent donations, pending construction or nonstarter resources is left untouched. Ambiguous cases still benefit from the corrected managed-desk routing, but are not auto-merged.

For eligible records, a single full snapshot saves the retained village, Bank associations and a one-hop identity redirect. Unspent purpose-specific gifts, reserve, endowment principal, fast-track provenance, donor totals and contribution receipts move once. The phantom's starter food/materials/treasury/development do not transfer. Player accounts/global donor awards and all generated/retired structures remain unchanged. Checked arithmetic and ledger limits refuse unsafe transfers. Save failure restores both records and every association.

Format 28 rejects dangling/cyclic redirects and prevents old identities being reused. Journal replay is tied to the new checkpoint generation. Back up a world before testing; older format-27 binaries cannot read a format-28 save.

## Operational integrity

Roof stair orientation and other cosmetic block-state changes no longer fail structural comparison. Essential workstations/cabinetry, doors, floor support, approach, lobby clearance and hazards are inspected separately. Thin floor coverings such as carpets remain walkable. Diagnostics include a problem and block position where available. Large missing portions retain demolition classification; cosmetic changes do not relocate a Bank or overwrite any player block.

## Validation

- Loader-neutral regression suite, including the new identity tests: exact matching, distant owner/ghost geometry, preservation of all unspent fund categories, no starter-resource duplication, second Bank reassociation without deletion, idempotence, reload/journal replay, refusal of history-bearing villages and injected save-failure rollback.
- Disposable Fabric and NeoForge dedicated-server integration tests: full authored Bank, whole-roof stair rotation, actual blocked lobby and floor hole, precise diagnostics, restricted desk menu showing 18 original residents, generated-bell exclusion, missing/replaced desk retaining ownership, migration without removing the bell, and unloaded census checks without chunk loads.
- Existing construction, walking, protection, loot receipts, inventory/funding transaction and economy tests remain enabled.

These tests use disposable fixtures, not the user's live world. No live save, installed mod profile or GitHub remote is modified by this change. A prolonged full-modpack gameplay test remains advisable.
