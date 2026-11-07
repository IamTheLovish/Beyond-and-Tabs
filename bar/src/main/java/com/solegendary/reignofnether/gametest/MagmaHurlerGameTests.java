package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.ability.abilities.Eruption;
import com.solegendary.reignofnether.building.Buildings;
import com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement;
import com.solegendary.reignofnether.building.production.ProductionItems;
import com.solegendary.reignofnether.keybinds.Keybindings;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.unit.T3Cap;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.units.piglins.MagmaHurlerUnit;
import com.solegendary.reignofnether.unit.units.piglins.WarMammothUnit;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
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
 * Slice T3b-2 of the second Tier 3 (second-t3-design.md): the Horde's <b>Magma Hurler</b>. One test per acceptance
 * criterion, all un-hosted and inside the test's own arena: trained from slot 5 (T) of the Fortress as its own type and
 * capped one alive; Molten Hurl splashes, burns and has a dead zone; Slag Skin burns melee attackers and nothing else;
 * Eruption is telegraphed, then hurts and lifts enemies in the ring and spares friends and the far; the numbers keep the
 * T3 rules (distinct HP, the same price as the Mammoth).
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class MagmaHurlerGameTests {

    static final String ARENA = SkirmishGameTests.ARENA;

    static String owner(GameTestHelper helper, String tag) {
        return "gametest_hurler_" + tag + "_" + helper.absolutePos(BlockPos.ZERO).asLong();
    }

    /** A body that survives any one hit of the test (the real HP is tested elsewhere). */
    static <T extends Mob> T tough(T m) {
        m.getAttribute(Attributes.MAX_HEALTH).setBaseValue(900);
        m.setHealth(900);
        return m;
    }

    @SuppressWarnings("unchecked")
    static <T extends Mob> T put(GameTestHelper helper, net.minecraft.world.entity.EntityType<T> type, String owner, double x, double z) {
        Mob m = BigUnitGameTests.spawn(helper, type, owner, x, z);
        return (T) m;
    }

    /** AC-1: trained from slot 5 (T) of the Fortress as a Magma Hurler (no placeholder tag), under the Mammoth's price. */
    @GameTest(template = ARENA, timeoutTicks = 60)
    public static void magma_hurler_is_trained_from_slot_t_as_its_own_unit(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        ServerLevel level = helper.getLevel();
        String owner = owner(helper, "train");
        if (Buildings.FORTRESS.productions.forHotkey(Keybindings.abilitySlot5) != ProductionItems.HORDE_T3B)
            problems.add("slot 5 (T) of the Fortress is not the second T3");
        if (!MagmaHurlerUnit.ITEM_NAME.equals(ProductionItems.HORDE_T3B.getItemName()))
            problems.add("the second Horde T3 is still named " + ProductionItems.HORDE_T3B.getItemName());
        ProductionPlacement pp = SecondT3FrameGameTests.placement(helper, Buildings.FORTRESS, owner);
        if (pp == null) {
            T3RoleGameTests.finish(helper, List.of("no Fortress placement"));
            return;
        }
        ProductionItems.HORDE_T3B.onComplete.accept(level, pp);
        helper.runAfterDelay(3, () -> {
            AABB all = new AABB(helper.absolutePos(BlockPos.ZERO)).inflate(80);
            List<MagmaHurlerUnit> made = level.getEntitiesOfClass(MagmaHurlerUnit.class, all, u -> owner.equals(u.getOwnerName()));
            if (made.size() != 1)
                problems.add("training the second Horde T3 made " + made.size() + " Magma Hurlers");
            else {
                if (made.get(0).getPersistentData().getBoolean(T3Cap.TAG_SECOND))
                    problems.add("a real unit must not carry the placeholder tag");
                made.get(0).discard();
            }
            if (level.getEntitiesOfClass(WarMammothUnit.class, all, u -> owner.equals(u.getOwnerName())).size() != 0)
                problems.add("the second Horde T3 trained a War Mammoth body");
            T3RoleGameTests.finish(helper, problems);
        });
    }

    /** AC-2: one alive of each type: a living Hurler blocks a second Hurler, not a Mammoth; and the other way round. */
    @GameTest(template = ARENA, timeoutTicks = 60)
    public static void magma_hurler_cap_is_one_alive_beside_the_mammoth(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        String owner = owner(helper, "cap");
        SecondT3FrameGameTests.grantTiers(owner);
        ProductionPlacement pp = SecondT3FrameGameTests.placement(helper, Buildings.FORTRESS, owner);
        if (pp == null) {
            T3RoleGameTests.finish(helper, List.of("no Fortress placement"));
            return;
        }
        if (T3Cap.productionError(pp, ProductionItems.HORDE_T3B) != null)
            problems.add("a Hurler was refused with none alive");
        var hurler = put(helper, EntityRegistrar.MAGMA_HURLER_UNIT.get(), owner, 3.5, 8.5);
        if (hurler == null) {
            T3RoleGameTests.finish(helper, List.of("could not create the Hurler"));
            return;
        }
        if (!T3Cap.LIMIT_MSG.equals(T3Cap.productionError(pp, ProductionItems.HORDE_T3B)))
            problems.add("a second Hurler was not refused while one lives");
        if (T3Cap.productionError(pp, ProductionItems.WAR_MAMMOTH) != null)
            problems.add("a living Hurler blocked the Mammoth");
        hurler.discard();
        var mammoth = put(helper, EntityRegistrar.WAR_MAMMOTH_UNIT.get(), owner, 3.5, 8.5);
        if (mammoth != null) {
            if (T3Cap.productionError(pp, ProductionItems.HORDE_T3B) != null)
                problems.add("a living Mammoth blocked the Hurler");
            if (!T3Cap.LIMIT_MSG.equals(T3Cap.productionError(pp, ProductionItems.WAR_MAMMOTH)))
                problems.add("a second Mammoth was not refused while one lives");
            mammoth.discard();
        }
        T3RoleGameTests.finish(helper, problems);
    }

    /**
     * AC-3 (Molten Hurl): the boulder bursts for the full damage on the centre (less at the rim), sets every enemy in
     * {@link MagmaHurlerUnit#HURL_SPLASH_RADIUS} alight, spares a friend and an enemy outside the ring, and cannot fire
     * inside its dead zone.
     */
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void magma_hurler_hurl_splashes_and_burns(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = owner(helper, "hurl"), foe = owner(helper, "hurlfoe");
        var hurler = put(helper, EntityRegistrar.MAGMA_HURLER_UNIT.get(), owner, 2.5, 8.5);
        var centre = tough(put(helper, EntityRegistrar.VINDICATOR_UNIT.get(), foe, 14.5, 8.5));
        var edge = tough(put(helper, EntityRegistrar.VINDICATOR_UNIT.get(), foe, 16.5, 8.5));
        var far = tough(put(helper, EntityRegistrar.VINDICATOR_UNIT.get(), foe, 19.5, 8.5));
        var friend = tough(put(helper, EntityRegistrar.VINDICATOR_UNIT.get(), owner, 14.5, 10.0));
        if (hurler == null || centre == null || edge == null || far == null || friend == null) {
            helper.fail("could not create the hurl units");
            return;
        }
        for (Mob m : List.of(centre, edge, far, friend))
            if (m instanceof com.solegendary.reignofnether.unit.interfaces.AttackerUnit)
                m.setNoAi(true);
        helper.runAfterDelay(4, () -> {
            List<String> problems = new ArrayList<>();
            // dead zone: a target 3 blocks away is not fired on
            if (hurler.hurl(hurler.getX() + 3, hurler.getY(), hurler.getZ()) != null)
                problems.add("fired at a target inside the dead zone (" + MagmaHurlerUnit.MIN_RANGE + ")");
            var shell = hurler.hurl(centre.getX(), centre.getY(), centre.getZ());
            if (shell == null) {
                T3RoleGameTests.finish(helper, List.of("did not fire at a target in range"));
                return;
            }
            if (shell.getSplashRadius() != MagmaHurlerUnit.HURL_SPLASH_RADIUS || shell.getBurnTicks() != MagmaHurlerUnit.HURL_BURN_TICKS)
                problems.add("the shell is not molten: radius " + shell.getSplashRadius() + ", burn " + shell.getBurnTicks());
            if (shell.getDamage() != MagmaHurlerUnit.attackDamage)
                problems.add("the shell carries " + shell.getDamage() + " damage, not " + MagmaHurlerUnit.attackDamage);
            // land it on the centre (the flight itself is BombardShell's, tested in ArtilleryGameTests)
            shell.setPos(centre.getX(), centre.getY(), centre.getZ());
            float cHp = centre.getHealth(), eHp = edge.getHealth(), fHp = far.getHealth(), frHp = friend.getHealth();
            int hit = shell.burst();
            if (hit != 2)
                problems.add("the burst hurt " + hit + " units, expected the centre and the edge (2)");
            float cLoss = cHp - centre.getHealth(), eLoss = eHp - edge.getHealth();
            if (Math.abs(cLoss - MagmaHurlerUnit.attackDamage) > 1.5f)
                problems.add("the centre took " + cLoss + ", expected about " + MagmaHurlerUnit.attackDamage);
            if (eLoss <= 0 || eLoss >= cLoss)
                problems.add("the edge should take less than the centre: " + eLoss + " vs " + cLoss);
            if (centre.getRemainingFireTicks() < MagmaHurlerUnit.HURL_BURN_TICKS || edge.getRemainingFireTicks() < MagmaHurlerUnit.HURL_BURN_TICKS)
                problems.add("enemies in the splash were not set alight for " + MagmaHurlerUnit.HURL_BURN_TICKS + " ticks");
            if (far.getHealth() != fHp || far.getRemainingFireTicks() > 0)
                problems.add("an enemy outside the splash was hurt or burned");
            if (friend.getHealth() != frHp || friend.getRemainingFireTicks() > 0)
                problems.add("the splash hurt or burned a friendly unit");
            for (LivingEntity e : List.<LivingEntity>of(hurler, centre, edge, far, friend))
                e.discard();
            T3RoleGameTests.finish(helper, problems);
        });
    }

    /**
     * AC-4 (Slag Skin): a melee attacker burns for {@link MagmaHurlerUnit#SLAG_SKIN_DAMAGE} a hit; a ranged attacker does
     * not; fire does nothing to the Hurler.
     */
    @GameTest(template = ARENA, timeoutTicks = 60)
    public static void magma_hurler_slag_skin_burns_melee_attackers_only(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = owner(helper, "slag"), foe = owner(helper, "slagfoe");
        var hurler = tough(put(helper, EntityRegistrar.MAGMA_HURLER_UNIT.get(), owner, 4.5, 8.5));
        var melee = tough(put(helper, EntityRegistrar.VINDICATOR_UNIT.get(), foe, 6.5, 8.5));
        var ranged = tough(put(helper, EntityRegistrar.HEADHUNTER_UNIT.get(), foe, 6.5, 11.5));
        if (hurler == null || melee == null || ranged == null) {
            helper.fail("could not create the slag skin units");
            return;
        }
        melee.setNoAi(true);
        ranged.setNoAi(true);
        helper.runAfterDelay(3, () -> {
            List<String> problems = new ArrayList<>();
            float mHp = melee.getHealth(), rHp = ranged.getHealth();
            hurler.hurt(level.damageSources().mobAttack(melee), 5f);
            float burned = mHp - melee.getHealth();
            if (Math.abs(burned - MagmaHurlerUnit.SLAG_SKIN_DAMAGE) > 0.01f)
                problems.add("a melee attacker took " + burned + " from Slag Skin, expected " + MagmaHurlerUnit.SLAG_SKIN_DAMAGE);
            hurler.hurt(level.damageSources().mobAttack(ranged), 5f);
            if (ranged.getHealth() != rHp)
                problems.add("a ranged attacker burned from Slag Skin");
            float afterBlows = hurler.getHealth();
            hurler.hurt(level.damageSources().inFire(), 40f);
            hurler.hurt(level.damageSources().onFire(), 40f);
            hurler.hurt(level.damageSources().lava(), 40f);
            if (hurler.getHealth() < afterBlows)
                problems.add("fire hurt the Hurler: " + afterBlows + " -> " + hurler.getHealth());
            for (LivingEntity e : List.<LivingEntity>of(hurler, melee, ranged))
                e.discard();
            T3RoleGameTests.finish(helper, problems);
        });
    }

    /**
     * AC-5 (Eruption): casting marks a ring and puts the ability on cooldown, hurts nobody for the telegraph (1.2 s, under
     * the 1.5 s cap), then deals {@link Eruption#DAMAGE} and a knock-up to enemies inside {@link Eruption#RADIUS} - not to a
     * friend, not to an enemy outside the ring. No stun is applied.
     */
    @GameTest(template = ARENA, timeoutTicks = 120)
    public static void magma_hurler_eruption_telegraphs_then_hurts_and_lifts(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = owner(helper, "erupt"), foe = owner(helper, "eruptfoe");
        var hurler = put(helper, EntityRegistrar.MAGMA_HURLER_UNIT.get(), owner, 2.5, 8.5);
        var inside = tough(put(helper, EntityRegistrar.VINDICATOR_UNIT.get(), foe, 14.5, 8.5));
        var rim = tough(put(helper, EntityRegistrar.VINDICATOR_UNIT.get(), foe, 14.5, 12.0));
        var outside = tough(put(helper, EntityRegistrar.VINDICATOR_UNIT.get(), foe, 14.5, 15.5));
        var friend = tough(put(helper, EntityRegistrar.VINDICATOR_UNIT.get(), owner, 12.5, 8.5));
        if (hurler == null || inside == null || rim == null || outside == null || friend == null) {
            helper.fail("could not create the eruption units");
            return;
        }
        for (Mob m : List.of(inside, rim, outside, friend))
            m.setNoAi(true);
        Eruption eruption = (Eruption) hurler.getAbilities().get().stream().filter(a -> a instanceof Eruption).findFirst().orElse(null);
        if (eruption == null) {
            helper.fail("the Hurler has no Eruption");
            return;
        }
        List<String> problems = new ArrayList<>();
        if (Eruption.TELEGRAPH_TICKS < 10 || Eruption.TELEGRAPH_TICKS > 30)
            problems.add("the telegraph should be readable but under 1.5 s: " + Eruption.TELEGRAPH_TICKS + " ticks");
        helper.runAfterDelay(4, () -> {
            BlockPos aim = inside.blockPosition().below();
            float[] hp = { inside.getHealth(), rim.getHealth(), outside.getHealth(), friend.getHealth() };
            double y0 = inside.getY();
            eruption.use(level, hurler, aim);
            if (eruption.isOffCooldown(hurler))
                problems.add("Eruption did not go on cooldown");
            helper.runAfterDelay(Eruption.TELEGRAPH_TICKS / 2, () -> {
                if (inside.getHealth() != hp[0])
                    problems.add("the enemy was hurt during the telegraph");
            });
            helper.runAfterDelay(Eruption.TELEGRAPH_TICKS + 2, () -> {
                float dIn = hp[0] - inside.getHealth(), dRim = hp[1] - rim.getHealth();
                if (Math.abs(dIn - Eruption.DAMAGE) > 1.5f)
                    problems.add("the enemy in the ring took " + dIn + ", expected " + Eruption.DAMAGE);
                if (dRim <= 0)
                    problems.add("the enemy at the rim (3.5 blocks) took no damage");
                if (inside.getDeltaMovement().y <= 0.05 && inside.getY() <= y0 + 0.05)
                    problems.add("the enemy in the ring was not knocked up");
                if (outside.getHealth() != hp[2])
                    problems.add("an enemy outside the ring was hurt");
                if (friend.getHealth() != hp[3])
                    problems.add("a friendly unit in the ring was hurt");
                if (inside.hasEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN)
                        || inside.hasEffect(net.minecraft.world.effect.MobEffects.WEAKNESS))
                    problems.add("Eruption applied a stun/slow effect it should not have");
                for (LivingEntity e : List.<LivingEntity>of(hurler, inside, rim, outside, friend))
                    e.discard();
                T3RoleGameTests.finish(helper, problems);
            });
        });
    }

    /** AC-6: the numbers keep the T3 rules: its own HP total, the same price as the first T3, a dead zone, 24 range. */
    @GameTest(template = ARENA, timeoutTicks = 40)
    public static void magma_hurler_numbers_keep_the_t3_rules(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        float[] others = { com.solegendary.reignofnether.unit.units.villagers.SunColossusUnit.maxHealth,
            com.solegendary.reignofnether.unit.units.monsters.BoneDragonUnit.maxHealth, WarMammothUnit.maxHealth,
            com.solegendary.reignofnether.unit.units.verdant.WorldTreeWalkerUnit.maxHealth,
            com.solegendary.reignofnether.unit.units.tide.LeviathanUnit.maxHealth,
            com.solegendary.reignofnether.unit.units.storm.CelestialDragonUnit.maxHealth,
            com.solegendary.reignofnether.unit.units.brass.IronLeviathanEngineUnit.maxHealth };
        for (float hp : others)
            if (hp == MagmaHurlerUnit.maxHealth)
                problems.add("the Hurler shares " + hp + " HP with another T3");
        var first = ProductionItems.WAR_MAMMOTH.getCost(false, "nobody");
        var second = ProductionItems.HORDE_T3B.getCost(false, "nobody");
        if (first.metal() != second.metal() || first.energy() != second.energy() || first.population != second.population)
            problems.add("the Hurler's price differs from the Mammoth's");
        if (MagmaHurlerUnit.attackRange != 24 || MagmaHurlerUnit.MIN_RANGE != 6)
            problems.add("range should be 24 with a dead zone of 6");
        MagmaHurlerUnit h = EntityRegistrar.MAGMA_HURLER_UNIT.get().create(helper.getLevel());
        if (h == null)
            problems.add("could not create the Hurler");
        else {
            if (h.getMinRange() != MagmaHurlerUnit.MIN_RANGE)
                problems.add("the Hurler is not indirect fire with its dead zone");
            if (h.isPushable())
                problems.add("a titan must not be pushed by other bodies");
            if (((Unit) h).getCost() != com.solegendary.reignofnether.resources.ResourceCosts.HORDE_T3B)
                problems.add("the Hurler's cost entry is not HORDE_T3B");
            h.discard();
        }
        T3RoleGameTests.finish(helper, problems);
    }
}
