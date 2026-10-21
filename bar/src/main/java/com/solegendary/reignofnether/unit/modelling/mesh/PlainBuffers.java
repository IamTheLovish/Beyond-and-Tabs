package com.solegendary.reignofnether.unit.modelling.mesh;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * The buffers a mesh body is drawn into: the same buffers, behind a VertexConsumer that forwards every call unchanged
 * and is NOT a Sodium VertexBufferWriter - which is the whole point.
 *
 * Why (eyes #249, the shader lane: Oculus 1.8.0 + Embeddium 0.3.31): Oculus' copyEntity ModelPartMixin cancels
 * ModelPart.render at its head whenever the consumer is a VertexBufferWriter, and draws the whole tree itself
 * (Embeddium's EntityRenderer.render) from the ModelCuboid each cube's CONSTRUCTOR made - compile() is never called.
 * A MeshCube's ModelCuboid has no faces (it passes none to the Cube constructor), so the mesh Paladin drew 0 px there
 * while the cube Paladin drew 118,200. Behind this consumer VertexBufferWriter.tryOf is null, so Oculus and Embeddium
 * both step aside and vanilla's ModelPart.render runs: translateAndRotate, compile, MeshCube.compile, and the
 * 14-argument vertex(...) reaches the real buffer as it is (with Embeddium: SodiumBufferBuilder.vertex, whose
 * endVertex Oculus extends with the entity id, mid-texture and tangent - the extended data shaders read).
 *
 * Only a mesh body's draws come through here (RadiantPaladinRenderer wraps its buffers while the mesh body is on); cube
 * bodies keep Embeddium's writer. Without Embeddium nothing changes at all: vanilla never asks what a consumer is.
 * One small object per getBuffer call (a few per unit per frame), never per vertex.
 */
@OnlyIn(Dist.CLIENT)
public final class PlainBuffers implements MultiBufferSource {
    private final MultiBufferSource source;

    private PlainBuffers(MultiBufferSource source) {
        this.source = source;
    }

    /** The source's buffers as plain consumers (a source already wrapped is returned as it is). */
    public static MultiBufferSource of(MultiBufferSource source) {
        return source instanceof PlainBuffers ? source : new PlainBuffers(source);
    }

    /** One consumer behind a plain one. */
    static VertexConsumer plain(VertexConsumer consumer) {
        return consumer instanceof Plain ? consumer : new Plain(consumer);
    }

    @Override
    public VertexConsumer getBuffer(RenderType type) {
        return plain(source.getBuffer(type));
    }

    /**
     * Every VertexConsumer method a draw can reach, forwarded unchanged: the abstract ones and the 14-argument vertex
     * (so the real buffer's own fast path for it runs, e.g. BufferBuilder's for the entity format); the interface's
     * default methods are made of these, so they arrive here as these.
     */
    private static final class Plain implements VertexConsumer {
        private final VertexConsumer to;

        Plain(VertexConsumer to) {
            this.to = to;
        }

        @Override
        public void vertex(float x, float y, float z, float red, float green, float blue, float alpha, float u, float v,
                           int overlay, int light, float normalX, float normalY, float normalZ) {
            to.vertex(x, y, z, red, green, blue, alpha, u, v, overlay, light, normalX, normalY, normalZ);
        }

        @Override
        public VertexConsumer vertex(double x, double y, double z) {
            to.vertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer color(int red, int green, int blue, int alpha) {
            to.color(red, green, blue, alpha);
            return this;
        }

        @Override
        public VertexConsumer uv(float u, float v) {
            to.uv(u, v);
            return this;
        }

        @Override
        public VertexConsumer overlayCoords(int u, int v) {
            to.overlayCoords(u, v);
            return this;
        }

        @Override
        public VertexConsumer uv2(int u, int v) {
            to.uv2(u, v);
            return this;
        }

        @Override
        public VertexConsumer normal(float x, float y, float z) {
            to.normal(x, y, z);
            return this;
        }

        @Override
        public void endVertex() {
            to.endVertex();
        }

        @Override
        public void defaultColor(int red, int green, int blue, int alpha) {
            to.defaultColor(red, green, blue, alpha);
        }

        @Override
        public void unsetDefaultColor() {
            to.unsetDefaultColor();
        }
    }
}
