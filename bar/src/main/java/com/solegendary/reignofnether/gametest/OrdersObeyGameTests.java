package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.unit.EngagementStance;
import com.solegendary.reignofnether.unit.StuckNudge;
import com.solegendary.reignofnether.unit.UnitAction;
import com.solegendary.reignofnether.unit.UnitActionItem;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.command.CommandQueues;
import com.solegendary.reignofnether.unit.command.QueueMode;
import com.solegendary.reignofnether.unit.goals.MoveToTargetBlockGoal;
import com.solegendary.reignofnether.unit.interfaces.AttackerUnit;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.packets.UnitActionServerboundPacket;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * "Units sometimes don't listen to my commands" (lovish, Oct 21): the ways an order used to be lost.
 * <ul>
 *   <li>a re-click of the spot a stalled unit was already sent to was skipped as "redundant", so clicking again could
 *       not rescue it (UnitActionItem.isRedundantMove; now only while the unit is really getting there);</li>
 *   <li>a move to a target in an unloaded chunk got no path, every retry, forever - the unit stood, counted busy
 *       (RtsPathfinder + MoveToTargetBlockGoal; now it walks as far as it can and then gives up, idle);</li>
 *   <li>a Stop given right after a 20+ unit vanilla-mode formation move was undone when the queued moves dispatched
 *       (UnitServerEvents formation queue; a new order now drops the unit's queued move);</li>
 *   <li>a player-ordered attack target was dropped by StuckNudge's give-up (now only auto-picked targets are);</li>
 *   <li>a second big formation drag right after a first hit the unit_action rate limit and was dropped silently.</li>
 * </ul>
 * Every unit here is on Hold fire, so none of them picks a fight with a neighbouring arena's units.
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class OrdersObeyGameTests {

    static final String ARENA = SkirmishGameTests.ARENA;
    static final String BATCH = "orders_obey";
    // its own batch: it switches the server-wide rtsPathfinding flag on for its run (batches run one at a time)
    static final String BATCH_RTS = "orders_obey_rts";
    // the shifted-along-the-wall test switches the flag too: in the same batch, whichever test ended first restored the
    // flag to off under the other one, whose path result then waited forever (stress run 40587689274)
    static final String BATCH_RTS_SHIFTED = "orders_obey_rts_shifted";
    static final String BATCH_RTS_RESCUED = "orders_obey_rts_rescued";
    static final String BATCH_RTS_RESCUED_FREE = "orders_obey_rts_rescued_free";
    static final String BATCH_RTS_RESCUED_CADENCE = "orders_obey_rts_rescued_cadence";
    static final String BATCH_VANILLA_RESCUED_CADENCE = "orders_obey_vanilla_rescued_cadence";
    static final String BATCH_VANILLA_RESCUED_FREE = "orders_obey_vanilla_rescued_free";

    static String owner(GameTestHelper helper, String tag) {
        return "gametest_obey_" + tag + "_" + helper.absolutePos(BlockPos.ZERO).asLong();
    }

    static Mob vindicator(ServerLevel level, String owner, double x, double y, double z, List<Entity> made) {
        Mob m = TerrainGameTests.spawn(level, EntityRegistrar.VINDICATOR_UNIT.get(), owner, x, y, z, made);
        if (m != null) {
            EngagementStance.set(m, EngagementStance.HOLD_FIRE);
            m.setInvulnerable(true);
        }
        return m;
    }

    static double dist(Mob m, BlockPos bp) {
        double dx = m.getX() - (bp.getX() + 0.5), dz = m.getZ() - (bp.getZ() + 0.5);
        return Math.sqrt(dx * dx + dz * dz);
    }

    static void move(ServerLevel level, String owner, Mob m, BlockPos to) {
        CommandQueues.issue(level, owner, UnitAction.MOVE, -1, new int[] { m.getId() }, to, BlockPos.ZERO, QueueMode.REPLACE);
    }

    // ------------------------------------------------------------------------------------------ (a) re-click rescues

    /**
     * Two vindicators sent across the arena. A walks: a re-click of its spot 30 ticks in is redundant and skipped
     * (spam-click protection kept - no new path request). B is held in place (movement speed 0) so it makes no
     * progress; 60 ticks in, a re-click of the very same spot is NOT redundant: it re-paths B at once (a new request
     * that tick) and, its speed back, B arrives. Before the fix that re-click was skipped (B's target was the same
     * block) and B's request time did not change.
     */
    @GameTest(template = ARENA, batch = BATCH, timeoutTicks = 400)
    public static void a_re_click_re_paths_a_unit_that_is_not_progressing(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        SkirmishGameTests.openGround(helper, 0, 0, 15, 15);
        String mine = owner(helper, "reclick");
        BlockPos o = helper.absolutePos(new BlockPos(0, 2, 0));
        List<Entity> made = new ArrayList<>();
        Mob a = vindicator(level, mine, o.getX() + 0.5, o.getY(), o.getZ() + 1.5, made);
        Mob b = vindicator(level, mine, o.getX() + 0.5, o.getY(), o.getZ() + 12.5, made);
        if (a == null || b == null) {
            TerrainGameTests.cleanUp(made);
            helper.fail("could not create the vindicators");
            return;
        }
        BlockPos toA = helper.absolutePos(new BlockPos(15, 2, 6)), toB = helper.absolutePos(new BlockPos(14, 2, 12));
        double speedB = b.getAttribute(Attributes.MOVEMENT_SPEED).getBaseValue();
        List<String> problems = new ArrayList<>();
        int[] ordered = { -1 };
        boolean[] spamChecked = { false }, reclicked = { false }, done = { false };

        ArenaReady.whenTicking(helper, made, 150, tick -> {
            ordered[0] = tick;
            b.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.0);
            move(level, mine, a, toA);
            move(level, mine, b, toB);
        });
        helper.onEachTick(() -> {
            if (ordered[0] < 0 || done[0])
                return;
            long t = helper.getTick() - ordered[0];
            long now = level.getGameTime();
            MoveToTargetBlockGoal goalA = ((Unit) a).getMoveGoal(), goalB = ((Unit) b).getMoveGoal();
            if (!spamChecked[0] && t >= 30) {
                spamChecked[0] = true;
                if (a.getNavigation().isDone() || dist(a, toA) < 2)
                    problems.add("A was not walking 30 ticks after its order (" + TerrainGameTests.f(dist(a, toA))
                        + " from its spot): nothing to check the spam-click rule on");
                else {
                    if (!UnitActionItem.isRedundantMove((Unit) a, toA))
                        problems.add("a re-click of a walking unit's own spot was not redundant (progressing "
                            + goalA.isMakingProgress() + ")");
                    long before = goalA.getLastRequestTime();
                    move(level, mine, a, toA);
                    if (goalA.getLastRequestTime() != before)
                        problems.add("a re-click of a walking unit's own spot re-pathed it (spam-click protection lost)");
                }
            }
            if (!reclicked[0] && t >= 60 && goalB.getLastRequestTime() != now) {
                reclicked[0] = true;
                if (UnitActionItem.isRedundantMove((Unit) b, toB))
                    problems.add("a re-click of a stalled unit's spot was still redundant (move target "
                        + goalB.getMoveTarget() + ", progressing " + goalB.isMakingProgress() + ")");
                b.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(speedB);
                move(level, mine, b, toB);
                if (goalB.getLastRequestTime() != now)
                    problems.add("the re-click did not re-path the stalled unit (last request at "
                        + goalB.getLastRequestTime() + ", now " + now + ", move target " + goalB.getMoveTarget() + ")");
            }
            if (!reclicked[0])
                return;
            // "arrived" as the game settles a unit: within PathfinderConfig.ARRIVAL_SETTLE (1.5 blocks of the target cell)
            // plus the half block it may stand off the cell's centre. A fixed 1.6 failed on a unit that had settled 1.93
            // from the spot and stayed there (CI stress, Oct 22: "A 1.93 from its spot, B 0.01")
            double settle = Math.sqrt(com.solegendary.reignofnether.unit.pathfinding.PathfinderConfig.ARRIVAL_SETTLE_SQ) + 0.75;
            boolean arrived = dist(a, toA) < settle && dist(b, toB) < settle;
            if (!problems.isEmpty() || arrived || t > 330) {
                done[0] = true;
                try {
                    if (problems.isEmpty() && !arrived)
                        problems.add("not both arrived " + t + " ticks after the order: A " + TerrainGameTests.f(dist(a, toA))
                            + " from its spot (A move target " + goalA.getMoveTarget() + ", nav done " + a.getNavigation().isDone()
                            + "), B " + TerrainGameTests.f(dist(b, toB)) + " (B move target "
                            + goalB.getMoveTarget() + ", nav done " + b.getNavigation().isDone() + ")");
                    TerrainGameTests.finish(helper, problems);
                } finally {
                    TerrainGameTests.cleanUp(made);
                }
            }
        });
    }

    // --------------------------------------------------------------------------------- (b) a move into unloaded land

    /**
     * RTS pathfinding on (as on a small world-border map). A vindicator in a closed stone pen is sent 4000 blocks
     * east, into chunks no one has loaded. It walks to the pen's east wall (as far as it can toward the target) and,
     * within 30 s, gives the order up (idle) - or at the least is still walking; never standing with the order.
     * Before the fix every request got no path at all, so it stood where it was and retried every 20 ticks forever,
     * counted busy.
     */
    @GameTest(template = ARENA, batch = BATCH_RTS, timeoutTicks = 800)
    public static void a_move_into_unloaded_chunks_walks_toward_it_never_stands_busy(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        boolean wasRts = UnitServerEvents.rtsPathfinding;
        UnitServerEvents.rtsPathfinding = true;
        // on the ground, not a sky pad: a pad near the build limit sits at the edge of the pathfinder's vertical band
        SkirmishGameTests.openGround(helper, 0, 0, 15, 15);
        // a closed stone pen (x 0..14, z 0..15): the unit must not walk out into fresh world - generating new
        // chunks from a test crashed a CI server (ocean-ruin feature placement, run 39708206584)
        for (int x = 0; x <= 14; x++)
            for (int z = 0; z <= 15; z++)
                if (x == 0 || x == 14 || z == 0 || z == 15)
                    for (int y = 2; y <= 6; y++)
                        helper.setBlock(new BlockPos(x, y, z), net.minecraft.world.level.block.Blocks.STONE);
        BlockPos pad = helper.absolutePos(new BlockPos(0, 2, 0));
        String mine = owner(helper, "unloaded");
        List<Entity> made = new ArrayList<>();
        Mob m = vindicator(level, mine, pad.getX() + 2.5, pad.getY(), pad.getZ() + 8.5, made);
        if (m == null) {
            UnitServerEvents.rtsPathfinding = wasRts;
            TerrainGameTests.cleanUp(made);
            helper.fail("could not create the vindicator");
            return;
        }
        BlockPos far = new BlockPos(pad.getX() + 4000, pad.getY(), pad.getZ() + 8);
        double startX = m.getX();
        double[] xAt500 = { Double.NaN };
        int[] ordered = { -1 };
        // the pool's state a few times after the order: where a request that never comes back is held
        StringBuilder trace = new StringBuilder();
        StringBuilder events = new StringBuilder();
        long[] lastSeen = { Long.MIN_VALUE, 0 };
        BlockPos[] finalSeen = { null };
        boolean[] done = { false };
        ArenaReady.whenTicking(helper, made, 150, tick -> {
            if (level.getChunkSource().getChunkNow(far.getX() >> 4, far.getZ() >> 4) != null) {
                done[0] = true;
                UnitServerEvents.rtsPathfinding = wasRts;
                TerrainGameTests.cleanUp(made);
                helper.fail("the far target's chunk is loaded: this test proves nothing");
                return;
            }
            ordered[0] = tick;
            move(level, mine, m, far);
        });
        helper.onEachTick(() -> {
            if (ordered[0] < 0 || done[0])
                return;
            long t = helper.getTick() - ordered[0];
            MoveToTargetBlockGoal goal = ((Unit) m).getMoveGoal();
            boolean idle = goal.getMoveTarget() == null;
            if (t == 500)
                xAt500[0] = m.getX();
            if (t == 1 || t == 5 || t == 40 || t == 300)
                trace.append(" [t ").append(t).append(": pending ").append(goal.isPathPending()).append(", ")
                    .append(com.solegendary.reignofnether.unit.pathfinding.PathfinderWorkerPool.debugState()).append("]");
            // every request and every path that came back, with where the unit stood: a stuck count that never
            // reaches its give-up shows as final nodes that change, or requests spaced too short
            BlockPos fin = goal.getFinalNodePos();
            if (goal.getLastRequestTime() != lastSeen[0] || goal.isPathPending() != (lastSeen[1] == 1)
                    || !java.util.Objects.equals(fin, finalSeen[0])) {
                lastSeen[0] = goal.getLastRequestTime();
                lastSeen[1] = goal.isPathPending() ? 1 : 0;
                finalSeen[0] = fin;
                if (events.length() < 3000)
                    events.append(" t").append(t).append(goal.isPathPending() ? " req" : " got").append(" at ")
                        .append(TerrainGameTests.f(m.getX() - startX)).append(",").append(TerrainGameTests.f(m.getZ()))
                        .append(" end ").append(fin == null ? "none" : fin.getX() + "," + fin.getY() + "," + fin.getZ())
                        .append(goal.isInBackoff() ? " backoff" : "").append(";");
            }
            if (!idle && t < 600)
                return;
            done[0] = true;
            try {
                double moved = m.getX() - startX;
                String state = "after " + t + " ticks: move target " + goal.getMoveTarget() + ", moved "
                    + TerrainGameTests.f(moved) + " east, nav done " + m.getNavigation().isDone() + ", last request at "
                    + goal.getLastRequestTime() + " (now " + level.getGameTime() + "), path pending " + goal.isPathPending()
                    + ", goal running " + goal.isGoalRunning() + ", pool "
                    + (com.solegendary.reignofnether.unit.pathfinding.PathfinderWorkerPool.isInitialised()
                        ? "queue depth " + com.solegendary.reignofnether.unit.pathfinding.PathfinderWorkerPool.queueDepth() : "off")
                    + ", fallback point " + com.solegendary.reignofnether.unit.pathfinding.RtsPathfinder.lastLoadedToward(level, m.blockPosition(), far)
                    + ", now " + com.solegendary.reignofnether.unit.pathfinding.PathfinderWorkerPool.debugState() + ";" + trace
                    + "; events:" + events;
                ReignOfNether.LOGGER.info("[Orders] a move into unloaded chunks: {}", state);
                List<String> problems = new ArrayList<>();
                if (!idle && !(m.getX() - xAt500[0] > 1.0))
                    problems.add("the unit still had its order after 30 s but stood still for its last 100 ticks "
                        + "(busy forever?): " + state + ", x 100 ticks earlier " + TerrainGameTests.f(xAt500[0] - startX));
                if (moved < 5)
                    problems.add("the unit did not walk toward the target: " + state);
                TerrainGameTests.finish(helper, problems);
            } finally {
                UnitServerEvents.rtsPathfinding = wasRts;
                TerrainGameTests.cleanUp(made);
            }
        });
    }

    /**
     * The same move into unloaded land, but each time the unit has come to rest at the pen's east wall it is shifted one
     * block north or south (alternately), as separation and pushing shifted it by chance in CI. The path into unloaded
     * land aims at the last loaded ground straight ahead of the unit, so each shift moved the path's end one block, and
     * "a different end node" counted as progress: the stuck count never reached its give-up and the unit stood with
     * its order forever (a_move_into_unloaded_chunks..., CI runs 40572488789, 40577575656). A sideways shift of a
     * block is no progress toward a target 4000 blocks east: the unit gives the order up within 30 s.
     */
    @GameTest(template = ARENA, batch = BATCH_RTS_SHIFTED, timeoutTicks = 800)
    public static void a_unit_shifted_along_the_wall_still_gives_up_a_move_into_unloaded_chunks(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        boolean wasRts = UnitServerEvents.rtsPathfinding;
        UnitServerEvents.rtsPathfinding = true;
        SkirmishGameTests.openGround(helper, 0, 0, 15, 15);
        // the same closed stone pen as a_move_into_unloaded_chunks... (x 0..14, z 0..15)
        for (int x = 0; x <= 14; x++)
            for (int z = 0; z <= 15; z++)
                if (x == 0 || x == 14 || z == 0 || z == 15)
                    for (int y = 2; y <= 6; y++)
                        helper.setBlock(new BlockPos(x, y, z), net.minecraft.world.level.block.Blocks.STONE);
        BlockPos pad = helper.absolutePos(new BlockPos(0, 2, 0));
        String mine = owner(helper, "shifted");
        List<Entity> made = new ArrayList<>();
        Mob m = vindicator(level, mine, pad.getX() + 2.5, pad.getY(), pad.getZ() + 8.5, made);
        if (m == null) {
            UnitServerEvents.rtsPathfinding = wasRts;
            TerrainGameTests.cleanUp(made);
            helper.fail("could not create the vindicator");
            return;
        }
        BlockPos far = new BlockPos(pad.getX() + 4000, pad.getY(), pad.getZ() + 8);
        double startX = m.getX();
        int[] ordered = { -1 };
        long[] shiftedFor = { Long.MIN_VALUE };
        int[] shifts = { 0 };
        StringBuilder events = new StringBuilder();
        StringBuilder trace = new StringBuilder();
        long[] traced = { Long.MIN_VALUE, -1, -1, Long.MIN_VALUE };
        BlockPos[] tracedEnd = { null };
        boolean[] done = { false };
        ArenaReady.whenTicking(helper, made, 150, tick -> {
            if (level.getChunkSource().getChunkNow(far.getX() >> 4, far.getZ() >> 4) != null) {
                done[0] = true;
                UnitServerEvents.rtsPathfinding = wasRts;
                TerrainGameTests.cleanUp(made);
                helper.fail("the far target's chunk is loaded: this test proves nothing");
                return;
            }
            ordered[0] = tick;
            move(level, mine, m, far);
        });
        helper.onEachTick(() -> {
            if (ordered[0] < 0 || done[0])
                return;
            long t = helper.getTick() - ordered[0];
            MoveToTargetBlockGoal goal = ((Unit) m).getMoveGoal();
            boolean idle = goal.getMoveTarget() == null;
            // every request, path result, navigation start/stop and nudge, with where the unit stood: a give-up that never
            // comes shows which repath read as progress (CI stress on #294, 40628389476: 2 shifts, then it stood
            // 3 blocks back from the wall with its order)
            BlockPos fin = goal.getFinalNodePos();
            boolean navDone = m.getNavigation().isDone();
            long nudge = StuckNudge.lastNudge(m);
            if (goal.getLastRequestTime() != traced[0] || goal.isPathPending() != (traced[1] == 1)
                    || navDone != (traced[2] == 1) || nudge != traced[3] || !java.util.Objects.equals(fin, tracedEnd[0]) || t % 40 == 0) {
                traced[0] = goal.getLastRequestTime();
                traced[1] = goal.isPathPending() ? 1 : 0;
                traced[2] = navDone ? 1 : 0;
                traced[3] = nudge;
                tracedEnd[0] = fin;
                if (trace.length() < 12000) {
                    net.minecraft.world.level.pathfinder.Path p = m.getNavigation().getPath();
                    trace.append(" t").append(t).append(goal.isPathPending() ? " req" : navDone ? " done" : " nav")
                        .append(" at ").append(TerrainGameTests.f(m.getX() - startX)).append(",")
                        .append(TerrainGameTests.f(m.getZ())).append(",y").append(TerrainGameTests.f(m.getY() - pad.getY()))
                        .append(m.onGround() ? "" : " air").append(m.isPassenger() ? " riding " + m.getVehicle() : "")
                        .append(" on ").append(level.getBlockState(BlockPos.containing(m.getX(), m.getY() - 0.2, m.getZ()))
                            .getBlock().getDescriptionId().replace("block.minecraft.", ""))
                        .append(" wall ").append(eastWall(helper))
                        .append(" end ").append(fin == null ? "none" : (fin.getX() - pad.getX()) + "," + fin.getY() + "," + fin.getZ())
                        .append(p == null ? "" : " n" + p.getNextNodeIndex() + "/" + p.getNodeCount())
                        .append(" stuck ").append(stuckRepaths(goal)).append(goal.isInBackoff() ? " backoff" : "")
                        .append(" req@").append(goal.getLastRequestTime() - level.getGameTime());
                    if (nudge >= 0 && level.getGameTime() - nudge < 2)
                        trace.append(" NUDGE");
                    if (goal.isPathPending()) {
                        BlockPos edge = com.solegendary.reignofnether.unit.pathfinding.RtsPathfinder.lastLoadedToward(level, m.blockPosition(), far);
                        trace.append(" edge ").append(edge == null ? "none" : (edge.getX() - pad.getX()) + "," + edge.getY() + "," + edge.getZ());
                    }
                    trace.append(" rts ").append(UnitServerEvents.rtsPathfinding).append(";");
                }
            }
            // at rest at the wall after this request's path came in: shift it a block along the wall, once per request
            if (!idle && m.getX() - startX >= 8 && !goal.isPathPending() && m.getNavigation().isDone()
                    && goal.getLastRequestTime() != shiftedFor[0]) {
                shiftedFor[0] = goal.getLastRequestTime();
                // south first: the last loaded ground the path aims at is floor(z + a hair toward the target's z), so a unit
                // shifted north of the target's row (z 9 -> floor 8.98 = 8) keeps the same path end; south (7) moves it
                double dz = shifts[0]++ % 2 == 0 ? -1 : 1;
                m.teleportTo(m.getX(), m.getY(), m.getZ() + dz);
                if (events.length() < 2000)
                    events.append(" t").append(t).append(" shifted to z ").append(TerrainGameTests.f(m.getZ()))
                        .append(" (path end ").append(goal.getFinalNodePos()).append(");");
            }
            if (!idle && t < 600)
                return;
            done[0] = true;
            try {
                List<String> problems = new ArrayList<>();
                if (!idle)
                    problems.add("the unit still had its order after " + t + " ticks (" + shifts[0] + " shifts along the"
                        + " wall, moved " + TerrainGameTests.f(m.getX() - startX) + " east, last request at "
                        + goal.getLastRequestTime() + ", now " + level.getGameTime() + "):" + events + " trace:" + trace);
                else if (m.getX() - startX < 5)
                    problems.add("the unit gave up without walking toward the target (moved "
                        + TerrainGameTests.f(m.getX() - startX) + " east)");
                // gave up, but only proves something if it was shifted along the wall in between (a unit that never
                // came to rest there passes on any code)
                else if (shifts[0] < 2)
                    problems.add("the unit gave up after only " + shifts[0] + " shifts along the wall: the test did not"
                        + " exercise the drift:" + events + " trace:" + trace);
                TerrainGameTests.finish(helper, problems);
            } finally {
                UnitServerEvents.rtsPathfinding = wasRts;
                TerrainGameTests.cleanUp(made);
            }
        });
    }

    /**
     * The same move into unloaded land, but each time the unit has come to rest at the pen's east wall UnitUnstick
     * rescues it where it stands (teleported to the centre of its own cell, its move goal repathed), as UnitUnstick did
     * in CI: the rescue's forceRepath wiped the move goal's stuck count, so the count never reached its give-up and the
     * unit stood with its order forever (a_unit_shifted_along_the_wall..., stress 40675281944: the count went back to 0
     * at three rescues, the unit snapped to x .50, z .50). A rescue that moves the unit nowhere is no progress toward a
     * target 4000 blocks east: the unit gives the order up within 30 s. Rescued on every other request (the goal's own
     * repath in between), as UnitUnstick takes a while to see a unit trapped again; a rescue's own repath never gives
     * the order up by itself (a_unit_rescued_with_its_count_near_the_give_up_keeps_an_order_it_can_walk).
     */
    @GameTest(template = ARENA, batch = BATCH_RTS_RESCUED, timeoutTicks = 800)
    public static void a_unit_rescued_in_place_at_the_wall_still_gives_up_a_move_into_unloaded_chunks(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        boolean wasRts = UnitServerEvents.rtsPathfinding;
        UnitServerEvents.rtsPathfinding = true;
        SkirmishGameTests.openGround(helper, 0, 0, 15, 15);
        // the same closed stone pen as a_move_into_unloaded_chunks... (x 0..14, z 0..15)
        for (int x = 0; x <= 14; x++)
            for (int z = 0; z <= 15; z++)
                if (x == 0 || x == 14 || z == 0 || z == 15)
                    for (int y = 2; y <= 6; y++)
                        helper.setBlock(new BlockPos(x, y, z), net.minecraft.world.level.block.Blocks.STONE);
        BlockPos pad = helper.absolutePos(new BlockPos(0, 2, 0));
        String mine = owner(helper, "rescued");
        List<Entity> made = new ArrayList<>();
        Mob m = vindicator(level, mine, pad.getX() + 2.5, pad.getY(), pad.getZ() + 8.5, made);
        if (m == null) {
            UnitServerEvents.rtsPathfinding = wasRts;
            TerrainGameTests.cleanUp(made);
            helper.fail("could not create the vindicator");
            return;
        }
        BlockPos far = new BlockPos(pad.getX() + 4000, pad.getY(), pad.getZ() + 8);
        double startX = m.getX();
        int[] ordered = { -1 };
        long[] rescuedFor = { Long.MIN_VALUE };
        int[] rescues = { 0 };
        StringBuilder events = new StringBuilder();
        boolean[] done = { false };
        ArenaReady.whenTicking(helper, made, 150, tick -> {
            if (level.getChunkSource().getChunkNow(far.getX() >> 4, far.getZ() >> 4) != null) {
                done[0] = true;
                UnitServerEvents.rtsPathfinding = wasRts;
                TerrainGameTests.cleanUp(made);
                helper.fail("the far target's chunk is loaded: this test proves nothing");
                return;
            }
            ordered[0] = tick;
            move(level, mine, m, far);
        });
        helper.onEachTick(() -> {
            if (ordered[0] < 0 || done[0])
                return;
            long t = helper.getTick() - ordered[0];
            MoveToTargetBlockGoal goal = ((Unit) m).getMoveGoal();
            boolean idle = goal.getMoveTarget() == null;
            // at rest at the wall after the goal's own repath came in: rescue it in place (not again on the rescue's
            // own request: the next rescue waits for the goal's next repath)
            if (!idle && m.getX() - startX >= 8 && !goal.isPathPending() && m.getNavigation().isDone()
                    && goal.getLastRequestTime() != rescuedFor[0]) {
                rescues[0]++;
                com.solegendary.reignofnether.unit.UnitUnstick.rescue(m, m.blockPosition());
                rescuedFor[0] = goal.getLastRequestTime();
                if (events.length() < 2000)
                    events.append(" t").append(t).append(" rescued at ").append(TerrainGameTests.f(m.getX() - startX))
                        .append(",").append(TerrainGameTests.f(m.getZ())).append(" stuck ").append(stuckRepaths(goal)).append(";");
            }
            if (!idle && t < 600)
                return;
            done[0] = true;
            try {
                List<String> problems = new ArrayList<>();
                if (!idle)
                    problems.add("the unit still had its order after " + t + " ticks (" + rescues[0] + " rescues at the"
                        + " wall, moved " + TerrainGameTests.f(m.getX() - startX) + " east):" + events);
                else if (m.getX() - startX < 5)
                    problems.add("the unit gave up without walking toward the target (moved "
                        + TerrainGameTests.f(m.getX() - startX) + " east)");
                // gave up, but only proves something if it was rescued at the wall in between
                else if (rescues[0] < 2)
                    problems.add("the unit gave up after only " + rescues[0] + " rescues at the wall: the test did not"
                        + " exercise the reset:" + events);
                TerrainGameTests.finish(helper, problems);
            } finally {
                UnitServerEvents.rtsPathfinding = wasRts;
                TerrainGameTests.cleanUp(made);
            }
        });
    }

    // how many of the pen's east wall blocks (x 14, y 2..6, z 0..15: 80) are still stone (diagnostics only)
    static int eastWall(GameTestHelper helper) {
        int n = 0;
        for (int z = 0; z <= 15; z++)
            for (int y = 2; y <= 6; y++)
                if (helper.getBlockState(new BlockPos(14, y, z)).is(net.minecraft.world.level.block.Blocks.STONE))
                    n++;
        return n;
    }

    /**
     * The same move into unloaded land, but UnitUnstick rescues the unit in place at the pen's wall on its own cadence:
     * every STRIKES x CHECK_INTERVAL ticks (80, the soonest it can flag a unit that has not moved again), whatever the
     * move goal is doing. A forced repath that reads stuck backs the goal off to 40 checks, so its own next repath
     * comes after the next rescue: a rescue clamped one short of the give-up every time held a truly stuck unit at
     * count 2 with its order forever (review of #341, round 2). It must still give the order up. In vanilla (where
     * the rescue's repath is judged inside rescue()) one rescue must also add at most one stuck repath: rescue() used
     * to repath the plain move goal twice, from the goal selector and from getMoveGoal.
     */
    @GameTest(template = ARENA, batch = BATCH_RTS_RESCUED_CADENCE, timeoutTicks = 800)
    public static void a_unit_rescued_at_the_wall_on_unstick_cadence_still_gives_up_a_move_into_unloaded_chunks(GameTestHelper helper) {
        rescuedOnCadence(helper, true);
    }

    @GameTest(template = ARENA, batch = BATCH_VANILLA_RESCUED_CADENCE, timeoutTicks = 800)
    public static void a_unit_rescued_at_the_wall_on_unstick_cadence_still_gives_up_a_move_into_unloaded_chunks_vanilla(GameTestHelper helper) {
        rescuedOnCadence(helper, false);
    }

    private static void rescuedOnCadence(GameTestHelper helper, boolean rts) {
        ServerLevel level = helper.getLevel();
        boolean wasRts = UnitServerEvents.rtsPathfinding;
        UnitServerEvents.rtsPathfinding = rts;
        SkirmishGameTests.openGround(helper, 0, 0, 15, 15);
        // the same closed stone pen as a_move_into_unloaded_chunks... (x 0..14, z 0..15)
        for (int x = 0; x <= 14; x++)
            for (int z = 0; z <= 15; z++)
                if (x == 0 || x == 14 || z == 0 || z == 15)
                    for (int y = 2; y <= 6; y++)
                        helper.setBlock(new BlockPos(x, y, z), net.minecraft.world.level.block.Blocks.STONE);
        BlockPos pad = helper.absolutePos(new BlockPos(0, 2, 0));
        String mine = owner(helper, rts ? "cadence" : "cadencev");
        List<Entity> made = new ArrayList<>();
        Mob m = vindicator(level, mine, pad.getX() + 2.5, pad.getY(), pad.getZ() + 8.5, made);
        if (m == null) {
            UnitServerEvents.rtsPathfinding = wasRts;
            TerrainGameTests.cleanUp(made);
            helper.fail("could not create the vindicator");
            return;
        }
        BlockPos far = new BlockPos(pad.getX() + 4000, pad.getY(), pad.getZ() + 8);
        int cadence = com.solegendary.reignofnether.unit.UnitUnstick.STRIKES * com.solegendary.reignofnether.unit.UnitUnstick.CHECK_INTERVAL;
        double startX = m.getX();
        int[] ordered = { -1 };
        long[] lastRescue = { Long.MIN_VALUE };
        int[] rescues = { 0 }, worstAdded = { 0 };
        StringBuilder events = new StringBuilder();
        boolean[] done = { false };
        ArenaReady.whenTicking(helper, made, 150, tick -> {
            if (level.getChunkSource().getChunkNow(far.getX() >> 4, far.getZ() >> 4) != null) {
                done[0] = true;
                UnitServerEvents.rtsPathfinding = wasRts;
                TerrainGameTests.cleanUp(made);
                helper.fail("the far target's chunk is loaded: this test proves nothing");
                return;
            }
            ordered[0] = tick;
            move(level, mine, m, far);
        });
        helper.onEachTick(() -> {
            if (ordered[0] < 0 || done[0])
                return;
            long t = helper.getTick() - ordered[0];
            MoveToTargetBlockGoal goal = ((Unit) m).getMoveGoal();
            boolean idle = goal.getMoveTarget() == null;
            // first rescue once it has come to rest at the wall, then every cadence ticks whatever the goal is doing
            boolean due = lastRescue[0] == Long.MIN_VALUE
                ? m.getX() - startX >= 8 && !goal.isPathPending() && m.getNavigation().isDone()
                : t - lastRescue[0] >= cadence;
            if (!idle && due) {
                lastRescue[0] = t;
                rescues[0]++;
                int before = stuckRepaths(goal);
                com.solegendary.reignofnether.unit.UnitUnstick.rescue(m, m.blockPosition());
                int after = stuckRepaths(goal);
                if (goal.getMoveTarget() != null)
                    worstAdded[0] = Math.max(worstAdded[0], after - before);
                if (events.length() < 2000)
                    events.append(" t").append(t).append(" rescued at ").append(TerrainGameTests.f(m.getX() - startX))
                        .append(",").append(TerrainGameTests.f(m.getZ())).append(" stuck ").append(before).append(" -> ")
                        .append(goal.getMoveTarget() == null ? "gave up" : String.valueOf(after)).append(";");
            }
            if (!idle && t < 600)
                return;
            done[0] = true;
            try {
                List<String> problems = new ArrayList<>();
                if (!idle)
                    problems.add("the unit still had its order after " + t + " ticks (" + rescues[0] + " rescues every "
                        + cadence + " ticks at the wall, moved " + TerrainGameTests.f(m.getX() - startX) + " east):" + events);
                else if (m.getX() - startX < 5)
                    problems.add("the unit gave up without walking toward the target (moved "
                        + TerrainGameTests.f(m.getX() - startX) + " east)");
                else if (rescues[0] < 2)
                    problems.add("the unit gave up after only " + rescues[0] + " rescues at the wall: the test did not"
                        + " exercise the cadence:" + events);
                if (!rts && worstAdded[0] > 1)
                    problems.add("one rescue counted " + worstAdded[0] + " stuck repaths (rescue() repathed the move goal"
                        + " more than once):" + events);
                TerrainGameTests.finish(helper, problems);
            } finally {
                UnitServerEvents.rtsPathfinding = wasRts;
                TerrainGameTests.cleanUp(made);
            }
        });
    }

    /**
     * A unit UnitUnstick rescues while its stuck count is one short of the give-up, and that can walk to its target
     * from there, keeps its order and gets there. The rescue's own repath is judged (it can count as stuck), so it must
     * never be the one that gives the order up, or a unit freed from a block dropped its order the moment it was freed;
     * and rescue() repaths each goal once (the plain move goal is both in the selector and getMoveGoal: twice made
     * every rescue two stuck repaths). RTS pathfinding and vanilla.
     */
    @GameTest(template = ARENA, batch = BATCH_RTS_RESCUED_FREE, timeoutTicks = 600)
    public static void a_unit_rescued_with_its_count_near_the_give_up_keeps_an_order_it_can_walk(GameTestHelper helper) {
        rescuedThenWalks(helper, true);
    }

    @GameTest(template = ARENA, batch = BATCH_VANILLA_RESCUED_FREE, timeoutTicks = 600)
    public static void a_unit_rescued_with_its_count_near_the_give_up_keeps_an_order_it_can_walk_vanilla(GameTestHelper helper) {
        rescuedThenWalks(helper, false);
    }

    private static void rescuedThenWalks(GameTestHelper helper, boolean rts) {
        ServerLevel level = helper.getLevel();
        boolean wasRts = UnitServerEvents.rtsPathfinding;
        UnitServerEvents.rtsPathfinding = rts;
        SkirmishGameTests.openGround(helper, 0, 0, 15, 15);
        BlockPos pad = helper.absolutePos(new BlockPos(0, 2, 0));
        String mine = owner(helper, rts ? "freed" : "freedv");
        List<Entity> made = new ArrayList<>();
        Mob m = vindicator(level, mine, pad.getX() + 2.5, pad.getY(), pad.getZ() + 8.5, made);
        if (m == null) {
            UnitServerEvents.rtsPathfinding = wasRts;
            TerrainGameTests.cleanUp(made);
            helper.fail("could not create the vindicator");
            return;
        }
        BlockPos to = new BlockPos(pad.getX() + 13, pad.getY(), pad.getZ() + 8);
        int[] ordered = { -1 };
        boolean[] rescued = { false }, done = { false };
        int[] before = { -1 }, after = { -1 };
        ArenaReady.whenTicking(helper, made, 150, tick -> {
            ordered[0] = tick;
            move(level, mine, m, to);
        });
        helper.onEachTick(() -> {
            if (ordered[0] < 0 || done[0])
                return;
            long t = helper.getTick() - ordered[0];
            MoveToTargetBlockGoal goal = ((Unit) m).getMoveGoal();
            // once its first path is in and it is on its way: one short of the give-up (vanilla: none, so a rescue
            // that repaths it twice shows as 2), then rescued where it stands
            if (!rescued[0] && goal.getMoveTarget() != null && !goal.isPathPending() && !m.getNavigation().isDone()) {
                rescued[0] = true;
                setStuckRepaths(goal, rts ? MoveToTargetBlockGoal.GIVE_UP_AFTER_STUCK_REPATHS - 1 : 0);
                before[0] = stuckRepaths(goal);
                com.solegendary.reignofnether.unit.UnitUnstick.rescue(m, m.blockPosition());
                after[0] = stuckRepaths(goal);
            }
            double dist = Math.sqrt(m.distanceToSqr(to.getX() + 0.5, m.getY(), to.getZ() + 0.5));
            boolean gaveUp = goal.getLastUnreachableTarget() != null;
            boolean arrived = goal.getMoveTarget() == null && !gaveUp && dist <= 3;
            if (!arrived && !gaveUp && t < 400)
                return;
            done[0] = true;
            try {
                List<String> problems = new ArrayList<>();
                if (!rescued[0])
                    problems.add("the unit was never on its way, so never rescued: this test proves nothing");
                else if (gaveUp)
                    problems.add("the unit gave its order up right after the rescue (stuck count " + before[0] + " -> "
                        + after[0] + ", " + TerrainGameTests.f(dist) + " from a target it could walk to)");
                else if (!arrived)
                    problems.add("the unit did not reach its target in " + t + " ticks (" + TerrainGameTests.f(dist)
                        + " away, order " + (goal.getMoveTarget() == null ? "gone" : "kept") + ")");
                // one rescue is at most one stuck repath (the vanilla path is judged inside rescue())
                if (!rts && after[0] > before[0] + 1)
                    problems.add("one rescue counted " + (after[0] - before[0]) + " stuck repaths (rescue() repathed"
                        + " the move goal more than once)");
                TerrainGameTests.finish(helper, problems);
            } finally {
                UnitServerEvents.rtsPathfinding = wasRts;
                TerrainGameTests.cleanUp(made);
            }
        });
    }

    // the move goal's count of repaths in a row that made no progress (diagnostics only: -1 if it cannot be read)
    static int stuckRepaths(MoveToTargetBlockGoal goal) {
        try {
            java.lang.reflect.Field f = MoveToTargetBlockGoal.class.getDeclaredField("stuckRepaths");
            f.setAccessible(true);
            return f.getInt(goal);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return -1;
        }
    }

    static void setStuckRepaths(MoveToTargetBlockGoal goal, int n) {
        try {
            java.lang.reflect.Field f = MoveToTargetBlockGoal.class.getDeclaredField("stuckRepaths");
            f.setAccessible(true);
            f.setInt(goal, n);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    // ------------------------------------------------------------------- (c) Stop after a queued big formation move

    /**
     * 24 vindicators (more than 20, so with vanilla pathfinding their formation move goes into the time-sliced
     * dispatch queue) are ordered to move, and in the same tick - as when the Move and the Stop packets are handled
     * together - told to Stop. The Stop holds: nothing is left in the queue and 40 ticks later no unit has a move
     * target or has walked off. Before the fix the queued moves dispatched at the end of the tick, over the Stop.
     */
    @GameTest(template = ARENA, batch = BATCH, timeoutTicks = 300)
    public static void a_stop_after_a_queued_big_formation_move_is_not_overridden(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        SkirmishGameTests.openGround(helper, 0, 0, 15, 15);
        String mine = owner(helper, "stop");
        BlockPos o = helper.absolutePos(new BlockPos(0, 2, 0));
        List<Entity> made = new ArrayList<>();
        List<Mob> units = new ArrayList<>();
        for (int i = 0; i < 24; i++) {
            Mob m = vindicator(level, mine, o.getX() + 1.5 + 2 * (i % 6), o.getY(), o.getZ() + 1.5 + 2 * (i / 6), made);
            if (m == null) {
                TerrainGameTests.cleanUp(made);
                helper.fail("could not create the vindicators");
                return;
            }
            units.add(m);
        }
        int[] ids = units.stream().mapToInt(Entity::getId).toArray();
        BlockPos spot = helper.absolutePos(new BlockPos(8, 2, 13));
        List<double[]> starts = new ArrayList<>();
        int[] ordered = { -1 };
        List<String> problems = new ArrayList<>();
        ArenaReady.whenTicking(helper, made, 150, tick -> {
            ordered[0] = tick;
            for (Mob m : units)
                starts.add(new double[] { m.getX(), m.getZ() });
            if (UnitServerEvents.rtsPathfinding) {
                problems.add("rtsPathfinding is on: big formation moves are not queued and this test proves nothing");
                return;
            }
            CommandQueues.issue(level, mine, UnitAction.MOVE, -1, ids, spot, BlockPos.ZERO, QueueMode.REPLACE);
            int queued = 0;
            for (int id : ids)
                if (UnitServerEvents.hasQueuedFormationMove(id))
                    queued++;
            if (queued == 0)
                problems.add("the 24-unit move was not queued for dispatch: this test proves nothing");
            CommandQueues.issue(level, mine, UnitAction.STOP, -1, ids, BlockPos.ZERO, BlockPos.ZERO, QueueMode.REPLACE);
            for (int id : ids)
                if (UnitServerEvents.hasQueuedFormationMove(id)) {
                    problems.add("a unit's formation move was still queued after the Stop");
                    break;
                }
        });
        boolean[] done = { false };
        helper.onEachTick(() -> {
            if (ordered[0] < 0 || done[0] || helper.getTick() < ordered[0] + 40)
                return;
            done[0] = true;
            try {
                int moving = 0, walked = 0;
                for (int i = 0; i < units.size(); i++) {
                    Mob m = units.get(i);
                    if (((Unit) m).getMoveGoal().getMoveTarget() != null)
                        moving++;
                    double dx = m.getX() - starts.get(i)[0], dz = m.getZ() - starts.get(i)[1];
                    if (dx * dx + dz * dz > 1.5 * 1.5)
                        walked++;
                }
                if (moving > 0 || walked > 0)
                    problems.add("40 ticks after Stop, " + moving + " of 24 units had a move target and " + walked
                        + " had walked more than 1.5 blocks: the queued formation move landed over the Stop");
                TerrainGameTests.finish(helper, problems);
            } finally {
                TerrainGameTests.cleanUp(made);
            }
        });
    }

    // ---------------------------------------------------------------- (d) an ordered target survives a nudge give-up

    /**
     * The twin of TerrainGameTests.stuck_chaser_is_nudged_then_drops_its_target, with a target the player ordered
     * (forced): an immobile vindicator chasing it reaches StuckNudge's give-up stage but keeps the target (its ring
     * slot released and the nudge cycle restarted) - it is never dropped, and stays forced.
     */
    @GameTest(template = ARENA, batch = BATCH, timeoutTicks = 560)
    public static void a_forced_target_survives_a_stuck_nudge_give_up(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        SkirmishGameTests.openGround(helper, 0, 0, 15, 15);
        String mine = owner(helper, "forced_a"), theirs = owner(helper, "forced_b");
        BlockPos o = helper.absolutePos(new BlockPos(0, 2, 0));
        List<Entity> made = new ArrayList<>();
        Mob stuck = vindicator(level, mine, o.getX() + 2.5, o.getY(), o.getZ() + 7.5, made);
        Mob foe = TerrainGameTests.dummy(level, EntityRegistrar.VILLAGER_UNIT.get(), theirs, o.getX() + 8.5, o.getY(), o.getZ() + 7.5, made);
        if (stuck == null || foe == null) {
            TerrainGameTests.cleanUp(made);
            helper.fail("could not create the vindicator and the villager");
            return;
        }
        stuck.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.0);
        ArenaReady.whenTicking(helper, made, 150, orderedAt -> {
            ((AttackerUnit) stuck).setUnitAttackTargetForced(foe);
            helper.runAfterDelay(300, () -> {
                try {
                    String state = "kept " + StuckNudge.keptOrderedTargets(stuck) + ", give-ups " + StuckNudge.giveUps(stuck)
                        + ", target " + (stuck.getTarget() == null ? "none" : stuck.getTarget() == foe ? "its foe" : "another")
                        + ", forced " + ((Unit) stuck).getTargetGoal().forced + "; "
                        + TerrainGameTests.chaseState(stuck, foe);
                    ReignOfNether.LOGGER.info("[Orders] forced target vs stuck nudge after 300 ticks: {}", state);
                    List<String> problems = new ArrayList<>();
                    if (StuckNudge.keptOrderedTargets(stuck) < 1)
                        problems.add("the immobile vindicator never reached the give-up stage (nothing tested): " + state);
                    if (StuckNudge.giveUps(stuck) > 0 || stuck.getTarget() != foe
                            || ((Unit) stuck).getTargetGoal().getTarget() != foe)
                        problems.add("the player-ordered target was dropped: " + state);
                    if (!((Unit) stuck).getTargetGoal().forced)
                        problems.add("the target is no longer marked as ordered (forced): " + state);
                    TerrainGameTests.finish(helper, problems);
                } finally {
                    TerrainGameTests.cleanUp(made);
                }
            });
        });
    }

    // ----------------------------------------------------------------------------- (e) formation drags vs rate limit

    /**
     * A formation drag sends one single-unit order per unit at once. Two 800-unit drags back to back all pass the
     * rate limit; a flood far past that is still refused (anti-abuse kept), and whole-selection orders keep their own
     * bucket. Before, the shared 600-packet burst dropped most of the second drag - silently.
     */
    @GameTest(template = ARENA, batch = BATCH, timeoutTicks = 40)
    public static void two_back_to_back_800_unit_formation_drags_pass_the_rate_limit(GameTestHelper helper) {
        UUID player = UUID.randomUUID();
        long now = System.nanoTime();
        int[] one = { 1 };
        List<String> problems = new ArrayList<>();
        int refused = 0;
        for (int i = 0; i < 1600; i++)
            if (!UnitActionServerboundPacket.allowOrder(player, one, now))
                refused++;
        if (refused > 0)
            problems.add(refused + " of two 800-unit formation drags' orders were refused");
        int flood = 0;
        for (int i = 0; i < 2000; i++)
            if (!UnitActionServerboundPacket.allowOrder(player, one, now))
                flood++;
        if (flood == 0)
            problems.add("a flood of 3600 single-unit orders in one instant was never refused: no rate limit left");
        if (!UnitActionServerboundPacket.allowOrder(player, new int[] { 1, 2, 3 }, now))
            problems.add("a whole-selection order was refused after the formation slots' flood: the buckets are not separate");
        com.solegendary.reignofnether.network.PacketAuth.forget(player);
        TerrainGameTests.finish(helper, problems);
    }
}
