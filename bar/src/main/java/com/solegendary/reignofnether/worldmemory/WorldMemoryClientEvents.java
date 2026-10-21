package com.solegendary.reignofnether.worldmemory;

import com.solegendary.reignofnether.ReignOfNether;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Client hooks for the ground memory: a chunk arriving (its regions need the real ground heights), the logout (drop
 * everything), and a "[Frame] scars" log line every 10 s when there is anything to draw - the eyes tour's and
 * lovish's latest.log's record of what the mesh costs. Self-registered, client only.
 */
@Mod.EventBusSubscriber(modid = ReignOfNether.MOD_ID, value = Dist.CLIENT)
public final class WorldMemoryClientEvents {
    private WorldMemoryClientEvents() { }

    static final int LOG_EVERY_TICKS = 200;
    private static int ticks = 0;

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load evt) {
        if (evt.getLevel() != null && evt.getLevel().isClientSide() && evt.getChunk() instanceof LevelChunk chunk)
            GroundMemoryClient.onChunkLoaded(chunk.getPos().x, chunk.getPos().z);
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut evt) {
        GroundMemoryClient.clear();
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent evt) {
        if (evt.phase != TickEvent.Phase.END)
            return;
        if (Minecraft.getInstance().level == null) {
            ticks = 0;
            return;
        }
        if (++ticks % LOG_EVERY_TICKS == 0 && GroundMemoryClient.heldChunks() > 0)
            ReignOfNether.LOGGER.info("[Frame] scars: {}", GroundMemoryClient.status());
    }
}
