package com.solegendary.reignofnether.building;

import com.solegendary.reignofnether.registrars.PacketHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** "What is this building I'm hovering producing?" - answered by BuildingHoverInfo, fog-gated and throttled. */
public class BuildingHoverServerboundPacket {

    private final BlockPos pos;

    public static void ask(BlockPos pos) {
        PacketHandler.INSTANCE.sendToServer(new BuildingHoverServerboundPacket(pos));
    }

    public BuildingHoverServerboundPacket(BlockPos pos) {
        this.pos = pos;
    }

    public BuildingHoverServerboundPacket(FriendlyByteBuf buffer) {
        this.pos = buffer.readBlockPos();
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeBlockPos(pos);
    }

    public boolean handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> BuildingHoverInfo.answer(ctx.get().getSender(), pos));
        ctx.get().setPacketHandled(true);
        return true;
    }
}
