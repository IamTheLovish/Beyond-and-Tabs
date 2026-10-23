package com.solegendary.reignofnether.gametest;

import com.mojang.brigadier.tree.CommandNode;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.BuildingServerEvents;
import com.solegendary.reignofnether.building.Buildings;
import com.solegendary.reignofnether.fogofwar.FogOfWarServerEvents;
import com.solegendary.reignofnether.maps.ArenaOrigins;
import com.solegendary.reignofnether.maps.ArenaSlots;
import com.solegendary.reignofnether.maps.MapLayout;
import com.solegendary.reignofnether.maps.MapPreset;
import com.solegendary.reignofnether.maps.NewMatch;
import com.solegendary.reignofnether.match.MatchPhase;
import com.solegendary.reignofnether.match.MatchSetup;
import com.solegendary.reignofnether.match.MatchState;
import com.solegendary.reignofnether.matchstart.MultiplayerLobby;
import com.solegendary.reignofnether.matchstart.MultiplayerMatch;
import com.solegendary.reignofnether.player.PlayerServerEvents;
import com.solegendary.reignofnether.resources.ResourcesServerEvents;
import com.solegendary.reignofnether.unit.UnitServerEvents;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.phys.AABB;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * The bodies of NewMatchGameTests (the holder keeps only entry points, see there). Written before maps.NewMatch
 * (lovish's rule, Oct 22 19:58): they state what Joisco asked for - after a match, the server can host another on
 * fresh ground without regenerating or reloading the world - and failed on the stub that did nothing.
 * <p>
 * Everything the first test touches is global (the world border, the world spawn, the live match, the fog of war,
 * the unit and building lists), so it puts all of it back in {@code finally} and the batch runs alone.
 */
final class NewMatchCases {
    private NewMatchCases() { }

    /** Above every other test's pad (SandboxSecurityGameTests uses 284, the tallest so far is 301). */
    static final int Y = 310;

    private static int cheb(BlockPos a, BlockPos b) {
        return Math.max(Math.abs(a.getX() - b.getX()), Math.abs(a.getZ() - b.getZ()));
    }

    private static boolean near(double a, double b) {
        return Math.abs(a - b) <= 1.0;
    }

    /**
     * Joisco's ask, end to end: a match is decided, the host asks for a new one, and the next arena is on fresh ground
     * a whole arena away - the world border follows it, the world spawn (where people land) follows it, the lobby's
     * map and start positions are built round it, nothing of the old match is still alive or registered, and the
     * fog of war has forgotten what it saw there. A second new match goes to yet another slot.
     */
    public static void new_match_moves_the_arena_to_fresh_ground(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        ArenaOrigins origins = ArenaOrigins.get(server);
        int[] savedCursor = origins.snapshot();
        WorldBorder border = level.getWorldBorder();
        double bx = border.getCenterX(), bz = border.getCenterZ(), bs = border.getSize();
        BlockPos spawn0 = level.getSharedSpawnPos();
        String a = "gametest_nm_a", b = "gametest_nm_b";
        List<String> problems = new ArrayList<>();
        MultiplayerMatch[] played = { null };
        Runnable undo = () -> {
            border.setCenter(bx, bz);
            border.setSize(bs);
            level.setDefaultSpawnPos(spawn0, 0f);
            origins.restore(savedCursor);
            FogOfWarServerEvents.neutralFogUnits.remove(910_001);
            MatchState.live().reset();
            if (played[0] != null)
                played[0].end();
            MatchLifecycleCases.forget(a, b);
        };
        try {
            origins.restore(null);   // a world that has never moved its arena
            BlockPos old = MatchLifecycleCases.pad(helper, Y);
            border.setCenter(old.getX() + 0.5, old.getZ() + 0.5);
            border.setSize(600);   // a bounded (RTS-optimised) map: the fog of war and the pathing key off this
            MatchLifecycleCases.side(a, 1_200_001);
            MatchLifecycleCases.side(b, 1_200_002);
            ResourcesServerEvents.assignResources(a);

            // the match that was played here: a unit, a building, a neutral the fog saw, and a decided result
            LivingEntity unit = MatchLifecycleCases.soldier(level, a, old.getX() + 0.5, Y + 1, old.getZ() + 0.5);
            BuildingPlacement building = BuildingServerEvents.placeBuilding(Buildings.WIND_GENERATOR_VILLAGERS,
                new BlockPos(old.getX() - 4, Y, old.getZ() - 4), Rotation.NONE, a, new int[0], false, false, true, true);
            if (unit == null || building == null) {
                undo.run();
                helper.fail("test setup: could not make the old match's unit or building");
                return;
            }
            FogOfWarServerEvents.neutralFogUnits.put(910_001, new AABB(old).inflate(3));
            played[0] = new MultiplayerMatch(Set.of(a, b), Set.of()).register();
            if (MatchState.live().seatNow(() -> played[0], MatchState.Origin.LOBBY) == null) {
                undo.run();
                helper.fail("test setup: the live match state was not idle: " + MatchState.live().phase());
                return;
            }
            MatchState.live().onDecided(played[0], Set.of(a));
            if (MatchState.live().phase() != MatchPhase.ENDED) {
                undo.run();
                helper.fail("test setup: the match did not end: " + MatchState.live().phase());
                return;
            }

            // ---- the first new match
            NewMatch.Result r1 = NewMatch.start(server, old, false);
            if (!r1.ok() || r1.origin() == null) {
                undo.run();
                helper.fail("a new match after a decided match was refused or failed: " + r1.message());
                return;
            }
            BlockPos o1 = r1.origin();
            if (r1.slot() != 1)
                problems.add("the first new match is slot " + r1.slot() + ", not 1 (slot 0 is the arena just played)");
            if (cheb(o1, old) < ArenaSlots.PITCH)
                problems.add("the new arena " + o1 + " is only " + cheb(o1, old) + " from the old one (a slot is "
                    + ArenaSlots.PITCH + ")");
            if (!near(border.getCenterX(), o1.getX() + 0.5) || !near(border.getCenterZ(), o1.getZ() + 0.5))
                problems.add("the world border is still centred at " + border.getCenterX() + ", " + border.getCenterZ()
                    + ", not at the new origin " + o1);
            if (border.getSize() != 600)
                problems.add("the world border changed size to " + border.getSize());
            BlockPos spawn = level.getSharedSpawnPos();
            if (!near(spawn.getX(), o1.getX()) || !near(spawn.getZ(), o1.getZ()))
                problems.add("the world spawn (where people land and where the lobby opens) is still " + spawn);
            if (MatchState.live().phase() != MatchPhase.LOBBY)
                problems.add("the match state is " + MatchState.live().phase() + ", not LOBBY, after a new match");
            if (PlayerServerEvents.isRTSPlayer(a) || PlayerServerEvents.isRTSPlayer(b))
                problems.add("a side of the old match is still an RTS player");
            if (UnitServerEvents.getAllUnits().contains(unit))
                problems.add("the old match's unit is still registered");
            if (BuildingServerEvents.getBuildings().contains(building))
                problems.add("the old match's building is still registered");
            if (FogOfWarServerEvents.neutralFogUnits.containsKey(910_001))
                problems.add("the fog of war still holds a neutral from the old arena");

            // the next match is built round the new origin: map, start slots and the random-terrain ring
            BlockPos centre = ArenaOrigins.centreFor(level, old);
            if (!near(centre.getX(), o1.getX()) || !near(centre.getZ(), o1.getZ()))
                problems.add("the next match's centre is " + centre + ", not the new origin " + o1);
            for (MapPreset preset : new MapPreset[] { MapPreset.HIGHLANDS, MapPreset.RANDOM }) {
                MatchSetup setup = MatchSetup.create(level, MatchLifecycleCases.lineup("gametest_nm_h"), preset, true, 1, 1, 1,
                    centre, new Random(5), MatchState.Origin.SKIRMISH);
                if (setup.cx != centre.getX() || setup.cz != centre.getZ())
                    problems.add(preset + ": the setup is centred at " + setup.cx + ", " + setup.cz + ", not " + centre);
                MapLayout layout = setup.layout();
                if (layout.slots.isEmpty())
                    problems.add(preset + ": no start slots");
                for (MapLayout.Slot s : layout.slots) {
                    BlockPos start = new BlockPos(setup.cx + s.dx(), Y, setup.cz + s.dz());
                    if (cheb(start, o1) > layout.radius)
                        problems.add(preset + ": start " + s.index() + " at " + start + " is outside the new arena");
                    if (cheb(start, old) < ArenaSlots.PITCH - ArenaSlots.MAX_HALF_EXTENT)
                        problems.add(preset + ": start " + s.index() + " at " + start + " is still on the old arena's ground");
                }
            }
            // every map the lobby offers must fit its slot, at the most sides a lobby seats
            int[] allSeats = new int[MultiplayerLobby.MAX_SEATS];
            for (int i = 0; i < allSeats.length; i++)
                allSeats[i] = i;
            for (MapPreset preset : MapPreset.values())
                if (preset.premade()) {
                    MapLayout big = MapLayout.build(preset, allSeats);
                    if (big.radius + 8 > ArenaSlots.MAX_HALF_EXTENT)
                        problems.add(preset + " with " + allSeats.length + " sides is " + big.radius
                            + " radius: wider than its slot (" + ArenaSlots.MAX_HALF_EXTENT + ")");
                }

            // ---- the second new match: yet another slot
            NewMatch.Result r2 = NewMatch.start(server, old, false);
            if (!r2.ok() || r2.origin() == null) {
                problems.add("a second new match was refused or failed: " + r2.message());
            } else {
                BlockPos o2 = r2.origin();
                if (r2.slot() != 2)
                    problems.add("the second new match is slot " + r2.slot() + ", not 2");
                if (cheb(o2, o1) < ArenaSlots.PITCH || cheb(o2, old) < ArenaSlots.PITCH)
                    problems.add("the second new arena " + o2 + " touches an earlier one (" + o1 + ", " + old + ")");
                if (!near(border.getCenterX(), o2.getX() + 0.5) || !near(border.getCenterZ(), o2.getZ() + 0.5))
                    problems.add("the world border did not follow the second new match to " + o2);
                BlockPos centre2 = ArenaOrigins.centreFor(level, old);
                if (!near(centre2.getX(), o2.getX()) || !near(centre2.getZ(), o2.getZ()))
                    problems.add("the next match's centre is " + centre2 + " after the second new match, not " + o2);
            }

            // the old arena is taken down over a few ticks, not all in the tick of the request
            helper.runAfterDelay(80, () -> {
                try {
                    if (!unit.isRemoved())
                        problems.add("the old match's unit was still in the world 4 s after the new match");
                } finally {
                    undo.run();
                }
                if (problems.isEmpty())
                    helper.succeed();
                else
                    helper.fail(String.join("; ", problems));
            });
        } catch (RuntimeException e) {
            undo.run();
            throw e;
        }
    }

    /**
     * "so restarts don't reuse ground": the slot cursor goes through real saved-data bytes and comes back handing out
     * the slot after the last one, whatever position the next caller stands at.
     */
    public static void new_match_slots_survive_a_save_and_a_restart(GameTestHelper helper) throws IOException {
        ArenaOrigins before = new ArenaOrigins();
        int[] last = null;
        for (int i = 0; i < 4; i++)
            last = before.cursor().shift(500, -700);
        CompoundTag bytes = SkirmishGameTests.bytesRoundTrip(before.save(new CompoundTag()));
        ArenaOrigins after = ArenaOrigins.load(bytes);
        List<String> problems = new ArrayList<>();
        if (after.cursor().slot() != 4)
            problems.add("the saved cursor came back at slot " + after.cursor().slot() + ", not 4");
        int[] cur = after.cursor().current();
        if (cur == null || last == null || cur[0] != last[0] || cur[1] != last[1])
            problems.add("the arena in use changed through the save");
        int[] next = after.cursor().shift(0, 0);
        int[] expected = ArenaSlots.origin(500, -700, 5);
        if (next[0] != expected[0] || next[1] != expected[1])
            problems.add("after a restart the next arena is " + next[0] + ", " + next[1] + " instead of slot 5's "
                + expected[0] + ", " + expected[1]);
        if (problems.isEmpty())
            helper.succeed();
        else
            helper.fail(String.join("; ", problems));
    }

    /** An op command for hosts until the lobby has a button: /rts-newmatch, permission level 2 like /rts-reset. */
    public static void new_match_command_is_registered_for_ops_only(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        CommandNode<CommandSourceStack> node = server.getCommands().getDispatcher().getRoot().getChild("rts-newmatch");
        if (node == null) {
            helper.fail("/rts-newmatch is not registered");
            return;
        }
        CommandSourceStack source = server.createCommandSourceStack();
        if (node.getRequirement().test(source.withPermission(0)))
            helper.fail("/rts-newmatch is open to players without op");
        else if (!node.getRequirement().test(source.withPermission(2)))
            helper.fail("/rts-newmatch is closed to an op");
        else
            helper.succeed();
    }
}
