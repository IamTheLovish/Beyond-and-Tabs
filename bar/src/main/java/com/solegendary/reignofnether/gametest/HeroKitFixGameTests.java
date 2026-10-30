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
 * HeroGameTests does. Written before the code (tests first).
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class HeroKitFixGameTests {

    static final String ARENA = SkirmishGameTests.ARENA;

    static FactionHeroAbility atRank(FactionHero hero, int slot, int rank) {
        FactionHeroAbility a = HeroKits.ability(hero, slot);
        a.setRank(hero, rank);
        a.updateStatsForRank(hero);
        return a;
    }

    static int slowTicks(Mob m) {
        MobEffectInstance e = m.getEffect(MobEffects.MOVEMENT_SLOWDOWN);
        return e == null ? 0 : e.getDuration();
    }

    static void done(GameTestHelper helper, List<String> problems, List<? extends Entity> mobs) {
        for (Entity m : mobs)
            if (m != null)
                m.discard();
        if (problems.isEmpty())
            helper.succeed();
        else
            helper.fail(String.join("; ", problems));
    }

    /** Rank 3 slows for longer than rank 1: Frost Nova past its old flat 3 s, Riptide past its old flat 2 s. */
    @GameTest(template = ARENA, timeoutTicks = 60)
    public static void hero_kit_frost_nova_and_riptide_slow_longer_at_rank_three(GameTestHelper helper) {
        String me = HeroGameTests.owner(helper, "kit_slow"), them = HeroGameTests.owner(helper, "kit_slow_enemy");
        Mob witch = HeroGameTests.spawnHero(helper, HeroKits.STORM_WITCH, me, 3, 3);
        Mob corsair = HeroGameTests.spawnHero(helper, HeroKits.CORSAIR_CAPTAIN, me, 12, 3);
        Mob a = HeroGameTests.spawnUnit(helper, EntityRegistrar.VINDICATOR_UNIT.get(), them, 3, 5);
        Mob b = HeroGameTests.spawnUnit(helper, EntityRegistrar.VINDICATOR_UNIT.get(), them, 12, 5);
        List<Mob> mobs = List.of(witch, corsair, a, b);
        if (!(witch instanceof FactionHero w) || !(corsair instanceof FactionHero c) || a == null || b == null) {
            done(helper, List.of("could not spawn the test units"), mobs);
            return;
        }
        helper.runAfterDelay(3, () -> {
            List<String> problems = new ArrayList<>();
            if (!atRank(w, 2, 3).castNow(w, null, witch.position()))
                problems.add("Frost Nova did not cast");
            else if (slowTicks(a) <= 60)
                problems.add("Frost Nova at rank 3 slowed for " + slowTicks(a) + " ticks, no longer than its old flat 3 s");
            if (!atRank(c, 2, 3).castNow(c, null, corsair.position()))
                problems.add("Riptide did not cast");
            else if (slowTicks(b) <= 40)
                problems.add("Riptide at rank 3 slowed for " + slowTicks(b) + " ticks, no longer than its old flat 2 s");
            done(helper, problems, mobs);
        });
    }

    /**
     * Deploy Turret at rank 3, aimed six blocks east: the two turrets stand either side of the aim, across the line the
     * hero aimed along, not one behind the other.
     */
    @GameTest(template = ARENA, timeoutTicks = 60)
    public static void hero_kit_deploy_turret_puts_its_pair_either_side_of_the_aim(GameTestHelper helper) {
        String me = HeroGameTests.owner(helper, "kit_turret");
        Mob engineer = HeroGameTests.spawnHero(helper, HeroKits.ARCH_ENGINEER, me, 3, 7);
        if (!(engineer instanceof FactionHero e)) {
            done(helper, List.of("could not spawn the hero"), new ArrayList<Mob>());
            return;
        }
        helper.runAfterDelay(3, () -> {
            List<String> problems = new ArrayList<>();
            List<Entity> all = new ArrayList<>(List.of(engineer));
            Vec3 aim = engineer.position().add(6, 0, 0);
            if (!atRank(e, 2, 3).castNow(e, null, aim)) {
                problems.add("Deploy Turret did not cast");
                done(helper, problems, all);
                return;
            }
            List<? extends Entity> turrets = helper.getLevel().getEntities(EntityRegistrar.DEPLOYED_TURRET_UNIT.get(),
                engineer.getBoundingBox().inflate(12), t -> true);
            all.addAll(turrets);
            if (turrets.size() != 2) {
                problems.add(turrets.size() + " turrets, not 2 at rank 3");
            } else {
                Entity t0 = turrets.get(0), t1 = turrets.get(1);
                if (Math.abs(t0.getZ() - t1.getZ()) < 2)
                    problems.add(String.format("the pair stands in a line along the aim (z %.1f and %.1f), not side by side across it",
                        t0.getZ(), t1.getZ()));
                for (Entity t : turrets)
                    if (Math.abs(t.getX() - aim.x) > 1.5)
                        problems.add(String.format("a turret stands %.1f blocks along the aim from the spot aimed at", t.getX() - aim.x));
            }
            done(helper, problems, all);
        });
    }

    /** Field Repair with no machine near and nobody hurt: no cast, so no 45 s cooldown and no mana for nothing. */
    @GameTest(template = ARENA, timeoutTicks = 60)
    public static void hero_kit_field_repair_with_nothing_to_repair_is_not_spent(GameTestHelper helper) {
        String me = HeroGameTests.owner(helper, "kit_repair");
        Mob engineer = HeroGameTests.spawnHero(helper, HeroKits.ARCH_ENGINEER, me, 3, 3);
        if (!(engineer instanceof FactionHero e)) {
            done(helper, List.of("could not spawn the hero"), new ArrayList<Mob>());
            return;
        }
        helper.runAfterDelay(3, () -> {
            List<String> problems = new ArrayList<>();
            FactionHeroAbility repair = atRank(e, 3, 1);
            e.setMana(e.getMaxMana());
            float mana = e.getMana();
            if (repair.castNow(e, null, engineer.position()))
                problems.add("Field Repair cast with nothing to repair");
            if (!repair.isOffCooldown(e))
                problems.add("Field Repair went on cooldown with nothing to repair");
            if (e.getMana() < mana)
                problems.add("Field Repair took " + (mana - e.getMana()) + " mana with nothing to repair");
            done(helper, problems, List.of(engineer));
        });
    }
}
