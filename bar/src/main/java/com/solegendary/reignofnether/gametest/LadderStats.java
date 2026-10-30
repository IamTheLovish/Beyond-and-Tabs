package com.solegendary.reignofnether.gametest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.TreeSet;

/**
 * The numbers a bot-ladder match (LadderGameTests) records, and the pace figures worked out from them (design 4.6
 * "Pacing"). No Minecraft classes in here, so plain JUnit pins the arithmetic (LadderStatsTest): the match itself only
 * feeds in what it sees - one sample per second, every unit death, every hit between the two sides.
 * <p>
 * Sides are 0 and 1. Army value is the summed metal cost of a side's fighters (attackers that are not workers), the
 * measure the bot soak and the duel simulator already use. All times are game ticks (20 per second).
 * <ul>
 *   <li><b>time to kill</b> - median seconds from the first hit a fighter took from the enemy to its death (how long
 *       a unit lives once the fight is on: the owner wants slower fights, so abilities matter);</li>
 *   <li><b>casts per unit-life</b> - ability casts (a cooldown that started, seen once a second) by fighters, divided
 *       by the fighters that ever stood on the field;</li>
 *   <li><b>peak army</b> - the highest army value and the highest fighter count of a side, and the game time of the
 *       value's peak; <b>army value at minute m</b> as the sample nearest the minute;</li>
 *   <li><b>produced per minute</b> - units that first appear after the start units, per side, per game minute;</li>
 *   <li><b>median lifetime in combat</b> - birth to death, in seconds, of the fighters an enemy killed;</li>
 *   <li><b>fighting share</b> - the share of the match, in {@link #FIGHT_BUCKET}-tick buckets, in which at least one
 *       unit of one side hurt a unit of the other;</li>
 *   <li><b>decisive fight</b> - a {@link #FIGHT_WINDOW}-tick window in which one side lost at least
 *       {@link #DECISIVE_LOSS} of its army value to enemy units (an army under {@link #MIN_ARMY} metal is too small to
 *       call a fight); windows do not overlap, and a fight is dated by its first qualifying death;</li>
 *   <li><b>alive at minute m</b> - units standing at the sample nearest that minute, -1 if the match ended before.</li>
 * </ul>
 */
public final class LadderStats {
    public static final int TPS = 20;
    public static final int FIGHT_BUCKET = 10 * TPS;
    public static final int FIGHT_WINDOW = 30 * TPS;
    public static final float DECISIVE_LOSS = 0.40f;
    public static final int MIN_ARMY = 150;

    /** One fighter or worker's death. {@code byEnemy}: the other side's unit dealt the killing blow. */
    public record Death(long tick, int side, int value, boolean fighter, boolean byEnemy, long bornTick, long firstHitTick) { }

    /** One per-second sample: each side's army value and unit count. */
    public record Sample(long tick, int[] army, int[] units, int[] fighters) { }

    private final List<Sample> samples = new ArrayList<>();
    private final List<Death> deaths = new ArrayList<>();
    private final TreeSet<Long> fightBuckets = new TreeSet<>();
    private final int[] produced = new int[2];
    private final int[] producedFighters = new int[2];
    private final int[] peakArmy = new int[2];
    private final int[] lost = new int[2];
    private final int[] fightersLost = new int[2];
    private final int[] peakFighters = new int[2];
    private final long[] peakArmyTick = new long[2];
    private final int[] fighterLives = new int[2];
    private final int[] casts = new int[2];
    private long endTick;

    public void sample(long tick, int[] army, int[] units, int[] fighters) {
        samples.add(new Sample(tick, army.clone(), units.clone(), fighters.clone()));
        for (int s = 0; s < 2; s++) {
            if (army[s] > peakArmy[s]) {
                peakArmy[s] = army[s];
                peakArmyTick[s] = tick;
            }
            peakFighters[s] = Math.max(peakFighters[s], fighters[s]);
        }
        endTick = Math.max(endTick, tick);
    }

    /** A unit seen for the first time after the start units (the bases' own commander and workers are not output). */
    public void produced(int side, boolean fighter) {
        produced[side]++;
        if (fighter)
            producedFighters[side]++;
    }

    /** A fighter seen on the field (start units included): the denominator of casts per unit-life. */
    public void fighterSeen(int side) {
        fighterLives[side]++;
    }

    /** An ability cast by a fighter of {@code side}. */
    public void cast(int side) {
        casts[side]++;
    }

    public void death(Death d) {
        deaths.add(d);
        if (d.fighter()) {
            lost[d.side()] += d.value();
            fightersLost[d.side()]++;
        }
        endTick = Math.max(endTick, d.tick());
    }

    /** A hit between the two sides at {@code tick}. */
    public void fighting(long tick) {
        fightBuckets.add(tick / FIGHT_BUCKET);
    }

    public void end(long tick) {
        endTick = Math.max(endTick, tick);
    }

    public long endTick() { return endTick; }
    public int peakArmy(int side) { return peakArmy[side]; }
    public int peakFighters(int side) { return peakFighters[side]; }
    public long peakArmyTick(int side) { return peakArmyTick[side]; }
    public int casts(int side) { return casts[side]; }
    public int fighterLives(int side) { return fighterLives[side]; }

