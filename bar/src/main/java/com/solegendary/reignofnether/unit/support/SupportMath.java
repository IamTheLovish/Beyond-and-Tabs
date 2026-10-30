package com.solegendary.reignofnether.unit.support;

import static com.solegendary.reignofnether.unit.support.SupportField.*;

/**
 * The healer AI's decisions as plain maths over one {@link SupportField} (claude/healer-ai-oct21.md, sections B and C),
 * for any delivery (SupportKind): where an area harm may land without touching a friend, which friend a single heal
 * should go to, where an area heal covers the most hurt friends, which friend needs a cleanse, which friend is the
 * front line, which way to step out of danger and where a healer stands behind a fight. Each cast brings its own
 * radius and its {@link Undead} rule (vanilla's potions work backwards on the undead; a plain heal does not). No
 * Minecraft types, no allocation: SupportBrain feeds it and acts on the answer, and SupportMathTest checks every rule
 * without a game.
 * <p>
 * Candidates are capped at {@link #MAX_CANDIDATES} (the nearest), so one look costs at most 12 x the field's size
 * distance tests per rule, whatever the crowd.
 */
public final class SupportMath {
    private SupportMath() { }

    /** At most this many candidate targets per rule (the nearest ones). */
    public static final int MAX_CANDIDATES = 12;

    /** An area harm is cast only when no friend it would hurt stands (or will stand in ~1 s) within its radius plus this. */
    public static final double HARM_SAFETY = 1.5;
    /** An area harm is aimed this far beyond the enemy cluster's centre, away from our side. */
    public static final double AIM_OFFSET = 1.0;
    /** A heal target with an enemy this close would heal the enemy too (a splash, a circle): its score is halved. */
    public static final double HEAL_ENEMY_NEAR = 2.0;
    /** The healer heals itself only below this fraction of its health, and then counts it this much more. */
    public static final float SELF_HEAL_FRACTION = 0.5f, SELF_HEAL_WEIGHT = 1.5f;
    /**
     * lovish, Oct 30 ("heal low hp units not full hp units"): a friend above this fraction of its health is not hurt
     * enough for a heal, however much health that is (3 of a golem's 120 is a scratch).
     */
    public static final float HEAL_MAX_FRACTION = 0.9f;
    /**
     * A heal goes to the friend lowest on health (as a fraction); what the friend is worth only breaks a near-tie: the
     * most valuable unit (value 5) scores this much more, the cheapest nothing.
     */
    public static final double VALUE_TIEBREAK = 0.05;
    /** An area heal needs at least this many hurt friends under it (one is the single heal's job). */
    public static final int AREA_HEAL_MIN_HURT = 2;
    /** What an area heal is reckoned to give each friend under it at most (the score's cap). */
    public static final float AREA_HEAL_PER_FRIEND = 8f;
    /** The front line: our unit with the most enemies within this many blocks. */
    public static final double ENGAGE_RADIUS = 6.0;
    /** A threat that only aims at the healer from afar (an archer) weighs this much of a melee threat next to it. */
    public static final double AIMED_WEIGHT = 0.5;

    /** How a cast treats the undead. */
    public enum Undead {
        /** Like anyone else. */
        NORMAL,
        /** Not at all: a heal does not mend them, a harm does not hurt them (vanilla regeneration). */
        IMMUNE,
        /** Backwards (vanilla instant health and harming): a heal hurts them, a harm mends them. */
        INVERTED
    }

    /** The flags to leave out of a cast's beneficiaries / victims under {@code rule}. */
    static int skipUndead(Undead rule) {
        return rule == Undead.NORMAL ? 0 : F_UNDEAD;
    }

    /** A chosen point (and, when it is a unit, its index in the field). */
    public static final class Aim {
        public int index = -1;
        public double x, z, score;

        public void reset() {
            index = -1;
            x = z = score = 0;
        }
    }

    static double d2(double ax, double az, double bx, double bz) {
        double dx = ax - bx, dz = az - bz;
        return dx * dx + dz * dz;
    }

    static boolean has(int flags, int all, int none) {
        return (flags & all) == all && (flags & none) == 0;
    }

