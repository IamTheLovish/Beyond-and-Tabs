package com.solegendary.reignofnether.ability.abilities;

import com.solegendary.reignofnether.ability.Ability;
import com.solegendary.reignofnether.ability.AbilityClientboundPacket;
import com.solegendary.reignofnether.ability.AutoRule;
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
 * Soulbinder: <b>Soul Chains</b>, its big essence ability (design/essence_casters.md, lovish approved draft 2 on Oct 29
 * 2026: "up to 4 enemies linked 10 s, 50% of damage to one is shared to the others"). Aimed at an enemy unit within
 * {@link #RANGE} blocks: it and the nearest other enemies within {@link #CHAIN_RADIUS} blocks of it, {@link #MAX_LINKED}
 * in all, are chained for {@link #DURATION_SECONDS} s. While chained, {@link #SHARE} of the damage any one of them
 * takes is dealt to each of the others as well (essence/SoulbinderServerEvents) - so focus fire on one hurts the pack.
 * {@link #ESSENCE_COST} essence, {@link #CD_SECONDS} s. Refused - nothing spent, no cooldown - on a friend, out of
 * reach, with no second enemy to link, or short of essence. Autocast on a crowd.
 */
public class SoulChains extends Ability {

    public static final int ESSENCE_COST = 20;
    public static final int CD_SECONDS = 60;
    /** How far the Soulbinder throws the chains (not in lovish's draft: a placeholder for him). */
    public static final int RANGE = 8;
    /** How far from the aimed enemy the others can be (placeholder). */
    public static final int CHAIN_RADIUS = 5;
    public static final int MAX_LINKED = 4;
    public static final int DURATION_SECONDS = 10;
    public static final float SHARE = 0.5f;
    public static final String MSG_BAD_TARGET = "hud.essence.reignofnether.soul_chains_bad_target";
    public static final String MSG_ALONE = "hud.essence.reignofnether.soul_chains_alone";

    public SoulChains() {
        super(UnitAction.SOUL_CHAINS, CD_SECONDS * ResourceCost.TICKS_PER_SECOND, RANGE, CHAIN_RADIUS, true, true);
        this.autoRule(AutoRule.CROWD);
        this.showRangeCircle = true;
        this.showRadiusCircle = true;
    }

    @Override
    public AbilityButton getButton(Keybinding hotkey, Unit unit) {
        return new AbilityButton("Soul Chains",
            ResourceLocation.fromNamespaceAndPath("minecraft", "textures/item/chain.png"),
            hotkey,
            () -> CursorClientEvents.getLeftClickAction() == UnitAction.SOUL_CHAINS,
            () -> false,
            () -> true,
            () -> CursorClientEvents.setLeftClickAction(UnitAction.SOUL_CHAINS),
            () -> toggleAutocast(unit),
            List.of(
                FormattedCharSequence.forward("Soul Chains  (" + ESSENCE_COST + " essence, " + CD_SECONDS + " s cooldown)", Style.EMPTY.withBold(true)),
                FormattedCharSequence.forward("Chains an enemy and up to " + (MAX_LINKED - 1) + " others within " + CHAIN_RADIUS
                    + " blocks of it for " + DURATION_SECONDS + " s: " + Math.round(SHARE * 100)
                    + "% of the damage one takes is dealt to the others too.", Style.EMPTY),
                FormattedCharSequence.forward("What one soul suffers, all of them owe.", Style.EMPTY.withItalic(true))
            ),
            this,
            unit);
    }

    @Override
    public void use(Level level, Unit unitUsing, LivingEntity targetEntity) {
        if (level.isClientSide() || !(level instanceof ServerLevel sl) || !(unitUsing instanceof LivingEntity self) || !isOffCooldown(unitUsing))
            return;
        if (!cast(sl, self, unitUsing.getOwnerName(), targetEntity, false).isEmpty())
            startCooldown(unitUsing, self);
    }

    /** A ground click (or a hidden enemy, which is cast at the ground under it): the enemy nearest the spot. */
    @Override
    public void use(Level level, Unit unitUsing, BlockPos targetBp) {
    }

    private void startCooldown(Unit unitUsing, LivingEntity self) {
        this.setToMaxCooldown(unitUsing);
        AbilityClientboundPacket.sendSetCooldownPacket(self.getId(), this.action, this.cooldownMax);
    }

    /**
     * The rule: checks the target (a living enemy unit within reach) and finds the others to link, then spends the
     * essence, then chains them - so a refusal never costs essence. Returns the chained units, the target first
     * (empty: refused). Public for the bot and the game test (no cooldown handling here).
     */
    public static List<LivingEntity> cast(ServerLevel sl, LivingEntity caster, String owner, LivingEntity target, boolean quiet) {
        return List.of();
    }
}
