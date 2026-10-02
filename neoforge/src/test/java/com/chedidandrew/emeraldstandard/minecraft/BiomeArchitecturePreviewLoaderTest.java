package com.chedidandrew.emeraldstandard.minecraft;

import org.junit.jupiter.api.Test;

/** Patched Minecraft classes require the actual ModDevGradle loader bootstrap. */
final class BiomeArchitecturePreviewLoaderTest {
    @Test
    void isolatedPrototypesPreserveProductionCatalog() throws Exception {
        BiomeArchitecturePreviewSelfTest.main(new String[0]);
    }
}
