package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.building.Building;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.BuildingServerEvents;
import com.solegendary.reignofnether.building.buildings.defence.Defences;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.unit.interfaces.AttackerUnit;
import com.solegendary.reignofnether.unit.units.villagers.MilitiaUnit;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.Locale;
import java.util.function.Consumer;

/**
 * Archers damage defences (lovish, Nov 5: "I want archers to be able to damage defenses I dont think they can atm").
 * Every ranged unit that shoots arrows or bolts chips an enemy building it is ordered onto, through one shared rule
 * (RangedBuildingHit), at the archer rate the Skeleton and Thornbow already had: the unit's damage x the building's
 * melee multiplier x its building-damage multiplier x 0.5 (arrows are weak against stone), 2 health a point.
 * <p>
 * Each test orders one kind onto an enemy raid tower through the real order path ({@code setAttackBuildingTarget}, as a
 * right-click on a building does) and reads the tower's health. The shooter's health is raised so the tower's return
 * fire cannot end the test before it shoots (a test-layer setup; the tower and the shot are the game's own). The
 * Skeleton is the control: it chipped buildings before this change, so it shows the harness can pass on old code.
 * The upper bound says "chip, not siege": over a 10 s window the tower loses at most what one shot per cooldown at the
 * archer rate takes, plus slack for the building's whole-block rounding (a siege lobber takes 54 a shell).
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class ArcherBuildingGameTests {

    static final String ARENA = SkirmishGameTests.ARENA;
    /** The archer rate on stone (AbstractArrowMixin's 0.5 before this change) and the health a point of building damage costs. */
    static final float ARCHER_MULT = 0.5f, HEALTH_PER_DAMAGE = 2f;
    /** Ticks for the shooter to walk into range and land its first hit; then the window the rate is read over. */
    static final int FIRST_HIT_TICKS = 700, WINDOW_TICKS = 200;

    @GameTest(template = ARENA, timeoutTicks = 2400)
    public static void a_skeleton_chips_an_enemy_tower(GameTestHelper helper) {
        chips(helper, 14, EntityRegistrar.SKELETON_UNIT.get(), "Skeleton", m -> { });
    }

    @GameTest(template = ARENA, timeoutTicks = 2400)
    public static void a_rifle_squad_chips_an_enemy_tower(GameTestHelper helper) {
        chips(helper, 15, EntityRegistrar.RIFLE_SQUAD_UNIT.get(), "Rifle Squad", m -> { });
    }

    @GameTest(template = ARENA, timeoutTicks = 2400)
    public static void a_harpooner_chips_an_enemy_tower(GameTestHelper helper) {
        chips(helper, 16, EntityRegistrar.HARPOONER_UNIT.get(), "Harpooner", m -> { });
    }

    @GameTest(template = ARENA, timeoutTicks = 2400)
    public static void a_pillager_on_foot_chips_an_enemy_tower(GameTestHelper helper) {
        chips(helper, 17, EntityRegistrar.PILLAGER_UNIT.get(), "Pillager", m -> { });
    }

    @GameTest(template = ARENA, timeoutTicks = 2400)
    public static void a_militia_with_its_bow_chips_an_enemy_tower(GameTestHelper helper) {
        chips(helper, 18, EntityRegistrar.MILITIA_UNIT.get(), "Militia (bow)", m -> ((MilitiaUnit) m).swapWeapons(true));
    }

    @GameTest(template = ARENA, timeoutTicks = 2400)
    public static void a_seraph_archer_chips_an_enemy_tower(GameTestHelper helper) {
        chips(helper, 19, EntityRegistrar.SERAPH_ARCHER_UNIT.get(), "Seraph Archer", m -> { });
    }

    /**
     * A high flyer: the Sky Corsair (range 13 in 3D, cruising 10 blocks up) ordered onto the raid tower must hurt it.
     * Its bombs are not an archer's chip (they splash at their own building rate), so only the hit is checked, not the
     * archer cap. Review r1 of the flyer stand-off (#538): a stand-off measured flat, ignoring the flight height, parks it
     * where the tower's lowest blocks are out of its 3D reach.
     */
    @GameTest(template = ARENA, timeoutTicks = 2400)
    public static void a_sky_corsair_bombs_an_enemy_tower(GameTestHelper helper) {
        hurts(helper, 22, EntityRegistrar.SKY_CORSAIR_UNIT.get(), "Sky Corsair", m -> { }, false);
    }

    /**
     * One shooter of {@code type} 15 blocks from an enemy raid tower on its own far pad, ordered to attack it: the tower
     * must lose health within {@link #FIRST_HIT_TICKS}, and over the next {@link #WINDOW_TICKS} no more than the archer
     * rate allows.
     */
    static void chips(GameTestHelper helper, int slot, EntityType<? extends Mob> type, String name, Consumer<Mob> arm) {
        hurts(helper, slot, type, name, arm, true);
    }

    /** {@link #chips}, with the archer-rate cap only when {@code archerRate} (a bomber's splash has its own rate). */
    static void hurts(GameTestHelper helper, int slot, EntityType<? extends Mob> type, String name, Consumer<Mob> arm, boolean archerRate) {
        ServerLevel level = helper.getLevel();
        RaidGameTests.Pads pads = new RaidGameTests.Pads(level, slot, 1);
        // registered here, once, idling until the pad is ready: registering from inside whenEntitiesReady's callback
        // corrupts vanilla's tick-time map (BuildingDamageGameTests)
        Runnable[] watch = { null };
        helper.onEachTick(() -> {
            if (watch[0] != null)
                watch[0].run();
        });
        GameTestChunks.whenEntitiesReady(helper, level, pads.chunks, 5, 400, 1300, "archer building pad", () -> {
            RaidGameTests.Cell c = pads.cells.get(0);
            Building tower = Defences.sets().get(0).raidTower();
            BlockPos origin = new BlockPos(c.x0() + 19, RaidGameTests.PAD_Y + 1, c.z0() + 5);
            BuildingPlacement bp = RaidGameTests.build(level, tower, origin, "gametest_ab_foe", false);
            Mob shooter = RaidGameTests.spawn(level, type, "gametest_ab_archer", c.x0() + 4, c.z0() + 6);
            if (bp == null || shooter == null) {
                if (shooter != null)
                    shooter.discard();
                if (bp != null)
                    BuildingServerEvents.getBuildings().remove(bp);
                pads.release();
                helper.fail("setup: could not make the tower (" + bp + ") or the " + name + " (" + shooter + ")");
                return;
            }
            arm.accept(shooter);
            shooter.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);
            shooter.setHealth(1000);
            AttackerUnit au = (AttackerUnit) shooter;
            int start = (int) helper.getTick();
            int[] firstHit = { -1, 0 };   // the tick of the first lost health, the health then
            int before = bp.getHealth();
            boolean[] ordered = { false }, over = { false };
            // the state trail every verdict prints: ticks with a building target, times it was let go, the lowest and
            // highest the shooter flew above the pad, its nearest and furthest from the tower's centre, ranged hits it
            // landed on buildings (RangedBuildingHit), and every effect it carried
            int[] trail = { 0, 0, Integer.MAX_VALUE, Integer.MIN_VALUE };
            double[] reach = { Double.MAX_VALUE, 0 };
            boolean[] hadTarget = { false };
            int hitsBefore = com.solegendary.reignofnether.building.RangedBuildingHit.hits(type);
            java.util.Set<String> effects = new java.util.TreeSet<>();
            watch[0] = () -> {
                if (over[0])
                    return;
                int now = (int) helper.getTick();
                if (!ordered[0]) {
                    if (now < start + 2)   // let it tick once with its weapon in hand before the order
                        return;
                    au.setAttackBuildingTarget(bp.originPos);
                    ordered[0] = true;
                    return;
                }
                boolean targeted = au.getAttackBuildingGoal() instanceof com.solegendary.reignofnether.unit.goals.RangedAttackBuildingGoal<?> g
                    ? g.getBuildingTarget() != null
                    : au.getAttackBuildingGoal() instanceof com.solegendary.reignofnether.unit.goals.MeleeAttackBuildingGoal m && m.getBuildingTarget() != null;
                if (targeted)
                    trail[0]++;
                else if (hadTarget[0])
                    trail[1]++;
                hadTarget[0] = targeted;
                int up = (int) Math.floor(shooter.getY() - (RaidGameTests.PAD_Y + 1));
                trail[2] = Math.min(trail[2], up);
                trail[3] = Math.max(trail[3], up);
                double flat = Math.sqrt(shooter.distanceToSqr(bp.centrePos.getX() + 0.5, shooter.getY(), bp.centrePos.getZ() + 0.5));
                reach[0] = Math.min(reach[0], flat);
                reach[1] = Math.max(reach[1], flat);
                for (var e : shooter.getActiveEffects())
                    effects.add(e.getEffect().getDescriptionId().replace("effect.", ""));
                String state = String.format(Locale.ROOT, "trail: building target %d ticks, let go %d times, %d to %d above the pad, %.1f to %.1f"
                    + " from the tower's centre, %d ranged hits landed, effects %s", trail[0], trail[1], trail[2], trail[3], reach[0], reach[1],
                    com.solegendary.reignofnether.building.RangedBuildingHit.hits(type) - hitsBefore, effects.isEmpty() ? "none" : String.join(" ", effects));
                int health = bp.getHealth();
                String verdict = null;
                if (firstHit[0] < 0) {
                    if (health < before) {
                        firstHit[0] = now;
                        firstHit[1] = health;
                    } else if (now - start > FIRST_HIT_TICKS) {
                        verdict = "the " + name + " never hurt the enemy tower in " + FIRST_HIT_TICKS / 20 + " s (health " + health + " of "
                            + before + "; canAttackBuildings " + au.canAttackBuildings() + ", building goal "
                            + (au.getAttackBuildingGoal() == null ? "none" : au.getAttackBuildingGoal().getClass().getSimpleName())
                            + ", " + String.format(Locale.ROOT, "%.1f", Math.sqrt(shooter.distanceToSqr(bp.centrePos.getX() + 0.5,
                                shooter.getY(), bp.centrePos.getZ() + 0.5))) + " blocks from its centre, alive " + shooter.isAlive() + "); " + state;
                    }
                    if (verdict == null)
                        return;
                } else if (now < firstHit[0] + WINDOW_TICKS) {
                    return;
                }
                over[0] = true;
                float perHit = au.getUnitAttackDamage() * bp.getMeleeDamageMult() * au.getBuildingDamageMultiplier() * ARCHER_MULT * HEALTH_PER_DAMAGE;
                if (verdict == null && !archerRate)
                    ReignOfNether.LOGGER.info("[ArcherBuilding] GameTest {}: first hit after {} ticks, {} health in the next {} s (no archer cap); {}",
                        name, firstHit[0] - start, firstHit[1] - health, WINDOW_TICKS / 20, state);
                if (verdict == null && archerRate) {
                    int lost = firstHit[1] - health;
                    int shots = WINDOW_TICKS / Math.max(1, (int) au.getAttackCooldown()) + 1;
                    float cap = shots * perHit * 1.5f + (float) (2 * bp.getHealthPerBlock());
                    ReignOfNether.LOGGER.info("[ArcherBuilding] GameTest {}: first hit after {} ticks, {} health in the next {} s ({} per hit, at most {} shots, cap {}); {}",
                        name, firstHit[0] - start, lost, WINDOW_TICKS / 20, String.format(Locale.ROOT, "%.1f", perHit), shots,
                        String.format(Locale.ROOT, "%.1f", cap), state);
                    if (lost > cap)
                        verdict = "the " + name + " took " + lost + " health off the tower in " + WINDOW_TICKS / 20 + " s: an archer chips ("
                            + String.format(Locale.ROOT, "%.1f", perHit) + " a hit, at most " + shots + " shots, cap "
                            + String.format(Locale.ROOT, "%.1f", cap) + "), it does not siege; " + state;
                }
                shooter.discard();
                BuildingServerEvents.getBuildings().remove(bp);
                pads.release();
                if (verdict != null)
                    helper.fail(verdict);
                else
                    helper.succeed();
            };
        });
    }
}
