package com.solegendary.reignofnether.unit.modelling.models;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.unit.units.storm.RocketRackGeometry;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * The Rocket Battery's rack: a copper frame on the crew's back carrying four lightning rods fanned out over the shoulders,
 * a charged orb on each rod's tip (lovish, Nov 5: "a frame of copper lightning rods instead of gunpowder tubes"). It is
 * hinged at the crew's hip so it can lift forward as it charges and be kicked back by each bolt (RocketRackLayer,
 * RocketLaunchPose, RocketCharge); the orbs are their own part so the layer can draw them lit and scale each one with how
 * loaded its rod is - the rack empties as each bolt leaves. Where the rods end is {@link RocketRackGeometry}, shared with
 * the battery that fires the bolts from those tips. Coordinates are the torso's model space: y down, the back at z = +3.
 * Texture: tools/gen_rocket_shell.py (64x32).
 */
@OnlyIn(Dist.CLIENT)
public class RocketRackModel {
    public static final ModelLayerLocation LAYER_LOCATION =
        new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "rocket_rack_layer"), "main");

    /** How far (radians) a full recoil kicks the rack back, and how far (px) it shoves it straight back. */
    public static final float RECOIL_TILT = 0.30F, RECOIL_SHOVE = 1.6F;

    /** The copper frame with its rods, and the lit part holding the orbs: both hinged at the same place. */
    public final ModelPart rack, glow;
    private final ModelPart[] orbs = new ModelPart[RocketRackGeometry.RODS];

    public RocketRackModel(ModelPart root) {
        this.rack = root.getChild("rack");
        this.glow = root.getChild("glow");
        for (int i = 0; i < orbs.length; i++)
            orbs[i] = glow.getChild("orb" + i);
    }

    /** Poses the rack for a lift and a recoil, both 0 to 1 (RocketLaunchPose, RocketCharge). */
    public void pose(float lift, float recoil) {
        float tilt = RocketRackGeometry.REST_TILT + RocketRackGeometry.LIFT_TILT * lift - RECOIL_TILT * recoil;
        float z = RocketRackGeometry.HINGE_Z + RECOIL_SHOVE * recoil;
        for (ModelPart p : new ModelPart[] { rack, glow }) {
            p.xRot = tilt;
            p.z = z;
        }
    }

    /** Sizes the orb on {@code rod} (0 = gone, 1 = full) - an empty rod shows none. */
    public void orb(int rod, float scale) {
        ModelPart o = orbs[rod];
        o.visible = scale > 0.02F;
        o.xScale = o.yScale = o.zScale = scale;
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        float hy = RocketRackGeometry.HINGE_Y, hz = RocketRackGeometry.HINGE_Z;
        // the frame: a cross bar over the shoulder blades and a plate hanging from it, then the rods
        PartDefinition rack = root.addOrReplaceChild("rack", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-5.5F, -1.0F, -1.0F, 11.0F, 3.0F, 2.0F)
                .texOffs(0, 6).addBox(-4.5F, 1.5F, -0.5F, 9.0F, 5.0F, 1.0F),
            PartPose.offset(0.0F, hy, hz));
        PartDefinition glow = root.addOrReplaceChild("glow", CubeListBuilder.create(), PartPose.offset(0.0F, hy, hz));
        for (int i = 0; i < RocketRackGeometry.RODS; i++) {
            float len = RocketRackGeometry.ROD_LENGTH[i], fan = RocketRackGeometry.ROD_FAN[i], x = RocketRackGeometry.ROD_X[i];
            rack.addOrReplaceChild("rod" + i, CubeListBuilder.create()
                    .texOffs(28, 0).addBox(-1.0F, -len, -1.0F, 2.0F, len + 1.0F, 2.0F)
                    .texOffs(38, 0).addBox(-1.5F, -len + 3.0F, -1.5F, 3.0F, 2.0F, 3.0F),
                PartPose.offsetAndRotation(x, 0.0F, 0.0F, 0.0F, 0.0F, fan));
            // the orb sits on the rod's tip: a core and three thin spikes crossing it
            float tx = x + len * (float) Math.sin(fan), ty = -len * (float) Math.cos(fan);
            glow.addOrReplaceChild("orb" + i, CubeListBuilder.create()
                    .texOffs(0, 14).addBox(-2.0F, -2.0F, -2.0F, 4.0F, 4.0F, 4.0F)
                    .texOffs(40, 8).addBox(-4.0F, -0.5F, -0.5F, 8.0F, 1.0F, 1.0F)
                    .texOffs(40, 8).addBox(-0.5F, -4.0F, -0.5F, 1.0F, 8.0F, 1.0F)
                    .texOffs(40, 8).addBox(-0.5F, -0.5F, -4.0F, 1.0F, 1.0F, 8.0F),
                PartPose.offsetAndRotation(tx, ty - 1.0F, 0.0F, 0.0F, 0.0F, fan));
        }
        return LayerDefinition.create(mesh, 64, 32);
    }
}
