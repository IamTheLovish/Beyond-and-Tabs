package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.player.CommanderServerEvents;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.packets.UnitIdleWorkerClientBoundPacket;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * The idle-worker list (UnitIdleWorkerClientBoundPacket.countsAsIdleWorker): an idle builder is listed, an idle
 * commander never is, so "select idle builders" can't pull the commander along (Lane C playtest, Oct 20).
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class IdleWorkerGameTests {

    static final String ARENA = SkirmishGameTests.ARENA;

    static Mob spawnWorker(GameTestHelper helper, String owner, int x, int z, List<Entity> made) {
        Mob m = EntityRegistrar.VILLAGER_UNIT.get().create(helper.getLevel());
        if (m == null)
            return null;
        BlockPos at = helper.absolutePos(new BlockPos(x, 2, z));
        m.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
        ((Unit) m).setOwnerName(owner);
        helper.getLevel().addFreshEntity(m);
        made.add(m);
        return m;
    }

    @GameTest(template = ARENA, timeoutTicks = 60)
    public static void idle_worker_list_skips_the_commander(GameTestHelper helper) {
        List<Entity> made = new ArrayList<>();
        String owner = "gametest_idle_" + helper.absolutePos(BlockPos.ZERO).asLong();
        Mob builder = spawnWorker(helper, owner, 3, 3, made);
        Mob commander = spawnWorker(helper, owner, 9, 3, made);
        if (builder == null || commander == null) {
            made.forEach(Entity::discard);
            helper.fail("could not create the builder and the commander");
            return;
        }
        CommanderServerEvents.makeCommander(commander);
        // wait for both to settle (spawn navigation done), then judge with a bounded poll rather than one tick
        helper.succeedWhen(() -> {
            if (!UnitIdleWorkerClientBoundPacket.countsAsIdleWorker(builder))
                helper.fail("an idle plain builder must be listed as an idle worker (is it still moving?)");
            if (UnitIdleWorkerClientBoundPacket.countsAsIdleWorker(commander))
                helper.fail("the commander must never be listed as an idle worker");
            made.forEach(Entity::discard);
        });
    }
}
