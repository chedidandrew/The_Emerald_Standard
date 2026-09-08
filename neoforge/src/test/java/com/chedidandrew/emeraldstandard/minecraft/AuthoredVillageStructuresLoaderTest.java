package com.chedidandrew.emeraldstandard.minecraft;

import org.junit.jupiter.api.Test;

/** Uses ModDevGradle's loader bootstrap before touching NeoForge-patched Minecraft classes. */
final class AuthoredVillageStructuresLoaderTest {
    @Test
    void everyAuthoredMasterPassesTheProductionAdmissionGate() throws ReflectiveOperationException {
        AuthoredVillageStructuresSelfTest.main(new String[0]);
    }
}
