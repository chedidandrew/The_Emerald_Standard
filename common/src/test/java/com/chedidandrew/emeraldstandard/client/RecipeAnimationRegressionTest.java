package com.chedidandrew.emeraldstandard.client;

import java.util.HashSet;
import java.util.Set;

public final class RecipeAnimationRegressionTest {
    public static void main(String[] args) {
        RecipeAnimation animation = new RecipeAnimation();
        check(animation.frame(100, false) == 0, "initial frame");
        check(animation.frame(999, false) == 0, "not too fast");
        check(animation.frame(1000, false) == 1, "900 ms cadence");
        check(animation.frame(9000, true) == 1, "pause freezes frame");
        check(animation.frame(9900, false) == 2, "resume does not catch up paused time");
        check(animation.frame(9800, false) == 2, "negative time is ignored");
        for (int count = 1; count <= 4; count++) {
            int total = 1;
            for (int i = 0; i < count; i++) total *= 9 - i;
            Set<String> arrangements = new HashSet<>();
            for (int frame = 0; frame < total; frame++) {
                Set<Integer> occupied = new HashSet<>();
                StringBuilder arrangement = new StringBuilder();
                for (int i = 0; i < count; i++) {
                    int slot = RecipeAnimation.shapelessSlot(i, count, frame);
                    check(slot >= 0 && slot < 9 && occupied.add(slot), "collision/out of bounds");
                    arrangement.append(slot);
                    check(slot == RecipeAnimation.shapelessSlot(i, count, frame + total), "cycle wrap");
                }
                check(arrangements.add(arrangement.toString()), "duplicate arrangement");
            }
            check(arrangements.size() == total, "all arrangements covered");
        }
        System.out.println("PASS animated recipes: timing, pause/resume, and all shapeless arrangements");
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
