package com.solegendary.reignofnether.essence;

import com.solegendary.reignofnether.ability.abilities.SoulChains;
import com.solegendary.reignofnether.barfx.BarFx;
import com.solegendary.reignofnether.hud.HudClientboundPacket;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The Gravebound Soulbinder's lasting effects (design/essence_casters.md): Soul Harvest's skeletons and Soul Chains'
 * links. Server side only.
 * <ul>
 *   <li>A Soul Harvest skeleton carries {@link #SUMMON_TAG}, the game time it crumbles and its caster's UUID in its
 *       persistent data (saved with it, so a reloaded skeleton still crumbles on time); this keeps the live ones in a
 *       list and discards each the tick its time is up.</li>
 *   <li>A chain is a set of enemies linked until a game time. {@link #onDamage} runs last on every hit
 *       (LivingDamageEvent: armour, resist and shields already applied): when the hurt unit is chained, each other
 *       living link takes {@link SoulChains#SHARE} of what it lost, as magic damage credited to whoever struck - and
 *       that shared damage is not shared again. Chains are short (10 s) and not saved.</li>
 * </ul>
 */
public final class SoulbinderServerEvents {

    private SoulbinderServerEvents() { }

    public static final String SUMMON_TAG = "bt_soul_harvest";
    static final String END_KEY = "bt_soul_harvest_end";
    static final String CASTER_KEY = "bt_soul_harvest_caster";
    /** A refusal message to the same owner at most this often: an autocast tries once a second. */
    static final int WARN_EVERY_TICKS = 60;

    private static final List<Mob> summons = new ArrayList<>();
    private static final List<Chain> chains = new ArrayList<>();
    private static final Map<LivingEntity, Chain> linked = new IdentityHashMap<>();
    private static final Map<String, Long> lastWarn = new HashMap<>();
    /** True while a chain's share is being dealt: those hits are not shared again. */
    private static boolean sharing = false;

    private static final class Chain {
        final List<LivingEntity> members;
        final Entity caster;
        long end;

        Chain(List<LivingEntity> members, Entity caster, long end) {
            this.members = members;
            this.caster = caster;
            this.end = end;
        }
    }

    // ------------------------------------------------------------------------------------------ Soul Harvest

    /** Marks {@code skeleton} as {@code caster}'s Soul Harvest summon, crumbling at {@code endTick}. Before it joins. */
    public static void registerSummon(Mob skeleton, LivingEntity caster, long endTick) {
        Essence.markSummoned(skeleton);
        skeleton.addTag(SUMMON_TAG);
        skeleton.getPersistentData().putLong(END_KEY, endTick);
        skeleton.getPersistentData().putUUID(CASTER_KEY, caster.getUUID());
        if (!summons.contains(skeleton))
            summons.add(skeleton);
    }

    /** The Soul Harvest skeletons of {@code caster} still standing. */
    public static List<Mob> summonsOf(LivingEntity caster) {
        List<Mob> out = new ArrayList<>();
        UUID id = caster.getUUID();
        for (Mob m : summons)
            if (!m.isRemoved() && m.isAlive() && m.getPersistentData().hasUUID(CASTER_KEY)
                    && id.equals(m.getPersistentData().getUUID(CASTER_KEY)))
                out.add(m);
        return out;
    }

    public static boolean isHarvestSummon(Entity e) {
        return e != null && e.getTags().contains(SUMMON_TAG);
    }

    /** The game time a Soul Harvest skeleton crumbles (0 for anything else). */
    public static long summonEnd(Entity e) {
        return isHarvestSummon(e) ? e.getPersistentData().getLong(END_KEY) : 0;
    }

    public static void setSummonEnd(Entity e, long endTick) {
        if (isHarvestSummon(e))
            e.getPersistentData().putLong(END_KEY, endTick);
    }

    /** A saved skeleton coming back (a reload): watched again, so it still crumbles on time. */
    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent evt) {
        if (evt.getLevel().isClientSide() || !(evt.getEntity() instanceof Mob m) || !isHarvestSummon(m))
            return;
        if (!summons.contains(m))
            summons.add(m);
    }

    // ------------------------------------------------------------------------------------------- Soul Chains

    /**
     * Chains {@code members} (the aimed enemy first) until {@code endTick}; a unit already in another chain leaves it
     * for this one. {@code caster} is credited with the shared damage when the hit had no attacker of its own.
     */
    public static void link(List<LivingEntity> members, Entity caster, long endTick) {
        Chain chain = new Chain(new ArrayList<>(members), caster, endTick);
        for (LivingEntity le : chain.members) {
            Chain old = linked.put(le, chain);
            if (old != null)
                old.members.remove(le);
        }
        chains.add(chain);
    }

    /** The units chained with {@code e}, itself included (empty when it is in no live chain). */
    public static List<LivingEntity> chainOf(LivingEntity e) {
        Chain c = linked.get(e);
        if (c == null || c.end <= e.level().getGameTime())
            return List.of();
        return List.copyOf(c.members);
    }

    /** The game time {@code e}'s chain breaks (0 when it is in none). */
    public static long chainEnd(LivingEntity e) {
        Chain c = linked.get(e);
        return c == null ? 0 : c.end;
    }

    /** Moves the end of {@code e}'s whole chain (game tests). */
    public static void setChainEnd(LivingEntity e, long endTick) {
        Chain c = linked.get(e);
        if (c != null)
            c.end = endTick;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDamage(LivingDamageEvent evt) {
        LivingEntity hurt = evt.getEntity();
        if (sharing || evt.isCanceled() || !(hurt.level() instanceof ServerLevel sl))
            return;
        Chain chain = linked.get(hurt);
        if (chain == null || chain.end <= sl.getGameTime())
            return;
        // what the hit really takes: an overkill shares only the health it had
        float dealt = Math.min(evt.getAmount(), hurt.getHealth());
        if (dealt <= 0)
            return;
        float share = dealt * SoulChains.SHARE;
        Entity striker = evt.getSource().getEntity() != null ? evt.getSource().getEntity() : chain.caster;
        DamageSource source = sl.damageSources().indirectMagic(chain.caster != null ? chain.caster : striker, striker);
        sharing = true;
        try {
            for (LivingEntity other : List.copyOf(chain.members)) {
                if (other == hurt || !other.isAlive() || other.isRemoved())
                    continue;
                // a share is a hit of its own, never swallowed by the vanilla hurt window of the hit before it
                other.invulnerableTime = 0;
                other.hurt(source, share);
                sl.sendParticles(ParticleTypes.SCULK_SOUL, other.getX(), other.getY() + other.getBbHeight() * 0.6, other.getZ(),
                    3, 0.2, 0.2, 0.2, 0.01);
            }
        } finally {
            sharing = false;
        }
    }

    // ------------------------------------------------------------------------------------------------ shared

    /** Tells {@code owner} why a cast was refused, at most once every {@link #WARN_EVERY_TICKS} (autocast retries). */
    public static void warn(ServerLevel sl, String owner, String key) {
        if (owner == null || owner.isEmpty())
            return;
        long now = sl.getGameTime();
        Long last = lastWarn.get(owner);
        if (last != null && now - last < WARN_EVERY_TICKS && now >= last)
            return;
        lastWarn.put(owner, now);
        HudClientboundPacket.showTempMessageI18n(owner, key);
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent evt) {
        if (evt.phase != TickEvent.Phase.END || evt.getServer() == null)
            return;
        // every dimension reads the overworld's clock (as the wisps do): the skeletons' and chains' ends are game times
        long now = evt.getServer().overworld().getGameTime();
        for (int i = summons.size() - 1; i >= 0; i--) {
            Mob m = summons.get(i);
            if (m.isRemoved() || !m.isAlive()) {
                summons.remove(i);
                continue;
            }
            if (now >= summonEnd(m)) {
                if (m.level() instanceof ServerLevel sl)
                    sl.sendParticles(ParticleTypes.SOUL, m.getX(), m.getY() + 0.8, m.getZ(), 12, 0.3, 0.5, 0.3, 0.02);
                m.discard();
                summons.remove(i);
            } else if ((now + m.getId()) % 10 == 0 && m.level() instanceof ServerLevel sl) {
                // spectral: a lick of soul fire round each, so they read as borrowed (placeholder look)
                sl.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, m.getX(), m.getY() + 1.0, m.getZ(), 2, 0.2, 0.4, 0.2, 0.0);
            }
        }
        for (int i = chains.size() - 1; i >= 0; i--) {
            Chain c = chains.get(i);
            c.members.removeIf(le -> le.isRemoved() || !le.isAlive());
            if (now >= c.end || c.members.size() < 2) {
                for (LivingEntity le : c.members)
                    linked.remove(le, c);
                chains.remove(i);
                continue;
            }
            if (now % 20 == 0) {
                // the links: a soul-blue beam from each to the next, renewed every second as they move
                for (int k = 0; k + 1 < c.members.size(); k++) {
                    LivingEntity from = c.members.get(k), to = c.members.get(k + 1);
                    BarFx.beam(from.level(), from.position().add(0, from.getBbHeight() * 0.6, 0),
                        to.position().add(0, to.getBbHeight() * 0.6, 0), BarFx.N_GRAVEBOUND, 20);
                }
            }
        }
        linked.keySet().removeIf(le -> le.isRemoved() || !le.isAlive());
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent evt) {
        summons.clear();
        chains.clear();
        linked.clear();
        lastWarn.clear();
        sharing = false;
    }
}
