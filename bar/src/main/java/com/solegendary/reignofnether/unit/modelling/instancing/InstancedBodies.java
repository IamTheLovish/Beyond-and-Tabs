package com.solegendary.reignofnether.unit.modelling.instancing;

import com.google.common.collect.ImmutableMap;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.mixin.instancing.LightTextureAccessor;
import com.solegendary.reignofnether.mixin.instancing.OverlayTextureAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RegisterShadersEvent;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL31;
import org.lwjgl.opengl.GL33;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.List;

/**
 * The instanced unit bodies' GL side: each rig's vertices in a vertex buffer of their own (uploaded once), each variant's
 * quads as an index buffer, and per frame a texture buffer of the bone matrices and a vertex buffer of per-instance
 * attributes (bone block, colour, light-map and overlay texels: nothing per unit is fetched per vertex) - then one
 * {@code glDrawElementsInstanced} per {@link InstanceBatch.Group}. The render type of a group is set up exactly as the
 * old path's draw sets it up ({@link RenderType#setupRenderState}: its texture with its mipmaps, transparency, cull,
 * light map, overlay, depth and write mask); only the shader program is ours - {@code bat_unit_instanced} (the entity
 * cutout's math after the bone transform) or {@code bat_unit_instanced_eyes} (the eyes'), with the same uniforms set the
 * way the game's own draw sets them.
 * <p>
 * Anything that fails here (a shader that did not compile, a GL error, a driver without texture buffers) switches the
 * path off for the session and says so in the log once: the units go back to the old renderer the next frame.
 */
@OnlyIn(Dist.CLIENT)
public final class InstancedBodies {
    private InstancedBodies() { }

