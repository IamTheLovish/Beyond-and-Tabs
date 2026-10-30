package com.solegendary.reignofnether.hero;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.solegendary.reignofnether.barfx.BarFxRenderTypes;
import com.solegendary.reignofnether.fogofwar.FogOfWarClientEvents;
import com.solegendary.reignofnether.orthoview.OrthoviewClientEvents;
import com.solegendary.reignofnether.player.PlayerColors;
import com.solegendary.reignofnether.unit.UnitClientEvents;
import com.solegendary.reignofnether.unit.interfaces.HeroUnit;
import com.solegendary.reignofnether.unit.interfaces.Unit;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * The hero marker (HeroLook; lovish, Oct 30: "Ring + crest, 1.25x"): in the RTS view every hero the client holds has a
 * thin ring in its owner's colour on the ground round its feet and a small crest over its head - a camera-facing
 * diamond, gold rim round the owner's colour - so a hero is found in a crowd at a glance. The viewer's own hero with
 * an unspent ability point also shows a pulsing gold "+" beside its crest (HeroLook.showPointBadge). One translucent batch a frame
 * for all heroes (a handful per match). Heroes under the fog of war, or cloaked from the viewer, get none
 * (HeroLook.showMarker).
 */
@OnlyIn(Dist.CLIENT)
public final class HeroMarkerClientEvents {
    private HeroMarkerClientEvents() { }

    private static final int GOLD = 0xDEAC3A;
    private static final int RING_SEGMENTS = 28;
    private static boolean failedOnce = false;

