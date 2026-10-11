package com.solegendary.reignofnether.ability.abilities;

import com.solegendary.reignofnether.ability.Ability;
import com.solegendary.reignofnether.ability.AbilityClientboundPacket;
import com.solegendary.reignofnether.cursor.CursorClientEvents;
import com.solegendary.reignofnether.hud.buttons.AbilityButton;
import com.solegendary.reignofnether.keybinds.Keybinding;
import com.solegendary.reignofnether.mana.ManaServerEvents;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.unit.UnitAction;
import com.solegendary.reignofnether.unit.interfaces.Unit;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Stormcallers Earthshaker: <b>Ground Slam</b> (design/stormcallers_plan.md, slice 3 - the plan's "Rift"). Aim at the
 * ground: a {@link #LENGTH}-block crack runs from under the golem toward the spot. For {@link #TELEGRAPH_TICKS} ticks
 * stone dust marks the line (BAR rule: the danger is shown first), then the earth heaves - {@link #DAMAGE} (x the cast's
 * power: Overcharge, a Mana Well) and a root for {@link #ROOT_TICKS} ticks (the Thornbow's Slowness VII: it cannot walk)
 * to every enemy on the line, with a small knock-up. Friends are never touched. The line is fixed at cast time
 * (MagmaRupture's rule), so what the enemy sees is what gets hit. No block is changed: a "split" that edited terrain
 * would break pathing and buildings. {@link #ENERGY_COST} energy through the Mana core (refused = nothing paid, no
 * cooldown), {@link #CD_SECONDS} s cooldown.
 */
public class GroundSlam extends Ability {

    public static final int CD_SECONDS = 25;
    public static final int LENGTH = 8;
    public static final float HALF_WIDTH = 1.5f;
    public static final float DAMAGE = 12f;
    public static final int ROOT_TICKS = 2 * 20;
    public static final int ROOT_AMPLIFIER = Rootquake.ROOT_AMPLIFIER;
    public static final int TELEGRAPH_TICKS = 20;
    public static final int ENERGY_COST = 60;

    public GroundSlam() {
        super(UnitAction.GROUND_SLAM, CD_SECONDS * ResourceCost.TICKS_PER_SECOND, LENGTH, 0, false, true);
        this.showRangeLine = true;
        this.showRadiusCircle = false;
        this.showRangeCircle = true;
    }

    @Override
    public AbilityButton getButton(Keybinding hotkey, Unit unit) {
        return new AbilityButton("Ground Slam",
            ResourceLocation.fromNamespaceAndPath("minecraft", "textures/block/cracked_deepslate_bricks.png"),
            hotkey,
            () -> CursorClientEvents.getLeftClickAction() == UnitAction.GROUND_SLAM,
            () -> false,
            () -> true,
            () -> CursorClientEvents.setLeftClickAction(UnitAction.GROUND_SLAM),
            null,
            List.of(
                FormattedCharSequence.forward("Ground Slam  (" + ENERGY_COST + " energy, " + CD_SECONDS + " s cooldown)", Style.EMPTY.withBold(true)),
                FormattedCharSequence.forward("Cracks the ground in an " + LENGTH + "-block line. After 1 s it heaves: "
                    + (int) DAMAGE + " damage and a " + ROOT_TICKS / 20 + " s root to enemies on it.", Style.EMPTY),
                FormattedCharSequence.forward("The dust marks the line - enemies can step off it.", Style.EMPTY.withItalic(true))
            ),
            this,
            unit);
    }

    @Override
    public void use(Level level, Unit unitUsing, LivingEntity targetEntity) {
        if (targetEntity != null)
            use(level, unitUsing, targetEntity.getOnPos());
    }

    @Override
    public void use(Level level, Unit unitUsing, BlockPos targetBp) {
        if (level.isClientSide() || !(unitUsing instanceof LivingEntity self) || !(level instanceof ServerLevel sl) || targetBp == null)
            return;
        if (cast(sl, self, unitUsing.getOwnerName(), targetBp, false)) {
            this.setToMaxCooldown(unitUsing);
            AbilityClientboundPacket.sendSetCooldownPacket(self.getId(), this.action, this.cooldownMax);
        }
    }

    /**
     * Pays and schedules the slam toward {@code aim}. Returns false when refused (nothing paid). Public for the bot
     * and the game test (no cooldown handling here).
     */
    public static boolean cast(ServerLevel sl, LivingEntity self, String owner, BlockPos aim, boolean quiet) {
        if (owner == null || aim == null)
            return false;
        float power = quiet ? ManaServerEvents.tryCastQuiet(self, owner, ENERGY_COST) : ManaServerEvents.tryCast(self, owner, ENERGY_COST);
        if (power <= 0)
            return false;
        // the crack runs along the ground the golem stands on (not the heightmap: under a roof that is the ceiling)
        Vec3 from = new Vec3(self.getX(), Math.floor(self.getY()), self.getZ());
        Vec3 flat = new Vec3(aim.getX() + 0.5 - from.x, 0, aim.getZ() + 0.5 - from.z);
        Vec3 dir = flat.lengthSqr() < 0.01 ? self.getLookAngle().multiply(1, 0, 1).normalize() : flat.normalize();
        Vec3 to = from.add(dir.scale(LENGTH));
        telegraph(sl, from, dir);
        sl.playSound(null, BlockPos.containing(from), SoundEvents.IRON_GOLEM_ATTACK, SoundSource.NEUTRAL, 2f, 0.6f);
        // the tick scheduler, not MinecraftServer.tell: a TickTask runs as soon as the server has spare time
        com.solegendary.reignofnether.taskscheduler.TaskSchedulerServerEvents.schedule(TELEGRAPH_TICKS / 2, () -> telegraph(sl, from, dir));
        com.solegendary.reignofnether.taskscheduler.TaskSchedulerServerEvents.schedule(TELEGRAPH_TICKS, () -> {
            if (self.isAlive())   // a golem broken mid-cast never finishes the slam
                heave(sl, self, owner, from, to, power);
        });
        return true;
    }

    static void telegraph(ServerLevel sl, Vec3 from, Vec3 dir) {
        BlockParticleOption dust = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.STONE.defaultBlockState());
        for (int i = 1; i <= LENGTH; i++) {
            Vec3 at = from.add(dir.scale(i));
            sl.sendParticles(dust, at.x, at.y + 0.2, at.z, 2, 0.4, 0.05, 0.4, 0.02);
            if (i % 2 == 0)
                sl.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, at.x, at.y + 0.3, at.z, 1, 0.3, 0.1, 0.3, 0.0);
        }
    }

    /**
     * The heave: {@link #DAMAGE} x power, a knock-up and a root to every hostile unit within {@link #HALF_WIDTH} of
     * the line. Returns how many were caught. Public for the game test.
     */
    public static int heave(ServerLevel sl, LivingEntity self, String owner, Vec3 from, Vec3 to, float power) {
        int hit = 0;
        for (LivingEntity le : CommanderDGun.hits(self, owner, from, to, HALF_WIDTH)) {
            if (le instanceof Unit u && u.isFlyingUnit())
                continue;   // the ground heaves; the sky doesn't
            // indirect magic, not mobAttack: RoN rewrites mob-attack damage to the attacker's melee damage
            le.hurt(sl.damageSources().indirectMagic(self, self), DAMAGE * power);
            if (!le.isAlive())
                continue;
            le.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ROOT_TICKS, ROOT_AMPLIFIER));
            le.setDeltaMovement(le.getDeltaMovement().x, 0.35, le.getDeltaMovement().z);
            le.hasImpulse = true;
            le.hurtMarked = true;   // the vanilla motion packet: clients see the heave at once
            hit++;
        }
        Vec3 seg = to.subtract(from);
        BlockParticleOption dust = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.COBBLESTONE.defaultBlockState());
        for (int i = 1; i <= LENGTH; i++) {
            Vec3 at = from.add(seg.scale(i / (double) LENGTH));
            sl.sendParticles(dust, at.x, at.y + 0.3, at.z, 6, 0.4, 0.3, 0.4, 0.08);
        }
        Vec3 mid = from.add(seg.scale(0.5));
        com.solegendary.reignofnether.barfx.BarFx.heavyImpact(sl, mid, 2f);
        sl.playSound(null, BlockPos.containing(mid), SoundEvents.GENERIC_EXPLODE, SoundSource.NEUTRAL, 1.2f, 0.5f);
        sl.playSound(null, BlockPos.containing(mid), SoundEvents.DEEPSLATE_BREAK, SoundSource.NEUTRAL, 2f, 0.6f);
        return hit;
    }
}
