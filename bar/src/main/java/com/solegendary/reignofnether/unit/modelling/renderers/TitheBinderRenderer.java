package com.solegendary.reignofnether.unit.modelling.renderers;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.solegendary.reignofnether.barfx.BarFxRenderTypes;
import com.solegendary.reignofnether.unit.modelling.animations.TitheBinderDrive;
import com.solegendary.reignofnether.unit.modelling.animations.TitheBinderRig;
import com.solegendary.reignofnether.unit.modelling.layers.TeamColourLayer;
import com.solegendary.reignofnether.unit.modelling.models.TitheBinderUnitModel;
import com.solegendary.reignofnether.unit.modelling.models.gen.TitheBinderModel;
import com.solegendary.reignofnether.unit.units.monsters.TitheBinderUnit;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.List;

/**
 * The Gravebound Tithe Binder (tithe_binder_unit): the generated body (TitheBinderModel; tools/gen_tithe_binder.py ->
 * tools/dsl_to_game.py) moved by its rig, team colour on the hood and mantle, the docked weapons in their value ladder
 * ({@link WeaponLayer}: the hook solid iron_rust, the bound weapons translucent soul_core, each in a thin soul_glow
 * shell), then its own glow pass ({@link GlowLayer}: the lantern core at full). The whole of it hovers
 * {@link #HOVER_LIFT} px higher than the rig puts it. The Tithe of Iron upgrade's cubes stay hidden (no research yet).
 *
 * <p>The rig: each unit carries a {@link TitheBinderDrive} (TitheBinderUnit.clientDrive, made here on the first draw).
 * {@link #render} reads the unit into the drive's signals and steps it to the entity's tickCount (once per game tick;
 * a second draw in the same tick, such as a shader pack's shadow pass, steps nothing); the model lerps the last two
 * poses by partialTick. Vanilla's sideways death flip is off: the rig plays its own death (sheet 5.6).
 *
 * <p>{@link FxLayer}, after the body, draws what is not bone-tree: the flying weapons (the volley's, the falling ones at
 * death, the Grave Hook) from the pose's fly* fields; then, alpha blended and untextured (BarFxRenderTypes, no new
 * shader), the dark things: the hover's own ground shadow (an ellipse wider than the robe, shrinking as the hover
 * rises; vanilla's disc is off), the hem chains as iron_rust rods into the hover gap, the soul chains' iron links,
 * the count ring's dark shells and unlit cubes and the ground ring's dark notches; then the light, additive: the
 * volley's telegraph wedge on the ground, the soul-chain glow cores, the count ring's lit cubes and lit ground notches,
 * the impact flashes, the lantern's glow (the one strong
 * glow on the body), the bind's lash, orb, flash and forge line, the flight streaks, the shatter sparks and four 1 px
 * soul motes. Far LOD (GeneratedBodies.wantsDetail false, the tactical band): the thin docked chains (the rig's 1.2 px),
 * the streaks, the motes, the bind's lash and forge line, the shatter sparks and every impact flash after the first are
 * culled; the ring (bigger cubes), the weapons (their far stand-ins, drawn thicker), the thrown chains, the shadow, the
 * hem rods, the wedge, the lantern, the orb and the flashes stay. Nothing is allocated per frame.
 */
@OnlyIn(Dist.CLIENT)
public class TitheBinderRenderer extends MobRenderer<TitheBinderUnit, TitheBinderUnitModel> {
    private static final String NAME = "tithe_binder_unit";
    private static final ResourceLocation TEXTURE = GeneratedBodies.texture(NAME);
    /** Forge's white texture: the weapons' flat colours (as UpgradeTrimLayer's trim). */
    private static final ResourceLocation WHITE = ResourceLocation.parse("forge:textures/white.png");

