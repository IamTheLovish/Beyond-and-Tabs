package com.solegendary.reignofnether.unit.modelling.mesh;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.ModelPart;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The .bmesh version 2 (tools/mesh_import.py): a flags byte before each LOD's vertices, and with bit 0 an RGBA colour
 * after every vertex's uv - a sculpt's baked marble and occlusion (tools/sculpt_angel.py), multiplied into the draw
 * colour by MeshCube. A LOD without colours draws exactly as version 1.
 */
class MeshBodyColourTest {
    private static final int LIGHT = 15728880, OVERLAY = 655360;

    private static ByteBuffer le(int n) {
        return ByteBuffer.allocate(n).order(ByteOrder.LITTLE_ENDIAN);
    }

    /** One part, one LOD: a quad whose corners carry the given colours (or none), version 2. */
    private static byte[] body(int[][] rgba, int flags) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes("BMSH".getBytes(StandardCharsets.US_ASCII));
        out.writeBytes(le(8).putShort((short) 2).putShort((short) 64).putShort((short) 64).putShort((short) 1).array());
        byte[] name = "body".getBytes(StandardCharsets.UTF_8);
        out.write(name.length);
        out.writeBytes(name);
        out.writeBytes(le(2).putShort((short) -1).array());
        ByteBuffer pose = le(24);
        for (int i = 0; i < 6; i++)
            pose.putFloat(0f);
        out.writeBytes(pose.array());
        out.write(1);                      // one LOD
        out.write(flags);
        float[][] v = {{0, 0, 0}, {4, 0, 0}, {4, 4, 0}, {0, 4, 0}};
        out.writeBytes(le(4).putInt(4).array());
        for (int i = 0; i < 4; i++) {
            ByteBuffer b = le(32);
            b.putFloat(v[i][0]).putFloat(v[i][1]).putFloat(v[i][2]).putFloat(0f).putFloat(0f).putFloat(-1f)
                .putFloat(i * 0.25f).putFloat(0.5f);
            out.writeBytes(b.array());
            if ((flags & 1) != 0)
                for (int c = 0; c < 4; c++)
                    out.write(rgba[i][c]);
        }
        out.writeBytes(le(4).putInt(1).array());
        out.writeBytes(le(16).putInt(0).putInt(1).putInt(2).putInt(3).array());
        out.writeBytes(le(24).putFloat(0).putFloat(0).putFloat(0).putFloat(4).putFloat(4).putFloat(0).array());
        return out.toByteArray();
    }

    private static final int[][] COLOURS = {{255, 255, 255, 255}, {128, 64, 0, 255}, {0, 0, 0, 255}, {255, 0, 255, 51}};

    @Test
    void readsColours() throws IOException {
        MeshBody.Lod lod = MeshBody.read(body(COLOURS, 1)).part(0).lod(0);
        assertTrue(lod.hasColours());
        assertEquals(128 / 255f, lod.col[4]);
        assertEquals(51 / 255f, lod.col[15]);
        assertFalse(MeshBody.read(body(COLOURS, 0)).part(0).lod(0).hasColours(), "flag clear: no colours");
    }

    @Test
    void refusesUnknownFlagsAndShortColours() {
        assertThrows(IOException.class, () -> MeshBody.read(body(COLOURS, 2)));
        byte[] good = body(COLOURS, 1);
        assertThrows(IOException.class, () -> MeshBody.read(Arrays.copyOf(good, good.length - 30)));
    }

    @Test
    void colourMultipliesTheDrawColour() throws IOException {
        ModelPart root = MeshBody.read(body(COLOURS, 1)).buildTree(0);
        MeshBodyTest.Recorder r = new MeshBodyTest.Recorder();
        root.render(new PoseStack(), r, LIGHT, OVERLAY, 1f, 0.5f, 1f, 1f);
        int[] s = r.stream();
        assertEquals(4, r.vertices());
        // vertex 1: (128, 64, 0, 255) x (1, 0.5, 1, 1)
        assertEquals(128 / 255f, Float.intBitsToFloat(s[14 + 3]));
        assertEquals(0.5f * (64 / 255f), Float.intBitsToFloat(s[14 + 4]));
        assertEquals(0f, Float.intBitsToFloat(s[14 + 5]));
        // vertex 3's alpha: 51 / 255
        assertEquals(51 / 255f, Float.intBitsToFloat(s[3 * 14 + 6]));
    }

    @Test
    void noColoursDrawsTheDrawColour() throws IOException {
        ModelPart root = MeshBody.read(body(COLOURS, 0)).buildTree(0);
        MeshBodyTest.Recorder r = new MeshBodyTest.Recorder();
        root.render(new PoseStack(), r, LIGHT, OVERLAY, 1f, 0.5f, 0.25f, 1f);
        int[] s = r.stream();
        for (int v = 0; v < 4; v++) {
            assertEquals(1f, Float.intBitsToFloat(s[v * 14 + 3]));
            assertEquals(0.5f, Float.intBitsToFloat(s[v * 14 + 4]));
            assertEquals(0.25f, Float.intBitsToFloat(s[v * 14 + 5]));
        }
    }
}
