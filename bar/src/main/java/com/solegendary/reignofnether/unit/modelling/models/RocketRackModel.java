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
 * The Rocket Battery's rack: two compact pods of copper tubes over the crew's shoulders, two tubes each, strapped to a
 * yoke and a plate on its back (lovish, Nov 7: "B: copper tubes" - the fanned lightning rods with orbs before read as
 * "loose blue cubes orbiting off the shoulders" to the blind critic, the old compact tube rack as artillery). Each tube
 * holds one charged bolt seated in its mouth. The rack is hinged at the crew's hip so it can lift forward as it charges
 * and be kicked back by each bolt (RocketRackLayer, RocketLaunchPose, RocketCharge); the charges are their own part so
 * the layer can draw them lit and sink each one into its tube with how loaded it is - the rack empties as each bolt
 * leaves. Where the tubes are is {@link RocketRackGeometry}, shared with the battery that fires the bolts from those
 * muzzles. Coordinates are the torso's model space: y down, the back at z = +3. Texture: tools/gen_rocket_shell.py (64x32).
 */
@OnlyIn(Dist.CLIENT)
public class RocketRackModel {
    public static final ModelLayerLocation LAYER_LOCATION =
        new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "rocket_rack_layer"), "main");

    /** How far (radians) a full recoil kicks the rack back, and how far (px) it shoves it straight back. */
    public static final float RECOIL_TILT = 0.30F, RECOIL_SHOVE = 1.6F;

    /** The copper tubes and their frame, and the lit part holding the charges: both hinged at the same place. */
    public final ModelPart rack, glow;
    private final ModelPart[] charges = new ModelPart[RocketRackGeometry.TUBES];

    public RocketRackModel(ModelPart root) {
        this.rack = root.getChild("rack");
        this.glow = root.getChild("glow");
        for (int i = 0; i < charges.length; i++)
            charges[i] = glow.getChild("charge" + i);
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

    /** Sizes the charge in {@code tube} (0 = gone, 1 = full): it sinks into the tube's mouth as it empties. */
    public void charge(int tube, float scale) {
        ModelPart c = charges[tube];
        c.visible = scale > 0.02F;
        c.xScale = c.zScale = Math.min(scale, 1.2F);
        c.yScale = scale;
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        float hy = RocketRackGeometry.HINGE_Y, hz = RocketRackGeometry.HINGE_Z;
        float w = RocketRackGeometry.TUBE_WIDTH, bottom = RocketRackGeometry.TUBE_BOTTOM, mouth = RocketRackGeometry.TUBE_MOUTH;
        // the frame: a plate on the back and a yoke across the shoulder blades that carries the two pods
        CubeListBuilder frame = CubeListBuilder.create()
            .texOffs(0, 0).addBox(-4.5F, -8.0F, -0.5F, 9.0F, 10.0F, 1.0F)
            .texOffs(0, 17).addBox(-7.5F, bottom - 2.0F, -0.5F, 15.0F, 2.0F, 3.0F);
        for (int i = 0; i < RocketRackGeometry.TUBES; i++) {
            float x = RocketRackGeometry.TUBE_X[i], z = RocketRackGeometry.TUBE_Z[i];
            // the tube, and a bright copper ring round its mouth
            frame.texOffs(22, 0).addBox(x - w / 2, mouth, z - w / 2, w, bottom - mouth, w);
            frame.texOffs(36, 0).addBox(x - w / 2 - 0.5F, mouth - 1.0F, z - w / 2 - 0.5F, w + 1.0F, 2.0F, w + 1.0F);
        }
        // one strap round each pod's pair of tubes, half way up
        for (float x : new float[] { RocketRackGeometry.TUBE_X[0], RocketRackGeometry.TUBE_X[1] })
            frame.texOffs(36, 7).addBox(x - w / 2 - 0.5F, (mouth + bottom) / 2 - 1.0F, RocketRackGeometry.TUBE_Z[0] - w / 2 - 0.5F,
                w + 1.0F, 2.0F, RocketRackGeometry.TUBE_Z[2] - RocketRackGeometry.TUBE_Z[0] + w + 1.0F);
        root.addOrReplaceChild("rack", frame, PartPose.offset(0.0F, hy, hz));
        PartDefinition glow = root.addOrReplaceChild("glow", CubeListBuilder.create(), PartPose.offset(0.0F, hy, hz));
        for (int i = 0; i < RocketRackGeometry.TUBES; i++)
            // the charge stands two pixels proud of the ring, pivoted in the mouth so it sinks into the tube as it empties
            glow.addOrReplaceChild("charge" + i, CubeListBuilder.create()
                    .texOffs(56, 0).addBox(-1.0F, -3.0F, -1.0F, 2.0F, 3.0F, 2.0F),
                PartPose.offset(RocketRackGeometry.TUBE_X[i], mouth, RocketRackGeometry.TUBE_Z[i]));
        return LayerDefinition.create(mesh, 64, 32);
    }
}
