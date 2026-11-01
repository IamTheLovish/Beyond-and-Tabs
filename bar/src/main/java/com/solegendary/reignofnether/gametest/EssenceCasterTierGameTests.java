package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.barfx.BarFx;
import com.solegendary.reignofnether.essence.EssenceCarrier;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.resources.ResourceCosts;
import com.solegendary.reignofnether.unit.UnitIconTier;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * The essence casters are tier 1, like rez bots (lovish, Oct 29), and priced as tier 1 too (lovish, Oct 30: 115 metal /
 * 120 energy, the same for every race). A caster's baked metal (ResourceCost.bakeValues) must stay below both tier-2
 * lines - the death blast's {@link BarFx#T2_METAL} and the strategic icon's {@link UnitIconTier#T2_METAL} - so it reads
 * as the tier it trains at. Each race's caster is listed in {@link #CASTERS}; a caster PR adds its own line.
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class EssenceCasterTierGameTests {

    static final String ARENA = SkirmishGameTests.ARENA;
    static final String BATCH = "caster_tier";
    /** lovish, Oct 30: every essence caster costs this much after ResourceCost.bakeValues. */
    static final int METAL = 115, ENERGY = 120;

    record Caster(String name, ResourceCost cost, Supplier<? extends EntityType<?>> type) { }

    static final List<Caster> CASTERS = List.of(
        new Caster("Soul Engineer", ResourceCosts.SOUL_ENGINEER, EntityRegistrar.SOUL_ENGINEER_UNIT),
        new Caster("Spark Sage", ResourceCosts.SPARK_SAGE, EntityRegistrar.SPARK_SAGE_UNIT),
        new Caster("Angel Caller", ResourceCosts.ANGEL_CALLER, EntityRegistrar.ANGEL_CALLER_UNIT));

    @GameTest(template = ARENA, timeoutTicks = 40, batch = BATCH)
    public static void essence_casters_cost_and_read_as_tier_1(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        for (Caster c : CASTERS) {
            int metal = c.cost().metal(), energy = c.cost().energy();
            if (metal != METAL || energy != ENERGY)
                problems.add(c.name() + " costs " + metal + " metal / " + energy + " energy, lovish set " + METAL + " / " + ENERGY);
            if (metal >= BarFx.T2_METAL)
                problems.add(c.name() + "'s " + metal + " metal is a T2 death blast (BarFx.T2_METAL " + BarFx.T2_METAL + ")");
            if (metal >= UnitIconTier.T2_METAL)
                problems.add(c.name() + "'s " + metal + " metal is a T2 icon (UnitIconTier.T2_METAL " + UnitIconTier.T2_METAL + ")");
            Entity e = c.type().get().create(helper.getLevel());
            try {
                if (!(e instanceof EssenceCarrier))
                    problems.add(c.name() + " is not an essence carrier: " + e);
                else if (e instanceof net.minecraft.world.entity.LivingEntity le && UnitIconTier.tierOf(le, false) != UnitIconTier.T1)
                    problems.add(c.name() + "'s icon is tier " + UnitIconTier.tierOf(le, false) + ", not T1 (isCheapT2?)");
            } finally {
                if (e != null)
                    e.discard();
            }
        }
        if (problems.isEmpty())
            helper.succeed();
        else
            helper.fail(String.join("; ", problems));
    }
}
