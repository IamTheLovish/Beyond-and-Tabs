package com.solegendary.reignofnether.barfx;

import com.solegendary.reignofnether.fogofwar.FogOfWarServerEvents;
import com.solegendary.reignofnether.network.Audience;
import com.solegendary.reignofnether.registrars.PacketHandler;
import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Server side of the BAR-style battle effects. Gameplay code calls the static helpers below whenever something
 * worth showing happens (a shot, an impact, an explosion, a death, a building part breaking); events are queued per
 * level and sent once per tick to each nearby player as one {@link BarFxClientboundPacket}. Everything visual is
 * done on the client in {@code BarFxClient}. Safe to call from anywhere: client-side calls are ignored.
 */
public final class BarFx {
    private BarFx() { }

    // ---- event types
    public static final byte SHOT = 0, IMPACT = 1, EXPLOSION = 2, DEATH = 3, BUILDING_PART = 4, COLLAPSE = 5, NANO = 6,
            SCORCH = 7, SFX = 8, DOME = 9;

    // ---- DOME (a barrier drawn as geometry, barfx/DomeFx): flags low 2 bits = what happened, bit 4 = it follows the
    // entity whose id is the dome id; kind = style; a = radius, b = lifetime in ticks, c = the dome id (float-exact)
    public static final byte DOME_START = 0, DOME_HIT = 1, DOME_END = 2, DOME_BROKEN = 3;
    public static final byte DOME_FOLLOW = 0x10;
    public static final byte DS_SUN = 0, DS_BRASS = 1, DS_LANTERN = 2;
    /** Live domes are announced again this often: a late viewer sees them, and a channelled one stays alive. */
    public static final int DOME_REFRESH_TICKS = 40;
    private static int domeCounter = 0;

    // ---- SHOT flags, low 4 bits: the weapon's sound class where the projectile kind alone can't tell (a musket's
    // hit-scan shot reuses K_ARROW's tracer look but must not sound like a bow). 0 = by kind. Bits 4-6 = the shooter's
    // faction (F_*), see shotFlags: the client tints flash, trail and sparks with it while the kind keeps the shape.
    public static final byte W_BY_KIND = 0, W_GUN = 1;

    // ---- SFX flags (sounds.Sfx): bit 0 = global (every player, no position)
    public static final byte SFX_GLOBAL = 1;

    // ---- nanolathe beam tints (NANO kind): one per faction so a glance tells whose builders are at work
    // (N_NEUTRAL: factions without their own tint - they used to borrow Sunforged gold; see FactionTraits)
    public static final byte N_SUNFORGED = 0, N_GRAVEBOUND = 1, N_HORDE = 2, N_NEUTRAL = 3, N_VERDANT = 4, N_TIDE = 5,
            N_STORM = 6, N_BRASS = 7;

    /** Minimum ticks between two nano beams from the same worker (a beam lives a little longer, so they overlap). */
    public static final int NANO_INTERVAL = 8;

    // ---- weapon / projectile kinds (shots and impacts)
    public static final byte K_ARROW = 0, K_FIREBALL = 1, K_BIG_FIREBALL = 2, K_MAGIC = 3, K_THROWN = 4, K_TNT = 5,
            K_ROCKET = 6, K_SONIC = 7, K_MELEE = 8, K_POTION = 9, K_HEAVY_MELEE = 10, K_OTHER = 11;

    // ---- impact "where" flags (low 4 bits; bits 4-6 = the shooter's faction, see shotFlags)
    public static final byte AT_GROUND = 0, AT_UNIT = 1, AT_BUILDING = 2;

    // ---- death kinds
    public static final byte D_FLESH = 0, D_CONSTRUCT = 1, D_BONE = 2, D_SLIME = 3;

