package com.solegendary.reignofnether.essence;

import com.solegendary.reignofnether.ability.abilities.Empower;
import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.barfx.BarFx;
import com.solegendary.reignofnether.hero.HeroExperienceOrb;
import com.solegendary.reignofnether.hero.HeroFx;
import com.solegendary.reignofnether.registrars.AttributeRegistrar;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.unit.Summons;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.interfaces.HeroUnit;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.units.monsters.GraveAdeptUnit;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The Grave Adept's rules (design/essence_casters.md "Gravebound Gravecaller" and Revision 1; every number is on
 * {@link GraveAdeptUnit}). Server side only.
 * <ul>
 *   <li><b>XP.</b> An enemy unit's death (another player's, not allied, not a summon) within
 *       {@link GraveAdeptUnit#XP_RANGE} feeds every Adept there {@link #xpFor} - what the same death pays a hero in
 *       orbs - shown as a soul shot. Empower on an Adept gives {@link GraveAdeptUnit#EMPOWER_XP} through the essence
 *       base's target hook (refused at the top level). A new level sets its health and damage modifiers (saved with
 *       it), keeping its share of health.</li>
 *   <li><b>Raising.</b> From level 1, at its raise tick (every {@link GraveAdeptUnit#RAISE_EVERY_SECONDS} s), up to
 *       its level in skeletons stand: on its owner's grave creep it raises the missing ones for free; at level
 *       {@link GraveAdeptUnit#ESSENCE_RAISE_LEVEL} off the creep it raises what its own essence pays for. They are the
 *       tombstones' Risen Skeletons (TombcallerServerEvents.raiseSkeleton).</li>
 *   <li><b>Soul bolt.</b> From level {@link GraveAdeptUnit#BOLT_LEVEL}, every {@link GraveAdeptUnit#BOLT_EVERY_SECONDS}
 *       s, magic damage to the nearest enemy unit within {@link GraveAdeptUnit#BOLT_RANGE}.</li>
 *   <li><b>Death.</b> Its XP flies as hero orbs to each hero of the killer's owner within the orbs' reach (a summon's
 *       own death pays no orbs, Summons; this is what it stored).</li>
 * </ul>
 */
public final class GraveAdeptServerEvents {

    private GraveAdeptServerEvents() { }

    static final UUID HP_MOD = UUID.fromString("6c2b1e8e-5d8f-4a9b-9b51-2f0d3c7a1e11");
    static final UUID DMG_MOD = UUID.fromString("6c2b1e8e-5d8f-4a9b-9b51-2f0d3c7a1e12");
    /** One orb carries this much hero XP (an orb of value v gives a hero 10 v); the last one the rest. */
    static final int XP_PER_ORB = 100;
    /** How often an Adept with nobody in reach looks again for a soul-bolt target. */
    static final int BOLT_LOOK_TICKS = 10;

    private static final List<GraveAdeptUnit> adepts = new ArrayList<>();
    /**
     * Adepts whose chunk unloaded, by their UUID: still their caster's until they come back or die (PR 371 review round
     * 1: an unloaded Adept let its Tombcaller call a second; the tombstones keep the same map).
     */
    private static final Map<UUID, Away> away = new HashMap<>();

    private record Away(UUID caster, String owner) { }

    static {
        Empower.setTargetHook(EntityRegistrar.GRAVE_ADEPT_UNIT.get(), new Empower.TargetHook() {
            @Override
            public boolean accepts(LivingEntity target) {
                return target instanceof GraveAdeptUnit a && a.adeptLevel() < GraveAdeptUnit.MAX_LEVEL;
            }

            @Override
            public void apply(ServerLevel level, LivingEntity caster, LivingEntity target) {
                if (target instanceof GraveAdeptUnit a)
                    addXp(a, GraveAdeptUnit.EMPOWER_XP);
            }
        });
    }

    /**
     * What a unit's death is worth: exactly what the hero orbs of HeroServerEvents pay a hero ((population + 1) x 5,
     * plus 5 a level for a hero, in orbs of 2 worth 10 XP each; an odd 1 is worth nothing). 0 for a free unit.
     */
    public static int xpFor(LivingEntity dead) {
        if (!(dead instanceof Unit u) || u.getCost() == null || u.getCost().population <= 0)
            return 0;
        int value = (u.getCost().population + 1) * 5;
        if (dead instanceof HeroUnit hero)
            value += hero.getHeroLevel() * 5;
        return value / 2 * 10;
    }

    /**
     * {@code caster}'s Grave Adept, if it stands loaded and still belongs to the caster's owner (one a Wraith possessed
     * is the enemy's now and no longer counts as the caster's).
     */
    @Nullable
    public static GraveAdeptUnit adeptOf(LivingEntity caster) {
        UUID id = caster.getUUID();
        String owner = ownerOf(caster);
        for (GraveAdeptUnit a : adepts)
            if (a.isAlive() && !a.isRemoved() && id.equals(a.casterId()) && owner.equals(a.getOwnerName()))
                return a;
        return null;
    }

    /** Whether {@code caster} has an Adept: one {@link #adeptOf} sees, or one of its owner's whose chunk unloaded. */
    public static boolean hasAdept(LivingEntity caster) {
        if (adeptOf(caster) != null)
            return true;
        UUID id = caster.getUUID();
        String owner = ownerOf(caster);
        for (Away w : away.values())
            if (id.equals(w.caster) && owner.equals(w.owner))
                return true;
        return false;
    }

    private static String ownerOf(LivingEntity le) {
        String owner = le instanceof Unit u ? u.getOwnerName() : null;
        return owner == null ? "" : owner;
    }

    /** Feeds {@code adept} {@code xp}, applying any new level's health and damage. */
    public static void addXp(GraveAdeptUnit adept, int xp) {
        if (xp <= 0)
            return;
        int before = adept.adeptLevel();
        adept.setXpRaw(adept.xp() + xp);
        int after = adept.adeptLevel();
        if (after == before)
            return;
        float hpFrac = adept.getHealth() / adept.getMaxHealth();
        setModifier(adept.getAttribute(Attributes.MAX_HEALTH), HP_MOD, "bt_adept_hp", GraveAdeptUnit.HP_PER_LEVEL * after);
        setModifier(adept.getAttribute(Attributes.ATTACK_DAMAGE), DMG_MOD, "bt_adept_dmg", GraveAdeptUnit.DAMAGE_PER_LEVEL * after);
        setModifier(adept.getAttribute(AttributeRegistrar.ATTACK_DAMAGE.get()), DMG_MOD, "bt_adept_dmg", GraveAdeptUnit.DAMAGE_PER_LEVEL * after);
        adept.setHealth(adept.getMaxHealth() * hpFrac);
        if (adept.level() instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.SCULK_SOUL, adept.getX(), adept.getY() + adept.getBbHeight() + 0.3, adept.getZ(),
                6 * after, 0.3, 0.2, 0.3, 0.02);
            sl.playSound(null, adept.blockPosition(), SoundEvents.SOUL_ESCAPE, SoundSource.NEUTRAL, 1.0f, 0.8f + 0.1f * after);
        }
    }

    private static void setModifier(AttributeInstance attr, UUID id, String name, double amount) {
        if (attr == null)
            return;
        if (attr.getModifier(id) != null)
            attr.removeModifier(id);
        attr.addPermanentModifier(new AttributeModifier(id, name, amount, AttributeModifier.Operation.MULTIPLY_TOTAL));
    }

    /** The skeletons {@code adept} raised still standing. */
    public static List<Mob> raisedBy(GraveAdeptUnit adept) {
        return TombcallerServerEvents.risenFrom(adept.getUUID());
    }

    // ------------------------------------------------------------------------------------------ the events

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent evt) {
        if (evt.getLevel().isClientSide() || !(evt.getEntity() instanceof GraveAdeptUnit a))
            return;
        away.remove(a.getUUID());
        if (!adepts.contains(a))
            adepts.add(a);
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent evt) {
        LivingEntity dead = evt.getEntity();
        if (!(dead.level() instanceof ServerLevel sl))
            return;
        if (dead instanceof GraveAdeptUnit adept) {
            burst(sl, adept, evt.getSource().getEntity());
            adepts.remove(adept);
            away.remove(adept.getUUID());
            return;
        }
        if (!away.isEmpty()) {
            UUID id = dead.getUUID();   // a dead Tombcaller calls no more: its unloaded Adept need not be held for it
            away.values().removeIf(w -> id.equals(w.caster));
        }
        if (adepts.isEmpty() || !(dead instanceof Unit du) || Summons.isSummoned(dead))
            return;
        String deadOwner = du.getOwnerName();
        int xp = xpFor(dead);
        if (xp <= 0 || deadOwner == null || deadOwner.isBlank())
            return;
        double r2 = GraveAdeptUnit.XP_RANGE * GraveAdeptUnit.XP_RANGE;
        for (GraveAdeptUnit a : adepts) {
            if (!a.isAlive() || a.level() != dead.level() || a.distanceToSqr(dead) > r2
                    || AlliancesServerEvents.isAlliedOrOwned(a.getOwnerName(), deadOwner))
                continue;
            addXp(a, xp);
            BarFx.shot(sl, dead.position().add(0, dead.getBbHeight() * 0.5, 0), a.getEyePosition(), BarFx.K_MAGIC, a);
        }
    }

    /** The dead Adept's XP as hero orbs to every hero of the killer's owner within the orbs' reach. */
    private static void burst(ServerLevel sl, GraveAdeptUnit adept, @Nullable Entity killer) {
        int xp = adept.xp();
        if (xp <= 0 || !(killer instanceof Unit ku))
            return;
        String killerOwner = ku.getOwnerName();
        if (killerOwner == null || killerOwner.isBlank() || AlliancesServerEvents.isAlliedOrOwned(killerOwner, adept.getOwnerName()))
            return;   // only an enemy's kill pays out what it stored
        double r2 = HeroExperienceOrb.RANGE * HeroExperienceOrb.RANGE;
        for (LivingEntity le : UnitServerEvents.getAllUnits()) {
            if (!(le instanceof HeroUnit hero) || le.level() != sl || !le.isAlive() || le.distanceToSqr(adept) > r2
                    || !killerOwner.equals(((Unit) le).getOwnerName()) || hero.getHeroLevel() >= HeroUnit.MAX_LEVEL)
                continue;
            for (int left = xp; left > 0; left -= XP_PER_ORB) {
                int part = Math.min(XP_PER_ORB, left);
                // an orb's value is half what it is made with, and a hero takes 10 XP per value
                HeroExperienceOrb orb = HeroExperienceOrb.newOrb(sl, hero, false, adept.getX(), adept.getY() + 1, adept.getZ(), part / 5);
                sl.addFreshEntity(orb);
            }
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent evt) {
        if (evt.phase != TickEvent.Phase.END || evt.getServer() == null || adepts.isEmpty())
            return;
        long now = evt.getServer().overworld().getGameTime();
        for (int i = adepts.size() - 1; i >= 0; i--) {
            GraveAdeptUnit a = adepts.get(i);
            if (a.isRemoved() || !a.isAlive()) {
                // a death paid out in onDeath; an unload stops watching it but it is still its caster's
                // (not isAlive(): a removed LivingEntity is never alive, unloaded or not)
                if (a.getHealth() > 0 && a.getRemovalReason() != null && !a.getRemovalReason().shouldDestroy() && a.casterId() != null)
                    away.put(a.getUUID(), new Away(a.casterId(), a.getOwnerName()));
                adepts.remove(i);
                continue;
            }
            if (!(a.level() instanceof ServerLevel sl))
                continue;
            int level = a.adeptLevel();
            if (level >= 1 && now >= a.nextRaise()) {
                a.setNextRaise(now + GraveAdeptUnit.RAISE_EVERY_SECONDS * 20L);
                raise(sl, a, level, now);
            }
            if (level >= GraveAdeptUnit.BOLT_LEVEL && now >= a.nextBolt())
                bolt(sl, a, now);
        }
    }

    private static void raise(ServerLevel sl, GraveAdeptUnit a, int level, long now) {
        int room = level - raisedBy(a).size();
        if (room <= 0)
            return;
        int n;
        if (TombcallerServerEvents.onCreep(sl, a.position(), a.getOwnerName())) {
            n = room;
        } else if (level >= GraveAdeptUnit.ESSENCE_RAISE_LEVEL) {
            n = Math.min(room, Essence.get(a) / GraveAdeptUnit.ESSENCE_PER_SKELETON);
            if (n <= 0 || !Essence.trySpend(a, n * GraveAdeptUnit.ESSENCE_PER_SKELETON))
                return;
        } else {
            return;
        }
        for (int k = 0; k < n; k++)
            TombcallerServerEvents.raiseSkeleton(sl, a, a.getOwnerName(), GraveAdeptUnit.SKELETON_LIFETIME_SECONDS, now);
    }

    private static void bolt(ServerLevel sl, GraveAdeptUnit a, long now) {
        LivingEntity target = null;
        for (LivingEntity le : HeroFx.enemies(sl, a, a.position(), GraveAdeptUnit.BOLT_RANGE))
            if (le instanceof Unit && (target == null || le.distanceToSqr(a) < target.distanceToSqr(a)))
                target = le;
        if (target == null) {
            a.setNextBolt(now + BOLT_LOOK_TICKS);
            return;
        }
        a.setNextBolt(now + GraveAdeptUnit.BOLT_EVERY_SECONDS * 20L);
        target.invulnerableTime = 0;
        target.hurt(sl.damageSources().indirectMagic(a, a), GraveAdeptUnit.BOLT_DAMAGE);
        BarFx.shot(sl, a.getEyePosition(), target.getEyePosition(), BarFx.K_MAGIC, a);
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent evt) {
        adepts.clear();
        away.clear();
    }
}
