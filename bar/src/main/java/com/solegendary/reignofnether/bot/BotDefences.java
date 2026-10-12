package com.solegendary.reignofnether.bot;

import com.solegendary.reignofnether.building.Building;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.BuildingServerEvents;
import com.solegendary.reignofnether.building.BuildingValidators;
import com.solegendary.reignofnether.building.buildings.defence.Defences;
import com.solegendary.reignofnether.faction.Faction;
import com.solegendary.reignofnether.util.MiscUtil;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Rotation;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * The bots' static-defence rules (design/defences.md), kept out of BotPlayer so the bot's own file stays about economy
 * and army: BotPlayer.think calls {@link #think} once per think, after its own build rules, and it places at most one
 * thing. Two rules, both BAR habits:
 * <ol>
 *   <li><b>A tower at every expansion</b>: an own extractor more than {@link #EXPANSION_MIN_DIST} blocks from home with
 *       no own anti-raid tower within {@link #COVER_RADIUS} gets one beside it (raiders hunt outlying extractors);</li>
 *   <li><b>A wall across the approach</b>: from minute {@link #WALL_MINUTE}, once, a line of {@link #WALL_SEGMENTS}
 *       wall segments across the line from home to the nearest enemy, {@link #WALL_DIST} blocks out, with a gate in the
 *       middle so the bot's own army still walks straight out. A real choke finder can replace {@link #wallLine} later.</li>
 * </ol>
 * The helpers are public and static so other bot code (bots-maps) can call them on their own.
 */
public final class BotDefences {
    private BotDefences() { }

    public static final int EXPANSION_MIN_DIST = 28;
    public static final int COVER_RADIUS = 12;
    public static final int WALL_MINUTE = 6;
    public static final int WALL_DIST = 22;
    public static final int WALL_SEGMENTS = 7;   // odd: the gate takes the middle cell
    public static final int MAX_RAID_TOWERS = 6;

    @Nullable
    public static Building raidTowerFor(Faction faction) {
        Defences.Set set = Defences.of(faction);
        return set == null ? null : set.raidTower();
    }

    @Nullable
    public static Building wallFor(Faction faction) {
        Defences.Set set = Defences.of(faction);
        return set == null ? null : set.wall();
    }

    @Nullable
    public static Building gateFor(Faction faction) {
        Defences.Set set = Defences.of(faction);
        return set == null ? null : set.gate();
    }

    @Nullable
    public static Building antiT3For(Faction faction) {
        Defences.Set set = Defences.of(faction);
        return set == null ? null : set.antiT3();
    }

    /**
     * An own extractor at least {@link #EXPANSION_MIN_DIST} from home with no own {@code tower} within
     * {@link #COVER_RADIUS}: where the next expansion tower goes. Null when every expansion is covered.
     */
    @Nullable
    public static BlockPos expansionWithoutTower(BlockPos home, List<BuildingPlacement> own, Building tower) {
        BlockPos best = null;
        double bestD = Double.MAX_VALUE, min2 = (double) EXPANSION_MIN_DIST * EXPANSION_MIN_DIST, cover2 = (double) COVER_RADIUS * COVER_RADIUS;
        for (BuildingPlacement ex : own) {
            if (!(ex.getBuilding() instanceof com.solegendary.reignofnether.building.buildings.shared.MetalExtractor) || !ex.isBuilt)
                continue;
            double d = ex.centrePos.distSqr(home);
            if (d < min2 || d >= bestD)
                continue;
            boolean covered = false;
            for (BuildingPlacement t : own)
                if (t.getBuilding() == tower && t.centrePos.distSqr(ex.centrePos) <= cover2) {
                    covered = true;
                    break;
                }
            if (!covered) {
                best = ex.centrePos;
                bestD = d;
            }
        }
        return best;
    }

    /**
     * The cell centres of a wall line across the approach: {@code segments} cells of {@code cell} blocks, centred
     * {@link #WALL_DIST} blocks from home toward {@code threat}, perpendicular to that direction, walked along the
     * dominant perpendicular axis so the segments touch edge to edge. Index segments/2 is the gate's cell.
     */
    public static List<BlockPos> wallLine(BlockPos home, BlockPos threat, int segments, int cell) {
        List<BlockPos> out = new ArrayList<>();
        double dx = threat.getX() - home.getX(), dz = threat.getZ() - home.getZ();
        double len = Math.max(1, Math.hypot(dx, dz));
        double ux = dx / len, uz = dz / len;
        int cx = home.getX() + (int) Math.round(ux * WALL_DIST), cz = home.getZ() + (int) Math.round(uz * WALL_DIST);
        // the line runs across the threat direction; step along whichever axis is more across it
        boolean alongX = Math.abs(uz) >= Math.abs(ux);
        for (int i = 0; i < segments; i++) {
            int off = (i - segments / 2) * cell;
            out.add(alongX ? new BlockPos(cx + off, home.getY(), cz) : new BlockPos(cx, home.getY(), cz + off));
        }
        return out;
    }

    /** Places {@code building} with its centre on {@code centre} if the spot is valid; the nearest worker builds it. */
    public static boolean placeAt(ServerLevel level, String owner, Building building, BlockPos centre, List<LivingEntity> workers) {
        if (building == null || workers.isEmpty())
            return false;
        level.getChunk(centre.getX() >> 4, centre.getZ() >> 4);
        BlockPos surface = MiscUtil.getHighestNonAirBlock(level, new BlockPos(centre.getX(), 0, centre.getZ()));
        // a 2x2 segment's origin is its min corner (getBuildingOriginPos with Rotation.NONE: centre - size/2)
        BlockPos origin = surface.offset(-1, 0, -1);
        if (!BuildingValidators.isPlacementValid(level, building, origin, owner, Rotation.NONE, false, false, true))
            return false;
        LivingEntity nearest = workers.get(0);
        for (LivingEntity w : workers)
            if (w.distanceToSqr(centre.getX(), centre.getY(), centre.getZ()) < nearest.distanceToSqr(centre.getX(), centre.getY(), centre.getZ()))
                nearest = w;
        return BuildingServerEvents.placeBuilding(building, origin, Rotation.NONE, owner, new int[] { nearest.getId() },
            false, false, false, true) != null;
    }

    /**
     * One defence decision for a bot: an expansion tower if one is missing, else (once, from minute WALL_MINUTE) the
     * wall line with its gate. Returns true if it placed something.
     */
    public static boolean think(BotPlayer bot, ServerLevel level, List<BuildingPlacement> own, List<LivingEntity> workers,
                                BlockPos home, long minutes) {
        if (workers.isEmpty() || home == null)
            return false;
        Building tower = raidTowerFor(bot.faction);
        if (tower != null && minutes >= 3 && count(own, tower) < MAX_RAID_TOWERS) {
            BlockPos ex = expansionWithoutTower(home, own, tower);
            if (ex != null && bot.placeNear(level, tower, ex.offset(4, 0, 4), workers, 1))
                return true;
        }
        Building wall = wallFor(bot.faction), gate = gateFor(bot.faction);
        if (wall == null || gate == null || minutes < WALL_MINUTE || count(own, gate) > 0)
            return false;
        BlockPos threat = bot.nearestEnemyBuilding(home);
        if (threat == null)
            return false;
        List<BlockPos> cells = wallLine(home, threat, WALL_SEGMENTS, 2);
        // the gate first (it is what counts as "walled"), then as many segments as fit; one batch, one think
        boolean any = placeAt(level, bot.name, gate, cells.get(WALL_SEGMENTS / 2), workers);
        if (!any)
            return false;
        for (int i = 0; i < cells.size(); i++)
            if (i != WALL_SEGMENTS / 2)
                placeAt(level, bot.name, wall, cells.get(i), workers);
        return true;
    }

    static int count(List<BuildingPlacement> own, Building b) {
        int n = 0;
        for (BuildingPlacement bp : own)
            if (bp.getBuilding() == b)
                n++;
        return n;
    }
}
