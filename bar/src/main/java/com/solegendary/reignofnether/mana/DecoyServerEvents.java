package com.solegendary.reignofnether.mana;

import com.solegendary.reignofnether.unit.interfaces.Unit;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * The Mirror Mage's <b>decoys</b> (design/stormcallers_plan.md, slice 3: Mirror Images). A decoy is a real unit of the
 * copied type, owned by the caster's side and tagged {@link #TAG}, so enemies target it exactly as they would the
 * original - clients cannot tell them apart. The rules that make it a decoy live here, in three event hooks and one
 * once-a-second pass:
 * <ul>
 *   <li>it <b>deals no damage</b>: any attack whose source (or projectile owner) is a decoy is cancelled before it hurts;</li>
 *   <li>it <b>shatters at a touch</b>: the first hit of any kind discards it (no death event, so no wreck, no kill credit
 *       and no Totem wolf) in a burst of glass;</li>
 *   <li>it <b>fades after {@link #LIFETIME_TICKS}</b>, is <b>never population</b> (UnitServerEvents skips decoys) and
 *       leaves <b>no wreck</b> (WreckServerEvents); one left over from a save is discarded when it loads (the Awaken
 *       Thicket rule).</li>
 * </ul>
 * At most {@link #MAX_PER_OWNER} alive per player (two casts' worth): each is a pathing, ticking entity at 8v8 scale.
 */
public final class DecoyServerEvents {
    private DecoyServerEvents() { }

    public static final String TAG = "bt_decoy";
    public static final int LIFETIME_TICKS = 10 * 20;
    public static final int MAX_PER_OWNER = 6;
    static final String KEY_EXPIRE = "bt_decoy_expire";

    // every live decoy, so the expiry pass and the cap never scan the unit list; server thread only
    static final List<LivingEntity> DECOYS = new ArrayList<>();
    // leftovers from a save, discarded on the next server tick (see onJoin)
    static final List<Entity> STALE = new ArrayList<>();

    public static boolean isDecoy(Entity e) {
        return e != null && e.getTags().contains(TAG);
    }

    /** Marks a freshly spawned unit as a decoy for its lifetime. */
    public static void register(ServerLevel sl, LivingEntity decoy) {
        decoy.addTag(TAG);
        decoy.getPersistentData().putLong(KEY_EXPIRE, sl.getGameTime() + LIFETIME_TICKS);
        DECOYS.add(decoy);
    }

    public static int countOf(String owner) {
        int n = 0;
        for (LivingEntity d : DECOYS)
            if (d.isAlive() && !d.isRemoved() && d instanceof Unit u && owner.equals(u.getOwnerName()))
                n++;
        return n;
    }

    /** The live decoys (for the game test). */
    public static List<LivingEntity> decoys() {
        return DECOYS;
    }

    /** A decoy breaks like a mirror: a burst of glass, no death. Public for the game test. */
    public static void shatter(LivingEntity decoy) {
        if (decoy.level() instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.END_ROD, decoy.getX(), decoy.getY() + decoy.getBbHeight() * 0.5, decoy.getZ(), 12, 0.3, 0.5, 0.3, 0.05);
            sl.sendParticles(ParticleTypes.ENCHANT, decoy.getX(), decoy.getY() + 1, decoy.getZ(), 10, 0.3, 0.4, 0.3, 0.3);
            sl.playSound(null, decoy.blockPosition(), SoundEvents.GLASS_BREAK, SoundSource.NEUTRAL, 1.2f, 1.3f);
        }
        decoy.discard();
        DECOYS.remove(decoy);
    }

    /** A decoy's attack is a reflection: it lands on nothing. A hit on a decoy breaks it before any damage is done. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onAttack(LivingAttackEvent evt) {
        if (DECOYS.isEmpty() || evt.getEntity().level().isClientSide())
            return;
        Entity src = evt.getSource().getEntity();
        Entity direct = evt.getSource().getDirectEntity();
        if (direct instanceof Projectile p && p.getOwner() != null)
            src = p.getOwner();
        if (isDecoy(src) || isDecoy(direct)) {
            evt.setCanceled(true);
            return;
        }
        LivingEntity victim = evt.getEntity();
        if (isDecoy(victim)) {
            evt.setCanceled(true);
            // inWall: which block the eyes were in (the Oct 21 red said "by inWall" and nothing on what it was), and
            // whose decoy it was - this text is one static string, so a parallel test's decoy can overwrite it
            String detail = "";
            if ("inWall".equals(evt.getSource().getMsgId())) {
                net.minecraft.core.BlockPos eye = net.minecraft.core.BlockPos.containing(victim.getEyePosition());
                detail = " (eyes in " + victim.level().getBlockState(eye) + " at " + eye.toShortString() + ", feet at "
                        + String.format(java.util.Locale.ROOT, "%.2f/%.2f/%.2f", victim.getX(), victim.getY(), victim.getZ())
                        + ", chunk loaded " + victim.level().hasChunkAt(eye) + ")";
            }
            lastShatter = victim.getName().getString() + " of " + (victim instanceof Unit vu ? vu.getOwnerName() : "?")
                    + " at " + victim.blockPosition().toShortString() + " by "
                    + evt.getSource().getMsgId() + detail + (src == null ? "" : " from " + src.getName().getString() + " at "
                    + src.blockPosition().toShortString()) + " (tick " + victim.level().getGameTime() + ")";
            shatter(victim);
        }
    }

    // the last blow that broke a decoy: the mirror-images game test failed once on the arena floor with one decoy
    // gone and no way to tell what hit it (Oct 19); the failure message now says so
    private static String lastShatter = "none";

    /** What broke the last decoy (source, attacker, position, tick) - for the game test's failure message. */
    public static String lastShatter() {
        return lastShatter;
    }

    /**
     * A decoy that (re)loads from disk - a save, or its chunk unloading - is a new entity object this class doesn't
     * track, so its timer is lost: queue it to be discarded on the next tick (discarding inside the join event itself is
     * unsafe for an entity still being loaded). A fresh one is not caught: spawnMob adds it before it gets its tag.
     */
    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent evt) {
        if (evt.getLevel().isClientSide())
            return;
        Entity e = evt.getEntity();
        if (isDecoy(e) && !DECOYS.contains(e))
            STALE.add(e);
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent evt) {
        if (evt.phase != TickEvent.Phase.END || evt.getServer() == null)
            return;
        if (!STALE.isEmpty()) {
            for (Entity e : STALE)
                e.discard();
            STALE.clear();
        }
        if (DECOYS.isEmpty() || evt.getServer().getTickCount() % 20 != 0)
            return;
        Iterator<LivingEntity> it = DECOYS.iterator();
        while (it.hasNext()) {
            LivingEntity d = it.next();
            if (d.isRemoved()) {
                it.remove();
                continue;
            }
            if (!(d.level() instanceof ServerLevel dl) || dl.getServer() != evt.getServer())
                continue;
            if (dl.getGameTime() >= d.getPersistentData().getLong(KEY_EXPIRE)) {
                it.remove();
                dl.sendParticles(ParticleTypes.END_ROD, d.getX(), d.getY() + 1, d.getZ(), 8, 0.3, 0.5, 0.3, 0.03);
                d.discard();   // fades: no death event, so no wreck and no kill credit
            } else {
                // a glint a second: allies see a shimmer, enemies see a unit (the particle is faint at a distance)
                dl.sendParticles(ParticleTypes.ENCHANT, d.getX(), d.getY() + d.getBbHeight() + 0.2, d.getZ(), 1, 0.1, 0.1, 0.1, 0.0);
            }
        }
    }

    /** Defeat: the owner's decoys shatter. */
    public static void clearOwner(String owner) {
        Iterator<LivingEntity> it = DECOYS.iterator();
        while (it.hasNext()) {
            LivingEntity d = it.next();
            if (d instanceof Unit u && owner.equals(u.getOwnerName())) {
                it.remove();
                d.discard();
            }
        }
    }

    public static void clearAll() {
        for (LivingEntity d : DECOYS)
            d.discard();
        DECOYS.clear();
        STALE.clear();
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent evt) {
        DECOYS.clear();
        STALE.clear();
    }
}
