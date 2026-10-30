package com.solegendary.reignofnether.hud;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.solegendary.reignofnether.orthoview.OrthoviewClientEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * BAR's "Unit Group Number" widget (luaui/Widgets/gui_unit_group_number.lua): the control-group digit drawn beside each
 * grouped unit, at the left end of where its health bar sits (HealthBarClientEvents uses the same height and width
 * rule). Units are queued as they are drawn (HudClientEvents.onRenderLivingEntity), so off-screen units cost nothing.
 */
public final class ControlGroupNumbers {
    private ControlGroupNumbers() { }

    private static final List<LivingEntity> queued = new ArrayList<>();
    private static final List<Integer> queuedGroup = new ArrayList<>();
    // rebuilt after each frame's draw from the groups (one frame behind, which no one can see)
    private static Map<Integer, Integer> groupById = new HashMap<>();

    public static void queue(LivingEntity entity) {
        if (groupById.isEmpty() || !(entity instanceof Unit) || !OrthoviewClientEvents.isEnabled())
            return;
        if (com.solegendary.reignofnether.postfx.ShaderPacks.renderingShadowPass())
            return;   // the shadow map's draw of the unit is not the view's
        Integer g = groupById.get(entity.getId());
        if (g == null)
            return;
        queued.add(entity);
        queuedGroup.add(g);
    }

    public static void render(PoseStack pose, Camera camera, float partialTick) {
        Minecraft MC = Minecraft.getInstance();
        try {
            if (queued.isEmpty() || MC.level == null || camera == null
                    || com.solegendary.reignofnether.postfx.ShaderPacks.skipOverlays())
                return;
            Vec3 cam = camera.getPosition();
            Font font = MC.font;
            MultiBufferSource.BufferSource buffers = MC.renderBuffers().bufferSource();
            float scale = 0.025f;
            for (int i = 0; i < queued.size(); i++) {
                LivingEntity e = queued.get(i);
                if (e.isRemoved())
                    continue;
                double x = Mth.lerp(partialTick, e.xo, e.getX());
                double y = Mth.lerp(partialTick, e.yo, e.getY()) + e.getBbHeight() + 0.6;
                double z = Mth.lerp(partialTick, e.zo, e.getZ());
                int barWidth = Math.max(20, Math.min(120, (int) e.getMaxHealth()));
                String text = String.valueOf(queuedGroup.get(i));
                pose.pushPose();
                pose.translate(x - cam.x, y - cam.y, z - cam.z);
                pose.mulPose(Axis.YP.rotationDegrees(-camera.getYRot()));
                pose.mulPose(Axis.XP.rotationDegrees(camera.getXRot()));
                pose.scale(-scale, -scale, scale);
                font.drawInBatch(text, -barWidth / 2f - font.width(text) - 3, -4, 0xFFFFFFFF, false,
                        pose.last().pose(), buffers, Font.DisplayMode.SEE_THROUGH, 0x80000000, 0xF000F0);
                pose.popPose();
            }
            buffers.endBatch();
            RenderSystem.enableDepthTest();
        } finally {
            queued.clear();
            queuedGroup.clear();
            groupById = ControlGroup.groupByUnitId();
        }
    }
}
