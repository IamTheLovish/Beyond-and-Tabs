package com.solegendary.reignofnether.unit.modelling.mesh;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.solegendary.reignofnether.unit.modelling.models.gen.RadiantPaladinModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.Entity;
import org.joml.Quaternionf;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Mesh bodies step 1, the no-new-art gate (inbox/spec-1-mesh-bodies-2026-10-17.md): the Paladin's .bmesh
 * (tools/mesh_export.py --from-dsl, which CI runs before the build) must draw EXACTLY what its cube model draws - every
 * vertex of every pass, bit for bit and in the same order - before any sculpted body may replace a cube body. If this
 * fails, the vertex emission (MeshCube) or the tree (MeshBody.buildTree) is wrong, and no picture would show it as
 * clearly. And the reader must refuse a broken file loudly instead of drawing garbage.
 */
class MeshBodyTest {
    private static final String PALADIN = "/assets/reignofnether/meshes/radiant_paladin_unit.bmesh";
    private static final int LIGHT = 15728880, OVERLAY = 655360;   // full-bright; OverlayTexture.NO_OVERLAY

    // ------------------------------------------------------------------------------------- a small .bmesh writer --

    /** Writes the format tools/mesh_export.py writes (little-endian), for bodies made up here. */
    private static final class Writer {
        private final ByteArrayOutputStream out = new ByteArrayOutputStream();

        Writer bytes(byte[] b) {
            out.writeBytes(b);
            return this;
        }

        Writer u8(int v) {
            out.write(v);
            return this;
        }

        Writer u16(int v) {
            return put(ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort((short) v));
        }

        Writer i32(int v) {
            return put(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(v));
        }

        Writer f32(float... vs) {
            for (float v : vs)
                put(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putFloat(v));
            return this;
        }

        private Writer put(ByteBuffer b) {
            out.writeBytes(b.array());
            return this;
        }

        Writer header(int parts) {
            return bytes("BMSH".getBytes(StandardCharsets.US_ASCII)).u16(1).u16(256).u16(128).u16(parts);
        }

        Writer part(String name, int parent, float... pose) {
            byte[] n = name.getBytes(StandardCharsets.UTF_8);
            return u8(n.length).bytes(n).u16(parent).f32(pose);
        }

        /** One LOD: vertices as {x, y, z, nx, ny, nz, u, v}, quads as 4 indices each. */
        Writer lod(float[][] vertices, int[][] quads) {
            i32(vertices.length);
            float[] min = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE};
            float[] max = {-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
            for (float[] v : vertices) {
                f32(v);
                for (int k = 0; k < 3; k++) {
                    min[k] = Math.min(min[k], v[k]);
                    max[k] = Math.max(max[k], v[k]);
                }
            }
            i32(quads.length);
            for (int[] q : quads)
                for (int i : q)
                    i32(i);
            return vertices.length == 0 ? f32(0, 0, 0, 0, 0, 0) : f32(min[0], min[1], min[2], max[0], max[1], max[2]);
        }

        byte[] done() {
            return out.toByteArray();
        }
    }

    private static final float[][] SQUARE = {
        {0, 0, 0, 0, 0, -1, 0.00f, 0.00f}, {4, 0, 0, 0, 0, -1, 0.25f, 0.00f},
        {4, 4, 0, 0, 0, -1, 0.25f, 0.50f}, {0, 4, 0, 0, 0, -1, 0.00f, 0.50f},
        {1, 1, 1, 0.6f, 0.8f, 0, 0.50f, 0.50f}, {3, 1, 2, 0.6f, 0.8f, 0, 0.75f, 0.50f}, {2, 3, 1, 0, 1, 0, 0.6f, 1.0f}};
    private static final int[][] SQUARE_AND_TRIANGLE = {{0, 1, 2, 3}, {4, 5, 6, 6}};

    /** body (a square and a triangle) > arm (two LODs: a square, then nothing); empty on the root. */
    private static byte[] smallBody() {
        return new Writer().header(3)
            .part("body", -1, 0f, 0.3f, 0f, 0f, 0f, 0f).u8(1).lod(SQUARE, SQUARE_AND_TRIANGLE)
            .part("arm", 0, -5f, 2f, 0f, 0.5f, -0.25f, 0.125f).u8(2)
            .lod(Arrays.copyOf(SQUARE, 4), new int[][] {{0, 1, 2, 3}}).lod(new float[0][], new int[0][])
            .part("empty", -1, 0f, 0f, 0f, 0f, 0f, 0f).u8(1).lod(new float[0][], new int[0][])
            .done();
    }

