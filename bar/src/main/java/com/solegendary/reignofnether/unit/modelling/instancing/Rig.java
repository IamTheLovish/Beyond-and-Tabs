package com.solegendary.reignofnether.unit.modelling.instancing;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * One model part tree baked for instancing: the instanced unit bodies' CPU side (pure - no GL; the JUnit tests build
 * and pose real models with it).
 * <p>
 * Vanilla draws a part tree by walking every part - push the pose, translate and rotate, write the part's cubes' vertices
 * on the CPU, recurse, pop - for every unit and every pass (a generated body is 220-640 parts; ~92% of them are
 * rotation helpers no animation ever moves). A rig splits the tree into BONES - the root, and every part whose pose is
 * not the one it was built in (animated) - and folds every other part into its nearest bone: its cubes' vertices are
 * written once, here, in that bone's frame. A frame then costs one matrix per bone (computed with the part's own
 * {@link ModelPart#translateAndRotate}, so Embeddium's overwrite of it is used exactly as the old path uses it), and the
 * GPU puts every vertex where the old walk would have.
 * <p>
 * What may differ between frames is checked, never assumed:
 * <ul>
 *   <li>a folded part whose pose moved ({@link #staticDeviation}) - the caller turns it into a bone and builds again
 *       ({@link RigCache}; a tree that keeps moving parts falls back to the old path for good);</li>
 *   <li>which parts draw ({@link #signature}: visibility and skipDraw, which the detail switch, the upgrade parts and the
 *       team / glow passes - SplitPasses, PassPruning - all set): every combination seen is its own {@link Variant},
 *       the list of quads that pass draws, in the old walk's order.</li>
 * </ul>
 * The tree is read through ModelPart's public API alone ({@link ModelPart#visit} for the cubes in drawing order and their
 * parts' paths, {@link ModelPart#getChild}, {@link ModelPart.Cube#compile} into a {@link VertexCapture}), so this works
 * with or without mixins, in the game and in a unit test.
 */
@OnlyIn(Dist.CLIENT)
public final class Rig {
    /** Floats of a part's pose: x y z, xRot yRot zRot, xScale yScale zScale. */
    static final int POSE = 9;
    private static final AtomicInteger IDS = new AtomicInteger();

    public final int id = IDS.incrementAndGet();
    public final ModelPart root;
    /** The parts in drawing order (pre-order: a part, then its children in the map's order). */
    public final ModelPart[] parts;
    final int[] parent;
    final boolean[] hasCubes;
    final boolean[] isBone;
    /** Part -> slot of its nearest bone (itself if it is one). */
    final int[] boneOf;
    public final int boneCount;
    final int[] bonePart;
    final int[] boneParentSlot;
    /** Per bone: the folded parts strictly between its parent bone and it, as one transform (pose and normal). */
    final Matrix4f[] chainPose;
    final Matrix3f[] chainNormal;
    final boolean[] chainIdentity;
    /** Per part: the pose it was baked in (a folded part must still have it). */
    final float[] baked;
    /** Per part: its quads [start, end). */
    final int[] quadStart, quadEnd;
    public final int quadCount;
    /** Per vertex (quad * 4 + k): position and normal in its bone's frame, uv, bone slot. */
    final float[] pos, nrm, uv;
    final int[] vertBone;

    private final List<Variant> variants = new ArrayList<>();
    /** The GL side's objects for this rig (InstancedBodies: vertex array and buffer), null until first drawn. */
    public Object gpu;
    final int signatureWords;

    /** The quads one combination of drawn parts draws, in the old walk's order. */
    public static final class Variant {
        private static final AtomicInteger VIDS = new AtomicInteger();
        public final int id = VIDS.incrementAndGet();
        final long[] bits;
        /** Quad indices of the rig, drawing order. */
        public final int[] quads;
        /** The GL side's handle (InstancedBodies: the index buffer), null until first drawn. */
        public Object gpu;

        Variant(long[] bits, int[] quads) {
            this.bits = bits;
            this.quads = quads;
        }
    }

    private Rig(ModelPart root, List<ModelPart> parts, List<Integer> parent, List<List<ModelPart.Cube>> cubes,
                Set<ModelPart> forcedBones) {
        this.root = root;
        int n = parts.size();
        this.parts = parts.toArray(new ModelPart[0]);
        this.parent = new int[n];
        this.hasCubes = new boolean[n];
        this.isBone = new boolean[n];
        this.boneOf = new int[n];
        this.baked = new float[n * POSE];
        for (int p = 0; p < n; p++) {
            this.parent[p] = parent.get(p);
            this.hasCubes[p] = !cubes.get(p).isEmpty();
            ModelPart part = this.parts[p];
            readPose(part, baked, p * POSE);
            // the root is always a bone (its parent is the caller's pose stack); a part not at its built pose moves
            this.isBone[p] = p == 0 || forcedBones.contains(part) || !atInitialPose(part);
        }
        int slots = 0;
        for (int p = 0; p < n; p++)
            if (isBone[p])
                slots++;
        this.boneCount = slots;
        this.bonePart = new int[slots];
        this.boneParentSlot = new int[slots];
        this.chainPose = new Matrix4f[slots];
        this.chainNormal = new Matrix3f[slots];
        this.chainIdentity = new boolean[slots];
        int s = 0;
        for (int p = 0; p < n; p++) {
            if (isBone[p]) {
                bonePart[s] = p;
                boneOf[p] = s;
                boneParentSlot[s] = p == 0 ? -1 : boneOf[nearestBoneAncestor(p)];
                // the folded parts between the parent bone and this one, top down
                List<Integer> chain = new ArrayList<>();
                for (int q = p == 0 ? -1 : this.parent[p]; q >= 0 && !isBone[q]; q = this.parent[q])
                    chain.add(0, q);
                PoseStack ps = new PoseStack();
                for (int q : chain)
                    this.parts[q].translateAndRotate(ps);
                chainPose[s] = new Matrix4f(ps.last().pose());
                chainNormal[s] = new Matrix3f(ps.last().normal());
                chainIdentity[s] = chain.isEmpty();
                s++;
            } else
                boneOf[p] = boneOf[nearestBoneAncestor(p)];
        }
        // the vertices: each part's cubes in its own frame, carried into its bone's by the folded parts between
        VertexCapture cap = new VertexCapture();
        PoseStack identity = new PoseStack();
        this.quadStart = new int[n];
        this.quadEnd = new int[n];
        int quads = 0;
        List<float[]> partVerts = new ArrayList<>();
        for (int p = 0; p < n; p++) {
            cap.clear();
            for (ModelPart.Cube cube : cubes.get(p))
                cube.compile(identity.last(), cap, 0, 0, 1f, 1f, 1f, 1f);
            int verts = cap.count() - cap.count() % 4;
            quadStart[p] = quads;
            quads += verts / 4;
            quadEnd[p] = quads;
            float[] copy = Arrays.copyOf(cap.f, verts * VertexCapture.FLOATS);
            partVerts.add(copy);
        }
        this.quadCount = quads;
        this.pos = new float[quads * 4 * 3];
        this.nrm = new float[quads * 4 * 3];
        this.uv = new float[quads * 4 * 2];
        this.vertBone = new int[quads * 4];
        Vector4f v4 = new Vector4f();
        Vector3f v3 = new Vector3f();
        for (int p = 0; p < n; p++) {
            // the folded parts from the bone (exclusive) down to this part (inclusive, unless it is the bone)
            List<Integer> chain = new ArrayList<>();
            for (int q = p; q >= 0 && !isBone[q]; q = this.parent[q])
                chain.add(0, q);
            PoseStack ps = new PoseStack();
            for (int q : chain)
                this.parts[q].translateAndRotate(ps);
            Matrix4f m = ps.last().pose();
            Matrix3f nm = ps.last().normal();
            float[] src = partVerts.get(p);
            int v0 = quadStart[p] * 4;
            for (int k = 0; k < (quadEnd[p] - quadStart[p]) * 4; k++) {
                int o = k * VertexCapture.FLOATS, v = v0 + k;
                if (chain.isEmpty()) {
                    pos[v * 3] = src[o];
                    pos[v * 3 + 1] = src[o + 1];
                    pos[v * 3 + 2] = src[o + 2];
                    nrm[v * 3] = src[o + 9];
                    nrm[v * 3 + 1] = src[o + 10];
                    nrm[v * 3 + 2] = src[o + 11];
                } else {
                    m.transform(v4.set(src[o], src[o + 1], src[o + 2], 1f));
                    pos[v * 3] = v4.x;
                    pos[v * 3 + 1] = v4.y;
                    pos[v * 3 + 2] = v4.z;
                    nm.transform(v3.set(src[o + 9], src[o + 10], src[o + 11]));
                    nrm[v * 3] = v3.x;
                    nrm[v * 3 + 1] = v3.y;
                    nrm[v * 3 + 2] = v3.z;
                }
                uv[v * 2] = src[o + 7];
                uv[v * 2 + 1] = src[o + 8];
                vertBone[v] = boneOf[p];
            }
        }
        this.signatureWords = (n + 63) / 64;
    }

    private int nearestBoneAncestor(int p) {
        int q = parent[p];
        while (q >= 0 && !isBone[q])
            q = parent[q];
        return q;
    }

    /** Builds the rig of the tree under {@code root}; {@code forcedBones} are parts already seen moving. */
    public static Rig build(ModelPart root, Set<ModelPart> forcedBones) {
        List<ModelPart> parts = new ArrayList<>();
        List<Integer> parent = new ArrayList<>();
        List<List<ModelPart.Cube>> cubes = new ArrayList<>();
        Map<String, Integer> byPath = new HashMap<>();
        parts.add(root);
        parent.add(-1);
        cubes.add(new ArrayList<>());
        byPath.put("", 0);
        // visit: every cube of the tree, in drawing order, with its part's path ("" the root, "/body", "/body/chest")
        root.visit(new PoseStack(), (pose, path, index, cube) -> {
            Integer at = byPath.get(path);
            if (at == null) {
                // the path's parts that are not here yet (those without cubes of their own come in on their way down)
                int cur = 0;
                StringBuilder sofar = new StringBuilder();
                for (String name : path.substring(1).split("/")) {
                    sofar.append('/').append(name);
                    Integer next = byPath.get(sofar.toString());
                    if (next == null) {
                        parts.add(parts.get(cur).getChild(name));
                        parent.add(cur);
                        cubes.add(new ArrayList<>());
                        next = parts.size() - 1;
                        byPath.put(sofar.toString(), next);
                    }
                    cur = next;
                }
                at = cur;
            }
            cubes.get(at).add(cube);
        });
        return new Rig(root, parts, parent, cubes, forcedBones);
    }

    static void readPose(ModelPart p, float[] out, int o) {
        out[o] = p.x;
        out[o + 1] = p.y;
        out[o + 2] = p.z;
        out[o + 3] = p.xRot;
        out[o + 4] = p.yRot;
        out[o + 5] = p.zRot;
        out[o + 6] = p.xScale;
        out[o + 7] = p.yScale;
        out[o + 8] = p.zScale;
    }

    static boolean atInitialPose(ModelPart p) {
        PartPose i = p.getInitialPose();
        return p.x == i.x && p.y == i.y && p.z == i.z && p.xRot == i.xRot && p.yRot == i.yRot && p.zRot == i.zRot
            && p.xScale == 1f && p.yScale == 1f && p.zScale == 1f;
    }

    /** True if part {@code p}'s pose is still the one it was baked in. */
    boolean atBakedPose(int p) {
        ModelPart q = parts[p];
        int o = p * POSE;
        return q.x == baked[o] && q.y == baked[o + 1] && q.z == baked[o + 2] && q.xRot == baked[o + 3]
            && q.yRot == baked[o + 4] && q.zRot == baked[o + 5] && q.xScale == baked[o + 6] && q.yScale == baked[o + 7]
            && q.zScale == baked[o + 8];
    }

    // ---- per frame ----

    /** Scratch sized for this rig: the parts' effective visibility and the drawn-part signature. */
    public boolean[] newVisibility() {
        return new boolean[parts.length];
    }

    public long[] newSignature() {
        return new long[signatureWords];
    }

    /**
     * Which parts the pass would draw now, as vanilla decides it (ModelPart.render: an invisible part hides its subtree;
     * skipDraw drops a part's own cubes only): {@code visible[p]} = the part and all its ancestors are visible, bit p of
     * {@code bits} = it draws its cubes. Returns false when nothing draws.
     */
    public boolean signature(boolean[] visible, long[] bits) {
        Arrays.fill(bits, 0L);
        boolean any = false;
        for (int p = 0; p < parts.length; p++) {
            ModelPart q = parts[p];
            boolean v = q.visible && (p == 0 || visible[parent[p]]);
            visible[p] = v;
            if (v && hasCubes[p] && !q.skipDraw) {
                bits[p >>> 6] |= 1L << (p & 63);
                any = true;
            }
        }
        return any;
    }

    /** Every visible folded part whose pose moved since it was baked, added to {@code out} (the rig learns them all at once). */
    public void movedParts(boolean[] visible, List<ModelPart> out) {
        for (int p = 1; p < parts.length; p++)
            if (visible[p] && !isBone[p] && !atBakedPose(p))
                out.add(parts[p]);
    }

    /** The first visible folded part whose pose moved since it was baked, or -1 (the rig still holds). */
    public int staticDeviation(boolean[] visible) {
        for (int p = 1; p < parts.length; p++)
            if (visible[p] && !isBone[p] && !atBakedPose(p))
                return p;
        return -1;
    }

    /**
     * The bone matrices for a pass drawn under {@code base} (the pose stack's top when the old path would render the
     * root): bone = parent bone x folded chain x the bone's own translateAndRotate - the old walk's product, regrouped.
     * Every bone is computed (a pass that shows a part an earlier pass hid can reuse the block); {@code scratch} is a
     * pose stack the caller owns.
     */
    public void bones(PoseStack.Pose base, PoseStack scratch, Matrix4f[] outPose, Matrix3f[] outNormal) {
        scratch.pushPose();   // one pose for the whole walk (pushPose allocates): set from the parent per bone
        PoseStack.Pose top = scratch.last();
        for (int s = 0; s < boneCount; s++) {
            int p = bonePart[s];
            if (s == 0) {
                top.pose().set(base.pose());
                top.normal().set(base.normal());
            } else {
                top.pose().set(outPose[boneParentSlot[s]]);
                top.normal().set(outNormal[boneParentSlot[s]]);
                if (!chainIdentity[s]) {
                    top.pose().mul(chainPose[s]);
                    top.normal().mul(chainNormal[s]);
                }
            }
            parts[p].translateAndRotate(scratch);
            outPose[s].set(top.pose());
            outNormal[s].set(top.normal());
        }
        scratch.popPose();
    }

    /** The variant for this signature: found, or made (its quads, drawing order). */
    public Variant variant(long[] bits) {
        for (int k = 0; k < variants.size(); k++)
            if (Arrays.equals(variants.get(k).bits, bits))
                return variants.get(k);
        int count = 0;
        for (int p = 0; p < parts.length; p++)
            if ((bits[p >>> 6] & (1L << (p & 63))) != 0)
                count += quadEnd[p] - quadStart[p];
        int[] quads = new int[count];
        int at = 0;
        for (int p = 0; p < parts.length; p++)
            if ((bits[p >>> 6] & (1L << (p & 63))) != 0)
                for (int q = quadStart[p]; q < quadEnd[p]; q++)
                    quads[at++] = q;
        Variant v = new Variant(bits.clone(), quads);
        variants.add(v);
        return v;
    }

    public int variantCount() {
        return variants.size();
    }

    public List<Variant> variants() {
        return variants;
    }

    /** Matrices sized for this rig's bones. */
    public Matrix4f[] newPoses() {
        Matrix4f[] m = new Matrix4f[boneCount];
        for (int s = 0; s < boneCount; s++)
            m[s] = new Matrix4f();
        return m;
    }

    public Matrix3f[] newNormals() {
        Matrix3f[] m = new Matrix3f[boneCount];
        for (int s = 0; s < boneCount; s++)
            m[s] = new Matrix3f();
        return m;
    }

    /**
     * The CPU twin of the instanced vertex shader: the variant's vertices at the given bone matrices, in drawing order,
     * with the pass's colour, overlay and light - what the old path's walk writes for the same pass (within float
     * rounding; the tests and the in-game vertex check hold the two together).
     */
    public void skin(Variant v, Matrix4f[] bonePose, Matrix3f[] boneNormal, float r, float g, float b, float a,
                     int overlay, int light, VertexCapture out) {
        Vector4f v4 = new Vector4f();
        Vector3f v3 = new Vector3f();
        for (int q : v.quads)
            for (int k = 0; k < 4; k++) {
                int vi = q * 4 + k, s = vertBone[vi];
                bonePose[s].transform(v4.set(pos[vi * 3], pos[vi * 3 + 1], pos[vi * 3 + 2], 1f));
                boneNormal[s].transform(v3.set(nrm[vi * 3], nrm[vi * 3 + 1], nrm[vi * 3 + 2]));
                out.put(v4.x, v4.y, v4.z, r, g, b, a, uv[vi * 2], uv[vi * 2 + 1], overlay, light, v3.x, v3.y, v3.z);
            }
    }

    /** Is part {@code p} a bone (moves per frame) of this rig. */
    public boolean isBone(ModelPart part) {
        for (int p = 0; p < parts.length; p++)
            if (parts[p] == part)
                return isBone[p];
        return false;
    }

    /** Vertex data for the GPU (per vertex, in the bone's frame): positions x y z. */
    public float[] positions() {
        return pos;
    }

    public float[] normals() {
        return nrm;
    }

    public float[] uvs() {
        return uv;
    }

    public int[] vertexBones() {
        return vertBone;
    }
}
