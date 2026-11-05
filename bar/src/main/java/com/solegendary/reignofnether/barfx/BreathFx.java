package com.solegendary.reignofnether.barfx;

import com.solegendary.reignofnether.unit.FireBreath;
import com.solegendary.reignofnether.unit.units.monsters.BoneDragonUnit;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
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
 * <p>Per tick, per breathing dragon: a stream of flames from the jaws (the real jaw bone, BoneDragonRenderer) that land
 * on the aim's ground inside the cone and spill over it; a bright core along the first part of the stream and a glow at
 * the jaws, so the stream never shows a gap between two ticks; and flames left lying along the line the aim sweeps,
 * which burn on for 1-2 s - the line of fire a dragon's pass leaves over an army. Our own light quads (BarFxClient's
 * additive discs), no texture, no vanilla particle. Sizes grow with the RTS zoom (readability R6: x clamp(zoom / 30,
 * 1, 2.5)), counts do not: ~10 new quads a tick per dragon, ~120 alive. Nothing is drawn in the fog of war.
 */
@OnlyIn(Dist.CLIENT)
public final class BreathFx {
    private BreathFx() { }

    private static final Minecraft MC = Minecraft.getInstance();
    /** Flames from the jaws per tick, and lingering flames laid on the ground per tick. */
    static final int STREAM = 6, LINGER = 2;
    /** The jaws the renderer found are trusted this many client ticks (an undrawn dragon's are guessed instead). */
    static final long JAWS_FRESH = 3;
    // blue soul-fire, white-hot at the heart: each flame fades from its first colour to its second (additive: dark = gone)
    static final int CORE = 0xE4F6FF, CORE_END = 0x3C6CF0, FLAME = 0x66C8FF, FLAME_END = 0x16249A, LINGER_C = 0x8AD6FF,
        LINGER_END = 0x0E1A66, GLOW = 0x7FCCFF;

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
        float dist = (float) Math.sqrt((ax - jx) * (ax - jx) + (ay - jy) * (ay - jy) + (az - jz) * (az - jz));
        // it swells in over the first ticks and gutters out over the last (the jaw opens before, shuts after)
        float swell = Math.min(1f, (t + 1) / 4f) * Math.min(1f, (FireBreath.TICKS - t) / 6f);
        float far = (float) (dist * tan * 0.7);   // a flame's size where it lands: the cone's width there
        // the stream: flames that land on the aim's ground inside the cone
        for (int i = 0; i < STREAM; i++) {
            double u = BarFxClient.R.nextDouble(), v = BarFxClient.R.nextDouble();
            boolean core = i < 2;
            FireBreath.flame(jx, jy, jz, ax, ay, az, tan, core ? u * 0.15 : u, v, V);
            // spread along the tick: born a little way down its path, so two ticks' flames leave no gap between them
            float lead = BarFxClient.rnd(0, .05f);
            float size = (core ? .22f : .3f) * zs;
            BarFxClient.P p = BarFxClient.add(BarFxClient.BREATH, jx + (float) V[0] * lead, jy + (float) V[1] * lead,
                jz + (float) V[2] * lead, (float) FireBreath.FLAME_SECONDS + BarFxClient.rnd(.15f, .4f), size,
                core ? CORE : FLAME);
            p.vx = (float) V[0]; p.vy = (float) V[1]; p.vz = (float) V[2];
            p.color2 = core ? CORE_END : FLAME_END;
            p.grow = Math.max(1.5f, Math.min(9f, far / size));   // as wide as the cone where it lands
            p.ground = ay;
            p.alpha = swell * (core ? 1f : .8f);
        }
        // the heart of the stream and the glow in the jaws: one bright core, drawn fresh each tick
        BarFxClient.beam(jx, jy, jz, jx + (ax - jx) * .45f, jy + (ay - jy) * .45f, jz + (az - jz) * .45f, .1f,
            .1f * zs * swell, CORE);
        BarFxClient.flash(jx, jy, jz, .55f * zs * swell, .12f, GLOW);
        // where it lands: a glow on the ground, and flames left lying along the line the aim sweeps
        BarFxClient.flash(ax, ay + .3f, az, (float) (dist * tan * .55f) * zs * swell, .15f, FLAME);
        for (int i = 0; i < LINGER; i++) {
            double r = dist * tan * .6 * Math.sqrt(BarFxClient.R.nextDouble()), a = BarFxClient.R.nextDouble() * Math.PI * 2;
            BarFxClient.P p = BarFxClient.add(BarFxClient.BREATH, ax + (float) (Math.cos(a) * r), ay + .1f,
                az + (float) (Math.sin(a) * r), BarFxClient.rnd(1f, 2.2f), BarFxClient.rnd(.3f, .6f) * zs, LINGER_C);
            p.vx = BarFxClient.rnd(-.15f, .15f); p.vz = BarFxClient.rnd(-.15f, .15f); p.vy = BarFxClient.rnd(.25f, .7f);
            p.color2 = LINGER_END;
            p.grow = 1.5f;
            p.ground = Float.NaN;
            p.alpha = swell * .85f;
        }
    }
}
