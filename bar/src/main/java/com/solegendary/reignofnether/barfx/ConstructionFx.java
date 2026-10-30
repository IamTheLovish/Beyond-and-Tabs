package com.solegendary.reignofnether.barfx;

import com.solegendary.reignofnether.building.BuildingBlock;
import com.solegendary.reignofnether.building.BuildingClientEvents;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.ConstructionShape;
import com.solegendary.reignofnether.faction.FactionTraits;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Each faction builds its own way (claude/faction-construction-oct29.md; lovish, Oct 29: "Building looks fake, each
 * race could get their own method of building"). The White Angel pilot, approved from a paintover ("i loveeee"):
 * <ul>
 *   <li>a cloud HALO hugging the layer being built - not a cloud overhead, the RTS camera looks down - lying flat just
 *   under the build front and climbing with it, a cool band on its inner edge;</li>
 *   <li>the part still to come is a pale GOLD ghost (its hull only, real 1 px course lines): gold "not yet" over white
 *   "done" says how far along it is at a glance;</li>
 *   <li>the build front's cut line, the one gold rectangle, drawn only where wall stands below it, a bead running
 *   round it;</li>
 *   <li>light from above: one soft cone landing in a cool pool on the cut plane, slow gold motes rising;</li>
 *   <li>a golden hammer and a chisel working built blocks of the course being laid, on opposite sides: each blow a
 *   white flash and the struck face fading from warm white, sparks, dust;</li>
 *   <li>done: the halo swells and fades, a flash on the top, a light ring runs out over the ground, the tools fly up.</li>
 * </ul>
 * Building-agnostic: everything comes from the placement's own blocks (the buildings are being reworked). Pure client
 * rendering through BarFxClient's passes; thinner when zoomed out; nothing allocated per frame once a site's shape is
 * cached (one ConstructionShape per placement, built from its blocks the first time it is seen rising). Glow budget:
 * the ghost, halo, cool accents, cut line, bead, tools and flashes are normal-blended; only the cone, its warm core,
 * motes, glints and sparks are additive, small and capped.
 */
final class ConstructionFx {

    private ConstructionFx() { }

    private static final int CLOUD = 0xFFFBF2, GOLD = 0xFFD27A, LIGHT = 0xFFF3D2;
    /** The cool counterpoint (round 8 critic: it sampled WARM - it was additive / buried under the white band): alpha-
     *  blended on top, a band on the halo's inner edge and the floor pool. */
    private static final int COOL_BAND = 0xCFDDF7, COOL_POOL = 0xDDE7FA;
    /** The cut line, the one gold rectangle: a white-gold core in a soft #F0B840 glow; the bead's glow. */
    private static final int CUT_CORE = 0xFFF8E0, CUT_GLOW = 0xF0B840, BEAD_GLOW = 0xFFD66B;
    /** The ghost of the part still to come: pale gold (round 8 critic: round 7's gold read progress at a glance; round
     *  8's white vanished), its 1 px corners and course lines. */
    private static final int GHOST = 0xF2DFAE, GHOST_EDGE = 0xE8C76A;
    /** The cone of light: a warm white, additive and capped at 0.12. */
    private static final int RAY = 0xFFF8E6;
    /** Sparks at each blow; the blow's flash and the struck face's warm white; the blow's dust. */
    private static final int SPARK = 0xFFE08A, FLASH = 0xFFFFFF, STRUCK_FACE = 0xFFF6D6, DUST = 0xD9D3C6;
    /** The tools, normal-blended: a gold head and an ivory handle inside a dark rim inside a 1 px gold outline, so a
     *  tool reads on the white walls and on the lawn alike (rounds 3-6). */
    private static final int TOOL_BODY = 0xE0A83A, TOOL_HANDLE = 0xF1E7CF, TOOL_RIM = 0x3A2710, TOOL_OUTLINE = 0xF0B840;
    /** Seconds the completion beat lasts (the ring's 0.6 s swell is the longest part, the tools' flight 0.6 s). */
    private static final float DONE_SECONDS = 0.8f;
    /** Past this RTS zoom (strategic) the halo is thinner, the tools become glints and the light stops. */
    private static final float FAR_ZOOM = 70;
    /** The halo's middle stands this far outside the layer's footprint; it is HALO_W thick (round 8 critic: 0.7). */
    private static final float RING_PAD = 0.75f, HALO_W = 0.7f;
    /** The halo lies this far below the cut plane, never lower than RING_FLOOR above the building's foot. */
    private static final float RING_DROP = 0.7f, RING_FLOOR = 0.35f;
    /** The cut line sits this far inside the wall faces, never on the selection box's edge (round 6 critic). */
    private static final float CUT_INSET = 0.05f;
    /** Seconds between recounts of what stands in each layer (a few hundred block-state reads per site). */
    private static final float RECOUNT = 0.5f;
    /** Built blocks remembered per side of the course for the tools to work. */
    private static final int CAND = 12;

    /** Per rising site: its shape, the front drawn (eased), the ring's eased footprint, when it finished. */
    private static final class Site {
        final ConstructionShape shape;
        final float[] outline;
        final float[] cut = new float[4];              // the layer's footprint at the front
        final float[] cutIn = new float[4];            // ... inset CUT_INSET: the cut line
        final float[] course = new float[4];           // ... inset half a block: the motes' area
        final float[] ring = { Float.NaN, 0, 0, 0 };   // the halo's footprint, easing toward the cut's
        final float[] tmp = new float[4];
        final float[] pt = new float[4];               // ringPoint / perimeterPoint results {x, z, nx, nz}
        final float[] tool = new float[6];             // a tool's struck point {x, y, z} and face normal {nx, nz}, ok
        final float[] beat = new float[4];             // a tool's strikeBeat
        final float[] heads = new float[6];            // each tool's working end this frame {x, y, z} (for its glint)
        final int[] standing;     // blocks standing per layer, recounted every RECOUNT seconds
        // the course: which perimeter cells of the front layer's footprint have wall (this layer or the one below),
        // per side (0: -z, 1: +x, 2: +z, 3: -x), and built blocks of the course on each side for the tools
        final int span;
        final boolean[] walled;
        final float[] wallBox = new float[4];
        int wallLayer = Integer.MIN_VALUE;
        final int[] cand = new int[4 * CAND * 3];
        final int[] candN = new int[4];
        float countedAt = -1;
        float shown;
        float doneAt = -1;
        float lastFront;
        Site(ConstructionShape s, int span) {
            shape = s;
            outline = new float[6 * s.maxOutlineBoxes()];
            standing = new int[s.layers()];
            this.span = span;
            walled = new boolean[4 * span];
        }
    }
    private static final Map<BuildingPlacement, Site> SITES = new WeakHashMap<>();
    private static final List<BuildingPlacement> scratch = new ArrayList<>();

    static boolean isAngel(BuildingPlacement p) {
        return FactionTraits.of(p.getFaction()) == FactionTraits.SUNFORGED;
    }

    private static Site siteOf(BuildingPlacement p) {
        Site s = SITES.get(p);
        if (s == null) {
            List<int[]> pts = new ArrayList<>();
            for (BuildingBlock b : p.getBlocks()) {
                if (b.getBlockState().isAir())
                    continue;
                BlockPos bp = b.getBlockPos();
                pts.add(new int[] { bp.getX(), bp.getY(), bp.getZ() });
            }
            ConstructionShape shape = ConstructionShape.of(pts);
            float[] box = new float[4];
            int span = 1;
            for (int l = 0; l < shape.layers(); l++) {
                shape.layerBox(shape.minY() + l + 0.5f, box);
                span = Math.max(span, (int) Math.max(box[1] - box[0], box[3] - box[2]));
            }
            s = new Site(shape, span);
            s.shown = Float.NaN;   // set from the first recount (world heights can be negative)
            SITES.put(p, s);
        }
        return s;
    }

    /** The cut's footprint and its insets, for the front. */
    private static void boxes(Site s, float front) {
        s.shape.layerBox(front, s.cut);
        inset(s.cut, CUT_INSET, s.cutIn);
        inset(s.cut, 0.5f, s.course);
    }

    /** {@code src} shrunk by {@code d} on every side, never past its middle. */
    private static void inset(float[] src, float d, float[] out) {
        float mx = (src[0] + src[1]) * 0.5f, mz = (src[2] + src[3]) * 0.5f;
        out[0] = Math.min(mx, src[0] + d); out[1] = Math.max(mx, src[1] - d);
        out[2] = Math.min(mz, src[2] + d); out[3] = Math.max(mz, src[3] - d);
    }

    /**
     * Every RECOUNT seconds: what stands per layer (the front), then, for the front's layer, which cells round its
     * footprint have wall (this layer or the one below: the cut line is drawn only there - round 8 critic) and up to
     * CAND built blocks of the course per side (the tools work only those - round 8: they struck air). A second pass
     * over the placement's blocks, filtered by height before any block-state read.
     */
    private static void recount(Site s, BuildingPlacement p) {
        java.util.Arrays.fill(s.standing, 0);
        net.minecraft.client.multiplayer.ClientLevel level = net.minecraft.client.Minecraft.getInstance().level;
        if (level == null)
            return;
        for (BuildingBlock b : p.getBlocks())
            if (!b.getBlockState().isAir() && b.isPlaced(level))
                s.standing[s.shape.layerOf(b.getBlockPos().getY())]++;
        float target = s.shape.frontFromLayers(s.standing);
        int l = s.shape.layerOf(target), ly = s.shape.minY() + l;
        s.shape.layerBox(target, s.wallBox);
        s.wallLayer = l;
        java.util.Arrays.fill(s.walled, false);
        java.util.Arrays.fill(s.candN, 0);
        int x0 = (int) s.wallBox[0], x1 = (int) s.wallBox[1] - 1, z0 = (int) s.wallBox[2], z1 = (int) s.wallBox[3] - 1;
        for (int pass = 0; pass < 2; pass++) {
            // pass 0: this layer (wall cells and the tools' blocks); pass 1: the layer below - wall cells, and the
            // tools' blocks only if this layer has none yet (a course just begun: they work the top of the last one)
            boolean need = pass == 0 || s.candN[0] + s.candN[1] + s.candN[2] + s.candN[3] == 0;
            int y = pass == 0 ? ly : ly - 1;
            if (pass == 1 && l == 0)
                break;
            for (BuildingBlock b : p.getBlocks()) {
                BlockPos bp = b.getBlockPos();
                if (bp.getY() != y || b.getBlockState().isAir())
                    continue;
                int x = bp.getX(), z = bp.getZ();
                if (x < x0 || x > x1 || z < z0 || z > z1 || (x != x0 && x != x1 && z != z0 && z != z1))
                    continue;
                if (!b.isPlaced(level))
                    continue;
                for (int side = 0; side < 4; side++) {
                    boolean on = side == 0 ? z == z0 : side == 1 ? x == x1 : side == 2 ? z == z1 : x == x0;
                    if (!on)
                        continue;
                    int idx = (side & 1) == 0 ? x - x0 : z - z0;
                    if (idx >= 0 && idx < s.span)
                        s.walled[side * s.span + idx] = true;
                    if (need && s.candN[side] < CAND) {
                        int c = (side * CAND + s.candN[side]++) * 3;
                        s.cand[c] = x; s.cand[c + 1] = y; s.cand[c + 2] = z;
                    }
                }
            }
        }
    }

    /**
     * Called from BarFxClient.render before its early return (a site can rise with no other effect alive). The
     * completion beat starts the first frame a known rising site reads {@code isBuilt}: the game's own hook is
     * BuildingPlacement.onBuilt (its client branch plays the completion sound and sets isBuilt), so this is within a
     * frame of it without touching that shared class; a site never seen rising (loaded in finished) gets no beat.
     */
    static void render(MultiBufferSource.BufferSource buffers, Matrix4f mat, float clock, float dt) {
        List<BuildingPlacement> all = BuildingClientEvents.getBuildings();
        if (all == null || all.isEmpty())
            return;
        scratch.clear();
        for (BuildingPlacement p : all) {
            if (p == null || p.centrePos == null || !isAngel(p))
                continue;
            Site known = SITES.get(p);
            boolean rising = !p.isBuilt && p.serverBlocksPlaced > 0;
            boolean finishing = p.isBuilt && known != null && (known.doneAt < 0 || clock - known.doneAt < DONE_SECONDS);
            if (!rising && !finishing)
                continue;
            if (!BarFxClient.near(p.centrePos.getX(), p.centrePos.getY(), p.centrePos.getZ(), 24))
                continue;
            scratch.add(p);
        }
        if (scratch.isEmpty())
            return;
        boolean far = BarFxClient.ortho && BarFxClient.zoom > FAR_ZOOM;

        // normal-blended: the halo and its cool band, the pool, the ghost, the cut line and bead, the tools, the blows
        BarFxClient.begin(buffers, BarFxRenderTypes.TRANSLUCENT, mat);
        for (BuildingPlacement p : scratch) {
            Site s = siteOf(p);
            if (p.isBuilt) {
                if (s.doneAt < 0)
                    s.doneAt = clock;
                float k = clock - s.doneAt;
                // the halo swells to 1.5x and fades (0.6 s); the tools fly up and vanish (0.6 s)
                if (k < 0.6f && !Float.isNaN(s.ring[0])) {
                    float e = k / 0.6f;
                    drawRing(s, s.lastFront, clock, far, 1 - e, 1 + 0.5f * e);
                }
                if (!far && k < 0.6f) {
                    boxes(s, s.lastFront);
                    for (int i = 0; i < TOOLS; i++)
                        drawTool(s, i, s.doneAt + i * 0.37f, 1 - k / 0.6f, 4 * k * k / 0.36f, true);
                }
                continue;
            }
            // the front from what actually stands per layer (round 3: the count lagged the walls - stragglers like a
            // jammed pool held it down - and the outline wrapped built walls), eased so it climbs smoothly
            if (s.countedAt < 0 || clock - s.countedAt > RECOUNT) {
                s.countedAt = clock;
                recount(s, p);
            }
            float target = s.shape.frontFromLayers(s.standing);
            s.shown = Float.isNaN(s.shown) ? target : s.shown + (target - s.shown) * Math.min(1, dt * 4);
            float front = s.shown;
            s.lastFront = front;
            boxes(s, front);
            // the halo's footprint glides to a narrower layer rather than snapping at a step
            float ease = Float.isNaN(s.ring[0]) ? 1 : Math.min(1, dt * 2.5f);
            for (int i = 0; i < 4; i++)
                s.ring[i] = Float.isNaN(s.ring[i]) || ease >= 1 ? s.cut[i] : s.ring[i] + (s.cut[i] - s.ring[i]) * ease;
            drawRing(s, front, clock, far, 1f, 1f);
            if (!far)
                drawPool(s, front, clock);
            drawGhost(s, front, far);
            drawCut(s, front, far);
            drawBead(s, front, clock, far);
            if (!far)
                for (int i = 0; i < TOOLS; i++) {
                    float t = clock + i * 0.37f;
                    drawStruck(s, i, t);
                    drawDust(s, i, t);
                    // a faint trail: three fading copies a moment behind (they coincide when the tool is still)
                    drawTool(s, i, t - 0.12f, 0.1f, 0, false);
                    drawTool(s, i, t - 0.08f, 0.18f, 0, false);
                    drawTool(s, i, t - 0.04f, 0.3f, 0, false);
                    drawTool(s, i, t, 1f, 0, true);
                }
        }
        buffers.endBatch(BarFxRenderTypes.TRANSLUCENT);

        // additive, small and capped: the cone and its warm core, the motes, the tools' glints and sparks, the
        // completion flash and ground ring
        BarFxClient.begin(buffers, BarFxRenderTypes.ADDITIVE, mat);
        for (BuildingPlacement p : scratch) {
            Site s = SITES.get(p);
            if (s == null)
                continue;
            if (p.isBuilt) {
                drawDone(s, clock - s.doneAt);
                continue;
            }
            float front = s.lastFront;
            if (far) {
                drawGlints(s, front, clock);
                continue;
            }
            drawCone(s, front, clock);
            drawMotes(s, front, clock);
            for (int i = 0; i < TOOLS; i++)
                drawToolLight(s, i, clock + i * 0.37f);
        }
        buffers.endBatch(BarFxRenderTypes.ADDITIVE);
        BarFxClient.vc = null;
    }

    // ------------------------------------------------------------------ the halo

    /**
     * The cloud halo: a thin flat ring RING_DROP below the cut plane round the layer's footprint (ConstructionShape
     * .ringPoint, rounded corners) - round 7/8: the camera-facing puffs read as mist fogging the built stone; the flat
     * halo was the right call. Round 8 critic: HALO_W thick, 0.5 at its inner edge falling to nothing outside, pulsing
     * +-0.1 at 0.5 Hz, 30% fainter on the far side (where the ghost stands behind it); a few flat lumps on it so it
     * reads as cloud; and the cool accent ON TOP of it, alpha-blended - a 0.4-block #CFDDF7 band at 0.45 on its inner
     * edge (round 8 sampled it warm: it lay under the white band, soft, half of it lawn). {@code grow} scales it about
     * its centre (the completion swell).
     */
    static void drawRing(Site s, float front, float clock, boolean far, float alpha, float grow) {
        float[] box = s.tmp, pt = s.pt;
        float mx = (s.ring[0] + s.ring[1]) * 0.5f, mz = (s.ring[2] + s.ring[3]) * 0.5f;
        box[0] = mx + (s.ring[0] - mx) * grow; box[1] = mx + (s.ring[1] - mx) * grow;
        box[2] = mz + (s.ring[2] - mz) * grow; box[3] = mz + (s.ring[3] - mz) * grow;
        float pad = RING_PAD * grow, y = Math.max(front - RING_DROP, s.shape.minY() + RING_FLOOR);
        float pulse = 0.5f + 0.1f * (float) Math.sin(clock * Math.PI);   // 0.5 +- 0.1 at 0.5 Hz
        float in = -HALO_W * 0.5f, out = HALO_W * 0.5f;
        float len = ConstructionShape.ringLength(box, pad);
        int n = Math.max(24, Math.min(96, Math.round(len / 0.8f)));
        float lx = BarFxClient.lookX, lz = BarFxClient.lookZ, lh = (float) Math.sqrt(lx * lx + lz * lz);
        if (lh > 1e-4f) { lx /= lh; lz /= lh; }
        float px = 0, pz = 0, pnx = 0, pnz = 0, pf = 1;
        for (int k = 0; k <= n; k++) {
            ConstructionShape.ringPoint(box, pad, (float) k / n, pt);
            float f = 1 - 0.3f * Math.max(0, Math.min(1, pt[2] * lx + pt[3] * lz));   // the far side: 30% less
            if (k > 0) {
                float a0 = pulse * alpha * pf, a1 = pulse * alpha * f;
                strip(px, pz, pnx, pnz, pt[0], pt[1], pt[2], pt[3], y, in - 0.1f, 0, in, a0, a1, CLOUD);
                strip(px, pz, pnx, pnz, pt[0], pt[1], pt[2], pt[3], y, in, a0, out, 0, a1, CLOUD, 0);
                float c0 = 0.45f * alpha * pf, c1 = 0.45f * alpha * f;
                strip(px, pz, pnx, pnz, pt[0], pt[1], pt[2], pt[3], y + 0.01f, in - 0.05f, c0, in + 0.35f, c0, c1,
                        COOL_BAND, c1);
            }
            px = pt[0]; pz = pt[1]; pnx = pt[2]; pnz = pt[3]; pf = f;
        }
        if (far)
            return;
        // flat lumps on the halo's middle (three sizes, breathing), orbiting once in 12 s, so it reads as cloud
        int puffs = Math.max(10, Math.min(60, Math.round(len / 1.4f)));
        for (int k = 0; k < puffs; k++) {
            float r = puffRadius(k) * 0.8f * (1 + 0.1f * (float) Math.sin(clock * 2.51f + k * 1.9f));
            ConstructionShape.ringPoint(box, pad + 0.1f, (k + 0.3f * hash(k * 3 + 1)) / puffs + clock / 12f, pt);
            float f = 1 - 0.3f * Math.max(0, Math.min(1, pt[2] * lx + pt[3] * lz));
            flatDisc(pt[0], y + 0.03f, pt[1], r, CLOUD, 0.4f * alpha * f, 12);
        }
    }

    /** Puff k's radius: three sizes (1.5 / 1.0 / 0.6 blocks across, mostly the bigger two), a little jitter. */
    private static float puffRadius(int k) {
        float hs = hash(k * 5 + 2);
        return (hs < 0.45f ? 0.75f : hs < 0.8f ? 0.5f : 0.3f) * (0.9f + 0.2f * hash(k * 11 + 7));
    }

    /**
     * One flat quad of a band between two ring points (p0 with normal n0, p1 with n1), from offset o0 to o1 along
     * the normals: alpha {@code a00} / {@code a10} at offset o0 (at p0 / p1) and {@code a01} / {@code a11} at o1.
     */
    private static void strip(float x0, float z0, float nx0, float nz0, float x1, float z1, float nx1, float nz1, float y,
                              float o0, float a00, float o1, float a01, float a10, int c, float a11) {
        BarFxClient.v(x0 + nx0 * o0, y, z0 + nz0 * o0, c, a00);
        BarFxClient.v(x1 + nx1 * o0, y, z1 + nz1 * o0, c, a10);
        BarFxClient.v(x1 + nx1 * o1, y, z1 + nz1 * o1, c, a11);
        BarFxClient.v(x0 + nx0 * o1, y, z0 + nz0 * o1, c, a01);
    }

    /** {@link #strip} with one alpha per offset at both ends. */
    private static void strip(float x0, float z0, float nx0, float nz0, float x1, float z1, float nx1, float nz1, float y,
                              float o0, float ao0, float o1, float a0, float a1, int c) {
        strip(x0, z0, nx0, nz0, x1, z1, nx1, nz1, y, o0, ao0 * a0, o1, a0, ao0 * a1, c, a1);
    }

    // ------------------------------------------------------------------ the part still to come

    /**
     * The unbuilt part as a pale GOLD ghost. History: round 6 (0.065 a face) vanished; round 7's 0.3 gold read progress
     * at a glance but as amber glass, its nested boxes clutter; round 8's white stone at 0.2 vanished over the stone and
     * smeared grey over the lawn. Round 9 (critic): #F2DFAE at ~0.30 on the camera-facing sides and ~0.40 on the top so
     * the plan reads at the RTS zoom; the HULL only (front-facing sides; of each box's top only the ledge the box above
     * leaves uncovered - no face inside another on screen); from the top of the layer being laid, so it never runs down
     * beside the built part; #E8C76A edges at 0.8 on the corners and real 1 px course lines every block (strokes, which
     * survive 30 px a block, not a texture).
     */
    static void drawGhost(Site s, float front, boolean far) {
        float from = (float) Math.floor(front) + 1;
        int n = s.shape.outline(from, s.outline);
        if (n == 0)
            return;
        float[] o = s.outline;
        float lx = BarFxClient.lookX, lz = BarFxClient.lookZ;
        boolean showMinX = lx > 0, showMaxX = lx < 0, showMinZ = lz > 0, showMaxZ = lz < 0;   // faces turned to us
        float aSide = 0.3f, aTop = 0.4f, w = far ? 0.07f : 0.035f, ea = 0.8f;
        for (int i = 0; i < n; i++) {
            int b = i * 6;
            float x0 = o[b], x1 = o[b + 1], z0 = o[b + 2], z1 = o[b + 3], y0 = o[b + 4] + 0.02f, y1 = o[b + 5];
            if (showMinZ) wall(x0, z0, x1, z0, y0, y1, aSide, far);
            if (showMaxZ) wall(x1, z1, x0, z1, y0, y1, aSide, far);
            if (showMinX) wall(x0, z1, x0, z0, y0, y1, aSide * 0.9f, far);
            if (showMaxX) wall(x1, z0, x1, z1, y0, y1, aSide * 0.9f, far);
            // the top: all of it for the highest box, else the ledge round the box above (it stands on this top)
            boolean last = i == n - 1 || o[b + 6 + 4] > y1 + 1e-3f;
            if (last) {
                top(x0, x1, z0, z1, y1, aTop, far);
            } else {
                float ux0 = Math.min(x1, Math.max(x0, o[b + 6])), ux1 = Math.max(ux0, Math.min(x1, o[b + 7]));
                float uz0 = Math.min(z1, Math.max(z0, o[b + 8])), uz1 = Math.max(uz0, Math.min(z1, o[b + 9]));
                top(x0, x1, z0, uz0, y1, aTop, far);         // the four ledge strips round the covered part
                top(x0, x1, uz1, z1, y1, aTop, far);
                top(x0, ux0, uz0, uz1, y1, aTop, far);
                top(ux1, x1, uz0, uz1, y1, aTop, far);
            }
            // the edges: the top rim of every box (each is a step's edge, drawn once) and the visible corners
            line(x0, y1, z0, x1, y1, z0, w, GHOST_EDGE, ea);
            line(x1, y1, z0, x1, y1, z1, w, GHOST_EDGE, ea);
            line(x1, y1, z1, x0, y1, z1, w, GHOST_EDGE, ea);
            line(x0, y1, z1, x0, y1, z0, w, GHOST_EDGE, ea);
            if (showMinX || showMinZ) line(x0, y0, z0, x0, y1, z0, w, GHOST_EDGE, ea);
            if (showMaxX || showMinZ) line(x1, y0, z0, x1, y1, z0, w, GHOST_EDGE, ea);
            if (showMaxX || showMaxZ) line(x1, y0, z1, x1, y1, z1, w, GHOST_EDGE, ea);
            if (showMinX || showMaxZ) line(x0, y0, z1, x0, y1, z1, w, GHOST_EDGE, ea);
        }
    }

    /** A ghost wall from (xa, za) to (xb, zb), y0..y1: the fill, then a 1 px course line every block. */
    private static void wall(float xa, float za, float xb, float zb, float y0, float y1, float a, boolean far) {
        BarFxClient.v(xa, y0, za, GHOST, a);
        BarFxClient.v(xb, y0, zb, GHOST, a);
        BarFxClient.v(xb, y1, zb, GHOST, a);
        BarFxClient.v(xa, y1, za, GHOST, a);
        if (far)
            return;
        for (int c = (int) Math.ceil(y0); c < y1 - 0.05f; c++)
            if (c > y0 + 0.05f)
                line(xa, c, za, xb, c, zb, 0.035f, GHOST_EDGE, 0.55f);
    }

    /** A flat piece of ghost top, with a faint 1 px block grid (none at strategic zoom). */
    private static void top(float x0, float x1, float z0, float z1, float y, float a, boolean far) {
        if (x1 - x0 < 1e-3f || z1 - z0 < 1e-3f)
            return;
        BarFxClient.v(x0, y, z0, GHOST, a);
        BarFxClient.v(x1, y, z0, GHOST, a);
        BarFxClient.v(x1, y, z1, GHOST, a);
        BarFxClient.v(x0, y, z1, GHOST, a);
        if (far)
            return;
        for (float x = (float) Math.ceil(x0 + 0.05f); x < x1 - 0.05f; x += 1f)
            line(x, y + 0.01f, z0, x, y + 0.01f, z1, 0.03f, GHOST_EDGE, 0.3f);
        for (float z = (float) Math.ceil(z0 + 0.05f); z < z1 - 0.05f; z += 1f)
            line(x0, y + 0.01f, z, x1, y + 0.01f, z, 0.03f, GHOST_EDGE, 0.3f);
    }

    // ------------------------------------------------------------------ the cut line

    /**
     * The cut line - the one gold rectangle - normal-blended, CUT_INSET inside the wall faces: a 2 px #FFF8E0 core in
     * a 2 px soft #F0B840 glow, drawn only along cells with wall standing below it (round 8 critic: skip spans over
     * air), runs of walled cells merged into one stroke.
     */
    static void drawCut(Site s, float front, boolean far) {
        float y = front + 0.04f;
        float wg = far ? 0.2f : 0.1f, wc = far ? 0.12f : 0.07f;
        boolean known = s.wallLayer == s.shape.layerOf(front) && s.wallBox[0] == s.cut[0] && s.wallBox[1] == s.cut[1]
                && s.wallBox[2] == s.cut[2] && s.wallBox[3] == s.cut[3];
        float[] c = s.cutIn;
        for (int side = 0; side < 4; side++) {
            int cells = (int) ((side & 1) == 0 ? s.cut[1] - s.cut[0] : s.cut[3] - s.cut[2]);
            int run = -1;
            for (int i = 0; i <= cells; i++) {
                boolean on = i < cells && (!known || i >= s.span || s.walled[side * s.span + i]);
                if (on && run < 0)
                    run = i;
                if (!on && run >= 0) {
                    cutRun(s, side, run, i, y, wg, wc, c);
                    run = -1;
                }
            }
        }
    }

    /** One stroke of the cut line along {@code side} over cells [i0, i1). */
    private static void cutRun(Site s, int side, int i0, int i1, float y, float wg, float wc, float[] c) {
        float ax, az, bx, bz;
        if ((side & 1) == 0) {
            float z = side == 0 ? c[2] : c[3];
            ax = Math.max(c[0], s.cut[0] + i0); bx = Math.min(c[1], s.cut[0] + i1); az = bz = z;
        } else {
            float x = side == 1 ? c[1] : c[0];
            az = Math.max(c[2], s.cut[2] + i0); bz = Math.min(c[3], s.cut[2] + i1); ax = bx = x;
        }
        BarFxClient.streak(ax, y, az, bx, y, bz, wg, CUT_GLOW, 0.7f, 0.7f);
        line(ax, y, az, bx, y, bz, wc, CUT_CORE, 1f);
    }

    /**
     * The travelling bead (round 8 critic: it must exist in a frozen still): 0.3 block across, a white core in a
     * #FFD66B glow at 0.8, 4 blocks a second round the cut line, a 1-block fading trail. Normal-blended, so it shows
     * on the white stone as well as the lawn.
     */
    static void drawBead(Site s, float front, float clock, boolean far) {
        float[] box = s.cutIn, pt = s.pt;
        float per = 2 * (Math.max(0, box[1] - box[0]) + Math.max(0, box[3] - box[2]));
        if (per <= 0)
            return;
        float y = front + 0.06f, at = clock * 4f, r = far ? 0.25f : 0.15f;
        for (int j = 5; j >= 1; j--) {   // the trail, a block long
            ConstructionShape.perimeterPoint(box, (at - j * 0.2f) / per, pt);
            float k = 1 - j / 6f;
            BarFxClient.softDisc(pt[0], y, pt[1], r * (0.5f + 0.5f * k), BEAD_GLOW, 0.5f * k, BEAD_GLOW, 0, 8, 0, 0);
        }
        ConstructionShape.perimeterPoint(box, at / per, pt);
        BarFxClient.softDisc(pt[0], y, pt[1], r, BEAD_GLOW, 0.8f, BEAD_GLOW, 0.35f, 12, 0, 0);
        BarFxClient.softDisc(pt[0], y, pt[1], r * 0.45f, 0xFFFFFF, 1f, 0xFFFFFF, 0.9f, 8, 0, 0);
    }

    // ------------------------------------------------------------------ light from above

    /**
     * Where the light lands into {@code s.pt}: drifting slowly over the cut's footprint, but kept ~1 block or more
     * from where the builders' beams meet (round 8 critic: the cone and the beam fan merged) - the mean target of the
     * live nano beams aimed into this footprint (BarFxClient.live; read only, allocation-free).
     */
    private static void landing(Site s, float clock) {
        float mx = (s.cut[0] + s.cut[1]) * 0.5f, mz = (s.cut[2] + s.cut[3]) * 0.5f;
        float hx = Math.max(0, (s.cut[1] - s.cut[0]) * 0.5f - 1.5f), hz = Math.max(0, (s.cut[3] - s.cut[2]) * 0.5f - 1.5f);
        float x = mx + hx * 0.4f * (float) Math.sin(clock * 0.07f), z = mz + hz * 0.4f * (float) Math.cos(clock * 0.05f);
        float bx = 0, bz = 0;
        int nb = 0;
        List<BarFxClient.P> live = BarFxClient.live;
        for (int i = 0, m = live.size(); i < m; i++) {
            BarFxClient.P q = live.get(i);
            if (q.kind != BarFxClient.BEAM || q.x2 < s.cut[0] - 1 || q.x2 > s.cut[1] + 1 || q.z2 < s.cut[2] - 1
                    || q.z2 > s.cut[3] + 1)
                continue;
            bx += q.x2; bz += q.z2; nb++;
        }
        if (nb > 0) {
            bx /= nb; bz /= nb;
            float dx = x - bx, dz = z - bz, d = (float) Math.sqrt(dx * dx + dz * dz);
            if (d < 1.5f) {   // push it off the beams' meeting point, inside the footprint
                if (d < 1e-3f) { dx = 1; dz = 0; d = 1; }
                x = Math.max(s.cut[0] + 0.5f, Math.min(s.cut[1] - 0.5f, bx + dx / d * 1.5f));
                z = Math.max(s.cut[2] + 0.5f, Math.min(s.cut[3] - 0.5f, bz + dz / d * 1.5f));
            }
        }
        s.pt[0] = x; s.pt[1] = z;
    }

    /** The cool pool where the light lands: #DDE7FA alpha-blended, a soft disc clamped to 0.35 (round 8 critic). */
    static void drawPool(Site s, float front, float clock) {
        landing(s, clock);
        float pulse = 0.9f + 0.1f * (float) Math.sin(clock * 0.6f);
        flatDisc(s.pt[0], front + 0.05f, s.pt[1], 1.8f, COOL_POOL, 0.35f * pulse, 18);
    }

    /**
     * The cone of light, with no lines (round 7: anything under ~6 px reads as a builder beam): 3 blocks wide at the
     * top, 5 where it lands, #FFF8E6 at 0.12 at most, two softer internal bands at 0.06, and a warm core kept to a
     * 1-block disc in the cool pool (round 8 critic: the cap and pool had blown out to near-white blobs).
     */
    static void drawCone(Site s, float front, float clock) {
        landing(s, clock);
        float x = s.pt[0], z = s.pt[1], yb = front + 0.06f, yt = front + 9f;
        float pulse = 0.85f + 0.15f * (float) Math.sin(clock * 0.6f);
        taper(x, yt, z, 1.5f, 0f, x, yb, z, 2.5f, 0.12f * pulse);
        float sx = BarFxClient.leftX, sz = BarFxClient.leftZ;
        for (int k = -1; k <= 1; k += 2) {
            float o = 0.8f * k + 0.2f * (float) Math.sin(clock * 0.4f + k);
            taper(x + sx * o * 0.6f, yt, z + sz * o * 0.6f, 0.4f, 0f, x + sx * o, yb, z + sz * o, 0.6f, 0.06f * pulse);
        }
        flatDisc(x, yb + 0.01f, z, 0.5f, RAY, 0.15f * pulse, 12);
    }

    /** A soft camera-facing band from p to q: half-width hp / hq, centre alpha ap / aq, nothing at its sides. */
    private static void taper(float px, float py, float pz, float hp, float ap, float qx, float qy, float qz, float hq,
                              float aq) {
        float dx = qx - px, dy = qy - py, dz = qz - pz;
        float sx = dy * BarFxClient.lookZ - dz * BarFxClient.lookY, sy = dz * BarFxClient.lookX - dx * BarFxClient.lookZ;
        float sz = dx * BarFxClient.lookY - dy * BarFxClient.lookX;
        float l = (float) Math.sqrt(sx * sx + sy * sy + sz * sz);
        if (l < 1e-5f) { sx = BarFxClient.leftX; sy = BarFxClient.leftY; sz = BarFxClient.leftZ; l = 1; }
        sx /= l; sy /= l; sz /= l;
        BarFxClient.v(px - sx * hp, py - sy * hp, pz - sz * hp, RAY, 0);
        BarFxClient.v(px, py, pz, RAY, ap);
        BarFxClient.v(qx, qy, qz, RAY, aq);
        BarFxClient.v(qx - sx * hq, qy - sy * hq, qz - sz * hq, RAY, 0);
        BarFxClient.v(px, py, pz, RAY, ap);
        BarFxClient.v(px + sx * hp, py + sy * hp, pz + sz * hp, RAY, 0);
        BarFxClient.v(qx + sx * hq, qy + sy * hq, qz + sz * hq, RAY, 0);
        BarFxClient.v(qx, qy, qz, RAY, aq);
    }

    /** Slow gold motes rising inside the footprint: six at a time, 0.3 block a second, each living 6 s. */
    static void drawMotes(Site s, float front, float clock) {
        float x0 = s.course[0], x1 = s.course[1], z0 = s.course[2], z1 = s.course[3];
        for (int j = 0; j < 6; j++) {
            float t = clock / 6f + j / 6f;
            int cyc = (int) Math.floor(t);
            float k = t - cyc;
            float x = x0 + (x1 - x0) * hash(cyc * 7 + j * 131) + 0.15f * (float) Math.sin(clock * 0.9f + j);
            float z = z0 + (z1 - z0) * hash(cyc * 13 + j * 71) + 0.15f * (float) Math.cos(clock * 0.7f + j * 2);
            BarFxClient.softDisc(x, front + 0.2f + 1.8f * k, z, 0.07f, GOLD, 0.7f * (float) Math.sin(Math.PI * k), GOLD,
                    0, 8, 0, 0);
        }
    }

    // ------------------------------------------------------------------ the tools

    /** Tools in view at once (round 7 critic: at most 2), on opposite sides: a hammer and a chisel. */
    private static final int TOOLS = 2;
    /** Hammer / chisel parts (blocks; ~30 px a block at the RTS zoom): ~1.0-1.1 blocks long (round 6 critic). */
    private static final float HANDLE = 0.85f, HANDLE_W = 0.14f, HEAD_L = 0.55f, HEAD_W = 0.26f;
    private static final float CHISEL_GRIP = 0.4f, CHISEL_GRIP_W = 0.2f, CHISEL_BLADE = 0.6f;
    /** The 1 px gold outline outside a 1 px dark rim (blocks). */
    private static final float OUTLINE = 0.07f, RIM = 0.035f;
    /** The strike arc: 25 degrees toward the face (round 8 critic); at the top of it the head stands ~0.35 off. */
    private static final float WIND_BACK = 0.44f;
    /** The tools are pulled this far toward the camera so the block they strike never hides their heads. */
    private static final float NUDGE = 0.6f;

    /**
     * Which side of the course tool {@code i} works: tool 0 the side most turned to the camera, tool 1 the other side
     * turned to it - from the RTS camera's diagonal those two lie either side of the near corner, opposite on screen
     * (round 8 critic: two tools max, on opposite sides), and both faces are in view, so every blow's flash shows. A
     * camera square to one side: tool 1 takes the side opposite.
     */
    private static int toolSide(int i) {
        float lx = BarFxClient.lookX, lz = BarFxClient.lookZ;
        float[] d = SIDE_DOT;
        d[0] = -lz; d[1] = lx; d[2] = lz; d[3] = -lx;   // each side's normal . look: below 0 faces us
        int best = 0;
        for (int k = 1; k < 4; k++)
            if (d[k] < d[best])
                best = k;
        if (i == 0)
            return best;
        int a = (best + 1) & 3, b = (best + 3) & 3;
        int next = d[a] < d[b] ? a : b;
        return d[next] < -0.05f ? next : (best + 2) & 3;
    }
    private static final float[] SIDE_DOT = new float[4];

    /**
     * The built block tool {@code i} strikes in strike cycle {@code cycle} into {@code s.tool}: the point on its outer
     * face {x, y, z} (upper middle) and the face's normal {nx, nz}; s.tool[5] = 1 if there is one. The tools work only
     * blocks that stand on the course being built (recount) - round 8: anchored on the cut line, which runs mostly
     * over cells not yet laid (the front's layer is under half placed by definition), with the handle out along the
     * normal, they struck air outside the hull. No built block on the side: no tool.
     */
    private static boolean toolBlock(Site s, int i, int cycle) {
        int side = toolSide(i), n = s.candN[side];
        s.tool[5] = 0;
        if (n == 0)
            return false;
        int pick = (int) (hash(cycle * 31 + i * 977) * n) % n;
        int c = (side * CAND + pick) * 3;
        float nx = side == 1 ? 1 : side == 3 ? -1 : 0, nz = side == 0 ? -1 : side == 2 ? 1 : 0;
        s.tool[0] = s.cand[c] + 0.5f + nx * 0.5f;
        s.tool[1] = s.cand[c + 1] + 0.7f;
        s.tool[2] = s.cand[c + 2] + 0.5f + nz * 0.5f;
        s.tool[3] = nx; s.tool[4] = nz; s.tool[5] = 1;
        return true;
    }

    /**
     * Tool {@code i}'s struck point at time {@code t} into {@code s.tool} (with the strikeBeat in {@code s.beat}),
     * gliding between blows from the last block to the next. Returns the hop's lift, or NaN for no tool.
     */
    private static float toolSpot(Site s, int i, float t) {
        ConstructionShape.strikeBeat(t, s.beat);
        int c = (int) s.beat[0];
        float m = s.beat[1];
        if (!toolBlock(s, i, c - 1))
            return Float.NaN;
        float ax = s.tool[0], ay = s.tool[1], az = s.tool[2];
        toolBlock(s, i, c);
        float e = m * m * (3 - 2 * m);
        s.tool[0] = ax + (s.tool[0] - ax) * e;
        s.tool[1] = ay + (s.tool[1] - ay) * e;
        s.tool[2] = az + (s.tool[2] - az) * e;
        return 0.35f * (float) Math.sin(Math.PI * m);
    }

    /**
     * One tool at time {@code t}: a hard-edged silhouette in the SCREEN plane (camera left / up), so it always shows
     * full length with its head across the handle. Its working end lands on a built block's face; the tool stands out
     * along that face's normal as the screen shows it, and swings 25 degrees toward the face (0.3 s wind-up, 0.1 s
     * blow - ConstructionShape.strikeBeat). {@code full}: the 1 px gold outline, the dark rim and the body; without it,
     * a faint fill (a trail copy). {@code fly}: blocks it has risen (the completion beat).
     */
    static void drawTool(Site s, int i, float t, float alpha, float fly, boolean full) {
        if (alpha <= 0.01f)
            return;
        float lift = toolSpot(s, i, t);
        if (Float.isNaN(lift))
            return;
        boolean chisel = i == 1;
        float[] b = s.beat, tl = s.tool;
        // the face's outward normal on screen (up, left); the struck direction: into the face, a little down
        float nu = tl[3] * BarFxClient.upX + tl[4] * BarFxClient.upZ, nl = tl[3] * BarFxClient.leftX + tl[4] * BarFxClient.leftZ;
        float su = -nu - 0.35f, sl = -nl;
        float sn = (float) Math.sqrt(su * su + sl * sl);
        if (sn < 1e-4f) { su = -1; sl = 0; sn = 1; }
        su /= sn; sl /= sn;
        float struck = (float) Math.atan2(sl, su);                         // du = cos, dl = sin
        float back = struck > 0 ? -1 : 1;                                  // the way that lifts the head off the face
        float since = b[3];
        float theta = struck + back * ((chisel ? 0.6f : 1f) * WIND_BACK * b[2] + 0.25f * (float) Math.sin(Math.PI * b[1])
                + (since >= 0 && since < 0.25f && b[1] >= 1 ? 0.1f * (float) Math.sin(Math.PI * since / 0.25f) : 0));
        float reach = chisel ? CHISEL_GRIP + CHISEL_BLADE : HANDLE;
        float px = tl[0] - BarFxClient.lookX * NUDGE, py = tl[1] + lift + fly - BarFxClient.lookY * NUDGE,
                pz = tl[2] - BarFxClient.lookZ * NUDGE;
        float gx = px - (su * BarFxClient.upX + sl * BarFxClient.leftX) * reach;
        float gy = py - (su * BarFxClient.upY + sl * BarFxClient.leftY) * reach;
        float gz = pz - (su * BarFxClient.upZ + sl * BarFxClient.leftZ) * reach;
        float du = (float) Math.cos(theta), dl = (float) Math.sin(theta);   // grip -> working end
        for (int pass = full ? 0 : 2; pass < 3; pass++) {   // the gold outline, the dark rim, then the fill
            float e = pass == 0 ? OUTLINE : pass == 1 ? RIM : 0;
            int c0 = pass == 0 ? TOOL_OUTLINE : pass == 1 ? TOOL_RIM : TOOL_HANDLE;
            int c1 = pass == 0 ? TOOL_OUTLINE : pass == 1 ? TOOL_RIM : TOOL_BODY;
            float a = (pass == 0 ? 0.9f : pass == 1 ? 0.85f : 1f) * alpha;
            if (chisel) {
                bar(gx, gy, gz, du, dl, -e, CHISEL_GRIP + e, CHISEL_GRIP_W + 2 * e, CHISEL_GRIP_W + 2 * e, c0, a);
                bar(gx, gy, gz, du, dl, CHISEL_GRIP, CHISEL_BLADE + e, 0.14f + 2 * e, 0.05f + 2 * e, c1, a);
            } else {
                bar(gx, gy, gz, du, dl, -e, HANDLE + e, HANDLE_W + 2 * e, HANDLE_W + 2 * e, c0, a);
                float hx = gx + (du * BarFxClient.upX + dl * BarFxClient.leftX) * HANDLE;
                float hy = gy + (du * BarFxClient.upY + dl * BarFxClient.leftY) * HANDLE;
                float hz = gz + (du * BarFxClient.upZ + dl * BarFxClient.leftZ) * HANDLE;
                bar(hx, hy, hz, -dl, du, -HEAD_L / 2 - e, HEAD_L + 2 * e, HEAD_W + 2 * e, HEAD_W + 2 * e, c1, a);
            }
        }
        if (full && fly == 0) {   // the tool itself: remember its working end for the light pass's glint
            s.heads[i * 3] = gx + (du * BarFxClient.upX + dl * BarFxClient.leftX) * reach;
            s.heads[i * 3 + 1] = gy + (du * BarFxClient.upY + dl * BarFxClient.leftY) * reach;
            s.heads[i * 3 + 2] = gz + (du * BarFxClient.upZ + dl * BarFxClient.leftZ) * reach;
        }
    }

    /**
     * Where tool {@code i}'s last blow landed into {@code s.tool}, and how long ago (seconds, -1 for none or no
     * block): this cycle's block if it has struck in it, else the last cycle's (the aftermath outlives the cycle). The
     * cycle the blow belongs to is left in {@code s.beat[0]}.
     */
    private static float lastBlow(Site s, int i, float t) {
        ConstructionShape.strikeBeat(t, s.beat);
        float since = s.beat[3], c = s.beat[0];
        if (since < 0)
            return -1;
        float hit = ConstructionShape.BEAT_MOVE + ConstructionShape.BEAT_WIND + ConstructionShape.BEAT_STRIKE;
        int sc = (int) (t - c * ConstructionShape.BEAT_CYCLE >= hit ? c : c - 1);
        if (!toolBlock(s, i, sc))
            return -1;
        s.beat[0] = sc;
        return since;
    }

    /**
     * A blow's consequence (round 8 critic: no visible flash): a 0.6-block white flash at 0.9 for two frames (~0.05
     * s), then the struck block's face - and its top - fade from #FFF6D6 at 0.7 to nothing over 400 ms. Normal-blended.
     */
    static void drawStruck(Site s, int i, float t) {
        float since = lastBlow(s, i, t);
        if (since < 0 || since >= 0.4f)
            return;
        float[] tl = s.tool;
        float a = 0.7f * (1 - since / 0.4f);
        float nx = tl[3], nz = tl[4], tx = -nz * 0.5f, tz = nx * 0.5f;     // half a block along the face
        float fx = tl[0] + nx * 0.01f, fz = tl[2] + nz * 0.01f;
        float y0 = (float) Math.floor(tl[1]), y1 = y0 + 1;
        BarFxClient.v(fx - tx, y0, fz - tz, STRUCK_FACE, a);
        BarFxClient.v(fx + tx, y0, fz + tz, STRUCK_FACE, a);
        BarFxClient.v(fx + tx, y1, fz + tz, STRUCK_FACE, a);
        BarFxClient.v(fx - tx, y1, fz - tz, STRUCK_FACE, a);
        float ix = fx - nx * 1.01f, iz = fz - nz * 1.01f;                   // its top
        BarFxClient.v(fx - tx, y1 + 0.01f, fz - tz, STRUCK_FACE, a);
        BarFxClient.v(fx + tx, y1 + 0.01f, fz + tz, STRUCK_FACE, a);
        BarFxClient.v(ix + tx, y1 + 0.01f, iz + tz, STRUCK_FACE, a);
        BarFxClient.v(ix - tx, y1 + 0.01f, iz - tz, STRUCK_FACE, a);
        if (since < 0.05f) {
            float x = tl[0] - BarFxClient.lookX * NUDGE, y = tl[1] - BarFxClient.lookY * NUDGE;
            float z = tl[2] - BarFxClient.lookZ * NUDGE;
            BarFxClient.softDisc(x, y, z, 0.3f, FLASH, 0.9f, FLASH, 0.6f, 12, 0, 0);
        }
    }

    /** A blow's dust: three small soft puffs of stone dust swelling and rising off the struck face (0.5 s). */
    static void drawDust(Site s, int i, float t) {
        float since = lastBlow(s, i, t);
        if (since < 0 || since >= 0.5f)
            return;
        int sc = (int) s.beat[0];
        float[] tl = s.tool;
        float e = since / 0.5f;
        for (int j = 0; j < 3; j++) {
            float ang = (float) (hash(sc * 41 + i * 7 + j * 3) * 2 * Math.PI);
            float d = 0.15f + 0.3f * e;
            float ox = tl[3] * 0.3f * e, oz = tl[4] * 0.3f * e;
            BarFxClient.softDisc(tl[0] + ox + (float) Math.cos(ang) * d * 0.5f, tl[1] + 0.35f * e,
                    tl[2] + oz + (float) Math.sin(ang) * d * 0.5f, 0.16f + 0.2f * e, DUST, 0.35f * (1 - e), DUST, 0, 8,
                    j, 0.15f);
        }
    }

    /** A tool's light: a small glint on its head and, after each blow, 8 gold sparks off the face (0.4 s). */
    static void drawToolLight(Site s, int i, float t) {
        if (Float.isNaN(toolSpot(s, i, t)))
            return;
        float tw = 0.75f + 0.25f * (float) Math.sin(t * 5 + i * 2.1f);
        BarFxClient.softDisc(s.heads[i * 3], s.heads[i * 3 + 1], s.heads[i * 3 + 2], 0.1f, LIGHT, 0.25f * tw, GOLD, 0,
                8, 0, 0);
        float since = lastBlow(s, i, t);
        if (since < 0 || since >= 0.4f)
            return;
        int sc = (int) s.beat[0];
        float[] tl = s.tool;
        float x = tl[0] - BarFxClient.lookX * NUDGE, y = tl[1] - BarFxClient.lookY * NUDGE;
        float z = tl[2] - BarFxClient.lookZ * NUDGE;
        float f = 1 - since / 0.4f;
        for (int j = 0; j < 8; j++) {
            float ang = (float) (hash(sc * 131 + i * 17 + j * 7) * 2 * Math.PI);
            float sp = 1.2f + 1.3f * hash(sc * 57 + j * 29 + i);
            float vx = (float) Math.cos(ang) * sp + tl[3] * 1.5f, vz = (float) Math.sin(ang) * sp + tl[4] * 1.5f;
            float vy = 2f + 1.5f * hash(sc * 23 + j * 3 + i * 5);
            float sx = x + vx * since, sy = y + vy * since - 6 * since * since, sz = z + vz * since;
            float vyNow = vy - 12 * since, l = 0.04f;
            BarFxClient.streak(sx - vx * l, sy - vyNow * l, sz - vz * l, sx, sy, sz, 0.07f, SPARK, 0.3f * f, f);
        }
    }

    /** Strategic zoom: the tools shrink to golden glints walking the cut line. */
    static void drawGlints(Site s, float front, float clock) {
        for (int i = 0; i < 2; i++) {
            ConstructionShape.perimeterPoint(s.cutIn, i / 2f + clock * 0.05f, s.pt);
            BarFxClient.softDisc(s.pt[0], front + 0.6f, s.pt[1], 0.32f, LIGHT, 0.8f, GOLD, 0.2f, 10, 0, 0);
        }
    }

    // ------------------------------------------------------------------ the completion beat

    /**
     * Done (the additive half; the halo's swell and the tools' flight are drawn in the normal-blended pass): a flash
     * on the building's top (0.3 s) and one ground-plane light ring radiating out and fading (0.5 s) - a flash, never
     * persistent.
     */
    static void drawDone(Site s, float k) {
        if (k < 0)
            return;
        float top = s.shape.minY() + s.shape.layers();
        s.shape.layerBox(top - 0.5f, s.tmp);
        if (k < 0.3f) {
            float e = k / 0.3f, a = (1 - e) * (1 - e);
            float tx = (s.tmp[0] + s.tmp[1]) * 0.5f, tz = (s.tmp[2] + s.tmp[3]) * 0.5f;
            BarFxClient.softDisc(tx, top + 0.3f, tz, 1.0f + 0.5f * e, LIGHT, a, GOLD, 0.3f * a, 14, 0, 0);
        }
        if (k < 0.5f) {
            float e = k / 0.5f;
            float r0 = s.shape.ringRadiusAt(s.shape.minY());
            BarFxClient.flatRing(s.shape.centreX(), s.shape.minY() + 0.1f, s.shape.centreZ(), r0 * (1 + 1.5f * e),
                    0.35f + 0.3f * e, GOLD, 0.7f * (1 - e), 48);
        }
    }

    // ------------------------------------------------------------------ primitives

    /**
     * A hard-edged bar in the screen plane: from (x, y, z) offset {@code from} along the screen direction (du up, dl
     * left; unit), {@code len} long, {@code w0} wide at its start and {@code w1} at its end, one alpha throughout
     * (BarFxClient.streak fades to nothing at its sides, so a thin one vanishes - the round 4 tools).
     */
    private static void bar(float x, float y, float z, float du, float dl, float from, float len, float w0, float w1,
                            int c, float a) {
        float dx = du * BarFxClient.upX + dl * BarFxClient.leftX;
        float dy = du * BarFxClient.upY + dl * BarFxClient.leftY;
        float dz = du * BarFxClient.upZ + dl * BarFxClient.leftZ;
        float nx = dl * BarFxClient.upX - du * BarFxClient.leftX;   // the screen perpendicular (-dl, du) up to sign
        float ny = dl * BarFxClient.upY - du * BarFxClient.leftY;
        float nz = dl * BarFxClient.upZ - du * BarFxClient.leftZ;
        float ax = x + dx * from, ay = y + dy * from, az = z + dz * from;
        float bx = ax + dx * len, by = ay + dy * len, bz = az + dz * len;
        float h0 = w0 * 0.5f, h1 = w1 * 0.5f;
        BarFxClient.v(ax - nx * h0, ay - ny * h0, az - nz * h0, c, a);
        BarFxClient.v(bx - nx * h1, by - ny * h1, bz - nz * h1, c, a);
        BarFxClient.v(bx + nx * h1, by + ny * h1, bz + nz * h1, c, a);
        BarFxClient.v(ax + nx * h0, ay + ny * h0, az + nz * h0, c, a);
    }

    /**
     * A hard-edged camera-facing line between two world points, {@code w} wide, one alpha throughout - a crisp 1-2 px
     * edge where BarFxClient.streak (alpha on its centre line only) would vanish.
     */
    private static void line(float x0, float y0, float z0, float x1, float y1, float z1, float w, int c, float a) {
        float dx = x1 - x0, dy = y1 - y0, dz = z1 - z0;
        float sx = dy * BarFxClient.lookZ - dz * BarFxClient.lookY, sy = dz * BarFxClient.lookX - dx * BarFxClient.lookZ;
        float sz = dx * BarFxClient.lookY - dy * BarFxClient.lookX;
        float l = (float) Math.sqrt(sx * sx + sy * sy + sz * sz);
        if (l < 1e-5f) { sx = BarFxClient.leftX; sy = BarFxClient.leftY; sz = BarFxClient.leftZ; l = 1; }
        float h = w * 0.5f / l;
        sx *= h; sy *= h; sz *= h;
        BarFxClient.v(x0 - sx, y0 - sy, z0 - sz, c, a);
        BarFxClient.v(x1 - sx, y1 - sy, z1 - sz, c, a);
        BarFxClient.v(x1 + sx, y1 + sy, z1 + sz, c, a);
        BarFxClient.v(x0 + sx, y0 + sy, z0 + sz, c, a);
    }

    /** A soft disc lying flat (a decal or a pool of light): {@code a} at its centre, nothing at its rim. */
    private static void flatDisc(float x, float y, float z, float r, int c, float a, int seg) {
        float px = x + r, pz = z;
        for (int i = 1; i <= seg; i++) {
            double ang = i * Math.PI * 2 / seg;
            float qx = x + (float) Math.cos(ang) * r, qz = z + (float) Math.sin(ang) * r;
            BarFxClient.v(x, y, z, c, a);
            BarFxClient.v(px, y, pz, c, 0);
            BarFxClient.v(qx, y, qz, c, 0);
            BarFxClient.v(x, y, z, c, a);
            px = qx; pz = qz;
        }
    }

    private static float hash(int i) {
        int h = i * 0x27d4eb2d;
        h ^= h >>> 15;
        h *= 0x85ebca6b;
        h ^= h >>> 13;
        return (h & 0xFFFF) / 65535f;
    }
}
