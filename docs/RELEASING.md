# Release procedure

The Emerald Standard publishes only binaries produced by the normal read-only `build.yml` workflow. A publisher must not rebuild a release locally because doing so would produce bytes that were never covered by the recorded CI run.

## Candidate gate

1. Confirm Fabric and NeoForge declare the same version and that release notes describe the candidate honestly.
2. Push the exact candidate commit to `main` and wait for every standard workflow job: common tests, both builds, both packaged-JAR checks, both server launches, and both client launches.
3. Record the full 40-character source commit and successful workflow run. Do not use a pull-request-only run or a run for a different commit.
4. Download the Fabric and NeoForge artifacts from that run without changing their directory names. Each must contain one playable JAR, one sources JAR, and the CI-generated `SHA256SUMS` file.
5. Check out the exact source commit with a clean worktree and stage the release set:

```bash
bash scripts/prepare-release-assets.sh \
  <full-commit> \
  the-emerald-standard-fabric-<full-commit> \
  the-emerald-standard-neoforge-<full-commit> \
  staged-release
```

The staging script rejects abbreviated or mismatched commits, renamed artifact directories, symlinks, missing or extra artifact files, version disagreement, checksum failures, and a non-empty output directory. It produces exactly four JARs plus a combined `SHA256SUMS` and `RELEASE_MANIFEST.txt`.

## Publication

- For a beta, create a GitHub prerelease whose tag targets the exact recorded commit.
- Attach the four staged JARs, combined `SHA256SUMS`, and `RELEASE_MANIFEST.txt`. Do not attach files from `build/libs` or another workflow.
- Download the published assets into a fresh directory and verify them against the published checksum file. This catches upload mix-ups as well as byte changes.
- Record the workflow run, artifact IDs, full commit, tag, release URL, and final release-asset checksums in `release/BUILD_STATUS.md`.
- Keep the release marked beta until the manual matrix has real evidence for every Critical row on both loaders. An automated client bootstrap is not a visual or multiplayer playtest.

If any source, resource, metadata, wrapper, or workflow input changes after the successful run, the candidate has changed and must pass the complete exact-commit workflow again.
