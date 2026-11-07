package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.ability.abilities.GroundSlam;
import com.solegendary.reignofnether.ability.abilities.MirrorPlate;
import com.solegendary.reignofnether.ability.abilities.Thunderclap;
import com.solegendary.reignofnether.building.Buildings;
import com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement;
import com.solegendary.reignofnether.building.production.ProductionItems;
import com.solegendary.reignofnether.keybinds.Keybindings;
import com.solegendary.reignofnether.registrars.AttributeRegistrar;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.registrars.MobEffectRegistrar;
import com.solegendary.reignofnether.unit.T3Cap;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.units.storm.CelestialDragonUnit;
import com.solegendary.reignofnether.unit.units.storm.EarthshakerUnit;
import com.solegendary.reignofnether.unit.units.storm.JadeSentinelUnit;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * Slice T3b-4 of the second Tier 3 (second-t3-design.md): the Stormcallers' <b>Jade Sentinel</b>. One test per
 * acceptance criterion, all un-hosted and inside the test's own arena: trained from slot 5 (T) of Heaven's Gate as its
 * own type and capped one alive beside the Celestial Dragon; Mirror Plate turns a frontal arrow back on its shooter
 * and lets shots from behind (and after the plate drops) through; Thunderclap is telegraphed, then hurts and stuns
 * enemies in the ring and spares friends and the far; Ley Anchor lends friends in range 10% ranged resist and takes it
 * back when they leave or it falls; the numbers keep the T3 rules.
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class JadeSentinelGameTests {

    static final String ARENA = SkirmishGameTests.ARENA;

    static String owner(GameTestHelper helper, String tag) {
        return "gametest_sentinel_" + tag + "_" + helper.absolutePos(BlockPos.ZERO).asLong();
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

    /** AC-1: trained from slot 5 (T) of Heaven's Gate as a Jade Sentinel (no placeholder tag, no Celestial Dragon). */
    @GameTest(template = ARENA, timeoutTicks = 60)
    public static void jade_sentinel_is_trained_from_slot_t_as_its_own_unit(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        ServerLevel level = helper.getLevel();
        String owner = owner(helper, "train");
        if (Buildings.HEAVENS_GATE.productions.forHotkey(Keybindings.abilitySlot5) != ProductionItems.STORM_T3B)
            problems.add("slot 5 (T) of Heaven's Gate is not the second T3");
        if (!JadeSentinelUnit.ITEM_NAME.equals(ProductionItems.STORM_T3B.getItemName()))
            problems.add("the second Stormcallers T3 is still named " + ProductionItems.STORM_T3B.getItemName());
        ProductionPlacement pp = SecondT3FrameGameTests.placement(helper, Buildings.HEAVENS_GATE, owner);
        if (pp == null) {
            T3RoleGameTests.finish(helper, List.of("no Heaven's Gate placement"));
            return;
        }
        ProductionItems.STORM_T3B.onComplete.accept(level, pp);
        helper.runAfterDelay(3, () -> {
            AABB all = new AABB(helper.absolutePos(BlockPos.ZERO)).inflate(80);
            List<JadeSentinelUnit> made = level.getEntitiesOfClass(JadeSentinelUnit.class, all, u -> owner.equals(u.getOwnerName()));
            if (made.size() != 1)
                problems.add("training the second Stormcallers T3 made " + made.size() + " Jade Sentinels");
            else {
                if (made.get(0).getPersistentData().getBoolean(T3Cap.TAG_SECOND))
                    problems.add("a real unit must not carry the placeholder tag");
                made.get(0).discard();
            }
            if (level.getEntitiesOfClass(CelestialDragonUnit.class, all, u -> owner.equals(u.getOwnerName())).size() != 0)
                problems.add("the second Stormcallers T3 trained a Celestial Dragon body");
            T3RoleGameTests.finish(helper, problems);
        });
    }

    /** AC-2: one alive of each type: a living Sentinel blocks a second Sentinel, not a Celestial Dragon; and the other way round. */
    @GameTest(template = ARENA, timeoutTicks = 60)
    public static void jade_sentinel_cap_is_one_alive_beside_the_celestial_dragon(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        String owner = owner(helper, "cap");
        SecondT3FrameGameTests.grantTiers(owner);
        ProductionPlacement pp = SecondT3FrameGameTests.placement(helper, Buildings.HEAVENS_GATE, owner);
        if (pp == null) {
            T3RoleGameTests.finish(helper, List.of("no Heaven's Gate placement"));
            return;
        }
        if (T3Cap.productionError(pp, ProductionItems.STORM_T3B) != null)
            problems.add("a Sentinel was refused with none alive");
        var sentinel = put(helper, EntityRegistrar.JADE_SENTINEL_UNIT.get(), owner, 3.5, 8.5);
        if (sentinel == null) {
            T3RoleGameTests.finish(helper, List.of("could not create the Sentinel"));
            return;
        }
        if (!T3Cap.LIMIT_MSG.equals(T3Cap.productionError(pp, ProductionItems.STORM_T3B)))
            problems.add("a second Sentinel was not refused while one lives");
        if (T3Cap.productionError(pp, ProductionItems.CELESTIAL_DRAGON) != null)
            problems.add("a living Sentinel blocked the Celestial Dragon");
        sentinel.discard();
        var dragon = put(helper, EntityRegistrar.CELESTIAL_DRAGON_UNIT.get(), owner, 3.5, 8.5);
        if (dragon != null) {
            if (T3Cap.productionError(pp, ProductionItems.STORM_T3B) != null)
                problems.add("a living Celestial Dragon blocked the Sentinel");
            if (!T3Cap.LIMIT_MSG.equals(T3Cap.productionError(pp, ProductionItems.CELESTIAL_DRAGON)))
                problems.add("a second Celestial Dragon was not refused while one lives");
            dragon.discard();
        }
        T3RoleGameTests.finish(helper, problems);
    }

    /** An arrow of the shooter's, parked {@code dz} in front of (+) or behind (-) the Sentinel, and the hit it would make. */
    static DamageSource arrowFrom(ServerLevel level, LivingEntity shooter, JadeSentinelUnit s, double dz) {
        Arrow arrow = new Arrow(level, shooter);
        arrow.setPos(s.getX(), s.getY() + 1.5, s.getZ() + dz);
        s.invulnerableTime = 0;   // a hit a moment ago must not make this one a no-op
        return level.damageSources().arrow(arrow, shooter);
    }

    /**
     * AC-3 (Mirror Plate): with the plate down an arrow from the front hurts; raised, a frontal arrow does no damage and
     * a fresh arrow owned by the Sentinel flies back at the shooter; an arrow from behind still hurts; the plate slows
     * it, goes on cooldown and drops after {@link MirrorPlate#DURATION_TICKS}, after which a frontal arrow hurts again.
     */
    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void jade_sentinel_mirror_plate_reflects_frontal_arrows(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = owner(helper, "mirror"), foe = owner(helper, "mirrorfoe");
        var s = put(helper, EntityRegistrar.JADE_SENTINEL_UNIT.get(), owner, 8.5, 6.5);
        var archer = tough(put(helper, EntityRegistrar.VINDICATOR_UNIT.get(), foe, 8.5, 14.5));
        if (s == null || archer == null) {
            helper.fail("could not create the mirror units");
            return;
        }
        s.setNoAi(true);
        archer.setNoAi(true);
        com.solegendary.reignofnether.ability.AutocastState.setAll(s, false);
        MirrorPlate plate = (MirrorPlate) s.getAbilities().get().stream().filter(a -> a instanceof MirrorPlate).findFirst().orElse(null);
        if (plate == null) {
            helper.fail("the Sentinel has no Mirror Plate");
            return;
        }
        helper.runAfterDelay(3, () -> {
            List<String> problems = new ArrayList<>();
            s.yBodyRot = 0;   // facing +z: the archer is in front
            AABB zone = new AABB(s.blockPosition()).inflate(30);
            float hp = s.getHealth();
            // plate down: an arrow from the front lands
            s.hurt(arrowFrom(level, archer, s, 3), 20f);
            if (s.getHealth() >= hp)
                problems.add("with the plate down a frontal arrow did no damage");
            s.setHealth(hp);
            if (s.mirrorActive())
                problems.add("the plate reads as raised before it was cast");
            plate.use(level, s, s.blockPosition());
            if (plate.isOffCooldown(s))
                problems.add("Mirror Plate did not go on cooldown");
            if (!s.mirrorActive())
                problems.add("the plate is not raised after the cast");
            if (!s.hasEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN))
                problems.add("the Sentinel does not walk slower behind the plate");
            Arrow shot = new Arrow(level, archer);
            shot.setPos(s.getX(), s.getY() + 1.5, s.getZ() + 3);
            level.addFreshEntity(shot);
            boolean hurt = s.hurt(level.damageSources().arrow(shot, archer), 20f);
            if (hurt || s.getHealth() < hp)
                problems.add("a frontal arrow hurt the Sentinel behind its plate");
            if (!shot.isRemoved())
                problems.add("the reflected arrow itself was not taken out of the air");
            List<Arrow> back = level.getEntitiesOfClass(Arrow.class, zone, a -> a != shot && a.getOwner() == s);
            if (back.size() != 1)
                problems.add(back.size() + " arrows were sent back, expected 1");
            else if (back.get(0).getDeltaMovement().z <= 0.3)
                problems.add("the reflected arrow does not fly toward the shooter (dz " + back.get(0).getDeltaMovement().z + ")");
            back.forEach(Arrow::discard);
            // from behind the plate does nothing
            s.setHealth(hp);
            s.hurt(arrowFrom(level, archer, s, -3), 20f);
            if (s.getHealth() >= hp)
                problems.add("an arrow from behind was stopped by the plate");
            s.setHealth(hp);
            helper.runAfterDelay(MirrorPlate.DURATION_TICKS + 5, () -> {
                if (s.mirrorActive())
                    problems.add("the plate is still up after " + MirrorPlate.DURATION_TICKS + " ticks");
                s.hurt(arrowFrom(level, archer, s, 3), 20f);
                if (s.getHealth() >= hp)
                    problems.add("a frontal arrow did no damage after the plate dropped");
                if (MirrorPlate.DURATION_TICKS > 5 * 20)
                    problems.add("the plate should be a short window, not a permanent shield");
                for (LivingEntity e : List.<LivingEntity>of(s, archer))
                    e.discard();
                T3RoleGameTests.finish(helper, problems);
            });
        });
    }

    /**
     * AC-4 (Thunderclap): casting puts the ability on cooldown and hurts nobody for the telegraph; then the ring deals
     * {@link Thunderclap#DAMAGE} and a stun (at most 1.5 s) to enemies inside {@link Thunderclap#RADIUS}, and never to a
     * friend or an enemy outside the ring.
     */
    @GameTest(template = ARENA, timeoutTicks = 120)
    public static void jade_sentinel_thunderclap_telegraphs_then_hurts_and_stuns(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = owner(helper, "clap"), foe = owner(helper, "clapfoe");
        var s = put(helper, EntityRegistrar.JADE_SENTINEL_UNIT.get(), owner, 4.5, 6.5);
        if (s != null) {
            s.setNoAi(true);
            com.solegendary.reignofnether.ability.AutocastState.setAll(s, false);
        }
        var inside = tough(put(helper, EntityRegistrar.VINDICATOR_UNIT.get(), foe, 4.5, 10.5));
        var outside = tough(put(helper, EntityRegistrar.VINDICATOR_UNIT.get(), foe, 4.5, 14.5));
        var friend = tough(put(helper, EntityRegistrar.VINDICATOR_UNIT.get(), owner, 7.5, 6.5));
        if (s == null || inside == null || outside == null || friend == null) {
            helper.fail("could not create the clap units");
            return;
        }
        for (Mob m : List.of(inside, outside, friend))
            m.setNoAi(true);
        Thunderclap clap = (Thunderclap) s.getAbilities().get().stream().filter(a -> a instanceof Thunderclap).findFirst().orElse(null);
        if (clap == null) {
            helper.fail("the Sentinel has no Thunderclap");
            return;
        }
        List<String> problems = new ArrayList<>();
        if (Thunderclap.TELEGRAPH_TICKS < 10 || Thunderclap.TELEGRAPH_TICKS > 30)
            problems.add("the telegraph should be readable but under 1.5 s: " + Thunderclap.TELEGRAPH_TICKS + " ticks");
        if (Thunderclap.STUN_TICKS > 30)
            problems.add("a stun over 1.5 s breaks the design's cap: " + Thunderclap.STUN_TICKS);
        helper.runAfterDelay(4, () -> {
            float[] hp = { inside.getHealth(), outside.getHealth(), friend.getHealth() };
            clap.use(level, s, s.blockPosition());
            if (clap.isOffCooldown(s))
                problems.add("Thunderclap did not go on cooldown");
            helper.runAfterDelay(Thunderclap.TELEGRAPH_TICKS / 2, () -> {
                if (inside.getHealth() != hp[0] || inside.hasEffect(MobEffectRegistrar.STUN.get()))
                    problems.add("the enemy was hurt or stunned during the telegraph");
            });
            helper.runAfterDelay(Thunderclap.TELEGRAPH_TICKS + 4, () -> {
                float d = hp[0] - inside.getHealth();
                if (Math.abs(d - Thunderclap.DAMAGE) > 1.5f)
                    problems.add("the clap dealt " + d + ", expected " + Thunderclap.DAMAGE);
                var stun = inside.getEffect(MobEffectRegistrar.STUN.get());
                if (stun == null || stun.getDuration() > Thunderclap.STUN_TICKS)
                    problems.add("the enemy in the ring is not stunned for at most " + Thunderclap.STUN_TICKS + " ticks");
                if (outside.getHealth() != hp[1] || outside.hasEffect(MobEffectRegistrar.STUN.get()))
                    problems.add("an enemy outside the ring was hit");
                if (friend.getHealth() != hp[2] || friend.hasEffect(MobEffectRegistrar.STUN.get()))
                    problems.add("a friendly unit in the ring was hit");
                for (LivingEntity e : List.<LivingEntity>of(s, inside, outside, friend))
                    e.discard();
                T3RoleGameTests.finish(helper, problems);
            });
        });
    }

    /**
     * AC-5 (Ley Anchor): a pulse gives friends within {@link JadeSentinelUnit#ANCHOR_RADIUS} (and the Sentinel itself)
     * {@link JadeSentinelUnit#ANCHOR_RANGED_RESIST} more ranged resist, never an enemy or a far friend; a friend that
     * walks out loses it; when the Sentinel falls everyone loses it.
     */
    @GameTest(template = ARENA, timeoutTicks = 60)
    public static void jade_sentinel_ley_anchor_lends_ranged_resist_to_friends_in_range(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = owner(helper, "anchor"), foe = owner(helper, "anchorfoe");
        var s = put(helper, EntityRegistrar.JADE_SENTINEL_UNIT.get(), owner, 2.5, 8.5);
        var near = tough(put(helper, EntityRegistrar.VINDICATOR_UNIT.get(), owner, 6.5, 8.5));
        var far = tough(put(helper, EntityRegistrar.VINDICATOR_UNIT.get(), owner, 14.5, 8.5));
        var enemy = tough(put(helper, EntityRegistrar.VINDICATOR_UNIT.get(), foe, 4.5, 10.5));
        if (s == null || near == null || far == null || enemy == null) {
            helper.fail("could not create the anchor units");
            return;
        }
        for (Mob m : List.of(s, near, far, enemy))
            m.setNoAi(true);
        var attr = AttributeRegistrar.RANGED_DAMAGE_RESIST.get();
        helper.runAfterDelay(3, () -> {
            List<String> problems = new ArrayList<>();
            double baseNear = near.getAttributeValue(attr), baseFar = far.getAttributeValue(attr);
            double baseEnemy = enemy.getAttributeValue(attr), baseSelf = s.getAttributeValue(attr);
            int n = s.anchor(level);
            if (n != 2)
                problems.add("the pulse covered " + n + " units, expected the Sentinel and one friend");
            if (Math.abs(near.getAttributeValue(attr) - baseNear - JadeSentinelUnit.ANCHOR_RANGED_RESIST) > 1.0e-6)
                problems.add("a friend in range did not gain the resist");
            if (Math.abs(s.getAttributeValue(attr) - baseSelf - JadeSentinelUnit.ANCHOR_RANGED_RESIST) > 1.0e-6)
                problems.add("the Sentinel did not gain the resist itself");
            if (far.getAttributeValue(attr) != baseFar)
                problems.add("a friend out of range gained the resist");
            if (enemy.getAttributeValue(attr) != baseEnemy)
                problems.add("an enemy gained the resist");
            s.anchor(level);   // a second pulse must not stack
            if (Math.abs(near.getAttributeValue(attr) - baseNear - JadeSentinelUnit.ANCHOR_RANGED_RESIST) > 1.0e-6)
                problems.add("two pulses stacked the resist");
            near.setPos(near.getX() + 12, near.getY(), near.getZ());
            s.anchor(level);
            if (near.getAttributeValue(attr) != baseNear)
                problems.add("a friend that walked out kept the resist");
            near.setPos(near.getX() - 12, near.getY(), near.getZ());
            s.anchor(level);
            if (near.getAttributeValue(attr) == baseNear)
                problems.add("a friend that came back did not regain the resist");
            s.discard();
            if (near.getAttributeValue(attr) != baseNear)
                problems.add("the resist stayed on a friend after the Sentinel fell");
            for (LivingEntity e : List.<LivingEntity>of(near, far, enemy))
                e.discard();
            T3RoleGameTests.finish(helper, problems);
        });
    }

    /** AC-6: the numbers keep the T3 rules: its own HP total, the same price as the Celestial Dragon, melee, slowest Storm unit, never pushed. */
    @GameTest(template = ARENA, timeoutTicks = 40)
    public static void jade_sentinel_numbers_keep_the_t3_rules(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        float[] others = { com.solegendary.reignofnether.unit.units.villagers.SunColossusUnit.maxHealth,
            com.solegendary.reignofnether.unit.units.monsters.BoneDragonUnit.maxHealth,
            com.solegendary.reignofnether.unit.units.monsters.PlagueAbominationUnit.maxHealth,
            com.solegendary.reignofnether.unit.units.piglins.WarMammothUnit.maxHealth,
            com.solegendary.reignofnether.unit.units.piglins.MagmaHurlerUnit.maxHealth,
            com.solegendary.reignofnether.unit.units.verdant.WorldTreeWalkerUnit.maxHealth,
            com.solegendary.reignofnether.unit.units.tide.LeviathanUnit.maxHealth,
            CelestialDragonUnit.maxHealth,
            com.solegendary.reignofnether.unit.units.brass.IronLeviathanEngineUnit.maxHealth };
        for (float hp : others)
            if (hp == JadeSentinelUnit.maxHealth)
                problems.add("the Sentinel shares " + hp + " HP with another T3");
        var first = ProductionItems.CELESTIAL_DRAGON.getCost(false, "nobody");
        var second = ProductionItems.STORM_T3B.getCost(false, "nobody");
        if (first.metal() != second.metal() || first.energy() != second.energy() || first.population != second.population)
            problems.add("the Sentinel's price differs from the Celestial Dragon's");
        if (JadeSentinelUnit.movementSpeed >= EarthshakerUnit.movementSpeed)
            problems.add("the Sentinel should be the slowest Storm unit (the Earthshaker walks at " + EarthshakerUnit.movementSpeed + ")");
        JadeSentinelUnit u = EntityRegistrar.JADE_SENTINEL_UNIT.get().create(helper.getLevel());
        if (u == null)
            problems.add("could not create the Sentinel");
        else {
            if (u.isPushable())
                problems.add("a titan must not be pushed by other bodies");
            if (((Unit) u).getCost() != com.solegendary.reignofnether.resources.ResourceCosts.STORM_T3B)
                problems.add("the Sentinel's cost entry is not STORM_T3B");
            u.updateAbilityButtons();   // a refresh must not duplicate the abilities
            var list = u.getAbilities().get();
            if (list.stream().filter(a -> a instanceof MirrorPlate).count() != 1
                    || list.stream().filter(a -> a instanceof Thunderclap).count() != 1)
                problems.add("the Sentinel should carry exactly one Mirror Plate and one Thunderclap");
            if (list.stream().anyMatch(a -> a instanceof GroundSlam))
                problems.add("the Sentinel kept the Earthshaker's Ground Slam");
            u.discard();
        }
        T3RoleGameTests.finish(helper, problems);
    }
}
