package com.solegendary.reignofnether.unit.controls;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Vanilla's ghast move control for our flyers (Sky Corsair, dragons, gliders...): every few ticks it nudges the
 * velocity toward the wanted point. Vanilla sweeps the body along that line first ({@code Ghast.GhastMoveControl
 * .canReach}) and refuses to accelerate into blocks; the port had dropped that sweep, so a flyer idling or drifting
 * next to a mast or a wall would push straight into it and sit "in a wall" (the production_buildings_place_every_unit
 * flake: a Sky Corsair inside its drydock's superstructure 40 ticks after spawning). The sweep is back, with one
 * addition an airship deserves: a blocked line is tried again straight up first, so the unit climbs over the
 * obstacle instead of waiting under it.
 */
public class FlyingUnitMoveControl extends MoveControl {
    private final Mob mob;
    private int floatDuration;

    // 0.05 == 0.25 for movement speed attribute
    private static final float MOVE_SPEED_RATIO = 5;
    /** How many body-lengths ahead the line is swept (vanilla sweeps the whole distance; past this the goal re-plans). */
    private static final int MAX_SWEEP = 12;

    public FlyingUnitMoveControl(Mob mob) {
        super(mob);
        this.mob = (Mob) mob;
    }

    public void tick() {
        if (this.operation == Operation.MOVE_TO) {
            if (this.floatDuration-- <= 0) {
                this.floatDuration += this.mob.getRandom().nextInt(5) + 2;
                Vec3 toWanted = new Vec3(this.wantedX - this.mob.getX(), this.wantedY - this.mob.getY(), this.wantedZ - this.mob.getZ());
                double dist = toWanted.length();
                if (dist < 1.0E-4)
                    return;
                Vec3 dir = toWanted.normalize();
                AttributeInstance ms = this.mob.getAttribute(Attributes.MOVEMENT_SPEED);
                if (ms == null)
                    return;
                double speed = ms.getValue() / MOVE_SPEED_RATIO;
                int steps = Math.min(MAX_SWEEP, Mth.ceil(dist));
                if (canReach(dir, steps)) {
                    this.mob.setDeltaMovement(this.mob.getDeltaMovement().add(dir.scale(speed)));
                } else if (canReach(new Vec3(0, 1, 0), 2)) {
                    // the straight line is blocked: climb, the next nudge tries the line again from higher up
                    this.mob.setDeltaMovement(this.mob.getDeltaMovement().add(0, speed, 0));
                } else {
                    this.operation = Operation.WAIT;
                }
            }
        }
    }

    /** True when the body can move {@code steps} whole body-steps along {@code dir} without touching a block. */
    private boolean canReach(Vec3 dir, int steps) {
        AABB box = this.mob.getBoundingBox();
        for (int i = 1; i < Math.max(2, steps); ++i) {
            box = box.move(dir);
            if (!this.mob.level().noCollision(this.mob, box))
                return false;
        }
        return true;
    }
}
