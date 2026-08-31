package com.chedidandrew.emeraldstandard.fabric;

import com.chedidandrew.emeraldstandard.core.*;
import com.mojang.brigadier.arguments.*;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.*;
import net.minecraft.world.level.storage.LevelResource;
import java.util.*;

public final class EmeraldStandardFabric implements ModInitializer {
    private static final EconomyService ECON = new EconomyService(); private static long ticks;
    @Override public void onInitialize(){
        ServerLifecycleEvents.SERVER_STARTED.register(server->{try{ticks=server.overworld().getGameTime(); ECON.start(server.getWorldPath(LevelResource.DATA), server.overworld().getSeed(), ticks);}catch(Exception e){throw new RuntimeException(e);}});
        ServerLifecycleEvents.SERVER_STOPPING.register(server->ECON.saveQuiet());
        ServerTickEvents.END_SERVER_TICK.register(server->{ticks=server.overworld().getGameTime(); ECON.tick(ticks);});
        CommandRegistrationCallback.EVENT.register((dispatcher,registry,env)-> EmeraldCommands.register(dispatcher, ECON));
    }
}