    // ---- death flags: low 3 bits = faction debris tint, next 2 bits = cost tier (0 = not an RTS unit).
    // The faction field was 2 bits until the Verdant Court needed a fifth value (F_NONE + three factions filled it);
    // 3 bits leave room for factions 5-7 of design-factions.md (the Tidewrought took 5, the Stormcallers 6, the
    // Brassworks 7: the field is full - an eighth faction widens FACTION_BITS). The flags byte still has 3 spare bits.
    public static final byte F_NONE = 0, F_SUNFORGED = 1, F_GRAVEBOUND = 2, F_HORDE = 3, F_VERDANT = 4, F_TIDE = 5,
            F_STORM = 6, F_BRASS = 7;
    public static final int FACTION_BITS = 3, FACTION_MASK = (1 << FACTION_BITS) - 1;

    /** Packs a death event's flags: faction debris tint (F_*, 0-7) and cost tier (0-3). Public for the game test. */
    public static byte deathFlags(byte faction, int tier) {
        return (byte) ((faction & FACTION_MASK) | ((Math.max(0, Math.min(3, tier)) & 3) << FACTION_BITS));
    }

    /** The faction debris tint (F_*) of packed death flags. */
    public static int deathFaction(byte flags) {
        return flags & FACTION_MASK;
    }

    /** The cost tier (0-3) of packed death flags. */
    public static int deathTier(byte flags) {
        return (flags >> FACTION_BITS) & 3;
    }

    // ---- SHOT / IMPACT flags: low 4 bits = the detail the type already carried (W_* weapon class for a shot, AT_*
    // "where" for an impact), bits 4-6 = the shooter's faction (F_*, same ids as the death field) so the client can
    // tint the effect; bit 7 spare. The faction rides in bits the packet already sent, so nothing grew.
    static final int SHOT_FACTION_SHIFT = 4, SHOT_DETAIL_MASK = (1 << SHOT_FACTION_SHIFT) - 1;

    /** Packs a shot's or impact's flags: detail (W_* or AT_*, 0-15) and the shooter's faction (F_*, 0-7). */
    public static byte shotFlags(byte detail, byte faction) {
        return (byte) ((detail & SHOT_DETAIL_MASK) | ((faction & FACTION_MASK) << SHOT_FACTION_SHIFT));
    }

    /** The detail (W_* for SHOT, AT_* for IMPACT) of packed shot / impact flags. */
    public static int shotDetail(byte flags) {
        return flags & SHOT_DETAIL_MASK;
    }

    /** The shooter's faction (F_*) of packed shot / impact flags; F_NONE = untinted (today's kind colour). */
    public static int shotFaction(byte flags) {
        return (flags >> SHOT_FACTION_SHIFT) & FACTION_MASK;
    }

    public static byte impactFlags(byte where, boolean metal, byte faction) {
        return 0;
    }

    public static int impactWhere(byte flags) {
        return 0;
    }

    public static boolean impactMetal(byte flags) {
        return false;
    }

    /**
     * The F_* faction of whoever fired: an RTS unit's faction (by entity type, like its death debris), a projectile's
     * owner's, else F_NONE (players, wild mobs, towers without a known owner). Works on both sides; never throws
     * and allocates nothing.
     */
    public static byte factionOf(Entity shooter) {
        try {
            if (shooter instanceof net.minecraft.world.entity.projectile.Projectile p)
                shooter = p.getOwner();
            if (shooter instanceof com.solegendary.reignofnether.unit.interfaces.Unit u)
                return com.solegendary.reignofnether.faction.FactionTraits.of(
                    com.solegendary.reignofnether.faction.Factions.getFaction(u)).deathDebris;
        } catch (Exception ignored) { }
        return F_NONE;
    }

    /** Metal cost from which a dying unit counts as T2 / T3 for its death blast (T1 below). */
    public static final int T2_METAL = 120, T3_METAL = 500;

    /** Cost tier 1-3 from a unit's metal cost (death blast size). */
    public static int tierOf(int metal) {
        return metal >= T3_METAL ? 3 : metal >= T2_METAL ? 2 : 1;
    }

    /** One effect event. Meaning of a/b/c depends on the type (see the emit helpers). */
    public static final class Event {
        public final byte type, kind, flags;
        public final float x, y, z, a, b, c;

        public Event(byte type, byte kind, byte flags, float x, float y, float z, float a, float b, float c) {
            this.type = type; this.kind = kind; this.flags = flags;
            this.x = x; this.y = y; this.z = z; this.a = a; this.b = b; this.c = c;
        }
    }

