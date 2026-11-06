package com.solegendary.reignofnether.unit.modelling.renderers;

import com.mojang.blaze3d.vertex.PoseStack;
import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.unit.modelling.models.SplitPasses;
import com.solegendary.reignofnether.orthoview.OrthoviewClientEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import com.solegendary.reignofnether.player.PlayerColors;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * What every unit body made through the concept -> game pipeline shares (tools/dsl_to_game.py writes the model and
 * its textures; claude/paladin-radiant.md tells the story): the texture paths by convention
 * ({@code textures/entities/<name>.png}, {@code _team.png} for TeamColourLayer, {@code _glow.png} drawn full-bright
 * and additive), and the one rule for the small detail parts ("lod"): drawn while the RTS camera is zoomed in to
 * {@link #DETAIL_ZOOM} or closer, or, outside the RTS view, while big enough on screen (UnitLod: within
 * {@link #DETAIL_DISTANCE} blocks on a 1080p screen at FOV 70, further on a bigger screen or a narrower FOV).
 */
@OnlyIn(Dist.CLIENT)
public final class GeneratedBodies {
    private GeneratedBodies() { }

    public static final float DETAIL_ZOOM = 36f;
    public static final double DETAIL_DISTANCE = 40.0;

    /**
     * Mesh bodies (spec 1, inbox/spec-1-mesh-bodies-2026-10-17.md): {@code -Dreignofnether.meshBodies=paladin} - a comma
     * list of units, or "all" - draws those units from their .bmesh (unit/modelling/mesh) instead of their cube model.
     * Off by default. Step 1 ships the Paladin's own cube model as its mesh (tools/mesh_export.py --from-dsl), so the
     * switch must change nothing on screen: the gate every sculpted body comes after.
     */
    public static final Set<String> MESH_BODIES = Arrays.stream(System.getProperty("reignofnether.meshBodies", "")
            .split(",")).map(s -> s.trim().toLowerCase(Locale.ROOT)).filter(s -> !s.isEmpty()).collect(Collectors.toSet());

    /** The unit (its short name: "paladin") is drawn from its mesh body. */
    public static boolean meshBody(String unit) {
        return MESH_BODIES.contains(unit) || MESH_BODIES.contains("all");
    }

    /** A generated body's texture (registered for mipmaps together with its _team mask). */
    public static ResourceLocation texture(String name) {
        MipmappedTextures.register(location(name + "_team"));
        return mipmapped(name);
    }

    private static ResourceLocation location(String name) {
        return ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "textures/entities/" + name + ".png");
    }

    private static ResourceLocation mipmapped(String name) {
        ResourceLocation location = location(name);
        MipmappedTextures.register(location);
        return location;
    }

    /** The light of a generated body (halo, eyes, a sun disc, vials): its _glow texture, full-bright and additive. */
    public static <T extends LivingEntity, M extends EntityModel<T>> EyesLayer<T, M> glow(RenderLayerParent<T, M> parent,
                                                                                         String name) {
        RenderType type = MipRenderTypes.glow(mipmapped(name + "_glow"));
        return new EyesLayer<>(parent) {
            @Override
            public RenderType renderType() {
                return type;
            }

            /** Only the parts that carry light (SplitPasses): the glow texture is black everywhere else. */
            @Override
            public void render(PoseStack pose, MultiBufferSource buffers, int light, T entity, float limbSwing,
                               float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw,
                               float headPitch) {
                if (UnitLod.skipOverlay(entity))   // shader shadow map: the body pass already casts this silhouette
                    return;
                SplitPasses split = this.getParentModel() instanceof SplitPasses sp ? sp : null;
                if (split != null)
                    split.beginPass(split.glowParts());
                try {
                    super.render(pose, buffers, light, entity, limbSwing, limbSwingAmount, partialTick, ageInTicks,
                            netHeadYaw, headPitch);
                } finally {
                    if (split != null)
                        split.endPass(split.glowParts());
                }
            }
        };
    }

    /**
     * The light lines of a body drawn in its OWNER's colour (the Light Grid, design/ai_faction.md: "light as the only
     * ornament", one team light colour): its {@code _team_glow} texture (tools/dsl_to_game.py writes it for a colour
     * both emissive and on --team), full-bright and additive like {@link #glow}, multiplied by the owner's display
     * colour - the same colour as the unit's outline and selection circle, so from the RTS camera the brightest thing
     * on the body says whose it is. An unowned unit glows {@link #UNOWNED_LIGHT}. Draws only the glow parts.
     */
    public static <T extends LivingEntity, M extends EntityModel<T>> RenderLayer<T, M> teamGlow(
            RenderLayerParent<T, M> parent, String name) {
        RenderType type = MipRenderTypes.glow(mipmapped(name + "_team_glow"));
        return new RenderLayer<>(parent) {
            @Override
            public void render(PoseStack pose, MultiBufferSource buffers, int light, T entity, float limbSwing,
                               float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw,
                               float headPitch) {
                if (entity.isInvisible() || UnitLod.skipOverlay(entity))
                    return;
                int rgb = UNOWNED_LIGHT;
                if (entity instanceof Unit unit && unit.getOwnerName() != null && !unit.getOwnerName().isBlank())
                    rgb = PlayerColors.getPlayerDisplayColorHex(unit.getOwnerName());
                float r = ((rgb >> 16) & 0xFF) / 255f, g = ((rgb >> 8) & 0xFF) / 255f, b = (rgb & 0xFF) / 255f;
                M model = this.getParentModel();
                SplitPasses split = model instanceof SplitPasses sp ? sp : null;
                if (split != null)
                    split.beginPass(split.glowParts());
                try {
                    model.renderToBuffer(pose, buffers.getBuffer(type), LightTexture.FULL_BRIGHT,
                        OverlayTexture.NO_OVERLAY, r, g, b, 1f);
                } finally {
                    if (split != null)
                        split.endPass(split.glowParts());
                }
            }
        };
    }

    /** The light of a body with no owner (a neutral or a preview): a cool white. */
    public static final int UNOWNED_LIGHT = 0xCFEFFF;

    public static boolean wantsDetail(Entity entity) {
        if (OrthoviewClientEvents.isEnabled())
            return OrthoviewClientEvents.getZoom() <= DETAIL_ZOOM;
        // outside the RTS view: by on-screen size (UnitLod; 40 blocks on a 1080p screen at FOV 70, as before)
        Minecraft mc = Minecraft.getInstance();
        return UnitLod.firstPersonDetail(Math.sqrt(mc.gameRenderer.getMainCamera().getPosition()
            .distanceToSqr(entity.position())));
    }
}
