package com.solegendary.reignofnether.unit;

import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.perf.Sys;
import com.solegendary.reignofnether.registrars.GameRuleRegistrar;
import com.solegendary.reignofnether.unit.interfaces.AttackerUnit;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.util.MiscUtil;

import it.unimi.dsi.fastutil.ints.Int2LongMap;
import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.function.Predicate;

/**
 * Target acquisition (Oct 21 perf plan, PR 5): MiscUtil.findClosestAttackableEntity without walking the level's entity
 * sections. lovish's 3-player match ran 55-105 ms per tick at 400-650 units against a 50 ms budget, and every idle
 * attacker asked for an AABB entity query every 4 ticks, sorted everything in it - its own army included - and then
 * raycast its way down the list. Now:
 * <ul>
 *   <li><b>One grid per tick.</b> The first scan in a server tick buckets the level's living entities into 8x8 column
 *       cells (one pass over the entity list, {@code bat:targetGrid} in [Sys]); every scan in that tick reads only the
 *       cells its reach touches. Each cell also keeps a mask of which sides stand in it.</li>
 *   <li><b>A unit with nothing hostile near stops there.</b> Before it gathers anything, a unit ORs the masks of the
 *       cells round it: if every unit there is its own or an ally's and no other possible target (a player, a mob
 *       when neutralAggro is on) stands there, the answer is "nothing" with no list, no sort and no raycast. That is
 *       most idle units in a big match - an army waiting in its base.</li>
 *   <li><b>Checks first, line of sight last, in distance order, lazily.</b> The order-free checks (relationship,
 *       garrison, concealment, world border) run while gathering, so friends never reach the ordering; the rest are
 *       taken nearest first, one at a time, and only those are raycast - the first in sight wins.</li>
 * </ul>
 * Same answers as the full scan (unit/TargetScansLegacy; the equivalence GameTest holds them to it): the grid holds
 * every living entity the section walk could return - units of every side, dying ones too, players and mobs that may
 * be picked ({@link MiscUtil#mayBeAutoTargeted}) - and the distance, priority and sort rules are MiscUtil's, shared by
 * both. Two differences, both at the edges: on an exact tie in distance the lower entity id wins (the full scan kept
 * entity-section order), and an entity that moved more than {@link #PAD} blocks since this tick's rebuild is looked
 * for in the cells round where it stood.
 *
 * Idle units also look less often ({@link AttackerUnit#IDLE_AGGRO_TICKS}, a gameplay number for lovish), except a unit
 * that was fighting a moment ago ({@link #inCombat}): it picks its next target as fast as before.
 *
 * Server thread only.
 */
public final class TargetScan {
    private TargetScan() { }

    // ------------------------------------------------------------------------------------------------- switches

    /**
     * True: findClosestAttackableEntity runs the full scan it replaced and idle units look every 4 ticks again - the
     * game exactly as before this change. Only the [Stress] benchmark and the equivalence test flip it.
     */
    public static boolean legacyScans = false;

    /**
     * Ticks between an idle unit's looks for something to attack: {@link AttackerUnit#IDLE_AGGRO_TICKS}. The benchmark
     * sets 4 to time the new scan without the stagger. A multiple of 4 (AttackerUnit's scans run every 4 ticks).
     */
    public static int idleAggroTicks = AttackerUnit.IDLE_AGGRO_TICKS;

    /** Scans that stopped at the masks (nothing hostile near) and scans that gathered candidates, since start. */
    public static long skips = 0, gathers = 0;

    // ------------------------------------------------------------------------------------------------- the grid

    static final int SHIFT = 3;
    /**
     * The grid is built at the first scan of a tick and entities keep moving after it: a scan reads this many blocks
     * past its reach, so whatever moved less since the rebuild is still found. A walking unit moves well under a block
     * per tick; a knockback fling or a teleport can do more, and is found from the next tick's grid.
     */
    static final double PAD = 3.0;
    /** Bits of a cell mask: one per side (owner name) on the level, up to this many; the next ones share OVERFLOW_BIT. */
    static final int SIDE_BITS = 62;
    static final long OVERFLOW_BIT = 1L << 62;
    /** A living entity that is not a unit and may be picked: a player, a mob when neutralAggro is on. */
    static final long OTHER_BIT = 1L << 63;

