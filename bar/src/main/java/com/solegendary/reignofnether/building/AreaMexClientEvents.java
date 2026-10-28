package com.solegendary.reignofnether.building;

import com.mojang.blaze3d.vertex.PoseStack;
import com.solegendary.reignofnether.building.buildings.shared.MetalExtractor;
import com.solegendary.reignofnether.config.ReignOfNetherClientConfigs;
import com.solegendary.reignofnether.cursor.CursorClientEvents;
import com.solegendary.reignofnether.hud.HudClientEvents;
import com.solegendary.reignofnether.keybinds.Keybindings;
import com.solegendary.reignofnether.orthoview.OrthoviewClientEvents;
import com.solegendary.reignofnether.resources.MetalPatchesClient;
import com.solegendary.reignofnether.unit.UnitClientEvents;
import com.solegendary.reignofnether.unit.interfaces.WorkerUnit;
import com.solegendary.reignofnether.util.MyRenderer;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * BAR's area mex (Lane C playtest: "when building a metal extractor and holding Shift, it should switch to area mex"):
 * with an extractor ghost armed and Shift held, the single ghost becomes a circle under the cursor. Shift + wheel
 * resizes it ({@link AreaMex#MIN_RADIUS}-{@link AreaMex#MAX_RADIUS}), a click queues an extractor on every free patch
 * inside, split between the selected builders. Placement goes through the same validator and packet as a normal
 * Shift-click, so cost and validity rules are unchanged. Which patches and who builds them lives in {@link AreaMex}.
 */
public class AreaMexClientEvents {

    static final Minecraft MC = Minecraft.getInstance();
    private static int radius = -1;   // -1: not read from the config yet

    public static int radius() {
        if (radius < 0)
            radius = AreaMex.clampRadius(ReignOfNetherClientConfigs.AREA_MEX_RADIUS.get());
        return radius;
    }

    /** True while the extractor ghost is armed and Shift is held: the circle replaces the single ghost. */
    public static boolean active() {
        return OrthoviewClientEvents.isEnabled() && MC.level != null
            && BuildingClientEvents.getBuildingToPlace() instanceof MetalExtractor
            && Keybindings.shiftMod.isDown();
    }

    /** Shift + wheel while active resizes the circle. Returns true when it used the wheel (the camera must not zoom). */
    public static boolean onScroll(double delta) {
        if (!active() || delta == 0)
            return false;
        radius = AreaMex.clampRadius(radius() + (delta > 0 ? 2 : -2));
        ReignOfNetherClientConfigs.AREA_MEX_RADIUS.set(radius);
        HudClientEvents.showTemporaryMessage(Component.translatable("hud.reignofnether.area_mex_radius", radius).getString());
        return true;
    }

    static List<BlockPos> freePatches() {
        return AreaMex.freeInside(MetalPatchesClient.getPatches(), BuildingClientEvents::isPatchTaken,
            CursorClientEvents.getPreselectedBlockPos(), radius());
    }

    /** Left click while active: queue an extractor on every free patch in the circle, builders sharing them. */
    static void place() {
        if (MC.level == null || MC.player == null)
            return;
        Building extractor = BuildingClientEvents.getBuildingToPlace();
        BlockPos cursor = CursorClientEvents.getPreselectedBlockPos();
        String owner = MC.player.getName().getString();
        List<AreaMex.Builder> builders = new ArrayList<>();
        for (LivingEntity e : UnitClientEvents.getSelectedUnits())
            if (e instanceof WorkerUnit)
                builders.add(new AreaMex.Builder(e.getId(), (int) Math.floor(e.getX()), (int) Math.floor(e.getZ())));
        int queued = 0;
        String firstError = null;
        for (Map.Entry<Integer, List<BlockPos>> group : AreaMex.split(freePatches(), builders, cursor).entrySet()) {
            int[] ids = group.getKey() == AreaMex.NO_BUILDER ? new int[0] : new int[] { group.getKey() };
            for (BlockPos target : group.getValue()) {
                BlockPos originPos = target.offset(-2, 0, -2);   // 5x5 centred on the patch, as the one-click mex
                String error = BuildingValidators.getPlacementValidityError(MC.level, extractor, originPos, owner,
                    Rotation.NONE, false, false, true);
                if (error != null) {
                    if (firstError == null)
                        firstError = error;
                    continue;
                }
                BuildingServerboundPacket.placeAndQueueBuilding(extractor, originPos, Rotation.NONE, owner, ids, false);
                queued++;
            }
        }
        if (queued == 0)
            HudClientEvents.showTemporaryMessage(firstError != null
                ? Component.translatable("hud.reignofnether.extractor_blocked", Component.translatable(firstError).getString()).getString()
                : Component.translatable("hud.reignofnether.extractor_no_free_patch").getString());
        else
            HudClientEvents.showTemporaryMessage(Component.translatable("hud.reignofnether.building_extractors", queued).getString());
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent evt) {
        if (evt.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || !active())
            return;
        BlockPos cursor = CursorClientEvents.getPreselectedBlockPos();
        if (cursor == null)
            return;
        PoseStack matrix = evt.getPoseStack();
        double cx = cursor.getX() + 0.5, cz = cursor.getZ() + 0.5, y = cursor.getY() + 1.05;
        int r = radius(), steps = Math.max(48, r * 6);
        for (int i = 0; i < steps; i++) {
            double a0 = Math.PI * 2 * i / steps, a1 = Math.PI * 2 * (i + 1) / steps;
            MyRenderer.drawLine(matrix, cx + Math.cos(a0) * r, y, cz + Math.sin(a0) * r,
                cx + Math.cos(a1) * r, y, cz + Math.sin(a1) * r, 0.3f, 1f, 0.3f, 0.9f);
        }
        // each free patch inside gets the extractor ghost's green footprint
        for (BlockPos c : freePatches()) {
            AABB box = new AABB(c.getX() - 2, c.getY() + 1.05, c.getZ() - 2, c.getX() + 3, c.getY() + 1.05, c.getZ() + 3);
            MyRenderer.drawLineBox(matrix, box, 0f, 1f, 0f, 0.9f);
        }
    }
}
