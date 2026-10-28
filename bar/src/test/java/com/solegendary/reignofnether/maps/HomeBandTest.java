package com.solegendary.reignofnether.maps;

import com.solegendary.reignofnether.maps.gen.GenSeed;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Home metal moves out for the tech-4 capitol (design/proposals/capitols_extractors_creep.md section 4.2 on PR #310,
 * approved by lovish Oct 28): the L4 capitol is 41x41, so its corner reaches 28.3 blocks; with an 11x13 vehicle lab and
 * a block of gap each side (15) and a patch's reserved square (4.2 at a corner) that is 47.5, so <b>no patch within 48
 * of a start</b>, every seat's <b>7 home patches at 52-76</b> blocks, and a <b>build ground of 50</b> the open-ground
 * metal keeps clear. "Pre-made maps move their home metal out to the same band as generated maps" (lovish's answer 3):
 * one rule for both kinds of map. And the start check tests the full L4 square, reserved from the start (answer 4).
 * <p>
 * The numbers are the design's, written here from the doc rather than read from the code, so the code cannot drift from
 * them and still pass. Pure layout maths: every hand-made map at every player count a lobby seats, and generated maps.
 */
class HomeBandTest {

    /** Home patches a seat starts with, on every map: exactly this many, never "about". */
    static final int HOME_COUNT = 7;
    /** Every home patch stands this far from its own start, at least and at most (whole blocks, as stamped). */
    static final int HOME_MIN = 52, HOME_MAX = 76;
    /** No patch of any kind stands closer than this to any start: the L4 capitol, a lab beside it, a patch's square. */
    static final int NO_PATCH_WITHIN = 48;
    /** The open-ground metal keeps this clear round every start (the fill judges the unrounded spot: 0.75 for rounding). */
    static final int BUILD_GROUND = 50;
    /** Half the L4 capitol's 41x41 square, and the doc's sampling of it: 5x5 points, this far apart. */
    static final int CAPITOL_HALF = 20, CAPITOL_STEP = 10;
    /** A patch's reserved square (MetalPatches.RESERVED_HALF, = MapLayout.PAD: MapMetalGameTests pins the two). */
    static final int PATCH_HALF = 3;

    static final long[] SEEDS = { 0L, 77L, 0x5EED0001L };
    static final long[] GEN_SEEDS = { GenSeed.pack(0x5EED0001L, 1, 0), GenSeed.pack(0x5EED0002L, 2, 0) };

    static int[] teams(int n) {
        int[] t = new int[n];
        for (int i = 0; i < n; i++)
            t[i] = i < (n + 1) / 2 ? 0 : 1;
        return t;
    }

    static List<MapPreset> handmade() {
        List<MapPreset> out = new ArrayList<>();
        for (MapPreset p : MapPreset.values())
            if (p.handmade())
                out.add(p);
        return out;
    }

    static String fail(List<String> problems) {
        return problems.size() + " problem(s): " + String.join("; ", problems.subList(0, Math.min(10, problems.size())));
    }

    /** Exactly HOME_COUNT home patches a seat, all standard, every one HOME_MIN-HOME_MAX from its own start. */
    static void checkHome(MapLayout l, String tag, List<String> problems) {
        if (l.homeEnd() != HOME_COUNT * l.n)
            problems.add(tag + l.homeEnd() + " home patches for " + l.n + " seats, want exactly " + HOME_COUNT + " each");
        int[] count = new int[l.n];
        for (int i = 0; i < Math.min(l.homeEnd(), l.mexes.size()); i++) {
            MapLayout.Mex m = l.mexes.get(i);
            count[m.slot()]++;
            MapLayout.Slot s = l.slots.get(m.slot());
            double d = Math.hypot(m.dx() - s.dx(), m.dz() - s.dz());
            if (d < HOME_MIN || d > HOME_MAX)
                problems.add(tag + "seat " + s.index() + "'s home patch at " + m.dx() + "," + m.dz() + " is "
                    + String.format(java.util.Locale.ROOT, "%.1f", d) + " from its start, outside " + HOME_MIN + "-" + HOME_MAX);
            if (m.value() != 1.0f)
                problems.add(tag + "seat " + s.index() + "'s home patch yields " + m.value() + ", not the standard 1.0");
        }
        for (int s = 0; s < l.n; s++)
            if (count[s] != HOME_COUNT)
                problems.add(tag + "seat " + s + " has " + count[s] + " home patches, not exactly " + HOME_COUNT);
    }

    /** No patch within NO_PATCH_WITHIN of any start; outside the home sets, none on the BUILD_GROUND either. */
    static void checkClear(MapLayout l, String tag, List<String> problems) {
        for (int i = 0; i < l.mexes.size(); i++) {
            MapLayout.Mex m = l.mexes.get(i);
            double d = l.distanceToNearestStart(m.dx(), m.dz());
            if (d < NO_PATCH_WITHIN)
                problems.add(tag + "patch " + m.dx() + "," + m.dz() + " is " + String.format(java.util.Locale.ROOT, "%.1f", d)
                    + " from a start, inside " + NO_PATCH_WITHIN);
            else if (i >= l.homeEnd() && d < BUILD_GROUND - 0.75)
                problems.add(tag + "open-ground patch " + m.dx() + "," + m.dz() + " is on the " + BUILD_GROUND
                    + "-block build ground (" + String.format(java.util.Locale.ROOT, "%.1f", d) + ")");
        }
    }

    @Test
    void handMadeMapsGiveEverySeatSevenHomePatchesAt52To76() {
        List<String> problems = new ArrayList<>();
        for (MapPreset preset : handmade())
            for (int n = 2; n <= 16; n++)
                for (long seed : SEEDS) {
                    MapLayout l = MapLayout.build(preset, teams(n), seed);
                    checkHome(l, preset + " n=" + n + " seed " + seed + ": ", problems);
                }
        assertTrue(problems.isEmpty(), fail(problems));
    }

    @Test
    void handMadeMapsKeepEveryPatchOffTheCapitolsGround() {
        List<String> problems = new ArrayList<>();
        for (MapPreset preset : handmade())
            for (int n = 2; n <= 16; n++)
                for (long seed : SEEDS) {
                    MapLayout l = MapLayout.build(preset, teams(n), seed);
                    checkClear(l, preset + " n=" + n + " seed " + seed + ": ", problems);
                }
        assertTrue(problems.isEmpty(), fail(problems));
    }

    @Test
    void generatedMapsFollowTheSameBand() {
        List<String> problems = new ArrayList<>();
        for (int n : new int[] { 2, 4, 8 })
            for (long seed : GEN_SEEDS) {
                MapLayout l = MapLayout.build(MapPreset.GENERATED, teams(n), seed);
                String tag = "GENERATED n=" + n + " seed " + Long.toHexString(seed) + ": ";
                checkHome(l, tag, problems);
                checkClear(l, tag, problems);
            }
        assertTrue(problems.isEmpty(), fail(problems));
    }

    /**
     * The start check is the L4 square (doc 4.3): wherever startGround says a capitol may be founded, all 25 points of
     * the 41x41 square (5x5, 10 apart) are ground - no void, lava or water deeper than a ford - and no patch's reserved
     * square reaches into it, so the capitol can grow to L4 without crushing a patch or hanging over the void. Checked on
     * a grid round every start of every map (the start boxes reach 40 either side of the ring), and every start itself
     * must pass.
     */
    @Test
    void startGroundHoldsTheWholeL4Square() {
        List<String> problems = new ArrayList<>();
        MapLayout.Sample s = new MapLayout.Sample();
        List<MapLayout> maps = new ArrayList<>();
        for (MapPreset preset : handmade())
            for (int n : new int[] { 2, 4, 8, 16 })
                maps.add(MapLayout.build(preset, teams(n), 0L));
        for (int n : new int[] { 2, 4 })
            maps.add(MapLayout.build(MapPreset.GENERATED, teams(n), GEN_SEEDS[0]));
        for (MapLayout l : maps) {
            String tag = l.preset + " n=" + l.n + ": ";
            for (MapLayout.Slot sl : l.slots) {
                if (!l.startGround(sl.dx(), sl.dz()))
                    problems.add(tag + "start " + sl.index() + " itself is refused");
                for (int ox = -40; ox <= 40; ox += 4)
                    for (int oz = -40; oz <= 40; oz += 4) {
                        double x = sl.dx() + ox + 0.5, z = sl.dz() + oz + 0.5;
                        if (!l.startGround(x, z))
                            continue;
                        String why = squareFault(l, x, z, s);
                        if (why != null)
                            problems.add(tag + "startGround accepts " + x + "," + z + " but its 41x41 square " + why);
                    }
            }
        }
        assertTrue(problems.isEmpty(), fail(problems));
    }

    /** Why the L4 square centred at (x, z) cannot take the capitol, or null. */
    static String squareFault(MapLayout l, double x, double z, MapLayout.Sample s) {
        for (int i = -CAPITOL_HALF; i <= CAPITOL_HALF; i += CAPITOL_STEP)
            for (int k = -CAPITOL_HALF; k <= CAPITOL_HALF; k += CAPITOL_STEP) {
                l.sample(x + i, z + k, s);
                if (s.surface == MapLayout.Surface.VOID || s.surface == MapLayout.Surface.LAVA
                        || (s.surface == MapLayout.Surface.WATER && s.height < -1))
                    return "has " + s.surface + " at " + (x + i) + "," + (z + k);
            }
        for (MapLayout.Mex m : l.mexes)
            if (Math.abs(m.dx() - x) <= CAPITOL_HALF + PATCH_HALF && Math.abs(m.dz() - z) <= CAPITOL_HALF + PATCH_HALF)
                return "holds the reserved square of the patch at " + m.dx() + "," + m.dz();
        return null;
    }
}
