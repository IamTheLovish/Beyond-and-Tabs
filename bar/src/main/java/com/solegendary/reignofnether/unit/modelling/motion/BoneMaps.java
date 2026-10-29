package com.solegendary.reignofnether.unit.modelling.motion;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.unit.modelling.models.VillagerUnitModel;
import com.solegendary.reignofnether.unit.modelling.models.KeyframeHierarchicalModel;
import com.solegendary.reignofnether.unit.modelling.models.SplitPasses;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.IllagerModel;
import net.minecraft.client.model.IronGolemModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.SpiderModel;
import net.minecraft.client.model.VillagerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.WeakHashMap;

/**
 * Which parts of a model the living army moves (design/living_army.md, section 1), one map per model instance (a
 * renderer's model is built once and draws every unit of its type). Families in round 1: every {@link HumanoidModel}
 * (the villager units, piglins, skeletons, zombies...), {@link IllagerModel}, {@link IronGolemModel},
 * {@link VillagerModel} (the witch) and {@link SpiderModel}; the keyframe-clip units ({@link KeyframeHierarchicalModel})
 * and our generated rigs ({@link SplitPasses}) get breath-and-flinch on their own hierarchy, the rigs without a second
 * breath. Anything else - {@link OwnMotion}, the Bone Dragon while its rework (PR #145) is open, a quadruped, an Alex's
 * Mobs creature - gets no map and is left exactly as it was.
 * The humans fall by {@link DeathFall} instead of vanilla's flip (the death-pose contract, humans first): the
 * humanoid, illager and villager families on their living-army parts, and the generated human rigs on their own
 * jointed parts (chest, arms, forearms, thighs, shins), found by name.
 * A model class whose pass throws is switched off for the session ({@link #disable}).
 */
@OnlyIn(Dist.CLIENT)
public final class BoneMaps {

    private BoneMaps() {}

    private static final BoneMap NONE = new BoneMap(0f, false);
    // weak keys: a resource reload builds every renderer's model again, and the old ones must be free to go
    // (models keep Object's identity equals, so this is still one map per model object)
    private static final Map<EntityModel<?>, BoneMap> MAPS = new WeakHashMap<>();
    private static final Map<Class<?>, Boolean> DISABLED = new IdentityHashMap<>();

    /** This model's map, or null when the living army leaves it alone. */
    public static BoneMap forModel(EntityModel<?> model) {
        if (DISABLED.containsKey(model.getClass()))
            return null;
        BoneMap m = MAPS.get(model);
        if (m == null) {
            m = build(model);
            MAPS.put(model, m);
        }
        return m == NONE ? null : m;
    }

    /** Switches the living army off for this model class for the rest of the session, saying why once. */
    public static void disable(Class<?> cls, Throwable why) {
        if (DISABLED.put(cls, Boolean.TRUE) == null)
            ReignOfNether.LOGGER.warn("[Motion] disabled for {}: {}", cls.getName(), String.valueOf(why));
    }

    private static BoneMap build(EntityModel<?> model) {
        if (model instanceof OwnMotion)
            return NONE;
        if (model instanceof HumanoidModel<?> h) {
            BoneMap m = new BoneMap(12f, false)
                .set(Motion.BODY, h.body).set(Motion.HEAD, h.head)
                .set(Motion.ARM_R, h.rightArm).set(Motion.ARM_L, h.leftArm)
                .set(Motion.LEG_R, h.rightLeg).set(Motion.LEG_L, h.leftLeg);
            m.follow(h.hat, h.head);
            if (model instanceof PlayerModel<?> pl)
                m.follow(pl.jacket, pl.body).follow(pl.rightSleeve, pl.rightArm).follow(pl.leftSleeve, pl.leftArm)
                    .follow(pl.rightPants, pl.rightLeg).follow(pl.leftPants, pl.leftLeg);
            if (model instanceof VillagerUnitModel<?> v)
                m.set(Motion.ARMS, v.crossedArms);
            return m.fallsAsIs();
        }
        if (model instanceof IllagerModel<?> il) {
            ModelPart root = il.root();
            BoneMap m = new BoneMap(12f, false)
                .set(Motion.BODY, child(root, "body")).set(Motion.HEAD, child(root, "head"))
                .set(Motion.ARMS, child(root, "arms"))
                .set(Motion.ARM_R, child(root, "right_arm")).set(Motion.ARM_L, child(root, "left_arm"))
                .set(Motion.LEG_R, child(root, "right_leg")).set(Motion.LEG_L, child(root, "left_leg"));
            return m.fallsAsIs();   // the illager's hat is the head's child: it follows the head by itself
        }
        if (model instanceof IronGolemModel<?> g) {
            ModelPart root = g.root();
            return new BoneMap(3f, false)
                .set(Motion.BODY, child(root, "body")).set(Motion.HEAD, child(root, "head"))
                .set(Motion.ARM_R, child(root, "right_arm")).set(Motion.ARM_L, child(root, "left_arm"))
                .set(Motion.LEG_R, child(root, "right_leg")).set(Motion.LEG_L, child(root, "left_leg"));
        }
        if (model instanceof VillagerModel<?> v) {
            ModelPart root = v.root();
            return new BoneMap(12f, false)
                .set(Motion.BODY, child(root, "body")).set(Motion.HEAD, child(root, "head"))
                .set(Motion.ARMS, child(root, "arms"))
                .set(Motion.LEG_R, child(root, "right_leg")).set(Motion.LEG_L, child(root, "left_leg"))
                .fallsAsIs();
        }
        if (model instanceof SpiderModel<?> sp) {
            ModelPart root = sp.root();
            return new BoneMap(15f, false)
                .set(Motion.BODY, child(root, "body1")).set(Motion.HEAD, child(root, "head"));
        }
        if (model.getClass().getSimpleName().startsWith("BoneDragon"))
            return NONE;   // PR #145 (the dragon's rework) owns its motion for now
        boolean keyframes = model instanceof KeyframeHierarchicalModel<?>;
        if ((keyframes || model instanceof SplitPasses) && model instanceof HierarchicalModel<?> hm) {
            ModelPart body = descendant(hm, "body"), head = descendant(hm, "head");
            if (body != null && !bendsAlone(body, head))
                body = null;      // bending it would part the head from the torso, or swing the legs and feet
            if (body == null && head == null)
                return NONE;
            BoneMap m = new BoneMap(body == null ? 0f : body.y, true, true, !keyframes)
                .set(Motion.BODY, body).set(Motion.HEAD, head);
            ModelPart[] fall = keyframes ? null : humanRig(hm, head);
            if (fall == null)
                return m;
            m.fallsJointed(fall);
            for (String weapon : GRIP_WEAPONS)
                m.drops(gripWeapon(hm, weapon));
            return m;
        }
        return NONE;
    }

