package com.solegendary.reignofnether.matchstart;

import com.solegendary.reignofnether.guiscreen.TopdownGui;
import com.solegendary.reignofnether.orthoview.OrthoviewClientEvents;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Client side of the multiplayer lobby: keeps the lobby the server last sent, opens and closes
 * {@link MultiplayerLobbyScreen} with it, puts the RTS view on when the host presses Start (or when you join a match
 * that is already being played, as a spectator), and adds "Multiplayer Lobby" to the pause menu of a world that is
 * open to LAN or of a server.
 */
public class LobbyClientEvents {

    /** The lobby as the server last sent it (null before any). */
    static LobbyClientboundPacket latest = null;
    /** Bumped on every snapshot so an open screen knows to rebuild. */
    static int version = 0;
    /** The player hid the lobby screen: don't push it back up until the lobby is reopened. */
    static boolean dismissed = false;
    /** Ticks until we turn on the RTS view for a start (-1 idle), and whether we watch. */
    static int enterRtsIn = -1;
    static boolean enterAsSpectator = false;

    /** For the [Screen] log line: {humans seated or bots, seats total}, or null when no lobby snapshot has arrived. */
    public static int[] seatCounts() {
        LobbyClientboundPacket l = latest;
        if (l == null || l.seats == null)
            return null;
        int filled = 0;
        for (MultiplayerLobby.Seat s : l.seats)
            if (s.bot || (s.name != null && !s.name.isEmpty()))
                filled++;
        return new int[] { filled, l.seats.size() };
    }

    static void onPacket(LobbyClientboundPacket p) {
        Minecraft mc = Minecraft.getInstance();
        switch (p.kind) {
            case SNAPSHOT -> {
                boolean wasOpen = latest != null && latest.open;
                latest = p;
                version++;
                if (p.open) {
                    if (!wasOpen)
                        dismissed = false;
                    if (!dismissed && !(mc.screen instanceof MultiplayerLobbyScreen)
                            && (mc.screen == null || mc.screen instanceof TopdownGui))
                        mc.setScreen(new MultiplayerLobbyScreen());
                } else if (mc.screen instanceof MultiplayerLobbyScreen) {
                    mc.setScreen(null);
                }
            }
            case START, SPECTATE -> {
                if (mc.screen instanceof MultiplayerLobbyScreen)
                    mc.setScreen(null);
                enterRtsIn = 2;
                enterAsSpectator = p.spectating;
            }
        }
    }

    /** Opens the lobby screen and asks the server for the lobby (the host's request opens one). */
    public static void openLobby() {
        dismissed = false;
        LobbyServerboundPacket.send(MultiplayerLobby.Action.OPEN, 0, 0);
        Minecraft.getInstance().setScreen(new MultiplayerLobbyScreen());
    }

    /** A world other people can be in: an integrated server opened to LAN, or a remote server. */
    public static boolean isSharedWorld() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.hasSingleplayerServer())
            return mc.getSingleplayerServer() != null && mc.getSingleplayerServer().isPublished();
        return mc.getConnection() != null;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent evt) {
        if (evt.phase != TickEvent.Phase.END || enterRtsIn < 0)
            return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null)
            return;
        // like QuickBattle: wait for no foreign screen (the RTS view itself is a screen)
        if (mc.screen != null && !(mc.screen instanceof TopdownGui))
            return;
        if (--enterRtsIn > 0)
            return;
        enterRtsIn = -1;
        if (!OrthoviewClientEvents.isEnabled())
            OrthoviewClientEvents.toggleEnable();
        if (enterAsSpectator)
            com.solegendary.reignofnether.hud.playerdisplay.PlayerDisplayClientEvents.showObserverDisplays();
    }

    // "Multiplayer Lobby" on the vanilla pause menu of a shared world (top left, clear of vanilla's buttons). In
    // the RTS view the match menu replaces the pause menu and carries its own button (MatchMenuScreen).
    @SubscribeEvent
    public static void onPauseScreen(ScreenEvent.Init.Post evt) {
        if (!(evt.getScreen() instanceof PauseScreen) || !isSharedWorld())
            return;
        Button b = Button.builder(Component.literal("Multiplayer Lobby"), btn -> openLobby())
            .bounds(6, 6, 120, 20).build();
        b.setTooltip(Tooltip.create(Component.literal("The skirmish lobby for everyone on this server: seats, factions, teams, Ready, Start.")));
        evt.addListener(b);
    }

    @SubscribeEvent
    public static void onClientLogout(ClientPlayerNetworkEvent.LoggingOut evt) {
        latest = null;
        version++;
        dismissed = false;
        enterRtsIn = -1;
    }
}
