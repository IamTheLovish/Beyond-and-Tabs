package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.gamerules.GameruleServerEvents;
import com.solegendary.reignofnether.match.PlayerListRule;
import com.solegendary.reignofnether.matchstart.MultiplayerLobby;
import com.solegendary.reignofnether.matchstart.SkirmishServerboundPacket;
import com.solegendary.reignofnether.registrars.GameRuleRegistrar;

import io.netty.buffer.Unpooled;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * The player list (stats strip / table, top right) is hidden in a match unless the host switched "Player list" on in
 * the lobby (tester Joisco: "we need to minimize elements that block the screen"; match.PlayerListRule). The option
 * is the saved game rule showPlayerList, which every client mirrors, so a reload keeps it like the other match rules.
 * Own class; no units, so it runs anywhere.
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class PlayerListGameTests {

    static final String ARENA = SkirmishGameTests.ARENA;

    /** Off by default: the rule's default, the option's default and what a player sees before touching anything. */
    @GameTest(template = ARENA, batch = "player_list", timeoutTicks = 20)
    public static void player_list_is_hidden_by_default(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        if (PlayerListRule.DEFAULT)
            problems.add("the option defaults to on");
        if (PlayerListRule.visible(false, false))
            problems.add("a fresh match with the option off draws the list");
        // the key brings it up for one player, and a second press hides it again
        boolean opened = PlayerListRule.afterKey(false, false);
        if (!opened || !PlayerListRule.visible(false, opened))
            problems.add("the list key does not bring the list up with the option off");
        if (PlayerListRule.afterKey(false, opened))
            problems.add("a second press of the list key does not hide it again");
        // with the match option on the list is drawn for everyone and the key never un-draws it
        if (!PlayerListRule.visible(true, false) || PlayerListRule.afterKey(true, false))
            problems.add("with the lobby option on the list is not drawn from the start");
        if (GameRuleRegistrar.SHOW_PLAYER_LIST == null)
            problems.add("the showPlayerList game rule is not registered");
        else if (helper.getLevel().getServer().getGameRules().getRule(GameRuleRegistrar.SHOW_PLAYER_LIST).get())
            problems.add("a world that never set the rule has the list on");
        fail(helper, problems);
    }

    /**
     * The lobby option turns it on and is synced: the host's pick is written to the saved game rule at Start (which the
     * server then sends to every client), and turning it off again writes off. The rule is level data, so it is
     * what a reload reads back like the sun's matchDayCycle. Puts the world's value back afterwards.
     */
    @GameTest(template = ARENA, batch = "player_list", timeoutTicks = 20)
    public static void lobby_option_turns_the_list_on_and_is_saved_as_a_game_rule(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        boolean was = server.getGameRules().getRule(GameRuleRegistrar.SHOW_PLAYER_LIST).get();
        List<String> problems = new ArrayList<>();
        try {
            GameruleServerEvents.applyShowPlayerList(server, true);
            if (!server.getGameRules().getRule(GameRuleRegistrar.SHOW_PLAYER_LIST).get())
                problems.add("the host's lobby choice On did not reach the saved game rule");
            GameruleServerEvents.applyShowPlayerList(server, false);
            if (server.getGameRules().getRule(GameRuleRegistrar.SHOW_PLAYER_LIST).get())
                problems.add("the host's lobby choice Off did not reach the saved game rule");
        } finally {
            GameruleServerEvents.applyShowPlayerList(server, was);
        }
        // the lobby action travels by ordinal: appended after the sun's, never inserted
        if (MultiplayerLobby.Action.SET_PLAYER_LIST.ordinal() != MultiplayerLobby.Action.SET_CYCLE.ordinal() + 1)
            problems.add("SET_PLAYER_LIST is not appended after SET_CYCLE (actions travel by ordinal)");
        fail(helper, problems);
    }

    /**
     * The skirmish lobby's Start carries the option to the server: a packet built with the list on re-encodes with the
     * list on after a round trip, and differs from one built with it off; the old constructors mean off.
     */
    @GameTest(template = ARENA, batch = "player_list", timeoutTicks = 20)
    public static void skirmish_start_packet_carries_the_option(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        byte[] on = roundTrip(new SkirmishServerboundPacket(0, 0, 0, List.of(), 1, 1, 1, 1, 0, true, 5L, 0, 1, true));
        byte[] off = roundTrip(new SkirmishServerboundPacket(0, 0, 0, List.of(), 1, 1, 1, 1, 0, true, 5L, 0, 1, false));
        byte[] old = roundTrip(new SkirmishServerboundPacket(0, 0, 0, List.of(), 1, 1, 1, 1, 0, true, 5L, 0, 1));
        if (java.util.Arrays.equals(on, off))
            problems.add("a packet with the list on encodes the same as one with it off");
        if (!java.util.Arrays.equals(off, old))
            problems.add("the older constructor does not mean list off");
        if (on[on.length - 1] != 1 || off[off.length - 1] != 0)
            problems.add("the option is not the packet's last byte as documented");
        fail(helper, problems);
    }

    private static byte[] roundTrip(SkirmishServerboundPacket p) {
        FriendlyByteBuf first = new FriendlyByteBuf(Unpooled.buffer());
        p.encode(first);
        SkirmishServerboundPacket back = new SkirmishServerboundPacket(first);
        FriendlyByteBuf second = new FriendlyByteBuf(Unpooled.buffer());
        back.encode(second);
        byte[] out = new byte[second.readableBytes()];
        second.readBytes(out);
        return out;
    }

    private static void fail(GameTestHelper helper, List<String> problems) {
        if (problems.isEmpty())
            helper.succeed();
        else
            helper.fail(problems.size() + " problem(s): " + String.join("; ", problems));
    }
}
