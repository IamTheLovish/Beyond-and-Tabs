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
 *   <li>a luminous cloud RING round the layer being built - not a cloud overhead, the RTS camera looks down - that
 *   climbs with the build front and narrows as the building does (ConstructionShape: the ring hugs each layer);</li>
 *   <li>the part still to come is absent but for one bold golden outline of its steps (slice 2 - the outline-only
 *   paintover beat the "built but foggy" one a fresh critic scored 5/10), so how much is left reads at a glance;</li>
 *   <li>the build front's cut line glows where blocks are being set;</li>
 *   <li>shafts of light land on the front, a few at a time;</li>
 *   <li>golden hammers and chisels orbit on the ring and swing down onto the front (slice 2);</li>
 *   <li>done: the ring flares into a halo and fades.</li>
 * </ul>
 * Building-agnostic: everything comes from the placement's own blocks (the buildings are being reworked). Pure client
 * rendering through BarFxClient's passes; thinner when zoomed out; nothing allocated per frame once a site's shape is
 * cached (one ConstructionShape per placement, built from its blocks the first time it is seen rising).
 */
final class ConstructionFx {

    private ConstructionFx() { }

    private static final int CLOUD = 0xFFFBF2, BELLY = 0xA9B6CB, GOLD = 0xFFD27A, LIGHT = 0xFFF3D2;
    /** The outline and cut line: a saturated gold (round 2 critic: the warm-white core over gold read cream-white). */
    private static final int LINE_GOLD = 0xFFC21A;
    /** The outline of the part still to come: a warm gold, quieter than the cut line (round 3 critic, eyes 41023134378:
     *  a saturated full-opacity box read as a debug wireframe / CAD tool, not the White Angel). */
    private static final int OUTLINE_GOLD = 0xF0C35A;
    /** The tools' bodies, normal-blended: additive gold over the white walls vanished (round 3: "pale smudges"). */
    private static final int TOOL_BODY = 0xB8862B;
    /** Seconds the finished halo takes to flare and fade. */
    private static final float DONE_SECONDS = 2.2f;
    /** Past this RTS zoom (strategic) the ring keeps half its puffs and the light shafts stop. */
    private static final float FAR_ZOOM = 70;
    /** Seconds between recounts of what stands in each layer (a few hundred block-state reads per site). */
    private static final float RECOUNT = 0.5f;

