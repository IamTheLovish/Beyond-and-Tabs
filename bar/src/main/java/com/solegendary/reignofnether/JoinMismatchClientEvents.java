package com.solegendary.reignofnether;

import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.client.gui.ModMismatchDisconnectedScreen;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.lang.reflect.Field;
import java.util.List;

/**
 * Plain-words version line above the title of Forge's join-failure screens (user request, Oct 23): the channel-list
 * screen lists mod versions (a constant here) and not what to do; the registry-mismatch DisconnectedScreen shows
 * nothing about versions. No mixin: a ScreenEvent.Render.Post draws the translated line over the screen.
 * The server's build id comes from BuildId.lastServerChannelVersion(), recorded by our channel's client check:
 * ModMismatchDisconnectedScreen's own data holds mod versions, never the channel string (which carries the build id) (ConnectionData.java:131-143).
 */
@Mod.EventBusSubscriber(modid = ReignOfNether.MOD_ID, value = Dist.CLIENT)
public final class JoinMismatchClientEvents {
    private JoinMismatchClientEvents() { }

    @SubscribeEvent
    public static void onRender(ScreenEvent.Render.Post evt) {
        Screen s = evt.getScreen();
        boolean forge = s instanceof ModMismatchDisconnectedScreen;
        if (!forge && !(s instanceof DisconnectedScreen)) return;
        // a plain disconnect for any other reason (kick, timeout) must not claim a version problem
        if (!forge && !isRegistryMismatch((DisconnectedScreen) s)) return;
        Component line = BuildId.toComponent(BuildId.joinFailureParts(BuildId.current(),
                com.solegendary.reignofnether.registrars.PacketHandler.protocolVersion(),
                BuildId.lastServerChannelVersion()));
        var font = s.getMinecraft().font;
        List<FormattedCharSequence> rows = font.split(line, Math.max(100, s.width - 40));
        // Both screens centre the title at titleY (Forge: ModMismatchDisconnectedScreen.render; vanilla:
        // DisconnectedScreen.init/render, height/2 - textHeight/2 - 18); the block ends 8 px above it, never over it.
        int bottom = Math.max(rows.size() * 10 + 4, titleY(s, forge) - 8);
        int y = bottom - rows.size() * 10;
        for (FormattedCharSequence row : rows) {
            evt.getGuiGraphics().drawString(font, row, (s.width - font.width(row)) / 2, y, 0xFFFFAA33);
            y += 10;
        }
    }

    /** Where the screen draws its title (top y), from the screen's own layout rule. */
    private static int titleY(Screen s, boolean forge) {
        int textHeight = 9;
        if (forge) {
            try {
                Field f = ModMismatchDisconnectedScreen.class.getDeclaredField("textHeight");
                f.setAccessible(true);
                textHeight = f.getInt(s);
                // listHeight 140 and textYOffset 18 when the screen has a mismatch list (always, for a failed join)
                return (s.height - 140 - textHeight) / 2 - 18 - 18;
            } catch (Exception e) {
                return s.height / 4;
            }
        }
        return s.height / 2 - textHeight / 2 - 18;
    }

    private static boolean isRegistryMismatch(DisconnectedScreen s) {
        // field names differ between dev and release mappings, so look at every Component field by type
        try {
            for (Field f : DisconnectedScreen.class.getDeclaredFields()) {
                if (f.getType() != Component.class) continue;
                f.setAccessible(true);
                Object v = f.get(s);
                String txt = v instanceof Component c ? c.getString().toLowerCase() : "";
                if (txt.contains("registry") || txt.contains("mismatch") || txt.contains("mod")) return true;
            }
        } catch (Exception e) {
            // fall through
        }
        return false;
    }
}
