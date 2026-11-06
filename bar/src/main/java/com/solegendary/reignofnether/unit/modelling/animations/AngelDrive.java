package com.solegendary.reignofnether.unit.modelling.animations;

/**
 * An angel's rig in play: one per unit on the client (AngelUnit.clientDrive, made on the first draw, gone with the
 * entity). The renderer fills {@link #signals} from the unit and calls {@link #catchUp} with the entity's tickCount;
 * this steps {@link AngelRig} once per game tick (never per frame), keeps the last two poses, and {@link #blend} lerps
 * them by partialTick. Pure maths, like the rig: no Minecraft class here, so AngelDriveTest drives it without a game.
 *
 * <p>The contact on the server's clock (tools/angel_anim.py's Inputs): the rig starts its attack clip when the next
 * contact is due within the clip's own lead (EVENT ticks), so the wind-up plays BEFORE the contact, and the contact
 * cue retimes it onto the server's tick. Where the client learns the next contact from ({@link Timing}):
 * <ul>
 *   <li>Seraph Archer: the server's wind-up cue (the release follows {@code windupLead} ticks later) and, after a
 *       release while it still has a target, the shot cooldown plus the wind-up ({@code repeat});</li>
 *   <li>Mender Angel: after a heal while its patient is still hurt, the heal interval ({@code repeat});</li>
 *   <li>Sword Seraph: the swing (vanilla's animate packet) - its blow lands {@code swingLead} ticks later
 *       (BlowTiming), and the drive makes the contact cue itself on that tick.</li>
 * </ul>
 * The arrival plays when the drive starts on a young entity (tickCount under the arrival's length), caught up to the
 * entity's age; death holds the rig's death clip.
 */
public final class AngelDrive {

    /** More ticks behind than this (off screen, a lag spike) and the drive starts over instead of catching up. */
    public static final int MAX_CATCH_UP = 40;
    private static final long NONE = Long.MIN_VALUE;

    /** When a kind's contact comes, in ticks (the units' own numbers, handed in by the renderer). */
    public static final class Timing {
        /** From the wind-up cue to the contact (0: no wind-up cue). */
        public final int windupLead;
        /** From the swing's first tick to the blow (0: no swing signal). */
        public final int swingLead;
        /** From one contact to the next while still engaged (0: not predicted). */
        public final int repeat;

        public Timing(int windupLead, int swingLead, int repeat) {
            this.windupLead = windupLead;
            this.swingLead = swingLead;
            this.repeat = repeat;
        }
    }

    /** What the unit shows this tick, in plain numbers (the renderer fills it from the entity). */
    public static final class Signals {
        /** Horizontal speed along the body's facing, blocks per tick (backwards counts as 0). */
        public double speed;
        /** Degrees the body turned this tick (+ = to its right). */
        public double yawRate;
        /** The server's wind-up cue counter (archer) and contact cue counter (archer release, mender heal). */
        public int windupSeq, contactSeq;
        /** Mid-swing (LivingEntity.swinging: the sword's blow follows the swing's first tick). */
        public boolean swinging;
        /** Still on its target (the archer's target alive, the mender's patient still hurt): the next contact comes. */
        public boolean engaged;
        /** LivingEntity.hurtTime. */
        public int hurtTime;
        /** Dying (deathTime > 0). */
        public boolean dying;
    }

    public final Signals signals = new Signals();
    private final AngelRig.Kind kind;
    private final long seed;
    private final Timing timing;
    private AngelRig.State st;
    private final AngelRig.Inputs in = new AngelRig.Inputs();
    private AngelRig.Pose prev = new AngelRig.Pose(), cur = new AngelRig.Pose();
    private final AngelRig.Pose out = new AngelRig.Pose();
    private boolean ready, swingingBefore;
    private int lastTick, seenWindup, seenContact;
    private long clock, contactAt = NONE, selfCueAt = NONE;

    public AngelDrive(AngelRig.Kind kind, long seed, Timing timing) {
        this.kind = kind;
        this.seed = seed;
        this.timing = timing;
    }

    /** Stepped at least once: {@link #blend} has two poses to lerp. */
    public boolean ready() {
        return ready;
    }

    /** The rig's state (null before the first step). */
    public AngelRig.State state() {
        return st;
    }

    /** The newest stepped pose (tick time). */
    public AngelRig.Pose current() {
        return cur;
    }

