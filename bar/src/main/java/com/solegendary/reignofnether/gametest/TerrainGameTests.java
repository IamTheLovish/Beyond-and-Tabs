package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.ability.AutocastState;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.unit.Terrain;
import com.solegendary.reignofnether.unit.interfaces.AttackerUnit;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Headless tests of terrain intelligence (design/terrain_intelligence.md): wading slows and weakens a unit; a chase
 * never follows a target into water; melee attackers ring their target; shooters do not stack; a move formation is a
 * line facing the move; a chaser that makes no progress is nudged and then gives up. Each test runs on open ground
 * in its own arena column (SkirmishGameTests.openGround) with unique owners; the formation test, which needs 24 blocks
 * of run, lays its own glass pad at a height no other test uses. Every unit is discarded in a finally.
 * Diagnostics go out as "[Melee]" lines, which CI folds into one readable notice.
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class TerrainGameTests {

    static final String ARENA = SkirmishGameTests.ARENA;

    static String owner(GameTestHelper helper, String tag) {
        return "gametest_terrain_" + tag + "_" + helper.absolutePos(BlockPos.ZERO).asLong();
    }

    static Mob spawn(ServerLevel level, EntityType<? extends Mob> type, String owner, double x, double y, double z,
                     List<Entity> made) {
        Mob m = type.create(level);
        if (m == null)
            return null;
        m.moveTo(x, y, z, 0, 0);
        if (m instanceof Unit u)
            u.setOwnerName(owner);
        level.addFreshEntity(m);
        if (m instanceof Unit u)
            AutocastState.setAll(u, false);
        made.add(m);
        return m;
    }

    /** A target that stands still and cannot die: no AI, invulnerable. */
    static Mob dummy(ServerLevel level, EntityType<? extends Mob> type, String owner, double x, double y, double z,
                     List<Entity> made) {
        Mob m = spawn(level, type, owner, x, y, z, made);
        if (m != null) {
            m.setNoAi(true);
            m.setInvulnerable(true);
        }
        return m;
    }

    static void cleanUp(List<Entity> made) {
        for (Entity e : made)
            if (!e.isRemoved())
                e.discard();
    }

    static void finish(GameTestHelper helper, List<String> problems) {
        if (problems.isEmpty())
            helper.succeed();
        else
            helper.fail(String.join("; ", problems));
    }

    static String f(double d) {
        return String.format(Locale.ROOT, "%.2f", d);
    }

    static double horizontal(Entity a, Entity b) {
        double dx = a.getX() - b.getX(), dz = a.getZ() - b.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** A 1-deep pool: stone at template y=0, water at y=1, air above (the arena's standing height is y=2). */
    static void pool(GameTestHelper helper, int x0, int z0, int x1, int z1) {
        ServerLevel level = helper.getLevel();
        for (int x = x0; x <= x1; x++)
            for (int z = z0; z <= z1; z++) {
                level.setBlock(helper.absolutePos(new BlockPos(x, 0, z)), Blocks.STONE.defaultBlockState(), 3);
                level.setBlock(helper.absolutePos(new BlockPos(x, 1, z)), Blocks.WATER.defaultBlockState(), 3);
                for (int y = 2; y <= 4; y++)
                    level.setBlock(helper.absolutePos(new BlockPos(x, y, z)), Blocks.AIR.defaultBlockState(), 3);
            }
    }

    static double x(GameTestHelper helper, int tx) { return helper.absolutePos(new BlockPos(tx, 0, 0)).getX() + 0.5; }
    static double z(GameTestHelper helper, int tz) { return helper.absolutePos(new BlockPos(0, 0, tz)).getZ() + 0.5; }
    static int y(GameTestHelper helper, int ty) { return helper.absolutePos(new BlockPos(0, ty, 0)).getY(); }

    // ------------------------------------------------------------------------------------------- 1. water is a decision

    /**
     * A Pillager wading a 1-deep pool covers between a quarter and three quarters of what one on land covers in 60
     * ticks (half its land speed once up to speed; vanilla's own water crawl would be about a fifth), and its hit
     * lands for 75% of the land Pillager's on a plain zombie.
     */
    @GameTest(template = ARENA, timeoutTicks = 120)
    public static void wading_unit_moves_at_half_speed_and_hits_for_three_quarters(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        SkirmishGameTests.openGround(helper, 0, 0, 15, 15);
        pool(helper, 1, 2, 14, 4);
        List<Entity> made = new ArrayList<>();
        List<String> problems = new ArrayList<>();
        String owner = owner(helper, "wade");
        Mob wader = spawn(level, EntityRegistrar.PILLAGER_UNIT.get(), owner, x(helper, 2), y(helper, 1), z(helper, 3), made);
        Mob walker = spawn(level, EntityRegistrar.PILLAGER_UNIT.get(), owner, x(helper, 2), y(helper, 2), z(helper, 9), made);
        Mob zombieA = spawn(level, EntityType.ZOMBIE, owner, x(helper, 14), y(helper, 2), z(helper, 13), made);
        Mob zombieB = spawn(level, EntityType.ZOMBIE, owner, x(helper, 11), y(helper, 2), z(helper, 13), made);
        if (wader == null || walker == null || zombieA == null || zombieB == null) {
            cleanUp(made);
            helper.fail("could not create the pillagers and zombies");
            return;
        }
        for (Mob z : List.of(zombieA, zombieB)) {
            z.setNoAi(true);
            z.setHealth(z.getMaxHealth());
        }
        double startWader = wader.getX(), startWalker = walker.getX();
        helper.runAfterDelay(2, () -> {
            if (!Terrain.isWading(wader))
                problems.add("the pillager in the pool is not wading (inWater " + wader.isInWater() + ")");
            if (Terrain.isWading(walker))
                problems.add("the pillager on land is wading");
            ((Unit) wader).getMoveGoal().setMoveTarget(helper.absolutePos(new BlockPos(13, 1, 3)));
            ((Unit) walker).getMoveGoal().setMoveTarget(helper.absolutePos(new BlockPos(13, 2, 9)));
        });
        helper.runAfterDelay(62, () -> {
            try {
                double wade = wader.getX() - startWader, walk = walker.getX() - startWalker;
                double ratio = walk > 0.01 ? wade / walk : -1;
                ReignOfNether.LOGGER.info("[Melee] terrain: 60 ticks on land {} blocks, wading {} blocks (ratio {}), friction {}",
                    f(walk), f(wade), f(ratio), f(Terrain.waterFriction(wader, 0.8f)));
                if (walk < 2.0)
                    problems.add("the land pillager only moved " + f(walk) + " blocks in 60 ticks");
                else if (ratio < 0.25 || ratio > 0.75)
                    problems.add("the wading pillager moved " + f(wade) + " blocks to the land one's " + f(walk) + " (ratio "
                        + f(ratio) + ", expected 0.25-0.75)");
                // damage: one hit each on a plain zombie (no armour, not a unit, not an animal)
                float hpA = zombieA.getHealth(), hpB = zombieB.getHealth();
                zombieA.hurt(level.damageSources().mobAttack(walker), 1f);
                zombieB.hurt(level.damageSources().mobAttack(wader), 1f);
                float land = hpA - zombieA.getHealth(), water = hpB - zombieB.getHealth();
                if (land <= 0)
                    problems.add("the land pillager's hit did not land");
                else if (Math.abs(water / land - Terrain.WADING_DAMAGE) > 0.03)
                    problems.add("the wading pillager hit for " + f(water) + " to the land one's " + f(land) + " (expected "
                        + Terrain.WADING_DAMAGE + "x)");
                finish(helper, problems);
            } finally {
                cleanUp(made);
            }
        });
    }

}
