package com.solegendary.reignofnether.unit.modelling.motion;

import com.solegendary.reignofnether.barfx.BarFx;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The faction finishes of a fallen body (lovish, Oct 29: Sunforged pulverize into light, Gravebound crumble; the rest
 * proposed): the shipped data covers every faction with a known style, every finish starts after the fall has landed
 * and the server has removed the unit and is over before the old hold ended, and the timeline does what the data says.
 */
class DeathFinishTest {

    private static final Set<String> STYLES = Set.of("light", "crumble", "dust", "leaves", "seawater", "static", "gears",
        "derez", "sink");

    private static DeathFinish.Style[] shipped() throws Exception {
        try (InputStream in = DeathFinish.class.getResourceAsStream(DeathFinish.RESOURCE)) {
            assertNotNull(in, "the finishes ship in the jar");
            return DeathFinish.parse(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
    }

    @Test
    void everyFactionHasItsOwnFinish() throws Exception {
        DeathFinish.Style[] s = shipped();
        assertEquals(8, s.length);
        assertEquals("light", s[BarFx.F_SUNFORGED].style(), "lovish: white angel troops pulverize into light");
        assertEquals("crumble", s[BarFx.F_GRAVEBOUND].style(), "lovish: death could crumble");
        assertEquals("sink", s[BarFx.F_NONE].style(), "no faction: the plain sink");
        assertEquals("derez", s[BarFx.F_TIDE].style(), "the AI faction that replaces the Tidewrought derezzes into light");
        assertTrue(s[BarFx.F_TIDE].glow(), "its shards are light");
        for (int f = BarFx.F_SUNFORGED; f <= BarFx.F_BRASS; f++) {
            assertTrue(STYLES.contains(s[f].style()), DeathFinish.KEYS[f] + ": a known style " + s[f].style());
            assertTrue(!s[f].style().equals("sink") && s[f].particles() > 0, DeathFinish.KEYS[f] + " has a finish of its own");
            for (int g = f + 1; g <= BarFx.F_BRASS; g++)
                assertTrue(!s[f].style().equals(s[g].style()), "no two factions alike");
        }
    }

    @Test
    void aFinishStartsOnceTheBodyIsDownAndHeld() throws Exception {
        for (DeathFinish.Style s : shipped()) {
            assertTrue(s.start() >= DeathFinish.EARLIEST_START, "after the fall (18) and the removal (20): " + s.start());
            assertTrue(s.end() <= DeathFall.END, "gone no later than the old sink: " + s.end());
            assertTrue(s.particles() <= 64);
        }
        assertEquals(DeathFinish.EARLIEST_START, DeathFinish.parse(new StringReader(
            "{\"sunforged\": {\"style\": \"light\", \"start\": 3, \"ticks\": 10, \"fade\": 1, \"rise\": 0, \"squash\": 0,"
                + " \"flicker\": 0, \"glow\": true, \"color\": \"#FFFFFF\", \"color2\": \"#FFFFFF\", \"particles\": 5}}"))
            [BarFx.F_SUNFORGED].start(), "a start before the body is down is moved after it");
    }

    @Test
    void aMissingFactionSinksAsBefore() {
        DeathFinish.Style[] s = DeathFinish.parse(new StringReader("{}"));
        for (DeathFinish.Style f : s)
            assertEquals(DeathFinish.SINK, f);
        assertEquals(DeathFall.HOLD_END, DeathFinish.SINK.start());
        assertEquals(DeathFall.END, DeathFinish.SINK.end());
        for (float t = 20f; t <= DeathFall.END; t += 0.5f)
            assertEquals(-DeathFall.sinkBlocks(t), DeathFinish.lift(DeathFinish.SINK, t), 1e-5f, "the same sink as before");
    }

    @Test
    void theTimelineDoesWhatTheDataSays() throws Exception {
        DeathFinish.Style light = shipped()[BarFx.F_SUNFORGED];
        float before = light.start() - 1f, after = light.end() + 0f;
        assertEquals(1f, DeathFinish.alpha(light, before), 0f, "whole until the finish");
        assertEquals(0f, DeathFinish.lift(light, before), 0f);
        assertEquals(1f - light.fade(), DeathFinish.alpha(light, after), 1e-5f, "faded by the end");
        assertEquals(light.rise(), DeathFinish.lift(light, after), 1e-5f, "risen by the end");
        assertEquals(0f, DeathFinish.scale(light, after), 1e-5f, "a body that fades away shrinks away too");
        assertEquals(1f, DeathFinish.scale(light, light.start() + light.ticks() * 0.7f), 1e-5f, "only at the very end");

        DeathFinish.Style bone = shipped()[BarFx.F_GRAVEBOUND];
        assertEquals(1f - bone.squash(), DeathFinish.heightScale(bone, bone.end()), 1e-5f, "crumbled flat");
        assertEquals(1f, DeathFinish.scale(bone, bone.end()), 0f, "a body that leaves bones behind does not shrink");

        DeathFinish.Style storm = shipped()[BarFx.F_STORM];
        float lo = 1f, hi = 0f;
        for (float t = storm.start() + 0.25f; t < storm.end() - 4; t += 0.25f) {
            float a = DeathFinish.alpha(storm, t);
            lo = Math.min(lo, a);
            hi = Math.max(hi, a);
        }
        assertTrue(hi - lo > 0.5f, "static strobes the body");
    }

    @Test
    void fewerParticlesZoomedOutNoneAtTheStrategicZoom() throws Exception {
        DeathFinish.Style light = shipped()[BarFx.F_SUNFORGED];
        assertEquals(light.particles(), DeathFinish.particles(light, 0f, false), "first person: all of it");
        assertEquals(light.particles(), DeathFinish.particles(light, 25f, true), "up close: all of it");
        int mid = DeathFinish.particles(light, 50f, true);
        assertTrue(mid > 0 && mid < light.particles(), "fewer at a mid zoom: " + mid);
        assertEquals(0, DeathFinish.particles(light, 80f, true), "units are icons: none");
    }
}
