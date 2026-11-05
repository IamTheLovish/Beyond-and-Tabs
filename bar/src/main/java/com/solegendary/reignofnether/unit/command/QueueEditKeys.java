package com.solegendary.reignofnether.unit.command;

/**
 * The key rules of BAR's queue editing (Legacy layout: command_skip_current on N, command_cancel_last on Ctrl+N,
 * commandinsert on Space held while giving an order). Pure decisions with no client classes, so the GameTests can pin
 * them; the client (unit/QueueEditClientEvents) feeds them the key state.
 * <ul>
 *   <li>N is also the Garrison / Ungarrison button. One action per press: N skips the current order unless the
 *       command card shows a button on N (garrison) AND no selected unit has an order waiting - then the button keeps
 *       N. So with a queue to step through N is BAR's skip; without one the card's Garrison works as before.</li>
 *   <li>Ctrl+N always cancels the last order (nothing else is on Ctrl+N; the card buttons ignore it).</li>
 *   <li>Space held while an order is issued with units selected makes it an insert (front of the queue). The Space
 *       release after such an order is swallowed, so it neither jumps to the last alert nor opens the shop; a plain
 *       Space tap keeps its old meaning.</li>
 * </ul>
 */
public final class QueueEditKeys {

    private QueueEditKeys() { }

    public enum Edit { NONE, SKIP_CURRENT, CANCEL_LAST }

    /** Set when an order went out while the insert key was held: that key's release is not a tap. */
    private static boolean insertUsed;

    /** What a press of the skip key does. */
    public static Edit onSkipKey(boolean ctrl, boolean haveOwnUnits, boolean cardHasButtonOnKey, boolean selectionHasQueue) {
        if (!haveOwnUnits)
            return Edit.NONE;
        if (ctrl)
            return Edit.CANCEL_LAST;
        if (cardHasButtonOnKey && !selectionHasQueue)
            return Edit.NONE;   // the card's Garrison / Ungarrison keeps the key
        return Edit.SKIP_CURRENT;
    }

    /** The queue mode for an order given now: Ctrl or the held insert key (with units selected) put it in front. */
    public static QueueMode modeFor(boolean shift, boolean ctrl, boolean insertHeld, boolean haveUnits) {
        boolean insert = insertHeld && haveUnits;
        if (insert)
            insertUsed = true;
        return QueueMode.of(shift, ctrl || insert);
    }

    /** The insert key was released: true if it was used for an order (swallow the release), false for a plain tap. */
    public static boolean consumeInsertRelease() {
        boolean used = insertUsed;
        insertUsed = false;
        return used;
    }
}
