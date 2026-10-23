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
    public static void buildid_join_failure_parts_use_server_id_or_fall_back(GameTestHelper helper) {
        BuildId.Parts known = BuildId.joinFailureParts("A-1", "Beyond and Tabs B-2");
        BuildId.Parts unknown = BuildId.joinFailureParts("A-1", null);
        BuildId.Parts same = BuildId.joinFailureParts("A-1", "Beyond and Tabs A-1");
        BuildId.Parts blank = BuildId.joinFailureParts("A-1", "  ");
        if (!known.key().equals(BuildId.KEY_MISMATCH) || known.args().length != 2
                || !known.args()[0].equals("A-1") || !known.args()[1].equals("B-2"))
            helper.fail("known server id must give (have, need) = (A-1, B-2): " + known);
        else if (!unknown.key().equals(BuildId.KEY_MISMATCH_UNKNOWN) || unknown.args().length != 1
                || !unknown.args()[0].equals("A-1"))
            helper.fail("unknown server id fallback wrong: " + unknown);
        else if (!same.key().equals(unknown.key()) || !blank.key().equals(unknown.key()))
            helper.fail("a server id equal to ours or blank is no explanation");
        else
            helper.succeed();
    }

    @GameTest(template = ARENA, timeoutTicks = 20)
    public static void buildid_lang_keys_exist_and_take_the_args(GameTestHelper helper) {
        // read the shipped lang file directly: the dedicated test server does not load mod lang for Component.getString
        try (var in = BuildId.class.getResourceAsStream("/assets/reignofnether/lang/en_us.json")) {
            var json = com.google.gson.JsonParser.parseReader(
                    new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            String a = json.has(BuildId.KEY_MISMATCH) ? json.get(BuildId.KEY_MISMATCH).getAsString() : null;
            String b = json.has(BuildId.KEY_MISMATCH_UNKNOWN) ? json.get(BuildId.KEY_MISMATCH_UNKNOWN).getAsString() : null;
            if (a == null || b == null)
                helper.fail("lang key missing: " + BuildId.KEY_MISMATCH + " / " + BuildId.KEY_MISMATCH_UNKNOWN);
            else if (String.format(a, "A-1", "B-2").contains("%") || !String.format(a, "A-1", "B-2").contains("needs B-2")
                    || !a.contains("installer"))
                helper.fail("mismatch line must take (have, need) and say how to update: " + a);
            else if (!String.format(b, "A-1").contains("Beyond and Tabs A-1") || !b.contains("different build"))
                helper.fail("fallback line must take (have) and say different build: " + b);
            else
                helper.succeed();
        } catch (Exception e) {
            helper.fail("could not read the lang file: " + e);
        }
    }
}
