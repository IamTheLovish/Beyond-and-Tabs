package com.solegendary.reignofnether.matchstart;

import com.solegendary.reignofnether.hud.GothicText;
import com.solegendary.reignofnether.hud.LobbyBackdrop;
import com.solegendary.reignofnether.faction.Faction;
import com.solegendary.reignofnether.player.PlayerPalette;
import com.solegendary.reignofnether.util.MiscUtil;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.ShareToLanScreen;
import net.minecraft.client.tutorial.TutorialSteps;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * The multiplayer lobby screen (MultiplayerLobby on the server, design/multiplayer.md). It only shows the lobby the
 * server last sent (LobbyClientEvents.latest) and sends clicks back; the server decides. Rows are grouped by team:
 * a colour swatch (click: the colour palette, BAR's colour box - colours another side holds are crossed out;
 * right-click: automatic; the host also sets the bots'), the name, the faction (a button on your own row and,
 * for the host, on bot rows), the team, and Ready / the bot's skill. On the right: the map settings and Add AI (the
 * host's), your Ready toggle, Spectate / Play, Start (the host's, once everyone is ready) and Close. Both panels scroll
 * with the mouse wheel when they hold more than fits (LobbyLayout); Start / Close / Hide stay pinned below.
 */
public class MultiplayerLobbyScreen extends Screen {

    static final int BG = 0xE0101216, PANEL = 0xC0181B20, EDGE = 0xFF3A3F46, GOLD = 0xFFD27A, DIM = 0x9A9A9A;
    static final int ROW_H = 18, SWATCH = 10, RIGHT_W = 150;

    int left, top, bottom, listW, rightX;
    int builtVersion = -1;
    final FactionPicker picker = new FactionPicker();
    /** Swatches drawn this layout: x, y, seat id. */
    final List<int[]> swatches = new ArrayList<>();
    /** The colour palette popup and the seat id it picks for (-1 when closed); kept across lobby updates. */
    final ColourPalette palette = new ColourPalette();
    int paletteSeat = -1;
    /** Faction icons beside the faction buttons/labels: x, y, faction, editable, label width. */
    final List<Object[]> factionIcons = new ArrayList<>();
    /** Rows to draw: y, seat (null for a header), header text and the room left for the name. */
    final List<Object[]> rows = new ArrayList<>();
    /** The scrolling widgets of each panel with their unscrolled y; the y values above are unscrolled too. */
    final Map<AbstractWidget, Integer> colWidgets = new IdentityHashMap<>(), listWidgets = new IdentityHashMap<>();
    /** Kept across rebuilds (every lobby update rebuilds the layout), clamped after each one. */
    int colScroll, listScroll;
    int colContentH, listContentH, colViewTop, colViewBottom, listViewTop, listViewBottom;

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

    /** While set, every widget added joins that panel's scrolling set. */
    Map<AbstractWidget, Integer> capture;

    @Override
    protected <T extends net.minecraft.client.gui.components.events.GuiEventListener & net.minecraft.client.gui.components.Renderable & net.minecraft.client.gui.narration.NarratableEntry> T addRenderableWidget(T w) {
        T added = super.addRenderableWidget(w);
        if (capture != null && added instanceof AbstractWidget a)
            capture.put(a, a.getY());
        return added;
    }

