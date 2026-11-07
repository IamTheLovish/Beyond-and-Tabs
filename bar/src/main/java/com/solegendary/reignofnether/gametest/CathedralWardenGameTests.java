package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.ability.abilities.BellToll;
import com.solegendary.reignofnether.ability.abilities.Sanctify;
import com.solegendary.reignofnether.building.Buildings;
import com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement;
import com.solegendary.reignofnether.building.production.ProductionItems;
import com.solegendary.reignofnether.keybinds.Keybindings;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.unit.T3Cap;
import com.solegendary.reignofnether.unit.goals.AbstractMeleeAttackUnitGoal;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.units.villagers.CathedralWardenUnit;
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
import net.minecraft.world.phys.AABB;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * Slice T3b-5 of the second Tier 3 (second-t3-design.md): the Sunforged <b>Cathedral Warden</b>. One test per
 * acceptance criterion, all un-hosted and inside the test's own arena: trained from slot 5 (T) of the Castle as its
 * own type and capped one alive beside the Sun Colossus; Sunbeam Volley shares its rays between up to six enemies in
 * range, air included, and spares friends and the far; Sanctify heals and cleanses friends in range; Bell Toll is
 * telegraphed, then slows enemies in range; the numbers keep the T3 rules.
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class CathedralWardenGameTests {

    static final String ARENA = SkirmishGameTests.ARENA;

    static String owner(GameTestHelper helper, String tag) {
        return "gametest_warden_" + tag + "_" + helper.absolutePos(BlockPos.ZERO).asLong();
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

    /** AC-1: trained from slot 5 (T) of the Castle as a Cathedral Warden (no placeholder tag, no Sun Colossus). */
    @GameTest(template = ARENA, timeoutTicks = 60)
    public static void cathedral_warden_is_trained_from_slot_t_as_its_own_unit(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        ServerLevel level = helper.getLevel();
        String owner = owner(helper, "train");
        if (Buildings.CASTLE.productions.forHotkey(Keybindings.abilitySlot5) != ProductionItems.SUN_T3B)
            problems.add("slot 5 (T) of the Castle is not the second T3");
        if (!CathedralWardenUnit.ITEM_NAME.equals(ProductionItems.SUN_T3B.getItemName()))
            problems.add("the second Sunforged T3 is still named " + ProductionItems.SUN_T3B.getItemName());
        ProductionPlacement pp = SecondT3FrameGameTests.placement(helper, Buildings.CASTLE, owner);
        if (pp == null) {
            T3RoleGameTests.finish(helper, List.of("no Castle placement"));
            return;
        }
        ProductionItems.SUN_T3B.onComplete.accept(level, pp);
        helper.runAfterDelay(3, () -> {
            AABB all = new AABB(helper.absolutePos(BlockPos.ZERO)).inflate(80);
            List<CathedralWardenUnit> made = level.getEntitiesOfClass(CathedralWardenUnit.class, all, u -> owner.equals(u.getOwnerName()));
            if (made.size() != 1)
                problems.add("training the second Sunforged T3 made " + made.size() + " Cathedral Wardens");
            else {
                if (made.get(0).getPersistentData().getBoolean(T3Cap.TAG_SECOND))
                    problems.add("a real unit must not carry the placeholder tag");
                made.get(0).discard();
            }
            if (level.getEntitiesOfClass(SunColossusUnit.class, all, u -> owner.equals(u.getOwnerName())).size() != 0)
                problems.add("the second Sunforged T3 trained a Sun Colossus body");
            T3RoleGameTests.finish(helper, problems);
        });
    }

    /** AC-2: one alive of each type: a living Warden blocks a second Warden, not a Sun Colossus; and the other way round. */
    @GameTest(template = ARENA, timeoutTicks = 60)
    public static void cathedral_warden_cap_is_one_alive_beside_the_sun_colossus(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        String owner = owner(helper, "cap");
        SecondT3FrameGameTests.grantTiers(owner);
        ProductionPlacement pp = SecondT3FrameGameTests.placement(helper, Buildings.CASTLE, owner);
        if (pp == null) {
            T3RoleGameTests.finish(helper, List.of("no Castle placement"));
            return;
        }
        if (T3Cap.productionError(pp, ProductionItems.SUN_T3B) != null)
            problems.add("a Warden was refused with none alive");
        var warden = put(helper, EntityRegistrar.CATHEDRAL_WARDEN_UNIT.get(), owner, 3.5, 8.5);
        if (warden == null) {
            T3RoleGameTests.finish(helper, List.of("could not create the Warden"));
            return;
        }
        if (!T3Cap.LIMIT_MSG.equals(T3Cap.productionError(pp, ProductionItems.SUN_T3B)))
            problems.add("a second Warden was not refused while one lives");
        if (T3Cap.productionError(pp, ProductionItems.SUN_COLOSSUS) != null)
            problems.add("a living Warden blocked the Sun Colossus");
        warden.discard();
        var colossus = put(helper, EntityRegistrar.SUN_COLOSSUS_UNIT.get(), owner, 3.5, 8.5);
        if (colossus != null) {
            if (T3Cap.productionError(pp, ProductionItems.SUN_T3B) != null)
                problems.add("a living Sun Colossus blocked the Warden");
            if (!T3Cap.LIMIT_MSG.equals(T3Cap.productionError(pp, ProductionItems.SUN_COLOSSUS)))
                problems.add("a second Sun Colossus was not refused while one lives");
            colossus.discard();
        }
        T3RoleGameTests.finish(helper, problems);
    }

    /**
     * AC-3 (Sunbeam Volley): it is a ranged unit with no melee goal; a volley with five enemies in range (one a flyer)
     * fires all {@link CathedralWardenUnit#VOLLEY_RAYS} rays, every enemy is hit, the total is rays x ray damage; with one
     * target all rays land on it; with eight enemies at most {@link CathedralWardenUnit#VOLLEY_TARGETS} are hit; an
     * enemy out of range and a friend are never hit.
     */
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void cathedral_warden_sunbeam_volley_hits_up_to_six_including_air(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = owner(helper, "volley"), foe = owner(helper, "volleyfoe");
        var w = put(helper, EntityRegistrar.CATHEDRAL_WARDEN_UNIT.get(), owner, 0.5, 0.5);
        var friend = tough(put(helper, EntityRegistrar.VINDICATOR_UNIT.get(), owner, 2.5, 0.5));
        List<Mob> foes = new ArrayList<>();
        for (int i = 0; i < 4; i++)
            foes.add(tough(put(helper, EntityRegistrar.VINDICATOR_UNIT.get(), foe, 3.5 + i * 2, 7.5)));
        var flyer = tough(put(helper, EntityRegistrar.BONE_DRAGON_UNIT.get(), foe, 8.5, 12.5));
        if (w == null || friend == null || flyer == null || foes.contains(null)) {
            helper.fail("could not create the volley units");
            return;
        }
        foes.add(flyer);
        w.setNoAi(true);
        friend.setNoAi(true);
        for (Mob m : foes)
            m.setNoAi(true);
        helper.runAfterDelay(3, () -> {
            List<String> problems = new ArrayList<>();
            if (!(w.getAttackGoal() != null) || w.getAttackGoal() instanceof AbstractMeleeAttackUnitGoal)
                problems.add("the Warden should attack from range, not in melee");
            if (CathedralWardenUnit.attackRange < 18)
                problems.add("the design's range is 18: " + CathedralWardenUnit.attackRange);
            float[] before = new float[foes.size()];
            for (int i = 0; i < foes.size(); i++)
                before[i] = foes.get(i).getHealth();
            float friendHp = friend.getHealth();
            int fired = w.volley(foes.get(0));
            if (fired != CathedralWardenUnit.VOLLEY_RAYS)
                problems.add("a volley fired " + fired + " rays, expected " + CathedralWardenUnit.VOLLEY_RAYS);
            float total = 0;
            for (int i = 0; i < foes.size(); i++) {
                float d = before[i] - foes.get(i).getHealth();
                total += d;
                if (d < CathedralWardenUnit.RAY_DAMAGE * 0.5f)
                    problems.add("enemy " + i + (foes.get(i) == flyer ? " (the flyer)" : "") + " was not hit (" + d + ")");
            }
            if (total < CathedralWardenUnit.RAY_DAMAGE * CathedralWardenUnit.VOLLEY_RAYS * 0.5f
                    || total > CathedralWardenUnit.RAY_DAMAGE * CathedralWardenUnit.VOLLEY_RAYS * 2.0f)
                problems.add("the volley dealt " + total + " in all, expected about "
                    + CathedralWardenUnit.RAY_DAMAGE * CathedralWardenUnit.VOLLEY_RAYS);
            if (friend.getHealth() != friendHp)
                problems.add("a friendly unit was hit by the volley");

            // one target: every ray lands on it
            for (int i = 1; i < foes.size(); i++)
                foes.get(i).discard();
            var lone = foes.get(0);
            float hp = lone.getHealth();
            w.volley(lone);
            float d = hp - lone.getHealth();
            if (d < CathedralWardenUnit.RAY_DAMAGE * CathedralWardenUnit.VOLLEY_RAYS * 0.5f)
                problems.add("with one target the rays were not all put on it (" + d + ")");

            // eight enemies: at most six are hit, the far one never
            List<Mob> crowd = new ArrayList<>();
            for (int i = 0; i < 8; i++)
                crowd.add(tough(put(helper, EntityRegistrar.VINDICATOR_UNIT.get(), foe, 1.5 + i, 7.5 + (i % 2))));
            Mob far = tough(put(helper, EntityRegistrar.VINDICATOR_UNIT.get(), foe, 14.5, 14.5));
            if (crowd.contains(null) || far == null) {
                helper.fail("could not create the crowd");
                return;
            }
            crowd.forEach(m -> m.setNoAi(true));
            far.setNoAi(true);
            float farHp = far.getHealth();
            float[] cb = new float[crowd.size()];
            for (int i = 0; i < cb.length; i++)
                cb[i] = crowd.get(i).getHealth();
            w.volley(crowd.get(0));
            int hit = 0;
            for (int i = 0; i < cb.length; i++)
                if (crowd.get(i).getHealth() < cb[i])
                    hit++;
            if (hit > CathedralWardenUnit.VOLLEY_TARGETS || hit < 2)
                problems.add("a volley into eight enemies hit " + hit + ", expected up to " + CathedralWardenUnit.VOLLEY_TARGETS);
            if (far.getHealth() != farHp)
                problems.add("an enemy out of range was hit");
            if (!(far.distanceTo(w) > CathedralWardenUnit.attackRange))
                problems.add("the far enemy was not really out of range");
            for (LivingEntity e : crowd)
                e.discard();
            for (LivingEntity e : List.<LivingEntity>of(w, friend, lone, far))
                e.discard();
            T3RoleGameTests.finish(helper, problems);
        });
    }

    /**
     * AC-4 (Sanctify): friends in range heal {@link Sanctify#HEAL_FRACTION} of their max HP and lose wither, slowness and
     * burning; the Warden is healed too; an enemy and a friend out of range are untouched; it goes on cooldown.
     */
    @GameTest(template = ARENA, timeoutTicks = 60)
    public static void cathedral_warden_sanctify_heals_and_cleanses(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = owner(helper, "sanctify"), foe = owner(helper, "sanctifyfoe");
        var w = put(helper, EntityRegistrar.CATHEDRAL_WARDEN_UNIT.get(), owner, 1.0, 2.5);
        var near = tough(put(helper, EntityRegistrar.VINDICATOR_UNIT.get(), owner, 5.5, 2.5));
        var far = tough(put(helper, EntityRegistrar.VINDICATOR_UNIT.get(), owner, 14.5, 14.5));
        var enemy = tough(put(helper, EntityRegistrar.VINDICATOR_UNIT.get(), foe, 3.5, 4.5));
        if (w == null || near == null || far == null || enemy == null) {
            helper.fail("could not create the sanctify units");
            return;
        }
        for (Mob m : List.of(w, near, far, enemy))
            m.setNoAi(true);
        var sanctify = (Sanctify) w.getAbilities().get().stream().filter(a -> a instanceof Sanctify).findFirst().orElse(null);
        if (sanctify == null) {
            helper.fail("the Warden has no Sanctify");
            return;
        }
        helper.runAfterDelay(3, () -> {
            List<String> problems = new ArrayList<>();
            for (Mob m : List.of(near, far, enemy)) {
                m.setHealth(400);
                m.addEffect(new MobEffectInstance(MobEffects.WITHER, 400, 1));
                m.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 400, 1));
                m.setSecondsOnFire(20);
            }
            w.setHealth(w.getMaxHealth() - 100);
            float wHp = w.getHealth();
            sanctify.use(level, w, w.blockPosition());
            if (sanctify.isOffCooldown(w))
                problems.add("Sanctify did not go on cooldown");
            float expect = 400 + near.getMaxHealth() * Sanctify.HEAL_FRACTION;
            if (Math.abs(near.getHealth() - expect) > 1.5f)
                problems.add("a friend in range has " + near.getHealth() + " HP, expected " + expect);
            if (near.hasEffect(MobEffects.WITHER) || near.hasEffect(MobEffects.MOVEMENT_SLOWDOWN) || near.isOnFire())
                problems.add("a friend in range was not cleansed of wither, slowness and burning");
            if (w.getHealth() <= wHp)
                problems.add("the Warden did not heal itself");
            if (far.getHealth() != 400 || !far.hasEffect(MobEffects.WITHER))
                problems.add("a friend out of range was healed or cleansed");
            if (enemy.getHealth() != 400 || !enemy.hasEffect(MobEffects.WITHER))
                problems.add("an enemy was healed or cleansed");
            for (LivingEntity e : List.<LivingEntity>of(w, near, far, enemy))
                e.discard();
            T3RoleGameTests.finish(helper, problems);
        });
    }

    /**
     * AC-5 (Bell Toll): casting puts it on cooldown and slows nobody for the telegraph; then enemies inside
     * {@link BellToll#RADIUS} are slowed for {@link BellToll#SLOW_TICKS}, and no friend and no enemy outside the ring is.
     */
    @GameTest(template = ARENA, timeoutTicks = 120)
    public static void cathedral_warden_bell_toll_telegraphs_then_slows(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = owner(helper, "toll"), foe = owner(helper, "tollfoe");
        var w = put(helper, EntityRegistrar.CATHEDRAL_WARDEN_UNIT.get(), owner, 0.5, 0.5);
        var inside = tough(put(helper, EntityRegistrar.VINDICATOR_UNIT.get(), foe, 4.5, 8.5));
        var outside = tough(put(helper, EntityRegistrar.VINDICATOR_UNIT.get(), foe, 14.5, 14.5));
        var friend = tough(put(helper, EntityRegistrar.VINDICATOR_UNIT.get(), owner, 3.5, 0.5));
        if (w == null || inside == null || outside == null || friend == null) {
            helper.fail("could not create the toll units");
            return;
        }
        for (Mob m : List.of(w, inside, outside, friend))
            m.setNoAi(true);
        var toll = (BellToll) w.getAbilities().get().stream().filter(a -> a instanceof BellToll).findFirst().orElse(null);
        if (toll == null) {
            helper.fail("the Warden has no Bell Toll");
            return;
        }
        List<String> problems = new ArrayList<>();
        if (BellToll.TELEGRAPH_TICKS < 10 || BellToll.TELEGRAPH_TICKS > 30)
            problems.add("the telegraph should be readable but under 1.5 s: " + BellToll.TELEGRAPH_TICKS + " ticks");
        helper.runAfterDelay(4, () -> {
            toll.use(level, w, w.blockPosition());
            if (toll.isOffCooldown(w))
                problems.add("Bell Toll did not go on cooldown");
            helper.runAfterDelay(BellToll.TELEGRAPH_TICKS / 2, () -> {
                if (inside.hasEffect(MobEffects.MOVEMENT_SLOWDOWN))
                    problems.add("the enemy was slowed during the telegraph");
            });
            helper.runAfterDelay(BellToll.TELEGRAPH_TICKS + 4, () -> {
                var slow = inside.getEffect(MobEffects.MOVEMENT_SLOWDOWN);
                if (slow == null || slow.getDuration() > BellToll.SLOW_TICKS || slow.getDuration() < BellToll.SLOW_TICKS - 20)
                    problems.add("the enemy in the ring is not slowed for about " + BellToll.SLOW_TICKS + " ticks");
                if (outside.hasEffect(MobEffects.MOVEMENT_SLOWDOWN))
                    problems.add("an enemy outside the ring was slowed");
                if (friend.hasEffect(MobEffects.MOVEMENT_SLOWDOWN))
                    problems.add("a friendly unit in the ring was slowed");
                for (LivingEntity e : List.<LivingEntity>of(w, inside, outside, friend))
                    e.discard();
                T3RoleGameTests.finish(helper, problems);
            });
        });
    }

    /** AC-6: the numbers keep the T3 rules: its own HP total, the same price as the Sun Colossus, below its damage, slow, never pushed. */
    @GameTest(template = ARENA, timeoutTicks = 40)
    public static void cathedral_warden_numbers_keep_the_t3_rules(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        float[] others = { SunColossusUnit.maxHealth,
            com.solegendary.reignofnether.unit.units.monsters.BoneDragonUnit.maxHealth,
            com.solegendary.reignofnether.unit.units.monsters.PlagueAbominationUnit.maxHealth,
            com.solegendary.reignofnether.unit.units.piglins.WarMammothUnit.maxHealth,
            com.solegendary.reignofnether.unit.units.piglins.MagmaHurlerUnit.maxHealth,
            com.solegendary.reignofnether.unit.units.verdant.WorldTreeWalkerUnit.maxHealth,
            com.solegendary.reignofnether.unit.units.tide.LeviathanUnit.maxHealth,
            com.solegendary.reignofnether.unit.units.storm.CelestialDragonUnit.maxHealth,
            com.solegendary.reignofnether.unit.units.storm.JadeSentinelUnit.maxHealth,
            com.solegendary.reignofnether.unit.units.brass.IronLeviathanEngineUnit.maxHealth };
        for (float hp : others)
            if (hp == CathedralWardenUnit.maxHealth)
                problems.add("the Warden shares " + hp + " HP with another T3");
        var first = ProductionItems.SUN_COLOSSUS.getCost(false, "nobody");
        var second = ProductionItems.SUN_T3B.getCost(false, "nobody");
        if (first.metal() != second.metal() || first.energy() != second.energy() || first.population != second.population)
            problems.add("the Warden's price differs from the Sun Colossus'");
        if (CathedralWardenUnit.maxHealth >= SunColossusUnit.maxHealth)
            problems.add("the support should be frailer than the anchor");
        if (CathedralWardenUnit.attackDamage * CathedralWardenUnit.attacksPerSecond
                >= SunColossusUnit.attackDamage * SunColossusUnit.attacksPerSecond)
            problems.add("the support should deal less damage than the anchor it supports");
        if (CathedralWardenUnit.movementSpeed >= SunColossusUnit.movementSpeed)
            problems.add("the Warden should walk slower than the Colossus line");
        CathedralWardenUnit u = EntityRegistrar.CATHEDRAL_WARDEN_UNIT.get().create(helper.getLevel());
        if (u == null)
            problems.add("could not create the Warden");
        else {
            if (u.isPushable())
                problems.add("a titan must not be pushed by other bodies");
            if (((Unit) u).getCost() != com.solegendary.reignofnether.resources.ResourceCosts.SUN_T3B)
                problems.add("the Warden's cost entry is not SUN_T3B");
            u.updateAbilityButtons();   // a refresh must not duplicate the abilities
            var list = u.getAbilities().get();
            if (list.stream().filter(a -> a instanceof Sanctify).count() != 1
                    || list.stream().filter(a -> a instanceof BellToll).count() != 1)
                problems.add("the Warden should carry exactly one Sanctify and one Bell Toll");
            u.discard();
        }
        T3RoleGameTests.finish(helper, problems);
    }
}
