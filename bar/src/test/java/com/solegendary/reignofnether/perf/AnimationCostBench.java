package com.solegendary.reignofnether.perf;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.solegendary.reignofnether.unit.modelling.animations.Life;
import com.solegendary.reignofnether.unit.modelling.animations.RoyalGuardAnimations;
import com.solegendary.reignofnether.unit.modelling.models.RoyalGuardModel;
import com.solegendary.reignofnether.unit.modelling.models.gen.HalberdierAnim;
import com.solegendary.reignofnether.unit.modelling.models.gen.HalberdierModel;
import com.solegendary.reignofnether.unit.modelling.models.gen.RadiantPaladinModel;
import net.minecraft.client.animation.KeyframeAnimations;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import org.joml.Vector3f;
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
 * The per-frame CPU cost of animating and drawing ~500 units, measured on the real model classes without a window
 * (claude/animation-foundation-audit.md, Lane A's perf proof for the Life layer, Oct 21 2026). Runs in CI's
 * {@code ./gradlew build} like every JUnit test here; its lines go to stdout and to {@code build/anim-bench.txt},
 * which build.yml turns into {@code [AnimBench]} notices.
 *
 * What is timed, per unit, for {@link #UNITS} units a frame:
 * <ul>
 * <li><b>restPose</b> - every part back to the concept's rest (the generated models do this first in setupAnim);</li>
 * <li><b>procedural layer</b> - a transcription of HalberdierUnitModel.setupAnim at a walk with the thrust playing:
 *     the gait, the breath, the tabard, the head's look, the tassel and the keyed thrust. Same bones, same math; the
 *     entity reads (id, target, attackTime) are stand-ins, since no entity can exist without a level;</li>
 * <li><b>Life math</b> - what a creature rig (dragon, bison) costs on top: breath, a wing beat and a 12-link chain
 *     wave per unit (Life.java, #72);</li>
 * <li><b>keyframe layer</b> - vanilla's KeyframeAnimations.animate on the Royal Guard's WALK (the path every
 *     KeyframeHierarchicalModel unit takes);</li>
 * <li><b>body / team / glow pass</b> - the model drawn into a vertex sink (vanilla's ModelPart.compile: the CPU
 *     transform of every vertex), at the close zoom (the "lod" detail parts on) and at the RTS zoom (off), the
 *     team and glow passes restricted to their parts as SplitPasses does in game.</li>
 * </ul>
 * The draw numbers are vanilla's path. Embeddium (in the tester pack) replaces ModelPart.compile with an
 * allocation-free writer and is several times faster there; the animation numbers are the same under both.
 *
 * The only hard gate is a regression gate on the animation side: {@link #ANIM_BUDGET_US_PER_UNIT} per unit for
 * restPose + the procedural layer, ten times what it costs today, so a CI runner's noise never trips it but an
 * O(parts) lookup or an allocation per bone per frame would.
 */
class AnimationCostBench {

    static final int UNITS = 500;
    static final int FRAMES = 30, WARMUP = 10;
    /** restPose + procedural layer, microseconds per unit: a 10x regression trips it, runner noise does not. */
    static final double ANIM_BUDGET_US_PER_UNIT = 40.0;

    /** A vertex sink that only counts: no GL, no buffer. */
    static final class CountingSink implements VertexConsumer {
        long vertices;

        @Override
        public VertexConsumer vertex(double x, double y, double z) {
            vertices++;
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

    /** One timed phase: nanoseconds per frame over the measured frames, median reported. */
    static final class Phase {
        final String name;
        final double[] frameNs = new double[FRAMES];
        long vertices;

        Phase(String name) {
            this.name = name;
        }

        double medianMs() {
            double[] s = frameNs.clone();
            Arrays.sort(s);
            return s[FRAMES / 2] / 1.0e6;
        }

        double perUnitUs() {
            return medianMs() * 1000.0 / UNITS;
        }
    }

    private static final int LIGHT = 0xF000F0;

    @Test
    void fiveHundredUnitsAFrame() throws IOException {
        HalberdierModel<Entity> hal = new HalberdierModel<>(HalberdierModel.createBodyLayer().bakeRoot());
        RadiantPaladinModel<Entity> pal = new RadiantPaladinModel<>(RadiantPaladinModel.createBodyLayer().bakeRoot());
        RoyalGuardModel<Entity> guard = new RoyalGuardModel<>(RoyalGuardModel.createBodyLayer().bakeRoot());
        List<ModelPart> guardParts = guard.root().getAllParts().toList();
        ModelPart[] halBones = {hal.bone("body"), hal.bone("head"), hal.bone("halberd_arm"), hal.bone("halberd"),
            hal.bone("tassel"), hal.bone("left_arm"), hal.bone("right_arm"), hal.bone("right_fore"),
            hal.bone("tabard_front"), hal.bone("tabard_back"), hal.bone("left_thigh"), hal.bone("left_shin"),
            hal.bone("left_foot"), hal.bone("right_thigh"), hal.bone("right_shin"), hal.bone("right_foot")};
        ModelPart[] chain = new ModelPart[12];   // a dragon-class neck + tail, stood in by the paladin's bones
        List<ModelPart> palParts = pal.root().getAllParts().toList();
        for (int k = 0; k < chain.length; k++)
            chain[k] = palParts.get(1 + k);

        Phase rest = new Phase("restPose (Halberdier, 279 parts)");
        Phase proc = new Phase("procedural layer (Halberdier walk + thrust)");
        Phase life = new Phase("Life math (breath + wing beat + 12-link chain)");
        Phase key = new Phase("keyframe layer (Royal Guard WALK, vanilla KeyframeAnimations)");
        Phase bodyClose = new Phase("body pass, close zoom (detail on)");
        Phase bodyRts = new Phase("body pass, RTS zoom (detail off)");
        Phase teamRts = new Phase("team pass, RTS zoom");
        Phase glowRts = new Phase("glow pass, RTS zoom");
        Phase palClose = new Phase("Radiant Paladin body pass, close zoom (505 cubes)");
        Phase palRts = new Phase("Radiant Paladin body pass, RTS zoom");

        CountingSink sink = new CountingSink();
        PoseStack pose = new PoseStack();
        Vector3f cache = new Vector3f();

        for (int frame = -WARMUP; frame < FRAMES; frame++) {
            float age = 1000f + frame * 1.0f;
            long t0, t1;

            // --- animation side ---
            t0 = System.nanoTime();
            for (int i = 0; i < UNITS; i++)
                hal.restPose();
            t1 = System.nanoTime();
            record(rest, frame, t1 - t0);

            t0 = System.nanoTime();
            for (int i = 0; i < UNITS; i++)
                proceduralWalk(halBones, i, age, (i % 5) == 0 ? ((age * 0.1f) % 1f) : 0f);
            t1 = System.nanoTime();
            record(proc, frame, t1 - t0);

            float sinkF = 0f;
            t0 = System.nanoTime();
            for (int i = 0; i < UNITS; i++)
                sinkF += lifeMath(chain, i, age);
            t1 = System.nanoTime();
            record(life, frame, t1 - t0);
            assertTrue(sinkF == sinkF, "the Life layer never returns NaN");

            t0 = System.nanoTime();
            for (int i = 0; i < UNITS; i++) {
                for (int p = 0, n = guardParts.size(); p < n; p++)
                    guardParts.get(p).resetPose();
                KeyframeAnimations.animate(guard, RoyalGuardAnimations.WALK, (long) ((age + i * 37) * 50), 1f, cache);
            }
            t1 = System.nanoTime();
            record(key, frame, t1 - t0);

            // --- draw side (vanilla ModelPart.compile into a counting sink) ---
            hal.setDetail(true);
            drawPass(hal, UNITS, pose, sink, bodyClose, frame);
            hal.setDetail(false);
            drawPass(hal, UNITS, pose, sink, bodyRts, frame);
            hal.drawOnly(hal.teamParts());
            try {
                drawPass(hal, UNITS, pose, sink, teamRts, frame);
            } finally {
                hal.drawAll();
            }
            hal.drawOnly(hal.glowParts());
            try {
                drawPass(hal, UNITS, pose, sink, glowRts, frame);
            } finally {
                hal.drawAll();
            }
            pal.setDetail(true);
            drawPass(pal, UNITS, pose, sink, palClose, frame);
            pal.setDetail(false);
            drawPass(pal, UNITS, pose, sink, palRts, frame);
        }

        List<String> lines = new ArrayList<>();
        double animUs = rest.perUnitUs() + proc.perUnitUs();
        double drawRtsMs = bodyRts.medianMs() + teamRts.medianMs() + glowRts.medianMs();
        double drawCloseMs = bodyClose.medianMs() + teamRts.medianMs() + glowRts.medianMs();
        lines.add(String.format(Locale.ROOT,
            "[AnimBench] %d units a frame, median of %d frames, vanilla ModelPart path, no GPU. Animation side: %s %.2f ms | %s %.2f ms | %s %.2f ms | %s %.2f ms",
            UNITS, FRAMES, rest.name, rest.medianMs(), proc.name, proc.medianMs(), life.name, life.medianMs(),
            key.name, key.medianMs()));
        lines.add(String.format(Locale.ROOT,
            "[AnimBench] Draw side (CPU vertex transform): Halberdier %s %.2f ms (%d verts/unit) | %s %.2f ms (%d verts/unit) | %s %.2f ms (%d) | %s %.2f ms (%d) | %s %.2f ms (%d) | %s %.2f ms (%d)",
            bodyClose.name, bodyClose.medianMs(), bodyClose.vertices / UNITS, bodyRts.name, bodyRts.medianMs(),
            bodyRts.vertices / UNITS, teamRts.name, teamRts.medianMs(), teamRts.vertices / UNITS, glowRts.name,
            glowRts.medianMs(), glowRts.vertices / UNITS, palClose.name, palClose.medianMs(), palClose.vertices / UNITS,
            palRts.name, palRts.medianMs(), palRts.vertices / UNITS));
        lines.add(String.format(Locale.ROOT,
            "[AnimBench] Per unit: animation (restPose + procedural) %.1f us, Life math %.1f us, keyframe %.1f us; draw %.1f us at RTS zoom, %.1f us close. Animation = %.0f%% of a unit's CPU frame cost at RTS zoom, %.0f%% close (budget %.0f us/unit)",
            animUs, life.perUnitUs(), key.perUnitUs(), drawRtsMs * 1000.0 / UNITS, drawCloseMs * 1000.0 / UNITS,
            100.0 * animUs / (animUs + drawRtsMs * 1000.0 / UNITS), 100.0 * animUs / (animUs + drawCloseMs * 1000.0 / UNITS),
            ANIM_BUDGET_US_PER_UNIT));
        for (String line : lines)
            System.out.println(line);
        Path out = Path.of("build", "anim-bench.txt");
        Files.createDirectories(out.getParent());
        Files.write(out, lines);

        assertTrue(animUs < ANIM_BUDGET_US_PER_UNIT, "the animation layers cost " + animUs
            + " us per unit, over the " + ANIM_BUDGET_US_PER_UNIT + " us budget: an O(parts) lookup or an allocation per bone per frame crept in");
        assertTrue(bodyRts.vertices <= bodyClose.vertices, "the RTS zoom draws no more than the close zoom");
    }

    private static void record(Phase phase, int frame, long ns) {
        if (frame >= 0)
            phase.frameNs[frame] = ns;
    }

    private static void drawPass(net.minecraft.client.model.HierarchicalModel<Entity> model, int units, PoseStack pose,
                                 CountingSink sink, Phase phase, int frame) {
        long before = sink.vertices;
        long t0 = System.nanoTime();
        for (int i = 0; i < units; i++) {
            pose.pushPose();
            pose.translate(i * 0.001, 0, 0);
            model.renderToBuffer(pose, sink, LIGHT, 0, 1f, 1f, 1f, 1f);
            pose.popPose();
        }
        long t1 = System.nanoTime();
        if (frame >= 0) {
            phase.frameNs[frame] = t1 - t0;
            phase.vertices = sink.vertices - before;
        }
    }

    /**
     * HalberdierUnitModel.setupAnim's body, line for line, with the entity's reads replaced by the loop's values:
     * bones = {body, head, halberd_arm, halberd, tassel, left_arm, right_arm, right_fore, tabard_front, tabard_back,
     * left_thigh, left_shin, left_foot, right_thigh, right_shin, right_foot}.
     */
    private static void proceduralWalk(ModelPart[] b, int id, float ageInTicks, float attackTime) {
        ModelPart body = b[0], head = b[1], arm = b[2], pole = b[3], tassel = b[4], leftArm = b[5], rArm = b[6],
            rFore = b[7], tabFront = b[8], tabBack = b[9];
        ModelPart[] thigh = {b[10], b[13]}, shin = {b[11], b[14]}, foot = {b[12], b[15]};
        float limbSwing = ageInTicks * 0.9f, limbSwingAmount = 0.8f;
        float netHeadYaw = (id % 60) - 30f, headPitch = (id % 20) - 10f;
        float ready = (id % 3) == 0 ? 1f : 0f;

        float amt = Math.min(1f, limbSwingAmount);
        float w = limbSwing * 0.6662f;
        float t = ageInTicks + (id & 63);
        float breathe = Mth.sin(t * 0.07f);
        float bob = (Math.abs(Mth.cos(w)) - 0.5f) * 0.5f * amt;
        for (int i = 0; i < 2; i++) {
            float ph = w + (i == 0 ? 0f : Mth.PI);
            thigh[i].xRot += (Mth.cos(ph) * 0.35f - 0.14f) * amt;
            thigh[i].y += bob;
            float s = Mth.sin(ph + 0.6f);
            float lift = Math.max(0f, s);
            shin[i].xRot += (s > 0f ? s * 0.95f : -s * 0.16f) * amt;
            foot[i].xRot -= lift * 0.3f * amt;
        }
        float spread = Math.abs(Mth.cos(w)) * amt;
        tabFront.xRot += -spread * 0.35f + 0.02f * Mth.sin(t * 0.08f);
        tabBack.xRot += spread * 0.3f;
        body.y += breathe * 0.08f + bob;
        body.yRot += Mth.sin(w) * 0.05f * amt;
        leftArm.xRot += -Mth.cos(w) * 0.45f * amt + breathe * 0.02f;
        float carry = Mth.cos(w) * 0.12f * amt * (1f - ready);
        arm.xRot += carry;
        pole.xRot -= carry;
        head.yRot += Mth.clamp(netHeadYaw, -40f, 40f) * Mth.DEG_TO_RAD * 0.6f;
        head.xRot += Mth.clamp(headPitch, -25f, 25f) * Mth.DEG_TO_RAD * 0.4f;
        tassel.xRot += 0.06f * Mth.sin(t * 0.12f) + 0.3f * amt;
        tassel.zRot += 0.05f * Mth.sin(t * 0.1f);

        float[] keyP = HalberdierAnim.THRUST_P;
        float[][] keyPole = HalberdierAnim.THRUST_POLE, keyGrip = HalberdierAnim.THRUST_POLE_MOVE;
        float[][] keyArm = HalberdierAnim.THRUST_RARM, keyBody = HalberdierAnim.THRUST_BODY, keyFore = HalberdierAnim.THRUST_RFORE;
        if (attackTime > 0f) {
            float p = attackTime;
            int k = 0;
            while (k < keyP.length - 2 && p > keyP[k + 1])
                k++;
            float f = smooth(Mth.clamp((p - keyP[k]) / (keyP[k + 1] - keyP[k]), 0f, 1f));
            float[] p0 = keyPole[k], p1 = keyPole[k + 1], g0 = keyGrip[k], g1 = keyGrip[k + 1];
            float[] a0 = keyArm[k], a1 = keyArm[k + 1];
            dsl(pole, Mth.lerp(f, p0[0], p1[0]), Mth.lerp(f, p0[1], p1[1]), Mth.lerp(f, p0[2], p1[2]), 1f);
            move(pole, Mth.lerp(f, g0[0], g1[0]), Mth.lerp(f, g0[1], g1[1]), Mth.lerp(f, g0[2], g1[2]), 1f);
            dsl(rArm, Mth.lerp(f, a0[0], a1[0]), Mth.lerp(f, a0[1], a1[1]), Mth.lerp(f, a0[2], a1[2]), 1f);
            dsl(rFore, Mth.lerp(f, keyFore[k][0], keyFore[k + 1][0]), 0, 0, 1f);
            dsl(body, Mth.lerp(f, keyBody[k][0], keyBody[k + 1][0]), 0, 0, 1f);
        } else if (ready > 0f) {
            float bl = smooth(ready);
            dsl(pole, HalberdierAnim.READY_POLE[0], HalberdierAnim.READY_POLE[1], HalberdierAnim.READY_POLE[2], bl);
            move(pole, HalberdierAnim.READY_POLE_MOVE[0], HalberdierAnim.READY_POLE_MOVE[1], HalberdierAnim.READY_POLE_MOVE[2], bl);
            dsl(rArm, HalberdierAnim.READY_RARM[0], HalberdierAnim.READY_RARM[1], HalberdierAnim.READY_RARM[2], bl);
            dsl(rFore, HalberdierAnim.READY_RFORE[0], 0, 0, bl);
            dsl(body, HalberdierAnim.READY_BODY[0], HalberdierAnim.READY_BODY[1], HalberdierAnim.READY_BODY[2], bl);
        }
    }

    /** A creature rig's Life calls for one unit: a breath, a wing beat with its fold, and a 12-link chain wave. */
    private static float lifeMath(ModelPart[] chain, int id, float age) {
        float breath = Life.breath(age, id, 90f);
        float beat = Life.wingBeat(age, id, 24f, 0.6f);
        float fold = Life.upstroke(age, id, 24f, 0.6f);
        chain[0].xRot += breath * Life.deg(3f);
        chain[1].zRot += beat * Life.deg(35f);
        chain[2].zRot += fold * Life.deg(50f);
        for (int k = 0; k < chain.length; k++)
            chain[k].xRot += Life.chain(age, id, 60f, k, 3f) * Life.deg(4f);
        return breath + beat + fold;
    }

    private static void dsl(ModelPart p, float x, float y, float z, float k) {
        if (p == null || k == 0f)
            return;
        p.xRot -= x * Mth.DEG_TO_RAD * k;
        p.yRot += y * Mth.DEG_TO_RAD * k;
        p.zRot -= z * Mth.DEG_TO_RAD * k;
    }

    private static void move(ModelPart p, float x, float y, float z, float k) {
        if (p == null || k == 0f)
            return;
        p.x += x * k;
        p.y -= y * k;
        p.z += z * k;
    }

    private static float smooth(float x) {
        return x * x * (3f - 2f * x);
    }
}
