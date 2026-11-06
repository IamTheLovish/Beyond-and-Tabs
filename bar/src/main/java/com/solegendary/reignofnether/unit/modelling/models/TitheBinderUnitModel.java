package com.solegendary.reignofnether.unit.modelling.models;

import com.solegendary.reignofnether.unit.modelling.animations.TitheBinderDrive;
import com.solegendary.reignofnether.unit.modelling.animations.TitheBinderParts;
import com.solegendary.reignofnether.unit.modelling.animations.TitheBinderRig;
import com.solegendary.reignofnether.unit.modelling.models.gen.TitheBinderModel;
import com.solegendary.reignofnether.unit.modelling.motion.OwnMotion;
import com.solegendary.reignofnether.unit.units.monsters.TitheBinderUnit;
import com.solegendary.reignofnether.unit.units.monsters.TitheLedger;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * The Tithe Binder (tithe_binder_unit) over its generated body (TitheBinderModel, from
 * design/models/gravebound_tithe_binder.json by tools/dsl_to_game.py; the generator is tools/gen_tithe_binder.py),
 * moved by its own rig: TitheBinderRenderer steps the unit's {@link TitheBinderDrive} once per game tick and hands it
 * over with the frame's partialTick ({@link #frame}); setupAnim lays the blended pose on the rest pose through
 * {@link TitheBinderRig#apply} (bones, the docked slots, the hidden lantern core). Flying weapons and every effect are
 * the renderer's. The living army's motion pass leaves this body alone ({@link OwnMotion}): the rig owns it.
 *
 * Far LOD (tools/gen_tithe_binder.py far_lod): when the camera is far, every weapon draws its tactical stand-in
 * (weapon_k_far) instead of its own cubes, and the flail's ball is hidden.
 *
 * The body's own passes ({@link #renderToBuffer}: the paint, the team mask, the glow) leave the weapons out; the
 * renderer draws them alone ({@link #renderDocked}) in the value ladder's flat colours (in-game critic r3).
 */
@OnlyIn(Dist.CLIENT)
public class TitheBinderUnitModel extends TitheBinderModel<TitheBinderUnit> implements OwnMotion {

    private final ModelPart[] weapons = new ModelPart[TitheLedger.MAX_WEAPONS + 1];
    private final ModelPart[] weaponsFar = new ModelPart[TitheLedger.MAX_WEAPONS + 1];
    /** Per weapon: every part of its subtree but the far stand-in (their cubes are skipped when far). */
    private final ModelPart[][] weaponsNear = new ModelPart[TitheLedger.MAX_WEAPONS + 1][];
    private final ModelPart flailBall;
    private final TitheBinderRig.Bones rig;
    /** Per weapon: its bones from the root's child down to weapon_k itself (TitheBinderModel.path). */
    private final ModelPart[][] weaponPaths = new ModelPart[TitheLedger.MAX_WEAPONS + 1][];
    // the body passes' scratch: which weapons were visible before renderToBuffer hid them
    private final boolean[] bodyShown = new boolean[TitheLedger.MAX_WEAPONS + 1];

    // this draw (set by the renderer just before it, taken by setupAnim)
    private TitheBinderDrive drive;
    private float partialTick;
    private boolean far;

    public TitheBinderUnitModel(ModelPart root) {
        super(root);
        for (int k = 1; k <= TitheLedger.MAX_WEAPONS; k++) {
            ModelPart w = bone("weapon_" + k);
            ModelPart f = bone("weapon_" + k + "_far");
            weapons[k] = w;
            weaponsFar[k] = f;
            weaponsNear[k] = w == null ? new ModelPart[0]
                : w.getAllParts().filter(p -> p != f).toArray(ModelPart[]::new);
            weaponPaths[k] = path("weapon_" + k);
        }
        flailBall = bone("weapon_6_ball");
        rig = TitheBinderParts.bones(this::bone);
    }

    /** The renderer's hand-over for one draw: the unit's drive (stepped to this tick), partialTick, near / far. */
    public void frame(TitheBinderDrive drive, float partialTick, boolean far) {
        this.drive = drive;
        this.partialTick = partialTick;
        this.far = far;
    }

    /** Shows weapon_1..weapon_count, hides the rest (1-10): the rest pose, while there is no drive. */
    public void showWeapons(int count) {
        for (int k = 1; k <= TitheLedger.MAX_WEAPONS; k++)
            if (weapons[k] != null)
                weapons[k].visible = k <= count;
    }

    /** Weapon k (1-10), or null. */
    public ModelPart weapon(int k) {
        return k >= 1 && k <= TitheLedger.MAX_WEAPONS ? weapons[k] : null;
    }

    /** Weapon k (1-10) draws its far stand-in (true) or its own cubes (false). */
    public void weaponLod(int k, boolean far) {
        for (ModelPart p : weaponsNear[k])
            p.skipDraw = far;
        if (weaponsFar[k] != null)
            weaponsFar[k].visible = far;
    }

    @Override
    public void setupAnim(TitheBinderUnit entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw,
                          float headPitch) {
        restPose();
        TitheBinderDrive d = this.drive;
        this.drive = null;
        if (d != null && d.ready()) {
            TitheBinderRig.Pose p = d.blend(partialTick);
            TitheBinderRig.apply(p, rig);
            thickenDocked(p);
        } else {
            showWeapons(entity.getWeapons());
        }
        for (int k = 1; k <= TitheLedger.MAX_WEAPONS; k++)
            weaponLod(k, far);
        if (flailBall != null)
            flailBall.visible = !far;
    }

    /**
     * The wheel as a sawblade (in-game critic r2): a docked weapon's cross-section (its part's x = width, y =
     * thickness; z is its length, kept) is drawn thicker, so its blade is about 3 px wide and 2 px thick, its widest
     * feature (a hammer head, a hook, a cross-guard) held to about 6 x 4 px. Near: per weapon, from its blade cube in
     * TitheBinderModel; far: the stand-in (2 x 1.2 px) to 3 x 2 px. A flying weapon is the renderer's (FxLayer sets
     * its scale itself).
     */
    private void thickenDocked(TitheBinderRig.Pose p) {
        for (int k = 1; k <= TitheLedger.MAX_WEAPONS; k++) {
            ModelPart w = weapons[k];
            if (w == null || !p.slotOn[k])
                continue;
            w.xScale *= far ? FAR_BLADE_X : BLADE_X[k];
            w.yScale *= far ? FAR_BLADE_Y : BLADE_Y[k];
        }
    }

    /**
     * Per weapon 1-10: width factor min(3 / blade width, 6 / widest x), thickness factor min(2 / blade thickness,
     * 4 / thickest y), from TitheBinderModel's cubes (1 sword 0.61 x 0.61, hook 1.84; 2 twin blades 1.48 x 0.59;
     * 3 halberd shaft 0.85, head 3.55; 4 blade 1.23 x 0.49, guard 3.07; 5 spear 0.41, head 1.24 x 0.25; 6 flail
     * 0.75, ball 2.5; 7 hammer shaft 0.78, head 4.55 x 2.6; 8 scythe 0.57, its blade already 7 px across; 9 blade
     * 1.21 x 0.40, guard 3.22; 10 axe haft 0.47, head 2.74 x 0.39).
     */
    static final float[] BLADE_X = {1f, 3.27f, 2.03f, 1.69f, 1.96f, 4.85f, 2.4f, 1.32f, 1f, 1.86f, 2.04f};
    static final float[] BLADE_Y = {1f, 3.27f, 3.38f, 2.36f, 4f, 4f, 1.6f, 1.54f, 3.5f, 4f, 4f};
    static final float FAR_BLADE_X = 1.5f, FAR_BLADE_Y = 1.67f;
    /**
     * The value ladder's edge (in-game critic r3: the weapons read as opaque mint diamond tools): every weapon's soul
     * shell stands EDGE_PX proud of it. A thickened blade is BLADE_W x BLADE_T px (far: the stand-in, FAR_W x FAR_T
     * before its thickening), so its shell is 1 + 2 EDGE_PX / (that size x the weapon's own scale) larger across;
     * along its length, EDGE_Z (about 0.3 px on a 12-16 px weapon). Never more than EDGE_MAX.
     */
    public static final float EDGE_PX = 0.3f, EDGE_Z = 1.04f, EDGE_MAX = 2f;
    static final float BLADE_W = 3f, BLADE_T = 2f, FAR_W = 2f, FAR_T = 1.2f;

    /** The shell's factor across weapon k's width for its x scale now (1 for no weapon). */
    public static float edgeX(int k, boolean far, float xScale) {
        if (k < 1 || k > TitheLedger.MAX_WEAPONS)
            return 1f;
        float w = (far ? FAR_W : BLADE_W / BLADE_X[k]) * Math.abs(xScale);
        return w < 1e-4f ? 1f : Math.min(EDGE_MAX, 1f + 2f * EDGE_PX / w);
    }

    /** The shell's factor across weapon k's thickness for its y scale now (1 for no weapon). */
    public static float edgeY(int k, boolean far, float yScale) {
        if (k < 1 || k > TitheLedger.MAX_WEAPONS)
            return 1f;
        float t = (far ? FAR_T : BLADE_T / BLADE_Y[k]) * Math.abs(yScale);
        return t < 1e-4f ? 1f : Math.min(EDGE_MAX, 1f + 2f * EDGE_PX / t);
    }

    /**
     * The body's own passes (the paint, the team mask, the glow) draw no weapon: the renderer's WeaponLayer draws them
     * in flat colours on Forge's white texture (the value ladder), so the body texture's opaque mint is never under them.
     */
    @Override
    public void renderToBuffer(PoseStack pose, VertexConsumer vc, int light, int overlay, float r, float g, float b,
                               float a) {
        for (int k = 1; k <= TitheLedger.MAX_WEAPONS; k++) {
            ModelPart w = weapons[k];
            bodyShown[k] = w != null && w.visible;
            if (w != null)
                w.visible = false;
        }
        try {
            super.renderToBuffer(pose, vc, light, overlay, r, g, b, a);
        } finally {
            for (int k = 1; k <= TitheLedger.MAX_WEAPONS; k++)
                if (weapons[k] != null)
                    weapons[k].visible = bodyShown[k];
        }
    }

    /**
     * The glow pass (TitheBinderRenderer.GlowLayer, between SplitPasses.beginPass / endPass of the glow parts): the
     * body's light at full - the lantern core, the one strong body glow. The weapons' light is their soul shell
     * (TitheBinderRenderer.WeaponLayer), not their glow texels.
     */
    public void renderGlow(PoseStack pose, VertexConsumer vc) {
        renderToBuffer(pose, vc, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 1f, 1f, 1f, 1f);
    }

    /**
     * Docked weapon k alone, through its posed bones, in {@code vc} and colour (r, g, b, a), its scale times (sx, sy,
     * sz) for this draw only (a negative sy mirrors it: the shell). False when it is not on the wheel (no weapon, a
     * slot off, a weapon in the air).
     */
    public boolean renderDocked(int k, PoseStack pose, VertexConsumer vc, int light, float r, float g, float b, float a,
                                float sx, float sy, float sz) {
        ModelPart w = weapon(k);
        if (w == null || !w.visible || !shown(weaponPaths[k]))
            return false;
        pose.pushPose();
        float x = w.xScale, y = w.yScale, z = w.zScale;
        try {
            root().translateAndRotate(pose);
            ModelPart[] path = weaponPaths[k];
            for (int i = 0; i < path.length - 1; i++)
                path[i].translateAndRotate(pose);
            w.xScale = x * sx;
            w.yScale = y * sy;
            w.zScale = z * sz;
            w.render(pose, vc, light, OverlayTexture.NO_OVERLAY, r, g, b, a);
        } finally {
            w.xScale = x;
            w.yScale = y;
            w.zScale = z;
            pose.popPose();
        }
        return true;
    }

    /** Every part of the path (root excluded) is visible: a slot off (a weapon in the air) hides its weapon. */
    private static boolean shown(ModelPart[] path) {
        for (ModelPart p : path)
            if (!p.visible)
                return false;
        return path.length > 0;
    }
}
