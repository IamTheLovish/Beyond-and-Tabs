package com.solegendary.reignofnether.unit.modelling.motion;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Combat feel, part 3 (lovish approved Oct 28): when a unit dies its bones get a short physical collapse instead of
 * vanilla's red flash and tip-over - a velocity from the killing blow's direction plus a little randomness, gravity and
 * ground contact for ~1.2-1.5 s, then it settles and fades. Client only and cheap: at most N concurrent collapses, past
 * the cap the plain death plays.
 */
class DeathCollapseTest {

    private static final float DEG = (float) (Math.PI / 180);
    private static final int[] BONES = {DeathCollapse.BODY, DeathCollapse.HEAD, DeathCollapse.ARM_R, DeathCollapse.ARM_L,
        DeathCollapse.LEG_R, DeathCollapse.LEG_L, DeathCollapse.ARMS};

    private static float[] started(float dir, float strength, int seed) {
        float[] s = new float[DeathCollapse.STRIDE];
        DeathCollapse.start(s, 0, dir, strength, seed, 3f, 10f);
        return s;
    }

    private static float angleBetween(float a, float b) {
        float d = (float) Math.IEEEremainder(a - b, Math.PI * 2);
        return Math.abs(d);
    }

    @Test
    void thePoolCapsConcurrentCollapsesAndFallsBackPastIt() {
        DeathCollapsePool pool = new DeathCollapsePool(DeathCollapse.MAX_CONCURRENT);
        for (int id = 0; id < DeathCollapse.MAX_CONCURRENT; id++)
            assertTrue(pool.claim(id) >= 0, "collapse " + id + " fits");
        assertEquals(-1, pool.claim(9999), "past the cap: the plain death plays");
        assertEquals(pool.base(7), pool.claim(7), "claiming a live one again gives the same slot");
        assertEquals(DeathCollapse.MAX_CONCURRENT, pool.size());
        pool.release(7);
        assertEquals(-1, pool.base(7));
        assertTrue(pool.claim(9999) >= 0, "a freed slot is reused");
        assertTrue(pool.slab().length >= DeathCollapse.MAX_CONCURRENT * DeathCollapse.STRIDE);
        pool.clear();
        assertEquals(0, pool.size());
        assertTrue(DeathCollapse.MAX_CONCURRENT >= 20 && DeathCollapse.MAX_CONCURRENT <= 64, "a real cap, not a token one");
    }

    @Test
    void itFallsTheWayTheBlowPushedIt() {
        float[] dirs = {0f, (float) Math.PI / 2, (float) Math.PI, -(float) Math.PI / 2, 2f, -2.6f};
        for (float dir : dirs)
            for (int seed = 0; seed < 20; seed++) {
                float[] s = started(dir, 1f, seed);
                DeathCollapse.advance(s, 0, 1.0f);
                assertTrue(angleBetween(DeathCollapse.fallDir(s, 0), dir) <= 0.5f,
                    "falls within 0.5 rad of the push " + dir + " (seed " + seed + "): " + DeathCollapse.fallDir(s, 0));
                assertTrue(DeathCollapse.slide(s, 0) > 0f, "and slides a little along it");
                assertTrue(DeathCollapse.slide(s, 0) < 1.5f, "but never far: " + DeathCollapse.slide(s, 0));
            }
    }

    @Test
    void itTopplesOverAboutHalfASecondNotInstantly() {
        for (int seed = 0; seed < 20; seed++) {
            float[] s = started(1f, 1f, seed);
            DeathCollapse.advance(s, 0, 0.1f);
            assertTrue(DeathCollapse.tilt(s, 0) < 50f * DEG, "not a snap: " + DeathCollapse.tilt(s, 0) / DEG);
            DeathCollapse.advance(s, 0, 0.9f);
            assertTrue(DeathCollapse.tilt(s, 0) > 80f * DEG, "down by 0.9 s: " + DeathCollapse.tilt(s, 0) / DEG);
        }
        float[] soft = started(0f, 0f, 3), hard = started(0f, 2f, 3);
        DeathCollapse.advance(soft, 0, 0.25f);
        DeathCollapse.advance(hard, 0, 0.25f);
        assertTrue(DeathCollapse.tilt(hard, 0) > DeathCollapse.tilt(soft, 0), "a harder blow knocks it down faster");
    }

