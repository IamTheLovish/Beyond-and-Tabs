package com.solegendary.reignofnether.unit.modelling.animations;

import com.solegendary.reignofnether.unit.modelling.motion.AnimState;
import com.solegendary.reignofnether.unit.modelling.motion.AnimStates;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Function;

/**
 * Client only. The rig every hovering body shares (the Light Grid's drones, design/ai_faction.md): it reads the unit's
 * travel from the living army's per-tick snapshot (AnimStates: speed, acceleration, yaw rate), steps the unit's own
 * {@link HoverMotion} springs once per client tick, and puts the pose on the generated parts by name - {@code body}
 * (the hull: bob, lean, bank, a slow yaw drift), {@code head} (the turret dips toward the work and the strike),
 * {@code arm_l} / {@code arm_r} (the manipulators: a forward-pointing arm REACHES by dipping its tip, so the motion's
 * "forward swing" is a downward rotation here), {@code thrust} (the hover ring: shrinks and goes dark as the thrust
 * cuts out on death). Absent parts are skipped, so a scout drone with no arms uses the same rig.
 *
 * The death fall is not here: the hull bone is named {@code body} and the model is a HoverBody, so the living army's
 * pass drops and crashes it (DeathFall#poseHover) and keeps the wreck for the faction's finish. The living army's
 * flinch lands on the same {@code body}. Pure per-entity state in a WeakHashMap, no allocation per frame.
 */
@OnlyIn(Dist.CLIENT)
public final class Hover {

    private Hover() {}

    /** The parts a hover body's pose moves (null where the body has none). */
    public static final class Rig {
        final ModelPart body, head, armL, armR, thrust;

        public Rig(Function<String, ModelPart> bone) {
            body = bone.apply("body");
            head = bone.apply("head");
            armL = bone.apply("arm_l");
            armR = bone.apply("arm_r");
            thrust = bone.apply("thrust");
        }
    }

    private static final class State {
        final float[] s = HoverMotion.newState();
        int lastTick = Integer.MIN_VALUE;
    }

    private static final Map<LivingEntity, State> STATES = new WeakHashMap<>();
    private static final HoverMotion.Pose POSE = new HoverMotion.Pose();
    private static final float D = Mth.DEG_TO_RAD;
    /** How far the head follows the arms' reach (the turret looks at the work), and the arms' dip per degree. */
    private static final float HEAD_FOLLOW = 0.25f, ARM_DIP = 0.9f;

    /**
     * Poses {@code rig} for this frame. Call after the model's restPose(). {@code ageInTicks} is the entity's age plus
     * the partial tick; {@code working} whether it builds or gathers now.
     */
    public static void animate(Rig rig, HoverMotion.Params p, LivingEntity e, float ageInTicks, boolean working) {
        State st = STATES.computeIfAbsent(e, k -> new State());
        float partial = Mth.clamp(ageInTicks - e.tickCount, 0f, 1f);
        if (st.lastTick != e.tickCount) {
            float dt = st.lastTick == Integer.MIN_VALUE ? 1f : Mth.clamp(e.tickCount - st.lastTick, 1, 5);
            float speed = 0f, accel = 0f, yawRate = 0f;
            int b = AnimStates.enabled() ? AnimStates.base(e.getId()) : -1;
            if (b >= 0) {
                float[] slab = AnimStates.slab();
                speed = slab[b + AnimState.SPEED];
                accel = slab[b + AnimState.ACCEL];
                yawRate = slab[b + AnimState.YAW_RATE];
            }
            HoverMotion.step(p, st.s, speed, accel, yawRate, working && e.deathTime <= 0, dt);
            st.lastTick = e.tickCount;
        }
        float attack = e.getAttackAnim(partial);
        float attackP = !working && attack > 0f ? attack : -1f;
        float deathT = e.deathTime > 0 ? e.deathTime - 1 + partial : -1f;
        HoverMotion.pose(p, st.s, partial, ageInTicks, e.getId(), attackP, deathT, POSE);

        if (rig.body != null) {
            rig.body.y -= POSE.lift;                 // model y points down
            rig.body.xRot += POSE.pitch * D;         // + = nose down
            rig.body.zRot -= POSE.roll * D;          // + roll = rolled to the unit's right (MotionPass.bend's sign)
            rig.body.yRot += POSE.yaw * D;
        }
        float reach = POSE.armR - p.armRest;
        if (rig.head != null)
            rig.head.xRot += reach * HEAD_FOLLOW * D;
        if (rig.armL != null)
            rig.armL.xRot -= (POSE.armL - p.armRest) * ARM_DIP * D;
        if (rig.armR != null)
            rig.armR.xRot -= reach * ARM_DIP * D;
        if (rig.thrust != null) {
            rig.thrust.visible = POSE.thrust > 0.05f;
            float k = 0.75f + 0.25f * POSE.thrust;
            rig.thrust.xScale = k;
            rig.thrust.zScale = k;
        }
    }
}