    /** Ability casts per fighter that ever stood on the field; -1 without fighters. */
    public double castsPerLife(int side) {
        return fighterLives[side] == 0 ? -1 : casts[side] / (double) fighterLives[side];
    }

    /** Median seconds from first hit taken to death, for fighters the enemy killed; -1 when none (or hit unseen). */
    public double medianTimeToKill(int side) {
        List<Double> secs = new ArrayList<>();
        for (Death d : deaths)
            if (d.side() == side && d.fighter() && d.byEnemy() && d.firstHitTick() >= 0)
                secs.add((d.tick() - d.firstHitTick()) / (double) TPS);
        return median(secs);
    }

    /** A side's army value at game minute {@code minute}; -1 if the match was over before that minute. */
    public int armyAt(int side, int minute) {
        Sample s = nearest(minute);
        return s == null ? -1 : s.army()[side];
    }
    public int lostValue(int side) { return lost[side]; }
    public int fightersLost(int side) { return fightersLost[side]; }
    public int produced(int side) { return produced[side]; }
    public int producedFighters(int side) { return producedFighters[side]; }
    public List<Sample> samples() { return Collections.unmodifiableList(samples); }

    /** Units produced per game minute (all kinds, or only fighters). */
    public double producedPerMinute(int side, boolean fightersOnly) {
        double minutes = endTick / (60.0 * TPS);
        return minutes <= 0 ? 0 : (fightersOnly ? producedFighters[side] : produced[side]) / minutes;
    }

    /** Median seconds from birth to death of the fighters {@code side} lost to the enemy; -1 when there were none. */
    public double medianCombatLifetime(int side) {
        List<Double> secs = new ArrayList<>();
        for (Death d : deaths)
            if (d.side() == side && d.fighter() && d.byEnemy())
                secs.add((d.tick() - d.bornTick()) / (double) TPS);
        return median(secs);
    }

    /** Share (0..1) of the match in which the sides were hitting each other. */
    public double fightingShare() {
        long buckets = (endTick + FIGHT_BUCKET - 1) / FIGHT_BUCKET;
        return buckets <= 0 ? 0 : Math.min(1.0, fightBuckets.size() / (double) buckets);
    }

    /** Units alive for {@code side} at game minute {@code minute}; -1 if the match was over before that minute. */
    public int unitsAliveAt(int side, int minute) {
        Sample s = nearest(minute);
        return s == null ? -1 : s.units()[side];
    }

    private Sample nearest(int minute) {
        long want = minute * 60L * TPS;
        if (endTick + TPS < want || samples.isEmpty())
            return null;
        Sample best = samples.get(0);
        for (Sample s : samples)
            if (Math.abs(s.tick() - want) < Math.abs(best.tick() - want))
                best = s;
        return best;
    }

    /** One decisive fight: when its first qualifying death happened and which side lost at least 40% of its army. */
    public record Fight(long tick, int loser, int lostValue, int armyBefore) { }

    /** The decisive fights, in time order (see the class comment for the rule). */
    public List<Fight> decisiveFights() {
        List<Fight> out = new ArrayList<>();
        long cursor = Long.MIN_VALUE;
        for (Sample s : samples) {
            if (s.tick() < cursor)
                continue;
            Fight found = null;
            for (int side = 0; side < 2; side++) {
                int base = s.army()[side];
                long windowEnd = s.tick() + FIGHT_WINDOW;
                for (Sample w : samples)
                    if (w.tick() >= s.tick() && w.tick() <= windowEnd)
                        base = Math.max(base, w.army()[side]);
                if (base < MIN_ARMY)
                    continue;
                int sum = 0;
                long first = -1;
                for (Death d : deaths)
                    if (d.side() == side && d.fighter() && d.byEnemy() && d.tick() >= s.tick() && d.tick() <= windowEnd) {
                        sum += d.value();
                        if (first < 0)
                            first = d.tick();
                    }
                if (sum >= DECISIVE_LOSS * base && (found == null || sum > found.lostValue()))
                    found = new Fight(first, side, sum, base);
            }
            if (found != null) {
                out.add(found);
                cursor = s.tick() + FIGHT_WINDOW + 1;   // strictly after: a death at the window edge is not a second fight
            }
        }
        return out;
    }

    /** Seconds between each decisive fight and the next (empty for fewer than two fights). */
    public List<Long> fightGapsSeconds() {
        List<Fight> f = decisiveFights();
        List<Long> gaps = new ArrayList<>();
        for (int i = 1; i < f.size(); i++)
            gaps.add((f.get(i).tick() - f.get(i - 1).tick()) / TPS);
        return gaps;
    }

    static double median(List<Double> values) {
        if (values.isEmpty())
            return -1;
        List<Double> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        int n = sorted.size();
        return n % 2 == 1 ? sorted.get(n / 2) : (sorted.get(n / 2 - 1) + sorted.get(n / 2)) / 2.0;
    }

    /** m:ss for a tick count. */
    public static String clock(long ticks) {
        long s = ticks / TPS;
        return String.format(java.util.Locale.ROOT, "%d:%02d", s / 60, s % 60);
    }
}
