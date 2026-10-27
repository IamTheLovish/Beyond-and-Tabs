package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.building.BuildingServerEvents;
import com.solegendary.reignofnether.building.Buildings;
import com.solegendary.reignofnether.hero.HeroKit;
import com.solegendary.reignofnether.hero.HeroKits;
import com.solegendary.reignofnether.hero.HeroProgression;
import com.solegendary.reignofnether.hero.HeroServerEvents;
import com.solegendary.reignofnether.player.CommanderServerEvents;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.registrars.GameRuleRegistrar;
import com.solegendary.reignofnether.resources.ResourceCosts;
import com.solegendary.reignofnether.resources.Resources;
import com.solegendary.reignofnether.resources.ResourcesServerEvents;
import com.solegendary.reignofnether.resources.WreckServerEvents;
import com.solegendary.reignofnether.unit.interfaces.HeroUnit;
import com.solegendary.reignofnether.unit.interfaces.Unit;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * "Make winning pay" (lovish, Oct 24: like Beyond All Reason, winning a fight should let the winner tech faster, and
 * decisive kills matter). The wrecks and the hero XP orbs already existed; players did not feel them. Written before
 * the code, from his numbers:
 * <ul>
 *   <li>a commander's death leaves a 600 metal wreck, a hero's death one of 150 + 50 per hero level (never less than
 *       the unit's normal half-cost heap);</li>
 *   <li>when a commander or a hero dies, every enemy hero within 28 blocks gets a one-time XP burst on top of the
 *       normal orbs: 400 for a commander, 150 + 50 per victim level for a hero (not own or allied heroes);</li>
 *   <li>an idle worker with a wreck within 8 blocks starts stripping it on its own, but never one that is building,
 *       walking to an order, a commander, or when the {@code idleReclaim} gamerule is off.</li>
 * </ul>
 * The heroes that must show the burst stand 24 blocks above the kill (noAi, noGravity): outside the normal orbs'
 * 20-block reach and inside the burst's 28, so the XP they end with is exactly the burst - which also proves the
 * normal per-kill XP did not change. Every test has its own owners, wrecks and positions (they run in parallel).
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class WinPaysGameTests {

    static final String ARENA = SkirmishGameTests.ARENA;

    private static String owner(GameTestHelper helper, String what) {
        return "gametest_winpays_" + what + "_" + helper.absolutePos(BlockPos.ZERO).asLong();
    }

    /** Wrecks standing where a unit fell. */
    private static List<Entity> wrecksAt(Vec3 at) {
        List<Entity> found = new ArrayList<>();
        for (Entity w : WreckServerEvents.getWrecks())
            if (!w.isRemoved() && w.position().distanceToSqr(at) < 1.5 * 1.5)
                found.add(w);
        return found;
    }

    private static float richest(List<Entity> wrecks) {
        float best = 0;
        for (Entity w : wrecks)
            best = Math.max(best, WreckServerEvents.metalOf(w));
        return best;
    }

    /** A hero parked {@code up} blocks above arena cell (x, z): it cannot fall, walk off or be pushed. */
    private static Mob hangingHero(GameTestHelper helper, HeroKit kit, String owner, int x, int z, int up) {
        Mob hero = HeroGameTests.spawnHero(helper, kit, owner, x, z);
        if (hero == null)
            return null;
        hero.setNoAi(true);
        hero.setNoGravity(true);
        hero.setPos(hero.getX(), hero.getY() + up, hero.getZ());
        return hero;
    }

    private static int xp(Mob hero) {
        return ((HeroUnit) hero).getExperience();
    }

    // ------------------------------------------------------------------------------ the wreck a decisive kill leaves

    /** A commander's death leaves a 600 metal wreck; an ordinary worker of the same type still leaves its small heap. */
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void commander_death_leaves_a_600_metal_wreck(GameTestHelper helper) {
        String owner = owner(helper, "cwreck");
        Mob commander = HeroGameTests.spawnUnit(helper, EntityRegistrar.VILLAGER_UNIT.get(), owner, 3, 3);
        Mob plain = HeroGameTests.spawnUnit(helper, EntityRegistrar.VILLAGER_UNIT.get(), owner, 12, 3);
        if (commander == null || plain == null) {
            helper.fail("could not spawn the commander and the worker");
            return;
        }
        CommanderServerEvents.makeCommander(commander);
        Vec3 commanderAt = commander.position(), plainAt = plain.position();
        helper.runAfterDelay(3, () -> {
            commander.kill();
            plain.kill();
        });
        helper.runAfterDelay(8, () -> {
            List<Entity> cw = wrecksAt(commanderAt), pw = wrecksAt(plainAt);
            float got = richest(cw), small = richest(pw);
            float normal = ResourceCosts.VILLAGER.metal() * WreckServerEvents.WRECK_SHARE;
            cw.forEach(Entity::discard);
            pw.forEach(Entity::discard);
            if (WreckServerEvents.COMMANDER_WRECK_METAL != 600f)
                helper.fail("lovish's commander wreck is 600 metal, the constant says " + WreckServerEvents.COMMANDER_WRECK_METAL);
            else if (Math.abs(got - 600f) > 0.5f)
                helper.fail("the fallen commander's wreck holds " + got + " metal, expected 600");
            else if (Math.abs(small - normal) > 0.5f)
                helper.fail("an ordinary worker's wreck should stay at " + normal + " metal, got " + small);
            else
                helper.succeed();
        });
    }

    /** A hero's death leaves 150 + 50 x its level (here level 1 and level 5), never less than the normal half-cost heap. */
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void hero_death_leaves_a_level_scaled_wreck(GameTestHelper helper) {
        String owner = owner(helper, "hwreck");   // one owner for both: neither is the other's enemy, no XP burst here
        Mob low = HeroGameTests.spawnHero(helper, HeroKits.DAWN_PALADIN, owner, 3, 3);
        Mob high = HeroGameTests.spawnHero(helper, HeroKits.HORDE_WARCHIEF, owner, 12, 12);
        if (low == null || high == null) {
            helper.fail("could not spawn the two heroes");
            return;
        }
        ((HeroUnit) high).addExperience(HeroProgression.xpForLevel(5));
        int lowLevel = ((HeroUnit) low).getHeroLevel(), highLevel = ((HeroUnit) high).getHeroLevel();
        if (lowLevel != 1 || highLevel != 5) {
            helper.fail("test heroes should be level 1 and 5, are " + lowLevel + " and " + highLevel);
            return;
        }
        Vec3 lowAt = low.position(), highAt = high.position();
        float lowCost = ((Unit) low).getCost().metal() * WreckServerEvents.WRECK_SHARE;
        float highCost = ((Unit) high).getCost().metal() * WreckServerEvents.WRECK_SHARE;
        helper.runAfterDelay(3, () -> {
            low.kill();
            high.kill();
        });
        helper.runAfterDelay(8, () -> {
            List<Entity> lw = wrecksAt(lowAt), hw = wrecksAt(highAt);
            float gotLow = richest(lw), gotHigh = richest(hw);
            lw.forEach(Entity::discard);
            hw.forEach(Entity::discard);
            HeroServerEvents.fallenHeroes.removeIf(f -> f.ownerName.equals(owner));
            float wantLow = Math.max(lowCost, 200f), wantHigh = Math.max(highCost, 400f);   // 150 + 50 x 1, 150 + 50 x 5
            if (WreckServerEvents.heroWreckMetal(1) != 200f || WreckServerEvents.heroWreckMetal(5) != 400f)
                helper.fail("hero wreck formula should be 150 + 50 x level (200 at 1, 400 at 5), got "
                    + WreckServerEvents.heroWreckMetal(1) + " and " + WreckServerEvents.heroWreckMetal(5));
            else if (Math.abs(gotLow - wantLow) > 0.5f)
                helper.fail("the level 1 hero's wreck holds " + gotLow + " metal, expected " + wantLow);
            else if (Math.abs(gotHigh - wantHigh) > 0.5f)
                helper.fail("the level 5 hero's wreck holds " + gotHigh + " metal, expected " + wantHigh);
            else if (gotHigh <= gotLow)
                helper.fail("a higher level hero must leave a bigger wreck (" + gotLow + " vs " + gotHigh + ")");
            else
                helper.succeed();
        });
    }

    // ------------------------------------------------------------------------------------------------ the XP burst

    /**
     * A fallen commander pays every ENEMY hero within 28 blocks 400 XP once. The heroes hang 24 blocks up: the enemy
     * gets exactly 400 (no normal orbs reach it, so the per-kill XP is unchanged); one 31 blocks up, the commander's
     * own hero and an allied hero get nothing.
     */
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void commander_kill_gives_a_nearby_enemy_hero_400_xp(GameTestHelper helper) {
        String victim = owner(helper, "cv"), foe = owner(helper, "cf"), foe2 = owner(helper, "cf2"), ally = owner(helper, "ca");
        AlliancesServerEvents.addAlliance(victim, ally);
        Mob commander = HeroGameTests.spawnUnit(helper, EntityRegistrar.VILLAGER_UNIT.get(), victim, 3, 3);
        Mob inRange = hangingHero(helper, HeroKits.DAWN_PALADIN, foe, 3, 3, 24);
        Mob tooFar = hangingHero(helper, HeroKits.HORDE_WARCHIEF, foe2, 6, 3, 31);
        Mob own = hangingHero(helper, HeroKits.THORNHEART_DRUID, victim, 9, 3, 24);
        Mob allied = hangingHero(helper, HeroKits.GRAVECALLER, ally, 12, 3, 24);
        if (commander == null || inRange == null || tooFar == null || own == null || allied == null) {
            AlliancesServerEvents.removeAlliance(victim, ally);
            helper.fail("could not spawn the commander and the four heroes");
            return;
        }
        CommanderServerEvents.makeCommander(commander);
        List<Mob> all = List.of(inRange, tooFar, own, allied);
        helper.runAfterDelay(3, commander::kill);
        helper.runAfterDelay(8, () -> {
            int a = xp(inRange), b = xp(tooFar), c = xp(own), d = xp(allied);
            all.forEach(Mob::discard);
            AlliancesServerEvents.removeAlliance(victim, ally);
            wrecksAt(commander.position()).forEach(Entity::discard);
            if (HeroProgression.COMMANDER_KILL_XP != 400 || HeroProgression.BURST_RANGE != 28)
                helper.fail("lovish's numbers: commander kill 400 XP within 28 blocks, constants say "
                    + HeroProgression.COMMANDER_KILL_XP + " / " + HeroProgression.BURST_RANGE);
            else if (a != 400)
                helper.fail("the enemy hero 24 blocks away should get exactly the 400 XP burst, has " + a);
            else if (b != 0)
                helper.fail("a hero 31 blocks away is out of the burst's 28 blocks but has " + b + " XP");
            else if (c != 0)
                helper.fail("the dead commander's own hero was paid " + c + " XP");
            else if (d != 0)
                helper.fail("an allied hero was paid " + d + " XP");
            else
                helper.succeed();
        });
    }

    /** A fallen hero pays a nearby enemy hero 150 + 50 x the victim's level (here level 3: 300) once. */
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void hero_kill_gives_a_nearby_enemy_hero_a_level_scaled_burst(GameTestHelper helper) {
        String victimOwner = owner(helper, "hv"), foe = owner(helper, "hf");
        Mob victim = HeroGameTests.spawnHero(helper, HeroKits.DAWN_PALADIN, victimOwner, 3, 3);
        Mob killer = hangingHero(helper, HeroKits.HORDE_WARCHIEF, foe, 3, 3, 24);
        if (victim == null || killer == null) {
            helper.fail("could not spawn the two heroes");
            return;
        }
        ((HeroUnit) victim).addExperience(HeroProgression.xpForLevel(3));
        if (((HeroUnit) victim).getHeroLevel() != 3) {
            helper.fail("the victim should be level 3, is " + ((HeroUnit) victim).getHeroLevel());
            return;
        }
        Vec3 victimAt = victim.position();
        helper.runAfterDelay(3, victim::kill);
        helper.runAfterDelay(8, () -> {
            int got = xp(killer);
            killer.discard();
            wrecksAt(victimAt).forEach(Entity::discard);
            HeroServerEvents.fallenHeroes.removeIf(f -> f.ownerName.equals(victimOwner));
            if (HeroProgression.killBurstXp(false, 3) != 300 || HeroProgression.killBurstXp(false, 1) != 200)
                helper.fail("hero kill burst should be 150 + 50 x victim level (300 at 3, 200 at 1)");
            else if (got != 300)
                helper.fail("the enemy hero should get exactly 150 + 50 x 3 = 300 XP, has " + got);
            else
                helper.succeed();
        });
    }

    // ------------------------------------------------------------------------------------------ idle auto-reclaim

    /**
     * lovish's backlog #32: an idle worker with a wreck 6 blocks away (outside the 3-block strip reach, inside the
     * 8-block idle reach) walks to it on its own and strips it - no order given, the real server tick drives it.
     */
    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void idle_worker_near_a_wreck_starts_reclaiming(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = owner(helper, "idle");
        Resources pool = new Resources(owner, 0, 0, 0);
        ResourcesServerEvents.resourcesList.add(pool);
        Mob worker = HeroGameTests.spawnUnit(helper, EntityRegistrar.VILLAGER_UNIT.get(), owner, 8, 14);
        BlockPos at = helper.absolutePos(new BlockPos(8, 2, 8));
        Entity wreck = WreckServerEvents.spawnWreck(level, at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 100f,
            com.solegendary.reignofnether.faction.Factions.VILLAGERS);
        if (worker == null || wreck == null) {
            ResourcesServerEvents.resourcesList.remove(pool);
            helper.fail("could not spawn the worker or the wreck");
            return;
        }
        if (worker.distanceToSqr(wreck) < 4.5 * 4.5 || worker.distanceToSqr(wreck) > 7.5 * 7.5) {
            helper.fail("test setup: the worker should start 4.5 to 7.5 blocks from the wreck");
            return;
        }
        helper.succeedWhen(() -> {
            float left = wreck.isRemoved() ? 0 : WreckServerEvents.metalOf(wreck);
            boolean started = left < 99.9f && pool.getMetal() > 0;
            if (!started) {
                helper.fail("the idle worker never started stripping the wreck (" + left + " left, "
                    + pool.getMetal() + " metal gained, target " + WreckServerEvents.getReclaimTarget(worker) + ")");
            }
            worker.discard();
            wreck.discard();
            ResourcesServerEvents.resourcesList.remove(pool);
        });
    }

    /**
     * What auto-reclaim must NOT do, and the gamerule: with the rule off nobody is sent; with it on only the idle
     * builder is - not a worker building a site, one walking to a move order, or the commander. All judged in one
     * synchronous call each (no tick lands in between), on a platform in this test's own column (as
     * builders_send_nano_beams does).
     */
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void idle_reclaim_never_overrides_orders_or_building(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos base = helper.absolutePos(new BlockPos(8, 1, 8));
        final int y = 230;
        int cx = base.getX(), cz = base.getZ();
        for (int dx = -8; dx <= 8; dx++)
            for (int dz = -8; dz <= 8; dz++) {
                level.setBlock(new BlockPos(cx + dx, y - 1, cz + dz), Blocks.DIRT.defaultBlockState(), 3);
                level.setBlock(new BlockPos(cx + dx, y, cz + dz), Blocks.GRASS_BLOCK.defaultBlockState(), 3);
            }
        String owner = owner(helper, "busy");
        var site = BuildingServerEvents.placeBuilding(Buildings.WIND_GENERATOR_VILLAGERS, new BlockPos(cx, y, cz),
            Rotation.NONE, owner, new int[0], false, false, true, true);
        var builder = EntityRegistrar.VILLAGER_UNIT.get().create(level);
        var walker = EntityRegistrar.VILLAGER_UNIT.get().create(level);
        var commander = EntityRegistrar.VILLAGER_UNIT.get().create(level);
        var idleA = EntityRegistrar.VILLAGER_UNIT.get().create(level);
        var idleB = EntityRegistrar.VILLAGER_UNIT.get().create(level);
        if (site == null || builder == null || walker == null || commander == null || idleA == null || idleB == null) {
            helper.fail("could not set up the site (" + site + ") or the workers");
            return;
        }
        // the wreck at (cx+6.5, cz+0.5); everyone is 4 to 7 blocks from it: inside the 8-block idle reach, outside the
        // 3-block strip reach (the 3x3 wind generator stands at cx..cx+2, clear of them all)
        Entity wreck = WreckServerEvents.spawnWreck(level, cx + 6.5, y + 1, cz + 0.5, 300f,
            com.solegendary.reignofnether.faction.Factions.VILLAGERS);
        place(builder, owner, cx - 0.5, y + 1, cz + 0.5);        // at its site's edge, 7 from the wreck
        place(walker, owner, cx + 6.5, y + 1, cz - 3.5);
        place(commander, owner, cx + 6.5, y + 1, cz + 5.5);
        place(idleA, owner, cx + 4.5, y + 1, cz + 5.5);
        place(idleB, owner, cx + 4.5, y + 1, cz - 5.5);
        for (var w : List.of(builder, walker, commander, idleA, idleB))
            level.addFreshEntity(w);
        CommanderServerEvents.makeCommander(commander);
        builder.getBuildRepairGoal().setBuildingTarget(site);
        ((Unit) walker).setMoveTarget(new BlockPos(cx + 6, y + 1, cz - 8));
        // The rule-off half is judged now, in the tick the workers arrive: WreckServerEvents' own once-a-second pass
        // (gameTime % 20 == 0, rule on) may run during the 3-tick wait below and send the idle pair on its own, which
        // is right with the rule on but read as "sent with the rule off" when this check came after the wait (the
        // shard 8 flake, main runs 40566241275 / 40566701183). Nothing else can run between these lines.
        var rule = level.getGameRules().getRule(GameRuleRegistrar.IDLE_RECLAIM);
        String sentWhileOff = null;
        try {
            rule.set(false, level.getServer());
            WreckServerEvents.autoReclaimIdle(level);
            if (WreckServerEvents.isReclaiming(idleA) || WreckServerEvents.isReclaiming(idleB))
                sentWhileOff = "with the idleReclaim gamerule off an idle worker was sent to the wreck";
        } finally {
            rule.set(true, level.getServer());
        }
        final String offProblem = sentWhileOff;
        helper.runAfterDelay(3, () -> {
            String problem = offProblem;
            try {
                WreckServerEvents.autoReclaimIdle(level);
                if (problem == null && (WreckServerEvents.getReclaimTarget(idleA) != wreck
                        || WreckServerEvents.getReclaimTarget(idleB) != wreck))
                    problem = "an idle worker 5-6 blocks from a wreck was not sent to it (A " + WreckServerEvents.getReclaimTarget(idleA)
                        + ", B " + WreckServerEvents.getReclaimTarget(idleB) + ")";
                else if (problem == null && WreckServerEvents.isReclaiming(builder))
                    problem = "a worker with a build order was pulled onto the wreck";
                else if (problem == null && builder.getBuildRepairGoal().getBuildingTarget() != site)
                    problem = "the builder lost its building target";
                else if (problem == null && WreckServerEvents.isReclaiming(walker))
                    problem = "a worker walking to a move order was pulled onto the wreck";
                else if (problem == null && ((Unit) walker).getMoveGoal().getMoveTarget() == null)
                    problem = "the walker's move order was cancelled";
                else if (problem == null && WreckServerEvents.isReclaiming(commander))
                    problem = "the idle commander was sent off to a wreck";
            } finally {
                rule.set(true, level.getServer());
                for (var w : List.of(builder, walker, commander, idleA, idleB)) {
                    WreckServerEvents.clearReclaimTarget(w);
                    w.discard();
                }
                wreck.discard();
                BuildingServerEvents.getBuildings().remove(site);
            }
            if (problem != null)
                helper.fail(problem);
            else
                helper.succeed();
        });
    }

    private static void place(Mob m, String owner, double x, double y, double z) {
        m.moveTo(x, y, z, 0, 0);
        ((Unit) m).setOwnerName(owner);
    }
}