    // design sheet 2.3 palette (0xRRGGBB)
    static final int SOUL_GLOW = 0x62F3CB, SOUL_CORE = 0x34DBA3, SOUL_DIM = 0x1B6D53, WHITE_HOT = 0xE9FFF8;
    /** The hover's ground shadow and the count ring's shells: near-black. */
    static final int SHADOW = 0x0A0A0C;
    /** Wrought iron and its rust: the soul chains' links. */
    static final int IRON = 0x45434A, RUST = 0x7A4326;
    /** The palette's iron_rust (rust_mail): the hook in slot 1 and the hem rods (in-game critic r3: lighter rods). */
    static final int IRON_RUST = 0x7A5A44;
    /**
     * px: the whole Binder - body, wheel and every effect laid in its model space - drawn this much higher than the
     * rig puts it (in-game critic r3: the hover gap still did not read), by the renderer's scale step. What stays on
     * the ground (the shadow, the count's ground ring, the telegraph wedge, the hem rods' floor) and the targets read
     * from the world (toModel) take it back off. The rig's own bob (hoverPx) still sizes the shadow.
     */
    static final float HOVER_LIFT = 3f;
    /**
     * The value ladder (in-game critic r3: the docked weapons read as opaque mint diamond tools): the hook in slot 1
     * solid iron_rust, lit like the body; slots 2-10 soul_core at BOUND_ALPHA, full-bright; every weapon in a soul_glow
     * shell TitheBinderUnitModel.EDGE_PX proud of it at full (additive, mirrored so only its far side draws: behind the
     * weapon's own depth except along its silhouette - a rim, no new shader).
     */
    static final float BOUND_ALPHA = 0.55f;
    private static final RenderType HOOK_TYPE = RenderType.entityCutoutNoCull(WHITE);
    private static final RenderType BOUND_TYPE = RenderType.entityTranslucentCull(WHITE);
    private static final RenderType EDGE_TYPE = RenderType.eyes(WHITE);
    /** px: the thinnest chain kept far (the rig's widths: docked 1.2, thrown 1.6). */
    static final double FAR_MIN_WIDTH = 1.5;
    /** px: a chain longer than this fades, down to 40% (sheet 4.4: beyond 3 blocks). */
    static final double FADE_LENGTH = 48.0;
    /**
     * Soul chains (in-game critic r2: glow hierarchy, the wheel as a sawblade): every chain 2 px wide in the world, its
     * links iron and rust (alpha 0.8), a glowing core half as wide down its middle at 0.7 (thrown 0.85).
     */
    static final double CHAIN_WIDTH = 2.0, CHAIN_CORE = 0.5;
    static final float CHAIN_IRON_ALPHA = 0.8f, CHAIN_GLOW = 0.7f, CHAIN_GLOW_THROWN = 0.85f;
    /**
     * The count ring (in-game critic r2: the dots sat on the hood like rivets, the unlit ones unseen; r3: the selected
     * unit's health bar hid it): a flat ring RING_LIFT px over the hood's crown, RING_R px round, ten RING_CUBE px cubes
     * 36 deg apart, turning RING_SPIN deg a tick. Lit: soul_glow, full; unlit: soul_dim at RING_DIM_ALPHA; each in a
     * RING_SHELL px dark shell, so the gaps read. Far, the cubes are RING_CUBE_FAR px.
     */
    static final float RING_LIFT = 7f, RING_R = 7f, RING_CUBE = 2.5f, RING_CUBE_FAR = 3f, RING_SHELL = 0.3f;
    static final float RING_SPIN = 0.6f, RING_DIM_ALPHA = 0.2f, RING_SHELL_ALPHA = 0.7f;
    /**
     * The count again on the ground (in-game critic r3: so it survives the health bar and a crowd): GROUND_R px round
     * the unit, ten NOTCH_LEN x NOTCH_W px notches turning with the ring; the first N lit (soul_glow at NOTCH_ALPHA,
     * additive), the rest dark (alpha blended at NOTCH_DARK_ALPHA).
     */
    static final float GROUND_R = 20f, NOTCH_LEN = 3f, NOTCH_W = 1.5f, NOTCH_ALPHA = 0.8f, NOTCH_DARK_ALPHA = 0.45f;
    /** The hood's crown in the head bone (TitheBinderModel: head box y -9..0, z -4..5). */
    static final float CROWN_Y = -9f, CROWN_Z = 0.5f;
    /**
     * Hover shadow (sheet 6; in-game critic r2): SHADOW_W x SHADOW_D px (side x front-back), wider than the robe (the
     * mantle is 14 x 8, the hem 11 x 9) so its core shows round the hem from the RTS camera; alpha 0.45, solid to
     * SHADOW_CORE of its radius, then fading to the rim; 10% smaller per px the hover rises (the rig's bob, +-2 px over
     * 60 ticks), bigger as it dips.
     */
    static final float SHADOW_W = 22f, SHADOW_D = 18f, SHADOW_ALPHA = 0.45f, SHADOW_LIFT = 0.25f, SHADOW_CORE = 0.75f;
    static final float SHADOW_PER_PX = 0.10f, SHADOW_MIN = 0.6f, SHADOW_MAX = 1.3f;
    /**
     * The hem chains drawn again as rods: 1.5 px wide, iron_rust (lighter than the robe and the shadow under it), 11 px
     * from the hem, their tips at least 5 px off the ground.
     */
    static final float HEM_ROD_WIDTH = 1.5f, HEM_ROD_LEN = 11f, HEM_GAP = 5f, HEM_ROD_ALPHA = 0.95f;
    /**
     * Far LOD: a flying weapon's cross-section (its stand-in is 2 x 1.2 px, x the flight scale 1.5) is drawn this much
     * thicker, so its thinnest side is >= 4 px at 21 px a block (1.2 x 1.5 x 1.7 = 3.06 model px = 4.0 screen px); its
     * length stays.
     */
    static final float FAR_FLY_THICK = 1.7f;
    /**
     * The volley's telegraph (sheet 6, attack): a 30-degree ground wedge toward the target over the clip's t0-12,
     * fading in over 3 ticks and out over the last 3. It starts past the wheel (the docked pommels ride at 18 px, the
     * blades reach ~30: a wedge from 8 px was under the wheel from the RTS camera, r2's 07zc) and runs toward the target
     * (36-80 px), solid to 65% of its length, then fading to its tip, its two sides lined.
     */
    static final int WEDGE_END = 12, WEDGE_SEGS = 4;
    static final double WEDGE_HALF_DEG = 15.0, WEDGE_R0 = 20.0, WEDGE_R1_MIN = 36.0, WEDGE_R1_MAX = 80.0;
    static final double WEDGE_SOLID = 0.65, WEDGE_EDGE = 1.0;
    static final float WEDGE_ALPHA = 0.5f;
    /**
     * Soul motes (in-game critic r2: no scattered glyph swirls): MOTES 1 px soul_glow specks, each rising MOTE_RISE px
     * over MOTE_LIFE ticks from MOTE_Y0 px, MOTE_R0-(R0 + SPAN) px out, fading in and out; never more than four.
     */
    static final int MOTES = 4;
    static final float MOTE_LIFE = 48f, MOTE_PX = 1f, MOTE_ALPHA = 0.8f;
    static final double MOTE_Y0 = 8.0, MOTE_RISE = 18.0, MOTE_R0 = 6.0, MOTE_R_SPAN = 8.0;
    /** The shadow's fan: 16 segments round. */
    private static final int SHADOW_SEGS = 16;
    private static final float[] SHADOW_COS = new float[SHADOW_SEGS + 1], SHADOW_SIN = new float[SHADOW_SEGS + 1];
    static {
        for (int i = 0; i <= SHADOW_SEGS; i++) {
            SHADOW_COS[i] = (float) Math.cos(2 * Math.PI * i / SHADOW_SEGS);
            SHADOW_SIN[i] = (float) Math.sin(2 * Math.PI * i / SHADOW_SEGS);
        }
    }
    private static final String[] HEM = {"hem_chain_1", "hem_chain_2", "hem_chain_3"};
    /** Where each hem chain's rod starts in its own bone (the links' centre line, model px). */
    private static final float[] HEM_TOP_Z = {-0.6f, -0.6f, 0.6f};
    /** The second segment's tip below its own pivot (the broken shackle's end, model px): the rod's direction. */
    private static final float HEM_TIP_Y = 3.9f;

    private final ModelPart[] headPath, lanternPath;
    private final ModelPart[][] hemPaths = new ModelPart[3][], hemTipPaths = new ModelPart[3][];

    // scratch for the effects pass (the render thread only)
    private VertexConsumer vc;
    private Matrix4f mat;
    private final Matrix4f partM = new Matrix4f();
    private final Quaternionf partQ = new Quaternionf();
    private final Vector3f view = new Vector3f(), right = new Vector3f(), up = new Vector3f(), pt = new Vector3f();
    private final Vector3f rodA = new Vector3f(), rodB = new Vector3f(), rodC = new Vector3f();
    private final float[] prevA = new float[3], prevB = new float[3];
    private final double[] edgeA = new double[3], edgeB = new double[3];
    // this frame's count ring: its centre (model space), radius and turn (radians)
    private float ringX, ringY, ringZ, ringR, ringTurn;

    public TitheBinderRenderer(EntityRendererProvider.Context context) {
        // no vanilla shadow (a 16 px disc the hem seemed to touch): FxLayer draws the hover's own
        super(context, new TitheBinderUnitModel(context.bakeLayer(TitheBinderModel.LAYER_LOCATION)), 0f);
        this.headPath = this.model.path("head");
        this.lanternPath = this.model.path("lantern_core");
        for (int i = 0; i < 3; i++) {
            this.hemPaths[i] = this.model.path(HEM[i]);
            this.hemTipPaths[i] = this.model.path(HEM[i] + "_b");
        }
        this.addLayer(new TeamColourLayer<>(this));
        // the docked weapons, in the value ladder's flat colours (the body's passes leave them out)
        this.addLayer(new WeaponLayer(this));
        // the generated body's glow type (mipmapped _glow, registered there), drawn by our own pass
        this.addLayer(new GlowLayer(this, GeneratedBodies.glow(this, NAME).renderType()));
        this.addLayer(new FxLayer(this));
    }

