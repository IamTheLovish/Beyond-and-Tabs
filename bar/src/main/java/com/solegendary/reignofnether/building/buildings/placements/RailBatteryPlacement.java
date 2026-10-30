package com.solegendary.reignofnether.building.buildings.placements;

import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.building.Building;
import com.solegendary.reignofnether.building.BuildingBlock;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.production.ProductionItems;
import com.solegendary.reignofnether.research.ResearchServerEvents;
import com.solegendary.reignofnether.unit.Concealment;
import com.solegendary.reignofnether.unit.UnitGrid;
import com.solegendary.reignofnether.unit.interfaces.Unit;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * The Rail Battery's shot (building/buildings/brass/RailBattery.java; design/brassworks_plan.md slice 3). Server side
 * only: once built (and while its owner has Tier 2) the battery looks for the nearest enemy unit within {@link #RANGE}
 * every {@link #SCAN_TICKS} ticks; when it finds one it <b>locks the line</b> - from the barrel through the target's
 * spot out to {@link #RANGE} - and <b>charges</b> for {@link #CHARGE_TICKS}, drawing the line on the ground every few
 * ticks (the telegraph: anyone standing on it can step off). Then the rail fires along the locked line: every enemy
 * unit within {@link #LINE_WIDTH} of it takes {@link #DAMAGE}, friends and allies never. Then {@link #COOLDOWN_TICKS}.
 * <p>
 * A building has no entity to own the hit, so the damage is generic (armour soaks it, it is not magic). Cost at 8v8:
 * one grid query per battery twice a second while idle, a handful of particle packets every 5 ticks while charging,
 * one query along the line per shot (every ~6.5 s).
 */
public class RailBatteryPlacement extends BuildingPlacement {
    public static final int RANGE = 30;
    public static final float DAMAGE = 30f;
    public static final int CHARGE_TICKS = 30;      // 1.5 s of telegraph
    public static final int COOLDOWN_TICKS = 100;   // 5 s between shots once one has fired
    public static final int SCAN_TICKS = 10;
    public static final double LINE_WIDTH = 1.0;

    /** Ticks until the next charge may start (0 = ready). Not saved: a reloaded battery is ready at once. */
    public int cooldown = 0;
    /** Ticks left on the charge (0 = not charging). */
    public int charge = 0;
    /** The locked line's ends while charging (barrel -> far end). */
    @Nullable public Vec3 lineFrom, lineTo;
    /** The enemy owner a game test limits targets to (null in play: game tests share one world). */
    @Nullable public String onlyFoe = null;
    /** The last look found a target: the battery looks again the tick it is ready, not at the next SCAN_TICKS. */
    private boolean engaged = false;

    private final List<LivingEntity> scratch = new ArrayList<>();

    public RailBatteryPlacement(Building building, Level level, BlockPos originPos, Rotation rotation, String ownerName,
                                ArrayList<BuildingBlock> blocks, boolean isCapitol) {
        super(building, level, originPos, rotation, ownerName, blocks, isCapitol);
    }

    @Override
    public void tick(Level tickLevel) {
        super.tick(tickLevel);
        if (tickLevel instanceof ServerLevel sl)
            tickRail(sl);
    }

    /**
     * One server tick of the weapon: cooldown, then charge, then fire - a new charge may start on the tick the cooldown
     * reaches 0, so a battery in a fight fires every CHARGE_TICKS + COOLDOWN_TICKS exactly; an idle one looks only
     * every SCAN_TICKS. It used to skip that tick and wait for the next SCAN_TICKS: 140 ticks a shot, not 130
     * (DefenceTower's tickWeapon had the same). Public for the game test.
     */
    public void tickRail(ServerLevel sl) {
        if (!isBuilt || isDestroyedServerside || ownerName == null || ownerName.isBlank())
            return;
        if (charge > 0) {
            charge--;
            if (charge % 5 == 0 && charge > 0)
                drawTelegraph(sl);
            if (charge == 0)
                fire(sl);
            return;
        }
        if (cooldown > 0 && --cooldown > 0)
            return;
        if (engaged || tickAge % SCAN_TICKS == 0)
            engaged = startCharge(sl);
    }

    /** The barrel: the top of the battery's centre column. */
    public Vec3 barrel() {
        return new Vec3(centrePos.getX() + 0.5, maxCorner.getY() + 0.5, centrePos.getZ() + 0.5);
    }

    /**
     * Locks a line at the nearest enemy in range and starts charging. False when there is nobody to shoot, or the
     * owner lacks Tier 2 (dormant: the build button is the client gate, this the server's). Public for the game test.
     */
    public boolean startCharge(ServerLevel sl) {
        if (charge > 0 || cooldown > 0 || !ResearchServerEvents.playerHasResearch(ownerName, ProductionItems.RESEARCH_TIER_2))
            return false;
        LivingEntity target = pickTarget(sl);
        if (target == null)
            return false;
        Vec3 from = barrel();
        Vec3 flat = new Vec3(target.getX() - from.x, 0, target.getZ() - from.z);
        if (flat.lengthSqr() < 0.01)
            return false;
        Vec3 dir = flat.normalize();
        lineFrom = new Vec3(from.x, target.getY() + 0.5, from.z);
        lineTo = lineFrom.add(dir.scale(RANGE));
        charge = CHARGE_TICKS;
        drawTelegraph(sl);
        sl.playSound(null, BlockPos.containing(from), SoundEvents.BEACON_ACTIVATE, SoundSource.NEUTRAL, 0.8f, 1.8f);
        return true;
    }

    /** The nearest visible enemy unit within {@link #RANGE} of the battery, or null. */
    @Nullable
    public LivingEntity pickTarget(ServerLevel sl) {
        double cx = centrePos.getX() + 0.5, cz = centrePos.getZ() + 0.5;
        LivingEntity best = null;
        double bestD = (double) RANGE * RANGE;
        for (LivingEntity le : UnitGrid.near(sl, cx, cz, RANGE, scratch)) {
            if (!isFoe(le) || Concealment.isHiddenFrom(le, ownerName) || le.isInvisible())
                continue;
            double dx = le.getX() - cx, dz = le.getZ() - cz, d = dx * dx + dz * dz;
            if (d < bestD && Math.abs(le.getY() - centrePos.getY()) <= RANGE) {
                bestD = d;
                best = le;
            }
        }
        scratch.clear();
        return best;
    }

    private boolean isFoe(LivingEntity le) {
        if (!le.isAlive() || le.level() != level || !(le instanceof Unit u))
            return false;
        String o = u.getOwnerName();
        return o != null && !o.isBlank() && !ownerName.equals(o) && !AlliancesServerEvents.isAllied(ownerName, o)
            && (onlyFoe == null || onlyFoe.equals(o));
    }

    /** The telegraph: a dotted line of embers along the locked line, every 1.5 blocks. */
    void drawTelegraph(ServerLevel sl) {
        if (lineFrom == null || lineTo == null)
            return;
        Vec3 d = lineTo.subtract(lineFrom);
        int steps = (int) (d.length() / 1.5);
        for (int i = 1; i <= steps; i++) {
            Vec3 p = lineFrom.add(d.scale((double) i / steps));
            sl.sendParticles(ParticleTypes.SMALL_FLAME, p.x, p.y - 0.3, p.z, 1, 0.05, 0, 0.05, 0);
        }
    }

    /** The rail: every enemy within {@link #LINE_WIDTH} of the locked line is hit. Returns how many. Public for the game test. */
    public int fire(ServerLevel sl) {
        cooldown = COOLDOWN_TICKS;
        if (lineFrom == null || lineTo == null)
            return 0;
        Vec3 a = lineFrom, b = lineTo, ab = b.subtract(a);
        double len2 = ab.lengthSqr();
        Vec3 mid = a.add(ab.scale(0.5));
        int hits = 0;
        for (LivingEntity le : UnitGrid.near(sl, mid.x, mid.z, RANGE * 0.5 + LINE_WIDTH, scratch)) {
            if (!isFoe(le))
                continue;
            Vec3 p = new Vec3(le.getX(), le.getY() + le.getBbHeight() * 0.5, le.getZ());
            double t = Math.max(0, Math.min(1, p.subtract(a).dot(ab) / len2));
            Vec3 c = a.add(ab.scale(t));
            double dx = p.x - c.x, dz = p.z - c.z;
            if (dx * dx + dz * dz > LINE_WIDTH * LINE_WIDTH || Math.abs(p.y - c.y) > 2.5)
                continue;
            le.hurt(sl.damageSources().generic(), DAMAGE);
            hits++;
        }
        scratch.clear();
        // the shot's look: a bright rail of sparks from the barrel along the line, a crack at the muzzle
        Vec3 from = barrel();
        int steps = (int) (Math.sqrt(len2) / 0.8);
        for (int i = 0; i <= steps; i++) {
            Vec3 p = from.lerp(b, (double) i / steps);
            sl.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.x, p.y, p.z, 1, 0.02, 0.02, 0.02, 0);
        }
        sl.sendParticles(ParticleTypes.FLASH, from.x, from.y, from.z, 1, 0, 0, 0, 0);
        sl.playSound(null, BlockPos.containing(from), SoundEvents.TRIDENT_THUNDER, SoundSource.NEUTRAL, 0.7f, 1.6f);
        lineFrom = lineTo = null;
        return hits;
    }
}
