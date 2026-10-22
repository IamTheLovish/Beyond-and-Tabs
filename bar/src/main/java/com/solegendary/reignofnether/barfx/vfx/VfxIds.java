package com.solegendary.reignofnether.barfx.vfx;

import java.nio.charset.StandardCharsets;

/**
 * Effect ids on the wire. The definitions are client assets (a dedicated server never reads them), so the server
 * names an effect by a stable 24-bit hash of its id: it rides float-exact in a BarFx event field (like the dome id),
 * and the client maps it back through the definitions it loaded. VfxDefTest proves the shipped ids do not collide.
 * Pure Java.
 */
public final class VfxIds {
    private VfxIds() { }

    public static final int MASK = (1 << 24) - 1;

    /**
     * The effects shipped in assets/reignofnether/vfx (not the projectile visuals): what /bat vfx suggests. The
     * server cannot list client assets, so this list is kept by hand - VfxDefTest fails when it and the folder differ.
     */
    public static final java.util.List<String> SHIPPED = java.util.List.of("judgement_pillar", "soul_bolt_impact", "sun_pillar");

    /** FNV-1a over the UTF-8 bytes, xor-folded to 24 bits; never 0 (0 = "no effect"). */
    public static int hash(String id) {
        if (id == null) return 0;
        int h = 0x811C9DC5;
        for (byte b : id.getBytes(StandardCharsets.UTF_8)) {
            h ^= b & 0xFF;
            h *= 0x01000193;
        }
        int folded = ((h >>> 24) ^ h) & MASK;
        return folded == 0 ? 1 : folded;
    }

    /** A valid effect id: lower-case letters, digits, '_' and '/', 1-64 characters (the asset file's name). */
    public static boolean valid(String id) {
        if (id == null || id.isEmpty() || id.length() > 64) return false;
        for (int i = 0; i < id.length(); i++) {
            char c = id.charAt(i);
            if (!(c >= 'a' && c <= 'z' || c >= '0' && c <= '9' || c == '_' || c == '/')) return false;
        }
        return true;
    }
}
