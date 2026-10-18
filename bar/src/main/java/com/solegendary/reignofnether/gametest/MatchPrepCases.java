package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.maps.MapPreset;
import com.solegendary.reignofnether.maps.StartPhase;
import com.solegendary.reignofnether.match.MatchEffects;
import com.solegendary.reignofnether.match.MatchPhase;
import com.solegendary.reignofnether.match.MatchPrep;
import com.solegendary.reignofnether.match.MatchSetup;
import com.solegendary.reignofnether.match.MatchState;
import com.solegendary.reignofnether.matchstart.MultiplayerLobby;
import com.solegendary.reignofnether.matchstart.MultiplayerMatch;
import com.solegendary.reignofnether.startpos.BattlefieldWall;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The bodies of the preparation GameTests ({@link MatchPrepGameTests}; inbox/spec-3, section 5.1). A MatchState is
 * driven with effects that start a real {@link MatchPrep} over a {@link FakeWork}: the stages' world work is
 * simulated tick by tick (the forge's stages, wall columns at 160 a tick, chunks at 8 a tick, a light backlog) and
 * every call recorded in order, so the job's order, its saved stages, its resumes and its failure are tested
 * without stamping a map or generating a chunk - the headless server has no human online for ArenaPregen anyway.
 * The one test against the real world raises a small wall on a pad of its own.
 */
final class MatchPrepCases {
    private MatchPrepCases() { }

    /** The stand-in for the stages' world work: records every start, finishes each over a few ticks. */
    static final class FakeWork implements MatchPrep.Work {
        final List<String> calls = new ArrayList<>();
        boolean forging = false;
        int forgeStage = -1;
        boolean wallBuilding = false;
        int wallDone = 0, wallTotal = 0;
        boolean scouting = false;
        int scoutDone = 0, scoutTotal = 0;
        int lightBusyTicks = 0;
        boolean cancelled = false;
        /** The stage whose work throws ("clear", "wall", "scout"), or null. */
        String throwAt = null;

        /** One tick of the simulated world, before the job's own tick. */
        void tick() {
            if (forging) {
                if (forgeStage < 3)
                    forgeStage++;
                else {
                    forging = false;   // the economy is stamped: the builder is idle
                    forgeStage = 4;
                }
            }
            if (wallBuilding) {
                wallDone = Math.min(wallTotal, wallDone + MatchPrep.WALL_COLUMNS_PER_TICK);
                if (wallDone >= wallTotal)
                    wallBuilding = false;
            }
            if (scouting) {
                scoutDone = Math.min(scoutTotal, scoutDone + MatchPrep.SCOUT_IN_FLIGHT);
                if (scoutDone >= scoutTotal) {
                    scouting = false;
                    lightBusyTicks = 2;   // the freshly generated chunks leave the lights a backlog
                }
            } else if (lightBusyTicks > 0) {
                lightBusyTicks--;
            }
        }

        private void maybeThrow(String stage) {
            if (stage.equals(throwAt))
                throw new IllegalStateException("gametest: the " + stage + " stage failed on purpose");
        }

        @Override
        public void startForge(MatchSetup setup, int fromStage) {
            calls.add("forge:" + fromStage);
            forging = true;
            forgeStage = fromStage;
        }

        @Override
        public boolean forging() {
            return forging;
        }

        @Override
        public int forgeStage() {
            return forgeStage;
        }

        @Override
        public int forgePct() {
            return Math.max(0, Math.min(99, 25 * forgeStage));
        }

        @Override
        public void clear(MatchSetup setup) {
            calls.add("clear");
            maybeThrow("clear");
        }

        @Override
        public void startWall(MatchSetup setup, CompoundTag resume) {
            int fromColumn = resume == null ? 0 : resume.getInt("columns");
            calls.add("wall:" + fromColumn);
            maybeThrow("wall");
            wallTotal = BattlefieldWall.ringLength(setup.cx + 0.5, setup.cz + 0.5, setup.layout().radius);
            wallDone = Math.min(wallTotal, fromColumn);
            wallBuilding = wallDone < wallTotal;
        }

        @Override
        public CompoundTag wallProgress() {
            CompoundTag tag = new CompoundTag();
            tag.putInt("columns", wallDone);
            return tag;
        }

        @Override
        public boolean wallBuilding() {
            return wallBuilding;
        }

        @Override
        public int wallDone() {
            return wallDone;
        }

        @Override
        public int wallTotal() {
            return wallTotal;
        }

        @Override
        public void startScout(MatchSetup setup) {
            calls.add("scout");
            maybeThrow("scout");
            int r = (setup.layout().radius + MatchPrep.SCOUT_MARGIN) / 16 + 1;
            scoutTotal = (2 * r + 1) * (2 * r + 1);
            scoutDone = 0;
            scouting = true;
        }

        @Override
        public boolean scouting() {
            return scouting;
        }

        @Override
        public int scoutDone() {
            return scoutDone;
        }

        @Override
        public int scoutTotal() {
            return scoutTotal;
        }

        @Override
        public boolean lightBusy() {
            return lightBusyTicks > 0;
        }

        @Override
        public void cancel() {
            calls.add("cancel");
            cancelled = true;
            forging = wallBuilding = scouting = false;
        }
    }

    /**
     * MatchLifecycleCases' Recorder for the start phase and the seat, with the preparation run for real over a
     * {@link FakeWork} - the way MapMatch.EFFECTS runs it over the world.
     */
    static final class PrepEffects implements MatchEffects {
        final MatchLifecycleCases.Recorder base;
        final FakeWork work = new FakeWork();
        MatchPrep prep = null;

        PrepEffects(ServerLevel level) {
            base = new MatchLifecycleCases.Recorder(level);
        }

        /** One server tick: the simulated world, then the job. */
        void tick() {
            work.tick();
            if (prep != null)
                prep.tick();
        }

        @Override
        public void prepare(MatchState state, int fromStage, CompoundTag fromDetail) {
            base.calls.add(fromStage >= 0 ? "prep:resumed" : "prep");
            prep = new MatchPrep(state, work, fromStage, fromDetail);
            prep.start();
        }

        @Override
        public int prepStage() {
            return prep == null ? -1 : prep.savedStage();
        }

        @Override
        public CompoundTag prepDetail() {
            return prep == null ? null : prep.savedDetail();
        }

        @Override
        public StartPhase newStartPhase(MatchState state) {
            return base.newStartPhase(state);
        }

        @Override
        public void beginStartPhase(MatchState state, StartPhase phase, boolean resumed) {
            base.beginStartPhase(state, phase, resumed);
        }

        @Override
        public MultiplayerMatch seat(MatchState state, StartPhase finished) {
            return base.seat(state, finished);
        }

        @Override
        public void cancelPreparation() {
            base.calls.add("cancel");
            if (prep != null)
                prep.cancel();
        }

        @Override
        public void showMap(MapPreset preset, int cx, int cz, int radius) {
            base.showMap(preset, cx, cz, radius);
        }

        @Override
        public void say(String message) {
            base.say(message);
        }
    }

    static MatchSetup premadeSetup(GameTestHelper helper, String human, long seed) {
        return MatchSetup.create(helper.getLevel(), MatchLifecycleCases.lineup(human), MapPreset.HIGHLANDS, true, 1, 1,
            1, helper.absolutePos(new BlockPos(8, 1, 8)), new Random(seed), MatchState.Origin.SKIRMISH);
    }

    /**
     * A random-terrain setup built from its saved form, with its ring sites given: MatchSetup.create samples the
     * ground at the ring (180 blocks out), which on the headless server means generating chunks on the server thread.
     */
    static MatchSetup randomSetup(GameTestHelper helper, String human, long seed) {
        BlockPos c = helper.absolutePos(new BlockPos(8, 1, 8));
        CompoundTag tag = new CompoundTag();
        tag.putString("origin", MatchState.Origin.SKIRMISH.name());
        tag.putString("preset", MapPreset.RANDOM.name());
        tag.putBoolean("choose", true);
        tag.putInt("arena", 1);
        tag.putInt("metal", 1);
        tag.putInt("spawns", 1);
        tag.putInt("cx", c.getX());
        tag.putInt("cy", c.getY());
        tag.putInt("cz", c.getZ());
        tag.putLong("seed", seed);
        tag.putInt("baseY", 0);
        tag.putInt("ringRadius", 150);
        tag.putInt("arenaRadius", 320);
        tag.putIntArray("ring", new int[] { -150, 0, 150, 0 });
        ListTag seats = new ListTag();
        for (MultiplayerLobby.Seat s : MatchLifecycleCases.lineup(human)) {
            CompoundTag st = new CompoundTag();
            st.putInt("id", s.id);
            st.putBoolean("bot", s.bot);
            st.putString("name", s.name);
            if (s.uuid != null)
                st.putUUID("uuid", s.uuid);
            st.putInt("faction", s.faction);
            st.putInt("colour", s.colorMapId);
            st.putInt("team", s.team);
            st.putBoolean("ready", s.ready);
            st.putInt("difficulty", s.difficulty);
            st.putBoolean("hero", s.heroRole);
            seats.add(st);
        }
        tag.put("lineup", seats);
        return MatchSetup.load(tag);
    }

    /** Ticks until the phase leaves MAP_RISING, at most {@code cap} ticks; the distinct stages seen, in order. */
    static List<MatchPrep.Stage> runToReady(MatchState s, PrepEffects fx, int cap) {
        List<MatchPrep.Stage> seen = new ArrayList<>();
        for (int t = 0; t < cap && s.phase() == MatchPhase.MAP_RISING; t++) {
            if (fx.prep != null && (seen.isEmpty() || seen.get(seen.size() - 1) != fx.prep.stage()))
                seen.add(fx.prep.stage());
            fx.tick();
        }
        if (fx.prep != null && (seen.isEmpty() || seen.get(seen.size() - 1) != fx.prep.stage()))
            seen.add(fx.prep.stage());
        return seen;
    }

    /**
     * Spec 3, 5.1 (a): a pre-made map's preparation runs FORGE -> CLEAR -> WALL -> SCOUT -> LIGHT -> READY, the
     * phase stays MAP_RISING the whole way and only leaves it (for the starts) once the clearing ran, every wall
     * column is placed (count == ring length), the scout reported every chunk and the lights are idle. Progress goes
     * through every stage with a percentage inside 0..100.
     */
    public static void the_battlefield_is_ready_before_the_start_phase_begins(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        PrepEffects fx = new PrepEffects(helper.getLevel());
        MatchState s = new MatchState(fx);
        MatchSetup setup = premadeSetup(helper, "gametest_mp_order", 11);
        try {
            if (!s.prepare(setup, null) || s.phase() != MatchPhase.MAP_RISING || fx.prep == null)
                problems.add("the match did not start preparing (phase " + s.phase() + ", calls " + fx.base.calls
                    + ")");
            if (!fx.work.calls.equals(List.of("forge:0")))
                problems.add("a pre-made map's preparation did not begin with the forge: " + fx.work.calls);
            boolean earlyExit = false;
            List<Integer> pcts = new ArrayList<>();
            for (int t = 0; t < 600 && s.phase() == MatchPhase.MAP_RISING; t++) {
                MatchPrep.PrepProgress p = fx.prep.progress();
                pcts.add(p.pct());
                fx.tick();
                if (s.phase() != MatchPhase.MAP_RISING && (fx.work.wallDone != fx.work.wallTotal
                        || fx.work.scoutDone != fx.work.scoutTotal || fx.work.lightBusy()
                        || !fx.work.calls.contains("clear")))
                    earlyExit = true;
            }
            if (s.phase() != MatchPhase.START_POSITIONS)
                problems.add("the preparation did not hand over to the start phase within 600 ticks (phase " + s.phase()
                    + ", stage " + fx.prep.stage() + ")");
            if (earlyExit)
                problems.add("the start phase began before the battlefield was ready (wall " + fx.work.wallDone + "/"
                    + fx.work.wallTotal + ", scout " + fx.work.scoutDone + "/" + fx.work.scoutTotal + ")");
            if (!fx.work.calls.equals(List.of("forge:0", "clear", "wall:0", "scout")))
                problems.add("the stages did not run in order: " + fx.work.calls);
            if (fx.work.wallTotal <= 0 || fx.work.wallTotal != BattlefieldWall.ringLength(setup.cx + 0.5,
                    setup.cz + 0.5, setup.layout().radius))
                problems.add("the wall's column count is not the ring's length: " + fx.work.wallTotal);
            if (!fx.prep.isFinished() || fx.prep.stage() != MatchPrep.Stage.READY
                    || fx.prep.savedStage() != MatchPrep.SAVED_READY)
                problems.add("the job did not end READY (stage " + fx.prep.stage() + ", saved " + fx.prep.savedStage()
                    + ")");
            for (int pct : pcts)
                if (pct < 0 || pct > 100) {
                    problems.add("a progress figure left 0..100: " + pct);
                    break;
                }
            if (!fx.base.calls.contains("phase"))
                problems.add("the start phase did not begin after READY: " + fx.base.calls);
        } finally {
            s.reset();
            if (problems.isEmpty())
                helper.succeed();
            else
                helper.fail(String.join("; ", problems));
        }
    }

    /**
     * Random terrain used to skip MAP_RISING and go straight to the start boxes, with the wall and the arena's
     * generation running during the first minutes of play (lovish's Oct 17 playtest, "Can't keep up"). Now it is
     * prepared too - CLEAR, WALL, SCOUT, LIGHT, no forge - over the arena the setup rolled at Start, so the wall is
     * the layout's radius and known before anyone is seated.
     */
    public static void random_terrain_is_prepared_too_without_a_forge(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        PrepEffects fx = new PrepEffects(helper.getLevel());
        MatchState s = new MatchState(fx);
        MatchSetup setup = randomSetup(helper, "gametest_mp_random", 12);
        try {
            if (setup == null) {
                problems.add("the random-terrain setup did not load from its saved form");
                return;
            }
            if (setup.raisesMap() || !setup.prepares())
                problems.add("a random-terrain setup with two sides should prepare without a forge");
            if (!s.prepare(setup, null) || s.phase() != MatchPhase.MAP_RISING)
                problems.add("random terrain did not enter MAP_RISING to be prepared (phase " + s.phase() + ")");
            if (s.mapRadius() != setup.layout().radius || setup.layout().radius != 320)
                problems.add("the arena shown is not the layout's: " + s.mapRadius() + " vs " + setup.layout().radius);
            List<MatchPrep.Stage> seen = runToReady(s, fx, 600);
            if (s.phase() != MatchPhase.START_POSITIONS)
                problems.add("the preparation did not hand over to the start phase (phase " + s.phase() + ")");
            if (!fx.work.calls.equals(List.of("clear", "wall:0", "scout")))
                problems.add("random terrain's stages were not clear, wall, scout: " + fx.work.calls);
            if (!seen.equals(List.of(MatchPrep.Stage.CLEAR, MatchPrep.Stage.WALL, MatchPrep.Stage.SCOUT,
                    MatchPrep.Stage.LIGHT, MatchPrep.Stage.READY)))
                problems.add("the stages seen were " + seen);
            if (fx.work.wallTotal != BattlefieldWall.ringLength(setup.cx + 0.5, setup.cz + 0.5, 320))
                problems.add("the wall was not planned on the layout's radius: " + fx.work.wallTotal + " columns");
        } finally {
            s.reset();
            if (problems.isEmpty())
                helper.succeed();
            else
                helper.fail(String.join("; ", problems));
        }
    }

    /**
     * Spec 3, 5.1 (b), the lifecycle half: a world saved at every stage of the preparation (0-3 the forge's own, 4
     * clear, 5 wall, 6 scout, 7 light) reloads as REBUILD_MAP and resumes AT that stage - a forge saved at 3 stamps
     * only its economy, a wall saved mid-way is raised again, nothing before the saved stage runs twice - and goes
     * on to READY and the start phase. (Whether the world's bytes come out the same is the wall test below and
     * MapBuilder's own idempotence; the chunk-hash comparison of a whole arena needs a client and is left to the
     * eyes.)
     */
    public static void a_preparation_resumes_from_every_saved_stage_and_repeats_nothing_before_it(GameTestHelper helper)
            throws IOException {
        List<String> problems = new ArrayList<>();
        ServerLevel level = helper.getLevel();
        int[] stages = { 0, 1, 2, 3, MatchPrep.SAVED_CLEAR, MatchPrep.SAVED_WALL, MatchPrep.SAVED_SCOUT,
            MatchPrep.SAVED_LIGHT };
        for (int target : stages) {
            PrepEffects fx = new PrepEffects(level);
            MatchState s = new MatchState(fx);
            MatchSetup setup = premadeSetup(helper, "gametest_mp_resume", 20 + target);
            s.prepare(setup, null);
            int guard = 0;
            while (fx.prepStage() != target && s.phase() == MatchPhase.MAP_RISING && guard++ < 600)
                fx.tick();
            if (fx.prepStage() != target) {
                problems.add("stage " + target + " was never the saved stage (phase " + s.phase() + ", saved "
                    + fx.prepStage() + ")");
                s.reset();
                continue;
            }
            if (target == MatchPrep.SAVED_WALL)   // two ticks into the ring: the save keeps the columns done
                for (int t = 0; t < 2; t++)
                    fx.tick();
            CompoundTag saved = SkirmishGameTests.bytesRoundTrip(s.save());
            int detail = saved.getCompound("builderDetail").getInt("columns");
            s.reset();
            if (!saved.contains("builderStage") || saved.getInt("builderStage") != target) {
                problems.add("stage " + target + " did not save as itself: " + saved.getInt("builderStage"));
                continue;
            }
            if (target == MatchPrep.SAVED_WALL && (detail <= 0 || detail >= fx.work.wallTotal))
                problems.add("a save mid-wall did not keep its columns done: " + detail + " of " + fx.work.wallTotal);
            if (target != MatchPrep.SAVED_WALL && saved.contains("builderDetail"))
                problems.add("stage " + target + " saved a wall detail: " + saved.getCompound("builderDetail"));
            PrepEffects fx2 = new PrepEffects(level);
            MatchState r = MatchState.load(saved, fx2);
            if (r.phase() != MatchPhase.MAP_RISING || r.resumePlan() != MatchState.ResumePlan.REBUILD_MAP) {
                problems.add("stage " + target + " did not reload as a preparation to rebuild: " + r.phase() + " / "
                    + r.resumePlan());
                continue;
            }
            r.resume();
            if (!fx2.base.calls.contains("prep:resumed") || fx2.prep == null || r.resumeNotice() == null) {
                problems.add("stage " + target + " did not resume the preparation: " + fx2.base.calls);
                continue;
            }
            runToReady(r, fx2, 600);
            List<String> expected = switch (target) {
                case 0, 1, 2 -> List.of("forge:0", "clear", "wall:0", "scout");
                case 3 -> List.of("forge:3", "clear", "wall:0", "scout");
                case MatchPrep.SAVED_CLEAR -> List.of("clear", "wall:0", "scout");
                case MatchPrep.SAVED_WALL -> List.of("wall:" + detail, "scout");
                case MatchPrep.SAVED_SCOUT -> List.of("scout");
                default -> List.of();
            };
            if (!fx2.work.calls.equals(expected))
                problems.add("resumed at stage " + target + " the work was " + fx2.work.calls + ", expected "
                    + expected);
            if (r.phase() != MatchPhase.START_POSITIONS)
                problems.add("resumed at stage " + target + " the match did not reach the start phase (" + r.phase()
                    + ")");
            r.reset();
        }
        if (problems.isEmpty())
            helper.succeed();
        else
            helper.fail(String.join("; ", problems));
    }

    /**
     * Spec 3, 5.1 (d): a stage that throws cancels the match cleanly to LOBBY with the chat line, as a setup that
     * cannot resume does today - the job dropped, nothing seated, a notice for whoever logs in - and the world can
     * start a new match at once.
     */
    public static void a_preparation_stage_that_throws_cancels_the_match_to_the_lobby(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        PrepEffects fx = new PrepEffects(helper.getLevel());
        fx.work.throwAt = "clear";
        MatchState s = new MatchState(fx);
        try {
            s.prepare(premadeSetup(helper, "gametest_mp_throw", 31), null);
            for (int t = 0; t < 100 && s.phase() == MatchPhase.MAP_RISING; t++)
                fx.tick();
            if (s.phase() != MatchPhase.LOBBY)
                problems.add("a failed stage left the phase at " + s.phase());
            if (!fx.base.calls.contains("cancel") || !fx.base.calls.contains("say"))
                problems.add("the cancel did not drop the preparation and say so: " + fx.base.calls);
            if (s.resumeNotice() == null || s.setup() != null || s.startPhase() != null)
                problems.add("the cancel left part of the match behind (notice " + s.resumeNotice() + ")");
            if (!fx.work.cancelled || !fx.prep.isCancelled())
                problems.add("the job's work was not dropped");
            if (fx.base.calls.contains("phase"))
                problems.add("a start phase began after the preparation failed");
            fx.work.throwAt = null;
            if (!s.prepare(premadeSetup(helper, "gametest_mp_throw", 32), null) || s.phase() != MatchPhase.MAP_RISING)
                problems.add("a new match could not be prepared after the cancel (phase " + s.phase() + ")");
        } finally {
            s.reset();
            if (problems.isEmpty())
                helper.succeed();
            else
                helper.fail(String.join("; ", problems));
        }
    }

    /**
     * Spec 3, 5.1 (b), the world half: a prepared wall (BattlefieldWall.raise, seeded) built in one go and the same
     * wall built part-way, dropped and resumed from the column count a save would keep come out block for block
     * the same - the ring's rolls come from the seed, each column's weathering from the seed and its index, and
     * every column founds on the ground as it was before the wall, which the save carries (CI run 39087534140: the
     * columns beside the tower read the tower's top off the heightmap, rose seven blocks higher than the box this
     * test clears, and the leftovers made the second run differ in 73 blocks; the ring's closing columns share the
     * first tower's footprint, so a resume must know the ground under it). The ring's column count is its length.
     */
    public static void a_prepared_wall_raised_twice_lays_the_same_blocks(GameTestHelper helper) throws IOException {
        ServerLevel level = helper.getLevel();
        BlockPos c = MatchLifecycleCases.pad(helper, 236);
        int y = c.getY(), radius = 7, box = radius + 4, perTick = MatchPrep.WALL_COLUMNS_PER_TICK;
        int roof = y + 28;   // a tower tops out at ground + 12; anything higher is a spike, and would be seen
        double wx = c.getX() + 0.5, wz = c.getZ() + 0.5;
        List<String> problems = new ArrayList<>();
        try {
            BattlefieldWall.raise(level, wx, wz, radius, 0, 4242L, perTick, null);
            int planned = BattlefieldWall.columnsPlanned();
            BattlefieldWall.finishNow();
            if (BattlefieldWall.isBuilding() || BattlefieldWall.columnsDone() != planned
                    || planned != BattlefieldWall.ringLength(wx, wz, radius))
                problems.add("the ring was not built whole (" + BattlefieldWall.columnsDone() + " of " + planned + ")");
            List<BlockState> whole = snapshot(level, c, box, y - 3, roof);
            int wallBlocks = 0;
            for (BlockState st : whole)
                if (!st.isAir() && !st.is(Blocks.GRASS_BLOCK) && !st.is(Blocks.DIRT))
                    wallBlocks++;
            if (wallBlocks < planned * 8)
                problems.add("too few wall blocks for " + planned + " columns: " + wallBlocks);
            int spikes = 0;   // a tower's crenellation is the highest block, 12 above the ground
            BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
            for (int x = -box; x <= box; x++)
                for (int z = -box; z <= box; z++)
                    for (int yy = y + 15; yy <= roof; yy++)
                        if (!level.getBlockState(p.set(c.getX() + x, yy, c.getZ() + z)).isAir())
                            spikes++;
            if (spikes > 0)
                problems.add(spikes + " blocks stand more than 14 above the ground: a column founded on the wall"
                    + " itself");

            // the same ring again on a fresh pad: a third of it, then "a save and a reload" (dropped, resumed at the
            // count)
            clearPad(level, c, box, y, roof);
            MatchLifecycleCases.pad(helper, y);
            BattlefieldWall.raise(level, wx, wz, radius, 0, 4242L, perTick, null);
            int part = BattlefieldWall.buildNow(planned / 3);
            int done = BattlefieldWall.columnsDone();
            CompoundTag progress = SkirmishGameTests.bytesRoundTrip(BattlefieldWall.saveProgress());   // as the save
            BattlefieldWall.cancel();
            if (part != planned / 3 || done != part || progress.getInt("columns") != done)
                problems.add("the part build did not count its columns (" + part + " built, " + done + " done, saved "
                    + progress.getInt("columns") + ")");
            BattlefieldWall.raise(level, wx, wz, radius, 0, 4242L, perTick, progress);
            if (BattlefieldWall.columnsDone() != done || BattlefieldWall.columnsPlanned() != planned)
                problems.add("the resumed ring did not start at the saved column (" + BattlefieldWall.columnsDone()
                    + " of " + BattlefieldWall.columnsPlanned() + ")");
            BattlefieldWall.finishNow();
            List<BlockState> resumed = snapshot(level, c, box, y - 3, roof);
            int differ = 0;
            for (int i = 0; i < whole.size(); i++)
                if (whole.get(i) != resumed.get(i))
                    differ++;
            if (differ > 0)
                problems.add(differ + " blocks differ between the wall built in one go and the one resumed at column "
                    + done);
        } finally {
            BattlefieldWall.cancel();
            clearPad(level, c, box, y, roof);
            if (problems.isEmpty())
                helper.succeed();
            else
                helper.fail(String.join("; ", problems));
        }
    }

    static void clearPad(ServerLevel level, BlockPos c, int half, int y, int roof) {
        for (int x = -half; x <= half; x++)
            for (int z = -half; z <= half; z++)
                for (int yy = y - 3; yy <= roof; yy++)
                    level.setBlock(new BlockPos(c.getX() + x, yy, c.getZ() + z), Blocks.AIR.defaultBlockState(), 3);
    }

    static List<BlockState> snapshot(ServerLevel level, BlockPos c, int half, int y0, int y1) {
        List<BlockState> out = new ArrayList<>();
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int x = -half; x <= half; x++)
            for (int z = -half; z <= half; z++)
                for (int y = y0; y <= y1; y++)
                    out.add(level.getBlockState(p.set(c.getX() + x, y, c.getZ() + z)));
        return out;
    }
}
