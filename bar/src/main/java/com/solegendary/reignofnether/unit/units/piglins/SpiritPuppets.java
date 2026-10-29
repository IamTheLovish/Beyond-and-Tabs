package com.solegendary.reignofnether.unit.units.piglins;

import com.solegendary.reignofnether.ability.abilities.SpiritPuppet;
import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * The Voodoo Shaman's Spirit Puppets ({@link SpiritPuppet}): a taken unit's original owner and the game tick it goes
 * back are kept in its persistent data ({@link #KEY_OWNER}, {@link #KEY_UNTIL}), so a save in the middle of the 15 s
 * cannot leave it with the puppeteer for good; this class holds the live list and hands each one back on time.
 * <p>
 * A hand-over either way is the Wraith's Possess one: the unit's orders are dropped (Unit.fullResetBehaviours), and the
 * units of its new side that were attacking it let go. The client drops it from any selection when its owner changes
 * (UnitClientEvents, SelectionKeep.ownerFlipped), and the server refuses its old side's orders by owner as for any unit.
 * A puppet that dies is a normal death - it is not a summon, so it drops its essence wisp - and its record goes with it.
 * Server side only.
 */
public final class SpiritPuppets {
    private SpiritPuppets() { }

    public static final String KEY_OWNER = "bt_puppet_owner";
    public static final String KEY_UNTIL = "bt_puppet_until";

    private static final List<LivingEntity> ACTIVE = new ArrayList<>();

    public static boolean isPuppet(LivingEntity e) {
        return e != null && e.getPersistentData().contains(KEY_OWNER);
    }

    /** Whose unit a puppet really is (null for anything that is not a puppet). */
    public static String originalOwner(LivingEntity e) {
        return isPuppet(e) ? e.getPersistentData().getString(KEY_OWNER) : null;
    }

    /** Whether the live list holds {@code e} (the tests: a dead puppet must be forgotten). */
    public static boolean tracks(LivingEntity e) {
        return ACTIVE.contains(e);
    }

    public static int activeCount() {
        return ACTIVE.size();
    }

    /** Gives {@code target} to {@code newOwner} for {@code ticks}; the caller checked everything (SpiritPuppet.cast). */
    public static void take(ServerLevel sl, LivingEntity target, String newOwner, int ticks) {
        if (!(target instanceof Unit unit))
            return;
        CompoundTag data = target.getPersistentData();
        data.putString(KEY_OWNER, unit.getOwnerName());
        data.putLong(KEY_UNTIL, sl.getGameTime() + ticks);
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
    }

    /** The Wraith's possession hand-over (WraithUnit.onCastPossess): new owner, no orders, its new side lets go of it. */
    private static void handOver(Unit unit, String owner) {
        LivingEntity le = (LivingEntity) unit;
        unit.setOwnerName(owner);
        unit.setAnchor(null);
        Unit.fullResetBehaviours(unit);
        for (LivingEntity other : UnitServerEvents.getAllUnits())
            if (other != le && other instanceof Unit u && u.getTargetGoal() != null && u.getTargetGoal().getTarget() == le
                    && AlliancesServerEvents.isAlliedOrOwned(u.getOwnerName(), owner))
                u.getTargetGoal().setTarget(null);
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
            if (e.level().getGameTime() >= e.getPersistentData().getLong(KEY_UNTIL))
                giveBack(e);
        }
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
