package com.solegendary.reignofnether.building.buildings.defence;

import static com.solegendary.reignofnether.building.BuildingUtils.getAbsoluteBlockData;

import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.building.BuildingBlock;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.unit.interfaces.Unit;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * A gate in a wall line (design/defences.md): a 2x2 wall segment whose middle course is fence gates under a lintel.
 * <b>Open for its own side, shut for the enemy</b>: the gates stand open (an open fence gate has no collision and the
 * pathfinder walks through it - WalkabilityBuilder.isFenceLike) so the owner's and allies' units path through as if
 * there were no wall, and they swing shut whenever an enemy unit is within {@link GatePlacement#ENEMY_RADIUS} blocks -
 * a closed gate is a fence to the pathfinder, so the enemy has to break it or go round. Shut it stays while any enemy
 * is near, even with friends beside it: holding a gate against a push matters more than letting a straggler in.
 * <p>
 * The swing is a real block-state change, so the walkability grid patches the chunk (LevelChunkMixin) like any other
 * edit, and BuildingBlock.isPlaced treats an open and a shut gate as the same block (no "damage" from swinging).
 * Cost at 8v8: one grid query per gate every {@link GatePlacement#SCAN_TICKS} ticks, block updates only on a change.
 */
public class WallGate extends DefenceBuilding {

    public WallGate(String id, String displayName, DefenceStyle style) {
        super(id, displayName, style, DefenceCosts.GATE);
        this.portraitBlock = style.gatePortrait();
        this.maxHealth = 320d;
        this.buildTimeModifier = 0.7f;
        this.drawAggro = false;
    }

    @Override
    protected Object[] tooltipArgs() {
        return new Object[] { GatePlacement.ENEMY_RADIUS };
    }

    @Override
    public BuildingPlacement createBuildingPlacement(Level level, BlockPos pos, Rotation rotation, String ownerName) {
        return new GatePlacement(this, level, pos, rotation, ownerName,
            getAbsoluteBlockData(getRelativeBlockData(level), level, pos, rotation), false);
    }

    public static class GatePlacement extends WallSegment.WallPlacement {
        public static final int ENEMY_RADIUS = 6;
        public static final int SCAN_TICKS = 5;

        /** What the gate is showing (true = open). The structure ships its gates shut; the first check opens them. */
        public boolean open = false;
        /** The enemy owner a game test limits the check to (null in play: game tests share one world). */
        @Nullable public String onlyFoe = null;

        private final List<LivingEntity> scratch = new ArrayList<>();

        public GatePlacement(WallGate building, Level level, BlockPos originPos, Rotation rotation, String ownerName,
                             ArrayList<BuildingBlock> blocks, boolean isCapitol) {
            super(building, level, originPos, rotation, ownerName, blocks, isCapitol);
        }

        @Override
        public void tick(Level tickLevel) {
            super.tick(tickLevel);
            if (tickLevel instanceof ServerLevel sl && isBuilt && !isDestroyedServerside
                    && (tickAge + originPos.hashCode()) % SCAN_TICKS == 0)
                updateGate(sl);
        }

        /** Whether an enemy unit stands within {@link #ENEMY_RADIUS} of the gate's centre. Public for the game test. */
        public boolean enemyNear(ServerLevel sl) {
            if (ownerName == null || ownerName.isBlank())
                return false;
            double cx = centrePos.getX() + 0.5, cz = centrePos.getZ() + 0.5, r2 = ENEMY_RADIUS * ENEMY_RADIUS;
            boolean found = false;
            for (LivingEntity le : com.solegendary.reignofnether.unit.UnitGrid.near(sl, cx, cz, ENEMY_RADIUS, scratch)) {
                if (!le.isAlive() || le.level() != sl || !(le instanceof Unit u))
                    continue;
                String o = u.getOwnerName();
                if (o == null || o.isBlank() || ownerName.equals(o) || AlliancesServerEvents.isAllied(ownerName, o)
                        || (onlyFoe != null && !onlyFoe.equals(o)))
                    continue;
                double dx = le.getX() - cx, dz = le.getZ() - cz;
                if (dx * dx + dz * dz <= r2 && Math.abs(le.getY() - centrePos.getY()) <= ENEMY_RADIUS) {
                    found = true;
                    break;
                }
            }
            scratch.clear();
            return found;
        }

        /** One check: shut while an enemy is near, open otherwise. Returns whether the gate is now open. */
        public boolean updateGate(ServerLevel sl) {
            setOpen(sl, !enemyNear(sl));
            return open;
        }

        /** Swings every gate block of this placement (no-op when it already shows that state). */
        public void setOpen(ServerLevel sl, boolean wantOpen) {
            open = wantOpen;
            // compared block by block rather than trusting the flag: a reloaded gate's flag starts shut whatever its
            // blocks show, and a repaired gate block comes back from the structure shut
            boolean changed = false;
            for (BuildingBlock b : blocks) {
                BlockPos bp = b.getBlockPos();
                BlockState bs = sl.getBlockState(bp);
                if (bs.getBlock() instanceof FenceGateBlock && bs.getValue(FenceGateBlock.OPEN) != wantOpen) {
                    sl.setBlock(bp, bs.setValue(FenceGateBlock.OPEN, wantOpen), 2 | 16);
                    changed = true;
                }
            }
            if (changed)   // a heavy creak and chain, then the latch (design/audio.md)
                (wantOpen ? com.solegendary.reignofnether.sounds.Sfx.GATE_OPEN : com.solegendary.reignofnether.sounds.Sfx.GATE_CLOSE).play(sl, centrePos);
        }
    }
}
