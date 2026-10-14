package com.solegendary.reignofnether.ability.abilities;

import com.solegendary.reignofnether.ability.Ability;
import com.solegendary.reignofnether.ability.AutoRule;
import com.solegendary.reignofnether.ability.AbilityClientboundPacket;
import com.solegendary.reignofnether.brass.Heat;
import com.solegendary.reignofnether.brass.Machine;
import com.solegendary.reignofnether.cursor.CursorClientEvents;
import com.solegendary.reignofnether.hud.HudClientboundPacket;
import com.solegendary.reignofnether.hud.buttons.AbilityButton;
import com.solegendary.reignofnether.keybinds.Keybinding;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.resources.Resources;
import com.solegendary.reignofnether.resources.ResourcesClientboundPacket;
import com.solegendary.reignofnether.resources.ResourcesServerEvents;
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

import java.util.List;

/**
 * Brassworks Tinker: <b>Field Upgrade</b> (design/brassworks_plan.md section 3.1) - two buttons, one ability class:
 * <b>Brass Plating</b> (ARMOR +{@link Heat#PLATING_ARMOUR}) or <b>Long Barrel</b> (attack range +{@link Heat#BARREL_RANGE},
 * ranged machines only). Aim at an own {@link Machine} within {@link #RANGE} that has no upgrade yet; {@link #METAL_COST}
 * metal from the pool (the HUD warning when short); nothing paid when the target can't take it. One upgrade per
 * machine, ever: the synced byte drives the render layer (brass plates / a longer barrel) and survives a reload.
 */
public class FieldUpgrade extends Ability {

    public static final int CD_SECONDS = 3;
    public static final int RANGE = 6;
    public static final int METAL_COST = 30;
    public static final String MSG_BAD_TARGET = "hud.brass.reignofnether.upgrade_bad_target";
    public static final String MSG_ALREADY = "hud.brass.reignofnether.upgrade_already";
    public static final String MSG_NOT_RANGED = "hud.brass.reignofnether.upgrade_not_ranged";

    public final byte kind;

    public FieldUpgrade(byte kind) {
        super(kind == Heat.UPGRADE_BARREL ? UnitAction.FIELD_UPGRADE_BARREL : UnitAction.FIELD_UPGRADE_PLATING,
            CD_SECONDS * ResourceCost.TICKS_PER_SECOND, RANGE, 0, true, true);
        this.autoRule(AutoRule.MANUAL);   // autocast: spends metal on a chosen machine
        this.kind = kind;
        this.showRangeCircle = true;
    }

    @Override
    public AbilityButton getButton(Keybinding hotkey, Unit unit) {
        boolean barrel = kind == Heat.UPGRADE_BARREL;
        String title = barrel ? "Long Barrel" : "Brass Plating";
        return new AbilityButton(title,
            ResourceLocation.fromNamespaceAndPath("minecraft", barrel ? "textures/item/spyglass.png" : "textures/item/copper_ingot.png"),
            hotkey,
            () -> CursorClientEvents.getLeftClickAction() == this.action,
            () -> false,
            () -> true,
            () -> CursorClientEvents.setLeftClickAction(this.action),
            null,
            List.of(
                FormattedCharSequence.forward(title + "  (field upgrade, " + METAL_COST + " metal)", Style.EMPTY.withBold(true)),
                FormattedCharSequence.forward(barrel
                    ? "Fit a machine within " + RANGE + " blocks with a longer barrel: +" + (int) Heat.BARREL_RANGE + " attack range. Ranged machines only."
                    : "Rivet brass plates onto a machine within " + RANGE + " blocks: +" + (int) Heat.PLATING_ARMOUR + " armour.", Style.EMPTY),
                FormattedCharSequence.forward("One upgrade per machine, ever.", Style.EMPTY),
                FormattedCharSequence.forward(barrel ? "Measure twice, bore once." : "Rivets hold where welds crack.", Style.EMPTY.withItalic(true))
            ),
            this,
            unit);
    }

    @Override
    public void use(Level level, Unit unitUsing, LivingEntity targetEntity) {
        if (level.isClientSide() || !(level instanceof ServerLevel sl) || !(unitUsing instanceof LivingEntity self) || targetEntity == null)
            return;
        if (apply(sl, self, unitUsing.getOwnerName(), targetEntity, kind, false)) {
            this.setToMaxCooldown(unitUsing);
            AbilityClientboundPacket.sendSetCooldownPacket(self.getId(), this.action, this.cooldownMax);
        }
    }

    /** Ground clicks do nothing: an upgrade needs a machine. */
    @Override
    public void use(Level level, Unit unitUsing, BlockPos targetBp) { }

    /** Can {@code owner} upgrade this: an own, living, bare machine. */
    public static boolean canUpgrade(String owner, LivingEntity target) {
        return owner != null && target != null && target.isAlive() && target instanceof Machine m && target instanceof Unit u
            && owner.equals(u.getOwnerName()) && m.upgrade() == Heat.UPGRADE_NONE;
    }

    /**
     * Checks (own bare machine in reach, the barrel only on a ranged one), pays {@link #METAL_COST} and applies. Returns
     * false when refused (nothing paid). Public for the bot and the game test (no cooldown handling here).
     */
    public static boolean apply(ServerLevel sl, LivingEntity tinker, String owner, LivingEntity target, byte kind, boolean quiet) {
        if (!(target instanceof Machine m) || !(target instanceof Unit u) || !owner.equals(u.getOwnerName()) || !target.isAlive()) {
            if (!quiet)
                HudClientboundPacket.showTempMessageI18n(owner, MSG_BAD_TARGET);
            return false;
        }
        if (m.upgrade() != Heat.UPGRADE_NONE) {
            if (!quiet)
                HudClientboundPacket.showTempMessageI18n(owner, MSG_ALREADY);
            return false;
        }
        if (kind == Heat.UPGRADE_BARREL && !m.isRangedMachine()) {
            if (!quiet)
                HudClientboundPacket.showTempMessageI18n(owner, MSG_NOT_RANGED);
            return false;
        }
        double reach = RANGE + target.getBbWidth() / 2 + 0.5;
        if (tinker.distanceToSqr(target) > reach * reach)
            return false;
        Resources pool = null;
        for (Resources r : ResourcesServerEvents.resourcesList)
            if (r.ownerName.equals(owner))
                pool = r;
        if (pool == null || pool.getMetal() < METAL_COST) {
            if (!quiet)
                ResourcesClientboundPacket.warnInsufficientResources(owner, true, true, false, true);
            return false;
        }
        if (!Heat.applyUpgrade(m, target, kind))
            return false;
        pool.addMetal(-METAL_COST);
        sl.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY() + target.getBbHeight() * 0.6, target.getZ(), 12, 0.4, 0.4, 0.4, 0.1);
        sl.playSound(null, target.blockPosition(), SoundEvents.ANVIL_USE, SoundSource.NEUTRAL, 1.0f, 1.3f);
        return true;
    }
}
