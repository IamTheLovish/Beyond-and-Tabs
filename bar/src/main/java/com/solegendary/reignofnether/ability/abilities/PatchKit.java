package com.solegendary.reignofnether.ability.abilities;

import com.solegendary.reignofnether.ability.Ability;
import com.solegendary.reignofnether.ability.AbilityClientboundPacket;
import com.solegendary.reignofnether.ability.AutoRule;
import com.solegendary.reignofnether.brass.MachineRepair;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.cursor.CursorClientEvents;
import com.solegendary.reignofnether.essence.Essence;
import com.solegendary.reignofnether.hud.HudClientboundPacket;
import com.solegendary.reignofnether.hud.buttons.AbilityButton;
import com.solegendary.reignofnether.keybinds.Keybinding;
import com.solegendary.reignofnether.resources.ResourceCost;
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

import javax.annotation.Nullable;
import java.util.List;

/**
 * Brassworks essence caster (the Soul Engineer; design/essence_casters.md, lovish approved draft 2 on Oct 29): <b>Patch
 * Kit</b>, the cheap spell. Aim at a friendly (own or allied) Brassworks machine ({@link MachineRepair#isFriendlyMachine},
 * crews are people) or a built friendly building within {@link #RANGE} blocks: it is mended by {@link #REPAIR_HP} at
 * once - a machine heals, a building has its missing blocks put straight back (BuildingPlacement.restoreHealthNow: no
 * builder, no wood). {@link #ESSENCE_COST} essence, {@link #CD_SECONDS} s cooldown.
 * <p>
 * Refused - nothing spent, no cooldown - on a target at full health, an enemy's, one out of reach, a building still
 * going up, anything else, or short of essence. Autocast ({@link AutoRule#BUILDING_HURT}, Revision 1): the most hurt
 * friendly building in reach under 70% health; a machine is mended by hand.
 */
public class PatchKit extends Ability {

    public static final int ESSENCE_COST = 5;
    public static final int CD_SECONDS = 12;
    public static final int REPAIR_HP = 120;
    /** Not in lovish's draft (only the 120 HP was): how far the caster reaches, a placeholder for him to tune. */
    public static final int RANGE = 8;
    public static final String MSG_BAD_TARGET = "hud.essence.reignofnether.patch_bad_target";
    public static final String MSG_FULL = "hud.essence.reignofnether.patch_full";
    public static final String MSG_TOO_FAR = "hud.essence.reignofnether.patch_too_far";

    public PatchKit() {
        super(UnitAction.PATCH_KIT, CD_SECONDS * ResourceCost.TICKS_PER_SECOND, RANGE, 0, true, true);
        this.autoRule(AutoRule.BUILDING_HURT);
        this.autocastEssence = ESSENCE_COST;
        this.showRangeCircle = true;
    }

    @Override
    public AbilityButton getButton(Keybinding hotkey, Unit unit) {
        return new AbilityButton("Patch Kit",
            ResourceLocation.fromNamespaceAndPath("minecraft", "textures/item/copper_ingot.png"),
            hotkey,
            () -> CursorClientEvents.getLeftClickAction() == UnitAction.PATCH_KIT,
            () -> false,
            () -> true,
            () -> CursorClientEvents.setLeftClickAction(UnitAction.PATCH_KIT),
            null,
            List.of(
                FormattedCharSequence.forward("Patch Kit  (" + ESSENCE_COST + " essence, " + CD_SECONDS + " s cooldown)", Style.EMPTY.withBold(true)),
                FormattedCharSequence.forward("Mends a friendly machine or building within " + RANGE + " blocks by "
                    + REPAIR_HP + " health at once.", Style.EMPTY),
                FormattedCharSequence.forward("Autocast: a friendly building under 70% health.", Style.EMPTY),
                FormattedCharSequence.forward("Soul-solder holds where rivets fail.", Style.EMPTY.withItalic(true))
            ),
            this,
            unit);
    }

    @Override
    public void use(Level level, Unit unitUsing, LivingEntity targetEntity) {
        if (level.isClientSide() || !(unitUsing instanceof LivingEntity self) || !(level instanceof ServerLevel sl) || targetEntity == null)
            return;
        // a click that found no machine (a crew, a building's target stand) means the building there, if any
        if (!MachineRepair.isMachine(targetEntity)) {
            use(level, unitUsing, targetEntity.blockPosition());
            return;
        }
        if (castOnMachine(sl, self, unitUsing.getOwnerName(), targetEntity, false) > 0)
            startCooldown(unitUsing, self);
    }

