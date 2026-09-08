package com.chedidandrew.emeraldstandard.minecraft;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Guards the Minecraft-facing distinction between buildable lots and paveable public trails. */
public final class TrailGroundWiringRegressionTest {
    private TrailGroundWiringRegressionTest() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException("Repository root argument is required");
        }
        Path source = Path.of(args[0]).resolve(
                "common/src/minecraft/java/com/chedidandrew/emeraldstandard/minecraft/"
                        + "VillageProsperityManager.java");
        String java = Files.readString(source);

        String placement = methodBody(java, "private static boolean mayApplyPlacement");
        require(placement.contains("isPaveableTrailGround(current)"),
                "Trail placement is not wired to the narrow paving predicate");
        require(!placement.contains("isNaturalProjectGround(current)"),
                "Trail placement still uses the broader foundation/site predicate");

        String paveable = methodBody(java, "static boolean isPaveableTrailGround");
        for (String intended : List.of(
                "BlockTags.DIRT",
                "Blocks.GRASS_BLOCK",
                "Blocks.PODZOL",
                "Blocks.MYCELIUM",
                "Blocks.SAND",
                "Blocks.RED_SAND")) {
            require(paveable.contains(intended),
                    "Intended dirt-like trail ground is missing: " + intended);
        }
        for (String construction : List.of(
                "Blocks.STONE",
                "Blocks.ANDESITE",
                "Blocks.DIORITE",
                "Blocks.GRANITE",
                "Blocks.SNOW_BLOCK")) {
            require(!paveable.contains(construction),
                    "A common full construction block can still be paved: " + construction);
        }

        String natural = methodBody(java, "static boolean isNaturalProjectGround");
        for (String retainedFoundation : List.of(
                "Blocks.STONE",
                "Blocks.ANDESITE",
                "Blocks.DIORITE",
                "Blocks.GRANITE",
                "Blocks.SNOW_BLOCK")) {
            require(natural.contains(retainedFoundation),
                    "The broader natural lot/foundation predicate was narrowed accidentally: "
                            + retainedFoundation);
        }
        System.out.println("PASS trail ground wiring regression");
    }

    private static String methodBody(String source, String signature) {
        int method = source.indexOf(signature);
        require(method >= 0, "Missing source method: " + signature);
        int openingBrace = source.indexOf('{', method);
        require(openingBrace >= 0, "Missing method body: " + signature);
        int depth = 0;
        for (int index = openingBrace; index < source.length(); index++) {
            char character = source.charAt(index);
            if (character == '{') {
                depth++;
            } else if (character == '}' && --depth == 0) {
                return source.substring(openingBrace + 1, index);
            }
        }
        throw new AssertionError("Unterminated method body: " + signature);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