    @Override
    public ResourceLocation getTextureLocation(TitheBinderUnit entity) {
        return TEXTURE;
    }

    @Override
    public void render(TitheBinderUnit entity, float yaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers,
                       int light) {
        boolean near = GeneratedBodies.wantsDetail(entity);
        this.model.setDetail(near);
        this.model.setUpgrade("tithe_of_iron", false);
        this.model.frame(drive(entity), partialTick, !near);
        super.render(entity, yaw, partialTick, poseStack, buffers, light);
    }

    /** No sideways death flip: the rig's death clip lays the body down (sheet 5.6). */
    @Override
    protected float getFlipDegrees(TitheBinderUnit entity) {
        return 0f;
    }

    /**
     * The hover {@link #HOVER_LIFT} px higher: after vanilla's (-1, -1, 1) flip, -y is up. Everything the model and its
     * layers draw rides on it; the ground things take it back off.
     */
    @Override
    protected void scale(TitheBinderUnit entity, PoseStack pose, float partialTick) {
        pose.translate(0.0F, -HOVER_LIFT / 16.0F, 0.0F);
    }

    // ------------------------------------------------------------------ the drive

    /** The unit's drive, made on the first draw, stepped to the entity's tick (reads the unit only when it steps). */
    static TitheBinderDrive drive(TitheBinderUnit e) {
        TitheBinderDrive d = e.clientDrive;
        if (d == null) {
            d = new TitheBinderDrive(e.getId());
            e.clientDrive = d;
        }
        if (d.behind(e.tickCount)) {
            read(e, d.signals);
            d.catchUp(e.tickCount);
        }
        return d;
    }

    /** The unit as the drive's signals: forward ground speed, the count, the synced cues, hurt and death. */
    static void read(TitheBinderUnit e, TitheBinderDrive.Signals s) {
        double yaw = Math.toRadians(e.yBodyRot);
        double sin = Math.sin(yaw), cos = Math.cos(yaw);
        double dx = e.getX() - e.xo, dz = e.getZ() - e.zo;
        s.speed = Math.max(0.0, -dx * sin + dz * cos);      // along the facing (-sin, cos)
        s.yawRate = Mth.wrapDegrees(e.yBodyRot - e.yBodyRotO);   // + = turning to its right (the lantern lags)
        s.weapons = e.getWeapons();
        s.hurtTime = e.hurtTime;
        s.dead = e.isDeadOrDying();
        s.volleySeq = e.getVolleyCue();
        s.volleyHasTarget = toModel(e, e.getVolleyTargetId(), 0.6, sin, cos, s.volleyTarget);
        s.hookSeq = e.getHookCue();
        s.hookHasTarget = toModel(e, e.getHookTargetId(), 0.5, sin, cos, s.hookTarget);
    }

    /**
     * A unit's point ({@code heightFrac} of its height up) in the Binder's model space, DSL px (y up from the feet,
     * -z ahead): the inverse of the renderer's Ry(180 - yaw) * scale(-1, -1, 1) and its {@link #HOVER_LIFT}. False when
     * the client has no such unit.
     */
    static boolean toModel(TitheBinderUnit e, int id, double heightFrac, double sin, double cos, double[] out) {
        if (id < 0)
            return false;
        Entity t = e.level().getEntity(id);
        if (t == null)
            return false;
        double dx = t.getX() - e.getX(), dz = t.getZ() - e.getZ();
        out[0] = 16.0 * (dx * cos + dz * sin);
        out[1] = 16.0 * (t.getY() + t.getBbHeight() * heightFrac - e.getY()) - HOVER_LIFT;
        out[2] = 16.0 * (dx * sin - dz * cos);
        return true;
    }

    // ------------------------------------------------------------------ DSL px -> the layer's model space (blocks)

    private static float mx(double x) {
        return (float) (x / 16.0);
    }

    private static float my(double y) {
        return (float) ((24.0 - y) / 16.0);
    }

    private static float mz(double z) {
        return (float) (z / 16.0);
    }

    /** A point in a bone's own frame (model px, y down) -> model space, through the bone's posed path. */
    private void partPoint(ModelPart[] path, float lx, float ly, float lz, Vector3f out) {
        partM.identity();
        for (ModelPart p : path) {
            partM.translate(p.x / 16f, p.y / 16f, p.z / 16f);
            partM.rotate(partQ.rotationZYX(p.zRot, p.yRot, p.xRot));
            partM.scale(p.xScale, p.yScale, p.zScale);
        }
        partM.transformPosition(lx / 16f, ly / 16f, lz / 16f, out);
    }

    // ------------------------------------------------------------------ the glow pass

    /**
     * The body's light in place of GeneratedBodies.glow's pass (same render type, same SplitPasses pruning): the lantern
     * core at full (TitheBinderUnitModel.renderGlow) - in-game critic r2's glow hierarchy: the lantern is the one strong
     * body glow. The weapons' light is their shell (WeaponLayer).
     */
    static final class GlowLayer extends RenderLayer<TitheBinderUnit, TitheBinderUnitModel> {
        private final RenderType type;

        GlowLayer(RenderLayerParent<TitheBinderUnit, TitheBinderUnitModel> parent, RenderType type) {
            super(parent);
            this.type = type;
        }

        @Override
        public void render(PoseStack pose, MultiBufferSource buffers, int light, TitheBinderUnit entity, float limbSwing,
                           float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw,
                           float headPitch) {
            if (UnitLod.skipOverlay(entity))   // shader shadow map: the body pass already casts this silhouette
                return;
            TitheBinderUnitModel m = this.getParentModel();
            VertexConsumer glow = buffers.getBuffer(type);
            List<ModelPart> keep = m.glowParts();
            m.beginPass(keep);
            try {
                m.renderGlow(pose, glow);
            } finally {
                m.endPass(keep);
            }
        }
    }

    /**
     * The docked weapons in the value ladder (in-game critic r3): the hook in slot 1 solid iron_rust, slots 2-10
     * soul_core at {@link #BOUND_ALPHA}, each in a soul_glow shell at full. After the team pass (whose endPass gave every
     * cube back: the far stand-ins are chosen again here), before the glow. In a shader pack's shadow map only the
     * solid hook (the soul weapons cast nothing).
     */
    final class WeaponLayer extends RenderLayer<TitheBinderUnit, TitheBinderUnitModel> {
        WeaponLayer(RenderLayerParent<TitheBinderUnit, TitheBinderUnitModel> parent) {
            super(parent);
        }

