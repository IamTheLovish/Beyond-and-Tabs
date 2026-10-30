package com.solegendary.reignofnether.sounds;

import com.solegendary.reignofnether.ReignOfNether;
import net.minecraft.core.Holder;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundSoundEntityPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.concurrent.atomic.AtomicIntegerArray;

/**
 * "[Ears]": which world sounds reach the RTS player - a measurement for the eyes runs only (-Dbat.eyes=true), nothing in
 * a real game (design-draft-combat-feel-2026-10-30.md, gain 2a). The server sends a world sound only to players within
 * vanilla's radius, 16 x max(1, volume) blocks in 3D (PlayerList.broadcast), and the RTS view's player entity floats
 * about 30 blocks over the ground (OrthoviewClientEvents), so a unit's hurt or death voice (volume 1) from the fight
 * right under the camera may never be sent. Counted per kind (hurt, death, other):
 * <ul>
 *   <li>sent: a player was inside the radius;</li>
 *   <li>lost to height: a player was inside the radius across the ground but outside it in 3D, more than
 *   {@link #ABOVE} blocks above the sound - the RTS camera's case;</li>
 *   <li>received: the client got it from the server (ClientLevel.playSeededSound for the client's own player);</li>
 *   <li>local: the client played it itself (an entity's playSound with no player: a hurt or death voice from the
 *   client's own damage and death events, which ClientLevelMixin plays in the RTS view).</li>
 * </ul>
 * One log line at most every {@link #EVERY_MS} while sounds come in. The integrated server and the client share the
 * counters (one JVM in an eyes run).
 */
public final class EarsCount {

    public static final boolean ON = Boolean.getBoolean("bat.eyes");
    static final int HURT = 0, DEATH = 1, OTHER = 2;
    static final String[] KINDS = { "hurt", "death", "other" };
    /** A player this far over the sound counts as the RTS camera's floating body. */
    static final double ABOVE = 8;
    static final long EVERY_MS = 10_000;

    private static final AtomicIntegerArray SENT = new AtomicIntegerArray(3), LOST = new AtomicIntegerArray(3),
        RECEIVED = new AtomicIntegerArray(3), LOCAL = new AtomicIntegerArray(3);
    private static volatile long lastLog = 0;

    private EarsCount() { }

    static int kind(@Nullable SoundEvent sound) {
        if (sound == null)
            return OTHER;
        String path = sound.getLocation().getPath();
        if (path.contains(".hurt"))
            return HURT;
        if (path.contains(".death"))
            return DEATH;
        return OTHER;
    }

    @Nullable
    static SoundEvent soundOf(Packet<?> packet) {
        Holder<SoundEvent> holder = packet instanceof ClientboundSoundPacket p ? p.getSound()
            : packet instanceof ClientboundSoundEntityPacket p ? p.getSound() : null;
        return holder == null ? null : holder.get();
    }

    /** One world sound the server broadcasts (ServerLevelSoundMixin, before its own fog test). Never throws. */
    public static void server(PlayerList list, @Nullable net.minecraft.world.entity.player.Player except, double x,
                              double y, double z, double radius, ResourceKey<Level> dimension, Packet<?> packet) {
        if (!ON)
            return;
        try {
            int k = kind(soundOf(packet));
            double r2 = radius * radius;
            for (ServerPlayer sp : list.getPlayers()) {
                if (sp == except || sp.level().dimension() != dimension)
                    continue;
                double dx = x - sp.getX(), dy = y - sp.getY(), dz = z - sp.getZ();
                double flat = dx * dx + dz * dz;
                if (flat + dy * dy < r2)
                    SENT.incrementAndGet(k);
                else if (flat < r2 && -dy > ABOVE)
                    LOST.incrementAndGet(k);
            }
            maybeLog();
        } catch (Exception ignored) { }
    }

    /** A world sound on the client (ClientLevelMixin): from the server, or played locally. Never throws. */
    public static void client(@Nullable SoundEvent sound, boolean fromServer) {
        if (!ON)
            return;
        try {
            (fromServer ? RECEIVED : LOCAL).incrementAndGet(kind(sound));
            maybeLog();
        } catch (Exception ignored) { }
    }

    static void maybeLog() {
        long now = System.currentTimeMillis();
        if (now - lastLog < EVERY_MS)
            return;
        lastLog = now;
        ReignOfNether.LOGGER.info("[Ears] {}", line());
    }

    /** "hurt sent 3 lost-to-height 41 received 3 local 40; death ...; other ..." (totals since the game started). */
    static String line() {
        StringBuilder sb = new StringBuilder();
        for (int k = 0; k < 3; k++) {
            if (k > 0)
                sb.append("; ");
            sb.append(KINDS[k]).append(" sent ").append(SENT.get(k)).append(" lost-to-height ").append(LOST.get(k))
                .append(" received ").append(RECEIVED.get(k)).append(" local ").append(LOCAL.get(k));
        }
        return sb.toString();
    }
}
