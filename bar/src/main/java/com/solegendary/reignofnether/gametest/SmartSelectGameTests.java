package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.unit.SmartSelect;
import com.solegendary.reignofnether.unit.SmartSelect.Mods;
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
 * BAR's Smart Select rules on a drag box (SmartSelect, after aegis' widget): with real units in the arena (zombies and
 * a skeleton as combat units, villagers as workers, a totem as the immobile "building"), the pure filter is fed the
 * lists a box would and must give what a BAR player expects - mobiles over buildings, combat over workers, Alt combat
 * only, Z same type, Space idle only, Shift adds, Ctrl subtracts.
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class SmartSelectGameTests {

    static final String ARENA = SkirmishGameTests.ARENA;

    /** The units one test spawns, in a row on open ground; discarded when it ends. */
    private static final class Crowd {
        final GameTestHelper helper;
        final ServerLevel level;
        final String owner;
        final List<Entity> made = new ArrayList<>();
        int next = 1;

        Crowd(GameTestHelper helper) {
            this.helper = helper;
            this.level = helper.getLevel();
            this.owner = "gametest_smart_" + helper.absolutePos(BlockPos.ZERO).asLong();
            SkirmishGameTests.openGround(helper, 0, 0, 15, 15);
        }

        LivingEntity spawn(EntityType<? extends Mob> type) {
            Mob m = CombatEngineerGameTests.spawn(level, type, owner, helper.absolutePos(new BlockPos(next, 2, 4)), made);
            next += 2;
            if (m == null)
                throw new IllegalStateException("could not create " + type);
            return m;
        }

        LivingEntity zombie()   { return spawn(EntityRegistrar.ZOMBIE_UNIT.get()); }
        LivingEntity skeleton() { return spawn(EntityRegistrar.SKELETON_UNIT.get()); }
        LivingEntity worker()   { return spawn(EntityRegistrar.VILLAGER_UNIT.get()); }
        LivingEntity totem()    { return spawn(EntityRegistrar.TOTEM_OF_REGENERATION.get()); }

        void finish(List<String> problems) {
            for (Entity e : made)
                if (!e.isRemoved())
                    e.discard();
            CombatEngineerGameTests.finish(helper, problems);
        }
    }

    private static List<LivingEntity> box(LivingEntity... units) {
        return new ArrayList<>(List.of(units));
    }

    /** Adds a problem when the filter's result is not exactly these units (same order). */
    private static void expect(List<String> problems, String what, List<LivingEntity> got, LivingEntity... want) {
        List<LivingEntity> w = List.of(want);
        if (!got.equals(w))
            problems.add(what + ": expected " + names(w) + " but got " + names(got));
    }

    private static String names(List<LivingEntity> units) {
        List<String> out = new ArrayList<>();
        for (LivingEntity le : units)
            out.add(le.getType().toShortString() + "#" + le.getId());
        return out.toString();
    }

    private static final Mods ALT = new Mods(false, false, true, false, false);
    private static final Mods SAME_TYPE = new Mods(false, false, false, true, false);
    private static final Mods IDLE = new Mods(false, false, false, false, true);
    private static final Mods SHIFT = new Mods(true, false, false, false, false);
    private static final Mods CTRL = new Mods(false, true, false, false, false);

    @GameTest(template = ARENA, timeoutTicks = 40)
    public static void smart_select_drops_buildings_when_a_mobile_unit_is_in_the_box(GameTestHelper helper) {
        Crowd c = new Crowd(helper);
        List<String> problems = new ArrayList<>();
        LivingEntity totem = c.totem(), zombie = c.zombie();
        expect(problems, "totem + zombie", SmartSelect.filter(box(totem, zombie), box(), Mods.NONE), zombie);
        expect(problems, "totem alone", SmartSelect.filter(box(totem), box(), Mods.NONE), totem);
        c.finish(problems);
    }

    @GameTest(template = ARENA, timeoutTicks = 40)
    public static void smart_select_prefers_combat_units_over_workers(GameTestHelper helper) {
        Crowd c = new Crowd(helper);
        List<String> problems = new ArrayList<>();
        LivingEntity w1 = c.worker(), zombie = c.zombie(), w2 = c.worker(), skeleton = c.skeleton();
        expect(problems, "workers around combat", SmartSelect.filter(box(w1, zombie, w2, skeleton), box(), Mods.NONE),
            zombie, skeleton);
        c.finish(problems);
    }

    @GameTest(template = ARENA, timeoutTicks = 40)
    public static void smart_select_takes_workers_only_when_no_combat_unit_is_in_the_box(GameTestHelper helper) {
        Crowd c = new Crowd(helper);
        List<String> problems = new ArrayList<>();
        LivingEntity w1 = c.worker(), w2 = c.worker(), totem = c.totem();
        expect(problems, "workers alone", SmartSelect.filter(box(w1, w2), box(), Mods.NONE), w1, w2);
        expect(problems, "workers + totem", SmartSelect.filter(box(w1, totem, w2), box(), Mods.NONE), w1, w2);
        c.finish(problems);
    }

    @GameTest(template = ARENA, timeoutTicks = 40)
    public static void smart_select_alt_keeps_only_mobile_combat_units(GameTestHelper helper) {
        Crowd c = new Crowd(helper);
        List<String> problems = new ArrayList<>();
        LivingEntity worker = c.worker(), zombie = c.zombie(), totem = c.totem(), skeleton = c.skeleton();
        expect(problems, "Alt over a crowd", SmartSelect.filter(box(worker, zombie, totem, skeleton), box(), ALT),
            zombie, skeleton);
        expect(problems, "Alt over workers only", SmartSelect.filter(box(worker, totem), box(), ALT));
        c.finish(problems);
    }

    @GameTest(template = ARENA, timeoutTicks = 40)
    public static void smart_select_z_keeps_only_the_types_already_selected(GameTestHelper helper) {
        Crowd c = new Crowd(helper);
        List<String> problems = new ArrayList<>();
        LivingEntity z1 = c.zombie(), z2 = c.zombie(), skeleton = c.skeleton(), worker = c.worker();
        expect(problems, "Z with a zombie selected", SmartSelect.filter(box(z2, skeleton, worker), box(z1), SAME_TYPE), z2);
        expect(problems, "Z with nothing selected is no filter",
            SmartSelect.filter(box(z2, skeleton, worker), box(), SAME_TYPE), z2, skeleton);
        c.finish(problems);
    }

    @GameTest(template = ARENA, timeoutTicks = 40)
    public static void smart_select_space_keeps_only_idle_units(GameTestHelper helper) {
        Crowd c = new Crowd(helper);
        List<String> problems = new ArrayList<>();
        LivingEntity idle = c.zombie(), busy = c.zombie();
        // a move order far away: Unit.isIdle is false from the moment the target is set
        ((Unit) busy).getMoveGoal().setMoveTarget(helper.absolutePos(new BlockPos(14, 2, 14)));
        if (!((Unit) idle).isIdle())
            problems.add("a freshly spawned zombie should be idle");
        if (((Unit) busy).isIdle())
            problems.add("a zombie with a move order should not be idle");
        expect(problems, "Space over idle + busy", SmartSelect.filter(box(idle, busy), box(), IDLE), idle);
        c.finish(problems);
    }

    @GameTest(template = ARENA, timeoutTicks = 40)
    public static void smart_select_shift_adds_the_box_to_the_selection(GameTestHelper helper) {
        Crowd c = new Crowd(helper);
        List<String> problems = new ArrayList<>();
        LivingEntity z1 = c.zombie(), z2 = c.zombie(), worker = c.worker();
        expect(problems, "Shift adds", SmartSelect.filter(box(worker, z2), box(z1), SHIFT), z1, z2);
        expect(problems, "Shift never duplicates", SmartSelect.filter(box(z1, z2), box(z1), SHIFT), z1, z2);
        c.finish(problems);
    }

    @GameTest(template = ARENA, timeoutTicks = 40)
    public static void smart_select_ctrl_subtracts_the_box_from_the_selection(GameTestHelper helper) {
        Crowd c = new Crowd(helper);
        List<String> problems = new ArrayList<>();
        LivingEntity z1 = c.zombie(), z2 = c.zombie(), w1 = c.worker(), w2 = c.worker();
        // the box is filtered first: Ctrl over z1 and w2 removes z1, and w2 (dropped beside a combat unit) was
        // never selected anyway
        expect(problems, "Ctrl subtracts", SmartSelect.filter(box(z1, w2), box(z1, z2, w1), CTRL), z2, w1);
        expect(problems, "Ctrl over workers alone", SmartSelect.filter(box(w1), box(z1, z2, w1), CTRL), z1, z2);
        c.finish(problems);
    }
}
