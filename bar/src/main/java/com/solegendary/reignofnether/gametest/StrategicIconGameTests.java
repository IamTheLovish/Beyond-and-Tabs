package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.unit.UnitIconTier;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * The strategic view's tier-sized unit icons (unit/UnitIconTier, drawn by orthoview/StrategicViewClientEvents):
 * the pure size table, and that the roster's tiers come out of the cost bands plus the cheap-T2 list.
 * Cheap: no world changes, a handful of entities created and discarded.
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class StrategicIconGameTests {
    static final String ARENA = SkirmishGameTests.ARENA;

    @GameTest(template = ARENA, timeoutTicks = 20)
    public static void icon_size_grows_with_tier(GameTestHelper helper) {
        float w = UnitIconTier.iconSize(UnitIconTier.WORKER), t1 = UnitIconTier.iconSize(UnitIconTier.T1),
              t2 = UnitIconTier.iconSize(UnitIconTier.T2), t3 = UnitIconTier.iconSize(UnitIconTier.T3),
              hero = UnitIconTier.iconSize(UnitIconTier.HERO);
        if (w != 0.9f || t1 != 1f || t2 != 1.25f || t3 != 1.6f || hero != 1.6f)
            helper.fail("icon sizes should be worker 0.9, T1 1, T2 1.25, T3 1.6, hero 1.6; got "
                    + w + ", " + t1 + ", " + t2 + ", " + t3 + ", " + hero);
        else if (UnitIconTier.iconSize(99) != 1f)
            helper.fail("an unknown tier should fall back to T1 size");
        else if (UnitIconTier.tierForMetal(170) != UnitIconTier.T1 || UnitIconTier.tierForMetal(180) != UnitIconTier.T2
                || UnitIconTier.tierForMetal(780) != UnitIconTier.T2 || UnitIconTier.tierForMetal(1840) != UnitIconTier.T3)
            helper.fail("cost bands: 170 metal is T1 (Sentinel Treant), 180-780 T2 (heavies, beasts), 1840 T3");
        else
            helper.succeed();
    }

    @GameTest(template = ARENA, timeoutTicks = 20)
    public static void unit_tiers_follow_the_roster(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        List<LivingEntity> made = new ArrayList<>();
        try {
            check(helper, made, problems, EntityRegistrar.VILLAGER_UNIT.get(), UnitIconTier.WORKER, false, "villager (worker)");
            check(helper, made, problems, EntityRegistrar.ZOMBIE_UNIT.get(), UnitIconTier.T1, false, "zombie (T1)");
            check(helper, made, problems, EntityRegistrar.CREEPER_UNIT.get(), UnitIconTier.T2, false, "creeper (T2 priced like T1)");
            check(helper, made, problems, EntityRegistrar.RAVAGER_UNIT.get(), UnitIconTier.T2, true, "ravager (T2 heavy, 1.5+ wide)");
            check(helper, made, problems, EntityRegistrar.ORIFLAMME_ELEPHANT_UNIT.get(), UnitIconTier.T2, true, "elephant (T2 beast)");
            check(helper, made, problems, EntityRegistrar.SUN_COLOSSUS_UNIT.get(), UnitIconTier.T3, true, "colossus (T3)");
        } finally {
            for (LivingEntity e : made)
                e.discard();
        }
        if (problems.isEmpty())
            helper.succeed();
        else
            helper.fail(String.join("; ", problems));
    }

    private static void check(GameTestHelper helper, List<LivingEntity> made, List<String> problems,
                              EntityType<? extends LivingEntity> type, int tier, boolean big, String what) {
        LivingEntity e = type.create(helper.getLevel());
        if (e == null) {
            problems.add(what + ": could not be created");
            return;
        }
        made.add(e);
        int got = UnitIconTier.tierOf(e);
        if (got != tier)
            problems.add(what + ": tier " + got + ", expected " + tier);
        if (UnitIconTier.isBig(e) != big)
            problems.add(what + ": isBig " + UnitIconTier.isBig(e) + " (width " + e.getBbWidth() + "), expected " + big);
    }
}
