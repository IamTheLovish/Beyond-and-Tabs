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
import java.util.function.Consumer;

/**
 * The Bone Dragon's Dive (#145, lovish: "an option to let it descend to the ground to fight ... like in BAR ... a
 * cooldown on this that's long to punish players that dive in and prevents people from abusing the dive mechanic").
 * Pinned to lovish's numbers (Oct 28, "OK as proposed"): cooldown 90 s from the dive, 60 damage within 4.5 blocks with
 * knockback 0.8 to ground enemies only, 10 s on the ground (a ground unit), early take-off after 4 s, 16-block range.
 * The cooldown and the dive / landed state are the server's and survive the dragon being saved and loaded again (an
 * unloaded chunk, a restart) - else a reload would hand back a fresh dive at once (#300 review round 1).
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
        for (Ability a : dragon.getAbilities().get())
            if (a instanceof DragonDive d)
                return d;
        return null;
    }

    /** Calls back on the first tick (from 1 to {@code until} ticks from now) the dragon is landed, with that tick. */
    private static void onLanding(GameTestHelper helper, BoneDragonUnit dragon, int until, Consumer<Integer> then) {
        final boolean[] seen = { false };
        for (int t = 1; t <= until; t++) {
            final int tick = t;
            helper.runAfterDelay(t, () -> {
                if (!seen[0] && dragon.isLanded()) {
                    seen[0] = true;
                    then.accept(tick);
                }
            });
        }
        helper.runAfterDelay(until + 1, () -> {
            if (!seen[0]) {
                dragon.discard();
                helper.fail(String.format("the dragon never landed (diving: %s)", dragon.isDiving()));
            }
        });
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
        if (p.isEmpty())
            helper.succeed();
        else
            helper.fail(String.join("; ", p));
    }

    /**
     * The impact, judged on the landing tick alone (the dragon also breathes on enemies in reach on its own): both
     * foes by the spot lose the full 60 (workers have no armour) and are thrown away from it; a friend and an ALLY by
     * the spot and a foe outside the radius are untouched. Every unit by the spot stands 1.5 blocks from it, so a
     * landing within 2 blocks of the spot keeps them all inside the radius.
     */
    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void bone_dragon_dive_slams_ground_foes_not_friends_and_lands(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pad = skyPad(helper, 290);
        String owner = "gametest_dive_" + helper.absolutePos(BlockPos.ZERO).asLong(), foe = owner + "_foe", ally = owner + "_ally";
        AlliancesServerEvents.addAlliance(owner, ally);
        var dragon = (BoneDragonUnit) put(level, pad, EntityRegistrar.BONE_DRAGON_UNIT.get(), owner, 3.5, 9, 9.0);
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
        for (int i = 0; i < 5; i++)
            before[i] = watched[i].getHealth();
        for (int t = 1; t <= 120; t++)
            helper.runAfterDelay(t, () -> {
                if (!dragon.isLanded())
                    for (int i = 0; i < 5; i++)
                        before[i] = watched[i].getHealth();
            });
        final Vec3 spot = new Vec3(pad.getX() + 12.0, pad.getY(), pad.getZ() + 9.0);
        helper.runAfterDelay(3, () -> {
            if (!dragon.isFlyingUnit())
                helper.fail("the dragon should start in the air (a flyer)");
            if (!dragon.startDive(spot))
                helper.fail("the dive was refused from the air");
            onLanding(helper, dragon, 100, landedAfter -> {
                List<String> problems = new ArrayList<>();
                try {
                    if (landedAfter > 80)
                        problems.add("the dive took " + landedAfter + " ticks (want under 4 s)");
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
                } finally {
                    clean.run();
                }
                if (problems.isEmpty())
                    helper.succeed();
                else
                    helper.fail(String.join("; ", problems));
            });
        });
    }

    /** Down exactly LAND_TICKS: still landed just before, flying again just after, and climbing. */
    @GameTest(template = ARENA, timeoutTicks = 60 + BoneDragonUnit.LAND_TICKS + 120)
    public static void bone_dragon_flies_again_by_itself_after_the_landing(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pad = skyPad(helper, 290);
        var dragon = (BoneDragonUnit) put(level, pad, EntityRegistrar.BONE_DRAGON_UNIT.get(), "gametest_dive_up", 8.5, 6, 8.5);
        if (dragon == null) {
            helper.fail("could not create the dragon");
            return;
        }
        helper.runAfterDelay(3, () -> {
            dragon.startDive(new Vec3(pad.getX() + 8.5, pad.getY(), pad.getZ() + 8.5));
            onLanding(helper, dragon, 60, landedAfter -> {
                List<String> problems = new ArrayList<>();
                helper.runAfterDelay(BoneDragonUnit.LAND_TICKS - 3, () -> {
                    if (!dragon.isLanded())
                        problems.add("up again before " + BoneDragonUnit.LAND_TICKS / 20 + " s");
                });
                helper.runAfterDelay(BoneDragonUnit.LAND_TICKS + 3, () -> {
                    if (dragon.isLanded() || !dragon.isFlyingUnit())
                        problems.add("still down after " + BoneDragonUnit.LAND_TICKS / 20 + " s");
                });
                helper.runAfterDelay(BoneDragonUnit.LAND_TICKS + 80, () -> {
                    try {
                        if (dragon.getY() < pad.getY() + 3)
                            problems.add(String.format("it took off but did not climb: %.1f above the pad", dragon.getY() - pad.getY()));
                        if (problems.isEmpty())
                            helper.succeed();
                        else
                            helper.fail(String.join("; ", problems));
                    } finally {
                        dragon.discard();
                    }
                });
            });
        });
    }

    /**
     * The button: a 90 s cooldown from the dive; on the ground it is greyed for the first 4 s and refuses to take off;
     * after that it takes off (the cooldown still running); back in the air, a second dive is refused on cooldown.
     */
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
        BlockPos ground = new BlockPos(pad.getX() + 8, pad.getY() - 1, pad.getZ() + 8);
        helper.runAfterDelay(3, () -> {
            DragonDive d = dive(dragon);
            if (d == null) {
                dragon.discard();
                helper.fail("the dragon has no Dive");
                return;
            }
            if (d.cooldownMax != BoneDragonUnit.DIVE_CD_SECONDS * 20)
                problems.add("the Dive's cooldown should be " + BoneDragonUnit.DIVE_CD_SECONDS + " s, is " + d.cooldownMax / 20 + " s");
            d.use(level, dragon, ground);
            if (d.isOffCooldown(dragon))
                problems.add("the cooldown should start at the dive");
            if (!dragon.isDiving())
                problems.add("the Dive did not start the dive");
            onLanding(helper, dragon, 60, landedAfter -> {
                if (d.canBypassCooldown(dragon))
                    problems.add("the button should stay greyed for the first " + BoneDragonUnit.MIN_LAND_TICKS / 20 + " s on the ground");
                d.use(level, dragon, dragon.blockPosition());
                helper.runAfterDelay(2, () -> {
                    if (!dragon.isLanded())
                        problems.add("it took off right after landing (before " + BoneDragonUnit.MIN_LAND_TICKS / 20 + " s)");
                });
                helper.runAfterDelay(BoneDragonUnit.MIN_LAND_TICKS + 5, () -> {
                    if (d.isOffCooldown(dragon))
                        problems.add("the cooldown should still be running on the ground");
                    if (!d.canBypassCooldown(dragon))
                        problems.add("on the ground the button should take off even on cooldown");
                    d.use(level, dragon, dragon.blockPosition());
                });
                helper.runAfterDelay(BoneDragonUnit.MIN_LAND_TICKS + 5 + 80, () -> {
                    try {
                        if (dragon.isLanded() || !dragon.isFlyingUnit())
                            problems.add("pressed again after " + BoneDragonUnit.MIN_LAND_TICKS / 20 + " s, it should fly again");
                        else if (dragon.getY() < pad.getY() + 3)
                            problems.add(String.format("it should climb back up, is %.1f above the pad", dragon.getY() - pad.getY()));
                        if (d.isOffCooldown(dragon))
                            problems.add("the cooldown ran out within " + (BoneDragonUnit.MIN_LAND_TICKS + 85) / 20 + " s");
                        d.use(level, dragon, ground);        // the server's own check, as a click would reach it
                        if (dragon.isDiving() || dragon.isLanded())
                            problems.add("a second dive went off on cooldown");
                        if (problems.isEmpty())
                            helper.succeed();
                        else
                            helper.fail(String.join("; ", problems));
                    } finally {
                        dragon.discard();
                    }
                });
            });
        });
    }

    /** Aimed 30 blocks away, it dives to the point 16 blocks toward the aim (never farther). */
    @GameTest(template = ARENA, timeoutTicks = 20)
    public static void bone_dragon_dive_reaches_sixteen_blocks_at_most(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pad = skyPad(helper, 290);
        var dragon = (BoneDragonUnit) put(level, pad, EntityRegistrar.BONE_DRAGON_UNIT.get(), "gametest_dive_far", 2.5, 6, 2.5);
        if (dragon == null) {
            helper.fail("could not create the dragon");
            return;
        }
        helper.runAfterDelay(3, () -> {
            try {
                Vec3 from = dragon.position();
                if (!dragon.startDive(from.add(30, -6, 0))) {
                    helper.fail("the dive was refused");
                    return;
                }
                Vec3 to = dragon.diveTarget();
                double flat = Math.sqrt((to.x - from.x) * (to.x - from.x) + (to.z - from.z) * (to.z - from.z));
                if (Math.abs(flat - BoneDragonUnit.DIVE_RANGE) > 0.05)
                    helper.fail(String.format("aimed 30 blocks off, it dives %.2f blocks (want %d)", flat, BoneDragonUnit.DIVE_RANGE));
                else
                    helper.succeed();
            } finally {
                dragon.discard();
            }
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
        var dragon = (BoneDragonUnit) put(level, pad, EntityRegistrar.BONE_DRAGON_UNIT.get(), "gametest_dive_roof", 8.5, 5, 8.5);
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
        helper.runAfterDelay(3, () -> {
            if (!dragon.startDive(new Vec3(pad.getX() + 10.5, pad.getY(), pad.getZ() + 8.5))) {
                clean.run();
                helper.fail("the dive was refused under the roof");
                return;
            }
            onLanding(helper, dragon, 100, landedAfter -> {
                double up = dragon.getY() - pad.getY();
                clean.run();
                if (up > 1.0)
                    helper.fail(String.format("it landed %.1f blocks above the floor (under a roof)", up));
                else
                    helper.succeed();
            });
        });
    }

    /** Saved and loaded again while down: still down for the rest of its 10 s, the cooldown still running. */
    @GameTest(template = ARENA, timeoutTicks = 60 + BoneDragonUnit.LAND_TICKS + 120)
    public static void bone_dragon_dive_state_and_cooldown_survive_a_reload(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pad = skyPad(helper, 290);
        var first = (BoneDragonUnit) put(level, pad, EntityRegistrar.BONE_DRAGON_UNIT.get(), "gametest_dive_reload", 8.5, 6, 8.5);
        if (first == null) {
            helper.fail("could not create the dragon");
            return;
        }
        BlockPos ground = new BlockPos(pad.getX() + 8, pad.getY() - 1, pad.getZ() + 8);
        helper.runAfterDelay(3, () -> {
            DragonDive d0 = dive(first);
            if (d0 != null)
                d0.use(level, first, ground);
            onLanding(helper, first, 60, landedAfter -> helper.runAfterDelay(20, () -> {
                float cdBefore = d0 == null ? 0 : d0.getCooldown(first);
                BoneDragonUnit dragon = reload(level, first);
                if (dragon == null) {
                    helper.fail("could not load the saved dragon");
                    return;
                }
                List<String> problems = new ArrayList<>();
                DragonDive d = dive(dragon);
                if (!dragon.isLanded() || dragon.isFlyingUnit())
                    problems.add("loaded mid-landing, it should still be down (a ground unit)");
                if (d == null || d.isOffCooldown(dragon) || Math.abs(d.getCooldown(dragon) - cdBefore) > 5)
                    problems.add(String.format("loaded mid-cooldown, the Dive should have its %.0f ticks left, has %s",
                        cdBefore, d == null ? "no Dive" : String.format("%.0f", d.getCooldown(dragon))));
                if (d != null) {
                    d.use(level, dragon, ground);
                    if (!dragon.isLanded())
                        problems.add("loaded, the button took it off before " + BoneDragonUnit.MIN_LAND_TICKS / 20 + " s down");
                }
                // 20 ticks were spent down before the save: the rest of the 10 s, then up
                helper.runAfterDelay(BoneDragonUnit.LAND_TICKS - 20 - 5, () -> {
                    if (!dragon.isLanded())
                        problems.add("the reload cut the time on the ground short");
                });
                helper.runAfterDelay(BoneDragonUnit.LAND_TICKS - 20 + 5, () -> {
                    try {
                        if (dragon.isLanded())
                            problems.add("the reload kept it down past its 10 s");
                        DragonDive dd = dive(dragon);
                        if (dd == null || dd.isOffCooldown(dragon))
                            problems.add("the cooldown ran out early after the reload");
                        if (problems.isEmpty())
                            helper.succeed();
                        else
                            helper.fail(String.join("; ", problems));
                    } finally {
                        dragon.discard();
                    }
                });
            }));
        });
    }

    /** Saved and loaded again mid-dive: it carries on down to its spot and lands there. */
    @GameTest(template = ARENA, timeoutTicks = 140)
    public static void bone_dragon_dive_in_flight_survives_a_reload(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pad = skyPad(helper, 290);
        var first = (BoneDragonUnit) put(level, pad, EntityRegistrar.BONE_DRAGON_UNIT.get(), "gametest_dive_reload2", 2.5, 10, 2.5);
        if (first == null) {
            helper.fail("could not create the dragon");
            return;
        }
        Vec3 spot = new Vec3(pad.getX() + 12.5, pad.getY(), pad.getZ() + 12.5);
        helper.runAfterDelay(3, () -> first.startDive(spot));
        helper.runAfterDelay(6, () -> {
            if (!first.isDiving()) {
                first.discard();
                helper.fail("the dive did not start");
                return;
            }
            BoneDragonUnit dragon = reload(level, first);
            if (dragon == null) {
                helper.fail("could not load the saved dragon");
                return;
            }
            if (!dragon.isDiving()) {
                dragon.discard();
                helper.fail("loaded mid-dive, it should still be diving");
                return;
            }
            onLanding(helper, dragon, 100, landedAfter -> {
                double off = Math.sqrt(Math.pow(dragon.getX() - spot.x, 2) + Math.pow(dragon.getZ() - spot.z, 2));
                dragon.discard();
                if (off > 2.5)
                    helper.fail(String.format("after the reload it landed %.1f blocks from its spot", off));
                else
                    helper.succeed();
            });
        });
    }
}
