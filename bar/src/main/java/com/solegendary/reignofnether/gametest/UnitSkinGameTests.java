package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import com.solegendary.reignofnether.util.TextureMips;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

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

    /**
     * The mip filter of the generated unit bodies (claude/unit-mipmaps.md): averages in linear light, lends no colour
     * from empty texels, and keeps an empty block empty (the glow is drawn additively - empty must stay black).
     */
    @GameTest(template = ARENA, batch = "texture_mips")
    public static void unit_texture_mips_average_in_linear_light_and_ignore_empty_texels(GameTestHelper helper) {
        List<String> wrong = new ArrayList<>();
        int black = 0xFF000000, white = 0xFFFFFFFF, red = 0xFF0000FF, clearGreen = 0x0000FF00;
        int grey = TextureMips.halve(new int[] {black, white, white, black}, 2, 2)[0];
        if ((grey >>> 24) != 255 || Math.abs((grey & 0xFF) - 188) > 1)
            wrong.add(String.format("black+white -> %08x, wanted opaque grey 188 (linear light)", grey));
        int edge = TextureMips.halve(new int[] {red, clearGreen, clearGreen, clearGreen}, 2, 2)[0];
        if ((edge & 0xFFFFFF) != 0x0000FF || (edge >>> 24) != 64)
            wrong.add(String.format("one red texel among empty green ones -> %08x, wanted red at alpha 64", edge));
        int empty = TextureMips.halve(new int[] {clearGreen, clearGreen, clearGreen, clearGreen}, 2, 2)[0];
        if (empty != 0)
            wrong.add(String.format("an empty block -> %08x, wanted 0", empty));
        if (TextureMips.levels(1024, 1024, 3) != 3 || TextureMips.levels(2048, 1024, 9) != 9
                || TextureMips.levels(24, 16, 3) != 3 || TextureMips.levels(12, 16, 3) != 2
                || TextureMips.levels(64, 64, 0) != 0)
            wrong.add("levels(): " + TextureMips.levels(1024, 1024, 3) + ", " + TextureMips.levels(2048, 1024, 9)
                    + ", " + TextureMips.levels(24, 16, 3) + ", " + TextureMips.levels(12, 16, 3) + ", "
                    + TextureMips.levels(64, 64, 0) + " - wanted 3, 9, 3, 2, 0");
        if (!wrong.isEmpty())
            helper.fail("unit texture mips: " + wrong);
        helper.succeed();
    }

    /**
     * Why the generated bodies may take three mip levels with no repacking: tools/dsl_to_game.py packs every island at
     * whole model pixels, 8 texels each, and paints it whole, so every 8x8 block of the body, its _team mask and its
     * _glow is all empty or all painted - levels 1-3 never blend two islands or an island with empty space. A
     * generator change that breaks this fails here instead of as speckled seams on units seen from the RTS camera.
     */
    @GameTest(template = ARENA, batch = "texture_mips")
    public static void generated_unit_atlases_mip_three_levels_without_mixing_islands(GameTestHelper helper) {
        var modFile = net.minecraftforge.fml.ModList.get().getModFileById(ReignOfNether.MOD_ID);
        if (modFile == null) {
            helper.fail("the mod file is not loaded");
            return;
        }
        Path dir = modFile.getFile().findResource("assets", ReignOfNether.MOD_ID, "textures", "entities");
        List<String> bodies = new ArrayList<>();
        List<String> wrong = new ArrayList<>();
        try (Stream<Path> files = Files.list(dir)) {
            files.map(p -> p.getFileName().toString()).filter(n -> n.endsWith("_unit_glow.png"))
                    .map(n -> n.substring(0, n.length() - "_glow.png".length())).sorted().forEach(bodies::add);
        } catch (Exception e) {
            helper.fail("could not list textures/entities: " + e);
            return;
        }
        // the HD skins on the shared villager model (tools/hd_villager.py) are mipmapped too, and have no _glow
        List<String> hdSkins = List.of("evoker_unit", "royal_architect_unit", "field_mason_unit", "dawn_paladin_unit");
        bodies.addAll(hdSkins);
        for (String body : bodies) {
            for (String file : hdSkins.contains(body) ? List.of(body + ".png", body + "_team.png")
                    : List.of(body + ".png", body + "_team.png", body + "_glow.png")) {
                Path path = dir.resolve(file);
                if (!Files.exists(path)) {
                    wrong.add(file + " is missing");
                    continue;
                }
                try (var in = Files.newInputStream(path)) {
                    BufferedImage img = javax.imageio.ImageIO.read(in);
                    int w = img.getWidth(), h = img.getHeight();
                    int[] px = img.getRGB(0, 0, w, h, null, 0, w);
                    if (TextureMips.levels(w, h, TextureMips.MAX_LEVELS) != TextureMips.MAX_LEVELS)
                        wrong.add(file + " is " + w + "x" + h + ", not divisible by 8");
                    String mixed = TextureMips.firstMixedBlock(px, w, h, 1 << TextureMips.MAX_LEVELS);
                    if (mixed != null)
                        wrong.add(file + " has a half-painted 8x8 block at texel " + mixed);
                } catch (Exception e) {
                    wrong.add(file + " could not be read: " + e);
                }
            }
        }
        if (bodies.size() < 9)
            wrong.add("found only " + bodies.size() + " generated bodies (" + bodies + "), expected at least the five "
                    + "Sunforged ones and the four HD villager-model skins; "
                    + "Sunforged ones");
        if (!wrong.isEmpty())
            helper.fail("generated unit atlases: " + wrong);
        helper.succeed();
    }
}
