package com.solegendary.reignofnether.unit.packets;

import com.solegendary.reignofnether.network.Audience;
import com.solegendary.reignofnether.registrars.PacketHandler;
import com.solegendary.reignofnether.unit.UnitKills;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.function.Supplier;

/** Per-unit kill counters that changed since the last flush: parallel (entity id, kills) arrays. See UnitKills. */
public class UnitKillsClientboundPacket {

    static final int MAX_ENTRIES = 512;

    private final int[] ids;
    private final int[] kills;

    /**
     * A flush of changed counters: each player gets the entries for the units they may know (network/Audience SEEN:
     * their side, spectators, or in their fog's view), so a kill in the fog no longer tells the enemy anything.
     * AudienceResync sends a unit's tally when it comes into view.
     */
    public static void broadcast(int[] ids, int[] kills) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null)
            return;
        Entity[] units = new Entity[ids.length];
        for (int i = 0; i < ids.length; i++)
            units[i] = Audience.entity(ids[i]);
        IntArrayList mineIds = new IntArrayList(ids.length), mineKills = new IntArrayList(ids.length);
        for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
            mineIds.clear();
            mineKills.clear();
            for (int i = 0; i < ids.length; i++)
                if (units[i] != null && Audience.mayKnow(sp, units[i])) {
                    mineIds.add(ids[i]);
                    mineKills.add(kills[i]);
                }
            if (!mineIds.isEmpty())
                sendTo(sp, mineIds.toIntArray(), mineKills.toIntArray());
        }
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
