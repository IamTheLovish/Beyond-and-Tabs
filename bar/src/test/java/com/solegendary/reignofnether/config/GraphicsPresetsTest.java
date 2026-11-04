package com.solegendary.reignofnether.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** N1 graphics presets (BAC-013, HANDOFF-lane-c C5): one setting picks the costly display features together. */
class GraphicsPresetsTest {

    private static GraphicsPresets.Values v(GraphicsPresets.Preset p) {
        return GraphicsPresets.values(p);
    }

    @Test
    void eachPresetMapsToItsStatedValues() {
        assertEquals(new GraphicsPresets.Values(0, false, false, false, 0, 0), v(GraphicsPresets.Preset.LOW));
        assertEquals(new GraphicsPresets.Values(1, false, true, true, 1, 0), v(GraphicsPresets.Preset.MEDIUM));
        assertEquals(new GraphicsPresets.Values(1, true, true, true, 2, 1), v(GraphicsPresets.Preset.HIGH));
        assertEquals(new GraphicsPresets.Values(2, true, true, true, 2, 2), v(GraphicsPresets.Preset.ULTRA));
    }

    @Test
    void presetsOnlyGetCostlierUpwards() {
        GraphicsPresets.Preset[] all = GraphicsPresets.Preset.values();
        for (int i = 1; i < all.length; i++) {
            GraphicsPresets.Values lo = v(all[i - 1]), hi = v(all[i]);
            assertTrue(hi.worldLook() >= lo.worldLook() && hi.buildingAnimation() >= lo.buildingAnimation()
                && hi.branchWind() >= lo.branchWind() && (hi.valleyMist() || !lo.valleyMist())
                && (hi.pathEdges() || !lo.pathEdges()) && (hi.buildingAmbience() || !lo.buildingAmbience()),
                all[i - 1] + " -> " + all[i]);
        }
    }

    @Test
    void theCurrentSettingsReadBackAsTheirPresetOrCustom() {
        for (GraphicsPresets.Preset p : GraphicsPresets.Preset.values())
            assertEquals(p, GraphicsPresets.match(v(p)));
        assertNull(GraphicsPresets.match(new GraphicsPresets.Values(0, true, false, false, 0, 2)), "a mix is Custom");
    }

    @Test
    void cyclingGoesLowToUltraAndRoundAndCustomStartsAtLow() {
        assertEquals(GraphicsPresets.Preset.MEDIUM, GraphicsPresets.next(GraphicsPresets.Preset.LOW));
        assertEquals(GraphicsPresets.Preset.LOW, GraphicsPresets.next(GraphicsPresets.Preset.ULTRA));
        assertEquals(GraphicsPresets.Preset.LOW, GraphicsPresets.next(null));
    }

    private static int intDefault(String src, String key) {
        Matcher m = Pattern.compile("defineInRange\\(\"" + key + "\", (-?\\d+),").matcher(src);
        assertTrue(m.find(), key);
        return Integer.parseInt(m.group(1));
    }

    private static boolean boolDefault(String src, String key) {
        Matcher m = Pattern.compile("define\\(\"" + key + "\", (true|false)\\)").matcher(src);
        assertTrue(m.find(), key);
        return Boolean.parseBoolean(m.group(1));
    }

    @Test
    void highIsTodaysLookSoTheDefaultChangesNothing() throws IOException {
        Path p = Path.of("src/main/java/com/solegendary/reignofnether/config/ReignOfNetherClientConfigs.java");
        if (!Files.exists(p)) p = Path.of("bar/").resolve(p);
        String src = Files.readString(p);
        boolean ambience = boolDefault(src, "building_ambience");
        int anim = intDefault(src, "building_animation");
        // -1 = not picked yet: follows ambience (DisplaySettings.buildingAnimation), on -> Full
        int animShown = anim < 0 ? (ambience ? DisplaySettings.ANIM_FULL : DisplaySettings.ANIM_OFF) : anim;
        assertEquals(new GraphicsPresets.Values(intDefault(src, "world_look"), boolDefault(src, "valley_mist"),
            boolDefault(src, "path_edges"), ambience, animShown, intDefault(src, "branch_wind")), v(GraphicsPresets.Preset.HIGH));
    }
}