    // ----------------------------------------------------------------------------- a vertex-stream recorder --

    /** Every argument of every vertex(...) call, floats as raw bits. Cube and MeshCube call nothing else. */
    static final class Recorder implements VertexConsumer {
        private int[] data = new int[1 << 16];
        private int size = 0;

        int vertices() {
            return size / 14;
        }

        int[] stream() {
            return Arrays.copyOf(data, size);
        }

        private void add(int v) {
            if (size == data.length)
                data = Arrays.copyOf(data, size * 2);
            data[size++] = v;
        }

        @Override
        public void vertex(float x, float y, float z, float red, float green, float blue, float alpha, float u, float v,
                           int overlay, int light, float nx, float ny, float nz) {
            for (float f : new float[] {x, y, z, red, green, blue, alpha, u, v})
                add(Float.floatToRawIntBits(f));
            add(overlay);
            add(light);
            add(Float.floatToRawIntBits(nx));
            add(Float.floatToRawIntBits(ny));
            add(Float.floatToRawIntBits(nz));
        }

        private static AssertionError unexpected() {
            return new AssertionError("a cube wrote through a VertexConsumer method other than vertex(14 args)");
        }

        @Override
        public VertexConsumer vertex(double x, double y, double z) {
            throw unexpected();
        }

        @Override
        public VertexConsumer color(int r, int g, int b, int a) {
            throw unexpected();
        }

        @Override
        public VertexConsumer uv(float u, float v) {
            throw unexpected();
        }

        @Override
        public VertexConsumer overlayCoords(int u, int v) {
            throw unexpected();
        }

        @Override
        public VertexConsumer uv2(int u, int v) {
            throw unexpected();
        }

        @Override
        public VertexConsumer normal(float x, float y, float z) {
            throw unexpected();
        }

        @Override
        public void endVertex() {
            throw unexpected();
        }

        @Override
        public void defaultColor(int r, int g, int b, int a) {
            throw unexpected();
        }

        @Override
        public void unsetDefaultColor() {
            throw unexpected();
        }
    }

    /** A pose stack like a unit's in the world: placed, turned, the renderer's scale and LivingEntityRenderer's flip. */
    private static PoseStack worldPose() {
        PoseStack pose = new PoseStack();
        pose.translate(12.37f, 64.5f, -7.25f);
        pose.mulPose(new Quaternionf().rotationY(2.31f));
        pose.scale(0.92f, 0.92f, 0.92f);
        pose.scale(-1f, -1f, 1f);
        pose.translate(0f, -1.501f, 0f);
        return pose;
    }

    private static Recorder draw(ModelPart root) {
        Recorder r = new Recorder();
        root.render(worldPose(), r, LIGHT, OVERLAY, 1f, 0.875f, 0.75f, 1f);
        return r;
    }

    // ---------------------------------------------------------------------------------------------- the reader --

    @Test
    void readsWhatWasWritten() throws IOException {
        MeshBody body = MeshBody.read(smallBody());
        assertEquals(256, body.atlasWidth);
        assertEquals(128, body.atlasHeight);
        assertEquals(3, body.partCount());
        MeshBody.Part arm = body.part(1);
        assertEquals("arm", arm.name);
        assertEquals(0, arm.parent);
        assertEquals(2, arm.lodCount());
        assertEquals(-5f, arm.x);
        assertEquals(0.125f, arm.zRot);
        MeshBody.Lod top = body.part(0).lod(0);
        assertEquals(7, top.vertexCount());
        assertEquals(2, top.quadCount());
        assertEquals(4f, top.maxX);
        assertEquals(2f, top.maxZ);
        assertArrayEquals(new float[] {0.6f, 0.8f, 0f}, Arrays.copyOfRange(top.nrm, 12, 15));
        assertEquals(4, body.part(1).lod(0).vertexCount());
        assertEquals(0, body.part(1).lod(1).vertexCount());
        assertEquals(0, body.part(1).lod(9).vertexCount(), "a LOD past the last is the last");
    }

