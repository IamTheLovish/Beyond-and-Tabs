package com.solegendary.reignofnether.unit.modelling.models;

import com.solegendary.reignofnether.unit.modelling.animations.Quadruped;
import com.solegendary.reignofnether.unit.modelling.models.gen.MossbackBisonModel;
import com.solegendary.reignofnether.unit.units.verdant.MossbackBisonUnit;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * The Mossback Bison in motion: our own body (MossbackBisonModel, from design/models/verdant_mossback_bison.json by
 * tools/gen_quadruped.py -> tools/dsl_to_game.py) on the shared quadruped rig ({@link Quadruped}). A heavy,
 * high-withered ox: the head carried low, a 20-degree walk that blends to a trot, a 90-tick breath, a horn toss that
 * takes 16 ticks. Everything is keyed off the entity's own state (walk distance, swing, hurt and death time).
 */
@OnlyIn(Dist.CLIENT)
public class MossbackBisonUnitModel extends MossbackBisonModel<MossbackBisonUnit> {

    private static final Quadruped.Params PARAMS = new Quadruped.Params();
    static {
        PARAMS.legPx = 22f;                     // the shoulder at 22 px over the hoof
        PARAMS.scale = MossbackBisonUnit.SCALE;
        PARAMS.walkAmp = 22f;
        PARAMS.runAmp = 9f;
        PARAMS.footDipPx = 1.0f;
        PARAMS.breathTicks = 90f;
        PARAMS.neckDrop = 22f;
        PARAMS.attackTicks = 16f;
        PARAMS.neckLook = 0.4f;
        PARAMS.grazeEvery = 320f;
        PARAMS.grazeTicks = 90f;
        PARAMS.grazeDeg = 38f;
    }

    private final Quadruped.Rig rig;

    public MossbackBisonUnitModel(ModelPart root) {
        super(root);
        rig = new Quadruped.Rig(this::bone);
    }

    @Override
    public void setupAnim(MossbackBisonUnit entity, float limbSwing, float limbSwingAmount, float ageInTicks,
                          float netHeadYaw, float headPitch) {
        restPose();
        Quadruped.animate(rig, PARAMS, entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch,
            this.attackTime);
    }
}
