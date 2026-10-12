package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.building.Buildings;
import com.solegendary.reignofnether.faction.Factions;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Rotation;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * Headless tests of the Brassworks Artificers' slice 2 (design/brassworks_plan.md: the Assembly Works and its T2).
 * Their own holder next to SkirmishGameTests (same arena template, same openGround helper, unique owners, no global
 * counts) so the faction's tests sit together; every test runs in parallel with the rest.
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class BrassworksGameTests {

    static final String ARENA = SkirmishGameTests.ARENA;

    // ---------------------------------------------------------------------------------------- pre-made maps

    /**
     * design/maps.md: every pre-made map is fair for every start slot, for every player count - each slot's patches
     * (those in its wedge) are worth the same within 5%, every slot is the same distance from the centre, stands at
     * the same terrain height, lies inside its team's start box, and no two patches crowd each other's extractor.
     */
    @GameTest(template = ARENA)
    public static void premade_maps_are_fair_for_every_slot(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        for (var preset : com.solegendary.reignofnether.maps.MapPreset.values()) {
            if (!preset.premade())
                continue;
            for (int n : new int[] { 2, 3, 4, 6, 8, 16 }) {
                int[] teams = new int[n];
                for (int i = 0; i < n; i++)
                    teams[i] = i < (n + 1) / 2 ? 0 : 1;
                var layout = com.solegendary.reignofnether.maps.MapLayout.build(preset, teams);
                String tag = preset + " n=" + n + ": ";
                float min = Float.MAX_VALUE, max = 0;
                for (int s = 0; s < n; s++) {
                    float v = 0;
                    for (var m : layout.mexes)
                        if (layout.slotAt(m.dx(), m.dz()) == s)
                            v += m.value();
                    min = Math.min(min, v);
                    max = Math.max(max, v);
                }
                if (min <= 0 || max > min * 1.05f)
                    problems.add(tag + "slot mex value " + min + ".." + max);
                double dmin = Double.MAX_VALUE, dmax = 0;
                var sample = new com.solegendary.reignofnether.maps.MapLayout.Sample();
                Set<Integer> heights = new HashSet<>();
                for (var s : layout.slots) {
                    double d = Math.hypot(s.dx(), s.dz());
                    dmin = Math.min(dmin, d);
                    dmax = Math.max(dmax, d);
                    layout.sample(s.dx(), s.dz(), sample);
                    heights.add(sample.height);
                    if (!layout.boxOf(s.team()).contains(s.dx(), s.dz()))
                        problems.add(tag + "slot " + s.index() + " outside its team's box");
                }
                if (dmax - dmin > 2)
                    problems.add(tag + "distance to centre " + dmin + ".." + dmax);
                if (heights.size() != 1)
                    problems.add(tag + "slot heights " + heights);
                for (int i = 0; i < layout.mexes.size(); i++)
                    for (int j = i + 1; j < layout.mexes.size(); j++) {
                        var a = layout.mexes.get(i);
                        var b = layout.mexes.get(j);
                        if (Math.abs(a.dx() - b.dx()) <= 5 && Math.abs(a.dz() - b.dz()) <= 5)
                            problems.add(tag + "patches crowd at " + a + " / " + b);
                    }
                for (var m : layout.mexes) {
                    layout.sample(m.dx(), m.dz(), sample);
                    var surf = sample.surface;
                    if (surf == com.solegendary.reignofnether.maps.MapLayout.Surface.WATER
                            || surf == com.solegendary.reignofnether.maps.MapLayout.Surface.LAVA
                            || surf == com.solegendary.reignofnether.maps.MapLayout.Surface.VOID)
                        problems.add(tag + "patch on " + surf + " at " + m);
                }
            }
        }
        if (problems.isEmpty())
            helper.succeed();
        else
            helper.fail(problems.size() + " problem(s): " + String.join("; ", problems.subList(0, Math.min(8, problems.size()))));
    }

    /**
     * design/maps.md: every map spawns its own capture kinds, and they do what they say - a held Lava Vent pays
     * energy, a held Ancient Forge discounts research (not units), a held Oasis heals its owner's units, and Bells
     * and Watchtowers give vision.
     */
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void map_capture_sites_spawn_with_their_effect(GameTestHelper helper) {
        java.util.Map<com.solegendary.reignofnether.maps.MapPreset, Set<com.solegendary.reignofnether.startpos.CapturePointServerEvents.Kind>> expected = java.util.Map.of(
            com.solegendary.reignofnether.maps.MapPreset.ARCHIPELAGO, Set.of(com.solegendary.reignofnether.startpos.CapturePointServerEvents.Kind.BELL, com.solegendary.reignofnether.startpos.CapturePointServerEvents.Kind.WATCHTOWER),
            com.solegendary.reignofnether.maps.MapPreset.HIGHLANDS, Set.of(com.solegendary.reignofnether.startpos.CapturePointServerEvents.Kind.WATCHTOWER, com.solegendary.reignofnether.startpos.CapturePointServerEvents.Kind.FORGE),
            com.solegendary.reignofnether.maps.MapPreset.NETHER_RIFT, Set.of(com.solegendary.reignofnether.startpos.CapturePointServerEvents.Kind.VENT, com.solegendary.reignofnether.startpos.CapturePointServerEvents.Kind.MINE),
            com.solegendary.reignofnether.maps.MapPreset.END_SPIRES, Set.of(com.solegendary.reignofnether.startpos.CapturePointServerEvents.Kind.WATCHTOWER, com.solegendary.reignofnether.startpos.CapturePointServerEvents.Kind.SHRINE),
            com.solegendary.reignofnether.maps.MapPreset.VERDANT_VALLEY, Set.of(com.solegendary.reignofnether.startpos.CapturePointServerEvents.Kind.CAMP, com.solegendary.reignofnether.startpos.CapturePointServerEvents.Kind.SHRINE),
            com.solegendary.reignofnether.maps.MapPreset.DESERT_MESA, Set.of(com.solegendary.reignofnether.startpos.CapturePointServerEvents.Kind.OASIS, com.solegendary.reignofnether.startpos.CapturePointServerEvents.Kind.MINE));
        List<String> problems = new ArrayList<>();
        for (var e : expected.entrySet()) {
            var layout = com.solegendary.reignofnether.maps.MapLayout.build(e.getKey(), new int[] { 0, 0, 1, 1 });
            Set<com.solegendary.reignofnether.startpos.CapturePointServerEvents.Kind> kinds = new HashSet<>();
            for (var s : layout.sites)
                kinds.add(s.kind());
            if (!kinds.equals(e.getValue()))
                problems.add(e.getKey() + " sites " + kinds + " (expected " + e.getValue() + ")");
            if (layout.sites.size() != 4 + 1)
                problems.add(e.getKey() + " has " + layout.sites.size() + " sites for 4 players (one per boundary + centre)");
        }

        // the effects, on three real sites high above this test's arena
        ServerLevel level = helper.getLevel();
        BlockPos base = helper.absolutePos(new BlockPos(8, 1, 8));
        int y = 224;
        String owner = "gametest_mapsite_owner";
        int[][] offs = { { -8, 0 }, { 0, 0 }, { 8, 0 } };
        for (int[] o : offs)
            for (int dx = -4; dx <= 4; dx++)
                for (int dz = -4; dz <= 4; dz++)
                    level.setBlock(new BlockPos(base.getX() + o[0] + dx, y, base.getZ() + o[1] + dz), Blocks.STONE.defaultBlockState(), 3);
        var vent = com.solegendary.reignofnether.startpos.CapturePointServerEvents.place(level, base.getX() - 8, base.getZ(),
            com.solegendary.reignofnether.startpos.CapturePointServerEvents.Kind.VENT);
        var forge = com.solegendary.reignofnether.startpos.CapturePointServerEvents.place(level, base.getX(), base.getZ(),
            com.solegendary.reignofnether.startpos.CapturePointServerEvents.Kind.FORGE);
        var oasis = com.solegendary.reignofnether.startpos.CapturePointServerEvents.place(level, base.getX() + 8, base.getZ(),
            com.solegendary.reignofnether.startpos.CapturePointServerEvents.Kind.OASIS);
        var pool = new com.solegendary.reignofnether.resources.Resources(owner, 0, 0, 0);
        com.solegendary.reignofnether.resources.ResourcesServerEvents.resourcesList.add(pool);
        var unit = com.solegendary.reignofnether.registrars.EntityRegistrar.VINDICATOR_UNIT.get().create(level);
        if (vent == null || forge == null || oasis == null || unit == null) {
            helper.fail("could not set up the map sites");
            return;
        }
        if (com.solegendary.reignofnether.startpos.CapturePointServerEvents.researchCostMultiplier(owner) != 1f)
            problems.add("research discount before the forge is held");
        for (var site : List.of(vent, forge, oasis))
            site.getPersistentData().putString(com.solegendary.reignofnether.startpos.CapturePointServerEvents.KEY_OWNER, owner);
        unit.moveTo(base.getX() + 10.5, y + 1, base.getZ() + 0.5, 0, 0);
        unit.setOwnerName(owner);
        level.addFreshEntity(unit);
        unit.setHealth(unit.getMaxHealth() * 0.5f);
        float hp0 = unit.getHealth();
        helper.runAfterDelay(3, () -> {
            try {
                for (int i = 0; i < 3; i++)
                    com.solegendary.reignofnether.startpos.CapturePointServerEvents.tick(level, 1f);
                if (pool.getEnergy() <= 0)
                    problems.add("a held Lava Vent paid no energy");
                if (Math.abs(com.solegendary.reignofnether.startpos.CapturePointServerEvents.researchCostMultiplier(owner)
                        - (1f - com.solegendary.reignofnether.startpos.CapturePointServerEvents.FORGE_DISCOUNT)) > 1e-4)
                    problems.add("a held Ancient Forge gave no research discount");
                if (unit.getHealth() <= hp0)
                    problems.add("a held Oasis did not heal (" + hp0 + " -> " + unit.getHealth() + ")");
                if (com.solegendary.reignofnether.startpos.CapturePointServerEvents.sightOf(vent) != 0
                        || com.solegendary.reignofnether.startpos.CapturePointServerEvents.WATCHTOWER_SIGHT <= com.solegendary.reignofnether.startpos.CapturePointServerEvents.BELL_SIGHT)
                    problems.add("vision kinds wrong");
            } finally {
                unit.discard();
                vent.discard();
                forge.discard();
                oasis.discard();
                com.solegendary.reignofnether.resources.ResourcesServerEvents.resourcesList.remove(pool);
            }
            if (problems.isEmpty())
                helper.succeed();
            else
                helper.fail(String.join("; ", problems));
        });
    }

    /**
     * design/maps.md section 5: in the start-position phase a click outside your team's box is refused, a click
     * inside is kept (snapped clear of patches), bots are placed at once, and the timeout places everyone left
     * inside their box and starts the match. When every human has placed, the phase ends early.
     */
    @GameTest(template = ARENA)
    public static void start_phase_places_everyone_in_their_box(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        // far from every other test's arena, patches and sites: only the box rules are under test here
        BlockPos base = helper.absolutePos(new BlockPos(8, 1, 8));
        int cx = base.getX() + 40000, cz = base.getZ() + 40000;
        int[] teams = { 0, 0, 1, 1 };
        var layout = com.solegendary.reignofnether.maps.MapLayout.build(com.solegendary.reignofnether.maps.MapPreset.HIGHLANDS, teams);
        List<String> problems = new ArrayList<>();
        int[] done = { 0 };
        List<com.solegendary.reignofnether.maps.StartPhase.Pick> picks = new ArrayList<>(List.of(
            new com.solegendary.reignofnether.maps.StartPhase.Pick("gametest_sp_a", false, 0, 0, 0xFF0000, null),
            new com.solegendary.reignofnether.maps.StartPhase.Pick("gametest_sp_b", false, 0, 1, 0x00FF00, null),
            new com.solegendary.reignofnether.maps.StartPhase.Pick("AI 1", true, 1, 2, 0x0000FF, null),
            new com.solegendary.reignofnether.maps.StartPhase.Pick("gametest_sp_c", false, 1, 3, 0xFFFF00, null)));
        var phase = new com.solegendary.reignofnether.maps.StartPhase(level, cx, cz, layout, picks, 45, ph -> done[0]++);
        if (!picks.get(2).placed)
            problems.add("the bot was not placed at once");
        var enemySlot = layout.slots.get(3);
        if (phase.place("gametest_sp_a", cx + enemySlot.dx(), cz + enemySlot.dz()))
            problems.add("a click in the enemy's box was accepted");
        var mySlot = layout.slots.get(0);
        // ten blocks towards the centre from slot 0: inside team 0's box
        int px = cx + (int) Math.round(mySlot.dx() * 0.93), pz = cz + (int) Math.round(mySlot.dz() * 0.93);
        if (!phase.place("gametest_sp_a", px, pz))
            problems.add("a click inside the own box was refused");
        else if (!phase.inBox(0, picks.get(0).x, picks.get(0).z))
            problems.add("the kept spot left the box");
        for (int t = 0; t < 45 * 20 + 5 && !phase.isFinished(); t++)
            phase.tick();
        if (!phase.isFinished() || done[0] != 1)
            problems.add("the timeout did not end the phase (finished " + phase.isFinished() + ", starts " + done[0] + ")");
        for (var p : picks)
            if (!p.placed || !phase.inBox(p.team, p.x, p.z))
                problems.add(p.name + " ended outside the box at [" + p.x + ", " + p.z + "]");
        if (picks.get(1).x != cx + layout.slots.get(1).dx() || picks.get(1).z != cz + layout.slots.get(1).dz())
            problems.add("an unplaced human was not put on their slot");

        // everyone places: the phase ends a few seconds later, long before the clock
        List<com.solegendary.reignofnether.maps.StartPhase.Pick> picks2 = new ArrayList<>(List.of(
            new com.solegendary.reignofnether.maps.StartPhase.Pick("gametest_sp_d", false, 0, 0, 0xFF0000, null),
            new com.solegendary.reignofnether.maps.StartPhase.Pick("AI 2", true, 1, 1, 0x0000FF, null)));
        var layout2 = com.solegendary.reignofnether.maps.MapLayout.build(com.solegendary.reignofnether.maps.MapPreset.DESERT_MESA, new int[] { 0, 1 });
        int[] done2 = { 0 };
        var phase2 = new com.solegendary.reignofnether.maps.StartPhase(level, cx, cz, layout2, picks2, 45, ph -> done2[0]++);
        var s0 = layout2.slots.get(0);
        if (!phase2.place("gametest_sp_d", cx + s0.dx(), cz + s0.dz() + 6))
            problems.add("phase 2: the click was refused");
        for (int t = 0; t < com.solegendary.reignofnether.maps.StartPhase.EARLY_END_TICKS + 2; t++)
            phase2.tick();
        if (!phase2.isFinished() || done2[0] != 1)
            problems.add("all placed did not end the phase early");
        if (problems.isEmpty())
            helper.succeed();
        else
            helper.fail(String.join("; ", problems));
    }
}
