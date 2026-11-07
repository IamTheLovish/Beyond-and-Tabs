package com.solegendary.reignofnether.hud;

import com.solegendary.reignofnether.keybinds.Keybinding;
import com.solegendary.reignofnether.keybinds.Keybindings;

import java.util.ArrayList;
import java.util.List;

/**
 * Which orders a unit's command card shows, in BAR's order, and the key of each - side-neutral (no client classes)
 * so the HUD lays the card out from it and the GameTest
 * {@code no_two_buttons_on_a_command_card_share_a_hotkey} checks the same list against every unit's abilities and
 * its faction's build menu. lovish's clip: the Geomancer's build menu had the Lotus Academy on Y while Patrol sat on
 * Y too, and the card read X / B / G / N / Y / P / "Spc" with Attack nowhere obvious.
 *
 * Rows of {@link #ROW} from the bottom: Attack, Fight (fighters), Move, Stop, Hold (fighters) or Build (workers),
 * Patrol, Guard, then the fire state, gather target and garrison above them.
 */
public final class CommandCard {

    public static final int ROW = 6;

    public enum Order {
        ATTACK, FIGHT, MOVE, STOP, HOLD, BUILD, PATROL, GUARD, STANCE, GATHER, GARRISON, UNGARRISON;

        /** The order's key, or null for a click-only toggle (gather lost its key to Guard). */
        public Keybinding key() {
            return switch (this) {
                case ATTACK -> Keybindings.attack;
                case FIGHT -> Keybindings.fight;
                case MOVE -> Keybindings.move;
                case STOP -> Keybindings.stop;
                case HOLD -> Keybindings.hold;
                case BUILD -> Keybindings.build;
                case PATROL -> Keybindings.patrol;
                case GUARD -> Keybindings.guard;
                case GARRISON, UNGARRISON -> Keybindings.garrison;
                case STANCE -> Keybindings.fireState;
                case GATHER -> null;
            };
        }
    }

    private CommandCard() { }

    /**
     * @param attacker   an AttackerUnit (gets Attack and, unless a worker, the fire state)
     * @param worker     a WorkerUnit (Build and Gather instead of Hold)
     * @param canGarrison can enter a garrisonable building right now
     * @param garrisoned is inside one (Ungarrison instead)
     */
    public static List<Order> orders(boolean attacker, boolean worker, boolean canGarrison, boolean garrisoned) {
        List<Order> out = new ArrayList<>(10);
        if (attacker)
            out.add(Order.ATTACK);
        // Fight (F) for fighters: a worker's F is its build menu, and the commander is both - it keeps A + click/drag
        if (attacker && !worker)
            out.add(Order.FIGHT);
        out.add(Order.MOVE);
        out.add(Order.STOP);
        out.add(worker ? Order.BUILD : Order.HOLD);
        out.add(Order.PATROL);
        out.add(Order.GUARD);
        if (attacker && !worker)
            out.add(Order.STANCE);   // BAR fire state: Hold fire / Return fire / Fire at will
        if (worker)
            out.add(Order.GATHER);
        if (garrisoned)
            out.add(Order.UNGARRISON);
        else if (canGarrison)
            out.add(Order.GARRISON);
        return out;
    }

    /** How many rows the orders take (the ability / build-menu rows start above them). */
    public static int rows(int orderCount) {
        return Math.max(1, (orderCount + ROW - 1) / ROW);
    }
}
