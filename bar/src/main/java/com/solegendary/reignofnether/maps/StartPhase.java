package com.solegendary.reignofnether.maps;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.resources.MetalPatches;
import com.solegendary.reignofnether.startpos.CapturePointServerEvents;
import com.solegendary.reignofnether.startpos.StartAreaClearing;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * BAR's start-position mode (design/maps.md section 5). Every human clicks inside their team's start box to place
 * their commander; the spot snaps clear of metal patches and capture sites and can be moved until the phase ends.
 * Bots are placed at their slot at once. Simultaneous rather than turn order (16 players taking turns would cost
 * minutes, and every slot owns its own metal so stacking only hurts the staller).
 * <p>
 * Ready (tester Joisco, Oct 26: "there should be a ready button that all players should have to press when placing
 * down their spawn location"; BAR does the same): placing no longer starts anything. Each human places, then presses
 * Ready ({@link #setReady}, refused while unplaced); a ready human must un-ready to move. Bots are always ready. The
 * phase ends {@link #EARLY_END_TICKS} after every human still in it is placed and ready - an un-ready during those
 * ticks stops it again. A human who logs out drops out of the count ({@link #leave}; everyone is told) and is back
 * in it, unready, on a rejoin. The host can {@link #forceStart} (the client warns first, naming who is not ready).
 * The clock stays as the AFK guard, now {@link #SECONDS} long: when it runs out, everyone still unplaced goes to
 * their slot and the match starts, with a chat line naming who was not ready.
 *
 * An instance holds one phase; {@link #begin} makes it the live one that the server ticks and syncs to clients
 * (MapStateClientboundPacket). The game test drives its own instance, so it never touches the live phase.
 * <p>
 * Saved with the world (code_review CR-13, through match.MatchState): {@link #save} / {@link #load} keep every
 * spot, who placed and the clock. A phase loaded from a save is <i>held</i> - its clock waits until one of its humans
 * is online again (at server start nobody is: a reloading player must not be auto-placed while still loading in),
 * then runs on with at least {@link #RESUME_FLOOR_SECONDS} - and it ends early only once every human is back (on a
 * dedicated server players return one by one). AoE2 restores a saved multiplayer game the same way: it waits for the
 * players to come back before it runs.
 */
public final class StartPhase {

    /** The AFK guard (was 45 s before Ready existed: a ready check needs time for a team to talk its starts over). */
    public static final int SECONDS = 120;
    public static final int EARLY_END_TICKS = 60;
    /** A resumed phase runs at least this long once a human is back, whatever was left at the save. */
    public static final int RESUME_FLOOR_SECONDS = 20;
    /** How far a blocked click may be moved to find room for the capitol. */
    static final int MAX_SNAP = 14;

    /** One side placing a start. {@code seat} is whatever the caller needs back at the end (a lobby seat). */
    public static final class Pick {
        public final String name;
        public final boolean bot;
        public final int team;
        public final int slot;
        public final int colourHex;
        public final Object seat;
        public int x, z;
        public boolean placed;
        /** Pressed Ready (bots always are). Not saved: a reloaded phase asks every human again. */
        public boolean ready;
        /** Logged out during the phase: out of the ready count until they log back in. */
        public boolean left;

        public Pick(String name, boolean bot, int team, int slot, int colourHex, Object seat) {
            this.name = name;
            this.bot = bot;
            this.team = team;
            this.slot = slot;
            this.colourHex = colourHex;
            this.seat = seat;
            this.ready = bot;
        }

        /** Placed and ready, or a bot, or gone: nothing to wait for. */
        public boolean isSettled() {
            return bot || left || (placed && ready);
        }
    }

    public final ServerLevel level;
    public final int cx, cz;
    public final MapLayout layout;
    public final List<Pick> picks;
    private final Consumer<StartPhase> onDone;
    int ticksLeft;
    int earlyEnd = -1;
    boolean finished = false;
    // restored from a save: the clock is held until a human is online, and an early end needs every human back
    boolean resumed = false;
    boolean holding = false;
    /** Who was not ready when the clock ran out (told in chat by the live phase); empty otherwise. */
    List<String> timedOutWaitingFor = List.of();
    private Predicate<String> online = null;   // null: the server's player list (the game tests pass their own)

    public StartPhase(ServerLevel level, int cx, int cz, MapLayout layout, List<Pick> picks, int seconds,
                      Consumer<StartPhase> onDone) {
        this.level = level;
        this.cx = cx;
        this.cz = cz;
        this.layout = layout;
        this.picks = picks;
        this.onDone = onDone;
        this.ticksLeft = Math.max(1, seconds) * 20;
        for (Pick p : picks) {
            MapLayout.Slot s = layout.slots.get(Math.min(p.slot, layout.slots.size() - 1));
            p.x = cx + s.dx();
            p.z = cz + s.dz();
            p.placed = p.bot;   // bots take their slot straight away
        }
    }

    public boolean isFinished() {
        return finished;
    }

    public int secondsLeft() {
        return (ticksLeft + 19) / 20;
    }

    public int ticksLeft() {
        return ticksLeft;
    }

    /** Restored from a save and still waiting for a human to come back: the clock doesn't run. */
    public boolean isHolding() {
        return holding;
    }

    public boolean isResumed() {
        return resumed;
    }

    /** Who counts as online for a resumed phase (game tests); null puts back the server's player list. */
    public void setOnlineCheck(Predicate<String> online) {
        this.online = online;
    }

    boolean isOnline(String name) {
        if (online != null)
            return online.test(name);
        return level.getServer() != null && level.getServer().getPlayerList().getPlayerByName(name) != null;
    }

    private boolean anyHumanOnline() {
        for (Pick p : picks)
            if (!p.bot && isOnline(p.name))
                return true;
        return false;
    }

    public Pick pickOf(String name) {
        for (Pick p : picks)
            if (p.name.equals(name))
                return p;
        return null;
    }

    /**
     * Whether (x, z) is inside {@code team}'s start box, on ground a capitol can stand on (not the void of the End
     * maps, a lava lake or open sea: MapLayout.startGround).
     */
    public boolean inBox(int team, int x, int z) {
        MapLayout.Box b = layout.boxOf(team);
        return b != null && b.contains(x + 0.5 - cx, z + 0.5 - cz) && layout.startGround(x + 0.5 - cx, z + 0.5 - cz);
    }

    /**
     * A human's click: accepted (and snapped clear of patches and sites, staying inside the box) when it is inside
     * their team's box. Returns whether it was accepted.
     */
    public boolean place(String name, int x, int z) {
        Pick p = pickOf(name);
        if (finished || p == null || p.bot || p.ready || !inBox(p.team, x, z))
            return false;   // a ready human un-readies first to move (their start is what the others agreed to)
        int[] spot = clearSpot(p.team, x, z);
        if (spot == null)
            return false;
        p.x = spot[0];
        p.z = spot[1];
        p.placed = true;
        return true;
    }

    /**
     * A human presses Ready ({@code ready}) or un-readies. Ready is refused until they placed; un-ready always works
     * and stops a countdown that everyone-ready began. Returns whether it changed anything.
     */
    public boolean setReady(String name, boolean ready) {
        Pick p = pickOf(name);
        if (finished || p == null || p.bot || p.left || p.ready == ready)
            return false;
        if (ready && !p.placed)
            return false;
        p.ready = ready;
        return true;
    }

    /** Every human still in the phase has placed and pressed Ready (bots always have; one who left is out). */
    public boolean allReady() {
        for (Pick p : picks)
            if (!p.isSettled())
                return false;
        return true;
    }

    /** The humans the phase is waiting for, in lineup order. */
    public List<String> notReady() {
        List<String> names = new ArrayList<>();
        for (Pick p : picks)
            if (!p.isSettled())
                names.add(p.name);
        return names;
    }

    /** A human logged out: out of the ready count (and unready). False when they have no pick or already left. */
    public boolean leave(String name) {
        Pick p = pickOf(name);
        if (finished || p == null || p.bot || p.left)
            return false;
        p.left = true;
        p.ready = false;
        return true;
    }

    /** A human who left logged back in: back in the count, unready. */
    public boolean rejoin(String name) {
        Pick p = pickOf(name);
        if (finished || p == null || p.bot || !p.left)
            return false;
        p.left = false;
        p.ready = false;
        return true;
    }

    /**
     * The host's override: ends the phase now whoever is not ready (they start where they stand, or at their slot if
     * unplaced). Refused for anyone but the host ({@code byHost}: PacketAuth.isHost). Returns whether it started.
     */
    public boolean forceStart(boolean byHost) {
        if (finished || !byHost)
            return false;
        finish();
        return true;
    }

    /** (x, z) or the nearest spot in the box whose capitol square is clear of patches and capture sites. */
    int[] clearSpot(int team, int x, int z) {
        int half = (StartAreaClearing.capitolSpan(level) - 1) / 2;
        List<BlockPos> patches;
        List<BlockPos> live = MetalPatches.getPatches(level);
        synchronized (live) {
            patches = new ArrayList<>(live);
        }
        if (clear(x, z, half, patches))
            return new int[] { x, z };
        for (int r = 1; r <= MAX_SNAP; r++)
            for (int i = 0; i < 8 * r; i++) {
                double a = Math.PI * 2 * i / (8 * r);
                int nx = x + (int) Math.round(Math.cos(a) * r), nz = z + (int) Math.round(Math.sin(a) * r);
                if (inBox(team, nx, nz) && clear(nx, nz, half, patches))
                    return new int[] { nx, nz };
            }
        return null;
    }

    private boolean clear(int x, int z, int half, List<BlockPos> patches) {
        int r = MetalPatches.RESERVED_HALF;
        for (BlockPos c : patches)
            if (Math.abs(c.getX() - x) <= half + r && Math.abs(c.getZ() - z) <= half + r)
                return false;
        for (Entity e : CapturePointServerEvents.getPoints())
            if (e.level() == level && Math.abs(e.getBlockX() - x) <= half + 3 && Math.abs(e.getBlockZ() - z) <= half + 3)
                return false;
        return true;
    }

    /**
     * Bot-only hook (design/bot.md "Start position"): every bot moves from its slot to the spot BotStartPick likes
     * best inside its box - near the richest metal - snapped clear like a human's click. Only in the live phase
     * ({@link #begin}): with Start positions: Auto every side keeps its slot, bots included.
     */
    public void placeBots() {
        List<BlockPos> patches;
        List<BlockPos> live = MetalPatches.getPatches(level);
        synchronized (live) {
            patches = new ArrayList<>(live);
        }
        if (patches.isEmpty())
            return;   // random terrain stamps its patches after the phase: nothing to choose by
        int half = (StartAreaClearing.capitolSpan(level) - 1) / 2;
        for (Pick p : picks) {
            if (!p.bot)
                continue;
            int[] want = com.solegendary.reignofnether.bot.BotStartPick.choose(this, p, patches, MetalPatches::valueOf, half);
            int[] spot = want == null ? null : clearSpot(p.team, want[0], want[1]);
            if (spot != null) {
                p.x = spot[0];
                p.z = spot[1];
            }
        }
    }

    /** One server tick. Public for the game test. */
    public void tick() {
        if (finished)
            return;
        if (holding) {
            if (!anyHumanOnline())
                return;   // nobody can place while nobody is here: the clock waits
            holding = false;
            ticksLeft = Math.max(ticksLeft, RESUME_FLOOR_SECONDS * 20);
            earlyEnd = -1;
        }
        ticksLeft--;
        // after a reload the ready flags start cleared, so "everyone ready" also means everyone came back
        boolean ready = allReady();
        if (!ready)
            earlyEnd = -1;   // an un-ready (or a rejoin) during the last ticks stops the start again
        else if (earlyEnd < 0)
            earlyEnd = EARLY_END_TICKS;
        if (earlyEnd >= 0 && --earlyEnd <= 0) {
            finish();
        } else if (ticksLeft <= 0) {
            timedOutWaitingFor = notReady();
            finish();
        }
    }

    /** Ends the phase now: anyone unplaced takes their slot, then the match starts. Public for the game test. */
    public void finish() {
        if (finished)
            return;
        finished = true;
        for (Pick p : picks)
            if (!p.placed) {
                MapLayout.Slot s = layout.slots.get(Math.min(p.slot, layout.slots.size() - 1));
                p.x = cx + s.dx();
                p.z = cz + s.dz();
                p.placed = true;
            }
        if (onDone != null)
            onDone.accept(this);
    }

    // ------------------------------------------------------------------------------------------ save / load

    /** Every pick's spot and whether it was placed, and the clock (match.MatchState saves it with the world). */
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("ticksLeft", ticksLeft);
        ListTag list = new ListTag();
        for (Pick p : picks) {
            CompoundTag pt = new CompoundTag();
            pt.putString("name", p.name);
            pt.putInt("slot", p.slot);
            pt.putInt("x", p.x);
            pt.putInt("z", p.z);
            pt.putBoolean("placed", p.placed);
            list.add(pt);
        }
        tag.put("picks", list);
        return tag;
    }

    /**
     * Puts a {@link #save} of the same lineup back into this fresh phase and holds it until a human is online (class
     * comment). A spot that fell outside its box (a changed layout) stays at the slot, unplaced. False, and nothing
     * changed, when the save is of other sides.
     */
    public boolean load(CompoundTag tag) {
        if (tag == null)
            return false;
        ListTag list = tag.getList("picks", Tag.TAG_COMPOUND);
        if (list.size() != picks.size())
            return false;
        for (int i = 0; i < picks.size(); i++) {
            CompoundTag pt = list.getCompound(i);
            if (picks.get(i).slot != pt.getInt("slot") || !picks.get(i).name.equals(pt.getString("name")))
                return false;
        }
        for (int i = 0; i < picks.size(); i++) {
            CompoundTag pt = list.getCompound(i);
            Pick p = picks.get(i);
            int x = pt.getInt("x"), z = pt.getInt("z");
            if (inBox(p.team, x, z)) {
                p.x = x;
                p.z = z;
                p.placed = p.bot || pt.getBoolean("placed");
            }
        }
        ticksLeft = Math.max(1, Math.min(SECONDS * 20, tag.getInt("ticksLeft")));
        earlyEnd = -1;
        resumed = true;
        holding = true;
        return true;
    }

    // ------------------------------------------------------------------------------------------ the live phase

    private static StartPhase live = null;
    private static int syncTicks = 0;

    public static StartPhase live() {
        return live;
    }

    /** /rts-reset: drop the live phase without seating anyone. */
    public static void cancel() {
        if (live != null) {
            live.finished = true;
            live = null;
        }
    }

    /**
     * A phase restored from a save becomes the live one as it was: no bot re-pick (their saved spots stand) and no
     * camera moves (nobody is online at server start; MatchServerEvents moves each human's camera as they log in).
     */
    public static void resume(StartPhase phase) {
        live = phase;
        syncTicks = 0;
        ReignOfNether.LOGGER.info("[StartPhase] resumed: {} sides, {} s left, held until a player is back",
            phase.picks.size(), phase.secondsLeft());
        MapStateClientboundPacket.broadcast();
    }

    public static void begin(StartPhase phase) {
        live = phase;
        syncTicks = 0;
        try {
            phase.placeBots();
        } catch (Exception e) {
            ReignOfNether.LOGGER.error("[StartPhase] bot start picks failed, bots keep their slots", e);
        }
        ReignOfNether.LOGGER.info("[StartPhase] {} sides placing their starts ({} s)", phase.picks.size(), phase.secondsLeft());
        // every human's camera goes to their own slot first, BAR-style
        for (Pick p : phase.picks)
            if (!p.bot)
                com.solegendary.reignofnether.player.PlayerClientboundPacket.teleport(p.name,
                    new BlockPos(p.x, MetalPatches.solidTop(phase.level, p.x, p.z), p.z));
        MapStateClientboundPacket.broadcast();
    }

    /** A click from a client (StartPhaseServerboundPacket). */
    public static void handlePlace(ServerPlayer sp, int x, int z) {
        StartPhase phase = live;
        if (phase == null || sp == null)
            return;
        // each place runs clearSpot (up to MAX_SNAP rings, every patch and site per ring) and a success broadcasts
        // the map state to everyone: a client spamming clicks must not turn that into a server-wide storm (CR-05)
        if (!com.solegendary.reignofnether.network.PacketAuth.allow(sp, "startpos", 8, 16))
            return;
        if (phase.place(sp.getName().getString(), x, z))
            MapStateClientboundPacket.broadcast();
    }

    /** Ready / un-ready from a client (StartPhaseServerboundPacket). Refusals are told to the sender, never silent. */
    public static void handleReady(ServerPlayer sp, boolean ready) {
        StartPhase phase = live;
        if (phase == null || sp == null)
            return;
        if (!com.solegendary.reignofnether.network.PacketAuth.allow(sp, "startpos_ready", 4, 8))
            return;
        String name = sp.getName().getString();
        Pick p = phase.pickOf(name);
        if (phase.setReady(name, ready)) {
            MapStateClientboundPacket.broadcast();
        } else if (ready && p != null && !p.bot && !p.placed) {
            sp.sendSystemMessage(Component.translatable("maps.reignofnether.startphase_ready_needs_place"));
        }
    }

    /**
     * Force start from a client: the host only (PacketAuth.isHost - the singleplayer owner, or an op on a dedicated
     * server). Everyone is told who forced it and who was not ready. Returns whether it started (the game test).
     */
    public static boolean handleForceStart(StartPhase phase, ServerPlayer sp) {
        if (phase == null || sp == null || phase.isFinished())
            return false;
        boolean host = com.solegendary.reignofnether.network.PacketAuth.isHost(sp);
        if (!host) {
            sp.sendSystemMessage(Component.translatable("maps.reignofnether.startphase_force_refused"));
            return false;
        }
        List<String> waiting = phase.notReady();
        if (!phase.forceStart(true))
            return false;
        String by = sp.getName().getString();
        ReignOfNether.LOGGER.info("[StartPhase] {} forced the start; not ready: {}", by, waiting);
        tellAll(phase, waiting.isEmpty()
            ? Component.translatable("maps.reignofnether.startphase_forced_all_ready", by)
            : Component.translatable("maps.reignofnether.startphase_forced", by, String.join(", ", waiting)));
        return true;
    }

    private static void tellAll(StartPhase phase, Component msg) {
        if (phase.level.getServer() == null)
            return;
        for (ServerPlayer sp : phase.level.getServer().getPlayerList().getPlayers())
            sp.sendSystemMessage(msg);
    }

    /** A player left during the live phase: out of the ready count, and everyone is told (fail loud). */
    @SubscribeEvent
    public static void onPlayerLeave(PlayerEvent.PlayerLoggedOutEvent evt) {
        StartPhase phase = live;
        if (phase == null || !(evt.getEntity() instanceof ServerPlayer sp))
            return;
        String name = sp.getName().getString();
        if (!phase.leave(name))
            return;
        ReignOfNether.LOGGER.info("[StartPhase] {} left during start positions; out of the ready count", name);
        tellAll(phase, Component.translatable("maps.reignofnether.startphase_left", name));
        MapStateClientboundPacket.broadcast();
    }

    @SubscribeEvent
    public static void onPlayerBack(PlayerEvent.PlayerLoggedInEvent evt) {
        StartPhase phase = live;
        if (phase == null || !(evt.getEntity() instanceof ServerPlayer sp))
            return;
        String name = sp.getName().getString();
        if (!phase.rejoin(name))
            return;
        tellAll(phase, Component.translatable("maps.reignofnether.startphase_rejoined", name));
        MapStateClientboundPacket.broadcast();
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent evt) {
        if (evt.phase != TickEvent.Phase.END || live == null)
            return;
        StartPhase phase = live;
        try {
            phase.tick();
        } catch (Exception e) {
            ReignOfNether.LOGGER.error("[StartPhase] tick failed, starting the match", e);
            phase.finish();
        }
        if (phase.finished) {
            if (!phase.timedOutWaitingFor.isEmpty())
                tellAll(phase, Component.translatable("maps.reignofnether.startphase_timed_out",
                    String.join(", ", phase.timedOutWaitingFor)));
            if (live == phase)
                live = null;
            MapStateClientboundPacket.broadcast();
            return;
        }
        if (++syncTicks >= 20) {   // the countdown, once a second
            syncTicks = 0;
            MapStateClientboundPacket.broadcast();
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent evt) {
        live = null;
    }
}
