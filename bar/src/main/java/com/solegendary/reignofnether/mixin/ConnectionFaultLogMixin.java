package com.solegendary.reignofnether.mixin;

import com.solegendary.reignofnether.network.ConnectionFaultLog;
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
// at ERROR with its stack trace (both sides, every connection) so the next drop names the code that threw. Only the
// first fault of a connection carries the trace; a dying channel repeats it thousands of times (ConnectionFaultLog).
@Mixin(Connection.class)
public class ConnectionFaultLogMixin {

    @Unique
    private static final Logger reignofnether$LOG = LogManager.getLogger("BAT/Connection");

    @Unique
    private int reignofnether$faults;   // one connection's faults all arrive on its own Netty event loop

    @Inject(method = "exceptionCaught", at = @At("HEAD"), remap = false)
    private void reignofnether$logFault(ChannelHandlerContext ctx, Throwable t, CallbackInfo ci) {
        if (t instanceof net.minecraft.network.SkipPacketException)
            return; // one packet skipped, the connection lives on - vanilla's DEBUG line is enough
        int n = ++reignofnether$faults;
        switch (ConnectionFaultLog.entryFor(n)) {
            case FULL -> reignofnether$LOG.error("[Connection] fault on {} - the connection will drop",
                Thread.currentThread().getName(), t);
            case REPEATS_FOLLOW -> reignofnether$LOG.error("[Connection] fault again on {} ({}) - further faults on this "
                + "connection are counted, one line per {}", Thread.currentThread().getName(), t.toString(),
                ConnectionFaultLog.COUNT_EVERY);
            case COUNT -> reignofnether$LOG.error("[Connection] {} faults on {} so far (last: {})", n,
                Thread.currentThread().getName(), t.toString());
            case NONE -> { }
        }
    }
}
