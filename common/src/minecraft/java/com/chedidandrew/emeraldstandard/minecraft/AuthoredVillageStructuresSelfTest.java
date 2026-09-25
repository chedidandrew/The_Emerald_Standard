package com.chedidandrew.emeraldstandard.minecraft;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.IdentityHashMap;
import net.minecraft.SharedConstants;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Runs the exact authored-catalog admission gate without launching or mutating a world. */
public final class AuthoredVillageStructuresSelfTest {
    private AuthoredVillageStructuresSelfTest() {
    }

    public static void main(String[] args) throws Exception {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        registerExactDeskFixture();
        if (args.length == 1 && args[0].equals("sequence")) {
            SupportedConstructionOrderSelfTest.run(); return;
        }
        if (args.length == 1 && args[0].equals("smithy")) {
            AuthoredSmithyRoofSelfTest.run();
            return;
        }
        VillageBankVersionSevenSelfTest.run();
        SupportedConstructionOrderSelfTest.run();
        AuthoredRevisionCompatibilitySelfTest.run();
        AuthoredStairRefinementsSelfTest.run();
        AuthoredChimneyRefinementsSelfTest.run();
        AuthoredCeilingClearanceSelfTest.run();
        AuthoredSmithyRoofSelfTest.run();
        AuthoredPaletteContrastSelfTest.run();
        AuthoredLandscapeRefinementsSelfTest.run();
        AuthoredStructuralContactRefinementsSelfTest.run();
        AuthoredWorkshopContactRefinementsSelfTest.run();
        AuthoredLandscapeRefinementsSelfTest.reportCurrentGallery();
        AuthoredDoodadSurvivalSelfTest.run();
        AuthoredApproachRefinementsSelfTest.run();
        AuthoredMarketRefinementsSelfTest.run();
        GalleryAttachmentExpectationsSelfTest.run();
        long started = System.nanoTime();
        AuthoredVillageStructures.validateCatalog();
        AuthoredApproachRefinementsSelfTest.reportCanonicalCoverage();
        System.out.printf(
                "PASS authored village structure catalog self-test (52 masters, all dialects, "
                        + "palettes, dressing and characters; %.1f seconds)%n",
                (System.nanoTime() - started) / 1_000_000_000.0);
    }

    /**
     * Vanilla's standalone bootstrap freezes the block registry before a JavaExec test can run
     * loader registration. Open only that disposable test registry long enough to register the
     * actual production Desk factory, then freeze it again before testing any blueprint. This
     * keeps the exact role, geometry and lighting gates strict: a lectern proxy is not accepted.
     * No game entry point calls this method and the process never loads or writes a world.
     */
    private static void registerExactDeskFixture() throws ReflectiveOperationException {
        if (BankerProfessionSupport.isExchangeDesk(
                BankerProfessionSupport.exchangeDeskOrLectern().defaultBlockState())) {
            return;
        }
        Field frozen = MappedRegistry.class.getDeclaredField("frozen");
        Field intrusive = MappedRegistry.class.getDeclaredField("unregisteredIntrusiveHolders");
        Field allTags = MappedRegistry.class.getDeclaredField("allTags");
        Method unboundTags = allTags.getType().getDeclaredMethod("unbound");
        frozen.setAccessible(true);
        intrusive.setAccessible(true);
        allTags.setAccessible(true);
        unboundTags.setAccessible(true);
        Registry<Block> registry = BuiltInRegistries.BLOCK;
        if (!frozen.getBoolean(registry)) {
            throw new IllegalStateException("Headless block bootstrap did not reach its frozen state");
        }
        frozen.setBoolean(registry, false);
        intrusive.set(registry, new IdentityHashMap<>());
        try {
            Block desk = Registry.register(registry, BankerProfessionSupport.EXCHANGE_DESK_ID,
                    BankerProfessionSupport.createExchangeDeskBlock());
            for (BlockState state : desk.getStateDefinition().getPossibleStates()) {
                Block.BLOCK_STATE_REGISTRY.add(state);
                state.initCache();
            }
        } finally {
            // freeze() rebuilds the same tag view from its retained frozenTags map; clear only
            // the already-bound view, not its definitions or any holder membership.
            allTags.set(registry, unboundTags.invoke(null));
            registry.freeze();
        }
        if (!frozen.getBoolean(registry)
                || !BankerProfessionSupport.isExchangeDesk(
                        BankerProfessionSupport.exchangeDeskOrLectern().defaultBlockState())) {
            throw new IllegalStateException("Headless exact Exchange Desk fixture failed to register");
        }
    }
}
