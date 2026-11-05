package com.solegendary.reignofnether.unit.modelling.animations;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The Tithe Binder's rig against its spec: tools/tithe_binder_anim.py (the clip driver) samples every 5th tick of every
 * clip into design/fixtures/tithe_binder_clips.json; the Java rig, run through the same harness (seed 1, 40 idle ticks
 * of warm-up, the same inputs), must land on every sampled number within 0.01 (degrees, px, alpha / glow / scale):
 * every bone delta, every docked slot, every flying / falling weapon (state, pommel, world rotation, scale), the hidden
 * parts, the body alpha, the lantern glow and every soul-chain ribbon. Then the API's shape (one state, one pose, reused,
 * no allocation per step) and the DSL -> ModelPart sign mapping of tools/dsl_to_game.py ((x, y, z) degrees ->
 * (-x, +y, -z) radians; offsets y down) on three bones and one slot.
 */
class TitheBinderRigTest {

    private static final Path FIXTURE = Paths.get("..", "design", "fixtures", "tithe_binder_clips.json");
    private static final double EPS = 0.01;
    private static final int WARM = 40, SEED = 1;
    private static JsonObject clips;

    /** tools/tithe_binder_anim.py CLIPS: name -> clip, N, ticks, target (DSL px). */
    private static final Map<String, Object[]> RUNS = new HashMap<>();

    static {
        double[] ahead = {0.0, 20.0, -80.0};
        RUNS.put("idle", new Object[]{TitheBinderRig.Clip.IDLE, 10, 60, ahead});
        RUNS.put("fidget", new Object[]{TitheBinderRig.Clip.FIDGET, 10, 30, ahead});
        RUNS.put("move", new Object[]{TitheBinderRig.Clip.MOVE, 10, 50, ahead});
        RUNS.put("volley_N5", new Object[]{TitheBinderRig.Clip.VOLLEY, 5, 60, ahead});
        RUNS.put("volley_N10", new Object[]{TitheBinderRig.Clip.VOLLEY, 10, 60, ahead});
        RUNS.put("bind", new Object[]{TitheBinderRig.Clip.BIND, 5, 32, new double[]{-40.0, 4.0, -56.0}});
        RUNS.put("hit", new Object[]{TitheBinderRig.Clip.HIT, 10, 16, ahead});
        RUNS.put("death", new Object[]{TitheBinderRig.Clip.DEATH, 10, 50, ahead});
    }

    @BeforeAll
    static void load() throws IOException {
        try (Reader r = Files.newBufferedReader(FIXTURE)) {
            clips = JsonParser.parseReader(r).getAsJsonObject().getAsJsonObject("clips");
        }
    }

    /** The driver's move_speed: 0 -> 1 over t0-10, full to t30, stopped by t34 (smoothstep keys). */
    private static double moveSpeed(int t) {
        if (t <= 0)
            return 0.0;
        if (t <= 10)
            return TitheBinderRig.smooth(t / 10.0);
        if (t <= 30)
            return 1.0;
        if (t <= 34)
            return 1.0 - TitheBinderRig.smooth((t - 30) / 4.0);
        return 0.0;
    }

    @Test
    void idle_matches_the_driver() {
        check("idle");
    }

    @Test
    void fidget_matches_the_driver() {
        check("fidget");
    }

    @Test
    void move_matches_the_driver() {
        check("move");
    }

    @Test
    void volley_n5_matches_the_driver() {
        check("volley_N5");
    }

    @Test
    void volley_n10_matches_the_driver() {
        check("volley_N10");
    }

    @Test
    void bind_matches_the_driver() {
        check("bind");
    }

    @Test
    void hit_matches_the_driver() {
        check("hit");
    }

    @Test
    void death_matches_the_driver() {
        check("death");
    }