    @Test
    void buildsTheTree() throws IOException {
        MeshBody body = MeshBody.read(smallBody());
        ModelPart root = body.buildTree(0);
        ModelPart top = root.getChild("body"), arm = top.getChild("arm"), empty = root.getChild("empty");
        assertEquals(0.3f, top.y);
        assertEquals(0.3f, top.getInitialPose().y, "the rest pose is the initial pose (resetPose returns to it)");
        assertEquals(0.5f, arm.xRot);
        assertFalse(top.isEmpty());
        assertFalse(arm.isEmpty());
        assertTrue(empty.isEmpty(), "a part without vertices has no cube, as vanilla's empty parts");
        assertInstanceOf(MeshCube.class, top.getRandomCube(net.minecraft.util.RandomSource.create(1)));
        // 4 + 4 vertices for body (the triangle written a b c c), 4 for the arm at LOD0
        assertEquals(12, draw(root).vertices());
        assertEquals(8, draw(body.buildTree(1)).vertices(), "the arm has nothing at LOD1");
        // the triangle's fourth corner repeats its third: Iris takes a quad's tangent from corners 0, 1, 2
        int[] s = draw(root).stream();
        int third = 6 * 14, fourth = 7 * 14;
        assertArrayEquals(Arrays.copyOfRange(s, third, third + 14), Arrays.copyOfRange(s, fourth, fourth + 14));
    }

    @Test
    void refusesBrokenFiles() {
        byte[] good = smallBody();
        Consumer<byte[]> refused = b -> assertThrows(IOException.class, () -> MeshBody.read(b));
        byte[] magic = good.clone();
        magic[0] = 'X';
        refused.accept(magic);
        byte[] version = good.clone();
        version[4] = 2;
        refused.accept(version);
        refused.accept(Arrays.copyOf(good, good.length - 5));         // truncated
        refused.accept(Arrays.copyOf(good, good.length + 1));         // a byte too many
        refused.accept(new Writer().header(1).part("p", 0, 0, 0, 0, 0, 0, 0).u8(1)
            .lod(new float[0][], new int[0][]).done());                // parent is not an earlier part
        refused.accept(new Writer().header(1).part("p", -1, 0, 0, 0, 0, 0, 0).u8(1)
            .lod(Arrays.copyOf(SQUARE, 4), new int[][] {{0, 1, 2, 4}}).done());   // a corner past the vertices
        refused.accept(new Writer().header(2).part("p", -1, 0, 0, 0, 0, 0, 0).u8(1).lod(new float[0][], new int[0][])
            .part("p", -1, 0, 0, 0, 0, 0, 0).u8(1).lod(new float[0][], new int[0][]).done());   // two siblings, one name
        refused.accept(new Writer().header(1).part("p", -1, 0, 0, 0, 0, 0, 0).u8(0).done());     // no LOD
    }

    // ------------------------------------------------------------------------------------------- the Paladin --

    private static MeshBody paladin() throws IOException {
        try (InputStream in = MeshBodyTest.class.getResourceAsStream(PALADIN)) {
            if (in == null)
                fail(PALADIN + " is missing: run python3 tools/mesh_export.py --from-dsl "
                    + "design/models/sunforged_paladin_radiant_winged.json (CI runs it before the build)");
            return MeshBody.read(in);
        }
    }

    /** ModelPart.children, for comparing the order parts draw in (vanilla gives no accessor). */
    @SuppressWarnings("unchecked")
    private static Map<String, ModelPart> children(ModelPart p) {
        try {
            Field f = ModelPart.class.getDeclaredField("children");
            f.setAccessible(true);
            return (Map<String, ModelPart>) f.get(p);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("ModelPart.children not found - the test needs updating for this Minecraft", e);
        }
    }

