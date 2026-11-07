package com.solegendary.reignofnether.tide;

import com.solegendary.reignofnether.alliance.AlliancesServerEvents;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * The Admiral's <b>Fog Bank</b> (design/tidewrought_plan.md, slice 2): live sea-fog clouds and who they cover. A bank is
 * a flat circle ({@link #RADIUS} blocks, {@link #HEIGHT} up and down) that lasts {@link #LIFETIME_TICKS}; while it
 * stands, its owner's and allies' units inside it are hidden from their enemies.
 * <p>
 * This class only answers "is this unit inside a friendly bank?" ({@link #covers}). The hiding itself is
 * blocks/ThicketCover's: a bank is its second cover source next to the Verdant thicket, so the hidden set, the
 * invisible flag, the "an enemy within ThicketCover.REVEAL_RADIUS sees in" and "attacking gives you away" rules, the
 * target drop on going under and the Holy Bell reveal (Concealment.revealFor) all apply to fog exactly as to thickets.
 * <p>
 * Cost: one bank per Admiral, so at most 16 at 8v8. {@link #covers} is an empty-list check in the common case and a
 * squared-distance test per live bank otherwise. Expiry and the drifting cloud run every {@link #PASS_TICKS} ticks:
 * two particle packets per bank, nothing per frame. Not saved: a bank is 12 s.
 */
public class FogBankServerEvents {

    public static final int RADIUS = 7;
    /** Vertical half-height of the cloud: a unit this far above or below the centre is outside it. */
    public static final int HEIGHT = 4;
    public static final int LIFETIME_TICKS = 12 * 20;
    public static final int PASS_TICKS = 10;

    /** One live bank. {@code sourceId} is the casting Admiral's entity id: one bank per Admiral, a recast replaces it. */
    public record Bank(ServerLevel level, String owner, int sourceId, double x, double y, double z, long expireTick,
                       int radius, boolean spoilsAim) {
        public boolean contains(LivingEntity le) {
            double dx = le.getX() - x, dz = le.getZ() - z;
            return dx * dx + dz * dz <= radius * radius && Math.abs(le.getY() - y) <= HEIGHT;
        }
    }

    /** The share of enemy ranged shots at a unit inside a mist ({@code spoilsAim}) that miss: the Abyssal Hydra's Brine Mist. */
    public static final float MIST_MISS_CHANCE = 0.5f;

    private static final List<Bank> BANKS = new ArrayList<>();

    public static boolean isEmpty() {
        return BANKS.isEmpty();
    }

    /** Read-only view of the live banks (the game test and debugging). */
    public static List<Bank> banks() {
        return java.util.Collections.unmodifiableList(BANKS);
    }

    /**
     * Raises a bank centred on (x, y, z) - y is the floor the units stand on - for {@code owner}, replacing the one
     * {@code sourceId} already has. Returns the bank.
     */
    public static Bank raise(ServerLevel level, String owner, int sourceId, double x, double y, double z) {
        return raise(level, owner, sourceId, x, y, z, RADIUS, LIFETIME_TICKS, false);
    }

    /**
     * A bank of its own size and length. {@code spoilsAim}: enemy ranged shots at units inside it miss
     * {@link #MIST_MISS_CHANCE} of the time ({@link #shotSpoiled}) - the Hydra's Brine Mist.
     */
    public static Bank raise(ServerLevel level, String owner, int sourceId, double x, double y, double z,
                             int radius, int lifetimeTicks, boolean spoilsAim) {
        BANKS.removeIf(b -> b.sourceId == sourceId && b.level == level);
        Bank b = new Bank(level, owner, sourceId, x, y, z, level.getGameTime() + lifetimeTicks, radius, spoilsAim);
        BANKS.add(b);
        puff(b, 60);
        return b;
    }

    /**
     * Is {@code le} (a unit of {@code owner}) inside a live bank of its owner or an ally? ThicketCover asks this as
     * its second cover source; everything else about hiding is decided there.
     */
    public static boolean covers(ServerLevel level, LivingEntity le, String owner) {
        if (BANKS.isEmpty() || owner == null || owner.isBlank())
            return false;
        long now = level.getGameTime();
        for (int i = 0; i < BANKS.size(); i++) {
            Bank b = BANKS.get(i);
            if (b.level != level || b.expireTick <= now || !b.contains(le))
                continue;
            if (owner.equals(b.owner) || AlliancesServerEvents.isAllied(owner, b.owner))
                return true;
        }
        return false;
    }

    /**
     * Does a mist spoil an enemy projectile's aim at {@code target}? True when {@code roll} (0..1, the projectile's own
     * fixed roll) is under {@link #MIST_MISS_CHANCE} and the target stands in a live aim-spoiling bank of its own side.
     * An empty-list check in the common case.
     */
    public static boolean shotSpoiled(ServerLevel level, LivingEntity target, String targetOwner, float roll) {
        if (BANKS.isEmpty() || roll >= MIST_MISS_CHANCE || targetOwner == null || targetOwner.isBlank())
            return false;
        long now = level.getGameTime();
        for (int i = 0; i < BANKS.size(); i++) {
            Bank b = BANKS.get(i);
            if (!b.spoilsAim || b.level != level || b.expireTick <= now || !b.contains(target))
                continue;
            if (targetOwner.equals(b.owner) || AlliancesServerEvents.isAllied(targetOwner, b.owner))
                return true;
        }
        return false;
    }

    /** Drops every bank of this owner (defeat) - its units come out at the next cover pass. */
    public static void clearOwner(String owner) {
        BANKS.removeIf(b -> b.owner.equals(owner));
    }

    public static void clearAll() {
        BANKS.clear();
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent evt) {
        if (evt.phase != TickEvent.Phase.END || evt.getServer() == null || BANKS.isEmpty())
            return;
        if (evt.getServer().getTickCount() % PASS_TICKS != 0)
            return;
        for (int i = BANKS.size() - 1; i >= 0; i--) {
            Bank b = BANKS.get(i);
            if (b.level.getGameTime() >= b.expireTick)
                BANKS.remove(i);
            else
                puff(b, 18);
        }
    }

    // the visible cloud: a low wide layer and a few taller wisps. Server-sent (one packet each per watching player),
    // so the fog reads the same for both sides - the enemy sees the bank, not what is in it
    private static void puff(Bank b, int count) {
        b.level.sendParticles(ParticleTypes.CLOUD, b.x, b.y + 0.8, b.z, count, b.radius * 0.5, 0.4, b.radius * 0.5, 0.0);
        b.level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, b.x, b.y + 1.0, b.z, Math.max(2, count / 6),
                b.radius * 0.4, 0.6, b.radius * 0.4, 0.005);
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent evt) {
        BANKS.clear();
    }
}
