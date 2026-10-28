package com.solegendary.reignofnether.gametest;

import com.mojang.authlib.GameProfile;
import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.mixin.ChunkMapAccessor;
import com.solegendary.reignofnether.mixin.fogofwar.TrackedEntityInvoker;
import com.solegendary.reignofnether.unit.units.villagers.VindicatorUnit;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
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
 * Vanilla tracks an entity only inside a CIRCLE of about the view distance round the player, while the client keeps
 * the whole SQUARE of chunks loaded: an own unit standing on loaded, visible ground near a corner was never sent to
 * its owner, so it could not be drawn, clicked or boxed, and a selected one lost its ring. Own (and allied) units
 * are tracked across the whole loaded square; enemies keep vanilla's circle (and the fog gate).
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
            try {
                int vd = level.getServer().getPlayerList().getViewDistance();
                // the viewer near the far corner of its loaded square from the unit: same chunk distance as the edge
                // of the loaded ground, but ~1.4x the view distance away in a straight line
                double off = vd * 16 - 8;
                viewer.moveTo(mine.getX() - off, mine.getY() + 40, mine.getZ() - off, 0, 0);
                int dcx = Math.abs((viewer.getBlockX() >> 4) - (mine.getBlockX() >> 4));
                int dcz = Math.abs((viewer.getBlockZ() >> 4) - (mine.getBlockZ() >> 4));
                double dist = Math.sqrt(2) * off;
                if (Math.max(dcx, dcz) > vd || dist <= (vd + 1) * 16)
                    failure = "test geometry: chunk distance " + Math.max(dcx, dcz) + " / " + vd + ", " + (int) dist + " blocks";

                if (failure == null && !trackFor(level, mine, viewer))
                    failure = "an own unit " + (int) dist + " blocks away, " + Math.max(dcx, dcz)
                        + " chunks inside the owner's loaded square (view distance " + vd + "), is not sent to its owner";
                if (failure == null && trackFor(level, enemy, viewer))
                    failure = "an enemy unit outside vanilla's tracking circle is sent to the viewer";

                // the reach ends with the loaded ground: past it nothing shows anyway (and the client keeps the selection)
                viewer.moveTo(mine.getX() - (vd + 3) * 16, mine.getY() + 40, mine.getZ(), 0, 0);
                if (failure == null && trackFor(level, mine, viewer))
                    failure = "an own unit " + (vd + 3) + " chunks away, outside the loaded square, is still tracked";
            } catch (ReflectiveOperationException | RuntimeException ex) {
                failure = "tracking lookup failed: " + ex;
            } finally {
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
