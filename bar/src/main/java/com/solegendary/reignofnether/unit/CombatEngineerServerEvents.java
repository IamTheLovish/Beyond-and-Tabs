package com.solegendary.reignofnether.unit;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.ability.abilities.Bulwark;
import com.solegendary.reignofnether.ability.abilities.MendingRite;
import com.solegendary.reignofnether.ability.abilities.Rootwalk;
import com.solegendary.reignofnether.ability.abilities.SalvageHook;

import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The one server tick behind the combat engineers' abilities that outlive their cast (design/combat_engineers.md):
 * Bulwark braces, Rootwalk sinks, Salvage Hook lines and Mending Rites. Each keeps a short list of live records and
 * returns at once while it is empty, so this costs four isEmpty checks a tick on a quiet map. Self-registered
 * (BigUnitServerEvents' pattern): nothing to add to the two event registrars, and it runs for the integrated server and
 * a dedicated one alike. Everything is cleared when the server stops, so nothing leaks into the next world.
 */
@Mod.EventBusSubscriber(modid = ReignOfNether.MOD_ID)
public final class CombatEngineerServerEvents {
    private CombatEngineerServerEvents() { }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent evt) {
        if (evt.phase != TickEvent.Phase.END || evt.getServer() == null)
            return;
        long gameTime = evt.getServer().overworld().getGameTime();
        Bulwark.tick(gameTime);
        Rootwalk.tick(gameTime);
        SalvageHook.tick(gameTime);
        MendingRite.tick(gameTime);
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent evt) {
        Bulwark.clearAll();
        Rootwalk.clearAll();
        SalvageHook.clearAll();
        MendingRite.clearAll();
    }
}
