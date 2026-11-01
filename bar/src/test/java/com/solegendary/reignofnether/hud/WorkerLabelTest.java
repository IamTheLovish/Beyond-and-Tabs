package com.solegendary.reignofnether.hud;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.function.ToIntFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The worker count under the unit count must stay inside its slot (EconomyBarRenderer.POP_W) in every shipped
 * language with a key for it. Codex review of BAC-008 (2026-11-01): es_es "10 trabajadores" is 86 px in the 58 px
 * slot, so it ran over the metal expense figure beside it. Width stub: Minecraft's default font advances about 6 px
 * a character and 4 px a space (Codex measured "10 workers" at 57 px; this stub gives 58), no Font in JUnit.
 */
class WorkerLabelTest {

    private static final ToIntFunction<String> MC_APPROX = s -> {
        int w = 0;
        for (char c : s.toCharArray())
            w += c == ' ' ? 4 : 6;
        return w;
    };

    private static String pattern(String lang) throws IOException {
        try (InputStream in = WorkerLabelTest.class.getResourceAsStream("/assets/reignofnether/lang/" + lang + ".json")) {
            assertNotNull(in, lang + ".json on the classpath");
            String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            Matcher m = Pattern.compile("\"hud\\.reignofnether\\.economy\\.workers\"\\s*:\\s*\"([^\"]*)\"").matcher(json);
            assertTrue(m.find(), lang + " has hud.reignofnether.economy.workers");
            return m.group(1);
        }
    }

    @Test
    void the_worker_label_fits_its_slot_in_english_and_spanish() throws IOException {
        for (String lang : new String[] { "en_us", "es_es" }) {
            for (int workers : new int[] { 1, 10, 100, 250 }) {
                String full = String.format(pattern(lang), workers);
                String shown = EconomyBarRenderer.workerLabel(full, MC_APPROX);
                assertTrue(MC_APPROX.applyAsInt(shown) <= EconomyBarRenderer.POP_W,
                    lang + " " + workers + ": \"" + shown + "\" is " + MC_APPROX.applyAsInt(shown) + " px, slot "
                        + EconomyBarRenderer.POP_W);
                if (MC_APPROX.applyAsInt(full) <= EconomyBarRenderer.POP_W)
                    assertTrue(shown.equals(full), lang + " " + workers + ": a label that fits is shown whole: " + shown);
                assertTrue(shown.startsWith(String.valueOf(workers)), lang + ": the count itself is never cut: " + shown);
            }
        }
    }
}
