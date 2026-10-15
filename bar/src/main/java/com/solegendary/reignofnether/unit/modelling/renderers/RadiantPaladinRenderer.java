package com.solegendary.reignofnether.unit.modelling.renderers;

import com.mojang.blaze3d.vertex.PoseStack;
import com.solegendary.reignofnether.unit.UpgradeVisuals;
import com.solegendary.reignofnether.unit.modelling.layers.TeamColourLayer;
import com.solegendary.reignofnether.unit.modelling.models.RadiantPaladinUnitModel;
import com.solegendary.reignofnether.unit.modelling.models.gen.RadiantPaladinModel;
import com.solegendary.reignofnether.unit.units.villagers.IronGolemUnit;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * The Sunforged Paladin (Radiant) - the concept that lovish picked (Oct 14 17:44), in its winged form (his angel ref
 * #1; "up to you", 20:22 - the winged one was my recommendation), drawn in the game from the concept model itself:
 * RadiantPaladinModel is generated from design/models/sunforged_paladin_radiant_winged.json with its
 * painted 4x atlas, owner-coloured tabard / cape / pauldron lames (TeamColourLayer over radiant_paladin_unit_team.png)
 * and the light - halo, sword fuller, eyes, sun emblems - drawn again full-bright and additive (the _glow texture, the
 * spider-eyes render type), so it shines at night and under fog as it does on the sheet.
 *
 * Detail: ~100 of its cubes are small detail (rivets, wire, splits) in "lod" parts, hidden once the RTS camera is
 * zoomed out (GeneratedBodies.wantsDetail: zoom 36, or 40 blocks outside the RTS view) - a few pixels
 * each there, and the cost of a whole golem each. The Golem Smithing research shows the "sun_ward" parts: the inner
 * halo ring, the crest of light, the blade's lit edges, the wings' lit primaries (UpgradeVisuals SHAPE slot).
 */
@OnlyIn(Dist.CLIENT)
public class RadiantPaladinRenderer extends MobRenderer<IronGolemUnit, RadiantPaladinUnitModel> {
    private static final String NAME = "radiant_paladin_unit";
    private static final ResourceLocation TEXTURE = GeneratedBodies.texture(NAME);
    /** The concept is 16 px to the block; at 0.92 the helm's top stands ~2.6 blocks, the vanilla golem's height. */
    static final float SCALE = 0.92f;

    public RadiantPaladinRenderer(EntityRendererProvider.Context context) {
        super(context, new RadiantPaladinUnitModel(context.bakeLayer(RadiantPaladinModel.LAYER_LOCATION)), 0.8f);
        this.addLayer(new TeamColourLayer<>(this));
        this.addLayer(GeneratedBodies.glow(this, NAME));
    }

    @Override
    public ResourceLocation getTextureLocation(IronGolemUnit entity) {
        return TEXTURE;
    }

    @Override
    protected void scale(IronGolemUnit entity, PoseStack poseStack, float partialTick) {
        poseStack.scale(SCALE, SCALE, SCALE);
    }

    @Override
    public void render(IronGolemUnit entity, float yaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers,
                       int light) {
        this.model.setDetail(GeneratedBodies.wantsDetail(entity));
        this.model.setUpgrade("sun_ward", UpgradeVisuals.has(entity.getUpgradeMask(), UpgradeVisuals.Kind.SHAPE));
        super.render(entity, yaw, partialTick, poseStack, buffers, light);
    }

}
