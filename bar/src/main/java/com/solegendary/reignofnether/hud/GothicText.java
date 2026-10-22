package com.solegendary.reignofnether.hud;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.config.ReignOfNetherClientConfigs;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.Util;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.network.chat.Component;
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
    private static final int TITLE_H = 16;

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
        // Cinzel capitals, not blackletter: at heading size the blackletter "k" read as "t" (lovish: "Stirmish")
        text = text.toUpperCase(java.util.Locale.ROOT);
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

    /** the big engraved capitals for every restyled button label (Cinzel Black, provider size 25, see BTN_* below) */
    public static final ResourceLocation PLATE_FONT = ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "lobby_plate");
    /** carved dark stone, 4 px per GUI unit, sampled per button at a hashed offset (tools/gen_lobby_art.py plate()) */
    private static final ResourceLocation PLATE_TEX = ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "textures/gui/lobby/plate.png");
    private static final int PLATE_TW = 1024, PLATE_TH = 96, PLATE_PX = 4;
    // Cinzel's metrics at provider size 25 (stbtt scales ascent - descent = 976 + 372 units to the size): the capitals
    // are 700 units tall = 13 GUI, their tops sit 276 units below the ascent line = 5.1 GUI below the drawString y
    private static final float BTN_SIZE = 25f, BTN_CAP = BTN_SIZE * 700f / 1348f, BTN_CAP_TOP = BTN_SIZE * 276f / 1348f;
    private static final int IVORY = 0xFFF8EED2, IVORY_GOLD = 0xFFEFD08A, GOLD = 0xFFD2A04A, GOLD_DEEP = 0xFFA87428;
    private static final int OUTLINE = 0xFF3A0309, OUTLINE_HOT = 0xFF6A0612;
    private static final int PLATE_BLOOD = 0xFFA8101C, PLATE_BLOOD_DARK = 0xFF5A0610, PLATE_BLOOD_HI = 0xFFF06070;

    /**
     * Repaints text buttons as carved dark stone plates (a bevel: light top edge, dark bottom; a thin gold inlay; a
     * crimson glow when hovered) with blood seeping from the plate's bottom edge in 2-4 strands that grow, bead and let a
     * drop fall (longer and quicker while hovered, a burst while pressed), and the label in big Cinzel Black capitals:
     * ivory to gold, a dark crimson outline and a drop shadow, ~65 % of the button's height (scaled down only when a
     * label would not fit). Drawn after the buttons have rendered, so clicks, narration and tooltips stay vanilla; the
     * plate is opaque, so the vanilla label never shows through. Positions and hit areas are untouched.
     * `only` limits it to those labels (null = every text button at least 40 wide); `primary` labels (null = all) let
     * the drops fall quickly while hovered. `blackletter` is kept for callers: blackletter now only draws the headings.
     */
    public static void restyleButtons(GuiGraphics g, Font font, List<? extends GuiEventListener> children, Set<String> only,
                                      Set<String> primary, boolean blackletter, float alpha) {
        int a = Mth.clamp(Mth.ceil(alpha * 255f), 0, 255);
        if (a <= 3)
            return;
        float t = seconds();
        boolean pressed = false;
        try {
            pressed = net.minecraft.client.Minecraft.getInstance().mouseHandler.isLeftPressed();
        } catch (Throwable ignored) {
        }
        // three passes, so a hovered plate's glow sits behind its neighbours and the blood hangs over the plate below
        for (int pass = 0; pass < 3; pass++) {
            for (int i = 0; i < children.size(); i++) {
                if (!(children.get(i) instanceof Button b) || !b.visible || b.getWidth() < 40)
                    continue;
                String label = b.getMessage().getString();
                if (only != null && !only.contains(label))
                    continue;
                boolean hot = b.active && b.isHoveredOrFocused();
                if (pass == 0) {
                    if (hot)
                        glow(g, b.getX(), b.getY(), b.getWidth(), b.getHeight(), t, a);
                } else if (pass == 1) {
                    plate(g, b.getX(), b.getY(), b.getWidth(), b.getHeight(), label.hashCode(), b.active, hot, a);
                } else {
                    boolean fast = hot && (primary == null || primary.contains(label));
                    if (b.active)
                        plateDrips(g, b.getX(), b.getY() + b.getHeight(), b.getWidth(), label, t, hot, fast, hot && pressed, a);
                    label(g, font, label, b.getX(), b.getY(), b.getWidth(), b.getHeight(), b.active, hot, a);
                }
            }
        }
    }

    /** the crimson glow round a hovered plate, breathing slowly */
    private static void glow(GuiGraphics g, int x0, int y0, int bw, int bh, float t, int a) {
        float pulse = t < 0 ? 0.6f : 0.6f + 0.4f * Mth.sin(t * 3.1f);
        for (int i = 5; i >= 1; i--) {
            int al = (int) ((22 + 26 * pulse) * (6 - i) / 5f * a / 255f);
            g.fill(x0 - i - 1, y0 - i, x0 + bw + i + 1, y0 + bh + i, (al << 24) | 0xB00A18);
        }
    }

    /** the carved stone plate: stone texture, a bevel, a sunken gold inlay, a crimson wash when hovered */
    private static void plate(GuiGraphics g, int x0, int y0, int bw, int bh, int seed, boolean active, boolean hot, int a) {
        int x1 = x0 + bw, y1 = y0 + bh;
        RenderSystem.enableBlend();
        float lit = !active ? 0.55f : hot ? 1.18f : 1f;
        RenderSystem.setShaderColor(lit, hot ? lit * 0.93f : lit, hot ? lit * 0.92f : lit, a / 255f);
        int uw = Math.min(PLATE_TW, bw * PLATE_PX), vh = Math.min(PLATE_TH, bh * PLATE_PX);
        int u = (hash(seed) % Math.max(1, (PLATE_TW - uw) / PLATE_PX)) * PLATE_PX;
        g.blit(PLATE_TEX, x0, y0, bw, bh, u, (PLATE_TH - vh) / 2f, uw, vh, PLATE_TW, PLATE_TH);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        // the bevel: a black outer rim, a lit top edge and left edge, a dark bottom and right
        g.fill(x0, y0, x1, y0 + 1, withAlpha(0xFF050304, a));
        g.fill(x0, y1 - 1, x1, y1, withAlpha(0xFF050304, a));
        g.fill(x0, y0, x0 + 1, y1, withAlpha(0xFF050304, a));
        g.fill(x1 - 1, y0, x1, y1, withAlpha(0xFF050304, a));
        g.fill(x0 + 1, y0 + 1, x1 - 1, y0 + 2, withAlpha(hot ? 0xE0C8B49C : 0xC0A89886, a));
        g.fill(x0 + 1, y0 + 2, x1 - 1, y0 + 3, withAlpha(0x50A09080, a));
        g.fill(x0 + 1, y0 + 2, x0 + 2, y1 - 2, withAlpha(0x806E625A, a));
        g.fill(x0 + 1, y1 - 3, x1 - 1, y1 - 1, withAlpha(0xD0080506, a));
        g.fill(x1 - 2, y0 + 2, x1 - 1, y1 - 1, withAlpha(0xA0080506, a));
        // the sunken field inside the inlay: shadow under its top lip, light on its bottom lip
        g.fill(x0 + 4, y0 + 4, x1 - 4, y0 + 5, withAlpha(0x90000000, a));
        g.fill(x0 + 4, y0 + 5, x0 + 5, y1 - 5, withAlpha(0x60000000, a));
        g.fill(x0 + 4, y1 - 5, x1 - 4, y1 - 4, withAlpha(0x58C0A890, a));
        if (hot)
            g.fill(x0 + 4, y0 + 4, x1 - 4, y1 - 4, withAlpha(0x50B01020, a));
        // the gold inlay, sunk into the stone: a dark groove under a thin gold line, studs at the corners
        int gold = !active ? 0xFF5C4A2C : hot ? 0xFFF0CC72 : 0xFFB89040;
        g.renderOutline(x0 + 3, y0 + 4, bw - 6, bh - 7, withAlpha(0xC0000000, a));
        g.renderOutline(x0 + 3, y0 + 3, bw - 6, bh - 7, withAlpha(gold, a));
        if (active) {
            int stud = hot ? 0xFFFFE6A0 : 0xFFD8B060;
            int[][] at = {{x0 + 2, y0 + 2}, {x1 - 5, y0 + 2}, {x0 + 2, y1 - 6}, {x1 - 5, y1 - 6}};
            for (int[] p : at) {
                g.fill(p[0], p[1], p[0] + 3, p[1] + 3, withAlpha(stud, a));
                g.fill(p[0] + 1, p[1] + 1, p[0] + 2, p[1] + 2, withAlpha(0xFF5A3A14, a));
            }
        }
    }

    /** the label: big engraved capitals, ivory at the top to gold below, a dark crimson outline and a drop shadow */
    private static void label(GuiGraphics g, Font font, String label, int x0, int y0, int bw, int bh, boolean active,
                              boolean hot, int a) {
        Component c = styled(label.toUpperCase(java.util.Locale.ROOT), PLATE_FONT);
        float w = font.width(c);
        float s = Math.min(1f, (bw - 14f) / Math.max(1f, w));
        s = Math.min(s, (bh * 0.68f) / BTN_CAP);
        if (BTN_CAP * s < 6.5f) {               // a very narrow button: the vanilla face, in ivory with a crimson edge
            Component v = Component.literal(label);
            int vx = x0 + (bw - font.width(v)) / 2, vy = y0 + (bh - 8) / 2;
            g.drawString(font, v, vx + 1, vy + 1, withAlpha(OUTLINE, a), false);
            g.drawString(font, v, vx, vy, withAlpha(active ? IVORY : 0xFF8A7C68, a), false);
            return;
        }
        float capH = BTN_CAP * s;
        float tx = x0 + (bw - w * s) / 2f;
        float ty = y0 + (bh - capH) / 2f - 0.5f - BTN_CAP_TOP * s;
        if (!active) {
            drawAt(g, font, c, tx + 0.5f, ty + 0.75f, s, withAlpha(0xD0000000, a));
            drawAt(g, font, c, tx, ty, s, withAlpha(0xFF6E6152, a));
            return;
        }
        // the drop shadow, then the outline in eight directions (one GUI unit, whatever the scale)
        drawAt(g, font, c, tx + 1.2f, ty + 1.8f, s, withAlpha(0xC0000000, a));
        int ol = hot ? OUTLINE_HOT : OUTLINE;
        for (int dx = -1; dx <= 1; dx++)
            for (int dy = -1; dy <= 1; dy++)
                if (dx != 0 || dy != 0)
                    drawAt(g, font, c, tx + dx * 0.75f, ty + dy * 0.75f, s, withAlpha(ol, a));
        // the fill in four bands, ivory at the top to deep gold at the foot of the letters
        float top = ty + BTN_CAP_TOP * s;
        int[] bands = hot ? new int[]{0xFFE8B850, 0xFFF6D890, 0xFFFFF2D8, 0xFFFFFAEE}
                          : new int[]{GOLD_DEEP, GOLD, IVORY_GOLD, IVORY};
        float[] cut = {2f, 0.80f, 0.55f, 0.30f};
        for (int k = 0; k < 4; k++) {
            if (k > 0)
                g.enableScissor(x0, y0 - 2, x0 + bw, Math.round(top + capH * cut[k]));
            drawAt(g, font, c, tx, ty, s, withAlpha(bands[k], a));
            if (k > 0)
                g.disableScissor();
        }
    }

    private static void drawAt(GuiGraphics g, Font font, Component c, float x, float y, float s, int argb) {
        var pose = g.pose();
        pose.pushPose();
        pose.translate(x, y, 0f);
        pose.scale(s, s, 1f);
        g.drawString(font, c, 0, 0, argb, false);
        pose.popPose();
    }

    /**
     * Blood seeping from the plate's bottom edge: 2-4 strands (by width) at hashed places, each from a smear along the
     * lip, growing (0-55 % of its cycle), swelling into a bead (55-70 %) and letting the drop fall (70-100 %), on its
     * own 3-6 s cycle. Hovered: the strands hang longer and the cycle runs 3x faster; pressed: every strand lets a drop
     * go at once. Reduce motion: still strands of middling length. Mirrored by tools/gen_lobby_art.py _plate_drips.
     */
    private static void plateDrips(GuiGraphics g, int x0, int y, int bw, String label, float t, boolean hot, boolean fast,
                                   boolean burst, int a) {
        int n = bw >= 150 ? 4 : bw >= 90 ? 3 : 2;
        int seed = label.hashCode() * 31 + 7;
        float maxLen = hot ? 12f : 7f;
        for (int i = 0; i < n; i++) {
            int h = hash(seed + i * 7919);
            float fx = (i + 0.25f + 0.5f * ((h >>> 3) % 1000) / 1000f) / n;
            int sx = x0 + 6 + Math.round(fx * (bw - 12));
            int wdt = (h % 3 == 0) ? 2 : 1;
            float len = maxLen * (0.55f + 0.45f * ((hash(h + 3) % 100) / 100f));
            // the seep: a smear of blood along the lip above every strand
            g.fill(sx - 2 - h % 5, y - 1, sx + wdt + 2 + (h >>> 5) % 5, y, withAlpha(PLATE_BLOOD, a));
            if (t < 0f) {
                bloodStrand(g, sx, y, Math.round(len * 0.7f), wdt, a);
                continue;
            }
            float period = (3f + 3f * (h % 1000) / 1000f) / (fast ? 3f : 1f);
            float p = (t / period + (hash(h) % 1000) / 1000f) % 1f;
            if (burst)
                p = Math.max(p, 0.70f + 0.30f * ((t * 2.5f + i * 0.21f) % 1f));
            if (p < 0.55f) {
                float k = p / 0.55f;
                bloodStrand(g, sx, y, Math.round(len * (0.35f + 0.65f * k * k * (3 - 2 * k))), wdt, a);
            } else if (p < 0.70f) {
                int L = Math.round(len);
                bloodStrand(g, sx, y, L, wdt, a);
                if ((p - 0.55f) / 0.15f > 0.5f)       // the bead swells
                    g.fill(sx - 1, y + L + 2, sx + 2, y + L + 3, withAlpha(PLATE_BLOOD, a));
            } else {
                float k = (p - 0.70f) / 0.30f;
                bloodStrand(g, sx, y, Math.round(len * (0.35f + 0.15f * (1 - k))), wdt, a);
                int dy = Math.round(len + 4 + k * k * 22f);
                int da = Math.round(a * (1f - k * k));
                g.fill(sx, y + dy - 1, sx + 1, y + dy, withAlpha(PLATE_BLOOD, da));
                g.fill(sx - 1, y + dy, sx + 2, y + dy + 2, withAlpha(PLATE_BLOOD, da));
                g.fill(sx - 1, y + dy, sx, y + dy + 1, withAlpha(PLATE_BLOOD_HI, da));
            }
        }
    }

    /** one strand: a wide root, a crimson body (shaded on its right when 2 wide) and a rounded, lit bead at the tip */
    private static void bloodStrand(GuiGraphics g, int x, int y, int len, int wdt, int a) {
        if (len <= 0)
            return;
        g.fill(x - 1, y, x + wdt + 1, y + 2, withAlpha(PLATE_BLOOD, a));
        g.fill(x, y, x + wdt, y + len, withAlpha(PLATE_BLOOD, a));
        if (wdt > 1)
            g.fill(x + wdt - 1, y + 2, x + wdt, y + len, withAlpha(PLATE_BLOOD_DARK, a));
        g.fill(x - 1, y + len - 1, x + 2, y + len + 2, withAlpha(PLATE_BLOOD, a));
        g.fill(x - 1, y + len - 1, x, y + len, withAlpha(PLATE_BLOOD_HI, a));
        g.fill(x + 1, y + len + 1, x + 2, y + len + 2, withAlpha(PLATE_BLOOD_DARK, a));
        g.fill(x, y + len + 2, x + 1, y + len + 3, withAlpha(PLATE_BLOOD_DARK, a));
    }
}
