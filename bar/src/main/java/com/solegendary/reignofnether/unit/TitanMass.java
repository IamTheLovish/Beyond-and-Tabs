package com.solegendary.reignofnether.unit;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

/**
 * A T3 titan has the right of way (claude/t3-roles.md, section 5 "pushes through small units"). Vanilla's entity push
 * moves both bodies 0.05 blocks a tick per contact, so a Mammoth walking into a line of T1 bodies was pushed back by
 * every one of them and crawled - "the enemy walked around my T3s" (lovish's Oct 21 playtest). The rule, shared by the
 * ground experimentals:
 * <ul>
 *   <li>a titan is never pushed by other bodies ({@link #isPushable}: vanilla then skips it in every other body's push
 *   pass, and Separation already lets knockback-proof bodies hold still);</li>
 *   <li>a body narrower than {@link #SMALL_BODY_RATIO} of the titan is <b>shoved aside</b> in the titan's own push pass
 *   ({@link #doPush}): sideways off the titan's heading while it walks (so the small body ends up beside it, not
 *   carried along in front of it), {@link #SHOVE} blocks a tick - friends and foes alike, an army parts for its titan;</li>
 *   <li>a body as big as the titan is pushed as vanilla pushes (two titans still shoulder each other apart).</li>
 * </ul>
 * Damage never comes from this: a shove is a step aside, the Trample is the Mammoth's own order. Server side (the push
 * pass runs on the server for mobs); the client's copy of the titan is not pushed either, which is what the player sees.
 */
public final class TitanMass {
    private TitanMass() { }

    /** A body narrower than this fraction of the titan's width is shoved aside instead of pushing back. */
    public static final double SMALL_BODY_RATIO = 0.6;
    /** Blocks a tick a small body in the way is shoved (vanilla's push is 0.05). */
    public static final double SHOVE = 0.12;
    /** A titan walking at least this fast (blocks a tick, flat) shoves sideways off its heading; slower, radially. */
    static final double WALKING_SPEED_SQR = 0.02 * 0.02;

    /** Whether {@code other} is small enough to be shoved aside by {@code titan}. */
    public static boolean isSmallBody(Mob titan, Entity other) {
        return other instanceof LivingEntity && other.getBbWidth() <= titan.getBbWidth() * SMALL_BODY_RATIO;
    }

    /** The titan's answer to {@code isPushable}: never moved by another body. */
    public static boolean isPushable() {
        return false;
    }

    /**
     * The titan's push pass over one touching body (LivingEntity.doPush): a small body is shoved aside, anything else
     * gets vanilla's push. Returns whether it shoved.
     */
    public static boolean doPush(Mob titan, Entity other) {
        if (!isSmallBody(titan, other) || other.isPassenger() || other.noPhysics || !((LivingEntity) other).isPushable()) {
            other.push(titan);
            return false;
        }
        double dx = other.getX() - titan.getX(), dz = other.getZ() - titan.getZ();
        double d2 = dx * dx + dz * dz;
        if (d2 < 1.0e-4) {
            // dead centre under the titan: pick a side by id so a pair never stays stacked
            double a = ((other.getId() * 0.6180339887) % 1.0) * Math.PI * 2;
            dx = Math.cos(a);
            dz = Math.sin(a);
            d2 = 1.0;
        }
        double d = Math.sqrt(d2);
        double px, pz;
        Vec3 v = titan.getDeltaMovement();
        if (v.x * v.x + v.z * v.z >= WALKING_SPEED_SQR) {
            // walking: shove off the heading to the side the body is on (perpendicular to the velocity), with a touch
            // of forward so a body straight ahead still clears the front
            double len = Math.sqrt(v.x * v.x + v.z * v.z);
            double hx = v.x / len, hz = v.z / len;
            double side = Math.signum(dx * -hz + dz * hx);
            if (side == 0)
                side = 1;
            px = -hz * side * 0.9 + hx * 0.3;
            pz = hx * side * 0.9 + hz * 0.3;
        } else {
            px = dx / d;
            pz = dz / d;
        }
        other.push(px * SHOVE, 0, pz * SHOVE);   // Entity.push sets hasImpulse: the velocity reaches clients
        return true;
    }
}
