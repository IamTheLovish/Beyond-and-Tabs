package com.solegendary.reignofnether.player;

import com.solegendary.reignofnether.alliance.AlliancesClient;
import com.solegendary.reignofnether.guiscreen.TopdownGui;
import com.solegendary.reignofnether.hud.HudClientEvents;
import com.solegendary.reignofnether.hud.NotificationClientEvents;
import com.solegendary.reignofnether.hud.TextInputClientEvents;
import com.solegendary.reignofnether.keybinds.Keybindings;
import com.solegendary.reignofnether.orthoview.OrthoviewClientEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.lwjgl.glfw.GLFW;

/**
 * The client half of the team resign vote (ResignVoteServerEvents): keeps the state the server sends
 * (RESIGN_VOTE_SYNC), draws a persistent banner in the alert column while a vote is open - "X called a vote to
 * resign (1/2) - F6 yes / F7 no  24s" - and turns F6 / F7 into answers. The banner is the first thing in the
 * column; the toasts and kill feed move down under it ({@link #bannerHeight()}). The chat line the server also
 * sends is what you read back after the fact; the banner is what you act on.
 */
public class ResignVoteClientEvents {

    private static final Minecraft MC = Minecraft.getInstance();
    private static final int BANNER_H = 13;

    private static boolean open = false;
    private static String caller = "";
    private static int yes = 0, needed = 0, secondsLeft = 0, mine = 0;   // mine: 0 no answer, 1 yes, 2 no
    // the banner text is rebuilt only when a sync changes it, never per frame
    private static String line = "";
    private static int lineW = 0;

    public static boolean isVoteOpen() {
        return open;
    }

    /** True when at least one living ally is a human player: the Resign button then reads "Call resign vote". */
    public static boolean hasHumanAllies() {
        if (MC.player == null)
            return false;
        String me = MC.player.getName().getString();
        for (String ally : AlliancesClient.getAllConnectedAllies(me)) {
            if (ally.equals(me))
                continue;
            RTSPlayer p = PlayerClientEvents.getRTSPlayer(ally);
            if (p != null && !p.isBot())
                return true;
        }
        return false;
    }

    /** From PlayerClientboundPacket: value1 packs yes | needed<<8 | secondsLeft<<16 | mine<<24; state 1 open, 2 passed, 3 failed. */
    public static void sync(String callerName, long packed, int state) {
        caller = callerName;
        yes = (int) (packed & 0xFF);
        needed = (int) ((packed >> 8) & 0xFF);
        secondsLeft = (int) ((packed >> 16) & 0xFF);
        mine = (int) ((packed >> 24) & 0xFF);
        open = state == 1;
        if (open) {
            line = I18n.get(mine == 0 ? "hud.reignofnether.resign_vote" : "hud.reignofnether.resign_vote_answered",
                caller, yes, needed, secondsLeft);
            lineW = MC.font.width(line);
        } else if (state == 2 || state == 3) {
            // the result as a toast, bypassing the alert cooldowns (it is not an alert type)
            NotificationClientEvents.showLine(I18n.get(state == 2 ? "hud.reignofnether.resign_vote_passed"
                : "hud.reignofnether.resign_vote_failed"), state == 2 ? 0xE05A5A : 0x9AA0A6);
        }
    }

    /** Height the banner takes at the top of the alert column right now (0 when no vote is open). */
    public static int bannerHeight() {
        return open ? BANNER_H + 2 : 0;
    }

    @SubscribeEvent
    public static void onInput(InputEvent.Key evt) {
        if (!open || evt.getAction() != GLFW.GLFW_PRESS || !OrthoviewClientEvents.isEnabled()
                || TextInputClientEvents.isAnyInputFocused())
            return;
        if (evt.getKey() == Keybindings.getFnum(6).getKey())
            PlayerServerboundPacket.answerResignVote(true);
        else if (evt.getKey() == Keybindings.getFnum(7).getKey())
            PlayerServerboundPacket.answerResignVote(false);
    }

    // drawn before the toasts (NotificationClientEvents is LOW) at the top of the alert column
    @SubscribeEvent(priority = EventPriority.NORMAL)
    public static void onDrawScreen(ScreenEvent.Render.Post evt) {
        if (!open || !OrthoviewClientEvents.isEnabled() || !(evt.getScreen() instanceof TopdownGui) || com.solegendary.reignofnether.hud.HudPanels.hidden())
            return;
        GuiGraphics gg = evt.getGuiGraphics();
        int[] col = HudClientEvents.alertColumn(MC.getWindow().getGuiScaledWidth());
        int cx = (col[0] + col[1]) / 2;
        int y = HudClientEvents.alertBottom + 2;
        int w = lineW + 10;
        gg.fill(cx - w / 2, y - 2, cx + w / 2, y + BANNER_H - 2, 0xC0101216);
        gg.fill(cx - w / 2, y - 2, cx - w / 2 + 2, y + BANNER_H - 2, 0xFFE05A5A);   // red tab: this needs an answer
        gg.drawString(MC.font, line, cx - lineW / 2 + 1, y, mine == 0 ? 0xFFFFE070 : 0xFFC8C8C8, true);
    }

    @SubscribeEvent
    public static void onClientLogout(ClientPlayerNetworkEvent.LoggingOut evt) {
        open = false;
    }

    /** A match reset: nothing left to vote on. */
    public static void clear() {
        open = false;
    }
}
