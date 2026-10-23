package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.BuildId;
import com.solegendary.reignofnether.ReignOfNether;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** The version-mismatch rules: build ids compare exactly, the channel string round-trips, the message names both. */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class BuildIdGameTests {
    static final String ARENA = SkirmishGameTests.ARENA;

    @GameTest(template = ARENA, timeoutTicks = 20)
    public static void buildid_equal_ids_match_and_different_ids_do_not(GameTestHelper helper) {
        if (!BuildId.matches("2026.10.23-0443-911d903", " 2026.10.23-0443-911d903\n"))
            helper.fail("the same id with stray whitespace must match");
        else if (BuildId.matches("2026.10.23-0443-911d903", "2026.10.22-1210-abc1234"))
            helper.fail("two different builds must not match");
        else if (!BuildId.matches(null, "dev") || !BuildId.matches("", "dev"))
            helper.fail("a missing or empty id counts as dev");
        else
            helper.succeed();
    }

    @GameTest(template = ARENA, timeoutTicks = 20)
    public static void buildid_channel_version_round_trips(GameTestHelper helper) {
        String id = "2026.10.23-0443-911d903";
        String ch = BuildId.channelVersion(id);
        if (!ch.equals("Beyond and Tabs " + id))
            helper.fail("channel version should read as words, got " + ch);
        else if (!BuildId.parse(ch).equals(id))
            helper.fail("parse must give the bare id back, got " + BuildId.parse(ch));
        else if (!BuildId.parse("1.5.0-beta-4").equals("1.5.0-beta-4"))
            helper.fail("a string without our prefix comes back as it is");
        else
            helper.succeed();
    }

    @GameTest(template = ARENA, timeoutTicks = 20)
    public static void buildid_mismatch_message_names_both_versions(GameTestHelper helper) {
        String msg = BuildId.mismatchText("2026.10.23-0443-911d903", "2026.10.22-1210-abc1234");
        if (!msg.contains("You have Beyond and Tabs 2026.10.23-0443-911d903"))
            helper.fail("must name what the player has: " + msg);
        else if (!msg.contains("this server needs 2026.10.22-1210-abc1234"))
            helper.fail("must name what the server needs: " + msg);
        else if (!msg.contains("installer"))
            helper.fail("must say how to update: " + msg);
        else
            helper.succeed();
    }

    @GameTest(template = ARENA, timeoutTicks = 20)
    public static void buildid_join_failure_text_uses_server_id_or_falls_back(GameTestHelper helper) {
        String known = BuildId.joinFailureText("A-1", "Beyond and Tabs B-2");
        String unknown = BuildId.joinFailureText("A-1", null);
        String same = BuildId.joinFailureText("A-1", "Beyond and Tabs A-1");
        if (!known.contains("You have Beyond and Tabs A-1") || !known.contains("this server needs B-2"))
            helper.fail("known server id must be named: " + known);
        else if (!unknown.equals("You have Beyond and Tabs A-1; this server runs a different build."))
            helper.fail("unknown server id fallback wrong: " + unknown);
        else if (!same.equals(unknown))
            helper.fail("a server id equal to ours is no explanation: " + same);
        else
            helper.succeed();
    }
}
