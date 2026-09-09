package com.chedidandrew.emeraldstandard.minecraft;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;

/**
 * Executes the exact production admission gate for every current Village Bank dialect without
 * launching a client or mutating a world.
 */
public final class VillageBankStructureSelfTest {
    private VillageBankStructureSelfTest() {
    }

    public static void main(String[] args) throws Exception {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        VillageBankVersionSevenSelfTest.run();
        VillageBankManager.validateBankV6MaterialRefinement();
        VillageBankManager.validateHeadlessBankDialectStructureContract();
        System.out.println("PASS Village Bank v7 structure self-test (all five dialects)");
    }
}
