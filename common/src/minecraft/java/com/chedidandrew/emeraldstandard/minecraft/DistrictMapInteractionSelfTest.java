package com.chedidandrew.emeraldstandard.minecraft;

import com.mojang.authlib.GameProfile;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.server.level.*;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.*;

/** Opt-in disposable server only. Native terrain colors and normal block-placement consumption. */
final class DistrictMapInteractionSelfTest {
    static void verify(ServerLevel level) {
        BlockPos desk = new BlockPos(976, level.getMaxY() - 24, 976);
        Map<BlockPos, BlockState> before = new LinkedHashMap<>();
        try {
            for (int y = -1; y <= 6; y++) for (int z = -1; z <= 1; z++) {
                BlockPos pos = desk.offset(0, y, z);
                level.getChunk(pos);
                before.put(pos, level.getBlockState(pos));
                level.setBlock(pos, y == -1 ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState(), 18);
            }
            level.setBlock(desk, BankerProfessionSupport.exchangeDeskOrLectern().defaultBlockState(), 18);
            var player = new ServerPlayer(level.getServer(), level, new GameProfile(UUID.randomUUID(), "DeskMapFixture"),
                    ClientInformation.createDefault());
            player.setPos(desk.getX() + 3, desk.getY(), desk.getZ());
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STONE, 3));
            require(!ExchangeDeskInteraction.placingBlock(player), "normal click with a block still opens banking");
            player.setShiftKeyDown(true);
            require(ExchangeDeskInteraction.placingBlock(player), "crouched main-hand block bypasses banking");
            var hit = new BlockHitResult(Vec3.atCenterOf(desk).add(0, .5, 0), Direction.UP, desk, false);
            var menuBefore = player.containerMenu;
            player.gameMode.useItemOn(player, level, player.getMainHandItem(), InteractionHand.MAIN_HAND, hit);
            require(level.getBlockState(desk.above()).is(Blocks.STONE) && player.getMainHandItem().getCount() == 2,
                    "native Survival placement consumes exactly one block");
            require(player.containerMenu == menuBefore, "loader interaction hook does not open a GUI when crouch placing");
            player.gameMode.useItemOn(player, level, player.getMainHandItem(), InteractionHand.MAIN_HAND, hit);
            require(player.getMainHandItem().getCount() == 2, "occupied placement never consumes a block");
            level.setBlock(desk.above(), Blocks.AIR.defaultBlockState(), 18);
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.STONE, 2));
            require(ExchangeDeskInteraction.placingBlock(player), "offhand block also bypasses main-hand GUI callback");
            player.gameMode.useItemOn(player, level, player.getOffhandItem(), InteractionHand.OFF_HAND, hit);
            require(level.getBlockState(desk.above()).is(Blocks.STONE) && player.getOffhandItem().getCount() == 1,
                    "native offhand placement consumes exactly one block");
            level.setBlock(desk.above(), Blocks.AIR.defaultBlockState(), 18);
            player.getAbilities().instabuild = true;
            player.gameMode.useItemOn(player, level, player.getOffhandItem(), InteractionHand.OFF_HAND, hit);
            require(player.getOffhandItem().getCount() == 1, "creative placement retains stack");
            player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
            require(!ExchangeDeskInteraction.placingBlock(player), "empty-handed crouch retains GUI");
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.EMERALD));
            require(!ExchangeDeskInteraction.placingBlock(player), "non-block items retain GUI");

            BlockPos surface = desk.above(3);
            var chunk = level.getChunkAt(surface); // Test setup explicitly loads its disposable fixture.
            for (var block : List.of(Blocks.GRASS_BLOCK, Blocks.OAK_LOG, Blocks.WATER, Blocks.STONE)) {
                level.setBlock(surface, block.defaultBlockState(), 18);
                int color = DistrictTerrainColors.sample(level, chunk, surface.getX(), surface.getZ());
                MapColor expected = block.defaultBlockState().getMapColor(level, surface);
                require(Arrays.stream(MapColor.Brightness.values()).anyMatch(shade -> expected.calculateARGBColor(shade) == color),
                        "surface samples use native map palette for " + block);
            }
            level.setBlock(surface.above(), Blocks.GLASS.defaultBlockState(), 18);
            int transparent = DistrictTerrainColors.sample(level, chunk, surface.getX(), surface.getZ());
            MapColor stone = Blocks.STONE.defaultBlockState().getMapColor(level, surface);
            require(Arrays.stream(MapColor.Brightness.values()).anyMatch(shade -> stone.calculateARGBColor(shade) == transparent),
                    "transparent surface reveals underlying map color within bounded scan");
        } finally { before.forEach((pos, state) -> level.setBlock(pos, state, 18)); }
        System.out.println("PASS district terrain native palette, transparency, desk crouch placement and stack safety");
    }
    private static void require(boolean value, String message) { if (!value) throw new IllegalStateException(message); }
    private DistrictMapInteractionSelfTest() {}
}
