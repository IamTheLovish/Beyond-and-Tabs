package com.solegendary.reignofnether.maps;

import com.solegendary.reignofnether.network.Audience;
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
 * The map in play and the start-position phase, server to client (design/maps.md): which preset (fog tint), its
 * centre and radius, and while the phase runs the team boxes, every pick (name, team colour, spot, placed) and the
 * seconds left. A few hundred bytes; sent when the map starts, on every placement, once a second during the phase
 * and when it ends - never during the match itself.
 */
public class MapStateClientboundPacket {

    public record BoxInfo(int team, float a0, float a1, float r0, float r1) { }
    public record PickInfo(String name, int team, int colour, int x, int z, boolean placed, boolean bot) { }

    final int preset, cx, cz, radius;
    final boolean phase;
    final int secondsLeft;
    final List<BoxInfo> boxes;
    final List<PickInfo> picks;

    MapStateClientboundPacket(int preset, int cx, int cz, int radius, boolean phase, int secondsLeft,
                              List<BoxInfo> boxes, List<PickInfo> picks) {
        this.preset = preset;
        this.cx = cx;
        this.cz = cz;
        this.radius = radius;
        this.phase = phase;
        this.secondsLeft = secondsLeft;
        this.boxes = boxes;
        this.picks = picks;
    }

    static MapStateClientboundPacket current() {
        StartPhase ph = StartPhase.live();
        List<BoxInfo> boxes = new ArrayList<>();
        List<PickInfo> picks = new ArrayList<>();
        int cx = MapMatch.activeCx, cz = MapMatch.activeCz;
        if (ph != null && !ph.isFinished()) {
            cx = ph.cx;
            cz = ph.cz;
            for (MapLayout.Box b : ph.layout.boxes)
                boxes.add(new BoxInfo(b.team(), (float) b.a0(), (float) b.a1(), (float) b.r0(), (float) b.r1()));
            for (StartPhase.Pick p : ph.picks)
                picks.add(new PickInfo(p.name, p.team, p.colourHex, p.x, p.z, p.placed, p.bot));
        }
        return new MapStateClientboundPacket(MapMatch.activePreset.ordinal(), cx, cz, MapMatch.activeRadius,
            ph != null && !ph.isFinished(), ph != null ? ph.secondsLeft() : 0, boxes, picks);
    }

    public static void broadcast() {
        PacketHandler.INSTANCE.send(Audience.all(), current()); // design/maps.md 5: picks show for everyone
    }

    public static void sendTo(ServerPlayer sp) {
        if (sp instanceof net.minecraftforge.common.util.FakePlayer)
            return;
        PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> sp), current());
    }

    public MapStateClientboundPacket(FriendlyByteBuf buf) {
        preset = buf.readByte();
        cx = buf.readVarInt();
        cz = buf.readVarInt();
        radius = buf.readVarInt();
        phase = buf.readBoolean();
        secondsLeft = buf.readByte();
        int nb = Math.min(buf.readVarInt(), 16);
        boxes = new ArrayList<>(nb);
        for (int i = 0; i < nb; i++)
            boxes.add(new BoxInfo(buf.readByte(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat()));
        int np = Math.min(buf.readVarInt(), 32);
        picks = new ArrayList<>(np);
        for (int i = 0; i < np; i++)
            picks.add(new PickInfo(buf.readUtf(64), buf.readByte(), buf.readInt(), buf.readVarInt(), buf.readVarInt(),
                buf.readBoolean(), buf.readBoolean()));
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeByte(preset);
        buf.writeVarInt(cx);
        buf.writeVarInt(cz);
        buf.writeVarInt(radius);
        buf.writeBoolean(phase);
        buf.writeByte(Math.min(127, secondsLeft));
        buf.writeVarInt(boxes.size());
        for (BoxInfo b : boxes) {
            buf.writeByte(b.team());
            buf.writeFloat(b.a0());
            buf.writeFloat(b.a1());
            buf.writeFloat(b.r0());
            buf.writeFloat(b.r1());
        }
        buf.writeVarInt(picks.size());
        for (PickInfo p : picks) {
            buf.writeUtf(p.name(), 64);
            buf.writeByte(p.team());
            buf.writeInt(p.colour());
            buf.writeVarInt(p.x());
            buf.writeVarInt(p.z());
            buf.writeBoolean(p.placed());
            buf.writeBoolean(p.bot());
        }
    }

    public boolean handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> MapClient.onState(this)));
        ctx.get().setPacketHandled(true);
        return true;
    }
}
