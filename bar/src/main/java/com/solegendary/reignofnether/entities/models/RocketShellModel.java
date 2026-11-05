package com.solegendary.reignofnether.entities.models;

import com.solegendary.reignofnether.ReignOfNether;
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
 * The Rocket Battery's charged bolt (RocketShell): a white-hot core with a nose and needle and two crossed halo fins, lying
 * along +X with the nose at +X like vanilla's arrow, and a jagged tail behind it as a second part so the renderer can flicker
 * it. Every part is drawn lit. Texture: tools/gen_rocket_shell.py (32x32).
 */
@OnlyIn(Dist.CLIENT)
public class RocketShellModel {
    public static final ModelLayerLocation LAYER_LOCATION =
        new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "rocket_shell_layer"), "main");

    /** The rocket (body, nose, fins) and the exhaust flame, drawn apart. */
    public final ModelPart body, flame;

    public RocketShellModel(ModelPart root) {
        this.body = root.getChild("body");
        this.flame = root.getChild("flame");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("body", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-4.0F, -1.0F, -1.0F, 8.0F, 2.0F, 2.0F)
                .texOffs(0, 4).addBox(4.0F, -1.0F, -1.0F, 2.0F, 2.0F, 2.0F)
                .texOffs(8, 4).addBox(6.0F, -0.5F, -0.5F, 1.0F, 1.0F, 1.0F)
                .texOffs(0, 8).addBox(-5.0F, -0.25F, -2.5F, 2.0F, 0.5F, 5.0F)
                .texOffs(14, 8).addBox(-5.0F, -2.5F, -0.25F, 2.0F, 5.0F, 0.5F),
            PartPose.ZERO);
        // the tail: a streak and two kinked pieces, so it reads as a lightning bolt, not a flame
        root.addOrReplaceChild("flame", CubeListBuilder.create()
                .texOffs(0, 16).addBox(-9.0F, -0.75F, -0.75F, 5.0F, 1.5F, 1.5F)
                .texOffs(0, 16).addBox(-12.0F, -1.5F, -0.25F, 3.0F, 1.0F, 1.0F)
                .texOffs(0, 16).addBox(-14.5F, -0.25F, -1.4F, 2.5F, 1.0F, 1.0F),
            PartPose.ZERO);
        return LayerDefinition.create(mesh, 32, 32);
    }
}
