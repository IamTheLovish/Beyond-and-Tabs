package com.solegendary.reignofnether.hero.ai;

import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.building.BuildingGrid;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.buildings.defence.DefenceTower;
import com.solegendary.reignofnether.fogofwar.FogOfWarServerEvents;
import com.solegendary.reignofnether.hero.HeroFx;
import com.solegendary.reignofnether.unit.Concealment;
import com.solegendary.reignofnether.unit.UnitGrid;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.support.SupportOrders;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * What the hero knows, once a second (lovish's hero AI requirement 10, fog-of-war fairness: "only what the owner can
 * see; no peeking at hidden units"). One UnitGrid query round the hero and one BuildingGrid query for the enemy towers
 * whose guns reach near it - the same spatial grids every unit already uses, never a scan of the map.
 * <p>
 * A unit is seen when its side would see it on screen:
 * <ul>
 *   <li>never while it hides from the hero's side ({@link Concealment}: a cloak, a thicket, under ground or water);</li>
 *   <li>with the fog of war off (or not applying to the owner), everything else;</li>
 *   <li>under fog, for an owner who is online: what the fog shows them now (the vision masks the fog tick computed);</li>
 *   <li>under fog with nobody online for the owner (a bot): what the hero and its friends round it see - each one's own
 *       sight range, the circles the fog is built from.</li>
 * </ul>
 * Enemy towers count when their side is seen the same way (their centre column) and their guns shoot ground units
 * ({@link SupportOrders#gunRange}: defence towers that are not anti-air only, the T2 faction towers, a manned garrison).
 */
public final class Perception {
    private Perception() { }

    /** How far round the hero it looks for units (HeroBot's FIGHT_RADIUS in v1). */
    public static final double RADIUS = 16;
    /** Longest gun a building has (the flak's 24, a garrison's bow) plus room: towers this far off may still reach. */
    static final int MAX_GUN_REACH = 32;
    /** A gun building's damage per second when it is not a defence tower (a manned garrison, a T2 tower): a guess. */
    static final float OTHER_GUN_DPS = 10f;

    /** An enemy building that shoots ground units: where, how far, how tough and how hard it hits. */
    public record Tower(double x, double z, int range, float health, float dps) {
        /** Is (px, pz) within the gun's range plus {@code margin}? */
        public boolean reaches(double px, double pz, double margin) {
            double dx = px - x, dz = pz - z, r = range + margin;
            return dx * dx + dz * dz <= r * r;
        }
    }

    /** One scan's result. Each hero's brain keeps one and refills it: the lists are reused, never reallocated. */
    public static final class Snapshot {
        public final List<LivingEntity> enemies = new ArrayList<>();
        /** Own and allied units round the hero, the hero included. */
        public final List<LivingEntity> friends = new ArrayList<>();
        public final List<Tower> towers = new ArrayList<>();
        public long tick = Long.MIN_VALUE;

        /** Would going after {@code target} take a hero standing outside every tower's reach into one? */
        public boolean chaseIntoTower(Entity hero, Entity target, double reach) {
            for (Tower t : towers)
                if (!t.reaches(hero.getX(), hero.getZ(), 0) && t.reaches(target.getX(), target.getZ(), reach))
                    return true;
            return false;
        }

        /** Would a hero standing outside every tower's reach land inside one at (x, z)? */
        public boolean landsUnderTower(Entity hero, double x, double z) {
            for (Tower t : towers)
                if (!t.reaches(hero.getX(), hero.getZ(), 0) && t.reaches(x, z, 1.0))
                    return true;
            return false;
        }

        /** Is (x, z) inside an enemy tower's reach (plus margin)? */
        public boolean underTower(double x, double z, double margin) {
            for (Tower t : towers)
                if (t.reaches(x, z, margin))
                    return true;
            return false;
        }
    }

    private static final List<LivingEntity> scratch = new ArrayList<>();
    private static final List<LivingEntity> candidates = new ArrayList<>();

    /** Fills {@code out} with what {@code owner}'s side sees round the hero now. Server thread only. */
    public static void scan(ServerLevel level, Mob hero, String owner, Snapshot out) {
        out.enemies.clear();
        out.friends.clear();
        out.towers.clear();
        out.tick = level.getGameTime();
        double hx = hero.getX(), hz = hero.getZ(), r2 = RADIUS * RADIUS;
        // the owner's own fog, looked up once per scan
        boolean fog = FogOfWarServerEvents.isEnabled() && FogOfWarServerEvents.fogAppliesTo(owner);
        ServerPlayer viewer = fog ? level.getServer().getPlayerList().getPlayerByName(owner) : null;

        candidates.clear();
        for (LivingEntity le : UnitGrid.near(level, hx, hz, RADIUS, scratch)) {
            double dx = le.getX() - hx, dz = le.getZ() - hz;
            if (dx * dx + dz * dz > r2)
                continue;
            if (HeroFx.isFriend(hero, le))
                out.friends.add(le);
            else if (HeroFx.isEnemy(hero, le))
                candidates.add(le);
        }
        scratch.clear();
        if (!out.friends.contains(hero))
            out.friends.add(hero);
        for (LivingEntity e : candidates)
            if (sees(owner, fog, viewer, out.friends, e, e.getX(), e.getZ()))
                out.enemies.add(e);
        candidates.clear();

        int reach = (int) RADIUS + MAX_GUN_REACH;
        BuildingGrid.of(false).forEachNear((int) hx - reach, (int) hz - reach, (int) hx + reach, (int) hz + reach,
            (b, index) -> {
                Tower t = towerOf(b, owner);
                if (t == null)
                    return;
                double dx = t.x - hx, dz = t.z - hz, near = RADIUS + t.range;
                if (dx * dx + dz * dz <= near * near && sees(owner, fog, viewer, out.friends, null, t.x, t.z))
                    out.towers.add(t);
            });
    }

    /** An enemy building of {@code owner}'s that shoots ground units, as a Tower; null for anything else. */
    @Nullable
    static Tower towerOf(BuildingPlacement b, String owner) {
        if (!b.isBuilt || b.isDestroyedServerside || b.ownerName == null || b.ownerName.isBlank()
                || b.ownerName.equals(owner) || AlliancesServerEvents.isAllied(owner, b.ownerName)
                || Concealment.isBuildingHiddenFrom(b, owner))
            return null;
        int range = SupportOrders.gunRange(b);
        if (range <= 0)
            return null;
        float dps = b.getBuilding() instanceof DefenceTower t ? t.kind.dps() : OTHER_GUN_DPS;
        return new Tower(b.centrePos.getX() + 0.5, b.centrePos.getZ() + 0.5, range, b.getHealth(), dps);
    }

    /** Does {@code owner}'s side see the unit {@code e} (or, with e null, the column at x, z)? */
    static boolean sees(String owner, boolean fog, @Nullable ServerPlayer viewer, List<LivingEntity> friends,
                        @Nullable Entity e, double x, double z) {
        if (e != null && Concealment.isHiddenFrom(e, owner))
            return false;
        if (!fog)
            return true;
        if (viewer != null)
            return FogOfWarServerEvents.isBlockVisibleFor(viewer, (int) Math.floor(x), (int) Math.floor(z));
        for (LivingEntity f : friends) {
            if (!(f instanceof Unit u))
                continue;
            double dx = f.getX() - x, dz = f.getZ() - z, s = u.getSightRange();
            if (dx * dx + dz * dz <= s * s)
                return true;
        }
        return false;
    }
}