        @Override
        public void render(PoseStack pose, MultiBufferSource buffers, int light, TitheBinderUnit entity, float limbSwing,
                           float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw,
                           float headPitch) {
            if (entity.isInvisible())
                return;
            TitheBinderUnitModel m = this.getParentModel();
            boolean far = !GeneratedBodies.wantsDetail(entity);
            for (int k = 1; k <= TitheBinderRig.MAX_WEAPONS; k++)
                m.weaponLod(k, far);
            TitheBinderDrive d = entity.clientDrive;
            float body = d != null && d.ready() ? (float) Math.max(0.0, Math.min(1.0, d.blended().alpha)) : 1f;
            m.renderDocked(1, pose, buffers.getBuffer(HOOK_TYPE), light, r(IRON_RUST), g(IRON_RUST), b(IRON_RUST), 1f,
                1f, 1f, 1f);
            if (UnitLod.skipOverlay(entity) || body < 0.01f)
                return;
            VertexConsumer bound = buffers.getBuffer(BOUND_TYPE);
            for (int k = 2; k <= TitheBinderRig.MAX_WEAPONS; k++)
                m.renderDocked(k, pose, bound, LightTexture.FULL_BRIGHT, r(SOUL_CORE), g(SOUL_CORE), b(SOUL_CORE),
                    BOUND_ALPHA * body, 1f, 1f, 1f);
            VertexConsumer edge = buffers.getBuffer(EDGE_TYPE);
            for (int k = 1; k <= TitheBinderRig.MAX_WEAPONS; k++) {
                ModelPart w = m.weapon(k);
                if (w == null)
                    continue;
                float fx = TitheBinderUnitModel.edgeX(k, far, w.xScale), fy = TitheBinderUnitModel.edgeY(k, far, w.yScale);
                m.renderDocked(k, pose, edge, LightTexture.FULL_BRIGHT, r(SOUL_GLOW) * body, g(SOUL_GLOW) * body,
                    b(SOUL_GLOW) * body, 1f, fx, -fy, TitheBinderUnitModel.EDGE_Z);
            }
        }
    }

    private static float r(int rgb) {
        return ((rgb >> 16) & 255) / 255f;
    }

    private static float g(int rgb) {
        return ((rgb >> 8) & 255) / 255f;
    }

    private static float b(int rgb) {
        return (rgb & 255) / 255f;
    }

    // ------------------------------------------------------------------ the effects

    /** Flying weapons and the rig's light, after the body and its team / glow passes. */
    final class FxLayer extends RenderLayer<TitheBinderUnit, TitheBinderUnitModel> {
        FxLayer(RenderLayerParent<TitheBinderUnit, TitheBinderUnitModel> parent) {
            super(parent);
        }

        @Override
        public void render(PoseStack pose, MultiBufferSource buffers, int light, TitheBinderUnit entity, float limbSwing,
                           float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw,
                           float headPitch) {
            TitheBinderDrive d = entity.clientDrive;
            if (d == null || !d.ready() || entity.isInvisible() || UnitLod.skipOverlay(entity))
                return;
            TitheBinderRig.Pose p = d.blended();
            boolean far = !GeneratedBodies.wantsDetail(entity);
            flyingWeapons(pose, buffers, light, p, far);
            camera(entity, partialTick);
            mat = pose.last().pose();
            float body = (float) Math.max(0.0, Math.min(1.0, p.alpha));
            placeRing(p, ageInTicks, far);
            // the dark things first, alpha blended (BarFx's scorch-decal type)
            vc = buffers.getBuffer(BarFxRenderTypes.TRANSLUCENT);
            shadow(SHADOW_ALPHA * body, shadowScale(d.hoverPx()));
            hemRods(body);
            chainLinks(p, far, body);
            ringDark(p, far, body);
            notches(lit(p), 10, SHADOW, NOTCH_DARK_ALPHA * body);
            // then the light, additive (switching the buffer draws the dark batch first)
            vc = buffers.getBuffer(BarFxRenderTypes.ADDITIVE);
            wedge(d);
            chainGlow(p, far);
            ringLit(p, far);
            notches(0, lit(p), SOUL_GLOW, NOTCH_ALPHA * body);
            lantern(p, ageInTicks);
            impacts(p, far);
            bind(p, far);
            if (!far) {
                streaks(p);
                if (!entity.isDeadOrDying())
                    motes(entity.getId(), ageInTicks, body);
            }
            vc = null;
            mat = null;
        }
    }

    private static boolean drawn(int flyState) {
        return flyState != TitheBinderRig.FLY_NONE && flyState != TitheBinderRig.FLY_SHATTER;
    }

    /**
     * Every weapon in the air (or falling) where the rig says, in the value ladder as on the wheel (WeaponLayer): the
     * hook solid iron_rust, the rest soul_core at BOUND_ALPHA, each in its soul_glow shell.
     */
    private void flyingWeapons(PoseStack pose, MultiBufferSource buffers, int light, TitheBinderRig.Pose p, boolean far) {
        boolean any = false;
        for (int k = 1; k <= 10 && !any; k++)
            any = drawn(p.flyState[k]) && this.model.weapon(k) != null;
        if (!any)
            return;
        for (int k = 1; k <= 10; k++) {
            ModelPart w = this.model.weapon(k);
            if (w == null || !drawn(p.flyState[k]))
                continue;
            double[] at = p.flyPos[k], rot = p.flyRot[k];
            w.x = (float) at[0];                     // the pommel, model px (y down from 24 = the feet)
            w.y = (float) (24.0 - at[1]);
            w.z = (float) at[2];
            w.xRot = (float) Math.toRadians(-rot[0]);
            w.yRot = (float) Math.toRadians(rot[1]);
            w.zRot = (float) Math.toRadians(-rot[2]);
            float s = (float) p.flyScale[k];
            float thick = far ? s * FAR_FLY_THICK : s;   // far: the stand-in's cross-section >= 4 px, its length as is
            w.xScale = thick;
            w.yScale = thick;
            w.zScale = s;
            w.visible = true;
            this.model.weaponLod(k, far);
        }
        float body = (float) Math.max(0.0, Math.min(1.0, p.alpha));
        ModelPart hook = this.model.weapon(1);
        if (hook != null && drawn(p.flyState[1]))
            hook.render(pose, buffers.getBuffer(HOOK_TYPE), light, OverlayTexture.NO_OVERLAY, r(IRON_RUST), g(IRON_RUST),
                b(IRON_RUST), 1f);
        if (body < 0.01f)
            return;
        VertexConsumer bound = buffers.getBuffer(BOUND_TYPE);
        for (int k = 2; k <= 10; k++) {
            ModelPart w = this.model.weapon(k);
            if (w != null && drawn(p.flyState[k]))
                w.render(pose, bound, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, r(SOUL_CORE), g(SOUL_CORE),
                    b(SOUL_CORE), BOUND_ALPHA * body);
        }
        VertexConsumer edge = buffers.getBuffer(EDGE_TYPE);
        for (int k = 1; k <= 10; k++) {
            ModelPart w = this.model.weapon(k);
            if (w == null || !drawn(p.flyState[k]))
                continue;
            float sx = w.xScale, sy = w.yScale, sz = w.zScale;
            w.xScale = sx * TitheBinderUnitModel.edgeX(k, far, sx);
            w.yScale = -sy * TitheBinderUnitModel.edgeY(k, far, sy);
            w.zScale = sz * TitheBinderUnitModel.EDGE_Z;
            w.render(pose, edge, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, r(SOUL_GLOW) * body,
                g(SOUL_GLOW) * body, b(SOUL_GLOW) * body, 1f);
            w.xScale = sx;
            w.yScale = sy;
            w.zScale = sz;
        }
    }

