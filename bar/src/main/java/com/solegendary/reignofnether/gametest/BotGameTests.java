package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.bot.BotPlayer;
import com.solegendary.reignofnether.bot.BotStartPick;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.Buildings;
import com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement;
import com.solegendary.reignofnether.faction.Factions;
import com.solegendary.reignofnether.maps.MapLayout;
import com.solegendary.reignofnether.maps.MapPreset;
import com.solegendary.reignofnether.maps.StartPhase;
import com.solegendary.reignofnether.startpos.CapturePointServerEvents;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Headless tests of the skirmish bot on tonight's systems (design/bot.md "Maps"): capture sites, patch values, the
 * start-position phase and lab levels. Their own holder next to SkirmishGameTests (same arena template, unique
 * owners, own sky pads, no global counts) so they run in parallel with everything else.
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class BotGameTests {

    static final String ARENA = SkirmishGameTests.ARENA;

    /** A solid stone pad high over the arena's corner (glass is not solid ground to MetalPatches.solidTop, which
     *  places capture sites), {@code size} across. Returns the standing position at its corner. */
    private static BlockPos stonePad(GameTestHelper helper, int y, int size) {
        BlockPos corner = helper.absolutePos(BlockPos.ZERO);
        for (int dx = 0; dx < size; dx++)
            for (int dz = 0; dz < size; dz++)
                helper.getLevel().setBlock(new BlockPos(corner.getX() + dx, y, corner.getZ() + dz), Blocks.SMOOTH_STONE.defaultBlockState(), 3);
        return new BlockPos(corner.getX(), y + 1, corner.getZ());
    }

    /** n Vindicators of one owner in a row of four from a corner. */
    private static List<LivingEntity> vindicators(ServerLevel level, String owner, BlockPos at, int n) {
        List<LivingEntity> out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            var v = com.solegendary.reignofnether.registrars.EntityRegistrar.VINDICATOR_UNIT.get().create(level);
            if (v == null)
                continue;
            v.moveTo(at.getX() + 0.5 + (i % 4), at.getY(), at.getZ() + 0.5 + (i / 4), 0, 0);
            v.setOwnerName(owner);
            level.addFreshEntity(v);
            out.add(v);
        }
        return out;
    }

    /**
     * BotSites: a hard bot five minutes in, with six Vindicators at home and a neutral Watchtower 12 blocks away on
     * its own sky pad, sends a squad onto the site and holds it until the capture (20 s uncontested) makes it the
     * owner - within a minute of game time, ticked by the real capture loop. The squad is on site duty
     * (BotSites.holders). Nobody else counts as an enemy (enemyFilter), so a neighbouring test can't contest it.
     */
    @GameTest(template = ARENA, timeoutTicks = 1500)
    public static void bot_takes_a_nearby_capture_site(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = "gametest_bot_site_" + helper.absolutePos(BlockPos.ZERO).asLong();
        BlockPos pad = stonePad(helper, 170, 26);
        BlockPos home = pad.offset(4, 0, 12);
        var brain = new BotPlayer(owner, Factions.VILLAGERS, BotPlayer.Difficulty.HARD, home);
        brain.enemyFilter = o -> false;
        Entity site = CapturePointServerEvents.place(level, home.getX() + 12, home.getZ(), CapturePointServerEvents.Kind.WATCHTOWER);
        List<LivingEntity> army = vindicators(level, owner, home.offset(-2, 0, -2), 6);
        if (site == null || army.size() != 6) {
            helper.fail("could not place the site or the vindicators");
            return;
        }
        boolean[] done = { false };
        Runnable cleanup = () -> {
            army.forEach(Entity::discard);
            site.discard();
        };
        for (int t = 5; t < 1440; t += 20) {
            final int at = t;
            helper.runAfterDelay(t, new Runnable() {   // a fresh Runnable each: GameTestInfo keys tasks by instance
                @Override
                public void run() {
                    if (done[0])
                        return;
                    army.removeIf(le -> !le.isAlive());
                    brain.army.run(level, level.getGameTime(), army, army, List.of(), 5);
                    if (owner.equals(CapturePointServerEvents.ownerOf(site))) {
                        done[0] = true;
                        boolean onDuty = !brain.sites.holders.isEmpty();
                        cleanup.run();
                        if (!onDuty)
                            helper.fail("the site was taken but no squad is on site duty");
                        else
                            helper.succeed();
                    } else if (at + 20 >= 1440) {
                        done[0] = true;
                        String why = "the bot did not take the Watchtower 12 blocks from home in " + at + " ticks: owner '"
                            + CapturePointServerEvents.ownerOf(site) + "' claimant '" + CapturePointServerEvents.claimantOf(site)
                            + "' progress " + CapturePointServerEvents.progressOf(site) + ", squad " + brain.sites.holders.size()
                            + ", last target " + brain.sites.lastTarget + ", nearest unit "
                            + (army.isEmpty() ? "none" : String.format("%.1f", Math.sqrt(army.get(0).distanceToSqr(site))));
                        cleanup.run();
                        helper.fail(why);
                    }
                }
            });
        }
    }

    /**
     * BotPlayer.bestPatch, the expansion rule (design/maps.md: 1.0 / 1.5 / 2.0x patches): at equal distance the rich
     * patch wins; a 2.0x patch is worth walking a bit less than twice as far for, a 1.5x patch at twice the distance
     * is not; a patch the usable test refuses is never picked. Pure: no world.
     */
    @GameTest(template = ARENA)
    public static void bot_prefers_a_rich_patch_at_equal_distance(GameTestHelper helper) {
        BlockPos home = new BlockPos(0, 64, 0);
        BlockPos east = new BlockPos(20, 64, 0), south = new BlockPos(0, 64, 20), west = new BlockPos(-20, 64, 0);
        Map<BlockPos, Float> value = new HashMap<>(Map.of(east, 1f, south, 1.5f, west, 1f));
        List<String> problems = new ArrayList<>();
        BlockPos got = BotPlayer.bestPatch(home, List.of(east, south, west), p -> value.getOrDefault(p, 1f), p -> true);
        if (!south.equals(got))
            problems.add("at equal distance it took " + got + " over the 1.5x patch " + south);
        got = BotPlayer.bestPatch(home, List.of(east, south, west), p -> value.getOrDefault(p, 1f), p -> !p.equals(south));
        if (got == null || got.equals(south))
            problems.add("with the rich patch taken it chose " + got);
        BlockPos farCentre = new BlockPos(0, 64, -36);
        value.put(farCentre, 2f);
        got = BotPlayer.bestPatch(home, List.of(east, farCentre), p -> value.getOrDefault(p, 1f), p -> true);
        if (!farCentre.equals(got))
            problems.add("a 2.0x patch at 36 should beat a 1.0x at 20, got " + got);
        BlockPos farRich = new BlockPos(0, 64, 40);
        value.put(farRich, 1.5f);
        got = BotPlayer.bestPatch(home, List.of(farRich, east), p -> value.getOrDefault(p, 1f), p -> true);
        if (!east.equals(got))
            problems.add("a 1.5x patch at 40 should lose to a 1.0x at 20, got " + got);
        if (!problems.isEmpty())
            helper.fail(String.join("; ", problems));
        else
            helper.succeed();
    }

    /**
     * BotStartPick on a real pre-made layout (Highlands, 1v1; the phase is never begun, so nothing is seated): the
     * bot's start lies inside its team's box, keeps its capitol square clear of every patch's reserved square, and
     * scores at least as well as the bare slot. With two 2.0x patches added 26 blocks to one side of the slot (inside
     * the box), the start moves toward them. Patches are handed in, not stamped: no world change.
     */
    @GameTest(template = ARENA)
    public static void bot_places_its_start_inside_its_box_near_metal(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos base = helper.absolutePos(new BlockPos(8, 1, 8));
        int cx = base.getX() + 60000, cz = base.getZ() + 60000;
        var layout = MapLayout.build(MapPreset.HIGHLANDS, new int[] { 0, 1 });
        var bot = new StartPhase.Pick("AI 1", true, 0, 0, 0xFF0000, null);
        List<StartPhase.Pick> picks = new ArrayList<>(List.of(bot,
            new StartPhase.Pick("gametest_sp_bot_h", false, 1, 1, 0x0000FF, null)));
        var phase = new StartPhase(level, cx, cz, layout, picks, 45, null);
        Map<BlockPos, Float> value = new HashMap<>();
        List<BlockPos> patches = new ArrayList<>();
        for (MapLayout.Mex m : layout.mexes) {
            BlockPos p = new BlockPos(cx + m.dx(), 64, cz + m.dz());
            patches.add(p);
            value.put(p, m.value());
        }
        int half = (com.solegendary.reignofnether.startpos.StartAreaClearing.capitolSpan(level) - 1) / 2;
        var slot = layout.slots.get(0);
        int sx = cx + slot.dx(), sz = cz + slot.dz();
        int ring = (int) Math.round(Math.hypot(slot.dx(), slot.dz()));
        List<String> problems = new ArrayList<>();
        int[] got = BotStartPick.choose(phase, bot, patches, p -> value.getOrDefault(p, 1f), half);
        if (got == null) {
            helper.fail("no start chosen on Highlands (" + patches.size() + " patches)");
            return;
        }
        if (!phase.inBox(0, got[0], got[1]))
            problems.add("the start [" + got[0] + ", " + got[1] + "] is outside its box");
        int r = half + com.solegendary.reignofnether.resources.MetalPatches.RESERVED_HALF;
        for (BlockPos p : patches)
            if (Math.abs(p.getX() - got[0]) <= r && Math.abs(p.getZ() - got[1]) <= r)
                problems.add("the start's capitol covers the patch at " + p.toShortString());
        double atSlot = BotStartPick.score(sx, sz, sx, sz, cx, cz, ring, patches, p -> value.getOrDefault(p, 1f));
        double atPick = BotStartPick.score(got[0], got[1], sx, sz, cx, cz, ring, patches, p -> value.getOrDefault(p, 1f));
        if (atPick + 1e-6 < atSlot)
            problems.add("the start scores " + atPick + ", worse than the slot's " + atSlot);
        // two 2.0x patches 26 blocks to the side of the slot (perpendicular to the centre line, inside the box)
        double ux = -slot.dz() / (double) ring, uz = slot.dx() / (double) ring;
        BlockPos rich1 = new BlockPos(sx + (int) Math.round(ux * 26), 64, sz + (int) Math.round(uz * 26));
        BlockPos rich2 = new BlockPos(sx + (int) Math.round(ux * 30 - uz * 4), 64, sz + (int) Math.round(uz * 30 + ux * 4));
        List<BlockPos> more = new ArrayList<>(patches);
        more.add(rich1);
        more.add(rich2);
        value.put(rich1, 2f);
        value.put(rich2, 2f);
        if (!phase.inBox(0, rich1.getX(), rich1.getZ()))
            problems.add("(test setup) the rich patches fell outside the box");
        int[] moved = BotStartPick.choose(phase, bot, more, p -> value.getOrDefault(p, 1f), half);
        if (moved == null)
            problems.add("no start chosen with the rich patches added");
        else if (Math.hypot(moved[0] - rich1.getX(), moved[1] - rich1.getZ()) >= Math.hypot(sx - rich1.getX(), sz - rich1.getZ()))
            problems.add("the start [" + moved[0] + ", " + moved[1] + "] did not move toward the 2.0x patches (slot ["
                + sx + ", " + sz + "], patch " + rich1.toShortString() + ")");
        else if (!phase.inBox(0, moved[0], moved[1]))
            problems.add("the moved start left the box");
        if (!problems.isEmpty())
            helper.fail(String.join("; ", problems));
        else
            helper.succeed();
    }

    /**
     * BotPlayer.queueLabLevel with BotEconomy's upgrade-or-new-lab rule: one Barracks gets no level (a second door
     * first); two Barracks with metal nearly empty get none; two with spare metal queue Level 2 on one of them; and a
     * bot defending its base levels nothing (the lab would stop making units). Placements never put in the world, a
     * pool under a unique owner.
     */
    @GameTest(template = ARENA)
    public static void bot_upgrades_a_lab_with_two_labs_and_spare_metal(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = "gametest_bot_lablevel_" + helper.absolutePos(BlockPos.ZERO).asLong();
        var pool = new com.solegendary.reignofnether.resources.Resources(owner, 0, 2000, 20);
        com.solegendary.reignofnether.resources.ResourcesServerEvents.resourcesList.add(pool);
        var a = com.solegendary.reignofnether.building.BuildingUtils.getNewBuildingPlacement(Buildings.BARRACKS, level,
            helper.absolutePos(new BlockPos(2, 2, 2)), Rotation.NONE, owner, false);
        var b = com.solegendary.reignofnether.building.BuildingUtils.getNewBuildingPlacement(Buildings.BARRACKS, level,
            helper.absolutePos(new BlockPos(2, 2, 14)), Rotation.NONE, owner, false);
        try {
            if (!(a instanceof ProductionPlacement pa) || !(b instanceof ProductionPlacement pb)) {
                helper.fail("the Barracks is not a production placement");
                return;
            }
            pa.isBuilt = true;
            pb.isBuilt = true;
            var brain = new BotPlayer(owner, Factions.VILLAGERS, BotPlayer.Difficulty.HARD, a.originPos);
            List<String> problems = new ArrayList<>();
            brain.metalFill = 0.8f;
            if (brain.queueLabLevel(new ArrayList<BuildingPlacement>(List.of(a))))
                problems.add("one lab was levelled (a second lab comes first)");
            brain.metalFill = 0.02f;   // 20 metal in the pool: under half of Level 2's 90
            if (brain.queueLabLevel(new ArrayList<BuildingPlacement>(List.of(a, b))))
                problems.add("a lab was levelled with the metal nearly empty");
            pool.addMetal(580);
            brain.metalFill = 0.6f;
            brain.army.state = com.solegendary.reignofnether.bot.BotArmy.State.DEFEND;
            if (brain.queueLabLevel(new ArrayList<BuildingPlacement>(List.of(a, b))))
                problems.add("a lab was levelled while the base is under attack");
            brain.army.state = com.solegendary.reignofnether.bot.BotArmy.State.BUILD_UP;
            boolean queued = brain.queueLabLevel(new ArrayList<BuildingPlacement>(List.of(a, b)));
            int upgrades = 0;
            for (var pp : List.of(pa, pb))
                for (var item : pp.productionQueue)
                    if (item.item == com.solegendary.reignofnether.building.production.ProductionItems.UPGRADE_LAB_L2)
                        upgrades++;
            if (!queued || upgrades != 1)
                problems.add("two labs and spare metal should queue exactly one Level 2: queued " + queued + ", upgrades " + upgrades);
            if (brain.queueLabLevel(new ArrayList<BuildingPlacement>(List.of(a, b))))
                problems.add("a second upgrade was queued while one is running");
            if (!problems.isEmpty())
                helper.fail(String.join("; ", problems));
            else
                helper.succeed();
        } finally {
            if (a instanceof ProductionPlacement pa)
                pa.productionQueue.clear();
            if (b instanceof ProductionPlacement pb)
                pb.productionQueue.clear();
            com.solegendary.reignofnether.resources.ResourcesServerEvents.resourcesList.remove(pool);
        }
    }
}
