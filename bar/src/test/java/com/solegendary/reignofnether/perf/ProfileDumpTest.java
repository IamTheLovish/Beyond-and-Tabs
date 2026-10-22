package com.solegendary.reignofnether.perf;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** perf/ProfileDump: reading vanilla's profiling.txt for the [FrameAuto] line. */
class ProfileDumpTest {

    // the shape ProfileResults.saveResults writes: "[depth] |   |   name(count/per tick)- share of parent%/share of all%"
    static final String DUMP = String.join("\n",
        "---- Minecraft Profiler Results ----",
        "// Why did you do that?",
        "",
        "Version: 1.20.1",
        "Time span: 10031 ms",
        "Tick span: 200 ticks",
        "",
        "--- BEGIN PROFILE DUMP ---",
        "",
        "[00] root(1/0)- 100.00%/100.00%",
        "[01] |   render(200/1)- 80.00%/80.00%",
        "[02] |   |   gui(200/1)- 25.00%/20.00%",
        "[03] |   |   |   bat:client/hud(200/1)- 60.00%/12.00%",
        "[03] |   |   |   unspecified(200/1)- 40.00%/8.00%",
        "[02] |   |   level(200/1)- 60.00%/48.00%",
        "[03] |   |   |   bat:client/unitOverlays(200/1)- 25.00%/12.00%",
        "[03] |   |   |   bat:client/hud(200/1)- 3.00%/1.00%",
        "[03] |   |   |   unspecified(200/1)- 70.00%/35.00%",
        "[02] |   |   unspecified(200/1)- 15.00%/12.00%",
        "[01] |   tick(200/1)- 20.00%/20.00%",
        "");

    @Test
    void rowsAreReadWithTheirDepthAndShareOfTheWhole() {
        List<ProfileDump.Row> rows = ProfileDump.parse(DUMP);
        assertEquals(11, rows.size());
        assertEquals(new ProfileDump.Row(0, "root", 100.0), rows.get(0));
        assertEquals(new ProfileDump.Row(3, "bat:client/hud", 12.0), rows.get(3));
    }

    @Test
    void headerLinesAndJunkAreSkipped() {
        assertTrue(ProfileDump.parse("nothing here\n[xx] not a row\n").isEmpty());
        assertTrue(ProfileDump.parse("").isEmpty());
    }

    @Test
    void selfShareIsTheNodeMinusTheRowsPrintedBelowIt() {
        List<ProfileDump.Row> rows = ProfileDump.parse(DUMP);
        double[] self = ProfileDump.selfShares(rows);
        assertEquals(0.0, self[0], 1e-9, "root 100 = render 80 + tick 20");
        assertEquals(12.0, self[3], 1e-9, "a leaf is all self");
        assertEquals(0.0, self[4], 1e-9, "unspecified rows have no self of their own: the parent keeps it");
        assertEquals(8.0, self[2], 1e-9, "gui 20 = hud 12 + unspecified 8, so 8 is gui's own");
    }

    @Test
    void theBatSectionsAreSummedByNameAcrossBranches() {
        List<BatSections.Row> bat = ProfileDump.batSections(ProfileDump.parse(DUMP));
        assertEquals("bat:client/hud", bat.get(0).name());
        assertEquals(13.0, bat.get(0).selfPct(), 1e-9, "12 under gui and 1 under level");
        assertEquals("bat:client/unitOverlays", bat.get(1).name());
        assertEquals(12.0, bat.get(1).selfPct(), 1e-9);
    }

    @Test
    void theTopListIsHeaviestSelfFirstWithAShortPath() {
        List<ProfileDump.Top> top = ProfileDump.topSelf(ProfileDump.parse(DUMP), 3, 0.5);
        assertEquals(3, top.size());
        assertEquals(35.0, top.get(0).selfPct(), 1e-9, "level's own time, the biggest");
        assertEquals("root > render > level", top.get(0).path(), "the last three names of the path");
        String text = ProfileDump.text(top);
        assertTrue(text.startsWith("root > render > level 35.0% | "), text);
    }

    @Test
    void readsVanillaDumpLinesWithASpaceBeforeTheDashAndWindowsLineEnds() {
        // the real format, from lovish's client capture (Oct 22): "name(calls/avg) - local%/global%", CRLF line ends
        String real = "[00] gameRenderer(284/1) - 77.91%/77.91%\r\n"
            + "[01] |   level(284/1) - 84.94%/66.18%\r\n"
            + "[02] |   |   bat:client/buildingBoxes(284/1) - 31.70%/7.40%\r\n";
        List<ProfileDump.Row> rows = ProfileDump.parse(real);
        assertEquals(3, rows.size());
        assertEquals(2, rows.get(2).depth());
        assertEquals("bat:client/buildingBoxes", rows.get(2).name());
        assertEquals(7.40, rows.get(2).globalPct(), 1e-9);
    }
}
