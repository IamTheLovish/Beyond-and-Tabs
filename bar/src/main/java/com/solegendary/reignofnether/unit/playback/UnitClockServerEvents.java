package com.solegendary.reignofnether.unit.playback;

import com.solegendary.reignofnether.ReignOfNether;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Stamps every server tick for the clients (UnitClockClientboundPacket), before the levels tick. */
@Mod.EventBusSubscriber(modid = ReignOfNether.MOD_ID)
public final class UnitClockServerEvents {

    private UnitClockServerEvents() { }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent evt) {
        if (evt.phase != TickEvent.Phase.START || evt.getServer() == null || evt.getServer().getPlayerCount() == 0)
            return;
        UnitClockClientboundPacket.broadcast(evt.getServer().getTickCount());
    }
}
