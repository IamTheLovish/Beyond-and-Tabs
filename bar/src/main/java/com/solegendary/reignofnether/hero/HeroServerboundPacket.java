package com.solegendary.reignofnether.hero;

import com.solegendary.reignofnether.ability.HeroAbility;
import com.solegendary.reignofnether.registrars.PacketHandler;
import com.solegendary.reignofnether.unit.interfaces.HeroUnit;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.network.NetworkEvent;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

public class HeroServerboundPacket {

    private final int unitId;
    private final HeroAction heroAction;

    public static void requestHeroSync(int unitId) {
        PacketHandler.INSTANCE.sendToServer(new HeroServerboundPacket(unitId, HeroAction.REQUEST_SYNC));
    }

    public HeroServerboundPacket(
            int unitId,
            HeroAction heroAction
    ) {
        this.unitId = unitId;
        this.heroAction = heroAction;
    }

    public HeroServerboundPacket(FriendlyByteBuf buffer) {
        this.unitId = buffer.readInt();
        this.heroAction = buffer.readEnum(HeroAction.class);
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeInt(this.unitId);
        buffer.writeEnum(this.heroAction);
    }

    // server-side packet-consuming functions
    public boolean handle(Supplier<NetworkEvent.Context> ctx) {
        final var success = new AtomicBoolean(false);
        ctx.get().enqueueWork(() -> {
            // a client asks when a hero loads for it (UnitClientEvents.onEntityJoin): cap how often one client may ask
            net.minecraft.server.level.ServerPlayer sender = ctx.get().getSender();
            if (!com.solegendary.reignofnether.network.PacketAuth.allow(sender, "hero_sync_request", 50, 100))
                return;
            // the answer goes to the asker alone, and only if they may know the hero (its side, a spectator, or in
            // their fog's view - network/Audience): it went to every player, and anyone could ask about any id
            if (heroAction == HeroAction.REQUEST_SYNC
                    && sender.serverLevel().getEntity(this.unitId) instanceof HeroUnit hero
                    && com.solegendary.reignofnether.network.Audience.mayKnow(sender, (LivingEntity) hero)) {
                com.solegendary.reignofnether.network.AudienceResync.syncHeroTo(sender, hero,
                    com.solegendary.reignofnether.network.Audience.knowsPrivately(sender, hero.getOwnerName()));
            }
            success.set(true);
        });
        ctx.get().setPacketHandled(true);
        return success.get();
    }
}
