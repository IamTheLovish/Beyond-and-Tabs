package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.BuildingServerEvents;
import com.solegendary.reignofnether.building.Buildings;
import com.solegendary.reignofnether.faction.Factions;
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
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The match lifecycle (design/ron_rewrite_plan.md, rewrite 5): how a side loses, who wins, resigning, spectating.
 * The first half pins the behaviour the rewrite had to keep - written against the public API that existed before
 * it (PlayerServerEvents, CommanderServerEvents, ResignVoteServerEvents), so the same tests passed on the old code
 * and pass on the new match package.
 * <p>
 * Every test uses its own side names, scopes its victory check with its own MultiplayerMatch (several tests run side
 * by side in one world) and takes its sides, alliances, units and fake players out again in {@code finally}. Sides
 * are human RTS players (non-negative ids, no ServerPlayer behind most of them), never bots, so no bot brain wakes up
 * on a test's buildings.
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class MatchLifecycleGameTests {

    static final String ARENA = SkirmishGameTests.ARENA;

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
    @GameTest(template = ARENA, timeoutTicks = 200)
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
     * lovish's rule, team half: in a team game a fallen commander does not beat its side - the commander blast goes
     * off and the player fights on with what is left - and that holds for a second commander of the same team too,
     * as long as a teammate is still in the match.
     */
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void commander_death_in_a_team_game_fights_on(GameTestHelper helper) {
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
        helper.runAfterDelay(3, () -> com1.kill());
        helper.runAfterDelay(6, () -> com2.kill());
        helper.runAfterDelay(10, () -> {
            try {
                List<String> problems = new ArrayList<>();
                if (!PlayerServerEvents.isRTSPlayer(a1))
                    problems.add("a team player was beaten by losing their commander");
                if (!PlayerServerEvents.isRTSPlayer(a2))
                    problems.add("the second commander of a team beat its side while a teammate was still in");
                if (match.isOver() || finished(b))
                    problems.add("the enemy was declared the winner over a team that still fights");
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
    @GameTest(template = ARENA, timeoutTicks = 100)
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
    @GameTest(template = ARENA, timeoutTicks = 100)
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
    @GameTest(template = ARENA, timeoutTicks = 100)
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
}
