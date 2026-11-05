package com.solegendary.reignofnether.perf;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Late-game frame rate (lovish's log, Oct 23): worldMarkers cost 2.2 to 2.8 ms a frame in "165 calls". There are not
 * 165 draws: Forge fires RenderLevelStageEvent once per level stage (about ten a frame) at every handler, and each of
 * our handlers opened its FrameParts timer and asked the shader pack (a reflective call) before checking the stage - so
 * 15 handlers did that work about ten times a frame to draw in one stage. Every handler of the worldMarkers and
 * buildingDecor sections must test getStage() before it opens its section, asks the pack or fetches a buffer.
 */
class RenderStageGateTest {

    private static final String ROOT = "src/main/java/com/solegendary/reignofnether/";
    private static final List<String> FILES = List.of(
            "blocks/BlockClientEvents.java", "building/BuildingLineClientEvents.java",
            "building/PlacementGridClientEvents.java", "fogofwar/PlayerChunksClientEvents.java",
            "maps/MapClient.java", "minimap/MapDrawClientEvents.java", "startpos/StartPosClientEvents.java",
            "resources/MetalPatchMarkerClientEvents.java", "resources/ResourcesClientEvents.java",
            "resources/WreckClientEvents.java", "building/BannerRenderClientEvents.java",
            "building/ScaffoldRenderClientEvents.java", "building/StatueRenderClientEvents.java",
            "building/WindmillRenderClientEvents.java");

    // only the @SubscribeEvent handlers: a private helper taking the event (StatueRenderClientEvents.draw) runs after
    // its handler's gate and has no section of its own
    private static final Pattern HANDLER = Pattern.compile(
            "@SubscribeEvent(?:\\([^)]*\\))?\\s+public\\s+static\\s+void\\s+\\w+\\(RenderLevelStageEvent\\s+(\\w+)\\)\\s*\\{");

    @Test
    void everyLevelStageHandlerChecksItsStageBeforeAnyWork() throws IOException {
        int handlers = 0;
        for (String file : FILES) {
            Path p = Path.of(ROOT + file);
            if (!Files.exists(p))
                p = Path.of("bar/" + ROOT + file);   // run from the repo root
            assertTrue(Files.exists(p), "source not found: " + file);
            String src = Files.readString(p);
            Matcher m = HANDLER.matcher(src);
            while (m.find()) {
                handlers++;
                String evt = m.group(1);
                int begin = src.indexOf("FrameParts.begin(", m.end());
                assertTrue(begin > 0, file + ": handler without a FrameParts section");
                String before = src.substring(m.end(), begin);
                assertTrue(before.contains(evt + ".getStage()"), file + ": a RenderLevelStageEvent handler opens its "
                        + "FrameParts section before checking getStage(): it would run on every level stage");
            }
        }
        assertTrue(handlers >= 14, "expected the section's handlers, found " + handlers);
    }
}