    private static VertexConsumer vc;
    private static Matrix4f mat;
    private static float camX, camY, camZ;

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent evt) {
        if (evt.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS)
            return;
        int fp = com.solegendary.reignofnether.perf.FrameParts.begin(com.solegendary.reignofnether.perf.FrameParts.WORLD_MARKERS);
        try {
            if (com.solegendary.reignofnether.postfx.ShaderPacks.skipOverlays())
                return;   // shader pack shadow map: overlays cast no shadow
            if (!OrthoviewClientEvents.isEnabled())
                return;   // HeroLook.showMarker: the RTS view only
            render(evt.getPoseStack(), evt.getCamera(), evt.getPartialTick());
        } catch (Exception e) {
            // never take the game down over a marker
            if (!failedOnce) {
                failedOnce = true;
                com.solegendary.reignofnether.ReignOfNether.LOGGER.error("Hero marker render failed", e);
            }
        } finally {
            com.solegendary.reignofnether.perf.FrameParts.end(fp);
        }
    }

    private static void render(PoseStack pose, Camera camera, float pt) {
        MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        var cp = camera.getPosition();
        camX = (float) cp.x;
        camY = (float) cp.y;
        camZ = (float) cp.z;
        Vector3f left = camera.getLeftVector(), up = camera.getUpVector();
        boolean begun = false;
        String me = Minecraft.getInstance().player == null ? null : Minecraft.getInstance().player.getName().getString();
        float pulse = 0.65f + 0.35f * (float) Math.sin(System.currentTimeMillis() / 160.0);
        for (LivingEntity e : UnitClientEvents.getAllUnits()) {
            if (!(e instanceof Unit u) || !e.isAlive() || !HeroLook.showMarker(e instanceof HeroUnit, true,
                    FogOfWarClientEvents.isInBrightChunk(e), UnitClientEvents.isHiddenFromLocalPlayer(e)))
                continue;
            if (!begun) {
                vc = buffers.getBuffer(BarFxRenderTypes.TRANSLUCENT);
                mat = pose.last().pose();
                begun = true;
            }
            String owner = u.getOwnerName();
            int colour = owner == null || owner.isBlank() ? 0xFFFFFF : PlayerColors.getPlayerDisplayColorHex(owner) & 0xFFFFFF;
            float x = (float) Mth.lerp(pt, e.xo, e.getX());
            float y = (float) Mth.lerp(pt, e.yo, e.getY());
            float z = (float) Mth.lerp(pt, e.zo, e.getZ());
            ring(x, y + 0.06f, z, HeroLook.ringRadius(e.getBbWidth()), 0.07f, colour, 0.9f);
            float cy = y + HeroLook.crestHeight(e.getBbHeight());
            crest(x, cy, z, 0.32f, colour, left, up);
            boolean own = me != null && me.equals(owner);
            if (HeroLook.showPointBadge(own, ((HeroUnit) e).getSkillPoints()))
                plus(x - left.x() * 0.55f, cy + up.y() * 0.2f, z - left.z() * 0.55f, 0.16f, pulse, left, up);
        }
        if (begun) {
            buffers.endBatch(BarFxRenderTypes.TRANSLUCENT);
            vc = null;
        }
    }

    private static void v(float x, float y, float z, int rgb, float a) {
        vc.vertex(mat, x - camX, y - camY, z - camZ)
            .color(((rgb >> 16) & 255) / 255f, ((rgb >> 8) & 255) / 255f, (rgb & 255) / 255f, a)
            .endVertex();
    }

    /** A flat ring on the ground: a solid band of half-width {@code w} round radius {@code r}. */
    private static void ring(float x, float y, float z, float r, float w, int c, float a) {
        float ri = r - w, ro = r + w;
        for (int i = 0; i < RING_SEGMENTS; i++) {
            double a0 = i * Math.PI * 2 / RING_SEGMENTS, a1 = (i + 1) * Math.PI * 2 / RING_SEGMENTS;
            float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0), c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
            v(x + c0 * ri, y, z + s0 * ri, c, a);
            v(x + c1 * ri, y, z + s1 * ri, c, a);
            v(x + c1 * ro, y, z + s1 * ro, c, a);
            v(x + c0 * ro, y, z + s0 * ro, c, a);
        }
    }

    /** A camera-facing diamond of half-size {@code s}: a gold rim round the owner's colour. */
    private static void crest(float x, float y, float z, float s, int c, Vector3f left, Vector3f up) {
        diamond(x, y, z, s, GOLD, 0.95f, left, up);
        diamond(x, y, z, s * 0.62f, c, 1.0f, left, up);
    }

    /** The unspent-point badge: a gold camera-facing "+" of half-size {@code s}, its alpha pulsing. */
    private static void plus(float x, float y, float z, float s, float a, Vector3f left, Vector3f up) {
        float t = s * 0.32f;
        quad(x, y, z, left, s, up, t, GOLD, a);   // the bar across
        quad(x, y, z, left, t, up, s, GOLD, a);   // the bar up
    }

    /** A camera-facing rectangle: {@code w} along {@code left}, {@code h} along {@code up}, both half-sizes. */
    private static void quad(float x, float y, float z, Vector3f left, float w, Vector3f up, float h, int c, float a) {
        float lx = left.x() * w, ly = left.y() * w, lz = left.z() * w, ux = up.x() * h, uy = up.y() * h, uz = up.z() * h;
        v(x + lx + ux, y + ly + uy, z + lz + uz, c, a);
        v(x - lx + ux, y - ly + uy, z - lz + uz, c, a);
        v(x - lx - ux, y - ly - uy, z - lz - uz, c, a);
        v(x + lx - ux, y + ly - uy, z + lz - uz, c, a);
    }

    private static void diamond(float x, float y, float z, float s, int c, float a, Vector3f left, Vector3f up) {
        // corners: top, left, bottom, right (one quad); drawn twice so the inner one sits over the rim
        v(x + up.x() * s, y + up.y() * s, z + up.z() * s, c, a);
        v(x + left.x() * s, y + left.y() * s, z + left.z() * s, c, a);
        v(x - up.x() * s, y - up.y() * s, z - up.z() * s, c, a);
        v(x - left.x() * s, y - left.y() * s, z - left.z() * s, c, a);
    }
}
