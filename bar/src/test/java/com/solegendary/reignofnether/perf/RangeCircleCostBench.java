package com.solegendary.reignofnether.perf;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.solegendary.reignofnether.blocks.RangeCircleMesh;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The range circles' per-frame CPU cost, old way against new, in one JVM (lovish's Oct 29 game: markersRange 1.9-2.1 ms
 * of every late-game frame). {@link #CIRCLES} circles of radius {@link #RADIUS} (a defence tower's ring, three blocks
 * thick as MiscUtil.getRangeIndicatorCircleBlocks makes it), drawn into a vertex sink that only counts:
 * <ul>
 * <li><b>old</b> - MyRenderer.drawBlockFace per face, as BlockClientEvents called it: the texture's name parsed
 *     anew, two boxes, a pose push / translate / pop and the face. What the bench cannot run without a game is left
 *     out, so the real old path was dearer still: a block-state read per face for the snow, and a translucent-buffer
 *     fetch (a render-type lookup) per face through the buffer source;</li>
 * <li><b>new</b> - each circle's {@link RangeCircleMesh} (built once, as BlockClientEvents keeps it for
 *     {@link RangeCircleMesh#REFRESH_TICKS} ticks) emitted through one pose.</li>
 * </ul>
 * The sink adds up every coordinate it is given, so the JIT cannot drop the work (a sink that only counted let it fold
 * the whole loop away: "0.000 ms" on the first CI run). The view pose is pushed once per frame in the game, outside
 * the timed loop here. Gates: the new draw at most half the old one (best round each), allocation-free, and the same
 * vertices. Lines go to stdout and build/range-circle-bench.txt, which build.yml turns into notices.
 */
class RangeCircleCostBench {

    static final int CIRCLES = 15;
    static final int RADIUS = 20;
    static final int ROUNDS = 7, WARMUP = 300, FRAMES = 30;
    static final long PAUSE_MS = 15;

    /** A ring of top cells round {@code c}, three cells thick (x, x+1, z+1), like getRangeIndicatorCircleBlocks. */
    static List<BlockPos> ring(BlockPos c, int radius) {
        Set<BlockPos> out = new LinkedHashSet<>();
        for (int i = 0; i < 8 * radius; i++) {
            double ang = Math.PI * 2 * i / (8 * radius);
            int x = c.getX() + (int) Math.round(Math.cos(ang) * radius), z = c.getZ() + (int) Math.round(Math.sin(ang) * radius);
            out.add(new BlockPos(x, c.getY(), z));
            out.add(new BlockPos(x + 1, c.getY(), z));
            out.add(new BlockPos(x, c.getY(), z + 1));
        }
        return new ArrayList<>(out);
    }

    static long blackhole;

    /** A vertex sink that keeps a sum of every position it is given (no GL, no buffer), so nothing is dead code. */
    static final class SummingSink implements VertexConsumer {
        long vertices;
        double sum;

        @Override public VertexConsumer vertex(double x, double y, double z) { vertices++; sum += x + y + z; return this; }
        @Override public VertexConsumer color(int r, int g, int b, int a) { sum += a; return this; }
        @Override public VertexConsumer uv(float u, float v) { return this; }
        @Override public VertexConsumer overlayCoords(int u, int v) { return this; }
        @Override public VertexConsumer uv2(int u, int v) { return this; }
        @Override public VertexConsumer normal(float x, float y, float z) { sum += y; return this; }
        @Override public void endVertex() { }
        @Override public void defaultColor(int r, int g, int b, int a) { }
        @Override public void unsetDefaultColor() { }
    }

    /** MyRenderer.drawBlockFace(..., Direction.UP, yOffset, bp, ...) -> drawSolidBox, without the buffer fetch. */
    static void oldFace(PoseStack ps, VertexConsumer sink, BlockPos bp, float yOffset, double cx, double cy, double cz,
                        float r, float g, float b, float a) {
        AABB aabb = new AABB(bp);
        aabb = aabb.setMaxY(aabb.maxY + 0.01f);
        ResourceLocation rl = ResourceLocation.parse("forge:textures/white.png");
        blackhole += rl.hashCode();
        ps.pushPose();
        ps.translate(-cx, -cy, -cz);
        Matrix4f m = ps.last().pose();
        Matrix3f n = ps.last().normal();
        RangeCircleMesh.face(m, n, sink, (float) aabb.minX, (float) aabb.maxY + yOffset, (float) aabb.minZ, r, g, b, a);
        ps.popPose();
    }

    @Test
    void cachedCirclesDrawAtLeastTwiceAsFastWithoutAllocating() throws IOException {
        List<List<BlockPos>> circles = new ArrayList<>();
        List<RangeCircleMesh> meshes = new ArrayList<>();
        int faces = 0;
        for (int c = 0; c < CIRCLES; c++) {
            List<BlockPos> bps = ring(new BlockPos(1000 + 60 * c, 70, -2000 + 37 * c), RADIUS);
            circles.add(bps);
            meshes.add(new RangeCircleMesh(bps, bp -> (bp.getX() & 7) == 0 ? 2 : 0, bps, 0));
            faces += bps.size();
        }
        double cx = 1400.5, cy = 140.62, cz = -1750.5;
        SummingSink oldSink = new SummingSink();
        SummingSink newSink = new SummingSink();
        PoseStack oldPs = new PoseStack(), newPs = new PoseStack();
        oldPs.mulPose(new org.joml.Quaternionf().rotateX(1.1f));
        newPs.mulPose(new org.joml.Quaternionf().rotateX(1.1f));

        BenchRounds.Result old = BenchRounds.measure(ROUNDS, WARMUP, FRAMES, PAUSE_MS, (round, frame) -> {
            long t0 = System.nanoTime();
            for (List<BlockPos> bps : circles)
                for (BlockPos bp : bps)
                    oldFace(oldPs, oldSink, bp, (bp.getX() & 7) == 0 ? 0.25f : 0f, cx, cy, cz, 0f, 0f, 0f, 0.6f);
            return System.nanoTime() - t0;
        });
        newPs.translate(-cx, -cy, -cz);
        Matrix4f m = newPs.last().pose();
        Matrix3f n = newPs.last().normal();
        BenchRounds.Result now = BenchRounds.measure(ROUNDS, WARMUP, FRAMES, PAUSE_MS, (round, frame) -> {
            long t0 = System.nanoTime();
            for (RangeCircleMesh mesh : meshes)
                mesh.emit(m, n, newSink, 0f, 0f, 0f, 0.6f);
            return System.nanoTime() - t0;
        });

        String line = String.format(Locale.ROOT,
            "[RangeBench] %d circles, %d faces/frame: old drawBlockFace path %.4f ms (%s), cached mesh %.4f ms (%s, %s) - x%.1f",
            CIRCLES, faces, old.bestMs(), old.roundsText(), now.bestMs(), now.roundsText(), now.bytesText(),
            old.bestMs() / Math.max(now.bestMs(), 1e-6));
        assertTrue(oldSink.sum != 0 && newSink.sum != 0, "the sinks saw the vertices: " + line);
        System.out.println(line);
        Path out = Path.of("build", "range-circle-bench.txt");
        Files.createDirectories(out.getParent());
        Files.writeString(out, line + System.lineSeparator());

        assertEquals(oldSink.vertices, newSink.vertices, "the same faces: " + line);
        assertTrue(now.allocationFree(), "the cached draw allocates: " + line);
        assertTrue(now.bestMs() * 2 <= old.bestMs(), "the cached draw is not twice as fast: " + line);
    }

    @Test
    void theMeshHasDrawBlockFacesHeights() {
        List<BlockPos> bps = List.of(new BlockPos(-5, 64, 9), new BlockPos(3, -12, 4));
        RangeCircleMesh mesh = new RangeCircleMesh(bps, bp -> bp.getY() < 0 ? 3 : 0, bps, 10);
        assertEquals(2, mesh.faces);
        assertEquals((float) (65 + (double) 0.01f), mesh.y[0], 0f, "0.01 over the block, as drawBlockFace's box");
        assertEquals((float) (-11 + (double) 0.01f) + 3 * 0.125f, mesh.y[1], 0f, "plus 1/8 per snow layer");
        assertEquals(-5f, mesh.bounds.minX, 0);
        assertEquals(10f, mesh.bounds.maxZ, 1e-6, "bounds reach the far edge of the farthest block (9 + 1)");
        assertTrue(mesh.fresh(bps, 2, 10 + RangeCircleMesh.REFRESH_TICKS - 1));
        assertTrue(!mesh.fresh(bps, 2, 10 + RangeCircleMesh.REFRESH_TICKS), "rebuilt after a refresh");
        assertTrue(!mesh.fresh(bps, 3, 11), "a set that changed size");
        assertTrue(!mesh.fresh(new ArrayList<>(bps), 2, 11), "another set");
    }
}