    /**
     * How many entries with every bit of {@code all} and none of {@code none} stand within {@code r} of (cx, cz) - or,
     * with {@code predicted}, will stand there about a second from now (SupportField.px/pz).
     */
    public static int countWithin(SupportField f, int all, int none, double cx, double cz, double r, boolean predicted) {
        double r2 = r * r;
        int c = 0;
        for (int i = 0; i < f.n; i++) {
            if (!has(f.flags[i], all, none))
                continue;
            if (d2(f.x[i], f.z[i], cx, cz) <= r2 || (predicted && d2(f.px[i], f.pz[i], cx, cz) <= r2))
                c++;
        }
        return c;
    }

    /**
     * Up to {@link #MAX_CANDIDATES} entries matching the flags within {@code r} of (cx, cz), nearest first, into
     * {@code f.cand}; returns how many. A {@code minMissing} above 0 keeps only the {@link #hurt} ones.
     */
    static int nearest(SupportField f, int all, int none, double cx, double cz, double r, float minMissing) {
        double r2 = r * r;
        int k = 0;
        for (int i = 0; i < f.n; i++) {
            if (!has(f.flags[i], all, none) || (minMissing > 0 && !hurt(f, i, minMissing)))
                continue;
            double d = d2(f.x[i], f.z[i], cx, cz);
            if (d > r2)
                continue;
            int at = k < MAX_CANDIDATES ? k++ : MAX_CANDIDATES;
            while (at > 0 && f.candD[at - 1] > d) {
                if (at < MAX_CANDIDATES) {
                    f.cand[at] = f.cand[at - 1];
                    f.candD[at] = f.candD[at - 1];
                }
                at--;
            }
            if (at < MAX_CANDIDATES) {
                f.cand[at] = i;
                f.candD[at] = d;
            }
        }
        return k;
    }

    /**
     * Where to aim an area harm of {@code radius} (autocast), or false for "nowhere safe". Candidates are the nearest
     * enemies it hurts within {@code reach} of the caster at (sx, sz). For each, the area goes on the centre of the
     * enemies round it - where they will be in about a second - pushed {@link #AIM_OFFSET} further from our side at
     * (ourX, ourZ); it is dropped if any friend it would hurt stands, or will stand in about a second, within
     * {@code radius} + {@link #HARM_SAFETY}. Score: enemies under it + the candidate's value - undead enemies it would
     * mend (INVERTED). The best positive score wins.
     */
    public static boolean harmAim(SupportField f, double sx, double sz, double reach, double radius, double ourX, double ourZ,
                                  Undead rule, Aim out) {
        out.reset();
        int skip = skipUndead(rule);
        double reach2 = reach * reach, r2 = radius * radius;
        int k = nearest(f, F_ENEMY, skip, sx, sz, reach, 0f);
        for (int c = 0; c < k; c++) {
            int i = f.cand[c];
            double cx = 0, cz = 0;
            int m = 0;
            for (int j = 0; j < f.n; j++)
                if (has(f.flags[j], F_ENEMY, skip) && d2(f.x[j], f.z[j], f.x[i], f.z[i]) <= r2) {
                    cx += f.px[j];
                    cz += f.pz[j];
                    m++;
                }
            cx /= m;   // m >= 1: the candidate itself
            cz /= m;
            double ax = cx, az = cz;
            double dx = cx - ourX, dz = cz - ourZ, len = Math.sqrt(dx * dx + dz * dz);
            if (len > 0.01) {
                ax += dx / len * AIM_OFFSET;
                az += dz / len * AIM_OFFSET;
            }
            if (d2(ax, az, sx, sz) > reach2) {
                ax = f.px[i];
                az = f.pz[i];
                if (d2(ax, az, sx, sz) > reach2) {
                    ax = f.x[i];
                    az = f.z[i];
                }
            }
            if (!harmSafeAt(f, ax, az, radius, rule))
                continue;
            int foes = countWithin(f, F_ENEMY, skip, ax, az, radius, false);
            if (foes == 0)
                continue;
            int mended = rule == Undead.INVERTED ? countWithin(f, F_ENEMY | F_UNDEAD, 0, ax, az, radius, false) : 0;
            double score = foes + f.value[i] - mended;
            if (score > out.score) {
                out.index = i;
                out.x = ax;
                out.z = az;
                out.score = score;
            }
        }
        return out.index >= 0;
    }

