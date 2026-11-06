package com.solegendary.reignofnether.worldmemory;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.config.ReignOfNetherClientConfigs;
import com.solegendary.reignofnether.orthoview.OrthoviewClientEvents;
import com.solegendary.reignofnether.time.TimeClientEvents;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.joml.Matrix4f;

import java.util.Set;

/**
 * The client side of the ground memory (claude/world-that-remembers.md 3.3): the field copies the server sends
 * ({@link #ingest}), kept per chunk independently of the chunk cache so a chunk out of view, out of range or under
 * the fog keeps the scars as last seen; and the decal mesh - one vertex buffer per 64x64-block region per layer
 * (trampled ground, scorch, craters), rebuilt when its field, its ground, its light or its fog changes, drawn with
 * vanilla's entity-translucent state so it looks the same with or without a shader pack.
 * <p>
 * Trample (slice 2) changes every sync pass while an army marches, so a change that touches only the trample channel
 * makes a region "soft" dirty: it is rebuilt at most every TrampleRules.TRAMPLE_REBUILD_MS. A scorch or crater change,
 * a block, the fog or a decay step makes it "hard" dirty and is rebuilt at once, as before.
 * <p>
 * Budgets: at most one region is (re)built per frame and the build is spread over frames in row steps
 * ({@link #ROWS_PER_STEP}); regions outside the frustum are not drawn; the client holds at most {@link #MAX_CHUNKS}
 * chunk copies (the oldest go first, like the fog memory). Decay runs on the copies with the server's own parameters
 * (DecayParams), one step every DecayParams.QUANTUM ticks, after which every region re-meshes over the following
 * frames. Rendering is called from BarFxClientEvents, under BarFx's own effects (the first seconds' glow and smoke
 * sit on top of the scar they leave).
 */
@OnlyIn(Dist.CLIENT)
public final class GroundMemoryClient {
    private GroundMemoryClient() { }

    static final int MAX_CHUNKS = 8192;
    /** Rows of a region built per frame: 8 rows x 64 cells = 512 cells, well inside the 0.6 ms build budget. */
    static final int ROWS_PER_STEP = 8;
    /** First-person draw range in blocks (the RTS view is bounded by its frustum). */
    static final double DRAW_RANGE = 160;
    /** A region whose chunks were missing is checked again this often (a safety net under the chunk load event). */
    static final int MISSING_RECHECK_TICKS = 40;

    private static final Minecraft MC = Minecraft.getInstance();
    private static final Long2ObjectLinkedOpenHashMap<GroundMemory> CHUNKS = new Long2ObjectLinkedOpenHashMap<>();
    private static final Long2ObjectOpenHashMap<Region> REGIONS = new Long2ObjectOpenHashMap<>();
    private static DecayParams params = DecayParams.DEFAULT;
    private static ClientLevel lastLevel = null;
    private static long lastQuantum = Long.MIN_VALUE;
    private static long lastMissingCheck = 0;
    private static final BufferBuilder SCORCH_BUF = new BufferBuilder(256 * 1024), CRATER_BUF = new BufferBuilder(256 * 1024),
        TRAMPLE_BUF = new BufferBuilder(256 * 1024);
    /**
     * Whether the trample layer is built and drawn (on in play; the eyes scene turns it off and on over the same pad,
     * so the layer's frame cost is measured inside one run). Flipping it re-meshes every region.
     */
    private static boolean drawTrample = true;
    private static ScarRegionMesh building = null;
    private static Region buildingRegion = null;
    private static boolean failedOnce = false;

    // stats for the eyes log and the frame profile
    static int quadsDrawn = 0, regionsDrawn = 0, regionsHeld = 0, rebuilds = 0;
    static double lastBuildMs = 0, lastDrawMs = 0;
    // the eyes scene's frame-cost window: build + draw milliseconds summed over the frames since costReset()
    private static double costMsSum = 0;
    private static int costFrames = 0;

