package com.solegendary.reignofnether.unit.modelling.models;

import com.solegendary.reignofnether.unit.modelling.animations.HumanBase;
import com.solegendary.reignofnether.unit.modelling.animations.Life;
import com.solegendary.reignofnether.unit.modelling.models.gen.ClericAnim;
import com.solegendary.reignofnether.unit.modelling.models.gen.DomeClericModel;
import com.solegendary.reignofnether.unit.units.villagers.EvokerUnit;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * The Sunforged Dome Cleric (evoker_unit) in motion, over its generated body (DomeClericModel, from
 * design/models/sunforged_cleric_v2.json by tools/dsl_to_game.py; the generator is tools/gen_cleric.py) - until Oct 21
 * the villager body (lovish 21:20: every human unit moves onto the human body).
 *
 * - walk and idle: the SHARED human base clips (HumanBase, from design/anims/cleric.json - tools/human_base.py's gait
 *   and idle with the priest's parameters: a short measured step, the book held to the chest, the free arm swinging;
 *   the under-robe's four panels and the chasuble's two skirts parting for the legs). On top: the breath, the look;
 * - the cast keeps the villager model's SPELLCASTING timing (no base cast clip exists yet): while
 *   EvokerUnit.getEvokerArmPose() says SPELLCASTING the free hand rises high, palm to the sky, and the book is lifted
 *   before him, both swaying at the old rate (cos(age x 0.6662) x 0.25); eased in and out over CAST_TICKS.
 */
@OnlyIn(Dist.CLIENT)
public class DomeClericUnitModel extends DomeClericModel<EvokerUnit> {
    private static final float CAST_TICKS = 4f;

    private final ModelPart body, head, rArm, rFore, lArm, lFore, book, chasFront, chasBack;
    private final HumanBase base;
    private final Map<EvokerUnit, float[]> state = new WeakHashMap<>();   // {cast blend, last age}

    public DomeClericUnitModel(ModelPart root) {
        super(root);
        body = bone("body");
        head = bone("head");
        rArm = bone("right_arm");
        rFore = bone("right_fore");
        lArm = bone("left_arm");
        lFore = bone("left_fore");
        book = bone("book");
        chasFront = bone("chasuble_front");
        chasBack = bone("chasuble_back");
        base = new HumanBase(ClericAnim.BONES, bones,
            new HumanBase.Clip(ClericAnim.IDLE_P, ClericAnim.IDLE_LOOP, ClericAnim.IDLE_ROT, ClericAnim.IDLE_MOVE),
            new HumanBase.Clip(ClericAnim.WALK_P, ClericAnim.WALK_LOOP, ClericAnim.WALK_ROT, ClericAnim.WALK_MOVE),
            ClericAnim.IDLE_TICKS, ClericAnim.WALK_PHASE);
    }

    private float castBlend(EvokerUnit entity, float age) {
        float[] st = state.get(entity);
        if (st == null)
            state.put(entity, st = new float[] {0f, age});
        float dt = Mth.clamp(age - st[1], 0f, 20f);
        boolean casting = entity.getEvokerArmPose() == VillagerUnitModel.ArmPose.SPELLCASTING;
        float target = casting ? 1f : 0f;
        st[0] = target > st[0] ? Math.min(target, st[0] + dt / CAST_TICKS) : Math.max(target, st[0] - dt / CAST_TICKS);
        st[1] = age;
        return HumanBase.smooth(st[0]);
    }

    @Override
    public void setupAnim(EvokerUnit entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw,
                          float headPitch) {
        restPose();
        if (body == null)
            return;
        float cast = castBlend(entity, ageInTicks);
        float breathe = Life.breath(ageInTicks, entity.getId(), 100f);   // an older man's slower breath
        float t = ageInTicks + (entity.getId() & 63);

        // the shared base: the idle's drift held off while he casts, the walk crossfaded in by the stride
        base.pose(limbSwing, limbSwingAmount, ageInTicks, entity.getId(), 1f - cast);
        body.y -= breathe * 0.07f;
        if (rArm != null) rArm.xRot += breathe * 0.02f;
        if (head != null) {
            head.yRot += Mth.clamp(netHeadYaw, -40f, 40f) * Mth.DEG_TO_RAD * 0.6f;
            head.xRot += Mth.clamp(headPitch, -25f, 25f) * Mth.DEG_TO_RAD * 0.4f;
        }
        // the heavy silk stirs a little at rest
        if (chasFront != null) chasFront.xRot += 0.015f * Mth.sin(t * 0.07f);
        if (chasBack != null) chasBack.xRot += 0.015f * Mth.sin(t * 0.07f + 1.3f);

        // the cast: the free hand raised high and forward, the book lifted before him; the old sway on both
        if (cast > 0f) {
            float sway = Mth.cos(ageInTicks * 0.6662f) * 0.25f;
            HumanBase.dsl(rArm, 150f, 0f, -18f, cast);
            HumanBase.dsl(rFore, 22f, 0f, 0f, cast);
            HumanBase.dsl(lArm, 26f, 0f, 0f, cast);
            HumanBase.dsl(lFore, 18f, 0f, 0f, cast);
            if (rArm != null) rArm.xRot += sway * 0.5f * cast;
            if (lArm != null) lArm.xRot += sway * 0.25f * cast;
            if (head != null) head.xRot -= 0.25f * cast;                  // he looks up to the light
            body.xRot -= 0.05f * cast;
        }
    }
}
