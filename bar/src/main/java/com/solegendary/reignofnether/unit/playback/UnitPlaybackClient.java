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

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent evt) {
        if (evt.phase == TickEvent.Phase.START)
            CLOCK.clientTick();
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
