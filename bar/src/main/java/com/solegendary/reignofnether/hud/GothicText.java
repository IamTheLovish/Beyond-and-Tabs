package com.solegendary.reignofnether.hud;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.config.ReignOfNetherClientConfigs;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.Util;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    /**
     * Repaints every text button (Button and CycleButton; not the icon buttons) as carved stone with a gold frame (a
     * bevel: light top edge, dark bottom; a thin gold inlay; a crimson glow when hovered) and the label in the plate
     * capitals: ivory to gold, a dark crimson outline and a drop shadow. No blood drips (tester, Oct 26: they hung over
     * the buttons below). The label follows ButtonLabelFit: one size per button height on the screen, shrunk only when
     * it would not fit, centred both ways and clipped to the frame's padding, so it can never overflow. Drawn after the
     * buttons have rendered, so clicks, narration and tooltips stay vanilla; the plate is opaque, so the vanilla label
     * never shows through. Positions and hit areas are untouched.
     */
    public static void restyleButtons(GuiGraphics g, Font font, List<? extends GuiEventListener> children, float alpha) {
        int a = Mth.clamp(Mth.ceil(alpha * 255f), 0, 255);
        if (a <= 3)
            return;
        // The first real capture (eyes run 39741755064) showed the vanilla labels over our stone plates: vanilla text
        // sits a hair in front (Font draws it at z +0.03) and wins the depth test against fills at z 0. So: flush what
        // the buttons queued, then draw the plates and our labels a step nearer the viewer.
        g.flush();
        g.pose().pushPose();
        g.pose().translate(0f, 0f, 20f);
        try {
            restyle(g, font, children, a);
        } finally {
            g.flush();
            g.pose().popPose();
        }
    }

    /** The buttons that get the theme: visible text buttons; icon buttons (language, accessibility) keep their look. */
    static boolean themed(GuiEventListener c) {
        return c instanceof AbstractButton b && b.visible && !(c instanceof ImageButton)
            && !b.getMessage().getString().isEmpty();
    }

    private static void restyle(GuiGraphics g, Font font, List<? extends GuiEventListener> children, int a) {
        float t = seconds();
        List<AbstractButton> bs = new ArrayList<>();
        for (GuiEventListener c : children)
            if (themed(c))
                bs.add((AbstractButton) c);
        if (bs.isEmpty())
            return;
        // each button's own fit, then one shared size per button height
        float[] fit = new float[bs.size()];
        Component[] text = new Component[bs.size()];
        float[] width = new float[bs.size()];
        for (int i = 0; i < bs.size(); i++) {
            AbstractButton b = bs.get(i);
            text[i] = styled(ButtonLabelFit.display(b.getMessage().getString()).toUpperCase(java.util.Locale.ROOT), PLATE_FONT);
            width[i] = font.width(text[i]) + OUTLINE_W;
            fit[i] = ButtonLabelFit.fitScale(width[i], BTN_CAP, b.getWidth(), b.getHeight());
        }
        Map<Integer, Float> group = new HashMap<>();
        for (int i = 0; i < bs.size(); i++) {
            int h = bs.get(i).getHeight();
            if (group.containsKey(h))
                continue;
            List<Float> same = new ArrayList<>();
            for (int j = 0; j < bs.size(); j++)
                if (bs.get(j).getHeight() == h)
                    same.add(fit[j]);
            float[] arr = new float[same.size()];
            for (int k = 0; k < arr.length; k++)
                arr[k] = same.get(k);
            group.put(h, ButtonLabelFit.groupScale(arr, ButtonLabelFit.FLOOR_CAP / BTN_CAP));
        }
        // two passes, so a hovered plate's glow sits behind its neighbours
        for (int pass = 0; pass < 2; pass++) {
            for (int i = 0; i < bs.size(); i++) {
                AbstractButton b = bs.get(i);
                boolean hot = b.active && b.isHoveredOrFocused();
                if (pass == 0) {
                    if (hot)
                        glow(g, b.getX(), b.getY(), b.getWidth(), b.getHeight(), t, a);
                } else {
                    plate(g, b.getX(), b.getY(), b.getWidth(), b.getHeight(), text[i].getString().hashCode(), b.active, hot, a);
                    float s = ButtonLabelFit.scaleFor(fit[i], group.get(b.getHeight()));
                    label(g, font, text[i], width[i], s, b.getX(), b.getY(), b.getWidth(), b.getHeight(), b.active, hot, a);
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

    /** the outline's reach, both sides together, in label units: measured with the label so it fits too */
    private static final float OUTLINE_W = 1.5f;

    /** the label: engraved capitals, ivory at the top to gold below, a dark crimson outline and a drop shadow */
    private static void label(GuiGraphics g, Font font, Component c, float w, float s, int x0, int y0, int bw, int bh,
                              boolean active, boolean hot, int a) {
        float capH = BTN_CAP * s;
        float tx = ButtonLabelFit.left(x0, bw, (w - OUTLINE_W) * s);
        float top = ButtonLabelFit.capTop(y0, bh, capH);
        float ty = top - BTN_CAP_TOP * s;
        // the hard edge: nothing of the label leaves the frame, whatever the font measures
        int pad = Math.max(2, Math.round(ButtonLabelFit.padding(bw)) - 2);
        g.enableScissor(x0 + pad, y0 + 2, x0 + bw - pad, y0 + bh - 2);
        try {
            if (!active) {
                drawAt(g, font, c, tx + 0.5f, ty + 0.75f, s, withAlpha(0xD0000000, a));
                drawAt(g, font, c, tx, ty, s, withAlpha(0xFF6E6152, a));
                return;
            }
            // the drop shadow, then the outline in eight directions (three quarters of a GUI unit, whatever the scale)
            drawAt(g, font, c, tx + 0.8f, ty + 1.0f, s, withAlpha(0xC0000000, a));
            int ol = hot ? OUTLINE_HOT : OUTLINE;
            for (int dx = -1; dx <= 1; dx++)
                for (int dy = -1; dy <= 1; dy++)
                    if (dx != 0 || dy != 0)
                        drawAt(g, font, c, tx + dx * 0.75f, ty + dy * 0.75f, s, withAlpha(ol, a));
            // the fill in four bands, ivory at the top to deep gold at the foot of the letters
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
        } finally {
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
}
