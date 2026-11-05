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
 * <p>Layers, per tick and per breathing dragon (the blind Fable critic's round 1, Nov 5: "a white laser" - the additive
 * stream saturated to white, had no structure and did nothing where it landed):
 * <ul>
 *   <li>the BODY: ragged flame tongues ALPHA-BLENDED (BarFxClient P.blend), saturated blue, flying from the real jaw
 *   bone (BoneDragonRenderer) to the aim's ground inside the cone (FireBreath.flame) and growing to the cone's width,
 *   with thin dark-blue wisps round them - blue that stays blue over grass;</li>
 *   <li>the CORE: a pale hot light only in the first third of the stream, and a glow in the jaws (added light);</li>
 *   <li>EMBERS riding the stream and rising where it lands, and dark smoke wisps over its far half;</li>
 *   <li>the IMPACT: a pool of flame spilling out over the ground round the aim, a flickering hot spot, sparks, licks
 *   of fire on every unit standing in it, and floor fire left burning 2-3 s along the line the aim swept.</li>
 * </ul>
 * Our own quads (BarFxClient's soft discs), no texture, no vanilla particle. Sizes grow with the RTS zoom (readability
 * R6: x clamp(zoom / 30, 1, 2.5)), counts do not: ~40 new quads a tick per dragon. Nothing is drawn in the fog of war.
 */
@OnlyIn(Dist.CLIENT)
public final class BreathFx {
    private BreathFx() { }

    private static final Minecraft MC = Minecraft.getInstance();
    /** Per tick: body tongues, core lights, embers, smoke wisps, splash tongues, sparks, floor flames, unit licks. */
    static final int BODY = 6, CORES = 2, EMBERS = 6, SMOKES = 2, SPLASH = 6, SPARKS = 4, FLOOR = 2, LICKS_MAX = 6;
    /** The jaws the renderer found are trusted this many client ticks (an undrawn dragon's are guessed instead). */
    static final long JAWS_FRESH = 3;
    // each flame fades from its first colour to its second over its life
    static final int CORE = 0xCFF6FF, CORE_END = 0x4FD3FF, BODY_C = 0x1E90FF, BODY_END = 0x1655D8, WISP = 0x0A2E8C,
        EMBER = 0x7FE8FF, EMBER_END = 0x2A6BFF, SMOKE = 0x2B3A6E, SPLASH_C = 0x39B8FF, SPLASH_END = 0x1550C8,
        HOT = 0xDFF8FF, SPARK = 0xBFF4FF, FLOOR_C = 0x5FB8FF, FLOOR_END = 0x0E1A66, SCORCH = 0x2F7BFF,
        LICK = 0x4FD3FF, LICK_END = 0x1A40C0, GLOW = 0xA8E6FF;

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
        float life = (float) FireBreath.FLAME_SECONDS;

