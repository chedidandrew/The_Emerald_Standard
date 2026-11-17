package com.chedidandrew.emeraldstandard.client;

import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.Set;
import javax.imageio.ImageIO;

/** Locks the icon-matching suit/glasses to the native villager and zombie-villager model UVs. */
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

        validate("villager", villager, 1670, false);
        validate("zombie villager", zombie, 1533, true);
        validateTailoredFront("villager", villager);
        validateTailoredFront("zombie villager", zombie);
        check(!samePixels(villager, zombie), "Villager and zombie Banker textures must be distinct");
        for(String kind:new String[]{"villager","zombie_villager"}) {
            String metadata=Files.readString(root.resolve("common/src/main/resources/assets/the_emerald_standard/textures/entity/"+kind+"/profession/banker.png.mcmeta"));
            check(metadata.replaceAll("\\s+", "").equals("{\"villager\":{\"hat\":\"full\"}}"),"Biome hats must not obscure icon-matching sunglasses");
        }
        System.out.println("PASS icon-matching Banker suit, white shirt/cuffs, emerald tie and sunglasses; native face and zombie UVs retained");
    }

    private static BufferedImage read(Path path) throws Exception {
        BufferedImage image = ImageIO.read(path.toFile());
        check(image != null, "Could not decode " + path);
        return image;
    }

    private static void validate(String name, BufferedImage image, int expectedOpaque, boolean zombie) {
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
                check(isJacketUv(x, y)||isSuitUv(x,y,zombie)||isGlassesUv(x,y),
                        name + " texture leaks outside suit/glasses UV at " + x + "," + y);
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
        for(int x=40;x<48;x++) for(int y=13;y<=14;y++) check(image.getRGB(x,y)>>>24==255,"Missing sunglasses lens/bridge");
        check(image.getRGB(43,15)>>>24==0&&image.getRGB(44,15)>>>24==0,"Glasses cover the nose bridge");
        check(image.getRGB(48,26)>>>24==255&&image.getRGB(48,zombie?34:32)>>>24==255,"Sleeve/cuff missing");
        check(image.getRGB(8,14)>>>24==0&&image.getRGB(26,3)>>>24==0,"Native face/nose repainted");
        if(zombie)check(image.getRGB(48,36)>>>24==0,"Zombie's native hand skin repainted");
    }

    private static void validateTailoredFront(String name, BufferedImage image) {
        Set<Integer> frontColors = new HashSet<>();
        int shirtPixels = 0;
        int tiePixels = 0;
        int suitPixels = 0;
        for (int y = 44; y <= 63; y++) {
            for (int x = 6; x <= 13; x++) {
                int argb = image.getRGB(x, y);
                frontColors.add(argb);
                int red = argb >>> 16 & 0xFF;
                int green = argb >>> 8 & 0xFF;
                int blue = argb & 0xFF;
                if (red > 140 && green > 150 && blue > 140) {
                    shirtPixels++;
                }
                if (green > red*1.5 && green > blue*1.2) {
                    tiePixels++;
                }
                if(red<100&&green<120&&blue<130&&Math.abs(red-green)<30) suitPixels++;
            }
        }
        check(frontColors.size() >= 10, name + " front lacks tailored shading");
        check(shirtPixels >= 6, name + " front lacks the icon's white collar/shirt");
        check(tiePixels >= 20, name + " front lacks the icon's emerald tie");
        check(suitPixels >= 120, name + " jacket must be charcoal, not a green/gold robe");
        for (int y : new int[] {46, 49, 51, 53}) {
            int argb = image.getRGB(9, y);
            int red = argb >>> 16 & 0xFF;
            int green = argb >>> 8 & 0xFF;
            int blue = argb & 0xFF;
            check(green > red*1.5&&green > blue*1.2, name + " is missing the emerald tie at 9," + y);
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
    private static boolean isGlassesUv(int x,int y) {
        return y==13&&x>=32&&x<56||y==14&&x>=40&&x<48
                ||y==15&&(x>=40&&x<43||x>=45&&x<48);
    }
    private static boolean isSuitUv(int x,int y,boolean zombie) {
        return x>=22&&x<38&&y>=20&&y<26||x>=16&&x<44&&y>=26&&y<38
                ||x>=4&&x<12&&y>=22&&y<26||x>=0&&x<16&&y>=26&&y<38
                ||x>=48&&x<56&&y>=22&&y<26||x>=44&&x<60&&y>=26&&y<(zombie?36:34)
                ||!zombie&&(y>=38&&y<42&&x>=44&&x<60||y>=42&&y<46&&x>=40&&x<64);
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
