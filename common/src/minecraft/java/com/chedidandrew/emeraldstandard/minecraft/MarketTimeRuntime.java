package com.chedidandrew.emeraldstandard.minecraft;

import com.chedidandrew.emeraldstandard.core.EconomyService;
import net.minecraft.server.MinecraftServer;

/** Explicit command bridge, independent of player news and Creative/Survival mode. */
public final class MarketTimeRuntime {
    private static MinecraftServer server;
    private static EconomyService economy;
    public static void start(MinecraftServer s,EconomyService e){server=s;economy=e;}
    public static void stop(MinecraftServer s){if(server==s){server=null;economy=null;}}
    public static void changed(MinecraftServer s,long before){changed(s,before,false);}
    public static void changed(MinecraftServer s,long before,boolean marker){
        if(server==s&&economy!=null&&!(marker
                ?economy.timeMarkerCommand(s.overworld().getGameTime(),before,s.overworld().getOverworldClockTime())
                :economy.timeCommand(s.overworld().getGameTime(),before,s.overworld().getOverworldClockTime())))
            org.slf4j.LoggerFactory.getLogger("The Emerald Standard").error("Market time command: {}",economy.lastError());
    }
    private MarketTimeRuntime(){}
}
