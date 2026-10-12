package com.solegendary.reignofnether.building.gameender;

import com.solegendary.reignofnether.building.Building;
import com.solegendary.reignofnether.building.BuildingBlock;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.production.ProductionItems;
import com.solegendary.reignofnether.research.ResearchServerEvents;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;

import java.util.ArrayList;

/**
 * The Ward Spire's interception stockpile (WardSpire; design/big_units.md 2.1). Server side only: once built, and while
 * its owner has Tier 2, it charges one interception over {@link #CHARGE_TICKS}; it holds at most {@link #MAX_CHARGES}.
 * A game ender's strike aimed within {@link #RANGE} blocks of a charged spire of the victim's side is spent against it
 * ({@link GameEnderPlacement#resolveStrike}). Like BAR's anti-nuke, one charge stops one strike: the threat comes back
 * when the game ender recharges, and a ward that has just fired is empty for {@link #CHARGE_TICKS}.
 * <p>
 * Not saved (like the Rail Battery's cooldown): a reloaded spire charges again from zero.
 */
public class WardSpirePlacement extends BuildingPlacement {

    public static final int RANGE = 40;
    public static final int CHARGE_TICKS = 90 * 20;
    public static final int MAX_CHARGES = 1;

    public int charges = 0;
    public int chargeProgress = 0;

    public WardSpirePlacement(Building building, Level level, BlockPos originPos, Rotation rotation, String ownerName,
                              ArrayList<BuildingBlock> blocks, boolean isCapitol) {
        super(building, level, originPos, rotation, ownerName, blocks, isCapitol);
    }

    @Override
    public void tick(Level tickLevel) {
        super.tick(tickLevel);
        if (tickLevel instanceof ServerLevel sl)
            tickWard(sl);
    }

    /** One server tick of the stockpile. Public for the game test. */
    public void tickWard(ServerLevel sl) {
        if (!isBuilt || isDestroyedServerside || ownerName == null || ownerName.isBlank())
            return;
        if (charges >= MAX_CHARGES) {
            // charged: a slow shimmer at the tip so both sides can see it is armed
            if (tickAge % 30 == 0)
                sl.sendParticles(ParticleTypes.END_ROD, centrePos.getX() + 0.5, maxCorner.getY() + 1.2, centrePos.getZ() + 0.5, 2, 0.2, 0.3, 0.2, 0.01);
            return;
        }
        if (!ResearchServerEvents.playerHasResearch(ownerName, ProductionItems.RESEARCH_TIER_2))
            return;
        if (++chargeProgress >= CHARGE_TICKS) {
            chargeProgress = 0;
            charges++;
            sl.playSound(null, centrePos, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.NEUTRAL, 1.5f, 1f);
        }
    }

    public boolean canIntercept() {
        return isBuilt && !isDestroyedServerside && charges > 0;
    }

    /** Spends one interception against a strike at {@code at}: a beam from the spire to the spot. */
    public void spendCharge(ServerLevel sl, BlockPos at) {
        charges = Math.max(0, charges - 1);
        chargeProgress = 0;
        double x0 = centrePos.getX() + 0.5, y0 = maxCorner.getY() + 1.0, z0 = centrePos.getZ() + 0.5;
        double dx = at.getX() + 0.5 - x0, dy = at.getY() + 8 - y0, dz = at.getZ() + 0.5 - z0;
        int steps = (int) Math.min(80, Math.sqrt(dx * dx + dy * dy + dz * dz));
        for (int i = 0; i <= steps; i++) {
            double t = steps == 0 ? 0 : (double) i / steps;
            sl.sendParticles(ParticleTypes.END_ROD, x0 + dx * t, y0 + dy * t, z0 + dz * t, 1, 0, 0, 0, 0);
        }
        sl.sendParticles(ParticleTypes.FLASH, at.getX() + 0.5, at.getY() + 8, at.getZ() + 0.5, 1, 0, 0, 0, 0);
        sl.playSound(null, centrePos, SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.NEUTRAL, 2f, 0.5f);
    }
}
