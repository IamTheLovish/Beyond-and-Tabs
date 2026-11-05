package com.solegendary.reignofnether.unit.modelling.models;

import com.solegendary.reignofnether.ReignOfNether;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * The Rocket Battery's rack: a back plate and a bundle of four rocket tubes (2 x 2) with a rocket nose peeking from each,
 * hinged at the crew's hip so it can lift forward over the shoulder to fire and be kicked back by each rocket
 * (RocketRackLayer, RocketLaunchPose). Coordinates are in the torso's model space: y down, the back at z = +3.
 * Texture: tools/gen_rocket_shell.py (64x32).
 */
@OnlyIn(Dist.CLIENT)
public class RocketRackModel {
    public static final ModelLayerLocation LAYER_LOCATION =
        new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "rocket_rack_layer"), "main");

    /** Where the hinge sits behind the torso at rest. */
    public static final float HINGE_Y = 9.5F, HINGE_Z = 3.5F;
    /** Rest tilt (the top leans back against the crew), and the extra tilt forward at full lift and back at full recoil, radians. */
    public static final float REST_TILT = -0.12F, LIFT_TILT = 0.30F, RECOIL_TILT = 0.45F;
    /** How far a full recoil shoves the rack straight back, in model pixels. */
    public static final float RECOIL_SHOVE = 1.6F;

    public final ModelPart rack;

    public RocketRackModel(ModelPart root) {
        this.rack = root.getChild("rack");
    }

    /** Poses the rack for a lift and a recoil, both 0 to 1 (RocketLaunchPose). */
    public void pose(float lift, float recoil) {
        rack.xRot = REST_TILT + LIFT_TILT * lift - RECOIL_TILT * recoil;
        rack.z = HINGE_Z + RECOIL_SHOVE * recoil;
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        CubeListBuilder rack = CubeListBuilder.create()
            .texOffs(0, 0).addBox(-4.5F, -9.0F, -0.5F, 9.0F, 10.0F, 1.0F);
        // the four tubes (two columns, two rows deep) and a rocket nose in the mouth of each
        for (float x : new float[] { -4.0F, 0.5F })
            for (float z : new float[] { 0.5F, 3.5F }) {
                rack.texOffs(0, 12).addBox(x, -10.0F, z, 3.5F, 11.0F, 3.0F);
                rack.texOffs(14, 12).addBox(x + 0.5F, -12.0F, z + 0.25F, 2.5F, 2.0F, 2.5F);
            }
        mesh.getRoot().addOrReplaceChild("rack", rack, PartPose.offset(0.0F, HINGE_Y, HINGE_Z));
        return LayerDefinition.create(mesh, 64, 32);
    }
}
