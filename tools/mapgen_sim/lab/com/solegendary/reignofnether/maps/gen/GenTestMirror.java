package com.solegendary.reignofnether.maps.gen;

import com.solegendary.reignofnether.maps.ExtractorClearance;
import com.solegendary.reignofnether.maps.MapLayout;
import com.solegendary.reignofnether.maps.MapPreset;
import com.solegendary.reignofnether.maps.MetalOptions;

import java.util.ArrayList;
import java.util.List;

/**
 * The MapGenGameTests checks run offline on the repo's MapLayout + generator (lab only): the same assertions on more
 * seeds, so CI's run is a confirmation, not the first look. Usage: GenTestMirror seedsPerConfig
 */
public final class GenTestMirror {
    static int[] teams(int n) {
        int[] t = new int[n];
        for (int i = 0; i < n; i++)
            t[i] = i < (n + 1) / 2 ? 0 : 1;
        return t;
    }

    static String padFault(MapLayout l, int dx, int dz, MapLayout.Sample s) {
        l.sample(dx, dz, s);
        int h0 = s.height;
        for (int a = -MapLayout.PAD; a <= MapLayout.PAD; a++)
            for (int b = -MapLayout.PAD; b <= MapLayout.PAD; b++) {
                l.sample(dx + a, dz + b, s);
                if (s.surface == MapLayout.Surface.WATER || s.surface == MapLayout.Surface.LAVA
                        || s.surface == MapLayout.Surface.VOID || s.surface == MapLayout.Surface.CLIFF)
                    return s.surface + " at " + (dx + a) + "," + (dz + b);
                if (Math.abs(s.height - h0) > 1)
                    return "a drop of " + Math.abs(s.height - h0) + " at " + (dx + a) + "," + (dz + b);
            }
        return null;
    }

    public static void main(String[] a) {
        int per = Integer.parseInt(a[0]);
        List<String> problems = new ArrayList<>();
        MetalOptions.ReservedZones disc = (dx, dz) -> (dx - 90) * (dx - 90) + dz * dz <= 30 * 30;
        MetalOptions metal = MetalOptions.DEFAULT.withReserved(disc);
        MapLayout.Sample s = new MapLayout.Sample();
        int maps = 0;
        double worst = 0, worstTeam = 0;
        long t0 = System.nanoTime();
        List<Double> spreads = new ArrayList<>();
        for (int n : new int[] { 2, 4, 8 })
            for (int shape = 1; shape <= 2; shape++)
                for (int k = 0; k < per; k++) {
                    long seed = GenSeed.pack(0x5EED0000L + k * 7919L + n, shape, k % 3);
                    String tag = "n=" + n + " shape " + shape + " seed " + Long.toHexString(seed) + ": ";
                    long b0 = System.nanoTime();
                    MapLayout l = MapLayout.build(MapPreset.GENERATED, teams(n), seed, metal);
                    long ms = (System.nanoTime() - b0) / 1_000_000;
                    maps++;
                    if (!l.gen.valid)
                        problems.add(tag + "invalid: " + l.gen.invalidWhy);
                    MapLayout.Slot s0 = l.slots.get(0);
                    MapLayout.Reach reach = l.reachFrom(s0.dx(), s0.dz());
                    for (MapLayout.Slot sl : l.slots)
                        if (!reach.containsNear(sl.dx(), sl.dz(), 1))
                            problems.add(tag + "seat " + sl.index() + " cut off");
                    if (!reach.containsNear(0, 0, 6))
                        problems.add(tag + "middle cut off");
                    for (MapLayout.Mex m : l.mexes) {
                        if (!reach.containsNear(m.dx(), m.dz(), 1))
                            problems.add(tag + "patch " + m.dx() + "," + m.dz() + " unreached");
                        String f = padFault(l, m.dx(), m.dz(), s);
                        if (f != null)
                            problems.add(tag + "patch " + m.dx() + "," + m.dz() + " pad " + f);
                        if (l.distanceToArenaEdge(m.dx(), m.dz()) < ExtractorClearance.blocks())
                            problems.add(tag + "patch at the edge");
                        if (disc.reserves(m.dx(), m.dz()))
                            problems.add(tag + "patch in reserved disc " + m.dx() + "," + m.dz());
                        for (GenMap.Jungle j : l.gen.jungle)
                            if (j.contains(m.dx(), m.dz()))
                                problems.add(tag + "patch in jungle");
                        boolean deep = false;
                        for (int x = -7; x <= 7 && !deep; x++)
                            for (int z = -7; z <= 7 && !deep; z++) {
                                l.sample(m.dx() + x, m.dz() + z, s);
                                deep = s.surface == MapLayout.Surface.WATER && s.height < -1;
                            }
                        if (deep)
                            problems.add(tag + "patch " + m.dx() + "," + m.dz() + " deep water within 7");
                    }
                    for (MapLayout.Site st : l.sites)
                        if (!reach.containsNear(st.dx(), st.dz(), 4))
                            problems.add(tag + "site unreached");
                    for (MapLayout.Spot v : l.vents)
                        if (!reach.containsNear(v.dx(), v.dz(), 3))
                            problems.add(tag + "vent unreached");
                    for (GenMap.Jungle j : l.gen.jungle)
                        for (int[] door : new int[][] { j.entry(), j.exit() }) {
                            double rim = Double.MAX_VALUE;
                            for (int[] d : j.discs())
                                rim = Math.min(rim, Math.abs(Math.hypot(door[0] - d[0], door[1] - d[1]) - d[2]));
                            if (rim > 2.5)
                                problems.add(tag + "jungle door off the edge");
                        }
                    double sp = l.gen.report.spread(), ts = l.gen.report.teamSpread();
                    spreads.add(sp);
                    worst = Math.max(worst, sp);
                    worstTeam = Math.max(worstTeam, ts);
                    if (sp > 0.05 + 1e-9 || ts > 0.05 + 1e-9)
                        problems.add(tag + String.format("spread %.3f team %.3f (attempt %d, %d ms)", sp, ts, l.gen.attempt, ms));
                    for (MapLayout.Slot sl : l.slots) {
                        if (!l.startGround(sl.dx(), sl.dz()))
                            problems.add(tag + "seat " + sl.index() + " start ground");
                        if (l.boxOf(sl.team()) == null || !l.boxOf(sl.team()).contains(sl.dx(), sl.dz()))
                            problems.add(tag + "seat " + sl.index() + " outside box");
                    }
                    if (GenSeed.modifiersIndex(seed) == 1 && !l.gen.modifiers.isEmpty())
                        problems.add(tag + "modifiers off but rolled");
                    for (Modifier mo : l.gen.modifiers)
                        if (!mo.live)
                            problems.add(tag + "non-live modifier " + mo);
                    if ((shape == 2) != l.squareArena())
                        problems.add(tag + "shape mismatch");
                    System.out.printf("%s%d ms spread %.3f team %.3f attempt %d%n", tag, ms, sp, ts, l.gen.attempt);
                }
        spreads.sort(Double::compare);
        System.out.printf("%d maps in %d s; worst spread %.3f team %.3f; median %.3f%n", maps,
            (System.nanoTime() - t0) / 1_000_000_000L, worst, worstTeam, spreads.get(spreads.size() / 2));
        System.out.println(problems.size() + " problems");
        for (int i = 0; i < Math.min(40, problems.size()); i++)
            System.out.println("  " + problems.get(i));
    }
}