    @Test
    void theGroundStopsItAndNothingSinksThroughBeforeTheFade() {
        for (int seed = 0; seed < 30; seed++) {
            float[] s = started(seed * 0.7f, 0.5f + (seed % 4) * 0.5f, seed);
            for (int f = 1; f <= 84; f++) {
                float t = f / 60f;
                DeathCollapse.advance(s, 0, t);
                float tilt = DeathCollapse.tilt(s, 0);
                assertTrue(tilt <= 91f * DEG, "the body stops on the ground (seed " + seed + ", t " + t + "): " + tilt / DEG);
                assertTrue(tilt >= -5f * DEG, "never tips back through its feet: " + tilt / DEG);
                if (t < DeathCollapse.SINK_START)
                    assertTrue(DeathCollapse.lift(s, 0) >= -1e-4f, "not under the ground before the fade: " + DeathCollapse.lift(s, 0));
            }
        }
    }

    @Test
    void itSettlesBeforeItFades() {
        for (int seed = 0; seed < 30; seed++) {
            float[] s = started(seed * 1.3f, 1f + (seed % 3) * 0.5f, seed);
            float[] a = new float[DeathCollapse.OUT], b = new float[DeathCollapse.OUT];
            DeathCollapse.advance(s, 0, DeathCollapse.SINK_START - 0.1f);
            DeathCollapse.pose(s, 0, a);
            float tiltA = DeathCollapse.tilt(s, 0);
            DeathCollapse.advance(s, 0, DeathCollapse.SINK_START);
            DeathCollapse.pose(s, 0, b);
            assertEquals(tiltA, DeathCollapse.tilt(s, 0), 1.5f * DEG, "the body lies still (seed " + seed + ")");
            for (int i = 0; i < a.length; i++)
                assertEquals(a[i], b[i], 0.03f, "bone channel " + i + " lies still (seed " + seed + ")");
        }
    }

    @Test
    void whenItLiesFlatNoLimbPokesThroughTheGround() {
        for (int seed = 0; seed < 30; seed++) {
            float[] s = started(seed * 0.9f, 1.5f, seed);
            DeathCollapse.advance(s, 0, DeathCollapse.SINK_START);
            assertTrue(DeathCollapse.tilt(s, 0) > 80f * DEG);
            for (int bone : BONES)
                assertTrue(DeathCollapse.swing(s, 0, bone) <= 0.06f,
                    "bone " + bone + " rests on the ground, not through it (seed " + seed + "): " + DeathCollapse.swing(s, 0, bone));
        }
    }

    @Test
    void theLimbsReallyFlopTheBodyIsNotAStiffPlank() {
        int floppy = 0;
        for (int seed = 0; seed < 20; seed++) {
            float[] s = started(0.4f, 1f, seed);
            float most = 0f;
            for (int f = 1; f <= 60; f++) {
                DeathCollapse.advance(s, 0, f / 60f);
                for (int bone : new int[] {DeathCollapse.ARM_R, DeathCollapse.ARM_L, DeathCollapse.HEAD})
                    most = Math.max(most, Math.abs(DeathCollapse.swing(s, 0, bone)) + Math.abs(DeathCollapse.across(s, 0, bone)));
            }
            if (most > 0.3f)
                floppy++;
        }
        assertTrue(floppy >= 18, "the arms and head swing on their own in nearly every death: " + floppy + " of 20");
        float[] a = started(0.4f, 1f, 1), b = started(0.4f, 1f, 2);
        DeathCollapse.advance(a, 0, 0.5f);
        DeathCollapse.advance(b, 0, 0.5f);
        float[] pa = new float[DeathCollapse.OUT], pb = new float[DeathCollapse.OUT];
        DeathCollapse.pose(a, 0, pa);
        DeathCollapse.pose(b, 0, pb);
        boolean differ = false;
        for (int i = 0; i < pa.length; i++)
            differ |= Math.abs(pa[i] - pb[i]) > 0.02f;
        assertTrue(differ, "a little randomness: two deaths from the same blow differ");
    }

    @Test
    void noBoneSnapsFromOneFrameToTheNext() {
        // a flop, not a pop: at 60 fps no bone turns more than 15 degrees between two frames, whatever the blow
        float worst = 0f;
        String where = "";
        for (int seed = 0; seed < 200; seed++) {
            float[] s = started(seed * 0.37f, 0.5f + (seed % 5) * 0.5f, seed);
            float[] prev = new float[DeathCollapse.BONES], prevAcross = new float[DeathCollapse.BONES];
            for (int f = 1; f <= 84; f++) {
                DeathCollapse.advance(s, 0, f / 60f);
                for (int bone : BONES) {
                    float a = DeathCollapse.swing(s, 0, bone), c = DeathCollapse.across(s, 0, bone);
                    float d = Math.max(Math.abs(a - prev[bone]), Math.abs(c - prevAcross[bone]));
                    if (d > worst) {
                        worst = d;
                        where = "seed " + seed + " bone " + bone + " at " + f / 60f + " s";
                    }
                    prev[bone] = a;
                    prevAcross[bone] = c;
                }
            }
        }
        assertTrue(worst <= 15f * DEG, "worst jump " + worst / DEG + " deg (" + where + ")");
    }

