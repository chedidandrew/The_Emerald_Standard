package com.chedidandrew.emeraldstandard.minecraft;

import net.minecraft.core.component.DataComponents;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.WrittenBookItem;
import net.minecraft.world.level.Level;

/** Same item id/content and lectern compatibility, with a roomy client reader when held. */
public final class HandbookReaderItem extends WrittenBookItem {
    // A client-installed callback keeps Minecraft client classes off dedicated servers.
    private static Runnable reader;

    public HandbookReaderItem() {
        super(new Item.Properties().setId(EmeraldHandbook.HANDBOOK_ITEM_KEY).stacksTo(1)
                .rarity(Rarity.UNCOMMON)
                .component(DataComponents.WRITTEN_BOOK_CONTENT, EmeraldHandbook.content()));
    }

    public static void registerReader(Runnable opener) {
        reader = java.util.Objects.requireNonNull(opener);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide()) {
            if (reader != null) reader.run();
        } else {
            player.awardStat(Stats.ITEM_USED.get(this));
        }
        // Do not send the vanilla open-book packet: it would overwrite the new reader.
        return InteractionResult.SUCCESS;
    }
}
