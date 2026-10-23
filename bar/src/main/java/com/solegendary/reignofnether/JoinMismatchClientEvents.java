package com.solegendary.reignofnether;

import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.client.gui.ModMismatchDisconnectedScreen;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.lang.reflect.Field;
import java.util.Map;

/**
 * Plain-words version line at the top of Forge's join-failure screens (user request, Oct 23): the channel-list screen
 * shows both build ids but not what to do; the registry-mismatch DisconnectedScreen shows neither. No mixin: a
 * ScreenEvent.Render.Post draws one line over the screen.
 */
@Mod.EventBusSubscriber(modid = ReignOfNether.MOD_ID, value = Dist.CLIENT)
public final class JoinMismatchClientEvents {
    private static final ResourceLocation OUR_CHANNEL = new ResourceLocation(ReignOfNether.MOD_ID, "main");

    private JoinMismatchClientEvents() { }

    @SubscribeEvent
    public static void onRender(ScreenEvent.Render.Post evt) {
        Screen s = evt.getScreen();
        boolean mismatch = s instanceof ModMismatchDisconnectedScreen;
        if (!mismatch && !(s instanceof DisconnectedScreen)) return;
        String text = BuildId.joinFailureText(BuildId.current(), mismatch ? serverChannelVersion(s) : null);
        // a plain disconnect for any other reason (kick, timeout) must not claim a version problem
        if (!mismatch && !isRegistryMismatch((DisconnectedScreen) s)) return;
        var font = s.getMinecraft().font;
        evt.getGuiGraphics().drawCenteredString(font, text, s.width / 2, 6, 0xFFFFAA33);
    }

    private static boolean isRegistryMismatch(DisconnectedScreen s) {
        // field names differ between dev and release mappings, so look at every Component field by type
        try {
            for (Field f : DisconnectedScreen.class.getDeclaredFields()) {
                if (f.getType() != net.minecraft.network.chat.Component.class) continue;
                f.setAccessible(true);
                Object v = f.get(s);
                String txt = v instanceof net.minecraft.network.chat.Component c ? c.getString().toLowerCase() : "";
                if (txt.contains("registry") || txt.contains("mismatch") || txt.contains("mod")) return true;
            }
        } catch (Exception e) {
            // fall through
        }
        return false;
    }

    /** The server's channel string for our channel, from the screen's mismatch data; null if not there. */
    private static String serverChannelVersion(Screen s) {
        try {
            Field f = ModMismatchDisconnectedScreen.class.getDeclaredField("mismatchedModData");
            f.setAccessible(true);
            Map<?, ?> m = (Map<?, ?>) f.get(s);
            Object v = m.get(OUR_CHANNEL);
            return v == null ? null : v.toString();
        } catch (Exception e) {
            return null;
        }
    }
}
