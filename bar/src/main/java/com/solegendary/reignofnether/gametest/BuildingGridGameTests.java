package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.building.Building;
import com.solegendary.reignofnether.building.BuildingGrid;
import com.solegendary.reignofnether.building.BuildingList;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.BuildingScansLegacy;
import com.solegendary.reignofnether.building.BuildingServerEvents;
import com.solegendary.reignofnether.building.BuildingUtils;
import com.solegendary.reignofnether.building.Buildings;
import com.solegendary.reignofnether.building.buildings.defence.Defences;
import com.solegendary.reignofnether.building.buildings.placements.FarmPlacement;
import com.solegendary.reignofnether.building.buildings.shared.WindGenerator;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.resources.Resources;
import com.solegendary.reignofnether.resources.ResourcesServerEvents;
import com.solegendary.reignofnether.unit.interfaces.AttackerUnit;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.util.MiscUtil;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;
import java.util.function.Predicate;

/**
 * BuildingGrid (building/BuildingGrid, Oct 21 perf plan PR 2) against the full scans it replaced
 * (building/BuildingScansLegacy, the old bodies unchanged): on random layouts - overlapping footprints, every
 * rotation, shared origins, four owners with an alliance, farms, bridges, walls, buildings filed as oversize - every
 * lookup must pick the very same building (the same tie-breaks), before and after the list is edited every way an
 * ArrayList can be. Then the real server list end to end, and a [Stress] benchmark: 300 idle attackers beside 1,000
 * enemy wind generators, server ms per tick through the full scans and through the grid.
 * Areas: the layouts 42000 blocks north of the test grid, the stress pad 45000 north and 12000 east (no other test
 * goes there).
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class BuildingGridGameTests {

    static final String ARENA = SkirmishGameTests.ARENA;

    /** The layouts' square (blocks) and the height of their pad. */
    static final int PAD = 96, PAD_Y = 240;

    static String owner(GameTestHelper helper, String tag) {
        return "gametest_bgrid_" + tag + "_" + helper.absolutePos(BlockPos.ZERO).asLong();
    }

    static List<long[]> chunksOf(int x0, int z0, int x1, int z1) {
        List<long[]> out = new ArrayList<>();
        for (int cx = x0 >> 4; cx <= x1 >> 4; cx++)
            for (int cz = z0 >> 4; cz <= z1 >> 4; cz++)
                out.add(new long[] { cx, cz });
        return out;
    }

    /** Grass on dirt over the square, with a scatter of water (getClosestGroundPos steers round it: +100 per spot). */
    static void pad(ServerLevel level, int x0, int z0, int x1, int z1, int y, Random rng, double water) {
        for (int x = x0; x <= x1; x++)
            for (int z = z0; z <= z1; z++) {
                level.setBlock(new BlockPos(x, y - 1, z), Blocks.DIRT.defaultBlockState(), 2);
                level.setBlock(new BlockPos(x, y, z), rng.nextDouble() < water
                    ? Blocks.WATER.defaultBlockState() : Blocks.GRASS_BLOCK.defaultBlockState(), 2);
            }
    }

    static String describe(BuildingPlacement b, List<BuildingPlacement> list) {
        if (b == null)
            return "none";
        int i = -1;
        for (int k = 0; k < list.size(); k++)
            if (list.get(k) == b) {
                i = k;
                break;
            }
        return b.getBuilding().name + "@" + b.originPos.toShortString() + "(" + b.ownerName + ") #" + i;
    }

    // ---------------------------------------------------------------------------------------------------------------
    // equivalence on random layouts
    // ---------------------------------------------------------------------------------------------------------------

    /** What the layouts are made of: 3x3 economy, a farm, labs, a tower, a bridge, a wall, a house, a vault. */
    static Building[] kinds() {
        return new Building[] { Buildings.WIND_GENERATOR_VILLAGERS, Buildings.ENERGY_CONVERTER_VILLAGERS,
            Buildings.WHEAT_FARM, Buildings.BARRACKS, Buildings.WATCHTOWER, Buildings.OAK_BRIDGE,
            Defences.VILLAGERS.wall(), Buildings.VILLAGER_HOUSE, Buildings.STORAGE_VAULT_VILLAGERS,
            Buildings.LIBRARY, Buildings.WIND_GENERATOR_VILLAGERS };
    }

    /** A random placement of a random kind in the square (on an 8-block lattice half the time, so distances tie). */
    static BuildingPlacement randomPlacement(ServerLevel level, Random rng, int ox, int oz, String[] owners) {
        Building[] kinds = kinds();
        for (int attempt = 0; attempt < 5; attempt++) {
            int x = rng.nextInt(PAD), z = rng.nextInt(PAD);
            if (rng.nextBoolean()) {
                x = x / 8 * 8;
                z = z / 8 * 8;
            }
            BuildingPlacement bp = BuildingUtils.getNewBuildingPlacement(kinds[rng.nextInt(kinds.length)], level,
                new BlockPos(ox + x, PAD_Y, oz + z), Rotation.values()[rng.nextInt(4)], owners[rng.nextInt(owners.length)], false);
            if (bp != null) {
                bp.isBuilt = rng.nextInt(3) != 0;
                return bp;
            }
        }
        return null;
    }

    /** Every lookup, grid against full scan, on {@code queries} random questions each; differences go to {@code problems}. */
    static int compareAll(ServerLevel level, Random rng, BuildingList list, BuildingGrid grid, int ox, int oz,
                          String[] owners, List<Mob> askers, int queries, String where, List<String> problems) {
        int checks = 0, before = problems.size();
        // ---- points: every building's corners, origin and centre, one block past them, and random spots ----
        List<BlockPos> points = new ArrayList<>();
        for (int k = 0; k < Math.min(list.size(), queries / 2); k++) {
            BuildingPlacement b = list.get(rng.nextInt(list.size()));
            points.add(b.minCorner);
            points.add(b.maxCorner);
            points.add(b.minCorner.offset(-1, 0, -1));
            points.add(b.maxCorner.offset(1, 1, 1));
            points.add(b.originPos);
            points.add(b.centrePos);
        }
        for (int k = 0; k < queries * 2; k++)
            points.add(new BlockPos(ox - 24 + rng.nextInt(PAD + 48), PAD_Y - 2 + rng.nextInt(28), oz - 24 + rng.nextInt(PAD + 48)));
        for (BlockPos p : points) {
            checks += 7;
            if (grid.anyInside(p, 0, false) != BuildingScansLegacy.isPosInsideAnyBuilding(list, p))
                problems.add(where + ": isPosInsideAnyBuilding " + p.toShortString());
            if (grid.anyInside(p, 0, true) != BuildingScansLegacy.isPosInsideFarm(list, p))
                problems.add(where + ": isPosInsideFarm " + p.toShortString());
            for (int edge : new int[] { 1, 6, 21 })
                if (grid.anyInside(p, edge, false) != BuildingScansLegacy.isPosNearAnyBuilding(list, p, edge))
                    problems.add(where + ": near any building (edge " + edge + ") " + p.toShortString());
            BuildingPlacement g = grid.findAt(p), l = BuildingScansLegacy.findBuilding(list, p);
            if (g != l)
                problems.add(where + ": findBuilding " + p.toShortString() + " grid " + describe(g, list) + ", scan " + describe(l, list));
            if (grid.hasOrigin(p) != BuildingScansLegacy.hasOrigin(list, p))
                problems.add(where + ": has origin " + p.toShortString());
        }
        // ---- membership ----
        for (BuildingPlacement b : list) {
            checks++;
            if (!grid.contains(b))
                problems.add(where + ": contains missed " + describe(b, list));
        }
        for (int k = 0; k < 5; k++) {
            BuildingPlacement stranger = randomPlacement(level, rng, ox, oz, owners);
            checks++;
            if (stranger != null && grid.contains(stranger) != list.contains(stranger))
                problems.add(where + ": contains said yes to a building not in the list");
        }
        // ---- closest building by centre (BuildingUtils.findClosestBuilding) ----
        String a = owners[0];
        List<Predicate<BuildingPlacement>> conditions = List.of(
            b -> true,
            b -> b.ownerName.equals(a),
            b -> b.isBuilt && b.getBuilding() instanceof WindGenerator,
            b -> b instanceof FarmPlacement,
            b -> !b.isBuilt,
            b -> false);
        for (int k = 0; k < queries; k++) {
            Vec3 pos;
            if (k % 5 == 0 && !list.isEmpty()) {   // exactly on a centre: distance 0, ties with anything sharing it
                BlockPos c = list.get(rng.nextInt(list.size())).centrePos;
                pos = new Vec3(c.getX(), c.getY(), c.getZ());
            } else {
                pos = new Vec3(ox - 160 + rng.nextDouble() * (PAD + 320), PAD_Y - 12 + rng.nextDouble() * 40,
                    oz - 160 + rng.nextDouble() * (PAD + 320));
                if (k % 3 == 0)
                    pos = new Vec3(Math.floor(pos.x), Math.floor(pos.y), Math.floor(pos.z));
            }
            for (int c = 0; c < conditions.size(); c++) {
                checks++;
                BuildingPlacement g = BuildingUtils.findClosestBuilding(grid, pos, conditions.get(c));
                BuildingPlacement l = BuildingScansLegacy.findClosestBuilding(list, pos, conditions.get(c));
                if (g != l)
                    problems.add(where + ": findClosestBuilding cond " + c + " at " + fmt(pos) + " grid " + describe(g, list)
                        + ", scan " + describe(l, list));
            }
        }
        // ---- the building an idle unit attacks by itself (MiscUtil.findClosestAttackableBuilding) ----
        float[] ranges = { 0.5f, 2f, 5f, 10f, 16f, 24.5f, 40f, 90f };
        for (int k = 0; k < queries; k++) {
            Mob m = askers.get(rng.nextInt(askers.size()));
            double x = ox - 30 + rng.nextDouble() * (PAD + 60), z = oz - 30 + rng.nextDouble() * (PAD + 60);
            double y = PAD_Y + 1 + (rng.nextInt(4) == 0 ? rng.nextInt(9) - 4 : 0);
            if (k % 4 == 0) {
                x = Math.floor(x) + 0.5;
                z = Math.floor(z) + 0.5;
            }
            m.moveTo(x, y, z, 0, 0);
            float range = ranges[rng.nextInt(ranges.length)];
            checks++;
            BuildingPlacement g = MiscUtil.findClosestAttackableBuilding(m, range, grid);
            BuildingPlacement l = BuildingScansLegacy.findClosestAttackableBuilding(m, range, list);
            if (g != l)
                problems.add(where + ": findClosestAttackableBuilding " + ((Unit) m).getOwnerName() + " " + m.getType().getDescriptionId()
                    + " at " + m.blockPosition().toShortString() + " range " + range + " grid " + describe(g, list) + ", scan " + describe(l, list));
        }
        // ---- the enemy building an attack-move without a target marches on ----
        for (int k = 0; k < queries; k++) {
            Mob m = askers.get(rng.nextInt(askers.size()));
            double spread = k % 4 == 0 ? 900 : 200;   // a quarter far off the layout: the ring search's list fallback
            m.moveTo(ox + PAD / 2.0 + (rng.nextDouble() - 0.5) * spread, PAD_Y + rng.nextInt(30) - 10,
                oz + PAD / 2.0 + (rng.nextDouble() - 0.5) * spread, 0, 0);
            checks++;
            BuildingPlacement g = MiscUtil.nearestEnemyBuildingForAttackMove((Unit) m, grid);
            BuildingPlacement l = BuildingScansLegacy.nearestEnemyBuildingForAttackMove((Unit) m, list);
            if (g != l)
                problems.add(where + ": attack-move target for " + ((Unit) m).getOwnerName() + " at " + fmt(m.position())
                    + " grid " + describe(g, list) + ", scan " + describe(l, list));
        }
        // keep the failure message readable: the first few differences of this round, and how many there were
        int diffs = problems.size() - before;
        if (diffs > 6) {
            List<String> first = new ArrayList<>(problems.subList(before, before + 6));
            problems.subList(before, problems.size()).clear();
            problems.addAll(first);
            problems.add(where + ": ... " + (diffs - 6) + " more differences");
        }
        return checks;
    }

    static String fmt(Vec3 v) {
        return String.format(Locale.ROOT, "(%.2f, %.2f, %.2f)", v.x, v.y, v.z);
    }

    /**
     * Five random layouts (12 to 320 buildings, one with most of them filed as oversize), every lookup asked a few
     * hundred times each way: the grid must return the very same building as the full scan every time - the same
     * tie-break included (layouts on an 8-block lattice and queries on centres make plenty of exact ties). Then the list
     * is edited every way an ArrayList can be (remove by index and by object, insert, set, removeIf, sort, shuffle,
     * swap, clear and refill), and every edit is followed by another comparison: the index follows the list without
     * any hook. Placements are never stamped or registered, so the layouts touch nothing but their own list.
     */
    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void building_grid_matches_the_full_scans_on_random_layouts(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos base = helper.absolutePos(new BlockPos(8, 1, 8));
        int ox = (base.getX() >> 4) << 4, oz = ((base.getZ() - 42000) >> 4) << 4;
        List<long[]> chunks = chunksOf(ox - 24, oz - 24, ox + PAD + 24, oz + PAD + 24);
        GameTestChunks.holdAndAwaitOrFail(level, chunks, "building grid layouts");
        String a = owner(helper, "a"), b = owner(helper, "b"), c = owner(helper, "c");
        String[] owners = { a, b, c, "" };   // c is a's ally; "" is neutral
        List<String> problems = new ArrayList<>();
        int savedMax = BuildingGrid.maxCellsPerBuilding;
        int checks = 0, buildings = 0;
        try {
            pad(level, ox - 24, oz - 24, ox + PAD + 24, oz + PAD + 24, PAD_Y, new Random(17), 0.04);
            AlliancesServerEvents.addAlliance(a, c);
            // who asks: melee and ranged (walls never draw them), a golem (walls do), for every owner; never added to
            // the level, so they never act
            List<Mob> askers = new ArrayList<>();
            List<EntityType<? extends Mob>> types = List.of(EntityRegistrar.VINDICATOR_UNIT.get(),
                EntityRegistrar.PILLAGER_UNIT.get(), EntityRegistrar.IRON_GOLEM_UNIT.get());
            for (String o : owners)
                for (EntityType<? extends Mob> t : types) {
                    Mob m = t.create(level);
                    if (m instanceof Unit u) {
                        u.setOwnerName(o);
                        askers.add(m);
                    }
                }
            if (askers.size() < 8) {
                helper.fail("could not create the asking units (" + askers.size() + ")");
                return;
            }
            int[] sizes = { 12, 60, 160, 320, 160 };
            for (int layout = 0; layout < sizes.length; layout++) {
                // the last layout files every building wider than two cells as oversize (checked by every query)
                BuildingGrid.maxCellsPerBuilding = layout == sizes.length - 1 ? 2 : savedMax;
                Random rng = new Random(1000 + layout);
                BuildingList list = new BuildingList();
                BuildingGrid grid = new BuildingGrid(list);
                for (int i = 0; i < sizes[layout]; i++) {
                    BuildingPlacement bp = randomPlacement(level, rng, ox, oz, owners);
                    if (bp == null)
                        continue;
                    list.add(bp);
                    if (rng.nextInt(12) == 0) {   // a second building on the same origin: first-in-list must win
                        BuildingPlacement twin = BuildingUtils.getNewBuildingPlacement(kinds()[rng.nextInt(kinds().length)], level,
                            bp.originPos, Rotation.values()[rng.nextInt(4)], owners[rng.nextInt(owners.length)], false);
                        if (twin != null)
                            list.add(twin);
                    }
                }
                buildings += list.size();
                String where = "layout " + layout + " (" + list.size() + " buildings)";
                checks += compareAll(level, rng, list, grid, ox, oz, owners, askers, 160, where, problems);

                // ---- every way the list can change; the grid must follow each one ----
                List<String> steps = List.of("remove(i)", "remove(o)", "add(i, b)", "set(i, b)", "removeIf", "sort",
                    "shuffle", "swap", "clear+addAll", "add");
                for (String step : steps) {
                    switch (step) {
                        case "remove(i)" -> {
                            for (int k = 0; k < 5 && !list.isEmpty(); k++)
                                list.remove(rng.nextInt(list.size()));
                        }
                        case "remove(o)" -> {
                            if (!list.isEmpty())
                                list.remove(list.get(rng.nextInt(list.size())));
                        }
                        case "add(i, b)" -> {
                            for (int k = 0; k < 5; k++) {
                                BuildingPlacement bp = randomPlacement(level, rng, ox, oz, owners);
                                if (bp != null)
                                    list.add(list.isEmpty() ? 0 : rng.nextInt(list.size()), bp);
                            }
                        }
                        case "set(i, b)" -> {
                            for (int k = 0; k < 3 && !list.isEmpty(); k++) {
                                BuildingPlacement bp = randomPlacement(level, rng, ox, oz, owners);
                                if (bp != null)
                                    list.set(rng.nextInt(list.size()), bp);
                            }
                        }
                        case "removeIf" -> list.removeIf(bp -> bp.ownerName.equals(c));
                        case "sort" -> list.sort((p, q) -> Integer.compare(p.originPos.getX(), q.originPos.getX()));
                        case "shuffle" -> Collections.shuffle(list, rng);
                        case "swap" -> {
                            if (list.size() > 1)
                                Collections.swap(list, 0, list.size() - 1);
                        }
                        case "clear+addAll" -> {
                            List<BuildingPlacement> keep = new ArrayList<>(list);
                            list.clear();
                            checks += compareAll(level, rng, list, grid, ox, oz, owners, askers, 10, where + " empty", problems);
                            list.addAll(keep);
                        }
                        default -> {
                            BuildingPlacement bp = randomPlacement(level, rng, ox, oz, owners);
                            if (bp != null)
                                list.add(bp);
                        }
                    }
                    checks += compareAll(level, rng, list, grid, ox, oz, owners, askers, 30, where + " after " + step, problems);
                }
            }
        } finally {
            BuildingGrid.maxCellsPerBuilding = savedMax;
            AlliancesServerEvents.removeAlliance(a, c);
            GameTestChunks.releaseAll(level, chunks);
        }
        ReignOfNether.LOGGER.info("[BuildingGrid] {} lookups compared over {} buildings in 5 layouts and their edits: {} differences",
            checks, buildings, problems.size());
        if (problems.isEmpty())
            helper.succeed();
        else
            helper.fail("the grid and the full scans disagree (" + checks + " lookups): " + String.join("; ", problems));
    }

    /**
     * The real server list, end to end: three buildings registered in BuildingServerEvents.getBuildings() answer
     * through the public lookups (BuildingUtils, MiscUtil, BuildingServerEvents.isRegistered) the same with the grid
     * and with the full scans (BuildingGrid.legacyScans), and a building removed from or swapped into the list is gone
     * from or found by the very next lookup. Registered, asked and removed inside one call (never a tick in between,
     * so the unstamped buildings are never ticked).
     */
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void building_grid_follows_the_server_building_list(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos base = helper.absolutePos(new BlockPos(8, 1, 8));
        // inside the layouts' square (the two tests run one after the other within a tick, never interleaved)
        int ox = (base.getX() >> 4) << 4, oz = ((base.getZ() - 42000) >> 4) << 4;
        List<long[]> chunks = chunksOf(ox - 16, oz - 16, ox + 64, oz + 64);
        GameTestChunks.holdAndAwaitOrFail(level, chunks, "building grid server list");
        String a = owner(helper, "list_a"), b = owner(helper, "list_b");
        List<String> problems = new ArrayList<>();
        List<BuildingPlacement> mine = new ArrayList<>();
        var list = BuildingServerEvents.getBuildings();
        try {
            pad(level, ox - 8, oz - 8, ox + 56, oz + 56, PAD_Y, new Random(5), 0);
            BuildingPlacement wind = BuildingUtils.getNewBuildingPlacement(Buildings.WIND_GENERATOR_VILLAGERS, level,
                new BlockPos(ox + 4, PAD_Y, oz + 4), Rotation.NONE, a, false);
            BuildingPlacement farm = BuildingUtils.getNewBuildingPlacement(Buildings.WHEAT_FARM, level,
                new BlockPos(ox + 24, PAD_Y, oz + 4), Rotation.NONE, a, false);
            BuildingPlacement barracks = BuildingUtils.getNewBuildingPlacement(Buildings.BARRACKS, level,
                new BlockPos(ox + 4, PAD_Y, oz + 30), Rotation.NONE, a, false);
            if (wind == null || farm == null || barracks == null) {
                helper.fail("could not create the three placements");
                return;
            }
            for (BuildingPlacement bp : List.of(wind, farm, barracks)) {
                bp.isBuilt = true;
                list.add(bp);
                mine.add(bp);
            }
            Mob foe = EntityRegistrar.VINDICATOR_UNIT.get().create(level);
            if (!(foe instanceof Unit foeUnit)) {
                helper.fail("could not create the enemy vindicator");
                return;
            }
            foeUnit.setOwnerName(b);
            foe.moveTo(wind.maxCorner.getX() + 4.5, PAD_Y + 1, wind.minCorner.getZ() + 0.5, 0, 0);

            // the same answers through the grid and through the full scans
            BlockPos inWind = wind.centrePos, inFarm = farm.centrePos, nowhere = new BlockPos(ox + 50, PAD_Y + 1, oz + 50);
            Vec3 nearBarracks = Vec3.atCenterOf(barracks.centrePos);
            Predicate<BuildingPlacement> mineOnly = bp -> bp.ownerName.equals(a);
            Object[] viaGrid = ask(foe, inWind, inFarm, nowhere, nearBarracks, mineOnly, wind);
            BuildingGrid.legacyScans = true;
            Object[] viaScan;
            try {
                viaScan = ask(foe, inWind, inFarm, nowhere, nearBarracks, mineOnly, wind);
            } finally {
                BuildingGrid.legacyScans = false;
            }
            String[] names = { "inside (wind)", "inside farm (farm)", "inside (nowhere)", "findBuilding (wind)",
                "findClosestBuilding (barracks)", "registered (wind)", "attack-move for the foe",
                "isBuildingBuildable (wind)" };
            for (int i = 0; i < names.length; i++)
                if (!java.util.Objects.equals(viaGrid[i], viaScan[i]))
                    problems.add(names[i] + ": grid " + viaGrid[i] + ", full scan " + viaScan[i]);
            Object[] expected = { true, true, false, wind, barracks, true, wind };   // [7]: grid = full scan only
            for (int i = 0; i < expected.length; i++)
                if (!java.util.Objects.equals(viaGrid[i], expected[i]))
                    problems.add(names[i] + ": " + viaGrid[i] + ", expected " + expected[i]);
            // the attackable lookup through the grid only: its full scan reads blocks round every hostile building in
            // the list, other tests' far-off ones too (the random layouts compare it on a list of their own)
            BuildingPlacement attackable = MiscUtil.findClosestAttackableBuilding(foe, 12);
            if (attackable != wind)
                problems.add("attackable for the foe: " + attackable + ", expected the wind generator 3 blocks away");

            // removed: gone at once
            list.remove(wind);
            if (BuildingUtils.isPosInsideAnyBuilding(false, inWind))
                problems.add("a removed building still holds its centre");
            if (BuildingServerEvents.isRegistered(wind))
                problems.add("a removed building is still registered");
            if (BuildingUtils.findBuilding(false, wind.originPos) != null)
                problems.add("findBuilding still finds a removed building");
            // swapped in by set(): found at once
            int at = list.indexOf(farm);
            list.set(at, wind);
            if (!BuildingUtils.isPosInsideAnyBuilding(false, inWind) || BuildingUtils.isPosInsideFarm(false, inFarm))
                problems.add("set() did not reach the grid (wind back, farm out)");
            list.set(at, farm);
        } finally {
            list.removeAll(new HashSet<>(mine));
            GameTestChunks.releaseAll(level, chunks);
        }
        if (problems.isEmpty())
            helper.succeed();
        else
            helper.fail(String.join("; ", problems));
    }

    /** The public lookups the server-list test compares, in its names[] order. */
    static Object[] ask(Mob foe, BlockPos inWind, BlockPos inFarm, BlockPos nowhere, Vec3 nearBarracks,
                        Predicate<BuildingPlacement> mineOnly, BuildingPlacement wind) {
        return new Object[] {
            BuildingUtils.isPosInsideAnyBuilding(false, inWind),
            BuildingUtils.isPosInsideFarm(false, inFarm),
            BuildingUtils.isPosInsideAnyBuilding(false, nowhere),
            BuildingUtils.findBuilding(false, inWind),
            BuildingUtils.findClosestBuilding(false, nearBarracks, mineOnly),
            BuildingServerEvents.isRegistered(wind),
            MiscUtil.nearestEnemyBuildingForAttackMove((Unit) foe),
            BuildingUtils.isBuildingBuildable(false, wind),
        };
    }

    // ---------------------------------------------------------------------------------------------------------------
    // [Stress] benchmark
    // ---------------------------------------------------------------------------------------------------------------

    static final int STRESS_UNITS = 300, STRESS_BUILDINGS = 1000;
    /**
     * Wind generators (3x3, the most-built building of the Oct 21 match) on a 4-block lattice round the army: a block
     * apart, so they never merge (MergedBuildings joins edge to edge only), out to 74 blocks, none within 40 of the
     * centre (the army stands within 19 x 14 of it: 20+ blocks from the nearest attack spot, past every aggro range).
     */
    static final int STRESS_PITCH = 4, STRESS_REACH = 74, STRESS_HOLE = 40;
    static final int STRESS_TIMEOUT = 3600;
    static final int STRESS_Y = 240;
    /** Ticks to settle (spawn nudges, light), then alternating full-scan / grid phases of STRESS_PHASE ticks. */
    static final int STRESS_WARMUP = 60, STRESS_PHASE = 30, STRESS_PHASES = 6;

    /**
     * Lovish's late game in miniature: 300 idle vindicators (an army waiting in base) and 1,000 enemy
     * wind generators all round them, 20+ blocks away - out of every unit's aggro range, so every unit looks for an
     * enemy building on every idle scan (AttackerUnit idle auto-aggression) and finds none, which is the expensive case:
     * the full scan walks all 1,000 buildings (relationship + footprint walk with block reads each), the grid reads the
     * cells within reach. The server really ticks it all. After a warm-up the test alternates three full-scan phases
     * and three grid phases (BuildingGrid.legacyScans) of 30 ticks and averages vanilla's own tick times
     * (MinecraftServer.tickTimes) over each, counting only ticks that ran wholly in one mode; then times one direct
     * pass of the 300 lookups each way. Logged as "[Stress] building scans" (a CI notice). It fails only when the
     * scenario is not the one described (units missing, not scanning, or finding a building) or when the two ways
     * pick different buildings - never on the timings, which a shared CI runner moves; the flake-stress workflow
     * repeats it for a spread.
     */
    @GameTest(template = ARENA, timeoutTicks = STRESS_TIMEOUT, batch = "stress_building_scans")
    public static void stress_building_scans_idle_army_beside_1000_buildings(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        BlockPos base = helper.absolutePos(new BlockPos(8, 1, 8));
        int cx = (((base.getX() + 12000) >> 4) << 4) + 8, cz = (((base.getZ() - 45000) >> 4) << 4) + 8;
        int edge = STRESS_REACH + 8;
        List<long[]> chunks = chunksOf(cx - edge, cz - edge, cx + edge, cz + edge);
        GameTestChunks.holdAndAwaitOrFail(level, chunks, "building scan stress");
        List<long[]> armyChunks = chunksOf(cx - 24, cz - 24, cx + 24, cz + 24);
        String a = owner(helper, "stress_army"), b = owner(helper, "stress_base");
        List<Entity> made = new ArrayList<>();
        List<BuildingPlacement> placed = new ArrayList<>();
        List<Resources> pools = new ArrayList<>();
        // armed: the scene stands and the phases run; done: the verdict is in (or the test failed) and all is cleaned up
        boolean[] armed = { false }, done = { false };
        Runnable cleanUp = () -> {
            BuildingGrid.legacyScans = false;
            for (Entity e : made)
                if (!e.isRemoved())
                    e.discard();
            Set<BuildingPlacement> ours = Collections.newSetFromMap(new java.util.IdentityHashMap<>());
            ours.addAll(placed);
            BuildingServerEvents.getBuildings().removeIf(ours::contains);
            ResourcesServerEvents.resourcesList.removeAll(pools);
            GameTestChunks.releaseAll(level, chunks);
        };
        // registered here, from the test body: a callback that registers per-tick callbacks of its own would add to
        // the map GameTestInfo is iterating
        measure(helper, server, made, placed, armed, done, cleanUp);
        GameTestChunks.whenEntitiesReady(helper, level, armyChunks, 5, 200, 1200, "building scan stress army", () -> {
            try {
                for (String o : List.of(a, b)) {
                    Resources pool = new Resources(o, 0, 0, 0);
                    ResourcesServerEvents.resourcesList.add(pool);
                    pools.add(pool);
                }
                pad(level, cx - edge, cz - edge, cx + edge, cz + edge, STRESS_Y, new Random(3), 0);
                // the enemy base: the lattice round the hole, nearest rings first, 1,000 of them, blocks stamped
                List<int[]> sites = new ArrayList<>();
                for (int dx = -STRESS_REACH; dx <= STRESS_REACH; dx += STRESS_PITCH)
                    for (int dz = -STRESS_REACH; dz <= STRESS_REACH; dz += STRESS_PITCH)
                        if (Math.abs(dx) >= STRESS_HOLE || Math.abs(dz) >= STRESS_HOLE)
                            sites.add(new int[] { dx, dz });
                sites.sort((p, q) -> Integer.compare(Math.max(Math.abs(p[0]), Math.abs(p[1])), Math.max(Math.abs(q[0]), Math.abs(q[1]))));
                for (int i = 0; i < sites.size() && placed.size() < STRESS_BUILDINGS; i++) {
                    BuildingPlacement bp = BuildingUtils.getNewBuildingPlacement(Buildings.WIND_GENERATOR_VILLAGERS, level,
                        new BlockPos(cx + sites.get(i)[0], STRESS_Y, cz + sites.get(i)[1]), Rotation.NONE, b, false);
                    if (bp == null)
                        continue;
                    for (var block : bp.getBlocks())
                        if (!block.getBlockState().isAir())
                            level.setBlock(block.getBlockPos(), block.getBlockState(), 2);
                    bp.initPlacedBlocks();
                    bp.isBuilt = true;
                    BuildingServerEvents.getBuildings().add(bp);
                    placed.add(bp);
                }
                // the army: 20 x 15 vindicators on a 2-block lattice. Every idle attacker that can hit buildings runs
                // the same lookup; a pillager only can when mounted (canAttackBuildings) and skeletons burn by day
                for (int i = 0; i < STRESS_UNITS; i++) {
                    Mob m = EntityRegistrar.VINDICATOR_UNIT.get().create(level);
                    if (m == null)
                        continue;
                    m.moveTo(cx - 19 + (i % 20) * 2 + 0.5, STRESS_Y + 1, cz - 14 + (i / 20) * 2 + 0.5, 0, 0);
                    ((Unit) m).setOwnerName(a);
                    level.addFreshEntity(m);
                    made.add(m);
                }
                if (placed.size() < STRESS_BUILDINGS * 95 / 100 || made.size() < STRESS_UNITS * 95 / 100) {
                    done[0] = true;
                    cleanUp.run();
                    helper.fail("stress scenario incomplete: " + placed.size() + " buildings, " + made.size() + " units");
                    return;
                }
                armed[0] = true;
            } catch (RuntimeException e) {
                if (!done[0]) {
                    done[0] = true;
                    cleanUp.run();
                }
                throw e;
            }
        });
    }

    /** The phases and the verdict of the stress benchmark (see its javadoc); idle until {@code armed}. */
    static void measure(GameTestHelper helper, MinecraftServer server, List<Entity> made, List<BuildingPlacement> placed,
                        boolean[] armed, boolean[] done, Runnable cleanUp) {
        final int callbacks = STRESS_WARMUP + STRESS_PHASES * STRESS_PHASE + 1;
        final int[] k = { 0 }, rebuildsBefore = { 0 };
        final long[] phaseNanos = new long[STRESS_PHASES];
        final int[] phaseTicks = new int[STRESS_PHASES];
        final long[] scansAt = new long[STRESS_PHASES + 1];
        helper.onEachTick(() -> {
            if (done[0])
                return;
            try {
                // never leave 1,000 registered buildings and 300 scanning units to the batches after this one
                if (helper.getTick() >= STRESS_TIMEOUT - 40) {
                    done[0] = true;
                    cleanUp.run();
                    throw new net.minecraft.gametest.framework.GameTestAssertException("the stress scene "
                        + (armed[0] ? "ran out of time after " + k[0] + " of " + callbacks + " measured ticks" : "was never set up"));
                }
                if (!armed[0])
                    return;
                int t = k[0]++;
                if (t == 0)
                    rebuildsBefore[0] = BuildingServerEvents.getBuildingGrid().rebuilds();
                // the tick before this callback is complete: count it if the mode was the same at both callbacks round it
                if (t >= 2 && phaseOf(t - 1) >= 0 && phaseOf(t - 1) == phaseOf(t - 2)) {
                    int p = phaseOf(t - 1);
                    phaseNanos[p] += server.tickTimes[(server.getTickCount() - 1) % 100];
                    phaseTicks[p]++;
                }
                int p = phaseOf(t);
                if (p >= 0 && (t - STRESS_WARMUP) % STRESS_PHASE == 0)
                    scansAt[p] = MiscUtil.attackableBuildingScans;
                if (t == callbacks - 1) {
                    long[] phaseScans = new long[STRESS_PHASES];
                    for (int q = 0; q < STRESS_PHASES; q++)
                        phaseScans[q] = scansAt[q + 1] - scansAt[q];
                    done[0] = true;
                    BuildingGrid.legacyScans = false;
                    try {
                        verdict(helper, made, placed, phaseNanos, phaseTicks, phaseScans,
                            BuildingServerEvents.getBuildingGrid().rebuilds() - rebuildsBefore[0]);
                    } finally {
                        cleanUp.run();
                    }
                    return;
                }
                // even phases through the full scans, odd ones through the grid; the warm-up through the grid
                BuildingGrid.legacyScans = p >= 0 && p % 2 == 0;
            } catch (RuntimeException e) {
                if (!done[0]) {
                    done[0] = true;
                    cleanUp.run();
                }
                throw e;
            }
        });
    }

    /** The phase callback t falls in (-1 in the warm-up, STRESS_PHASES past the end). */
    static int phaseOf(int t) {
        if (t < STRESS_WARMUP)
            return -1;
        return Math.min(STRESS_PHASES, (t - STRESS_WARMUP) / STRESS_PHASE);
    }

    static void verdict(GameTestHelper helper, List<Entity> made, List<BuildingPlacement> placed,
                        long[] phaseNanos, int[] phaseTicks, long[] phaseScans, int rebuilds) {
        List<String> problems = new ArrayList<>();
        double[] ms = new double[STRESS_PHASES];
        for (int p = 0; p < STRESS_PHASES; p++) {
            ms[p] = phaseTicks[p] == 0 ? Double.NaN : phaseNanos[p] / 1e6 / phaseTicks[p];
            if (phaseTicks[p] < STRESS_PHASE / 2)
                problems.add("phase " + p + " counted only " + phaseTicks[p] + " ticks");
        }
        List<Mob> units = new ArrayList<>();
        for (Entity e : made)
            if (e instanceof Mob m && m.isAlive())
                units.add(m);
        // the scenario: every unit idle and scanning (one lookup per unit per idle scan: AttackerUnit.IDLE_AGGRO_TICKS,
        // perf plan PR 5), none attacking a building
        int scanners = 0;
        for (Mob m : units)
            if (m instanceof AttackerUnit au && au.canAttackBuildings() && au.getAggressiveWhenIdle())
                scanners++;
        if (scanners < units.size() * 95 / 100)
            problems.add("only " + scanners + " of " + units.size() + " units look for buildings when idle");
        double expected = units.size() / (double) com.solegendary.reignofnether.unit.TargetScan.idleScanEvery();
        for (int p = 0; p < STRESS_PHASES; p++) {
            double perTick = phaseScans[p] / (double) STRESS_PHASE;
            if (perTick < expected * 0.6)
                problems.add(String.format(Locale.ROOT, "phase %d: %.1f building lookups per tick, expected ~%.0f (units not idle?)", p, perTick, expected));
        }
        int attacking = 0;
        for (Mob m : units)
            if (m instanceof AttackerUnit au && (au.getAttackMoveTarget() != null || m.getTarget() != null
                    || (au.getAttackBuildingGoal() instanceof com.solegendary.reignofnether.unit.goals.MeleeAttackBuildingGoal g && g.getBuildingTarget() != null)
                    || (au.getAttackBuildingGoal() instanceof com.solegendary.reignofnether.unit.goals.RangedAttackBuildingGoal<?> r && r.getBuildingTarget() != null)))
                attacking++;
        if (attacking > 0)
            problems.add(attacking + " units picked a target: the base must stay out of reach");
        // one direct pass of the 300 lookups each way (no tick around them), and the same answers at a reach that
        // does find buildings
        long[] passNanos = new long[2];
        int different = 0;
        for (int mode = 0; mode < 2; mode++) {
            BuildingGrid.legacyScans = mode == 0;
            try {
                for (int rep = 0; rep < 3; rep++) {
                    long t0 = System.nanoTime();
                    for (Mob m : units)
                        MiscUtil.findClosestAttackableBuilding(m, ((AttackerUnit) m).getAggroRange());
                    if (rep > 0)
                        passNanos[mode] += System.nanoTime() - t0;
                }
            } finally {
                BuildingGrid.legacyScans = false;
            }
        }
        for (int i = 0; i < units.size(); i += 10) {
            Mob m = units.get(i);
            BuildingPlacement g = MiscUtil.findClosestAttackableBuilding(m, 64, BuildingServerEvents.getBuildingGrid());
            BuildingPlacement l = BuildingScansLegacy.findClosestAttackableBuilding(m, 64, BuildingServerEvents.getBuildings());
            if (g != l)
                different++;
            if (g == null)
                problems.add("unit " + i + " found no building within 64 blocks (the base starts 20-40 away)");
        }
        if (different > 0)
            problems.add(different + " units picked a different building through the grid than through the full scan");
        double full = (ms[0] + ms[2] + ms[4]) / 3, grid = (ms[1] + ms[3] + ms[5]) / 3;
        ReignOfNether.LOGGER.info(String.format(Locale.ROOT,
            "[Stress] building scans: %d idle units, %d enemy buildings | full scan %.2f ms/tick (%.2f/%.2f/%.2f) | grid %.2f ms/tick (%.2f/%.2f/%.2f) | %+.1f%% | lookups/tick %.1f | direct pass of %d lookups: full %.2f ms, grid %.3f ms | grid rebuilds %d",
            units.size(), placed.size(), full, ms[0], ms[2], ms[4], grid, ms[1], ms[3], ms[5],
            full > 0 ? (grid - full) / full * 100 : 0, phaseScans[1] / (double) STRESS_PHASE,
            units.size(), passNanos[0] / 2e6, passNanos[1] / 2e6, rebuilds));
        if (problems.isEmpty())
            helper.succeed();
        else
            helper.fail(String.join("; ", problems));
    }
}
