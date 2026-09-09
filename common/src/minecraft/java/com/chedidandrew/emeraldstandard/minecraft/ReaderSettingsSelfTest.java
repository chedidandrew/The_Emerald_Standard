package com.chedidandrew.emeraldstandard.minecraft;

import com.chedidandrew.emeraldstandard.client.HandbookLayout;
import com.chedidandrew.emeraldstandard.client.ReaderPreferences;
import com.chedidandrew.emeraldstandard.core.EconomyService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.Properties;

/** Real file/validation regressions, not merely source-string wiring assertions. */
public final class ReaderSettingsSelfTest {
    public static void main(String[] args) throws Exception {
        for (int width : new int[] {320, 426, 640, 960, 1920}) {
            for (int height : new int[] {240, 360, 540, 1080}) {
                var layout = HandbookLayout.fit(width, height);
                check(layout.x() >= 0 && layout.y() >= 0, "negative panel origin");
                check(layout.x() + layout.width() <= width, "panel exceeds screen");
                check(layout.bodyX() + layout.bodyWidth() < width, "text exceeds screen");
                check(layout.bodyY() + layout.bodyHeight() < height, "text exceeds height");
                check(layout.bodyWidth() > 114, "reader must be wider than vanilla book text");
                check(layout.visibleChapters() >= 1, "no usable navigation");
            }
        }
        check(HandbookLayout.maximumScroll(100, 12) == 88, "last line unreachable");
        check(HandbookLayout.maximumScroll(3, 12) == 0, "empty scroll range");
        Path dir = Files.createTempDirectory("tes-reader-settings-");
        try {
            Path prefs = dir.resolve("client/reader.properties");
            check(ReaderPreferences.load(prefs) == 90, "default text size");
            ReaderPreferences.save(prefs, 80);
            check(ReaderPreferences.load(prefs) == 80, "preference did not persist");
            ReaderPreferences.save(prefs, 140);
            check(ReaderPreferences.load(prefs) == 120, "font bound bypassed");
            Path world = dir.resolve("world/data");
            EmeraldConfig original = EmeraldConfig.load(world);
            original.applyTo(new EconomyService());
            check(original.values().size() == 27, "incomplete settings snapshot");
            // Exercise each editor key, not just representative settings. Invalid mixed drafts
            // must leave the complete active snapshot and exact original file bytes unchanged.
            for (var entry : original.values().entrySet()) {
                String key = entry.getKey();
                boolean toggle = entry.getValue().equals("true") || entry.getValue().equals("false");
                for (String invalid : toggle ? new String[] {"", " ", "maybe", "1"}
                        : new String[] {"", " ", "text", "1.5", "-1", "2147483648", "2147483647"}) {
                    byte[] unchanged = Files.readAllBytes(world.resolve("the_emerald_standard-config.properties"));
                    Map<String, String> draft = new java.util.LinkedHashMap<>();
                    draft.put("onboarding.join_hint_enabled", "false");
                    draft.put(key, invalid);
                    expectFailure(() -> EmeraldConfig.update(world, original, draft));
                    check(EmeraldConfig.current() == original, "invalid " + key + " changed runtime");
                    check(Arrays.equals(unchanged, Files.readAllBytes(world.resolve(
                            "the_emerald_standard-config.properties"))), "invalid " + key + " changed disk");
                }
                Properties valid = new Properties(); valid.putAll(original.values());
                valid.setProperty(key, toggle ? "false" : entry.getValue());
                check(EmeraldConfig.parse(valid).values().get(key).equals(valid.getProperty(key)),
                        "valid key rejected: " + key);
            }
            Properties props = new Properties(); props.putAll(original.values());
            check(EmeraldConfig.parse(props).values().equals(original.values()), "snapshot drift");
            Path file = world.resolve("the_emerald_standard-config.properties");
            byte[] before = Files.readAllBytes(file);
            expectFailure(() -> EmeraldConfig.update(world, original,
                    Map.of("village_prosperity.development_radius", "-1")));
            check(Arrays.equals(before, Files.readAllBytes(file)), "invalid save changed disk");
            check(EmeraldConfig.current() == original, "invalid save changed runtime");
            expectFailure(() -> EmeraldConfig.update(dir.resolve("other"), original,
                    Map.of("market.events_enabled", "false")));
            expectFailure(() -> EmeraldConfig.update(world, original, Map.of("unknown", "1")));
            expectFailure(() -> EmeraldConfig.update(world, original, Map.of("market.events_enabled", "maybe")));
            expectFailure(() -> EmeraldConfig.update(world, original, Map.of("transactions.cooldown_ticks", "")));
            EmeraldConfig saved = EmeraldConfig.update(world, original, Map.of(
                    "market.events_enabled", "false", "onboarding.join_hint_enabled", "false"));
            check(!saved.marketEventsEnabled() && !saved.onboardingJoinHintEnabled(), "update not applied");
            check(EmeraldConfig.load(world).values().equals(saved.values()), "update not persistent");
            expectFailure(() -> EmeraldConfig.update(world, original, Map.of("market.events_enabled", "true")));
            EmeraldConfig latest = EmeraldConfig.current();
            Files.writeString(file, "market.events_enabled=true\n");
            byte[] external = Files.readAllBytes(file);
            expectFailure(() -> EmeraldConfig.update(world, latest, Map.of("market.events_enabled", "false")));
            check(Arrays.equals(external, Files.readAllBytes(file)), "external edit overwritten");
            Path allKeysWorld = dir.resolve("all-keys/data");
            EmeraldConfig active = EmeraldConfig.load(allKeysWorld);
            active.applyTo(new EconomyService());
            for (String key : active.values().keySet()) {
                String old = active.values().get(key);
                String next = old.equals("true") ? "false" : old.equals("false") ? "true"
                        : Integer.toString(Integer.parseInt(old) + (key.equals("economic_clock.max_offline_days") ? -1 : 1));
                EmeraldConfig updated = EmeraldConfig.update(allKeysWorld, active, Map.of(key, next));
                check(updated.values().get(key).equals(next), "valid write failed: " + key);
                active = EmeraldConfig.load(allKeysWorld);
                check(active.values().equals(updated.values()), "reload lost setting: " + key);
            }
            System.out.println("PASS reader geometry, client preferences, atomic world edits and stale-world rejection");
        } finally {
            try (var paths = Files.walk(dir)) {
                for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
            }
        }
    }
    private static void expectFailure(IOAction action) throws Exception {
        try { action.run(); } catch (IOException expected) { return; }
        throw new AssertionError("invalid edit was accepted");
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    private interface IOAction { void run() throws Exception; }
}
