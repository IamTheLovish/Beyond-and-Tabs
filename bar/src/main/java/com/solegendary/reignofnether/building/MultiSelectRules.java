package com.solegendary.reignofnether.building;

import java.util.List;
import java.util.function.IntPredicate;
import java.util.function.Predicate;

/**
 * The decision rules for a command card shown over SEVERAL selected buildings of one type (tester, #38 items 41-42).
 * No client or Minecraft imports, so the rules are plain-Java testable and the HUD and the server share one answer.
 * <ul>
 *   <li>{@link #firstVisible}: a per-building button (a level upgrade) stays on the card while ANY selected building
 *       can still take it - the card used to read only the building the HUD shows, so an upgrade vanished as soon as
 *       that one was upgraded or maxed, though others were not. It is hidden only when none are left.</li>
 *   <li>{@link #pickRoundRobin}: a unit goes to a factory that can build right now. A factory with an upgrade queued
 *       makes nothing until it finishes, so it is only used when no other factory takes the unit.</li>
 * </ul>
 */
public final class MultiSelectRules {
    private MultiSelectRules() { }

    /**
     * The card source for one button: {@code shown} if it is visible there, else the first group member for which it
     * is visible (selection order), else null (hide the button).
     */
    public static <B> B firstVisible(B shown, List<B> group, Predicate<B> visible) {
        if (shown != null && visible.test(shown))
            return shown;
        for (B b : group)
            if (b != shown && visible.test(b))
                return b;
        return null;
    }

    /**
     * Round-robin walk from {@code start} over {@code n} factories: first pass only those {@code preferred}
     * (can build now), second pass any. {@code take} tries to queue on factory i and says whether it did. Returns the
     * index that took it, or -1.
     */
    public static int pickRoundRobin(int n, int start, IntPredicate preferred, IntPredicate take) {
        if (n <= 0)
            return -1;
        start = Math.floorMod(start, n);
        for (int pass = 0; pass < 2; pass++) {
            for (int k = 0; k < n; k++) {
                int i = (start + k) % n;
                if (pass == 0 && !preferred.test(i))
                    continue;
                if (take.test(i))
                    return i;
            }
        }
        return -1;
    }
}
