package com.solegendary.reignofnether.gametest;

import com.mojang.authlib.GameProfile;
import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.mixin.ChunkMapAccessor;
import com.solegendary.reignofnether.mixin.fogofwar.TrackedEntityInvoker;
import com.solegendary.reignofnether.unit.units.villagers.VindicatorUnit;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * lovish, Oct 27: "units cant be selected if you move too far away from them ... they get deselected". The RTS
 * camera is the player entity, slid back along the view ray, so the ground on screen can be ~200 blocks from it.
 * Vanilla tracks an entity only inside a circle of view distance * 16 blocks round the player, while the client is
 * sent the chunks ChunkMap.isChunkInRange passes (out to view distance + 1 on the axes): an own unit standing on
 * loaded, visible ground was never sent to its owner, so it could not be drawn, clicked or boxed, and a selected one
 * lost its ring. Own (and allied) units are tracked across the chunks the owner is sent, and no further; enemies keep
 * vanilla's circle (and the fog gate).
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class OwnUnitTrackingGameTests {

    private static boolean seenBy(Object tracked, ServerPlayer viewer) throws ReflectiveOperationException {
        Field f = tracked.getClass().getDeclaredField("seenBy");
        f.setAccessible(true);
        return ((Set<?>) f.get(tracked)).contains(viewer.connection);
    }

    private static boolean trackFor(ServerLevel level, Entity e, ServerPlayer viewer) throws ReflectiveOperationException {
        Object tracked = ((ChunkMapAccessor) level.getChunkSource().chunkMap).reignofnether$getEntityMap().get(e.getId());
        if (!(tracked instanceof TrackedEntityInvoker inv))
            throw new IllegalStateException("unit " + e.getId() + " has no TrackedEntity");
        inv.reignofnether$updatePlayers(List.of(viewer));
        return seenBy(tracked, viewer);
    }

    @GameTest(template = SkirmishGameTests.ARENA, timeoutTicks = 100)
    public static void own_units_stay_tracked_across_the_loaded_square(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String me = "gt_owntrack_" + Long.toHexString(helper.absolutePos(net.minecraft.core.BlockPos.ZERO).asLong());
        String foe = me + "_foe";
        ServerPlayer viewer = FakePlayerFactory.get(level,
                new GameProfile(UUID.nameUUIDFromBytes(me.getBytes(StandardCharsets.UTF_8)), me));

        VindicatorUnit mine = FogHidesEnemiesGameTests.unit(helper, me, 2.5, 2.5);
        VindicatorUnit enemy = FogHidesEnemiesGameTests.unit(helper, foe, 3.5, 2.5);
        if (mine == null || enemy == null) {
            if (mine != null) mine.discard();
            if (enemy != null) enemy.discard();
            helper.fail("could not create the units");
            return;
        }

        helper.runAfterDelay(2, () -> {
            String failure = null;
            // the headless server's chunk map runs at the minimum view distance (2): give it a real one for this
            // check and put the old one back. No real players here, so nothing else notices.
            Integer oldChunkMapVd = null;
            try {
                Field vdField = ChunkMap.class.getDeclaredField("viewDistance");
                vdField.setAccessible(true);
                oldChunkMapVd = vdField.getInt(level.getChunkSource().chunkMap);
                level.getChunkSource().setViewDistance(10);
                int vd = vdField.getInt(level.getChunkSource().chunkMap);   // 1.20.1 keeps the view distance as is
                ChunkPos uc = mine.chunkPosition();

                // the viewer vd+1 chunks off along an axis, at the far edge of its chunk: vanilla still sends that
                // chunk (ChunkMap.isChunkInRange), but the unit is more than vd*16 blocks off, outside the circle
                double vx = (uc.x - (vd + 1)) * 16 + 0.5;
                viewer.moveTo(vx, mine.getY() + 40, mine.getZ(), 0, 0);
                double dist = mine.getX() - vx;
                if (!ChunkMap.isChunkInRange(uc.x - (vd + 1), uc.z, uc.x, uc.z, vd) || dist <= vd * 16)
                    failure = "test geometry: axis chunk " + (vd + 1) + " not loaded at view distance " + vd
                        + ", or only " + (int) dist + " blocks";

                if (failure == null && !trackFor(level, mine, viewer))
                    failure = "an own unit " + (int) dist + " blocks away, " + (vd + 1)
                        + " chunks off on the owner's loaded ground (view distance " + vd + "), is not sent to its owner";
                // an enemy beside it gets no such reach (fog is off for this viewer, so only vanilla's circle applies)
                if (failure == null && trackFor(level, enemy, viewer))
                    failure = "an enemy unit on the same loaded ground, outside vanilla's circle, is sent to the viewer";

                // a diagonal chunk inside the square but outside the rounded edge the client is sent: no reach there
                int d = 1;
                while (d <= vd && ChunkMap.isChunkInRange(0, 0, d, d, vd)) d++;
                if (failure == null && d > vd)
                    failure = "test geometry: no unsent diagonal chunk inside view distance " + vd;
                viewer.moveTo((uc.x - d) * 16 + 8, mine.getY() + 40, (uc.z - d) * 16 + 8, 0, 0);
                if (failure == null && trackFor(level, mine, viewer))
                    failure = "an own unit in diagonal chunk (" + d + ", " + d + "), which the client is never sent, is tracked";

                // the reach ends with the loaded ground on the axis too (and the client keeps the selection)
                viewer.moveTo((uc.x - (vd + 2)) * 16 + 8, mine.getY() + 40, mine.getZ(), 0, 0);
                if (failure == null && trackFor(level, mine, viewer))
                    failure = "an own unit " + (vd + 2) + " chunks away, past the loaded ground, is still tracked";
            } catch (ReflectiveOperationException | RuntimeException ex) {
                failure = "tracking lookup failed: " + ex;
            } finally {
                if (oldChunkMapVd != null)
                    level.getChunkSource().setViewDistance(oldChunkMapVd);
                mine.discard();
                enemy.discard();
            }
            if (failure != null)
                helper.fail(failure);
            else
                helper.succeed();
        });
    }
}
