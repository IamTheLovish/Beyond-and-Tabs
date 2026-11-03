package com.solegendary.reignofnether.resources;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.solegendary.reignofnether.cursor.CursorClientEvents;
import com.solegendary.reignofnether.orthoview.OrthoviewClientEvents;
import com.solegendary.reignofnether.unit.AreaCommandClientEvents;
import com.solegendary.reignofnether.unit.UnitClientEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.interfaces.WorkerUnit;
import com.solegendary.reignofnether.time.ClientTicks;

import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;

/**
 * BAR's green reclaim numbers. In the RTS view every known wreck carries a compact metal marker (lovish, Oct 24:
 * players did not notice the wrecks; WreckMarkerRules picks which, how big and how faint). With a worker (or the
 * commander) selected, hovering a wreck floats the fuller "⟲ 120 metal" over it instead, and while an area-reclaim
 * circle is being dragged (Ctrl + right-drag, AreaCommandClientEvents) the circle's total hangs over its centre.
 * The metal comes from WreckSyncServerEvents once a second; the wreck itself is the block-display entity the client
 * already tracks, matched to the cursor by the hovered block position (a wreck is not a LivingEntity, so RoN's
 * preselection never sees it). At most {@link WreckServerEvents#MAX_WRECKS} entries,
 * walked once per frame; the markers are culled by distance, frustum and fog and capped, and a label string is rebuilt
 * only when its value changes.
 */
public class WreckClientEvents {

    private static final Minecraft MC = Minecraft.getInstance();
    public static final int LABEL_COLOUR = 0x7CE07A;   // BAR's reclaim green
    public static final int LABEL_BACKING = 0xC0101216;   // the HUD's dark panel colour, mostly opaque
    static final double HOVER_RANGE_SQR = 1.5 * 1.5;

    private static final Int2IntOpenHashMap metal = new Int2IntOpenHashMap();
    private static final ArrayList<Integer> stale = new ArrayList<>();
    private static int lastHoverValue = -1, lastAreaValue = -1;
    private static String hoverLabel = "", areaLabel = "";

    public static void sync(int[] ids, int[] metals) {
        for (int i = 0; i < ids.length; i++)
            metal.put(ids[i], metals[i]);
    }

    public static int metalOf(Entity e) {
        return metal.getOrDefault(e.getId(), 0);
    }

    public static boolean isKnownWreck(Entity e) {
        return e != null && metal.containsKey(e.getId());
    }

    public static void clear() {
        metal.clear();
        lastHoverValue = lastAreaValue = -1;
    }

    static boolean workerSelected() {
        ArrayList<LivingEntity> sel = UnitClientEvents.getSelectedUnits();
        for (int i = 0; i < sel.size(); i++) {
            LivingEntity le = sel.get(i);
            if (le instanceof WorkerUnit && le instanceof Unit)
                return true;
        }
        return false;
    }

