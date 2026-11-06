package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.BuildingServerEvents;
import com.solegendary.reignofnether.unit.UnitAction;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.command.QueueMode;
import com.solegendary.reignofnether.unit.goals.MeleeAttackBuildingGoal;
import com.solegendary.reignofnether.unit.interfaces.AttackerUnit;
import com.solegendary.reignofnether.unit.packets.UnitActionServerboundPacket;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiPredicate;

/**
 * ATTACK_BUILDING under the fog (Oct 28 list on #35, "Fog leaks": a modified client could name a building its player
 * never saw and its units walked there; Command Center Eris.17, Nov 6). UnitActionServerboundPacket.accept refuses an
 * ATTACK_BUILDING on a building the sender does not know - neither in view now nor seen before (explored memory,
 * fogofwar/ExploredBuildings; the rule's cases are ExploredBuildingsTest). The test server runs with fog off for
 * everyone, so this calls accept's overload with the building rule set by the test (the packet path passes
 * FogOfWarServerEvents.mayTargetBuilding); the refusal, the return value, the unit's building target and the queues
 * are the real ones. A refused order does what an order on empty ground does - nothing - so the refusal itself tells a
 * client nothing about what stands there.
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class AttackBuildingFogGameTests {

    static BuildingPlacement buildingTarget(Mob unit) {
        return ((AttackerUnit) unit).getAttackBuildingGoal() instanceof MeleeAttackBuildingGoal g ? g.getBuildingTarget() : null;
    }

    /**
     * An unknown building: the ATTACK_BUILDING is refused (false), the unit takes no building target and queues
     * nothing. A known one (seen before, or in view): the same order goes through and the unit takes it. With fog off
     * (the real rule, this server): accepted.
     */
    @GameTest(template = SkirmishGameTests.ARENA, timeoutTicks = 100)
    public static void an_attack_on_a_building_the_sender_never_saw_is_refused_and_a_known_one_obeyed(GameTestHelper helper) {
        String me = CommandQueueGameTests.owner(helper, "fogbme"), foe = CommandQueueGameTests.owner(helper, "fogbfoe");
        FakePlayer sender = CommandQueueGameTests.fakePlayer(helper.getLevel(), me);
        BuildingPlacement keep = FriendlyFireGameTests.built(helper, foe, 9, 9);
        Mob mine = CommandQueueGameTests.fighter(helper, me, 2, 2);
        if (mine == null) {
            BuildingServerEvents.getBuildings().remove(keep);
            helper.fail("could not create the unit");
            return;
        }
        TestCleanup.entities(helper, List.of(mine));
        int[] ids = { mine.getId() };
        BiPredicate<ServerPlayer, Entity> anyEntity = (p, e) -> true;
        BiPredicate<ServerPlayer, BuildingPlacement> neverSaw = (p, b) -> b != keep;
        BiPredicate<ServerPlayer, BuildingPlacement> known = (p, b) -> true;
        List<String> problems = new ArrayList<>();
        helper.runAfterDelay(2, () -> {
            if (UnitActionServerboundPacket.accept(sender, me, UnitAction.ATTACK_BUILDING, -1, ids, keep.originPos,
                    BlockPos.ZERO, QueueMode.REPLACE, anyEntity, neverSaw))
                problems.add("an ATTACK_BUILDING on a building the sender never saw was accepted");
        });
        helper.runAfterDelay(4, () -> {
            if (buildingTarget(mine) == keep)
                problems.add("the unit took the unknown building as its target");
            if (UnitServerEvents.hasQueuedActions(mine.getId()))
                problems.add("the refused order left something in the unit's queue");
            if (!UnitActionServerboundPacket.accept(sender, me, UnitAction.ATTACK_BUILDING, -1, ids, keep.originPos,
                    BlockPos.ZERO, QueueMode.REPLACE, anyEntity, known))
                problems.add("an ATTACK_BUILDING on a building the sender knows was refused");
        });
        helper.runAfterDelay(6, () -> {
            if (buildingTarget(mine) != keep)
                problems.add("after the accepted order the unit's building target is " + buildingTarget(mine));
            // fog off for everyone on this server: the packet path's own rule accepts
            if (!UnitActionServerboundPacket.accept(sender, me, UnitAction.ATTACK_BUILDING, -1, ids, keep.originPos,
                    BlockPos.ZERO, QueueMode.REPLACE))
                problems.add("with fog off, the packet path refused an ATTACK_BUILDING");
        });
        helper.runAfterDelay(8, () -> {
            mine.discard();
            BuildingServerEvents.getBuildings().remove(keep);
            if (problems.isEmpty())
                helper.succeed();
            else
                helper.fail(String.join("; ", problems));
        });
    }
}
