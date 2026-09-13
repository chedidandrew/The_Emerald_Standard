# Beta.16 — Continuous newspaper reading and subtle paper wear

Date: 2026-09-12. Unreleased local candidate; no installation, publication, commit or live-save changes.

## Changes

The portable paper follows a single bounded route: cover -> all Contents sheets -> all ranked stories.
Wheel scrolling moves through text and into the next story at its end; Next advances one two-column
view before changing stories. Previous/upward scrolling reverses the route and lands at the previous
story's end. Disabled boundary buttons stop at the cover and final story end. Direct Front/Contents
and story links remain available. Contents labels use story numbers, and the article footer gives the
visible text range, rather than suggesting every article occupies exactly one printed page.

The fixed “The Voice of the Overworld” subtitle was not dynamic and was removed. The Emerald Wire
remains as the centered masthead. A bounded, deterministic GUI-native layer adds low-opacity fibres,
paired fold highlights/shadows, a small corner crease and a tiny margin notch. It draws behind ink,
does not animate, requires no external bitmap, and keeps the margin wear outside text columns.
The item sprite, recipe and Exchange Desk channel/search browser were not changed.

Economy format remains 36. No market simulation, transactions, balances, world blocks, news-event
generation, transport permissions or server policy changed in this revision.

## Validation

- Full common suite: 87 Java entry points plus loader-version and wrapper checks passed; exit 0.
  Log: `build/beta16-common.log`.
- New pure navigation tests traverse 0, 1, 19 and 256 stories with contents capacities 1/8/16/24
  and multiple step sizes. They cover both directions, long-story text, zero-delta input and edition
  endpoints. Paper-mark tests check deterministic bounded geometry at small/large dimensions.
- Both Fabric and NeoForge native newspaper clients passed at GUI scales 2 and 4. A real-font
  19-story fixture overflows Contents and includes a long multi-view article. Tests exercise actual
  wheel events and native Previous/Next controls, every Contents boundary, forward/reverse story
  transitions, complete edition traversal, empty/last/first boundaries, resize/reflow, accepted
  editions preserving retained IDs and immediate privacy removal.
- Existing search, outlet cycling, incoming-edition stability, 100-entry far-archive search, newspaper
  recipe and read-only presentation checks remain. The initial native test attempt revealed that the
  test-only acceptance helper had not received the newly published fixture before accepting it; the
  helper now explicitly receives first. The subsequent full native runs passed.
- Logs: `build/beta16-fabric-news-b.log`, `build/beta16-neoforge-news-b.log`.
  Screenshots: `build/beta16-{fabric,neoforge}-news-b/screenshots/tes-reader-ci/`.
  Selected cover, Contents and article screenshots were visually inspected across GUI scales 2/4;
  the fold/edge wear is noticeable but low contrast, and the masthead has no subtitle.
- Long-form and compact handbook text updated together, with explicit navigation/texture regression
  assertions. The stronger resource test passed again after its addition. Native real-font checks
  passed for all 60 compact pages and all 67 guided sections, including long chapter ends.
- No new dedicated-server smoke was required for this client-only revision; do not treat prior
  beta.15 server logs as a beta.16 full server certification. Both loader build gates are checked below.

## Final candidate verification

Both final Gradle builds passed (exit 0), including native build gates:
`build/beta16-fabric-build.log` and `build/beta16-neoforge-build.log`.
All 137 required packaged entries were present in each candidate. Packaged versions,
current-source identity and cross-loader parity passed; `git diff --check` passed.
Older candidate JARs were left untouched.

Shared source SHA-256:
`08d82570cdfc75e7b991d77549d12cfdbbe760528eaff904d55e29a62486c09b`

| Candidate | JAR SHA-256 |
| --- | --- |
| `fabric/build/libs/the-emerald-standard-fabric-0.4.0-beta.16.jar` | `598B2408D027D85F4DC3339113F09C833E196CED2DB987A4898AEBDF65E9830A` |
| `neoforge/build/libs/the-emerald-standard-neoforge-0.4.0-beta.16.jar` | `23F2336F117B280311FAFF5BD905D6F85681967C1FD0B2592129BB335765EB65` |

Native client runs logged the existing Windows OSHI performance-counter warnings; controlled
client shutdown and the actual mod-test success markers were checked independently. No mod
assertion or rendering failure was treated as an acceptable warning.

## Handbook review

Reviewed the portable newspaper and recipe explanations against actual reader behavior. Updated
long/compact navigation guidance, story-vs-page terminology, edition endpoints and subtle paper
appearance. The Desk remains a searchable archive of the same published facts; accepting new
stories and privacy semantics remain unchanged. No artificial recipe changes were made.

The broader extended human-play matrix remains uncertified. Use the correct loader JAR and
matching client/server versions; back up worlds before changing mod versions.
