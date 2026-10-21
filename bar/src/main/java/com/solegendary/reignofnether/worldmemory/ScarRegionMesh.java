package com.solegendary.reignofnether.worldmemory;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.solegendary.reignofnether.fogofwar.FogOfWarClientEvents;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.function.LongFunction;

/**
 * Builds one 64x64-block region's decal geometry from the client's field copies (GroundMemoryClient): per cell one
 * quad per active layer (scorch, crater), each on the column's real top face (GroundSurface), lit by the block light
 * above it, tinted like fogged terrain where the fog of war hides the column.
 * <p>
 * What makes it read as ground rather than discs (claude/world-that-remembers.md 4.1, 4.2, ART-FAULTS):
 * <ul>
 *   <li>Vertex alpha is sampled at the cell CORNERS (the mean of the four cells round the corner), so a stamp's edge
 *       fades across cells instead of stepping at block borders; a world-space value-noise threshold per corner makes
 *       the edge ragged and the faint residual patchy.</li>
 *   <li>The scorch colour is ash, never black: at full strength it darkens grass by ~40% and pulls the colour toward
 *       grey (the vertex colour multiplies a light ash texture whose char flecks carry the darkest spots).</li>
 *   <li>A crater needs no centre: the field's depth gradient is the rim. Where depth falls off, the colour swings to
 *       lighter thrown soil (the subsoil's dirt brown, tinted by the local ground); the flat core stays dark. Small
 *       craters therefore read as a dark middle with a pale ring at RTS zoom, and the rim shades as if dished.</li>
 *   <li>Texture coordinates are world space (x/4, z/4): neighbouring cells tile the same texture without seams.</li>
 * </ul>
 * Building is incremental: {@link #step} does a row budget per call and the caller swaps the finished buffers in, so
 * a 4096-cell region never costs one frame more than its budget. Client only.
 */
@OnlyIn(Dist.CLIENT)
final class ScarRegionMesh {

    static final int SIZE = 64, PAD = 1, W = SIZE + 2 * PAD;
    /** A hair above the face plus polygon offset in the render type: never coplanar with the ground. */
    static final float LIFT = 0.008f;
    static final float TILE = 4f;   // blocks per texture repeat

    // the art rule: never black. Ash at full strength darkens grass ~40% (class comment)
    static final int ASH_R = 64, ASH_G = 57, ASH_B = 50;
    static final float SCORCH_MAX_ALPHA = 0.66f;
    static final int CORE_R = 44, CORE_G = 37, CORE_B = 31;        // the crater's floor
    static final int SPRAY_R = 150, SPRAY_G = 112, SPRAY_B = 78;   // thrown subsoil, before the ground tint
    static final float CRATER_MAX_ALPHA = 0.78f, RIM_ALPHA = 0.62f;

    final int rx, rz, ox, oz;
    private final ClientLevel level;
    // the field round the region (one cell of padding for the corner samples), 0 where no chunk copy exists
    private final byte[] scorch = new byte[W * W];
    private final byte[] crater = new byte[W * W];
    private boolean anyMark = false;
    private boolean chunkMissing = false;
    private int row = 0;
    private int scorchQuads = 0, craterQuads = 0;
    private float minY = Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
    private final BufferBuilder scorchBuf, craterBuf;
    private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
    private final LevelChunk[] chunks = new LevelChunk[16];
    // per-cell scratch (no garbage per cell)
    private final float[] core = new float[4], rim = new float[4];
    private final int[] cr = new int[4], cg = new int[4], cb = new int[4];

