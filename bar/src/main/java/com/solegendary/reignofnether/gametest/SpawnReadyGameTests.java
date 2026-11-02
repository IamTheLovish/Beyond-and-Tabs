package com.solegendary.reignofnether.gametest;

import com.mojang.authlib.GameProfile;
import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.maps.MapLayout;
import com.solegendary.reignofnether.maps.MapPreset;
import com.solegendary.reignofnether.maps.MapStateClientboundPacket;
import com.solegendary.reignofnether.maps.StartPhase;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The Ready rule of the start-position phase (tester Joisco, Oct 26: "there should be a ready button that all players
 * should have to press when placing down their spawn location"; design/maps.md section 5). Each test drives its own
 * StartPhase far from every arena (only the box rules are touched, never the world), so the live phase is untouched.
 * Its own batch, spawn_ready.
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class SpawnReadyGameTests {

    static final String ARENA = SkirmishGameTests.ARENA;
    static final String BATCH = "spawn_ready";

    /** Two humans and a bot on HIGHLANDS (teams 0, 1, 1), each pick at its own slot; {@code done[0]} counts starts. */
    private static StartPhase phase(GameTestHelper helper, String a, String b, int[] done) {
        ServerLevel level = helper.getLevel();
        BlockPos base = helper.absolutePos(new BlockPos(8, 1, 8));
        int cx = base.getX() + 40000, cz = base.getZ() + 40000;
        MapLayout layout = MapLayout.build(MapPreset.HIGHLANDS, new int[] { 0, 1, 1 });
        List<StartPhase.Pick> picks = new ArrayList<>(List.of(
            new StartPhase.Pick(a, false, 0, 0, 0xFF0000, null),
            new StartPhase.Pick(b, false, 1, 1, 0x00FF00, null),
            new StartPhase.Pick("AI 1", true, 1, 2, 0x0000FF, null)));
        return new StartPhase(level, cx, cz, layout, picks, StartPhase.SECONDS, ph -> done[0]++);
    }

    /** Places {@code name} on their own slot (inside their box). */
    private static boolean placeAtSlot(StartPhase ph, String name) {
        StartPhase.Pick p = ph.pickOf(name);
        return p != null && ph.place(name, p.x, p.z);
    }

    private static void ticks(StartPhase ph, int n) {
        for (int t = 0; t < n && !ph.isFinished(); t++)
            ph.tick();
    }

    private static void report(GameTestHelper helper, List<String> problems) {
        if (problems.isEmpty())
            helper.succeed();
        else
            helper.fail(String.join("; ", problems));
    }

    @GameTest(template = ARENA, timeoutTicks = 100, batch = BATCH)
    public static void the_match_does_not_start_until_every_human_is_ready(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        int[] done = { 0 };
        StartPhase ph = phase(helper, "gt_rdy_a", "gt_rdy_b", done);
        if (!ph.pickOf("AI 1").ready)
            problems.add("the bot does not count as ready");
        if (ph.setReady("gt_rdy_a", true))
            problems.add("Ready was accepted before placing");
        if (!placeAtSlot(ph, "gt_rdy_a") || !placeAtSlot(ph, "gt_rdy_b"))
            problems.add("placing at the own slot was refused");
        ticks(ph, StartPhase.EARLY_END_TICKS + 5);
        if (ph.isFinished())
            problems.add("everyone placed (nobody ready) started the match");
        if (!ph.setReady("gt_rdy_a", true))
            problems.add("Ready after placing was refused");
        StartPhase.Pick a = ph.pickOf("gt_rdy_a");
        if (ph.place("gt_rdy_a", a.x, a.z))
            problems.add("a ready player moved their start without un-readying");
        ticks(ph, StartPhase.EARLY_END_TICKS + 5);
        if (ph.isFinished())
            problems.add("one of two humans ready started the match");
        if (!ph.notReady().equals(List.of("gt_rdy_b")))
            problems.add("the waiting list is wrong: " + ph.notReady());
        ph.setReady("gt_rdy_b", true);
        ticks(ph, StartPhase.EARLY_END_TICKS + 2);
        if (!ph.isFinished() || done[0] != 1)
            problems.add("every human ready did not start the match (finished " + ph.isFinished() + ", starts " + done[0] + ")");
        report(helper, problems);
    }

    @GameTest(template = ARENA, timeoutTicks = 100, batch = BATCH)
    public static void un_ready_blocks_the_start_again(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        int[] done = { 0 };
        StartPhase ph = phase(helper, "gt_unr_a", "gt_unr_b", done);
        placeAtSlot(ph, "gt_unr_a");
        placeAtSlot(ph, "gt_unr_b");
        ph.setReady("gt_unr_a", true);
        ph.setReady("gt_unr_b", true);
        ticks(ph, StartPhase.EARLY_END_TICKS / 2);   // the start countdown is running
        if (ph.isFinished())
            problems.add("the phase ended before the countdown ran out");
        if (!ph.setReady("gt_unr_b", false))
            problems.add("un-ready was refused");
        ticks(ph, StartPhase.EARLY_END_TICKS + 5);
        if (ph.isFinished())
            problems.add("an un-ready during the countdown did not stop the start");
        StartPhase.Pick b = ph.pickOf("gt_unr_b");
        if (!ph.place("gt_unr_b", b.x, b.z))
            problems.add("an un-readied player could not move their start");
        ph.setReady("gt_unr_b", true);
        ticks(ph, StartPhase.EARLY_END_TICKS + 2);
        if (!ph.isFinished() || done[0] != 1)
            problems.add("ready again did not start the match (starts " + done[0] + ")");
        report(helper, problems);
    }

    @GameTest(template = ARENA, timeoutTicks = 100, batch = BATCH)
    public static void a_leaving_player_does_not_block_the_start(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        int[] done = { 0 };
        StartPhase ph = phase(helper, "gt_lv_a", "gt_lv_b", done);
        placeAtSlot(ph, "gt_lv_a");
        ph.setReady("gt_lv_a", true);
        ticks(ph, StartPhase.EARLY_END_TICKS + 5);
        if (ph.isFinished())
            problems.add("the phase ended with a human not ready");
        if (!ph.leave("gt_lv_b"))
            problems.add("the leaving player was not dropped from the ready count");
        if (!ph.rejoin("gt_lv_b"))
            problems.add("a rejoin did not put the player back in the count");
        ticks(ph, StartPhase.EARLY_END_TICKS + 5);
        if (ph.isFinished())
            problems.add("a rejoined, unready player did not block the start");
        ph.leave("gt_lv_b");
        if (ph.setReady("gt_lv_b", true))
            problems.add("a player who left could press Ready");
        ticks(ph, StartPhase.EARLY_END_TICKS + 2);
        if (!ph.isFinished() || done[0] != 1)
            problems.add("the player who left still blocked the start (starts " + done[0] + ")");
        StartPhase.Pick b = ph.pickOf("gt_lv_b");
        if (!b.placed || !ph.inBox(b.team, b.x, b.z))
            problems.add("the player who left was not seated at their slot");
        report(helper, problems);
    }

    @GameTest(template = ARENA, timeoutTicks = 100, batch = BATCH)
    public static void a_non_host_cannot_force_start(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        int[] done = { 0 };
        StartPhase ph = phase(helper, "gt_fs_a", "gt_fs_b", done);
        // a fake player is no singleplayer owner and no op: not the host (network.PacketAuth.isHost)
        ServerPlayer guest = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "gt_fs_guest"));
        if (StartPhase.handleForceStart(ph, guest) || ph.isFinished() || done[0] != 0)
            problems.add("a non-host forced the start");
        if (ph.forceStart(false))
            problems.add("forceStart accepted a non-host");
        if (!ph.notReady().equals(List.of("gt_fs_a", "gt_fs_b")))
            problems.add("the force-start warning would name the wrong players: " + ph.notReady());
        if (!ph.forceStart(true) || !ph.isFinished() || done[0] != 1)
            problems.add("the host's force start did not start the match");
        for (StartPhase.Pick p : ph.picks)
            if (!p.placed)
                problems.add(p.name + " was not seated by the force start");
        report(helper, problems);
    }

    /**
     * The live path, ticked by the server itself: one human and a bot, the human places and presses Ready; the phase
     * must end through onServerTick, start the match (onDone, which is MatchState.startsPlaced in play), drop the live
     * phase and broadcast the phase-off map state (the client's Ready panel and start ghost go on that packet).
     */
    @GameTest(template = ARENA, timeoutTicks = 200, batch = BATCH)
    public static void all_ready_on_the_live_phase_sends_phase_off_and_starts(GameTestHelper helper) {
        if (StartPhase.live() != null) {
            helper.fail("a live start phase is already running");
            return;
        }
        ServerLevel level = helper.getLevel();
        BlockPos base = helper.absolutePos(new BlockPos(8, 1, 8));
        int cx = base.getX() + 40000, cz = base.getZ() + 40000;
        MapLayout layout = MapLayout.build(MapPreset.HIGHLANDS, new int[] { 0, 1 });
        int[] done = { 0 };
        StartPhase ph = new StartPhase(level, cx, cz, layout, new ArrayList<>(List.of(
            new StartPhase.Pick("gt_live_solo", false, 0, 0, 0xFF0000, null),
            new StartPhase.Pick("AI 1", true, 1, 1, 0x00FF00, null))), StartPhase.SECONDS, p -> done[0]++);
        StartPhase.resume(ph);   // live without the camera teleport or the bot re-pick (nothing outside the phase)
        // a phase that never finished stayed the server's live one (broadcast, read by later tests) until its own
        // 120 s ran out: ended with the test, however it ends
        TestCleanup.atEnd(helper, () -> {
            if (StartPhase.live() == ph)
                StartPhase.cancel();
        });
        if (StartPhase.live() != ph || !MapStateClientboundPacket.lastBroadcastPhase()) {
            StartPhase.cancel();
            helper.fail("the live phase did not start or its phase-on state was not sent");
            return;
        }
        if (!placeAtSlot(ph, "gt_live_solo") || !ph.setReady("gt_live_solo", true)) {
            StartPhase.cancel();
            helper.fail("place or Ready was refused");
            return;
        }
        helper.succeedWhen(() -> {
            helper.assertTrue(ph.isFinished(), "the live phase did not end after everyone was ready");
            helper.assertTrue(done[0] == 1, "the match was not started (starts " + done[0] + ")");
            helper.assertTrue(StartPhase.live() == null, "the finished phase is still the live one");
            helper.assertFalse(MapStateClientboundPacket.lastBroadcastPhase(),
                "the phase-off map state was not sent to the clients");
        });
    }

    @GameTest(template = ARENA, timeoutTicks = 100, batch = BATCH)
    public static void one_human_against_bots_starts_on_one_ready_press(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        ServerLevel level = helper.getLevel();
        BlockPos base = helper.absolutePos(new BlockPos(8, 1, 8));
        int cx = base.getX() + 40000, cz = base.getZ() + 40000;
        MapLayout layout = MapLayout.build(MapPreset.HIGHLANDS, new int[] { 0, 1, 1 });
        int[] done = { 0 };
        StartPhase ph = new StartPhase(level, cx, cz, layout, new ArrayList<>(List.of(
            new StartPhase.Pick("gt_solo", false, 0, 0, 0xFF0000, null),
            new StartPhase.Pick("AI 1", true, 1, 1, 0x00FF00, null),
            new StartPhase.Pick("AI 2", true, 1, 2, 0x0000FF, null))), StartPhase.SECONDS, p -> done[0]++);
        if (!placeAtSlot(ph, "gt_solo"))
            problems.add("placing was refused");
        ticks(ph, StartPhase.EARLY_END_TICKS + 5);
        if (ph.isFinished())
            problems.add("placing alone started the match");
        if (!ph.setReady("gt_solo", true))
            problems.add("Ready was refused");
        ticks(ph, StartPhase.EARLY_END_TICKS + 2);
        if (!ph.isFinished() || done[0] != 1)
            problems.add("one Ready press did not start a match against bots");
        report(helper, problems);
    }
}
