package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.unit.UnitAction;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.modelling.motion.AnimState;
import com.solegendary.reignofnether.unit.modelling.motion.GroundTilt;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.game.ClientboundMoveEntityPacket;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import net.minecraft.network.protocol.game.VecDeltaCodec;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

/**
 * Motion fluidity, step 1: MEASURE what makes units look laggy or jerky before anything is changed (lovish, Nov 5: "I
 * want the units to have more fluidity in the game and not look like their lagging or bugging around"). Each unit kind
 * walks a flat lane and a stepped lane (up 1, 2, 1.5 blocks, then down) on an open pad, and a mixed crowd walks to one
 * point and stands there; every server tick the unit's motion is recorded, and a copy of the game's own entity tracker
 * (ServerEntity, as OrderLatencyGameTests makes one) records the packets a watching player would get. ClientView then
 * replays those packets as the 1.20.1 client draws them, under four match-server clocks: a steady 20 TPS, the p50/p95
 * tick times of lovish's own 500-599 and 700-799 unit windows (server logs, Oct 31 - Nov 3), and a server stuck at
 * 14.5 TPS (his Oct 29 770-unit game).
 * <p>
 * Each lane logs one "[Motion]" line (CI folds them into one notice): the server's walk (speed, its variation, stops,
 * yaw steps and reversals, rises, airborne ticks) and the packets (gaps between position packets, rotation-only
 * packets, teleports), then the drawn walk per jittery clock: speed cv / % of client ticks the unit stands still for
 * vanilla's lerp ("now") and the game's stamped playback ("buf", with its lag behind the newest tick heard), and the
 * position packets vanilla's lerp tracked every tick would cost ("e1 pos"). A ground lane adds where the drawn body's
 * feet stand against its blocks ("gnd", step 3 A); a stepped lane that breaks its ground bounds names, in its failure,
 * the client ticks around its worst sink and float (CI keeps only the first 300 characters of 15 "[Motion]" lines, and
 * every failure line whole). The crowd adds what idle units do after arriving. The asserts guard the measurement itself (the unit moved)
 * and, on the stepped lanes, the ground under the drawn body ({@link Gnd}); the other bounds come with their fixes.
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class MotionFluidityGameTests {

    static final String ARENA = SkirmishGameTests.ARENA;
    // its own batch: it switches the server-wide rtsPathfinding flag on (every battlefield match runs it)
    static final String BATCH = "motion_fluidity";
    static final int SETUP = 20;
    static final int PAD_Y = WalkLineGameTests.PAD_Y;
    /** Pad cells: the flat lane, the stepped lane, the glass wall between them, the start and the goal. */
    static final int FLAT_Z = 6, STEP_Z = 17, WALL_Z = 11, START_X = 3, GOAL_X = 42;
    /** Ticks a walk may take before it is judged on what it walked. */
    static final int WALK_TICKS = 420;
    /** Ticks the crowd is watched standing, after it arrived. */
    static final int IDLE_TICKS = 100;
    /**
     * A snap: the yaw the packets carry (yRot) turning over this many degrees in one server tick (vanilla MoveControl
     * turns up to 90 a tick toward its wanted point); NEAR_ACROSS: a wanted point closer across than this is almost
     * straight above or below the unit, so the heading toward it is noise.
     */
    static final double SNAP_DEG = 45, NEAR_ACROSS = 0.3;

    /** The median of {@code v} (NaN when empty). */
    static double median(List<Double> v) {
        if (v.isEmpty())
            return Double.NaN;
        double[] s = v.stream().mapToDouble(Double::doubleValue).sorted().toArray();
        return s[s.length / 2];
    }

    static final ClientView.Clock[] CLOCKS = {
        ClientView.Clock.steady(),
        ClientView.Clock.logged("500u", 34.0, 70.9),
        ClientView.Clock.logged("700u", 38.9, 85.2),
        ClientView.Clock.slow("14.5tps", 1000.0 / 14.5),
    };

    @GameTest(template = ARENA, timeoutTicks = SETUP + WALK_TICKS + 80, batch = BATCH)
    public static void motion_measure_infantry_on_flat_and_stepped_ground(GameTestHelper helper) {
        lanes(helper, "infantry Vindicator", EntityRegistrar.VINDICATOR_UNIT::get, 16, true, false);
    }

    @GameTest(template = ARENA, timeoutTicks = SETUP + WALK_TICKS + 80, batch = BATCH)
    public static void motion_measure_beast_on_flat_and_stepped_ground(GameTestHelper helper) {
        lanes(helper, "beast Mossback Bison", EntityRegistrar.MOSSBACK_BISON_UNIT::get, 17, false, false);
    }

    @GameTest(template = ARENA, timeoutTicks = SETUP + WALK_TICKS + 80, batch = BATCH)
    public static void motion_measure_large_unit_on_flat_and_stepped_ground(GameTestHelper helper) {
        lanes(helper, "large War Mammoth", EntityRegistrar.WAR_MAMMOTH_UNIT::get, 18, false, false);
    }

    @GameTest(template = ARENA, timeoutTicks = SETUP + WALK_TICKS + 80, batch = BATCH)
    public static void motion_measure_flyer_over_flat_and_stepped_ground(GameTestHelper helper) {
        lanes(helper, "flyer Gyrocopter", EntityRegistrar.GYROCOPTER_GUNSHIP_UNIT::get, 19, false, true);
    }

    // ------------------------------------------------------------------------------------------------ the lanes

    /** {@code man}: the unit is held to a man's ground bounds ({@link Gnd}), not a beast's. */
    private static void lanes(GameTestHelper helper, String kind, Supplier<? extends EntityType<? extends Mob>> type,
                              int padIndex, boolean man, boolean flyer) {
        TestCleanup.rtsPathfinding(helper);
        WalkLineGameTests.Pad pad = WalkLineGameTests.Pad.lay(helper, padIndex);
        // a glass wall between the lanes, so neither walk can step round the other's ground
        for (int dx = 1; dx < WalkLineGameTests.W - 1; dx++)
            for (int dy = 1; dy <= 3; dy++)
                pad.set(dx, dy, WALL_Z, Blocks.GLASS.defaultBlockState());
        steps(pad);
        ServerLevel level = pad.level;
        String owner = "gametest_motion_" + helper.absolutePos(BlockPos.ZERO).asLong();
        List<Mob> made = new ArrayList<>();
        TestCleanup.atEnd(helper, () -> {
            for (Mob m : made) {
                UnitServerEvents.clearQueuedActions(m.getId());
                m.discard();
            }
        });
        int y0 = PAD_Y + 1 + (flyer ? 5 : 0);
        helper.runAfterDelay(SETUP, () -> {
            for (int lane : new int[] { FLAT_Z, STEP_Z }) {
                Mob m = type.get().create(level);
                if (m == null)
                    throw new GameTestAssertException("could not create the " + kind);
                m.moveTo(pad.x0 + START_X + 0.5, y0, pad.z0 + lane + 0.5, -90, 0);
                ((Unit) m).setOwnerName(owner);
                level.addFreshEntity(m);
                made.add(m);
            }
        });
        Probe[] probes = new Probe[2];
        int[] ordered = { -1 };
        helper.onEachTick(() -> {
            if (made.size() < 2 || probes[1] != null && probes[0].done && probes[1].done)
                return;
            if (ordered[0] < 0) {
                for (Mob m : made)
                    if (m.tickCount < ArenaReady.TICKS) {
                        if (helper.getTick() > SETUP + 200)
                            throw new GameTestAssertException("the units were not ticking: " + ArenaReady.describe(level, made));
                        return;
                    }
                ordered[0] = (int) helper.getTick();
                for (int i = 0; i < 2; i++) {
                    Mob m = made.get(i);
                    int lane = i == 0 ? FLAT_Z : STEP_Z;
                    probes[i] = new Probe(level, m, pad.x0 + GOAL_X + 0.5, pad.z0 + lane + 0.5);
                    // a flyer's order names the ground block (FlyingMoveToTargetGoal ignores an AIR target)
                    UnitServerEvents.addActionItem(owner, UnitAction.MOVE, -1, new int[] { m.getId() },
                        new BlockPos(pad.x0 + GOAL_X, PAD_Y + (flyer ? 0 : 1), pad.z0 + lane), BlockPos.ZERO);
                }
                return;
            }
            int t = (int) helper.getTick() - ordered[0];
            for (Probe p : probes)
                if (!p.done) {
                    p.tick();
                    if (p.left() < 3 || t >= WALK_TICKS)
                        p.done = true;
                }
            if (!probes[0].done || !probes[1].done)
                return;
            List<String> problems = new ArrayList<>(), worse = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                String ground = i == 0 ? "flat" : "stepped";
                Probe p = probes[i];
                p.log(kind + " " + ground);
                if (p.walked() < 8)
                    problems.add(ground + " lane: walked only " + String.format(Locale.ROOT, "%.1f", p.walked())
                        + " blocks in " + p.ticks() + " ticks");
                String bad = i == 1 && p.gnd != null ? p.gnd.broken(man) : null;
                if (bad != null)
                    worse.add(bad + (p.gndStory == null ? "" : "; worst frames" + p.gndStory));
            }
            if (!problems.isEmpty())
                helper.fail(kind + ": the measurement has no walk to judge: " + String.join("; ", problems));
            else if (!worse.isEmpty())
                helper.fail(kind + " on the stepped lane is drawn off its ground: " + String.join("; ", worse));
            else
                helper.succeed();
        });
    }

    /** The stepped lane's ground, across the whole lane (wall to rim): up 1, up 2, down to 1.5 (a slab), 1, the floor. */
    private static void steps(WalkLineGameTests.Pad pad) {
        BlockState stone = Blocks.STONE.defaultBlockState();
        BlockState slab = Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM);
        for (int dz = WALL_Z + 1; dz < WalkLineGameTests.D - 1; dz++)
            for (int dx = 12; dx <= 27; dx++) {
                pad.set(dx, 1, dz, stone);
                if (dx >= 16 && dx <= 19)
                    pad.set(dx, 2, dz, stone);
                else if (dx >= 20 && dx <= 23)
                    pad.set(dx, 2, dz, slab);
            }
    }

    // ------------------------------------------------------------------------------------------------ the crowd

    /**
     * Twelve Vindicators, three Hoglins and a Siege Ox in a block, ordered to one point 30 blocks east; the walk as
     * above per kind (the first unit of each), then {@link #IDLE_TICKS} ticks after the crowd arrived: how far each idle
     * unit still moves (push and separation), how much its body still turns, and how many position packets it still
     * costs a watching client a second.
     */
    @GameTest(template = ARENA, timeoutTicks = SETUP + WALK_TICKS + IDLE_TICKS + 120, batch = BATCH)
    public static void motion_measure_a_crowd_walking_to_one_point_and_standing(GameTestHelper helper) {
        TestCleanup.rtsPathfinding(helper);
        WalkLineGameTests.Pad pad = WalkLineGameTests.Pad.lay(helper, 20);
        ServerLevel level = pad.level;
        String owner = "gametest_motion_crowd_" + helper.absolutePos(BlockPos.ZERO).asLong();
        List<Mob> made = new ArrayList<>();
        TestCleanup.atEnd(helper, () -> {
            for (Mob m : made) {
                UnitServerEvents.clearQueuedActions(m.getId());
                m.discard();
            }
        });
        List<Supplier<? extends EntityType<? extends Mob>>> types = new ArrayList<>();
        for (int i = 0; i < 12; i++)
            types.add(EntityRegistrar.VINDICATOR_UNIT::get);
        for (int i = 0; i < 3; i++)
            types.add(EntityRegistrar.HOGLIN_UNIT::get);
        types.add(EntityRegistrar.RAVAGER_UNIT::get);
        helper.runAfterDelay(SETUP, () -> {
            for (int i = 0; i < types.size(); i++) {
                Mob m = types.get(i).get().create(level);
                if (m == null)
                    throw new GameTestAssertException("could not create crowd unit " + i);
                // a 4 x 4 block, 2 blocks apart (the Ox at the back corner)
                m.moveTo(pad.x0 + 3.5 + (i % 4) * 2, PAD_Y + 1, pad.z0 + 6.5 + (i / 4) * 2.5, -90, 0);
                ((Unit) m).setOwnerName(owner);
                level.addFreshEntity(m);
                made.add(m);
            }
        });
        final double gx = pad.x0 + 34.5, gz = pad.z0 + 12.5;
        List<Probe> probes = new ArrayList<>();
        int[] ordered = { -1 }, arrived = { -1 };
        helper.onEachTick(() -> {
            if (made.size() < types.size())
                return;
            if (ordered[0] < 0) {
                for (Mob m : made)
                    if (m.tickCount < ArenaReady.TICKS) {
                        if (helper.getTick() > SETUP + 200)
                            throw new GameTestAssertException("the crowd was not ticking: " + ArenaReady.describe(level, made));
                        return;
                    }
                ordered[0] = (int) helper.getTick();
                for (Mob m : made)
                    probes.add(new Probe(level, m, gx, gz).arriveWithin(6));
                UnitServerEvents.addActionItem(owner, UnitAction.MOVE, -1, made.stream().mapToInt(Mob::getId).toArray(),
                    BlockPos.containing(gx, PAD_Y + 1, gz), BlockPos.ZERO);
                return;
            }
            int t = (int) helper.getTick() - ordered[0];
            for (Probe p : probes)
                p.tick();
            if (arrived[0] < 0) {
                boolean all = t >= WALK_TICKS;
                if (!all) {
                    all = true;
                    for (Mob m : made) {
                        var goal = ((Unit) m).getMoveGoal();
                        boolean moving = goal != null && (goal.getMoveTarget() != null || goal.isPathPending())
                            || m.getNavigation().isInProgress();
                        if (moving) { all = false; break; }
                    }
                }
                if (all) {
                    arrived[0] = t;
                    for (Probe p : probes)
                        p.markIdle();
                }
                return;
            }
            if (t - arrived[0] < IDLE_TICKS)
                return;
            // the walk, for the first unit of each kind
            probes.get(0).log("crowd Vindicator");
            probes.get(12).log("crowd Hoglin");
            probes.get(15).log("crowd Siege Ox");
            double moved = 0, worst = 0, churn = 0, packets = 0;
            int still = 0;
            for (Probe p : probes) {
                double[] idle = p.idle();
                moved += idle[0];
                worst = Math.max(worst, idle[0]);
                churn += idle[1];
                packets += idle[2];
                if (idle[0] < 0.05)
                    still++;
            }
            int n = probes.size();
            String line = String.format(Locale.ROOT, "[Motion] crowd of %d idle for %d ticks after arriving at t%d:"
                + " moved %.2f blocks each on average (worst %.2f), %d of %d stood still; body turned %.1f deg each;"
                + " %.1f position packets a second each", n, IDLE_TICKS, arrived[0], moved / n, worst, still, n, churn / n,
                packets / n / (IDLE_TICKS / 20.0));
            ReignOfNether.LOGGER.warn(line);
            double walked = 0;
            for (Probe p : probes)
                walked += p.walked();
            if (walked / n < 8)
                helper.fail("the crowd did not walk (" + String.format(Locale.ROOT, "%.1f", walked / n) + " blocks each): " + line);
            else
                helper.succeed();
        });
    }

    // ------------------------------------------------------------------------------------------------ the ground's cost

    /**
     * What the ground under the body (GroundTilt, step 3 A) costs, set before it was measured: 300 units' ground read and
     * tilt stepped in at most this many ms a client tick (a client tick is 50 ms; 1.5 ms is 3% of it, on a shared CI
     * runner).
     */
    static final double TILT_BUDGET_MS = 1.5;

    /**
     * 100 and then 300 units spread over the stepped lane (every width from a man to a War Mammoth, every heading) each
     * read the ground under their four feet off the lane's real blocks and step their tilt, as AnimStates does every
     * client tick. After a warm-up, the median of 21 rounds is the cost a client tick.
     */
    @GameTest(template = ARENA, timeoutTicks = 200, batch = BATCH)
    public static void motion_ground_tilt_costs_little_at_100_and_300_units(GameTestHelper helper) {
        WalkLineGameTests.Pad pad = WalkLineGameTests.Pad.lay(helper, 21);
        steps(pad);
        helper.runAfterDelay(5, () -> {
            GroundTilt.Ground g = GroundTilt.of(pad.level);
            float[] widths = { 0.6f, 0.9f, 1.95f, 3.315f };
            float[] target = new float[3];
            double[] ms = new double[2];
            int[] counts = { 100, 300 };
            for (int k = 0; k < counts.length; k++) {
                int n = counts[k];
                float[] slab = new float[n * AnimState.STRIDE];
                double[] xs = new double[n], ys = new double[n], zs = new double[n];
                float[] yaws = new float[n], ws = new float[n];
                for (int i = 0; i < n; i++) {
                    xs[i] = pad.x0 + 3 + (i * 7.31) % 38;
                    zs[i] = pad.z0 + 12.5 + (i * 3.17) % 8;
                    double top = GroundTilt.foot(g, xs[i], zs[i], 0.3, PAD_Y + 2.0);
                    ys[i] = Double.isNaN(top) ? PAD_Y + 1 : top;
                    yaws[i] = (i * 37) % 360 - 180f;
                    ws[i] = widths[i % widths.length];
                }
                double[] rounds = new double[21];
                for (int round = -5; round < rounds.length; round++) {
                    long t0 = System.nanoTime();
                    for (int i = 0; i < n; i++) {
                        GroundTilt.target(g, xs[i], ys[i], zs[i], yaws[i], ws[i], true, target);
                        GroundTilt.step(slab, i * AnimState.STRIDE, target, ys[i], round == -5);
                    }
                    if (round >= 0)
                        rounds[round] = (System.nanoTime() - t0) / 1e6;
                }
                Arrays.sort(rounds);
                ms[k] = rounds[rounds.length / 2];
            }
            String line = String.format(Locale.ROOT, "[Motion] ground tilt cost a client tick: 100u %.3f ms, 300u %.3f ms"
                + " (budget %.1f ms at 300u)", ms[0], ms[1], TILT_BUDGET_MS);
            ReignOfNether.LOGGER.warn(line);
            if (ms[1] > TILT_BUDGET_MS)
                helper.fail("the ground under the body costs too much: " + line);
            else
                helper.succeed();
        });
    }

    // ------------------------------------------------------------------------------------------------ one unit

    /**
     * The ground under one drawn walk ("gnd", step 3 A), today (upright) and tilted: the mean |gap| per foot pad, the
     * worst float over the frames that show the unit on the ground (the hop up a step floats by design), the worst sink
     * over all of them. The bounds were set before the run that judges them (Eris.16's round 1 review of #551, Nov 6): a
     * beast stands at most half today's mean gap off the ground and never sinks deeper than today's worst; a man stays
     * upright by design (3 degrees of tilt at most) and is never drawn worse than today, with 0.02 blocks of slack on his
     * sink.
     * <p>
     * A beast's worst float is not bounded in step 3 A. lovish, Nov 6 (via Eris.17's buttons): "Accept for 3A, fix in 3B
     * (Recommended)". It is a one-frame event, one corner pad first passing over a 1-block edge while the other three
     * stand level (CI build 42166157505), and a rigid body cannot put one corner down without lifting the others. Step 3
     * B (per-foot contact) brings the bound back: at most three quarters of today's worst.
     */
    record Gnd(double meanToday, double mean, double floatToday, double floatMax, double sinkToday, double sinkMax) {
        /** What breaks the bounds, or null. */
        String broken(boolean man) {
            List<String> out = new ArrayList<>();
            if (mean > (man ? meanToday : 0.5 * meanToday) + 1e-9)
                out.add("the feet stand off the ground");
            if (man && floatMax > floatToday + 1e-9)
                out.add("a foot floats over a step");
            if (sinkMax > sinkToday + (man ? 0.02 : 0.0) + 1e-9)
                out.add("a foot sinks deeper into a step than today");
            return out.isEmpty() ? null : String.join(", ", out) + String.format(Locale.ROOT,
                " (mean gap %.3f > %.3f, worst float on the ground %.2f > %.2f, worst sink %.2f > %.2f)", meanToday, mean,
                floatToday, floatMax, sinkToday, sinkMax);
        }
    }

    /** One unit under measurement: its motion each server tick, and the packets a copy of its tracker sent. */
    static final class Probe {
        final Mob mob;
        final double gx, gz;
        final ServerEntity tracker;
        final VecDeltaCodec codec = new VecDeltaCodec();
        final List<ClientView.Sent> sent = new ArrayList<>();
        // option "every tick": the same unit tracked as the dragons are (updateInterval 1), its packets on their own
        final ServerEntity everyTick;
        final VecDeltaCodec codec1 = new VecDeltaCodec();
        final List<ClientView.Sent> sent1 = new ArrayList<>();
        // per tick: x, y, z, body yaw, on ground (1/0), yRot, the wanted point's distance across (-1: none) and rise
        final List<double[]> track = new ArrayList<>();
        final double x0, y0, z0;
        final float yaw0;
        int tick = 0, teleports = 0, rotOnly = 0, idleFrom = -1;
        boolean done = false;
        // the ground under the drawn body, once logged (null for a flyer), and its worst frames' story
        Gnd gnd = null;
        String gndStory = null;
        // how near the goal the walk's window ends: 3 blocks on a lane; in the crowd, which spreads round one point, 6 -
        // so a unit standing there while the others arrive is not counted as stopping on the way
        double arrive = 3;

        Probe(ServerLevel level, Mob mob, double gx, double gz) {
            this.mob = mob;
            this.gx = gx;
            this.gz = gz;
            this.x0 = mob.getX();
            this.y0 = mob.getY();
            this.z0 = mob.getZ();
            this.yaw0 = mob.getYRot();
            codec.setBase(mob.position());
            codec1.setBase(mob.position());
            EntityType<?> type = mob.getType();
            this.tracker = new ServerEntity(level, mob, type.updateInterval(), type.trackDeltas(),
                packet -> record(packet, codec, sent, true));
            this.everyTick = new ServerEntity(level, mob, 1, type.trackDeltas(), packet -> record(packet, codec1, sent1, false));
        }

        /** One packet as the client decodes it (ClientPacketListener.handleMoveEntity / handleTeleportEntity). */
        private void record(net.minecraft.network.protocol.Packet<?> packet, VecDeltaCodec c, List<ClientView.Sent> into,
                            boolean count) {
            if (packet instanceof ClientboundMoveEntityPacket p) {
                if (p.hasPosition()) {
                    Vec3 v = c.decode(p.getXa(), p.getYa(), p.getZa());
                    c.setBase(v);
                    into.add(new ClientView.Sent(tick, true, v.x, v.y, v.z, p.hasRotation(), p.getyRot() * 360f / 256f));
                } else if (p.hasRotation()) {
                    if (count)
                        rotOnly++;
                    into.add(new ClientView.Sent(tick, false, 0, 0, 0, true, p.getyRot() * 360f / 256f));
                }
            } else if (packet instanceof ClientboundTeleportEntityPacket p) {
                if (count)
                    teleports++;
                c.setBase(new Vec3(p.getX(), p.getY(), p.getZ()));
                into.add(new ClientView.Sent(tick, true, p.getX(), p.getY(), p.getZ(), true, p.getyRot() * 360f / 256f));
            }
        }

        /** Once per server tick, after the entities ticked (the real tracker runs at the next tick's start). */
        void tick() {
            // the game's own cadence first: a tracker's pass clears the knockback flag (hasImpulse) and the dirty data
            // that make it send off its cadence, and an every-tick tracker needs neither
            tracker.sendChanges();
            everyTick.sendChanges();
            // with the yaw the packets carry (yRot) and how far across and up the move control's wanted point is: a snap
            // turn toward a point almost straight above (a step's foot) is a turn toward noise
            // (MoveControl.tick sets MOVE_TO back to WAIT before it moves, so hasWanted() is false by now: the last
            // wanted point is read while the unit still has a path)
            net.minecraft.world.entity.ai.control.MoveControl mc = mob.getMoveControl();
            boolean pathing = !mob.getNavigation().isDone();
            double wantedAcross = pathing ? Math.hypot(mc.getWantedX() - mob.getX(), mc.getWantedZ() - mob.getZ()) : -1;
            double wantedUp = pathing ? mc.getWantedY() - mob.getY() : 0;
            track.add(new double[] { mob.getX(), mob.getY(), mob.getZ(), mob.yBodyRot, mob.onGround() ? 1 : 0,
                mob.getYRot(), wantedAcross, wantedUp });
            tick++;
        }

        double left() {
            double dx = gx - mob.getX(), dz = gz - mob.getZ();
            return Math.sqrt(dx * dx + dz * dz);
        }

        double walked() {
            double dx = mob.getX() - x0, dz = mob.getZ() - z0;
            return Math.sqrt(dx * dx + dz * dz);
        }

        int ticks() { return tick; }

        Probe arriveWithin(double blocks) {
            arrive = blocks;
            return this;
        }

        void markIdle() { idleFrom = tick; }

        /** The walk's window: from 5 ticks after the first step, until {@code arrive} blocks short of the goal (or the idle mark). */
        int[] window() {
            int from = -1, to = idleFrom >= 0 ? idleFrom : track.size();
            for (int k = 1; k < to; k++) {
                double[] a = track.get(k - 1), b = track.get(k);
                if (from < 0 && Math.hypot(b[0] - a[0], b[2] - a[2]) > 0.02)
                    from = k + 5;
                double l = Math.hypot(gx - b[0], gz - b[2]);
                if (from >= 0 && l < arrive) {
                    to = k;
                    break;
                }
            }
            return new int[] { from < 0 ? to : Math.min(from, to), to };
        }

        /** Logs the server line and the client line for this walk. */
        void log(String what) {
            int[] w = window();
            int from = w[0], to = w[1];
            List<Double> speed = new ArrayList<>();
            double maxYaw = 0, maxDy = 0, lastStep = 0;
            int reversals = 0, air = 0;
            List<Double> snapAcross = new ArrayList<>(), snapUp = new ArrayList<>();
            for (int k = Math.max(1, from); k < to; k++) {
                double[] a = track.get(k - 1), b = track.get(k);
                speed.add(Math.hypot(b[0] - a[0], b[2] - a[2]));
                double step = Mth.wrapDegrees(b[3] - a[3]);
                maxYaw = Math.max(maxYaw, Math.abs(step));
                if (Math.abs(step) > 1) {
                    if (lastStep != 0 && Math.signum(step) != Math.signum(lastStep))
                        reversals++;
                    lastStep = step;
                }
                maxDy = Math.max(maxDy, b[1] - a[1]);
                if (b[4] == 0)
                    air++;
                if (Math.abs(Mth.wrapDegrees(b[5] - a[5])) > SNAP_DEG && b[6] >= 0) {
                    snapAcross.add(b[6]);
                    snapUp.add(b[7]);
                }
            }
            // the yRot snaps (the yaw the packets carry, over SNAP_DEG in one tick): how many, how many toward a wanted
            // point under NEAR_ACROSS blocks across (straight above or below the unit), the median distance across and up
            int snaps = snapAcross.size(), near = 0;
            for (double v : snapAcross)
                if (v < NEAR_ACROSS)
                    near++;
            String snapPart = String.format(Locale.ROOT, " snap %d/%d a%.2f u%.2f;", snaps, near, median(snapAcross),
                median(snapUp));
            double[] s = speed.stream().mapToDouble(Double::doubleValue).toArray();
            double mean = Arrays.stream(s).average().orElse(0), var = 0;
            for (double v : s)
                var += (v - mean) * (v - mean);
            double[] sorted = s.clone();
            Arrays.sort(sorted);
            double median = sorted.length > 0 ? sorted[sorted.length / 2] : 0;
            int stops = 0;
            for (double v : s)
                if (v < 0.2 * median)
                    stops++;
            // the gaps between position packets over the window: 1, 2, 3, 4+ ticks
            int[] gaps = new int[4];
            int lastPos = -1, posPackets = 0, rotPackets = 0;
            for (ClientView.Sent p : sent) {
                if (p.tick() < from || p.tick() >= to)
                    continue;
                if (!p.hasPos()) {
                    rotPackets++;
                    continue;
                }
                posPackets++;
                if (lastPos >= 0)
                    gaps[Math.min(4, Math.max(1, p.tick() - lastPos)) - 1]++;
                lastPos = p.tick();
            }
            // one line a walk (CI folds every "[Motion]" line into ONE notice: a shard keeps only its first 10, ~4 KB each):
            // the server's walk, then the drawn walk per jittery clock
            String serverPart = String.format(Locale.ROOT, "[Motion] %s: %dt %.3fb/t cv %.2f stops %d; yaw max %.0f rev %d;"
                    + " rise %.2f air %d;%s pos %d gaps %d/%d/%d/%d rot %d tp %d", what, s.length, mean,
                mean > 0 ? Math.sqrt(var / Math.max(1, s.length)) / mean : 0, stops, maxYaw, reversals, maxDy, air,
                snapPart, posPackets, gaps[0], gaps[1], gaps[2], gaps[3], rotPackets, teleports);
            // the drawn walk per clock: vanilla's lerp (today) and the game's stamped playback (UnitPlayback, "buf"),
            // each as speed cv / % ticks frozen / lag behind the newest known server tick; then, right after the server's
            // part (CI cuts each line at 300 characters), the ground under the drawn body ("gnd", ground)
            double[] sx = new double[track.size()], sz = new double[track.size()];
            for (int k = 0; k < track.size(); k++) {
                sx[k] = track.get(k)[0];
                sz[k] = track.get(k)[2];
            }
            int n = Math.max(1, track.size()), interval = mob.getType().updateInterval();
            StringBuilder client = new StringBuilder();
            String groundPart = "";
            for (ClientView.Clock c : CLOCKS) {
                if (c.p50Ms() == 0 && c.fixedMs() == 0)
                    continue;   // the steady clock: every option draws it evenly (UnitPlaybackTest's control)
                long seed = 1234L + mob.getId() % 7;
                ClientView.Smooth today = ClientView.smoothness(ClientView.replay(sent, n, c, seed, x0, y0, z0, yaw0),
                    from, to, mean, sx, sz);
                ClientView.Drawn drawnBuf = ClientView.replayStamped(sent, n, c, seed, interval, x0, y0, z0, yaw0);
                ClientView.Smooth buffered = ClientView.smoothness(drawnBuf, from, to, mean, sx, sz);
                // (y: the largest drawn yaw change in one client tick, degrees; r: the largest drawn rise in one)
                client.append(String.format(Locale.ROOT, " | %s %.2f/%.1f y%.0f r%.2f buf %.2f/%.1f/%.1ft y%.0f r%.2f",
                    c.name(), today.cv(), today.frozenPct(), today.maxYawStep(), today.maxDy(), buffered.cv(),
                    buffered.frozenPct(), buffered.lagTicks(), buffered.maxYawStep(), buffered.maxDy()));
                if (c.name().equals("700u") && !(mob instanceof Unit u && u.isFlyingUnit()))
                    groundPart = ground(drawnBuf, from, to);
            }
            long e1 = sent1.stream().filter(p -> p.hasPos() && p.tick() >= from && p.tick() < to).count();
            ReignOfNether.LOGGER.warn(serverPart + groundPart + client + " | e1 pos " + e1);
        }

        /**
         * The ground under the drawn body (GroundTilt, step 3 A) over the walk's window, for the playback's drawn path:
         * the four foot pads drawn as today (upright at the drawn position) and with the ground's tilt and drop, against
         * this lane's real blocks - "gnd" the mean |gap| per pad, "f" the worst float over the frames that show the unit
         * on the ground ({@link #drawnOnGround}), "s" the worst sink over all, each today > tilted, blocks. The ground's
         * "on the ground" is the client's (the newest tick heard), as AnimStates reads {@code onGround()}.
         */
        private String ground(ClientView.Drawn d, int from, int to) {
            GroundTilt.Ground g = GroundTilt.of(mob.level());
            float[] s = new float[AnimState.STRIDE];
            float[] target = new float[3];
            double[] feet = new double[4], today = new double[4];
            int n = d.x.length;
            // each client tick, for the worst frames' story
            double[] tgt = new double[n], drop = new double[n], sink = new double[n], flt = new double[n],
                sinkToday = new double[n];
            boolean[] newest = new boolean[n], settled = new boolean[n];
            double sum = 0, sumToday = 0, pads = 0, padsToday = 0, floatMax = 0, floatToday = 0, sinkMax = 0,
                sinkTodayMax = 0;
            int worstSink = -1, worstFloat = -1;
            float width = mob.getBbWidth();
            boolean fresh = true;
            for (int c = 0; c < n; c++) {
                int at = Math.max(0, Math.min(d.serverTickAt[c], track.size() - 1));
                boolean grounded = track.get(at)[4] == 1;
                GroundTilt.target(g, d.x[c], d.y[c], d.z[c], d.yaw[c], width, grounded, target);
                GroundTilt.step(s, 0, target, d.y[c], fresh);
                fresh = false;
                newest[c] = grounded;
                tgt[c] = target[2];
                drop[c] = GroundTilt.dropAt(s, 0, 1f);
                if (d.serverTickAt[c] < from || d.serverTickAt[c] >= to)
                    continue;
                GroundTilt.feet(g, d.x[c], d.y[c], d.z[c], d.yaw[c], width, GroundTilt.pitchAt(s, 0, 1f),
                    GroundTilt.rollAt(s, 0, 1f), GroundTilt.dropAt(s, 0, 1f), feet);
                GroundTilt.feet(g, d.x[c], d.y[c], d.z[c], d.yaw[c], width, 0f, 0f, 0f, today);
                settled[c] = drawnOnGround(d, c, at);
                sink[c] = feet[1];
                flt[c] = feet[0];
                sinkToday[c] = today[1];
                sum += feet[2];
                pads += feet[3];
                sumToday += today[2];
                padsToday += today[3];
                if (settled[c]) {
                    if (feet[0] > floatMax) {
                        floatMax = feet[0];
                        worstFloat = c;
                    }
                    floatToday = Math.max(floatToday, today[0]);
                }
                if (feet[1] > sinkMax) {
                    sinkMax = feet[1];
                    worstSink = c;
                }
                sinkTodayMax = Math.max(sinkTodayMax, today[1]);
            }
            gnd = new Gnd(sumToday / Math.max(1, padsToday), sum / Math.max(1, pads), floatToday, floatMax, sinkTodayMax,
                sinkMax);
            StringBuilder story = new StringBuilder();
            if (worstSink >= 0 && sinkMax > 0.005)
                story.append(" | sink ").append(frames(d, g, width, worstSink, tgt, drop, sink, flt, sinkToday, newest,
                    settled));
            if (worstFloat >= 0 && floatMax > 0.005)
                story.append(" | float ").append(frames(d, g, width, worstFloat, tgt, drop, sink, flt, sinkToday, newest,
                    settled));
            gndStory = story.length() == 0 ? null : story.toString();
            return String.format(Locale.ROOT, " gnd %.2f>%.2f f%.2f>%.2f s%.2f>%.2f;", gnd.meanToday(), gnd.mean(),
                gnd.floatToday(), gnd.floatMax(), gnd.sinkToday(), gnd.sinkMax());
        }

        /**
         * Whether a drawn frame shows the unit on the ground: the server tick whose position the frame is drawn nearest
         * (the playback draws a few ticks behind the newest it heard), and the ticks either side of it, were all on the
         * ground - the hop up a step and a fall are not.
         */
        private boolean drawnOnGround(ClientView.Drawn d, int c, int at) {
            int best = at;
            double bestDist = Double.MAX_VALUE;
            for (int k = at; k >= Math.max(0, at - 12); k--) {
                double[] p = track.get(k);
                double dist = Math.hypot(p[0] - d.x[c], p[2] - d.z[c]);
                if (dist < bestDist) {
                    bestDist = dist;
                    best = k;
                }
            }
            for (int k = Math.max(0, best - 1); k <= Math.min(track.size() - 1, best + 1); k++)
                if (track.get(k)[4] != 1)
                    return false;
            return true;
        }

        /**
         * A worst frame's story: where it is drawn, each foot pad's ground against its drawn height (front right, front
         * left, back right, back left; "-" none), then the client ticks from 6 before to 2 after it: [offset, drawn
         * height and its move, on the ground by the newest tick heard (g/a) and as drawn (G/A), the drop's target and
         * value, today's > the tilted sink, the tilted float].
         */
        private String frames(ClientView.Drawn d, GroundTilt.Ground g, float width, int c, double[] tgt, double[] drop,
                              double[] sink, double[] flt, double[] sinkToday, boolean[] newest, boolean[] settled) {
            StringBuilder b = new StringBuilder(String.format(Locale.ROOT, "c%d srv%d x%.2f z%.2f yaw%.0f pads", c,
                d.serverTickAt[c], d.x[c], d.z[c], d.yaw[c]));
            float yr = d.yaw[c] * Mth.DEG_TO_RAD;
            double fx = -Mth.sin(yr), fz = Mth.cos(yr), rx = -Mth.cos(yr), rz = -Mth.sin(yr);
            double along = GroundTilt.SPAN * width, across = GroundTilt.SIDE * width, r = GroundTilt.footRadius(width);
            for (int a = 1; a >= -1; a -= 2)
                for (int l = 1; l >= -1; l -= 2) {
                    double h = GroundTilt.foot(g, d.x[c] + a * fx * along + l * rx * across,
                        d.z[c] + a * fz * along + l * rz * across, r, d.y[c]);
                    b.append(Double.isNaN(h) ? " -" : String.format(Locale.ROOT, " %+.2f", h - d.y[c]));
                }
            b.append(':');
            for (int k = Math.max(0, c - 6); k <= Math.min(d.x.length - 1, c + 2); k++)
                b.append(String.format(Locale.ROOT, " [%d y%.2f%+.2f %s%s t%.2f d%.2f s%.2f>%.2f f%.2f]", k - c, d.y[k],
                    k > 0 ? d.y[k] - d.y[k - 1] : 0.0, newest[k] ? "g" : "a", settled[k] ? "G" : "A", tgt[k], drop[k],
                    sinkToday[k], sink[k], flt[k]));
            return b.toString();
        }

        /** After the idle mark: horizontal distance moved, degrees the body turned, position packets sent. */
        double[] idle() {
            double moved = 0, turned = 0;
            for (int k = Math.max(1, idleFrom + 1); k < track.size(); k++) {
                double[] a = track.get(k - 1), b = track.get(k);
                moved += Math.hypot(b[0] - a[0], b[2] - a[2]);
                turned += Math.abs(Mth.wrapDegrees(b[3] - a[3]));
            }
            int packets = 0;
            for (ClientView.Sent p : sent)
                if (p.hasPos() && p.tick() >= idleFrom)
                    packets++;
            return new double[] { moved, turned, packets };
        }
    }
}