    /** Runs one clip the way the driver's run() does and compares every sampled tick. */
    private static void check(String name) {
        Object[] run = RUNS.get(name);
        TitheBinderRig.Clip clip = (TitheBinderRig.Clip) run[0];
        int n = (Integer) run[1], ticks = (Integer) run[2];
        double[] target = (double[]) run[3];
        JsonObject want = clips.getAsJsonObject(name);
        assertEquals(n, want.get("n").getAsInt(), name + ": N");
        assertEquals(ticks, want.get("ticks").getAsInt(), name + ": ticks");
        JsonObject poses = want.getAsJsonObject("poses");

        TitheBinderRig.State st = new TitheBinderRig.State(n, SEED);
        TitheBinderRig.Inputs in = new TitheBinderRig.Inputs();
        TitheBinderRig.Pose pose = new TitheBinderRig.Pose();
        for (int i = 0; i < WARM; i++) {
            in.clip = TitheBinderRig.Clip.IDLE;
            in.t = i + 1;
            in.speed = 0.0;
            TitheBinderRig.step(st, in, pose);
        }
        Diff d = new Diff(name);
        int sampled = 0;
        for (int t = 0; t < ticks; t++) {
            in.clip = clip;
            in.t = t;
            in.speed = clip == TitheBinderRig.Clip.MOVE ? moveSpeed(t) : 0.0;
            System.arraycopy(target, 0, in.target, 0, 3);
            TitheBinderRig.step(st, in, pose);
            JsonElement p = poses.get(Integer.toString(t));
            if (p == null)
                continue;
            sampled++;
            compare(d, "t" + t, p.getAsJsonObject(), pose);
        }
        assertEquals(poses.size(), sampled, name + ": every sampled tick reached");
        System.out.println("TitheBinderRigTest " + name + ": " + sampled + " poses, " + d.values + " values, "
            + d.bad + " off by more than " + EPS);
        assertTrue(d.bad == 0, d.report());
    }

    private static void compare(Diff d, String at, JsonObject want, TitheBinderRig.Pose got) {
        // bones: every bone the driver wrote, and every other rig bone stays at rest
        JsonObject bones = want.getAsJsonObject("bones");
        for (int b = 0; b < TitheBinderRig.BONES.length; b++) {
            String bone = TitheBinderRig.BONES[b];
            JsonObject e = bones.has(bone) ? bones.getAsJsonObject(bone) : null;
            for (int i = 0; i < 3; i++) {
                d.near(at + " " + bone + ".rot[" + i + "]", e == null ? 0.0 : e.getAsJsonArray("rot").get(i).getAsDouble(),
                    got.rot[b][i]);
                d.near(at + " " + bone + ".off[" + i + "]", e == null ? 0.0 : e.getAsJsonArray("off").get(i).getAsDouble(),
                    got.off[b][i]);
            }
        }
        for (String bone : bones.keySet())
            d.truth(at + " bone " + bone + " is a rig bone", TitheBinderRig.bone(bone) >= 0);
        // docked slots
        JsonObject slots = want.getAsJsonObject("slots");
        TreeSet<Integer> wantSlots = new TreeSet<>(), gotSlots = new TreeSet<>();
        for (String k : slots.keySet())
            wantSlots.add(Integer.parseInt(k));
        for (int k = 1; k <= TitheBinderRig.MAX_WEAPONS; k++)
            if (got.slotOn[k])
                gotSlots.add(k);
        d.same(at + " docked slots", wantSlots.toString(), gotSlots.toString());
        for (int k : wantSlots) {
            if (!got.slotOn[k])
                continue;
            JsonObject s = slots.getAsJsonObject(Integer.toString(k));
            String w = at + " slot " + k + ".";
            d.near(w + "angle", s.get("angle").getAsDouble(), got.slotAngle[k]);
            d.near(w + "radial", s.get("radial").getAsDouble(), got.slotRadial[k]);
            d.near(w + "y", s.get("y").getAsDouble(), got.slotY[k]);
            d.near(w + "pitch", s.get("pitch").getAsDouble(), got.slotPitch[k]);
            d.near(w + "roll", s.get("roll").getAsDouble(), got.slotRoll[k]);
            d.near(w + "scale", s.get("scale").getAsDouble(), got.slotScale[k]);
        }
        // flying / falling weapons
        JsonObject flying = want.getAsJsonObject("flying");
        TreeSet<Integer> wantFly = new TreeSet<>(), gotFly = new TreeSet<>();
        for (String k : flying.keySet())
            wantFly.add(Integer.parseInt(k));
        for (int k = 1; k <= TitheBinderRig.MAX_WEAPONS; k++)
            if (got.flyState[k] != TitheBinderRig.FLY_NONE)
                gotFly.add(k);
        d.same(at + " flying weapons", wantFly.toString(), gotFly.toString());
        for (int k : wantFly) {
            if (got.flyState[k] == TitheBinderRig.FLY_NONE)
                continue;
            JsonObject f = flying.getAsJsonObject(Integer.toString(k));
            String w = at + " weapon " + k + " ";
            d.same(w + "state", f.get("state").getAsString(), TitheBinderRig.FLY_STATES[got.flyState[k]]);
            for (int i = 0; i < 3; i++) {
                d.near(w + "pos[" + i + "]", f.getAsJsonArray("pos").get(i).getAsDouble(), got.flyPos[k][i]);
                d.nearAngle(w + "rot[" + i + "]", f.getAsJsonArray("rot").get(i).getAsDouble(), got.flyRot[k][i]);
            }
            d.near(w + "scale", f.get("scale").getAsDouble(), got.flyScale[k]);
        }
        // hidden parts, alpha, lantern
        TreeSet<String> wantHidden = new TreeSet<>(), gotHidden = new TreeSet<>();
        for (JsonElement h : want.getAsJsonArray("hidden"))
            wantHidden.add(h.getAsString());
        for (int k = 1; k <= TitheBinderRig.MAX_WEAPONS; k++)
            if (got.weaponHidden[k])
                gotHidden.add("weapon_" + k);
        if (got.lanternCoreHidden)
            gotHidden.add("lantern_core");
        d.same(at + " hidden", wantHidden.toString(), gotHidden.toString());
        d.near(at + " alpha", want.get("alpha").getAsDouble(), got.alpha);
        d.near(at + " lantern", want.get("lantern").getAsDouble(), got.lantern);
        // ribbons (the soul chains), in the driver's order
        JsonArray ribbons = want.getAsJsonArray("ribbons");
        d.same(at + " ribbon count", Integer.toString(ribbons.size()), Integer.toString(got.ribbons));
        for (int r = 0; r < Math.min(ribbons.size(), got.ribbons); r++) {
            JsonArray rb = ribbons.get(r).getAsJsonArray();
            String w = at + " ribbon " + r + " ";
            for (int i = 0; i < 3; i++) {
                d.near(w + "hub[" + i + "]", rb.get(0).getAsJsonArray().get(i).getAsDouble(), got.ribbonHub[r][i]);
                d.near(w + "end[" + i + "]", rb.get(1).getAsJsonArray().get(i).getAsDouble(), got.ribbonEnd[r][i]);
            }
            d.near(w + "sag", rb.get(2).getAsDouble(), got.ribbonSag[r]);
            d.near(w + "width", rb.get(3).getAsDouble(), got.ribbonWidth[r]);
        }
    }

