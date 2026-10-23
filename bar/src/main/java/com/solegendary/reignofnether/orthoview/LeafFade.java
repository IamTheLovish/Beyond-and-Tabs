package com.solegendary.reignofnether.orthoview;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.cursor.CursorClientEvents;
import com.solegendary.reignofnether.postfx.ShaderPacks;
import com.solegendary.reignofnether.registrars.BlockRegistrar;
import com.solegendary.reignofnether.unit.Relationship;
import com.solegendary.reignofnether.unit.UnitClientEvents;
import com.solegendary.reignofnether.util.MiscUtil;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.core.Vec3i;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * The FADE leaf mode of the RTS view: leaves fade out softly in a circle round the cursor and your selected units under
 * cover, replacing the old swap of every nearby leaf for green stained glass (lovish Oct 22: glass trees looked wrong,
 * and the swap re-meshed chunk sections on every cursor move).
 * <p>
 * Why this shape. A soft, per-pixel fade of terrain needs either a terrain shader we control - which a shader pack
 * replaces wholesale (Oculus draws terrain with the pack's own programs) - or vertex alpha baked into the chunk mesh,
 * which is a re-mesh per change. So the work is split in two:
 * <ol>
 * <li><b>Holes, re-meshed rarely.</b> Each anchor (the cursor, up to {@link #MAX_UNIT_ANCHORS} selected units under
 * cover) owns a hole: a cylinder along the view direction, {@link CanopyRules#HOLE_RADIUS} wide, centred where the
 * anchor was when the hole was made. Chunk meshing (RenderChunkRegionMixin for vanilla, EmbeddiumWorldSliceMixin for
 * Embeddium) reads the leaves inside any hole as air. A hole is only re-centred - and only the chunk sections that
 * held its leaves re-meshed - when its anchor drifts more than {@link CanopyRules#HOLE_DRIFT} blocks across the view
 * (never along it), at most every {@link #REMESH_TICKS} ticks.</li>
 * <li><b>A shell, drawn every frame.</b> The leaves inside the holes are drawn by us each frame as baked block quads
 * with per-vertex alpha: smoothstep from 0 within {@link CanopyRules#FADE_INNER} of the LIVE anchor to 1 beyond
 * {@link CanopyRules#FADE_OUTER}. The circle therefore glides with the cursor every frame with no re-mesh; the hole
 * just has to contain it. Only faces open to air are drawn (the surface of the canopy), back to front, through
 * vanilla's entity_translucent_cull shader on the mipmapped block atlas - which a shader pack replaces with its own
 * translucent-entity program, so under Oculus the fade is drawn and lit by the pack like any translucent entity.</li>
 * </ol>
 * A hole that is given up keeps its shell, drawn solid, for {@link #RETIRE_TICKS} ticks while the section re-mesh
 * that puts its leaves back completes, so a moving cursor never leaves a flickering gap behind it.
 * <p>
 * Costs: chunk meshing pays one volatile read per block while no mode is on, one instanceof per block otherwise; a
 * hole change re-meshes the few sections its leaves were in (typically 4-8). The shell is a few hundred to ~2,000
 * leaf blocks, ~1-2 faces each. Under a shader pack the shell is skipped in the pack's shadow pass (its geometry there
 * is not ours to vouch for), so the canopy's shadow keeps a gap the size of the hole.
 */
@OnlyIn(Dist.CLIENT)
public final class LeafFade {

    private LeafFade() {
    }

    private static final Minecraft MC = Minecraft.getInstance();

    /** Selected units under cover that get a fade of their own; every other unit gets its silhouette. */
    public static final int MAX_UNIT_ANCHORS = 8;
    /** At most one hole update (and its re-mesh) per this many ticks. */
    public static final int REMESH_TICKS = 4;
    /** A given-up hole's shell is drawn solid this long, while the re-mesh that restores its leaves runs. */
    public static final int RETIRE_TICKS = 12;
    private static final int CURSOR_ID = Integer.MIN_VALUE;
    // the cursor's fade centre chases the cursor's ground point at this rate (1/s), so the circle glides rather than
    // stepping a block at a time; a jump of more than SNAP blocks (a minimap click) snaps
    private static final double CURSOR_CHASE_RATE = 14.0, CURSOR_SNAP = 24.0;

    // ---- what the chunk meshers read (worker threads): immutable snapshots behind volatile fields ------------------

    private static final int MESH_NONE = 0, MESH_HOLES = 1, MESH_ALL = 2;
    private static volatile int meshMode = MESH_NONE;
    private static volatile Hole[] holes = new Hole[0];
    private static Block wartBlock = null;

    /** A cylinder along the view direction (dx, dy, dz: toward the camera) whose leaves the meshers read as air. */
    private static final class Hole {
        final int id;
        final double cx, cy, cz, dx, dy, dz;
        final int minX, minY, minZ, maxX, maxY, maxZ;   // conservative bounds: one compare rejects most blocks
        final LongOpenHashSet sections = new LongOpenHashSet();   // sections its leaves were found in (main thread)
        final LongArrayList blocks = new LongArrayList();          // shell keys it holds (main thread)

        Hole(int id, double cx, double cy, double cz, double dx, double dy, double dz) {
            this.id = id;
            this.cx = cx;
            this.cy = cy;
            this.cz = cz;
            this.dx = dx;
            this.dy = dy;
            this.dz = dz;
            double r = CanopyRules.HOLE_RADIUS + 1;
            double ax = cx - dx * CanopyRules.HOLE_BEHIND, bx = cx + dx * CanopyRules.HOLE_LENGTH;
            double ay = cy - dy * CanopyRules.HOLE_BEHIND, by = cy + dy * CanopyRules.HOLE_LENGTH;
            double az = cz - dz * CanopyRules.HOLE_BEHIND, bz = cz + dz * CanopyRules.HOLE_LENGTH;
            minX = Mth.floor(Math.min(ax, bx) - r);
            maxX = Mth.floor(Math.max(ax, bx) + r);
            minY = Mth.floor(Math.min(ay, by) - r);
            maxY = Mth.floor(Math.max(ay, by) + r);
            minZ = Mth.floor(Math.min(az, bz) - r);
            maxZ = Mth.floor(Math.max(az, bz) + r);
        }

        boolean contains(int x, int y, int z) {
            if (x < minX || x > maxX || y < minY || y > maxY || z < minZ || z > maxZ)
                return false;
            return CanopyRules.inHole(x + 0.5 - cx, y + 0.5 - cy, z + 0.5 - cz, dx, dy, dz, CanopyRules.HOLE_RADIUS);
        }
    }

    /** Any leaf hiding on at all: the meshers' one volatile read per block when the answer is no. */
    public static boolean meshActive() {
        return meshMode != MESH_NONE;
    }

    /** Leaves and the Gravebound's decaying nether wart canopy: the blocks the fade (and ALL) hide. */
    public static boolean isFadeable(BlockState state) {
        Block b = state.getBlock();
        if (b instanceof LeavesBlock)
            return true;
        Block wart = wartBlock;
        if (wart == null) {
            wart = BlockRegistrar.DECAYABLE_NETHER_WART_BLOCK.get();
            wartBlock = wart;
        }
        return b == wart;
    }

    /** Is a fadeable block at (x, y, z) left out of the chunk mesh? Any thread. */
    public static boolean hiddenInMesh(int x, int y, int z) {
        int mode = meshMode;
        if (mode == MESH_ALL)
            return true;
        if (mode == MESH_NONE)
            return false;
        for (Hole h : holes)
            if (h.contains(x, y, z))
                return true;
        return false;
    }

    /** A snow layer resting on a hidden leaf is hidden with it (it would float in the air otherwise). */
    public static boolean isSnowLayer(BlockState state) {
        return MiscUtil.isSnowLayerBlock(state.getBlock());
    }

    // ---- main thread ------------------------------------------------------------------------------------------------

    private static final Int2ObjectOpenHashMap<Hole> holeById = new Int2ObjectOpenHashMap<>();
    private static final Long2ObjectOpenHashMap<ShellBlock> shell = new Long2ObjectOpenHashMap<>();
    private static final List<double[]> anchors = new ArrayList<>();   // live anchor points this frame: x, y, z
    private static final Int2ObjectOpenHashMap<double[]> anchorById = new Int2ObjectOpenHashMap<>();
    private static double hdx = 0, hdy = 1, hdz = 0;   // the view direction the current holes were made along
    private static int tick = 0, lastRemesh = -1000;
    private static double cursorX = Double.NaN, cursorY, cursorZ;
    private static long lastFrameNanos = 0;
    private static boolean warned = false;

    /** One leaf block of the shell: its open faces as baked quads, each with its colour and light. */
    private static final class ShellBlock {
        final int x, y, z;
        final BakedQuad[] quads;
        final float[] rgb;     // 3 per quad: tint x face shade
        final int[] light;     // per quad: the light of the air the face looks into
        int refs = 0;
        int retireAt = Integer.MAX_VALUE;

        ShellBlock(int x, int y, int z, BakedQuad[] quads, float[] rgb, int[] light) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.quads = quads;
            this.rgb = rgb;
            this.light = light;
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent evt) {
        if (evt.phase != TickEvent.Phase.END)
            return;
        try {
            tickFade();
        } catch (RuntimeException e) {
            if (!warned) {
                warned = true;
                ReignOfNether.LOGGER.warn("Leaf fade: tick failed ({}), holes cleared", e.toString());
            }
            clearHoles();
        }
    }

    private static int wantedMode() {
        OrthoviewClientEvents.LeafHideMethod method = OrthoviewClientEvents.hideLeavesMethod;
        boolean rts = OrthoviewClientEvents.isEnabled() && MC.level != null && MC.player != null;
        // ALL hides every leaf whether or not the RTS view is open (as before); FADE only works in the RTS view
        return method == OrthoviewClientEvents.LeafHideMethod.ALL ? MESH_ALL
                : method == OrthoviewClientEvents.LeafHideMethod.FADE && rts ? MESH_HOLES : MESH_NONE;
    }

    /**
     * The HUD leaves button changed the mode: the meshers must see the new mode BEFORE the full re-mesh the button
     * asks for, or sections rebuilt in between keep the old mode until the next change.
     */
    public static void syncMode() {
        int want = wantedMode();
        if (want != MESH_HOLES && !holeById.isEmpty())
            clearHoles();
        meshMode = want;
    }

    private static void tickFade() {
        tick++;
        int want = wantedMode();
        if (want != MESH_HOLES && !holeById.isEmpty())
            clearHoles();
        meshMode = want;   // a switch to or from ALL re-meshes everything (the HUD button: resetFogChunks)
        // retired shell blocks whose re-mesh has had time to finish
        if (!shell.isEmpty())
            shell.values().removeIf(sb -> sb.refs <= 0 && sb.retireAt <= tick);
        if (want != MESH_HOLES)
            return;

        Vector3f look = MC.gameRenderer.getMainCamera().getLookVector();
        double dx = -look.x(), dy = -look.y(), dz = -look.z();
        boolean turned = CanopyRules.viewTurned(dx, dy, dz, hdx, hdy, hdz);

        // the anchors this tick
        anchorById.clear();
        Vector3d c = CursorClientEvents.getCursorWorldPos();
        if (c != null && (c.x != 0 || c.y != 0 || c.z != 0))
            anchorById.put(CURSOR_ID, new double[] { c.x, c.y, c.z });
        int units = 0;
        for (LivingEntity e : UnitClientEvents.getSelectedUnits()) {
            if (units >= MAX_UNIT_ANCHORS)
                break;
            if (UnitClientEvents.getPlayerToEntityRelationship(e) != Relationship.OWNED || !CanopySilhouettes.isUnderCover(e))
                continue;
            anchorById.put(e.getId(), new double[] { e.getX(), e.getY(), e.getZ() });
            units++;
        }

        if (tick - lastRemesh < REMESH_TICKS)
            return;
        // which holes must change: gone, drifted, or all of them when the camera turned
        List<Hole> drop = new ArrayList<>();
        List<double[]> add = new ArrayList<>();
        List<Integer> addIds = new ArrayList<>();
        for (Hole h : holeById.values())
            if (turned || !anchorById.containsKey(h.id))
                drop.add(h);
        for (var entry : anchorById.int2ObjectEntrySet()) {
            double[] a = entry.getValue();
            Hole h = holeById.get(entry.getIntKey());
            if (h != null && !turned && !CanopyRules.needsRecentre(a[0], a[1], a[2], h.cx, h.cy, h.cz, dx, dy, dz))
                continue;
            if (h != null && !drop.contains(h))
                drop.add(h);
            add.add(a);
            addIds.add(entry.getIntKey());
        }
        if (drop.isEmpty() && add.isEmpty())
            return;
        lastRemesh = tick;
        hdx = dx;
        hdy = dy;
        hdz = dz;

        LongOpenHashSet dirty = new LongOpenHashSet();
        for (Hole h : drop) {
            holeById.remove(h.id);
            dirty.addAll(h.sections);
            release(h);
        }
        for (int i = 0; i < add.size(); i++) {
            double[] a = add.get(i);
            Hole h = new Hole(addIds.get(i), a[0], a[1], a[2], dx, dy, dz);
            scan(h);
            holeById.put(h.id, h);
            dirty.addAll(h.sections);
        }
        holes = holeById.values().toArray(new Hole[0]);   // publish before marking: a rebuild must see the new set
        markDirty(dirty);
    }

    /** Finds the leaves inside a new hole: its shell blocks and the sections whose meshes it changes. */
    private static void scan(Hole h) {
        ClientLevel level = MC.level;
        if (level == null)
            return;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = h.minX; x <= h.maxX; x++) {
            for (int z = h.minZ; z <= h.maxZ; z++) {
                // nothing that blocks motion (leaves do) above the column's height map: stop there
                int top = Math.min(h.maxY, level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) - 1);
                for (int y = h.minY; y <= top; y++) {
                    if (!h.contains(x, y, z))
                        continue;
                    BlockState state = level.getBlockState(pos.set(x, y, z));
                    if (!isFadeable(state))
                        continue;
                    h.sections.add(SectionPos.asLong(x >> 4, y >> 4, z >> 4));
                    long key = BlockPos.asLong(x, y, z);
                    ShellBlock sb = shell.get(key);
                    if (sb == null) {
                        sb = build(level, state, pos);
                        if (sb == null)
                            continue;
                        shell.put(key, sb);
                    }
                    sb.refs++;
                    sb.retireAt = Integer.MAX_VALUE;
                    h.blocks.add(key);
                }
            }
        }
    }

    private static void release(Hole h) {
        for (int i = 0, n = h.blocks.size(); i < n; i++) {
            ShellBlock sb = shell.get(h.blocks.getLong(i));
            if (sb != null && --sb.refs <= 0)
                sb.retireAt = tick + RETIRE_TICKS;
        }
        h.blocks.clear();
    }

    private static final Direction[] DIRECTIONS = Direction.values();
    private static final RandomSource RANDOM = RandomSource.create();

    /** The open faces of one leaf block, as the chunk mesher would have drawn them (same random model variant). */
    private static ShellBlock build(ClientLevel level, BlockState state, BlockPos pos) {
        BakedModel model = MC.getBlockRenderer().getBlockModel(state);
        long seed = state.getSeed(pos);
        List<BakedQuad> quads = new ArrayList<>();
        List<Direction> faceDir = new ArrayList<>();
        BlockPos.MutableBlockPos n = new BlockPos.MutableBlockPos();
        for (Direction d : DIRECTIONS) {
            n.setWithOffset(pos, d);
            BlockState ns = level.getBlockState(n);
            // a face against another leaf or a full solid block stays undrawn: leaves inside the hole fade together,
            // and leaves outside it are in the mesh, drawing their own face toward the hole
            if (isFadeable(ns) || ns.isSolidRender(level, n))
                continue;
            RANDOM.setSeed(seed);
            for (BakedQuad q : model.getQuads(state, d, RANDOM, ModelData.EMPTY, null)) {
                quads.add(q);
                faceDir.add(d);
            }
        }
        RANDOM.setSeed(seed);
        for (BakedQuad q : model.getQuads(state, null, RANDOM, ModelData.EMPTY, null)) {
            quads.add(q);
            faceDir.add(q.getDirection());
        }
        if (quads.isEmpty())
            return null;
        float[] rgb = new float[quads.size() * 3];
        int[] light = new int[quads.size()];
        for (int i = 0; i < quads.size(); i++) {
            BakedQuad q = quads.get(i);
            float r = 1f, g = 1f, b = 1f;
            if (q.isTinted()) {
                int col = MC.getBlockColors().getColor(state, level, pos, q.getTintIndex());
                if (col != -1) {
                    r = ((col >> 16) & 0xFF) / 255f;
                    g = ((col >> 8) & 0xFF) / 255f;
                    b = (col & 0xFF) / 255f;
                }
            }
            float shade = level.getShade(q.getDirection(), q.isShade());
            rgb[i * 3] = r * shade;
            rgb[i * 3 + 1] = g * shade;
            rgb[i * 3 + 2] = b * shade;
            light[i] = LevelRenderer.getLightColor(level, n.setWithOffset(pos, faceDir.get(i)));
        }
        return new ShellBlock(pos.getX(), pos.getY(), pos.getZ(), quads.toArray(new BakedQuad[0]), rgb, light);
    }

    private static void markDirty(LongOpenHashSet sections) {
        if (MC.levelRenderer == null)
            return;
        // setSectionDirty reaches Embeddium's renderer too (it overwrites the vanilla method with its own scheduler)
        for (long s : sections)
            MC.levelRenderer.setSectionDirty(SectionPos.x(s), SectionPos.y(s), SectionPos.z(s));
    }

    private static void clearHoles() {
        LongOpenHashSet dirty = new LongOpenHashSet();
        for (Hole h : holeById.values()) {
            dirty.addAll(h.sections);
            release(h);
        }
        holeById.clear();
        holes = new Hole[0];
        markDirty(dirty);
        cursorX = Double.NaN;
    }

    // ---- the shell, every frame -------------------------------------------------------------------------------------

    private static final Matrix4f basePose = new Matrix4f();
    private static final Matrix3f baseNormal = new Matrix3f();

    // HIGH: before the other overlays of this stage (health bars, scaffolds), so the faded canopy sits under them
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onRenderLevel(RenderLevelStageEvent evt) {
        if (evt.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS)
            return;
        if (meshMode != MESH_HOLES || shell.isEmpty() || MC.level == null)
            return;
        if (ShaderPacks.renderingShadowPass())
            return;
        int fp = com.solegendary.reignofnether.perf.FrameParts.begin(com.solegendary.reignofnether.perf.FrameParts.CANOPY);
        try {
            drawShell(evt);
        } catch (RuntimeException e) {
            if (!warned) {
                warned = true;
                ReignOfNether.LOGGER.warn("Leaf fade: shell draw failed ({})", e.toString());
            }
        } finally {
            com.solegendary.reignofnether.perf.FrameParts.end(fp);
        }
    }

    private static void drawShell(RenderLevelStageEvent evt) {
        Vector3f look = evt.getCamera().getLookVector();
        double dx = -look.x(), dy = -look.y(), dz = -look.z();
        liveAnchors(evt.getPartialTick());
        Vec3 cam = evt.getCamera().getPosition();

        MultiBufferSource.BufferSource buffers = MC.renderBuffers().bufferSource();
        VertexConsumer vc = buffers.getBuffer(CanopyRenderTypes.LEAF_FADE);
        PoseStack matrix = evt.getPoseStack();
        matrix.pushPose();
        PoseStack.Pose pose = matrix.last();
        basePose.set(pose.pose());
        baseNormal.set(pose.normal());
        int overlay = OverlayTexture.NO_OVERLAY;
        try {
            float[] va = new float[4];
            for (ShellBlock sb : shell.values()) {
                // a whole block clear of every soft circle is skipped; a whole block beyond them is drawn solid
                double reach = 0.9;   // half a block's diagonal: a corner is at most this much nearer or further
                boolean uniform = alphaAt(sb.x + 0.5, sb.y + 0.5, sb.z + 0.5, dx, dy, dz, reach) >= 1f;
                if (!uniform && alphaAt(sb.x + 0.5, sb.y + 0.5, sb.z + 0.5, dx, dy, dz, -reach) < 0.02f)
                    continue;
                pose.pose().set(basePose).translate((float) (sb.x - cam.x), (float) (sb.y - cam.y), (float) (sb.z - cam.z));
                for (int i = 0; i < sb.quads.length; i++) {
                    BakedQuad q = sb.quads[i];
                    int[] v = q.getVertices();
                    // alpha per VERTEX, from its own distance to the circles: the fade runs smoothly across a block
                    // instead of stepping a whole block at a time (round 1 looked like missing chunks)
                    float max = 0f;
                    for (int k = 0; k < 4; k++) {
                        int o = k * STRIDE;
                        va[k] = uniform ? 1f : alphaAt(sb.x + Float.intBitsToFloat(v[o]), sb.y + Float.intBitsToFloat(v[o + 1]),
                                sb.z + Float.intBitsToFloat(v[o + 2]), dx, dy, dz);
                        max = Math.max(max, va[k]);
                    }
                    if (max < 0.02f)
                        continue;
                    Vec3i nrm = q.getDirection().getNormal();
                    float r = sb.rgb[i * 3], g = sb.rgb[i * 3 + 1], b = sb.rgb[i * 3 + 2];
                    for (int k = 0; k < 4; k++) {
                        int o = k * STRIDE;
                        vc.vertex(pose.pose(), Float.intBitsToFloat(v[o]), Float.intBitsToFloat(v[o + 1]), Float.intBitsToFloat(v[o + 2]))
                                .color(r, g, b, va[k])
                                .uv(Float.intBitsToFloat(v[o + 4]), Float.intBitsToFloat(v[o + 5]))
                                .overlayCoords(overlay)
                                .uv2(sb.light[i])
                                .normal(pose.normal(), nrm.getX(), nrm.getY(), nrm.getZ())
                                .endVertex();
                    }
                }
            }
        } finally {
            pose.pose().set(basePose);
            pose.normal().set(baseNormal);
            matrix.popPose();
            buffers.endBatch(CanopyRenderTypes.LEAF_FADE);
        }
    }

    // a baked quad's vertex: position (3 floats), colour, uv (2 floats), light, normal - DefaultVertexFormat.BLOCK
    private static final int STRIDE = 8;

    /** The fade's alpha at a world point: the softest (lowest) over every live anchor's circle. */
    private static float alphaAt(double x, double y, double z, double dx, double dy, double dz) {
        return alphaAt(x, y, z, dx, dy, dz, 0);
    }

    /** ... with the point's distance to each circle's axis shifted by {@code bias} blocks (a whole block's bounds). */
    private static float alphaAt(double x, double y, double z, double dx, double dy, double dz, double bias) {
        float alpha = 1f;
        for (int a = 0, n = anchors.size(); a < n && alpha > 0f; a++) {
            double[] p = anchors.get(a);
            double sq = CanopyRules.perpDistSq(x - p[0], y - p[1], z - p[2], dx, dy, dz,
                    CanopyRules.HOLE_BEHIND, CanopyRules.HOLE_LENGTH);
            if (sq != Double.MAX_VALUE)
                alpha = Math.min(alpha, CanopyRules.fadeAlpha(Math.max(0, Math.sqrt(sq) - bias)));
        }
        return alpha;
    }

    /** This frame's anchor points: the cursor's smoothed ground point and the selected units' interpolated feet. */
    private static void liveAnchors(float partialTick) {
        anchors.clear();
        long now = System.nanoTime();
        double dt = lastFrameNanos == 0 ? 1.0 : Math.min((now - lastFrameNanos) / 1e9, 1.0);
        lastFrameNanos = now;
        double[] c = anchorById.get(CURSOR_ID);
        Vector3d live = CursorClientEvents.getCursorWorldPos();
        if (c != null && live != null) {
            if (Double.isNaN(cursorX) || Math.abs(live.x - cursorX) + Math.abs(live.z - cursorZ) > CURSOR_SNAP) {
                cursorX = live.x;
                cursorY = live.y;
                cursorZ = live.z;
            } else {
                double k = 1.0 - Math.exp(-CURSOR_CHASE_RATE * dt);
                cursorX += (live.x - cursorX) * k;
                cursorY += (live.y - cursorY) * k;
                cursorZ += (live.z - cursorZ) * k;
            }
            anchors.add(new double[] { cursorX, cursorY, cursorZ });
        }
        if (MC.level == null)
            return;
        for (var entry : anchorById.int2ObjectEntrySet()) {
            if (entry.getIntKey() == CURSOR_ID)
                continue;
            if (MC.level.getEntity(entry.getIntKey()) instanceof LivingEntity e)
                anchors.add(new double[] { Mth.lerp(partialTick, e.xOld, e.getX()), Mth.lerp(partialTick, e.yOld, e.getY()),
                        Mth.lerp(partialTick, e.zOld, e.getZ()) });
            else
                anchors.add(entry.getValue());
        }
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut evt) {
        holeById.clear();
        holes = new Hole[0];
        shell.clear();
        anchorById.clear();
        anchors.clear();
        cursorX = Double.NaN;
    }
}
