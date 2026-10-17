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
 * only when a cube's class is exactly ModelPart.Cube (its CuboidMixin: getClass() == ModelPart.Cube.class); any
 * subclass is drawn through compile(), so this override is what draws the mesh there too. Not through the fast writer,
 * though: a vertex costs a little more there than a cube's - spec 5 measures it before bodies get heavier.
 *
 * The super constructor gets the mesh's bounds and NO faces (an empty face set builds zero polygons): vanilla reads
 * minX..maxZ for what hangs on a random cube (ModelPart.getRandomCube - stuck arrows, particles).
 *
 * Not thread-safe: compile reuses two vectors, because it runs per unit per frame and must not allocate per vertex;
 * entity rendering is on the render thread.
 */
@OnlyIn(Dist.CLIENT)
public final class MeshCube extends ModelPart.Cube {
    private final float[] pos;   // xyz per vertex: model px, part-local, Minecraft part space (y down)
    private final float[] nrm;   // xyz per vertex, unit length
    private final float[] uv;    // uv per vertex: atlas 0..1
    private final int[] quads;   // 4 vertex indices per face, in drawing order (a triangle: a b c c)
    private final Vector4f p = new Vector4f();
    private final Vector3f n = new Vector3f();

    MeshCube(MeshBody.Lod lod) {
        super(0, 0, lod.minX, lod.minY, lod.minZ, lod.maxX - lod.minX, lod.maxY - lod.minY, lod.maxZ - lod.minZ,
                0f, 0f, 0f, false, 64f, 64f, EnumSet.noneOf(Direction.class));
        this.pos = lod.pos;
        this.nrm = lod.nrm;
        this.uv = lod.uv;
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
        float[] pos = this.pos, nrm = this.nrm, uv = this.uv;
        int[] quads = this.quads;
        int held = -1;   // the vertex whose normal n holds, transformed (a face's corners share one: vanilla does it once)
        for (int k = 0, count = quads.length; k < count; k++) {
            int i = quads[k];
            int i3 = i * 3;
            if (held < 0 || !sameNormal(nrm, held * 3, i3)) {
                nm.transform(n.set(nrm[i3], nrm[i3 + 1], nrm[i3 + 2]));
                held = i;
            }
            m.transform(p.set(pos[i3] / 16.0F, pos[i3 + 1] / 16.0F, pos[i3 + 2] / 16.0F, 1.0F));
            consumer.vertex(p.x(), p.y(), p.z(), red, green, blue, alpha, uv[i * 2], uv[i * 2 + 1], overlay, light,
                    n.x(), n.y(), n.z());
        }
    }

    /** Bit-equal normals (so -0 and 0 are not mixed up: the transform keeps the sign of a zero). */
    private static boolean sameNormal(float[] nrm, int a, int b) {
        return Float.floatToRawIntBits(nrm[a]) == Float.floatToRawIntBits(nrm[b])
            && Float.floatToRawIntBits(nrm[a + 1]) == Float.floatToRawIntBits(nrm[b + 1])
            && Float.floatToRawIntBits(nrm[a + 2]) == Float.floatToRawIntBits(nrm[b + 2]);
    }
}
