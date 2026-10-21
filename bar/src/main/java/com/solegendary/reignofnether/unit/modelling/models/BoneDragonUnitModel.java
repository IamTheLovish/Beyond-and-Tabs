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
        PARAMS.beatTicks = 36f;
        PARAMS.down = 0.64f;
        PARAMS.flapUp = 32f;
        PARAMS.flapDown = -38f;
        PARAMS.glideArm = 7f;
        PARAMS.breathTicks = 90f;
        PARAMS.neckLag = 2.5f;
        PARAMS.tailLag = 3f;
        PARAMS.riseP = 9f;
        PARAMS.dropPx = 6f;
        PARAMS.pitchUp = 10f;
        PARAMS.pitchDown = 6f;
        PARAMS.attackTicks = 22f;
        PARAMS.attackWindup = 6f;
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
        // the dragon's attack is the ghast's fireball (performUnitRangedAttack): it never swings an arm, so vanilla's
        // attackTime stays 0 and the bite clip never ran in game (the r8 eyes bite still: "no jaw opening visible").
        // The shooting face the client is told to show (GhastUnit.isShooting, 14 ticks from the shot) is the swing
        float swing = this.attackTime > 0f ? this.attackTime : (entity.isShooting() ? 0.5f : 0f);
        // r10: the shooting face arrives WITH the fireball, so a clip started from it has no wind-up and the sprite
        // covers the head (the r9 eyes tour: no open jaw in any frame). The dragon's shots come on its attack
        // cooldown (UnitBowAttackGoal: the cooldown dominates the 0-9 tick draw, so once it is shooting the next shot
        // is (int) cooldown ticks after the last - 66 at 0.3/s); the rig foresees the next one and rears back, jaw
        // opening, PARAMS.attackWindup ticks before it
        float shotPeriod = (float) Math.floor(entity.getAttackCooldown());
        Dragon.animate(rig, PARAMS, entity, ageInTicks, netHeadYaw, headPitch, swing, shotPeriod);
    }
}
