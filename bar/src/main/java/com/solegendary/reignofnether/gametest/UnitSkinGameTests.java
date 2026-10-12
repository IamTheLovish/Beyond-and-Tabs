package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * The unit skins of design/unit_skins.md (tools/gen_hd_skins.py). The renderers that name them are client-only, so
 * this server test checks the files ship in the mod: a typo would otherwise only show in game as the purple-and-black
 * missing texture. Covers the skins whose paths are new on the unit-skins branch (units that wore vanilla textures
 * before) and the team textures TeamColourLayer looks up for the most common units.
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class UnitSkinGameTests {

    static final String ARENA = SkirmishGameTests.ARENA;

    @GameTest(template = ARENA)
    public static void hd_unit_skins_ship_in_the_mod(GameTestHelper helper) {
        List<String> missing = new ArrayList<>();
        for (String skin : List.of("zombie_unit", "husk_unit", "skeleton_unit", "wither_skeleton_unit",
                "grunt_unit", "brute_unit", "headhunter_unit"))
            for (String file : List.of(skin + ".png", skin + "_team.png"))
                if (!SkirmishGameTests.modFileHas("assets", ReignOfNether.MOD_ID, "textures", "entities", file))
                    missing.add("textures/entities/" + file);
        for (String skin : List.of("vindicator_unit", "pillager_unit", "seedshaper_unit", "shipwright_unit",
                "geomancer_unit", "tinker_unit"))
            if (!SkirmishGameTests.modFileHas("assets", ReignOfNether.MOD_ID, "textures", "entities", skin + "_team.png"))
                missing.add("textures/entities/" + skin + "_team.png");
        if (!missing.isEmpty())
            helper.fail("unit skins missing from the mod file: " + missing);
        helper.succeed();
    }
}
