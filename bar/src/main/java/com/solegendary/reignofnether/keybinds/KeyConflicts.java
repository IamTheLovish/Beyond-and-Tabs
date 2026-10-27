package com.solegendary.reignofnether.keybinds;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.ToIntFunction;

/**
 * A command-card key (an order, an ability, a build slot) that a player has put on a camera pan key. Camera and
 * orders are separate conflict contexts (RtsConflictContext), so vanilla's Controls screen never flags them - yet a
 * short tap of a WASD key fires the card button on it and a hold pans, and before the tap rule was lag-tolerant a
 * tap measured as a hold at random (Oct 21 playtest: "attack click is still bugged with WASD" - Attack saved on A).
 * No Minecraft types, so the rule is unit-tested (KeyConflictsTest); {@link Keybinding} uses it to make Controls
 * mark the pair red, and KeybindsClientEvents logs the pairs at start-up.
 *
 * Not a conflict: a binding on the key it ships on. Stop is on S by design (tap = Stop, hold = pan; check_guards
 * ALLOWED_ON_CAMERA_KEYS), so a fresh install has no red keys. It is the player's own move onto a shared key that is
 * flagged (an order put on a pan key, or a pan key put on an order) - or a stale saved binding the layout migration
 * has not moved.
 */
public final class KeyConflicts {

    /** The camera pan bindings (WASD and the arrow keys). */
    public static final Set<String> PAN_BINDINGS = Set.of(
        "key.reignofnether.pan_up", "key.reignofnether.pan_left", "key.reignofnether.pan_down", "key.reignofnether.pan_right",
        "key.reignofnether.pan_plus_x", "key.reignofnether.pan_minus_x", "key.reignofnether.pan_plus_z", "key.reignofnether.pan_minus_z");

    /** One command-card binding on a pan binding's key. */
    public record Overlap(String binding, String pan, int key) { }

    private KeyConflicts() { }

    /**
     * Whether {@code binding} (name, category, key now, key it ships on) conflicts with {@code pan} (key now, key it
     * ships on). Keys are GLFW key codes; unbound (-1) and {@link KeybindMigration#CUSTOM} (a modifier combination or
     * a mouse button) never conflict. Both on the keys they ship on is the one designed overlap (Stop and pan-down on
     * S), so only a move by the player - of either binding - onto the shared key counts.
     */
    public static boolean isPanOverlap(String binding, String category, int key, int defaultKey,
                                       String pan, int panKey, int panDefaultKey) {
        return PAN_BINDINGS.contains(pan)
            && !PAN_BINDINGS.contains(binding)
            && Keybindings.CATEGORY_RTS.equals(category)
            && key > 0 && key == panKey
            && !(key == defaultKey && panKey == panDefaultKey);
    }

    /**
     * A camera anchor key (F5-F8 by default, Keybindings.cameraAnchors) gives way to the F-key meaning that is live on
     * the same key: the resign vote's yes / no (fn_6 / fn_7) while a vote is open, and the spectator's view cycle (fn_8)
     * while spectating. {@code key} is the anchor's key; the others are those bindings' keys now.
     */
    public static boolean anchorYields(int key, boolean voteOpen, int voteYesKey, int voteNoKey,
                                       boolean spectating, int spectateKey) {
        if (key < 0)
            return false;
        return (voteOpen && (key == voteYesKey || key == voteNoKey)) || (spectating && key == spectateKey);
    }

    /** Every RTS binding that sits on a pan key it does not ship on. {@code currentKey} maps a translation key to its key now. */
    public static List<Overlap> panOverlaps(ToIntFunction<String> currentKey) {
        List<Overlap> out = new ArrayList<>();
        Map<String, Integer> defaults = new HashMap<>();
        for (Keybinding kb : Keybindings.all())
            if (kb.getTranslationKey() != null)
                defaults.put(kb.getTranslationKey(), kb.getDefaultKey());
        for (Keybinding kb : Keybindings.all()) {
            String name = kb.getTranslationKey();
            if (name == null)
                continue;
            int key = currentKey.applyAsInt(name);
            for (String pan : PAN_BINDINGS)
                if (isPanOverlap(name, kb.getCategory(), key, kb.getDefaultKey(), pan, currentKey.applyAsInt(pan), defaults.get(pan)))
                    out.add(new Overlap(name, pan, key));
        }
        return out;
    }
}
