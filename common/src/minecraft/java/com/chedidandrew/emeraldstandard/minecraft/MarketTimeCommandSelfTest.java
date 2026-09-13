package com.chedidandrew.emeraldstandard.minecraft;

import com.chedidandrew.emeraldstandard.core.*;
import com.chedidandrew.emeraldstandard.client.MarketDisplay;
import java.nio.file.*;
import java.util.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.network.Filterable;
import io.netty.buffer.Unpooled;

/** Opt-in isolated server smoke: exercise actual command dispatch, mixins and chart packet codec. */
public final class MarketTimeCommandSelfTest {
    public static void run(MinecraftServer server,EconomyService original)throws Exception{
        if(!Boolean.getBoolean("the_emerald_standard.integrationSmoke"))throw new IllegalStateException("Smoke only");
        var dispatcher=server.getCommands().getDispatcher();var source=server.createCommandSourceStack();
        long previous=server.overworld().getOverworldClockTime();
        Path root=Files.createTempDirectory("tes-native-market-clock-");
        MarketTimeRuntime.stop(server);
        try{
            dispatcher.execute("time set 0",source);
            var e=new EconomyService();e.configureEconomicClock(false,1);
            e.start(root,112,server.overworld().getGameTime(),server.overworld().getOverworldClockTime());
            MarketTimeRuntime.start(server,e);
            dispatcher.execute("time set 570000",source);
            while(e.catchUpDaysRemaining()>0)e.tick(server.overworld().getGameTime(),server.overworld().getOverworldClockTime());
            check(e.economicDay()==23&&e.snapshot().liveMarket.slot==60,"numeric set not captured");
            dispatcher.execute("time set 0",source);
            check(e.economicDay()==24&&e.snapshot().liveMarket.slot==0,"native backward set rewound or failed");
            var dawn=e.marketSnapshot().prices();
            dispatcher.execute("time set 0",source);
            check(e.economicDay()==24&&e.marketSnapshot().prices().equals(dawn),"repeated zero rerolled");
            dispatcher.execute("time set night",source);long night=server.overworld().getOverworldClockTime();
            check(e.snapshot().liveMarket.slot==(night%24000)/300,"night marker not captured");
            dispatcher.execute("time set day",source);
            long day=e.economicDay();long phase=e.snapshot().pendingEconomicMillis;
            var quote=e.marketSnapshot().prices();
            dispatcher.execute("time set day",source);
            check(e.economicDay()==day&&e.snapshot().pendingEconomicMillis==phase&&e.marketSnapshot().prices().equals(quote),"same marker minted a day");
            dispatcher.execute("time add 600",source);
            check(!quote.equals(e.marketSnapshot().prices()),"add command not captured");
            dispatcher.execute("time query gametime",source);
            long before=e.economicDay();
            dispatcher.execute("time add -24000",source);
            check(e.economicDay()>=before,"negative add rolled back");
            var display=e.marketDisplay("RSDN","VILX","RSDN",false);
            ItemStack book=new ItemStack(Items.WRITTEN_BOOK);
            book.set(DataComponents.WRITTEN_BOOK_CONTENT,new WrittenBookContent(Filterable.passThrough("TES:market:2"),"Exchange",0,
                display.pages().stream().map(Component::literal).map(c->Filterable.passThrough((Component)c)).toList(),true));
            var buffer=new RegistryFriendlyByteBuf(Unpooled.buffer(),server.registryAccess());
            try{
                ItemStack.STREAM_CODEC.encode(buffer,book);int size=buffer.readableBytes();
                var decoded=ItemStack.STREAM_CODEC.decode(buffer);
                var pages=decoded.get(DataComponents.WRITTEN_BOOK_CONTENT).getPages(false).stream().map(Component::getString).toList();
                check(MarketDisplay.parse(pages).equals(display),"native market packet changed history");
                System.out.println("PASS native live-market commands and 17-page chart packet ("+size+" bytes)");
            }finally{buffer.release();}
        }finally{
            MarketTimeRuntime.stop(server);
            dispatcher.execute("time set "+previous,source);
            MarketTimeRuntime.start(server,original);
            try(var paths=Files.walk(root)){for(var p:paths.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(p);}
        }
    }
    private static void check(boolean value,String message){if(!value)throw new IllegalStateException(message);}
    private MarketTimeCommandSelfTest(){}
}
