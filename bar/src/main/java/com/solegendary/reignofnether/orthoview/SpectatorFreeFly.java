package com.solegendary.reignofnether.orthoview;

import com.solegendary.reignofnether.guiscreen.TopdownGui;
import com.solegendary.reignofnether.hud.TextInputClientEvents;
import com.solegendary.reignofnether.keybinds.Keybindings;
import com.solegendary.reignofnether.matchstart.MatchLoadingScreen;
import com.solegendary.reignofnether.player.PlayerClientEvents;
import com.solegendary.reignofnether.player.PlayerServerboundPacket;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderNameTagEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import org.lwjgl.glfw.GLFW;

/**
 * A watcher's free flight (lovish, Oct 21: "spectators should be able to toggle between rts mode and non interacting
 * creative mode that can fly around and spectate like in creative mode"). One key swaps the RTS view for vanilla
 * spectator flight - noclip, no interaction, the RTS camera and its terrain-following off - and back; the way back
 * puts the RTS camera's focus on the spot the watcher flew to, so the view does not jump. Only for a watcher
 * ({@link PlayerClientEvents#isMatchSpectator}: no side in a running match); the server decides all the same
 * (match.Spectators.setFreeFly), so a modified client cannot give itself free flight past the fog.
 * <p>
 * Also here: the scroll wheel sets the flight speed (a step through 0.5x to 8x vanilla's spectator speed, kept for the
 * session) and watchers are drawn for nobody who plays - see {@link #hidesWatcher}.
 */
public final class SpectatorFreeFly {
    private SpectatorFreeFly() { }

    private static final Minecraft MC = Minecraft.getInstance();

    /** Vanilla's spectator flying speed (PlayerAbilities' default), which the steps below multiply. */
    static final float BASE_FLY_SPEED = 0.05f;
    /** The speed steps, as multiples of vanilla; the scroll wheel walks along them. */
    static final float[] SPEED_STEPS = { 0.5f, 0.75f, 1f, 1.5f, 2f, 3f, 4f, 6f, 8f };
    private static final int DEFAULT_STEP = 2;

    /** Kept for the client session (not saved): a watcher who turned it up stays fast across toggles. */
    private static int speedStep = DEFAULT_STEP;
    /** True while this class has changed the flying speed, so it puts vanilla's back once when flight ends. */
    private static boolean speedApplied = false;

    private static final int GOLD = 0xFFFFD27A, DIM = 0xFFB0B0B0, PANEL = 0xA0101418;

    public static float speedMultiplier() {
        return SPEED_STEPS[speedStep];
    }

    /** One scroll notch: {@code direction} > 0 faster, < 0 slower, clamped to the ends of {@link #SPEED_STEPS}. */
    static void stepSpeed(int direction) {
        speedStep = Math.max(0, Math.min(SPEED_STEPS.length - 1, speedStep + Integer.signum(direction)));
    }

    // ---- the toggle ----

    @SubscribeEvent
    public static void onKey(InputEvent.Key evt) {
        if (evt.getAction() != GLFW.GLFW_PRESS || evt.getKey() != Keybindings.toggleFreeFly.getKey())
            return;
        // not under the loading veil, not while typing in chat or a lobby field, only in the world or on the RTS screen
        if (MatchLoadingScreen.isVeiling() || TextInputClientEvents.isAnyInputFocused())
            return;
        if (MC.screen == null || MC.screen instanceof TopdownGui)
            toggle();
    }

    /** RTS view -> free flight, or back. Does nothing for anyone who is not a watcher of a running match. */
    static void toggle() {
        if (MC.player == null || MC.level == null || !PlayerClientEvents.isMatchSpectator()
                || OrthoviewClientEvents.isCameraLocked())
            return;
        if (OrthoviewClientEvents.enabled) {
            // leaving the RTS view puts back the game mode saved when it opened; the request goes last, so the server
            // sets spectator AFTER that (the channel keeps packets in order)
            OrthoviewClientEvents.setSpectatorLeftOnPurpose(true);
            OrthoviewClientEvents.toggleEnable();
            PlayerServerboundPacket.spectatorFreeFly(true);
        } else {
            Vec3 at = MC.player.position();
            double groundY = MC.level.getHeight(Heightmap.Types.MOTION_BLOCKING, (int) Math.floor(at.x), (int) Math.floor(at.z));
            PlayerServerboundPacket.spectatorFreeFly(false);
            OrthoviewClientEvents.setSpectatorLeftOnPurpose(false);
            OrthoviewClientEvents.toggleEnable();
            // the RTS camera looks at where the watcher is, not at wherever the camera body happens to stand
            OrthoviewClientEvents.centreCameraOnPosInstant(new Vec3(at.x, groundY, at.z));
        }
    }

