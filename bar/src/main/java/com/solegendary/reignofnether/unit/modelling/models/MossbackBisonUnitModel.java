package com.solegendary.reignofnether.unit.modelling.models;

import com.solegendary.reignofnether.unit.modelling.animations.Life;
import com.solegendary.reignofnether.unit.modelling.animations.Quadruped;
import com.solegendary.reignofnether.unit.modelling.models.gen.MossbackBisonModel;
import com.solegendary.reignofnether.unit.units.verdant.MossbackBisonUnit;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
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
        PARAMS.walkAmp = 28f;
        PARAMS.runAmp = 9f;
        PARAMS.footDipPx = 0.8f;                // each footfall sinks the body; with the compass bob ~0.1 block at a front hoof
        PARAMS.breathTicks = 90f;
        PARAMS.neckDrop = 22f;
        PARAMS.attackTicks = 16f;
        PARAMS.neckLook = 0.4f;
        PARAMS.grazeEvery = 320f;
        PARAMS.grazeTicks = 90f;
        PARAMS.grazeDeg = 32f;
    }

    private final Quadruped.Rig rig;
    /** The six roots hanging off the hump and the hips, each a chain of up to three bones (root_l0 > root_l0_1 > ...). */
    private final ModelPart[][] roots = new ModelPart[6][3];

    public MossbackBisonUnitModel(ModelPart root) {
        super(root);
        rig = new Quadruped.Rig(this::bone);
        for (int i = 0; i < 6; i++) {
            String base = "root_" + (i < 3 ? "l" : "r") + (i % 3);
            roots[i][0] = bone(base);
            roots[i][1] = bone(base + "_1");
            roots[i][2] = bone(base + "_2");
        }
    }

    @Override
    public void setupAnim(MossbackBisonUnit entity, float limbSwing, float limbSwingAmount, float ageInTicks,
                          float netHeadYaw, float headPitch) {
        restPose();
        Quadruped.animate(rig, PARAMS, entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch,
            this.attackTime);
        swayRoots(entity.getId(), limbSwing, limbSwingAmount, ageInTicks);
    }

    /**
     * The roots are living wood hanging in the shag: at rest they stir slowly, each on its own period; on the move
     * they swing with the stride, and every lower segment sees the motion a few ticks after the one above it
     * ({@link Life#chain}), so a root trails and whips instead of swinging as one rigid slab.
     */
    private void swayRoots(int id, float limbSwing, float limbSwingAmount, float age) {
        float amt = Math.min(1f, limbSwingAmount);
        float stride = limbSwing * 0.55f;                       // the body's sway period, in radians
        for (int i = 0; i < 6; i++) {
            float side = i < 3 ? 1f : -1f;
            float period = 64f + 11f * (i % 3) + 5f * (i / 3);   // no two roots stir in step
            for (int k = 0; k < 3; k++) {
                ModelPart p = roots[i][k];
                if (p == null)
                    continue;
                float lag = 4f * k;
                float idle = Life.chain(age, id, period, k, 7f) * (1f - 0.7f * amt);
                float walk = Mth.sin(stride - lag * 0.12f + i * 0.6f) * amt;
                float gain = 1f + 0.9f * k;                       // the tip travels furthest: ~22 degrees at a full walk
                Quadruped.dsl(p, (2.5f * idle + 8f * walk) * gain, 0, side * (1.6f * idle + 3f * walk) * gain, 1f);
            }
        }
    }
}
