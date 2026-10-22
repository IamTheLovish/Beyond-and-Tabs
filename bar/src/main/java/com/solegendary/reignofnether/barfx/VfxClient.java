package com.solegendary.reignofnether.barfx;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.solegendary.reignofnether.barfx.vfx.VfxAtlas;
import com.solegendary.reignofnether.barfx.vfx.VfxDef;
import com.solegendary.reignofnether.barfx.vfx.VfxParser;
import com.solegendary.reignofnether.barfx.vfx.VfxTimeline;
import com.solegendary.reignofnether.fogofwar.FogOfWarClientEvents;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.registries.ForgeRegistries;
import org.joml.Matrix4f;

import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * The client side of the data-driven effects (design/vfx.md): loads the definitions, runs the live effects and their
 * particles, and draws everything in ONE pass per frame with ONE texture (the vfx atlas) - one buffer for light
 * (additive) and one for smoke (translucent). It extends BarFx rather than duplicating it: the effects arrive as a
 * BarFx event (same queue, send range and fog filter on the server), and the pass reuses BarFxClient's camera,
 * clock, ground probe and "near the view" test.
 * <p>
 * Performance rules (big battles, ~500 entities, Complementary through Oculus):
 * <ul>
 *   <li>never drawn into a shader pack's shadow map (BarFxClientEvents returns before this pass there);</li>
 *   <li>caps: {@link #MAX_INSTANCES} live effects, {@link #MAX_PARTICLES} particles (struct-of-arrays, no allocation
 *       per particle), {@link #MAX_LIGHTS} cast lights; spawns are refused, never queued, when a cap is hit;</li>
 *   <li>the client setting vfx_quality scales every emitter's rate and burst (0 = no particles, no cast light);</li>
 *   <li>culling: an effect or particle away from the view is stepped but not drawn; an effect whose spot is under
 *       the fog of war is not drawn and spawns nothing (checked every 0.25 s);</li>
 *   <li>ground sigils sample the terrain once into a small height grid (and again only when a followed caster moves a
 *       block), never per vertex per frame;</li>
 *   <li>timed as its own [FrameParts] section "vfx".</li>
 * </ul>
 * Light: an effect's light is drawn as a soft pool on the terrain (additive, conforming to the blocks, so it reads as
 * light falling on the ground under every shader pack) and lifts the block light of units within its radius
 * ({@link #blockLightAt}, used by mixin/VfxEntityLightMixin through the packed light every entity renderer already
 * receives - shader packs light units from that same value). No light blocks are placed: a client-side light block
 * would relight and re-mesh chunk sections every frame it moved.
 */
@OnlyIn(Dist.CLIENT)
public final class VfxClient {
    private VfxClient() { }

    private static final Minecraft MC = Minecraft.getInstance();

    public static final int MAX_INSTANCES = 48, MAX_PARTICLES = 3000, MAX_LIGHTS = 16, MAX_PROJECTILES = 256;

    // ------------------------------------------------------------------ definitions

    private static final Int2ObjectOpenHashMap<VfxDef> BY_HASH = new Int2ObjectOpenHashMap<>();
    private static final Map<String, VfxDef> BY_ID = new HashMap<>();
    private static final Map<String, VfxDef> BY_ENTITY = new HashMap<>();
    private static boolean warnedUnknown = false;

    /** Reads every assets/<ns>/vfx/*.json (F3+T reloads them - iterate on an effect without restarting). */
    public static void reload(ResourceManager rm) {
        Map<String, VfxDef> byId = new HashMap<>();
        List<String> errors = new ArrayList<>();
        try {
            for (Map.Entry<ResourceLocation, Resource> en : rm.listResources("vfx", rl -> rl.getPath().endsWith(".json")).entrySet()) {
                String path = en.getKey().getPath();
                String id = path.substring("vfx/".length(), path.length() - ".json".length());
                StringBuilder sb = new StringBuilder();
                try (Reader r = en.getValue().openAsReader()) {
                    char[] buf = new char[4096];
                    int n;
                    while ((n = r.read(buf)) > 0) sb.append(buf, 0, n);
                } catch (Exception e) {
                    errors.add(id + ": unreadable (" + e.getMessage() + ")");
                    continue;
                }
                VfxDef def = VfxParser.parse(id, sb.toString(), errors);
                if (def != null) byId.put(id, def);
            }
        } catch (Exception e) {
            errors.add("listing assets/*/vfx failed: " + e);
        }
        synchronized (BY_HASH) {
            BY_HASH.clear();
            BY_ID.clear();
            BY_ENTITY.clear();
            for (VfxDef d : byId.values()) {
                VfxDef clash = BY_HASH.put(com.solegendary.reignofnether.barfx.vfx.VfxIds.hash(d.id), d);
                if (clash != null) errors.add(d.id + ": id hash collides with " + clash.id + " - rename one");
                BY_ID.put(d.id, d);
                if (d.isProjectile())
                    for (String et : d.projectile.entities) BY_ENTITY.put(et, d);
            }
        }
        warnedUnknown = false;
        for (String e : errors)
            com.solegendary.reignofnether.ReignOfNether.LOGGER.warn("[Vfx] {}", e);
        com.solegendary.reignofnether.ReignOfNether.LOGGER.info("[Vfx] loaded {} effect definitions ({} projectile visuals, {} problems)",
            byId.size(), BY_ENTITY.size(), errors.size());
    }

    public static VfxDef byId(String id) {
        synchronized (BY_HASH) { return BY_ID.get(id); }
    }

    /** The projectile visual bound to this entity's type, or null (the entity keeps its own look). */
    public static VfxDef projectileVisual(Entity e) {
        if (e == null) return null;
        ResourceLocation key = ForgeRegistries.ENTITY_TYPES.getKey(e.getType());
        if (key == null) return null;
        synchronized (BY_HASH) { return BY_ENTITY.get(key.toString()); }
    }

    // ------------------------------------------------------------------ quality

    /** 0 Minimal, 1 Low, 2 Medium, 3 High (client config vfx_quality; -Dreignofnether.vfxQuality overrides). */
    public static int quality() {
        try {
            String p = System.getProperty("reignofnether.vfxQuality");
            if (p != null) return Math.max(0, Math.min(3, Integer.parseInt(p)));
            return com.solegendary.reignofnether.config.ReignOfNetherClientConfigs.VFX_QUALITY.get();
        } catch (Exception e) {
            return 3;
        }
    }

    static final float[] QUALITY_SCALE = { 0f, 0.35f, 0.67f, 1f };

    // ------------------------------------------------------------------ live effects

    static final class Inst {
        VfxDef def;
        float x, y, z;            // anchor (the caster's feet, or the spot)
        float scale;
        float born;
        int follow = -1;
        boolean visible = true;
        float nextFogCheck = 0;
        float[] acc;              // per layer: emitter rate carry
        boolean[] burst;          // per layer: burst done
        // terrain height grid for ground layers / light pool: (2h+1)^2 cells of 1 block centred on (gx, gz)
        int gh = -1, gx, gz;
        float[] heights;
        float ground;             // ground height under the anchor
    }

    static final List<Inst> live = new ArrayList<>();
    private static final Random R = new Random();

    /** A BarFx VFX event from the server. */
    static void ingest(BarFx.Event e) {
        VfxDef def;
        synchronized (BY_HASH) { def = BY_HASH.get(((int) e.a) & 0xFFFFFF); }
        if (def == null) {
            if (!warnedUnknown) {
                warnedUnknown = true;
                com.solegendary.reignofnether.ReignOfNether.LOGGER.warn("[Vfx] unknown effect hash {} (a missing assets/*/vfx/*.json?)", (int) e.a);
            }
            return;
        }
        play(def, e.x, e.y, e.z, e.c, e.kind == BarFx.VFX_FOLLOW ? (int) e.b : -1);
    }

    /** Starts an effect on this client only (the debug command and the eyes scene go through the server instead). */
    public static void play(VfxDef def, float x, float y, float z, float scale, int follow) {
        if (def == null || def.isProjectile() || MC.level == null) return;
        if (!BarFxClient.near(x, y, z, def.cullRadius * scale + 16)) return;
        if (!brightAt(x, y, z)) return;
        if (live.size() >= MAX_INSTANCES) live.remove(0);   // the oldest makes room: a new cast matters more
        Inst in = new Inst();
        in.def = def;
        in.x = x; in.y = y; in.z = z;
        in.scale = scale <= 0 ? 1 : scale;
        in.born = vclock;
        in.follow = follow;
        in.acc = new float[def.layers.size()];
        in.burst = new boolean[def.layers.size()];
        sampleGround(in);
        live.add(in);
    }

    static boolean brightAt(float x, float y, float z) {
        try {
            return FogOfWarClientEvents.isInBrightChunk(BlockPos.containing(x, y, z));
        } catch (Exception ex) {
            return true;
        }
    }

    /** Terrain heights under the effect, once (a followed caster re-samples when he has moved a block). */
    static void sampleGround(Inst in) {
        float reach = 2;
        for (VfxDef.Layer l : in.def.layers)
            if (l instanceof VfxDef.Ground g) reach = Math.max(reach, g.radius * 1.42f);
        if (in.def.light != null) reach = Math.max(reach, in.def.light.radius * 0.75f);
        int h = Math.min(20, (int) Math.ceil(reach * in.scale) + 1);
        int gx = (int) Math.floor(in.x), gz = (int) Math.floor(in.z);
        if (in.heights != null && in.gh == h && in.gx == gx && in.gz == gz) return;
        int n = 2 * h + 1;
        float[] hs = (in.heights != null && in.heights.length == n * n) ? in.heights : new float[n * n];
        float probe = in.y + 3;
        for (int j = 0; j < n; j++)
            for (int i = 0; i < n; i++)
                hs[j * n + i] = BarFxClient.groundY(gx - h + i + .5f, probe, gz - h + j + .5f);
        in.heights = hs;
        in.gh = h; in.gx = gx; in.gz = gz;
        in.ground = BarFxClient.groundY(in.x, in.y + 1, in.z);
    }

    /** Bilinear terrain height at (x, z) from the instance's grid (the anchor's ground outside it). */
    static float heightAt(Inst in, float x, float z) {
        int n = 2 * in.gh + 1;
        float fx = x - (in.gx - in.gh) - .5f, fz = z - (in.gz - in.gh) - .5f;
        int ix = (int) Math.floor(fx), iz = (int) Math.floor(fz);
        if (ix < 0 || iz < 0 || ix >= n - 1 || iz >= n - 1) return in.ground;
        float tx = fx - ix, tz = fz - iz;
        float a = in.heights[iz * n + ix], b = in.heights[iz * n + ix + 1];
        float c = in.heights[(iz + 1) * n + ix], d = in.heights[(iz + 1) * n + ix + 1];
        // a block step (a ledge) would stretch the sigil down a wall: clamp to the anchor's level +- 1.5
        float h = (a * (1 - tx) + b * tx) * (1 - tz) + (c * (1 - tx) + d * tx) * tz;
        // at a ledge the blend runs under the higher block (eyes still: a wedge of the sigil missing): never below
        // the top of the column the point stands on
        h = Math.max(h, in.heights[Math.min(n - 1, Math.round(fz)) * n + Math.min(n - 1, Math.round(fx))]);
        return Math.max(in.ground - 1.5f, Math.min(in.ground + 1.5f, h));
    }

    // ------------------------------------------------------------------ particles (struct of arrays)

    static final int P_EMBER = 0, P_SPARK = 1, P_SMOKE = 2, P_GLINT = 3, P_HEAT = 4;
    static int pCount = 0;
    static final float[] px = new float[MAX_PARTICLES], py = new float[MAX_PARTICLES], pz = new float[MAX_PARTICLES];
    static final float[] pvx = new float[MAX_PARTICLES], pvy = new float[MAX_PARTICLES], pvz = new float[MAX_PARTICLES];
    static final float[] pAge = new float[MAX_PARTICLES], pLife = new float[MAX_PARTICLES], pSize = new float[MAX_PARTICLES];
    static final float[] pSizeEnd = new float[MAX_PARTICLES], pGrav = new float[MAX_PARTICLES], pDrag = new float[MAX_PARTICLES];
    static final float[] pWind = new float[MAX_PARTICLES], pAlpha = new float[MAX_PARTICLES], pFloor = new float[MAX_PARTICLES];
    static final int[] pColor = new int[MAX_PARTICLES], pColorEnd = new int[MAX_PARTICLES];
    static final byte[] pKind = new byte[MAX_PARTICLES];
    static final com.solegendary.reignofnether.barfx.vfx.VfxEasing[] pSizeEase = new com.solegendary.reignofnether.barfx.vfx.VfxEasing[MAX_PARTICLES];
    static final Inst[] pOwner = new Inst[MAX_PARTICLES];

    public static int liveParticles() { return pCount; }

    public static int liveEffects() { return live.size(); }

    /** Spawns n particles of an emitter round (cx, cy, cz). Quality and caps already applied by the caller. */
    static void spawn(VfxDef.Emitter e, Inst owner, int n, float cx, float cy, float cz, float scale, float floor, float alphaMul) {
        float r = e.radius * scale, hgt = e.height * scale;
        for (int k = 0; k < n && pCount < MAX_PARTICLES; k++) {
            float ox = 0, oy = 0, oz = 0, dirx = 0, dirz = 0;
            float a = R.nextFloat() * 6.2831855f;
            switch (e.shape) {
                case POINT -> { }
                case DISC -> { float d = r * (float) Math.sqrt(R.nextFloat()); ox = (float) Math.cos(a) * d; oz = (float) Math.sin(a) * d; }
                case RING -> { ox = (float) Math.cos(a) * r; oz = (float) Math.sin(a) * r; }
                case COLUMN -> { float d = r * (float) Math.sqrt(R.nextFloat()); ox = (float) Math.cos(a) * d; oz = (float) Math.sin(a) * d; oy = R.nextFloat() * hgt; }
                case SPHERE -> {
                    float u = R.nextFloat() * 2 - 1, d = r * (float) Math.cbrt(R.nextFloat()), s = (float) Math.sqrt(1 - u * u);
                    ox = (float) Math.cos(a) * s * d; oy = u * d; oz = (float) Math.sin(a) * s * d;
                }
            }
            float len = (float) Math.sqrt(ox * ox + oz * oz);
            if (len > 1e-4f) { dirx = ox / len; dirz = oz / len; }
            else { dirx = (float) Math.cos(a); dirz = (float) Math.sin(a); }
            int i = pCount++;
            px[i] = cx + ox; py[i] = cy + oy; pz[i] = cz + oz;
            float sp = e.spread;
            pvx[i] = e.vx + (R.nextFloat() * 2 - 1) * sp + dirx * e.outward - dirz * e.swirl;
            pvy[i] = e.vy + (R.nextFloat() * 2 - 1) * sp * 0.5f;
            pvz[i] = e.vz + (R.nextFloat() * 2 - 1) * sp + dirz * e.outward + dirx * e.swirl;
            pAge[i] = 0;
            pLife[i] = e.lifeMin + R.nextFloat() * (e.lifeMax - e.lifeMin);
            pSize[i] = (e.sizeMin + R.nextFloat() * (e.sizeMax - e.sizeMin)) * Math.max(0.5f, Math.min(2f, scale));
            pSizeEnd[i] = e.sizeEnd;
            pSizeEase[i] = e.sizeEase;
            pGrav[i] = e.gravity; pDrag[i] = e.drag; pWind[i] = e.wind;
            pAlpha[i] = e.alpha * alphaMul;
            pColor[i] = e.color; pColorEnd[i] = e.colorEnd < 0 ? e.color : e.colorEnd;
            pKind[i] = (byte) e.kind.ordinal();
            pOwner[i] = owner;
            pFloor[i] = floor;
        }
    }

    /** Wind the particles drift in: the trees' wind field heading (environment/wind/WindField), ~1.2 blocks/s. */
    static float windX = 0, windZ = 0;

    static void stepParticles(float dt) {
        if (dt <= 0) return;
        try {
            double sec = MC.level == null ? 0 : MC.level.getGameTime() / 20.0;
            double hd = com.solegendary.reignofnether.environment.wind.WindField.heading(sec);
            float gust = 1.0f + 0.35f * (float) Math.sin(sec * 0.7);
            windX = (float) Math.cos(hd) * 1.2f * gust;
            windZ = (float) Math.sin(hd) * 1.2f * gust;
        } catch (Throwable ignored) { }
        int w = 0;
        for (int i = 0; i < pCount; i++) {
            pAge[i] += dt;
            if (pAge[i] >= pLife[i]) continue;
            float k = Math.min(1, pDrag[i] * dt);
            pvx[i] += (windX * pWind[i] - pvx[i]) * k;
            pvz[i] += (windZ * pWind[i] - pvz[i]) * k;
            pvy[i] -= pvy[i] * k * 0.5f;
            pvy[i] -= pGrav[i] * dt;
            px[i] += pvx[i] * dt; py[i] += pvy[i] * dt; pz[i] += pvz[i] * dt;
            if (py[i] < pFloor[i] && pvy[i] < 0) {
                int kind = pKind[i];
                if (kind == P_SPARK) { py[i] = pFloor[i]; pvy[i] *= -0.3f; pvx[i] *= 0.5f; pvz[i] *= 0.5f; }
                else if (kind == P_EMBER) { py[i] = pFloor[i]; pvx[i] = pvy[i] = pvz[i] = 0; pLife[i] = Math.min(pLife[i], pAge[i] + 0.4f); }
            }
            if (w != i) copyParticle(i, w);
            w++;
        }
        for (int i = w; i < pCount; i++) pOwner[i] = null;
        pCount = w;
    }

    private static void copyParticle(int from, int to) {
        px[to] = px[from]; py[to] = py[from]; pz[to] = pz[from];
        pvx[to] = pvx[from]; pvy[to] = pvy[from]; pvz[to] = pvz[from];
        pAge[to] = pAge[from]; pLife[to] = pLife[from]; pSize[to] = pSize[from]; pSizeEnd[to] = pSizeEnd[from];
        pGrav[to] = pGrav[from]; pDrag[to] = pDrag[from]; pWind[to] = pWind[from]; pAlpha[to] = pAlpha[from];
        pFloor[to] = pFloor[from]; pColor[to] = pColor[from]; pColorEnd[to] = pColorEnd[from]; pKind[to] = pKind[from];
        pOwner[to] = pOwner[from]; pSizeEase[to] = pSizeEase[from];
    }

    // ------------------------------------------------------------------ lights

    static int lightCount = 0;
    static final float[] lx = new float[MAX_LIGHTS], ly = new float[MAX_LIGHTS], lz = new float[MAX_LIGHTS];
    static final float[] lr = new float[MAX_LIGHTS], li = new float[MAX_LIGHTS];

    static void addLight(float x, float y, float z, float radius, float intensity) {
        if (lightCount >= MAX_LIGHTS || intensity <= 0.01f || radius <= 0) return;
        lx[lightCount] = x; ly[lightCount] = y; lz[lightCount] = z; lr[lightCount] = radius; li[lightCount] = intensity;
        lightCount++;
    }

    /**
     * Block light (0-15) the live effects cast at a point, 0 when none reaches it. Called for every entity drawn
     * (mixin/VfxEntityLightMixin): with no live light it is one int compare.
     */
    public static int blockLightAt(double x, double y, double z) {
        int n = lightCount;
        if (n == 0) return 0;
        float best = 0;
        for (int i = 0; i < n; i++) {
            float dx = (float) x - lx[i], dy = ((float) y - ly[i]) * 0.7f, dz = (float) z - lz[i];
            float d = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
            float v = li[i] * VfxTimeline.falloff(d, lr[i]);
            if (v > best) best = v;
        }
        return Math.round(best * 15f);
    }

    // ------------------------------------------------------------------ projectile visuals

    static final class Trail {
        final float[] x = new float[48], y = new float[48], z = new float[48];
        int head = 0, count = 0;
        float lastSeen, acc;
        float[] emitAcc = new float[1];
    }

    private static final Int2ObjectOpenHashMap<Trail> TRAILS = new Int2ObjectOpenHashMap<>();
    private static final List<Entity> projQueue = new ArrayList<>();
    private static final List<VfxDef> projDefs = new ArrayList<>();
    private static final List<Vec3> projPos = new ArrayList<>();

    /**
     * Called by VfxProjectileRenderer when it draws a projectile with a visual: the core, ribbon, shed particles and
     * light are drawn later in the batched pass (one buffer for every projectile on screen, not one per entity).
     * Never from the shader pack's shadow pass.
     */
    public static void queueProjectile(Entity e, VfxDef def, float partialTick) {
        if (projQueue.size() >= MAX_PROJECTILES) return;
        projQueue.add(e);
        projDefs.add(def);
        projPos.add(e.getPosition(partialTick).add(0, e.getBbHeight() * 0.5, 0));
    }

    private static void projectiles(float dt, float now, float q) {
        for (int k = 0; k < projQueue.size(); k++) {
            Entity e = projQueue.get(k);
            VfxDef.Projectile p = projDefs.get(k).projectile;
            Vec3 pos = projPos.get(k);
            float x = (float) pos.x, y = (float) pos.y, z = (float) pos.z;
            Trail t = TRAILS.get(e.getId());
            if (t == null) {
                if (TRAILS.size() > MAX_PROJECTILES * 2) continue;
                t = new Trail();
                TRAILS.put(e.getId(), t);
            }
            t.lastSeen = now;
            int cap = Math.min(48, Math.max(1, p.trailSamples));
            int last = (t.head - 1 + 48) % 48;
            float ddx = x - t.x[last], ddy = y - t.y[last], ddz = z - t.z[last];
            if (t.count == 0 || ddx * ddx + ddy * ddy + ddz * ddz >= p.trailStep * p.trailStep) {
                t.x[t.head] = x; t.y[t.head] = y; t.z[t.head] = z;
                t.head = (t.head + 1) % 48;
                t.count = Math.min(cap, t.count + 1);
            }
            if (p.emitter != null && q > 0) {
                int n = VfxTimeline.spawnCount(p.emitter.rate * q, dt, t.emitAcc);
                if (n > 0) spawn(p.emitter, null, n, x, y, z, 1, -1e9f, 1);
            }
            VfxDef.Light li = projDefs.get(k).light;
            if (li != null && q > 0) addLight(x, y, z, li.radius, li.intensity);
        }
        // forget trails of projectiles not drawn for a second (hit, expired, out of view)
        if (!TRAILS.isEmpty() && R.nextInt(30) == 0)
            TRAILS.int2ObjectEntrySet().removeIf(en -> now - en.getValue().lastSeen > 1f);
    }

    private static void drawProjectiles(float now) {
        for (int k = 0; k < projQueue.size(); k++) {
            VfxDef.Projectile p = projDefs.get(k).projectile;
            Vec3 pos = projPos.get(k);
            float x = (float) pos.x, y = (float) pos.y, z = (float) pos.z;
            Trail t = TRAILS.get(projQueue.get(k).getId());
            if (t != null && t.count >= 1 && p.trailSamples > 0)
                ribbon(t, x, y, z, p);
            float pulse = 1 + p.pulse * (float) Math.sin(now * 13 + projQueue.get(k).getId());
            sprite(p.coreTile, x, y, z, p.coreSize * pulse, p.coreColor, 0.9f, 0);
            if (p.innerSize > 0)
                sprite(VfxAtlas.GLOW, x, y, z, p.innerSize * pulse, p.innerColor, 1f, 0);
        }
    }

    /** A camera-facing ribbon from the projectile back through its samples: tapers and cools toward the tail. */
    private static void ribbon(Trail t, float hx, float hy, float hz, VfxDef.Projectile p) {
        int n = t.count;
        float prevX = hx, prevY = hy, prevZ = hz;
        float prevW = p.trailWidth, prevA = p.trailAlpha;
        int prevC = p.trailColor;
        int endC = p.trailColorEnd < 0 ? p.trailColor : p.trailColorEnd;
        for (int s = 0; s < n; s++) {
            int idx = (t.head - 1 - s + 96) % 48;
            float x = t.x[idx], y = t.y[idx], z = t.z[idx];
            float f = (s + 1f) / n;
            float w = p.trailWidth * (1 - f);
            float a = p.trailAlpha * (1 - f) * (1 - f);
            int c = lerpColor(p.trailColor, endC, f);
            // side vector: segment direction x view direction
            float dx = prevX - x, dy = prevY - y, dz = prevZ - z;
            float sx = dy * BarFxClient.lookZ - dz * BarFxClient.lookY;
            float sy = dz * BarFxClient.lookX - dx * BarFxClient.lookZ;
            float sz = dx * BarFxClient.lookY - dy * BarFxClient.lookX;
            float sl = (float) Math.sqrt(sx * sx + sy * sy + sz * sz);
            if (sl < 1e-5f) continue;
            sx /= sl; sy /= sl; sz /= sl;
            float u0 = VfxAtlas.u(VfxAtlas.TRAIL, (s) / (float) n), u1 = VfxAtlas.u(VfxAtlas.TRAIL, (s + 1f) / n);
            float v0 = VfxAtlas.v0(VfxAtlas.TRAIL), v1 = VfxAtlas.v1(VfxAtlas.TRAIL);
            vt(prevX - sx * prevW, prevY - sy * prevW, prevZ - sz * prevW, prevC, prevA, u0, v0);
            vt(prevX + sx * prevW, prevY + sy * prevW, prevZ + sz * prevW, prevC, prevA, u0, v1);
            vt(x + sx * w, y + sy * w, z + sz * w, c, a, u1, v1);
            vt(x - sx * w, y - sy * w, z - sz * w, c, a, u1, v0);
            prevX = x; prevY = y; prevZ = z; prevW = w; prevA = a; prevC = c;
        }
    }

    // ------------------------------------------------------------------ frame

    static long lastNanos = -1;
    /** Longest step a frame may take (s): a hitch does not teleport particles through the ground. */
    static final float MAX_DT = 0.25f;
    /** Quads drawn (the eyes scene logs it: drawn-but-invisible vs not drawn at all). */
    static int quads = 0, quadsLastFrame = 0;

    public static int quadsLastFrame() { return quadsLastFrame / 4; }

    /** Age (effect seconds) of the newest live effect, or -1 (the eyes scene times its stills by it). */
    public static float newestAge() {
        return live.isEmpty() ? -1 : vclock - live.get(live.size() - 1).born;
    }
    /** The effects' own clock (seconds): BarFx's clock times {@link #timeScale}. */
    static float vclock = 0;
    /**
     * Playback speed of the effects (1 = real time). The eyes scene slows them (EyesVfxScene: 0.25) so a software-
     * rendered client a few frames a second still photographs each phase; never changed in play.
     */
    public static float timeScale = 1f;
    static ClientLevel lastLevel = null;

    public static void clear() {
        live.clear();
        for (int i = 0; i < pCount; i++) pOwner[i] = null;
        pCount = 0;
        lightCount = 0;
        TRAILS.clear();
        projQueue.clear(); projDefs.clear(); projPos.clear();
    }

    /** Step and draw everything. Called from BarFxClientEvents right after BarFx's own pass (camera already set). */
    public static void onRenderFrame(com.mojang.blaze3d.vertex.PoseStack poseStack, float partialTick) {
        ClientLevel level = MC.level;
        if (level == null || level != lastLevel) { clear(); lastLevel = level; lastNanos = -1; if (level == null) return; }
        // own frame timer: BarFx's clock caps a frame at 0.1 s, which at the eyes client's ~2 fps stretched a 3.7 s
        // effect over half a minute (first eyes run, Oct 22: the stills caught the wind-up's first tenth)
        long nanos = System.nanoTime();
        float dt = lastNanos < 0 || MC.isPaused() ? 0 : Math.min(MAX_DT, (nanos - lastNanos) / 1e9f) * Math.max(0, timeScale);
        lastNanos = nanos;
        vclock += dt;
        quadsLastFrame = quads;
        quads = 0;
        float now = vclock;
        int quality = quality();
        float q = QUALITY_SCALE[quality];
        if (BarFxClient.live.size() > BarFxClient.MAX * 3 / 4) q *= 0.5f;   // the battle effects are busy: spell particles give way

        lightCount = 0;
        stepInstances(dt, now, q, partialTick);
        projectiles(dt, now, q);
        stepParticles(dt);
        if (live.isEmpty() && pCount == 0 && projQueue.isEmpty()) return;

        MultiBufferSource.BufferSource buffers = MC.renderBuffers().bufferSource();
        mat = poseStack.last().pose();
        // 1. smoke and heat (blended) first, so the light below adds on top of the veil
        if (pCount > 0) {
            begin(buffers, BarFxRenderTypes.VFX_TRANSLUCENT);
            additive = false;
            drawParticles(true);
            buffers.endBatch(BarFxRenderTypes.VFX_TRANSLUCENT);
        }
        // 2. everything that is light
        // with a shader pack the light goes through the entity emissive program (see BarFxRenderTypes.VFX_EMISSIVE)
        RenderType lightType = com.solegendary.reignofnether.postfx.ShaderPacks.inUse()
            ? BarFxRenderTypes.VFX_EMISSIVE : BarFxRenderTypes.VFX_ADDITIVE;
        begin(buffers, lightType);
        additive = lightType == BarFxRenderTypes.VFX_ADDITIVE;
        light = 0xF000F0;
        for (Inst in : live) if (in.visible && BarFxClient.near(in.x, in.y, in.z, in.def.cullRadius * in.scale + 4)) drawInstance(in, now - in.born, now, quality);
        drawParticles(false);
        drawProjectiles(now);
        buffers.endBatch(lightType);
        vc = null;
        projQueue.clear(); projDefs.clear(); projPos.clear();
    }

    private static void stepInstances(float dt, float now, float q, float partialTick) {
        int w = 0;
        for (int i = 0; i < live.size(); i++) {
            Inst in = live.get(i);
            float t = now - in.born;
            if (t >= in.def.timeline.total()) continue;
            live.set(w++, in);
            if (in.follow >= 0) {
                Entity e = MC.level.getEntity(in.follow);
                if (e == null || e.isRemoved()) in.follow = -1;
                else {
                    Vec3 p = e.getPosition(partialTick);
                    in.x = (float) p.x; in.y = (float) p.y; in.z = (float) p.z;
                    if ((int) Math.floor(in.x) != in.gx || (int) Math.floor(in.z) != in.gz) sampleGround(in);
                }
            }
            if (now >= in.nextFogCheck) {
                in.visible = brightAt(in.x, in.y, in.z);
                in.nextFogCheck = now + 0.25f;
            }
            if (!in.visible) continue;
            VfxDef.Timeline tl = in.def.timeline;
            if (q > 0 && in.def.light != null) {
                VfxDef.Light li = in.def.light;
                float a = VfxTimeline.lightAlpha(li, tl, t) * li.intensity * (1 - li.flicker * R.nextFloat());
                addLight(in.x, in.ground + li.y * in.scale, in.z, li.radius * in.scale, a);
            }
            if (q <= 0) continue;
            for (int k = 0; k < in.def.layers.size(); k++) {
                if (!(in.def.layers.get(k) instanceof VfxDef.Emitter e)) continue;
                if (!VfxTimeline.layerActive(e, tl, t)) continue;
                float cy = (e.onGround ? in.ground : in.y) + e.y * in.scale;
                float floor = in.ground + 0.05f;
                if (!in.burst[k]) {
                    in.burst[k] = true;
                    int n = Math.round(e.burst * q);
                    if (n > 0) spawn(e, in, n, in.x, cy, in.z, in.scale, floor, 1);
                }
                float[] acc = { in.acc[k] };
                int n = VfxTimeline.spawnCount(e.rate * q, dt, acc);
                in.acc[k] = acc[0];
                if (n > 0) spawn(e, in, n, in.x, cy, in.z, in.scale, floor, VfxTimeline.layerAlpha(e, tl, t));
            }
        }
        for (int i = live.size() - 1; i >= w; i--) live.remove(i);
    }

    // ------------------------------------------------------------------ drawing

    static VertexConsumer vc;
    static Matrix4f mat;

    static void begin(MultiBufferSource.BufferSource buffers, RenderType type) {
        vc = buffers.getBuffer(type);
    }

    /** Which pass is being written: additive (premultiplied, ONE + ONE) or translucent (lit by {@link #light}). */
    static boolean additive = true;
    /** Packed light for translucent vertices (smoke is lit by the world; light ignores it). */
    static int light = 0xF000F0;

    /** One NEW_ENTITY vertex (the eyes / entity-translucent states, BarFxRenderTypes). */
    static void vt(float x, float y, float z, int rgb, float a, float u, float v) {
        quads++;   // per vertex; divided by 4 when read
        a = Math.max(0, Math.min(1, a));
        float k = additive ? a : 1;   // ONE + ONE ignores alpha: the brightness rides in the colour
        vc.vertex(mat, x - BarFxClient.camX, y - BarFxClient.camY, z - BarFxClient.camZ)
            .color(((rgb >> 16) & 255) / 255f * k, ((rgb >> 8) & 255) / 255f * k, (rgb & 255) / 255f * k, a)
            .uv(u, v).overlayCoords(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY).uv2(light)
            .normal(0, 1, 0).endVertex();
    }

    /** The world's packed light at a point (smoke is dark at night, lit by torches). */
    static int lightAt(float x, float y, float z) {
        try {
            return net.minecraft.client.renderer.LevelRenderer.getLightColor(MC.level, BlockPos.containing(x, y, z));
        } catch (Exception e) {
            return 0xF000F0;
        }
    }

    static int lerpColor(int a, int b, float f) {
        if (a == b) return a;
        f = Math.max(0, Math.min(1, f));
        int r = (int) (((a >> 16) & 255) * (1 - f) + ((b >> 16) & 255) * f);
        int g = (int) (((a >> 8) & 255) * (1 - f) + ((b >> 8) & 255) * f);
        int bl = (int) ((a & 255) * (1 - f) + (b & 255) * f);
        return (r << 16) | (g << 8) | bl;
    }

    /** A camera-facing square sprite of half-size s, rotated by rot radians in the view plane. */
    static void sprite(int tile, float x, float y, float z, float s, int c, float a, float rot) {
        float cs = (float) Math.cos(rot) * s, sn = (float) Math.sin(rot) * s;
        float ax = BarFxClient.leftX * cs + BarFxClient.upX * sn, ay = BarFxClient.leftY * cs + BarFxClient.upY * sn, az = BarFxClient.leftZ * cs + BarFxClient.upZ * sn;
        float bx = -BarFxClient.leftX * sn + BarFxClient.upX * cs, by = -BarFxClient.leftY * sn + BarFxClient.upY * cs, bz = -BarFxClient.leftZ * sn + BarFxClient.upZ * cs;
        float u0 = VfxAtlas.u0(tile), u1 = VfxAtlas.u1(tile), v0 = VfxAtlas.v0(tile), v1 = VfxAtlas.v1(tile);
        vt(x - ax - bx, y - ay - by, z - az - bz, c, a, u0, v1);
        vt(x + ax - bx, y + ay - by, z + az - bz, c, a, u1, v1);
        vt(x + ax + bx, y + ay + by, z + az + bz, c, a, u1, v0);
        vt(x - ax + bx, y - ay + by, z - az + bz, c, a, u0, v0);
    }

    /** A flat disc quad (tile) of radius r, turned by angle, optionally tilted toward heading phi. */
    static void flat(int tile, float x, float y, float z, float r, float angle, float tilt, float phi, int c, float a) {
        float ca = (float) Math.cos(angle) * r, sa = (float) Math.sin(angle) * r;
        float tx = (float) Math.cos(phi) * tilt, tz = (float) Math.sin(phi) * tilt;   // slope (dy per block) of the tilt
        float[] cx = { -ca + sa, ca + sa, ca - sa, -ca - sa };
        float[] cz = { -sa - ca, sa - ca, sa + ca, -sa + ca };
        float u0 = VfxAtlas.u0(tile), u1 = VfxAtlas.u1(tile), v0 = VfxAtlas.v0(tile), v1 = VfxAtlas.v1(tile);
        float[] us = { u0, u1, u1, u0 }, vs = { v0, v0, v1, v1 };
        for (int i = 0; i < 4; i++)
            vt(x + cx[i], y + cx[i] * tx + cz[i] * tz, z + cz[i], c, a, us[i], vs[i]);
    }

    /** A tile laid on the terrain: an n x n grid whose vertices follow the instance's height grid. */
    static void groundTile(Inst in, int tile, float r, float angle, int c, float a) {
        int n = r > 2.5f ? 10 : 6;
        float ca = (float) Math.cos(angle), sa = (float) Math.sin(angle);
        float u0 = VfxAtlas.u0(tile), u1 = VfxAtlas.u1(tile), v0 = VfxAtlas.v0(tile), v1 = VfxAtlas.v1(tile);
        float step = 2 * r / n;
        for (int j = 0; j < n; j++) {
            for (int i = 0; i < n; i++) {
                for (int k = 0; k < 4; k++) {
                    int ci = i + (k == 1 || k == 2 ? 1 : 0), cj = j + (k >= 2 ? 1 : 0);   // corners in quad order
                    float lx = -r + ci * step, lz = -r + cj * step;
                    float wx = in.x + lx * ca - lz * sa, wz = in.z + lx * sa + lz * ca;
                    vt(wx, heightAt(in, wx, wz) + 0.06f, wz, c, a, u0 + (u1 - u0) * ci / n, v0 + (v1 - v0) * cj / n);
                }
            }
        }
    }

    static void drawInstance(Inst in, float t, float now, int quality) {
        VfxDef def = in.def;
        VfxDef.Timeline tl = def.timeline;
        float s = in.scale;
        // the light pool first: the warm light the effect throws on the ground under everything else
        if (def.light != null && quality > 0) {
            VfxDef.Light li = def.light;
            float a = VfxTimeline.lightAlpha(li, tl, t) * li.intensity * 0.32f;
            if (a > 0.005f) groundTile(in, VfxAtlas.GLOW, li.radius * s, 0, li.color, a);
        }
        for (VfxDef.Layer l : def.layers) {
            if (l instanceof VfxDef.Emitter) continue;
            float a = VfxTimeline.layerAlpha(l, tl, t) * l.alpha;
            if (a <= 0.003f) continue;
            float g = VfxTimeline.layerScale(l, t);
            float lt = t - l.start;
            if (l instanceof VfxDef.Ground gr) {
                if (gr.flicker > 0) a *= 1 - gr.flicker * (0.5f + 0.5f * (float) Math.sin(now * 23 + in.born * 7) * (float) Math.sin(now * 9.1f));
                groundTile(in, gr.tile, gr.radius * s * g, (float) Math.toRadians(gr.spin * lt), gr.color, a);
            } else if (l instanceof VfxDef.Ring rg) {
                float base = (rg.onGround ? in.ground : in.y) + rg.y * s;
                float winLen = Math.max(0.01f, VfxTimeline.layerEnd(rg, tl) - rg.start);
                float rise = rg.rise * s * VfxTimeline.smooth(lt / winLen);
                float tilt = (float) Math.tan(Math.toRadians(rg.tilt));
                for (int k = 0; k < rg.stack; k++) {
                    float r = Math.max(0.1f, (rg.radius + k * rg.radiusStep) * s * g);
                    float dir = rg.alternate && (k & 1) == 1 ? -1 : 1;
                    float ang = (float) Math.toRadians(rg.spin * lt * dir + k * 17);
                    float yk = base + (k * rg.spacing * s + rise * (k + 1) / rg.stack);
                    flat(rg.tile, in.x, yk, in.z, r, ang, tilt, now * 0.6f + k * 2.1f, rg.color, a * (1 - k * 0.12f));
                }
            } else if (l instanceof VfxDef.Beam b) {
                beam(in, b, lt, a, g, s, now);
            }
        }
    }

    static void beam(Inst in, VfxDef.Beam b, float lt, float a, float g, float s, float now) {
        float y0 = in.ground + b.y * s;   // a shaft always stands on the ground
        float h = b.height * s;
        float rb = b.radius * s * g;
        // axis billboard: the quad turns about the vertical to face the camera (a soft round shaft from any side)
        float sx = BarFxClient.lookZ, sz = -BarFxClient.lookX;
        float sl = (float) Math.sqrt(sx * sx + sz * sz);
        if (sl < 1e-4f) { sx = 1; sz = 0; } else { sx /= sl; sz /= sl; }
        float rep = Math.max(2f, rb * 8);                  // blocks per texture repeat along the shaft
        float off = (lt * b.scroll) % 1f;
        shaft(in.x, in.z, y0, h, rb, sx, sz, rep, off, b.color, a);
        if (b.core > 0) shaft(in.x, in.z, y0, h * 0.85f, rb * b.core, sx, sz, rep * 0.7f, (off * 1.7f) % 1f, b.coreColor, a);
        // where the shaft meets the ground: a flare
        sprite(VfxAtlas.GLOW, in.x, y0 + 0.3f * s, in.z, rb * 3.2f, b.color, a * 0.8f, 0);
        // the double helix of sparkles
        if (b.strands > 0) {
            float hh = Math.min(h, 24 * s);
            int pts = b.helixPoints;
            for (int st = 0; st < b.strands; st++) {
                float ph = st * 6.2831855f / b.strands + now * b.helixSpeed * 6.2831855f * 0.25f;
                for (int i = 0; i < pts; i++) {
                    float f = i / (float) (pts - 1);
                    float th = ph + f * b.turns * 6.2831855f;
                    float hr = b.helixRadius * s * g * (1 - 0.25f * f);
                    float twinkle = 0.65f + 0.35f * (float) Math.sin(now * 9 + i * 1.7f + st * 3);
                    sprite(VfxAtlas.GLINT, in.x + (float) Math.cos(th) * hr, y0 + f * hh, in.z + (float) Math.sin(th) * hr,
                        b.glintSize * s * twinkle, b.helixColor, a * (1 - f * 0.8f), th);
                }
            }
        }
        // bands climbing the shaft
        if (b.bands > 0) {
            float hb = Math.min(h, 20 * s);
            for (int k = 0; k < b.bands; k++) {
                float ph = ((now * b.bandSpeed / hb) + k / (float) b.bands) % 1f;
                float fa = (float) Math.sin(Math.PI * ph);
                flat(b.bandTile, in.x, y0 + ph * hb, in.z, b.bandRadius * s * g * (1 - 0.35f * ph), now * 2 + k, 0, 0, b.color, a * fa * 0.8f);
            }
        }
    }

    /** One axis-billboard shaft from y0 up h, its texture scrolling up, fading out toward the top. */
    static void shaft(float x, float z, float y0, float h, float r, float sx, float sz, float rep, float off, int c, float a) {
        int tile = VfxAtlas.BEAM;
        float u0 = VfxAtlas.u0(tile), u1 = VfxAtlas.u1(tile);
        // segments aligned to texture repeats, so v never wraps inside a quad (the atlas cannot REPEAT one tile)
        float yStart = y0 - off * rep;
        for (float ys = yStart; ys < y0 + h; ys += rep) {
            float a0 = Math.max(ys, y0), a1 = Math.min(ys + rep, y0 + h);
            if (a1 <= a0) continue;
            float vA = VfxAtlas.v(tile, 1 - (a0 - ys) / rep), vB = VfxAtlas.v(tile, 1 - (a1 - ys) / rep);
            float fA = fadeUp((a0 - y0) / h), fB = fadeUp((a1 - y0) / h);
            vt(x - sx * r, a0, z - sz * r, c, a * fA, u0, vA);
            vt(x + sx * r, a0, z + sz * r, c, a * fA, u1, vA);
            vt(x + sx * r, a1, z + sz * r, c, a * fB, u1, vB);
            vt(x - sx * r, a1, z - sz * r, c, a * fB, u0, vB);
        }
    }

    /** Brightness up a shaft: a short soft foot, full low down, thinning into the sky. */
    static float fadeUp(float f) {
        float foot = Math.min(1, f * 25);
        float top = 1 - f;
        return foot * top * (float) Math.sqrt(top);
    }

    static void drawParticles(boolean translucentPass) {
        for (int i = 0; i < pCount; i++) {
            int kind = pKind[i];
            boolean translucent = kind == P_SMOKE || kind == P_HEAT;
            if (translucent != translucentPass) continue;
            Inst o = pOwner[i];
            if (o != null && !o.visible) continue;
            if (!BarFxClient.near(px[i], py[i], pz[i], 1)) continue;
            float f = pAge[i] / pLife[i];
            float fade = Math.min(1, f * 8) * (f > 0.6f ? (1 - f) / 0.4f : 1);
            float size = pSize[i] * (1 + (pSizeEnd[i] - 1) * pSizeEase[i].apply(f));
            int c = lerpColor(pColor[i], pColorEnd[i], (float) Math.pow(f, 0.7));
            float a = pAlpha[i] * fade;
            switch (kind) {
                case P_EMBER -> {
                    float tw = 0.75f + 0.25f * (float) Math.sin(pAge[i] * 21 + i);
                    sprite(VfxAtlas.EMBER, px[i], py[i], pz[i], size * 1.6f, c, a * tw, 0);
                }
                case P_SPARK -> streak(px[i], py[i], pz[i], pvx[i], pvy[i], pvz[i], size, c, a);
                case P_GLINT -> sprite(VfxAtlas.GLINT, px[i], py[i], pz[i], size, c, a * (0.6f + 0.4f * (float) Math.sin(pAge[i] * 14 + i)), pAge[i] * 1.5f);
                case P_SMOKE -> { light = lightAt(px[i], py[i], pz[i]); sprite(VfxAtlas.SMOKE, px[i], py[i], pz[i], size, c, a, i * 0.7f + pAge[i] * 0.3f); }
                case P_HEAT -> { light = lightAt(px[i], py[i], pz[i]); sprite(VfxAtlas.HEAT, px[i], py[i], pz[i], size, c, a * 0.5f, 0); }
                default -> { }
            }
        }
    }

    /** A spark: a streak along its velocity (longer the faster), facing the camera. */
    static void streak(float x, float y, float z, float vx, float vy, float vz, float w, int c, float a) {
        float sp = (float) Math.sqrt(vx * vx + vy * vy + vz * vz);
        if (sp < 1e-3f) { sprite(VfxAtlas.GLOW, x, y, z, w, c, a, 0); return; }
        float len = Math.max(w * 2, Math.min(1.2f, sp * 0.06f));
        float dx = vx / sp, dy = vy / sp, dz = vz / sp;
        float sx = dy * BarFxClient.lookZ - dz * BarFxClient.lookY;
        float sy = dz * BarFxClient.lookX - dx * BarFxClient.lookZ;
        float sz = dx * BarFxClient.lookY - dy * BarFxClient.lookX;
        float sl = (float) Math.sqrt(sx * sx + sy * sy + sz * sz);
        if (sl < 1e-4f) { sprite(VfxAtlas.GLOW, x, y, z, w, c, a, 0); return; }
        sx = sx / sl * w; sy = sy / sl * w; sz = sz / sl * w;
        float tx = x - dx * len, ty = y - dy * len, tz = z - dz * len;
        int tile = VfxAtlas.SPARK;
        float u0 = VfxAtlas.u0(tile), u1 = VfxAtlas.u1(tile), v0 = VfxAtlas.v0(tile), v1 = VfxAtlas.v1(tile);
        vt(tx - sx, ty - sy, tz - sz, c, a, u0, v0);
        vt(tx + sx, ty + sy, tz + sz, c, a, u0, v1);
        vt(x + sx, y + sy, z + sz, c, a, u1, v1);
        vt(x - sx, y - sy, z - sz, c, a, u1, v0);
    }
}
