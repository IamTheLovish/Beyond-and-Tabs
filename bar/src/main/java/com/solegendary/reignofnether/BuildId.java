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

    /** Marks the build id inside the network protocol string; it ends at the next '+' (ids never contain one). */
    public static final String BUILD_TAG = "+build.";

    /**
     * The mod version with the build id appended, e.g. {@code 1.5.0-beta-4+build.2026.10.23-0443-911d903}; PacketHandler
     * then adds {@code +packets.<table hash>}. The id is for the words players see, never for the handshake decision.
     */
    public static String withBuild(String version, String buildId) {
        return version + BUILD_TAG + clean(buildId).replace('+', '-');
    }

    /** The build id inside a protocol string, or null when it carries none (a server from before build ids). */
    public static String buildOf(String protocol) {
        if (protocol == null) return null;
        int at = protocol.indexOf(BUILD_TAG);
        if (at < 0) return null;
        int from = at + BUILD_TAG.length();
        int to = protocol.indexOf('+', from);
        String id = (to < 0 ? protocol.substring(from) : protocol.substring(from, to)).trim();
        return id.isEmpty() ? null : id;
    }

    /** The protocol string without its build id: what the handshake actually compares. */
    public static String withoutBuild(String protocol) {
        if (protocol == null) return "";
        String t = protocol.trim();
        int at = t.indexOf(BUILD_TAG);
        if (at < 0) return t;
        int to = t.indexOf('+', at + BUILD_TAG.length());
        return t.substring(0, at) + (to < 0 ? "" : t.substring(to));
    }

    /**
     * The handshake rule: equal after dropping the build ids, so two commits with the same packet table connect (the
     * packet-table hash from security audit F3 decides) and only the message to a refused player names the builds.
     */
    public static boolean sameProtocol(String mine, String theirs) {
        if (mine == null || theirs == null) return false;
        return withoutBuild(mine).equals(withoutBuild(theirs));
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
     * Called by our channel's client-side version check with the server's protocol string. Forge's mismatch screen
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
     * What the join-failure screen says. mine is this jar's build id, myProtocol its handshake string, serverProtocol
     * the server's (null/blank when not known: registry mismatch, server without the mod). A server string the
     * handshake would accept cannot explain the failure, so it also counts as unknown. The server's need is its build
     * id, or its whole protocol string when it carries none.
     */
    public static Parts joinFailureParts(String mine, String myProtocol, String serverProtocol) {
        if (serverProtocol == null || serverProtocol.isBlank() || sameProtocol(myProtocol, serverProtocol))
            return new Parts(KEY_MISMATCH_UNKNOWN, clean(mine));
        String theirs = buildOf(serverProtocol);
        return new Parts(KEY_MISMATCH, clean(mine), theirs != null ? theirs : serverProtocol.trim());
    }

    public static Component toComponent(Parts p) {
        return Component.translatable(p.key(), p.args());
    }
}
