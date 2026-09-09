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

Validation is in progress. No final candidate or merge is certified by this document yet.
Minecraft remains 26.2, Java 25, both loaders 0.4.0-beta.4 and economy format 18.
