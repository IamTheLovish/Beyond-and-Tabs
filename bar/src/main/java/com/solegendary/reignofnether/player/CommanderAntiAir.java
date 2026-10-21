package com.solegendary.reignofnether.player;

import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.barfx.BarFx;
import com.solegendary.reignofnether.faction.Faction;
import com.solegendary.reignofnether.faction.FactionTraits;
import com.solegendary.reignofnether.faction.Factions;
import com.solegendary.reignofnether.unit.Concealment;
import com.solegendary.reignofnether.unit.UnitGrid;
import com.solegendary.reignofnether.unit.interfaces.Unit;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Every commander shoots at flyers (lovish Oct 21 03:18: "commanders should be able to hit antiair ... maybe not like in
 * bar ... they definitely should be able to shoot air, maybe not completely massacre air but be able to stop things
 * early game like a light swarm of air units"). Before this a commander - a melee worker - could not touch a flyer at
 * all, and in his playtest fast flyers ran through his army and sniped it.
 * <p>
 * A separate weapon from the commander's own attack: while it fights on the ground, every {@link #COOLDOWN_TICKS} it
 * also takes a shot at an enemy flyer within {@link #RANGE} (the flyer it was ordered to attack first, else the
 * nearest). {@link #DAMAGE} a shot (~13 dmg/s): a scout falls in two shots, a 90-hp T2 gunship in ~7 s, a raid of two
 * in ~14 s while they deal the commander ~6 dmg/s - a light raid is stopped; four or more still need anti-air. Instant, like the towers' shots, drawn in the faction's tint (BarFx).
 * Generic damage, as the towers deal: the commander's own attack attribute (LivingEntityMixin) never inflates it.
 * <p>
 * Cost: commanders are tracked in a weak map (one per player), scanned every {@link #SCAN_TICKS}; one grid query each.
 */
public final class CommanderAntiAir {
    private CommanderAntiAir() { }

    public static final int RANGE = 14;
    public static final float DAMAGE = 10f;
    public static final int COOLDOWN_TICKS = 15;
    static final int SCAN_TICKS = 5;

    /** Live commanders (server side) and the game time each may next fire. Weak: a removed commander drops out. */
    private static final Map<LivingEntity, Long> COMMANDERS = new WeakHashMap<>();
    private static final List<LivingEntity> scratch = new ArrayList<>();

    /** Starts tracking a commander (CommanderServerEvents.makeCommander and onJoin, server side). */
    public static void track(LivingEntity commander) {
        if (!commander.level().isClientSide())
            COMMANDERS.putIfAbsent(commander, 0L);
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent evt) {
        if (evt.phase != TickEvent.Phase.END || COMMANDERS.isEmpty())
            return;
        MinecraftServer server = evt.getServer();
        if (server == null || server.getTickCount() % SCAN_TICKS != 0)
            return;
        List<LivingEntity> dead = null;
        for (Map.Entry<LivingEntity, Long> e : COMMANDERS.entrySet()) {
            LivingEntity c = e.getKey();
            if (c == null || c.isRemoved() || !c.isAlive()) {
                if (dead == null)
                    dead = new ArrayList<>();
                dead.add(c);
                continue;
            }
            if (!(c.level() instanceof ServerLevel sl))
                continue;
            long now = sl.getGameTime();
            if (now < e.getValue())
                continue;
            if (shoot(sl, c) != null)
                e.setValue(now + COOLDOWN_TICKS);
        }
        if (dead != null)
            for (LivingEntity c : dead)
                COMMANDERS.remove(c);
    }

    /** One anti-air shot from this commander if a target is in reach (no cooldown here). Returns the flyer hit. */
    @Nullable
    public static LivingEntity shoot(ServerLevel sl, LivingEntity commander) {
        LivingEntity target = pickTarget(sl, commander);
        if (target == null)
            return null;
        Vec3 from = commander.getEyePosition();
        Vec3 to = new Vec3(target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ());
        Faction faction = commander instanceof Unit u ? Factions.getFaction(u) : null;
        byte tint = faction == null ? BarFx.F_NONE : FactionTraits.of(faction).deathDebris;
        BarFx.shot(sl, from, to, BarFx.K_ARROW, BarFx.W_BY_KIND, tint);
        sl.playSound(null, BlockPos.containing(from), SoundEvents.ARROW_SHOOT, SoundSource.NEUTRAL, 0.6f, 1.4f);
        target.hurt(sl.damageSources().generic(), DAMAGE);
        return target;
    }

    /** The flyer it was ordered to attack if that one is in reach, else the nearest enemy flyer in reach, or null. */
    @Nullable
    public static LivingEntity pickTarget(ServerLevel sl, LivingEntity commander) {
        if (!(commander instanceof Unit self))
            return null;
        String owner = self.getOwnerName();
        if (owner == null || owner.isBlank())
            return null;
        double r2 = (double) RANGE * RANGE;
        LivingEntity ordered = self.getTargetGoal() == null ? null : self.getTargetGoal().getTarget();
        if (ordered != null && isFoeFlyer(ordered, owner) && ordered.distanceToSqr(commander) <= r2)
            return ordered;
        LivingEntity best = null;
        double bestD = Double.MAX_VALUE;
        for (LivingEntity le : UnitGrid.near(sl, commander.getX(), commander.getZ(), RANGE, scratch)) {
            if (!isFoeFlyer(le, owner))
                continue;
            double d = le.distanceToSqr(commander);
            if (d <= r2 && d < bestD) {
                bestD = d;
                best = le;
            }
        }
        scratch.clear();
        return best;
    }

    static boolean isFoeFlyer(LivingEntity le, String owner) {
        if (!le.isAlive() || le.isInvisible() || !(le instanceof Unit u) || !u.isFlyingUnit())
            return false;
        String o = u.getOwnerName();
        return o != null && !o.isBlank() && !owner.equals(o) && !AlliancesServerEvents.isAllied(owner, o)
            && !Concealment.isHiddenFrom(le, owner);
    }
}
