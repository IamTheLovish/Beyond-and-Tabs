package com.solegendary.reignofnether.unit.modelling.instancing;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.solegendary.reignofnether.unit.modelling.models.RoyalGuardModel;
import com.solegendary.reignofnether.unit.modelling.models.SplitPasses;
import com.solegendary.reignofnether.unit.modelling.models.gen.BoneDragonModel;
import com.solegendary.reignofnether.unit.modelling.models.gen.HalberdierModel;
import com.solegendary.reignofnether.unit.modelling.models.gen.RadiantPaladinModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.world.entity.Entity;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * The instanced unit bodies' CPU side against vanilla's (big-army performance): for real models - three generated
 * bodies (Halberdier, Radiant Paladin, Bone Dragon), a hand-made keyframe body (Royal Guard) and vanilla's humanoid -
 * the vertices a pass draws through a {@link Rig} (bones x baked vertices, the vertex shader's product, done on the CPU
 * by {@link Rig#skin}) equal what vanilla's ModelPart.render writes for the same pass: same count, same order, positions
 * and normals within {@link #EPS}, uv, colour, overlay and light exact. At rest, animated (random bone motion, under an
 * entity-like pose stack with a flip and a non-unit scale), with folded parts that unexpectedly move (the rig must notice
 * and learn them), and in every pass a unit draws: the detail on and off, an upgrade on, the team and the glow pass.
 * <p>
 * A negative control in every model's run: a folded part moved behind the rig's back (the deviation check skipped) must
 * make the comparison FAIL - so a pass here means the comparison can see a wrong pose.
 */
class RigTest {
    /** Blocks (positions) and unit-normal components: float regrouping of the same products stays far under this. */
    static final float EPS = 1e-4f;
    static final int LIGHT = 0x00F000A0, OVERLAY = (3) | (10 << 16);
    static final float R = 0.85f, G = 0.6f, B = 0.3f, A = 1f;

    /** A unit's pose stack at render time, roughly: camera-relative, yawed, flipped (LivingEntityRenderer), scaled. */
    static PoseStack entityPose(Random rnd) {
        PoseStack ps = new PoseStack();
        ps.mulPose(Axis.XP.rotationDegrees(55f));   // the RTS camera's tilt
        ps.translate(rnd.nextFloat() * 40 - 20, -12.5f, rnd.nextFloat() * 40 - 20);
        ps.mulPose(Axis.YP.rotationDegrees(180f - rnd.nextFloat() * 360f));
        ps.scale(-1f, -1f, 1f);
        ps.translate(0f, -1.501f, 0f);
        ps.scale(0.95f, 0.95f, 0.95f);
        return ps;
    }

    /** What vanilla writes for one pass of {@code root}. */
    static VertexCapture vanilla(ModelPart root, PoseStack ps) {
        VertexCapture cap = new VertexCapture();
        root.render(ps, cap, LIGHT, OVERLAY, R, G, B, A);
        return cap;
    }

    /** The same pass through the rig (learning moved parts first); null if the rig gave the tree up. */
    static VertexCapture instanced(RigCache cache, ModelPart root, PoseStack ps) {
        RigCache.Entry e = cache.entry(root);
        assertNotNull(e, "a root part gets a rig");
        Rig rig = e.rig();
        for (int guard = 0; rig != null && guard <= RigCache.MAX_REBUILDS + 1; guard++) {
            boolean[] vis = rig.newVisibility();
            long[] bits = rig.newSignature();
            if (!rig.signature(vis, bits))
                return new VertexCapture();   // nothing drawn
            List<ModelPart> moved = new ArrayList<>();
            rig.movedParts(vis, moved);
            if (!moved.isEmpty()) {
                rig = cache.promote(e, moved);
                continue;
            }
            Matrix4f[] bp = rig.newPoses();
            Matrix3f[] bn = rig.newNormals();
            rig.bones(ps.last(), new PoseStack(), bp, bn);
            VertexCapture out = new VertexCapture();
            rig.skin(rig.variant(bits), bp, bn, R, G, B, A, OVERLAY, LIGHT, out);
            return out;
        }
        return null;
    }

    /** The two streams agree; returns a description of the first difference, or null. */
    static String compare(VertexCapture a, VertexCapture b) {
        if (a.count() != b.count())
            return "vertex count " + a.count() + " vs " + b.count();
        for (int v = 0; v < a.count(); v++) {
            float dp = Math.max(Math.abs(a.x(v) - b.x(v)), Math.max(Math.abs(a.y(v) - b.y(v)), Math.abs(a.z(v) - b.z(v))));
            float dn = Math.max(Math.abs(a.nx(v) - b.nx(v)), Math.max(Math.abs(a.ny(v) - b.ny(v)), Math.abs(a.nz(v) - b.nz(v))));
            if (dp > EPS)
                return "vertex " + v + " position off by " + dp;
            if (dn > EPS)
                return "vertex " + v + " normal off by " + dn;
            if (a.u(v) != b.u(v) || a.vv(v) != b.vv(v))
                return "vertex " + v + " uv";
            if (a.r(v) != b.r(v) || a.g(v) != b.g(v) || a.b(v) != b.b(v) || a.a(v) != b.a(v))
                return "vertex " + v + " colour";
            if (a.overlay(v) != b.overlay(v) || a.light(v) != b.light(v))
                return "vertex " + v + " overlay/light";
        }
        return null;
    }

    static void assertMatches(String what, VertexCapture vanilla, VertexCapture inst) {
        assertNotNull(inst, what + ": the rig gave the tree up");
        assertTrue(vanilla.count() > 0, what + ": vanilla drew nothing - the test pose is wrong");
        String diff = compare(vanilla, inst);
        if (diff != null)
            fail(what + ": " + diff);
    }

    /** Random rotations (and a few moves) on {@code moving}. */
    static void animate(List<ModelPart> moving, Random rnd) {
        for (ModelPart p : moving) {
            p.xRot = p.getInitialPose().xRot + (rnd.nextFloat() - 0.5f) * 1.2f;
            p.yRot = p.getInitialPose().yRot + (rnd.nextFloat() - 0.5f) * 1.2f;
            p.zRot = p.getInitialPose().zRot + (rnd.nextFloat() - 0.5f) * 1.2f;
            if (rnd.nextInt(4) == 0)
                p.y = p.getInitialPose().y + (rnd.nextFloat() - 0.5f) * 3f;
        }
    }

    /** Every leaf part (no children) of the tree - the rotation helpers of a generated body among them. */
    static List<ModelPart> leaves(ModelPart root) {
        List<ModelPart> out = new ArrayList<>();
        for (ModelPart p : root.getAllParts().toList())
            if (p != root && !p.isEmpty() && p.getAllParts().count() == 1)   // its stream is itself alone: a leaf
                out.add(p);
        return out;
    }

    /** The run every model gets: rest, animation over frames, a surprise move, and the negative control. */
    static void fullRun(String name, ModelPart root, List<ModelPart> bones, long seed) {
        Random rnd = new Random(seed);
        RigCache cache = new RigCache();
        root.getAllParts().forEach(ModelPart::resetPose);
        // at rest
        PoseStack ps = entityPose(rnd);
        assertMatches(name + " at rest", vanilla(root, ps), instanced(cache, root, ps));
        // twenty animated frames, each under a new unit pose (the model is shared by every unit of the type)
        for (int f = 0; f < 20; f++) {
            root.getAllParts().forEach(ModelPart::resetPose);
            animate(bones, rnd);
            ps = entityPose(rnd);
            assertMatches(name + " animated frame " + f, vanilla(root, ps), instanced(cache, root, ps));
        }
        // a folded part (a leaf no animation names) moves: the rig must notice, learn it, and still match
        List<ModelPart> leaves = leaves(root);
        leaves.removeAll(bones);
        if (!leaves.isEmpty()) {
            ModelPart surprise = leaves.get(rnd.nextInt(leaves.size()));
            root.getAllParts().forEach(ModelPart::resetPose);
            animate(bones, rnd);
            surprise.zRot += 0.7f;
            ps = entityPose(rnd);
            int before = cache.entry(root).rebuilds();
            assertMatches(name + " with a folded part moved", vanilla(root, ps), instanced(cache, root, ps));
            assertTrue(cache.entry(root).rebuilds() > before, name + ": the moved part was not learned (no rebuild)");
            assertTrue(cache.entry(root).rig().isBone(surprise), name + ": the moved part is not a bone after learning");
            // the negative control: move another folded part but skip the deviation check - the comparison must fail
            List<ModelPart> folded = new ArrayList<>();
            Rig rig = cache.entry(root).rig();
            for (ModelPart p : rig.parts)
                if (!rig.isBone(p) && p.getAllParts().count() == 1)
                    folded.add(p);
            if (!folded.isEmpty()) {
                ModelPart hidden = null;
                // one that draws something (has cubes): its vertices must land elsewhere in vanilla's stream
                for (ModelPart p : folded)
                    if (!p.isEmpty()) {
                        hidden = p;
                        break;
                    }
                if (hidden != null) {
                    hidden.xRot += 0.9f;
                    ps = entityPose(rnd);
                    boolean[] vis = rig.newVisibility();
                    long[] bits = rig.newSignature();
                    rig.signature(vis, bits);
                    Matrix4f[] bp = rig.newPoses();
                    Matrix3f[] bn = rig.newNormals();
                    rig.bones(ps.last(), new PoseStack(), bp, bn);
                    VertexCapture wrong = new VertexCapture();
                    rig.skin(rig.variant(bits), bp, bn, R, G, B, A, OVERLAY, LIGHT, wrong);
                    assertNotNull(compare(vanilla(root, ps), wrong),
                        name + ": NEGATIVE CONTROL - a moved folded part went unseen by the comparison");
                    assertTrue(rig.staticDeviation(vis) >= 0, name + ": the deviation check missed a moved folded part");
                }
            }
        }
        root.getAllParts().forEach(ModelPart::resetPose);
    }

    @Test
    void halberdier_every_pass_matches_vanilla() {
        HalberdierModel<Entity> m = new HalberdierModel<>(HalberdierModel.createBodyLayer().bakeRoot());
        List<ModelPart> bones = new ArrayList<>(m.bones.values());
        fullRun("halberdier", m.root(), bones, 11);
        passes("halberdier", m, m.root(), bones, m::setDetail, on -> m.setUpgrade("tempered", on), 12);
    }

    @Test
    void radiant_paladin_every_pass_matches_vanilla() {
        RadiantPaladinModel<Entity> m = new RadiantPaladinModel<>(RadiantPaladinModel.createBodyLayer().bakeRoot());
        List<ModelPart> bones = new ArrayList<>(m.bones.values());
        fullRun("radiant paladin", m.root(), bones, 21);
        passes("radiant paladin", m, m.root(), bones, m::setDetail, on -> { }, 22);
    }

    @Test
    void bone_dragon_matches_vanilla() {
        BoneDragonModel<Entity> m = new BoneDragonModel<>(BoneDragonModel.createBodyLayer().bakeRoot());
        List<ModelPart> bones = new ArrayList<>(m.bones.values());
        fullRun("bone dragon", m.root(), bones, 31);
        passes("bone dragon", m, m.root(), bones, m::setDetail, on -> { }, 32);
    }

    @Test
    void royal_guard_keyframe_body_matches_vanilla() {
        RoyalGuardModel<Entity> m = new RoyalGuardModel<>(RoyalGuardModel.createBodyLayer().bakeRoot());
        // a keyframe body: any part may be keyed - animate every part that has children (the joints)
        List<ModelPart> joints = new ArrayList<>();
        for (ModelPart p : m.root().getAllParts().toList())
            if (p != m.root() && p.getAllParts().count() > 1)
                joints.add(p);
        fullRun("royal guard", m.root(), joints, 41);
    }

    @Test
    void vanilla_humanoid_parts_match_vanilla() {
        ModelPart root = LayerDefinition.create(HumanoidModel.createMesh(CubeDeformation.NONE, 0f), 64, 64).bakeRoot();
        HumanoidModel<net.minecraft.world.entity.LivingEntity> m = new HumanoidModel<>(root);
        // vanilla draws a humanoid as separate roots (head, hat, body, arms, legs): each is its own rig
        Random rnd = new Random(51);
        RigCache cache = new RigCache();
        ModelPart[] roots = { m.head, m.hat, m.body, m.rightArm, m.leftArm, m.rightLeg, m.leftLeg };
        for (int f = 0; f < 10; f++) {
            for (ModelPart p : roots) {
                p.resetPose();
                p.xRot = (rnd.nextFloat() - 0.5f) * 2f;
                p.yRot = (rnd.nextFloat() - 0.5f) * 0.5f;
            }
            PoseStack ps = entityPose(rnd);
            for (ModelPart p : roots)
                assertMatches("humanoid part frame " + f, vanilla(p, ps), instanced(cache, p, ps));
        }
    }

    /** The passes a unit draws: detail on / off, an upgrade, the team and the glow passes (SplitPasses + PassPruning). */
    static void passes(String name, SplitPasses split, ModelPart root, List<ModelPart> bones,
                       java.util.function.Consumer<Boolean> detail, java.util.function.Consumer<Boolean> upgrade,
                       long seed) {
        Random rnd = new Random(seed);
        RigCache cache = new RigCache();
        for (int f = 0; f < 6; f++) {
            root.getAllParts().forEach(ModelPart::resetPose);
            animate(bones, rnd);
            detail.accept(f % 2 == 0);
            upgrade.accept(f % 3 == 0);
            PoseStack ps = entityPose(rnd);
            assertMatches(name + " body pass, frame " + f, vanilla(root, ps), instanced(cache, root, ps));
            split.beginPass(split.teamParts());
            try {
                assertMatches(name + " team pass, frame " + f, vanilla(root, ps), instanced(cache, root, ps));
            } finally {
                split.endPass(split.teamParts());
            }
            split.beginPass(split.glowParts());
            try {
                assertMatches(name + " glow pass, frame " + f, vanilla(root, ps), instanced(cache, root, ps));
            } finally {
                split.endPass(split.glowParts());
            }
        }
        detail.accept(true);
        upgrade.accept(false);
        // a signature seen before is the same variant (one index buffer per combination, not one per frame)
        Rig rig = cache.entry(root).rig();
        assertNotNull(rig, name + ": the rig gave the tree up");
        int variants = rig.variantCount();
        boolean[] vis = rig.newVisibility();
        long[] bits = rig.newSignature();
        rig.signature(vis, bits);
        Rig.Variant v = rig.variant(bits);
        assertSame(v, rig.variant(bits.clone()), name + ": the same signature made a second variant");
        assertTrue(rig.variantCount() <= variants + 1, name + ": variants grew past the passes seen");
        assertTrue(rig.boneCount < rig.parts.length / 2,
            name + ": " + rig.boneCount + " bones of " + rig.parts.length + " parts - the folding folded nothing");
    }

    @Test
    void bones_pack_as_the_shader_reads_them() {
        Random rnd = new Random(61);
        InstanceBatch batch = new InstanceBatch();
        Matrix4f[] poses = new Matrix4f[3];
        Matrix3f[] normals = new Matrix3f[3];
        for (int s = 0; s < 3; s++) {
            PoseStack ps = entityPose(rnd);
            ps.mulPose(Axis.ZP.rotationDegrees(rnd.nextFloat() * 90));
            poses[s] = new Matrix4f(ps.last().pose());
            normals[s] = new Matrix3f(ps.last().normal());
        }
        int first = batch.addBones(poses, normals, 3);
        assertEquals(0, first);
        int second = batch.addBones(poses, normals, 1);
        assertEquals(3 * InstanceBatch.TEXELS_PER_BONE, second, "bone blocks are contiguous, 6 texels a bone");
        float[] out = new float[6];
        for (int s = 0; s < 3; s++) {
            float x = rnd.nextFloat(), y = rnd.nextFloat(), z = rnd.nextFloat();
            InstanceBatch.unpack(batch.bones(), s * InstanceBatch.TEXELS_PER_BONE, x, y, z, 0.6f, 0.8f, 0f, out);
            org.joml.Vector4f p = poses[s].transform(new org.joml.Vector4f(x, y, z, 1f));
            org.joml.Vector3f n = normals[s].transform(new org.joml.Vector3f(0.6f, 0.8f, 0f));
            assertEquals(p.x, out[0], 1e-5f);
            assertEquals(p.y, out[1], 1e-5f);
            assertEquals(p.z, out[2], 1e-5f);
            assertEquals(n.x, out[3], 1e-5f);
            assertEquals(n.y, out[4], 1e-5f);
            assertEquals(n.z, out[5], 1e-5f);
        }
    }

    @Test
    void colour_packs_as_the_vertex_writer_stores_it() {
        float[] cs = { 0f, 1f, 0.5f, 0.85f * 0.73f, 0.999f, 0.0039f };
        for (float c : cs) {
            int p = InstanceBatch.packColour(c, c, c, 1f);
            assertEquals((int) (c * 255.0F), p & 0xFF, "channel of " + c);
            assertEquals(255, (p >>> 24) & 0xFF);
        }
    }

    @Test
    void groups_draw_bodies_before_layers_and_batch_by_type() {
        HalberdierModel<Entity> m = new HalberdierModel<>(HalberdierModel.createBodyLayer().bakeRoot());
        RigCache cache = new RigCache();
        Rig rig = cache.entry(m.root()).rig();
        boolean[] vis = rig.newVisibility();
        long[] bits = rig.newSignature();
        rig.signature(vis, bits);
        Rig.Variant body = rig.variant(bits);
        m.beginPass(m.teamParts());
        rig.signature(vis, bits);
        Rig.Variant team = rig.variant(bits);
        m.endPass(m.teamParts());
        Object bodyType = new Object(), teamType = new Object(), glowType = new Object();
        InstanceBatch batch = new InstanceBatch();
        // three units: body (0), team (1), glow (2) each, in the order a frame meets them
        for (int u = 0; u < 3; u++) {
            batch.add(0, InstanceBatch.KIND_CUTOUT, bodyType, rig, body, u * 100, 10 + u, 20 + u, 30 + u, u);
            batch.add(1, InstanceBatch.KIND_CUTOUT, teamType, rig, team, u * 100, 10 + u, 20 + u, 40 + u, u);
            batch.add(2, InstanceBatch.KIND_EYES, glowType, rig, body, u * 100, 0xF000F0, 0, 50 + u, u);
        }
        // a fourth unit demoted after its body: its records go
        batch.add(0, InstanceBatch.KIND_CUTOUT, bodyType, rig, body, 900, 1, 1, 1, 3);
        assertEquals(1, batch.kill(3));
        List<InstanceBatch.Group> groups = batch.build();
        assertEquals(3, groups.size(), "one draw per pass kind here: bodies, team cloth, glow");
        assertSame(bodyType, groups.get(0).type);
        assertSame(teamType, groups.get(1).type);
        assertSame(glowType, groups.get(2).type);
        int[] h = batch.headers();
        for (InstanceBatch.Group g : groups) {
            assertEquals(3, g.count, "every live unit once per group");
            for (int k = 0; k < g.count; k++) {
                int at = (g.first + k) * InstanceBatch.HEADER;
                assertEquals(k * 100, h[at], "instances keep the order the units came in");
            }
        }
        assertEquals(30, h[groups.get(0).first * InstanceBatch.HEADER + 3]);
        assertEquals(40, h[groups.get(1).first * InstanceBatch.HEADER + 3]);
        assertEquals(9, batch.liveCount());
    }

    @Test
    void a_rig_that_keeps_moving_is_given_up() {
        HalberdierModel<Entity> m = new HalberdierModel<>(HalberdierModel.createBodyLayer().bakeRoot());
        RigCache cache = new RigCache();
        RigCache.Entry e = cache.entry(m.root());
        List<ModelPart> leaves = leaves(m.root());
        Collections.shuffle(leaves, new Random(71));
        Rig rig = e.rig();
        int k = 0;
        while (rig != null && k < leaves.size())
            rig = cache.promote(e, leaves.get(k++));
        assertEquals(null, e.rig(), "after " + RigCache.MAX_REBUILDS + " rebuilds the tree is left to the old path");
        assertTrue(e.why() != null && !e.why().isEmpty(), "the give-up names why");
        assertTrue(k <= RigCache.MAX_REBUILDS + 1);
        // the members still map back (a pass on a part of it is recognised as this tree's)
        assertSame(e, cache.memberOf(m.root()));
        assertSame(e, cache.memberOf(leaves.get(0)));
        assertTrue(cache.takeRetired().size() >= 1, "the replaced rigs are handed to the GL side to free");
    }

    /**
     * Which passes this path draws (eyes run 42189253540: every grunt went the old way, "render-type" - PlayerModel
     * bodies use entity_translucent, whose shader code and blend are vanilla's entity cutout's). A translucent pass is
     * drawn instanced only while it is opaque: a fading one (alpha < 1) needs vanilla's back-to-front quad sort.
     */
    @Test
    void translucent_passes_are_instanced_only_while_opaque() {
        assertTrue(InstanceBatch.instanceable(InstanceBatch.KIND_CUTOUT, 1f));
        assertTrue(InstanceBatch.instanceable(InstanceBatch.KIND_CUTOUT, 0.5f), "cutout keeps its own alpha test");
        assertTrue(InstanceBatch.instanceable(InstanceBatch.KIND_EYES, 1f));
        assertTrue(InstanceBatch.instanceable(InstanceBatch.KIND_TRANSLUCENT, 1f), "an opaque translucent body (grunt)");
        assertTrue(!InstanceBatch.instanceable(InstanceBatch.KIND_TRANSLUCENT, 0.99f), "a fading body goes the old way");
        assertTrue(!InstanceBatch.instanceable(InstanceBatch.KIND_TRANSLUCENT, 0f));
        assertTrue(!InstanceBatch.instanceable(-1, 1f), "a shader this path does not reproduce");
    }
}
