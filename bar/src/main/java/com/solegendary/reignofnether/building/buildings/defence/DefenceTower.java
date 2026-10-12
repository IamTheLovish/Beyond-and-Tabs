package com.solegendary.reignofnether.building.buildings.defence;

import static com.solegendary.reignofnether.building.BuildingUtils.getAbsoluteBlockData;

import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.blocks.BlockClientEvents;
import com.solegendary.reignofnether.building.Building;
import com.solegendary.reignofnether.building.BuildingBlock;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.addon.RangeIndicatorAddon;
import com.solegendary.reignofnether.building.production.ProductionItems;
import com.solegendary.reignofnether.research.ResearchServerEvents;
import com.solegendary.reignofnether.unit.Concealment;
import com.solegendary.reignofnether.unit.UnitGrid;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.util.MiscUtil;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * The self-firing static defences (design/defences.md), one weapon class with two tunings:
 * <ul>
 *   <li>{@link Kind#RAID} - the <b>T1 anti-raid tower</b> (BAR's LLT): a small 3x3 tower every faction can raise once
 *       its capitol stands. Short reach ({@link Kind#range}), a quick light shot that hits light units (max health
 *       {@link #LIGHT_HEALTH} or less) {@link Kind#bonusMult} times as hard, ground targets only. A pair of raiders
 *       dies at its foot; anything that outranges it (artillery, archers behind a line) takes it apart for free.</li>
 *   <li>{@link Kind#ANTI_T3} - the <b>anti-experimental emplacement</b>: a 5x5 heavy weapon at the T3 lab tier, priced
 *       at DefenceCosts.ANTI_T3_SHARE of the priciest experimental. A slow, heavy shot that hits experimentals
 *       (and anything that big) {@link Kind#bonusMult} times as hard and prefers them; it reaches flyers too (two of
 *       the experimentals are dragons). Weak against a swarm, short-ranged, and too big to tuck away.</li>
 * </ul>
 * The shot is instant (a tracer of the faction's particle from the muzzle to the target, DefenceStyle) and generic
 * damage, like the Rail Battery: a building has no entity to own the hit. Cost at 8v8: one grid query per tower every
 * {@link DefenceTowerPlacement#SCAN_TICKS} while idle, one per shot.
 */
public class DefenceTower extends DefenceBuilding implements RangeIndicatorAddon {

    /** A target at or under this max health is "light" (raiders, scouts, workers): the anti-raid tower's bonus. */
    public static final float LIGHT_HEALTH = 70f;

    public enum Kind {
        //    range dmg  cooldown bonus  hitsAir hp
        RAID(14, 7f, 20, 1.5f, false, 260d),
        ANTI_T3(16, 25f, 60, 3f, true, 700d);

        public final int range;
        public final float damage;
        public final int cooldownTicks;
        public final float bonusMult;
        public final boolean hitsAir;
        public final double hp;

        Kind(int range, float damage, int cooldownTicks, float bonusMult, boolean hitsAir, double hp) {
            this.range = range;
            this.damage = damage;
            this.cooldownTicks = cooldownTicks;
            this.bonusMult = bonusMult;
            this.hitsAir = hitsAir;
            this.hp = hp;
        }
    }

    public final Kind kind;

    public DefenceTower(String id, String displayName, DefenceStyle style, Kind kind, @Nullable Supplier<Building> t3Lab) {
        super(id, displayName, style, kind == Kind.RAID ? DefenceCosts.RAID_TOWER : DefenceCosts.ANTI_T3);
        this.kind = kind;
        this.portraitBlock = kind == Kind.RAID ? style.towerPortrait() : style.antiT3Portrait();
        this.maxHealth = kind.hp;
        this.buildTimeModifier = kind == Kind.RAID ? 0.8f : 0.9f;
        this.explodeChance = 0.2f;
        if (kind == Kind.ANTI_T3) {
            this.requiredLab = t3Lab;
            this.needsTier3 = true;
        }
        setActiveAddon(RangeIndicatorAddon.class, this, true);
    }

    /** Whether the target counts for the bonus: light units for the anti-raid tower, experimentals for the anti-T3 one. */
    public boolean isBonusTarget(LivingEntity le) {
        if (kind == Kind.RAID)
            return le.getMaxHealth() <= LIGHT_HEALTH;
        return isExperimental(le);
    }

    /** An experimental: priced like a T3 (WallSegment.T3_METAL) or simply that big. */
    public static boolean isExperimental(LivingEntity le) {
        if (le instanceof Unit u && u.getCost() != null && u.getCost().metal() >= WallSegment.T3_METAL)
            return true;
        return le.getBbWidth() >= 2.5f;
    }

    /** The damage one shot deals to this target. */
    public float damageAgainst(LivingEntity le) {
        return isBonusTarget(le) ? kind.damage * kind.bonusMult : kind.damage;
    }

    @Override
    protected Object[] tooltipArgs() {
        return new Object[] { (int) kind.damage, kind.range, (int) (kind.damage * kind.bonusMult) };
    }

    @Override
    public BuildingPlacement createBuildingPlacement(Level level, BlockPos pos, Rotation rotation, String ownerName) {
        return new DefenceTowerPlacement(this, level, pos, rotation, ownerName,
            getAbsoluteBlockData(getRelativeBlockData(level), level, pos, rotation), false);
    }

    @Override
    public int getRange(BuildingPlacement placement) {
        return kind.range;
    }

    @Override
    public void updateHighlightBps(BuildingPlacement placement) {
        if (!placement.level.isClientSide())
            return;
        placement.getDataStorage().getData(RangeIndicatorAddon.HIGHLIGHT_BPS_CACHE).clear();
        placement.getDataStorage().getData(RangeIndicatorAddon.HIGHLIGHT_BPS_CACHE).addAll(MiscUtil.getRangeIndicatorCircleBlocks(placement.centrePos,
                getRange(placement) - BlockClientEvents.VISIBLE_BORDER_ADJ, placement.level));
    }

    @Override
    public boolean showOnlyWhenSelected(BuildingPlacement placement) {
        return true;
    }

    /** The weapon. Server side only; nothing here is saved (a reloaded tower is simply ready). */
    public static class DefenceTowerPlacement extends BuildingPlacement {
        public static final int SCAN_TICKS = 10;

        public final DefenceTower tower;
        /** Ticks until the next shot may fire (0 = ready). */
        public int cooldown = 0;
        /** The enemy owner a game test limits targets to (null in play: game tests share one world). */
        @Nullable public String onlyFoe = null;

        private final List<LivingEntity> scratch = new ArrayList<>();

        public DefenceTowerPlacement(DefenceTower building, Level level, BlockPos originPos, Rotation rotation, String ownerName,
                                     ArrayList<BuildingBlock> blocks, boolean isCapitol) {
            super(building, level, originPos, rotation, ownerName, blocks, isCapitol);
            this.tower = building;
        }

        @Override
        public void tick(Level tickLevel) {
            super.tick(tickLevel);
            if (tickLevel instanceof ServerLevel sl)
                tickWeapon(sl);
        }

        /** One server tick: count the cooldown down, then look for a target every SCAN_TICKS. Public for the game test. */
        public void tickWeapon(ServerLevel sl) {
            if (cooldown > 0) {
                cooldown--;
                return;
            }
            if (tickAge % SCAN_TICKS != 0 && onlyFoe == null)
                return;
            fire(sl);
        }

        /** Dormant: unbuilt, destroyed, ownerless, or an anti-T3 emplacement whose owner lacks Tier 3. */
        public boolean dormant() {
            if (!isBuilt || isDestroyedServerside || ownerName == null || ownerName.isBlank())
                return true;
            return tower.needsTier3 && !ResearchServerEvents.playerHasResearch(ownerName, ProductionItems.RESEARCH_TIER_3);
        }

        /** Shoots the best target in range if ready. Returns the unit hit, or null. Public for the game test. */
        @Nullable
        public LivingEntity fire(ServerLevel sl) {
            if (cooldown > 0 || dormant())
                return null;
            LivingEntity target = pickTarget(sl);
            if (target == null)
                return null;
            cooldown = tower.kind.cooldownTicks;
            drawShot(sl, target);
            target.hurt(sl.damageSources().generic(), tower.damageAgainst(target));
            return target;
        }

        /** Anti-raid: the nearest enemy on the ground. Anti-T3: the nearest experimental, else the nearest enemy. */
        @Nullable
        public LivingEntity pickTarget(ServerLevel sl) {
            double cx = centrePos.getX() + 0.5, cz = centrePos.getZ() + 0.5, cy = centrePos.getY();
            int range = tower.kind.range;
            double r2 = (double) range * range;
            LivingEntity best = null, bestBonus = null;
            double bestD = Double.MAX_VALUE, bestBonusD = Double.MAX_VALUE;
            for (LivingEntity le : UnitGrid.near(sl, cx, cz, range, scratch)) {
                if (!isFoe(le) || Concealment.isHiddenFrom(le, ownerName) || le.isInvisible())
                    continue;
                if (!tower.kind.hitsAir && le instanceof Unit u && u.isFlyingUnit())
                    continue;
                double dx = le.getX() - cx, dz = le.getZ() - cz, d = dx * dx + dz * dz;
                if (d > r2 || Math.abs(le.getY() - cy) > range)
                    continue;
                if (d < bestD) {
                    bestD = d;
                    best = le;
                }
                if (tower.kind == Kind.ANTI_T3 && d < bestBonusD && tower.isBonusTarget(le)) {
                    bestBonusD = d;
                    bestBonus = le;
                }
            }
            scratch.clear();
            return bestBonus != null ? bestBonus : best;
        }

        private boolean isFoe(LivingEntity le) {
            if (!le.isAlive() || le.level() != level || !(le instanceof Unit u))
                return false;
            String o = u.getOwnerName();
            return o != null && !o.isBlank() && !ownerName.equals(o) && !AlliancesServerEvents.isAllied(ownerName, o)
                && (onlyFoe == null || onlyFoe.equals(o));
        }

        /** The muzzle: the top of the tower's centre column. */
        public Vec3 muzzle() {
            return new Vec3(centrePos.getX() + 0.5, maxCorner.getY() + 0.5, centrePos.getZ() + 0.5);
        }

        /**
         * The tracer goes through BarFx (one batched packet per player per tick for every shot on the map) and the
         * faction's particle only marks the hit. It used to be a dotted line of sendParticles calls - ~16 per light shot,
         * each its own packet to every player in range and each through the fog filter per player
         * (ServerLevelParticleMixin) - so forty raid towers in an 8v8 fight sent hundreds of packets a second
         * (design/code_review.md R2-02).
         */
        void drawShot(ServerLevel sl, LivingEntity target) {
            Vec3 from = muzzle();
            Vec3 to = new Vec3(target.getX(), target.getY() + target.getBbHeight() * 0.6, target.getZ());
            boolean heavy = tower.kind == Kind.ANTI_T3;
            var particle = heavy ? tower.style.heavyShot() : tower.style.shot();
            com.solegendary.reignofnether.barfx.BarFx.shot(sl, from, to,
                heavy ? com.solegendary.reignofnether.barfx.BarFx.K_ROCKET : com.solegendary.reignofnether.barfx.BarFx.K_ARROW);
            sl.sendParticles(particle, to.x, to.y, to.z, heavy ? 10 : 3, 0.2, 0.2, 0.2, 0.02);
            sl.playSound(null, BlockPos.containing(from), heavy ? tower.style.heavySound() : tower.style.shotSound(),
                SoundSource.NEUTRAL, heavy ? 1.2f : 0.7f, heavy ? 0.7f : 1.3f);
        }
    }
}
