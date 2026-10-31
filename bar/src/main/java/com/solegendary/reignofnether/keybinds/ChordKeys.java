package com.solegendary.reignofnether.keybinds;

import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;

/**
 * BAR's Ctrl selection keys (keybind_defaults.json, profile "Legacy"): Ctrl+W army, Ctrl+C commander (selectcomm focus),
 * Ctrl+B next idle builder, Ctrl+A every unit, Ctrl+Z every unit of the selected types. Ctrl+Shift+B is ours: the next
 * idle T2 builder. Fixed chords, not rebindable, like the Ctrl+W that came first; HudClientEvents.onKeyRelease runs them
 * before anything else on the key, so the order, ability, build-tab and build-slot buttons on W / C / B / A / Z stay
 * quiet while Ctrl is held. Ctrl + a digit stays the control groups (no digit here). No Minecraft types:
 * IntuitiveKeysGameTests and ChordKeysTest check the table and the selection rule headless.
 *
 * Selection and camera (tester request, BAR Legacy): Ctrl+Q keeps half the selection (SelectPart_50), Alt+Q keeps only
 * the damaged units, Ctrl+. / Ctrl+, centre the camera on the next / previous selected unit (BAR's cycleselected,
 * which sits on plain . and , - here "." is the idle-worker cycle and "," is build slot 9, so they take Ctrl).
 * SelectCameraGameTests and SelectCameraRulesTest check them.
 */
public final class ChordKeys {

    private ChordKeys() { }

    public enum Chord { NONE, SELECT_ARMY, SELECT_COMMANDER, IDLE_BUILDER, IDLE_T2_BUILDER, SELECT_ALL_UNITS, SELECT_SAME_TYPE,
        SELECT_HALF, SELECT_DAMAGED, CYCLE_SELECTED_NEXT, CYCLE_SELECTED_PREV }

    /** BAR's damaged-units filter: a unit below this share of its max health counts as damaged. */
    public static final double DAMAGED_BELOW = 0.6;

    /** One chord: Ctrl + {@code key}; {@code shift} true = Shift must be held, false = must not, null = either. */
    public record Def(Chord chord, int key, Boolean shift, String label) { }

    public static final List<Def> ALL = List.of(
        new Def(Chord.SELECT_ARMY,      GLFW.GLFW_KEY_W, null,          "Ctrl + W"),
        new Def(Chord.SELECT_COMMANDER, GLFW.GLFW_KEY_C, null,          "Ctrl + C"),
        new Def(Chord.IDLE_BUILDER,     GLFW.GLFW_KEY_B, Boolean.FALSE, "Ctrl + B"),
        new Def(Chord.IDLE_T2_BUILDER,  GLFW.GLFW_KEY_B, Boolean.TRUE,  "Ctrl + Shift + B"),
        new Def(Chord.SELECT_ALL_UNITS, GLFW.GLFW_KEY_A, null,          "Ctrl + A"),
        new Def(Chord.SELECT_SAME_TYPE, GLFW.GLFW_KEY_Z, null,          "Ctrl + Z"),
        new Def(Chord.SELECT_HALF,      GLFW.GLFW_KEY_Q, null,          "Ctrl + Q"),
        new Def(Chord.CYCLE_SELECTED_NEXT, GLFW.GLFW_KEY_PERIOD, null,  "Ctrl + ."),
        new Def(Chord.CYCLE_SELECTED_PREV, GLFW.GLFW_KEY_COMMA,  null,  "Ctrl + ,")
    );

    /** The Alt chords (Alt + key, Ctrl not held): BAR's Alt+Q damaged-units filter. Alt + a pan key stays rotate / tilt. */
    public static final List<Def> ALT = List.of(
        new Def(Chord.SELECT_DAMAGED,   GLFW.GLFW_KEY_Q, null,          "Alt + Q")
    );

    /**
     * The chord a key release is, from the modifiers held then: Ctrl without Alt looks in {@link #ALL}, Alt without
     * Ctrl in {@link #ALT}; Ctrl+Alt is never a chord (Ctrl+Alt+D deletes).
     */
    public static Chord of(int key, boolean ctrl, boolean shift, boolean alt) {
        if (key < 0 || ctrl == alt)
            return Chord.NONE;
        for (Def d : ctrl ? ALL : ALT)
            if (d.key() == key && (d.shift() == null || d.shift() == shift))
                return d.chord();
        return Chord.NONE;
    }

    private static Def def(Chord chord) {
        for (Def d : ALL)
            if (d.chord() == chord)
                return d;
        for (Def d : ALT)
            if (d.chord() == chord)
                return d;
        return null;
    }

    /** "Ctrl + C" for the controls sheet and tooltips; "" for NONE. */
    public static String label(Chord chord) {
        Def d = def(chord);
        return d == null ? "" : d.label();
    }

    /** The short label for a 3-character button corner: "^C", "^SB"; an Alt chord is "!Q". */
    public static String shortLabel(Chord chord) {
        Def d = def(chord);
        if (d == null)
            return "";
        return (ALT.contains(d) ? "!" : "^") + (Boolean.TRUE.equals(d.shift()) ? "S" : "") + (char) d.key();
    }

    /** BAR's SelectPart_50 (Ctrl+Q): every other unit of the selection, starting with the first; at least one. */
    public static <T> List<T> half(List<T> selected) {
        List<T> out = new ArrayList<>();
        for (int i = 0; i < selected.size(); i += 2)
            out.add(selected.get(i));
        return out;
    }

    /**
     * Alt+Q: the units of the selection below {@link #DAMAGED_BELOW} of their max health, in selection order. None
     * damaged: an empty list, and the caller keeps the selection unchanged.
     */
    public static <T> List<T> damaged(List<T> selected, ToDoubleFunction<T> healthFraction) {
        List<T> out = new ArrayList<>();
        for (T t : selected)
            if (healthFraction.applyAsDouble(t) < DAMAGED_BELOW)
                out.add(t);
        return out;
    }

    /**
     * Ctrl+. / Ctrl+, (BAR cycleselected): the index of the next ({@code step} 1) or previous (-1) selected unit after
     * {@code current}, wrapping; a {@code current} outside the selection (none yet, or the selection shrank) starts at
     * the first (next) or the last (previous). -1 for an empty selection.
     */
    public static int cycleIndex(int current, int size, int step) {
        if (size <= 0)
            return -1;
        if (current < 0 || current >= size)
            return step >= 0 ? 0 : size - 1;
        return Math.floorMod(current + step, size);
    }

    /**
     * Ctrl+Z: every one of {@code owned} whose type is the type of one of {@code selected}, in {@code owned} order.
     * Nothing selected: nothing (BAR's select-same needs a selection to copy).
     */
    public static <T, K> List<T> sameTypes(List<T> owned, List<T> selected, Function<T, K> type) {
        Set<K> types = new LinkedHashSet<>();
        for (T t : selected)
            types.add(type.apply(t));
        List<T> out = new ArrayList<>();
        if (types.isEmpty())
            return out;
        for (T t : owned)
            if (types.contains(type.apply(t)))
                out.add(t);
        return out;
    }
}
