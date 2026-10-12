package com.solegendary.reignofnether.building;

import com.solegendary.reignofnether.building.buildings.shared.WindGenerator;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.Map;

/**
 * Moving parts of the capitols that have one (design/biome_bases.md, the RoN capitol detail pass), declared the way
 * the energy buildings declare theirs (WindGenerator.MOTIONS) so WindmillRenderClientEvents draws them with the same
 * code: client-side boxes, built buildings only, never in strategic zoom, culled by distance, nothing simulated.
 * Positions are in the structure's own (unrotated) coordinates; tools/gen_capitol_details.py keeps each anchor clear
 * and the GameTest capitol_moving_parts_sit_on_their_anchors checks it on the shipped structure.
 */
public final class CapitolLife {
    private CapitolLife() { }

    /** Structure name -> its moving part. The Mausoleum's soul belfry swings a bone bell under its ceiling. */
    public static final Map<String, WindGenerator.Motion> MOTIONS = Map.of(
        "mausoleum", new WindGenerator.Motion(WindGenerator.MotionKind.BELL, new BlockPos(4, 8, 4), Direction.NORTH, 0.4f));

    /** The moving part of the structure this placement wears, or null. */
    public static WindGenerator.Motion motionOf(BuildingPlacement placement) {
        String s = placement.currentStructureName;
        return s == null ? null : MOTIONS.get(s);
    }
}