    /** Counts compared values and keeps the first mismatches for the failure message. */
    private static final class Diff {
        final String clip;
        int values, bad;
        final List<String> first = new ArrayList<>();

        Diff(String clip) {
            this.clip = clip;
        }

        void fail(String msg) {
            bad++;
            if (first.size() < 25)
                first.add(msg);
        }

        void near(String what, double want, double got) {
            values++;
            if (!(Math.abs(want - got) <= EPS))
                fail(what + ": want " + want + ", got " + got);
        }

        void nearAngle(String what, double want, double got) {
            values++;
            double e = Math.abs(want - got) % 360.0;
            if (!(Math.min(e, 360.0 - e) <= EPS))
                fail(what + ": want " + want + ", got " + got);
        }

        void same(String what, String want, String got) {
            values++;
            if (!want.equals(got))
                fail(what + ": want " + want + ", got " + got);
        }

        void truth(String what, boolean ok) {
            values++;
            if (!ok)
                fail(what);
        }

        String report() {
            return clip + ": " + bad + " of " + values + " values off by more than " + EPS + "; first: "
                + String.join("\n  ", first);
        }
    }

    // ------------------------------------------------------------------ the API's shape

    @Test
    void step_reuses_one_state_and_one_pose() throws NoSuchMethodException {
        Method m = TitheBinderRig.class.getMethod("step", TitheBinderRig.State.class, TitheBinderRig.Inputs.class,
            TitheBinderRig.Pose.class);
        assertTrue(Modifier.isStatic(m.getModifiers()), "step is a static function of (state, inputs, outPose)");
        assertEquals(void.class, m.getReturnType(), "step writes into the caller's state and pose, returns nothing");
        TitheBinderRig.State st = new TitheBinderRig.State(10, SEED);
        TitheBinderRig.Pose pose = new TitheBinderRig.Pose();
        TitheBinderRig.Inputs in = new TitheBinderRig.Inputs();
        double[][] rot = pose.rot;
        double[] slotAngle = pose.slotAngle;
        for (int t = 0; t < 60; t++) {
            in.clip = TitheBinderRig.Clip.VOLLEY;
            in.t = t;
            TitheBinderRig.step(st, in, pose);
        }
        assertSame(rot, pose.rot, "the pose's arrays are reused, not replaced");
        assertSame(slotAngle, pose.slotAngle, "the pose's arrays are reused, not replaced");
        assertEquals(10, st.n(), "a volley keeps all 10 weapons");
    }

