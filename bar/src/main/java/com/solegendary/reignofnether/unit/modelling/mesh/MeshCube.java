package com.solegendary.reignofnether.unit.modelling.mesh;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.core.Direction;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.EnumSet;

/**
 * A triangle mesh drawn where a cube would be (mesh bodies, inbox/spec-1-mesh-bodies-2026-10-17.md): one ModelPart's
 * whole mesh, written into the same VertexConsumer with the same arithmetic as vanilla's ModelPart.Cube.compile. The
 * lightmap, the hurt overlay, fog, TeamColourLayer, the glow pass, SplitPasses, mipmaps and Oculus' extended vertex
 * data (it derives each quad's tangent from its first three corners - so a triangle is stored as a b c c) all see an
 * ordinary entity quad. Not a new renderer but a new kind of cube, so every layer and animation keeps working.
 *
 * Exactness is the point of step 1: per vertex, {@code pose.transform(x / 16, y / 16, z / 16, 1)} and
 * {@code normal.transform(n)} through the same JOML methods vanilla calls, then the same 14-argument
 * {@code vertex(...)} in the same order - MeshBodyTest demands the cube Paladin's vertex stream bit for bit.
 *
 * Embeddium (0.3.31: the testers' Windows pack and the eyes shader lane) draws a part's cubes with its own fast writer
 * only when a cube's class is exactly ModelPart.Cube (its CuboidMixin: getClass() == ModelPart.Cube.class), and any
 * subclass through compile(). But Oculus (1.8.0) cancels ModelPart.render for a Sodium consumer and draws the tree from
 * each cube's constructor arguments, never calling compile() - this cube would draw nothing (eyes #249: 0 px). So a mesh
 * body is drawn through PlainBuffers, where both step aside. A vertex costs a little more here than in the fast writer -
 * spec 5 measures it before bodies get heavier.
 *
 * The super constructor gets the mesh's bounds and NO faces (an empty face set builds zero polygons): vanilla reads
 * minX..maxZ for what hangs on a random cube (ModelPart.getRandomCube - stuck arrows, particles).
 *
 * A sculpt's baked vertex colours (MeshBody.Lod.col: the marble and its carved-depth occlusion, tools/sculpt_angel.py)
 * multiply the draw colour per vertex - vanilla's entity shader lights a vertex from two flat lights and no shadow, so
 * a running-drill channel only reads dark if its vertices say so. Without colours the vertex call is exactly the one
 * above (a version-1 body's stream is unchanged, bit for bit).
 *
 * Back-face culling (r12, the White Angel statue's optimisation pass): a closed mesh never shows the side of a quad that
 * faces away from the camera, yet every quad was transformed and written each frame. With {@link #backFaceCulling}
 * on, a quad is skipped when both its triangles' normals and all four corners' normals face away (orthographic: from
 * the view direction; perspective: from the camera) - quads whose winding disagrees with their corners' normals are
 * always drawn. Proved offline to change no pixel of the statue (the PR's notes) and skips ~20-33% of its quads.
 * Off by default: the bodies MeshBodyTest holds bit for bit to their cube models never see it.
 *
 * Not thread-safe: compile reuses two vectors, because it runs per unit per frame and must not allocate per vertex;
 * entity rendering is on the render thread.
 */
@OnlyIn(Dist.CLIENT)
public final class MeshCube extends ModelPart.Cube {
    private final float[] pos;   // xyz per vertex: model px, part-local, Minecraft part space (y down)
    private final float[] nrm;   // xyz per vertex, unit length
    private final float[] uv;    // uv per vertex: atlas 0..1
    private final float[] col;   // rgba per vertex 0..1, or null
    private final int[] quads;   // 4 vertex indices per face, in drawing order (a triangle: a b c c)
    private final Vector4f p = new Vector4f();
    private final Vector3f n = new Vector3f();
    private final Matrix4f inv = new Matrix4f();
    private float[] faceN;       // per quad: its two triangles' outward normals (6 floats; NaN: never culled), lazily

    private static boolean cullOn, cullOrtho;

    /**
     * For the draws that follow on the render thread: skip the quads of closed meshes that face away from the camera -
     * orthographic (the RTS view) or perspective. The caller turns it off again after its draw.
     */
    public static void backFaceCulling(boolean on, boolean ortho) {
        cullOn = on;
        cullOrtho = ortho;
    }

    MeshCube(MeshBody.Lod lod) {
        super(0, 0, lod.minX, lod.minY, lod.minZ, lod.maxX - lod.minX, lod.maxY - lod.minY, lod.maxZ - lod.minZ,
                0f, 0f, 0f, false, 64f, 64f, EnumSet.noneOf(Direction.class));
        this.pos = lod.pos;
        this.nrm = lod.nrm;
        this.uv = lod.uv;
        this.col = lod.col;
        this.quads = lod.quads;
    }

    /** Vertices written per draw (4 per face). */
    public int drawnVertices() {
        return quads.length;
    }

