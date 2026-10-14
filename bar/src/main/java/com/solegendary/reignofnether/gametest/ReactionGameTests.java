package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.ability.AutocastState;
import com.solegendary.reignofnether.ability.abilities.BraceAbility;
import com.solegendary.reignofnether.ability.abilities.KiteAbility;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.unit.EngagementStance;
import com.solegendary.reignofnether.unit.UnitDamage;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.reactions.Brace;
import com.solegendary.reignofnether.unit.reactions.Kite;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * Combat reactions, Round A (claude/combat-reactions.md): Kite and Brace. Each test lays its own glass pad at a
 * height no other test uses (262-270), spawns a Crossbow Levy or a Tabard Paladin against Zombies with set targets,
 * and reads the reaction's own counters. Diagnostics go out as "[Reaction]" lines. Everything is discarded in a
 * finally.
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class ReactionGameTests {

    static final String ARENA = SkirmishGameTests.ARENA;
    static final int PAD_W = 32, PAD_D = 12;

    static String owner(GameTestHelper helper, String tag) {
        return "gametest_react_" + tag + "_" + helper.absolutePos(BlockPos.ZERO).asLong();
    }

    /** A glass pad from the arena corner, PAD_W along x and PAD_D along z, at {@code y}. */
    static BlockPos pad(GameTestHelper helper, int y) {
        ServerLevel level = helper.getLevel();
        BlockPos corner = helper.absolutePos(BlockPos.ZERO);
        for (int dx = 0; dx < PAD_W; dx++)
            for (int dz = 0; dz < PAD_D; dz++) {
                level.setBlock(new BlockPos(corner.getX() + dx, y, corner.getZ() + dz), Blocks.GLASS.defaultBlockState(), 3);
                for (int dy = 1; dy <= 3; dy++)
                    level.setBlock(new BlockPos(corner.getX() + dx, y + dy, corner.getZ() + dz), Blocks.AIR.defaultBlockState(), 3);
            }
        return new BlockPos(corner.getX(), y + 1, corner.getZ());
    }

    static Mob spawn(ServerLevel level, EntityType<? extends Mob> type, String owner, double x, double y, double z, List<Entity> made) {
        Mob m = type.create(level);
        if (m == null)
            return null;
        m.moveTo(x, y, z, 0, 0);
        if (m instanceof Unit u)
            u.setOwnerName(owner);
        level.addFreshEntity(m);
        made.add(m);
        return m;
    }

    static void speedTo(Mob m, double speed) {
        var a = m.getAttribute(Attributes.MOVEMENT_SPEED);
        if (a != null)
            a.setBaseValue(speed);
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

    static String f(double d) {
        return String.format(java.util.Locale.ROOT, "%.2f", d);
    }

    /** A Levy with the switch on, a slower Zombie three blocks away that has it as its target: within 40 ticks the Levy has stepped away. */
    @GameTest(template = ARENA, timeoutTicks = 120)
    public static void kite_steps_back_from_a_closing_melee_unit(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos p = pad(helper, 262);
        List<Entity> made = new ArrayList<>();
        List<String> problems = new ArrayList<>();
        String mine = owner(helper, "kite_a"), theirs = owner(helper, "kite_b");
        Mob levy = spawn(level, EntityRegistrar.PILLAGER_UNIT.get(), mine, p.getX() + 8.5, p.getY(), p.getZ() + 6.5, made);
        Mob foe = spawn(level, EntityRegistrar.ZOMBIE_UNIT.get(), theirs, p.getX() + 5.5, p.getY(), p.getZ() + 6.5, made);
        if (levy == null || foe == null) {
            cleanUp(made);
            helper.fail("could not create the levy and the zombie");
            return;
        }
        if (!Kite.has((Unit) levy) || !AutocastState.isOn((Unit) levy, KiteAbility.INSTANCE))
            problems.add("the Crossbow Levy has no Kite switch, or it is off by default");
        foe.setNoAi(true);
        foe.setTarget(levy);
        speedTo(foe, ((Unit) levy).getBaseMovementSpeed() * 0.9);
        EngagementStance.set(foe, EngagementStance.HOLD_FIRE);
        levy.setTarget(foe);
        double startX = levy.getX();
        helper.runAfterDelay(40, () -> {
            try {
                String where = "levy moved " + f(levy.getX() - startX) + " along x (steps " + Kite.steps(levy) + "), "
                    + f(levy.distanceTo(foe)) + " from the zombie, stepping " + Kite.stepping(levy);
                ReignOfNether.LOGGER.info("[Reaction] kite: {}", where);
                if (Kite.steps(levy) < 1)
                    problems.add("the levy took no step: " + where);
                if (levy.getX() - startX < 1.5)
                    problems.add("the levy did not move away from the zombie: " + where);
                finish(helper, problems);
            } finally {
                cleanUp(made);
            }
        });
    }

    /** The same, but the Zombie is 1.3x the Levy's speed: kiting a faster unit is a slow death, so it stands and shoots. */
    @GameTest(template = ARENA, timeoutTicks = 120)
    public static void kite_does_not_run_from_a_faster_enemy(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos p = pad(helper, 265);
        List<Entity> made = new ArrayList<>();
        List<String> problems = new ArrayList<>();
        String mine = owner(helper, "kitef_a"), theirs = owner(helper, "kitef_b");
        Mob levy = spawn(level, EntityRegistrar.PILLAGER_UNIT.get(), mine, p.getX() + 8.5, p.getY(), p.getZ() + 6.5, made);
        Mob foe = spawn(level, EntityRegistrar.ZOMBIE_UNIT.get(), theirs, p.getX() + 5.5, p.getY(), p.getZ() + 6.5, made);
        if (levy == null || foe == null) {
            cleanUp(made);
            helper.fail("could not create the levy and the zombie");
            return;
        }
        foe.setNoAi(true);
        foe.setTarget(levy);
        speedTo(foe, ((Unit) levy).getBaseMovementSpeed() * 1.3);
        EngagementStance.set(foe, EngagementStance.HOLD_FIRE);
        levy.setTarget(foe);
        double startX = levy.getX();
        helper.runAfterDelay(40, () -> {
            try {
                String where = "levy moved " + f(levy.getX() - startX) + " along x (steps " + Kite.steps(levy) + ")";
                ReignOfNether.LOGGER.info("[Reaction] kite vs faster: {}", where);
                if (Kite.steps(levy) > 0)
                    problems.add("the levy kited a faster enemy: " + where);
                finish(helper, problems);
            } finally {
                cleanUp(made);
            }
        });
    }

    /** A wall one block behind the Levy: nowhere dry and walkable to step to, so it holds its ground. */
    @GameTest(template = ARENA, timeoutTicks = 120)
    public static void kite_holds_when_cornered(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos p = pad(helper, 268);
        List<Entity> made = new ArrayList<>();
        List<String> problems = new ArrayList<>();
        String mine = owner(helper, "kitec_a"), theirs = owner(helper, "kitec_b");
        // the wall: x = +10..+11 across the whole pad, 3 high, so every cell behind the levy is blocked
        for (int dx = 10; dx <= 11; dx++)
            for (int dz = 0; dz < PAD_D; dz++)
                for (int dy = 0; dy < 3; dy++)
                    level.setBlock(new BlockPos(p.getX() + dx, p.getY() + dy, p.getZ() + dz), Blocks.STONE.defaultBlockState(), 3);
        Mob levy = spawn(level, EntityRegistrar.PILLAGER_UNIT.get(), mine, p.getX() + 9.5, p.getY(), p.getZ() + 6.5, made);
        Mob foe = spawn(level, EntityRegistrar.ZOMBIE_UNIT.get(), theirs, p.getX() + 6.5, p.getY(), p.getZ() + 6.5, made);
        if (levy == null || foe == null) {
            cleanUp(made);
            helper.fail("could not create the levy and the zombie");
            return;
        }
        foe.setNoAi(true);
        foe.setTarget(levy);
        speedTo(foe, ((Unit) levy).getBaseMovementSpeed() * 0.9);
        EngagementStance.set(foe, EngagementStance.HOLD_FIRE);
        levy.setTarget(foe);
        double startX = levy.getX();
        helper.runAfterDelay(40, () -> {
            try {
                String where = "levy moved " + f(levy.getX() - startX) + " along x (steps " + Kite.steps(levy) + ")";
                ReignOfNether.LOGGER.info("[Reaction] kite cornered: {}", where);
                if (Kite.steps(levy) > 0 || Math.abs(levy.getX() - startX) > 1.0)
                    problems.add("the cornered levy stepped: " + where);
                finish(helper, problems);
            } finally {
                cleanUp(made);
            }
        });
    }

    /**
     * Two Zombies with the Paladin as their target: it braces within 10 ticks, moves at half speed, takes 0.75x of
     * an explicit hit, and after the 40 ticks takes the full hit again (the cooldown keeps it from re-bracing).
     */
    @GameTest(template = ARENA, timeoutTicks = 160)
    public static void brace_cuts_damage_while_braced_and_not_after(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos p = pad(helper, 270);
        List<Entity> made = new ArrayList<>();
        List<String> problems = new ArrayList<>();
        String mine = owner(helper, "brace_a"), theirs = owner(helper, "brace_b");
        Mob paladin = spawn(level, EntityRegistrar.IRON_GOLEM_UNIT.get(), mine, p.getX() + 10.5, p.getY(), p.getZ() + 6.5, made);
        Mob foeA = spawn(level, EntityRegistrar.ZOMBIE_UNIT.get(), theirs, p.getX() + 7.5, p.getY(), p.getZ() + 5.5, made);
        Mob foeB = spawn(level, EntityRegistrar.ZOMBIE_UNIT.get(), theirs, p.getX() + 7.5, p.getY(), p.getZ() + 7.5, made);
        if (paladin == null || foeA == null || foeB == null) {
            cleanUp(made);
            helper.fail("could not create the paladin and two zombies");
            return;
        }
        if (!Brace.has((Unit) paladin) || !AutocastState.isOn((Unit) paladin, BraceAbility.INSTANCE))
            problems.add("the Tabard Paladin has no Brace switch, or it is off by default");
        for (Mob foe : List.of(foeA, foeB)) {
            foe.setNoAi(true);
            foe.setTarget(paladin);
            EngagementStance.set(foe, EngagementStance.HOLD_FIRE);
        }
        paladin.setNoAi(true);   // stands still: the pad is small and the numbers are what matter
        paladin.setHealth(paladin.getMaxHealth());
        float[] loss = new float[2];
        double baseSpeed = ((Unit) paladin).getBaseMovementSpeed();
        helper.runAfterDelay(12, () -> {
            if (!Brace.isBraced(paladin)) {
                problems.add("the paladin did not brace with two zombies coming for it (braces " + Brace.braces(paladin) + ")");
                return;
            }
            double speed = paladin.getAttribute(Attributes.MOVEMENT_SPEED).getValue();
            if (speed > baseSpeed * 0.55)
                problems.add("braced speed " + f(speed) + " is not half of " + f(baseSpeed));
            float hp = paladin.getHealth();
            UnitDamage.hurtExplicit(paladin, level.damageSources().mobAttack(foeA), 10f);
            loss[0] = hp - paladin.getHealth();
            if (loss[0] <= 0)
                problems.add("the braced hit did not land");
        });
        helper.runAfterDelay(70, () -> {
            try {
                if (Brace.isBraced(paladin))
                    problems.add("still braced after " + Brace.DURATION_TICKS + " ticks (braces " + Brace.braces(paladin) + ")");
                double speed = paladin.getAttribute(Attributes.MOVEMENT_SPEED).getValue();
                if (Math.abs(speed - baseSpeed) > 1e-4)
                    problems.add("speed " + f(speed) + " not restored to " + f(baseSpeed) + " after the brace");
                float hp = paladin.getHealth();
                UnitDamage.hurtExplicit(paladin, level.damageSources().mobAttack(foeA), 10f);
                loss[1] = hp - paladin.getHealth();
                String where = "braced hit " + f(loss[0]) + ", unbraced hit " + f(loss[1]) + " (braces " + Brace.braces(paladin) + ")";
                ReignOfNether.LOGGER.info("[Reaction] brace: {}", where);
                if (loss[1] <= 0)
                    problems.add("the unbraced hit did not land: " + where);
                else if (Math.abs(loss[0] / loss[1] - Brace.DAMAGE_MULT) > 0.03)
                    problems.add("braced/unbraced damage ratio " + f(loss[0] / loss[1]) + ", expected " + Brace.DAMAGE_MULT + ": " + where);
                finish(helper, problems);
            } finally {
                cleanUp(made);
            }
        });
    }

    /** With the switch off, nothing happens: the same two zombies, no brace. */
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void brace_switch_off_means_no_brace(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos p = pad(helper, 273);
        List<Entity> made = new ArrayList<>();
        List<String> problems = new ArrayList<>();
        String mine = owner(helper, "braceoff_a"), theirs = owner(helper, "braceoff_b");
        Mob paladin = spawn(level, EntityRegistrar.IRON_GOLEM_UNIT.get(), mine, p.getX() + 10.5, p.getY(), p.getZ() + 6.5, made);
        Mob foeA = spawn(level, EntityRegistrar.ZOMBIE_UNIT.get(), theirs, p.getX() + 7.5, p.getY(), p.getZ() + 5.5, made);
        Mob foeB = spawn(level, EntityRegistrar.ZOMBIE_UNIT.get(), theirs, p.getX() + 7.5, p.getY(), p.getZ() + 7.5, made);
        if (paladin == null || foeA == null || foeB == null) {
            cleanUp(made);
            helper.fail("could not create the paladin and two zombies");
            return;
        }
        AutocastState.set((Unit) paladin, BraceAbility.INSTANCE, false);
        for (Mob foe : List.of(foeA, foeB)) {
            foe.setNoAi(true);
            foe.setTarget(paladin);
            EngagementStance.set(foe, EngagementStance.HOLD_FIRE);
        }
        paladin.setNoAi(true);
        helper.runAfterDelay(20, () -> {
            try {
                if (Brace.isBraced(paladin) || Brace.braces(paladin) > 0)
                    problems.add("the paladin braced with the switch off");
                finish(helper, problems);
            } finally {
                cleanUp(made);
            }
        });
    }
}