    private static final Long2ObjectOpenHashMap<ArrayList<LivingEntity>> CELLS = new Long2ObjectOpenHashMap<>();
    private static final Long2LongOpenHashMap MASKS = new Long2LongOpenHashMap();
    private static final Object2IntOpenHashMap<String> SIDES = new Object2IntOpenHashMap<>();
    private static final String[] SIDE_NAMES = new String[SIDE_BITS];
    /** Per side: the bits of the sides it never auto-targets (its own and its allies'); 0 until first asked in a build. */
    private static final long[] FRIENDLY = new long[SIDE_BITS];
    static {
        SIDES.defaultReturnValue(-1);
    }
    private static ServerLevel builtFor = null;
    private static long builtAt = Long.MIN_VALUE;
    private static int builtJoins = -1;
    private static boolean builtNeutralAggro = false;
    /** Living entities that joined a server level: one joining mid-tick (a unit out of a lab) rebuilds the grid. */
    private static int joins = 0;
    private static int rebuilds = 0, cellEntries = 0;

    static long key(int cx, int cz) {
        return ((long) cx << 32) ^ (cz & 0xffffffffL);
    }

    static int cell(double v) {
        return ((int) Math.floor(v)) >> SHIFT;
    }

    private static void ensure(ServerLevel level, boolean neutralAggro) {
        long now = level.getGameTime();
        if (builtFor == level && builtAt == now && builtJoins == joins && builtNeutralAggro == neutralAggro)
            return;
        int sys = Sys.begin(Sys.TARGET_GRID);
        try {
            rebuild(level, neutralAggro);
        } finally {
            Sys.end(sys);
        }
        builtFor = level;
        builtAt = now;
        builtJoins = joins;
        builtNeutralAggro = neutralAggro;
    }

    private static void rebuild(ServerLevel level, boolean neutralAggro) {
        // clear in place; drop the map now and then so the cells of long-gone fights don't pile up
        if (++rebuilds % 1200 == 0) {
            CELLS.clear();
            pruneEngaged(level.getGameTime());
        } else {
            for (ArrayList<LivingEntity> cell : CELLS.values())
                cell.clear();
        }
        MASKS.clear();
        SIDES.clear();
        Arrays.fill(SIDE_NAMES, null);
        Arrays.fill(FRIENDLY, 0L);
        cellEntries = 0;
        // every living entity the section walk could return (accessible sections, no spectators), except those no unit
        // could ever pick by itself: those are only dropped later by the same checks
        for (Entity e : level.getAllEntities()) {
            if (!(e instanceof LivingEntity le) || le.isRemoved() || le.isSpectator())
                continue;
            long bit;
            if (le instanceof Unit unit)
                bit = sideBit(unit.getOwnerName());
            else if (MiscUtil.mayBeAutoTargeted(le, neutralAggro))
                bit = OTHER_BIT;
            else
                continue;
            long k = key(cell(le.getX()), cell(le.getZ()));
            ArrayList<LivingEntity> cell = CELLS.get(k);
            if (cell == null) {
                cell = new ArrayList<>();
                CELLS.put(k, cell);
            }
            cell.add(le);
            MASKS.put(k, MASKS.get(k) | bit);
            cellEntries++;
        }
    }

    private static long sideBit(String owner) {
        int i = SIDES.getInt(owner);
        if (i < 0) {
            if (SIDES.size() >= SIDE_BITS)
                return OVERFLOW_BIT;
            i = SIDES.size();
            SIDES.put(owner, i);
            SIDE_NAMES[i] = owner;
        }
        return 1L << i;
    }

    /**
     * The mask bits of everything a unit of this side never picks by itself: its own side and its allies
     * (UnitServerEvents.getUnitToEntityRelationship: FRIENDLY). 0 for a side that has no unit on the level - or is
     * past the SIDE_BITS-th - so then nothing counts as friendly and every scan gathers.
     */
    static long friendlyMask(String owner) {
        int i = SIDES.getInt(owner);
        if (i < 0)
            return 0;
        long f = FRIENDLY[i];
        if (f == 0) {
            for (int j = 0; j < SIDE_BITS && SIDE_NAMES[j] != null; j++)
                if (j == i || AlliancesServerEvents.isAllied(owner, SIDE_NAMES[j]) || owner.equals(SIDE_NAMES[j]))
                    f |= 1L << j;
            FRIENDLY[i] = f;
        }
        return f;
    }

    // ------------------------------------------------------------------------------------------------- the scan

    private static LivingEntity[] ents = new LivingEntity[64];
    private static double[] keys = new double[64];

