package com.solegendary.reignofnether.essence;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.List;

/**
 * The Gravebound Soulbinder's lasting effects (design/essence_casters.md): Soul Harvest's skeletons and Soul Chains'
 * links. Stub for the game tests written first.
 */
public final class SoulbinderServerEvents {

    private SoulbinderServerEvents() { }

    /** The Soul Harvest skeletons of {@code caster} still standing. */
    public static List<Mob> summonsOf(LivingEntity caster) {
        return List.of();
    }

    public static boolean isHarvestSummon(Entity e) {
        return false;
    }

    /** The game time a Soul Harvest skeleton crumbles (0 for anything else). */
    public static long summonEnd(Entity e) {
        return 0;
    }

    public static void setSummonEnd(Entity e, long endTick) {
    }

    /** The units chained with {@code e}, itself included (empty when it is in no live chain). */
    public static List<LivingEntity> chainOf(LivingEntity e) {
        return List.of();
    }

    /** The game time {@code e}'s chain breaks (0 when it is in none). */
    public static long chainEnd(LivingEntity e) {
        return 0;
    }

    public static void setChainEnd(LivingEntity e, long endTick) {
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent evt) {
    }
}
