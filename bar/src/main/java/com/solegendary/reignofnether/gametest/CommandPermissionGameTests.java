package com.solegendary.reignofnether.gametest;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.tree.CommandNode;
import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.network.PacketAuth;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Who may run which server command (security audit, Oct 26). {@code /rts-fog disable} had no requirement, so any joined
 * player could switch fog of war off for the whole match - a map hack - although the HUD's fog packet needs op level 4;
 * {@code /bot remove} had none either, so a remote player could delete every bot (and so defeat them) or add bots
 * mid-match. These tests only PARSE commands - nothing runs, no match-wide state changes.
 *
 * <p>The regression net: every root command anyone can reach with no requirement must be on {@link #OPEN_COMMANDS}
 * with a reason. A new command without {@code .requires(...)} fails here until someone decides it is meant for
 * everyone.
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class CommandPermissionGameTests {

    static final String ARENA = SkirmishGameTests.ARENA;

    /**
     * Root commands deliberately open to every player, each with why. rts-fog and bot must never be here. Vanilla's
     * own open commands are listed too, since the walk sees the whole server dispatcher.
     */
    static final Map<String, String> OPEN_COMMANDS = new LinkedHashMap<>();
    static {
        // vanilla 1.20.1: chat, help and the scoreboard trigger, open to every player by design
        OPEN_COMMANDS.put("help", "vanilla: lists the commands the caller may use");
        OPEN_COMMANDS.put("list", "vanilla: who is online");
        OPEN_COMMANDS.put("me", "vanilla: chat emote");
        OPEN_COMMANDS.put("msg", "vanilla: private chat");
        OPEN_COMMANDS.put("tell", "vanilla: private chat (redirect to msg)");
        OPEN_COMMANDS.put("w", "vanilla: private chat (redirect to msg)");
        OPEN_COMMANDS.put("teammsg", "vanilla: team chat");
        OPEN_COMMANDS.put("tm", "vanilla: team chat (redirect to teammsg)");
        OPEN_COMMANDS.put("trigger", "vanilla: scoreboard triggers a map maker enabled per player");
        OPEN_COMMANDS.put("test", "vanilla's GameTest harness command: only on development and GameTest servers");
        // Forge 47: its root has subcommands without a requirement (read-only listings); ours never live under it
        OPEN_COMMANDS.put("forge", "Forge's own command; not ours to gate");
        // ours: each acts only on the caller's own state, or checks the caller in its body
        OPEN_COMMANDS.put("ally", "alliance request from the caller to a player");
        OPEN_COMMANDS.put("allyconfirm", "the caller accepts an alliance request made to them");
        OPEN_COMMANDS.put("allycancelrequest", "the caller withdraws their own request");
        OPEN_COMMANDS.put("disband", "the caller leaves their own alliance with a player");
        OPEN_COMMANDS.put("allycontrol", "reads/sets whether the caller's allies may order the caller's units");
        OPEN_COMMANDS.put("rts-scores", "read-only post-game scores");
        OPEN_COMMANDS.put("sendfood", "the caller sends their own resources to a player");
        OPEN_COMMANDS.put("sendwood", "the caller sends their own resources to a player");
        OPEN_COMMANDS.put("sendore", "the caller sends their own resources to a player");
        OPEN_COMMANDS.put("sendemerald", "the caller sends their own resources to a player");
        OPEN_COMMANDS.put("rts-possession", "the caller's own possession lending and release");
        OPEN_COMMANDS.put("rts-lobby", "anyone views the open lobby; close checks isLobbyHost in its body");
        OPEN_COMMANDS.put("rts-lock", "checks op level 4 (or a non-player source) in its body - follow-up: move to requires");
        OPEN_COMMANDS.put("debug-end-wave", "needs the caller's 'thereisnospoon' cheat, checked in its body");
        OPEN_COMMANDS.put("debug-next-night", "needs the caller's 'thereisnospoon' cheat, checked in its body");
    }

    /** Commands that change the whole match: never open, whatever the allow-list says. */
    static final List<String> NEVER_OPEN = List.of("rts-fog", "bot");

    /**
     * Brigadier's "no requirement": ArgumentBuilder starts every node with the same non-capturing {@code s -> true},
     * so a node that never had {@code .requires(...)} holds this very instance.
     */
    static Predicate<CommandSourceStack> defaultRequirement() {
        return Commands.literal("probe").getRequirement();
    }

    /**
     * Can a node with no requirement lead to something runnable? It runs itself, redirects (tell to msg), or one of
     * its children is open. A root whose every runnable child has a requirement (Forge's /forge) is gated.
     */
    static boolean open(CommandNode<CommandSourceStack> node, Predicate<CommandSourceStack> none) {
        if (node.getRequirement() != none)
            return false;
        if (node.getCommand() != null || node.getRedirect() != null)
            return true;
        for (CommandNode<CommandSourceStack> child : node.getChildren())
            if (open(child, none))
                return true;
        return false;
    }

    /** Does this whole line parse, for this source, to something that would run? (Nothing is executed.) */
    static boolean runnable(CommandDispatcher<CommandSourceStack> d, String line, CommandSourceStack src) {
        ParseResults<CommandSourceStack> r = d.parse(line, src);
        return !r.getReader().canRead() && r.getContext().getCommand() != null;
    }

    static CommandDispatcher<CommandSourceStack> dispatcher(GameTestHelper helper) {
        return helper.getLevel().getServer().getCommands().getDispatcher();
    }

    @GameTest(template = ARENA, batch = "command_permissions", timeoutTicks = 20)
    public static void every_open_root_command_is_on_the_allow_list(GameTestHelper helper) {
        Predicate<CommandSourceStack> none = defaultRequirement();
        List<String> problems = new ArrayList<>();
        if (none != Commands.literal("probe2").getRequirement())
            problems.add("test setup: Brigadier's default requirement is not one shared instance - the walk cannot tell 'no requirement' apart");
        for (String bad : NEVER_OPEN)
            if (OPEN_COMMANDS.containsKey(bad))
                problems.add("'" + bad + "' is on the allow-list but changes the whole match");
        List<String> unlisted = new ArrayList<>();
        int roots = 0;
        for (CommandNode<CommandSourceStack> root : dispatcher(helper).getRoot().getChildren()) {
            roots++;
            if (open(root, none) && !OPEN_COMMANDS.containsKey(root.getName()))
                unlisted.add(root.getName());
        }
        if (roots < 20)
            problems.add("test setup: only " + roots + " root commands - is this the server dispatcher?");
        if (!unlisted.isEmpty())
            problems.add("root commands with no requirement and not on the allow-list (add .requires(...) or list them with a reason): "
                + String.join(", ", unlisted));
        TerrainGameTests.finish(helper, problems);
    }

    @GameTest(template = ARENA, batch = "command_permissions", timeoutTicks = 20)
    public static void a_joined_non_op_cannot_turn_fog_off_or_remove_bots(GameTestHelper helper) {
        String name = "gt_cmd_joiner";
        ServerPlayer joiner = FakePlayerFactory.get(helper.getLevel(),
            new GameProfile(UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8)), name));
        List<String> problems = new ArrayList<>();
        if (PacketAuth.isHostOrOp(joiner, 2)) {
            helper.fail("test setup: the fake joiner is a host or an op");
            return;
        }
        CommandDispatcher<CommandSourceStack> d = dispatcher(helper);
        CommandSourceStack joinerSrc = joiner.createCommandSourceStack();
        // the server console (op level 4): the same lines must still parse, or the test proves nothing
        CommandSourceStack consoleSrc = helper.getLevel().getServer().createCommandSourceStack();
        for (String line : List.of("rts-fog disable", "rts-fog enable", "bot remove", "bot add villagers", "bot add villagers hard")) {
            if (runnable(d, line, joinerSrc))
                problems.add("a non-op player may run /" + line);
            if (!runnable(d, line, consoleSrc))
                problems.add("control: the console cannot parse /" + line);
        }
        TerrainGameTests.finish(helper, problems);
    }
}
