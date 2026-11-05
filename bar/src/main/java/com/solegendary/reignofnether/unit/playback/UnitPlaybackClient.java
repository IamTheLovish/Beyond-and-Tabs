package com.solegendary.reignofnether.unit.playback;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.hero.PossessionClientEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.modelling.motion.UnitPlayback;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The client side of the stamped playback (motion fluidity, lovish's pick "A: Smooth buffer", Nov 5): the server's tick
 * clock as this client hears it (UnitClockClientboundPacket), moved on once a client tick before the units are drawn.
 * UnitPlaybackMixin routes each unit's position packets into its UnitPlayback.Track and draws it from there.
 * Until the first stamp arrives (a server without this mod's clock) every unit keeps vanilla's lerp.
 */
@Mod.EventBusSubscriber(modid = ReignOfNether.MOD_ID, value = Dist.CLIENT)
public final class UnitPlaybackClient {

    private UnitPlaybackClient() { }

    public static final UnitPlayback.Clock CLOCK = new UnitPlayback.Clock();

    static void heard(int tick) {
        CLOCK.heard(tick);
    }

    // What the move or teleport packet being handled carries (UnitPlayback.Track.POS | ROT), set by
    // UnitPlaybackPacketMixin just before ClientPacketListener calls lerpTo, on the main thread; -1: none (a lerpTo
    // from elsewhere). A teleport's own position too: vanilla hands lerpTo the drawn position when it is within 1/32.
    private static int packetKind = -1;
    private static boolean teleport;
    private static double teleportX, teleportY, teleportZ;

    /** A move packet is being handed to lerpTo (ClientboundMoveEntityPacket.hasPosition / hasRotation). */
    public static void moving(boolean hasPosition, boolean hasRotation) {
        packetKind = (hasPosition ? UnitPlayback.Track.POS : 0) | (hasRotation ? UnitPlayback.Track.ROT : 0);
        teleport = false;
    }

    /** A teleport packet is being handed to lerpTo: it carries all, at its own position. */
    public static void teleporting(double x, double y, double z) {
        packetKind = UnitPlayback.Track.POS | UnitPlayback.Track.ROT;
        teleport = true;
        teleportX = x;
        teleportY = y;
        teleportZ = z;
    }

    /** What the packet now being handled carries, once (-1: lerpTo was not called for a packet). */
    public static int takePacketKind() {
        int kind = packetKind;
        packetKind = -1;
        return kind;
    }

    /** Whether the packet whose kind was just taken is a teleport (its own position: teleportX() on). */
    public static boolean wasTeleport() {
        return teleport;
    }

    public static double teleportX() { return teleportX; }
    public static double teleportY() { return teleportY; }
    public static double teleportZ() { return teleportZ; }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent evt) {
        if (evt.phase == TickEvent.Phase.START) {
            CLOCK.clientTick();
            packetKind = -1;   // a kind set for a packet whose lerpTo was not a LivingEntity's never outlives the tick
        }
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut evt) {
        CLOCK.reset();
    }

    /**
     * Whether this client-side entity is drawn from its stamps: a unit, once the clock runs, not riding (its vehicle
     * places it) and not the unit this player possesses (the possession prediction drives that one, and hands it back
     * to the server's packets itself).
     */
    public static boolean drawsFromStamps(LivingEntity e) {
        return e instanceof Unit && CLOCK.ready() && !e.isPassenger() && !e.isControlledByLocalInstance()
            && e.getId() != PossessionClientEvents.getPossessedId();
    }
}
