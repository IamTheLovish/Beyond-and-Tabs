package com.solegendary.reignofnether.hud;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * BAC-008: nothing on the economy bar, the player strip or the commander ability tooltip falls back to English in
 * es_es. Every key those three surfaces read must exist in en_us AND es_es, with the same number of %-arguments.
 * The list below is the surfaces' key list:
 *  - economy bar: EconomyBarRenderer (every I18n.get key; the tooltip, stall, waste and overcharge lines)
 *  - player strip: PlayerPanelClientEvents.java:207-208 (stats.strip, stats.strip_spectating)
 *  - commander ability: CommanderAbility.getButton (title / line1 / line2 per kind, plus the cooldown line)
 */
class LangCoverageTest {

    private static final String ECO = "hud.reignofnether.economy.";
    private static final String CMD = "ability.reignofnether.commander.";

    private static final List<String> KEYS = new ArrayList<>();
    static {
        for (String k : List.of("workers", "stall", "stall_short", "pool.metal", "pool.energy", "waste", "sharing",
                "overcharge.word", "overcharge.chip_on", "overcharge.chip_off", "overcharge.tip_on",
                "overcharge.tip_off", "overcharge.tip_cost", "overcharge.tip_cap", "overcharge.tip_auto",
                "metal.title", "metal.flow", "metal.grow", "metal.share_full", "metal.share", "metal.drag",
                "energy.title", "energy.title_overcharged", "energy.flow", "energy.wind", "energy.mana",
                "energy.mana_overcharged", "energy.overcharge", "energy.conv1", "energy.conv2", "energy.conv3",
                "energy.conv4", "energy.mana_pool", "energy.wind_cheap", "units_tip"))
            KEYS.add(ECO + k);
        for (String k : List.of("both", "metal", "energy", "todo_metal", "todo_energy"))
            KEYS.add("hud.reignofnether.overflow." + k);
        KEYS.add("hud.reignofnether.stats.strip");
        KEYS.add("hud.reignofnether.stats.strip_spectating");
        KEYS.add(CMD + "cooldown");
        for (String kind : List.of("war_horn", "wildstride", "riptide", "blink", "overclock", "dread", "rally"))
            for (String part : List.of("title", "line1", "line2"))
                KEYS.add(CMD + kind + "." + part);
    }

    private static Map<String, String> lang(String file) throws IOException {
        Map<String, String> out = new HashMap<>();
        try (InputStream in = LangCoverageTest.class.getResourceAsStream("/assets/reignofnether/lang/" + file)) {
            assertNotNull(in, file + " on the classpath");
            String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            Matcher m = Pattern.compile("\"([^\"]+)\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").matcher(json);
            while (m.find())
                out.put(m.group(1), m.group(2));
        }
        return out;
    }

    private static int args(String value) {
        Matcher m = Pattern.compile("%(\\d+\\$)?[sdfx]").matcher(value.replace("%%", ""));
        int n = 0;
        while (m.find())
            n++;
        return n;
    }

    @Test
    void every_key_of_the_three_surfaces_is_in_en_us() throws IOException {
        Map<String, String> en = lang("en_us.json");
        List<String> missing = new ArrayList<>();
        for (String k : KEYS)
            if (!en.containsKey(k))
                missing.add(k);
        assertTrue(missing.isEmpty(), "missing in en_us: " + missing);
    }

    @Test
    void every_key_of_the_three_surfaces_is_in_es_es() throws IOException {
        Map<String, String> es = lang("es_es.json");
        List<String> missing = new ArrayList<>();
        for (String k : KEYS)
            if (!es.containsKey(k))
                missing.add(k);
        assertTrue(missing.isEmpty(), "missing in es_es (English fallback): " + missing);
    }

    @Test
    void argument_counts_match_between_en_us_and_es_es() throws IOException {
        Map<String, String> en = lang("en_us.json"), es = lang("es_es.json");
        List<String> bad = new ArrayList<>();
        for (String k : KEYS)
            if (en.containsKey(k) && es.containsKey(k) && args(en.get(k)) != args(es.get(k)))
                bad.add(k + " en=" + args(en.get(k)) + " es=" + args(es.get(k)));
        assertTrue(bad.isEmpty(), "argument count differs: " + bad);
    }

    @Test
    void es_es_is_not_just_a_copy_of_the_english() throws IOException {
        // the point of the task: Spanish text, not the English pasted in. Single-word names and the bare "Metal"
        // title may legitimately match, so only count how many differ.
        Map<String, String> en = lang("en_us.json"), es = lang("es_es.json");
        long same = KEYS.stream().filter(k -> en.containsKey(k) && en.get(k).equals(es.get(k))).count();
        assertTrue(same <= 4, same + " of " + KEYS.size() + " es_es values are identical to the English");
    }
}