    /**
     * A generated human rig's parts for its death ({@link DeathFall}'s bones), or null when the rig is not a human
     * (a quadruped, a dragon) or lacks one of them. The torso is the part that carries the head and arms but not the
     * legs: the chest where the body is a pelvis (the Halberdier), else the body itself (the Levy, the Squire); the
     * thighs are the legs, the forearms and shins the elbows and knees (tools/human_kit.py's names).
     */
    static ModelPart[] humanRig(HierarchicalModel<?> hm, ModelPart head) {
        if (head == null)
            return null;
        ModelPart torso = descendant(hm, "chest");
        if (torso == null || !bendsAlone(torso, head))
            torso = descendant(hm, "body");
        if (torso == null || !bendsAlone(torso, head))
            return null;
        ModelPart[] p = new ModelPart[DeathFall.BONES];
        p[Motion.BODY] = torso;
        p[Motion.HEAD] = head;
        p[Motion.ARM_R] = descendant(hm, "right_arm");
        p[Motion.ARM_L] = descendant(hm, "left_arm");
        p[Motion.LEG_R] = descendant(hm, "right_thigh");
        p[Motion.LEG_L] = descendant(hm, "left_thigh");
        p[DeathFall.FORE_R] = descendant(hm, "right_fore");
        p[DeathFall.FORE_L] = descendant(hm, "left_fore");
        p[DeathFall.KNEE_R] = descendant(hm, "right_shin");
        p[DeathFall.KNEE_L] = descendant(hm, "left_shin");
        for (int bone : new int[] {Motion.ARM_R, Motion.ARM_L, Motion.LEG_R, Motion.LEG_L})
            if (p[bone] == null)
                return null;
        return p;
    }

    /**
     * The weapons a generated rig plants upright at rest (tools/gen_halberdier.py, the Field Medic's staff): laid back
     * to that rest, they lie along a fallen body. The Levy's crossbow rests held forwards, so it is not one.
     */
    private static final String[] GRIP_WEAPONS = {"halberd", "staff"};

    /** A weapon on a grip bone beside the hand (the grip also carries an arm), else null: one in a hand rides it. */
    static ModelPart gripWeapon(HierarchicalModel<?> hm, String name) {
        return hm.root().getAllParts()
            .filter(p -> p.hasChild(name) && (p.hasChild("right_arm") || p.hasChild("left_arm")))
            .findFirst().map(p -> p.getChild(name)).orElse(null);
    }

    /** Every leg, thigh and foot name our rigs and keyframe models use (bone names are not readable from a part). */
    private static final String[] LEG_NAMES = {"left_leg", "right_leg", "left_thigh", "right_thigh", "left_foot",
        "right_foot", "legL", "legR", "Lleg", "Rleg", "LLeg", "RLeg", "front_left_leg", "front_right_leg",
        "back_left_leg", "back_right_leg", "front_left_foot", "front_right_foot", "hind_left_foot", "hind_right_foot"};

    /**
     * A hierarchical rig's torso may bend by itself (the head and arms following as its children) only when the head
     * is in its subtree and no leg is - a torso beside its head would leave it behind, a pelvis would swing the feet.
     */
    static boolean bendsAlone(ModelPart body, ModelPart head) {
        if (head == null || body.getAllParts().noneMatch(p -> p == head))
            return false;
        return body.getAllParts().noneMatch(p -> {
            for (String leg : LEG_NAMES)
                if (p.hasChild(leg))
                    return true;
            return false;
        });
    }

    private static ModelPart descendant(HierarchicalModel<?> m, String name) {
        return m.root().getAllParts().filter(p -> p.hasChild(name)).findFirst().map(p -> p.getChild(name))
            .orElse(null);
    }

    private static ModelPart child(ModelPart p, String name) {
        try {
            return p.getChild(name);
        } catch (NoSuchElementException e) {
            return null;
        }
    }
}
