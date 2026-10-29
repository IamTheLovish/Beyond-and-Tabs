package com.solegendary.reignofnether.ability.abilities;

import com.solegendary.reignofnether.ability.Ability;
import com.solegendary.reignofnether.ability.AutoRule;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.unit.UnitAction;
import com.solegendary.reignofnether.unit.interfaces.Unit;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

/**
 * Empower, every essence caster's shared ability (design/essence_casters.md). STUB: the API the game tests are written
 * against; the rule lands in the next commit.
 */
public class Empower extends Ability {

    public static final int ESSENCE_COST = 15;
    public static final int RANGE = 6;
    public static final int CD_SECONDS = 10;

    public Empower() {
        super(UnitAction.EMPOWER, CD_SECONDS * ResourceCost.TICKS_PER_SECOND, RANGE, 0, true, true);
        this.autoRule(AutoRule.MANUAL);
    }

    @Override
    public void use(Level level, Unit unitUsing, LivingEntity targetEntity) { }

    @Override
    public void use(Level level, Unit unitUsing, BlockPos targetBp) { }

    public static boolean cast(ServerLevel sl, LivingEntity caster, String owner, LivingEntity target, boolean quiet) {
        return false;
    }
}
