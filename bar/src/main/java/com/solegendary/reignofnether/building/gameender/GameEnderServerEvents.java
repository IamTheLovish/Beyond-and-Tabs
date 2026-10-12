package com.solegendary.reignofnether.building.gameender;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.unit.DragonRaiseServerEvents;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

/**
 * Server housekeeping for the game enders: the Necropolis's risen crumble when their time is up. Their expiry tick
 * lives in the entity's persistent data, so a risen host that was saved with the world is picked up again when it
 * loads (EntityJoinLevelEvent) instead of standing forever. One sweep a second over a list that is empty almost the
 * whole match.
 */
@Mod.EventBusSubscriber(modid = ReignOfNether.MOD_ID)
public class GameEnderServerEvents {

    static final String KEY_EXPIRE = "bt_necro_expire";
    private static final List<Entity> risen = new ArrayList<>();

    static void markRisen(Entity e, long expireTick) {
        e.addTag(DragonRaiseServerEvents.TAG);   // no wreck, no free metal (WreckServerEvents.onDeath)
        e.getPersistentData().putLong(KEY_EXPIRE, expireTick);
        if (!risen.contains(e))
            risen.add(e);
    }

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent evt) {
        if (evt.getLevel().isClientSide())
            return;
        Entity e = evt.getEntity();
        // the scoreboard tag first: Forge's getPersistentData() creates (and from then on saves) an empty ForgeData tag
        // on every entity it is asked about, and this runs for every entity that ever joins (R2-03)
        if (e.getTags().contains(DragonRaiseServerEvents.TAG) && e.getPersistentData().contains(KEY_EXPIRE) && !risen.contains(e))
            risen.add(e);
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent evt) {
        if (evt.phase != TickEvent.Phase.END || evt.getServer() == null || risen.isEmpty())
            return;
        ServerLevel overworld = evt.getServer().overworld();
        if (overworld.getGameTime() % 20 != 0)
            return;
        long now = overworld.getGameTime();
        for (int i = risen.size() - 1; i >= 0; i--) {
            Entity e = risen.get(i);
            if (e.isRemoved()) {
                risen.remove(i);
            } else if (now >= e.getPersistentData().getLong(KEY_EXPIRE)) {
                if (e.level() instanceof ServerLevel sl)
                    sl.sendParticles(ParticleTypes.ASH, e.getX(), e.getY() + 0.8, e.getZ(), 20, 0.3, 0.5, 0.3, 0.02);
                e.discard();   // crumbles: no death event, so no wreck and no kill credit
                risen.remove(i);
            }
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent evt) {
        risen.clear();
    }
}
