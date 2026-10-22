package com.solegendary.reignofnether.orthoview;

import com.solegendary.reignofnether.unit.Relationship;

/**
 * The pure rules behind seeing units under tree canopies in the RTS view (no Minecraft state, so CanopyRulesTest checks
 * them in plain JUnit): CanopySilhouettes draws a unit hidden by leaves or walls as a coloured silhouette, and LeafFade
 * fades the leaves in a soft circle round the cursor and the selected units.
 * <p>
 * Geometry: the RTS camera is orthographic, so everything that hides a point lies on ONE line through it, parallel to the
 * view direction (toward the camera). A screen-space circle round a point is a cylinder round that line - that is the
 * fade's shape, and the occlusion test walks that line.
 */
public final class CanopyRules {

    private CanopyRules() {
    }

    // ---- the fade (LeafFade) -----------------------------------------------------------------------------------------

    // Round 2 (coordinator's review of the eyes stills, Oct 22): a small hole round the cursor - round 1's 2.5 / 6 / 3
    // cleared most of a grove once three units and the cursor each had one.
    /** Inside this distance from the view line through an anchor, leaves are fully faded (alpha 0). Blocks. */
    public static final double FADE_INNER = 1.5;
    /** Beyond this distance leaves are fully solid (alpha 1); between the two a smoothstep. Blocks. */
    public static final double FADE_OUTER = 4.0;
    /**
     * How far an anchor (the cursor, a selected unit) may drift from its hole's centre before the hole is re-centred and
     * the chunk sections under it re-meshed. The hole is FADE_OUTER + HOLE_DRIFT wide, so while the anchor stays within
     * the drift the whole soft circle lies inside the hole and follows it every frame with no re-mesh at all.
     */
    public static final double HOLE_DRIFT = 2.0;
    public static final double HOLE_RADIUS = FADE_OUTER + HOLE_DRIFT;
    /** A hole reaches this far from its anchor toward the camera (blocks along the view line): any canopy height. */
    public static final double HOLE_LENGTH = 40.0;
    /** ...and this far behind it (a block straddling the anchor's ground level). */
    public static final double HOLE_BEHIND = 1.0;

    /** Alpha of a leaf block whose centre is {@code perpDist} blocks from the nearest anchor's view line. */
    public static float fadeAlpha(double perpDist) {
        return fadeAlpha(perpDist, FADE_INNER, FADE_OUTER);
    }

    public static float fadeAlpha(double perpDist, double inner, double outer) {
        if (perpDist <= inner)
            return 0f;
        if (perpDist >= outer)
            return 1f;
        double t = (perpDist - inner) / (outer - inner);
        return (float) (t * t * (3 - 2 * t));
    }

    /**
     * Squared distance of point v (relative to the anchor) from the line through the anchor along unit vector d, or
     * {@code Double.MAX_VALUE} when v lies outside the line's [-behind, length] span along d.
     */
    public static double perpDistSq(double vx, double vy, double vz, double dx, double dy, double dz,
                                    double behind, double length) {
        double t = vx * dx + vy * dy + vz * dz;
        if (t < -behind || t > length)
            return Double.MAX_VALUE;
        double sq = vx * vx + vy * vy + vz * vz - t * t;
        return sq < 0 ? 0 : sq;
    }

    /** Is the block centre v (relative to a hole's centre) inside that hole (a cylinder along d)? */
    public static boolean inHole(double vx, double vy, double vz, double dx, double dy, double dz, double radius) {
        return perpDistSq(vx, vy, vz, dx, dy, dz, HOLE_BEHIND, HOLE_LENGTH) <= radius * radius;
    }

    /**
     * Has an anchor drifted so far from its hole's centre (both points, compared across the view direction d) that the
     * soft circle would leave the hole? Movement ALONG the view line changes nothing on screen, so it never counts.
     */
    public static boolean needsRecentre(double ax, double ay, double az, double hx, double hy, double hz,
                                        double dx, double dy, double dz) {
        double vx = ax - hx, vy = ay - hy, vz = az - hz;
        double t = vx * dx + vy * dy + vz * dz;
        double sq = vx * vx + vy * vy + vz * vz - t * t;
        // the anchor's own height also moves the hole's [behind, length] span: re-centre on a big climb or drop
        return sq > HOLE_DRIFT * HOLE_DRIFT || Math.abs(t) > HOLE_DRIFT;
    }

    /** Has the camera turned far enough that the holes (cylinders along the old view direction) must be rebuilt? */
    public static boolean viewTurned(double ax, double ay, double az, double bx, double by, double bz) {
        return ax * bx + ay * by + az * bz < 0.9994;   // ~2 degrees
    }

