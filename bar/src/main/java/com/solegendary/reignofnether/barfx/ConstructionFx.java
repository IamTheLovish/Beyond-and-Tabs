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
 *   <li>the build front's cut line glows where blocks are being set;</li>
 *   <li>shafts of light land on the front, a few at a time;</li>
 *   <li>golden glints orbit on the ring (the tools; slice 2 gives them hammer and chisel models);</li>
 *   <li>done: the ring flares into a halo and fades.</li>
 * </ul>
 * Building-agnostic: everything comes from the placement's own blocks (the buildings are being reworked). Pure client
 * rendering through BarFxClient's passes; thinner when zoomed out; nothing allocated per frame once a site's shape is
 * cached (one ConstructionShape per placement, built from its blocks the first time it is seen rising).
 */
final class ConstructionFx {

    private ConstructionFx() { }

    private static final int CLOUD = 0xFFFBF2, BELLY = 0xA9B6CB, GOLD = 0xFFD27A, LIGHT = 0xFFF3D2;
    /** Seconds the finished halo takes to flare and fade. */
    private static final float DONE_SECONDS = 2.2f;
    /** Past this RTS zoom (strategic) the ring keeps half its puffs and the light shafts stop. */
    private static final float FAR_ZOOM = 70;

    /** Per rising site: its shape, the placed count drawn (eased toward the server's), when it finished. */
    private static final class Site {
        final ConstructionShape shape;
        float shown;
        float doneAt = -1;
        float lastFront, lastRadius;
        Site(ConstructionShape s) { shape = s; }
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
            s.shown = Math.max(0, p.serverBlocksPlaced);
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
            // ease toward the server's count so the ring climbs smoothly between packets
            s.shown += (p.serverBlocksPlaced - s.shown) * Math.min(1, dt * 4);
            int placed = Math.round(s.shown);
            float front = s.shape.frontY(placed);
            float r = s.shape.ringRadius(placed);
            // a radius change at a layer step glides rather than snaps
            s.lastRadius = s.lastRadius <= 0 ? r : s.lastRadius + (r - s.lastRadius) * Math.min(1, dt * 2.5f);
            s.lastFront = front;
            drawRing(s.shape.centreX(), front, s.shape.centreZ(), s.lastRadius, clock, far, 1f);
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
            // the cut line: a soft ring of light just inside the walls, where blocks are being set
            BarFxClient.flatRing(cx, front + 0.05f, cz, r / ConstructionShape.RING_STANDOFF * 0.9f, 0.45f, GOLD, 0.45f, 36);
            if (!far)
                drawShafts(cx, front, cz, r, clock);
            drawGlints(cx, front, cz, r, clock, far);
        }
        buffers.endBatch(BarFxRenderTypes.ADDITIVE);
        BarFxClient.vc = null;
    }

    /** The cloud ring: puffs on a circle at the front, each a lit top over a grey-blue belly, slowly orbiting. */
    static void drawRing(float cx, float front, float cz, float r, float clock, boolean far, float alpha) {
        int n = Math.max(6, Math.min(26, Math.round((float) (2 * Math.PI * r / 1.5f))));
        if (far)
            n = Math.max(6, n / 2);
        float size = Math.min(1.5f, 0.7f + r * 0.09f);
        for (int k = 0; k < n; k++) {
            float ang = (float) (k * 2 * Math.PI / n + clock * 0.06f);
            float wob = 0.18f * (float) Math.sin(clock * 0.7f + k * 1.7f);
            float x = cx + (float) Math.cos(ang) * (r + wob), z = cz + (float) Math.sin(ang) * (r + wob);
            float y = front + 0.55f + 0.2f * (float) Math.sin(clock * 0.9f + k * 2.3f);
            float s = size * (0.85f + 0.3f * hash(k));
            BarFxClient.softDisc(x, y - s * 0.28f, z, s * 1.05f, BELLY, 0.30f * alpha, BELLY, 0, 12, k, 0.08f);
            BarFxClient.softDisc(x, y + s * 0.12f, z, s, CLOUD, 0.55f * alpha, CLOUD, 0, 12, k * 1.3f, 0.12f);
        }
    }

    /** Three shafts at a time, each landing on the front for ~0.7 s at its own spot round the walls. */
    static void drawShafts(float cx, float front, float cz, float r, float clock) {
        for (int i = 0; i < 3; i++) {
            float t = clock / 0.7f + i / 3f;
            int cycle = (int) Math.floor(t);
            float k = t - cycle;
            float ang = (float) (hash(cycle * 7 + i * 131) * Math.PI * 2);
            float rr = r * (0.45f + 0.35f * hash(cycle * 13 + i));
            float x = cx + (float) Math.cos(ang) * rr, z = cz + (float) Math.sin(ang) * rr;
            float a = (float) Math.sin(Math.PI * k);   // fades in and out
            BarFxClient.streak(x, front + 4.5f, z, x, front + 0.05f, z, 0.28f, LIGHT, 0.10f * a, 0.85f * a);
            BarFxClient.softDisc(x, front + 0.1f, z, 0.45f, LIGHT, 0.9f * a, GOLD, 0, 10, 0, 0);
        }
    }

    /** The tools (for now golden glints with a short trail) orbiting on the ring, dipping as they strike. */
    static void drawGlints(float cx, float front, float cz, float r, float clock, boolean far) {
        int n = far ? 2 : 4;
        for (int i = 0; i < n; i++) {
            float ang = (float) (i * 2 * Math.PI / n + clock * 0.55f);
            float strike = (float) Math.max(0, Math.sin(clock * 3.1f + i * 1.9f));   // a quick dip toward the front
            float y = front + 1.3f - 0.8f * strike * strike;
            float x = cx + (float) Math.cos(ang) * r * 0.97f, z = cz + (float) Math.sin(ang) * r * 0.97f;
            float pa = ang - 0.35f;
            float px = cx + (float) Math.cos(pa) * r * 0.97f, pz = cz + (float) Math.sin(pa) * r * 0.97f;
            BarFxClient.streak(px, front + 1.3f, pz, x, y, z, 0.12f, GOLD, 0f, 0.5f);
            BarFxClient.softDisc(x, y, z, 0.32f, LIGHT, 1f, GOLD, 0.2f, 10, 0, 0);
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