    @Override
    protected void init() {
        LobbyClientboundPacket l = LobbyClientEvents.latest;
        builtVersion = LobbyClientEvents.version;
        swatches.clear();
        factionIcons.clear();
        rows.clear();
        colWidgets.clear();
        listWidgets.clear();
        picker.close();
        // vanilla's "Move with W, A, S and D" toast drew over the settings column (Joisco's lobby, Oct 20); the
        // orthoview stops the tutorial too, but players reach the lobby before it
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.tutorialStep != TutorialSteps.NONE) {
            mc.options.tutorialStep = TutorialSteps.NONE;
            mc.getTutorial().stop();
        }
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
        capture = colWidgets;
        if (live) {
            // the battlefield (design/maps.md): random terrain or a pre-made map, and BAR's start-position phase
            int maps = com.solegendary.reignofnether.maps.MapPreset.count();
            // a long map name scrolled half out of the button at a large GUI scale (lobby check, Oct 20): cut it with
            // "..." and give the full name in the tooltip
            CycleButton<Integer> preset = addRenderableWidget(CycleButton.<Integer>builder(i -> Component.literal(LobbyLayout.fit(
                    "Map: " + com.solegendary.reignofnether.maps.MapPreview.nameOf(com.solegendary.reignofnether.maps.MapPreset.byId(i)),
                    rw - 8, font::width)))
                .withValues(SkirmishSetupScreen.range(maps)).withInitialValue(clamp(l.map, maps)).displayOnlyValue()
                .withTooltip(i -> Tooltip.create(Component.literal(com.solegendary.reignofnether.maps.MapPreview.nameOf(com.solegendary.reignofnether.maps.MapPreset.byId(i))
                    + "\n" + com.solegendary.reignofnether.maps.MapPreview.blurbOf(com.solegendary.reignofnether.maps.MapPreset.byId(i)))))
                .create(rx, ry, rw, 18, Component.literal("Map"), (b, v) -> send(MultiplayerLobby.Action.SET_MAP, 0, v)));
            preset.active = host && !l.starting;
            ry += 20;
            CycleButton<Boolean> starts = addRenderableWidget(CycleButton.<Boolean>builder(v -> Component.literal(v ? "Choose" : "Auto"))
                .withValues(true, false).withInitialValue(l.choosePositions)
                .withTooltip(v -> Tooltip.create(Component.literal(v
                    ? "A 45 s phase before the match: everyone clicks inside their team's start box to place their commander"
                    : "Everyone starts at their slot straight away")))
                .create(rx, ry, rw, 18, Component.literal("Starts"), (b, v) -> send(MultiplayerLobby.Action.SET_STARTPOS, 0, v ? 1 : 0)));
            starts.active = host && !l.starting;
            ry += 20;
            CycleButton<Integer> map = addRenderableWidget(CycleButton.<Integer>builder(i -> Component.literal(SkirmishSetupScreen.ARENAS.get(i)))
                .withValues(SkirmishSetupScreen.range(5)).withInitialValue(clamp(l.arena, 5))
                .create(rx, ry, rw, 18, Component.literal("Size"), (b, v) -> send(MultiplayerLobby.Action.SET_ARENA, 0, v)));
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
            ry += 20;
            // the match sun: start time and day/night cycle, one row of two (lovish, Oct 18)
            int halfW = (rw - 2) / 2;
            CycleButton<Integer> time = addRenderableWidget(SkirmishSetupScreen.timeButton(l.timeOfDay, rx, ry, halfW, 18,
                v -> send(MultiplayerLobby.Action.SET_TIME, 0, v)));
            time.active = host && !l.starting;
            CycleButton<Integer> cycle = addRenderableWidget(SkirmishSetupScreen.cycleButton(l.dayCycle, rx + halfW + 2, ry,
                rw - halfW - 2, 18, v -> send(MultiplayerLobby.Action.SET_CYCLE, 0, v)));
            cycle.active = host && !l.starting;
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
                        ? "Hero: no base of your own. Take control of your team's hero ("
                            + com.solegendary.reignofnether.keybinds.Keybindings.toggleCamera.getDisplayName()
                            + ") and fight in first person."
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
        capture = null;
        // the column scrolls above the pinned buttons: Start + Close/Hide for the host, Hide for everyone else
        colViewTop = top + 4;
        colViewBottom = (host && live) ? bottom - 48 : bottom - 26;
        colContentH = ry - colViewTop;
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
        listViewTop = top + 4;
        listViewBottom = bottom - 2;
        listContentH = 0;
        if (!live) {
            applyScroll();
            return;
        }
        capture = listWidgets;
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
        capture = null;
        listContentH = y - listViewTop;
        applyScroll();
    }

