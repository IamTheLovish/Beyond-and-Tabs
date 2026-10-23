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
     * BotArmy: a base razed from the air is defended. A hard bot in BUILD_UP with twenty Vindicators idle at home and
     * its Town Centre standing; an enemy Ghast hovers 30 blocks above the pad (outside the home scan's HEIGHT_BAND,
     * so the flat scan sees no intruder - lovish's Oct 13 2v2: forty Sunforged fighters stood in rows while a
     * Celestial Dragon razed their base) and hits the Town Centre (health down, lastAttacker set, as the building's
     * own damage path does). The next army step flips to DEFEND and within a few ticks every fighter carries an
     * attack-move target on the ground under the Ghast - not a block in the air, not the far rally. Own glass pad (high
     * enough that no other test's capture site is within BotSites' height band);
     * everyone holds fire so nothing dies while the orders land.
     */
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void bot_army_defends_its_base_against_a_flyer(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = "gametest_bot_airdef_" + helper.absolutePos(BlockPos.ZERO).asLong(), raider = owner + "_dragon";
        BlockPos corner = helper.absolutePos(BlockPos.ZERO);
        int padY = 230;   // (60 above the site test's stone pad: past BotSites.HEIGHT_BAND, so its Watchtower is not on this map)
        for (int dx = 0; dx < 16; dx++)
            for (int dz = 0; dz < 16; dz++)
                level.setBlock(new BlockPos(corner.getX() + dx, padY, corner.getZ() + dz), Blocks.GLASS.defaultBlockState(), 3);
        BlockPos home = new BlockPos(corner.getX() + 8, padY + 1, corner.getZ() + 8);
        var brain = new BotPlayer(owner, Factions.VILLAGERS, BotPlayer.Difficulty.HARD, home);
        brain.enemyFilter = raider::equals;
        var capitol = com.solegendary.reignofnether.building.BuildingUtils.getNewBuildingPlacement(Buildings.TOWN_CENTRE, level,
            home, Rotation.NONE, owner, false);
        List<LivingEntity> army = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            var v = com.solegendary.reignofnether.registrars.EntityRegistrar.VINDICATOR_UNIT.get().create(level);
            if (v == null)
                continue;
            v.moveTo(home.getX() - 5.5 + (i % 4), home.getY(), home.getZ() - 2.5 + (i / 4), 0, 0);
            v.setOwnerName(owner);
            com.solegendary.reignofnether.unit.EngagementStance.set(v, com.solegendary.reignofnether.unit.EngagementStance.HOLD_FIRE);
            level.addFreshEntity(v);
            army.add(v);
        }
        var ghast = com.solegendary.reignofnether.registrars.EntityRegistrar.GHAST_UNIT.get().create(level);
        if (capitol == null || army.size() != 20 || ghast == null) {
            army.forEach(Entity::discard);
            helper.fail("could not make the Town Centre, 20 Vindicators and a Ghast");
            return;
        }
        capitol.isBuilt = true;
        ghast.moveTo(home.getX() + 4.5, home.getY() + 30, home.getZ() + 3.5, 0, 0);
        ghast.setOwnerName(raider);
        com.solegendary.reignofnether.unit.EngagementStance.set(ghast, com.solegendary.reignofnether.unit.EngagementStance.HOLD_FIRE);
        level.addFreshEntity(ghast);
        Runnable cleanup = () -> {
            army.forEach(Entity::discard);
            ghast.discard();
        };
        List<BuildingPlacement> buildings = new ArrayList<>(List.of(capitol));
        helper.runAfterDelay(3, () -> {
            // a quiet step first: the health is remembered, the flyer above the band is no intruder, no alarm
            brain.army.run(level, level.getGameTime(), army, army, buildings, 5);
            if (brain.army.state != com.solegendary.reignofnether.bot.BotArmy.State.BUILD_UP) {
                cleanup.run();
                helper.fail("a Ghast 30 blocks up that has not fired should raise no alarm: state " + brain.army.state
                    + " intruder " + brain.army.lastIntruder);
                return;
            }
            if (!ghast.isFlyingUnit()) {
                cleanup.run();
                helper.fail("the Ghast is not a flying unit");
            }
        });
        helper.runAfterDelay(6, () -> {
            // the Ghast's bolt lands: the Town Centre loses health and records its attacker
            capitol.lastAttacker = ghast;
            capitol.partialBlocksDestroyed += 10;
            brain.army.run(level, level.getGameTime(), army, army, buildings, 5);
            if (brain.army.state != com.solegendary.reignofnether.bot.BotArmy.State.DEFEND) {
                cleanup.run();
                helper.fail("the Town Centre under fire from the air should pull the army onto the attacker: state "
                    + brain.army.state + " intruder " + brain.army.lastIntruder);
            }
        });
        helper.runAfterDelay(12, () -> {
            int under = 0;
            String where = "";
            for (LivingEntity le : army)
                if (le instanceof com.solegendary.reignofnether.unit.interfaces.AttackerUnit au) {
                    BlockPos t = au.getAttackMoveTarget();
                    double dx = t == null ? 99 : t.getX() - ghast.getX(), dz = t == null ? 99 : t.getZ() - ghast.getZ();
                    if (t != null && dx * dx + dz * dz <= 6 * 6 && t.getY() <= padY + 2)
                        under++;
                    else
                        where = String.valueOf(t);
                }
            int n = army.size();
            cleanup.run();
            if (under != n)
                helper.fail("every fighter should attack-move to the ground under the Ghast (pad y " + padY + ", Ghast at "
                    + ghast.blockPosition().toShortString() + "): " + under + " of " + n + " (one target: " + where + ")");
            else
                helper.succeed();
        });
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

    /** n Shipwrights of one owner in a row from a corner, added to the level. */
    private static List<LivingEntity> shipwrights(ServerLevel level, String owner, BlockPos at, int n) {
        List<LivingEntity> out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            var s = com.solegendary.reignofnether.registrars.EntityRegistrar.SHIPWRIGHT_UNIT.get().create(level);
            if (s == null)
                continue;
            s.moveTo(at.getX() + 0.5 + i, at.getY(), at.getZ() + 0.5, 0, 0);
            s.setOwnerName(owner);
            level.addFreshEntity(s);
            out.add(s);
        }
        return out;
    }

    /**
     * A Tidewrought bot on plain land - a grass pad in the sky over this test's arena, no water anywhere near, no
     * battlefield, no patches - with three Shipwrights at its seat lays its Wreck Harbour on its first think and the
     * Shipwrights start raising it (paid progress within 20 s). The Oct 13 CI soak had a Tidewrought bot sit for four
     * minutes on three unbuilt sites; this pins down that nothing about the faction's capitol, workers or kit needs
     * water or a battlefield to start. The brain is only thought until its capitol stands: a bot with a capitol goes
     * looking for metal patches, and the only ones in a test world belong to other tests' arenas.
     */
    @GameTest(template = ARENA, timeoutTicks = 700)
    public static void tide_bot_starts_building_on_plain_land(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = "gametest_bot_tide_land_" + helper.absolutePos(BlockPos.ZERO).asLong();
        BlockPos centre = helper.absolutePos(new BlockPos(8, 1, 8));
        final int y = 250;   // its own column: nothing of another test's arena is this high
        for (int dx = -8; dx <= 8; dx++)
            for (int dz = -8; dz <= 8; dz++) {
                level.setBlock(new BlockPos(centre.getX() + dx, y - 1, centre.getZ() + dz), Blocks.DIRT.defaultBlockState(), 3);
                level.setBlock(new BlockPos(centre.getX() + dx, y, centre.getZ() + dz), Blocks.GRASS_BLOCK.defaultBlockState(), 3);
            }
        // the seat is the capitol's origin (its corner): 5 back from the centre, the 11x11 harbour fits the pad
        BlockPos home = new BlockPos(centre.getX() - 5, y, centre.getZ() - 5);
        var pool = new com.solegendary.reignofnether.resources.Resources(owner, 0, 2000, 2000);
        com.solegendary.reignofnether.resources.ResourcesServerEvents.resourcesList.add(pool);
        // the three Shipwrights stand two blocks off the harbour's corner: in build range, outside its footprint
        List<LivingEntity> workers = shipwrights(level, owner, home.offset(-2, 1, -2), 3);
        var brain = new BotPlayer(owner, Factions.TIDEWROUGHT, BotPlayer.Difficulty.MEDIUM, home);
        brain.enemyFilter = o -> false;
        List<String> problems = new ArrayList<>();
        boolean[] done = { false };
        Runnable cleanup = () -> {
            for (var bp : new ArrayList<>(com.solegendary.reignofnether.building.BuildingServerEvents.getBuildings()))
                if (owner.equals(bp.ownerName))
                    com.solegendary.reignofnether.building.BuildingServerEvents.getBuildings().remove(bp);
            workers.forEach(Entity::discard);
            com.solegendary.reignofnether.resources.ResourcesServerEvents.resourcesList.remove(pool);
        };
        if (workers.size() != 3) {
            cleanup.run();
            helper.fail("could not spawn three Shipwrights");
            return;
        }
        java.util.function.Supplier<BuildingPlacement> capitol = () -> {
            for (var bp : com.solegendary.reignofnether.building.BuildingServerEvents.getBuildings())
                if (owner.equals(bp.ownerName) && bp.getBuilding() == Buildings.WRECK_HARBOUR && !bp.isDestroyedServerside)
                    return bp;
            return null;
        };
        // think once a second until the harbour stands (three tries: the spawned workers join the unit list a tick
        // after they are added), then leave the brain alone
        for (int t = 5; t <= 45; t += 20)
            helper.runAfterDelay(t, new Runnable() {
                @Override
                public void run() {
                    if (done[0] || capitol.get() != null)
                        return;
                    try {
                        brain.think(level, level.getGameTime());
                    } catch (Exception e) {
                        done[0] = true;
                        cleanup.run();
                        helper.fail("the Tidewrought brain threw on plain land: " + e);
                    }
                }
            });
        helper.runAfterDelay(60, () -> {
            if (done[0])
                return;
            if (capitol.get() == null) {
                done[0] = true;
                cleanup.run();
                helper.fail("the Tidewrought bot laid no Wreck Harbour on plain land in 60 ticks: placeFailures "
                    + brain.placeFailures + ", blocked " + brain.lastBlockedReason);
            }
        });
        helper.runAfterDelay(460, () -> {
            if (done[0])
                return;
            done[0] = true;
            BuildingPlacement cap = capitol.get();
            if (cap == null)
                problems.add("the Wreck Harbour is gone");
            else if (cap.buildProgress <= 0f && cap.getBuilders().isEmpty())
                problems.add("the Wreck Harbour at " + cap.originPos.toShortString() + " has no paid progress and no builder in range after 400 ticks: "
                    + "blocks " + cap.getBlocksPlaced() + "/" + cap.getBlocksTotal() + ", metal " + pool.getMetal()
                    + ", nearest worker " + (workers.isEmpty() ? "none" : workers.get(0).blockPosition().toShortString()));
            if (brain.siteWatch.cancelled != 0)
                problems.add("the site watch cancelled " + brain.siteWatch.cancelled + " site(s) on plain land");
            cleanup.run();
            if (problems.isEmpty())
                helper.succeed();
            else
                helper.fail(String.join("; ", problems));
        });
    }

    /**
     * BotSiteWatch: a capitol site with a worker free for it and no progress for 45 s is reported, cancelled and the
     * next one wanted a step away from the seat (up to three times); a stuck site with the pools empty is reported as
     * unaffordable and kept; a stuck extractor is cancelled. Placements never put in the world, workers never added
     * to the level, the cancel recorded instead of run.
     */
    @GameTest(template = ARENA)
    public static void bot_site_watch_cancels_a_stuck_capitol_and_relays_it_a_step_away(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = "gametest_bot_sitewatch_" + helper.absolutePos(BlockPos.ZERO).asLong();
        BlockPos home = helper.absolutePos(new BlockPos(2, 2, 2));
        var cap = com.solegendary.reignofnether.building.BuildingUtils.getNewBuildingPlacement(Buildings.WRECK_HARBOUR, level,
            home, Rotation.NONE, owner, false);
        var mex = com.solegendary.reignofnether.building.BuildingUtils.getNewBuildingPlacement(Buildings.METAL_EXTRACTOR_TIDE, level,
            helper.absolutePos(new BlockPos(2, 2, 14)), Rotation.NONE, owner, false);
        var worker = com.solegendary.reignofnether.registrars.EntityRegistrar.SHIPWRIGHT_UNIT.get().create(level);
        if (cap == null || mex == null || worker == null) {
            helper.fail("could not make the placements or the worker");
            return;
        }
        worker.setOwnerName(owner);
        worker.moveTo(home.getX() + 0.5, home.getY() + 1, home.getZ() + 0.5, 0, 0);
        List<LivingEntity> workers = new ArrayList<>(List.of(worker));
        var brain = new BotPlayer(owner, Factions.TIDEWROUGHT, BotPlayer.Difficulty.MEDIUM, home);
        brain.metalFill = 1f;
        brain.energyFill = 1f;
        List<BuildingPlacement> dropped = new ArrayList<>();
        brain.siteWatch.canceller = dropped::add;
        brain.siteWatch.treatEveryWorkerAsIdle();   // the worker is never added to the world: isIdle cannot read its goals
        List<String> problems = new ArrayList<>();
        long t0 = 1000;
        long stuck = com.solegendary.reignofnether.bot.BotSiteWatch.STUCK_TICKS;
        // the capitol: watched from t0, stuck at t0 + 45 s
        if (brain.siteWatch.think(level, t0, List.of(cap), workers) || !dropped.isEmpty())
            problems.add("a fresh site was cancelled at once");
        if (brain.siteWatch.think(level, t0 + stuck - 20, List.of(cap), workers) || !dropped.isEmpty())
            problems.add("a site was cancelled before " + stuck + " ticks without progress");
        if (!brain.siteWatch.think(level, t0 + stuck, List.of(cap), workers) || dropped.size() != 1 || dropped.get(0) != cap)
            problems.add("the stuck capitol was not cancelled at " + stuck + " ticks: dropped " + dropped.size());
        if (brain.siteWatch.capitolRelocations != 1)
            problems.add("capitolRelocations " + brain.siteWatch.capitolRelocations + ", expected 1");
        BlockPos want = brain.siteWatch.capitolWant(home);
        double step = Math.sqrt(want.distSqr(home));
        if (step < 10 || step > 20)
            problems.add("the next capitol is wanted " + step + " blocks from the seat, expected about 14");
        if (!brain.lastBlockedReason.startsWith("wreck_harbour: no build progress"))
            problems.add("lastBlockedReason reads '" + brain.lastBlockedReason + "'");
        // the pools empty: the economy's fault, the site stays
        dropped.clear();
        brain.metalFill = 0f;
        brain.siteWatch.think(level, t0 + 2 * stuck, List.of(mex), workers);
        brain.siteWatch.think(level, t0 + 3 * stuck + 20, List.of(mex), workers);
        if (!dropped.isEmpty())
            problems.add("a stuck site was cancelled with the metal pool empty");
        if (!brain.lastBlockedReason.contains("unaffordable"))
            problems.add("an empty pool should read as unaffordable, got '" + brain.lastBlockedReason + "'");
        // with metal again: the stuck extractor is dropped
        brain.metalFill = 1f;
        brain.siteWatch.think(level, t0 + 4 * stuck, List.of(mex), workers);
        if (brain.siteWatch.think(level, t0 + 5 * stuck + 20, List.of(mex), workers))
            problems.add("cancelling an extractor claimed to have cancelled the capitol");
        if (dropped.size() != 1 || dropped.get(0) != mex)
            problems.add("the stuck extractor was not cancelled: dropped " + dropped.size());
        // no worker at all: nothing is ever stuck (nobody could build it)
        dropped.clear();
        brain.siteWatch.think(level, t0 + 6 * stuck, List.of(cap), List.of());
        brain.siteWatch.think(level, t0 + 8 * stuck, List.of(cap), List.of());
        if (!dropped.isEmpty())
            problems.add("a site was cancelled with no workers alive");
        if (problems.isEmpty())
            helper.succeed();
        else
            helper.fail(String.join("; ", problems));
    }

    /**
     * A bot whose extractor site its workers cannot reach (the site sits on a rise: the Oct 23 CI run 40027854308 hard
     * bot's 2749,70,-456, worker stuck at y64 below it) must not pick that patch again after the watch cancels the site,
     * and must pick the next patch instead. The site is raised 4 blocks over the patch, so its centre is well above the
     * patch it covers; the watch cancels it after 45 s with no progress. Placements never put in the world.
     */
    @GameTest(template = ARENA)
    public static void bot_leaves_the_patch_of_an_extractor_site_its_workers_could_not_reach(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = "gametest_bot_unreach_" + helper.absolutePos(BlockPos.ZERO).asLong();
        BlockPos at = helper.absolutePos(new BlockPos(8, 2, 8));
        BlockPos near = com.solegendary.reignofnether.resources.MetalPatches.stamp(level, at.getX(), at.getZ(), 1f);
        BlockPos far = com.solegendary.reignofnether.resources.MetalPatches.stamp(level, at.getX() + 24, at.getZ(), 1f);
        try {
            if (near == null || far == null) {
                helper.fail("could not stamp the two patches");
                return;
            }
            var brain = new BotPlayer(owner, Factions.VILLAGERS, BotPlayer.Difficulty.HARD, near.offset(-6, 0, 0));
            var site = com.solegendary.reignofnether.building.BuildingUtils.getNewBuildingPlacement(brain.kit.extractor(), level,
                near.offset(-2, 4, -2), Rotation.NONE, owner, false);
            var worker = com.solegendary.reignofnether.registrars.EntityRegistrar.VILLAGER_UNIT.get().create(level);
            if (site == null || worker == null) {
                helper.fail("could not make the site or the worker");
                return;
            }
            worker.setOwnerName(owner);
            worker.moveTo(near.getX() + 0.5, near.getY() + 1, near.getZ() + 3.5, 0, 0);
            List<LivingEntity> workers = new ArrayList<>(List.of(worker));
            brain.metalFill = 1f;
            brain.energyFill = 1f;
            List<BuildingPlacement> dropped = new ArrayList<>();
            brain.siteWatch.canceller = dropped::add;
            brain.siteWatch.treatEveryWorkerAsIdle();
            long t0 = level.getGameTime();
            long stuck = com.solegendary.reignofnether.bot.BotSiteWatch.STUCK_TICKS;
            brain.siteWatch.think(level, t0, List.of(site), workers);
            brain.siteWatch.think(level, t0 + stuck, List.of(site), workers);
            List<String> problems = new ArrayList<>();
            if (dropped.size() != 1)
                problems.add("the unreachable extractor site was not cancelled: dropped " + dropped.size());
            BlockPos next = brain.freePatch(level);
            if (near.equals(next))
                problems.add("the bot would pick the patch of the site it just gave up on again (site centre y "
                    + site.centrePos.getY() + ", patch y " + near.getY() + ")");
            else if (!far.equals(next))
                problems.add("the next patch should be the other one at " + far.toShortString() + ", got "
                    + (next == null ? "none" : next.toShortString()));
            worker.discard();
            if (problems.isEmpty())
                helper.succeed();
            else
                helper.fail(String.join("; ", problems));
        } finally {
            var list = com.solegendary.reignofnether.resources.MetalPatches.getPatches(level);
            list.remove(near);
            list.remove(far);
        }
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
