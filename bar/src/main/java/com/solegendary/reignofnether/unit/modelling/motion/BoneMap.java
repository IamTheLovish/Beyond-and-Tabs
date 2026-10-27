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
    /** Rides with the head (vanilla copies the hat from the head inside setupAnim, before the pass). */
    ModelPart hat;
    /** The waist, model px (y down, from the model origin): the torso bends about it. */
    final float waistY;
    /** A keyframe-clip body or a custom rig: breath and flinch only. */
    final boolean flinchOnly;
    /** The head and arms are the body's children (they follow its bend by themselves): bend the body about its pivot. */
    final boolean hierarchical;
    /** The rig breathes by itself: no second breath on top. */
    final boolean noBreath;

    BoneMap(float waistY, boolean flinchOnly) {
        this(waistY, flinchOnly, false, false);
    }

    BoneMap(float waistY, boolean flinchOnly, boolean hierarchical, boolean noBreath) {
        this.waistY = waistY;
        this.flinchOnly = flinchOnly;
        this.hierarchical = hierarchical;
        this.noBreath = noBreath;
    }

    BoneMap set(int bone, ModelPart part) {
        parts[bone] = part;
        return this;
    }
}