    /** Whether an area harm of {@code radius} at (x, z) would touch no friend it hurts, now or in about a second. */
    public static boolean harmSafeAt(SupportField f, double x, double z, double radius, Undead rule) {
        return countWithin(f, F_FRIEND, skipUndead(rule), x, z, radius + HARM_SAFETY, true) == 0;
    }

    /**
     * Hurt enough for a heal: missing at least {@code minMissing} health and at most {@link #HEAL_MAX_FRACTION} of its
     * health left.
     */
    static boolean hurt(SupportField f, int i, float minMissing) {
        return f.missing(i) >= minMissing && f.health[i] <= f.maxHealth[i] * HEAL_MAX_FRACTION;
    }

    /** What a friend's value adds to a heal's score: 0 for the cheapest (value 1) up to {@link #VALUE_TIEBREAK}. */
    static double valueTiebreak(float value) {
        return VALUE_TIEBREAK * Math.min(1.0, Math.max(0.0, (value - 1.0) / 4.0));
    }

    /**
     * The friend a single heal should go to, or -1: a {@link #hurt} friend the heal mends within {@code reach}, the
     * lowest on health as a fraction of its own (lovish, Oct 30: "heal low hp units"; the old score, missing health x
     * value, sent the heal to a costly unit at 80% over a cheap one at 60%) - its value only breaks a near-tie
     * ({@link #valueTiebreak}). The healer itself only below {@link #SELF_HEAL_FRACTION} of its health, then x
     * {@link #SELF_HEAL_WEIGHT}; halved with an enemy within {@link #HEAL_ENEMY_NEAR}. A heal that hurts the undead
     * (INVERTED) never goes where an undead friend stands within its {@code radius}, now or in a second.
     */
    public static int healTarget(SupportField f, double sx, double sz, double reach, double radius, float minMissing, Undead rule) {
        int skip = skipUndead(rule);
        double reach2 = reach * reach;
        double undead2 = (radius + 0.5) * (radius + 0.5);
        double near2 = HEAL_ENEMY_NEAR * HEAL_ENEMY_NEAR;
        boolean guardUndead = rule == Undead.INVERTED && anyUndeadFriend(f);
        int best = -1;
        double bestScore = 0;
        for (int i = 0; i < f.n; i++) {
            int fl = f.flags[i];
            if (!has(fl, F_FRIEND, skip))
                continue;
            if (!hurt(f, i, minMissing))
                continue;
            boolean self = (fl & F_SELF) != 0;
            if (self && f.health[i] >= f.maxHealth[i] * SELF_HEAL_FRACTION)
                continue;
            if (d2(f.x[i], f.z[i], sx, sz) > reach2)
                continue;
            if (guardUndead && undeadFriendNear(f, f.x[i], f.z[i], undead2))
                continue;
            double score = 1.0 - f.health[i] / Math.max(1f, f.maxHealth[i]);
            if (self)
                score *= SELF_HEAL_WEIGHT;
            for (int j = 0; j < f.n; j++)
                if ((f.flags[j] & F_ENEMY) != 0 && d2(f.x[j], f.z[j], f.x[i], f.z[i]) <= near2) {
                    score *= 0.5;
                    break;
                }
            score += valueTiebreak(f.value[i]);
            if (score > bestScore) {
                bestScore = score;
                best = i;
            }
        }
        return best;
    }

    static boolean anyUndeadFriend(SupportField f) {
        for (int j = 0; j < f.n; j++)
            if ((f.flags[j] & (F_FRIEND | F_UNDEAD)) == (F_FRIEND | F_UNDEAD))
                return true;
        return false;
    }

    static boolean undeadFriendNear(SupportField f, double x, double z, double r2) {
        for (int j = 0; j < f.n; j++)
            if ((f.flags[j] & (F_FRIEND | F_UNDEAD)) == (F_FRIEND | F_UNDEAD)
                    && (d2(f.x[j], f.z[j], x, z) <= r2 || d2(f.px[j], f.pz[j], x, z) <= r2))
                return true;
        return false;
    }

