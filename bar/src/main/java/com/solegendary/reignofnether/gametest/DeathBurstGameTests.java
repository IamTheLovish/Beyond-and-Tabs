package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.unit.interfaces.Unit;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Explosive deaths (backlog 11, unit/DeathBurst): a Brassworks machine or a Rocket Battery bursts when it dies. The
 * targets are Vindicators given 500 HP and no armour (so a hit reads as the burst's own number), with their AI off.
 * Each test has its own batch: a burst reaches 6 blocks at most, and nothing else may die beside it.
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class DeathBurstGameTests {

    static final String ARENA = SkirmishGameTests.ARENA;
    /** A Steam Juggernaut's burst 1.7 and 2.2 from its middle: 40 x (1 - d / 3.25). */
    static final float NEAR = 19.08f, MID = 12.92f;

    static Mob unit(GameTestHelper helper, EntityType<? extends Mob> type, String owner, double x, double z, List<Mob> made) {
        Mob m = HeroGameTests.spawnUnit(helper, type, owner, 0, 0);
        if (m == null)
            throw new net.minecraft.gametest.framework.GameTestAssertException("could not spawn " + type.getDescriptionId());
        var at = helper.absoluteVec(new net.minecraft.world.phys.Vec3(x, 2, z));
        m.moveTo(at.x, at.y, at.z, 0, 0);
        m.setNoAi(true);
        made.add(m);
        return m;
    }

    /** A target that reads damage as taken: 500 HP, no armour. */
    static Mob target(GameTestHelper helper, String owner, double x, double z, List<Mob> made) {
        Mob m = unit(helper, EntityRegistrar.VINDICATOR_UNIT.get(), owner, x, z, made);
        m.getAttribute(Attributes.MAX_HEALTH).setBaseValue(500);
        m.getAttribute(Attributes.ARMOR).setBaseValue(0);
        if (m.getAttribute(Attributes.ARMOR_TOUGHNESS) != null)
            m.getAttribute(Attributes.ARMOR_TOUGHNESS).setBaseValue(0);
        m.setHealth(500);
        return m;
    }

    static float taken(Mob m) {
        return 500f - m.getHealth();
    }

    static void discard(List<Mob> made) {
        for (Mob m : made)
            if (!m.isRemoved())
                m.discard();
    }

    /**
     * A Steam Juggernaut (1.5 wide: radius 3.25; 200 HP: peak 40) dies with three enemies round it: one beside it, one
     * further out and one past the radius. The numbers are pinned here, worked by hand from the rule (peak x (1 - d /
     * radius), d from the boiler's middle to the target's box; a Vindicator is 0.6 wide), not from DeathBurst's own
     * functions (review round 1 on #517): the near one 1.7 from it takes 40 x (1 - 1.7 / 3.25) = 19.08, the one 2.2 out
     * 40 x (1 - 2.2 / 3.25) = 12.92, the one 4.2 out nothing.
     */
    @GameTest(template = ARENA, batch = "death_burst_falloff", timeoutTicks = 60)
    public static void a_machine_bursts_on_death_and_hurts_less_further_out(GameTestHelper helper) {
        String me = HeroGameTests.owner(helper, "burst_me"), them = HeroGameTests.owner(helper, "burst_them");
        List<Mob> made = new ArrayList<>();
        Mob boiler = unit(helper, EntityRegistrar.STEAM_JUGGERNAUT_UNIT.get(), me, 8.5, 8.5, made);
        Mob near = target(helper, them, 10.5, 8.5, made);   // 2.0 from the middle, 1.7 to its box
        Mob mid = target(helper, them, 8.5, 11.0, made);    // 2.5 out, 2.2 to its box
        Mob far = target(helper, them, 4.0, 8.5, made);     // 4.5 out, 4.2 to its box: past the radius
        helper.runAfterDelay(5, () -> {
            boiler.kill();
            float n = taken(near), md = taken(mid), f = taken(far);
            discard(made);
            String got = String.format(Locale.ROOT, "near %.2f (want %.2f), mid %.2f (want %.2f), far %.2f (want 0)",
                n, NEAR, md, MID, f);
            if (n <= 0)
                helper.fail("a Steam Juggernaut's death hurt nobody beside it: " + got);
            else if (Math.abs(n - NEAR) > 0.5f)
                helper.fail("the unit beside the burst took a different amount than the falloff gives: " + got);
            else if (Math.abs(md - MID) > 0.5f)
                helper.fail("the unit further out took a different amount than the falloff gives: " + got);
            else if (f != 0)
                helper.fail("a unit past the radius was hurt: " + got);
            else
                helper.succeed();
        });
    }

    /**
     * Friendly fire: an Iron Warden dies with an enemy, an ally and one of its owner's own units each the same distance
     * away. Own and allied units take half what the enemy takes.
     */
    @GameTest(template = ARENA, batch = "death_burst_friendly", timeoutTicks = 60)
    public static void own_and_allied_units_take_half_a_burst(GameTestHelper helper) {
        String me = HeroGameTests.owner(helper, "burst_ff_me"), ally = HeroGameTests.owner(helper, "burst_ff_ally"),
            them = HeroGameTests.owner(helper, "burst_ff_them");
        AlliancesServerEvents.addAlliance(me, ally);
        List<Mob> made = new ArrayList<>();
        try {
            Mob boiler = unit(helper, EntityRegistrar.IRON_WARDEN_UNIT.get(), me, 8.5, 8.5, made);
            Mob enemy = target(helper, them, 10.0, 8.5, made);
            Mob friend = target(helper, ally, 7.0, 8.5, made);
            Mob own = target(helper, me, 8.5, 10.0, made);
            helper.runAfterDelay(5, () -> {
                boiler.kill();
                float e = taken(enemy), a = taken(friend), o = taken(own);
                AlliancesServerEvents.removeAlliance(me, ally);
                discard(made);
                String got = String.format(Locale.ROOT, "enemy %.2f, ally %.2f, own %.2f", e, a, o);
                if (e <= 0)
                    helper.fail("the enemy beside an Iron Warden's burst took nothing: " + got);
                else if (Math.abs(a / e - 0.5f) > 0.1f)
                    helper.fail("an ally must take half the enemy's share: " + got);
                else if (Math.abs(o / e - 0.5f) > 0.1f)
                    helper.fail("the owner's own unit must take half the enemy's share: " + got);
                else
                    helper.succeed();
            });
        } catch (RuntimeException ex) {
            AlliancesServerEvents.removeAlliance(me, ally);
            discard(made);
            throw ex;
        }
    }

    /**
     * Only a real death bursts: a Steam Juggernaut despawned (discarded, as a new match's retirement of the old arena or a
     * garrison's removal takes a unit off the field) hurts nobody, and neither does the death of a unit that is not a machine (a Vindicator).
     * The Rocket Battery, not a machine, does burst.
     */
    @GameTest(template = ARENA, batch = "death_burst_who", timeoutTicks = 60)
    public static void only_a_machine_or_a_rocket_battery_dying_bursts(GameTestHelper helper) {
        String me = HeroGameTests.owner(helper, "burst_who_me"), them = HeroGameTests.owner(helper, "burst_who_them");
        List<Mob> made = new ArrayList<>();
        Mob gone = unit(helper, EntityRegistrar.STEAM_JUGGERNAUT_UNIT.get(), me, 4.5, 4.5, made);
        Mob byGone = target(helper, them, 6.0, 4.5, made);
        Mob plain = unit(helper, EntityRegistrar.VINDICATOR_UNIT.get(), me, 12.5, 4.5, made);
        Mob byPlain = target(helper, them, 14.0, 4.5, made);
        Mob rack = unit(helper, EntityRegistrar.ROCKET_BATTERY_UNIT.get(), me, 8.5, 12.5, made);
        Mob byRack = target(helper, them, 9.7, 12.5, made);
        helper.runAfterDelay(5, () -> {
            gone.discard();
            plain.kill();
            rack.kill();
            float g = taken(byGone), p = taken(byPlain), r = taken(byRack);
            discard(made);
            String got = String.format(Locale.ROOT, "beside the despawned machine %.2f, beside the dead Vindicator %.2f, "
                + "beside the dead Rocket Battery %.2f", g, p, r);
            if (g != 0)
                helper.fail("a machine taken off the field without dying burst: " + got);
            else if (p != 0)
                helper.fail("a unit that is not a machine burst on death: " + got);
            else if (r <= 0)
                helper.fail("a Rocket Battery's death did not burst: " + got);
            else
                helper.succeed();
        });
    }

    /**
     * A machine that dies garrisoned does not burst: the building holds it (DeathBurst.onLivingDeath asks
     * GarrisonableBuildingAddon.getGarrison). An Iron Warden on a built Watchtower's garrison spot dies with an enemy
     * 1.3 blocks from it, and a second Warden on the ground dies beside another enemy (the burst is on): the first
     * enemy takes nothing, the second takes its share.
     */
    @GameTest(template = ARENA, batch = "death_burst_garrison", timeoutTicks = 60)
    public static void a_machine_dying_in_a_garrison_does_not_burst(GameTestHelper helper) {
        String me = HeroGameTests.owner(helper, "burst_gar_me"), them = HeroGameTests.owner(helper, "burst_gar_them");
        net.minecraft.server.level.ServerLevel level = helper.getLevel();
        net.minecraft.core.BlockPos origin = helper.absolutePos(new net.minecraft.core.BlockPos(2, 1, 2));
        TestCleanup.blocks(helper).region(origin.offset(-1, -1, -1), origin.offset(8, 16, 8));
        com.solegendary.reignofnether.building.BuildingPlacement tower = RaidGameTests.build(level,
            com.solegendary.reignofnether.building.Buildings.WATCHTOWER, origin, me, false);
        List<Mob> made = new ArrayList<>();
        try {
            if (tower == null) {
                helper.fail("could not build the Watchtower");
                return;
            }
            net.minecraft.core.BlockPos spot = ((com.solegendary.reignofnether.building.addon.GarrisonableBuildingAddon)
                tower.getBuilding().getActiveAddon(com.solegendary.reignofnether.building.addon.GarrisonableBuildingAddon.class))
                .getEntryPosition(tower);
            Mob inside = unit(helper, EntityRegistrar.IRON_WARDEN_UNIT.get(), me, 0, 0, made);
            inside.moveTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, 0, 0);
            inside.setNoGravity(true);
            Mob byInside = target(helper, them, 0, 0, made);
            byInside.moveTo(spot.getX() + 1.8, spot.getY(), spot.getZ() + 0.5, 0, 0);
            byInside.setNoGravity(true);
            Mob outside = unit(helper, EntityRegistrar.IRON_WARDEN_UNIT.get(), me, 12.5, 12.5, made);
            Mob byOutside = target(helper, them, 14.0, 12.5, made);
            helper.runAfterDelay(5, () -> {
                boolean garrisoned = com.solegendary.reignofnether.building.addon.GarrisonableBuildingAddon.getGarrison(
                    (Unit) inside) == tower;
                inside.kill();
                outside.kill();
                float in = taken(byInside), out = taken(byOutside);
                discard(made);
                com.solegendary.reignofnether.building.BuildingServerEvents.getBuildings().remove(tower);
                String got = String.format(Locale.ROOT, "beside the garrisoned Warden %.2f, beside the Warden outside %.2f "
                    + "(garrisoned %b)", in, out, garrisoned);
                if (!garrisoned)
                    helper.fail("the Warden on the Watchtower's spot is not garrisoned there: " + got);
                else if (out <= 0)
                    helper.fail("the Warden outside did not burst: " + got);
                else if (in != 0)
                    helper.fail("a machine dying in a garrison burst: " + got);
                else
                    helper.succeed();
            });
        } catch (RuntimeException ex) {
            discard(made);
            if (tower != null)
                com.solegendary.reignofnether.building.BuildingServerEvents.getBuildings().remove(tower);
            throw ex;
        }
    }

    /**
     * A match reset clears the field with entity.kill() (PlayerServerEvents.resetRTS: back to the lobby, /rts-reset,
     * /rts-reset-hard, the reset packets), and that is no death in battle: no machine bursts (review round 1 on #517,
     * blocker 1 - every machine on the map burst into its neighbours). A Steam Juggernaut cleared by the reset's own
     * kill (PlayerServerEvents.killForReset) beside an enemy: it is dead, the enemy takes nothing.
     */
    @GameTest(template = ARENA, batch = "death_burst_reset", timeoutTicks = 60)
    public static void a_match_reset_clearing_a_machine_does_not_burst(GameTestHelper helper) {
        String me = HeroGameTests.owner(helper, "burst_reset_me"), them = HeroGameTests.owner(helper, "burst_reset_them");
        List<Mob> made = new ArrayList<>();
        Mob boiler = unit(helper, EntityRegistrar.STEAM_JUGGERNAUT_UNIT.get(), me, 8.5, 8.5, made);
        Mob near = target(helper, them, 10.5, 8.5, made);   // 1.7 from the boiler's middle to its box: 19.08 if it burst
        helper.runAfterDelay(5, () -> {
            com.solegendary.reignofnether.player.PlayerServerEvents.killForReset(List.of(boiler), e -> true);
            boolean dead = !boiler.isAlive();
            float n = taken(near);
            discard(made);
            String got = String.format(Locale.ROOT, "the Juggernaut dead %b, the enemy beside it took %.2f", dead, n);
            if (!dead)
                helper.fail("the reset's kill left the Juggernaut alive (nothing tested): " + got);
            else if (n != 0)
                helper.fail("a machine cleared by a match reset burst: " + got);
            else
                helper.succeed();
        });
    }

    /**
     * A defeated side's units are cleared with a kill over the next ticks (DefeatCleanupServerEvents), and that is
     * bookkeeping, not combat: none of its machines bursts into the winner's army (Command Center Eris.14, Nov 5,
     * provisional until lovish confirms). A defeated owner's Steam Juggernaut handed to the cleanup beside an enemy 1.7
     * from its middle: it is dead 10 ticks later, the enemy took nothing.
     */
    @GameTest(template = ARENA, batch = "death_burst_defeat", timeoutTicks = 60)
    public static void a_defeated_sides_machine_cleared_by_the_cleanup_does_not_burst(GameTestHelper helper) {
        String loser = HeroGameTests.owner(helper, "burst_def_loser"), them = HeroGameTests.owner(helper, "burst_def_them");
        List<Mob> made = new ArrayList<>();
        Mob boiler = unit(helper, EntityRegistrar.STEAM_JUGGERNAUT_UNIT.get(), loser, 8.5, 8.5, made);
        Mob near = target(helper, them, 10.5, 8.5, made);   // 19.08 if it burst
        helper.runAfterDelay(5, () -> com.solegendary.reignofnether.player.DefeatCleanupServerEvents.begin(
            helper.getLevel(), loser, List.of(), List.of(boiler)));
        helper.runAfterDelay(15, () -> {
            boolean dead = !boiler.isAlive();
            float n = taken(near);
            discard(made);
            String got = String.format(Locale.ROOT, "the Juggernaut dead %b, the enemy beside it took %.2f", dead, n);
            if (!dead)
                helper.fail("the defeat cleanup left the Juggernaut alive (nothing tested): " + got);
            else if (n != 0)
                helper.fail("a defeated side's machine cleared by the cleanup burst: " + got);
            else
                helper.succeed();
        });
    }

    /**
     * The Delete order kills the unit, and a deleted machine does not burst: else deleting your own machine beside the
     * enemy is a free bomb (Command Center Eris.14, Nov 5, provisional until lovish confirms). The owner deletes a Steam
     * Juggernaut (the order the client sends) with an enemy 1.7 from its middle: it is dead, the enemy took nothing.
     */
    @GameTest(template = ARENA, batch = "death_burst_delete", timeoutTicks = 60)
    public static void a_deleted_machine_does_not_burst(GameTestHelper helper) {
        String me = HeroGameTests.owner(helper, "burst_del_me"), them = HeroGameTests.owner(helper, "burst_del_them");
        List<Mob> made = new ArrayList<>();
        Mob boiler = unit(helper, EntityRegistrar.STEAM_JUGGERNAUT_UNIT.get(), me, 8.5, 8.5, made);
        Mob near = target(helper, them, 10.5, 8.5, made);   // 19.08 if it burst
        helper.runAfterDelay(5, () -> {
            new com.solegendary.reignofnether.unit.UnitActionItem(me, com.solegendary.reignofnether.unit.UnitAction.DELETE,
                -1, new int[] { boiler.getId() }).action(helper.getLevel());
            boolean dead = !boiler.isAlive();
            float n = taken(near);
            discard(made);
            String got = String.format(Locale.ROOT, "the Juggernaut dead %b, the enemy beside it took %.2f", dead, n);
            if (!dead)
                helper.fail("the Delete order left the Juggernaut alive (nothing tested): " + got);
            else if (n != 0)
                helper.fail("a deleted machine burst: " + got);
            else
                helper.succeed();
        });
    }

    /**
     * The burst deals its full damage to a unit hit a moment before (review round 1 on #517, blocker 2). The burst has
     * no causing entity, so LivingEntityMixin.unitHitsStack (which opens vanilla's hurt window for a hit a unit causes)
     * leaves it shut: a target hit earlier the same tick took only the burst's excess over that hit, and two bursts in
     * one tick did not add. An enemy 1.7 from a Steam Juggernaut's middle is hit for 10 (vanilla's window: 20 ticks, last
     * hit 10), then the Juggernaut dies: it must take the burst's 19.08 on top, 29.08 in all - not 19.08 (9.08 more).
     */
    @GameTest(template = ARENA, batch = "death_burst_window", timeoutTicks = 60)
    public static void a_burst_hits_a_unit_hit_the_same_tick_in_full(GameTestHelper helper) {
        String me = HeroGameTests.owner(helper, "burst_win_me"), them = HeroGameTests.owner(helper, "burst_win_them");
        List<Mob> made = new ArrayList<>();
        Mob boiler = unit(helper, EntityRegistrar.STEAM_JUGGERNAUT_UNIT.get(), me, 8.5, 8.5, made);
        Mob near = target(helper, them, 10.5, 8.5, made);
        helper.runAfterDelay(5, () -> {
            boolean first = near.hurt(helper.getLevel().damageSources().generic(), 10f);
            float before = taken(near);
            int window = near.invulnerableTime;
            boiler.kill();
            float burst = taken(near) - before;
            discard(made);
            String got = String.format(Locale.ROOT, "first hit landed %b (%.2f, window %d), then the burst %.2f (want %.2f)",
                first, before, window, burst, NEAR);
            if (!first || Math.abs(before - 10f) > 0.01f)
                helper.fail("the first hit did not land as 10 (nothing tested): " + got);
            else if (Math.abs(burst - NEAR) > 0.5f)
                helper.fail("a unit hit the same tick took less than the burst's full damage: " + got);
            else
                helper.succeed();
        });
    }
}
