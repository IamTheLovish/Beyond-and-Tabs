package com.solegendary.reignofnether.util;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * lovish, Oct 23 00:11: "the old minecraft on is on top of our art so its reprinted twice" - the title screen's
 * buttons drew Minecraft's own label AND the gothic one. The vanilla pass must draw no label; the real label must be
 * back right after (clicks, narration, tooltips, the gothic pass itself read it).
 */
class HiddenLabelsTest {

    static final class Btn {
        String label;
        final int width;
        final boolean visible;
        Btn(String label, int width, boolean visible) { this.label = label; this.width = width; this.visible = visible; }
    }

    static HiddenLabels.Restore hide(List<Btn> bs) {
        return HiddenLabels.hide(bs, b -> b.label, (b, s) -> b.label = s, b -> b.visible && b.width >= 40);
    }

    @Test
    void blanksTextButtonsDuringTheVanillaPassThenRestores() {
        List<Btn> bs = List.of(new Btn("Singleplayer", 200, true), new Btn("Mods", 98, true));
        HiddenLabels.Restore r = hide(bs);
        assertEquals("", bs.get(0).label);
        assertEquals("", bs.get(1).label);
        r.restore();
        assertEquals("Singleplayer", bs.get(0).label);
        assertEquals("Mods", bs.get(1).label);
    }

    @Test
    void leavesIconButtonsAndHiddenButtonsAlone() {
        // the language / accessibility icon buttons (20 wide) keep vanilla drawing: the gothic pass skips them too
        Btn icon = new Btn("Language", 20, true);
        Btn hidden = new Btn("Realms", 200, false);
        HiddenLabels.Restore r = hide(List.of(icon, hidden));
        assertEquals("Language", icon.label);
        assertEquals("Realms", hidden.label);
        r.restore();
    }

    @Test
    void restoresEvenWhenTheVanillaPassThrows() {
        List<Btn> bs = List.of(new Btn("Skirmish", 200, true));
        assertThrows(IllegalStateException.class, () -> {
            try (HiddenLabels.Restore r = hide(bs)) {
                throw new IllegalStateException("render failed");
            }
        });
        assertEquals("Skirmish", bs.get(0).label);
    }

    @Test
    void restoreTwiceIsHarmless() {
        List<Btn> bs = new ArrayList<>(List.of(new Btn("First Battle", 200, true)));
        HiddenLabels.Restore r = hide(bs);
        r.restore();
        bs.get(0).label = "First Battle (new)";   // a label the screen changed after the frame
        r.restore();
        assertEquals("First Battle (new)", bs.get(0).label);
    }
}
