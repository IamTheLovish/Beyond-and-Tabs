package com.solegendary.reignofnether.hero.ai;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * What the hero AI decided and why (lovish's hero AI requirement 8: "Debug overlay / log line: what it decided and
 * why"), as a ring of the last {@link #SIZE} decisions of one hero.
 * <p>
 * Cheap by construction. A decision is five primitives written into arrays: no object, no string, no lookup. The
 * brain records at most one decision per scan (once a second per hero) plus the odd combo follow-up, never per tick.
 * The text is built only when somebody reads it ({@link #line}) or when the logger {@code reignofnether/HeroAI} is at
 * DEBUG: then each decision is also written as one {@code [HeroAI]} line. With DEBUG off (the default) the only extra
 * work is that one level check per decision.
 */
public final class DecisionLog {

    public static final int SIZE = 16;
    public static final Logger LOG = LogManager.getLogger("reignofnether/HeroAI");

    /** Why the brain did what it did; one per decision. */
    public enum Why {
        IDLE,               // nothing to fight, no order
        OBEY,               // carrying out the player's order; casts only where the order allows
        FIGHT,              // no order, enemies in view: the brain picks the fight
        CAST,               // an ability cast (slot and target recorded)
        GAP_CLOSER,         // a gap closer cast, its follow-up armed
        NO_FOLLOW_UP,       // a gap closer held back: no follow-up ready (combo rule)
        COMBO,              // the follow-up after a gap closer
        FLEE,               // near death and threatened: running (D030: may break the player's order at first)
        REST,               // out of reach while still near death: waiting for a heal or the player's word
        RESUME,             // healed (or selected): back to the order the flight broke off
        COMMAND,            // a fresh player command: it wins, the danger so far is acknowledged
        SELECTED,           // the player selected the hero (D030)
        TOWER               // a gap closer held back: it would land under an enemy tower
    }

    private final long[] tick = new long[SIZE];
    private final byte[] state = new byte[SIZE];
    private final byte[] why = new byte[SIZE];
    private final byte[] slot = new byte[SIZE];
    private final int[] target = new int[SIZE];
    private final float[] ratio = new float[SIZE];
    private int next = 0, count = 0;
    private final int heroId;

    public DecisionLog(int heroId) {
        this.heroId = heroId;
    }

    /**
     * One decision. {@code abilitySlot} is -1 when no ability was cast, {@code targetId} -1 when none; {@code ratio}
     * is FightEval's power ratio at the time (ours over theirs).
     */
    public void record(long gameTick, HeroBrain.State s, Why w, int abilitySlot, int targetId, double ratio) {
        int i = next;
        tick[i] = gameTick;
        state[i] = (byte) s.ordinal();
        why[i] = (byte) w.ordinal();
        slot[i] = (byte) abilitySlot;
        target[i] = targetId;
        this.ratio[i] = (float) ratio;
        next = (next + 1) % SIZE;
        if (count < SIZE)
            count++;
        if (LOG.isDebugEnabled())
            LOG.debug("[HeroAI] {}", line(0));
    }

    public int size() {
        return count;
    }

    private int index(int age) {
        if (age < 0 || age >= count)
            throw new IndexOutOfBoundsException(age + " of " + count);
        return ((next - 1 - age) % SIZE + SIZE) % SIZE;
    }

    /** The decision {@code age} back (0 = the newest). */
    public Why why(int age) { return Why.values()[why[index(age)]]; }
    public HeroBrain.State state(int age) { return HeroBrain.State.values()[state[index(age)]]; }
    public long tick(int age) { return tick[index(age)]; }
    public int slot(int age) { return slot[index(age)]; }
    public int target(int age) { return target[index(age)]; }

    /** True when one of the last {@code within} decisions was {@code w}. */
    public boolean saw(Why w, int within) {
        for (int age = 0; age < Math.min(within, count); age++)
            if (why(age) == w)
                return true;
        return false;
    }

    /** The decision {@code age} back as one line of text (built only here). */
    public String line(int age) {
        int i = index(age);
        return String.format("hero %d t%d %s %s%s%s ratio %.2f", heroId, tick[i], HeroBrain.State.values()[state[i]],
            Why.values()[why[i]], slot[i] >= 0 ? " slot " + (slot[i] + 1) : "", target[i] >= 0 ? " -> " + target[i] : "",
            ratio[i]);
    }
}