    static final int MAX_PER_TICK = 160;
    static final double SEND_RANGE = 200;
    private static final Map<ResourceKey<Level>, List<Event>> QUEUE = new HashMap<>();

    /** While > 0, explosions are not reported (a building collapse already reports one big effect). */
    static int muteExplosions = 0;

    public static void muteExplosions(boolean mute) {
        muteExplosions = Math.max(0, muteExplosions + (mute ? 1 : -1));
    }

    static void emit(Level level, Event e) {
        if (!(level instanceof ServerLevel sl) || e == null)
            return;
        if (!Float.isFinite(e.x) || !Float.isFinite(e.y) || !Float.isFinite(e.z))
            return;
        List<Event> q = QUEUE.computeIfAbsent(sl.dimension(), k -> new ArrayList<>());
        if (q.size() >= MAX_PER_TICK) {
            // when flooded, keep the big stuff and drop small shots/hits/nano beams
            if (isMinor(e.type))
                return;
            for (int i = 0; i < q.size(); i++) {
                if (isMinor(q.get(i).type)) {
                    q.set(i, e);
                    return;
                }
            }
            return;
        }
        q.add(e);
    }

    /**
     * A sound effect (sounds.Sfx ordinal in the kind byte, a = volume, b = pitch). Positional ones join the per-tick
     * queue like any other event; global ones go to every player of the server at once.
     */
    public static void sfx(ServerLevel level, double x, double y, double z, int id, float volume, float pitch, boolean global) {
        if (level == null)
            return;
        Event e = new Event(SFX, (byte) id, global ? SFX_GLOBAL : 0, (float) x, (float) y, (float) z, volume, pitch, 0);
        if (!global) {
            emit(level, e);
            return;
        }
        try {
            PacketHandler.INSTANCE.send(Audience.all(), new BarFxClientboundPacket(List.of(e))); // match moments, by design
        } catch (Exception ignored) { }
    }

    /** Cosmetic chatter that may be dropped first when a tick is flooded. */
    private static boolean isMinor(byte type) {
        return type == SHOT || type == IMPACT || type == NANO;
    }

    // ------------------------------------------------------------------ emit helpers

    /** A fresh id for an anchored dome: negative, so it can never collide with an entity id (a following dome's). */
    public static int nextDomeId() {
        domeCounter = (domeCounter + 1) & 0x7FFFFF;   // stays float-exact
        return -domeCounter - 1;
    }

    /**
     * A barrier rises: a hemisphere of {@code radius} at {@code centre} for {@code ticks} (DomeFx draws it, rim, fill,
     * shimmer). {@code follow}: the id is an entity id and the dome rides on that entity.
     */
    public static void dome(Level level, int id, Vec3 centre, float radius, int ticks, byte style, boolean follow) {
        emit(level, new Event(DOME, style, (byte) (DOME_START | (follow ? DOME_FOLLOW : 0)), (float) centre.x, (float) centre.y,
            (float) centre.z, radius, ticks, id));
    }

    /** Something struck the barrier at {@code at} (on its skin): a ripple spreads from there. */
    public static void domeHit(Level level, int id, Vec3 at, byte style) {
        emit(level, new Event(DOME, style, DOME_HIT, (float) at.x, (float) at.y, (float) at.z, 0, 0, id));
    }

    /** The barrier goes down: {@code broken} = shattered by damage (a flash), else it simply fades. */
    public static void domeEnd(Level level, int id, Vec3 centre, byte style, boolean broken) {
        emit(level, new Event(DOME, style, broken ? DOME_BROKEN : DOME_END, (float) centre.x, (float) centre.y, (float) centre.z, 0, 0, id));
    }

    /** A weapon fires from 'from' towards 'to'. */
    public static void shot(Level level, Vec3 from, Vec3 to, byte kind) {
        shot(level, from, to, kind, W_BY_KIND);
    }

