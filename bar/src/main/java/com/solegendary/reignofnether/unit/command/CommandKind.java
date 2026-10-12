package com.solegendary.reignofnether.unit.command;

import com.solegendary.reignofnether.unit.UnitAction;

/**
 * What an order is, as far as a unit's command queue is concerned - Spring / BAR's command families. Reign of
 * Nether's dispatch (UnitActionItem) still decides what each UnitAction does to the unit; the kind decides how the
 * queue treats it:
 * <ul>
 *   <li>{@link #STATE}: a state command (BAR fire state, autocast and passive toggles). Applied at once to every
 *       listed unit in any queue mode, never queued, and it neither clears the queue nor cancels a patrol or guard -
 *       in BAR, CMD_FIRE_STATE / CMD_REPEAT / CMD_ONOFF never enter the command queue either.</li>
 *   <li>{@link #needsLiveEntity}: the order is about an entity (attack, follow, guard a unit, reclaim a wreck) and
 *       is skipped when it comes up after that entity died or was removed - Spring finishes such a command at
 *       once.</li>
 *   <li>{@link #resumable}: an order a Ctrl (insert-front) order may interrupt, which then goes back to the head of
 *       the queue. One-shot orders (abilities, stop, hold, delete, toggles) never run twice.</li>
 * </ul>
 * Everything not listed is {@link #OTHER}: it still queues and replaces like any order, it just isn't resumed.
 */
public enum CommandKind {
    MOVE(true),
    FIGHT(true),            // ATTACK_MOVE: walk there, engaging whatever comes in range (BAR's CMD_FIGHT)
    PATROL(true),
    GUARD(true),
    ATTACK(true),
    ATTACK_BUILDING(true),
    FOLLOW(true),
    BUILD_REPAIR(true),
    RECLAIM(true),
    GARRISON(true),
    WORKER_JOB(true),       // FARM, RETURN_RESOURCES: a worker's errand
    STOP(false),
    HOLD(false),
    STATE(false),
    OTHER(false);

    public final boolean resumable;

    CommandKind(boolean resumable) {
        this.resumable = resumable;
    }

    // one array read per lookup, filled once: the executor classifies every order it starts
    private static final CommandKind[] BY_ACTION = new CommandKind[UnitAction.values().length];

    static {
        for (UnitAction a : UnitAction.values())
            BY_ACTION[a.ordinal()] = classify(a);
    }

    public static CommandKind of(UnitAction action) {
        return action == null ? OTHER : BY_ACTION[action.ordinal()];
    }

    public static boolean isStateCommand(UnitAction action) {
        return of(action) == STATE;
    }

    /** Whether an order of this kind with {@code targetId} names an entity that must still be there when it runs. */
    public boolean needsLiveEntity(int targetId) {
        return switch (this) {
            case ATTACK, FOLLOW -> true;                 // nothing to do without the target
            case GUARD, RECLAIM -> targetId >= 0;        // a unit / wreck; otherwise they name a building / tidepool spot
            default -> false;
        };
    }

    private static CommandKind classify(UnitAction a) {
        switch (a) {
            case MOVE: return MOVE;
            case ATTACK_MOVE: return FIGHT;
            case PATROL: return PATROL;
            case GUARD: return GUARD;
            case ATTACK: return ATTACK;
            case ATTACK_BUILDING: return ATTACK_BUILDING;
            case FOLLOW: return FOLLOW;
            case BUILD_REPAIR: return BUILD_REPAIR;
            case RECLAIM: return RECLAIM;
            case GARRISON: return GARRISON;
            case FARM:
            case RETURN_RESOURCES: return WORKER_JOB;
            case STOP: return STOP;
            case HOLD: return HOLD;
            case SET_STANCE_HOLD_FIRE:
            case SET_STANCE_RETURN_FIRE:
            case SET_STANCE_FIRE_AT_WILL:
            case ENABLE_SOUL_SIPHON_PASSIVE:
            case DISBLE_SOUL_SIPHON_PASSIVE:
            case ENABLE_GREED_IS_GOOD_PASSIVE:
            case DISABLE_GREED_IS_GOOD_PASSIVE: return STATE;
            default:
                // every autocast toggle is named *_AUTOCAST_ENABLE / *_AUTOCAST_DISABLE / ENABLE|DISABLE_AUTOCAST_*,
                // so a new ability's toggles are state commands without being listed here
                return a.name().contains("AUTOCAST") ? STATE : OTHER;
        }
    }
}
