
package com.solegendary.reignofnether.attackwarnings;

import com.solegendary.reignofnether.network.Audience;
import com.solegendary.reignofnether.registrars.PacketHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

public class AttackWarningClientboundPacket {

    private final String attackedPlayerName;
    private final BlockPos attackPos;
    private final boolean isCommander; // lets allies ping their teammate's commander in orange on the minimap

    public static void sendWarning(String attackedPlayerName, BlockPos attackPos, boolean isCommander) {
        // who reacts (AttackWarningClientEvents): the attacked player, and their allies to a commander hit. It marks
        // where a fight is, so it went nowhere else - it reached every enemy before (network/Audience)
        PacketHandler.INSTANCE.send(isCommander ? Audience.allies(attackedPlayerName) : Audience.owner(attackedPlayerName),
            new AttackWarningClientboundPacket(
                attackedPlayerName,
                attackPos,
                isCommander
            ));
    }

    // packet-handler functions
    public AttackWarningClientboundPacket(
        String attackedPlayerName,
        BlockPos attackPos,
        boolean isCommander
    ) {
        this.attackedPlayerName = attackedPlayerName;
        this.attackPos = attackPos;
        this.isCommander = isCommander;
    }

    public AttackWarningClientboundPacket(FriendlyByteBuf buffer) {
        this.attackedPlayerName = buffer.readUtf();
        this.attackPos = buffer.readBlockPos();
        this.isCommander = buffer.readBoolean();
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeUtf(this.attackedPlayerName);
        buffer.writeBlockPos(this.attackPos);
        buffer.writeBoolean(this.isCommander);
    }

    // server-side packet-consuming functions
    public boolean handle(Supplier<NetworkEvent.Context> ctx) {
        final var success = new AtomicBoolean(false);
        ctx.get().enqueueWork(() -> {
            AttackWarningClientEvents.checkAndTriggerAttackWarning(attackedPlayerName, attackPos, isCommander);
            success.set(true);
        });
        ctx.get().setPacketHandled(true);
        return success.get();
    }
}