    /** The camera's axes in this unit's model space: world (x, y, z) -> (x c + z s, -y, x s - z c), yaw = body yaw. */
    private void camera(TitheBinderUnit e, float partialTick) {
        Camera cam = Minecraft.getInstance().gameRenderer.getMainCamera();
        float yaw = Mth.rotLerp(partialTick, e.yBodyRotO, e.yBodyRot) * Mth.DEG_TO_RAD;
        float c = Mth.cos(yaw), s = Mth.sin(yaw);
        Vector3f look = cam.getLookVector(), left = cam.getLeftVector(), u = cam.getUpVector();
        view.set(-(look.x() * c + look.z() * s), look.y(), -(look.x() * s - look.z() * c));   // toward the camera
        right.set(-(left.x() * c + left.z() * s), left.y(), -(left.x() * s - left.z() * c));
        up.set(u.x() * c + u.z() * s, -u.y(), u.x() * s - u.z() * c);
    }

    // ------------------------------------------------------------------ the soul chains

    /** The chain's alpha share: one beyond FADE_LENGTH fades, down to 40% (sheet 4.4). */
    private static float fade(double[] a, double[] b) {
        double len = Math.sqrt(sq(b[0] - a[0]) + sq(b[1] - a[1]) + sq(b[2] - a[2]));
        return (float) (len > FADE_LENGTH ? Math.max(0.4, FADE_LENGTH / len) : 1.0);
    }

    /** The chains' iron: CHAIN_WIDTH px wide, links alternately iron and rust; far, the thin docked ones go. */
    private void chainLinks(TitheBinderRig.Pose p, boolean far, float body) {
        for (int i = 0; i < p.ribbons; i++) {
            double w = p.ribbonWidth[i];
            if (far && w < FAR_MIN_WIDTH)
                continue;
            boolean thrown = w > 1.3;
            double[] a = p.ribbonHub[i], b = p.ribbonEnd[i];
            float alpha = CHAIN_IRON_ALPHA * fade(a, b) * body;
            ribbon(a, p.ribbonCtrl[i], b, Math.max(w, CHAIN_WIDTH), thrown ? 8 : 6, IRON, RUST, alpha);
        }
    }

    /** The chains' light: a soul_glow core half as wide down each chain, 0.7 (thrown 0.85). */
    private void chainGlow(TitheBinderRig.Pose p, boolean far) {
        for (int i = 0; i < p.ribbons; i++) {
            double w = p.ribbonWidth[i];
            if (far && w < FAR_MIN_WIDTH)
                continue;
            boolean thrown = w > 1.3;
            double[] a = p.ribbonHub[i], b = p.ribbonEnd[i];
            float alpha = (thrown ? CHAIN_GLOW_THROWN : CHAIN_GLOW) * fade(a, b);
            ribbon(a, p.ribbonCtrl[i], b, Math.max(w, CHAIN_WIDTH) * CHAIN_CORE, thrown ? 8 : 6, SOUL_GLOW, SOUL_GLOW,
                alpha);
        }
    }

    // ------------------------------------------------------------------ the count ring

    /** This frame's ring: RING_LIFT px over the crown (the head's posed path), level, turning RING_SPIN deg a tick. */
    private void placeRing(TitheBinderRig.Pose p, float ageInTicks, boolean far) {
        partPoint(headPath, 0f, CROWN_Y - RING_LIFT, CROWN_Z, pt);
        ringX = pt.x();
        ringY = pt.y() + (float) (p.haloDip / 16.0);
        ringZ = pt.z();
        ringR = RING_R / 16f;
        ringTurn = (ageInTicks * RING_SPIN % 360f) * Mth.DEG_TO_RAD;
    }

    /** Cube i's centre on the ring, into {@link #pt}. */
    private void ringCube(int i) {
        float th = ringTurn + i * 36f * Mth.DEG_TO_RAD;
        pt.set(ringX - Mth.sin(th) * ringR, ringY, ringZ - Mth.cos(th) * ringR);
    }

    /** Lit cubes: the first Pose.halo of the ten (N, less the weapons shattered at death). */
    private static int lit(TitheBinderRig.Pose p) {
        return Math.max(0, Math.min(10, p.halo));
    }

    /** Alpha blended: every cube's dark shell, then the unlit cubes in soul_dim. */
    private void ringDark(TitheBinderRig.Pose p, boolean far, float body) {
        if (body < 0.01f)
            return;
        float h = (far ? RING_CUBE_FAR : RING_CUBE) / 32f, hs = h + RING_SHELL / 16f;
        for (int i = 0; i < 10; i++) {
            ringCube(i);
            cube(pt.x(), pt.y(), pt.z(), hs, SHADOW, RING_SHELL_ALPHA * body);
        }
        for (int i = lit(p); i < 10; i++) {
            ringCube(i);
            cube(pt.x(), pt.y(), pt.z(), h, SOUL_DIM, RING_DIM_ALPHA * body);
        }
    }

    /** Additive: the lit cubes, soul_glow at full. */
    private void ringLit(TitheBinderRig.Pose p, boolean far) {
        float h = (far ? RING_CUBE_FAR : RING_CUBE) / 32f;
        for (int i = 0, n = lit(p); i < n; i++) {
            ringCube(i);
            cube(pt.x(), pt.y(), pt.z(), h, SOUL_GLOW, 1f);
        }
    }

