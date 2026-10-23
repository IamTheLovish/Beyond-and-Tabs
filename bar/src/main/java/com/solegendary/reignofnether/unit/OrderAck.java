package com.solegendary.reignofnether.unit;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * A unit's first step (or turn) answering an order goes to the players straight away.
 * <p>
 * The server tells each watching client where a mob is from its tracker (ServerEntity.sendChanges), and for a mob that
 * happens every 3rd tick - the entity type's update interval - unless something asks for it sooner. So a unit that
 * took its first step on an order was shown moving 0-2 ticks later, depending only on where that 3-tick beat stood
 * (OrderLatencyGameTests: up to 2 of the "seen" ticks; lovish Oct 22 20:23, "it feels like there's sometimes a delay
 * in actions"). The beat stays for everything else - it is what keeps a few hundred walking units cheap on the wire -
 * but the tick a unit first moves or turns after an order, it sets the entity's own "send me now" flag
 * ({@code hasImpulse}, the flag a knockback sets), and the tracker sends its position, facing and motion at its very
 * next run. One extra packet per unit per order.
 * <p>
 * Server only. {@link #expect} is called for each unit that takes an order (UnitActionItem); {@link #tick} from the
 * unit's own tick, after it has moved. A unit that has neither moved nor turned {@link #WATCH_TICKS} after its order
 * (a Stop, a Hold, a target already in range) is forgotten.
 */
public final class OrderAck {
    private OrderAck() { }

    /** How long an order waits for its unit's first step or turn. */
    static final int WATCH_TICKS = 40;
    /** A turn smaller than this is not an answer (a mob's idle head-and-body drift). */
    static final float TURN_DEGREES = 2.0f;

    // unit -> the game time the watch ends. Weak keys: a unit removed before it moved leaves nothing behind.
    private static final Map<Mob, Long> WAITING = new WeakHashMap<>();

    public static void expect(Mob mob) {
        if (!mob.level().isClientSide())
            WAITING.put(mob, mob.level().getGameTime() + WATCH_TICKS);
    }

    /** From the unit's tick, once it has moved this tick (Unit.tick runs after the mob's own tick). */
    public static void tick(Mob mob) {
        if (WAITING.isEmpty())
            return;
        Long until = WAITING.get(mob);
        if (until == null)
            return;
        boolean moved = mob.getX() != mob.xo || mob.getZ() != mob.zo;
        boolean turned = Math.abs(Mth.wrapDegrees(mob.getYRot() - mob.yRotO)) >= TURN_DEGREES;
        if (moved || turned) {
            mob.hasImpulse = true;
            WAITING.remove(mob);
        } else if (mob.level().getGameTime() > until) {
            WAITING.remove(mob);
        }
    }
}
