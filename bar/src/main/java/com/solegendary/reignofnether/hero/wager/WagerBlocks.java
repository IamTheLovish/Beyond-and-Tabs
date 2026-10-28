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
    public static boolean place(ServerLevel level, BlockPos pos, BlockState state, long until) {
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

    // ---- red-proof stubs (Mode A B2): the seams the GameTest drives
    public static void sweep(ServerLevel level, long now, java.util.function.Predicate<BlockPos> loaded) {
        for (int i = PLACED.size() - 1; i >= 0; i--) {
            Placed p = PLACED.get(i);
            if (p.level == level && now >= p.until) {
                if (loaded.test(p.pos))
                    p.level.removeBlock(p.pos, false);
                PLACED.remove(i);
            }
        }
    }

    public static void stop(ServerLevel level, java.util.function.Predicate<BlockPos> loaded) {
        for (int i = PLACED.size() - 1; i >= 0; i--)
            if (PLACED.get(i).level == level)
                PLACED.remove(i);
    }

    public static int pendingAt(ServerLevel level, BlockPos pos) {
        int n = 0;
        for (Placed p : PLACED)
            if (p.level == level && p.pos.equals(pos))
                n++;
        return n;
    }

    public static net.minecraft.nbt.CompoundTag saveTag() {
        return new net.minecraft.nbt.CompoundTag();
    }

    public static void loadTag(net.minecraft.server.MinecraftServer server, net.minecraft.nbt.CompoundTag tag) { }

    private static void takeDown(Placed p) {
        if (p.level.isLoaded(p.pos) && p.level.getBlockState(p.pos) == p.state)
            p.level.removeBlock(p.pos, false);
    }
}
