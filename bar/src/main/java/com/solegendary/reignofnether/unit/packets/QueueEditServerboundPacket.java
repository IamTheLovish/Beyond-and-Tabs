package com.solegendary.reignofnether.unit.packets;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.network.PacketAuth;
import com.solegendary.reignofnether.unit.command.CommandQueues;
import com.solegendary.reignofnether.unit.command.QueueEditKeys;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * BAR's queue edits (QueueEditKeys): N drops each selected unit's current order and starts its next one, Ctrl+N
 * drops the last order in each unit's queue. The sender's own (or controllable) units only; the queue lines reach the
 * client with the next UnitQueueSync pass.
 */
public class QueueEditServerboundPacket {

    private final QueueEditKeys.Edit edit;
    private final int[] unitIds;

    public QueueEditServerboundPacket(QueueEditKeys.Edit edit, int[] unitIds) {
        this.edit = edit == null ? QueueEditKeys.Edit.NONE : edit;
        this.unitIds = unitIds == null ? new int[0] : unitIds;
    }

    public QueueEditServerboundPacket(FriendlyByteBuf buffer) {
        int ordinal = buffer.readByte();
        QueueEditKeys.Edit[] values = QueueEditKeys.Edit.values();
        this.edit = ordinal < 0 || ordinal >= values.length ? QueueEditKeys.Edit.NONE : values[ordinal];
        this.unitIds = buffer.readVarIntArray();
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeByte(edit.ordinal());
        buffer.writeVarIntArray(unitIds);
    }

    public QueueEditKeys.Edit getEdit() {
        return edit;
    }

    public int[] getUnitIds() {
        return unitIds;
    }

    public boolean handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) {
                ReignOfNether.LOGGER.warn("Sender for queue edit packet was null");
                return;
            }
            if (!PacketAuth.allowLogged(player, "queue_edit", UnitActionServerboundPacket.ORDER_PER_SECOND,
                    UnitActionServerboundPacket.ORDER_BURST))
                return;
            accept(player, edit, unitIds);
        });
        ctx.get().setPacketHandled(true);
        return true;
    }

    /** Applies the edit for {@code player}: only the units they may order are touched. */
    public static void accept(ServerPlayer player, QueueEditKeys.Edit edit, int[] unitIds) {
        if (player == null || edit == null || edit == QueueEditKeys.Edit.NONE)
            return;
        String name = player.getName().getString();
        int[] ids = PacketAuth.controllableIds(player.serverLevel(), name, unitIds);
        if (edit == QueueEditKeys.Edit.SKIP_CURRENT)
            CommandQueues.skipCurrent(player.serverLevel(), name, ids);
        else
            CommandQueues.cancelLast(player.serverLevel(), name, ids);
    }
}
