package com.solegendary.reignofnether.maps;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.solegendary.reignofnether.cursor.CursorClientEvents;
import com.solegendary.reignofnether.guiscreen.TopdownGui;
import com.solegendary.reignofnether.hud.HudClientEvents;
import com.solegendary.reignofnether.minimap.MinimapClientEvents;
import com.solegendary.reignofnether.orthoview.OrthoviewClientEvents;
import com.solegendary.reignofnether.player.PlayerColors;
import com.solegendary.reignofnether.startpos.CapturePointServerEvents;
import com.solegendary.reignofnether.startpos.CapturePointsClient;
import com.solegendary.reignofnether.util.MyRenderer;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * Clientside half of the maps (design/maps.md):
 * <ul>
 *   <li>the start-position phase: team boxes outlined on the ground and hatched on the minimap, every pick's name
 *       where they placed (world + minimap), a countdown banner, and the click that places your commander
 *       (left-click the ground, or right-click the minimap);</li>
 *   <li>capture sites' state over each site and on the minimap: "Neutral", "Capturing 45% (name)", "Neutralizing",
 *       "Captured by name";</li>
 *   <li>the Nether / End maps' fog tint.</li>
 * </ul>
 * Everything heavy (outline points, labels) is rebuilt only when the server's state changes, not per frame.
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

    // per box: world outline points {x, z} (minimap + world lines) and sparse hatch points inside (minimap)
    static final List<int[]> outline = new ArrayList<>();   // {x, z, rgb}
    static final List<int[]> hatch = new ArrayList<>();
    static int[] outlineY = new int[0];
    static int heightRefreshTicks = 0;

    public static boolean isPhaseActive() {
        return phase;
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
        phase = p.phase;
        secondsLeft = p.secondsLeft;
        boxes = List.copyOf(p.boxes);
        picks = List.copyOf(p.picks);
        // the phase's sounds: a gong to begin, a marker knock per placement, the last five seconds ticked, a horn
        // when the starts lock (design/audio.md)
        com.solegendary.reignofnether.sounds.SfxClient.onStartPhase(wasPhase, phase, secondsLeft, lastSeconds,
            picksChanged && wasPhase);
        if (boxesChanged)
            rebuildOutline();
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
        outlineY = new int[outline.size()];
        heightRefreshTicks = 0;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent evt) {
        if (evt.phase != TickEvent.Phase.END)
            return;
        if (MC.player == null || MC.level == null) {
            phase = false;
            preset = MapPreset.RANDOM;   // the next world starts without this one's fog tint
            return;
        }
        // the terrain under the outline can still be rising (or loading): re-read its height every two seconds
        if (phase && --heightRefreshTicks <= 0) {
            heightRefreshTicks = 40;
            for (int i = 0; i < outline.size(); i++)
                outlineY[i] = MC.level.getHeight(Heightmap.Types.MOTION_BLOCKING, outline.get(i)[0], outline.get(i)[1]);
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

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent evt) {
        if (evt.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || MC.level == null
                || !OrthoviewClientEvents.isEnabled())
            return;
        try {
            renderWorld(evt.getPoseStack(), evt.getCamera());
        } catch (Exception ignored) {
            // a cosmetic overlay must never break the frame
        }
    }

    private static void renderWorld(PoseStack pose, Camera camera) {
        Vec3 cam = camera.getPosition();
        MultiBufferSource.BufferSource buffers = MC.renderBuffers().bufferSource();
        if (phase && !outline.isEmpty()) {
            VertexConsumer vc = buffers.getBuffer(MyRenderer.LINES_NO_DEPTH_TEST);
            pose.pushPose();
            pose.translate(-cam.x, -cam.y, -cam.z);
            Matrix4f m = pose.last().pose();
            Matrix3f n = pose.last().normal();
            for (int i = 1; i < outline.size(); i++) {
                int[] a = outline.get(i - 1), b = outline.get(i);
                if (Math.abs(a[0] - b[0]) > 3 || Math.abs(a[1] - b[1]) > 3)
                    continue;   // the jump between one edge and the next
                float r = ((b[2] >> 16) & 0xFF) / 255f, g = ((b[2] >> 8) & 0xFF) / 255f, bl = (b[2] & 0xFF) / 255f;
                line(vc, m, n, a[0] + 0.5f, outlineY[i - 1] + 0.3f, a[1] + 0.5f, b[0] + 0.5f, outlineY[i] + 0.3f, b[1] + 0.5f, r, g, bl, 0.9f);
            }
            pose.popPose();
            buffers.endBatch(MyRenderer.LINES_NO_DEPTH_TEST);
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

    private static void line(VertexConsumer vc, Matrix4f m, Matrix3f n, float x1, float y1, float z1,
                             float x2, float y2, float z2, float r, float g, float b, float a) {
        float dx = x2 - x1, dy = y2 - y1, dz = z2 - z1;
        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1.0e-4f)
            return;
        dx /= len;
        dy /= len;
        dz /= len;
        vc.vertex(m, x1, y1, z1).color(r, g, b, a).normal(n, dx, dy, dz).endVertex();
        vc.vertex(m, x2, y2, z2).color(r, g, b, a).normal(n, dx, dy, dz).endVertex();
    }

    // ------------------------------------------------------------------------------------------ HUD

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post evt) {
        if (!phase || MC.player == null || !OrthoviewClientEvents.isEnabled())
            return;
        GuiGraphics gg = evt.getGuiGraphics();
        Font font = MC.font;
        String me = MC.player.getName().getString();
        MapStateClientboundPacket.PickInfo mine = null;
        for (MapStateClientboundPacket.PickInfo p : picks)
            if (p.name().equals(me))
                mine = p;
        String banner = mine == null ? I18n.get("maps.reignofnether.startphase_watch", secondsLeft)
            : mine.placed() ? I18n.get("maps.reignofnether.startphase_placed", secondsLeft)
            : I18n.get("maps.reignofnether.startphase", secondsLeft);
        int w = font.width(banner);
        int x = gg.guiWidth() / 2 - w / 2, y = 34;
        int edge = mine != null ? MapMatch.teamColour(mine.team()) : 0xD8D8D8;
        gg.fill(x - 6, y - 4, x + w + 6, y + 12, 0xB0101216);
        gg.fill(x - 6, y + 12, x + w + 6, y + 13, 0xFF000000 | edge);
        gg.drawString(font, banner, x, y, 0xFFFFFF, true);
        // names beside the picks on the minimap
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
        gg.pose().popPose();
    }

    // ------------------------------------------------------------------------------------------ input

    @SubscribeEvent
    public static void onMousePress(ScreenEvent.MouseButtonPressed.Pre evt) {
        if (!phase || MC.player == null || !OrthoviewClientEvents.isEnabled() || !(MC.screen instanceof TopdownGui))
            return;
        boolean onMinimap = MinimapClientEvents.isPointInsideMinimap(evt.getMouseX(), evt.getMouseY());
        BlockPos target = null;
        if (evt.getButton() == GLFW.GLFW_MOUSE_BUTTON_2 && onMinimap) {
            target = MinimapClientEvents.minimapScreenToWorld((float) evt.getMouseX(), (float) evt.getMouseY());
        } else if (evt.getButton() == GLFW.GLFW_MOUSE_BUTTON_1 && !onMinimap && !HudClientEvents.isMouseOverAnyButtonOrHud()) {
            target = CursorClientEvents.getPreselectedBlockPos();
        }
        if (target == null)
            return;
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
