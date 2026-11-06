package com.solegendary.reignofnether.unit.modelling.renderers;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.solegendary.reignofnether.ReignOfNether;
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

/**
 * The Gravebound Tithe Binder (tithe_binder_unit): the generated body (TitheBinderModel; tools/gen_tithe_binder.py ->
 * tools/dsl_to_game.py) moved by its rig, team colour on the hood and mantle, the lantern core and the weapons' edges
 * drawn again full-bright (the _glow pass). The Tithe of Iron upgrade's cubes stay hidden (no research yet).
 *
 * <p>The rig: each unit carries a {@link TitheBinderDrive} (TitheBinderUnit.clientDrive, made here on the first draw).
 * {@link #render} reads the unit into the drive's signals and steps it to the entity's tickCount (once per game tick;
 * a second draw in the same tick, such as a shader pack's shadow pass, steps nothing); the model lerps the last two
 * poses by partialTick. Vanilla's sideways death flip is off: the rig plays its own death (sheet 5.6).
 *
 * <p>{@link FxLayer}, after the body, draws what is not bone-tree: the flying weapons (the volley's, the falling ones at
 * death, the Grave Hook) from the pose's fly* fields; then, alpha blended and untextured (BarFxRenderTypes, no new
 * shader), the hover's own ground shadow (a soft 14 x 12 px ellipse; vanilla's 16 px disc is off) and the hem chains as
 * dark rods into the hover gap; then the light, additive: the volley's telegraph wedge on the ground, the soul-chain
 * ribbons hub -> weapon, the count halo over the hood (ten dots, exactly N bright, its ring growing with N), the impact
 * flashes, the lantern's glow, the bind's lash, orb, flash and forge line, the flight streaks and the shatter sparks.
 * Far LOD (GeneratedBodies.wantsDetail false, the tactical band): the thin docked chains (under 1.5 px), the streaks,
 * the bind's lash and forge line, the shatter sparks and every impact flash after the first are culled; the halo
 * (bigger dots), the weapons (their far stand-ins, drawn thicker in flight), the thrown chains (held to 1.5 px), the
 * shadow, the hem rods, the wedge, the lantern, the orb and the flashes stay. Nothing is allocated per frame.
 */
