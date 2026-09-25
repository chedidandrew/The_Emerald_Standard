package com.chedidandrew.emeraldstandard.minecraft.mixin;

import com.chedidandrew.emeraldstandard.minecraft.MarketTimeRuntime;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.commands.TimeCommand;
import net.minecraft.world.clock.ClockTimeMarker;
import net.minecraft.world.clock.WorldClock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(TimeCommand.class)
public abstract class MarketTimeCommandMixin {
    // Commands execute serially on the server thread; these three vanilla methods do not nest.
    @Unique private static long emerald$before;
    @Inject(method={"setTotalTicks","addTime"},at=@At("HEAD"))
    private static void emerald$beforeTicks(CommandSourceStack source,Holder<WorldClock> clock,int ticks,CallbackInfoReturnable<Integer> ci){
        emerald$before=source.getServer().overworld().getOverworldClockTime();
    }
    @Inject(method={"setTotalTicks","addTime"},at=@At("RETURN"))
    private static void emerald$afterTicks(CommandSourceStack source,Holder<WorldClock> clock,int ticks,CallbackInfoReturnable<Integer> ci){
        MarketTimeRuntime.changed(source.getServer(),emerald$before);
    }
    @Inject(method="setTimeToTimeMarker",at=@At("HEAD"))
    private static void emerald$beforeMarker(CommandSourceStack source,Holder<WorldClock> clock,ResourceKey<ClockTimeMarker> marker,CallbackInfoReturnable<Integer> ci){
        emerald$before=source.getServer().overworld().getOverworldClockTime();
    }
    @Inject(method="setTimeToTimeMarker",at=@At("RETURN"))
    private static void emerald$afterMarker(CommandSourceStack source,Holder<WorldClock> clock,ResourceKey<ClockTimeMarker> marker,CallbackInfoReturnable<Integer> ci){
        MarketTimeRuntime.changed(source.getServer(),emerald$before,true);
    }
}
