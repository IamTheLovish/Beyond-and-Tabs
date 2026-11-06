package com.solegendary.reignofnether.unit.modelling.animations;

import com.solegendary.reignofnether.unit.modelling.animations.TitheBinderRig.Clip;
import com.solegendary.reignofnether.unit.modelling.animations.TitheBinderRig.Pose;

/**
 * The Tithe Binder's rig in play: one per unit on the client (TitheBinderUnit.clientDrive, made on the first draw and
 * gone with the entity). The renderer fills {@link #signals} from the unit and calls {@link #catchUp} with the
 * entity's tickCount; this steps {@link TitheBinderRig} once per game tick (never per frame), keeps the last two
 * poses, and {@link #blend} lerps them by partialTick for the frame. Pure maths, like the rig: no Minecraft class
 * here, so TitheBinderDriveTest drives it without a game.
 *
 * <p>Which clip plays (the rig takes one clip at a time):
 * <ul>
 *   <li>death wins and holds (DEATH, t from the first dead tick);</li>
 *   <li>a volley cue (the server's counter steps at the wind-up's first tick) starts VOLLEY at t0 with the victim's
 *       chest as the target; nothing else interrupts it;</li>
 *   <li>one weapon more than the wheel holds is a BIND (after a volley ends); any other change of the count (the
 *       restless decay, a jump) rebuilds the wheel at the new count;</li>
 *   <li>a hit (hurtTime rising) flinches (HIT) when nothing else is playing;</li>
 *   <li>else MOVE while the ground speed is up (forward speed / 0.25 blocks per tick, eased), IDLE after 10 still
 *       ticks, and a FIDGET every 200-400 idle ticks.</li>
 * </ul>
 * Grave Hook has no clip in the driver (r6b): {@link #hook} lays a short overlay on top of whatever plays, on the
 * server's clock (TitheLedger: the wrap at t21, the drag t22-37): the throwing arm and the twist (t0-14), the hook
 * thrown from slot 1 at t13 and flying straight to the target (t13-20), held on it while it is dragged (t21-37, an
 * impact flash at the wrap), reeled home (t38-45). The hook is drawn as a flying weapon (Pose.fly*[1]) and its chain
 * as a soul-chain ribbon, so the renderer needs no special case.
 */
public final class TitheBinderDrive {

    /** More ticks behind than this (off screen, a lag spike) and the drive starts over instead of catching up. */
    public static final int MAX_CATCH_UP = 40;
    /** Blocks per tick that count as the driver's full move speed (v / 0.25). */
    public static final double MOVE_FULL = 0.25;
    static final double SPEED_EASE = 0.35, MOVE_ON = 0.02;
    static final int IDLE_AFTER = 10;
    static final int BIND_TICKS = 32, HIT_TICKS = 14, FIDGET_TICKS = 30, FIDGET_MIN = 200, FIDGET_SPAN = 200;
    /** Grave Hook overlay ticks (server: TitheLedger.HOOK_WRAP_TICK 21, DRAG_TICKS 16). */
    public static final int HOOK_RELEASE = 13, HOOK_WRAP = 21, HOOK_PULL_END = 37, HOOK_DOCK = 45;
    static final int HOOK_FLASH_TICKS = 3;
    static final double HOOK_SCALE = 1.5, HOOK_SPIN = 60.0, HOOK_WIDTH = 1.6;
    /** Where the volley aims when the client cannot see the victim (5 blocks ahead, chest high). */
    static final double[] DEFAULT_TARGET = {0.0, 20.0, -80.0};
    /** Where the bind's lash reaches when the corpse is unknown: 2 blocks ahead and to the right (-x). */
    static final double[] DEFAULT_CORPSE = {-20.0, 4.0, -28.0};
    /** Where the hook flies when its target is not on this client: 6 blocks ahead. */
    static final double[] DEFAULT_HOOK = {0.0, 14.0, -96.0};
    /**
     * The lantern's sideways swing (ingame critic r1: "the lantern doesn't visibly swing"): its chain rolls toward the
     * unit's left while the body turns right (it lags the yaw), SWING_GAIN deg per deg/tick of turn, at most SWING_MAX,
     * on the chains' own spring (sheet 4.3, K_CHAIN / C_CHAIN: about one overshoot back), plus an idle sway of
     * SWAY_DEG over SWAY_TICKS. The rig's own lantern spring (the lean, x) is untouched.
     */
    public static final double SWING_GAIN = 0.45, SWING_MAX = 12.0, SWAY_DEG = 2.0;
    static final int SWAY_TICKS = 48;
    /** px: a ribbon thinner than this is a docked chain (the rig's 1.2; thrown ones are 1.6 and up). */
    static final double DOCKED_RIBBON_MAX = 1.3;

