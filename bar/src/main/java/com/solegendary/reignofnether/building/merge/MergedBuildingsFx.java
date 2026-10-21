package com.solegendary.reignofnether.building.merge;

import com.solegendary.reignofnether.barfx.BarFx;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.faction.FactionTraits;
import com.solegendary.reignofnether.sounds.Sfx;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * The placeholder for the raising moment (claude/merged-buildings-animation.md 1) until Lane B's animations land on
 * Life.java: when a complex forms, grows or closes ranks after a loss, every section sends the game's link beam (the
 * nanolathe / ley-link look, in the faction's tint) to the complex's centre for the raising window, and the faction's
 * completion chime plays once at the centre. Server side: one BarFx event per section, the clients draw and fade them;
 * nothing here decides anything (MergedBuildings holds the state). Replace, do not extend.
 */
public final class MergedBuildingsFx {
    private MergedBuildingsFx() { }

    public static void onChanges(ServerLevel level, List<MergedBuildings.Change> changes) {
        if (level == null)
            return;
        for (MergedBuildings.Change ch : changes) {
            MergedComplex c = ch.complex();
            if (c == null)
                continue;
            boolean closingRanks = ch.kind() == MergedBuildings.ChangeKind.SHRUNK;
            if (closingRanks && ch.destroyed() <= 0)
                continue;   // a re-cut or a switch-off: nothing to see
            FactionTraits traits = FactionTraits.of(c.building.getFaction());
            // the beams meet over the middle of the footprint: a Quarter's centre spire, the junction of a Farm's four
            Vec3 centre = new Vec3((c.minCorner.getX() + c.maxCorner.getX()) / 2.0 + 0.5, c.maxCorner.getY() + 2.5,
                (c.minCorner.getZ() + c.maxCorner.getZ()) / 2.0 + 0.5);
            int life = Math.min(100, closingRanks ? MergedBuildings.RAISING_TICKS / 2 : MergedBuildings.RAISING_TICKS);
            for (BuildingPlacement m : c.members) {
                Vec3 from = top(m);
                if (from.distanceToSqr(centre) < 0.25)
                    continue;
                BarFx.beam(level, from, centre, traits.nanoTint, life);
            }
            if (!closingRanks) {
                // the faction's completion motif again, a step lower the bigger the shape (the last member's own
                // chime just played at full pitch, so the two read as one rising chord, not a doubled note)
                Sfx chime = Sfx.built(traits.deathDebris);
                if (chime != null)
                    chime.play(level, centre.x, centre.y, centre.z, 1f, pitchFor(c.shape));
            }
        }
    }

    /** Pair 0.95, Farm 0.9, Row 0.85, Quarter 0.8 ... down to 0.7. */
    static float pitchFor(MergedShape shape) {
        return Math.max(0.7f, 1f - 0.05f * (shape.ordinal() + 1));
    }

    /** Just above the middle of a section's top (its spire or cap). */
    private static Vec3 top(BuildingPlacement m) {
        return new Vec3((m.minCorner.getX() + m.maxCorner.getX()) / 2.0 + 0.5, m.maxCorner.getY() + 1.5,
            (m.minCorner.getZ() + m.maxCorner.getZ()) / 2.0 + 0.5);
    }
}
