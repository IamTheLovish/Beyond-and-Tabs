package com.solegendary.reignofnether.postfx;

/**
 * Grass tint noise (lovish Oct 22 20:23, "yes this sounds good"; Oct 28 "the grass needs a lot more work, looks like
 * 1 bland color from rts view"): the biome colour of grass and foliage varies across the world the way a meadow does
 * seen from above, so a big grass field is not one flat green from the RTS camera. A block texture cannot do this:
 * the pack's grass is one tile per block, and any patch drawn into it repeats on every block; a field's large-scale
 * variation has to come from the colour the game multiplies the texture by.
 *
 * <p>Round 3 (Oct 28), from a fresh critic's scores on tools/grass_tint_preview.py renders beside the reference
 * (the CC0 scan Grass004 - one meadow: mixed green blades, straw, dark gaps): round 2 varied at one blurred
 * 20-40 block scale only (4/10 zoomed out, 3/10 at the default zoom), its dark patches went teal, and it had no straw.
 * Now, each on its own deterministic field:
 * <ul>
 *   <li><b>Olive base</b>: the plains green moved toward the reference's olive (red +7%, green -4%).</li>
 *   <li><b>Value</b> (+-8%): octaves at 48, 14 and 4.5 blocks through a contrast curve.</li>
 *   <li><b>Mottle</b> (+-7% value, +-6% hue): 3 and 1.5-block noise - the scale the default RTS zoom (~30 blocks
 *       in view) actually shows, between neighbouring clumps.</li>
 *   <li><b>Dry / lush moods</b> (56 and 16 blocks): dry leans yellow (blue down), lush leans a cooler green by
 *       dropping red - never by raising blue (that read teal).</li>
 *   <li><b>Straw</b>: 2-6 block clumps, lighter and a more saturated yellow (red and green up, blue down), only
 *       on the dry side.</li>
 *   <li><b>Dark clumps</b>: 2-7 block clumps 14% darker, the same green (round 3's warmer darks read as mud), only
 *       on the lush side and never on straw.</li>
 * </ul>
 *
 * Round 4 (second critic, 5/10 and 4/10): the 48 / 14-block blob octaves halved, the value range widened toward the
 * dark side, hue swings capped (no orange-brown mud). The critic's single-block straw and dark "pips" with hard edges
 * cannot come from this tint (Embeddium blends the colour across each block's corners); they belong to the grass
 * texture's per-block variants (the pack, tools/gen_blocks.py SCAN section).
 *
 * <p>Cost: a few hash lookups per call, only where the game asks for a biome colour - while a chunk section is meshed
 * (BiomeColorsMixin), never per frame. Deterministic: the same block always gets the same colour, on every client.
 * Continuous: value noise with a quintic fade; under Embeddium (in the pack) the colour is blended per vertex corner,
 * so even the 1.5-block mottle is a gradient, not block steps (GrassTintTest).
 */
public final class GrassTint {

    /** Salts: grass and foliage vary on their own fields (a tree is not always as dry as the grass under it). */
    public static final int GRASS = 1;
    public static final int FOLIAGE = 2;

    static final double[] OLIVE = {1.05, 0.97, 1.0};
    /**
     * Grass only, round 5 (Oct 28, noon eyes stills): the lawn read too blue-green - hue 80 under lovish's
     * Complementary preset, 96 in the plain lane, the reference scan 72. A little more red, a little less green and
     * blue: the hue moves ~7 degrees toward yellow-green, luma -2%, the saturation holds. Leaves: CANOPY (round 6).
     */
    static final double[] OLIVE_GRASS = {1.10, 0.94, 0.95};
    /**
     * Foliage only, round 6 (Oct 29, lovish "lets keep improving textures"; eyes run 40817045672): the oak crowns
     * measured the lawn's own hue and value from the RTS camera and dissolved into the meadow. A canopy from above is a
     * cooler, darker mass than sunlit grass: red down most, green a little, blue least - the hue leans blue-green and
     * the value drops ~9% (the pack's r37 leaves are 15% darker too). Replaces OLIVE for leaves.
     */
    static final double[] CANOPY = {0.88, 0.91, 0.97};

    static final double[] CELLS = {48.0, 14.0, 4.5};
    static final double[] WEIGHTS = {1.0, 0.8, 0.6};
    static final double CONTRAST = 2.6;
    static final double VALUE = 0.085;   // round 4 0.045 (critic: camouflage, blur); round 7 0.085 inside the soft limit

