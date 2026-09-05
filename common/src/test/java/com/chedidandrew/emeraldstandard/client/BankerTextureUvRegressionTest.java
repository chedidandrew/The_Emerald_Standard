package com.chedidandrew.emeraldstandard.client;

import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import javax.imageio.ImageIO;

/** Prevents profession artwork from leaking into the villager head, body, legs, or hat rim. */
public final class BankerTextureUvRegressionTest {
    private BankerTextureUvRegressionTest() {
    }

    public static void main(String[] args) throws Exception {
        Path root = args.length == 0
                ? Path.of("").toAbsolutePath()
                : Path.of(args[0]).toAbsolutePath();
        BufferedImage villager = read(root.resolve(
                "common/src/main/resources/assets/the_emerald_standard/textures/entity/villager/profession/banker.png"));
        BufferedImage zombie = read(root.resolve(
                "common/src/main/resources/assets/the_emerald_standard/textures/entity/zombie_villager/profession/banker.png"));

        validate("villager", villager, 656);
        validate("zombie villager", zombie, 647);
        validateTailoredFront("villager", villager);
        validateTailoredFront("zombie villager", zombie);
        check(!samePixels(villager, zombie), "Villager and zombie Banker textures must be distinct");
        System.out.println("PASS Banker profession textures stay inside the 26.2 jacket UV");
    }

    private static BufferedImage read(Path path) throws Exception {
        BufferedImage image = ImageIO.read(path.toFile());
        check(image != null, "Could not decode " + path);
        return image;
    }

    private static void validate(String name, BufferedImage image, int expectedOpaque) {
        check(image.getWidth() == 64 && image.getHeight() == 64,
                name + " texture must remain exactly 64x64");
        int opaque = 0;
        int frontOpaque = 0;
        Set<Integer> colors = new HashSet<>();
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int argb = image.getRGB(x, y);
                int alpha = argb >>> 24;
                check(alpha == 0 || alpha == 255,
                        name + " texture has semitransparent pixel at " + x + "," + y);
                if (alpha == 0) {
                    continue;
                }
                check(isJacketUv(x, y),
                        name + " texture leaks outside jacket UV at " + x + "," + y);
                opaque++;
                colors.add(argb);
                if (x >= 6 && x <= 13 && y >= 44 && y <= 63) {
                    frontOpaque++;
                }
            }
        }
        check(opaque == expectedOpaque,
                name + " texture opaque count changed: " + opaque + " != " + expectedOpaque);
        check(frontOpaque == 160, name + " jacket front must remain complete and readable");
        check(colors.size() >= 12, name + " texture regressed to flat coloring");
        check(colors.size() <= 16, name + " texture palette is no longer restrained pixel art");
    }

    private static void validateTailoredFront(String name, BufferedImage image) {
        Set<Integer> frontColors = new HashSet<>();
        int ivoryPixels = 0;
        int goldPixels = 0;
        for (int y = 44; y <= 63; y++) {
            for (int x = 6; x <= 13; x++) {
                int argb = image.getRGB(x, y);
                frontColors.add(argb);
                int red = argb >>> 16 & 0xFF;
                int green = argb >>> 8 & 0xFF;
                int blue = argb & 0xFF;
                if (red > 125 && green > 115 && blue > 90) {
                    ivoryPixels++;
                }
                if (red > 90 && green > 65 && blue < 100 && red > green) {
                    goldPixels++;
                }
            }
        }
        check(frontColors.size() >= 11, name + " front lacks tailored shading and trim");
        check(ivoryPixels >= 16, name + " front must retain a readable ivory shirt and lapels");
        check(goldPixels >= 8, name + " front must retain buttons, pin, and watch chain");
        for (int y : new int[] {50, 53, 56, 59}) {
            int argb = image.getRGB(9, y);
            int red = argb >>> 16 & 0xFF;
            int green = argb >>> 8 & 0xFF;
            int blue = argb & 0xFF;
            check(red > green && green > blue, name + " is missing a gold button at 9," + y);
        }
    }

    private static boolean isJacketUv(int x, int y) {
        return x >= 6 && x <= 13 && y >= 38 && y <= 43
                || x >= 14 && x <= 21 && y >= 38 && y <= 43
                || x >= 0 && x <= 5 && y >= 44 && y <= 63
                || x >= 6 && x <= 13 && y >= 44 && y <= 63
                || x >= 14 && x <= 19 && y >= 44 && y <= 63
                || x >= 20 && x <= 27 && y >= 44 && y <= 63;
    }

    private static boolean samePixels(BufferedImage first, BufferedImage second) {
        for (int y = 0; y < first.getHeight(); y++) {
            for (int x = 0; x < first.getWidth(); x++) {
                if (first.getRGB(x, y) != second.getRGB(x, y)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
