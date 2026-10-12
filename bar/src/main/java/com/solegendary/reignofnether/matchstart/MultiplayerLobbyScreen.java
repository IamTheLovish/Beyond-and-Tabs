package com.solegendary.reignofnether.matchstart;

import com.solegendary.reignofnether.faction.Faction;
import com.solegendary.reignofnether.player.PlayerPalette;
import com.solegendary.reignofnether.util.MiscUtil;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.ShareToLanScreen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * The multiplayer lobby screen (MultiplayerLobby on the server, design/multiplayer.md). It only shows the lobby the
 * server last sent (LobbyClientEvents.latest) and sends clicks back; the server decides. Rows are grouped by team:
 * a colour swatch (click: next free colour, right-click: any), the name, the faction (a button on your own row and,
 * for the host, on bot rows), the team, and Ready / the bot's skill. On the right: the map settings and Add AI (the
 * host's), your Ready toggle, Spectate / Play, Start (the host's, once everyone is ready) and Close.
 */
public class MultiplayerLobbyScreen extends Screen {

    static final int BG = 0xE0101216, PANEL = 0xC0181B20, EDGE = 0xFF3A3F46, GOLD = 0xFFD27A, DIM = 0x9A9A9A;
    static final int ROW_H = 18, SWATCH = 10, RIGHT_W = 150;

    int left, top, bottom, listW, rightX;
    int builtVersion = -1;
    final FactionPicker picker = new FactionPicker();
    /** Swatches drawn this layout: x, y, seat id. */
    final List<int[]> swatches = new ArrayList<>();
    /** Faction icons beside the faction buttons/labels: x, y, faction. */
    final List<Object[]> factionIcons = new ArrayList<>();
    /** Rows to draw: y, seat (null for a header) and header text. */
    final List<Object[]> rows = new ArrayList<>();

    public MultiplayerLobbyScreen() {
        super(Component.literal("Multiplayer Lobby"));
    }

