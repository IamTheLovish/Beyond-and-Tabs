package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.ability.Ability;
import com.solegendary.reignofnether.ability.AutocastState;
import com.solegendary.reignofnether.ability.abilities.CallNecromancer;
import com.solegendary.reignofnether.ability.abilities.Empower;
import com.solegendary.reignofnether.essence.Essence;
import com.solegendary.reignofnether.essence.EssenceServerEvents;
import com.solegendary.reignofnether.essence.GraveAdeptServerEvents;
import com.solegendary.reignofnether.faction.Factions;
import com.solegendary.reignofnether.hero.HeroExperienceOrb;
import com.solegendary.reignofnether.hero.HeroKits;
import com.solegendary.reignofnether.registrars.AttributeRegistrar;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.unit.UnitDamage;
import com.solegendary.reignofnether.unit.VeterancyServerEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.units.monsters.GraveAdeptUnit;
import com.solegendary.reignofnether.unit.units.monsters.SkeletonUnit;
import com.solegendary.reignofnether.unit.units.monsters.TombstoneUnit;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * The Tombcaller's Grave Adept and Call the Necromancer (design/essence_casters.md "Gravebound Gravecaller" and Revision
 * 1), written before the code:
 * <ul>
 *   <li>Call the Necromancer (20 essence, 60 s): one Grave Adept per Tombcaller, a summon, until killed;</li>
 *   <li>it levels at 120 / 300 / 540 / 840 / 1200 XP, each level +15% health and +10% damage, at most 5;</li>
 *   <li>enemy deaths within 16 feed it what they would pay a hero; friends, summons and deaths farther off do not;</li>
 *   <li>from level 1 it raises (its level) skeletons every 15 s on its owner's creep; at level 5 off the creep too, for
 *       2 essence of its own each; from level 3 a soul bolt;</li>
 *   <li>killed, its XP flies to the killer's heroes as orbs; Empower on it gives 120 XP, not a rank.</li>
 * </ul>
 * Every unit stands still (no AI) with its autocast off; each test records in its early callbacks (which never throw)
 * and judges and cleans up in its last.
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class GraveAdeptGameTests {

    static final String ARENA = SkirmishGameTests.ARENA;
    static final String BATCH = "grave_adept";

    private static String owner(GameTestHelper helper, String what) {
        return "gametest_adept_" + what + "_" + helper.absolutePos(BlockPos.ZERO).asLong();
    }

    private static void discardAll(List<? extends Entity> entities) {
        for (Entity e : entities)
            if (e != null && !e.isRemoved())
                e.discard();
    }

    private static void finish(GameTestHelper helper, List<String> problems) {
        if (problems.isEmpty())
            helper.succeed();
        else
            helper.fail(String.join("; ", problems));
    }

    private static Mob still(GameTestHelper helper, EntityType<? extends Mob> type, String owner, int x, int z) {
        Mob m = HeroGameTests.spawnUnit(helper, type, owner, x, z);
        if (m != null) {
            m.setNoAi(true);
            AutocastState.setAll((Unit) m, false);
        }
        return m;
    }

    private static Mob caller(GameTestHelper helper, String owner, int x, int z, int essence) {
        Mob m = still(helper, EntityRegistrar.TOMBCALLER_UNIT.get(), owner, x, z);
        if (m != null)
            Essence.add(m, essence);
        return m;
    }

    /** A Grave Adept raised directly (not through the ability) with {@code xp}, standing still. */
    private static GraveAdeptUnit adept(GameTestHelper helper, LivingEntity caster, String owner, int x, int z, int xp) {
        ServerLevel level = helper.getLevel();
        GraveAdeptUnit a = EntityRegistrar.GRAVE_ADEPT_UNIT.get().create(level);
        if (a == null)
            return null;
        BlockPos at = helper.absolutePos(new BlockPos(x, 2, z));
        a.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
        a.setOwnerName(owner);
        Essence.markSummoned(a);
        if (caster != null)
            a.setCaster(caster);
        a.setNextRaise(level.getGameTime() + 100_000);
        a.setNextBolt(level.getGameTime() + 100_000);
        a.setNoAi(true);
        level.addFreshEntity(a);
        AutocastState.setAll(a, false);
        if (xp > 0)
            GraveAdeptServerEvents.addXp(a, xp);
        return a;
    }

    private static TombstoneUnit stone(GameTestHelper helper, String owner, int x, int z) {
        ServerLevel level = helper.getLevel();
        TombstoneUnit s = EntityRegistrar.TOMBSTONE_UNIT.get().create(level);
        if (s == null)
            return null;
        BlockPos at = helper.absolutePos(new BlockPos(x, 2, z));
        s.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
        s.setOwnerName(owner);
        Essence.markSummoned(s);
        s.begin(null, level.getGameTime());
        s.backdate(8 * 40);   // full-grown creep
        s.setNextRaise(level.getGameTime() + 100_000);
        level.addFreshEntity(s);
        return s;
    }

    private static <T extends Ability> T ability(Mob unit, Class<T> type) {
        for (Ability a : ((Unit) unit).getAbilities().get())
            if (type.isInstance(a))
                return type.cast(a);
        return null;
    }

    private static List<Entity> nonNull(Entity... es) {
        return Arrays.stream(es).filter(Objects::nonNull).toList();
    }

    private static void clearNear(ServerLevel level, Vec3 at, double r, String owner) {
        for (Entity w : EssenceServerEvents.getWisps())
            if (!w.isRemoved() && w.position().distanceTo(at) < r)
                w.discard();
        for (HeroExperienceOrb o : level.getEntitiesOfClass(HeroExperienceOrb.class, new net.minecraft.world.phys.AABB(at, at).inflate(r)))
            o.discard();
        for (SkeletonUnit sk : level.getEntitiesOfClass(SkeletonUnit.class, new net.minecraft.world.phys.AABB(at, at).inflate(r)))
            if (owner.equals(sk.getOwnerName()))
                sk.discard();
    }

    // ----------------------------------------------------------------------------------------- Call the Necromancer

    /**
     * Call the Necromancer: one Grave Adept rises beside the Tombcaller for its owner - a Gravebound summon at level 0
     * with 60 health; 20 of 30 essence spent, the 60 s cooldown starts and holds. With the cooldown cleared it is
     * refused while that Adept stands; with the Adept gone, refused short of essence (10 of 20). No refusal spends.
     */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = BATCH)
    public static void grave_adept_call_the_necromancer_raises_one(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = owner(helper, "call");
        Mob caller = caller(helper, owner, 3, 3, 30);
        if (caller == null) {
            helper.fail("could not spawn the Tombcaller");
            return;
        }
        List<Entity> all = new ArrayList<>(List.of(caller));
        helper.runAfterDelay(2, () -> {
            List<String> problems = new ArrayList<>();
            try {
                CallNecromancer call = ability(caller, CallNecromancer.class);
                if (call == null)
                    throw new IllegalStateException("the Tombcaller has no Call the Necromancer");
                if (CallNecromancer.ESSENCE_COST != 20 || CallNecromancer.CD_SECONDS != 60)
                    problems.add("the draft set 20 essence and 60 s, Call the Necromancer has " + CallNecromancer.ESSENCE_COST + " and " + CallNecromancer.CD_SECONDS);
                call.use(level, (Unit) caller, caller.blockPosition());
                for (GraveAdeptUnit a : level.getEntitiesOfClass(GraveAdeptUnit.class, caller.getBoundingBox().inflate(6)))
                    all.add(a);
                GraveAdeptUnit a = GraveAdeptServerEvents.adeptOf(caller);
                if (a == null) {
                    problems.add("Call the Necromancer raised no Grave Adept");
                } else {
                    if (!owner.equals(a.getOwnerName()))
                        problems.add("the Adept belongs to " + a.getOwnerName());
                    if (!Essence.isSummoned(a))
                        problems.add("the Adept is not marked summoned");
                    if (Factions.getFaction(a) != Factions.MONSTERS)
                        problems.add("the Adept is not Gravebound");
                    if (a.level() != 0 || a.xp() != 0)
                        problems.add("a new Adept is level " + a.level() + " with " + a.xp() + " XP, expected 0 and 0");
                    if (Math.abs(a.getMaxHealth() - GraveAdeptUnit.maxHealth) > 0.5f)
                        problems.add("a new Adept has " + a.getMaxHealth() + " health, expected " + GraveAdeptUnit.maxHealth);
                    if (a.distanceTo(caller) > 3)
                        problems.add("the Adept rose " + String.format("%.1f", a.distanceTo(caller)) + " blocks off");
                }
                if (Essence.get(caller) != 10)
                    problems.add("the Tombcaller holds " + Essence.get(caller) + " essence after the call, expected 10");
                if (((Unit) caller).getCooldown(call) < 60 * 20 - 2)
                    problems.add("the cooldown is " + ((Unit) caller).getCooldown(call) + " ticks after the call, expected 1200");
                call.use(level, (Unit) caller, caller.blockPosition());
                if (Essence.get(caller) != 10)
                    problems.add("a second call during the cooldown spent essence");
                Essence.add(caller, 20);   // 30 again
                call.setCooldown(0, (Unit) caller);
                call.use(level, (Unit) caller, caller.blockPosition());
                if (Essence.get(caller) != 30 || ((Unit) caller).getCooldown(call) > 0)
                    problems.add("a call while its Adept stands was not refused cleanly (essence " + Essence.get(caller) + ")");
                if (a != null)
                    a.discard();
                Essence.trySpend(caller, 20);   // 10 left
                call.use(level, (Unit) caller, caller.blockPosition());
                if (Essence.get(caller) != 10 || ((Unit) caller).getCooldown(call) > 0 || GraveAdeptServerEvents.adeptOf(caller) != null)
                    problems.add("a call short of essence was not refused cleanly (essence " + Essence.get(caller) + ")");
                for (GraveAdeptUnit extra : level.getEntitiesOfClass(GraveAdeptUnit.class, caller.getBoundingBox().inflate(6)))
                    if (!all.contains(extra))
                        all.add(extra);
            } catch (RuntimeException ex) {
                problems.add("threw " + ex);
            } finally {
                discardAll(all);
            }
            finish(helper, problems);
        });
    }

    // -------------------------------------------------------------------------------------------- levels and XP

    /**
     * Levels at 120 / 300 / 540 / 840 / 1200 XP: 119 is level 0 (60 health), 120 level 1 (69, +10% damage), 300 level 2
     * (78), 1200 level 5 (105, +50%), and more XP stays at 5.
     */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = BATCH)
    public static void grave_adept_levels_at_its_xp_thresholds(GameTestHelper helper) {
        String owner = owner(helper, "levels");
        GraveAdeptUnit a = adept(helper, null, owner, 8, 8, 0);
        if (a == null) {
            helper.fail("could not raise the Adept");
            return;
        }
        helper.runAfterDelay(2, () -> {
            List<String> problems = new ArrayList<>();
            try {
                if (!Arrays.equals(GraveAdeptUnit.LEVEL_XP, new int[] { 120, 300, 540, 840, 1200 }))
                    problems.add("Revision 1 set levels at 120/300/540/840/1200 XP, LEVEL_XP is " + Arrays.toString(GraveAdeptUnit.LEVEL_XP));
                if (GraveAdeptUnit.HP_PER_LEVEL != 0.15 || GraveAdeptUnit.DAMAGE_PER_LEVEL != 0.10)
                    problems.add("the draft set +15% health and +10% damage a level");
                double baseDmg = a.getAttributeBaseValue(AttributeRegistrar.ATTACK_DAMAGE.get());
                int[][] steps = { { 119, 0 }, { 1, 1 }, { 180, 2 }, { 900, 5 }, { 5000, 5 } };   // added XP -> level
                int total = 0;
                for (int[] step : steps) {
                    GraveAdeptServerEvents.addXp(a, step[0]);
                    total += step[0];
                    int lvl = step[1];
                    if (a.xp() != total)
                        problems.add("after " + total + " XP added the Adept holds " + a.xp());
                    if (a.level() != lvl)
                        problems.add("at " + total + " XP the Adept is level " + a.level() + ", expected " + lvl);
                    double hp = GraveAdeptUnit.maxHealth * (1 + 0.15 * lvl);
                    if (Math.abs(a.getMaxHealth() - hp) > 0.05)
                        problems.add("at level " + lvl + " the Adept has " + a.getMaxHealth() + " max health, expected " + hp);
                    double dmg = a.getAttributeValue(AttributeRegistrar.ATTACK_DAMAGE.get());
                    if (baseDmg > 0 && Math.abs(dmg / baseDmg - (1 + 0.10 * lvl)) > 0.005)
                        problems.add("at level " + lvl + " the Adept deals " + dmg / baseDmg + " of its base damage, expected " + (1 + 0.1 * lvl));
                }
            } catch (RuntimeException ex) {
                problems.add("threw " + ex);
            } finally {
                a.discard();
            }
            finish(helper, problems);
        });
    }

    /**
     * An enemy vindicator killed 8 blocks from the Adept feeds it exactly what it would pay a hero; an enemy killed 18
     * blocks off, a friend killed beside it and a summoned enemy killed beside it feed nothing.
     */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = BATCH)
    public static void grave_adept_feeds_on_enemy_deaths_within_16(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = owner(helper, "feed"), foe = owner(helper, "feedfoe");
        GraveAdeptUnit a = adept(helper, null, owner, 2, 2, 0);
        Mob near = still(helper, EntityRegistrar.VINDICATOR_UNIT.get(), foe, 10, 2);
        Mob far = still(helper, EntityRegistrar.VINDICATOR_UNIT.get(), foe, 15, 15);
        Mob friend = still(helper, EntityRegistrar.VINDICATOR_UNIT.get(), owner, 4, 4);
        Mob summoned = still(helper, EntityRegistrar.VINDICATOR_UNIT.get(), foe, 6, 6);
        List<Mob> victims = new ArrayList<>(Arrays.asList(near, far, friend, summoned));
        if (a == null || victims.contains(null)) {
            discardAll(nonNull(a, near, far, friend, summoned));
            helper.fail("could not spawn the Adept and the four vindicators");
            return;
        }
        Essence.markSummoned(summoned);
        int want = GraveAdeptServerEvents.xpFor(near);
        List<Vec3> spots = new ArrayList<>();
        List<String> early = new ArrayList<>();
        helper.runAfterDelay(2, () -> {
            for (Mob m : victims) {
                spots.add(m.position());
                try {
                    m.kill();
                } catch (RuntimeException ex) {
                    early.add("killing threw " + ex);
                }
            }
        });
        helper.runAfterDelay(6, () -> {
            List<String> problems = new ArrayList<>(early);
            try {
                if (GraveAdeptUnit.XP_RANGE != 16)
                    problems.add("the draft set 16 blocks, XP_RANGE is " + GraveAdeptUnit.XP_RANGE);
                if (want <= 0)
                    problems.add("a vindicator's death is worth " + want + " XP: the test lost its point");
                if (a.xp() != want)
                    problems.add("the Adept holds " + a.xp() + " XP, expected the near enemy's " + want
                        + " (and nothing from the far one, the friend or the summon)");
            } finally {
                for (Vec3 at : spots)
                    clearNear(level, at, 2, owner);
                discardAll(victims);
                a.discard();
            }
            finish(helper, problems);
        });
    }

    // --------------------------------------------------------------------------------------------- what it does

    /**
     * When its raise comes: a level-2 Adept on its owner's creep raises 2 skeletons (summoned, its owner's) and its next
     * raise is 15 s on, with no more while those 2 stand; a level-2 Adept off the creep raises none; a level-5 Adept off
     * the creep holding 5 essence raises 2 for 4 of it.
     */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = BATCH)
    public static void grave_adept_raises_on_creep_and_from_essence_at_level_5(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = owner(helper, "raise");
        TombstoneUnit s = stone(helper, owner, 4, 4);
        GraveAdeptUnit onCreep = adept(helper, null, owner, 5, 4, 300);
        GraveAdeptUnit offCreep = adept(helper, null, owner, 12, 12, 300);
        GraveAdeptUnit five = adept(helper, null, owner, 13, 2, 1200);
        List<Entity> all = new ArrayList<>(nonNull(s, onCreep, offCreep, five));
        if (all.size() != 4) {
            discardAll(all);
            helper.fail("could not raise the stone and the three Adepts");
            return;
        }
        Essence.add(five, 5);
        List<String> early = new ArrayList<>();
        List<Mob> first = new ArrayList<>();
        helper.runAfterDelay(2, () -> {
            long now = level.getGameTime();
            onCreep.setNextRaise(now);
            offCreep.setNextRaise(now);
            five.setNextRaise(now);
        });
        helper.runAfterDelay(5, () -> {
            try {
                long now = level.getGameTime();
                first.addAll(GraveAdeptServerEvents.raisedBy(onCreep));
                if (first.size() != 2)
                    early.add("a level-2 Adept on the creep raised " + first.size() + " skeletons, expected 2");
                for (Mob m : first)
                    if (!(m instanceof SkeletonUnit) || !owner.equals(((Unit) m).getOwnerName()) || !Essence.isSummoned(m))
                        early.add("a raised unit is not a summoned skeleton of the owner");
                if (onCreep.nextRaise() - now < 15 * 20 - 4)
                    early.add("the Adept's next raise is " + (onCreep.nextRaise() - now) + " ticks off, expected 15 s");
                if (!GraveAdeptServerEvents.raisedBy(offCreep).isEmpty())
                    early.add("a level-2 Adept off the creep raised " + GraveAdeptServerEvents.raisedBy(offCreep).size());
                if (GraveAdeptServerEvents.raisedBy(five).size() != 2 || Essence.get(five) != 1)
                    early.add("a level-5 Adept off the creep with 5 essence raised " + GraveAdeptServerEvents.raisedBy(five).size()
                        + " and kept " + Essence.get(five) + ", expected 2 and 1");
                onCreep.setNextRaise(now);   // time again, with its 2 standing
            } catch (RuntimeException ex) {
                early.add("threw " + ex);
            }
        });
        helper.runAfterDelay(8, () -> {
            List<String> problems = new ArrayList<>(early);
            try {
                if (GraveAdeptUnit.RAISE_EVERY_SECONDS != 15 || GraveAdeptUnit.ESSENCE_PER_SKELETON != 2)
                    problems.add("the draft set a raise every 15 s and 2 essence a skeleton at level 5");
                if (GraveAdeptServerEvents.raisedBy(onCreep).size() > 2)
                    problems.add("with its 2 standing the level-2 Adept raised more (" + GraveAdeptServerEvents.raisedBy(onCreep).size() + ")");
            } finally {
                for (GraveAdeptUnit a : List.of(onCreep, offCreep, five))
                    discardAll(GraveAdeptServerEvents.raisedBy(a));
                clearNear(level, s.position(), 12, owner);
                discardAll(all);
            }
            finish(helper, problems);
        });
    }

    /** From level 3 a soul bolt: a level-3 Adept's bolt takes 15 from an enemy 3 blocks off; a level-2 Adept's takes none. */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = BATCH)
    public static void grave_adept_soul_bolt_from_level_3(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = owner(helper, "bolt"), foe = owner(helper, "boltfoe");
        GraveAdeptUnit three = adept(helper, null, owner, 3, 3, 540);
        GraveAdeptUnit two = adept(helper, null, owner, 3, 12, 300);
        Mob target3 = still(helper, EntityRegistrar.VINDICATOR_UNIT.get(), foe, 6, 3);
        Mob target2 = still(helper, EntityRegistrar.VINDICATOR_UNIT.get(), foe, 6, 12);
        List<Entity> all = new ArrayList<>(nonNull(three, two, target3, target2));
        if (all.size() != 4) {
            discardAll(all);
            helper.fail("could not spawn the Adepts and their targets");
            return;
        }
        float[] before = new float[2];
        long[] boltAt = { 0 };
        helper.runAfterDelay(2, () -> {
            before[0] = target3.getHealth();
            before[1] = target2.getHealth();
            boltAt[0] = level.getGameTime();
            three.setNextBolt(boltAt[0]);
            two.setNextBolt(boltAt[0]);
        });
        helper.runAfterDelay(5, () -> {
            List<String> problems = new ArrayList<>();
            try {
                if (GraveAdeptUnit.BOLT_LEVEL != 3)
                    problems.add("the draft set the soul bolt at level 3");
                float lost3 = before[0] - target3.getHealth(), lost2 = before[1] - target2.getHealth();
                if (Math.abs(lost3 - GraveAdeptUnit.BOLT_DAMAGE) > 0.05f)
                    problems.add("the level-3 Adept's bolt took " + lost3 + ", expected " + GraveAdeptUnit.BOLT_DAMAGE);
                if (lost2 > 0.01f)
                    problems.add("a level-2 Adept's bolt took " + lost2 + ": no bolt below level 3");
                if (three.nextBolt() - boltAt[0] < GraveAdeptUnit.BOLT_EVERY_SECONDS * 20 - 4)
                    problems.add("the next bolt is " + (three.nextBolt() - boltAt[0]) + " ticks after the last, expected 3 s");
            } finally {
                discardAll(all);
            }
            finish(helper, problems);
        });
    }

    /**
     * Killed by an enemy, an Adept holding 300 XP sends it as hero orbs (300 in all) to the killer's hero beside it -
     * none to a third player's hero there - and drops no essence; the killer gets no veterancy for a summon.
     */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = BATCH)
    public static void grave_adept_death_sends_its_xp_to_the_killers_heroes(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = owner(helper, "death"), foe = owner(helper, "deathfoe"), third = owner(helper, "deaththird");
        GraveAdeptUnit a = adept(helper, null, owner, 4, 4, 300);
        Mob killer = still(helper, EntityRegistrar.VINDICATOR_UNIT.get(), foe, 6, 4);
        Mob hero = HeroGameTests.spawnHero(helper, HeroKits.CORSAIR_CAPTAIN, foe, 8, 8);
        Mob otherHero = HeroGameTests.spawnHero(helper, HeroKits.STORM_WITCH, third, 10, 10);
        List<Entity> all = new ArrayList<>(nonNull(a, killer, hero, otherHero));
        if (all.size() != 4) {
            discardAll(all);
            helper.fail("could not spawn the Adept, the killer and the two heroes");
            return;
        }
        hero.setNoAi(true);
        otherHero.setNoAi(true);
        Vec3 at = a.position();
        List<String> early = new ArrayList<>();
        helper.runAfterDelay(2, () -> {
            try {
                if (a.xp() != 300)
                    early.add("setup: the Adept holds " + a.xp() + " XP, expected 300");
                a.invulnerableTime = 0;
                UnitDamage.hurtExplicit(a, level.damageSources().mobAttack(killer), 10_000f);
                if (a.isAlive())
                    early.add("setup: the Adept survived the killing blow");
            } catch (RuntimeException ex) {
                early.add("killing threw " + ex);
            }
        });
        helper.runAfterDelay(4, () -> {
            List<String> problems = new ArrayList<>(early);
            try {
                int total = 0;
                for (HeroExperienceOrb o : level.getEntitiesOfClass(HeroExperienceOrb.class, new net.minecraft.world.phys.AABB(at, at).inflate(8)))
                    total += o.value * 10;
                if (total != 300)
                    problems.add("the dead Adept's orbs carry " + total + " hero XP, expected its 300 (to the killer's one hero only)");
                for (Entity w : EssenceServerEvents.getWisps())
                    if (!w.isRemoved() && w.position().distanceTo(at) < 2)
                        problems.add("the dead Adept dropped an essence wisp");
                if (VeterancyServerEvents.getXp(killer) > 0)
                    problems.add("the killer earned veterancy for a summon");
            } finally {
                clearNear(level, at, 8, owner);
                discardAll(all);
            }
            finish(helper, problems);
        });
    }

    /**
     * Empower on a level-0 Adept gives it 120 XP (level 1) for 15 essence, not a rank; on a level-5 Adept it is refused
     * without spending.
     */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = BATCH)
    public static void grave_adept_takes_120_xp_from_empower(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = owner(helper, "emp");
        Mob caller = caller(helper, owner, 3, 3, 30);
        Mob caller2 = caller(helper, owner, 12, 12, 30);
        GraveAdeptUnit fresh = adept(helper, caller, owner, 5, 3, 0);
        GraveAdeptUnit top = adept(helper, caller2, owner, 13, 13, 1200);
        List<Entity> all = new ArrayList<>(nonNull(caller, caller2, fresh, top));
        if (all.size() != 4) {
            discardAll(all);
            helper.fail("could not spawn the two Tombcallers and their Adepts");
            return;
        }
        helper.runAfterDelay(2, () -> {
            List<String> problems = new ArrayList<>();
            try {
                Empower empower = ability(caller, Empower.class);
                if (empower == null)
                    throw new IllegalStateException("the Tombcaller has no Empower");
                if (GraveAdeptUnit.EMPOWER_XP != 120)
                    problems.add("Revision 1 set 120 XP, EMPOWER_XP is " + GraveAdeptUnit.EMPOWER_XP);
                empower.use(level, (Unit) caller, fresh);
                if (fresh.xp() != 120 || fresh.level() != 1)
                    problems.add("Empower left the Adept with " + fresh.xp() + " XP (level " + fresh.level() + "), expected 120 (level 1)");
                if (VeterancyServerEvents.getRank(fresh) != 0)
                    problems.add("Empower gave the Adept a veterancy rank");
                if (Essence.get(caller) != 15)
                    problems.add("the Tombcaller holds " + Essence.get(caller) + " essence after Empower, expected 15");
                empower.use(level, (Unit) caller2, top);
                if (Essence.get(caller2) != 30 || top.xp() != 1200)
                    problems.add("Empower on a level-5 Adept was not refused cleanly (essence " + Essence.get(caller2) + ", XP " + top.xp() + ")");
            } catch (RuntimeException ex) {
                problems.add("threw " + ex);
            } finally {
                discardAll(all);
            }
            finish(helper, problems);
        });
    }
}
