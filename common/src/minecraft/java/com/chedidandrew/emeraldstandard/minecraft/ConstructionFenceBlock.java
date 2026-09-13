package com.chedidandrew.emeraldstandard.minecraft;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** A real caution barrier. A saved receipt owns only this block, never subsequent player edits. */
public final class ConstructionFenceBlock extends FenceBlock implements EntityBlock {
    public static final MapCodec<FenceBlock> CODEC = simpleCodec(ConstructionFenceBlock::new);
    public ConstructionFenceBlock(Properties properties) { super(properties); }
    @Override public MapCodec<FenceBlock> codec() { return CODEC; }
    @Override public boolean connectsTo(BlockState state, boolean solid, Direction direction) {
        return state.getBlock() instanceof ConstructionFenceBlock;
    }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new Receipt(pos, state); }
    @Override protected java.util.List<net.minecraft.world.item.ItemStack> getDrops(BlockState state,
            net.minecraft.world.level.storage.loot.LootParams.Builder params) {
        var entity=params.getOptionalParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.BLOCK_ENTITY);
        // Only manual, unowned fences are recoverable. Automatic barriers must never be farmed.
        if (!(entity instanceof Receipt receipt) || !receipt.job().isEmpty()) return java.util.List.of();
        return super.getDrops(state,params);
    }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide() || type != ConstructionContent.receipt) return null;
        return (world, pos, current, entity) -> ((Receipt) entity).tick((ServerLevel) world);
    }
    public static final class Receipt extends BlockEntity {
        private String job = "";
        private BlockState before = Blocks.AIR.defaultBlockState();
        private BlockState beforeAbove = Blocks.AIR.defaultBlockState();
        public Receipt(BlockPos pos, BlockState state) { super(ConstructionContent.receipt, pos, state); }
        void own(String job, BlockState before) { own(job, before, Blocks.AIR.defaultBlockState()); }
        void own(String job, BlockState before, BlockState above) {
            this.job = job; this.before = before; this.beforeAbove = above; setChanged();
        }
        String job() { return job; }
        void tick(ServerLevel world) {
            if (job.isEmpty() || Math.floorMod(world.getGameTime() + worldPosition.asLong(), 80) != 0) return;
            Boolean active = ConstructionSitePresentation.active(world, job);
            if (Boolean.FALSE.equals(active)) restore(world);
        }
        void restore(ServerLevel world) {
            // A receipt exists only while the exact fence block entity still owns the cell.
            if (world.getBlockEntity(worldPosition) != this || !world.getBlockState(worldPosition).is(ConstructionContent.fence)) return;
            if (ConstructionSitePresentation.tallPlant(before)) {
                // Restore the pair atomically with respect to shape updates, never overwrite a
                // player's new upper block or leave an orphaned lower half from an old receipt.
                if (ConstructionSitePresentation.matchingUpper(before, beforeAbove)
                        && world.getBlockState(worldPosition.above()).isAir()
                        && before.canSurvive(world, worldPosition)) {
                    world.setBlock(worldPosition, before, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                    world.setBlock(worldPosition.above(), beforeAbove, 3);
                    world.updateNeighborsAt(worldPosition, before.getBlock());
                } else world.setBlock(worldPosition, Blocks.AIR.defaultBlockState(), 3);
            } else {
                BlockState replacement = before.canSurvive(world, worldPosition) ? before : Blocks.AIR.defaultBlockState();
                world.setBlock(worldPosition, replacement, 3);
            }
        }
        @Override protected void saveAdditional(ValueOutput output) {
            super.saveAdditional(output); output.putString("Worksite", job); output.store("Replaced", BlockState.CODEC, before);
            output.store("ReplacedAbove", BlockState.CODEC, beforeAbove);
        }
        @Override protected void loadAdditional(ValueInput input) {
            super.loadAdditional(input); job = input.getStringOr("Worksite", "");
            before = input.read("Replaced", BlockState.CODEC).filter(ConstructionSitePresentation::replaceablePlant)
                    .orElse(Blocks.AIR.defaultBlockState());
            beforeAbove = input.read("ReplacedAbove", BlockState.CODEC).orElse(Blocks.AIR.defaultBlockState());
        }
    }
}
