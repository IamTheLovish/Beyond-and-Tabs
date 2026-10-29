package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.ability.Ability;
import com.solegendary.reignofnether.ability.abilities.DragonDive;
import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.units.monsters.BoneDragonUnit;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * The Bone Dragon's Dive (#145, lovish: "an option to let it descend to the ground to fight ... like in BAR ... a
 * cooldown on this that's long to punish players that dive in and prevents people from abusing the dive mechanic").
 * Pinned to lovish's numbers (Oct 28, "OK as proposed"): cooldown 90 s from the dive, 60 damage within 4.5 blocks with
 * knockback 0.8 to ground enemies only, 10 s on the ground (a ground unit), early take-off after 4 s, 16-block range.
 * The cooldown and the dive / landed state are the server's and survive the dragon being saved and loaded again (an
 * unloaded chunk, a restart) - else a reload would hand back a fresh dive at once (#300 review round 1).
 *
 * Every test schedules ALL its tick callbacks from its own body ({@link #everyTick}) and runs as a little state machine
 * on the tick number: vanilla's GameTestInfo iterates its tick-time map while it runs them and removes as it goes, so a
 * runAfterDelay from inside a callback corrupts the iteration (fastutil's "wrapped is null" - CI 40630152099 crashed
 * the whole shard-1 server that way).
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class DragonDiveGameTests {

    static final String ARENA = SkirmishGameTests.ARENA;

    /** One step per tick, 1..ticks, all scheduled now; step returns true once the test is over (no more steps). */
    interface Step {
        boolean at(int tick);
    }

    private static void everyTick(GameTestHelper helper, int ticks, Step step) {
        final boolean[] over = { false };
        for (int t = 1; t <= ticks; t++) {
            final int tick = t;
            helper.runAfterDelay(t, () -> {
                if (!over[0])
                    over[0] = step.at(tick);
            });
        }
    }

    /** Ends the test (succeed when no problems, else fail with them) and says the steps are over. */
    private static boolean finish(GameTestHelper helper, List<String> problems, Runnable clean) {
        if (clean != null)
            clean.run();
        if (problems.isEmpty())
            helper.succeed();
        else
            helper.fail(String.join("; ", problems));
        return true;
    }

    /** A 16x16 glass floor high over the arena: open sky to dive through. Returns the standing position's corner. */
    private static BlockPos skyPad(GameTestHelper helper, int y) {
        BlockPos corner = helper.absolutePos(BlockPos.ZERO);
        // put back when the test ends, pass or fail: a pad left over another test's column is in its sky
        TestCleanup.BlockUndo pad = TestCleanup.blocks(helper);
        for (int dx = 0; dx < 16; dx++)
            for (int dz = 0; dz < 16; dz++)
                pad.set(new BlockPos(corner.getX() + dx, y, corner.getZ() + dz), Blocks.GLASS.defaultBlockState());
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

    private static BoneDragonUnit dragonAt(ServerLevel level, BlockPos pad, String owner, double x, double y, double z) {
        return (BoneDragonUnit) put(level, pad, EntityRegistrar.BONE_DRAGON_UNIT.get(), owner, x, y, z);
    }

    /** A worker that never fights (so every hurt is the dragon's) with a lot of health (so the blow can be measured). */
    private static Mob sturdyWorker(ServerLevel level, BlockPos pad, String owner, double x, double z) {
        Mob m = put(level, pad, EntityRegistrar.VILLAGER_UNIT.get(), owner, x, 0, z);
        if (m != null) {
            AttributeInstance hp = m.getAttribute(Attributes.MAX_HEALTH);
            if (hp != null)
                hp.setBaseValue(500);
            m.setHealth(500);
        }
        return m;
    }

    private static DragonDive dive(BoneDragonUnit dragon) {
        if (dragon == null)
            return null;
        for (Ability a : dragon.getAbilities().get())
            if (a instanceof DragonDive d)
                return d;
        return null;
    }

    /** The saved dragon, loaded back as a new entity in its place (an unloaded chunk or a restart, in one tick). */
    private static BoneDragonUnit reload(ServerLevel level, BoneDragonUnit dragon) {
        CompoundTag tag = dragon.saveWithoutId(new CompoundTag());
        dragon.discard();
        BoneDragonUnit copy = EntityRegistrar.BONE_DRAGON_UNIT.get().create(level);
        if (copy == null)
            return null;
        copy.load(tag);
        level.addFreshEntity(copy);
        return copy;
    }

    @GameTest(template = ARENA, timeoutTicks = 20)
    public static void bone_dragon_dive_numbers_are_lovishs(GameTestHelper helper) {
        List<String> p = new ArrayList<>();
        if (BoneDragonUnit.DIVE_CD_SECONDS != 90) p.add("cooldown " + BoneDragonUnit.DIVE_CD_SECONDS + " s, lovish set 90");
        if (BoneDragonUnit.IMPACT_DAMAGE != 60f) p.add("impact " + BoneDragonUnit.IMPACT_DAMAGE + ", lovish set 60");
        if (BoneDragonUnit.IMPACT_RADIUS != 4.5f) p.add("radius " + BoneDragonUnit.IMPACT_RADIUS + ", lovish set 4.5");
        if (BoneDragonUnit.IMPACT_KNOCKBACK != 0.8) p.add("knockback " + BoneDragonUnit.IMPACT_KNOCKBACK + ", lovish set 0.8");
        if (BoneDragonUnit.LAND_TICKS != 200) p.add("down " + BoneDragonUnit.LAND_TICKS + " ticks, lovish set 10 s");
        if (BoneDragonUnit.MIN_LAND_TICKS != 80) p.add("early take-off after " + BoneDragonUnit.MIN_LAND_TICKS + " ticks, lovish set 4 s");
        if (BoneDragonUnit.DIVE_RANGE != 16) p.add("range " + BoneDragonUnit.DIVE_RANGE + ", lovish set 16");
        if (DragonDive.CD_SECONDS != BoneDragonUnit.DIVE_CD_SECONDS) p.add("the button's cooldown is not the dragon's");
        finish(helper, p, null);
    }

    /**
     * The impact, judged on the landing tick alone (the dragon also breathes on enemies in reach on its own): both
     * foes by the spot lose the full 60 (workers have no armour) and are thrown away from it; a friend and an ALLY by
     * the spot and a foe outside the radius are untouched. Every unit by the spot stands 1.5 blocks from it, so a
     * landing within 2 blocks of the spot keeps them all inside the radius.
     */
    @GameTest(template = ARENA, timeoutTicks = 140)
    public static void bone_dragon_dive_slams_ground_foes_not_friends_and_lands(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pad = skyPad(helper, 290);
        String owner = "gametest_dive_" + helper.absolutePos(BlockPos.ZERO).asLong(), foe = owner + "_foe", ally = owner + "_ally";
        AlliancesServerEvents.addAlliance(owner, ally);
        BoneDragonUnit dragon = dragonAt(level, pad, owner, 3.5, 9, 9.0);
        Mob inA = sturdyWorker(level, pad, foe, 13.5, 9.0), inB = sturdyWorker(level, pad, foe, 12.0, 10.5);
        Mob friend = sturdyWorker(level, pad, owner, 10.5, 9.0), allied = sturdyWorker(level, pad, ally, 12.0, 7.5);
        Mob far = sturdyWorker(level, pad, foe, 1.5, 1.5);
        List<Mob> all = new ArrayList<>();
        for (Mob m : new Mob[] { dragon, inA, inB, friend, allied, far })
            if (m != null)
                all.add(m);
        Runnable clean = () -> {
            all.forEach(Mob::discard);
            AlliancesServerEvents.removeAlliance(owner, ally);
        };
        if (all.size() != 6) {
            clean.run();
            helper.fail("could not create the dive test units");
            return;
        }
        Mob[] watched = { inA, inB, friend, allied, far };
        final float[] before = new float[5];
        final Vec3 spot = new Vec3(pad.getX() + 12.0, pad.getY(), pad.getZ() + 9.0);
        everyTick(helper, 130, tick -> {
            List<String> problems = new ArrayList<>();
            if (tick == 3) {
                if (!dragon.isFlyingUnit())
                    problems.add("the dragon should start in the air (a flyer)");
                if (!dragon.startDive(spot))
                    problems.add("the dive was refused from the air");
                if (!problems.isEmpty())
                    return finish(helper, problems, clean);
            }
            if (!dragon.isLanded()) {
                for (int i = 0; i < 5; i++)
                    before[i] = watched[i].getHealth();
                if (tick >= 125) {
                    problems.add("the dragon never landed (diving: " + dragon.isDiving() + ")");
                    return finish(helper, problems, clean);
                }
                return false;
            }
            // the landing tick
            if (tick > 3 + 80)
                problems.add("the dive took " + (tick - 3) + " ticks (want under 4 s)");
            double off = Math.sqrt(Math.pow(dragon.getX() - spot.x, 2) + Math.pow(dragon.getZ() - spot.z, 2));
            if (off > 2.0)
                problems.add(String.format("it landed %.1f blocks from its spot", off));
            if (dragon.isFlyingUnit())
                problems.add("a landed dragon is a ground unit (melee reaches it)");
            for (int i = 0; i < 2; i++) {
                Mob f = watched[i];
                float lost = before[i] - f.getHealth();
                if (lost < BoneDragonUnit.IMPACT_DAMAGE - 0.5f)
                    problems.add(String.format("foe %d by the spot lost %.1f, want %.0f", i, lost, BoneDragonUnit.IMPACT_DAMAGE));
                Vec3 away = new Vec3(f.getX() - dragon.getX(), 0, f.getZ() - dragon.getZ()).normalize();
                Vec3 v = f.getDeltaMovement();
                double out = v.x * away.x + v.z * away.z;
                if (out < 0.3)
                    problems.add(String.format("foe %d was not thrown back (%.2f b/t away from the dragon)", i, out));
            }
            if (watched[2].getHealth() < before[2])
                problems.add("the impact hurt a friend");
            if (watched[3].getHealth() < before[3])
                problems.add("the impact hurt an ally");
            if (watched[4].getHealth() < before[4])
                problems.add("the impact reached a foe outside its radius");
            return finish(helper, problems, clean);
        });
    }

    /** Down exactly LAND_TICKS: still landed just before, flying again just after, and climbing. */
    @GameTest(template = ARENA, timeoutTicks = 70 + BoneDragonUnit.LAND_TICKS + 100)
    public static void bone_dragon_flies_again_by_itself_after_the_landing(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pad = skyPad(helper, 290);
        BoneDragonUnit dragon = dragonAt(level, pad, "gametest_dive_up", 8.5, 6, 8.5);
        if (dragon == null) {
            helper.fail("could not create the dragon");
            return;
        }
        final int[] landedAt = { -1 };
        List<String> problems = new ArrayList<>();
        everyTick(helper, 60 + BoneDragonUnit.LAND_TICKS + 90, tick -> {
            if (tick == 3)
                dragon.startDive(new Vec3(pad.getX() + 8.5, pad.getY(), pad.getZ() + 8.5));
            if (landedAt[0] < 0) {
                if (dragon.isLanded())
                    landedAt[0] = tick;
                else if (tick >= 60) {
                    problems.add("the dragon never landed");
                    return finish(helper, problems, dragon::discard);
                }
                return false;
            }
            int down = tick - landedAt[0];
            if (down == BoneDragonUnit.LAND_TICKS - 3 && !dragon.isLanded())
                problems.add("up again before " + BoneDragonUnit.LAND_TICKS / 20 + " s");
            if (down == BoneDragonUnit.LAND_TICKS + 3 && (dragon.isLanded() || !dragon.isFlyingUnit()))
                problems.add("still down after " + BoneDragonUnit.LAND_TICKS / 20 + " s");
            if (down == BoneDragonUnit.LAND_TICKS + 80) {
                if (dragon.getY() < pad.getY() + 3)
                    problems.add(String.format("it took off but did not climb: %.1f above the pad", dragon.getY() - pad.getY()));
                return finish(helper, problems, dragon::discard);
            }
            return false;
        });
    }

    /**
     * The button: a 90 s cooldown from the dive; on the ground it is greyed for the first 4 s and refuses to take off;
     * after that it takes off (the cooldown still running); back in the air, a second dive is refused on cooldown.
     */
    @GameTest(template = ARENA, timeoutTicks = 70 + BoneDragonUnit.MIN_LAND_TICKS + 100)
    public static void bone_dragon_dive_is_on_a_long_cooldown_and_takes_off_again(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pad = skyPad(helper, 290);
        BoneDragonUnit dragon = dragonAt(level, pad, "gametest_dive_cd_" + helper.absolutePos(BlockPos.ZERO).asLong(), 8.5, 6, 8.5);
        DragonDive d = dive(dragon);
        if (dragon == null || d == null) {
            if (dragon != null)
                dragon.discard();
            helper.fail("could not create the dragon and its Dive");
            return;
        }
        List<String> problems = new ArrayList<>();
        BlockPos ground = new BlockPos(pad.getX() + 8, pad.getY() - 1, pad.getZ() + 8);
        final int[] landedAt = { -1 };
        everyTick(helper, 60 + BoneDragonUnit.MIN_LAND_TICKS + 95, tick -> {
            if (tick == 3) {
                if (d.cooldownMax != BoneDragonUnit.DIVE_CD_SECONDS * 20)
                    problems.add("the Dive's cooldown should be " + BoneDragonUnit.DIVE_CD_SECONDS + " s, is " + d.cooldownMax / 20 + " s");
                d.use(level, dragon, ground);
                if (d.isOffCooldown(dragon))
                    problems.add("the cooldown should start at the dive");
                if (!dragon.isDiving())
                    problems.add("the Dive did not start the dive");
                return false;
            }
            if (landedAt[0] < 0) {
                if (dragon.isLanded()) {
                    landedAt[0] = tick;
                    if (d.canBypassCooldown(dragon))
                        problems.add("the button should stay greyed for the first " + BoneDragonUnit.MIN_LAND_TICKS / 20 + " s on the ground");
                    d.use(level, dragon, dragon.blockPosition());
                } else if (tick >= 60) {
                    problems.add("the dragon never landed");
                    return finish(helper, problems, dragon::discard);
                }
                return false;
            }
            int down = tick - landedAt[0];
            if (down == 2 && !dragon.isLanded())
                problems.add("it took off right after landing (before " + BoneDragonUnit.MIN_LAND_TICKS / 20 + " s)");
            if (down == BoneDragonUnit.MIN_LAND_TICKS + 5) {
                if (d.isOffCooldown(dragon))
                    problems.add("the cooldown should still be running on the ground");
                if (!d.canBypassCooldown(dragon))
                    problems.add("on the ground the button should take off even on cooldown");
                d.use(level, dragon, dragon.blockPosition());
            }
            if (down == BoneDragonUnit.MIN_LAND_TICKS + 5 + 80) {
                if (dragon.isLanded() || !dragon.isFlyingUnit())
                    problems.add("pressed again after " + BoneDragonUnit.MIN_LAND_TICKS / 20 + " s, it should fly again");
                else if (dragon.getY() < pad.getY() + 3)
                    problems.add(String.format("it should climb back up, is %.1f above the pad", dragon.getY() - pad.getY()));
                if (d.isOffCooldown(dragon))
                    problems.add("the cooldown ran out within " + (BoneDragonUnit.MIN_LAND_TICKS + 85) / 20 + " s");
                d.use(level, dragon, ground);        // the server's own check, as a click would reach it
                if (dragon.isDiving() || dragon.isLanded())
                    problems.add("a second dive went off on cooldown");
                return finish(helper, problems, dragon::discard);
            }
            return false;
        });
    }

    /** Aimed 30 blocks away, it dives to the point 16 blocks toward the aim (never farther) - still over the pad. */
    @GameTest(template = ARENA, timeoutTicks = 20)
    public static void bone_dragon_dive_reaches_sixteen_blocks_at_most(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pad = skyPad(helper, 290);
        BoneDragonUnit dragon = dragonAt(level, pad, "gametest_dive_far", 0.5, 6, 0.5);
        if (dragon == null) {
            helper.fail("could not create the dragon");
            return;
        }
        everyTick(helper, 10, tick -> {
            if (tick < 3)
                return false;
            List<String> problems = new ArrayList<>();
            Vec3 from = dragon.position();
            if (!dragon.startDive(from.add(21.2, -6, 21.2))) {     // 30 blocks off along the pad's diagonal
                problems.add("the dive was refused");
            } else {
                Vec3 to = dragon.diveTarget();
                double flat = Math.sqrt((to.x - from.x) * (to.x - from.x) + (to.z - from.z) * (to.z - from.z));
                if (Math.abs(flat - BoneDragonUnit.DIVE_RANGE) > 0.05)
                    problems.add(String.format("aimed 30 blocks off, it dives %.2f blocks (want %d)", flat, BoneDragonUnit.DIVE_RANGE));
            }
            return finish(helper, problems, dragon::discard);
        });
    }

    /**
     * Under a roof (a cave, an overhang) it dives to the ground below it, not to the roof's height - r1 took
     * min(roof, its own height) and "landed" in mid-air on the next tick, slam and all.
     */
    @GameTest(template = ARENA, timeoutTicks = 120)
    public static void bone_dragon_dive_under_a_roof_lands_on_the_ground(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pad = skyPad(helper, 290);
        BlockPos corner = helper.absolutePos(BlockPos.ZERO);
        List<BlockPos> roof = new ArrayList<>();
        for (int dx = 0; dx < 16; dx++)
            for (int dz = 0; dz < 16; dz++)
                roof.add(new BlockPos(corner.getX() + dx, pad.getY() + 8, corner.getZ() + dz));
        for (BlockPos b : roof)
            level.setBlock(b, Blocks.GLASS.defaultBlockState(), 3);
        BoneDragonUnit dragon = dragonAt(level, pad, "gametest_dive_roof", 8.5, 5, 8.5);
        Runnable clean = () -> {
            if (dragon != null)
                dragon.discard();
            for (BlockPos b : roof)
                level.setBlock(b, Blocks.AIR.defaultBlockState(), 3);
        };
        if (dragon == null) {
            clean.run();
            helper.fail("could not create the dragon");
            return;
        }
        everyTick(helper, 110, tick -> {
            List<String> problems = new ArrayList<>();
            if (tick == 3 && !dragon.startDive(new Vec3(pad.getX() + 10.5, pad.getY(), pad.getZ() + 8.5))) {
                problems.add("the dive was refused under the roof");
                return finish(helper, problems, clean);
            }
            if (dragon.isLanded()) {
                double up = dragon.getY() - pad.getY();
                if (up > 1.0)
                    problems.add(String.format("it landed %.1f blocks above the floor (under a roof)", up));
                return finish(helper, problems, clean);
            }
            if (tick >= 105) {
                problems.add("the dragon never landed");
                return finish(helper, problems, clean);
            }
            return false;
        });
    }

    /**
     * A spot inside a cliff that rises past the dragon's own height is refused (no dive, no cooldown) - r2 scanned
     * down from the dragon's block, so a cliff beside it gave a ledge above it as the "ground" (#300 review round 2).
     */
    @GameTest(template = ARENA, timeoutTicks = 20)
    public static void bone_dragon_dive_refuses_a_spot_inside_a_cliff(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pad = skyPad(helper, 290);
        List<BlockPos> cliff = new ArrayList<>();
        for (int y = 0; y <= 9; y++)
            cliff.add(new BlockPos(pad.getX() + 12, pad.getY() + y, pad.getZ() + 8));
        for (BlockPos b : cliff)
            level.setBlock(b, Blocks.STONE.defaultBlockState(), 3);
        BoneDragonUnit dragon = dragonAt(level, pad, "gametest_dive_cliff", 4.5, 5, 8.5);
        Runnable clean = () -> {
            if (dragon != null)
                dragon.discard();
            for (BlockPos b : cliff)
                level.setBlock(b, Blocks.AIR.defaultBlockState(), 3);
        };
        everyTick(helper, 10, tick -> {
            if (tick < 3)
                return false;
            List<String> problems = new ArrayList<>();
            if (dragon == null)
                problems.add("could not create the dragon");
            else if (dragon.startDive(new Vec3(pad.getX() + 12.5, pad.getY(), pad.getZ() + 8.5)) || dragon.isDiving())
                problems.add("it took a spot inside a cliff higher than itself (dive to y "
                    + (dragon.diveTarget() == null ? "?" : String.format("%.0f", dragon.diveTarget().y - pad.getY())) + " over the pad)");
            return finish(helper, problems, clean);
        });
    }

    /** Water or lava at the spot is no ground to land on: refused (r2 scanned through them to the bed). */
    @GameTest(template = ARENA, timeoutTicks = 20)
    public static void bone_dragon_dive_refuses_water_and_lava(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pad = skyPad(helper, 290);
        BlockPos wet = new BlockPos(pad.getX() + 10, pad.getY(), pad.getZ() + 4), hot = new BlockPos(pad.getX() + 10, pad.getY(), pad.getZ() + 12);
        BoneDragonUnit dragon = dragonAt(level, pad, "gametest_dive_wet", 4.5, 5, 8.5);
        Runnable clean = () -> {
            if (dragon != null)
                dragon.discard();
            level.setBlock(wet, Blocks.AIR.defaultBlockState(), 3);
            level.setBlock(hot, Blocks.AIR.defaultBlockState(), 3);
        };
        everyTick(helper, 10, tick -> {
            if (tick == 2) {
                level.setBlock(wet, Blocks.WATER.defaultBlockState(), 2);
                level.setBlock(hot, Blocks.LAVA.defaultBlockState(), 2);
            }
            if (tick < 3)
                return false;
            List<String> problems = new ArrayList<>();
            if (dragon == null) {
                problems.add("could not create the dragon");
            } else {
                if (dragon.startDive(Vec3.atBottomCenterOf(wet)))
                    problems.add("it dived into water");
                if (!dragon.isDiving() && dragon.startDive(Vec3.atBottomCenterOf(hot)))
                    problems.add("it dived into lava");
            }
            return finish(helper, problems, clean);
        });
    }

    /** Saved and loaded again while down: still down for the rest of its 10 s, the cooldown still running. */
    @GameTest(template = ARENA, timeoutTicks = 70 + BoneDragonUnit.LAND_TICKS + 30)
    public static void bone_dragon_dive_state_and_cooldown_survive_a_reload(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pad = skyPad(helper, 290);
        final BoneDragonUnit[] dragon = { dragonAt(level, pad, "gametest_dive_reload", 8.5, 6, 8.5) };
        if (dragon[0] == null) {
            helper.fail("could not create the dragon");
            return;
        }
        BlockPos ground = new BlockPos(pad.getX() + 8, pad.getY() - 1, pad.getZ() + 8);
        List<String> problems = new ArrayList<>();
        final int[] landedAt = { -1 };
        final float[] cdBefore = { 0 };
        Runnable clean = () -> {
            if (dragon[0] != null)
                dragon[0].discard();
        };
        everyTick(helper, 60 + BoneDragonUnit.LAND_TICKS + 20, tick -> {
            BoneDragonUnit dr = dragon[0];
            if (tick == 3) {
                DragonDive d0 = dive(dr);
                if (d0 == null) {
                    problems.add("the dragon has no Dive");
                    return finish(helper, problems, clean);
                }
                d0.use(level, dr, ground);
                return false;
            }
            if (landedAt[0] < 0) {
                if (dr.isLanded())
                    landedAt[0] = tick;
                else if (tick >= 60) {
                    problems.add("the dragon never landed");
                    return finish(helper, problems, clean);
                }
                return false;
            }
            int down = tick - landedAt[0];
            if (down == 20) {
                DragonDive d0 = dive(dr);
                cdBefore[0] = d0 == null ? 0 : d0.getCooldown(dr);
                BoneDragonUnit copy = reload(level, dr);
                dragon[0] = copy;
                if (copy == null) {
                    problems.add("could not load the saved dragon");
                    return finish(helper, problems, clean);
                }
                DragonDive d = dive(copy);
                if (!copy.isLanded() || copy.isFlyingUnit())
                    problems.add("loaded mid-landing, it should still be down (a ground unit)");
                if (d == null || d.isOffCooldown(copy) || Math.abs(d.getCooldown(copy) - cdBefore[0]) > 5)
                    problems.add(String.format("loaded mid-cooldown, the Dive should have its %.0f ticks left, has %s",
                        cdBefore[0], d == null ? "no Dive" : String.format("%.0f", d.getCooldown(copy))));
                if (d != null) {
                    d.use(level, copy, ground);
                    if (!copy.isLanded())
                        problems.add("loaded, the button took it off before " + BoneDragonUnit.MIN_LAND_TICKS / 20 + " s down");
                }
                return false;
            }
            if (down == BoneDragonUnit.LAND_TICKS - 5 && !dr.isLanded())
                problems.add("the reload cut the time on the ground short");
            if (down == BoneDragonUnit.LAND_TICKS + 5) {
                if (dr.isLanded())
                    problems.add("the reload kept it down past its 10 s");
                DragonDive dd = dive(dr);
                if (dd == null || dd.isOffCooldown(dr))
                    problems.add("the cooldown ran out early after the reload");
                return finish(helper, problems, clean);
            }
            return false;
        });
    }

    /** Saved and loaded again mid-dive: it carries on down to its spot and lands there. */
    @GameTest(template = ARENA, timeoutTicks = 120)
    public static void bone_dragon_dive_in_flight_survives_a_reload(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pad = skyPad(helper, 290);
        final BoneDragonUnit[] dragon = { dragonAt(level, pad, "gametest_dive_reload2", 2.5, 10, 2.5) };
        if (dragon[0] == null) {
            helper.fail("could not create the dragon");
            return;
        }
        Vec3 spot = new Vec3(pad.getX() + 12.5, pad.getY(), pad.getZ() + 12.5);
        List<String> problems = new ArrayList<>();
        Runnable clean = () -> {
            if (dragon[0] != null)
                dragon[0].discard();
        };
        everyTick(helper, 110, tick -> {
            BoneDragonUnit dr = dragon[0];
            if (tick == 3) {
                dr.startDive(spot);
                return false;
            }
            if (tick == 6) {
                if (!dr.isDiving()) {
                    problems.add("the dive did not start");
                    return finish(helper, problems, clean);
                }
                BoneDragonUnit copy = reload(level, dr);
                dragon[0] = copy;
                if (copy == null || !copy.isDiving()) {
                    problems.add(copy == null ? "could not load the saved dragon" : "loaded mid-dive, it should still be diving");
                    return finish(helper, problems, clean);
                }
                return false;
            }
            if (tick > 6 && dr.isLanded()) {
                double off = Math.sqrt(Math.pow(dr.getX() - spot.x, 2) + Math.pow(dr.getZ() - spot.z, 2));
                if (off > 2.5)
                    problems.add(String.format("after the reload it landed %.1f blocks from its spot", off));
                return finish(helper, problems, clean);
            }
            if (tick >= 105) {
                problems.add(String.format("after the reload it never landed (diving %s, %.1f above the pad, %.1f from its spot, "
                    + "alive %s, in the level %s)", dr.isDiving(), dr.getY() - pad.getY(),
                    Math.sqrt(Math.pow(dr.getX() - spot.x, 2) + Math.pow(dr.getZ() - spot.z, 2)), dr.isAlive(),
                    level.getEntity(dr.getId()) == dr));
                return finish(helper, problems, clean);
            }
            return false;
        });
    }
}
