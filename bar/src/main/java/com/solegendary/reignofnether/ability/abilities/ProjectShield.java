package com.solegendary.reignofnether.ability.abilities;

import com.solegendary.reignofnether.ability.Ability;
import com.solegendary.reignofnether.ability.AutoRule;
import com.solegendary.reignofnether.ability.AbilityClientboundPacket;
import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.brass.ShieldServerEvents;
import com.solegendary.reignofnether.cursor.CursorClientEvents;
import com.solegendary.reignofnether.hud.buttons.AbilityButton;
import com.solegendary.reignofnether.keybinds.Keybinding;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.unit.UnitAction;
import com.solegendary.reignofnether.unit.interfaces.Unit;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * Brassworks Shield Projector: <b>Project Shield</b> (design/brassworks_plan.md section 3.4). Aim at a friendly unit
 * within {@link #RANGE}: a bubble of {@link ShieldServerEvents#CAPACITY} projectile damage follows it for
 * {@link ShieldServerEvents#DURATION_TICKS} ticks, covering every friend within {@link ShieldServerEvents#RADIUS} of it
 * (the Bastion Aegis rule, small and mobile). Free; {@link #CD_SECONDS} s cooldown.
 */
public class ProjectShield extends Ability {

    public static final int CD_SECONDS = 20;
    public static final int RANGE = 10;

    public ProjectShield() {
        super(UnitAction.PROJECT_SHIELD, CD_SECONDS * ResourceCost.TICKS_PER_SECOND, RANGE, ShieldServerEvents.RADIUS, true, true);
        this.autoRule(AutoRule.ALLY_HURT);   // autocast: a bubble on the friend taking the fire (the most hurt one)
        this.showRangeCircle = true;
    }

    @Override
    public AbilityButton getButton(Keybinding hotkey, Unit unit) {
        return new AbilityButton("Project Shield",
            ResourceLocation.fromNamespaceAndPath("minecraft", "textures/item/heart_of_the_sea.png"),
            hotkey,
            () -> CursorClientEvents.getLeftClickAction() == UnitAction.PROJECT_SHIELD,
            () -> false,
            () -> true,
            () -> CursorClientEvents.setLeftClickAction(UnitAction.PROJECT_SHIELD),
            null,
            List.of(
                FormattedCharSequence.forward("Project Shield  (" + CD_SECONDS + " s cooldown)", Style.EMPTY.withBold(true)),
                FormattedCharSequence.forward("A bubble on a friendly unit within " + RANGE + " blocks for " + ShieldServerEvents.DURATION_TICKS / 20
                    + " s: soaks up to " + (int) ShieldServerEvents.CAPACITY + " projectile damage to it and friends within "
                    + (int) ShieldServerEvents.RADIUS + " blocks. Follows the unit.", Style.EMPTY),
                FormattedCharSequence.forward("Melee and spells pass through it.", Style.EMPTY.withItalic(true))
            ),
            this,
            unit);
    }

    @Override
    public void use(Level level, Unit unitUsing, LivingEntity targetEntity) {
        if (level.isClientSide() || !(level instanceof ServerLevel sl) || !(unitUsing instanceof LivingEntity self) || targetEntity == null)
            return;
        if (cast(sl, self, unitUsing.getOwnerName(), targetEntity) != null) {
            this.setToMaxCooldown(unitUsing);
            AbilityClientboundPacket.sendSetCooldownPacket(self.getId(), this.action, this.cooldownMax);
        }
    }

    /** Ground clicks do nothing: the bubble needs a unit to follow. */
    @Override
    public void use(Level level, Unit unitUsing, BlockPos targetBp) { }

    /** Raises the bubble on a friendly unit in reach; null when refused. Public for the bot and the game test. */
    public static ShieldServerEvents.Shield cast(ServerLevel sl, LivingEntity projector, String owner, LivingEntity target) {
        if (owner == null || target == null || !target.isAlive() || !(target instanceof Unit u)
                || !AlliancesServerEvents.isAlliedOrOwned(u.getOwnerName(), owner))
            return null;
        double reach = RANGE + target.getBbWidth() / 2 + 0.5;
        if (projector.distanceToSqr(target) > reach * reach)
            return null;
        return ShieldServerEvents.raise(sl, target, owner);
    }
}
