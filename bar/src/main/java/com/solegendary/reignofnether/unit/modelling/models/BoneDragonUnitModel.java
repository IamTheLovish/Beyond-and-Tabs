package com.solegendary.reignofnether.unit.modelling.models;

import com.solegendary.reignofnether.unit.modelling.animations.Dragon;
import com.solegendary.reignofnether.unit.modelling.models.gen.BoneDragonModel;
import com.solegendary.reignofnether.unit.units.monsters.BoneDragonUnit;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * The Bone Dragon in motion: our own body (BoneDragonModel, from design/models/gravebound_bone_dragon.json by
 * tools/gen_dragon.py -> tools/dsl_to_game.py) on the shared dragon rig ({@link Dragon}). A huge dead wyrm (r13): a
 * slow 52-tick beat with a heavy downstroke and a slow folding recovery, flap bursts between glides, three-strip sails
 * that billow, an eight-link neck that holds an S and a ten-link tail that steers, hover / flare / take-off states, a
 * banking roll into every turn. Everything is keyed off the entity's own state (speed, turn rate, climb, swing, hurt
 * and death time, age).
 */
@OnlyIn(Dist.CLIENT)
public class BoneDragonUnitModel extends BoneDragonModel<BoneDragonUnit> {

    /** r13: the rig's defaults ARE the Bone Dragon's (design/anims/bone_dragon_flight.md; tools/dragon_poses.py
     *  draws the same numbers) - a 52-tick beat with a 34 % power stroke, 3-beat bursts between 2-beat glides. */
    private static final Dragon.Params PARAMS = new Dragon.Params();

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
