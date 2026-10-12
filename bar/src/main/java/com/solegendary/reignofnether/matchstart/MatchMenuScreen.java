package com.solegendary.reignofnether.matchstart;

import com.solegendary.reignofnether.alliance.AlliancesClient;
import com.solegendary.reignofnether.config.ReignOfNetherConfigScreen;
import com.solegendary.reignofnether.faction.Faction;
import com.solegendary.reignofnether.player.PlayerClientEvents;
import com.solegendary.reignofnether.player.PlayerColors;
import com.solegendary.reignofnether.player.PlayerServerboundPacket;
import com.solegendary.reignofnether.player.RTSPlayer;
import com.solegendary.reignofnether.player.ResignVoteClientEvents;
import com.solegendary.reignofnether.sandbox.SandboxClientEvents;
import com.solegendary.reignofnether.time.TimeUtils;
import com.solegendary.reignofnether.util.MyRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.GenericDirtMessageScreen;
import net.minecraft.client.gui.screens.OptionsScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.ShareToLanScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.network.chat.Component;

/**
 * BAR's in-match menu, in place of the vanilla "Game Menu" (lovish's clip: he left a skirmish through Advancements /
 * Statistics / Give Feedback / Open to LAN). Opened by {@link MatchMenuClientEvents} whenever Escape would have
 * opened the vanilla PauseScreen while the RTS view is up. One narrow dark panel, titled with the match clock and
 * your faction, a thin stripe in your player colour down its left edge (the faction accent of the lobby and results
 * screens), and the buttons a match actually needs:
 * <ul>
 *   <li><b>Resume</b> - back to the battlefield;</li>
 *   <li><b>Resign</b> - two clicks (the second confirms). On a team it calls a resign vote
 *       ({@link ResignVoteClientEvents}); alone it is the existing surrender path, after which the results popup
 *       offers Spectate;</li>
 *   <li><b>Spectate</b> - once you are out of the match (defeated or a winner), watch the rest of it;</li>
 *   <li><b>Options</b> (vanilla) and <b>Mod Settings</b> (alerts, announcer voice...);</li>
 *   <li><b>Open to LAN</b> - kept from the vanilla menu for a friend to join;</li>
 *   <li><b>Save &amp; Quit to Title</b>.</li>
 * </ul>
 * It pauses a single-player world the way the vanilla menu does ({@link #isPauseScreen()}).
 */
public class MatchMenuScreen extends Screen {

    private static final int BG_PANEL = 0xE0101216, EDGE = 0xFF3A3F46, ACCENT = 0xFFE6C76A, DIM = 0xFF9A9A9A;
    private static final int PANEL_W = 170, BTN_W = 150, BTN_H = 20, GAP = 4, PAD = 10;

    private boolean confirmResign = false;
    private final boolean fromEscape;

    public MatchMenuScreen() {
        this(true);
    }

    public MatchMenuScreen(boolean fromEscape) {
        super(Component.translatable("matchmenu.reignofnether.title"));
        this.fromEscape = fromEscape;
    }

    /** Still fighting: an RTS player with a faction whose match has no result yet. */
    static boolean inMatch() {
        return PlayerClientEvents.isRTSPlayer() && !SandboxClientEvents.isSandboxPlayer() && !MatchEndClientEvents.hasResults();
    }

    /** The match is over for you (defeated, or a winner still watching with fog on): offer Spectate like the results popup. */
    private static boolean canSpectate() {
        return MatchEndClientEvents.hasResults();
    }

    /** A shared world with no match of yours running: offer the multiplayer lobby (LobbyClientEvents). */
    private static boolean canOpenLobby() {
        return !inMatch() && LobbyClientEvents.isSharedWorld();
    }

    private static int rows() {
        return 5 + (inMatch() ? 1 : 0) + (canSpectate() ? 1 : 0) + (canOpenLobby() ? 1 : 0);
    }

    private int panelHeight() {
        return PAD + 26 + rows() * (BTN_H + GAP) + PAD;
    }

    @Override
    protected void init() {
        int panelH = panelHeight();
        int left = (width - PANEL_W) / 2;
        int top = Math.max(8, (height - panelH) / 2);
        int x = left + (PANEL_W - BTN_W) / 2;
        int y = top + PAD + 26;

        addRenderableWidget(Button.builder(Component.translatable("matchmenu.reignofnether.resume"), b -> onClose())
            .bounds(x, y, BTN_W, BTN_H).build());
        y += BTN_H + GAP;

        if (inMatch()) {
            boolean team = ResignVoteClientEvents.hasHumanAllies();
            Button resign = addRenderableWidget(Button.builder(confirmResign
                    ? Component.translatable("matchmenu.reignofnether.resign_confirm") : resignLabel(team), b -> onResign())
                .bounds(x, y, BTN_W, BTN_H).build());
            resign.setTooltip(Tooltip.create(Component.translatable(team
                ? "matchmenu.reignofnether.resign.tooltip_team" : "matchmenu.reignofnether.resign.tooltip")));
            y += BTN_H + GAP;
        }
        if (canSpectate()) {
            Button spectate = addRenderableWidget(Button.builder(Component.translatable("matchend.reignofnether.spectate"), b -> {
                MatchEndClientEvents.spectate();
                onClose();
            }).bounds(x, y, BTN_W, BTN_H).build());
            spectate.setTooltip(Tooltip.create(Component.translatable("matchend.reignofnether.spectate.tooltip")));
            y += BTN_H + GAP;
        }
        if (canOpenLobby()) {
            Button lobby = addRenderableWidget(Button.builder(Component.literal("Multiplayer Lobby"), b -> LobbyClientEvents.openLobby())
                .bounds(x, y, BTN_W, BTN_H).build());
            lobby.setTooltip(Tooltip.create(Component.literal("The skirmish lobby for everyone on this server")));
            y += BTN_H + GAP;
        }
        addRenderableWidget(Button.builder(Component.translatable("menu.options"),
                b -> minecraft.setScreen(new OptionsScreen(this, minecraft.options)))
            .bounds(x, y, BTN_W, BTN_H).build());
        y += BTN_H + GAP;
        Button settings = addRenderableWidget(Button.builder(Component.translatable("matchmenu.reignofnether.settings"),
                b -> minecraft.setScreen(new ReignOfNetherConfigScreen(this)))
            .bounds(x, y, BTN_W, BTN_H).build());
        settings.setTooltip(Tooltip.create(Component.translatable("matchmenu.reignofnether.settings.tooltip")));
        y += BTN_H + GAP;
        // vanilla's rule: a single-player world that isn't already shared
        Button lan = addRenderableWidget(Button.builder(Component.translatable("menu.shareToLan"),
                b -> minecraft.setScreen(new ShareToLanScreen(this)))
            .bounds(x, y, BTN_W, BTN_H).build());
        lan.active = minecraft.hasSingleplayerServer() && !minecraft.getSingleplayerServer().isPublished();
        y += BTN_H + GAP;
        addRenderableWidget(Button.builder(Component.translatable("menu.returnToMenu"), this::quitToTitle)
            .bounds(x, y, BTN_W, BTN_H).build());
    }

