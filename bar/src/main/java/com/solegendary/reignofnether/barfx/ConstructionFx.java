package com.solegendary.reignofnether.barfx;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.building.BuildingBlock;
import com.solegendary.reignofnether.building.BuildingClientEvents;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.ConstructionGrid;
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
 *   <li>a soft cloud HALO round the course being built, lying just under it and climbing with it, a cool band on its
 *   inner edge;</li>
 *   <li>a pale GOLD ghost over every block still missing - and nothing else (ConstructionGrid): gold "not yet" over
 *   white "done" says how far along it is at a glance;</li>
 *   <li>a gold-white RIM along the top of every standing wall still to be built on, beads running along it;</li>
 *   <li>light from above: one soft cone landing in a cool pool, slow gold motes rising;</li>
 *   <li>a golden hammer and a chisel striking standing faces under the rim, each blow a white flash on the face fading
 *   through warm white, sparks and dust;</li>
 *   <li>done: the halo swells and fades, a flash on the top, a light ring runs out over the ground, the tools fly up.</li>
 * </ul>
 * Building-agnostic: everything comes from the placement's own blocks (the buildings are being reworked). Pure client
 * rendering through BarFxClient's passes; thinner when zoomed out; nothing allocated per frame once a site is cached.
 * <p>Colour (round 9 critic: the cool accent did not survive): BarFx draws at AFTER_TRANSLUCENT_BLOCKS, before the
 * world look's grade (postfx/WorldLookPass, AFTER_LEVEL), so every colour here is graded - but the grade barely moves a
 * cool colour (#BFD0F5 -> (197, 211, 241) in the plain look: bat_postfx.fsh's curve and split tone worked by hand,
 * chroma is capped). What killed
 * it was what lay UNDER it: 0.45 of #CFDDF7 over a green lawn and a warm halo lands near (189, 200, 194). The cool band
 * is now #A9C0F0 at 0.6 over a neutral #F4F4F6 halo: ~(176, 192, 203) over the lawn, ~(200, 210, 230) over stone, B - R
 * 25-30 after the grade.
 */
final class ConstructionFx {

    private ConstructionFx() { }

    private static final int GOLD = 0xFFD27A, LIGHT = 0xFFF3D2;
    /** The halo: a neutral white so gold is the only warm element (round 9 critic). */
    private static final int HALO = 0xF4F4F6;
    /** The cool accent, alpha-blended (see the class note for the pixel arithmetic): the halo's inner band and the
     *  pool where the light lands. */
    private static final int COOL = 0xA9C0F0;
    /** The rim: a 2 px #FFF6D6 core in a 6 px #F2DFAE glow; its beads' glow. */
    private static final int RIM_CORE = 0xFFF6D6, RIM_GLOW = 0xF2DFAE, BEAD_GLOW = 0xFFD66B;
    /** The ghost and its 1 px lines (round 9 critic). */
    private static final int GHOST = 0xF2DFAE;
    /** The cone of light: a warm white, additive, 0.18 at its top fading to nothing at the ground. */
    private static final int RAY = 0xFFF8E6;
    /** Sparks; the blow's white flash and the struck face's warm afterglow; dust. */
    private static final int SPARK = 0xFFE08A, FLASH = 0xFFF8E0, AFTERGLOW = 0xFFE9B0, DUST = 0xD9D3C6;
    /** The tools, normal-blended: a gold head and an ivory handle inside a dark rim inside a 1 px gold outline. */
    private static final int TOOL_BODY = 0xE0A83A, TOOL_HANDLE = 0xF1E7CF, TOOL_RIM = 0x3A2710, TOOL_OUTLINE = 0xF0B840;
    /** Seconds the completion beat lasts (the halo's 0.6 s swell is the longest part, the tools' flight 0.6 s). */
    private static final float DONE_SECONDS = 0.8f;
    /** Past this RTS zoom (strategic) the ghost loses its lines, the tools become glints and the light stops. */
    private static final float FAR_ZOOM = 70;
    /** The halo's ring line stands this far outside the course's footprint; it falls off over HALO_OUT outside it. */
    private static final float RING_PAD = 0.75f, HALO_OUT = 1.2f;
    /** The halo lies this far below the rim, never lower than RING_FLOOR above the building's foot. */
    private static final float RING_DROP = 0.7f, RING_FLOOR = 0.35f;
    /** The rim sits this far inside the wall faces, never on the selection box's edge (round 6 critic). */
    private static final float RIM_INSET = 0.05f;
    /** Seconds between recounts of what stands (a few hundred block-state reads per site). */
    private static final float RECOUNT = 0.5f;

