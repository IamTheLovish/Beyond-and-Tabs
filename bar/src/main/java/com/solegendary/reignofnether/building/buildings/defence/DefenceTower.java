package com.solegendary.reignofnether.building.buildings.defence;

import static com.solegendary.reignofnether.building.BuildingUtils.getAbsoluteBlockData;

import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.blocks.BlockClientEvents;
import com.solegendary.reignofnether.bot.BotRoles;
import com.solegendary.reignofnether.building.Building;
import com.solegendary.reignofnether.building.BuildingBlock;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.addon.RangeIndicatorAddon;
import com.solegendary.reignofnether.building.production.ProductionItems;
import com.solegendary.reignofnether.research.ResearchServerEvents;
import com.solegendary.reignofnether.unit.Concealment;
import com.solegendary.reignofnether.unit.UnitGrid;
import com.solegendary.reignofnether.unit.interfaces.FieldArtillery;
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
 * The self-firing static defences (design/defences.md), one weapon class with seven tunings ({@link Kind}):
 * <ul>
 *   <li>{@link Kind#RAID} - the <b>T1 anti-raid tower</b> (BAR's LLT): a small 3x3 tower every faction can raise once
 *       its capitol stands. Short reach, a quick light shot that hits light units (max health {@link #LIGHT_HEALTH} or
 *       less) {@link Kind#bonusMult} times as hard, ground targets only. A pair of raiders dies at its foot; anything
 *       that outranges it (artillery, archers behind a line) takes it apart for free.</li>
 *   <li>{@link Kind#RAID_HEAVY} - its <b>enhanced</b> twin (BAR's HLLT): still a T1 builder's job, once a T1 lab stands;
 *       about 2.2x the price for about 2.3x the weapon - holds a raid alone, still loses to artillery.</li>
 *   <li>{@link Kind#SKY_WATCH} / {@link Kind#SKY_WATCH_HEAVY} - the <b>T1 anti-air towers</b> (lovish Oct 21 02:47:
 *       "flying units ran through everything... anti air defenses should be added"): air only, long reach (flyers are
 *       fast - the tower has to reach them before they are past), one target at a time. The heavy one waits for a T1
 *       lab like the heavy raid tower ("tier 1 constructors can make enhanced version of tier 1 defenses - kinda like in
 *       bar", 02:51).</li>
 *   <li>{@link Kind#FLAK} - the <b>T2 flak</b>: air only, a burst that hits every enemy flyer within
 *       {@link Kind#splash} of the target - the answer to a clump of flyers. Tier 2 research and a T2 constructor.</li>
 *   <li>{@link Kind#SIEGE_GUARD} - the <b>anti-siege tower</b> (BAR's HLT; lovish Oct 29, design/siege_t1.md): a tall
 *       T1-lab tower whose reach (20) is past the T1 siege lobber's (15) and every T1 line unit's, with a slow, heavy
 *       shot (45 every 2.5 s) that hits siege units ({@link #isSiege}) {@link Kind#bonusMult} times as hard and
 *       prefers them - one kills a lobber - and the health to outlast six lobbers. Ground only; lovish's balance pass
 *       (Oct 29) slowed it from 1.5 s so the enhanced anti-raid tower stays the better raid answer and a lobber push
 *       keeps its window. The long artillery (22-24) still outranges it.</li>
 *   <li>{@link Kind#ANTI_T3} - the <b>anti-experimental emplacement</b>: a 5x5 heavy weapon at the T3 lab tier, priced
 *       at DefenceCosts.ANTI_T3_SHARE of the priciest experimental. A slow, heavy shot that hits experimentals
 *       (and anything that big) {@link Kind#bonusMult} times as hard and prefers them; it reaches flyers too (two of
 *       the experimentals are dragons). Weak against a swarm, short-ranged, and too big to tuck away.</li>
 * </ul>
 * Every number lives in {@link Kind} and {@link DefenceCosts} - one table to tune after a playtest (lovish 02:53: "we can
 * adjust numbers to fit balance, keep them flexible"). The shot is instant (a tracer of the faction's particle from the
 * muzzle to the target, DefenceStyle) and generic damage, like the Rail Battery: a building has no entity to own the
 * hit. Cost at 8v8: one grid query per tower every {@link DefenceTowerPlacement#SCAN_TICKS} while idle, one per shot
 * (two for a flak burst).
 */
public class DefenceTower extends DefenceBuilding implements RangeIndicatorAddon {

    /** A target at or under this max health is "light" (raiders, scouts, workers): the anti-raid tower's bonus. */
    public static final float LIGHT_HEALTH = 70f;

    /** What a defence needs before its button lights up (and, for the research gates, before it fires). */
    public enum Gate { CAPITOL, T1_LAB, TIER_2, TIER_3 }

    public enum Kind {
        //               range dmg  cooldown bonus hitsAir airOnly splash hp    gate        buildTime
        RAID(            14,   7f,  20,      1.5f, false,  false,  0f,    260d, Gate.CAPITOL, 0.8f),
        RAID_HEAVY(      16,   18f, 20,      1.5f, false,  false,  0f,    600d, Gate.T1_LAB,  1.0f),
        SKY_WATCH(       20,   15f, 20,      1f,   true,   true,   0f,    280d, Gate.CAPITOL, 0.8f),
        SKY_WATCH_HEAVY( 22,   35f, 20,      1f,   true,   true,   0f,    550d, Gate.T1_LAB,  1.0f),
        FLAK(            24,   12f, 15,      1.5f, true,   true,   3f,    650d, Gate.TIER_2,  0.9f),
        ANTI_T3(         16,   25f, 60,      3f,   true,   false,  0f,    700d, Gate.TIER_3,  0.9f),
        SIEGE_GUARD(     20,   45f, 50,      2f,   false,  false,  0f,    1000d, Gate.T1_LAB, 1.2f);

        public final int range;
        public final float damage;
        public final int cooldownTicks;
        public final float bonusMult;
        public final boolean hitsAir;
        /** Shoots flyers and nothing else (the anti-air towers). */
        public final boolean airOnly;
        /** A burst: every other enemy it may target within this many blocks of the target takes the hit too (0: none). */
        public final float splash;
        public final double hp;
        public final Gate gate;
        public final float buildTimeModifier;

        Kind(int range, float damage, int cooldownTicks, float bonusMult, boolean hitsAir, boolean airOnly, float splash,
             double hp, Gate gate, float buildTimeModifier) {
            this.range = range;
            this.damage = damage;
            this.cooldownTicks = cooldownTicks;
            this.bonusMult = bonusMult;
            this.hitsAir = hitsAir;
            this.airOnly = airOnly;
            this.splash = splash;
            this.hp = hp;
            this.gate = gate;
            this.buildTimeModifier = buildTimeModifier;
        }

        /** Damage per second against an ordinary target (the tooltip's headline number). */
        public float dps() {
            return damage * 20f / cooldownTicks;
        }

        /** The big-weapon look (the anti-T3 portrait, the heavy tracer and sound). */
        public boolean heavyLook() {
            return this == ANTI_T3 || this == FLAK || this == SIEGE_GUARD;
        }

        /** The bonus goes against light units (the raid towers) rather than experimentals (anti-T3, flak). */
        public boolean bonusVsLight() {
            return this == RAID || this == RAID_HEAVY;
        }

        /** The bonus goes against siege units (the anti-siege tower). */
        public boolean bonusVsSiege() {
            return this == SIEGE_GUARD;
        }
    }

    public final Kind kind;

    /**
     * @param labs the faction's T3 lab for {@link Gate#TIER_3}, or any of its T1 labs for {@link Gate#T1_LAB} (the
     *             Horde has two: a Basic Portal and its Military refit); null for the other gates
     */
    public DefenceTower(String id, String displayName, DefenceStyle style, Kind kind, @Nullable Supplier<List<Building>> labs) {
        super(id, displayName, style, DefenceCosts.of(kind));
        this.kind = kind;
        this.portraitBlock = kind.heavyLook() ? style.antiT3Portrait() : style.towerPortrait();
        this.maxHealth = kind.hp;
        this.buildTimeModifier = kind.buildTimeModifier;
        switch (kind.gate) {
            case T1_LAB -> this.anyOfLabs = labs;
            case TIER_2 -> {
                this.needsTier2 = true;
                this.t2Builder = true;
            }
            case TIER_3 -> {
                this.anyOfLabs = labs;
                this.needsTier3 = true;
            }
            default -> { }
        }
        setActiveAddon(RangeIndicatorAddon.class, this, true);
    }

    /**
     * Whether the target counts for the bonus: light units for the anti-raid tower, siege units for the anti-siege one,
     * experimentals for the anti-T3 one.
     */
    public boolean isBonusTarget(LivingEntity le) {
        if (kind.bonusMult == 1f)
            return false;
        if (kind.bonusVsLight())
            return le.getMaxHealth() <= LIGHT_HEALTH;
        if (kind.bonusVsSiege())
            return isSiege(le);
        return isExperimental(le);
    }

    /** A siege unit: the bot's siege role (the T1 lobbers, the wall-breakers) or the shell-lobbing field artillery. */
    public static boolean isSiege(LivingEntity le) {
        return le instanceof FieldArtillery || BotRoles.isSiege(le);
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
        // %1$s damage a shot (= a second for the 1 s towers), %2$s range, %3$s bonus damage, %4$s damage a second,
        // %5$s burst radius
        return new Object[] { (int) kind.damage, kind.range, (int) (kind.damage * kind.bonusMult), Math.round(kind.dps()),
            (int) kind.splash };
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
        /** The last look found a target: the tower looks again the tick it is ready, not at the next SCAN_TICKS. */
        private boolean engaged = false;

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

        /**
         * One server tick: count the cooldown down and fire on the tick it reaches 0, so a tower shoots every
         * cooldownTicks exactly. A tower still in a fight looks for its next target the tick it is ready; an idle one
         * only every SCAN_TICKS (one grid query per shot, one per SCAN_TICKS while idle). It used to skip the ready
         * tick and then wait for the next SCAN_TICKS even mid-fight: a 1 s tower fired every 1.5 s. Public for the
         * game test.
         */
        public void tickWeapon(ServerLevel sl) {
            if (cooldown > 0 && --cooldown > 0)
                return;
            if (!engaged && tickAge % SCAN_TICKS != 0 && onlyFoe == null)
                return;
            engaged = fire(sl) != null;
        }

        /** Dormant: unbuilt, destroyed, ownerless, or a flak / anti-T3 defence whose owner lacks its tier's research. */
        public boolean dormant() {
            if (!isBuilt || isDestroyedServerside || ownerName == null || ownerName.isBlank())
                return true;
            if (tower.needsTier2 && !ResearchServerEvents.playerHasResearch(ownerName, ProductionItems.RESEARCH_TIER_2))
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
            if (tower.kind.splash > 0)
                burst(sl, target);
            target.hurt(sl.damageSources().generic(), tower.damageAgainst(target));
            return target;
        }

        /** The flak burst: every other enemy the tower may target within its splash of the target takes the hit too. */
        void burst(ServerLevel sl, LivingEntity target) {
            float r = tower.kind.splash;
            double r2 = (double) r * r;
            for (LivingEntity le : UnitGrid.near(sl, target.getX(), target.getZ(), (int) Math.ceil(r), scratch)) {
                if (le == target || !canTarget(le) || le.distanceToSqr(target) > r2)
                    continue;
                le.hurt(sl.damageSources().generic(), tower.damageAgainst(le));
            }
            scratch.clear();
        }

        /** An enemy this tower may shoot at all (ignoring range): visible, and on the right side of the ground/air line. */
        boolean canTarget(LivingEntity le) {
            if (!isFoe(le) || Concealment.isHiddenFrom(le, ownerName) || le.isInvisible())
                return false;
            boolean flyer = le instanceof Unit u && u.isFlyingUnit();
            if (flyer)
                return tower.kind.hitsAir;
            return !tower.kind.airOnly;
        }

        /**
         * Raid towers: the nearest enemy on the ground. Anti-air: the nearest enemy flyer. Anti-T3 and flak: the nearest
         * experimental, else the nearest enemy they may shoot. Anti-siege: the nearest siege unit, else the nearest
         * ground enemy.
         */
        @Nullable
        public LivingEntity pickTarget(ServerLevel sl) {
            double cx = centrePos.getX() + 0.5, cz = centrePos.getZ() + 0.5, cy = centrePos.getY();
            int range = tower.kind.range;
            double r2 = (double) range * range;
            LivingEntity best = null, bestBonus = null;
            double bestD = Double.MAX_VALUE, bestBonusD = Double.MAX_VALUE;
            for (LivingEntity le : UnitGrid.near(sl, cx, cz, range, scratch)) {
                if (!canTarget(le))
                    continue;
                double dx = le.getX() - cx, dz = le.getZ() - cz, d = dx * dx + dz * dz;
                if (d > r2 || Math.abs(le.getY() - cy) > range)
                    continue;
                if (d < bestD) {
                    bestD = d;
                    best = le;
                }
                if (!tower.kind.bonusVsLight() && d < bestBonusD && tower.isBonusTarget(le)) {
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
            boolean heavy = tower.kind.heavyLook();
            var particle = heavy ? tower.style.heavyShot() : tower.style.shot();
            com.solegendary.reignofnether.barfx.BarFx.shot(sl, from, to,
                heavy ? com.solegendary.reignofnether.barfx.BarFx.K_ROCKET : com.solegendary.reignofnether.barfx.BarFx.K_ARROW, com.solegendary.reignofnether.barfx.BarFx.W_BY_KIND,
                com.solegendary.reignofnether.faction.FactionTraits.of(tower.getFaction()).deathDebris);   // the tower's faction tints it
            sl.sendParticles(particle, to.x, to.y, to.z, heavy ? 10 : 3, 0.2, 0.2, 0.2, 0.02);
            sl.playSound(null, BlockPos.containing(from), heavy ? tower.style.heavySound() : tower.style.shotSound(),
                SoundSource.NEUTRAL, heavy ? 1.2f : 0.7f, heavy ? 0.7f : 1.3f);
        }
    }
}
