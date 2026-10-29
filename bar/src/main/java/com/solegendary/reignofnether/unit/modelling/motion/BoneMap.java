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
    /**
     * Weapons held on a grip beside the hand rather than in it (the grip also carries the arm): in the death they slip
     * back to their planted rest along the body ({@link MotionPass#placeFall}). One in the hand rides the arm.
     */
    final ModelPart[] dropped = new ModelPart[MAX_DROPPED];
    int droppedCount = 0;
    static final int MAX_DROPPED = 2;
    /** The waist, model px (y down, from the model origin): the torso bends about it. */
    final float waistY;
    /** A keyframe-clip body or a custom rig: breath and flinch only. */
    final boolean flinchOnly;
    /** The head and arms are the body's children (they follow its bend by themselves): bend the body about its pivot. */
    final boolean hierarchical;
    /** The rig breathes by itself: no second breath on top. */
    final boolean noBreath;
    /**
     * The death-pose contract ({@link DeathFall}): the body falls by itself instead of vanilla's flip, posing these
     * parts (indexed by DeathFall's bones: Motion's eight, then the forearms and shins); null = vanilla's flip.
     */
    ModelPart[] death;
    /** The death's torso bends about its own pivot, the head and arms following as its children. */
    boolean deathHierarchical;
    /** The death parts include elbows and knees (a jointed rig). */
    boolean jointed;

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

    /** The death falls on the same parts as the living army (a vanilla body: no elbows, no knees). */
    BoneMap fallsAsIs() {
        death = new ModelPart[DeathFall.BONES];
        System.arraycopy(parts, 0, death, 0, Motion.BONES);
        deathHierarchical = hierarchical;
        jointed = false;
        return this;
    }

    /** The death falls on a jointed rig's own parts (its chest as the torso, its forearms and shins). */
    BoneMap fallsJointed(ModelPart[] parts) {
        death = parts;
        deathHierarchical = true;
        jointed = true;
        return this;
    }

    /** True when the body falls by {@link DeathFall} rather than vanilla's flip. */
    public boolean deathPose() {
        return death != null;
    }

    BoneMap drops(ModelPart weapon) {
        if (weapon != null && droppedCount < MAX_DROPPED)
            dropped[droppedCount++] = weapon;
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
