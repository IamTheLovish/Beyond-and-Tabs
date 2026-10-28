package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.api.ReignOfNetherRegistries;
import com.solegendary.reignofnether.building.Building;
import com.solegendary.reignofnether.building.buildings.shared.MetalExtractor;
import com.solegendary.reignofnether.maps.ExtractorClearance;
import com.solegendary.reignofnether.maps.MapLayout;
import com.solegendary.reignofnether.maps.MapPreset;
import com.solegendary.reignofnether.maps.MetalOptions;
import com.solegendary.reignofnether.resources.MetalPatches;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The metal placement of the pre-made maps (maps spec M1, design/maps.md): the flood-fill reach that replaced the
 * straight-line walk, flat ground under the whole 7x7 pad of every patch, the lobby's metal richness reaching these maps,
 * the rim limit (the extractor clearance) and the reserved-zone hook. Pure maths on MapLayout (no world access), in their own batch
 * "maps_metal" so the layout sweeps never hold up another batch.
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class MapMetalGameTests {

    static final String ARENA = SkirmishGameTests.ARENA;
    static final int[] COUNTS = { 2, 3, 4, 6, 8, 16 };

    static List<MapPreset> premade() {
        return MapSeedGameTests.premade();
    }

    static String fail(List<String> problems) {
        return problems.size() + " problem(s): " + String.join("; ", problems.subList(0, Math.min(8, problems.size())));
    }

    /** Why the 7x7 pad round (dx, dz) is not level dry ground (null when it is); a BRIDGE column is a plaza floor. */
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

    static int[] countPerSlot(MapLayout l) {
        int[] count = new int[l.n];
        for (MapLayout.Mex m : l.mexes)
            count[m.slot()]++;
        return count;
    }

    /** Every slot owns the same number of patches and the same value (exact: the slots are copies of one list). */
    static void checkEqualSlots(MapLayout l, String tag, List<String> problems) {
        int[] count = countPerSlot(l);
        for (int s = 1; s < l.n; s++) {
            if (count[s] != count[0])
                problems.add(tag + "slot " + s + " has " + count[s] + " patches, slot 0 " + count[0]);
            if (Math.abs(l.mexValueOfSlot(s) - l.mexValueOfSlot(0)) > 1e-3f)
                problems.add(tag + "slot " + s + " owns " + l.mexValueOfSlot(s) + " metal, slot 0 " + l.mexValueOfSlot(0));
        }
    }

    /**
     * Level ground under every patch (bug 2): the whole 7x7 pad, not the centre column, is land within a block of the
     * centre's height - on every map, for 2 to 16 players. On Highlands at 8 players 20 of 96 patches stood on the
     * plateau's edge with an 8-block drop inside the pad, and the fairness test, which sampled the centre column, passed.
     */
    @GameTest(template = ARENA, batch = "maps_metal")
    public static void map_metal_pad_is_level_dry_ground_under_every_patch(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        MapLayout.Sample s = new MapLayout.Sample();
        int checked = 0;
        for (MapPreset preset : premade())
            for (int n : COUNTS)
                for (long seed : new long[] { 0L, 0x5EED0001L }) {
                    MapLayout l = MapLayout.build(preset, RealmMapGameTests.teams(n), seed);
                    String tag = preset + " n=" + n + " seed " + seed + ": ";
                    for (MapLayout.Mex m : l.mexes) {
                        checked++;
                        String fault = padFault(l, m.dx(), m.dz(), s);
                        if (fault != null)
                            problems.add(tag + "patch " + m.dx() + "," + m.dz() + " has " + fault + " in its pad");
                    }
                    checkEqualSlots(l, tag, problems);
                }
        ReignOfNether.LOGGER.info("[MapMetal] {} patch pads checked", checked);
        if (!problems.isEmpty())
            helper.fail(fail(problems));
        helper.succeed();
    }

    /**
     * Reachable by ground (bug 1): every open-ground patch lies on a column the 4-connected flood from its own start
     * reaches (steps of one block, no void, lava, deep water or tree stem) - hills and cliffs included, which the
     * straight-line walk refused. The template patches (home ring, rich pairs, centre) are not asked: some maps cut
     * them off by design or by noise, which is logged as a [MapMetal] notice so it can be seen, not hidden.
     */
    @GameTest(template = ARENA, batch = "maps_metal")
    public static void map_metal_open_ground_patches_are_reachable_by_flood(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        int spreadTotal = 0, templateCut = 0, templateTotal = 0;
        for (MapPreset preset : premade())
            for (int n : COUNTS)
                for (long seed : new long[] { 0L, 0x5EED0001L }) {
                    MapLayout l = MapLayout.build(preset, RealmMapGameTests.teams(n), seed);
                    String tag = preset + " n=" + n + " seed " + seed + ": ";
                    for (MapLayout.Slot sl : l.slots) {
                        MapLayout.Reach reach = l.reachFrom(sl.dx(), sl.dz());
                        for (int i = 0; i < l.mexes.size(); i++) {
                            MapLayout.Mex m = l.mexes.get(i);
                            if (m.slot() != sl.index())
                                continue;
                            boolean ok = reach.contains(m.dx(), m.dz());
                            if (i >= l.spreadFrom()) {
                                spreadTotal++;
                                if (!ok)
                                    problems.add(tag + "open-ground patch " + m.dx() + "," + m.dz() + " not reachable from slot " + sl.index());
                            } else {
                                templateTotal++;
                                if (!ok)
                                    templateCut++;
                            }
                        }
                    }
                }
        ReignOfNether.LOGGER.info("[MapMetal] {} open-ground patches reachable; {} of {} template patches are cut off from their start by ground",
            spreadTotal, templateCut, templateTotal);
        if (!problems.isEmpty())
            helper.fail(fail(problems));
        helper.succeed();
    }

    /**
     * Hills no longer starve the fill (bug 1): Highlands, the one map with real height, keeps at least 20 patches a slot
     * at 2 and 4 players (it had 20 and 16, at 4 players because the straight line refused 15 of 17 candidates), and
     * the largest bare gap on its open ground stays under 80 blocks (it was 171 at 1v1).
     */
    @GameTest(template = ARENA, batch = "maps_metal")
    public static void map_metal_highlands_fills_behind_its_ridges(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        MapLayout.Sample s = new MapLayout.Sample();
        for (int n : new int[] { 2, 4 }) {
            MapLayout l = MapLayout.build(MapPreset.HIGHLANDS, RealmMapGameTests.teams(n));
            int perSlot = l.mexes.size() / n;
            if (perSlot < 20)
                problems.add("Highlands n=" + n + " has " + perSlot + " patches a slot");
            double worst = 0;
            int r = l.radius - 8;
            for (int x = -r; x <= r; x += 4)
                for (int z = -r; z <= r; z += 4) {
                    if (!l.insideArena(x, z) || l.distanceToArenaEdge(x, z) < 8)
                        continue;
                    l.sample(x, z, s);
                    if (s.surface == MapLayout.Surface.VOID || s.surface == MapLayout.Surface.LAVA
                            || s.surface == MapLayout.Surface.WATER)
                        continue;
                    double d = Double.MAX_VALUE;
                    for (MapLayout.Mex m : l.mexes)
                        d = Math.min(d, Math.hypot(m.dx() - x, m.dz() - z));
                    worst = Math.max(worst, d);
                }
            if (worst >= 80)
                problems.add("Highlands n=" + n + ": open ground " + Math.round(worst) + " blocks from metal");
        }
        if (!problems.isEmpty())
            helper.fail(fail(problems));
        helper.succeed();
    }

    /**
     * The lobby's metal richness reaches pre-made maps (bug 3). Lean, normal and rich (0.65 / 1.0 / 1.5) give a
     * non-decreasing core patch count per slot (the home set, templates and core fill; the extra metal on top is its own
     * knob) on every map, with every lean patch also in the normal map and every normal
     * patch in the rich one (more metal never moves the metal that was there), strictly more rich than lean on
     * Verdant Valley and Highlands, and normal (1.0) is exactly the map every build drew before the setting applied.
     */
    @GameTest(template = ARENA, batch = "maps_metal")
    public static void map_metal_lobby_richness_scales_the_open_ground(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        for (MapPreset preset : premade())
            for (int n : new int[] { 2, 4, 8 }) {
                int[] teams = RealmMapGameTests.teams(n);
                MapLayout lean = MapLayout.build(preset, teams, 0L, MetalOptions.ofRichness(0.65f));
                MapLayout normal = MapLayout.build(preset, teams, 0L, MetalOptions.ofRichness(1.0f));
                MapLayout rich = MapLayout.build(preset, teams, 0L, MetalOptions.ofRichness(1.5f));
                MapLayout legacy = MapLayout.build(preset, teams, 0L);
                String tag = preset + " n=" + n + ": ";
                if (!MapSeedGameTests.fingerprint(normal).equals(MapSeedGameTests.fingerprint(legacy)))
                    problems.add(tag + "richness 1.0 is not the default map");
                // the core fill (what richness scales); the extra metal is laid on top of it and depends on it
                if (lean.extraFrom() > normal.extraFrom() || normal.extraFrom() > rich.extraFrom())
                    problems.add(tag + "core patches " + lean.extraFrom() + " / " + normal.extraFrom() + " / " + rich.extraFrom()
                        + " are not lean <= normal <= rich");
                if ((preset == MapPreset.VERDANT_VALLEY || preset == MapPreset.HIGHLANDS) && n <= 4
                        && lean.extraFrom() >= rich.extraFrom())
                    problems.add(tag + "the setting changed nothing: " + lean.extraFrom() + " vs " + rich.extraFrom());
                if (!corePositions(normal).containsAll(corePositions(lean)))
                    problems.add(tag + "a lean patch is missing from the normal map");
                if (!corePositions(rich).containsAll(corePositions(normal)))
                    problems.add(tag + "a normal patch is missing from the rich map");
                if (!homePositions(normal).equals(homePositions(lean)) || !homePositions(normal).equals(homePositions(rich)))
                    problems.add(tag + "the metal setting moved the home metal");
                checkEqualSlots(lean, tag + "lean ", problems);
                checkEqualSlots(rich, tag + "rich ", problems);
            }
        if (!problems.isEmpty())
            helper.fail(fail(problems));
        helper.succeed();
    }

    /** Positions (and, for the core fill, yields) of the home set, the templates and the core fill: everything before the extra metal. */
    static Set<String> corePositions(MapLayout l) {
        Set<String> out = new HashSet<>();
        for (int i = 0; i < l.extraFrom(); i++)
            out.add(l.mexes.get(i).dx() + "," + l.mexes.get(i).dz());
        return out;
    }

    static Set<String> homePositions(MapLayout l) {
        Set<String> out = new HashSet<>();
        for (int i = 0; i < l.homeEnd(); i++)
            out.add(l.mexes.get(i).dx() + "," + l.mexes.get(i).dz() + "," + l.mexes.get(i).value());
        return out;
    }

    /**
     * The rim limit is the extractor clearance (lovish Oct 22: "edgeline means maybe enough blocks away that the
     * extractor doesn't get blocked by the wall"), read from the building definitions: all fourteen extractor structures
     * (seven factions, the build and the tier-2 swap) are read, the footprint the clearance was derived from is the
     * placement code's own (MetalPatches.EXTRACTOR_HALF, RESERVED_HALF, PAD_HALF, MapLayout.PAD), the upgrade fits where
     * the old extractor stood, the default map keeps every open-ground patch that far from the edge and the edge band is
     * ON (open-ground patches stand inside the old 22-block limit); a larger knob keeps the fill further in, a smaller
     * one never goes below the clearance, and either way every slot keeps the same patches.
     */
    @GameTest(template = ARENA, batch = "maps_metal")
    public static void map_metal_rim_inset_is_the_extractor_clearance(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        ExtractorClearance.Parts p = ExtractorClearance.parts();
        ReignOfNether.LOGGER.info("[MapMetal] extractor clearance: {}", p);
        List<String> names = ExtractorClearance.structureNames();
        if (names.size() != 14 || p.structures() != names.size())
            problems.add("expected 14 extractor structures (7 factions x build and tier 2), found " + names.size()
                + ", read " + p.structures() + " (a fallback means the files could not be read)");
        if (p.footprintSide() != ExtractorClearance.FALLBACK_FOOTPRINT)
            problems.add("the biggest extractor is " + p.footprintSide() + " wide (" + p.widest() + "), the fallback says "
                + ExtractorClearance.FALLBACK_FOOTPRINT + ": update FALLBACK_FOOTPRINT and the class doc");
        if (p.blocks() != ExtractorClearance.derive(ExtractorClearance.FALLBACK_FOOTPRINT, 0, "").blocks())
            problems.add("the read clearance " + p.blocks() + " differs from the fallback's");
        if (MetalPatches.EXTRACTOR_HALF != p.footprintReach())
            problems.add("MetalPatches.EXTRACTOR_HALF " + MetalPatches.EXTRACTOR_HALF + " is not the footprint's reach " + p.footprintReach());
        if (MetalPatches.RESERVED_HALF != p.footprintReach() + MetalPatches.BUILDING_GAP)
            problems.add("MetalPatches.RESERVED_HALF is not the footprint plus the building gap");
        if (MetalPatches.PAD_HALF != MapLayout.PAD)
            problems.add("the pad MetalPatches.stamp levels (" + MetalPatches.PAD_HALF + ") is not the pad the layout keeps level (" + MapLayout.PAD + ")");
        // the upgrade swaps the structure in place: it must not reach further than the extractor it replaces
        for (Building b : ReignOfNetherRegistries.BUILDING)
            if (b instanceof MetalExtractor me) {
                int[] t1 = ExtractorClearance.extent(me.getUpgradedStructureName(0)), t2 = ExtractorClearance.extent(me.getUpgradedStructureName(1));
                if (t1 == null || t2 == null)
                    problems.add(me.variant + ": an extractor structure could not be read");
                else if (t2[0] > t1[0] || t2[1] > t1[1] || t1[0] > p.footprintSide() || t1[1] > p.footprintSide())
                    problems.add(me.variant + ": the tier-2 structure " + t2[0] + "x" + t2[1] + " does not fit the tier-1's " + t1[0] + "x" + t1[1]);
            }
        int inside22 = 0;
        for (MapPreset preset : premade())
            for (int n : new int[] { 2, 4, 8 }) {
                int[] teams = RealmMapGameTests.teams(n);
                MapLayout def = MapLayout.build(preset, teams);
                MapLayout far = MapLayout.build(preset, teams, 0L, MetalOptions.DEFAULT.withRimInset(30));
                MapLayout low = MapLayout.build(preset, teams, 0L, MetalOptions.DEFAULT.withRimInset(4));
                String tag = preset + " n=" + n + ": ";
                if (def.metal.rimInset() != p.blocks() || def.metal.rimInset() < 10)
                    problems.add(tag + "default rim inset is " + def.metal.rimInset() + ", the clearance is " + p.blocks());
                if (low.metal.rimInset() != p.blocks() || !MapSeedGameTests.fingerprint(low).equals(MapSeedGameTests.fingerprint(def)))
                    problems.add(tag + "a rim knob below the clearance changed the map");
                // 0.75: the fill judges the unrounded position, the patch is then rounded to a block
                for (int i = def.spreadFrom(); i < def.mexes.size(); i++) {
                    MapLayout.Mex m = def.mexes.get(i);
                    double edge = def.distanceToArenaEdge(m.dx(), m.dz());
                    if (edge < p.blocks() - 0.75)
                        problems.add(tag + "open-ground patch " + m.dx() + "," + m.dz() + " is " + edge + " from the edge, inside the clearance");
                    if (edge < 21.5)
                        inside22++;
                }
                for (int i = far.spreadFrom(); i < far.mexes.size(); i++) {
                    MapLayout.Mex m = far.mexes.get(i);
                    if (far.distanceToArenaEdge(m.dx(), m.dz()) < 29.25)
                        problems.add(tag + "open-ground patch " + m.dx() + "," + m.dz() + " inside the rim knob's limit of 30");
                }
                checkEqualSlots(def, tag, problems);
                checkEqualSlots(far, tag + "rim 30 ", problems);
            }
        ReignOfNether.LOGGER.info("[MapMetal] the edge band is on: {} open-ground patches stand inside the old 22-block limit", inside22);
        if (inside22 == 0)
            problems.add("no open-ground patch stands inside the old 22-block limit: the edge band is still off");
        if (!problems.isEmpty())
            helper.fail(fail(problems));
        helper.succeed();
    }

    /**
     * The hook for the hero jungle (lovish, Oct 21 21:47: reserve areas later): no patch, template or open-ground,
     * stands in a reserved zone, whichever slot's copy it is, and every slot still keeps the same patches. With no zone
     * reserved the map is exactly the default one. A zone of radius 45 is reserved at the middle of every map's wedge
     * between slot 0 and 1 for the check (a mask that is not itself symmetric: it costs a spot in every slot alike).
     */
    @GameTest(template = ARENA, batch = "maps_metal")
    public static void map_metal_reserved_zones_are_left_alone(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        for (MapPreset preset : premade())
            for (int n : new int[] { 2, 4, 8 }) {
                int[] teams = RealmMapGameTests.teams(n);
                MapLayout plain = MapLayout.build(preset, teams, 0L, MetalOptions.DEFAULT.withReserved(MetalOptions.ReservedZones.NONE));
                MapLayout def = MapLayout.build(preset, teams);
                String tag = preset + " n=" + n + ": ";
                if (!MapSeedGameTests.fingerprint(plain).equals(MapSeedGameTests.fingerprint(def)))
                    problems.add(tag + "an empty reserved mask changed the map");
                double zx = def.ring * 0.55 * Math.cos(def.slotAngle(0) + def.half),
                    zz = def.ring * 0.55 * Math.sin(def.slotAngle(0) + def.half);
                MetalOptions.ReservedZones zone = (x, z) -> Math.hypot(x - zx, z - zz) < 45;
                MapLayout l = MapLayout.build(preset, teams, 0L, MetalOptions.DEFAULT.withReserved(zone));
                for (MapLayout.Mex m : l.mexes)
                    if (zone.reserves(m.dx(), m.dz()))
                        problems.add(tag + "patch " + m.dx() + "," + m.dz() + " inside the reserved zone");
                checkEqualSlots(l, tag + "reserved ", problems);
            }
        if (!problems.isEmpty())
            helper.fail(fail(problems));
        helper.succeed();
    }

    /**
     * The arena's edge goes through one helper (lovish, Oct 21 21:49: square maps will come later): a circle today.
     * Pinned here so a square shape changes the helper and this test together, nowhere else.
     */
    @GameTest(template = ARENA, batch = "maps_metal")
    public static void map_metal_arena_edge_is_asked_of_one_helper(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        for (MapPreset preset : premade()) {
            MapLayout l = MapLayout.build(preset, RealmMapGameTests.teams(4));
            if (Math.abs(l.distanceToArenaEdge(0, 0) - l.radius) > 1e-9)
                problems.add(preset + ": the centre is " + l.distanceToArenaEdge(0, 0) + " from the edge, radius " + l.radius);
            if (!l.insideArena(l.radius, 0) || l.insideArena(l.radius + 1, 0) || l.insideArena(0, -l.radius - 1))
                problems.add(preset + ": insideArena does not follow the radius");
            if (l.arenaHalfExtent() < l.radius)
                problems.add(preset + ": the arena box is smaller than the arena");
        }
        if (!problems.isEmpty())
            helper.fail(fail(problems));
        helper.succeed();
    }
}
