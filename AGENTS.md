# The Emerald Standard working agreement

## Keep the in-game handbook current

For every requested addition, change, or revision, review the in-game handbook against the
actual implementation. Do not finish a player-facing change without updating its relevant
handbook explanations, examples, recipes, limitations, and safety guidance in the same change.
Use guided prose, not just a terse feature list.

- Long-form chapters: common/src/client/java/com/chedidandrew/emeraldstandard/client/HandbookChapters.java
- English reader text: common/src/main/resources/assets/the_emerald_standard/lang/en_us.json
- Animated recipes: HandbookRecipes.java and HandbookScreen.java in the client package
- Compact legacy/lectern pages: EmeraldHandbook.java and the book.* localization keys
- Update handbook/recipe regression coverage and relevant client checks together.
- Check recipes against server recipe definitions; do not describe unfinished features as live.
- Record the handbook review in validation notes. Internal-only changes with no change to
  player behavior need an explicit accuracy review, not artificial edits to unrelated pages.

Creative spawn eggs are intentionally creative-only, per the user's choice. Usable custom
blocks should have documented Survival recipes. Keep all registered player-facing content
discoverable in the mod's own creative tab, with coverage that catches omissions.
