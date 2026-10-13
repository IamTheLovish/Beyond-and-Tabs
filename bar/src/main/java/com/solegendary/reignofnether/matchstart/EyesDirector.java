package com.solegendary.reignofnether.matchstart;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.orthoview.OrthoviewClientEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * "Eyes" (lovish, Oct 12: "add yourself some eyes"): a scripted tour that only runs when the client is started with
 * -Dbat.eyes=true, as .github/workflows/eyes.yml does on a CI runner (real client, Xvfb + Mesa, nothing on anyone's
 * PC). It photographs the title screen, starts a small Quick Battle against one bot, photographs the RTS view at
 * three zoom levels and a minute into the match, then quits. The workflow publishes the PNGs to the `eyes` branch so
 * the developer AI can look at what a player sees - HUD layout, icons, skins, terrain - instead of guessing.
 *
 * Inert in every normal game: the property is never set by a launcher.
 */
public final class EyesDirector {
    private EyesDirector() { }

    public static final boolean ENABLED = Boolean.getBoolean("bat.eyes");

    private static int step = 0;
    private static int ticks = 0;
    private static int totalTicks = 0;
    private static int titleSince = -1;   // tick (within step 0) at which the title screen first showed
    // a hard stop so a stuck tour never holds a CI runner (20 min at 20 tps)
    private static final int GIVE_UP_TICKS = 20 * 60 * 20;

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent evt) {
        if (!ENABLED || evt.phase != TickEvent.Phase.END)
            return;
        Minecraft mc = Minecraft.getInstance();
        ticks++;
        if (++totalTicks > GIVE_UP_TICKS) {
            ReignOfNether.LOGGER.error("[Eyes] gave up at step {}", step);
            mc.stop();
            return;
        }
        // options.txt overrideWidth was ignored on the runner (shots stayed 854x480): size the window ourselves
        if (totalTicks == 40)
            org.lwjgl.glfw.GLFW.glfwSetWindowSize(mc.getWindow().getWindow(), 1600, 900);
        // a breadcrumb every 30 s, so a stuck tour shows where it is stuck in the CI log
        if (totalTicks % (20 * 30) == 0)
            ReignOfNether.LOGGER.info("[Eyes] step {} screen {} level {}", step,
                    mc.screen == null ? "none" : mc.screen.getClass().getName(), mc.level != null);
        switch (step) {
            case 0 -> {   // title screen settled: photograph it, then start a small battle
                // another mod's first-launch screen (warnings, onboarding) can sit in front of the title screen:
                // photograph it once so we can see what it was, then go to the title screen ourselves
                if (!(mc.screen instanceof TitleScreen) && mc.screen != null && ticks == 20 * 90) {
                    shot(mc, "00_unexpected_screen");
                    mc.setScreen(new TitleScreen());
                    titleSince = ticks;
                }
                if (mc.screen instanceof TitleScreen && titleSince < 0)
                    titleSince = ticks;
                // give the title screen a few seconds to render (and its fade-in to finish) before photographing it
                if (mc.screen instanceof TitleScreen && ticks > 200 && ticks - titleSince > 20 * 4) {
                    shot(mc, "01_title");
                    SkirmishSetupScreen.Settings s = new SkirmishSetupScreen.Settings();
                    s.faction = 0;
                    s.team = 0;
                    s.arena = 0;
                    s.metal = 1;
                    s.map = 0;
                    s.choosePositions = false;
                    // Settings already holds the default 1v1 bot; adding another made it a 1v2 ("Enemies 2")
                    if (s.bots.isEmpty())
                        s.bots.add(new SkirmishSetupScreen.Bot(1));
                    s.bots.get(0).faction = 1;
                    QuickBattle.launch(mc.screen, s);
                    next();
                }
            }
            case 1 -> {   // in the match and in the RTS view, chunks around us loaded
                if (mc.level != null && mc.player != null && OrthoviewClientEvents.isEnabled() && ticks > 20 * 30) {
                    shot(mc, "02_rts_default");
                    groundCensus(mc, "0:30");
                    OrthoviewClientEvents.zoomCam(-1000);   // all the way in
                    next();
                }
            }
            case 2 -> {
                if (ticks > 20 * 4) {
                    shot(mc, "03_rts_close");
                    OrthoviewClientEvents.zoomCam(1000);    // all the way out
                    next();
                }
            }
            case 3 -> {
                if (ticks > 20 * 6) {
                    shot(mc, "04_rts_far");
                    OrthoviewClientEvents.zoomCam(-1000);
                    OrthoviewClientEvents.zoomCam(40);      // back to a middle zoom
                    next();
                }
            }
            case 4 -> {   // a minute of play: the bot builds, the HUD fills
                if (ticks > 20 * 60) {
                    shot(mc, "05_rts_one_minute");
                    groundCensus(mc, "1:00");
                    next();
                }
            }
            case 5 -> {
                if (ticks > 20 * 2) {
                    ReignOfNether.LOGGER.info("[Eyes] tour complete");
                    mc.stop();
                    next();
                }
            }
            default -> { }
        }
    }

    /**
     * Logs which blocks form the visible ground within 24 blocks of the camera's column (the start base), counted by
     * type, so a change on screen (eyes, Oct 13: grey tiles appearing round the capitol in the first minute) can be
     * named from the log instead of guessed. Runs on the integrated server's thread.
     */
    private static void groundCensus(Minecraft mc, String when) {
        var server = mc.getSingleplayerServer();
        if (server == null || mc.player == null)
            return;
        int cx = mc.player.getBlockX(), cz = mc.player.getBlockZ();
        server.execute(() -> {
            var level = server.overworld();
            java.util.Map<String, Integer> counts = new java.util.TreeMap<>();
            for (int x = cx - 24; x <= cx + 24; x++)
                for (int z = cz - 24; z <= cz + 24; z++) {
                    var top = level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                            new net.minecraft.core.BlockPos(x, 0, z)).below();
                    String id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(level.getBlockState(top).getBlock()).toString();
                    counts.merge(id, 1, Integer::sum);
                }
            ReignOfNether.LOGGER.info("[Eyes] ground {} round [{}, {}]: {}", when, cx, cz, counts);
        });
    }

    private static void next() {
        step++;
        ticks = 0;
    }

    private static void shot(Minecraft mc, String name) {
        try {
            Screenshot.grab(mc.gameDirectory, "bat_eyes_" + name + ".png", mc.getMainRenderTarget(),
                    msg -> ReignOfNether.LOGGER.info("[Eyes] {}: {}", name, msg.getString()));
        } catch (Exception e) {
            ReignOfNether.LOGGER.error("[Eyes] screenshot {} failed", name, e);
        }
    }
}
