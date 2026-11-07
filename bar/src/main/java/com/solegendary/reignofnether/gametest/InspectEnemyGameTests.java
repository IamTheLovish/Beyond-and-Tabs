package com.solegendary.reignofnether.gametest;

import com.mojang.authlib.GameProfile;
import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.fogofwar.FogOfWarServerEvents;
import com.solegendary.reignofnether.unit.InspectRules;
import com.solegendary.reignofnether.unit.InspectRules.Info;
import com.solegendary.reignofnether.unit.InspectRules.Tag;
import com.solegendary.reignofnether.unit.Relationship;
import com.solegendary.reignofnether.unit.SelectionKeep;
import com.solegendary.reignofnether.unit.UnitAction;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.packets.UnitActionServerboundPacket;
import com.solegendary.reignofnether.unit.units.villagers.VindicatorUnit;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Inspecting enemy units (tester request Oct 26): "select enemy units to pull up their information just like your
 * own, you just can't command them ... it shouldn't show the abilities for heroes". The selection and the card are
 * client code; they ask {@link InspectRules}, which these tests drive with the server's real answers - fog of war's
 * entity check (what the client is even sent), the unit relationship, the order packet's ownership gate and the
 * selection's death rule.
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class InspectEnemyGameTests {

    static String name(GameTestHelper helper, String tag) {
        return "gt_insp_" + tag + "_" + Long.toHexString(helper.absolutePos(BlockPos.ZERO).asLong());
    }

    static ServerPlayer viewer(GameTestHelper helper, String name) {
        ServerPlayer p = FakePlayerFactory.get(helper.getLevel(),
            new GameProfile(UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8)), name));
        // its own first-person sight bubble far away, so only its units give it vision
        BlockPos o = helper.absolutePos(BlockPos.ZERO);
        p.moveTo(o.getX() + 4000, o.getY(), o.getZ() + 4000, 0, 0);
        return p;
    }

    /** A Vindicator for {@code owner} at arena (x, z) on Hold fire, so neighbours never start a fight mid-test. */
    static VindicatorUnit unit(GameTestHelper helper, String owner, double x, double z) {
        VindicatorUnit v = FogHidesEnemiesGameTests.unit(helper, owner, x, z);
        if (v != null)
            com.solegendary.reignofnether.unit.EngagementStance.set(v,
                com.solegendary.reignofnether.unit.EngagementStance.HOLD_FIRE);
        return v;
    }

    /**
     * The viewer's relation to {@code u} as the client card sees it (player-to-unit: own is OWNED). Not
     * UnitServerEvents.getUnitToEntityRelationship, a unit-to-unit relation that calls own units FRIENDLY.
     */
    static Relationship rel(ServerPlayer viewer, VindicatorUnit u) {
        String me = viewer.getName().getString();
        return InspectRules.relation(me, u.getOwnerName(),
            com.solegendary.reignofnether.alliance.AlliancesServerEvents.isAllied(me, u.getOwnerName()));
    }

    static void finish(GameTestHelper helper, List<String> problems) {
        if (problems.isEmpty())
            helper.succeed();
        else
            helper.fail(String.join("; ", problems));
    }

    /**
     * AC-1: an enemy in sight is inspectable with its public card (portrait, name, health, stats; a building's type
     * and health); an enemy under fog is never sent to the client (the fog entity check) and fails the client's
     * inspect test too, as does a cloaked unit or a rider.
     */
    @GameTest(template = SkirmishGameTests.ARENA, timeoutTicks = 60)
    public static void ac1_only_visible_enemies_can_be_inspected(GameTestHelper helper) {
        String me = name(helper, "a1"), foe = me + "_foe";
        ServerPlayer viewer = viewer(helper, me);
        VindicatorUnit mine = unit(helper, me, 2.5, 2.5);
        VindicatorUnit near = unit(helper, foe, 4.5, 2.5);
        VindicatorUnit far = mine == null ? null
            : unit(helper, foe, 2.5 + mine.getSightRange() + 12, 2.5);
        helper.runAfterDelay(2, () -> {
            List<String> problems = new ArrayList<>();
            try {
                if (mine == null || near == null || far == null) {
                    problems.add("could not create the units");
                    return;
                }
                FogOfWarServerEvents.recomputeVisionFor(viewer);
                boolean nearSeen = FogOfWarServerEvents.isEntityVisibleUnderFog(viewer, near);
                boolean farSeen = FogOfWarServerEvents.isEntityVisibleUnderFog(viewer, far);
                if (!InspectRules.mayInspect(nearSeen, false, false))
                    problems.add("an enemy 2 blocks from my unit cannot be inspected");
                if (InspectRules.mayInspect(farSeen, false, false))
                    problems.add("an enemy outside my vision can be inspected");
                if (InspectRules.mayInspect(true, true, false))
                    problems.add("a cloaked enemy can be inspected");
                if (InspectRules.mayInspect(true, false, true))
                    problems.add("a rider can be inspected");
                Relationship r = rel(viewer, near);
                if (r != Relationship.HOSTILE)
                    problems.add("the enemy's relation is " + r + ", not HOSTILE");
                for (Info i : new Info[] { Info.PORTRAIT, Info.NAME, Info.HEALTH, Info.STATS, Info.BUILDING_TYPE })
                    if (!InspectRules.shows(i, r, false, false))
                        problems.add("an inspected enemy hides its " + i);
            } finally {
                FogOfWarServerEvents.onPlayerDisconnect(viewer.getUUID());
                for (VindicatorUnit u : new VindicatorUnit[] { mine, near, far })
                    if (u != null)
                        u.discard();
                finish(helper, problems);
            }
        });
    }

    /**
     * AC-2: read-only. An order packet naming the inspected enemy is refused for that unit (in the sender's own name:
     * the enemy is filtered out and does not move; in the enemy's name: refused outright); the owner's own order
     * still works. No command card, and an enemy never stays in a group (box / Shift-click / Ctrl+A take own units).
     */
    @GameTest(template = SkirmishGameTests.ARENA, timeoutTicks = 100)
    public static void ac2_orders_naming_an_inspected_enemy_are_refused(GameTestHelper helper) {
        String me = name(helper, "a2"), foe = me + "_foe";
        ServerPlayer viewer = viewer(helper, me);
        ServerPlayer owner = viewer(helper, foe);
        VindicatorUnit enemy = unit(helper, foe, 2.5, 2.5);
        List<String> problems = new ArrayList<>();
        if (enemy == null) {
            helper.fail("could not create the enemy unit");
            return;
        }
        int[] ids = { enemy.getId() };
        BlockPos to = helper.absolutePos(new BlockPos(13, 2, 13));
        helper.runAfterDelay(2, () -> {
            if (UnitActionServerboundPacket.accept(viewer, foe, UnitAction.MOVE, -1, ids, to, BlockPos.ZERO, false))
                problems.add("an order in the enemy's name was accepted");
            UnitActionServerboundPacket.accept(viewer, me, UnitAction.MOVE, -1, ids, to, BlockPos.ZERO, false);
            UnitActionServerboundPacket.accept(viewer, me, UnitAction.STOP, -1, ids, to, BlockPos.ZERO, true);
            Relationship r = rel(viewer, enemy);
            if (InspectRules.canCommand(r, false, false))
                problems.add("an enemy (" + r + ") is commandable");
            if (InspectRules.keepInSelection(2, InspectRules.canCommand(r, false, false)))
                problems.add("an enemy stays in a group selection");
        });
        helper.runAfterDelay(6, () -> {
            if (enemy.getMoveGoal().getMoveTarget() != null || UnitServerEvents.hasQueuedActions(enemy.getId()))
                problems.add("the enemy took the inspecting player's order (target " + enemy.getMoveGoal().getMoveTarget() + ")");
            if (!UnitActionServerboundPacket.accept(owner, foe, UnitAction.MOVE, -1, ids, to, BlockPos.ZERO, false))
                problems.add("the owner's own order was refused");
        });
        helper.runAfterDelay(9, () -> {
            try {
                if (!to.equals(enemy.getMoveGoal().getMoveTarget()))
                    problems.add("the owner's unit did not take its owner's order (target " + enemy.getMoveGoal().getMoveTarget() + ")");
            } finally {
                UnitServerEvents.clearQueuedActions(enemy.getId());
                enemy.discard();
                finish(helper, problems);
            }
        });
    }

    /**
     * AC-3: an enemy's card hides its plans - abilities and cooldowns (a hero's too), items, rally - and carries a red "Enemy" tag; an ally's card shows them with an "Ally" tag; my own card no tag.
     */
    @GameTest(template = SkirmishGameTests.ARENA, timeoutTicks = 40)
    public static void ac3_enemy_card_hides_plans_and_is_tagged(GameTestHelper helper) {
        String me = name(helper, "a3"), foe = me + "_foe";
        ServerPlayer viewer = viewer(helper, me);
        VindicatorUnit enemy = unit(helper, foe, 2.5, 2.5);
        VindicatorUnit mine = unit(helper, me, 5.5, 2.5);
        helper.runAfterDelay(1, () -> {
            List<String> problems = new ArrayList<>();
            try {
                if (enemy == null || mine == null) {
                    problems.add("could not create the units");
                    return;
                }
                Relationship re = rel(viewer, enemy), rm = rel(viewer, mine);
                for (Info i : new Info[] { Info.ABILITIES, Info.ITEMS, Info.RALLY })
                    if (InspectRules.shows(i, re, false, false))
                        problems.add("an enemy card shows its " + i);
                if (InspectRules.tag(re) != Tag.ENEMY || !"Enemy".equals(InspectRules.tag(re).label))
                    problems.add("an enemy card is tagged " + InspectRules.tag(re));
                if (InspectRules.tag(rm) != Tag.NONE)
                    problems.add("my own card is tagged " + InspectRules.tag(rm));
                for (Info i : Info.values())
                    if (!InspectRules.shows(i, rm, false, false))
                        problems.add("my own card hides its " + i);
                if (!InspectRules.shows(Info.ABILITIES, Relationship.FRIENDLY, false, false)
                        || InspectRules.tag(Relationship.FRIENDLY) != Tag.ALLY)
                    problems.add("an ally card hides its abilities or is not tagged Ally");
            } finally {
                if (enemy != null) enemy.discard();
                if (mine != null) mine.discard();
                finish(helper, problems);
            }
        });
    }

    /**
     * AC-4: one or the other. Selecting own units drops an inspected enemy (a group keeps controllable units only);
     * an inspected enemy that dies, or walks out of vision into fog, drops out of the selection.
     */
    @GameTest(template = SkirmishGameTests.ARENA, timeoutTicks = 60)
    public static void ac4_inspection_clears_on_own_select_death_or_fog(GameTestHelper helper) {
        String me = name(helper, "a4"), foe = me + "_foe";
        ServerPlayer viewer = viewer(helper, me);
        VindicatorUnit mine = unit(helper, me, 2.5, 2.5);
        VindicatorUnit enemy = unit(helper, foe, 4.5, 2.5);
        List<String> problems = new ArrayList<>();
        if (mine == null || enemy == null) {
            if (mine != null) mine.discard();
            if (enemy != null) enemy.discard();
            helper.fail("could not create the units");
            return;
        }
        helper.runAfterDelay(2, () -> {
            Relationship re = rel(viewer, enemy), rm = rel(viewer, mine);
            if (InspectRules.keepInSelection(2, InspectRules.canCommand(re, false, false)))
                problems.add("selecting an own unit next to the inspected enemy keeps the enemy");
            if (!InspectRules.keepInSelection(2, InspectRules.canCommand(rm, false, false)))
                problems.add("an own unit is dropped from a group");
            if (!InspectRules.keepInSelection(1, InspectRules.canCommand(re, false, false)))
                problems.add("a lone inspected enemy is dropped");
            // walks out of sight: next vision recompute hides it
            FogOfWarServerEvents.recomputeVisionFor(viewer);
            if (!InspectRules.mayInspect(FogOfWarServerEvents.isEntityVisibleUnderFog(viewer, enemy), false, false))
                problems.add("the enemy in sight is not inspectable");
            enemy.moveTo(enemy.getX() + mine.getSightRange() + 12, enemy.getY(), enemy.getZ(), 0, 0);
            FogOfWarServerEvents.recomputeVisionFor(viewer);
            if (InspectRules.mayInspect(FogOfWarServerEvents.isEntityVisibleUnderFog(viewer, enemy), false, false))
                problems.add("the enemy stays inspectable after walking into fog");
            if (SelectionKeep.shouldDrop(enemy.isDeadOrDying(), enemy.getRemovalReason()))
                problems.add("a living enemy is dropped as dead");
            enemy.kill();
        });
        helper.runAfterDelay(4, () -> {
            try {
                if (!SelectionKeep.shouldDrop(enemy.isDeadOrDying(), enemy.getRemovalReason()))
                    problems.add("a killed enemy stays inspected");
            } finally {
                FogOfWarServerEvents.onPlayerDisconnect(viewer.getUUID());
                mine.discard();
                enemy.discard();
                finish(helper, problems);
            }
        });
    }
}
