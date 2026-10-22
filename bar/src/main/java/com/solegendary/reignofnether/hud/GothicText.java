package com.solegendary.reignofnether.hud;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.config.ReignOfNetherClientConfigs;
import net.minecraft.Util;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Gothic, blood-dripping lettering for the title screen buttons and the lobby screens (design/lobby-backdrop.md).
 * lovish (Oct 21): "customize the text ... to be gothic and bold, standing out" with "blood dripping down to highlight
 * them". Two OFL fonts ship in assets/reignofnether/font/ (licences beside them): UnifrakturCook Bold (blackletter, the
 * titles and the title screen's buttons) and a static Black instance of Cinzel (engraved capitals, the lobby buttons and
 * any label too wide for blackletter).
 *
 * The lettering is a blood-red fill (lighter at the top, deep crimson below), a thin gold rim and a dark shadow; strands
 * of blood grow from the bottom of a few letters, swell, let a drop fall and start again, each on its own timing.
 * `splash_reduce_motion` leaves short, still drips. Everything is drawn with GuiGraphics text and fills - no textures.
 */
public final class GothicText {

    public static final ResourceLocation TITLE_FONT = ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "lobby_title");
    public static final ResourceLocation BUTTON_FONT = ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "lobby_button");
    public static final ResourceLocation CAPS_FONT = ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "lobby_caps");
    // glyph heights in GUI units (the providers' sizes)
    private static final int TITLE_H = 16, BUTTON_H = 12, CAPS_H = 10;

    private static final int RIM = 0xFFB8892F, RIM_HOT = 0xFFF0CC72;
    private static final int BLOOD_DEEP = 0xFF7E0812, BLOOD = 0xFFB8141F, BLOOD_LIGHT = 0xFFE8505A;
    private static final int STRAND = 0xFF8A0A14, STRAND_HI = 0xFFD8444E;

    /** the lobby screens' main buttons (restyled); the rest (faction, team, colour, x) keep the vanilla look */
    public static final Set<String> LOBBY_MAIN = Set.of("Start Battle", "Watch Battle", "Starting...", "Host Online",
        "Skirmish", "Load Game", "Spectate", "Back", "Ready", "Not Ready", "Play", "Close Lobby", "Hide", "Open to LAN",
        "Add AI", "Join");
    /** the buttons whose hover lets a drop of blood fall */
    public static final Set<String> LOBBY_PRIMARY = Set.of("Start Battle", "Watch Battle", "Ready", "Host Online");

    /** the letter centres for the drips, per text and font (computed once per label) */
    private static final Map<String, float[]> DRIP_X = new HashMap<>();

    private GothicText() {
    }

    public static Component styled(String text, ResourceLocation font) {
        return Component.literal(text).withStyle(s -> s.withFont(font));
    }

    private static boolean reduceMotion() {
        try {
            return ReignOfNetherClientConfigs.SPLASH_REDUCE_MOTION.get();
        } catch (Throwable e) {
            return false;
        }
    }

    private static float seconds() {
        return reduceMotion() ? -1f : (Util.getMillis() % 3_600_000L) / 1000f;
    }

    // ---------------------------------------------------------------- titles

    /**
     * A lobby heading in blackletter: a softly pulsing red glow behind it, the blood lettering and drips. (x, y) is the
     * top-left of the glyphs; returns the width drawn.
     */
    public static int title(GuiGraphics gg, Font font, String text, int x, int y) {
        Component c = styled(text, TITLE_FONT);
        int w = font.width(c);
        float t = seconds();
        float pulse = t < 0 ? 0.5f : 0.5f + 0.5f * Mth.sin(t * 1.3f);
        // the glow: nested translucent fills, the outer ones fainter
        for (int i = 4; i >= 1; i--) {
            int a = (int) ((10 + 8 * pulse) * (5 - i) / 4f);
            gg.fill(x - 4 * i, y - 2 * i + 2, x + w + 4 * i, y + TITLE_H + 2 * i, (a << 24) | 0x900810);
        }
        blood(gg, font, c, x, y, TITLE_H, 2, 0xFF000000 | 0x9A7428, 1f);
        drips(gg, font, text, TITLE_FONT, c, x, y + TITLE_H - 1, 3, 9, 2, t, false, 0xFF);
        return w;
    }

    /** The blood lettering: shadow, a gold rim in eight directions, the fill in three bands (light top to deep below). */
    private static void blood(GuiGraphics gg, Font font, Component c, int x, int y, int h, int shadow, int rim, float alpha) {
        int a = Mth.clamp((int) (alpha * 255f), 0, 255);
        int w = font.width(c);
        gg.drawString(font, c, x + 1, y + shadow, withAlpha(0xC0000000, a * 0.75f), false);
        for (int dx = -1; dx <= 1; dx++)
            for (int dy = -1; dy <= 1; dy++)
                if (dx != 0 || dy != 0)
                    gg.drawString(font, c, x + dx, y + dy, withAlpha(rim, a), false);
        gg.drawString(font, c, x, y, withAlpha(BLOOD_DEEP, a), false);
        gg.enableScissor(x - 2, y - 4, x + w + 2, y + Math.round(h * 0.62f));
        gg.drawString(font, c, x, y, withAlpha(BLOOD, a), false);
        gg.disableScissor();
        gg.enableScissor(x - 2, y - 4, x + w + 2, y + Math.round(h * 0.30f));
        gg.drawString(font, c, x, y, withAlpha(BLOOD_LIGHT, a), false);
        gg.disableScissor();
    }

    private static int withAlpha(int argb, float a) {
        int base = (argb >>> 24) & 0xFF;
        int out = Mth.clamp((int) (base * a / 255f), 0, 255);
        return (out << 24) | (argb & 0xFFFFFF);
    }

    /**
     * Blood strands hanging from the bottom of a few letters. Each strand grows (0-55 % of its cycle), swells into a
     * bead (55-70 %), lets the drop fall (70-100 %) and starts again; its cycle (3.5-7 s) and phase come from a hash
     * of the text, so the drips never march in step. t < 0 (reduce motion) leaves short, still strands.
     */
    private static void drips(GuiGraphics gg, Font font, String text, ResourceLocation fontId, Component c, int x, int baseY,
                              int strands, int maxLen, int width, float t, boolean forceDrop, int alpha) {
        float[] xs = dripXs(font, text, fontId, strands);
        int seed = text.hashCode();
        for (int i = 0; i < xs.length; i++) {
            int hsh = hash(seed + i * 7919);
            float period = 3.5f + 3.5f * (hsh % 1000) / 1000f;
            float phase = (hash(hsh) % 1000) / 1000f;
            float len = maxLen * (0.55f + 0.45f * ((hash(hsh + 3) % 100) / 100f));
            int sx = x + Math.round(xs[i]);
            if (t < 0f) {
                strand(gg, sx, baseY, Math.round(len * 0.45f), width, alpha);
                continue;
            }
            float p = (t / (forceDrop ? period * 0.35f : period) + phase) % 1f;
            if (p < 0.55f) {
                float k = p / 0.55f;
                strand(gg, sx, baseY, Math.round(len * (0.2f + 0.8f * k * k * (3 - 2 * k))), width, alpha);
            } else if (p < 0.70f) {
                strand(gg, sx, baseY, Math.round(len), width, alpha);
                int bead = (p - 0.55f) / 0.15f > 0.5f ? 1 : 0;
                gg.fill(sx - bead, baseY + Math.round(len) - 1, sx + width + bead, baseY + Math.round(len) + 1 + bead,
                    withAlpha(STRAND, alpha));
            } else {
                float k = (p - 0.70f) / 0.30f;
                strand(gg, sx, baseY, Math.round(len * (0.25f + 0.1f * (1 - k))), width, alpha);
                int dy = Math.round(len + k * k * len * 3.2f);
                int da = Math.round(alpha * (1f - k));
                gg.fill(sx, baseY + dy, sx + width, baseY + dy + 2 + width / 2, withAlpha(STRAND, da));
                gg.fill(sx, baseY + dy, sx + 1, baseY + dy + 1, withAlpha(STRAND_HI, da));
            }
        }
    }

    private static void strand(GuiGraphics gg, int x, int y, int len, int width, int alpha) {
        if (len <= 0)
            return;
        gg.fill(x, y, x + width, y + len, withAlpha(STRAND, alpha));
        gg.fill(x, y, x + 1, y + Math.max(1, len - 1), withAlpha(STRAND_HI, alpha * 0.6f));
        // a little bead at the tip
        gg.fill(x - (width > 1 ? 1 : 0), y + len - 1, x + width + (width > 1 ? 1 : 0), y + len + 1, withAlpha(STRAND, alpha));
    }

    private static float[] dripXs(Font font, String text, ResourceLocation fontId, int strands) {
        String key = fontId.getPath() + "|" + strands + "|" + text;
        float[] xs = DRIP_X.get(key);
        if (xs != null)
            return xs;
        int n = Math.min(strands, Math.max(1, text.replace(" ", "").length() / 3));
        xs = new float[n];
        int seed = text.hashCode();
        for (int i = 0; i < n; i++) {
            // spread over the text: one strand per segment, on a letter (never a space)
            int len = text.length();
            int from = len * i / n, to = Math.max(from + 1, len * (i + 1) / n);
            int j = from + hash(seed + i * 31) % (to - from);
            while (j < len - 1 && text.charAt(j) == ' ')
                j++;
            float left = font.width(styled(text.substring(0, j), fontId));
            float cw = font.width(styled(text.substring(j, j + 1), fontId));
            xs[i] = left + cw * 0.5f;
        }
        if (DRIP_X.size() > 256)
            DRIP_X.clear();
        DRIP_X.put(key, xs);
        return xs;
    }

    private static int hash(int x) {
        x *= 0x9E3779B1;
        x ^= x >>> 15;
        x *= 0x85EBCA77;
        x ^= x >>> 13;
        return x & 0x7FFFFFFF;
    }

    // ---------------------------------------------------------------- buttons

    /**
     * Repaints text buttons as dark stone with a gold border and draws their label in the gothic blood lettering
     * (after the buttons have rendered, so clicks, narration and tooltips stay vanilla; the stone is opaque, so the
     * vanilla label never shows through). `only` limits it to those labels (null = every text button at least 40 wide);
     * `primary` labels (null = all) let a drop fall quickly while hovered. Blackletter when `blackletter` and it fits, Cinzel capitals otherwise.
     */
    public static void restyleButtons(GuiGraphics g, Font font, List<? extends GuiEventListener> children, Set<String> only,
                                      Set<String> primary, boolean blackletter, float alpha) {
        int a = Mth.clamp(Mth.ceil(alpha * 255f), 0, 255);
        if (a <= 3)
            return;
        float t = seconds();
        for (int i = 0; i < children.size(); i++) {
            if (!(children.get(i) instanceof Button b) || !b.visible || b.getWidth() < 40)
                continue;
            String label = b.getMessage().getString();
            if (only != null && !only.contains(label))
                continue;
            int x0 = b.getX(), y0 = b.getY(), bw = b.getWidth(), bh = b.getHeight();
            boolean hot = b.active && b.isHoveredOrFocused();
            g.fill(x0, y0, x0 + bw, y0 + bh, (a << 24) | (hot ? 0x2A1A18 : 0x16110F));
            g.fill(x0 + 1, y0 + 1, x0 + bw - 1, y0 + bh / 2, withAlpha(0x4D4A3D33, a));   // the lit upper face
            int gold = !b.active ? 0x5C4A2C : hot ? 0xF0CC72 : 0xB08A3E;
            g.renderOutline(x0, y0, bw, bh, (a << 24) | gold);
            if (hot)
                g.renderOutline(x0 + 1, y0 + 1, bw - 2, bh - 2, withAlpha(0x73F0CC72, a));

            ResourceLocation fid = BUTTON_FONT;
            int fh = BUTTON_H;
            Component c = styled(label, fid);
            if (!blackletter || font.width(c) > bw - 8) {
                fid = CAPS_FONT;
                fh = CAPS_H;
                c = styled(label.toUpperCase(java.util.Locale.ROOT), fid);
                if (font.width(c) > bw - 6) {       // a narrow button: the vanilla face, still in blood and gold
                    fid = Style.DEFAULT_FONT;
                    fh = 8;
                    c = Component.literal(label);
                }
            }
            int tw = font.width(c);
            int tx = x0 + (bw - tw) / 2, ty = y0 + (bh - fh) / 2;
            if (!b.active) {
                g.drawString(font, c, tx, ty, (a << 24) | 0x7A6C58, false);
                continue;
            }
            blood(g, font, c, tx, ty, fh, 1, hot ? RIM_HOT : RIM, a / 255f);
            String key = c.getString();
            drips(g, font, key, fid, c, tx, ty + fh - 1, 2, 4, 1, t, hot && (primary == null || primary.contains(label)), a);
        }
    }
}
