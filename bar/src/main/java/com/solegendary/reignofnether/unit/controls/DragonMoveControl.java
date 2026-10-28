package com.solegendary.reignofnether.unit.controls;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;

/**
 * The Bone Dragon's move control (#145): {@link DragonFlight}'s steering every tick in place of the ghast's random
 * nudges - a nose that turns at a capped rate, the velocity along it, a smooth speed-up, a braking ramp into the point
 * and a gentle climb - keeping {@link FlyingUnitMoveControl}'s sweep (a shut way ahead climbs over the obstacle; one
 * that cannot climb either waits). With nowhere to go it brakes along its nose to a hover instead of coasting.
 *
 * The flight owns the velocity, but a push from outside (knockback, a separation nudge) is kept and decays in the air
 * as it did before: whatever the velocity holds beyond what this control set last tick (after the 0.91 drag) is
 * carried on top of the steering.
 */
public class DragonMoveControl extends FlyingUnitMoveControl {
    /** The air drag FlyingMob.travel applies after the move. */
    private static final double DRAG = 0.91;
    /** Ticks between the sweeps ahead (each sweeps the big hitbox up to MAX_SWEEP lengths: not every tick). */
    private static final int SWEEP_EVERY = 4;
    private static final int MAX_SWEEP = 12;

    private final Mob mob;
    private final DragonFlight.State state;
    private Vec3 lastSet = Vec3.ZERO, push = Vec3.ZERO;
    private boolean blocked = false;
    private int sweepIn = 0;
    /** The Dive's ground spot while it stoops (BoneDragonUnit.startDive), and whether it is down on the ground. */
    private Vec3 diveTo = null;
    private boolean landed = false;

    public DragonMoveControl(Mob mob) {
        super(mob);
        this.mob = mob;
        this.state = new DragonFlight.State(mob.getYRot());
    }

    /** The nose's heading (MC yaw degrees): where the dragon is flying. */
    public float heading() {
        return state.heading;
    }

    /** Turns the nose while hovering (the dragon facing its target); the next flight starts from here. */
    public void setHeading(float yaw) {
        state.heading = Mth.wrapDegrees(yaw);
    }

    /**
     * Flying somewhere, or still carrying speed: the body faces along the flight. A MOVE_TO already on its point is
     * not (a held position or a persistent move leaves it set): there the dragon hovers and faces its target.
     */
    public boolean isCruising() {
        if (landed)
            return false;
        if (diveTo != null || state.speed > 0.03)
            return true;
        if (operation != Operation.MOVE_TO)
            return false;
        double dx = wantedX - mob.getX(), dz = wantedZ - mob.getZ();
        return dx * dx + dz * dz > DragonFlight.STOP * DragonFlight.STOP;
    }

    /** The forward speed and the climb (b/t), for the dive's save; restored by {@link #restoreFlight}. */
    public double speed() {
        return state.speed;
    }

    public double climb() {
        return state.vy;
    }

    /** Picks the flight up where a saved dragon left it (a reload mid-dive keeps its way on). */
    public void restoreFlight(double speed, double climb) {
        state.speed = speed;
        state.vy = climb;
    }

    /** Stoop onto this ground spot (null: stop diving) - every other order waits while it dives. */
    public void setDive(Vec3 spot) {
        diveTo = spot;
    }

    /** On the ground (the Dive's landing): no way on, whatever the move goal wants, until it is let up again. */
    public void setLanded(boolean landed) {
        this.landed = landed;
        if (landed)
            diveTo = null;
    }

    @Override
    public void tick() {
        // what moved us besides the steering since last tick (a knockback, a push) rides on top and decays
        Vec3 now = mob.getDeltaMovement();
        // (in water or lava the drag is not the air's 0.91, so what is left there is no push)
        // diving or down, nothing pushes it: the stoop is aimed and a landed dragon is rooted (#300 CI: the units'
        // separation shoved a dragon landing on a crowd 4.6 blocks off its spot)
        if (landed || diveTo != null || mob.horizontalCollision || mob.verticalCollision || mob.isInWater() || mob.isInLava())
            push = Vec3.ZERO;
        else    // last tick's push is in there too, already through the drag
            push = now.subtract(lastSet.scale(DRAG));
        if (push.lengthSqr() < 1.0e-5)
            push = Vec3.ZERO;
        else if (push.lengthSqr() > 2.25)
            push = push.normalize().scale(1.5);

        double[] v;
        if (landed) {
            v = DragonFlight.perched(state);
            if (mob.onGround())
                v[1] = 0.0;
        } else if (diveTo != null) {
            AttributeInstance ms = mob.getAttribute(Attributes.MOVEMENT_SPEED);
            double cruise = DragonFlight.cruiseSpeed(ms == null ? 0.0 : ms.getValue());
            v = DragonFlight.dive(state, diveTo.x - mob.getX(), diveTo.y - mob.getY(), diveTo.z - mob.getZ(), cruise);
        } else if (operation == Operation.MOVE_TO) {
            AttributeInstance ms = mob.getAttribute(Attributes.MOVEMENT_SPEED);
            double cruise = DragonFlight.cruiseSpeed(ms == null ? 0.0 : ms.getValue());
            double dx = wantedX - mob.getX(), dy = wantedY - mob.getY(), dz = wantedZ - mob.getZ();
            double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (dist < 1.0 && state.speed < 0.02)
                operation = Operation.WAIT;     // there (a take-off's climb has no goal to end it): hover, face targets
            if (sweepIn-- <= 0 && dist > 1.0e-4) {
                sweepIn = SWEEP_EVERY;
                double r = Math.toRadians(state.heading);
                Vec3 ahead = new Vec3(-Math.sin(r), Mth.clamp(dy / Math.max(1.0, dist), -0.5, 0.5), Math.cos(r)).normalize();
                int steps = Math.min(MAX_SWEEP, Mth.ceil(dist));
                blocked = !canReach(ahead, steps);
                if (blocked && !canReach(new Vec3(0, 1, 0), 2)) {
                    operation = Operation.WAIT;
                    blocked = false;
                }
            }
            v = operation == Operation.MOVE_TO
                ? DragonFlight.step(state, dx, dy, dz, cruise, blocked)
                : DragonFlight.brake(state);
        } else {
            v = DragonFlight.brake(state);
            blocked = false;
        }
        lastSet = new Vec3(v[0], v[1], v[2]);
        mob.setDeltaMovement(lastSet.add(push));
    }
}
