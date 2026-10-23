package com.solegendary.reignofnether.maps;

import com.solegendary.reignofnether.match.MatchPhase;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;

import javax.annotation.Nullable;

/**
 * A new match on a running server: the arena moves to fresh ground (issue #35). NOT BUILT YET: the stub the tests
 * are written against; it refuses nothing and moves nothing.
 */
public final class NewMatch {
    private NewMatch() { }

    /** What a new-match request came to. */
    public record Result(boolean ok, String message, int slot, @Nullable BlockPos origin) { }

    /** Null when a new match may start in {@code phase}, else why not. */
    @Nullable
    public static String refusal(MatchPhase phase, boolean force) {
        return null;
    }

    public static Result start(MinecraftServer server, @Nullable BlockPos hint, boolean force) {
        return new Result(false, "not built yet", -1, null);
    }
}