    @Test
    void step_allocates_nothing_once_warm() {
        java.lang.management.ThreadMXBean mx = ManagementFactory.getThreadMXBean();
        assumeTrue(mx instanceof com.sun.management.ThreadMXBean, "thread allocation counter available");
        com.sun.management.ThreadMXBean bean = (com.sun.management.ThreadMXBean) mx;
        assumeTrue(bean.isThreadAllocatedMemorySupported(), "thread allocation counter supported");
        bean.setThreadAllocatedMemoryEnabled(true);
        TitheBinderRig.State st = new TitheBinderRig.State(10, SEED);
        TitheBinderRig.Inputs in = new TitheBinderRig.Inputs();
        TitheBinderRig.Pose pose = new TitheBinderRig.Pose();
        TitheBinderRig.Clip[] order = {TitheBinderRig.Clip.IDLE, TitheBinderRig.Clip.MOVE, TitheBinderRig.Clip.VOLLEY,
            TitheBinderRig.Clip.FIDGET, TitheBinderRig.Clip.HIT, TitheBinderRig.Clip.IDLE, TitheBinderRig.Clip.DEATH};
        int[] len = {60, 50, 60, 30, 16, 60, 50};
        runClips(st, in, pose, order, len);                 // warm: every branch once (the JIT, lazy class loads)
        st = new TitheBinderRig.State(10, SEED);
        long tid = Thread.currentThread().getId();
        long before = bean.getThreadAllocatedBytes(tid);
        int steps = runClips(st, in, pose, order, len);
        long bytes = bean.getThreadAllocatedBytes(tid) - before;
        System.out.println("TitheBinderRigTest allocation: " + bytes + " bytes over " + steps + " steps");
        assertTrue(bytes < 2048, bytes + " bytes allocated over " + steps + " steps (want none)");
    }

    private static int runClips(TitheBinderRig.State st, TitheBinderRig.Inputs in, TitheBinderRig.Pose pose,
                                TitheBinderRig.Clip[] order, int[] len) {
        int steps = 0;
        for (int c = 0; c < order.length; c++)
            for (int t = 0; t < len[c]; t++) {
                in.clip = order[c];
                in.t = t;
                in.speed = order[c] == TitheBinderRig.Clip.MOVE ? moveSpeed(t) : 0.0;
                TitheBinderRig.step(st, in, pose);
                steps++;
            }
        return steps;
    }

    // ------------------------------------------------------------------ DSL -> ModelPart signs

    /** A part that records what apply() writes, with ModelPart's fields (radians; model px, y down). */
    private static final class Rec implements TitheBinderRig.Part {
        float x, y, z, xRot, yRot, zRot, scale = 1f;
        boolean visible = true;

        Rec(float x, float y, float z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        @Override
        public void addRotation(float xRad, float yRad, float zRad) {
            xRot += xRad;
            yRot += yRad;
            zRot += zRad;
        }

        @Override
        public void addOffset(float dx, float dy, float dz) {
            x += dx;
            y += dy;
            z += dz;
        }

        @Override
        public void setOffset(float px, float py, float pz) {
            x = px;
            y = py;
            z = pz;
        }

        @Override
        public void setRotation(float xRad, float yRad, float zRad) {
            xRot = xRad;
            yRot = yRad;
            zRot = zRad;
        }

        @Override
        public void setScale(float s) {
            scale = s;
        }

        @Override
        public void setVisible(boolean v) {
            visible = v;
        }
    }

    private static Rec part(Map<String, Rec> parts, String name) {
        return parts.computeIfAbsent(name, k -> new Rec(0f, 0f, 0f));
    }

