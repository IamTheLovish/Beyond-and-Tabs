package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.api.ReignOfNetherRegistries;
import com.solegendary.reignofnether.building.Building;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.BuildingServerEvents;
import com.solegendary.reignofnether.building.Buildings;
import com.solegendary.reignofnether.building.ExitArrowClientboundPacket;
import com.solegendary.reignofnether.building.ExitArrowServerboundPacket;
import com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement;
import com.solegendary.reignofnether.building.production.ProductionBuilding;

import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Rotation;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * The exit arrow on a remote server (PR #271, plan-pr-271-remote-exit-arrow.md): the client cannot dry-run a building's
 * spawn rule, so it asks the server (ExitArrowServerboundPacket) and gets the spec per building type and the cell per
 * own building back (ExitArrowClientboundPacket). The request is untrusted input (Twin A review D-2).
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class ExitArrowSyncGameTests {

    static final String ARENA = SkirmishGameTests.ARENA;

    static String key(Building b) {
        return ReignOfNetherRegistries.BUILDING.getKey(b).toString();
    }

    /** Owner gets its building's safe cell; an enemy asking about the same origin gets none; fog hides it too. */
    @GameTest(template = ARENA, timeoutTicks = 60)
    public static void exit_arrow_sync_owner_gets_a_cell_enemy_gets_none(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = "gametest_exitsync_own_" + helper.absolutePos(BlockPos.ZERO).asLong();
        ExitArrowGameTests.Stamp s = ExitArrowGameTests.stamp(helper, Buildings.BARRACKS, Rotation.NONE, owner);
        if (s == null) {
            helper.fail("(setup) the Barracks is not a production placement");
            return;
        }
        List<String> problems = new ArrayList<>();
        try {
            ProductionPlacement.ExitSpec spec = s.pp.exitSpec(level);
            BlockPos want = s.pp.exitArrowPos(level, spec, true);
            ExitArrowServerboundPacket ask = new ExitArrowServerboundPacket("", List.of(s.pp.originPos));
            var mine = ask.reply(level, owner, p -> true).entries();
            if (mine.size() != 1 || !mine.get(0).show() || !want.equals(mine.get(0).cell()))
                problems.add("owner: expected the safe cell " + want + ", got " + mine);
            var foe = ask.reply(level, "gametest_exitsync_enemy", p -> true).entries();
            if (foe.size() != 1 || foe.get(0).show())
                problems.add("enemy asking about another player's building got " + foe);
            var fogged = ask.reply(level, owner, p -> false).entries();
            if (fogged.size() != 1 || fogged.get(0).show())
                problems.add("a cell in fog was sent: " + fogged);
        } finally {
            s.undo();
        }
        TerrainGameTests.finish(helper, problems);
    }

    /** D-2 point 2: a forged request - an enemy origin, a position with no building, a bad type key - yields no cell. */
    @GameTest(template = ARENA, timeoutTicks = 60)
    public static void exit_arrow_sync_forged_request_gets_no_cell(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = "gametest_exitsync_victim_" + helper.absolutePos(BlockPos.ZERO).asLong();
        ExitArrowGameTests.Stamp s = ExitArrowGameTests.stamp(helper, Buildings.BARRACKS, Rotation.NONE, owner);
        if (s == null) {
            helper.fail("(setup) the Barracks is not a production placement");
            return;
        }
        List<String> problems = new ArrayList<>();
        try {
            String forger = "gametest_exitsync_forger";
            BlockPos nowhere = s.pp.originPos.offset(500, 0, 500);
            var r = new ExitArrowServerboundPacket(key(Buildings.BARRACKS), List.of(s.pp.originPos, nowhere)).reply(level, forger, p -> true);
            for (var e : r.entries())
                if (e.show() || e.cell() != null)
                    problems.add("forged request got a cell: " + e);
            var junk = new ExitArrowServerboundPacket("not a key!!", List.of(s.pp.originPos)).reply(level, owner, p -> true);
            if (junk.hasSpec())
                problems.add("a bad type key got a spec");
            var notABuilding = new ExitArrowServerboundPacket("minecraft:stone", List.of()).reply(level, owner, p -> true);
            if (notABuilding.hasSpec())
                problems.add("a key that is not a building got a spec");
        } finally {
            s.undo();
        }
        TerrainGameTests.finish(helper, problems);
    }

    /** The spec per type equals exitSpec of a real placement, and answering builds no building. */
    @GameTest(template = ARENA, timeoutTicks = 60)
    public static void exit_arrow_sync_spec_per_type_equals_exit_spec_and_builds_nothing(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = "gametest_exitsync_spec_" + helper.absolutePos(BlockPos.ZERO).asLong();
        int before = BuildingServerEvents.getBuildings().size();
        List<String> problems = new ArrayList<>();
        int withSpec = 0;
        for (ProductionBuilding b : ExitArrowGameTests.productionBuildings()) {
            BuildingPlacement bp = b.createBuildingPlacement(level, helper.absolutePos(new BlockPos(16, ExitArrowGameTests.LIFT, 16)), Rotation.NONE, owner);
            ProductionPlacement.ExitSpec want = ((ProductionPlacement) bp).exitSpec(level);
            var got = new ExitArrowServerboundPacket(key(b), List.of()).reply(level, owner, p -> true);
            if (got.hasSpec() != (want != null)
                    || (want != null && (got.indoors() != want.indoors() || !got.offset().equals(want.offset()))))
                problems.add(b.name + ": sent hasSpec=" + got.hasSpec() + " indoors=" + got.indoors() + " " + got.offset() + ", exitSpec=" + want);
            if (want != null)
                withSpec++;
        }
        if (withSpec < 20)
            problems.add("only " + withSpec + " types with a spec");
        if (BuildingServerEvents.getBuildings().size() != before)
            problems.add("answering added a building to the server list");
        TerrainGameTests.finish(helper, problems);
    }

    /** Both packets survive encode/decode; a request for 9 origins is refused at decode, 8 are fine. */
    @GameTest(template = ARENA, timeoutTicks = 20)
    public static void exit_arrow_sync_packets_round_trip_and_nine_origins_are_refused(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        List<BlockPos> eight = new ArrayList<>();
        for (int i = 0; i < 8; i++)
            eight.add(new BlockPos(i, 64, -i));
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        new ExitArrowServerboundPacket(key(Buildings.BARRACKS), eight).encode(buf);
        ExitArrowServerboundPacket up = new ExitArrowServerboundPacket(buf);
        if (!up.typeKey().equals(key(Buildings.BARRACKS)) || !up.origins().equals(eight))
            problems.add("serverbound did not round trip: " + up.typeKey() + " " + up.origins());

        var down = new ExitArrowClientboundPacket(key(Buildings.BARRACKS), true, true, new Vec3i(1, 0, -2),
            List.of(new ExitArrowClientboundPacket.Entry(new BlockPos(3, 64, 4), true, new BlockPos(5, 64, 6)),
                    new ExitArrowClientboundPacket.Entry(new BlockPos(7, 64, 8), false, null)));
        FriendlyByteBuf buf2 = new FriendlyByteBuf(Unpooled.buffer());
        down.encode(buf2);
        ExitArrowClientboundPacket back = new ExitArrowClientboundPacket(buf2);
        if (!back.typeKey().equals(down.typeKey()) || !back.hasSpec() || !back.indoors() || !back.offset().equals(down.offset())
                || !back.entries().equals(down.entries()))
            problems.add("clientbound did not round trip: " + back.entries());

        List<BlockPos> nine = new ArrayList<>(eight);
        nine.add(BlockPos.ZERO);
        FriendlyByteBuf buf3 = new FriendlyByteBuf(Unpooled.buffer());
        new ExitArrowServerboundPacket("", nine).encode(buf3);
        try {
            new ExitArrowServerboundPacket(buf3);
            problems.add("9 origins were accepted");
        } catch (RuntimeException expected) {
            // refused
        }
        TerrainGameTests.finish(helper, problems);
    }

    /** What the client works out from the sent spec (exitNominalPos) is the server's own non-safe position. */
    @GameTest(template = ARENA, timeoutTicks = 60)
    public static void exit_arrow_sync_client_nominal_cell_equals_the_server_unsafe_cell(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = "gametest_exitsync_nom_" + helper.absolutePos(BlockPos.ZERO).asLong();
        List<String> problems = new ArrayList<>();
        int checked = 0;
        for (ProductionBuilding b : ExitArrowGameTests.productionBuildings())
            for (Rotation rot : new Rotation[] { Rotation.NONE, Rotation.CLOCKWISE_90 }) {
                ProductionPlacement pp = (ProductionPlacement) b.createBuildingPlacement(level,
                    helper.absolutePos(new BlockPos(16, ExitArrowGameTests.LIFT, 16)), rot, owner);
                ProductionPlacement.ExitSpec spec = pp.exitSpec(level);
                if (spec == null)
                    continue;
                var sent = new ExitArrowServerboundPacket(key(b), List.of()).reply(level, owner, p -> true);
                BlockPos client = pp.exitNominalPos(level, sent.indoors(), sent.offset());
                BlockPos server = pp.exitArrowPos(level, spec, false);
                checked++;
                if (!client.equals(server))
                    problems.add(b.name + " " + rot + ": client " + client + " vs server " + server);
            }
        if (checked < 40)
            problems.add("only " + checked + " building/rotation pairs checked");
        TerrainGameTests.finish(helper, problems);
    }
}
