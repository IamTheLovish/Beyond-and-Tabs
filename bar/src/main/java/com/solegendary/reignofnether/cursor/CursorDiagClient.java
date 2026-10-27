package com.solegendary.reignofnether.cursor;

import com.mojang.brigadier.Command;
import com.solegendary.reignofnether.building.BuildingClientEvents;
import com.solegendary.reignofnether.guiscreen.TopdownGui;
import com.solegendary.reignofnether.hud.TextInputClientEvents;
import com.solegendary.reignofnether.keybinds.Keybindings;
import com.solegendary.reignofnether.unit.UnitClientEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.lwjgl.glfw.GLFW;

import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;

// feeds CursorDiag from the game (logs/bat-cursor.log) and draws the cursor diagnostics overlay.
// Log lines are events only (mode changes, grabs, focus, screens, jumps); the per-frame poll below allocates nothing
// unless something changed.
public class CursorDiagClient {
    private static final Minecraft MC = Minecraft.getInstance();
    private static final CursorDiag DIAG = new CursorDiag(CursorDiag.RING_SIZE, null);

    private static boolean overlayOn = false;
    private static boolean sinkTried = false;
    private static BufferedWriter writer = null;
    private static long frame = 0;

    // poll state
    private static int lastCursorMode = Integer.MIN_VALUE;
    private static int lastRaw = -1;
    private static boolean lastFocus = true;
    private static boolean focusKnown = false;
    private static Class<?> lastScreen = null;
    private static boolean screenKnown = false;
    private static double lastX = Double.NaN, lastY = Double.NaN;

    // set by CursorClientEvents each RTS frame
    private static boolean systemCursor = false;
    private static boolean rtsView = false;
    private static float drawnGuiX = 0, drawnGuiY = 0;

    /** one event line: ring buffer plus logs/bat-cursor.log */
    public static void event(String kind, String detail) {
        ensureSink();
        DIAG.log(System.currentTimeMillis(), frame, kind, detail);
    }

    /** a grabMouse / releaseMouse call: records who called it (one stack frame) */
    public static void grabCall(String what) {
        event(what, "by " + CursorDiag.callerOf(new Throwable().getStackTrace()));
    }

    /** CursorClientEvents tells us the mode it picked for this frame and where it draws the cursor (GUI px) */
    public static void setRtsState(boolean inRtsView, boolean system, float guiX, float guiY) {
        if (inRtsView != rtsView || system != systemCursor)
            event("mode-switch", inRtsView ? (system ? "rts system-cursor" : "rts virtual-cursor") : "not-rts");
        rtsView = inRtsView;
        systemCursor = system;
        drawnGuiX = guiX;
        drawnGuiY = guiY;
    }

