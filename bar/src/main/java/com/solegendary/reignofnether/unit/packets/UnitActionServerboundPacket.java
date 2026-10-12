package com.solegendary.reignofnether.unit.packets;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.network.PacketAuth;
import com.solegendary.reignofnether.unit.UnitAction;
import com.solegendary.reignofnether.unit.UnitServerEvents;
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
    private final boolean shiftQueue; // shift queue actions

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
        this.ownerName = ownerName;
        this.action = action;
        this.unitId = unitId;
        this.unitIds = unitIds;
        this.preselectedBlockPos = preselectedBlockPos;
        this.selectedBuildingPos = selectedBuildingPos;
        this.shiftQueue = shiftQueue;
    }

    public UnitActionServerboundPacket(FriendlyByteBuf buffer) {
        this.ownerName = buffer.readUtf();
        this.action = buffer.readEnum(UnitAction.class);
        this.unitId = buffer.readInt();
        this.unitIds = buffer.readVarIntArray();
        this.preselectedBlockPos = buffer.readBlockPos();
        this.selectedBuildingPos = buffer.readBlockPos();
        this.shiftQueue = buffer.readBoolean();
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeUtf(this.ownerName);
        buffer.writeEnum(this.action);
        buffer.writeInt(this.unitId);
        buffer.writeVarIntArray(this.unitIds);
        buffer.writeBlockPos(this.preselectedBlockPos);
        buffer.writeBlockPos(this.selectedBuildingPos);
        buffer.writeBoolean(this.shiftQueue);
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
                this.preselectedBlockPos, this.selectedBuildingPos, this.shiftQueue));
        });
        ctx.get().setPacketHandled(true);
        return success.get();
    }

    /**
     * Queues an order from {@code player} if they may give it: the claimed owner must be the sender (or an ally who
     * gave them control, or the sender is a sandbox player - the old check let anyone through when the claimed
     * OWNER was a sandbox player), and only the units the sender may order are kept, so a remote player can't slip
     * the host's unit ids into their own order. UnitActionItem checks each unit's owner again when the order runs;
     * this drops them before they are even queued. Returns false when the order is refused outright.
     */
    public static boolean accept(ServerPlayer player, String ownerName, UnitAction action, int unitId, int[] unitIds,
                                 BlockPos preselectedBlockPos, BlockPos selectedBuildingPos, boolean shiftQueue) {
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
        UnitServerEvents.addActionItem(ownerName, action, unitId, ids, preselectedBlockPos, selectedBuildingPos, shiftQueue);
        // First Battle tutorial: move / attack-move / line-formation steps finish on the real order (no-op otherwise)
        com.solegendary.reignofnether.onboarding.OnboardingServer.onOrder(player.getName().getString(), action,
            ids.length, preselectedBlockPos);
        return true;
    }
}
