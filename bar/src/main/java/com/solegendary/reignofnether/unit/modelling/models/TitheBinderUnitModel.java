package com.solegendary.reignofnether.unit.modelling.models;

import com.solegendary.reignofnether.unit.modelling.models.gen.TitheBinderModel;
import com.solegendary.reignofnether.unit.units.monsters.TitheBinderUnit;
import com.solegendary.reignofnether.unit.units.monsters.TitheLedger;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * The Tithe Binder (tithe_binder_unit) over its generated body (TitheBinderModel, from
 * design/models/gravebound_tithe_binder.json by tools/dsl_to_game.py; the generator is tools/gen_tithe_binder.py).
 * For now the body stands in its rest pose (which already hovers: the robe's lowest tatter is 5 px above the ground)
 * and shows one docked weapon per bound weapon: weapon_1 (his own hook) .. weapon_N, N = the synced count
 * ({@link TitheBinderUnit#getWeapons}); the rest of the wheel stays empty.
 */
@OnlyIn(Dist.CLIENT)
public class TitheBinderUnitModel extends TitheBinderModel<TitheBinderUnit> {

    private final ModelPart[] weapons = new ModelPart[TitheLedger.MAX_WEAPONS];

    public TitheBinderUnitModel(ModelPart root) {
        super(root);
        for (int k = 0; k < weapons.length; k++)
            weapons[k] = bone("weapon_" + (k + 1));
    }

    /** Shows weapon_1..weapon_count, hides the rest (1-10). */
    public void showWeapons(int count) {
        for (int k = 0; k < weapons.length; k++)
            if (weapons[k] != null)
                weapons[k].visible = k < count;
    }

    @Override
    public void setupAnim(TitheBinderUnit entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw,
                          float headPitch) {
        restPose();
        showWeapons(entity.getWeapons());
        // TODO(anim rig, another maker): the Tithe Binder rig goes here - idle hover/bob, move glide, the volley (5.3),
        //  the bind overlay (5.4; entity.hasHalfBind() is the ghost in slot getWeapons() + 1), hit react, death and
        //  Grave Hook (5.5-5.7). Call it after restPose() and showWeapons(): e.g. TitheBinderRig.animate(this, entity,
        //  limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch).
    }
}