    /** One 64x64 region's geometry. */
    static final class Region {
        final int rx, rz;
        VertexBuffer scorch, crater, trample;
        int scorchQuads, craterQuads, trampleQuads;
        float minY = 0, maxY = 0;
        boolean dirty = true;
        /** The dirtiness is a scorch / crater / block / fog / decay change (rebuild now), not trample alone. */
        boolean hard = true;
        long lastBuiltMs = Long.MIN_VALUE / 2;
        boolean chunkMissing = false;

        Region(int rx, int rz) {
            this.rx = rx;
            this.rz = rz;
        }

        void close() {
            if (scorch != null) {
                scorch.close();
                scorch = null;
            }
            if (crater != null) {
                crater.close();
                crater = null;
            }
            if (trample != null) {
                trample.close();
                trample = null;
            }
            scorchQuads = craterQuads = trampleQuads = 0;
        }
    }

    static {
        WorldMemoryHooks.listener = new WorldMemoryHooks.Listener() {
            @Override
            public void blockChanged(Level level, BlockPos pos) {
                GroundMemoryClient.onBlockChanged(pos.getX(), pos.getZ());
            }

            @Override
            public void fogChanged(Set<ChunkPos> chunks) {
                for (ChunkPos cp : chunks)
                    dirtyRegionsOfChunk(cp.x, cp.z);
            }
        };
    }

    /** The client setting (ground_scars): off = the field is kept but nothing is drawn, as before this slice. */
    public static boolean enabled() {
        try {
            return ReignOfNetherClientConfigs.GROUND_SCARS.get();
        } catch (Exception e) {
            return true;
        }
    }

    /** Wires the hooks (the static block above does it on first use; the registrar calls this at startup). */
    public static void init() {
        // nothing beyond the static initialiser
    }

    // ------------------------------------------------------------------ the field copies

    public static void ingest(GroundMemoryClientboundPacket packet) {
        if (MC.level == null || packet == null)
            return;
        try {
            if (!packet.params.equals(params))
                params = packet.params;
            for (GroundMemoryClientboundPacket.Update u : packet.updates)
                apply(u);
        } catch (Exception e) {
            if (!failedOnce) {
                failedOnce = true;
                ReignOfNether.LOGGER.error("World memory: bad update", e);
            }
        }
    }

    private static void apply(GroundMemoryClientboundPacket.Update u) {
        long key = ChunkPos.asLong(u.chunkX, u.chunkZ);
        GroundMemory mem = CHUNKS.getAndMoveToLast(key);
        boolean fresh = mem == null;
        if (mem == null) {
            mem = new GroundMemory(u.decayTick);
            CHUNKS.putAndMoveToLast(key, mem);
            while (CHUNKS.size() > MAX_CHUNKS) {
                long first = CHUNKS.firstLongKey();
                CHUNKS.removeFirst();
                dirtyRegionsOfChunk(ChunkPos.getX(first), ChunkPos.getZ(first));
            }
        }
        long before = fresh ? 0 : scarSignature(mem);
        u.applyTo(mem, params);
        dirtyRegionsOfChunk(u.chunkX, u.chunkZ, fresh || scarSignature(mem) != before);
    }

    /** A hash of every channel but trample: equal before and after an update = the update changed trample only. */
    static long scarSignature(GroundMemory mem) {
        byte[] c = mem.cells();
        if (c == null)
            return 0;
        long h = 1125899906842597L;
        for (int i = 0; i < c.length; i += GroundMemory.CHANNELS)
            h = 31 * (31 * (31 * h + c[i + GroundMemory.SCORCH]) + c[i + GroundMemory.CRATER_STAIN]) + c[i + GroundMemory.STAIN];
        return h;
    }

    /** The field copy of a chunk, or null. */
    public static GroundMemory chunk(int cx, int cz) {
        return CHUNKS.get(ChunkPos.asLong(cx, cz));
    }

    public static int heldChunks() {
        return CHUNKS.size();
    }

    static GroundMemory field(long chunkKey) {
        return CHUNKS.get(chunkKey);
    }

    // ------------------------------------------------------------------ dirtiness

    static long regionKey(int rx, int rz) {
        return ChunkPos.asLong(rx, rz);
    }