    /** See {@link MiscUtil#findClosestAttackableEntity}; the answers of {@link TargetScansLegacy}, from the grid. */
    public static LivingEntity findClosestAttackableEntity(Mob unitMob, float range, ServerLevel level) {
        boolean neutralAggro = unitMob.level().getGameRules().getRule(GameRuleRegistrar.NEUTRAL_AGGRO).get();
        ensure(level, neutralAggro);
        double x = unitMob.getX(), z = unitMob.getZ();
        int x0 = cell(x - range - PAD), x1 = cell(x + range + PAD);
        int z0 = cell(z - range - PAD), z1 = cell(z + range + PAD);

        // nothing within reach that this unit could ever pick: stop before gathering anything. A mob that is not a unit
        // has no side (everything is NEUTRAL to it), so it always gathers
        long wanted = unitMob instanceof Unit unit ? ~friendlyMask(unit.getOwnerName()) : -1L;
        boolean any = false;
        for (int cx = x0; cx <= x1 && !any; cx++)
            for (int cz = z0; cz <= z1; cz++)
                if ((MASKS.get(key(cx, cz)) & wanted) != 0) {
                    any = true;
                    break;
                }
        probe(unitMob, any);
        if (!any) {
            skips++;
            return null;
        }
        gathers++;

        // gather what passes every check that does not depend on the order; each one is a pure function of the two
        // entities, so which comes first is decided by the sort key and line of sight alone, as in the full scan
        Vec3 pos = unitMob.position();
        boolean isMelee = MiscUtil.isMeleeAttacker(unitMob);
        int n = 0;
        try {
            for (int cx = x0; cx <= x1; cx++)
                for (int cz = z0; cz <= z1; cz++) {
                    if ((MASKS.get(key(cx, cz)) & wanted) == 0)
                        continue;
                    ArrayList<LivingEntity> cell = CELLS.get(key(cx, cz));
                    if (cell == null)
                        continue;
                    for (int i = 0, m = cell.size(); i < m; i++) {
                        LivingEntity e = cell.get(i);
                        if (e.isRemoved())   // discarded since the rebuild: no longer in any entity section
                            continue;
                        double dist = e.position().distanceTo(pos);
                        if (dist > range
                                || !e.level().getWorldBorder().isWithinBounds(e.blockPosition())
                                || !MiscUtil.isIdleOrMoveAttackable(unitMob, e, neutralAggro)
                                || (e instanceof Unit u && u.isGarrisoned())
                                || Concealment.isConcealed(e))
                            continue;
                        if (n == ents.length) {
                            ents = Arrays.copyOf(ents, n * 2);
                            keys = Arrays.copyOf(keys, n * 2);
                        }
                        ents[n] = e;
                        keys[n] = MiscUtil.isDeprioritisedTarget(e, isMelee) ? dist + 100 : dist;
                        n++;
                    }
                }
            if (n == 0)
                return null;
            // the priority kind first (the full scan's first pass), then the rest. A priority candidate the first pass
            // passed over was out of sight, so the second pass would pass over it again: it only reads the others
            Predicate<LivingEntity> priority = MiscUtil.targetPriority(unitMob);
            int p = 0;
            if (priority != null)
                for (int i = 0; i < n; i++)
                    if (priority.test(ents[i]))
                        swap(i, p++);
            LivingEntity hit = firstInSight(unitMob, 0, p);
            return hit != null ? hit : firstInSight(unitMob, p, n);
        } finally {
            Arrays.fill(ents, 0, n, null);   // hold no entity past the call
        }
    }

    /**
     * The nearest of ents[from, to) in this unit's line of sight: a selection sort that stops at the first hit, so a
     * scan usually orders and raycasts one candidate. On equal keys the lower entity id comes first.
     */
    private static LivingEntity firstInSight(Mob unitMob, int from, int to) {
        for (int i = from; i < to; i++) {
            int best = i;
            for (int j = i + 1; j < to; j++)
                if (keys[j] < keys[best] || (keys[j] == keys[best] && ents[j].getId() < ents[best].getId()))
                    best = j;
            swap(i, best);
            if (MiscUtil.hasLineOfSightForAttacks(unitMob, ents[i]))
                return ents[i];
        }
        return null;
    }

    private static void swap(int a, int b) {
        if (a == b)
            return;
        LivingEntity e = ents[a];
        ents[a] = ents[b];
        ents[b] = e;
        double k = keys[a];
        keys[a] = keys[b];
        keys[b] = k;
    }

    // ------------------------------------------------------------------------------------------------- idle stagger

