package com.solegendary.reignofnether.maps;

import java.util.ArrayList;
import java.util.List;

/**
 * The one home metal rule, for every map and every player count (lovish Oct 22: "starting mexes around the base can be
 * standardized"; Oct 28: "pre-made maps move their home metal out to the same band as generated maps"). Defined here
 * once: the generator (maps/gen/GenEconomy.homeClumps) and the hand-made maps ({@link MapLayout}'s homeSet) both lay the
 * same template from these numbers, and HomeBandTest / MetalClumpsTest / MapExtraMetalGameTests assert them for every
 * map, seat and seed.
 *
 * <p><b>Why these distances</b> (design/proposals/capitols_extractors_creep.md section 4.2, PR #310, approved Oct 28):
 * the tech-4 capitol is 41x41 ({@link #CAPITOL_HALF} 20), its corner reaches 20 x root 2 = 28.3 blocks from the start;
 * an 11x13 vehicle lab with a block of gap each side needs 15 more, and a patch's reserved square is 3 x root 2 = 4.2
 * nearer at its corner: 47.5, so {@link #NO_PATCH_WITHIN} 48. The home clumps then stand {@link #MIN_DIST}-{@link
 * #MAX_DIST} (52-76, was 48-72) out and the open-ground metal keeps {@link #BUILD_GROUND} (50, was 46) clear, so a full
 * tech-1 base (two labs, the vehicle lab, eco) still fits round the reserved L4 square. Two starts need their build
 * grounds and home bands clear of each other: at least 170 apart.
 *
 * <p><b>The template</b>: a triangle of three straight behind the base (a row of three where the wall is near: a seat on
 * a square arena's side has 62 blocks behind it), and a pair on each rear flank, {@link #FLANK} degrees off straight back
 * ("clumps ... including the back rear sides", lovish Oct 27). Every clump is tried at each turn of {@link #TURNS} and
 * distance of {@link #DISTS}; the first option the ground takes for every seat is everyone's, so the set is the same for
 * every seat. A map that cannot give every seat all {@link #COUNT} is not served (the generator draws again, a hand-made
 * map's ground is shaped for them): "exactly seven" is a promise, not a target.
 *
 * <p>Not reached by the extra-metal knob ({@link MetalOptions#extra()}) or the lobby's richness: home metal is the same in
 * every lobby setting. (The procedural "Vanilla terrain" ring, StartAreaClearing.stampHomeRing, is its own thing.)
 */
public final class HomeMetal {
    private HomeMetal() { }

    /** Patches per seat: the triangle (or row) behind and two flank pairs. */
    public static final int COUNT = 7;
    /** Yield of each: the standard patch. */
    public static final float VALUE = 1.0f;
    /** Every home patch stands at least this far from its own start, and at most this far (whole blocks, as stamped). */
    public static final int MIN_DIST = 52, MAX_DIST = 76;
    /** No patch of any kind, home or open ground, stands closer than this to any start (the L4 capitol and a lab beside it). */
    public static final int NO_PATCH_WITHIN = 48;
    /** The start's build ground: the open-ground metal, sites and spreads keep at least this clear of every start. */
    public static final int BUILD_GROUND = 50;
    /** Half the side of the tech-4 (L4) capitol's square, 41x41, reserved from the start (MapLayout.startGround). */
    public static final int CAPITOL_HALF = 20;

    /** The step between a clump's patches: the old natural pair stood 9-13 apart, a pad is 7 wide (also GenEconomy's groups). */
    public static final double CLUMP_STEP = 11;
    /** Every patch keeps this far from every patch of another group, so the groups read apart (GenEconomy.GROUP_SEP). */
    public static final double CLUMP_SEP = 26;
    /** Bearing of a home flank pair from the seat's outward direction, degrees: its rear sides. */
    public static final double FLANK = 72;
    /**
     * Distances from the start a clump's anchor may take, nearest first. An option is offered only when every member,
     * unrounded, is ROUNDING inside the band, so rounding to the stamped block can never push it out: 53 is the nearest
     * anchor whose triangle base (53.3) keeps that margin, 75 the farthest for a pair (75.2).
     */
    static final int[] DISTS = { 53, 56, 60, 64, 68, 72, 75 };
    /** The most rounding to a whole block can move a point: half a block on each axis. */
    static final double ROUNDING = 0.75;
    /** Turns from a clump's bearing, degrees, in the order tried: straight first, then either way. */
    static final int[] TURNS = { 0, 10, -10, 20, -20, 30, -30, 40, -40, 50, -50 };

    /** Whether a home patch this far (rounded position) from its own start is in the band. */
    public static boolean inBand(double d) {
        return d >= MIN_DIST && d <= MAX_DIST;
    }

    /**
     * One way to lay a home clump: its members' {radial, tangential} offsets from the anchor, the clump's bearing from the
     * seat's outward direction (radians), its turn (degrees) and the anchor's distance from the start.
     */
    public record Option(double[][] shape, double bearing, int turn, int dist) {
        /**
         * The members for a start at (sx, sz) whose outward direction is {@code outward} (radians), unrounded. StrictMath:
         * the lobby preview and the server must draw the same map on any machine.
         */
        public double[][] points(double sx, double sz, double outward) {
            double b = outward + bearing + Math.toRadians(turn);
            double rx = StrictMath.cos(b), rz = StrictMath.sin(b);
            double[][] out = new double[shape.length][];
            for (int k = 0; k < shape.length; k++) {
                double r = dist + shape[k][0], tg = shape[k][1];
                out[k] = new double[] { sx + rx * r - rz * tg, sz + rz * r + rx * tg };
            }
            return out;
        }

        /** As {@link #points}, rounded to the block a patch is stamped on. */
        public int[][] spots(double sx, double sz, double outward) {
            double[][] p = points(sx, sz, outward);
            int[][] out = new int[p.length][];
            for (int k = 0; k < p.length; k++)
                out[k] = new int[] { (int) Math.round(p[k][0]), (int) Math.round(p[k][1]) };
            return out;
        }
    }

    /**
     * The template's clumps (behind, left flank, right flank), each with its options in the order tried: the turn
     * outermost (a row straight behind beats a triangle turned into a flank's way), then the shape, then the distance.
     * An option is offered only when all its members lie inside the band with room for rounding (the placers check the
     * rounded spot again). Their members add up to {@link #COUNT}.
     */
    public static List<List<Option>> clumps() {
        double h = CLUMP_STEP / 2, up = CLUMP_STEP * Math.sqrt(3) / 2;
        double[][] triangle = { { 0, -h }, { 0, h }, { up, 0 } }, row = { { 0, -CLUMP_STEP }, { 0, 0 }, { 0, CLUMP_STEP } };
        double[][] pair = { { 0, -h }, { 0, h } };
        List<List<Option>> out = new ArrayList<>();
        out.add(options(List.<double[][]>of(triangle, row), 0));
        out.add(options(List.<double[][]>of(pair), Math.toRadians(FLANK)));
        out.add(options(List.<double[][]>of(pair), -Math.toRadians(FLANK)));
        return out;
    }

    private static List<Option> options(List<double[][]> shapes, double bearing) {
        List<Option> out = new ArrayList<>();
        for (int turn : TURNS)
            for (double[][] shape : shapes)
                for (int dist : DISTS) {
                    boolean inside = true;
                    for (double[] m : shape) {
                        double d = Math.hypot(dist + m[0], m[1]);
                        inside &= d >= MIN_DIST + ROUNDING && d <= MAX_DIST - ROUNDING;
                    }
                    if (inside)
                        out.add(new Option(shape, bearing, turn, dist));
                }
        return out;
    }
}