    @Override
    public void compile(PoseStack.Pose pose, VertexConsumer consumer, int light, int overlay, float red, float green,
                        float blue, float alpha) {
        Matrix4f m = pose.pose();
        Matrix3f nm = pose.normal();
        Vector4f p = this.p;
        Vector3f n = this.n;
        float[] pos = this.pos, nrm = this.nrm, uv = this.uv, col = this.col;
        int[] quads = this.quads;
        boolean cull = cullOn;
        float[] fn = null;
        float vx = 0f, vy = 0f, vz = 0f, cx = 0f, cy = 0f, cz = 0f;
        if (cull) {
            fn = faceNormals();
            if (cullOrtho) {   // toward the camera in part space: a normal faces it when its view-space z is positive
                vx = nm.m02();
                vy = nm.m12();
                vz = nm.m22();
            } else {           // the camera in part space (px): the view's origin through the pose's inverse
                m.invertAffine(inv);
                cx = inv.m30() * 16f;
                cy = inv.m31() * 16f;
                cz = inv.m32() * 16f;
            }
        }
        int held = -1;   // the vertex whose normal n holds, transformed (a face's corners share one: vanilla does it once)
        for (int k = 0, count = quads.length; k < count; k++) {
            if (cull && (k & 3) == 0) {
                if (!cullOrtho) {
                    int a3 = quads[k] * 3;
                    vx = cx - pos[a3];
                    vy = cy - pos[a3 + 1];
                    vz = cz - pos[a3 + 2];
                }
                if (!facesCamera(fn, k, vx, vy, vz)) {
                    k += 3;
                    continue;
                }
            }
            int i = quads[k];
            int i3 = i * 3;
            if (held < 0 || !sameNormal(nrm, held * 3, i3)) {
                nm.transform(n.set(nrm[i3], nrm[i3 + 1], nrm[i3 + 2]));
                held = i;
            }
            m.transform(p.set(pos[i3] / 16.0F, pos[i3 + 1] / 16.0F, pos[i3 + 2] / 16.0F, 1.0F));
            if (col == null) {
                consumer.vertex(p.x(), p.y(), p.z(), red, green, blue, alpha, uv[i * 2], uv[i * 2 + 1], overlay, light,
                        n.x(), n.y(), n.z());
            } else {
                int i4 = i * 4;
                consumer.vertex(p.x(), p.y(), p.z(), red * col[i4], green * col[i4 + 1], blue * col[i4 + 2],
                        alpha * col[i4 + 3], uv[i * 2], uv[i * 2 + 1], overlay, light, n.x(), n.y(), n.z());
            }
        }
    }

    /** Whether the quad starting at quads[k] may show its front to a camera in direction (x, y, z). */
    private boolean facesCamera(float[] fn, int k, float x, float y, float z) {
        int q6 = (k >> 2) * 6;
        if (Float.isNaN(fn[q6]))
            return true;
        if (fn[q6] * x + fn[q6 + 1] * y + fn[q6 + 2] * z > 0f || fn[q6 + 3] * x + fn[q6 + 4] * y + fn[q6 + 5] * z > 0f)
            return true;
        for (int j = 0; j < 4; j++) {
            int i3 = quads[k + j] * 3;
            if (nrm[i3] * x + nrm[i3 + 1] * y + nrm[i3 + 2] * z > 0f)
                return true;
        }
        return false;
    }

    /**
     * Each quad's two triangle normals, turned to agree with its corners' normals (the stored winding's handedness does
     * not matter); NaN when a triangle disagrees with them (a decimated sliver: always drawn). Computed once.
     */
    private float[] faceNormals() {
        if (faceN != null)
            return faceN;
        int nq = quads.length / 4;
        float[] out = new float[nq * 6];
        for (int q = 0; q < nq; q++) {
            int a = quads[q * 4] * 3, b = quads[q * 4 + 1] * 3, c = quads[q * 4 + 2] * 3, d = quads[q * 4 + 3] * 3;
            float[] n1 = cross(a, b, c), n2 = d == c ? n1 : cross(c, d, a);
            float sx = 0f, sy = 0f, sz = 0f;
            for (int j = 0; j < 4; j++) {
                int i3 = quads[q * 4 + j] * 3;
                sx += nrm[i3];
                sy += nrm[i3 + 1];
                sz += nrm[i3 + 2];
            }
            float d1 = cosine(n1, sx, sy, sz), d2 = cosine(n2, sx, sy, sz);
            float sign = d1 >= 0f ? 1f : -1f;
            if (Math.abs(d1) < 0.3f || d1 * d2 < 0f || Math.abs(d2) < 0.3f) {
                out[q * 6] = Float.NaN;
                continue;
            }
            for (int j = 0; j < 3; j++) {
                out[q * 6 + j] = sign * n1[j];
                out[q * 6 + 3 + j] = sign * n2[j];
            }
        }
        return faceN = out;
    }

    private float[] cross(int a, int b, int c) {
        float ux = pos[b] - pos[a], uy = pos[b + 1] - pos[a + 1], uz = pos[b + 2] - pos[a + 2];
        float wx = pos[c] - pos[a], wy = pos[c + 1] - pos[a + 1], wz = pos[c + 2] - pos[a + 2];
        return new float[]{uy * wz - uz * wy, uz * wx - ux * wz, ux * wy - uy * wx};
    }

    private static float cosine(float[] v, float x, float y, float z) {
        float lv = (float) Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
        float ls = (float) Math.sqrt(x * x + y * y + z * z);
        return lv < 1e-12f || ls < 1e-12f ? 0f : (v[0] * x + v[1] * y + v[2] * z) / (lv * ls);
    }

    /** Bit-equal normals (so -0 and 0 are not mixed up: the transform keeps the sign of a zero). */
    private static boolean sameNormal(float[] nrm, int a, int b) {
        return Float.floatToRawIntBits(nrm[a]) == Float.floatToRawIntBits(nrm[b])
            && Float.floatToRawIntBits(nrm[a + 1]) == Float.floatToRawIntBits(nrm[b + 1])
            && Float.floatToRawIntBits(nrm[a + 2]) == Float.floatToRawIntBits(nrm[b + 2]);
    }
}
