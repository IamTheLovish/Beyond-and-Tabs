package com.solegendary.reignofnether.player;

import com.solegendary.reignofnether.ability.AutocastState;
import com.solegendary.reignofnether.ability.abilities.SkywardShotAbility;
import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.barfx.BarFx;
import com.solegendary.reignofnether.faction.Faction;
import com.solegendary.reignofnether.faction.FactionTraits;
import com.solegendary.reignofnether.faction.Factions;
import com.solegendary.reignofnether.unit.Concealment;
import com.solegendary.reignofnether.unit.EngagementStance;
import com.solegendary.reignofnether.unit.UnitGrid;
import com.solegendary.reignofnether.unit.interfaces.Unit;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
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
 * <b>A second weapon</b> beside the commander's own attack: every {@link #COOLDOWN_TICKS} it takes a shot at an enemy
 * flyer within {@link #RANGE} (the flyer it was ordered to attack first, else the nearest) for {@link #DAMAGE}, ~13
 * dmg/s - a scout falls in two shots, a 90-hp T2 gunship in ~7 s, a raid of two in ~14 s while they deal the commander
 * ~6 dmg/s. A light raid is stopped; four or more still need anti-air.
 * <p>
 * <b>Part of the commander, not a turret on it</b> (lovish 03:23, "how will it be cohesive to the commander" - approved):
 * <ul>
 *   <li>a <b>gesture</b>: the commander turns its head to the flyer and swings its off hand (the weapon hand keeps
 *       fighting), with a glow at the hand, then the shot leaves {@link #WINDUP_TICKS} later from that hand;</li>
 *   <li>a <b>look and sound from its own kit</b> ({@link Style}): the Lord Marshal throws a sunbolt, the Warlord an axe,
 *       the Admiral a harpoon, the Thunder Sage a sky spark... tinted with the faction's colour (BarFx);</li>
 *   <li>a <b>card entry</b> ({@link SkywardShotAbility}, passive): players see it and can switch it off; Hold Fire stops
 *       it too, like every other autonomous weapon.</li>
 * </ul>
 * The carried props (an axe on the belt, a harpoon on the back) and a real throw animation are Lane B's (posted on #35).
 * Generic damage, as the towers deal: the commander's own attack attribute (LivingEntityMixin) never inflates it.
 * <p>
 * Cost: commanders are tracked in a weak map (one per player) and checked every tick; a grid query only when one is
 * ready to shoot.
 */
public final class CommanderAntiAir {
    private CommanderAntiAir() { }

    public static final int RANGE = 14;
    public static final float DAMAGE = 10f;
    /** Shot to shot (the wind-up is inside it). */
    public static final int COOLDOWN_TICKS = 15;
    /** From the gesture to the shot leaving the hand. */
    public static final int WINDUP_TICKS = 6;
    /** A flyer that moved this much past RANGE during the wind-up is still hit (the throw was already on its way). */
    static final double WINDUP_SLACK = 2;

    /**
     * One faction's anti-air look, from its commander's kit.
     *
     * @param kind     the BarFx projectile shape (the client tints it with the faction)
     * @param weapon   BarFx weapon-sound class (W_GUN for a shot that must not sound like a bow)
     * @param handGlow particles at the hand during the wind-up
     * @param name     the card name (lang key under ability.reignofnether.skyward_shot.)
     * @param icon     the card icon (textures/icons/items/)
     */
    public record Style(byte kind, byte weapon, SoundEvent sound, float pitch, ParticleOptions handGlow, String name, String icon) { }

    public static final Style SUN = new Style(BarFx.K_MAGIC, BarFx.W_BY_KIND, SoundEvents.EVOKER_CAST_SPELL, 1.6f, ParticleTypes.END_ROD, "sunbolt", "sparkler");
    public static final Style SOUL = new Style(BarFx.K_MAGIC, BarFx.W_BY_KIND, SoundEvents.SOUL_ESCAPE, 1.0f, ParticleTypes.SOUL_FIRE_FLAME, "soul_lash", "sparkler");
    public static final Style AXE = new Style(BarFx.K_THROWN, BarFx.W_BY_KIND, SoundEvents.TRIDENT_THROW, 0.8f, ParticleTypes.SMALL_FLAME, "throwing_axe", "sword_and_bow");
    public static final Style THORN = new Style(BarFx.K_THROWN, BarFx.W_BY_KIND, SoundEvents.SWEET_BERRY_BUSH_PICK_BERRIES, 0.8f, ParticleTypes.COMPOSTER, "thorn_burst", "bow");
    public static final Style HARPOON = new Style(BarFx.K_THROWN, BarFx.W_BY_KIND, SoundEvents.TRIDENT_THROW, 1.1f, ParticleTypes.SPLASH, "harpoon", "trident");
    public static final Style SPARK = new Style(BarFx.K_MAGIC, BarFx.W_BY_KIND, SoundEvents.TRIDENT_THUNDER, 1.8f, ParticleTypes.ELECTRIC_SPARK, "sky_spark", "sparkler");
    public static final Style FLAK_PISTOL = new Style(BarFx.K_ARROW, BarFx.W_GUN, SoundEvents.FIREWORK_ROCKET_BLAST, 1.2f, ParticleTypes.SMOKE, "flak_pistol", "crossbow");

    /** The faction's style (an unknown faction gets the sunbolt). */
    public static Style styleOf(@Nullable Faction f) {
        if (f == null)
            return SUN;
        if (f.equals(Factions.MONSTERS))
            return SOUL;
        if (f.equals(Factions.PIGLINS))
            return AXE;
        if (f.equals(Factions.VERDANT_COURT))
            return THORN;
        if (f.equals(Factions.TIDEWROUGHT))
            return HARPOON;
        if (f.equals(Factions.STORMCALLERS))
            return SPARK;
        if (f.equals(Factions.BRASSWORKS))
            return FLAK_PISTOL;
        return SUN;
    }

    /** A commander's weapon state: when it may next start a gesture, and the throw on its way (if any). */
    static final class State {
        long readyAt = 0;
        @Nullable LivingEntity pending = null;
        long fireAt = 0;
    }

    private static final Map<LivingEntity, State> COMMANDERS = new WeakHashMap<>();
    private static final List<LivingEntity> scratch = new ArrayList<>();

    /** Starts tracking a commander (CommanderServerEvents.makeCommander and onJoin, server side). */
    public static void track(LivingEntity commander) {
        if (!commander.level().isClientSide())
            COMMANDERS.computeIfAbsent(commander, c -> new State());
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent evt) {
        if (evt.phase != TickEvent.Phase.END || COMMANDERS.isEmpty())
            return;
        MinecraftServer server = evt.getServer();
        if (server == null)
            return;
        List<LivingEntity> gone = null;
        for (Map.Entry<LivingEntity, State> e : COMMANDERS.entrySet()) {
            LivingEntity c = e.getKey();
            if (c == null || c.isRemoved() || !c.isAlive()) {
                if (gone == null)
                    gone = new ArrayList<>();
                gone.add(c);
                continue;
            }
            if (c.level() instanceof ServerLevel sl)
                tick(sl, c, e.getValue());
        }
        if (gone != null)
            for (LivingEntity c : gone)
                COMMANDERS.remove(c);
    }

    /** One commander's tick: let a throw on its way land, or start the next gesture when ready. */
    static void tick(ServerLevel sl, LivingEntity c, State st) {
        long now = sl.getGameTime();
        if (st.pending != null) {
            if (now < st.fireAt)
                return;
            LivingEntity t = st.pending;
            st.pending = null;
            String owner = c instanceof Unit u ? u.getOwnerName() : null;
            double reach = RANGE + WINDUP_SLACK;
            if (owner != null && isFoeFlyer(t, owner) && t.distanceToSqr(c) <= reach * reach && mayShoot(c))
                fire(sl, c, t);
            return;
        }
        if (now < st.readyAt || !mayShoot(c))
            return;
        LivingEntity target = pickTarget(sl, c);
        if (target == null)
            return;
        gesture(sl, c, target);
        st.pending = target;
        st.fireAt = now + WINDUP_TICKS;
        st.readyAt = now + COOLDOWN_TICKS;
    }

    /** Switched on on its card and not holding fire. */
    public static boolean mayShoot(LivingEntity c) {
        if (EngagementStance.of(c) == EngagementStance.HOLD_FIRE)
            return false;
        return !(c instanceof Unit u) || !SkywardShotAbility.has(u) || AutocastState.isOn(u, SkywardShotAbility.INSTANCE);
    }

    /** The wind-up: head to the flyer, off hand up, a glow at that hand. */
    static void gesture(ServerLevel sl, LivingEntity c, LivingEntity target) {
        if (c instanceof Mob mob)
            mob.getLookControl().setLookAt(target, 30f, 30f);
        c.swing(InteractionHand.OFF_HAND, true);
        Vec3 hand = hand(c);
        Style style = styleOf(c instanceof Unit u ? Factions.getFaction(u) : null);
        sl.sendParticles(style.handGlow(), hand.x, hand.y, hand.z, 6, 0.12, 0.12, 0.12, 0.01);
    }

    /**
     * The off hand, raised for the throw: beside the shoulder on the body's left (yaw 0 faces +z, so its left is +x),
     * a little ahead, near the top of the head.
     */
    public static Vec3 hand(LivingEntity c) {
        double yaw = Math.toRadians(c.yBodyRot);
        double fx = -Math.sin(yaw), fz = Math.cos(yaw);   // forward
        double lx = Math.cos(yaw), lz = Math.sin(yaw);    // left
        return new Vec3(c.getX() + lx * 0.35 + fx * 0.2, c.getY() + c.getBbHeight() * 0.95, c.getZ() + lz * 0.35 + fz * 0.2);
    }

    /** One shot now, no wind-up and no cooldown (the game test's entry point). Returns the flyer hit, or null. */
    @Nullable
    public static LivingEntity shoot(ServerLevel sl, LivingEntity commander) {
        LivingEntity target = pickTarget(sl, commander);
        if (target != null)
            fire(sl, commander, target);
        return target;
    }

    /** The shot leaves the hand: tracer in the commander's style and faction tint, its sound, the hit. */
    static void fire(ServerLevel sl, LivingEntity commander, LivingEntity target) {
        Vec3 from = hand(commander);
        Vec3 to = new Vec3(target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ());
        Faction faction = commander instanceof Unit u ? Factions.getFaction(u) : null;
        Style style = styleOf(faction);
        byte tint = faction == null ? BarFx.F_NONE : FactionTraits.of(faction).deathDebris;
        BarFx.shot(sl, from, to, style.kind(), style.weapon(), tint);
        sl.playSound(null, BlockPos.containing(from), style.sound(), SoundSource.NEUTRAL, 0.5f, style.pitch());
        target.hurt(sl.damageSources().generic(), DAMAGE);
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
