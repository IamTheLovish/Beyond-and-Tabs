package com.solegendary.reignofnether.unit.modelling.layers;

import com.mojang.blaze3d.vertex.PoseStack;
import com.solegendary.reignofnether.player.PlayerColors;
import com.solegendary.reignofnether.registrars.MobEffectRegistrar;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.HashMap;
import java.util.Map;

/**
 * Owner-coloured cloth on a unit (design/unit_skins.md). A skin {@code foo_unit.png} may ship a companion
 * {@code foo_unit_team.png} of the same size: grey-scale cloth (tabard, sash, plume, cape) on transparent pixels,
 * drawn by tools/gen_hd_skins.py. This layer draws the parent model a second time with that texture, multiplied by
 * the owner's display colour (the same colour as the unit's outline and selection circle, so it follows the
 * player-colour / alliance-colour toggle), so every unit carries its side's colour on its body and reads at a
 * glance from the RTS camera - as RoN already does for outlines, but on the unit itself.
 *
 * Same geometry, same depth: the overlay passes the LEQUAL depth test exactly like vanilla's horse markings, so no
 * z-fighting. A skin without a team texture costs one cached map lookup per frame and draws nothing. Units without an
 * owner (neutral / survival waves) keep the faction-default cloth painted into the base skin under the team region.
 */
@OnlyIn(Dist.CLIENT)
public class TeamColourLayer<T extends LivingEntity, M extends EntityModel<T>> extends RenderLayer<T, M> {

    // base texture -> its team texture, or NONE when the skin has no team region (checked once per texture)
    private static final Map<ResourceLocation, ResourceLocation> TEAM_TEXTURES = new HashMap<>();
    private static final ResourceLocation NONE = ResourceLocation.fromNamespaceAndPath("reignofnether", "none");

    public TeamColourLayer(RenderLayerParent<T, M> parent) {
        super(parent);
    }

    /** Forgets every cached answer (ClientModEvents' reload listener: packs can add or remove _team.png files). */
    public static void clearCache() {
        TEAM_TEXTURES.clear();
    }

    static ResourceLocation teamTexture(ResourceLocation base) {
        ResourceLocation team = TEAM_TEXTURES.get(base);
        if (team == null) {
            String path = base.getPath();
            team = NONE;
            if (path.endsWith(".png")) {
                ResourceLocation candidate = ResourceLocation.fromNamespaceAndPath(base.getNamespace(),
                        path.substring(0, path.length() - 4) + "_team.png");
                if (Minecraft.getInstance().getResourceManager().getResource(candidate).isPresent())
                    team = candidate;
            }
            TEAM_TEXTURES.put(base, team);
        }
        return team == NONE ? null : team;
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int light, T entity, float limbSwing, float limbSwingAmount,
                       float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        if (!(entity instanceof Unit unit) || entity.isInvisible())
            return;
        // a phasing unit is drawn translucent (LivingEntityRendererMixin); an opaque tabard on it would give it away
        if (entity.hasEffect(MobEffectRegistrar.PHASING.get()))
            return;
        String owner = unit.getOwnerName();
        if (owner == null || owner.isBlank())
            return;
        ResourceLocation team = teamTexture(this.getTextureLocation(entity));
        if (team == null)
            return;
        int rgb = PlayerColors.getPlayerDisplayColorHex(owner);
        float r = ((rgb >> 16) & 0xFF) / 255f, g = ((rgb >> 8) & 0xFF) / 255f, b = (rgb & 0xFF) / 255f;
        renderColoredCutoutModel(this.getParentModel(), team, pose, buffers, light, entity, r, g, b);
    }
}
