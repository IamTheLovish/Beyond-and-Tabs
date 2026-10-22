package com.solegendary.reignofnether.keybinds;

import com.mojang.blaze3d.platform.InputConstants;
import com.solegendary.reignofnether.config.ReignOfNetherClientConfigs;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.loading.ClientModLoader;
import net.minecraftforge.client.settings.KeyModifier;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Applies {@link KeybindMigration} to the player's real saved bindings, once, as soon as the game has finished loading
 * (the Forge event bus only starts after Forge reloaded options.txt into the mod's key mappings, so a handler on it
 * sees the SAVED keys, never the defaults), and logs any command-card key that sits on a camera pan key.
 */
public class KeybindsClientEvents {

    private static final Logger LOGGER = LogManager.getLogger("reignofnether/keys");
    private static boolean done = false;

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent evt) {
        if (done || evt.phase != TickEvent.Phase.END)
            return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.options == null || mc.getOverlay() != null || ClientModLoader.isLoading())
            return;
        done = true;
        try {
            migrate(mc);
        } catch (RuntimeException e) {
            // never stop the game for a key move: the player can still rebind in Controls
            LOGGER.error("[Keys] keybind migration failed", e);
        }
        logPanOverlaps();
    }

    private static Map<String, KeyMapping> mappings() {
        Map<String, KeyMapping> map = new HashMap<>();
        for (Keybinding kb : Keybindings.all())
            if (kb.getTranslationKey() != null && kb.getMapping() != null)
                map.put(kb.getTranslationKey(), kb.getMapping());
        return map;
    }

    /** The key a binding sits on now: a plain keyboard key, -1 when unbound, CUSTOM for a combo or a mouse button. */
    private static int keyOf(KeyMapping m) {
        if (m == null || m.getKeyModifier() != KeyModifier.NONE)
            return KeybindMigration.CUSTOM;
        InputConstants.Key key = m.getKey();
        if (key.equals(InputConstants.UNKNOWN))
            return InputConstants.UNKNOWN.getValue();
        return key.getType() == InputConstants.Type.KEYSYM ? key.getValue() : KeybindMigration.CUSTOM;
    }

    private static void migrate(Minecraft mc) {
        int saved = ReignOfNetherClientConfigs.KEYBIND_LAYOUT.get();
        if (saved >= KeybindMigration.LAYOUT_VERSION)
            return;
        Map<String, KeyMapping> mappings = mappings();
        List<KeybindMigration.Move> moves = KeybindMigration.plan(saved, name -> keyOf(mappings.get(name)));
        StringBuilder toast = new StringBuilder();
        for (KeybindMigration.Move move : moves) {
            KeyMapping m = mappings.get(move.binding());
            InputConstants.Key to = move.to() < 0 ? InputConstants.UNKNOWN : InputConstants.Type.KEYSYM.getOrCreate(move.to());
            m.setKeyModifierAndCode(KeyModifier.NONE, to);
            LOGGER.info("[Keys] moved {} from {} to {}: {}", move.binding(), keyName(move.from()), keyName(move.to()),
                KeybindMigration.history(move.binding()));
            if (toast.length() > 0)
                toast.append(", ");
            toast.append(I18n.get(move.binding())).append(' ').append(keyName(move.from())).append(" -> ").append(keyName(move.to()));
        }
        if (!moves.isEmpty()) {
            KeyMapping.resetMapping();
            mc.options.save();
            mc.getToasts().addToast(SystemToast.multiline(mc, SystemToast.SystemToastIds.PERIODIC_NOTIFICATION,
                Component.translatable("controls.reignofnether.moved.title"),
                Component.translatable("controls.reignofnether.moved.body", toast.toString())));
        }
        ReignOfNetherClientConfigs.KEYBIND_LAYOUT.set(KeybindMigration.LAYOUT_VERSION);
        LOGGER.info("[Keys] key layout {} -> {}, {} binding(s) moved", saved, KeybindMigration.LAYOUT_VERSION, moves.size());
    }

    private static String keyName(int glfwKey) {
        return glfwKey < 0 ? I18n.get("controls.reignofnether.unbound")
            : InputConstants.Type.KEYSYM.getOrCreate(glfwKey).getDisplayName().getString();
    }

    private static void logPanOverlaps() {
        Map<String, KeyMapping> mappings = mappings();
        for (KeyConflicts.Overlap o : KeyConflicts.panOverlaps(name -> keyOf(mappings.get(name))))
            LOGGER.warn("[Keys] {} is on {}, which is also {}: a quick tap gives the order, holding pans (Controls marks it red)",
                o.binding(), keyName(o.key()), o.pan());
    }
}
