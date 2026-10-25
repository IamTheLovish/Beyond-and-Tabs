package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.ability.Ability;
import com.solegendary.reignofnether.ability.ExperimentalAbilityText;
import com.solegendary.reignofnether.ability.abilities.AttackGround;
import com.solegendary.reignofnether.ability.abilities.DeepDive;
import com.solegendary.reignofnether.ability.abilities.GrowGrove;
import com.solegendary.reignofnether.ability.abilities.Rootquake;
import com.solegendary.reignofnether.ability.abilities.SolarLance;
import com.solegendary.reignofnether.ability.abilities.Tempest;
import com.solegendary.reignofnether.ability.abilities.TidalSlam;
import com.solegendary.reignofnether.ability.abilities.Trample;
import com.solegendary.reignofnether.ability.abilities.WitheringFog;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.units.brass.IronLeviathanEngineUnit;
import com.solegendary.reignofnether.unit.units.tide.LeviathanUnit;
import com.solegendary.reignofnether.unit.units.verdant.WorldTreeWalkerUnit;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * Every experimental (T3) unit's own ability has a description and shows its reach in blocks, read from the ability's
 * own range / radius value (never a typed copy). The text methods need no client, so this runs headless.
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class ExperimentalAbilityTooltipGameTests {

    static final String ARENA = SkirmishGameTests.ARENA;

    /** One check: the unit's ability of this class has a description and the expected range line. */
    private static void check(List<String> problems, String unitName, Unit unit, Class<? extends Ability> type, String expectedRangeLine) {
        Ability found = null;
        for (Ability a : unit.getAbilities().get())
            if (type.isInstance(a))
                found = a;
        if (found == null) {
            problems.add(unitName + " has no " + type.getSimpleName());
            return;
        }
        String desc, line;
        if (found instanceof WitheringFog f) { desc = f.description(); line = f.rangeLine(); }
        else if (found instanceof SolarLance f) { desc = f.description(); line = f.rangeLine(); }
        else if (found instanceof Tempest f) { desc = f.description(); line = f.rangeLine(); }
        else if (found instanceof TidalSlam f) { desc = f.description(); line = f.rangeLine(); }
        else if (found instanceof DeepDive f) { desc = f.description(); line = f.rangeLine(); }
        else if (found instanceof GrowGrove f) { desc = f.description(); line = f.rangeLine(); }
        else if (found instanceof Rootquake f) { desc = f.description(); line = f.rangeLine(); }
        else if (found instanceof Trample f) { desc = f.description(); line = f.rangeLine(); }
        else if (found instanceof AttackGround f) { desc = f.description(); line = f.rangeLine(); }
        else { problems.add(unitName + ": " + type.getSimpleName() + " has no tooltip text"); return; }
        if (desc == null || desc.isBlank())
            problems.add(unitName + " " + type.getSimpleName() + " has an empty description");
        if (!expectedRangeLine.equals(line))
            problems.add(unitName + " " + type.getSimpleName() + " shows \"" + line + "\" but its value says \"" + expectedRangeLine + "\"");
    }

    private static <T extends Entity> Unit make(GameTestHelper helper, EntityType<T> type, List<String> problems) {
        Entity e = type.create(helper.getLevel());
        if (e instanceof Unit u)
            return u;
        problems.add("could not create " + type);
        return null;
    }

    @GameTest(template = ARENA, timeoutTicks = 40)
    public static void experimental_abilities_describe_themselves_and_show_their_range(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        List<Entity> made = new ArrayList<>();

        Unit dragon = make(helper, EntityRegistrar.BONE_DRAGON_UNIT.get(), problems);
        Unit colossus = make(helper, EntityRegistrar.SUN_COLOSSUS_UNIT.get(), problems);
        Unit celestial = make(helper, EntityRegistrar.CELESTIAL_DRAGON_UNIT.get(), problems);
        Unit leviathan = make(helper, EntityRegistrar.LEVIATHAN_UNIT.get(), problems);
        Unit walker = make(helper, EntityRegistrar.WORLD_TREE_WALKER_UNIT.get(), problems);
        Unit mammoth = make(helper, EntityRegistrar.WAR_MAMMOTH_UNIT.get(), problems);
        Unit engine = make(helper, EntityRegistrar.IRON_LEVIATHAN_ENGINE_UNIT.get(), problems);
        for (Unit u : new Unit[] { dragon, colossus, celestial, leviathan, walker, mammoth, engine })
            if (u instanceof Entity e)
                made.add(e);
        try {
            if (dragon != null)
                check(problems, "Bone Dragon", dragon, WitheringFog.class, ExperimentalAbilityText.rangeLine(WitheringFog.RANGE));
            if (colossus != null)
                check(problems, "Sun Colossus", colossus, SolarLance.class, ExperimentalAbilityText.rangeLine(SolarLance.LENGTH));
            if (celestial != null)
                check(problems, "Celestial Dragon", celestial, Tempest.class, ExperimentalAbilityText.rangeLine(Tempest.RANGE));
            if (leviathan != null) {
                check(problems, "Leviathan", leviathan, TidalSlam.class, ExperimentalAbilityText.selfLine(TidalSlam.RADIUS));
                check(problems, "Leviathan", leviathan, DeepDive.class, ExperimentalAbilityText.rangeLine(LeviathanUnit.DIVE_RANGE));
            }
            if (walker != null) {
                check(problems, "World Tree Walker", walker, GrowGrove.class, ExperimentalAbilityText.selfLine(WorldTreeWalkerUnit.GROVE_RADIUS));
                check(problems, "World Tree Walker", walker, Rootquake.class, ExperimentalAbilityText.selfLine(Rootquake.RADIUS));
            }
            if (mammoth != null)
                check(problems, "War Mammoth", mammoth, Trample.class, ExperimentalAbilityText.rangeLine((float) Trample.LENGTH));
            if (engine != null)
                check(problems, "Iron Leviathan Engine", engine, AttackGround.class, ExperimentalAbilityText.rangeLine(IronLeviathanEngineUnit.attackRange));
        } finally {
            for (Entity e : made)
                e.discard();
        }
        if (problems.isEmpty())
            helper.succeed();
        else
            helper.fail(String.join("; ", problems));
    }
}