    // forget wrecks whose entity is gone (stripped, decayed, left the client's view); once a second, off the frame
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent evt) {
        if (evt.phase != TickEvent.Phase.END || metal.isEmpty())
            return;
        if (MC.level == null) {
            clear();
            return;
        }
        if (ClientTicks.now() % 20 != 0)
            return;
        stale.clear();
        for (Int2IntMap.Entry e : metal.int2IntEntrySet()) {
            Entity ent = MC.level.getEntity(e.getIntKey());
            if (ent == null || ent.isRemoved())
                stale.add(e.getIntKey());
        }
        for (int i = 0; i < stale.size(); i++)
            metal.remove(stale.get(i).intValue());
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent evt) {
        if (evt.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS)
            return;
        int fp = com.solegendary.reignofnether.perf.FrameParts.begin(com.solegendary.reignofnether.perf.FrameParts.WORLD_MARKERS);
        int fpS = com.solegendary.reignofnether.perf.FrameParts.begin(com.solegendary.reignofnether.perf.FrameParts.MARKERS_WRECK);
        try {
            if (com.solegendary.reignofnether.postfx.ShaderPacks.skipOverlays()) return;   // shader pack shadow map: overlays cast no shadow
            if (evt.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || metal.isEmpty()
                    || !OrthoviewClientEvents.isEnabled() || MC.level == null)
                return;
            Camera camera = MC.getEntityRenderDispatcher().camera;
            PoseStack pose = evt.getPoseStack();
            boolean worker = workerSelected();

            // area-reclaim circle: the sum of every known wreck inside it, over the centre
            BlockPos centre = worker ? AreaCommandClientEvents.getCentre() : null;
            if (centre != null && AreaCommandClientEvents.hasWorkers()) {
                int r = AreaCommandClientEvents.getRadius();
                double r2 = (r + 0.5) * (r + 0.5);
                int total = 0;
                for (Int2IntMap.Entry e : metal.int2IntEntrySet()) {
                    Entity w = MC.level.getEntity(e.getIntKey());
                    if (w == null)
                        continue;
                    double dx = w.getX() - (centre.getX() + 0.5), dz = w.getZ() - (centre.getZ() + 0.5);
                    if (dx * dx + dz * dz <= r2)
                        total += e.getIntValue();
                }
                if (total > 0) {
                    if (total != lastAreaValue) {
                        lastAreaValue = total;
                        areaLabel = I18n.get("hud.reignofnether.reclaim_label", total);
                    }
                    drawLabel(pose, camera, centre.getX() + 0.5, centre.getY() + 1.6, centre.getZ() + 0.5, areaLabel);
                }
                return;   // while dragging, the hover label and the markers would only clutter the circle
            }

            // with a worker selected, the wreck under the cursor gets the full label instead of its marker
            Entity hovered = worker ? hoveredWreck() : null;
            drawMarkers(pose, camera, evt.getFrustum(), hovered);
            if (hovered == null)
                return;
            int value = metalOf(hovered);
            if (value != lastHoverValue) {
                lastHoverValue = value;
                hoverLabel = I18n.get("hud.reignofnether.reclaim_label", value);
            }
            drawLabel(pose, camera, hovered.getX(), hovered.getY() + 1.1, hovered.getZ(), hoverLabel);
        } finally {
            com.solegendary.reignofnether.perf.FrameParts.end(fpS);
            com.solegendary.reignofnether.perf.FrameParts.end(fp);
        }
    }

    /** The nearest known wreck to the cursor block, within a block and a half, or null. */
    private static Entity hoveredWreck() {
        BlockPos cursor = CursorClientEvents.getPreselectedBlockPos();
        if (cursor == null)
            return null;
        Entity hovered = null;
        double best = HOVER_RANGE_SQR;
        for (Int2IntMap.Entry e : metal.int2IntEntrySet()) {
            Entity w = MC.level.getEntity(e.getIntKey());
            if (w == null || Math.abs(w.getY() - cursor.getY()) > 3)
                continue;
            double dx = w.getX() - (cursor.getX() + 0.5), dz = w.getZ() - (cursor.getZ() + 0.5);
            double d = dx * dx + dz * dz;
            if (d < best) {
                best = d;
                hovered = w;
            }
        }
        return hovered;
    }

    // per-frame scratch for the markers (sized to the most wrecks the server keeps; nothing is allocated per frame
    // beyond the frustum box of each wreck in range) and the label strings by metal value
    private static final Entity[] markEnt = new Entity[WreckServerEvents.MAX_WRECKS];
    private static final double[] markDistSq = new double[WreckServerEvents.MAX_WRECKS];
    private static final int[] markMetal = new int[WreckServerEvents.MAX_WRECKS];
    private static final boolean[] markView = new boolean[WreckServerEvents.MAX_WRECKS];
    private static final int[] markPick = new int[WreckServerEvents.MAX_WRECKS];
    private static final Int2ObjectOpenHashMap<String> markLabels = new Int2ObjectOpenHashMap<>();

    private static final float MARKER_SCALE = 0.045f;   // blocks per font pixel at zoom 30; grows with zoom so it stays readable
    static final int BIG_COLOUR = 0xC9FFB4, HUGE_COLOUR = 0xFFD84A;   // brighter green, then gold: a commander wreck

    /**
     * Every known wreck gets a small metal number (lovish, Oct 24: players did not notice the wrecks). Only wrecks the
     * server told this client about (WreckSyncServerEvents sends fogged ones to nobody), in a chunk the fog shows now,
     * inside the frustum and {@link WreckMarkerRules#RANGE} of the camera, richest first up to
     * {@link WreckMarkerRules#MAX_MARKERS}; the poorest are drawn first so a big heap number lands on top.
     */
    private static void drawMarkers(PoseStack pose, Camera camera, Frustum frustum, Entity skip) {
        Vec3 cam = camera.getPosition();
        double r2 = WreckMarkerRules.RANGE * WreckMarkerRules.RANGE;
        int n = 0;
        for (Int2IntMap.Entry e : metal.int2IntEntrySet()) {
            if (n >= markEnt.length)
                break;
            int v = e.getIntValue();
            if (v <= 0)
                continue;
            Entity w = MC.level.getEntity(e.getIntKey());
            if (w == null || w == skip || w.isRemoved())
                continue;
            double d2 = cam.distanceToSqr(w.getX(), w.getY(), w.getZ());
            if (d2 > r2)
                continue;
            markEnt[n] = w;
            markDistSq[n] = d2;
            markMetal[n] = v;
            markView[n] = (frustum == null || frustum.isVisible(w.getBoundingBox().inflate(1.5)))
                && com.solegendary.reignofnether.fogofwar.FogOfWarClientEvents.isInBrightChunk(w);
            n++;
        }
        if (n == 0)
            return;
        int count = WreckMarkerRules.choose(n, markDistSq, markMetal, markView, WreckMarkerRules.MAX_MARKERS, markPick);
        if (markLabels.size() > 512)
            markLabels.clear();
        float zoomScale = Mth.clamp(OrthoviewClientEvents.getZoom() / 30f, 0.7f, 4f);
        for (int i = count - 1; i >= 0; i--) {
            int k = markPick[i];
            Entity w = markEnt[k];
            int v = markMetal[k];
            WreckMarkerRules.Tier tier = WreckMarkerRules.tier(v);
            float fade = WreckMarkerRules.fade(Math.sqrt(markDistSq[k]));
            int rgb = switch (tier) {
                case SMALL -> LABEL_COLOUR;
                case BIG -> BIG_COLOUR;
                case HUGE -> HUGE_COLOUR;
            };
            String text = markLabels.get(v);
            if (text == null) {
                text = WreckMarkerRules.label(v);
                markLabels.put(v, text);
            }
            int backing = ((int) (0xC0 * fade) << 24) | (LABEL_BACKING & 0xFFFFFF);
            drawText(pose, camera, w.getX(), w.getY() + 0.9 * zoomScale, w.getZ(), text,
                MARKER_SCALE * WreckMarkerRules.sizeMult(tier) * zoomScale, ((int) (255 * fade) << 24) | rgb, backing);
        }
        java.util.Arrays.fill(markEnt, 0, n, null);   // no stale entity references between frames
    }

    /** A camera-facing text label at a world position, same scale as RoN's resource floating text. */
    static void drawLabel(PoseStack pose, Camera camera, double x, double y, double z, String text) {
        // lovish's clip: "⟲ 45 metal" read as faint grey - the see-through pass alone, on a 0x60 backing, washes the
        // green out over bright ground. Now as vanilla name tags do it: a see-through pass carrying a dark, mostly
        // opaque backing, then a normal pass on top in full green (TextContrast: 0x7CE07A on the backing is > 7:1)
        drawText(pose, camera, x, y, z, text, 0.075F, 0xFF000000 | LABEL_COLOUR, LABEL_BACKING);
    }

    /** Camera-facing text: {@code scale} blocks per font pixel, {@code argb} the text colour, {@code backing} the see-through pass backing. */
    static void drawText(PoseStack pose, Camera camera, double x, double y, double z, String text, float scale, int argb,
                         int backing) {
        Vec3 cam = camera.getPosition();
        Font font = MC.font;
        pose.pushPose();
        pose.translate(x - cam.x(), y - cam.y(), z - cam.z());
        pose.mulPose(Axis.YP.rotationDegrees(-camera.getYRot()));
        pose.mulPose(Axis.XP.rotationDegrees(camera.getXRot()));
        pose.scale(-scale, -scale, scale);
        float half = -font.width(text) / 2f;
        font.drawInBatch(text, half, 0, argb, false, pose.last().pose(),
            MC.renderBuffers().bufferSource(), Font.DisplayMode.SEE_THROUGH, backing, 0xF000F0);
        font.drawInBatch(text, half, 0, argb, false, pose.last().pose(),
            MC.renderBuffers().bufferSource(), Font.DisplayMode.NORMAL, 0, 0xF000F0);
        pose.popPose();
    }
}
