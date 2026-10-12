package com.solegendary.reignofnether.matchstart;

import com.solegendary.reignofnether.player.PlayerServerEvents;
import com.solegendary.reignofnether.registrars.PacketHandler;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * The multiplayer lobby, server to client (MultiplayerLobby):
 * <ul>
 *   <li>SNAPSHOT - the whole lobby (open or closed, host, map settings, every seat). Sent to everyone after any
 *       change, at most every other tick: 16 seats are well under half a kilobyte, so a full copy is simpler and as
 *       cheap as deltas. A closed snapshot closes the lobby screens.</li>
 *   <li>START - Start was pressed: enter the RTS view now, the bases appear in two seconds ({@code spectating}
 *       when this player watches).</li>
 *   <li>SPECTATE - you joined a match already being played: watch it (RTS view, observer HUD).</li>
 * </ul>
 */
public class LobbyClientboundPacket {

    public enum Kind { SNAPSHOT, START, SPECTATE }

    public final Kind kind;
    public final boolean open;
    public final boolean starting;
    public final String hostName;
    public final int arena, metal, spawnDistance;
    public final int pace;   // economy pace code (EconomyPace)
    public final boolean spectating;
    public final List<MultiplayerLobby.Seat> seats;

    LobbyClientboundPacket(Kind kind, boolean open, boolean starting, String hostName, int arena, int metal,
                           int spawnDistance, int pace, boolean spectating, List<MultiplayerLobby.Seat> seats) {
        this.kind = kind;
        this.open = open;
        this.starting = starting;
        this.hostName = hostName;
        this.arena = arena;
        this.metal = metal;
        this.spawnDistance = spawnDistance;
        this.pace = pace;
        this.spectating = spectating;
        this.seats = seats;
    }

    static LobbyClientboundPacket snapshot() {
        return new LobbyClientboundPacket(Kind.SNAPSHOT, MultiplayerLobby.open, MultiplayerLobby.startCountdown >= 0,
            MultiplayerLobby.hostName, MultiplayerLobby.arena, MultiplayerLobby.metal, MultiplayerLobby.spawnDistance,
            MultiplayerLobby.pace, false, new ArrayList<>(MultiplayerLobby.seats));
    }

    static void broadcastSnapshot() {
        LobbyClientboundPacket p = snapshot();
        for (ServerPlayer sp : PlayerServerEvents.players)
            send(sp, p);
    }

    static void sendSnapshot(ServerPlayer sp) {
        send(sp, snapshot());
    }

    /** Start pressed: every lobby human (spectators too) turns on the RTS view. */
    static void sendStart(List<MultiplayerLobby.Seat> seats) {
        for (ServerPlayer sp : PlayerServerEvents.players) {
            boolean watching = true;
            for (MultiplayerLobby.Seat s : seats)
                if (!s.bot && sp.getUUID().equals(s.uuid))
                    watching = s.spectating();
            send(sp, new LobbyClientboundPacket(Kind.START, false, true, "", 0, 0, 0, 1, watching, List.of()));
        }
    }

    static void sendSpectate(ServerPlayer sp) {
        send(sp, new LobbyClientboundPacket(Kind.SPECTATE, false, false, "", 0, 0, 0, 1, true, List.of()));
    }

    private static void send(ServerPlayer sp, LobbyClientboundPacket p) {
        // fake players (game tests) have no connection to send on
        if (sp instanceof net.minecraftforge.common.util.FakePlayer)
            return;
        PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> sp), p);
    }

    public LobbyClientboundPacket(FriendlyByteBuf buf) {
        kind = buf.readEnum(Kind.class);
        open = buf.readBoolean();
        starting = buf.readBoolean();
        hostName = buf.readUtf(64);
        arena = buf.readByte();
        metal = buf.readByte();
        spawnDistance = buf.readByte();
        pace = buf.readByte();
        spectating = buf.readBoolean();
        int n = Math.min(buf.readVarInt(), MultiplayerLobby.MAX_SEATS);
        seats = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            MultiplayerLobby.Seat s = new MultiplayerLobby.Seat(buf.readVarInt(), buf.readBoolean());
            s.name = buf.readUtf(64);
            s.faction = buf.readByte();
            s.colorMapId = buf.readVarInt();
            s.team = buf.readByte();
            s.ready = buf.readBoolean();
            s.difficulty = buf.readByte();
            seats.add(s);
        }
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeEnum(kind);
        buf.writeBoolean(open);
        buf.writeBoolean(starting);
        buf.writeUtf(hostName, 64);
        buf.writeByte(arena);
        buf.writeByte(metal);
        buf.writeByte(spawnDistance);
        buf.writeByte(pace);
        buf.writeBoolean(spectating);
        int n = Math.min(seats.size(), MultiplayerLobby.MAX_SEATS);
        buf.writeVarInt(n);
        for (int i = 0; i < n; i++) {
            MultiplayerLobby.Seat s = seats.get(i);
            buf.writeVarInt(s.id);
            buf.writeBoolean(s.bot);
            buf.writeUtf(s.name, 64);
            buf.writeByte(s.faction);
            buf.writeVarInt(s.colorMapId);
            buf.writeByte(s.team);
            buf.writeBoolean(s.ready);
            buf.writeByte(s.difficulty);
        }
    }

    public boolean handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> LobbyClientEvents.onPacket(this)));
        ctx.get().setPacketHandled(true);
        return true;
    }
}