    // ---- the occlusion test (CanopySilhouettes) ----------------------------------------------------------------------

    /** The blocks the test asks about, in world coordinates. */
    @FunctionalInterface
    public interface Voxels {
        boolean occludes(int x, int y, int z);
    }

    /** A ray stops after this many voxels (it has risen well above any canopy by then). */
    public static final int MAX_RAY_VOXELS = 96;

    /**
     * Walks the voxels from point o along unit vector d (toward the camera) with an exact grid traversal (Amanatides &
     * Woo) and answers whether any of them occludes, until the ray has risen {@code maxRise} blocks or crossed
     * MAX_RAY_VOXELS voxels. The voxel holding o itself is skipped: that is the unit's own space.
     */
    public static boolean rayOccluded(double ox, double oy, double oz, double dx, double dy, double dz,
                                      double maxRise, Voxels voxels) {
        int x = floor(ox), y = floor(oy), z = floor(oz);
        int stepX = dx > 0 ? 1 : (dx < 0 ? -1 : 0);
        int stepY = dy > 0 ? 1 : (dy < 0 ? -1 : 0);
        int stepZ = dz > 0 ? 1 : (dz < 0 ? -1 : 0);
        double tDeltaX = stepX != 0 ? Math.abs(1.0 / dx) : Double.MAX_VALUE;
        double tDeltaY = stepY != 0 ? Math.abs(1.0 / dy) : Double.MAX_VALUE;
        double tDeltaZ = stepZ != 0 ? Math.abs(1.0 / dz) : Double.MAX_VALUE;
        double tMaxX = stepX > 0 ? (x + 1 - ox) * tDeltaX : stepX < 0 ? (ox - x) * tDeltaX : Double.MAX_VALUE;
        double tMaxY = stepY > 0 ? (y + 1 - oy) * tDeltaY : stepY < 0 ? (oy - y) * tDeltaY : Double.MAX_VALUE;
        double tMaxZ = stepZ > 0 ? (z + 1 - oz) * tDeltaZ : stepZ < 0 ? (oz - z) * tDeltaZ : Double.MAX_VALUE;
        double topY = oy + maxRise;
        for (int i = 0; i < MAX_RAY_VOXELS; i++) {
            if (tMaxX < tMaxY && tMaxX < tMaxZ) {
                x += stepX;
                tMaxX += tDeltaX;
            } else if (tMaxY < tMaxZ) {
                y += stepY;
                tMaxY += tDeltaY;
            } else {
                z += stepZ;
                tMaxZ += tDeltaZ;
            }
            if (y > topY)
                return false;
            if (voxels.occludes(x, y, z))
                return true;
        }
        return false;
    }

    private static int floor(double v) {
        int i = (int) v;
        return v < i ? i - 1 : i;
    }

    // ---- colours -----------------------------------------------------------------------------------------------------

    /** Allies through the canopy: cyan, apart from every lobby colour's hue family except the cyan player's own. */
    public static final int ALLY_RGB = 0x38D8FF;
    /** Enemies: red, the colour every RTS player reads as "hostile". */
    public static final int ENEMY_RGB = 0xFF3A2E;
    /** Neutral units (rare in a match): amber. */
    public static final int NEUTRAL_RGB = 0xF2C14A;

    /**
     * The silhouette colour: your own units in your team colour, allies cyan, enemies red (lovish, Oct 22: "own = team
     * colour, ally = cyan, enemy = red"), lifted so a dark team colour still reads against dark foliage.
     */
    public static int silhouetteRgb(Relationship rel, int ownTeamRgb) {
        return switch (rel) {
            case OWNED -> readable(ownTeamRgb);
            case FRIENDLY -> ALLY_RGB;
            case HOSTILE -> ENEMY_RGB;
            case NEUTRAL -> NEUTRAL_RGB;
        };
    }

    /** Mixes a dark colour toward white until its luma reaches 0.45: navy, brown and black read through leaves. */
    public static int readable(int rgb) {
        float r = ((rgb >> 16) & 0xFF) / 255f, g = ((rgb >> 8) & 0xFF) / 255f, b = (rgb & 0xFF) / 255f;
        float luma = 0.2126f * r + 0.7152f * g + 0.0722f * b;
        final float min = 0.45f;
        if (luma < min) {
            float k = (min - luma) / (1f - luma);   // the white mix that lifts luma exactly to min
            r += (1 - r) * k;
            g += (1 - g) * k;
            b += (1 - b) * k;
        }
        return (Math.round(r * 255) << 16) | (Math.round(g * 255) << 8) | Math.round(b * 255);
    }
}
