package com.solegendary.reignofnether.unit.packets;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.network.PacketAuth;
import com.solegendary.reignofnether.unit.UnitAction;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.command.CommandQueues;
import com.solegendary.reignofnether.unit.command.QueueMode;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

public class UnitActionServerboundPacket {

    private final String ownerName; // player that is issuing this command
    private final UnitAction action;
    private final int unitId;
    private final int[] unitIds; // units to be controlled
    private final BlockPos preselectedBlockPos;
    private final BlockPos selectedBuildingPos; // for building abilities
    // how the order meets each unit's queue: plain = replace, Shift = append, Ctrl = insert at the front. Written as
    // one byte in the slot of the old shift boolean (false/true = REPLACE/APPEND), so the wire format kept its shape
    private final QueueMode queueMode;

    // packet-handler functions
    public UnitActionServerboundPacket(
        String ownerName,
        UnitAction action,
        int unitId,
        int[] unitIds,
        BlockPos preselectedBlockPos,
        BlockPos selectedBuildingPos,
        boolean shiftQueue
    ) {
        this(ownerName, action, unitId, unitIds, preselectedBlockPos, selectedBuildingPos,
            shiftQueue ? QueueMode.APPEND : QueueMode.REPLACE);
    }

    public UnitActionServerboundPacket(
        String ownerName,
        UnitAction action,
        int unitId,
        int[] unitIds,
        BlockPos preselectedBlockPos,
        BlockPos selectedBuildingPos,
        QueueMode queueMode
    ) {
        this.ownerName = ownerName;
        this.action = action;
        this.unitId = unitId;
        this.unitIds = unitIds;
        this.preselectedBlockPos = preselectedBlockPos;
        this.selectedBuildingPos = selectedBuildingPos;
        this.queueMode = queueMode == null ? QueueMode.REPLACE : queueMode;
    }

    public UnitActionServerboundPacket(FriendlyByteBuf buffer) {
        this.ownerName = buffer.readUtf();
        this.action = buffer.readEnum(UnitAction.class);
        this.unitId = buffer.readInt();
        this.unitIds = buffer.readVarIntArray();
        this.preselectedBlockPos = buffer.readBlockPos();
        this.selectedBuildingPos = buffer.readBlockPos();
        this.queueMode = QueueMode.byOrdinal(buffer.readByte());
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeUtf(this.ownerName);
        buffer.writeEnum(this.action);
        buffer.writeInt(this.unitId);
        buffer.writeVarIntArray(this.unitIds);
        buffer.writeBlockPos(this.preselectedBlockPos);
        buffer.writeBlockPos(this.selectedBuildingPos);
        buffer.writeByte(this.queueMode.ordinal());
    }

    /** The queue mode this packet carries (the round-trip game test reads it back). */
    public QueueMode getQueueMode() {
        return queueMode;
    }

    // server-side packet-consuming functions
    public boolean handle(Supplier<NetworkEvent.Context> ctx) {
        final var success = new AtomicBoolean(false);
        ctx.get().enqueueWork(() -> {
            if (this.action == UnitAction.DEBUG1) {
                UnitServerEvents.debug1(this.preselectedBlockPos);
            }
            if (this.action == UnitAction.DEBUG2) {
                UnitServerEvents.debug2();
            }

            ServerPlayer player = ctx.get().getSender();
            if (player == null) {
                ReignOfNether.LOGGER.warn("Sender for unit action packet was null");
                return;
            }
            // a formation drag sends one order per unit in a single tick (FormationDragMove: 200 units = 200
            // packets), so the burst is large; sustained, nobody issues more than a few hundred orders a second
            if (!PacketAuth.allow(player, "unit_action", 200, 600))
                return;
            success.set(accept(player, this.ownerName, this.action, this.unitId, this.unitIds,
                this.preselectedBlockPos, this.selectedBuildingPos, this.queueMode));
        });
        ctx.get().setPacketHandled(true);
        return success.get();
    }

    /** {@link #accept(ServerPlayer, String, UnitAction, int, int[], BlockPos, BlockPos, QueueMode)} with Shift or not. */
    public static boolean accept(ServerPlayer player, String ownerName, UnitAction action, int unitId, int[] unitIds,
                                 BlockPos preselectedBlockPos, BlockPos selectedBuildingPos, boolean shiftQueue) {
        return accept(player, ownerName, action, unitId, unitIds, preselectedBlockPos, selectedBuildingPos,
            shiftQueue ? QueueMode.APPEND : QueueMode.REPLACE);
    }

    /**
     * Gives an order from {@code player} if they may give it: the claimed owner must be the sender (or an ally who
     * gave them control, or the sender is a sandbox player - the old check let anyone through when the claimed
     * OWNER was a sandbox player), and only the units the sender may order are kept, so a remote player can't slip
     * the host's unit ids into their own order. UnitActionItem checks each unit's owner again when the order runs;
     * this drops them before they reach any queue. The order then goes into the units' command queues
     * (CommandQueues.issue): a plain or Ctrl order takes effect right here, in the tick the packet is handled.
     * Returns false when the order is refused outright.
     */
    public static boolean accept(ServerPlayer player, String ownerName, UnitAction action, int unitId, int[] unitIds,
                                 BlockPos preselectedBlockPos, BlockPos selectedBuildingPos, QueueMode queueMode) {
        if (player == null || !PacketAuth.mayActFor(player, ownerName)) {
            ReignOfNether.LOGGER.warn("UnitActionServerboundPacket: Tried to process packet from " +
                (player == null ? "null" : player.getName().getString()) + " for " + ownerName);
            return false;
        }
        // (an order whose units were all someone else's still goes through with none: a building ability rides on
        // selectedBuildingPos, and UnitActionItem checks that building's owner itself)
        int[] ids = PacketAuth.controllableIds(player.serverLevel(), player.getName().getString(), unitIds);
        // a unit a player is possessing takes no RTS orders: the order becomes that player's objective instead
        ids = com.solegendary.reignofnether.hero.PossessionServer.divertOrders(player.serverLevel(), action, ids,
            preselectedBlockPos, unitId);
        CommandQueues.issue(player.serverLevel(), ownerName, action, unitId, ids, preselectedBlockPos, selectedBuildingPos,
            queueMode);
        // First Battle tutorial: move / attack-move / line-formation steps finish on the real order (no-op otherwise)
        com.solegendary.reignofnether.onboarding.OnboardingServer.onOrder(player.getName().getString(), action,
            ids.length, preselectedBlockPos);
        return true;
    }
}
