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

    /** Plain-words mismatch text, English form (what the lang key says). */
    public static String mismatchText(String have, String need) {
        return "You have " + PREFIX + clean(have) + "; this server needs " + clean(need)
                + ". Update with the installer or the test pack link.";
    }

    /**
     * The sentence shown on the join-failure screen. serverChannelVersion is the server's channel string from
     * Forge's mismatch data, or null when it is not known (registry mismatch); a value equal to ours cannot explain
     * the failure, so it also counts as unknown.
     */
    public static String joinFailureText(String mine, String serverChannelVersion) {
        if (serverChannelVersion == null || serverChannelVersion.isBlank()
                || matches(mine, parse(serverChannelVersion)))
            return "You have " + PREFIX + clean(mine) + "; this server runs a different build.";
        return mismatchText(mine, parse(serverChannelVersion));
    }

    public static Component mismatchMessage(String have, String need) {
        return Component.translatable("multiplayer.reignofnether.version_mismatch", clean(have), clean(need));
    }
}
