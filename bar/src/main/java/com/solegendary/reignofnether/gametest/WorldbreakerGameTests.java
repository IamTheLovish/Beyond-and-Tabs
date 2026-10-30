package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.ability.abilities.Earthsplitter;
import com.solegendary.reignofnether.ability.abilities.Rampage;
import com.solegendary.reignofnether.ability.abilities.WorldbreakerHurl;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.unit.interfaces.AttackerUnit;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.units.piglins.WarMammothUnit;
import com.solegendary.reignofnether.unit.units.piglins.WorldbreakerUnit;
import com.solegendary.reignofnether.unit.units.tide.BombardShell;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * Slice H5a (hero-and-tier4-design.md): the Horde's <b>Worldbreaker</b>. One test per acceptance criterion, all un-hosted
 * and inside the test's own arena: Earthsplitter telegraphs then hits the line only (AC-4), Hurl splashes and leaves
 * burning ground for 5 s (AC-5), Rampage throws units aside and ends at 3 s without a stun (AC-6), nothing of it targets
 * or hits a flyer (AC-7), and an un-hosted Worldbreaker loses or is even against equal metal of the Horde's first T3
 * (AC-8). The frame (cap, research, announcements) is T4FrameGameTests.
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class WorldbreakerGameTests {

    static final String ARENA = SkirmishGameTests.ARENA;

    static String owner(GameTestHelper helper, String tag) {
        return "gametest_wb_" + tag + "_" + helper.absolutePos(BlockPos.ZERO).asLong();
    }

    static void discard(LivingEntity... all) {
        for (LivingEntity e : all)
            if (e != null)
                e.discard();
    }

    /** AC-4: Earthsplitter telegraphs for at least 0.5 s with no damage, then hits enemies on the line, not off it; the slow is 1 s. */
    @GameTest(template = ARENA, timeoutTicks = 120)
    public static void earthsplitter_telegraphs_then_hits_the_line_only(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = owner(helper, "split"), foe = owner(helper, "splitfoe");
        var wb = MagmaHurlerGameTests.put(helper, EntityRegistrar.WORLDBREAKER_UNIT.get(), owner, 2.5, 8.5);
        var on1 = MagmaHurlerGameTests.tough(MagmaHurlerGameTests.put(helper, EntityRegistrar.VINDICATOR_UNIT.get(), foe, 6.5, 8.5));
        var on2 = MagmaHurlerGameTests.tough(MagmaHurlerGameTests.put(helper, EntityRegistrar.VINDICATOR_UNIT.get(), foe, 10.5, 9.0));
        var off = MagmaHurlerGameTests.tough(MagmaHurlerGameTests.put(helper, EntityRegistrar.VINDICATOR_UNIT.get(), foe, 6.5, 13.5));
        var beyond = MagmaHurlerGameTests.tough(MagmaHurlerGameTests.put(helper, EntityRegistrar.VINDICATOR_UNIT.get(), foe, 16.5, 8.5));
        var friend = MagmaHurlerGameTests.tough(MagmaHurlerGameTests.put(helper, EntityRegistrar.VINDICATOR_UNIT.get(), owner, 8.5, 8.5));
        if (wb == null || on1 == null || on2 == null || off == null || beyond == null || friend == null) {
            helper.fail("could not create the earthsplitter units");
            return;
        }
        for (Mob m : List.of(on1, on2, off, beyond, friend))
            m.setNoAi(true);
        Earthsplitter split = (Earthsplitter) wb.getAbilities().get().stream().filter(a -> a instanceof Earthsplitter).findFirst().orElse(null);
        if (split == null) {
            helper.fail("the Worldbreaker has no Earthsplitter");
            return;
        }
        List<String> problems = new ArrayList<>();
        if (Earthsplitter.TELEGRAPH_TICKS < 10 || Earthsplitter.TELEGRAPH_TICKS > 30)
            problems.add("the telegraph should be at least 0.5 s and readable: " + Earthsplitter.TELEGRAPH_TICKS + " ticks");
        if (Earthsplitter.SLOW_TICKS > 30)
            problems.add("the slow is longer than 1.5 s: " + Earthsplitter.SLOW_TICKS + " ticks");
        helper.runAfterDelay(4, () -> {
            float[] hp = { on1.getHealth(), on2.getHealth(), off.getHealth(), beyond.getHealth(), friend.getHealth() };
            split.use(level, wb, helper.absolutePos(new BlockPos(14, 2, 8)));
            if (split.isOffCooldown(wb))
                problems.add("Earthsplitter did not go on cooldown");
            helper.runAfterDelay(Earthsplitter.TELEGRAPH_TICKS - 2, () -> {
                if (on1.getHealth() != hp[0] || on2.getHealth() != hp[1])
                    problems.add("the enemy was hurt during the telegraph");
            });
            helper.runAfterDelay(Earthsplitter.TELEGRAPH_TICKS + 2, () -> {
                for (int i = 0; i < 2; i++) {
                    LivingEntity e = i == 0 ? on1 : on2;
                    float lost = hp[i] - e.getHealth();
                    if (Math.abs(lost - Earthsplitter.DAMAGE) > 1.5f)
                        problems.add("enemy " + i + " on the line took " + lost + ", expected " + Earthsplitter.DAMAGE);
                    MobEffectInstance slow = e.getEffect(MobEffects.MOVEMENT_SLOWDOWN);
                    if (slow == null)
                        problems.add("enemy " + i + " on the line was not slowed");
                    else if (slow.getDuration() > 30)
                        problems.add("the slow lasts " + slow.getDuration() + " ticks, over 1.5 s");
                }
                if (off.getHealth() != hp[2] || off.hasEffect(MobEffects.MOVEMENT_SLOWDOWN))
                    problems.add("an enemy off the line was hurt or slowed");
                if (beyond.getHealth() != hp[3])
                    problems.add("an enemy beyond the line's end was hurt");
                if (friend.getHealth() != hp[4])
                    problems.add("a friendly unit on the line was hurt");
                discard(wb, on1, on2, off, beyond, friend);
                T3RoleGameTests.finish(helper, problems);
            });
        });
    }

    /** AC-4 (autocast): the un-hosted rule casts at an enemy only when 3 or more stand on the line. */
    @GameTest(template = ARENA, timeoutTicks = 60)
    public static void earthsplitter_autocast_needs_three_on_the_line(GameTestHelper helper) {
        String owner = owner(helper, "splitauto"), foe = owner(helper, "splitautofoe");
        var wb = MagmaHurlerGameTests.put(helper, EntityRegistrar.WORLDBREAKER_UNIT.get(), owner, 2.5, 8.5);
        var a = MagmaHurlerGameTests.put(helper, EntityRegistrar.VINDICATOR_UNIT.get(), foe, 6.5, 8.5);
        var b = MagmaHurlerGameTests.put(helper, EntityRegistrar.VINDICATOR_UNIT.get(), foe, 8.5, 8.5);
        var c = MagmaHurlerGameTests.put(helper, EntityRegistrar.VINDICATOR_UNIT.get(), foe, 8.5, 14.5);
        if (wb == null || a == null || b == null || c == null) {
            helper.fail("could not create the units");
            return;
        }
        for (Mob m : List.of(a, b, c))
            m.setNoAi(true);
        helper.runAfterDelay(4, () -> {
            List<String> problems = new ArrayList<>();
            Earthsplitter split = (Earthsplitter) wb.getAbilities().get().stream().filter(x -> x instanceof Earthsplitter).findFirst().orElse(null);
            if (split.autocastTargetOk(wb, a))
                problems.add("it would cast with only two enemies on the line");
            var third = MagmaHurlerGameTests.put(helper, EntityRegistrar.VINDICATOR_UNIT.get(), foe, 4.5, 9.0);
            third.setNoAi(true);
            helper.runAfterDelay(2, () -> {
                if (!split.autocastTargetOk(wb, a))
                    problems.add("it would not cast with three enemies on the line");
                discard(wb, a, b, c, third);
                T3RoleGameTests.finish(helper, problems);
            });
        });
    }

    /** AC-5: Hurl's boulder bursts for its damage in 3 blocks, burns what it hits, spares the far and friends; the ground burns for 5 s. */
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void hurl_splashes_in_three_blocks(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = owner(helper, "hurl"), foe = owner(helper, "hurlfoe");
        var wb = MagmaHurlerGameTests.put(helper, EntityRegistrar.WORLDBREAKER_UNIT.get(), owner, 2.5, 8.5);
        var centre = MagmaHurlerGameTests.tough(MagmaHurlerGameTests.put(helper, EntityRegistrar.VINDICATOR_UNIT.get(), foe, 14.5, 8.5));
        var edge = MagmaHurlerGameTests.tough(MagmaHurlerGameTests.put(helper, EntityRegistrar.VINDICATOR_UNIT.get(), foe, 16.5, 8.5));
        var far = MagmaHurlerGameTests.tough(MagmaHurlerGameTests.put(helper, EntityRegistrar.VINDICATOR_UNIT.get(), foe, 19.5, 8.5));
        var friend = MagmaHurlerGameTests.tough(MagmaHurlerGameTests.put(helper, EntityRegistrar.VINDICATOR_UNIT.get(), owner, 14.5, 10.0));
        if (wb == null || centre == null || edge == null || far == null || friend == null) {
            helper.fail("could not create the hurl units");
            return;
        }
        for (Mob m : List.of(centre, edge, far, friend))
            m.setNoAi(true);
        helper.runAfterDelay(4, () -> {
            List<String> problems = new ArrayList<>();
            BombardShell shell = WorldbreakerHurl.lob(level, wb, centre.position());
            if (shell.getSplashRadius() != WorldbreakerHurl.SPLASH_RADIUS || WorldbreakerHurl.SPLASH_RADIUS != 3.0)
                problems.add("the splash is " + shell.getSplashRadius() + " blocks, expected 3");
            if (shell.getDamage() != WorldbreakerHurl.DAMAGE || !shell.isGroundOnly())
                problems.add("the shell carries " + shell.getDamage() + " damage or is not ground-only");
            shell.setPos(centre.getX(), centre.getY(), centre.getZ());   // land it (the flight is BombardShell's, tested elsewhere)
            float cHp = centre.getHealth(), eHp = edge.getHealth(), fHp = far.getHealth(), frHp = friend.getHealth();
            int hit = shell.burst();
            if (hit != 2)
                problems.add("the burst hurt " + hit + " units, expected the centre and the edge (2)");
            float cLoss = cHp - centre.getHealth(), eLoss = eHp - edge.getHealth();
            if (Math.abs(cLoss - WorldbreakerHurl.DAMAGE) > 1.5f)
                problems.add("the centre took " + cLoss + ", expected about " + WorldbreakerHurl.DAMAGE);
            if (eLoss <= 0 || eLoss >= cLoss)
                problems.add("the edge should take less than the centre: " + eLoss + " vs " + cLoss);
            if (centre.getRemainingFireTicks() <= 0 || edge.getRemainingFireTicks() <= 0)
                problems.add("enemies in the splash were not set alight");
            if (far.getHealth() != fHp || far.getRemainingFireTicks() > 0)
                problems.add("an enemy outside the splash was hurt or burned");
            if (friend.getHealth() != frHp || friend.getRemainingFireTicks() > 0)
                problems.add("the splash hurt or burned a friendly unit");
            discard(wb, centre, edge, far, friend);
            T3RoleGameTests.finish(helper, problems);
        });
    }

    /** AC-5: the burning ground re-ignites enemies standing in it for 5 s and not after. */
    @GameTest(template = ARENA, timeoutTicks = 160)
    public static void hurl_leaves_burning_ground_for_five_seconds(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = owner(helper, "burn"), foe = owner(helper, "burnfoe");
        var victim = MagmaHurlerGameTests.tough(MagmaHurlerGameTests.put(helper, EntityRegistrar.VINDICATOR_UNIT.get(), foe, 12.5, 8.5));
        if (victim == null) {
            helper.fail("could not create the victim");
            return;
        }
        victim.setNoAi(true);
        if (WorldbreakerHurl.BURN_GROUND_TICKS != 100)
            helper.fail("the burning ground should last 100 ticks (5 s): " + WorldbreakerHurl.BURN_GROUND_TICKS);
        List<String> problems = new ArrayList<>();
        helper.runAfterDelay(2, () -> {
            WorldbreakerHurl.scorch(level, owner, victim.getX(), victim.getY(), victim.getZ());
            helper.runAfterDelay(15, () -> {
                if (victim.getRemainingFireTicks() <= 0)
                    problems.add("the ground did not light an enemy standing in it");
            });
            helper.runAfterDelay(94, () -> victim.clearFire());   // put it out just before the last pulse (tick 100)
            helper.runAfterDelay(106, () -> {
                if (victim.getRemainingFireTicks() <= 0)
                    problems.add("the ground stopped burning before 5 s");
                victim.clearFire();
            });
            helper.runAfterDelay(130, () -> {
                if (victim.getRemainingFireTicks() > 0)
                    problems.add("the ground still burned after 5 s");
                discard(victim);
                T3RoleGameTests.finish(helper, problems);
            });
        });
    }

    /** AC-6: Rampage throws the units in its lane aside, spares those off it, applies no stun, and ends within 3 s. */
    @GameTest(template = ARENA, timeoutTicks = 140)
    public static void rampage_displaces_units_and_ends_at_three_seconds(GameTestHelper helper) {
        SkirmishGameTests.openGround(helper, 1, 2, 14, 14);   // clear the arena's fixtures out of the lane
        String owner = owner(helper, "ramp"), foe = owner(helper, "rampfoe");
        var wb = MagmaHurlerGameTests.put(helper, EntityRegistrar.WORLDBREAKER_UNIT.get(), owner, 2.5, 8.5);
        var in1 = MagmaHurlerGameTests.tough(MagmaHurlerGameTests.put(helper, EntityRegistrar.VINDICATOR_UNIT.get(), foe, 7.5, 8.5));
        var in2 = MagmaHurlerGameTests.tough(MagmaHurlerGameTests.put(helper, EntityRegistrar.VINDICATOR_UNIT.get(), foe, 11.5, 9.2));
        var off = MagmaHurlerGameTests.tough(MagmaHurlerGameTests.put(helper, EntityRegistrar.VINDICATOR_UNIT.get(), foe, 9.5, 15.0));
        if (wb == null || in1 == null || in2 == null || off == null) {
            helper.fail("could not create the rampage units");
            return;
        }
        for (Mob m : List.of(in1, in2, off))
            if (m instanceof Unit u)
                u.setHoldPosition(true);   // not NoAi: a frozen mob ignores the throw
        wb.setHoldPosition(true);
        List<String> problems = new ArrayList<>();
        if (WorldbreakerUnit.RAMPAGE_TICKS != 60)
            problems.add("the charge should last 60 ticks (3 s): " + WorldbreakerUnit.RAMPAGE_TICKS);
        helper.runAfterDelay(4, () -> {
            Rampage rampage = (Rampage) wb.getAbilities().get().stream().filter(a -> a instanceof Rampage).findFirst().orElse(null);
            Vec3[] before = { in1.position(), in2.position(), off.position() };
            float[] hp = { in1.getHealth(), in2.getHealth(), off.getHealth() };
            rampage.use(wb.level(), wb, helper.absolutePos(new BlockPos(13, 2, 8)));
            if (!wb.isRampaging())
                problems.add("Rampage did not start a charge");
            if (rampage.isOffCooldown(wb))
                problems.add("Rampage did not go on cooldown");
            helper.runAfterDelay(WorldbreakerUnit.RAMPAGE_TICKS + 3, () -> {
                if (wb.isRampaging())
                    problems.add("the charge was still running after 3 s");
                if (wb.lastRampageTicks <= 0 || wb.lastRampageTicks > WorldbreakerUnit.RAMPAGE_TICKS)
                    problems.add("the charge ran " + wb.lastRampageTicks + " ticks, expected 1 to 60");
                if (wb.lastRampageHits < 2)
                    problems.add("(ran " + wb.lastRampageTicks + " ticks, at x " + wb.getX() + ") the charge hit " + wb.lastRampageHits + " units, expected the two in its lane");
                LivingEntity[] hit = { in1, in2 };
                for (int i = 0; i < 2; i++) {
                    if (hit[i].getHealth() >= hp[i])
                        problems.add("lane unit " + i + " took no damage");
                    if (hit[i].position().distanceTo(before[i]) < 0.5)
                        problems.add("lane unit " + i + " was not displaced");
                    for (MobEffectInstance eff : hit[i].getActiveEffects())
                        if (eff.getDuration() > 30)
                            problems.add("lane unit " + i + " carries " + eff.getEffect().getDescriptionId() + " for " + eff.getDuration() + " ticks");
                    if (hit[i].hasEffect(com.solegendary.reignofnether.registrars.MobEffectRegistrar.STUN.get()))
                        problems.add("lane unit " + i + " was stunned");
                }
                if (off.getHealth() != hp[2])
                    problems.add("a unit off the lane was hurt");
                discard(wb, in1, in2, off);
                T3RoleGameTests.finish(helper, problems);
            });
        });
    }

    /** AC-7: it cannot target or hit a flyer, and none of its three abilities touches one. */
    @GameTest(template = ARENA, timeoutTicks = 80)
    public static void worldbreaker_cannot_target_or_hit_a_flyer(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = owner(helper, "air"), foe = owner(helper, "airfoe");
        var wb = MagmaHurlerGameTests.put(helper, EntityRegistrar.WORLDBREAKER_UNIT.get(), owner, 2.5, 8.5);
        var roc = MagmaHurlerGameTests.tough(MagmaHurlerGameTests.put(helper, EntityRegistrar.THORNWING_ROC_UNIT.get(), foe, 5.5, 8.5));
        var walker = MagmaHurlerGameTests.tough(MagmaHurlerGameTests.put(helper, EntityRegistrar.VINDICATOR_UNIT.get(), foe, 6.5, 8.5));
        if (wb == null || roc == null || walker == null) {
            helper.fail("could not create the flyer units");
            return;
        }
        roc.setNoAi(true);
        walker.setNoAi(true);
        helper.runAfterDelay(4, () -> {
            List<String> problems = new ArrayList<>();
            if (!((Unit) roc).isFlyingUnit()) {
                helper.fail("the test flyer is not a flying unit");
                return;
            }
            float rocHp = roc.getHealth();
            if (wb.doHurtTarget(roc) || roc.getHealth() != rocHp)
                problems.add("a blow landed on a flyer");
            wb.setUnitAttackTarget(roc);
            if (wb.getTarget() == roc)
                problems.add("a click on a flyer set it as a target");
            wb.getTargetGoal().setTarget(roc);
            // a forced flyer target is dropped by the next tick
            helper.runAfterDelay(3, () -> {
                if (wb.getTarget() == roc)
                    problems.add("a flyer stayed the target");
                if (!WorldbreakerUnit.groundFoes(level, owner, wb.getX(), wb.getZ(), 30).stream().noneMatch(e -> e == roc))
                    problems.add("a flyer is in the ground-foe list");
                Vec3 from = wb.position();
                Vec3 dir = new Vec3(1, 0, 0);
                float wHp = walker.getHealth();
                int hit = Earthsplitter.crack(level, wb, owner, from, dir);
                if (roc.getHealth() != rocHp)
                    problems.add("Earthsplitter hurt a flyer");
                if (hit != 1 || walker.getHealth() >= wHp)
                    problems.add("Earthsplitter (control) hit " + hit + " units, expected the walker only");
                BombardShell shell = WorldbreakerHurl.lob(level, wb, roc.position());
                shell.setPos(roc.getX(), roc.getY(), roc.getZ());
                shell.burst();
                if (roc.getHealth() != rocHp || roc.getRemainingFireTicks() > 0)
                    problems.add("Hurl hurt or burned a flyer");
                Earthsplitter split = (Earthsplitter) wb.getAbilities().get().stream().filter(a -> a instanceof Earthsplitter).findFirst().orElse(null);
                Rampage ramp = (Rampage) wb.getAbilities().get().stream().filter(a -> a instanceof Rampage).findFirst().orElse(null);
                WorldbreakerHurl hurl = (WorldbreakerHurl) wb.getAbilities().get().stream().filter(a -> a instanceof WorldbreakerHurl).findFirst().orElse(null);
                if (split.autocastTargetOk(wb, roc) || ramp.autocastTargetOk(wb, roc) || hurl.autocastTargetOk(wb, roc))
                    problems.add("an autocast rule accepted a flyer as its target");
                discard(wb, roc, walker);
                T3RoleGameTests.finish(helper, problems);
            });
        });
    }

    /**
     * AC-8: un-hosted (no abilities fire by hand; the AutoCaster's rules need 3+ enemies, so two Mammoths trigger none),
     * a Worldbreaker fights two War Mammoths (equal metal: 3500 vs 2 x 1840) for 50 s and does not come out ahead: its
     * remaining value fraction is at or under the Mammoths' (a dead Worldbreaker passes).
     */
    @GameTest(template = ARENA, timeoutTicks = 1200)
    public static void worldbreaker_loses_or_is_even_against_equal_metal_of_mammoths(GameTestHelper helper) {
        String owner = owner(helper, "duel"), foe = owner(helper, "duelfoe");
        var wb = MagmaHurlerGameTests.put(helper, EntityRegistrar.WORLDBREAKER_UNIT.get(), owner, 3.5, 8.5);
        var m1 = MagmaHurlerGameTests.put(helper, EntityRegistrar.WAR_MAMMOTH_UNIT.get(), foe, 13.5, 6.5);
        var m2 = MagmaHurlerGameTests.put(helper, EntityRegistrar.WAR_MAMMOTH_UNIT.get(), foe, 13.5, 10.5);
        if (wb == null || m1 == null || m2 == null) {
            helper.fail("could not create the duel units");
            return;
        }
        helper.runAfterDelay(4, () -> {
            wb.setUnitAttackTarget(m1);
            ((AttackerUnit) m1).setUnitAttackTarget(wb);
            ((AttackerUnit) m2).setUnitAttackTarget(wb);
        });
        helper.runAfterDelay(1000, () -> {
            double wbCost = ((Unit) wb).getCost().metal(), mCost = ((Unit) m1).getCost().metal();
            double wbFrac = wb.isAlive() ? wb.getHealth() / wb.getMaxHealth() : 0;
            double mFrac = ((m1.isAlive() ? m1.getHealth() / m1.getMaxHealth() : 0) + (m2.isAlive() ? m2.getHealth() / m2.getMaxHealth() : 0)) / 2;
            String line = "Worldbreaker " + wbFrac + " of " + wbCost + "M vs 2 Mammoths " + mFrac + " of " + mCost + "M each";
            ReignOfNether.LOGGER.info("[WorldbreakerDuel] " + line);
            boolean ok = wbFrac <= mFrac + 0.05;
            discard(wb, m1, m2);
            if (ok)
                helper.succeed();
            else
                helper.fail("the Worldbreaker is ahead of equal metal of Mammoths: " + line);
        });
    }

    /** The numbers: HP x1.8 and damage x1.4 of the War Mammoth, a distinct HP total, ground and melee, no mass push, the config cost. */
    @GameTest(template = ARENA, timeoutTicks = 40)
    public static void worldbreaker_numbers_come_from_the_mammoth(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        if (Math.abs(WorldbreakerUnit.maxHealth - WarMammothUnit.maxHealth * 1.8f) > 1f)
            problems.add("HP " + WorldbreakerUnit.maxHealth + " is not 1.8x the Mammoth's " + WarMammothUnit.maxHealth);
        if (Math.abs(WorldbreakerUnit.attackDamage - WarMammothUnit.attackDamage * 1.4f) > 0.5f)
            problems.add("damage " + WorldbreakerUnit.attackDamage + " is not 1.4x the Mammoth's " + WarMammothUnit.attackDamage);
        WorldbreakerUnit w = EntityRegistrar.WORLDBREAKER_UNIT.get().create(helper.getLevel());
        if (w == null)
            problems.add("could not create the Worldbreaker");
        else {
            if (w.isPushable())
                problems.add("a titan must not be pushed by other bodies");
            if (((Unit) w).getCost() != com.solegendary.reignofnether.resources.ResourceCosts.WORLDBREAKER)
                problems.add("the cost entry is not WORLDBREAKER");
            if (w.getAbilities().get().size() != 3)
                problems.add("expected Earthsplitter, Hurl and Rampage, found " + w.getAbilities().get().size() + " abilities");
            if (w.isFlyingUnit())
                problems.add("the Worldbreaker flies");
            w.discard();
        }
        T3RoleGameTests.finish(helper, problems);
    }
}
