package com.solegendary.reignofnether.unit.modelling.renderers;

import com.mojang.blaze3d.vertex.PoseStack;
import com.solegendary.reignofnether.unit.UpgradeVisuals;
import com.solegendary.reignofnether.unit.modelling.layers.TeamColourLayer;
import com.solegendary.reignofnether.unit.modelling.mesh.MeshBody;
import com.solegendary.reignofnether.unit.modelling.mesh.MeshModel;
import com.solegendary.reignofnether.unit.modelling.mesh.PlainBuffers;
import com.solegendary.reignofnether.unit.modelling.models.RadiantPaladinUnitModel;
import com.solegendary.reignofnether.unit.modelling.models.gen.RadiantPaladinModel;
import com.solegendary.reignofnether.unit.units.villagers.IronGolemUnit;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import javax.annotation.Nullable;

/**
 * The Sunforged Paladin (Radiant) - the concept that lovish picked (Oct 14 17:44), in its winged form (his angel ref
 * #1; "up to you", 20:22 - the winged one was my recommendation), drawn in the game from the concept model itself:
 * RadiantPaladinModel is generated from design/models/sunforged_paladin_radiant_winged.json with its
 * painted 8x atlas (mipmapped - MipmappedTextures), owner-coloured tabard / cape / pauldron lames (TeamColourLayer over radiant_paladin_unit_team.png)
 * and the light - halo, sword fuller, eyes, sun emblems - drawn again full-bright and additive (the _glow texture, the
 * spider-eyes render type), so it shines at night and under fog as it does on the sheet.
 *
 * Detail: ~100 of its cubes are small detail (rivets, wire, splits) in "lod" parts, hidden once the RTS camera is
 * zoomed out (GeneratedBodies.wantsDetail: zoom 36, or 40 blocks outside the RTS view) - a few pixels
 * each there, and the cost of a whole golem each. The Golem Smithing research shows the "sun_ward" parts: the inner
 * halo ring, the crest of light, the blade's lit edges, the wings' lit primaries (UpgradeVisuals SHAPE slot).
 *
 * Mesh body (spec 1 step 1, inbox/spec-1-mesh-bodies-2026-10-17.md): with -Dreignofnether.meshBodies=paladin the same
 * body is drawn from meshes/radiant_paladin_unit.bmesh (unit/modelling/mesh) - tools/mesh_export.py exports this very
 * cube model into it, so nothing may change on screen: MeshBodyTest checks the vertex stream bit for bit and the eyes
 * tour's MeshEyes compares the two bodies drawn in one frame. The model is swapped in before each draw, so the layers
 * (they draw getParentModel()) follow it; without the .bmesh the cube body stays (MeshBody logs it once). The mesh body
 * is drawn through PlainBuffers: with Oculus + Embeddium a mesh cube is otherwise never drawn at all (see there).
 */
@OnlyIn(Dist.CLIENT)
public class RadiantPaladinRenderer extends MobRenderer<IronGolemUnit, RadiantPaladinUnitModel> {
    private static final String NAME = "radiant_paladin_unit";
    private static final ResourceLocation TEXTURE = GeneratedBodies.texture(NAME);
    /** The concept is 16 px to the block; at 0.92 the helm's top stands ~2.6 blocks, the vanilla golem's height. */
    static final float SCALE = 0.92f;
    private static final boolean MESH = GeneratedBodies.meshBody("paladin");

    private final RadiantPaladinUnitModel cubeModel;
    private final MeshModel<RadiantPaladinUnitModel> meshModel =
        new MeshModel<>(MeshBody.location(NAME), 0, RadiantPaladinUnitModel::new);
    @Nullable
    private Boolean forcedMesh;   // MeshEyes' A/B: this body whatever the switch says; null = the switch

    public RadiantPaladinRenderer(EntityRendererProvider.Context context) {
        super(context, new RadiantPaladinUnitModel(context.bakeLayer(RadiantPaladinModel.LAYER_LOCATION)), 0.8f);
        this.cubeModel = this.model;
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
        RadiantPaladinUnitModel mesh = (forcedMesh != null ? forcedMesh : MESH) ? meshModel.get() : null;
        this.model = mesh != null ? mesh : cubeModel;
        this.model.setDetail(GeneratedBodies.wantsDetail(entity));
        this.model.setUpgrade("sun_ward", UpgradeVisuals.has(entity.getUpgradeMask(), UpgradeVisuals.Kind.SHAPE));
        super.render(entity, yaw, partialTick, poseStack, mesh != null ? PlainBuffers.of(buffers) : buffers, light);
    }

    /** The eyes A/B (MeshEyes): draw with the mesh body (true) or the cube body (false); null = as the switch says. */
    public void forceBody(@Nullable Boolean mesh) {
        this.forcedMesh = mesh;
    }

    /** The Paladin's mesh body is loaded (meshes/radiant_paladin_unit.bmesh). */
    public boolean meshReady() {
        return meshModel.get() != null;
    }
}
