package com.solegendary.reignofnether.maps.gen;

import com.solegendary.reignofnether.building.buildings.shared.MetalExtractor;
import com.solegendary.reignofnether.maps.MapLayout;
import com.solegendary.reignofnether.maps.MapPreset;
import com.solegendary.reignofnether.maps.MetalOptions;
import com.solegendary.reignofnether.resources.TierGate;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The generated maps' metal (lovish Oct 27 21:37): tier 2 is priced with main's numbers, and "each map should have mexes
 * spread randomly and not be connected to where the townhall spawns" - with the fairness score still honest about a
 * start the random spread leaves far from metal.
 */
class GeneratedMetalTest {

    private static final long[] SEEDS = { GenSeed.pack(0x5EED0001L, 1, 0), GenSeed.pack(0x5EED0002L, 2, 0) };

    private static int[] teams(int n) {
        int[] t = new int[n];
        for (int i = 0; i < n; i++)
            t[i] = i < (n + 1) / 2 ? 0 : 1;
        return t;
    }

    /** The score's tier 2 is the game's: 4.5 metal/s (MetalExtractor) and a 75 s refit (TierGate), commit 0e223c88. */
    @Test
    void tier_two_is_priced_with_the_games_numbers() {
        assertEquals(MetalExtractor.T2_METAL_INCOME, Fairness.T2, 1e-6, "T2 metal/s in the fairness score");
        assertEquals(TierGate.UPGRADE_EXTRACTOR_SECONDS, Fairness.REFIT, 1e-6, "refit seconds in the fairness score");
        // one safe patch at the start, owned outright: T1 from 45 s, the refit from t2Time, T2 after the refit
        Fairness.Weights w = Fairness.Weights.CENTRAL;
        List<double[]> rows = new ArrayList<>();
        rows.add(new double[] { 0, 1, 1, 0 });
        double t1 = 45, t2 = w.t2Time();
        double expected = (2.0 * (t2 - t1) + MetalExtractor.T2_METAL_INCOME
            * (Fairness.HORIZON - t2 - TierGate.UPGRADE_EXTRACTOR_SECONDS)) / Fairness.HORIZON;
        assertEquals(expected, Fairness.metalIncome(rows, w), 1e-9, "15-minute income of one safe patch");
    }

    /**
     * No home ring: nothing stands round a start. No patch within 48 blocks of any start (design #310 section 4.2: the
     * L4 capitol's corner reach 28.3, a vehicle lab and its gaps 15, a patch's reserved square 4.2 = 47.5; it was 40
     * before the 41x41 capitol, and a home ring sat at 18).
     */
    @Test
    void no_seat_has_a_patch_ring_at_its_start() {
        for (int n : new int[] { 2, 4 })
            for (long seed : SEEDS) {
                MapLayout l = MapLayout.build(MapPreset.GENERATED, teams(n), seed);
                for (MapLayout.Slot s : l.slots)
                    for (MapLayout.Mex m : l.mexes) {
                        double d = Math.hypot(m.dx() - s.dx(), m.dz() - s.dz());
                        assertTrue(d >= 48, "n=" + n + " seed " + Long.toHexString(seed) + ": a patch " + Math.round(d)
                            + " blocks from seat " + s.index() + "'s start at (" + m.dx() + ", " + m.dz() + ")");
                    }
            }
    }

    /** The lobby's extra-metal option adds patches to the random spread (it was ignored on generated maps). */
    @Test
    void extra_metal_adds_patches_to_the_spread() {
        for (long seed : SEEDS) {
            int none = MapLayout.build(MapPreset.GENERATED, teams(2), seed,
                MetalOptions.DEFAULT.withExtra(MetalOptions.Extra.NONE)).mexes.size();
            int high = MapLayout.build(MapPreset.GENERATED, teams(2), seed,
                MetalOptions.DEFAULT.withExtra(MetalOptions.Extra.HIGH)).mexes.size();
            assertTrue(high > none, "seed " + Long.toHexString(seed) + ": HIGH extra metal drew " + high
                + " patches, NONE " + none);
        }
    }

    /**
     * The fairness guard: a map whose only metal-bearing ground lies round one start (here everything else is reserved)
     * leaves the other start far from all metal, and the generator must refuse it rather than serve it as fair.
     */
    @Test
    void a_seed_whose_start_is_far_from_all_metal_is_rejected() {
        long seed = 0x5EED0011L;
        GenMap first = MapGenerator.generate(teams(2), seed, GenOptions.DEFAULT, MetalOptions.DEFAULT);
        int px = first.slots.get(0).dx(), pz = first.slots.get(0).dz();
        MetalOptions onlyNearSeat0 = MetalOptions.DEFAULT.withReserved((dx, dz) -> Math.hypot(dx - px, dz - pz) > 100);
        GenMap m = MapGenerator.generate(teams(2), seed, GenOptions.DEFAULT, onlyNearSeat0);
        assertFalse(m.valid, "all " + m.mexes.size() + " patches lie within 100 of seat 0's start, yet the map is valid");
        assertTrue(m.invalidWhy.contains("metal"), "invalid for the wrong reason: " + m.invalidWhy);
    }
}
