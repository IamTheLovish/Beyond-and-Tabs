package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.perf.DiagRules;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.time.LocalDateTime;

/**
 * The pure rules behind the diagnosis lines and the report bundle (perf/DiagRules): the Xmx-vs-RAM flag, the
 * waiting-text of the [Screen] line and the report file name. In its own class (check_guards.py).
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class DiagnoseGameTests {

    private static final long GB = 1024L * 1024L * 1024L;

    private static void check(boolean ok, String why) {
        if (!ok)
            throw new GameTestAssertException(why);
    }

    @GameTest(template = SkirmishGameTests.ARENA, timeoutTicks = 20)
    public static void diag_xmx_ram_flag(GameTestHelper helper) {
        check(!DiagRules.xmxTooHigh(8 * GB, 16 * GB), "8 of 16 GB is fine");
        check(!DiagRules.xmxTooHigh(12 * GB - 1, 16 * GB), "just under 75% is fine");
        check(DiagRules.xmxTooHigh(12 * GB, 16 * GB), "exactly 75% is flagged");
        check(DiagRules.xmxTooHigh(15 * GB, 16 * GB), "15 of 16 GB is flagged");
        check(!DiagRules.xmxTooHigh(8 * GB, 0), "unknown RAM is never flagged");
        check(!DiagRules.xmxTooHigh(0, 16 * GB), "unknown Xmx is never flagged");
        check(DiagRules.xmxText(12 * GB, 16 * GB).contains("TOO HIGH"), "the text carries the warning");
        check(DiagRules.xmxText(8 * GB, 16 * GB).equals("Xmx 8192 MB of 16384 MB RAM (50%)"), "plain text: " + DiagRules.xmxText(8 * GB, 16 * GB));
        helper.succeed();
    }

    @GameTest(template = SkirmishGameTests.ARENA, timeoutTicks = 20)
    public static void diag_screen_waiting_text(GameTestHelper helper) {
        check(DiagRules.waiting(-1, -1, -1, -1, null).isEmpty(), "nothing known gives an empty text");
        check(DiagRules.waiting(1, 4, -1, -1, null).equals("lobby seats 1/4"), "seats only");
        check(DiagRules.waiting(-1, -1, 62, -1, "").equals("chunks in view 62%"), "chunks only");
        check(DiagRules.waiting(-1, -1, 150, 40, "start").equals("chunks in view 100%, terrain prep 40%, phase start"), "capped and joined: "
            + DiagRules.waiting(-1, -1, 150, 40, "start"));
        check(DiagRules.screenLine("ReceivingLevelScreen", "", 0, "").equals("ReceivingLevelScreen"), "change line without extras");
        check(DiagRules.screenLine("MultiplayerLobbyScreen", "Lobby", 30, "lobby seats 1/4")
            .equals("MultiplayerLobbyScreen \"Lobby\" still open after 30 s; waiting for: lobby seats 1/4"), "repeat line");
        check(DiagRules.screenLine("", null, 0, null).equals("(anonymous screen)"), "anonymous class");
        helper.succeed();
    }

    @GameTest(template = SkirmishGameTests.ARENA, timeoutTicks = 20)
    public static void diag_report_file_name(GameTestHelper helper) {
        String n = DiagRules.reportName(LocalDateTime.of(2026, 10, 22, 2, 5, 9));
        check(n.equals("bat-report-20261022-020509.zip"), "name was " + n);
        helper.succeed();
    }

    @GameTest(template = SkirmishGameTests.ARENA, timeoutTicks = 20)
    public static void diag_shader_error_match(GameTestHelper helper) {
        check(DiagRules.isShaderError("Oculus", true, "Unknown variable 'foo'"), "GLSL unknown variable");
        check(DiagRules.isShaderError("Minecraft", false, "GL_INVALID_OPERATION error generated"), "GL error text");
        check(DiagRules.isShaderError("Iris", true, "anything"), "an ERROR from Iris");
        check(!DiagRules.isShaderError("Iris", false, "Loaded shader pack"), "an INFO from Iris is fine");
        check(!DiagRules.isShaderError("minecraft/Minecraft", true, "Failed to save"), "unrelated error");
        check(!DiagRules.isShaderError(null, true, null), "nulls are safe");
        helper.succeed();
    }
}
