package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.matchstart.MultiplayerLobby;
import com.solegendary.reignofnether.matchstart.MultiplayerMatch;
import com.solegendary.reignofnether.player.PlayerColors;
import com.solegendary.reignofnether.player.PlayerPalette;
import com.solegendary.reignofnether.player.PlayerServerEvents;
import com.solegendary.reignofnether.player.RTSPlayer;
import com.solegendary.reignofnether.resources.MetalPatches;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Lobby colours (tester Joisco, Oct 26: "a box for color options in the lobby screen", BAR's colour square): each
 * side's colour box opens a palette; the pick travels in the lobby seat (MultiplayerLobby.Seat.colorMapId, synced by
 * LobbyClientboundPacket), becomes the side's RTSPlayer.startPosColorId at the start - the one colour PlayerColors
 * draws units, buildings, minimap, player list and results with - and is kept by Back to lobby. Two sides never
 * share a colour, and only the seat's owner (or the host, for bots) may change it. Own batch "lobby_colors".
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class LobbyColorsGameTests {

    static final String ARENA = SkirmishGameTests.ARENA;
    static final String BATCH = "lobby_colors";

    static MultiplayerLobby.Seat human(int id, String name, int team, int colour) {
        MultiplayerLobby.Seat s = new MultiplayerLobby.Seat(id, false);
        s.name = name;
        s.uuid = UUID.nameUUIDFromBytes(name.getBytes());
        s.team = team;
        s.colorMapId = colour;
        return s;
    }

    static MultiplayerLobby.Seat bot(int id, int team, int colour) {
        MultiplayerLobby.Seat s = new MultiplayerLobby.Seat(id, true);
        s.name = "AI";
        s.team = team;
        s.colorMapId = colour;
        s.ready = true;
        return s;
    }

    /** The {@code n}-th palette colour from the end that no side in play holds (other tests' sides share the server). */
    static int freeColour(int n, Set<Integer> also) {
        Set<Integer> taken = new HashSet<>(PlayerServerEvents.takenColorIds());
        taken.addAll(also);
        int found = 0;
        for (int i = PlayerPalette.COUNT - 1; i >= 0; i--) {
            int id = PlayerPalette.ENTRIES.get(i).mapColorId();
            if (!taken.contains(id) && found++ == n)
                return id;
        }
        return 0;
    }

    /**
     * A colour picked in the lobby is the side's colour in the match: started through the real lobby start
     * (MultiplayerLobby.startMatch) with two fake humans, each RTS player carries their pick as startPosColorId, and
     * PlayerColors (the colour every unit, building, minimap dot, player row and result line is drawn with) maps it
     * to the same palette colour.
     */
    @GameTest(template = ARENA, timeoutTicks = 200, batch = BATCH)
    public static void lobby_colours_picked_colour_reaches_player_colors_at_match_start(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String nameA = "gametest_col_a", nameB = "gametest_col_b";
        ServerPlayer fakeA = FakePlayerFactory.get(level, new GameProfile(UUID.nameUUIDFromBytes(nameA.getBytes()), nameA));
        ServerPlayer fakeB = FakePlayerFactory.get(level, new GameProfile(UUID.nameUUIDFromBytes(nameB.getBytes()), nameB));
        BlockPos origin = helper.absolutePos(new BlockPos(8, 1, 8));

        helper.runAfterDelay(20, () -> {
            // dry ground for both bases, far out from the other batches' arenas
            BlockPos centre = null;
            // three columns of six: siteOk now reads real ground (it loads the chunk), so an ocean can wet a whole column
            for (int k = 0; k < 18 && centre == null; k++) {
                int cx = origin.getX() - 1500 - 500 * (k / 6), cz = origin.getZ() + 1500 + 500 * (k % 6);
                BlockPos c = new BlockPos(cx, MetalPatches.solidTop(level, cx, cz), cz);
                boolean dry = true;
                for (var p : MultiplayerLobby.startPositions(level, c, 2, 1, 0))
                    if (!MultiplayerLobby.siteOk(level, (int) Math.floor(p.x), (int) Math.floor(p.z)))
                        dry = false;
                if (dry)
                    centre = c;
            }
            if (centre == null) {
                helper.fail("no dry ground for two lobby bases near x=" + (origin.getX() - 1500));
                return;
            }
            int pickA = freeColour(0, Set.of()), pickB = freeColour(1, Set.of());
            if (pickA == 0 || pickB == 0 || pickA == pickB) {
                helper.fail("no two free palette colours to pick (taken: " + PlayerServerEvents.takenColorIds() + ")");
                return;
            }
            List<String> problems = new ArrayList<>();
            var players = PlayerServerEvents.players;
            players.add(fakeA);
            players.add(fakeB);
            try {
                MultiplayerLobby.Seat seatA = human(900101, nameA, 0, pickA);
                seatA.uuid = fakeA.getUUID();
                seatA.faction = 0;
                MultiplayerLobby.Seat seatB = human(900102, nameB, 1, pickB);
                seatB.uuid = fakeB.getUUID();
                seatB.faction = 1;
                MultiplayerMatch match = MultiplayerLobby.startMatch(level, List.of(seatA, seatB), 0, 1, 1, centre, new Random(11));
                RTSPlayer rA = PlayerServerEvents.getRTSPlayer(nameA), rB = PlayerServerEvents.getRTSPlayer(nameB);
                if (rA == null || rB == null) {
                    problems.add("both humans should be RTS players (A " + (rA != null) + ", B " + (rB != null) + ")");
                } else {
                    if (rA.startPosColorId != pickA)
                        problems.add("A picked " + PlayerPalette.byMapColorId(pickA).name() + " but plays as colour id " + rA.startPosColorId);
                    if (rB.startPosColorId != pickB)
                        problems.add("B picked " + PlayerPalette.byMapColorId(pickB).name() + " but plays as colour id " + rB.startPosColorId);
                    for (int pick : List.of(pickA, pickB)) {
                        PlayerColors.PlayerColor pc = PlayerColors.byMapColorId(pick);
                        PlayerPalette.Entry e = PlayerPalette.byMapColorId(pick);
                        if (pc == null || pc.hexCode != e.hex() || !pc.name.equals(e.name()))
                            problems.add("PlayerColors does not draw the picked " + e.name() + " (got "
                                + (pc == null ? "nothing" : pc.name + " #" + Integer.toHexString(pc.hexCode)) + ")");
                    }
                }
                // clean up as SkirmishGameTests' two-human lobby test does: B is beaten, then A leaves through a match
                // of its own so its defeat crowns nobody else
                PlayerServerEvents.defeat(nameB, "gametest");
                new MultiplayerMatch(Set.of(nameA), Set.of(nameA)).register();
                PlayerServerEvents.defeat(nameA, "gametest");
                MultiplayerMatch leftover = MultiplayerMatch.of(nameA);
                if (leftover != null)
                    leftover.end();
                if (match != null && !match.isOver())
                    match.end();
            } finally {
                players.remove(fakeA);
                players.remove(fakeB);
            }
            if (!problems.isEmpty())
                helper.fail(String.join("; ", problems));
            else
                helper.succeed();
        });
    }

    /**
     * Two sides never share a colour: a colour another playing side holds is refused (a spectator's or a Hero-role
     * seat's colour is free - they take no base); automatic (0) and your own colour are always fine; a map colour
     * that is not in the palette is refused; and at the start, sides that somehow hold one colour (or none) still get
     * distinct palette colours, the first holder keeping it. Bots included.
     */
    @GameTest(template = ARENA, timeoutTicks = 20, batch = BATCH)
    public static void lobby_colours_two_sides_cannot_hold_the_same_colour(GameTestHelper helper) {
        int red = PlayerPalette.ENTRIES.get(0).mapColorId(), blue = PlayerPalette.ENTRIES.get(1).mapColorId();
        int cyan = PlayerPalette.ENTRIES.get(2).mapColorId();
        MultiplayerLobby.Seat a = human(1, "col_a", 0, red);
        MultiplayerLobby.Seat b = human(2, "col_b", 1, 0);
        MultiplayerLobby.Seat ai = bot(3, 1, 0);
        MultiplayerLobby.Seat watcher = human(4, "col_watch", MultiplayerLobby.SPECTATE, blue);
        MultiplayerLobby.Seat hero = human(5, "col_hero", 0, cyan);
        hero.heroRole = true;
        List<MultiplayerLobby.Seat> lineup = List.of(a, b, ai, watcher, hero);
        List<String> problems = new ArrayList<>();

        if (MultiplayerLobby.colourChangeAllowed(lineup, b, b.uuid, false, red))
            problems.add("B may take red, which A holds");
        if (MultiplayerLobby.colourChangeAllowed(lineup, ai, a.uuid, true, red))
            problems.add("the host may put a bot on red, which A holds");
        if (!MultiplayerLobby.colourTakenIn(lineup, red, b) || MultiplayerLobby.colourTakenIn(lineup, red, a))
            problems.add("red should read as taken for everyone but its holder A");
        if (!MultiplayerLobby.colourChangeAllowed(lineup, a, a.uuid, true, red))
            problems.add("A may not keep their own red");
        if (!MultiplayerLobby.colourChangeAllowed(lineup, b, b.uuid, false, blue))
            problems.add("B may not take blue, which only a spectator holds");
        if (!MultiplayerLobby.colourChangeAllowed(lineup, b, b.uuid, false, cyan))
            problems.add("B may not take cyan, which only a Hero-role seat (no base) holds");
        if (!MultiplayerLobby.colourChangeAllowed(lineup, b, b.uuid, false, 0))
            problems.add("B may not go back to the automatic colour");
        if (MultiplayerLobby.colourChangeAllowed(lineup, b, b.uuid, false, 9999))
            problems.add("B may take a colour id that is not in the palette");

        // the start: a clash (two seats on red) and automatic seats all end up on distinct palette colours
        MultiplayerLobby.Seat clash = human(6, "col_clash", 1, red);
        List<MultiplayerLobby.Seat> playing = List.of(a, b, ai, clash);
        Map<MultiplayerLobby.Seat, Integer> colours = MultiplayerLobby.assignColours(playing);
        Set<Integer> seen = new HashSet<>();
        for (MultiplayerLobby.Seat s : playing) {
            Integer c = colours.get(s);
            if (c == null || !PlayerPalette.isPaletteColor(c))
                problems.add(s.name + " starts with no palette colour (" + c + ")");
            else if (!seen.add(c))
                problems.add(s.name + " starts on " + PlayerPalette.byMapColorId(c).name() + ", which another side has");
        }
        if (!PlayerServerEvents.takenColorIds().contains(red) && !Integer.valueOf(red).equals(colours.get(a)))
            problems.add("A, the first to hold red, should keep it at the start (got " + colours.get(a) + ")");

        if (!problems.isEmpty())
            helper.fail(String.join("; ", problems));
        else
            helper.succeed();
    }

    /**
     * Only a seat's owner sets its colour; the host also sets the bots' (BAR's lobby). A non-host may not change
     * another player's colour or a bot's; the host may not change another human's; nobody without an identity may
     * change anything.
     */
    @GameTest(template = ARENA, timeoutTicks = 20, batch = BATCH)
    public static void lobby_colours_non_host_cannot_change_another_players_or_a_bots_colour(GameTestHelper helper) {
        int gold = PlayerPalette.byMapColorId(net.minecraft.world.level.material.MapColor.GOLD.id).mapColorId();
        MultiplayerLobby.Seat host = human(1, "col_host", 0, 0);
        MultiplayerLobby.Seat guest = human(2, "col_guest", 1, 0);
        MultiplayerLobby.Seat ai = bot(3, 1, 0);
        List<MultiplayerLobby.Seat> lineup = List.of(host, guest, ai);
        List<String> problems = new ArrayList<>();

        if (MultiplayerLobby.colourChangeAllowed(lineup, host, guest.uuid, false, gold))
            problems.add("a guest may change the host's colour");
        if (MultiplayerLobby.colourChangeAllowed(lineup, ai, guest.uuid, false, gold))
            problems.add("a guest may change a bot's colour");
        if (MultiplayerLobby.colourChangeAllowed(lineup, ai, guest.uuid, false, 0))
            problems.add("a guest may put a bot back on automatic");
        if (MultiplayerLobby.colourChangeAllowed(lineup, guest, host.uuid, true, gold))
            problems.add("the host may change another human's colour");
        if (MultiplayerLobby.colourChangeAllowed(lineup, guest, null, true, gold))
            problems.add("a change with no sender was allowed");
        if (!MultiplayerLobby.colourChangeAllowed(lineup, guest, guest.uuid, false, gold))
            problems.add("a guest may not set their own colour");
        if (!MultiplayerLobby.colourChangeAllowed(lineup, ai, host.uuid, true, gold))
            problems.add("the host may not set a bot's colour");
        if (!MultiplayerLobby.colourChangeAllowed(lineup, host, host.uuid, true, gold))
            problems.add("the host may not set their own colour");

        if (!problems.isEmpty())
            helper.fail(String.join("; ", problems));
        else
            helper.succeed();
    }

    /**
     * Back to lobby (PR #233) keeps the lineup with its colours: a human's pick and a bot's pick come back as they
     * were, an automatic seat stays automatic, the kept picks still block the same colour for anyone else, and the
     * rematch starts every side on its kept pick.
     */
    @GameTest(template = ARENA, timeoutTicks = 20, batch = BATCH)
    public static void lobby_colours_colour_survives_back_to_lobby(GameTestHelper helper) {
        int pickHuman = freeColour(0, Set.of()), pickBot = freeColour(1, Set.of());
        if (pickHuman == 0 || pickBot == 0) {
            helper.fail("no two free palette colours (taken: " + PlayerServerEvents.takenColorIds() + ")");
            return;
        }
        MultiplayerLobby.Seat me = human(1, "col_back", 0, pickHuman);
        MultiplayerLobby.Seat auto = human(2, "col_auto", 1, 0);
        MultiplayerLobby.Seat ai = bot(3, 1, pickBot);
        List<MultiplayerLobby.Seat> kept = MultiplayerLobby.keptSeats(List.of(me, auto, ai), Set.of(me.uuid, auto.uuid), 50);
        List<String> problems = new ArrayList<>();
        if (kept.size() != 3) {
            helper.fail("Back to lobby should keep all three seats, kept " + kept.size());
            return;
        }
        MultiplayerLobby.Seat k0 = kept.get(0), k1 = kept.get(1), k2 = kept.get(2);
        if (k0.colorMapId != pickHuman)
            problems.add("the human's pick " + PlayerPalette.byMapColorId(pickHuman).name() + " came back as " + k0.colorMapId);
        if (k1.colorMapId != 0)
            problems.add("an automatic seat came back on colour " + k1.colorMapId);
        if (k2.colorMapId != pickBot)
            problems.add("the bot's pick " + PlayerPalette.byMapColorId(pickBot).name() + " came back as " + k2.colorMapId);
        if (MultiplayerLobby.colourChangeAllowed(kept, k1, k1.uuid, false, pickHuman))
            problems.add("after Back to lobby another side may take the kept " + PlayerPalette.byMapColorId(pickHuman).name());
        Map<MultiplayerLobby.Seat, Integer> colours = MultiplayerLobby.assignColours(MultiplayerLobby.playingSorted(kept));
        if (!Integer.valueOf(pickHuman).equals(colours.get(k0)) || !Integer.valueOf(pickBot).equals(colours.get(k2)))
            problems.add("the rematch should start on the kept picks (human " + colours.get(k0) + ", bot " + colours.get(k2) + ")");

        if (!problems.isEmpty())
            helper.fail(String.join("; ", problems));
        else
            helper.succeed();
    }
}
