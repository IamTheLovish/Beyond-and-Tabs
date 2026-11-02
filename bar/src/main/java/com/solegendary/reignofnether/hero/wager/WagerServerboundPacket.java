package com.solegendary.reignofnether.hero.wager;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.hero.FactionHero;
import com.solegendary.reignofnether.network.PacketAuth;
import com.solegendary.reignofnether.registrars.PacketHandler;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.network.NetworkEvent;

import javax.annotation.Nullable;
import java.util.function.Supplier;

/**
 * The book's Wager button: the page's text and the hero it was written to (the possessed or selected hero, or -1 to let
 * the server pick the sender's nearest hero). The server decides everything else (Wager.cast); the client never sends a
 * verdict. Only while the debug toggle is on, only for the sender's own heroes, rate-limited.
 */
public class WagerServerboundPacket {

    public static final int MAX_TEXT = 512;
    static final double NEAREST_HERO_RANGE = 64;

    private final int heroId;
    private final String text;

    public WagerServerboundPacket(int heroId, String text) {
        this.heroId = heroId;
        this.text = text == null ? "" : text.length() > MAX_TEXT ? text.substring(0, MAX_TEXT) : text;
    }

    public WagerServerboundPacket(FriendlyByteBuf buffer) {
        this.heroId = buffer.readInt();
        this.text = buffer.readUtf(MAX_TEXT);
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeInt(heroId);
        buffer.writeUtf(text, MAX_TEXT);
    }

    /** Client side: send a written page. */
    public static void send(int heroId, String text) {
        PacketHandler.INSTANCE.sendToServer(new WagerServerboundPacket(heroId, text));
    }

    public boolean handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) {
                ReignOfNether.LOGGER.warn("Sender for wager packet was null");
                return;
            }
            if (!PacketAuth.allowLogged(player, "wager", 2, 4))
                return;
            accept(player, heroId, text);
        });
        ctx.get().setPacketHandled(true);
        return true;
    }

    static void accept(ServerPlayer player, int heroId, String text) {
        if (!Wager.enabled) {
            player.sendSystemMessage(Component.literal("The Wager Book is a debug prototype: an op turns it on with /wagerbook on"));
            return;
        }
        ServerLevel level = player.serverLevel();
        Mob hero = heroFor(player, level, heroId);
        if (hero == null) {
            player.sendSystemMessage(Component.literal("Lovish needs a hero of yours to read the book: select or possess one"));
            return;
        }
        Wager.Result r = Wager.cast(level, hero, text);
        WagerState book = WagerState.of(hero);
        player.sendSystemMessage(Component.literal("[Lovish] " + r.reply()));
        WagerClientboundPacket.reply(player, hero.getId(), WagerParser.requestLine(text), r.reply(), book.tally(), book.pages());
    }

    /** The named hero if the sender may order it, else the sender's nearest own hero. */
    @Nullable
    static Mob heroFor(ServerPlayer player, ServerLevel level, int heroId) {
        String name = player.getName().getString();
        Entity e = heroId >= 0 ? level.getEntity(heroId) : null;
        if (e instanceof FactionHero && e instanceof Mob m && m.isAlive() && PacketAuth.mayControl(level, name, e))
            return m;
        Mob best = null;
        double bestD = NEAREST_HERO_RANGE * NEAREST_HERO_RANGE;
        for (LivingEntity le : UnitServerEvents.getAllUnits()) {
            if (!(le instanceof FactionHero) || !(le instanceof Mob m) || le.level() != level || !le.isAlive()
                    || !(le instanceof Unit u) || !name.equals(u.getOwnerName()))
                continue;
            double dx = le.getX() - player.getX(), dz = le.getZ() - player.getZ();
            double d = dx * dx + dz * dz;
            if (d < bestD) {
                bestD = d;
                best = m;
            }
        }
        return best;
    }
}
