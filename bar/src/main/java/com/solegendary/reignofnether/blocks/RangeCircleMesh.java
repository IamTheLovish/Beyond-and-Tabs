package com.solegendary.reignofnether.blocks;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.Collection;
import java.util.function.ToIntFunction;

/**
 * The top faces of a range-indicator circle (a tower's, a town centre's, a night source's), kept as plain numbers so a
 * frame draws them as four vertices each. lovish's Oct 29 game: the range circles cost 1.9-2.1 ms of every late-game
 * frame (markersRange, 10.7-13.5% of the whole client profile in all four 22:39-22:54 captures, at every zoom) - each
 * of the hundreds of faces per circle read the block above it for snow, parsed the white texture's name, made two
 * boxes, pushed and popped the pose stack and fetched the translucent buffer again (MyRenderer.drawBlockFace), every
 * frame, on screen or not. BlockClientEvents now builds a mesh per circle now and then ({@link #REFRESH_TICKS}: snow
 * and a changed circle show within a second), skips circles outside the view, and draws the faces with one pose.
 * Same faces, same colours, same heights as drawBlockFace (top face 0.01 over the block, plus 1/8 per snow layer).
 */
public final class RangeCircleMesh {
    /** How often a circle's mesh is rebuilt from the level (snow layers, a changed circle). */
    public static final int REFRESH_TICKS = 20;
    /** The light value MyRenderer.drawSolidBox writes: 255 marks an overlay for our shader pack (lit.vsh). */
    static final int LIGHT = 255;

    /** Per face: the block's x and z (its minimum corner) and the top face's y. */
    public final float[] x, y, z;
    public final int faces;
    /** The faces' bounds, for the view test. */
    public final AABB bounds;
    /** The highlight set this mesh was built from and its size then (BlockClientEvents' cache key), and when. */
    final Object source;
    final int sourceSize;
    final long builtAt;

    /**
     * @param snowLayers the snow layers on the block above each position (0 when none), read once per build
     */
    public RangeCircleMesh(Collection<BlockPos> bps, ToIntFunction<BlockPos> snowLayers, Object source, long builtAt) {
        faces = bps.size();
        x = new float[faces];
        y = new float[faces];
        z = new float[faces];
        double x0 = Double.MAX_VALUE, y0 = Double.MAX_VALUE, z0 = Double.MAX_VALUE;
        double x1 = -Double.MAX_VALUE, y1 = -Double.MAX_VALUE, z1 = -Double.MAX_VALUE;
        int i = 0;
        for (BlockPos bp : bps) {
            x[i] = bp.getX();
            z[i] = bp.getZ();
            // drawBlockFace: AABB(bp) with maxY + 0.01f, then + yOffset - in double, cast to float, as it did
            y[i] = (float) (bp.getY() + 1 + (double) 0.01f) + snowLayers.applyAsInt(bp) * 0.125f;
            x0 = Math.min(x0, x[i]);
            z0 = Math.min(z0, z[i]);
            y0 = Math.min(y0, bp.getY());
            x1 = Math.max(x1, x[i] + 1);
            z1 = Math.max(z1, z[i] + 1);
            y1 = Math.max(y1, y[i]);
            i++;
        }
        bounds = faces == 0 ? new AABB(0, 0, 0, 0, 0, 0) : new AABB(x0, y0, z0, x1, y1, z1);
        this.source = source;
        this.sourceSize = faces;
        this.builtAt = builtAt;
    }

    /** Still the mesh of this highlight set: the same set, the same size, built less than a refresh ago. */
    public boolean fresh(Object source, int size, long now) {
        return this.source == source && sourceSize == size && now - builtAt < REFRESH_TICKS && now >= builtAt;
    }

    /** Every face, through {@code pose} (the view, translated by minus the camera entity's eye, as drawSolidBox). */
    public void emit(Matrix4f pose, Matrix3f normal, VertexConsumer vc, float r, float g, float b, float a) {
        for (int i = 0; i < faces; i++)
            face(pose, normal, vc, x[i], y[i], z[i], r, g, b, a);
    }

    /** One top face: drawSolidBox's +y face, vertex for vertex. */
    public static void face(Matrix4f pose, Matrix3f normal, VertexConsumer vc, float minX, float maxY, float minZ,
                            float r, float g, float b, float a) {
        float maxX = minX + 1, maxZ = minZ + 1;
        vc.vertex(pose, minX, maxY, minZ).color(r, g, b, a).uv(0, 0).overlayCoords(0, 10).uv2(LIGHT).normal(normal, 0.0F, 1.0F, 0.0F).endVertex();
        vc.vertex(pose, minX, maxY, maxZ).color(r, g, b, a).uv(0, 0).overlayCoords(0, 10).uv2(LIGHT).normal(normal, 0.0F, 1.0F, 0.0F).endVertex();
        vc.vertex(pose, maxX, maxY, maxZ).color(r, g, b, a).uv(0, 0).overlayCoords(0, 10).uv2(LIGHT).normal(normal, 0.0F, 1.0F, 0.0F).endVertex();
        vc.vertex(pose, maxX, maxY, minZ).color(r, g, b, a).uv(0, 0).overlayCoords(0, 10).uv2(LIGHT).normal(normal, 0.0F, 1.0F, 0.0F).endVertex();
    }
}
