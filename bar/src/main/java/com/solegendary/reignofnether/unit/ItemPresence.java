package com.solegendary.reignofnether.unit;

import com.solegendary.reignofnether.ReignOfNether;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Whether a server level may hold an item on the ground, so Unit.tick can skip its item-pickup query when it cannot
 * (Oct 26 perf audit: every unit ran a getEntitiesOfClass(ItemEntity) box query every 2nd tick - ~500 world queries a
 * tick at 1,000 units - while a match usually has no item lying anywhere).
 * <p>
 * Exact in the safe direction only: "no" means no ItemEntity existed in the level when its tick started and none has
 * joined since. Each level tick starts with one walk over the level's entities (stops at the first item), and every
 * item that joins mid-tick (a drop, a death, a chunk load) flips the answer to "yes" at once, so a unit ticking after
 * the drop still queries in that same tick. A level never seen answers "yes". Server thread only.
 */
@Mod.EventBusSubscriber(modid = ReignOfNether.MOD_ID)
public final class ItemPresence {
    private ItemPresence() { }

    private static final Map<Level, Boolean> MAY_HAVE_ITEMS = new WeakHashMap<>();

    /** False only when the level certainly holds no ItemEntity right now. */
    public static boolean mayHaveItems(Level level) {
        Boolean may = MAY_HAVE_ITEMS.get(level);
        return may == null || may;
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent evt) {
        if (evt.phase != TickEvent.Phase.START || !(evt.level instanceof ServerLevel level))
            return;
        boolean any = false;
        for (Entity e : level.getAllEntities()) {
            if (e instanceof ItemEntity) {
                any = true;
                break;
            }
        }
        MAY_HAVE_ITEMS.put(level, any);
    }

    // counted even when another handler cancels the join: a spare "yes" only costs the query it would have run anyway
    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent evt) {
        if (evt.getEntity() instanceof ItemEntity && !evt.getLevel().isClientSide())
            MAY_HAVE_ITEMS.put(evt.getLevel(), true);
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent evt) {
        MAY_HAVE_ITEMS.clear();
    }
}
