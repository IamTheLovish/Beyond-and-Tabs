package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.ability.Ability;
import com.solegendary.reignofnether.ability.AutocastState;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.unit.UnitAction;
import com.solegendary.reignofnether.unit.UnitActionItem;
import com.solegendary.reignofnether.unit.interfaces.Unit;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * An order whose target id is not a living entity (security audit F8, Oct 26). The dispatch cast
 * {@code (LivingEntity) level.getEntity(id)} for attack, follow and entity-targeted abilities, so an id that is a
 * wreck, a dropped item or a projectile - a stale click, or a modified client - threw ClassCastException on the
 * server thread and the order was lost. Now such an order is dropped cleanly: no throw, no target taken.
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class OrderTargetGameTests {

    static final String ARENA = SkirmishGameTests.ARENA;

    static Mob spawn(ServerLevel level, EntityType<? extends Mob> type, String owner, BlockPos at, List<Entity> made) {
        return AutocastGameTests.spawn(level, type, owner, at, made);
    }

    @GameTest(template = ARENA, batch = "order_targets", timeoutTicks = 40)
    public static void an_order_on_a_non_living_target_is_dropped_without_throwing(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = "gt_order_target_" + helper.absolutePos(BlockPos.ZERO).asLong();
        List<Entity> made = new ArrayList<>();
        List<String> problems = new ArrayList<>();
        try {
            Mob fighter = spawn(level, EntityRegistrar.VINDICATOR_UNIT.get(), owner, helper.absolutePos(new BlockPos(3, 2, 3)), made);
            Mob worker = spawn(level, EntityRegistrar.VILLAGER_UNIT.get(), owner, helper.absolutePos(new BlockPos(5, 2, 3)), made);
            Mob dancer = spawn(level, EntityRegistrar.BLADE_DANCER_UNIT.get(), owner, helper.absolutePos(new BlockPos(7, 2, 3)), made);
            if (fighter == null || worker == null || dancer == null) {
                helper.fail("test setup: a unit did not spawn");
                return;
            }
            for (Mob m : List.of(fighter, worker, dancer))
                AutocastState.setAll((Unit) m, false);
            BlockPos itemAt = helper.absolutePos(new BlockPos(5, 2, 6));
            ItemEntity item = new ItemEntity(level, itemAt.getX() + 0.5, itemAt.getY(), itemAt.getZ() + 0.5, new ItemStack(Items.STICK));
            level.addFreshEntity(item);
            made.add(item);
            int[] ids = { fighter.getId(), worker.getId(), dancer.getId() };

            List<UnitAction> actions = new ArrayList<>(List.of(UnitAction.ATTACK, UnitAction.FOLLOW));
            for (Ability a : ((Unit) dancer).getAbilities().get())
                if (a.canTargetEntities)
                    actions.add(a.action);   // Blink Strike: an entity-targeted ability
            if (actions.size() < 3)
                problems.add("test setup: the blade dancer has no entity-targeted ability, so that path is not covered");

            for (UnitAction action : actions) {
                for (int targetId : new int[] { item.getId(), Integer.MAX_VALUE - 7 }) {   // a non-living entity; no entity at all
                    String what = action + " on " + (targetId == item.getId() ? "a dropped item" : "an unknown id");
                    try {
                        new UnitActionItem(owner, action, targetId, ids, item.blockPosition(), BlockPos.ZERO).action(level);
                    } catch (RuntimeException e) {
                        problems.add(what + " threw " + e);
                        continue;
                    }
                    for (Mob m : List.of(fighter, worker, dancer)) {
                        Unit u = (Unit) m;
                        if (u.getTargetGoal() != null && u.getTargetGoal().getTarget() != null)
                            problems.add(what + ": " + m.getType().getDescriptionId() + " took a target");
                    }
                }
            }
            if (!item.isAlive())
                problems.add("the item was removed by an order on it");
        } finally {
            AutocastGameTests.cleanUp(made);
        }
        TerrainGameTests.finish(helper, problems);
    }
}