    /** Same, with a weapon sound class (W_*) for shots whose kind doesn't say how they sound. */
    public static void shot(Level level, Vec3 from, Vec3 to, byte kind, byte weapon) {
        shot(level, from, to, kind, weapon, F_NONE);
    }

    /** A unit fires: its faction (factionOf) tints the flash and sparks on the client. */
    public static void shot(Level level, Vec3 from, Vec3 to, byte kind, Entity shooter) {
        shot(level, from, to, kind, W_BY_KIND, factionOf(shooter));
    }

    /** Same, with both the weapon sound class and the shooter. */
    public static void shot(Level level, Vec3 from, Vec3 to, byte kind, byte weapon, Entity shooter) {
        shot(level, from, to, kind, weapon, factionOf(shooter));
    }

    /** Same, with the shooter's faction (F_*) spelled out; F_NONE = untinted. */
    public static void shot(Level level, Vec3 from, Vec3 to, byte kind, byte weapon, byte faction) {
        if (level == null || level.isClientSide() || from == null || to == null) return;
        emit(level, new Event(SHOT, kind, shotFlags(weapon, faction), (float) from.x, (float) from.y, (float) from.z,
                (float) to.x, (float) to.y, (float) to.z));
    }

    /** Something lands at pos travelling along dir (may be null). size ~1 for a normal hit. */
    public static void impact(Level level, Vec3 pos, Vec3 dir, byte kind, byte where, float size) {
        impact(level, pos, dir, kind, where, size, F_NONE);
    }

    /** Same, naming who shot (factionOf tints the sparks on the client). */
    public static void impact(Level level, Vec3 pos, Vec3 dir, byte kind, byte where, float size, Entity shooter) {
        impact(level, pos, dir, kind, where, size, factionOf(shooter));
    }

    /** Same, with the shooter's faction (F_*) spelled out; F_NONE = untinted. */
    public static void impact(Level level, Vec3 pos, Vec3 dir, byte kind, byte where, float size, byte faction) {
        if (level == null || level.isClientSide() || pos == null) return;
        float dx = 0, dz = 0;
        if (dir != null) {
            double l = Math.sqrt(dir.x * dir.x + dir.z * dir.z);
            if (l > 1e-4) { dx = (float) (dir.x / l); dz = (float) (dir.z / l); }
        }
        emit(level, new Event(IMPACT, kind, shotFlags(where, faction), (float) pos.x, (float) pos.y, (float) pos.z, dx, size, dz));
        // a world that remembers (worldmemory/): a shell on open ground burns it for the match, not the client's 12 s
        if (where == AT_GROUND)
            remember(level, m -> m.groundImpact((ServerLevel) level, pos.x, pos.y, pos.z, kind, size));
    }

    /** An explosion of the given radius. kind: K_FIREBALL / K_BIG_FIREBALL / K_TNT / K_MAGIC / K_OTHER. */
    public static void explosion(Level level, Vec3 pos, float radius, byte kind) {
        if (level == null || level.isClientSide() || pos == null || muteExplosions > 0) return;
        float r = Math.max(.5f, Math.min(radius, 16f));
        emit(level, new Event(EXPLOSION, kind, (byte) 0, (float) pos.x, (float) pos.y, (float) pos.z, r, 0, 0));
        remember(level, m -> m.blast((ServerLevel) level, pos.x, pos.y, pos.z, r, kind));
    }

    /**
     * The ground memory stamp that goes with an effect (worldmemory/GroundMemoryServer): the server's field keeps the
     * scorch and crater for the match while the client's particles do the first seconds. Never lets a stamp break an
     * effect, or the gameplay call behind it.
     */
    private static void remember(Level level, java.util.function.Consumer<GroundMemoryServerCalls> stamp) {
        if (!(level instanceof ServerLevel))
            return;
        try {
            stamp.accept(GroundMemoryServerCalls.INSTANCE);
        } catch (Exception e) {
            if (!memoryFailed) {
                memoryFailed = true;
                com.solegendary.reignofnether.ReignOfNether.LOGGER.error("World memory stamp failed", e);
            }
        }
    }

    private static boolean memoryFailed = false;

