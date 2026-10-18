package com.solegendary.reignofnether.match;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.maps.MapBuilder;
import com.solegendary.reignofnether.maps.MapLayout;
import com.solegendary.reignofnether.maps.MapStateClientboundPacket;
import com.solegendary.reignofnether.startpos.ArenaPregen;
import com.solegendary.reignofnether.startpos.BattlefieldWall;
import com.solegendary.reignofnether.startpos.StartAreaClearing;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Everything heavy, before the start phase (inbox/spec-3, section 2.1). One job ticked by the server while the
 * phase is MAP_RISING: {@code FORGE} (MapBuilder, pre-made maps only) -> {@code CLEAR} (the start discs and lanes at
 * every slot) -> {@code WALL} (the arena wall, at the preparation budget) -> {@code SCOUT} (every chunk of the arena
 * generated, ArenaPregen with eight in flight) -> {@code LIGHT} (the light engine's backlog drained) -> READY ->
 * {@link MatchState#mapRaised}. BAR's map is on disk and lit before the start boxes appear; here, until this, the
 * match BEGAN and only then did the server raise 2376 wall columns at 40 a tick and generate 1156 chunks during the
 * first half minute of play (lovish's playtest, Oct 17 16:04: "Can't keep up! Running 2008ms or 40 ticks behind",
 * units and animations stuttering), and he had asked that "the map should load before the players join".
 * <p>
 * The phase stays MAP_RISING throughout (MatchPhase's names and table are saved and pinned); what MatchState saves as
 * {@code builderStage} is {@link #savedStage}: 0-3 the forge (MapBuilder's own stage), {@value #SAVED_CLEAR} clear,
 * {@value #SAVED_WALL} wall, {@value #SAVED_SCOUT} scout, {@value #SAVED_LIGHT} light, {@value #SAVED_READY} ready.
 * A reload re-runs the job from the saved stage; every stage is idempotent (the builder writes only blocks that
 * differ, the clearing finds the ground already clear, the wall is deterministic from the setup's seed and carries
 * on from the column count the save kept - {@link #savedDetail} - a generated chunk is simply loaded), so the
 * resume closes the lifecycle proposal "battlefield forming is memory only": the forming record (centre, radius,
 * material) IS the MatchSetup, and the columns done ride with the stage.
 * <p>
 * The world work goes through {@link Work}, so the game tests drive a job through every stage and every resume with
 * a stand-in and never generate a chunk (headless servers have no human online, which ArenaPregen requires); the
 * live job uses {@link LiveWork}. A stage that throws cancels the match cleanly to LOBBY with a chat line, as a
 * setup that cannot resume does ({@link MatchState#prepFailed}). Game thread only.
 */
public final class MatchPrep {

    /** The stages in order. Carried to the client as an ordinal (MapStateClientboundPacket); names are not saved. */
    public enum Stage { FORGE, CLEAR, WALL, SCOUT, LIGHT, READY }

    /** The saved stage numbers past the forge's own 0-3 (MatchState's {@code builderStage}). */
    public static final int SAVED_CLEAR = 4, SAVED_WALL = 5, SAVED_SCOUT = 6, SAVED_LIGHT = 7, SAVED_READY = 8;

    /** Wall columns per tick while preparing: four times the in-play speed, nobody is playing yet. */
    public static final int WALL_COLUMNS_PER_TICK = 160;
    /** Arena chunks generating at once while preparing (ArenaPregen's in-play default is 2). */
    public static final int SCOUT_IN_FLIGHT = 8;
    /** The scouted disc reaches this far past the wall, so a camera at the edge never sees a hole. */
    public static final int SCOUT_MARGIN = 32;
    /** The light stage waits at most this long (5 s) for the engine to report no backlog - never a trap. */
    public static final int LIGHT_CAP_TICKS = 100;
    /** Progress goes to every client this often (PrepProgress in MapStateClientboundPacket). */
    static final int PROGRESS_EVERY_TICKS = 10;

    /** What the clients are shown: the stage and its 0..100. */
    public record PrepProgress(Stage stage, int pct) { }

    /** The world work of each stage. The live one is {@link LiveWork}; the game tests record and simulate. */
    public interface Work {
        /** Start MapBuilder from its stage {@code fromStage} (0 the terrain, 3 the economy only). */
        void startForge(MatchSetup setup, int fromStage);

        boolean forging();

        /** MapBuilder's stage while forging (0-3), 4 once it stamped the economy, -1 when idle. */
        int forgeStage();

        int forgePct();

        /** The start discs and lanes at every slot, synchronously, the economy left alone. */
        void clear(MatchSetup setup);

        /** Raise the wall from column {@code fromColumn} (0 fresh; a resume's saved count). */
        void startWall(MatchSetup setup, int fromColumn);

        boolean wallBuilding();

        int wallDone();

        int wallTotal();

        void startScout(MatchSetup setup);

        boolean scouting();

        int scoutDone();

        int scoutTotal();

        /** Whether the level's light engine still has updates queued. */
        boolean lightBusy();

        /** Drop whatever this work has running. */
        void cancel();
    }

    // ------------------------------------------------------------------------------------------ the live job

    @Nullable private static MatchPrep live = null;

    /**
     * Starts the live job for the state's setup; {@code fromStage} -1 for a fresh start, else the saved stage, with
     * {@code fromDetail} its saved detail (the wall's columns done).
     */
    public static void begin(ServerLevel level, MatchState state, int fromStage, int fromDetail) {
        MatchPrep old = live;
        if (old != null)
            old.cancel();
        MatchPrep job = new MatchPrep(state, new LiveWork(level), fromStage, fromDetail);
        live = job;
        job.start();
        if (job.finished || job.cancelled)
            live = null;
    }

    /** One server tick of the live job (MatchServerEvents), and its progress to the clients every 10 ticks. */
    public static void tickLive() {
        MatchPrep job = live;
        if (job == null)
            return;
        job.tick();
        if (job.finished || job.cancelled) {
            if (live == job)
                live = null;
            MapStateClientboundPacket.broadcast();
        } else if (job.ticks % PROGRESS_EVERY_TICKS == 0) {
            MapStateClientboundPacket.broadcast();
        }
    }

    /** The live job's saved stage for MatchState.save, -1 when none runs. */
    public static int liveStage() {
        MatchPrep job = live;
        return job == null ? -1 : job.savedStage();
    }

    /** The live job's saved detail for MatchState.save (the wall's columns done), 0 when none runs. */
    public static int liveDetail() {
        MatchPrep job = live;
        return job == null ? 0 : job.savedDetail();
    }

    /** The live job's progress for the clients, null when none runs. */
    @Nullable
    public static PrepProgress liveProgress() {
        MatchPrep job = live;
        return job == null ? null : job.progress();
    }

    /** Drop the live job (reset, a setup that cannot resume, a stage that threw). */
    public static void cancelLive() {
        MatchPrep job = live;
        live = null;
        if (job != null)
            job.cancel();
    }

    /** Server stop: the next world opened in this game starts with no job (the snapshot was taken before). */
    public static void dropLive() {
        live = null;
    }

    // ------------------------------------------------------------------------------------------ one job

    private final MatchState state;
    private final MatchSetup setup;
    private final Work work;
    private final int fromStage, fromDetail;
    private Stage stage = Stage.FORGE;
    private int ticks = 0;
    private int lightTicks = 0;
    private long startedAtMs = 0;
    private boolean started = false, finished = false, cancelled = false;

    /**
     * A job for {@code state}'s setup over {@code work}; {@code fromStage} -1 fresh, else the saved stage to resume
     * from, {@code fromDetail} that stage's saved detail (the wall's columns done). Nothing runs until {@link #start}.
     */
    public MatchPrep(MatchState state, Work work, int fromStage, int fromDetail) {
        this.state = state;
        this.setup = state.setup();
        this.work = work;
        this.fromStage = fromStage;
        this.fromDetail = Math.max(0, fromDetail);
    }

    public Stage stage() {
        return stage;
    }

    public boolean isFinished() {
        return finished;
    }

    public boolean isCancelled() {
        return cancelled;
    }

    /** Enters the first stage (the saved one on a resume) and starts its work. */
    public void start() {
        if (started || setup == null) {
            if (setup == null)
                fail(new IllegalStateException("no setup to prepare"));
            return;
        }
        started = true;
        startedAtMs = System.currentTimeMillis();
        try {
            if (fromStage >= SAVED_READY) {
                // saved on its last tick: nothing left but the hand-over (MatchState.resumePlan goes AFTER_MAP
                // before it gets here, so this is only a guard)
                stage = Stage.READY;
                finish();
                return;
            }
            Stage first;
            if (fromStage >= SAVED_LIGHT)
                first = Stage.LIGHT;
            else if (fromStage >= SAVED_SCOUT)
                first = Stage.SCOUT;
            else if (fromStage >= SAVED_WALL)
                first = Stage.WALL;
            else if (fromStage >= SAVED_CLEAR || !setup.raisesMap())
                first = Stage.CLEAR;
            else
                first = Stage.FORGE;
            ReignOfNether.LOGGER.info("[Prep] {} for {} sides at [{}, {}], radius {}: starting at {}{}",
                setup.preset.displayName, setup.playing().size(), setup.cx, setup.cz, setup.layout().radius, first,
                fromStage >= 0 ? " (resumed from saved stage " + fromStage + ")" : "");
            stage = first;
            enter(first);
        } catch (Exception e) {
            fail(e);
        }
    }

    /** One tick: waits on the running stage's work, or runs the next one. */
    public void tick() {
        if (!started || finished || cancelled)
            return;
        ticks++;
        try {
            switch (stage) {
                case FORGE -> {
                    if (!work.forging())
                        enter(Stage.CLEAR);
                }
                case CLEAR -> {
                    work.clear(setup);
                    enter(Stage.WALL);
                }
                case WALL -> {
                    if (!work.wallBuilding()) {
                        if (work.wallDone() != work.wallTotal())
                            ReignOfNether.LOGGER.warn("[Prep] the wall stopped at {} of {} columns", work.wallDone(),
                                work.wallTotal());
                        enter(Stage.SCOUT);
                    }
                }
                case SCOUT -> {
                    if (!work.scouting())
                        enter(Stage.LIGHT);
                }
                case LIGHT -> {
                    lightTicks++;
                    if (!work.lightBusy() || lightTicks >= LIGHT_CAP_TICKS) {
                        if (lightTicks >= LIGHT_CAP_TICKS)
                            ReignOfNether.LOGGER.warn("[Prep] the light engine was still busy after {} ticks; going on",
                                lightTicks);
                        enter(Stage.READY);
                        finish();
                    }
                }
                case READY -> finish();
            }
        } catch (Exception e) {
            fail(e);
        }
    }

    /**
     * Moves to {@code next} and starts its work. The forge, the wall and the scout run on over later ticks (their
     * own tick handlers); the clearing is one synchronous pass in {@link #tick}, so a save between stages sees it
     * whole or not at all.
     */
    private void enter(Stage next) {
        if (next != stage)
            ReignOfNether.LOGGER.info("[Prep] stage {} -> {}", stage, next);
        stage = next;
        lightTicks = 0;
        switch (next) {
            case FORGE -> work.startForge(setup, Math.max(0, Math.min(3, fromStage)));
            case WALL -> work.startWall(setup, fromStage == SAVED_WALL ? fromDetail : 0);
            case SCOUT -> work.startScout(setup);
            default -> { }
        }
    }

    private void finish() {
        if (finished)
            return;
        finished = true;
        long s = Math.round((System.currentTimeMillis() - startedAtMs) / 1000.0);
        ReignOfNether.LOGGER.info("[Prep] READY in {} s", s);
        state.mapRaised();
    }

    private void fail(Exception e) {
        if (cancelled || finished)
            return;
        ReignOfNether.LOGGER.error("[Prep] stage {} failed", stage, e);
        cancelled = true;
        try {
            work.cancel();
        } catch (Exception ignored) {
            // the work is being dropped anyway
        }
        state.prepFailed("the battlefield could not be prepared at stage " + stage + ": " + e);
    }

    /** Drop the job: nothing more runs and nothing is reported. */
    public void cancel() {
        if (finished)
            return;
        cancelled = true;
        work.cancel();
    }

    /**
     * The stage for the save ({@code builderStage}): while forging, MapBuilder's own stage so a reload re-runs the
     * builder from there (3 = economy next; a builder that has stamped it, 4 or idle, saves as CLEAR so the economy
     * is never stamped twice); past the forge the stage numbers above.
     */
    public int savedStage() {
        return switch (stage) {
            case FORGE -> {
                int fs = work.forging() ? work.forgeStage() : 4;
                yield fs < 0 || fs >= 4 ? SAVED_CLEAR : Math.max(0, fs);
            }
            case CLEAR -> SAVED_CLEAR;
            case WALL -> SAVED_WALL;
            case SCOUT -> SAVED_SCOUT;
            case LIGHT -> SAVED_LIGHT;
            case READY -> SAVED_READY;
        };
    }

    /** The stage's own counter for the save: the wall's columns done (a resume carries on there), else 0. */
    public int savedDetail() {
        return stage == Stage.WALL ? Math.max(0, work.wallDone()) : 0;
    }

    /** The stage and its 0..100: the builder's own figure, wall columns done, chunks done, light ticks waited. */
    public PrepProgress progress() {
        int pct = switch (stage) {
            case FORGE -> work.forging() ? work.forgePct() : 100;
            case CLEAR -> 0;
            case WALL -> share(work.wallDone(), work.wallTotal());
            case SCOUT -> share(work.scoutDone(), work.scoutTotal());
            case LIGHT -> share(lightTicks, LIGHT_CAP_TICKS);
            case READY -> 100;
        };
        return new PrepProgress(stage, pct);
    }

    private static int share(int done, int total) {
        if (total <= 0)
            return 100;
        return Math.max(0, Math.min(100, (int) (100L * done / total)));
    }

    // ------------------------------------------------------------------------------------------ the live work

    /** The stages against the real world: MapBuilder, StartAreaClearing, BattlefieldWall, ArenaPregen, the lights. */
    public static final class LiveWork implements Work {
        private final ServerLevel level;

        public LiveWork(ServerLevel level) {
            this.level = level;
        }

        @Override
        public void startForge(MatchSetup setup, int fromStage) {
            // the build only writes blocks that differ, so after a reload it simply runs again over what is there;
            // from 3 it stamps the economy only (terrain, groves and decoration were saved with the world)
            MapBuilder.start(level, setup.cx, setup.cz, setup.baseY, setup.layout(), fromStage, null);
        }

        @Override
        public boolean forging() {
            return MapBuilder.isBuilding();
        }

        @Override
        public int forgeStage() {
            return MapBuilder.stage();
        }

        @Override
        public int forgePct() {
            return MapBuilder.progress();
        }

        @Override
        public void clear(MatchSetup setup) {
            // the discs and lanes of every slot now, the economy later: a pre-made map stamped its own patches and
            // sites, and on random terrain the patches follow where the humans actually place (BattlefieldSetup at
            // seat time, over ground that is then already clear)
            List<BlockPos> slots = setup.slotPositions(level);
            StartAreaClearing.skipEconomyStamp = true;
            try {
                StartAreaClearing.clearStartAreas(level, slots);
            } finally {
                StartAreaClearing.skipEconomyStamp = false;
            }
        }

        @Override
        public void startWall(MatchSetup setup, int fromColumn) {
            MapLayout layout = setup.layout();
            BattlefieldWall.raise(level, setup.cx + 0.5, setup.cz + 0.5, layout.radius, setup.wallMaterial(),
                setup.seed, WALL_COLUMNS_PER_TICK, fromColumn);
        }

        @Override
        public boolean wallBuilding() {
            return BattlefieldWall.isBuilding();
        }

        @Override
        public int wallDone() {
            return BattlefieldWall.columnsDone();
        }

        @Override
        public int wallTotal() {
            return BattlefieldWall.columnsPlanned();
        }

        @Override
        public void startScout(MatchSetup setup) {
            ArenaPregen.start(level, setup.cx + 0.5, setup.cz + 0.5, setup.layout().radius + SCOUT_MARGIN,
                SCOUT_IN_FLIGHT, false);
        }

        @Override
        public boolean scouting() {
            return ArenaPregen.isRunning();
        }

        @Override
        public int scoutDone() {
            return ArenaPregen.done();
        }

        @Override
        public int scoutTotal() {
            return ArenaPregen.total();
        }

        @Override
        public boolean lightBusy() {
            return level.getChunkSource().getLightEngine().hasLightWork();
        }

        @Override
        public void cancel() {
            MapBuilder.cancel();
            BattlefieldWall.cancel();
            ArenaPregen.stop();
        }
    }
}