    /** What the unit shows this tick, in plain numbers (the renderer fills it from the entity). */
    public static final class Signals {
        /** Horizontal speed along the body's facing, blocks per tick (backwards counts as 0). */
        public double speed;
        /** Degrees the body turned this tick (yBodyRot - yBodyRotO, wrapped): + = turning to its right. */
        public double yawRate;
        /** Bound weapons, 1-10 (TitheBinderUnit.getWeapons). */
        public int weapons = 1;
        /** The server's volley cue counter (steps once per wind-up) and the victim's chest in model px, if seen. */
        public int volleySeq;
        public boolean volleyHasTarget;
        public final double[] volleyTarget = new double[3];
        /** The server's Grave Hook cue counter (steps once per throw) and the hooked unit's waist in model px. */
        public int hookSeq;
        public boolean hookHasTarget;
        public final double[] hookTarget = new double[3];
        /** LivingEntity.hurtTime (10 on the tick a hit lands, counting down). */
        public int hurtTime;
        /** Dead or dying (LivingEntity.isDeadOrDying). */
        public boolean dead;
    }

    public final Signals signals = new Signals();
    private final long seed;
    private TitheBinderRig.State st;
    private final TitheBinderRig.Inputs in = new TitheBinderRig.Inputs();
    private Pose prev = new Pose(), cur = new Pose();
    private final Pose out = new Pose();
    private boolean ready;
    private int lastTick;
    private Clip clip = Clip.IDLE;
    private int t, clipLength;
    private int seenVolley, seenHook, lastHurt;
    private double speed;
    private int still;
    private int fidgetIn;
    private long rand;
    // the lantern's sideways swing (deg, deg/tick) and the idle sway's clock
    private double swing, swingV;
    private int swayT;
    // Grave Hook
    private int hookAge = -1;
    private boolean hookLost, hookSeen;
    private final double[] hookTarget = new double[3], hookFrom = new double[3], hookPos = new double[3];
    private final double[] reelFrom = new double[3];
    private final double[] va = new double[3], vb = new double[3], vc = new double[3], vd = new double[3];
    private final double[] ma = new double[9], qa = new double[4], qb = new double[4], qc = new double[4];
    private final TitheBinderRig.Keys keys = new TitheBinderRig.Keys();

    public TitheBinderDrive(long seed) {
        this.seed = seed;
        this.rand = seed & 0x7FFFFFFFL;
    }

    // ------------------------------------------------------------------ reads

    /** Stepped at least once: {@link #blend} has two poses to lerp. */
    public boolean ready() {
        return ready;
    }

    /** The clip playing now and the tick inside it (the next step's t). */
    public Clip clip() {
        return clip;
    }

    public int clipTick() {
        return t;
    }

    /** Weapons on the wheel now (a bind adds one at its t0). */
    public int weapons() {
        return st == null ? 0 : st.n();
    }

    /** Ticks since the Grave Hook cue (-1: no hook out). */
    public int hookAge() {
        return hookAge;
    }

    /** The newest stepped pose (tick time, no partialTick). */
    public Pose current() {
        return cur;
    }

