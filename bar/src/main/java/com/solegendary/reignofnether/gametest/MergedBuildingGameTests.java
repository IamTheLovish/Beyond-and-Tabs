package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.building.Building;
import com.solegendary.reignofnether.building.BuildingMerge;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.BuildingReclaim;
import com.solegendary.reignofnether.building.BuildingUtils;
import com.solegendary.reignofnether.building.Buildings;
import com.solegendary.reignofnether.building.ComplexPlacement;
import com.solegendary.reignofnether.building.MergeShapes;
import com.solegendary.reignofnether.building.MergedBuildingClientboundPacket;
import com.solegendary.reignofnether.resources.Wind;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Merged buildings, slice 1 (project doc claude/merged-buildings.md; building/MergeShapes, BuildingMerge,
 * ComplexPlacement): White Angel wind generators laid edge to edge grow into Pairs, Farms, Rows and Quarters. Own
 * holder (the CI guard keeps new tests out of SkirmishGameTests). Every placement is made with
 * getNewBuildingPlacement, stamped on its own sky pad and registered in a PRIVATE list through BuildingMerge.Registry
 * - never in the world's building list, so no other test can see it and no packet goes out.
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class MergedBuildingGameTests {

    static final String ARENA = SkirmishGameTests.ARENA;
    static final Building MILL = Buildings.WIND_GENERATOR_VILLAGERS;
    /** A White Angel mill's footprint is 3x3 (structures/wind_generator.nbt): the grid tool lays them 3 apart. */
    static final int PITCH = 3;

    // ------------------------------------------------------------------ helpers

    /** A private registry: a list of its own, no packets. */
    static BuildingMerge.Registry registry() {
        return new BuildingMerge.Registry(new ArrayList<>(), false);
    }

    /**
     * Stamps a finished building on a stone pad (flag 2: nothing pops off) and registers it in the private list -
     * exactly what a lone built building is, minus the world's list.
     */
    static BuildingPlacement stamp(ServerLevel level, Building b, BlockPos origin, String owner, BuildingMerge.Registry reg) {
        var bp = BuildingUtils.getNewBuildingPlacement(b, level, origin, Rotation.NONE, owner, false);
        if (bp == null)
            return null;
        for (int x = bp.minCorner.getX() - 1; x <= bp.maxCorner.getX() + 1; x++)
            for (int z = bp.minCorner.getZ() - 1; z <= bp.maxCorner.getZ() + 1; z++)
                level.setBlock(new BlockPos(x, bp.minCorner.getY() - 1, z), Blocks.STONE.defaultBlockState(), 2);
        for (var blk : bp.getBlocks())
            if (!blk.getBlockState().isAir())
                level.setBlock(blk.getBlockPos(), blk.getBlockState(), 2);
        bp.initPlacedBlocks();
        bp.isBuilt = true;
        reg.list.add(bp);
        return bp;
    }

    /** A w x d grid of finished mills, origins PITCH apart, the least corner at (x0, y, z0). Row-major. */
    static List<BuildingPlacement> stampGrid(ServerLevel level, int x0, int y, int z0, int w, int d, String owner, BuildingMerge.Registry reg) {
        List<BuildingPlacement> out = new ArrayList<>();
        for (int j = 0; j < d; j++)
            for (int i = 0; i < w; i++)
                out.add(stamp(level, MILL, new BlockPos(x0 + i * PITCH, y, z0 + j * PITCH), owner, reg));
        return out;
    }

    /** Clears a test's sky pad: everything from the pad up through a mill's height, over the grid's area. */
    static void clear(ServerLevel level, int x0, int y, int z0, int w, int d) {
        for (int x = x0 - 2; x <= x0 + w * PITCH + 1; x++)
            for (int z = z0 - 2; z <= z0 + d * PITCH + 1; z++)
                for (int yy = y - 1; yy <= y + 14; yy++)
                    level.setBlock(new BlockPos(x, yy, z), Blocks.AIR.defaultBlockState(), 2);
    }

    static String shapes(List<ComplexPlacement> complexes) {
        List<String> names = new ArrayList<>();
        for (ComplexPlacement c : complexes)
            names.add(c.getShape() + "(" + c.memberCount() + ")");
        return String.join(", ", names);
    }

    static ComplexPlacement onlyComplex(BuildingMerge.Registry reg, List<String> problems, String when) {
        List<ComplexPlacement> cs = BuildingMerge.complexes(reg.list);
        if (cs.size() != 1) {
            problems.add(when + ": expected one complex, got " + cs.size() + " [" + shapes(cs) + "] among " + reg.list.size() + " placements");
            return null;
        }
        return cs.get(0);
    }

    static Set<MergeShapes.Cell> cells(int[][] colRows) {
        Set<MergeShapes.Cell> s = new HashSet<>();
        for (int[] cr : colRows)
            s.add(new MergeShapes.Cell(cr[0], cr[1]));
        return s;
    }

    static String describe(List<MergeShapes.Group> groups) {
        List<String> names = new ArrayList<>();
        for (MergeShapes.Group g : groups)
            names.add(g.shape() + (g.rotated() ? "r" : "") + g.cells());
        return String.join(", ", names);
    }

    /** The lattice matcher on a plain grid: every lattice neighbour touches. */
    static List<MergeShapes.Group> matchAll(Set<MergeShapes.Cell> cells) {
        return MergeShapes.match(cells, MergeShapes.MAX_MEMBERS, (a, b) -> true);
    }

    static boolean groupsAre(List<MergeShapes.Group> groups, MergeShapes.Shape... expected) {
        if (groups.size() != expected.length)
            return false;
        List<MergeShapes.Shape> got = new ArrayList<>();
        for (MergeShapes.Group g : groups)
            got.add(g.shape());
        List<MergeShapes.Shape> want = new ArrayList<>(List.of(expected));
        got.sort(null);
        want.sort(null);
        return got.equals(want);
    }

    // ------------------------------------------------------------------ the shape ladder (pure)

    /**
     * The ladder, biggest first: 2 = Pair, 3 in a row = Pair + single, 4 = Farm, 6 (either way round) = Row,
     * 9 = Quarter, an L of 5 = two Pairs + single, 4x3 of 12 = Quarter + Pair + single, and a 4x4 under the slice-1
     * cap of 9 never makes anything bigger than a Quarter. Every cell is grouped at most once.
     */
    @GameTest(template = ARENA, timeoutTicks = 20)
    public static void shapes_match_the_ladder_biggest_first(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        check(problems, "2 in a row", MergeShapes.rectangle(2, 1), MergeShapes.Shape.PAIR);
        check(problems, "3 in a row", MergeShapes.rectangle(3, 1), MergeShapes.Shape.PAIR);
        check(problems, "2x2", MergeShapes.rectangle(2, 2), MergeShapes.Shape.FARM);
        check(problems, "3x2", MergeShapes.rectangle(3, 2), MergeShapes.Shape.ROW);
        check(problems, "2x3", MergeShapes.rectangle(2, 3), MergeShapes.Shape.ROW);
        check(problems, "3x3", MergeShapes.rectangle(3, 3), MergeShapes.Shape.QUARTER);
        check(problems, "L of 5", cells(new int[][] { {0, 0}, {1, 0}, {2, 0}, {0, 1}, {0, 2} }),
            MergeShapes.Shape.PAIR, MergeShapes.Shape.PAIR);
        check(problems, "4x3", MergeShapes.rectangle(4, 3), MergeShapes.Shape.QUARTER, MergeShapes.Shape.PAIR);
        check(problems, "3x3 minus a corner", cells(new int[][] { {0, 0}, {1, 0}, {2, 0}, {0, 1}, {1, 1}, {2, 1}, {0, 2}, {1, 2} }),
            MergeShapes.Shape.ROW, MergeShapes.Shape.PAIR);
        check(problems, "3x3 minus the centre", cells(new int[][] { {0, 0}, {1, 0}, {2, 0}, {0, 1}, {2, 1}, {0, 2}, {1, 2}, {2, 2} }),
            MergeShapes.Shape.PAIR, MergeShapes.Shape.PAIR, MergeShapes.Shape.PAIR, MergeShapes.Shape.PAIR);
        if (!matchAll(MergeShapes.rectangle(1, 1)).isEmpty())
            problems.add("a single cell should match nothing");
        // the 2x3 Row stands rotated (depth along x)
        List<MergeShapes.Group> tall = matchAll(MergeShapes.rectangle(2, 3));
        if (tall.size() != 1 || !tall.get(0).rotated())
            problems.add("a 2x3 should be one rotated Row, got " + describe(tall));
        // the cap: a 4x4 of 16 makes a Quarter and leftovers, never a Citadel, and the ladder above 9 only waits
        List<MergeShapes.Group> big = matchAll(MergeShapes.rectangle(4, 4));
        int covered = 0;
        for (MergeShapes.Group g : big) {
            covered += g.members();
            if (g.members() > MergeShapes.MAX_MEMBERS)
                problems.add("a 4x4 made a " + g.shape() + " of " + g.members() + " over the cap of " + MergeShapes.MAX_MEMBERS);
        }
        if (big.isEmpty() || big.get(0).shape() != MergeShapes.Shape.QUARTER || covered > 16)
            problems.add("a 4x4 under the cap should start with a Quarter, got " + describe(big));
        List<MergeShapes.Group> uncapped = MergeShapes.match(MergeShapes.rectangle(4, 4), 25, (a, b) -> true);
        if (uncapped.size() != 1 || uncapped.get(0).shape() != MergeShapes.Shape.CITADEL)
            problems.add("with the cap lifted a 4x4 should be one Citadel, got " + describe(uncapped));
        if (MergeShapes.Shape.ofMembers(6) != MergeShapes.Shape.ROW || MergeShapes.Shape.ofMembers(5) != null)
            problems.add("Shape.ofMembers misreads the ladder");
        // the bonuses, as the spec sets them
        float[] want = { 0.04f, 0.08f, 0.10f, 0.12f, 0.14f, 0.15f };
        MergeShapes.Shape[] ladder = MergeShapes.Shape.values();
        for (int i = 0; i < ladder.length; i++)
            if (Math.abs(ladder[i].bonus - want[i]) > 1e-6)
                problems.add(ladder[i] + " bonus is " + ladder[i].bonus + ", the spec says " + want[i]);
        if (problems.isEmpty())
            helper.succeed();
        else
            helper.fail(String.join("; ", problems));
    }

    static void check(List<String> problems, String what, Set<MergeShapes.Cell> cells, MergeShapes.Shape... expected) {
        List<MergeShapes.Group> groups = matchAll(cells);
        if (!groupsAre(groups, expected))
            problems.add(what + " should give " + List.of(expected) + ", got " + describe(groups));
        Set<MergeShapes.Cell> seen = new HashSet<>();
        for (MergeShapes.Group g : groups)
            for (MergeShapes.Cell c : g.cells())
                if (!cells.contains(c) || !seen.add(c))
                    problems.add(what + ": cell " + c + " is outside the set or grouped twice");
    }

    // ------------------------------------------------------------------ forming

    /**
     * Four finished White Angel mills in a 2x2 grow into one Farm: one placement of four sections, its energy the
     * four mills' summed times 1.08, its health and refund four mills' worth, its name "Farm x4"; it still counts as
     * four generators to anything that counts them.
     */
    @GameTest(template = ARENA, timeoutTicks = 20)
    public static void a_two_by_two_of_wind_generators_forms_a_farm_with_summed_output_plus_eight_percent(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos base = helper.absolutePos(new BlockPos(5, 1, 5));
        final int y = 266, x0 = base.getX(), z0 = base.getZ();
        String owner = "gametest_merge_farm_" + base.asLong();
        List<String> problems = new ArrayList<>();
        try {
            var reg = registry();
            List<BuildingPlacement> mills = stampGrid(level, x0, y, z0, 2, 2, owner, reg);
            if (mills.contains(null)) {
                helper.fail("could not stamp the mills");
                return;
            }
            float lone = mills.get(0).getEnergyIncome();
            int loneHp = mills.get(0).getMaxHealth();
            int loneMetal = BuildingReclaim.metalInvested(mills.get(0));
            BuildingMerge.reformAll(reg);
            ComplexPlacement farm = onlyComplex(reg, problems, "after the re-match");
            if (farm != null) {
                if (farm.getShape() != MergeShapes.Shape.FARM || farm.memberCount() != 4)
                    problems.add("expected a Farm of 4, got " + farm.getShape() + " of " + farm.memberCount());
                if (reg.list.size() != 1)
                    problems.add("the four mills should be gone from the list, " + reg.list.size() + " placements remain");
                float want = lone * 4 * 1.08f;
                if (Math.abs(farm.getEnergyIncome() - want) > 1e-3f)
                    problems.add("a Farm should earn " + want + " (4 x " + lone + " x 1.08), got " + farm.getEnergyIncome());
                if (farm.getMaxHealth() != loneHp * 4)
                    problems.add("a Farm's max health should be 4 x " + loneHp + ", got " + farm.getMaxHealth());
                if (Math.abs(farm.getHealth() - farm.getMaxHealth()) > 1)
                    problems.add("a fresh Farm should stand at full health, got " + farm.getHealth() + " / " + farm.getMaxHealth());
                if (BuildingReclaim.metalInvested(farm) != loneMetal * 4)
                    problems.add("a Farm's refund base should be 4 mills', got " + BuildingReclaim.metalInvested(farm) + " vs " + loneMetal);
                if (!farm.isBuilt || farm.getBuilding() != MILL || !owner.equals(farm.ownerName))
                    problems.add("the Farm should be a built White Angel mill of its owner");
                if (!farm.originPos.equals(mills.get(0).originPos))
                    problems.add("the Farm's origin should be its least corner's (" + mills.get(0).originPos + "), got " + farm.originPos);
                if (farm.minCorner.getX() != mills.get(0).minCorner.getX() || farm.maxCorner.getX() != mills.get(3).maxCorner.getX()
                        || farm.maxCorner.getZ() != mills.get(3).maxCorner.getZ())
                    problems.add("the Farm's box should span all four mills: " + farm.minCorner + " .. " + farm.maxCorner);
                if (farm.sectionAt(mills.get(2).centrePos) == null || farm.sectionAt(mills.get(2).centrePos).originPos.equals(mills.get(0).originPos))
                    problems.add("sectionAt should find the mill under a position");
                for (ComplexPlacement.Section s : farm.getSections())
                    if (farm.sectionBlocksPlaced(s) != s.totalBlocks() || farm.isSectionDead(s))
                        problems.add("a fresh section should be whole: " + farm.sectionBlocksPlaced(s) + " / " + s.totalBlocks());
            }
            if (problems.isEmpty())
                helper.succeed();
            else
                helper.fail(String.join("; ", problems));
        } finally {
            clear(level, x0, y, z0, 2, 2);
        }
    }

    /** Only mills standing edge to edge and in line merge: a one-block gap or a sideways offset keeps them apart. */
    @GameTest(template = ARENA, timeoutTicks = 20)
    public static void only_touching_aligned_neighbours_merge(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos base = helper.absolutePos(new BlockPos(3, 1, 3));
        final int y = 282, x0 = base.getX(), z0 = base.getZ();
        String owner = "gametest_merge_touch_" + base.asLong();
        List<String> problems = new ArrayList<>();
        try {
            var reg = registry();
            BuildingPlacement a = stamp(level, MILL, new BlockPos(x0, y, z0), owner, reg);
            BuildingPlacement gap = stamp(level, MILL, new BlockPos(x0 + PITCH + 1, y, z0), owner, reg);         // one block apart
            BuildingPlacement askew = stamp(level, MILL, new BlockPos(x0, y, z0 + PITCH + 1), owner, reg);       // the bot's pitch
            if (a == null || gap == null || askew == null) {
                helper.fail("could not stamp the mills");
                return;
            }
            if (BuildingMerge.touching(a, gap) || BuildingMerge.touching(a, askew))
                problems.add("a one-block gap must not count as touching");
            BuildingMerge.reformAll(reg);
            if (!BuildingMerge.complexes(reg.list).isEmpty())
                problems.add("mills a block apart merged: " + shapes(BuildingMerge.complexes(reg.list)));
            // now a mill right beside the first, but shifted a block sideways: aligned on neither axis
            BuildingPlacement shifted = stamp(level, MILL, new BlockPos(x0 - PITCH, y, z0 + 1), owner, reg);
            if (shifted == null) {
                helper.fail("could not stamp the shifted mill");
                return;
            }
            if (BuildingMerge.touching(a, shifted))
                problems.add("a sideways-shifted neighbour must not count as touching");
            BuildingMerge.reformAll(reg);
            if (!BuildingMerge.complexes(reg.list).isEmpty())
                problems.add("a misaligned neighbour merged: " + shapes(BuildingMerge.complexes(reg.list)));
            if (problems.isEmpty())
                helper.succeed();
            else
                helper.fail(String.join("; ", problems));
        } finally {
            clear(level, x0 - PITCH, y, z0, 4, 3);
        }
    }

    /**
     * The per-building switch: a mill with merging off stays its own building while its neighbours grow into what
     * they can without it (an L of three: a Pair and a single); switched back on, the Farm forms. Lab and capitol
     * grids never merge at all.
     */
    @GameTest(template = ARENA, timeoutTicks = 20)
    public static void merge_off_keeps_a_mill_separate_and_labs_never_merge(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos base = helper.absolutePos(new BlockPos(5, 1, 5));
        final int y = 298, x0 = base.getX(), z0 = base.getZ();
        String owner = "gametest_merge_off_" + base.asLong();
        List<String> problems = new ArrayList<>();
        try {
            var reg = registry();
            List<BuildingPlacement> mills = stampGrid(level, x0, y, z0, 2, 2, owner, reg);
            if (mills.contains(null)) {
                helper.fail("could not stamp the mills");
                return;
            }
            BuildingPlacement loner = mills.get(3);
            loner.setMergeEnabled(false);
            if (loner.isMergeEnabled() || !loner.getTags().contains(BuildingPlacement.MERGE_OFF_TAG))
                problems.add("setMergeEnabled(false) should set the no_merge tag");
            BuildingMerge.reformAll(reg);
            List<ComplexPlacement> cs = BuildingMerge.complexes(reg.list);
            if (cs.size() != 1 || cs.get(0).getShape() != MergeShapes.Shape.PAIR)
                problems.add("three merge-on mills in an L should make one Pair, got [" + shapes(cs) + "]");
            if (!reg.list.contains(loner))
                problems.add("the merge-off mill should still stand on its own");
            if (BuildingMerge.complexWithSection(reg.list, loner.originPos) != null)
                problems.add("the merge-off mill joined a complex");
            if (reg.list.size() != 3)
                problems.add("expected a Pair, a single and the merge-off mill, got " + reg.list.size() + " placements");
            // switched back on (the server's toggle flips the flag; its own re-match only runs on the world's list)
            BuildingMerge.toggle(loner);
            if (!loner.isMergeEnabled())
                problems.add("toggle should switch merging back on");
            BuildingMerge.reformAll(reg);
            ComplexPlacement farm = onlyComplex(reg, problems, "after switching merging back on");
            if (farm != null && (farm.getShape() != MergeShapes.Shape.FARM || reg.list.size() != 1))
                problems.add("the four should now be one Farm, got " + farm.getShape() + " among " + reg.list.size());
            if (MILL.canMerge() != true || Buildings.BARRACKS.canMerge() || Buildings.TOWN_CENTRE.canMerge()
                    || Buildings.WIND_GENERATOR_STORM.canMerge())
                problems.add("only the White Angel mill merges in slice 1 (Buildings.canMerge)");
            // a grid of labs (and of capitols) never merges, however they stand
            var labs = registry();
            for (int i = 0; i < 2; i++)
                for (int j = 0; j < 2; j++) {
                    var lab = BuildingUtils.getNewBuildingPlacement(Buildings.BARRACKS, level,
                        new BlockPos(x0 + i * 20, y, z0 + j * 20), Rotation.NONE, owner, false);
                    if (lab == null)
                        continue;
                    lab.isBuilt = true;
                    labs.list.add(lab);
                }
            BuildingMerge.reformAll(labs);
            if (!BuildingMerge.complexes(labs.list).isEmpty() || labs.list.size() != 4)
                problems.add("labs merged: " + shapes(BuildingMerge.complexes(labs.list)));
            if (problems.isEmpty())
                helper.succeed();
            else
                helper.fail(String.join("; ", problems));
        } finally {
            clear(level, x0, y, z0, 2, 2);
        }
    }

    // ------------------------------------------------------------------ sections and shrinking

    /**
     * Damage lands on a section: a Quarter that loses a corner shrinks to a Row and a Pair (the biggest shapes the
     * eight survivors form) with those shapes' bonuses, the fallen mill's blocks are gone and the survivors' stand;
     * a Quarter that loses its centre becomes four Pairs; the new complexes carry the collapse cue. Losing every
     * section ends the complex.
     */
    @GameTest(template = ARENA, timeoutTicks = 20)
    public static void losing_a_section_shrinks_the_complex_to_the_biggest_shapes_left(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos base = helper.absolutePos(new BlockPos(3, 1, 3));
        final int y = 266, x0 = base.getX(), z0 = base.getZ();
        String owner = "gametest_merge_shrink_" + base.asLong();
        List<String> problems = new ArrayList<>();
        try {
            // a corner falls: Row + Pair
            var reg = registry();
            List<BuildingPlacement> mills = stampGrid(level, x0, y, z0, 3, 3, owner, reg);
            if (mills.contains(null)) {
                helper.fail("could not stamp the mills");
                return;
            }
            BuildingMerge.reformAll(reg);
            ComplexPlacement quarter = onlyComplex(reg, problems, "nine mills");
            if (quarter == null || quarter.getShape() != MergeShapes.Shape.QUARTER) {
                helper.fail("nine mills should make a Quarter; " + String.join("; ", problems));
                return;
            }
            BlockPos cornerOrigin = mills.get(8).originPos;   // (2, 2): the far corner
            ComplexPlacement.Section corner = quarter.sectionAt(mills.get(8).centrePos);
            if (corner == null || !corner.originPos.equals(cornerOrigin)) {
                helper.fail("the far corner's section was not found");
                return;
            }
            int hpBefore = quarter.getHealth();
            quarter.damageSection(corner, 10000);
            if (!quarter.isSectionDead(corner) || !quarter.hasDeadSection())
                problems.add("a 10000-point hit on one section should kill it: " + quarter.sectionBlocksPlaced(corner) + " blocks stand");
            for (ComplexPlacement.Section s : quarter.getSections())
                if (s != corner && quarter.sectionBlocksPlaced(s) != s.totalBlocks())
                    problems.add("the hit spilled onto another section: " + quarter.sectionBlocksPlaced(s) + " / " + s.totalBlocks());
            if (quarter.getHealth() >= hpBefore)
                problems.add("the complex's health did not drop with its section");
            long now = level.getGameTime();
            BuildingMerge.checkSections(quarter, reg);
            List<ComplexPlacement> after = BuildingMerge.complexes(reg.list);
            if (reg.list.contains(quarter))
                problems.add("the Quarter should have been replaced");
            if (!groupShapes(after, MergeShapes.Shape.ROW, MergeShapes.Shape.PAIR) || reg.list.size() != 2)
                problems.add("a Quarter minus a corner should be a Row and a Pair, got [" + shapes(after) + "] among " + reg.list.size());
            for (ComplexPlacement c : after) {
                float lone = mills.get(0).getEnergyIncome();
                float want = lone * c.memberCount() * c.getShape().outputMultiplier();
                if (Math.abs(c.getEnergyIncome() - want) > 1e-3f)
                    problems.add(c.getShape() + " should earn " + want + ", got " + c.getEnergyIncome());
                if (c.collapsedAtTick != now || !c.recentlyCollapsed.contains(cornerOrigin))
                    problems.add(c.getShape() + " should carry the collapse cue (" + cornerOrigin + " at " + now + "), has "
                        + c.recentlyCollapsed + " at " + c.collapsedAtTick);
                if (c.sectionAt(mills.get(8).centrePos) != null)
                    problems.add("the fallen corner is still a section of the " + c.getShape());
                if (Math.abs(c.getHealth() - c.getMaxHealth()) > 1)
                    problems.add("the survivors were whole, the " + c.getShape() + " should be at full health: " + c.getHealth() + " / " + c.getMaxHealth());
            }
            int standing = 0;
            for (var blk : mills.get(8).getBlocks())
                if (!level.getBlockState(blk.getBlockPos()).isAir())
                    standing++;
            if (standing > 0)
                problems.add("the fallen section left " + standing + " blocks standing");
            for (var blk : mills.get(0).getBlocks())
                if (!blk.getBlockState().isAir() && level.getBlockState(blk.getBlockPos()).isAir())
                    problems.add("a survivor lost blocks it was never hit on");
            clear(level, x0, y, z0, 3, 3);

            // the centre falls: four Pairs round the hole
            var reg2 = registry();
            List<BuildingPlacement> ring = stampGrid(level, x0, y, z0, 3, 3, owner + "_c", reg2);
            BuildingMerge.reformAll(reg2);
            ComplexPlacement q2 = onlyComplex(reg2, problems, "nine mills again");
            if (q2 != null) {
                ComplexPlacement.Section centre = q2.sectionAt(ring.get(4).centrePos);
                q2.damageSection(centre, 10000);
                BuildingMerge.checkSections(q2, reg2);
                List<ComplexPlacement> pairs = BuildingMerge.complexes(reg2.list);
                if (!groupShapes(pairs, MergeShapes.Shape.PAIR, MergeShapes.Shape.PAIR, MergeShapes.Shape.PAIR, MergeShapes.Shape.PAIR)
                        || reg2.list.size() != 4)
                    problems.add("a Quarter minus its centre should be four Pairs, got [" + shapes(pairs) + "] among " + reg2.list.size());
            }
            clear(level, x0, y, z0, 3, 3);

            // every section falls: the complex is gone
            var reg3 = registry();
            List<BuildingPlacement> two = stampGrid(level, x0, y, z0, 2, 1, owner + "_p", reg3);
            BuildingMerge.reformAll(reg3);
            ComplexPlacement pair = onlyComplex(reg3, problems, "two mills");
            if (pair != null) {
                for (ComplexPlacement.Section s : new ArrayList<>(pair.getSections()))
                    pair.damageSection(s, 10000);
                BuildingMerge.checkSections(pair, reg3);
                if (!reg3.list.isEmpty())
                    problems.add("a Pair that lost both sections should be gone, " + reg3.list.size() + " placements remain");
                for (BuildingPlacement m : two)
                    for (var blk : m.getBlocks())
                        if (!level.getBlockState(blk.getBlockPos()).isAir()) {
                            problems.add("a dead Pair left blocks standing");
                            break;
                        }
            }
            if (problems.isEmpty())
                helper.succeed();
            else
                helper.fail(String.join("; ", problems));
        } finally {
            clear(level, x0, y, z0, 3, 3);
        }
    }

    static boolean groupShapes(List<ComplexPlacement> complexes, MergeShapes.Shape... expected) {
        if (complexes.size() != expected.length)
            return false;
        List<MergeShapes.Shape> got = new ArrayList<>();
        for (ComplexPlacement c : complexes)
            got.add(c.getShape());
        List<MergeShapes.Shape> want = new ArrayList<>(List.of(expected));
        got.sort(null);
        want.sort(null);
        return got.equals(want);
    }

    /** A mill rebuilt on a fallen footprint re-forms the bigger shape: Farm -> (corner lost) Pair + single -> Farm again. */
    @GameTest(template = ARENA, timeoutTicks = 20)
    public static void rebuilding_a_fallen_section_re_forms_the_bigger_shape(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos base = helper.absolutePos(new BlockPos(5, 1, 5));
        final int y = 282, x0 = base.getX(), z0 = base.getZ();
        String owner = "gametest_merge_rebuild_" + base.asLong();
        List<String> problems = new ArrayList<>();
        try {
            var reg = registry();
            List<BuildingPlacement> mills = stampGrid(level, x0, y, z0, 2, 2, owner, reg);
            BuildingMerge.reformAll(reg);
            ComplexPlacement farm = onlyComplex(reg, problems, "four mills");
            if (farm == null) {
                helper.fail(String.join("; ", problems));
                return;
            }
            BlockPos lostOrigin = mills.get(3).originPos;
            farm.damageSection(farm.sectionAt(mills.get(3).centrePos), 10000);
            BuildingMerge.checkSections(farm, reg);
            List<ComplexPlacement> cs = BuildingMerge.complexes(reg.list);
            if (!groupShapes(cs, MergeShapes.Shape.PAIR) || reg.list.size() != 2)
                problems.add("a Farm minus a corner should be a Pair and a single, got [" + shapes(cs) + "] among " + reg.list.size());
            // the owner builds a new mill on the fallen footprint; when it finishes, the Farm is back
            BuildingPlacement rebuilt = stamp(level, MILL, lostOrigin, owner, reg);
            if (rebuilt == null) {
                helper.fail("could not stamp the rebuilt mill");
                return;
            }
            BuildingMerge.reformAll(reg);
            ComplexPlacement again = onlyComplex(reg, problems, "after the rebuild");
            if (again != null && (again.getShape() != MergeShapes.Shape.FARM || reg.list.size() != 1 || again.sectionAt(rebuilt.centrePos) == null))
                problems.add("the rebuilt mill should re-form the Farm, got " + again.getShape() + " among " + reg.list.size());
            if (again != null && !again.recentlyCollapsed.isEmpty())
                problems.add("a complex formed by a rebuild carries no collapse cue, has " + again.recentlyCollapsed);
            if (problems.isEmpty())
                helper.succeed();
            else
                helper.fail(String.join("; ", problems));
        } finally {
            clear(level, x0, y, z0, 2, 2);
        }
    }

    // ------------------------------------------------------------------ stability and state

    /**
     * A complex only changes when the grid round it grows into a bigger shape: a Quarter on the right of a 4x3 keeps
     * standing (the same object) when a 12th column appears on its left - the column becomes a Pair and a single -
     * while a Pair whose neighbours complete a Farm, and a Row whose third row completes a Quarter, are replaced by
     * the bigger shape.
     */
    @GameTest(template = ARENA, timeoutTicks = 20)
    public static void existing_complexes_stand_until_a_bigger_shape_forms(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos base = helper.absolutePos(new BlockPos(3, 1, 3));
        final int y = 266, x0 = base.getX(), z0 = base.getZ();
        String owner = "gametest_merge_stable_" + base.asLong();
        List<String> problems = new ArrayList<>();
        try {
            // the Quarter at columns 1-3, then column 0
            var reg = registry();
            stampGrid(level, x0 + PITCH, y, z0, 3, 3, owner, reg);
            BuildingMerge.reformAll(reg);
            ComplexPlacement quarter = onlyComplex(reg, problems, "nine mills");
            if (quarter == null) {
                helper.fail(String.join("; ", problems));
                return;
            }
            for (int j = 0; j < 3; j++)
                stamp(level, MILL, new BlockPos(x0, y, z0 + j * PITCH), owner, reg);
            BuildingMerge.reformAll(reg);
            List<ComplexPlacement> cs = BuildingMerge.complexes(reg.list);
            if (!reg.list.contains(quarter))
                problems.add("the Quarter was dissolved and re-formed for a column that could not grow it: [" + shapes(cs) + "]");
            if (!groupShapes(cs, MergeShapes.Shape.QUARTER, MergeShapes.Shape.PAIR) || reg.list.size() != 3)
                problems.add("a 4x3 round a standing Quarter should be that Quarter, a Pair and a single, got [" + shapes(cs) + "] among " + reg.list.size());
            clear(level, x0, y, z0, 4, 3);

            // a Pair grows into a Farm, a Row into a Quarter: the bigger shape wins over standing still
            var reg2 = registry();
            stampGrid(level, x0, y, z0, 2, 1, owner + "_f", reg2);
            BuildingMerge.reformAll(reg2);
            ComplexPlacement pair = onlyComplex(reg2, problems, "two mills");
            stamp(level, MILL, new BlockPos(x0, y, z0 + PITCH), owner + "_f", reg2);
            stamp(level, MILL, new BlockPos(x0 + PITCH, y, z0 + PITCH), owner + "_f", reg2);
            BuildingMerge.reformAll(reg2);
            ComplexPlacement farm = onlyComplex(reg2, problems, "a Pair plus two");
            if (farm == null || farm.getShape() != MergeShapes.Shape.FARM || farm == pair || reg2.list.size() != 1)
                problems.add("a Pair whose neighbours complete a Farm should become the Farm, got [" + shapes(BuildingMerge.complexes(reg2.list)) + "]");
            stamp(level, MILL, new BlockPos(x0 + 2 * PITCH, y, z0), owner + "_f", reg2);
            stamp(level, MILL, new BlockPos(x0 + 2 * PITCH, y, z0 + PITCH), owner + "_f", reg2);
            BuildingMerge.reformAll(reg2);
            ComplexPlacement row = onlyComplex(reg2, problems, "a Farm plus a column");
            if (row == null || row.getShape() != MergeShapes.Shape.ROW || reg2.list.size() != 1)
                problems.add("a Farm plus a column should become a Row, got [" + shapes(BuildingMerge.complexes(reg2.list)) + "]");
            for (int i = 0; i < 3; i++)
                stamp(level, MILL, new BlockPos(x0 + i * PITCH, y, z0 + 2 * PITCH), owner + "_f", reg2);
            BuildingMerge.reformAll(reg2);
            ComplexPlacement q = onlyComplex(reg2, problems, "a Row plus a row");
            if (q == null || q.getShape() != MergeShapes.Shape.QUARTER || reg2.list.size() != 1)
                problems.add("a Row plus its third row should become a Quarter, got [" + shapes(BuildingMerge.complexes(reg2.list)) + "]");
            // the pure rule: [6] beats [2, 2, 2]; [9, 2] ties [9, 2]; [2, 2] beats [2]
            if (!BuildingMerge.biggerShapes(groupsOf(6), groupsOf(2, 2, 2)) || BuildingMerge.biggerShapes(groupsOf(9, 2), groupsOf(9, 2))
                    || !BuildingMerge.biggerShapes(groupsOf(2, 2), groupsOf(2)))
                problems.add("biggerShapes misorders the partitions");
            if (problems.isEmpty())
                helper.succeed();
            else
                helper.fail(String.join("; ", problems));
        } finally {
            clear(level, x0, y, z0, 4, 3);
        }
    }

    static List<MergeShapes.Group> groupsOf(int... sizes) {
        List<MergeShapes.Group> out = new ArrayList<>();
        for (int n : sizes) {
            List<MergeShapes.Cell> cells = new ArrayList<>();
            for (int i = 0; i < n; i++)
                cells.add(new MergeShapes.Cell(i, 0));
            out.add(new MergeShapes.Group(MergeShapes.Shape.ofMembers(n) == null ? MergeShapes.Shape.PAIR : MergeShapes.Shape.ofMembers(n), cells, false));
        }
        return out;
    }

    /**
     * What a member brought goes with it: its scenario role, tags, payer, commands and the fraction of a block it
     * had lost ride on the complex and come back to the parts when it dissolves; its attack-target stand is dropped
     * when it is absorbed; the sections' save data is their own. Neutral buildings never merge. A complex being
     * decommissioned is left alone by its neighbours; when one of its sections falls, the parts carry the
     * decommission on - the complex's progress and each its share of the metal paid - and do not re-merge, so no
     * metal is ever paid twice.
     */
    @GameTest(template = ARENA, timeoutTicks = 20)
    public static void member_state_and_a_decommission_survive_merging_and_dissolving(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos base = helper.absolutePos(new BlockPos(5, 1, 5));
        final int y = 282, x0 = base.getX(), z0 = base.getZ();
        String owner = "gametest_merge_state_" + base.asLong();
        List<String> problems = new ArrayList<>();
        try {
            var reg = registry();
            List<BuildingPlacement> mills = stampGrid(level, x0, y, z0, 2, 2, owner, reg);
            for (BuildingPlacement m : mills) {
                m.scenarioRoleIndex = 2;
                m.payerName = owner + "_payer";
            }
            mills.get(1).addTag("scenario_marker");
            mills.get(2).addCommand("say hi", "ON_DESTROY", 0, 0);
            mills.get(3).partialBlocksDestroyed = 0.25;
            var stand = mills.get(0).getTargetStand();
            BuildingMerge.reformAll(reg);
            ComplexPlacement farm = onlyComplex(reg, problems, "four mills");
            if (farm == null) {
                helper.fail(String.join("; ", problems));
                return;
            }
            if (farm.scenarioRoleIndex != 2 || !(owner + "_payer").equals(farm.payerName))
                problems.add("the Farm should carry its members' role and payer, has role " + farm.scenarioRoleIndex + " payer " + farm.payerName);
            if (!farm.getTags().contains("scenario_marker") || farm.commands.size() != 1)
                problems.add("the Farm should carry its members' tags and commands: " + farm.getTags() + " / " + farm.commands.size());
            if (Math.abs(farm.partialBlocksDestroyed - 0.25) > 1e-6)
                problems.add("the Farm should carry the fraction of a block its members had lost, has " + farm.partialBlocksDestroyed);
            if (stand != null && !stand.isRemoved())
                problems.add("an absorbed member's attack-target stand should be discarded");
            var saved = farm.sectionSaveData(farm.getSections().get(1));
            var savedTags = saved.getData(com.solegendary.reignofnether.building.BuildingServerEvents.BUILDING_TAGS);
            if (savedTags == null || !savedTags.contains("scenario_marker") || saved.getData(BuildingReclaim.DECOMMISSION) != null)
                problems.add("a section's save data should hold its own tags and no decommission: " + savedTags);
            // nobody merges with a neutral grid
            var neutral = registry();
            stampGrid(level, x0 + 4 * PITCH, y, z0, 2, 1, "", neutral);
            BuildingMerge.reformAll(neutral);
            if (!BuildingMerge.complexes(neutral.list).isEmpty())
                problems.add("neutral (ownerless) mills merged");

            // the owner starts reclaiming the Farm: a new neighbour leaves it alone
            farm.decommissioning = true;
            farm.reclaimProgress = 0.4f;
            farm.reclaimPaid = 40f;
            BuildingPlacement late = stamp(level, MILL, new BlockPos(x0 + 2 * PITCH, y, z0), owner, reg);
            late.scenarioRoleIndex = 2;
            BuildingMerge.reformAll(reg);
            if (!reg.list.contains(farm) || BuildingMerge.complexes(reg.list).size() != 1 || reg.list.size() != 2)
                problems.add("a decommissioning Farm should stand untouched beside a new mill, got [" + shapes(BuildingMerge.complexes(reg.list)) + "] among " + reg.list.size());
            var mid = farm.sectionSaveData(farm.getSections().get(0));
            float[] share = mid.getData(BuildingReclaim.DECOMMISSION);
            if (share == null || Math.abs(share[0] - 0.4f) > 1e-5f || Math.abs(share[1] - 10f) > 1e-3f)
                problems.add("a decommissioning Farm's section should save progress 0.4 and a quarter of the 40 paid, got " + java.util.Arrays.toString(share));
            // a section falls: three parts, each carrying the decommission with a third of the metal paid, none re-merging
            ComplexPlacement.Section corner = farm.sectionAt(mills.get(3).centrePos);
            farm.damageSection(corner, 10000);
            BuildingMerge.checkSections(farm, reg);
            if (!BuildingMerge.complexes(reg.list).isEmpty() || reg.list.size() != 4)
                problems.add("the parts of a decommissioning Farm must not re-merge: [" + shapes(BuildingMerge.complexes(reg.list)) + "] among " + reg.list.size());
            float paid = 0;
            int parts = 0;
            for (BuildingPlacement p : reg.list) {
                if (p == late)
                    continue;
                parts++;
                if (!p.decommissioning || Math.abs(p.reclaimProgress - 0.4f) > 1e-5f)
                    problems.add("a part should carry the decommission at progress 0.4, has " + p.decommissioning + " " + p.reclaimProgress);
                if (p.scenarioRoleIndex != 2 || !(owner + "_payer").equals(p.payerName))
                    problems.add("a part should get its role and payer back, has role " + p.scenarioRoleIndex + " payer " + p.payerName);
                if (p.originPos.equals(mills.get(1).originPos) && !p.getTags().contains("scenario_marker"))
                    problems.add("a part should get its own tags back, has " + p.getTags());
                if (p.originPos.equals(mills.get(2).originPos) && p.commands.size() != 1)
                    problems.add("a part should get its own commands back, has " + p.commands.size());
                paid += p.reclaimPaid;
            }
            if (parts != 3 || Math.abs(paid - 40f) > 1e-3f)
                problems.add("the parts' shares should add up to the 40 the Farm had returned, got " + paid + " over " + parts + " parts");
            if (problems.isEmpty())
                helper.succeed();
            else
                helper.fail(String.join("; ", problems));
        } finally {
            clear(level, x0, y, z0, 6, 2);
        }
    }

    // ------------------------------------------------------------------ economy

    /**
     * With the wind at 4 and at 24 (resources/Wind's range) a Farm earns exactly its four mills' wind-driven income
     * times 1.08 - the complex follows the wind through its members, it never flattens it. The live wind is put back
     * before the call returns (no tick runs in between).
     */
    @GameTest(template = ARENA, timeoutTicks = 20)
    public static void income_follows_the_wind_through_the_members(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos base = helper.absolutePos(new BlockPos(5, 1, 5));
        final int y = 298, x0 = base.getX(), z0 = base.getZ();
        String owner = "gametest_merge_wind_" + base.asLong();
        List<String> problems = new ArrayList<>();
        float windBefore = Wind.strength();
        try {
            var reg = registry();
            List<BuildingPlacement> mills = stampGrid(level, x0, y, z0, 2, 2, owner, reg);
            BuildingMerge.reformAll(reg);
            ComplexPlacement farm = onlyComplex(reg, problems, "four mills");
            if (farm == null) {
                helper.fail(String.join("; ", problems));
                return;
            }
            float flat = MILL.getEnergyIncome();
            for (float wind : new float[] { Wind.MIN, Wind.MAX }) {
                Wind.setFromServer(wind);
                float lone = mills.get(0).getEnergyIncome();
                float wantLone = flat * wind / Wind.MEAN;
                if (Math.abs(lone - wantLone) > 1e-3f)
                    problems.add("a lone mill at wind " + wind + " should earn " + wantLone + ", got " + lone);
                float want = lone * 4 * 1.08f;
                if (Math.abs(farm.getEnergyIncome() - want) > 1e-3f)
                    problems.add("a Farm at wind " + wind + " should earn " + want + ", got " + farm.getEnergyIncome());
            }
            if (farm.getMetalIncome() != 0 || farm.getMetalStorage() != 0 || farm.getEnergyStorage() != 0)
                problems.add("a wind farm gives no metal and no storage");
            if (problems.isEmpty())
                helper.succeed();
            else
                helper.fail(String.join("; ", problems));
        } finally {
            Wind.setFromServer(windBefore);
            clear(level, x0, y, z0, 2, 2);
        }
    }

    // ------------------------------------------------------------------ the wire

    /** The MERGE packet comes back from its bytes as it went in: members, cells, shape, counts and the animation cues. */
    @GameTest(template = ARENA, timeoutTicks = 20)
    public static void merge_packet_round_trips(GameTestHelper helper) {
        List<BlockPos> origins = List.of(new BlockPos(10, 64, -20), new BlockPos(13, 64, -20), new BlockPos(10, 65, -17));
        List<Rotation> rotations = List.of(Rotation.NONE, Rotation.CLOCKWISE_90, Rotation.COUNTERCLOCKWISE_90);
        List<MergeShapes.Cell> cells = List.of(new MergeShapes.Cell(0, 0), new MergeShapes.Cell(1, 0), new MergeShapes.Cell(0, 1));
        List<BlockPos> fallen = List.of(new BlockPos(13, 64, -17));
        ResourceLocation key = ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "wind_generator_villagers");
        var out = new MergedBuildingClientboundPacket(key, "lovish", origins, rotations, cells, MergeShapes.Shape.PAIR, 168, 3, 1234L, 1300L, fallen);
        FriendlyByteBuf buf = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        out.encode(buf);
        var in = new MergedBuildingClientboundPacket(buf);
        List<String> problems = new ArrayList<>();
        if (!key.equals(in.buildingKey()) || !"lovish".equals(in.ownerName()))
            problems.add("key / owner changed: " + in.buildingKey() + " " + in.ownerName());
        if (!origins.equals(in.origins()) || !rotations.equals(in.rotations()) || !cells.equals(in.cells()))
            problems.add("members changed: " + in.origins() + " " + in.rotations() + " " + in.cells());
        if (in.shape() != MergeShapes.Shape.PAIR || in.blocksPlaced() != 168 || in.scenarioRoleIndex() != 3
                || in.formedAtTick() != 1234L || in.collapsedAtTick() != 1300L)
            problems.add("shape / counts / role / ticks changed: " + in.shape() + " " + in.blocksPlaced() + " " + in.scenarioRoleIndex()
                + " " + in.formedAtTick() + " " + in.collapsedAtTick());
        if (!fallen.equals(in.collapsed()))
            problems.add("the collapse cue changed: " + in.collapsed());
        if (buf.readableBytes() != 0)
            problems.add(buf.readableBytes() + " bytes left unread");
        if (problems.isEmpty())
            helper.succeed();
        else
            helper.fail(String.join("; ", problems));
    }
}
