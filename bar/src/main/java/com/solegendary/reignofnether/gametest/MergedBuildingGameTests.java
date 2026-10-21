package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.building.Building;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.BuildingServerEvents;
import com.solegendary.reignofnether.building.BuildingUtils;
import com.solegendary.reignofnether.building.Buildings;
import com.solegendary.reignofnether.building.data.DataStorage;
import com.solegendary.reignofnether.building.merge.MergeGrid;
import com.solegendary.reignofnether.building.merge.MergedBuildings;
import com.solegendary.reignofnether.building.merge.MergedComplex;
import com.solegendary.reignofnether.building.merge.MergedShape;
import com.solegendary.reignofnether.resources.Wind;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Rotation;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * Merged buildings, slice 1 (claude/merged-buildings.md; building/merge): a Shift+Alt grid of White Angel wind
 * generators grows into one complex - Pair, Farm, Row, Quarter - whose members are sections with their own health;
 * output = the members summed x the shape's bonus; a dead section shrinks the complex to the biggest shape the
 * survivors still form, a rebuilt one re-forms it; the merge switch keeps a building out; only touching, aligned
 * buildings of one owner and kind merge; the ladder stops at nine in this slice. Own holder (the CI guard keeps new
 * tests out of SkirmishGameTests). The shape tests run a private registry over placements that are never in the
 * world's list; the income and collapse tests register theirs and take them out again, as FriendlyFireGameTests does.
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class MergedBuildingGameTests {

    static final String ARENA = SkirmishGameTests.ARENA;
    /** The White Angel mill's footprint: 3x3, so grid cells are three blocks apart (edge to edge). */
    static final int PITCH = 3;

    static String owner(GameTestHelper helper) {
        return "gametest_merge_" + helper.absolutePos(BlockPos.ZERO).asLong();
    }

    /** A finished placement at grid cell (i, j) of a grid whose (0, 0) member's origin is {@code base}; not in the world. */
    static BuildingPlacement member(GameTestHelper helper, Building building, String owner, BlockPos base, int i, int j) {
        BuildingPlacement bp = BuildingUtils.getNewBuildingPlacement(building, helper.getLevel(),
            base.offset(i * PITCH, 0, j * PITCH), Rotation.NONE, owner, false);
        bp.isBuilt = true;
        return bp;
    }

    /** As {@link #member}, with its blocks standing in the world so damage and collapse are the real thing. */
    static BuildingPlacement standing(GameTestHelper helper, String owner, BlockPos base, int i, int j) {
        ServerLevel level = helper.getLevel();
        BuildingPlacement bp = BuildingUtils.getNewBuildingPlacement(Buildings.WIND_GENERATOR_VILLAGERS, level,
            base.offset(i * PITCH, 0, j * PITCH), Rotation.NONE, owner, false);
        for (var b : bp.getBlocks())
            if (!b.getBlockState().isAir())
                level.setBlock(b.getBlockPos(), b.getBlockState(), 3);
        bp.initPlacedBlocks();
        bp.isBuilt = true;
        return bp;
    }

    static List<BuildingPlacement> grid(GameTestHelper helper, Building building, String owner, BlockPos base, int w, int d) {
        List<BuildingPlacement> out = new ArrayList<>();
        for (int j = 0; j < d; j++)
            for (int i = 0; i < w; i++)
                out.add(member(helper, building, owner, base, i, j));
        return out;
    }

    static String shapes(MergedBuildings reg) {
        List<String> names = new ArrayList<>();
        for (MergedComplex c : reg.complexes())
            names.add(c.shape.name());
        java.util.Collections.sort(names);
        return String.join("+", names);
    }

    static String cut(List<MergeGrid.Match> matches, int singles) {
        List<String> names = new ArrayList<>();
        for (MergeGrid.Match m : matches)
            names.add(m.shape().name());
        java.util.Collections.sort(names);
        if (singles > 0)
            names.add(singles + "single");
        return String.join("+", names);
    }

    static String cutOf(List<MergeGrid.Cell> cells) {
        List<MergeGrid.Match> matches = MergeGrid.partition(cells, MergedBuildings.MAX_MEMBERS);
        return cut(matches, MergeGrid.singles(cells, matches).size());
    }

    static List<MergeGrid.Cell> cells(int... xz) {
        List<MergeGrid.Cell> out = new ArrayList<>();
        for (int k = 0; k + 1 < xz.length; k += 2)
            out.add(new MergeGrid.Cell(xz[k], xz[k + 1]));
        return out;
    }

    /** The matcher's ladder (spec 2b): biggest shapes first, leftovers as the most pairs, the rest single. */
    @GameTest(template = ARENA, timeoutTicks = 20)
    public static void grids_cut_into_the_biggest_shapes_first_and_leftovers_into_pairs(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        String[][] cases = {
            { "2x1", cutOf(MergeGrid.rectangle(2, 1)), "PAIR" },
            { "1x2", cutOf(MergeGrid.rectangle(1, 2)), "PAIR" },
            { "3x1", cutOf(MergeGrid.rectangle(3, 1)), "PAIR+1single" },
            { "2x2", cutOf(MergeGrid.rectangle(2, 2)), "FARM" },
            { "3x2", cutOf(MergeGrid.rectangle(3, 2)), "ROW" },
            { "2x3", cutOf(MergeGrid.rectangle(2, 3)), "ROW" },
            { "3x3", cutOf(MergeGrid.rectangle(3, 3)), "QUARTER" },
            { "4x3", cutOf(MergeGrid.rectangle(4, 3)), "PAIR+QUARTER+1single" },
            { "2x4", cutOf(MergeGrid.rectangle(2, 4)), "PAIR+ROW" },
            { "4x4 (slice 1 stops at nine)", cutOf(MergeGrid.rectangle(4, 4)), "PAIR+PAIR+PAIR+QUARTER+1single" },
            { "6x5 drag", cutOf(MergeGrid.rectangle(6, 5)), "QUARTER+QUARTER+ROW+ROW" },
            { "7x1 line", cutOf(MergeGrid.rectangle(7, 1)), "PAIR+PAIR+PAIR+1single" },
            { "L of 5", cutOf(cells(0, 0, 1, 0, 2, 0, 0, 1, 0, 2)), "PAIR+PAIR+1single" },
            { "ring of 8 (a Quarter without its centre)", cutOf(cells(0, 0, 1, 0, 2, 0, 0, 1, 2, 1, 0, 2, 1, 2, 2, 2)), "PAIR+PAIR+PAIR+PAIR" },
            { "Quarter without a corner", cutOf(cells(1, 0, 2, 0, 0, 1, 1, 1, 2, 1, 0, 2, 1, 2, 2, 2)), "PAIR+ROW" },
            { "single", cutOf(MergeGrid.rectangle(1, 1)), "1single" },
            { "negative cells", cutOf(cells(-1, -1, 0, -1, -1, 0, 0, 0)), "FARM" },
        };
        for (String[] c : cases)
            if (!c[1].equals(c[2]))
                problems.add(c[0] + " cut into " + c[1] + ", wanted " + c[2]);
        // the same cells in another order give the same cut (the server, the ghost and every client must agree)
        List<MergeGrid.Cell> shuffled = new ArrayList<>(MergeGrid.rectangle(4, 3));
        java.util.Collections.reverse(shuffled);
        List<MergeGrid.Match> a = MergeGrid.partition(MergeGrid.rectangle(4, 3), MergedBuildings.MAX_MEMBERS);
        List<MergeGrid.Match> b = MergeGrid.partition(shuffled, MergedBuildings.MAX_MEMBERS);
        if (!a.equals(b))
            problems.add("the cut depends on the cells' order: " + a + " vs " + b);
        if (MergedBuildings.MAX_MEMBERS != 9 || MergedShape.ladder(9).get(0) != MergedShape.QUARTER
                || MergedShape.ladder(25).get(0) != MergedShape.WONDER)
            problems.add("slice 1's ladder should stop at the Quarter (" + MergedShape.ladder(MergedBuildings.MAX_MEMBERS) + ")");
        float[] bonus = { MergedShape.PAIR.bonus, MergedShape.FARM.bonus, MergedShape.ROW.bonus, MergedShape.QUARTER.bonus };
        if (Math.abs(bonus[0] - 0.04f) > 1e-6 || Math.abs(bonus[1] - 0.08f) > 1e-6 || Math.abs(bonus[2] - 0.10f) > 1e-6 || Math.abs(bonus[3] - 0.12f) > 1e-6)
            problems.add("the spec's bonus ladder is +4 / +8 / +10 / +12%, not " + java.util.Arrays.toString(bonus));
        if (problems.isEmpty())
            helper.succeed();
        else
            helper.fail(String.join("; ", problems));
    }

    /**
     * Four mills in a 2x2 become a Farm the moment the registry sees them all built: each earns its own income (the
     * wind included) times 1.08, so the Farm's output is the four summed plus 8% - at the live wind, at the calmest
     * wind (4) and at the strongest (24). A mill on its own beside them earns the plain income.
     */
    @GameTest(template = ARENA, timeoutTicks = 40)
    public static void a_2x2_grid_forms_a_farm_whose_output_is_the_members_summed_plus_8_percent(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = owner(helper);
        BlockPos base = helper.absolutePos(new BlockPos(1, 1, 1));
        List<BuildingPlacement> mine = grid(helper, Buildings.WIND_GENERATOR_VILLAGERS, owner, base, 2, 2);
        BuildingPlacement alone = member(helper, Buildings.WIND_GENERATOR_VILLAGERS, owner, base, 4, 0);   // a gap of one cell
        mine.add(alone);
        List<String> problems = new ArrayList<>();
        float windBefore = Wind.strength();
        try {
            BuildingServerEvents.getBuildings().addAll(mine);
            MergedBuildings.SERVER.update(BuildingServerEvents.getBuildings(), level.getGameTime());
            MergedComplex farm = MergedBuildings.SERVER.complexOf(mine.get(0));
            if (farm == null || farm.shape != MergedShape.FARM || farm.members.size() != 4)
                problems.add("the 2x2 did not form a Farm: " + farm);
            else {
                for (int k = 0; k < 4; k++)
                    if (MergedBuildings.SERVER.complexOf(mine.get(k)) != farm)
                        problems.add("member " + k + " is not in the Farm");
                if (!farm.isRaising(level.getGameTime()) || farm.isRaising(level.getGameTime() + MergedBuildings.RAISING_TICKS))
                    problems.add("a complex that just formed should be raising for " + MergedBuildings.RAISING_TICKS + " ticks");
                if (farm.previousShape != null || !farm.lostSections.isEmpty())
                    problems.add("a Farm out of separate mills has no previous shape: " + farm.previousShape + " " + farm.lostSections);
                if (farm.minCorner.getX() != base.getX() || farm.maxCorner.getX() != base.getX() + 2 * PITCH - 1
                        || farm.minCorner.getZ() != base.getZ() || farm.maxCorner.getZ() != base.getZ() + 2 * PITCH - 1)
                    problems.add("the Farm's footprint is wrong: " + farm.minCorner + " - " + farm.maxCorner);
            }
            if (MergedBuildings.SERVER.complexOf(alone) != null)
                problems.add("a mill one cell away joined the Farm");
            float base14 = Buildings.WIND_GENERATOR_VILLAGERS.getEnergyIncome();
            for (float wind : new float[] { Wind.strength(), Wind.MIN, Wind.MAX }) {
                Wind.setFromServer(wind);
                float single = alone.getEnergyIncome();
                float wantSingle = base14 * wind / Wind.MEAN;
                if (Math.abs(single - wantSingle) > 0.01f)
                    problems.add("at wind " + wind + " a lone mill earns " + single + ", wanted " + wantSingle);
                float sum = 0;
                for (int k = 0; k < 4; k++) {
                    float e = mine.get(k).getEnergyIncome();
                    if (Math.abs(e - wantSingle * MergedShape.FARM.outputMultiplier()) > 0.01f)
                        problems.add("at wind " + wind + " a Farm section earns " + e + ", wanted " + wantSingle * 1.08f);
                    sum += e;
                }
                if (Math.abs(sum - 4 * wantSingle * 1.08f) > 0.05f)
                    problems.add("at wind " + wind + " the Farm's output is " + sum + ", wanted the four summed plus 8% = " + 4 * wantSingle * 1.08f);
            }
        } finally {
            Wind.setFromServer(windBefore);
            BuildingServerEvents.getBuildings().removeAll(mine);
            MergedBuildings.SERVER.update(BuildingServerEvents.getBuildings(), level.getGameTime());
        }
        for (BuildingPlacement p : mine)
            if (MergedBuildings.SERVER.complexOf(p) != null)
                problems.add("a removed mill is still in a complex");
        if (problems.isEmpty())
            helper.succeed();
        else
            helper.fail(String.join("; ", problems));
    }

    /**
     * Sections (spec 2d): a hit lands on the one section under it and leaves the others whole; a corner of a Quarter
     * that falls - its blocks destroyed, the server's own tick removing it - shrinks the complex to the biggest shape
     * the eight survivors form (a Row and a Pair), the Row remembering the Quarter it was and where the corner stood;
     * a mill rebuilt on the corner re-forms the Quarter.
     */
    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void a_lost_section_shrinks_the_complex_and_a_rebuilt_one_re_forms_it(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = owner(helper);
        SkirmishGameTests.openGround(helper, 0, 0, 10, 10);
        BlockPos base = helper.absolutePos(new BlockPos(1, 1, 1));
        List<BuildingPlacement> mine = new ArrayList<>();
        for (int j = 0; j < 3; j++)
            for (int i = 0; i < 3; i++)
                mine.add(standing(helper, owner, base, i, j));
        BuildingPlacement corner = mine.get(0), centre = mine.get(4);
        List<String> problems = new ArrayList<>();
        Runnable cleanup = () -> {
            BuildingServerEvents.getBuildings().removeAll(mine);
            MergedBuildings.SERVER.update(BuildingServerEvents.getBuildings(), level.getGameTime());
        };
        try {
            BuildingServerEvents.getBuildings().addAll(mine);
            MergedBuildings.SERVER.update(BuildingServerEvents.getBuildings(), level.getGameTime());
            MergedComplex quarter = MergedBuildings.SERVER.complexOf(corner);
            if (quarter == null || quarter.shape != MergedShape.QUARTER) {
                cleanup.run();
                helper.fail("the 3x3 did not form a Quarter: " + quarter);
                return;
            }
            // a hit on the corner: the corner's own blocks, nobody else's
            int cornerBefore = corner.getBlocksPlaced(), centreBefore = centre.getBlocksPlaced();
            corner.destroyRandomBlocks(6);
            if (corner.getBlocksPlaced() >= cornerBefore)
                problems.add("a 6-damage hit left the corner's " + cornerBefore + " blocks standing");
            if (centre.getBlocksPlaced() != centreBefore)
                problems.add("a hit on the corner cost the centre " + (centreBefore - centre.getBlocksPlaced()) + " blocks");
            if (MergedBuildings.SERVER.complexOf(corner) != quarter)
                problems.add("a damaged section left its complex");
            float hp = quarter.healthFraction();
            if (hp <= 0 || hp >= 1)
                problems.add("the Quarter's health should read a little under full after one hit: " + hp);
            // the corner falls: every block gone, and the server's building tick takes it out of the world
            corner.destroyRandomBlocks(1000);
            if (!corner.shouldBeDestroyed()) {
                cleanup.run();
                helper.fail("the corner did not collapse: " + corner.getBlocksPlaced() + " blocks placed, " + corner.constructionState());
                return;
            }
        } catch (RuntimeException e) {
            cleanup.run();
            throw e;
        }
        helper.runAfterDelay(3, () -> {
            try {
                if (BuildingServerEvents.getBuildings().contains(corner))
                    problems.add("the collapsed corner is still in the building list");
                MergedBuildings.SERVER.update(BuildingServerEvents.getBuildings(), level.getGameTime());
                if (MergedBuildings.SERVER.complexOf(corner) != null)
                    problems.add("the collapsed corner is still in a complex");
                MergedComplex row = MergedBuildings.SERVER.complexOf(centre);
                MergedComplex pair = MergedBuildings.SERVER.complexOf(mine.get(1));
                if (row == null || row.shape != MergedShape.ROW || row.members.size() != 6)
                    problems.add("the eight survivors should form a Row round the centre: " + row);
                else {
                    if (row.previousShape != MergedShape.QUARTER)
                        problems.add("the Row should remember it was a Quarter: " + row.previousShape);
                    if (row.lostSections.size() != 1 || !row.lostSections.get(0).equals(corner.minCorner))
                        problems.add("the Row should know where the corner stood: " + row.lostSections);
                    if (!row.isSettling(level.getGameTime()))
                        problems.add("a complex that just shrank should be settling");
                    if (Math.abs(centre.getEnergyIncome() / Wind.multiplier() - 14f * 1.10f) > 0.02f)
                        problems.add("a Row section should earn +10%: " + centre.getEnergyIncome() / Wind.multiplier());
                }
                if (pair == null || pair.shape != MergedShape.PAIR || pair == row)
                    problems.add("the two mills beside the corner should form a Pair: " + pair);
                // rebuilt on the corner's footprint: the Quarter stands again
                BuildingPlacement rebuilt = standing(helper, owner, base, 0, 0);
                mine.add(rebuilt);
                BuildingServerEvents.getBuildings().add(rebuilt);
                List<MergedBuildings.Change> changes = MergedBuildings.SERVER.update(BuildingServerEvents.getBuildings(), level.getGameTime());
                MergedComplex again = MergedBuildings.SERVER.complexOf(rebuilt);
                if (again == null || again.shape != MergedShape.QUARTER || again.members.size() != 9)
                    problems.add("the rebuilt corner did not re-form the Quarter: " + again);
                else if (again.previousShape != MergedShape.ROW)
                    problems.add("the re-formed Quarter should know it grew out of the Row: " + again.previousShape);
                boolean grew = false;
                for (MergedBuildings.Change ch : changes)
                    if (ch.kind() == MergedBuildings.ChangeKind.GREW && ch.complex() == again)
                        grew = true;
                if (!grew)
                    problems.add("re-forming should report GREW, got " + changes);
            } finally {
                cleanup.run();
            }
            if (problems.isEmpty())
                helper.succeed();
            else
                helper.fail(String.join("; ", problems));
        });
    }

    /** The merge switch (spec 2e): a mill switched off stays its own building and earns no bonus; the rest re-cut round it. */
    @GameTest(template = ARENA, timeoutTicks = 20)
    public static void merging_switched_off_keeps_a_mill_on_its_own(GameTestHelper helper) {
        String owner = owner(helper);
        BlockPos base = helper.absolutePos(new BlockPos(1, 1, 1));
        List<BuildingPlacement> mine = grid(helper, Buildings.WIND_GENERATOR_VILLAGERS, owner, base, 2, 2);
        List<String> problems = new ArrayList<>();
        MergedBuildings reg = new MergedBuildings();
        reg.recompute(mine, 0);
        if (!"FARM".equals(shapes(reg)))
            problems.add("four mills switched on should form a Farm: " + shapes(reg));
        mine.get(3).mergeEnabled = false;
        List<MergedBuildings.Change> changes = reg.update(mine, 10);
        if (reg.complexOf(mine.get(3)) != null)
            problems.add("a mill switched off is still in a complex");
        if (!"PAIR".equals(shapes(reg)))
            problems.add("the three left should make a Pair and a single: " + shapes(reg));
        if (Math.abs(reg.outputMultiplier(mine.get(3)) - 1f) > 1e-6)
            problems.add("a mill switched off should earn no bonus: " + reg.outputMultiplier(mine.get(3)));
        boolean dissolvedQuietly = false;
        for (MergedBuildings.Change ch : changes)
            if (ch.kind() == MergedBuildings.ChangeKind.SHRUNK && ch.destroyed() == 0)
                dissolvedQuietly = true;
        if (!dissolvedQuietly)
            problems.add("switching a section off is a SHRUNK with nothing destroyed, got " + changes);
        if (!reg.update(mine, 11).isEmpty())
            problems.add("nothing changed, yet the registry re-derived");
        mine.get(3).mergeEnabled = true;
        reg.update(mine, 12);
        if (!"FARM".equals(shapes(reg)))
            problems.add("switched back on, the Farm should stand again: " + shapes(reg));
        if (problems.isEmpty())
            helper.succeed();
        else
            helper.fail(String.join("; ", problems));
    }

    /**
     * What never merges: mills a block apart or offset by a block, two owners' mills, another faction's generator (not
     * in this slice), a lab or a capitol grid. Members of a complex are all one owner's.
     */
    @GameTest(template = ARENA, timeoutTicks = 20)
    public static void only_touching_aligned_mills_of_one_owner_merge_and_never_labs(GameTestHelper helper) {
        String owner = owner(helper), other = owner + "_other";
        BlockPos base = helper.absolutePos(new BlockPos(1, 1, 1));
        List<String> problems = new ArrayList<>();
        MergedBuildings reg = new MergedBuildings();
        var wind = Buildings.WIND_GENERATOR_VILLAGERS;
        // a gap of one block
        List<BuildingPlacement> gapped = List.of(member(helper, wind, owner, base, 0, 0),
            BuildingUtils.getNewBuildingPlacement(wind, helper.getLevel(), base.offset(PITCH + 1, 0, 0), Rotation.NONE, owner, false));
        gapped.get(1).isBuilt = true;
        reg.recompute(gapped, 0);
        if (!reg.complexes().isEmpty())
            problems.add("mills a block apart merged: " + shapes(reg));
        // offset by one block along the shared edge
        List<BuildingPlacement> offset = List.of(member(helper, wind, owner, base, 0, 0),
            BuildingUtils.getNewBuildingPlacement(wind, helper.getLevel(), base.offset(PITCH, 0, 1), Rotation.NONE, owner, false));
        offset.get(1).isBuilt = true;
        reg.recompute(offset, 0);
        if (!reg.complexes().isEmpty())
            problems.add("mills offset by a block merged: " + shapes(reg));
        // two owners in one 2x2: each owner's pair, never a Farm across them
        List<BuildingPlacement> shared = List.of(member(helper, wind, owner, base, 0, 0), member(helper, wind, owner, base, 1, 0),
            member(helper, wind, other, base, 0, 1), member(helper, wind, other, base, 1, 1));
        reg.recompute(shared, 0);
        if (!"PAIR+PAIR".equals(shapes(reg)))
            problems.add("two owners' 2x2 should be two Pairs: " + shapes(reg));
        for (MergedComplex c : reg.complexes())
            for (BuildingPlacement m : c.members)
                if (!m.ownerName.equals(c.ownerName))
                    problems.add("a complex mixed owners: " + c);
        // an unfinished member does not count yet
        List<BuildingPlacement> building = grid(helper, wind, owner, base, 2, 2);
        building.get(3).isBuilt = false;
        reg.recompute(building, 0);
        if (!"PAIR".equals(shapes(reg)))
            problems.add("three built and one rising should be a Pair and singles: " + shapes(reg));
        // not in this slice: the other factions' generators; never: labs, capitols, extractors (spec 2a)
        if (Buildings.TOWN_CENTRE.mergeable)
            problems.add("the capitol is marked mergeable");
        for (Building b : List.of(Buildings.WIND_GENERATOR_MONSTERS, Buildings.WIND_GENERATOR_PIGLINS, Buildings.BARRACKS,
                Buildings.METAL_EXTRACTOR_VILLAGERS)) {
            if (b.mergeable)
                problems.add(b.structureName + " is marked mergeable in slice 1");
            List<BuildingPlacement> others = new ArrayList<>();
            // lay them edge to edge whatever their footprint
            BuildingPlacement first = member(helper, b, owner, base, 0, 0);
            int sx = first.maxCorner.getX() - first.minCorner.getX() + 1, sz = first.maxCorner.getZ() - first.minCorner.getZ() + 1;
            others.add(first);
            for (int[] ij : new int[][] { { 1, 0 }, { 0, 1 }, { 1, 1 } }) {
                BuildingPlacement p = BuildingUtils.getNewBuildingPlacement(b, helper.getLevel(),
                    base.offset(ij[0] * sx, 0, ij[1] * sz), Rotation.NONE, owner, false);
                p.isBuilt = true;
                others.add(p);
            }
            reg.recompute(others, 0);
            if (!reg.complexes().isEmpty())
                problems.add(b.structureName + " merged: " + shapes(reg));
        }
        if (!wind.mergeable)
            problems.add("the White Angel mill should be mergeable");
        if (problems.isEmpty())
            helper.succeed();
        else
            helper.fail(String.join("; ", problems));
    }

    /**
     * A complex keeps its identity (and its formed tick) while its members stand, whatever changes elsewhere; a Pair
     * that loses a member dissolves (DISSOLVED, one destroyed) and the survivor earns the plain income again; a member
     * laid with the ghost rotated has the same footprint and merges like any other; after a reload, the same mills form
     * the same complexes again from nothing but the buildings.
     */
    @GameTest(template = ARENA, timeoutTicks = 20)
    public static void complexes_keep_their_identity_a_pair_losing_a_member_dissolves_and_rotation_does_not_matter(GameTestHelper helper) {
        String owner = owner(helper);
        BlockPos base = helper.absolutePos(new BlockPos(1, 1, 1));
        List<String> problems = new ArrayList<>();
        MergedBuildings reg = new MergedBuildings();
        List<BuildingPlacement> all = new ArrayList<>(grid(helper, Buildings.WIND_GENERATOR_VILLAGERS, owner, base, 2, 2));
        reg.update(all, 100);
        MergedComplex farm = reg.complexOf(all.get(0));
        if (farm == null || farm.shape != MergedShape.FARM || farm.formedAt != 100)
            problems.add("no Farm at tick 100: " + farm);
        // an unrelated pair far away: the Farm is untouched, the same object with the same formed tick
        BuildingPlacement a = member(helper, Buildings.WIND_GENERATOR_VILLAGERS, owner, base, 4, 4);
        // a quarter turn maps the structure's (x, z) to (-z, x), so its footprint runs 2 blocks WEST of the origin:
        // the origin two blocks further east puts it edge to edge with a (as getBuildingOriginPos does for the ghost)
        BuildingPlacement b = BuildingUtils.getNewBuildingPlacement(Buildings.WIND_GENERATOR_VILLAGERS, helper.getLevel(),
            base.offset(5 * PITCH + 2, 0, 4 * PITCH), Rotation.CLOCKWISE_90, owner, false);
        b.isBuilt = true;
        if (b.minCorner.getX() != a.maxCorner.getX() + 1 || b.minCorner.getZ() != a.minCorner.getZ() || b.maxCorner.getZ() != a.maxCorner.getZ())
            problems.add("the rotated mill does not stand edge to edge with its neighbour: " + a.minCorner + "-" + a.maxCorner
                + " vs " + b.minCorner + "-" + b.maxCorner);
        all.add(a);
        all.add(b);
        List<MergedBuildings.Change> changes = reg.update(all, 150);
        if (reg.complexOf(all.get(0)) != farm || farm.formedAt != 100)
            problems.add("the Farm was re-made by a change elsewhere: " + reg.complexOf(all.get(0)));
        MergedComplex pair = reg.complexOf(a);
        if (pair == null || pair.shape != MergedShape.PAIR || !pair.contains(b) || pair.formedAt != 150)
            problems.add("a mill laid rotated did not pair with its neighbour: " + pair);
        if (changes.size() != 1 || changes.get(0).kind() != MergedBuildings.ChangeKind.FORMED || changes.get(0).complex() != pair)
            problems.add("the only change should be the Pair forming, got " + changes);
        // the rotated mill falls: the Pair dissolves, its survivor stands alone at the plain income
        all.remove(b);
        changes = reg.update(all, 200);
        if (reg.complexOf(a) != null || reg.complexOf(b) != null)
            problems.add("a Pair that lost a member still stands: " + reg.complexOf(a));
        boolean dissolved = false;
        for (MergedBuildings.Change ch : changes)
            if (ch.kind() == MergedBuildings.ChangeKind.DISSOLVED && ch.previous() == pair && ch.destroyed() == 1)
                dissolved = true;
        if (!dissolved)
            problems.add("losing one of a Pair should be a DISSOLVED with one destroyed, got " + changes);
        if (Math.abs(reg.outputMultiplier(a) - 1f) > 1e-6)
            problems.add("the survivor of a Pair should earn the plain income: x" + reg.outputMultiplier(a));
        if (reg.complexOf(all.get(0)) != farm)
            problems.add("the Farm was re-made by the Pair's loss");
        // a reload: nothing but the buildings, and the same complexes come back
        MergedBuildings reloaded = new MergedBuildings();
        reloaded.update(all, 300);
        MergedComplex again = reloaded.complexOf(all.get(0));
        if (again == null || again.shape != MergedShape.FARM || again.members.size() != 4 || reloaded.complexes().size() != 1)
            problems.add("after a reload the same mills should form the same Farm and nothing else: " + reloaded.complexes());
        if (problems.isEmpty())
            helper.succeed();
        else
            helper.fail(String.join("; ", problems));
    }

    /** The switch is the only saved state: it survives a save as the building's data; the complexes are re-derived. */
    @GameTest(template = ARENA, timeoutTicks = 20)
    public static void the_merge_switch_survives_a_save(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        DataStorage storage = new DataStorage();
        storage.setData(MergedBuildings.MERGE_OFF, true);
        DataStorage back = DataStorage.read(storage.write(), helper.getLevel().getServer());
        if (!Boolean.TRUE.equals(back.getData(MergedBuildings.MERGE_OFF)))
            problems.add("merge off did not come back from a save: " + back.getData(MergedBuildings.MERGE_OFF));
        DataStorage fresh = DataStorage.read(new DataStorage().write(), helper.getLevel().getServer());
        if (Boolean.TRUE.equals(fresh.getData(MergedBuildings.MERGE_OFF)))
            problems.add("a building saved before merging existed should load with merging on");
        // a freshly made placement merges by default
        BuildingPlacement bp = member(helper, Buildings.WIND_GENERATOR_VILLAGERS, owner(helper), helper.absolutePos(new BlockPos(1, 1, 1)), 0, 0);
        if (!bp.mergeEnabled || !MergedBuildings.isCandidate(bp))
            problems.add("a new mill should merge by default");
        if (problems.isEmpty())
            helper.succeed();
        else
            helper.fail(String.join("; ", problems));
    }
}
