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
    public static final int MOUNT_SCAN = 21;       // a unit looking for a mount (MountGoal autofind)
    public static final int NEUTRAL_AGGRO = 22;    // the once-a-second query for the wild mobs near each unit (neutral aggro)
    public static final int SECTIONS = 23;

    static final String[] NAMES = {
        "unit", "attacker", "attacker/attackMove", "attacker/idleAggro", "attacker/retarget", "attacker/entityScan",
        "attacker/buildingScan", "separation", "stuckNudge", "unstick", "reactions", "pathfinder", "buildings", "bots",
        "unitSync", "commandQueues", "autoCaster", "unitGrid", "chunkLoader", "economy", "fogOfWar",
        "mountScan", "neutralAggro" };
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
    private static int captureRefs = 0;
    private static double windowTickMs = 0;
    /** The last [Sys] line written (tests and the lag-capture summary read it). */
    public static volatile String lastLine = null;

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

    /** Switch measuring on for a test, whatever the tick times are. Call from the server thread. */
    public static void force(boolean value) {
        serverThread = Thread.currentThread();
        forced = value;
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
            line = SysReport.line("[Sys]", "tick", NAMES, TIMER, windowTickMs / TIMER.ticks);
            lastLine = line;
            ReignOfNether.LOGGER.info(line);
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
        String line = SysReport.line("[Sys]", "tick", NAMES, TIMER, windowTickMs / TIMER.ticks);
        lastLine = line;
        TIMER.reset();
        windowTickMs = 0;
        return line;
    }

    /** Calls recorded for a section since the last report (tests). */
    public static long calls(int id) {
        return TIMER.calls[id];
    }

    static void onServerStopping() {
        on = false;
        serverThread = null;
        forced = false;
        captureRefs = 0;
        slow = false;
        windowTickMs = 0;
        Arrays.fill(PUSHED, null);
        TIMER.reset();
    }
}