    static String myName() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null ? mc.player.getName().getString() : "";
    }

    static boolean amHost(LobbyClientboundPacket l) {
        return l != null && l.hostName.equals(myName());
    }

    static MultiplayerLobby.Seat mySeat(LobbyClientboundPacket l) {
        if (l == null)
            return null;
        for (MultiplayerLobby.Seat s : l.seats)
            if (!s.bot && s.name.equals(myName()))
                return s;
        return null;
    }

    static boolean canEdit(LobbyClientboundPacket l, MultiplayerLobby.Seat s) {
        if (l == null || l.starting)
            return false;
        return s.bot ? amHost(l) : s.name.equals(myName());
    }

    @Override
    protected void init() {
        LobbyClientboundPacket l = LobbyClientEvents.latest;
        builtVersion = LobbyClientEvents.version;
        swatches.clear();
        factionIcons.clear();
        rows.clear();
        picker.close();
        int totalW = Math.min(width - 16, 600);
        left = width / 2 - totalW / 2;
        listW = totalW - RIGHT_W - 8;
        rightX = left + listW + 8;
        top = Math.max(24, height / 2 - 140);
        bottom = Math.min(height - 22, top + 290);

        boolean host = amHost(l);
        boolean live = l != null && l.open;
        int rw = RIGHT_W - 8, rx = rightX + 4;

        // --- right column: settings (host), seat buttons, start ---
        int ry = top + 4;
        if (live) {
            CycleButton<Integer> map = addRenderableWidget(CycleButton.<Integer>builder(i -> Component.literal(SkirmishSetupScreen.ARENAS.get(i)))
                .withValues(SkirmishSetupScreen.range(5)).withInitialValue(clamp(l.arena, 5))
                .create(rx, ry, rw, 18, Component.literal("Map"), (b, v) -> send(MultiplayerLobby.Action.SET_ARENA, 0, v)));
            map.active = host && !l.starting;
            ry += 20;
            CycleButton<Integer> metal = addRenderableWidget(CycleButton.<Integer>builder(i -> Component.literal(SkirmishSetupScreen.METALS.get(i)))
                .withValues(SkirmishSetupScreen.range(4)).withInitialValue(clamp(l.metal, 4))
                .create(rx, ry, rw, 18, Component.literal("Metal"), (b, v) -> send(MultiplayerLobby.Action.SET_METAL, 0, v)));
            metal.active = host && !l.starting;
            ry += 20;
            CycleButton<Integer> spawns = addRenderableWidget(CycleButton.<Integer>builder(i -> Component.literal(SkirmishSetupScreen.DISTANCES.get(i)))
                .withValues(SkirmishSetupScreen.range(3)).withInitialValue(clamp(l.spawnDistance, 3))
                .create(rx, ry, rw, 18, Component.literal("Spawns"), (b, v) -> send(MultiplayerLobby.Action.SET_SPAWNS, 0, v)));
            spawns.active = host && !l.starting;
            ry += 20;
            CycleButton<Integer> paceBtn = addRenderableWidget(CycleButton.<Integer>builder(i -> Component.literal(SkirmishSetupScreen.PACES.get(i)))
                .withValues(SkirmishSetupScreen.range(3)).withInitialValue(clamp(l.pace, 3))
                .withTooltip(i -> Tooltip.create(Component.literal(
                    com.solegendary.reignofnether.resources.EconomyPace.describe(i))))
                .create(rx, ry, rw, 18, Component.literal("Pace"), (b, v) -> send(MultiplayerLobby.Action.SET_PACE, 0, v)));
            paceBtn.active = host && !l.starting;
            ry += 24;
            if (host) {
                int half = (rw - 2) / 2;
                for (int t = 0; t < 2; t++) {
                    final int team = t;
                    Button add = addRenderableWidget(Button.builder(Component.literal("+AI T" + (t + 1)), b -> {
                        send(MultiplayerLobby.Action.ADD_BOT, 0, team);
                        LobbySounds.join();
                    }).bounds(rx + t * (half + 2), ry, half, 18).build());
                    add.active = !l.starting && l.seats.size() < MultiplayerLobby.MAX_SEATS;
                    add.setTooltip(Tooltip.create(Component.literal("Add an AI to Team " + (t + 1))));
                }
                ry += 20;
            }
            MultiplayerLobby.Seat me = mySeat(l);
            if (me != null) {
                if (!host && !me.spectating()) {
                    Button ready = addRenderableWidget(Button.builder(Component.literal(me.ready ? "Not Ready" : "Ready"), b -> {
                        send(MultiplayerLobby.Action.SET_READY, me.id, me.ready ? 0 : 1);
                        LobbySounds.ready(!me.ready);
                    }).bounds(rx, ry, rw, 18).build());
                    ready.active = !l.starting;
                    ry += 20;
                }
                Button watch = addRenderableWidget(Button.builder(Component.literal(me.spectating() ? "Play" : "Spectate"), b -> {
                    send(MultiplayerLobby.Action.SET_TEAM, me.id, me.spectating() ? 0 : MultiplayerLobby.SPECTATE);
                    LobbySounds.leave();
                }).bounds(rx, ry, rw, 18).build());
                watch.active = !l.starting;
                watch.setTooltip(Tooltip.create(Component.literal(me.spectating()
                    ? "Take a seat on a team again" : "Watch the match instead of playing (fog off, no base)")));
                ry += 20;
                if (!me.spectating()) {
                    // hero mode (design/hero_mode.md): no base of your own - you play your team's hero
                    Button role = addRenderableWidget(Button.builder(Component.literal(me.heroRole ? "Role: Hero" : "Role: Commander"), b -> {
                        send(MultiplayerLobby.Action.SET_ROLE, me.id, me.heroRole ? 0 : 1);
                        LobbySounds.join();
                    }).bounds(rx, ry, rw, 18).build());
                    role.active = !l.starting;
                    role.setTooltip(Tooltip.create(Component.literal(me.heroRole
                        ? "Hero: no base of your own. Take control of your team's hero (Backspace) and fight in first person."
                        : "Commander: your own base and army. Click to play your team's Hero instead.")));
                    ry += 20;
                }
            }
            if (host && Minecraft.getInstance().hasSingleplayerServer()
                    && !Minecraft.getInstance().getSingleplayerServer().isPublished()) {
                Button lan = addRenderableWidget(Button.builder(Component.literal("Open to LAN"),
                    b -> Minecraft.getInstance().setScreen(new ShareToLanScreen(this))).bounds(rx, ry, rw, 18).build());
                lan.setTooltip(Tooltip.create(Component.literal("Let friends join: with the e4mc mod the public address appears in chat. Leave Allow Cheats OFF.")));
                ry += 20;
            }
        }
        if (host && live) {
            Button start = addRenderableWidget(Button.builder(Component.literal(l.starting ? "Starting..." : "Start Battle"), b -> {
                send(MultiplayerLobby.Action.START, 0, 0);
            }).bounds(rx, bottom - 44, rw, 18).build());
            String why = clientWhyNot(l);
            start.active = !l.starting && why == null;
            if (why != null)
                start.setTooltip(Tooltip.create(Component.literal(why)));
            Button closeLobby = addRenderableWidget(Button.builder(Component.literal("Close Lobby"), b -> {
                send(MultiplayerLobby.Action.CLOSE, 0, 0);
                onClose();
            }).bounds(rx, bottom - 22, (rw - 2) / 2, 18).build());
            closeLobby.active = !l.starting;
            addRenderableWidget(Button.builder(Component.literal("Hide"), b -> onClose())
                .bounds(rx + (rw - 2) / 2 + 2, bottom - 22, (rw - 2) / 2, 18).build());
        } else {
            addRenderableWidget(Button.builder(Component.literal("Hide"), b -> onClose())
                .bounds(rx, bottom - 22, rw, 18).build());
        }

        // --- the seats, by team ---
        if (!live)
            return;
        int y = top + 4;
        int x = left + 4, w = listW - 8;
        for (int t = 0; t < MultiplayerLobby.TEAMS; t++) {
            final int team = t;
            List<MultiplayerLobby.Seat> members = l.seats.stream().filter(s -> s.team == team).toList();
            if (members.isEmpty() && t >= 2)
                continue;
            rows.add(new Object[]{ y, null, "Team " + (t + 1) + "   " + members.size() + "/" + MultiplayerLobby.MAX_PER_TEAM });
            y += 14;
            for (MultiplayerLobby.Seat s : members) {
                y = buildRow(l, s, x, y, w);
            }
            y += 2;
        }
        List<MultiplayerLobby.Seat> watchers = l.seats.stream().filter(MultiplayerLobby.Seat::spectating).toList();
        if (!watchers.isEmpty()) {
            rows.add(new Object[]{ y, null, "Spectators" });
            y += 14;
            for (MultiplayerLobby.Seat s : watchers)
                y = buildRow(l, s, x, y, w);
        }
    }

    int buildRow(LobbyClientboundPacket l, MultiplayerLobby.Seat s, int x, int y, int w) {
        rows.add(new Object[]{ y, s, "" });
        boolean edit = canEdit(l, s);
        if (!s.spectating())
            swatches.add(new int[]{ x + 4, y + 4, s.id });
        int rightEdge = x + w;
        if (s.bot && amHost(l) && !l.starting) {
            addRenderableWidget(Button.builder(Component.literal("x"), b -> {
                send(MultiplayerLobby.Action.REMOVE_BOT, s.id, 0);
                LobbySounds.leave();
            }).bounds(rightEdge - 14, y, 14, ROW_H - 2).build());
            rightEdge -= 16;
        }
        if (!s.spectating()) {
            // team: a small cycler (T1..T4) on your own row and on bot rows for the host
            Button team = addRenderableWidget(Button.builder(Component.literal("T" + (s.team + 1)), b -> {
                send(MultiplayerLobby.Action.SET_TEAM, s.id, (s.team + 1) % MultiplayerLobby.TEAMS);
                LobbySounds.join();
            }).bounds(rightEdge - 24, y, 24, ROW_H - 2).build());
            team.active = edit;
            team.setTooltip(Tooltip.create(Component.literal("Team " + (s.team + 1) + (edit ? " - click for the next team" : ""))));
            rightEdge -= 26;
            if (s.bot) {
                CycleButton<Integer> diff = addRenderableWidget(CycleButton.<Integer>builder(i -> Component.literal(SkirmishSetupScreen.DIFFICULTIES.get(i)))
                    .withValues(SkirmishSetupScreen.range(3)).withInitialValue(clamp(s.difficulty, 3)).displayOnlyValue()
                    .create(rightEdge - 52, y, 52, ROW_H - 2, Component.literal(""), (b, v) -> send(MultiplayerLobby.Action.SET_DIFFICULTY, s.id, v)));
                diff.active = edit;
                rightEdge -= 54;
            } else {
                rightEdge -= 54;   // the Ready / Host tag is drawn here
            }
            int bw = Math.max(60, Math.min(104, rightEdge - (x + 110)));
            int bx = rightEdge - bw;
            Faction shown = SkirmishSetupScreen.factionForCode(s.faction);
            if (edit) {
                final int by = y;
                addRenderableWidget(Button.builder(Component.literal(MiscUtil.getFactionName(shown)), b -> picker.open(
                    b.getX(), b.getY(), b.getX() + b.getWidth(), b.getY() + b.getHeight(), width, height,
                    FactionPicker.skirmishEntries(), shown, f -> {
                        int c = SkirmishServerboundPacket.codeOf(f);
                        send(MultiplayerLobby.Action.SET_FACTION, s.id, c >= 0 ? c : SkirmishServerboundPacket.RANDOM_CODE);
                        LobbySounds.faction(f);
                    })).bounds(bx, by, bw, ROW_H - 2).build());
            }
            factionIcons.add(new Object[]{ bx, y, shown, edit });
        }
        return y + ROW_H;
    }

    /** The same checks the server makes (MultiplayerLobby.whyNotStartable), to grey the button out with a reason. */
    static String clientWhyNot(LobbyClientboundPacket l) {
        java.util.Set<Integer> teams = new java.util.HashSet<>();
        int playing = 0;
        for (MultiplayerLobby.Seat s : l.seats) {
            if (s.spectating())
                continue;
            if (!s.bot && !s.ready && !s.name.equals(l.hostName))
                return s.name + " is not ready yet";
            if (s.heroRole)
                continue;
            playing++;
            teams.add(s.team);
        }
        for (MultiplayerLobby.Seat s : l.seats)
            if (s.heroRole && !s.spectating() && !teams.contains(s.team))
                return s.name + " is a Hero on a team with no commander";
        if (playing < 2)
            return "Add an AI or wait for a friend to join";
        if (teams.size() < 2)
            return "Put someone on another team";
        return null;
    }

    static int clamp(int v, int n) {
        return Math.max(0, Math.min(n - 1, v));
    }

    static void send(MultiplayerLobby.Action a, int seatId, int value) {
        LobbyServerboundPacket.send(a, seatId, value);
    }

    @Override
    public void tick() {
        // the server's latest lobby replaces this layout whenever it changes (and never while the faction list is open)
        if (builtVersion != LobbyClientEvents.version && !picker.isOpen())
            rebuildWidgets();
    }

    @Override
    public void render(GuiGraphics gg, int mx, int my, float pt) {
        int realMx = mx, realMy = my;
        if (picker.isOpen()) {
            mx = -10000;
            my = -10000;
        }
        LobbyClientboundPacket l = LobbyClientEvents.latest;
        renderBackground(gg);
        gg.fill(left - 4, top - 4, rightX + RIGHT_W + 4, bottom + 4, BG);
        gg.fill(left - 4, top - 5, rightX + RIGHT_W + 4, top - 4, EDGE);
        gg.fill(left - 4, bottom + 4, rightX + RIGHT_W + 4, bottom + 5, EDGE);
        gg.fill(left, top, left + listW, bottom, PANEL);
        gg.fill(rightX, top, rightX + RIGHT_W, bottom, PANEL);
        String title = "MULTIPLAYER LOBBY";
        if (l != null && l.open)
            title += "   host: " + l.hostName;
        gg.drawString(font, title, left, top - 16, GOLD);

        if (l == null || !l.open) {
            gg.drawString(font, "Waiting for the host to open the lobby...", left + 8, top + 10, 0xFFFFFF, false);
            gg.drawString(font, "(host: Esc -> Multiplayer Lobby, or /rts-lobby)", left + 8, top + 24, DIM, false);
        } else {
            int x = left + 4, w = listW - 8;
            for (Object[] r : rows) {
                int y = (Integer) r[0];
                MultiplayerLobby.Seat s = (MultiplayerLobby.Seat) r[1];
                if (s == null) {
                    gg.drawString(font, (String) r[2], x, y + 3, GOLD, false);
                    gg.fill(x, y + 12, x + w, y + 13, EDGE);
                    continue;
                }
                boolean me = !s.bot && s.name.equals(myName());
                gg.fill(x, y, x + w, y + ROW_H - 2, me ? 0x40FFD27A : 0x30000000);
                int sx = x + 4, sy = y + 4;
                if (!s.spectating()) {
                    gg.fill(sx - 1, sy - 1, sx + SWATCH + 1, sy + SWATCH + 1, 0xFF000000);
                    PlayerPalette.Entry e = PlayerPalette.byMapColorId(s.colorMapId);
                    if (e != null) {
                        gg.fill(sx, sy, sx + SWATCH, sy + SWATCH, 0xFF000000 | e.hex());
                    } else {   // any free colour
                        gg.fill(sx, sy, sx + 5, sy + 5, 0xFFA12722);
                        gg.fill(sx + 5, sy, sx + 10, sy + 5, 0xFF35399D);
                        gg.fill(sx, sy + 5, sx + 5, sy + 10, 0xFFF8C627);
                        gg.fill(sx + 5, sy + 5, sx + 10, sy + 10, 0xFF70B919);
                    }
                }
                String name = s.bot ? "AI" : s.name + (me ? " (you)" : "") + (s.heroRole ? " [Hero]" : "");
                gg.drawString(font, name, x + 20, y + 5, 0xFFFFFF, false);
                if (!s.bot && !s.spectating()) {
                    boolean isHost = s.name.equals(l.hostName);
                    String tag = isHost ? "Host" : s.ready ? "Ready" : "Not ready";
                    int col = isHost ? GOLD : s.ready ? 0x6CE26C : 0xE05A5A;
                    int tagX = x + w - 26 - 52;   // the space buildRow keeps free left of the team button
                    gg.drawString(font, tag, tagX, y + 5, col, false);
                }
            }
            String hint = l.starting ? "Starting - the battlefield is being prepared..."
                : amHost(l) ? "Press Start once everyone is ready. Friends join with the address e4mc prints in chat."
                : "Pick your faction, colour and team, then press Ready.";
            gg.drawString(font, hint, left, bottom + 8, DIM, false);
        }

        super.render(gg, mx, my, pt);

        // faction icons left of each faction button; on rows you can't edit, the icon and the name as a label
        for (Object[] fi : factionIcons) {
            int bx = (Integer) fi[0], by = (Integer) fi[1];
            Faction f = (Faction) fi[2];
            FactionPicker.renderIcon(gg, f, bx - 14, by + (ROW_H - 2 - 12) / 2, 12, false);
            if (!(Boolean) fi[3])
                gg.drawString(font, MiscUtil.getFactionName(f), bx, by + 5, 0xD0D0D0, false);
        }
        picker.render(gg, font, realMx, realMy);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (picker.mouseClicked(mx, my, button))
            return true;
        LobbyClientboundPacket l = LobbyClientEvents.latest;
        if (l != null && l.open && !l.starting) {
            for (int[] sw : swatches) {
                if (mx >= sw[0] - 2 && mx < sw[0] + SWATCH + 2 && my >= sw[1] - 2 && my < sw[1] + SWATCH + 2) {
                    MultiplayerLobby.Seat s = null;
                    for (MultiplayerLobby.Seat x : l.seats)
                        if (x.id == sw[2])
                            s = x;
                    if (s == null || !canEdit(l, s)) {
                        LobbySounds.denied();
                        return true;
                    }
                    // left: the next colour nobody else has; right: any free colour (the server picks at start)
                    send(MultiplayerLobby.Action.SET_COLOUR, s.id, button == 1 ? 0 : nextFreeColour(l, s));
                    LobbySounds.colour();
                    return true;
                }
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    static int nextFreeColour(LobbyClientboundPacket l, MultiplayerLobby.Seat s) {
        int start = 0;
        for (int i = 0; i < PlayerPalette.COUNT; i++)
            if (PlayerPalette.ENTRIES.get(i).mapColorId() == s.colorMapId)
                start = i + 1;
        for (int k = 0; k < PlayerPalette.COUNT; k++) {
            int id = PlayerPalette.ENTRIES.get((start + k) % PlayerPalette.COUNT).mapColorId();
            boolean taken = false;
            for (MultiplayerLobby.Seat o : l.seats)
                if (o != s && !o.spectating() && o.colorMapId == id)
                    taken = true;
            if (!taken)
                return id;
        }
        return 0;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (picker.mouseScrolled(mx, my, delta))
            return true;
        return super.mouseScrolled(mx, my, delta);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (picker.keyPressed(keyCode))
            return true;
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        LobbyClientEvents.dismissed = true;
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        // a lobby is a multiplayer screen: it must never pause the integrated server under the other players
        return false;
    }
}
