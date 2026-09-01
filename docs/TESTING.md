# Testing and publication gate

## Automated common-core tests

Run:

```bash
bash scripts/run-common-tests.sh
```

The suite verifies:

- Gaussian mean, standard deviation, and both tails
- Exact deterministic replay for equal seeds
- VILX long-run CAGR and negative-year frequency
- Every individual company's long-run distribution
- Severe bear years and strong recovery years
- Multi-month and multi-year regime persistence
- Villager-lending default and recovery distributions
- Expected economics for 30, 90, 180, and 365-day lending terms
- Dynamic commodity movement and material-equivalent resource quotes
- Locked CD rates and maturity stopping
- Villager-lending maturity resolution
- Save and reload equality
- Partial game-day accumulation across repeated restarts
- Partial wall-clock accumulation across repeated restarts
- Catch-up cap, startup batch, background batches, and banking pause
- Inventory transaction journal prepare, commit, reload, adjust, and completion
- Journal blocking of unrelated account mutations
- Save-format 1 and 2 compatibility and future-format rejection
- Primary-save corruption recovery without overwriting the known-good backup
- Synchronous mutation rollback after a failed save
- Automatic-save retry backoff without deterministic progress rollback
- Backward-clock protection
- Trading spread application
- No-negative-balance and no-player-debt invariants
- Fabric and NeoForge version parity

## Loader builds

GitHub Actions builds Fabric and NeoForge independently with `fail-fast: false`, so one loader failure does not cancel diagnostics for the other.

Local commands with Java 25 and Gradle installed:

```bash
gradle --no-daemon -p fabric build --stacktrace
gradle --no-daemon -p neoforge build --stacktrace
```

## Dedicated-server smoke tests

After both builds pass, CI launches each loader's `runServer` task with an accepted EULA. The smoke script waits for both signals:

- `The Emerald Standard economy started`
- Minecraft's `Done (...)!` server-ready message

The process is then stopped and its log is uploaded as a workflow artifact. This catches loader entrypoint, dedicated-server classloading, world-path, persistence-startup, and lifecycle registration failures before manual testing.

Local smoke commands:

```bash
bash scripts/smoke-server.sh fabric
bash scripts/smoke-server.sh neoforge
```

## Manual Minecraft checklist

1. Launch Minecraft 26.2 with exactly one loader JAR.
2. Run `/emerald help`, `/emerald market`, and `/emerald commodities`.
3. Deposit emeralds and verify they leave inventory only after a successful bank transaction.
4. Fill the inventory, withdraw, and verify no emeralds disappear.
5. Interrupt a deposit or withdrawal, reconnect, and verify the recovery journal reconciles the exact quantity.
6. Buy and sell every ticker and verify the spread is applied.
7. Open a CD, advance past maturity, and verify interest stops.
8. Close a CD early and verify the documented penalty.
9. Fund and collect villager business lending across multiple test worlds.
10. Exchange every accepted resource form.
11. Play two sessions shorter than 20 minutes whose combined time exceeds 20 minutes and verify one economic day is retained.
12. Save, quit, wait at least 20 minutes, and verify one or more offline days advance.
13. Reopen immediately and verify the same market is retained rather than rerolled.
14. Test two multiplayer accounts and verify private balances with shared market prices.
15. Confirm a large catch-up backlog temporarily pauses banking and drains in bounded batches.
16. Launch a dedicated Fabric server outside the development environment.
17. Launch a dedicated NeoForge server outside the development environment.

## Public release gate

A public release may be published only when:

- Common regression tests pass.
- Fabric builds successfully.
- NeoForge builds successfully.
- Fabric dedicated-server smoke passes.
- NeoForge dedicated-server smoke passes.
- Both release JARs launch in a Minecraft 26.2 client.
- Deposit, withdrawal, recovery, investment, maturity, save/reload, partial-day, and offline catch-up behavior are manually verified.
- The release notes and `CHANGELOG.md` describe all changes.
