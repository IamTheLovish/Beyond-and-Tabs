package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.ability.abilities.RaiseTidepool;
import com.solegendary.reignofnether.registrars.BlockRegistrar;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.resources.Resources;
import com.solegendary.reignofnether.resources.ResourcesServerEvents;
import com.solegendary.reignofnether.tide.TidepoolServerEvents;
import com.solegendary.reignofnether.unit.EngagementStance;
import com.solegendary.reignofnether.unit.UnitAction;
import com.solegendary.reignofnether.unit.command.CommandQueues;
import com.solegendary.reignofnether.unit.command.QueueMode;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.interfaces.WorkerUnit;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * The Light Grid's worker, the <b>Builder Drone</b> (design/ai_faction.md section 2, PR 379: "hover constructor; draws
 * grid patches"). The Tidewrought Shipwright turns into it in place (lovish, Oct 29: "convert in place"), so the entity
 * is still {@code shipwright_unit}: these tests hold what the drone must do on top of the shared worker plumbing.
 * <ul>
 *   <li>It draws the grid: Draw Grid (the Tide Priest's RaiseTidepool, the Light Grid's core mechanic) lays a patch the
 *   drone's owner owns, pays its energy and cools down; a worker on main has no ability at all, so this is the red.</li>
 *   <li>It is a T1 worker at the T1 worker's price ("same roles and prices as the other factions at each tier", the
 *   design's rules table): the Sunforged worker's health, armour, speed and cost, and it builds, repairs and reclaims.
 *   This one is a pin (main already agrees): the body change must not move a number lovish has not asked to move.</li>
 *   <li>Its welding zap lands on an enemy it is ordered to attack and on nobody else (also a pin: the drone's attack
 *   is drawn, not new).</li>
 * </ul>
 * Each test builds its own units in its own arena and removes them, its owners' pools and patches at the end.
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class BuilderDroneGameTests {

    static final String ARENA = SkirmishGameTests.ARENA;

    static String owner(GameTestHelper helper, String tag) {
        return "gametest_drone_" + tag + "_" + helper.absolutePos(BlockPos.ZERO).asLong();
    }

    static RaiseTidepool drawGrid(Unit drone) {
        for (var a : drone.getAbilities().get())
            if (a instanceof RaiseTidepool r)
                return r;
        return null;
    }

    /**
     * Draw Grid on the Builder Drone: aimed 6 blocks off, it lays a patch of at least 9 cells owned by the drone's
     * owner round the aimed ground, takes {@link RaiseTidepool#ENERGY_COST} energy and goes on cooldown. The drone is
     * never added to the level (the ability needs only its owner and position).
     */
    @GameTest(template = ARENA, timeoutTicks = 60)
    public static void builder_drone_draws_a_grid_patch(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = owner(helper, "grid");
        SkirmishGameTests.openGround(helper, 1, 1, 14, 14);
        var pool = new Resources(owner, 0, 0, 0);
        pool.addEnergy(RaiseTidepool.ENERGY_COST + 10);
        ResourcesServerEvents.resourcesList.add(pool);
        var drone = EntityRegistrar.SHIPWRIGHT_UNIT.get().create(level);
        try {
            if (drone == null) {
                helper.fail("could not create a Builder Drone");
                return;
            }
            drone.setOwnerName(owner);
            BlockPos at = helper.absolutePos(new BlockPos(3, 2, 7));
            drone.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
            RaiseTidepool draw = drawGrid(drone);
            if (draw == null) {
                helper.fail("the Builder Drone has no Draw Grid (abilities: " + drone.getAbilities().get().size() + ")");
                return;
            }
            if (!draw.isOffCooldown(drone)) {
                helper.fail("a new Builder Drone's Draw Grid starts on cooldown");
                return;
            }
            BlockPos aim = helper.absolutePos(new BlockPos(9, 1, 7));   // the ground block, 6 blocks off
            draw.use(level, drone, aim);
            var patches = TidepoolServerEvents.poolsOf(owner);
            if (patches.isEmpty()) {
                helper.fail("Draw Grid drew nothing: " + TidepoolServerEvents.lastRefusal());
                return;
            }
            var p = patches.get(0);
            if (p.cellCount() < 9 || p.centre.distSqr(aim.above()) > 4)
                helper.fail("the patch is too small or in the wrong place: " + p.cellCount() + " cells at " + p.centre);
            if (!level.getBlockState(aim.above()).is(BlockRegistrar.TIDEPOOL.get()))
                helper.fail("no grid cell on the aimed ground");
            if (pool.getEnergy() > 10.01)
                helper.fail("Draw Grid did not charge its energy: " + pool.getEnergy() + " left of "
                    + (RaiseTidepool.ENERGY_COST + 10));
            if (draw.isOffCooldown(drone))
                helper.fail("Draw Grid did not go on cooldown");
            helper.succeed();
        } finally {
            TidepoolServerEvents.clearOwner(owner);
            ResourcesServerEvents.resourcesList.remove(pool);
            if (drone != null)
                drone.discard();
        }
    }

    /**
     * Same role, same price: the Builder Drone carries the Sunforged worker's health, armour, speed and cost (metal,
     * energy and build time), and the worker plumbing - it builds, repairs and gathers. A hover body never drowns
     * (Tidewrought pathing still sends it over water).
     */
    @GameTest(template = ARENA, timeoutTicks = 40)
    public static void builder_drone_is_a_t1_worker_at_the_t1_worker_price(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var drone = EntityRegistrar.SHIPWRIGHT_UNIT.get().create(level);
        var peer = EntityRegistrar.VILLAGER_UNIT.get().create(level);
        try {
            if (drone == null || peer == null) {
                helper.fail("could not create the Builder Drone and its Sunforged peer");
                return;
            }
            List<String> problems = new ArrayList<>();
            if (!(drone instanceof WorkerUnit w) || w.getBuildRepairGoal() == null || w.getGatherResourceGoal() == null)
                problems.add("the Builder Drone is not a worker that builds and gathers");
            compare(problems, "health", drone.getMaxHealth(), peer.getMaxHealth());
            compare(problems, "armour", drone.getAttributeValue(Attributes.ARMOR), peer.getAttributeValue(Attributes.ARMOR));
            compare(problems, "speed", drone.getAttributeValue(Attributes.MOVEMENT_SPEED),
                peer.getAttributeValue(Attributes.MOVEMENT_SPEED));
            var cost = drone.getCost();
            var peerCost = peer.getCost();
            if (cost == null || peerCost == null)
                problems.add("a worker has no cost");
            else {
                compare(problems, "metal", cost.metal(), peerCost.metal());
                compare(problems, "energy", cost.energy(), peerCost.energy());
                compare(problems, "build ticks", cost.ticks, peerCost.ticks);
            }
            if (!drone.canBreatheUnderwater())
                problems.add("the hover body drowns");
            if (!problems.isEmpty())
                helper.fail(String.join("; ", problems));
            else
                helper.succeed();
        } finally {
            if (drone != null)
                drone.discard();
            if (peer != null)
                peer.discard();
        }
    }

    static void compare(List<String> problems, String what, double drone, double peer) {
        if (Math.abs(drone - peer) > 1e-6)
            problems.add(what + " " + drone + " is not the T1 worker's " + peer);
    }

    /**
     * The welding zap: ordered onto an enemy a block and a half away, the Builder Drone hurts it within 8 s; the
     * owner's own unit standing just as close is never hurt. The enemy has no AI (it neither walks off nor answers),
     * the friend holds fire.
     */
    @GameTest(template = ARENA, timeoutTicks = 220)
    public static void builder_drone_zaps_an_enemy_it_is_ordered_to_attack(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = owner(helper, "zap"), foeOwner = owner + "_foe";
        SkirmishGameTests.openGround(helper, 1, 1, 14, 14);
        List<Mob> made = new ArrayList<>();
        Mob drone = spawn(level, EntityRegistrar.SHIPWRIGHT_UNIT.get(), owner,
            helper.absolutePos(new BlockPos(6, 2, 7)), made);
        Mob foe = spawn(level, EntityRegistrar.VILLAGER_UNIT.get(), foeOwner,
            helper.absolutePos(new BlockPos(8, 2, 7)), made);
        Mob friend = spawn(level, EntityRegistrar.VILLAGER_UNIT.get(), owner,
            helper.absolutePos(new BlockPos(6, 2, 9)), made);
        if (drone == null || foe == null || friend == null) {
            for (Mob m : made)
                m.discard();
            helper.fail("could not create the zap test's units");
            return;
        }
        foe.setNoAi(true);
        EngagementStance.set(friend, EngagementStance.HOLD_FIRE);
        float foeFull = foe.getHealth(), friendFull = friend.getHealth();
        int[] waited = {0};
        helper.onEachTick(() -> {
            if (waited[0] < 0)
                return;
            // the order goes out once the units have ticked (a unit spawned this tick can be missing from the
            // level's id lookup for a tick or two)
            if (waited[0] == 0 && helper.getTick() >= 5 && drone.tickCount > 0)
                CommandQueues.issue(level, owner, UnitAction.ATTACK, foe.getId(), new int[] { drone.getId() },
                    BlockPos.ZERO, BlockPos.ZERO, QueueMode.REPLACE);
            else if (waited[0] == 0)
                return;
            waited[0]++;
            if (friend.getHealth() < friendFull) {
                waited[0] = -1;
                cleanUp(made);
                helper.fail("the Builder Drone hurt its owner's own unit");
            } else if (foe.getHealth() < foeFull) {
                waited[0] = -1;
                cleanUp(made);
                helper.succeed();
            } else if (waited[0] > 160) {
                waited[0] = -1;
                String where = String.format("drone at %.1f %.1f, foe %.1f blocks off", drone.getX(), drone.getZ(),
                    drone.distanceTo(foe));
                cleanUp(made);
                helper.fail("the Builder Drone never landed its zap in 8 s (" + where + ")");
            }
        });
    }

    /** A unit of {@code type} owned by {@code owner}, feet at the cell {@code at}, added to the level. */
    static Mob spawn(ServerLevel level, net.minecraft.world.entity.EntityType<? extends Mob> type, String owner,
                     BlockPos at, List<Mob> made) {
        Mob m = type.create(level);
        if (m == null)
            return null;
        m.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
        if (m instanceof Unit u)
            u.setOwnerName(owner);
        level.addFreshEntity(m);
        made.add(m);
        return m;
    }

    static void cleanUp(List<Mob> made) {
        for (LivingEntity m : made)
            if (!m.isRemoved())
                m.discard();
    }
}
