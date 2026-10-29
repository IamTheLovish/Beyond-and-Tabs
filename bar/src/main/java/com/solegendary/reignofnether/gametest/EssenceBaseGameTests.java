package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.ability.Ability;
import com.solegendary.reignofnether.ability.AutoCaster;
import com.solegendary.reignofnether.ability.AutoRule;
import com.solegendary.reignofnether.ability.abilities.Empower;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.BuildingServerEvents;
import com.solegendary.reignofnether.building.BuildingUtils;
import com.solegendary.reignofnether.building.Buildings;
import com.solegendary.reignofnether.essence.Essence;
import com.solegendary.reignofnether.essence.EssenceServerEvents;
import com.solegendary.reignofnether.hero.HeroExperienceOrb;
import com.solegendary.reignofnether.hero.HeroKits;
import com.solegendary.reignofnether.player.CommanderServerEvents;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.resources.WreckServerEvents;
import com.solegendary.reignofnether.unit.UnitAction;
import com.solegendary.reignofnether.unit.VeterancyServerEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * Essence base, Revision 1 (design/essence_casters.md "Revision 1", the Oct 29 design review: summons must not feed a
 * race's own loops, Empower needs limits, decisive kills should pay, and bots need to be able to cast essence spells).
 * Written before the code:
 * <ul>
 *   <li>a summoned unit's death gives no hero XP orbs and its killer no veterancy experience (and, from PR 1, no wisp);</li>
 *   <li>a carrier that dies drops the essence it held as a wisp (a summoned carrier drops nothing);</li>
 *   <li>Empower: 20 s cooldown, one rank per target per 60 s, never on a hero, commander, experimental or summon - all
 *       refused with nothing spent and no cooldown - and a per-type hook that replaces the rank (the Grave Adept's
 *       120 XP, a later PR);</li>
 *   <li>bots: an ability's {@code autocastEssence} gates the AutoCaster like {@code autocastEnergy}, and the
 *       {@link AutoRule#BUILDING_HURT} rule casts at the most hurt friendly building in reach.</li>
 * </ul>
 * Same shape as EssenceGameTests: early callbacks record, the last one judges after cleaning up in {@code finally}.
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class EssenceBaseGameTests {

    static final String ARENA = SkirmishGameTests.ARENA;
    static final String BATCH = "essence_base";

    private static String owner(GameTestHelper helper, String what) {
        return "gametest_essbase_" + what + "_" + helper.absolutePos(BlockPos.ZERO).asLong();
    }

    private static Mob still(GameTestHelper helper, EntityType<? extends Mob> type, String owner, int x, int z) {
        Mob m = HeroGameTests.spawnUnit(helper, type, owner, x, z);
        if (m != null)
            m.setNoAi(true);
        return m;
    }

    private static Mob carrier(GameTestHelper helper, String owner, int x, int z, int essence) {
        Mob m = still(helper, EntityRegistrar.VILLAGER_UNIT.get(), owner, x, z);
        if (m == null)
            return null;
        Essence.setCarrier(m, true);
        Essence.add(m, essence);
        return m;
    }

    private static List<Entity> wispsNear(Vec3 at) {
        List<Entity> found = new ArrayList<>();
        for (Entity w : EssenceServerEvents.getWisps()) {
            double dx = w.getX() - at.x, dz = w.getZ() - at.z;
            if (!w.isRemoved() && dx * dx + dz * dz <= 1.5 * 1.5 && Math.abs(w.getY() - at.y) <= 3)
                found.add(w);
        }
        return found;
    }

    /** Wisps, wrecks and XP orbs the dead left where they fell: gone, whatever the verdict. */
    private static void clearDrops(ServerLevel level, List<Vec3> spots) {
        for (Vec3 at : spots) {
            wispsNear(at).forEach(Entity::discard);
            for (Entity w : WreckServerEvents.getWrecks())
                if (!w.isRemoved() && w.position().distanceToSqr(at) < 2 * 2)
                    w.discard();
            level.getEntitiesOfClass(HeroExperienceOrb.class, new AABB(BlockPos.containing(at)).inflate(4)).forEach(Entity::discard);
        }
    }

    private static void discardAll(List<? extends Entity> entities) {
        for (Entity e : entities)
            if (e != null && !e.isRemoved())
                e.discard();
    }

    private static List<Entity> present(Entity... entities) {
        List<Entity> out = new ArrayList<>();
        for (Entity e : entities)
            if (e != null)
                out.add(e);
        return out;
    }

    private static void finish(GameTestHelper helper, List<String> problems) {
        if (problems.isEmpty())
            helper.succeed();
        else
            helper.fail(String.join("; ", problems));
    }

    // ------------------------------------------------------------------------------------- summons give nothing

    /**
     * A summoned unit's death sends no XP orbs to the enemy hero near it; a normal unit's death beside it does. The orbs
     * are counted where they appear, the tick after the deaths (they fly off slowly).
     */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = BATCH)
    public static void essence_base_a_summon_drops_no_hero_xp_orbs(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String mine = owner(helper, "orbs"), foe = owner(helper, "orbsfoe");
        Mob hero = HeroGameTests.spawnHero(helper, HeroKits.DAWN_PALADIN, foe, 8, 8);
        Mob summon = still(helper, EntityRegistrar.VINDICATOR_UNIT.get(), mine, 3, 3);
        Mob normal = still(helper, EntityRegistrar.VINDICATOR_UNIT.get(), mine, 12, 12);
        List<Entity> all = present(hero, summon, normal);
        if (all.size() != 3) {
            discardAll(all);
            helper.fail("could not spawn the hero and the two units");
            return;
        }
        hero.setNoAi(true);
        Essence.markSummoned(summon);
        List<Vec3> spots = List.of(summon.position(), normal.position());
        List<String> early = new ArrayList<>();
        int[] orbs = { -1, -1 };
        helper.runAfterDelay(2, () -> {
            try {
                summon.kill();
                normal.kill();
            } catch (RuntimeException ex) {
                early.add("a kill threw " + ex);
            }
        });
        helper.runAfterDelay(3, () -> {
            for (int i = 0; i < 2; i++)
                orbs[i] = level.getEntitiesOfClass(HeroExperienceOrb.class, new AABB(BlockPos.containing(spots.get(i))).inflate(3)).size();
        });
        helper.runAfterDelay(6, () -> {
            List<String> problems = new ArrayList<>(early);
            try {
                if (orbs[0] != 0)
                    problems.add("the summon's death sent " + orbs[0] + " XP orbs to the enemy hero, expected none");
                if (orbs[1] <= 0)
                    problems.add("the normal unit's death beside it sent no XP orbs (" + orbs[1] + "): the test lost its point");
            } finally {
                clearDrops(level, spots);
                discardAll(all);
            }
            finish(helper, problems);
        });
    }

    /** Killing a summon gives the killer no veterancy experience; killing a normal unit of the same type does. */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = BATCH)
    public static void essence_base_killing_a_summon_gives_no_veterancy(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String mine = owner(helper, "vet"), foe = owner(helper, "vetfoe");
        Mob killer = still(helper, EntityRegistrar.VINDICATOR_UNIT.get(), mine, 7, 7);
        Mob summon = still(helper, EntityRegistrar.VINDICATOR_UNIT.get(), foe, 4, 7);
        Mob normal = still(helper, EntityRegistrar.VINDICATOR_UNIT.get(), foe, 10, 7);
        List<Entity> all = present(killer, summon, normal);
        if (all.size() != 3) {
            discardAll(all);
            helper.fail("could not spawn the killer and its two victims");
            return;
        }
        Essence.markSummoned(summon);
        List<Vec3> spots = List.of(summon.position(), normal.position());
        helper.runAfterDelay(2, () -> {
            List<String> problems = new ArrayList<>();
            try {
                // the death event a killing blow raises, with the killer as its source
                summon.die(level.damageSources().mobAttack(killer));
                float afterSummon = VeterancyServerEvents.getXp(killer);
                normal.die(level.damageSources().mobAttack(killer));
                float afterNormal = VeterancyServerEvents.getXp(killer);
                if (afterSummon != 0)
                    problems.add("killing a summon gave the killer " + afterSummon + " veterancy experience, expected none");
                if (afterNormal <= afterSummon)
                    problems.add("killing a normal unit gave no veterancy experience (" + afterNormal + "): the test lost its point");
            } finally {
                clearDrops(level, spots);
                discardAll(all);
            }
            finish(helper, problems);
        });
    }

    // ------------------------------------------------------------------------------------- a dead carrier pays

    /**
     * A carrier that dies drops the essence it held as a wisp, beside its own death wisp (a villager: 2 + 17); a
     * summoned carrier drops neither.
     */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = BATCH)
    public static void essence_base_a_dead_carrier_drops_its_essence(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String mine = owner(helper, "deadcarrier");
        Mob full = carrier(helper, mine, 4, 4, 17);
        Mob summoned = carrier(helper, mine, 12, 12, 17);
        List<Entity> all = present(full, summoned);
        if (all.size() != 2) {
            discardAll(all);
            helper.fail("could not spawn the two carriers");
            return;
        }
        Essence.markSummoned(summoned);
        int own = Essence.essenceFor(((Unit) full).getCost().metal());
        List<Vec3> spots = List.of(full.position(), summoned.position());
        List<String> early = new ArrayList<>();
        helper.runAfterDelay(2, () -> {
            try {
                full.kill();
                summoned.kill();
            } catch (RuntimeException ex) {
                early.add("a kill threw " + ex);
            }
        });
        helper.runAfterDelay(6, () -> {
            List<String> problems = new ArrayList<>(early);
            try {
                List<Entity> here = wispsNear(spots.get(0));
                int sum = 0;
                boolean held = false;
                for (Entity w : here) {
                    sum += EssenceServerEvents.valueOf(w);
                    held |= EssenceServerEvents.valueOf(w) == 17;
                }
                if (!held || sum != own + 17)
                    problems.add("the dead carrier left " + here.size() + " wisps worth " + sum + ", expected its own " + own
                        + " and a wisp of the 17 it held");
                int n = wispsNear(spots.get(1)).size();
                if (n != 0)
                    problems.add("a summoned carrier's death dropped " + n + " wisps");
            } finally {
                clearDrops(level, spots);
                discardAll(all);
            }
            finish(helper, problems);
        });
    }

    // ------------------------------------------------------------------------------------- Empower's limits

    /** Empower's cooldown is 20 s: after a cast the caster waits 400 ticks. */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = BATCH)
    public static void essence_base_empower_has_a_20_second_cooldown(GameTestHelper helper) {
        String mine = owner(helper, "empcd");
        Mob caster = carrier(helper, mine, 4, 4, 30);
        Mob target = still(helper, EntityRegistrar.VINDICATOR_UNIT.get(), mine, 7, 4);
        List<Entity> all = present(caster, target);
        if (all.size() != 2) {
            discardAll(all);
            helper.fail("could not spawn the caster and its friend");
            return;
        }
        Empower ability = new Empower();
        helper.runAfterDelay(2, () -> {
            List<String> problems = new ArrayList<>();
            try {
                ability.use(helper.getLevel(), (Unit) caster, target);
                float cd = ((Unit) caster).getCooldown(ability);
                if (Empower.CD_SECONDS != 20)
                    problems.add("Revision 1 set a 20 s cooldown, CD_SECONDS is " + Empower.CD_SECONDS);
                if (VeterancyServerEvents.getRank(target) != 1)
                    problems.add("setup: the friend did not rank up (rank " + VeterancyServerEvents.getRank(target) + ")");
                if (cd != 20 * ResourceCost.TICKS_PER_SECOND)
                    problems.add("after a cast the cooldown is " + cd + " ticks, expected " + 20 * ResourceCost.TICKS_PER_SECOND);
            } finally {
                discardAll(all);
            }
            finish(helper, problems);
        });
    }

    /**
     * One rank per target per 60 s: a second caster's Empower on the same friend straight after is refused (nothing spent,
     * no cooldown); 60 s later it works.
     */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = BATCH)
    public static void essence_base_empower_ranks_a_target_once_per_60_seconds(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String mine = owner(helper, "emp60");
        Mob first = carrier(helper, mine, 4, 8, 30);
        Mob second = carrier(helper, mine, 10, 8, 30);
        Mob target = still(helper, EntityRegistrar.VINDICATOR_UNIT.get(), mine, 7, 8);
        List<Entity> all = present(first, second, target);
        if (all.size() != 3) {
            discardAll(all);
            helper.fail("could not spawn the two casters and their friend");
            return;
        }
        Empower a = new Empower(), b = new Empower();
        helper.runAfterDelay(2, () -> {
            List<String> problems = new ArrayList<>();
            try {
                if (Empower.RANK_EVERY_TICKS != 60 * ResourceCost.TICKS_PER_SECOND)
                    problems.add("Revision 1 set one rank per 60 s, RANK_EVERY_TICKS is " + Empower.RANK_EVERY_TICKS);
                a.use(level, (Unit) first, target);
                if (VeterancyServerEvents.getRank(target) != 1)
                    problems.add("setup: the first Empower did not rank the friend up");
                b.use(level, (Unit) second, target);
                if (VeterancyServerEvents.getRank(target) != 1)
                    problems.add("a second Empower within 60 s ranked the friend up again (rank " + VeterancyServerEvents.getRank(target) + ")");
                if (Essence.get(second) != 30 || ((Unit) second).getCooldown(b) > 0)
                    problems.add("the refused second Empower spent essence (" + Essence.get(second) + " of 30) or set the cooldown");
                // 60 s later
                target.getPersistentData().putLong(Empower.KEY_LAST, level.getGameTime() - Empower.RANK_EVERY_TICKS);
                b.use(level, (Unit) second, target);
                if (VeterancyServerEvents.getRank(target) != 2 || Essence.get(second) != 15)
                    problems.add("60 s later the second Empower left the friend at rank " + VeterancyServerEvents.getRank(target)
                        + " and the caster with " + Essence.get(second) + ", expected rank 2 and 15");
            } finally {
                discardAll(all);
            }
            finish(helper, problems);
        });
    }

    /**
     * Never on a hero, a commander, an experimental (a Sun Colossus) or a summon: each refused with nothing spent and no
     * cooldown.
     */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = BATCH)
    public static void essence_base_empower_refuses_heroes_commanders_experimentals_and_summons(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String mine = owner(helper, "emprefuse");
        Mob caster = carrier(helper, mine, 7, 7, 30);
        Mob hero = HeroGameTests.spawnHero(helper, HeroKits.DAWN_PALADIN, mine, 7, 3);
        Mob commander = still(helper, EntityRegistrar.VILLAGER_UNIT.get(), mine, 3, 7);
        Mob colossus = still(helper, EntityRegistrar.SUN_COLOSSUS_UNIT.get(), mine, 11, 11);
        Mob summon = still(helper, EntityRegistrar.VINDICATOR_UNIT.get(), mine, 11, 4);
        List<Entity> all = present(caster, hero, commander, colossus, summon);
        if (all.size() != 5) {
            discardAll(all);
            helper.fail("could not spawn the caster and its four targets");
            return;
        }
        hero.setNoAi(true);
        CommanderServerEvents.makeCommander(commander);
        Essence.markSummoned(summon);
        Empower ability = new Empower();
        helper.runAfterDelay(2, () -> {
            List<String> problems = new ArrayList<>();
            try {
                Mob[] targets = { hero, commander, colossus, summon };
                String[] names = { "a hero", "a commander", "an experimental", "a summon" };
                for (int i = 0; i < targets.length; i++) {
                    float xp = VeterancyServerEvents.getXp(targets[i]);
                    ability.use(level, (Unit) caster, targets[i]);
                    if (VeterancyServerEvents.getXp(targets[i]) != xp)
                        problems.add("Empower ranked up " + names[i]);
                    if (Empower.cast(level, caster, mine, targets[i], true))
                        problems.add("cast() reports success on " + names[i]);
                }
                if (Essence.get(caster) != 30)
                    problems.add("refused casts spent essence (" + Essence.get(caster) + " left of 30)");
                if (((Unit) caster).getCooldown(ability) > 0)
                    problems.add("a refused Empower set the cooldown");
            } finally {
                discardAll(all);
            }
            finish(helper, problems);
        });
    }

    /**
     * The hook a later PR uses for the Grave Adept (120 XP instead of a level): a hook set for a unit type replaces the
     * rank - even on a summon, as the Adept is one - and the cast still costs 15 essence and sets the cooldown.
     */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = BATCH)
    public static void essence_base_empower_target_hook_replaces_the_rank(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String mine = owner(helper, "emphook");
        Mob caster = carrier(helper, mine, 4, 12, 30);
        Mob witch = still(helper, EntityRegistrar.WITCH_UNIT.get(), mine, 7, 12);
        List<Entity> all = present(caster, witch);
        if (all.size() != 2) {
            discardAll(all);
            helper.fail("could not spawn the caster and the hooked unit");
            return;
        }
        Essence.markSummoned(witch);
        List<LivingEntity> hooked = new ArrayList<>();
        Empower ability = new Empower();
        helper.runAfterDelay(2, () -> {
            List<String> problems = new ArrayList<>();
            try {
                Empower.setTargetHook(EntityRegistrar.WITCH_UNIT.get(), (sl, c, t) -> hooked.add(t));
                ability.use(level, (Unit) caster, witch);
                if (hooked.size() != 1 || hooked.get(0) != witch)
                    problems.add("the hook ran " + hooked.size() + " times on the hooked unit, expected once");
                if (VeterancyServerEvents.getXp(witch) != 0)
                    problems.add("the hooked unit also got a veterancy rank");
                if (Essence.get(caster) != 15 || ((Unit) caster).getCooldown(ability) <= 0)
                    problems.add("a hooked Empower should still cost 15 essence and set the cooldown (holds " + Essence.get(caster) + ")");
            } finally {
                Empower.clearTargetHook(EntityRegistrar.WITCH_UNIT.get());
                discardAll(all);
            }
            finish(helper, problems);
        });
    }

    // ------------------------------------------------------------------------------------- bots

    /** A building-targeted essence spell for the test: it pays its essence and remembers where it was cast. */
    static class EssenceProbe extends Ability {
        final int cost;
        BlockPos castAt = null;

        EssenceProbe(int cost) {
            super(UnitAction.EMPOWER, 10 * ResourceCost.TICKS_PER_SECOND, 6, 0, false);
            this.cost = cost;
            this.autocastEssence = cost;
            this.autoRule(AutoRule.BUILDING_HURT);
        }

        @Override
        public void use(Level level, Unit unitUsing, BlockPos targetBp) {
            if (Essence.trySpend((LivingEntity) unitUsing, cost)) {
                castAt = targetBp;
                setToMaxCooldown(unitUsing);
            }
        }
    }

    /**
     * The AutoCaster gates an essence spell on the caster's essence (14 of 15: no cast, nothing ordered) and its
     * BUILDING_HURT rule aims it at a friendly building under 70% health in reach - never a healthy one or an enemy's.
     */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = BATCH)
    public static void essence_base_autocast_waits_for_essence_and_aims_at_a_hurt_building(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String mine = owner(helper, "bothurt"), foe = owner(helper, "bothurtfoe");
        Mob caster = carrier(helper, mine, 4, 9, 14);
        if (caster == null) {
            helper.fail("could not spawn the caster");
            return;
        }
        EssenceProbe probe = new EssenceProbe(15);
        ((Unit) caster).getAbilities().add(probe);
        BuildingPlacement b = BuildingUtils.getNewBuildingPlacement(Buildings.WIND_GENERATOR_VILLAGERS, level,
            helper.absolutePos(new BlockPos(8, 1, 7)), Rotation.NONE, mine, false);
        for (var block : b.getBlocks())
            if (!block.getBlockState().isAir())
                level.setBlock(block.getBlockPos(), block.getBlockState(), 3);
        b.initPlacedBlocks();
        b.isBuilt = true;
        BuildingServerEvents.getBuildings().add(b);
        helper.runAfterDelay(2, () -> {
            List<String> problems = new ArrayList<>();
            try {
                if (AutoCaster.look(level, caster, (Unit) caster) || probe.castAt != null)
                    problems.add("it cast at a building at full health");
                for (int i = 0; i < 400 && b.getHealth() >= b.getMaxHealth() * 0.7f && b.getHealth() > 0; i++)
                    b.destroyRandomBlocks(1);
                if (b.getHealth() >= b.getMaxHealth() * 0.7f || b.getHealth() <= 0)
                    problems.add("setup: could not bring the building under 70% health (" + b.getHealth() + "/" + b.getMaxHealth() + ")");
                if (AutoCaster.look(level, caster, (Unit) caster) || probe.castAt != null || Essence.get(caster) != 14)
                    problems.add("with 14 of 15 essence it still cast (or ordered a cast)");
                Essence.add(caster, 1);
                boolean cast = AutoCaster.look(level, caster, (Unit) caster);
                if (!cast || probe.castAt == null)
                    problems.add("with 15 essence and a hurt friendly building in reach it did not cast");
                else if (!new AABB(b.minCorner).minmax(new AABB(b.maxCorner)).inflate(1).contains(Vec3.atCenterOf(probe.castAt)))
                    problems.add("it cast at " + probe.castAt.toShortString() + ", not at the hurt building");
                if (Essence.get(caster) != 0)
                    problems.add("the cast left " + Essence.get(caster) + " essence, expected 0");
                // the same hurt building, now the enemy's: never
                ((Unit) caster).setCooldown(probe, 0);
                Essence.add(caster, 15);
                probe.castAt = null;
                b.ownerName = foe;
                if (AutoCaster.look(level, caster, (Unit) caster) || probe.castAt != null)
                    problems.add("it cast at an enemy's hurt building");
            } finally {
                BuildingServerEvents.getBuildings().remove(b);
                for (var block : b.getBlocks())
                    level.setBlock(block.getBlockPos(), Blocks.AIR.defaultBlockState(), 3);
                caster.discard();
            }
            finish(helper, problems);
        });
    }
}
