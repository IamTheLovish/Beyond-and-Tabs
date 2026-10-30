package com.solegendary.reignofnether.barfx;

import com.solegendary.reignofnether.building.BuildingBlock;
import com.solegendary.reignofnether.building.BuildingClientEvents;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.ConstructionCrew;
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
 *   <li>a cool slate-blue GHOST over every block still missing - and nothing else (ConstructionGrid) - one volume,
 *   LIGHTENED onto the scene and then tinted once a pixel (the laid stone outside it stays white, its upper courses over
 *   the laid wall read pale blue - round 16 critic), a dark 1.5 px outline on its silhouette: cool "not yet" against the warm white "done" (round 12 critic: separate them by hue; rounds 13-14:
 *   one volume, not a lattice; round 15: the laid wall read as ghost);</li>
 *   <li>ONE build front for the whole building: an amber band along the lowest course still being laid, breathing,
 *   continuous (faint through what hides it); the next block to be placed flashing amber (round 16 critic);</li>
 *   <li>three thin beams rising from the front row, slow gold motes;</li>
 *   <li>a small tool in the hands of each builder at work (up to three), each blow a 2-frame flash and sparks;</li>
 *   <li>done: a cloud halo swells and fades, a flash on the top, a light ring runs out over the ground.</li>
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
    /** The build front (round 14 critic): one #FFB347 band, opaque, normal-blended, on one course a wall face. */
    private static final int FRONT = 0xFFB347;
    /**
     * The ghost, LIGHTENED onto the scene (BarFxRenderTypes.LIGHTEN: each channel max(scene, ghost)). Round 15 critic:
     * blended over at 0.45, #4C6BA8 tinted the laid white wall top under the ghost of the courses above it pale blue
     * (157, 166, 184) - the building read as 100% ghost - and still measured B - R -2.8 over grass ((83, 105, 80); the
     * grade takes ~14 off a cool blue and adds ~12 to its red). Lightened, #284687 (40, 70, 135) leaves everything
     * brighter than it in every channel exactly as it is - laid quartz (~185-235) and its shaded sides (~122, 122, 128:
     * B +7 at most) - and lifts only the blue of the darker ground: grass (75, 92, 47) -> (75, 92, 135), B - R +60
     * before the grade, ~+35 after; the RTS grass (74-82, 95-100, 52) the same. One colour throughout; a dark 1 px
     * outline #3A5070 (never the selection box's pale 2 px).
     */
    private static final int GHOST_FILL = 0x284687, GHOST_EDGE = 0x3A5070;
    /**
     * The ghost's TINT, normal-blended over the lightened ghost, once a pixel (BarFxRenderTypes.DEPTH_ONLY prepass).
     * Round 16 critic: lightening contributes nothing where the scene is brighter than the ghost - over the laid white
     * walls the upper courses showed only as thin blue edges (3% of white-backed ghost pixels), so from the RTS camera
     * the wall top looked finished; over sand the ghost was invisible. #8FA9F0 at 0.30 lands the laid white (217, 213,
     * 205) at (195, 200, 216): dL -13.5, B - R +21. (The brief's #5A7FD0 at 0.18 works out to (194, 197, 206): dL -16
     * and B - R +11, under the +12 asked for - a lighter, bluer tint at a little more alpha meets both.) Over grass the
     * lightened (75, 92, 135) goes to (95, 115, 166), B - R +71; over sand (219, 211, 160) to (196, 199, 184).
     */
    private static final int GHOST_TINT = 0x8FA9F0;
    private static final float GHOST_TINT_A = 0.30f;
    /** The next block to be placed (round 16 critic): its cell's faces flash #FFB347 at 0.7, 2 Hz. */
    private static final float NEXT_A = 0.7f, NEXT_HZ = 2f;
    /** The beams: #FFE2A0, 0.45 at most, normal-blended (round 13 critic: peak L <= 200). */
    private static final int BEAM = 0xFFE2A0;
    private static final float BEAM_A = 0.45f;
    /** Sparks and the blow's white flash, at the worker's hands. */
    private static final int SPARK = 0xF2DFAE, BLOW_FLASH = 0xFFF4D0;
    /** The tools, normal-blended and SHADED (round 10 critic: the flat sprite with a dark outline read as a loot icon):
     *  each part a dark side and a lit side, a metallic highlight on the head. */
    private static final int GOLD_DARK = 0xC9A24A, IRON_DARK = 0x8A8A8A;
    /** Seconds the completion beat lasts (the halo's 0.6 s swell is the longest part). */
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
        final float[] pt = new float[5];               // ringPoint results {x, z, nx, nz}; a crew anchor {x, y, z, dx, dz}
        final float[] tool = new float[5];             // a tool's working end {x, y, z}, its normal {nx, nz} (toward the worker)
        final float[] beat = new float[4];             // a tool's strikeBeat
        final ConstructionCrew crew = new ConstructionCrew();   // the builders at work: they hold the tools (round 14)
        float countedAt = -1;
        float shown = Float.NaN;
        float doneAt = -1;
        float lastFront;
        final float[] quad = new float[12];            // a ghost face's corners (ConstructionGrid.ghostQuad)
        final int[] next = new int[4];                 // the next block to be placed {x, y, z, valid} (nextBlock)
        Site(ConstructionShape s, ConstructionGrid g) {
            shape = s; grid = g; standing = new int[s.layers()];
        }
    }
    private static final Map<BuildingPlacement, Site> SITES = new WeakHashMap<>();
    /** The bounds of the rising White Angel sites drawn last frame {x0, x1, y0, y1, z0, z1} (absorbsNano). */
    private static final int MAX_ACTIVE = 32;
    private static final float[] ACTIVE = new float[6 * MAX_ACTIVE];
    private static final Site[] ACTIVE_SITE = new Site[MAX_ACTIVE];
    private static int activeN;

    /**
     * Whether a worker's nano beam aimed at (x, y, z) lands on a rising White Angel site - BarFxClient.nano then draws
     * no beam. Round 13 critic: a fan of 7-9 hard warm lines from one point under the HUD, converging below the
     * ground, read as clutter over the site in every round since 11. That fan is not ConstructionFx's: it is the
     * builders' nanolathe beams (BuildingPlacement's build tick -> BarFx.nano, golden for the Sunforged), one per
     * worker, every ~8 ticks. On an Angel site the construction effect already shows the work (tools, beams from the
     * front), so the fan is dropped there; every other faction, and repair / reclaim off an Angel site, keep it.
     * <p>Round 14 critic: a hammer floated over the roof with nobody under it. The beam says who is building and where
     * their hands are ({@code hx, hy, hz}, BarFx.nano): it is reported to the site's crew, whose workers hold the tools.
     */
    static boolean absorbsNano(float hx, float hy, float hz, float x, float y, float z) {
        for (int i = 0; i < activeN; i++) {
            int a = i * 6;
            if (x >= ACTIVE[a] && x <= ACTIVE[a + 1] && y >= ACTIVE[a + 2] && y <= ACTIVE[a + 3] && z >= ACTIVE[a + 4]
                    && z <= ACTIVE[a + 5]) {
                ACTIVE_SITE[i].crew.report(hx, hy, hz, x, y, z, BarFxClient.clock);
                return true;
            }
        }
        return false;
    }
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
            // ... or anything solid at all (round 15 critic: "cull every ghost cell whose world block is already
            // placed" - a laid block in another variant, or ground the plan builds into, is never ghosted); air, water and
            // grass tufts (canBeReplaced) still count as missing
            net.minecraft.world.level.block.state.BlockState ws = level.getBlockState(bp);
            boolean placed = b.isPlaced(level) || ws.getBlock() == b.getBlockState().getBlock() || !ws.canBeReplaced();
            s.grid.mark(bp.getX(), bp.getY(), bp.getZ(), placed);
            if (placed)
                s.standing[s.shape.layerOf(bp.getY())]++;
        }
        s.grid.finish();
        // the build front: ConstructionGrid.frontRow (the rim height with the most edges, ties to the higher)
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
        pxBlocks = ConstructionShape.pxToBlocks(1, BarFxClient.ortho, BarFxClient.zoom,
                net.minecraft.client.Minecraft.getInstance().getWindow().getHeight());

        // Pass order (rounds 13, 16 critic). 1 normal-blended: the halo (done), the beams - before the ghost, which then
        // lies over whatever is inside its volume. 2 additive: motes, the workers' sparks, the completion flash. 3 the
        // next block's flash (world depth only). 4 the ghost: its nearest surface into the depth buffer, lightened, then
        // tinted once a pixel. 5 its outline, the tools in the workers' hands, the next block's flash over its face.
        // 6 the build front: its hidden stretches at 0.4 through everything, then the whole course polygon-offset.
        BarFxClient.begin(buffers, BarFxRenderTypes.TRANSLUCENT, mat);
        for (BuildingPlacement p : scratch) {
            Site s = siteOf(p);
            if (p.isBuilt) {
                if (s.doneAt < 0)
                    s.doneAt = clock;
                float k = clock - s.doneAt;
                // the halo swells to 1.5x and fades (0.6 s)
                if (k < 0.6f && !Float.isNaN(s.ring[0])) {
                    float e = k / 0.6f;
                    drawHalo(s, s.lastFront, clock, far, 1 - e, 1 + 0.5f * e);
                }
                continue;
            }
            if (s.countedAt < 0 || clock - s.countedAt > RECOUNT) {
                s.countedAt = clock;
                recount(s, p);
            }
            if (activeN < MAX_ACTIVE) {   // this site's bounds, for absorbsNano
                ACTIVE_SITE[activeN] = s;
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
            // (no pool of light under the ghost - round 14 critic: anything under the ghost reads as a second layer -
            // and no struck face or dust: the tools are in the workers' hands, not on the wall)
            if (!far)
                drawLight(s, clock);
        }
        buffers.endBatch(BarFxRenderTypes.TRANSLUCENT);

        // additive, small and capped: the motes, the workers' flashes and sparks, the completion flash and ring
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
                drawGlints(s, clock);
                continue;
            }
            drawMotes(s, front, clock);
            for (int i = 0; i < TOOLS; i++)
                drawToolLight(s, i, clock + i * 0.37f, clock);
        }
        buffers.endBatch(BarFxRenderTypes.ADDITIVE);

        // the next block to be placed, before the ghost: depth-tested against the world only, so it glows through
        // the ghost in front of it (round 16 critic: "being built now" read only as a faint dashed outline)
        boolean nextOn = (clock * NEXT_HZ) % 1f < 0.5f;
        if (!far && nextOn) {
            BarFxClient.begin(buffers, BarFxRenderTypes.TRANSLUCENT, mat);
            for (BuildingPlacement p : scratch) {
                Site s = SITES.get(p);
                if (s != null && !p.isBuilt && s.grid.nextBlock(s.crew, clock, BarFxClient.lookX, BarFxClient.lookY,
                        BarFxClient.lookZ, s.next))
                    nextFaces(s, 0);
            }
            buffers.endBatch(BarFxRenderTypes.TRANSLUCENT);
        }

        // the ghost: its nearest surface into the depth buffer, lightened (dark ground toward blue), then tinted once a
        // pixel (the laid white under it: round 16 critic)
        BarFxClient.begin(buffers, BarFxRenderTypes.DEPTH_ONLY, mat);
        for (BuildingPlacement p : scratch)
            if (!p.isBuilt)
                ghostFaces(SITES.get(p), GHOST_FILL, 1f);
        buffers.endBatch(BarFxRenderTypes.DEPTH_ONLY);
        BarFxClient.begin(buffers, BarFxRenderTypes.LIGHTEN, mat);
        for (BuildingPlacement p : scratch)
            if (!p.isBuilt)
                ghostFaces(SITES.get(p), GHOST_FILL, 1f);
        buffers.endBatch(BarFxRenderTypes.LIGHTEN);
        BarFxClient.begin(buffers, BarFxRenderTypes.TRANSLUCENT, mat);
        for (BuildingPlacement p : scratch)
            if (!p.isBuilt)
                ghostFaces(SITES.get(p), GHOST_TINT, GHOST_TINT_A);
        for (BuildingPlacement p : scratch) {
            Site s = SITES.get(p);
            if (s == null || p.isBuilt || far)   // done: the workers walk off, their tools with them
                continue;
            ghostOutline(s);
            for (int i = 0; i < TOOLS; i++)
                drawTool(s, i, clock + i * 0.37f, clock);
            if (nextOn && s.next[3] != 0)   // the next block again, over its own ghost face where it is in view
                nextFaces(s, NEXT_NUDGE);
        }
        buffers.endBatch(BarFxRenderTypes.TRANSLUCENT);

        // the build front last: first its stretches hidden behind laid blocks, at 0.4 with no depth test (round 16
        // critic: it read as a faint dashed outline, broken by the laid blocks and the selection box), then the whole
        // course at full strength, pulled forward
        BarFxClient.begin(buffers, BarFxRenderTypes.TRANSLUCENT_SEE_THROUGH, mat);
        for (BuildingPlacement p : scratch)
            if (!p.isBuilt)
                drawFront(SITES.get(p), clock, FRONT_HIDDEN_A);
        buffers.endBatch(BarFxRenderTypes.TRANSLUCENT_SEE_THROUGH);
        BarFxClient.begin(buffers, BarFxRenderTypes.TRANSLUCENT_OFFSET, mat);
        for (BuildingPlacement p : scratch)
            if (!p.isBuilt)
                drawFront(SITES.get(p), clock, 1f);
        buffers.endBatch(BarFxRenderTypes.TRANSLUCENT_OFFSET);
        BarFxClient.vc = null;
    }

    /** This frame's size of one screen pixel, in blocks (ConstructionShape.pxToBlocks). */
    private static float pxBlocks = 1 / 36f;

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
     * The ghost: ONE volume over the blocks still MISSING and nothing else - ConstructionGrid's volume, the missing
     * blocks and the room they will enclose (a hollow hall ghosts as one solid hull). Round 13 critic: a lattice
     * of translucent cubes - where a pixel saw two ghost faces (a ring's near wall over its far wall's inner face) the
     * alpha doubled (~0.45 over grass, not 0.28), the old inset on every side of every cell left a seam between each two,
     * and the slate read teal-grey (hue 139, sat 0.14). Now:
     * <ul>
     *   <li>only the hull's faces (ConstructionGrid.ghostFace: the cell beyond is not volume - against standing blocks
     *   too, so no hole behind a lantern or a slab, round 15), the ones turned to the camera, each spanning its whole
     *   cell in its plane (ghostQuad: edge to edge, no seam) and GHOST_INSET inside it (never on a standing block's
     *   plane: behind a laid full block the depth test hides it);</li>
     *   <li>LIGHTENED (round 15 critic: blended over, it tinted the laid wall under it): each pixel the brighter of the
     *   scene and #284687 per channel - once or many times, the same;</li>
     *   <li>then TINTED (round 16 critic: lightening adds nothing where the scene is brighter - the upper courses over
     *   the laid white wall showed only as thin edges): GHOST_TINT at GHOST_TINT_A, normal-blended, once a pixel behind
     *   a depth-only prepass of these same quads; a laid block in front of a ghost face hides it (depth), so the tint
     *   never lands on laid stone the ghost does not stand in front of;</li>
     *   <li>one colour throughout (round 14 critic: no brighter next course - it nested).</li>
     * </ul>
     */
    static void ghostFaces(Site s, int c, float a) {
        if (s == null)
            return;
        ConstructionGrid g = s.grid;
        float lx = BarFxClient.lookX, lz = BarFxClient.lookZ;
        int dx = lx > 0 ? 3 : 1, dz = lz > 0 ? 0 : 2, dy = BarFxClient.lookY <= 0 ? 4 : 5;   // the sides turned to us
        float[] q = s.quad;
        for (int i = 0, n = g.volumeCount(); i < n; i++) {
            int x = g.volumeX(i), y = g.volumeY(i), z = g.volumeZ(i);
            for (int k = 0; k < 3; k++) {
                int dir = k == 0 ? dy : k == 1 ? dx : dz;
                if (!g.ghostFace(x, y, z, dir))
                    continue;
                ConstructionGrid.ghostQuad(x, y, z, dir, GHOST_INSET, q);
                for (int j = 0; j < 12; j += 3)
                    BarFxClient.v(q[j], q[j + 1], q[j + 2], c, a);
            }
        }
    }

    /** The flashing next block is pulled this far toward the camera over its own ghost face (no shift on screen in the
     *  orthographic RTS view). */
    private static final float NEXT_NUDGE = 0.05f;

    /**
     * The next block to be placed (ConstructionGrid.nextBlock: the cell the builder at work nearest the camera aims at,
     * else the front course's missing cell nearest the camera): its three faces turned to the camera, #FFB347 at 0.7,
     * just outside its cell and pulled {@code nudge} toward the camera. Round 16 critic: "being built now" read only as a
     * faint dashed footprint outline.
     */
    static void nextFaces(Site s, float nudge) {
        int[] n = s.next;
        if (n[3] == 0)
            return;
        float lx = BarFxClient.lookX, lz = BarFxClient.lookZ;
        int dx = lx > 0 ? 3 : 1, dz = lz > 0 ? 0 : 2, dy = BarFxClient.lookY <= 0 ? 4 : 5;
        float ox = -lx * nudge, oy = -BarFxClient.lookY * nudge, oz = -lz * nudge;
        float[] q = s.quad;
        for (int k = 0; k < 3; k++) {
            ConstructionGrid.ghostQuad(n[0], n[1], n[2], k == 0 ? dy : k == 1 ? dx : dz, -0.01f, q);
            for (int j = 0; j < 12; j += 3)
                BarFxClient.v(q[j] + ox, q[j + 1] + oy, q[j + 2] + oz, FRONT, NEXT_A);
        }
    }

    /** The ghost's outline, on its silhouette only: at most EDGE_CAP edges a building. */
    static void ghostOutline(Site s) {
        ConstructionGrid g = s.grid;
        int sx = BarFxClient.lookX > 0 ? -1 : 1, sz = BarFxClient.lookZ > 0 ? -1 : 1;
        int drawn = 0;
        for (int i = 0, n = g.volumeCount(); i < n && drawn < EDGE_CAP; i++)
            drawn += ghostEdges(g, g.volumeX(i), g.volumeY(i), g.volumeZ(i), sx, sz, EDGE_CAP - drawn);
    }

    /** The most ghost edges one building draws; how far the ghost sits inside its cells. */
    private static final int EDGE_CAP = 400;
    private static final float GHOST_INSET = 0.02f;   // round 16 critic: clear of a laid block's face, no z-fight
    /** The outline is pulled this far toward the camera so the laid block it runs along never hides half of it; in the
     *  RTS view (orthographic) that moves nothing on screen. */
    private static final float EDGE_NUDGE = 0.1f;

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

    /**
     * One ghost silhouette edge: #3A5070, 1.5 px at 0.6 (round 16 critic: over the laid white wall the ghost's upper
     * courses need their edges to read). Round 13 critic: the old pale 2 px edge (L154-171) matched the game's
     * selection box (L166-173, 2 px) - an effect must never look like the selection: this one is dark. Returns 1.
     */
    private static int edge(float x0, float y0, float z0, float x1, float y1, float z1) {
        float ox = -BarFxClient.lookX * EDGE_NUDGE, oy = -BarFxClient.lookY * EDGE_NUDGE, oz = -BarFxClient.lookZ * EDGE_NUDGE;
        line(x0 + ox, y0 + oy, z0 + oz, x1 + ox, y1 + oy, z1 + oz, 1.5f * pxBlocks, GHOST_EDGE, 0.6f);
        return 1;
    }

    // ------------------------------------------------------------------ the build front

    /** The front's on-screen width (px) close and from the strategic zoom; how far it is pulled toward the camera; its
     *  breath (a cycle every FRONT_PULSE_S seconds, brightness +-FRONT_PULSE). */
    private static final float FRONT_PX = 3, FRONT_NUDGE = 0.3f, FRONT_PULSE_S = 0.8f, FRONT_PULSE = 0.35f;
    /** The front's stretches hidden behind laid blocks: this alpha, no depth test (round 16 critic: a faint dashed
     *  outline, broken by the laid blocks and the selection box - now one continuous course). */
    private static final float FRONT_HIDDEN_A = 0.4f;

    /**
     * ONE build front for the whole building (round 14 critic: a 3-5 px orange edge on scattered block tops at many
     * heights read as "orange-trimmed blocks"; round 15: one course a face still made 5 orange pieces over 270 px of
     * screen height). Now:
     * <ul>
     *   <li>the lowest course with a block still missing (ConstructionGrid.courseFront), on every wall face turned to the
     *   camera - one height for the building, so all its orange lies in one course's band on each face;</li>
     *   <li>along that course's whole OUTER edge at its bed (laid and missing cells alike - one continuous line, never
     *   a run of block tops), and nothing orange on any other block: no beads, no rim elsewhere;</li>
     *   <li>#FFB347 at 1.0, NORMAL-blended (round 13: additive vanished on a white wall), 3 px on screen at every zoom
     *   (sized in pixels, so never a hairline zoomed out);</li>
     *   <li>breathing: its brightness +-35% over 0.8 s - the site's "being built now" even with no builder in view
     *   (rounds 15, 16 critic);</li>
     *   <li>CONTINUOUS (round 16 critic: a faint dashed outline, broken by laid blocks and the selection box): drawn
     *   twice - at {@code a} = FRONT_HIDDEN_A with no depth test, so its stretches behind laid blocks still read, then at
     *   1.0 polygon-offset and pulled FRONT_NUDGE toward the camera, so nothing of the site covers the rest.</li>
     * </ul>
     */
    static void drawFront(Site s, float clock, float a) {
        if (s == null)
            return;
        ConstructionGrid g = s.grid;
        float w = FRONT_PX * pxBlocks;
        float lx = BarFxClient.lookX, lz = BarFxClient.lookZ;
        float ox = -lx * FRONT_NUDGE, oy = -BarFxClient.lookY * FRONT_NUDGE, oz = -lz * FRONT_NUDGE;
        int c = shade(FRONT, 1 + FRONT_PULSE * (float) Math.sin(clock * 2 * Math.PI / FRONT_PULSE_S));
        for (int i = 0, n = g.courseEdgeCount(); i < n; i++) {
            int side = g.courseSide(i);
            if (sideNx(side) * lx + sideNz(side) * lz > -0.05f)   // a face turned away: hidden behind the building
                continue;
            float ix = -sideNx(side) * RIM_INSET + ox, iz = -sideNz(side) * RIM_INSET + oz, y = g.courseY(i) + oy;
            line(g.courseX0(i) + ix, y, g.courseZ0(i) + iz, g.courseX1(i) + ix, y, g.courseZ1(i) + iz, w, c, a);
        }
    }

    // ------------------------------------------------------------------ light from above

    /** The beams: their on-screen width (px), their height (blocks), the share of it at the top they fade out over. */
    private static final float BEAM_PX = 2, BEAM_H = 4, BEAM_FADE = 0.3f;

    /**
     * Light from the build front. Round 13 critic: the beams out-shouted the front (~8 lines at L240: each soft
     * additive streak saturated over the white wall). Now exactly three hard beams, 2 px on screen, #FFE2A0 at 0.45
     * at most (peak L <= 200), NORMAL-blended, each rising BEAM_H from a point on the front row, full to 70% of its
     * height and fading to nothing over the top 30%; each holds a spot ~4 s with a fade in and out, then moves on.
     */
    static void drawLight(Site s, float clock) {
        ConstructionGrid g = s.grid;
        float top = g.frontRow();
        if (Float.isNaN(top))
            return;
        int rowN = 0, n = g.rimCount();
        for (int i = 0; i < n; i++)
            if (g.rimY(i) == top)
                rowN++;
        if (rowN == 0)
            return;
        float w = BEAM_PX * pxBlocks;
        for (int k = 0; k < 3; k++) {
            float t = clock / 4f + k / 3f;
            int cyc = (int) Math.floor(t);
            float f = t - cyc, a = BEAM_A * (float) Math.sin(Math.PI * f);
            int want = (int) (hash(cyc * 37 + k * 101) * rowN) % rowN, seen = 0, i = 0;
            for (; i < n; i++)
                if (g.rimY(i) == top && seen++ == want)
                    break;
            if (i >= n)
                continue;
            int side = g.faceSide(i);
            float x = (g.rimX0(i) + g.rimX1(i)) * 0.5f - sideNx(side) * 0.5f;
            float z = (g.rimZ0(i) + g.rimZ1(i)) * 0.5f - sideNz(side) * 0.5f;
            float y0 = top + 0.05f, ym = y0 + BEAM_H * (1 - BEAM_FADE), y1 = y0 + BEAM_H;
            line2(x, y0, z, x, ym, z, w, BEAM, a, a);
            line2(x, ym, z, x, y1, z, w, BEAM, a, 0);
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

    /** Tools at once: one per builder at work (ConstructionCrew.SLOTS) - two hammers and a chisel. */
    private static final int TOOLS = ConstructionCrew.SLOTS;
    /** Hammer / chisel parts (blocks): ~0.5 block in all (round 12 critic); the handle's length is the pivot's
     *  distance from the working end. */
    private static final float HANDLE_W = 0.07f, HEAD_L = 0.28f, HEAD_W = 0.13f, CHISEL_GRIP_W = 0.09f;
    /** The anticipation before a blow (~2 frames, seconds). */
    private static final float ANTICIPATION = 0.035f;
    /** The pivot (the grip) stands this far back toward the worker and this far above the working end: with the end
     *  ConstructionCrew.REACH (0.3) in front of the hands, the grip is in them; the swing is a 40-degree arc. */
    private static final float PIVOT_OUT = 0.36f, PIVOT_UP = 0.12f, ARC = 0.7f;
    /** The blow's flash lasts this long: ~2 frames (round 14 critic: a soft orb that stayed read as an object). */
    private static final float FLASH_S = 0.035f;

    /**
     * Tool {@code i}'s working end at {@code now} into {@code s.tool}: {x, y, z} REACH in front of its worker's hands at
     * hand height, {nx, nz} pointing back at the worker. False when no worker is building in that slot - then there is
     * no tool, no spark, nothing (round 14 critic: a hammer floated over the roof with nobody under it).
     */
    private static boolean toolAt(Site s, int i, float now) {
        if (!s.crew.anchor(i, now, s.pt))
            return false;
        float[] tl = s.tool;
        tl[0] = s.pt[0]; tl[1] = s.pt[1]; tl[2] = s.pt[2];
        tl[3] = -s.pt[3]; tl[4] = -s.pt[4];
        return true;
    }

    /**
     * One tool at beat time {@code t}, held by its worker (round 14 critic: an opaque hammer floated horizontally in
     * mid-air above the roof, no worker under it; the tools were laid out on the WALL, any standing face under a missing
     * block). Now each is parented to a builder at work (ConstructionCrew, from the builders' own nanolathe reports):
     * the grip in the hands, the head swinging a 40-degree arc onto a point REACH in front of them toward the block the
     * worker builds, ~2 frames of anticipation, a small recoil. Only while that worker is building; gone as it stops.
     * A small shaded 3D model (a gold head on an iron haft, a chisel's gold grip and iron blade), depth-tested.
     */
    static void drawTool(Site s, int i, float t, float now) {
        if (!toolAt(s, i, now))
            return;
        ConstructionShape.strikeBeat(t, s.beat);
        boolean chisel = i == 1;
        float[] b = s.beat, tl = s.tool;
        float nx = tl[3], nz = tl[4], alpha = 1f;
        // the pivot (the grip), in the world
        float gx = tl[0] + nx * PIVOT_OUT, gy = tl[1] + PIVOT_UP, gz = tl[2] + nz * PIVOT_OUT;
        // the head: on the working end at the blow; lifted, wound back and swung ARC round the pivot (in the plane of
        // the worker's facing and up), struck, recoiled
        float va = -PIVOT_OUT, vb = -PIVOT_UP;                              // pivot -> working end (normal, up)
        float k = t - b[0] * ConstructionShape.BEAT_CYCLE;
        float windEnd = ConstructionShape.BEAT_MOVE + ConstructionShape.BEAT_WIND;
        float wind = k < ConstructionShape.BEAT_MOVE ? 0.5f * Math.min(1, 3 * k / ConstructionShape.BEAT_MOVE)
                : k < windEnd - ANTICIPATION ? 0.5f + 0.5f * b[2]
                : k < windEnd ? 1.15f                                       // the anticipation: ~2 frames further back
                : b[2];
        wind *= chisel ? 0.6f : 1f;
        float since = b[3];
        if (since >= 0 && since < 0.15f && b[1] >= 1)
            wind = Math.max(wind, 0.15f * (float) Math.sin(Math.PI * since / 0.15f));   // the recoil
        float phi = ARC * wind, cs = (float) Math.cos(phi), sn = (float) Math.sin(phi);
        float ha = va * cs - vb * sn, hb = va * sn + vb * cs;              // rotated toward +normal (back, up)
        float hx = gx + nx * ha, hy = gy + hb, hz = gz + nz * ha;
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
    }

    /**
     * A tool's light: at each blow a ~2-frame flash and 4 small 2 px gold sparks (0.15 s) at the working end, in its
     * worker's hands only (round 14 critic: the soft orb on the head - a glint - sat on a ghost face like an object;
     * it is gone).
     */
    static void drawToolLight(Site s, int i, float t, float now) {
        if (!toolAt(s, i, now))
            return;
        ConstructionShape.strikeBeat(t, s.beat);
        float since = s.beat[3];
        if (since < 0 || since >= 0.15f)
            return;
        int sc = (int) s.beat[0];
        float[] tl = s.tool;
        float x = tl[0], y = tl[1], z = tl[2];
        if (since < FLASH_S)
            BarFxClient.softDisc(x, y, z, 0.06f, BLOW_FLASH, 0.9f, BLOW_FLASH, 0.4f, 8, 0, 0);
        float f = 1 - since / 0.15f;
        for (int j = 0; j < 4; j++) {   // round 10 critic: 3-5 small sparks
            float ang = (float) (hash(sc * 131 + i * 17 + j * 7) * 2 * Math.PI);
            float sp = 1.2f + 1.3f * hash(sc * 57 + j * 29 + i);
            float vx = (float) Math.cos(ang) * sp - tl[3] * 1.5f, vz = (float) Math.sin(ang) * sp - tl[4] * 1.5f;
            float vy = 2f + 1.5f * hash(sc * 23 + j * 3 + i * 5);
            float sx = x + vx * since, sy = y + vy * since - 6 * since * since, sz = z + vz * since;
            float vyNow = vy - 12 * since, l = 0.06f;
            line(sx - vx * l, sy - vyNow * l, sz - vz * l, sx, sy, sz, 0.035f, SPARK, f);   // 2 px
        }
    }

    /** Strategic zoom: each tool shrinks to a small golden glint at its worker's hands (never a free-floating one). */
    static void drawGlints(Site s, float clock) {
        for (int i = 0; i < TOOLS; i++)
            if (toolAt(s, i, clock))
                BarFxClient.softDisc(s.tool[0], s.tool[1], s.tool[2], 0.2f, LIGHT, 0.8f, GOLD, 0.2f, 10, 0, 0);
    }

    // ------------------------------------------------------------------ the completion beat

    /**
     * Done (the additive half; the halo's swell is drawn in the normal-blended pass): a flash
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

    /** {@link #line} with an alpha at each end. */
    private static void line2(float x0, float y0, float z0, float x1, float y1, float z1, float w, int c, float a0,
                              float a1) {
        float dx = x1 - x0, dy = y1 - y0, dz = z1 - z0;
        float sx = dy * BarFxClient.lookZ - dz * BarFxClient.lookY, sy = dz * BarFxClient.lookX - dx * BarFxClient.lookZ;
        float sz = dx * BarFxClient.lookY - dy * BarFxClient.lookX;
        float l = (float) Math.sqrt(sx * sx + sy * sy + sz * sz);
        if (l < 1e-5f) { sx = BarFxClient.leftX; sy = BarFxClient.leftY; sz = BarFxClient.leftZ; l = 1; }
        float h = w * 0.5f / l;
        sx *= h; sy *= h; sz *= h;
        BarFxClient.v(x0 - sx, y0 - sy, z0 - sz, c, a0);
        BarFxClient.v(x1 - sx, y1 - sy, z1 - sz, c, a1);
        BarFxClient.v(x1 + sx, y1 + sy, z1 + sz, c, a1);
        BarFxClient.v(x0 + sx, y0 + sy, z0 + sz, c, a0);
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