        // the body: blue tongues from the jaws to the ground, alpha-blended, as wide as the cone where they land
        for (int i = 0; i <= BODY; i++) {
            boolean wisp = i == BODY;
            FireBreath.flame(jx, jy, jz, ax, ay, az, tan, BarFxClient.R.nextDouble(), BarFxClient.R.nextDouble(), V);
            float lead = rnd(0, .05f), size = (wisp ? .4f : .3f) * zs;
            flame(jx + (float) V[0] * lead, jy + (float) V[1] * lead, jz + (float) V[2] * lead, life + rnd(.15f, .35f), size,
                wisp ? WISP : BODY_C, wisp ? WISP : BODY_END, (float) V[0], (float) V[1], (float) V[2],
                Math.max(1.5f, Math.min(10f, far * (wisp ? 1.3f : 1f) / size)), ay, swell * (wisp ? .15f : .55f), true)
                .spin = rnd(0, 6.28f);
        }
        // the core: pale light in the first third of the stream only
        for (int i = 0; i < CORES; i++) {
            FireBreath.flame(jx, jy, jz, ax, ay, az, tan, BarFxClient.R.nextDouble() * .05, BarFxClient.R.nextDouble(), V);
            float lead = rnd(0, .3f) * life;
            flame(jx + (float) V[0] * lead, jy + (float) V[1] * lead, jz + (float) V[2] * lead, rnd(.18f, .28f), .14f * zs,
                CORE, CORE_END, (float) V[0], (float) V[1], (float) V[2], 2.2f, Float.NaN, swell * .85f, false);
        }
        BarFxClient.beam(jx, jy, jz, jx + sx * .3f, jy + sy * .3f, jz + sz * .3f, .1f, .06f * zs * swell, CORE_END);
        BarFxClient.flash(jx, jy, jz, .45f * zs * swell, .12f, GLOW);
        // embers ride the stream and rise where it lands; dark wisps of smoke over its far half
        for (int i = 0; i < EMBERS; i++) {
            FireBreath.flame(jx, jy, jz, ax, ay, az, tan, BarFxClient.R.nextDouble(), BarFxClient.R.nextDouble(), V);
            float k = rnd(.7f, 1f);
            flame(jx, jy, jz, rnd(1f, 1.75f), rnd(.05f, .1f) * zs, EMBER, EMBER_END, (float) V[0] * k + rnd(-3, 3),
                (float) V[1] * k + rnd(-1, 1), (float) V[2] * k + rnd(-3, 3), 1f, ay, swell * .9f, false);
        }
        for (int i = 0; i < SMOKES; i++) {
            float f = rnd(.6f, 1f);
            BarFxClient.P p = BarFxClient.smoke(jx + sx * f + rnd(-.5f, .5f), jy + sy * f + .5f, jz + sz * f + rnd(-.5f, .5f),
                rnd(.75f, 1.25f) * zs, rnd(1.25f, 2f), SMOKE, .25f * swell);
            p.vy = rnd(.6f, 1f);
        }
        // where it lands: a pool of flame spilling over the ground, a flickering hot spot, sparks, floor fire
        for (int i = 0; i < SPLASH; i++) {
            double a = BarFxClient.R.nextDouble() * Math.PI * 2;
            float sp = rnd(5f, 8f), r0 = rnd(0, .5f) * far;
            flame(ax + (float) Math.cos(a) * r0, ay + .15f, az + (float) Math.sin(a) * r0, rnd(.6f, .9f), .3f * zs,
                SPLASH_C, SPLASH_END, (float) Math.cos(a) * sp, rnd(.5f, 1.2f), (float) Math.sin(a) * sp, 2.3f, Float.NaN,
                swell * .6f, true).spin = rnd(0, 6.28f);
        }
        BarFxClient.flash(ax, ay + .2f, az, .75f * zs * swell * rnd(.85f, 1.15f), .1f, HOT);
        BarFxClient.sparks(ax, ay + .2f, az, SPARKS, rnd(5f, 8f), SPARK, 0, 0);
        for (int i = 0; i < FLOOR; i++) {
            double r = far * .8 * Math.sqrt(BarFxClient.R.nextDouble()), a = BarFxClient.R.nextDouble() * Math.PI * 2;
            float fx = ax + (float) (Math.cos(a) * r), fz = az + (float) (Math.sin(a) * r);
            flame(fx, ay + .1f, fz, rnd(1f, 2.2f), rnd(.25f, .45f) * zs, FLOOR_C, FLOOR_END, rnd(-.15f, .15f), rnd(.25f, .7f),
                rnd(-.15f, .15f), 1.5f, Float.NaN, swell * .6f, false);
            if (i == 0) {
                BarFxClient.P sc = BarFxClient.add(BarFxClient.DECAL, fx, ay + .02f, fz, rnd(2f, 3f), rnd(.75f, 1.2f), SCORCH);
                sc.alpha = .5f * swell;
                sc.spin = rnd(0, 6.28f);
            }
        }
        // licks of fire on every unit standing in the pool (what the server burns, give or take its sweep)
        if (t % 2 == 0) {
            int n = 0;
            for (LivingEntity le : MC.level.getEntitiesOfClass(LivingEntity.class,
                    new AABB(ax - far - 1, ay - 1, az - far - 1, ax + far + 1, ay + 3, az + far + 1))) {
                if (le == d || !le.isAlive() || n++ >= LICKS_MAX)
                    continue;
                float w = le.getBbWidth() * .5f;
                flame((float) le.getX() + rnd(-w, w), (float) le.getY() + rnd(.1f, le.getBbHeight()), (float) le.getZ() + rnd(-w, w),
                    rnd(.4f, .6f), .2f * zs, LICK, LICK_END, 0, rnd(.9f, 1.5f), 0, 1.6f, Float.NaN, swell * .8f, false);
            }
        }
    }
}
