package com.solegendary.reignofnether.building;

import com.solegendary.reignofnether.api.ReignOfNetherRegistries;
import com.solegendary.reignofnether.fogofwar.FogOfWarServerEvents;
import com.solegendary.reignofnether.registrars.PacketHandler;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Rotation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/**
 * A merged building (ComplexPlacement) to the clients that may see it: the building type, the owner, every member's
 * origin, rotation and lattice cell, the shape, the standing block count, and the animation cues (when it formed,
 * which footprints fell to make it). The client rebuilds the same complex from the members and drops whatever it
 * held at those origins (the lone members, or the complex this one replaces). Sent on formation, on a shrink, at
 * login and on a replacement request; the usual SYNC packet keeps its health current afterwards.
 */
public class MergedBuildingClientboundPacket {

    private final ResourceLocation buildingKey;
    private final String ownerName;
    private final List<BlockPos> origins;
    private final List<Rotation> rotations;
    private final List<MergeShapes.Cell> cells;
    private final MergeShapes.Shape shape;
    private final int blocksPlaced;
    private final int scenarioRoleIndex;
    private final long formedAtTick;
    private final long collapsedAtTick;
    private final List<BlockPos> collapsed;

    /** To every player whose fog shows a corner of the complex (the same rule as the building packets). */
    public static void send(ComplexPlacement c) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null || c == null || c.getShape() == null)
            return;
        ResourceLocation key = ReignOfNetherRegistries.BUILDING.getKey(c.getBuilding());
        if (key == null)
            return;
        List<BlockPos> origins = new ArrayList<>();
        List<Rotation> rotations = new ArrayList<>();
        List<MergeShapes.Cell> cells = new ArrayList<>();
        for (ComplexPlacement.Section s : c.getSections()) {
            origins.add(s.originPos);
            rotations.add(s.rotation == null ? Rotation.NONE : s.rotation);
            cells.add(s.cell);
        }
        MergedBuildingClientboundPacket packet = new MergedBuildingClientboundPacket(key, c.ownerName, origins, rotations,
            cells, c.getShape(), c.getBlocksPlaced(), c.scenarioRoleIndex, c.formedAtTick, c.collapsedAtTick,
            new ArrayList<>(c.recentlyCollapsed));
        for (ServerPlayer sp : server.getPlayerList().getPlayers())
            if (FogOfWarServerEvents.canPlayerSeeBuilding(sp, c))
                PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> sp), packet);
    }

    public MergedBuildingClientboundPacket(ResourceLocation buildingKey, String ownerName, List<BlockPos> origins,
                                           List<Rotation> rotations, List<MergeShapes.Cell> cells, MergeShapes.Shape shape,
                                           int blocksPlaced, int scenarioRoleIndex, long formedAtTick, long collapsedAtTick,
                                           List<BlockPos> collapsed) {
        this.buildingKey = buildingKey;
        this.ownerName = ownerName;
        this.origins = origins;
        this.rotations = rotations;
        this.cells = cells;
        this.shape = shape;
        this.blocksPlaced = blocksPlaced;
        this.scenarioRoleIndex = scenarioRoleIndex;
        this.formedAtTick = formedAtTick;
        this.collapsedAtTick = collapsedAtTick;
        this.collapsed = collapsed;
    }

    public MergedBuildingClientboundPacket(FriendlyByteBuf buffer) {
        this.buildingKey = buffer.readResourceLocation();
        this.ownerName = buffer.readUtf();
        int n = buffer.readVarInt();
        this.origins = new ArrayList<>(n);
        this.rotations = new ArrayList<>(n);
        this.cells = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            origins.add(buffer.readBlockPos());
            rotations.add(buffer.readEnum(Rotation.class));
            int col = buffer.readByte(), row = buffer.readByte();
            cells.add(new MergeShapes.Cell(col, row));
        }
        this.shape = buffer.readEnum(MergeShapes.Shape.class);
        this.blocksPlaced = buffer.readInt();
        this.scenarioRoleIndex = buffer.readInt();
        this.formedAtTick = buffer.readLong();
        this.collapsedAtTick = buffer.readLong();
        int m = buffer.readVarInt();
        this.collapsed = new ArrayList<>(m);
        for (int i = 0; i < m; i++)
            collapsed.add(buffer.readBlockPos());
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeResourceLocation(buildingKey);
        buffer.writeUtf(ownerName == null ? "" : ownerName);
        buffer.writeVarInt(origins.size());
        for (int i = 0; i < origins.size(); i++) {
            buffer.writeBlockPos(origins.get(i));
            buffer.writeEnum(rotations.get(i));
            buffer.writeByte(cells.get(i).col());
            buffer.writeByte(cells.get(i).row());
        }
        buffer.writeEnum(shape);
        buffer.writeInt(blocksPlaced);
        buffer.writeInt(scenarioRoleIndex);
        buffer.writeLong(formedAtTick);
        buffer.writeLong(collapsedAtTick);
        buffer.writeVarInt(collapsed.size());
        for (BlockPos p : collapsed)
            buffer.writeBlockPos(p);
    }

    // the decoded fields, for the round-trip test
    public ResourceLocation buildingKey() { return buildingKey; }
    public String ownerName() { return ownerName; }
    public List<BlockPos> origins() { return origins; }
    public List<Rotation> rotations() { return rotations; }
    public List<MergeShapes.Cell> cells() { return cells; }
    public MergeShapes.Shape shape() { return shape; }
    public int blocksPlaced() { return blocksPlaced; }
    public int scenarioRoleIndex() { return scenarioRoleIndex; }
    public long formedAtTick() { return formedAtTick; }
    public long collapsedAtTick() { return collapsedAtTick; }
    public List<BlockPos> collapsed() { return collapsed; }

    public boolean handle(Supplier<NetworkEvent.Context> ctx) {
        final var success = new AtomicBoolean(false);
        ctx.get().enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                BuildingClientEvents.mergeBuildings(ReignOfNetherRegistries.BUILDING.get(buildingKey), ownerName,
                    origins, rotations, cells, shape, blocksPlaced, scenarioRoleIndex, formedAtTick, collapsedAtTick, collapsed);
                success.set(true);
            });
        });
        ctx.get().setPacketHandled(true);
        return success.get();
    }
}
