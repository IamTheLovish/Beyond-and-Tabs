package com.solegendary.reignofnether.unit.modelling.models;

import com.solegendary.reignofnether.unit.modelling.animations.Gait;
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
    /** The coat and the grove, each on a bone of its own so it moves a beat behind the body: the three cape rows, the
     *  chest locks, the belly tufts, the two mane rows, the hump-coat row, the dewlap, the beard, the moss lobes. */
    private final ModelPart[] coat = new ModelPart[12];
    private final ModelPart dewlap, beard, mossHump, mossRump;

    public MossbackBisonUnitModel(ModelPart root) {
        super(root);
        rig = new Quadruped.Rig(this::bone);
        for (int i = 0; i < 6; i++) {
            String base = "root_" + (i < 3 ? "l" : "r") + (i % 3);
            roots[i][0] = bone(base);
            roots[i][1] = bone(base + "_1");
            roots[i][2] = bone(base + "_2");
        }
        String[] names = {"cape_0", "cape_1", "cape_2", "cape_front", "cape_belly", "mane_0", "mane_1", "humpcoat_0",
            "cape_3", "cape_4", "cape_5", "mane_2"};
        for (int i = 0; i < names.length; i++)
            coat[i] = bone(names[i]);
        dewlap = bone("dewlap");
        beard = bone("beard");
        mossHump = bone("moss_hump");
        mossRump = bone("moss_rump");
    }

    @Override
    public void setupAnim(MossbackBisonUnit entity, float limbSwing, float limbSwingAmount, float ageInTicks,
                          float netHeadYaw, float headPitch) {
        restPose();
        Quadruped.animate(rig, PARAMS, entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch,
            this.attackTime);
        swayRoots(entity.getId(), limbSwing, limbSwingAmount, ageInTicks);
        swayCoat(entity.getId(), limbSwing, limbSwingAmount, ageInTicks);
    }

    /** The stride's phase in radians for a lagging part: the hind-left hoof's cycle, as Quadruped reads it. */
    private static float stridePhase(float limbSwing) {
        float stride = Gait.strideUnits(PARAMS.legBlocks(), PARAMS.walkAmp, Gait.WALK_DUTY);
        return limbSwing / stride * Mth.TWO_PI;
    }

    /**
     * Secondary motion (round 10): the coat moves LAST. Each cape row swings fore-and-aft a beat behind the body's
     * bob and rolls a beat behind the shoulders, the lower rows later and further; the chest locks and the dewlap
     * swing with the neck; the beard with the head; the moss lobes jiggle with the footfalls. At rest everything
     * stirs on the breath, each on its own lag ({@link Life#chain}).
     */
    private void swayCoat(int id, float limbSwing, float limbSwingAmount, float age) {
        float amt = Math.min(1f, limbSwingAmount);
        float ph = stridePhase(limbSwing);
        float idle = 1f - amt;
        for (int k = 0; k < 6; k++) {                                   // the six cape rows: each 0.25 rad later, further
            float lag = 0.5f + 0.25f * k;
            float gain = 1f + 0.3f * k;
            float swing = Mth.sin(ph * 2f - lag) * amt;                 // two beats a stride: the body's bob
            float roll = Mth.sin(ph - lag) * amt;                       // one beat: the shoulders' roll
            float breath = Life.chain(age, id, 90f, k, 9f) * idle;
            Quadruped.dsl(coat[k < 3 ? k : 5 + k], (3f * swing + 0.8f * breath) * gain, 0, (2f * roll + 0.5f * breath) * gain, 1f);
        }
        Quadruped.dsl(coat[3], 6f * Mth.sin(ph * 2f - 0.9f) * amt + 1.2f * Life.chain(age, id, 90f, 1, 9f) * idle, 0,
            2f * Mth.sin(ph - 0.9f) * amt, 1f);                                                  // the chest locks
        Quadruped.dsl(coat[4], 4f * Mth.sin(ph * 2f - 1.1f) * amt, 0, 3f * Mth.sin(ph - 1.1f) * amt, 1f);   // the belly tufts
        for (int k = 0; k < 3; k++)                                                               // the mane rows
            Quadruped.dsl(k < 2 ? coat[5 + k] : coat[11], (4f + 2f * k) * Mth.sin(ph * 2f - 0.7f - 0.4f * k) * amt
                + (1f + 0.5f * k) * Life.chain(age, id, 90f, 2 + k, 9f) * idle, 0, (2f + k) * Mth.sin(ph - 0.7f - 0.4f * k) * amt, 1f);
        Quadruped.dsl(coat[7], 2.5f * Mth.sin(ph * 2f - 0.8f) * amt + 0.8f * Life.chain(age, id, 90f, 1, 9f) * idle, 0, 0, 1f);
        Quadruped.dsl(dewlap, 7f * Mth.sin(ph * 2f - 1.2f) * amt + 1.5f * Life.chain(age, id, 90f, 2, 9f) * idle, 0,
            2.5f * Mth.sin(ph - 1.2f) * amt, 1f);
        Quadruped.dsl(beard, 8f * Mth.sin(ph * 2f - 1.4f) * amt + 2f * Life.chain(age, id, 90f, 3, 9f) * idle, 0,
            3f * Mth.sin(ph - 1.4f) * amt, 1f);
        Quadruped.dsl(mossHump, 2f * Mth.sin(ph * 2f - 1.0f) * amt, 0, 1.5f * Mth.sin(ph - 1.0f) * amt, 1f);
        Quadruped.dsl(mossRump, 2.5f * Mth.sin(ph * 2f - 1.3f) * amt, 0, 2f * Mth.sin(ph - 1.3f) * amt, 1f);
    }

    /**
     * The roots are living wood hanging in the shag: at rest they stir slowly, each on its own period; on the move
     * they swing with the stride, and every lower segment sees the motion a few ticks after the one above it
     * ({@link Life#chain}), so a root trails and whips instead of swinging as one rigid slab.
     */
    private void swayRoots(int id, float limbSwing, float limbSwingAmount, float age) {
        float amt = Math.min(1f, limbSwingAmount);
        float stride = stridePhase(limbSwing);                  // the stride's phase, in radians
        for (int i = 0; i < 6; i++) {
            float side = i < 3 ? 1f : -1f;
            float period = 64f + 11f * (i % 3) + 5f * (i / 3);   // no two roots stir in step
            for (int k = 0; k < 3; k++) {
                ModelPart p = roots[i][k];
                if (p == null)
                    continue;
                float idle = Life.chain(age, id, period, k, 7f) * (1f - 0.7f * amt);
                float walk = Mth.sin(stride - 0.5f * k + i * 0.6f) * amt;   // each segment half a radian behind the one above: the tip trails a sixth of a stride
                float gain = 1f + 0.6f * k;                                 // 6 degrees at the top, ~13 at the tip
                Quadruped.dsl(p, (2.5f * idle + 6f * walk) * gain, 0, side * (1.6f * idle + 2.5f * walk) * gain, 1f);
            }
        }
    }
}
