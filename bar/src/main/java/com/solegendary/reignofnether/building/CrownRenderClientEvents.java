package com.solegendary.reignofnether.building;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.config.DisplaySettings;
import com.solegendary.reignofnether.environment.wind.WindField;
import com.solegendary.reignofnether.faction.FactionTraits;
import com.solegendary.reignofnether.fogofwar.FogOfWarClientEvents;
import com.solegendary.reignofnether.orthoview.StrategicViewClientEvents;
import com.solegendary.reignofnether.postfx.ShaderPacks;
import com.solegendary.reignofnether.resources.Wind;
import com.solegendary.reignofnether.time.ClientTicks;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;

/**
 * Living crowns (lovish, Nov 4: "ardent [Verdant] buildings can have trees growing out of them with animations"; he
 * picked "Living crowns", sway "Gentle"; the rules are {@link CrownMotion}). The leaf crowns the Verdant buildings
 * already carry stay ordinary blocks for the server, the HP, the paths and the saves; on each client, near the camera,
 * they leave the chunk mesh ({@link CrownBakedModel} reads {@link #hides}) and are drawn here from a copy vanilla
 * tessellated once - its own light, ambient occlusion, face shade and tint (the fog tint too) - so they can move: the
 * crown leans with the shared map wind, a leaf the builders lay grows out of its cell, and the crown swells once with a
 * burst of petals when the building completes or levels up.
 * <ul>
 *   <li><b>Which:</b> built or building Verdant placements ({@link CrownMotion#animates}: the Heartwood Hall pilot
 *       first), explored, within {@link #RANGE} blocks of the camera (kept to {@link #KEEP}, so a pan does not
 *       re-mesh), nearest first within a vertex budget (Full {@link #BUDGET_FULL}, Low {@link #BUDGET_LOW}); never in
 *       strategic zoom and never with "Building animation" Off - then every crown is back in the mesh, at no cost.</li>
 *   <li><b>When:</b> the list, which cells stand, fog and completion every {@link #REFRESH_TICKS} ticks (every
 *       {@link #BUILD_TICKS} for crowns being built, so a laid leaf starts growing at once); a crown is tessellated
 *       again only when a cell comes or goes, the fog changes, or every {@link #RECAPTURE_TICKS} (its neighbours' light);
 *       the frame poses and writes vertices, nothing else, and allocates nothing.</li>
 *   <li><b>Hand-over:</b> a crown that joins or leaves marks its sections for a re-mesh; the mesh rebuild is
 *       asynchronous, so for a frame or two both copies draw (at rest they coincide) - the branch wind's trade.</li>
 *   <li><b>Lanterns</b> (lovish's Verdant reference, Nov 4: a tower grown inside a great tree, lanterns on long chains
 *       from its branches, a purple-pink glow): {@link CrownMotion#lanterns} hangs a few under each big crown's open
 *       underside; drawn ({@link LanternMesh}: a small pearlescent froglight, fullbright, on a two-link vanilla chain -
 *       they light nothing), they swing with the same wind and ride the crown's lean, once the building stands.</li>
 *   <li><b>Fog:</b> a crown out of sight keeps the pose it was last seen in.</li>
 *   <li><b>Shaders:</b> Oculus fires the render stages again for its shadow map (ShaderPacks.skipOverlays): the crown
 *       draws there too, unculled, so it keeps its shadow.</li>
 * </ul>
 * Any error switches the crowns off for the session (every crown back in the mesh) with one WARN line.
 */
public class CrownRenderClientEvents {

    static final Minecraft MC = Minecraft.getInstance();

    /** Horizontal blocks from the camera a crown is taken within, and kept within once taken. */
    static final double RANGE = 160, KEEP = 184;
    /** Vertices a frame the crowns may cost (nearest first): ~0.5 ms / ~0.2 ms by CrownCostBench's rate. */
    static final int BUDGET_FULL = 48_000, BUDGET_LOW = 16_000;
    static final int REFRESH_TICKS = 10, BUILD_TICKS = 2, RECAPTURE_TICKS = 200;
    /** The petals shed when a crown swells. */
    private static final int PETALS = 24;
    /** The culling box's margin round the cells: the largest push (~0.6) and the swell, with room. */
    private static final double BOX_MARGIN = 1.5;

    private static final LongOpenHashSet NONE = new LongOpenHashSet();
    /** The cells the crowns draw: read by meshing threads, so never changed once published - replaced whole. */
    private static volatile LongOpenHashSet hidden = NONE;

    /** True when the chunk mesh must leave this leaf cell out (BlockPos.asLong): a living crown draws it. */
    public static boolean hides(long pos) {
        LongOpenHashSet h = hidden;
        return !h.isEmpty() && h.contains(pos);
    }