    private static void sameShape(String path, ModelPart cube, ModelPart mesh) {
        assertEquals(cube.isEmpty(), mesh.isEmpty(), path + ": has cubes");
        float[] a = {cube.x, cube.y, cube.z, cube.xRot, cube.yRot, cube.zRot};
        float[] b = {mesh.x, mesh.y, mesh.z, mesh.xRot, mesh.yRot, mesh.zRot};
        for (int k = 0; k < 6; k++)
            assertEquals(Float.floatToRawIntBits(a[k]), Float.floatToRawIntBits(b[k]), path + ": pose value " + k);
        List<String> ca = new ArrayList<>(children(cube).keySet()), cb = new ArrayList<>(children(mesh).keySet());
        assertEquals(ca, cb, path + ": children, in drawing order");
        for (String c : ca)
            sameShape(path + "/" + c, cube.getChild(c), mesh.getChild(c));
    }

    @Test
    void paladinTreeIsTheCubeTree() throws IOException {
        sameShape("root", RadiantPaladinModel.createBodyLayer().bakeRoot(), paladin().buildTree(0));
    }

    private interface Setup {
        void apply(RadiantPaladinModel<Entity> model);
    }

    /** Animation-like motion on every bone, the same on both bodies. */
    private static void move(RadiantPaladinModel<Entity> m) {
        for (int k = 0; k < RadiantPaladinModel.BONES.size(); k++) {
            ModelPart p = m.bone(RadiantPaladinModel.BONES.get(k));
            if (p == null)
                continue;
            p.xRot += 0.11f * (k + 1);
            p.yRot -= 0.07f * k;
            p.zRot += 0.05f;
            p.y += 0.3f * (k % 3);
        }
    }

    @Test
    void paladinMeshDrawsTheCubeBodyBitForBit() throws IOException {
        RadiantPaladinModel<Entity> cube = new RadiantPaladinModel<>(RadiantPaladinModel.createBodyLayer().bakeRoot());
        RadiantPaladinModel<Entity> mesh = new RadiantPaladinModel<>(paladin().buildTree(0));
        assertEquals(cube.lodParts.size(), mesh.lodParts.size());
        assertEquals(cube.teamParts().size(), mesh.teamParts().size());
        assertEquals(cube.glowParts().size(), mesh.glowParts().size());

        Map<String, Setup> passes = new java.util.LinkedHashMap<>();
        passes.put("rest, everything shown", m -> m.restPose());
        passes.put("far: detail off, upgrade off", m -> {
            m.restPose();
            m.setDetail(false);
            m.setUpgrade("sun_ward", false);
        });
        passes.put("moving", m -> {
            m.restPose();
            m.setDetail(true);
            m.setUpgrade("sun_ward", true);
            move(m);
        });
        passes.put("team pass (SplitPasses)", m -> {
            m.restPose();
            move(m);
            m.drawOnly(m.teamParts());
        });
        passes.put("glow pass (SplitPasses)", m -> {
            m.restPose();
            move(m);
            m.drawOnly(m.glowParts());
        });

        for (Map.Entry<String, Setup> pass : passes.entrySet()) {
            pass.getValue().apply(cube);
            pass.getValue().apply(mesh);
            Recorder a = draw(cube.root()), b = draw(mesh.root());
            cube.drawAll();
            mesh.drawAll();
            String name = pass.getKey();
            assertTrue(a.vertices() > 0 || name.startsWith("glow"), name + ": the cube body drew nothing");
            assertEquals(a.vertices(), b.vertices(), name + ": vertices drawn");
            int[] sa = a.stream(), sb = b.stream();
            for (int i = 0; i < sa.length; i++) {
                if (sa[i] != sb[i]) {
                    int v = i / 14, field = i % 14;
                    fail(String.format("%s: vertex %d of %d differs in argument %d of vertex(...): cube %s, mesh %s",
                        name, v, a.vertices(), field, show(sa[i], field), show(sb[i], field)));
                }
            }
        }
        // the whole body, everything shown: 505 cubes x 6 faces x 4 corners (tools/body_budget.py's 12,120)
        cube.restPose();
        assertEquals(505 * 24, draw(cube.root()).vertices(), "the cube Paladin's own count changed: re-check the gate");
        assertNotNull(paladin());
    }

    private static String show(int bits, int field) {
        return field == 9 || field == 10 ? Integer.toString(bits) : Float.intBitsToFloat(bits) + " (0x" + Integer.toHexString(bits) + ")";
    }
}
