package com.solegendary.reignofnether.ability.abilities;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.ability.Ability;
import com.solegendary.reignofnether.ability.ExperimentalAbilityText;
import com.solegendary.reignofnether.unit.units.brass.IronLeviathanEngineUnit;
import java.util.ArrayList;
import com.solegendary.reignofnether.cursor.CursorClientEvents;
import com.solegendary.reignofnether.hud.buttons.AbilityButton;
import com.solegendary.reignofnether.keybinds.Keybinding;
import com.solegendary.reignofnether.unit.UnitAction;
import com.solegendary.reignofnether.unit.goals.RangedAttackGroundGoal;
import com.solegendary.reignofnether.unit.interfaces.RangedAttackerUnit;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.units.villagers.PillagerUnit;
import com.solegendary.reignofnether.unit.units.villagers.RavagerUnit;
import com.solegendary.reignofnether.util.LanguageUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.level.Level;

import java.util.List;

public class AttackGround extends Ability {

    private static final int CD_MAX = 0;


    public AttackGround(float attackRange) {
        super(
                UnitAction.ATTACK_GROUND,
                CD_MAX,
                attackRange,
                0,
                false,
                false
        );
    }

    /** Tooltip body for the Iron Leviathan Engine's cannon. Public for the tooltip game test. */
    public String description() {
        return "Click the ground to shell that spot from afar: it outranges every tower, so static defences and slow armies fall to it.";
    }

    /** Tooltip range line, from the ability's own range. Public for the tooltip game test. */
    public String rangeLine() {
        return ExperimentalAbilityText.rangeLine(range);
    }

    /** Plain tooltip for every unit; the Engine, a T3 experimental, also gets its description and range. */
    private List<FormattedCharSequence> tooltip(Unit unit) {
        List<FormattedCharSequence> lines = new ArrayList<>();
        lines.add(FormattedCharSequence.forward(LanguageUtil.getTranslation("abilities.reignofnether.attack_ground"), Style.EMPTY));
        if (unit instanceof IronLeviathanEngineUnit) {
            lines.add(FormattedCharSequence.forward(rangeLine(), Style.EMPTY));
            lines.add(FormattedCharSequence.forward(description(), Style.EMPTY));
        }
        return lines;
    }

    @Override
    public AbilityButton getButton(Keybinding hotkey, Unit unit) {
        return new AbilityButton(
                "Attack Ground",
                ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "textures/icons/items/fireball.png"),
                hotkey,
                () -> {
                    boolean actionSelected = CursorClientEvents.getLeftClickAction() == UnitAction.ATTACK_GROUND;
                    boolean isGroundAttacking =
                            unit instanceof RangedAttackerUnit rau &&
                            rau.getRangedAttackGroundGoal() != null &&
                            rau.getRangedAttackGroundGoal().getGroundTarget() != null;
                    return actionSelected || isGroundAttacking;
                },
                () -> {
                    if (unit instanceof RavagerUnit ravagerUnit)
                        return !(ravagerUnit.getFirstPassenger() instanceof PillagerUnit);
                    return false;
                },
                () -> true,
                () -> CursorClientEvents.setLeftClickAction(UnitAction.ATTACK_GROUND),
                null,
                tooltip(unit),
                this,
                unit
        );
    }

    @Override
    public void use(Level level, Unit unitUsing, BlockPos targetBp) {
        if (unitUsing instanceof RavagerUnit ravagerUnit && ravagerUnit.getFirstPassenger() instanceof RangedAttackerUnit rangedPassenger) {
            RangedAttackGroundGoal<?> attackGroundGoal = rangedPassenger.getRangedAttackGroundGoal();
            if (attackGroundGoal != null)
                attackGroundGoal.setGroundTarget(targetBp);
        } else if (unitUsing instanceof RangedAttackerUnit rangedUnit){
            RangedAttackGroundGoal<?> attackGroundGoal = rangedUnit.getRangedAttackGroundGoal();
            if (attackGroundGoal != null)
                attackGroundGoal.setGroundTarget(targetBp);
        }
    }
}
