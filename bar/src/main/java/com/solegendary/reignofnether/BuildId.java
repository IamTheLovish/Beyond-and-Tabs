package com.solegendary.reignofnether;

import net.minecraft.network.chat.Component;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * The build id of this jar (e.g. {@code 2026.10.23-0443-911d903}, written at build time to bat_build_id.txt) and the
 * words players see when it differs from the server's. Why: the jar version is the constant 1.5.0-beta-4, so a
 * version mismatch was invisible or an anonymous channel-list screen (user request, Oct 23).
 */
public final class BuildId {
    public static final String PREFIX = "Beyond and Tabs ";
    private static String cached;

    private BuildId() { }

    /** This jar's build id; "dev" when run from source without one. */
    public static synchronized String current() {
        if (cached == null) {
            String id = "dev";
            try (InputStream in = BuildId.class.getResourceAsStream("/bat_build_id.txt")) {
                if (in != null) id = clean(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            } catch (Exception ignored) {
                // keep "dev"
            }
            cached = id;
        }
        return cached;
    }

    /** Trims, and turns an empty or missing id into "dev". */
    public static String clean(String raw) {
        if (raw == null) return "dev";
        String t = raw.trim();
        return t.isEmpty() ? "dev" : t;
    }

    /** The network channel version: Forge matches it exactly and prints it, so it reads as words. */
    public static String channelVersion(String buildId) {
        return PREFIX + clean(buildId);
    }

    /** Back from a channel version to the bare build id (anything without our prefix comes back cleaned). */
    public static String parse(String channelVersion) {
        if (channelVersion == null) return "dev";
        String t = channelVersion.trim();
        return clean(t.startsWith(PREFIX) ? t.substring(PREFIX.length()) : t);
    }

    /** Same build exactly; ids are compared as written, no "newer" rule. */
    public static boolean matches(String mine, String theirs) {
        return clean(mine).equals(clean(theirs));
    }

    public static final String KEY_MISMATCH = "multiplayer.reignofnether.version_mismatch";
    public static final String KEY_MISMATCH_UNKNOWN = "multiplayer.reignofnether.version_mismatch_unknown";

    /** A translation key plus its arguments: the rule is pure, the client turns it into a Component. */
    public record Parts(String key, Object... args) { }

    private static volatile String lastServerChannelVersion;

    /**
     * Called by our channel's client-side version check with the server's channel string. Forge's mismatch screen
     * keeps only MOD versions (constant 1.5.0-beta-4 on every build), never the channel string, so this is the one
     * place the server's build id is visible. Recorded on every check, accepted or not, so the latest join attempt wins.
     */
    public static void noteServerChannelVersion(String serverChannelVersion) {
        lastServerChannelVersion = serverChannelVersion;
    }

    public static String lastServerChannelVersion() {
        return lastServerChannelVersion;
    }

    /**
     * What the join-failure screen says. serverChannelVersion is the server's channel string, or null/blank when not
     * known (registry mismatch, server without the mod); a value equal to ours cannot explain the failure, so it also
     * counts as unknown.
     */
    public static Parts joinFailureParts(String mine, String serverChannelVersion) {
        if (serverChannelVersion == null || serverChannelVersion.isBlank()
                || matches(mine, parse(serverChannelVersion)))
            return new Parts(KEY_MISMATCH_UNKNOWN, clean(mine));
        return new Parts(KEY_MISMATCH, clean(mine), parse(serverChannelVersion));
    }

    public static Component toComponent(Parts p) {
        return Component.translatable(p.key(), p.args());
    }
}
