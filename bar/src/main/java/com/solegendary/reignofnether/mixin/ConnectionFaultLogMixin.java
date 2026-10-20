package com.solegendary.reignofnether.mixin;

import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.Connection;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Vanilla logs the throwable that kills a connection only at DEBUG ("Failed to sent packet"), which the shipped log
// configuration drops - the Oct 19 multiplayer drop left nothing but "Internal Exception: ...CME" on screen. Log it
// at ERROR with its stack trace (both sides, every connection) so the next drop names the code that threw.
@Mixin(Connection.class)
public class ConnectionFaultLogMixin {

    @Unique
    private static final Logger reignofnether$LOG = LogManager.getLogger("BAT/Connection");

    @Inject(method = "exceptionCaught", at = @At("HEAD"), remap = false)
    private void reignofnether$logFault(ChannelHandlerContext ctx, Throwable t, CallbackInfo ci) {
        if (t instanceof net.minecraft.network.SkipPacketException)
            return; // one packet skipped, the connection lives on - vanilla's DEBUG line is enough
        reignofnether$LOG.error("[Connection] fault on {} - the connection will drop", Thread.currentThread().getName(), t);
    }
}
