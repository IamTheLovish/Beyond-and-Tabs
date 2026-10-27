package com.solegendary.reignofnether.keybinds;

import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * BAR's Ctrl selection keys (keybind_defaults.json, profile "Legacy"): Ctrl+W army, Ctrl+C commander (selectcomm focus),
 * Ctrl+B next idle builder, Ctrl+A every unit, Ctrl+Z every unit of the selected types. Ctrl+Shift+B is ours: the next
 * idle T2 builder. Fixed chords, not rebindable, like the Ctrl+W that came first; HudClientEvents.onKeyRelease runs them
 * before anything else on the key, so the order, ability, build-tab and build-slot buttons on W / C / B / A / Z stay
 * quiet while Ctrl is held. Ctrl + a digit stays the control groups (no digit here). No Minecraft types:
 * IntuitiveKeysGameTests and ChordKeysTest check the table and the selection rule headless.
 */
public final class ChordKeys {

    private ChordKeys() { }

    public enum Chord { NONE, SELECT_ARMY, SELECT_COMMANDER, IDLE_BUILDER, IDLE_T2_BUILDER, SELECT_ALL_UNITS, SELECT_SAME_TYPE }

    /** One chord: Ctrl + {@code key}; {@code shift} true = Shift must be held, false = must not, null = either. */
    public record Def(Chord chord, int key, Boolean shift, String label) { }

    public static final List<Def> ALL = List.of(
        new Def(Chord.SELECT_ARMY,      GLFW.GLFW_KEY_W, null,          "Ctrl + W"),
        new Def(Chord.SELECT_COMMANDER, GLFW.GLFW_KEY_C, null,          "Ctrl + C"),
        new Def(Chord.IDLE_BUILDER,     GLFW.GLFW_KEY_B, Boolean.FALSE, "Ctrl + B"),
        new Def(Chord.IDLE_T2_BUILDER,  GLFW.GLFW_KEY_B, Boolean.TRUE,  "Ctrl + Shift + B"),
        new Def(Chord.SELECT_ALL_UNITS, GLFW.GLFW_KEY_A, null,          "Ctrl + A"),
        new Def(Chord.SELECT_SAME_TYPE, GLFW.GLFW_KEY_Z, null,          "Ctrl + Z")
    );

    /** The chord a key release is, from the modifiers held then; NONE without Ctrl or with Alt (Ctrl+Alt+D deletes). */
    public static Chord of(int key, boolean ctrl, boolean shift, boolean alt) {
        if (!ctrl || alt || key < 0)
            return Chord.NONE;
        for (Def d : ALL)
            if (d.key() == key && (d.shift() == null || d.shift() == shift))
                return d.chord();
        return Chord.NONE;
    }

    /** "Ctrl + C" for the controls sheet and tooltips; "" for NONE. */
    public static String label(Chord chord) {
        for (Def d : ALL)
            if (d.chord() == chord)
                return d.label();
        return "";
    }

    /** The short label for a 3-character button corner: "^C", "^SB". */
    public static String shortLabel(Chord chord) {
        for (Def d : ALL)
            if (d.chord() == chord)
                return "^" + (Boolean.TRUE.equals(d.shift()) ? "S" : "") + (char) d.key();
        return "";
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
