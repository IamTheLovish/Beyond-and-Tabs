package com.solegendary.reignofnether.unit.packets;

import com.solegendary.reignofnether.network.Audience;
import com.solegendary.reignofnether.player.CommanderServerEvents;
import com.solegendary.reignofnether.registrars.PacketHandler;
import com.solegendary.reignofnether.unit.UnitClientEvents;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.interfaces.WorkerUnit;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

// send a list of worker unit ids that are idle at this point in time
public class UnitIdleWorkerClientBoundPacket {

    private final int[] oldUnitIds; // units to be controlled

    private static final int[] NONE = new int[0];

    /**
     * Once a second (UnitServerEvents): each player gets the ids of their own idle workers, the only ones the idle
     * worker button uses (UnitClientEvents.syncIdleWorkers keeps OWNED workers only). It was one list of every side's
     * idle workers to every player - an enemy's idle economy through the fog (network/Audience, rewrite 3), and a
     * walk over every client unit per listed id on every client.
     */
    public static void sendIdleWorkerPacket() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null)
            return;
        Map<String, IntArrayList> byOwner = new HashMap<>();
        for (LivingEntity livingEntity : UnitServerEvents.getAllUnits()) {
            if (countsAsIdleBuilder(livingEntity))
                byOwner.computeIfAbsent(Audience.ownerOf(livingEntity), k -> new IntArrayList()).add(livingEntity.getId());
        }
        // every player, every second, even with an empty list: the client rebuilds its list from each packet
        for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
            IntArrayList mine = byOwner.get(sp.getGameProfile().getName());
            PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> sp),
                new UnitIdleWorkerClientBoundPacket(mine == null ? NONE : mine.toIntArray()));
        }
    }

    /**
     * An idle worker for the idle-worker button and hotkey. The commander is a worker too (the first starting worker,
     * tagged by CommanderServerEvents), but selecting it with the idle builders pulled it out of its own job and into
     * the front line by accident (Lane C playtest, Oct 20), so it is never listed: select it on its own.
     */
    public static boolean countsAsIdleWorker(LivingEntity entity) {
        return entity instanceof WorkerUnit wu && !CommanderServerEvents.isCommander(entity) && WorkerUnit.isIdle(wu);
    }

    /**
     * Every idle worker-type unit incl. the commander and T2 builders: the packet carries them all and the client
     * splits them into the idle-worker / idle-T2-builder / commander buttons (hud/IdleButtons).
     */
    public static boolean countsAsIdleBuilder(LivingEntity entity) {
        return entity instanceof WorkerUnit wu && WorkerUnit.isIdle(wu);
    }

    /**
     * "Select idle builders" in the typed command bar (client side, where the commander is known by its translated name
     * as well as by its tag): the same rule as the button, so the voice/typed group never pulls the commander in either.
     */
    public static boolean isSelectableIdleBuilder(LivingEntity entity) {
        return entity instanceof WorkerUnit && entity instanceof com.solegendary.reignofnether.unit.interfaces.Unit u
            && !CommanderServerEvents.isCommander(entity) && !CommanderServerEvents.looksLikeCommander(entity) && u.isIdle();
    }

    // packet-handler functions
    public UnitIdleWorkerClientBoundPacket(
            int[] oldUnitIds
    ) {
        this.oldUnitIds = oldUnitIds;
    }

    public UnitIdleWorkerClientBoundPacket(FriendlyByteBuf buffer) {
        this.oldUnitIds = buffer.readVarIntArray();
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeVarIntArray(this.oldUnitIds);
    }

    // server-side packet-consuming functions
    public boolean handle(Supplier<NetworkEvent.Context> ctx) {
        final var success = new AtomicBoolean(false);
        ctx.get().enqueueWork(() -> {
            UnitClientEvents.syncIdleWorkers(oldUnitIds);
            success.set(true);
        });
        ctx.get().setPacketHandled(true);
        return success.get();
    }
}
