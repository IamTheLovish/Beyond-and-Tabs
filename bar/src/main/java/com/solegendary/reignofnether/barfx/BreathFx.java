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
 * <p>Layers, per tick and per breathing dragon (the blind Fable critic: round 1 "a white laser" - additive, saturated,
 * one static gradient, nothing where it landed; round 2 "no directed stream", "flat colour", "units look shaded"):
 * <ul>
 *   <li>the JET: tongues drawn stretched along their motion (BarFxClient.tongue) that leave the real jaw bone
 *   (BoneDragonRenderer) fast and land slow (drag, launched so they still land on FireBreath.flame's point inside the
 *   cone), in three bands - a pale core near the jaws, a light-blue body and a deep-blue sheath, the last two
 *   ALPHA-BLENDED (BarFxClient P.blend) so blue stays blue over grass - and a dark fringe of smoke over its far half;</li>
 *   <li>the IMPACT: flame hugging the ground outward round the aim, licks rising, a ring puff, a flickering hot spot,
 *   embers, a dark scorch under it and floor fire left along the swept line; the units standing in it lit blue with
 *   flames licking up them.</li>
 * </ul>
 * Our own quads (BarFxClient's soft discs), no texture, no vanilla particle. Sizes grow with the RTS zoom (readability
 * R6: x clamp(zoom / 30, 1, 2.5)), counts do not: ~45 new quads a tick per dragon. Nothing is drawn in the fog of war.
 */
@OnlyIn(Dist.CLIENT)
public final class BreathFx {
    private BreathFx() { }

    private static final Minecraft MC = Minecraft.getInstance();
    /** Per tick: jet tongues, sheath tongues, core lights, fringe smoke, splash, rising licks, embers, floor flames. */
    static final int JET = 7, SHEATH = 3, CORES = 2, SMOKES = 2, SPLASH = 10, RISE = 4, EMBERS = 2, FLOOR = 2, LICKS_MAX = 6;
    /** The jet's drag (per second) and the launch factor that still lands it on its aim in FireBreath.FLAME_SECONDS:
     *  for drag c over time T a flame covers v0 (1 - e^-cT) / c, so v0 = (distance / T) * cT / (1 - e^-cT). */
    static final float DRAG = 3f;
    static final float JET_K = (float) (DRAG * FireBreath.FLAME_SECONDS / (1 - Math.exp(-DRAG * FireBreath.FLAME_SECONDS)));
    /** The jaws the renderer found are trusted this many client ticks (an undrawn dragon's are guessed instead). */
    static final long JAWS_FRESH = 3;
    // three bands and the rest (Fable round 2): each flame fades from its first colour to its second over its life
    static final int CORE = 0xF2FEFF, CORE_END = 0x8FDCFF, MID = 0x58C8FF, MID_END = 0x1E5BFF, OUTER = 0x1A4FE0,
        OUTER_END = 0x0E2A8C, FRINGE = 0x0B1E4A, SPLASH_C = 0x7FD4FF, SPLASH_END = 0x1E5BFF, RISE_C = 0xBFF3FF,
        RISE_END = 0x2A6DFF, RING_C = 0x9FE4FF, HOT = 0xDFF8FF, EMBER = 0xDFFFFF, SCORCH = 0x0A1F5C, FLOOR_C = 0x5FB8FF,
        FLOOR_END = 0x0E1A66, LICK = 0x8FE0FF, LICK_END = 0x2A6DFF, LIT = 0x66B8FF, GLOW = 0xFFFFFF;

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

