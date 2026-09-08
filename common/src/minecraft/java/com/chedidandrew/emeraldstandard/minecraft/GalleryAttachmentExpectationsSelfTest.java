package com.chedidandrew.emeraldstandard.minecraft;

import com.chedidandrew.emeraldstandard.core.StructureGalleryPlan;
import java.util.HashSet;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;

/** Regression for auditing a finished gallery without replaying superseded construction layers. */
final class GalleryAttachmentExpectationsSelfTest {
    private GalleryAttachmentExpectationsSelfTest() {
    }

    static void run() {
        String previous = System.getProperty(StructureGallery.ENABLE_PROPERTY);
        try {
            System.setProperty(StructureGallery.ENABLE_PROPERTY, "true");
            verifyFinalLayerCollapse();
            verifyRuntimeRejection();
            long signature = StructureGalleryPlan.layoutSignature();
            var entries = List.copyOf(StructureGalleryPlan.entries());
            int attachments = 0;
            for (var entry : entries) {
                BlockPos origin = new BlockPos(entry.originX(), -59, entry.originZ());
                // No ServerLevel exists: this must remain a pure production-plan expectation query.
                var expected = VillageProsperityManager.galleryProjectAttachmentExpectations(origin, entry);
                var occupied = new HashSet<BlockPos>();
                for (var block : expected) {
                    require(StructureGallery.requiresAttachmentAudit(block.state()), "non-fragile audit cell");
                    require(occupied.add(block.position()), "duplicate final attachment position");
                }
                attachments += expected.size();
                if (entry.templateId().equals("house_cross_01") && entry.originX() == 240
                        && entry.originZ() == 0) {
                    require(expected.stream().noneMatch(b -> b.position().equals(origin.offset(6, 0, 13))),
                            "superseded base support must not enter the completed-world attachment audit");
                    require(expected.equals(VillageProsperityManager.galleryProjectAttachmentExpectations(origin, entry)),
                            "expectation query is not deterministic");
                }
            }
            require(attachments > 0, "empty attachment expectation catalog");
            require(signature == StructureGalleryPlan.layoutSignature()
                            && entries.equals(StructureGalleryPlan.entries()),
                    "expectation query changed frozen gallery descriptors");
            System.clearProperty(StructureGallery.ENABLE_PROPERTY);
            expectFailure(() -> VillageProsperityManager.galleryProjectAttachmentExpectations(
                    BlockPos.ZERO, entries.getFirst()), "missing gallery opt-in");
            System.out.printf("PASS completed-gallery attachment expectations (%d authored attachments; "
                    + "final-layer collapse, frozen descriptors, missing/wrong/unsupported rejection)%n", attachments);
        } finally {
            if (previous == null) {
                System.clearProperty(StructureGallery.ENABLE_PROPERTY);
            } else {
                System.setProperty(StructureGallery.ENABLE_PROPERTY, previous);
            }
        }
    }

    private static void verifyFinalLayerCollapse() {
        BlockPos support = new BlockPos(6, 0, 13);
        BlockPos removedPlant = new BlockPos(1, 1, 1);
        BlockPos retainedRail = new BlockPos(2, 1, 2);
        BlockPos newPlant = new BlockPos(3, 1, 3);
        var rail = new StructureGalleryBlock(retainedRail, Blocks.RAIL.defaultBlockState());
        var plant = new StructureGalleryBlock(newPlant, Blocks.FLOWERING_AZALEA.defaultBlockState());
        var finalCells = VillageProsperityManager.finalGalleryAttachmentExpectations(List.of(
                new StructureGalleryBlock(support, Blocks.STONE_BRICKS.defaultBlockState()),
                new StructureGalleryBlock(removedPlant, Blocks.AZALEA.defaultBlockState()),
                rail,
                new StructureGalleryBlock(support, Blocks.DIRT_PATH.defaultBlockState()),
                new StructureGalleryBlock(removedPlant, Blocks.AIR.defaultBlockState()),
                rail,
                plant));
        require(finalCells.equals(List.of(rail, plant)),
                "filter must follow final-layer collapse, retaining each final attachment exactly once");
    }

    private static void verifyRuntimeRejection() {
        for (var block : List.of(Blocks.AZALEA, Blocks.FLOWERING_AZALEA, Blocks.RAIL)) {
            var expected = new StructureGalleryBlock(BlockPos.ZERO, block.defaultBlockState());
            StructureGallery.validateAttachmentState(0, expected, expected.state(), true);
            expectFailure(() -> StructureGallery.validateAttachmentState(
                    0, expected, Blocks.AIR.defaultBlockState(), true), "missing authored attachment");
            expectFailure(() -> StructureGallery.validateAttachmentState(
                    0, expected, Blocks.STONE.defaultBlockState(), true), "wrong authored attachment");
            expectFailure(() -> StructureGallery.validateAttachmentState(
                    0, expected, expected.state(), false), "unsupported authored attachment");
        }
    }

    private static void expectFailure(Runnable action, String label) {
        try {
            action.run();
        } catch (IllegalStateException expected) {
            return;
        }
        throw new IllegalStateException("Gallery attachment self-test accepted " + label);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException("Gallery attachment self-test: " + message);
        }
    }
}
