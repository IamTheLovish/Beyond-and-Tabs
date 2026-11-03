package com.solegendary.reignofnether.perf;

import com.mojang.blaze3d.vertex.PoseStack;
import com.solegendary.reignofnether.unit.modelling.models.gen.BoneDragonModel;
import com.solegendary.reignofnether.unit.modelling.models.gen.BoneDragonModelMain;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.world.entity.Entity;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What the rounder Bone Dragon (#423) costs to draw, against main's, on ONE JVM (lovish approved it on "a small
 * frame-rate cost, measured before merge"; the eyes overlays runs could not attribute it: two identical runs on
 * different runners differed by 58% in frame time). Both models are drawn {@link #DRAGONS} times a frame into
 * AnimationCostBench's counting sink (vanilla's ModelPart.compile: the CPU transform of every vertex), the old one from
 * a test copy of main's generated class (gen/BoneDragonModelMain), at the close zoom (detail parts on) and the RTS zoom
 * (off). Old and new alternate round by round, so a runner's slow spell lands on both. The lines go to stdout and to
 * {@code build/dragon-bench.txt} ([DragonBench] notices); a measurement only, no gate.
 */
class DragonModelCostBench {
    static final int DRAGONS = 48;
    static final int ROUNDS = 7, WARMUP = 300, FRAMES = 30;
    static final int LIGHT = 0xF000F0;

    @Test
    void roundedBoneDragonDrawCost() throws IOException {
        BoneDragonModel<Entity> now = new BoneDragonModel<>(BoneDragonModel.createBodyLayer().bakeRoot());
        BoneDragonModelMain<Entity> main = new BoneDragonModelMain<>(BoneDragonModelMain.createBodyLayer().bakeRoot());
        AnimationCostBench.CountingSink sink = new AnimationCostBench.CountingSink();
        PoseStack pose = new PoseStack();
        List<String> lines = new ArrayList<>();
        for (boolean detail : new boolean[] { true, false }) {
            now.setDetail(detail);
            main.setDetail(detail);
            // warm both before either is timed
            draw(main, pose, sink, WARMUP);
            draw(now, pose, sink, WARMUP);
            double[] oldUs = new double[ROUNDS], newUs = new double[ROUNDS];
            long oldVerts = 0, newVerts = 0;
            for (int r = 0; r < ROUNDS; r++) {
                long v0 = sink.vertices;
                oldUs[r] = medianFrameUs(main, pose, sink) / DRAGONS;
                oldVerts = (sink.vertices - v0) / ((long) FRAMES * DRAGONS);
                v0 = sink.vertices;
                newUs[r] = medianFrameUs(now, pose, sink) / DRAGONS;
                newVerts = (sink.vertices - v0) / ((long) FRAMES * DRAGONS);
            }
            double[] o = oldUs.clone(), n = newUs.clone();
            Arrays.sort(o);
            Arrays.sort(n);
            double ratio = n[ROUNDS / 2] / o[ROUNDS / 2];
            lines.add(String.format(Locale.ROOT,
                "[DragonBench] Bone Dragon draw, %s zoom: main %.2f us/dragon (min %.2f), #423 %.2f us/dragon (min %.2f),"
                    + " x%.2f; vertices/dragon %d -> %d; %d dragons a frame, median of %d frames, %d alternating rounds"
                    + " (main %s / #423 %s us)",
                detail ? "close" : "RTS", o[ROUNDS / 2], o[0], n[ROUNDS / 2], n[0], ratio, oldVerts, newVerts, DRAGONS,
                FRAMES, ROUNDS, join(oldUs), join(newUs)));
            assertTrue(newVerts > oldVerts, "the rounder model draws more vertices than main's");
        }
        now.setDetail(true);
        main.setDetail(true);
        lines.forEach(System.out::println);
        Path out = Path.of("build", "dragon-bench.txt");
        Files.createDirectories(out.getParent());
        Files.write(out, lines);
    }

    /** The median, over FRAMES frames, of one frame's draw of DRAGONS dragons (microseconds). */
    private static double medianFrameUs(HierarchicalModel<Entity> model, PoseStack pose,
                                        AnimationCostBench.CountingSink sink) {
        long[] ns = new long[FRAMES];
        for (int f = 0; f < FRAMES; f++) {
            long t0 = System.nanoTime();
            draw(model, pose, sink, 1);
            ns[f] = System.nanoTime() - t0;
        }
        Arrays.sort(ns);
        return ns[FRAMES / 2] / 1000.0;
    }

    private static void draw(HierarchicalModel<Entity> model, PoseStack pose, AnimationCostBench.CountingSink sink,
                             int frames) {
        for (int f = 0; f < frames; f++)
            for (int i = 0; i < DRAGONS; i++) {
                pose.pushPose();
                pose.translate(i * 0.001, 0, 0);
                model.renderToBuffer(pose, sink, LIGHT, 0, 1f, 1f, 1f, 1f);
                pose.popPose();
            }
    }

    private static String join(double[] us) {
        StringBuilder b = new StringBuilder();
        for (double u : us)
            b.append(b.length() == 0 ? "" : "/").append(String.format(Locale.ROOT, "%.2f", u));
        return b.toString();
    }
}
