package com.solegendary.reignofnether.hud;

import com.solegendary.reignofnether.hud.buttons.Button;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AC-2: no tooltip reads "Format error: ...". Minecraft's I18n.get(key) runs String.format over the lang value, so a
 * value holding %s read without arguments comes back as "Format error: <value>".
 */
class TooltipFormatTest {

    private static Map<String, String> lang() throws IOException {
        Map<String, String> out = new HashMap<>();
        try (InputStream in = TooltipFormatTest.class.getResourceAsStream("/assets/reignofnether/lang/en_us.json")) {
            assertNotNull(in, "en_us.json on the classpath");
            String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            Matcher m = Pattern.compile("\"([^\"]+)\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").matcher(json);
            while (m.find())
                out.put(m.group(1), m.group(2));
        }
        return out;
    }

    private static final Pattern FORMAT_SPEC = Pattern.compile("%(\\d+\\$)?[sdfx]");

    @Test
    void ac2_hotkey_line_formats_from_the_raw_pattern() throws IOException {
        String raw = lang().get("hud.reignofnether.tooltip_hotkey");
        assertEquals("Hotkey: %s", raw);
        for (String label : List.of(".", "Q", "Home", "End")) {
            String line = Button.hotkeyLineText(label, raw, List.of("Idle Workers", "(Ctrl-click to select all)"));
            assertEquals("Hotkey: " + label, line);
            assertFalse(line.contains("Format error"));
        }
    }

    @Test
    void ac2_idle_button_tooltip_keys_need_no_arguments() throws IOException {
        Map<String, String> lang = lang();
        List<String> keys = List.of(
            "hud.helperbuttons.reignofnether.idle_workers",
            "hud.helperbuttons.reignofnether.idle_workers_shift",
            "hud.helperbuttons.reignofnether.idle_t2_builders",
            "hud.helperbuttons.reignofnether.commander",
            "hud.helperbuttons.reignofnether.commander_hint");
        for (String k : keys) {
            assertTrue(lang.containsKey(k), "missing lang key " + k);
            assertFalse(FORMAT_SPEC.matcher(lang.get(k)).find(), k + " has a format spec but is read without arguments");
        }
    }

    /** Every I18n.get("key") call with no arguments must name a lang value without a format spec (else "Format error"). */
    @Test
    void ac2_no_argless_i18n_call_reads_a_format_string() throws IOException {
        Map<String, String> lang = lang();
        Pattern call = Pattern.compile("I18n\\.get\\(\\s*\"([^\"]+)\"\\s*\\)");
        List<String> bad = new ArrayList<>();
        try (Stream<Path> files = Files.walk(Paths.get("src/main/java"))) {
            for (Path p : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                Matcher m = call.matcher(Files.readString(p, StandardCharsets.UTF_8));
                while (m.find()) {
                    String v = lang.get(m.group(1));
                    if (v != null && FORMAT_SPEC.matcher(v).find())
                        bad.add(p.getFileName() + ": " + m.group(1) + " = " + v);
                }
            }
        }
        assertTrue(bad.isEmpty(), "argless I18n.get on format strings: " + bad);
    }
}
