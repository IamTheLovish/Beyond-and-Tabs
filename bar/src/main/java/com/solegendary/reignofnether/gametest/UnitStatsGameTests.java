package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.api.ReignOfNetherRegistries;
import com.solegendary.reignofnether.building.production.GraveyardUnitProductionItem;
import com.solegendary.reignofnether.building.production.HeroProductionItem;
import com.solegendary.reignofnether.building.production.IUnitProductionItem;
import com.solegendary.reignofnether.building.production.ProductionItem;
import com.solegendary.reignofnether.building.production.UnitStatTooltip;
import com.solegendary.reignofnether.registrars.AttributeRegistrar;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.unit.UnitStats;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * Headless tests of the unit stats a factory button shows (building/production/UnitStatTooltip, unit/UnitStats; #38
 * item 39): the numbers match the attributes of a real spawned unit, a worker shows no damage or range line, the lines
 * land after the cost block, and every unit production item resolves to a unit type (so no button silently lacks its
 * stats). The attribute check reads the spawned entity, not the type's supplier the stats came from.
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class UnitStatsGameTests {

    static final String ARENA = SkirmishGameTests.ARENA;

    static Mob spawn(GameTestHelper helper, EntityType<? extends Mob> type) {
        ServerLevel level = helper.getLevel();
        Mob m = type.create(level);
        if (m == null)
            return null;
        BlockPos at = helper.absolutePos(new BlockPos(3, 2, 3));
        m.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
        level.addFreshEntity(m);
        return m;
    }

    static void collectKeys(Component c, List<String> keys) {
        if (c.getContents() instanceof TranslatableContents t)
            keys.add(t.getKey());
        for (Component sibling : c.getSiblings())
            collectKeys(sibling, keys);
    }

    static List<String> keysOf(UnitStats stats) {
        List<String> keys = new ArrayList<>();
        for (Component line : stats.lines())
            collectKeys(line, keys);
        return keys;
    }

    static void near(List<String> problems, String label, String what, float shown, double actual) {
        if (Math.abs(shown - actual) > 0.001)
            problems.add(label + ": " + what + " shown " + shown + " but the unit has " + actual);
    }

    static void checkMatches(String label, EntityType<? extends Mob> type, Mob unit, List<String> problems) {
        UnitStats s = UnitStats.of(type);
        if (s == null) {
            problems.add(label + ": no stats for the type");
            return;
        }
        near(problems, label, "health", s.health(), unit.getAttributeBaseValue(Attributes.MAX_HEALTH));
        near(problems, label, "damage", s.damage(), unit.getAttributeBaseValue(AttributeRegistrar.ATTACK_DAMAGE.get()));
        near(problems, label, "attacks/s", s.attacksPerSecond(), unit.getAttributeBaseValue(AttributeRegistrar.ATTACKS_PER_SECOND.get()));
        near(problems, label, "range", s.range(), unit.getAttributeBaseValue(AttributeRegistrar.ATTACK_RANGE.get()));
        near(problems, label, "speed", s.speed(), unit.getAttributeBaseValue(Attributes.MOVEMENT_SPEED));
        near(problems, label, "armour", s.armour(), unit.getAttributeBaseValue(Attributes.ARMOR));
        if (s.health() <= 0)
            problems.add(label + ": no health to show");
    }

    static void finish(GameTestHelper helper, List<String> problems) {
        if (problems.isEmpty())
            helper.succeed();
        else
            helper.fail(String.join("; ", problems));
    }

    /** Three real units (a worker, a melee tank, a ranged unit): the reported numbers are the spawned unit's own. */
    @GameTest(template = ARENA, timeoutTicks = 40)
    public static void shown_stats_match_the_spawned_units_attributes(GameTestHelper helper) {
        SkirmishGameTests.openGround(helper, 0, 0, 15, 15);
        List<String> problems = new ArrayList<>();
        List<Mob> made = new ArrayList<>();
        try {
            List<EntityType<? extends Mob>> types = List.of(
                EntityRegistrar.VILLAGER_UNIT.get(), EntityRegistrar.IRON_GOLEM_UNIT.get(),
                EntityRegistrar.RIFLE_SQUAD_UNIT.get());
            for (EntityType<? extends Mob> type : types) {
                Mob m = spawn(helper, type);
                if (m == null) {
                    problems.add(EntityType.getKey(type) + ": could not spawn");
                    continue;
                }
                made.add(m);
                checkMatches(EntityType.getKey(type).getPath(), type, m, problems);
            }
            UnitStats rifle = UnitStats.of(EntityRegistrar.RIFLE_SQUAD_UNIT.get());
            if (rifle == null || !rifle.kinds().containsAll(List.of(
                UnitStats.Kind.HEALTH, UnitStats.Kind.DAMAGE, UnitStats.Kind.RANGE, UnitStats.Kind.SPEED)))
                problems.add("rifle squad: missing a health/damage/range/speed stat");
            else if (rifle.lines().size() != 2)
                problems.add("rifle squad: expected two compact stat lines, got " + rifle.lines().size());
        } finally {
            for (Mob m : made)
                m.discard();
        }
        finish(helper, problems);
    }

    /** A worker has no attack: no damage or range line (it keeps health and speed). */
    @GameTest(template = ARENA, timeoutTicks = 20)
    public static void a_worker_shows_no_damage_line(GameTestHelper helper) {
        UnitStats villager = UnitStatTooltip.statsOf(EntityRegistrar.VILLAGER_UNIT.get(), helper.getLevel());
        UnitStats golem = UnitStatTooltip.statsOf(EntityRegistrar.IRON_GOLEM_UNIT.get(), helper.getLevel());
        if (villager == null || golem == null) {
            helper.fail("no stats for the villager or the iron golem");
            return;
        }
        List<String> problems = new ArrayList<>();
        if (!UnitStatTooltip.isWorker(EntityRegistrar.VILLAGER_UNIT.get(), helper.getLevel()))
            problems.add("the villager is not detected as a worker");
        if (villager.attacks())
            problems.add("the villager attacks (damage " + villager.damage() + ", " + villager.attacksPerSecond()
                + "/s): damage not removed");
        List<String> keys = keysOf(villager);
        if (keys.contains("hud.reignofnether.unit_stats.damage") || keys.contains("hud.reignofnether.unit_stats.range"))
            problems.add("the worker shows a damage or range stat: " + keys);
        if (!keys.contains("hud.reignofnether.unit_stats.health"))
            problems.add("the worker shows no health: " + keys);
        if (!keysOf(golem).contains("hud.reignofnether.unit_stats.damage"))
            problems.add("the iron golem shows no damage stat");
        finish(helper, problems);
    }

    /** The stats go in after the cost block (the first blank line); a button without a blank line gets them last. */
    @GameTest(template = ARENA, timeoutTicks = 20)
    public static void stats_are_inserted_after_the_cost_lines(GameTestHelper helper) {
        List<FormattedCharSequence> base = List.of(
            Component.literal("Name").getVisualOrderText(), Component.literal("cost").getVisualOrderText(),
            FormattedCharSequence.EMPTY, Component.literal("lore").getVisualOrderText());
        List<Component> stats = UnitStats.of(EntityRegistrar.IRON_GOLEM_UNIT.get()).lines();
        List<FormattedCharSequence> out = UnitStatTooltip.withStats(base, stats);
        List<String> problems = new ArrayList<>();
        if (out.size() != base.size() + stats.size())
            problems.add("expected " + (base.size() + stats.size()) + " lines, got " + out.size());
        else if (out.get(0) != base.get(0) || out.get(1) != base.get(1)
            || out.get(2 + stats.size()) != base.get(2) || out.get(3 + stats.size()) != base.get(3))
            problems.add("the original lines were reordered or lost");
        if (UnitStatTooltip.withStats(base, List.of()) != base)
            problems.add("no stats should leave the tooltip untouched");
        List<FormattedCharSequence> noBlank = UnitStatTooltip.withStats(base.subList(0, 2), stats);
        if (noBlank.size() != 2 + stats.size() || noBlank.get(0) != base.get(0))
            problems.add("without a blank line the stats should be appended");
        finish(helper, problems);
    }

    /** Every unit production item resolves to a unit type with attributes, so no factory button silently lacks stats. */
    @GameTest(template = ARENA, timeoutTicks = 20)
    public static void every_unit_production_item_has_stats(GameTestHelper helper) {
        List<String> missing = new ArrayList<>();
        int checked = 0;
        for (ProductionItem item : ReignOfNetherRegistries.PRODUCTION_ITEM) {
            if (!(item instanceof IUnitProductionItem || item instanceof HeroProductionItem
                || item instanceof GraveyardUnitProductionItem))
                continue;
            checked++;
            EntityType<?> type = UnitStatTooltip.unitTypeOf(item);
            UnitStats stats = UnitStats.of(type);
            if (type == null)
                missing.add(item.getItemName() + " (no unit type)");
            else if (stats == null || stats.health() <= 0)
                missing.add(item.getItemName() + " (no attributes)");
        }
        System.out.println("[UnitStats] checked " + checked + " unit production items, " + missing.size() + " without stats");
        if (checked < 50)
            helper.fail("only " + checked + " unit production items found - is the registry empty?");
        else if (missing.isEmpty())
            helper.succeed();
        else
            helper.fail("unit production items without stats: " + String.join(", ", missing));
    }
}
