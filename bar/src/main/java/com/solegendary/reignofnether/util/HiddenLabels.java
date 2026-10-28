package com.solegendary.reignofnether.util;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Blanks widget labels for one draw pass and puts them back (lovish, Oct 23: the title screen printed Minecraft's
 * label under the gothic one - "reprinted twice"). Generic and plain Java so it is unit-tested; the title screen uses
 * it with Buttons around its vanilla render pass.
 */
public final class HiddenLabels {
    private HiddenLabels() {}

    /** Puts the labels back; a second call does nothing. Usable in try-with-resources. */
    public interface Restore extends AutoCloseable {
        void restore();

        @Override
        default void close() {
            restore();
        }
    }

    public static <T, L> Restore hide(List<? extends T> items, Function<T, L> get, BiConsumer<T, L> set,
                                      Predicate<T> eligible, L blank) {
        List<T> touched = new ArrayList<>();
        List<L> saved = new ArrayList<>();
        for (T t : items) {
            if (!eligible.test(t))
                continue;
            touched.add(t);
            saved.add(get.apply(t));
            set.accept(t, blank);
        }
        boolean[] done = {false};
        return () -> {
            if (done[0])
                return;
            done[0] = true;
            for (int i = 0; i < touched.size(); i++)
                set.accept(touched.get(i), saved.get(i));
        };
    }

    /** String-label convenience (tests); widgets pass their own blank, e.g. Component.empty(). */
    public static <T> Restore hide(List<? extends T> items, Function<T, String> get, BiConsumer<T, String> set,
                                   Predicate<T> eligible) {
        return hide(items, get, set, eligible, "");
    }
}
