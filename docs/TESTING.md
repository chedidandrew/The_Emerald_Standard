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
- Villager-loan default and recovery distributions
- Dynamic commodity movement
- Locked CD rates and maturity stopping
- Villager-loan maturity resolution
- Save and reload equality
- Primary-save corruption recovery without overwriting the known-good backup
- Mutation rollback after a failed save
- Backward-clock protection
- No-negative-balance and no-player-debt invariants

## Loader builds

GitHub Actions builds Fabric and NeoForge independently with `fail-fast: false`, so one loader failure does not cancel diagnostics for the other.

Local commands with Java 25 and Gradle installed:

```bash
gradle --no-daemon -p fabric build --stacktrace
gradle --no-daemon -p neoforge build --stacktrace
```

## Manual Minecraft checklist

1. Launch Minecraft 26.2 with exactly one loader JAR.
2. Run `/emerald help`, `/emerald market`, and `/emerald commodities`.
3. Deposit emeralds and verify they leave inventory only after a successful bank transaction.
4. Fill the inventory, withdraw, and verify no emeralds disappear.
5. Buy and sell every ticker and verify the spread is applied.
6. Open a CD, advance past maturity, and verify interest stops.
7. Close a CD early and verify the documented penalty.
8. Fund and collect villager business loans across multiple test worlds.
9. Exchange every accepted resource form.
10. Save, quit, wait at least 20 minutes, and verify one or more offline days advance.
11. Reopen immediately and verify the same market is retained rather than rerolled.
12. Test two multiplayer accounts and verify private balances with shared market prices.
13. Launch a dedicated server and confirm no client-only class loading occurs.

## Public release gate

A release may be published only when:

- Common regression tests pass.
- Fabric builds successfully.
- NeoForge builds successfully.
- Both release JARs launch in Minecraft 26.2.
- Deposit, withdrawal, investment, maturity, save/reload, and offline catch-up are manually verified.
- The release notes and `CHANGELOG.md` describe all changes.