    /** The stamps, behind one object so the call sites above read as what happened (and a test can swap it). */
    public static class GroundMemoryServerCalls {
        public static GroundMemoryServerCalls INSTANCE = new GroundMemoryServerCalls();

        public void groundImpact(ServerLevel level, double x, double y, double z, byte kind, float size) {
            com.solegendary.reignofnether.worldmemory.GroundMemoryServer.groundImpact(level, x, y, z, kind, size);
        }

        public void blast(ServerLevel level, double x, double y, double z, float radius, byte kind) {
            com.solegendary.reignofnether.worldmemory.GroundMemoryServer.blast(level, x, y, z, radius, kind);
        }

        public void heavyImpact(ServerLevel level, double x, double y, double z, float radius) {
            com.solegendary.reignofnether.worldmemory.GroundMemoryServer.heavyImpact(level, x, y, z, radius);
        }

        public void death(ServerLevel level, double x, double y, double z, byte kind, int tier, float width) {
            com.solegendary.reignofnether.worldmemory.GroundMemoryServer.death(level, x, y, z, kind, tier, width);
        }

        public void collapse(ServerLevel level, double x, double y, double z, float halfSize) {
            com.solegendary.reignofnether.worldmemory.GroundMemoryServer.collapse(level, x, y, z, halfSize);
        }
    }

    /** A unit / mob dies. width & height are its bounding box. */
    public static void death(Level level, Vec3 pos, byte kind, float width, float height) {
        death(level, pos, kind, width, height, F_NONE, 0);
    }

    /**
     * An RTS unit dies: faction (F_*) picks the debris tint, tier (1-3, from its metal cost) the size of the blast.
     * tier 0 = a plain mob (the old untinted puff).
     */
    public static void death(Level level, Vec3 pos, byte kind, float width, float height, byte faction, int tier) {
        if (level == null || level.isClientSide() || pos == null) return;
        emit(level, new Event(DEATH, kind, deathFlags(faction, tier), (float) pos.x, (float) pos.y, (float) pos.z, width, height, 0));
        remember(level, m -> m.death((ServerLevel) level, pos.x, pos.y, pos.z, kind, tier, width));
    }

    /**
     * A heavy weapon lands (artillery shell, T3 ability): the client leaves a ring of dark smoke and a scorch mark
     * that linger ~2 s. radius = the blast's reach in blocks.
     */
    public static void heavyImpact(Level level, Vec3 pos, float radius) {
        if (level == null || level.isClientSide() || pos == null) return;
        float r = Math.max(.75f, Math.min(radius, 8f));
        emit(level, new Event(SCORCH, (byte) 0, (byte) 0, (float) pos.x, (float) pos.y, (float) pos.z, r, 0, 0));
        remember(level, m -> m.heavyImpact((ServerLevel) level, pos.x, pos.y, pos.z, r));
    }

    /** One block of a building is knocked out (state = the block before it was removed, may be null). */
    public static void buildingPart(Level level, BlockPos bp, BlockState state) {
        if (level == null || level.isClientSide() || bp == null) return;
        int col = 0x8A7A68;
        try {
            if (state != null && !state.isAir()) {
                int c = state.getMapColor(level, bp).col;
                if (c != 0) col = c;
            }
        } catch (Exception ignored) { }
        emit(level, new Event(BUILDING_PART, (byte) 0, (byte) 0, bp.getX() + .5f, bp.getY() + .5f, bp.getZ() + .5f,
                (float) (col & 0xFFFFFF), 0, 0));
    }

    /** A whole building is destroyed: centre of its base, horizontal half size and height. */
    public static void collapse(Level level, Vec3 centre, float halfSize, float height) {
        collapse(level, centre, halfSize, height, true);
    }

    /** Same; shake = false skips the collapse's gentle camera shake (flags bit 0; the commander blast uses it). */
    public static void collapse(Level level, Vec3 centre, float halfSize, float height, boolean shake) {
        collapse(level, centre, halfSize, height, shake, F_NONE);
    }

