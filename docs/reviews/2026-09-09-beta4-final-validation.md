# Beta.4 final validation and cursor investigation

This review continues the [previous handoff](2026-09-09-beta4-reader-settings-handoff.md).
Historical failures and prior review documents are retained. No stable release is authorized.

## X11 diagnosis

The exact retry logs from [run 34309499608](https://github.com/chedidandrew/The_Emerald_Standard/actions/runs/34309499608),
jobs 102335052404 (Fabric/Mod Menu present) and 102335052450 (absent), show a three-line
Minecraft render-thread ERROR group: `########## GL ERROR ##########`, `@ Render`, and
`65547: X11: Standard cursor shape unavailable`. Both then report reader/settings success,
`Stopping!`, and Gradle success. The shell's strict ERROR gate, not a client crash, failed.

65547 is GLFW's `GLFW_CURSOR_UNAVAILABLE` (0x1000B), not an OpenGL error code.
[GLFW documents](https://www.glfw.org/docs/latest/group__errors.html) this as a platform/cursor-theme limitation.
The [GLFW 3.4 X11 implementation](https://github.com/glfw/glfw/blob/3.4/src/x11_window.c#L2733-L2824)
first tries Xcursor theme images; its core-font fallback cannot supply NOT_ALLOWED or diagonal
resize shapes. The exact error is emitted only in that unsupported fallback case.

Inspection of the actual Minecraft 26.2 `CursorTypes` bytecode shows vanilla initializes eight
standard shapes, including NOT_ALLOWED (but neither diagonal resize). `CursorType.createStandardCursor`
returns its supplied default when GLFW returns a zero handle. Thus the missing themed
NOT_ALLOWED cursor explains both the logged error and continued rendering. The mod uses
ordinary Minecraft widgets; it does not implement an X11 cursor path. Both Fabric variants
failing excludes optional Mod Menu as the cause. This is a minimal virtual-desktop dependency
problem exposed by vanilla cursor initialization, not a failed handbook entrypoint.

The candidate fix explicitly installs `libxcursor1` and `adwaita-icon-theme` alongside Xvfb,
selects `XCURSOR_THEME=Adwaita`, and checks the not-allowed image exists. A real-client platform
probe requires all eight vanilla cursor shapes to produce nonzero handles before opening the UI.
No GLFW/X11 error or generic GL ERROR group is whitelisted. The original strict error detection
remains active, including entrypoint, mixin, missing-class and crash checks. Final CI must prove
the environment repair works; any remaining cursor error is still fatal to the test.

## Verification record

Cursor-fix commit `851668781663cced76362882a33063e8338c6cf6` passed **all eight jobs** in
[run 34317794478](https://github.com/chedidandrew/The_Emerald_Standard/actions/runs/34317794478).
Both Fabric variants pass the new eight-shape probe with no cursor error. This confirms the
desktop-environment repair; narrator unavailability retains its pre-existing narrow handling.
There is no new X11/GLFW allowlist. A separate strict-log regression injects cursor errors,
other errors, crashes, missing markers, missing restart evidence and nonzero process exits;
every such fixture must fail. The original error detection is extracted into a reusable checker,
not removed or weakened.

The subsequent candidate strengthens real-client checks through all chapters, search, wheel/key
scrolling, text-size bounds, both directions of nested Settings navigation and preference reload.
The smoke harness launches two independent JVMs using the same isolated profile and requires
success from both. Fabric queries Mod Menu's actual registered Configure lookup, not a freshly
constructed entrypoint; NeoForge queries the installed config extension. Absence variants verify
Mod Menu is not present. Every world key has invalid mixed-draft rejection and valid write/reload
coverage. The README now identifies beta.4 source builds and labels the badge as main-only.

Local Windows Java 25 clients at 1280x800 exercised the reader without Mod Menu and then
restarted with Mod Menu 20.0.1. Navigation, search, every chapter's scroll end, 80/120% bounds,
Settings-return refresh, reopened preferences and the registered Configure lookup passed.
Default, 80%, 120%, scrolled-bottom and Settings screenshots were inspected: no clipping,
overlapping controls or excessively bright headings were seen. No further visual redesign was
needed beyond the prior contrast/spacing fixes. These are automated real-client interactions
and screenshot inspection, not a human gameplay session.

The exact strengthened candidate and eventual merge still require full CI verification.
Minecraft remains 26.2, Java 25, both loaders 0.4.0-beta.4 and economy format 18.

## Scope, compatibility and remaining checks

Package checks require the new reader, settings, preferences and handbook item classes, plus
the Fabric config entrypoint. Fabric metadata must keep Mod Menu optional and must not bundle it.
Local common regressions and strict-log failure fixtures pass. The unchanged handbook subclass
retains the item ID and WrittenBookItem lectern path; only that item's held-use callback opens
the reader. Ordinary written books are not replaced. Onboarding's toggle only guards future
delivery attempts; it neither removes existing books nor clears delivery tags.

Config source inspection confirms integrated-server identity checks, server-thread application,
whole-draft validation and atomic replacement before runtime application. All 27 keys are tested
with real temporary files, including invalid mixed drafts and valid write/reload. No active world
is written from the title screen. Remote multiplayer has no integrated-server editor authority.
Done closes the screen without applying its draft. These ownership and discard paths were
reviewed in source; an in-world GUI edit/rejoin and live remote multiplayer session were not run.

No manual Exchange Desk/deposit/withdraw/rejoin sequence, old-world lectern interaction, or
onboarding delivery session was performed in this pass. The automated real-client checks are
title-screen tests, not a replacement for those gameplay checks. Existing saves and the user's
structure-review worlds were not modified. The main-only September 8 repository review is
preserved, including its inventory-recovery isolation concern and synchronous-save performance
risk; this focused handbook/configuration pass does not claim to resolve them. This remains an
unreleased beta, not a stable-release certification.

## Merge gate

Preserve main's documentation by merging its history into the development candidate. Require
all eight jobs on that exact candidate, then use a normal PR merge commit (no squash/rebase).
The resulting main commit must independently pass all eight jobs. The PR and task handoff
record the final immutable commit/run links, avoiding a documentation-only post-validation
change. Do not publish a release, create a stable tag, or delete the development branch.
