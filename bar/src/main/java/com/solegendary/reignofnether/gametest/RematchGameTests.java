package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.match.BattlefieldJournal;
import com.solegendary.reignofnether.matchstart.MultiplayerLobby;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Back to lobby (the tester's Oct 26 ask: rematches on one server): the battlefield journal puts back every block a
 * match changed, and the lobby reopens with the last lineup. The journal is checked through a bounded probe
 * (BattlefieldJournal.probe) inside the test's own arena - the test server is shared, so the live journal is never
 * armed or restored here. No lambdas: every body runs in the test's first tick (own batch, MatchLifecycleGameTests'
 * note on holder methods).
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class RematchGameTests {

    static final String ARENA = SkirmishGameTests.ARENA;
    static final String BATCH = "rematch";

    /**
     * A block changed twice is kept with its state from before the first change; a block outside the journal's box
     * is not kept; restore writes every kept block back and stops recording (a later change is not kept).
     */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = BATCH)
    public static void rematch_journal_keeps_the_first_state_and_restore_puts_every_block_back(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos a = helper.absolutePos(new BlockPos(3, 3, 3));
        BlockPos b = helper.absolutePos(new BlockPos(5, 3, 3));
        BlockPos outside = helper.absolutePos(new BlockPos(9, 3, 3));
        level.setBlock(a, Blocks.AIR.defaultBlockState(), 3);
        level.setBlock(b, Blocks.OAK_PLANKS.defaultBlockState(), 3);
        level.setBlock(outside, Blocks.AIR.defaultBlockState(), 3);
        BlockState bBefore = level.getBlockState(b);
        BattlefieldJournal j = BattlefieldJournal.probe(level, BoundingBox.fromCorners(a, b));
        List<String> problems = new ArrayList<>();
        try {
            level.setBlock(a, Blocks.STONE.defaultBlockState(), 3);
            level.setBlock(a, Blocks.DIRT.defaultBlockState(), 3);   // second change: the first state stays kept
            level.setBlock(b, Blocks.AIR.defaultBlockState(), 3);
            level.setBlock(outside, Blocks.GLASS.defaultBlockState(), 3);
            if (j.original(a) != Blocks.AIR.defaultBlockState())
                problems.add("a block changed twice kept " + j.original(a) + ", not its state before the first change");
            if (j.original(b) != bBefore)
                problems.add("a removed block kept " + j.original(b) + ", not " + bBefore);
            if (j.original(outside) != null)
                problems.add("a block outside the journal's box was kept");
            int n = j.restoreInto(level);
            if (n != 2)
                problems.add("restore wrote " + n + " block(s), expected 2");
            if (!level.getBlockState(a).isAir())
                problems.add("restore left " + level.getBlockState(a) + " where there was air");
            if (level.getBlockState(b) != bBefore)
                problems.add("restore left " + level.getBlockState(b) + " where there were planks");
            if (j.kept() != 0)
                problems.add("the journal still keeps " + j.kept() + " block(s) after its restore");
            level.setBlock(a, Blocks.STONE.defaultBlockState(), 3);
            if (j.kept() != 0)
                problems.add("a restored journal kept recording");
        } finally {
            j.close();
            level.setBlock(a, Blocks.AIR.defaultBlockState(), 3);
            level.setBlock(b, Blocks.AIR.defaultBlockState(), 3);
            level.setBlock(outside, Blocks.AIR.defaultBlockState(), 3);
        }
        if (problems.isEmpty())
            helper.succeed();
        else
            helper.fail(String.join("; ", problems));
    }

    /** The journal survives the world's save: every kept position comes back with its state (a restart mid-match). */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = BATCH)
    public static void rematch_journal_survives_a_save_and_load(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos a = helper.absolutePos(new BlockPos(3, 3, 6));
        BlockPos b = helper.absolutePos(new BlockPos(4, 3, 6));
        level.setBlock(a, Blocks.AIR.defaultBlockState(), 3);
        level.setBlock(b, Blocks.OAK_PLANKS.defaultBlockState(), 3);
        BlockState bBefore = level.getBlockState(b);
        BattlefieldJournal j = BattlefieldJournal.probe(level, BoundingBox.fromCorners(a, b));
        List<String> problems = new ArrayList<>();
        try {
            level.setBlock(a, Blocks.COBBLESTONE.defaultBlockState(), 3);
            level.setBlock(b, Blocks.AIR.defaultBlockState(), 3);
            j.close();
            BattlefieldJournal back = BattlefieldJournal.load(j.save(new CompoundTag()));
            if (back.kept() != j.kept())
                problems.add("the loaded journal keeps " + back.kept() + " block(s), the saved one " + j.kept());
            if (back.original(a) != Blocks.AIR.defaultBlockState() || back.original(b) != bBefore)
                problems.add("the loaded journal changed the kept states: " + back.original(a) + " / " + back.original(b));
            back.restoreInto(level);
            if (!level.getBlockState(a).isAir() || level.getBlockState(b) != bBefore)
                problems.add("the loaded journal did not restore the blocks");
        } finally {
            j.close();
            level.setBlock(a, Blocks.AIR.defaultBlockState(), 3);
            level.setBlock(b, Blocks.AIR.defaultBlockState(), 3);
        }
        if (problems.isEmpty())
            helper.succeed();
        else
            helper.fail(String.join("; ", problems));
    }

    /**
     * The rematch lineup: every bot as it was, every human still online with faction, colour, team and role but not
     * ready, a human who left dropped, new seat ids.
     */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = BATCH)
    public static void rematch_lobby_keeps_bots_and_online_humans_with_their_choices(GameTestHelper helper) {
        UUID hereId = UUID.nameUUIDFromBytes("gametest_rm_here".getBytes());
        UUID goneId = UUID.nameUUIDFromBytes("gametest_rm_gone".getBytes());
        MultiplayerLobby.Seat here = new MultiplayerLobby.Seat(1, false);
        here.name = "gametest_rm_here";
        here.uuid = hereId;
        here.faction = 5;
        here.colorMapId = 17;
        here.team = 1;
        here.heroRole = true;
        here.ready = true;
        MultiplayerLobby.Seat gone = new MultiplayerLobby.Seat(2, false);
        gone.name = "gametest_rm_gone";
        gone.uuid = goneId;
        MultiplayerLobby.Seat bot = new MultiplayerLobby.Seat(3, true);
        bot.name = "AI";
        bot.faction = 2;
        bot.difficulty = 2;
        bot.team = 0;
        bot.colorMapId = 9;
        bot.ready = true;
        List<MultiplayerLobby.Seat> kept = MultiplayerLobby.keptSeats(List.of(here, gone, bot), Set.of(hereId), 40);
        List<String> problems = new ArrayList<>();
        if (kept.size() != 2)
            problems.add("expected the online human and the bot, got " + kept.size() + " seat(s)");
        else {
            MultiplayerLobby.Seat h = kept.get(0), k = kept.get(1);
            if (h.bot || !hereId.equals(h.uuid) || h.faction != 5 || h.colorMapId != 17 || h.team != 1 || !h.heroRole)
                problems.add("the human's choices were not kept");
            if (h.ready)
                problems.add("a human comes back ready: they must confirm the rematch");
            if (!k.bot || k.faction != 2 || k.difficulty != 2 || k.team != 0 || k.colorMapId != 9 || !k.ready)
                problems.add("the bot was not kept as it was");
            if (h.id != 40 || k.id != 41)
                problems.add("the kept seats should get new ids from 40: " + h.id + ", " + k.id);
        }
        if (problems.isEmpty())
            helper.succeed();
        else
            helper.fail(String.join("; ", problems));
    }
}
