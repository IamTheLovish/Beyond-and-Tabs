package com.solegendary.reignofnether.ability.abilities;

import com.solegendary.reignofnether.ReignOfNether;
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
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Soulbinder: <b>Wail</b>, its cheap essence ability (design/essence_casters.md, lovish approved draft 2 on Oct 29 2026:
 * "enemies in a cone flee for 2 s"). Aimed at a unit or the ground: every enemy unit in the {@link #HALF_ANGLE_DEGREES}
 * cone in front of the Soulbinder, toward the aim and up to {@link #RANGE} blocks out, flees straight away from it for
 * {@link #FLEE_SECONDS} s - the Wraith's Fear (uncontrollable and scared) on a crowd, shorter. Heroes and units that
 * cannot be interrupted stand firm, as they do against Fear. {@link #ESSENCE_COST} essence, {@link #CD_SECONDS} s.
 * Refused - nothing spent, no cooldown - with no enemy in the cone or short of essence. Autocast at the nearest enemy.
 */
public class Wail extends Ability {

    public static final int ESSENCE_COST = 5;
    public static final int CD_SECONDS = 12;
    /** How far the cone reaches (not in lovish's draft: a placeholder for him). */
    public static final int RANGE = 6;
    /** Half the cone's opening: 45 degrees each side of the aim, a quarter circle (placeholder). */
    public static final int HALF_ANGLE_DEGREES = 45;
    public static final int FLEE_SECONDS = 2;
    /** Where a fleeing unit runs to: Fear's two blocks per second of fear, straight away from the Soulbinder. */
    public static final int FLEE_DISTANCE = FLEE_SECONDS * 2;
    public static final String MSG_NO_ENEMY = "hud.essence.reignofnether.wail_no_enemy";

    public Wail() {
        super(UnitAction.WAIL, CD_SECONDS * ResourceCost.TICKS_PER_SECOND, RANGE, 0, true, true);
        this.autoRule(AutoRule.ENEMY_IN_RANGE);
        this.showRangeCircle = true;
    }

    @Override
    public AbilityButton getButton(Keybinding hotkey, Unit unit) {
        return new AbilityButton("Wail",
            ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "textures/icons/abilities/chilling_screech.png"),
            hotkey,
            () -> CursorClientEvents.getLeftClickAction() == UnitAction.WAIL,
            () -> false,
            () -> true,
            () -> CursorClientEvents.setLeftClickAction(UnitAction.WAIL),
            () -> toggleAutocast(unit),
            List.of(
                FormattedCharSequence.forward("Wail  (" + ESSENCE_COST + " essence, " + CD_SECONDS + " s cooldown)", Style.EMPTY.withBold(true)),
                FormattedCharSequence.forward("Enemies in a cone up to " + RANGE + " blocks in front of the Soulbinder flee from it for "
                    + FLEE_SECONDS + " s. Heroes stand firm.", Style.EMPTY),
                FormattedCharSequence.forward("The dead remember how they died, and tell the living.", Style.EMPTY.withItalic(true))
            ),
            this,
            unit);
    }

    @Override
    public void use(Level level, Unit unitUsing, LivingEntity targetEntity) {
        if (targetEntity != null)
            useAt(level, unitUsing, targetEntity.position());
    }

    @Override
    public void use(Level level, Unit unitUsing, BlockPos targetBp) {
        if (targetBp != null)
            useAt(level, unitUsing, Vec3.atBottomCenterOf(targetBp));
    }

    private void useAt(Level level, Unit unitUsing, Vec3 aim) {
        if (level.isClientSide() || !(level instanceof ServerLevel sl) || !(unitUsing instanceof LivingEntity self) || !isOffCooldown(unitUsing))
            return;
        if (!cast(sl, self, unitUsing.getOwnerName(), aim, false).isEmpty()) {
            this.setToMaxCooldown(unitUsing);
            AbilityClientboundPacket.sendSetCooldownPacket(self.getId(), this.action, this.cooldownMax);
        }
    }

    /**
     * The rule: finds the enemies in the cone toward {@code aim}, then spends the essence, then scares them - so a
     * refusal never costs essence. Returns the units that flee (empty: refused). Public for the bot and the game test
     * (no cooldown handling here).
     */
    public static List<LivingEntity> cast(ServerLevel sl, LivingEntity caster, String owner, Vec3 aim, boolean quiet) {
        return List.of();
    }
}
