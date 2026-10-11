package com.solegendary.reignofnether.mana;

import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.blocks.FrostSheetBlock;
import com.solegendary.reignofnether.building.BuildingUtils;
import com.solegendary.reignofnether.registrars.BlockRegistrar;
import com.solegendary.reignofnether.resources.MetalPatches;
import com.solegendary.reignofnether.unit.UnitGrid;
import com.solegendary.reignofnether.unit.interfaces.Unit;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Stormcallers <b>frost sheets</b> (design/stormcallers_plan.md 3.7) - the Frost Adept's Frost Field: a disc of
 * {@link FrostSheetBlock} cells (a skin of ice, no collision) that slows enemy units standing on it and melts away after
 * its lifetime. A trimmed copy of tide/TidepoolServerEvents' shape, so it inherits its safety rules:
 * <ul>
 *   <li><b>Placement</b> ({@link #raise}): per column the first <b>air</b> block (scanning centre.y+2 down to centre.y-2)
 *       on a floor with a sturdy top face and no fluid. Never replaces anything, never on a metal patch, never inside a
 *       building. No valid cell, or over {@link #MAX_CELLS_PER_LEVEL}: refused (null).</li>
 *   <li><b>Caps</b>: {@link #FIELDS_PER_PLAYER} live fields per owner (one more evicts the owner's oldest at once) and
 *       {@link #MAX_CELLS_PER_LEVEL} cells per level.</li>
 *   <li><b>Expiry</b>: once a second ({@link #EXPIRY_PASS_TICKS}). A cell is only removed while it is still a sheet, and
 *       each carries a scheduled fail-safe block tick (saved with the chunk) at lifetime + {@link #FAILSAFE_MARGIN_TICKS},
 *       so a field this (unsaved) registry forgot still melts: no permanent terrain change.</li>
 *   <li><b>Slow</b>: every {@link #SLOW_PASS_TICKS} ticks, one grid query per field; an enemy ground unit whose feet are
 *       in a cell of the field gets Slowness I for a little longer than the pass (topped up only when it runs low - no
 *       effect packet per pass for a unit that is already slowed). Friends and allies walk it freely.</li>
 *   <li><b>Counterplay</b>: fire or lava next to a cell melts it (FrostSheetBlock); placing a building or block over it
 *       fills it. Cleared on defeat ({@link #clearOwner}) and reset ({@link #clearAll}).</li>
 * </ul>
 * Pathing: a no-collision cell classifies as plain land in WalkabilityBuilder, so a stale chunk can never strand a unit.
 */
public final class FrostServerEvents {
    private FrostServerEvents() { }

    public static final int MAX_RADIUS = 3;
    public static final int FIELDS_PER_PLAYER = 3;
    public static final int MAX_CELLS_PER_LEVEL = 600;
    public static final int EXPIRY_PASS_TICKS = 20;
    public static final int SLOW_PASS_TICKS = 10;
    public static final int SLOW_TICKS = 15;
    public static final int FAILSAFE_MARGIN_TICKS = 5 * 20;
    public static final int SURFACE_SEARCH = 2;
    public static final String REFUSE_NO_GROUND = "hud.mana.reignofnether.frost_no_ground";

    /** Read-only handle to one field; {@link #isActive()} turns false once it has melted. */
    public static final class Field {
        public final String owner;
        public final ServerLevel level;
        public final BlockPos centre;
        public final int radius;
        final long[] cells;
        final long expireTick;
        boolean removed = false;

        Field(String owner, ServerLevel level, BlockPos centre, int radius, long[] cells, long expireTick) {
            this.owner = owner;
            this.level = level;
            this.centre = centre;
            this.radius = radius;
            this.cells = cells;
            this.expireTick = expireTick;
        }

        public boolean isActive() { return !removed; }
        public int cellCount() { return cells.length; }
        public long ticksLeft() { return removed ? 0 : Math.max(0, expireTick - level.getGameTime()); }

        /** The laid cell positions (allocates; game test only). */
        public List<BlockPos> positions() {
            List<BlockPos> out = new ArrayList<>(cells.length);
            for (long c : cells)
                out.add(BlockPos.of(c));
            return out;
        }
    }

    /** Live fields, oldest first (the first of an owner's is the one to evict). */
    private static final List<Field> FIELDS = new ArrayList<>();
    private static final Map<ResourceKey<Level>, Long2ObjectOpenHashMap<Field>> CELLS = new HashMap<>();
    private static String lastRefusal = null;

    // reused (server thread only)
    private static final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
    private static final BlockPos.MutableBlockPos below = new BlockPos.MutableBlockPos();
    private static final List<LivingEntity> scratch = new ArrayList<>();

    /** Lays a field; see the class comment. Returns null if refused ({@link #lastRefusal}). */
    public static Field raise(ServerLevel level, String owner, BlockPos centre, int radius, int lifetimeTicks) {
        lastRefusal = null;
        if (level == null || centre == null || owner == null || owner.isBlank() || lifetimeTicks <= 0) {
            lastRefusal = REFUSE_NO_GROUND;
            return null;
        }
        radius = Math.max(0, Math.min(MAX_RADIUS, radius));
        LongArrayList found = collectCells(level, centre, radius);
        if (found.isEmpty()) {
            lastRefusal = REFUSE_NO_GROUND;
            return null;
        }
        Field evict = null;
        int count = 0;
        for (Field f : FIELDS) {
            if (!f.owner.equals(owner))
                continue;
            if (evict == null)
                evict = f;
            count++;
        }
        if (count < FIELDS_PER_PLAYER)
            evict = null;
        int freed = evict != null && evict.level == level ? liveCellsOf(evict) : 0;
        if (cellsIn(level) - freed + found.size() > MAX_CELLS_PER_LEVEL) {
            lastRefusal = REFUSE_NO_GROUND;
            return null;
        }
        if (evict != null)
            remove(evict);

        FrostSheetBlock block = BlockRegistrar.FROST_SHEET.get();
        BlockState state = block.defaultBlockState();
        LongArrayList placed = new LongArrayList(found.size());
        for (int i = 0; i < found.size(); i++) {
            long c = found.getLong(i);
            cursor.set(c);
            if (!level.setBlock(cursor, state, Block.UPDATE_ALL) || !level.getBlockState(cursor).is(block))
                continue;   // refused, or melted at once by fire beside it
            level.scheduleTick(cursor.immutable(), block, lifetimeTicks + FAILSAFE_MARGIN_TICKS);
            placed.add(c);
        }
        if (placed.isEmpty()) {
            lastRefusal = REFUSE_NO_GROUND;
            return null;
        }
        Field field = new Field(owner, level, centre.immutable(), radius, placed.toLongArray(), level.getGameTime() + lifetimeTicks);
        FIELDS.add(field);
        Long2ObjectOpenHashMap<Field> map = CELLS.computeIfAbsent(level.dimension(), k -> new Long2ObjectOpenHashMap<>());
        for (long c : field.cells)
            map.put(c, field);
        level.sendParticles(ParticleTypes.SNOWFLAKE, centre.getX() + 0.5, centre.getY() + 0.4, centre.getZ() + 0.5,
                10 + radius * 10, radius * 0.6 + 0.3, 0.2, radius * 0.6 + 0.3, 0.05);
        return field;
    }

    /** The cells a field at this spot would cover (empty if none). Public for the game test. */
    public static LongArrayList collectCells(ServerLevel level, BlockPos centre, int radius) {
        LongArrayList out = new LongArrayList();
        int r2 = radius * radius + radius;   // a rounder disc than r^2 at small radii (r2 = 13 cells)
        for (int dx = -radius; dx <= radius; dx++)
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx * dx + dz * dz > r2)
                    continue;
                int x = centre.getX() + dx, z = centre.getZ() + dz;
                for (int y = centre.getY() + SURFACE_SEARCH; y >= centre.getY() - SURFACE_SEARCH; y--) {
                    cursor.set(x, y, z);
                    if (!level.isLoaded(cursor))
                        break;
                    BlockState at = level.getBlockState(cursor);
                    if (!at.isAir())
                        continue;
                    below.set(x, y - 1, z);
                    BlockState floor = level.getBlockState(below);
                    if (!floor.isFaceSturdy(level, below, Direction.UP) || !floor.getFluidState().isEmpty())
                        continue;
                    if (!floor.is(MetalPatches.PATCH_BLOCK) && !BuildingUtils.isPosInsideAnyBuilding(false, cursor))
                        out.add(cursor.asLong());
                    break;
                }
            }
        return out;
    }

    /** Melts a field now: every cell it laid that is still a sheet turns back to air. */
    static void remove(Field field) {
        if (field.removed)
            return;
        field.removed = true;
        FIELDS.remove(field);
        Long2ObjectOpenHashMap<Field> map = CELLS.get(field.level.dimension());
        for (long c : field.cells) {
            Field owner = map == null ? null : map.get(c);
            if (owner != null && owner != field)
                continue;   // another field has since been laid over this cell
            if (owner != null)
                map.remove(c);
            cursor.set(c);
            if (field.level.isLoaded(cursor) && field.level.getBlockState(cursor).getBlock() instanceof FrostSheetBlock)
                field.level.setBlock(cursor, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
    }

    /** Fire melts one cell (FrostSheetBlock calls this when fire or lava touches it). */
    public static void meltCell(ServerLevel level, BlockPos pos) {
        Long2ObjectOpenHashMap<Field> map = CELLS.get(level.dimension());
        if (map != null)
            map.remove(pos.asLong());
        if (level.getBlockState(pos).getBlock() instanceof FrostSheetBlock) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            level.levelEvent(LevelEvent.LAVA_FIZZ, pos, 0);
        }
    }

    /** The live field that laid this exact cell, or null. */
    public static Field fieldAt(Level level, BlockPos pos) {
        Long2ObjectOpenHashMap<Field> map = CELLS.get(level.dimension());
        if (map == null)
            return null;
        Field f = map.get(pos.asLong());
        return f == null || f.removed ? null : f;
    }

    public static long ticksLeftForCell(ServerLevel level, BlockPos pos) {
        Field f = fieldAt(level, pos);
        return f == null ? 0 : f.ticksLeft();
    }

    public static int cellsIn(Level level) {
        Long2ObjectOpenHashMap<Field> map = CELLS.get(level.dimension());
        return map == null ? 0 : map.size();
    }

    public static int fieldCount(String owner) {
        int n = 0;
        for (Field f : FIELDS)
            if (f.owner.equals(owner))
                n++;
        return n;
    }

    static int liveCellsOf(Field field) {
        Long2ObjectOpenHashMap<Field> map = CELLS.get(field.level.dimension());
        if (map == null)
            return 0;
        int n = 0;
        for (long c : field.cells)
            if (map.get(c) == field)
                n++;
        return n;
    }

    public static String lastRefusal() {
        return lastRefusal;
    }

    public static void clearOwner(String owner) {
        if (owner == null)
            return;
        for (Field f : new ArrayList<>(FIELDS))
            if (f.owner.equals(owner))
                remove(f);
    }

    public static void clearAll() {
        for (Field f : new ArrayList<>(FIELDS))
            remove(f);
    }

    // ------------------------------------------------------------------ ticking

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent evt) {
        if (evt.phase != TickEvent.Phase.END || evt.getServer() == null || FIELDS.isEmpty())
            return;
        int t = evt.getServer().getTickCount();
        boolean slow = t % SLOW_PASS_TICKS == 0, expiry = t % EXPIRY_PASS_TICKS == 0;
        if (!slow && !expiry)
            return;
        for (ServerLevel level : evt.getServer().getAllLevels()) {
            if (expiry)
                tickExpiry(level);
            if (slow)
                slowPass(level);
        }
    }

    /** One expiry pass for a level. Public for the game test. */
    public static void tickExpiry(ServerLevel level) {
        long now = level.getGameTime();
        for (int i = FIELDS.size() - 1; i >= 0; i--) {
            Field f = FIELDS.get(i);
            if (f.level == level && now >= f.expireTick)
                remove(f);
        }
    }

    /** One slow pass for a level: enemies standing on a field are slowed. Returns how many were. Public for the game test. */
    public static int slowPass(ServerLevel level) {
        int n = 0;
        Long2ObjectOpenHashMap<Field> map = CELLS.get(level.dimension());
        if (map == null || map.isEmpty())
            return 0;
        for (int i = 0; i < FIELDS.size(); i++) {
            Field f = FIELDS.get(i);
            if (f.level != level || f.removed)
                continue;
            for (LivingEntity le : UnitGrid.near(level, f.centre.getX() + 0.5, f.centre.getZ() + 0.5, f.radius + 1.5, scratch)) {
                if (!le.isAlive() || !(le instanceof Unit u) || u.isFlyingUnit() || map.get(le.blockPosition().asLong()) != f)
                    continue;
                String o = u.getOwnerName();
                if (o == null || o.equals(f.owner) || AlliancesServerEvents.isAllied(f.owner, o))
                    continue;
                MobEffectInstance cur = le.getEffect(MobEffects.MOVEMENT_SLOWDOWN);
                if (cur == null || (cur.getAmplifier() == 0 && cur.getDuration() < SLOW_PASS_TICKS))
                    le.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, SLOW_TICKS, 0, false, true));
                n++;
            }
            scratch.clear();
        }
        return n;
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent evt) {
        // blocks stay; their saved fail-safe ticks melt them when the world is next loaded
        FIELDS.clear();
        CELLS.clear();
        lastRefusal = null;
    }
}
