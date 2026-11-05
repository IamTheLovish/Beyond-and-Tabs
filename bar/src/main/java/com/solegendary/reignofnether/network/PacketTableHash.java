package com.solegendary.reignofnether.network;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;

/**
 * The network protocol version from the packet table itself (security audit F3, Oct 26). The channel version was the
 * mod version, a constant 1.5.0-beta-4, so any two builds passed Forge's handshake even when their packet tables
 * differed - and a packet then decoded as another, silently. The version is now the mod version plus a hash of what
 * is registered - index, class name and direction of every packet, in order - so two jars connect exactly when their
 * tables match: identical code from different commits still connects (no git id in it), a table that differs by one
 * packet is refused at the handshake. Pure (no Minecraft), so a JUnit test pins it.
 */
public final class PacketTableHash {
    private PacketTableHash() { }

    /** One registered packet as it enters the hash: {@code index:class:direction}. */
    public static String entry(int index, String className, String direction) {
        return index + ":" + className + ":" + direction;
    }

    /** The first 12 hex digits of SHA-256 over the entries, one per line, in registration order. */
    public static String hash(List<String> entries) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            for (String e : entries) {
                md.update(e.getBytes(StandardCharsets.UTF_8));
                md.update((byte) '\n');
            }
            byte[] d = md.digest();
            StringBuilder sb = new StringBuilder(12);
            for (int i = 0; i < 6; i++)
                sb.append(String.format("%02x", d[i] & 0xff));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is part of every Java runtime", e);
        }
    }

    /** What Forge compares at the handshake, e.g. {@code 1.5.0-beta-4+packets.3f9c01a2b4de}. */
    public static String protocolVersion(String modVersion, List<String> entries) {
        return modVersion + "+packets." + hash(entries);
    }
}
