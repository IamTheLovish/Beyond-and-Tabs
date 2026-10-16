package com.solegendary.reignofnether.unit.modelling.models;

import com.solegendary.reignofnether.unit.modelling.models.gen.CrossbowLevyModel;
import com.solegendary.reignofnether.unit.units.villagers.PillagerUnit;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * The Sunforged Crossbow Levy (pillager_unit) in motion, over its generated body (CrossbowLevyModel, from
 * design/models/sunforged_crossbow_levy_v2.json by tools/dsl_to_game.py; the generator is tools/gen_levy.py).
 *
 * - at rest the crossbow is carried at the ready in front of the chest, levelled (the art director's review, Oct 15:
 *   the crossbow, not the hat, must be the signature); with a target it comes up to the shoulder, the head down to the
 *   sights (the AIM, blended per unit over ~5 ticks);
 * - spanning it (Pillager.isChargingCrossbow) the whole rig - arms and crossbow together, so the hands stay on it -
 *   swings the nose down toward the ground and he leans into the crank;
 * - riding (a ravager: isPassenger) the legs sit astride, the tabard's front panel over the thighs, and only the arms
 *   aim;
 * - walk: a light stride, the tabard pushed by the legs, the crossbow bobbing; idle: breath, the head following the
 *   look. Keys laid out in tools/pose_preview.py (DSL degrees on "xbow_arms" / "crossbow"; a part takes -x, +y, -z).
 *
 * The crossbow is a machine and works (lovish, Oct 16: "the crossbow should be mechanical and have animations
 * including rebolting"). Its bones, under "crossbow": the steel prod's two limbs (prod_l / prod_r), each carrying its
 * half of the string (string_l / string_r, pivoted at the nock), the revolving nut that holds the drawn string, the
 * tickler under the stock (trigger), the windlass on the right lock plate (crank), the claw that rides the rail and
 * fetches the string, and the bolt in the groove. The model's rest is loaded and drawn. What the unit is doing comes
 * from state the client already has, nothing new is synced:
 * - loaded: the main-hand crossbow is charged (CrossbowItem.isCharged; equipment syncs to clients). Drawn, nut shut,
 *   the bolt in the groove;
 * - the shot: charged turning uncharged (UnitCrossbowAttackGoal.performAttack). The tickler squeezes, the nut flips
 *   open, the prod snaps forward and the string rings past straight for a few ticks; the crossbow kicks up;
 * - the reload (spanning: isChargingCrossbow and using the item, its progress the ticks used over
 *   CrossbowItem.getChargeDuration): the claw runs forward along the rail and hooks the string, the crank winds it back
 *   to the nut (three and a half turns, the prod bending as it comes), the nut turns shut on it and the claw goes home,
 *   then a fresh bolt is laid into the groove from above - all inside the spanning posture above;
 * - not loaded and not spanning: the string let go, straight across the relaxed prod, no bolt.
 * The timings and the formulas were keyed offline on renders of a Python mirror of this mechanism
 * (tools/levy_reload_preview.py, Oct 16), so a change here should be previewed the same way; the string's geometry is XbowString, plain Java, so a test can check
 * it against the model's numbers (CrossbowLevyStringTest).
 */
@OnlyIn(Dist.CLIENT)
public class CrossbowLevyUnitModel extends CrossbowLevyModel<PillagerUnit> {

    // ---- the crossbow's mechanism. These come from tools/gen_levy.py's crossbow() - the bone pivots it writes into
    // design/models/sunforged_crossbow_levy_v2.json and its XB dict - and must change with it. DSL px in the crossbow's
    // own frame (x across the stock, z along it, the nose at -z). Only their differences reach the pose, so moving the
    // whole crossbow leaves them right; reshaping the prod, the nut or the claw does not. Literals only: XbowString
    // reads them, and javac inlines only constants - so it (and a test of it) never loads this client-only class.
    // (Oct 16, the anatomy pass: the crossbow moved with his re-proportioned body - up 1.2 px with the shoulders and
    // 0.3 forward with the padded chest; the z's below moved by that 0.3, the differences are unchanged.)
    /** The stock's centre line: the prods' pivot x. */
    public static final float XB_X = -0.3f;
    /** The prods' pivot z (XB prod_centre). */
    public static final float XB_PROD_CZ = -12.9f;
    /** string_l's nock (its pivot) from the prod centre, the prod at rest (XB string_l tip); string_r mirrors it. */
    public static final float XB_TIP_DX = 8.708972f, XB_TIP_DZ = 2.555488f;
    /** Where the drawn string sits: the nut's horns (XB nut_z). */
    public static final float XB_NUT_Z = -7.25f;
    /** The claw parked behind the nut (its pivot z, XB claw_z). */
    public static final float XB_CLAW_Z = -6.05f;
    /** string_l's own rotation y, the turn that lays it from the nock onto the nut (string_r: the negative). */
    public static final float XB_REST_YAW = 19.561380f;
    /** A string half's modelled length, nock to nut (its cube's x size). */
    public static final float XB_REST_LEN = 9.242413f;

    // ---- the reload's own keys (keyed on the offline renders, Oct 16; the Python mirror must change with them)
    /** How far each limb sweeps forward when the string is let go (DSL degrees). */
    public static final float XB_RELAX = 6f;
    /** The spanning as fractions of the charge: the claw hooks the string by P_HOOK, the crank has it at the nut by
     *  P_DRAWN, the nut shuts on it from P_LATCH (the claw then goes home) and the bolt is laid in from P_BOLT. */
    public static final float XB_P_HOOK = 0.12f, XB_P_DRAWN = 0.74f, XB_P_LATCH = 0.80f, XB_P_BOLT = 0.80f;
    /** The windlass's turns while it winds; it backs off half a turn as the claw runs forward, so 3 net. */
    public static final float XB_CRANK_TURNS = 3.5f;
    /** The claw's hooks sit this far behind the string it holds (DSL px). */
    public static final float XB_CLAW_HOOK = 0.3f;
    /** The nut's turn when open (DSL degrees), and the share of the charge it takes to shut. */
    public static final float XB_NUT_OPEN = 55f, XB_NUT_SHUT = 0.05f;
    /** The new bolt starts above the groove (DSL px: up, back, to the side) and tilted (degrees), then drops in. */
    public static final float XB_BOLT_UP = 2.4f, XB_BOLT_BACK = 1.2f, XB_BOLT_SIDE = -1f, XB_BOLT_TILT = 25f;
    /** The shot's after-motion, in ticks since the shot: it lasts SHOT ticks; the tickler squeezed TRIGGER degrees,
     *  let go over SQUEEZE ticks; the crossbow kicked KICK degrees nose up, decaying over KICK_T; the string rings past
     *  straight by RING of a full draw, decaying over RING_T, at RING_RATE radians a tick. */
    public static final float XB_SHOT = 12f, XB_TRIGGER = 10f, XB_SQUEEZE = 5f, XB_KICK = 6f, XB_KICK_T = 2.2f,
            XB_RING = 0.18f, XB_RING_T = 1.6f, XB_RING_RATE = 2.4f;
    /** The charged crossbow reaches the client a tick after the spanning ends (PillagerUnit.tick charges it after
     *  LivingEntity.tick has sent this tick's equipment): hold the loaded pose that long instead of flashing the
     *  let-go string. Ticks, generous for network order. */
    private static final float XB_HOLD = 4f;
    /** A change seen after a gap this long (ticks without a frame: off screen) happened at an unknown time. */
    private static final float XB_SEEN = 4f;

    /** Riding: the front panel's lift onto the thighs (DSL degrees; design/poses' "riding" carries it). */
    private static final float RIDE_TABARD = 60f;
    private static final float AIM_TICKS = 5f;
    private static final float NEVER = -1e9f;
    // the state per unit: aim and charge blends, the last frame's age, whether the crossbow was charged then (0/1),
    // the shot's age, the spanning progress then (-1: not spanning), the loaded pose held until (age)
    private static final int AIM = 0, CHARGE = 1, AGE = 2, CHARGED = 3, SHOT = 4, SPAN = 5, HOLD = 6;
    private final ModelPart body, head, arms, xbow, tabFront, tabBack;
    private final ModelPart[] thigh = new ModelPart[2], shin = new ModelPart[2], foot = new ModelPart[2];
    // the mechanism's bones: null until the generated base class is exported with them, so every use is null-safe
    private final ModelPart prodL, prodR, stringL, stringR, nut, trigger, crank, claw, bolt;
    private final Map<PillagerUnit, float[]> state = new WeakHashMap<>();
    private final float[] geo = new float[4];   // XbowString.geom's scratch: one per model, not one per frame
    private float aim = 0f, charge = 0f;
    // this frame's mechanism, worked out in prepareMobModel: the draw (1 = drawn to the nut, 0 = let go), the claw's
    // run from its park, the nut's opening, the crank's turns, the bolt (shown; its offset and tilt), the tickler's
    // squeeze and the recoil
    private float xbDraw = 1f, xbClaw, xbNut, xbTurns, xbBoltX, xbBoltY, xbBoltZ, xbBoltTilt, xbTrig, xbKick;
    private boolean xbBolt = true;

    public CrossbowLevyUnitModel(ModelPart root) {
        super(root);
        body = bone("body");
        head = bone("head");
        arms = bone("xbow_arms");
        xbow = bone("crossbow");
        tabFront = bone("tabard_front");
        tabBack = bone("tabard_back");
        String[] sides = {"left", "right"};
        for (int i = 0; i < 2; i++) {
            thigh[i] = bone(sides[i] + "_thigh");
            shin[i] = bone(sides[i] + "_shin");
            foot[i] = bone(sides[i] + "_foot");
        }
        prodL = bone("prod_l");
        prodR = bone("prod_r");
        stringL = bone("string_l");
        stringR = bone("string_r");
        nut = bone("nut");
        trigger = bone("trigger");
        crank = bone("crank");
        claw = bone("claw");
        bolt = bone("bolt");
    }

    private static float approach(float v, float target, float step) {
        return target > v ? Math.min(target, v + step) : Math.max(target, v - step);
    }

    @Override
    public void prepareMobModel(PillagerUnit entity, float limbSwing, float limbSwingAmount, float partialTick) {
        super.prepareMobModel(entity, limbSwing, limbSwingAmount, partialTick);
        float age = entity.tickCount + partialTick;
        ItemStack stack = entity.getMainHandItem();
        boolean charged = stack.getItem() instanceof CrossbowItem && CrossbowItem.isCharged(stack);
        float[] st = state.get(entity);   // get/put, not computeIfAbsent: the lambda captures age and would allocate
        if (st == null)                   // every frame for every unit (and again in a shader pack's shadow pass)
            state.put(entity, st = new float[] {0f, 0f, age, charged ? 1f : 0f, NEVER, -1f, NEVER});
        float gap = age - st[AGE];
        float step = Mth.clamp(gap, 0f, 20f) / AIM_TICKS;
        boolean charging = entity.isChargingCrossbow();
        boolean aiming = !charging && (entity.getTarget() != null || entity.isAggressive());
        st[AIM] = approach(st[AIM], aiming ? 1f : 0f, step);
        st[CHARGE] = approach(st[CHARGE], charging ? 1f : 0f, step);
        aim = st[AIM];
        charge = st[CHARGE];

        // the crossbow's own state, from what the client already knows (the equipment and the using-item flags);
        // a second call in the same frame (a shader pack's shadow pass) changes nothing: it compares against itself
        boolean spanning = charging && entity.isUsingItem();
        float p = spanning ? Mth.clamp((entity.getTicksUsingItem() + partialTick) / CrossbowItem.getChargeDuration(stack),
                0f, 1f) : -1f;
        boolean seen = gap < XB_SEEN;
        if (st[CHARGED] > 0f && !charged)   // the shot (or, after a gap off screen, an old one: no after-motion)
            st[SHOT] = seen ? age : NEVER;
        if (charged || spanning)
            st[HOLD] = NEVER;
        else if (st[SPAN] >= XB_P_LATCH && seen)   // the spanning just ended, the charged crossbow still on its way
            st[HOLD] = age + XB_HOLD;
        st[CHARGED] = charged ? 1f : 0f;
        st[SPAN] = p;
        st[AGE] = age;
        mechanism(spanning, p, charged || age < st[HOLD], Math.max(0f, age - st[SHOT]));
    }

    /** The mechanism for a state into the xb fields: spanning at progress p (0..1), else loaded or not; t = ticks since
     *  the shot. The same formulas as the offline mirror's mech(). */
    private void mechanism(boolean spanning, float p, boolean loaded, float t) {
        float d, clawZ, nutOpen, turns;
        xbBolt = false;
        xbBoltX = xbBoltY = xbBoltZ = xbBoltTilt = 0f;
        if (spanning) {
            if (p < XB_P_HOOK) {   // the claw runs forward to the let-go string, the crank backing off to engage
                d = 0f;
                XbowString.geom(1f, 0f, geo);
                clawZ = XB_CLAW_Z + (geo[3] + XB_CLAW_HOOK - XB_CLAW_Z) * smooth(p / XB_P_HOOK);
                turns = -0.5f * p / XB_P_HOOK;
            } else if (p < XB_P_DRAWN) {   // the crank winds the claw, and the string in it, back to the nut
                float k = (p - XB_P_HOOK) / (XB_P_DRAWN - XB_P_HOOK);
                d = smooth(k);
                XbowString.geom(1f, d, geo);
                clawZ = geo[3] + XB_CLAW_HOOK;
                turns = -0.5f + XB_CRANK_TURNS * k;
            } else {   // drawn: the claw waits on the nut, then goes home once it has shut
                d = 1f;
                turns = XB_CRANK_TURNS - 0.5f;
                clawZ = XB_NUT_Z + XB_CLAW_HOOK;
                if (p >= XB_P_LATCH)
                    clawZ += (XB_CLAW_Z - XB_NUT_Z - XB_CLAW_HOOK) * smooth((p - XB_P_LATCH) / (1f - XB_P_LATCH));
            }
            nutOpen = p < XB_P_LATCH ? 1f : 1f - smooth((p - XB_P_LATCH) / XB_NUT_SHUT);
            if (p >= XB_P_BOLT) {   // the bolt comes down into the groove from above and behind, levelling
                float u = 1f - smooth((p - XB_P_BOLT) / (1f - XB_P_BOLT));
                xbBolt = true;
                xbBoltX = XB_BOLT_SIDE * u;
                xbBoltY = XB_BOLT_UP * u * u;
                xbBoltZ = XB_BOLT_BACK * u;
                xbBoltTilt = XB_BOLT_TILT * u;
            }
        } else if (loaded) {
            d = 1f;
            clawZ = XB_CLAW_Z;
            nutOpen = 0f;
            turns = 0f;
            xbBolt = true;
        } else {
            d = 0f;
            clawZ = XB_CLAW_Z;
            nutOpen = 1f;
            turns = 0f;
        }
        xbTrig = xbKick = 0f;
        if (t < XB_SHOT) {
            xbKick = (float) Math.exp(-t / XB_KICK_T);
            xbTrig = Math.max(0f, 1f - t / XB_SQUEEZE);
            if (d == 0f)   // let go: the string rings past straight and back
                d = (float) (-XB_RING * Math.exp(-t / XB_RING_T) * Math.cos(t * XB_RING_RATE));
        }
        xbDraw = d;
        xbClaw = clawZ - XB_CLAW_Z;
        xbNut = nutOpen;
        xbTurns = turns;
    }

    private static void dsl(ModelPart p, float x, float y, float z, float k) {
        if (p == null || k == 0f)
            return;
        p.xRot -= x * Mth.DEG_TO_RAD * k;
        p.yRot += y * Mth.DEG_TO_RAD * k;
        p.zRot -= z * Mth.DEG_TO_RAD * k;
    }

    /** A DSL move (px, y up) onto a part (y down). */
    private static void move(ModelPart p, float x, float y, float z) {
        if (p == null)
            return;
        p.x += x;
        p.y -= y;
        p.z += z;
    }

    private static float smooth(float x) {
        x = Mth.clamp(x, 0f, 1f);
        return x * x * (3f - 2f * x);
    }

    @Override
    public void setupAnim(PillagerUnit entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw,
                          float headPitch) {
        restPose();
        if (body == null)
            return;
        boolean riding = entity.isPassenger();
        float amt = riding ? 0f : Math.min(1f, limbSwingAmount);
        float w = limbSwing * 0.6662f;
        float t = ageInTicks + (entity.getId() & 63);
        float breathe = Mth.sin(t * 0.07f);

        if (riding) {
            // astride: thighs forward and splayed, shins down
            for (int i = 0; i < 2; i++) {
                float side = i == 0 ? 1f : -1f;
                // splayed OUT round the mount: yaw the knees apart (z alone rolled the shins inward and crossed the
                // feet - Oct 15 review)
                dsl(thigh[i], 75, -18 * side, 8 * side, 1f);
                dsl(shin[i], -75, 0, 0, 1f);
            }
            // the tabard's front panel lies forward over the thighs (hanging, the thighs ran through it - 1.1 px, and
            // 1.5 once the anatomy pass gave him real thighs, Oct 16)
            dsl(tabFront, RIDE_TABARD, 0, 0, 1f);
        } else {
            for (int i = 0; i < 2; i++) {
                float ph = w + (i == 0 ? 0f : Mth.PI);
                if (thigh[i] != null) thigh[i].xRot += Mth.cos(ph) * 0.6f * amt;
                float lift = Math.max(0f, Mth.sin(ph));
                if (shin[i] != null) shin[i].xRot += lift * 0.85f * amt;
                // the foot follows through, toe up, so it clears the ground on the swing (it dragged 0.5 px)
                if (foot[i] != null) foot[i].xRot -= lift * 0.3f * amt;
            }
        }
        float spread = Math.abs(Mth.cos(w)) * amt;
        if (tabFront != null) tabFront.xRot += -spread * 0.35f + 0.02f * Mth.sin(t * 0.08f);
        if (tabBack != null) tabBack.xRot += spread * 0.3f;
        body.y += breathe * 0.08f + (Math.abs(Mth.cos(w)) - 0.5f) * 0.5f * amt;
        body.yRot += Mth.sin(w) * 0.05f * amt;
        if (arms != null) arms.xRot += Mth.sin(w * 2f) * 0.04f * amt + breathe * 0.015f;
        if (head != null) {
            head.yRot += Mth.clamp(netHeadYaw, -40f, 40f) * Mth.DEG_TO_RAD * 0.6f;
            head.xRot += Mth.clamp(headPitch, -25f, 25f) * Mth.DEG_TO_RAD * 0.4f;
        }

        // the aim: the crossbow raised and levelled, its nose brought round to the front. At rest it is carried
        // angled 20 degrees to his left (tools/gen_levy.py), so the aim turns it 20 to his right (DSL y > 0): 12 by the
        // torso (shoulders, rig and head go with it) and 8 by the rig - a whole 20 on the rig alone would pull the
        // shoulders 1.3 px off the torso. The head turns back the 12 to keep its eyes on the target. (Keyed with
        // tools/game_preview.py --poses; until Oct 15 these keys turned the nose a further 10 left - 29 off target.)
        if (aim > 0f) {
            float a = smooth(aim);
            dsl(body, 0, 12, 0, a);
            dsl(arms, 14, 8, 0, a);
            dsl(xbow, -12, 0, 0, a);
            dsl(head, -6, -12, 0, a);
        }
        // spanning: the nose pitched down mostly by the crossbow's own bone, a little by the rig (the rig alone at -38
        // swung the stock and both gloves 1.7-3 px into his belly - Oct 15 review), leaning into the crank; a small
        // crank rhythm in the arms
        if (charge > 0f) {
            float c = smooth(charge);
            dsl(arms, -10, 0, 0, c);
            dsl(xbow, -28, 0, 0, c);
            dsl(body, -10, 0, 0, c);
            if (arms != null) arms.xRot += Mth.sin(t * 0.9f) * 0.06f * c;
        }
        // the shot's kick, nose up, on top of whatever pose the crossbow is in
        dsl(xbow, XB_KICK * xbKick, 0, 0, 1f);

        // the mechanism (worked out in prepareMobModel). The stretch and the bolt's visibility are set every frame:
        // restPose resets the angles and offsets, not the bolt's visible flag, and the stretch is only ours
        XbowString.geom(1f, xbDraw, geo);
        dsl(prodL, 0, geo[0], 0, 1f);
        dsl(stringL, 0, geo[1], 0, 1f);
        stretch(stringL, geo[2]);
        XbowString.geom(-1f, xbDraw, geo);
        dsl(prodR, 0, geo[0], 0, 1f);
        dsl(stringR, 0, geo[1], 0, 1f);
        stretch(stringR, geo[2]);
        move(claw, 0f, 0f, xbClaw);
        dsl(nut, -XB_NUT_OPEN * xbNut, 0, 0, 1f);
        dsl(crank, 360f * xbTurns, 0, 0, 1f);
        dsl(trigger, -XB_TRIGGER * xbTrig, 0, 0, 1f);
        if (bolt != null) {
            bolt.visible = xbBolt;
            if (xbBolt) {
                move(bolt, xbBoltX, xbBoltY, xbBoltZ);
                dsl(bolt, xbBoltTilt, 0, 0, 1f);
            }
        }
    }

    /** A string half stretched along its own x (from its nock); exactly 1 when it is at its modelled length, so a
     *  loaded crossbow at rest - most of them, most of the time - costs no scale in the pose stack. */
    private static void stretch(ModelPart p, float s) {
        if (p != null)
            p.xScale = Math.abs(s - 1f) < 1e-4f ? 1f : s;
    }

    /**
     * The crossbow string's geometry, in plain Java: it touches no Minecraft class and reads only the XB_ constants
     * (inlined), so a unit test - or a GameTest on the dedicated server, where this client model cannot load - can
     * call it.
     *
     * The string is two halves, each pivoted at its nock on its prod limb and laid by its rest yaw onto the nut. To
     * show a draw, the limb turns (the prod relaxes forward as the string lets go) and the half turns on the limb and
     * stretches so it ends where the two halves meet on the stock.
     */
    public static final class XbowString {
        private XbowString() {
        }

        /**
         * For side s (1 = string_l on prod_l, -1 = string_r on prod_r) at draw d (1 = drawn to the nut, the modelled
         * rest; 0 = let go, straight across the relaxed prod; below 0 = rung forward of straight after a shot), into
         * out[0..3]: the limb's yaw and the half's yaw on top of its rest yaw (DSL degrees), the half's stretch (1 = its
         * modelled length) and the z where the halves meet (DSL px, the crossbow's frame).
         */
        public static void geom(float s, float d, float[] out) {
            double phi = s * XB_RELAX * (1 - d);
            double r = Math.toRadians(phi), c = Math.cos(r), sn = Math.sin(r);
            double vx = s * XB_TIP_DX, vz = XB_TIP_DZ;
            double tx = XB_X + c * vx + sn * vz, tz = XB_PROD_CZ - sn * vx + c * vz;   // the nock, turned with the limb
            double sz = tz + (XB_NUT_Z - tz) * d;   // where the halves meet, on the stock's centre line
            double dx = XB_X - tx, dz = sz - tz;
            // the half's heading in the crossbow's frame: string_l is laid along -x from its nock, string_r along +x
            double a = Math.toDegrees(s > 0 ? Math.atan2(dz, -dx) : Math.atan2(-dz, dx));
            out[0] = (float) phi;
            out[1] = (float) (a - phi - s * XB_REST_YAW);   // less what the limb and the bone's own rotation turn
            out[2] = (float) (Math.sqrt(dx * dx + dz * dz) / XB_REST_LEN);
            out[3] = (float) sz;
        }
    }
}
