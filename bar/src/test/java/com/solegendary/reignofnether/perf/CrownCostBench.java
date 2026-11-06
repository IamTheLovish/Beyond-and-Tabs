package com.solegendary.reignofnether.perf;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.solegendary.reignofnether.building.CrownMesh;
import com.solegendary.reignofnether.building.CrownMotion;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What the living crowns cost a frame (CrownRenderClientEvents), measured on the real pose code without a window: the
 * Heartwood Hall crown (its 400+ leaf cells from the committed structure, 24 vertices a cell - fancy leaves draw the
 * faces between leaves too, the most a capture can hold) posed by {@link CrownMesh#pose} and written through the
 * renderer's own loop (camera rotation per vertex and normal, colour unpacked) into a sink that only counts. Its line
 * {@code [CrownBench]} gives ns a vertex and ms a frame for the hall and for the Full budget
 * (CrownRenderClientEvents.BUDGET_FULL vertices). Eyes frame times are software GL and not comparable (never-twice
 * ledger: "measure FPS with a same-JVM bench").
 * <p>
 * The gate is a regression gate, ten times today's cost on a CI runner: an allocation or a trig call per vertex trips
 * it, runner noise does not.
 */
class CrownCostBench {

    static final int FRAMES = 400, WARMUP = 200, ROUNDS = 5;
    static final int BUDGET_FULL = 48_000;
    /** ns a vertex (pose + write): ~10x today's. */
    static final double GATE_NS_PER_VERTEX = 400;

    @Test
    void aFrameOfHeartwoodHallsCrown() throws IOException {
        int[][] cells = leafCells(new File("src/main/resources/data/reignofnether/structures/heartwood_hall.nbt"));
        int n = cells[0].length;
        int[] of = new int[n];
        List<CrownMotion.Crown> crowns = CrownMotion.crowns(cells[0], cells[1], cells[2], of);
        int verts = n * 24;
        float[] pos = new float[verts * 3], uv = new float[verts * 2];
        int[] color = new int[verts], light = new int[verts], normal = new int[verts], cell = new int[verts];
        int v = 0;
        for (int k = 0; k < n; k++)
            for (int f = 0; f < 24; f++, v++) {
                pos[v * 3] = cells[0][k] + (f & 1);
                pos[v * 3 + 1] = cells[1][k] + (f >> 1 & 1);
                pos[v * 3 + 2] = cells[2][k] + (f >> 2 & 1);
                color[v] = 0xFFC0C0C0;
                light[v] = 0x00F000F0;
                normal[v] = 127 << 8;
                cell[v] = k;
            }
        float[] born = new float[n];
        Arrays.fill(born, Float.NaN);
        born[0] = 990;                                   // one cell growing in, as during construction
        CrownMesh mesh = new CrownMesh(new int[] { 0, 0, 0 }, cells[0], cells[1], cells[2], of, crowns, born, pos, uv,
            color, light, normal, cell, new int[] { 0 }, new int[] { verts });
        float[] out = new float[verts * 3], gust = new float[crowns.size()];
        Arrays.fill(gust, 0.8f);
        Sink sink = new Sink();
        Matrix4f base = new Matrix4f().rotateX(1.1f).rotateY(0.7f);
        Matrix3f baseN = new Matrix3f().rotateX(1.1f).rotateY(0.7f);
        Vector3f p = new Vector3f(), nr = new Vector3f();

        double best = Double.MAX_VALUE;
        for (int round = 0; round < ROUNDS; round++) {
            for (int frame = 0; frame < WARMUP + FRAMES; frame++) {
                if (frame == WARMUP)
                    sink.t0 = System.nanoTime();
                mesh.pose(-40f, -30f, -40f, frame / 20.0, 1.2, 0.6, gust, 1000 + frame, 1e6f, out);
                for (int i = 0; i < verts; i++) {
                    base.transformPosition(out[i * 3], out[i * 3 + 1], out[i * 3 + 2], p);
                    int nm = normal[i];
                    baseN.transform((byte) nm / 127f, (byte) (nm >> 8) / 127f, (byte) (nm >> 16) / 127f, nr);
                    int c = color[i];
                    sink.vertex(p.x, p.y, p.z, (c & 255) / 255f, (c >> 8 & 255) / 255f, (c >> 16 & 255) / 255f,
                        (c >>> 24) / 255f, uv[i * 2], uv[i * 2 + 1], 0, light[i], nr.x, nr.y, nr.z);
                }
            }
            double ns = (System.nanoTime() - sink.t0) / (double) FRAMES / verts;
            best = Math.min(best, ns);
        }
        String line = String.format(Locale.ROOT, "[CrownBench] Heartwood Hall crown: %d cells, %d vertices; %.1f ns a vertex "
                + "(pose + write, best of %d) = %.3f ms a frame for the hall, %.3f ms for the Full budget (%d vertices)",
            n, verts, best, ROUNDS, best * verts / 1e6, best * BUDGET_FULL / 1e6, BUDGET_FULL);
        System.out.println(line);
        Path file = Path.of("build", "crown-bench.txt");     // build.yml turns it into a [CrownBench] notice
        Files.createDirectories(file.getParent());
        Files.writeString(file, line + System.lineSeparator());
        assertTrue(sink.count > 0 && sink.sum != 0, "the sink saw the vertices");
        assertTrue(best < GATE_NS_PER_VERTEX, line);
    }

    /** Counts and folds the positions in, so the JIT cannot drop the work. */
    static final class Sink implements VertexConsumer {
        long count, t0;
        double sum;

        @Override
        public void vertex(float x, float y, float z, float r, float g, float b, float a, float u, float v, int overlay,
                           int light, float nx, float ny, float nz) {
            count++;
            sum += x + y + z + r + nx;
        }

        @Override
        public VertexConsumer vertex(double x, double y, double z) {
            return this;
        }

        @Override
        public VertexConsumer color(int r, int g, int b, int a) {
            return this;
        }

        @Override
        public VertexConsumer uv(float u, float v) {
            return this;
        }

        @Override
        public VertexConsumer overlayCoords(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer uv2(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer normal(float x, float y, float z) {
            return this;
        }

        @Override
        public void endVertex() {
        }

        @Override
        public void defaultColor(int r, int g, int b, int a) {
        }

        @Override
        public void unsetDefaultColor() {
        }
    }

    private static int[][] leafCells(File file) throws IOException {
        CompoundTag root = NbtIo.readCompressed(file);
        ListTag palette = root.getList("palette", Tag.TAG_COMPOUND);
        ListTag blocks = root.getList("blocks", Tag.TAG_COMPOUND);
        List<int[]> cells = new ArrayList<>();
        for (int i = 0; i < blocks.size(); i++) {
            CompoundTag b = blocks.getCompound(i);
            if (!palette.getCompound(b.getInt("state")).getString("Name").endsWith("_leaves"))
                continue;
            ListTag pos = b.getList("pos", Tag.TAG_INT);
            cells.add(new int[] { pos.getInt(0), pos.getInt(1), pos.getInt(2) });
        }
        int[][] out = new int[3][cells.size()];
        for (int i = 0; i < cells.size(); i++)
            for (int a = 0; a < 3; a++)
                out[a][i] = cells.get(i)[a];
        return out;
    }
}
