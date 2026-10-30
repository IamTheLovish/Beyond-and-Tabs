package com.solegendary.reignofnether.perf;

import com.solegendary.reignofnether.ReignOfNether;

import net.minecraft.server.MinecraftServer;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.Arrays;

/**
 * Names the server-tick cost of our own systems (Oct 21 perf plan, PR 1). Nothing measured a match as a whole before:
 * lovish's 3-player log said the tick ran at 55-105 ms with ~600 units, but not WHICH system. Two readings come out of
 * one pair of calls around each system:
 * <ul>
 *   <li>a {@code [Sys]} log line every 10 s while the server is slow (see {@link #SLOW_P95_MS}): the milliseconds each
 *       system spent per tick, its self time (sections nested in it taken out, {@link SysTimer}), the worst tick, and
 *       how much of the whole tick the timed systems are - the rest is vanilla, other mods and GC;</li>
 *   <li>a {@code bat:<name>} section in vanilla's profiler (the metrics recorder behind /perf and the automatic lag
 *       capture, ServerTickProfile), so a profile report names our systems instead of burying them under
 *       {@code minecraft:<unit type> > goalSelector}.</li>
 * </ul>
 *
 * Use at a call site: {@code int t = Sys.begin(Sys.ATTACKER); try { ... } finally { Sys.end(t); }}.
 *
 * Cost when the server is healthy and nothing is recording: one volatile boolean read per begin, and nothing else (no
 * clock, no allocation) - safe in a per-unit path. Measuring switches on by itself when the last 100 ticks' p95
 * reaches {@link #SLOW_P95_MS} (checked once a second), while a lag capture or /rts-perf runs, and in tests
 * ({@link #force}); it costs two nanoTime calls and an inactive-profiler call per section call, about 0.1 us.
 *
 * Server thread only: Unit.tick also runs on the client, and a LAN host shares the JVM, so a call from any other
 * thread is ignored.
 */
public final class Sys {
    private Sys() { }

    // the sections. Names are what [Sys] prints; the profiler gets "bat:" + name.
    public static final int UNIT = 0;              // Unit.tick, minus the systems below it
    public static final int ATTACKER = 1;          // AttackerUnit.tick, minus the scans below it
    public static final int ATTACK_MOVE = 2;       // an attack-moving unit looking for a target
    public static final int IDLE_AGGRO = 3;        // an idle unit looking for something to attack
    public static final int RETARGET = 4;          // a fighting unit checking for a closer enemy
    public static final int ENTITY_SCAN = 5;       // MiscUtil.findClosestAttackableEntity
    public static final int BUILDING_SCAN = 6;     // MiscUtil.findClosestAttackableBuilding
    public static final int SEPARATION = 7;        // bodies stepping apart
    public static final int STUCK_NUDGE = 8;       // the nudge for a unit that stopped making progress
    public static final int UNSTICK = 9;           // freeing a unit trapped in a block
    public static final int REACTIONS = 10;        // Kite and Brace
    public static final int PATHFINDER = 11;       // the main-thread half of the pathfinder (the workers are not timed)
    public static final int BUILDINGS = 12;        // BuildingPlacement.tick for every building
    public static final int BOTS = 13;             // bot brains thinking
    public static final int UNIT_SYNC = 14;        // UnitSyncBatcher: the delta packets to every player
    public static final int COMMAND_QUEUES = 15;   // queued orders starting their next step
    public static final int AUTO_CASTER = 16;      // units casting their abilities by themselves
    public static final int UNIT_GRID = 17;        // the rebuild of the shared unit spatial grid
    public static final int CHUNK_LOADER = 18;     // keeping the chunks around units loaded
    public static final int ECONOMY = 19;          // income, stalls and the resource sync
    public static final int FOG_OF_WAR = 20;       // the server half of fog of war
    public static final int PUSH_SCAN = 21;        // a unit's vanilla push looking up the bodies it overlaps
    public static final int MOUNT_SCAN = 22;       // a unit looking for a mount (MountGoal autofind)
    public static final int NEUTRAL_AGGRO = 23;    // the once-a-second query for the wild mobs near each unit (neutral aggro)
    public static final int TARGET_GRID = 24;      // the rebuild of TargetScan's per-tick grid of living entities
    // vanilla's own work, timed around the calls into it (mixin/ServerLevelMixin, LivingEntityMixin, EntityMixin). Every
    // id from VANILLA_FROM on prints in the "vanilla" group of the [Sys] line, not in "ours".
    public static final int VANILLA_UNIT = 25;     // ServerLevel.tickNonPassenger of a Unit, minus everything timed inside it
    public static final int VANILLA_OTHER = 26;    // ServerLevel.tickNonPassenger of any other entity, likewise
    public static final int TRAVEL = 27;           // LivingEntity.travel inside aiStep (movement and its collision)
    public static final int AI_GOALS = 28;         // LivingEntity.serverAiStep inside aiStep (goals, navigation, controls)
    public static final int BASE_TICK = 29;        // Entity.baseTick inside Entity.tick
    public static final int CHUNKS = 30;           // ServerChunkCache.tick inside ServerLevel.tick
    public static final int LEVEL_REST = 31;       // the rest of ServerLevel.tick: block and fluid ticks, block entities, entity manager, raids
    public static final int VANILLA_FROM = VANILLA_UNIT;
    public static final int SECTIONS = 32;

