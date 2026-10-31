package com.solegendary.reignofnether.keybinds;

import java.util.List;
import java.util.function.Predicate;

/**
 * One key press does one thing. The command card used to hand a press to every button that answered to the key, so an
 * ability and a build slot on the same key both ran (Lane C tester, Oct 21: Q cast the ability AND armed the Mausoleum
 * ghost). The card's keys are kept unique per card (GameTests), but a press must still never double-fire when one slips
 * through, so the first button in card order wins. No Minecraft types: BugSweep1GameTests checks the rule.
 */
public final class KeyPress {

    private KeyPress() { }

    /** The first of {@code candidates} (card order) that fires on the key, or null when none does. */
    public static <T> T firstThatFires(List<T> candidates, Predicate<T> firesOnKey) {
        for (T c : candidates)
            if (firesOnKey.test(c))
                return c;
        return null;
    }
}
