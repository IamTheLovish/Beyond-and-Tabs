package com.solegendary.reignofnether.onboarding;

import com.solegendary.reignofnether.faction.Faction;
import com.solegendary.reignofnether.faction.Factions;
import com.solegendary.reignofnether.matchstart.FactionPicker;
import com.solegendary.reignofnether.util.MiscUtil;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;

/**
 * The faction info card (design/onboarding.md): what a new player needs to choose - identity, strengths, weaknesses,
 * signature mechanic, the T3 experimental and how forgiving the faction is - drawn beside the faction picker in both
 * lobbies (FactionPicker calls {@link #renderBeside}) and on the First Battle screen. Text comes from the lang file
 * (onboarding.reignofnether.faction.&lt;faction&gt;.*), written from design/ faction docs, so it can be retuned without code.
 *
 * Wrapping is cached per faction and width: hovering down the list re-wraps once per row, not once per frame.
 */
public final class FactionInfoCard {

    public static final int WIDTH = 196;
    static final int PAD = 6;
    static final String[] SECTIONS = { "strengths", "weaknesses", "signature", "t3" };

    private static Faction cachedFaction = null;
    private static int cachedWidth = -1;
    private static final List<Line> cached = new ArrayList<>();

    private record Line(FormattedCharSequence text, int color, int gapBefore) { }

    private FactionInfoCard() { }

    static String key(Faction f, String part) {
        return "onboarding.reignofnether.faction." + f.getName() + "." + part;
    }

    /** True when the lang file has a card for this faction (Random and unknown factions have none). */
    public static boolean hasCard(Faction f) {
        return f != null && f != Factions.RANDOM && I18n.exists(key(f, "identity"));
    }

    private static void build(Font font, Faction f, int width) {
        cached.clear();
        cachedFaction = f;
        cachedWidth = width;
        int inner = width - PAD * 2;
        for (FormattedCharSequence s : font.split(Component.literal(I18n.get(key(f, "identity"))), inner))
            cached.add(new Line(s, OnboardingUi.TEXT, 0));
        for (String sec : SECTIONS) {
            String body = I18n.get(key(f, sec));
            String label = I18n.get("onboarding.reignofnether.faction_card." + sec);
            boolean first = true;
            for (FormattedCharSequence s : font.split(Component.literal(label + "  " + body), inner)) {
                cached.add(new Line(s, first ? OnboardingUi.TEXT_HOT : OnboardingUi.TEXT_DIM, first ? 4 : 0));
                first = false;
            }
        }
        String diff = I18n.get(key(f, "difficulty"));
        for (FormattedCharSequence s : font.split(Component.literal(I18n.get("onboarding.reignofnether.faction_card.difficulty") + "  " + diff), inner))
            cached.add(new Line(s, OnboardingUi.GOLD_HOT, 4));
    }

    /** Height the card needs at this width. */
    public static int height(Font font, Faction f, int width) {
        if (!hasCard(f))
            return 0;
        if (f != cachedFaction || width != cachedWidth)
            build(font, f, width);
        int h = PAD + 22;
        for (Line l : cached)
            h += 10 + l.gapBefore;
        return h + PAD;
    }

    /** Draws the card with its top-left at (x, y). Returns its height (0 when the faction has no card). */
    public static int render(GuiGraphics g, Font font, Faction f, int x, int y, int width) {
        int h = height(font, f, width);
        if (h == 0)
            return 0;
        OnboardingUi.panel(g, x, y, x + width, y + h, 1f);
        FactionPicker.renderIcon(g, f, x + PAD, y + PAD, 16, false);
        g.drawString(font, font.plainSubstrByWidth(MiscUtil.getFactionName(f), width - PAD * 2 - 20), x + PAD + 20, y + PAD,
            OnboardingUi.GOLD_HOT, false);
        String tag = FactionPicker.tagline(f);
        g.drawString(font, font.plainSubstrByWidth(tag, width - PAD * 2 - 20), x + PAD + 20, y + PAD + 9, OnboardingUi.TEXT_DIM, false);
        OnboardingUi.rule(g, x + PAD, x + width - PAD, y + PAD + 20);
        int ly = y + PAD + 24;
        for (Line l : cached) {
            ly += l.gapBefore;
            g.drawString(font, l.text, x + PAD, ly, l.color, false);
            ly += 10;
        }
        return h;
    }

    /**
     * Beside an open faction picker list (x, y, w, h): to its left when there is room, else to its right, else not at
     * all (a very narrow window keeps the list usable). Kept on screen vertically.
     */
    public static void renderBeside(GuiGraphics g, Font font, Faction f, int px, int py, int pw, int ph, int screenW, int screenH) {
        if (!hasCard(f))
            return;
        int w = Math.min(WIDTH, Math.max(140, screenW / 3));
        int x;
        if (px - 4 - w >= 2)
            x = px - 4 - w;
        else if (px + pw + 4 + w <= screenW - 2)
            x = px + pw + 4;
        else
            return;
        int h = height(font, f, w);
        int y = Math.max(2, Math.min(py, screenH - h - 2));
        g.pose().pushPose();
        g.pose().translate(0, 0, 400);   // above the owning screen like the picker itself
        render(g, font, f, x, y, w);
        g.pose().popPose();
    }
}