    // ---- flight speed ----

    @SubscribeEvent
    public static void onScroll(InputEvent.MouseScrollingEvent evt) {
        if (MC.screen != null || !OrthoviewClientEvents.isSpectatorFreeFlying())
            return;
        // vanilla's own scroll speed (0 to 4x, no readout) would fight ours
        evt.setCanceled(true);
        stepSpeed(evt.getScrollDelta() > 0 ? 1 : -1);
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent evt) {
        if (evt.phase != TickEvent.Phase.END || MC.player == null)
            return;
        if (OrthoviewClientEvents.isSpectatorFreeFlying()) {
            // every tick: the server re-sends the abilities (vanilla's speed) on a game mode change
            MC.player.getAbilities().setFlyingSpeed(BASE_FLY_SPEED * speedMultiplier());
            speedApplied = true;
        } else if (speedApplied) {
            MC.player.getAbilities().setFlyingSpeed(BASE_FLY_SPEED);
            speedApplied = false;
        }
    }

    // ---- the hint ----

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post evt) {
        if (MC.player == null || MC.options.hideGui || MC.screen != null || !OrthoviewClientEvents.isSpectatorFreeFlying())
            return;
        GuiGraphics gg = evt.getGuiGraphics();
        Font font = MC.font;
        int w = gg.guiWidth();
        String title = I18n.get("hud.reignofnether.free_fly_title");
        String back = I18n.get("hud.reignofnether.free_fly_back", Keybindings.toggleFreeFly.getDisplayName());
        String speed = I18n.get("hud.reignofnether.free_fly_speed", formatMultiplier(speedMultiplier()));
        int fw = Math.max(font.width(title), Math.max(font.width(back), font.width(speed)));
        gg.fill(w - fw - 14, 4, w - 4, 41, PANEL);
        gg.drawString(font, title, w - font.width(title) - 9, 8, GOLD, false);
        gg.drawString(font, back, w - font.width(back) - 9, 19, DIM, false);
        gg.drawString(font, speed, w - font.width(speed) - 9, 30, DIM, false);
    }

    /** 0.5, 0.75, 1, 1.5 ... without a trailing ".0". */
    static String formatMultiplier(float m) {
        return m == (int) m ? Integer.toString((int) m) : Float.toString(m);
    }

    // ---- watchers are invisible to the players ----

    /**
     * Is {@code e} a watcher's body, seen by someone who plays? A watcher (no side in a running match) flies as a
     * vanilla spectator, and so does every side's RTS camera body, so vanilla alone would draw watchers to the players
     * (translucent, with a nametag, in hero mode). The server never sends them either (match.Spectators.isHiddenFrom);
     * this covers the bodies already on the client. Watchers still see each other.
     */
    static boolean hidesWatcher(Entity e) {
        if (!(e instanceof Player p) || MC.player == null || p == MC.player || !p.isSpectator())
            return false;
        if (PlayerClientEvents.isMatchSpectator() || PlayerClientEvents.rtsPlayers.isEmpty())
            return false;
        return !PlayerClientEvents.isRTSPlayer(p.getName().getString());
    }

    @SubscribeEvent
    public static void onPlayerRender(RenderPlayerEvent.Pre evt) {
        if (hidesWatcher(evt.getEntity()))
            evt.setCanceled(true);   // body, held item and shadow
    }

    @SubscribeEvent
    public static void onNameTag(RenderNameTagEvent evt) {
        if (hidesWatcher(evt.getEntity()))
            evt.setResult(Event.Result.DENY);
    }
}
