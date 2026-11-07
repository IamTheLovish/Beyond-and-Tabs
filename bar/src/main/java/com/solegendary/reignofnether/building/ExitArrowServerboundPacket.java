package com.solegendary.reignofnether.building;

import com.solegendary.reignofnether.api.ReignOfNetherRegistries;
import com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement;
import com.solegendary.reignofnether.building.production.ProductionBuilding;
import com.solegendary.reignofnether.fogofwar.FogOfWarServerEvents;
import com.solegendary.reignofnether.network.PacketAuth;
import com.solegendary.reignofnether.registrars.PacketHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Rotation;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * "Where do my units come out?" for the exit arrow on a remote server (ExitArrowClientEvents): a building type to get
 * the spawn rule of (empty = none) and up to {@value #MAX_ORIGINS} buildings of the sender's own. Everything in it is
 * untrusted: the type must be a registered production building, the origins at most {@value #MAX_ORIGINS}, each one a
 * production building owned by the sender before any spawn-cell work is done, rate-limited by PacketAuth, and a cell is
 * only sent if the sender can see it (fog of war).
 */
public class ExitArrowServerboundPacket {

    public static final int MAX_ORIGINS = 8;

    private final String typeKey;
    private final List<BlockPos> origins;

    /** Client side: ask for {@code type}'s spawn rule (null = none) and the cells of these own buildings. */
    public static void ask(Building type, List<BlockPos> origins) {
        String key = ExitArrow.askKey(type, ReignOfNetherRegistries.BUILDING::getKey);
        if (key == null)
            throw new IllegalArgumentException("ExitArrowServerboundPacket.ask: " + type.name + " is not in the BUILDING registry");
        PacketHandler.INSTANCE.sendToServer(new ExitArrowServerboundPacket(key, new ArrayList<>(origins)));
    }

    public ExitArrowServerboundPacket(String typeKey, List<BlockPos> origins) {
        this.typeKey = typeKey;
        this.origins = origins;
    }

    public ExitArrowServerboundPacket(FriendlyByteBuf buffer) {
        this.typeKey = buffer.readUtf(128);
        int n = buffer.readVarInt();
        if (n > MAX_ORIGINS)
            throw new io.netty.handler.codec.DecoderException("ExitArrowServerboundPacket: " + n + " origins (max " + MAX_ORIGINS + ")");
        this.origins = new ArrayList<>(n);
        for (int i = 0; i < n; i++)
            origins.add(buffer.readBlockPos());
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeUtf(typeKey, 128);
        buffer.writeVarInt(origins.size());
        for (BlockPos p : origins)
            buffer.writeBlockPos(p);
    }

    public String typeKey() { return typeKey; }

    public List<BlockPos> origins() { return origins; }

    /**
     * The answer for {@code sender}: the type's spawn rule if {@link #typeKey} is a production building that spawns
     * units, and for each origin the safe exit cell if it is a production building owned by {@code sender} and
     * {@code visible} accepts the cell. Builds nothing in the world.
     */
    public ExitArrowClientboundPacket reply(ServerLevel level, String sender, Predicate<BlockPos> visible) {
        boolean hasSpec = false, indoors = false;
        net.minecraft.core.Vec3i offset = net.minecraft.core.Vec3i.ZERO;
        ResourceLocation rl = ResourceLocation.tryParse(typeKey);
        if (rl != null && ReignOfNetherRegistries.BUILDING.containsKey(rl)
                && ReignOfNetherRegistries.BUILDING.get(rl) instanceof ProductionBuilding pb
                && pb.createBuildingPlacement(level, BlockPos.ZERO, Rotation.NONE, "") instanceof ProductionPlacement blueprint) {
            ProductionPlacement.ExitSpec spec = blueprint.exitSpec(level);
            if (spec != null) {
                hasSpec = true;
                indoors = spec.indoors();
                offset = spec.offset();
            }
        }
        List<ExitArrowClientboundPacket.Entry> entries = new ArrayList<>();
        for (BlockPos origin : origins.size() > MAX_ORIGINS ? origins.subList(0, MAX_ORIGINS) : origins) {
            BlockPos cell = null;
            if (BuildingUtils.findBuilding(false, origin) instanceof ProductionPlacement pp && sender != null && sender.equals(pp.ownerName)) {
                ProductionPlacement.ExitSpec spec = pp.exitSpec(level);
                cell = spec == null ? null : pp.exitArrowCellFor(level, sender, spec);
                if (cell != null && !visible.test(cell))
                    cell = null;
            }
            entries.add(new ExitArrowClientboundPacket.Entry(origin, cell != null, cell));
        }
        return new ExitArrowClientboundPacket(typeKey, hasSpec, indoors, offset, entries);
    }

    public boolean handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer sp = ctx.get().getSender();
            // 4 a second sustained matches the client's burst limit (ExitArrow.shouldAsk); more is a flood
            if (sp == null || !PacketAuth.allow(sp, "exitarrow", 4, 4))
                return;
            ExitArrowClientboundPacket answer = reply(sp.serverLevel(), sp.getName().getString(),
                cell -> FogOfWarServerEvents.isBlockVisibleFor(sp, cell.getX(), cell.getZ()));
            PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> sp), answer);
        });
        ctx.get().setPacketHandled(true);
        return true;
    }
}
