package com.solegendary.reignofnether.unit.units.piglins;

import com.solegendary.reignofnether.ability.abilities.SpiritPuppet;
import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.essence.Essence;
import com.solegendary.reignofnether.hero.PossessionServer;
import com.solegendary.reignofnether.match.MatchRoster;
import com.solegendary.reignofnether.registrars.MobEffectRegistrar;
import com.solegendary.reignofnether.unit.GuardServerEvents;
import com.solegendary.reignofnether.unit.PatrolServerEvents;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The Voodoo Shaman's Spirit Puppets ({@link SpiritPuppet}): a taken unit's original owner, the game tick it goes back
 * and the shaman holding it are kept in its persistent data ({@link #KEY_OWNER}, {@link #KEY_UNTIL},
 * {@link #KEY_CASTER}), so a save in the middle of the 15 s cannot leave it with the puppeteer for good; this class holds
 * the live list and hands each one back on time.
 * <p>
 * <b>A channel</b> (Revision 1): the puppet goes back at once when its shaman is hit (any damage that lands), stunned
 * or frozen, more than {@link SpiritPuppet#CHANNEL_RANGE} blocks from it, dead, or gone (not loaded).
 * <p>
 * A hand-over either way is the Wraith's Possess one, made whole: the unit's orders are dropped
 * (Unit.fullResetBehaviours, and its patrol route and guard charge, which that reset leaves), anyone driving it in first
 * person lets go (PossessionServer checks the owner only when control is taken, so a possession would outlive the
 * hand-over), and the units of its new side that were attacking it let go. The client drops it from any selection when its owner changes
 * (UnitClientEvents, SelectionKeep.ownerFlipped), and the server refuses its old side's orders by owner as for any unit.
 * A puppet that dies is a normal death - it is not a summon, so it drops its essence wisp - and its record goes with it.
 * A puppet whose owner was beaten in the meantime (MatchRoster.isFinished: MatchOutcome's strip skipped it, it was the
 * puppeteer's then) is not handed to a finished side: it is stripped and killed as the strip would have done.
 * A puppet takes no population from the puppeteer: it counts for its original owner all along ({@link #populationOwner}).
 * Server side only.
 */
public final class SpiritPuppets {
    private SpiritPuppets() { }

    public static final String KEY_OWNER = "bt_puppet_owner";
    public static final String KEY_UNTIL = "bt_puppet_until";
    public static final String KEY_CASTER = "bt_puppet_caster";

    private static final List<LivingEntity> ACTIVE = new ArrayList<>();

    public static boolean isPuppet(LivingEntity e) {
        return e != null && e.getPersistentData().contains(KEY_OWNER);
    }

    /** Whose unit a puppet really is (null for anything that is not a puppet). */
    public static String originalOwner(LivingEntity e) {
        return isPuppet(e) ? e.getPersistentData().getString(KEY_OWNER) : null;
    }

    /** Whether the live list holds {@code e} (the tests: a dead or returned puppet must be forgotten). */
    public static boolean tracks(LivingEntity e) {
        return ACTIVE.contains(e);
    }

    public static int activeCount() {
        return ACTIVE.size();
    }

    /**
     * Whose population {@code unit} counts in (UnitServerEvents.getUnitPopulation): a puppet's original owner's, as if
     * it had never left (lovish, Oct 29: a steal takes no population from the Shaman's owner - no cap check, no change);
     * anything else its owner's.
     */
    public static String populationOwner(LivingEntity e, Unit unit) {
        String original = originalOwner(e);
        return original != null ? original : unit.getOwnerName();
    }

    /**
     * Whether Spirit Puppet may take {@code target} at all, beyond tier and side: nobody drives it in first person, it
     * is not garrisoned (it would fight for the puppeteer from its owner's tower), not riding or carrying anything, and
     * not a summon (a totem's aura would change sides).
     */
    public static boolean mayTake(LivingEntity target) {
        return target instanceof Unit u && !PossessionServer.isPossessed(target) && !u.isGarrisoned()
            && !target.isPassenger() && !target.isVehicle() && !Essence.isSummoned(target);
    }

    /**
     * Gives {@code target} to {@code newOwner} for {@code ticks} while {@code caster} keeps the channel; the caller
     * checked everything (SpiritPuppet.cast).
     */
    public static void take(ServerLevel sl, LivingEntity caster, LivingEntity target, String newOwner, int ticks) {
        if (!(target instanceof Unit unit))
            return;
        CompoundTag data = target.getPersistentData();
        data.putString(KEY_OWNER, unit.getOwnerName());
        data.putLong(KEY_UNTIL, sl.getGameTime() + ticks);
        data.putUUID(KEY_CASTER, caster.getUUID());
        handOver(unit, newOwner);
        if (!ACTIVE.contains(target))
            ACTIVE.add(target);
        sl.sendParticles(ParticleTypes.SOUL, target.getX(), target.getY() + target.getBbHeight() * 0.6, target.getZ(),
            20, 0.3, 0.5, 0.3, 0.03);
    }

    /** Back to its original owner, orders cleared, record gone. */
    public static void giveBack(LivingEntity puppet) {
        String owner = originalOwner(puppet);
        ACTIVE.remove(puppet);
        if (owner == null)
            return;
        forget(puppet);
        if (puppet instanceof Unit unit && puppet.isAlive() && MatchRoster.isFinished(owner)) {
            handOver(unit, "");   // MatchOutcome.stripOwnership's way: nobody's, then gone
            puppet.kill();
            return;
        }
        if (puppet instanceof Unit unit && puppet.isAlive()) {
            handOver(unit, owner);
            if (puppet.level() instanceof ServerLevel sl)
                sl.sendParticles(ParticleTypes.SOUL, puppet.getX(), puppet.getY() + puppet.getBbHeight() * 0.6, puppet.getZ(),
                    12, 0.3, 0.5, 0.3, 0.02);
        }
    }

    private static void forget(LivingEntity e) {
        e.getPersistentData().remove(KEY_OWNER);
        e.getPersistentData().remove(KEY_UNTIL);
        e.getPersistentData().remove(KEY_CASTER);
    }

    /** The Wraith's possession hand-over (WraithUnit.onCastPossess): new owner, no orders, its new side lets go of it. */
    private static void handOver(Unit unit, String owner) {
        LivingEntity le = (LivingEntity) unit;
        PossessionServer.Possession driven = PossessionServer.ofUnit(le.getId());
        if (driven != null)
            PossessionServer.release(driven.player, "The spirits took that unit from you.");
        PatrolServerEvents.clear(le.getId());
        GuardServerEvents.clear(le.getId());
        unit.setOwnerName(owner);
        unit.setAnchor(null);
        Unit.fullResetBehaviours(unit);
        for (LivingEntity other : UnitServerEvents.getAllUnits())
            if (other != le && other instanceof Unit u && u.getTargetGoal() != null && u.getTargetGoal().getTarget() == le
                    && AlliancesServerEvents.isAlliedOrOwned(u.getOwnerName(), owner))
                u.getTargetGoal().setTarget(null);
    }

    /** The shaman holding the channel, or null when it is dead, unloaded or the record names none. */
    private static LivingEntity casterOf(LivingEntity puppet) {
        CompoundTag data = puppet.getPersistentData();
        if (!data.hasUUID(KEY_CASTER) || !(puppet.level() instanceof ServerLevel sl))
            return null;
        Entity e = sl.getEntity(data.getUUID(KEY_CASTER));
        return e instanceof LivingEntity le && le.isAlive() ? le : null;
    }

    /** Whether the channel still holds: the shaman alive, not stunned or frozen, within reach of its puppet. */
    private static boolean channelHolds(LivingEntity puppet) {
        LivingEntity caster = casterOf(puppet);
        if (caster == null || caster.hasEffect(MobEffectRegistrar.STUN.get()) || caster.hasEffect(MobEffectRegistrar.FREEZE.get()))
            return false;
        double r = SpiritPuppet.CHANNEL_RANGE;
        return caster.distanceToSqr(puppet) <= r * r;
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent evt) {
        if (evt.phase != TickEvent.Phase.END || ACTIVE.isEmpty())
            return;
        for (LivingEntity e : new ArrayList<>(ACTIVE)) {
            if (e.isRemoved() || !e.isAlive() || !isPuppet(e)) {
                ACTIVE.remove(e);   // dead, given back elsewhere, or unloaded (onJoin lists it again when it loads)
                continue;
            }
            if (e.level().getGameTime() >= e.getPersistentData().getLong(KEY_UNTIL) || !channelHolds(e))
                giveBack(e);
        }
    }

    /** A hit on a shaman that lands breaks every channel it holds. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onHurt(LivingHurtEvent evt) {
        LivingEntity hurt = evt.getEntity();
        if (ACTIVE.isEmpty() || evt.isCanceled() || evt.getAmount() <= 0 || hurt.level().isClientSide())
            return;
        UUID id = hurt.getUUID();
        for (LivingEntity puppet : new ArrayList<>(ACTIVE)) {
            CompoundTag data = puppet.getPersistentData();
            if (data.hasUUID(KEY_CASTER) && id.equals(data.getUUID(KEY_CASTER)))
                giveBack(puppet);
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent evt) {
        ACTIVE.clear();
    }

    /** A puppet loaded from a save (or back in a loaded chunk) is listed again; one past its time goes back next tick. */
    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent evt) {
        if (!evt.getLevel().isClientSide() && evt.getEntity() instanceof LivingEntity le && isPuppet(le) && !ACTIVE.contains(le))
            ACTIVE.add(le);
    }

    /** A puppet that dies is a normal death (its wisp drops in EssenceServerEvents); only the record goes. */
    @SubscribeEvent(priority = EventPriority.LOWEST)   // after any save from death (a cancelled death keeps its record)
    public static void onDeath(LivingDeathEvent evt) {
        LivingEntity e = evt.getEntity();
        if (e.level().isClientSide() || !isPuppet(e))
            return;
        ACTIVE.remove(e);
        forget(e);
    }
}
