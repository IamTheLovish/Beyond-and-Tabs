package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.ability.Ability;
import com.solegendary.reignofnether.ability.AutoCaster;
import com.solegendary.reignofnether.ability.AutoRule;
import com.solegendary.reignofnether.ability.AutocastState;
import com.solegendary.reignofnether.ability.abilities.Bloom;
import com.solegendary.reignofnether.hero.HeroKits;
import com.solegendary.reignofnether.hero.HeroTypes;
import com.solegendary.reignofnether.player.CommanderServerEvents;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.unit.UnitAction;
import com.solegendary.reignofnether.unit.UnitActionItem;
import com.solegendary.reignofnether.unit.UnitGrid;
import com.solegendary.reignofnether.unit.interfaces.Unit;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * Headless tests of the autocast (ability/AutoCaster, ability/AutocastState): the developer's rule that every non-hero
 * unit uses its abilities by itself. A healer casts on a hurt friend and not on a whole one; a striker on an enemy in
 * reach and not on one out of it; a hero and a commander never; the per-ability switch is honoured through the old API
 * and the generic order; and a [Stress] line times a full pass over 200 casters. Small arenas, unique owners per test,
 * everything inside the test's own 16x16 (SkirmishGameTests.openGround), every unit discarded in a finally.
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class AutocastGameTests {

    static final String ARENA = SkirmishGameTests.ARENA;

    static String owner(GameTestHelper helper, String tag) {
        return "gametest_auto_" + tag + "_" + helper.absolutePos(BlockPos.ZERO).asLong();
    }

    static Mob spawn(ServerLevel level, EntityType<? extends Mob> type, String owner, BlockPos at, List<Entity> made) {
        Mob m = type.create(level);
        if (m == null)
            return null;
        m.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
        if (m instanceof Unit u)
            u.setOwnerName(owner);
        level.addFreshEntity(m);
        made.add(m);
        return m;
    }

    static void cleanUp(List<Entity> made) {
        for (Entity e : made)
            if (!e.isRemoved())
                e.discard();
    }

    static void finish(GameTestHelper helper, List<String> problems) {
        if (problems.isEmpty())
            helper.succeed();
        else
            helper.fail(String.join("; ", problems));
    }

    static Ability abilityOf(Mob m, Class<? extends Ability> cls) {
        for (Ability a : ((Unit) m).getAbilities().get())
            if (cls.isInstance(a))
                return a;
        return null;
    }

    static float lowestCooldown(Mob m) {
        float best = 0;
        for (Ability a : ((Unit) m).getAbilities().get())
            best = Math.max(best, a.getCooldown((Unit) m));
        return best;
    }

    /**
     * A Bloom Priestess (Bloom: ALLY_HURT, a self-centred heal) casts by itself when a friend 3 blocks away stands at
     * half health - the friend is healed and Bloom goes on cooldown - and a second priestess beside a whole friend does
     * not cast at all. Two groups, two owners, 8 blocks apart (outside each other's Bloom radius).
     */
    @GameTest(template = ARENA, timeoutTicks = 60)
    public static void healer_autocasts_on_a_hurt_ally_and_not_on_a_healthy_one(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        SkirmishGameTests.openGround(helper, 0, 0, 15, 15);
        List<Entity> made = new ArrayList<>();
        List<String> problems = new ArrayList<>();
        String a = owner(helper, "heal_a"), b = owner(helper, "heal_b");
        Mob healerA = spawn(level, EntityRegistrar.BLOOM_PRIESTESS_UNIT.get(), a, helper.absolutePos(new BlockPos(2, 2, 2)), made);
        Mob hurt = spawn(level, EntityRegistrar.VILLAGER_UNIT.get(), a, helper.absolutePos(new BlockPos(2, 2, 5)), made);
        Mob healerB = spawn(level, EntityRegistrar.BLOOM_PRIESTESS_UNIT.get(), b, helper.absolutePos(new BlockPos(13, 2, 2)), made);
        Mob whole = spawn(level, EntityRegistrar.VILLAGER_UNIT.get(), b, helper.absolutePos(new BlockPos(13, 2, 5)), made);
        if (healerA == null || hurt == null || healerB == null || whole == null) {
            cleanUp(made);
            helper.fail("could not create the priestesses and villagers");
            return;
        }
        Ability bloomA = abilityOf(healerA, Bloom.class), bloomB = abilityOf(healerB, Bloom.class);
        if (bloomA == null || bloomB == null || bloomA.getAutoRule() != AutoRule.ALLY_HURT) {
            cleanUp(made);
            helper.fail("the priestess has no Bloom, or its rule is not ALLY_HURT");
            return;
        }
        hurt.setHealth(hurt.getMaxHealth() * 0.5f);
        helper.runAfterDelay(5, () -> {
            try {
                UnitGrid.invalidate();
                hurt.setHealth(Math.min(hurt.getHealth(), hurt.getMaxHealth() * 0.5f));   // in case the tick's pass already healed it
                float before = hurt.getHealth();
                boolean cast = AutoCaster.look(level, healerA, (Unit) healerA);
                if (!cast && bloomA.getCooldown((Unit) healerA) <= 0)
                    problems.add("the priestess beside a half-health friend did not cast Bloom");
                if (bloomA.getCooldown((Unit) healerA) <= 0)
                    problems.add("Bloom is not on cooldown after the autocast");
                if (hurt.getHealth() <= before && cast)
                    problems.add("the hurt friend was not healed (" + before + " -> " + hurt.getHealth() + ")");
                if (AutoCaster.look(level, healerB, (Unit) healerB))
                    problems.add("the priestess beside a whole friend cast anyway");
                if (bloomB.getCooldown((Unit) healerB) > 0)
                    problems.add("Bloom went on cooldown beside a whole friend");
                finish(helper, problems);
            } finally {
                cleanUp(made);
            }
        });
    }

    /**
     * A Blaze (Magma Rupture: ENEMY_IN_RANGE, a 12-block crack toward the enemy's feet; Firewall Shot is MANUAL) casts
     * by itself on an enemy 5 blocks away - an ability goes on cooldown - and a second Blaze whose nearest stranger is
     * 16+ blocks off (past every reach) never casts; neither casts at the friend standing next to it.
     */
    @GameTest(template = ARENA, timeoutTicks = 60)
    public static void striker_autocasts_on_an_enemy_in_range_and_not_out_of_range(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        SkirmishGameTests.openGround(helper, 0, 0, 15, 15);
        List<Entity> made = new ArrayList<>();
        List<String> problems = new ArrayList<>();
        String a = owner(helper, "strike_a"), foeA = a + "_foe", b = owner(helper, "strike_b");
        Mob blazeA = spawn(level, EntityRegistrar.BLAZE_UNIT.get(), a, helper.absolutePos(new BlockPos(1, 2, 1)), made);
        Mob friendA = spawn(level, EntityRegistrar.VILLAGER_UNIT.get(), a, helper.absolutePos(new BlockPos(2, 2, 1)), made);
        Mob enemy = spawn(level, EntityRegistrar.VILLAGER_UNIT.get(), foeA, helper.absolutePos(new BlockPos(1, 2, 6)), made);
        // the far Blaze in the opposite corner: its only neighbour is its own friend; every stranger of group A is 16+
        // blocks off (past Magma Rupture's 12)
        Mob blazeB = spawn(level, EntityRegistrar.BLAZE_UNIT.get(), b, helper.absolutePos(new BlockPos(15, 2, 15)), made);
        Mob friendB = spawn(level, EntityRegistrar.VILLAGER_UNIT.get(), b, helper.absolutePos(new BlockPos(14, 2, 15)), made);
        if (blazeA == null || friendA == null || enemy == null || blazeB == null || friendB == null) {
            cleanUp(made);
            helper.fail("could not create the blazes and villagers");
            return;
        }
        float friendHp = friendA.getHealth();
        helper.runAfterDelay(5, () -> {
            try {
                UnitGrid.invalidate();
                boolean cast = AutoCaster.look(level, blazeA, (Unit) blazeA);
                if (!cast && lowestCooldown(blazeA) <= 0)
                    problems.add("the Blaze with an enemy 5 blocks away did not cast");
                if (lowestCooldown(blazeA) <= 0)
                    problems.add("no Blaze ability went on cooldown after the autocast");
                if (AutoCaster.look(level, blazeB, (Unit) blazeB))
                    problems.add("the Blaze with no enemy in reach cast anyway");
                if (lowestCooldown(blazeB) > 0)
                    problems.add("a Blaze ability went on cooldown with no enemy in reach");
                if (friendA.getHealth() < friendHp - 0.01f)
                    problems.add("the Blaze's own friend was hurt by the autocast");
                finish(helper, problems);
            } finally {
                cleanUp(made);
            }
        });
    }

    /**
     * A faction hero and a commander never autocast: AutocastState.isHero says so, none of their ability buttons carries
     * a switch, and a full look with an enemy 4 blocks away gives no order and sets no cooldown.
     */
    @GameTest(template = ARENA, timeoutTicks = 60)
    public static void heroes_and_commanders_never_autocast(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        SkirmishGameTests.openGround(helper, 0, 0, 15, 15);
        List<Entity> made = new ArrayList<>();
        List<String> problems = new ArrayList<>();
        String a = owner(helper, "hero"), foe = a + "_foe";
        EntityType<? extends Mob> heroType = HeroTypes.typeOf(HeroKits.ALL.get(0));
        Mob hero = heroType == null ? null : spawn(level, heroType, a, helper.absolutePos(new BlockPos(2, 2, 2)), made);
        Mob commander = spawn(level, EntityRegistrar.VILLAGER_UNIT.get(), a, helper.absolutePos(new BlockPos(12, 2, 2)), made);
        Mob enemy1 = spawn(level, EntityRegistrar.VILLAGER_UNIT.get(), foe, helper.absolutePos(new BlockPos(2, 2, 6)), made);
        Mob enemy2 = spawn(level, EntityRegistrar.VILLAGER_UNIT.get(), foe, helper.absolutePos(new BlockPos(12, 2, 6)), made);
        if (hero == null || commander == null || enemy1 == null || enemy2 == null) {
            cleanUp(made);
            helper.fail("could not create the hero, the commander and their enemies");
            return;
        }
        CommanderServerEvents.makeCommander(commander);
        helper.runAfterDelay(5, () -> {
            try {
                UnitGrid.invalidate();
                for (Mob m : List.of(hero, commander)) {
                    String tag = m == hero ? "hero" : "commander";
                    if (!AutocastState.isHero(m))
                        problems.add(tag + ": not a hero to AutocastState");
                    for (Ability ab : ((Unit) m).getAbilities().get())
                        if (ab.hasAutocastSwitch((Unit) m))
                            problems.add(tag + ": " + ab.getClass().getSimpleName() + " has an autocast switch");
                    if (AutoCaster.look(level, m, (Unit) m))
                        problems.add(tag + ": autocast gave an order");
                    if (lowestCooldown(m) > 0)
                        problems.add(tag + ": an ability went on cooldown");
                }
                finish(helper, problems);
            } finally {
                cleanUp(made);
            }
        });
    }

    /**
     * The switch is honoured: Bloom is on by default; the old API turns it off (setAutocast(false)) and the priestess
     * beside a half-health friend no longer casts; the generic AUTOCAST_ENABLE order (the ability's action ordinal in
     * x, the order a button's right click sends) turns it back on and she casts; the override survives a save round
     * trip of the unit's data; and a MANUAL ability (the Elder Druid's Awaken Thicket) is off and has no switch.
     */
    @GameTest(template = ARENA, timeoutTicks = 60)
    public static void autocast_toggle_is_honoured(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        SkirmishGameTests.openGround(helper, 0, 0, 15, 15);
        List<Entity> made = new ArrayList<>();
        List<String> problems = new ArrayList<>();
        String a = owner(helper, "toggle");
        Mob healer = spawn(level, EntityRegistrar.BLOOM_PRIESTESS_UNIT.get(), a, helper.absolutePos(new BlockPos(2, 2, 2)), made);
        Mob hurt = spawn(level, EntityRegistrar.VILLAGER_UNIT.get(), a, helper.absolutePos(new BlockPos(2, 2, 5)), made);
        if (healer == null || hurt == null) {
            cleanUp(made);
            helper.fail("could not create the priestess and the villager");
            return;
        }
        Unit hu = (Unit) healer;
        Ability bloom = abilityOf(healer, Bloom.class);
        if (bloom == null) {
            cleanUp(made);
            helper.fail("the priestess has no Bloom");
            return;
        }
        // off before the first tick's pass can cast
        bloom.setAutocast(false, hu);
        hurt.setHealth(hurt.getMaxHealth() * 0.5f);
        helper.runAfterDelay(5, () -> {
            try {
                UnitGrid.invalidate();
                if (hu.hasAutocast(bloom) || bloom.isAutocasting(hu))
                    problems.add("setAutocast(false) left the switch on");
                hurt.setHealth(Math.min(hurt.getHealth(), hurt.getMaxHealth() * 0.5f));
                if (AutoCaster.look(level, healer, hu) || bloom.getCooldown(hu) > 0)
                    problems.add("the priestess cast with the switch off");
                // the switch survives the unit's save data
                net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
                AutocastState.save(hu, tag);
                AutocastState.setOverrides(hu, null);
                if (!hu.hasAutocast(bloom))
                    problems.add("clearing the overrides did not restore the on-by-default");
                AutocastState.load(hu, tag);
                if (hu.hasAutocast(bloom))
                    problems.add("the off switch did not survive a save round trip");
                // the generic order a right click sends
                new UnitActionItem(a, UnitAction.AUTOCAST_ENABLE, -1, new int[] { healer.getId() },
                    new BlockPos(bloom.action.ordinal(), 0, 0), BlockPos.ZERO).action(level);
                if (!hu.hasAutocast(bloom))
                    problems.add("AUTOCAST_ENABLE did not turn the switch on");
                boolean cast = AutoCaster.look(level, healer, hu);
                if (!cast && bloom.getCooldown(hu) <= 0)
                    problems.add("the priestess did not cast once the switch was back on");
                new UnitActionItem(a, UnitAction.AUTOCAST_DISABLE, -1, new int[] { healer.getId() },
                    new BlockPos(bloom.action.ordinal(), 0, 0), BlockPos.ZERO).action(level);
                if (hu.hasAutocast(bloom))
                    problems.add("AUTOCAST_DISABLE did not turn the switch off");
                // a MANUAL ability has no switch and is off
                Ability manual = new com.solegendary.reignofnether.ability.abilities.AwakenThicket();
                if (manual.getAutoRule() != AutoRule.MANUAL || manual.hasAutocastSwitch(hu) || AutocastState.isOn(hu, manual))
                    problems.add("a MANUAL ability is switched on or has a switch");
                finish(helper, problems);
            } finally {
                cleanUp(made);
            }
        });
    }

    /**
     * What a full pass costs: 200 Bloom Priestesses (each with a grid query for a hurt friend, 25 of them finding one
     * and casting through the click path) spawned, looked at and removed in one callback, so they never tick. Logged
     * as "[Stress] autocast" (a CI notice); fails only past 100 ms, a tenth of what the real spread over 20 ticks
     * leaves a pass in a second.
     */
    @GameTest(template = ARENA, timeoutTicks = 200, batch = "stress_autocast")
    public static void stress_autocast_pass_over_200_casters(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        SkirmishGameTests.openGround(helper, 0, 0, 15, 15);
        List<Entity> made = new ArrayList<>();
        List<String> problems = new ArrayList<>();
        String a = owner(helper, "stress");
        helper.runAfterDelay(2, () -> {
            try {
                int casters = 0, hurtOnes = 0;
                for (int x = 0; x < 15; x++)
                    for (int z = 0; z < 15; z++) {
                        boolean hurt = (x + z) % 9 == 0;   // 25 of the 225 cells
                        Mob m = spawn(level, hurt ? EntityRegistrar.VILLAGER_UNIT.get() : EntityRegistrar.BLOOM_PRIESTESS_UNIT.get(),
                            a, helper.absolutePos(new BlockPos(x, 2, z)), made);
                        if (m == null)
                            continue;
                        if (hurt) {
                            m.setHealth(m.getMaxHealth() * 0.4f);
                            hurtOnes++;
                        } else {
                            casters++;
                        }
                    }
                UnitGrid.invalidate();
                // the full pass (-1: every unit, no budget) twice: the second is the steady state (the grid is built)
                AutoCaster.pass(level, -1, Long.MAX_VALUE);
                for (Entity e : made)
                    if (e instanceof Unit u && e instanceof Mob m) {
                        for (Ability ab : u.getAbilities().get())
                            u.setCooldown(ab, 0);
                        if (m.getType() == EntityRegistrar.VILLAGER_UNIT.get())
                            m.setHealth(m.getMaxHealth() * 0.4f);   // the first pass healed them
                    }
                int casts = AutoCaster.pass(level, -1, Long.MAX_VALUE);
                double ms = AutoCaster.lastPassNanos / 1e6;
                ReignOfNether.LOGGER.info("[Stress] autocast: {} casters ({} hurt friends), {} looked at, {} casts: {} ms per full pass ({} us per unit)",
                    casters, hurtOnes, AutoCaster.lastPassUnits, casts, String.format("%.2f", ms),
                    String.format("%.1f", AutoCaster.lastPassUnits == 0 ? 0 : AutoCaster.lastPassNanos / 1e3 / AutoCaster.lastPassUnits));
                if (casters < 190)
                    problems.add("only " + casters + " priestesses could be made");
                if (casts == 0)
                    problems.add("no priestess cast on the hurt friends");
                if (ms > 100)
                    problems.add("a full pass over " + casters + " casters took " + ms + " ms");
                finish(helper, problems);
            } finally {
                cleanUp(made);
            }
        });
    }
}
