package com.solegendary.reignofnether.hero.ai;

import com.solegendary.reignofnether.unit.command.CommandQueue;
import com.solegendary.reignofnether.unit.command.CommandQueues;
import com.solegendary.reignofnether.unit.command.UnitCommand;
import com.solegendary.reignofnether.unit.interfaces.Unit;

import net.minecraft.world.entity.LivingEntity;

import javax.annotation.Nullable;

/**
 * The player's order first (lovish's hero AI requirement 14: "player move/attack/hold/retreat always wins; AI only casts
 * while executing and takes over when idle; never leaves a move order to chase; hold = cast only from where it
 * stands"), checked before anything else the brain does.
 * <p>
 * It keeps no copy of the orders: what the player last told the hero is its command queue's current order
 * ({@link CommandQueues}: every order a player or a bot gives goes through it, and the brain's own moves never do), and
 * whether that order is still being carried out is the queue's own rule ({@link CommandQueues#stillCarriedOut}).
 * Hold position is the unit's hold flag. So the lock reads a few fields a tick and cannot drift from the unit.
 * <p>
 * D030 (lovish, Oct 27 00:21): "a near-death hero MAY flee on its own at first. Once the player selects or commands it
 * again, the player's order wins. FLEE can override an order, but a fresh player command clears FLEE until the next new
 * danger." Kept as two counters: {@link #newDanger} counts the times the hero became near death (FightEval: below the
 * flee line, after having been healed above the heal line - not merely stepping in and out of reach), and every fresh
 * command or select acknowledges the danger so far. The brain may break an order to flee only while a danger is
 * unacknowledged. The order a flight broke off is kept ({@link #suspended}) to go back to when the hero is healed.
 */
public final class OrderLock {

    /** What the player's current order lets the brain do. */
    public enum Mode {
        NONE(true),     // no order (or it is done): the brain is in charge
        FIGHT(true),    // Fight / patrol / guard: "fight your way there" - every ability, movement included
        MOVE(false),    // a move or any errand: casts from where the hero is, never a step off the route
        ATTACK(false),  // a chosen target: strikes go to it first, no gap closer off the chase the order makes
        HOLD(false);    // hold position: casts from where it stands

        /** May the brain move the hero (a gap closer, an escape) under this order? */
        public final boolean mayMove;

        Mode(boolean mayMove) {
            this.mayMove = mayMove;
        }
    }

    /** An order counts as carried out this many ticks after it was given, before the unit's goals acted on it. */
    static final int GRACE_TICKS = 3;

    private Mode mode = Mode.NONE;
    private int targetId = -1;
    private long seenStart = Long.MIN_VALUE;
    private int dangerEpoch = 0, ackedEpoch = 0;
    @Nullable private UnitCommand current;
    @Nullable private UnitCommand suspended;

    /**
     * Every tick: reads the hero's order. True when its owner gave a command since the last look - a fresh command.
     * A player's ({@code playerOwned}) acknowledges the danger so far (D030) and replaces any order a flight had
     * suspended. A bot's does neither: its army orders sweep every fighter along every few seconds, which is not a
     * player taking the hero back, so the newest of them simply becomes the order to go back to once the hero is healed.
     */
    public boolean refresh(LivingEntity le, Unit unit, long now, boolean playerOwned) {
        CommandQueue q = CommandQueues.get(le.getId());
        UnitCommand c = q == null ? null : q.current();
        boolean fresh = false;
        if (c != null && q.currentStartedAt() != seenStart) {
            seenStart = q.currentStartedAt();
            if (playerOwned) {
                acknowledge();
                suspended = null;
            } else if (suspended != null) {
                suspended = c;
            }
            fresh = true;
        }
        current = c;
        mode = modeOf(le, unit, c, q, now);
        targetId = mode == Mode.ATTACK && c != null ? c.targetId() : -1;
        return fresh;
    }

    private static Mode modeOf(LivingEntity le, Unit unit, @Nullable UnitCommand c, @Nullable CommandQueue q, long now) {
        if (unit.getHoldPosition())
            return Mode.HOLD;
        if (c == null || q == null)
            return Mode.NONE;
        boolean justGiven = now - q.currentStartedAt() <= GRACE_TICKS;
        Mode m = switch (c.kind()) {
            case FIGHT, PATROL, GUARD -> Mode.FIGHT;
            case ATTACK, ATTACK_BUILDING -> Mode.ATTACK;
            case STOP, HOLD, STATE -> Mode.NONE;
            default -> Mode.MOVE;   // move, follow, garrison, an ability cast, any errand
        };
        if (m == Mode.NONE || justGiven)
            return m;
        if (!CommandQueues.stillCarriedOut(le, unit, c))
            return Mode.NONE;
        // a move or an errand is over once the hero stands idle (an ability the player cast has landed, a garrison
        // order was refused); stillCarriedOut alone keeps a one-shot order "going" until the hero picks a fight
        if (m == Mode.MOVE && unit.isIdle())
            return Mode.NONE;
        return m;
    }

    public Mode mode() {
        return mode;
    }

    /** The entity the player ordered the hero to attack, or -1. */
    public int focusId() {
        return targetId;
    }

    // ---- D030 ----

    /** The hero became near death (FightEval): a new danger, which may break an order until acknowledged. */
    public void newDanger() {
        dangerEpoch++;
    }

    /** A select or a command: the player has seen the danger so far; no flight on its own until a new one. */
    public void acknowledge() {
        ackedEpoch = dangerEpoch;
    }

    public boolean fleeAllowed() {
        return dangerEpoch > ackedEpoch;
    }

    /** A flight starts: the order it breaks off (if one is still going) is kept to go back to. */
    public void suspend() {
        suspended = mode != Mode.NONE && mode != Mode.HOLD ? current : null;
    }

    /** The order a flight broke off, once; null if there was none. */
    @Nullable
    public UnitCommand takeSuspended() {
        UnitCommand c = suspended;
        suspended = null;
        return c;
    }
}