    /** The pose {@link #blend} wrote last (what was drawn). */
    public Pose blended() {
        return out;
    }

    /** True when {@code tickCount} is a tick not stepped yet: the caller fills {@link #signals}, then catches up. */
    public boolean behind(int tickCount) {
        return !ready || tickCount != lastTick;
    }

    // ------------------------------------------------------------------ stepping

    /**
     * Steps once per game tick up to {@code tickCount} (the entity's), reading {@link #signals}. The first call, a
     * jump back (the entity re-tracked) or more than {@link #MAX_CATCH_UP} ticks behind starts over from the signals.
     */
    public void catchUp(int tickCount) {
        if (!ready) {
            start();
            lastTick = tickCount;
            return;
        }
        int behind = tickCount - lastTick;
        if (behind == 0)
            return;
        if (behind < 0 || behind > MAX_CATCH_UP) {
            start();
        } else {
            for (int i = 0; i < behind; i++)
                tick();
        }
        lastTick = tickCount;
    }

    /** A fresh wheel at the signalled count; past cues are not replayed. Two steps, so both poses are live. */
    private void start() {
        Signals s = signals;
        st = new TitheBinderRig.State(clampN(s.weapons), seed);
        seenVolley = s.volleySeq;
        seenHook = s.hookSeq;
        lastHurt = s.hurtTime;
        hookAge = -1;
        speed = 0.0;
        swing = swingV = 0.0;
        swayT = 0;
        still = IDLE_AFTER;
        fidgetIn = nextFidget();
        begin(s.dead ? Clip.DEATH : Clip.IDLE, 0);
        ready = true;
        tick();
        tick();
    }

    private void tick() {
        Pose p = prev;
        prev = cur;
        cur = p;
        choose();
        in.clip = clip;
        in.t = t;
        in.speed = clip == Clip.MOVE ? speed : 0.0;
        TitheBinderRig.step(st, in, cur);
        swingLantern();
        flattenWheel();
        hook();
        t++;
    }

    /**
     * The wheel as a sawblade (in-game critic r2: from the RTS camera the wheel read as a tangle): at rest and on the
     * move the docked blades lie flat (no pitch bob: the tips level with the ring) and their soul chains run taut from
     * the ribs (no sag inside the ring), so from ~52 deg down it reads as a ring of blades. The clips that throw, bind,
     * flinch, fidget or fall keep the rig's own pitch and sag; the Grave Hook (laid on after this) keeps its chain.
     */
    private void flattenWheel() {
        if (clip != Clip.IDLE && clip != Clip.MOVE)
            return;
        Pose o = cur;
        for (int k = 1; k <= TitheBinderRig.MAX_WEAPONS; k++)
            if (o.slotOn[k])
                o.slotPitch[k] = 0.0;
        for (int i = 0; i < o.ribbons; i++)
            if (o.ribbonWidth[i] < DOCKED_RIBBON_MAX)
                for (int c = 0; c < 3; c++)
                    o.ribbonCtrl[i][c] = 0.5 * (o.ribbonHub[i][c] + o.ribbonEnd[i][c]);
    }

    /** The hover's lift in the pose last blended (DSL px, + up: the rig's 2 px bob, the move's dip): the shadow's size. */
    public double hoverPx() {
        return out.off[TitheBinderRig.HOVER][1];
    }

    /** Adds the lantern's sideways swing onto {@code cur} (DSL degrees: + rolls the lantern to the unit's left). */
    private void swingLantern() {
        swayT = (swayT + 1) % SWAY_TICKS;
        if (clip == Clip.DEATH) {
            swing = swingV = 0.0;
            return;
        }
        double turn = Math.max(-SWING_MAX, Math.min(SWING_MAX, SWING_GAIN * signals.yawRate));
        double target = turn + SWAY_DEG * Math.sin(2 * Math.PI * swayT / SWAY_TICKS);
        swingV += TitheBinderRig.K_CHAIN * (target - swing) - TitheBinderRig.C_CHAIN * swingV;
        swing += swingV;
        cur.rot[TitheBinderRig.LANTERN_CHAIN][2] += swing;
    }

