package com.solegendary.reignofnether.unit.reactions;

import net.minecraft.client.Minecraft;

import java.util.HashMap;
import java.util.Map;

/**
 * Client side of the reactions: which units are braced right now (by entity id, until a client game time), read by
 * the health bar renderer for the shield rim. Entries expire by time; a unit that died simply stops being asked.
 */
public final class ReactionClientEvents {
    private ReactionClientEvents() { }

    private static final Map<Integer, Long> BRACED_UNTIL = new HashMap<>();

    public static void receive(ReactionClientboundPacket.Kind kind, int entityId, int ticks) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null)
            return;
        if (kind == ReactionClientboundPacket.Kind.BRACE)
            BRACED_UNTIL.put(entityId, mc.level.getGameTime() + ticks);
        if (BRACED_UNTIL.size() > 256)   // a long match: drop what has expired
            BRACED_UNTIL.values().removeIf(t -> t < mc.level.getGameTime());
    }

    public static boolean isBraced(int entityId) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null)
            return false;
        Long until = BRACED_UNTIL.get(entityId);
        return until != null && mc.level.getGameTime() < until;
    }

    public static void clear() {
        BRACED_UNTIL.clear();
    }
}
