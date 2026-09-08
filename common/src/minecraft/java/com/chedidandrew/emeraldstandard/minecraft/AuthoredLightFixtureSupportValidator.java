package com.chedidandrew.emeraldstandard.minecraft;

import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SupportType;
import net.minecraft.world.level.block.TorchBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Fail-closed admission for authored light-fixture attachment graphs.
 *
 * <p>This validator is deliberately read-only. It proves that every authored fixture will survive
 * ordinary neighbor updates at the exact visual stage being admitted; runtime integrity policy is
 * then free to treat a player-removed fixture or support as damage without silently rebuilding it.
 */
final class AuthoredLightFixtureSupportValidator {
    private AuthoredLightFixtureSupportValidator() {
    }

    static void validate(Map<BlockPos, BlockState> authored, String snapshotId) {
        for (Map.Entry<BlockPos, BlockState> entry : authored.entrySet()) {
            BlockState state = entry.getValue();
            if (state.getBlock() instanceof LanternBlock) {
                if (state.getValue(LanternBlock.HANGING)) {
                    validateHangingLantern(authored, entry.getKey(), snapshotId);
                } else {
                    requireCenterBearingSupport(
                            authored,
                            entry.getKey().below(),
                            Direction.UP,
                            "standing lantern",
                            snapshotId);
                }
                continue;
            }
            if (state.getBlock() instanceof WallTorchBlock) {
                Direction facing = state.getValue(WallTorchBlock.FACING);
                BlockPos supportPosition = entry.getKey().relative(facing.getOpposite());
                BlockState support = authored.get(supportPosition);
                if (support == null
                        || !support.isFaceSturdy(
                                EmptyBlockGetter.INSTANCE, supportPosition, facing)) {
                    throw unsupported("wall torch", supportPosition, snapshotId);
                }
                continue;
            }
            if (state.getBlock() instanceof TorchBlock) {
                requireCenterBearingSupport(
                        authored,
                        entry.getKey().below(),
                        Direction.UP,
                        "standing torch",
                        snapshotId);
            }
        }
    }

    private static void validateHangingLantern(
            Map<BlockPos, BlockState> authored, BlockPos lantern, String snapshotId) {
        BlockPos supportPosition = lantern.above();
        BlockState support = authored.get(supportPosition);
        while (support != null && support.is(Blocks.IRON_CHAIN)) {
            if (!support.hasProperty(RotatedPillarBlock.AXIS)
                    || support.getValue(RotatedPillarBlock.AXIS) != Direction.Axis.Y) {
                throw unsupported("hanging lantern vertical chain", supportPosition, snapshotId);
            }
            supportPosition = supportPosition.above();
            support = authored.get(supportPosition);
        }
        if (support == null
                || support.is(BlockTags.UNSTABLE_BOTTOM_CENTER)
                || !support.isFaceSturdy(
                        EmptyBlockGetter.INSTANCE,
                        supportPosition,
                        Direction.DOWN,
                        SupportType.CENTER)) {
            throw unsupported("hanging lantern chain terminal", supportPosition, snapshotId);
        }
    }

    private static void requireCenterBearingSupport(
            Map<BlockPos, BlockState> authored,
            BlockPos supportPosition,
            Direction supportFace,
            String fixture,
            String snapshotId) {
        BlockState support = authored.get(supportPosition);
        if (support == null
                || !support.isFaceSturdy(
                        EmptyBlockGetter.INSTANCE,
                        supportPosition,
                        supportFace,
                        SupportType.CENTER)) {
            throw unsupported(fixture, supportPosition, snapshotId);
        }
    }

    private static IllegalStateException unsupported(
            String fixture, BlockPos supportPosition, String snapshotId) {
        return new IllegalStateException(
                "Authored "
                        + fixture
                        + " has no complete sturdy attachment at "
                        + supportPosition
                        + " in "
                        + snapshotId);
    }
}
