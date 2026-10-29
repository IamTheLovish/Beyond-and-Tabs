package com.solegendary.reignofnether.ability.abilities;

import com.solegendary.reignofnether.ability.Ability;
import com.solegendary.reignofnether.ability.AbilityClientboundPacket;
import com.solegendary.reignofnether.ability.AutoRule;
import com.solegendary.reignofnether.cursor.CursorClientEvents;
import com.solegendary.reignofnether.essence.Essence;
import com.solegendary.reignofnether.hud.HudClientboundPacket;
import com.solegendary.reignofnether.hud.buttons.AbilityButton;
import com.solegendary.reignofnether.keybinds.Keybinding;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.tide.TideSeerServerEvents;
import com.solegendary.reignofnether.unit.UnitAction;
import com.solegendary.reignofnether.unit.interfaces.Unit;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Tidewrought essence caster (the Tide Seer; design/essence_casters.md, Revision 1 numbers): <b>Brine Bolt</b>, the
 * cheap spell. Aim at an enemy unit within {@link #RANGE} blocks: a bolt of sea water deals {@link #DAMAGE} magic damage
 * and shoves it {@link #KNOCKBACK_BLOCKS} blocks away from the caster (TideSeerServerEvents.shove; knockback resistance
 * shortens it). {@link #ESSENCE_COST} essence, {@link #CD_SECONDS} s cooldown. Refused - nothing spent, no cooldown -
 * on a friend, a dead or out-of-reach target, or when the caster is short of essence. Autocast: the nearest enemy in
 * range (its current target first).
 */
public class BrineBolt extends Ability {

    public static final int ESSENCE_COST = 5;
    public static final int CD_SECONDS = 12;
    /** Revision 1 (scaled to T1 health, 30-80): 25, down from the draft's 50. */
    public static final float DAMAGE = 25f;
    public static final double KNOCKBACK_BLOCKS = 3;
    /** Not in lovish's draft: how far the bolt flies, a placeholder for him to tune (the other casters' 10). */
    public static final int RANGE = 10;
    public static final String MSG_BAD_TARGET = "hud.essence.reignofnether.brine_bolt_bad_target";

    public BrineBolt() {
        super(UnitAction.BRINE_BOLT, CD_SECONDS * ResourceCost.TICKS_PER_SECOND, RANGE, 0, true, true);
        this.autoRule(AutoRule.ENEMY_IN_RANGE);
        this.autocastEssence = ESSENCE_COST;
        this.showRangeCircle = true;
    }

    @Override
    public AbilityButton getButton(Keybinding hotkey, Unit unit) {
        return new AbilityButton("Brine Bolt",
            ResourceLocation.fromNamespaceAndPath("minecraft", "textures/item/prismarine_shard.png"),
            hotkey,
            () -> CursorClientEvents.getLeftClickAction() == UnitAction.BRINE_BOLT,
            () -> false,
            () -> true,
            () -> CursorClientEvents.setLeftClickAction(UnitAction.BRINE_BOLT),
            null,
            List.of(
                FormattedCharSequence.forward("Brine Bolt  (" + ESSENCE_COST + " essence, " + CD_SECONDS + " s cooldown)", Style.EMPTY.withBold(true)),
                FormattedCharSequence.forward("An enemy unit up to " + RANGE + " blocks away takes " + (int) DAMAGE
                    + " magic damage and is knocked " + (int) KNOCKBACK_BLOCKS + " blocks back.", Style.EMPTY),
                FormattedCharSequence.forward("The drowned lend their weight to the wave.", Style.EMPTY.withItalic(true))
            ),
            this,
            unit);
    }

    @Override
    public void use(Level level, Unit unitUsing, LivingEntity targetEntity) {
        if (level.isClientSide() || !(level instanceof ServerLevel sl) || !(unitUsing instanceof LivingEntity self) || targetEntity == null)
            return;
        if (cast(sl, self, unitUsing.getOwnerName(), targetEntity, false)) {
            this.setToMaxCooldown(unitUsing);
            AbilityClientboundPacket.sendSetCooldownPacket(self.getId(), this.action, this.cooldownMax);
        }
    }

    /** Ground clicks do nothing: the bolt needs a unit. */
    @Override
    public void use(Level level, Unit unitUsing, BlockPos targetBp) { }

    /**
     * The rule: checks the target (a living enemy unit within reach), then spends the essence, then hits and shoves it -
     * so a refusal never costs essence. True when cast. Public for the bot and the game test (no cooldown here).
     */
    public static boolean cast(ServerLevel sl, LivingEntity caster, String owner, LivingEntity target, boolean quiet) {
        if (target == null || !TideSeerServerEvents.isEnemy(owner, target)) {
            refuse(owner, MSG_BAD_TARGET, quiet);
            return false;
        }
        double reach = RANGE + target.getBbWidth() / 2 + 0.5;
        if (caster.distanceToSqr(target) > reach * reach)
            return false;
        if (!Essence.trySpend(caster, ESSENCE_COST)) {
            refuse(owner, Empower.MSG_SHORT, quiet);
            return false;
        }
        com.solegendary.reignofnether.barfx.BarFx.shot(sl, caster.getEyePosition(), target.getEyePosition(),
            com.solegendary.reignofnether.barfx.BarFx.K_MAGIC, caster);
        // the hit's own vanilla knockback is dropped: the shove is the bolt's knockback, the same 3 blocks every time
        Vec3 before = target.getDeltaMovement();
        target.hurt(sl.damageSources().indirectMagic(caster, caster), DAMAGE);
        target.setDeltaMovement(before);
        if (target.isAlive())
            TideSeerServerEvents.shove(target, caster.position(), KNOCKBACK_BLOCKS);
        sl.sendParticles(ParticleTypes.SPLASH, target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(),
            20, 0.4, 0.4, 0.4, 0.2);
        sl.sendParticles(ParticleTypes.BUBBLE, target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(),
            10, 0.3, 0.3, 0.3, 0.1);
        sl.playSound(null, target.blockPosition(), SoundEvents.TRIDENT_HIT, SoundSource.NEUTRAL, 1.5f, 0.8f);
        return true;
    }

    private static void refuse(String owner, String msg, boolean quiet) {
        if (!quiet && owner != null)
            HudClientboundPacket.showTempMessageI18n(owner, msg);
    }
}
