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

    /** Tells the server this player's Hero AI switch (on login and whenever the box is clicked). */
    public static void sendHeroAi(boolean on) {
        PacketHandler.INSTANCE.sendToServer(new HeroServerboundPacket(-1, on ? HeroAction.HERO_AI_ON : HeroAction.HERO_AI_OFF));
    }

    /** Tells the server this player's "Auto-learn hero abilities" setting (on login and whenever the box is clicked). */
    public static void sendAutoLearn(boolean on) {
        PacketHandler.INSTANCE.sendToServer(new HeroServerboundPacket(-1, on ? HeroAction.AUTO_LEARN_ON : HeroAction.AUTO_LEARN_OFF));
    }

    /** Tells the server the player opened or closed their hero's rank-up menu (auto-learn waits while it is open). */
    public static void sendRankMenu(int unitId, boolean open) {
        PacketHandler.INSTANCE.sendToServer(new HeroServerboundPacket(unitId, open ? HeroAction.RANK_MENU_OPEN : HeroAction.RANK_MENU_CLOSED));
    }

    /** Tells the server the player selected their hero (UnitClientEvents, when a hero joins the selection). */
    public static void sendHeroSelected(int unitId) {
        PacketHandler.INSTANCE.sendToServer(new HeroServerboundPacket(unitId, HeroAction.HERO_SELECTED));
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
            if (sender == null)
                return;
            if (heroAction == HeroAction.HERO_AI_ON || heroAction == HeroAction.HERO_AI_OFF) {
                // a player's own setting, for their own heroes only (the owner name is the player's name)
                if (com.solegendary.reignofnether.network.PacketAuth.allow(sender, "hero_ai_switch", 10, 100))
                    HeroBot.setAiEnabled(sender.getName().getString(), heroAction == HeroAction.HERO_AI_ON);
                success.set(true);
                return;
            }
            if (heroAction == HeroAction.AUTO_LEARN_ON || heroAction == HeroAction.AUTO_LEARN_OFF) {
                // as the Hero AI switch: the player's own setting, for their own heroes only
                if (com.solegendary.reignofnether.network.PacketAuth.allow(sender, "hero_auto_learn_switch", 10, 100))
                    HeroBot.setAutoLearn(sender.getName().getString(), heroAction == HeroAction.AUTO_LEARN_ON);
                success.set(true);
                return;
            }
            if (heroAction == HeroAction.RANK_MENU_OPEN || heroAction == HeroAction.RANK_MENU_CLOSED) {
                // only a hero the sender may command: its open menu holds auto-learn back for that hero
                if (com.solegendary.reignofnether.network.PacketAuth.allow(sender, "hero_rank_menu", 20, 100)
                        && sender.serverLevel().getEntity(this.unitId) instanceof FactionHero hero
                        && hero instanceof com.solegendary.reignofnether.unit.interfaces.Unit u
                        && com.solegendary.reignofnether.unit.UnitActionItem.mayCommand(sender.serverLevel(),
                            sender.getName().getString(), u, false))
                    hero.heroCore().rankUpMenuOpen = heroAction == HeroAction.RANK_MENU_OPEN;
                success.set(true);
                return;
            }
            if (heroAction == HeroAction.HERO_SELECTED) {
                // only the sender's own hero (or one they may command): a select hands a fleeing hero back (D030)
                if (com.solegendary.reignofnether.network.PacketAuth.allow(sender, "hero_select", 20, 100)
                        && sender.serverLevel().getEntity(this.unitId) instanceof net.minecraft.world.entity.Mob mob
                        && mob instanceof com.solegendary.reignofnether.unit.interfaces.Unit u
                        && com.solegendary.reignofnether.unit.UnitActionItem.mayCommand(sender.serverLevel(),
                            sender.getName().getString(), u, false))
                    HeroBot.onPlayerSelect(mob);
                success.set(true);
                return;
            }
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