    static final String[] NAMES = {
        "unit", "attacker", "attacker/attackMove", "attacker/idleAggro", "attacker/retarget", "attacker/entityScan",
        "attacker/buildingScan", "separation", "stuckNudge", "unstick", "reactions", "pathfinder", "buildings", "bots",
        "unitSync", "commandQueues", "autoCaster", "unitGrid", "chunkLoader", "economy", "fogOfWar", "pushScan",
        "mountScan", "neutralAggro", "targetGrid",
        "unitTick", "otherEntities", "travel", "aiGoals", "baseTick", "chunks", "levelRest" };
    private static final String[] PROFILER_NAMES = new String[SECTIONS];
    static {
        for (int i = 0; i < SECTIONS; i++)
            PROFILER_NAMES[i] = BatSections.PREFIX + NAMES[i];
    }

    /** Measuring starts when the p95 of the last 100 ticks reaches this (the tick budget is 50 ms), and stops below OFF. */
    static final double SLOW_P95_MS = 25.0, SLOW_OFF_P95_MS = 18.0;
    static final int EVALUATE_EVERY_TICKS = 20;
    /** A [Sys] line needs at least this many measured ticks (a second) behind it. */
    static final int MIN_TICKS = 20;

    private static volatile boolean on = false;
    private static volatile Thread serverThread = null;
    private static final SysTimer TIMER = new SysTimer(SECTIONS, System::nanoTime);
    private static final ProfilerFiller[] PUSHED = new ProfilerFiller[SysTimer.MAX_DEPTH + 1];

    // all below: server thread
    private static boolean forced = false, slow = false;
    /** Who is forcing measuring on right now ({@link #force}); forced = not empty. */
    private static final java.util.Set<Object> HOLDERS = new java.util.HashSet<>();
    private static int captureRefs = 0;
    private static double windowTickMs = 0;
    /** The last [Sys] line written (tests and the lag-capture summary read it). */
    public static volatile String lastLine = null;
    /** The share of the mean tick that the timed sections (ours and vanilla) covered in the last line written (tests). */
    public static volatile double lastCovered = 0;

    /** Opens a section; give the result to {@link #end}. Returns 0 (and does nothing) unless measuring on the server thread. */
    public static int begin(int id) {
        if (!on || Thread.currentThread() != serverThread)
            return 0;
        int token = TIMER.begin(id);
        if (token > 0) {
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            ProfilerFiller profiler = server == null ? null : server.getProfiler();
            if (profiler != null)
                profiler.push(PROFILER_NAMES[id]);
            PUSHED[token] = profiler;
        }
        return token;
    }

    public static void end(int token) {
        if (token <= 0)
            return;
        ProfilerFiller profiler = PUSHED[token];
        if (profiler != null) {
            PUSHED[token] = null;
            profiler.pop();
        }
        TIMER.end(token);
    }

    /** True while sections are being timed (the lag capture and the tests ask). */
    public static boolean isMeasuring() {
        return on;
    }

