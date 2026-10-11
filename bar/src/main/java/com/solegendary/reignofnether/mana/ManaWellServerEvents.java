package com.solegendary.reignofnether.mana;

import com.solegendary.reignofnether.hud.HudClientboundPacket;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * The Ley Master's <b>Mana Wells</b> (design/stormcallers_plan.md section 3.4, slice 3). The spell rules live in
 * {@link ManaServerEvents} ({@code registerWell} / {@code insideWell}: {@code WELL_POWER} x1.25 and {@code WELL_DISCOUNT}
 * x0.8 for every spell cast within {@code WELL_RADIUS} of an own or allied well, {@code WELLS_PER_PLAYER} 4, wells don't
 * stack). This class is the well itself: a marker in the world - two vanilla display entities (a jade basin and a soul
 * lantern floating over it; no block is ever placed, so pathing and buildings are untouched, the Totem of the Pack's
 * trick) and a ring of jade dust every second so the radius reads from the RTS camera - and its lifecycle: a well lives
 * until its owner dismantles it (a Ley Master casting on it again), is defeated or the match resets. Saved: the
 * registry (owner, dimension, centre) is written by MatchStateSaveData and {@link #restore}d when the server starts,
 * which raises fresh markers; the old markers that load from disk with their chunks are discarded as stale (they are
 * not the restored well's display objects), so a reload never doubles a well (design/persistence.md).
 */
public final class ManaWellServerEvents {
    private ManaWellServerEvents() { }

    public static final String MARKER_TAG = "bt_mana_well";
    /** Casting within this many blocks of an own well takes it down instead of raising another. */
    public static final double DISMANTLE_RADIUS = 2.5;
    public static final String MSG_RAISED = "hud.mana.reignofnether.well_raised";
    public static final String MSG_DISMANTLED = "hud.mana.reignofnether.well_dismantled";

    /** One standing well. Public for the game test. */
    public static final class Well {
        public final ServerLevel level;
        public final String owner;
        public final BlockPos centre;
        final List<Entity> displays = new ArrayList<>(2);

        Well(ServerLevel level, String owner, BlockPos centre) {
            this.level = level;
            this.owner = owner;
            this.centre = centre;
        }
    }

    // at most WELLS_PER_PLAYER per player, so a few dozen at 8v8; server thread only
    static final List<Well> WELLS = new ArrayList<>();
    // markers left over from a save, discarded on the next tick (see onJoin)
    static final List<Entity> STALE = new ArrayList<>();

    /**
     * Raises a well for {@code owner} centred on the ground at {@code at}'s column, or takes down the owner's well that
     * stands within {@link #DISMANTLE_RADIUS} of it. Pays nothing (the ability pays first). Returns the new well, or
     * null when one was dismantled or the cap refused it ({@link ManaServerEvents#lastRefusal} says which). Public for
     * the bot and the game test.
     */
    public static Well raise(ServerLevel sl, String owner, BlockPos at, boolean quiet) {
        if (owner == null || owner.isEmpty() || at == null)
            return null;
        Well near = nearest(sl, owner, at, DISMANTLE_RADIUS);
        if (near != null) {
            dismantle(near);
            if (!quiet)
                HudClientboundPacket.showTempMessageI18n(owner, MSG_DISMANTLED);
            return null;
        }
        // the well stands on the floor by the aimed block (a clicked ground block or a caster's feet), never the heightmap
        BlockPos centre = new BlockPos(at.getX(), Blink.groundY(sl, at.getX(), at.getZ(), at.getY() + 1), at.getZ());
        ManaServerEvents.Refusal r = ManaServerEvents.registerWell(owner, centre);
        if (r != ManaServerEvents.Refusal.NONE) {
            if (!quiet && r.langKey != null)
                HudClientboundPacket.showTempMessageI18n(owner, r.langKey);
            return null;
        }
        Well w = new Well(sl, owner, centre);
        WELLS.add(w);   // tracked before the displays join, so onJoin lets them live
        addDisplays(sl, w);
        double x = centre.getX() + 0.5, z = centre.getZ() + 0.5;
        sl.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, x, centre.getY() + 0.6, z, 16, 0.5, 0.3, 0.5, 0.02);
        sl.sendParticles(ParticleTypes.END_ROD, x, centre.getY() + 0.8, z, 8, 0.3, 0.4, 0.3, 0.03);
        sl.playSound(null, centre, SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.NEUTRAL, 1.2f, 1.4f);
        if (!quiet)
            HudClientboundPacket.showTempMessageI18n(owner, MSG_RAISED);
        return w;
    }

    /** The basin and the floating lantern of a well (the well must already be in WELLS, or onJoin culls them). */
    static void addDisplays(ServerLevel sl, Well w) {
        BlockPos centre = w.centre;
        double x = centre.getX() + 0.5, z = centre.getZ() + 0.5;
        Entity basin = EntityType.BLOCK_DISPLAY.create(sl);
        if (basin != null) {
            CompoundTag tag = new CompoundTag();
            basin.saveWithoutId(tag);
            tag.put("block_state", NbtUtils.writeBlockState(Blocks.WAXED_OXIDIZED_CUT_COPPER.defaultBlockState()));
            tag.put("transformation", transform(-0.7f, 0f, -0.7f, 1.4f, 0.45f, 1.4f));
            basin.load(tag);
            basin.moveTo(x, centre.getY(), z, 0, 0);
            basin.addTag(MARKER_TAG);
            w.displays.add(basin);
            sl.addFreshEntity(basin);
        }
        Entity lantern = EntityType.ITEM_DISPLAY.create(sl);
        if (lantern != null) {
            CompoundTag tag = new CompoundTag();
            lantern.saveWithoutId(tag);
            tag.put("item", new ItemStack(Items.SOUL_LANTERN).save(new CompoundTag()));
            tag.put("transformation", transform(0f, 0f, 0f, 0.8f, 0.8f, 0.8f));
            lantern.load(tag);
            lantern.moveTo(x, centre.getY() + 1.1, z, 0, 0);
            lantern.addTag(MARKER_TAG);
            w.displays.add(lantern);
            sl.addFreshEntity(lantern);
        }
    }

    // ------------------------------------------------------------------ persistence (MatchStateSaveData)

    /** Every standing well as {owner, dim, x, y, z}. */
    public static ListTag saveTag() {
        ListTag list = new ListTag();
        for (Well w : WELLS) {
            CompoundTag t = new CompoundTag();
            t.putString("owner", w.owner);
            t.putString("dim", w.level.dimension().location().toString());
            t.putInt("x", w.centre.getX());
            t.putInt("y", w.centre.getY());
            t.putInt("z", w.centre.getZ());
            list.add(t);
        }
        return list;
    }

    /**
     * Re-raises saved wells exactly where they stood (no re-grounding, no messages, no sounds). Idempotent: a well the
     * owner already has at that centre is kept, so restoring twice never doubles it. Returns how many were raised.
     */
    public static int restore(MinecraftServer server, ListTag list) {
        int n = 0;
        for (int i = 0; i < list.size(); i++) {
            CompoundTag t = list.getCompound(i);
            String owner = t.getString("owner");
            ServerLevel sl = levelOf(server, t.getString("dim"));
            if (owner.isEmpty() || sl == null)
                continue;
            BlockPos centre = new BlockPos(t.getInt("x"), t.getInt("y"), t.getInt("z"));
            if (nearest(sl, owner, centre, 0.5) != null)
                continue;
            if (ManaServerEvents.registerWell(owner, centre) != ManaServerEvents.Refusal.NONE)
                continue;   // over the cap (a save edited by hand): the registry rules still hold
            Well w = new Well(sl, owner, centre);
            WELLS.add(w);
            addDisplays(sl, w);
            n++;
        }
        return n;
    }

    static ServerLevel levelOf(MinecraftServer server, String dim) {
        ResourceLocation rl = ResourceLocation.tryParse(dim);
        return rl == null ? null : server.getLevel(ResourceKey.create(Registries.DIMENSION, rl));
    }

    static CompoundTag transform(float tx, float ty, float tz, float sx, float sy, float sz) {
        CompoundTag tf = new CompoundTag();
        tf.put("left_rotation", floats(0, 0, 0, 1));
        tf.put("right_rotation", floats(0, 0, 0, 1));
        tf.put("translation", floats(tx, ty, tz));
        tf.put("scale", floats(sx, sy, sz));
        return tf;
    }

    static ListTag floats(float... v) {
        ListTag list = new ListTag();
        for (float f : v)
            list.add(FloatTag.valueOf(f));
        return list;
    }

    /** The owner's well within r of pos, or null. */
    public static Well nearest(ServerLevel sl, String owner, BlockPos pos, double r) {
        Well best = null;
        double bestD = r * r;
        for (Well w : WELLS) {
            if (w.level != sl || !w.owner.equals(owner))
                continue;
            double d = w.centre.distSqr(pos);
            if (d <= bestD) {
                bestD = d;
                best = w;
            }
        }
        return best;
    }

    public static List<Well> wellsOf(String owner) {
        List<Well> out = new ArrayList<>();
        for (Well w : WELLS)
            if (w.owner.equals(owner))
                out.add(w);
        return out;
    }

    /** Takes a well down: its markers vanish and the spell registry forgets it. Public for the game test. */
    public static void dismantle(Well w) {
        WELLS.remove(w);
        ManaServerEvents.unregisterWell(w.owner, w.centre);
        for (Entity e : w.displays)
            e.discard();
        w.displays.clear();
        w.level.sendParticles(ParticleTypes.ASH, w.centre.getX() + 0.5, w.centre.getY() + 0.6, w.centre.getZ() + 0.5, 12, 0.4, 0.3, 0.4, 0.02);
    }

    /** Defeat: the owner's wells go with their registry entries (called from ManaServerEvents.clearOwner). */
    static void clearOwner(String owner) {
        Iterator<Well> it = WELLS.iterator();
        while (it.hasNext()) {
            Well w = it.next();
            if (!w.owner.equals(owner))
                continue;
            for (Entity e : w.displays)
                e.discard();
            it.remove();
        }
    }

    static void clearAll() {
        for (Well w : WELLS)
            for (Entity e : w.displays)
                e.discard();
        WELLS.clear();
        STALE.clear();
    }

    /**
     * A well marker that (re)loads from disk is a new entity object this class doesn't track (and its well is no longer
     * registered): queue it to be discarded on the next tick (discarding inside the join event itself is unsafe for an
     * entity still being loaded).
     */
    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent evt) {
        if (evt.getLevel().isClientSide())
            return;
        Entity e = evt.getEntity();
        if (!e.getTags().contains(MARKER_TAG))
            return;
        for (Well w : WELLS)
            if (w.displays.contains(e))
                return;
        STALE.add(e);
    }

    private static final DustParticleOptions JADE = new DustParticleOptions(new Vector3f(0.31f, 0.82f, 0.63f), 1.0f);

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent evt) {
        if (evt.phase != TickEvent.Phase.END || evt.getServer() == null)
            return;
        if (!STALE.isEmpty()) {
            for (Entity e : STALE)
                e.discard();
            STALE.clear();
        }
        if (WELLS.isEmpty() || evt.getServer().getTickCount() % 20 != 0)
            return;
        // once a second: a soul wisp at the lantern and eight jade motes on the radius, so the well's reach is visible
        for (Well w : WELLS) {
            if (w.level.getServer() != evt.getServer())
                continue;
            double x = w.centre.getX() + 0.5, y = w.centre.getY() + 0.3, z = w.centre.getZ() + 0.5;
            w.level.sendParticles(ParticleTypes.SOUL, x, y + 1.0, z, 1, 0.1, 0.1, 0.1, 0.01);
            for (int i = 0; i < 8; i++) {
                double a = i * Math.PI / 4;
                w.level.sendParticles(JADE, x + Math.cos(a) * ManaServerEvents.WELL_RADIUS, y, z + Math.sin(a) * ManaServerEvents.WELL_RADIUS,
                    1, 0.1, 0.1, 0.1, 0.0);
            }
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent evt) {
        WELLS.clear();
        STALE.clear();
    }
}