    private static void dirtyRegion(int rx, int rz, boolean hard) {
        long key = regionKey(rx, rz);
        Region r = REGIONS.get(key);
        if (r == null) {
            r = new Region(rx, rz);
            REGIONS.put(key, r);
        }
        if (!r.dirty)
            r.hard = hard;
        else
            r.hard |= hard;
        r.dirty = true;
    }

    /** The region holding a chunk, and the neighbours whose corner samples reach into it. */
    static void dirtyRegionsOfChunk(int cx, int cz) {
        dirtyRegionsOfChunk(cx, cz, true);
    }

    /** As above; {@code hard} = false for a change of the trample channel alone (rebuilt on the throttle). */
    static void dirtyRegionsOfChunk(int cx, int cz, boolean hard) {
        int rx = cx >> 2, rz = cz >> 2;
        int x0 = (cx & 3) == 0 ? rx - 1 : rx, x1 = (cx & 3) == 3 ? rx + 1 : rx;
        int z0 = (cz & 3) == 0 ? rz - 1 : rz, z1 = (cz & 3) == 3 ? rz + 1 : rz;
        for (int x = x0; x <= x1; x++)
            for (int z = z0; z <= z1; z++)
                dirtyRegion(x, z, hard);
    }

    /** Hard-dirties one region if it exists (a block or a decay step). */
    private static void hardDirty(Region r) {
        if (r != null) {
            r.dirty = true;
            r.hard = true;
        }
    }

    static void onBlockChanged(int x, int z) {
        if (REGIONS.isEmpty())
            return;
        int rx = x >> 6, rz = z >> 6;
        hardDirty(REGIONS.get(regionKey(rx, rz)));
        // a block on a region's edge changes the corner samples of the next region too
        if ((x & 63) == 0) hardDirty(REGIONS.get(regionKey(rx - 1, rz)));
        if ((x & 63) == 63) hardDirty(REGIONS.get(regionKey(rx + 1, rz)));
        if ((z & 63) == 0) hardDirty(REGIONS.get(regionKey(rx, rz - 1)));
        if ((z & 63) == 63) hardDirty(REGIONS.get(regionKey(rx, rz + 1)));
    }

    /** A client chunk arrived (WorldMemoryClientEvents): its regions need the real ground heights. */
    public static void onChunkLoaded(int cx, int cz) {
        if (CHUNKS.isEmpty())
            return;
        dirtyRegionsOfChunk(cx, cz);
    }

    public static void clear() {
        for (Region r : REGIONS.values())
            r.close();
        REGIONS.clear();
        CHUNKS.clear();
        building = null;
        buildingRegion = null;
        lastQuantum = Long.MIN_VALUE;
        quadsDrawn = regionsDrawn = regionsHeld = 0;
    }

    // ------------------------------------------------------------------ frame

    /** Called once per frame from BarFxClientEvents (AFTER_TRANSLUCENT_BLOCKS), before BarFx's own effects. */
    public static void render(PoseStack poseStack, Camera camera, Matrix4f projection, Frustum frustum) {
        ClientLevel level = MC.level;
        if (level == null) {
            if (!CHUNKS.isEmpty() || !REGIONS.isEmpty())
                clear();
            lastLevel = null;
            return;
        }
        if (level != lastLevel) {
            clear();
            lastLevel = level;
        }
        if (!enabled() || CHUNKS.isEmpty())
            return;
        long now = TimeClientEvents.getClientTime();   // the server stamps decay in its game time
        long q = Math.floorDiv(now, DecayParams.QUANTUM);
        if (q != lastQuantum) {
            if (lastQuantum != Long.MIN_VALUE)
                for (Region r : REGIONS.values())
                    hardDirty(r);   // a decay step: every region re-meshes over the next frames
            lastQuantum = q;
        }
        if (now - lastMissingCheck >= MISSING_RECHECK_TICKS) {
            lastMissingCheck = now;
            for (Region r : REGIONS.values())
                if (r.chunkMissing)
                    hardDirty(r);
        }
        Vec3 cam = camera.getPosition();
        long t0 = System.nanoTime();
        build(level, cam);
        lastBuildMs = (System.nanoTime() - t0) / 1e6;
        t0 = System.nanoTime();
        draw(poseStack, cam, projection, frustum);
        lastDrawMs = (System.nanoTime() - t0) / 1e6;
        costMsSum += lastBuildMs + lastDrawMs;
        costFrames++;
    }

