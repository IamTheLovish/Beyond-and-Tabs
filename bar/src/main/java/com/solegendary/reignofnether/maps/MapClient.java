package com.solegendary.reignofnether.maps;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.building.Building;
import com.solegendary.reignofnether.building.BuildingBlock;
import com.solegendary.reignofnether.building.BuildingClientEvents;
import com.solegendary.reignofnether.building.BuildingUtils;
import com.solegendary.reignofnether.cursor.CursorClientEvents;
import com.solegendary.reignofnether.faction.Faction;
import com.solegendary.reignofnether.faction.Factions;
import com.solegendary.reignofnether.guiscreen.TopdownGui;
import com.solegendary.reignofnether.hud.HintBar;
import com.solegendary.reignofnether.hud.HudClientEvents;
import com.solegendary.reignofnether.match.MatchPrep;
import com.solegendary.reignofnether.matchstart.MatchLoadingScreen;
import com.solegendary.reignofnether.matchstart.SkirmishServerboundPacket;
import com.solegendary.reignofnether.minimap.MinimapClientEvents;
import com.solegendary.reignofnether.orthoview.OrthoviewClientEvents;
import com.solegendary.reignofnether.player.PlayerClientEvents;
import com.solegendary.reignofnether.player.PlayerColors;
import com.solegendary.reignofnether.startpos.CapturePointServerEvents;
import com.solegendary.reignofnether.startpos.CapturePointsClient;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.lwjgl.glfw.GLFW;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Clientside half of the maps (design/maps.md):
 * <ul>
 *   <li>the start-position phase: team boxes marked on the ground (StartMarkers: a lit fill, a rim of light, a low
 *       fence, a light pillar and your capitol's ghost where starts are placed) and hatched on the minimap, every
 *       pick's name where they placed (world + minimap), a countdown banner (through the one hint bar, hud/HintBar),
 *       and the click that places your commander (left-click the ground, or right-click the minimap);</li>
 *   <li>the preparation before it (match.MatchPrep), as the packet reports it: the stage and its percentage and the
 *       lineup at its slots, which the loading veil (matchstart.MatchLoadingScreen) shows and uses for the cut;</li>
 *   <li>capture sites' state over each site and on the minimap: "Neutral", "Capturing 45% (name)", "Neutralizing",
 *       "Captured by name";</li>
 *   <li>the Nether / End maps' fog tint.</li>
 * </ul>
 * Everything heavy (outline points, labels, the markers' buffers) is rebuilt only when the server's state or the
 * ground changes, not per frame.
 */
public final class MapClient {
    private MapClient() { }

    private static final Minecraft MC = Minecraft.getInstance();

    static MapPreset preset = MapPreset.RANDOM;
    static int cx, cz, radius;
    static boolean phase = false;
    static int secondsLeft = 0;
    static List<MapStateClientboundPacket.BoxInfo> boxes = List.of();
    static List<MapStateClientboundPacket.PickInfo> picks = List.of();

    // the preparation as the last packet told it: MatchPrep.Stage ordinal (-1 none running) and its 0..100, and
    // whether any stage was ever reported in this world (so "-1 again" reads as "ended", not "never began")
    static int prepStage = -1, prepPct = 0;
    static boolean prepSeen = false;
    // the phase began behind the loading veil: its gong and alert wait for the cut, so they land on the first frame
    // the player sees and not on the loading art
    static boolean gongDeferred = false;
    // the spectator note was shown once by the hint bar after the cut (HudClientEvents keeps its corner line quiet)
    static boolean spectatorTipShown = false;
    // our pick as the last packet of the phase had it: the clock placing an unplaced human is told where (finding 17)
    @Nullable private static MapStateClientboundPacket.PickInfo lastMine = null;

    // per box: outline points {x, z, rgb} and sparse hatch points inside, both for the minimap
    static final List<int[]> outline = new ArrayList<>();
    static final List<int[]> hatch = new ArrayList<>();

    /**
     * The start-position phase is on AND in front of the player - not while the loading veil still covers it. The
     * eyes tour (EyesDirector) waits on this before it photographs the boxes; for everything the player sees, a phase
     * behind the veil has not started yet. The raw server state is {@link #phaseOn}.
     */
    public static boolean isPhaseActive() {
        return phase && !MatchLoadingScreen.isUp();
    }

    /** The server's start-position phase is running (whatever is in front of it). */
    public static boolean phaseOn() {
        return phase;
    }

    /** A preparation stage short of READY is running on the server, by the last packet. */
    public static boolean isPrepRunning() {
        return prepSeen && prepStage >= 0 && prepStage < MatchPrep.Stage.READY.ordinal();
    }

    public static boolean prepSeen() {
        return prepSeen;
    }

    public static int prepStage() {
        return prepStage;
    }

    public static int prepPct() {
        return prepPct;
    }

    /** A map or battlefield is in play (its centre and radius are known). */
    public static boolean hasMap() {
        return radius > 0;
    }

    public static int mapCx() {
        return cx;
    }

    public static int mapCz() {
        return cz;
    }

    public static String mapName() {
        return MapPreview.nameOf(preset);
    }

    public static String mapBlurb() {
        return MapPreview.blurbOf(preset);
    }

    /** Every pick of the phase, or every seat at its slot while the battlefield is prepared (read-only). */
    public static List<MapStateClientboundPacket.PickInfo> lineup() {
        return picks;
    }

    @Nullable
    public static MapStateClientboundPacket.PickInfo myPick() {
        return MC.player == null ? null : pickOf(MC.player.getName().getString());
    }

    public static boolean inLineup(String name) {
        return pickOf(name) != null;
    }

    public static boolean spectatorTipShown() {
        return spectatorTipShown;
    }

    @Nullable
    private static MapStateClientboundPacket.PickInfo pickOf(String name) {
        for (MapStateClientboundPacket.PickInfo p : picks)
            if (p.name().equals(name))
                return p;
        return null;
    }

    /** The humans the phase waits for (not placed, or placed and not ready; not one who left), lineup order. */
    public static List<String> notReadyNames() {
        List<String> names = new ArrayList<>();
        for (MapStateClientboundPacket.PickInfo p : picks)
            if (!p.bot() && !p.left() && !(p.placed() && p.ready()))
                names.add(p.name());
        return names;
    }

    /** True once the local player has pressed Ready (the eyes tour and the panel). */
    public static boolean myReady() {
        MapStateClientboundPacket.PickInfo mine = myPick();
        return mine != null && mine.ready();
    }

    /** The phase's clock as the banner shows it (the server's count, synced each second); 0 outside the phase. */
    public static int secondsLeft() {
        return phase ? secondsLeft : 0;
    }

    /**
     * The local player's start, {x, z}: where they placed, or their slot until they do (the server seats them there
     * when the clock runs out). Null when they are not a pick of this phase.
     */
    public static int[] myStart() {
        String me = MC.player == null ? "" : MC.player.getName().getString();
        for (MapStateClientboundPacket.PickInfo p : picks)
            if (p.name().equals(me))
                return new int[] { p.x(), p.z() };
        return null;
    }

    /** True once the local player has placed their start. */
    public static boolean myStartPlaced() {
        String me = MC.player == null ? "" : MC.player.getName().getString();
        for (MapStateClientboundPacket.PickInfo p : picks)
            if (p.name().equals(me))
                return p.placed();
        return false;
    }

    /** A placed start that is not the local player's (a bot's: they place at once), {x, z}; null when none yet. */
    public static int[] otherPlacedStart() {
        String me = MC.player == null ? "" : MC.player.getName().getString();
        for (MapStateClientboundPacket.PickInfo p : picks)
            if (p.placed() && !p.name().equals(me))
                return new int[] { p.x(), p.z() };
        return null;
    }

    /** One line on the markers' state for a log (the eyes tour prints it beside its stills). */
    public static String markersStatus() {
        return StartMarkers.status();
    }

    static void onState(MapStateClientboundPacket p) {
        preset = MapPreset.byId(p.preset);
        cx = p.cx;
        cz = p.cz;
        radius = p.radius;
        boolean boxesChanged = !p.boxes.equals(boxes);
        boolean wasPhase = phase;
        int lastSeconds = secondsLeft;
        boolean picksChanged = !p.picks.equals(picks);
        boolean wasPrepRunning = isPrepRunning();
        phase = p.phase;
        secondsLeft = p.secondsLeft;
        boxes = List.copyOf(p.boxes);
        picks = List.copyOf(p.picks);
        prepStage = p.prepStage;
        prepPct = p.prepPct;
        if (p.prepStage >= 0)
            prepSeen = true;
        if (wasPrepRunning && !isPrepRunning())
            ReignOfNether.LOGGER.info("[Prep] the server reports the battlefield {}",
                prepStage >= 0 ? "READY" : "preparation ended");
        // the phase's sounds: a gong to begin, a marker knock per placement, the last five seconds ticked, a horn
        // when the starts lock (design/audio.md). A phase that begins behind the loading veil keeps its gong (and the
        // alert it fires) for the cut: the player should hear it on the first frame of the boxes, not of the art.
        if (!wasPhase && phase && MatchLoadingScreen.isUp()) {
            gongDeferred = true;
            com.solegendary.reignofnether.sounds.SfxClient.onStartPhase(true, true, secondsLeft, lastSeconds, false);
        } else {
            com.solegendary.reignofnether.sounds.SfxClient.onStartPhase(wasPhase, phase, secondsLeft, lastSeconds,
                picksChanged && wasPhase);
        }
        // finding 17 (rover run 2): the clock ran out with our commander unplaced - the server put it at our slot
        // and nothing said where. The packet that ends the phase carries no picks, so the last one with ours is kept;
        // the seating has already made us an RTS player by then (a cancel has not), which tells the two apart.
        String me = MC.player == null ? "" : MC.player.getName().getString();
        if (wasPhase && !phase) {
            if (lastMine != null && !lastMine.placed() && PlayerClientEvents.isRTSPlayer(me))
                HintBar.queue("Time was up - your commander took your start position at " + lastMine.x() + ", "
                    + lastMine.z() + " (the camera is on it)", 300, MapMatch.teamColour(lastMine.team()));
            lastMine = null;
            gongDeferred = false;
        } else if (phase) {
            MapStateClientboundPacket.PickInfo mine = pickOf(me);
            if (mine != null)
                lastMine = mine;
        }
        if (boxesChanged)
            rebuildOutline();
        StartMarkers.onState();
    }

    /**
     * The loading veil is gone (MatchLoadingScreen): the gong the phase kept, and the one-time tips that used to be
     * chat lines at login (rover run 1, finding 8) - the "/rts-help" line for everyone, the spectator note for a
     * player with no seat - go through the hint bar, after the camera-controls hint it already holds.
     */
    public static void onVeilCut() {
        if (gongDeferred && phase) {
            com.solegendary.reignofnether.sounds.SfxClient.onStartPhase(false, true, secondsLeft, secondsLeft, false);
            gongDeferred = false;
        }
        if (MC.player == null)
            return;
        String me = MC.player.getName().getString();
        if (!PlayerClientEvents.isRTSPlayer(me) && !inLineup(me)) {
            HintBar.queue(I18n.get("hud.reignofnether.you_are_spectator") + " - "
                + I18n.get("hud.reignofnether.spectator_stays_rts"), 300);
            spectatorTipShown = true;
        }
        HintBar.queue(I18n.get("tutorial.reignofnether.help"), 240);
    }

    /** The countdown banner for the hint bar: yours, placed, or a watcher's. */
    private static String bannerText() {
        MapStateClientboundPacket.PickInfo mine = myPick();
        if (mine != null && mine.ready()) {
            List<String> waiting = notReadyNames();
            return waiting.isEmpty() ? I18n.get("maps.reignofnether.startphase_all_ready")
                : I18n.get("maps.reignofnether.startphase_ready_waiting", String.join(", ", waiting), secondsLeft);
        }
        return mine == null ? I18n.get("maps.reignofnether.startphase_watch", secondsLeft)
            : mine.placed() ? I18n.get("maps.reignofnether.startphase_placed", secondsLeft)
            : I18n.get("maps.reignofnether.startphase", secondsLeft);
    }

    static void rebuildOutline() {
        outline.clear();
        hatch.clear();
        for (MapStateClientboundPacket.BoxInfo b : boxes) {
            int rgb = MapMatch.teamColour(b.team());
            // the two arcs, a block apart along their length, and the two radial edges
            for (float r : new float[] { b.r0(), b.r1() }) {
                float step = 1f / Math.max(10f, r);
                for (float a = b.a0(); a <= b.a1(); a += step)
                    outline.add(new int[] { cx + Math.round((float) Math.cos(a) * r), cz + Math.round((float) Math.sin(a) * r), rgb });
            }
            for (float a : new float[] { b.a0(), b.a1() })
                for (float r = b.r0(); r <= b.r1(); r += 1f)
                    outline.add(new int[] { cx + Math.round((float) Math.cos(a) * r), cz + Math.round((float) Math.sin(a) * r), rgb });
            MapLayout.Box box = new MapLayout.Box(b.team(), b.a0(), b.a1(), b.r0(), b.r1());
            int rr = (int) b.r1() + 1;
            for (int x = -rr; x <= rr; x += 6)
                for (int z = -rr; z <= rr; z += 6)
                    if (box.contains(x, z))
                        hatch.add(new int[] { cx + x, cz + z, rgb });
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent evt) {
        if (evt.phase != TickEvent.Phase.END)
            return;
        if (MC.player == null || MC.level == null) {
            phase = false;
            preset = MapPreset.RANDOM;   // the next world starts without this one's fog tint
            radius = 0;
            prepStage = -1;
            prepPct = 0;
            prepSeen = false;            // the next world's preparation is its own
            gongDeferred = false;
            spectatorTipShown = false;
            lastMine = null;
            picks = List.of();
            StartMarkers.clear();
            return;
        }
        StartMarkers.tick();
        if (forceArmedTicks > 0 && (--forceArmedTicks == 0 || !phase))
            forceArmedTicks = 0;
        // the countdown banner rides on the one hint bar (hud/HintBar), which draws it only on the RTS screen - never
        // over the pause menu (rover run 1, finding 9) and never under the loading veil
        if (phase) {
            MapStateClientboundPacket.PickInfo mine = myPick();
            HintBar.setBanner(bannerText(), mine != null ? MapMatch.teamColour(mine.team()) : 0xD8D8D8);
        } else {
            HintBar.setBanner(null, 0);
        }
    }

    // ------------------------------------------------------------------------------------------ minimap

    /** Called by MinimapClientEvents while it fills its overlay (every client tick). */
    public static void drawOnMinimap() {
        for (CapturePointsClient.Site s : CapturePointsClient.getSites())
            drawSite(s);
        if (!phase)
            return;
        for (int[] h : hatch)
            MinimapClientEvents.plotOverlayPixel(h[0], h[1], h[2]);
        for (int[] o : outline)
            MinimapClientEvents.plotOverlayPixel(o[0], o[1], o[2]);
        for (MapStateClientboundPacket.PickInfo p : picks) {
            if (!p.placed())
                continue;
            for (int x = -3; x <= 3; x++)
                for (int z = -3; z <= 3; z++)
                    MinimapClientEvents.plotOverlayPixel(p.x() + x, p.z() + z,
                        Math.abs(x) == 3 || Math.abs(z) == 3 ? 0x000000 : p.colour());
        }
    }

    /** A capture site on the minimap: a diamond in the owner's colour (grey neutral), a claimant ring while taken. */
    static void drawSite(CapturePointsClient.Site s) {
        int fill = s.owner().isEmpty() ? 0xB4B4B4 : PlayerColors.getPlayerDisplayColorHex(s.owner()) & 0xFFFFFF;
        boolean contested = !s.claimant().isEmpty() && !s.claimant().equals(s.owner()) && s.progress() > 0
            && (s.owner().isEmpty() ? s.progress() < 1 : s.progress() < 1);
        int ring = contested && (MC.level.getGameTime() / 10) % 2 == 0
            ? PlayerColors.getPlayerDisplayColorHex(s.claimant()) & 0xFFFFFF : 0x000000;
        for (int x = -4; x <= 4; x++)
            for (int z = -4; z <= 4; z++) {
                int d = Math.abs(x) + Math.abs(z);
                if (d <= 4)
                    MinimapClientEvents.plotOverlayPixel(s.x() + x, s.z() + z, d == 4 ? ring : d == 0 ? 0xFFFFFF : fill);
            }
    }

    // ------------------------------------------------------------------------------------------ labels

    // labels are rebuilt when the synced site list (replaced wholesale on every sync) or the language changes
    private static List<CapturePointsClient.Site> labelledSites = null;
    private static final List<String> labels = new ArrayList<>();
    private static final List<Integer> labelColours = new ArrayList<>();

    static void refreshLabels() {
        List<CapturePointsClient.Site> sites = CapturePointsClient.getSites();
        if (sites == labelledSites)
            return;
        labelledSites = sites;
        labels.clear();
        labelColours.clear();
        CapturePointServerEvents.Kind[] kinds = CapturePointServerEvents.Kind.values();
        for (CapturePointsClient.Site s : sites) {
            String kind = s.kind() >= 0 && s.kind() < kinds.length ? kinds[s.kind()].name().toLowerCase() : "mine";
            String name = I18n.get("capture.reignofnether.short." + kind);
            String text;
            int colour;
            boolean held = false;
            int pct = Math.round(s.progress() * 100);
            if (s.owner().isEmpty()) {
                if (!s.claimant().isEmpty() && s.progress() > 0) {
                    text = I18n.get("capture.reignofnether.state.capturing", name, pct, s.claimant());
                    colour = PlayerColors.getPlayerDisplayColorHex(s.claimant()) & 0xFFFFFF;
                } else {
                    text = I18n.get("capture.reignofnether.state.neutral", name);
                    colour = 0xD8D8D8;
                }
            } else if (s.progress() < 1 && !s.claimant().isEmpty() && !s.claimant().equals(s.owner())) {
                text = I18n.get("capture.reignofnether.state.neutralizing", name, 100 - pct, s.claimant());
                colour = PlayerColors.getPlayerDisplayColorHex(s.claimant()) & 0xFFFFFF;
            } else {
                text = I18n.get("capture.reignofnether.state.captured", name, s.owner());
                colour = PlayerColors.getPlayerDisplayColorHex(s.owner()) & 0xFFFFFF;
                held = true;
            }
            // a garrison says what it is worth right now: recruits waiting for a taker, or its holder's next recruit
            // (not while it is being neutralized: its muster is held then)
            if (s.kind() == CapturePointServerEvents.Kind.GARRISON.ordinal()) {
                if (s.owner().isEmpty() && s.banked() > 0)
                    text = I18n.get("capture.reignofnether.state.garrison_waiting", text, s.banked());
                else if (held)
                    text = I18n.get("capture.reignofnether.state.garrison_muster", text, s.muster());
            }
            labels.add(text);
            labelColours.add(colour);
        }
    }

    private static boolean renderFailed = false;

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent evt) {
        if (evt.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS)
            return;
        int fp = com.solegendary.reignofnether.perf.FrameParts.begin(com.solegendary.reignofnether.perf.FrameParts.WORLD_MARKERS);
        try {
            if (com.solegendary.reignofnether.postfx.ShaderPacks.skipOverlays()) return;   // shader pack shadow map: overlays cast no shadow
            if (evt.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || MC.level == null)
                return;
            // the start markers show from the ground too (a hero sees the rim, fence and pillars); the rest is RTS-only
            if (phase)
                StartMarkers.render(evt.getPoseStack(), evt.getCamera());
            if (!OrthoviewClientEvents.isEnabled())
                return;
            try {
                renderWorld(evt.getPoseStack(), evt.getCamera());
            } catch (Exception e) {
                // a cosmetic overlay must never break the frame - but say so once, so a CI log shows what went wrong
                if (!renderFailed)
                    ReignOfNether.LOGGER.error("[Maps] the map overlay failed to draw", e);
                renderFailed = true;
            }
        } finally {
            com.solegendary.reignofnether.perf.FrameParts.end(fp);
        }
    }

    private static void renderWorld(PoseStack pose, Camera camera) {
        MultiBufferSource.BufferSource buffers = MC.renderBuffers().bufferSource();
        String me = MC.player == null ? "" : MC.player.getName().getString();
        // your capitol's ghost where you placed (as the classic flow's StartPosClientEvents draws it): the pick knows
        // its seat's faction, unless that is Random, which the seating rolls later
        if (phase)
            for (MapStateClientboundPacket.PickInfo p : picks) {
                if (!p.placed() || !p.name().equals(me))
                    continue;
                Faction f = SkirmishServerboundPacket.factionForCode(p.faction());
                if (!Factions.isLive(f))
                    continue;
                Building capitol = f.getBuilding(f.capitolBuilding);
                if (capitol == null)
                    continue;
                ArrayList<BuildingBlock> blocks = capitol.getRelativeBlockData(MC.level);
                Vec3i dims = BuildingUtils.getBuildingSize(blocks);
                // the start is the ground block (MapMatch.seat), one under the heightmap's first free block
                int y = MC.level.getHeight(Heightmap.Types.MOTION_BLOCKING, p.x(), p.z()) - 1;
                BlockPos origin = BuildingUtils.getBuildingOriginPos(new BlockPos(p.x(), y, p.z()), false, Rotation.NONE, dims);
                BuildingClientEvents.drawBuilding(blocks, pose, origin, 1);
            }
        Font font = MC.font;
        float scale = 0.03f * Math.max(0.8f, Math.min(2.5f, OrthoviewClientEvents.getZoom() / 30f));
        boolean any = false;
        // capture site states
        refreshLabels();
        List<CapturePointsClient.Site> sites = labelledSites;
        for (int i = 0; sites != null && i < sites.size() && i < labels.size(); i++) {
            CapturePointsClient.Site s = sites.get(i);
            label(pose, camera, font, buffers, s.x() + 0.5, s.y() + 5.5, s.z() + 0.5, labels.get(i), labelColours.get(i), scale);
            any = true;
        }
        // start picks: each player's name over where they placed
        if (phase)
            for (MapStateClientboundPacket.PickInfo p : picks) {
                if (!p.placed())
                    continue;
                int y = MC.level.getHeight(Heightmap.Types.MOTION_BLOCKING, p.x(), p.z());
                label(pose, camera, font, buffers, p.x() + 0.5, y + 3, p.z() + 0.5, p.name(), p.colour(), scale * 1.3f);
                any = true;
            }
        if (any) {
            RenderSystem.enableBlend();
            buffers.endBatch();
            RenderSystem.disableBlend();
        }
    }

    private static void label(PoseStack pose, Camera camera, Font font, MultiBufferSource buffers,
                              double x, double y, double z, String text, int rgb, float scale) {
        Vec3 cam = camera.getPosition();
        pose.pushPose();
        pose.translate(x - cam.x, y - cam.y, z - cam.z);
        pose.mulPose(Axis.YP.rotationDegrees(-camera.getYRot()));
        pose.mulPose(Axis.XP.rotationDegrees(camera.getXRot()));
        pose.scale(-scale, -scale, scale);
        float w = font.width(text);
        font.drawInBatch(text, -w / 2f, 0, 0xFF000000 | rgb, false, pose.last().pose(), buffers,
            Font.DisplayMode.SEE_THROUGH, 0x90000000, 0xF000F0);
        pose.popPose();
    }

    // ------------------------------------------------------------------------------------------ HUD

    /**
     * The names beside the picks on the minimap. The countdown banner that was drawn here moved to the hint bar
     * (HintBar, from onClientTick): RenderGuiEvent fires under every screen, so it drew over the pause menu (rover run
     * 1, finding 9) and beside the HUD's own hint (finding 8). The names keep this pass (they sit on the minimap) but
     * only while the RTS screen itself is up and the loading veil is down.
     */
    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post evt) {
        if (!phase || MC.player == null || !OrthoviewClientEvents.isEnabled())
            return;
        if ((MC.screen != null && !(MC.screen instanceof TopdownGui)) || MatchLoadingScreen.isUp())
            return;
        GuiGraphics gg = evt.getGuiGraphics();
        Font font = MC.font;
        gg.pose().pushPose();
        gg.pose().translate(0, 0, 300);
        for (MapStateClientboundPacket.PickInfo p : picks) {
            if (!p.placed())
                continue;
            Vec2 at = MinimapClientEvents.worldPosToMinimapScreen(p.x(), p.z());
            gg.pose().pushPose();
            gg.pose().translate(at.x + 4, at.y - 3, 0);
            gg.pose().scale(0.6f, 0.6f, 1f);
            gg.drawString(font, p.name(), 0, 0, 0xFF000000 | p.colour(), true);
            gg.pose().popPose();
        }
        drawReadyPanel(gg, font);
        gg.pose().popPose();
    }

    // ------------------------------------------------------------------------------------------ ready panel

    // tester Joisco (Oct 26): a Ready button for every human, and everyone sees who the game is waiting for
    private static final int PANEL_W = 150;
    private static final int[] readyRect = new int[4];   // x0, y0, x1, y1 (gui pixels); x1 == 0: not drawn
    private static final int[] forceRect = new int[4];
    /** Ticks left on an armed Force start: the first click warns (naming who is not ready), the second sends. */
    private static int forceArmedTicks = 0;

    /** The host as the server judges it (PacketAuth.isHost): the integrated server's owner, or an op on a dedicated one. */
    private static boolean localIsHost() {
        return MC.player != null && (MC.hasSingleplayerServer() || MC.player.hasPermissions(2));
    }

    private static void drawReadyPanel(GuiGraphics gg, Font font) {
        readyRect[2] = 0;
        forceRect[2] = 0;
        if (picks.isEmpty())
            return;
        MapStateClientboundPacket.PickInfo mine = myPick();
        boolean host = localIsHost();
        List<String> waiting = notReadyNames();
        int humans = 0, ready = 0;
        for (MapStateClientboundPacket.PickInfo p : picks)
            if (!p.bot() && !p.left()) {
                humans++;
                if (p.placed() && p.ready())
                    ready++;
            }
        boolean showForce = host && forceArmedTicks > 0 && !waiting.isEmpty();
        List<FormattedCharSequence> warn = showForce
            ? font.split(Component.literal(I18n.get("maps.reignofnether.startphase_force_warning",
                String.join(", ", waiting))), PANEL_W - 8)
            : List.of();
        int rows = picks.size();
        int h = 14 + rows * 10 + 4 + (mine != null && !mine.bot() ? 18 : 0) + (host ? 18 : 0) + warn.size() * 10 + 2;
        int sw = gg.guiWidth(), sh = gg.guiHeight();
        int x0 = sw - PANEL_W - 6, y0 = Math.max(24, sh / 2 - h / 2);
        gg.fill(x0, y0, x0 + PANEL_W, y0 + h, 0xB0101216);
        gg.fill(x0, y0 + h, x0 + PANEL_W, y0 + h + 1, waiting.isEmpty() ? 0xFF3CDC3C : 0xFFF0D23C);
        gg.drawString(font, I18n.get("maps.reignofnether.startphase_ready_title", ready, humans), x0 + 4, y0 + 3,
            0xFFFFFFFF, true);
        int y = y0 + 15;
        for (MapStateClientboundPacket.PickInfo p : picks) {
            String state;
            int col;
            if (p.left()) {
                state = I18n.get("maps.reignofnether.startphase_state_left");
                col = 0xFFE65A5A;
            } else if (p.bot()) {
                state = I18n.get("maps.reignofnether.startphase_state_bot");
                col = 0xFF7FD87F;
            } else if (p.placed() && p.ready()) {
                state = I18n.get("maps.reignofnether.startphase_state_ready");
                col = 0xFF3CDC3C;
            } else if (p.placed()) {
                state = I18n.get("maps.reignofnether.startphase_state_placed");
                col = 0xFFF0D23C;
            } else {
                state = I18n.get("maps.reignofnether.startphase_state_placing");
                col = 0xFFA0A0A0;
            }
            String name = font.plainSubstrByWidth(p.name(), PANEL_W - 14 - font.width(state));
            gg.drawString(font, name, x0 + 4, y, 0xFF000000 | p.colour(), true);
            gg.drawString(font, state, x0 + PANEL_W - 4 - font.width(state), y, col, true);
            y += 10;
        }
        y += 4;
        if (mine != null && !mine.bot()) {
            boolean can = mine.placed() || mine.ready();
            String label = I18n.get(mine.ready() ? "maps.reignofnether.startphase_unready_button"
                : mine.placed() ? "maps.reignofnether.startphase_ready_button"
                : "maps.reignofnether.startphase_ready_place_first");
            int bg = mine.ready() ? 0xFF6A5A20 : can ? 0xFF2E7D32 : 0xFF3A3A3A;
            button(gg, font, readyRect, x0 + 4, y, PANEL_W - 8, 15, label, bg, can ? 0xFFFFFFFF : 0xFF9A9A9A);
            y += 18;
        }
        if (host) {
            String label = I18n.get(showForce ? "maps.reignofnether.startphase_force_confirm"
                : "maps.reignofnether.startphase_force_button");
            button(gg, font, forceRect, x0 + 4, y, PANEL_W - 8, 15, label, showForce ? 0xFFB02020 : 0xFF6A2020,
                0xFFFFFFFF);
            y += 18;
            for (FormattedCharSequence line : warn) {
                gg.drawString(font, line, x0 + 4, y, 0xFFFF8080, true);
                y += 10;
            }
        }
    }

    private static void button(GuiGraphics gg, Font font, int[] rect, int x, int y, int w, int h, String label,
                               int bg, int fg) {
        gg.fill(x, y, x + w, y + h, bg);
        gg.fill(x, y + h - 1, x + w, y + h, 0x60000000);
        gg.drawString(font, label, x + w / 2 - font.width(label) / 2, y + 4, fg, true);
        rect[0] = x;
        rect[1] = y;
        rect[2] = x + w;
        rect[3] = y + h;
    }

    private static boolean inside(int[] r, double mx, double my) {
        return r[2] > 0 && mx >= r[0] && mx < r[2] && my >= r[1] && my < r[3];
    }

    /** A click on the ready panel's buttons: consumed (true) when it hit one. */
    private static boolean clickReadyPanel(double mx, double my) {
        if (inside(readyRect, mx, my)) {
            MapStateClientboundPacket.PickInfo mine = myPick();
            if (mine != null && !mine.bot()) {
                if (mine.ready())
                    StartPhaseServerboundPacket.sendReady(false);
                else if (mine.placed())
                    StartPhaseServerboundPacket.sendReady(true);
                else
                    HintBar.queue(I18n.get("maps.reignofnether.startphase_ready_needs_place"), 100);
            }
            return true;
        }
        if (inside(forceRect, mx, my)) {
            // nobody to warn about: one click; otherwise the first click shows who is not ready, the second sends
            if (notReadyNames().isEmpty() || forceArmedTicks > 0) {
                forceArmedTicks = 0;
                StartPhaseServerboundPacket.sendForceStart();
            } else {
                forceArmedTicks = 20 * 6;
            }
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------------------------------------ input

    @SubscribeEvent
    public static void onMousePress(ScreenEvent.MouseButtonPressed.Pre evt) {
        if (!phase || MC.player == null || !OrthoviewClientEvents.isEnabled() || !(MC.screen instanceof TopdownGui))
            return;
        if (evt.getButton() == GLFW.GLFW_MOUSE_BUTTON_1 && clickReadyPanel(evt.getMouseX(), evt.getMouseY())) {
            evt.setCanceled(true);
            return;
        }
        boolean onMinimap = MinimapClientEvents.isPointInsideMinimap(evt.getMouseX(), evt.getMouseY());
        BlockPos target = null;
        if (evt.getButton() == GLFW.GLFW_MOUSE_BUTTON_2 && onMinimap) {
            target = MinimapClientEvents.minimapScreenToWorld((float) evt.getMouseX(), (float) evt.getMouseY());
        } else if (evt.getButton() == GLFW.GLFW_MOUSE_BUTTON_1 && !onMinimap && !HudClientEvents.isMouseOverAnyButtonOrHud()) {
            target = CursorClientEvents.getPreselectedBlockPos();
        }
        if (target == null)
            return;
        if (myReady()) {   // the server refuses a ready player's move too: say why instead of nothing happening
            HintBar.queue(I18n.get("maps.reignofnether.startphase_unready_to_move"), 100);
            evt.setCanceled(true);
            return;
        }
        StartPhaseServerboundPacket.send(target.getX(), target.getZ());
        evt.setCanceled(true);
    }

    // ------------------------------------------------------------------------------------------ sky

    @SubscribeEvent
    public static void onFogColour(ViewportEvent.ComputeFogColor evt) {
        int rgb = preset.fogColour();
        if (rgb < 0 || MC.level == null)
            return;
        // only inside the map: the vanilla world past the wall keeps its own sky
        Vec3 c = evt.getCamera().getPosition();
        double dx = c.x - cx, dz = c.z - cz;
        if (dx * dx + dz * dz > (double) (radius + 120) * (radius + 120))
            return;
        evt.setRed(((rgb >> 16) & 0xFF) / 255f);
        evt.setGreen(((rgb >> 8) & 0xFF) / 255f);
        evt.setBlue((rgb & 0xFF) / 255f);
    }
}