    /**
     * Same, naming whose building it was (F_*, flags bits 1-3) so the client plays that faction's destruction sound;
     * F_NONE (the commander blast) plays the generic big blast.
     */
    public static void collapse(Level level, Vec3 centre, float halfSize, float height, boolean shake, byte faction) {
        if (level == null || level.isClientSide() || centre == null) return;
        emit(level, new Event(COLLAPSE, (byte) 0, (byte) ((shake ? 0 : 1) | ((faction & FACTION_MASK) << 1)),
                (float) centre.x, (float) centre.y, (float) centre.z, Math.max(1, halfSize), Math.max(1, height), 0));
        float half = Math.max(1, halfSize);
        remember(level, m -> m.collapse((ServerLevel) level, centre.x, centre.y, centre.z, half));
    }

    /** The faction (F_*) packed into a COLLAPSE event's flags. */
    public static int collapseFaction(byte flags) {
        return (flags >> 1) & FACTION_MASK;
    }

    /**
     * Last game time each worker (by entity id) sent a nano beam. This is the per-worker rate limit: with 100+ workers
     * building, a beam every {@link #NANO_INTERVAL} ticks each is ~12 tiny events a tick, well under MAX_PER_TICK.
     * Entity ids are unique for the whole server run, so one map serves every level.
     */
    private static final Int2LongOpenHashMap LAST_NANO = new Int2LongOpenHashMap();

    /**
     * A worker's nanolathe beam from its hands to (tx,ty,tz): it is building, repairing or reclaiming there.
     * Rate limited per worker to one beam every {@code interval} ticks; the client keeps it alive for
     * {@code lifeTicks}. Returns true if a beam was queued.
     */
    public static boolean nano(LivingEntity worker, double tx, double ty, double tz, int interval, int lifeTicks) {
        if (worker == null || !(worker.level() instanceof ServerLevel sl))
            return false;
        long now = sl.getGameTime();
        int id = worker.getId();
        long last = LAST_NANO.getOrDefault(id, Long.MIN_VALUE);
        if (last != Long.MIN_VALUE && now >= last && now - last < interval)   // (now < last: time was set back)
            return false;
        if (LAST_NANO.size() > 8192)   // dead workers are never removed one by one; a rare reset is cheaper
            LAST_NANO.clear();
        LAST_NANO.put(id, now);
        // hands: a bit in front of the body at chest height, so the beam leaves the worker rather than its feet
        float yaw = worker.yBodyRot * ((float) Math.PI / 180f);
        double reach = worker.getBbWidth() * .5 + .15;
        double hx = worker.getX() - Math.sin(yaw) * reach;
        double hy = worker.getY() + worker.getBbHeight() * .55;
        double hz = worker.getZ() + Math.cos(yaw) * reach;
        byte tint = N_SUNFORGED;   // non-unit workers keep the old golden beam
        try {
            if (worker instanceof com.solegendary.reignofnether.unit.interfaces.Unit u)
                tint = com.solegendary.reignofnether.faction.FactionTraits.of(
                    com.solegendary.reignofnether.faction.Factions.getFaction(u)).nanoTint;
        } catch (Exception ignored) { }
        emit(sl, new Event(NANO, tint, (byte) Math.max(1, Math.min(lifeTicks, 100)),
                (float) hx, (float) hy, (float) hz, (float) tx, (float) ty, (float) tz));
        return true;
    }

    /**
     * A thin steady beam from {@code from} to {@code to} in one of the nano tints (N_*), kept alive by the client for
     * {@code lifeTicks} (max 100): the nanolathe's look reused for standing links such as the Stormcallers' ley links.
     * Not rate limited here - callers send it sparsely (LeyNetwork: once per link every 2 s).
     */
    public static void beam(Level level, Vec3 from, Vec3 to, byte tint, int lifeTicks) {
        if (level == null || level.isClientSide() || from == null || to == null) return;
        emit(level, new Event(NANO, tint, (byte) Math.max(1, Math.min(lifeTicks, 100)),
                (float) from.x, (float) from.y, (float) from.z, (float) to.x, (float) to.y, (float) to.z));
    }

    /** Game time of the worker's last nano beam, or -1 if it never sent one (used by the game test). */
    public static long lastNanoTime(Entity worker) {
        return worker == null ? -1 : LAST_NANO.getOrDefault(worker.getId(), -1L);
    }

