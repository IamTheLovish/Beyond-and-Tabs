package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.unit.DeathBurst;
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
     * A Steam Juggernaut (1.5 wide: radius 3.25, peak 40) dies with three enemies round it: one beside it, one further
     * out and one past the radius. The near one takes the most, close to DeathBurst.damageAt; the far one nothing.
     */
    @GameTest(template = ARENA, batch = "death_burst_falloff", timeoutTicks = 60)
    public static void a_machine_bursts_on_death_and_hurts_less_further_out(GameTestHelper helper) {
        String me = HeroGameTests.owner(helper, "burst_me"), them = HeroGameTests.owner(helper, "burst_them");
        List<Mob> made = new ArrayList<>();
        Mob boiler = unit(helper, EntityRegistrar.STEAM_JUGGERNAUT_UNIT.get(), me, 8.5, 8.5, made);
        Mob near = target(helper, them, 10.5, 8.5, made);   // 2.0 from the middle, 1.7 to its box: ~19 of 40
        Mob mid = target(helper, them, 8.5, 11.0, made);    // 2.5 out, 2.2 to its box: ~13
        Mob far = target(helper, them, 4.0, 8.5, made);     // 4.5 out, 4.2 to its box: past the radius
        float radius = DeathBurst.radius(boiler.getBbWidth()), peak = DeathBurst.peak(boiler.getMaxHealth());
        helper.runAfterDelay(5, () -> {
            var centre = boiler.position().add(0, boiler.getBbHeight() / 2.0, 0);
            float wantNear = DeathBurst.damageAt(peak, radius, distance(near, centre));
            boiler.kill();
            float n = taken(near), md = taken(mid), f = taken(far);
            discard(made);
            String got = String.format(Locale.ROOT, "near %.1f (want %.1f), mid %.1f, far %.1f; radius %.2f, peak %.1f",
                n, wantNear, md, f, radius, peak);
            if (n <= 0)
                helper.fail("a Steam Juggernaut's death hurt nobody beside it: " + got);
            else if (Math.abs(n - wantNear) > wantNear * 0.25f)
                helper.fail("the unit beside the burst took a different amount than the falloff gives: " + got);
            else if (!(md > 0 && md < n))
                helper.fail("the unit further out must take less than the one beside it, and some: " + got);
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
                else if (Math.abs(a / e - DeathBurst.FRIENDLY_SHARE) > 0.1f)
                    helper.fail("an ally must take " + DeathBurst.FRIENDLY_SHARE + " of the enemy's share: " + got);
                else if (Math.abs(o / e - DeathBurst.FRIENDLY_SHARE) > 0.1f)
                    helper.fail("the owner's own unit must take " + DeathBurst.FRIENDLY_SHARE + " of the enemy's share: " + got);
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
     * Only a real death bursts: a Steam Juggernaut despawned (discarded, as a match reset or a garrison's removal takes
     * a unit off the field) hurts nobody, and neither does the death of a unit that is not a machine (a Vindicator).
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

    static double distance(Mob target, net.minecraft.world.phys.Vec3 p) {
        var box = target.getBoundingBox();
        double dx = Math.max(Math.max(box.minX - p.x, 0), p.x - box.maxX);
        double dy = Math.max(Math.max(box.minY - p.y, 0), p.y - box.maxY);
        double dz = Math.max(Math.max(box.minZ - p.z, 0), p.z - box.maxZ);
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}
