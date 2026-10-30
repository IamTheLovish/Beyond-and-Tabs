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
 * chroma is capped). What decides it is what lies UNDER it: a cool at 0.45 over a green lawn lands near neutral. The
 * band is #8FB0F0 at 0.7 over a neutral #F4F4F6 halo: ~(150, 175, 205) over the lawn, B - R ~56 after the grade.
 */
final class ConstructionFx {

    private ConstructionFx() { }

    private static final int GOLD = 0xFFD27A, LIGHT = 0xFFF3D2;
    /** The halo: a neutral white so gold is the only warm element (round 9 critic). */
    private static final int HALO = 0xF4F4F6;
    /** The cool accent, alpha-blended (see the class note for the pixel arithmetic): the halo's inner band and the
     *  pool where the light lands. */
    private static final int COOL = 0x8FB0F0;
    /** The rim: a 2 px #FFF6DC core in a 4 px #FFE9B0 glow (round 10 critic: the hero); its beads' glow. */
    private static final int RIM_CORE = 0xFFF6DC, RIM_GLOW = 0xFFE9B0, BEAD_GLOW = 0xFFD66B;
    /** The ghost: a pale cool fill, gold silhouette edges (round 10 critic). */
    private static final int GHOST_FILL = 0xE6ECF8, GHOST_EDGE = 0xF2DFAE;
    /** The cone of light: a warm white, additive, 0.18 at its top fading to nothing at the ground. */
    private static final int RAY = 0xFFF8E6;
    /** Sparks; the blow's white flash and the struck face's warm afterglow; dust. */
    private static final int SPARK = 0xF2DFAE, FLASH = 0xFFF8E0, AFTERGLOW = 0xFFE9B0, DUST = 0xD9D3C6, BLOW_FLASH = 0xFFF4D0;
    /** The tools, normal-blended and SHADED (round 10 critic: the flat sprite with a dark outline read as a loot icon):
     *  each part a dark side and a lit side, a metallic highlight on the head. */
    private static final int HANDLE_DARK = 0xA88F66, HANDLE_LIT = 0xF6EEDB, HEAD_DARK = 0x9C6A1A, HEAD_LIT = 0xF0C35A,
            BLADE_DARK = 0x8E97A6, BLADE_LIT = 0xE6EBF2;
    /** Seconds the completion beat lasts (the halo's 0.6 s swell is the longest part, the tools' flight 0.6 s). */
    private static final float DONE_SECONDS = 0.8f;
    /** Past this RTS zoom (strategic) the ghost loses its lines, the tools become glints and the light stops. */
    private static final float FAR_ZOOM = 70;
    /** The halo's ring line stands this far outside the course's footprint (round 10 critic: pulled 0.5 in, clear of
     *  the selection box). */
    private static final float RING_PAD = 0.25f;
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
        final float[] heads = new float[3 * TOOLS];    // each tool's working end this frame {x, y, z} (for its glint)
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
            drawLight(s, front, clock);
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
     * rim. Round 10 critic: still a hard ring track (grass to band in 6 px on both sides - the cool band's 0.08-block
     * fringes - no puffs visible), a second selection indicator. Now: pulled in to RING_PAD (0.25) outside the walls,
     * clear of the selection box; a soft falloff 0.4 block inward and 0.65 outward (its outer edge wandering with slow
     * noise), neutral #F4F4F6, 0.35-0.55 pulsing at 0.6 Hz on the near side, 0.25 at most on the far side; 6-8 flat
     * puffs on it (0.5-0.75 block radius, +-25% scale, 0.3-0.5 alpha) drifting at 0.1 block a second; then the cool band
     * over it with a soft profile (0 -> #8FB0F0 at 0.7 over the middle 0.2 -> 0, 0.7 block in all): over the lawn it
     * samples ~(144-155, 170-179, 200-211) after the grade, B - R ~56 (bat_postfx.fsh worked by hand).
     * {@code grow} scales it about its centre (the completion swell).
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
                float a = pass == 0 ? pulse * side * alpha * (0.8f + 0.2f * wob) : 0.7f * alpha;   // the accent: both sides
                float o = 0.65f * (0.75f + 0.5f * wob);                        // how far the falloff reaches out
                if (k > 0) {
                    if (pass == 0) {
                        strip(px, pz, pnx, pnz, pt[0], pt[1], pt[2], pt[3], y, -0.4f, -0.4f, 0, 0, 0, 0, pa, a, HALO);
                        strip(px, pz, pnx, pnz, pt[0], pt[1], pt[2], pt[3], y, 0, 0, pa, a, po, o, 0, 0, HALO);
                    } else {
                        float yc = y + 0.01f;
                        strip(px, pz, pnx, pnz, pt[0], pt[1], pt[2], pt[3], yc, -0.35f, -0.35f, 0, 0, -0.1f, -0.1f, pa,
                                a, COOL);
                        strip(px, pz, pnx, pnz, pt[0], pt[1], pt[2], pt[3], yc, -0.1f, -0.1f, pa, a, 0.1f, 0.1f, pa, a,
                                COOL);
                        strip(px, pz, pnx, pnz, pt[0], pt[1], pt[2], pt[3], yc, 0.1f, 0.1f, pa, a, 0.35f, 0.35f, 0, 0,
                                COOL);
                    }
                }
                px = pt[0]; pz = pt[1]; pnx = pt[2]; pnz = pt[3]; pa = a; po = o;
            }
        }
        // the puffs: flat, 0.5-0.75 block radius, scale +-25% (slowly breathing), alpha 0.3-0.5, drifting round at
        // 0.1 block a second; drawn over the cool band so they break it up too
        int puffs = Math.max(6, Math.min(8, Math.round(len / 5f)));
        for (int k = 0; k < puffs; k++) {
            float r = (0.5f + 0.25f * hash(k * 11 + 7)) * (1 + 0.25f * (float) Math.sin(clock * 0.5f + k * 2.3f));
            ConstructionShape.ringPoint(box, pad + 0.15f, (k + 0.4f * hash(k * 3 + 1)) / puffs + clock * 0.1f / len, pt);
            float side = 1 - (1 - 0.25f / 0.55f) * Math.max(0, Math.min(1, pt[2] * lx + pt[3] * lz));
            flatDisc(pt[0], y + 0.03f, pt[1], r, HALO, (0.3f + 0.2f * hash(k * 5 + 3)) * side * alpha, 14);
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
     * The ghost over every block still MISSING and nothing else (ConstructionGrid; round 9: drawn from one build front
     * it caged standing walls). Round 10 critic: the per-block lines were a thicket (every face of every missing block
     * at one weight - moire at 30 px, a hairball on a big keep), and the cream fill vanished over the grass and the
     * cream edges on the white stone. Now:
     * <ul>
     *   <li>the fill: each missing block's faces turned to the camera with an open neighbour, a pale COOL #E6ECF8 at
     *   0.30 on the sides and 0.42 on the tops - cool "not yet" against the warm built stone;</li>
     *   <li>GOLD edges on the silhouette of the missing volume only: an edge where the face does not continue into a
     *   neighbouring missing block's coplanar face, 1.5 px #F2DFAE at 0.6 (under half the rim's brightness); each
     *   shared edge once; the interior seams 1 px at 0.15; at most EDGE_CAP edges a building, interior dropped
     *   first.</li>
     * </ul>
     */
    static void drawGhost(Site s, boolean far) {
        ConstructionGrid g = s.grid;
        float lx = BarFxClient.lookX, lz = BarFxClient.lookZ;
        int sx = lx > 0 ? -1 : 1, sz = lz > 0 ? -1 : 1;   // the x / z side turned to us
        int n = g.missingCount();
        for (int i = 0; i < n; i++) {
            int x = g.missingX(i), y = g.missingY(i), z = g.missingZ(i);
            if (!g.isStructure(x, y + 1, z))
                fill(x, y + 1, z, x + 1, y + 1, z, x + 1, y + 1, z + 1, x, y + 1, z + 1, 0.42f);
            if (!g.isStructure(x + sx, y, z)) {
                float fx = sx > 0 ? x + 1 : x;
                fill(fx, y, z, fx, y, z + 1, fx, y + 1, z + 1, fx, y + 1, z, 0.27f);
            }
            if (!g.isStructure(x, y, z + sz)) {
                float fz = sz > 0 ? z + 1 : z;
                fill(x, y, fz, x + 1, y, fz, x + 1, y + 1, fz, x, y + 1, fz, 0.30f);
            }
        }
        if (far)
            return;
        int drawn = 0;
        for (int pass = 0; pass < 2 && drawn < EDGE_CAP; pass++)   // the silhouette first, then the interior seams
            for (int i = 0; i < n && drawn < EDGE_CAP; i++)
                drawn += ghostEdges(g, g.missingX(i), g.missingY(i), g.missingZ(i), sx, sz, pass == 1, EDGE_CAP - drawn);
    }

    /** The most ghost edges one building draws (round 10 critic: ~400, the interior dropped first). */
    private static final int EDGE_CAP = 400;

    /**
     * The edges of missing block (x, y, z)'s visible faces of one kind ({@code interior} or silhouette), at most
     * {@code room}. An edge is interior where the face continues into the neighbouring missing block's coplanar face.
     * Each edge is drawn by one face: a side face leaves its top edge to the top face when that shows, and the -z/+z
     * face leaves the corner it shares with the x face when that shows. Returns how many it drew.
     */
    private static int ghostEdges(ConstructionGrid g, int x, int y, int z, int sx, int sz, boolean interior, int room) {
        int drawn = 0;
        boolean top = !g.isStructure(x, y + 1, z), xf = !g.isStructure(x + sx, y, z), zf = !g.isStructure(x, y, z + sz);
        float fx = sx > 0 ? x + 1 : x, fz = sz > 0 ? z + 1 : z, bx = sx > 0 ? x : x + 1, bz = sz > 0 ? z : z + 1;
        float y1 = y + 1;
        if (top) {   // the top face's four edges: toward +-x and +-z
            for (int e = 0; e < 4 && drawn < room; e++) {
                int px = e == 0 ? 1 : e == 1 ? -1 : 0, pz = e == 2 ? 1 : e == 3 ? -1 : 0;
                if (seam(g, x, y, z, px, 0, pz, 0, 1, 0) != interior)
                    continue;
                float ax = px > 0 ? x + 1 : px < 0 ? x : x, az = pz > 0 ? z + 1 : pz < 0 ? z : z;
                float ex = px != 0 ? ax : x + 1, ez = pz != 0 ? az : z + 1;
                drawn += edge(ax, y1, az, ex, y1, ez, interior);
            }
        }
        if (xf) {    // the x face (normal sx): its bottom, its far (-sz) side, its near (+sz) side; its top if no top face
            if (drawn < room && seam(g, x, y, z, 0, -1, 0, sx, 0, 0) == interior)
                drawn += edge(fx, y, z, fx, y, z + 1, interior);
            if (drawn < room && seam(g, x, y, z, 0, 0, -sz, sx, 0, 0) == interior)
                drawn += edge(fx, y, bz, fx, y1, bz, interior);
            if (drawn < room && seam(g, x, y, z, 0, 0, sz, sx, 0, 0) == interior)
                drawn += edge(fx, y, fz, fx, y1, fz, interior);
            if (!top && drawn < room && seam(g, x, y, z, 0, 1, 0, sx, 0, 0) == interior)
                drawn += edge(fx, y1, z, fx, y1, z + 1, interior);
        }
        if (zf) {    // the z face (normal sz): its bottom, its far (-sx) side, its near side unless the x face drew it
            if (drawn < room && seam(g, x, y, z, 0, -1, 0, 0, 0, sz) == interior)
                drawn += edge(x, y, fz, x + 1, y, fz, interior);
            if (drawn < room && seam(g, x, y, z, -sx, 0, 0, 0, 0, sz) == interior)
                drawn += edge(bx, y, fz, bx, y1, fz, interior);
            if (!xf && drawn < room && seam(g, x, y, z, sx, 0, 0, 0, 0, sz) == interior)
                drawn += edge(fx, y, fz, fx, y1, fz, interior);
            if (!top && drawn < room && seam(g, x, y, z, 0, 1, 0, 0, 0, sz) == interior)
                drawn += edge(x, y1, fz, x + 1, y1, fz, interior);
        }
        return drawn;
    }

    /**
     * Whether the face of missing block (x, y, z) with normal (nx, ny, nz) continues across its edge toward (px, py,
     * pz) into the neighbour's coplanar face - an interior seam - rather than ending there (a silhouette edge).
     */
    private static boolean seam(ConstructionGrid g, int x, int y, int z, int px, int py, int pz, int nx, int ny, int nz) {
        int ax = x + px, ay = y + py, az = z + pz;
        return g.isMissing(ax, ay, az) && !g.isStructure(ax + nx, ay + ny, az + nz);
    }

    /** One ghost edge: the silhouette 1.5 px gold at 0.6, an interior seam 1 px at 0.15. Returns 1. */
    private static int edge(float x0, float y0, float z0, float x1, float y1, float z1, boolean interior) {
        line(x0, y0, z0, x1, y1, z1, interior ? 0.035f : 0.05f, GHOST_EDGE, interior ? 0.15f : 0.6f);
        return 1;
    }

    /** One ghost face's fill. */
    private static void fill(float ax, float ay, float az, float bx, float by, float bz, float cx, float cy, float cz,
                             float dx, float dy, float dz, float a) {
        BarFxClient.v(ax, ay, az, GHOST_FILL, a);
        BarFxClient.v(bx, by, bz, GHOST_FILL, a);
        BarFxClient.v(cx, cy, cz, GHOST_FILL, a);
        BarFxClient.v(dx, dy, dz, GHOST_FILL, a);
    }

    // ------------------------------------------------------------------ the rim

    /**
     * The RIM (round 9 critic: continuous along the full exposed top course of every standing wall segment): every
     * ConstructionGrid rim edge - the top edge of a standing block with a missing block on it, on an open side - inset
     * RIM_INSET into the block. Round 10 critic: the rim is the hero - a 2 px #FFF6DC core at 0.95 over a 4 px #FFE9B0
     * glow at 0.45, both pulsing 0.8-1.0 over 1.2 s (the ghost's gold edges stay under half of it). Beads, one per 6
     * blocks of rim, run along it at 1.5 blocks a second: a white core in a #FFD66B glow at 0.8. Normal-blended.
     */
    static void drawRim(Site s, float clock, boolean far) {
        ConstructionGrid g = s.grid;
        int n = g.rimCount();
        if (n == 0)
            return;
        float wg = far ? 0.2f : 0.1f, wc = far ? 0.12f : 0.07f;
        float pulse = 0.9f + 0.1f * (float) Math.sin(clock * 2 * Math.PI / 1.2f);   // 0.8-1.0 over 1.2 s
        float ag = 0.45f * pulse, ac = 0.95f * pulse;
        for (int i = 0; i < n; i++) {
            int side = g.faceSide(i);
            float ix = -sideNx(side) * RIM_INSET, iz = -sideNz(side) * RIM_INSET, y = g.rimY(i) + 0.02f;
            float ax = g.rimX0(i) + ix, az = g.rimZ0(i) + iz, bx = g.rimX1(i) + ix, bz = g.rimZ1(i) + iz;
            BarFxClient.streak(ax, y, az, bx, y, bz, wg, RIM_GLOW, ag, ag);
            line(ax, y, az, bx, y, bz, wc, RIM_CORE, ac);
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
     * Holy light with restraint (round 10 critic: the warm wash flattened the stone to ~250): a wash on the standing
     * surface where the light lands, 0.05 at its centre to nothing at 1.5 blocks (additive: the critic's cap was 0.10,
     * but 0.10 lifts ~222 stone to ~247 - 0.05 keeps it under ~235), and
     * 2-3 slow shafts round it - 0.2 block wide, 0.08 at the foot fading to nothing 6 blocks up, turning once in 20 s,
     * the first a touch stronger (0.11) so one shows at the RTS zoom. Additive.
     */
    static void drawLight(Site s, float front, float clock) {
        landing(s, clock);
        float x = s.pt[0], z = s.pt[1];
        float yb = Float.isNaN(s.pt[2]) ? front : s.pt[2];
        if (!Float.isNaN(s.pt[2]))
            flatDisc(x, yb + 0.04f, z, 1.5f, RAY, 0.05f, 16);   // additive: stone ~222 + 0.05 x 255 ~ 235
        for (int k = 0; k < 3; k++) {
            double ang = clock * 2 * Math.PI / 20 + k * 2 * Math.PI / 3;
            float r = 0.9f + 0.3f * k / 2f;
            float sx = x + (float) Math.cos(ang) * r, sz = z + (float) Math.sin(ang) * r;
            float a = k == 0 ? 0.11f : 0.08f;
            BarFxClient.streak(sx, yb + 6f, sz, sx, yb + 0.05f, sz, 0.1f, RAY, 0f, a);
        }
    }

    /** Slow gold motes rising inside the footprint: five at a time at 0.4, 0.3 block a second, each living 6 s. */
    static void drawMotes(Site s, float front, float clock) {
        float x0 = s.course[0], x1 = s.course[1], z0 = s.course[2], z1 = s.course[3];
        for (int j = 0; j < 5; j++) {
            float t = clock / 6f + j / 5f;
            int cyc = (int) Math.floor(t);
            float k = t - cyc;
            float x = x0 + (x1 - x0) * hash(cyc * 7 + j * 131) + 0.15f * (float) Math.sin(clock * 0.9f + j);
            float z = z0 + (z1 - z0) * hash(cyc * 13 + j * 71) + 0.15f * (float) Math.cos(clock * 0.7f + j * 2);
            BarFxClient.softDisc(x, front + 0.2f + 1.8f * k, z, 0.07f, GOLD, 0.4f * (float) Math.sin(Math.PI * k), GOLD,
                    0, 8, 0, 0);
        }
    }

    // ------------------------------------------------------------------ the tools

    /** Tools working at once (round 10 critic: 3-4 cycling): two hammers and a chisel, on the sides turned to us. */
    private static final int TOOLS = 3;
    /** Hammer / chisel parts (blocks): ~0.6 block in all (round 10 critic); the handle's length is the pivot's
     *  distance from the struck point. */
    private static final float HANDLE_W = 0.1f, HEAD_L = 0.3f, HEAD_W = 0.16f, CHISEL_GRIP_W = 0.13f;
    /** The pivot stands this far out along the face's normal and this far above the struck point: the tool tilted
     *  ~45 degrees into the face (round 10 critic: 35-50); the swing is a 40-degree arc. */
    private static final float PIVOT_OUT = 0.3f, PIVOT_UP = 0.3f, ARC = 0.7f;
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
        boolean a = (i & 1) == 0;
        int[] list = a ? s.facesA : s.facesB;
        int n = a ? s.nA : s.nB;
        if (n == 0) { list = a ? s.facesB : s.facesA; n = a ? s.nB : s.nA; }
        if (n == 0)
            return false;
        ConstructionGrid g = s.grid;
        // two tools on one side keep apart: the second starts half the list away
        int f = list[((int) (hash(cycle * 31 + i * 977) * n) + (i / 2) * (n / 2)) % n];
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
     * + 0.3 along its normal + 0.3 up (round 10: ~45 degrees into the face, ~0.6 block), the head swings a 40-degree
     * arc onto the face, the tool stays on one face per blow (a blow every 0.9 s), and a check confirms the struck head
     * is within 0.4 of its block. Shaded, no outline (round 10: a flat sprite with a dark outline read as loot).
     * {@code full}: the shaded tool; else its dark base only, faint (a trail copy). {@code fly}: blocks risen (done).
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
        // the light comes from the screen's upper left: the side of each part facing it is lit
        float pu = -dl, pl = du;                                            // the screen perpendicular
        float lit = pu * 0.7f + pl * 0.7f >= 0 ? 1 : -1;
        float ox2 = (pu * BarFxClient.upX + pl * BarFxClient.leftX) * lit, oy2 = (pu * BarFxClient.upY + pl * BarFxClient.leftY) * lit;
        float oz2 = (pu * BarFxClient.upZ + pl * BarFxClient.leftZ) * lit;
        if (chisel) {
            float grip = len * 0.45f;
            bar(gx, gy, gz, du, dl, 0, grip, CHISEL_GRIP_W, CHISEL_GRIP_W, HANDLE_DARK, alpha);
            bar(gx, gy, gz, du, dl, grip, len - grip + 0.08f, 0.1f, 0.035f, BLADE_DARK, alpha);
            if (full) {
                float q = CHISEL_GRIP_W * 0.25f;
                bar(gx + ox2 * q, gy + oy2 * q, gz + oz2 * q, du, dl, 0, grip, CHISEL_GRIP_W * 0.5f, CHISEL_GRIP_W * 0.5f,
                        HANDLE_LIT, alpha);
                bar(gx + ox2 * 0.025f, gy + oy2 * 0.025f, gz + oz2 * 0.025f, du, dl, grip, len - grip + 0.06f, 0.05f, 0.015f,
                        BLADE_LIT, alpha);
            }
        } else {
            bar(gx, gy, gz, du, dl, 0, len, HANDLE_W, HANDLE_W, HANDLE_DARK, alpha);
            bar(hx, hy, hz, -dl, du, -HEAD_L / 2, HEAD_L, HEAD_W, HEAD_W, HEAD_DARK, alpha);
            if (full) {
                float q = HANDLE_W * 0.25f;
                bar(gx + ox2 * q, gy + oy2 * q, gz + oz2 * q, du, dl, 0, len - HEAD_W * 0.5f, HANDLE_W * 0.5f,
                        HANDLE_W * 0.5f, HANDLE_LIT, alpha);
                // the head's lit half: toward the light along the handle's line (the head's top face)
                float hq = HEAD_W * 0.25f, sgn = du * 0.7f + dl * 0.7f >= 0 ? 1 : -1;
                float tx = (du * BarFxClient.upX + dl * BarFxClient.leftX) * sgn * hq;
                float ty = (du * BarFxClient.upY + dl * BarFxClient.leftY) * sgn * hq;
                float tz = (du * BarFxClient.upZ + dl * BarFxClient.leftZ) * sgn * hq;
                bar(hx + tx, hy + ty, hz + tz, -dl, du, -HEAD_L / 2, HEAD_L, HEAD_W * 0.5f, HEAD_W * 0.5f, HEAD_LIT, alpha);
                // the metallic highlight: a small white spot on the lit corner
                float hl = HEAD_L * 0.3f * lit;
                BarFxClient.softDisc(hx + tx * 1.4f + ox2 * hl, hy + ty * 1.4f + oy2 * hl, hz + tz * 1.4f + oz2 * hl, 0.035f,
                        0xFFFFFF, alpha, 0xFFFFFF, alpha * 0.8f, 8, 0, 0);
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
            if (since < 0.05f) {   // ~3 frames
                float x = tl[0] - BarFxClient.lookX * NUDGE, y = tl[1] - BarFxClient.lookY * NUDGE;
                float z = tl[2] - BarFxClient.lookZ * NUDGE;
                BarFxClient.softDisc(x, y, z, 0.3f, BLOW_FLASH, 0.9f, BLOW_FLASH, 0.6f, 12, 0, 0);
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

    /** A tool's light: a small glint on its head and, after each blow, 4 small 2 px gold sparks off the face (0.4 s). */
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
        for (int j = 0; j < 4; j++) {   // round 10 critic: 3-5 small sparks
            float ang = (float) (hash(sc * 131 + i * 17 + j * 7) * 2 * Math.PI);
            float sp = 1.2f + 1.3f * hash(sc * 57 + j * 29 + i);
            float vx = (float) Math.cos(ang) * sp + tl[3] * 1.5f, vz = (float) Math.sin(ang) * sp + tl[4] * 1.5f;
            float vy = 2f + 1.5f * hash(sc * 23 + j * 3 + i * 5);
            float sx = x + vx * since, sy = y + vy * since - 6 * since * since, sz = z + vz * since;
            float vyNow = vy - 12 * since, l = 0.06f;
            line(sx - vx * l, sy - vyNow * l, sz - vz * l, sx, sy, sz, 0.035f, SPARK, f);   // 2 px
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
