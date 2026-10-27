package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.building.Blueprints;
import com.solegendary.reignofnether.building.BuildSpacing;
import com.solegendary.reignofnether.building.Building;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.BuildingServerEvents;
import com.solegendary.reignofnether.building.BuildingUtils;
import com.solegendary.reignofnether.building.BuildingValidators;
import com.solegendary.reignofnether.building.buildings.defence.Defences;
import com.solegendary.reignofnether.resources.Resources;
import com.solegendary.reignofnether.resources.ResourcesServerEvents;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Rotation;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * BAR's buildspacing for Shift-drag lines and grids (tester request; building/BuildSpacing, wired in
 * BuildingLineClientEvents): Shift + wheel while placing sets the gap between copies, per building type, 0..8 blocks.
 * The client formation code is the same BuildSpacing math these tests lay out; here the copies are placed through the
 * real server placement path on a stone sky pad and their footprints measured.
 * <ul>
 *   <li>AC-1: raising / lowering changes the laid-out formation at once (one block per notch) and is clamped 0..8;
 *       the label and the controls row have their strings;</li>
 *   <li>AC-2: placed copies keep exactly the chosen gap, footprint to footprint, in a line and in a grid;</li>
 *   <li>AC-3: the choice is remembered per building type; an unchanged type keeps today's formation;</li>
 *   <li>AC-4: a slot the ground refuses is still invalid (and skipped) with spacing on; the others stay valid.</li>
 * </ul>
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class BuildSpacingGameTests {

    static final String ARENA = SkirmishGameTests.ARENA;
    static final String BATCH = "build_spacing";   // wide sky pads: one batch of their own
    static final int PAD_Y = 205, PAD_R = 26, PAD_H = 14;

    /** A stone pad high above the arena with an owner and a resource pool; undo() removes everything. */
    static final class Pad {
        final ServerLevel level;
        final String owner;
        final int cx, cz;
        final List<BuildingPlacement> placed = new ArrayList<>();
        final List<Resources> pools = new ArrayList<>();

        Pad(GameTestHelper helper, String tag) {
            level = helper.getLevel();
            owner = "gametest_spacing_" + tag + "_" + helper.absolutePos(BlockPos.ZERO).asLong();
            BlockPos base = helper.absolutePos(new BlockPos(8, 1, 8));
            cx = base.getX();
            cz = base.getZ();
            CombatEngineerGameTests.skyPad(level, cx, PAD_Y, cz, PAD_R, PAD_H, false);
            Resources pool = new Resources(owner, 0, 50000, 50000);
            ResourcesServerEvents.resourcesList.add(pool);
            pools.add(pool);
        }

        Vec3i dims(Building b) {
            return BuildingUtils.getBuildingSize(b.getRelativeBlockData(level));
        }

        /** The origin the client derives from a column centre (BuildingLineClientEvents: ground + getBuildingOriginPos). */
        BlockPos origin(Building b, int x, int z) {
            return BuildingUtils.getBuildingOriginPos(new BlockPos(x, PAD_Y, z), false, Rotation.NONE, dims(b));
        }

        String error(Building b, BlockPos origin) {
            // the exact check onMouseRelease runs before queueing each copy
            return BuildingValidators.getPlacementValidityError(level, b, origin, owner, Rotation.NONE, false, false, true);
        }

        BuildingPlacement place(Building b, BlockPos origin) {
            BuildingPlacement bp = BuildingServerEvents.placeBuilding(b, origin, Rotation.NONE, owner, new int[0],
                false, false, false, true);
            if (bp != null)
                placed.add(bp);
            return bp;
        }

        void undo() {
            for (BuildingPlacement bp : placed) {
                Blueprints.cancel(bp, null);
                if (BuildingServerEvents.getBuildings().remove(bp))
                    bp.forceChunk(false);
            }
            CombatEngineerGameTests.cleanUp(new ArrayList<Entity>(), pools, List.of(owner));
            CombatEngineerGameTests.skyPad(level, cx, PAD_Y, cz, PAD_R, PAD_H, true);
        }
    }

    static void finish(GameTestHelper helper, Pad pad, List<String> problems) {
        try {
            BuildSpacing.reset();
            if (problems.isEmpty())
                helper.succeed();
            else
                helper.fail(String.join("; ", problems));
        } finally {
            if (pad != null)
                pad.undo();
        }
    }

    static Building tower() {
        return Defences.VILLAGERS.raidTower();
    }

    // ------------------------------------------------------------------------------------------------ AC-1

    /** AC-1: each notch moves every copy of the line one block further per slot; clamped 0..8; strings present. */
    @GameTest(template = ARENA, timeoutTicks = 20, batch = BATCH)
    public static void ac1_wheel_notches_change_the_ghost_line_at_once_and_clamp(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        BuildSpacing.reset();
        String type = "spacing_test_mill";
        int w = 3;
        List<int[]> before = BuildSpacing.line(0, 0, 80, 0, w, w, BuildSpacing.get(type), 24);
        BuildSpacing.adjust(type, 1);
        BuildSpacing.adjust(type, 1);
        List<int[]> after = BuildSpacing.line(0, 0, 80, 0, w, w, BuildSpacing.get(type), 24);
        if (BuildSpacing.get(type) != 2)
            problems.add("two notches up should give a gap of 2, got " + BuildSpacing.get(type));
        for (int i = 0; i < Math.min(before.size(), after.size()); i++)
            if (after.get(i)[0] - before.get(i)[0] != 2 * (i + 1))
                problems.add("copy " + (i + 1) + " moved " + (after.get(i)[0] - before.get(i)[0]) + ", not " + 2 * (i + 1));
        BuildSpacing.adjust(type, -1);
        if (BuildSpacing.get(type) != 1)
            problems.add("a notch down should lower the gap to 1, got " + BuildSpacing.get(type));
        for (int i = 0; i < 30; i++)
            BuildSpacing.adjust(type, 1);
        if (BuildSpacing.get(type) != 8)
            problems.add("the gap should stop at 8, got " + BuildSpacing.get(type));
        for (int i = 0; i < 30; i++)
            BuildSpacing.adjust(type, -1);
        if (BuildSpacing.get(type) != 0)
            problems.add("the gap should stop at 0 (touching), got " + BuildSpacing.get(type));
        try (var in = BuildSpacingGameTests.class.getResourceAsStream("/assets/reignofnether/lang/en_us.json")) {
            String lang = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            for (String key : new String[] { "hud.reignofnether.build_spacing", "onboarding.reignofnether.controls.build_spacing" })
                if (!lang.contains("\"" + key + "\""))
                    problems.add(key + " is missing from en_us.json");
        } catch (Exception e) {
            problems.add("en_us.json unreadable: " + e);
        }
        finish(helper, null, problems);
    }

    // ------------------------------------------------------------------------------------------------ AC-2

    /** AC-2 (line): three towers laid with a gap of 3 stand exactly 3 blocks apart, footprint to footprint. */
    @GameTest(template = ARENA, timeoutTicks = 60, batch = BATCH)
    public static void ac2_a_spaced_line_places_copies_exactly_the_gap_apart(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        Pad pad = new Pad(helper, "line");
        try {
            Building b = tower();
            Vec3i d = pad.dims(b);
            int wx = d.getX() + 1, wz = d.getZ() + 1, gap = 3;
            int sx = pad.cx - PAD_R + wx, sz = pad.cz;
            List<int[]> centres = new ArrayList<>();
            centres.add(new int[] { sx, sz });
            centres.addAll(BuildSpacing.line(sx, sz, pad.cx + PAD_R - wx, sz, wx, wz, gap, 24));
            if (centres.size() < 3)
                problems.add("the pad fits only " + centres.size() + " towers of width " + wx);
            List<BuildingPlacement> row = new ArrayList<>();
            for (int[] c : centres) {
                BuildingPlacement bp = pad.place(b, pad.origin(b, c[0], c[1]));
                if (bp == null)
                    problems.add("copy at " + c[0] + "," + c[1] + " was refused: " + pad.error(b, pad.origin(b, c[0], c[1])));
                else
                    row.add(bp);
            }
            for (int i = 1; i < row.size(); i++) {
                BuildingPlacement a = row.get(i - 1), n = row.get(i);
                int g = BuildSpacing.gap(a.minCorner.getX(), a.maxCorner.getX(), n.minCorner.getX(), n.maxCorner.getX());
                if (g != gap)
                    problems.add("copies " + (i - 1) + "-" + i + " are " + g + " apart (" + a.minCorner + ".." + a.maxCorner
                        + " / " + n.minCorner + ".." + n.maxCorner + "), not " + gap);
            }
        } finally {
            finish(helper, pad, problems);
        }
    }

    /** AC-2 (grid): a 2x2 grid laid with a gap of 2 keeps 2 blocks between footprints along x and along z. */
    @GameTest(template = ARENA, timeoutTicks = 60, batch = BATCH)
    public static void ac2_a_spaced_grid_places_copies_exactly_the_gap_apart_both_ways(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        Pad pad = new Pad(helper, "grid");
        try {
            Building b = tower();
            Vec3i d = pad.dims(b);
            int gap = 2;
            int[] step = BuildSpacing.gridSteps(d.getX() + 1, d.getZ() + 1, gap);
            int sx = pad.cx - step[0] / 2, sz = pad.cz - step[1] / 2;
            BuildingPlacement[][] grid = new BuildingPlacement[2][2];
            for (int j = 0; j < 2; j++)
                for (int i = 0; i < 2; i++) {
                    BlockPos o = pad.origin(b, sx + i * step[0], sz + j * step[1]);
                    grid[i][j] = pad.place(b, o);
                    if (grid[i][j] == null)
                        problems.add("grid cell " + i + "," + j + " was refused: " + pad.error(b, o));
                }
            if (problems.isEmpty()) {
                for (int j = 0; j < 2; j++) {
                    BuildingPlacement a = grid[0][j], n = grid[1][j];
                    int g = BuildSpacing.gap(a.minCorner.getX(), a.maxCorner.getX(), n.minCorner.getX(), n.maxCorner.getX());
                    if (g != gap)
                        problems.add("row " + j + ": x gap " + g + ", not " + gap);
                }
                for (int i = 0; i < 2; i++) {
                    BuildingPlacement a = grid[i][0], n = grid[i][1];
                    int g = BuildSpacing.gap(a.minCorner.getZ(), a.maxCorner.getZ(), n.minCorner.getZ(), n.maxCorner.getZ());
                    if (g != gap)
                        problems.add("column " + i + ": z gap " + g + ", not " + gap);
                }
            }
        } finally {
            finish(helper, pad, problems);
        }
    }

    // ------------------------------------------------------------------------------------------------ AC-3

    /**
     * AC-3: the spacing is kept per building type, an untouched type is not "set" (so the line keeps its old step and
     * the grid steps one footprint - touching - exactly as before), and default spacing 0 lays touching copies.
     */
    @GameTest(template = ARENA, timeoutTicks = 60, batch = BATCH)
    public static void ac3_spacing_is_remembered_per_type_and_the_default_is_unchanged(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        Pad pad = new Pad(helper, "default");
        try {
            BuildSpacing.reset();
            BuildSpacing.adjust("spacing_test_a", 1);
            BuildSpacing.adjust("spacing_test_a", 1);
            BuildSpacing.adjust("spacing_test_b", 1);
            if (BuildSpacing.get("spacing_test_a") != 2 || BuildSpacing.get("spacing_test_b") != 1)
                problems.add("per-type memory broken: a=" + BuildSpacing.get("spacing_test_a") + " b=" + BuildSpacing.get("spacing_test_b"));
            Building b = tower();
            if (BuildSpacing.isSet(b.name))
                problems.add("an untouched type must not count as set");
            if (BuildSpacing.get(b.name) != 0)
                problems.add("default spacing should be 0 (touching), got " + BuildSpacing.get(b.name));
            // today's formations: grid step = dims + 1 (BuildingLineClientEvents' old fp + GAP), line step = max side + 1
            Vec3i d = pad.dims(b);
            int[] g = BuildSpacing.gridSteps(d.getX() + 1, d.getZ() + 1, BuildSpacing.get(b.name));
            if (g[0] != d.getX() + 1 || g[1] != d.getZ() + 1)
                problems.add("default grid step changed: " + g[0] + "," + g[1]);
            // and the default really is touching when placed: two copies side by side, gap 0
            int sx = pad.cx - g[0] / 2;
            BuildingPlacement a = pad.place(b, pad.origin(b, sx, pad.cz));
            BuildingPlacement n = pad.place(b, pad.origin(b, sx + g[0], pad.cz));
            if (a == null || n == null)
                problems.add("default-spaced copies refused: " + pad.error(b, pad.origin(b, sx + g[0], pad.cz)));
            else if (BuildSpacing.gap(a.minCorner.getX(), a.maxCorner.getX(), n.minCorner.getX(), n.maxCorner.getX()) != 0)
                problems.add("default grid copies are not touching: " + a.minCorner + ".." + a.maxCorner + " / " + n.minCorner);
        } finally {
            finish(helper, pad, problems);
        }
    }

    // ------------------------------------------------------------------------------------------------ AC-4

    /** AC-4: with a gap of 2, a slot already taken is still invalid (skipped on release); the free slots stay valid. */
    @GameTest(template = ARENA, timeoutTicks = 60, batch = BATCH)
    public static void ac4_an_invalid_slot_in_a_spaced_line_is_still_invalid(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        Pad pad = new Pad(helper, "valid");
        try {
            Building b = tower();
            Vec3i d = pad.dims(b);
            int wx = d.getX() + 1, wz = d.getZ() + 1;
            int sx = pad.cx - PAD_R + wx, sz = pad.cz;
            List<int[]> slots = BuildSpacing.line(sx, sz, pad.cx + PAD_R - wx, sz, wx, wz, 2, 24);
            if (slots.size() < 3) {
                problems.add("the pad fits only " + slots.size() + " spaced slots");
            } else {
                // something already stands on the second slot
                BlockPos taken = pad.origin(b, slots.get(1)[0], slots.get(1)[1]);
                if (pad.place(b, taken) == null)
                    problems.add("the blocker was refused: " + pad.error(b, taken));
                int valid = 0;
                for (int k = 0; k < slots.size(); k++) {
                    String err = pad.error(b, pad.origin(b, slots.get(k)[0], slots.get(k)[1]));
                    if (k == 1 && err == null)
                        problems.add("the occupied slot is shown valid");
                    if (k != 1 && err != null)
                        problems.add("free slot " + k + " is invalid: " + err);
                    if (err == null)
                        valid++;
                }
                if (valid != slots.size() - 1)
                    problems.add(valid + " of " + slots.size() + " slots valid, expected all but the taken one");
            }
        } finally {
            finish(helper, pad, problems);
        }
    }
}
