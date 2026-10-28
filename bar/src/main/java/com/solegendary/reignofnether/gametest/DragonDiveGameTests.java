package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.ability.Ability;
import com.solegendary.reignofnether.ability.abilities.DragonDive;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.units.monsters.BoneDragonUnit;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * The Bone Dragon's Dive (#145, lovish: "an option to let it descend to the ground to fight ... like in BAR ... a
 * cooldown on this that's long to punish players that dive in"): it stays in the air by default; the Dive stoops it
 * onto a spot, the impact hits the ground enemies round it (never a friend, never one out of the radius), it stays
 * down for {@link BoneDragonUnit#LAND_TICKS} as a ground unit melee can reach, then flies again; the cooldown is long
 * and starts at the dive; pressing the button again on the ground takes off early, but not before
 * {@link BoneDragonUnit#MIN_LAND_TICKS}.
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class DragonDiveGameTests {

    static final String ARENA = SkirmishGameTests.ARENA;

    /** A 16x16 glass floor high over the arena: open sky to dive through. Returns the standing position's corner. */
    private static BlockPos skyPad(GameTestHelper helper, int y) {
        BlockPos corner = helper.absolutePos(BlockPos.ZERO);
        for (int dx = 0; dx < 16; dx++)
            for (int dz = 0; dz < 16; dz++)
                helper.getLevel().setBlock(new BlockPos(corner.getX() + dx, y, corner.getZ() + dz), Blocks.GLASS.defaultBlockState(), 3);
        return new BlockPos(corner.getX(), y + 1, corner.getZ());
    }

    private static Mob put(ServerLevel level, BlockPos pad, net.minecraft.world.entity.EntityType<? extends Mob> type,
                           String owner, double x, double y, double z) {
        Mob m = type.create(level);
        if (m == null)
            return null;
        m.moveTo(pad.getX() + x, pad.getY() + y, pad.getZ() + z, 0, 0);
        if (m instanceof Unit u)
            u.setOwnerName(owner);
        level.addFreshEntity(m);
        return m;
    }

    private static DragonDive dive(BoneDragonUnit dragon) {
        for (Ability a : dragon.getAbilities().get())
            if (a instanceof DragonDive d)
                return d;
        return null;
    }

    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void bone_dragon_dive_slams_ground_foes_not_friends_and_lands(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pad = skyPad(helper, 290);
        String owner = "gametest_dive_" + helper.absolutePos(BlockPos.ZERO).asLong(), foe = owner + "_foe";
        var vind = EntityRegistrar.VINDICATOR_UNIT.get();
        var dragon = (BoneDragonUnit) put(level, pad, EntityRegistrar.BONE_DRAGON_UNIT.get(), owner, 3.5, 9, 8.5);
        Mob inA = put(level, pad, vind, foe, 11.5, 0, 8.5), inB = put(level, pad, vind, foe, 10.0, 0, 10.5);
        Mob friend = put(level, pad, vind, owner, 12.0, 0, 7.0), far = put(level, pad, vind, foe, 1.5, 0, 1.5);
        List<Mob> all = new ArrayList<>();
        for (Mob m : new Mob[] { dragon, inA, inB, friend, far })
            if (m != null)
                all.add(m);
        if (all.size() != 5) {
            all.forEach(Mob::discard);
            helper.fail("could not create the dive test units");
            return;
        }
        float a0 = inA.getHealth(), b0 = inB.getHealth(), f0 = friend.getHealth(), r0 = far.getHealth();
        final int[] landedAt = { -1 };
        helper.runAfterDelay(3, () -> {
            if (!dragon.isFlyingUnit())
                helper.fail("the dragon should start in the air (a flyer)");
            if (!dragon.startDive(new Vec3(pad.getX() + 11.5, pad.getY(), pad.getZ() + 9.0)))
                helper.fail("the dive was refused from the air");
        });
        for (int t = 4; t <= 120; t++) {
            final int tick = t;
            helper.runAfterDelay(t, () -> {
                if (landedAt[0] < 0 && dragon.isLanded())
                    landedAt[0] = tick;
            });
        }
        helper.runAfterDelay(125, () -> {
            try {
                List<String> problems = new ArrayList<>();
                if (landedAt[0] < 0)
                    problems.add("the dragon never landed (y " + String.format("%.1f", dragon.getY() - pad.getY()) + " above the pad)");
                else if (landedAt[0] > 3 + 80)
                    problems.add("the dive took " + (landedAt[0] - 3) + " ticks (want under 4 s)");
                if (dragon.isFlyingUnit())
                    problems.add("a landed dragon is a ground unit (melee reaches it)");
                float want = BoneDragonUnit.IMPACT_DAMAGE * 0.9f;
                if (inA.isAlive() && a0 - inA.getHealth() < want || inB.isAlive() && b0 - inB.getHealth() < want)
                    problems.add(String.format("the impact should hit both foes by it for ~%.0f (took %.0f, %.0f)",
                        BoneDragonUnit.IMPACT_DAMAGE, a0 - inA.getHealth(), b0 - inB.getHealth()));
                if (friend.getHealth() < f0)
                    problems.add("the impact hurt a friend");
                if (far.getHealth() < r0)
                    problems.add("the impact reached a foe outside its radius");
                if (problems.isEmpty())
                    helper.succeed();
                else
                    helper.fail(String.join("; ", problems));
            } finally {
                all.forEach(Mob::discard);
            }
        });
    }

    @GameTest(template = ARENA, timeoutTicks = 60 + BoneDragonUnit.LAND_TICKS + 160)
    public static void bone_dragon_dive_is_on_a_long_cooldown_and_takes_off_again(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pad = skyPad(helper, 290);
        String owner = "gametest_dive_cd_" + helper.absolutePos(BlockPos.ZERO).asLong();
        var dragon = (BoneDragonUnit) put(level, pad, EntityRegistrar.BONE_DRAGON_UNIT.get(), owner, 8.5, 6, 8.5);
        if (dragon == null) {
            helper.fail("could not create the dragon");
            return;
        }
        List<String> problems = new ArrayList<>();
        final int[] landedAt = { -1 };
        helper.runAfterDelay(3, () -> {
            DragonDive d = dive(dragon);
            if (d == null) {
                problems.add("the dragon has no Dive");
                return;
            }
            if (d.cooldownMax < 60 * 20)
                problems.add("the Dive's cooldown should be long (a minute or more), is " + d.cooldownMax / 20 + " s");
            d.use(level, dragon, new BlockPos(pad.getX() + 8, pad.getY() - 1, pad.getZ() + 8));
            if (d.isOffCooldown(dragon))
                problems.add("the cooldown should start at the dive");
            if (!dragon.isDiving())
                problems.add("the Dive did not start the dive");
        });
        for (int t = 4; t <= 60; t++) {
            final int tick = t;
            helper.runAfterDelay(t, () -> {
                if (landedAt[0] < 0 && dragon.isLanded()) {
                    landedAt[0] = tick;
                    // too early to take off: the punishment for diving in is a few seconds on the ground at least
                    DragonDive d = dive(dragon);
                    if (d != null) {
                        if (d.canBypassCooldown(dragon))
                            problems.add("the button should stay greyed for the first " + BoneDragonUnit.MIN_LAND_TICKS / 20 + " s on the ground");
                        d.use(level, dragon, dragon.blockPosition());
                    }
                    helper.runAfterDelay(2, () -> {
                        if (!dragon.isLanded())
                            problems.add("it took off right after landing (before " + BoneDragonUnit.MIN_LAND_TICKS / 20 + " s)");
                    });
                    // later the button takes it off early (the cooldown still running: the toggle bypasses it)
                    helper.runAfterDelay(BoneDragonUnit.MIN_LAND_TICKS + 5, () -> {
                        DragonDive dd = dive(dragon);
                        if (dd == null || dd.isOffCooldown(dragon))
                            problems.add("the cooldown should still be running on the ground");
                        if (dd != null && !dd.canBypassCooldown(dragon))
                            problems.add("on the ground the button should take off even on cooldown");
                        if (dd != null)
                            dd.use(level, dragon, dragon.blockPosition());
                    });
                    helper.runAfterDelay(BoneDragonUnit.MIN_LAND_TICKS + 5 + 80, () -> {
                        try {
                            if (dragon.isLanded() || !dragon.isFlyingUnit())
                                problems.add("pressed again after " + BoneDragonUnit.MIN_LAND_TICKS / 20 + " s, it should fly again");
                            else if (dragon.getY() < pad.getY() + 3)
                                problems.add(String.format("it should climb back up, is %.1f above the pad", dragon.getY() - pad.getY()));
                            if (problems.isEmpty())
                                helper.succeed();
                            else
                                helper.fail(String.join("; ", problems));
                        } finally {
                            dragon.discard();
                        }
                    });
                }
            });
        }
        helper.runAfterDelay(62, () -> {
            if (landedAt[0] < 0) {
                dragon.discard();
                helper.fail(problems.isEmpty() ? "the dragon never landed" : String.join("; ", problems));
            }
        });
    }

    @GameTest(template = ARENA, timeoutTicks = 60 + BoneDragonUnit.LAND_TICKS + 120)
    public static void bone_dragon_flies_again_by_itself_after_the_landing(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pad = skyPad(helper, 290);
        var dragon = (BoneDragonUnit) put(level, pad, EntityRegistrar.BONE_DRAGON_UNIT.get(), "gametest_dive_up", 8.5, 6, 8.5);
        if (dragon == null) {
            helper.fail("could not create the dragon");
            return;
        }
        helper.runAfterDelay(3, () -> dragon.startDive(new Vec3(pad.getX() + 8.5, pad.getY(), pad.getZ() + 8.5)));
        helper.runAfterDelay(60 + BoneDragonUnit.LAND_TICKS + 100, () -> {
            try {
                if (dragon.isLanded() || !dragon.isFlyingUnit())
                    helper.fail("still on the ground " + (BoneDragonUnit.LAND_TICKS / 20) + " s after the landing");
                else if (dragon.getY() < pad.getY() + 3)
                    helper.fail(String.format("it took off but did not climb: %.1f above the pad", dragon.getY() - pad.getY()));
                else
                    helper.succeed();
            } finally {
                dragon.discard();
            }
        });
    }
}
