package com.solegendary.reignofnether.hud;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tester, Oct 26 (title screen and lobbies at a large GUI scale): labels overflowed their frames, sat off-centre and came
 * in wildly different sizes. Every themed label must fit inside its frame's padding, be centred within 1 px both ways,
 * and share one size with the other buttons of its height unless it alone cannot fit at that size.
 */
class ButtonLabelFitTest {

    static final float CAP = 13f;   // the plate font's cap height at scale 1 (GothicText.BTN_CAP, about 12.98)

    @Test
    void labelFitsInsideTheFrameAtEveryWidth() {
        int[] widths = {14, 16, 24, 40, 52, 54, 76, 98, 142, 200};
        float[] labels = {12f, 40f, 84f, 113f, 165f, 215f, 400f};
        for (int bw : widths)
            for (float lw : labels)
                for (int bh : new int[]{14, 16, 18, 20}) {
                    float s = ButtonLabelFit.fitScale(lw, CAP, bw, bh);
                    assertTrue(lw * s <= ButtonLabelFit.innerWidth(bw) + 1e-3f,
                        "label " + lw + " at " + s + " overflows a " + bw + " wide button");
                    assertTrue(CAP * s <= bh * ButtonLabelFit.HEIGHT_SHARE + 1e-3f, "label too tall for " + bh);
                    assertTrue(s <= 1f);
                }
    }

    @Test
    void labelIsCentredWithinOnePixel() {
        int x0 = 37, y0 = 101;
        for (int bw : new int[]{16, 54, 98, 200})
            for (int bh : new int[]{16, 18, 20}) {
                float w = 60f * ButtonLabelFit.fitScale(60f, CAP, bw, bh);
                float capH = CAP * ButtonLabelFit.fitScale(60f, CAP, bw, bh);
                float left = ButtonLabelFit.left(x0, bw, w);
                float right = x0 + bw - (left + w);
                assertTrue(Math.abs((left - x0) - right) <= 1f, "horizontal gaps " + (left - x0) + " vs " + right);
                float top = ButtonLabelFit.capTop(y0, bh, capH);
                float below = y0 + bh - (top + capH);
                assertTrue(Math.abs((top - y0) - below) <= 1f, "vertical gaps " + (top - y0) + " vs " + below);
            }
    }

    @Test
    void buttonsOfOneHeightShareOneSize() {
        float floor = ButtonLabelFit.FLOOR_CAP / CAP;
        // title screen: SINGLEPLAYER, MULTIPLAYER (200 wide), MODS, REALMS, OPTIONS..., QUIT GAME (98 wide)
        float[] fits = {
            ButtonLabelFit.fitScale(165f, CAP, 200, 20), ButtonLabelFit.fitScale(160f, CAP, 200, 20),
            ButtonLabelFit.fitScale(60f, CAP, 98, 20), ButtonLabelFit.fitScale(84f, CAP, 98, 20),
            ButtonLabelFit.fitScale(113f, CAP, 98, 20), ButtonLabelFit.fitScale(126f, CAP, 98, 20)};
        float group = ButtonLabelFit.groupScale(fits, floor);
        for (float f : fits)
            assertEquals(group, ButtonLabelFit.scaleFor(f, group), 1e-6f, "every title label at the one size");
        // one label far too long for its button shrinks alone; the rest keep the shared size
        float tiny = ButtonLabelFit.fitScale(600f, CAP, 54, 20);
        float[] withOutlier = {fits[0], fits[2], tiny};
        float g2 = ButtonLabelFit.groupScale(withOutlier, floor);
        assertEquals(Math.min(fits[0], fits[2]), g2, 1e-6f);
        assertEquals(tiny, ButtonLabelFit.scaleFor(tiny, g2), 1e-6f);
    }

    @Test
    void realmsGetsAShortLabelOthersStay() {
        assertEquals("Realms", ButtonLabelFit.display("Minecraft Realms"));
        assertEquals("Singleplayer", ButtonLabelFit.display("Singleplayer"));
    }
}
