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

/**
 * Which parts of a model the living army moves (design/living_army.md, section 1), one map per model instance (a
 * renderer's model is built once and draws every unit of its type). Families in round 1: every {@link HumanoidModel}
 * (the villager units, piglins, skeletons, zombies...), {@link IllagerModel}, {@link IronGolemModel},
 * {@link VillagerModel} (the witch) and {@link SpiderModel}; the keyframe-clip units ({@link KeyframeHierarchicalModel})
 * and our generated rigs ({@link SplitPasses}) get breath-and-flinch on their own hierarchy, the rigs without a second
 * breath. Anything else - {@link OwnMotion}, the Bone Dragon while its rework (PR #145) is open, a quadruped, an Alex's
 * Mobs creature - gets no map and is left exactly as it was.
 * A model class whose pass throws is switched off for the session ({@link #disable}).
 */
@OnlyIn(Dist.CLIENT)
public final class BoneMaps {

    private BoneMaps() {}

    private static final BoneMap NONE = new BoneMap(0f, false);
    private static final Map<EntityModel<?>, BoneMap> MAPS = new IdentityHashMap<>();
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
            return m;
        }
        if (model instanceof IllagerModel<?> il) {
            ModelPart root = il.root();
            BoneMap m = new BoneMap(12f, false)
                .set(Motion.BODY, child(root, "body")).set(Motion.HEAD, child(root, "head"))
                .set(Motion.ARMS, child(root, "arms"))
                .set(Motion.ARM_R, child(root, "right_arm")).set(Motion.ARM_L, child(root, "left_arm"))
                .set(Motion.LEG_R, child(root, "right_leg")).set(Motion.LEG_L, child(root, "left_leg"));
            return m;   // the illager's hat is the head's child: it follows the head by itself
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
                .set(Motion.LEG_R, child(root, "right_leg")).set(Motion.LEG_L, child(root, "left_leg"));
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
            if (body == null && head == null)
                return NONE;
            return new BoneMap(body == null ? 0f : body.y, true, true, !keyframes)
                .set(Motion.BODY, body).set(Motion.HEAD, head);
        }
        return NONE;
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