    ScarRegionMesh(ClientLevel level, int rx, int rz, LongFunction<GroundMemory> fields, DecayParams params,
                   BufferBuilder scorchBuf, BufferBuilder craterBuf) {
        this.level = level;
        this.rx = rx;
        this.rz = rz;
        this.ox = rx << 6;
        this.oz = rz << 6;
        this.scorchBuf = scorchBuf;
        this.craterBuf = craterBuf;
        long now = level.getGameTime();
        int c0x = (ox - PAD) >> 4, c1x = (ox + SIZE) >> 4, c0z = (oz - PAD) >> 4, c1z = (oz + SIZE) >> 4;
        for (int cx = c0x; cx <= c1x; cx++)
            for (int cz = c0z; cz <= c1z; cz++) {
                GroundMemory mem = fields.apply(ChunkPos.asLong(cx, cz));
                if (mem == null || mem.isEmpty())
                    continue;
                mem.decayTo(now, params);
                boolean inside = cx >= (ox >> 4) && cx <= ((ox + SIZE - 1) >> 4) && cz >= (oz >> 4) && cz <= ((oz + SIZE - 1) >> 4);
                for (int lx = 0; lx < 16; lx++)
                    for (int lz = 0; lz < 16; lz++) {
                        int x = (cx << 4) + lx - ox + PAD, z = (cz << 4) + lz - oz + PAD;
                        if (x < 0 || z < 0 || x >= W || z >= W)
                            continue;
                        int cell = GroundMemory.index(lx, lz);
                        int s = mem.scorch(cell), d = mem.crater(cell);
                        scorch[z * W + x] = (byte) s;
                        crater[z * W + x] = (byte) d;
                        if (inside && (s != 0 || d != 0))
                            anyMark = true;
                    }
            }
        if (anyMark) {
            scorchBuf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.NEW_ENTITY);
            craterBuf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.NEW_ENTITY);
            for (int i = 0; i < 16; i++)
                chunks[i] = level.getChunkSource().getChunk((ox >> 4) + (i & 3), (oz >> 4) + (i >> 2), ChunkStatus.FULL, false);
        }
    }

    /** No cell of the region holds a value: nothing to build (the caller drops the region's buffers). */
    boolean isBlank() {
        return !anyMark;
    }

    /** A chunk of the region is not loaded on the client yet: its cells were skipped; rebuild when it arrives. */
    boolean chunkMissing() {
        return chunkMissing;
    }

    int scorchQuads() {
        return scorchQuads;
    }

    int craterQuads() {
        return craterQuads;
    }

    float minY() {
        return minY;
    }

    float maxY() {
        return maxY;
    }

    /** Builds up to {@code rows} more rows of cells; returns true when the region is complete. */
    boolean step(int rows) {
        if (!anyMark)
            return true;
        int end = Math.min(SIZE, row + rows);
        for (; row < end; row++)
            buildRow(row);
        return row >= SIZE;
    }

    private int field(byte[] f, int x, int z) {   // x, z in region cells, -1..64
        return f[(z + PAD) * W + (x + PAD)] & 0xFF;
    }

    /** The mean of the four cells round a corner (corner (x, z) touches cells x-1..x, z-1..z). */
    private float corner(byte[] f, int x, int z) {
        return (field(f, x - 1, z - 1) + field(f, x, z - 1) + field(f, x - 1, z) + field(f, x, z)) * 0.25f;
    }

    private void buildRow(int z) {
        int tint = FogOfWarClientEvents.FOG_TINT_RGB;
        boolean fogOn = FogOfWarClientEvents.isEnabled();
        for (int x = 0; x < SIZE; x++) {
            int s = field(scorch, x, z), d = field(crater, x, z);
            // a cell draws when it or any neighbour round its corners holds a value (the corner fade reaches in)
            boolean any = s != 0 || d != 0;
            for (int dz = -1; dz <= 1 && !any; dz++)
                for (int dx = -1; dx <= 1 && !any; dx++)
                    any = field(scorch, x + dx, z + dz) != 0 || field(crater, x + dx, z + dz) != 0;
            if (!any)
                continue;
            int wx = ox + x, wz = oz + z;
            LevelChunk chunk = chunks[(x >> 4) | ((z >> 4) << 2)];
            if (chunk == null) {
                chunkMissing = true;
                continue;
            }
            // the top block's Y + 1 (ChunkAccess.getHeight is the top block, not the first air: eyes run 39661116883), found
            // from the MOTION_BLOCKING map the server sends - the client's own NO_LEAVES map is zero until a block changes
            int top = GroundSurface.topBlockY(chunk, x & 15, z & 15) + 1;
            double h = GroundSurface.surfaceY(level, pos.set(wx, top - 1, wz));
            if (Double.isNaN(h))
                continue;
            float y = (float) h + LIFT;
            int light = LevelRenderer.getLightColor(level, pos.set(wx, top, wz));
            int ground = groundColour(wx, top - 1, wz);
            boolean fogged = fogOn && !FogOfWarClientEvents.isBlockVisible(wx, wz);
            boolean drew = false;
            // corner samples: 0 = (x,z) 1 = (x+1,z) 2 = (x+1,z+1) 3 = (x,z+1)
            float a0 = scorchAlpha(x, z), a1 = scorchAlpha(x + 1, z), a2 = scorchAlpha(x + 1, z + 1), a3 = scorchAlpha(x, z + 1);
            if (a0 + a1 + a2 + a3 > 0.004f) {
                int r = mix(ASH_R, (ground >> 16) & 255, 0.15f), g = mix(ASH_G, (ground >> 8) & 255, 0.15f), b = mix(ASH_B, ground & 255, 0.15f);
                if (fogged) {
                    r = fog(r, tint >> 16);
                    g = fog(g, tint >> 8);
                    b = fog(b, tint);
                }
                float u0 = Math.floorMod(wx, 64) / TILE, v0 = Math.floorMod(wz, 64) / TILE, u1 = u0 + 1 / TILE, v1 = v0 + 1 / TILE;
                vertex(scorchBuf, x, y, z, u0, v0, r, g, b, a0, light);
                vertex(scorchBuf, x, y, z + 1, u0, v1, r, g, b, a3, light);
                vertex(scorchBuf, x + 1, y, z + 1, u1, v1, r, g, b, a2, light);
                vertex(scorchBuf, x + 1, y, z, u1, v0, r, g, b, a1, light);
                scorchQuads++;
                drew = true;
            }
            core[0] = craterAlpha(x, z); core[1] = craterAlpha(x + 1, z); core[2] = craterAlpha(x + 1, z + 1); core[3] = craterAlpha(x, z + 1);
            rim[0] = rim(x, z); rim[1] = rim(x + 1, z); rim[2] = rim(x + 1, z + 1); rim[3] = rim(x, z + 1);
            if (core[0] + core[1] + core[2] + core[3] + rim[0] + rim[1] + rim[2] + rim[3] > 0.004f) {
                int sr = mix(SPRAY_R, (ground >> 16) & 255, 0.3f), sg = mix(SPRAY_G, (ground >> 8) & 255, 0.3f), sb = mix(SPRAY_B, ground & 255, 0.3f);
                int fr = CORE_R, fg = CORE_G, fb = CORE_B;
                if (fogged) {
                    sr = fog(sr, tint >> 16); sg = fog(sg, tint >> 8); sb = fog(sb, tint);
                    fr = fog(fr, tint >> 16); fg = fog(fg, tint >> 8); fb = fog(fb, tint);
                }
                for (int i = 0; i < 4; i++) {
                    float t = rim[i] / Math.max(0.001f, rim[i] + core[i] * 0.8f);   // the rim wins where the floor is faint
                    cr[i] = mix(fr, sr, t);
                    cg[i] = mix(fg, sg, t);
                    cb[i] = mix(fb, sb, t);
                    core[i] = Math.max(core[i], rim[i] * RIM_ALPHA);   // reused as the vertex alpha
                }
                float yc = y + LIFT * 0.5f;
                float u0 = Math.floorMod(wx, 64) / TILE, v0 = Math.floorMod(wz, 64) / TILE, u1 = u0 + 1 / TILE, v1 = v0 + 1 / TILE;
                vertex(craterBuf, x, yc, z, u0, v0, cr[0], cg[0], cb[0], core[0], light);
                vertex(craterBuf, x, yc, z + 1, u0, v1, cr[3], cg[3], cb[3], core[3], light);
                vertex(craterBuf, x + 1, yc, z + 1, u1, v1, cr[2], cg[2], cb[2], core[2], light);
                vertex(craterBuf, x + 1, yc, z, u1, v0, cr[1], cg[1], cb[1], core[1], light);
                craterQuads++;
                drew = true;
            }
            if (drew) {
                minY = Math.min(minY, y);
                maxY = Math.max(maxY, y + 0.1f);
            }
        }
    }

    /** Scorch alpha at a corner: a smooth ramp of the mean value over a ragged, world-anchored threshold. */
    private float scorchAlpha(int x, int z) {
        float v = corner(scorch, x, z) / 255f;
        if (v <= 0)
            return 0;
        float thr = 0.04f + 0.22f * noise(ox + x, oz + z, 11);
        if (v <= thr)
            return 0;
        float a = (v - thr) / (1 - thr);
        return (float) Math.pow(a, 0.7) * SCORCH_MAX_ALPHA;
    }

    /** The crater floor's alpha at a corner (depth 0-15). */
    private float craterAlpha(int x, int z) {
        float d = corner(crater, x, z) / 15f;
        if (d <= 0)
            return 0;
        float thr = 0.03f + 0.1f * noise(ox + x, oz + z, 23);
        if (d <= thr)
            return 0;
        return (float) Math.pow((d - thr) / (1 - thr), 0.6) * CRATER_MAX_ALPHA;
    }

    /** The rim at a corner: the depth gradient's magnitude, 0..1 (thrown soil shows where the dish falls off). */
    private float rim(int x, int z) {
        float gx = (field(crater, x, z - 1) + field(crater, x, z)) - (field(crater, x - 1, z - 1) + field(crater, x - 1, z));
        float gz = (field(crater, x - 1, z) + field(crater, x, z)) - (field(crater, x - 1, z - 1) + field(crater, x, z - 1));
        float g = (float) Math.sqrt(gx * gx + gz * gz) * 0.5f;   // depth units per block
        if (g <= 0)
            return 0;
        float r = Math.min(1, g / 5f);
        return r * (0.75f + 0.25f * noise(ox + x, oz + z, 37));
    }

    private static void vertex(VertexConsumer vc, float x, float y, float z, float u, float v, int r, int g, int b, float a, int light) {
        vc.vertex(x, y, z).color(r, g, b, Math.max(0, Math.min(255, Math.round(a * 255))))
            .uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(0, 1, 0).endVertex();
    }

    private int groundColour(int x, int y, int z) {
        try {
            int c = level.getBlockState(pos.set(x, y, z)).getMapColor(level, pos).col;
            return c == 0 ? 0x7A6A55 : c;
        } catch (Exception e) {
            return 0x7A6A55;
        }
    }

    static int mix(int a, int b, float t) {
        return Math.max(0, Math.min(255, Math.round(a + (b - a) * t)));
    }

    static int fog(int c, int tint) {
        return c * (tint & 255) / 255;
    }

    // ------------------------------------------------------------------ world-anchored value noise

    static float hash01(int x, int z, int salt) {
        int h = x * 374761393 + z * 668265263 + salt * 1274126177;
        h = (h ^ (h >>> 13)) * 1274126177;
        h ^= h >>> 16;
        return (h & 0xFFFFFF) / (float) 0x1000000;
    }

    /** Smooth value noise on a 3-block lattice, 0..1, the same for every client (it hashes world coordinates). */
    static float noise(int x, int z, int salt) {
        float fx = x / 3f, fz = z / 3f;
        int ix = (int) Math.floor(fx), iz = (int) Math.floor(fz);
        float tx = fx - ix, tz = fz - iz;
        tx = tx * tx * (3 - 2 * tx);
        tz = tz * tz * (3 - 2 * tz);
        float a = hash01(ix, iz, salt), b = hash01(ix + 1, iz, salt), c = hash01(ix, iz + 1, salt), d = hash01(ix + 1, iz + 1, salt);
        return (a + (b - a) * tx) * (1 - tz) + (c + (d - c) * tx) * tz;
    }
}
