package com.solegendary.reignofnether.unit.modelling.motion;

import com.mojang.blaze3d.vertex.PoseStack;
import com.solegendary.reignofnether.config.ReignOfNetherClientConfigs;
import com.solegendary.reignofnether.hero.PossessionClientEvents;
import com.solegendary.reignofnether.orthoview.OrthoviewClientEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.joml.Quaternionf;

/**
 * The living army on vanilla bodies (design/living_army.md, section 3): mixin/LivingEntityRendererMixin calls
 * {@link #apply} right after the model's setupAnim and {@link #restore} when the unit's render returns. In between
 * - the body pass and every layer (armour, team colour, held items) - the unit wears {@link Motion}'s offsets: the whole
 * body about its feet through the pose stack, the torso bent about the waist with the head and arms riding on it, and
 * the head, arms and legs on top. Restore puts every touched part back exactly, so nothing accumulates frame to frame
 * on parts vanilla does not reset. Render thread only; no allocation.
 *
 * Skipped: the living army off (config or the parade toggle), a unit with no slot yet, a dying unit (its death
 * collapse, DeathCollapseClient, or vanilla's fall), the possessed hero seen in first person, a model with no {@link BoneMap}. A model class that
 * throws is switched off for the session ({@link BoneMaps#disable}) and draws as before.
 */
@OnlyIn(Dist.CLIENT)
public final class MotionPass {

    private MotionPass() {}

    private static final float[] OUT = new float[Motion.OUT];
    private static final float[] SLIDERS = new float[7];
    private static final float[] SLIDERS_NO_BREATH = new float[7];   // for rigs that breathe by themselves
    private static final Quaternionf Q = new Quaternionf();   // PoseStack.mulPose copies it: one reused, no garbage
    private static int slidersTick = Integer.MIN_VALUE;

    /** The parts touched by the last apply (bones then the hat) and their saved pose: x, y, z, xRot, yRot, zRot. */
    private static final ModelPart[] TOUCHED = new ModelPart[Motion.BONES + BoneMap.MAX_FOLLOWERS];
    private static final float[] SAVED = new float[(Motion.BONES + BoneMap.MAX_FOLLOWERS) * 6];
    private static int touched = 0;

    private static void readSliders() {
        int t = AnimStates.tick();
        if (t == slidersTick)
            return;
        slidersTick = t;
        try {
            SLIDERS[Motion.BREATH] = ReignOfNetherClientConfigs.MOTION_BREATH.get() / 100f;
            SLIDERS[Motion.WEIGHT] = ReignOfNetherClientConfigs.MOTION_WEIGHT.get() / 100f;
            SLIDERS[Motion.FIDGET] = ReignOfNetherClientConfigs.MOTION_FIDGET.get() / 100f;
            SLIDERS[Motion.LEAN] = ReignOfNetherClientConfigs.MOTION_LEAN.get() / 100f;
            SLIDERS[Motion.ACCEL] = ReignOfNetherClientConfigs.MOTION_ACCEL.get() / 100f;
            SLIDERS[Motion.FLINCH] = ReignOfNetherClientConfigs.MOTION_FLINCH.get() / 100f;
            SLIDERS[Motion.SECONDARY] = ReignOfNetherClientConfigs.MOTION_SECONDARY.get() / 100f;
        } catch (RuntimeException e) {   // the config is not loaded yet: as designed
            java.util.Arrays.fill(SLIDERS, 1f);
        }
        System.arraycopy(SLIDERS, 0, SLIDERS_NO_BREATH, 0, SLIDERS.length);
        SLIDERS_NO_BREATH[Motion.BREATH] = 0f;
    }

    public static void apply(LivingEntity e, EntityModel<?> model, PoseStack ps, float partial) {
        restore();
        if (e.deathTime > 0) {   // a dying unit: its collapse, or vanilla's fall when it has none (DeathCollapseClient)
            DeathCollapseClient.apply(e, model, ps, partial);
            return;
        }
        if (!AnimStates.enabled())
            return;
        int b = AnimStates.base(e.getId());
        if (b < 0)
            return;
        if (PossessionClientEvents.isPossessing() && PossessionClientEvents.getPossessedId() == e.getId()
            && Minecraft.getInstance().options.getCameraType().isFirstPerson())
            return;
        BoneMap map = BoneMaps.forModel(model);
        if (map == null)
            return;
        try {
            readSliders();
            float zoom = OrthoviewClientEvents.isEnabled() ? OrthoviewClientEvents.getZoom() : 0f;
            Motion.pose(AnimStates.slab(), b, e.getId(), e.tickCount + partial, partial, zoom,
                map.noBreath ? SLIDERS_NO_BREATH : SLIDERS, map.flinchOnly, OUT);
            place(map, ps);
        } catch (RuntimeException ex) {
            restore();
            BoneMaps.disable(model.getClass(), ex);
        }
    }

    /** The offsets array {@link #place} reads ({@link Motion#pose} writes it): for the bench, which has no entity. */
    public static float[] scratch() {
        return OUT;
    }

    /** Puts {@link #scratch()}'s offsets on the map's parts and the pose stack (undone by {@link #restore}). */
    public static void place(BoneMap map, PoseStack ps) {
        save(map);
        root(ps);
        bend(map);
        limbs(map);
        for (int i = 0; i < map.followerCount; i++)
            map.followers[i].copyFrom(map.leaders[i]);
    }

