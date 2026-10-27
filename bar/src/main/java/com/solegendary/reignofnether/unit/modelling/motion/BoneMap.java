package com.solegendary.reignofnether.unit.modelling.motion;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * One model family's parts for the living army's {@link Motion} bones (null where the body has none), plus the parts
 * that ride along with one of them (a hat with the head) and where the waist is - the pivot the torso bends about.
 */
@OnlyIn(Dist.CLIENT)
public final class BoneMap {

    /** Indexed by {@link Motion#BODY}..{@link Motion#ARMS} (ROOT is the pose stack, never a part). */
    final ModelPart[] parts = new ModelPart[Motion.BONES];
    /**
     * Parts vanilla copies from another inside setupAnim - before the pass - (a humanoid's hat from its head, a player
     * body's jacket, sleeves and trouser legs from its body and limbs): re-copied after the pass so they never part.
     */
    final ModelPart[] followers = new ModelPart[MAX_FOLLOWERS], leaders = new ModelPart[MAX_FOLLOWERS];
    int followerCount = 0;
    static final int MAX_FOLLOWERS = 6;
    /** The waist, model px (y down, from the model origin): the torso bends about it. */
    final float waistY;
    /** A keyframe-clip body: breath and flinch only. */
    final boolean flinchOnly;

    BoneMap(float waistY, boolean flinchOnly) {
        this.waistY = waistY;
        this.flinchOnly = flinchOnly;
    }

    BoneMap set(int bone, ModelPart part) {
        parts[bone] = part;
        return this;
    }

    BoneMap follow(ModelPart follower, ModelPart leader) {
        if (follower != null && leader != null) {
            followers[followerCount] = follower;
            leaders[followerCount++] = leader;
        }
        return this;
    }
}