    /**
     * Switch measuring on (true) or off (false) for one holder - a test - whatever the tick times are. Measuring stays
     * forced while ANY holder holds it: the game-test server runs a batch's tests side by side, and one global flag let
     * a test that finished switch measuring off in the middle of another's window (unit_push_neighbour_lookups_are_counted
     * counted 174 of 1600 lookups on main 5421b13f, Oct 26). Releasing twice, or a holder that never held, is harmless.
     * Call from the server thread.
     */
    public static void force(Object holder, boolean value) {
        serverThread = Thread.currentThread();
        if (value)
            HOLDERS.add(holder);
        else
            HOLDERS.remove(holder);
        forced = !HOLDERS.isEmpty();
        refresh();
    }

    /** A lag capture or /rts-perf starts (true) or ends (false): time the sections for its whole length. */
    static void capture(boolean value) {
        captureRefs = Math.max(0, captureRefs + (value ? 1 : -1));
        refresh();
    }

    private static void refresh() {
        boolean now = forced || captureRefs > 0 || slow;
        if (now && !on) {
            TIMER.reset();
            windowTickMs = 0;
        }
        on = now;
    }

    /** End of a server tick (ServerTickProfile, server thread): closes the tick and re-checks whether to measure. */
    static void onServerTickEnd(MinecraftServer server, double lastTickMs) {
        serverThread = Thread.currentThread();
        if (on) {
            TIMER.tickEnd();
            windowTickMs += lastTickMs;
        }
        if (server.getTickCount() % EVALUATE_EVERY_TICKS != 0)
            return;
        double[] sorted = ServerTickProfile.sortedMs(server.tickTimes);
        double p95 = ServerTickProfile.percentile(sorted, 0.95);
        boolean wasSlow = slow;
        slow = wasSlow ? p95 >= SLOW_OFF_P95_MS : p95 >= SLOW_P95_MS;
        if (slow != wasSlow)
            ReignOfNether.LOGGER.info("[Sys] the server is {} (tick p95 {} ms): per-system timing {}", slow ? "slow" : "healthy again",
                ServerTickProfile.fmt(p95), slow ? "on" : "off");
        refresh();
    }

    /**
     * Every 10 s (ServerTickProfile's cadence): writes the [Sys] line for the window if enough ticks were measured,
     * and starts a new window. Returns the line, or null when there was nothing to say.
     */
    static String report() {
        String line = null;
        if (TIMER.ticks >= MIN_TICKS) {
            line = write();
            ReignOfNether.LOGGER.info(line);
        } else {
            GcLog.takeSysWindowMs();
        }
        TIMER.reset();
        windowTickMs = 0;
        return line;
    }

    /**
     * The [Sys] line for what was measured since the last report - without the MIN_TICKS rule - and a new window. For
     * the GameTest, which measures a few dozen ticks.
     */
    public static String snapshot() {
        if (TIMER.ticks == 0)
            return null;
        String line = write();
        TIMER.reset();
        windowTickMs = 0;
        return line;
    }

    private static String write() {
        double meanMs = windowTickMs / TIMER.ticks;
        String line = SysReport.line("[Sys]", "tick", NAMES, TIMER, meanMs, VANILLA_FROM, GcLog.takeSysWindowMs());
        lastCovered = meanMs <= 0 ? 0 : TIMER.totalSelfNs() / 1.0e6 / TIMER.ticks / meanMs;
        lastLine = line;
        return line;
    }

    /** Calls recorded for a section since the last report (tests). */
    public static long calls(int id) {
        return TIMER.calls[id];
    }

    /**
     * A section's self nanoseconds and calls while measuring, summed since the server started - never reset by a
     * report, so a benchmark can difference two readings across the 10-second [Sys] windows. {calls, nanos}.
     */
    public static long[] lifetime(int id) {
        return new long[] { TIMER.lifetimeCalls[id], TIMER.lifetimeNs[id] };
    }

    static void onServerStopping() {
        on = false;
        serverThread = null;
        forced = false;
        HOLDERS.clear();
        captureRefs = 0;
        slow = false;
        windowTickMs = 0;
        Arrays.fill(PUSHED, null);
        TickGates.onServerStopping();
        TIMER.reset();
    }
}
