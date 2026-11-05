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
 * feet stand against its blocks ("gnd", step 3 A). The crowd adds what idle units do after arriving. The asserts only guard the measurement itself (the unit moved); the bounds come with
 * the fixes, from these numbers.
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
        lanes(helper, "infantry Vindicator", EntityRegistrar.VINDICATOR_UNIT::get, 16, false);
    }

    @GameTest(template = ARENA, timeoutTicks = SETUP + WALK_TICKS + 80, batch = BATCH)
    public static void motion_measure_beast_on_flat_and_stepped_ground(GameTestHelper helper) {
        lanes(helper, "beast Mossback Bison", EntityRegistrar.MOSSBACK_BISON_UNIT::get, 17, false);
    }

    @GameTest(template = ARENA, timeoutTicks = SETUP + WALK_TICKS + 80, batch = BATCH)
    public static void motion_measure_large_unit_on_flat_and_stepped_ground(GameTestHelper helper) {
        lanes(helper, "large War Mammoth", EntityRegistrar.WAR_MAMMOTH_UNIT::get, 18, false);
    }

    @GameTest(template = ARENA, timeoutTicks = SETUP + WALK_TICKS + 80, batch = BATCH)
    public static void motion_measure_flyer_over_flat_and_stepped_ground(GameTestHelper helper) {
        lanes(helper, "flyer Gyrocopter", EntityRegistrar.GYROCOPTER_GUNSHIP_UNIT::get, 19, true);
    }

    // ------------------------------------------------------------------------------------------------ the lanes

    private static void lanes(GameTestHelper helper, String kind, Supplier<? extends EntityType<? extends Mob>> type,
                              int padIndex, boolean flyer) {
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
            List<String> problems = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                String ground = i == 0 ? "flat" : "stepped";
                Probe p = probes[i];
                p.log(kind + " " + ground);
                if (p.walked() < 8)
                    problems.add(ground + " lane: walked only " + String.format(Locale.ROOT, "%.1f", p.walked())
                        + " blocks in " + p.ticks() + " ticks");
            }
            if (!problems.isEmpty())
                helper.fail(kind + ": the measurement has no walk to judge: " + String.join("; ", problems));
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

    // ------------------------------------------------------------------------------------------------ one unit

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
         * this lane's real blocks - "gnd" the mean |gap| per pad, "f" the worst float, "s" the worst sink, each today >
         * tilted, blocks.
         */
        private String ground(ClientView.Drawn d, int from, int to) {
            GroundTilt.Ground g = GroundTilt.of(mob.level());
            float[] s = new float[AnimState.STRIDE];
            float[] target = new float[3];
            double[] feet = new double[4], today = new double[4];
            double sum = 0, sumToday = 0, pads = 0, padsToday = 0, floatMax = 0, floatToday = 0, sinkMax = 0,
                sinkToday = 0;
            float width = mob.getBbWidth();
            boolean fresh = true;
            for (int c = 0; c < d.x.length; c++) {
                int at = Math.max(0, Math.min(d.serverTickAt[c], track.size() - 1));
                boolean grounded = track.get(at)[4] == 1;
                GroundTilt.target(g, d.x[c], d.y[c], d.z[c], d.yaw[c], width, grounded, target);
                GroundTilt.step(s, 0, target, fresh);
                fresh = false;
                if (d.serverTickAt[c] < from || d.serverTickAt[c] >= to)
                    continue;
                GroundTilt.feet(g, d.x[c], d.y[c], d.z[c], d.yaw[c], width, GroundTilt.pitchAt(s, 0, 1f),
                    GroundTilt.rollAt(s, 0, 1f), GroundTilt.dropAt(s, 0, 1f), feet);
                GroundTilt.feet(g, d.x[c], d.y[c], d.z[c], d.yaw[c], width, 0f, 0f, 0f, today);
                sum += feet[2];
                pads += feet[3];
                sumToday += today[2];
                padsToday += today[3];
                floatMax = Math.max(floatMax, feet[0]);
                floatToday = Math.max(floatToday, today[0]);
                sinkMax = Math.max(sinkMax, feet[1]);
                sinkToday = Math.max(sinkToday, today[1]);
            }
            return String.format(Locale.ROOT, " gnd %.2f>%.2f f%.2f>%.2f s%.2f>%.2f;", sumToday / Math.max(1, padsToday),
                sum / Math.max(1, pads), floatToday, floatMax, sinkToday, sinkMax);
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
