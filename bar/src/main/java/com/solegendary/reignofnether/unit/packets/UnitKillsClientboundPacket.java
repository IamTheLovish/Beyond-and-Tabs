package com.solegendary.reignofnether.unit.packets;

import com.solegendary.reignofnether.registrars.PacketHandler;
import com.solegendary.reignofnether.unit.UnitKills;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.function.Supplier;

/** Per-unit kill counters that changed since the last flush: parallel (entity id, kills) arrays. See UnitKills. */
public class UnitKillsClientboundPacket {

    static final int MAX_ENTRIES = 512;

    private final int[] ids;
    private final int[] kills;

    public static void sendToAll(int[] ids, int[] kills) {
        PacketHandler.INSTANCE.send(PacketDistributor.ALL.noArg(), new UnitKillsClientboundPacket(ids, kills));
    }

    public static void sendTo(ServerPlayer sp, int[] ids, int[] kills) {
        PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> sp), new UnitKillsClientboundPacket(ids, kills));
    }

    public UnitKillsClientboundPacket(int[] ids, int[] kills) {
        this.ids = ids;
        this.kills = kills;
    }

    public UnitKillsClientboundPacket(FriendlyByteBuf buffer) {
        int n = Math.min(buffer.readVarInt(), MAX_ENTRIES);
        ids = new int[n];
        kills = new int[n];
        for (int i = 0; i < n; i++) {
            ids[i] = buffer.readVarInt();
            kills[i] = buffer.readVarInt();
        }
    }

    public void encode(FriendlyByteBuf buffer) {
        int n = Math.min(ids.length, MAX_ENTRIES);
        buffer.writeVarInt(n);
        for (int i = 0; i < n; i++) {
            buffer.writeVarInt(ids[i]);
            buffer.writeVarInt(kills[i]);
        }
    }

    public boolean handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
            var level = Minecraft.getInstance().level;
            if (level == null)
                return;
            for (int i = 0; i < ids.length; i++)
                if (level.getEntity(ids[i]) instanceof LivingEntity le)
                    UnitKills.set(le, kills[i]);
        }));
        ctx.get().setPacketHandled(true);
        return true;
    }
}