    // review round 1 (Oct 28): no octave finer than 3 blocks - under Embeddium the colour blends across each block's
    // corners, so a 2-block cell reads as single-block pips, the "rule 3+" of round 4
    static final double[] MOTTLE_CELLS = {5.0, 3.5};
    static final double[] MOTTLE_WEIGHTS = {1.0, 0.25};
    static final double MOTTLE = 0.085;   // light side; the dark side is 1.6x (critic: the range sat too mid); round 7 0.06 -> 0.085
    static final double HUE_MOTTLE = 0.06;

    static final double[] DRY_CELLS = {56.0, 16.0};
    static final double[] DRY_WEIGHTS = {1.0, 0.6};
    static final double DRY_CONTRAST = 2.0;

    static final double[] STRAW_CELLS = {6.0, 3.0};
    static final double[] STRAW_WEIGHTS = {1.0, 0.35};
    static final double STRAW_AT = -0.25;
    static final double STRAW_SOFT = 1.7;    // round 7 1.4 -> 1.7: gradient noise is steeper at its edges
    static final double STRAW_GATE = -0.15;   // straw only where dryness is above this (fully 0.4 above it)

    static final double[] CLUMP_CELLS = {7.0, 3.5};
    static final double[] CLUMP_WEIGHTS = {1.0, 0.4};
    static final double CLUMP_AT = 0.05;
    static final double CLUMP_SOFT = 0.9;     // round 7 0.70 -> 0.9, as STRAW_SOFT
    static final double CLUMP_GATE = 0.0;     // dark clumps only where dryness is below this (fully 0.4 below it)
    static final double CLUMP_DARK = 0.14;

    /**
     * Round 7 (Oct 29, critic r40 #3: "one flat olive; patches show as rectangles"): the noise. The lattice (value)
     * noise of rounds 1-6 had zero slope on every lattice line - its quintic fade - and every octave sat on one
     * unrotated grid, so from the RTS camera the lawn read as a grid of squares (eyes r42: the colour changed 1.6x
     * faster mid-cell than on the 4-block lines; 20% more variation along the world axes than the diagonals). Now
     * gradient noise, each octave on a lattice turned by its seed x the golden angle, so no two octaves share lines.
     * NOISE_NORM scales it to the value noise's spread (std 0.452 vs 0.216), so the tuned ranges above keep meaning.
     */
    static final double GOLDEN_ANGLE = 2.399963229728653;
    static final double NOISE_NORM = 2.1;
    /**
     * Round 7: the brightness multiplier is soft-limited (tanh) to +20% / -22%, so the wider VALUE and MOTTLE ranges
     * spread the middle of the meadow without pushing its rare extremes past the readability floor
     * (darksKeepUnitsReadable) or the meadow's range.
     */
    static final double SOFT_LIGHT = 0.20;
    static final double SOFT_DARK = 0.22;

    private GrassTint() {
    }

    /** The shaped value field in [-1, 1] at block (x, z), its own field per salt. */
    public static double field(int x, int z, int salt) {
        return shape(octaves(x, z, salt * 8, CELLS, WEIGHTS), CONTRAST);
    }

    /** Dryness in [-1, 1] (+ = dry, toward straw; - = lush) at block (x, z). */
    static double dryness(int x, int z, int salt) {
        return shape(octaves(x + 7919, z - 4583, (salt + 101) * 8, DRY_CELLS, DRY_WEIGHTS), DRY_CONTRAST);
    }

    /** Straw cover in [0, 1] at block (x, z). */
    static double straw(int x, int z, int salt) {
        return straw(x, z, salt, dryness(x, z, salt));
    }

    /** Straw cover given the dryness already worked out at (x, z) (review round 1: apply() computed it twice). */
    static double straw(int x, int z, int salt, double dry) {
        double st = octaves(x - 1201, z + 911, (salt + 303) * 8, STRAW_CELLS, STRAW_WEIGHTS);
        return smooth(clamp01((st - STRAW_AT) / STRAW_SOFT)) * clamp01((dry - STRAW_GATE) / 0.4);
    }

