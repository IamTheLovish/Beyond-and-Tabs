package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.maps.MapLayout;
import com.solegendary.reignofnether.maps.MapPreset;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * The per-match map seed (inbox spec-2 PR 1): every match draws a new map, so the seed must never cost the folded
 * frame its fairness. Pure maths on MapLayout (no world access), in their own batch "maps_seeded" so the 50-seed sweep
 * never holds up another batch. Pass marks from the spec: the same seed builds the same map, another seed another map,
 * seed 0 is the map every build drew before seeds, and every slot owns the same metal on every seed.
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class MapSeedGameTests {

    static final String ARENA = SkirmishGameTests.ARENA;
    static final int SEEDS = 50;
    static final int[] COUNTS = { 2, 4 };

    /** Seed i of the sweep: spread out, never 0 (0 is the unseeded map). */
    static long seedOf(int i) {
        return 0x5EED0000L + (i + 1) * 0x9E3779B97F4A7C15L;
    }

    static List<MapPreset> premade() {
        List<MapPreset> out = new ArrayList<>();
        for (MapPreset p : MapPreset.values())
            if (p.premade())
                out.add(p);
        return out;
    }

    /** The map as a string: every patch, every site, and the terrain sampled on a 6-block grid over the arena. */
    static String fingerprint(MapLayout l) {
        StringBuilder sb = new StringBuilder();
        for (MapLayout.Mex m : l.mexes)
            sb.append('m').append(m.dx()).append(',').append(m.dz()).append(',').append(m.value()).append(',').append(m.slot());
        for (MapLayout.Site s : l.sites)
            sb.append('s').append(s.dx()).append(',').append(s.dz()).append(',').append(s.kind());
        MapLayout.Sample sample = new MapLayout.Sample();
        for (int x = -l.radius; x <= l.radius; x += 6)
            for (int z = -l.radius; z <= l.radius; z += 6) {
                if ((long) x * x + (long) z * z > (long) l.radius * l.radius)
                    continue;
                l.sample(x, z, sample);
                sb.append(sample.height).append(sample.surface.ordinal()).append(sample.forest ? 'f' : '.');
            }
        return sb.toString();
    }

    static String fail(List<String> problems) {
        return problems.size() + " problem(s): " + String.join("; ", problems.subList(0, Math.min(8, problems.size())));
    }

    /**
     * Deterministic and seeded: a layout rebuilt from the same seed is identical (the lobby preview, the builder and a
     * resumed setup all rebuild it - code_review CR-13), seed 0 is exactly the unseeded build, and on Verdant Valley
     * (the skirmish default) two different seeds give two different maps. Which other presets the seed changes is
     * logged as a [MapSeed] notice.
     */
    @GameTest(template = ARENA, batch = "maps_seeded")
    public static void map_seed_same_seed_same_map_other_seed_other_map(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        List<String> varies = new ArrayList<>();
        int[] teams = RealmMapGameTests.teams(4);
        for (MapPreset preset : premade()) {
            String unseeded = fingerprint(MapLayout.build(preset, teams));
            if (!unseeded.equals(fingerprint(MapLayout.build(preset, teams, 0L))))
                problems.add(preset + ": seed 0 is not the unseeded map");
            boolean differs = false;
            for (int i = 0; i < 5; i++) {
                long seed = seedOf(i);
                String a = fingerprint(MapLayout.build(preset, teams, seed));
                if (!a.equals(fingerprint(MapLayout.build(preset, teams, seed))))
                    problems.add(preset + ": seed " + seed + " built two different maps");
                if (!a.equals(fingerprint(MapLayout.build(preset, teams, seedOf(i + 7)))))
                    differs = true;
            }
            if (differs)
                varies.add(preset.name());
            else if (preset == MapPreset.VERDANT_VALLEY)
                problems.add(preset + ": five pairs of different seeds all built the same map");
        }
        ReignOfNether.LOGGER.info("[MapSeed] the seed changes {} of {} pre-made maps: {}", varies.size(), premade().size(), varies);
        if (!problems.isEmpty())
            helper.fail(fail(problems));
        helper.succeed();
    }

    /**
     * Fair on every seed: for 50 seeds, every pre-made map and 2 and 4 players, every slot owns the same metal
     * (mexValueOfSlot, exact) and the same number of patches, the patches tile the map n-fold, and every start stands
     * on ground a capitol can take (startGround).
     */
    @GameTest(template = ARENA, batch = "maps_seeded")
    public static void map_seed_metal_fair_for_50_seeds(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        long t0 = System.nanoTime();
        int built = 0;
        for (MapPreset preset : premade())
            for (int n : COUNTS)
                for (int i = 0; i < SEEDS; i++) {
                    long seed = seedOf(i);
                    MapLayout l = MapLayout.build(preset, RealmMapGameTests.teams(n), seed);
                    built++;
                    String tag = preset + " n=" + n + " seed#" + i + ": ";
                    float lo = Float.MAX_VALUE, hi = 0;
                    int[] count = new int[n];
                    for (MapLayout.Mex m : l.mexes)
                        if (m.slot() >= 0 && m.slot() < n)
                            count[m.slot()]++;
                        else
                            problems.add(tag + "patch at " + m.dx() + "," + m.dz() + " has slot " + m.slot());
                    for (int s = 0; s < n; s++) {
                        float v = l.mexValueOfSlot(s);
                        lo = Math.min(lo, v);
                        hi = Math.max(hi, v);
                        if (count[s] != count[0])
                            problems.add(tag + "slot " + s + " has " + count[s] + " patches, slot 0 " + count[0]);
                        MapLayout.Slot sl = l.slots.get(s);
                        if (!l.startGround(sl.dx(), sl.dz()))
                            problems.add(tag + "slot " + s + " not on start ground");
                    }
                    if (lo <= 0 || hi - lo > 1e-3f)
                        problems.add(tag + "slot metal " + lo + ".." + hi);
                    if (l.mexes.size() % n != 0)
                        problems.add(tag + l.mexes.size() + " patches for " + n + " slots");
                }
        ReignOfNether.LOGGER.info("[MapSeed] {} seeded layouts checked in {} ms", built, (System.nanoTime() - t0) / 1_000_000);
        if (!problems.isEmpty())
            helper.fail(fail(problems));
        helper.succeed();
    }
}