    /** A building's crowns on this client. Holds no reference to its placement (it is a WeakHashMap value). */
    private static final class Tree {
        String structure;
        int blockCount = -1;
        int[] cx = new int[0], cy = new int[0], cz = new int[0], crownOf = new int[0];
        List<CrownMotion.Crown> crowns = List.of();
        float[] bornAt = new float[0], gust = new float[0];
        boolean[] standing = new boolean[0];
        int standingCount;
        final int[] anchor = new int[3];
        int minX, minY, minZ, maxX, maxY, maxZ;
        AABB box;
        double px, pz;               // the biggest crown's pivot: the distance pick
        double dist;
        boolean seen, built, seenUnbuilt, taken, dirty;
        boolean visible = true;
        float frozenAt = Float.NaN;  // the clock (ticks) it was last seen at, while fogged
        float swellAt = -1e9f;
        long capturedAt = Long.MIN_VALUE, releaseUntil;
        CrownMesh mesh;
        RenderType[] types = new RenderType[0];
        float[] out = new float[0];
        // the lanterns hung under the crowns: {x, y, z, crown} of the leaf cell each hangs from
        List<int[]> lanterns = List.of();
        LanternMesh lanternMesh;
        RenderType[] lanternTypes = new RenderType[0];
        float[] lanternOut = new float[0], sway = new float[0];
    }

    private static final Map<BuildingPlacement, Tree> TREES = new WeakHashMap<>();
    private static final ArrayList<Tree> TAKEN = new ArrayList<>();
    /** Crowns just handed back to the mesh, still drawn until their re-mesh has landed. */
    private static final ArrayList<Tree> RELEASING = new ArrayList<>();
    private static final ArrayList<Tree> CANDIDATES = new ArrayList<>();
    private static final Comparator<Tree> NEAREST = Comparator.comparingDouble(t -> t.dist);
    private static int[] candidateVerts = new int[64];
    private static long refreshedAt = Long.MIN_VALUE;
    private static final BlockPos.MutableBlockPos MUT = new BlockPos.MutableBlockPos();
    private static final RandomSource RANDOM = RandomSource.create();

    // per draw (render thread only)
    private static final Matrix4f BASE = new Matrix4f();
    private static final Matrix3f BASE_N = new Matrix3f();
    private static final Vector3f POS = new Vector3f(), NRM = new Vector3f();
    private static final ArrayList<RenderType> USED = new ArrayList<>();

    private static boolean disabled = false, firstDrawLogged = false;
    // eyes tours only: a [Crowns] timing line every 20 s
    private static long nanosTotal = 0, nanosPeak = 0, timingFrom = 0;
    private static int timedFrames = 0, vertsPeak = 0, treesPeak = 0;

