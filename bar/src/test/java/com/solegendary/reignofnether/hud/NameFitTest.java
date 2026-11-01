package com.solegendary.reignofnether.hud;

import org.junit.jupiter.api.Test;

import java.util.function.ToIntFunction;

import static org.junit.jupiter.api.Assertions.*;

/**
 * BAC-008: a building name too wide for its panel ("Centro Urbano (Capital)" in es_es) is cut to the width and ends in
 * an ellipsis; it is never shrunk. The width function is a stub (6 px a character), no Minecraft Font in JUnit.
 */
class NameFitTest {

    private static final ToIntFunction<String> SIX_PX = s -> s.length() * 6;

    @Test
    void a_name_that_fits_comes_back_unchanged() {
        assertEquals("Town Centre", NameFit.fit("Town Centre", 100, SIX_PX));
    }

    @Test
    void a_name_exactly_the_width_is_unchanged() {
        assertEquals("Centro", NameFit.fit("Centro", 36, SIX_PX));
    }

    @Test
    void a_too_long_name_is_fitted_with_an_ellipsis() {
        String name = "Centro Urbano (Capital)";   // 23 chars = 138 px
        String out = NameFit.fit(name, 120, SIX_PX);
        assertTrue(SIX_PX.applyAsInt(out) <= 120, "fits the width: " + out);
        assertTrue(out.endsWith("…"), "ends with an ellipsis: " + out);
        assertTrue(name.startsWith(out.substring(0, out.length() - 1)), "is a prefix of the name: " + out);
        assertEquals("Centro Urbano (Capi…", out, "keeps as much of the name as fits");
    }

    @Test
    void no_dangling_bracket_or_space_before_the_ellipsis() {
        // 90 px: "Centro Urbano (C" + ellipsis is 102 px, "Centro Urbano (" is cut back to "Centro Urbano"
        assertEquals("Centro Urbano…", NameFit.fit("Centro Urbano (Capital)", 90, SIX_PX));
    }

    @Test
    void a_width_too_small_for_anything_gives_an_empty_string_not_an_overrun() {
        assertEquals("", NameFit.fit("Centro Urbano (Capital)", 3, SIX_PX));
        assertEquals("…", NameFit.fit("Centro Urbano (Capital)", 6, SIX_PX));
    }
}
