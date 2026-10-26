package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.BuildingServerEvents;
import com.solegendary.reignofnether.building.BuildingUtils;
import com.solegendary.reignofnether.building.Buildings;
import com.solegendary.reignofnether.registrars.AttributeRegistrar;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.unit.EngagementStance;
import com.solegendary.reignofnether.unit.UnitAction;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.interfaces.AttackerUnit;
import com.solegendary.reignofnether.unit.units.villagers.VindicatorUnit;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.level.block.Rotation;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * Attack-move / Fight (Lane C playtest #26, Oct 20: "units move forward and stop to fight whatever they meet"), checked
 * the way BAR's Fight behaves: stop and fight what comes in range, then carry on to the spot; an enemy unit comes before
 * a building the unit picked up on the way; a target picked up on the way that runs past the leash is let go.
 * Each attacker's aggro range is cut to {@link #AGGRO} so units from tests running alongside stay out of it.
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class AttackMoveGameTests {

    static final String ARENA = SkirmishGameTests.ARENA;
    static final double AGGRO = 6;

    static VindicatorUnit spawn(GameTestHelper helper, String owner, double x, double z, List<Entity> made) {
        VindicatorUnit v = EntityRegistrar.VINDICATOR_UNIT.get().create(helper.getLevel());
        if (v == null)
            return null;
        BlockPos at = helper.absolutePos(new BlockPos(0, 2, 0));
        v.moveTo(at.getX() + x, at.getY(), at.getZ() + z, 0, 0);
        v.setOwnerName(owner);
        helper.getLevel().addFreshEntity(v);
        made.add(v);
        return v;
    }

    static void shortSighted(VindicatorUnit v) {
        AttributeInstance aggro = v.getAttribute(AttributeRegistrar.AGGRO_RANGE.get());
        if (aggro != null)
            aggro.setBaseValue(AGGRO);
    }

    /** A target that never fights back, so each test watches the attacker alone. */
    static void passive(VindicatorUnit v) {
        EngagementStance.set(v, EngagementStance.HOLD_FIRE);
    }

    static void attackMove(String owner, VindicatorUnit v, BlockPos to) {
        UnitServerEvents.addActionItem(owner, UnitAction.ATTACK_MOVE, -1, new int[] { v.getId() }, to, BlockPos.ZERO);
    }

    static void notYet(String why) {
        throw new GameTestAssertException(why);
    }

    static void cleanUp(List<Entity> made) {
        for (Entity e : made) {
            UnitServerEvents.clearQueuedActions(e.getId());
            e.discard();
        }
    }

    /** Stops for the enemy beside its path, kills it, then walks on to the spot it was sent to. */
    @GameTest(template = ARENA, timeoutTicks = 400)
    public static void attack_move_stops_to_fight_then_carries_on(GameTestHelper helper) {
        String owner = "gametest_amove_go_" + helper.absolutePos(BlockPos.ZERO).asLong();
        List<Entity> made = new ArrayList<>();
        VindicatorUnit attacker = spawn(helper, owner, 1.5, 8.5, made);
        VindicatorUnit foe = spawn(helper, owner + "_foe", 8.5, 11.5, made);
        if (attacker == null || foe == null) {
            cleanUp(made);
            helper.fail("could not create the units");
            return;
        }
        shortSighted(attacker);
        passive(foe);
        foe.setHealth(2);
        BlockPos dest = helper.absolutePos(new BlockPos(15, 2, 8));
        boolean[] engaged = { false };
        helper.runAfterDelay(2, () -> attackMove(owner, attacker, dest));
        helper.succeedWhen(() -> {
            if (attacker.getTargetGoal().getTarget() == foe)
                engaged[0] = true;
            if (foe.isAlive())
                notYet("the enemy beside the path is still alive (engaged " + engaged[0] + ")");
            double dx = attacker.getX() - (dest.getX() + 0.5), dz = attacker.getZ() - (dest.getZ() + 0.5);
            if (dx * dx + dz * dz > 2.5 * 2.5)
                notYet("the attacker stopped " + Math.round(Math.sqrt(dx * dx + dz * dz)) + " blocks short of the spot"
                    + stopShort(attacker));
            cleanUp(made);
        });
    }

    /**
     * Why an attack-mover is not where it was sent: its orders, path and what stands round it. Red once in three full
     * runs of PR #119 (run 39704723375 attempt 1, "stopped 3 blocks short"; green on the rerun and 6 of 6 alone in
     * flake-stress) with nothing else to read - the stop-short family of tools/known_flakes.txt.
     */
    static String stopShort(VindicatorUnit v) {
        BlockPos amt = v.getAttackMoveTarget(), mt = v.getMoveGoal().getMoveTarget();
        StringBuilder near = new StringBuilder();
        for (net.minecraft.world.entity.LivingEntity o : UnitServerEvents.getAllUnits())
            if (o != v && o.distanceToSqr(v) < 6 * 6)
                near.append(' ').append(net.minecraft.world.entity.EntityType.getKey(o.getType()).getPath())
                    .append(String.format(java.util.Locale.ROOT, "@%.1f,%.1f", o.getX() - v.getX(), o.getZ() - v.getZ()));
        return String.format(java.util.Locale.ROOT, " (at %.1f %.1f %.1f, tick %d; attack-move %s, move %s, target %s,"
                + " building %s, nav done %s, idle %s; units within 6:%s)", v.getX(), v.getY(), v.getZ(), v.tickCount,
            amt == null ? "none" : amt.toShortString(), mt == null ? "none" : mt.toShortString(),
            v.getTargetGoal().getTarget() == null ? "none"
                : net.minecraft.world.entity.EntityType.getKey(v.getTargetGoal().getTarget().getType()).getPath(),
            AttackerUnit.isAttackingBuilding(v), v.getNavigation().isDone(), v.isIdle(), near.length() == 0 ? " none" : near);
    }

    /**
     * Hitting a building it picked up on the way, it turns on an enemy unit that comes into range - even one that
     * isn't hitting it (only a hit on itself turned it round before).
     */
    @GameTest(template = ARENA, timeoutTicks = 300)
    public static void attack_move_turns_from_a_building_to_a_unit(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = "gametest_amove_bld_" + helper.absolutePos(BlockPos.ZERO).asLong();
        String foeOwner = owner + "_foe";
        List<Entity> made = new ArrayList<>();
        BuildingPlacement wall = BuildingUtils.getNewBuildingPlacement(Buildings.WIND_GENERATOR_VILLAGERS, level,
            helper.absolutePos(new BlockPos(9, 1, 7)), Rotation.NONE, foeOwner, false);
        for (var b : wall.getBlocks())
            if (!b.getBlockState().isAir())
                level.setBlock(b.getBlockPos(), b.getBlockState(), 3);
        wall.initPlacedBlocks();
        wall.isBuilt = true;
        BuildingServerEvents.getBuildings().add(wall);
        VindicatorUnit attacker = spawn(helper, owner, 4.5, 8.5, made);
        if (attacker == null) {
            BuildingServerEvents.getBuildings().remove(wall);
            helper.fail("could not create the attacker");
            return;
        }
        shortSighted(attacker);
        BlockPos dest = helper.absolutePos(new BlockPos(15, 2, 8));
        helper.runAfterDelay(2, () -> attackMove(owner, attacker, dest));
        VindicatorUnit[] foe = { null };
        helper.succeedWhen(() -> {
            if (foe[0] == null) {
                if (!isAttackingBuilding(attacker))
                    notYet("the attacker has not picked up the building on its way yet");
                foe[0] = spawn(helper, foeOwner, attacker.getX() - helper.absolutePos(BlockPos.ZERO).getX() - 3,
                    attacker.getZ() - helper.absolutePos(BlockPos.ZERO).getZ() + 2, made);
                if (foe[0] == null)
                    notYet("could not create the enemy unit");
                passive(foe[0]);
                notYet("enemy unit placed beside the attacker");
            }
            if (attacker.getTargetGoal().getTarget() != foe[0])
                notYet("still hitting the building with an enemy unit " + Math.round(attacker.distanceTo(foe[0]))
                    + " blocks away");
            if (isAttackingBuilding(attacker))
                notYet("turned on the unit but still holds the building target");
            BuildingServerEvents.getBuildings().remove(wall);
            cleanUp(made);
        });
    }

    /**
     * A target picked up on the way that gets past the leash is let go, and the attacker carries on to its spot
     * instead of chasing it across the map.
     */
    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void attack_move_lets_a_runaway_target_go(GameTestHelper helper) {
        String owner = "gametest_amove_leash_" + helper.absolutePos(BlockPos.ZERO).asLong();
        List<Entity> made = new ArrayList<>();
        VindicatorUnit attacker = spawn(helper, owner, 1.5, 8.5, made);
        VindicatorUnit foe = spawn(helper, owner + "_foe", 5.5, 8.5, made);
        if (attacker == null || foe == null) {
            cleanUp(made);
            helper.fail("could not create the units");
            return;
        }
        shortSighted(attacker);
        passive(foe);
        BlockPos dest = helper.absolutePos(new BlockPos(15, 2, 8));
        helper.runAfterDelay(2, () -> attackMove(owner, attacker, dest));
        long[] ranAt = { -1 };
        helper.succeedWhen(() -> {
            if (ranAt[0] < 0) {
                if (attacker.getTargetGoal().getTarget() != foe)
                    notYet("the attacker has not picked up the enemy on its way yet");
                // the enemy "runs": put it past the leash, hanging still so it stays there
                double leash = AttackerUnit.attackMoveLeash(attacker);
                foe.setNoAi(true);
                foe.setNoGravity(true);
                foe.teleportTo(attacker.getX(), attacker.getY() + 1, attacker.getZ() + leash + 4);
                ranAt[0] = helper.getTick();
                notYet("the enemy ran past the leash");
            }
            if (attacker.getTargetGoal().getTarget() == foe) {
                if (helper.getTick() - ranAt[0] > 20)
                    helper.fail("still chasing a target " + Math.round(attacker.distanceTo(foe))
                        + " blocks away, past its leash of " + Math.round(AttackerUnit.attackMoveLeash(attacker)));
                notYet("still on the runaway target");
            }
            if (!dest.equals(attacker.getAttackMoveTarget()))
                notYet("let the target go but dropped its attack-move spot: " + attacker.getAttackMoveTarget());
            cleanUp(made);
        });
    }

    static boolean isAttackingBuilding(VindicatorUnit v) {
        var goal = v.getAttackBuildingGoal();
        return goal instanceof com.solegendary.reignofnether.unit.goals.MeleeAttackBuildingGoal m && m.getBuildingTarget() != null;
    }
}