    // ---------------------------------------------------------------------------------------------------------------
    // the client tick: which crowns, which cells stand, completion, fog; captures
    // ---------------------------------------------------------------------------------------------------------------

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent evt) {
        if (evt.phase != TickEvent.Phase.END)
            return;
        if (MC.level == null) {
            TREES.clear();
            TAKEN.clear();
            RELEASING.clear();
            hidden = NONE;
            refreshedAt = Long.MIN_VALUE;
            return;
        }
        if (disabled)
            return;
        try {
            long now = ClientTicks.now();
            if (refreshedAt == Long.MIN_VALUE || now - refreshedAt >= REFRESH_TICKS || now < refreshedAt) {
                refreshedAt = now;
                refresh(now);
            } else if (now % BUILD_TICKS == 0) {
                trackBuilding(now);
            }
        } catch (RuntimeException | LinkageError e) {
            disable(e);
        }
    }

    private static boolean on() {
        return DisplaySettings.buildingAnimation() != DisplaySettings.ANIM_OFF && !StrategicViewClientEvents.isStrategicView();
    }

    private static void refresh(long now) {
        CANDIDATES.clear();
        if (on()) {
            Vec3 cam = MC.gameRenderer.getMainCamera().getPosition();
            List<BuildingPlacement> buildings = BuildingClientEvents.getBuildings();
            for (int i = 0; i < buildings.size(); i++) {
                BuildingPlacement p = buildings.get(i);
                if (p.originPos == null || !p.isExploredClientside)
                    continue;
                boolean verdant = FactionTraits.of(p.getBuilding().getFaction()) == FactionTraits.VERDANT
                    || (p.currentStructureName != null && p.currentStructureName.contains("_verdant"));
                if (!CrownMotion.animates(p.currentStructureName, verdant))
                    continue;
                Tree t = TREES.get(p);
                if (t == null) {
                    t = new Tree();
                    TREES.put(p, t);
                }
                if (t.blockCount != p.getBlocks().size() || !Objects.equals(t.structure, p.currentStructureName))
                    shape(t, p, now);
                if (t.cx.length == 0)
                    continue;
                double dx = t.px - cam.x, dz = t.pz - cam.z, lim = t.taken ? KEEP : RANGE;
                t.dist = dx * dx + dz * dz;
                if (t.dist > lim * lim)
                    continue;
                track(t, p, now);
                CANDIDATES.add(t);
            }
            CANDIDATES.sort(NEAREST);
        }
        int n = CANDIDATES.size();
        if (candidateVerts.length < n)
            candidateVerts = new int[n * 2];
        for (int i = 0; i < n; i++) {
            Tree t = CANDIDATES.get(i);
            candidateVerts[i] = t.mesh != null ? t.mesh.vertexCount : t.standingCount * CrownMotion.VERTS_PER_CELL;
        }
        int budget = DisplaySettings.buildingAnimation() == DisplaySettings.ANIM_LOW ? BUDGET_LOW : BUDGET_FULL;
        int take = CrownMotion.takeCount(candidateVerts, n, budget);

        boolean changed = false;
        for (int i = RELEASING.size() - 1; i >= 0; i--) {
            Tree t = RELEASING.get(i);
            if (now >= t.releaseUntil || now < t.releaseUntil - REFRESH_TICKS) {
                RELEASING.remove(i);
                if (!t.taken)
                    t.mesh = null;
            }
        }
        for (int i = 0; i < TAKEN.size(); i++) {
            Tree t = TAKEN.get(i);
            int at = CANDIDATES.indexOf(t);
            if (at < 0 || at >= take) {
                // handed back to the mesh: drawn until its re-mesh has surely landed, so the crown never blinks out
                t.taken = false;
                t.releaseUntil = now + REFRESH_TICKS;
                RELEASING.add(t);
                dirty(t);
                changed = true;
            }
        }
        TAKEN.clear();
        for (int i = 0; i < take; i++) {
            Tree t = CANDIDATES.get(i);
            RELEASING.remove(t);
            if (t.mesh == null || t.dirty || now - t.capturedAt >= RECAPTURE_TICKS)
                capture(t, now);
            else
                t.mesh.settle(now);
            if (!t.taken) {
                t.taken = true;
                dirty(t);
                changed = true;
            }
            TAKEN.add(t);
        }
        if (changed || hiddenStale)
            publishHidden();
    }

    /** Crowns being built: which cells stand, every BUILD_TICKS, so a laid leaf starts growing at once. */
    private static void trackBuilding(long now) {
        for (Map.Entry<BuildingPlacement, Tree> e : TREES.entrySet()) {
            Tree t = e.getValue();
            BuildingPlacement p = e.getKey();
            if (!t.taken || p == null || t.built && p.isBuilt)
                continue;                       // only crowns being built (or just completed: the swell)
            if (p.getBlocks().size() != t.blockCount || !Objects.equals(t.structure, p.currentStructureName))
                continue;                       // a new shape waits for the refresh
            track(t, p, now);
            if (t.dirty)
                capture(t, now);
        }
    }

    private static boolean hiddenStale = false;

    /** A placement's leaf cells and their crowns: once per shape (structure, block count). */
    private static void shape(Tree t, BuildingPlacement p, long now) {
        if (t.taken)
            dirty(t);                           // the old cells go back to the mesh
        if (t.structure != null && !t.structure.equals(p.currentStructureName) && t.built && p.isBuilt)
            t.swellAt = now;                    // a level-up: the crown swells as the new level stands
        t.structure = p.currentStructureName;
        t.blockCount = p.getBlocks().size();
        List<BlockPos> cells = new ArrayList<>();
        java.util.Set<Long> solid = new java.util.HashSet<>();
        for (BuildingBlock bb : p.getBlocks()) {
            BlockPos at = bb.getBlockPos();
            if (!bb.getBlockState().isAir())
                solid.add(CrownMotion.cellKey(at.getX(), at.getY(), at.getZ()));
            if (bb.getBlockState().getBlock() instanceof LeavesBlock)
                cells.add(at);
        }
        int n = cells.size();
        t.cx = new int[n];
        t.cy = new int[n];
        t.cz = new int[n];
        t.crownOf = new int[n];
        for (int k = 0; k < n; k++) {
            BlockPos c = cells.get(k);
            t.cx[k] = c.getX();
            t.cy[k] = c.getY();
            t.cz[k] = c.getZ();
        }
        t.crowns = CrownMotion.crowns(t.cx, t.cy, t.cz, t.crownOf);
        t.gust = new float[t.crowns.size()];
        t.sway = new float[t.crowns.size()];
        for (int c = 0; c < t.crowns.size(); c++)
            t.sway[c] = t.crowns.get(c).sway();
        t.lanterns = CrownMotion.lanterns(t.cx, t.cy, t.cz, t.crownOf, t.crowns, solid);
        t.lanternMesh = null;
        t.bornAt = new float[n];
        Arrays.fill(t.bornAt, Float.NaN);
        t.standing = new boolean[n];
        t.standingCount = 0;
        t.seen = false;
        t.anchor[0] = p.originPos.getX();
        t.anchor[1] = p.originPos.getY();
        t.anchor[2] = p.originPos.getZ();
        t.minX = t.minY = t.minZ = Integer.MAX_VALUE;
        t.maxX = t.maxY = t.maxZ = Integer.MIN_VALUE;
        for (int k = 0; k < n; k++) {
            t.minX = Math.min(t.minX, t.cx[k]);
            t.minY = Math.min(t.minY, t.cy[k]);
            t.minZ = Math.min(t.minZ, t.cz[k]);
            t.maxX = Math.max(t.maxX, t.cx[k]);
            t.maxY = Math.max(t.maxY, t.cy[k]);
            t.maxZ = Math.max(t.maxZ, t.cz[k]);
        }
        if (n > 0) {
            t.box = new AABB(t.minX - BOX_MARGIN, t.minY - BOX_MARGIN, t.minZ - BOX_MARGIN,
                t.maxX + 1 + BOX_MARGIN, t.maxY + 1 + BOX_MARGIN, t.maxZ + 1 + BOX_MARGIN);
            t.px = t.crowns.get(0).cx();
            t.pz = t.crowns.get(0).cz();
        }
        t.mesh = null;
        t.dirty = true;
        if (t.taken) {
            hiddenStale = true;
            dirty(t);
        }
    }

    /** Which cells stand (a newly laid one starts growing), completion, and the fog. */
    private static void track(Tree t, BuildingPlacement p, long now) {
        ClientLevel level = MC.level;
        int standing = 0;
        for (int k = 0; k < t.cx.length; k++) {
            boolean st = level.getBlockState(MUT.set(t.cx[k], t.cy[k], t.cz[k])).getBlock() instanceof LeavesBlock;
            if (st != t.standing[k]) {
                t.standing[k] = st;
                t.dirty = true;
                // a cell standing when the crown is first seen is simply there; one laid after it grows in
                t.bornAt[k] = st && t.seen ? now : Float.NaN;
            }
            if (st)
                standing++;
        }
        t.standingCount = standing;
        t.seen = true;
        if (!p.isBuilt)
            t.seenUnbuilt = true;
        else if (!t.built && t.seenUnbuilt) {
            t.swellAt = now;                    // completed in sight of this client: the crown swells, petals fall
            petals(t);
        }
        t.built = p.isBuilt;
        boolean visible = FogOfWarClientEvents.isInBrightChunk(p.centrePos);
        if (visible != t.visible) {
            t.visible = visible;
            t.frozenAt = visible ? Float.NaN : now;
            t.dirty = true;                     // the fog tint is in the captured colours
        }
    }

    private static boolean completedLogged = false;

    /**
     * Once a session: what a built crown's first capture holds - proof in a log (an eyes tour's) that the cells and
     * the lanterns were really captured, not only that the renderer ran.
     */
    private static boolean log(Tree t, String when, boolean done) {
        if (done)
            return true;
        ReignOfNether.LOGGER.info("[Crowns] {} {}: {} of {} leaf cells standing, {} crown vertices, {} lanterns ({} vertices), "
                + "biggest crown sway {} block", when, t.structure, t.standingCount, t.cx.length,
            t.mesh == null ? 0 : t.mesh.vertexCount, t.lanterns.size(),
            t.lanternMesh == null ? 0 : t.lanternMesh.vertexCount, t.sway.length == 0 ? 0 : t.sway[0]);
        return true;
    }

    private static void petals(Tree t) {
        if (t.standingCount == 0)
            return;
        for (int i = 0; i < PETALS; i++) {
            int k = RANDOM.nextInt(t.cx.length);
            if (!t.standing[k])
                continue;
            MC.level.addParticle(ParticleTypes.CHERRY_LEAVES, t.cx[k] + RANDOM.nextDouble(), t.cy[k] + RANDOM.nextDouble() * 0.5,
                t.cz[k] + RANDOM.nextDouble(), 0, 0, 0);
        }
    }

    /** Marks a crown's sections for a re-mesh (it joined or left the crowns we draw). */
    private static void dirty(Tree t) {
        if (t.cx.length > 0)
            MC.levelRenderer.setBlocksDirty(t.minX, t.minY, t.minZ, t.maxX, t.maxY, t.maxZ);
    }

    private static void publishHidden() {
        hiddenStale = false;
        int n = 0;
        for (int i = 0; i < TAKEN.size(); i++)
            n += TAKEN.get(i).cx.length;
        if (n == 0) {
            hidden = NONE;
            return;
        }
        LongOpenHashSet h = new LongOpenHashSet(n);
        for (int i = 0; i < TAKEN.size(); i++) {
            Tree t = TAKEN.get(i);
            // every cell of the crown, standing or not: a leaf laid later is ours from its first mesh, and grows in
            for (int k = 0; k < t.cx.length; k++)
                h.add(BlockPos.asLong(t.cx[k], t.cy[k], t.cz[k]));
        }
        hidden = h;
    }

    private static void disable(Throwable e) {
        disabled = true;
        for (int i = 0; i < TAKEN.size(); i++)
            dirty(TAKEN.get(i));
        TAKEN.clear();
        RELEASING.clear();
        hidden = NONE;
        StackTraceElement[] st = e.getStackTrace();
        ReignOfNether.LOGGER.warn("[Crowns] living crowns switched off for this session after an error: {}{}", e,
            st.length > 0 ? " at " + st[0] : "");
    }

    // ---------------------------------------------------------------------------------------------------------------
    // the capture: vanilla tessellates the standing cells once, into our arrays
    // ---------------------------------------------------------------------------------------------------------------

    private static final ArrayList<RenderType> CAPTURE_TYPES = new ArrayList<>();
    private static final ArrayList<Recorder> RECORDERS = new ArrayList<>();
    private static final PoseStack CAPTURE_POSE = new PoseStack();

    private static void capture(Tree t, long now) {
        ClientLevel level = MC.level;
        BlockRenderDispatcher dispatcher = MC.getBlockRenderer();
        CAPTURE_TYPES.clear();
        for (Recorder r : RECORDERS)
            r.reset();
        for (int k = 0; k < t.cx.length; k++) {
            if (!t.standing[k])
                continue;
            BlockPos pos = MUT.set(t.cx[k], t.cy[k], t.cz[k]).immutable();
            BlockState state = level.getBlockState(pos);
            if (!(state.getBlock() instanceof LeavesBlock))
                continue;
            BakedModel model = dispatcher.getBlockModel(state);
            long seed = state.getSeed(pos);
            RANDOM.setSeed(seed);
            for (RenderType rt : model.getRenderTypes(state, RANDOM, ModelData.EMPTY)) {
                Recorder r = recorder(rt);
                r.cell = k;
                CAPTURE_POSE.pushPose();
                CAPTURE_POSE.translate(t.cx[k] - t.anchor[0], t.cy[k] - t.anchor[1], t.cz[k] - t.anchor[2]);
                RANDOM.setSeed(seed);
                // ModelData.EMPTY: the real quads (the mesher's call carries CrownBakedModel.DRAWN instead)
                dispatcher.getModelRenderer().tesselateBlock(level, model, state, pos, CAPTURE_POSE, r, true, RANDOM, seed,
                    OverlayTexture.NO_OVERLAY, ModelData.EMPTY, rt);
                CAPTURE_POSE.popPose();
            }
        }
        Captured cap = collect();
        t.types = cap.types;
        t.mesh = new CrownMesh(t.anchor, t.cx, t.cy, t.cz, t.crownOf, t.crowns, t.bornAt, cap.pos, cap.uv, cap.color,
            cap.light, cap.normal, cap.owner, cap.start, cap.count);
        if (t.out.length < cap.color.length * 3)
            t.out = new float[cap.color.length * 3];
        captureLanterns(t, level, dispatcher);
        t.dirty = false;
        t.capturedAt = now;
        if (t.built)
            completedLogged = log(t, "built crown captured:", completedLogged);
    }

    // the lanterns' look: a small pearlescent froglight (the reference's purple-pink glow, and the Court's own light -
    // Heartwood Hall is lit by froglights) hung fullbright on a chain; drawn, so it lights nothing round it. A vanilla
    // lantern tinted pink read orange-red (shader eyes run 41818390477): a tint only multiplies its orange texture.
    private static final BlockState CHAIN = net.minecraft.world.level.block.Blocks.CHAIN.defaultBlockState();
    private static final BlockState LANTERN = net.minecraft.world.level.block.Blocks.PEARLESCENT_FROGLIGHT.defaultBlockState();
    /** The light's size (a block's fraction) and where it hangs: its top at the chain's end. */
    private static final float LANTERN_SIZE = 0.42f;
    private static final int FULLBRIGHT = net.minecraft.client.renderer.LightTexture.FULL_BRIGHT;

    /** The chain and lantern under each hang cell that stands, tessellated where they hang (their light, their AO). */
    private static void captureLanterns(Tree t, ClientLevel level, BlockRenderDispatcher dispatcher) {
        t.lanternMesh = null;
        int n = t.lanterns.size();
        if (n == 0)
            return;
        CAPTURE_TYPES.clear();
        for (Recorder r : RECORDERS)
            r.reset();
        float[] hang = new float[n * 3], weight = new float[n], phase = new float[n];
        int[] crown = new int[n];
        for (int l = 0; l < n; l++) {
            int[] h = t.lanterns.get(l);
            CrownMotion.Crown c = t.crowns.get(h[3]);
            hang[l * 3] = h[0] + 0.5f - t.anchor[0];
            hang[l * 3 + 1] = h[1] - t.anchor[1];
            hang[l * 3 + 2] = h[2] + 0.5f - t.anchor[2];
            crown[l] = h[3];
            weight[l] = CrownMotion.heightWeight(h[1], c.baseY(), c.height());
            phase[l] = (float) CrownMotion.flutterPhase(h[0], h[1], h[2]) * 7f;
            if (!level.getBlockState(MUT.set(h[0], h[1], h[2])).is(net.minecraft.tags.BlockTags.LEAVES))
                continue;                       // its bough is not there (yet, or any more): no lantern
            for (int k = 1; k <= CrownMotion.LANTERN_CHAIN + 1; k++) {
                BlockState state = k <= CrownMotion.LANTERN_CHAIN ? CHAIN : LANTERN;
                BlockPos pos = MUT.set(h[0], h[1] - k, h[2]).immutable();
                BakedModel model = dispatcher.getBlockModel(state);
                long seed = state.getSeed(pos);
                RANDOM.setSeed(seed);
                for (RenderType rt : model.getRenderTypes(state, RANDOM, ModelData.EMPTY)) {
                    Recorder r = recorder(rt);
                    r.cell = l;
                    int from = r.n;
                    CAPTURE_POSE.pushPose();
                    CAPTURE_POSE.translate(h[0] - t.anchor[0], h[1] - k - t.anchor[1], h[2] - t.anchor[2]);
                    if (state == LANTERN) {             // a small light hung from the chain's end (the cell's top)
                        CAPTURE_POSE.translate(0.5f, 1f - LANTERN_SIZE / 2, 0.5f);
                        CAPTURE_POSE.scale(LANTERN_SIZE, LANTERN_SIZE, LANTERN_SIZE);
                        CAPTURE_POSE.translate(-0.5f, -0.5f, -0.5f);
                    }
                    RANDOM.setSeed(seed);
                    dispatcher.getModelRenderer().tesselateBlock(level, model, state, pos, CAPTURE_POSE, r, false, RANDOM,
                        seed, OverlayTexture.NO_OVERLAY, ModelData.EMPTY, rt);
                    CAPTURE_POSE.popPose();
                    if (state == LANTERN)
                        r.glow(from, FULLBRIGHT);
                }
            }
        }
        Captured cap = collect();
        if (cap.color.length == 0)
            return;
        t.lanternTypes = cap.types;
        t.lanternMesh = new LanternMesh(hang, crown, weight, phase, cap.pos, cap.uv, cap.color, cap.light, cap.normal,
            cap.owner, cap.start, cap.count);
        if (t.lanternOut.length < cap.color.length * 3)
            t.lanternOut = new float[cap.color.length * 3];
    }

    /** What the recorders hold, batched by render type, as one set of arrays. */
    private record Captured(RenderType[] types, float[] pos, float[] uv, int[] color, int[] light, int[] normal,
                            int[] owner, int[] start, int[] count) { }

    private static Captured collect() {
        int batches = CAPTURE_TYPES.size(), total = 0;
        for (int b = 0; b < batches; b++)
            total += RECORDERS.get(b).n;
        float[] pos = new float[total * 3], uv = new float[total * 2];
        int[] color = new int[total], light = new int[total], normal = new int[total], owner = new int[total];
        int[] start = new int[batches], count = new int[batches];
        int at = 0;
        for (int b = 0; b < batches; b++) {
            Recorder r = RECORDERS.get(b);
            start[b] = at;
            count[b] = r.n;
            System.arraycopy(r.pos, 0, pos, at * 3, r.n * 3);
            System.arraycopy(r.uv, 0, uv, at * 2, r.n * 2);
            System.arraycopy(r.color, 0, color, at, r.n);
            System.arraycopy(r.light, 0, light, at, r.n);
            System.arraycopy(r.normal, 0, normal, at, r.n);
            System.arraycopy(r.cellOf, 0, owner, at, r.n);
            at += r.n;
        }
        return new Captured(CAPTURE_TYPES.toArray(new RenderType[0]), pos, uv, color, light, normal, owner, start, count);
    }

    private static Recorder recorder(RenderType rt) {
        int i = CAPTURE_TYPES.indexOf(rt);
        if (i < 0) {
            i = CAPTURE_TYPES.size();
            CAPTURE_TYPES.add(rt);
            if (RECORDERS.size() <= i)
                RECORDERS.add(new Recorder());
        }
        return RECORDERS.get(i);
    }

    /** Takes the vertices vanilla's block tessellation writes (its bulk path and the chained one) into arrays. */
    private static final class Recorder implements VertexConsumer {
        float[] pos = new float[3 * 1024], uv = new float[2 * 1024];
        int[] color = new int[1024], light = new int[1024], normal = new int[1024], cellOf = new int[1024];
        int n = 0;
        int cell;   // the cell being tessellated
        // the chained path's pending vertex
        private float px, py, pz, pu, pv, pnx, pny, pnz;
        private int pc = -1, pl;

        void reset() {
            n = 0;
        }

        /** Lights the vertices from {@code from} on fully, unshaded (a lantern's glow: no face shade, no AO). */
        void glow(int from, int lightmap) {
            for (int v = from; v < n; v++) {
                color[v] = 0xFFFFFFFF;
                light[v] = lightmap;
            }
        }

        private void grow() {
            int cap = color.length * 2;
            pos = Arrays.copyOf(pos, cap * 3);
            uv = Arrays.copyOf(uv, cap * 2);
            color = Arrays.copyOf(color, cap);
            light = Arrays.copyOf(light, cap);
            normal = Arrays.copyOf(normal, cap);
            cellOf = Arrays.copyOf(cellOf, cap);
        }

        private void add(float x, float y, float z, int rgba, float u, float v, int lightmap, float nx, float ny, float nz) {
            if (n == color.length)
                grow();
            pos[n * 3] = x;
            pos[n * 3 + 1] = y;
            pos[n * 3 + 2] = z;
            uv[n * 2] = u;
            uv[n * 2 + 1] = v;
            color[n] = rgba;
            light[n] = lightmap;
            normal[n] = packNormal(nx, ny, nz);
            cellOf[n] = cell;
            n++;
        }

        @Override
        public void vertex(float x, float y, float z, float r, float g, float b, float a, float u, float v, int overlay,
                           int lightmap, float nx, float ny, float nz) {
            add(x, y, z, packColor(r, g, b, a), u, v, lightmap, nx, ny, nz);
        }

        @Override
        public VertexConsumer vertex(double x, double y, double z) {
            px = (float) x;
            py = (float) y;
            pz = (float) z;
            return this;
        }

        @Override
        public VertexConsumer color(int r, int g, int b, int a) {
            pc = (r & 255) | (g & 255) << 8 | (b & 255) << 16 | (a & 255) << 24;
            return this;
        }

        @Override
        public VertexConsumer uv(float u, float v) {
            pu = u;
            pv = v;
            return this;
        }

        @Override
        public VertexConsumer overlayCoords(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer uv2(int u, int v) {
            pl = (u & 0xFFFF) | (v & 0xFFFF) << 16;
            return this;
        }

        @Override
        public VertexConsumer normal(float x, float y, float z) {
            pnx = x;
            pny = y;
            pnz = z;
            return this;
        }

        @Override
        public void endVertex() {
            add(px, py, pz, pc, pu, pv, pl, pnx, pny, pnz);
        }

        @Override
        public void defaultColor(int r, int g, int b, int a) {
        }

        @Override
        public void unsetDefaultColor() {
        }
    }

    /** RGBA as bytes, r lowest. */
    static int packColor(float r, float g, float b, float a) {
        return (int) (Math.min(1f, Math.max(0f, r)) * 255f + 0.5f)
            | (int) (Math.min(1f, Math.max(0f, g)) * 255f + 0.5f) << 8
            | (int) (Math.min(1f, Math.max(0f, b)) * 255f + 0.5f) << 16
            | (int) (Math.min(1f, Math.max(0f, a)) * 255f + 0.5f) << 24;
    }

    static int packNormal(float x, float y, float z) {
        return ((int) (x * 127f) & 255) | ((int) (y * 127f) & 255) << 8 | ((int) (z * 127f) & 255) << 16;
    }

    // ---------------------------------------------------------------------------------------------------------------
    // the frame
    // ---------------------------------------------------------------------------------------------------------------

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent evt) {
        // with the other cutout blocks, before the translucent pass (water and glass behind a crown draw over it right)
        if (evt.getStage() != RenderLevelStageEvent.Stage.AFTER_CUTOUT_BLOCKS || disabled || MC.level == null
                || TAKEN.isEmpty() && RELEASING.isEmpty())
            return;
        long t0 = System.nanoTime();
        int verts = 0, trees = 0;
        try {
            Vec3 cam = evt.getCamera().getPosition();
            Frustum frustum = ShaderPacks.renderingShadowPass() ? null : evt.getFrustum();   // the shadow map: every crown
            PoseStack.Pose last = evt.getPoseStack().last();
            BASE.set(last.pose());
            BASE_N.set(last.normal());
            float partial = evt.getPartialTick();
            float now = ClientTicks.now() + partial;
            double windMul = Wind.multiplier();
            MultiBufferSource.BufferSource buffers = MC.renderBuffers().bufferSource();
            USED.clear();
            for (int i = 0, nt = TAKEN.size(), n = nt + RELEASING.size(); i < n; i++) {
                Tree t = i < nt ? TAKEN.get(i) : RELEASING.get(i - nt);
                if (t.mesh == null || frustum != null && !frustum.isVisible(t.box))
                    continue;
                // the wind's clock is the wrapped one (WindField works in seconds); a fogged crown keeps its last pose
                float clock = t.frozenAt == t.frozenAt ? t.frozenAt % ClientTicks.WRAP : ClientTicks.at(partial);
                double seconds = clock / 20.0;
                double heading = WindField.heading(seconds);
                CrownMesh m = t.mesh;
                for (int c = 0; c < m.crowns.length; c++)
                    t.gust[c] = (float) WindField.gust(m.crowns[c].cx(), m.crowns[c].cz(), seconds, heading);
                m.pose((float) (t.anchor[0] - cam.x), (float) (t.anchor[1] - cam.y), (float) (t.anchor[2] - cam.z),
                    seconds, windMul, heading, t.gust, now, now - t.swellAt, t.out);
                for (int b = 0; b < t.types.length; b++) {
                    RenderType rt = t.types[b];
                    if (!USED.contains(rt))
                        USED.add(rt);
                    write(buffers.getBuffer(rt), t.out, m.pos.length / 3, m.normal, m.color, m.uv, m.light,
                        m.batchStart[b], m.batchCount[b]);
                }
                verts += m.vertexCount;
                LanternMesh lm = t.lanternMesh;
                if (lm != null && t.built) {           // the lanterns are hung once the building stands
                    lm.pose((float) (t.anchor[0] - cam.x), (float) (t.anchor[1] - cam.y), (float) (t.anchor[2] - cam.z),
                        seconds, windMul, heading, t.gust, t.sway, t.lanternOut);
                    for (int b = 0; b < t.lanternTypes.length; b++) {
                        RenderType rt = t.lanternTypes[b];
                        if (!USED.contains(rt))
                            USED.add(rt);
                        write(buffers.getBuffer(rt), t.lanternOut, lm.vertexCount, lm.normal, lm.color, lm.uv, lm.light,
                            lm.batchStart[b], lm.batchCount[b]);
                    }
                    verts += lm.vertexCount;
                }
                trees++;
                if (!firstDrawLogged) {
                    firstDrawLogged = true;
                    ReignOfNether.LOGGER.info("[Crowns] first crown drawn: {} at {} {} {}, {} cells in {} crowns, {} vertices, "
                            + "{} lanterns ({} vertices)", t.structure, t.anchor[0], t.anchor[1], t.anchor[2], t.cx.length,
                        m.crowns.length, m.vertexCount, t.lanterns.size(), lm == null ? 0 : lm.vertexCount);
                }
            }
            for (int i = 0; i < USED.size(); i++)
                buffers.endBatch(USED.get(i));
        } catch (RuntimeException | LinkageError e) {
            disable(e);
        }
        if (com.solegendary.reignofnether.matchstart.EyesDirector.ENABLED && trees > 0)
            timing(System.nanoTime() - t0, verts, trees);
    }

    /** The posed vertices of one batch into its buffer, through the camera's rotation. No allocation. */
    private static void write(VertexConsumer vc, float[] out, int vertices, int[] normal, int[] color, float[] uv,
                              int[] light, int from, int count) {
        for (int v = from, end = Math.min(from + count, vertices); v < end; v++) {
            BASE.transformPosition(out[v * 3], out[v * 3 + 1], out[v * 3 + 2], POS);
            int nrm = normal[v];
            BASE_N.transform((byte) nrm / 127f, (byte) (nrm >> 8) / 127f, (byte) (nrm >> 16) / 127f, NRM);
            int c = color[v];
            vc.vertex(POS.x, POS.y, POS.z, (c & 255) / 255f, (c >> 8 & 255) / 255f, (c >> 16 & 255) / 255f,
                (c >>> 24) / 255f, uv[v * 2], uv[v * 2 + 1], OverlayTexture.NO_OVERLAY, light[v], NRM.x, NRM.y, NRM.z);
        }
    }

    /** Eyes tours: one line every 20 s with what the crowns cost on the render thread. */
    private static void timing(long nanos, int verts, int trees) {
        nanosTotal += nanos;
        nanosPeak = Math.max(nanosPeak, nanos);
        timedFrames++;
        vertsPeak = Math.max(vertsPeak, verts);
        treesPeak = Math.max(treesPeak, trees);
        long ms = System.currentTimeMillis();
        if (timingFrom == 0)
            timingFrom = ms;
        if (ms - timingFrom < 20_000)           // every 20 s: an eyes work scene lasts ~45 s
            return;
        ReignOfNether.LOGGER.info(String.format("[Crowns] timing: %.3f ms/frame on average, %.3f peak, up to %d crowned "
                + "buildings and %d vertices a frame, %d frames", nanosTotal / 1e6 / timedFrames, nanosPeak / 1e6, treesPeak,
            vertsPeak, timedFrames));
        timingFrom = ms;
        nanosTotal = nanosPeak = 0;
        timedFrames = vertsPeak = treesPeak = 0;
    }
}
