package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.fogofwar.FogChunkSnapshot;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * The fog snapshot of a chunk whose block entities vanilla has not created yet.
 * <p>
 * A chunk generated and loaded at ticket level 33 (FULL but not ticking: the level the capture's own load ticket asks
 * for) keeps the block entities its features placed as pending tags (WorldGenRegion writes an {@code id: "DUMMY"} tag
 * for them). Vanilla creates them on the first {@code getBlockEntity} of their position: its background chunk save
 * (ChunkMap.processUnloads -> saveChunkIfNeeded -> ChunkSerializer.write -> getBlockEntityNbtForSaving) or the chunk
 * turning ticking (postProcessGeneration, which runs before vanilla ever sends a chunk to a player). A chunk packet
 * lists only the created ones, so the snapshot's block entities depended on whether the save had reached the chunk before
 * the capture built it: main run 40456368179, the_snapshot_of_unloaded_chunks_loads_them_without_blocking_a_tick,
 * "block entities (file only [], reference only [...])" at y -44..-47, "file != live, reference = live".
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class FogSnapshotBlockEntityGameTests {

    static final String BATCH = "fog_snapshot_pending_be";

    /**
     * A chest of the test's arena is put back into the state a feature leaves a block entity in (the block, and only a
     * pending DUMMY tag for its block entity); the capture then serves that chunk on the spot, in the same tick, so no
     * background save can create the block entity first. The snapshot must list the chest, as vanilla's packet of the
     * same chunk does once the chunk is saved or ticking.
     */
    @GameTest(template = SkirmishGameTests.ARENA, batch = BATCH)
    public static void the_snapshot_lists_block_entities_vanilla_has_not_created_yet(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos at = helper.absolutePos(new BlockPos(5, 2, 5));
        LevelChunk chunk = level.getChunkAt(at);
        ChunkPos cp = chunk.getPos();
        BlockState was = level.getBlockState(at);
        level.setBlock(at, Blocks.CHEST.defaultBlockState(), 2);
        chunk.removeBlockEntity(at);
        CompoundTag dummy = new CompoundTag();
        dummy.putString("id", "DUMMY");
        dummy.putInt("x", at.getX());
        dummy.putInt("y", at.getY());
        dummy.putInt("z", at.getZ());
        chunk.setBlockEntityNbt(dummy);
        List<String> problems = new ArrayList<>();
        try {
            boolean created = chunk.getBlockEntities().containsKey(at);
            boolean known = chunk.getBlockEntitiesPos().contains(at);
            if (created || !known) {
                problems.add("(setup) the chest's block entity is not pending: created " + created + ", known "
                    + known);
            } else {
                FogChunkSnapshot.beginCapture(level, cp.x, cp.x, cp.z, cp.z);
                ClientboundLevelChunkWithLightPacket packet = FogChunkSnapshot.get(cp);
                if (packet == null) {
                    problems.add("(setup) the capture did not serve chunk " + cp + " on the spot");
                } else {
                    List<String> listed = new ArrayList<>();
                    boolean[] chest = { false };
                    packet.getChunkData().getBlockEntitiesTagsConsumer(cp.x, cp.z).accept((p, type, tag) -> {
                        listed.add(p.toShortString() + " " + BlockEntityType.getKey(type));
                        if (p.equals(at) && type == BlockEntityType.CHEST)
                            chest[0] = true;
                    });
                    if (!chest[0])
                        problems.add("the snapshot of " + cp + " does not list the chest at " + at.toShortString()
                            + " whose block entity was still pending (listed: " + listed + ")");
                }
            }
        } finally {
            FogChunkSnapshot.clear();
            FogChunkSnapshot.awaitWrites();
            chunk.getBlockEntity(at);   // created now if the capture did not, so the block's removal takes it along
            level.setBlock(at, was, 2);
        }
        if (problems.isEmpty())
            helper.succeed();
        else
            helper.fail(String.join("; ", problems));
    }
}
