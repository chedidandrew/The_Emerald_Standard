package com.chedidandrew.emeraldstandard.minecraft.mixin;

import com.chedidandrew.emeraldstandard.minecraft.DebugFlightRecorder;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.function.BooleanSupplier;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;

/** Whole tick work, including loader end callbacks; no sampling or file access while capture is off. */
@Mixin(MinecraftServer.class)
public abstract class DebugServerTickMixin {
    @WrapMethod(method = "tickServer")
    private void emerald$measureCompleteTick(BooleanSupplier hasTimeLeft, Operation<Void> original) {
        MinecraftServer server = (MinecraftServer) (Object) this;
        Object capture = DebugFlightRecorder.activeCapture(server);
        if (capture == null) {
            original.call(hasTimeLeft);
            return;
        }
        long started = System.nanoTime();
        boolean completed = false;
        try {
            original.call(hasTimeLeft);
            completed = true;
        } finally {
            DebugFlightRecorder.completedServerTick(server, capture, System.nanoTime() - started, completed);
        }
    }
}
