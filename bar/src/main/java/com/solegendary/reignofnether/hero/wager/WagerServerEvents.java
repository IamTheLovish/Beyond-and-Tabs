package com.solegendary.reignofnether.hero.wager;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.network.PacketAuth;
import com.solegendary.reignofnether.registrars.ItemRegistrar;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The Wager Book's debug toggle: {@code /wagerbook on} lets any faction hero carry Lovish's book (key 1 of a hero opens
 * it; the Wager Book item does too) and gives the sender a book, {@code /wagerbook off} puts it away. Host or op only:
 * it is a prototype, not a match rule. Joining players learn the toggle's state.
 */
@Mod.EventBusSubscriber(modid = ReignOfNether.MOD_ID)
public final class WagerServerEvents {
    private WagerServerEvents() { }

    static boolean mayToggle(CommandSourceStack source) {
        return source.getEntity() instanceof ServerPlayer sp ? PacketAuth.isHostOrOp(sp, 2) : source.hasPermission(2);
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("wagerbook").requires(WagerServerEvents::mayToggle)
            .then(Commands.literal("on").executes(c -> toggle(c.getSource(), true)))
            .then(Commands.literal("off").executes(c -> toggle(c.getSource(), false))));
    }

    static int toggle(CommandSourceStack source, boolean on) {
        Wager.enabled = on;
        WagerClientboundPacket.toggleAll(on);
        if (on && source.getEntity() instanceof ServerPlayer sp)
            sp.getInventory().add(new ItemStack(ItemRegistrar.WAGER_BOOK.get()));
        source.sendSuccess(() -> Component.literal(on
            ? "Wager Book on: key 1 of a hero (or the book) opens it. Write a request, press Wager."
            : "Wager Book off."), true);
        return 1;
    }

    /** The legendary clocks and the debug toggle belong to one world: the next world in the same game starts clean. */
    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent evt) {
        WagerClock.MATCH.reset();
        Wager.enabled = false;
    }

    @SubscribeEvent
    public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent evt) {
        if (evt.getEntity() instanceof ServerPlayer sp)
            WagerClientboundPacket.toggle(sp, Wager.enabled);
    }
}
