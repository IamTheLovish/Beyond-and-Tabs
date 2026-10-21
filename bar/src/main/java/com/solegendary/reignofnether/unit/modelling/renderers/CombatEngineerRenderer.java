package com.solegendary.reignofnether.unit.modelling.renderers;

import com.mojang.blaze3d.vertex.PoseStack;
import com.solegendary.reignofnether.unit.modelling.models.VillagerUnitModel;
import com.solegendary.reignofnether.unit.units.engineers.CombatEngineerUnit;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * The six illager-body combat engineers (design/combat_engineers.md): the worker illager body with each one's own HD
 * skin ({@code textures/entities/<id>.png} from tools/gen_hd_skins.py) and its team region (TeamColourLayer, wired by
 * AbstractVillagerUnitRenderer), tools in hand only while working or fighting, like the workers. One class for all six:
 * only the texture differs, and it is resolved once here, not per frame.
 */
@OnlyIn(Dist.CLIENT)
public class CombatEngineerRenderer extends AbstractVillagerUnitRenderer<CombatEngineerUnit> {
    private final ResourceLocation texture;
    private static final java.util.Set<String> HD_SKINS = java.util.Set.of("field_mason_unit");

    /** @param textureName the skin's file name without extension, e.g. "field_mason_unit" */
    public CombatEngineerRenderer(EntityRendererProvider.Context context, String textureName) {
        super(context, new VillagerUnitModel<>(context.bakeLayer(VillagerUnitModel.LAYER_LOCATION)), 0.5F);
        // an HD skin (tools/hd_villager.py, 8x) is mipmapped with its _team mask; the 64x64 ones keep vanilla sampling
        this.texture = HD_SKINS.contains(textureName) ? GeneratedBodies.texture(textureName)
                : ResourceLocation.fromNamespaceAndPath("reignofnether", "textures/entities/" + textureName + ".png");
        this.addLayer(new ItemInHandLayer<CombatEngineerUnit, VillagerUnitModel<CombatEngineerUnit>>(this, context.getItemInHandRenderer()) {
            public void render(PoseStack pose, MultiBufferSource mbs, int light, CombatEngineerUnit unit, float a, float b, float c, float d, float e, float f) {
                if ((unit.getBuildRepairGoal() != null && unit.getBuildRepairGoal().isBuilding()) ||
                    (unit.getGatherResourceGoal() != null && unit.getGatherResourceGoal().isGathering()) ||
                    (unit.getTargetGoal() != null && unit.getTargetGoal().getTarget() != null)) {
                    super.render(pose, mbs, light, unit, a, b, c, d, e, f);
                }
            }
        });
    }

    public ResourceLocation getTextureLocation(CombatEngineerUnit unit) {
        return texture;
    }
}