    private void begin(Clip c, int length) {
        clip = c;
        t = 0;
        clipLength = length;
    }

    /** The volley clip's length for n weapons: the last release, its return, the idle fade (the rig's fEnd + 6). */
    static int volleyTicks(int n) {
        return TitheBinderRig.VOLLEY_RELEASE + TitheBinderRig.RELEASE_GAP * (n - 1) + 18;
    }

    static int clampN(int n) {
        return Math.max(1, Math.min(10, n));
    }

    private int nextFidget() {
        rand = (rand * 1103515245L + 12345L) & 0x7FFFFFFFL;
        return FIDGET_MIN + (int) (FIDGET_SPAN * (rand / (double) 0x7FFFFFFF));
    }

    private void target(double[] p) {
        System.arraycopy(p, 0, in.target, 0, 3);
    }

    /** Picks this tick's clip from the signals (the rules in the class comment). */
    private void choose() {
        Signals s = signals;
        double v = Math.max(0.0, Math.min(1.0, s.speed / MOVE_FULL));
        speed += (v - speed) * SPEED_EASE;
        if (speed < 1e-4)
            speed = 0.0;
        still = speed > MOVE_ON ? 0 : Math.min(still + 1, IDLE_AFTER);
        boolean hurt = s.hurtTime > lastHurt;
        lastHurt = s.hurtTime;
        boolean volley = s.volleySeq != seenVolley;
        seenVolley = s.volleySeq;
        if (s.hookSeq != seenHook) {
            seenHook = s.hookSeq;
            if (!s.dead && clip != Clip.DEATH)
                startHook();
        }
        if (clip == Clip.DEATH)
            return;
        if (s.dead) {
            hookAge = -1;
            begin(Clip.DEATH, 0);
            return;
        }
        boolean playing = clipLength > 0 && t < clipLength;
        if (volley) {
            target(s.volleyHasTarget ? s.volleyTarget : DEFAULT_TARGET);
            begin(Clip.VOLLEY, volleyTicks(st.n()));
            return;
        }
        if (playing && (clip == Clip.VOLLEY || clip == Clip.BIND))
            return;
        int want = clampN(s.weapons);
        if (want != st.n()) {
            if (want == st.n() + 1) {
                target(DEFAULT_CORPSE);
                begin(Clip.BIND, BIND_TICKS);
                return;
            }
            st = new TitheBinderRig.State(want, seed);    // a decay or a jump: the wheel is rebuilt (rare)
        }
        if (hurt && hookAge < 0) {
            begin(Clip.HIT, HIT_TICKS);
            return;
        }
        if (playing)
            return;
        if (still < IDLE_AFTER) {
            if (clip != Clip.MOVE)
                begin(Clip.MOVE, 0);
            return;
        }
        if (clip != Clip.IDLE)
            begin(Clip.IDLE, 0);
        if (--fidgetIn <= 0) {
            fidgetIn = nextFidget();
            if (hookAge < 0)
                begin(Clip.FIDGET, FIDGET_TICKS);
        }
    }

    // ------------------------------------------------------------------ Grave Hook (overlay on the stepped pose)

    private void startHook() {
        hookAge = 0;
        hookLost = false;
        hookSeen = signals.hookHasTarget;
        System.arraycopy(signals.hookHasTarget ? signals.hookTarget : DEFAULT_HOOK, 0, hookTarget, 0, 3);
    }