    /**
     * Where an area heal of {@code radius} covers the most hurt friends, or false: on the centre of at least
     * {@link #AREA_HEAL_MIN_HURT} {@link #hurt} friends it mends - where they will be in a
     * second, since a walking group walks on - within {@code reach} of the caster; never where the enemies under it
     * outnumber the friends (it would heal both sides of a melee), nor, for one that hurts the undead, over an undead
     * friend. Score: the share of each friend's health it would give back (its health given, capped at
     * {@link #AREA_HEAL_PER_FRIEND}, over its maximum), so the lowest friends count most, plus each one's
     * {@link #valueTiebreak}.
     */
    public static boolean areaHealAim(SupportField f, double sx, double sz, double reach, double radius, float minMissing,
                                      Undead rule, Aim out) {
        out.reset();
        int skip = skipUndead(rule);
        double reach2 = reach * reach, r2 = radius * radius;
        double undead2 = (radius + 0.5) * (radius + 0.5);
        boolean guardUndead = rule == Undead.INVERTED && anyUndeadFriend(f);
        // candidates within reach plus the radius: a hurt friend just out of reach can still sit at the area's edge
        int k = nearest(f, F_FRIEND, skip, sx, sz, reach + radius, minMissing);
        for (int c = 0; c < k; c++) {
            int i = f.cand[c];
            double cx = 0, cz = 0;
            int m = 0;
            for (int j = 0; j < f.n; j++)
                if (has(f.flags[j], F_FRIEND, skip) && hurt(f, j, minMissing)
                        && d2(f.x[j], f.z[j], f.x[i], f.z[i]) <= r2) {
                    cx += f.px[j];
                    cz += f.pz[j];
                    m++;
                }
            if (m < AREA_HEAL_MIN_HURT)
                continue;
            cx /= m;
            cz /= m;
            if (d2(cx, cz, sx, sz) > reach2)
                continue;
            if (guardUndead && undeadFriendNear(f, cx, cz, undead2))
                continue;
            int mended = 0;
            double gain = 0;
            for (int j = 0; j < f.n; j++)
                if (has(f.flags[j], F_FRIEND, skip) && hurt(f, j, minMissing) && d2(f.px[j], f.pz[j], cx, cz) <= r2) {
                    mended++;
                    gain += Math.min(AREA_HEAL_PER_FRIEND, f.missing(j)) / Math.max(1f, f.maxHealth[j])
                        + valueTiebreak(f.value[j]);
                }
            if (mended < AREA_HEAL_MIN_HURT)
                continue;
            int friends = countWithin(f, F_FRIEND, skip, cx, cz, radius, true);
            int foes = countWithin(f, F_ENEMY, skip, cx, cz, radius, true);
            if (foes > friends)
                continue;
            if (gain > out.score) {
                out.index = i;
                out.x = cx;
                out.z = cz;
                out.score = gain;
            }
        }
        return out.index >= 0;
    }

    /**
     * The friend within {@code reach} with an affliction the cleanse lifts ({@code mask}: F_BURNING, F_AFFLICTED), the
     * lowest on health first; -1 if none.
     */
    public static int cleanseTarget(SupportField f, double sx, double sz, double reach, int mask) {
        int best = -1;
        float bestFrac = Float.MAX_VALUE;
        double reach2 = reach * reach;
        for (int i = 0; i < f.n; i++) {
            if ((f.flags[i] & F_FRIEND) == 0 || (f.flags[i] & mask) == 0 || d2(f.x[i], f.z[i], sx, sz) > reach2)
                continue;
            float frac = f.health[i] / Math.max(1f, f.maxHealth[i]);
            if (frac < bestFrac) {
                bestFrac = frac;
                best = i;
            }
        }
        return best;
    }

