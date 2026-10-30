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
 *   <li>a cool slate GHOST over every block still missing - and nothing else (ConstructionGrid) - outlined only on its
 *   silhouette: cool "not yet" against the warm white "done" (round 12 critic: separate them by hue);</li>
 *   <li>ONE build front: a warm gold line along the highest built row, a cool strip under it on the face;</li>
 *   <li>a few soft beams rising from that row, slow gold motes;</li>
 *   <li>up to three small golden tools striking built faces under the front, each blow a flash and sparks;</li>
 *   <li>done: a cloud halo swells and fades, a flash on the top, a light ring runs out over the ground, the tools fly
 *   up.</li>
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
    private static final int COOL = 0xB9CBEE;   // round 11 critic: desaturated (a periwinkle kerb at #8FB0F0, 0.7)
    /** The rim: a 2 px #FFF6DC core in a 4 px #FFE9B0 glow (round 10 critic: the hero); its beads' glow. */
    private static final int FRONT_WARM = 0xFFD27A, FRONT_COOL = 0x7FB2FF, BEAD_GLOW = 0xFFD66B;
    /** The ghost: a pale cool fill, gold silhouette edges (round 10 critic). */
    private static final int GHOST_FILL = 0x6F86A8, GHOST_EDGE = 0xB8C8E0;
    private static final float GHOST_A = 0.28f;
    /** The cone of light: a warm white, additive, 0.18 at its top fading to nothing at the ground. */
    private static final int RAY = 0xFFF8E6;
    /** Sparks; the blow's white flash and the struck face's warm afterglow; dust. */
    private static final int SPARK = 0xF2DFAE, STRUCK_FACE = 0xF2DC96, DUST = 0xD9D3C6, BLOW_FLASH = 0xFFF4D0;
    /** The tools, normal-blended and SHADED (round 10 critic: the flat sprite with a dark outline read as a loot icon):
     *  each part a dark side and a lit side, a metallic highlight on the head. */
    private static final int GOLD_DARK = 0xC9A24A, IRON_DARK = 0x8A8A8A;
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
        final int[] rowEdges;                          // rim edges per height (recount), to find the build front
        float frontRow = Float.NaN;                    // the build front's height: the row with the most rim edges
        Site(ConstructionShape s, ConstructionGrid g) {
            shape = s; grid = g; standing = new int[s.layers()]; rowEdges = new int[s.layers() + 2];
        }
    }
    private static final Map<BuildingPlacement, Site> SITES = new WeakHashMap<>();
    /** The bounds of the rising White Angel sites drawn last frame {x0, x1, y0, y1, z0, z1} (absorbsNano). */
    private static final int MAX_ACTIVE = 32;
    private static final float[] ACTIVE = new float[6 * MAX_ACTIVE];
    private static int activeN;

    /**
     * Whether a worker's nano beam aimed at (x, y, z) lands on a rising White Angel site - BarFxClient.nano then draws
     * no beam. Round 13 critic: a fan of 7-9 hard warm lines from one point under the HUD, converging below the
     * ground, read as clutter over the site in every round since 11. That fan is not ConstructionFx's: it is the
     * builders' nanolathe beams (BuildingPlacement's build tick -> BarFx.nano, golden for the Sunforged), one per
     * worker, every ~8 ticks. On an Angel site the construction effect already shows the work (tools, beams from the
     * front), so the fan is dropped there; every other faction, and repair / reclaim off an Angel site, keep it.
     */
    static boolean absorbsNano(float x, float y, float z) {
        for (int i = 0; i < activeN; i++) {
            int a = i * 6;
            if (x >= ACTIVE[a] && x <= ACTIVE[a + 1] && y >= ACTIVE[a + 2] && y <= ACTIVE[a + 3] && z >= ACTIVE[a + 4]
                    && z <= ACTIVE[a + 5])
                return true;
        }
        return false;
    }
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
            // standing = the exact state (isPlaced) or the same block in another state: a slab's type, a lantern's
            // hanging, a banner's rotation or a candle's lit flag differ while the block plainly stands - round 12: the
            // ghost was drawn over standing quartz
            boolean placed = b.isPlaced(level) || level.getBlockState(bp).getBlock() == b.getBlockState().getBlock();
            s.grid.mark(bp.getX(), bp.getY(), bp.getZ(), placed);
            if (placed)
                s.standing[s.shape.layerOf(bp.getY())]++;
        }
        s.grid.finish();
        // the build front: the rim height with the most edges (ties: the higher) - round 13 critic: "the highest built
        // row" was often a lone pillar top, a 2-3 px tick at the RTS zoom, not the course being laid
        java.util.Arrays.fill(s.rowEdges, 0);
        ConstructionGrid g = s.grid;
        for (int i = 0, n = g.rimCount(); i < n; i++) {
            int r = (int) g.rimY(i) - s.shape.minY();
            if (r >= 0 && r < s.rowEdges.length)
                s.rowEdges[r]++;
        }
        int best = -1;
        for (int r = 0; r < s.rowEdges.length; r++)
            if (s.rowEdges[r] > 0 && (best < 0 || s.rowEdges[r] >= s.rowEdges[best]))
                best = r;
        s.frontRow = best < 0 ? Float.NaN : s.shape.minY() + best;
    }

    /**
     * Called from BarFxClient.render before its early return (a site can rise with no other effect alive). The
     * completion beat starts the first frame a known rising site reads {@code isBuilt}: the game's own hook is
     * BuildingPlacement.onBuilt (its client branch plays the completion sound and sets isBuilt), so this is within a
     * frame of it without touching that shared class; a site never seen rising (loaded in finished) gets no beat.
     */
    static void render(MultiBufferSource.BufferSource buffers, Matrix4f mat, float clock, float dt) {
        activeN = 0;
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
            if (activeN < MAX_ACTIVE) {   // this site's bounds, for absorbsNano
                int a = activeN++ * 6;
                ACTIVE[a] = s.grid.minX() - 0.5f; ACTIVE[a + 1] = s.grid.maxX() + 0.5f;
                ACTIVE[a + 2] = s.shape.minY() - 0.5f; ACTIVE[a + 3] = s.shape.minY() + s.shape.layers() + 0.5f;
                ACTIVE[a + 4] = s.grid.minZ() - 0.5f; ACTIVE[a + 5] = s.grid.maxZ() + 0.5f;
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
            // (no halo while it rises - round 12 critic: it lay as a white ground haze at the walls' foot; the completion
            // beat still swells it once)
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
                float a = pass == 0 ? pulse * side * alpha * (0.8f + 0.2f * wob) : 0.4f * alpha;   // the accent: both sides
                float o = 1.0f * (0.75f + 0.5f * wob);                         // how far the falloff reaches out
                if (k > 0) {
                    if (pass == 0) {   // Gaussian falloffs: 0.5 block inward, ~1 block outward (its reach wandering)
                        gauss(px, pz, pnx, pnz, pt[0], pt[1], pt[2], pt[3], y, -0.5f, -0.5f, pa, a, HALO);
                        gauss(px, pz, pnx, pnz, pt[0], pt[1], pt[2], pt[3], y, po, o, pa, a, HALO);
                    } else {           // the cool band: a Gaussian 0.35 each way, no edge
                        float yc = y + 0.01f;
                        gauss(px, pz, pnx, pnz, pt[0], pt[1], pt[2], pt[3], yc, -0.35f, -0.35f, pa, a, COOL);
                        gauss(px, pz, pnx, pnz, pt[0], pt[1], pt[2], pt[3], yc, 0.35f, 0.35f, pa, a, COOL);
                    }
                }
                px = pt[0]; pz = pt[1]; pnx = pt[2]; pnz = pt[3]; pa = a; po = o;
            }
        }
        // the puffs: flat, 0.5-0.75 block radius, scale +-25% (slowly breathing), alpha 0.3-0.5, each at its own height
        // (+-0.3 block, so their discs cross the band at different places on screen), drifting round at 0.15 block a
        // second; drawn over the cool band so they break it up too - no straight edge survives at the RTS zoom
        int puffs = Math.max(6, Math.min(8, Math.round(len / 5f)));
        for (int k = 0; k < puffs; k++) {
            float r = (0.5f + 0.25f * hash(k * 11 + 7)) * (1 + 0.25f * (float) Math.sin(clock * 0.5f + k * 2.3f));
            ConstructionShape.ringPoint(box, pad + 0.15f, (k + 0.4f * hash(k * 3 + 1)) / puffs + clock * 0.15f / len, pt);
            float side = 1 - (1 - 0.25f / 0.55f) * Math.max(0, Math.min(1, pt[2] * lx + pt[3] * lz));
            float py = y + 0.3f * (2 * hash(k * 7 + 1) - 1) + 0.05f * (float) Math.sin(clock * 0.7f + k);
            flatDisc(pt[0], py + 0.03f, pt[1], r, HALO, (0.3f + 0.2f * hash(k * 5 + 3)) * side * alpha, 14);
        }
    }

    /**
     * A Gaussian falloff of a band between two ring points (p0 with normal n0, p1 with n1): {@code a0} / {@code a1} on
     * the ring line falling to ~0 at offsets {@code e0} / {@code e1} along the normals (negative: inward), in five
     * flat steps - no hard edge anywhere.
     */
    private static void gauss(float x0, float z0, float nx0, float nz0, float x1, float z1, float nx1, float nz1,
                              float y, float e0, float e1, float a0, float a1, int c) {
        for (int j = 0; j < 5; j++) {
            float f0 = j / 5f, f1 = (j + 1) / 5f;
            float g0 = (float) Math.exp(-0.5 * (2.5 * f0) * (2.5 * f0)), g1 = (float) Math.exp(-0.5 * (2.5 * f1) * (2.5 * f1));
            if (j == 4) g1 = 0;
            strip(x0, z0, nx0, nz0, x1, z1, nx1, nz1, y, e0 * f0, e1 * f0, a0 * g0, a1 * g0, e0 * f1, e1 * f1, a0 * g1,
                    a1 * g1, c);
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
     * The ghost over every block still MISSING and nothing else (ConstructionGrid). Round 12 critic: the ghost-vs-built
     * contrast was the weak point - a faint grey-green over the white quartz. Now it is set apart by HUE:
     * <ul>
     *   <li>the fill: each missing block's faces turned to the camera with an open neighbour, a cool slate #6F86A8 at
     *   0.28 - every face of the volume the same, inset GHOST_INSET from the cell so it never shares a plane with a
     *   standing block's face (no z-fighting);</li>
     *   <li>edges only on the OUTER SILHOUETTE of each missing region, #B8C8E0, 1 px at 0.6: a face's edge where the
     *   volume ends beyond it AND the fold there turns away from the camera (the top's far edges, the sides' far and
     *   bottom edges). The creases between two faces turned to us (the top's near edges, the near corner) are where
     *   the old lattice made its Y-junctions - none now; nothing inside a face.</li>
     * </ul>
     * A block counts as missing only when no block of its kind stands there (recount), so nothing is ghosted over stone.
     * It walks ConstructionGrid's VOLUME - the missing blocks and the room they will enclose - so a hollow building
     * ghosts as one hull: no inner faces or edges seen through the outer ones (round 13 critic: the lattice).
     */
    static void drawGhost(Site s, boolean far) {
        ConstructionGrid g = s.grid;
        float lx = BarFxClient.lookX, lz = BarFxClient.lookZ;
        int sx = lx > 0 ? -1 : 1, sz = lz > 0 ? -1 : 1;   // the x / z side turned to us
        float e = GHOST_INSET;
        int n = g.volumeCount();
        for (int i = 0; i < n; i++) {
            int x = g.volumeX(i), y = g.volumeY(i), z = g.volumeZ(i);
            float x0 = x + e, x1 = x + 1 - e, z0 = z + e, z1 = z + 1 - e, y0 = y + e, y1 = y + 1 - e;
            if (open(g, x, y + 1, z))
                fill(x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1);
            if (open(g, x + sx, y, z)) {
                float fx = sx > 0 ? x1 : x0;
                fill(fx, y0, z0, fx, y0, z1, fx, y1, z1, fx, y1, z0);
            }
            if (open(g, x, y, z + sz)) {
                float fz = sz > 0 ? z1 : z0;
                fill(x0, y0, fz, x1, y0, fz, x1, y1, fz, x0, y1, fz);
            }
        }
        if (far)
            return;
        int drawn = 0;
        for (int i = 0; i < n && drawn < EDGE_CAP; i++)
            drawn += ghostEdges(g, g.volumeX(i), g.volumeY(i), g.volumeZ(i), sx, sz, EDGE_CAP - drawn);
    }

    /** The most ghost edges one building draws; how far the ghost sits inside its cells. */
    private static final int EDGE_CAP = 400;
    private static final float GHOST_INSET = 0.01f;

    /**
     * The silhouette edges of missing block (x, y, z)'s visible faces, at most {@code room}. A face's edge counts only
     * where the block beyond it in the face's plane is not missing (the volume ends there) and the direction across
     * it is one turned AWAY from the camera (down, -sx, -sz) - so the fold behind that edge is out of view and the
     * edge is the outline. Each such edge belongs to exactly one visible face. Returns how many it drew.
     */
    private static int ghostEdges(ConstructionGrid g, int x, int y, int z, int sx, int sz, int room) {
        int drawn = 0;
        float e = GHOST_INSET;
        boolean top = open(g, x, y + 1, z), xf = open(g, x + sx, y, z), zf = open(g, x, y, z + sz);
        float x0 = x + e, x1 = x + 1 - e, z0 = z + e, z1 = z + 1 - e, y0 = y + e, y1 = y + 1 - e;
        float fx = sx > 0 ? x1 : x0, fz = sz > 0 ? z1 : z0, bx = sx > 0 ? x0 : x1, bz = sz > 0 ? z0 : z1;
        if (top) {   // the top's far edges: toward -sx and -sz
            if (drawn < room && !g.isVolume(x - sx, y, z)) drawn += edge(bx, y1, z0, bx, y1, z1);
            if (drawn < room && !g.isVolume(x, y, z - sz)) drawn += edge(x0, y1, bz, x1, y1, bz);
        }
        if (xf) {    // the x face: its bottom and its far (-sz) side
            if (drawn < room && !g.isVolume(x, y - 1, z)) drawn += edge(fx, y0, z0, fx, y0, z1);
            if (drawn < room && !g.isVolume(x, y, z - sz)) drawn += edge(fx, y0, bz, fx, y1, bz);
        }
        if (zf) {    // the z face: its bottom and its far (-sx) side
            if (drawn < room && !g.isVolume(x, y - 1, z)) drawn += edge(x0, y0, fz, x1, y0, fz);
            if (drawn < room && !g.isVolume(x - sx, y, z)) drawn += edge(bx, y0, fz, bx, y1, fz);
        }
        return drawn;
    }

    /** A cell the ghost's surface faces: neither ghost volume nor a block of the site (air outside, or the outside). */
    private static boolean open(ConstructionGrid g, int x, int y, int z) {
        return !g.isVolume(x, y, z) && !g.isStructure(x, y, z);
    }

    /** One ghost silhouette edge, #B8C8E0, 1 px at 0.6. Returns 1. */
    private static int edge(float x0, float y0, float z0, float x1, float y1, float z1) {
        line(x0, y0, z0, x1, y1, z1, 0.035f, GHOST_EDGE, 0.6f);
        return 1;
    }

    /** One ghost face's fill. */
    private static void fill(float ax, float ay, float az, float bx, float by, float bz, float cx, float cy, float cz,
                             float dx, float dy, float dz) {
        BarFxClient.v(ax, ay, az, GHOST_FILL, GHOST_A);
        BarFxClient.v(bx, by, bz, GHOST_FILL, GHOST_A);
        BarFxClient.v(cx, cy, cz, GHOST_FILL, GHOST_A);
        BarFxClient.v(dx, dy, dz, GHOST_FILL, GHOST_A);
    }

    // ------------------------------------------------------------------ the rim

    /**
     * ONE build front (round 12 critic: cream lines on every built edge coincided with the ghost's edges, doubled up
     * and z-fought). Only the rim edges of the build-front row - the rim height with the most edges, the course being
     * laid (round 13: the highest row was often one pillar top) - (ConstructionGrid: the top edge of a standing block
     * with a missing block on it, on an open side): a warm #FFD27A line, 2 px at 0.9, RIM_INSET into the block; under
     * it, on the vertical face, a cool #7FB2FF strip at 0.45, 0.25 block tall - the cool counterpoint, half-way between
     * round 11's strong band and none. Beads (one per 6 blocks) run along that row at 1.5 blocks a second.
     */
    static void drawRim(Site s, float clock, boolean far) {
        ConstructionGrid g = s.grid;
        int n = g.rimCount();
        if (n == 0)
            return;
        float top = s.frontRow;
        if (Float.isNaN(top))
            return;
        // 2 px on screen at any zoom (round 13 critic: at the RTS zoom it collapsed): the ortho view is zoom blocks tall
        float wc = Math.max(far ? 0.12f : 0.07f, 2 * pixel());
        int rowN = 0;
        for (int i = 0; i < n; i++) {
            if (g.rimY(i) != top)
                continue;
            rowN++;
            int side = g.faceSide(i);
            float nx = sideNx(side), nz = sideNz(side), y = g.rimY(i);
            float ax = g.rimX0(i), az = g.rimZ0(i), bx = g.rimX1(i), bz = g.rimZ1(i);
            // the cool strip on the face, just outside it, the 0.25 block under the line
            float ox = nx * 0.01f, oz = nz * 0.01f;
            BarFxClient.v(ax + ox, y - 0.25f, az + oz, FRONT_COOL, 0.45f);
            BarFxClient.v(bx + ox, y - 0.25f, bz + oz, FRONT_COOL, 0.45f);
            BarFxClient.v(bx + ox, y, bz + oz, FRONT_COOL, 0.45f);
            BarFxClient.v(ax + ox, y, az + oz, FRONT_COOL, 0.45f);
            float ix = -nx * RIM_INSET, iz = -nz * RIM_INSET;
            line(ax + ix, y + 0.02f, az + iz, bx + ix, y + 0.02f, bz + iz, wc, FRONT_WARM, 0.9f);
        }
        if (rowN == 0)
            return;
        int beads = Math.max(1, Math.round(rowN / 6f));
        float r = far ? 0.25f : 0.15f;
        for (int k = 0; k < beads; k++) {
            float at = clock * 1.5f + k * 6f;
            at -= (float) Math.floor(at / rowN) * rowN;
            int want = Math.min(rowN - 1, (int) at), seen = 0, i = 0;
            for (; i < n; i++)
                if (g.rimY(i) == top && seen++ == want)
                    break;
            if (i >= n)
                continue;
            float f = at - want;
            int side = g.faceSide(i);
            float ix = -sideNx(side) * RIM_INSET, iz = -sideNz(side) * RIM_INSET, y = g.rimY(i) + 0.05f;
            float x = g.rimX0(i) + (g.rimX1(i) - g.rimX0(i)) * f + ix, z = g.rimZ0(i) + (g.rimZ1(i) - g.rimZ0(i)) * f + iz;
            BarFxClient.softDisc(x, y, z, r, BEAD_GLOW, 0.8f, BEAD_GLOW, 0.35f, 12, 0, 0);
            BarFxClient.softDisc(x, y, z, r * 0.45f, 0xFFFFFF, 1f, 0xFFFFFF, 0.9f, 8, 0, 0);
        }
    }

    /** One screen pixel in blocks (the ortho view is zoom blocks tall); a fixed small size in first person. */
    private static float pixel() {
        if (!BarFxClient.ortho)
            return 0.02f;
        int h = net.minecraft.client.Minecraft.getInstance().getWindow().getHeight();
        return h > 0 ? BarFxClient.zoom / h : 0.035f;
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

    /** A soft cool pool on the standing surface inside the footprint (the accent colour at 0.5, radius 1.5). */
    static void drawPool(Site s, float clock) {
        landing(s, clock);
        if (Float.isNaN(s.pt[2]))
            return;
        flatDisc(s.pt[0], s.pt[2] + 0.03f, s.pt[1], 1.5f, COOL, 0.5f, 18);
    }

    /**
     * Light from the build front (round 12 critic: 2-3 beams, 0.35, 0.15 block wide, only from the build-front row):
     * three soft vertical beams rising 4 blocks from points on the highest built row's rim, each holding a spot for
     * ~4 s with a fade in and out, then moving on. No wash on built stone (round 11). Additive.
     */
    static void drawLight(Site s, float front, float clock) {
        ConstructionGrid g = s.grid;
        float top = s.frontRow;
        if (Float.isNaN(top))
            return;
        int rowN = 0;
        for (int i = 0, n = g.rimCount(); i < n; i++)
            if (g.rimY(i) == top)
                rowN++;
        for (int k = 0; k < 3; k++) {
            float t = clock / 4f + k / 3f;
            int cyc = (int) Math.floor(t);
            float f = t - cyc, a = 0.35f * (float) Math.sin(Math.PI * f);
            int want = (int) (hash(cyc * 37 + k * 101) * rowN) % rowN, seen = 0, i = 0, n = g.rimCount();
            for (; i < n; i++)
                if (g.rimY(i) == top && seen++ == want)
                    break;
            if (i >= n)
                continue;
            int side = g.faceSide(i);
            float x = (g.rimX0(i) + g.rimX1(i)) * 0.5f - sideNx(side) * 0.5f;
            float z = (g.rimZ0(i) + g.rimZ1(i)) * 0.5f - sideNz(side) * 0.5f;
            BarFxClient.streak(x, top + 4f, z, x, top + 0.05f, z, 0.075f, RAY, 0f, a);
        }
    }

    /** Slow gold motes rising inside the footprint: four larger ones (~3 px) at 0.6, 0.3 block a second, 6 s each. */
    static void drawMotes(Site s, float front, float clock) {
        float x0 = s.course[0], x1 = s.course[1], z0 = s.course[2], z1 = s.course[3];
        for (int j = 0; j < 4; j++) {
            float t = clock / 6f + j / 4f;
            int cyc = (int) Math.floor(t);
            float k = t - cyc;
            float x = x0 + (x1 - x0) * hash(cyc * 7 + j * 131) + 0.15f * (float) Math.sin(clock * 0.9f + j);
            float z = z0 + (z1 - z0) * hash(cyc * 13 + j * 71) + 0.15f * (float) Math.cos(clock * 0.7f + j * 2);
            float a = 0.6f * (float) Math.sin(Math.PI * k);
            BarFxClient.softDisc(x, front + 0.2f + 1.8f * k, z, 0.1f, GOLD, a, GOLD, 0.3f * a, 8, 0, 0);
        }
    }

    // ------------------------------------------------------------------ the tools

    /** Tools working at once (round 10 critic: 3-4 cycling): two hammers and a chisel, on the sides turned to us. */
    private static final int TOOLS = 3;
    /** Hammer / chisel parts (blocks): ~0.5 block in all (round 12 critic); the handle's length is the pivot's
     *  distance from the struck point. */
    private static final float HANDLE_W = 0.07f, HEAD_L = 0.28f, HEAD_W = 0.13f, CHISEL_GRIP_W = 0.09f;
    /** The anticipation before a blow (~2 frames, seconds). */
    private static final float ANTICIPATION = 0.035f;
    /** The pivot stands this far out along the face's normal and this far above the struck point: the haft near
     *  square to the wall, the head ON it at the blow; the swing is a 40-degree arc. */
    private static final float PIVOT_OUT = 0.36f, PIVOT_UP = 0.12f, ARC = 0.7f;
    /** The struck point: on its face, just clear of the plane (round 13 critic: snap to the face at 0 offset; this is
     *  only enough to keep the head's face from z-fighting the wall's). */
    private static final float FACE_GAP = 0.004f;
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
        s.tool[0] = x + 0.5f + nx * (0.5f + FACE_GAP); s.tool[1] = y + 0.5f; s.tool[2] = z + 0.5f + nz * (0.5f + FACE_GAP);
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
     * is within 0.4 of its block. Shaded (a lit and a dark side, a highlight), a gold head on an iron haft, a 1 px dark
     * outline, ~0.8 block (round 11 critic); its head's striking face on the struck point; ~2 frames of anticipation.
     * {@code full}: the shaded tool; else its base only, faint (a trail copy). {@code fly}: blocks risen (done).
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
        float windEnd = ConstructionShape.BEAT_MOVE + ConstructionShape.BEAT_WIND;
        float wind = k < ConstructionShape.BEAT_MOVE ? 0.5f * Math.min(1, 3 * k / ConstructionShape.BEAT_MOVE)
                : k < windEnd - ANTICIPATION ? 0.5f + 0.5f * b[2]
                : k < windEnd ? 1.15f                                       // the anticipation: ~2 frames further back
                : b[2];
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
        // a small 3D model in the world (round 13 critic: the flat sprites read as items floating off the wall): boxes
        // along d (pivot -> head), t (along the face) and u (d x t), lit from above, depth-tested against the wall
        float dx = hx - gx, dy = hy - gy, dz = hz - gz, len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1e-3f)
            return;
        dx /= len; dy /= len; dz /= len;
        float tx = -nz, tz = nx;                                            // along the face (horizontal)
        float ux = dy * tz, uy = dz * tx - dx * tz, uz = -dy * tx;          // d x t
        if (chisel) {
            // a gold grip and an iron blade whose edge is the struck point
            float grip = len * 0.5f, gh = CHISEL_GRIP_W * 0.5f;
            box(gx + dx * grip * 0.5f, gy + dy * grip * 0.5f, gz + dz * grip * 0.5f, dx, dy, dz, grip * 0.5f, tx, 0, tz, gh,
                    ux, uy, uz, gh, GOLD_DARK, alpha);
            float bl = len - grip, bc = grip + bl * 0.5f;
            box(gx + dx * bc, gy + dy * bc, gz + dz * bc, dx, dy, dz, bl * 0.5f, tx, 0, tz, 0.045f, ux, uy, uz, 0.02f,
                    IRON_DARK, alpha);
        } else {
            // an iron haft to the head; the gold head's striking face ON the struck point (its centre half its depth
            // back along d)
            float hd = HEAD_W * 0.5f, shaft = len - HEAD_W;
            box(gx + dx * shaft * 0.5f, gy + dy * shaft * 0.5f, gz + dz * shaft * 0.5f, dx, dy, dz, shaft * 0.5f, tx, 0, tz,
                    HANDLE_W * 0.5f, ux, uy, uz, HANDLE_W * 0.5f, IRON_DARK, alpha);
            box(hx - dx * hd, hy - dy * hd, hz - dz * hd, dx, dy, dz, hd, tx, 0, tz, HEAD_L * 0.5f, ux, uy, uz, hd,
                    GOLD_DARK, alpha);
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
     * A blow's consequence on the struck face (round 11 critic: the old 0.8 / 0.6 fades read as solid gold blocks):
     * the ONE struck face, #F2DC96 at 0.35 at the blow fading linearly to 0 over 0.5 s, no outline; only tools 0 and 1
     * leave one, so at most 2 are live per building (a blow every 0.9 s each); and a 6 px #FFF4D0 flash for 120 ms at
     * the contact. The decay runs off the beat's time since the blow, recomputed from the clock every frame.
     */
    static void drawStruck(Site s, int i, float t) {
        float since = blow(s, i, t, 0);
        if (since < 0 || since >= 0.5f)
            return;
        float[] tl = s.tool;
        if (i < 2) {
            float a = 0.35f * (1 - since / 0.5f);
            float nx = tl[3], nz = tl[4], tx = -nz * 0.5f, tz = nx * 0.5f;
            float fx = tl[0] + nx * 0.01f, fz = tl[2] + nz * 0.01f, y0 = tl[6], y1 = y0 + 1;
            BarFxClient.v(fx - tx, y0, fz - tz, STRUCK_FACE, a);
            BarFxClient.v(fx + tx, y0, fz + tz, STRUCK_FACE, a);
            BarFxClient.v(fx + tx, y1, fz + tz, STRUCK_FACE, a);
            BarFxClient.v(fx - tx, y1, fz - tz, STRUCK_FACE, a);
        }
        if (since < 0.12f) {
            float x = tl[0] - BarFxClient.lookX * NUDGE, y = tl[1] - BarFxClient.lookY * NUDGE;
            float z = tl[2] - BarFxClient.lookZ * NUDGE;
            BarFxClient.softDisc(x, y, z, 0.1f, BLOW_FLASH, 0.9f, BLOW_FLASH, 0.6f, 10, 0, 0);
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

    /** A tool's light: a small glint on its head and, after each blow, 4 small 2 px gold sparks off the face (0.3 s). */
    static void drawToolLight(Site s, int i, float t) {
        ConstructionShape.strikeBeat(t, s.beat);
        if (!toolFace(s, i, (int) s.beat[0]))
            return;
        float tw = 0.75f + 0.25f * (float) Math.sin(t * 5 + i * 2.1f);
        BarFxClient.softDisc(s.heads[i * 3], s.heads[i * 3 + 1], s.heads[i * 3 + 2], 0.1f, LIGHT, 0.25f * tw, GOLD, 0,
                8, 0, 0);
        float since = blow(s, i, t, 0);
        if (since < 0 || since >= 0.3f)
            return;
        int sc = (int) s.beat[0];
        float[] tl = s.tool;
        float x = tl[0] - BarFxClient.lookX * NUDGE, y = tl[1] - BarFxClient.lookY * NUDGE;
        float z = tl[2] - BarFxClient.lookZ * NUDGE;
        float f = 1 - since / 0.3f;
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

    /** The light the tools' boxes are shaded by (from above, a little toward the camera's left). */
    private static final float LIGHT_X = -0.33f, LIGHT_Y = 0.84f, LIGHT_Z = -0.43f;

    /**
     * A small shaded box (a tool's part): centre c, unit axes a / b / u with half-extents ha / hb / hu. Only the faces
     * turned to the camera are drawn, each its colour times 0.6 + 0.4 x its light. Depth-tested like the world.
     */
    private static void box(float cx, float cy, float cz, float ax, float ay, float az, float ha, float bx, float by,
                            float bz, float hb, float ux, float uy, float uz, float hu, int c, float alpha) {
        for (int f = 0; f < 6; f++) {
            float sg = (f & 1) == 0 ? 1 : -1;
            int axis = f >> 1;
            // n: this face's normal axis; p / q: the other two
            float nx = axis == 0 ? ax : axis == 1 ? bx : ux, ny = axis == 0 ? ay : axis == 1 ? by : uy;
            float nz = axis == 0 ? az : axis == 1 ? bz : uz, hn = axis == 0 ? ha : axis == 1 ? hb : hu;
            float px = axis == 0 ? bx : ax, py = axis == 0 ? by : ay, pz = axis == 0 ? bz : az, hp = axis == 0 ? hb : ha;
            float qx = axis == 2 ? bx : ux, qy = axis == 2 ? by : uy, qz = axis == 2 ? bz : uz, hq = axis == 2 ? hb : hu;
            nx *= sg; ny *= sg; nz *= sg;
            if (nx * BarFxClient.lookX + ny * BarFxClient.lookY + nz * BarFxClient.lookZ >= 0)
                continue;
            float lit = Math.max(0, nx * LIGHT_X + ny * LIGHT_Y + nz * LIGHT_Z);
            int col = shade(c, 0.6f + 0.4f * lit);
            float fx = cx + nx * hn, fy = cy + ny * hn, fz = cz + nz * hn;
            px *= hp; py *= hp; pz *= hp; qx *= hq; qy *= hq; qz *= hq;
            BarFxClient.v(fx - px - qx, fy - py - qy, fz - pz - qz, col, alpha);
            BarFxClient.v(fx + px - qx, fy + py - qy, fz + pz - qz, col, alpha);
            BarFxClient.v(fx + px + qx, fy + py + qy, fz + pz + qz, col, alpha);
            BarFxClient.v(fx - px + qx, fy - py + qy, fz - pz + qz, col, alpha);
        }
    }

    private static int shade(int c, float f) {
        int r = Math.min(255, Math.round(((c >> 16) & 255) * f)), g = Math.min(255, Math.round(((c >> 8) & 255) * f));
        int b = Math.min(255, Math.round((c & 255) * f));
        return (r << 16) | (g << 8) | b;
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
