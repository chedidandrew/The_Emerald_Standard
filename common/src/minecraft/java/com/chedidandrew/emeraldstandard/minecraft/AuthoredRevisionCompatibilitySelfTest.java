package com.chedidandrew.emeraldstandard.minecraft;

import com.chedidandrew.emeraldstandard.core.VillageArchitecture;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/** Golden placement-stream fingerprint from the published revision-three source checkpoint. */
final class AuthoredRevisionCompatibilitySelfTest {
    private AuthoredRevisionCompatibilitySelfTest() { }

    static void run() throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        for (var descriptor : VillageArchitecture.activeBlueprints()) {
            for (var dialect : VillageArchitecture.BiomeDialect.values()) {
                var plan = AuthoredVillageStructures.plan(descriptor.type(), descriptor.templateId(),
                        3, VillageArchitecture.PALETTE_BALANCED,
                        VillageArchitecture.DRESSING_PROSPEROUS,
                        VillageArchitecture.Character.RUSTIC, dialect);
                digest.update((descriptor.templateId() + "/" + dialect + "\n")
                        .getBytes(StandardCharsets.UTF_8));
                for (var stage : java.util.List.of(plan.base(), plan.stageOne(), plan.stageTwo())) {
                    for (var cell : stage) {
                        digest.update((cell.toString() + "\n").getBytes(StandardCharsets.UTF_8));
                    }
                    digest.update((byte) 0);
                }
            }
        }
        String actual = HexFormat.of().formatHex(digest.digest());
        String expected = "22b5cab0b0b815cfbdada2f25c5b63507b163eb92523679e3c76b1986b06ce08";
        if (!expected.equals(actual)) {
            throw new IllegalStateException("Frozen revision-three placements changed: " + actual);
        }
        System.out.println("PASS frozen revision-three placements (52 masters x five dialects)");
    }
}