    @Test
    void itSinksAwayAtTheEndAndIsDone() {
        float[] s = started(0f, 1f, 5);
        DeathCollapse.advance(s, 0, DeathCollapse.SINK_START);
        float before = DeathCollapse.lift(s, 0);
        assertTrue(!DeathCollapse.done(s, 0));
        DeathCollapse.advance(s, 0, DeathCollapse.DURATION);
        assertTrue(DeathCollapse.lift(s, 0) <= before - 9f, "sunk by its sink depth: " + before + " -> " + DeathCollapse.lift(s, 0));
        assertTrue(DeathCollapse.done(s, 0));
        assertTrue(DeathCollapse.DURATION >= 1.2f && DeathCollapse.DURATION <= 1.5f, "the approved ~1.2-1.5 s");
    }

    @Test
    void theSameDeathLooksTheSameAtAnyFrameRate() {
        float[] slow = started(2.2f, 1.3f, 11), fast = started(2.2f, 1.3f, 11), jump = started(2.2f, 1.3f, 11);
        for (int f = 1; f <= 20; f++)
            DeathCollapse.advance(slow, 0, f / 20f);
        for (int f = 1; f <= 144; f++)
            DeathCollapse.advance(fast, 0, f / 144f);
        DeathCollapse.advance(jump, 0, 1f);
        DeathCollapse.advance(jump, 0, 1f);   // the same frame posed twice (a shader pack's shadow pass) changes nothing
        DeathCollapse.advance(jump, 0, 0.5f); // and time never runs backwards
        assertArrayEquals(slow, fast, 1e-4f);
        assertArrayEquals(slow, jump, 1e-4f);
    }

    @Test
    void anyBlowGivesAFinitePose() {
        float[] strengths = {0f, 1f, 3f, 50f, Float.NaN, -2f, Float.POSITIVE_INFINITY};
        float[] dirs = {0f, 7f, -30f, Float.NaN};
        float[] out = new float[DeathCollapse.OUT];
        for (float st : strengths)
            for (float dir : dirs)
                for (int seed = -3; seed < 3; seed++) {
                    float[] s = started(dir, st, seed);
                    for (int f = 1; f <= 90; f += 7) {
                        DeathCollapse.advance(s, 0, f / 60f);
                        DeathCollapse.pose(s, 0, out);
                        for (float v : out)
                            assertTrue(Float.isFinite(v) && Math.abs(v) <= 4f, "finite, sane channel: " + v);
                        assertTrue(Float.isFinite(DeathCollapse.tilt(s, 0)) && Float.isFinite(DeathCollapse.fallDir(s, 0))
                            && Float.isFinite(DeathCollapse.slide(s, 0)) && Float.isFinite(DeathCollapse.lift(s, 0)));
                    }
                }
    }

    @Test
    void thePoseLeavesTheRootToTheRendererAndMovesTheBones() {
        float[] s = started(0.3f, 1f, 4);
        DeathCollapse.advance(s, 0, 0.35f);
        float[] out = new float[DeathCollapse.OUT];
        java.util.Arrays.fill(out, 99f);   // pose overwrites every channel
        DeathCollapse.pose(s, 0, out);
        for (int ch = 0; ch < DeathCollapse.CH; ch++)
            assertEquals(0f, out[ch], 0f, "root channel " + ch + " (the tip goes on the pose stack)");
        float moved = 0f;
        for (int bone : BONES)
            moved += Math.abs(out[bone * DeathCollapse.CH]) + Math.abs(out[bone * DeathCollapse.CH + 2]);
        assertTrue(moved > 0.2f, "the bones are posed: " + moved);
    }

    @Test
    void manyCollapsesAreCheap() {
        // the cap's worth of collapses, stepped frame by frame through their whole life: well under a millisecond a frame
        DeathCollapsePool pool = new DeathCollapsePool(DeathCollapse.MAX_CONCURRENT);
        float[] out = new float[DeathCollapse.OUT];
        for (int id = 0; id < DeathCollapse.MAX_CONCURRENT; id++)
            DeathCollapse.start(pool.slab(), pool.claim(id), id, 1f, id, 3f, 10f);
        long t0 = System.nanoTime();
        for (int f = 1; f <= 84; f++)
            for (int id = 0; id < DeathCollapse.MAX_CONCURRENT; id++) {
                DeathCollapse.advance(pool.slab(), pool.base(id), f / 60f);
                DeathCollapse.pose(pool.slab(), pool.base(id), out);
            }
        double msPerFrame = (System.nanoTime() - t0) / 1e6 / 84;
        assertTrue(msPerFrame < 2.0, "40 collapses cost " + msPerFrame + " ms a frame (cold JIT included)");
    }
}
