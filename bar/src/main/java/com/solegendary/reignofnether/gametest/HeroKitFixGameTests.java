package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.hero.FactionHero;
import com.solegendary.reignofnether.hero.FactionHeroAbility;
import com.solegendary.reignofnether.hero.HeroKits;
import com.solegendary.reignofnether.registrars.EntityRegistrar;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * Small ability bugs that make a hero feel off (design-draft-heroes-better-2026-10-30 #9): Frost Nova's and Riptide's
 * slows were fixed lengths whatever the rank (3 s and 2 s) while everything else in the tooltip grew; Deploy Turret's two
 * turrets went in a row along +x whatever way the hero aimed (one behind the other when aimed east); Field Repair went
 * on its 45 s cooldown and took its mana with nothing to repair. Each ability is cast directly (castNow), as
 * HeroGameTests does; every test discards what it spawned whatever happens (try/finally). Written before the code (tests
 * first; review r1 made them exact: every rank's slow, three aims and rank 1 for the turrets, and each case Field Repair
 * must still cast in).
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class HeroKitFixGameTests {

    static final String ARENA = SkirmishGameTests.ARENA;

    static FactionHeroAbility atRank(FactionHero hero, int slot, int rank) {
        FactionHeroAbility a = HeroKits.ability(hero, slot);
        a.setRank(hero, rank);
        a.updateStatsForRank(hero);
        a.setCooldown(0, hero);
        hero.setMana(hero.getMaxMana());
        return a;
    }

    static int slowTicks(Mob m) {
        MobEffectInstance e = m.getEffect(MobEffects.MOVEMENT_SLOWDOWN);
        return e == null ? 0 : e.getDuration();
    }

    static void done(GameTestHelper helper, List<String> problems) {
        if (problems.isEmpty())
            helper.succeed();
        else
            helper.fail(String.join("; ", problems));
    }

    static void discard(List<? extends Entity> all) {
        for (Entity e : all)
            if (e != null && !e.isRemoved())
                e.discard();
    }

    // ------------------------------------------------------------------------------------------------ slows by rank

    /**
     * Frost Nova slows for exactly 3 / 3.5 / 4 s (60 / 70 / 80 ticks) at ranks 1 / 2 / 3 and Riptide for 2 / 2.5 / 3 s
     * (40 / 50 / 60): each rank cast on a fresh slow, read the tick it lands.
     */
    @GameTest(template = ARENA, timeoutTicks = 60)
    public static void hero_kit_frost_nova_and_riptide_slow_longer_at_every_rank(GameTestHelper helper) {
        String me = HeroGameTests.owner(helper, "kit_slow"), them = HeroGameTests.owner(helper, "kit_slow_enemy");
        Mob witch = HeroGameTests.spawnHero(helper, HeroKits.STORM_WITCH, me, 3, 3);
        Mob corsair = HeroGameTests.spawnHero(helper, HeroKits.CORSAIR_CAPTAIN, me, 12, 3);
        Mob a = HeroGameTests.spawnUnit(helper, EntityRegistrar.VINDICATOR_UNIT.get(), them, 3, 5);
        Mob b = HeroGameTests.spawnUnit(helper, EntityRegistrar.VINDICATOR_UNIT.get(), them, 12, 5);
        List<Mob> mobs = new ArrayList<>(java.util.Arrays.asList(witch, corsair, a, b));
        helper.runAfterDelay(3, () -> {
            List<String> problems = new ArrayList<>();
            try {
                if (!(witch instanceof FactionHero w) || !(corsair instanceof FactionHero c) || a == null || b == null) {
                    problems.add("could not spawn the test units");
                    return;
                }
                int[] nova = { 60, 70, 80 }, riptide = { 40, 50, 60 };
                for (int rank = 1; rank <= 3; rank++) {
                    a.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
                    b.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
                    a.teleportTo(witch.getX(), witch.getY(), witch.getZ() + 2);
                    b.teleportTo(corsair.getX(), corsair.getY(), corsair.getZ() + 2);
                    if (!atRank(w, 2, rank).castNow(w, null, witch.position()))
                        problems.add("Frost Nova did not cast at rank " + rank);
                    else if (slowTicks(a) != nova[rank - 1])
                        problems.add("Frost Nova at rank " + rank + " slowed for " + slowTicks(a) + " ticks, not " + nova[rank - 1]);
                    if (!atRank(c, 2, rank).castNow(c, null, corsair.position()))
                        problems.add("Riptide did not cast at rank " + rank);
                    else if (slowTicks(b) != riptide[rank - 1])
                        problems.add("Riptide at rank " + rank + " slowed for " + slowTicks(b) + " ticks, not " + riptide[rank - 1]);
                }
            } finally {
                discard(mobs);
                done(helper, problems);
            }
        });
    }

    // ------------------------------------------------------------------------------------------------ Deploy Turret

    /**
     * One Deploy Turret cast from the hero toward {@code aimDir} (flat, six blocks): the turrets it set down. Each cast
     * starts with no turret near and the ability ready; the caller discards what it got.
     */
    static List<? extends Entity> turretsFor(GameTestHelper helper, Mob engineer, FactionHero e, int rank, Vec3 aimDir,
                                            List<Entity> all, List<String> problems, String label) {
        Vec3 aim = engineer.position().add(aimDir.normalize().scale(6));
        if (!atRank(e, 2, rank).castNow(e, null, aim)) {
            problems.add(label + ": Deploy Turret did not cast");
            return List.of();
        }
        List<? extends Entity> turrets = helper.getLevel().getEntities(EntityRegistrar.DEPLOYED_TURRET_UNIT.get(),
            engineer.getBoundingBox().inflate(12), t -> !t.isRemoved());
        all.addAll(turrets);
        return turrets;
    }

    /**
     * Rank 3's pair for an aim: its midpoint on the spot aimed at, about three blocks apart, across the aim line (not
     * along it), each facing the way the hero aimed. A turret's spot is snapped to its block, so the limits allow half
     * a block either way on each axis.
     */
    static void checkPair(List<? extends Entity> turrets, Vec3 hero, Vec3 aimDir, List<String> problems, String label) {
        if (turrets.size() != 2) {
            problems.add(label + ": " + turrets.size() + " turrets at rank 3, not 2");
            return;
        }
        Vec3 dir = aimDir.normalize();
        Vec3 aim = hero.add(dir.scale(6));
        Entity t0 = turrets.get(0), t1 = turrets.get(1);
        double midX = (t0.getX() + t1.getX()) / 2, midZ = (t0.getZ() + t1.getZ()) / 2;
        double off = Math.hypot(midX - aim.x, midZ - aim.z);
        if (off > 1.0)
            problems.add(String.format("%s: the pair's midpoint is %.1f blocks from the spot aimed at", label, off));
        double dx = t1.getX() - t0.getX(), dz = t1.getZ() - t0.getZ();
        double spacing = Math.hypot(dx, dz), along = Math.abs(dx * dir.x + dz * dir.z);
        if (spacing < 2.0 || spacing > 4.3)
            problems.add(String.format("%s: the turrets stand %.1f blocks apart, not about 3", label, spacing));
        if (along > 1.0)
            problems.add(String.format("%s: the pair lies %.1f blocks along the aim line, not across it", label, along));
        float want = (float) Math.toDegrees(Math.atan2(-dir.x, dir.z));
        for (Entity t : turrets) {
            float diff = Math.abs(net.minecraft.util.Mth.wrapDegrees(t.getYRot() - want));
            if (diff > 15)
                problems.add(String.format("%s: a turret faces %.0f degrees off the aim", label, diff));
        }
    }

    /** Rank 3 aimed east, north and north-east: the pair stands across each aim; rank 1's single turret on the spot. */
    @GameTest(template = ARENA, timeoutTicks = 80)
    public static void hero_kit_deploy_turret_puts_its_pair_either_side_of_the_aim(GameTestHelper helper) {
        String me = HeroGameTests.owner(helper, "kit_turret");
        Mob engineer = HeroGameTests.spawnHero(helper, HeroKits.ARCH_ENGINEER, me, 7, 7);
        List<Entity> all = new ArrayList<>();
        if (engineer != null)
            all.add(engineer);
        helper.runAfterDelay(3, () -> {
            List<String> problems = new ArrayList<>();
            try {
                if (!(engineer instanceof FactionHero e)) {
                    problems.add("could not spawn the hero");
                    return;
                }
                Vec3[] aims = { new Vec3(1, 0, 0), new Vec3(0, 0, -1), new Vec3(1, 0, -1) };
                String[] names = { "aimed east", "aimed north", "aimed north-east" };
                for (int i = 0; i < aims.length; i++) {
                    List<? extends Entity> got = turretsFor(helper, engineer, e, 3, aims[i], all, problems, names[i]);
                    checkPair(got, engineer.position(), aims[i], problems, names[i]);
                    discard(got);
                }
                List<? extends Entity> one = turretsFor(helper, engineer, e, 1, aims[0], all, problems, "rank 1");
                Vec3 aim = engineer.position().add(6, 0, 0);
                if (one.size() != 1)
                    problems.add("rank 1: " + one.size() + " turrets, not 1");
                else if (Math.hypot(one.get(0).getX() - aim.x, one.get(0).getZ() - aim.z) > 0.75)
                    problems.add(String.format("rank 1: the turret stands %.1f blocks from the spot aimed at",
                        Math.hypot(one.get(0).getX() - aim.x, one.get(0).getZ() - aim.z)));
            } finally {
                discard(all);
                done(helper, problems);
            }
        });
    }

    // ------------------------------------------------------------------------------------------------ Field Repair

    /**
     * Field Repair with no machine near and nobody hurt: no cast, so no 45 s cooldown and no mana for nothing. And each
     * case it must still go off in: a friendly machine in reach (even at full health - the plating is the point), a
     * hurt friend, and the hero alone but hurt.
     */
    @GameTest(template = ARENA, timeoutTicks = 60)
    public static void hero_kit_field_repair_casts_only_with_something_to_repair(GameTestHelper helper) {
        String me = HeroGameTests.owner(helper, "kit_repair");
        Mob engineer = HeroGameTests.spawnHero(helper, HeroKits.ARCH_ENGINEER, me, 3, 3);
        List<Entity> all = new ArrayList<>();
        if (engineer != null)
            all.add(engineer);
        helper.runAfterDelay(3, () -> {
            List<String> problems = new ArrayList<>();
            try {
                if (!(engineer instanceof FactionHero e)) {
                    problems.add("could not spawn the hero");
                    return;
                }
                // nothing to repair
                FactionHeroAbility repair = atRank(e, 3, 1);
                float mana = e.getMana();
                if (repair.castNow(e, null, engineer.position()))
                    problems.add("Field Repair cast with nothing to repair");
                if (!repair.isOffCooldown(e))
                    problems.add("Field Repair went on cooldown with nothing to repair");
                if (e.getMana() < mana)
                    problems.add("Field Repair took " + (mana - e.getMana()) + " mana with nothing to repair");
                // the hero alone, hurt
                engineer.setHealth(engineer.getMaxHealth() * 0.5f);
                if (!atRank(e, 3, 1).castNow(e, null, engineer.position()))
                    problems.add("Field Repair did not cast for the hurt hero alone");
                engineer.setHealth(engineer.getMaxHealth());
                // a hurt friend
                Mob friend = HeroGameTests.spawnUnit(helper, EntityRegistrar.VINDICATOR_UNIT.get(), me, 5, 3);
                all.add(friend);
                if (friend == null)
                    problems.add("setup: could not spawn the friend");
                else {
                    friend.setHealth(friend.getMaxHealth() * 0.5f);
                    if (!atRank(e, 3, 1).castNow(e, null, engineer.position()))
                        problems.add("Field Repair did not cast with a hurt friend in reach");
                    friend.discard();
                }
                // a friendly machine at full health
                Mob machine = HeroGameTests.spawnUnit(helper, EntityRegistrar.CLOCKWORK_SPIDER_UNIT.get(), me, 3, 6);
                all.add(machine);
                if (machine == null)
                    problems.add("setup: could not spawn the machine");
                else if (!atRank(e, 3, 1).castNow(e, null, engineer.position()))
                    problems.add("Field Repair did not cast with a friendly machine (full health) in reach");
            } finally {
                discard(all);
                done(helper, problems);
            }
        });
    }
}
