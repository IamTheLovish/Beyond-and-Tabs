package com.solegendary.reignofnether.barfx;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Rune magic, the server half (lovish, Nov 5: "I want to add rune magic effects that ive seen in other minecraft mods
 * and games"; clean room - nothing copied, our own alphabets). One shared effect for every faction: a rune circle on the
 * ground in the faction's own alphabet (RuneFxClient draws it from the atlas tools/gen_rune_textures.py makes). It is a
 * {@link BarFx#RUNE} event like a shot or a dome, so it reaches only the players whose fog shows it
 * ({@link BarFx#eventsFor}).
 * <p>
 * Where it starts: {@link #cast}, called by Essence.trySpend - the one call every caster spell makes, last, once all its
 * other checks passed, so a spend is a cast. Fixed at that shared entry, never per unit. Other systems (heroes,
 * channels, buildings) can call {@link #start} / {@link #stop} with their own length and radius.
 * <p>
 * The server keeps one entry per live circle (by id: the caster's entity id for a following circle) so it can end a
 * circle early - its caster died - and hold the {@link #MAX_LIVE} cap per level. A circle is live from the tick it
 * starts until {@code ticks} later ({@link #isLive} reads the game time, so it needs no ticking); expired entries are
 * pruned while counting and by {@link #tick}. Server thread only.
 */
public final class RuneFx {
    private RuneFx() { }

    /**
     * The cast time of an essence caster's spell, in ticks: how long its circle lives (RuneFxClient: a 0.3 s draw-in,
     * then the turning layers and rising glyphs, the last 0.35 s a fade). The casters' spells land at once; this is the
     * flourish the study puts at 0.5-1.5 s, so a glance catches who cast.
     */
    public static final int CAST_TICKS = 30;
    /** The most circles live at once in one level (a 100-caster fight shows 48; each start past it is dropped). */
    public static final int MAX_LIVE = 48;
    /** A T1 caster's circle, in blocks: about twice its footprint, ~115 px across at zoom 30. */
    public static final float CAST_RADIUS = 1.3f;

    /** A live circle: its level, when it ends, and where it started (the end event's spot when its entity is gone). */
    private record Live(ResourceKey<Level> dim, long endsAt, float x, float y, float z) { }

    private static final Int2ObjectOpenHashMap<Live> LIVE = new Int2ObjectOpenHashMap<>();

    /** A caster's spell went off: its faction's circle under it for {@link #CAST_TICKS}. False when nothing started. */
    public static boolean cast(LivingEntity caster) {
        if (caster == null || !(caster.level() instanceof ServerLevel sl))
            return false;
        return start(sl, caster.getId(), caster.position(), BarFx.factionOf(caster), CAST_RADIUS, CAST_TICKS, true);
    }

    /**
     * Starts (or restarts: the same id again) a circle of {@code radius} at {@code at} for {@code ticks}, in faction
     * style {@code style} (F_*); {@code follow}: {@code id} is an entity id and the circle rides on that entity. False
     * when the level already holds {@link #MAX_LIVE} other live circles.
     */
    public static boolean start(ServerLevel level, int id, Vec3 at, byte style, float radius, int ticks, boolean follow) {
        if (level == null || at == null || ticks <= 0)
            return false;
        long now = level.getGameTime();
        ResourceKey<Level> dim = level.dimension();
        Live old = LIVE.get(id);
        boolean restart = old != null && old.dim == dim && now < old.endsAt;
        if (!restart && countLive(dim, now) >= MAX_LIVE)
            return false;
        LIVE.put(id, new Live(dim, now + ticks, (float) at.x, (float) at.y, (float) at.z));
        BarFx.emit(level, new BarFx.Event(BarFx.RUNE, style, (byte) (BarFx.RUNE_START | (follow ? BarFx.RUNE_FOLLOW : 0)),
            (float) at.x, (float) at.y, (float) at.z, radius, ticks, id));
        return true;
    }

    /** Ends circle {@code id} now (its caster died, a channel broke): the client fades it out. No-op if not live. */
    public static void stop(ServerLevel level, int id) {
        if (level == null)
            return;
        Live l = LIVE.get(id);
        if (l == null || l.dim != level.dimension())
            return;
        LIVE.remove(id);
        if (level.getGameTime() >= l.endsAt)
            return;   // over already: nothing on screen to end
        Entity e = level.getEntity(id);   // the fog test goes by where it is now, or where it started
        float x = e != null ? (float) e.getX() : l.x, y = e != null ? (float) e.getY() : l.y, z = e != null ? (float) e.getZ() : l.z;
        BarFx.emit(level, new BarFx.Event(BarFx.RUNE, BarFx.F_NONE, BarFx.RUNE_END, x, y, z, 0, 0, id));
    }

    /** Is circle {@code id} live in {@code level} now: from its start tick until {@code ticks} later, exclusive. */
    public static boolean isLive(ServerLevel level, int id) {
        Live l = LIVE.get(id);
        return level != null && l != null && l.dim == level.dimension() && level.getGameTime() < l.endsAt;
    }

    /** How many circles are live in {@code level} now. */
    public static int liveCount(ServerLevel level) {
        return level == null ? 0 : countLive(level.dimension(), level.getGameTime());
    }

    private static int countLive(ResourceKey<Level> dim, long now) {
        int n = 0;
        for (ObjectIterator<Live> it = LIVE.values().iterator(); it.hasNext(); ) {
            Live l = it.next();
            if (l.dim != dim)
                continue;
            if (now >= l.endsAt)
                it.remove();   // expired: pruned while counting
            else
                n++;
        }
        return n;
    }

    /** Once a server tick per level (BarFxServerEvents, before the send): drops the expired entries. */
    static void tick(ServerLevel level) {
        if (!LIVE.isEmpty())
            countLive(level.dimension(), level.getGameTime());
    }

    /** A unit died: the circle riding on it ends with it (BarFxServerEvents.onLivingDeath). */
    static void onDeath(LivingEntity e) {
        if (e.level() instanceof ServerLevel sl && LIVE.containsKey(e.getId()))
            stop(sl, e.getId());
    }

    static void clearAll() {
        LIVE.clear();
    }
}
