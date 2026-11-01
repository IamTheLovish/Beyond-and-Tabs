package com.solegendary.reignofnether.gametest;

import com.mojang.authlib.GameProfile;
import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.unit.EngagementStance;
import com.solegendary.reignofnether.unit.UnitAction;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.pathfinding.PathfinderWorkerPool;
import com.solegendary.reignofnether.unit.command.CommandQueues;
import com.solegendary.reignofnether.unit.command.QueueMode;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.packets.UnitActionServerboundPacket;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.game.ClientboundMoveEntityPacket;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * "I think we need a way to have commands from the player reach the units quicker, it feels like there's sometimes a
 * delay in actions" (lovish, Oct 22 20:23, dedicated server at 3 ms ticks). The bar is Beyond All Reason / StarCraft:
 * a unit answers an order on the very next frame. Each test gives idle units an order exactly the way a click arrives
 * - a packet the server handles between two ticks (a server task running UnitActionServerboundPacket.accept, the
 * handler minus the network) - and measures, in the unit's own ticks after the order:
 * <ul>
 *   <li><b>move start</b>: the first tick the unit's position changed;</li>
 *   <li><b>seen</b>: the first tick the entity tracker sends that movement to the players watching. Measured with the
 *       game's own tracker class (ServerEntity, with the unit type's update interval and delta tracking, as
 *       ChunkMap.TrackedEntity makes it), one per unit; a mob's tracker sends its position every 3rd tick, so the six
 *       or seven units' trackers start on different phases of that cadence to cover all three. The client shows the
 *       unit moving on its next frame after that packet arrives (plus the network's own delay, which no server change
 *       removes).</li>
 * </ul>
 * Every unit must be seen moving within {@link #MAX_SEEN} ticks of its order. The orders are spread over ticks
 * ({@link #STAGGER} apart), so they also land on every phase of the goal selector (a mob checks for new goals every
 * other tick) and of the melee goal's re-check timer. Every unit is on Hold fire and cannot be hurt, so no neighbouring
 * arena's fight touches them; the attack targets stand still (no AI) and cannot die.
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class OrderLatencyGameTests {

    static final String ARENA = SkirmishGameTests.ARENA;
    // its own batch: it takes the rtsPathfinding flag (a real match runs the RTS pathfinder: ArenaPathing)
    static final String BATCH_RTS = "order_latency_rts";

    /** The bar: seen moving within this many of its own ticks after the order. */
    static final int MAX_SEEN = 2;
    /** Test ticks between one unit's order and the next one's. */
    static final int STAGGER = 3;
    /** Ticks the units stand idle after the warm-up move, before the first order. */
    static final int QUIET = 30;
    /** Ticks after the last order for every unit to be seen moving before the test gives up on it. */
    static final int WAIT = 60;
    /** "Moved": farther than this from where it stood when the order came (a first step is ~0.1 block). */
    static final double MOVED = 0.03;

    // ------------------------------------------------------------------------------------------------ the tests

    /** Seven idle vindicators, each sent 10 blocks east. Match pathfinder (async grid A*). */
    @GameTest(template = ARENA, batch = BATCH_RTS, timeoutTicks = 600)
    public static void an_idle_unit_is_seen_moving_within_two_ticks_of_a_move_order(GameTestHelper helper) {
        run(helper, "move (rts)", true, EntityRegistrar.VINDICATOR_UNIT.get(), 1, 7, UnitAction.MOVE, 13);
    }

    /** The same on the vanilla pathfinder (the gamerule's default, on a world without an RTS map border). */
    @GameTest(template = ARENA, timeoutTicks = 600)
    public static void an_idle_unit_is_seen_moving_within_two_ticks_of_a_move_order_on_vanilla_pathing(GameTestHelper helper) {
        run(helper, "move (vanilla)", false, EntityRegistrar.VINDICATOR_UNIT.get(), 1, 7, UnitAction.MOVE, 13);
    }

    /** Seven idle vindicators, each ordered to attack a target 7 blocks off (out of reach: it must walk to it). */
    @GameTest(template = ARENA, batch = BATCH_RTS, timeoutTicks = 600)
    public static void an_idle_melee_unit_is_seen_moving_within_two_ticks_of_an_attack_order(GameTestHelper helper) {
        run(helper, "melee attack", true, EntityRegistrar.VINDICATOR_UNIT.get(), 1, 7, UnitAction.ATTACK, 10);
    }

    /** Seven idle skeletons (bow range 12), each ordered to attack a target 13 blocks off: it must close in first. */
    @GameTest(template = ARENA, batch = BATCH_RTS, timeoutTicks = 600)
    public static void an_idle_archer_is_seen_moving_within_two_ticks_of_an_attack_order(GameTestHelper helper) {
        run(helper, "ranged attack", true, EntityRegistrar.SKELETON_UNIT.get(), 0, 7, UnitAction.ATTACK, 15);
    }

    // ------------------------------------------------------------------------------------------------ the run

    /** One unit under measurement: its tracker copy, when the order came, when it moved, when it was seen moving. */
    private static final class Probe {
        final Mob mob;
        final ServerEntity tracker;
        boolean sent;           // the tracker sent a move or teleport packet in its last sendChanges
        boolean refused;        // the server refused the order
        int orderTick = -1;     // the unit's tickCount when the order was handled (its last finished tick)
        int firstMove = -1, firstSeen = -1;
        Vec3 orderPos;
        // what the unit's pathing looked like, one entry per tick from the order until it first moved: printed only
        // when the test fails (a late first step), so a passing run says nothing
        final StringBuilder trace = new StringBuilder();

        Probe(ServerLevel level, Mob mob, int phase) {
            this.mob = mob;
            EntityType<?> type = mob.getType();
            this.tracker = new ServerEntity(level, mob, type.updateInterval(), type.trackDeltas(), packet -> {
                if (packet instanceof ClientboundMoveEntityPacket || packet instanceof ClientboundTeleportEntityPacket)
                    sent = true;
            });
            for (int k = 0; k < phase; k++)
                tracker.sendChanges();
        }

        /** Once per server tick, after the entities have ticked (the real tracker runs at the next tick's start). */
        void tick() {
            sent = false;
            tracker.sendChanges();
            if (orderTick < 0 || orderPos == null)
                return;
            double dx = mob.getX() - orderPos.x, dz = mob.getZ() - orderPos.z;
            boolean moved = dx * dx + dz * dz > MOVED * MOVED;
            if (firstMove < 0 && trace.length() < 400)
                trace.append(" +").append(mob.tickCount - orderTick).append('{').append(state()).append('}');
            if (moved && firstMove < 0)
                firstMove = mob.tickCount;
            if (moved && sent && firstSeen < 0)
                firstSeen = mob.tickCount;
        }

        /** rts flag, the move goal's request in flight / running, the navigation's path, on the ground: one tick's look. */
        String state() {
            Path path = mob.getNavigation().getPath();
            String nav = path == null ? "nopath" : (path.isDone() ? "done" : "node" + path.getNextNodeIndex() + "/" + path.getNodeCount());
            var goal = ((Unit) mob).getMoveGoal();
            return (UnitServerEvents.rtsPathfinding ? "rts " : "vanilla ") + (goal.isPathPending() ? "pending " : "")
                + (goal.isGoalRunning() ? "goal " : "nogoal ") + nav + (mob.onGround() ? "" : " AIR")
                + (goal.isPathPending() ? " (" + PathfinderWorkerPool.debugState() + ")" : "");
        }

        int moveLatency() { return firstMove < 0 ? -1 : firstMove - orderTick; }
        int seenLatency() { return firstSeen < 0 ? -1 : firstSeen - orderTick; }
    }

    /**
     * {@code count} units of {@code type} on open ground at x = {@code x0} (z 1, 3, 5 ...), each with a foe dummy at
     * x = {@code targetX} for an attack. After a warm-up move of two blocks (it warms the pathfinder's corridor, as a
     * match's prewarm does) and {@link #QUIET} ticks standing, unit i gets its order {@link #STAGGER}*i ticks after
     * the first: a move to x = {@code targetX}, or an attack on its dummy.
     */
    private static void run(GameTestHelper helper, String name, boolean rts, EntityType<? extends Mob> type, int x0,
                            int count, UnitAction action, int targetX) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        Runnable giveBackPathing = rts ? RtsPathingLease.takeUntilEnd(helper) : () -> { };
        SkirmishGameTests.openGround(helper, 0, 0, 15, 15);
        String tag = name.replaceAll("[^a-z]", "");
        String mine = "gt_lat_" + tag + "_" + Long.toHexString(helper.absolutePos(BlockPos.ZERO).asLong());
        String foe = "gt_latfoe_" + tag + "_" + Long.toHexString(helper.absolutePos(BlockPos.ZERO).asLong());
        BlockPos o = helper.absolutePos(new BlockPos(0, 2, 0));
        List<Entity> made = new ArrayList<>();
        List<Mob> units = new ArrayList<>();
        List<Mob> targets = new ArrayList<>();
        boolean[] finished = { false };
        java.util.function.Consumer<String> finish = failure -> {
            if (finished[0])
                return;
            finished[0] = true;
            TerrainGameTests.cleanUp(made);
            giveBackPathing.run();
            if (failure == null)
                helper.succeed();
            else
                helper.fail(failure);
        };
        try {
            for (int i = 0; i < count; i++) {
                int z = 1 + 2 * i;
                Mob m = TerrainGameTests.spawn(level, type, mine, o.getX() + x0 + 0.5, o.getY(), o.getZ() + z + 0.5, made);
                if (m == null) {
                    finish.accept("could not create unit " + i);
                    return;
                }
                ((Unit) m).setupEquipmentAndUpgradesServer();   // a skeleton's bow
                EngagementStance.set(m, EngagementStance.HOLD_FIRE);
                m.setInvulnerable(true);
                units.add(m);
                if (action == UnitAction.ATTACK) {
                    Mob t = TerrainGameTests.dummy(level, EntityRegistrar.VINDICATOR_UNIT.get(), foe,
                        o.getX() + targetX + 0.5, o.getY(), o.getZ() + z + 0.5, made);
                    if (t == null) {
                        finish.accept("could not create target " + i);
                        return;
                    }
                    EngagementStance.set(t, EngagementStance.HOLD_FIRE);
                    targets.add(t);
                }
            }
        } catch (GameTestAssertException e) {
            throw e;   // helper.fail's own way of failing the test
        } catch (Throwable t) {
            finish.accept("setting up: " + t);
            return;
        }
        // a scout, not measured: its warm-up walk along the arena's south edge has the pathfinder classify the whole
        // corridor the measured orders use, as a match's map is prewarmed at load (ArenaPathing)
        Mob scout = TerrainGameTests.spawn(level, EntityRegistrar.VINDICATOR_UNIT.get(), mine, o.getX() + 15.5, o.getY(),
            o.getZ() + 15.5, made);
        if (scout != null) {
            EngagementStance.set(scout, EngagementStance.HOLD_FIRE);
            scout.setInvulnerable(true);
        }
        ServerPlayer player = FakePlayerFactory.get(level,
            new GameProfile(UUID.nameUUIDFromBytes(mine.getBytes(StandardCharsets.UTF_8)), mine));
        List<Probe> probes = new ArrayList<>();
        int[] phase = { 0 };   // 0 warm-up, 1 quiet, 2 measuring
        int[] warmedAt = { -1 }, quietFrom = { -1 }, firstOrderAt = { -1 };

        ArenaReady.whenTicking(helper, made, 150, tick -> {
            // the warm-up: two blocks east, given directly (not measured); the scout across the arena
            for (Mob m : units)
                CommandQueues.issue(level, mine, UnitAction.MOVE, -1, new int[] { m.getId() },
                    m.blockPosition().east(2), BlockPos.ZERO, QueueMode.REPLACE);
            if (scout != null)
                CommandQueues.issue(level, mine, UnitAction.MOVE, -1, new int[] { scout.getId() },
                    new BlockPos(o.getX() + 1, o.getY(), o.getZ() + 15), BlockPos.ZERO, QueueMode.REPLACE);
            warmedAt[0] = tick;
        });
        helper.onEachTick(() -> {
            if (finished[0])
                return;
            try {
                long t = helper.getTick();
                if (phase[0] == 0) {
                    if (warmedAt[0] < 0 || t < warmedAt[0] + 10)
                        return;
                    boolean settled = scout == null || scout.getNavigation().isDone();
                    for (Mob m : units)
                        settled &= m.getNavigation().isDone() && m.getDeltaMovement().horizontalDistanceSqr() < 1e-6;
                    if (!settled && t < warmedAt[0] + 200)
                        return;
                    phase[0] = 1;
                    quietFrom[0] = (int) t;
                    // one tracker per unit, its cadence shifted by i % 3 ticks: the units cover every phase
                    for (int i = 0; i < units.size(); i++)
                        probes.add(new Probe(level, units.get(i), i % 3));
                    return;
                }
                for (Probe p : probes)
                    p.tick();
                if (phase[0] == 1) {
                    if (t < quietFrom[0] + QUIET)
                        return;
                    phase[0] = 2;
                    firstOrderAt[0] = (int) t;
                    for (int i = 0; i < units.size(); i++) {
                        final int n = i;
                        helper.runAfterDelay(1 + (long) STAGGER * i, () -> {
                            if (finished[0])
                                return;
                            // handled between two ticks, as the server handles a player's packet (Forge enqueueWork)
                            server.tell(new TickTask(server.getTickCount(), () -> {
                                if (finished[0])
                                    return;
                                Probe p = probes.get(n);
                                Mob m = p.mob;
                                p.orderTick = m.tickCount;
                                p.orderPos = m.position();
                                int targetId = action == UnitAction.ATTACK ? targets.get(n).getId() : -1;
                                BlockPos to = action == UnitAction.ATTACK ? targets.get(n).blockPosition()
                                    : new BlockPos(o.getX() + targetX, o.getY(), m.getBlockZ());
                                // (a server task, not a test tick: a refusal is reported by the tick that measures)
                                p.refused = !UnitActionServerboundPacket.accept(player, mine, action, targetId,
                                    new int[] { m.getId() }, to, BlockPos.ZERO, QueueMode.REPLACE);
                            }));
                        });
                    }
                    return;
                }
                boolean all = true;
                for (Probe p : probes)
                    all &= p.firstSeen >= 0;
                long lastOrder = firstOrderAt[0] + 1 + (long) STAGGER * (probes.size() - 1);
                if (!all && t < lastOrder + WAIT)
                    return;
                int[] move = new int[probes.size()], seen = new int[probes.size()];
                List<String> problems = new ArrayList<>();
                for (int i = 0; i < probes.size(); i++) {
                    Probe p = probes.get(i);
                    move[i] = p.moveLatency();
                    seen[i] = p.seenLatency();
                    if (p.orderTick < 0)
                        problems.add("unit " + i + " never got its order");
                    else if (p.refused)
                        problems.add("the order for unit " + i + " was refused");
                    else if (seen[i] < 0)
                        problems.add(String.format(Locale.ROOT, "unit %d (#%d) was not seen moving %d ticks after its order (moved at %d, "
                                + "at %.2f/%.2f, target %s, nav done %s, attack target %s)", i, p.mob.getId(), WAIT, move[i],
                            p.mob.getX(), p.mob.getZ(), ((Unit) p.mob).getMoveGoal().getMoveTarget(), p.mob.getNavigation().isDone(),
                            p.mob.getTarget() == null ? "none" : "#" + p.mob.getTarget().getId()));
                    else if (seen[i] > MAX_SEEN)
                        problems.add("unit " + i + " (#" + p.mob.getId() + ") seen moving " + seen[i] + " ticks after its order (moved at "
                            + move[i] + "); per tick from the order:" + p.trace);
                }
                String line = String.format(Locale.ROOT, "%s: move start %s, seen by clients %s (unit ticks after the order; max %d, "
                        + "median %d)", name, Arrays.toString(move), Arrays.toString(seen), max(seen), median(seen));
                ReignOfNether.LOGGER.info("[Latency] {}", line);
                finish.accept(problems.isEmpty() ? null : line + " - " + String.join("; ", problems));
            } catch (GameTestAssertException e) {
                throw e;   // helper.fail's own way of failing the test
            } catch (Throwable e) {
                finish.accept("measuring: " + e);
            }
        });
    }

    private static int max(int[] a) {
        int m = Integer.MIN_VALUE;
        for (int v : a)
            m = Math.max(m, v < 0 ? Integer.MAX_VALUE : v);
        return m;
    }

    private static int median(int[] a) {
        int[] s = a.clone();
        for (int i = 0; i < s.length; i++)
            if (s[i] < 0)
                s[i] = Integer.MAX_VALUE;
        Arrays.sort(s);
        return s[s.length / 2];
    }
}
