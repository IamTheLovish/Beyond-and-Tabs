package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.resources.EconomyServerEvents;
import com.solegendary.reignofnether.resources.Resources;
import com.solegendary.reignofnether.resources.ResourcesServerEvents;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * The economy bar's two BAR sliders (Oct 21): the energy marker is the converter threshold, the metal marker the level
 * above which metal is shared with allies. Server side only (the HUD and the packet are thin): the stored levels, the
 * clamps and the rules, on pools of their own that no tick touches.
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class EconomySliderGameTests {

    static final String ARENA = SkirmishGameTests.ARENA;

    private static Resources pool(String name, float storage, float metal, float energy) {
        var p = new Resources(name, 0, 0, 0);
        p.addMetal(metal);
        p.addEnergy(energy);
        var eco = EconomyServerEvents.getEconomy(name);
        eco.metalStorage = storage;
        eco.energyStorage = storage;
        eco.converterLevel = -1f;
        eco.shareLevel = 1f;
        ResourcesServerEvents.resourcesList.add(p);
        return p;
    }

    @GameTest(template = ARENA, timeoutTicks = 20)
    public static void converter_slider_moves_the_threshold_and_is_clamped_and_validated(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        String owner = "gt_slider_conv";
        var p = pool(owner, 1000, 0, 900);   // 90% energy
        var eco = EconomyServerEvents.getEconomy(owner);
        try {
            eco.conversionCapacity = 50f;
            // the default threshold is the faction's (50% for an owner with no faction): 900 is above it, so it converts
            if (Math.abs(EconomyServerEvents.converterLevel(owner, eco) - EconomyServerEvents.CONVERSION_THRESHOLD) > 1e-4)
                problems.add("the default level is " + EconomyServerEvents.converterLevel(owner, eco));
            // slid up to 95%: 90% energy is under it, nothing converts
            if (EconomyServerEvents.setSlider(owner, true, 0.95f) != 0.95f)
                problems.add("0.95 was not stored");
            float metal = p.getMetal();
            EconomyServerEvents.convertEnergy(p, eco);
            if (p.getMetal() > metal)
                problems.add("converted at 90% with the threshold at 95%");
            // slid down to 20%: converts, and the spare energy it uses is above 20% only
            EconomyServerEvents.clearSliderLimit(owner);
            EconomyServerEvents.setSlider(owner, true, 0.2f);
            p.addEnergy(-p.getEnergy() + 300f);   // 30% energy
            EconomyServerEvents.convertEnergy(p, eco);
            if (p.getMetal() <= metal)
                problems.add("did not convert at 30% with the threshold at 20%");
            if (p.getEnergy() < 200f - 0.01f)
                problems.add("used energy below the threshold: " + p.getEnergy());
            // clamps and validation
            EconomyServerEvents.clearSliderLimit(owner);
            if (EconomyServerEvents.setSlider(owner, true, 7f) != 1f)
                problems.add("above 1 was not clamped to 1");
            EconomyServerEvents.clearSliderLimit(owner);
            if (EconomyServerEvents.setSlider(owner, true, -3f) != EconomyServerEvents.CONVERTER_MIN)
                problems.add("below 0 was not clamped to the minimum");
            EconomyServerEvents.clearSliderLimit(owner);
            if (EconomyServerEvents.setSlider(owner, true, Float.NaN) != -1f
                    || EconomyServerEvents.setSlider(owner, true, Float.POSITIVE_INFINITY) != -1f)
                problems.add("NaN or infinity was accepted");
            if (EconomyServerEvents.setSlider(owner, true, 0.5f) != -1f)
                problems.add("two changes in one tick were both accepted (rate limit)");
            if (EconomyServerEvents.setSlider("gt_slider_nobody", true, 0.5f) != -1f)
                problems.add("a player with no pool could set a slider");
        } finally {
            eco.conversionCapacity = 0;
            eco.converterLevel = -1f;
            ResourcesServerEvents.resourcesList.remove(p);
        }
        if (problems.isEmpty())
            helper.succeed();
        else
            helper.fail(String.join("; ", problems));
    }

    @GameTest(template = ARENA, timeoutTicks = 20)
    public static void share_slider_gives_metal_above_the_level_to_allies_and_never_back_and_forth(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        String me = "gt_share_me", ally = "gt_share_ally", sharer = "gt_share_sharer", foe = "gt_share_foe";
        var pm = pool(me, 1000, 800, 0);
        var pa = pool(ally, 1000, 100, 0);
        var ps = pool(sharer, 1000, 400, 0);   // an ally who also shares above 50%
        var pf = pool(foe, 1000, 0, 0);
        AlliancesServerEvents.addAlliance(me, ally);
        AlliancesServerEvents.addAlliance(me, sharer);
        var ecoMe = EconomyServerEvents.getEconomy(me);
        try {
            // default (100%): nothing is shared by the slider
            if (EconomyServerEvents.shareAboveLevel(me) != 0f)
                problems.add("shared with the slider at 100%");
            EconomyServerEvents.setSlider(me, false, 0.5f);
            EconomyServerEvents.getEconomy(sharer).shareLevel = 0.5f;
            float given = EconomyServerEvents.shareAboveLevel(me);
            float cap = EconomyServerEvents.SHARE_RATE_PER_SECOND / 20f;
            if (Math.abs(given - cap) > 0.01f)
                problems.add("gave " + given + " in a tick, expected the rate cap " + cap);
            if (Math.abs(pm.getMetal() - (800f - given)) > 0.01f)
                problems.add("the giver's pool is " + pm.getMetal());
            // the sharer (at 400, level 50% = 500) takes up to its level, the plain ally up to storage; they split evenly
            if (Math.abs((pa.getMetal() - 100f) - (ps.getMetal() - 400f)) > 0.01f || pa.getMetal() <= 100f)
                problems.add("allies did not split evenly: " + pa.getMetal() + " / " + ps.getMetal());
            if (pf.getMetal() > 0.001f)
                problems.add("the enemy received shared metal");
            // an ally already at its own level takes nothing from us
            ps.addMetal(600f - ps.getMetal());   // 600 > its 500 level
            float before = ps.getMetal();
            EconomyServerEvents.shareAboveLevel(me);
            if (ps.getMetal() > before + 0.001f)
                problems.add("an ally above its own share level still took metal (ping-pong)");
            // at or below the level: nothing leaves
            pm.addMetal(450f - pm.getMetal());
            float mine = pm.getMetal();
            if (EconomyServerEvents.shareAboveLevel(me) != 0f || pm.getMetal() != mine)
                problems.add("shared while under the level");
        } finally {
            ecoMe.shareLevel = 1f;
            EconomyServerEvents.getEconomy(sharer).shareLevel = 1f;
            AlliancesServerEvents.removeAlliance(me, ally);
            AlliancesServerEvents.removeAlliance(me, sharer);
            ResourcesServerEvents.resourcesList.removeAll(List.of(pm, pa, ps, pf));
        }
        if (problems.isEmpty())
            helper.succeed();
        else
            helper.fail(String.join("; ", problems));
    }
}
