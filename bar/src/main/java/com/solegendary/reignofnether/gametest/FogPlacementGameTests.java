package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.building.Blueprints;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.BuildingServerEvents;
import com.solegendary.reignofnether.building.BuildingValidators;
import com.solegendary.reignofnether.building.Buildings;
import com.solegendary.reignofnether.building.FogPlacement;
import com.solegendary.reignofnether.unit.units.villagers.VillagerUnit;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * Placing a building over fogged / never-seen ground (tester, Oct 26: "the blueprint should follow your cursor, it
 * shouldn't be invisible"). The cursor ray missed ground the client was never sent and the ghost was drawn at the
 * ray's far end, underground; now it lands on the plane of the last real hit (FogPlacement). The client sends the
 * order when its only objection is "no blocks there"; the server keeps it in the worker's fog queue and checks the
 * real terrain when the worker reaches the site. The owner here has no player, so the server's fog says "unexplored"
 * everywhere (as WorkerQueueFogGameTests).
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class FogPlacementGameTests {

    static final String ARENA = SkirmishGameTests.ARENA;
    static final double DY = -Math.sqrt(0.5), DZ = Math.sqrt(0.5);

    /** A flat grass pad of 17x17 round (cx, y, cz), grass top at y. */
    static void flat(ServerLevel level, int cx, int y, int cz) {
        for (int dx = -8; dx <= 8; dx++)
            for (int dz = -8; dz <= 8; dz++) {
                level.setBlock(new BlockPos(cx + dx, y - 1, cz + dz), Blocks.DIRT.defaultBlockState(), 3);
                level.setBlock(new BlockPos(cx + dx, y, cz + dz), Blocks.GRASS_BLOCK.defaultBlockState(), 3);
            }
    }

    static void finish(GameTestHelper helper, List<String> problems) {
        if (problems.isEmpty())
            helper.succeed();
        else
            helper.fail(String.join("; ", problems));
    }

    /** AC-1: a missed ray (never-sent ground) still gives the ghost a spot under the cursor, at the last seen height. */
    @GameTest(template = ARENA, timeoutTicks = 20)
    public static void ac1_ghost_follows_the_cursor_over_unsent_ground(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        double lastHitY = 64;
        for (int i = 0; i < 5; i++) {
            double cx = 100 + i * 7, cz = -40 + i * 3;   // the cursor sweeping over fog
            double t = 200;
            double[] p = FogPlacement.rayToPlaneY(cx, lastHitY - DY * t, cz - DZ * t, 0, DY, DZ, lastHitY);
            if (p == null)
                problems.add("no ghost spot for cursor " + i);
            else if (Math.abs(p[0] - cx) > 1e-6 || Math.abs(p[2] - cz) > 1e-6 || p[1] != lastHitY)
                problems.add("cursor " + i + ": ghost at " + p[0] + "/" + p[1] + "/" + p[2] + ", expected " + cx + "/" + lastHitY + "/" + cz);
        }
        finish(helper, problems);
    }

    /**
     * AC-2: the order the client now sends for fogged ground reaches the server's real placement path and waits in the
     * worker's fog queue; when the worker gets there (the fog re-place: ignoreFog, no builder ids) a site that turns
     * out wrong (the guessed height is above the real ground) is refused the normal way and a right one is accepted.
     */
    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void ac2_a_fogged_order_is_queued_then_checked_against_real_terrain(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = "gametest_fogplace_" + helper.absolutePos(BlockPos.ZERO).asLong();
        BlockPos o = ArenaReady.pad(helper, 312);
        int cx = o.getX() + 8, cz = o.getZ() + 8;
        final int y = o.getY() + 1;
        flat(level, cx, y, cz);
        VillagerUnit worker = WorkerQueueFogGameTests.worker(level, owner, cx + 0.5, y + 1, cz - 7.5);
        if (worker == null) {
            helper.fail("could not create the worker");
            return;
        }
        var fog = worker.getExploreBuildLocationGoal().getFogQueuedBuildings();
        List<BuildingPlacement> made = new ArrayList<>();
        Runnable cleanup = () -> {
            for (BuildingPlacement bp : made)
                Blueprints.cancel(bp, null);
            worker.getExploreBuildLocationGoal().reset();
            worker.discard();
        };
        ArenaReady.whenTicking(helper, List.of(worker), 100, start -> {
            List<String> problems = new ArrayList<>();
            BlockPos origin = new BlockPos(cx - 1, y, cz - 1);
            if (BuildingServerEvents.placeBuilding(Buildings.WIND_GENERATOR_VILLAGERS, origin, Rotation.NONE, owner,
                    new int[] { worker.getId() }, false, false, false, false) != null)
                problems.add("a fogged order was placed at once instead of waiting for the worker");
            if (fog.size() != 1)
                problems.add("the fogged order should wait in the worker's fog queue, got " + fog.size());
            // the worker arrives: the site is checked against the real ground
            BlockPos tooHigh = origin.above(4);
            BuildingPlacement wrong = BuildingServerEvents.placeBuilding(Buildings.WIND_GENERATOR_VILLAGERS, tooHigh,
                    Rotation.NONE, owner, new int[0], false, false, false, true);
            if (wrong != null) {
                made.add(wrong);
                problems.add("a site in the air was accepted when the worker arrived");
            }
            String err = BuildingValidators.getPlacementValidityError(level, Buildings.WIND_GENERATOR_VILLAGERS, origin,
                    owner, Rotation.NONE, false, false, true);
            BuildingPlacement right = BuildingServerEvents.placeBuilding(Buildings.WIND_GENERATOR_VILLAGERS, origin,
                    Rotation.NONE, owner, new int[0], false, false, false, true);
            if (right == null)
                problems.add("the real site was refused when the worker arrived: " + err);
            else
                made.add(right);
            cleanup.run();
            finish(helper, problems);
        });
    }

    /** AC-3: on visible ground the client judges exactly as before - no error is ever left to the server. */
    @GameTest(template = ARENA, timeoutTicks = 20)
    public static void ac3_visible_ground_keeps_every_client_refusal(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        for (String key : new String[] { FogPlacement.NOT_LOADED, "building.reignofnether.ground_not_flat",
                "building.reignofnether.too_close", "building.reignofnether.outside_map", "building.reignofnether.unexplored" })
            if (FogPlacement.clientDefers(key, false))
                problems.add("visible ground lets '" + key + "' through");
        for (String key : new String[] { "building.reignofnether.ground_not_flat", "building.reignofnether.too_close",
                "building.reignofnether.outside_map" })
            if (FogPlacement.clientDefers(key, true))
                problems.add("fog lets '" + key + "' through (the client judged it with ground it has)");
        finish(helper, problems);
    }

    /**
     * AC-4: placing in fog reveals nothing. The ghost's spot uses only the ray and the last seen height (no block of
     * the hidden ground); the server answers a fogged order on bad hidden ground exactly as one on good ground (queued,
     * no reply), and neither touches the world or makes a blueprint anyone could be sent.
     */
    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void ac4_a_fogged_order_tells_nothing_about_the_hidden_ground(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = "gametest_fogleak_" + helper.absolutePos(BlockPos.ZERO).asLong();
        BlockPos o = ArenaReady.pad(helper, 323);
        int cx = o.getX() + 8, cz = o.getZ() + 8;
        final int y = o.getY() + 1;
        flat(level, cx, y, cz);
        VillagerUnit worker = WorkerQueueFogGameTests.worker(level, owner, cx + 0.5, y + 1, cz - 7.5);
        if (worker == null) {
            helper.fail("could not create the worker");
            return;
        }
        var fog = worker.getExploreBuildLocationGoal().getFogQueuedBuildings();
        Runnable cleanup = () -> {
            worker.getExploreBuildLocationGoal().reset();
            worker.discard();
        };
        ArenaReady.whenTicking(helper, List.of(worker), 100, start -> {
            List<String> problems = new ArrayList<>();
            // the ghost's spot: same answer whatever stands in the hidden column (the function reads no world)
            double[] a = FogPlacement.rayToPlaneY(cx, y + 141, cz - 141, 0, DY, DZ, y);
            if (a == null || Math.abs(a[0] - cx) > 1e-6 || Math.abs(a[2] - cz) > 1e-6)
                problems.add("the fallback spot is not under the cursor");
            int blueprints = Blueprints.all().size();
            BlockState before = level.getBlockState(new BlockPos(cx, y + 1, cz));
            BlockPos good = new BlockPos(cx - 1, y, cz - 1);
            BlockPos bad = good.above(4);   // in the air: the hidden ground would refuse it
            BuildingPlacement g = BuildingServerEvents.placeBuilding(Buildings.WIND_GENERATOR_VILLAGERS, good, Rotation.NONE,
                    owner, new int[] { worker.getId() }, true, false, false, false);
            BuildingPlacement b = BuildingServerEvents.placeBuilding(Buildings.WIND_GENERATOR_VILLAGERS, bad, Rotation.NONE,
                    owner, new int[] { worker.getId() }, true, false, false, false);
            if (g != null || b != null)
                problems.add("a fogged order got a placement back (good " + (g != null) + ", bad " + (b != null) + ")");
            if (fog.size() != 2)
                problems.add("good and bad hidden sites must be queued alike, fog queue " + fog.size());
            if (Blueprints.all().size() != blueprints)
                problems.add("a fogged order made a blueprint before anyone saw the site");
            if (level.getBlockState(new BlockPos(cx, y + 1, cz)) != before)
                problems.add("a fogged order changed the world");
            cleanup.run();
            finish(helper, problems);
        });
    }
}