    /** Adds the hook's tick onto {@code cur}: body deltas, weapon 1 out of its slot, its chain. */
    private void hook() {
        if (hookAge < 0)
            return;
        Signals s = signals;
        if (clip == Clip.DEATH || hookAge > HOOK_DOCK) {
            hookAge = -1;
            return;
        }
        int a = hookAge++;
        if (s.hookHasTarget && !hookLost) {
            System.arraycopy(s.hookTarget, 0, hookTarget, 0, 3);
            hookSeen = true;
        } else if (hookSeen && !hookLost && a <= HOOK_PULL_END) {
            // the target died or the server let go: nothing is thrown, or the hook comes home from where it is
            hookLost = true;
            if (a <= HOOK_RELEASE) {
                hookAge = -1;
                return;
            }
            System.arraycopy(hookPos, 0, reelFrom, 0, 3);
            a = HOOK_PULL_END + 1;
            hookAge = a + 1;
        }
        Pose o = cur;
        TitheBinderRig.Keys K = keys;
        // the body (sheet 5.7): the side-arm wind-up and twist, the sling, the haul
        o.rot[TitheBinderRig.R_ARM][0] += K.of(0, 0).k(9, 180).k(12, 180).k(14, 40).k(HOOK_WRAP, 60)
            .k(HOOK_PULL_END, -10).k(HOOK_DOCK, 0).at(a);
        o.rot[TitheBinderRig.R_ARM][2] += K.of(0, 0).k(9, -40).k(12, -40).k(14, 0).at(a);
        o.rot[TitheBinderRig.L_ARM][0] += K.of(0, 0).k(HOOK_WRAP, 0).k(HOOK_WRAP + 3, 60).k(HOOK_PULL_END, -10)
            .k(HOOK_DOCK, 0).at(a);
        o.rot[TitheBinderRig.BODY][1] += K.of(0, 0).k(9, 35).k(12, 35).k(14, -20).k(HOOK_WRAP, 0).at(a);
        o.rot[TitheBinderRig.BODY][0] += K.of(0, 0).k(HOOK_WRAP, 0).k(HOOK_WRAP + 4, 20).k(HOOK_PULL_END, 20)
            .k(HOOK_DOCK, 0).at(a);
        o.off[TitheBinderRig.HOVER][1] += K.of(0, 0).k(HOOK_WRAP, 0).k(HOOK_WRAP + 4, -2).k(HOOK_PULL_END, -2)
            .k(HOOK_DOCK, 0).at(a);
        if (a < HOOK_RELEASE || a >= HOOK_DOCK)
            return;
        boolean docked = o.slotOn[1];
        double angle = docked ? o.slotAngle[1] : st.ringYaw();
        if (a == HOOK_RELEASE)
            TitheBinderRig.slotWorld(st, angle, 0.0, hookFrom);
        // weapon 1 is the hook; a volley that has it in the air keeps it
        if (o.flyState[1] != TitheBinderRig.FLY_NONE)
            return;
        double[] pos = hookPos, dir = va;
        int state;
        double spin = 0.0, sag;
        if (a < HOOK_WRAP) {                                   // straight and fast, spinning, point-first at the end
            double u = (a - HOOK_RELEASE + 1) / (double) (HOOK_WRAP - HOOK_RELEASE);
            for (int i = 0; i < 3; i++) {
                pos[i] = hookFrom[i] + (hookTarget[i] - hookFrom[i]) * u;
                dir[i] = hookTarget[i] - hookFrom[i];
            }
            spin = a < HOOK_WRAP - 2 ? HOOK_SPIN * (a - HOOK_RELEASE + 1) : 0.0;
            state = TitheBinderRig.FLY_FLYING;
            sag = 1.0;
        } else if (a <= HOOK_PULL_END) {                       // wrapped round the target, dragged with it: taut
            for (int i = 0; i < 3; i++) {
                pos[i] = hookTarget[i];
                dir[i] = hookTarget[i] - st.hub[i];
            }
            state = TitheBinderRig.FLY_EMBED;
            sag = 0.0;
            if (a == HOOK_PULL_END)
                System.arraycopy(pos, 0, reelFrom, 0, 3);
            int age = a - HOOK_WRAP;
            if (age < HOOK_FLASH_TICKS && o.impacts < o.impactAge.length) {
                System.arraycopy(pos, 0, o.impactPos[o.impacts], 0, 3);
                o.impactAge[o.impacts] = age;
                o.impacts++;
            }
        } else {                                               // reeled home on a low arc, slow start, fast arrival
            double u = (a - HOOK_PULL_END) / (double) (HOOK_DOCK - HOOK_PULL_END);
            double e = u * u;
            double[] dock = vb;
            TitheBinderRig.slotWorld(st, angle, 0.0, dock);
            for (int i = 0; i < 3; i++) {
                pos[i] = reelFrom[i] + (dock[i] - reelFrom[i]) * e;
                dir[i] = reelFrom[i] - dock[i];
            }
            pos[1] += 6.0 * Math.sin(Math.PI * u);
            spin = 360.0 * e;
            state = TitheBinderRig.FLY_REEL;
            sag = 3.0;
        }
        TitheBinderRig.frameAlong(dir, ma);
        TitheBinderRig.qFromMat(ma, qa);
        TitheBinderRig.qFromAxis(1, 0, 0, spin, qb);
        TitheBinderRig.qMul(qa, qb, qc);
        TitheBinderRig.qToMat(qc, ma);
        o.slotOn[1] = false;
        o.weaponHidden[1] = false;
        o.flyState[1] = state;
        System.arraycopy(pos, 0, o.flyPos[1], 0, 3);
        TitheBinderRig.euler(ma, o.flyRot[1]);
        o.flyScale[1] = HOOK_SCALE;
        o.flyTrailN[1] = 0;
        // its chain: slot 1's docked ribbon (the first one the step wrote) becomes the thrown chain
        double[] hub = vc, ctrl = vd;
        TitheBinderRig.hubToward(st, pos, hub);
        TitheBinderRig.sagCtrl(hub, pos, sag, ctrl);
        if (docked && o.ribbons > 0) {
            System.arraycopy(hub, 0, o.ribbonHub[0], 0, 3);
            System.arraycopy(ctrl, 0, o.ribbonCtrl[0], 0, 3);
            System.arraycopy(pos, 0, o.ribbonEnd[0], 0, 3);
            o.ribbonWidth[0] = HOOK_WIDTH;
        } else if (o.ribbons < o.ribbonWidth.length) {
            o.ribbon(hub, ctrl, pos, HOOK_WIDTH);
        }
    }