    /**
     * The count's ground ring: notches {@code from} to {@code to - 1} of the ten, GROUND_R px round the unit's foot on
     * the ground, each NOTCH_LEN px along the ring and NOTCH_W px across it, turning with the floating ring.
     */
    private void notches(int from, int to, int rgb, float a) {
        if (a < 0.01f || from >= to)
            return;
        float y = my(SHADOW_LIFT + 0.05f - HOVER_LIFT), rr = GROUND_R / 16f, hl = NOTCH_LEN / 32f, hw = NOTCH_W / 32f;
        for (int i = from; i < to; i++) {
            float th = ringTurn + i * 36f * Mth.DEG_TO_RAD;
            float s = Mth.sin(th), c = Mth.cos(th);
            float cx = -s * rr, cz = -c * rr;                          // as ringCube: the ring's point at th
            float tx = -c * hl, tz = s * hl, rx = -s * hw, rz = -c * hw;  // along the ring, and out from it
            v(cx - tx - rx, y, cz - tz - rz, rgb, a);
            v(cx + tx - rx, y, cz + tz - rz, rgb, a);
            v(cx + tx + rx, y, cz + tz + rz, rgb, a);
            v(cx - tx + rx, y, cz - tz + rz, rgb, a);
        }
    }

    // ------------------------------------------------------------------ the ground: shadow, hem rods, wedge

    /** The shadow's size for the hover's lift (px over its rest): 10% smaller per px up, bigger as it dips. */
    static float shadowScale(double hoverPx) {
        return (float) Math.max(SHADOW_MIN, Math.min(SHADOW_MAX, 1.0 - SHADOW_PER_PX * hoverPx));
    }

    /**
     * The hover's shadow (sheet 6): a soft dark ellipse, SHADOW_W x SHADOW_D px x {@code scale}, under the unit's
     * origin a hair above the ground. Drawn as BarFxClient.drawDecal draws a scorch mark: a fan, solid to SHADOW_CORE of
     * the radius, then a fringe fading to nothing at the rim.
     */
    private void shadow(float a, float scale) {
        if (a < 0.01f)
            return;
        float y = my(SHADOW_LIFT - HOVER_LIFT), rx = SHADOW_W / 32f * scale, rz = SHADOW_D / 32f * scale, k = SHADOW_CORE;
        for (int i = 0; i < SHADOW_SEGS; i++) {
            float c0 = SHADOW_COS[i], s0 = SHADOW_SIN[i], c1 = SHADOW_COS[i + 1], s1 = SHADOW_SIN[i + 1];
            float ix0 = c0 * rx * k, iz0 = s0 * rz * k, ix1 = c1 * rx * k, iz1 = s1 * rz * k;
            v(0f, y, 0f, SHADOW, a);                                     // the core (a fan quad, centre twice)
            v(ix0, y, iz0, SHADOW, a * 0.9f);
            v(ix1, y, iz1, SHADOW, a * 0.9f);
            v(0f, y, 0f, SHADOW, a);
            v(ix0, y, iz0, SHADOW, a * 0.9f);                            // the soft fringe
            v(c0 * rx, y, s0 * rz, SHADOW, 0f);
            v(c1 * rx, y, s1 * rz, SHADOW, 0f);
            v(ix1, y, iz1, SHADOW, a * 0.9f);
        }
    }

    /**
     * The three hem chains again as rods, HEM_ROD_WIDTH px: from where they leave the hem through the posed first link
     * (they swing with their springs), then on along the second link's line to HEM_ROD_LEN px in all, iron_rust; a tip
     * never comes nearer the ground than HEM_GAP px, so the hover gap stays open under them (sheet 2.1).
     */
    private void hemRods(float body) {
        float a = HEM_ROD_ALPHA * body;
        if (a < 0.01f)
            return;
        float hw = HEM_ROD_WIDTH / 32f, len = HEM_ROD_LEN / 16f, cap = my(HEM_GAP - HOVER_LIFT);
        for (int i = 0; i < 3; i++) {
            if (hemPaths[i].length == 0 || hemTipPaths[i].length == 0)
                continue;
            partPoint(hemPaths[i], 0f, 0f, HEM_TOP_Z[i], rodA);
            partPoint(hemTipPaths[i], 0f, 0f, 0f, rodB);
            partPoint(hemTipPaths[i], 0f, HEM_TIP_Y, 0f, rodC);
            float ax = rodB.x() - rodA.x(), ay = rodB.y() - rodA.y(), az = rodB.z() - rodA.z();
            float l1 = (float) Math.sqrt(ax * ax + ay * ay + az * az);
            float dx = rodC.x() - rodB.x(), dy = rodC.y() - rodB.y(), dz = rodC.z() - rodB.z();
            float n = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (n < 1e-6f) {
                dx = 0f;
                dy = 1f;                                                 // straight down (model y is down)
                dz = 0f;
            } else {
                dx /= n;
                dy /= n;
                dz /= n;
            }
            float rest = Math.max(0f, len - l1);
            if (dy > 1e-4f && rodB.y() + dy * rest > cap)
                rest = Math.max(0f, (cap - rodB.y()) / dy);
            rodC.set(rodB.x() + dx * rest, rodB.y() + dy * rest, rodB.z() + dz * rest);
            rod(rodA, rodB, hw, IRON_RUST, IRON_RUST, a);
            if (rest > 1e-4f)
                rod(rodB, rodC, hw, IRON_RUST, IRON_RUST, a);
        }
    }

    /**
     * The volley's telegraph: over the clip's t0-WEDGE_END a 30-degree wedge on the ground toward the target (the
     * victim's chest as the client sees it this tick, else straight ahead), fading in over 3 ticks and out over the last
     * 3, from past the wheel toward the target, its two sides lined.
     */
    private void wedge(TitheBinderDrive d) {
        if (d.clip() != TitheBinderRig.Clip.VOLLEY)
            return;
        int t = d.clipTick() - 1;                                      // the tick the newest pose was stepped at
        if (t < 0 || t > WEDGE_END)
            return;
        float env = Math.min(1f, (t + 1) / 3f) * Math.min(1f, (WEDGE_END + 1 - t) / 3f);
        float a = WEDGE_ALPHA * env;
        TitheBinderDrive.Signals s = d.signals;
        double tx = s.volleyHasTarget ? s.volleyTarget[0] : 0.0, tz = s.volleyHasTarget ? s.volleyTarget[2] : -1.0;
        double len = Math.sqrt(tx * tx + tz * tz);
        if (len < 1e-6) {
            tx = 0.0;
            tz = -1.0;
            len = 1.0;
        }
        double dx = tx / len, dz = tz / len;
        double r1 = Math.max(WEDGE_R1_MIN, Math.min(WEDGE_R1_MAX, len - 4.0));
        double rm = WEDGE_R0 + WEDGE_SOLID * (r1 - WEDGE_R0);
        float y = my(SHADOW_LIFT + 0.1f - HOVER_LIFT);
        double step = Math.toRadians(2.0 * WEDGE_HALF_DEG / WEDGE_SEGS), phi0 = -Math.toRadians(WEDGE_HALF_DEG);
        for (int i = 0; i < WEDGE_SEGS; i++) {
            double pa = phi0 + step * i, pb = pa + step;
            double ca = Math.cos(pa), sa = Math.sin(pa), cb = Math.cos(pb), sb = Math.sin(pb);
            double ax = dx * ca - dz * sa, az = dx * sa + dz * ca, bx = dx * cb - dz * sb, bz = dx * sb + dz * cb;
            v(mx(ax * WEDGE_R0), y, mz(az * WEDGE_R0), SOUL_GLOW, a);     // solid part
            v(mx(ax * rm), y, mz(az * rm), SOUL_GLOW, a);
            v(mx(bx * rm), y, mz(bz * rm), SOUL_GLOW, a);
            v(mx(bx * WEDGE_R0), y, mz(bz * WEDGE_R0), SOUL_GLOW, a);
            v(mx(ax * rm), y, mz(az * rm), SOUL_GLOW, a);                 // the tip, fading
            v(mx(ax * r1), y, mz(az * r1), SOUL_GLOW, 0f);
            v(mx(bx * r1), y, mz(bz * r1), SOUL_GLOW, 0f);
            v(mx(bx * rm), y, mz(bz * rm), SOUL_GLOW, a);
        }
        for (int side = -1; side <= 1; side += 2) {                    // the two sides, lined
            double ph = side * Math.toRadians(WEDGE_HALF_DEG), c = Math.cos(ph), sn = Math.sin(ph);
            double ex = dx * c - dz * sn, ez = dx * sn + dz * c;
            edgeA[0] = ex * WEDGE_R0;
            edgeA[1] = SHADOW_LIFT + 0.1 - HOVER_LIFT;
            edgeA[2] = ez * WEDGE_R0;
            edgeB[0] = ex * rm;
            edgeB[1] = SHADOW_LIFT + 0.1 - HOVER_LIFT;
            edgeB[2] = ez * rm;
            segment(edgeA, edgeB, WEDGE_EDGE, SOUL_GLOW, Math.min(1f, a * 1.6f));
        }
    }

