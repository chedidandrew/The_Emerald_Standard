# Handbook reader and configuration repair

## Request and scope

Andrew reported that the starting book was too small, its text felt too large, and its presentation was messy. The Mod Menu config action also did not work. This change is based on development-branch source d21b2ff267e9c856e0bd0cc2ec31456d1f933b68 and advances both loaders to 0.4.0-beta.4. It does not merge the architecture branch into main or claim a stable release.

## Changes

- Keep the existing handbook item id, first-join delivery rules, replacement recipe, saved stacks, and written content. The registered item now opens a custom reader when held; other written books are unchanged. Lectern usage retains vanilla behavior.
- Replace the narrow held-book page with a responsive, wide reader, a chapter sidebar, search, previous/next controls, keyboard navigation, scrollable text, and an explicit Done button.
- Regroup the guide into 16 chapters. Remove repeated inline navigation and decorative object glyphs from this reader, reflow prose, retain section headings, and spell out the crafting grid. Do not delete the original 46-page content used by legacy book views.
- Default reader text to 90 percent and expose A-/A+ controls from 80 through 120 percent. Store the preference in config/the_emerald_standard-client.properties separately from world settings. Changing reader size does not change Minecraft's global GUI scale.
- Register an optional Fabric Mod Menu API entry point and a NeoForge config-screen extension. The same screen is accessible through Settings in the handbook, even without Mod Menu installed.
- Present all 27 existing world settings in a paged editor when the local integrated world is open. Boolean controls and integer fields keep edits until Apply; Done discards unapplied world edits. Reader-size changes save immediately.
- Main-menu and remote-server sessions do not pretend to edit a world's economy. They expose reader settings and explain the ownership boundary. Remote administrator configuration remains on the server; this patch does not introduce a remote config-write protocol.
- Validate the whole proposed configuration before writing. Reject unknown, empty, malformed, out-of-range, externally changed, or stale-world edits. Apply changes on the owning integrated server thread only after atomic file replacement, then show success or failure. Preserve every pre-existing config key, range, and default.
- Replace the client smoke helper's fixed eight-second readiness assumption with a bounded resource/language readiness check. Retain its real-font validation for the legacy book, and add actual reader/settings opening and screenshot capture.
- Add build-time file and geometry tests and separate Fabric client runs with and without optional Mod Menu. CI remains read-only, retains all earlier test jobs, and uploads screen evidence alongside logs.

## Using the update

Install only the JAR matching the loader, replacing the old mod file. Minecraft 26.2 and Java 25 remain required. Fabric still requires matching Fabric API; Mod Menu is optional. The compile-time Mod Menu API target is 20.0.1 for 26.2. No configuration-library dependency is added to the playable mod.

Use an existing or newly crafted handbook to open the new reader. A- makes its text smaller; A+ makes it larger. Search finds chapter titles and body text. Scroll the body or use Up/Down, Page Up/Page Down, Home/End; Left/Right change chapters. Tab navigates buttons and the search field. Escape returns to the previous screen.

In single player, open the world, then Mods > The Emerald Standard > Configure, or Handbook > Settings. Adjust world settings and press Apply. Starting book and discovery hint controls future eligible first-join delivery; disabling it does not delete an already received book or reset the one-time marker. Reader size works even on the main menu or a multiplayer server.

## Compatibility and boundaries

The economy format remains 18. Existing architecture, balances, trading, lending, and recovery logic are unchanged. The previously documented inventory-recovery failure scenario and synchronous whole-economy save cost are not fixed by this UI work. The custom reader warns against continuing ordinary item transfers during repeated unresolved recovery instead of asserting that every journal failure is safe.

The new surrounding interface labels are English in this patch; the guide's existing content uses its existing translation keys. Long content scrolls instead of being forced into a fixed book-page height. The reader does not modify other mods' books or global font settings.

## Validation record

The new pure geometry/preferences/configuration suite passed in the review container against the implementation prototype. This checks default and persisted text size, bounds, all 27 settings, valid writes, invalid-value rejection without disk/runtime mutation, stale snapshots, wrong-world paths, and external-file conflicts. This is not a claim of a local Minecraft launch.

The committed build must pass its own common tests, both loader builds and package checks, both dedicated servers, and all three client variants before a verified JAR handoff. Client screenshots are generated only under the explicit CI smoke property in isolated temporary game directories. Hands-on multiplayer testing and the existing manual matrix remain unclaimed.

## Change history

Initial implementation: wide held-handbook reader, separate persistent text preference, optional loader config entry points, guarded integrated-world editor, readiness-gated client tests, screen captures, and beta.4 build identity. No existing world or installed player instance was modified while making this patch.
