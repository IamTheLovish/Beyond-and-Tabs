package com.solegendary.reignofnether.unit.modelling.models;

import com.solegendary.reignofnether.unit.modelling.models.gen.RadiantPaladinModel;
import com.solegendary.reignofnether.unit.reactions.ReactionClientEvents;
import com.solegendary.reignofnether.unit.units.villagers.IronGolemUnit;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * The Sunforged Paladin (Radiant) in motion: the concept model's own part tree (RadiantPaladinModel, generated from
 * design/models/sunforged_paladin_radiant_v2.json by tools/dsl_to_game.py) plus the animation, every angle of which
 * was laid out first on paper with tools/pose_preview.py and tools/game_preview.py (DSL degrees there; a part takes
 * (-x, +y, -z) in radians, which is what {@link #dsl} does).
 *
 * - walk: a heavy stride (thighs +-0.45 rad), the knee bending on the swing-through, a hip twist and sway, the cape
 *   streaming back, the tabards pushed by the legs, the relic book and the sash swinging;
 * - idle: breath in the torso and the sword, the cape and tabards stirring, the halo turning slowly about its own
 *   axis (its ring is a child of the tilted halo bone) and bobbing, the head following the look;
 * - attack (the golem's 10-tick attack animation): the overhead chop - wind-up with the sword laid back over the
 *   shoulders, the strike forward and down with the whole torso behind it, follow-through, recovery. Arms and sword
 *   hang from one rig bone on the shoulder line ("arms"), so the gauntlets never let go of the grip;
 * - brace (Brace reaction, ReactionClientEvents): a guard crouch - knees bent, the sword raised before him - blended
 *   in and out over ~6 ticks per unit.
 * The same class drives the winged option (design/models/sunforged_paladin_radiant_winged.json) if it is exported
 * instead: a "hands" bone means the sword is planted (its own chop keys, carried low on the march) and "wing_left" /
 * "wing_right" breathe, lift on the march and open with the wind-up.
 */
@OnlyIn(Dist.CLIENT)
public class RadiantPaladinUnitModel extends RadiantPaladinModel<IronGolemUnit> {

    // the chop, keyed on p = 0..1 through the attack: arms and body offsets in DSL degrees (tools/pose_preview.py)
    private static final float[] KEY_P = {0f, 0.32f, 0.50f, 0.62f, 1f};
    private static final float[][] KEY_ARMS = {{0, 0, 0}, {95, 8, 0}, {-22, -8, 0}, {-30, -10, 0}, {0, 0, 0}};
    private static final float[][] KEY_BODY = {{0, 0, 0}, {8, 6, 0}, {-14, -8, 0}, {-18, -10, 0}, {0, 0, 0}};
    // the winged option (design/models/sunforged_paladin_radiant_winged.json: sword PLANTED point-down, a "hands"
    // bone): a rising cut that ends with the sword to the sky, then the smash (tools/pose_preview.py)
    private static final float[] KEY_P_PLANTED = {0f, 0.28f, 0.48f, 0.60f, 1f};
    // the wind-up lifts the gauntlets only to the chin and the WRISTS lay the blade back past the sky (KEY_SWORD_PLANTED
    // on the sword bone, turned about the grip): arms raised to 150 swept the forearms 2.0-2.9 px through the helm on
    // every attack - the gauntlets sit a fixed 11.4 px from the shoulder line and the helm reaches 8-14 px above it,
    // so no overhead arc of the rig clears it (a roll only shrugged a shoulder off). This clears helm, halo and wings
    // at 0.00 px standing and walking, and the strike lands where it did (Oct 15 review, searched in a harness port)
    private static final float[][] KEY_ARMS_PLANTED = {{0, 0, 0}, {40, 0, 0}, {20, 0, 0}, {24, 0, 0}, {0, 0, 0}};
    private static final float[] KEY_SWORD_PLANTED = {0, 140, 0, 0, 0};
    // the grip's centre relative to the sword bone's pivot (model px, the planted sword's rest): the cock turns about
    // the gauntlets, not about the pivot 2 px below them (else the handle slides ~3.6 px out of the hands)
    private static final float GRIP_DY = -1.725f, GRIP_DZ = -0.963f;
    private static final float[][] KEY_BODY_PLANTED = {{0, 0, 0}, {10, 0, 0}, {-16, 0, 0}, {-18, 0, 0}, {0, 0, 0}};
    private static final float BRACE_TICKS = 6f;     // blend in / out
    private static final float BRACE_DROP = 1.1f;    // px the crouch lowers the body (keeps the bent legs on the ground)

    private final ModelPart body, arms, head, haloRing, cape0, cape1, cape2, tabFront0, tabFront1, tabBack0, tabBack1,
        sash, relic, sword;
    private final ModelPart[] thigh = new ModelPart[2], shin = new ModelPart[2], foot = new ModelPart[2];
    private final ModelPart[] wing = new ModelPart[2];   // the winged option only
    private final boolean planted;                       // the winged option's sword is planted point-down

    // per unit: {brace blend 0..1, the age it was last updated at}; weak keys, so a removed unit is forgotten
    private final Map<IronGolemUnit, float[]> braceState = new WeakHashMap<>();
    private float attack = -1f;   // p through the chop, or -1
    private float brace = 0f;

    public RadiantPaladinUnitModel(ModelPart root) {
        super(root);
        body = bone("body");
        arms = bone("arms");
        head = bone("head");
        haloRing = bone("halo_ring");
        cape0 = bone("cape_0");
        cape1 = bone("cape_1");
        cape2 = bone("cape_2");
        tabFront0 = bone("tabard_front_0");
        tabFront1 = bone("tabard_front_1");
        tabBack0 = bone("tabard_back_0");
        tabBack1 = bone("tabard_back_1");
        sash = bone("sash");
        relic = bone("relic");
        sword = bone("sword");
        String[] sides = {"left", "right"};
        for (int i = 0; i < 2; i++) {
            thigh[i] = bone(sides[i] + "_thigh");
            shin[i] = bone(sides[i] + "_shin");
            foot[i] = bone(sides[i] + "_foot");
            wing[i] = bone("wing_" + sides[i]);
        }
        planted = bone("hands") != null;
    }

    @Override
    public void prepareMobModel(IronGolemUnit entity, float limbSwing, float limbSwingAmount, float partialTick) {
        int t = entity.getAttackAnimationTick();
        attack = t > 0 ? Mth.clamp(1f - (t - partialTick) / 10f, 0f, 1f) : -1f;
        float age = entity.tickCount + partialTick;
        float[] st = braceState.computeIfAbsent(entity, e -> new float[] {0f, age});
        float dt = Mth.clamp(age - st[1], 0f, 20f);
        float target = ReactionClientEvents.isBraced(entity.getId()) ? 1f : 0f;
        st[0] = target > st[0] ? Math.min(target, st[0] + dt / BRACE_TICKS) : Math.max(target, st[0] - dt / BRACE_TICKS);
        st[1] = age;
        brace = st[0];
    }

    /** Adds DSL degrees (x, y, z) to a part: (-x, +y, -z) radians. */
    private static void dsl(ModelPart p, float x, float y, float z, float k) {
        if (p == null || k == 0f)
            return;
        p.xRot -= x * Mth.DEG_TO_RAD * k;
        p.yRot += y * Mth.DEG_TO_RAD * k;
        p.zRot -= z * Mth.DEG_TO_RAD * k;
    }

    private static float smooth(float x) {
        return x * x * (3f - 2f * x);
    }

    @Override
    public void setupAnim(IronGolemUnit entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw,
                          float headPitch) {
        restPose();
        if (body == null || arms == null)
            return;
        float amt = Math.min(1f, limbSwingAmount) * (1f - brace);
        float w = limbSwing * 0.6662f;
        float t = ageInTicks + (entity.getId() & 63);       // per-unit phase: a line of paladins does not breathe as one
        float breathe = Mth.sin(t * 0.07f);

        // ---- legs: a heavy stride, the knee bending on the swing-through
        for (int i = 0; i < 2; i++) {
            float ph = w + (i == 0 ? 0f : Mth.PI);
            float c = Mth.cos(ph), lift = Math.max(0f, Mth.sin(ph));
            if (thigh[i] != null) thigh[i].xRot += c * 0.45f * amt;
            if (shin[i] != null) shin[i].xRot += lift * 0.9f * amt;
            if (foot[i] != null) foot[i].xRot -= lift * 0.3f * amt;
        }

        // ---- torso: breath, the step's bob, a hip twist and sway
        body.y += breathe * 0.12f + (Math.abs(Mth.cos(w)) - 0.5f) * 0.6f * amt;
        body.yRot += Mth.sin(w) * 0.06f * amt;
        body.zRot += Mth.cos(w) * 0.025f * amt;
        arms.xRot += breathe * 0.02f + Mth.sin(w * 2f) * 0.03f * amt;
        if (planted)
            dsl(arms, 28, 0, 0, amt);                            // on the march the planted sword is carried low
        // wings (the winged option): breathing, a little lift on the march
        for (int i = 0; i < 2; i++)
            if (wing[i] != null) {
                float side = i == 0 ? 1f : -1f;
                wing[i].yRot += side * (0.03f * breathe + 0.06f * amt);
                wing[i].xRot += 0.05f * amt;      // lift (game xRot > 0 = DSL x < 0 tips the tops back, the tips up)
            }

        // ---- head: follows the look, a little
        if (head != null) {
            head.yRot += Mth.clamp(netHeadYaw, -40f, 40f) * Mth.DEG_TO_RAD * 0.6f;
            head.xRot += Mth.clamp(headPitch, -25f, 25f) * Mth.DEG_TO_RAD * 0.4f + Mth.sin(t * 0.07f + 1f) * 0.02f;
        }
        if (haloRing != null) {
            haloRing.zRot += t * 0.012f;                        // one turn in ~26 s
            haloRing.y += Mth.sin(t * 0.05f) * 0.25f;
        }

        // ---- cloth: the cape streams back on the march, the tabards are pushed by the legs, the relic swings
        if (cape0 != null) {
            cape0.xRot += 0.04f * Mth.sin(t * 0.09f) + 0.35f * amt;
            cape0.zRot += Mth.sin(w) * 0.05f * amt;
        }
        if (cape1 != null) cape1.xRot += 0.04f * Mth.sin(t * 0.09f - 0.7f) + amt * (0.12f + 0.08f * Mth.sin(w * 2f));
        if (cape2 != null) cape2.xRot += 0.05f * Mth.sin(t * 0.09f - 1.4f) + amt * 0.1f * Mth.sin(w * 2f - 0.6f);
        if (tabFront0 != null) tabFront0.xRot += -Math.abs(Mth.cos(w)) * 0.3f * amt + 0.02f * Mth.sin(t * 0.08f);
        if (tabFront1 != null) tabFront1.xRot += -0.08f * amt * Mth.sin(w * 2f);
        if (tabBack0 != null) tabBack0.xRot += Math.abs(Mth.cos(w)) * 0.3f * amt + 0.02f * Mth.sin(t * 0.08f + 1f);
        if (tabBack1 != null) tabBack1.xRot += 0.08f * amt * Mth.sin(w * 2f + 0.5f);
        if (relic != null) {
            relic.xRot += Mth.sin(w) * 0.25f * amt + 0.04f * Mth.sin(t * 0.1f);
            relic.zRot += Mth.cos(w) * 0.1f * amt;
        }
        if (sash != null) sash.xRot += Mth.sin(w + 0.5f) * 0.15f * amt + 0.03f * Mth.sin(t * 0.1f + 1f);

        // ---- brace: the guard crouch (tools/pose_preview.py "brace")
        if (brace > 0f) {
            float b = smooth(brace);
            dsl(arms, planted ? 40 : 30, 0, 0, b);
            dsl(body, -8, 0, 0, b);
            dsl(head, 6, 0, 0, b);
            for (int i = 0; i < 2; i++) {
                dsl(thigh[i], 18, 0, 0, b);
                dsl(shin[i], -24, 0, 0, b);
                dsl(foot[i], 6, 0, 0, b);
                if (thigh[i] != null) thigh[i].y += BRACE_DROP * b;
            }
            body.y += BRACE_DROP * b;
            // the front tabard rides out over the crouched knees (they went 2.8 px through it - Oct 15 review)
            dsl(tabFront0, 25, 0, 0, b);
            dsl(tabFront1, 8, 0, 0, b);
        }

        // ---- attack: the overhead chop
        if (attack >= 0f) {
            float[] keyP = planted ? KEY_P_PLANTED : KEY_P;
            float[][] keyArms = planted ? KEY_ARMS_PLANTED : KEY_ARMS, keyBody = planted ? KEY_BODY_PLANTED : KEY_BODY;
            int k = 0;
            while (k < keyP.length - 2 && attack > keyP[k + 1])
                k++;
            float f = smooth(Mth.clamp((attack - keyP[k]) / (keyP[k + 1] - keyP[k]), 0f, 1f));
            float[] a0 = keyArms[k], a1 = keyArms[k + 1], b0 = keyBody[k], b1 = keyBody[k + 1];
            dsl(arms, Mth.lerp(f, a0[0], a1[0]), Mth.lerp(f, a0[1], a1[1]), Mth.lerp(f, a0[2], a1[2]), 1f);
            dsl(body, Mth.lerp(f, b0[0], b1[0]), Mth.lerp(f, b0[1], b1[1]), Mth.lerp(f, b0[2], b1[2]), 1f);
            if (planted && sword != null) {
                float cock = Mth.lerp(f, KEY_SWORD_PLANTED[k], KEY_SWORD_PLANTED[k + 1]);
                if (cock != 0f) {
                    dsl(sword, cock, 0, 0, 1f);
                    float th = -cock * Mth.DEG_TO_RAD, cs = Mth.cos(th), sn = Mth.sin(th);
                    sword.y += GRIP_DY - (cs * GRIP_DY - sn * GRIP_DZ);
                    sword.z += GRIP_DZ - (sn * GRIP_DY + cs * GRIP_DZ);
                }
            }
            // the wings open with the wind-up and fold on the strike
            float open = Mth.sin(Mth.clamp(attack / 0.5f, 0f, 1f) * Mth.PI) * 0.35f;
            for (int i = 0; i < 2; i++)
                if (wing[i] != null)
                    wing[i].yRot += (i == 0 ? 1f : -1f) * open;
        }
    }
}
