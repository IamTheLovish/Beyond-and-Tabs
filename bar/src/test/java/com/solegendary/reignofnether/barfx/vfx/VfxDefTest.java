package com.solegendary.reignofnether.barfx.vfx;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The data side of the vfx foundation (design/vfx.md): every shipped effect parses without a single problem, ids hash
 * apart and ride float-exact in a BarFx event, the parser catches the typos an author makes, the atlas order matches
 * the generator, and the timeline maths (phases, fades, growth, light falloff, spawn counts) holds at its edges.
 */
class VfxDefTest {

    static final Path VFX_DIR = Paths.get("src/main/resources/assets/reignofnether/vfx");
    static final Path GENERATOR = Paths.get("../tools/gen_vfx_textures.py");

    static Map<String, String> shipped() throws IOException {
        Map<String, String> out = new HashMap<>();
        try (Stream<Path> files = Files.list(VFX_DIR)) {
            for (Path p : (Iterable<Path>) files::iterator) {
                String name = p.getFileName().toString();
                if (name.endsWith(".json"))
                    out.put(name.substring(0, name.length() - 5), Files.readString(p, StandardCharsets.UTF_8));
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ shipped data

    @Test
    void everyShippedEffectParsesWithoutAProblem() throws IOException {
        Map<String, String> files = shipped();
        assertFalse(files.isEmpty(), "no effects found in " + VFX_DIR.toAbsolutePath());
        TreeSet<String> effects = new TreeSet<>();
        for (Map.Entry<String, String> f : files.entrySet()) {
            List<String> errors = new ArrayList<>();
            VfxDef def = VfxParser.parse(f.getKey(), f.getValue(), errors);
            assertNotNull(def, f.getKey());
            assertTrue(errors.isEmpty(), f.getKey() + ": " + errors);
            if (def.isProjectile())
                assertFalse(def.projectile.entities.isEmpty(), f.getKey() + " binds no entity type");
            else {
                effects.add(def.id);
                assertTrue(def.timeline.total() > 0 && def.timeline.total() <= 60, def.id);
                assertTrue(def.cullRadius > 0, def.id);
            }
        }
        assertEquals(new TreeSet<>(VfxIds.SHIPPED), effects,
            "VfxIds.SHIPPED (what /bat vfx suggests) must list exactly the effects in " + VFX_DIR);
    }

    @Test
    void theShowcaseHasEveryLayerKind() throws IOException {
        VfxDef d = VfxParser.parse("sun_pillar", shipped().get("sun_pillar"), new ArrayList<>());
        assertNotNull(d);
        assertTrue(d.layers.stream().anyMatch(l -> l instanceof VfxDef.Ground));
        assertTrue(d.layers.stream().anyMatch(l -> l instanceof VfxDef.Ring r && r.stack >= 2 && r.alternate));
        assertTrue(d.layers.stream().anyMatch(l -> l instanceof VfxDef.Beam b && b.strands == 2 && b.bands > 0));
        assertTrue(d.layers.stream().anyMatch(l -> l instanceof VfxDef.Emitter e && e.kind == VfxDef.Emitter.Kind.EMBER && e.gravity > 0));
        assertNotNull(d.light);
        // the pillar drops in at the peak, after the circle has wound up
        VfxDef.Beam beam = (VfxDef.Beam) d.layers.stream().filter(l -> l instanceof VfxDef.Beam).findFirst().orElseThrow();
        assertEquals(d.timeline.windup, beam.start, 1e-6);
        assertEquals(0f, VfxTimeline.layerAlpha(beam, d.timeline, d.timeline.windup * 0.5f), 1e-6);
        assertTrue(VfxTimeline.layerAlpha(beam, d.timeline, d.timeline.windup + 0.5f) > 0.9f);
    }

    @Test
    void idsHashApartAndRideFloatExact() throws IOException {
        Map<Integer, String> seen = new HashMap<>();
        for (String id : shipped().keySet()) {
            int h = VfxIds.hash(id);
            assertTrue(h > 0 && h <= VfxIds.MASK, id + " hash out of 24 bits");
            assertEquals(h, (int) (float) h, id + " hash is not float-exact");
            String clash = seen.put(h, id);
            assertNull(clash, id + " collides with " + clash);
        }
        assertEquals(VfxIds.hash("sun_pillar"), VfxIds.hash("sun_pillar"));
        assertTrue(VfxIds.valid("sun_pillar") && VfxIds.valid("faction/storm_bolt"));
        assertFalse(VfxIds.valid("Sun Pillar") || VfxIds.valid("") || VfxIds.valid(null));
    }

    @Test
    void atlasOrderMatchesTheGenerator() throws IOException {
        String src = Files.readString(GENERATOR, StandardCharsets.UTF_8);
        int i = src.indexOf("NAMES = [");
        assertTrue(i >= 0, "NAMES list not found in the generator");
        String list = src.substring(i + 9, src.indexOf(']', i));
        List<String> names = new ArrayList<>();
        for (String part : list.split(",")) {
            String s = part.trim().replace("\"", "");
            if (!s.isEmpty()) names.add(s);
        }
        assertEquals(List.of(VfxAtlas.NAMES), names);
        assertEquals(VfxAtlas.GRID * VfxAtlas.GRID, VfxAtlas.NAMES.length);
        // uv rects stay inside their tile
        for (int t = 0; t < VfxAtlas.NAMES.length; t++) {
            assertTrue(VfxAtlas.u0(t) < VfxAtlas.u1(t) && VfxAtlas.v0(t) < VfxAtlas.v1(t));
            assertTrue(VfxAtlas.u0(t) >= 0 && VfxAtlas.u1(t) <= 1 && VfxAtlas.v0(t) >= 0 && VfxAtlas.v1(t) <= 1);
            assertEquals(t, VfxAtlas.byName(VfxAtlas.NAMES[t]));
        }
    }

    // ------------------------------------------------------------------ parser

    @Test
    void parserReportsAuthorMistakes() {
        List<String> errors = new ArrayList<>();
        VfxDef d = VfxParser.parse("bad_one", """
            { "timeline": { "windup": 0.5, "peak": 1, "fade": 0.5 },
              "layers": [
                { "type": "ring", "radus": 2 },
                { "type": "ground", "tile": "nope", "color": "#12" },
                { "type": "emitter", "particle": "ember", "start": 1.5, "end": 1.0 },
                { "type": "laser" },
                { "type": "beam", "start": "sometime" }
              ] }""", errors);
        assertNotNull(d, "a typo must not cost the whole effect");
        String all = String.join("\n", errors);
        assertTrue(all.contains("unknown key \"radus\""), all);
        assertTrue(all.contains("unknown tile \"nope\""), all);
        assertTrue(all.contains("not a colour"), all);
        assertTrue(all.contains("is not after start"), all);
        assertTrue(all.contains("unknown layer type \"laser\""), all);
        assertTrue(all.contains("\"sometime\" is not a time"), all);
        assertEquals(4, d.layers.size(), "the broken layers still load with defaults, the unknown type is skipped");

        errors.clear();
        assertNull(VfxParser.parse("x", "{ not json", errors));
        assertFalse(errors.isEmpty());
    }

    @Test
    void phaseNamesAndDefaultsResolve() {
        List<String> errors = new ArrayList<>();
        VfxDef d = VfxParser.parse("phases", """
            { "timeline": { "windup": 0.4, "peak": 1.0, "fade": 0.6 },
              "layers": [
                { "type": "ring", "start": "peak", "end": "fade", "color": [1, 0.5, 0] },
                { "type": "emitter", "particle": "smoke", "life": [3, 2], "size": 0.5 }
              ] }""", errors);
        assertTrue(errors.isEmpty(), errors.toString());
        VfxDef.Layer ring = d.layers.get(0);
        assertEquals(0.4f, ring.start, 1e-6);
        assertEquals(1.4f, ring.end, 1e-6);
        assertEquals(0xFF8000, ring.color);
        VfxDef.Emitter smoke = (VfxDef.Emitter) d.layers.get(1);
        assertEquals(2f, smoke.lifeMin, 1e-6);   // [max, min] is put in order
        assertEquals(3f, smoke.lifeMax, 1e-6);
        assertEquals(0.5f, smoke.sizeMin, 1e-6);
        assertTrue(smoke.gravity < 0, "smoke is buoyant by default");
        assertTrue(smoke.sizeEnd > 1, "smoke grows by default");
    }

    // ------------------------------------------------------------------ timeline maths

    @Test
    void envelopeRisesHoldsAndFalls() {
        VfxDef.Timeline tl = new VfxDef.Timeline(1, 2, 1);
        assertEquals(4f, tl.total(), 1e-6);
        assertEquals(VfxTimeline.Phase.WINDUP, VfxTimeline.phase(tl, 0.5f));
        assertEquals(VfxTimeline.Phase.PEAK, VfxTimeline.phase(tl, 1.0f));
        assertEquals(VfxTimeline.Phase.FADE, VfxTimeline.phase(tl, 3.5f));
        assertEquals(VfxTimeline.Phase.DONE, VfxTimeline.phase(tl, 4.0f));
        assertEquals(0f, VfxTimeline.envelope(tl, 0), 1e-6);
        assertEquals(0.5f, VfxTimeline.envelope(tl, 0.5f), 1e-6);
        assertEquals(1f, VfxTimeline.envelope(tl, 1.0f), 1e-6);
        assertEquals(1f, VfxTimeline.envelope(tl, 2.5f), 1e-6);
        assertEquals(0.5f, VfxTimeline.envelope(tl, 3.5f), 1e-6);
        assertEquals(0f, VfxTimeline.envelope(tl, 4.0f), 1e-6);
        assertEquals(0f, VfxTimeline.envelope(tl, 5f), 1e-6);
        float prev = -1;
        for (float t = 0; t <= 1; t += 0.01f) {   // monotonic through the wind-up: no flicker
            float e = VfxTimeline.envelope(tl, t);
            assertTrue(e >= prev - 1e-6);
            prev = e;
        }
    }

    @Test
    void windowSqueezesFadesLongerThanItself() {
        // a 0.4 s window with 1 s fades each way still peaks (at 1) in its middle and is 0 at both ends
        assertEquals(1f, VfxTimeline.window(0.2f, 0, 0.4f, 1, 1), 1e-5);
        assertEquals(0f, VfxTimeline.window(0f, 0, 0.4f, 1, 1), 1e-6);
        assertEquals(0f, VfxTimeline.window(0.4f, 0, 0.4f, 1, 1), 1e-6);
        assertEquals(0f, VfxTimeline.window(0.5f, 0, 0.4f, 0, 0), 1e-6);
        assertEquals(1f, VfxTimeline.window(0.0f, 0, 0.4f, 0, 0), 1e-6);   // zero fade = hard edge
    }

    @Test
    void layerDefaultsFollowTheTimeline() {
        VfxDef.Timeline tl = new VfxDef.Timeline(0.5f, 1, 0.5f);
        VfxDef.Ground whole = new VfxDef.Ground();          // starts with the effect, runs to its end
        assertEquals(0.5f, VfxTimeline.layerFadeIn(whole, tl), 1e-6);
        assertEquals(0.5f, VfxTimeline.layerFadeOut(whole, tl), 1e-6);
        assertEquals(VfxTimeline.envelope(tl, 0.25f), VfxTimeline.layerAlpha(whole, tl, 0.25f), 1e-6);
        VfxDef.Ground late = new VfxDef.Ground();
        late.start = 1.0f; late.end = 1.2f;
        assertEquals(0f, VfxTimeline.layerAlpha(late, tl, 0.9f), 1e-6);
        assertTrue(VfxTimeline.layerActive(late, tl, 1.1f));
        assertFalse(VfxTimeline.layerActive(late, tl, 1.3f));
    }

    @Test
    void growthEasesFromItsStartToFullSize() {
        VfxDef.Ring r = new VfxDef.Ring();
        r.start = 1; r.grow = 0.5f; r.growFrom = 0.2f;
        assertEquals(0.2f, VfxTimeline.layerScale(r, 0.5f), 1e-6);
        assertEquals(0.2f, VfxTimeline.layerScale(r, 1.0f), 1e-6);
        assertEquals(1f, VfxTimeline.layerScale(r, 1.5f), 1e-6);
        assertEquals(1f, VfxTimeline.layerScale(r, 9f), 1e-6);
        float mid = VfxTimeline.layerScale(r, 1.25f);
        assertTrue(mid > 0.6f && mid < 1f, "ease-out: past the halfway size at half the time, got " + mid);
        r.grow = 0;
        assertEquals(1f, VfxTimeline.layerScale(r, 0), 1e-6);
    }

    @Test
    void everyEasingRunsFromZeroToOne() {
        for (VfxEasing e : VfxEasing.values()) {
            assertEquals(0f, e.apply(0), 1e-4, e + " at 0");
            assertEquals(1f, e.apply(1), 1e-4, e + " at 1");
            assertEquals(0f, e.apply(-1), 1e-6, e + " clamps below");
            assertEquals(1f, e.apply(2), 1e-6, e + " clamps above");
            assertEquals(e, VfxEasing.byName(e.name().toLowerCase(java.util.Locale.ROOT)));
        }
        assertEquals(0.5f, VfxEasing.LINEAR.apply(0.5f), 1e-6);
        assertEquals(0.5f, VfxEasing.CUBIC_IN_OUT.apply(0.5f), 1e-5);
        assertTrue(VfxEasing.QUAD_OUT.apply(0.5f) > 0.5f && VfxEasing.QUAD_IN.apply(0.5f) < 0.5f);
        float maxBack = 0;
        for (float x = 0; x <= 1; x += 0.01f) maxBack = Math.max(maxBack, VfxEasing.BACK_OUT.apply(x));
        assertTrue(maxBack > 1.05f, "back_out overshoots (a ring snapping open), got " + maxBack);
        assertEquals(VfxEasing.CUBIC_OUT, VfxEasing.byName("cubic-out"));
        assertNull(VfxEasing.byName("wobbly"));
    }

    @Test
    void lightFallsOffSmoothlyToNothingAtItsRadius() {
        assertEquals(1f, VfxTimeline.falloff(0, 10), 1e-6);
        assertEquals(0f, VfxTimeline.falloff(10, 10), 1e-6);
        assertEquals(0f, VfxTimeline.falloff(11, 10), 1e-6);
        assertEquals(0f, VfxTimeline.falloff(1, 0), 1e-6);
        float prev = 2;
        for (float d = 0; d <= 10; d += 0.1f) {
            float f = VfxTimeline.falloff(d, 10);
            assertTrue(f <= prev + 1e-6 && f >= 0, "falloff must only decrease, at " + d);
            prev = f;
        }
        assertTrue(VfxTimeline.falloff(9.9f, 10) < 0.01f, "no pop when a unit walks out of the light");
    }

    @Test
    void spawnCountCarriesTheFraction() {
        float[] acc = { 0 };
        int total = 0;
        for (int i = 0; i < 100; i++) total += VfxTimeline.spawnCount(10, 0.025f, acc);
        assertEquals(25, total);   // 10/s for 2.5 s, a quarter particle per frame: none lost to rounding
        assertEquals(0, VfxTimeline.spawnCount(0, 1, acc));
        assertEquals(0, VfxTimeline.spawnCount(10, 0, acc));
    }
}
