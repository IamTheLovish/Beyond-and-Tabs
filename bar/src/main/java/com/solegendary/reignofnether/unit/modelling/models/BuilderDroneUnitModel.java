package com.solegendary.reignofnether.unit.modelling.models;

import com.solegendary.reignofnether.unit.modelling.animations.Hover;
import com.solegendary.reignofnether.unit.modelling.animations.HoverMotion;
import com.solegendary.reignofnether.unit.modelling.models.gen.BuilderDroneModel;
import com.solegendary.reignofnether.unit.modelling.motion.HoverBody;
import com.solegendary.reignofnether.unit.units.tide.ShipwrightUnit;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * The Light Grid's Builder Drone (the Shipwright, converted in place - design/ai_faction.md, PR 379) on its generated
 * body (tools/gen_builder_drone.py -> BuilderDroneModel): the shared hover rig ({@link Hover}) does all of its motion -
 * the bob, the lean and bank, the arms reaching to the work and striking, the hover ring cutting out - and as a
 * {@link HoverBody} the living army drops and crashes it when it dies (DeathFall#poseHover), so the wreck is kept and
 * derezzes in the faction's finish. Nothing here is the drone's own: the next drone is a body and a Params.
 */
@OnlyIn(Dist.CLIENT)
public class BuilderDroneUnitModel extends BuilderDroneModel<ShipwrightUnit> implements HoverBody {

    /** The Builder Drone's motion: the defaults (HoverMotion.Params.drone), hull bottom 10 px up (gen_builder_drone HOVER). */
    public static final HoverMotion.Params PARAMS = HoverMotion.Params.drone();
    /** The hull's half width at the shoulders, px (gen_builder_drone HALF_WIDTH). */
    static final float HULL_HALF = 6.4f;

    private final Hover.Rig rig;

    public BuilderDroneUnitModel(ModelPart root) {
        super(root);
        this.rig = new Hover.Rig(this::bone);
    }

    @Override
    public void setupAnim(ShipwrightUnit entity, float limbSwing, float limbSwingAmount, float ageInTicks,
                          float netHeadYaw, float headPitch) {
        restPose();
        Hover.animate(rig, PARAMS, entity, ageInTicks, entity.isSwingingArmRepeatedly());
    }

    @Override
    public float hoverPx() {
        return PARAMS.hoverPx;
    }

    @Override
    public float hullHalfPx() {
        return HULL_HALF;
    }
}