    private static Component resignLabel(boolean team) {
        return Component.translatable(team ? "matchmenu.reignofnether.resign_vote" : "matchmenu.reignofnether.resign");
    }

    // first click arms the button, the second resigns; any other click (or reopening the menu) disarms it
    private void onResign() {
        if (!confirmResign) {
            confirmResign = true;
            rebuildWidgets();
            return;
        }
        PlayerServerboundPacket.resignVote();
        onClose();   // the defeat title, then the results popup with Spectate, play on the battlefield
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        boolean armed = confirmResign;
        boolean handled = super.mouseClicked(mx, my, button);
        // a click anywhere but the resign button itself disarms it
        if (armed && confirmResign && !handled) {
            confirmResign = false;
            rebuildWidgets();
        }
        return handled;
    }

    // the vanilla "Save and Quit to Title" minus Realms
    private void quitToTitle(Button b) {
        boolean local = minecraft.isLocalServer();
        b.active = false;
        if (minecraft.level != null)
            minecraft.level.disconnect();
        if (local)
            minecraft.clearLevel(new GenericDirtMessageScreen(Component.translatable("menu.savingLevel")));
        else
            minecraft.clearLevel();
        TitleScreen title = new TitleScreen();
        minecraft.setScreen(local ? title : new JoinMultiplayerScreen(title));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        int panelH = panelHeight();
        int left = (width - PANEL_W) / 2;
        int top = Math.max(8, (height - panelH) / 2);
        g.fill(left, top, left + PANEL_W, top + panelH, BG_PANEL);
        g.fill(left, top - 1, left + PANEL_W, top, EDGE);
        g.fill(left, top + panelH, left + PANEL_W, top + panelH + 1, EDGE);
        // the accent stripe: your player colour, the same one the roster and minimap use for you
        Minecraft mc = Minecraft.getInstance();
        String me = mc.player != null ? mc.player.getName().getString() : "";
        int stripe = PlayerClientEvents.isRTSPlayer()
            ? 0xFF000000 | (PlayerColors.getPlayerDisplayColorHex(me) & 0xFFFFFF) : ACCENT;
        g.fill(left, top, left + 2, top + panelH, stripe);

        g.drawString(font, title, left + PAD, top + PAD, ACCENT, true);
        // match clock and faction under the title, like the results screen's header
        String clock = TimeUtils.getTimeStrFromTicks(PlayerClientEvents.rtsGameTicks);
        RTSPlayer p = PlayerClientEvents.getRTSPlayer();
        Faction f = p != null ? p.faction : null;
        String sub = clock;
        if (f != null && f.key != null)
            sub = clock + "  -  " + com.solegendary.reignofnether.util.MiscUtil.getFactionName(f);
        // the match's economy pace (EconomyPace) - always shown, so nobody wonders why the game feels slow or fast
        sub = sub + "  -  " + com.solegendary.reignofnether.resources.EconomyPace.name(
            com.solegendary.reignofnether.resources.EconomyPace.getClientCode()) + " pace";
        g.drawString(font, sub, left + PAD, top + PAD + 12, DIM, false);
        if (f != null && f.icon != null)
            MyRenderer.renderIcon(g, f.icon, left + PANEL_W - PAD - 12, top + PAD, 12);
        if (confirmResign) {
            Component warn = Component.translatable("matchmenu.reignofnether.resign_warning");
            g.drawString(font, warn, (width - font.width(warn)) / 2, top + panelH + 6, 0xFFE05A5A, true);
        }
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public void removed() {
        confirmResign = false;   // coming back from Options shouldn't find the button still armed
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        // only a private single-player world pauses: once it is open to LAN (or on a server) the other players'
        // match must go on while you are in this menu. Vanilla also ignores the flag for a published server; this
        // says so here rather than relying on it.
        return fromEscape && !LobbyClientEvents.isSharedWorld();
    }

    @Override
    public void onClose() {
        // TopdownGuiClientEvents reopens the RTS screen a few ticks after no screen is open
        minecraft.setScreen(null);
    }
}
