package com.solegendary.reignofnether.building;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Building animations, slice 1 (lovish, Oct 30: smoke + lit windows, medium; "smoke = working"). What drives each
 * animation, as pure rules: a lab smokes from its stacks only while it works, its windows light only while it works
 * (half-lit for a beat on the way in and out, never a pop), idle labs no longer breathe the working idiom, and the
 * places the smoke and the light come from are found in the real lab structures.
 */
class BuildingWorkTest {

    // ---------------------------------------------------------------- the state that drives both

    @Test
    void aLabWorksOnlyWhileBuiltInSightAndQueued() {
        assertTrue(BuildingWork.isWorking(true, true, true));
        assertFalse(BuildingWork.isWorking(false, true, true), "under construction: no smoke, no light");
        assertFalse(BuildingWork.isWorking(true, true, false), "empty queue: idle");
        // a remembered building's queue is stale (the server stops sending once it is out of sight): it shows nothing
        assertFalse(BuildingWork.isWorking(true, false, true), "fogged: no smoke, no light");
    }

    @Test
    void idleLabsNoLongerBreatheTheWorkingIdiom() {
        // lovish, Oct 30: "smoke = working" - a busy lab and an idle lab used to look the same
        assertFalse(BuildingWork.breathesIdle(false, false, 3), "an idle lab is quiet");
        assertTrue(BuildingWork.breathesIdle(true, false, 3), "the capitol keeps its idle life");
        assertTrue(BuildingWork.breathesIdle(false, true, 1), "energy buildings keep theirs");
        assertFalse(BuildingWork.breathesIdle(false, false, 1));
    }

    @Test
    void aLabIsALevelledArmyBuildingNotTheCapitol() {
        assertTrue(BuildingWork.isLab(false, 3));
        assertFalse(BuildingWork.isLab(true, 3), "the capitol is not a lab");
        assertFalse(BuildingWork.isLab(false, 1));
    }

    // ---------------------------------------------------------------- windows: lit only while working, no pops

    @Test
    void windowsLightOnlyWhileWorkingAndFadeInAndOutInSteps() {
        BuildingWork.Clock clock = new BuildingWork.Clock();
        for (long t : new long[] { 0, 5, 100, 99_999 })
            assertEquals(0f, clock.windowLevel(t), 0f, "never worked: dark at " + t);

        assertTrue(clock.update(true, 100), "starting work is a change");
        assertFalse(clock.update(true, 104), "still working is not");
        for (long t = 100; t < 100 + BuildingWork.WINDOW_ON_TICKS; t++)
            assertEquals(BuildingWork.WINDOW_HALF, clock.windowLevel(t), 0f, "warming up at " + t);
        for (long t = 100 + BuildingWork.WINDOW_ON_TICKS; t < 500; t++)
            assertEquals(1f, clock.windowLevel(t), 0f, "lit while working at " + t);

        assertTrue(clock.update(false, 500), "the queue emptied");
        for (long t = 500; t < 500 + BuildingWork.WINDOW_OFF_TICKS; t++)
            assertEquals(BuildingWork.WINDOW_HALF, clock.windowLevel(t), 0f, "dimming at " + t);
        for (long t = 500 + BuildingWork.WINDOW_OFF_TICKS; t < 1000; t++)
            assertEquals(0f, clock.windowLevel(t), 0f, "dark when idle at " + t);
    }

    @Test
    void theStepsAreLongEnoughToSeeAndTheHalfStepIsBetween() {
        // at 20 tps a step under ~8 ticks is a flicker (Animation craft: hold a key 2-3 ticks at the very least)
        assertTrue(BuildingWork.WINDOW_ON_TICKS >= 8 && BuildingWork.WINDOW_ON_TICKS <= 20, "on step " + BuildingWork.WINDOW_ON_TICKS);
        assertTrue(BuildingWork.WINDOW_OFF_TICKS >= BuildingWork.WINDOW_ON_TICKS && BuildingWork.WINDOW_OFF_TICKS <= 40,
            "off step (heavier than on) " + BuildingWork.WINDOW_OFF_TICKS);
        assertTrue(BuildingWork.WINDOW_HALF > 0.3f && BuildingWork.WINDOW_HALF < 0.8f, "half step " + BuildingWork.WINDOW_HALF);
    }

    @Test
    void aWorldReloadNeverLeavesTheWindowsHalfLit() {
        BuildingWork.Clock clock = new BuildingWork.Clock();
        clock.update(true, 50_000);
        // game time went back (a new world): the clock reads as settled, not as "just changed"
        assertEquals(1f, clock.windowLevel(10), 0f);
        clock.update(false, 60_000);
        assertEquals(0f, clock.windowLevel(10), 0f);
    }

