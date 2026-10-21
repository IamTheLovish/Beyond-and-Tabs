package com.solegendary.reignofnether.matchstart;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.function.IntPredicate;
import java.util.function.IntSupplier;

/**
 * The port Host Online opens the world on. It was a random free port every session, so a fixed tunnel (playit.gg and
 * the like) pointed at the old one and friends had to get a new address each time (Lane C playtest, Oct 20). Now
 * the configured port (client config host_port, 25565 by default) is used when it is free, and a random free port
 * only when it isn't (or host_port is 0). Kept free of client classes so HostPortGameTests can check it headless.
 */
public final class HostPort {

    private HostPort() {}

    public static int choose(int preferred, IntPredicate isFree, IntSupplier anyFree) {
        if (preferred > 0 && preferred <= 65535 && isFree.test(preferred))
            return preferred;
        return anyFree.getAsInt();
    }

    /** Can a server socket bind this port right now? */
    public static boolean isFree(int port) {
        try (ServerSocket s = new ServerSocket(port)) {
            s.setReuseAddress(true);
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}
