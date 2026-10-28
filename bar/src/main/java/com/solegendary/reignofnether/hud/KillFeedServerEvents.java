package com.solegendary.reignofnether.hud;

import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;

import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.HashMap;
import java.util.Map;

/**
 * Per-player kill tally: units killed and units lost to enemy units. The on-screen kill feed is gone (user request);
 * this tally still feeds the stats table's Army tab (kills / losses) and the bot's army log.
 */
public class KillFeedServerEvents {

    // per-player {units killed, units lost} for the stats table's Army tab and the observer bar
    // (PlayerPanelServerEvents reads it every 2 s and clears it when the match ends)
    private static final Map<String, int[]> tally = new HashMap<>();

    /** One enemy unit killed by {@code killerOwner}'s unit (public for the game test). */
    public static void count(String killerOwner, String victimOwner) {
        synchronized (tally) {
            tally.computeIfAbsent(killerOwner, k -> new int[2])[0]++;
            if (victimOwner != null && !victimOwner.isEmpty())
                tally.computeIfAbsent(victimOwner, k -> new int[2])[1]++;
        }
    }

    public static int kills(String owner) {
        synchronized (tally) {
            int[] t = tally.get(owner);
            return t == null ? 0 : t[0];
        }
    }

    public static int losses(String owner) {
        synchronized (tally) {
            int[] t = tally.get(owner);
            return t == null ? 0 : t[1];
        }
    }

    public static void resetTally() {
        synchronized (tally) {
            tally.clear();
        }
    }

    public static void onUnitKilled(LivingEntity killer, LivingEntity victim) {
        if (!(killer instanceof Unit k) || !(victim instanceof Unit v))
            return;
        String ko = k.getOwnerName(), vo = v.getOwnerName();
        if (ko == null || ko.isEmpty() || ko.equals(vo) || AlliancesServerEvents.isAllied(ko, vo))
            return;
        count(ko, vo);
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent evt) {
        LivingEntity victim = evt.getEntity();
        if (victim.level().isClientSide() || !(victim instanceof Unit)
                || !(evt.getSource().getEntity() instanceof LivingEntity killer))
            return;
        onUnitKilled(killer, victim);
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent evt) {
        resetTally();
    }
}
