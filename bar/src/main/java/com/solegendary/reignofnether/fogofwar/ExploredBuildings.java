package com.solegendary.reignofnether.fogofwar;

import java.util.function.Predicate;

/** STUB (tests first): the signatures only; the next commit gives them their meaning. */
public final class ExploredBuildings<P, B> {

    public void update(P player, Iterable<B> buildings, Predicate<B> visibleNow) { }

    public boolean hasSeen(P player, B building) {
        return false;
    }

    public void forget() { }

    public static boolean mayTarget(boolean fogActive, boolean visibleNow, boolean seenBefore) {
        return true;
    }
}
