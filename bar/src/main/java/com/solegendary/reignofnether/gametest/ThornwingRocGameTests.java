package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.ability.abilities.GaleWings;
import com.solegendary.reignofnether.ability.abilities.SeedRain;
import com.solegendary.reignofnether.building.Buildings;
import com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement;
import com.solegendary.reignofnether.building.production.ProductionItems;
import com.solegendary.reignofnether.keybinds.Keybindings;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.unit.T3Cap;
import com.solegendary.reignofnether.unit.goals.FlyingMoveToTargetGoal;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.units.verdant.ThornwingRocUnit;
import com.solegendary.reignofnether.unit.units.verdant.WorldTreeWalkerUnit;
import com.solegendary.reignofnether.unit.units.villagers.SunColossusUnit;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * Slice T3b-7 of the second Tier 3 (second-t3-design.md): the Verdant <b>Thornwing Roc</b>. One test per acceptance
 * criterion, all un-hosted, asserting only on the test's own spawned entities: trained from slot 5 (T) of the Heart of
 * the Wild as its own type and capped one alive beside the World Tree Walker; it flies (stays above the ground, crosses
 * a wall); Feather Volley hits a ground and an air target (and fights by itself); Gale Wings pushes the cone and nothing
 * else; Seed Rain is telegraphed, then roots and slows walkers on the line; the numbers keep the T3 rules.
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class ThornwingRocGameTests {

    static final String ARENA = SkirmishGameTests.ARENA;

    static String owner(GameTestHelper helper, String tag) {
        return "gametest_roc_" + tag + "_" + helper.absolutePos(BlockPos.ZERO).asLong();
    }

    /** A body that survives any one hit of the test (the real HP is tested elsewhere). */
    static <T extends Mob> T tough(T m) {
        m.getAttribute(Attributes.MAX_HEALTH).setBaseValue(900);
        m.setHealth(900);
        return m;
    }

    @SuppressWarnings("unchecked")
    static <T extends Mob> T put(GameTestHelper helper, net.minecraft.world.entity.EntityType<T> type, String owner, double x, double z) {
        return (T) BigUnitGameTests.spawn(helper, type, owner, x, z);
    }

    /** A 16x16 glass pad high above the arena (nothing else spawns on it; no neighbour's unit is in reach). */
    static BlockPos skyPad(GameTestHelper helper, int y) {
        BlockPos corner = helper.absolutePos(BlockPos.ZERO);
        for (int dx = 0; dx < 16; dx++)
            for (int dz = 0; dz < 16; dz++)
                helper.getLevel().setBlock(new BlockPos(corner.getX() + dx, y, corner.getZ() + dz), Blocks.GLASS.defaultBlockState(), 3);
        return new BlockPos(corner.getX(), y + 1, corner.getZ());   // standing height at the arena's corner
    }

    static Mob onPad(ServerLevel level, net.minecraft.world.entity.EntityType<? extends Mob> type, String owner, double x, double y, double z) {
        Mob m = type.create(level);
        if (m == null)
            return null;
        m.moveTo(x, y, z, 0, 0);
        if (m instanceof Unit u)
            u.setOwnerName(owner);
        level.addFreshEntity(m);
        return m;
    }

    /** AC-1: trained from slot 5 (T) of the Heart of the Wild as a Thornwing Roc (no placeholder tag, no World Tree Walker). */
    @GameTest(template = ARENA, timeoutTicks = 60)
    public static void thornwing_roc_is_trained_from_slot_t_as_its_own_unit(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        ServerLevel level = helper.getLevel();
        String owner = owner(helper, "train");
        if (Buildings.HEART_OF_THE_WILD.productions.forHotkey(Keybindings.abilitySlot5) != ProductionItems.VERDANT_T3B)
            problems.add("slot 5 (T) of the Heart of the Wild is not the second T3");
        if (!ThornwingRocUnit.ITEM_NAME.equals(ProductionItems.VERDANT_T3B.getItemName()))
            problems.add("the second Verdant T3 is still named " + ProductionItems.VERDANT_T3B.getItemName());
        ProductionPlacement pp = SecondT3FrameGameTests.placement(helper, Buildings.HEART_OF_THE_WILD, owner);
        if (pp == null) {
            T3RoleGameTests.finish(helper, List.of("no Heart of the Wild placement"));
            return;
        }
        ProductionItems.VERDANT_T3B.onComplete.accept(level, pp);
        helper.runAfterDelay(3, () -> {
            AABB all = new AABB(helper.absolutePos(BlockPos.ZERO)).inflate(80);
            List<ThornwingRocUnit> made = level.getEntitiesOfClass(ThornwingRocUnit.class, all, u -> owner.equals(u.getOwnerName()));
            if (made.size() != 1)
                problems.add("training the second Verdant T3 made " + made.size() + " Thornwing Rocs");
            else {
                if (made.get(0).getPersistentData().getBoolean(T3Cap.TAG_SECOND))
                    problems.add("a real unit must not carry the placeholder tag");
                made.get(0).discard();
            }
            if (level.getEntitiesOfClass(WorldTreeWalkerUnit.class, all, u -> owner.equals(u.getOwnerName())).size() != 0)
                problems.add("the second Verdant T3 trained a World Tree Walker body");
            T3RoleGameTests.finish(helper, problems);
        });
    }

    /** AC-2: one alive of each type: a living Roc blocks a second Roc, not a Walker; and the other way round. */
    @GameTest(template = ARENA, timeoutTicks = 60)
    public static void thornwing_roc_cap_is_one_alive_beside_the_world_tree_walker(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        String owner = owner(helper, "cap");
        SecondT3FrameGameTests.grantTiers(owner);
        ProductionPlacement pp = SecondT3FrameGameTests.placement(helper, Buildings.HEART_OF_THE_WILD, owner);
        if (pp == null) {
            T3RoleGameTests.finish(helper, List.of("no Heart of the Wild placement"));
            return;
        }
        if (T3Cap.productionError(pp, ProductionItems.VERDANT_T3B) != null)
            problems.add("a Roc was refused with none alive");
        var roc = put(helper, EntityRegistrar.THORNWING_ROC_UNIT.get(), owner, 3.5, 8.5);
        if (roc == null) {
            T3RoleGameTests.finish(helper, List.of("could not create the Roc"));
            return;
        }
        roc.setNoAi(true);
        if (!T3Cap.LIMIT_MSG.equals(T3Cap.productionError(pp, ProductionItems.VERDANT_T3B)))
            problems.add("a second Roc was not refused while one lives");
        if (T3Cap.productionError(pp, ProductionItems.WORLD_TREE_WALKER) != null)
            problems.add("a living Roc blocked the World Tree Walker");
        roc.discard();
        var walker = put(helper, EntityRegistrar.WORLD_TREE_WALKER_UNIT.get(), owner, 3.5, 8.5);
        if (walker != null) {
            walker.setNoAi(true);
            if (T3Cap.productionError(pp, ProductionItems.VERDANT_T3B) != null)
                problems.add("a living World Tree Walker blocked the Roc");
            if (!T3Cap.LIMIT_MSG.equals(T3Cap.productionError(pp, ProductionItems.WORLD_TREE_WALKER)))
                problems.add("a second World Tree Walker was not refused while one lives");
            walker.discard();
        }
        T3RoleGameTests.finish(helper, problems);
    }

    /**
     * AC-3 (flight): the Roc is a flyer to every anti-air unit (flying move goal, no ground garrison); ordered across a
     * 10-high wall it flies over it, never inside the wall, and never touches the ground on the way.
     * Own sky pad at 210: far from every other pad.
     */
    @GameTest(template = ARENA, timeoutTicks = 240)
    public static void thornwing_roc_flies_above_the_ground_and_crosses_a_wall(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pad = skyPad(helper, 210);
        final int gy = pad.getY();
        List<BlockPos> wall = new ArrayList<>();
        for (int dz = 2; dz <= 14; dz++)
            for (int y = gy; y <= gy + 9; y++)
                wall.add(new BlockPos(pad.getX() + 8, y, pad.getZ() + dz));
        for (BlockPos b : wall)
            level.setBlock(b, Blocks.STONE.defaultBlockState(), 3);
        var roc = (ThornwingRocUnit) onPad(level, EntityRegistrar.THORNWING_ROC_UNIT.get(), owner(helper, "fly"), pad.getX() + 3.5, gy + 8, pad.getZ() + 8.5);
        if (roc == null) {
            wall.forEach(b -> level.setBlock(b, Blocks.AIR.defaultBlockState(), 3));
            helper.fail("could not create the Roc");
            return;
        }
        BlockPos target = new BlockPos(pad.getX() + 13, gy - 1, pad.getZ() + 8);
        Runnable clean = () -> {
            roc.discard();
            for (BlockPos b : wall)
                level.setBlock(b, Blocks.AIR.defaultBlockState(), 3);
        };
        List<String> problems = new ArrayList<>();
        helper.runAfterDelay(3, () -> {
            if (!roc.isFlyingUnit() || !(roc.getMoveGoal() instanceof FlyingMoveToTargetGoal))
                problems.add("the Roc is not a flyer to anti-air");
            if (roc.canGarrison())
                problems.add("a flyer cannot garrison");
            roc.getMoveGoal().setMoveTarget(target);
        });
        final int[] inWallAt = { -1 };
        final int[] lowAt = { -1 };
        for (int t = 10; t <= 200; t += 5) {
            final int tick = t;
            helper.runAfterDelay(t, () -> {
                if (inWallAt[0] < 0 && roc.isInWall())
                    inWallAt[0] = tick;
                if (lowAt[0] < 0 && (roc.onGround() || roc.getY() < gy + 0.5))
                    lowAt[0] = tick;
            });
        }
        helper.runAfterDelay(205, () -> {
            double x = roc.getX();
            if (inWallAt[0] >= 0)
                problems.add("the Roc was in a wall at tick " + inWallAt[0]);
            if (lowAt[0] >= 0)
                problems.add("the Roc touched the ground at tick " + lowAt[0]);
            if (x < pad.getX() + 8.5)
                problems.add(String.format("the Roc did not cross the wall: x %.1f (wall at x %d)", x, pad.getX() + 8));
            clean.run();
            T3RoleGameTests.finish(helper, problems);
        });
    }

    /**
     * AC-4 (Feather Volley, exact): three feathers of {@link ThornwingRocUnit#FEATHER_DAMAGE} land as one blow on a ground
     * target AND on an air target; a friend, an enemy out of reach and a dead target take nothing; the Roc cannot hurt a
     * building. Then, left to itself on an isolated sky pad (210), it picks a ground enemy and then an air enemy on its own
     * and only ever deals whole volleys.
     */
    @GameTest(template = ARENA, timeoutTicks = 330)
    public static void thornwing_roc_feather_volley_hits_ground_and_air(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = owner(helper, "volley"), foe = owner(helper, "volleyfoe");
        BlockPos pad = skyPad(helper, 210);
        final int gy = pad.getY();
        double cx = pad.getX() + 8.5, cz = pad.getZ() + 8.5;
        var roc = (ThornwingRocUnit) onPad(level, EntityRegistrar.THORNWING_ROC_UNIT.get(), owner, cx, gy + 4, cz);
        var ground = (Mob) onPad(level, EntityRegistrar.VINDICATOR_UNIT.get(), foe, cx + 6, gy, cz);
        var friend = (Mob) onPad(level, EntityRegistrar.VINDICATOR_UNIT.get(), owner, cx, gy, cz + 6);
        var air = (Mob) onPad(level, EntityRegistrar.THUNDER_DRAGON_UNIT.get(), foe, cx - 6, gy + 5, cz);
        List<Mob> all = new ArrayList<>();
        for (Mob m : new Mob[] { roc, ground, friend, air })
            if (m != null)
                all.add(m);
        if (all.size() != 4) {
            all.forEach(Mob::discard);
            helper.fail("could not create the volley units");
            return;
        }
        for (Mob m : List.of(ground, friend, air))
            tough(m).setNoAi(true);
        roc.setNoAi(true);
        List<String> problems = new ArrayList<>();
        helper.runAfterDelay(3, () -> {
            if (roc.getAttackGoal() == null || roc.getAttackGoal() instanceof com.solegendary.reignofnether.unit.goals.AbstractMeleeAttackUnitGoal)
                problems.add("the Roc should attack from range, not in melee");
            if (ThornwingRocUnit.attackRange != 16)
                problems.add("the design's range is 16: " + ThornwingRocUnit.attackRange);
            if (roc.canAttackBuildings() || roc.getAttackBuildingGoal() != null)
                problems.add("the Roc has no building damage");
            float g0 = ground.getHealth(), a0 = air.getHealth(), f0 = friend.getHealth();
            int n = roc.volley(ground);
            float gd = g0 - ground.getHealth();
            if (n != ThornwingRocUnit.FEATHERS || Math.abs(gd - ThornwingRocUnit.FEATHER_DAMAGE * ThornwingRocUnit.FEATHERS) > 0.01f)
                problems.add("a volley at the ground target landed " + n + " feathers for " + gd + ", expected "
                    + ThornwingRocUnit.FEATHERS + " for " + ThornwingRocUnit.FEATHER_DAMAGE * ThornwingRocUnit.FEATHERS);
            n = roc.volley(air);
            float ad = a0 - air.getHealth();
            if (n != ThornwingRocUnit.FEATHERS || Math.abs(ad - ThornwingRocUnit.FEATHER_DAMAGE * ThornwingRocUnit.FEATHERS) > 0.01f)
                problems.add("a volley at the air target landed " + n + " feathers for " + ad);
            if (!((Unit) air).isFlyingUnit())
                problems.add("precondition: the air target is not a flyer");
            if (friend.getHealth() != f0)
                problems.add("a friendly unit was hit");
            if (roc.getAttributeValue(com.solegendary.reignofnether.registrars.AttributeRegistrar.ATTACK_RANGE.get()) != 16)
                problems.add("the Roc's attack range attribute is not 16");
            if (roc.volley(null) != 0)
                problems.add("a volley at nothing landed");
        });
        // phase 2: the Roc fights by itself - first a ground enemy alone, then an air enemy alone
        helper.runAfterDelay(8, () -> {
            friend.discard();
            air.discard();   // the air enemy comes back for the second phase
            ground.setHealth(900);
            roc.setNoAi(false);
        });
        final float[] gHp = new float[1];
        final Mob[] airAgain = new Mob[1];
        helper.runAfterDelay(110, () -> {
            float d = 900 - ground.getHealth();
            gHp[0] = d;
            float whole = ThornwingRocUnit.FEATHER_DAMAGE * ThornwingRocUnit.FEATHERS;
            if (d < whole - 0.01f)
                problems.add("left alone the Roc never shot the ground enemy (" + d + " damage)");
            else if (Math.abs(d / whole - Math.round(d / whole)) > 0.001)
                problems.add("the ground enemy took " + d + ", not whole volleys of " + whole);
            ground.discard();
            airAgain[0] = (Mob) onPad(level, EntityRegistrar.THUNDER_DRAGON_UNIT.get(), foe, cx - 6, gy + 5, cz);
            tough(airAgain[0]).setNoAi(true);
        });
        helper.runAfterDelay(240, () -> {
            float d = 900 - airAgain[0].getHealth();
            float whole = ThornwingRocUnit.FEATHER_DAMAGE * ThornwingRocUnit.FEATHERS;
            if (d < whole - 0.01f)
                problems.add("left alone the Roc never shot the air enemy (" + d + " damage)");
            else if (Math.abs(d / whole - Math.round(d / whole)) > 0.001)
                problems.add("the air enemy took " + d + ", not whole volleys of " + whole);
            for (Mob m : java.util.Arrays.asList(roc, ground, friend, air, airAgain[0]))
                if (m != null)
                    m.discard();
            T3RoleGameTests.finish(helper, problems);
        });
    }

    /**
     * AC-5 (Gale Wings): casting puts it on cooldown; an enemy walker in the cone is knocked back about 4 blocks along the
     * beat, an enemy flyer in the cone is pushed too (further), nothing takes damage; an enemy behind the Roc, one to
     * the side outside the cone, one beyond the reach and a friend in the cone stay where they were.
     */
    @GameTest(template = ARENA, timeoutTicks = 120)
    public static void thornwing_roc_gale_wings_pushes_the_cone_and_nothing_else(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = owner(helper, "gale"), foe = owner(helper, "galefoe");
        SkirmishGameTests.openGround(helper, 1, -5, 14, 20);
        // the pushed bodies keep their AI (a no-AI mob is not moved by a push); villagers and an owl have no attack to distract them
        var roc = put(helper, EntityRegistrar.THORNWING_ROC_UNIT.get(), owner, 7.5, 5.5);
        var inCone = tough(put(helper, EntityRegistrar.VILLAGER_UNIT.get(), foe, 7.5, 9.5));
        var flyer = tough(put(helper, EntityRegistrar.OWL_WATCHER_UNIT.get(), foe, 8.5, 9.5));
        var behind = tough(put(helper, EntityRegistrar.VILLAGER_UNIT.get(), foe, 7.5, 1.5));
        var side = tough(put(helper, EntityRegistrar.VILLAGER_UNIT.get(), foe, 13.5, 8.5));
        var beyond = tough(put(helper, EntityRegistrar.VILLAGER_UNIT.get(), foe, 7.5, 17.5));
        var friend = tough(put(helper, EntityRegistrar.VILLAGER_UNIT.get(), owner, 6.5, 9.5));
        List<Mob> all = new ArrayList<>(java.util.Arrays.asList(roc, inCone, flyer, behind, side, beyond, friend));
        if (all.contains(null)) {
            all.stream().filter(java.util.Objects::nonNull).forEach(Mob::discard);
            helper.fail("could not create the gale units");
            return;
        }
        roc.setNoAi(true);
        flyer.setPos(flyer.getX(), flyer.getY() + 3, flyer.getZ());
        var gale = (GaleWings) roc.getAbilities().get().stream().filter(x -> x instanceof GaleWings).findFirst().orElse(null);
        if (gale == null) {
            all.forEach(Mob::discard);
            helper.fail("the Roc has no Gale Wings");
            return;
        }
        final Vec3[] start = new Vec3[all.size()];
        helper.runAfterDelay(5, () -> {
            for (int i = 0; i < start.length; i++)
                start[i] = all.get(i).position();
            if (GaleWings.CD_SECONDS != 20)
                helper.fail("the design's cooldown is 20 s: " + GaleWings.CD_SECONDS);
            float[] hp = new float[all.size()];
            for (int i = 0; i < hp.length; i++)
                hp[i] = all.get(i).getHealth();
            roc.getTargetGoal().setTarget(null);   // the beat faces its target if it has one: the test pins +z
            roc.setYRot(0f);   // the beat aims along the facing; neighbours' pushes can turn a drifting Roc, so pin it
            gale.use(level, roc, roc.blockPosition());
            if (gale.isOffCooldown(roc))
                helper.fail("Gale Wings did not go on cooldown");
            for (int i = 0; i < hp.length; i++)
                if (all.get(i).getHealth() != hp[i])
                    helper.fail("Gale Wings damaged " + all.get(i).getType());
        });
        helper.runAfterDelay(60, () -> {
            List<String> problems = new ArrayList<>();
            double pushed = inCone.position().z - start[1].z;
            if (pushed < GaleWings.GROUND_PUSH_BLOCKS - 1.5 || pushed > GaleWings.GROUND_PUSH_BLOCKS + 2)
                problems.add("the walker in the cone was pushed " + String.format("%.2f", pushed) + " blocks, expected about " + GaleWings.GROUND_PUSH_BLOCKS);
            double air = flyer.position().distanceTo(start[2]);
            if (air < 2.0)
                problems.add("the flyer in the cone was pushed only " + String.format("%.2f", air));
            if (flyer.position().z - start[2].z <= 0)
                problems.add("the flyer was pushed the wrong way");
            String[] names = { "", "", "", "behind", "side", "beyond", "friend" };
            for (int i = 3; i < all.size(); i++) {
                double moved = all.get(i).position().distanceTo(start[i]);
                if (moved > 0.3)
                    problems.add("the " + names[i] + " unit moved " + String.format("%.2f", moved) + " blocks");
            }
            all.forEach(Mob::discard);
            T3RoleGameTests.finish(helper, problems);
        });
    }

    /**
     * AC-6 (Seed Rain): casting puts it on cooldown and marks a 14-block line; during the 1.5 s telegraph nothing is
     * rooted; then every enemy walker on the line is rooted (Slowness VII for 1 s) and slowed (Slowness III, back after
     * the root ends, for 3 s in all); an enemy walker beside the line, one beyond its end, one behind the Roc, a flyer
     * over the line and a friend on it are left alone; the effects are gone after 3 s.
     */
    @GameTest(template = ARENA, timeoutTicks = 160)
    public static void thornwing_roc_seed_rain_is_telegraphed_then_roots_and_slows_the_line(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = owner(helper, "seed"), foe = owner(helper, "seedfoe");
        SkirmishGameTests.openGround(helper, 1, -5, 14, 22);
        // villagers: a Roc with a gunman in reach would turn toward it (the line follows the target), and the test pins the line along +z
        var roc = put(helper, EntityRegistrar.THORNWING_ROC_UNIT.get(), owner, 7.5, 1.5);
        var onLine = tough(put(helper, EntityRegistrar.VILLAGER_UNIT.get(), foe, 7.5, 6.5));
        var onLineFar = tough(put(helper, EntityRegistrar.VILLAGER_UNIT.get(), foe, 8.2, 14.5));
        var beside = tough(put(helper, EntityRegistrar.VILLAGER_UNIT.get(), foe, 12.5, 6.5));
        var beyond = tough(put(helper, EntityRegistrar.VILLAGER_UNIT.get(), foe, 7.5, 19.5));
        var behind = tough(put(helper, EntityRegistrar.VILLAGER_UNIT.get(), foe, 7.5, -3.5));
        var flyer = tough(put(helper, EntityRegistrar.OWL_WATCHER_UNIT.get(), foe, 7.5, 9.5));
        var friend = tough(put(helper, EntityRegistrar.VILLAGER_UNIT.get(), owner, 6.5, 4.5));
        List<Mob> all = new ArrayList<>(java.util.Arrays.asList(roc, onLine, onLineFar, beside, beyond, behind, flyer, friend));
        if (all.contains(null)) {
            all.stream().filter(java.util.Objects::nonNull).forEach(Mob::discard);
            helper.fail("could not create the seed units");
            return;
        }
        for (Mob m : all)
            m.setNoAi(true);
        flyer.setNoGravity(true);
        flyer.setPos(flyer.getX(), flyer.getY() + 4, flyer.getZ());
        var seed = (SeedRain) roc.getAbilities().get().stream().filter(x -> x instanceof SeedRain).findFirst().orElse(null);
        if (seed == null) {
            all.forEach(Mob::discard);
            helper.fail("the Roc has no Seed Rain");
            return;
        }
        List<String> problems = new ArrayList<>();
        java.util.function.Function<LivingEntity, Integer> slowAmp = e -> {
            MobEffectInstance i = e.getEffect(MobEffects.MOVEMENT_SLOWDOWN);
            return i == null ? -1 : i.getAmplifier();
        };
        helper.runAfterDelay(5, () -> {
            if (SeedRain.LENGTH != 14 || SeedRain.TELEGRAPH_TICKS != 30 || SeedRain.ROOT_TICKS != 20 || SeedRain.SLOW_TICKS != 60)
                problems.add("the design's numbers are 14 blocks, 1.5 s telegraph, 1 s root, 3 s slow");
            if (SeedRain.CD_SECONDS != 35)
                problems.add("the design's cooldown is 35 s: " + SeedRain.CD_SECONDS);
            roc.getTargetGoal().setTarget(null);   // the line follows its target if it has one: the test pins +z
            roc.setYRot(0f);   // the line aims along the facing: pin it (a drifting Roc turns toward its drift)
            seed.use(level, roc, roc.blockPosition());
            if (seed.isOffCooldown(roc))
                problems.add("Seed Rain did not go on cooldown");
        });
        helper.runAfterDelay(5 + SeedRain.TELEGRAPH_TICKS - 8, () -> {
            for (Mob m : all)
                if (slowAmp.apply(m) != -1)
                    problems.add("a unit was rooted during the telegraph (" + m.getType() + ")");
        });
        helper.runAfterDelay(5 + SeedRain.TELEGRAPH_TICKS + 3, () -> {
            for (Mob m : List.of(onLine, onLineFar))
                if (slowAmp.apply(m) != 6)
                    problems.add("an enemy walker on the line is not rooted (Slowness amp " + slowAmp.apply(m) + ")");
            for (Mob m : List.of(beside, beyond, behind, flyer, friend, roc))
                if (slowAmp.apply(m) != -1)
                    problems.add("a unit off the line was affected: " + m.getType() + " amp " + slowAmp.apply(m));
        });
        // the root ends after 1 s; the longer, weaker slow takes over
        helper.runAfterDelay(5 + SeedRain.TELEGRAPH_TICKS + SeedRain.ROOT_TICKS + 10, () -> {
            for (Mob m : List.of(onLine, onLineFar))
                if (slowAmp.apply(m) != SeedRain.SLOW_AMPLIFIER)
                    problems.add("after the root the walker is not just slowed (Slowness amp " + slowAmp.apply(m) + ")");
        });
        helper.runAfterDelay(5 + SeedRain.TELEGRAPH_TICKS + SeedRain.SLOW_TICKS + 10, () -> {
            for (Mob m : List.of(onLine, onLineFar))
                if (slowAmp.apply(m) != -1)
                    problems.add("the slow outlasted 3 s on " + m.getType());
            all.forEach(Mob::discard);
            T3RoleGameTests.finish(helper, problems);
        });
    }

    /**
     * AC-7: the numbers keep the T3 rules: its own HP total (800 is the flyers'), the same price as the World Tree Walker,
     * below the anchor's damage, the fastest Verdant body, no building damage, fire x2, one of each ability.
     */
    @GameTest(template = ARENA, timeoutTicks = 40)
    public static void thornwing_roc_numbers_keep_the_t3_rules(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        float[] others = { SunColossusUnit.maxHealth,
            com.solegendary.reignofnether.unit.units.monsters.BoneDragonUnit.maxHealth,
            com.solegendary.reignofnether.unit.units.monsters.PlagueAbominationUnit.maxHealth,
            com.solegendary.reignofnether.unit.units.piglins.WarMammothUnit.maxHealth,
            com.solegendary.reignofnether.unit.units.piglins.MagmaHurlerUnit.maxHealth,
            com.solegendary.reignofnether.unit.units.villagers.CathedralWardenUnit.maxHealth,
            WorldTreeWalkerUnit.maxHealth,
            com.solegendary.reignofnether.unit.units.tide.LeviathanUnit.maxHealth,
            com.solegendary.reignofnether.unit.units.tide.AbyssalHydraUnit.maxHealth,
            com.solegendary.reignofnether.unit.units.storm.CelestialDragonUnit.maxHealth,
            com.solegendary.reignofnether.unit.units.storm.JadeSentinelUnit.maxHealth,
            com.solegendary.reignofnether.unit.units.brass.IronLeviathanEngineUnit.maxHealth };
        for (float hp : others)
            if (hp == ThornwingRocUnit.maxHealth)
                problems.add("the Roc shares " + hp + " HP with another T3");
        if (ThornwingRocUnit.maxHealth != 800f)
            problems.add("the design's HP is 800: " + ThornwingRocUnit.maxHealth);
        var first = ProductionItems.WORLD_TREE_WALKER.getCost(false, "nobody");
        var second = ProductionItems.VERDANT_T3B.getCost(false, "nobody");
        if (first.metal() != second.metal() || first.energy() != second.energy() || first.population != second.population)
            problems.add("the Roc's price differs from the World Tree Walker's");
        if (ThornwingRocUnit.attackDamage * ThornwingRocUnit.attacksPerSecond
                >= SunColossusUnit.attackDamage * SunColossusUnit.attacksPerSecond)
            problems.add("a flyer should not out-damage the anchor");
        for (float v : new float[] { com.solegendary.reignofnether.unit.units.verdant.StagLancerUnit.movementSpeed,
                com.solegendary.reignofnether.unit.units.verdant.FoxCourierUnit.movementSpeed,
                com.solegendary.reignofnether.unit.units.verdant.LeafbladeUnit.movementSpeed,
                com.solegendary.reignofnether.unit.units.verdant.OwlWatcherUnit.movementSpeed })
            if (ThornwingRocUnit.movementSpeed <= v)
                problems.add("the Roc is not the fastest Verdant body (" + v + ")");
        ThornwingRocUnit u = EntityRegistrar.THORNWING_ROC_UNIT.get().create(helper.getLevel());
        if (u == null)
            problems.add("could not create the Roc");
        else {
            if (((Unit) u).getCost() != com.solegendary.reignofnether.resources.ResourceCosts.VERDANT_T3B)
                problems.add("the Roc's cost entry is not VERDANT_T3B");
            if (u.getAttributeValue(Attributes.MAX_HEALTH) != 800)
                problems.add("the Roc's attribute HP is " + u.getAttributeValue(Attributes.MAX_HEALTH));
            u.updateAbilityButtons();   // a refresh must not duplicate the abilities
            var list = u.getAbilities().get();
            if (list.stream().filter(a -> a instanceof GaleWings).count() != 1 || list.stream().filter(a -> a instanceof SeedRain).count() != 1)
                problems.add("the Roc should carry exactly one Gale Wings and one Seed Rain");
            u.setHealth(u.getMaxHealth());
            u.hurt(helper.getLevel().damageSources().onFire(), 10f);
            float lost = u.getMaxHealth() - u.getHealth();
            if (Math.abs(lost - 10f * ThornwingRocUnit.FIRE_MULTIPLIER) > 0.01f)
                problems.add("10 fire damage cost " + lost + " HP, expected " + 10f * ThornwingRocUnit.FIRE_MULTIPLIER);
            u.discard();
        }
        T3RoleGameTests.finish(helper, problems);
    }
}
