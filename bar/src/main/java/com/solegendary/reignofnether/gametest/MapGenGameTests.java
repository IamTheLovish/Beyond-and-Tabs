package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.maps.MapLayout;
import com.solegendary.reignofnether.maps.MapPreset;
import com.solegendary.reignofnether.maps.MetalOptions;
import com.solegendary.reignofnether.maps.gen.GenMap;
import com.solegendary.reignofnether.maps.gen.GenOptions;
import com.solegendary.reignofnether.maps.gen.GenSeed;
import com.solegendary.reignofnether.maps.gen.MapGenerator;
import com.solegendary.reignofnether.maps.gen.Modifier;
import com.solegendary.reignofnether.startpos.BattlefieldWall;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * The generated maps (design/maps.md "Generated maps", lovish Oct 21 22:28): MapPreset.GENERATED through MapLayout, as
 * the lobby preview, the builder and the start phase read it. A generated map is not fair by construction like the
 * hand-made ones (it is natural and its sides differ on purpose), so these tests check what the generator promises
 * instead: the same seed always draws the same map (the preview is the map played), every seat reaches every seat,
 * the middle and all of its economy by ground, every patch stands on a level pad, nothing is placed in a reserved zone
 * or past the rim inset, the seats' measured fairness is within the tolerance, square arenas answer the edge helpers,
 * and the lobby's options ride the seed. Pure maths (no world access), in their own batch.
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class MapGenGameTests {

    static final String ARENA = SkirmishGameTests.ARENA;
    static final int[] COUNTS = { 2, 4, 8 };
    /** Packed seeds: round and square, modifiers on (the lobby's defaults otherwise). */
    static final long[] SEEDS = {
        GenSeed.pack(0x5EED0001L, 1, 0), GenSeed.pack(0x5EED0002L, 2, 0),
        GenSeed.pack(0x5EED0003L, 1, 0), GenSeed.pack(0x5EED0004L, 2, 0) };

    static MapLayout build(int n, long seed) {
        return MapLayout.build(MapPreset.GENERATED, RealmMapGameTests.teams(n), seed);
    }

    static String tag(int n, long seed) {
        return "n=" + n + " seed " + Long.toHexString(seed) + ": ";
    }

    /** A hash of what a match is built from: every 2nd column's ground, the patches, sites, vents, seats and boxes. */
    static long fingerprint(MapLayout l) {
        long h = 1469598103934665603L;
        MapLayout.Sample s = new MapLayout.Sample();
        int e = l.arenaHalfExtent();
        for (int x = -e; x <= e; x += 2)
            for (int z = -e; z <= e; z += 2) {
                l.sample(x, z, s);
                h = (h ^ (s.height * 31L + s.surface.ordinal() * 7L + (s.forest ? 1 : 0))) * 1099511628211L;
            }
        for (MapLayout.Mex m : l.mexes)
            h = (h ^ (m.dx() * 73856093L ^ m.dz() * 19349663L ^ (long) (m.value() * 8) ^ m.slot())) * 1099511628211L;
        for (MapLayout.Site st : l.sites)
            h = (h ^ (st.dx() * 73856093L ^ st.dz() * 19349663L ^ st.kind().ordinal())) * 1099511628211L;
        for (MapLayout.Spot v : l.vents)
            h = (h ^ (v.dx() * 73856093L ^ v.dz() * 19349663L)) * 1099511628211L;
        for (MapLayout.Slot sl : l.slots)
            h = (h ^ (sl.dx() * 73856093L ^ sl.dz() * 19349663L ^ sl.team())) * 1099511628211L;
        for (MapLayout.Box b : l.boxes)
            h = (h ^ Double.doubleToLongBits(b.a0()) ^ Double.doubleToLongBits(b.r1())) * 1099511628211L;
        return h;
    }

    /**
     * Deterministic (the lobby preview IS the map played, on another machine and after a reload): the same seed and
     * seats draw the same ground, economy, seats and boxes twice; two seeds draw two different maps.
     */
    @GameTest(template = ARENA, batch = "maps_gen", timeoutTicks = 400)
    public static void generated_map_is_the_same_map_from_the_same_seed(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        for (int n : COUNTS) {
            long first = 0;
            for (int k = 0; k < 2; k++) {
                long seed = SEEDS[k];
                long a = fingerprint(build(n, seed)), b = fingerprint(build(n, seed));
                if (a != b)
                    problems.add(tag(n, seed) + "two builds differ");
                if (k == 0)
                    first = a;
                else if (a == first)
                    problems.add(tag(n, seed) + "the same map as seed " + Long.toHexString(SEEDS[0]));
            }
        }
        if (!problems.isEmpty())
            helper.fail(MapMetalGameTests.fail(problems));
        helper.succeed();
    }

    /**
     * Every seat reaches every other seat and the plaza by ground (MapLayout.reachFrom: steps of one, no deep water,
     * lava, void or tree stem), and every patch, site and vent is reached - the generator drops what is not, never
     * leaving a patch on an island.
     */
    @GameTest(template = ARENA, batch = "maps_gen", timeoutTicks = 400)
    public static void generated_map_every_seat_reaches_everything(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        int patches = 0;
        for (int n : COUNTS)
            for (long seed : SEEDS) {
                MapLayout l = build(n, seed);
                String tag = tag(n, seed);
                if (!l.gen.valid)
                    problems.add(tag + "the generator reports " + l.gen.invalidWhy);
                MapLayout.Slot s0 = l.slots.get(0);
                MapLayout.Reach reach = l.reachFrom(s0.dx(), s0.dz());
                for (MapLayout.Slot sl : l.slots)
                    if (!reach.containsNear(sl.dx(), sl.dz(), 1))
                        problems.add(tag + "seat " + sl.index() + " is cut off from seat 0");
                if (!reach.containsNear(0, 0, 6))
                    problems.add(tag + "the middle is cut off");
                for (MapLayout.Mex m : l.mexes) {
                    patches++;
                    if (!reach.containsNear(m.dx(), m.dz(), 1))
                        problems.add(tag + "patch " + m.dx() + "," + m.dz() + " is not reached by ground");
                }
                for (MapLayout.Site st : l.sites)
                    if (!reach.containsNear(st.dx(), st.dz(), 4))
                        problems.add(tag + st.kind() + " site at " + st.dx() + "," + st.dz() + " is not reached by ground");
                for (MapLayout.Spot v : l.vents)
                    if (!reach.containsNear(v.dx(), v.dz(), 3))
                        problems.add(tag + "vent at " + v.dx() + "," + v.dz() + " is not reached by ground");
            }
        ReignOfNether.LOGGER.info("[MapGen] {} patches checked for reach", patches);
        if (!problems.isEmpty())
            helper.fail(MapMetalGameTests.fail(problems));
        helper.succeed();
    }

    /**
     * No patch on a drop (maps spec R2): the whole 7x7 pad of every patch is dry land within a block of its centre,
     * no patch within 7 of deep water, and none nearer the arena's edge than the rim inset (the wall's walkway).
     */
    @GameTest(template = ARENA, batch = "maps_gen", timeoutTicks = 400)
    public static void generated_map_patches_stand_on_level_ground(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        MapLayout.Sample s = new MapLayout.Sample();
        for (int n : COUNTS)
            for (long seed : SEEDS) {
                MapLayout l = build(n, seed);
                String tag = tag(n, seed);
                for (MapLayout.Mex m : l.mexes) {
                    String fault = MapMetalGameTests.padFault(l, m.dx(), m.dz(), s);
                    if (fault != null)
                        problems.add(tag + "patch " + m.dx() + "," + m.dz() + " has " + fault + " in its pad");
                    if (l.distanceToArenaEdge(m.dx(), m.dz()) < MetalOptions.MIN_RIM_INSET)
                        problems.add(tag + "patch " + m.dx() + "," + m.dz() + " is " + (int) l.distanceToArenaEdge(m.dx(), m.dz())
                            + " from the edge");
                    for (int a = -7; a <= 7; a++)
                        for (int b = -7; b <= 7; b++) {
                            l.sample(m.dx() + a, m.dz() + b, s);
                            if (s.surface == MapLayout.Surface.WATER && s.height < -1) {
                                problems.add(tag + "patch " + m.dx() + "," + m.dz() + " has deep water within 7");
                                a = b = 99;
                            }
                        }
                }
            }
        if (!problems.isEmpty())
            helper.fail(MapMetalGameTests.fail(problems));
        helper.succeed();
    }

    /**
     * Reserved zones (MetalOptions.reserved, the hero jungle's hook): the generator's own wild ground and a zone the
     * caller reserves both stay free of metal, and the jungle's entry and exit lie on its edge.
     */
    @GameTest(template = ARENA, batch = "maps_gen", timeoutTicks = 400)
    public static void generated_map_leaves_reserved_ground_alone(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        // the caller's zone: a 40-block disc on the axis between the sides, where the fill would otherwise place
        MetalOptions.ReservedZones disc = (dx, dz) -> (dx - 60) * (dx - 60) + dz * dz <= 40 * 40;
        MetalOptions metal = MetalOptions.DEFAULT.withReserved(disc);
        int zones = 0;
        for (int n : COUNTS)
            for (long seed : SEEDS) {
                MapLayout l = MapLayout.build(MapPreset.GENERATED, RealmMapGameTests.teams(n), seed, metal);
                String tag = tag(n, seed);
                for (MapLayout.Mex m : l.mexes) {
                    if (disc.reserves(m.dx(), m.dz()))
                        problems.add(tag + "patch " + m.dx() + "," + m.dz() + " in the caller's reserved disc");
                    for (GenMap.Jungle j : l.gen.jungle)
                        if (j.contains(m.dx(), m.dz()))
                            problems.add(tag + "patch " + m.dx() + "," + m.dz() + " in the jungle");
                }
                for (GenMap.Jungle j : l.gen.jungle) {
                    zones++;
                    for (int[] door : new int[][] { j.entry(), j.exit() }) {
                        double rim = Double.MAX_VALUE;
                        for (int[] d : j.discs())
                            rim = Math.min(rim, Math.abs(Math.hypot(door[0] - d[0], door[1] - d[1]) - d[2]));
                        if (rim > 2.5)
                            problems.add(tag + "a jungle door at " + door[0] + "," + door[1] + " is not on its edge");
                    }
                }
            }
        ReignOfNether.LOGGER.info("[MapGen] {} jungle zones checked", zones);
        if (!problems.isEmpty())
            helper.fail(MapMetalGameTests.fail(problems));
        helper.succeed();
    }

    /**
     * The seats' measured fairness (maps/gen/Fairness: metal per second over 15 minutes, objectives, defence, tempo,
     * space) after balancing is within the default tolerance (lovish Oct 21: "within ~5% of the others"), and the two
     * teams' means within it too. Every seat also starts on the ground its capitol needs, inside its team's box.
     */
    @GameTest(template = ARENA, batch = "maps_gen", timeoutTicks = 400)
    public static void generated_map_seats_are_even_within_tolerance(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        double worst = 0;
        for (int n : COUNTS)
            for (long seed : SEEDS) {
                MapLayout l = build(n, seed);
                String tag = tag(n, seed);
                double spread = l.gen.report.spread(), team = l.gen.report.teamSpread();
                worst = Math.max(worst, spread);
                if (spread > GenOptions.DEFAULT_TOLERANCE + 1e-9)
                    problems.add(tag + String.format("seat spread %.1f%%", 100 * spread));
                if (team > GenOptions.DEFAULT_TOLERANCE + 1e-9)
                    problems.add(tag + String.format("team spread %.1f%%", 100 * team));
                for (MapLayout.Slot sl : l.slots) {
                    if (!l.startGround(sl.dx(), sl.dz()))
                        problems.add(tag + "seat " + sl.index() + " starts on ground a capitol cannot take");
                    if (l.boxOf(sl.team()) == null || !l.boxOf(sl.team()).contains(sl.dx(), sl.dz()))
                        problems.add(tag + "seat " + sl.index() + " is outside its team's box");
                }
            }
        ReignOfNether.LOGGER.info("[MapGen] worst seat spread {}%", String.format("%.1f", 100 * worst));
        if (!problems.isEmpty())
            helper.fail(MapMetalGameTests.fail(problems));
        helper.succeed();
    }

    /**
     * Square arenas (lovish Oct 21 21:49): the edge is the nearest side (distanceToArenaEdge, insideArena), the corners
     * lie inside the arena and inside its outer radius, the wall's square line has eight half sides of columns, and a
     * round generated arena still answers as a circle.
     */
    @GameTest(template = ARENA, batch = "maps_gen", timeoutTicks = 400)
    public static void generated_square_arena_answers_the_edge_helpers(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        MapLayout sq = build(4, GenSeed.pack(0x5EED0010L, 2, 1));
        MapLayout rd = build(4, GenSeed.pack(0x5EED0010L, 1, 1));
        int h = sq.radius;
        if (!sq.squareArena() || rd.squareArena())
            problems.add("square " + sq.squareArena() + ", round " + rd.squareArena());
        if (Math.abs(sq.distanceToArenaEdge(h - 10, h - 30) - 10) > 1e-9)
            problems.add("a point 10 inside the east side is " + sq.distanceToArenaEdge(h - 10, h - 30) + " from the edge");
        if (!sq.insideArena(h - 2, h - 2) || sq.insideArena(h + 2, 0))
            problems.add("corner inside " + sq.insideArena(h - 2, h - 2) + ", past the side inside " + sq.insideArena(h + 2, 0));
        if (sq.outerRadius() < Math.hypot(h, h) - 1e-9)
            problems.add("outer radius " + sq.outerRadius() + " does not hold the corners (" + Math.hypot(h, h) + ")");
        if (Math.abs(rd.distanceToArenaEdge(0, rd.radius - 5) - 5) > 1e-9 || rd.outerRadius() != rd.radius)
            problems.add("the round arena's edge is not a circle");
        int cols = BattlefieldWall.squareLength(0.5, 0.5, h);
        if (cols != 8 * h)
            problems.add("the square wall has " + cols + " columns, expected " + 8 * h);
        if (!problems.isEmpty())
            helper.fail(MapMetalGameTests.fail(problems));
        helper.succeed();
    }

    /**
     * The lobby's options ride the seed (GenSeed): shape and modifiers round-trip through the top byte, the landscape
     * seed is the low 56 bits, a seed without the flag plays the defaults; modifiers off rolls none, the lobby rolls
     * only modifiers whose mechanic exists, and the shape asked for is the shape drawn.
     */
    @GameTest(template = ARENA, batch = "maps_gen", timeoutTicks = 400)
    public static void generated_map_options_ride_the_seed(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        for (int shape = 0; shape < GenSeed.SHAPES.length; shape++)
            for (int mods = 0; mods < GenSeed.MODIFIERS.length; mods++) {
                long p = GenSeed.pack(0x0123456789ABCDEFL, shape, mods);
                if (GenSeed.shapeIndex(p) != shape || GenSeed.modifiersIndex(p) != mods)
                    problems.add("pack(" + shape + ", " + mods + ") reads back " + GenSeed.shapeIndex(p) + ", " + GenSeed.modifiersIndex(p));
                if (GenSeed.generatorSeed(p) != (0x0123456789ABCDEFL & 0x00FFFFFFFFFFFFFFL))
                    problems.add("pack changed the landscape seed");
            }
        if (!GenSeed.options(0x7FEDCBA987654321L).equals(GenOptions.DEFAULT))
            problems.add("a seed without options does not play the defaults");
        for (long s = 1; s <= 6; s++) {
            MapLayout off = build(2, GenSeed.pack(s * 7919L, 0, 1));
            if (!off.gen.modifiers.isEmpty())
                problems.add("modifiers off rolled " + off.gen.modifiers);
            MapLayout on = build(2, GenSeed.pack(s * 7919L, 0, 2));
            for (Modifier m : on.gen.modifiers)
                if (!m.live)
                    problems.add("the lobby rolled " + m + ", which needs " + m.needs);
            for (int shape = 1; shape <= 2; shape++) {
                MapLayout l = build(2, GenSeed.pack(s * 7919L, shape, 0));
                if ((shape == 2) != l.squareArena())
                    problems.add("shape " + GenSeed.SHAPES[shape] + " drew " + l.gen.shape);
            }
        }
        if (!problems.isEmpty())
            helper.fail(MapMetalGameTests.fail(problems));
        helper.succeed();
    }

    /** What the generator draws matches MapGenerator.generate's own call (the layout does not alter the map). */
    @GameTest(template = ARENA, batch = "maps_gen", timeoutTicks = 400)
    public static void generated_layout_carries_the_generators_map(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        long seed = GenSeed.pack(0x5EED0020L, 0, 0);
        MapLayout l = build(4, seed);
        GenMap g = MapGenerator.generate(RealmMapGameTests.teams(4), GenSeed.generatorSeed(seed), GenSeed.options(seed),
            MetalOptions.DEFAULT);
        if (l.mexes.size() != g.mexes.size() || l.sites.size() != g.sites.size() || l.vents.size() != g.vents.size())
            problems.add("layout " + l.mexes.size() + "/" + l.sites.size() + "/" + l.vents.size() + " vs generator "
                + g.mexes.size() + "/" + g.sites.size() + "/" + g.vents.size());
        for (int i = 0; i < l.slots.size(); i++)
            if (Math.abs(l.slotAngle(i) - l.slots.get(i).angle()) > 1e-9)
                problems.add("slot " + i + " angle " + l.slots.get(i).angle() + " but the layout's ring says " + l.slotAngle(i));
        if (l.radius != g.half || l.ring != g.ring)
            problems.add("radius/ring " + l.radius + "/" + l.ring + " vs " + g.half + "/" + g.ring);
        if (!problems.isEmpty())
            helper.fail(MapMetalGameTests.fail(problems));
        helper.succeed();
    }
}
