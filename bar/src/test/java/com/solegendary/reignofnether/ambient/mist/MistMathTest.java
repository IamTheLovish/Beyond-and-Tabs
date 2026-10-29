package com.solegendary.reignofnether.ambient.mist;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The living valley mist's rules (MistMath) without the world: where mist pools (hollows, water, the foot of a cliff,
 * thinning on hilltops and high ground), how thick it is through the day (thick at dawn, burnt off by noon, back at
 * dusk, a little at night), how many sprites the camera's zoom allows (none at strategic zoom), and the look ranges
 * (alpha 0.15-0.35, 5-12 blocks, three layers 0.3-2.5 blocks up, 0.02-0.06 blocks a tick, a bright cool white-grey,
 * never pure white). Written from the design lovish approved on Oct 28 ("can we make the fog more animated and alive
 * or rich") before the code. Round 2 (eyes runs 40815085922 / 40815098025): r1's 21 wisps at alpha 0.05-0.18, 3-8
 * blocks, lit by the dawn sky, were nearly invisible from the RTS camera (mean pixel change 5.7 against the plain
 * _rts still) - more, bigger, brighter, denser, layered.
 * Round 3 (eyes run 40817045672): r2 changed the still by a mean 3.0 per pixel - still invisible. The cause was not
 * the numbers but the renderer: vanilla's particle shader discards every fragment under alpha 0.1, and r2 drew each
 * winding at half its alpha (0.075-0.175, times the sprite's own 0-0.9), so nearly every pixel of every wisp was
 * thrown away. r3 draws one quad with our own render type (no 0.1 cut, no depth write), at alpha 0.2-0.5.
 * Round 4 (eyes run 40819635724): r3 showed, as separate round cotton balls. r4: a veil - longer, bigger, fainter
 * wisps that overlap (expectedCover), and the dawn still just after sunrise.
 */
class MistMathTest {

    private static final int SEA = 63;
    private static final double EPS = 1e-9;
    private static final int N = MistMath.GRID_N, C = MistMath.GRID_R;

    // ------------------------------------------------------------------------------------------- height grids

    private static int[][] flat(int y) {
        int[][] h = new int[N][N];
        for (int[] row : h)
            java.util.Arrays.fill(row, y);
        return h;
    }

    private static boolean[][] dry() {
        return new boolean[N][N];
    }

    /** A bowl: every cell `rim` blocks above the centre. */
    private static int[][] bowl(int y, int rim) {
        int[][] h = flat(y + rim);
        h[C][C] = y;
        return h;
    }

    /** A cliff: every cell east of the centre (dx >= 1) `drop` blocks higher. */
    private static int[][] cliffFoot(int y, int drop) {
        int[][] h = flat(y);
        for (int dx = C + 1; dx < N; dx++)
            for (int dz = 0; dz < N; dz++)
                h[dx][dz] = y + drop;
        return h;
    }

    // ------------------------------------------------------------------------------------------- the terrain score

    @Test
    void theGridSpansAboutSixBlocksEachWay() {
        assertEquals(6, MistMath.GRID_R * MistMath.GRID_STEP, "the hollow test looks ~6 blocks round a column");
        assertEquals(2 * MistMath.GRID_R + 1, MistMath.GRID_N);
    }

    @Test
    void hollowDepthIsTheMeanOfTheOthersAboveTheCentre() {
        Object[][] rows = {
            // grid, expected depth
            { flat(SEA), 0.0 },
            { bowl(SEA, 3), 3.0 },
            { bowl(SEA, -2), -2.0 },                         // a hilltop: the others are below
            { cliffFoot(SEA, 6), 6.0 * (C * N) / (N * N - 1) },
        };
        for (Object[] r : rows)
            assertEquals((double) r[1], MistMath.hollowDepth((int[][]) r[0]), 1e-6, "hollow depth");
    }

    @Test
    void cliffDropIsTheHighestRiseNextToTheCentre() {
        assertEquals(0, MistMath.cliffDrop(flat(SEA)));
        assertEquals(5, MistMath.cliffDrop(cliffFoot(SEA, 5)));
        int[][] far = flat(SEA);
        far[N - 1][C] = SEA + 9;                             // a pillar 6 blocks off is not a cliff over this column
        assertEquals(0, MistMath.cliffDrop(far));
        assertEquals(0, MistMath.cliffDrop(bowl(SEA, -4)), "a drop below is not a cliff above");
    }

    @Test
    void aCliffCountsFromThreeBlocks() {
        Object[][] rows = { { 0, 0.0 }, { 2, 0.0 }, { 3, 0.35 }, { 5, 0.45 }, { 7, 0.55 }, { 30, 0.6 } };
        for (Object[] r : rows)
            assertEquals((double) r[1], MistMath.cliffScore((int) r[0]), 1e-9, "cliff score at drop " + r[0]);
    }

    @Test
    void waterCountsAtTheCentreOrTheNextCell() {
        boolean[][] w = dry();
        assertFalse(MistMath.nearWater(w));
        w[C][C] = true;
        assertTrue(MistMath.nearWater(w), "over water");
        w = dry();
        w[C + 1][C - 1] = true;
        assertTrue(MistMath.nearWater(w), "a cell away (2 blocks)");
        w = dry();
        w[C + 2][C] = true;
        assertFalse(MistMath.nearWater(w), "two cells away (4 blocks) is dry ground");
    }

    @Test
    void altitudeThinsTheMistOnHighGround() {
        Object[][] rows = { { SEA - 10, 1.0 }, { SEA, 1.0 }, { SEA + 12, 1.0 }, { SEA + 30, 0.5 }, { SEA + 48, 0.0 },
            { SEA + 90, 0.0 } };
        for (Object[] r : rows)
            assertEquals((double) r[1], MistMath.altitudeThin((int) r[0], SEA), 1e-9, "altitude " + r[0]);
    }

    @Test
    void openMeadowStillShowsSome() {
        assertEquals(0.3, MistMath.BASE_SCORE, EPS, "r2: a flat meadow at dawn draws a visible sheet, hollows more");
    }

    @Test
    void scoreTable() {
        Object[][] rows = {
            // what, heights, water, expected
            { "flat open meadow: a little", flat(SEA), dry(), MistMath.BASE_SCORE },
            { "a 1-block dip", bowl(SEA, 1), dry(), MistMath.BASE_SCORE + 0.2 },
            { "a 2-block hollow", bowl(SEA, 2), dry(), MistMath.BASE_SCORE + 0.4 },
            { "a 3-block pit: hollow and walls, full", bowl(SEA, 3), dry(), 1.0 },
            { "a hilltop 3 above its ground", bowl(SEA, -3), dry(), 0.0 },
            { "high plateau (sea + 48)", flat(SEA + 48), dry(), 0.0 },
            { "half way up (sea + 30)", flat(SEA + 30), dry(), MistMath.BASE_SCORE * 0.5 },
        };
        for (Object[] r : rows)
            assertEquals((double) r[3], MistMath.score((int[][]) r[1], (boolean[][]) r[2], SEA), 1e-6, (String) r[0]);
        boolean[][] w = dry();
        w[C][C + 1] = true;
        assertEquals(MistMath.BASE_SCORE + 0.45, MistMath.score(flat(SEA), w, SEA), 1e-6, "flat ground by water");
        assertEquals(1.0, MistMath.score(cliffFoot(SEA, 5), dry(), SEA), 1e-6, "the foot of a 5-block wall: full");
    }

    @Test
    void poolsScoreAboveOpenGroundAndNeverLeaveZeroToOne() {
        double open = MistMath.score(flat(SEA), dry(), SEA);
        assertTrue(MistMath.score(bowl(SEA, 2), dry(), SEA) > open, "a hollow beats the meadow");
        assertTrue(MistMath.score(cliffFoot(SEA, 3), dry(), SEA) > open, "a terrace foot beats the meadow");
        assertTrue(MistMath.score(cliffFoot(SEA, 2), dry(), SEA) < MistMath.score(cliffFoot(SEA, 3), dry(), SEA),
            "3 blocks is where a terrace wall starts to hold mist");
        int[][][] grids = { flat(SEA), bowl(SEA, 20), bowl(SEA, -20), cliffFoot(SEA, 40), flat(SEA + 200) };
        for (int[][] g : grids) {
            boolean[][] wet = dry();
            wet[C][C] = true;
            for (boolean[][] w : new boolean[][][] { dry(), wet }) {
                double s = MistMath.score(g, w, SEA);
                assertTrue(s >= 0 && s <= 1, "score in 0..1: " + s);
            }
        }
    }

    // ------------------------------------------------------------------------------------------- time of day

    @Test
    void densityThroughTheDay() {
        Object[][] rows = {
            { 23000L, 1.0 }, { 23500L, 1.0 }, { 0L, 1.0 }, { 1500L, 1.0 },    // dawn: thick
            { 2750L, 0.5 },                                                   // burning off
            { 4000L, 0.0 }, { 6000L, 0.0 }, { 9000L, 0.0 },                   // late morning to afternoon: none
            { 10250L, 0.35 },                                                 // rising again
            { 11500L, 0.7 }, { 12500L, 0.7 }, { 13500L, 0.7 },               // dusk
            { 14250L, 0.475 },
            { 15000L, 0.25 }, { 18000L, 0.25 }, { 21500L, 0.25 },             // night: a little
            { 22250L, 0.625 },
            { 24000L + 1000L, 1.0 }, { -1000L, 1.0 }, { 48000L + 6000L, 0.0 }, // wraps either way
        };
        for (Object[] r : rows)
            assertEquals((double) r[1], MistMath.density((long) r[0]), 1e-9, "density at " + r[0]);
    }

    @Test
    void densityNeverLeavesZeroToOne() {
        for (long t = -24000; t < 48000; t += 37) {
            double d = MistMath.density(t);
            assertTrue(d >= 0 && d <= 1, "density " + d + " at " + t);
        }
    }

    // ------------------------------------------------------------------------------------------- the zoom cap

    @Test
    void capShrinksWithZoomAndIsZeroAtStrategic() {
        Object[][] rows = {
            // zoom, strategic view, expected (base 30)
            { 10f, false, 30 }, { 30f, false, 30 }, { 40f, false, 23 }, { 50f, false, 15 }, { 60f, false, 8 },
            { 70f, false, 0 }, { 120f, false, 0 },
            { 40f, true, 0 }, { 20f, true, 0 },     // the icons are on: no mist whatever the zoom
        };
        for (Object[] r : rows)
            assertEquals((long) (int) r[2], MistMath.cap(30, (float) r[0], (boolean) r[1]), "cap at zoom " + r[0] + " strategic " + r[1]);
    }

    @Test
    void thickEnoughToReadFromTheRtsCamera() {
        int close = MistMath.cap(MistMath.BASE_CAP, 30f, false);
        assertTrue(close >= 80 && close <= 150, "80-150 wisps round the focus at the default zoom: " + close);
        assertEquals(0, MistMath.cap(MistMath.BASE_CAP, 85f, true), "still none at strategic zoom");
        // the spawner fills the dawn cap within the eyes still's 8 s at 20 ticks a second
        int perEightSeconds = MistMath.PER_PASS * (160 / MistMath.PERIOD);
        assertTrue(perEightSeconds >= MistMath.BASE_CAP, "fills in 8 s: " + perEightSeconds);
        assertTrue(MistMath.SAMPLES >= 2 * MistMath.PER_PASS, "enough columns tried for the score to choose");
    }

    @Test
    void wantedFollowsDensityAndSpawnsAFewAtATime() {
        assertEquals(0, MistMath.wanted(24, 0.0));
        assertEquals(24, MistMath.wanted(24, 1.0));
        assertEquals(6, MistMath.wanted(24, 0.25));
        assertEquals(2, MistMath.toSpawn(0, 24, 2));
        assertEquals(1, MistMath.toSpawn(23, 24, 2));
        assertEquals(0, MistMath.toSpawn(30, 24, 2), "never negative");
    }

    @Test
    void radiusFollowsTheView() {
        assertEquals(12, MistMath.radius(5), EPS);
        assertEquals(27, MistMath.radius(30), EPS);
        assertEquals(48, MistMath.radius(200), EPS);
    }

    @Test
    void focusIsWhereTheCameraLooksOnTheGround() {
        // looking straight down from 30 up: the eye's own column
        double[] f = MistMath.focus(5, 100, 7, 0, 90, 70);
        assertEquals(5, f[0], 1e-6);
        assertEquals(70, f[1], 1e-6);
        assertEquals(7, f[2], 1e-6);
        // 45 degrees down facing +z (yaw 0) from 20 up: 20 blocks ahead along +z
        f = MistMath.focus(0, 90, 0, 0, 45, 70);
        assertEquals(0, f[0], 1e-6);
        assertEquals(20, f[2], 1e-6);
        // yaw -90 faces +x
        f = MistMath.focus(0, 90, 0, -90, 45, 70);
        assertEquals(20, f[0], 1e-6);
        assertEquals(0, f[2], 1e-6);
        // at the horizon: the eye's column
        f = MistMath.focus(3, 90, 4, 0, 0, 70);
        assertEquals(3, f[0], 1e-6);
        assertEquals(4, f[2], 1e-6);
    }

    // ------------------------------------------------------------------------------------------- the look

    @Test
    void peakAlphaStaysSoft() {
        Object[][] rows = { { 0.0, 0.0, 0.12 }, { 1.0, 1.0, 0.25 }, { 0.5, 0.5, 0.1525 }, { 2.0, 3.0, 0.25 },
            { -1.0, 1.0, 0.12 } };
        for (Object[] r : rows)
            assertEquals((double) r[2], MistMath.peakAlpha((double) r[0], (double) r[1]), 1e-9,
                "alpha at score " + r[0] + " density " + r[1]);
    }

    @Test
    void evenTheFaintestWispClearsVanillasParticleCut() {
        // r2's lesson: a sprite's centre (texture alpha SPRITE_PEAK times the vertex alpha) must stay over the 0.1
        // that vanilla's particle shader discards, so the mist still shows if it is ever drawn through that shader
        assertEquals(0.1, MistMath.VANILLA_PARTICLE_CUT, EPS);
        assertTrue(MistMath.ALPHA_MIN * MistMath.SPRITE_PEAK > MistMath.VANILLA_PARTICLE_CUT,
            "faintest centre " + MistMath.ALPHA_MIN * MistMath.SPRITE_PEAK);
    }

    @Test
    void aDawnMeadowIsAVeilNotABlanketNorCottonBalls() {
        // r3 (eyes run 40819635724) showed: 120 round wisps of alpha 0.2-0.5, 5-12 blocks, read as separate cotton
        // balls - about 18% of the ground under mist, in discs. r4: bigger, longer (stretched along the wind), fainter
        // each, so they overlap into a veil. The model: cover = 1 - exp(-wisps x quad area x mean alpha / spawn disc);
        // a veil is 20-45% of open meadow at the default zoom (pillar 2: units still read through it)
        double cover = MistMath.expectedCover(30f, MistMath.BASE_SCORE);
        assertTrue(cover >= 0.2 && cover <= 0.45, "open meadow at dawn, zoom 30: " + cover);
        // and a wisp overlaps several others: no wisp stands alone as a disc
        assertTrue(MistMath.overlaps(30f) >= 4, "overlaps " + MistMath.overlaps(30f));
        assertTrue(MistMath.ASPECT >= 1.5 && MistMath.ASPECT <= 2.2, "drawn longer along the wind: " + MistMath.ASPECT);
    }

    @Test
    void fadesInAndOutOverSeconds() {
        int life = 500;
        assertTrue(MistMath.FADE_IN >= 40 && MistMath.FADE_OUT >= 40, "fades last 2 s or more");
        assertEquals(0, MistMath.fade(0, life), EPS);
        assertEquals(0.5, MistMath.fade(MistMath.FADE_IN / 2, life), EPS);
        assertEquals(1, MistMath.fade(MistMath.FADE_IN, life), EPS);
        assertEquals(1, MistMath.fade(life / 2, life), EPS);
        assertEquals(0.5, MistMath.fade(life - MistMath.FADE_OUT / 2, life), EPS);
        assertEquals(0, MistMath.fade(life, life), EPS);
        assertEquals(0, MistMath.fade(life + 10, life), EPS);
        double prev = -1;
        for (int a = 0; a <= MistMath.FADE_IN; a++) {
            double v = MistMath.fade(a, life);
            assertTrue(v >= prev, "the fade-in never dips");
            prev = v;
        }
    }

    @Test
    void lifetimesLastTwentyToThirtyFiveSeconds() {
        assertEquals(400, MistMath.lifetime(0));
        assertEquals(700, MistMath.lifetime(1));
        assertEquals(550, MistMath.lifetime(0.5));
    }

    @Test
    void sizesAreBigAndGrowSlowly() {
        assertEquals(8, MistMath.startSize(0), EPS);
        assertEquals(13, MistMath.startSize(1), EPS);
        assertEquals(5, MistMath.size(5, 0, 500), EPS);
        assertEquals(5.5, MistMath.size(5, 250, 500), EPS);
        assertEquals(6, MistMath.size(5, 500, 500), EPS);
        for (double r = 0; r <= 1; r += 0.05)
            for (int age = 0; age <= 700; age += 50) {
                double s = MistMath.size(MistMath.startSize(r), age, 700);
                assertTrue(s >= 8 && s <= 15.6, "8-15.6 blocks across (x ASPECT along the wind): " + s);
            }
    }

    @Test
    void mistLiesLow() {
        // three layers (0.5, 1.3, 2.2) so the sheet has depth, each jittered +-0.2
        assertEquals(0.5, MistMath.hover(0, 0.5), EPS);
        assertEquals(1.3, MistMath.hover(0.5, 0.5), EPS);
        assertEquals(2.2, MistMath.hover(0.99, 0.5), EPS);
        assertEquals(0.3, MistMath.hover(0, 0), EPS);
        assertEquals(2.4, MistMath.hover(1, 1), EPS);
        java.util.Set<Long> bands = new java.util.HashSet<>();
        for (double r = 0; r <= 1; r += 0.01) {
            double h = MistMath.hover(r, 0.5);
            assertTrue(h >= 0.3 && h <= 2.5, "low: " + h);
            bands.add(Math.round(h * 10));
        }
        assertEquals(3, bands.size(), "three layers");
    }

    @Test
    void driftIsSlowAndAlongTheWind() {
        assertEquals(0.02, MistMath.driftSpeed(0), EPS);
        assertEquals(0.06, MistMath.driftSpeed(1), EPS);
        double[] d = MistMath.drift(0, 0.04);
        assertEquals(0.04, d[0], EPS);
        assertEquals(0, d[1], EPS);
        d = MistMath.drift(Math.PI / 2, 0.03);
        assertEquals(0, d[0], 1e-12);
        assertEquals(0.03, d[1], EPS);
        for (double h = 0; h < 7; h += 0.3) {
            double[] v = MistMath.drift(h, MistMath.driftSpeed(0.7));
            assertEquals(MistMath.driftSpeed(0.7), Math.hypot(v[0], v[1]), 1e-12, "speed kept at any heading");
        }
    }

    @Test
    void colourIsCoolGreyByDayWarmAtDuskNeverPureWhite() {
        float[] noon = MistMath.colour(6000), dawn = MistMath.colour(0), dusk = MistMath.colour(12500),
            night = MistMath.colour(18000);
        assertTrue(noon[2] > noon[0], "cool: more blue than red by day");
        assertTrue(dawn[2] > dawn[0], "cool at dawn");
        assertTrue(dusk[0] > dusk[2], "warm at dusk: more red than blue");
        assertTrue(night[2] > night[0], "cool at night");
        for (long t = 0; t < 24000; t += 100)
            for (float c : MistMath.colour(t))
                assertTrue(c <= 0.97f, "never pure white: " + c + " at " + t);
        // r2: drawn full-bright (the dawn sky's lightmap dimmed r1 to the grass's own value), so the colour carries the
        // light: a bright white-grey wherever there is mist by day, dim at night (no glowing fog in the dark)
        for (long t : new long[] { 0, 1000, 23000, 23500, 12000, 13000 })
            for (float c : MistMath.colour(t))
                assertTrue(c >= 0.8f, "bright enough to read against dark grass: " + c + " at " + t);
        for (float c : night)
            assertTrue(c <= 0.45f, "dim at night: " + c);
    }

    @Test
    void unitsMovingFastPushTheMistAside() {
        // a unit 1 block west of a 6-block sprite's centre, running: the push points east (away from it)
        double[] p = MistMath.push(1, 0, 0.25, 6);
        assertTrue(p[0] > 0, "pushed away from the unit");
        assertEquals(0, p[1], EPS);
        assertTrue(p[0] <= MistMath.MAX_PUSH + EPS, "a nudge, not a blast");
        assertEquals(0, MistMath.push(1, 0, 0.05, 6)[0], EPS, "walking slowly: no push");
        assertEquals(0, MistMath.push(5, 0, 0.3, 6)[0], EPS, "outside the sprite: no push");
        assertTrue(MistMath.push(0.5, 0, 0.3, 6)[0] > MistMath.push(2.5, 0, 0.3, 6)[0], "nearer pushes harder");
        double[] centre = MistMath.push(0, 0, 0.3, 6);
        assertTrue(Double.isFinite(centre[0]) && Double.isFinite(centre[1]), "a unit dead centre: no NaN");
    }

    // ------------------------------------------------------------------------------------------- the eyes switch

    @Test
    void eyesKeepMistOutOfTheMeasuredStillsUnlessAsked() {
        Object[][] rows = {
            // ambientLifeOff, bat.eyes.mist, allowed
            { false, null, true },         // normal play
            { true, null, false },         // the structure photos: out of the measured crops
            { true, "false", false },
            { true, "true", true },        // the mist pilot run asks for it
            { true, "TRUE", true },
            { false, "true", true },
        };
        for (Object[] r : rows)
            assertEquals(r[2], MistMath.allowed((boolean) r[0], (String) r[1]), "allowed " + r[0] + " / " + r[1]);
    }

    @Test
    void theDawnStillIsInTheThickOfIt() {
        assertEquals(1.0, MistMath.density(MistMath.EYES_DAWN_TIME), EPS);
        // r3's 23500 was before sunrise: with shaders the still was near black. Just after sunrise, lit and thick
        long t = Math.floorMod(MistMath.EYES_DAWN_TIME, 24000L);
        assertTrue(t >= 0 && t <= 1000, "just after sunrise: " + t);
    }

    // ------------------------------------------------------------------------------------------- the volume (r6)

    @Test
    void theMistIsThickerInTheFogOfWar() {
        // lovish, Oct 29: "the fod should be thicker in fog of war compared to not fog of war"
        assertEquals(1.0, MistMath.fogBoost(true, true, true), EPS, "in sight: the mist as it is");
        assertEquals(1.0, MistMath.fogBoost(false, false, false), EPS, "fog of war off: nothing is hidden");
        double explored = MistMath.fogBoost(true, false, true), unexplored = MistMath.fogBoost(true, false, false);
        assertTrue(explored >= 2.0, "out of sight but explored: thicker " + explored);
        assertTrue(unexplored > explored, "never explored: thickest " + unexplored);
        assertTrue(unexplored <= 4.0, "but never a wall " + unexplored);
    }

    @Test
    void theVolumeFadesOutAsTheCameraClimbs() {
        // the volume pass follows the same zoom rule as the sprites: full to the default zoom, none at strategic zoom
        assertEquals(1.0, MistMath.zoomFade(20f, false), EPS);
        assertEquals(1.0, MistMath.zoomFade(MistMath.ZOOM_FULL, false), EPS);
        assertEquals(0.5, MistMath.zoomFade((MistMath.ZOOM_FULL + MistMath.ZOOM_NONE) / 2, false), EPS);
        assertEquals(0.0, MistMath.zoomFade(MistMath.ZOOM_NONE, false), EPS);
        assertEquals(0.0, MistMath.zoomFade(20f, true), EPS);
    }

    @Test
    void deepInTheHollowsThinOnTheRises() {
        // the volume's own optical depth model (bat_mist.fsh): exponential height fog over the mist floor, so a hollow
        // fills and a rise pokes out. Open ground at the floor in sight at dawn: a veil, 25-50% (units read through it)
        double atFloor = MistMath.volumeCover(0, 1.0, 1.0), rise = MistMath.volumeCover(2.5, 1.0, 1.0);
        double hollow = MistMath.volumeCover(-3, 1.0, 1.0);
        assertTrue(atFloor >= 0.25 && atFloor <= 0.5, "at the floor " + atFloor);
        assertTrue(rise < atFloor * 0.6, "a rise thins it " + rise);
        assertTrue(hollow >= 0.7, "a hollow fills " + hollow);
        double fogged = MistMath.volumeCover(0, 1.0, MistMath.fogBoost(true, false, false));
        assertTrue(fogged >= 0.7, "in the fog of war, thick " + fogged);
        assertTrue(MistMath.volumeCover(-10, 1.0, 4.0) <= MistMath.MAX_COVER + EPS, "capped " + MistMath.MAX_COVER);
    }
}
