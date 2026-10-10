package com.solegendary.reignofnether.ability.abilities;

import com.solegendary.reignofnether.ability.Ability;
import com.solegendary.reignofnether.ability.AbilityClientboundPacket;
import com.solegendary.reignofnether.cursor.CursorClientEvents;
import com.solegendary.reignofnether.hud.buttons.AbilityButton;
import com.solegendary.reignofnether.keybinds.Keybinding;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.tide.TidesServerEvents;
import com.solegendary.reignofnether.unit.Concealment;
import com.solegendary.reignofnether.unit.Relationship;
import com.solegendary.reignofnether.unit.UnitAction;
import com.solegendary.reignofnether.unit.UnitGrid;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Tidewrought Kraken Caller: <b>Tentacle Grab</b> (design/tidewrought_plan.md, slice 3). A kelp-dark tentacle bursts up
 * under one enemy unit within {@link #RANGE} blocks: it takes {@link #DAMAGE}, is dragged up to {@link #DRAG_BLOCKS}
 * blocks toward the caller (never closer than {@link #DRAG_STOP}) and is rooted for {@link #ROOT_TICKS} ticks (the
 * Crypt Tide's root: slowness so heavy it cannot walk). The sea answers better where it already is: if the target or
 * the caller stands in water or on a tidepool, every number grows ({@link #WET_DAMAGE}, {@link #WET_DRAG_BLOCKS},
 * {@link #WET_ROOT_TICKS}).
 * <p>
 * Friends are never grabbed, and nothing too big to haul ({@link #MAX_DRAG_WIDTH} / {@link #MAX_DRAG_HEIGHT}: Ravagers,
 * golems, treants, experimentals) is dragged - those are only rooted, for half as long. Click a unit, or the ground
 * (the nearest visible enemy within {@link #GROUND_PICK_RADIUS} blocks of the click is taken). The Kraken Caller also
 * grabs its own attack target by itself whenever the ability is ready (KrakenCallerUnit), and the bot aims it at the
 * most expensive enemy in reach. {@link #CD_SECONDS} s cooldown; an out-of-range or empty cast costs nothing.
 * <p>
 * The drag is one velocity change (vanilla motion packet), the tentacle a single visual fang (CryptTide's tagged
 * fangs deal no damage of their own) plus a few particles - nothing per tick.
 */
public class TentacleGrab extends Ability {

    public static final int CD_SECONDS = 14;
    public static final int RANGE = 12;
    public static final float DAMAGE = 3f, WET_DAMAGE = 6f;
    public static final double DRAG_BLOCKS = 4.0, WET_DRAG_BLOCKS = 6.0;
    public static final int ROOT_TICKS = 40, WET_ROOT_TICKS = 70;
    public static final int ROOT_AMPLIFIER = CryptTide.ROOT_AMPLIFIER;
    /** A drag never brings the target closer to the caller than this. */
    public static final double DRAG_STOP = 2.0;
    /** Nothing wider or taller than this is dragged (it is still rooted, for half as long). */
    public static final float MAX_DRAG_WIDTH = 1.5f, MAX_DRAG_HEIGHT = 2.5f;
    public static final double GROUND_PICK_RADIUS = 3.0;
    /** Blocks travelled per unit of starting horizontal speed over a 0.42 hop (the Harpooner's pull model). */
    static final double AIR_GLIDE = 7.0;

    private static final List<LivingEntity> scratch = new ArrayList<>();   // grid query results (server thread)

    public TentacleGrab() {
        super(UnitAction.TENTACLE_GRAB, CD_SECONDS * ResourceCost.TICKS_PER_SECOND, RANGE, 0, true, true);
        this.showRangeCircle = true;
    }

    @Override
    public AbilityButton getButton(Keybinding hotkey, Unit unit) {
        return new AbilityButton("Tentacle Grab",
            ResourceLocation.fromNamespaceAndPath("minecraft", "textures/item/ink_sac.png"),
            hotkey,
            () -> CursorClientEvents.getLeftClickAction() == UnitAction.TENTACLE_GRAB,
            () -> false,
            () -> true,
            () -> CursorClientEvents.setLeftClickAction(UnitAction.TENTACLE_GRAB),
            null,
            List.of(
                FormattedCharSequence.forward("Tentacle Grab  (" + CD_SECONDS + " s cooldown)", Style.EMPTY.withBold(true)),
                FormattedCharSequence.forward("A tentacle seizes an enemy up to " + RANGE + " blocks away: drags it "
                    + (int) DRAG_BLOCKS + " blocks closer and roots it for " + ROOT_TICKS / 20 + " s.", Style.EMPTY),
                FormattedCharSequence.forward("In water or on a tidepool: " + (int) WET_DRAG_BLOCKS + " blocks, "
                    + WET_ROOT_TICKS / 20.0 + " s and double damage. Big beasts are only rooted.", Style.EMPTY),
                FormattedCharSequence.forward("Cast automatically on the caller's target when ready.", Style.EMPTY.withItalic(true))
            ),
            this,
            unit);
    }

    @Override
    public void use(Level level, Unit unitUsing, LivingEntity targetEntity) {
        if (level.isClientSide() || !(level instanceof ServerLevel sl) || !(unitUsing instanceof LivingEntity self))
            return;
        if (!inReach(self, targetEntity) || Concealment.isHiddenFrom(targetEntity, unitUsing.getOwnerName()))
            return;
        if (grab(sl, unitUsing, targetEntity)) {
            this.setToMaxCooldown(unitUsing);
            AbilityClientboundPacket.sendSetCooldownPacket(self.getId(), this.action, this.cooldownMax);
        }
    }

    @Override
    public void use(Level level, Unit unitUsing, BlockPos targetBp) {
        if (level.isClientSide() || !(level instanceof ServerLevel sl) || !(unitUsing instanceof LivingEntity self) || targetBp == null)
            return;
        // the ground form: the nearest visible enemy near the click (also what a click on a hidden unit becomes)
        LivingEntity best = null;
        double bestD = GROUND_PICK_RADIUS * GROUND_PICK_RADIUS;
        double x = targetBp.getX() + 0.5, z = targetBp.getZ() + 0.5;
        for (LivingEntity le : UnitGrid.near(sl, x, z, GROUND_PICK_RADIUS, scratch)) {
            if (!canGrab(unitUsing, le) || !inReach(self, le) || Concealment.isHiddenFrom(le, unitUsing.getOwnerName()))
                continue;
            double d = (le.getX() - x) * (le.getX() - x) + (le.getZ() - z) * (le.getZ() - z);
            if (d < bestD) {
                bestD = d;
                best = le;
            }
        }
        if (best != null)
            use(level, unitUsing, best);
    }

    /** Within cast range of the caller (flat distance, a generous height window). */
    public static boolean inReach(LivingEntity self, @Nullable LivingEntity target) {
        if (target == null)
            return false;
        double dx = target.getX() - self.getX(), dz = target.getZ() - self.getZ();
        return dx * dx + dz * dz <= (RANGE + 0.5) * (RANGE + 0.5) && Math.abs(target.getY() - self.getY()) <= 8;
    }

    /** Can this be grabbed at all: alive, in the caller's level, not the caller, not a friend (own or allied). */
    public static boolean canGrab(Unit caller, @Nullable LivingEntity target) {
        if (target == null || target == caller || !target.isAlive() || !(caller instanceof LivingEntity self)
                || target.level() != self.level())
            return false;
        return UnitServerEvents.getUnitToEntityRelationship(caller, target) != Relationship.FRIENDLY;
    }

    /** Too big to drag (it is still rooted). */
    public static boolean tooBigToDrag(LivingEntity target) {
        return target.getBbWidth() > MAX_DRAG_WIDTH || target.getBbHeight() > MAX_DRAG_HEIGHT;
    }

    /**
     * The grab itself, with no range or cooldown handling: hurts, drags (unless too big) and roots {@code target},
     * stronger when either side is wet. Returns whether it grabbed. Public for the game test and the bot.
     */
    public static boolean grab(ServerLevel sl, Unit caller, LivingEntity target) {
        if (!canGrab(caller, target))
            return false;
        LivingEntity self = (LivingEntity) caller;
        boolean wet = TidesServerEvents.isWet(target) || TidesServerEvents.isWet(self);
        // indirect magic, not mobAttack: RoN rewrites mob-attack damage to the attacker's melee damage
        target.hurt(sl.damageSources().indirectMagic(self, self), wet ? WET_DAMAGE : DAMAGE);
        boolean big = tooBigToDrag(target);
        if (!big) {
            double dx = self.getX() - target.getX(), dz = self.getZ() - target.getZ();
            double dist = Math.sqrt(dx * dx + dz * dz);
            double drag = Math.min(wet ? WET_DRAG_BLOCKS : DRAG_BLOCKS, dist - DRAG_STOP);
            if (drag > 0.25 && dist > 1e-3) {
                double kbRes = target.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
                double v = drag / AIR_GLIDE * Math.max(0.25, 1.0 - kbRes);
                target.setDeltaMovement(dx / dist * v, 0.42, dz / dist * v);
                target.hasImpulse = true;
                target.hurtMarked = true;   // the vanilla motion packet: clients see the haul at once
                if (target instanceof Mob mob)
                    mob.getNavigation().stop();
            }
        }
        int root = wet ? WET_ROOT_TICKS : ROOT_TICKS;
        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, big ? root / 2 : root, ROOT_AMPLIFIER));
        // the tentacle: one tagged (harmless) fang where it burst up, ink and bubbles, a wet slap
        CryptTide.spawnFang(sl, self, target.getX(), target.getY(), target.getZ(), 0);
        sl.sendParticles(ParticleTypes.SQUID_INK, target.getX(), target.getY() + 0.6, target.getZ(), 12, 0.3, 0.4, 0.3, 0.05);
        sl.sendParticles(ParticleTypes.BUBBLE_POP, target.getX(), target.getY() + 0.2, target.getZ(), 10, 0.4, 0.2, 0.4, 0.05);
        sl.sendParticles(ParticleTypes.NAUTILUS, self.getX(), self.getY() + 1.8, self.getZ(), 5, 0.3, 0.3, 0.3, 0.4);
        sl.playSound(null, target.blockPosition(), SoundEvents.SQUID_SQUIRT, SoundSource.NEUTRAL, 1.6f, 0.6f);
        sl.playSound(null, target.blockPosition(), SoundEvents.FISHING_BOBBER_RETRIEVE, SoundSource.NEUTRAL, 1.2f, 0.6f);
        return true;
    }
}
