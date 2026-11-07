package com.solegendary.reignofnether.unit.modelling.animations;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The angels' rig against its spec: tools/angel_anim.py (the clip driver, THE motion spec) samples every 2nd tick of
 * every clip of every kind into design/fixtures/angel_clips.json; the Java rig, run through the same harness (run():
 * seed 1, 40 hover ticks of warm-up, the same inputs), must land on every sampled number within 0.01 (degrees, px,
 * scale, fx): every bone's rotation delta, move and scale, the hidden bones and the fx (white, alpha, halo alpha,
 * glow). The parity tests read whatever the fixture holds (the motion is revised on the render branch: a new fixture
 * and driver arrive together, and the port follows them).
 *
 * <p>Then the motion rules the spec must keep, checked on the rig itself every tick: no joint turns more than 45 deg
 * in one tick in any clip (every bone, props and hidden bones included; local rotation, rest included, from the b4
 * bodies' design/models JSON), and the wing phase is
 * STATE (changing beatTicks mid-flight never jumps the wing more than a normal step; never age / period, #303). Last,
 * the DSL -> ModelPart sign mapping of {@link AngelRig#apply}.
 */
class AngelRigTest {

    private static final Path FIXTURE = Paths.get("..", "design", "fixtures", "angel_clips.json");
    private static final String MODEL = "../design/models/sunforged_angel_b4_%s.json";
    private static final double EPS = 0.01;
    private static final int WARM = 40, SEED = 1;
    private static final String[] KINDS = {"mender", "archer", "sword"};
    private static final String[] CLIPS = {"hover", "fly", "bank", "attack", "hit", "arrive", "death"};
    /** The most any joint may turn in one tick. */
    private static final double MAX_STEP = 45.0;
    private static JsonObject kinds;

    @BeforeAll
    static void load() throws IOException {
        try (Reader r = Files.newBufferedReader(FIXTURE)) {
            JsonObject root = JsonParser.parseReader(r).getAsJsonObject();
            assertEquals(WARM, root.get("warm").getAsInt(), "the fixture's warm-up");
            assertEquals(SEED, root.get("seed").getAsInt(), "the fixture's seed");
            kinds = root.getAsJsonObject("kinds");
        }
    }

    // ------------------------------------------------------------------ the harness (the driver's run())

    /** Steps one clip the way the driver's run() does; calls {@code each} after every clip tick. */
    private static void run(int kind, String clip, int ticks, TickSink each) {
        AngelRig.State st = new AngelRig.State(AngelRig.Kind.values()[kind], SEED, clip.equals("arrive"));
        AngelRig.Inputs in = new AngelRig.Inputs();
        AngelRig.Pose pose = new AngelRig.Pose();
        if (!clip.equals("arrive")) {
            for (int i = 0; i < WARM; i++)
                AngelRig.step(st, in, pose);
            if (clip.equals("bank")) {
                in.speed = AngelRig.CRUISE;
                for (int i = 0; i < 30; i++)
                    AngelRig.step(st, in, pose);
            }
        }
        int e = AngelRig.EVENT[kind];
        for (int t = 0; t < ticks; t++) {
            switch (clip) {
                case "fly" -> in.speed = AngelRig.CRUISE;
                case "bank" -> {
                    in.speed = AngelRig.CRUISE;
                    in.yawRate = t < 16 ? 6.0 : 0.0;
                }
                case "attack" -> {
                    in.attackIn = t <= e ? e - t : -1;
                    if (t == e)
                        in.attackSeq++;
                }
                case "hit" -> in.hurtTime = Math.max(0, 10 - t);
                case "death" -> in.dying = true;
                default -> { }
            }
            AngelRig.step(st, in, pose);
            each.at(t, pose);
        }
    }

    private interface TickSink {
        void at(int t, AngelRig.Pose pose);
    }

    private static int ticks(int kind, String clip) {
        return kinds.getAsJsonObject(KINDS[kind]).getAsJsonObject(clip).get("ticks").getAsInt();
    }

    // ------------------------------------------------------------------ parity with the driver

    @Test
    void mender_matches_the_driver() {
        checkKind(0);
    }

    @Test
    void archer_matches_the_driver() {
        checkKind(1);
    }

    @Test
    void sword_matches_the_driver() {
        checkKind(2);
    }

    private static void checkKind(int kind) {
        String name = KINDS[kind];
        JsonObject ck = kinds.getAsJsonObject(name);
        List<String> report = new ArrayList<>();
        int bad = 0, values = 0;
        for (String clip : CLIPS) {
            JsonObject c = ck.getAsJsonObject(clip);
            if (clip.equals("attack"))
                assertEquals(AngelRig.EVENT[kind], c.get("event").getAsInt(), name + " attack: the contact tick");
            JsonObject poses = c.getAsJsonObject("poses");
            Diff d = new Diff(name + " " + clip);
            int[] sampled = {0};
            run(kind, clip, c.get("ticks").getAsInt(), (t, pose) -> {
                JsonElement p = poses.get(Integer.toString(t));
                if (p == null)
                    return;
                sampled[0]++;
                compare(d, "t" + t, p.getAsJsonObject(), pose);
            });
            d.same("sampled ticks", Integer.toString(poses.size()), Integer.toString(sampled[0]));
            System.out.println("AngelRigTest " + name + " " + clip + ": " + sampled[0] + " poses, " + d.values
                + " values, " + d.bad + " off by more than " + EPS);
            bad += d.bad;
            values += d.values;
            if (d.bad > 0)
                report.add(d.report());
        }
        assertTrue(bad == 0, name + ": " + bad + " of " + values + " values off by more than " + EPS + "\n"
            + String.join("\n", report));
    }

    private static void compare(Diff d, String at, JsonObject want, AngelRig.Pose got) {
        JsonObject move = want.has("_move") ? want.getAsJsonObject("_move") : new JsonObject();
        JsonObject scale = want.has("_scale") ? want.getAsJsonObject("_scale") : new JsonObject();
        for (int b = 0; b < AngelRig.N; b++) {
            String bone = AngelRig.BONES[b];
            JsonArray r = want.has(bone) ? want.getAsJsonArray(bone) : null;
            JsonArray m = move.has(bone) ? move.getAsJsonArray(bone) : null;
            JsonArray s = scale.has(bone) ? scale.getAsJsonArray(bone) : null;
            for (int i = 0; i < 3; i++) {
                d.nearAngle(at + " " + bone + ".rot[" + i + "]", r == null ? 0.0 : r.get(i).getAsDouble(), got.rot[b][i]);
                d.near(at + " " + bone + ".move[" + i + "]", m == null ? 0.0 : m.get(i).getAsDouble(), got.move[b][i]);
                d.near(at + " " + bone + ".scale[" + i + "]", s == null ? 1.0 : s.get(i).getAsDouble(), got.scale[b][i]);
            }
        }
        for (String key : want.keySet())
            if (!key.startsWith("_"))
                d.truth(at + " bone " + key + " is a rig bone", AngelRig.bone(key) >= 0);
        for (String key : move.keySet())
            d.truth(at + " moved bone " + key + " is a rig bone", AngelRig.bone(key) >= 0);
        for (String key : scale.keySet())
            d.truth(at + " scaled bone " + key + " is a rig bone", AngelRig.bone(key) >= 0);
        TreeSet<String> wantHidden = new TreeSet<>(), gotHidden = new TreeSet<>();
        if (want.has("_hide"))
            for (JsonElement h : want.getAsJsonArray("_hide"))
                wantHidden.add(h.getAsString());
        for (int b = 0; b < AngelRig.N; b++)
            if (got.hidden[b])
                gotHidden.add(AngelRig.BONES[b]);
        d.same(at + " hidden", wantHidden.toString(), gotHidden.toString());
        JsonObject fx = want.getAsJsonObject("_fx");
        d.near(at + " fx.white", fx.get("white").getAsDouble(), got.white);
        d.near(at + " fx.alpha", fx.get("alpha").getAsDouble(), got.alpha);
        d.near(at + " fx.halo_alpha", fx.get("halo_alpha").getAsDouble(), got.haloAlpha);
        d.near(at + " fx.glow", fx.get("glow").getAsDouble(), got.glow);
        for (String key : fx.keySet())
            d.truth(at + " fx key " + key + " is ported",
                key.equals("white") || key.equals("alpha") || key.equals("halo_alpha") || key.equals("glow"));
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
            if (first.size() < 12)
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
            return "  " + clip + ": " + bad + " of " + values + " off; first: " + String.join("; ", first);
        }
    }

    // ------------------------------------------------------------------ joint steps

    /** The b4 body's rest rotation per rig bone (DSL degrees), null where the body has no such bone. */
    private static double[][] rests(int kind) throws IOException {
        double[][] rest = new double[AngelRig.N][];
        try (Reader r = Files.newBufferedReader(Paths.get(String.format(MODEL, KINDS[kind])))) {
            for (JsonElement e : JsonParser.parseReader(r).getAsJsonObject().getAsJsonArray("bones")) {
                JsonObject b = e.getAsJsonObject();
                int i = AngelRig.bone(b.get("name").getAsString());
                if (i < 0)
                    continue;
                double[] v = {0.0, 0.0, 0.0};
                if (b.has("rotation"))
                    for (int a = 0; a < 3; a++)
                        v[a] = b.getAsJsonArray("rotation").get(a).getAsDouble();
                rest[i] = v;
            }
        }
        return rest;
    }

    /** DSL rotation matrix (degrees, Rz Ry Rx). */
    private static double[][] rmat(double x, double y, double z) {
        double cx = Math.cos(Math.toRadians(x)), sx = Math.sin(Math.toRadians(x));
        double cy = Math.cos(Math.toRadians(y)), sy = Math.sin(Math.toRadians(y));
        double cz = Math.cos(Math.toRadians(z)), sz = Math.sin(Math.toRadians(z));
        return new double[][]{
            {cz * cy, cz * sy * sx - sz * cx, cz * sy * cx + sz * sx},
            {sz * cy, sz * sy * sx + cz * cx, sz * sy * cx - cz * sx},
            {-sy, cy * sx, cy * cx}};
    }

    /** The angle (deg) between two rotations. */
    private static double geo(double[][] a, double[][] b) {
        double tr = 0.0;
        for (int i = 0; i < 3; i++)
            for (int j = 0; j < 3; j++)
                tr += a[j][i] * b[j][i];
        return Math.toDegrees(Math.acos(Math.max(-1.0, Math.min(1.0, (tr - 1.0) / 2.0))));
    }

    /** Every bone's local rotation (rest + the pose's delta), null for a bone the body lacks (hidden ones count). */
    private static double[][][] local(double[][] rest, AngelRig.Pose pose) {
        double[][][] r = new double[AngelRig.N][][];
        for (int b = 0; b < AngelRig.N; b++)
            if (rest[b] != null)
                r[b] = rmat(rest[b][0] + pose.rot[b][0], rest[b][1] + pose.rot[b][1], rest[b][2] + pose.rot[b][2]);
        return r;
    }

    @Test
    void no_joint_turns_more_than_45_degrees_in_a_tick() throws IOException {
        List<String> over = new ArrayList<>();
        double hoverMax = 0.0;
        for (int kind = 0; kind < 3; kind++) {
            double[][] rest = rests(kind);
            for (String clip : CLIPS) {
                double[][][][] prev = {null};
                double[] worst = {0.0};
                String[] where = {""};
                run(kind, clip, ticks(kind, clip), (t, pose) -> {
                    double[][][] cur = local(rest, pose);
                    if (prev[0] != null)
                        for (int b = 0; b < AngelRig.N; b++)
                            if (cur[b] != null && prev[0][b] != null) {
                                double g = geo(prev[0][b], cur[b]);
                                if (g > worst[0]) {
                                    worst[0] = g;
                                    where[0] = AngelRig.BONES[b] + " at t" + t;
                                }
                            }
                    prev[0] = cur;
                });
                System.out.printf("AngelRigTest step %s %s: %.1f deg (%s)%n", KINDS[kind], clip, worst[0], where[0]);
                if (worst[0] > MAX_STEP)
                    over.add(String.format("%s %s %.1f deg (%s)", KINDS[kind], clip, worst[0], where[0]));
                if (clip.equals("hover"))
                    hoverMax = Math.max(hoverMax, worst[0]);
            }
        }
        assertTrue(hoverMax > 1.0, "the hover never moves a joint more than " + hoverMax + " deg a tick: the wings "
            + "do not beat");
        assertTrue(over.isEmpty(), "joints turning more than " + MAX_STEP + " deg in one tick: " + over);
    }

    @Test
    void the_wing_phase_is_state_a_tempo_change_never_jumps_the_wing() throws IOException {
        List<String> bad = new ArrayList<>();
        int[] wings = {AngelRig.WING_ARM[0], AngelRig.WING_FORE[0], AngelRig.WING_HAND[0], AngelRig.WING_TIPS[0],
            AngelRig.WING_ARM[1], AngelRig.WING_FORE[1], AngelRig.WING_HAND[1], AngelRig.WING_TIPS[1]};
        for (int kind = 0; kind < 3; kind++) {
            double[][] rest = rests(kind);
            for (double factor : new double[]{0.5, 0.7, 1.5}) {
                AngelRig.State st = new AngelRig.State(AngelRig.Kind.values()[kind], SEED, false);
                AngelRig.Inputs in = new AngelRig.Inputs();
                AngelRig.Pose pose = new AngelRig.Pose();
                double[][][] prev = null;
                double normal = 0.0, jump = 0.0;
                for (int t = 0; t < 120; t++) {
                    if (t == 80)
                        st.beat *= factor;                      // beatTicks changed mid-flight
                    AngelRig.step(st, in, pose);
                    double[][][] cur = local(rest, pose);
                    if (prev != null) {
                        double g = 0.0;
                        for (int b : wings)
                            g = Math.max(g, geo(prev[b], cur[b]));
                        if (t >= 40 && t < 80)
                            normal = Math.max(normal, g);
                        if (t == 80)
                            jump = g;
                    }
                    prev = cur;
                }
                System.out.printf("AngelRigTest phase %s x%.1f: normal step %.2f deg, at the change %.2f deg%n",
                    KINDS[kind], factor, normal, jump);
                if (normal <= 1.0)
                    bad.add(KINDS[kind] + ": the hovering wings do not beat (largest step " + normal + " deg)");
                else if (jump > normal)
                    bad.add(String.format("%s x%.1f: the wing jumped %.2f deg at the tempo change, a normal step is %.2f",
                        KINDS[kind], factor, jump, normal));
            }
        }
        assertTrue(bad.isEmpty(), String.join("; ", bad));
    }

    // ------------------------------------------------------------------ the passes fade with the body

    /** The pose the driver's run() reaches at tick {@code at} of {@code clip}. */
    private static AngelRig.Pose poseAt(int kind, String clip, int at) {
        AngelRig.Pose[] got = {null};
        run(kind, clip, at + 1, (t, pose) -> {
            if (t == at) {
                got[0] = new AngelRig.Pose();
                got[0].set(pose);
            }
        });
        return got[0];
    }

    private static double fx(int kind, String clip, int t, String key) {
        return kinds.getAsJsonObject(KINDS[kind]).getAsJsonObject(clip).getAsJsonObject("poses")
            .getAsJsonObject(Integer.toString(t)).getAsJsonObject("_fx").get(key).getAsDouble();
    }

    /**
     * Every pass of the body - the paint, the team mask, the glow - fades with it: at the arrival's t4 and the death's
     * t12 (the fixture's alpha and halo alpha) the body, the lantern and the halo draw at the driver's alpha / halo
     * alpha in both passes and the passes go translucent; the Mender's heal (attack t10, glow 1.6) brightens only the
     * glow pass's lantern (once at full, once more at 0.6); a hovering angel draws everything at 1, opaque.
     */
    @Test
    void every_pass_fades_with_the_body_and_the_halo_with_its_own_alpha() {
        double[] f = new double[4];
        for (int kind = 0; kind < 3; kind++)
            for (Object[] at : new Object[][]{{"arrive", 4}, {"death", 12}}) {
                String clip = (String) at[0];
                int t = (Integer) at[1];
                AngelRig.Pose p = poseAt(kind, clip, t);
                double alpha = fx(kind, clip, t, "alpha"), halo = fx(kind, clip, t, "halo_alpha");
                String where = KINDS[kind] + " " + clip + " t" + t;
                assertTrue(alpha < 0.999, where + ": the fixture fades the body here (alpha " + alpha + ")");
                for (boolean glow : new boolean[]{false, true}) {
                    AngelRig.passFactors(p, glow, f);
                    String pass = where + (glow ? " glow pass" : " paint / team pass");
                    assertEquals(alpha, f[AngelRig.F_BODY], EPS, pass + ": the body at the driver's alpha");
                    assertEquals(halo, f[AngelRig.F_HALO], EPS, pass + ": the halo at the driver's halo alpha");
                    assertEquals(alpha, f[AngelRig.F_LANTERN], EPS, pass + ": the lantern fades with the body");
                    assertEquals(0.0, f[AngelRig.F_LANTERN_EXTRA], EPS, pass + ": no extra lantern light");
                }
                assertTrue(AngelRig.translucent(p), where + ": the passes draw translucent while it fades");
            }
        AngelRig.Pose heal = poseAt(0, "attack", 10);
        assertEquals(1.6, fx(0, "attack", 10, "glow"), EPS, "the fixture's heal glow");
        AngelRig.passFactors(heal, true, f);
        assertEquals(1.0, f[AngelRig.F_LANTERN], EPS, "the heal: the lantern's glow at full");
        assertEquals(0.6, f[AngelRig.F_LANTERN_EXTRA], EPS, "the heal: and 0.6 more on top (x1.6)");
        assertEquals(1.0, f[AngelRig.F_BODY], EPS, "the heal: the body's own glow unchanged");
        AngelRig.passFactors(heal, false, f);
        assertEquals(0.0, f[AngelRig.F_LANTERN_EXTRA], EPS, "the heal brightens the glow pass only");
        AngelRig.Pose hover = poseAt(2, "hover", 10);
        AngelRig.passFactors(hover, true, f);
        for (int i = 0; i < 3; i++)
            assertEquals(1.0, f[i], EPS, "hovering: group " + i + " draws in full");
        assertTrue(!AngelRig.translucent(hover), "hovering: the passes stay opaque");
    }

    // ------------------------------------------------------------------ no allocation per tick

    /**
     * One kind's tour of its clips on {@code st} (hover, fly, the attack with its cue, a hit, the death; the arrival on
     * {@code arrive}): about 200 steps, the inputs set the way the driver's run() sets them.
     */
    private static int tour(int kind, AngelRig.State st, AngelRig.State arrive, AngelRig.Inputs in, AngelRig.Pose pose) {
        int steps = 0, e = AngelRig.EVENT[kind];
        in.speed = 0.0;
        for (int t = 0; t < 40; t++, steps++)
            AngelRig.step(st, in, pose);
        in.speed = AngelRig.CRUISE;
        in.yawRate = 6.0;
        for (int t = 0; t < 40; t++, steps++)
            AngelRig.step(st, in, pose);
        in.speed = 0.0;
        in.yawRate = 0.0;
        for (int t = 0; t < AngelRig.ATTACK_LEN[kind] + 4; t++, steps++) {
            in.attackIn = t <= e ? e - t : -1;
            if (t == e)
                in.attackSeq++;
            AngelRig.step(st, in, pose);
        }
        for (int t = 0; t < 14; t++, steps++) {
            in.hurtTime = Math.max(0, 10 - t);
            AngelRig.step(st, in, pose);
        }
        for (int t = 0; t < AngelRig.ARRIVE_LEN + 4; t++, steps++)
            AngelRig.step(arrive, in, pose);
        in.dying = true;
        for (int t = 0; t < AngelRig.DEATH_LEN + 4; t++, steps++)
            AngelRig.step(st, in, pose);
        in.dying = false;
        return steps;
    }

    /** The most a warm step may allocate, on average (bytes): HotSpot's counter reads 0 for an allocation-free loop. */
    private static final double MAX_BYTES_PER_STEP = 64.0;

    /**
     * The rig steps once per client tick for every angel on screen, so a step allocates nothing. Allocation, not time:
     * after a warm-up of at least 200 steps (one tour of every clip per kind on other states: classes loaded, the key
     * tables built), about 1000 steps of every clip of every kind on fresh states (made before the counter is read)
     * must average under {@link #MAX_BYTES_PER_STEP} bytes, read with the thread's allocation counter. The allocating
     * port measured ~15 KB per step here (8,498,448 bytes over 550 steps), so the cap is not fitted to the code.
     * Skipped, not passed, where the JVM has no such counter.
     */
    @Test
    void step_allocates_nothing_once_warm() {
        java.lang.management.ThreadMXBean mx = java.lang.management.ManagementFactory.getThreadMXBean();
        assumeTrue(mx instanceof com.sun.management.ThreadMXBean, "the thread allocation counter is available");
        com.sun.management.ThreadMXBean bean = (com.sun.management.ThreadMXBean) mx;
        assumeTrue(bean.isThreadAllocatedMemorySupported(), "the thread allocation counter is supported");
        bean.setThreadAllocatedMemoryEnabled(true);
        long tid = Thread.currentThread().getId();
        AngelRig.Kind[] kindsOf = AngelRig.Kind.values();
        int warm = 0;
        for (int kind = 0; kind < 3; kind++)
            warm += tour(kind, new AngelRig.State(kindsOf[kind], SEED, false),
                new AngelRig.State(kindsOf[kind], SEED, true), new AngelRig.Inputs(), new AngelRig.Pose());
        assertTrue(warm >= 200, "the warm-up ran " + warm + " steps");
        int rounds = 2;                                  // two tours of the three kinds: ~1100 steps
        AngelRig.State[] st = new AngelRig.State[3 * rounds], arrive = new AngelRig.State[3 * rounds];
        AngelRig.Inputs[] in = new AngelRig.Inputs[3 * rounds];
        AngelRig.Pose[] pose = new AngelRig.Pose[3 * rounds];
        for (int i = 0; i < 3 * rounds; i++) {
            st[i] = new AngelRig.State(kindsOf[i % 3], SEED, false);
            arrive[i] = new AngelRig.State(kindsOf[i % 3], SEED, true);
            in[i] = new AngelRig.Inputs();
            pose[i] = new AngelRig.Pose();
        }
        long before = bean.getThreadAllocatedBytes(tid);
        int steps = 0;
        for (int i = 0; i < 3 * rounds; i++)
            steps += tour(i % 3, st[i], arrive[i], in[i], pose[i]);
        long bytes = bean.getThreadAllocatedBytes(tid) - before;
        double perStep = bytes / (double) steps;
        System.out.printf("AngelRigTest allocation: %d bytes over %d steps (%.1f per step; warm-up %d steps)%n", bytes,
            steps, perStep, warm);
        assertTrue(steps >= 1000, "measured " + steps + " steps");
        assertTrue(perStep < MAX_BYTES_PER_STEP, String.format("%d bytes over %d warm steps = %.1f per step (cap %.0f): "
            + "the rig must not allocate per tick", bytes, steps, perStep, MAX_BYTES_PER_STEP));
    }

    // ------------------------------------------------------------------ DSL -> ModelPart signs

    /** A part that records what apply() writes, with ModelPart's fields (radians; model px, y down). */
    private static final class Rec implements AngelRig.Part {
        float x, y, z, xRot, yRot, zRot, xs = 1f, ys = 1f, zs = 1f;
        boolean visible = true;

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
        public void setScale(float sx, float sy, float sz) {
            xs = sx;
            ys = sy;
            zs = sz;
        }

        @Override
        public void setVisible(boolean v) {
            visible = v;
        }
    }

    @Test
    void apply_maps_dsl_signs_onto_model_parts() {
        Map<String, Rec> parts = new HashMap<>();
        AngelRig.Bones bones = new AngelRig.Bones(name -> name.equals("lantern") ? null
            : parts.computeIfAbsent(name, k -> new Rec()));
        AngelRig.Pose pose = new AngelRig.Pose();
        double d2r = Math.PI / 180.0;
        int head = AngelRig.bone("head"), body = AngelRig.bone("body"), arrow = AngelRig.bone("arrow");
        pose.rot[head][0] = 10.0;
        pose.rot[head][1] = 20.0;
        pose.rot[head][2] = 30.0;
        pose.move[body][0] = 1.0;
        pose.move[body][1] = 2.0;
        pose.move[body][2] = 3.0;
        pose.scale[body][1] = 1.06;
        pose.hidden[arrow] = true;
        AngelRig.apply(pose, bones);
        Rec h = parts.get("head");
        assertEquals(-10.0 * d2r, h.xRot, 1e-5, "head x: DSL +x -> xRot -");
        assertEquals(20.0 * d2r, h.yRot, 1e-5, "head y: DSL +y -> yRot +");
        assertEquals(-30.0 * d2r, h.zRot, 1e-5, "head z: DSL +z -> zRot -");
        Rec b = parts.get("body");
        assertEquals(1.0, b.x, 1e-5, "body x: same sign");
        assertEquals(-2.0, b.y, 1e-5, "body y: DSL up -> model y down");
        assertEquals(3.0, b.z, 1e-5, "body z: same sign");
        assertEquals(1.06, b.ys, 1e-5, "body scale y");
        assertEquals(1.0, b.xs, 1e-5, "body scale x stays 1");
        assertTrue(!parts.get("arrow").visible, "a hidden bone is not drawn");
        assertTrue(parts.get("sword").visible, "every other bone is drawn");
        assertTrue(!parts.containsKey("lantern"), "a bone the body lacks is skipped");
    }
}
