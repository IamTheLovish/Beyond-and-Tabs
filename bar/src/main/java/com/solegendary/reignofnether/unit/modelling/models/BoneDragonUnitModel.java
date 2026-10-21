package com.solegendary.reignofnether.unit.modelling.models;

import com.solegendary.reignofnether.unit.modelling.animations.Dragon;
import com.solegendary.reignofnether.unit.modelling.models.gen.BoneDragonModel;
import com.solegendary.reignofnether.unit.units.monsters.BoneDragonUnit;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * The Bone Dragon in motion: our own body (BoneDragonModel, from design/models/gravebound_bone_dragon.json by
 * tools/gen_dragon.py -> tools/dsl_to_game.py) on the shared dragon rig ({@link Dragon}). A huge dead wyrm: a slow
 * 40-tick beat with a heavy downstroke, a 120-tick breath that swells the ribcage round the soul-light, a five-link
 * neck and a seven-link tail that roll with lag, a yawn and a head shake now and then. Everything is keyed off the
 * entity's own state (speed, turn rate, swing, hurt and death time).
 */
@OnlyIn(Dist.CLIENT)
public class BoneDragonUnitModel extends BoneDragonModel<BoneDragonUnit> {

    private static final Dragon.Params PARAMS = new Dragon.Params();
    static {
        PARAMS.beatTicks = 40f;
        PARAMS.down = 0.55f;
        PARAMS.flapUp = 40f;
        PARAMS.flapDown = -32f;
        PARAMS.glideArm = 10f;
        PARAMS.breathTicks = 120f;
        PARAMS.neckLag = 2.5f;
        PARAMS.tailLag = 3f;
        PARAMS.riseP = 8f;
        PARAMS.dropPx = 5f;
        PARAMS.attackTicks = 16f;
    }

    private final Dragon.Rig rig;

    public BoneDragonUnitModel(ModelPart root) {
        super(root);
        rig = new Dragon.Rig(this::bone);
    }

    @Override
    public void setupAnim(BoneDragonUnit entity, float limbSwing, float limbSwingAmount, float ageInTicks,
                          float netHeadYaw, float headPitch) {
        restPose();
        Dragon.animate(rig, PARAMS, entity, ageInTicks, netHeadYaw, headPitch, this.attackTime);
    }
}
