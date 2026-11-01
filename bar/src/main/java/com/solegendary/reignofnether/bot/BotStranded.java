package com.solegendary.reignofnether.bot;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Workers that cannot get anywhere: a worker that stands on one spot while it holds build orders, order after order,
 * is stranded - in a pit or a river bed it cannot climb out of. BuildRepairGoal gives up on each site after 15 s and
 * the bot's next think hands it the next one, so without this it kept every site it was ordered to waiting, and the bot
 * kept counting it as a constructor: in bots_reach_tier_2 (flake-stress 41168069584, runner 2) seven of the easy bot's
 * ten workers stood at y=63 for the last twelve minutes, its T2 lab rose with one builder (0.05 to 0.33 in four minutes)
 * and was never finished, and the capitol trained no one because the bot had its ten.
 * <p>
 * The clock counts the time a worker stands (within {@link #MOVED} blocks of one spot) holding a build order without
 * building, across its give-ups and re-orders; idle time does not count, so a worker waiting at home is never stranded.
 * Walking {@link #MOVED} blocks or building (in range of a site) clears it. At {@link #STRANDED_TICKS} the worker is
 * stranded until it does one of those: BotPlayer leaves it out of every builder pick and out of its worker count (so the
 * capitol trains a replacement), and walks it home every {@link #RESCUE_EVERY_TICKS}; a worker that gets out walks, and
 * is a worker again. Pure: fed positions, so the unit test drives it.
 */
public class BotStranded {

    /** Standing this long under build orders and the worker is stranded (more than two 15 s give-ups). */
    public static final long STRANDED_TICKS = 20 * 40;
    /** Blocks (flat) a worker has to walk from where it stood to count as moving. */
    public static final double MOVED = 3;
    /** How often a stranded worker is walked home. */
    public static final long RESCUE_EVERY_TICKS = 20 * 30;
    /** The longest gap between two samples that counts in full (the bot thinks once a second). */
    static final long MAX_STEP_TICKS = 40;

    static final class Stand {
        double x, z;
        long lastAt;
        long orderedTicks;
        long rescuedAt = -1;
    }

    final Map<Integer, Stand> stands = new HashMap<>();

    /**
     * One worker's sample this think: where it stands, whether it holds a build order and whether it is building (in
     * range of its site). True when it is stranded.
     */
    public boolean sample(int id, double x, double z, boolean ordered, boolean building, long gameTime) {
        Stand s = stands.get(id);
        if (s == null) {
            s = new Stand();
            s.x = x;
            s.z = z;
            s.lastAt = gameTime;
            stands.put(id, s);
        }
        long step = Math.max(0, Math.min(MAX_STEP_TICKS, gameTime - s.lastAt));
        s.lastAt = gameTime;
        double dx = x - s.x, dz = z - s.z;
        if (dx * dx + dz * dz > MOVED * MOVED || building) {
            s.x = x;
            s.z = z;
            s.orderedTicks = 0;
            return false;
        }
        if (ordered)
            s.orderedTicks += step;
        return s.orderedTicks >= STRANDED_TICKS;
    }

    /** Whether a stranded worker is due a walk home now (and records that it got one). */
    public boolean rescueDue(int id, long gameTime) {
        Stand s = stands.get(id);
        if (s == null || (s.rescuedAt >= 0 && gameTime - s.rescuedAt < RESCUE_EVERY_TICKS))
            return false;
        s.rescuedAt = gameTime;
        return true;
    }

    /** Forgets workers that are gone. */
    public void retain(Set<Integer> alive) {
        stands.keySet().retainAll(alive);
    }
}
