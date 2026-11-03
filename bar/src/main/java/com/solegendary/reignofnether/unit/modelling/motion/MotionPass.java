package com.solegendary.reignofnether.unit.modelling.motion;

import com.mojang.blaze3d.vertex.PoseStack;
import com.solegendary.reignofnether.config.ReignOfNetherClientConfigs;
import com.solegendary.reignofnether.hero.PossessionClientEvents;
import com.solegendary.reignofnether.orthoview.OrthoviewClientEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
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
 * A dying unit whose map has a death pose ({@link BoneMap#deathPose}) wears {@link DeathFall}'s fall instead - the
 * renderer then skips vanilla's flip ({@link #ownsDeath}) - and so does the body {@link Corpses} holds after the
 * server removed the unit; any other dying unit plays vanilla's death alone.
 *
 * Skipped: the living army off (config or the parade toggle), a unit with no slot yet, the possessed hero seen in
 * first person, a model with no {@link BoneMap}. A model class that throws is switched off for the session
 * ({@link BoneMaps#disable}) and draws as before.
 */
@OnlyIn(Dist.CLIENT)
public final class MotionPass {

    private MotionPass() {}

    private static final float[] OUT = new float[Motion.OUT];
    private static final float[] DEATH_OUT = new float[DeathFall.OUT];
    /** The offsets the placement reads: OUT, or DEATH_OUT for a fall. */
    private static float[] cur = OUT;
    private static final float[] LOOK = new float[Motion.LOOK_LEN];
    private static final float[] SLIDERS = new float[7];
    private static final float[] SLIDERS_NO_BREATH = new float[7];   // for rigs that breathe by themselves
    private static final Quaternionf Q = new Quaternionf();   // PoseStack.mulPose copies it: one reused, no garbage
    private static int slidersTick = Integer.MIN_VALUE;

    /** The parts touched by the last apply (bones then the hat) and their saved pose: x, y, z, xRot, yRot, zRot. */
    private static final int MAX_TOUCHED = DeathFall.BONES + BoneMap.MAX_FOLLOWERS + BoneMap.MAX_DROPPED
        + BoneMap.MAX_LOWERED;
    private static final ModelPart[] TOUCHED = new ModelPart[MAX_TOUCHED];
    private static final float[] SAVED = new float[MAX_TOUCHED * 6];
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
        if (e.deathTime > 0) {
            BoneMap map = ownsDeath(e, model);
            if (map != null)
                fall(e, model, map, ps, partial);
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
            LOOK[Motion.LOOK_MODE] = map.look;
            if (map.look != Motion.LOOK_NONE) {   // what LivingEntityRenderer gave setupAnim this frame
                float body = bodyYaw(e, partial, e.yBodyRotO, e.yBodyRot);
                LOOK[Motion.LOOK_BODY_YAW] = body;
                LOOK[Motion.LOOK_NET_YAW] = Mth.wrapDegrees(Mth.rotLerp(partial, e.yHeadRotO, e.yHeadRot) - body);
                LOOK[Motion.LOOK_PITCH] = Mth.lerp(partial, e.xRotO, e.getXRot());
            }
            Motion.pose(AnimStates.slab(), b, e.getId(), e.tickCount + partial, partial, zoom,
                map.noBreath ? SLIDERS_NO_BREATH : SLIDERS, map.flinchOnly, LOOK, OUT);
            place(map, ps);
        } catch (RuntimeException ex) {
            restore();
            BoneMaps.disable(model.getClass(), ex);
        }
    }

    /**
     * The body yaw a frame draws for `e` (LivingEntityRendererMixin redirects LivingEntityRenderer.render's own
     * rotLerp of yBodyRotO..yBodyRot here): {@link Kinematics#bodyYawAt} for a ground unit with a slot - flyers keep
     * vanilla's (their own flight code banks and turns them) - else vanilla's. A GUI render (the HUD portrait, the
     * inventory screen) draws at partial 1 with its own pose in yBodyRot - a world frame's partial is always under 1 -
     * so it keeps vanilla's too, or the portrait would turn to the unit's heading in the world.
     */
    public static float bodyYaw(LivingEntity e, float partial, float from, float to) {
        if (e == null || partial >= 1f || !AnimStates.enabled() || (e instanceof Unit u && u.isFlyingUnit()))
            return Mth.rotLerp(partial, from, to);
        int b = AnimStates.base(e.getId());
        return b < 0 ? Mth.rotLerp(partial, from, to) : Kinematics.bodyYawAt(AnimStates.slab(), b, from, to, partial);
    }

    /**
     * The map of a dying unit that falls by {@link DeathFall} (the renderer then skips vanilla's flip and its red
     * overlay), or null for vanilla's death: the living army on, a slot with the death latched, a death-pose map, and
     * not the possessed hero seen from inside.
     */
    public static BoneMap ownsDeath(LivingEntity e, EntityModel<?> model) {
        if (e.deathTime <= 0 || !AnimStates.enabled())
            return null;
        int b = AnimStates.base(e.getId());
        if (b < 0 || AnimStates.slab()[b + AnimState.DEATH_KIND] == DeathFall.ALIVE)
            return null;
        if (PossessionClientEvents.isPossessing() && PossessionClientEvents.getPossessedId() == e.getId()
            && Minecraft.getInstance().options.getCameraType().isFirstPerson())
            return null;
        BoneMap map = BoneMaps.forModel(model);
        return map != null && map.deathPose() ? map : null;
    }

    /** {@link #ownsDeath(LivingEntity, EntityModel)} for a caller with no model at hand: its own renderer's model. */
    public static BoneMap ownsDeath(LivingEntity e) {
        if (e.deathTime <= 0)
            return null;
        EntityRenderer<?> renderer = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(e);
        return renderer instanceof LivingEntityRenderer<?, ?> r ? ownsDeath(e, r.getModel()) : null;
    }

    private static void fall(LivingEntity e, EntityModel<?> model, BoneMap map, PoseStack ps, float partial) {
        int b = AnimStates.base(e.getId());
        float[] s = AnimStates.slab();
        try {
            float t = e.deathTime - 1 + partial;
            DeathFall.pose(s, b, e.getId(), t, map.jointed, DEATH_OUT);
            s[b + AnimState.DEATH_DRAWN] = 1f;
            placeFall(map, ps, DeathFall.dropped(t, s[b + AnimState.DEATH_KIND], e.getId()));
        } catch (RuntimeException ex) {
            restore();
            BoneMaps.disable(model.getClass(), ex);
        }
    }

    /** The fall's offsets ({@link DeathFall#pose} writes them): for the bench and the tests, which have no entity. */
    public static float[] fallScratch() {
        return DEATH_OUT;
    }

    /** {@link #placeFall(BoneMap, PoseStack, float)} with any weapon beside the hand already at rest (a lying body). */
    public static void placeFall(BoneMap map, PoseStack ps) {
        placeFall(map, ps, 1f);
    }

    /**
     * Puts {@link #fallScratch()}'s offsets on the map's death parts and the pose stack (undone by {@link #restore});
     * a weapon held beside the hand is {@code dropped} (0..1, {@link DeathFall#dropped}) of the way to its rest.
     */
    public static void placeFall(BoneMap map, PoseStack ps, float dropped) {
        cur = DEATH_OUT;
        try {
            ModelPart[] p = map.death;
            for (int bone = 1; bone < DeathFall.BONES; bone++)
                keep(p[bone]);
            for (int i = 0; i < map.followerCount; i++)
                keep(map.followers[i]);
            for (int i = 0; i < map.droppedCount; i++)
                keep(map.dropped[i]);
            for (int i = 0; i < map.loweredCount; i++)
                keep(map.lowered[i]);
            tilt(ps);
            bend(p, map.deathHierarchical, map.waistY);
            limbs(p);
            hanging(p[DeathFall.FORE_R], DeathFall.FORE_R);
            hanging(p[DeathFall.FORE_L], DeathFall.FORE_L);
            hanging(p[DeathFall.KNEE_R], DeathFall.KNEE_R);
            hanging(p[DeathFall.KNEE_L], DeathFall.KNEE_L);
            for (int i = 0; i < map.droppedCount; i++)
                settle(map.dropped[i], dropped);
            for (int i = 0; i < map.loweredCount; i++)
                map.lowered[i].xRot += dropped * LOWER_RAD;
            for (int i = 0; i < map.followerCount; i++)
                map.followers[i].copyFrom(map.leaders[i]);
        } finally {
            cur = OUT;
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
        bend(map.parts, map.hierarchical, map.waistY);
        limbs(map.parts);
        for (int i = 0; i < map.followerCount; i++)
            map.followers[i].copyFrom(map.leaders[i]);
    }

    /**
     * How far a grip holding a weapon in both hands ({@link BoneMap#lowered}) tips down over the fall, radians: the
     * weapon, out in front at rest, comes to point at the feet, lying along the body a little proud of it. Left alone
     * it pointed forwards - at the sky once the body lay on its back (the Levy's crossbow, eyes run 40978023283).
     */
    static final float LOWER_RAD = 1.2f;

    /**
     * A weapon on a grip beside the hand slips k (0..1) of the way back to its planted rest, its forward offset gone:
     * upright at rest, it lies along the fallen body in the body's plane. Left at the guard it pointed forwards - at
     * the sky once the body lay on its back (the Halberdier's halberd stood up beside him in the captures).
     */
    private static void settle(ModelPart p, float k) {
        if (!(k > 0f))
            return;
        float x = p.x, y = p.y, z = p.z, rx = p.xRot, ry = p.yRot, rz = p.zRot;
        p.resetPose();
        p.x = Mth.lerp(k, x, p.x);
        p.y = Mth.lerp(k, y, p.y);
        p.z = Mth.lerp(k, z, 0f);
        p.xRot = Mth.lerp(k, rx, p.xRot);
        p.yRot = Mth.lerp(k, ry, p.yRot);
        p.zRot = Mth.lerp(k, rz, p.zRot);
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
        return cur[bone * Motion.CH + ch];
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

    /**
     * A fall's whole body about its feet: one tilt of up to 90 degrees about a horizontal axis (two turns in a row,
     * as {@link #root} makes them, would not lay it flat along a diagonal), then the twist about its own length.
     */
    private static void tilt(PoseStack ps) {
        float rx = out(Motion.ROOT, Motion.RX), rz = out(Motion.ROOT, Motion.RZ), ry = out(Motion.ROOT, Motion.RY);
        ps.translate(-out(Motion.ROOT, Motion.TX) / 16f, -out(Motion.ROOT, Motion.TY) / 16f,
            -out(Motion.ROOT, Motion.TZ) / 16f);
        float angle = Mth.sqrt(rx * rx + rz * rz);
        if (angle < 1e-5f && ry == 0f)
            return;
        ps.translate(0f, 1.5f, 0f);
        if (angle >= 1e-5f)
            ps.mulPose(Q.rotationAxis(angle, rx / angle, 0f, -rz / angle));
        if (ry != 0f)
            ps.mulPose(Q.rotationY(ry));
        ps.translate(0f, -1.5f, 0f);
    }

    /** The torso bends about the waist and turns about its own upright (the look); the head (and hat) and the arms ride on it. */
    private static void bend(ModelPart[] p, boolean hierarchical, float waistY) {
        float ax = out(Motion.BODY, Motion.RX), az = -out(Motion.BODY, Motion.RZ), ay = out(Motion.BODY, Motion.RY);
        if (ax == 0f && az == 0f && ay == 0f)
            return;
        if (hierarchical) {           // the head and arms are its children: they follow the bend by themselves
            if (p[Motion.BODY] != null) {
                p[Motion.BODY].xRot += ax;
                p[Motion.BODY].yRot += ay;
                p[Motion.BODY].zRot += az;
            }
            return;
        }
        if (ay != 0f) {               // first the turn: the shoulders swing round the spine, every rider turns with it
            float cy = Mth.cos(ay), sy = Mth.sin(ay);
            turn(p[Motion.BODY], ay, cy, sy);
            turn(p[Motion.HEAD], ay, cy, sy);
            turn(p[Motion.ARM_R], ay, cy, sy);
            turn(p[Motion.ARM_L], ay, cy, sy);
            turn(p[Motion.ARMS], ay, cy, sy);
        }
        if (ax == 0f && az == 0f)
            return;
        float cx = Mth.cos(ax), sx = Mth.sin(ax), cz = Mth.cos(az), sz = Mth.sin(az);
        ride(p[Motion.BODY], waistY, ax, az, cx, sx, cz, sz);
        ride(p[Motion.HEAD], waistY, ax, az, cx, sx, cz, sz);
        ride(p[Motion.ARM_R], waistY, ax, az, cx, sx, cz, sz);
        ride(p[Motion.ARM_L], waistY, ax, az, cx, sx, cz, sz);
        ride(p[Motion.ARMS], waistY, ax, az, cx, sx, cz, sz);
    }

    /** A part turned with the torso about the model's upright axis (x = z = 0): its pivot swings round, its yaw adds. */
    private static void turn(ModelPart p, float ay, float cy, float sy) {
        if (p == null)
            return;
        float x = p.x, z = p.z;
        p.x = x * cy + z * sy;
        p.z = -x * sy + z * cy;
        p.yRot += ay;
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
    private static void limbs(ModelPart[] p) {
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