    /** Puts every part the last apply touched back exactly as setupAnim left it. */
    public static void restore() {
        for (int i = 0; i < touched; i++) {
            ModelPart p = TOUCHED[i];
            int k = i * 6;
            p.x = SAVED[k];
            p.y = SAVED[k + 1];
            p.z = SAVED[k + 2];
            p.xRot = SAVED[k + 3];
            p.yRot = SAVED[k + 4];
            p.zRot = SAVED[k + 5];
            TOUCHED[i] = null;
        }
        touched = 0;
    }

    private static void save(BoneMap map) {
        for (int bone = 1; bone < Motion.BONES; bone++)
            keep(map.parts[bone]);
        for (int i = 0; i < map.followerCount; i++)
            keep(map.followers[i]);
    }

    private static void keep(ModelPart p) {
        if (p == null)
            return;
        int k = touched * 6;
        SAVED[k] = p.x;
        SAVED[k + 1] = p.y;
        SAVED[k + 2] = p.z;
        SAVED[k + 3] = p.xRot;
        SAVED[k + 4] = p.yRot;
        SAVED[k + 5] = p.zRot;
        TOUCHED[touched++] = p;
    }

    private static float out(int bone, int ch) {
        return OUT[bone * Motion.CH + ch];
    }

    /** The whole body about its feet. Model space here: y down, the unit faces -z, its right is -x, feet at y 1.5. */
    private static void root(PoseStack ps) {
        float rx = out(Motion.ROOT, Motion.RX), rz = out(Motion.ROOT, Motion.RZ);
        ps.translate(-out(Motion.ROOT, Motion.TX) / 16f, -out(Motion.ROOT, Motion.TY) / 16f,
            -out(Motion.ROOT, Motion.TZ) / 16f);
        if (rx == 0f && rz == 0f)
            return;
        ps.translate(0f, 1.5f, 0f);
        ps.mulPose(Q.rotationX(rx));
        ps.mulPose(Q.rotationZ(-rz));
        ps.translate(0f, -1.5f, 0f);
    }

    /** The torso bends about the waist; the head (and hat) and the arms ride on it. */
    private static void bend(BoneMap map) {
        float ax = out(Motion.BODY, Motion.RX), az = -out(Motion.BODY, Motion.RZ);
        if (ax == 0f && az == 0f)
            return;
        ModelPart[] p = map.parts;
        if (map.hierarchical) {           // the head and arms are its children: they follow the bend by themselves
            if (p[Motion.BODY] != null) {
                p[Motion.BODY].xRot += ax;
                p[Motion.BODY].zRot += az;
            }
            return;
        }
        float cx = Mth.cos(ax), sx = Mth.sin(ax), cz = Mth.cos(az), sz = Mth.sin(az);
        ride(p[Motion.BODY], map.waistY, ax, az, cx, sx, cz, sz);
        ride(p[Motion.HEAD], map.waistY, ax, az, cx, sx, cz, sz);
        ride(p[Motion.ARM_R], map.waistY, ax, az, cx, sx, cz, sz);
        ride(p[Motion.ARM_L], map.waistY, ax, az, cx, sx, cz, sz);
        ride(p[Motion.ARMS], map.waistY, ax, az, cx, sx, cz, sz);
    }

    private static void ride(ModelPart p, float waistY, float ax, float az, float cx, float sx, float cz, float sz) {
        if (p == null)
            return;
        float y = p.y - waistY, z = p.z;
        float y1 = y * cx - z * sx, z1 = y * sx + z * cx;    // about x
        float x = p.x;
        float x2 = x * cz - y1 * sz, y2 = x * sz + y1 * cz;  // about z
        p.x = x2;
        p.y = y2 + waistY;
        p.z = z1;
        p.xRot += ax;
        p.zRot += az;
    }

    /** The head up from the neck; arms and legs hang: their forward swing is a negative xRot. */
    private static void limbs(BoneMap map) {
        ModelPart[] p = map.parts;
        upright(p[Motion.HEAD], Motion.HEAD);
        hanging(p[Motion.ARM_R], Motion.ARM_R);
        hanging(p[Motion.ARM_L], Motion.ARM_L);
        hanging(p[Motion.ARMS], Motion.ARMS);
        hanging(p[Motion.LEG_R], Motion.LEG_R);
        hanging(p[Motion.LEG_L], Motion.LEG_L);
    }

    private static void upright(ModelPart p, int bone) {
        if (p == null)
            return;
        p.xRot += out(bone, Motion.RX);
        p.yRot += out(bone, Motion.RY);
        p.zRot -= out(bone, Motion.RZ);
        move(p, bone);
    }

    private static void hanging(ModelPart p, int bone) {
        if (p == null)
            return;
        p.xRot -= out(bone, Motion.RX);
        p.yRot += out(bone, Motion.RY);
        p.zRot += out(bone, Motion.RZ);
        move(p, bone);
    }

    private static void move(ModelPart p, int bone) {
        p.x -= out(bone, Motion.TX);
        p.y -= out(bone, Motion.TY);
        p.z -= out(bone, Motion.TZ);
    }
}