    // ------------------------------------------------------------------ the light

    /** The lantern's glow round its core: the rig's 0..2.5, pulsing 0.85-1.0 over 40 ticks (sheet 6). */
    private void lantern(TitheBinderRig.Pose p, float ageInTicks) {
        if (p.lanternCoreHidden || p.lantern <= 0.05)
            return;
        partPoint(lanternPath, 0f, 3f, 0f, pt);
        float g = (float) p.lantern * (0.925f + 0.075f * Mth.sin(ageInTicks * (float) (Math.PI * 2.0) / 40f));
        billboard(pt.x(), pt.y(), pt.z(), (2.0f + 1.6f * g) / 16f, SOUL_GLOW, Math.min(0.6f, 0.22f * g));
        billboard(pt.x(), pt.y(), pt.z(), (0.9f + 0.5f * g) / 16f, g > 1.5f ? WHITE_HOT : SOUL_GLOW, Math.min(0.9f, 0.4f * g));
    }

    /** Impact flashes (6 px, white-hot to teal over 4 ticks); far, the first one only (sheet 6.1's flash budget). */
    private void impacts(TitheBinderRig.Pose p, boolean far) {
        int n = far ? Math.min(1, p.impacts) : p.impacts;
        for (int i = 0; i < n; i++) {
            double[] at = p.impactPos[i];
            int age = p.impactAge[i];
            float a = 1f - age / 4f;
            billboard(mx(at[0]), my(at[1]), mz(at[2]), (3f + age) / 16f, age == 0 ? WHITE_HOT : SOUL_GLOW, a);
            billboard(mx(at[0]), my(at[1]), mz(at[2]), 1.5f / 16f, WHITE_HOT, a);
        }
        if (far)
            return;
        for (int k = 1; k <= TitheBinderRig.MAX_WEAPONS; k++)      // a weapon shattering at death: a 4 px spark
            if (p.flyState[k] == TitheBinderRig.FLY_SHATTER) {
                double[] at = p.flyPos[k];
                billboard(mx(at[0]), my(at[1]), mz(at[2]), 2f / 16f, SOUL_GLOW, 0.8f);
            }
    }

    /** The bind (sheet 5.4): the lash and the forge line (near only), the soul orb and the lantern's burst. */
    private void bind(TitheBinderRig.Pose p, boolean far) {
        if (p.lashOn && !far)
            ribbon(p.lashA, p.lashCtrl, p.lashB, 1.2, 6, SOUL_GLOW, SOUL_GLOW, 0.8f);
        if (p.forgeOn && !far)
            ribbon(p.forgeA, p.forgeA, p.forgeB, 1.0, 2, WHITE_HOT, WHITE_HOT, 0.7f);
        if (p.orbOn) {
            billboard(mx(p.orb[0]), my(p.orb[1]), mz(p.orb[2]), 3f / 16f, SOUL_GLOW, 0.7f);
            billboard(mx(p.orb[0]), my(p.orb[1]), mz(p.orb[2]), 1.4f / 16f, WHITE_HOT, 0.9f);
        }
        if (p.flashOn)
            billboard(mx(p.flash[0]), my(p.flash[1]), mz(p.flash[2]), 5f / 16f, WHITE_HOT, 0.8f);
    }

    /** The streak behind a flying or reeling weapon: its last positions to now, fading toward the oldest. */
    private void streaks(TitheBinderRig.Pose p) {
        for (int k = 1; k <= TitheBinderRig.MAX_WEAPONS; k++) {
            int n = p.flyTrailN[k];
            if (n == 0 || !drawn(p.flyState[k]))
                continue;
            for (int j = 0; j < n; j++) {
                double[] a = p.flyTrail[k][j], b = j + 1 < n ? p.flyTrail[k][j + 1] : p.flyPos[k];
                float fa = 0.15f + 0.35f * (j + 1) / n;
                segment(a, b, 0.9 * (j + 1) / n + 0.3, SOUL_CORE, fa);
            }
        }
    }

    /**
     * The soul motes (in-game critic r2, in place of scattered swirls): MOTES 1 px specks round the wheel, each rising
     * over MOTE_LIFE ticks and fading in and out, then reborn elsewhere (a hash of the unit, the mote and its life):
     * never more than four, staggered so about three show at once.
     */
    private void motes(int id, float ageInTicks, float body) {
        for (int i = 0; i < MOTES; i++) {
            float t = ageInTicks + i * (MOTE_LIFE / MOTES) + (id & 31);
            int life = (int) Math.floor(t / MOTE_LIFE);
            float u = t / MOTE_LIFE - life;
            int h = hash(id * 31 + i * 7 + life * 131);
            double ang = (h & 1023) / 1024.0 * 2.0 * Math.PI;
            double r = MOTE_R0 + ((h >>> 10) & 255) / 255.0 * MOTE_R_SPAN;
            float a = Mth.sin(u * (float) Math.PI) * MOTE_ALPHA * body;
            if (a < 0.02f)
                continue;
            billboard(mx(-Math.sin(ang) * r), my(MOTE_Y0 + MOTE_RISE * u), mz(-Math.cos(ang) * r), MOTE_PX / 32f,
                SOUL_GLOW, a);
        }
    }