        // the jet (Fable round 2: "no directed stream"): tongues leave the jaws fast and land slow - drag DRAG a second,
        // launched faster by JET_K so each still lands where FireBreath.flame aims it, on the ground inside the cone -
        // drawn stretched along their motion; three bands: a pale core, a light-blue body, a deep-blue sheath
        for (int i = 0; i < JET; i++) {
            FireBreath.flame(jx, jy, jz, ax, ay, az, tan, BarFxClient.R.nextDouble() * .5, BarFxClient.R.nextDouble(), V);
            float lead = rnd(0, .05f), size = rnd(.11f, .25f) * zs;
            BarFxClient.P p = flame(jx + (float) V[0] * lead, jy + (float) V[1] * lead, jz + (float) V[2] * lead,
                life + rnd(.05f, .2f), size, MID, MID_END, (float) V[0] * JET_K, (float) V[1] * JET_K, (float) V[2] * JET_K,
                Math.max(1.5f, Math.min(8f, far * .6f / size)), ay, swell * .7f, true);
            p.drag = DRAG;
            p.stretch = 2.5f;
            p.spin = rnd(0, 6.28f);
        }
        for (int i = 0; i < SHEATH; i++) {
            FireBreath.flame(jx, jy, jz, ax, ay, az, tan, BarFxClient.R.nextDouble(), BarFxClient.R.nextDouble(), V);
            float size = rnd(.3f, .5f) * zs;
            BarFxClient.P p = flame(jx, jy, jz, life + rnd(.2f, .35f), size, OUTER, OUTER_END, (float) V[0] * JET_K,
                (float) V[1] * JET_K, (float) V[2] * JET_K, Math.max(1.5f, Math.min(6f, far * 1.1f / size)), ay,
                swell * .3f, true);
            p.drag = DRAG;
            p.stretch = 2f;
            p.spin = rnd(0, 6.28f);
        }
        for (int i = 0; i < CORES; i++) {
            FireBreath.flame(jx, jy, jz, ax, ay, az, tan, BarFxClient.R.nextDouble() * .05, BarFxClient.R.nextDouble(), V);
            float lead = rnd(0, .25f) * life;
            BarFxClient.P p = flame(jx + (float) V[0] * lead, jy + (float) V[1] * lead, jz + (float) V[2] * lead, rnd(.15f, .22f),
                rnd(.12f, .25f) * zs, CORE, CORE_END, (float) V[0], (float) V[1], (float) V[2], 1.8f, Float.NaN, swell * .5f,
                false);
            p.stretch = 2f;
        }
        if (t % 2 == 0)
            BarFxClient.flash(jx, jy, jz, .6f * zs * swell, .1f, GLOW);
        // a dark fringe of smoke over the far half of the stream: an edge against the grass
        for (int i = 0; i < SMOKES; i++) {
            float f = rnd(.5f, 1f);
            BarFxClient.P p = BarFxClient.smoke(jx + sx * f + rnd(-.5f, .5f), jy + sy * f + .5f, jz + sz * f + rnd(-.5f, .5f),
                rnd(1f, 1.5f) * zs, rnd(1f, 1.4f), FRINGE, .15f * swell);
            p.vy = 1f;
        }
        // where it lands: flame hugging the ground outward, licks rising, a ring puff, embers, a dark scorch
        for (int i = 0; i < SPLASH; i++) {
            double a = BarFxClient.R.nextDouble() * Math.PI * 2;
            float sp = rnd(6f, 9f), r0 = rnd(0, .4f) * far;
            BarFxClient.P p = flame(ax + (float) Math.cos(a) * r0, ay + .2f, az + (float) Math.sin(a) * r0, rnd(.5f, .7f),
                rnd(.3f, .4f) * zs, SPLASH_C, SPLASH_END, (float) Math.cos(a) * sp, .15f, (float) Math.sin(a) * sp, 3f,
                Float.NaN, swell * .7f, true);
            p.stretch = 2f;
            p.spin = rnd(0, 6.28f);
        }
        for (int i = 0; i < RISE; i++) {
            double r = far * .7 * Math.sqrt(BarFxClient.R.nextDouble()), a = BarFxClient.R.nextDouble() * Math.PI * 2;
            BarFxClient.P p = flame(ax + (float) (Math.cos(a) * r), ay + .2f, az + (float) (Math.sin(a) * r), .6f, .2f * zs,
                RISE_C, RISE_END, 0, rnd(2f, 3f), 0, 2.5f, Float.NaN, swell * .7f, false);
            p.stretch = 2f;
        }
        if (t % 5 == 0)
            BarFxClient.ring(ax, ay, az, 3f * zs, .4f, RING_C);
        BarFxClient.flash(ax, ay + .2f, az, .7f * zs * swell * rnd(.85f, 1.15f), .1f, HOT);
        for (int i = 0; i < EMBERS; i++) {
            BarFxClient.P p = BarFxClient.add(BarFxClient.SPARK, ax + rnd(-.5f, .5f) * far, ay + .3f, az + rnd(-.5f, .5f) * far,
                rnd(.8f, 1.2f), rnd(.04f, .06f), EMBER);
            p.vx = rnd(-2, 2); p.vz = rnd(-2, 2); p.vy = rnd(3, 5);
        }
        if (t % 5 == 0) {
            BarFxClient.P sc = BarFxClient.add(BarFxClient.DECAL, ax, ay + .02f, az, 2.5f, 1.5f * Math.max(1f, far * .6f), SCORCH);
            sc.alpha = .25f;
            sc.spin = rnd(0, 6.28f);
        }
        for (int i = 0; i < FLOOR; i++) {
            double r = far * .8 * Math.sqrt(BarFxClient.R.nextDouble()), a = BarFxClient.R.nextDouble() * Math.PI * 2;
            flame(ax + (float) (Math.cos(a) * r), ay + .1f, az + (float) (Math.sin(a) * r), rnd(1f, 2.2f), rnd(.2f, .35f) * zs,
                FLOOR_C, FLOOR_END, rnd(-.15f, .15f), rnd(.25f, .7f), rnd(-.15f, .15f), 1.5f, Float.NaN, swell * .6f, false);
        }
        // the units standing in it: lit by the fire (a blue light over them), with flames licking up them
        if (t % 2 == 0) {
            int n = 0;
            for (LivingEntity le : MC.level.getEntitiesOfClass(LivingEntity.class,
                    new AABB(ax - far - 1, ay - 1, az - far - 1, ax + far + 1, ay + 3, az + far + 1))) {
                if (le == d || !le.isAlive() || n++ >= LICKS_MAX)
                    continue;
                float w = le.getBbWidth() * .5f, h = le.getBbHeight();
                flame((float) le.getX(), (float) le.getY() + h * .5f, (float) le.getZ(), .15f, Math.max(w, h * .5f) * 1.2f,
                    LIT, LIT, 0, 0, 0, 1f, Float.NaN, .4f * swell, false);
                BarFxClient.P p = flame((float) le.getX() + rnd(-w, w), (float) le.getY() + rnd(.1f, h * .8f),
                    (float) le.getZ() + rnd(-w, w), rnd(.3f, .45f), rnd(.15f, .25f) * zs, LICK, LICK_END, 0, rnd(1.2f, 2f), 0,
                    1.6f, Float.NaN, swell * .8f, false);
                p.stretch = 2f;
            }
        }
    }
}