    /**
     * A unit that had something to fight within this many ticks counts as in combat: when its target dies or runs
     * off it looks for the next one on AttackerUnit's 4-tick pass, not on the idle stagger.
     */
    public static final int COMBAT_MEMORY_TICKS = 60;
    /** Entity id -> game time of the last 4-tick pass at which the unit had a living target or a building to hit. */
    private static final Int2LongOpenHashMap ENGAGED = new Int2LongOpenHashMap();
    static {
        ENGAGED.defaultReturnValue(Long.MIN_VALUE);
    }

    /** AttackerUnit's 4-tick pass: this unit is fighting (a living target, or a building it is attacking). */
    public static void markEngaged(Mob mob) {
        ENGAGED.put(mob.getId(), mob.level().getGameTime());
    }

    /** Fought within {@link #COMBAT_MEMORY_TICKS}: had a target, or was hurt by a living attacker. */
    public static boolean inCombat(Mob mob) {
        long at = ENGAGED.get(mob.getId());
        if (at != Long.MIN_VALUE && mob.level().getGameTime() - at <= COMBAT_MEMORY_TICKS)
            return true;
        int hurtAt = mob.getLastHurtByMobTimestamp();
        return hurtAt > 0 && mob.tickCount - hurtAt <= COMBAT_MEMORY_TICKS;
    }

    /** The ticks between an idle unit's looks for a target now: 4 under {@link #legacyScans}. */
    public static int idleScanEvery() {
        return legacyScans ? 4 : Math.max(4, idleAggroTicks);
    }

    /**
     * Is this idle unit's look for a target due on this pass of AttackerUnit's 4-tick block (tickCount % 4 == 0)?
     * Every pass for a unit in combat; otherwise one pass in idleScanEvery() / 4, the phase set by the entity id, so
     * units that spawned on the same tick still spread over the ticks (their tickCount phase is the same).
     */
    public static boolean idleScanDue(Mob mob) {
        int passes = idleScanEvery() / 4;
        if (passes <= 1 || inCombat(mob))
            return true;
        return Math.floorMod((mob.tickCount >> 2) + mob.getId(), passes) == 0;
    }

    private static void pruneEngaged(long now) {
        ObjectIterator<Int2LongMap.Entry> it = ENGAGED.int2LongEntrySet().fastIterator();
        while (it.hasNext())
            if (now - it.next().getLongValue() > COMBAT_MEMORY_TICKS)
                it.remove();
    }

    // ------------------------------------------------------------------------------------------------- tests

    /** Per watched entity id: {scans stopped at the masks, scans that gathered}. Empty in play: one isEmpty() per scan. */
    private static final Int2ObjectOpenHashMap<long[]> PROBES = new Int2ObjectOpenHashMap<>();

    private static void probe(Mob unitMob, boolean gathered) {
        if (PROBES.isEmpty())
            return;
        long[] c = PROBES.get(unitMob.getId());
        if (c != null)
            c[gathered ? 1 : 0]++;
    }

    /** Count this entity's scans from now on (a GameTest); returns the live {skipped, gathered} counters. */
    public static long[] watch(Entity e) {
        long[] c = PROBES.get(e.getId());
        if (c == null) {
            c = new long[2];
            PROBES.put(e.getId(), c);
        }
        return c;
    }

    public static void unwatch(Entity e) {
        PROBES.remove(e.getId());
    }

    /** Sides on the level at the last rebuild (a GameTest's diagnostics: past SIDE_BITS nothing is friendly). */
    public static int sides() {
        return SIDES.size();
    }

    /** Rebuilds since start, and entries in the grid at the last one (the [Stress] line). */
    public static int rebuilds() {
        return rebuilds;
    }

    public static int cellEntries() {
        return cellEntries;
    }

    /** Forces a rebuild on the next scan (a test that just moved entities by hand). */
    public static void invalidate() {
        builtAt = Long.MIN_VALUE;
    }

    // ------------------------------------------------------------------------------------------------- events

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent evt) {
        if (!evt.getLevel().isClientSide() && evt.getEntity() instanceof LivingEntity)
            joins++;
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent evt) {
        CELLS.clear();
        MASKS.clear();
        SIDES.clear();
        Arrays.fill(SIDE_NAMES, null);
        ENGAGED.clear();
        PROBES.clear();
        builtFor = null;
        builtAt = Long.MIN_VALUE;
        legacyScans = false;
        idleAggroTicks = AttackerUnit.IDLE_AGGRO_TICKS;
    }
}