    /** Turns the trample layer's building and drawing on or off (the eyes A/B); every region re-meshes. */
    public static void setDrawTrample(boolean on) {
        if (on == drawTrample)
            return;
        drawTrample = on;
        // every held chunk's regions, not only the regions that exist: a region holding trample alone was dropped as
        // blank while the layer was off, and must come back
        for (long key : CHUNKS.keySet())
            dirtyRegionsOfChunk(ChunkPos.getX(key), ChunkPos.getZ(key), true);
    }

    public static boolean drawTrample() {
        return drawTrample;
    }

    /** Starts a frame-cost window (the eyes scene: the same pad, trample off then on). */
    public static void costReset() {
        costMsSum = 0;
        costFrames = 0;
    }

    /** Mean build + draw milliseconds per frame since {@link #costReset}, and the frame count. */
    public static double[] costMean() {
        return new double[] { costFrames == 0 ? 0 : costMsSum / costFrames, costFrames };
    }

    /** Whether no region is still dirty or being built (the eyes scene waits for a settled mesh before measuring). */
    public static boolean settled() {
        if (building != null)
            return false;
        for (Region r : REGIONS.values())
            if (r.dirty)
                return false;
        return true;
    }

    /** Continues the region being built, or starts the nearest dirty one; at most one region per frame. */
    private static void build(ClientLevel level, Vec3 cam) {
        if (building == null) {
            Region pick = null;
            double best = Double.MAX_VALUE;
            long nowMs = System.currentTimeMillis();
            for (Region r : REGIONS.values()) {
                if (!r.dirty || !TrampleRules.rebuildDue(r.hard, nowMs, r.lastBuiltMs))
                    continue;
                double dx = (r.rx << 6) + 32 - cam.x, dz = (r.rz << 6) + 32 - cam.z;
                double d = dx * dx + dz * dz;
                if (d < best) {
                    best = d;
                    pick = r;
                }
            }
            if (pick == null)
                return;
            pick.dirty = false;
            pick.hard = false;
            pick.lastBuiltMs = nowMs;
            ScarRegionMesh mesh = new ScarRegionMesh(level, pick.rx, pick.rz, GroundMemoryClient::field, params, SCORCH_BUF, CRATER_BUF,
                TRAMPLE_BUF, drawTrample);
            if (mesh.isBlank()) {
                pick.close();
                REGIONS.remove(regionKey(pick.rx, pick.rz));
                return;
            }
            building = mesh;
            buildingRegion = pick;
        }
        if (!building.step(ROWS_PER_STEP))
            return;
        // complete: swap the buffers in
        Region r = buildingRegion;
        ScarRegionMesh mesh = building;
        building = null;
        buildingRegion = null;
        upload(r, mesh);
        rebuilds++;
    }

    private static void upload(Region r, ScarRegionMesh mesh) {
        BufferBuilder.RenderedBuffer scorch = SCORCH_BUF.end(), crater = CRATER_BUF.end(), trample = TRAMPLE_BUF.end();
        r.scorchQuads = mesh.scorchQuads();
        r.craterQuads = mesh.craterQuads();
        r.trampleQuads = mesh.trampleQuads();
        if (r.trampleQuads > 0) {
            if (r.trample == null)
                r.trample = new VertexBuffer(VertexBuffer.Usage.STATIC);
            r.trample.bind();
            r.trample.upload(trample);
        } else {
            trample.release();
            if (r.trample != null) {
                r.trample.close();
                r.trample = null;
            }
        }
        r.chunkMissing = mesh.chunkMissing();
        r.minY = mesh.minY();
        r.maxY = mesh.maxY();
        if (r.scorchQuads > 0) {
            if (r.scorch == null)
                r.scorch = new VertexBuffer(VertexBuffer.Usage.STATIC);
            r.scorch.bind();
            r.scorch.upload(scorch);
        } else {
            scorch.release();
            if (r.scorch != null) {
                r.scorch.close();
                r.scorch = null;
            }
        }
        if (r.craterQuads > 0) {
            if (r.crater == null)
                r.crater = new VertexBuffer(VertexBuffer.Usage.STATIC);
            r.crater.bind();
            r.crater.upload(crater);
        } else {
            crater.release();
            if (r.crater != null) {
                r.crater.close();
                r.crater = null;
            }
        }
        VertexBuffer.unbind();
    }

