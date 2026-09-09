# Settings state consistency follow-up

Source review after the initial green beta.4 CI found two small interface-state issues: returning from a nested handbook could leave the parent Settings screen showing the old reader percentage, and a malformed numeric draft equal to `true` or `false` could be rendered as a toggle after pagination. Existing server-side validation already rejected the latter as an invalid number; it was an editor usability issue, not a configuration-validation bypass.

This follow-up reloads the saved reader preference during Settings initialization and chooses control type from the original validated configuration snapshot instead of draft text. It preserves unapplied world drafts, server ownership, atomic persistence, all existing limits, and every gameplay class.

The patch needs its own CI verification and is not represented as a hands-on multiplayer test. It preserves the earlier implementation and visual review history in docs/HANDBOOK_AND_SETTINGS.md and docs/reviews/2026-09-09-reader-visual-polish.md.
