package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.alliance.AllyCommand;
import com.solegendary.reignofnether.matchstart.MultiplayerLobby;
import com.solegendary.reignofnether.matchstart.MultiplayerMatch;
import com.solegendary.reignofnether.player.PlayerServerEvents;
import com.solegendary.reignofnether.registrars.GameRuleRegistrar;
import com.solegendary.reignofnether.resources.MetalPatches;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameRules;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Locked alliances (lovish, Oct 27, D032: "Lock Alliances, but enable an option to allow alliances to not be locked").
 * As in Beyond All Reason the teams are fixed once the match starts: in a match nobody may /ally, /allyconfirm or
 * /disband (FFA enemies could otherwise ally to share a win, a teammate could leave the team mid-game). The lock is the
 * saved game rule lockAlliances, on by default; the lobby's "Alliances" option writes it at Start, and "Open" keeps
 * the old free diplomacy. Teams are formed by the match start itself (AlliancesServerEvents.addAlliance, not the
 * command), so the lock never stops a team from forming.
 *
 * <p>Each case runs a real lobby start (MultiplayerLobby.startMatch) with three fake humans - A and B on team 1, C on
 * team 2 - and then the commands' own bodies as those players (AllyCommand.allyAs, allyConfirmAs, disbandAs): the
 * dispatcher would look the player argument up in the server's player list, which fakes cannot join (its getPlayers()
 * is unmodifiable). Every change (the rule, the alliances, the match) is undone before the tick ends, so the batch's
 * other test never sees them. Own batch.
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class AllianceLockGameTests {

    static final String ARENA = SkirmishGameTests.ARENA;
    static final String BATCH = "alliance_lock";

    static ServerPlayer fake(ServerLevel level, String name) {
        return FakePlayerFactory.get(level, new GameProfile(UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8)), name));
    }

    static MultiplayerLobby.Seat seat(int id, ServerPlayer sp, int team, int faction) {
        MultiplayerLobby.Seat s = new MultiplayerLobby.Seat(id, false);
        s.name = sp.getName().getString();
        s.uuid = sp.getUUID();
        s.team = team;
        s.faction = faction;
        return s;
    }

    /**
     * Runs /{@code command} {@code other} as {@code sp}: the command's own body (AllyCommand.*As) with the players its
     * argument would have looked up. Fakes cannot be looked up - the server's player list is unmodifiable from here.
     */
    static void run(ServerPlayer sp, String command, ServerPlayer other) {
        var source = sp.createCommandSourceStack();
        switch (command) {
            case "ally" -> AllyCommand.allyAs(source, sp, other);
            case "allyconfirm" -> AllyCommand.allyConfirmAs(source, sp, other);
            case "disband" -> AllyCommand.disbandAs(source, sp, other);
            default -> throw new IllegalArgumentException(command);
        }
    }

    static boolean pending(String to, String from) {
        return AlliancesServerEvents.pendingAlliances.getOrDefault(to, Set.of()).contains(from);
    }

    /**
     * Dry ground for three lobby bases, out past {@code dx} blocks from the arena; null when there is none. 36 centres
     * along z, 500 apart: siteOk reads real ground (it loads the chunk), and six could all be sea (flake-stress
     * 40754233086, "no dry ground for three lobby bases" 3 in 16). The x stays {@code dx}, so the batch's two cases stay
     * 1000 apart.
     */
    static BlockPos dryCentre(ServerLevel level, BlockPos origin, int dx) {
        for (int k = 0; k < 36; k++) {
            int cx = origin.getX() + dx, cz = origin.getZ() - 1500 - 500 * k;
            BlockPos c = new BlockPos(cx, MetalPatches.solidTop(level, cx, cz), cz);
            boolean dry = true;
            for (var p : MultiplayerLobby.startPositions(level, c, 3, 1, 0))
                if (!MultiplayerLobby.siteOk(level, (int) Math.floor(p.x), (int) Math.floor(p.z)))
                    dry = false;
            if (dry)
                return c;
        }
        return null;
    }

    /** A throwable and the frames that threw it, for a problem line. */
    static String where(Throwable e) {
        StringBuilder sb = new StringBuilder(String.valueOf(e));
        StackTraceElement[] st = e.getStackTrace();
        for (int i = 0; i < Math.min(8, st.length); i++)
            sb.append(" at ").append(st[i]);
        return sb.toString();
    }

    interface Checks {
        void run(ServerPlayer a, ServerPlayer b, ServerPlayer c, List<String> problems);
    }

    /**
     * Starts a 2v1 lobby match with the lock rule at {@code lock} (null: the rule's registered default, a world that
     * never set it), runs {@code checks}, then undoes everything: alliances, pending requests, the match, the rule.
     */
    static void lobbyMatch(GameTestHelper helper, String tag, int dx, Boolean lock, Checks checks) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        ServerPlayer a = fake(level, "gt_lk" + tag + "_a"), b = fake(level, "gt_lk" + tag + "_b"), c = fake(level, "gt_lk" + tag + "_c");
        String nA = a.getName().getString(), nB = b.getName().getString(), nC = c.getName().getString();
        BlockPos origin = helper.absolutePos(new BlockPos(8, 1, 8));

        helper.runAfterDelay(20, () -> {
            BlockPos centre = dryCentre(level, origin, dx);
            if (centre == null) {
                helper.fail("no dry ground for three lobby bases near x=" + (origin.getX() + dx));
                return;
            }
            GameRules.BooleanValue rule = server.getGameRules().getRule(GameRuleRegistrar.LOCK_ALLIANCES);
            boolean was = rule.get();
            List<String> problems = new ArrayList<>();
            MultiplayerMatch match = null;
            try {
                rule.set(lock != null ? lock : new GameRules().getRule(GameRuleRegistrar.LOCK_ALLIANCES).get(), server);
                PlayerServerEvents.players.addAll(List.of(a, b, c));
                match = MultiplayerLobby.startMatch(level, List.of(seat(900301, a, 0, 0), seat(900302, b, 0, 1),
                    seat(900303, c, 1, 0)), 0, 1, 1, centre, new Random(17));
                for (String n : List.of(nA, nB, nC))
                    if (!PlayerServerEvents.isRTSPlayer(n))
                        problems.add("test setup: " + n + " is not an RTS player after the lobby start");
                if (!AlliancesServerEvents.isAllied(nA, nB))
                    problems.add("the lobby start did not ally team 1 (A and B)");
                if (AlliancesServerEvents.isAllied(nA, nC) || AlliancesServerEvents.isAllied(nB, nC))
                    problems.add("test setup: the two teams are allied at the start");
                if (problems.isEmpty())
                    checks.run(a, b, c, problems);
            } catch (RuntimeException e) {
                // the framework reports a throw by its class alone (CI showed a bare UnsupportedOperationException):
                // say where it came from
                problems.add("threw " + where(e));
            } finally {
                try {
                    for (String x : List.of(nA, nB, nC)) {
                        for (String y : List.of(nA, nB, nC))
                            if (!x.equals(y) && AlliancesServerEvents.isAllied(x, y))
                                AlliancesServerEvents.removeAlliance(x, y);
                        AlliancesServerEvents.pendingAlliances.remove(x);
                    }
                    // as LobbyColorsGameTests: each side leaves through a match of its own, so no defeat crowns another
                    for (String n : List.of(nC, nB, nA)) {
                        new MultiplayerMatch(Set.of(n), Set.of(n)).register();
                        PlayerServerEvents.defeat(n, "gametest");
                        MultiplayerMatch leftover = MultiplayerMatch.of(n);
                        if (leftover != null)
                            leftover.end();
                    }
                    if (match != null && !match.isOver())
                        match.end();
                    PlayerServerEvents.players.removeAll(List.of(a, b, c));
                    rule.set(was, server);
                } catch (RuntimeException e) {
                    problems.add("the clean-up threw " + where(e));
                }
            }
            TerrainGameTests.finish(helper, problems);
        });
    }

    /**
     * Default settings: the lock is on. In the match a teammate's /disband is refused (the team stays allied), an
     * enemy's /ally sends no request, and a request left over from before the match cannot be accepted - so two FFA
     * enemies cannot ally to share a win.
     */
    @GameTest(template = ARENA, timeoutTicks = 200, batch = BATCH)
    public static void alliance_lock_by_default_a_match_refuses_ally_disband_and_confirm(GameTestHelper helper) {
        List<String> problems0 = new ArrayList<>();
        if (!new GameRules().getRule(GameRuleRegistrar.LOCK_ALLIANCES).get())
            problems0.add("the lockAlliances game rule is off by default (D032: locked unless the lobby opens them)");
        lobbyMatch(helper, "d", -1500, null, (a, b, c, problems) -> {
            problems.addAll(problems0);
            String nA = a.getName().getString(), nB = b.getName().getString(), nC = c.getName().getString();
            run(a, "disband", b);
            if (!AlliancesServerEvents.isAllied(nA, nB))
                problems.add("/disband in a match broke team 1 (A and B are no longer allied)");
            run(a, "ally", c);
            if (pending(nC, nA))
                problems.add("/ally in a match sent the enemy a request");
            // a request made before the match (the lobby world, nobody an RTS player yet) must not be accepted in it
            AlliancesServerEvents.pendingAlliances.computeIfAbsent(nB, k -> new HashSet<>()).add(nC);
            run(b, "allyconfirm", c);
            if (AlliancesServerEvents.isAllied(nB, nC))
                problems.add("/allyconfirm in a match allied B with the enemy C");
        });
    }

    /**
     * The lobby option "Alliances: Open" (the rule written off at Start): diplomacy is free as before - a teammate may
     * /disband, enemies may /ally and /allyconfirm. The team the start formed is there until someone disbands it.
     */
    @GameTest(template = ARENA, timeoutTicks = 200, batch = BATCH)
    public static void alliance_lock_open_option_allows_ally_and_disband_in_a_match(GameTestHelper helper) {
        lobbyMatch(helper, "o", -2500, false, (a, b, c, problems) -> {
            String nA = a.getName().getString(), nB = b.getName().getString(), nC = c.getName().getString();
            run(a, "disband", b);
            if (AlliancesServerEvents.isAllied(nA, nB))
                problems.add("with alliances open, /disband did not break A and B's alliance");
            run(a, "ally", c);
            if (!pending(nC, nA))
                problems.add("with alliances open, /ally sent C no request");
            run(c, "allyconfirm", a);
            if (!AlliancesServerEvents.isAllied(nA, nC))
                problems.add("with alliances open, /allyconfirm did not ally A and C");
        });
    }

    /**
     * The lobby option's wiring: SET_ALLIANCES is appended to the lobby actions (they travel by ordinal, so an insert
     * would re-map every older client's clicks), and what Start does with the choice (AllyCommand.setLocked) is the
     * saved rule the commands read - both ways.
     */
    @GameTest(template = ARENA, timeoutTicks = 20, batch = BATCH)
    public static void alliance_lock_lobby_option_is_appended_and_writes_the_rule(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        GameRules.BooleanValue rule = server.getGameRules().getRule(GameRuleRegistrar.LOCK_ALLIANCES);
        boolean was = rule.get();
        List<String> problems = new ArrayList<>();
        if (MultiplayerLobby.Action.SET_ALLIANCES.ordinal() != MultiplayerLobby.Action.BACK_TO_LOBBY.ordinal() + 1)
            problems.add("SET_ALLIANCES is not appended after BACK_TO_LOBBY (lobby actions travel by ordinal)");
        try {
            com.solegendary.reignofnether.alliance.AllyCommand.setLocked(server, false);
            if (rule.get())
                problems.add("the lobby's 'Open' left the lockAlliances rule on");
            com.solegendary.reignofnether.alliance.AllyCommand.setLocked(server, true);
            if (!rule.get())
                problems.add("the lobby's 'Locked' left the lockAlliances rule off");
        } finally {
            rule.set(was, server);
        }
        TerrainGameTests.finish(helper, problems);
    }
}
