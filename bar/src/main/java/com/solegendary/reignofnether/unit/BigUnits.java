package com.solegendary.reignofnether.unit;

import com.solegendary.reignofnether.unit.interfaces.Unit;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * The feel rules every big body shares - the T3 titans and the T2 beasts (project doc claude/t3-roles.md, "feel
 * fixes for every big unit"; lovish's Oct 21 playtest: every T3 felt "slow and clunky, almost unplayable" and the enemy
 * "ran through" them). A body is <b>heavy</b> when it is at least {@link #HEAVY_WIDTH} wide, knockback-proof and walks
 * (the elephant, rhino, turtle and the other beasts; the Mammoth, Colossus, Walker, Leviathan and Engine) - flyers and
 * everything a block wide are not. Two rules live here, one more is a test:
 * <ul>
 *   <li><b>Right of way</b> ({@link #hasRightOfWay}, read by LivingEntityMixin.isPushable): vanilla pushes two
 *       overlapping bodies apart 0.05 a contact a tick, so a titan walking into a clump of T1 bodies (its own line as
 *       much as the enemy's) was shoved back by every one of them and crawled; a heavy body is now never pushed by
 *       another entity, only pushes. Hits still knock it about exactly as before (knockback resistance decides that,
 *       not this), and abilities that fling it by hand (Breach Charge, a Tidal Slam) still can.</li>
 *   <li><b>The plough</b> ({@link #tick}): a heavy body that is moving shoves the small bodies its front overlaps out
 *       to the side, friend or foe, so it walks through a line instead of wading through it (Separation only steps
 *       overlapping bodies apart 0.08 every four ticks - a titan outruns that). Never another heavy body, never a
 *       rider or a mount, never while it stands still. One grid query every {@link #PLOUGH_INTERVAL} ticks per moving
 *       heavy body, nothing for the rest of the army.</li>
 *   <li><b>Marches with the army</b>: no ground T3 slower than its faction's line, except a summoner or artillery -
 *       checked by BigUnitFeelGameTests against the real attributes.</li>
 * </ul>
 * Server thread only.
 */
public final class BigUnits {
    private BigUnits() { }

    /** A body this wide (and knockback-proof) is heavy: it has right of way and ploughs. */
    public static final float HEAVY_WIDTH = 2.0f;
    static final int PLOUGH_INTERVAL = 2;
    /** Horizontal blocks a tick under which a heavy body counts as standing (a walking titan moves 0.06-0.2). */
    static final double PLOUGH_MIN_SPEED = 0.04;
    /** The sideways impulse a ploughed body gets (blocks a tick; friction bleeds it off over a few ticks). */
    public static final double PLOUGH_PUSH = 0.35;
    static final double PLOUGH_LIFT = 0.1;
    /** How far past its own half-width a heavy body's front reaches when it ploughs. */
    static final double PLOUGH_REACH = 0.5;
    /** The step a standing heavy body takes off another heavy body it overlaps (vanilla's push was 0.05 a contact). */
    static final double UNSTACK_STEP = 0.05;

    private static final List<LivingEntity> SCRATCH = new ArrayList<>();

    /** The widest ground bodies: a unit at least {@link #HEAVY_WIDTH} wide that cannot be knocked back and does not fly. */
    public static boolean isHeavy(LivingEntity le) {
        if (!(le instanceof Unit u) || le.getBbWidth() < HEAVY_WIDTH || u.isFlyingUnit())
            return false;
        return le.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE) >= 1.0;
    }

    /** Whether entity pushes (two bodies overlapping) leave this body where it is: a live heavy body on its own feet. */
    public static boolean hasRightOfWay(LivingEntity le) {
        return le.isAlive() && !le.isPassenger() && isHeavy(le);
    }

    /** Server side, every tick from Unit.tick: the plough, on the heavy body's own staggered tick. */
    public static void tick(Mob mob) {
        if ((mob.tickCount + mob.getId()) % PLOUGH_INTERVAL != 0 || !(mob.level() instanceof ServerLevel level))
            return;
        if (mob.isPassenger() || mob.noPhysics || !mob.onGround() || !hasRightOfWay(mob))
            return;   // (a heavy body carrying riders still ploughs: the Mammoth's crew rides a battering ram)
        Vec3 v = mob.getDeltaMovement();
        double speed = Math.sqrt(v.x * v.x + v.z * v.z);
        if (speed < PLOUGH_MIN_SPEED) {
            unstack(level, mob);
            return;
        }
        plough(level, mob, v.x / speed, v.z / speed);
    }

    /**
     * Two heavy bodies standing on top of each other (a lab that trained two titans onto the same apron) used to be
     * eased apart by vanilla's push; with right of way nobody pushes them, so a standing heavy body steps off any
     * heavy body it overlaps itself, at vanilla's old pace. Moving bodies pass through each other and are left alone.
     */
    static void unstack(ServerLevel level, Mob mob) {
        double w = mob.getBbWidth(), x = mob.getX(), z = mob.getZ(), y = mob.getY();
        double px = 0, pz = 0;
        for (LivingEntity le : UnitGrid.near(level, x, z, w, SCRATCH)) {
            if (le == mob || !le.isAlive() || le.isPassenger() || Math.abs(le.getY() - y) > 1.5 || !isHeavy(le))
                continue;
            double min = (w + le.getBbWidth()) / 2;
            double dx = x - le.getX(), dz = z - le.getZ(), d2 = dx * dx + dz * dz;
            if (d2 >= min * min)
                continue;
            double d = Math.sqrt(d2);
            if (d < 1.0e-3) {   // exactly stacked: a direction split by id so the pair moves apart
                double a = ((mob.getId() * 0.6180339887) % 1.0) * Math.PI * 2;
                dx = Math.cos(a);
                dz = Math.sin(a);
                d = 1.0;
            }
            px += dx / d;
            pz += dz / d;
        }
        SCRATCH.clear();
        double len = Math.sqrt(px * px + pz * pz);
        if (len < 1.0e-4)
            return;
        mob.setDeltaMovement(mob.getDeltaMovement().add(px / len * UNSTACK_STEP, 0, pz / len * UNSTACK_STEP));
        mob.hasImpulse = true;
    }

    /**
     * Shoves every small body whose own body overlaps the front half of {@code mob}'s to the side it stands on, with a
     * little forward carry and lift. Returns how many it shoved. Public for the game test.
     */
    public static int plough(ServerLevel level, Mob mob, double headX, double headZ) {
        double w = mob.getBbWidth(), half = w / 2;
        double rightX = -headZ, rightZ = headX;
        double x = mob.getX(), z = mob.getZ(), y = mob.getY();
        int shoved = 0;
        for (LivingEntity le : UnitGrid.near(level, x, z, half + PLOUGH_REACH + 1.0, SCRATCH)) {
            if (le == mob || !le.isAlive() || !(le instanceof Unit) || le.isPassenger() || le.isVehicle() || le.noPhysics
                    || Math.abs(le.getY() - y) > 2.0 || isHeavy(le))
                continue;
            double dx = le.getX() - x, dz = le.getZ() - z;
            double along = dx * headX + dz * headZ, across = dx * rightX + dz * rightZ;
            double lw = le.getBbWidth() / 2;
            if (along < -lw || along > half + lw + PLOUGH_REACH || Math.abs(across) > half + lw)
                continue;   // behind the centre line, out ahead, or beside the body: not in its way
            double side = across >= 0 ? 1 : -1;
            if (across == 0)
                side = (le.getId() & 1) == 0 ? 1 : -1;
            double px = rightX * side * PLOUGH_PUSH + headX * PLOUGH_PUSH * 0.3;
            double pz = rightZ * side * PLOUGH_PUSH + headZ * PLOUGH_PUSH * 0.3;
            le.push(px, PLOUGH_LIFT, pz);
            le.hurtMarked = true;
            shoved++;
        }
        SCRATCH.clear();
        return shoved;
    }
}
