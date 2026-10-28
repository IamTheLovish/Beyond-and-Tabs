package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.maps.HomeMetal;
import com.solegendary.reignofnether.maps.MapLayout;
import com.solegendary.reignofnether.maps.MapPreset;
import com.solegendary.reignofnether.maps.MetalOptions;
import com.solegendary.reignofnether.building.buildings.shared.MetalExtractor;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Extra metal (lovish Oct 22: "starting mexes around the base can be standardized, but we can add extra metal to give
 * bonuses to players that reach to the outskirts of the map", "we can add more mexes than we have to the game", "he does
 * want massive armies"): the home set is the same for every seat on every map, the extra patches are spread over the whole
 * map on top of the metal the map had before and never take any of it, and a patch pays more the further it stands from
 * the bases and the nearer the rim. Pure maths on MapLayout, batch "maps_metal".
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class MapExtraMetalGameTests {

    static final String ARENA = SkirmishGameTests.ARENA;
    static final long[] SEEDS = { 0L, 0x5EED0001L, 0xBA7C0DEL };
    /** Two home patches closer than this crowd each other (7x7 pads, 1 block apart, need 8; a clump's members stand 11 apart). */
    static final double HOME_CROWD = 9;
    static final Set<MapPreset> LAND = Set.of(MapPreset.HIGHLANDS, MapPreset.NETHER_RIFT, MapPreset.VERDANT_VALLEY,
        MapPreset.DESERT_MESA, MapPreset.SOULFIRE_DELTAS);

    /**
     * The map without extra metal (MetalOptions.Extra.NONE: the home set, the templates and the core fill), per
     * "PRESET:players:seed (0 or S = 0x5EED0001):patches a slot farther than 40 blocks from the wall:largest gap".
     * Gap: the largest distance from any open ground (no void, lava or deep water; every 3 blocks, the 8 outermost blocks
     * and every start's build ground skipped) to the nearest patch. Measured offline (tools/mapgen_sim harness) on this
     * layout.
     * <p>
     * Re-measured Oct 28 for the 41x41 tech-4 capitol (design #310 section 4.2), which moves home metal out to 52-76 and
     * keeps the 50-block build ground free of every patch. Until then the rows were head 2f732e29's map (rim limit 22, cap
     * 14 a slot, a home ring at 18), and the core fill had placed many of its patches 46-50 from a start: on the island
     * maps (Archipelago, End Spires, Chorus Isles), where the home island is most of the open ground, most of them. Those
     * patches are now on the build ground, so the rows fell; every lost patch was within 50 of a start - past 50 no map at
     * any player count has fewer mid-map patches a slot than on 2f732e29 (most have more), measured the same way. What this
     * baseline guards stays exactly as it was: the extra metal never takes a core patch and never widens a gap.
     */
    static final String BASELINE =
        "ARCHIPELAGO:2:0:9:144 ARCHIPELAGO:4:0:9:100 ARCHIPELAGO:8:0:9:62 HIGHLANDS:2:0:17:55 " +
        "HIGHLANDS:4:0:16:47 HIGHLANDS:8:0:11:33 NETHER_RIFT:2:0:21:81 NETHER_RIFT:4:0:17:49 " +
        "NETHER_RIFT:8:0:10:33 END_SPIRES:2:0:11:140 END_SPIRES:4:0:11:66 END_SPIRES:8:0:7:53 " +
        "VERDANT_VALLEY:2:0:15:58 VERDANT_VALLEY:4:0:16:35 VERDANT_VALLEY:8:0:12:33 DESERT_MESA:2:0:22:69 " +
        "DESERT_MESA:4:0:22:57 DESERT_MESA:8:0:10:33 SOULFIRE_DELTAS:2:0:21:77 SOULFIRE_DELTAS:4:0:19:58 " +
        "SOULFIRE_DELTAS:8:0:7:43 CHORUS_ISLES:2:0:15:137 CHORUS_ISLES:4:0:13:114 CHORUS_ISLES:8:0:11:55 " +
        "ARCHIPELAGO:2:S:9:144 ARCHIPELAGO:4:S:9:100 ARCHIPELAGO:8:S:9:62 HIGHLANDS:2:S:17:55 " +
        "HIGHLANDS:4:S:16:47 HIGHLANDS:8:S:11:33 NETHER_RIFT:2:S:21:81 NETHER_RIFT:4:S:17:49 " +
        "NETHER_RIFT:8:S:10:33 END_SPIRES:2:S:12:124 END_SPIRES:4:S:9:81 END_SPIRES:8:S:7:53 " +
        "VERDANT_VALLEY:2:S:15:58 VERDANT_VALLEY:4:S:16:35 VERDANT_VALLEY:8:S:12:33 DESERT_MESA:2:S:22:69 " +
        "DESERT_MESA:4:S:22:57 DESERT_MESA:8:S:10:33 SOULFIRE_DELTAS:2:S:21:77 SOULFIRE_DELTAS:4:S:19:58 " +
        "SOULFIRE_DELTAS:8:S:7:43 CHORUS_ISLES:2:S:15:136 CHORUS_ISLES:4:S:13:112 CHORUS_ISLES:8:S:11:56 ";

    static MetalOptions extra(MetalOptions.Extra level) {
        return MetalOptions.DEFAULT.withExtra(level);
    }

    /** Patches slot 0 owns farther from the wall than the rim band (the "mid-map" count of the tables). */
    static int midPerSlot(MapLayout l) {
        int mid = 0;
        for (MapLayout.Mex m : l.mexes)
            if (m.slot() == 0 && l.distanceToArenaEdge(m.dx(), m.dz()) > MapLayout.RIM_BAND)
                mid++;
        return mid;
    }

    /**
     * Largest distance from open ground to its nearest patch (the PR's measurement, see BASELINE). A start's build ground
     * (HomeMetal.BUILD_GROUND) is not open ground: it is kept free of metal by design (the tech-4 capitol and the labs
     * round it, design #310 section 4.2), so a start's own centre, 50+ from every patch, would otherwise be every land
     * map's largest gap and hide the gaps this measures.
     */
    static int largestGap(MapLayout l) {
        MapLayout.Sample s = new MapLayout.Sample();
        double worst = 0;
        int r = l.radius;
        for (int x = -r; x <= r; x += 3)
            for (int z = -r; z <= r; z += 3) {
                if (!l.insideArena(x, z) || l.distanceToArenaEdge(x, z) < 8
                        || l.distanceToNearestStart(x, z) < HomeMetal.BUILD_GROUND)
                    continue;
                l.sample(x, z, s);
                if (s.surface == MapLayout.Surface.VOID || s.surface == MapLayout.Surface.LAVA
                        || (s.surface == MapLayout.Surface.WATER && s.height < -1))
                    continue;
                double d = Double.MAX_VALUE;
                for (MapLayout.Mex m : l.mexes)
                    d = Math.min(d, Math.hypot(m.dx() - x, m.dz() - z));
                worst = Math.max(worst, d);
            }
        return (int) Math.round(worst);
    }

    static int extrasPerSlot(MapLayout l) {
        return (l.mexes.size() - l.extraFrom()) / l.n;
    }

    /** Metal a seat earns a minute with every patch it owns worked, tier 1 and tier 2 (income x yield, 60 seconds). */
    static double[] metalPerMinute(MapLayout l) {
        double v = l.mexValueOfSlot(0);
        return new double[] { v * 2.0 * 60, v * MetalExtractor.T2_METAL_INCOME * 60 };
    }

    /**
     * The design's home band (design/proposals/capitols_extractors_creep.md section 4.2, PR #310, lovish Oct 28: the
     * 41x41 tech-4 capitol pushes home metal out, pre-made maps "to the same band as generated maps"), written from the
     * doc so the code cannot drift from it: exactly 7 home patches a seat, every one 52-76 blocks from its start, and no
     * patch of any kind within 48 of a start.
     */
    static final int HOME_COUNT = 7, HOME_MIN = 52, HOME_MAX = 76, NO_PATCH_WITHIN = 48;

    /**
     * The home set is one thing (HomeMetal): on every pre-made map, 2 to 16 players, three seeds, every seat has exactly
     * HOME_COUNT home patches, all HomeMetal.VALUE, between HOME_MIN and HOME_MAX from its start, none crowding another
     * (closer than HOME_CROWD; a clump's members stand 11 apart), no patch at all within NO_PATCH_WITHIN of a start, and
     * the set is the same for every seat (the same distances from its start, to a block of rounding). Neither the metal
     * richness nor the extra-metal level moves it.
     */
    @GameTest(template = ARENA, batch = "maps_metal")
    public static void map_metal_home_set_is_standard_for_every_seat(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        if (HomeMetal.COUNT != HOME_COUNT || HomeMetal.MIN_DIST != HOME_MIN || HomeMetal.MAX_DIST != HOME_MAX)
            problems.add("HomeMetal says " + HomeMetal.COUNT + " patches at " + HomeMetal.MIN_DIST + "-" + HomeMetal.MAX_DIST
                + ", the design " + HOME_COUNT + " at " + HOME_MIN + "-" + HOME_MAX);
        for (MapPreset preset : MapMetalGameTests.premade())
            for (int n : MapMetalGameTests.COUNTS)
                for (long seed : SEEDS) {
                    int[] teams = RealmMapGameTests.teams(n);
                    MapLayout l = MapLayout.build(preset, teams, seed);
                    String tag = preset + " n=" + n + " seed " + seed + ": ";
                    if (l.homeEnd() != n * HOME_COUNT)
                        problems.add(tag + l.homeEnd() + " home patches for " + n + " seats, expected " + n * HOME_COUNT);
                    for (MapLayout.Mex m : l.mexes)
                        if (l.distanceToNearestStart(m.dx(), m.dz()) < NO_PATCH_WITHIN)
                            problems.add(tag + "patch " + m.dx() + "," + m.dz() + " within " + NO_PATCH_WITHIN + " of a start");
                    List<double[]> dist = new ArrayList<>();
                    for (int s = 0; s < n; s++)
                        dist.add(new double[0]);
                    for (int s = 0; s < n; s++) {
                        List<Double> ds = new ArrayList<>();
                        MapLayout.Slot sl = l.slots.get(s);
                        for (int i = 0; i < l.homeEnd(); i++) {
                            MapLayout.Mex m = l.mexes.get(i);
                            if (m.slot() != s)
                                continue;
                            if (m.value() != HomeMetal.VALUE)
                                problems.add(tag + "home patch of seat " + s + " yields " + m.value());
                            ds.add(Math.hypot(m.dx() - sl.dx(), m.dz() - sl.dz()));
                        }
                        double[] sorted = ds.stream().mapToDouble(Double::doubleValue).sorted().toArray();
                        dist.set(s, sorted);
                        if (sorted.length != HOME_COUNT)
                            problems.add(tag + "seat " + s + " has " + sorted.length + " home patches, not exactly " + HOME_COUNT);
                        for (double d : sorted)
                            if (d < HOME_MIN || d > HOME_MAX)
                                problems.add(tag + "seat " + s + " has a home patch " + d + " from its start");
                    }
                    for (int s = 1; s < n; s++)
                        for (int k = 0; k < Math.min(dist.get(s).length, dist.get(0).length); k++)
                            if (Math.abs(dist.get(s)[k] - dist.get(0)[k]) > 1.5)
                                problems.add(tag + "seat " + s + " home distances " + Arrays.toString(dist.get(s))
                                    + " differ from seat 0's " + Arrays.toString(dist.get(0)));
                    for (int i = 0; i < l.homeEnd(); i++)
                        for (int j = i + 1; j < l.homeEnd(); j++)
                            if (Math.hypot(l.mexes.get(i).dx() - l.mexes.get(j).dx(), l.mexes.get(i).dz() - l.mexes.get(j).dz()) < HOME_CROWD)
                                problems.add(tag + "home patches " + i + " and " + j + " crowd");
                    for (MetalOptions.Extra level : MetalOptions.Extra.values()) {
                        MapLayout o = MapLayout.build(preset, teams, seed, extra(level).withRimInset(MetalOptions.AUTO_RIM));
                        if (!MapMetalGameTests.homePositions(o).equals(MapMetalGameTests.homePositions(l)))
                            problems.add(tag + "the extra-metal level " + level + " moved the home metal");
                    }
                }
        if (!problems.isEmpty())
            helper.fail(MapMetalGameTests.fail(problems));
        helper.succeed();
    }

    /**
     * The extra metal is on top of the metal the map had (lovish: "we can add more mexes than we have"): on every
     * pre-made map at 2/4/8 players and two seeds, with or without the extra metal, no patch is lost from the mid-map
     * (farther than 40 blocks from the wall) against the baseline, and the largest gap from open ground to metal is not
     * larger than it was. LOW <= NORMAL <= HIGH extra patches, HIGH about double NORMAL where there is room (the land
     * maps at 2 and 4 players), and on those NORMAL carries at least 8 a slot. The five land maps leave no open ground
     * more than 60 blocks from metal (the old worst on them was 65 to 89; Highlands at 8 players keeps its 89, a ridge
     * nothing can walk to, as before). Logs the metal a seat earns a minute at full saturation for the PR's table.
     */
    @GameTest(template = ARENA, batch = "maps_metal", timeoutTicks = 400)
    public static void map_metal_extra_metal_is_on_top_of_the_old_map_and_spreads_over_it(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        Map<String, int[]> base = new HashMap<>();
        for (String row : BASELINE.trim().split("\\s+")) {
            String[] f = row.split(":");
            base.put(f[0] + ":" + f[1] + ":" + f[2], new int[] { Integer.parseInt(f[3]), Integer.parseInt(f[4]) });
        }
        for (MapPreset preset : MapMetalGameTests.premade())
            for (int n : new int[] { 2, 4, 8 })
                for (long seed : new long[] { 0L, 0x5EED0001L }) {
                    int[] teams = RealmMapGameTests.teams(n);
                    String tag = preset + " n=" + n + " seed " + seed + ": ";
                    int[] was = base.get(preset.name() + ":" + n + ":" + (seed == 0 ? "0" : "S"));
                    if (was == null) {
                        problems.add(tag + "no baseline row");
                        continue;
                    }
                    MapLayout none = MapLayout.build(preset, teams, seed, extra(MetalOptions.Extra.NONE));
                    MapLayout low = MapLayout.build(preset, teams, seed, extra(MetalOptions.Extra.LOW));
                    MapLayout normal = MapLayout.build(preset, teams, seed);
                    MapLayout high = MapLayout.build(preset, teams, seed, extra(MetalOptions.Extra.HIGH));
                    if (midPerSlot(none) < was[0])
                        problems.add(tag + "without extra metal the mid-map has " + midPerSlot(none) + " patches a slot, was " + was[0]);
                    if (midPerSlot(normal) < was[0])
                        problems.add(tag + "the mid-map has " + midPerSlot(normal) + " patches a slot, was " + was[0]);
                    int gap = largestGap(normal);
                    if (gap > was[1])
                        problems.add(tag + "the largest gap to metal is " + gap + " blocks, was " + was[1]);
                    if (extrasPerSlot(none) != 0 || !(extrasPerSlot(low) <= extrasPerSlot(normal) && extrasPerSlot(normal) <= extrasPerSlot(high)))
                        problems.add(tag + "extra patches a slot " + extrasPerSlot(none) + " / " + extrasPerSlot(low) + " / "
                            + extrasPerSlot(normal) + " / " + extrasPerSlot(high) + " are not none 0 <= low <= normal <= high");
                    if (!MapMetalGameTests.corePositions(normal).equals(MapMetalGameTests.corePositions(none)))
                        problems.add(tag + "the extra metal moved the core metal");
                    MapMetalGameTests.checkEqualSlots(normal, tag, problems);
                    MapMetalGameTests.checkEqualSlots(high, tag + "high ", problems);
                    if (LAND.contains(preset) && n <= 4) {
                        if (extrasPerSlot(normal) < 8)
                            problems.add(tag + "only " + extrasPerSlot(normal) + " extra patches a slot at normal");
                        if (extrasPerSlot(high) < 1.6 * extrasPerSlot(normal))
                            problems.add(tag + "high carries " + extrasPerSlot(high) + " extra patches a slot, normal " + extrasPerSlot(normal)
                                + ": not about double");
                    }
                    if (LAND.contains(preset) && !(preset == MapPreset.HIGHLANDS && n == 8) && gap > 60)
                        problems.add(tag + "open ground " + gap + " blocks from metal on a land map");
                    double[] before = null, after = metalPerMinute(normal), hi = metalPerMinute(high);
                    ReignOfNether.LOGGER.info("[MapExtra] {}: {} patches a slot ({} extra, {} high), mid-map {} (was {}), gap {} (was {}), "
                        + "metal a minute at full saturation tier 1 / tier 2: {} / {} (high {} / {}), core only {} / {}", tag.trim(),
                        normal.mexes.size() / n, extrasPerSlot(normal), extrasPerSlot(high), midPerSlot(normal), was[0], gap, was[1],
                        Math.round(after[0]), Math.round(after[1]), Math.round(hi[0]), Math.round(hi[1]),
                        Math.round(metalPerMinute(none)[0]), Math.round(metalPerMinute(none)[1]));
                }
        if (!problems.isEmpty())
            helper.fail(MapMetalGameTests.fail(problems));
        helper.succeed();
    }

    /**
     * Distance pays (lovish: "bonuses to players that reach to the outskirts"): on every land map the open-ground patches
     * in the outskirts (within MapLayout.OUTSKIRTS of the wall) yield more on average than those in the mid-map, which
     * yield more than a home patch (1.0); no patch yields less than 1.0; the low, normal and high levels are ordered in
     * yield; and every side's outskirts reward is the same (the outskirts' total yield per seat is equal).
     */
    @GameTest(template = ARENA, batch = "maps_metal")
    public static void map_metal_outskirts_pay_more_than_mid_pays_more_than_home(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        for (MapPreset preset : MapMetalGameTests.premade())
            for (int n : new int[] { 2, 4, 8 })
                for (long seed : SEEDS) {
                    int[] teams = RealmMapGameTests.teams(n);
                    MapLayout l = MapLayout.build(preset, teams, seed);
                    String tag = preset + " n=" + n + " seed " + seed + ": ";
                    double outSum = 0, midSum = 0;
                    int outN = 0, midN = 0;
                    double[] outPerSeat = new double[n];
                    for (int i = l.spreadFrom(); i < l.mexes.size(); i++) {
                        MapLayout.Mex m = l.mexes.get(i);
                        double edge = l.distanceToArenaEdge(m.dx(), m.dz());
                        if (m.value() < HomeMetal.VALUE)
                            problems.add(tag + "patch " + m.dx() + "," + m.dz() + " yields " + m.value() + ", less than a home patch");
                        if (edge <= MapLayout.OUTSKIRTS)
                            outPerSeat[m.slot()] += m.value();
                        if (m.slot() != 0)
                            continue;
                        if (edge <= MapLayout.OUTSKIRTS) {
                            outSum += m.value();
                            outN++;
                        } else {
                            midSum += m.value();
                            midN++;
                        }
                    }
                    for (int s = 1; s < n; s++)
                        if (Math.abs(outPerSeat[s] - outPerSeat[0]) > 1e-3)
                            problems.add(tag + "seat " + s + " has " + outPerSeat[s] + " outskirts metal, seat 0 " + outPerSeat[0]);
                    if (LAND.contains(preset) && outN >= 5 && midN >= 3) {
                        double out = outSum / outN, mid = midSum / midN;
                        if (!(out > mid + 0.1 && mid > HomeMetal.VALUE))
                            problems.add(tag + "yields: outskirts " + out + ", mid " + mid + ", home " + HomeMetal.VALUE);
                    }
                    if (LAND.contains(preset) && n <= 4) {
                        double lo = openYield(MapLayout.build(preset, teams, seed, extra(MetalOptions.Extra.LOW))),
                            no = openYield(l), hi = openYield(MapLayout.build(preset, teams, seed, extra(MetalOptions.Extra.HIGH)));
                        if (!(lo < no && no < hi))
                            problems.add(tag + "mean open-ground yield low " + lo + " normal " + no + " high " + hi + " is not rising");
                    }
                }
        if (!problems.isEmpty())
            helper.fail(MapMetalGameTests.fail(problems));
        helper.succeed();
    }

    private static double openYield(MapLayout l) {
        double sum = 0;
        int k = 0;
        for (int i = l.spreadFrom(); i < l.mexes.size(); i++)
            if (l.mexes.get(i).slot() == 0) {
                sum += l.mexes.get(i).value();
                k++;
            }
        return k == 0 ? 0 : sum / k;
    }
}
