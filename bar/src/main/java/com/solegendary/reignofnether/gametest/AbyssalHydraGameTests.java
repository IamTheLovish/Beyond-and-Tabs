package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.ability.abilities.BrineMist;
import com.solegendary.reignofnether.blocks.ThicketCover;
import com.solegendary.reignofnether.building.Buildings;
import com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement;
import com.solegendary.reignofnether.building.production.ProductionItems;
import com.solegendary.reignofnether.keybinds.Keybindings;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.tide.FogBankServerEvents;
import com.solegendary.reignofnether.tide.TidepoolServerEvents;
import com.solegendary.reignofnether.tide.TidesServerEvents;
import com.solegendary.reignofnether.unit.T3Cap;
import com.solegendary.reignofnether.unit.goals.AbstractMeleeAttackUnitGoal;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.units.tide.AbyssalHydraUnit;
import com.solegendary.reignofnether.unit.units.tide.LeviathanUnit;
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
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * Slice T3b-6 of the second Tier 3 (second-t3-design.md): the Tidewrought <b>Abyssal Hydra</b>. One test per
 * acceptance criterion, all un-hosted and inside the test's own arena: trained from slot 5 (T) of the Abyssal Shipyard
 * as its own type and capped one alive beside the Leviathan; Three Heads pick three different targets, air included,
 * and a severed head silences one; Brine Mist hides friends and spoils enemy shots for 6 s; Regrow heals 3 HP/s and
 * speeds it in water and on a tidepool, and not on dry land; the numbers keep the T3 rules.
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class AbyssalHydraGameTests {

    static final String ARENA = SkirmishGameTests.ARENA;

    static String owner(GameTestHelper helper, String tag) {
        return "gametest_hydra_" + tag + "_" + helper.absolutePos(BlockPos.ZERO).asLong();
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

    /** AC-1: trained from slot 5 (T) of the Abyssal Shipyard as an Abyssal Hydra (no placeholder tag, no Leviathan). */
    @GameTest(template = ARENA, timeoutTicks = 60)
    public static void abyssal_hydra_is_trained_from_slot_t_as_its_own_unit(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        ServerLevel level = helper.getLevel();
        String owner = owner(helper, "train");
        if (Buildings.ABYSSAL_SHIPYARD.productions.forHotkey(Keybindings.abilitySlot5) != ProductionItems.TIDE_T3B)
            problems.add("slot 5 (T) of the Abyssal Shipyard is not the second T3");
        if (!AbyssalHydraUnit.ITEM_NAME.equals(ProductionItems.TIDE_T3B.getItemName()))
            problems.add("the second Tidewrought T3 is still named " + ProductionItems.TIDE_T3B.getItemName());
        ProductionPlacement pp = SecondT3FrameGameTests.placement(helper, Buildings.ABYSSAL_SHIPYARD, owner);
        if (pp == null) {
            T3RoleGameTests.finish(helper, List.of("no Abyssal Shipyard placement"));
            return;
        }
        ProductionItems.TIDE_T3B.onComplete.accept(level, pp);
        helper.runAfterDelay(3, () -> {
            AABB all = new AABB(helper.absolutePos(BlockPos.ZERO)).inflate(80);
            List<AbyssalHydraUnit> made = level.getEntitiesOfClass(AbyssalHydraUnit.class, all, u -> owner.equals(u.getOwnerName()));
            if (made.size() != 1)
                problems.add("training the second Tidewrought T3 made " + made.size() + " Abyssal Hydras");
            else {
                if (made.get(0).getPersistentData().getBoolean(T3Cap.TAG_SECOND))
                    problems.add("a real unit must not carry the placeholder tag");
                made.get(0).discard();
            }
            if (level.getEntitiesOfClass(LeviathanUnit.class, all, u -> owner.equals(u.getOwnerName())).size() != 0)
                problems.add("the second Tidewrought T3 trained a Leviathan body");
            T3RoleGameTests.finish(helper, problems);
        });
    }

    /** AC-2: one alive of each type: a living Hydra blocks a second Hydra, not a Leviathan; and the other way round. */
    @GameTest(template = ARENA, timeoutTicks = 60)
    public static void abyssal_hydra_cap_is_one_alive_beside_the_leviathan(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        String owner = owner(helper, "cap");
        SecondT3FrameGameTests.grantTiers(owner);
        ProductionPlacement pp = SecondT3FrameGameTests.placement(helper, Buildings.ABYSSAL_SHIPYARD, owner);
        if (pp == null) {
            T3RoleGameTests.finish(helper, List.of("no Abyssal Shipyard placement"));
            return;
        }
        if (T3Cap.productionError(pp, ProductionItems.TIDE_T3B) != null)
            problems.add("a Hydra was refused with none alive");
        var hydra = put(helper, EntityRegistrar.ABYSSAL_HYDRA_UNIT.get(), owner, 3.5, 8.5);
        if (hydra == null) {
            T3RoleGameTests.finish(helper, List.of("could not create the Hydra"));
            return;
        }
        if (!T3Cap.LIMIT_MSG.equals(T3Cap.productionError(pp, ProductionItems.TIDE_T3B)))
            problems.add("a second Hydra was not refused while one lives");
        if (T3Cap.productionError(pp, ProductionItems.LEVIATHAN) != null)
            problems.add("a living Hydra blocked the Leviathan");
        hydra.discard();
        var leviathan = put(helper, EntityRegistrar.LEVIATHAN_UNIT.get(), owner, 3.5, 8.5);
        if (leviathan != null) {
            if (T3Cap.productionError(pp, ProductionItems.TIDE_T3B) != null)
                problems.add("a living Leviathan blocked the Hydra");
            if (!T3Cap.LIMIT_MSG.equals(T3Cap.productionError(pp, ProductionItems.LEVIATHAN)))
                problems.add("a second Leviathan was not refused while one lives");
            leviathan.discard();
        }
        T3RoleGameTests.finish(helper, problems);
    }

    /**
     * AC-3 (Three Heads): a ranged unit with no melee goal; with three enemies in range (one a flyer) every one of them
     * is hit by exactly one head ({@link AbyssalHydraUnit#HEAD_DAMAGE} each, three heads fire); with one target all three
     * heads land on it; with more than three enemies only three are hit; an enemy out of range and a friend are never
     * hit; a hit above {@link AbyssalHydraUnit#SEVER_DAMAGE} severs one head (two fire), and it regrows after
     * {@link AbyssalHydraUnit#SEVER_TICKS}.
     */
    @GameTest(template = ARENA, timeoutTicks = 160)
    public static void abyssal_hydra_three_heads_pick_three_targets_including_air(GameTestHelper helper) {
        String owner = owner(helper, "heads"), foe = owner(helper, "headsfoe");
        var h = put(helper, EntityRegistrar.ABYSSAL_HYDRA_UNIT.get(), owner, 0.5, 0.5);
        var friend = tough(put(helper, EntityRegistrar.VINDICATOR_UNIT.get(), owner, 2.5, 0.5));
        var a = tough(put(helper, EntityRegistrar.VINDICATOR_UNIT.get(), foe, 3.5, 7.5));
        var b = tough(put(helper, EntityRegistrar.VINDICATOR_UNIT.get(), foe, 6.5, 7.5));
        var flyer = tough(put(helper, EntityRegistrar.BONE_DRAGON_UNIT.get(), foe, 8.5, 9.5));
        if (h == null || friend == null || a == null || b == null || flyer == null) {
            helper.fail("could not create the heads units");
            return;
        }
        h.setNoAi(true);
        List<Mob> foes = List.of(a, b, flyer);
        for (Mob m : foes)
            m.setNoAi(true);
        friend.setNoAi(true);
        helper.runAfterDelay(3, () -> {
            List<String> problems = new ArrayList<>();
            if (h.getAttackGoal() == null || h.getAttackGoal() instanceof AbstractMeleeAttackUnitGoal)
                problems.add("the Hydra should attack from range, not in melee");
            if (AbyssalHydraUnit.attackRange != 14)
                problems.add("the design's range is 14: " + AbyssalHydraUnit.attackRange);
            float[] before = new float[3];
            for (int i = 0; i < 3; i++)
                before[i] = foes.get(i).getHealth();
            float friendHp = friend.getHealth();
            int fired = h.volley(a, foes::contains);   // only this test's own units can take a head
            if (fired != AbyssalHydraUnit.HEADS)
                problems.add("a volley fired " + fired + " heads, expected " + AbyssalHydraUnit.HEADS);
            for (int i = 0; i < 3; i++) {
                float d = before[i] - foes.get(i).getHealth();
                // the flyer may carry a little magic resist: it must still be hit by its own head
                if (foes.get(i) == flyer ? (d <= 0f || d > AbyssalHydraUnit.HEAD_DAMAGE + 0.01f) : Math.abs(d - AbyssalHydraUnit.HEAD_DAMAGE) > 0.01f)
                    problems.add("target " + i + (foes.get(i) == flyer ? " (the flyer)" : "") + " took " + d
                        + " instead of one head's " + AbyssalHydraUnit.HEAD_DAMAGE);
            }
            if (friend.getHealth() != friendHp)
                problems.add("a friendly unit was hit");

            // one target: all three heads on it
            b.discard();
            flyer.discard();
            float hp = a.getHealth();
            int heads1 = h.volley(a, a::equals);
            float d = hp - a.getHealth();
            if (Math.abs(d - AbyssalHydraUnit.HEAD_DAMAGE * AbyssalHydraUnit.HEADS) > 0.01f)
                problems.add("with one target the three heads dealt " + d + " (" + heads1 + " fired, hp " + hp + " -> " + a.getHealth() + ", up " + h.headsUp() + ")");

            // a severed head: one hit above the line silences one head for 5 s
            h.setHealth(h.getMaxHealth());
            hp = a.getHealth();
            h.hurt(helper.getLevel().damageSources().generic(), AbyssalHydraUnit.SEVER_DAMAGE - 10);
            if (h.headsUp() != AbyssalHydraUnit.HEADS)
                problems.add("a hit under the line severed a head");
            h.invulnerableTime = 0;
            h.hurt(helper.getLevel().damageSources().generic(), AbyssalHydraUnit.SEVER_DAMAGE + 40);
            if (h.headsUp() != AbyssalHydraUnit.HEADS - 1)
                problems.add("a hit over the line left " + h.headsUp() + " heads up, expected " + (AbyssalHydraUnit.HEADS - 1));
            int two = h.volley(a, a::equals);
            if (two != AbyssalHydraUnit.HEADS - 1)
                problems.add("with a head severed " + two + " heads fired");

            // many enemies: only three are hit
            h.severHead(helper.getLevel());   // a second head down: all three must be back after 5 s
            List<Mob> crowd = new ArrayList<>();
            for (int i = 0; i < 6; i++)
                crowd.add(tough(put(helper, EntityRegistrar.VINDICATOR_UNIT.get(), foe, 1.5 + i, 7.5 + (i % 2))));
            a.discard();
            Mob far = tough(put(helper, EntityRegistrar.VINDICATOR_UNIT.get(), foe, 14.5, 14.5));
            if (crowd.contains(null) || far == null) {
                helper.fail("could not create the crowd");
                return;
            }
            crowd.forEach(m -> m.setNoAi(true));
            far.setNoAi(true);
            float farHp = far.getHealth();
            helper.runAfterDelay(AbyssalHydraUnit.SEVER_TICKS + 5, () -> {
                if (h.headsUp() != AbyssalHydraUnit.HEADS)
                    problems.add("the heads did not regrow after 5 s (" + h.headsUp() + " up)");
                float[] cb = new float[crowd.size()];
                for (int i = 0; i < cb.length; i++)
                    cb[i] = crowd.get(i).getHealth();
                h.volley(crowd.get(0), crowd::contains);
                int hit = 0;
                for (int i = 0; i < cb.length; i++)
                    if (crowd.get(i).getHealth() < cb[i])
                        hit++;
                if (hit != AbyssalHydraUnit.HEADS)
                    problems.add("a volley into six enemies hit " + hit + ", expected " + AbyssalHydraUnit.HEADS);
                if (far.getHealth() != farHp)
                    problems.add("an enemy out of range was hit");
                if (!(far.distanceTo(h) > AbyssalHydraUnit.attackRange))
                    problems.add("the far enemy was not really out of range");
                for (LivingEntity e : crowd)
                    e.discard();
                for (LivingEntity e : List.<LivingEntity>of(h, friend, a, far))
                    e.discard();
                T3RoleGameTests.finish(helper, problems);
            });
        });
    }

    /**
     * AC-4 (Brine Mist): casting puts it on cooldown and rolls a mist 8 across for 6 s; a friend inside is hidden from an
     * enemy and visible to its own side, one outside is not; enemy shots at a unit inside miss about half (the fixed
     * roll under the line), at one outside never; after 6 s the mist is gone and the friend is seen again.
     */
    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void abyssal_hydra_brine_mist_hides_friends_and_spoils_enemy_shots(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = owner(helper, "mist"), foe = owner(helper, "mistfoe");
        var h = put(helper, EntityRegistrar.ABYSSAL_HYDRA_UNIT.get(), owner, 3.5, 3.5);
        var inside = tough(put(helper, EntityRegistrar.VINDICATOR_UNIT.get(), owner, 5.5, 3.5));
        var outside = tough(put(helper, EntityRegistrar.VINDICATOR_UNIT.get(), owner, 14.5, 14.5));
        var enemy = tough(put(helper, EntityRegistrar.VINDICATOR_UNIT.get(), foe, 3.5, 14.5));
        if (h == null || inside == null || outside == null || enemy == null) {
            helper.fail("could not create the mist units");
            return;
        }
        for (Mob m : List.of(h, inside, outside, enemy))
            m.setNoAi(true);
        var mist = (BrineMist) h.getAbilities().get().stream().filter(x -> x instanceof BrineMist).findFirst().orElse(null);
        if (mist == null) {
            helper.fail("the Hydra has no Brine Mist");
            return;
        }
        Runnable cleanup = () -> {
            for (LivingEntity e : List.<LivingEntity>of(h, inside, outside, enemy))
                e.discard();
            FogBankServerEvents.clearOwner(owner);
            ThicketCover.refresh(level.getServer());
        };
        helper.runAfterDelay(3, () -> {
            List<String> problems = new ArrayList<>();
            if (ThicketCover.isHidden(inside))
                problems.add("precondition: the friend was hidden before the mist");
            mist.use(level, h, h.blockPosition());
            if (mist.isOffCooldown(h))
                problems.add("Brine Mist did not go on cooldown");
            if (BrineMist.DIAMETER != 8 || BrineMist.LIFETIME_TICKS != 120)
                problems.add("the mist should be 8 across for 6 s: " + BrineMist.DIAMETER + " / " + BrineMist.LIFETIME_TICKS);
            if (!ThicketCover.isHidden(inside) || !inside.isInvisible()
                    || !com.solegendary.reignofnether.unit.Concealment.isHiddenFrom(inside, foe))
                problems.add("a friend inside the mist is not hidden");
            if (com.solegendary.reignofnether.unit.Concealment.isHiddenFrom(inside, owner))
                problems.add("the mist hides the friend from its own side");
            if (ThicketCover.isHidden(outside))
                problems.add("a friend outside the mist is hidden");
            // shots: the roll is the projectile's own fixed one; under the line misses, over it lands, outside never
            if (!FogBankServerEvents.shotSpoiled(level, inside, owner, 0.2f))
                problems.add("a low roll at a unit inside the mist should miss");
            if (FogBankServerEvents.shotSpoiled(level, inside, owner, 0.8f))
                problems.add("a high roll at a unit inside the mist should land");
            if (FogBankServerEvents.shotSpoiled(level, outside, owner, 0.2f))
                problems.add("a shot at a unit outside the mist was spoiled");
            if (FogBankServerEvents.shotSpoiled(level, enemy, foe, 0.2f))
                problems.add("the mist spoiled shots at the ENEMY's units");
            helper.runAfterDelay(BrineMist.LIFETIME_TICKS + 15, () -> {
                if (FogBankServerEvents.shotSpoiled(level, inside, owner, 0.2f))
                    problems.add("the mist still spoils shots after 6 s");
                ThicketCover.refresh(level.getServer());
                if (ThicketCover.isHidden(inside))
                    problems.add("the friend is still hidden after the mist ended");
                cleanup.run();
                T3RoleGameTests.finish(helper, problems);
            });
        });
    }

    /**
     * AC-5 (Regrow): in real water and on a tidepool the Tides pass heals the Hydra 3 HP/s (1.5 per 10-tick pass) and gives
     * it the wet speed bonus (more than any plain Tidewrought unit's); on dry land neither; it cannot drown.
     */
    @GameTest(template = ARENA, timeoutTicks = 80)
    public static void abyssal_hydra_regrows_and_speeds_up_in_water_not_on_land(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = owner(helper, "regrow");
        SkirmishGameTests.openGround(helper, 1, 1, 10, 6);
        BlockPos water = helper.absolutePos(new BlockPos(3, 2, 3));
        BlockPos poolAt = helper.absolutePos(new BlockPos(7, 2, 3));
        level.setBlock(water, Blocks.WATER.defaultBlockState(), 3);
        var pool = TidepoolServerEvents.raise(level, owner, poolAt, 1, 400);
        var inWater = EntityRegistrar.ABYSSAL_HYDRA_UNIT.get().create(level);
        var onPool = EntityRegistrar.ABYSSAL_HYDRA_UNIT.get().create(level);
        var dry = EntityRegistrar.ABYSSAL_HYDRA_UNIT.get().create(level);
        try {
            if (pool == null || inWater == null || onPool == null || dry == null) {
                helper.fail("could not set up the regrow test");
                return;
            }
            List<String> problems = new ArrayList<>();
            for (var u : List.of(inWater, onPool, dry))
                u.setOwnerName(owner);
            inWater.moveTo(water.getX() + 0.5, water.getY(), water.getZ() + 0.5, 0, 0);
            onPool.moveTo(poolAt.getX() + 0.5, poolAt.getY(), poolAt.getZ() + 0.5, 0, 0);
            dry.moveTo(water.getX() + 0.5, water.getY(), water.getZ() + 4.5, 0, 0);
            for (var u : List.of(inWater, onPool, dry))
                u.setHealth(u.getMaxHealth() - 100);
            float base = inWater.getHealth();
            double plain = inWater.getAttributeBaseValue(Attributes.MOVEMENT_SPEED);
            for (var u : List.of(inWater, onPool, dry))
                TidesServerEvents.update(u);
            float perPass = TidesServerEvents.REGEN_PER_PASS + AbyssalHydraUnit.WET_REGEN_BONUS;
            if (Math.abs(perPass * 2f - 3f) > 0.01f)
                problems.add("the wet regen is " + perPass * 2f + " HP/s, expected 3");
            for (var u : List.of(inWater, onPool))
                if (Math.abs(u.getHealth() - (u.getMaxHealth() - 100 + perPass)) > 0.05f)
                    problems.add("a Hydra in the " + (u == inWater ? "water" : "pool") + " healed "
                        + (u.getHealth() - (u.getMaxHealth() - 100)) + " in one pass, expected " + perPass);
            if (dry.getHealth() != dry.getMaxHealth() - 100)
                problems.add("a Hydra on dry land regrew");
            if (!TidesServerEvents.hasSpeedBonus(inWater) || !TidesServerEvents.hasSpeedBonus(onPool))
                problems.add("a Hydra in the water has no speed modifier");
            if (TidesServerEvents.hasSpeedBonus(dry))
                problems.add("a Hydra on dry land got the wet speed");
            double wet = inWater.getAttributeValue(Attributes.MOVEMENT_SPEED);
            double expect = plain * (1 + TidesServerEvents.SPEED_BONUS + AbyssalHydraUnit.WET_SPEED_BONUS);
            if (Math.abs(wet - expect) > 0.005)
                problems.add("wet speed is " + wet + ", expected " + expect);
            if (wet < plain * 1.9)
                problems.add("the wet Hydra is not about twice as fast");
            if (!inWater.canBreatheUnderwater())
                problems.add("the Hydra drowns");
            if (inWater.hurt(level.damageSources().drown(), 50f))
                problems.add("drowning hurt the Hydra");
            if (Math.abs(inWater.getHealth() - (base + perPass)) > 0.05f)
                problems.add("drowning changed the Hydra's HP");
            T3RoleGameTests.finish(helper, problems);
        } finally {
            TidepoolServerEvents.clearOwner(owner);
            level.setBlock(water, Blocks.AIR.defaultBlockState(), 3);
            for (var u : List.of(inWater, onPool, dry))
                if (u != null)
                    u.discard();
        }
    }

    /** AC-6: the numbers keep the T3 rules: its own HP total, the same price as the Leviathan, below the anchor's damage, siege 1.5x, never pushed. */
    @GameTest(template = ARENA, timeoutTicks = 40)
    public static void abyssal_hydra_numbers_keep_the_t3_rules(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        float[] others = { SunColossusUnit.maxHealth,
            com.solegendary.reignofnether.unit.units.monsters.BoneDragonUnit.maxHealth,
            com.solegendary.reignofnether.unit.units.monsters.PlagueAbominationUnit.maxHealth,
            com.solegendary.reignofnether.unit.units.piglins.WarMammothUnit.maxHealth,
            com.solegendary.reignofnether.unit.units.piglins.MagmaHurlerUnit.maxHealth,
            com.solegendary.reignofnether.unit.units.villagers.CathedralWardenUnit.maxHealth,
            com.solegendary.reignofnether.unit.units.verdant.WorldTreeWalkerUnit.maxHealth,
            LeviathanUnit.maxHealth,
            com.solegendary.reignofnether.unit.units.storm.CelestialDragonUnit.maxHealth,
            com.solegendary.reignofnether.unit.units.storm.JadeSentinelUnit.maxHealth,
            com.solegendary.reignofnether.unit.units.brass.IronLeviathanEngineUnit.maxHealth };
        for (float hp : others)
            if (hp == AbyssalHydraUnit.maxHealth)
                problems.add("the Hydra shares " + hp + " HP with another T3");
        for (float taken : new float[] { 800, 850 })   // kept for the flyers
            if (taken == AbyssalHydraUnit.maxHealth)
                problems.add("the Hydra took an HP total kept for the flyers");
        var first = ProductionItems.LEVIATHAN.getCost(false, "nobody");
        var second = ProductionItems.TIDE_T3B.getCost(false, "nobody");
        if (first.metal() != second.metal() || first.energy() != second.energy() || first.population != second.population)
            problems.add("the Hydra's price differs from the Leviathan's");
        if (AbyssalHydraUnit.attackDamage * AbyssalHydraUnit.attacksPerSecond
                >= SunColossusUnit.attackDamage * SunColossusUnit.attacksPerSecond)
            problems.add("a spread shooter should not out-damage the anchor");
        if (AbyssalHydraUnit.SIEGE_MULTIPLIER != 1.5f)
            problems.add("the design's siege multiplier is 1.5");
        AbyssalHydraUnit u = EntityRegistrar.ABYSSAL_HYDRA_UNIT.get().create(helper.getLevel());
        if (u == null)
            problems.add("could not create the Hydra");
        else {
            if (u.isPushable())
                problems.add("a titan must not be pushed by other bodies");
            if (((Unit) u).getCost() != com.solegendary.reignofnether.resources.ResourceCosts.TIDE_T3B)
                problems.add("the Hydra's cost entry is not TIDE_T3B");
            if (!TidesServerEvents.isTidal(u))
                problems.add("the Hydra is not a Tidewrought unit");
            u.updateAbilityButtons();   // a refresh must not duplicate the abilities
            var list = u.getAbilities().get();
            if (list.stream().filter(a -> a instanceof BrineMist).count() != 1)
                problems.add("the Hydra should carry exactly one Brine Mist");
            u.discard();
        }
        T3RoleGameTests.finish(helper, problems);
    }
}