    private static boolean inView(Region r, Vec3 cam, Frustum frustum) {
        if (r.scorchQuads + r.craterQuads + r.trampleQuads == 0)
            return false;
        double x0 = r.rx << 6, z0 = r.rz << 6;
        if (!OrthoviewClientEvents.isEnabled()) {
            double dx = Math.max(0, Math.max(x0 - cam.x, cam.x - (x0 + 64)));
            double dz = Math.max(0, Math.max(z0 - cam.z, cam.z - (z0 + 64)));
            if (dx * dx + dz * dz > DRAW_RANGE * DRAW_RANGE)
                return false;
        }
        if (frustum == null)
            return true;
        return frustum.isVisible(new AABB(x0, r.minY - 0.5, z0, x0 + 64, r.maxY + 0.5, z0 + 64));
    }

    private static void draw(PoseStack poseStack, Vec3 cam, Matrix4f projection, Frustum frustum) {
        quadsDrawn = 0;
        regionsDrawn = 0;
        regionsHeld = REGIONS.size();
        if (REGIONS.isEmpty())
            return;
        Matrix4f pose = poseStack.last().pose();
        // the trampled ground lies under the scorch, the scorch under the craters (translucent, no depth write: the
        // draw order is the layering)
        if (drawTrample)
            drawLayer(ScarRenderTypes.TRAMPLE, LAYER_TRAMPLE, pose, cam, projection, frustum);
        drawLayer(ScarRenderTypes.SCORCH, LAYER_SCORCH, pose, cam, projection, frustum);
        drawLayer(ScarRenderTypes.CRATER, LAYER_CRATER, pose, cam, projection, frustum);
    }

    static final int LAYER_SCORCH = 0, LAYER_CRATER = 1, LAYER_TRAMPLE = 2;

    private static void drawLayer(RenderType type, int layer, Matrix4f pose, Vec3 cam, Matrix4f projection, Frustum frustum) {
        boolean any = false;
        ShaderInstance shader = null;
        Matrix4f mv = new Matrix4f();
        boolean scorchLayer = layer == LAYER_SCORCH;
        for (Region r : REGIONS.values()) {
            VertexBuffer vbo = layer == LAYER_SCORCH ? r.scorch : layer == LAYER_CRATER ? r.crater : r.trample;
            int quads = layer == LAYER_SCORCH ? r.scorchQuads : layer == LAYER_CRATER ? r.craterQuads : r.trampleQuads;
            if (vbo == null || quads == 0 || !inView(r, cam, frustum))
                continue;
            if (!any) {
                type.setupRenderState();
                shader = RenderSystem.getShader();
                any = true;
            }
            mv.set(pose).translate((float) ((r.rx << 6) - cam.x), (float) -cam.y, (float) ((r.rz << 6) - cam.z));
            vbo.bind();
            vbo.drawWithShader(mv, projection, shader);
            quadsDrawn += quads;
            if (scorchLayer)
                regionsDrawn++;
        }
        if (any) {
            VertexBuffer.unbind();
            type.clearRenderState();
        }
    }

    /** One line for the eyes log: what the client holds and draws. */
    public static String status() {
        int marked = 0;
        for (GroundMemory m : CHUNKS.values())
            if (m.hasAnyMark())
                marked++;
        return String.format(java.util.Locale.ROOT, "%d chunk copies (%d marked), %d regions (%d drawn, %d quads), rebuilds %d, last build %.2f ms, draw %.2f ms",
            CHUNKS.size(), marked, REGIONS.size(), regionsDrawn, quadsDrawn, rebuilds, lastBuildMs, lastDrawMs);
    }
}
