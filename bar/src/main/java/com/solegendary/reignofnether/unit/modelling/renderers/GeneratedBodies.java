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
 * {@link #DETAIL_ZOOM} or closer, or, outside the RTS view, within {@link #DETAIL_DISTANCE} blocks.
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
                SplitPasses split = this.getParentModel() instanceof SplitPasses sp ? sp : null;
                if (split != null)
                    split.drawOnly(split.glowParts());
                try {
                    super.render(pose, buffers, light, entity, limbSwing, limbSwingAmount, partialTick, ageInTicks,
                            netHeadYaw, headPitch);
                } finally {
                    if (split != null)
                        split.drawAll();
                }
            }
        };
    }

    public static boolean wantsDetail(Entity entity) {
        if (OrthoviewClientEvents.isEnabled())
            return OrthoviewClientEvents.getZoom() <= DETAIL_ZOOM;
        Minecraft mc = Minecraft.getInstance();
        return mc.gameRenderer.getMainCamera().getPosition().distanceToSqr(entity.position())
            < DETAIL_DISTANCE * DETAIL_DISTANCE;
    }
}
