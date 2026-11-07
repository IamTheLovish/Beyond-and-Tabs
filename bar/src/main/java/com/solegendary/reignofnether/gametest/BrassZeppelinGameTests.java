package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.ability.abilities.BallastDump;
import com.solegendary.reignofnether.ability.abilities.CarpetBomb;
import com.solegendary.reignofnether.brass.Heat;
import com.solegendary.reignofnether.brass.Machine;
import com.solegendary.reignofnether.building.Buildings;
import com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement;
import com.solegendary.reignofnether.building.production.ProductionItems;
import com.solegendary.reignofnether.keybinds.Keybindings;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.unit.T3Cap;
import com.solegendary.reignofnether.unit.goals.FlyingMoveToTargetGoal;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.units.brass.BrassZeppelinUnit;
import com.solegendary.reignofnether.unit.units.brass.IronLeviathanEngineUnit;
import com.solegendary.reignofnether.unit.units.tide.BombardShell;
import com.solegendary.reignofnether.unit.units.villagers.SunColossusUnit;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
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
 * Slice T3b-8 of the second Tier 3 (second-t3-design.md): the Brassworks <b>Brass Zeppelin</b>. One test per acceptance
 * criterion, all un-hosted, asserting only on the test's own spawned entities: trained from slot 5 (T) of the Great
 * Forge as its own type and capped one alive beside the Iron Leviathan Engine; it flies (stays above the ground, crosses
 * a wall); Carpet Bomb is telegraphed, then its bombs hit the ground units on the line and nothing else (no flyer, no
 * friend, nothing off the line), and costs energy; Flak Burst hits air units in its radius and never the ground; Ballast
 * Dump speeds it up, stops its guns and vents steam; the numbers keep the T3 rules.
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class BrassZeppelinGameTests {

    static final String ARENA = SkirmishGameTests.ARENA;

    static String owner(GameTestHelper helper, String tag) {
        return "gametest_zep_" + tag + "_" + helper.absolutePos(BlockPos.ZERO).asLong();
    }

    /** A glass pad {@code w} x {@code l} high above the arena (nothing else spawns on it); returns the standing height at the arena's corner. */
    static BlockPos pad(GameTestHelper helper, int y, int w, int l) {
        BlockPos corner = helper.absolutePos(BlockPos.ZERO);
        for (int dx = 0; dx < w; dx++)
            for (int dz = 0; dz < l; dz++)
                helper.getLevel().setBlock(new BlockPos(corner.getX() + dx, y, corner.getZ() + dz), Blocks.GLASS.defaultBlockState(), 3);
        return new BlockPos(corner.getX(), y + 1, corner.getZ());
    }

    static void clearPad(GameTestHelper helper, int y, int w, int l) {
        BlockPos corner = helper.absolutePos(BlockPos.ZERO);
        for (int dx = 0; dx < w; dx++)
            for (int dz = 0; dz < l; dz++)
                helper.getLevel().setBlock(new BlockPos(corner.getX() + dx, y, corner.getZ() + dz), Blocks.AIR.defaultBlockState(), 3);
    }

    static Mob at(ServerLevel level, net.minecraft.world.entity.EntityType<? extends Mob> type, String owner, double x, double y, double z) {
        return ThornwingRocGameTests.onPad(level, type, owner, x, y, z);
    }

    /** AC-1: trained from slot 5 (T) of the Great Forge as a Brass Zeppelin (no placeholder tag, no Engine body). */
    @GameTest(template = ARENA, timeoutTicks = 60)
    public static void brass_zeppelin_is_trained_from_slot_t_as_its_own_unit(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        ServerLevel level = helper.getLevel();
        String owner = owner(helper, "train");
        if (Buildings.GREAT_FORGE.productions.forHotkey(Keybindings.abilitySlot5) != ProductionItems.BRASS_T3B)
            problems.add("slot 5 (T) of the Great Forge is not the second T3");
        if (!BrassZeppelinUnit.ITEM_NAME.equals(ProductionItems.BRASS_T3B.getItemName()))
            problems.add("the second Brassworks T3 is still named " + ProductionItems.BRASS_T3B.getItemName());
        ProductionPlacement pp = SecondT3FrameGameTests.placement(helper, Buildings.GREAT_FORGE, owner);
        if (pp == null) {
            T3RoleGameTests.finish(helper, List.of("no Great Forge placement"));
            return;
        }
        ProductionItems.BRASS_T3B.onComplete.accept(level, pp);
        helper.runAfterDelay(3, () -> {
            AABB all = new AABB(helper.absolutePos(BlockPos.ZERO)).inflate(80);
            List<BrassZeppelinUnit> made = level.getEntitiesOfClass(BrassZeppelinUnit.class, all, u -> owner.equals(u.getOwnerName()));
            if (made.size() != 1)
                problems.add("training the second Brassworks T3 made " + made.size() + " Brass Zeppelins");
            else {
                if (made.get(0).getPersistentData().getBoolean(T3Cap.TAG_SECOND))
                    problems.add("a real unit must not carry the placeholder tag");
                made.get(0).discard();
            }
            if (level.getEntitiesOfClass(IronLeviathanEngineUnit.class, all, u -> owner.equals(u.getOwnerName())).size() != 0)
                problems.add("the second Brassworks T3 trained an Iron Leviathan Engine body");
            T3RoleGameTests.finish(helper, problems);
        });
    }

    /** AC-2: one alive of each type: a living Zeppelin blocks a second Zeppelin, not an Engine; and the other way round. */
    @GameTest(template = ARENA, timeoutTicks = 60)
    public static void brass_zeppelin_cap_is_one_alive_beside_the_iron_leviathan_engine(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        String owner = owner(helper, "cap");
        SecondT3FrameGameTests.grantTiers(owner);
        ProductionPlacement pp = SecondT3FrameGameTests.placement(helper, Buildings.GREAT_FORGE, owner);
        if (pp == null) {
            T3RoleGameTests.finish(helper, List.of("no Great Forge placement"));
            return;
        }
        if (T3Cap.productionError(pp, ProductionItems.BRASS_T3B) != null)
            problems.add("a Zeppelin was refused with none alive");
        var zep = ThornwingRocGameTests.put(helper, EntityRegistrar.BRASS_ZEPPELIN_UNIT.get(), owner, 3.5, 8.5);
        if (zep == null) {
            T3RoleGameTests.finish(helper, List.of("could not create the Zeppelin"));
            return;
        }
        zep.setNoAi(true);
        if (!T3Cap.LIMIT_MSG.equals(T3Cap.productionError(pp, ProductionItems.BRASS_T3B)))
            problems.add("a second Zeppelin was not refused while one lives");
        if (T3Cap.productionError(pp, ProductionItems.IRON_LEVIATHAN_ENGINE) != null)
            problems.add("a living Zeppelin blocked the Iron Leviathan Engine");
        zep.discard();
        var engine = ThornwingRocGameTests.put(helper, EntityRegistrar.IRON_LEVIATHAN_ENGINE_UNIT.get(), owner, 3.5, 8.5);
        if (engine != null) {
            engine.setNoAi(true);
            if (T3Cap.productionError(pp, ProductionItems.BRASS_T3B) != null)
                problems.add("a living Iron Leviathan Engine blocked the Zeppelin");
            if (!T3Cap.LIMIT_MSG.equals(T3Cap.productionError(pp, ProductionItems.IRON_LEVIATHAN_ENGINE)))
                problems.add("a second Iron Leviathan Engine was not refused while one lives");
            engine.discard();
        }
        T3RoleGameTests.finish(helper, problems);
    }

    /**
     * AC-3 (flight): the Zeppelin is a flyer to every anti-air unit (flying move goal, no ground garrison); ordered across
     * a 10-high wall it flies over it, never inside the wall, and never touches the ground on the way. It is slow, so the
     * wall is close. Own sky pad at 230.
     */
    @GameTest(template = ARENA, timeoutTicks = 400)
    public static void brass_zeppelin_flies_above_the_ground_and_crosses_a_wall(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pad = pad(helper, 230, 16, 16);
        final int gy = pad.getY();
        List<BlockPos> wall = new ArrayList<>();
        for (int dz = 2; dz <= 14; dz++)
            for (int y = gy; y <= gy + 9; y++)
                wall.add(new BlockPos(pad.getX() + 8, y, pad.getZ() + dz));
        for (BlockPos b : wall)
            level.setBlock(b, Blocks.STONE.defaultBlockState(), 3);
        var zep = (BrassZeppelinUnit) at(level, EntityRegistrar.BRASS_ZEPPELIN_UNIT.get(), owner(helper, "fly"), pad.getX() + 5.5, gy + 8, pad.getZ() + 8.5);
        if (zep == null) {
            wall.forEach(b -> level.setBlock(b, Blocks.AIR.defaultBlockState(), 3));
            clearPad(helper, 230, 16, 16);
            helper.fail("could not create the Zeppelin");
            return;
        }
        BlockPos target = new BlockPos(pad.getX() + 14, gy - 1, pad.getZ() + 8);
        Runnable clean = () -> {
            zep.discard();
            for (BlockPos b : wall)
                level.setBlock(b, Blocks.AIR.defaultBlockState(), 3);
            clearPad(helper, 230, 16, 16);
        };
        List<String> problems = new ArrayList<>();
        helper.runAfterDelay(3, () -> {
            if (!zep.isFlyingUnit() || !(zep.getMoveGoal() instanceof FlyingMoveToTargetGoal))
                problems.add("the Zeppelin is not a flyer to anti-air");
            if (zep.canGarrison())
                problems.add("a flyer cannot garrison");
            zep.getMoveGoal().setMoveTarget(target);
        });
        final int[] inWallAt = { -1 };
        final int[] lowAt = { -1 };
        for (int t = 10; t <= 360; t += 5) {
            final int tick = t;
            helper.runAfterDelay(t, () -> {
                if (inWallAt[0] < 0 && zep.isInWall())
                    inWallAt[0] = tick;
                if (lowAt[0] < 0 && (zep.onGround() || zep.getY() < gy + 0.5))
                    lowAt[0] = tick;
            });
        }
        helper.runAfterDelay(365, () -> {
            double x = zep.getX();
            if (inWallAt[0] >= 0)
                problems.add("the Zeppelin was in a wall at tick " + inWallAt[0]);
            if (lowAt[0] >= 0)
                problems.add("the Zeppelin touched the ground at tick " + lowAt[0]);
            if (x < pad.getX() + 9.5 + zep.getBbWidth() / 2)
                problems.add(String.format("the Zeppelin did not cross the wall: x %.1f (wall at x %d)", x, pad.getX() + 8));
            clean.run();
            T3RoleGameTests.finish(helper, problems);
        });
    }

    /**
     * AC-4 (Carpet Bomb, exact): casting puts it on cooldown and marks the 20-block line; during the 1.5 s telegraph
     * nothing is hurt; then the five bombs fall and every enemy ground unit standing on a bomb's mark takes the bomb's
     * damage, once; a flyer hovering low right under the line, an enemy beside the line, one past its end, one behind the
     * Zeppelin and a friend on the line are left alone. Own 16 x 32 sky pad at 240.
     */
    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void brass_zeppelin_carpet_bomb_is_telegraphed_then_hits_the_ground_in_a_line_and_not_air(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = owner(helper, "bomb"), foe = owner(helper, "bombfoe");
        BlockPos pad = pad(helper, 240, 16, 32);
        final int gy = pad.getY();
        double cx = pad.getX() + 8.5, z0 = pad.getZ() + 3.5;
        var zep = (BrassZeppelinUnit) at(level, EntityRegistrar.BRASS_ZEPPELIN_UNIT.get(), owner, cx, gy + 10, z0);
        // enemy ground units on the first, third and last bomb marks (2, 10 and 18 blocks down the line)
        var m0 = at(level, EntityRegistrar.VINDICATOR_UNIT.get(), foe, cx, gy, z0 + CarpetBomb.bombDistance(0));
        var m2 = at(level, EntityRegistrar.VINDICATOR_UNIT.get(), foe, cx, gy, z0 + CarpetBomb.bombDistance(2));
        var m4 = at(level, EntityRegistrar.VINDICATOR_UNIT.get(), foe, cx, gy, z0 + CarpetBomb.bombDistance(4));
        var flyer = at(level, EntityRegistrar.OWL_WATCHER_UNIT.get(), foe, cx, gy + 1.5, z0 + CarpetBomb.bombDistance(1));
        var beside = at(level, EntityRegistrar.VINDICATOR_UNIT.get(), foe, cx + 6, gy, z0 + CarpetBomb.bombDistance(2));
        var beyond = at(level, EntityRegistrar.VINDICATOR_UNIT.get(), foe, cx, gy, z0 + CarpetBomb.LENGTH + 4);
        var behind = at(level, EntityRegistrar.VINDICATOR_UNIT.get(), foe, cx, gy, z0 - 3);
        var friend = at(level, EntityRegistrar.VINDICATOR_UNIT.get(), owner, cx, gy, z0 + CarpetBomb.bombDistance(3));
        List<Mob> all = new ArrayList<>(java.util.Arrays.asList(zep, m0, m2, m4, flyer, beside, beyond, behind, friend));
        if (all.contains(null)) {
            all.stream().filter(java.util.Objects::nonNull).forEach(Mob::discard);
            clearPad(helper, 240, 16, 32);
            helper.fail("could not create the bomb units");
            return;
        }
        for (Mob m : all) {
            ThornwingRocGameTests.tough(m).setNoAi(true);
        }
        flyer.setNoGravity(true);
        zep.setNoGravity(true);
        var bomb = (CarpetBomb) zep.getAbilities().get().stream().filter(x -> x instanceof CarpetBomb).findFirst().orElse(null);
        if (bomb == null) {
            all.forEach(Mob::discard);
            clearPad(helper, 240, 16, 32);
            helper.fail("the Zeppelin has no Carpet Bomb");
            return;
        }
        List<String> problems = new ArrayList<>();
        final float[] hp = new float[all.size()];
        helper.runAfterDelay(5, () -> {
            if (CarpetBomb.LENGTH != 20 || CarpetBomb.TELEGRAPH_TICKS != 30 || CarpetBomb.BOMB_COUNT * CarpetBomb.BOMB_GAP_TICKS != 40)
                problems.add("the design's numbers are a 20-block line, 1.5 s telegraph, bombs over 2 s");
            if (CarpetBomb.CD_SECONDS != 30)
                problems.add("the design's cooldown is 30 s: " + CarpetBomb.CD_SECONDS);
            for (int i = 0; i < hp.length; i++)
                hp[i] = all.get(i).getHealth();
            zep.getTargetGoal().setTarget(null);   // the line follows its target if it has one: the test pins +z
            zep.setYRot(0f);
            bomb.use(level, zep, zep.blockPosition());
            if (bomb.isOffCooldown(zep))
                problems.add("Carpet Bomb did not go on cooldown");
        });
        helper.runAfterDelay(5 + CarpetBomb.TELEGRAPH_TICKS - 4, () -> {
            for (int i = 1; i < hp.length; i++)
                if (all.get(i).getHealth() != hp[i])
                    problems.add("a unit was hurt during the telegraph (" + all.get(i).getType() + ")");
            if (!level.getEntitiesOfClass(BombardShell.class, new AABB(pad).inflate(40)).isEmpty())
                problems.add("a bomb fell during the telegraph");
        });
        // the first bomb is out at TELEGRAPH, in the air for 20 ticks; check that bombs are ground-only shells in flight
        helper.runAfterDelay(5 + CarpetBomb.TELEGRAPH_TICKS + 4, () -> {
            var shells = level.getEntitiesOfClass(BombardShell.class, new AABB(pad).inflate(40));
            if (shells.size() != 1)
                problems.add("expected the first bomb in flight, found " + shells.size());
            else if (!shells.get(0).isGroundOnly() || shells.get(0).getDamage() != CarpetBomb.BOMB_DAMAGE)
                problems.add("the bomb is not a ground-only shell of " + CarpetBomb.BOMB_DAMAGE);
        });
        helper.runAfterDelay(5 + CarpetBomb.TELEGRAPH_TICKS + CarpetBomb.BOMB_COUNT * CarpetBomb.BOMB_GAP_TICKS + 40, () -> {
            for (int i : new int[] { 1, 2, 3 }) {
                float loss = hp[i] - all.get(i).getHealth();
                if (loss < CarpetBomb.BOMB_DAMAGE - 1.5f || loss > CarpetBomb.BOMB_DAMAGE + 0.01f)
                    problems.add("the enemy on bomb mark " + (i == 1 ? 0 : i == 2 ? 2 : 4) + " took " + loss + ", expected " + CarpetBomb.BOMB_DAMAGE);
            }
            String[] names = { "", "", "", "", "flyer under the line", "unit beside the line", "unit past its end", "unit behind the Zeppelin", "friend on the line" };
            for (int i = 4; i < all.size(); i++)
                if (all.get(i).getHealth() != hp[i])
                    problems.add("the " + names[i] + " was hurt (" + (hp[i] - all.get(i).getHealth()) + ")");
            if (zep.getHealth() != hp[0])
                problems.add("the Zeppelin hurt itself");
            all.forEach(Mob::discard);
            level.getEntitiesOfClass(BombardShell.class, new AABB(pad).inflate(40)).forEach(net.minecraft.world.entity.Entity::discard);
            clearPad(helper, 240, 16, 32);
            T3RoleGameTests.finish(helper, problems);
        });
    }

    private static com.solegendary.reignofnether.resources.Resources manaPool(String owner, float energy) {
        var pool = new com.solegendary.reignofnether.resources.Resources(owner, 0, 0, 0);
        pool.addEnergy(energy);
        com.solegendary.reignofnether.resources.ResourcesServerEvents.resourcesList.add(pool);
        com.solegendary.reignofnether.mana.ManaServerEvents.bindTraits(owner, com.solegendary.reignofnether.faction.FactionTraits.STORMCALLERS);
        return pool;
    }

    private static void dropManaPool(String owner, com.solegendary.reignofnether.resources.Resources pool) {
        com.solegendary.reignofnether.mana.ManaServerEvents.clearOwner(owner);
        com.solegendary.reignofnether.resources.ResourcesServerEvents.resourcesList.remove(pool);
        var eco = com.solegendary.reignofnether.resources.EconomyServerEvents.getEconomy(owner);
        eco.conversionCapacity = 0;
        eco.stall = 1f;
    }

    /** AC-4b (energy): a sortie costs energy: refused (nothing paid, no cooldown) when the pool is short, paid exactly when it is not. Sky pad 250. */
    @GameTest(template = ARENA, timeoutTicks = 60)
    public static void brass_zeppelin_carpet_bomb_costs_energy_per_sortie(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = owner(helper, "energy");
        BlockPos pad = pad(helper, 250, 16, 16);
        var zep = (BrassZeppelinUnit) at(level, EntityRegistrar.BRASS_ZEPPELIN_UNIT.get(), owner, pad.getX() + 8.5, pad.getY() + 6, pad.getZ() + 8.5);
        if (zep == null) {
            clearPad(helper, 250, 16, 16);
            helper.fail("could not create the Zeppelin");
            return;
        }
        zep.setNoAi(true);
        zep.setNoGravity(true);
        var bomb = (CarpetBomb) zep.getAbilities().get().stream().filter(x -> x instanceof CarpetBomb).findFirst().orElse(null);
        var pool = manaPool(owner, CarpetBomb.ENERGY_COST - 20);
        List<String> problems = new ArrayList<>();
        try {
            if (CarpetBomb.ENERGY_COST != 100)
                problems.add("the sortie costs " + CarpetBomb.ENERGY_COST + ", not the 100 the tooltips say");
            bomb.use(level, zep, zep.blockPosition());
            if (!bomb.isOffCooldown(zep))
                problems.add("a Zeppelin with too little energy flew a sortie");
            if (Math.abs(pool.getEnergy() - (CarpetBomb.ENERGY_COST - 20)) > 0.01f)
                problems.add("a refused sortie still cost energy: " + pool.getEnergy());
            pool.addEnergy(20);
            bomb.use(level, zep, zep.blockPosition());
            if (bomb.isOffCooldown(zep))
                problems.add("a Zeppelin with enough energy did not fly the sortie");
            if (pool.getEnergy() > 0.01f)
                problems.add("the sortie should have taken " + CarpetBomb.ENERGY_COST + " energy; " + pool.getEnergy() + " is left");
        } finally {
            dropManaPool(owner, pool);
            zep.discard();
            clearPad(helper, 250, 16, 16);
        }
        T3RoleGameTests.finish(helper, problems);
    }

    /**
     * AC-5 (Flak Burst, exact): a burst at an air unit hurts it and every enemy flyer within the radius for the burst's
     * damage, once; a flyer beyond the radius, a friendly flyer in it, a ground unit under it and a dead target take
     * nothing, and a ground target gets no burst. Then, left to itself on an isolated pad (260), it picks the enemy flyer on
     * its own, deals only whole bursts, and never touches the ground enemy beside it.
     */
    @GameTest(template = ARENA, timeoutTicks = 330)
    public static void brass_zeppelin_flak_burst_hits_air_in_its_radius_and_never_the_ground(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = owner(helper, "flak"), foe = owner(helper, "flakfoe");
        BlockPos pad = pad(helper, 260, 16, 16);
        final int gy = pad.getY();
        double cx = pad.getX() + 8.5, cz = pad.getZ() + 8.5;
        var zep = (BrassZeppelinUnit) at(level, EntityRegistrar.BRASS_ZEPPELIN_UNIT.get(), owner, cx, gy + 6, cz);
        var air = at(level, EntityRegistrar.THUNDER_DRAGON_UNIT.get(), foe, cx - 8, gy + 6, cz);
        var near = at(level, EntityRegistrar.OWL_WATCHER_UNIT.get(), foe, cx - 8, gy + 6, cz + 2);   // 2 from the target: in the radius
        var far = at(level, EntityRegistrar.OWL_WATCHER_UNIT.get(), foe, cx - 8, gy + 6, cz - 7);     // 7 from the target: outside
        var friendAir = at(level, EntityRegistrar.OWL_WATCHER_UNIT.get(), owner, cx - 8, gy + 6, cz - 2);
        var ground = at(level, EntityRegistrar.VINDICATOR_UNIT.get(), foe, cx - 8, gy, cz + 1);       // right under the burst
        List<Mob> all = new ArrayList<>(java.util.Arrays.asList(zep, air, near, far, friendAir, ground));
        if (all.contains(null)) {
            all.stream().filter(java.util.Objects::nonNull).forEach(Mob::discard);
            clearPad(helper, 260, 16, 16);
            helper.fail("could not create the flak units");
            return;
        }
        for (Mob m : List.of(air, near, far, friendAir, ground))
            ThornwingRocGameTests.tough(m).setNoAi(true);
        for (Mob m : List.of(air, near, far, friendAir))
            m.setNoGravity(true);
        zep.setNoAi(true);
        zep.setNoGravity(true);
        List<String> problems = new ArrayList<>();
        helper.runAfterDelay(3, () -> {
            if (zep.getAttackGoal() == null || zep.getAttackGoal() instanceof com.solegendary.reignofnether.unit.goals.AbstractMeleeAttackUnitGoal)
                problems.add("the Zeppelin should attack from range, not in melee");
            if (BrassZeppelinUnit.attackRange != 18)
                problems.add("the design's range is 18: " + BrassZeppelinUnit.attackRange);
            if (zep.getAttributeValue(com.solegendary.reignofnether.registrars.AttributeRegistrar.ATTACK_RANGE.get()) != 18)
                problems.add("the Zeppelin's attack range attribute is not 18");
            if (zep.canAttackBuildings() || zep.getAttackBuildingGoal() != null)
                problems.add("the flak has no building damage (the bombs do)");
            if (!((Unit) air).isFlyingUnit())
                problems.add("precondition: the air target is not a flyer");
            float a0 = air.getHealth(), n0 = near.getHealth(), f0 = far.getHealth(), fa0 = friendAir.getHealth(), g0 = ground.getHealth();
            int n = zep.flak(air);
            if (n != 2)
                problems.add("a burst at the air target hurt " + n + " flyers, expected the target and the one beside it (2)");
            if (Math.abs((a0 - air.getHealth()) - BrassZeppelinUnit.FLAK_DAMAGE) > 0.01f)
                problems.add("the target took " + (a0 - air.getHealth()) + ", expected " + BrassZeppelinUnit.FLAK_DAMAGE);
            if (Math.abs((n0 - near.getHealth()) - BrassZeppelinUnit.FLAK_DAMAGE) > 0.01f)
                problems.add("the flyer 2 blocks from the target took " + (n0 - near.getHealth()) + ", expected " + BrassZeppelinUnit.FLAK_DAMAGE);
            if (far.getHealth() != f0)
                problems.add("a flyer outside the burst was hurt");
            if (friendAir.getHealth() != fa0)
                problems.add("a friendly flyer was hurt");
            if (ground.getHealth() != g0)
                problems.add("a ground unit under the burst was hurt");
            if (zep.flak(ground) != 0 || ground.getHealth() != g0)
                problems.add("the flak fired at a ground target");
            if (zep.flak(null) != 0)
                problems.add("a burst at nothing landed");
            // a unit out of reach (1.5 x the range) gets nothing
            far.setPos(cx - 8 - 40, far.getY(), cz);
            f0 = far.getHealth();
            if (zep.flak(far) != 0 || far.getHealth() != f0)
                problems.add("a burst reached a target 40 blocks away");
            far.discard();
        });
        // phase 2: left alone it picks the enemy flyer by itself and ignores the ground enemy
        helper.runAfterDelay(8, () -> {
            near.discard();
            friendAir.discard();
            air.setHealth(900);
            zep.setNoAi(false);
        });
        final float whole = BrassZeppelinUnit.FLAK_DAMAGE;
        helper.runAfterDelay(300, () -> {
            float d = 900 - air.getHealth();
            if (d < whole - 0.01f)
                problems.add("left alone the Zeppelin never shot the enemy flyer (" + d + " damage)");
            else if (Math.abs(d / whole - Math.round(d / whole)) > 0.001)
                problems.add("the enemy flyer took " + d + ", not whole bursts of " + whole);
            if (ground.getHealth() != 900f)
                problems.add("left alone the Zeppelin hurt the ground enemy: " + (900f - ground.getHealth()));
            for (Mob m : all)
                m.discard();
            clearPad(helper, 260, 16, 16);
            T3RoleGameTests.finish(helper, problems);
        });
    }

    /**
     * AC-6 (Ballast Dump): casting puts it on cooldown and starts the 5 s vent: +40% speed (exactly), the flak and the
     * Carpet Bomb refuse, it takes 25% more damage, steam; a second dump while venting is refused; after 5 s the speed,
     * the heat and the guns are back. Own sky pad at 270.
     */
    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void brass_zeppelin_ballast_dump_speeds_it_up_stops_the_guns_and_vents(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = owner(helper, "ballast"), foe = owner(helper, "ballastfoe");
        BlockPos pad = pad(helper, 270, 16, 16);
        final int gy = pad.getY();
        double cx = pad.getX() + 8.5, cz = pad.getZ() + 8.5;
        var zep = (BrassZeppelinUnit) at(level, EntityRegistrar.BRASS_ZEPPELIN_UNIT.get(), owner, cx, gy + 6, cz);
        var air = at(level, EntityRegistrar.THUNDER_DRAGON_UNIT.get(), foe, cx - 8, gy + 6, cz);
        if (zep == null || air == null) {
            for (Mob m : new Mob[] { zep, air })
                if (m != null)
                    m.discard();
            clearPad(helper, 270, 16, 16);
            helper.fail("could not create the ballast units");
            return;
        }
        ThornwingRocGameTests.tough(air).setNoAi(true);
        air.setNoGravity(true);
        zep.setNoAi(true);
        zep.setNoGravity(true);
        var dump = (BallastDump) zep.getAbilities().get().stream().filter(x -> x instanceof BallastDump).findFirst().orElse(null);
        var bomb = (CarpetBomb) zep.getAbilities().get().stream().filter(x -> x instanceof CarpetBomb).findFirst().orElse(null);
        List<String> problems = new ArrayList<>();
        final double base = zep.getAttributeBaseValue(Attributes.MOVEMENT_SPEED);
        final float[] normalHit = new float[1];
        helper.runAfterDelay(3, () -> {
            if (BallastDump.CD_SECONDS != 40 || BallastDump.DURATION_TICKS != 100 || BallastDump.SPEED_BONUS != 0.4)
                problems.add("the design's numbers are 40 s cooldown, 5 s, +40%");
            if (!(zep instanceof Machine))
                problems.add("the Zeppelin is not a Brassworks Machine");
            // the damage it takes now, for the vent's 25% to be measured against
            zep.setHealth(zep.getMaxHealth());
            zep.invulnerableTime = 0;
            zep.hurt(level.damageSources().generic(), 40f);
            normalHit[0] = zep.getMaxHealth() - zep.getHealth();
            zep.setHealth(zep.getMaxHealth());
            if (zep.flak(air) != 1)
                problems.add("the flak should work before the dump");
            air.setHealth(900);
            dump.use(level, zep, zep.blockPosition());
            if (dump.isOffCooldown(zep))
                problems.add("Ballast Dump did not go on cooldown");
            if (zep.ventTicks() <= 0 || zep.heat() != Heat.MAX || !Heat.isVenting(zep))
                problems.add("the dump did not start the vent: " + zep.ventTicks() + " ticks, heat " + zep.heat());
        });
        helper.runAfterDelay(8, () -> {
            double speed = zep.getAttributeValue(Attributes.MOVEMENT_SPEED);
            if (Math.abs(speed - base * (1 + BallastDump.SPEED_BONUS)) > 1e-6)
                problems.add("speed while dumping is " + speed + ", expected " + base * (1 + BallastDump.SPEED_BONUS));
            if (Math.abs(zep.getAttributeValue(Attributes.FLYING_SPEED) - zep.getAttributeBaseValue(Attributes.FLYING_SPEED) * (1 + BallastDump.SPEED_BONUS)) > 1e-6)
                problems.add("the flying speed did not rise with it");
            float a0 = air.getHealth();
            if (zep.flak(air) != 0 || air.getHealth() != a0)
                problems.add("the flak fired while dumping");
            float cd = bomb.getCooldown(zep);
            bomb.use(level, zep, zep.blockPosition());
            if (bomb.getCooldown(zep) != cd)
                problems.add("a Carpet Bomb sortie started while dumping");
            int vent = zep.ventTicks();
            float dumpCd = dump.getCooldown(zep);
            if (BallastDump.dump(level, zep) || zep.ventTicks() > vent)
                problems.add("a second dump while venting restarted the vent");
            if (dump.getCooldown(zep) != dumpCd)
                problems.add("the cooldown moved on a refused dump");
            zep.setHealth(zep.getMaxHealth());
            zep.invulnerableTime = 0;
            zep.hurt(level.damageSources().generic(), 40f);
            float ventHit = zep.getMaxHealth() - zep.getHealth();
            if (Math.abs(ventHit / normalHit[0] - Heat.VENT_DAMAGE_TAKEN) > 0.02f)
                problems.add("a hit cost " + ventHit + " while venting against " + normalHit[0] + " normally, expected x" + Heat.VENT_DAMAGE_TAKEN);
        });
        helper.runAfterDelay(3 + BallastDump.DURATION_TICKS + 12, () -> {
            if (zep.ventTicks() != 0 || zep.heat() != 0)
                problems.add("the vent did not end: " + zep.ventTicks() + " ticks, heat " + zep.heat());
            if (Math.abs(zep.getAttributeValue(Attributes.MOVEMENT_SPEED) - base) > 1e-6)
                problems.add("the speed did not come back: " + zep.getAttributeValue(Attributes.MOVEMENT_SPEED));
            float a0 = air.getHealth();
            if (zep.flak(air) != 1 || Math.abs((a0 - air.getHealth()) - BrassZeppelinUnit.FLAK_DAMAGE) > 0.01f)
                problems.add("the flak did not come back after the dump");
            zep.discard();
            air.discard();
            clearPad(helper, 270, 16, 16);
            T3RoleGameTests.finish(helper, problems);
        });
    }

    /**
     * AC-7: the numbers keep the T3 rules: its own HP total (850 is reserved for it), the same price as the Iron Leviathan
     * Engine, below the anchor's damage, the slowest flyer, no flak at buildings, fire x1.3, one of each ability.
     */
    @GameTest(template = ARENA, timeoutTicks = 40)
    public static void brass_zeppelin_numbers_keep_the_t3_rules(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        float[] others = { SunColossusUnit.maxHealth,
            com.solegendary.reignofnether.unit.units.monsters.BoneDragonUnit.maxHealth,
            com.solegendary.reignofnether.unit.units.monsters.PlagueAbominationUnit.maxHealth,
            com.solegendary.reignofnether.unit.units.piglins.WarMammothUnit.maxHealth,
            com.solegendary.reignofnether.unit.units.piglins.MagmaHurlerUnit.maxHealth,
            com.solegendary.reignofnether.unit.units.villagers.CathedralWardenUnit.maxHealth,
            com.solegendary.reignofnether.unit.units.verdant.WorldTreeWalkerUnit.maxHealth,
            com.solegendary.reignofnether.unit.units.verdant.ThornwingRocUnit.maxHealth,
            com.solegendary.reignofnether.unit.units.tide.LeviathanUnit.maxHealth,
            com.solegendary.reignofnether.unit.units.tide.AbyssalHydraUnit.maxHealth,
            com.solegendary.reignofnether.unit.units.storm.CelestialDragonUnit.maxHealth,
            com.solegendary.reignofnether.unit.units.storm.JadeSentinelUnit.maxHealth,
            IronLeviathanEngineUnit.maxHealth };
        for (float hp : others)
            if (hp == BrassZeppelinUnit.maxHealth)
                problems.add("the Zeppelin shares " + hp + " HP with another T3");
        if (BrassZeppelinUnit.maxHealth != 850f)
            problems.add("the design's HP is 850: " + BrassZeppelinUnit.maxHealth);
        var first = ProductionItems.IRON_LEVIATHAN_ENGINE.getCost(false, "nobody");
        var second = ProductionItems.BRASS_T3B.getCost(false, "nobody");
        if (first.metal() != second.metal() || first.energy() != second.energy() || first.population != second.population)
            problems.add("the Zeppelin's price differs from the Iron Leviathan Engine's");
        if (BrassZeppelinUnit.attackDamage * BrassZeppelinUnit.attacksPerSecond
                >= SunColossusUnit.attackDamage * SunColossusUnit.attacksPerSecond)
            problems.add("a flyer should not out-damage the anchor");
        for (float v : new float[] { com.solegendary.reignofnether.unit.units.verdant.ThornwingRocUnit.movementSpeed,
                com.solegendary.reignofnether.unit.units.storm.CelestialDragonUnit.movementSpeed,
                com.solegendary.reignofnether.unit.units.monsters.BoneDragonUnit.movementSpeed })
            if (BrassZeppelinUnit.movementSpeed >= v)
                problems.add("the Zeppelin is not slower than another T3 flyer (" + v + ")");
        if (BrassZeppelinUnit.movementSpeed < IronLeviathanEngineUnit.movementSpeed)
            problems.add("the Zeppelin should not be slower than the Engine that walks beside it");
        BrassZeppelinUnit u = EntityRegistrar.BRASS_ZEPPELIN_UNIT.get().create(helper.getLevel());
        if (u == null)
            problems.add("could not create the Zeppelin");
        else {
            if (((Unit) u).getCost() != com.solegendary.reignofnether.resources.ResourceCosts.BRASS_T3B)
                problems.add("the Zeppelin's cost entry is not BRASS_T3B");
            if (u.getAttributeValue(Attributes.MAX_HEALTH) != 850)
                problems.add("the Zeppelin's attribute HP is " + u.getAttributeValue(Attributes.MAX_HEALTH));
            u.updateAbilityButtons();   // a refresh must not duplicate the abilities
            var list = u.getAbilities().get();
            if (list.stream().filter(a -> a instanceof CarpetBomb).count() != 1 || list.stream().filter(a -> a instanceof BallastDump).count() != 1)
                problems.add("the Zeppelin should carry exactly one Carpet Bomb and one Ballast Dump");
            u.setHealth(u.getMaxHealth());
            u.hurt(helper.getLevel().damageSources().onFire(), 10f);
            float lost = u.getMaxHealth() - u.getHealth();
            if (Math.abs(lost - 10f * BrassZeppelinUnit.FIRE_MULTIPLIER) > 0.01f)
                problems.add("10 fire damage cost " + lost + " HP, expected " + 10f * BrassZeppelinUnit.FIRE_MULTIPLIER);
            u.discard();
        }
        T3RoleGameTests.finish(helper, problems);
    }
}
