# Repository review and playable build handoff: 2026-09-08

## Scope and identity

Andrew requested a repository review, a rating, and a playable JAR for Minecraft testing. This document records the assessment, evidence, limitations, and binary identity. It does not modify gameplay or claim that identified concerns have been fixed.

- Reviewed source: `08ea629606949769bf63b550b3dbcc6bab093f24`, Improve banking UX and village progression.
- Minecraft: Java Edition 26.2.
- Internal mod version: 0.4.0-beta.2.
- Existing successful CI: [run #300](https://github.com/chedidandrew/The_Emerald_Standard/actions/runs/33946822679), September 5, 2026 UTC.
- Delivered binaries: existing commit-specific CI artifacts, not a newly compiled Minecraft build.
- Older published prerelease: v0.4.0-beta.2, source `ae8e5d8a2a4eeea8ea8846291efbe0a75515d07a`.

Review scope was targeted production-source inspection, repository documentation and workflow inspection, CI results/logs, and local artifact verification. The production sources were inspected from the CI source archives and important files were matched against Git blob hashes at the reviewed commit. This was not an every-line audit or a hands-on Minecraft play session.

## Rating

**Overall: 7.5/10 as a playable beta. Not stable-release ready.** These are engineering judgments, not measured user-satisfaction scores or a test-coverage percentage.

| Area | Rating | Reason |
| --- | --- | --- |
| Feature implementation and fit to the original goal | 9/10 | In-world banking, a graphical dashboard, investing, commodities, and settlement development are substantially implemented. |
| Architecture and safeguards | 8/10 | Shared loader-neutral logic, validation, bounded operations, journaled transfers, checksummed saves, and regression tests are strengths. Recovery isolation still needs investigation. |
| Interface implementation | 8/10, provisional | Charts, exact amounts, balance previews, confirmation, responsive layout, and activity filters exist. Usability has not been demonstrated by human test evidence. |
| Large-world performance confidence | 6.5/10 | Simulation is bounded and scale-tested, but synchronous whole-economy saves have significant measured costs. |
| Stable-release readiness | 6/10 | Automated build/startup gates pass; manual testing, failure-path integration testing, and unique build identity need attention. |

The original player-debt restriction remains central: players supply capital to villagers rather than borrowing. The reviewed operations check available balances and limit investment exposure. This is not a proof that every interoperability or failure path is safe.

## Verified evidence

### Existing CI

All seven jobs in run #300 completed successfully: common regression tests; Fabric build/package verification; NeoForge build/package verification; Fabric server startup; NeoForge server startup; Fabric client bootstrap; NeoForge client bootstrap.

The [common regression job](https://github.com/chedidandrew/The_Emerald_Standard/actions/runs/33946822679/job/101254323012) records passing economy, persistence, village prosperity, project catalog, finance/Fund, scale, layout, texture, typed-amount, packet packing, configuration, confirmation, foundation, materialization, progression, version-parity, and wrapper-checksum suites. This is suite-level evidence, not a count of individual assertions.

### Local verification in this review

- Both artifact ZIP SHA-256 digests matched GitHub's reported digests.
- Both playable JARs and both source JARs matched their artifact-provided SHA256SUMS.
- All ZIP entry integrity checks passed.
- Every JSON resource parsed successfully: 11 in Fabric, 10 in NeoForge.
- Each playable JAR contains 136 class files, all class major version 69, consistent with Java 25.
- Fabric and NeoForge loader/dependency metadata were inspected inside the playable binaries.
- Six extracted production files matched their repository Git blob hashes: BankerScreen, EconomyEngine, EconomyService, EconomyPersistence, EconomyState, and BankTransactionCoordinator.
- The loader-neutral core compiled with the available local Java 21 compiler, and EconomySelfTest printed `PASS 100-year VILX smoke test, CAGR 7.34%`. This supplemental core-only check is not a Minecraft launch, and the distributed Java 25 JARs were not modified or recompiled.

No hands-on GUI, terrain-generation, multi-hour multiplayer, or injected Minecraft player-data I/O-failure test was performed. No manual test row was marked passed.

## Priority findings

### 1. High: unresolved inventory recovery must be isolated from ordinary inventory changes

**Status: source-derived failure scenario, not reproduced in a running Minecraft instance.**

BankingOperations.withdraw commits the bank debit, delivers items, and then attempts a verified player-data checkpoint. A failure returns RECOVERY_PENDING. BankTransactionCoordinator.reconcile later compares current total inventory counts to the journal's expected count and restores apparent missing items. The inspected handlers log recovery failures and block further bank mutations, but no ordinary-inventory isolation was found in those paths.

Failure-injection scenario: a withdrawal delivers emeralds, the player checkpoint fails, and the player transfers those emeralds to a chest or another player before recovery succeeds. Count-based recovery can interpret the moved emeralds as an undelivered withdrawal and restore them. Conversely, unrelated newly acquired items can be mistaken for rolled-back deposit inventory. Repeated recovery attempts and death also need coverage.

Before stable release, establish transaction provenance or an explicit isolation strategy, and test chest transfers, dropping/picking up items, trading, death, disconnects, and retries while a journal remains unresolved. Do not remove durable checkpoints as a performance shortcut.

Relevant reviewed sources:

- [BankingOperations.java](https://github.com/chedidandrew/The_Emerald_Standard/blob/08ea629606949769bf63b550b3dbcc6bab093f24/common/src/minecraft/java/com/chedidandrew/emeraldstandard/minecraft/BankingOperations.java), withdrawal and readiness paths.
- [BankTransactionCoordinator.java](https://github.com/chedidandrew/The_Emerald_Standard/blob/08ea629606949769bf63b550b3dbcc6bab093f24/common/src/minecraft/java/com/chedidandrew/emeraldstandard/minecraft/BankTransactionCoordinator.java), reconcile.
- PendingInventoryTransaction.expectedInventoryCount and the loader recovery handlers.

### 2. High for larger servers: synchronous whole-economy persistence

[EconomyService.mutatePlayer](https://github.com/chedidandrew/The_Emerald_Standard/blob/08ea629606949769bf63b550b3dbcc6bab093f24/common/src/main/java/com/chedidandrew/emeraldstandard/core/EconomyService.java#L2617-L2662) synchronously calls state.save(path) after a successful mutation. Persistence validates and serializes the full economy and writes a durable replacement. An inventory transfer can involve multiple durable economy mutations plus a player checkpoint.

The existing CI log measured a mature fixture with 100 accounts, 365 history samples, 128 ledger entries, 16 positions, and 256 Fund entries as follows:

| Operation | Time |
| --- | --- |
| Initial save | 833 ms |
| Replacement save | 842 ms |
| Load | 685 ms |

The serialized fixture was 14,943,362 bytes. These are CI fixture measurements, not observed live-server lag or measured end-to-end transaction latency. They nevertheless identify a credible server-thread scaling risk. Profile real transaction/tick latency before wider multiplayer release, and consider incremental journaling/checkpointing without weakening crash consistency.

### 3. Release gate: hands-on evidence remains missing

The [manual matrix](../MANUAL_TEST_MATRIX-0.4.md) still records Not run for its hands-on tests. Startup and pure-logic tests do not establish natural-looking construction, readable screens at all scales, easy onboarding, compatibility with other mods, or safe long multiplayer sessions. Complete critical financial, recovery, upgrade, and multiplayer evidence before calling this stable.

### 4. Build identity: different binaries share 0.4.0-beta.2

The older published release and the newer reviewed CI build both identify themselves internally as 0.4.0-beta.2. Their binaries are different. The next distribution should increment the prerelease version or embed a commit identity.

For this handoff, external filenames include mc26.2-08ea629 while binary contents remain unchanged. Multiplayer participants must use the same exact loader-specific binary, not merely the same displayed version string.

### 5. Maintainability and documentation drift

BankerScreen has 2,635 lines, BankerMenu 2,216, EconomyService 2,962, and VillageProsperityManager 2,887 in the inspected sources. Incremental decomposition by page, transaction type, and construction responsibility would simplify future changes. File length alone is not a defect.

The manual matrix includes a physical-completion gate for project benefits, whereas the current README describes economic completion before queued physical construction. Reconcile that expectation before testing. The quick start also uses older Overview/Savings navigation labels, while the current page list uses Account and Banking/Transfers.

Claim protection is cooperative: external protection rules require a registered VillageDevelopmentProtection guard. Universal claim-mod compatibility has not been demonstrated.

## Strengths

The in-world Banker and Exchange Desk loop meets the intended non-command-heavy direction. The seven-page dashboard implements market and account charts, exact amounts, balance previews, activity filters, and server-owned confirmation. Financial logic is shared across loaders, and reviewed player operations do not expose borrowing. The village economy separates compact simulation from bounded physical construction rather than force-loading distant chunks. Persistence and both-loader CI are substantial foundations, despite the remaining failure-path concern.

## Playable JAR identity and requirements

Both files require Minecraft Java Edition 26.2 and Java 25 or a compatible newer runtime. Use Java 25 for the initial test.

### Fabric

- Filename: `the-emerald-standard-fabric-0.4.0-beta.2-mc26.2-08ea629.jar`
- Size: 523,605 bytes.
- SHA-256: `7885a41796ae7a5065c3058da85aadb2fd1e732f0236e40ad2f5a2291ce1967c`
- Artifact ID: 9963605546.
- Metadata requires Fabric Loader >=0.19.3 and Fabric API >=0.158.0+26.2. Use a Fabric API build specifically compatible with Minecraft 26.2.

### NeoForge

- Filename: `the-emerald-standard-neoforge-0.4.0-beta.2-mc26.2-08ea629.jar`
- Size: 520,950 bytes.
- SHA-256: `4bd924ef38d6bc328861f675fcd76f81c15e0eb1ec3963bddf78b98ecce22298`
- Artifact ID: 9963603561.
- Metadata requires NeoForge >=26.2.0.72 and Minecraft 26.2. Use a compatible 26.2 loader build, not an arbitrary newer Minecraft branch.

Install exactly one playable loader-specific JAR in the instance's mods folder. Do not install sources JARs, both loader builds, or a second copy of the mod. Fabric also requires the matching Fabric API. Multiplayer requires the same exact loader-specific binary on server and clients.

Start in a disposable test world or a backed-up copy. For immediate GUI testing, Creative search exposes the Exchange Desk. For the intended survival loop, enter a loaded Overworld village and allow the periodic bank scan to run, then interact with a Banker or desk. Test a small deposit and withdrawal and verify values after leaving and rejoining. Normal gameplay does not require commands.

## Change record

This commit adds only this review document. It does not fix the recovery concern, change persistence or gameplay, bump the version, publish a release, change manual-test results, or modify the delivered CI binaries. The reviewed source commit and binary hashes above identify the test build independently of this documentation commit.