    @Test
    void apply_maps_dsl_signs_onto_model_parts() {
        Map<String, Rec> parts = new HashMap<>();
        TitheBinderRig.Bones bones = new TitheBinderRig.Bones(name -> part(parts, name));
        TitheBinderRig.Pose pose = new TitheBinderRig.Pose();
        double d2r = Math.PI / 180.0;
        // 1. head: a DSL rotation (10, 20, 30) deg -> ModelPart (-10, +20, -30) in radians
        int head = TitheBinderRig.bone("head");
        pose.rot[head][0] = 10.0;
        pose.rot[head][1] = 20.0;
        pose.rot[head][2] = 30.0;
        // 2. hover: a DSL move (1, 2, 3) px (y up) -> ModelPart offset (+1, -2, +3) (y down)
        int hover = TitheBinderRig.bone("hover");
        pose.off[hover][0] = 1.0;
        pose.off[hover][1] = 2.0;
        pose.off[hover][2] = 3.0;
        // 3. ring: the tilt and the yaw (-5, 40, 7) and a lift (0, 1, -4)
        int ring = TitheBinderRig.bone("ring");
        pose.rot[ring][0] = -5.0;
        pose.rot[ring][1] = 40.0;
        pose.rot[ring][2] = 7.0;
        pose.off[ring][1] = 1.0;
        pose.off[ring][2] = -4.0;
        // a docked slot at the generated rest layout: slot_1 yaw -18, radius 18 -> TitheBinderModel's
        // PartPose.offsetAndRotation(5.562F, 0F, -17.119F, 0F, -0.3142F, 0F); its weapon pitched 10, rolled -20
        pose.slotOn[1] = true;
        pose.slotAngle[1] = -18.0;
        pose.slotRadial[1] = 0.0;
        pose.slotPitch[1] = 10.0;
        pose.slotRoll[1] = -20.0;
        pose.slotScale[1] = 1.0;
        pose.weaponHidden[2] = true;
        pose.lanternCoreHidden = true;
        TitheBinderRig.apply(pose, bones);

        Rec h = part(parts, "head");
        assertEquals(-10.0 * d2r, h.xRot, 1e-5, "head x: DSL +x -> xRot -");
        assertEquals(20.0 * d2r, h.yRot, 1e-5, "head y: DSL +y -> yRot +");
        assertEquals(-30.0 * d2r, h.zRot, 1e-5, "head z: DSL +z -> zRot -");
        Rec hv = part(parts, "hover");
        assertEquals(1.0, hv.x, 1e-5, "hover x: same sign");
        assertEquals(-2.0, hv.y, 1e-5, "hover y: DSL up -> model y down");
        assertEquals(3.0, hv.z, 1e-5, "hover z: same sign");
        Rec rg = part(parts, "ring");
        assertEquals(5.0 * d2r, rg.xRot, 1e-5, "ring tilt x");
        assertEquals(40.0 * d2r, rg.yRot, 1e-5, "ring yaw");
        assertEquals(-7.0 * d2r, rg.zRot, 1e-5, "ring tilt z");
        assertEquals(-1.0, rg.y, 1e-5, "ring lift: y down");
        assertEquals(-4.0, rg.z, 1e-5, "ring z");
        Rec s1 = part(parts, "slot_1");
        assertTrue(s1.visible, "a docked slot is shown");
        assertEquals(5.562, s1.x, 1e-3, "slot_1 x = TitheBinderModel's rest offset");
        assertEquals(0.0, s1.y, 1e-3, "slot_1 y");
        assertEquals(-17.119, s1.z, 1e-3, "slot_1 z = TitheBinderModel's rest offset");
        assertEquals(-0.3142, s1.yRot, 1e-4, "slot_1 yaw = TitheBinderModel's rest yRot");
        Rec w1 = part(parts, "weapon_1");
        assertEquals(-10.0 * d2r, w1.xRot, 1e-5, "weapon pitch: DSL +x -> xRot -");
        assertEquals(20.0 * d2r, w1.zRot, 1e-5, "weapon roll: DSL z -> zRot -");
        assertTrue(w1.visible, "a docked weapon is shown");
        assertTrue(!part(parts, "slot_3").visible, "a slot the pose does not dock is hidden (flying, fallen, none)");
        assertTrue(!part(parts, "weapon_2").visible, "a hidden weapon is hidden");
        assertTrue(!part(parts, "lantern_core").visible, "a cracked lantern hides its core");
    }
}
