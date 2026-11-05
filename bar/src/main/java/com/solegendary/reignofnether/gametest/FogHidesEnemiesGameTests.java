package com.solegendary.reignofnether.gametest;

import com.mojang.authlib.GameProfile;
import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.fogofwar.FogOfWarServerEvents;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.unit.units.villagers.VindicatorUnit;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * "Fog of war means you can't see enemy units in it": the one entity check behind fog-gated tracking
 * (FogOfWarServerEvents.isEntityVisibleTo, used by TrackedEntityMixin and re-run every fog tick) hides an enemy unit
 * outside the viewer's vision, always shows the viewer's own units, and shows the enemy once a friendly unit stands
 * within sight of it. Vision is computed by the same code the fog tick runs (recomputeVisionFor), for a fake player
 * the tick never sees; fog itself stays off globally so no other test changes.
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class FogHidesEnemiesGameTests {

    static VindicatorUnit unit(GameTestHelper helper, String owner, double x, double z) {
        VindicatorUnit v = EntityRegistrar.VINDICATOR_UNIT.get().create(helper.getLevel());
        if (v == null)
            return null;
        BlockPos o = helper.absolutePos(new BlockPos(0, 2, 0));
        v.moveTo(o.getX() + x, o.getY(), o.getZ() + z, 0, 0);
        v.setOwnerName(owner);
        helper.getLevel().addFreshEntity(v);
        return v;
    }

    @GameTest(template = SkirmishGameTests.ARENA, timeoutTicks = 100)
    public static void fog_hides_enemy_until_a_friendly_is_in_range(GameTestHelper helper) {
        String me = "gt_foghide_" + Long.toHexString(helper.absolutePos(BlockPos.ZERO).asLong());
        String foe = me + "_foe";
        ServerPlayer viewer = FakePlayerFactory.get(helper.getLevel(),
                new GameProfile(UUID.nameUUIDFromBytes(me.getBytes(StandardCharsets.UTF_8)), me));
        // park the viewer's own body far away so its first-person sight bubble can't cover the test
        BlockPos o = helper.absolutePos(BlockPos.ZERO);
        viewer.moveTo(o.getX() + 4000, o.getY(), o.getZ() + 4000, 0, 0);

        VindicatorUnit mine = unit(helper, me, 2.5, 2.5);
        if (mine == null) {
            helper.fail("could not create the friendly unit");
            return;
        }
        int sight = mine.getSightRange();
        if (sight <= 0 || sight > FogOfWarServerEvents.MAX_SIGHT_BLOCKS - 8) {
            mine.discard();
            helper.fail("unexpected friendly sight range " + sight);
            return;
        }
        double far = sight + 12;   // well outside the friendly's circle
        VindicatorUnit enemy = unit(helper, foe, 2.5 + far, 2.5);
        if (enemy == null) {
            mine.discard();
            helper.fail("could not create the enemy unit");
            return;
        }

        helper.runAfterDelay(2, () -> {
            String failure = null;
            try {
                // fog off for this viewer (it is off globally in the test server): everything shows
                if (!FogOfWarServerEvents.isEntityVisibleTo(viewer, enemy))
                    failure = "with fog off an enemy unit must be visible";

                FogOfWarServerEvents.recomputeVisionFor(viewer);
                if (failure == null && !FogOfWarServerEvents.isEntityVisibleUnderFog(viewer, mine))
                    failure = "the viewer's own unit is hidden by fog";
                if (failure == null && FogOfWarServerEvents.isEntityVisibleUnderFog(viewer, enemy))
                    failure = "an enemy unit " + (int) far + " blocks from the only friendly (sight " + sight
                        + ") is visible under fog";

                // own units show even with no vision stored at all (the masks lag the units by up to 10 ticks)
                FogOfWarServerEvents.onPlayerDisconnect(viewer.getUUID());
                if (failure == null && !FogOfWarServerEvents.isEntityVisibleUnderFog(viewer, mine))
                    failure = "an own unit outside the stored vision is hidden";

                // a friendly walks up to the enemy: next vision recompute shows it
                mine.moveTo(enemy.getX() - Math.max(1, sight / 2.0), enemy.getY(), enemy.getZ(), 0, 0);
                FogOfWarServerEvents.recomputeVisionFor(viewer);
                if (failure == null && !FogOfWarServerEvents.isEntityVisibleUnderFog(viewer, enemy))
                    failure = "the enemy stays hidden with a friendly " + Math.max(1, sight / 2.0)
                        + " blocks away (sight " + sight + ")";
            } finally {
                FogOfWarServerEvents.onPlayerDisconnect(viewer.getUUID());
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