    @Test
    void theWindowLightIsTheMediumWarmWhite() {
        // lovish's pick (medium): lit pane luma 0.72-0.80, saturation 0.20-0.28 - a band, both edges (lesson 3), and
        // low enough in saturation that team colour stays the most saturated thing on screen
        float r = BuildingWork.WARM_R * BuildingWork.WHITE_CONCRETE_R;
        float g = BuildingWork.WARM_G * BuildingWork.WHITE_CONCRETE_G;
        float b = BuildingWork.WARM_B * BuildingWork.WHITE_CONCRETE_B;
        float luma = 0.2126f * r + 0.7152f * g + 0.0722f * b;
        float max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b));
        float sat = (max - min) / max;
        assertTrue(luma >= 0.72f && luma <= 0.80f, "luma " + luma);
        assertTrue(sat >= 0.20f && sat <= 0.28f, "saturation " + sat);
        assertTrue(r >= g && g >= b, "warm: red over green over blue");
    }

    // ---------------------------------------------------------------- smoke strength (medium)

    @Test
    void theSmokeIsMediumAndBudgeted() {
        assertEquals(2, BuildingWork.PUFFS_PER_STACK);
        assertEquals(4, BuildingWork.CHUFF_PUFFS);
        assertEquals(24, BuildingWork.WORK_BUDGET, "working smoke has a budget like idle life's 40");
        // campfire smoke lives 80-130 ticks: the column stands ~5 blocks (medium; subtle ~3, strong ~8)
        double column = BuildingWork.PUFF_RISE * 105;
        assertTrue(column >= 4 && column <= 6, "column " + column);
    }

    @Test
    void aUnitLeavingQueuesOneChuff() {
        // the packet handler notes each release (COMPLETE_PRODUCTION); the next ambience pass takes them, once
        Object lab = new Object();
        assertEquals(0, BuildingWork.takeReleases(lab));
        BuildingWork.noteRelease(lab);
        BuildingWork.noteRelease(lab);
        assertEquals(2, BuildingWork.takeReleases(lab));
        assertEquals(0, BuildingWork.takeReleases(lab), "taken once");
        assertEquals(0, BuildingWork.takeReleases(null));
    }

    // ---------------------------------------------------------------- where the smoke comes from

    @Test
    void anOpenCampfireIsAStackACoveredOneIsNot() {
        BlockPos open = new BlockPos(2, 6, 2), covered = new BlockPos(5, 3, 5);
        Set<BlockPos> solid = new HashSet<>(List.of(open, covered, covered.above(), open.below()));
        assertEquals(List.of(open), BuildingWork.stackTops(List.of(open, covered), solid));
        assertTrue(BuildingWork.isCampfire("campfire"));
        assertTrue(BuildingWork.isCampfire("soul_campfire"));
        assertFalse(BuildingWork.isCampfire("lantern"));
    }

    // ---------------------------------------------------------------- where the light comes from

    @Test
    void glassIsSortedByKind() {
        assertEquals(BuildingWork.FULL, BuildingWork.glassKind("orange_stained_glass", false, false, false, false));
        assertEquals(BuildingWork.FULL, BuildingWork.glassKind("glass", false, false, false, false));
        assertEquals(BuildingWork.PANE_X, BuildingWork.glassKind("light_blue_stained_glass_pane", false, false, true, true));
        assertEquals(BuildingWork.PANE_Z, BuildingWork.glassKind("glass_pane", true, true, false, false));
        assertEquals(BuildingWork.NOT_GLASS, BuildingWork.glassKind("glass_pane", true, false, true, false), "a corner pane");
        assertEquals(BuildingWork.NOT_GLASS, BuildingWork.glassKind("stone_bricks", false, false, false, false));
    }

    @Test
    void onlyOutsideFacesLightAndTheRoofComesFirst() {
        // a 5x3x5 box of stone: a skylight in the roof, a window in the south wall with the room's air behind it
        Set<BlockPos> solid = new HashSet<>();
        for (int x = 0; x < 5; x++)
            for (int z = 0; z < 5; z++)
                for (int y = 0; y < 3; y++)
                    if (y == 0 || y == 2 || x == 0 || x == 4 || z == 0 || z == 4)
                        solid.add(new BlockPos(x, y, z));
        BlockPos sky = new BlockPos(2, 2, 2), wall = new BlockPos(2, 1, 4);
        Map<BlockPos, Integer> glass = Map.of(sky, BuildingWork.FULL, wall, BuildingWork.FULL);
        List<BuildingWork.Face> faces = BuildingWork.windowFaces(glass, solid, 48);
        assertEquals(List.of(new BuildingWork.Face(sky, BuildingWork.UP, false),
            new BuildingWork.Face(wall, BuildingWork.SOUTH, false)), faces,
            "the skylight's underside and the window's room side are inside: unlit (and nothing faces down)");
        assertEquals(1, BuildingWork.windowFaces(glass, solid, 1).size(), "the cap keeps the roof");
        assertEquals(sky, BuildingWork.windowFaces(glass, solid, 1).get(0).cell());
    }

    @Test
    void aPaneLightsOnlyAcrossItsRun() {
        BlockPos pane = new BlockPos(0, 0, 0);
        Set<BlockPos> solid = Set.of(pane, new BlockPos(-1, 0, 0), new BlockPos(1, 0, 0));
        List<BuildingWork.Face> faces = BuildingWork.windowFaces(Map.of(pane, BuildingWork.PANE_X), solid, 48);
        assertEquals(Set.of(BuildingWork.NORTH, BuildingWork.SOUTH), dirs(faces));
        assertTrue(faces.stream().allMatch(BuildingWork.Face::pane));
    }

    @Test
    void everyLabWithWindowsLightsSomeAndEveryLabWithAnOpenCampfireSmokesFromIt() throws IOException {
        // the labs are the levelled structures (an _l2 sibling exists), read from the committed files - not a hand list.
        // Windows are judged per lab (its levels together): run 41037025909 showed the level-1 Graveyard's 3 panes and
        // the level-1 Lotus Academy's 4 are walled in (bricks or planks on every outside side), so no camera sees them
        // and nothing should light there; the rule is "glass you can see lights", checked where a lab has any.
        File dir = new File("src/main/resources/data/reignofnether/structures");
        File[] files = dir.listFiles((d, n) -> n.endsWith("_l2.nbt"));
        assertTrue(files != null && files.length >= 8, "lab structures found: " + (files == null ? 0 : files.length));
        List<String> problems = new ArrayList<>(), dark = new ArrayList<>();
        int labsWithGlass = 0, withFire = 0;
        for (File f : files) {
            String base = f.getName().substring(0, f.getName().length() - "_l2.nbt".length());
            int glassCells = 0, litFaces = 0;
            for (String level : new String[] { "", "_l2", "_l3" }) {
                File file = new File(dir, base + level + ".nbt");
                if (!file.exists())
                    continue;
                Structure s = read(file);
                List<BuildingWork.Face> faces = BuildingWork.windowFaces(s.glass, s.solid, BuildingWork.MAX_WINDOW_FACES);
                glassCells += s.glass.size();
                litFaces += faces.size();
                if (!s.glass.isEmpty() && faces.isEmpty())
                    dark.add(base + level + " (" + s.glass.size() + " glass, all walled in)");
                if (faces.size() > BuildingWork.MAX_WINDOW_FACES)
                    problems.add(base + level + ": " + faces.size() + " faces over the cap");
                boolean openFire = s.fires.stream().anyMatch(p -> !s.solid.contains(p.above()));
                if (openFire) {
                    withFire++;
                    if (BuildingWork.stackTops(s.fires, s.solid).isEmpty())
                        problems.add(base + level + ": an open campfire, no stack");
                }
            }
            if (glassCells > 0) {
                labsWithGlass++;
                if (litFaces == 0)
                    problems.add(base + ": " + glassCells + " glass over its levels, not one lit face");
            }
        }
        System.out.println("[BuildingWork] lab levels with only walled-in glass (stay dark): " + dark);
        assertTrue(labsWithGlass >= 5 && withFire >= 6,
            "labs with glass " + labsWithGlass + ", lab levels with an open campfire " + withFire);
        assertTrue(dark.size() <= 3, "more walled-in glass than expected - check the camera model: " + dark);
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }

    // ---------------------------------------------------------------- helpers

    private static Set<Integer> dirs(List<BuildingWork.Face> faces) {
        Set<Integer> out = new HashSet<>();
        for (BuildingWork.Face f : faces)
            out.add(f.dir());
        return out;
    }

    private record Structure(Set<BlockPos> solid, Map<BlockPos, Integer> glass, List<BlockPos> fires) { }

    /** A structure .nbt as the rules see it: solid cells, glass by kind, campfires. */
    private static Structure read(File file) throws IOException {
        CompoundTag root = NbtIo.readCompressed(file);
        ListTag palette = root.getList("palette", Tag.TAG_COMPOUND);
        ListTag blocks = root.getList("blocks", Tag.TAG_COMPOUND);
        Set<BlockPos> solid = new HashSet<>();
        Map<BlockPos, Integer> glass = new HashMap<>();
        List<BlockPos> fires = new ArrayList<>();
        for (int i = 0; i < blocks.size(); i++) {
            CompoundTag b = blocks.getCompound(i);
            int[] p = new int[3];
            ListTag pos = b.getList("pos", Tag.TAG_INT);
            for (int k = 0; k < 3; k++)
                p[k] = pos.getInt(k);
            BlockPos at = new BlockPos(p[0], p[1], p[2]);
            CompoundTag state = palette.getCompound(b.getInt("state"));
            String name = state.getString("Name");
            String path = name.substring(name.indexOf(':') + 1);
            if (path.equals("air") || path.equals("structure_void") || path.equals("cave_air"))
                continue;
            solid.add(at);
            CompoundTag props = state.getCompound("Properties");
            int kind = BuildingWork.glassKind(path, "true".equals(props.getString("north")),
                "true".equals(props.getString("south")), "true".equals(props.getString("west")),
                "true".equals(props.getString("east")));
            if (kind != BuildingWork.NOT_GLASS)
                glass.put(at, kind);
            if (BuildingWork.isCampfire(path))
                fires.add(at);
        }
        return new Structure(solid, glass, fires);
    }
}
