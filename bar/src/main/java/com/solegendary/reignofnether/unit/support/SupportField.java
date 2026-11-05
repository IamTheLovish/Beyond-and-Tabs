package com.solegendary.reignofnether.unit.support;

/**
 * What a support unit sees around it at one look (SupportBrain.scan): the nearby units flattened into parallel arrays
 * - where each stands now and about one second ahead, how hurt it is, what it is worth and what kind of unit it is
 * (the {@code F_*} flags). Every decision of the healer AI (SupportMath) reads this one snapshot, so one UnitGrid query
 * per look feeds the danger check, the standoff, and all four potion choices.
 * <p>
 * Plain Java on purpose - no Minecraft types - so the scoring maths is unit-tested without a game
 * (bar/src/test/.../unit/support/SupportMathTest). {@link #ref} carries the entity for the glue code that acts on a
 * choice. One instance is reused for every look (server thread only); {@link #add} drops entries past {@link #cap}.
 */
public final class SupportField {

    /** On the looker's side (its own units and its allies'), the looker itself included. */
    public static final int F_FRIEND = 1;
    /** Hostile to the looker. */
    public static final int F_ENEMY = 1 << 1;
    /** Healing hurts it and harming heals it (vanilla's inverted heal and harm). */
    public static final int F_UNDEAD = 1 << 2;
    /** A unit that fights in melee: the danger a healer keeps away from. */
    public static final int F_MELEE = 1 << 3;
    /** A support unit (healer): never counted as the front line. */
    public static final int F_SUPPORT = 1 << 4;
    /** The looker. */
    public static final int F_SELF = 1 << 5;
    /** Its current target is the looker. */
    public static final int F_TARGETS_ME = 1 << 6;
    /** On fire (the water potion's job). */
    public static final int F_BURNING = 1 << 7;
    /** A fighting unit (AttackerUnit). */
    public static final int F_ATTACKER = 1 << 8;
    /** Under a harmful effect (poison, slow, wither, a root...) or burning: what a cleanse can lift. */
    public static final int F_AFFLICTED = 1 << 9;

    /** How far ahead {@link #px}/{@link #pz} look, in ticks: about a thrown potion's flight. */
    public static final int LOOKAHEAD_TICKS = 20;
    /** A walking unit's predicted step is capped at this (blocks): a knockback spike is not a walk. */
    public static final double MAX_LOOKAHEAD = 4.0;

    public final int cap;
    public int n;
    public final Object[] ref;
    public final double[] x, z, px, pz;
    public final float[] health, maxHealth, value;
    public final int[] flags;

    // candidate scratch for SupportMath.nearest (one pick at a time; the maths never nests two picks)
    final int[] cand = new int[SupportMath.MAX_CANDIDATES];
    final double[] candD = new double[SupportMath.MAX_CANDIDATES];

    public SupportField(int cap) {
        this.cap = cap;
        ref = new Object[cap];
        x = new double[cap];
        z = new double[cap];
        px = new double[cap];
        pz = new double[cap];
        health = new float[cap];
        maxHealth = new float[cap];
        value = new float[cap];
        flags = new int[cap];
    }

    public void clear() {
        for (int i = 0; i < n; i++)
            ref[i] = null;
        n = 0;
    }

    /**
     * Adds one unit; {@code vx}/{@code vz} are its velocity in blocks per tick. Returns its index, or -1 when the field
     * is full (the scan then simply sees no more units - the nearest come first from the grid only roughly, so a full
     * field is a crowd and the cap is a cost bound, not a choice).
     */
    public int add(Object entity, double x, double z, double vx, double vz, float health, float maxHealth, float value, int flags) {
        if (n >= cap)
            return -1;
        int i = n++;
        ref[i] = entity;
        this.x[i] = x;
        this.z[i] = z;
        double sx = vx * LOOKAHEAD_TICKS, sz = vz * LOOKAHEAD_TICKS;
        double len = Math.sqrt(sx * sx + sz * sz);
        if (len > MAX_LOOKAHEAD) {
            sx *= MAX_LOOKAHEAD / len;
            sz *= MAX_LOOKAHEAD / len;
        }
        px[i] = x + sx;
        pz[i] = z + sz;
        this.health[i] = health;
        this.maxHealth[i] = maxHealth;
        this.value[i] = value;
        this.flags[i] = flags;
        return i;
    }

    public boolean is(int i, int flag) {
        return (flags[i] & flag) == flag;
    }

    public float missing(int i) {
        return Math.max(0f, maxHealth[i] - health[i]);
    }

    /** The looker's index, or -1 if it is not in the field. */
    public int self() {
        for (int i = 0; i < n; i++)
            if ((flags[i] & F_SELF) != 0)
                return i;
        return -1;
    }
}
