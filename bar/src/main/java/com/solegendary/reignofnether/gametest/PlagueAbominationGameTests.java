package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.ability.abilities.BileLob;
import com.solegendary.reignofnether.ability.abilities.CarrionBurst;
import com.solegendary.reignofnether.building.Buildings;
import com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement;
import com.solegendary.reignofnether.building.production.ProductionItems;
import com.solegendary.reignofnether.keybinds.Keybindings;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.research.ResearchServerEvents;
import com.solegendary.reignofnether.unit.DragonRaiseServerEvents;
import com.solegendary.reignofnether.unit.T3Cap;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.units.monsters.BoneDragonUnit;
import com.solegendary.reignofnether.unit.units.monsters.PlagueAbominationUnit;

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
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * Slice T3b-3 of the second Tier 3 (second-t3-design.md): the Gravebound's <b>Plague Abomination</b>. One test per
 * acceptance criterion, all un-hosted and inside the test's own arena: trained from slot 5 (T) of the Stronghold as its
 * own type and capped one alive beside the Bone Dragon; Rot Aura stacks wither to 3 on enemy units in range and spares
 * friends and the far; Bile Lob is telegraphed, then its pool hurts enemies in it and spares friends and the far; Carrion
 * Burst costs 20% HP and raises six skeletons under the shared risen cap; the numbers keep the T3 rules.
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class PlagueAbominationGameTests {

    static final String ARENA = SkirmishGameTests.ARENA;

    static String owner(GameTestHelper helper, String tag) {
        return "gametest_abom_" + tag + "_" + helper.absolutePos(BlockPos.ZERO).asLong();
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

    /** AC-1: trained from slot 5 (T) of the Stronghold as a Plague Abomination (no placeholder tag, no Bone Dragon). */
    @GameTest(template = ARENA, timeoutTicks = 60)
    public static void plague_abomination_is_trained_from_slot_t_as_its_own_unit(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        ServerLevel level = helper.getLevel();
        String owner = owner(helper, "train");
        if (Buildings.STRONGHOLD.productions.forHotkey(Keybindings.abilitySlot5) != ProductionItems.GRAVE_T3B)
            problems.add("slot 5 (T) of the Stronghold is not the second T3");
        if (!PlagueAbominationUnit.ITEM_NAME.equals(ProductionItems.GRAVE_T3B.getItemName()))
            problems.add("the second Gravebound T3 is still named " + ProductionItems.GRAVE_T3B.getItemName());
        ProductionPlacement pp = SecondT3FrameGameTests.placement(helper, Buildings.STRONGHOLD, owner);
        if (pp == null) {
            T3RoleGameTests.finish(helper, List.of("no Stronghold placement"));
            return;
        }
        ProductionItems.GRAVE_T3B.onComplete.accept(level, pp);
        helper.runAfterDelay(3, () -> {
            AABB all = new AABB(helper.absolutePos(BlockPos.ZERO)).inflate(80);
            List<PlagueAbominationUnit> made = level.getEntitiesOfClass(PlagueAbominationUnit.class, all, u -> owner.equals(u.getOwnerName()));
            if (made.size() != 1)
                problems.add("training the second Gravebound T3 made " + made.size() + " Plague Abominations");
            else {
                if (made.get(0).getPersistentData().getBoolean(T3Cap.TAG_SECOND))
                    problems.add("a real unit must not carry the placeholder tag");
                made.get(0).discard();
            }
            if (level.getEntitiesOfClass(BoneDragonUnit.class, all, u -> owner.equals(u.getOwnerName())).size() != 0)
                problems.add("the second Gravebound T3 trained a Bone Dragon body");
            T3RoleGameTests.finish(helper, problems);
        });
    }

    /** AC-2: one alive of each type: a living Abomination blocks a second Abomination, not a Bone Dragon; and the other way round. */
    @GameTest(template = ARENA, timeoutTicks = 60)
    public static void plague_abomination_cap_is_one_alive_beside_the_bone_dragon(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        String owner = owner(helper, "cap");
        SecondT3FrameGameTests.grantTiers(owner);
        ProductionPlacement pp = SecondT3FrameGameTests.placement(helper, Buildings.STRONGHOLD, owner);
        if (pp == null) {
            T3RoleGameTests.finish(helper, List.of("no Stronghold placement"));
            return;
        }
        if (T3Cap.productionError(pp, ProductionItems.GRAVE_T3B) != null)
            problems.add("an Abomination was refused with none alive");
        var abom = put(helper, EntityRegistrar.PLAGUE_ABOMINATION_UNIT.get(), owner, 3.5, 8.5);
        if (abom == null) {
            T3RoleGameTests.finish(helper, List.of("could not create the Abomination"));
            return;
        }
        if (!T3Cap.LIMIT_MSG.equals(T3Cap.productionError(pp, ProductionItems.GRAVE_T3B)))
            problems.add("a second Abomination was not refused while one lives");
        if (T3Cap.productionError(pp, ProductionItems.BONE_DRAGON) != null)
            problems.add("a living Abomination blocked the Bone Dragon");
        abom.discard();
        var dragon = put(helper, EntityRegistrar.BONE_DRAGON_UNIT.get(), owner, 3.5, 8.5);
        if (dragon != null) {
            if (T3Cap.productionError(pp, ProductionItems.GRAVE_T3B) != null)
                problems.add("a living Bone Dragon blocked the Abomination");
            if (!T3Cap.LIMIT_MSG.equals(T3Cap.productionError(pp, ProductionItems.BONE_DRAGON)))
                problems.add("a second Bone Dragon was not refused while one lives");
            dragon.discard();
        }
        T3RoleGameTests.finish(helper, problems);
    }

    /**
     * AC-3 (Rot Aura): each pulse adds one wither stack (amplifier 0, 1, 2, then it stays at 2) to enemy units within
     * {@link PlagueAbominationUnit#ROT_RADIUS}; a friend and an enemy outside the radius are untouched.
     */
    @GameTest(template = ARENA, timeoutTicks = 60)
    public static void plague_abomination_rot_aura_stacks_wither(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = owner(helper, "rot"), foe = owner(helper, "rotfoe");
        var abom = put(helper, EntityRegistrar.PLAGUE_ABOMINATION_UNIT.get(), owner, 2.5, 8.5);
        var near = tough(put(helper, EntityRegistrar.VINDICATOR_UNIT.get(), foe, 6.5, 8.5));
        var far = tough(put(helper, EntityRegistrar.VINDICATOR_UNIT.get(), foe, 12.5, 8.5));
        var friend = tough(put(helper, EntityRegistrar.VINDICATOR_UNIT.get(), owner, 4.5, 10.5));
        if (abom == null || near == null || far == null || friend == null) {
            helper.fail("could not create the rot units");
            return;
        }
        for (Mob m : List.of(near, far, friend))
            m.setNoAi(true);
        helper.runAfterDelay(3, () -> {
            List<String> problems = new ArrayList<>();
            int[] expect = { 0, 1, 2, 2 };
            for (int i = 0; i < expect.length; i++) {
                int touched = abom.rot(level);
                MobEffectInstance e = near.getEffect(MobEffects.WITHER);
                if (touched != 1)
                    problems.add("pulse " + (i + 1) + " touched " + touched + " units, expected only the one in range");
                if (e == null || e.getAmplifier() != expect[i])
                    problems.add("after pulse " + (i + 1) + " the wither amplifier is " + (e == null ? "none" : e.getAmplifier()) + ", expected " + expect[i]);
            }
            if (far.hasEffect(MobEffects.WITHER))
                problems.add("an enemy outside the aura got wither");
            if (friend.hasEffect(MobEffects.WITHER))
                problems.add("a friendly unit got wither");
            if (PlagueAbominationUnit.ROT_MAX_STACKS != 3)
                problems.add("the aura should stack to 3");
            for (LivingEntity e : List.<LivingEntity>of(abom, near, far, friend))
                e.discard();
            T3RoleGameTests.finish(helper, problems);
        });
    }

    /**
     * AC-4 (Bile Lob): casting puts the ability on cooldown and hurts nobody for the telegraph; then the pool deals
     * {@link BileLob#POOL_DAMAGE} a second for {@link BileLob#POOL_TICKS} ticks to enemies inside {@link BileLob#RADIUS},
     * and never to a friend or an enemy outside the ring.
     */
    @GameTest(template = ARENA, timeoutTicks = 260)
    public static void plague_abomination_bile_lob_telegraphs_then_burns_the_pool(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = owner(helper, "bile"), foe = owner(helper, "bilefoe");
        var abom = put(helper, EntityRegistrar.PLAGUE_ABOMINATION_UNIT.get(), owner, 2.5, 8.5);
        if (abom != null) {
            abom.setNoAi(true);   // it is melee: left free it would walk over and fight the targets
            com.solegendary.reignofnether.ability.AutocastState.setAll(abom, false);
        }
        var inside = tough(put(helper, EntityRegistrar.VINDICATOR_UNIT.get(), foe, 14.5, 8.5));
        var outside = tough(put(helper, EntityRegistrar.VINDICATOR_UNIT.get(), foe, 14.5, 13.5));
        var friend = tough(put(helper, EntityRegistrar.VINDICATOR_UNIT.get(), owner, 13.5, 8.5));
        if (abom == null || inside == null || outside == null || friend == null) {
            helper.fail("could not create the bile units");
            return;
        }
        for (Mob m : List.of(inside, outside, friend))
            m.setNoAi(true);
        BileLob bile = (BileLob) abom.getAbilities().get().stream().filter(a -> a instanceof BileLob).findFirst().orElse(null);
        if (bile == null) {
            helper.fail("the Abomination has no Bile Lob");
            return;
        }
        List<String> problems = new ArrayList<>();
        if (BileLob.TELEGRAPH_TICKS < 10 || BileLob.TELEGRAPH_TICKS > 30)
            problems.add("the telegraph should be readable but under 1.5 s: " + BileLob.TELEGRAPH_TICKS + " ticks");
        if (BileLob.RANGE != 18)
            problems.add("range should be 18");
        helper.runAfterDelay(4, () -> {
            float[] hp = { inside.getHealth(), outside.getHealth(), friend.getHealth() };
            // the pool function itself: exactly the units inside the ring, once
            int hit = BileLob.burn(level, abom, owner, new Vec3(inside.getX(), inside.getY(), inside.getZ()));
            if (hit != 1)
                problems.add("one burn hurt " + hit + " units, expected 1");
            if (Math.abs((hp[0] - inside.getHealth()) - BileLob.POOL_DAMAGE) > 1.5f)
                problems.add("a burn took " + (hp[0] - inside.getHealth()) + ", expected " + BileLob.POOL_DAMAGE);
            inside.setHealth(900);
            hp[0] = 900;
            bile.use(level, abom, inside.blockPosition().below());
            if (bile.isOffCooldown(abom))
                problems.add("Bile Lob did not go on cooldown");
            helper.runAfterDelay(BileLob.TELEGRAPH_TICKS / 2, () -> {
                if (inside.getHealth() != hp[0])
                    problems.add("the enemy was hurt during the telegraph");
            });
            helper.runAfterDelay(BileLob.TELEGRAPH_TICKS + BileLob.POOL_TICKS + 10, () -> {
                float dIn = hp[0] - inside.getHealth();
                float full = BileLob.POOL_DAMAGE * (BileLob.POOL_TICKS / BileLob.TICK_EVERY);
                if (dIn < BileLob.POOL_DAMAGE * 5 - 1.5f || dIn > full + 1.5f)
                    problems.add("the pool dealt " + dIn + " over its life, expected about " + full);
                if (outside.getHealth() != hp[1])
                    problems.add("an enemy outside the ring was hurt");
                if (friend.getHealth() != hp[2])
                    problems.add("a friendly unit in the ring was hurt");
                for (LivingEntity e : List.<LivingEntity>of(abom, inside, outside, friend))
                    e.discard();
                T3RoleGameTests.finish(helper, problems);
            });
        });
    }

    /**
     * AC-5 (Carrion Burst): 20% of max HP is paid, six skeletons rise for the owner (the Bone Dragon's risen), and a
     * second burst raises only what the shared cap of {@link DragonRaiseServerEvents#MAX_RISEN} allows.
     */
    @GameTest(template = ARENA, timeoutTicks = 60)
    public static void plague_abomination_carrion_burst_costs_hp_and_raises_skeletons(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = owner(helper, "carrion");
        var abom = put(helper, EntityRegistrar.PLAGUE_ABOMINATION_UNIT.get(), owner, 8.5, 8.5);
        if (abom == null) {
            helper.fail("could not create the Abomination");
            return;
        }
        CarrionBurst burst = (CarrionBurst) abom.getAbilities().get().stream().filter(a -> a instanceof CarrionBurst).findFirst().orElse(null);
        if (burst == null) {
            helper.fail("the Abomination has no Carrion Burst");
            return;
        }
        List<String> problems = new ArrayList<>();
        ResearchServerEvents.addCheat(owner, "foodforthought");   // population room, as the Bone Dragon's rise test
        List<LivingEntity> risen = new ArrayList<>();
        try {
            float before = abom.getHealth();
            burst.use(level, abom, abom.blockPosition());
            DragonRaiseServerEvents.risenOf(owner, risen);
            float paid = before - abom.getHealth();
            if (Math.abs(paid - PlagueAbominationUnit.maxHealth * CarrionBurst.SACRIFICE) > 1f)
                problems.add("the burst cost " + paid + " HP, expected " + PlagueAbominationUnit.maxHealth * CarrionBurst.SACRIFICE);
            if (risen.size() != CarrionBurst.COUNT)
                problems.add(risen.size() + " skeletons rose, expected " + CarrionBurst.COUNT);
            if (burst.isOffCooldown(abom))
                problems.add("Carrion Burst did not go on cooldown");
            int second = CarrionBurst.burst(level, abom, owner);
            if (second != DragonRaiseServerEvents.MAX_RISEN - CarrionBurst.COUNT)
                problems.add("a second burst raised " + second + " past the shared cap, expected "
                    + (DragonRaiseServerEvents.MAX_RISEN - CarrionBurst.COUNT));
            // the sacrifice never kills it
            abom.setHealth(5f);
            CarrionBurst.burst(level, abom, owner);
            if (!abom.isAlive() || abom.getHealth() < 1f)
                problems.add("the sacrifice killed the Abomination");
        } finally {
            ResearchServerEvents.removeCheat(owner, "foodforthought");
            risen.clear();
            DragonRaiseServerEvents.risenOf(owner, risen);
            for (LivingEntity e : risen)
                e.discard();
            abom.discard();
        }
        T3RoleGameTests.finish(helper, problems);
    }

    /** AC-6: the numbers keep the T3 rules: its own HP total, the same price as the Bone Dragon, melee, never pushed. */
    @GameTest(template = ARENA, timeoutTicks = 40)
    public static void plague_abomination_numbers_keep_the_t3_rules(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        float[] others = { com.solegendary.reignofnether.unit.units.villagers.SunColossusUnit.maxHealth,
            BoneDragonUnit.maxHealth, com.solegendary.reignofnether.unit.units.piglins.WarMammothUnit.maxHealth,
            com.solegendary.reignofnether.unit.units.piglins.MagmaHurlerUnit.maxHealth,
            com.solegendary.reignofnether.unit.units.verdant.WorldTreeWalkerUnit.maxHealth,
            com.solegendary.reignofnether.unit.units.tide.LeviathanUnit.maxHealth,
            com.solegendary.reignofnether.unit.units.storm.CelestialDragonUnit.maxHealth,
            com.solegendary.reignofnether.unit.units.brass.IronLeviathanEngineUnit.maxHealth };
        for (float hp : others)
            if (hp == PlagueAbominationUnit.maxHealth)
                problems.add("the Abomination shares " + hp + " HP with another T3");
        var first = ProductionItems.BONE_DRAGON.getCost(false, "nobody");
        var second = ProductionItems.GRAVE_T3B.getCost(false, "nobody");
        if (first.metal() != second.metal() || first.energy() != second.energy() || first.population != second.population)
            problems.add("the Abomination's price differs from the Bone Dragon's");
        if (PlagueAbominationUnit.movementSpeed >= 0.22f)
            problems.add("the Abomination should be slow (under a heavy line's 0.22)");
        PlagueAbominationUnit u = EntityRegistrar.PLAGUE_ABOMINATION_UNIT.get().create(helper.getLevel());
        if (u == null)
            problems.add("could not create the Abomination");
        else {
            if (u.isPushable())
                problems.add("a titan must not be pushed by other bodies");
            if (((Unit) u).getCost() != com.solegendary.reignofnether.resources.ResourceCosts.GRAVE_T3B)
                problems.add("the Abomination's cost entry is not GRAVE_T3B");
            long lobs = u.getAbilities().get().stream().filter(a -> a instanceof BileLob).count();
            long bursts = u.getAbilities().get().stream().filter(a -> a instanceof CarrionBurst).count();
            u.updateAbilityButtons();   // a refresh must not duplicate the abilities
            if (lobs != 1 || bursts != 1
                    || u.getAbilities().get().stream().filter(a -> a instanceof BileLob || a instanceof CarrionBurst).count() != 2)
                problems.add("the Abomination should carry exactly one Bile Lob and one Carrion Burst");
            u.discard();
        }
        T3RoleGameTests.finish(helper, problems);
    }
}
