package com.solegendary.reignofnether.unit.modelling.models;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.unit.units.tide.SkyCorsairUnit;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * The Tidewrought Sky Corsair: a pirate airship. A canvas balloon (a 16x12x22 gas bag with top and bottom caps and a
 * tail fin) carrying a timber hull (14x5x20 with a bow, a stern castle, a keel, a rudder and a bomb-mortar on the
 * foredeck) on four ropes. Two blocks tall and wide, like its hitbox (EntityRegistrar sizes it at the Ghast's
 * {@link SkyCorsairUnit#SCALE}), so it reads as a ship from the RTS camera instead of the interim ghast cube. The
 * texture is painted by tools/gen_tide_skins.py over the CORSAIR layout there; the UV offsets below ARE that layout.
 * The balloon bobs and the hull swings a little under it; no allocation per frame.
 */
public class SkyCorsairModel extends EntityModel<SkyCorsairUnit> {
    public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(
        ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "sky_corsair"), "main");

    private final ModelPart balloon;
    private final ModelPart hull;
    private final ModelPart rudder;
    private final ModelPart[] ropes = new ModelPart[4];

    public SkyCorsairModel(ModelPart root) {
        this.balloon = root.getChild("balloon");
        this.hull = root.getChild("hull");
        this.rudder = hull.getChild("rudder");
        for (int i = 0; i < 4; i++)
            ropes[i] = root.getChild("rope" + i);
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        // the balloon: gas bag, caps, fins. y = 0 is its middle; the bow is at -z like every vanilla model's front.
        PartDefinition balloon = root.addOrReplaceChild("balloon", CubeListBuilder.create()
            .texOffs(0, 0).addBox(-8.0F, -6.0F, -11.0F, 16.0F, 12.0F, 22.0F)
            .texOffs(0, 34).addBox(-6.0F, -9.0F, -9.0F, 12.0F, 3.0F, 18.0F)
            .texOffs(60, 34).addBox(-6.0F, 6.0F, -9.0F, 12.0F, 3.0F, 18.0F),
            PartPose.offset(0.0F, 0.0F, 0.0F));
        balloon.addOrReplaceChild("fin_v", CubeListBuilder.create()
            .texOffs(76, 0).addBox(-0.5F, -3.0F, 0.0F, 1.0F, 6.0F, 6.0F), PartPose.offset(0.0F, -1.0F, 11.0F));
        balloon.addOrReplaceChild("fin_l", CubeListBuilder.create()
            .texOffs(90, 0).addBox(-6.0F, -0.5F, 0.0F, 6.0F, 1.0F, 5.0F), PartPose.offset(-8.0F, 1.0F, 6.0F));
        balloon.addOrReplaceChild("fin_r", CubeListBuilder.create()
            .texOffs(90, 0).addBox(0.0F, -0.5F, 0.0F, 6.0F, 1.0F, 5.0F), PartPose.offset(8.0F, 1.0F, 6.0F));
        // the ropes: from the bottom cap's corners down to the gunwales
        int i = 0;
        for (float x : new float[] { -6.0F, 6.0F })
            for (float z : new float[] { -9.0F, 9.0F })
                root.addOrReplaceChild("rope" + i++, CubeListBuilder.create()
                    .texOffs(70, 80).addBox(-0.5F, 0.0F, -0.5F, 1.0F, 8.0F, 1.0F), PartPose.offset(x, 9.0F, z));
        // the hull: its pivot is the gunwale line (y = 17, 7/16 block over the feet at y = 24)
        PartDefinition hull = root.addOrReplaceChild("hull", CubeListBuilder.create()
            .texOffs(0, 55).addBox(-7.0F, 0.0F, -10.0F, 14.0F, 5.0F, 20.0F)
            .texOffs(68, 55).addBox(-3.0F, 0.0F, -14.0F, 6.0F, 4.0F, 4.0F)
            .texOffs(68, 63).addBox(-5.0F, -3.0F, 6.0F, 10.0F, 3.0F, 4.0F)
            .texOffs(0, 80).addBox(-2.0F, 5.0F, -8.0F, 4.0F, 2.0F, 16.0F)
            .texOffs(40, 80).addBox(-1.5F, -3.0F, -9.0F, 3.0F, 3.0F, 8.0F),
            PartPose.offset(0.0F, 17.0F, 0.0F));
        hull.addOrReplaceChild("rudder", CubeListBuilder.create()
            .texOffs(62, 80).addBox(-0.5F, 0.0F, 0.0F, 1.0F, 5.0F, 3.0F), PartPose.offset(0.0F, 3.0F, 10.0F));
        return LayerDefinition.create(mesh, 128, 128);
    }

    @Override
    public void setupAnim(SkyCorsairUnit unit, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        // the gas bag rides a slow swell; the hull swings a touch later and a touch wider, and the rudder works
        float swell = Mth.sin(ageInTicks * 0.06F);
        balloon.y = swell * 0.6F;
        balloon.zRot = Mth.sin(ageInTicks * 0.045F) * 0.02F;
        hull.y = 17.0F + Mth.sin(ageInTicks * 0.06F - 0.6F) * 0.8F;
        hull.zRot = Mth.sin(ageInTicks * 0.045F - 0.4F) * 0.05F;
        hull.xRot = Mth.sin(ageInTicks * 0.03F) * 0.03F;
        for (ModelPart rope : ropes) {
            rope.y = 9.0F + balloon.y;
            rope.zRot = hull.zRot * 0.5F;
        }
        rudder.yRot = Mth.sin(ageInTicks * 0.1F) * 0.2F;
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int packedLight, int packedOverlay, float red, float green, float blue, float alpha) {
        balloon.render(poseStack, buffer, packedLight, packedOverlay, red, green, blue, alpha);
        for (ModelPart rope : ropes)
            rope.render(poseStack, buffer, packedLight, packedOverlay, red, green, blue, alpha);
        hull.render(poseStack, buffer, packedLight, packedOverlay, red, green, blue, alpha);
    }
}