    /** The pose {@link #blend} wrote last (what is being drawn). */
    public AngelRig.Pose blended() {
        return out;
    }

    /** The ticks until the predicted contact the last step fed the rig (-1 none due). */
    public int attackIn() {
        return in.attackIn;
    }

    /** True when {@code tickCount} is a tick not stepped yet: the caller fills {@link #signals}, then catches up. */
    public boolean behind(int tickCount) {
        return !ready || tickCount != lastTick;
    }

    /**
     * Steps once per game tick up to {@code tickCount} (the entity's), reading {@link #signals}. The first call, a jump
     * back or more than {@link #MAX_CATCH_UP} ticks behind starts over: a young entity (under the arrival's length)
     * plays its arrival from its first tick.
     */
    public void catchUp(int tickCount) {
        if (ready) {
            int behind = tickCount - lastTick;
            if (behind == 0)
                return;
            if (behind > 0 && behind <= MAX_CATCH_UP) {
                for (int i = 0; i < behind; i++)
                    tick();
                lastTick = tickCount;
                return;
            }
        }
        boolean arrive = tickCount >= 0 && tickCount < AngelRig.ARRIVE_LEN;
        st = new AngelRig.State(kind, seed, arrive);
        seenWindup = signals.windupSeq;
        seenContact = signals.contactSeq;
        swingingBefore = signals.swinging;
        contactAt = selfCueAt = NONE;
        in.attackSeq = 0;
        tick();
        prev.set(cur);
        for (int i = 0; arrive && i < tickCount; i++)
            tick();
        ready = true;
        lastTick = tickCount;
    }

    private void tick() {
        AngelRig.Pose p = prev;
        prev = cur;
        cur = p;
        clock++;
        Signals s = signals;
        if (s.contactSeq != seenContact) {
            seenContact = s.contactSeq;
            in.attackSeq++;
            contactAt = s.engaged && timing.repeat > 0 ? clock + timing.repeat : NONE;
        }
        if (s.windupSeq != seenWindup) {
            seenWindup = s.windupSeq;
            if (timing.windupLead > 0)
                contactAt = clock + timing.windupLead;
        }
        if (timing.swingLead > 0) {
            if (s.swinging && !swingingBefore)
                contactAt = selfCueAt = clock + timing.swingLead;
            swingingBefore = s.swinging;
            if (selfCueAt == clock) {                // the held blow lands: the contact cue, made here
                in.attackSeq++;
                selfCueAt = NONE;
                contactAt = s.engaged && timing.repeat > 0 ? clock + timing.repeat : NONE;
            }
        }
        if (contactAt != NONE && contactAt < clock)
            contactAt = NONE;
        in.attackIn = contactAt == NONE ? -1 : (int) Math.min(Integer.MAX_VALUE, contactAt - clock);
        in.speed = s.speed;
        in.yawRate = s.yawRate;
        in.hurtTime = s.hurtTime;
        in.dying = s.dying;
        AngelRig.step(st, in, cur);
    }

    /** The pose for a frame {@code partial} of a tick after the newest step: the last two steps lerped (short way). */
    public AngelRig.Pose blend(float partial) {
        double u = Math.max(0.0, Math.min(1.0, partial));
        for (int b = 0; b < AngelRig.N; b++) {
            for (int i = 0; i < 3; i++) {
                out.rot[b][i] = lerpAngle(prev.rot[b][i], cur.rot[b][i], u);
                out.move[b][i] = prev.move[b][i] + (cur.move[b][i] - prev.move[b][i]) * u;
                out.scale[b][i] = prev.scale[b][i] + (cur.scale[b][i] - prev.scale[b][i]) * u;
            }
            out.hidden[b] = cur.hidden[b];
        }
        out.white = prev.white + (cur.white - prev.white) * u;
        out.alpha = prev.alpha + (cur.alpha - prev.alpha) * u;
        out.haloAlpha = prev.haloAlpha + (cur.haloAlpha - prev.haloAlpha) * u;
        out.glow = prev.glow + (cur.glow - prev.glow) * u;
        return out;
    }

    static double lerpAngle(double a, double b, double u) {
        double d = (b - a) % 360.0;
        if (d > 180.0)
            d -= 360.0;
        else if (d < -180.0)
            d += 360.0;
        return a + d * u;
    }
}