    /** rgb (0xRRGGBB) with the meadow's variation applied at block (x, z). */
    public static int apply(int rgb, int x, int z, int salt) {
        double f = field(x, z, salt);
        double dry = dryness(x, z, salt);
        double s = straw(x, z, salt, dry);
        double cl = octaves(x - 3001, z + 1777, (salt + 202) * 8, CLUMP_CELLS, CLUMP_WEIGHTS);
        double m = smooth(clamp01((cl - CLUMP_AT) / CLUMP_SOFT)) * clamp01((CLUMP_GATE - dry) / 0.4) * (1 - s);
        double mottle = octaves(x + 555, z - 777, salt * 8 + 40, MOTTLE_CELLS, MOTTLE_WEIGHTS);
        double hm = HUE_MOTTLE * octaves(x + 999, z + 333, salt * 8 + 60, MOTTLE_CELLS, MOTTLE_WEIGHTS);
        double v = (1 + VALUE * f) * (1 - CLUMP_DARK * m) * (1 + 0.10 * s)
                * (1 + MOTTLE * (mottle > 0 ? mottle : 1.6 * mottle));
        v = v > 1 ? 1 + SOFT_LIGHT * Math.tanh((v - 1) / SOFT_LIGHT) : 1 - SOFT_DARK * Math.tanh((1 - v) / SOFT_DARK);
        double dp = Math.max(dry, 0), dn = Math.min(dry, 0);
        double[] o = salt == GRASS ? OLIVE_GRASS : CANOPY;
        double r = ((rgb >> 16) & 0xFF) * o[0] * v * (1 + 0.6 * hm) * (1 + 0.04 * dp + 0.06 * dn) * (1 + 0.10 * s)
                * (1 - 0.02 * m);
        double g = ((rgb >> 8) & 0xFF) * o[1] * v * (1 - 0.01 * dp - 0.03 * dn) * (1 + 0.04 * s);
        double b = (rgb & 0xFF) * o[2] * v * (1 - 0.9 * Math.max(hm, 0)) * (1 - 0.15 * dp) * (1 - 0.12 * s) * (1 - 0.02 * m);
        return (channel(r) << 16) | (channel(g) << 8) | channel(b);
    }

    private static double octaves(int x, int z, int seed, double[] cells, double[] weights) {
        double s = 0, w = 0;
        for (int i = 0; i < cells.length; i++) {
            s += weights[i] * gradientNoise(x / cells[i], z / cells[i], seed + i);
            w += weights[i];
        }
        return s / w;
    }

    private static double shape(double f, double contrast) {
        return Math.tanh(contrast * f) / Math.tanh(contrast);
    }

    private static double clamp01(double t) {
        return Math.max(0, Math.min(1, t));
    }

    private static double smooth(double t) {
        return t * t * (3 - 2 * t);
    }

    private static int channel(double c) {
        return (int) Math.max(0, Math.min(255, Math.round(c)));
    }

    /** Gradient noise in [-1, 1] on a lattice turned by seed x the golden angle (round 7: no shared square grid). */
    private static double gradientNoise(double x0, double z0, int seed) {
        double rot = seed * GOLDEN_ANGLE, cos = Math.cos(rot), sin = Math.sin(rot);
        double x = cos * x0 - sin * z0, z = sin * x0 + cos * z0;
        int ix = (int) Math.floor(x);
        int iz = (int) Math.floor(z);
        double fx = x - ix, fz = z - iz;
        double u = fade(fx);
        double v = fade(fz);
        double a = grad(ix, iz, seed, fx, fz);
        double b = grad(ix + 1, iz, seed, fx - 1, fz);
        double c = grad(ix, iz + 1, seed, fx, fz - 1);
        double d = grad(ix + 1, iz + 1, seed, fx - 1, fz - 1);
        return Math.max(-1, Math.min(1, NOISE_NORM * (a + (b - a) * u + (c - a) * v + (a - b - c + d) * u * v)));
    }

    /** A lattice point's unit gradient (angle from its hash) dotted with the offset (dx, dz) to it. */
    private static double grad(int ix, int iz, int seed, double dx, double dz) {
        double angle = Math.PI * (lattice(ix, iz, seed) + 1);
        return Math.cos(angle) * dx + Math.sin(angle) * dz;
    }

    private static double fade(double t) {
        return t * t * t * (t * (t * 6 - 15) + 10);
    }

    /** A lattice point's value in [-1, 1): a 64-bit mix of its coordinates and seed (splitmix64's finaliser). */
    private static double lattice(int ix, int iz, int seed) {
        long h = ix * 0x9E3779B97F4A7C15L ^ iz * 0xC2B2AE3D27D4EB4FL ^ seed * 0x165667B19E3779F9L;
        h = (h ^ (h >>> 30)) * 0xBF58476D1CE4E5B9L;
        h = (h ^ (h >>> 27)) * 0x94D049BB133111EBL;
        h ^= h >>> 31;
        return (h >>> 11) * 0x1.0p-53 * 2 - 1;
    }
}
