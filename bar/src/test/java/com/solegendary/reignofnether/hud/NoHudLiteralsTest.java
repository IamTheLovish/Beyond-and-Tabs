package com.solegendary.reignofnether.hud;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * BAC-008: the economy bar and the commander ability tooltip draw no English literal; every drawn string comes from a
 * lang key (I18n.get). A string literal counts as English when it holds a run of 3+ letters and is not a lang key or
 * resource path (all lower case, only letters / digits / "_" "." "/" ":" "-").
 */
class NoHudLiteralsTest {

    private static final String ROOT = "src/main/java/com/solegendary/reignofnether/";

    private static String source(String file) throws IOException {
        Path p = Path.of(ROOT + file);
        if (!Files.exists(p))
            p = Path.of("bar/" + ROOT + file);
        return Files.readString(p);
    }

    /** The string literals of a Java source, with comments (and char literals) skipped. */
    static List<String> literals(String src) {
        List<String> out = new ArrayList<>();
        int i = 0, n = src.length();
        while (i < n) {
            char c = src.charAt(i);
            if (c == '/' && i + 1 < n && src.charAt(i + 1) == '/') {
                while (i < n && src.charAt(i) != '\n') i++;
            } else if (c == '/' && i + 1 < n && src.charAt(i + 1) == '*') {
                int end = src.indexOf("*/", i + 2);
                i = end < 0 ? n : end + 2;
            } else if (c == '\'') {
                i++;
                while (i < n && src.charAt(i) != '\'') i += src.charAt(i) == '\\' ? 2 : 1;
                i++;
            } else if (c == '"') {
                StringBuilder sb = new StringBuilder();
                i++;
                while (i < n && src.charAt(i) != '"') {
                    if (src.charAt(i) == '\\' && i + 1 < n) sb.append(src.charAt(++i));
                    else sb.append(src.charAt(i));
                    i++;
                }
                i++;
                out.add(sb.toString());
            } else {
                i++;
            }
        }
        return out;
    }

    private static final Pattern LETTERS = Pattern.compile("[A-Za-z]{3,}");
    private static final Pattern KEY_OR_PATH = Pattern.compile("[a-z0-9_./:\\-]+");

    static List<String> english(String src) {
        List<String> bad = new ArrayList<>();
        for (String s : literals(src))
            if (LETTERS.matcher(s).find() && !KEY_OR_PATH.matcher(s).matches())
                bad.add(s);
        return bad;
    }

    @Test
    void the_scanner_finds_english_and_skips_keys_and_comments() {
        String src = "// \"Hello world\"\n/* \"Block text\" */ a(\"Metal  \" + x); b(\"hud.reignofnether.x\");"
            + " c(\"textures/item/goat_horn.png\"); d('\"'); e(\" workers\");";
        assertEquals(List.of("Metal  ", " workers"), english(src));
    }

    @Test
    void economy_bar_has_no_english_literal() throws IOException {
        List<String> bad = english(source("hud/EconomyBarRenderer.java"));
        assertTrue(bad.isEmpty(), "EconomyBarRenderer draws English literals: " + bad);
    }

    @Test
    void commander_ability_has_no_english_literal() throws IOException {
        List<String> bad = english(source("ability/abilities/CommanderAbility.java"));
        assertTrue(bad.isEmpty(), "CommanderAbility.getButton holds English literals: " + bad);
    }

    @Test
    void building_name_is_fitted_with_the_helper_not_shrunk() throws IOException {
        String portrait = source("hud/PortraitRendererBuilding.java");
        assertFalse(portrait.contains("NAME_SHRINK_SCALE"), "the portrait title must never be drawn in a smaller font");
        assertTrue(portrait.contains("NameFit.fit("), "PortraitRendererBuilding fits the title with NameFit.fit");
        assertTrue(source("hud/HoverCards.java").contains("NameFit.fit("), "the building hover card fits its title with NameFit.fit");
    }
}
