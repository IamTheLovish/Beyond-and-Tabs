package com.solegendary.reignofnether.resources;

import java.util.Random;

/**
 * BAR's wind (lovish Oct 21 02:26: "we need to add the wind rng into the game"): wind-driven energy buildings earn
 * with the wind, which wanders between {@link #MIN} and {@link #MAX}. Every {@link #CHANGE_MIN_TICKS}-{@link
 * #CHANGE_MAX_TICKS} ticks a new target strength is drawn; the strength glides toward it at {@link #GLIDE_PER_SECOND}
 * per second, so income moves smoothly, never in jumps. The range's average is {@link #MEAN} = the old flat 14 energy
 * per generator, so the economy's balance is unchanged on average - only steadiness changes. One wind for the whole
 * match (every player sees the same wind - fair, and readable on the HUD).
 * <p>
 * Server: {@link #tick} once per server tick from EconomyServerEvents. Client: {@link #strength()} is the synced value
 * (EconomyClientboundPacket), used by the HUD and the sail spin. {@link State} is the same logic on its own, for the
 * GameTest (which must not move the live wind other tests run under).
 */
public final class Wind {
    private Wind() { }

    public static final float MIN = 4f, MAX = 24f, MEAN = (MIN + MAX) / 2f;   // 14: the old flat income
    static final int CHANGE_MIN_TICKS = 20 * 30, CHANGE_MAX_TICKS = 20 * 75;
    static final float GLIDE_PER_SECOND = 0.6f;

    /** One wind: a strength gliding toward a target drawn from a seeded sequence. */
    public static final class State {
        float strength = MEAN, target = MEAN;
        long nextChange = 0;
        Random random;

        public State(long seed) {
            random = new Random(seed);
        }

        public float strength() { return strength; }

        public void tick(long gameTime) {
            if (gameTime >= nextChange) {
                target = MIN + random.nextFloat() * (MAX - MIN);
                nextChange = gameTime + CHANGE_MIN_TICKS + random.nextInt(CHANGE_MAX_TICKS - CHANGE_MIN_TICKS + 1);
            }
            float step = GLIDE_PER_SECOND / 20f;
            if (strength < target)
                strength = Math.min(target, strength + step);
            else if (strength > target)
                strength = Math.max(target, strength - step);
        }
    }

    private static State live = new State(0x57696E64L);

    /** The current wind strength (server: live; client: the last synced value). */
    public static float strength() { return live.strength; }

    /** Income multiplier for a wind-driven building: 1.0 at the average wind. */
    public static float multiplier() { return live.strength / MEAN; }

    /** Called once per server tick. */
    public static void tick(long gameTime) { live.tick(gameTime); }

    /** A new match: the average wind, a fresh sequence. */
    public static void reset(long seed) { live = new State(seed); }

    /** Client side: the synced strength. */
    public static void setFromServer(float s) { live.strength = Math.max(MIN, Math.min(MAX, s)); }
}