    private static void ensureSink() {
        if (sinkTried) return;
        sinkTried = true;
        try {
            Path dir = MC.gameDirectory.toPath().resolve("logs");
            Files.createDirectories(dir);
            writer = Files.newBufferedWriter(dir.resolve("bat-cursor.log"), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            DIAG.setSink(line -> {
                try {
                    writer.write(line);
                    writer.newLine();
                    writer.flush();
                } catch (Exception e) {
                    DIAG.setSink(null); // the log is a convenience: never let it break the game
                }
            });
        } catch (Exception e) {
            // no log file; the ring buffer and overlay still work
        }
    }

    private static String modeName(int m) {
        return switch (m) {
            case 0x00034001 -> "NORMAL";
            case 0x00034002 -> "HIDDEN";
            case 0x00034003 -> "DISABLED";
            default -> Integer.toString(m);
        };
    }

    @SubscribeEvent
    public static void onRenderTick(TickEvent.RenderTickEvent evt) {
        if (evt.phase != TickEvent.Phase.START || MC.getWindow() == null) return;
        frame++;
        long window = MC.getWindow().getWindow();

        int mode = GLFW.glfwGetInputMode(window, GLFW.GLFW_CURSOR);
        int raw = GLFW.glfwGetInputMode(window, GLFW.GLFW_RAW_MOUSE_MOTION);
        if (mode != lastCursorMode || raw != lastRaw) {
            event("mode-change", "GLFW_CURSOR " + (lastCursorMode == Integer.MIN_VALUE ? "?" : modeName(lastCursorMode))
                    + "->" + modeName(mode) + " raw=" + (raw == GLFW.GLFW_TRUE ? "on" : "off"));
            lastCursorMode = mode;
            lastRaw = raw;
        }

        boolean focus = MC.isWindowActive();
        if (!focusKnown || focus != lastFocus) {
            if (focusKnown) event(focus ? "focus-gained" : "focus-lost", "");
            lastFocus = focus;
            focusKnown = true;
        }

        Class<?> screen = MC.screen == null ? null : MC.screen.getClass();
        if (!screenKnown || screen != lastScreen) {
            if (screenKnown) {
                if (screen == null) event("screen-close", lastScreen.getSimpleName());
                else event("screen-open", screen.getSimpleName());
            }
            lastScreen = screen;
            screenKnown = true;
        }

        double x = MC.mouseHandler.xpos(), y = MC.mouseHandler.ypos();
        if (!Double.isNaN(lastX)
                && CursorDiag.isJump(x - lastX, y - lastY, MC.getWindow().getScreenWidth(), MC.getWindow().getScreenHeight()))
            event("jump", (int) lastX + "," + (int) lastY + "->" + (int) x + "," + (int) y);
        lastX = x;
        lastY = y;
    }

    @SubscribeEvent
    public static void onInput(InputEvent.Key evt) {
        int key = evt.getKey();
        if (evt.getAction() != GLFW.GLFW_PRESS || key == GLFW.GLFW_KEY_UNKNOWN) return;
        if (key != Keybindings.cursorDiag.getKey()) return;
        if (TextInputClientEvents.isAnyInputFocused()) return;
        if (MC.screen != null && !(MC.screen instanceof TopdownGui)) return;
        overlayOn = !overlayOn;
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut evt) {
        if (MC.getWindow() != null)
            SystemCursors.dispose(MC.getWindow().getWindow()); // free the OS cursors with the world
    }

    @SubscribeEvent
    public static void onRegisterCommand(RegisterClientCommandsEvent evt) {
        evt.getDispatcher().register(Commands.literal("rts-cursordiag").executes(ctx -> {
            overlayOn = !overlayOn;
            return Command.SINGLE_SUCCESS;
        }));
    }

    @SubscribeEvent
    public static void onScreenOverlay(ScreenEvent.Render.Post evt) {
        if (overlayOn) draw(evt.getGuiGraphics());
    }

    @SubscribeEvent
    public static void onGuiOverlay(RenderGuiOverlayEvent.Post evt) {
        if (overlayOn && MC.screen == null && evt.getOverlay() == VanillaGuiOverlay.HOTBAR.type())
            draw(evt.getGuiGraphics());
    }

    private static String t(String key, Object... args) {
        return Component.translatable("cursordiag.reignofnether." + key, args).getString();
    }

    private static String hoverTarget() {
        List<LivingEntity> units = UnitClientEvents.getPreselectedUnits();
        if (!units.isEmpty()) return units.get(0).getName().getString();
        if (BuildingClientEvents.getPreselectedBuilding() != null)
            return BuildingClientEvents.getPreselectedBuilding().getClass().getSimpleName();
        if (CursorClientEvents.getPreselectedBlockPos() != null)
            return CursorClientEvents.getPreselectedBlockPos().toShortString();
        return t("none");
    }

    // overlay only: allocation here is fine
    private static void draw(GuiGraphics g) {
        long window = MC.getWindow().getWindow();
        double[] ox = new double[1], oy = new double[1];
        GLFW.glfwGetCursorPos(window, ox, oy);
        float osGuiX = CursorMath.windowToGui(ox[0], MC.getWindow().getScreenWidth(), MC.getWindow().getGuiScaledWidth());
        float osGuiY = CursorMath.windowToGui(oy[0], MC.getWindow().getScreenHeight(), MC.getWindow().getGuiScaledHeight());
        int mode = GLFW.glfwGetInputMode(window, GLFW.GLFW_CURSOR);
        boolean raw = GLFW.glfwGetInputMode(window, GLFW.GLFW_RAW_MOUSE_MOTION) == GLFW.GLFW_TRUE;

        int x = 4, y = 4, line = 10;
        g.fill(x - 2, y - 2, x + 250, y + line * 13 + 2, 0xA0000000);
        String[] rows = {
                t("title"),
                t("cursor_mode", !rtsView ? t("mode_not_rts") : systemCursor ? t("mode_system") : t("mode_virtual")),
                t("glfw_mode", modeName(mode), raw ? t("on") : t("off")),
                t("focus", MC.isWindowActive() ? t("yes") : t("no")),
                t("os_pos", String.format("%.1f, %.1f", osGuiX, osGuiY)),
                t("drawn_pos", rtsView ? String.format("%.1f, %.1f", drawnGuiX, drawnGuiY) : "-"),
                t("hover", hoverTarget()),
        };
        for (int i = 0; i < rows.length; i++)
            g.drawString(MC.font, rows[i], x, y + i * line, i == 0 ? 0xFFFFFF55 : 0xFFFFFFFF, false);
        List<CursorDiag.Entry> last = DIAG.last(5);
        int base = y + rows.length * line;
        g.drawString(MC.font, t("events"), x, base, 0xFFFFFF55, false);
        for (int i = 0; i < last.size(); i++) {
            CursorDiag.Entry e = last.get(i);
            g.drawString(MC.font, "f" + e.frame() + " " + e.kind() + (e.detail().isEmpty() ? "" : " " + e.detail()),
                    x, base + (i + 1) * line, 0xFFCCCCCC, false);
        }
    }
}
