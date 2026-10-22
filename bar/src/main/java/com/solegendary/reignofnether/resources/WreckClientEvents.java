package com.solegendary.reignofnether.resources;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.solegendary.reignofnether.cursor.CursorClientEvents;
import com.solegendary.reignofnether.orthoview.OrthoviewClientEvents;
import com.solegendary.reignofnether.unit.AreaCommandClientEvents;
import com.solegendary.reignofnether.unit.UnitClientEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.interfaces.WorkerUnit;

import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;

/**
 * BAR's green reclaim numbers. With a worker (or the commander) selected, hovering a wreck floats "⟲ 120 metal"
 * over it, and while an area-reclaim circle is being dragged (Ctrl + right-drag, AreaCommandClientEvents) the
 * circle's total hangs over its centre. The metal comes from WreckSyncServerEvents once a second; the wreck itself is
 * the block-display entity the client already tracks, matched to the cursor by the hovered block position (a wreck
 * is not a LivingEntity, so RoN's preselection never sees it). At most {@link WreckServerEvents#MAX_WRECKS} entries,
 * walked once per frame only while a worker is selected; the label string is rebuilt only when its value changes.
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
        if (MC.level.getGameTime() % 20 != 0)
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
        if (com.solegendary.reignofnether.postfx.ShaderPacks.skipOverlays()) return;   // shader pack shadow map: overlays cast no shadow
        if (evt.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || metal.isEmpty()
                || !OrthoviewClientEvents.isEnabled() || MC.level == null || !workerSelected())
            return;
        Camera camera = MC.getEntityRenderDispatcher().camera;
        PoseStack pose = evt.getPoseStack();

        // area-reclaim circle: the sum of every known wreck inside it, over the centre
        BlockPos centre = AreaCommandClientEvents.getCentre();
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
            return;   // while dragging, the hover label would only clutter the circle
        }

        // hovered wreck: the nearest known wreck to the cursor's block, within a block and a half
        BlockPos cursor = CursorClientEvents.getPreselectedBlockPos();
        if (cursor == null)
            return;
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
        if (hovered == null)
            return;
        int value = metalOf(hovered);
        if (value != lastHoverValue) {
            lastHoverValue = value;
            hoverLabel = I18n.get("hud.reignofnether.reclaim_label", value);
        }
        drawLabel(pose, camera, hovered.getX(), hovered.getY() + 1.1, hovered.getZ(), hoverLabel);
    }

    /** A camera-facing text label at a world position, same scale as RoN's resource floating text. */
    static void drawLabel(PoseStack pose, Camera camera, double x, double y, double z, String text) {
        Vec3 cam = camera.getPosition();
        Font font = MC.font;
        pose.pushPose();
        pose.translate(x - cam.x(), y - cam.y(), z - cam.z());
        pose.mulPose(Axis.YP.rotationDegrees(-camera.getYRot()));
        pose.mulPose(Axis.XP.rotationDegrees(camera.getXRot()));
        pose.scale(-0.075F, -0.075F, 0.075F);
        float half = -font.width(text) / 2f;
        // lovish's clip: "⟲ 45 metal" read as faint grey - the see-through pass alone, on a 0x60 backing, washes the
        // green out over bright ground. Now as vanilla name tags do it: a see-through pass carrying a dark, mostly
        // opaque backing, then a normal pass on top in full green (TextContrast: 0x7CE07A on the backing is > 7:1)
        font.drawInBatch(text, half, 0, 0xFF000000 | LABEL_COLOUR, false, pose.last().pose(),
            MC.renderBuffers().bufferSource(), Font.DisplayMode.SEE_THROUGH, LABEL_BACKING, 0xF000F0);
        font.drawInBatch(text, half, 0, 0xFF000000 | LABEL_COLOUR, false, pose.last().pose(),
            MC.renderBuffers().bufferSource(), Font.DisplayMode.NORMAL, 0, 0xF000F0);
        pose.popPose();
    }
}
