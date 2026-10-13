package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.BuildingServerEvents;
import com.solegendary.reignofnether.building.Buildings;
import com.solegendary.reignofnether.faction.Factions;
import com.solegendary.reignofnether.maps.MapMatch;
import com.solegendary.reignofnether.maps.MapPreset;
import com.solegendary.reignofnether.maps.StartPhase;
import com.solegendary.reignofnether.match.MatchEffects;
import com.solegendary.reignofnether.match.MatchLifecycleSaveData;
import com.solegendary.reignofnether.match.MatchOutcome;
import com.solegendary.reignofnether.match.MatchPhase;
import com.solegendary.reignofnether.match.MatchSetup;
import com.solegendary.reignofnether.match.MatchState;
import com.solegendary.reignofnether.matchstart.MultiplayerLobby;
import com.solegendary.reignofnether.matchstart.MultiplayerMatch;
import com.solegendary.reignofnether.player.CommanderServerEvents;
import com.solegendary.reignofnether.player.DefeatCleanupServerEvents;
import com.solegendary.reignofnether.player.PlayerServerEvents;
import com.solegendary.reignofnether.player.RTSPlayer;
import com.solegendary.reignofnether.player.ResignVoteServerEvents;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.resources.ResourcesServerEvents;
import com.solegendary.reignofnether.resources.WreckServerEvents;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraftforge.common.util.FakePlayerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * The bodies of the match lifecycle GameTests (design/ron_rewrite_plan.md, rewrite 5); the entry points, their
 * timeouts and their batch are in {@link MatchLifecycleGameTests}, which says why they are split. How a side loses,
 * who wins, resigning, spectating. The first half pins the behaviour the rewrite had to keep - written against the
 * public API that existed before it (PlayerServerEvents, CommanderServerEvents, ResignVoteServerEvents), so the same
 * tests passed on the old code and pass on the new match package.
 * <p>
 * Every test uses its own side names, scopes its victory check with its own MultiplayerMatch (several tests run side
 * by side in one world) and takes its sides, alliances, units and fake players out again in {@code finally}. Sides
 * are human RTS players (non-negative ids, no ServerPlayer behind most of them), never bots, so no bot brain wakes up
 * on a test's buildings.
 * <p>
 * The second half covers the new code: match.MatchState driven through every phase with a recording MatchEffects
 * (nothing stamped, the server's live match untouched), every phase sent through real NBT bytes and loaded back
 * (code_review CR-13), what a resume does with each, a resumed start phase holding its clock, the cancel of a setup
 * that can't resume, the saved alliances, and the roster surviving a defeat inside a walk over it.
 */
final class MatchLifecycleCases {
    private MatchLifecycleCases() { }

    /** A human side with no player behind it (id out of the entity id range the server hands out in a test run). */
    static RTSPlayer side(String name, int id) {
        RTSPlayer p = RTSPlayer.getNewPlayer(name, Factions.VILLAGERS, id, 0, true, -1L);
        PlayerServerEvents.rtsPlayers.add(p);
        return p;
    }

    /** A fake online player (resign and spectate find the sender among PlayerServerEvents.players by entity id). */
    static ServerPlayer fake(ServerLevel level, String name) {
        ServerPlayer sp = FakePlayerFactory.get(level, new GameProfile(UUID.nameUUIDFromBytes(name.getBytes()), name));
        PlayerServerEvents.players.add(sp);
        return sp;
    }

    static boolean finished(String name) {
        for (RTSPlayer p : PlayerServerEvents.postGameRtsPlayers)
            if (p != null && name.equals(p.name))
                return true;
        return false;
    }

    /** Takes every trace of these names out of the match lists (in play or finished). */
    static void forget(String... names) {
        Set<String> set = Set.of(names);
        PlayerServerEvents.rtsPlayers.removeIf(p -> p != null && set.contains(p.name));
        PlayerServerEvents.postGameRtsPlayers.removeIf(p -> p != null && set.contains(p.name));
        ResourcesServerEvents.resourcesList.removeIf(r -> set.contains(r.ownerName));
    }

    /** A flat 17x17 pad at height y over the arena's centre, its own column so nothing else stands there. */
    static BlockPos pad(GameTestHelper helper, int y) {
        ServerLevel level = helper.getLevel();
        BlockPos base = helper.absolutePos(new BlockPos(8, 1, 8));
        int cx = base.getX(), cz = base.getZ();
        for (int dx = -8; dx <= 8; dx++)
            for (int dz = -8; dz <= 8; dz++) {
                level.setBlock(new BlockPos(cx + dx, y - 1, cz + dz), Blocks.DIRT.defaultBlockState(), 3);
                level.setBlock(new BlockPos(cx + dx, y, cz + dz), Blocks.GRASS_BLOCK.defaultBlockState(), 3);
            }
        return new BlockPos(cx, y, cz);
    }

    static LivingEntity soldier(ServerLevel level, String owner, double x, double y, double z) {
        var u = EntityRegistrar.VINDICATOR_UNIT.get().create(level);
        if (u == null)
            return null;
        u.moveTo(x, y, z, 0, 0);
        u.setOwnerName(owner);
        level.addFreshEntity(u);
        return u;
    }

    // ------------------------------------------------------------------------------- pinned behaviour (before 5)

    /**
     * lovish's rule, 1v1 half: a side whose commander falls with no ally left in the match loses at once
     * (CommanderServerEvents, gamerule commanderDefeat), the other side wins this match, and the loser's base goes
     * the way of BAR's (DefeatCleanupServerEvents): its buildings turn into neutral wrecks holding half their metal
     * and its units die - nothing of a beaten side stands on as a live target.
     */
    public static void commander_death_ends_a_1v1_and_the_base_becomes_wrecks(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String a = "gametest_ml_c1_a", b = "gametest_ml_c1_b";
        BlockPos c = pad(helper, 226);
        int y = c.getY();
        side(a, 1_100_001);
        side(b, 1_100_002);
        ResourcesServerEvents.assignResources(a);
        MultiplayerMatch match = new MultiplayerMatch(Set.of(a, b), Set.of()).register();
        var wind = Buildings.WIND_GENERATOR_VILLAGERS;
        BuildingPlacement site = BuildingServerEvents.placeBuilding(wind, new BlockPos(c.getX() + 2, y, c.getZ() - 5),
            Rotation.NONE, a, new int[0], false, false, true, true);
        // the commander in one corner, the soldier in the other: 19 blocks apart, so the soldier is taken by the
        // defeat cleanup and not by the commander blast (8 blocks)
        LivingEntity commander = soldier(level, a, c.getX() - 6.5, y + 1, c.getZ() + 7.5);
        LivingEntity grunt = soldier(level, a, c.getX() + 7.5, y + 1, c.getZ() + 7.5);
        List<Entity> spawned = new ArrayList<>();
        if (commander != null) spawned.add(commander);
        if (grunt != null) spawned.add(grunt);
        Runnable cleanup = () -> {
            forget(a, b);
            match.end();
            if (site != null)
                BuildingServerEvents.getBuildings().remove(site);
            spawned.forEach(Entity::discard);
            for (Entity w : new ArrayList<>(WreckServerEvents.getWrecks()))
                if (!w.isRemoved() && Math.abs(w.getY() - y) < 4 && w.distanceToSqr(c.getX() + 0.5, y, c.getZ() + 0.5) < 13 * 13)
                    w.discard();
        };
        if (site == null || commander == null || grunt == null) {
            cleanup.run();
            helper.fail("could not set up the 1v1 (site " + site + ", commander " + commander + ", soldier " + grunt + ")");
            return;
        }
        CommanderServerEvents.makeCommander(commander);
        helper.runAfterDelay(3, () -> {
            commander.kill();
            List<String> problems = new ArrayList<>();
            if (PlayerServerEvents.isRTSPlayer(a))
                problems.add("a side whose commander fell in a 1v1 is still in the match");
            if (!match.isOver() || !finished(b) || !PlayerServerEvents.isRTSPlayer(b))
                problems.add("the other side should have won this match (over " + match.isOver() + ", finished "
                    + finished(b) + ", still an RTS player " + PlayerServerEvents.isRTSPlayer(b) + ")");
            if (!problems.isEmpty()) {
                cleanup.run();
                helper.fail(String.join("; ", problems));
            }
        });
        helper.runAfterDelay(80, () -> {
            try {
                List<String> problems = new ArrayList<>();
                if (DefeatCleanupServerEvents.isCleaningUp(a))
                    problems.add("the defeat cleanup is still running after 4 s");
                if (BuildingServerEvents.getBuildings().contains(site))
                    problems.add("the beaten side's building still stands in the match");
                boolean wreck = false;
                for (Entity w : WreckServerEvents.getWrecks())
                    if (!w.isRemoved() && w.distanceToSqr(site.centrePos.getX() + 0.5, y, site.centrePos.getZ() + 0.5) < 3 * 3
                            && WreckServerEvents.metalOf(w) >= wind.cost.metal() * DefeatCleanupServerEvents.BUILDING_WRECK_SHARE - 0.01f)
                        wreck = true;
                if (!wreck)
                    problems.add("no wreck holding half the building's metal where it stood");
                if (grunt.isAlive() && !grunt.isRemoved())
                    problems.add("the beaten side's soldier is still alive");
                if (!problems.isEmpty())
                    helper.fail(String.join("; ", problems));
                else
                    helper.succeed();
            } finally {
                cleanup.run();
            }
        });
    }

    /**
     * lovish's rule, team half: in a team game a fallen commander does not beat its side while a teammate still has
     * a commander - the blast goes off and the player fights on with what is left; when the team's LAST commander
     * falls the whole team is out and the enemy wins (BAR's commander-ends rule, chosen by lovish on Oct 12).
     */
    public static void commander_death_in_a_team_game_fights_on_until_the_last(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String a1 = "gametest_ml_team_a1", a2 = "gametest_ml_team_a2", b = "gametest_ml_team_b";
        BlockPos c = pad(helper, 228);
        int y = c.getY();
        side(a1, 1_100_011);
        side(a2, 1_100_012);
        side(b, 1_100_013);
        AlliancesServerEvents.addAlliance(a1, a2);
        MultiplayerMatch match = new MultiplayerMatch(Set.of(a1, a2, b), Set.of()).register();
        LivingEntity com1 = soldier(level, a1, c.getX() - 6.5, y + 1, c.getZ() - 6.5);
        LivingEntity com2 = soldier(level, a2, c.getX() + 7.5, y + 1, c.getZ() + 7.5);
        Runnable cleanup = () -> {
            forget(a1, a2, b);
            AlliancesServerEvents.removeAlliance(a1, a2);
            match.end();
            if (com1 != null) com1.discard();
            if (com2 != null) com2.discard();
        };
        if (com1 == null || com2 == null) {
            cleanup.run();
            helper.fail("could not create the commanders");
            return;
        }
        CommanderServerEvents.makeCommander(com1);
        CommanderServerEvents.makeCommander(com2);
        List<String> problems = new ArrayList<>();
        helper.runAfterDelay(3, () -> com1.kill());
        helper.runAfterDelay(5, () -> {
            if (!PlayerServerEvents.isRTSPlayer(a1))
                problems.add("a team player was beaten by losing their commander while a teammate still had one");
            if (match.isOver() || finished(b))
                problems.add("the enemy was declared the winner over a team that still has a commander");
        });
        helper.runAfterDelay(6, () -> com2.kill());
        helper.runAfterDelay(10, () -> {
            try {
                if (PlayerServerEvents.isRTSPlayer(a1) || PlayerServerEvents.isRTSPlayer(a2))
                    problems.add("the team's last commander fell but the team is still in the match");
                if (!match.isOver() && !finished(b))
                    problems.add("the enemy did not win when the other team's last commander fell");
                if (!problems.isEmpty())
                    helper.fail(String.join("; ", problems));
                else
                    helper.succeed();
            } finally {
                cleanup.run();
            }
        });
    }

    /**
     * Victory goes to the last team standing (PlayerServerEvents.winnersAmong over this match's remaining sides):
     * the whole team wins together - every member stays in play as a finished winner - while two sides still
     * stand nobody has won, and in a free-for-all the last one left wins. winnersAmong itself: nobody for nobody,
     * a lone survivor wins, two enemies don't, one alliance does.
     */
    public static void victory_goes_to_the_last_team_standing(GameTestHelper helper) {
        String a1 = "gametest_ml_win_a1", a2 = "gametest_ml_win_a2", b = "gametest_ml_win_b";
        String x = "gametest_ml_ffa_x", yy = "gametest_ml_ffa_y", z = "gametest_ml_ffa_z";
        side(a1, 1_100_021);
        side(a2, 1_100_022);
        side(b, 1_100_023);
        side(x, 1_100_024);
        side(yy, 1_100_025);
        side(z, 1_100_026);
        AlliancesServerEvents.addAlliance(a1, a2);
        MultiplayerMatch team = new MultiplayerMatch(Set.of(a1, a2, b), Set.of()).register();
        MultiplayerMatch ffa = new MultiplayerMatch(Set.of(x, yy, z), Set.of()).register();
        try {
            List<String> problems = new ArrayList<>();
            if (!PlayerServerEvents.winnersAmong(Set.of()).isEmpty())
                problems.add("winnersAmong of nobody is not empty");
            if (!PlayerServerEvents.winnersAmong(Set.of(z)).equals(Set.of(z)))
                problems.add("a lone survivor does not win");
            if (!PlayerServerEvents.winnersAmong(Set.of(x, z)).isEmpty())
                problems.add("two enemies were both declared winners");
            if (!PlayerServerEvents.winnersAmong(Set.of(a1, a2)).equals(Set.of(a1, a2)))
                problems.add("one alliance is not the winner");

            PlayerServerEvents.defeat(b, "gametest");
            if (!team.isOver())
                problems.add("the team match did not end when its only enemy fell");
            if (!finished(a1) || !finished(a2))
                problems.add("not every member of the winning team won (" + finished(a1) + ", " + finished(a2) + ")");
            if (!PlayerServerEvents.isRTSPlayer(a1) || !PlayerServerEvents.isRTSPlayer(a2))
                problems.add("winners left play before choosing to spectate");

            PlayerServerEvents.defeat(x, "gametest");
            if (ffa.isOver())
                problems.add("the free-for-all ended with two sides still standing");
            PlayerServerEvents.defeat(yy, "gametest");
            if (!ffa.isOver() || !finished(z) || !PlayerServerEvents.isRTSPlayer(z))
                problems.add("the last side of the free-for-all did not win (over " + ffa.isOver() + ")");
            if (PlayerServerEvents.isRTSPlayer(x) || PlayerServerEvents.isRTSPlayer(yy))
                problems.add("a beaten free-for-all side is still in play");
            if (!problems.isEmpty())
                helper.fail(String.join("; ", problems));
            else
                helper.succeed();
        } finally {
            forget(a1, a2, b, x, yy, z);
            AlliancesServerEvents.removeAlliance(a1, a2);
            team.end();
            ffa.end();
        }
    }

    /**
     * Resign (ResignVoteServerEvents): alone - no living human ally - it is the plain surrender and the enemy wins;
     * on a team it calls a vote, nothing happens while it is open, and once a majority of the living humans said
     * yes every human on the team is beaten while a bot ally fights on (so the match goes on).
     */
    public static void resign_alone_surrenders_and_a_passed_team_vote_beats_every_human(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String lone = "gametest_ml_rs_lone", enemy = "gametest_ml_rs_enemy";
        String f1 = "gametest_ml_rs_f1", f2 = "gametest_ml_rs_f2", bot = "gametest_ml_rs_bot", foe = "gametest_ml_rs_foe";
        ServerPlayer spLone = fake(level, lone), sp1 = fake(level, f1), sp2 = fake(level, f2);
        side(lone, spLone.getId());
        side(enemy, 1_100_031);
        side(f1, sp1.getId());
        side(f2, sp2.getId());
        RTSPlayer botSide = RTSPlayer.getNewBot(bot, Factions.MONSTERS);   // no buildings: no brain, it just stands
        PlayerServerEvents.rtsPlayers.add(botSide);
        side(foe, 1_100_032);
        AlliancesServerEvents.addAlliance(f1, f2);
        AlliancesServerEvents.addAlliance(f1, bot);
        AlliancesServerEvents.addAlliance(f2, bot);
        MultiplayerMatch duel = new MultiplayerMatch(Set.of(lone, enemy), Set.of(lone)).register();
        MultiplayerMatch teams = new MultiplayerMatch(Set.of(f1, f2, bot, foe), Set.of(f1, f2)).register();
        try {
            List<String> problems = new ArrayList<>();
            ResignVoteServerEvents.call(spLone.getId());
            if (PlayerServerEvents.isRTSPlayer(lone))
                problems.add("a lone player's resign did not surrender");
            if (!duel.isOver() || !finished(enemy))
                problems.add("the enemy of a surrendering player did not win");

            ResignVoteServerEvents.call(sp1.getId());
            if (!PlayerServerEvents.isRTSPlayer(f1) || !PlayerServerEvents.isRTSPlayer(f2))
                problems.add("an open resign vote already beat the team");
            ResignVoteServerEvents.answer(sp2.getId(), true);
            if (PlayerServerEvents.isRTSPlayer(f1) || PlayerServerEvents.isRTSPlayer(f2))
                problems.add("a passed resign vote left a human of the team in play");
            if (!PlayerServerEvents.isRTSPlayer(bot))
                problems.add("the bot ally resigned with the humans");
            if (teams.isOver())
                problems.add("the match ended although the bot ally fights on");
            if (!problems.isEmpty())
                helper.fail(String.join("; ", problems));
            else
                helper.succeed();
        } finally {
            forget(lone, enemy, f1, f2, bot, foe);
            AlliancesServerEvents.removeAlliance(f1, f2);
            AlliancesServerEvents.removeAlliance(f1, bot);
            AlliancesServerEvents.removeAlliance(f2, bot);
            duel.end();
            teams.end();
            PlayerServerEvents.players.remove(spLone);
            PlayerServerEvents.players.remove(sp1);
            PlayerServerEvents.players.remove(sp2);
        }
    }

    /**
     * Spectate (the results screen's button): refused for a side still in the fight - it is not a way to look
     * through the fog mid-match - and granted to a winner once their match is over, who then leaves play (fog off,
     * observer HUD) while everything they own stays standing and theirs.
     */
    public static void a_winner_spectates_and_keeps_their_base_but_a_fighter_cannot(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String w = "gametest_ml_spec_w", l = "gametest_ml_spec_l";
        BlockPos c = pad(helper, 230);
        ServerPlayer spW = fake(level, w);
        side(w, spW.getId());
        side(l, 1_100_041);
        MultiplayerMatch match = new MultiplayerMatch(Set.of(w, l), Set.of(w)).register();
        LivingEntity unit = soldier(level, w, c.getX() + 0.5, c.getY() + 1, c.getZ() + 0.5);
        helper.runAfterDelay(3, () -> {
            try {
                List<String> problems = new ArrayList<>();
                if (unit == null) {
                    helper.fail("could not create the winner's unit");
                    return;
                }
                PlayerServerEvents.spectate(spW.getId());
                if (!PlayerServerEvents.isRTSPlayer(w))
                    problems.add("a side still in the fight was let out to spectate");
                PlayerServerEvents.defeat(l, "gametest");
                PlayerServerEvents.spectate(spW.getId());
                if (PlayerServerEvents.isRTSPlayer(w))
                    problems.add("the winner could not spectate after the match was decided");
                if (!unit.isAlive() || !(unit instanceof com.solegendary.reignofnether.unit.interfaces.Unit u) || !w.equals(u.getOwnerName()))
                    problems.add("a spectating winner's unit did not stay standing and theirs");
                if (!problems.isEmpty())
                    helper.fail(String.join("; ", problems));
                else
                    helper.succeed();
            } finally {
                forget(w, l);
                match.end();
                if (unit != null)
                    unit.discard();
                PlayerServerEvents.players.remove(spW);
            }
        });
    }

    // ------------------------------------------------------------------------------- rewrite 5: the state machine

    /**
     * MatchEffects that change nothing in the world: they record what MatchState asked for, build start phases the
     * real way (MapMatch.newStartPhase, pure) and "seat" a match of the picks' names - unregistered, bots renamed so
     * nothing collides with another test's sides.
     */
    static final class Recorder implements MatchEffects {
        final ServerLevel level;
        final List<String> calls = new ArrayList<>();
        int buildStage = 0;

        Recorder(ServerLevel level) {
            this.level = level;
        }

        @Override
        public void raiseMap(MatchState state, boolean resumed) {
            calls.add(resumed ? "raise:resumed" : "raise");
        }

        @Override
        public int mapBuildStage() {
            return buildStage;
        }

        @Override
        public StartPhase newStartPhase(MatchState state) {
            return MapMatch.newStartPhase(level, state.setup(), state::startsPlaced);
        }

        @Override
        public void beginStartPhase(MatchState state, StartPhase phase, boolean resumed) {
            calls.add(resumed ? "phase:resumed" : "phase");
        }

        /** What refusesLateStart said for each human WHILE the seat ran: the real seat starts humans through startRTS. */
        final Map<String, Boolean> refusedDuringSeat = new LinkedHashMap<>();

        @Override
        public MultiplayerMatch seat(MatchState state, StartPhase finished) {
            calls.add("seat");
            for (StartPhase.Pick p : finished.picks)
                if (!p.bot)
                    refusedDuringSeat.put(p.name, state.refusesLateStart(p.name));
            Set<String> participants = new LinkedHashSet<>(), humans = new LinkedHashSet<>();
            for (StartPhase.Pick p : finished.picks) {
                String name = p.bot ? "gametest_ms_ai" + p.slot : p.name;
                participants.add(name);
                if (!p.bot)
                    humans.add(name);
            }
            return new MultiplayerMatch(participants, humans);
        }

        @Override
        public void cancelPreparation() {
            calls.add("cancel");
        }

        @Override
        public void showMap(MapPreset preset, int cx, int cz, int radius) {
            calls.add("map:" + preset.name());
        }

        @Override
        public void say(String message) {
            calls.add("say");
        }
    }

    /** A 1v1 lineup: a human on team 0, a bot on team 1. */
    static List<MultiplayerLobby.Seat> lineup(String human) {
        MultiplayerLobby.Seat h = new MultiplayerLobby.Seat(91_001, false);
        h.name = human;
        h.uuid = UUID.nameUUIDFromBytes(human.getBytes());
        h.faction = 0;
        h.team = 0;
        h.ready = true;
        MultiplayerLobby.Seat b = new MultiplayerLobby.Seat(91_002, true);
        b.name = "AI";
        b.faction = 1;
        b.team = 1;
        b.ready = true;
        b.difficulty = 2;
        return List.of(h, b);
    }

    /** Through real NBT bytes and back into a fresh state, as a world save and load would. */
    static MatchState reload(MatchState state, MatchEffects effects) throws IOException {
        return MatchState.load(SkirmishGameTests.bytesRoundTrip(state.save()), effects);
    }

    /**
     * code_review CR-13 and the phase machine: a skirmish on a pre-made map with start positions walks LOBBY ->
     * MAP_RISING -> START_POSITIONS -> PLAYING -> ENDED -> LOBBY, and every phase survives a save and load through real
     * NBT bytes with what it needs to go on: the frozen setup (seed, base height, seats and their colours), how far the
     * map got (a build below its economy stage is re-run, one past it continues to the starts), every pick and the
     * clock (the resumed phase holds until a human is back), the seated match's sides and humans (registered again),
     * the match clock and the winners. A second prepare is refused while a match is being set up.
     */
    public static void match_state_walks_every_phase_and_each_survives_a_reload(GameTestHelper helper) throws IOException {
        ServerLevel level = helper.getLevel();
        String human = "gametest_ms_h";
        Recorder fx = new Recorder(level);
        MatchState s = new MatchState(fx);
        List<String> problems = new ArrayList<>();
        MultiplayerMatch[] registered = { null };
        try {
            MatchState r0 = reload(s, fx);
            if (r0.phase() != MatchPhase.LOBBY || r0.resumePlan() != MatchState.ResumePlan.NOTHING)
                problems.add("LOBBY did not reload as LOBBY with nothing to resume");

            BlockPos centre = helper.absolutePos(new BlockPos(8, 1, 8));
            MatchSetup setup = MatchSetup.create(level, lineup(human), MapPreset.HIGHLANDS, true, 1, 1, 1, centre,
                new Random(5), MatchState.Origin.SKIRMISH);
            if (!s.prepare(setup, null) || s.phase() != MatchPhase.MAP_RISING || !fx.calls.contains("raise"))
                problems.add("a pre-made map did not start rising (phase " + s.phase() + ", calls " + fx.calls + ")");
            if (s.prepare(setup, null))
                problems.add("a second match was prepared while one was rising");
            if (!s.isPreparing() || !s.refusesLateStart("anyone"))
                problems.add("a late start was allowed while the map rose");

            // MAP_RISING: below the economy stage the build is run again, past it the match goes on to the starts
            fx.buildStage = 1;
            MatchState r1 = reload(s, fx);
            MatchSetup back = r1.setup();
            if (r1.phase() != MatchPhase.MAP_RISING || r1.resumePlan() != MatchState.ResumePlan.REBUILD_MAP)
                problems.add("a half-raised map does not rebuild after a reload: " + r1.phase() + " / " + r1.resumePlan());
            if (back == null || back.seed != setup.seed || back.baseY != setup.baseY || back.preset != setup.preset
                    || back.lineup.size() != setup.lineup.size() || !back.choosePositions)
                problems.add("the setup changed through the save");
            else
                for (int i = 0; i < setup.lineup.size(); i++) {
                    MultiplayerLobby.Seat a = setup.lineup.get(i), b = back.lineup.get(i);
                    if (!a.name.equals(b.name) || a.team != b.team || a.colorMapId != b.colorMapId || a.bot != b.bot
                            || a.faction != b.faction || !java.util.Objects.equals(a.uuid, b.uuid))
                        problems.add("seat " + a.name + " changed through the save");
                }
            fx.buildStage = 3;
            Recorder fx3 = new Recorder(level);
            MatchState r1c = reload(s, fx3);
            if (r1c.resumePlan() != MatchState.ResumePlan.AFTER_MAP)
                problems.add("a map with its economy stamped would be stamped twice after a reload");
            r1c.resume();
            if (fx3.calls.contains("raise:resumed") || r1c.phase() != MatchPhase.START_POSITIONS || !fx3.calls.contains("phase"))
                problems.add("a map saved on its last tick did not go straight on to the starts: " + fx3.calls);
            fx.buildStage = 1;   // the save asks the live build (s's effects) how far it got
            Recorder fx1 = new Recorder(level);
            MatchState r1b = reload(s, fx1);
            r1b.resume();
            if (!fx1.calls.contains("raise:resumed") || r1b.phase() != MatchPhase.MAP_RISING || r1b.resumeNotice() == null)
                problems.add("resuming the rising map did not raise it again: " + fx1.calls);

            // START_POSITIONS: the bot is placed at once, the human places after a while; then a save
            s.mapRaised();
            StartPhase ph = s.startPhase();
            if (s.phase() != MatchPhase.START_POSITIONS || ph == null || !fx.calls.contains("phase")) {
                problems.add("the map did not hand over to start positions (phase " + s.phase() + ")");
                return;
            }
            for (int t = 0; t < 40; t++)
                ph.tick();
            StartPhase.Pick mine = ph.pickOf(human);
            if (mine == null) {
                problems.add("the human has no pick in the start phase");
                return;
            }
            var slot = setup.layout().slots.get(mine.slot);
            mine.x = setup.cx + (int) Math.round(slot.dx() * 0.93);
            mine.z = setup.cz + (int) Math.round(slot.dz() * 0.93);
            mine.placed = true;
            Recorder fx2 = new Recorder(level);
            MatchState r2 = reload(s, fx2);
            if (r2.resumePlan() != MatchState.ResumePlan.CONTINUE_START_PHASE)
                problems.add("start positions do not continue after a reload: " + r2.resumePlan());
            r2.resume();
            StartPhase rph = r2.startPhase();
            if (rph == null || !fx2.calls.contains("phase:resumed")) {
                problems.add("the resumed start phase did not go live: " + fx2.calls);
            } else {
                StartPhase.Pick rmine = rph.pickOf(human);
                if (rmine == null || !rmine.placed || rmine.x != mine.x || rmine.z != mine.z)
                    problems.add("the human's placed start was lost in the reload");
                if (rph.ticksLeft() != ph.ticksLeft())
                    problems.add("the clock changed in the reload: " + rph.ticksLeft() + " vs " + ph.ticksLeft());
                rph.setOnlineCheck(n -> false);
                for (int t = 0; t < 100; t++)
                    rph.tick();
                if (!rph.isHolding() || rph.ticksLeft() != ph.ticksLeft() || rph.isFinished())
                    problems.add("the resumed phase ran with nobody back (holding " + rph.isHolding() + ", "
                        + rph.ticksLeft() + " ticks left)");
                rph.setOnlineCheck(n -> true);
                rph.tick();
                if (rph.isHolding() || rph.ticksLeft() != Math.max(ph.ticksLeft(), StartPhase.RESUME_FLOOR_SECONDS * 20) - 1)
                    problems.add("the resumed phase did not run on once a human was back (" + rph.ticksLeft() + " ticks left)");
            }

            // PLAYING: the phase ends, everyone is seated
            ph.finish();
            if (s.phase() != MatchPhase.PLAYING || !fx.calls.contains("seat") || s.match() == null
                    || !s.match().humans.contains(human) || s.startPhase() != null || s.setup() != null) {
                problems.add("the finished phase did not seat the match (phase " + s.phase() + ")");
                return;
            }
            // lovish's Oct 13 playtest: the seat's own startRTS for the human was refused as a late start (the phase
            // was still START_POSITIONS), so only the bot was seated and he watched his own match
            if (!Boolean.FALSE.equals(fx.refusedDuringSeat.get(human)))
                problems.add("the human was refused as a late start while the setup seated them: " + fx.refusedDuringSeat);
            for (int t = 0; t < 1234; t++)
                s.advanceClock(true);
            MatchState r3 = reload(s, new Recorder(level));
            if (r3.phase() != MatchPhase.PLAYING || r3.clock() != 1234 || r3.match() == null
                    || !r3.match().participants.equals(s.match().participants) || !r3.match().humans.equals(s.match().humans)
                    || r3.resumePlan() != MatchState.ResumePlan.REJOIN_MATCH || r3.mapPreset() != MapPreset.HIGHLANDS)
                problems.add("a match in play changed through the save (clock " + r3.clock() + ", map " + r3.mapPreset() + ")");
            r3.resume();
            registered[0] = r3.match();
            if (r3.match() == null || MultiplayerMatch.of(human) != r3.match())
                problems.add("the resumed match did not take its sides back (victory scope)");

            // ENDED: the winners are kept, the decided match scopes nothing
            s.onDecided(new MultiplayerMatch(Set.of(human), Set.of(human)), Set.of(human));
            if (s.phase() != MatchPhase.PLAYING)
                problems.add("another match's result ended this one");
            s.onDecided(s.match(), Set.of(human));
            // its sides get the results again on logging in (client memory), timed at the decision, nobody else does
            if (s.endClock() != 1234 || !s.owesResults(human) || s.owesResults("gametest_ms_stranger"))
                problems.add("the decided match does not owe its sides their results (end clock " + s.endClock() + ")");
            MatchState r4 = reload(s, new Recorder(level));
            if (r4.phase() != MatchPhase.ENDED || !r4.winners().equals(Set.of(human)) || r4.match() == null
                    || !r4.match().isOver() || r4.resumePlan() != MatchState.ResumePlan.NOTHING)
                problems.add("an ended match changed through the save (phase " + r4.phase() + ", winners " + r4.winners() + ")");
            if (r4.endClock() != 1234 || !r4.owesResults(human) || r4.owesResults("gametest_ms_stranger"))
                problems.add("the results owed lost their time or their sides in the save (end clock " + r4.endClock() + ")");

            s.reset();
            if (s.phase() != MatchPhase.LOBBY || s.clock() != 0 || s.mapPreset() != MapPreset.RANDOM || s.match() != null
                    || s.endClock() != 0 || s.owesResults(human))
                problems.add("a reset left something of the match behind");
        } finally {
            if (registered[0] != null)
                registered[0].end();
            if (problems.isEmpty())
                helper.succeed();
            else
                helper.fail(String.join("; ", problems));
        }
    }

    /**
     * A resumed start phase (StartPhase.load): every spot and the clock come back, the clock waits for a human, then
     * runs at least RESUME_FLOOR_SECONDS whatever was left; "everyone placed" ends it early only once every human is
     * back; a save of other sides is refused and changes nothing.
     */
    public static void resumed_start_phase_holds_for_its_humans_and_keeps_a_floor(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos base = helper.absolutePos(new BlockPos(8, 1, 8));
        int cx = base.getX() + 40000, cz = base.getZ() + 40000;   // only the box rules are under test (as the old test)
        var layout = com.solegendary.reignofnether.maps.MapLayout.build(MapPreset.DESERT_MESA, new int[] { 0, 1, 1 });
        java.util.function.Supplier<StartPhase> fresh = () -> new StartPhase(level, cx, cz, layout, new ArrayList<>(List.of(
            new StartPhase.Pick("gametest_rsp_a", false, 0, 0, 0xFF0000, null),
            new StartPhase.Pick("gametest_rsp_b", false, 1, 1, 0x00FF00, null),
            new StartPhase.Pick("AI 1", true, 1, 2, 0x0000FF, null))), 45, ph -> { });
        List<String> problems = new ArrayList<>();
        StartPhase before = fresh.get();
        for (StartPhase.Pick p : before.picks)
            p.placed = true;   // both humans placed (the bot always is)
        CompoundTag saved = before.save();
        saved.putInt("ticksLeft", 30);   // saved with a second and a half left

        StartPhase after = fresh.get();
        Set<String> online = new LinkedHashSet<>();
        after.setOnlineCheck(online::contains);
        if (!after.load(saved) || !after.isResumed() || !after.isHolding())
            problems.add("the saved phase did not load as a held, resumed phase");
        for (int t = 0; t < 200; t++)
            after.tick();
        if (after.isFinished() || after.ticksLeft() != 30)
            problems.add("the clock ran with nobody back: " + after.ticksLeft());
        online.add("gametest_rsp_a");
        after.tick();
        if (after.isHolding() || after.ticksLeft() != StartPhase.RESUME_FLOOR_SECONDS * 20 - 1)
            problems.add("one human back did not restart the clock with the floor: " + after.ticksLeft());
        for (int t = 0; t < StartPhase.EARLY_END_TICKS + 5; t++)
            after.tick();
        if (after.isFinished())
            problems.add("everyone placed ended a resumed phase early while a human was still away");
        online.add("gametest_rsp_b");
        for (int t = 0; t < StartPhase.EARLY_END_TICKS + 2; t++)
            after.tick();
        if (!after.isFinished())
            problems.add("with every human back and placed the phase did not end early");

        StartPhase other = new StartPhase(level, cx, cz, layout, new ArrayList<>(List.of(
            new StartPhase.Pick("gametest_rsp_x", false, 0, 0, 0xFF0000, null),
            new StartPhase.Pick("AI 1", true, 1, 1, 0x0000FF, null))), 45, ph -> { });
        int ticksBefore = other.ticksLeft();
        if (other.load(saved) || other.isResumed() || other.ticksLeft() != ticksBefore)
            problems.add("a save of other sides was taken");
        if (problems.isEmpty())
            helper.succeed();
        else
            helper.fail(String.join("; ", problems));
    }

    /**
     * code_review CR-13's other half: a setup that can't be resumed as saved is cancelled cleanly - back to LOBBY,
     * whatever was being prepared dropped, a notice for whoever logs in - never left half-way. Covers a rising map
     * whose setup is missing, a map this build doesn't know, start positions saved without their picks or with
     * another lineup's, and an unknown phase.
     */
    public static void a_setup_that_cannot_resume_is_cancelled_cleanly(GameTestHelper helper) throws IOException {
        ServerLevel level = helper.getLevel();
        List<String> problems = new ArrayList<>();
        Recorder fx = new Recorder(level);
        MatchState s = new MatchState(fx);
        MatchSetup setup = MatchSetup.create(level, lineup("gametest_ms_c"), MapPreset.HIGHLANDS, true, 1, 1, 1,
            helper.absolutePos(new BlockPos(8, 1, 8)), new Random(9), MatchState.Origin.LOBBY);
        s.prepare(setup, null);
        CompoundTag rising = SkirmishGameTests.bytesRoundTrip(s.save());

        CompoundTag noSetup = rising.copy();
        noSetup.remove("setup");
        CompoundTag unknownMap = rising.copy();
        unknownMap.getCompound("setup").putString("preset", "NOT_A_MAP");
        s.mapRaised();   // start positions
        CompoundTag placing = SkirmishGameTests.bytesRoundTrip(s.save());
        CompoundTag noPicks = placing.copy();
        noPicks.remove("startPhase");
        CompoundTag otherPicks = placing.copy();
        otherPicks.getCompound("startPhase").put("picks", new ListTag());

        for (var c : List.of(new Object[] { "missing setup", noSetup }, new Object[] { "unknown map", unknownMap },
                new Object[] { "no picks", noPicks }, new Object[] { "another lineup's picks", otherPicks })) {
            Recorder rfx = new Recorder(level);
            MatchState r = MatchState.load((CompoundTag) c[1], rfx);
            r.resume();
            if (r.phase() != MatchPhase.LOBBY || !rfx.calls.contains("cancel") || r.resumeNotice() == null
                    || r.setup() != null || r.startPhase() != null)
                problems.add(c[0] + ": not cancelled cleanly (phase " + r.phase() + ", calls " + rfx.calls + ")");
        }
        CompoundTag unknownPhase = placing.copy();
        unknownPhase.putString("phase", "NOT_A_PHASE");
        if (MatchState.load(unknownPhase, fx).phase() != MatchPhase.LOBBY)
            problems.add("an unknown phase did not read as LOBBY");
        s.reset();
        if (problems.isEmpty())
            helper.succeed();
        else
            helper.fail(String.join("; ", problems));
    }

    /**
     * One owner for phase changes: the transition table (only forward while preparing, LOBBY from anywhere, a new
     * match over an ENDED one), and MatchState ignoring what doesn't fit its phase - a map that rose with nothing
     * rising, starts placed with no setup, a result for another match, a side joining mid-setup.
     */
    public static void match_phases_move_only_along_the_table(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        Object[][] table = {
            { MatchPhase.LOBBY, MatchPhase.MAP_RISING, true }, { MatchPhase.LOBBY, MatchPhase.ENDED, false },
            { MatchPhase.MAP_RISING, MatchPhase.START_POSITIONS, true }, { MatchPhase.MAP_RISING, MatchPhase.ENDED, false },
            { MatchPhase.START_POSITIONS, MatchPhase.MAP_RISING, false }, { MatchPhase.START_POSITIONS, MatchPhase.PLAYING, true },
            { MatchPhase.PLAYING, MatchPhase.MAP_RISING, false }, { MatchPhase.PLAYING, MatchPhase.ENDED, true },
            { MatchPhase.ENDED, MatchPhase.PLAYING, true }, { MatchPhase.ENDED, MatchPhase.LOBBY, true },
            { MatchPhase.PLAYING, MatchPhase.LOBBY, true }, { MatchPhase.LOBBY, MatchPhase.LOBBY, false } };
        for (Object[] row : table)
            if (MatchPhase.canMove((MatchPhase) row[0], (MatchPhase) row[1]) != (Boolean) row[2])
                problems.add(row[0] + " -> " + row[1] + " should be " + row[2]);
        Recorder fx = new Recorder(helper.getLevel());
        MatchState s = new MatchState(fx);
        s.mapRaised();
        s.onDecided(null, Set.of("gametest_ms_nobody"));
        if (s.phase() != MatchPhase.LOBBY)
            problems.add("an idle match moved on a stray signal: " + s.phase());
        MatchSetup setup = MatchSetup.create(helper.getLevel(), lineup("gametest_ms_t"), MapPreset.HIGHLANDS, true, 1, 1, 1,
            helper.absolutePos(new BlockPos(8, 1, 8)), new Random(3), MatchState.Origin.LOBBY);
        s.prepare(setup, null);
        s.sideJoined("gametest_ms_stranger");
        if (s.phase() != MatchPhase.MAP_RISING)
            problems.add("a side starting by hand mid-setup changed the phase: " + s.phase());
        s.reset();
        if (s.phase() != MatchPhase.LOBBY || !fx.calls.contains("cancel"))
            problems.add("a reset mid-setup did not cancel it");
        s.sideJoined("gametest_ms_classic");
        if (s.phase() != MatchPhase.PLAYING || s.origin() != MatchState.Origin.CLASSIC || s.refusesLateStart("anyone"))
            problems.add("the first side of an idle world did not start an open classic match");
        if (problems.isEmpty())
            helper.succeed();
        else
            helper.fail(String.join("; ", problems));
    }

    /**
     * Saved with the match (MatchLifecycleSaveData): the alliances between its sides - before, a reloaded team game
     * turned teammates into enemies - each pair once, restored idempotently.
     */
    public static void alliances_between_the_sides_survive_a_reload(GameTestHelper helper) throws IOException {
        String a = "gametest_ms_ally_a", b = "gametest_ms_ally_b", c = "gametest_ms_ally_c";
        AlliancesServerEvents.addAlliance(a, b);
        try {
            CompoundTag tag = new CompoundTag();
            tag.put("pairs", MatchLifecycleSaveData.alliancePairs(new LinkedHashSet<>(List.of(a, b, c))));
            ListTag pairs = SkirmishGameTests.bytesRoundTrip(tag).getList("pairs", net.minecraft.nbt.Tag.TAG_LIST);
            AlliancesServerEvents.removeAlliance(a, b);
            int restored = MatchLifecycleSaveData.restoreAlliances(pairs);
            MatchLifecycleSaveData.restoreAlliances(pairs);   // twice: idempotent
            if (pairs.size() != 1 || restored != 1)
                helper.fail("expected exactly the one alliance a-b, saved " + pairs.size() + ", restored " + restored);
            else if (!AlliancesServerEvents.isAllied(a, b) || !AlliancesServerEvents.isAllied(b, a))
                helper.fail("the saved alliance did not come back");
            else if (AlliancesServerEvents.isAllied(a, c) || AlliancesServerEvents.isAllied(b, c))
                helper.fail("an alliance appeared that was never made");
            else
                helper.succeed();
        } finally {
            AlliancesServerEvents.removeAlliance(a, b);
        }
    }

    /**
     * The roster is a snapshot to walk (MatchRoster, copy-on-write): beating sides from inside a loop over
     * PlayerServerEvents.rtsPlayers - what a beacon victory does - finishes the loop. With RoN's synchronized list
     * the second defeat threw ConcurrentModificationException.
     */
    public static void the_roster_survives_a_defeat_inside_a_walk_over_it(GameTestHelper helper) {
        String x = "gametest_ms_walk_x", y = "gametest_ms_walk_y", z = "gametest_ms_walk_z";
        side(x, 1_100_051);
        side(y, 1_100_052);
        side(z, 1_100_053);
        MultiplayerMatch match = new MultiplayerMatch(Set.of(x, y, z), Set.of()).register();
        Set<String> mine = Set.of(x, y);
        try {
            int seen = 0;
            for (RTSPlayer p : PlayerServerEvents.rtsPlayers)
                if (mine.contains(p.name)) {
                    seen++;
                    PlayerServerEvents.defeat(p.name, "gametest");
                }
            if (seen != 2)
                helper.fail("the walk saw " + seen + " of the two sides it beat");
            else if (PlayerServerEvents.isRTSPlayer(x) || PlayerServerEvents.isRTSPlayer(y) || !finished(z) || !match.isOver())
                helper.fail("the sides beaten inside the walk did not leave, or the last one did not win");
            else
                helper.succeed();
        } catch (java.util.ConcurrentModificationException e) {
            helper.fail("a defeat inside a walk over the roster threw " + e);
        } finally {
            forget(x, y, z);
            match.end();
        }
    }

    /**
     * A beaten base is taken down over a few seconds (DefeatCleanupServerEvents), in memory only: a world saved
     * half-way used to reload with the rest of it standing ownerless and shootable for good. On server stop the queue
     * is finished before the final save - here for one side: at once, the building is a half-metal wreck and the
     * unit is dead, with nothing left queued.
     */
    public static void a_beaten_base_is_cleared_at_once_before_the_world_saves(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = "gametest_ml_stop", enemy = "gametest_ml_stop_e";
        BlockPos c = pad(helper, 232);
        int y = c.getY();
        side(owner, 1_100_061);
        side(enemy, 1_100_062);
        ResourcesServerEvents.assignResources(owner);
        MultiplayerMatch match = new MultiplayerMatch(Set.of(owner, enemy), Set.of()).register();
        var wind = Buildings.WIND_GENERATOR_VILLAGERS;
        BuildingPlacement site = BuildingServerEvents.placeBuilding(wind, new BlockPos(c.getX() - 2, y, c.getZ() - 2),
            Rotation.NONE, owner, new int[0], false, false, true, true);
        LivingEntity unit = soldier(level, owner, c.getX() + 6.5, y + 1, c.getZ() + 6.5);
        helper.runAfterDelay(3, () -> {
            List<String> problems = new ArrayList<>();
            try {
                if (site == null || unit == null) {
                    problems.add("could not set up the base (site " + site + ", unit " + unit + ")");
                    return;
                }
                PlayerServerEvents.defeat(owner, "gametest");
                if (!DefeatCleanupServerEvents.isCleaningUp(owner))
                    problems.add("no cleanup was queued for the beaten side");
                DefeatCleanupServerEvents.finish(owner);
                if (DefeatCleanupServerEvents.isCleaningUp(owner))
                    problems.add("the cleanup was not finished at once");
                if (BuildingServerEvents.getBuildings().contains(site))
                    problems.add("the beaten side's building still stands");
                boolean wreck = false;
                for (Entity w : WreckServerEvents.getWrecks())
                    if (!w.isRemoved() && w.distanceToSqr(site.centrePos.getX() + 0.5, y, site.centrePos.getZ() + 0.5) < 3 * 3
                            && WreckServerEvents.metalOf(w) >= wind.cost.metal() * DefeatCleanupServerEvents.BUILDING_WRECK_SHARE - 0.01f)
                        wreck = true;
                if (!wreck)
                    problems.add("no half-metal wreck where the building stood");
                if (unit.isAlive() && !unit.isRemoved())
                    problems.add("the beaten side's unit is still alive");
            } finally {
                forget(owner, enemy);
                match.end();
                if (site != null)
                    BuildingServerEvents.getBuildings().remove(site);
                if (unit != null)
                    unit.discard();
                for (Entity w : new ArrayList<>(WreckServerEvents.getWrecks()))
                    if (!w.isRemoved() && Math.abs(w.getY() - y) < 4 && w.distanceToSqr(c.getX() + 0.5, y, c.getZ() + 0.5) < 13 * 13)
                        w.discard();
                if (problems.isEmpty())
                    helper.succeed();
                else
                    helper.fail(String.join("; ", problems));
            }
        });
    }

    /**
     * Spectator polish (design/playtest_oct12.md, "BAR's spectator"): the observer table's population comes from the
     * server now - counting the units its own client had loaded, a far bot read 0. The figures ride the player panel's
     * existing gate (a spectator gets every side's, an RTS player only its own and its allies') and its codec.
     */
    public static void spectators_get_every_sides_population_and_enemies_do_not(GameTestHelper helper) {
        String me = "gametest_ml_pop_me", foe = "gametest_ml_pop_foe", watcher = "gametest_ml_pop_watcher";
        List<com.solegendary.reignofnether.player.PlayerPanelServerEvents.Row> rows = List.of(
            new com.solegendary.reignofnether.player.PlayerPanelServerEvents.Row(foe, "", true, 9, 90, 0.5f, 0, 0, 0, 0,
                1500, 40, 7, 3, 3, 2, 57, 12, 45),
            new com.solegendary.reignofnether.player.PlayerPanelServerEvents.Row(me, "", true, 2, 20, 0.25f, 0, 0, 0, 0,
                300, 12, 1, 2, 1, 0, 20, 8, 12));
        side(me, 1_100_071);   // in play: "me" is an RTS player, the watcher is not
        try {
            var forWatcher = com.solegendary.reignofnether.player.PlayerPanelServerEvents.entriesFor(watcher, rows);
            var forMe = com.solegendary.reignofnether.player.PlayerPanelServerEvents.entriesFor(me, rows);
            var buf = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
            new com.solegendary.reignofnether.player.PlayerPanelClientboundPacket(forWatcher).encode(buf);
            var decoded = com.solegendary.reignofnether.player.PlayerPanelClientboundPacket.decode(buf).entries;
            List<String> problems = new ArrayList<>();
            for (var list : List.of(forWatcher, decoded))
                for (var e : list)
                    if (e.name().equals(foe) && (e.popUsed() != 57 || e.popWorkers() != 12 || e.popArmy() != 45))
                        problems.add("a spectator's line for the enemy lacks its population: " + e);
            for (var e : forMe) {
                if (e.name().equals(foe) && (e.popUsed() != 0 || e.popWorkers() != 0 || e.popArmy() != 0))
                    problems.add("an RTS player got the enemy's population: " + e);
                if (e.name().equals(me) && (e.popUsed() != 20 || e.popWorkers() != 8 || e.popArmy() != 12))
                    problems.add("an RTS player lost its own population: " + e);
            }
            if (problems.isEmpty())
                helper.succeed();
            else
                helper.fail(String.join("; ", problems));
        } finally {
            forget(me);
        }
    }

    /**
     * Defeat reasons that callers passed as bare translation keys printed raw ("X has
     * server.reignofnether.defeat_commander and is defeated!"); they read as the sentence's fragment now, and plain
     * text stays as it is.
     */
    public static void defeat_reason_keys_read_as_sentence_fragments(GameTestHelper helper) {
        com.google.gson.JsonObject lang = SkirmishGameTests.enUsLang();
        List<String> problems = new ArrayList<>();
        for (String[] k : new String[][] { { "server.reignofnether.defeat_commander", "server.reignofnether.lost_commander" },
                { "server.reignofnether.defeat_bot_removed", "server.reignofnether.was_removed" },
                { "server.reignofnether.surrendered", "server.reignofnether.surrendered" } }) {
            var text = MatchOutcome.reasonText(k[0]);
            if (!(text.getContents() instanceof TranslatableContents tc) || !tc.getKey().equals(k[1]))
                problems.add(k[0] + " does not read as " + k[1]);
            else if (lang.size() > 0 && !lang.has(k[1]))
                problems.add(k[1] + " has no en_us text");
        }
        if (!MatchOutcome.reasonText("gametest").getString().equals("gametest"))
            problems.add("a plain-text reason was changed");
        if (problems.isEmpty())
            helper.succeed();
        else
            helper.fail(String.join("; ", problems));
    }
}
