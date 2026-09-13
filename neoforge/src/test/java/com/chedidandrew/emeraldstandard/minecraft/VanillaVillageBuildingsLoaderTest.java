package com.chedidandrew.emeraldstandard.minecraft;

import org.junit.jupiter.api.Test;

/** Uses the actual NeoForge environment; patched Minecraft cannot run in a plain JavaExec. */
final class VanillaVillageBuildingsLoaderTest {
    @Test
    void allDefaultBuildingsKeepTheirResolvedPlans() throws Exception {
        VanillaVillageBuildingsSelfTest.main(new String[0]);
    }
}