    /** Clamps both offsets and moves the panels' widgets; one that is not wholly inside its panel is hidden. */
    void applyScroll() {
        colScroll = LobbyLayout.clampScroll(colScroll, colContentH, colViewBottom - colViewTop);
        listScroll = LobbyLayout.clampScroll(listScroll, listContentH, listViewBottom - listViewTop);
        place(colWidgets, colScroll, colViewTop, colViewBottom);
        place(listWidgets, listScroll, listViewTop, listViewBottom);
    }

    static void place(Map<AbstractWidget, Integer> widgets, int scroll, int viewTop, int viewBottom) {
        for (Map.Entry<AbstractWidget, Integer> e : widgets.entrySet()) {
            AbstractWidget wd = e.getKey();
            wd.setY(e.getValue() - scroll);
            wd.visible = LobbyLayout.inView(wd.getY(), wd.getHeight(), viewTop, viewBottom);
        }
    }

    /** A seat row's screen y, or Integer.MIN_VALUE when it is scrolled out of the list. */
    int rowY(int baseY, int h) {
        int y = baseY - listScroll;
        return LobbyLayout.inView(y, h, listViewTop, listViewBottom) ? y : Integer.MIN_VALUE;
    }

    int buildRow(LobbyClientboundPacket l, MultiplayerLobby.Seat s, int x, int y, int w) {
        Object[] row = new Object[]{ y, s, "", 0 };
        rows.add(row);
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
                    .withValues(SkirmishSetupScreen.range(4)).withInitialValue(clamp(s.difficulty, 4)).displayOnlyValue()
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
            factionIcons.add(new Object[]{ bx, y, shown, edit, bw });
            row[3] = bx - 16 - (x + 20);   // the name ends before the faction icon (drawn at bx - 14)
        } else {
            row[3] = rightEdge - 4 - (x + 20);
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
        LobbyClientboundPacket l = LobbyClientEvents.latest;
        MultiplayerLobby.Seat paletteFor = syncPalette(l);
        if (picker.isOpen() || paletteFor != null) {
            mx = -10000;
            my = -10000;
        }
        // the animated cathedral backdrop (hud/LobbyBackdrop; vanilla background if its art cannot load) and a soft
        // scrim behind the panel, title and hint line so every line of the lobby stays readable over it
        LobbyBackdrop.render(this, gg, realMx, realMy);
        LobbyBackdrop.scrim(gg, left - 10, top - 22, rightX + RIGHT_W + 10, bottom + 20);
        gg.fill(left - 4, top - 4, rightX + RIGHT_W + 4, bottom + 4, BG);
        gg.fill(left - 4, top - 5, rightX + RIGHT_W + 4, top - 4, EDGE);
        gg.fill(left - 4, bottom + 4, rightX + RIGHT_W + 4, bottom + 5, EDGE);
        gg.fill(left, top, left + listW, bottom, PANEL);
        gg.fill(rightX, top, rightX + RIGHT_W, bottom, PANEL);
        int titleW = GothicText.title(gg, font, "Multiplayer Lobby", left, top - 21);
        if (l != null && l.open)
            gg.drawString(font, "host: " + l.hostName, left + titleW + 10, top - 12, GOLD);

        if (l == null || !l.open) {
            gg.drawString(font, "Waiting for the host to open the lobby...", left + 8, top + 10, 0xFFFFFF, false);
            gg.drawString(font, "(host: Esc -> Multiplayer Lobby, or /rts-lobby)", left + 8, top + 24, DIM, false);
        } else {
            int x = left + 4, w = listW - 8;
            for (Object[] r : rows) {
                MultiplayerLobby.Seat s = (MultiplayerLobby.Seat) r[1];
                int y = rowY((Integer) r[0], s == null ? 13 : ROW_H - 2);
                if (y == Integer.MIN_VALUE)
                    continue;
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
                String name = s.bot ? "AI" : LobbyLayout.fitName(s.name, (me ? " (you)" : "") + (s.heroRole ? " [Hero]" : ""),
                    (Integer) r[3], font::width);
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
            gg.drawString(font, LobbyLayout.fit(hint, rightX + RIGHT_W - left, font::width), left, bottom + 8, DIM, false);
            scrollBar(gg, left + listW - 3, listViewTop, listViewBottom, listScroll, listContentH);
            scrollBar(gg, rightX + RIGHT_W - 3, colViewTop, colViewBottom, colScroll, colContentH);
        }

        super.render(gg, mx, my, pt);
        GothicText.restyleButtons(gg, font, children(), GothicText.LOBBY_MAIN, GothicText.LOBBY_PRIMARY, false, 1f);

        // faction icons left of each faction button; on rows you can't edit, the icon and the name as a label
        for (Object[] fi : factionIcons) {
            int bx = (Integer) fi[0], by = rowY((Integer) fi[1], ROW_H - 2);
            if (by == Integer.MIN_VALUE)
                continue;
            Faction f = (Faction) fi[2];
            FactionPicker.renderIcon(gg, f, bx - 14, by + (ROW_H - 2 - 12) / 2, 12, false);
            if (!(Boolean) fi[3])
                gg.drawString(font, LobbyLayout.fit(MiscUtil.getFactionName(f), (Integer) fi[4], font::width),
                    bx, by + 5, 0xD0D0D0, false);
        }
        picker.render(gg, font, realMx, realMy);
        if (paletteFor != null) {
            final MultiplayerLobby.Seat seat = paletteFor;
            final List<MultiplayerLobby.Seat> lineup = l.seats;
            palette.render(gg, font, realMx, realMy, ColourPalette.indexOf(seat.colorMapId),
                i -> MultiplayerLobby.colourTakenIn(lineup, PlayerPalette.ENTRIES.get(i).mapColorId(), seat));
        }
    }

    static MultiplayerLobby.Seat seatById(LobbyClientboundPacket l, int id) {
        if (l == null)
            return null;
        for (MultiplayerLobby.Seat s : l.seats)
            if (s.id == id)
                return s;
        return null;
    }

    /** The colour box of seat {@code seatId} on screen: x, y; null when it has none or it is scrolled out. */
    int[] swatchOnScreen(int seatId) {
        for (int[] sw : swatches) {
            if (sw[2] != seatId)
                continue;
            int swY = rowY(sw[1] - 4, ROW_H - 2);
            return swY == Integer.MIN_VALUE ? null : new int[]{ sw[0], swY + 4 };
        }
        return null;
    }

    /**
     * The seat the open palette picks for, with the palette kept beside its colour box (every lobby update rebuilds
     * the rows); closes it - null - when that seat left, may no longer be edited (Start was pressed) or scrolled out.
     */
    MultiplayerLobby.Seat syncPalette(LobbyClientboundPacket l) {
        if (paletteSeat < 0)
            return null;
        MultiplayerLobby.Seat s = seatById(l, paletteSeat);
        int[] at = s == null || !l.open || !canEdit(l, s) || s.spectating() ? null : swatchOnScreen(s.id);
        if (at == null) {
            closePalette();
            return null;
        }
        palette.openBeside(at[0], at[1], SWATCH, width, height);
        return s;
    }

    void closePalette() {
        paletteSeat = -1;
        palette.close();
    }

    /**
     * Opens the palette on the colour box of drawn row {@code index} (0 = the first row with a box), as a click on it
     * would: the screenshot tours use it. False when there is no such row or it is not yours to edit.
     */
    public boolean openPaletteOnRow(int index) {
        LobbyClientboundPacket l = LobbyClientEvents.latest;
        if (index < 0 || index >= swatches.size())
            return false;
        MultiplayerLobby.Seat s = seatById(l, swatches.get(index)[2]);
        if (s == null || !canEdit(l, s))
            return false;
        paletteSeat = s.id;
        return syncPalette(l) != null;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (picker.mouseClicked(mx, my, button))
            return true;
        LobbyClientboundPacket l = LobbyClientEvents.latest;
        MultiplayerLobby.Seat paletteFor = syncPalette(l);
        if (paletteFor != null) {
            if (button == 1) {   // right-click anywhere while it is open: automatic colour (the server picks at start)
                send(MultiplayerLobby.Action.SET_COLOUR, paletteFor.id, 0);
                closePalette();
                LobbySounds.colour();
                return true;
            }
            int i = palette.hit(mx, my);
            if (i >= 0) {
                int id = PlayerPalette.ENTRIES.get(i).mapColorId();
                // another side holds it: refused here as on the server (two sides never share a colour)
                if (id != paletteFor.colorMapId && MultiplayerLobby.colourTakenIn(l.seats, id, paletteFor)) {
                    LobbySounds.denied();
                    return true;
                }
                send(MultiplayerLobby.Action.SET_COLOUR, paletteFor.id, id);
                closePalette();
                LobbySounds.colour();
                return true;
            }
            if (!palette.contains(mx, my)) {
                closePalette();   // clicked outside: close
                LobbySounds.close();
            }
            return true;
        }
        if (l != null && l.open && !l.starting) {
            for (int[] sw : swatches) {
                int[] at = swatchOnScreen(sw[2]);
                if (at == null)
                    continue;
                if (mx >= at[0] - 2 && mx < at[0] + SWATCH + 2 && my >= at[1] - 2 && my < at[1] + SWATCH + 2) {
                    MultiplayerLobby.Seat s = seatById(l, sw[2]);
                    if (s == null || !canEdit(l, s)) {
                        LobbySounds.denied();
                        return true;
                    }
                    if (button == 1) {   // right-click: automatic colour, as before
                        send(MultiplayerLobby.Action.SET_COLOUR, s.id, 0);
                        LobbySounds.colour();
                        return true;
                    }
                    paletteSeat = s.id;
                    palette.openBeside(at[0], at[1], SWATCH, width, height);
                    LobbySounds.open();
                    return true;
                }
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (picker.mouseScrolled(mx, my, delta))
            return true;
        if (paletteSeat >= 0)
            return true;   // the list holds still while the palette is open beside a row
        // the wheel scrolls the panel under the pointer, one button (column) or one seat row (list) per notch
        if (mx >= rightX && mx < rightX + RIGHT_W && colContentH > colViewBottom - colViewTop) {
            colScroll -= (int) Math.signum(delta) * 20;
            applyScroll();
            return true;
        }
        if (mx >= left && mx < left + listW && listContentH > listViewBottom - listViewTop) {
            listScroll -= (int) Math.signum(delta) * ROW_H;
            applyScroll();
            return true;
        }
        return super.mouseScrolled(mx, my, delta);
    }

    /** A thin bar along a panel's right edge, only when that panel scrolls. */
    static void scrollBar(GuiGraphics gg, int x, int viewTop, int viewBottom, int scroll, int contentH) {
        int viewH = viewBottom - viewTop;
        if (contentH <= viewH)
            return;
        int barH = Math.max(12, viewH * viewH / contentH);
        int barY = viewTop + (viewH - barH) * scroll / (contentH - viewH);
        gg.fill(x, viewTop, x + 2, viewBottom, 0x40FFFFFF);
        gg.fill(x, barY, x + 2, barY + barH, 0xC0FFD27A);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (picker.keyPressed(keyCode))
            return true;
        if (paletteSeat >= 0 && keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
            closePalette();   // Escape closes the palette, not the lobby
            LobbySounds.close();
            return true;
        }
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
