package com.solegendary.reignofnether.commands.lang;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.hud.HudClientEvents;
import com.solegendary.reignofnether.orthoview.OrthoviewClientEvents;
import net.minecraftforge.client.event.ClientChatEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * The command bar (claude/voice-commands.md, layer 2): a chat line that starts with {@link #PROMPT} is an order in
 * the command language. It is parsed against the live {@link GameVocabulary}, executed by {@link IntentExecutor},
 * echoed as a HUD caption ("12 workers: stop, then build Sun Spire near you"), and never sent as chat. A refused
 * line shows why and the nearest order that would have worked. The voice adapter (layer 3) feeds the same path.
 */
public class CommandBarClientEvents {

    public static final String PROMPT = "> ";

    /** Runs {@code line} as an order; the caption is what the HUD shows. Public for the voice adapter and scripts. */
    public static String run(String line) {
        String sentence = line.startsWith(PROMPT.trim()) ? line.substring(PROMPT.trim().length()).trim() : line.trim();
        CommandLanguage.Result r = CommandLanguage.parse(sentence, GameVocabulary.build());
        String caption;
        if (!r.ok()) {
            caption = r.error() + (r.suggestion() == null ? "" : " - try: " + r.suggestion());
        } else {
            caption = IntentExecutor.execute(r.intents());
            if (caption.isEmpty())
                caption = "nothing to do";
        }
        ReignOfNether.LOGGER.info("[Command] \"{}\" -> {}", sentence, r.ok() ? r.intents() : r.error());
        HudClientEvents.showTemporaryMessage(caption);
        return caption;
    }

    @SubscribeEvent
    public static void onChat(ClientChatEvent evt) {
        String msg = evt.getMessage();
        if (msg == null || !msg.startsWith(PROMPT.trim()))
            return;
        evt.setCanceled(true);   // an order, not a message to the other players
        if (!OrthoviewClientEvents.isEnabled())
            return;
        run(msg);
    }
}