    /**
     * Our front line near (sx, sz): of the {@link #MAX_CANDIDATES} nearest friends within {@code r} that are not
     * support units (nor the looker), the one with the most enemies within {@link #ENGAGE_RADIUS}; ties go to the
     * nearer. -1 when none of them has an enemy that close.
     */
    public static int front(SupportField f, double sx, double sz, double r) {
        int k = nearest(f, F_FRIEND, F_SUPPORT | F_SELF, sx, sz, r, 0f);
        int best = -1, bestCount = 0;
        for (int c = 0; c < k; c++) {
            int i = f.cand[c];
            int cnt = countWithin(f, F_ENEMY, 0, f.x[i], f.z[i], ENGAGE_RADIUS, false);
            if (cnt > bestCount) {
                bestCount = cnt;
                best = i;
            }
        }
        return best;
    }

    /** How far entry {@code i} stands from (sx, sz) along the unit direction (ux, uz); behind is negative. */
    public static double along(SupportField f, int i, double sx, double sz, double ux, double uz) {
        return (f.x[i] - sx) * ux + (f.z[i] - sz) * uz;
    }

    /** The entry matching the flags furthest along the direction (ux, uz) from (sx, sz) - a march's head; or -1. */
    public static int furthestAlong(SupportField f, int all, int none, double sx, double sz, double ux, double uz) {
        int best = -1;
        double bestA = -Double.MAX_VALUE;
        for (int i = 0; i < f.n; i++) {
            if (!has(f.flags[i], all, none))
                continue;
            double a = along(f, i, sx, sz, ux, uz);
            if (a > bestA) {
                bestA = a;
                best = i;
            }
        }
        return best;
    }

    /** The centre of the entries matching the flags within {@code r} of (cx, cz) into out[0], out[1]; returns how many. */
    public static int centroid(SupportField f, int all, int none, double cx, double cz, double r, double[] out) {
        double r2 = r * r, x = 0, z = 0;
        int m = 0;
        for (int i = 0; i < f.n; i++)
            if (has(f.flags[i], all, none) && d2(f.x[i], f.z[i], cx, cz) <= r2) {
                x += f.x[i];
                z += f.z[i];
                m++;
            }
        if (m > 0) {
            out[0] = x / m;
            out[1] = z / m;
        }
        return m;
    }

    /**
     * Which way to step out of danger, as a unit vector into out[0], out[1]; false when nothing threatens. Threats: an
     * enemy melee unit within {@code dangerR}, or an enemy within {@code aimedR} that has the healer as its target (at
     * half weight when it is not that close). The step leads away from their weighted centre (nearer = heavier), bent toward our own
     * army's centre (the friends round it that are not support units) unless that lies on the threat's side.
     */
    public static boolean fleeDirection(SupportField f, double sx, double sz, double dangerR, double aimedR, double[] out) {
        double tx = 0, tz = 0, w = 0, danger2 = dangerR * dangerR, aimed2 = aimedR * aimedR;
        for (int i = 0; i < f.n; i++) {
            int fl = f.flags[i];
            if ((fl & F_ENEMY) == 0)
                continue;
            double d = d2(f.x[i], f.z[i], sx, sz);
            boolean close = (fl & F_MELEE) != 0 && d <= danger2;
            boolean aimed = (fl & F_TARGETS_ME) != 0 && d <= aimed2;
            if (!close && !aimed)
                continue;
            double wi = 1.0 / (Math.sqrt(d) + 0.5);
            if (!close)
                wi *= AIMED_WEIGHT;
            tx += wi * f.x[i];
            tz += wi * f.z[i];
            w += wi;
        }
        if (w <= 0)
            return false;
        tx /= w;
        tz /= w;
        double ax = sx - tx, az = sz - tz, len = Math.sqrt(ax * ax + az * az);
        if (len < 0.01) {
            ax = 1;
            az = 0;
        } else {
            ax /= len;
            az /= len;
        }
        double bx = 0, bz = 0;
        int m = 0;
        for (int i = 0; i < f.n; i++)
            if (has(f.flags[i], F_FRIEND, F_SUPPORT | F_SELF)) {
                bx += f.x[i];
                bz += f.z[i];
                m++;
            }
        if (m > 0) {
            bx = bx / m - sx;
            bz = bz / m - sz;
            double bl = Math.sqrt(bx * bx + bz * bz);
            if (bl > 1.0) {
                bx /= bl;
                bz /= bl;
                if (bx * ax + bz * az > -0.3) {
                    ax += 0.5 * bx;
                    az += 0.5 * bz;
                    len = Math.sqrt(ax * ax + az * az);
                    if (len > 0.01) {
                        ax /= len;
                        az /= len;
                    }
                }
            }
        }
        out[0] = ax;
        out[1] = az;
        return true;
    }

