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
    /** Per tick: white mouth cores, hot tongues, body tongues, body puffs, fringe smoke, splash tongues, pool flames,
     *  ember motes; licks on at most LICKS_MAX units. */
    static final int CORES = 3, HOTS = 0, BODY = 4, PUFFS = 4, SMOKES = 2, SPLASH = 20, POOL = 1, EMBERS = 8,
        LICKS_MAX = 8;
    /** The jaws the renderer found are trusted this many client ticks (an undrawn dragon's are guessed instead). */
    static final long JAWS_FRESH = 3;
    /** Jaws that moved further than this in a tick jumped: no flames are filled in along that path. */
    static final double JAWS_JUMP = 3.0;
    /** Airborne: the radius (blocks) of the burning ground round the aim. */
    static final double POOL_R = 2.8;
    // soul-fire, cyan to cobalt: the white-hot core racing the whole jet (in-game blind A/B i3 picked it over a
    // core kept at the mouth in every air pair: "a readable white column to the ground"); each flame fades from its
    // first colour to its second over its life
    static final int CORE = 0xF4FFFF, CORE_END = 0xBFEFFF, HOT_C = 0x9FE0FF, HOT_END = 0x4FB4FF, BODY_C = 0x3F8DFF,
        BODY_END = 0x1A3FD6, SMOKE = 0x2A2F52, SMOKE_END = 0x1A1E36, MUZZLE = 0xFFFFFF, SPLASH_C = 0xC9F2FF,
        SPLASH_END = 0x3B8CFF, POOL_C = 0x6FB8FF, POOL_END = 0x1E4BD8, HOT = 0x7FD6FF, GLOW = 0x1E4BFF,
        EMBER = 0xC8F0FF, EMBER_END = 0x7FD0FF, SCORCH = 0x101C40, LICK = 0x8FE0FF, LICK_END = 0x2A6DFF, LIT = 0x66B8FF,
        SHEATH = 0x3F8CFF, HIT = 0xEAF6FF, CLING = 0x3D8BFF, CLING_END = 0x1E5BFF;

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
        float ax = (float) FireBreath.aim(landed, bx, off.x(), s), ay = (float) FireBreath.aim(landed, by, off.y(), s);
        float az = (float) FireBreath.aim(landed, bz, off.z(), s);
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
        // where the jaws were last tick (Fable round 6: sub-tick fill): stream flames are born along the path between,
        // so a turning dragon's stream never steps - unless the jaws JUMPED (the renderer's posed jaws taking over from
        // the guess, a teleport): filling that path strewed tongues 12-20 blocks off, over the void (in-game round 1)
        boolean fresh = Double.isNaN(d.clientJawsLast[0]) || t == 0;
        if (!fresh) {
            double jdx = jx - d.clientJawsLast[0], jdy = jy - d.clientJawsLast[1], jdz = jz - d.clientJawsLast[2];
            fresh = jdx * jdx + jdy * jdy + jdz * jdz > JAWS_JUMP * JAWS_JUMP;
        }
        float ljx = fresh ? jx : (float) d.clientJawsLast[0], ljy = fresh ? jy : (float) d.clientJawsLast[1];
        float ljz = fresh ? jz : (float) d.clientJawsLast[2];
        d.clientJawsLast[0] = jx; d.clientJawsLast[1] = jy; d.clientJawsLast[2] = jz;
        float zs = BarFxClient.ortho ? Math.max(1f, Math.min(2.5f, BarFxClient.zoom / 30f)) : 1f;
        double tan = FireBreath.tanHalf(landed);
        float sx = ax - jx, sy = ay - jy, sz = az - jz;
        float dist = (float) Math.sqrt(sx * sx + sy * sy + sz * sz);
        float far = (float) (dist * tan * 0.9);   // the cone's radius where it lands
        // the cone the server burns by (BoneDragonUnit.breathPulse): its axis and length - landed it runs the full
        // GROUND_LENGTH along the ground whatever the aim (in-game round 1: "a ball stuck at the mouth, no reach")
        float dl = Math.max(1.0e-3f, dist), cx = sx / dl, cy = sy / dl, cz = sz / dl;
        float cone = (float) FireBreath.length(landed, dist);
        float hl = (float) Math.sqrt(sx * sx + sz * sz), ux = hl > 1.0e-3f ? sx / hl : 1, uz = hl > 1.0e-3f ? sz / hl : 0;
        // it swells in over the first ticks and gutters out over the last (the jaw opens before, shuts after), and
        // flickers tick to tick (in-game round 1: "a rigid laser bar")
        float swell = Math.min(1f, (t + 1) / 4f) * Math.min(1f, (FireBreath.TICKS - t) / 6f);
        float flick = 1f;

        // the stream (in-game round 1: "white for the whole length, droplets - a water jet or frost beam"): white only
        // in a short core at the mouth, a hot pale-blue tongue a few blocks on, then a body of cobalt tongues that
        // wobble side to side and ragged puffs rolling as they go (ALPHA-BLENDED, BarFxClient P.blend, so blue stays
        // blue over grass), a deep-blue fringe drifting up off it - and a flash in the jaws
        float px = -sz, pz = sx, pl = (float) Math.sqrt(px * px + pz * pz);   // square to the stream, on the ground
        if (pl > 1.0e-3) { px /= pl; pz /= pl; }
        float wobble = 1.5f * (float) Math.sin(now() * 0.6);
        float dv = Math.max(1f, dist);
        for (int i = 0; i < CORES + HOTS; i++) {
            boolean hot = i >= CORES;
            FireBreath.flame(jx, jy, jz, ax, ay, az, tan, BarFxClient.R.nextDouble() * (hot ? .15 : .04),
                BarFxClient.R.nextDouble(), V);
            float k = rnd(22f, 28f) / (float) Math.sqrt(V[0] * V[0] + V[1] * V[1] + V[2] * V[2]);
            float lead = rnd(0, .05f), w = hot ? wobble * .4f : 0;
            BarFxClient.P p = flame(jx + (float) V[0] * k * lead, jy + (float) V[1] * k * lead, jz + (float) V[2] * k * lead,
                hot ? rnd(.18f, .26f) : rnd(.25f, .35f), (hot ? .3f : .25f) * zs, hot ? HOT_C : CORE, hot ? HOT_END : CORE_END,
                (float) V[0] * k + px * w, (float) V[1] * k, (float) V[2] * k + pz * w, hot ? 1.8f : 2.4f, ay, swell * flick,
                false);
            p.tex = BarFxClient.R.nextInt(6);
            p.stretch = 3.4f;
        }
        for (int i = 0; i < BODY + PUFFS; i++) {
            boolean puff = i >= BODY;
            float back = rnd(0, 1);
            float ox = jx + (ljx - jx) * back, oy = jy + (ljy - jy) * back, oz = jz + (ljz - jz) * back;
            float vx, vy, vz, life;
            if (landed) {
                // anywhere along the ground cone, spread evenly over its area: out to GROUND_LENGTH, as wide as it is
                float reach = cone * (float) Math.sqrt(rnd(.04f, 1f)), lat = rnd(-1f, 1f) * reach * (float) tan * .8f;
                float speed = rnd(15f, 19f), time = Math.max(.12f, reach / speed);
                vx = (jx + ux * reach - uz * lat - ox) / time;
                vz = (jz + uz * reach + ux * lat - oz) / time;
                vy = (ay - oy) / time;
                life = Math.min(1f, Math.max(.45f, time * 1.2f));
            } else {
                FireBreath.flame(jx, jy, jz, ax, ay, az, tan, BarFxClient.R.nextDouble() * .7, BarFxClient.R.nextDouble(), V);
                // 16-20 blocks a second, or what it takes to reach the ground in its life (a dragon 12 up)
                float k = Math.max(rnd(16f, 20f), dv / .5f) / (float) Math.sqrt(V[0] * V[0] + V[1] * V[1] + V[2] * V[2]);
                vx = (float) V[0] * k; vy = (float) V[1] * k; vz = (float) V[2] * k;
                life = rnd(.45f, .6f);
            }
            float lead = rnd(0, .05f), size = (puff ? .4f : .3f) * zs;
            BarFxClient.P p = flame(ox + vx * lead, oy + vy * lead, oz + vz * lead, life, size, BODY_C, BODY_END,
                vx + px * wobble, vy, vz + pz * wobble, Math.max(2.5f, Math.min(5f, 1.2f * zs / size)), ay,
                swell * .7f * flick, true);
            if (puff) {
                p.tex = 6 + BarFxClient.R.nextInt(2);
                p.spin = rnd(0, 6.28f);
                p.rate = rnd(1f, 3f) * (BarFxClient.R.nextBoolean() ? 1 : -1);   // 60-180 degrees a second
            } else {
                p.tex = BarFxClient.R.nextInt(6);
                p.stretch = 2.5f;
            }
        }
        for (int i = 0; i < SMOKES; i++) {
            float f = rnd(.3f, landed ? 1f : .7f) * (landed ? cone : dist);
            BarFxClient.P p = flame(jx + cx * f, jy + cy * f + .3f, jz + cz * f, rnd(.9f, 1.4f), .75f * zs, SMOKE, SMOKE_END,
                cx * 2f, 1f, cz * 2f, 2.3f, Float.NaN, swell * .35f, true);
            p.tex = 6 + BarFxClient.R.nextInt(2);
            p.spin = rnd(0, 6.28f);
            p.rate = rnd(-1f, 1f);
        }
        BarFxClient.flash(jx, jy, jz, .75f * zs * swell, .1f, MUZZLE);
        // a glow sheath along the fire (Fable round 7: "a pencil beam that vanishes at zoom 60"): its silhouette, out
        // over the whole ground cone when landed
        for (int i = 1; i <= 3; i++) {
            float f = i / 4f, along = (landed ? cone : dist) * f;
            flame(jx + cx * along, jy + cy * along, jz + cz * along, .1f,
                (.6f + (float) (along * tan) * .6f) * zs, SHEATH, SHEATH, 0, 0, 0, 1f, Float.NaN, .3f * swell, false);
        }
        // the impact: tongues hugging the ground outward, a pool of flames burning on ~2 s, a hot spot and a wide glow
        // that read from far, ember motes rising (in-game round 1: the old flung white sparks read as droplets), a
        // burnt track; landed, along the whole cone, not only at the aim
        for (int i = 0; i < SPLASH; i++) {
            double a = BarFxClient.R.nextDouble() * Math.PI * 2;
            float sp = rnd(6f, 10f), r0 = rnd(0, .3f) * far;
            // radial along the ground, pushed on in the breath's own direction: it splashes forward, not up
            float fx = sx / dv * 4f, fz = sz / dv * 4f;
            BarFxClient.P p = flame(ax + (float) Math.cos(a) * r0, ay + .2f, az + (float) Math.sin(a) * r0, rnd(.4f, .6f),
                .3f * zs, SPLASH_C, SPLASH_END, (float) Math.cos(a) * sp + fx, .1f, (float) Math.sin(a) * sp + fz, 2.2f,
                Float.NaN, swell * .45f, true);
            p.tex = BarFxClient.R.nextInt(6);
            p.stretch = 3f;
        }
        for (int i = 0; i < POOL + EMBERS; i++) {
            boolean ember = i >= POOL;
            // a point on the burning ground: the disc round the aim (air), anywhere in the cone (landed)
            float gx, gz;
            if (landed) {
                float reach = cone * (float) Math.sqrt(rnd(.04f, 1f)), lat = rnd(-1f, 1f) * reach * (float) tan * .8f;
                gx = jx + ux * reach - uz * lat;
                gz = jz + uz * reach + ux * lat;
            } else {
                double r = POOL_R * Math.sqrt(BarFxClient.R.nextDouble()), a = BarFxClient.R.nextDouble() * Math.PI * 2;
                gx = ax + (float) (Math.cos(a) * r);
                gz = az + (float) (Math.sin(a) * r);
            }
            BarFxClient.P p;
            if (ember) {
                double a = BarFxClient.R.nextDouble() * Math.PI * 2;
                float sp = rnd(3f, 6f), up = rnd(.5f, 1f);   // a random cone about 60 degrees off the ground
                p = BarFxClient.add(BarFxClient.SPARK, gx, ay + .3f, gz, rnd(.6f, 1f), .05f, EMBER);
                p.vx = (float) Math.cos(a) * sp * (1 - up * .5f); p.vz = (float) Math.sin(a) * sp * (1 - up * .5f); p.vy = sp * up;
            } else {
                p = flame(gx, ay + rnd(0, .6f), gz, rnd(1.5f, 2.5f), rnd(.25f, .4f) * zs, POOL_C, POOL_END, 0, rnd(.4f, .8f),
                    0, 1.4f, Float.NaN, swell * .7f, false);
                p.tex = BarFxClient.R.nextInt(6);
                p.stretch = 2f;
            }
        }
        if (t % 2 == 0) {
            float pulse = .45f + .15f * (float) Math.sin(now() * Math.PI * 2 * 3 / 20);   // 3 Hz: the hit twinkles from far
            flame(ax, ay + .3f, az, .3f, 1.5f * zs, HOT, HOT, 0, 0, 0, 1f, Float.NaN, pulse * swell, false);
            flame(ax, ay + .3f, az, .4f, 2f * zs, GLOW, GLOW, 0, 0, 0, 1f, Float.NaN, .25f * swell, false);
        }
        if (t % 5 == 0) {
            // a burnt track where the fire passed - never darker than the dragon's own shadow
            float gx = ax, gz = az;
            if (landed) {
                float reach = cone * rnd(.2f, 1f);
                gx = jx + ux * reach;
                gz = jz + uz * reach;
            }
            BarFxClient.P sc = BarFxClient.add(BarFxClient.DECAL, gx, ay + .02f, gz, 4f, 2f, SCORCH);
            sc.alpha = .3f;
            sc.spin = rnd(0, 6.28f);
        }
        // the units the fire reaches - by the server's own cone test on each body's box, so what burns is what shows:
        // lit blue, flames licking up them, and on each burn (the server's pulse ticks) a flash and three flames that
        // cling to them for 2 s (in-game round 1: "units never visibly burn")
        if (t % 2 == 0) {
            int n = 0;
            float mx = jx + cx * cone * .5f, my = jy + cy * cone * .5f, mz = jz + cz * cone * .5f;
            float rr = cone * .5f + cone * (float) tan + 2;
            for (LivingEntity le : MC.level.getEntitiesOfClass(LivingEntity.class,
                    new AABB(mx - rr, my - rr, mz - rr, mx + rr, my + rr, mz + rr))) {
                if (le == d || !le.isAlive())
                    continue;
                AABB b = le.getBoundingBox();
                if (!FireBreath.reachesBox(jx, jy, jz, cx, cy, cz, cone, tan, b.minX, b.minY, b.minZ, b.maxX, b.maxY,
                        b.maxZ) || n++ >= LICKS_MAX)
                    continue;
                float w = le.getBbWidth() * .5f, h = le.getBbHeight();
                flame((float) le.getX(), (float) le.getY() + h * .5f, (float) le.getZ(), .15f, Math.max(w, h * .5f) * 1.2f,
                    LIT, LIT, 0, 0, 0, 1f, Float.NaN, .3f * swell, false);
                if (FireBreath.pulse(t)) {   // the server burns on these ticks: the unit flashes and catches
                    flame((float) le.getX(), (float) le.getY() + h * .5f, (float) le.getZ(), .1f, 1.5f * zs, HIT, HIT, 0, 0, 0,
                        1f, Float.NaN, .6f * swell, false);
                    for (int j = 0; j < 3; j++) {
                        BarFxClient.P p = flame((float) le.getX() + rnd(-w, w), (float) le.getY() + h * rnd(.2f, .8f),
                            (float) le.getZ() + rnd(-w, w), 2f, .4f * zs, CLING, CLING_END, 0, rnd(.2f, .4f), 0, 1.3f,
                            Float.NaN, .85f, true);
                        p.tex = BarFxClient.R.nextInt(6);
                        p.stretch = 1.6f;
                    }
                }
                for (int j = 0; j < 2; j++) {
                    BarFxClient.P p = flame((float) le.getX() + rnd(-w, w), (float) le.getY() + h * rnd(.6f, 1f),
                        (float) le.getZ() + rnd(-w, w), .5f, .3f * zs, LICK, LICK_END, 0, rnd(1.2f, 2f), 0, 1.6f, Float.NaN,
                        swell * .8f, false);
                    p.tex = BarFxClient.R.nextInt(6);
                    p.stretch = 2f;
                }
            }
        }
    }
}