@OnlyIn(Dist.CLIENT)
public class TitheBinderRenderer extends MobRenderer<TitheBinderUnit, TitheBinderUnitModel> {
    private static final String NAME = "tithe_binder_unit";
    private static final ResourceLocation TEXTURE = GeneratedBodies.texture(NAME);
    private static final ResourceLocation GLOW_TEXTURE =
        ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "textures/entities/" + NAME + "_glow.png");

    // design sheet 2.3 palette (0xRRGGBB)
    static final int SOUL_GLOW = 0x62F3CB, SOUL_CORE = 0x34DBA3, SOUL_DIM = 0x1B6D53, WHITE_HOT = 0xE9FFF8;
    /** The hover's ground shadow and the hem chains' rods: near-black, and a dark rusted iron. */
    static final int SHADOW = 0x0A0A0C, HEM_ROD = 0x35281F;
    /** px: the thinnest chain kept far, and the width far chains are held to (sheet 4.4). */
    static final double FAR_MIN_WIDTH = 1.5;
    /** px: a chain longer than this fades, down to 40% (sheet 4.4: beyond 3 blocks). */
    static final double FADE_LENGTH = 48.0;
    /**
     * Count halo (ingame critic r1: ten equal dashes read as a selection ring at N = 1): exactly N lit dots, 2 px of
     * soul_glow near (3 px far), and 10 - N dim dots of soul_dim at alpha 0.25.
     */
    static final float HALO_DOT = 2.0f, HALO_DOT_FAR = 3.0f, HALO_LIT_ALPHA = 1.0f, HALO_DIM_ALPHA = 0.25f;
    /** Hover shadow (sheet 6): a soft ellipse 14 x 12 px (side x front-back) at alpha 0.45, a hair above the ground. */
    static final float SHADOW_W = 14f, SHADOW_D = 12f, SHADOW_ALPHA = 0.45f, SHADOW_LIFT = 0.25f;
    /** The hem chains drawn again as dark rods into the hover gap: 1.5 px wide (opaque-ish far, lighter near). */
    static final double HEM_ROD_WIDTH = 1.5;
    static final float HEM_ROD_ALPHA = 0.6f, HEM_ROD_ALPHA_FAR = 0.9f;
    /**
     * Far LOD: a flying weapon's cross-section (its stand-in is 2 x 1.2 px, x the flight scale 1.5) is drawn this much
     * thicker, so its thinnest side is >= 4 px at 21 px a block (1.2 x 1.5 x 1.7 = 3.06 model px = 4.0 screen px); its
     * length stays.
     */
    static final float FAR_FLY_THICK = 1.7f;
    /**
     * The volley's telegraph (sheet 6, attack): a faint 30-degree ground wedge toward the target over the clip's t0-12,
     * from the hover's edge out to the target (8 px to at most 48 px), soul_glow fading to nothing at its tip.
     */
    static final int WEDGE_END = 12, WEDGE_SEGS = 3;
    static final double WEDGE_HALF_DEG = 15.0, WEDGE_R0 = 8.0, WEDGE_R1_MIN = 24.0, WEDGE_R1_MAX = 48.0;
    static final float WEDGE_ALPHA = 0.28f;
    /** The shadow's fan: 16 segments round, the solid core to 0.7 of the radius, a soft fringe to the edge. */
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
    /** The second segment's tip below its own pivot (the broken shackle's end, model px). */
    private static final float HEM_TIP_Y = 3.9f;

    private final RenderType weaponBody = MipRenderTypes.body(TEXTURE);
    private final RenderType weaponGlow = MipRenderTypes.glow(GLOW_TEXTURE);
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

    public TitheBinderRenderer(EntityRendererProvider.Context context) {
        // no vanilla shadow (a 16 px disc the hem seemed to touch): FxLayer draws the hover's own, smaller and softer
        super(context, new TitheBinderUnitModel(context.bakeLayer(TitheBinderModel.LAYER_LOCATION)), 0f);
        this.headPath = this.model.path("head");
        this.lanternPath = this.model.path("lantern_core");
        for (int i = 0; i < 3; i++) {
            this.hemPaths[i] = this.model.path(HEM[i]);
            this.hemTipPaths[i] = this.model.path(HEM[i] + "_b");
        }
        this.addLayer(new TeamColourLayer<>(this));
        this.addLayer(GeneratedBodies.glow(this, NAME));
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
     * -z ahead): the inverse of the renderer's Ry(180 - yaw) * scale(-1, -1, 1). False when the client has no such unit.
     */
    static boolean toModel(TitheBinderUnit e, int id, double heightFrac, double sin, double cos, double[] out) {
        if (id < 0)
            return false;
        Entity t = e.level().getEntity(id);
        if (t == null)
            return false;
        double dx = t.getX() - e.getX(), dz = t.getZ() - e.getZ();
        out[0] = 16.0 * (dx * cos + dz * sin);
        out[1] = 16.0 * (t.getY() + t.getBbHeight() * heightFrac - e.getY());
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
            // the dark things first, alpha blended (BarFx's scorch-decal type): the hover's shadow, the hem chains' rods
            vc = buffers.getBuffer(BarFxRenderTypes.TRANSLUCENT);
            float body = (float) Math.max(0.0, Math.min(1.0, p.alpha));
            shadow(SHADOW_ALPHA * body);
            hemRods(far, body);
            // then the light, additive
            vc = buffers.getBuffer(BarFxRenderTypes.ADDITIVE);
            wedge(d);
            ribbons(p, far);
            halo(p, d.weapons(), far);
            lantern(p, ageInTicks);
            impacts(p, far);
            bind(p, far);
            if (!far)
                streaks(p);
            vc = null;
            mat = null;
        }
    }

    private static boolean drawn(int flyState) {
        return flyState != TitheBinderRig.FLY_NONE && flyState != TitheBinderRig.FLY_SHATTER;
    }

    /** Every weapon in the air (or falling) where the rig says, in the body's texture and then its glow. */
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
        VertexConsumer body = buffers.getBuffer(weaponBody);
        for (int k = 1; k <= 10; k++) {
            ModelPart w = this.model.weapon(k);
            if (w != null && drawn(p.flyState[k]))
                w.render(pose, body, light, OverlayTexture.NO_OVERLAY);
        }
        VertexConsumer glow = buffers.getBuffer(weaponGlow);
        for (int k = 1; k <= 10; k++) {
            ModelPart w = this.model.weapon(k);
            if (w != null && drawn(p.flyState[k]))
                w.render(pose, glow, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
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

    /** The soul chains (sheet 4.2): docked 4 segments at 1.2 px, thrown 8 at 1.6 px; far, the thin ones go. */
    private void ribbons(TitheBinderRig.Pose p, boolean far) {
        for (int i = 0; i < p.ribbons; i++) {
            double w = p.ribbonWidth[i];
            if (far && w < FAR_MIN_WIDTH)
                continue;
            boolean thrown = w > 1.3;
            double[] a = p.ribbonHub[i], b = p.ribbonEnd[i];
            double len = Math.sqrt(sq(b[0] - a[0]) + sq(b[1] - a[1]) + sq(b[2] - a[2]));
            float alpha = (float) ((thrown ? 0.85 : 0.6) * (len > FADE_LENGTH ? Math.max(0.4, FADE_LENGTH / len) : 1.0));
            ribbon(a, p.ribbonCtrl[i], b, far ? Math.max(w, FAR_MIN_WIDTH) : w, thrown ? 8 : 4, SOUL_GLOW, alpha);
        }
    }

    /**
     * The count halo over the hood: 10 dots on a ring that grows with N, exactly the lit count (Pose.halo: N, less the
     * weapons shattered at death) bright and the rest dim, so N = 1 reads as one bright dot; it dips on a hit. The ring
     * stands 13 px over the head pivot, above the hood's crown, so the RTS camera (~52 deg down) sees all of it.
     */
    private void halo(TitheBinderRig.Pose p, int n, boolean far) {
        partPoint(headPath, 0f, -13f, 0.5f, pt);
        float cx = pt.x(), cy = pt.y() + (float) (p.haloDip / 16.0), cz = pt.z();
        float r = (6.5f + 0.25f * (Math.max(1, Math.min(10, n)) - 1)) / 16f;
        float h = (far ? HALO_DOT_FAR : HALO_DOT) / 32f;               // half the dot's side, in blocks
        int lit = Math.max(0, Math.min(10, p.halo));
        for (int i = 0; i < 10; i++) {
            double th = Math.toRadians(36.0 * i);
            float x = cx - (float) Math.sin(th) * r, z = cz - (float) Math.cos(th) * r;
            if (i < lit)
                billboard(x, cy, z, h, SOUL_GLOW, HALO_LIT_ALPHA);
            else
                billboard(x, cy, z, h, SOUL_DIM, HALO_DIM_ALPHA);
        }
    }

    // ------------------------------------------------------------------ the ground: shadow, hem rods, wedge

    /**
     * The hover's shadow (sheet 6): a soft dark ellipse, SHADOW_W x SHADOW_D px, under the unit's origin a hair above
     * the ground. Drawn as BarFxClient.drawDecal draws a scorch mark: a fan with a solid core to 0.7 of the radius and
     * a fringe fading to nothing at the rim.
     */
    private void shadow(float a) {
        if (a < 0.01f)
            return;
        float y = my(SHADOW_LIFT), rx = SHADOW_W / 32f, rz = SHADOW_D / 32f;
        for (int i = 0; i < SHADOW_SEGS; i++) {
            float c0 = SHADOW_COS[i], s0 = SHADOW_SIN[i], c1 = SHADOW_COS[i + 1], s1 = SHADOW_SIN[i + 1];
            float ix0 = c0 * rx * 0.7f, iz0 = s0 * rz * 0.7f, ix1 = c1 * rx * 0.7f, iz1 = s1 * rz * 0.7f;
            v(0f, y, 0f, SHADOW, a);                                     // the core (a fan quad, centre twice)
            v(ix0, y, iz0, SHADOW, a * 0.85f);
            v(ix1, y, iz1, SHADOW, a * 0.85f);
            v(0f, y, 0f, SHADOW, a);
            v(ix0, y, iz0, SHADOW, a * 0.85f);                           // the soft fringe
            v(c0 * rx, y, s0 * rz, SHADOW, 0f);
            v(c1 * rx, y, s1 * rz, SHADOW, 0f);
            v(ix1, y, iz1, SHADOW, a * 0.85f);
        }
    }

    /**
     * The three hem chains again as dark rods (HEM_ROD_WIDTH px) from where they leave the hem to the broken shackle's
     * end, through the posed bones (they swing with their springs): they hang into the hover gap and never reach the
     * ground, which is how the gap reads from above (sheet 2.1).
     */
    private void hemRods(boolean far, float body) {
        float a = (far ? HEM_ROD_ALPHA_FAR : HEM_ROD_ALPHA) * body;
        if (a < 0.01f)
            return;
        float hw = (float) (HEM_ROD_WIDTH / 32.0);
        for (int i = 0; i < 3; i++) {
            if (hemPaths[i].length == 0 || hemTipPaths[i].length == 0)
                continue;
            partPoint(hemPaths[i], 0f, 0f, HEM_TOP_Z[i], rodA);
            partPoint(hemTipPaths[i], 0f, 0f, 0f, rodB);
            partPoint(hemTipPaths[i], 0f, HEM_TIP_Y, 0f, rodC);
            rod(rodA, rodB, hw, HEM_ROD, a);
            rod(rodB, rodC, hw, HEM_ROD, a);
        }
    }

    /**
     * The volley's telegraph: over the clip's t0-WEDGE_END a faint 30-degree wedge on the ground toward the target
     * (the victim's chest as the client sees it this tick, else straight ahead), fading in over 3 ticks and out over
     * the last 3, from the hover's edge out toward the target, its tip fading to nothing.
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
        float y = my(SHADOW_LIFT + 0.1f);
        double step = Math.toRadians(2.0 * WEDGE_HALF_DEG / WEDGE_SEGS), phi0 = -Math.toRadians(WEDGE_HALF_DEG);
        for (int i = 0; i < WEDGE_SEGS; i++) {
            double pa = phi0 + step * i, pb = pa + step;
            double ca = Math.cos(pa), sa = Math.sin(pa), cb = Math.cos(pb), sb = Math.sin(pb);
            double ax = dx * ca - dz * sa, az = dx * sa + dz * ca, bx = dx * cb - dz * sb, bz = dx * sb + dz * cb;
            v(mx(ax * WEDGE_R0), y, mz(az * WEDGE_R0), SOUL_GLOW, a);
            v(mx(ax * r1), y, mz(az * r1), SOUL_GLOW, 0f);
            v(mx(bx * r1), y, mz(bz * r1), SOUL_GLOW, 0f);
            v(mx(bx * WEDGE_R0), y, mz(bz * WEDGE_R0), SOUL_GLOW, a);
        }
    }

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
            ribbon(p.lashA, p.lashCtrl, p.lashB, 1.2, 6, SOUL_GLOW, 0.8f);
        if (p.forgeOn && !far)
            ribbon(p.forgeA, p.forgeA, p.forgeB, 1.0, 2, WHITE_HOT, 0.7f);
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

    // ------------------------------------------------------------------ primitives (model space, additive)

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

    /** A quadratic Bezier ribbon a -> b through control c (DSL px), {@code widthPx} wide, in {@code segs} quads. */
    private void ribbon(double[] a, double[] c, double[] b, double widthPx, int segs, int rgb, float alpha) {
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
                v(px - prevA[0], py - prevA[1], pz - prevA[2], rgb, alpha);
                v(px + prevA[0], py + prevA[1], pz + prevA[2], rgb, alpha);
                v(x + prevB[0], y + prevB[1], z + prevB[2], rgb, alpha);
                v(x - prevB[0], y - prevB[1], z - prevB[2], rgb, alpha);
            }
            prevA[0] = prevB[0];
            prevA[1] = prevB[1];
            prevA[2] = prevB[2];
            px = x;
            py = y;
            pz = z;
        }
    }

    /** One straight camera-facing strip a -> b, already in model space (blocks), {@code hw} half wide. */
    private void rod(Vector3f a, Vector3f b, float hw, int rgb, float alpha) {
        side(b.x() - a.x(), b.y() - a.y(), b.z() - a.z(), hw, prevA);
        v(a.x() - prevA[0], a.y() - prevA[1], a.z() - prevA[2], rgb, alpha);
        v(a.x() + prevA[0], a.y() + prevA[1], a.z() + prevA[2], rgb, alpha);
        v(b.x() + prevA[0], b.y() + prevA[1], b.z() + prevA[2], rgb, alpha);
        v(b.x() - prevA[0], b.y() - prevA[1], b.z() - prevA[2], rgb, alpha);
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