    /** Per rising site: its shape, the placed count drawn (eased toward the server's), when it finished. */
    private static final class Site {
        final ConstructionShape shape;
        final float[] outline;
        final float[] cut = new float[4];
        final int[] standing;     // blocks standing per layer, recounted every RECOUNT seconds
        float countedAt = -1;
        float shown;
        float doneAt = -1;
        float lastFront, lastRadius;
        Site(ConstructionShape s) { shape = s; outline = new float[6 * s.maxOutlineBoxes()]; standing = new int[s.layers()]; }
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
            s = new Site(ConstructionShape.of(pts));
            s.shown = Float.NaN;   // set from the first recount (world heights can be negative)
            SITES.put(p, s);
        }
        return s;
    }

    /** Called from BarFxClient.render before its early return (a site can rise with no other effect alive). */
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

        // the clouds: soft translucent puffs (drawn under the light so the glow sits on top)
        BarFxClient.begin(buffers, BarFxRenderTypes.TRANSLUCENT, mat);
        for (BuildingPlacement p : scratch) {
            Site s = siteOf(p);
            if (p.isBuilt) {
                if (s.doneAt < 0)
                    s.doneAt = clock;
                continue;
            }
            // the front from what actually stands per layer (round 3: the count lagged the walls - stragglers like a
            // jammed pool held it down - and the outline wrapped built walls), eased so it climbs smoothly
            if (s.countedAt < 0 || clock - s.countedAt > RECOUNT) {
                s.countedAt = clock;
                java.util.Arrays.fill(s.standing, 0);
                net.minecraft.client.multiplayer.ClientLevel level = net.minecraft.client.Minecraft.getInstance().level;
                if (level != null)
                    for (BuildingBlock b : p.getBlocks())
                        if (!b.getBlockState().isAir() && b.isPlaced(level))
                            s.standing[s.shape.layerOf(b.getBlockPos().getY())]++;
            }
            float target = s.shape.frontFromLayers(s.standing);
            s.shown = Float.isNaN(s.shown) ? target : s.shown + (target - s.shown) * Math.min(1, dt * 4);
            float front = s.shown;
            float r = s.shape.ringRadiusAt(front);
            // a radius change at a layer step glides rather than snaps
            s.lastRadius = s.lastRadius <= 0 ? r : s.lastRadius + (r - s.lastRadius) * Math.min(1, dt * 2.5f);
            s.lastFront = front;
            drawRing(s.shape.centreX(), front, s.shape.centreZ(), s.lastRadius, clock, far, 1f);
            // the cut line and the outline get a normal-blended gold core: additive gold alone over the lawn read olive
            // (eyes 41003840710); a warm-WHITE core read cream (round 2, 41014293234), so the core is gold too
            drawCut(s, front, (far ? 0.3f : 0.16f) * 0.5f, LINE_GOLD, 0.8f);
            if (!far)
                drawTools(s.shape.centreX(), front, s.shape.centreZ(), s.lastRadius, clock, true);
        }
        buffers.endBatch(BarFxRenderTypes.TRANSLUCENT);

        // the light: the cut line, shafts landing on the front, the orbiting glints, the finished halo
        BarFxClient.begin(buffers, BarFxRenderTypes.ADDITIVE, mat);
        for (BuildingPlacement p : scratch) {
            Site s = SITES.get(p);
            if (s == null)
                continue;
            float cx = s.shape.centreX(), cz = s.shape.centreZ();
            if (p.isBuilt) {
                float k = (clock - s.doneAt) / DONE_SECONDS;
                if (k >= 0 && k < 1) {
                    float rr = s.lastRadius * (1 + 0.5f * k);
                    float a = (1 - k) * (1 - k);
                    BarFxClient.flatRing(cx, s.lastFront + 0.6f + 1.5f * k, cz, rr, 0.5f + 0.6f * k, GOLD, 0.8f * a, 40);
                    BarFxClient.flatRing(cx, s.lastFront + 0.6f + 1.5f * k, cz, rr * 0.92f, 0.25f, LIGHT, a, 40);
                }
                continue;
            }
            float front = s.lastFront, r = s.lastRadius;
            // the cut line: the layer being built's own footprint at the front (round 2 critic: a circle round the corners
            // read as a glow band round the base, not where blocks are being set)
            drawCut(s, front, far ? 0.3f : 0.16f, LINE_GOLD, 0.9f);
            if (!far)
                drawShafts(s, front, clock);
            // one bold silhouette: twice the selection box's weight so the two never read as one (round 2 critic)
            // the part still to come: warm gold, half alpha, ~1.5x the selection box, fading toward the top - the cut
            // line below it stays the one bright line (round 3 critic)
            drawOutline(s, front, far ? 0.2f : 0.1f, OUTLINE_GOLD, far ? 0.6f : 0.5f);
            if (far)
                drawGlints(cx, front, cz, r, clock, true);
            else
                drawTools(cx, front, cz, r, clock, false);
        }
        buffers.endBatch(BarFxRenderTypes.ADDITIVE);
        BarFxClient.vc = null;
    }

    /** The cloud ring: puffs on a circle at the front, each a lit top over a grey-blue belly, slowly orbiting. */
    static void drawRing(float cx, float front, float cz, float r, float clock, boolean far, float alpha) {
        // round 3 overshot (a few faint puffs, no ring): closer puffs so they join into one continuous thin band
        int n = Math.max(10, Math.min(56, Math.round((float) (2 * Math.PI * r / 0.5f))));
        if (far)
            n = Math.max(6, n / 2);
        // a tighter, brighter band than the first stills (eyes 41003840710: a fresh critic read 1.5-block puffs at 0.55
        // as grey-green haze over the lawn - smoke, not cloud): smaller puffs, a whiter top, a darker belly under it
        // round 2 (41014293234): still fat cotton balls covering the wall corners - ~45% of that size and a third the
        // opacity, so the ring is a thin wisp at the front, not a necklace
        float size = 0.45f * Math.min(1.0f, 0.5f + r * 0.06f);
        for (int k = 0; k < n; k++) {
            float ang = (float) (k * 2 * Math.PI / n + clock * 0.06f);
            float wob = 0.18f * (float) Math.sin(clock * 0.7f + k * 1.7f);
            float x = cx + (float) Math.cos(ang) * (r + wob), z = cz + (float) Math.sin(ang) * (r + wob);
            float y = front + 0.35f + 0.12f * (float) Math.sin(clock * 0.9f + k * 2.3f);
            float s = size * (0.85f + 0.3f * hash(k));
            BarFxClient.softDisc(x, y - s * 0.3f, z, s * 1.05f, BELLY, 0.2f * alpha, BELLY, 0, 12, k, 0.08f);
            BarFxClient.softDisc(x, y + s * 0.12f, z, s, CLOUD, 0.5f * alpha, CLOUD, 0.15f * alpha, 12, k * 1.3f, 0.12f);
        }
    }

    /** Two shafts at a time, each landing on the front for ~0.7 s at its own spot round the walls. */
    /**
     * Two shafts at a time, each landing for ~0.7 s on its own spot INSIDE the layer being built (round 3: a shaft
     * landing past the walls read as a flare cone on the lawn beside the site).
     */
    static void drawShafts(Site s, float front, float clock) {
        s.shape.layerBox(front, s.cut);
        float x0 = s.cut[0], x1 = s.cut[1], z0 = s.cut[2], z1 = s.cut[3];
        for (int i = 0; i < 2; i++) {
            float t = clock / 0.7f + i / 2f;
            int cycle = (int) Math.floor(t);
            float k = t - cycle;
            float x = x0 + 0.5f + (x1 - x0 - 1f) * hash(cycle * 7 + i * 131);
            float z = z0 + 0.5f + (z1 - z0 - 1f) * hash(cycle * 13 + i);
            float a = (float) Math.sin(Math.PI * k);   // fades in and out
            BarFxClient.streak(x, front + 4.5f, z, x, front + 0.05f, z, 0.28f, LIGHT, 0.10f * a, 0.85f * a);
        }
    }

    static void drawCut(Site s, float front, float w, int c, float a) {
        s.shape.layerBox(front, s.cut);
        float x0 = s.cut[0], x1 = s.cut[1], z0 = s.cut[2], z1 = s.cut[3], y = front + 0.04f;
        edge(x0, y, z0, x1, y, z0, w, c, a);
        edge(x1, y, z0, x1, y, z1, w, c, a);
        edge(x1, y, z1, x0, y, z1, w, c, a);
        edge(x0, y, z1, x0, y, z0, w, c, a);
    }

    static void drawOutline(Site s, float front, float w, int c, float a) {
        int n = s.shape.outline(front, s.outline);
        if (n == 0)
            return;
        float[] o = s.outline;
        float top = o[(n - 1) * 6 + 5];
        float span = Math.max(1f, top - front);
        for (int i = 0; i < n; i++) {
            int b = i * 6;
            float x0 = o[b], x1 = o[b + 1], z0 = o[b + 2], z1 = o[b + 3], y0 = o[b + 4], y1 = o[b + 5];
            float a0 = a * (1f - 0.75f * (y0 - front) / span), a1 = a * (1f - 0.75f * (y1 - front) / span);
            for (int h = (i == 0 ? 1 : 0); h < 2; h++) {   // the first box's bottom IS the cut line - not drawn twice
                float y = h == 0 ? y0 : y1, ay = h == 0 ? a0 : a1;
                edge(x0, y, z0, x1, y, z0, w, c, ay, ay);
                edge(x1, y, z0, x1, y, z1, w, c, ay, ay);
                edge(x1, y, z1, x0, y, z1, w, c, ay, ay);
                edge(x0, y, z1, x0, y, z0, w, c, ay, ay);
            }
            edge(x0, y0, z0, x0, y1, z0, w, c, a0, a1);   // the corners, fading upward
            edge(x1, y0, z0, x1, y1, z0, w, c, a0, a1);
            edge(x1, y0, z1, x1, y1, z1, w, c, a0, a1);
            edge(x0, y0, z1, x0, y1, z1, w, c, a0, a1);
        }
    }

    private static void edge(float x0, float y0, float z0, float x1, float y1, float z1, float w, int c, float a) {
        edge(x0, y0, z0, x1, y1, z1, w, c, a, a);
    }

    private static void edge(float x0, float y0, float z0, float x1, float y1, float z1, float w, int c, float a0,
                             float a1) {
        BarFxClient.streak(x0, y0, z0, x1, y1, z1, w, c, a0, a1);
    }

    /**
     * The tools orbiting on the ring: golden hammers and chisels in turn, each swinging down onto the front as it
     * strikes (slice 2 - they were glints). Built from soft streaks so they need no model: a hammer is a handle and a
     * crosswise head, a chisel a tapered blade. About a block long - ~30 px at the default RTS zoom, enough to read
     * as a tool, not a spark. A short trail behind each keeps the orbit legible.
     */
    /** Strategic zoom: the tools shrink to golden glints with a short trail. */
    static void drawGlints(float cx, float front, float cz, float r, float clock, boolean far) {
        for (int i = 0; i < 2; i++) {
            float ang = (float) (i * Math.PI + clock * 0.55f);
            float rs = r / ConstructionShape.RING_STANDOFF * 0.92f;
            float x = cx + (float) Math.cos(ang) * rs, z = cz + (float) Math.sin(ang) * rs;
            BarFxClient.softDisc(x, front + 1.0f, z, 0.32f, LIGHT, 1f, GOLD, 0.2f, 10, 0, 0);
        }
    }

    /**
     * The tools: golden hammers and chisels orbiting just outside the walls and swinging down onto the cut line. Drawn
     * twice - {@code body}: a solid dark-gold silhouette, normal-blended, so a tool reads against the white walls
     * (round 3, eyes 41023134378: additive gold alone left "two pale smudges"); then the light: a bright edge, the
     * trail, and a flash on the cut line at each blow.
     */
    static void drawTools(float cx, float front, float cz, float r, float clock, boolean body) {
        for (int i = 0; i < 4; i++) {
            float ang = (float) (i * Math.PI / 2 + clock * 0.55f);
            float strike = (float) Math.max(0, Math.sin(clock * 3.1f + i * 1.9f));   // a quick dip toward the front
            float y = front + 1.8f - 0.75f * strike * strike;   // the 2.1-block tool lands its head on the front
            float ca = (float) Math.cos(ang), sa = (float) Math.sin(ang);
            // just outside the cut line (the walls): r stands off past the corners (code review Oct 30)
            float rs = r / ConstructionShape.RING_STANDOFF * 0.98f;
            float x = cx + ca * rs, z = cz + sa * rs;
            // a blow: from raised behind (2.4 rad - head up) down to struck (0.3 - head on the front, the handle still
            // tilted so it reads from above)
            float swing = 2.4f - 2.1f * strike;
            float tx = -sa, tz = ca;                          // the orbit's tangent
            float hx = (float) Math.sin(swing) * tx, hy = (float) -Math.cos(swing), hz = (float) Math.sin(swing) * tz;
            float len = 2.1f;
            float gx = x - hx * len * 0.5f, gy = y - hy * len * 0.5f, gz = z - hz * len * 0.5f;   // the grip end
            float bx = x + hx * len * 0.5f, by = y + hy * len * 0.5f, bz = z + hz * len * 0.5f;   // the working end
            float qx = tx * (float) Math.cos(swing), qy = (float) Math.sin(swing), qz = tz * (float) Math.cos(swing);
            boolean hammer = (i & 1) == 0;
            if (body) {
                BarFxClient.streak(gx, gy, gz, bx, by, bz, hammer ? 0.2f : 0.3f, TOOL_BODY, 0.95f, hammer ? 0.95f : 0.7f);
                if (hammer)
                    BarFxClient.streak(bx - qx * 0.65f, by - qy * 0.65f, bz - qz * 0.65f,
                            bx + qx * 0.65f, by + qy * 0.65f, bz + qz * 0.65f, 0.5f, TOOL_BODY, 0.95f, 0.95f);
                continue;
            }
            float pa = ang - 0.35f;
            float px = cx + (float) Math.cos(pa) * rs, pz = cz + (float) Math.sin(pa) * rs;
            BarFxClient.streak(px, front + 1.8f, pz, x, y, z, 0.1f, GOLD, 0f, 0.3f);              // the trail
            BarFxClient.streak(gx, gy, gz, bx, by, bz, 0.07f, LIGHT, 0.7f, 0.7f);                  // a lit edge
            if (hammer)
                BarFxClient.streak(bx - qx * 0.6f, by - qy * 0.6f, bz - qz * 0.6f,
                        bx + qx * 0.6f, by + qy * 0.6f, bz + qz * 0.6f, 0.16f, LIGHT, 0.8f, 0.8f);
            if (strike > 0.85f)                                                                    // the blow lands
                BarFxClient.softDisc(bx, front + 0.08f, bz, 0.5f * strike, LIGHT, strike, GOLD, 0.5f * strike, 10, 0, 0);
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
