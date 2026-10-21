package com.solegendary.reignofnether.building;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.solegendary.reignofnether.unit.modelling.mesh.MeshBody;
import com.solegendary.reignofnether.unit.modelling.mesh.MeshCube;
import net.minecraft.client.model.geom.ModelPart;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * What one White Angel statue costs the CPU per frame, LOD by LOD, measured on the real mesh (the .bmesh CI writes
 * from tools/sculpt_angel.py before the build) through the game's own draw path - ModelPart.render into
 * MeshCube.compile, the wings folded with BuildingParts.meshFold as StatueRenderClientEvents folds them - into a
 * vertex sink (lovish, Oct 21 16:13: "try to optimize it all", with before -> after numbers in the PR).
 *
 * Every vertex of every quad is transformed and written each frame on the render thread, so the vertices written per
 * draw are the cost's measure; the microseconds are this runner's, a guide only. Lines go to stdout and to
 * build/statue-bench.txt, which build.yml turns into [StatueBench] notices. Not a gate: when the mesh is missing (a
 * local build without the sculpt) it says so and passes.
 */
class StatueDrawBench {
    static final int WARMUP = 60, DRAWS = 300;

    /** Counts the vertices and keeps a checksum so the JIT cannot drop the work. */
    private static final class Sink implements VertexConsumer {
        long vertices;
        double sum;

        @Override
        public void vertex(float x, float y, float z, float r, float g, float b, float a, float u, float v, int overlay,
                           int light, float nx, float ny, float nz) {
            vertices++;
            sum += x + y + z + r + nx;
        }

        @Override public VertexConsumer vertex(double x, double y, double z) { vertices++; sum += x; return this; }
        @Override public VertexConsumer color(int r, int g, int b, int a) { return this; }
        @Override public VertexConsumer uv(float u, float v) { return this; }
        @Override public VertexConsumer overlayCoords(int u, int v) { return this; }
        @Override public VertexConsumer uv2(int u, int v) { return this; }
        @Override public VertexConsumer normal(float x, float y, float z) { return this; }
        @Override public void endVertex() { }
        @Override public void defaultColor(int r, int g, int b, int a) { }
        @Override public void unsetDefaultColor() { }
    }

    private static long allocatedBytes() {
        java.lang.management.ThreadMXBean mx = ManagementFactory.getThreadMXBean();
        if (mx instanceof com.sun.management.ThreadMXBean sun && sun.isThreadAllocatedMemorySupported())
            return sun.getThreadAllocatedBytes(Thread.currentThread().getId());
        return -1;
    }

    @Test
    void statueDrawCost() throws IOException {
        List<String> lines = new ArrayList<>();
        try (InputStream in = StatueDrawBench.class.getResourceAsStream("/assets/reignofnether/meshes/white_angel_statue.bmesh")) {
            if (in == null) {
                lines.add("[StatueBench] no white_angel_statue.bmesh in the build (CI writes it from tools/sculpt_angel.py)");
                write(lines);
                return;
            }
            MeshBody body = MeshBody.read(in);
            float[] ang = new float[3];
            for (int lod = 0; lod < 3; lod++) {
                ModelPart root = body.buildTree(lod);
                ModelPart b = root.getChild("body");
                ModelPart wl = b.getChild("wing_left"), wr = b.getChild("wing_right");
                ModelPart hl = wl.getChild("wing_left_hand"), hr = wr.getChild("wing_right_hand");
                Sink sink = new Sink();
                PoseStack pose = new PoseStack();
                long t0 = 0, a0 = 0;
                for (int i = 0; i < WARMUP + DRAWS; i++) {
                    if (i == WARMUP) {
                        sink.vertices = 0;
                        a0 = allocatedBytes();
                        t0 = System.nanoTime();
                    }
                    float fold = (i % 70);                      // every fold the levels and the unfolding show
                    BuildingParts.meshFold(false, fold, ang);
                    set(wl, ang, 1f);
                    set(wr, ang, -1f);
                    BuildingParts.meshFold(true, fold, ang);
                    set(hl, ang, 1f);
                    set(hr, ang, -1f);
                    root.render(pose, sink, 15728880, 655360, 1f, 1f, 1f, 1f);
                }
                double us = (System.nanoTime() - t0) / 1e3 / DRAWS;
                long a1 = allocatedBytes();
                long all = sink.vertices / DRAWS;
                // the same draw with MeshCube's back-face culling, from the RTS camera (orthographic, looking down 50
                // degrees from a diagonal) and in first person (perspective, the statue 40 blocks ahead)
                long rts = culled(root, true), fp = culled(root, false);
                lines.add(String.format(Locale.ROOT,
                    "[StatueBench] LOD%d: %d quads, %d vertices written per draw (%d with back-face culling at the RTS camera, %d in first person), %.0f us per draw unculled on this runner, %s bytes allocated per draw (checksum %.0f)",
                    lod, all / 4, all, rts, fp, us, a0 < 0 ? "?" : String.valueOf((a1 - a0) / DRAWS), sink.sum % 1000));
            }
        }
        write(lines);
    }

    /** Vertices one culled draw writes (the wings as the timed loop left them). */
    private static long culled(ModelPart root, boolean ortho) {
        Sink sink = new Sink();
        PoseStack pose = new PoseStack();
        pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(ortho ? 50f : 10f));
        pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(-45f));
        if (!ortho)
            pose.translate(0f, -12f, -40f);
        pose.scale(-1.24f, -1.24f, 1.24f);
        MeshCube.backFaceCulling(true, ortho);
        try {
            root.render(pose, sink, 15728880, 655360, 1f, 1f, 1f, 1f);
        } finally {
            MeshCube.backFaceCulling(false, false);
        }
        return sink.vertices;
    }

    private static void set(ModelPart p, float[] a, float mirror) {
        p.xRot = a[0];
        p.yRot = mirror * a[1];
        p.zRot = mirror * a[2];
    }

    private static void write(List<String> lines) throws IOException {
        lines.forEach(System.out::println);
        Path out = Path.of("build", "statue-bench.txt");
        Files.createDirectories(out.getParent());
        Files.write(out, lines);
    }
}