    /** Per rising site: its shape and grid, the rim height drawn (eased), the halo's eased footprint, when it finished. */
    private static final class Site {
        final ConstructionShape shape;
        final ConstructionGrid grid;
        final int[] standing;                          // blocks standing per layer (the fallback front)
        final float[] cut = new float[4];              // the course's footprint at the rim
        final float[] course = new float[4];           // ... inset half a block: the motes' area
        final float[] ring = { Float.NaN, 0, 0, 0 };   // the halo's footprint, easing toward the course's
        final float[] tmp = new float[4];
        final float[] pt = new float[4];               // ringPoint results {x, z, nx, nz}
        final float[] tool = new float[8];             // a tool's struck point {x, y, z}, face normal {nx, nz}, block {x, y, z}
        final float[] beat = new float[4];             // a tool's strikeBeat
        final float[] heads = new float[6];            // each tool's working end this frame {x, y, z} (for its glint)
        final int[] facesA = new int[ConstructionGrid.MAX_EDGES], facesB = new int[ConstructionGrid.MAX_EDGES];
        int nA, nB;                                    // this frame's camera-facing faces for tool 0 / tool 1
        float countedAt = -1;
        float shown = Float.NaN;
        float doneAt = -1;
        float lastFront;
        Site(ConstructionShape s, ConstructionGrid g) { shape = s; grid = g; standing = new int[s.layers()]; }
    }
    private static final Map<BuildingPlacement, Site> SITES = new WeakHashMap<>();
    private static final List<BuildingPlacement> scratch = new ArrayList<>();
    private static boolean warnedTool;

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
            s = new Site(ConstructionShape.of(pts), ConstructionGrid.of(pts));
            SITES.put(p, s);
        }
        return s;
    }

    /** Every RECOUNT seconds: what stands, block by block (ConstructionGrid), and per layer (the fallback front). */
    private static void recount(Site s, BuildingPlacement p) {
        net.minecraft.client.multiplayer.ClientLevel level = net.minecraft.client.Minecraft.getInstance().level;
        if (level == null)
            return;
        java.util.Arrays.fill(s.standing, 0);
        s.grid.clear();
        for (BuildingBlock b : p.getBlocks()) {
            if (b.getBlockState().isAir())
                continue;
            BlockPos bp = b.getBlockPos();
            boolean placed = b.isPlaced(level);
            s.grid.mark(bp.getX(), bp.getY(), bp.getZ(), placed);
            if (placed)
                s.standing[s.shape.layerOf(bp.getY())]++;
        }
        s.grid.finish();
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

        // normal-blended: the halo then its cool band, the pool, the ghost, the rim and beads, the blows, the tools
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
                    drawHalo(s, s.lastFront, clock, far, 1 - e, 1 + 0.5f * e);
                }
                if (!far && k < 0.6f) {
                    toolFaces(s);
                    for (int i = 0; i < TOOLS; i++)
                        drawTool(s, i, s.doneAt + i * 0.37f, 1 - k / 0.6f, 4 * k * k / 0.36f, true);
                }
                continue;
            }
            if (s.countedAt < 0 || clock - s.countedAt > RECOUNT) {
                s.countedAt = clock;
                recount(s, p);
            }
            // the height the halo follows: the rim's mean (each column's own build front), else the layered front
            float target = s.grid.meanRimY();
            if (Float.isNaN(target))
                target = s.shape.frontFromLayers(s.standing);
            s.shown = Float.isNaN(s.shown) ? target : s.shown + (target - s.shown) * Math.min(1, dt * 4);
            float front = s.shown;
            s.lastFront = front;
            s.shape.layerBox(front - 0.5f, s.cut);
            inset(s.cut, 0.5f, s.course);
            // the halo's footprint glides to a narrower course rather than snapping at a step
            float ease = Float.isNaN(s.ring[0]) ? 1 : Math.min(1, dt * 2.5f);
            for (int i = 0; i < 4; i++)
                s.ring[i] = Float.isNaN(s.ring[i]) || ease >= 1 ? s.cut[i] : s.ring[i] + (s.cut[i] - s.ring[i]) * ease;
            drawHalo(s, front, clock, far, 1f, 1f);
            if (!far)
                drawPool(s, clock);
            drawGhost(s, far);
            drawRim(s, clock, far);
            if (!far) {
                toolFaces(s);
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
        }
        buffers.endBatch(BarFxRenderTypes.TRANSLUCENT);

        // additive, small and capped: the cone, the motes, the tools' glints and sparks, the completion flash and ring
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

    /** {@code src} shrunk by {@code d} on every side, never past its middle. */
    private static void inset(float[] src, float d, float[] out) {
        float mx = (src[0] + src[1]) * 0.5f, mz = (src[2] + src[3]) * 0.5f;
        out[0] = Math.min(mx, src[0] + d); out[1] = Math.max(mx, src[1] - d);
        out[2] = Math.min(mz, src[2] + d); out[3] = Math.max(mz, src[3] - d);
    }

    /** The outward normal of a side (0: -z, 1: +x, 2: +z, 3: -x). */
    private static float sideNx(int side) { return side == 1 ? 1 : side == 3 ? -1 : 0; }
    private static float sideNz(int side) { return side == 2 ? 1 : side == 0 ? -1 : 0; }

    // ------------------------------------------------------------------ the halo

    /**
     * The cloud halo round the course's footprint (ConstructionShape.ringPoint, rounded corners), RING_DROP under the
     * rim. Round 9 critic: a hard-edged polygon read as a quartz path / UI. Now a cloud again: a soft falloff from the
     * ring line out over HALO_OUT (and a short one inward), its edge wandering with the slow noise of six to ten flat
     * puffs 0.6-1.0 block across drifting round at 0.15 block a second - no straight line anywhere; neutral #F4F4F6;
     * 0.35-0.55 pulsing at 0.6 Hz on the near side, 0.25 at most on the far side (the ghost stands behind it). Then the
     * cool band on its inner edge, in its own strips after it: #A9C0F0 at 0.6, 0.6 block wide. {@code grow} scales it
     * about its centre (the completion swell).
     */
    static void drawHalo(Site s, float front, float clock, boolean far, float alpha, float grow) {
        float[] box = s.tmp, pt = s.pt;
        float mx = (s.ring[0] + s.ring[1]) * 0.5f, mz = (s.ring[2] + s.ring[3]) * 0.5f;
        box[0] = mx + (s.ring[0] - mx) * grow; box[1] = mx + (s.ring[1] - mx) * grow;
        box[2] = mz + (s.ring[2] - mz) * grow; box[3] = mz + (s.ring[3] - mz) * grow;
        float pad = RING_PAD * grow, y = Math.max(front - RING_DROP, s.shape.minY() + RING_FLOOR);
        float pulse = 0.45f + 0.1f * (float) Math.sin(clock * 2 * Math.PI * 0.6f);   // 0.35-0.55 at 0.6 Hz
        float len = ConstructionShape.ringLength(box, pad);
        int n = Math.max(32, Math.min(128, Math.round(len / 0.5f)));
        float lx = BarFxClient.lookX, lz = BarFxClient.lookZ, lh = (float) Math.sqrt(lx * lx + lz * lz);
        if (lh > 1e-4f) { lx /= lh; lz /= lh; }
        for (int pass = 0; pass < 2; pass++) {   // the halo, then the cool band over it
            float px = 0, pz = 0, pnx = 0, pnz = 0, pa = 0, po = 0;
            for (int k = 0; k <= n; k++) {
                float u = (float) k / n;
                ConstructionShape.ringPoint(box, pad, u, pt);
                // near 1, far 0.25 / 0.55 of it
                float side = 1 - (1 - 0.25f / 0.55f) * Math.max(0, Math.min(1, pt[2] * lx + pt[3] * lz));
                float wob = 0.5f + 0.5f * (float) Math.sin(u * len * 0.9f - clock * 0.15f * 0.9f)
                        * (float) Math.cos(u * len * 0.37f + clock * 0.2f);
                float a = pass == 0 ? pulse * side * alpha * (0.75f + 0.25f * wob) : 0.6f * alpha;   // the accent: both sides
                float o = HALO_OUT * (0.7f + 0.5f * wob);                      // how far the falloff reaches out
                if (k > 0) {
                    if (pass == 0) {
                        strip(px, pz, pnx, pnz, pt[0], pt[1], pt[2], pt[3], y, -0.3f, -0.3f, 0, 0, 0, 0, pa, a, HALO);
                        strip(px, pz, pnx, pnz, pt[0], pt[1], pt[2], pt[3], y, 0, 0, pa, a, po, o, 0, 0, HALO);
                    } else {
                        float yc = y + 0.01f;
                        strip(px, pz, pnx, pnz, pt[0], pt[1], pt[2], pt[3], yc, -0.38f, -0.38f, 0, 0, -0.3f, -0.3f, pa,
                                a, COOL);
                        strip(px, pz, pnx, pnz, pt[0], pt[1], pt[2], pt[3], yc, -0.3f, -0.3f, pa, a, 0.3f, 0.3f, pa, a,
                                COOL);
                        strip(px, pz, pnx, pnz, pt[0], pt[1], pt[2], pt[3], yc, 0.3f, 0.3f, pa, a, 0.38f, 0.38f, 0, 0,
                                COOL);
                    }
                }
                px = pt[0]; pz = pt[1]; pnx = pt[2]; pnz = pt[3]; pa = a; po = o;
            }
        }
        // the noise puffs: flat, 0.6-1.0 block across, drifting round at 0.15 block a second
        int puffs = Math.max(6, Math.min(10, Math.round(len / 4f)));
        for (int k = 0; k < puffs; k++) {
            float r = 0.3f + 0.2f * hash(k * 11 + 7);
            ConstructionShape.ringPoint(box, pad + 0.3f, (k + 0.4f * hash(k * 3 + 1)) / puffs + clock * 0.15f / len, pt);
            float side = 1 - (1 - 0.25f / 0.55f) * Math.max(0, Math.min(1, pt[2] * lx + pt[3] * lz));
            flatDisc(pt[0], y + 0.02f, pt[1], r, HALO, pulse * side * alpha * 0.8f, 12);
        }
    }

    /**
     * One flat quad of a band between two ring points (p0 with normal n0, p1 with n1): its inner side at offsets
     * {@code i0} / {@code i1} along the normals with alphas {@code ai0} / {@code ai1}, its outer side at {@code o0} /
     * {@code o1} with {@code ao0} / {@code ao1}.
     */
    private static void strip(float x0, float z0, float nx0, float nz0, float x1, float z1, float nx1, float nz1, float y,
                              float i0, float i1, float ai0, float ai1, float o0, float o1, float ao0, float ao1, int c) {
        BarFxClient.v(x0 + nx0 * i0, y, z0 + nz0 * i0, c, ai0);
        BarFxClient.v(x1 + nx1 * i1, y, z1 + nz1 * i1, c, ai1);
        BarFxClient.v(x1 + nx1 * o1, y, z1 + nz1 * o1, c, ao1);
        BarFxClient.v(x0 + nx0 * o0, y, z0 + nz0 * o0, c, ao0);
    }

    // ------------------------------------------------------------------ the part still to come

    /**
     * The pale GOLD ghost over every block still MISSING and nothing else (round 9 critic: the hull and course lines
     * were drawn across finished faces, caging the building - they came from one build front, under walls that
     * already stood). From ConstructionGrid: each missing block's faces turned to the camera whose neighbour is open
     * (air or outside - a face against another missing block lies inside the ghost, one against a standing block is
     * behind stone), #F2DFAE at 0.22 on the sides and 0.34 on the tops, each face edged with a 1 px #F2DFAE line at 0.9
     * - the course lines and joints, block by block (strokes: they survive 30 px a block). Strategic zoom: no lines.
     */
    static void drawGhost(Site s, boolean far) {
        ConstructionGrid g = s.grid;
        float lx = BarFxClient.lookX, lz = BarFxClient.lookZ;
        int sx = lx > 0 ? -1 : 1, sz = lz > 0 ? -1 : 1;   // the x / z side turned to us
        float w = 0.035f;
        for (int i = 0, n = g.missingCount(); i < n; i++) {
            int x = g.missingX(i), y = g.missingY(i), z = g.missingZ(i);
            if (!g.isStructure(x, y + 1, z))
                quad(x, y + 1, z, x + 1, y + 1, z, x + 1, y + 1, z + 1, x, y + 1, z + 1, 0.34f, far, w);
            if (!g.isStructure(x + sx, y, z)) {
                float fx = sx > 0 ? x + 1 : x;
                quad(fx, y, z, fx, y, z + 1, fx, y + 1, z + 1, fx, y + 1, z, 0.22f * 0.9f, far, w);
            }
            if (!g.isStructure(x, y, z + sz)) {
                float fz = sz > 0 ? z + 1 : z;
                quad(x, y, fz, x + 1, y, fz, x + 1, y + 1, fz, x, y + 1, fz, 0.22f, far, w);
            }
        }
    }

    /** One ghost face: the fill, then its four 1 px edges (none at strategic zoom). */
    private static void quad(float ax, float ay, float az, float bx, float by, float bz, float cx, float cy, float cz,
                             float dx, float dy, float dz, float a, boolean far, float w) {
        BarFxClient.v(ax, ay, az, GHOST, a);
        BarFxClient.v(bx, by, bz, GHOST, a);
        BarFxClient.v(cx, cy, cz, GHOST, a);
        BarFxClient.v(dx, dy, dz, GHOST, a);
        if (far)
            return;
        line(ax, ay, az, bx, by, bz, w, GHOST, 0.9f);
        line(bx, by, bz, cx, cy, cz, w, GHOST, 0.9f);
        line(cx, cy, cz, dx, dy, dz, w, GHOST, 0.9f);
        line(dx, dy, dz, ax, ay, az, w, GHOST, 0.9f);
    }

    // ------------------------------------------------------------------ the rim

    /**
     * The RIM (round 9 critic: continuous along the full exposed top course of every standing wall segment): every
     * ConstructionGrid rim edge - the top edge of a standing block with a missing block on it, on an open side - inset
     * RIM_INSET into the block: a 6 px #F2DFAE glow at 0.5 under a 2 px #FFF6D6 core. Beads, one per 6 blocks of rim,
     * run along it at 1.5 blocks a second: a white core in a #FFD66B glow at 0.8. Normal-blended: a still shows them.
     */
    static void drawRim(Site s, float clock, boolean far) {
        ConstructionGrid g = s.grid;
        int n = g.rimCount();
        if (n == 0)
            return;
        float wg = far ? 0.3f : 0.15f, wc = far ? 0.12f : 0.07f;
        for (int i = 0; i < n; i++) {
            int side = g.faceSide(i);
            float ix = -sideNx(side) * RIM_INSET, iz = -sideNz(side) * RIM_INSET, y = g.rimY(i) + 0.02f;
            float ax = g.rimX0(i) + ix, az = g.rimZ0(i) + iz, bx = g.rimX1(i) + ix, bz = g.rimZ1(i) + iz;
            BarFxClient.streak(ax, y, az, bx, y, bz, wg, RIM_GLOW, 0.5f, 0.5f);
            line(ax, y, az, bx, y, bz, wc, RIM_CORE, 1f);
        }
        int beads = Math.max(1, Math.round(n / 6f));
        float r = far ? 0.25f : 0.15f;
        for (int k = 0; k < beads; k++) {
            float at = clock * 1.5f + k * 6f;
            at -= (float) Math.floor(at / n) * n;
            int i = Math.min(n - 1, (int) at);
            float f = at - i;
            int side = g.faceSide(i);
            float ix = -sideNx(side) * RIM_INSET, iz = -sideNz(side) * RIM_INSET, y = g.rimY(i) + 0.05f;
            float x = g.rimX0(i) + (g.rimX1(i) - g.rimX0(i)) * f + ix, z = g.rimZ0(i) + (g.rimZ1(i) - g.rimZ0(i)) * f + iz;
            BarFxClient.softDisc(x, y, z, r, BEAD_GLOW, 0.8f, BEAD_GLOW, 0.35f, 12, 0, 0);
            BarFxClient.softDisc(x, y, z, r * 0.45f, 0xFFFFFF, 1f, 0xFFFFFF, 0.9f, 8, 0, 0);
        }
    }

    // ------------------------------------------------------------------ light from above

    /**
     * Where the light lands into {@code s.pt}: drifting slowly over the course's footprint, kept 1.5 blocks or more
     * from the mean target of the live builder beams aimed into it (BarFxClient.live, read only) so the cone never
     * merges with their fan.
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
            if (d < 1.5f) {
                if (d < 1e-3f) { dx = 1; dz = 0; d = 1; }
                x = Math.max(s.cut[0] + 0.5f, Math.min(s.cut[1] - 0.5f, bx + dx / d * 1.5f));
                z = Math.max(s.cut[2] + 0.5f, Math.min(s.cut[3] - 0.5f, bz + dz / d * 1.5f));
            }
        }
        s.pt[0] = x; s.pt[1] = z;
        // the surface it lands on: the top of the highest standing block in that column
        s.pt[2] = s.grid.columnTop((int) Math.floor(x), (int) Math.floor(z));
    }

    /**
     * The cool pool where the light lands, on the standing surface: #A9C0F0 at 0.5 in its middle, radius 1.5, alpha-
     * blended - its centre pixel over the white stone ~(197, 208, 228) after the grade (0.35 of #C4D4F4 left B - R ~10).
     */
    static void drawPool(Site s, float clock) {
        landing(s, clock);
        if (Float.isNaN(s.pt[2]))
            return;
        flatDisc(s.pt[0], s.pt[2] + 0.03f, s.pt[1], 1.5f, COOL, 0.5f, 18);
    }

    /**
     * The cone of light, no lines (round 7: anything under ~6 px reads as a builder beam): 3 blocks wide at the top, 5
     * at the ground, 0.18 at its top fading to nothing at the ground, with two softer internal bands (0.06) - so it
     * adds almost nothing on the built faces (round 9 critic: cap the white wash).
     */
    static void drawCone(Site s, float front, float clock) {
        landing(s, clock);
        float x = s.pt[0], z = s.pt[1];
        float yb = Float.isNaN(s.pt[2]) ? front : s.pt[2], yt = yb + 9f, ym = yt - 1.5f;
        float pulse = 0.85f + 0.15f * (float) Math.sin(clock * 0.6f);
        taper(x, yt, z, 1.5f, 0f, x, ym, z, 1.7f, 0.18f * pulse);           // a soft top, not a cut
        taper(x, ym, z, 1.7f, 0.18f * pulse, x, yb, z, 2.5f, 0f);
        float sx = BarFxClient.leftX, sz = BarFxClient.leftZ;
        for (int k = -1; k <= 1; k += 2) {
            float o = 0.8f * k + 0.2f * (float) Math.sin(clock * 0.4f + k);
            taper(x + sx * o * 0.6f, ym, z + sz * o * 0.6f, 0.4f, 0.06f * pulse, x + sx * o, yb, z + sz * o, 0.6f, 0f);
        }
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

    /** Tools in view at once (round 7 critic: at most 2): a hammer and a chisel. */
    private static final int TOOLS = 2;
    /** Hammer / chisel parts (blocks); the handle's length comes from the pivot (see drawTool). */
    private static final float HANDLE_W = 0.14f, HEAD_L = 0.55f, HEAD_W = 0.26f, CHISEL_GRIP_W = 0.2f;
    /** The 1 px gold outline outside a 1 px dark rim (blocks). */
    private static final float OUTLINE = 0.07f, RIM = 0.035f;
    /** The pivot stands this far out along the face's normal and this far above the struck point (round 9 critic:
     *  face centre + 0.35 along the normal); the swing is a 40-degree arc. */
    private static final float PIVOT_OUT = 0.35f, PIVOT_UP = 0.6f, ARC = 0.7f;
    /** The tools are pulled this far toward the camera so the block they strike never hides their heads. */
    private static final float NUDGE = 0.6f;

    /**
     * This frame's faces the tools may strike (ConstructionGrid: a standing block with a missing block on it, its open
     * side) that are turned to the camera, in two lists: tool 0 the side most turned to it, tool 1 the next (either
     * side of the near corner from the RTS diagonal); if one list is empty it shares the other.
     */
    private static void toolFaces(Site s) {
        ConstructionGrid g = s.grid;
        float lx = BarFxClient.lookX, lz = BarFxClient.lookZ;
        int best = 0, next = 1;
        float db = 9, dn = 9;
        for (int side = 0; side < 4; side++) {
            float d = sideNx(side) * lx + sideNz(side) * lz;
            if (d < db) { dn = db; next = best; db = d; best = side; } else if (d < dn) { dn = d; next = side; }
        }
        s.nA = 0; s.nB = 0;
        for (int i = 0, n = g.faceCount(); i < n; i++) {
            int side = g.faceSide(i);
            if (side == best && db < -0.05f) s.facesA[s.nA++] = i;
            else if (side == next && dn < -0.05f) s.facesB[s.nB++] = i;
        }
    }

    /**
     * The face tool {@code i} strikes in strike cycle {@code cycle} into {@code s.tool}: {x, y, z} the face's centre,
     * {nx, nz} its normal, {bx, by, bz} the block. A face is re-checked against the grid before use (a standing block,
     * its side still open - the list is from the last recount), else the tool skips that blow. Returns false for no
     * face (no standing face turned to the camera: no tool).
     */
    private static boolean toolFace(Site s, int i, int cycle) {
        int[] list = i == 0 ? s.facesA : s.facesB;
        int n = i == 0 ? s.nA : s.nB;
        if (n == 0) { list = i == 0 ? s.facesB : s.facesA; n = i == 0 ? s.nB : s.nA; }
        if (n == 0)
            return false;
        ConstructionGrid g = s.grid;
        int f = list[(int) (hash(cycle * 31 + i * 977) * n) % n];
        int x = g.faceX(f), y = g.faceY(f), z = g.faceZ(f), side = g.faceSide(f);
        float nx = sideNx(side), nz = sideNz(side);
        if (!g.isStanding(x, y, z) || g.isStructure(x + (int) nx, y, z + (int) nz))
            return false;
        s.tool[0] = x + 0.5f + nx * 0.5f; s.tool[1] = y + 0.5f; s.tool[2] = z + 0.5f + nz * 0.5f;
        s.tool[3] = nx; s.tool[4] = nz;
        s.tool[5] = x; s.tool[6] = y; s.tool[7] = z;
        return true;
    }

    /**
     * One tool at time {@code t}, built in the WORLD round its struck face, then drawn as a hard-edged silhouette in
     * the screen plane. Round 9 critic: the hammer hung over the lawn 0.5-1 block outside the wall. Root cause: the
     * tool was laid out on SCREEN from the struck point - its grip a whole tool length out along the face's normal as
     * the screen shows it - so for every side face seen from the 65-degree camera the handle, and with the wind-up
     * and the hop the head too, lay over the ground beside the wall; and it hopped between two random blocks of a side
     * while the last blow's flash stayed on the old one (the flash "3 blocks away"). Now the pivot is the face centre
     * + 0.35 along its normal + 0.6 up, the head hangs from it and swings a 40-degree arc onto the face, the tool stays
     * on one face per blow (a blow every 0.8 s), and a check confirms the struck head is within 0.4 of its block.
     * {@code full}: the outline, rim and body; else a faint fill (a trail copy). {@code fly}: blocks risen (done).
     */
    static void drawTool(Site s, int i, float t, float alpha, float fly, boolean full) {
        if (alpha <= 0.01f)
            return;
        ConstructionShape.strikeBeat(t, s.beat);
        if (!toolFace(s, i, (int) s.beat[0]))
            return;
        boolean chisel = i == 1;
        float[] b = s.beat, tl = s.tool;
        float nx = tl[3], nz = tl[4];
        // the pivot, in the world
        float gx = tl[0] + nx * PIVOT_OUT, gy = tl[1] + PIVOT_UP + fly, gz = tl[2] + nz * PIVOT_OUT;
        // the head: struck at the face centre; wound back, swung ARC out round the pivot (in the plane of the normal
        // and up). Arriving at this face (the cycle's first 0.2 s) it lifts half back, winds the rest of the way
        // (0.3 s), strikes (0.1 s) and recoils; it fades in on arrival, so the hop from the last face is not a jump.
        float va = -PIVOT_OUT, vb = -PIVOT_UP;                              // pivot -> struck head (normal, up)
        float k = t - b[0] * ConstructionShape.BEAT_CYCLE;
        float wind = k < ConstructionShape.BEAT_MOVE ? 0.5f * Math.min(1, 3 * k / ConstructionShape.BEAT_MOVE)
                : k < ConstructionShape.BEAT_MOVE + ConstructionShape.BEAT_WIND ? 0.5f + 0.5f * b[2] : b[2];
        wind *= chisel ? 0.6f : 1f;
        alpha *= Math.min(1, k / 0.08f);
        float since = b[3];
        if (since >= 0 && since < 0.15f && b[1] >= 1)
            wind = Math.max(wind, 0.15f * (float) Math.sin(Math.PI * since / 0.15f));   // the recoil
        float phi = ARC * wind, cs = (float) Math.cos(phi), sn = (float) Math.sin(phi);
        float ha = va * cs - vb * sn, hb = va * sn + vb * cs;              // rotated toward +normal (out)
        float hx = gx + nx * ha, hy = gy + hb, hz = gz + nz * ha;
        if (wind == 0 && full && fly == 0) {
            // the debug check (round 9): the struck head must lie within its block grown by 0.4
            float bx = tl[5], by = tl[6], bz = tl[7];
            if (hx < bx - 0.4f || hx > bx + 1.4f || hy < by - 0.4f || hy > by + 1.4f || hz < bz - 0.4f || hz > bz + 1.4f) {
                if (!warnedTool) {
                    warnedTool = true;
                    ReignOfNether.LOGGER.warn("ConstructionFx: a tool's head {} {} {} missed its block {} {} {}", hx, hy,
                            hz, bx, by, bz);
                }
                return;
            }
        }
        // to the screen: the pivot and head pulled toward the camera together (ortho: no shift on screen)
        float ox = -BarFxClient.lookX * NUDGE, oy = -BarFxClient.lookY * NUDGE, oz = -BarFxClient.lookZ * NUDGE;
        gx += ox; gy += oy; gz += oz; hx += ox; hy += oy; hz += oz;
        float ex = hx - gx, ey = hy - gy, ez = hz - gz;
        float du = ex * BarFxClient.upX + ey * BarFxClient.upY + ez * BarFxClient.upZ;
        float dl = ex * BarFxClient.leftX + ey * BarFxClient.leftY + ez * BarFxClient.leftZ;
        float len = (float) Math.sqrt(du * du + dl * dl);
        if (len < 1e-3f)
            return;
        du /= len; dl /= len;
        for (int pass = full ? 0 : 2; pass < 3; pass++) {   // the gold outline, the dark rim, then the fill
            float e = pass == 0 ? OUTLINE : pass == 1 ? RIM : 0;
            int c0 = pass == 0 ? TOOL_OUTLINE : pass == 1 ? TOOL_RIM : TOOL_HANDLE;
            int c1 = pass == 0 ? TOOL_OUTLINE : pass == 1 ? TOOL_RIM : TOOL_BODY;
            float a = (pass == 0 ? 0.9f : pass == 1 ? 0.85f : 1f) * alpha;
            if (chisel) {
                float grip = len * 0.45f;
                bar(gx, gy, gz, du, dl, -e, grip + e, CHISEL_GRIP_W + 2 * e, CHISEL_GRIP_W + 2 * e, c0, a);
                bar(gx, gy, gz, du, dl, grip, len - grip + e, 0.14f + 2 * e, 0.05f + 2 * e, c1, a);
            } else {
                bar(gx, gy, gz, du, dl, -e, len + e, HANDLE_W + 2 * e, HANDLE_W + 2 * e, c0, a);
                bar(hx, hy, hz, -dl, du, -HEAD_L / 2 - e, HEAD_L + 2 * e, HEAD_W + 2 * e, HEAD_W + 2 * e, c1, a);
            }
        }
        if (full && fly == 0) {   // remember the working end for the light pass's glint
            s.heads[i * 3] = hx; s.heads[i * 3 + 1] = hy; s.heads[i * 3 + 2] = hz;
        }
    }

    /**
     * Tool {@code i}'s blow {@code back} cycles ago (0: the latest that has landed) into {@code s.tool}; returns how
     * long ago it landed (seconds), or -1 for none.
     */
    private static float blow(Site s, int i, float t, int back) {
        ConstructionShape.strikeBeat(t, s.beat);
        float since = s.beat[3], c = s.beat[0];
        if (since < 0)
            return -1;
        float hit = ConstructionShape.BEAT_MOVE + ConstructionShape.BEAT_WIND + ConstructionShape.BEAT_STRIKE;
        int sc = (int) (t - c * ConstructionShape.BEAT_CYCLE >= hit ? c : c - 1) - back;
        if (sc < 0 || !toolFace(s, i, sc))
            return -1;
        s.beat[0] = sc;
        return since + back * ConstructionShape.BEAT_CYCLE;
    }

    /**
     * A blow's consequence on the struck face (round 9 critic): a 1-block #FFF8E0 quad fading 0.8 -> 0 over 250 ms,
     * then a #FFE9B0 afterglow 0.6 -> 0 over 1.2 s (so a still catches it), the last two blows' each; and a crisp
     * white flash at the contact for the first ~2 frames. Normal-blended.
     */
    static void drawStruck(Site s, int i, float t) {
        for (int back = 1; back >= 0; back--) {
            float since = blow(s, i, t, back);
            if (since < 0 || since >= 1.45f)
                continue;
            float[] tl = s.tool;
            int c;
            float a;
            if (since < 0.25f) { c = FLASH; a = 0.8f * (1 - since / 0.25f); }
            else { c = AFTERGLOW; a = 0.6f * (1 - (since - 0.25f) / 1.2f); }
            float nx = tl[3], nz = tl[4], tx = -nz * 0.5f, tz = nx * 0.5f;
            float fx = tl[0] + nx * 0.01f, fz = tl[2] + nz * 0.01f, y0 = tl[6], y1 = y0 + 1;
            BarFxClient.v(fx - tx, y0, fz - tz, c, a);
            BarFxClient.v(fx + tx, y0, fz + tz, c, a);
            BarFxClient.v(fx + tx, y1, fz + tz, c, a);
            BarFxClient.v(fx - tx, y1, fz - tz, c, a);
            if (since < 0.05f) {
                float x = tl[0] - BarFxClient.lookX * NUDGE, y = tl[1] - BarFxClient.lookY * NUDGE;
                float z = tl[2] - BarFxClient.lookZ * NUDGE;
                BarFxClient.softDisc(x, y, z, 0.3f, 0xFFFFFF, 0.9f, 0xFFFFFF, 0.6f, 12, 0, 0);
            }
        }
    }

    /** A blow's dust: three small soft puffs of stone dust swelling and drifting off the struck face (0.5 s). */
    static void drawDust(Site s, int i, float t) {
        float since = blow(s, i, t, 0);
        if (since < 0 || since >= 0.5f)
            return;
        int sc = (int) s.beat[0];
        float[] tl = s.tool;
        float e = since / 0.5f;
        for (int j = 0; j < 3; j++) {
            float ang = (float) (hash(sc * 41 + i * 7 + j * 3) * 2 * Math.PI);
            float d = 0.1f + 0.2f * e;
            BarFxClient.softDisc(tl[0] + tl[3] * 0.3f * e + (float) Math.cos(ang) * d, tl[1] + 0.3f * e,
                    tl[2] + tl[4] * 0.3f * e + (float) Math.sin(ang) * d, 0.14f + 0.18f * e, DUST, 0.35f * (1 - e), DUST,
                    0, 8, j, 0.15f);
        }
    }

    /** A tool's light: a small glint on its head and, after each blow, 8 gold sparks off the face (0.4 s). */
    static void drawToolLight(Site s, int i, float t) {
        ConstructionShape.strikeBeat(t, s.beat);
        if (!toolFace(s, i, (int) s.beat[0]))
            return;
        float tw = 0.75f + 0.25f * (float) Math.sin(t * 5 + i * 2.1f);
        BarFxClient.softDisc(s.heads[i * 3], s.heads[i * 3 + 1], s.heads[i * 3 + 2], 0.1f, LIGHT, 0.25f * tw, GOLD, 0,
                8, 0, 0);
        float since = blow(s, i, t, 0);
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

    /** Strategic zoom: the tools shrink to golden glints walking the course. */
    static void drawGlints(Site s, float front, float clock) {
        for (int i = 0; i < 2; i++) {
            ConstructionShape.perimeterPoint(s.cut, i / 2f + clock * 0.05f, s.pt);
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
     * left; unit), {@code len} long, {@code w0} wide at its start and {@code w1} at its end, one alpha throughout.
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

    /** A hard-edged camera-facing line between two world points, {@code w} wide, one alpha throughout. */
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