    /** An integer hash (lowbias32): the motes' places, no Random per frame. */
    static int hash(int x) {
        x ^= x >>> 16;
        x *= 0x7feb352d;
        x ^= x >>> 15;
        x *= 0x846ca68b;
        x ^= x >>> 16;
        return x;
    }

    // ------------------------------------------------------------------ primitives (model space)

    private static double sq(double x) {
        return x * x;
    }

    private void v(float x, float y, float z, int rgb, float a) {
        vc.vertex(mat, x, y, z)
            .color(((rgb >> 16) & 255) / 255f, ((rgb >> 8) & 255) / 255f, (rgb & 255) / 255f, Math.max(0f, Math.min(1f, a)))
            .endVertex();
    }

    /** A camera-facing square of half-size {@code h} (model units). */
    private void billboard(float x, float y, float z, float h, int rgb, float a) {
        float rx = right.x() * h, ry = right.y() * h, rz = right.z() * h;
        float ux = up.x() * h, uy = up.y() * h, uz = up.z() * h;
        v(x - rx - ux, y - ry - uy, z - rz - uz, rgb, a);
        v(x + rx - ux, y + ry - uy, z + rz - uz, rgb, a);
        v(x + rx + ux, y + ry + uy, z + rz + uz, rgb, a);
        v(x - rx + ux, y - ry + uy, z - rz + uz, rgb, a);
    }

    /**
     * An axis-aligned cube of half-size {@code h} (model units): only its three faces toward the camera, so a blended
     * cube is never laid over itself; the top full, the sides at 75% (it reads as a cube, not a square).
     */
    private void cube(float x, float y, float z, float h, int rgb, float a) {
        float sx = view.x() >= 0f ? h : -h, sy = view.y() >= 0f ? h : -h, sz = view.z() >= 0f ? h : -h;
        float side = a * 0.75f;
        v(x - h, y + sy, z - h, rgb, a);                               // top / bottom (model y is down)
        v(x + h, y + sy, z - h, rgb, a);
        v(x + h, y + sy, z + h, rgb, a);
        v(x - h, y + sy, z + h, rgb, a);
        v(x + sx, y - h, z - h, rgb, side);                            // the side toward the camera in x
        v(x + sx, y + h, z - h, rgb, side);
        v(x + sx, y + h, z + h, rgb, side);
        v(x + sx, y - h, z + h, rgb, side);
        v(x - h, y - h, z + sz, rgb, side);                            // and in z
        v(x + h, y - h, z + sz, rgb, side);
        v(x + h, y + h, z + sz, rgb, side);
        v(x - h, y + h, z + sz, rgb, side);
    }

    /** The half-width side vector of a strip along (tx, ty, tz) (model space), facing the camera, into {@code out}. */
    private void side(float tx, float ty, float tz, float hw, float[] out) {
        float sx = ty * view.z() - tz * view.y(), sy = tz * view.x() - tx * view.z(), sz = tx * view.y() - ty * view.x();
        float n = (float) Math.sqrt(sx * sx + sy * sy + sz * sz);
        if (n < 1e-6f) {
            out[0] = right.x() * hw;
            out[1] = right.y() * hw;
            out[2] = right.z() * hw;
            return;
        }
        out[0] = sx / n * hw;
        out[1] = sy / n * hw;
        out[2] = sz / n * hw;
    }

    /**
     * A quadratic Bezier ribbon a -> b through control c (DSL px), {@code widthPx} wide, in {@code segs} quads, the odd
     * ones {@code rgb} and the even ones {@code rgb2} (a chain's links).
     */
    private void ribbon(double[] a, double[] c, double[] b, double widthPx, int segs, int rgb, int rgb2, float alpha) {
        float hw = (float) (widthPx / 32.0);
        float px = 0, py = 0, pz = 0;
        for (int i = 0; i <= segs; i++) {
            double u = i / (double) segs, iu = 1.0 - u;
            float x = mx(iu * iu * a[0] + 2 * iu * u * c[0] + u * u * b[0]);
            float y = my(iu * iu * a[1] + 2 * iu * u * c[1] + u * u * b[1]);
            float z = mz(iu * iu * a[2] + 2 * iu * u * c[2] + u * u * b[2]);
            float tx = (float) (2 * iu * (c[0] - a[0]) + 2 * u * (b[0] - c[0]));
            float ty = (float) -(2 * iu * (c[1] - a[1]) + 2 * u * (b[1] - c[1]));
            float tz = (float) (2 * iu * (c[2] - a[2]) + 2 * u * (b[2] - c[2]));
            side(tx, ty, tz, hw, prevB);
            if (i > 0) {
                int col = (i & 1) == 1 ? rgb : rgb2;
                v(px - prevA[0], py - prevA[1], pz - prevA[2], col, alpha);
                v(px + prevA[0], py + prevA[1], pz + prevA[2], col, alpha);
                v(x + prevB[0], y + prevB[1], z + prevB[2], col, alpha);
                v(x - prevB[0], y - prevB[1], z - prevB[2], col, alpha);
            }
            prevA[0] = prevB[0];
            prevA[1] = prevB[1];
            prevA[2] = prevB[2];
            px = x;
            py = y;
            pz = z;
        }
    }

    /** One straight camera-facing strip a -> b, already in model space (blocks), {@code hw} half wide, rgbA -> rgbB. */
    private void rod(Vector3f a, Vector3f b, float hw, int rgbA, int rgbB, float alpha) {
        side(b.x() - a.x(), b.y() - a.y(), b.z() - a.z(), hw, prevA);
        v(a.x() - prevA[0], a.y() - prevA[1], a.z() - prevA[2], rgbA, alpha);
        v(a.x() + prevA[0], a.y() + prevA[1], a.z() + prevA[2], rgbA, alpha);
        v(b.x() + prevA[0], b.y() + prevA[1], b.z() + prevA[2], rgbB, alpha);
        v(b.x() - prevA[0], b.y() - prevA[1], b.z() - prevA[2], rgbB, alpha);
    }

    /** One straight strip a -> b (DSL px). */
    private void segment(double[] a, double[] b, double widthPx, int rgb, float alpha) {
        float hw = (float) (widthPx / 32.0);
        float ax = mx(a[0]), ay = my(a[1]), az = mz(a[2]), bx = mx(b[0]), by = my(b[1]), bz = mz(b[2]);
        side(bx - ax, by - ay, bz - az, hw, prevA);
        v(ax - prevA[0], ay - prevA[1], az - prevA[2], rgb, alpha);
        v(ax + prevA[0], ay + prevA[1], az + prevA[2], rgb, alpha);
        v(bx + prevA[0], by + prevA[1], bz + prevA[2], rgb, alpha);
        v(bx - prevA[0], by - prevA[1], bz - prevA[2], rgb, alpha);
    }
}
