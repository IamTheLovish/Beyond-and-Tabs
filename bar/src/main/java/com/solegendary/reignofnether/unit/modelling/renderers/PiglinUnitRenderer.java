//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package com.solegendary.reignofnether.unit.modelling.renderers;

import com.google.common.collect.ImmutableMap;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.unit.modelling.models.PiglinUnitModel;
import net.minecraft.client.model.HumanoidArmorModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.Map;

@OnlyIn(Dist.CLIENT)
public class PiglinUnitRenderer extends HumanoidMobRenderer<Mob, PiglinUnitModel<Mob>> {
    private static final Map<EntityType<?>, ResourceLocation> TEXTURES;
    private static final ResourceLocation FALLBACK = ResourceLocation.parse("textures/entity/piglin/piglin.png");
    private static final float PIGLIN_CUSTOM_HEAD_SCALE = 1.0019531F;

    // See class EntityRenderers for the original code
    public PiglinUnitRenderer(EntityRendererProvider.Context pContext) {
        super(pContext, createModel(pContext.getModelSet(), ModelLayers.PIGLIN, false), 0.5F, 1.0019531F, 1.0F, 1.0019531F);
        this.addLayer(new HumanoidArmorLayer(this,
                    new HumanoidArmorModel(pContext.bakeLayer(ModelLayers.ZOMBIE_INNER_ARMOR)),
                    new HumanoidArmorModel(pContext.bakeLayer(ModelLayers.ZOMBIE_OUTER_ARMOR)), pContext.getModelManager()));
        // owner-coloured sash / tabard for skins that ship a *_team.png (tools/gen_hd_skins.py)
        this.addLayer(new com.solegendary.reignofnether.unit.modelling.layers.TeamColourLayer<>(this));
        // researched looks (unit/UpgradeVisuals): Shield Tactics bolts the Horde plate onto a Brute's chest; a weapon
        // glow lands after HumanoidMobRenderer's ItemInHandLayer
        this.addLayer(com.solegendary.reignofnether.unit.modelling.layers.UpgradeTrimLayer.body(this));
        this.addLayer(com.solegendary.reignofnether.unit.modelling.layers.UpgradeGlowLayer.heldItem(this));
    }

    private static PiglinUnitModel<Mob> createModel(EntityModelSet p_174350_, ModelLayerLocation pLayer, boolean p_174352_) {
        PiglinUnitModel<Mob> $$3 = new PiglinUnitModel(p_174350_.bakeLayer(pLayer));
        if (p_174352_) {
            $$3.rightEar.visible = false;
        }

        return $$3;
    }

    public ResourceLocation getTextureLocation(Mob pEntity) {
        ResourceLocation $$1 = TEXTURES.get(pEntity.getType());
        // never crash the client over a missing entry (a bot's Bonewright did, Oct 10 playtest): fall back to the grunt skin
        return $$1 != null ? $$1 : FALLBACK;
    }

    static {
        TEXTURES = ImmutableMap.of(
            // the Horde's own skins (tools/gen_hd_skins.py): fur-vested Clan Builder, rust-plated Shieldbearer,
            // fur-cloaked Spear Thrower - each with an owner-coloured team region (TeamColourLayer)
            EntityRegistrar.GRUNT_UNIT.get(), ResourceLocation.fromNamespaceAndPath("reignofnether", "textures/entities/grunt_unit.png"),
            EntityRegistrar.BRUTE_UNIT.get(), ResourceLocation.fromNamespaceAndPath("reignofnether", "textures/entities/brute_unit.png"),
            EntityRegistrar.HEADHUNTER_UNIT.get(), ResourceLocation.fromNamespaceAndPath("reignofnether", "textures/entities/headhunter_unit.png"),
            // the T2 worker's own skin: bone-plated apron, skull-and-tusk headpiece, hammer belt (tools/gen_t2t3_skins.py)
            EntityRegistrar.BONEWRIGHT_UNIT.get(), ResourceLocation.fromNamespaceAndPath("reignofnether", "textures/entities/bonewright_unit.png"),
            EntityRegistrar.ZOMBIE_PIGLIN_UNIT.get(), ResourceLocation.parse("textures/entity/piglin/zombified_piglin.png")
        );
    }
}
