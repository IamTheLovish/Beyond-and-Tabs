package com.solegendary.reignofnether.matchstart;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.faction.Faction;
import com.solegendary.reignofnether.faction.Factions;
import com.solegendary.reignofnether.guiscreen.TopdownGui;
import com.solegendary.reignofnether.hud.SplashArt;
import com.solegendary.reignofnether.keybinds.Keybindings;
import com.solegendary.reignofnether.maps.MapClient;
import com.solegendary.reignofnether.maps.MapMatch;
import com.solegendary.reignofnether.maps.MapStateClientboundPacket;
import com.solegendary.reignofnether.match.MatchPrep;
import com.solegendary.reignofnether.orthoview.OrthoviewClientEvents;
import com.solegendary.reignofnether.player.PlayerClientEvents;
import com.solegendary.reignofnether.util.MiscUtil;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.GenericDirtMessageScreen;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.gui.screens.Overlay;
import net.minecraft.client.gui.screens.ProgressScreen;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.lwjgl.glfw.GLFW;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * One Beyond and Tabs loading screen from Start Battle until the battlefield is on this client, then one cut to the
 * start boxes (inbox/spec-3, section 2.2). Until this, Start Battle showed the vanilla dirt-and-chunk-map world
 * loader, then a first-person flash, then the RTS view over terrain the server was still raising and the client had
 * not yet received - the "dark holes" of the Oct 12 clips, which no server-side work can fix because the holes were
 * chunks the client did not have (rover run 1, finding 6; lovish, Oct 17 06:07: "the map should load before the
 * players join"). BAR's map is on disk, lit and streamed before anyone sees it; so is ours now.
 * <p>
 * The veil has two forms with one picture: SplashArt's rose window and "Beyond and Tabs" (the same layers as the
 * title screen and the resource-reload overlay, so launch -> title -> loading reads as one piece), the map's name and
 * blurb, the lineup, the preparation's stage line and a thin gold bar, and one rotating controls tip.
 * <ul>
 *   <li><b>A Screen</b> while the world is being made: {@link ScreenEvent.Opening} swaps it in for the vanilla
 *       LevelLoadingScreen / ReceivingLevelScreen / ProgressScreen while {@link QuickBattle#loading} is set. It is not
 *       a pause screen, so the integrated server keeps running and chunks stream behind it. (An Overlay cannot be used
 *       here: Minecraft.doWorldLoad spins until the overlay is null.)</li>
 *   <li><b>An Overlay</b> ({@link Veil}) once the RTS screen (TopdownGui) wants the Screen slot: the overlay draws on
 *       top of the HUD and of any screen, swallows the mouse, and the world under it keeps ticking. The same overlay is
 *       raised, without a launch, for a client that only learns of the preparation from the packet - a LAN joiner or
 *       a lobby host already in the world.</li>
 * </ul>
 * As soon as the packet carries the lineup the RTS camera is put on the player's slot (a spectator's on the map
 * centre), so the chunks the server streams are the chunks the cut will show. The cut: the server reports READY (the
 * preparation's stage sequence ended, or the start phase is on, or this client was seated), the chunks in the camera's
 * view at the slot are present on the client ({@link #PRESENT} of them, for {@link #PRESENT_TICKS} ticks running) and
 * the client light engine has no backlog; then a half-second fade. A hard cap of {@link #CAP_MS} shows the world
 * whatever the state, with a line in the log - a loading screen must never trap the player; Esc does the same at once.
 * Everything it decides is logged with a {@code [Prep]} prefix for the eyes clip (spec 5.2): "chunks present N%" and
 * "cut after N s".
 * <p>
 * Single-player correct and multiplayer-safe: nobody waits on another client (the host's readiness handshake is PR 4).
 */
public final class MatchLoadingScreen extends Screen {

    /** The hard cap: the world is shown whatever the state after this long (never trap the player). */
    public static final long CAP_MS = 90_000L;
    /** The cut: from the art to the start boxes in half a second. */
    public static final long FADE_MS = 500L;
    /** The share of the chunks in the camera's view at the slot that must be on the client, and for how long. */
    public static final float PRESENT = 0.98f;
    public static final int PRESENT_TICKS = 20;
    /** One controls tip shows this long before the next. */
    static final long TIP_MS = 6_000L;

    private static final Minecraft MC = Minecraft.getInstance();

    /** The veil that is up right now, in either form; null when none. */
    @Nullable private static MatchLoadingScreen current = null;
    /** The hard cap cut this preparation short: the packet must not raise the veil again for the same one. */
    private static boolean cappedThisPrep = false;

    private final Veil veil = new Veil();
    private final long shownAtMs = Util.getMillis();
    private boolean overlayMode = false;
    private long fadeStartMs = -1;
    private int presentTicks = 0;
    private int lastLoggedPct = -1;
    private float lastPresent = 0;
    private boolean cameraPlaced = false;
    private boolean slotKnown = false;
    private int slotX, slotZ, cameraX, cameraZ;
    private float shownProgress = 0;
    private int tipIndex = -1;
    private long tipChangedMs = 0;
    private String tip = "";

    private MatchLoadingScreen() {
        super(Component.literal("Beyond and Tabs"));
    }

    // ------------------------------------------------------------------------------------------ state for others

    /** The veil is showing (fading out counts: the HUD and the hint bar stay hidden until it is gone). */
    public static boolean isUp() {
        return current != null;
    }

    /** The veil is fully up: input is swallowed, the start phase is not yet "active" for the player. */
    public static boolean isVeiling() {
        return current != null && current.fadeStartMs < 0;
    }

    /**
     * A match is being prepared or played on this client: vanilla recipe / tutorial toasts are dropped meanwhile
     * (ToastComponentMixin) - the "New Recipes Unlocked!" of rover run 1, finding 8.
     */
    public static boolean scrubbing() {
        return isUp() || QuickBattle.loading || MapClient.isPrepRunning() || MapClient.phaseOn()
            || PlayerClientEvents.isRTSPlayer() || PlayerClientEvents.isMatchSpectator();
    }

    /**
     * The HUD's permanent "You are a spectator" corner line stays quiet while a battlefield is being prepared, while
     * the start phase runs (every human is "not yet an RTS player" then) and once the one-time tip said it after the
     * cut (MapClient.onVeilCut) - it sat in the corner of every start in rover run 1, finding 8.
     */
    public static boolean hushesSpectatorLine() {
        return isUp() || MapClient.isPrepRunning() || MapClient.phaseOn() || MapClient.spectatorTipShown();
    }

    // ------------------------------------------------------------------------------------------ raising and dropping

    private static String nameOf(@Nullable Screen s) {
        return s == null ? "none" : s.getClass().getSimpleName();
    }

    private static boolean isVanillaLoading(@Nullable Screen s) {
        return s instanceof LevelLoadingScreen || s instanceof ReceivingLevelScreen || s instanceof ProgressScreen
            || s instanceof GenericDirtMessageScreen;
    }

    /** The overlay form, for a client that is already in the world (a joiner, a lobby host, the Screen slot taken). */
    private static void raiseOverlay() {
        MatchLoadingScreen veil = new MatchLoadingScreen();
        veil.overlayMode = true;
        current = veil;
        MC.setOverlay(veil.veil);
        ReignOfNether.LOGGER.info("[Prep] loading veil up (the battlefield is being prepared)");
    }

    private void toOverlay() {
        if (overlayMode)
            return;
        overlayMode = true;
        MC.setOverlay(veil);
    }

    /** Begins the half-second fade (or ends at once when there is nothing to fade over). */
    private void cut(String why) {
        if (fadeStartMs >= 0)
            return;
        fadeStartMs = Util.getMillis();
        ReignOfNether.LOGGER.info("[Prep] chunks present {}%", Math.round(lastPresent * 100));
        ReignOfNether.LOGGER.info("[Prep] cut after {} s ({})",
            String.format("%.1f", (fadeStartMs - shownAtMs) / 1000.0), why);
        if (MC.level == null)
            finish();
    }

    /** No fade: the player asked for a screen, so the world is theirs at once. */
    private void cutNow(String why) {
        cut(why);
        finish();
    }

    /** The veil is gone: whichever form it had, and everyone who waited for it is told. */
    private void finish() {
        if (current == this)
            current = null;
        if (MC.getOverlay() == veil)
            MC.setOverlay(null);
        if (!overlayMode && MC.screen == this)
            MC.setScreen(null);   // the RTS screen comes back by itself (TopdownGuiClientEvents) when the camera is on
        QuickBattle.loading = false;
        MapClient.onVeilCut();
    }

    /** Dropped without a cut (the launch failed, the lobby opened instead, the player left the world). */
    private void close(String why) {
        ReignOfNether.LOGGER.info("[Prep] loading veil dropped ({})", why);
        if (current == this)
            current = null;
        if (MC.getOverlay() == veil)
            MC.setOverlay(null);
        QuickBattle.loading = false;
    }

    // ------------------------------------------------------------------------------------------ events

    /**
     * HIGH: before TopdownGuiClientEvents reads the screen for its GUI-scale rule. The vanilla loaders are swapped for
     * the veil while a launch loads; the RTS screen is let through and the veil carries on as the overlay; any other
     * screen means the player (Esc) or the game wants it, and a loading screen never hides a screen from the player.
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onScreenOpening(ScreenEvent.Opening evt) {
        Screen next = evt.getNewScreen();
        MatchLoadingScreen veil = current;
        if (veil == null) {
            if (QuickBattle.loading && isVanillaLoading(next)) {
                veil = new MatchLoadingScreen();
                current = veil;
                evt.setNewScreen(veil);
                ReignOfNether.LOGGER.info("[Prep] loading veil up (in place of {})", nameOf(next));
            }
            return;
        }
        if (next == veil)
            return;
        if (!veil.overlayMode) {
            if (isVanillaLoading(next)) {
                evt.setNewScreen(veil);   // ReceivingLevelScreen after LevelLoadingScreen: ours stays
            } else if (next instanceof TopdownGui) {
                veil.toOverlay();
            } else {
                // the multiplayer lobby (Host Online), a disconnect, the title screen: the launch is over
                veil.close("screen " + nameOf(next) + " opened");
            }
            return;
        }
        if (next instanceof TopdownGui || isVanillaLoading(next))
            return;
        veil.cutNow("screen " + nameOf(next) + " opened");
    }

    /** A vanilla loader that slipped past the event (forceSetScreen) is painted over, so no dirt frame ever shows. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onScreenRenderPre(ScreenEvent.Render.Pre evt) {
        if (current == null && QuickBattle.loading && isVanillaLoading(evt.getScreen())) {
            evt.setCanceled(true);
            paint(evt.getGuiGraphics(), 1f, null);
        }
    }

    /** Clicks never reach the RTS screen under the veil (a blind click would place a commander). */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onMousePress(ScreenEvent.MouseButtonPressed.Pre evt) {
        if (isVeiling())
            evt.setCanceled(true);
    }

    /** Keys neither, except Esc: the RTS screen closes on it and the pause menu that follows cuts the veil. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onKeyPress(ScreenEvent.KeyPressed.Pre evt) {
        if (isVeiling() && evt.getKeyCode() != GLFW.GLFW_KEY_ESCAPE)
            evt.setCanceled(true);
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent evt) {
        if (evt.phase != TickEvent.Phase.END)
            return;
        MatchLoadingScreen veil = current;
        if (veil == null) {
            // the packet alone announces the preparation: a LAN joiner, a lobby host already in the world, or a
            // launch whose vanilla loader slipped past the Opening event - once the world and the RTS screen are up
            if (!MapClient.isPrepRunning())
                cappedThisPrep = false;
            else if (!cappedThisPrep && MC.level != null && MC.player != null
                    && (MC.screen == null || MC.screen instanceof TopdownGui))
                raiseOverlay();
            return;
        }
        veil.step();
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut evt) {
        MatchLoadingScreen veil = current;
        if (veil != null)
            veil.close("left the world");
        current = null;
        cappedThisPrep = false;
        QuickBattle.loading = false;
    }

    private void step() {
        long now = Util.getMillis();
        if (fadeStartMs >= 0) {
            if (now - fadeStartMs >= FADE_MS)
                finish();
            return;
        }
        if (MC.level == null || MC.player == null) {
            if (overlayMode)
                close("no level");
            return;   // the world is still being made
        }
        // the Screen slot went to something the event did not see (forceSetScreen): the picture carries on as the
        // overlay
        if (!overlayMode && MC.screen != this)
            toOverlay();
        placeCamera();
        if (now - shownAtMs >= CAP_MS) {
            cappedThisPrep = true;
            cut("hard cap of " + CAP_MS / 1000 + " s, showing the world anyway");
            return;
        }
        lastPresent = chunksPresent();
        int pct = (int) Math.floor(lastPresent * 100);
        // the climb in steps of ten, and the top, for the clip's log
        if (pct / 10 > lastLoggedPct / 10 || (pct == 100 && lastLoggedPct < 100)) {
            lastLoggedPct = pct;
            ReignOfNether.LOGGER.info("[Prep] chunks present {}%", pct);
        }
        presentTicks = lastPresent >= PRESENT ? presentTicks + 1 : 0;
        if (serverReady() && presentTicks >= PRESENT_TICKS && !lightBusy())
            cut("ready");
    }

    /**
     * The server's side of the cut. The packet's prep stage runs FORGE..LIGHT and then READY once, or goes back to -1
     * when the job ends (cancelled, or seated without a start phase) - so "a stage was seen and none runs" is ready.
     * A world with no preparation (a lone side, the classic instant start, a spectator of one) is ready when this
     * client is seated, when a start phase is on, or when the bots it watches are in play.
     */
    private static boolean serverReady() {
        if (MapClient.isPrepRunning())
            return false;
        if (MapClient.prepSeen() || MapClient.phaseOn())
            return true;
        String me = MC.player.getName().getString();
        return PlayerClientEvents.isRTSPlayer(me) || !PlayerClientEvents.rtsPlayers.isEmpty();
    }

    /** The camera goes to our slot (a spectator's to the map centre) as soon as the packet tells it, once. */
    private void placeCamera() {
        String me = MC.player.getName().getString();
        MapStateClientboundPacket.PickInfo mine = MapClient.myPick();
        boolean known;
        int x, z;
        if (mine != null) {
            x = mine.x();
            z = mine.z();
            known = true;
        } else if (MapClient.hasMap()) {
            x = MapClient.mapCx();
            z = MapClient.mapCz();
            known = true;
        } else {
            x = z = 0;
            known = false;
        }
        if (!known)
            return;
        slotKnown = true;
        slotX = x;
        slotZ = z;
        if (!OrthoviewClientEvents.isEnabled())
            return;
        // once, and again only if the slot moved before the start phase (the phase's own teleport takes over then)
        if (cameraPlaced && (MapClient.phaseOn() || (x == cameraX && z == cameraZ)))
            return;
        int y = Math.max(64, MC.level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z));
        OrthoviewClientEvents.centreCameraOnPosForPlayer(me, new BlockPos(x, y, z));
        cameraPlaced = true;
        cameraX = x;
        cameraZ = z;
        ReignOfNether.LOGGER.info("[Prep] camera on {} at {}, {}", mine != null ? "our slot" : "the map centre", x, z);
    }

    /**
     * The share of the chunks the RTS view at the slot covers that the client has (ClientChunkCache). The view is
     * {@code zoom} blocks tall and {@code zoom * aspect} wide; a chunk of margin round it, clamped to what the render
     * distance can ever deliver (a small render distance must not hold the veil until the cap).
     */
    private float chunksPresent() {
        ClientLevel level = MC.level;
        ClientChunkCache cache = level.getChunkSource();
        int bx = slotKnown ? slotX : MC.player.getBlockX();
        int bz = slotKnown ? slotZ : MC.player.getBlockZ();
        double aspect = (double) MC.getWindow().getScreenWidth() / Math.max(1, MC.getWindow().getScreenHeight());
        float zoom = OrthoviewClientEvents.isEnabled() ? OrthoviewClientEvents.getZoom() : 48f;
        double half = Math.max(zoom, zoom * aspect) / 2 + 16;
        int r = (int) Mth.clamp(Math.ceil(half / 16), 2, 8);
        r = Math.min(r, Math.max(2, MC.options.getEffectiveRenderDistance() - 1));
        int cx = bx >> 4, cz = bz >> 4, n = 0, have = 0;
        for (int dx = -r; dx <= r; dx++)
            for (int dz = -r; dz <= r; dz++) {
                n++;
                if (cache.hasChunk(cx + dx, cz + dz))
                    have++;
            }
        return n == 0 ? 1f : have / (float) n;
    }

    private static boolean lightBusy() {
        try {
            return MC.level != null && MC.level.getChunkSource().getLightEngine().hasLightWork();
        } catch (Exception e) {
            return false;   // a light engine that cannot be asked never holds the cut
        }
    }

    // ------------------------------------------------------------------------------------------ the Screen form

    @Override
    public void render(GuiGraphics gg, int mouseX, int mouseY, float partialTick) {
        paint(gg, alphaNow(), this);
    }

    @Override
    public boolean isPauseScreen() {
        return false;   // the world keeps ticking and the chunks keep streaming behind the art
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE)
            cutNow("Esc");   // never trap: Esc shows the world at once
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return true;
    }

    /** The Overlay form: the same picture, on top of the HUD and of any screen, with the mouse swallowed. */
    private final class Veil extends Overlay {
        @Override
        public void render(GuiGraphics gg, int mouseX, int mouseY, float partialTick) {
            paint(gg, alphaNow(), MatchLoadingScreen.this);
        }

        @Override
        public boolean isPauseScreen() {
            return false;
        }
    }

    private float alphaNow() {
        if (fadeStartMs < 0)
            return 1f;
        return 1f - Mth.clamp((Util.getMillis() - fadeStartMs) / (float) FADE_MS, 0f, 1f);
    }

    // ------------------------------------------------------------------------------------------ the picture

    /** The tips are controls lines the game already has (lang: controls.reignofnether.*): nothing new to translate. */
    private static final String[] TIP_KEYS = {
        "controls.reignofnether.command", "controls.reignofnether.zoom", "controls.reignofnether.rotate_cam",
        "controls.reignofnether.create_group", "controls.reignofnether.select_same",
        "controls.reignofnether.select_mods",
        "controls.reignofnether.destroy", "controls.reignofnether.rotate", "hud.reignofnether.patch_hint",
        "controls.reignofnether.toggle_cam",
    };

    private String tipNow(long now) {
        if (tipIndex < 0 || now - tipChangedMs >= TIP_MS) {
            tipIndex = tipIndex < 0 ? (int) ((now / 1000) % TIP_KEYS.length) : (tipIndex + 1) % TIP_KEYS.length;
            tipChangedMs = now;
            String key = TIP_KEYS[tipIndex];
            tip = key.endsWith("toggle_cam") ? I18n.get(key, Keybindings.toggleCamera.getDisplayName()) : I18n.get(key);
        }
        return tip;
    }

    /** The stage line: the preparation's stage from the packet, the client's own chunk wait after READY. */
    private static String stageLine(@Nullable MatchLoadingScreen veil) {
        if (MC.level == null || MC.player == null)
            return "Making the world";
        int stage = MapClient.prepStage();
        if (!MapClient.prepSeen())
            return MapClient.phaseOn() || PlayerClientEvents.isRTSPlayer() ? "Placing the armies" : "Making the world";
        if (stage < 0 || stage >= MatchPrep.Stage.READY.ordinal()) {
            int pct = veil == null ? 0 : (int) Math.floor(veil.lastPresent * 100);
            return "Placing the armies " + pct + "%";
        }
        String what = switch (MatchPrep.Stage.values()[stage]) {
            case FORGE -> "Forging " + MapClient.mapName();
            case CLEAR -> "Clearing the ground";
            case WALL -> "Raising the walls";
            case SCOUT -> "Scouting the ground";
            default -> "Lighting the land";
        };
        return what + " " + MapClient.prepPct() + "%";
    }

    /** 0..1 over the five server stages and the client's chunk wait as a sixth. */
    private static float overall(@Nullable MatchLoadingScreen veil) {
        int ready = MatchPrep.Stage.READY.ordinal();   // 5
        if (!MapClient.prepSeen())
            return 0.02f;
        int stage = MapClient.prepStage();
        if (stage < 0 || stage >= ready)
            return (ready + (veil == null ? 0f : veil.lastPresent)) / (ready + 1f);
        return (stage + MapClient.prepPct() / 100f) / (ready + 1f);
    }

    private static int argb(float alpha, int rgb) {
        return (Mth.clamp(Math.round(alpha * 255f), 0, 255) << 24) | (rgb & 0xFFFFFF);
    }

    private static void centred(GuiGraphics gg, Font font, String text, int cx, int y, int rgb, float alpha) {
        gg.drawString(font, text, cx - font.width(text) / 2, y, argb(Math.max(alpha, 0.04f), rgb), true);
    }

    /**
     * Draws the whole veil into the window at {@code alpha}: the rose window with the title, then a dark stone panel
     * (SplashArt's button look) with the map's name and blurb and the lineup, the stage line, the gold rule and a tip.
     * GUI units through GuiGraphics in both forms. Allocation-light: strings are built per frame but nothing else.
     */
    static void paint(GuiGraphics gg, float alpha, @Nullable MatchLoadingScreen veil) {
        int w = gg.guiWidth(), h = gg.guiHeight();
        if (SplashArt.ensureLoaded()) {
            gg.flush();
            SplashArt.renderScene(gg.pose().last().pose(), w, h, w / 2f, h / 2f, alpha);
        } else {
            gg.fill(0, 0, w, h, argb(alpha, 0x100C0A));
        }
        if (alpha < 0.05f)
            return;
        Font font = MC.font;
        long now = Util.getMillis();
        int cx = w / 2;

        // the panel over the lower part of the window, under the window's glass
        int pw = Math.min(w - 16, 460);
        int px0 = cx - pw / 2, px1 = cx + pw / 2;
        int py0 = Math.round(h * 0.585f), py1 = Math.round(h * 0.845f);
        gg.fill(px0, py0, px1, py1, argb(alpha * 0.86f, 0x1A1512));
        gg.fill(px0 + 1, py0 + 1, px1 - 1, py0 + (py1 - py0) / 4, argb(alpha * 0.25f, 0x4A3D33));
        gg.renderOutline(px0, py0, pw, py1 - py0, argb(alpha, 0xB08A3E));

        int y = py0 + 5;
        centred(gg, font, MapClient.mapName(), cx, y, 0xF0CC72, alpha);
        y += 11;
        for (FormattedCharSequence line : font.split(Component.literal(MapClient.mapBlurb()), pw - 16)) {
            if (y + 10 > py1 - 30)
                break;
            gg.drawString(font, line, cx - font.width(line) / 2, y, argb(alpha, 0xC9B894), false);
            y += 10;
        }
        y += 3;
        y = lineup(gg, font, y, px0 + 8, px1 - 8, py1 - 4, alpha);

        // the stage, the rule and the tip under the panel (the rule where SplashArt.renderLoading draws its own)
        int sy = Math.round(h * 0.86f);
        if (sy < py1 + 2)
            sy = py1 + 2;
        centred(gg, font, stageLine(veil), cx, sy, 0xE6CF9C, alpha);
        float target = overall(veil);
        if (veil != null) {
            veil.shownProgress = Mth.clamp(veil.shownProgress * 0.9f + target * 0.1f, 0f, 1f);
            target = veil.shownProgress;
        }
        int bw = Math.min(Math.round(h * 0.55f), Math.round(w * 0.8f));
        int by = Math.round(h * 0.905f);
        gg.fill(cx - bw / 2 - 1, by - 2, cx + bw / 2 + 1, by + 2, argb(alpha, 0xBF9445));
        gg.fill(cx - bw / 2, by - 1, cx + bw / 2, by + 1, argb(alpha, 0x120D0A));
        gg.fill(cx - bw / 2, by - 1, cx - bw / 2 + Math.round(bw * target), by + 1, argb(alpha, 0xF5CC6B));
        if (veil != null) {
            int ty = Math.round(h * 0.935f);
            List<FormattedCharSequence> lines = font.split(Component.literal(veil.tipNow(now)), Math.round(w * 0.8f));
            for (int i = 0; i < lines.size() && i < 2; i++) {
                FormattedCharSequence line = lines.get(i);
                gg.drawString(font, line, cx - font.width(line) / 2, ty + i * 10, argb(alpha, 0xB8A98A), false);
            }
        }
    }

    /** One line per team: a colour square, the name, the faction in brackets (Random stays Random);
     * returns the next y. */
    private static int lineup(GuiGraphics gg, Font font, int y, int x0, int x1, int yMax, float alpha) {
        List<MapStateClientboundPacket.PickInfo> picks = MapClient.lineup();
        if (picks.isEmpty())
            return y;
        List<Integer> teams = new ArrayList<>();
        for (MapStateClientboundPacket.PickInfo p : picks)
            if (!teams.contains(p.team()))
                teams.add(p.team());
        String me = MC.player == null ? "" : MC.player.getName().getString();
        for (int team : teams) {
            if (y + 10 > yMax)
                break;
            int x = x0;
            String label = "Team " + (team + 1);
            gg.drawString(font, label, x, y, argb(alpha, MapMatch.teamColour(team)), true);
            x += font.width(label) + 8;
            for (MapStateClientboundPacket.PickInfo p : picks) {
                if (p.team() != team)
                    continue;
                Faction f = SkirmishServerboundPacket.factionForCode(p.faction());
                String faction = f == Factions.NONE ? "" : " (" + MiscUtil.getFactionName(f) + ")";
                String entry = p.name() + faction;
                int ew = 9 + font.width(entry);
                if (x + ew > x1) {   // a long lineup wraps to the next line
                    x = x0 + font.width(label) + 8;
                    y += 10;
                    if (y + 10 > yMax)
                        return y;
                }
                gg.fill(x, y + 1, x + 6, y + 7, argb(alpha, p.colour()));
                gg.renderOutline(x, y + 1, 6, 6, argb(alpha * 0.6f, 0x000000));
                gg.drawString(font, entry, x + 9, y, argb(alpha, p.name().equals(me) ? 0xFFF2D6 : 0xE6CF9C), true);
                x += ew + 10;
            }
            y += 10;
        }
        return y;
    }
}
