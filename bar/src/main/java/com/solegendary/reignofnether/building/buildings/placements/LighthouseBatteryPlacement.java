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
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * The Lighthouse Battery's lamp and gun (building/buildings/tide/LighthouseBattery.java). Server side only, and only
 * once built while its owner has Tier 2 (the Storm Oak's dormancy rule):
 * <ul>
 *   <li><b>Beam</b>: the lamp turns a full circle every {@link #BEAM_PERIOD_TICKS} ticks. Every {@link #PASS_TICKS}
 *   ticks every concealed enemy unit - in a Verdant thicket, a Fog Bank, a Shade Ranger's cloak
 *   ({@link Concealment#isConcealed}) - inside the {@link #BEAM_HALF_ANGLE} degree cone out to {@link #BEAM_RANGE}
 *   blocks is stripped of its cover for {@link #REVEAL_TICKS} ticks ({@link Concealment#revealFor}, the Holy Bell's
 *   hook). The beam moves 30 degrees a pass and is 60 wide, so it sweeps with no gaps.</li>
 *   <li><b>Gun</b>: a light swivel shot ({@link #DAMAGE}) at the nearest enemy unit it can see within
 *   {@link #ATTACK_RANGE} every {@link #ATTACK_COOLDOWN_TICKS} ticks - a picket, not a fortress (the Storm Oak hits four
 *   times as hard). Hidden units are never shot until the beam finds them.</li>
 *   <li><b>Sight</b>: {@link #SIGHT_RANGE} blocks once built (a capitol sees 32), so the tower is also the faction's
 *   watch post.</li>
 * </ul>
 * Cost at 8v8: one grid query per pass (twice a second) and one per shot, no allocation (reused lists), a handful of
 * particle packets per pass for the visible beam.
 */
public class LighthouseBatteryPlacement extends BuildingPlacement {
    public static final int BEAM_RANGE = 30;
    public static final float BEAM_HALF_ANGLE = 30f;
    public static final int BEAM_PERIOD_TICKS = 120;   // 6 s a turn: 3 degrees a tick, 30 a pass
    public static final int PASS_TICKS = 10;
    public static final int REVEAL_TICKS = 60;         // lit for 3 s: long enough to be shot at, short of a Holy Bell
    public static final int ATTACK_RANGE = 20;
    public static final float DAMAGE = 6f;
    public static final int ATTACK_COOLDOWN_TICKS = 40;  // 6 dmg / 2 s
    public static final int SIGHT_RANGE = 40;

    /** Ticks until the gun may fire again (0 = ready). Not saved: a reloaded tower is ready at once. */
    public int cooldown = 0;

    private final List<LivingEntity> scratch = new ArrayList<>();   // grid query results, reused

    public LighthouseBatteryPlacement(Building building, Level level, BlockPos originPos, Rotation rotation, String ownerName,
                                      ArrayList<BuildingBlock> blocks, boolean isCapitol) {
        super(building, level, originPos, rotation, ownerName, blocks, isCapitol);
    }

    @Override
    public int getSightRange() {
        return isBuilt ? SIGHT_RANGE : super.getSightRange();
    }

    @Override
    public void tick(Level tickLevel) {
        super.tick(tickLevel);
        if (tickLevel instanceof ServerLevel sl)
            tickLamp(sl);
    }

    /** The beam's heading now, in degrees (0 = +x, 90 = +z), from the placement's age. */
    public float beamAngle() {
        return (float) ((tickAge % BEAM_PERIOD_TICKS) * (360.0 / BEAM_PERIOD_TICKS));
    }

    /** The heading from the lamp to a point, in the same degrees as {@link #beamAngle}. */
    public float angleTo(double x, double z) {
        return (float) Math.toDegrees(Math.atan2(z - (centrePos.getZ() + 0.5), x - (centrePos.getX() + 0.5)));
    }

    boolean active() {
        return isBuilt && !isDestroyedServerside && ownerName != null && !ownerName.isBlank()
            && ResearchServerEvents.playerHasResearch(ownerName, ProductionItems.RESEARCH_TIER_2);
    }

    /** One server tick: the gun's cooldown, a beam pass every PASS_TICKS, a shot when ready. Public for the game test. */
    public void tickLamp(ServerLevel sl) {
        if (cooldown > 0)
            cooldown--;
        if (tickAge % PASS_TICKS != 0 || !active())
            return;
        revealSweep(sl, beamAngle(), null);
        drawBeam(sl, beamAngle());
        if (cooldown <= 0)
            shoot(sl, null);
    }

    boolean isFoe(LivingEntity le, @Nullable String onlyFoe) {
        if (!le.isAlive() || le.level() != level || !(le instanceof Unit u))
            return false;
        String o = u.getOwnerName();
        return o != null && !o.isBlank() && !ownerName.equals(o) && !AlliancesServerEvents.isAllied(ownerName, o)
            && (onlyFoe == null || onlyFoe.equals(o));
    }

    /**
     * Strips cover from every concealed enemy unit in the beam cone heading {@code angleDeg}. Returns how many it lit.
     * {@code onlyFoe} (null in play) limits it to one owner so parallel game tests never touch each other's units.
     */
    public int revealSweep(ServerLevel sl, float angleDeg, @Nullable String onlyFoe) {
        if (!active())
            return 0;
        double cx = centrePos.getX() + 0.5, cz = centrePos.getZ() + 0.5;
        double r2 = (double) BEAM_RANGE * BEAM_RANGE;
        int lit = 0;
        for (LivingEntity le : UnitGrid.near(sl, cx, cz, BEAM_RANGE, scratch)) {
            if (!isFoe(le, onlyFoe) || !Concealment.isConcealed(le))
                continue;
            double dx = le.getX() - cx, dz = le.getZ() - cz;
            if (dx * dx + dz * dz > r2)
                continue;
            float off = Mth.degreesDifferenceAbs(angleDeg, angleTo(le.getX(), le.getZ()));
            // a unit right at the foot of the tower is in the lamp's glow whatever the heading
            if (off > BEAM_HALF_ANGLE && dx * dx + dz * dz > 9)
                continue;
            Concealment.revealFor(le, REVEAL_TICKS);
            sl.sendParticles(ParticleTypes.END_ROD, le.getX(), le.getY() + le.getBbHeight() + 0.3, le.getZ(), 3, 0.2, 0.2, 0.2, 0.01);
            lit++;
        }
        return lit;
    }

    /**
     * Fires the swivel gun at the nearest enemy unit in range that the owner can see (never a hidden one). Returns the
     * unit hit, or null (on cooldown, dormant, or nothing in range).
     */
    @Nullable
    public LivingEntity shoot(ServerLevel sl, @Nullable String onlyFoe) {
        if (cooldown > 0 || !active())
            return null;
        double cx = centrePos.getX() + 0.5, cz = centrePos.getZ() + 0.5, cy = centrePos.getY();
        double best = (double) ATTACK_RANGE * ATTACK_RANGE;
        LivingEntity target = null;
        for (LivingEntity le : UnitGrid.near(sl, cx, cz, ATTACK_RANGE, scratch)) {
            if (!isFoe(le, onlyFoe) || le.isInvisible() || Concealment.isHiddenFrom(le, ownerName)
                    || Math.abs(le.getY() - cy) > ATTACK_RANGE)
                continue;
            double dx = le.getX() - cx, dz = le.getZ() - cz, d = dx * dx + dz * dz;
            if (d < best) {
                best = d;
                target = le;
            }
        }
        if (target == null)
            return null;
        cooldown = ATTACK_COOLDOWN_TICKS;
        // indirect magic with no source entity, as the Storm Oak: a building has none, and RoN rewrites mob-attack damage
        target.hurt(sl.damageSources().indirectMagic(null, null), DAMAGE);
        double x0 = cx, y0 = maxCorner.getY() - 1.0, z0 = cz;
        double x1 = target.getX(), y1 = target.getY() + target.getBbHeight() * 0.6, z1 = target.getZ();
        for (int i = 1; i <= 6; i++) {
            double t = i / 7.0;
            sl.sendParticles(ParticleTypes.CRIT, x0 + (x1 - x0) * t, y0 + (y1 - y0) * t, z0 + (z1 - z0) * t, 1, 0, 0, 0, 0);
        }
        sl.sendParticles(ParticleTypes.SMOKE, x0, y0, z0, 3, 0.2, 0.1, 0.2, 0.01);
        sl.playSound(null, BlockPos.containing(x0, y0, z0), SoundEvents.GENERIC_EXPLODE, SoundSource.NEUTRAL, 0.5f, 1.8f);
        return target;
    }

    /** The visible beam: a few glints along the heading from the lamp, fading out toward its reach. */
    void drawBeam(ServerLevel sl, float angleDeg) {
        double a = Math.toRadians(angleDeg);
        double ux = Math.cos(a), uz = Math.sin(a);
        double x0 = centrePos.getX() + 0.5, y0 = maxCorner.getY() - 0.5, z0 = centrePos.getZ() + 0.5;
        for (int i = 1; i <= 6; i++) {
            double d = i * (BEAM_RANGE / 6.0);
            sl.sendParticles(ParticleTypes.END_ROD, x0 + ux * d, y0 - d * 0.12, z0 + uz * d, 1, 0.1, 0.05, 0.1, 0.0);
        }
    }
}