    static final ResourceLocation LIT = ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "bat_unit_instanced");
    static final ResourceLocation EYES = ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "bat_unit_instanced_eyes");
    /** The shader's attributes, in this order (ShaderInstance binds each name to its index). */
    static final VertexFormatElement BONE = new VertexFormatElement(0, VertexFormatElement.Type.INT,
        VertexFormatElement.Usage.GENERIC, 1);
    static final VertexFormatElement TEXEL = new VertexFormatElement(0, VertexFormatElement.Type.UBYTE,
        VertexFormatElement.Usage.GENERIC, 4);
    static final VertexFormat FORMAT = new VertexFormat(ImmutableMap.<String, VertexFormatElement>builder()
        .put("Position", DefaultVertexFormat.ELEMENT_POSITION).put("UV0", DefaultVertexFormat.ELEMENT_UV0)
        .put("Normal", DefaultVertexFormat.ELEMENT_NORMAL).put("Bone", BONE).put("BoneBase", BONE)
        .put("Color", DefaultVertexFormat.ELEMENT_COLOR).put("LightColor", TEXEL).put("OverlayColor", TEXEL).build());
    /** Per vertex: position 3 floats, uv 2, normal 3, bone 1 int. */
    static final int STRIDE = 36;
    /**
     * Per instance (attribute divisor 1): its bone block's first texel (int), its colour, its light-map texel and its
     * overlay texel (4 bytes each, read as normalised bytes like the old path's colour attribute and the RGBA8 textures).
     */
    static final int INSTANCE_STRIDE = 16;
    static final int A_BONE_BASE = 4, A_COLOR = 5, A_LIGHT = 6, A_OVERLAY = 7;
    /** The texture unit of the bone buffer (the entity shaders use 0-2; the game's state tracks 0-11). */
    static final int BONES_UNIT = 10;

    private static ShaderInstance lit, eyes;
    private static boolean failed = false;
    private static String failure = null;
    private static boolean glReady = false;
    private static int maxTexels = 0;
    private static int boneBuffer, boneTexture, instanceBuffer;
    private static FloatBuffer boneUpload = null;
    private static IntBuffer instanceUpload = null;

    /** The GPU objects of one rig. */
    static final class RigGpu {
        int vao, vbo;
    }

    /** Mod bus (ClientModEvents.registerShaders): both programs load with the game's core shaders, F3+T included. */
    public static void registerShaders(RegisterShadersEvent evt) {
        lit = null;
        eyes = null;
        try {
            evt.registerShader(new ShaderInstance(evt.getResourceProvider(), LIT, FORMAT), s -> lit = s);
            evt.registerShader(new ShaderInstance(evt.getResourceProvider(), EYES, FORMAT), s -> eyes = s);
        } catch (Exception e) {
            fail("the instanced unit shaders did not load: " + e);
        }
    }

    /** The path can draw (shaders loaded, nothing failed). */
    public static boolean ready() {
        return !failed && lit != null && eyes != null;
    }

    public static String failure() {
        return failure;
    }

    static void fail(String why) {
        if (!failed)
            ReignOfNether.LOGGER.error("[Instancing] switched off for this session: {}", why);
        failed = true;
        failure = why;
    }

    private static void initGl() {
        if (glReady)
            return;
        maxTexels = GL11.glGetInteger(GL31.GL_MAX_TEXTURE_BUFFER_SIZE);
        boneBuffer = GL15.glGenBuffers();
        boneTexture = GL11.glGenTextures();
        instanceBuffer = GL15.glGenBuffers();
        GL15.glBindBuffer(GL31.GL_TEXTURE_BUFFER, boneBuffer);
        GL15.glBufferData(GL31.GL_TEXTURE_BUFFER, 16L * 4096, GL15.GL_STREAM_DRAW);
        GL15.glBindBuffer(GL31.GL_TEXTURE_BUFFER, 0);
        int was = GlStateManager._getActiveTexture();
        GlStateManager._activeTexture(GL13.GL_TEXTURE0 + BONES_UNIT);
        GL11.glBindTexture(GL31.GL_TEXTURE_BUFFER, boneTexture);
        GL31.glTexBuffer(GL31.GL_TEXTURE_BUFFER, GL30.GL_RGBA32F, boneBuffer);
        GL11.glBindTexture(GL31.GL_TEXTURE_BUFFER, 0);
        GlStateManager._activeTexture(was);
        glReady = true;
        ReignOfNether.LOGGER.info("[Instancing] GL ready: {} texels per texture buffer, renderer {}", maxTexels,
            GL11.glGetString(GL11.GL_RENDERER));
    }

    /** The texels one frame may use (the driver's texture buffer limit). */
    static int maxTexels() {
        return maxTexels;
    }

    private static RigGpu gpu(Rig rig) {
        if (rig.gpu instanceof RigGpu g)
            return g;
        RigGpu g = new RigGpu();
        int verts = rig.quadCount * 4;
        ByteBuffer data = MemoryUtil.memAlloc(Math.max(STRIDE, verts * STRIDE));
        try {
            float[] pos = rig.positions(), nrm = rig.normals(), uv = rig.uvs();
            int[] bone = rig.vertexBones();
            for (int v = 0; v < verts; v++) {
                data.putFloat(pos[v * 3]).putFloat(pos[v * 3 + 1]).putFloat(pos[v * 3 + 2]);
                data.putFloat(uv[v * 2]).putFloat(uv[v * 2 + 1]);
                data.putFloat(nrm[v * 3]).putFloat(nrm[v * 3 + 1]).putFloat(nrm[v * 3 + 2]);
                data.putInt(bone[v]);
            }
            data.flip();
            g.vao = GL30.glGenVertexArrays();
            g.vbo = GL15.glGenBuffers();
            GlStateManager._glBindVertexArray(g.vao);
            GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER, g.vbo);
            GL15.glBufferData(GL15.GL_ARRAY_BUFFER, data, GL15.GL_STATIC_DRAW);
            GL20.glEnableVertexAttribArray(0);
            GL20.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, STRIDE, 0L);
            GL20.glEnableVertexAttribArray(1);
            GL20.glVertexAttribPointer(1, 2, GL11.GL_FLOAT, false, STRIDE, 12L);
            GL20.glEnableVertexAttribArray(2);
            GL20.glVertexAttribPointer(2, 3, GL11.GL_FLOAT, false, STRIDE, 20L);
            GL20.glEnableVertexAttribArray(3);
            GL30.glVertexAttribIPointer(3, 1, GL11.GL_INT, STRIDE, 32L);
            for (int a = A_BONE_BASE; a <= A_OVERLAY; a++) {   // per instance; pointed at the group's slice per draw
                GL20.glEnableVertexAttribArray(a);
                GL33.glVertexAttribDivisor(a, 1);
            }
        } finally {
            MemoryUtil.memFree(data);
        }
        rig.gpu = g;
        return g;
    }

    /** The variant's index buffer (quads as two triangles each, 0 1 2 2 3 0 - the game's quad order); bound into the VAO. */
    private static int indices(Rig.Variant v) {
        if (v.gpu instanceof Integer ebo) {
            GlStateManager._glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, ebo);
            return ebo;
        }
        IntBuffer idx = MemoryUtil.memAllocInt(Math.max(6, v.quads.length * 6));
        try {
            for (int q : v.quads) {
                int b = q * 4;
                idx.put(b).put(b + 1).put(b + 2).put(b + 2).put(b + 3).put(b);
            }
            idx.flip();
            int ebo = GL15.glGenBuffers();
            GlStateManager._glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, ebo);
            GL15.glBufferData(GL15.GL_ELEMENT_ARRAY_BUFFER, idx, GL15.GL_STATIC_DRAW);
            v.gpu = ebo;
            return ebo;
        } finally {
            MemoryUtil.memFree(idx);
        }
    }

    /** Frees a rig that was replaced (its rebuild, or a tree given up on). */
    static void free(Rig rig) {
        if (rig.gpu instanceof RigGpu g) {
            GL30.glDeleteVertexArrays(g.vao);
            GL15.glDeleteBuffers(g.vbo);
        }
        rig.gpu = null;
        for (Rig.Variant v : rig.variants())
            if (v.gpu instanceof Integer ebo) {
                GL15.glDeleteBuffers(ebo);
                v.gpu = null;
            }
    }

    /**
     * Draws the frame's batch. Called on the render thread right after the game's entity batches (AFTER_ENTITIES), with
     * the level's model-view and projection still set. Returns the instances drawn; on any failure the path switches off.
     */
    static int draw(InstanceBatch batch) {
        if (batch.isEmpty() || !ready())
            return 0;
        List<InstanceBatch.Group> groups = batch.build();
        if (groups.isEmpty())
            return 0;
        int drawn = 0;
        RenderType current = null;
        ShaderInstance shader = null;
        try {
            initGl();
            int boneTexels = batch.boneFloats() / 4;
            int instances = 0;
            for (InstanceBatch.Group g : groups)
                instances += g.count;
            if (boneTexels > maxTexels) {
                fail("a frame needs " + boneTexels + " bone texels, the driver allows " + maxTexels);
                return 0;
            }
            // upload: the bones, then the headers (orphaned each frame: the driver never waits on last frame's)
            if (boneUpload == null || boneUpload.capacity() < batch.boneFloats()) {
                if (boneUpload != null)
                    MemoryUtil.memFree(boneUpload);
                boneUpload = MemoryUtil.memAllocFloat(Math.max(batch.boneFloats(), 4096) * 2);
            }
            boneUpload.clear();
            boneUpload.put(batch.bones(), 0, batch.boneFloats()).flip();
            GL15.glBindBuffer(GL31.GL_TEXTURE_BUFFER, boneBuffer);
            GL15.glBufferData(GL31.GL_TEXTURE_BUFFER, boneUpload, GL15.GL_STREAM_DRAW);
            GL15.glBindBuffer(GL31.GL_TEXTURE_BUFFER, 0);
            int instInts = instances * (INSTANCE_STRIDE / 4);
            if (instanceUpload == null || instanceUpload.capacity() < instInts) {
                if (instanceUpload != null)
                    MemoryUtil.memFree(instanceUpload);
                instanceUpload = MemoryUtil.memAllocInt(Math.max(instInts, 4096) * 2);
            }
            instanceUpload.clear();
            fillInstances(batch.headers(), instances, instanceUpload);
            instanceUpload.flip();
            GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER, instanceBuffer);
            GL15.glBufferData(GL15.GL_ARRAY_BUFFER, instanceUpload, GL15.GL_STREAM_DRAW);

            for (int k = 0; k < 16 && GL11.glGetError() != GL11.GL_NO_ERROR; k++)
                ;   // whatever an earlier draw left: the check below is about ours
            for (InstanceBatch.Group g : groups) {
                RenderType type = (RenderType) g.type;
                if (type != current) {
                    if (current != null) {
                        shader.clear();
                        current.clearRenderState();
                    }
                    current = type;
                    type.setupRenderState();
                    shader = g.kind == InstanceBatch.KIND_EYES ? eyes : lit;
                    apply(shader);
                }
                RigGpu rg = gpu(g.rig);
                GlStateManager._glBindVertexArray(rg.vao);
                indices(g.variant);
                pointInstances(g.first);
                GL31.glDrawElementsInstanced(GL11.GL_TRIANGLES, g.variant.quads.length * 6, GL11.GL_UNSIGNED_INT, 0L,
                    g.count);
                drawn += g.count;
            }
            int err = GL11.glGetError();
            if (err != GL11.GL_NO_ERROR)
                fail("GL error 0x" + Integer.toHexString(err) + " after the instanced draw");
        } catch (RuntimeException e) {
            fail("the instanced draw threw " + e);
            ReignOfNether.LOGGER.error("[Instancing] draw failed", e);
        } finally {
            if (current != null) {
                if (shader != null)
                    shader.clear();
                current.clearRenderState();
            }
            unbindBuffers();
            GlStateManager._glBindVertexArray(0);
            BufferUploader.invalidate();
        }
        return drawn;
    }

    /**
     * The instances' attribute records, from the batch's headers (bone texel, light, overlay, colour): the light and
     * overlay become the texels the old path's shader would fetch, read from the images the game uploads (the light
     * map's every frame, the overlay's once) - the same bytes, so the same colours.
     */
    private static void fillInstances(int[] headers, int instances, IntBuffer out) {
        Minecraft mc = Minecraft.getInstance();
        NativeImage light = ((LightTextureAccessor) mc.gameRenderer.lightTexture()).bat$lightPixels();
        NativeImage overlay = ((OverlayTextureAccessor) mc.gameRenderer.overlayTexture()).bat$texture().getPixels();
        for (int k = 0; k < instances; k++) {
            int h = k * InstanceBatch.HEADER;
            int l = headers[h + 1], o = headers[h + 2];
            out.put(headers[h]).put(headers[h + 3])
                .put(texel(light, (l & 0xFFFF) / 16, ((l >>> 16) & 0xFFFF) / 16))
                .put(texel(overlay, o & 0xFFFF, (o >>> 16) & 0xFFFF));
        }
    }

    /** A texel as texelFetch reads it (RGBA bytes, r lowest), clamped into the image. */
    private static int texel(NativeImage img, int x, int y) {
        if (img == null)
            return -1;
        return img.getPixelRGBA(Math.max(0, Math.min(img.getWidth() - 1, x)), Math.max(0, Math.min(img.getHeight() - 1, y)));
    }

    /** Points the bound rig's per-instance attributes at the group's slice of the instance buffer. */
    private static void pointInstances(int first) {
        GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER, instanceBuffer);
        long at = (long) first * INSTANCE_STRIDE;
        GL30.glVertexAttribIPointer(A_BONE_BASE, 1, GL11.GL_INT, INSTANCE_STRIDE, at);
        GL20.glVertexAttribPointer(A_COLOR, 4, GL11.GL_UNSIGNED_BYTE, true, INSTANCE_STRIDE, at + 4);
        GL20.glVertexAttribPointer(A_LIGHT, 4, GL11.GL_UNSIGNED_BYTE, true, INSTANCE_STRIDE, at + 8);
        GL20.glVertexAttribPointer(A_OVERLAY, 4, GL11.GL_UNSIGNED_BYTE, true, INSTANCE_STRIDE, at + 12);
    }

    /** Sets the shader's uniforms the way the game's own draw does, binds it and the bone buffer. */
    private static void apply(ShaderInstance shader) {
        for (int i = 0; i < 12; i++)
            shader.setSampler("Sampler" + i, RenderSystem.getShaderTexture(i));
        if (shader.MODEL_VIEW_MATRIX != null)
            shader.MODEL_VIEW_MATRIX.set(RenderSystem.getModelViewMatrix());
        Uniform viewProj = shader.getUniform("BatViewProj");
        if (viewProj != null)
            viewProj.set(new Matrix4f(RenderSystem.getProjectionMatrix()).mul(RenderSystem.getModelViewMatrix()));
        if (shader.INVERSE_VIEW_ROTATION_MATRIX != null)
            shader.INVERSE_VIEW_ROTATION_MATRIX.set(RenderSystem.getInverseViewRotationMatrix());
        if (shader.COLOR_MODULATOR != null)
            shader.COLOR_MODULATOR.set(RenderSystem.getShaderColor());
        if (shader.FOG_START != null)
            shader.FOG_START.set(RenderSystem.getShaderFogStart());
        if (shader.FOG_END != null)
            shader.FOG_END.set(RenderSystem.getShaderFogEnd());
        if (shader.FOG_COLOR != null)
            shader.FOG_COLOR.set(RenderSystem.getShaderFogColor());
        if (shader.FOG_SHAPE != null)
            shader.FOG_SHAPE.set(RenderSystem.getShaderFogShape().getIndex());
        RenderSystem.setupShaderLights(shader);
        shader.apply();
        int program = shader.getId();
        int was = GlStateManager._getActiveTexture();
        GlStateManager._activeTexture(GL13.GL_TEXTURE0 + BONES_UNIT);
        GL11.glBindTexture(GL31.GL_TEXTURE_BUFFER, boneTexture);
        GlStateManager._activeTexture(was);
        Uniform.uploadInteger(Uniform.glGetUniformLocation(program, "Bones"), BONES_UNIT);
    }

    private static void unbindBuffers() {
        if (!glReady)
            return;
        int was = GlStateManager._getActiveTexture();
        GlStateManager._activeTexture(GL13.GL_TEXTURE0 + BONES_UNIT);
        GL11.glBindTexture(GL31.GL_TEXTURE_BUFFER, 0);
        GlStateManager._activeTexture(was);
    }

    /** Is a window open with a GL context (the eyes scene and the toggles ask before switching the path on). */
    static boolean hasContext() {
        return Minecraft.getInstance().getWindow() != null;
    }
}