    /**
     * The spot {@code dist} blocks from (fx, fz) on the side away from (ex, ez) - where a healer stands behind a front at
     * (fx, fz) fighting enemies centred on (ex, ez). When the two coincide, the side is the healer's own at (hx, hz).
     */
    public static void behind(double fx, double fz, double ex, double ez, double dist, double hx, double hz, double[] out) {
        double dx = fx - ex, dz = fz - ez, len = Math.sqrt(dx * dx + dz * dz);
        if (len < 0.01) {
            dx = hx - fx;
            dz = hz - fz;
            len = Math.sqrt(dx * dx + dz * dz);
            if (len < 0.01) {
                dx = 1;
                dz = 0;
                len = 1;
            }
        }
        out[0] = fx + dx / len * dist;
        out[1] = fz + dz / len * dist;
    }

    /**
     * Pushes the spot in out[0], out[1] straight away from every enemy melee unit nearer than {@code clear} (three
     * passes, so two foes either side settle), so a standoff never lands in someone's reach.
     */
    public static void keepClear(SupportField f, double clear, double[] out) {
        double c2 = clear * clear;
        for (int pass = 0; pass < 3; pass++) {
            boolean moved = false;
            for (int i = 0; i < f.n; i++) {
                if ((f.flags[i] & (F_ENEMY | F_MELEE)) != (F_ENEMY | F_MELEE))
                    continue;
                double dx = out[0] - f.x[i], dz = out[1] - f.z[i], d = dx * dx + dz * dz;
                if (d >= c2)
                    continue;
                double len = Math.sqrt(d);
                if (len < 0.01) {
                    dx = 1;
                    dz = 0;
                    len = 1;
                }
                out[0] = f.x[i] + dx / len * clear;
                out[1] = f.z[i] + dz / len * clear;
                moved = true;
            }
            if (!moved)
                return;
        }
    }

    /**
     * The spot of the {@code i}-th of {@code n} support units in a Fight order's rear rank: {@code back} blocks short of
     * the clicked spot (cx, cz) on the line from the group's centre (gx, gz) - never behind the group's centre - and
     * {@code spacing} apart across that line, in the order they stand across it (i = 0 leftmost).
     */
    public static void rearRank(double gx, double gz, double cx, double cz, double back, int i, int n, double spacing, double[] out) {
        double dx = cx - gx, dz = cz - gz, len = Math.sqrt(dx * dx + dz * dz);
        if (len < 0.5) {
            dx = 1;
            dz = 0;
            back = 0;
        } else {
            dx /= len;
            dz /= len;
            back = Math.min(back, len);
        }
        double side = (i - (n - 1) / 2.0) * spacing;
        out[0] = cx - dx * back - dz * side;
        out[1] = cz - dz * back + dx * side;
    }

    /** Across-the-move coordinate of (x, z) for a move from (gx, gz) to (cx, cz): sorts a rear rank left to right. */
    public static double across(double gx, double gz, double cx, double cz, double x, double z) {
        double dx = cx - gx, dz = cz - gz, len = Math.sqrt(dx * dx + dz * dz);
        if (len < 0.5) {
            dx = 1;
            dz = 0;
        } else {
            dx /= len;
            dz /= len;
        }
        return (x - gx) * -dz + (z - gz) * dx;
    }

    /** The point on the circle of radius {@code r} round (bx, bz) nearest to (sx, sz), into out[0], out[1]. */
    public static void onCircle(double bx, double bz, double r, double sx, double sz, double[] out) {
        double dx = sx - bx, dz = sz - bz, len = Math.sqrt(dx * dx + dz * dz);
        if (len < 0.01) {
            dx = 1;
            dz = 0;
            len = 1;
        }
        out[0] = bx + dx / len * r;
        out[1] = bz + dz / len * r;
    }
}
