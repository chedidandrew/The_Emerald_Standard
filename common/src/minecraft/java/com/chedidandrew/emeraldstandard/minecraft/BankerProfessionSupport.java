package com.chedidandrew.emeraldstandard.minecraft;

import com.google.common.collect.ImmutableSet;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.trading.TradeSet;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

/** Shared identifiers and factories for the cross-loader Banker and Exchange Desk. */
public final class BankerProfessionSupport {
    public static final String MOD_ID = "the_emerald_standard";
    public static final Identifier EXCHANGE_DESK_ID =
            Identifier.fromNamespaceAndPath(MOD_ID, "exchange_desk");
    public static final Identifier BANKER_POI_ID =
            Identifier.fromNamespaceAndPath(MOD_ID, "banker_poi");
    public static final Identifier BANKER_ID =
            Identifier.fromNamespaceAndPath(MOD_ID, "banker");
    public static final ResourceKey<Block> EXCHANGE_DESK_BLOCK_KEY =
            ResourceKey.create(Registries.BLOCK, EXCHANGE_DESK_ID);
    public static final ResourceKey<Item> EXCHANGE_DESK_ITEM_KEY =
            ResourceKey.create(Registries.ITEM, EXCHANGE_DESK_ID);
    public static final ResourceKey<PoiType> BANKER_POI_KEY =
            ResourceKey.create(Registries.POINT_OF_INTEREST_TYPE, BANKER_POI_ID);
    public static final ResourceKey<VillagerProfession> BANKER_KEY =
            ResourceKey.create(Registries.VILLAGER_PROFESSION, BANKER_ID);

    private BankerProfessionSupport() {
    }

    public static Block createExchangeDeskBlock() {
        return new ExchangeDeskBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.LECTERN)
                .setId(EXCHANGE_DESK_BLOCK_KEY));
    }

    public static Item.Properties createExchangeDeskItemProperties() {
        return new Item.Properties().setId(EXCHANGE_DESK_ITEM_KEY);
    }

    public static PoiType createBankerPoi(Block exchangeDesk) {
        return new PoiType(
                ImmutableSet.copyOf(exchangeDesk.getStateDefinition().getPossibleStates()),
                1,
                1);
    }

    public static VillagerProfession createBankerProfession() {
        return new VillagerProfession(
                Component.translatable("entity.the_emerald_standard.banker"),
                holder -> holder.is(BANKER_POI_KEY),
                holder -> holder.is(BANKER_POI_KEY),
                ImmutableSet.<Item>of(),
                ImmutableSet.<Block>of(),
                SoundEvents.VILLAGER_WORK_LIBRARIAN,
                new Int2ObjectOpenHashMap<ResourceKey<TradeSet>>());
    }

    public static Optional<Holder.Reference<VillagerProfession>> registeredBanker() {
        return BuiltInRegistries.VILLAGER_PROFESSION.get(BANKER_KEY);
    }

    public static boolean isRegisteredBanker(Holder<VillagerProfession> profession) {
        return profession != null && profession.is(BANKER_KEY);
    }

    /** Returns the custom desk after loader registration and a lectern during safe fallback. */
    public static Block exchangeDeskOrLectern() {
        Block block = BuiltInRegistries.BLOCK.getValue(EXCHANGE_DESK_ID);
        return block == null || block == Blocks.AIR ? Blocks.LECTERN : block;
    }

    /** Legacy lecterns remain valid bank counters after the custom workstation migration. */
    public static boolean isBankWorkstation(BlockState state) {
        return state != null && (state.is(Blocks.LECTERN) || isExchangeDesk(state));
    }

    public static boolean isExchangeDesk(BlockState state) {
        if (state == null) {
            return false;
        }
        Block block = BuiltInRegistries.BLOCK.getValue(EXCHANGE_DESK_ID);
        return block != null && block != Blocks.AIR && state.is(block);
    }
}
