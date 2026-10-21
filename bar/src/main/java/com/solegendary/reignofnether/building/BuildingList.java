package com.solegendary.reignofnether.building;

import java.util.ArrayList;

/**
 * The building list of one side (BuildingServerEvents / BuildingClientEvents), an ordinary ArrayList that can say
 * whether it changed: {@link #version()} moves on every add, remove, clear, removeIf, sort and set. {@link BuildingGrid}
 * re-buckets the list only when the version moved, so its index can never be stale - whoever adds or removes a
 * building (placement, destroy, reclaim, defeat clean-up, the sandbox, a GameTest) needs no hook of its own.
 */
public final class BuildingList extends ArrayList<BuildingPlacement> {

    // ArrayList.set is not a structural change, so it leaves modCount alone; counted here instead (a ListIterator.set
    // and Collections.swap/reverse/shuffle go through set too)
    private int replacements = 0;

    /** Changes whenever the list's content changes (both counters only ever grow, so a change never cancels out). */
    public int version() {
        return modCount + replacements;
    }

    @Override
    public BuildingPlacement set(int index, BuildingPlacement element) {
        replacements++;
        return super.set(index, element);
    }
}
