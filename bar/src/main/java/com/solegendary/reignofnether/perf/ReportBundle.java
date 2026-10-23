package com.solegendary.reignofnether.perf;

import com.mojang.blaze3d.platform.GlUtil;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.brigadier.Command;
import com.solegendary.reignofnether.ReignOfNether;

import net.minecraft.SharedConstants;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.ProgressScreen;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.versions.forge.ForgeVersion;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * The "Report a problem" bundle (Oct 22): {@code logs/bat-report-<yyyyMMdd-HHmmss>.zip} with a copy of latest.log, a
 * screenshot of the current frame and report.txt (versions, GPU, shader pack, memory, window, current screen, the last
 * 20 [Screen] lines). Made by the {@code /rts-report} client command or the "Report a problem" button, which is drawn
 * by this class (ScreenEvent hooks, no change to the screens themselves) on the pause and match menus, our lobby,
 * setup and match-start screens, and the connecting / receiving-level / loading screens. The folder opens afterwards.
 */
public final class ReportBundle {
    private ReportBundle() { }

    private static final int BTN_W = 120, BTN_H = 16;
    private static Button button = null;
    private static Screen buttonScreen = null;

    /** Screens that carry the button: the pause / menu, our lobby screens and every loading screen. */
    private static boolean carriesButton(Screen s) {
        if (s instanceof PauseScreen || s instanceof ReceivingLevelScreen || s instanceof LevelLoadingScreen
                || s instanceof ProgressScreen || s instanceof ConnectScreen)
            return true;
        String n = s.getClass().getName();
        return n.startsWith("com.solegendary.reignofnether.matchstart.")
            && (n.endsWith("MultiplayerLobbyScreen") || n.endsWith("SkirmishSetupScreen") || n.endsWith("MatchLoadingScreen")
                || n.endsWith("MatchStartScreen") || n.endsWith("MatchMenuScreen"));
    }

    // ------------------------------------------------------------------------------------------ the button

    @SubscribeEvent
    public static void onInit(ScreenEvent.Init.Post evt) {
        Screen s = evt.getScreen();
        if (!carriesButton(s)) {
            if (buttonScreen == s) {
                button = null;
                buttonScreen = null;
            }
            return;
        }
        buttonScreen = s;
        button = Button.builder(Component.translatable("hud.reignofnether.report.button"), b -> run(true))
            .bounds(6, s.height - BTN_H - 6, BTN_W, BTN_H).build();
    }

    @SubscribeEvent
    public static void onRender(ScreenEvent.Render.Post evt) {
        if (button != null && evt.getScreen() == buttonScreen)
            button.render(evt.getGuiGraphics(), evt.getMouseX(), evt.getMouseY(), evt.getPartialTick());
    }

    @SubscribeEvent
    public static void onClick(ScreenEvent.MouseButtonPressed.Pre evt) {
        if (button != null && evt.getScreen() == buttonScreen && button.mouseClicked(evt.getMouseX(), evt.getMouseY(), evt.getButton()))
            evt.setCanceled(true);
    }

    // ------------------------------------------------------------------------------------------ the command

    @SubscribeEvent
    public static void onRegisterCommand(RegisterClientCommandsEvent evt) {
        evt.getDispatcher().register(Commands.literal("rts-report").executes(c -> {
            run(true);
            return Command.SINGLE_SUCCESS;
        }));
    }

    // ------------------------------------------------------------------------------------------ the bundle

    /** Writes the bundle, tells the player where it is, opens the folder. Render thread only (it takes a screenshot). */
    public static void run(boolean openFolder) {
        Minecraft mc = Minecraft.getInstance();
        Path dir = FMLPaths.GAMEDIR.get().resolve("logs");
        Path zip = dir.resolve(DiagRules.reportName(LocalDateTime.now()));
        String msg;
        try {
            Files.createDirectories(dir);
            write(mc, dir, zip);
            msg = Component.translatable("hud.reignofnether.report.done", zip.getFileName().toString()).getString();
            ReignOfNether.LOGGER.info("[Screen] report bundle written: {}", zip);
            if (openFolder)
                Util.getPlatform().openFile(dir.toFile());
        } catch (IOException | RuntimeException e) {
            msg = Component.translatable("hud.reignofnether.report.failed", String.valueOf(e)).getString();
            ReignOfNether.LOGGER.warn("[Screen] report bundle failed: {}", e.toString());
        }
        if (mc.player != null)
            mc.player.sendSystemMessage(Component.literal(msg));
    }

    private static void write(Minecraft mc, Path dir, Path zip) throws IOException {
        byte[] shot = null;
        try (NativeImage img = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            shot = img.asByteArray();
        } catch (IOException | RuntimeException e) {
            ReignOfNether.LOGGER.warn("[Screen] report: no screenshot ({})", e.toString());
        }
        byte[] log;
        try {
            log = Files.readAllBytes(dir.resolve("latest.log"));
        } catch (IOException e) {
            log = ("latest.log could not be read: " + e).getBytes(StandardCharsets.UTF_8);
        }
        try (OutputStream os = Files.newOutputStream(zip); ZipOutputStream z = new ZipOutputStream(os)) {
            put(z, "latest.log", log);
            if (shot != null)
                put(z, "screenshot.png", shot);
            put(z, "report.txt", reportText(mc).getBytes(StandardCharsets.UTF_8));
        }
    }

    private static void put(ZipOutputStream z, String name, byte[] data) throws IOException {
        z.putNextEntry(new ZipEntry(name));
        z.write(data);
        z.closeEntry();
    }

    private static String reportText(Minecraft mc) {
        StringBuilder sb = new StringBuilder();
        String modVer = ModList.get().getModContainerById(ReignOfNether.MOD_ID)
            .map(c -> c.getModInfo().getVersion().toString()).orElse("unknown");
        sb.append("Beyond and Tabs ").append(modVer).append('\n');
        sb.append("Minecraft ").append(SharedConstants.getCurrentVersion().getName())
            .append(", Forge ").append(ForgeVersion.getVersion()).append('\n');
        sb.append("Java ").append(System.getProperty("java.version")).append(" (").append(System.getProperty("java.vendor")).append(")\n");
        String renderer = "?", vendor = "?", gl = "?";
        try {
            renderer = GlUtil.getRenderer();
            vendor = GlUtil.getVendor();
            gl = GlUtil.getOpenGLVersion();   // includes the driver version on most GPUs
        } catch (RuntimeException | LinkageError ignored) {
            // report what we have
        }
        sb.append("GPU: ").append(renderer).append(" / ").append(vendor).append(" / OpenGL ").append(gl)
            .append(GfxLog.looksIntegrated(renderer, vendor) ? " (looks like an integrated GPU)" : "").append('\n');
        sb.append("Shader pack: ").append(GfxLog.shaderSummary()).append('\n');
        sb.append("Memory: ").append(GfxLog.memoryText()).append('\n');
        sb.append("Window: ").append(mc.getWindow().getWidth()).append('x').append(mc.getWindow().getHeight())
            .append(", render distance ").append(mc.options.renderDistance().get()).append('\n');
        sb.append("Current screen: ").append(ScreenLog.currentText()).append('\n');
        sb.append("First shader/GL error: ").append(ShaderErrors.first()).append('\n');
        sb.append("\nLast [Screen] lines:\n").append(ScreenLog.recentLines());
        return sb.toString();
    }
}
