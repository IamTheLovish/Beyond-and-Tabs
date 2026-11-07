package com.solegendary.reignofnether.building;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.solegendary.reignofnether.building.buildings.shared.MetalExtractor;
import com.solegendary.reignofnether.cursor.CursorClientEvents;
import com.solegendary.reignofnether.faction.Faction;
import com.solegendary.reignofnether.faction.FactionTraits;
import com.solegendary.reignofnether.hud.HudClientEvents;
import com.solegendary.reignofnether.keybinds.Keybindings;
import com.solegendary.reignofnether.orthoview.OrthoviewClientEvents;
import com.solegendary.reignofnether.player.PlayerClientEvents;
import com.solegendary.reignofnether.resources.MetalPatches;
import com.solegendary.reignofnether.resources.MetalPatchesClient;
import com.solegendary.reignofnether.unit.UnitClientEvents;
import com.solegendary.reignofnether.unit.interfaces.WorkerUnit;
import com.solegendary.reignofnether.util.MiscUtil;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Rotation;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * BAR's mex building from the mouse.
 * <ul>
 *   <li>One-click mex: with a worker selected, right-clicking a metal patch (the raw iron in the ground) queues your
 *       faction's Metal Extractor centred on it - no trip to the build menu. The patch you're pointing at flashes a
 *       hint in the HUD while hovered.</li>
 *   <li>Area mex (BAR's areamex): with the extractor ghost out, hold the left button and drag a circle - the press
 *       point is the centre, the distance dragged the radius, and the circle is drawn while you drag. On release an
 *       extractor is queued on every free patch inside, as one nearest-neighbour chain from the builders. Shift adds
 *       to the builders' queue instead of replacing it. A plain click (no drag) places one extractor as always.</li>
 * </ul>
 * The pure part (which patches, in what order) is AreaMexCircle.
 */
public class PatchQuickBuildClientEvents {

    static final Minecraft MC = Minecraft.getInstance();
    static final int CIRCLE_SEGMENTS = 48;
    static long lastHintAt = 0;

    // the area-mex drag: where the left button went down (world: the circle's centre; GUI pixels: for the click test)
    static BlockPos dragCentre = null;
    static double pressX, pressY;
    // set while the click is replayed to the normal placement (BuildingClientEvents.onMouseClick), so we let it through
    static boolean replaying = false;

    /** FactionTraits.metalExtractor; null (no quick-build) for factions without one. */
    static Building extractorFor(Faction faction) {
        return FactionTraits.of(faction).metalExtractor.get();
    }

    /** The hovered patch block, if the cursor is on (or right above) one. */
    static BlockPos hoveredPatch() {
        if (MC.level == null)
            return null;
        BlockPos bp = CursorClientEvents.getPreselectedBlockPos();
        if (bp == null)
            return null;
        for (BlockPos p : new BlockPos[]{ bp, bp.below(), bp.above() })
            if (MC.level.getBlockState(p).is(MetalPatches.PATCH_BLOCK))
                return p;
        // never-explored fog shows the ground from before the patches were stamped: the synced list still knows them
        // (BuildingValidators.isKnownPatchUnderFog lets the extractor's blueprint go there)
        for (BlockPos c : MetalPatchesClient.getPatches())
            if (Math.abs(c.getX() - bp.getX()) + Math.abs(c.getZ() - bp.getZ()) <= 1 && Math.abs(c.getY() - bp.getY()) <= 3
                    && !com.solegendary.reignofnether.fogofwar.FogOfWarClientEvents.isInBrightChunk(c))
                return c;
        return null;
    }

    /**
     * The plus-shaped patch's centre block, from whichever of its five blocks the cursor is on: the synced centre
     * list first, then the shape itself. Centring the 5x5 on an arm put it a block off, against neighbours.
     */
    static BlockPos snapToCentre(BlockPos hovered) {
        for (BlockPos c : MetalPatchesClient.getPatches())
            if (Math.abs(c.getX() - hovered.getX()) + Math.abs(c.getZ() - hovered.getZ()) <= 1
                    && Math.abs(c.getY() - hovered.getY()) <= 2)
                return c;
        if (MC.level == null)
            return hovered;
        for (BlockPos n : new BlockPos[]{ hovered.east(), hovered.west(), hovered.north(), hovered.south() }) {
            if (!MC.level.getBlockState(n).is(MetalPatches.PATCH_BLOCK))
                continue;
            int arms = 0;
            for (BlockPos m : new BlockPos[]{ n.east(), n.west(), n.north(), n.south() })
                if (MC.level.getBlockState(m).is(MetalPatches.PATCH_BLOCK))
                    arms++;
            if (arms >= 3)
                return n;   // the hovered block is an arm; n is the hub
        }
        return hovered;
    }

    static boolean hasSelectedWorker() {
        for (LivingEntity entity : UnitClientEvents.getSelectedUnits())
            if (entity instanceof WorkerUnit)
                return true;
        return false;
    }

    static int[] selectedBuilderIds() {
        ArrayList<Integer> builderIds = new ArrayList<>();
        for (LivingEntity entity : UnitClientEvents.getSelectedUnits())
            if (entity instanceof WorkerUnit)
                builderIds.add(entity.getId());
        int[] ids = new int[builderIds.size()];
        for (int i = 0; i < ids.length; i++)
            ids[i] = builderIds.get(i);
        return ids;
    }

    /**
     * Queues an extractor on each target in order on the selected builders and says what happened on the HUD.
     * The first one replaces the builders' queue unless {@code append}; the rest always join it. A target the
     * validator refuses is skipped; its reason is shown when nothing at all could be queued.
     *
     * @return how many were queued
     */
    static int queueExtractors(Building extractor, List<BlockPos> targets, boolean append) {
        int[] ids = selectedBuilderIds();
        int queued = 0;
        String firstError = null;
        String owner = MC.player.getName().getString();
        for (BlockPos target : targets) {
            BlockPos originPos = target.offset(-2, 0, -2);   // 5x5 centred on the patch; the origin IS the ground block (blocks go at origin.y+1)
            // validate here so the player hears WHY when a patch can't take an extractor (the server rejects silently)
            String error = BuildingValidators.getPlacementValidityError(MC.level, extractor, originPos,
                    owner, Rotation.NONE, false, false, true);
            if (error != null) {
                if (firstError == null) firstError = error;
                continue;
            }
            if (!AreaMex.queueFor(queued, append))
                BuildingServerboundPacket.placeBuilding(extractor, originPos, Rotation.NONE, owner, ids, false);
            else
                BuildingServerboundPacket.placeAndQueueBuilding(extractor, originPos, Rotation.NONE, owner, ids, false);
            queued++;
        }
        if (queued == 0) {
            HudClientEvents.showTemporaryMessage(firstError != null
                    ? Component.translatable("hud.reignofnether.extractor_blocked",
                        Component.translatable(firstError).getString()).getString()
                    : Component.translatable("hud.reignofnether.extractor_no_free_patch").getString());
            return 0;
        }
        HudClientEvents.showTemporaryMessage(queued > 1
                ? Component.translatable("hud.reignofnether.building_extractors", queued).getString()
                : Component.translatable("hud.reignofnether.building_extractor").getString());
        return queued;
    }

    @SubscribeEvent
    public static void onMouseClick(ScreenEvent.MouseButtonPressed.Pre evt) {
        if (evt.getButton() != GLFW.GLFW_MOUSE_BUTTON_2 || !OrthoviewClientEvents.isEnabled()
                || MC.level == null || MC.player == null)
            return;
        if (BuildingClientEvents.getBuildingToPlace() != null || HudClientEvents.isMouseOverAnyButtonOrHud())
            return;
        if (!hasSelectedWorker())
            return;
        BlockPos patch = hoveredPatch();
        if (patch == null)
            return;
        patch = snapToCentre(patch);
        Building extractor = extractorFor(PlayerClientEvents.getFaction());
        if (!(extractor instanceof MetalExtractor))
            return;
        queueExtractors(extractor, List.of(patch), true);   // a quick-build always queues, as it did
        evt.setCanceled(true);   // don't also issue a move order
    }

    // ---- area mex: the left-button circle drag ----

    static boolean extractorGhostOut() {
        return BuildingClientEvents.getBuildingToPlace() instanceof MetalExtractor;
    }

    /**
     * Left press with the extractor ghost out starts the circle instead of placing at once (we run before
     * BuildingClientEvents and cancel the press). The press is replayed to the normal placement when the button comes
     * up without a drag, so a plain click places one extractor exactly as before.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLeftPress(ScreenEvent.MouseButtonPressed.Pre evt) {
        if (replaying || evt.getButton() != GLFW.GLFW_MOUSE_BUTTON_1 || !OrthoviewClientEvents.isEnabled()
                || MC.level == null || MC.player == null || !extractorGhostOut()
                || Keybindings.altMod.isDown() || HudClientEvents.isMouseOverAnyButtonOrHud())
            return;
        BlockPos cursor = CursorClientEvents.getPreselectedBlockPos();
        if (cursor == null)
            return;
        dragCentre = cursor;
        pressX = evt.getMouseX();
        pressY = evt.getMouseY();
        evt.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLeftRelease(ScreenEvent.MouseButtonReleased.Pre evt) {
        if (evt.getButton() != GLFW.GLFW_MOUSE_BUTTON_1 || dragCentre == null)
            return;
        BlockPos centre = dragCentre;
        dragCentre = null;
        if (MC.level == null || MC.player == null || !extractorGhostOut())
            return;
        if (AreaMexCircle.isClick(evt.getMouseX() - pressX, evt.getMouseY() - pressY)) {
            replayClick(evt);
            return;
        }
        evt.setCanceled(true);
        BlockPos cursor = CursorClientEvents.getPreselectedBlockPos();
        if (cursor == null)
            return;
        Building extractor = BuildingClientEvents.getBuildingToPlace();
        List<BlockPos> targets = patchesInDrag(centre, cursor);
        boolean shift = Keybindings.shiftMod.isDown();
        if (targets.isEmpty())
            HudClientEvents.showTemporaryMessage(Component.translatable("hud.reignofnether.extractor_no_free_patch").getString());
        else if (queueExtractors(extractor, targets, shift) > 0 && !shift)
            BuildingClientEvents.setBuildingToPlace(null);
    }

    /**
     * The free patches inside the dragged circle in build order (AreaMexCircle.order): one nearest-neighbour chain the
     * whole selection shares, from the builders' average start (with Shift, where their queues end).
     */
    static List<BlockPos> patchesInDrag(BlockPos centre, BlockPos cursor) {
        List<BlockPos> inside = AreaMexCircle.patchesIn(MetalPatchesClient.getPatches(),
            BuildingClientEvents::isPatchTaken, centre, AreaMexCircle.radius(centre, cursor));
        return AreaMexCircle.order(inside, AreaMexClientEvents.selectedBuilders(Keybindings.shiftMod.isDown()), centre);
    }

    /** The button came up where it went down: give the press to everyone else, as if we had never held it back. */
    private static void replayClick(ScreenEvent.MouseButtonReleased.Pre evt) {
        replaying = true;
        try {
            var screen = evt.getScreen();
            double x = pressX, y = pressY;
            if (MinecraftForge.EVENT_BUS.post(new ScreenEvent.MouseButtonPressed.Pre(screen, x, y, GLFW.GLFW_MOUSE_BUTTON_1)))
                return;
            MinecraftForge.EVENT_BUS.post(new ScreenEvent.MouseButtonPressed.Post(screen, x, y, GLFW.GLFW_MOUSE_BUTTON_1, false));
        } finally {
            replaying = false;
        }
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent evt) {
        if (dragCentre == null)
            return;
        if (!OrthoviewClientEvents.isEnabled() || MC.level == null || !extractorGhostOut() || !MiscUtil.isLeftClickDown(MC)) {
            dragCentre = null;   // released outside the screen, or the ghost was dropped: never leave a stale circle
            return;
        }
        if (com.solegendary.reignofnether.postfx.ShaderPacks.skipOverlays()) return;   // shader pack shadow map: overlays cast no shadow
        if (evt.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES)
            return;
        BlockPos cursor = CursorClientEvents.getPreselectedBlockPos();
        Entity cam = MC.getCameraEntity();
        if (cursor == null || cam == null)
            return;
        float radius = (float) AreaMexCircle.radius(dragCentre, cursor);
        if (radius < 1.0f)
            return;   // still a click
        int free = patchesInDrag(dragCentre, cursor).size();
        HudClientEvents.showTemporaryMessage(Component.translatable("hud.reignofnether.area_mex_circle", free).getString(), 1);
        PoseStack pose = evt.getPoseStack();
        VertexConsumer vc = MC.renderBuffers().bufferSource().getBuffer(RenderType.lines());
        pose.pushPose();
        pose.translate(-cam.getX(), -(cam.getY() + cam.getEyeHeight()), -cam.getZ());
        Matrix4f m = pose.last().pose();
        Matrix3f n = pose.last().normal();
        float cx = dragCentre.getX() + 0.5f, cy = dragCentre.getY() + 1.1f, cz = dragCentre.getZ() + 0.5f;
        // gold, like the metal it is after
        float r = 1.0f, g = 0.82f, b = 0.25f;
        float px = cx + radius, pz = cz;
        for (int i = 1; i <= CIRCLE_SEGMENTS; i++) {
            double ang = Math.PI * 2 * i / CIRCLE_SEGMENTS;
            float x = cx + (float) (Math.cos(ang) * radius), z = cz + (float) (Math.sin(ang) * radius);
            line(vc, m, n, px, cy, pz, x, cy, z, r, g, b);
            px = x;
            pz = z;
        }
        line(vc, m, n, cx - 0.4f, cy, cz, cx + 0.4f, cy, cz, r, g, b);
        line(vc, m, n, cx, cy, cz - 0.4f, cx, cy, cz + 0.4f, r, g, b);
        pose.popPose();
    }

    private static void line(VertexConsumer vc, Matrix4f m, Matrix3f n, float x1, float y1, float z1,
                             float x2, float y2, float z2, float r, float g, float b) {
        float dx = x2 - x1, dy = y2 - y1, dz = z2 - z1;
        float len = Mth.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1.0e-4f)
            return;
        // the lines shader widens each segment along its normal, so the normal must be the segment's direction
        dx /= len;
        dy /= len;
        dz /= len;
        vc.vertex(m, x1, y1, z1).color(r, g, b, 0.9f).normal(n, dx, dy, dz).endVertex();
        vc.vertex(m, x2, y2, z2).color(r, g, b, 0.9f).normal(n, dx, dy, dz).endVertex();
    }

    /** A gentle hint while hovering a patch with a worker selected. */
    @SubscribeEvent
    public static void onRenderTick(net.minecraftforge.event.TickEvent.RenderTickEvent evt) {
        if (evt.phase != net.minecraftforge.event.TickEvent.Phase.END || !OrthoviewClientEvents.isEnabled())
            return;
        if (BuildingClientEvents.getBuildingToPlace() != null || !hasSelectedWorker())
            return;
        if (hoveredPatch() == null)
            return;
        long now = System.currentTimeMillis();
        if (now - lastHintAt > 2500) {
            lastHintAt = now;
            HudClientEvents.showTemporaryMessage(Component.translatable("hud.reignofnether.patch_hint").getString());
        }
    }
}
