package com.solegendary.reignofnether.barfx;

import com.solegendary.reignofnether.registrars.PacketHandler;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.List;

/**
 * Ground telegraphs for big abilities (design: shown to everyone, enemies too, at least 400 ms before a stun or a
 * 30%+ hit lands). Server: {@link #announce} sends a TELEGRAPH BarFx event at once (not queued, and not filtered by
 * fog: the people standing in the ring must see it) to every player in range, and returns the game tick the effect
 * may land on - never earlier than {@link #MIN_LEAD_TICKS} ahead. Client: a small fixed pool of rings drawn from
 * BarFxClient's frame (no allocation per frame).
 */
public final class Telegraphs {
    private Telegraphs() { }

    /** 400 ms at 20 ticks per second. */
    public static final int MIN_LEAD_TICKS = 8;
    public static final double SEND_RANGE = 200;

    /** The lead actually used: the wanted delay, but never under the 400 ms floor. */
    public static int lead(int wantedDelayTicks) {
        return Math.max(MIN_LEAD_TICKS, wantedDelayTicks);
    }

    /** The telegraph event: a = radius, b = lead ticks until the effect lands, c = ticks the ring stays after. */
    public static BarFx.Event event(Vec3 centre, float radius, int leadTicks, int lingerTicks) {
        return new BarFx.Event(BarFx.TELEGRAPH, (byte) 0, (byte) 0, (float) centre.x, (float) centre.y, (float) centre.z,
            radius, leadTicks, lingerTicks);
    }

    /**
     * Shows a ring of {@code radius} at {@code centre} to every player in range. {@code wantedDelayTicks} is when the
     * effect would like to land; the returned game tick is when it MAY land (>= now + 8 ticks): schedule the
     * effect no earlier.
     */
    public static long announce(ServerLevel level, Vec3 centre, float radius, int wantedDelayTicks) {
        int lead = lead(wantedDelayTicks);
        BarFxClientboundPacket packet = new BarFxClientboundPacket(List.of(event(centre, radius, lead, 4)));
        double r2 = SEND_RANGE * SEND_RANGE;
        for (ServerPlayer p : level.players()) {
            double dx = p.getX() - centre.x, dz = p.getZ() - centre.z;
            if (dx * dx + dz * dz <= r2) {
                try {
                    PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> p), packet);
                } catch (Exception ignored) { }
            }
        }
        return level.getGameTime() + lead;
    }

    // ================================================================== client pool

    private static final int MAX = 12;
    private static final float[] X = new float[MAX], Y = new float[MAX], Z = new float[MAX], R = new float[MAX];
    private static final float[] BORN = new float[MAX], LEAD = new float[MAX], END = new float[MAX];
    private static int count = 0;

    public static boolean isEmpty() {
        return count == 0;
    }

    public static void clear() {
        count = 0;
    }

    /** A TELEGRAPH event arrived (client). {@code now} = BarFxClient clock in seconds. */
    @OnlyIn(Dist.CLIENT)
    static void show(BarFx.Event e, float now) {
        int i = count < MAX ? count++ : 0;   // a full pool recycles the first slot
        X[i] = e.x; Y[i] = e.y; Z[i] = e.z; R[i] = Math.max(0.5f, Math.min(24f, e.a));
        BORN[i] = now; LEAD[i] = e.b / 20f; END[i] = now + (e.b + e.c) / 20f;
    }

    /** Draws and expires the rings: red, a filling inner ring that reaches the edge exactly when the effect lands. */
    @OnlyIn(Dist.CLIENT)
    static void render(float now) {
        int w = 0;
        for (int i = 0; i < count; i++) {
            if (now >= END[i])
                continue;
            if (w != i) {
                X[w] = X[i]; Y[w] = Y[i]; Z[w] = Z[i]; R[w] = R[i]; BORN[w] = BORN[i]; LEAD[w] = LEAD[i]; END[w] = END[i];
            }
            float k = Math.min(1f, (now - BORN[w]) / Math.max(0.05f, LEAD[w]));
            float g = BarFxClient.groundOr(X[w], Y[w], Z[w]) + 0.12f;
            float pulse = 0.55f + 0.25f * (float) Math.sin(now * 14f);
            BarFxClient.flatRing(X[w], g, Z[w], R[w], 0.18f, 0xFF3020, pulse, 40);
            BarFxClient.flatRing(X[w], g, Z[w], R[w] * Math.max(0.05f, k), 0.12f, 0xFF7040, 0.5f, 40);
            w++;
        }
        count = w;
    }
}
