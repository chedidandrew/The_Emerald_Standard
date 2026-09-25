# Inventory-aware Exchange Desk payments — validation

Date: 2026-09-11. Minecraft 26.2; Fabric and NeoForge; mod 0.4.0-beta.5.

## Result

Exchange Desk spending now combines Bank Cash and ordinary loose inventory emeralds.
Bank Cash pays first; inventory covers only the shortage. Fractional change stays in
Bank Cash. Deposits remain optional, and Savings is never spent automatically.
Covered routes: investments, Savings deposits, CDs, villager lending, and all Village
Fund contribution types. Withdrawals and investment proceeds remain bank operations.

See [behavior and recovery limits](../UNIFIED_SPENDING.md) for exclusions and upgrade advice.
This build contains the existing working-tree changes as well; this review covers the
inventory-spending change, not a new validation of every prior feature.

## Checks completed

- Full common regression suite passed (`build/unified-funds-common-final.log`). New
  checks cover exact integer conservation, fractional change, invalid/oversized input,
  saturated availability, transaction receipts, restart/replay, and an injected bank
  save failure without losing cash or granting an investment.
- Fabric and NeoForge dedicated-server smoke tests passed, including real Minecraft
  inventories, player NBT checkpoints/reload, and all inventory-funded spending routes
  (`build/unified-funds-server-fabric-final.log` and its NeoForge counterpart).
- The live inventory test covers the requested 320-inventory/zero-cash example,
  offhand emeralds, preserved custom/named items, cooldowns, stale exact amounts,
  closed-menu rejection, Fund confirmation/replay, and a balance changing between
  choosing an amount and confirming it.
- Recovery tests check PREPARED rollback, committed-credit restart without taking
  subsequently acquired items, repeated recovery, contradictory/missing receipts,
  full creative inventories, and partially delivered withdrawals with exact refunds.
- Focused native client checks passed on both loaders at GUI scales 2 and 4 with
  320 inventory emeralds and zero cash. Fund, Market, and Bank actions enable without
  a preliminary deposit. Screenshots were inspected for source labels and layout.
  Logs: `build/unified-funds-client-fabric-scoped.log` and its NeoForge counterpart.
  Screenshots: `build/client-smoke/unified-funds-{fabric,neoforge}-scoped/screenshots/tes-reader-ci/`.
- Both loader assemblies and packaged-JAR verification passed. `git diff --check`
  passed for the working tree.

## Limits / not claimed

The broader Fabric client smoke run stopped at the separate handbook recipe-hover
animation assertion (`Hover did not pause recipe animation`). It is not a fully green
client-suite result. The focused payment GUI checks subsequently passed independently;
host OSHI/Perflib warnings also remain outside this feature's validation.

Deterministic journal/save-failure tests are not an exhaustive power-loss or hostile-mod
test. Unresolved or legacy receipt-less pending transfers are retained and fail closed,
not guessed from inventory totals. Back up the world before upgrading, finish old
pending transfers on the previous version, and restore consistent whole-world backups.

No JAR was installed into the user's Minecraft profile, and no world was modified by
these tests. Test servers used disposable smoke worlds. No commit or GitHub push was
performed for this request.

## Playable artifacts

Install only the JAR matching the loader, not a sources JAR. Replace the older TES JAR;
do not leave two versions installed together.

- `fabric/build/libs/the-emerald-standard-fabric-0.4.0-beta.5.jar`
  SHA-256: `58c080bb05d78347bc634e317ff598552f61432de3620c9979d18b5b32fe7161`
- `neoforge/build/libs/the-emerald-standard-neoforge-0.4.0-beta.5.jar`
  SHA-256: `9ab88886f20aee0ef54742c26dddb5e42083769df3530bd9a58a1bf0f13b09da`
