package com.solegendary.reignofnether.barfx;

import com.solegendary.reignofnether.unit.FireBreath;
import com.solegendary.reignofnether.unit.units.monsters.BoneDragonUnit;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Client: draws a dragon's blue fire breath every client tick (lovish, Nov 5: "the bone dragon should get a blue fire
 * breath attack from the air not the current ghast sound effects and projectiles"; the old breath was one burst of
 * vanilla soul-fire particles every 3.3 s that stopped ~8 blocks over its victims). Everything comes from the state the
 * server syncs (BoneDragonUnit's breath accessors) and the rules the server burns by (FireBreath), so the fire shows
 * exactly where it burns - nothing extra is sent while it breathes.
 *
 * <p>Layers, per tick and per breathing dragon, from the blind Fable critic's rounds (r1 "a white laser"; r2 "no
 * directed stream", "flat colour"; r3 "a flat solid wedge, a jet of liquid", "soap foam that buries the squad"):
 * <ul>
 *   <li>the STREAM: textured flame tongues (the atlas of tools/breath_sprites.py, BarFxClient.drawFlame) laid along
 *   their motion from the real jaw bone (BoneDragonRenderer) toward FireBreath.flame's point on the aim's ground
 *   inside the cone - a white-hot core that races ahead (added light), a light-blue body that wobbles side to side
 *   (ALPHA-BLENDED, BarFxClient P.blend, so blue stays blue over grass), dark wisps rising off it - and the jaws lit;</li>
 *   <li>the IMPACT: tongues racing out along the ground, faint at the centre so a unit inside still shows; a pulsing
 *   glow on the ground; a ragged burning ring; sparks flung up and out; embers left on the burnt ground; a dark scorch;
 *   the units standing in it lit blue with flames licking up them.</li>
 * </ul>
 * Our own quads (BarFxClient's soft discs), no texture, no vanilla particle. Sizes grow with the RTS zoom (readability
 * R6: x clamp(zoom / 30, 1, 2.5)), counts do not: ~55 new quads a tick per dragon. Nothing is drawn in the fog of war.
 */
@OnlyIn(Dist.CLIENT)
public final class BreathFx {
    private BreathFx() { }

    private static final Minecraft MC = Minecraft.getInstance();
    /** Per tick: core tongues, body tongues, wisps, splash tongues, ring flames, sparks, embers; licks on at most LICKS_MAX. */
    static final int CORES = 10, BODY = 8, WISPS = 2, SPLASH = 12, RING = 10, SPARKS = 6, EMBERS = 1, LICKS_MAX = 6;
    /** The jaws the renderer found are trusted this many client ticks (an undrawn dragon's are guessed instead). */
    static final long JAWS_FRESH = 3;
    // soul-fire (Fable round 3: away from a flat "water" blue): each flame fades from its first colour to its second
    static final int CORE = 0xF2FFFF, CORE_END = 0x8AF3FF, BODY_C = 0x5FD3FF, BODY_END = 0x2C6FE0, WISP = 0x1A2E6E,
        WISP_END = 0x0A1030, SPLASH_C = 0x9FF0FF, SPLASH_END = 0x1B3FAA, GROUND_GLOW = 0x1FA8FF, RING_C = 0x4FD0FF,
        SPARK = 0xBFF4FF, EMBER = 0x6FE8FF, EMBER_END = 0x2A6DFF, SCORCH = 0x0A1F5C, LICK = 0x8FE0FF, LICK_END = 0x2A6DFF,
        LIT = 0x66B8FF, JAW_GLOW = 0xBFEFFF, JAW_LIGHT = 0x5FD3FF;

    private static final double[] V = new double[3];
    private static final List<BoneDragonUnit> TICKING = new ArrayList<>();
    private static long clientTicks = 0;

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent evt) {
        if (evt.phase != TickEvent.Phase.END || MC.level == null || MC.isPaused())
            return;
        clientTicks++;
        if (BoneDragonUnit.CLIENT_BREATHING.isEmpty())
            return;
        TICKING.clear();
        TICKING.addAll(BoneDragonUnit.CLIENT_BREATHING);
        for (BoneDragonUnit d : TICKING) {
            if (d.isRemoved() || d.level() != MC.level || !d.isBreathingClient()) {
                BoneDragonUnit.CLIENT_BREATHING.remove(d);
                continue;
            }
            try {
                draw(d, d.clientBreathTick);
            } catch (Exception e) {
                BoneDragonUnit.CLIENT_BREATHING.remove(d);   // never take the game down over a visual
            }
            d.clientBreathTick++;
        }
        TICKING.clear();
    }

    /** The client tick count, for the renderer's "jaws found at" stamp. */
    public static long now() {
        return clientTicks;
    }

    private static float rnd(float a, float b) {
        return BarFxClient.rnd(a, b);
    }

    /** A BREATH particle (BarFxClient), its velocity, end colour, growth, ground and alpha set in one go. */
    private static BarFxClient.P flame(float x, float y, float z, float life, float size, int c1, int c2, float vx, float vy,
                                       float vz, float grow, float ground, float alpha, boolean blend) {
        BarFxClient.P p = BarFxClient.add(BarFxClient.BREATH, x, y, z, life, size, c1);
        p.vx = vx; p.vy = vy; p.vz = vz;
        p.color2 = c2;
        p.grow = grow;
        p.ground = ground;
        p.alpha = alpha;
        p.blend = blend;
        return p;
    }

    /** One tick of one dragon's breath, tick t of FireBreath.TICKS. */
    static void draw(BoneDragonUnit d, int t) {
        boolean landed = d.getEntityData().get(BoneDragonUnit.breathLandedAccessor);
        Vector3f base = d.getEntityData().get(BoneDragonUnit.breathAimAccessor);
        Vector3f off = d.getEntityData().get(BoneDragonUnit.breathOffsetAccessor);
        double bx = base.x(), by = base.y(), bz = base.z();
        // a unit target: where the client sees it now (smooth), the way the server aims at it each tick
        Entity target = MC.level.getEntity(d.getEntityData().get(BoneDragonUnit.breathTargetAccessor));
        if (target != null && target.isAlive()) {
            boolean flyer = target instanceof com.solegendary.reignofnether.unit.interfaces.Unit u ? u.isFlyingUnit() : !target.onGround();
            bx = target.getX();
            by = target.getY() + (flyer ? target.getBbHeight() * 0.5 : 0);
            bz = target.getZ();
        }
        double s = FireBreath.sweep(t);
        float ax = (float) FireBreath.along(bx, off.x(), s), ay = (float) FireBreath.along(by, off.y(), s);
        float az = (float) FireBreath.along(bz, off.z(), s);
        // the jaws: the posed model's, if it was drawn just now; else where the server puts them
        float jx, jy, jz;
        if (now() - d.clientJawsAt <= JAWS_FRESH) {
            jx = (float) d.clientJaws[0]; jy = (float) d.clientJaws[1]; jz = (float) d.clientJaws[2];
        } else {
            double dx = ax - d.getX(), dz = az - d.getZ(), l = Math.sqrt(dx * dx + dz * dz);
            double ahead = BoneDragonUnit.jawsAhead(l);
            if (l < 0.5) {
                Vec3 f = Vec3.directionFromRotation(0, d.yBodyRot);
                dx = f.x; dz = f.z; l = Math.sqrt(dx * dx + dz * dz);
            }
            jx = (float) (d.getX() + dx / l * ahead);
            jy = (float) (d.getY() + BoneDragonUnit.JAWS_UP);
            jz = (float) (d.getZ() + dz / l * ahead);
        }
        if (!BarFxClient.visible(jx, jy, jz) && !BarFxClient.visible(ax, ay, az))
            return;
        float zs = BarFxClient.ortho ? Math.max(1f, Math.min(2.5f, BarFxClient.zoom / 30f)) : 1f;
        double tan = FireBreath.tanHalf(landed);
        float sx = ax - jx, sy = ay - jy, sz = az - jz;
        float dist = (float) Math.sqrt(sx * sx + sy * sy + sz * sz);
        float far = (float) (dist * tan * 0.9);   // the cone's radius where it lands
        // it swells in over the first ticks and gutters out over the last (the jaw opens before, shuts after)
        float swell = Math.min(1f, (t + 1) / 4f) * Math.min(1f, (FireBreath.TICKS - t) / 6f);

        // the stream (Fable round 3: "a flat solid wedge, a jet of liquid"): textured flame TONGUES (the atlas of
        // tools/breath_sprites.py) laid along their motion - a white-hot core that races ahead, a light-blue body that
        // wobbles side to side as it goes, and dark wisps rising off its far half and the impact
        float px = -sz, pz = sx, pl = (float) Math.sqrt(px * px + pz * pz);   // square to the stream, on the ground
        if (pl > 1.0e-3) { px /= pl; pz /= pl; }
        float wobble = 1.5f * (float) Math.sin(now() * 0.6);
        for (int i = 0; i < CORES; i++) {
            FireBreath.flame(jx, jy, jz, ax, ay, az, tan, BarFxClient.R.nextDouble() * .04, BarFxClient.R.nextDouble(), V);
            float k = rnd(22f, 26f) / (float) Math.sqrt(V[0] * V[0] + V[1] * V[1] + V[2] * V[2]);
            float lead = rnd(0, .05f);
            BarFxClient.P p = flame(jx + (float) V[0] * k * lead, jy + (float) V[1] * k * lead, jz + (float) V[2] * k * lead,
                rnd(.35f, .45f), .18f * zs, CORE, CORE_END, (float) V[0] * k, (float) V[1] * k, (float) V[2] * k, 1.6f, ay,
                swell * .85f, false);
            p.tex = BarFxClient.R.nextInt(6);
            p.stretch = 3.4f;
        }
        for (int i = 0; i < BODY; i++) {
            FireBreath.flame(jx, jy, jz, ax, ay, az, tan, BarFxClient.R.nextDouble() * .7, BarFxClient.R.nextDouble(), V);
            // 16-20 blocks a second, or what it takes to reach the ground in its life (a dragon 12 up)
            float k = Math.max(rnd(16f, 20f), dist / .55f) / (float) Math.sqrt(V[0] * V[0] + V[1] * V[1] + V[2] * V[2]);
            float lead = rnd(0, .05f), size = .3f * zs;
            BarFxClient.P p = flame(jx + (float) V[0] * k * lead, jy + (float) V[1] * k * lead, jz + (float) V[2] * k * lead,
                rnd(.5f, .7f), size, BODY_C, BODY_END, (float) V[0] * k + px * wobble, (float) V[1] * k,
                (float) V[2] * k + pz * wobble, 2.7f, ay, swell * .55f, true);
            p.tex = BarFxClient.R.nextInt(6);
            p.stretch = 2.5f;
        }
        for (int i = 0; i < WISPS; i++) {
            float f = i == 0 ? rnd(.5f, 1f) : 1f;
            BarFxClient.P p = flame(jx + sx * f + rnd(-.5f, .5f), jy + sy * f + .4f, jz + sz * f + rnd(-.5f, .5f), rnd(1.2f, 1.8f),
                .75f * zs, WISP, WISP_END, rnd(-.3f, .3f), 1.5f, rnd(-.3f, .3f), 2f, Float.NaN, swell * .25f, true);
            p.tex = 6 + BarFxClient.R.nextInt(2);
            p.spin = rnd(0, 6.28f);
        }
        // the jaws carry the fire: a hot glow on the skull and a wider blue light round it
        BarFxClient.flash(jx, jy, jz, .45f * zs * swell, .1f, JAW_GLOW);
        flame(jx, jy, jz, .1f, 1f * zs, JAW_LIGHT, JAW_LIGHT, 0, 0, 0, 1f, Float.NaN, .25f * swell, false);
        // the impact (round 3: "soap foam that buries the squad"): tongues racing out along the ground, each faint at
        // the centre so a unit inside still shows; a pulsing glow on the ground; a ragged burning ring; sparks flung
        // up and out; embers left lying on the burnt ground; a dark scorch
        for (int i = 0; i < SPLASH; i++) {
            double a = BarFxClient.R.nextDouble() * Math.PI * 2;
            float sp = rnd(5f, 9f), r0 = rnd(0, .3f) * far;
            BarFxClient.P p = flame(ax + (float) Math.cos(a) * r0, ay + .2f, az + (float) Math.sin(a) * r0, rnd(.4f, .7f),
                .25f * zs, SPLASH_C, SPLASH_END, (float) Math.cos(a) * sp, rnd(1f, 2f), (float) Math.sin(a) * sp, 2f,
                Float.NaN, swell * .35f, true);
            p.tex = BarFxClient.R.nextInt(6);
            p.stretch = 3f;
        }
        BarFxClient.P glow = BarFxClient.add(BarFxClient.DECAL, ax, ay + .03f, az, .1f, Math.max(2f, far), GROUND_GLOW);
        glow.alpha = (.3f + rnd(-.1f, .1f)) * swell;
        glow.spin = rnd(0, 6.28f);
        for (int i = 0; i < RING; i++) {
            double a = BarFxClient.R.nextDouble() * Math.PI * 2;
            float rr = rnd(.8f, 1f) * Math.max(2.4f, far);
            flame(ax + (float) Math.cos(a) * rr, ay + .15f, az + (float) Math.sin(a) * rr, .3f, rnd(.06f, .12f) * zs, RING_C,
                RING_C, 0, rnd(.2f, .6f), 0, 1.5f, Float.NaN, swell * .6f, false);
        }
        for (int i = 0; i < SPARKS; i++) {
            double a = BarFxClient.R.nextDouble() * Math.PI * 2;
            float sp = landed ? rnd(6f, 12f) : rnd(4f, 8f);
            BarFxClient.P p = BarFxClient.add(BarFxClient.SPARK, ax, ay + .3f, az, rnd(.5f, 1f), .03f, SPARK);
            p.vx = (float) Math.cos(a) * sp; p.vz = (float) Math.sin(a) * sp; p.vy = landed ? rnd(3f, 6f) : rnd(1f, 3f);
        }
        for (int i = 0; i < EMBERS; i++) {
            double r = far * Math.sqrt(BarFxClient.R.nextDouble()), a = BarFxClient.R.nextDouble() * Math.PI * 2;
            flame(ax + (float) (Math.cos(a) * r), ay + .08f, az + (float) (Math.sin(a) * r), rnd(2f, 4f), .075f * zs, EMBER,
                EMBER_END, 0, .05f, 0, 1f, Float.NaN, rnd(.3f, 1f), false);
        }
        if (t % 5 == 0) {
            BarFxClient.P sc = BarFxClient.add(BarFxClient.DECAL, ax, ay + .02f, az, 2.5f, 1.5f * Math.max(1f, far * .6f), SCORCH);
            sc.alpha = .25f;
            sc.spin = rnd(0, 6.28f);
        }
        // the units standing in it: lit by the fire, with flames licking up them
        if (t % 2 == 0) {
            int n = 0;
            for (LivingEntity le : MC.level.getEntitiesOfClass(LivingEntity.class,
                    new AABB(ax - far - 1, ay - 1, az - far - 1, ax + far + 1, ay + 3, az + far + 1))) {
                if (le == d || !le.isAlive() || n++ >= LICKS_MAX)
                    continue;
                float w = le.getBbWidth() * .5f, h = le.getBbHeight();
                flame((float) le.getX(), (float) le.getY() + h * .5f, (float) le.getZ(), .15f, Math.max(w, h * .5f) * 1.2f,
                    LIT, LIT, 0, 0, 0, 1f, Float.NaN, .3f * swell, false);
                BarFxClient.P p = flame((float) le.getX() + rnd(-w, w), (float) le.getY() + rnd(.1f, h * .8f),
                    (float) le.getZ() + rnd(-w, w), rnd(.3f, .45f), rnd(.15f, .25f) * zs, LICK, LICK_END, 0, rnd(1.2f, 2f), 0,
                    1.6f, Float.NaN, swell * .8f, false);
                p.tex = BarFxClient.R.nextInt(6);
                p.stretch = 2f;
            }
        }
    }
}