    @Override
    public void use(Level level, Unit unitUsing, BlockPos targetBp) {
        if (level.isClientSide() || !(unitUsing instanceof LivingEntity self) || !(level instanceof ServerLevel sl) || targetBp == null)
            return;
        if (castOnBuilding(sl, self, unitUsing.getOwnerName(), SoulBoiler.buildingAt(sl, targetBp), false) > 0)
            startCooldown(unitUsing, self);
    }

    private void startCooldown(Unit unitUsing, LivingEntity self) {
        this.setToMaxCooldown(unitUsing);
        AbilityClientboundPacket.sendSetCooldownPacket(self.getId(), this.action, this.cooldownMax);
    }

    /**
     * The machine rule: a living friendly machine within reach under full health, then the essence, then the heal (at
     * most {@link #REPAIR_HP}, never past full). Returns the HP mended (0 = refused). Public for the game test.
     */
    public static float castOnMachine(ServerLevel sl, LivingEntity caster, String owner, LivingEntity target, boolean quiet) {
        if (true) return 0;   // STUB (red proof)
        if (owner == null || owner.isBlank() || target == null || !MachineRepair.isFriendlyMachine(target, owner)) {
            refuse(owner, MSG_BAD_TARGET, quiet);
            return 0;
        }
        double reach = RANGE + target.getBbWidth() / 2 + 0.5;
        if (caster.distanceToSqr(target) > reach * reach) {
            refuse(owner, MSG_TOO_FAR, quiet);
            return 0;
        }
        float missing = target.getMaxHealth() - target.getHealth();
        if (missing <= 0) {
            refuse(owner, MSG_FULL, quiet);
            return 0;
        }
        if (!Essence.trySpend(caster, ESSENCE_COST)) {
            refuse(owner, Empower.MSG_SHORT, quiet);
            return 0;
        }
        float healed = Math.min(missing, REPAIR_HP);
        target.heal(healed);
        sl.sendParticles(ParticleTypes.ELECTRIC_SPARK, target.getX(), target.getY() + target.getBbHeight() * 0.6, target.getZ(),
            12, 0.3, 0.3, 0.3, 0.06);
        sl.sendParticles(ParticleTypes.SOUL, target.getX(), target.getY() + target.getBbHeight() * 0.6, target.getZ(),
            4, 0.3, 0.3, 0.3, 0.02);
        sl.playSound(null, target.blockPosition(), SoundEvents.ANVIL_USE, SoundSource.NEUTRAL, 0.8f, 1.4f);
        return healed;
    }

    /**
     * The building rule: a built friendly building within reach (to its nearest edge) under full health, then the
     * essence, then the blocks. Returns the health regained (0 = refused). Public for the bot and the game test.
     */
    public static int castOnBuilding(ServerLevel sl, LivingEntity caster, String owner, @Nullable BuildingPlacement b, boolean quiet) {
        if (true) return 0;   // STUB (red proof)
        if (owner == null || owner.isBlank() || !SoulBoiler.isFriendly(b, owner) || !b.isBuilt || b.isDestroyedServerside) {
            refuse(owner, MSG_BAD_TARGET, quiet);
            return 0;
        }
        if (SoulBoiler.edgeDistance(caster, b) > RANGE) {
            refuse(owner, MSG_TOO_FAR, quiet);
            return 0;
        }
        if (b.getHealth() >= b.getMaxHealth()) {
            refuse(owner, MSG_FULL, quiet);
            return 0;
        }
        if (!Essence.trySpend(caster, ESSENCE_COST)) {
            refuse(owner, Empower.MSG_SHORT, quiet);
            return 0;
        }
        int regained = b.restoreHealthNow(REPAIR_HP);
        BlockPos c = b.centrePos;
        sl.sendParticles(ParticleTypes.ELECTRIC_SPARK, c.getX() + 0.5, c.getY() + 1, c.getZ() + 0.5, 20, 1.5, 1.0, 1.5, 0.08);
        sl.sendParticles(ParticleTypes.SOUL, c.getX() + 0.5, c.getY() + 1, c.getZ() + 0.5, 8, 1.2, 0.8, 1.2, 0.02);
        sl.playSound(null, c, SoundEvents.ANVIL_USE, SoundSource.NEUTRAL, 1.2f, 1.2f);
        return Math.max(1, regained);   // paid: a cast even if every missing block was blocked
    }

    private static void refuse(String owner, String msg, boolean quiet) {
        if (!quiet && owner != null && !owner.isBlank())
            HudClientboundPacket.showTempMessageI18n(owner, msg);
    }
}
