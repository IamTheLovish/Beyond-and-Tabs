package com.solegendary.reignofnether.postfx;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * lovish's crash of Oct 31 03:07 (crash-2026-10-31_03.07.54-client.txt): "Missing Palette entry for index 21" on an
 * Embeddium meshing worker - BiomeColors.getAverageGrassColor -> BiomeColorsMixin.shade -> CanopyShade -> the LIVE
 * ClientLevel's getBlockState, read off the render thread while the render thread wrote that chunk's palette. The
 * colour hooks run on meshing threads (Embeddium's chunk builder, vanilla's section compile): they may read only the
 * BlockAndTintGetter they are given (the meshing region's own copy), never the client level.
 */
class MeshingColourThreadTest {

    private static final String ROOT = "src/main/java/com/solegendary/reignofnether/";
    private static final String[] MESHING_HOOKS = {
            "mixin/fogofwar/BiomeColorsMixin.java",
            "mixin/fogofwar/LiquidBlockRendererMixin.java",
            "mixin/fogofwar/EmbeddiumFogOnlyQuadsMixin.java",
            "fogofwar/FogTintingBlockColor.java",
            "postfx/CanopyShade.java",
            "postfx/GrassTint.java",
    };

    private static String source(String file) throws IOException {
        Path p = Path.of(ROOT + file);
        if (!Files.exists(p))
            p = Path.of("bar/" + ROOT + file);   // run from the repo root
        assertTrue(Files.exists(p), "source not found: " + file);
        return Files.readString(p);
    }

    @Test
    void meshingColourHooksNeverReadTheClientLevel() throws IOException {
        for (String file : MESHING_HOOKS) {
            String src = source(file);
            assertFalse(src.contains("Minecraft.getInstance()"), file + " reaches the client (its level) from a meshing thread");
            assertFalse(src.contains("ClientLevel"), file + " names the client level on a meshing path");
        }
    }
}
