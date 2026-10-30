package com.solegendary.reignofnether.keybinds;

import com.solegendary.reignofnether.keybinds.BarGridDefaults.Row;
import com.solegendary.reignofnether.keybinds.ChordKeys.Chord;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** BarGridDefaults is BAR's profile "Grid" at commit 002dee75 as data: BAT's defaults must match it row by row. */
class BarGridDefaultsTest {

    private static Set<String> tsvLines() throws IOException {
        try (InputStream in = BarGridDefaultsTest.class.getResourceAsStream("/bar-grid-keybinds-002dee75.tsv")) {
            assertNotNull(in, "src/test/resources/bar-grid-keybinds-002dee75.tsv is missing");
            return new HashSet<>(List.of(new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\\R")));
        }
    }

    @Test
    void everyRowIsTheDefaultOrTheChord() throws IOException {
        Map<String, Keybinding> all = KeybindMigration.bindings();
        Set<String> tsv = tsvLines();
        for (Row r : BarGridDefaults.ROWS) {
            String name = r.bindingOrChord();
            if (name.startsWith("key.")) {
                Keybinding kb = all.get(name);
                assertNotNull(kb, name + " is not a binding in Keybindings");
                assertFalse(r.ctrl() || r.shift(), name + ": a binding row is a plain key; a modified key is a chord row");
                assertEquals(r.glfwKey(), kb.getDefaultKey(), name + " must ship on BAR's key (" + r.tsvLine() + ")");
            } else {
                assertEquals(Chord.valueOf(name), ChordKeys.of(r.glfwKey(), r.ctrl(), r.shift(), false), name + " chord (" + r.tsvLine() + ")");
            }
            assertTrue(tsv.contains(r.tsvLine()), name + ": '" + r.tsvLine() + "' is not a line of the BAR TSV");
        }
        for (String line : BarGridDefaults.DORMANT.keySet())
            assertTrue(tsv.contains(line), "dormant '" + line + "' is not a line of the BAR TSV");
    }

    @Test
    void everyBindingIsARowOrAnExtraWithAReason() {
        Set<String> rows = new HashSet<>();
        for (Row r : BarGridDefaults.ROWS)
            rows.add(r.bindingOrChord());
        Set<String> live = new HashSet<>();
        for (Keybinding kb : Keybindings.all()) {
            String name = kb.getTranslationKey();
            if (name == null)
                continue;
            live.add(name);
            assertTrue(rows.contains(name) || BarGridDefaults.EXTRAS.containsKey(name),
                name + " is neither a BAR row nor an extra in BarGridDefaults");
            assertFalse(rows.contains(name) && BarGridDefaults.EXTRAS.containsKey(name), name + " is both a row and an extra");
        }
        for (String name : BarGridDefaults.EXTRAS.keySet())
            assertTrue(live.contains(name), "extra " + name + " is not a binding any more");
        for (var e : BarGridDefaults.EXTRAS.entrySet())
            assertFalse(e.getValue().isBlank(), e.getKey() + " has no reason");
    }

    @Test
    void anExtraOnABarKeyIsMarkedAsSharing() {
        Set<Integer> barKeys = new HashSet<>();
        for (Row r : BarGridDefaults.ROWS)
            if (!r.ctrl() && !r.shift())
                barKeys.add(r.glfwKey());
        for (Keybinding kb : Keybindings.all()) {
            String name = kb.getTranslationKey();
            String why = name == null ? null : BarGridDefaults.EXTRAS.get(name);
            if (why != null && barKeys.contains(kb.getDefaultKey()))
                assertTrue(why.startsWith("shares:"),
                    name + " sits on BAR's plain key " + kb.getDefaultKey() + " without saying it shares it on purpose");
        }
    }
}