    /** Classifies a projectile entity (works on both sides; never throws). */
    public static byte kindOf(net.minecraft.world.entity.Entity e) {
        if (e == null) return K_OTHER;
        try {
            if (e instanceof net.minecraft.world.entity.projectile.FireworkRocketEntity) return K_ROCKET;
            if (e instanceof com.solegendary.reignofnether.entities.ThrowableTntProjectile) return K_TNT;
            if (e instanceof com.solegendary.reignofnether.entities.MoltenBombProjectile) return K_BIG_FIREBALL;
            if (e instanceof net.minecraft.world.entity.projectile.LargeFireball) return K_BIG_FIREBALL;
            if (e instanceof net.minecraft.world.entity.projectile.SmallFireball) return K_FIREBALL;
            if (e instanceof net.minecraft.world.entity.projectile.DragonFireball) return K_BIG_FIREBALL;
            if (e instanceof com.solegendary.reignofnether.entities.AbstractMagicProjectile) return K_MAGIC;
            if (e instanceof net.minecraft.world.entity.projectile.WitherSkull) return K_MAGIC;
            if (e instanceof net.minecraft.world.entity.projectile.ShulkerBullet) return K_MAGIC;
            if (e instanceof net.minecraft.world.entity.projectile.Fireball) return K_FIREBALL;
            if (e instanceof net.minecraft.world.entity.projectile.AbstractArrow) return K_ARROW;
            if (e instanceof net.minecraft.world.entity.projectile.ThrownPotion) return K_POTION;
            if (e instanceof net.minecraft.world.entity.projectile.ThrowableProjectile) return K_THROWN;
            if (e instanceof net.minecraft.world.entity.projectile.LlamaSpit) return K_THROWN;
        } catch (Throwable ignored) { }
        return K_OTHER;
    }

    // ------------------------------------------------------------------ sending

    /**
     * Called once per server tick: sends each player the queued events near them that their fog shows (rewrite 3:
     * shots, deaths, nano beams and battle sounds reached every player within range, through the fog).
     */
    static void flush(ServerLevel level) {
        List<Event> q = QUEUE.get(level.dimension());
        if (q == null || q.isEmpty())
            return;
        List<Event> events = new ArrayList<>(q);
        q.clear();
        for (ServerPlayer player : level.players()) {
            List<Event> mine = new ArrayList<>();
            double px = player.getX(), pz = player.getZ();
            boolean fogged = FogOfWarServerEvents.isFogActiveFor(player);
            for (Event e : events) {
                double dx = e.x - px, dz = e.z - pz;
                if (dx * dx + dz * dz <= SEND_RANGE * SEND_RANGE && (!fogged || seenBy(player, e)))
                    mine.add(e);
            }
            if (!mine.isEmpty()) {
                try {
                    PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), new BarFxClientboundPacket(mine));
                } catch (Exception ignored) { }
            }
        }
    }

    /**
     * Does a fogged player see this event (network/Audience.canSee)? Its spot, or either end of a shot or beam
     * (BAR shows a shell flying into your sight from a shooter you cannot see), or any corner of a collapsing
     * building. Like a vanilla particle (ServerLevelParticleMixin), never through the fog.
     */
    static boolean seenBy(ServerPlayer player, Event e) {
        if (Audience.canSee(player, e.x, e.z))
            return true;
        return switch (e.type) {
            case SHOT, NANO -> Audience.canSee(player, e.a, e.c);   // a, c = the far end's x, z
            case COLLAPSE -> Audience.canSee(player, e.x - e.a, e.z - e.a) || Audience.canSee(player, e.x + e.a, e.z - e.a)
                || Audience.canSee(player, e.x - e.a, e.z + e.a) || Audience.canSee(player, e.x + e.a, e.z + e.a);
            default -> false;
        };
    }

    static void clearAll() {
        com.solegendary.reignofnether.sounds.Sfx.clearServerState();
        QUEUE.clear();
        LAST_NANO.clear();
        muteExplosions = 0;
    }
}
