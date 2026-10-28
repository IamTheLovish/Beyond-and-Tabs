package com.solegendary.reignofnether.hero.wager;

import com.solegendary.reignofnether.ReignOfNether;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

/**
 * The Wager Book's temporary blocks (earth walls, a mound, a campfire): placed only into air with nobody standing there,
 * and always taken down again - on their own clock, not the hero's, so a wall outlives a hero who dies but never the
 * phenomenon's duration ("no phenomenon lasts past 90 s"), and nothing is left behind when the server stops. A block a
 * player or a building changed meanwhile is left alone.
 */
@Mod.EventBusSubscriber(modid = ReignOfNether.MOD_ID)
public final class WagerBlocks {
    private WagerBlocks() { }

    private record Placed(ServerLevel level, BlockPos pos, BlockState state, long until) { }

    private static final List<Placed> PLACED = new ArrayList<>();

    /** Places {@code state} at {@code pos} until {@code until} if the spot is air and empty; true if it did. */
    static boolean place(ServerLevel level, BlockPos pos, BlockState state, long until) {
        if (!level.isLoaded(pos) || !level.getBlockState(pos).isAir()
                || !level.getEntitiesOfClass(LivingEntity.class, new AABB(pos)).isEmpty())
            return false;
        level.setBlockAndUpdate(pos, state);
        PLACED.add(new Placed(level, pos.immutable(), state, until));
        return true;
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent evt) {
        if (evt.phase != TickEvent.Phase.END || PLACED.isEmpty())
            return;
        for (int i = PLACED.size() - 1; i >= 0; i--) {
            Placed p = PLACED.get(i);
            if (p.level.getGameTime() >= p.until) {
                takeDown(p);
                PLACED.remove(i);
            }
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent evt) {
        for (Placed p : PLACED)
            takeDown(p);
        PLACED.clear();
    }

    private static void takeDown(Placed p) {
        if (p.level.isLoaded(p.pos) && p.level.getBlockState(p.pos) == p.state)
            p.level.removeBlock(p.pos, false);
    }
}