    // ------------------------------------------------------------------ the frame

    /**
     * The pose for this frame: the last two stepped poses lerped by {@code partial} (0..1) into one reused pose
     * (angles the short way round; a weapon or ribbon that was not there a tick ago is taken as it is now).
     */
    public Pose blend(float partial) {
        double u = Math.max(0.0, Math.min(1.0, partial));
        Pose a = prev, b = cur, o = out;
        for (int i = 0; i < TitheBinderRig.BONES.length; i++)
            for (int c = 0; c < 3; c++) {
                o.rot[i][c] = lerp(a.rot[i][c], b.rot[i][c], u);
                o.off[i][c] = lerp(a.off[i][c], b.off[i][c], u);
            }
        for (int k = 0; k <= TitheBinderRig.MAX_WEAPONS; k++) {
            boolean both = a.slotOn[k] && b.slotOn[k];
            o.slotOn[k] = b.slotOn[k];
            o.slotAngle[k] = both ? lerpAngle(a.slotAngle[k], b.slotAngle[k], u) : b.slotAngle[k];
            o.slotRadial[k] = both ? lerp(a.slotRadial[k], b.slotRadial[k], u) : b.slotRadial[k];
            o.slotY[k] = both ? lerp(a.slotY[k], b.slotY[k], u) : b.slotY[k];
            o.slotPitch[k] = both ? lerp(a.slotPitch[k], b.slotPitch[k], u) : b.slotPitch[k];
            o.slotRoll[k] = both ? lerp(a.slotRoll[k], b.slotRoll[k], u) : b.slotRoll[k];
            o.slotScale[k] = both ? lerp(a.slotScale[k], b.slotScale[k], u) : b.slotScale[k];
            o.weaponHidden[k] = b.weaponHidden[k];
            boolean fly = a.flyState[k] != TitheBinderRig.FLY_NONE && b.flyState[k] != TitheBinderRig.FLY_NONE;
            o.flyState[k] = b.flyState[k];
            for (int c = 0; c < 3; c++) {
                o.flyPos[k][c] = fly ? lerp(a.flyPos[k][c], b.flyPos[k][c], u) : b.flyPos[k][c];
                o.flyRot[k][c] = fly ? lerpAngle(a.flyRot[k][c], b.flyRot[k][c], u) : b.flyRot[k][c];
            }
            o.flyScale[k] = fly ? lerp(a.flyScale[k], b.flyScale[k], u) : b.flyScale[k];
            int tn = b.flyTrailN[k];
            o.flyTrailN[k] = tn;
            for (int j = 0; j < tn; j++)
                System.arraycopy(b.flyTrail[k][j], 0, o.flyTrail[k][j], 0, 3);
        }
        o.impacts = b.impacts;
        for (int i = 0; i < b.impacts; i++) {
            System.arraycopy(b.impactPos[i], 0, o.impactPos[i], 0, 3);
            o.impactAge[i] = b.impactAge[i];
        }
        o.lanternCoreHidden = b.lanternCoreHidden;
        o.alpha = lerp(a.alpha, b.alpha, u);
        o.lantern = lerp(a.lantern, b.lantern, u);
        boolean rib = a.ribbons == b.ribbons;
        o.ribbons = b.ribbons;
        for (int i = 0; i < b.ribbons; i++) {
            for (int c = 0; c < 3; c++) {
                o.ribbonHub[i][c] = rib ? lerp(a.ribbonHub[i][c], b.ribbonHub[i][c], u) : b.ribbonHub[i][c];
                o.ribbonCtrl[i][c] = rib ? lerp(a.ribbonCtrl[i][c], b.ribbonCtrl[i][c], u) : b.ribbonCtrl[i][c];
                o.ribbonEnd[i][c] = rib ? lerp(a.ribbonEnd[i][c], b.ribbonEnd[i][c], u) : b.ribbonEnd[i][c];
            }
            o.ribbonWidth[i] = b.ribbonWidth[i];
        }
        o.halo = b.halo;
        o.haloDip = lerp(a.haloDip, b.haloDip, u);
        o.lashOn = b.lashOn;
        o.orbOn = b.orbOn;
        o.flashOn = b.flashOn;
        o.forgeOn = b.forgeOn;
        lerp3(a.lashOn, a.lashA, b.lashA, u, o.lashA);
        lerp3(a.lashOn, a.lashCtrl, b.lashCtrl, u, o.lashCtrl);
        lerp3(a.lashOn, a.lashB, b.lashB, u, o.lashB);
        lerp3(a.orbOn, a.orb, b.orb, u, o.orb);
        lerp3(a.flashOn, a.flash, b.flash, u, o.flash);
        lerp3(a.forgeOn, a.forgeA, b.forgeA, u, o.forgeA);
        lerp3(a.forgeOn, a.forgeB, b.forgeB, u, o.forgeB);
        return o;
    }

    private static double lerp(double a, double b, double u) {
        return a + (b - a) * u;
    }

    /** Degrees, the short way round. */
    static double lerpAngle(double a, double b, double u) {
        double d = b - a;
        d -= 360.0 * Math.floor((d + 180.0) / 360.0);
        return a + d * u;
    }

    private static void lerp3(boolean fromOn, double[] a, double[] b, double u, double[] out) {
        for (int c = 0; c < 3; c++)
            out[c] = fromOn ? lerp(a[c], b[c], u) : b[c];
    }
}
