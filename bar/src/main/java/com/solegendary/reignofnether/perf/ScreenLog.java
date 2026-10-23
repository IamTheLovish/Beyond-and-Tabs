package com.solegendary.reignofnether.perf;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.maps.MapClient;
import com.solegendary.reignofnether.matchstart.LobbyClientEvents;
import com.solegendary.reignofnether.matchstart.MatchLoadingScreen;
import com.solegendary.reignofnether.orthoview.OrthoviewClientEvents;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * The {@code [Screen]} line (Oct 22: a tester "stuck in the loading screen" and latest.log never said which screen he
 * was looking at). One line on every screen change - the screen's class and title, or "in-game (RTS view | first
 * person)" - and one every 10 s while a non-game screen stays open, saying what it waits for when known (lobby seats,
 * chunks in view, terrain prep, match phase). Also every 10 s, when the shader pipeline logged errors,
 * {@code [Gfx] shader errors}. The last 20 lines are kept for the report bundle ({@link ReportBundle}).
 * Cost per tick: two reference compares and a clock read; strings only when something changes or 10 s have passed.
 */
public final class ScreenLog {
    private ScreenLog() { }

    private static final long REPEAT_MS = 10_000L;
    private static final int KEEP = 20;

    private static final String[] recent = new String[KEEP];
    private static int recentCount = 0;

    private static Screen lastScreen = null;
    private static int lastGameState = -2;     // -1 no level, 0 first person, 1 RTS view
    private static long shownAtMs = 0;
    private static long lastRepeatMs = 0;
    private static long lastShaderMs = 0;
    private static String current = "(not started)";

    /** The newest {@link #KEEP} [Screen] lines, oldest first. */
    public static synchronized String recentLines() {
        StringBuilder sb = new StringBuilder();
        int n = Math.min(recentCount, KEEP);
        for (int i = recentCount - n; i < recentCount; i++)
            sb.append(recent[i % KEEP]).append('\n');
        return sb.toString();
    }

    public static String currentText() {
        return current;
    }

    private static synchronized void emit(String text) {
        recent[recentCount % KEEP] = text;
        recentCount++;
        ReignOfNether.LOGGER.info("[Screen] {}", text);
    }

    /** What the open non-game screen waits for, from what the client knows. Empty when nothing is known. */
    private static String waitingNow() {
        int[] seats = LobbyClientEvents.seatCounts();
        int prep = MapClient.isPrepRunning() ? MapClient.prepPct() : -1;
        String phase = MapClient.phaseOn() ? "start positions" : MapClient.isPrepRunning() ? "map prep" : null;
        return DiagRules.waiting(seats == null ? -1 : seats[0], seats == null ? -1 : seats[1],
            MatchLoadingScreen.chunkPct(), prep, phase);
    }

    private static String describe(Minecraft mc, Screen s, long openSec) {
        String title = "";
        try {
            title = s.getTitle() == null ? "" : s.getTitle().getString();
        } catch (RuntimeException ignored) {
            // a title that cannot be read is left out
        }
        String name = s.getClass().getSimpleName();
        if (name.isEmpty())
            name = s.getClass().getName();
        String wait = waitingNow();
        if (mc.level == null)
            wait = wait.isEmpty() ? "the server's level (none on the client yet)" : wait + ", the server's level";
        return DiagRules.screenLine(name, title, openSec, wait);
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent evt) {
        if (evt.phase != TickEvent.Phase.END)
            return;
        Minecraft mc = Minecraft.getInstance();
        Screen s = mc.screen;
        long now = Util.getMillis();
        ShaderErrors.install();
        if (s != lastScreen) {
            lastScreen = s;
            shownAtMs = lastRepeatMs = now;
            lastGameState = -2;
            if (s != null) {
                current = describe(mc, s, 0);
                emit(current);
            }
        } else if (s != null && now - lastRepeatMs >= REPEAT_MS) {
            lastRepeatMs = now;
            current = describe(mc, s, (now - shownAtMs) / 1000);
            emit(current);
        }
        if (s == null) {
            int state = mc.level == null ? -1 : OrthoviewClientEvents.isEnabled() ? 1 : 0;
            if (state != lastGameState) {
                lastGameState = state;
                current = state < 0 ? "no screen, no level" : state == 1 ? "in-game (RTS view)" : "in-game (first person)";
                emit(current);
            }
        }
        if (now - lastShaderMs >= REPEAT_MS) {
            lastShaderMs = now;
            int n = ShaderErrors.takeWindow();
            if (n > 0)
                ReignOfNether.LOGGER.warn("[Gfx] shader/GL errors in the last 10 s: {} ({} in total; first: {})", n,
                    ShaderErrors.total(), ShaderErrors.first());
        }
    }
}
