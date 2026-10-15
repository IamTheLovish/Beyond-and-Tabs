package com.solegendary.reignofnether.building;

import com.solegendary.reignofnether.api.ReignOfNetherRegistries;
import com.solegendary.reignofnether.building.buildings.placements.CustomBuildingPlacement;
import com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement;
import com.solegendary.reignofnether.building.production.ActiveProduction;
import com.solegendary.reignofnether.building.production.ProductionItem;
import com.solegendary.reignofnether.fogofwar.FogOfWarServerEvents;
import com.solegendary.reignofnether.registrars.PacketHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import static com.solegendary.reignofnether.building.BuildingUtils.findBuilding;

public class BuildingProductionClientboundPacket {
    public static final ResourceLocation EMPTY = ResourceLocation.fromNamespaceAndPath("", "");

    // pos is used to identify the building object serverside
    public BuildingAction action;
    public BlockPos buildingPos;
    public String itemName;
    public float ticksLeft;

    // send only to players whose fog reveals at least one corner of this building
    private static void sendFiltered(String ownerName, BlockPos buildingPos, BuildingProductionClientboundPacket packet) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        BuildingPlacement b = findBuilding(false, buildingPos);
        for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
            if (ownerName.equals(sp.getName().getString()) ||
                (b != null && FogOfWarServerEvents.canPlayerSeeBuilding(sp, b))) {
                PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> sp), packet);
            }
        }
    }

    public static void startProduction(String ownerName, BlockPos buildingPos, String itemName) {
        sendFiltered(ownerName, buildingPos,
                new BuildingProductionClientboundPacket(BuildingAction.START_PRODUCTION,
                        itemName,
                        buildingPos
                )
        );
    }

    public static void startProduction(String ownerName, BlockPos buildingPos, ProductionItem item, float ticksLeft) {
        sendFiltered(ownerName, buildingPos,
                new BuildingProductionClientboundPacket(BuildingAction.START_PRODUCTION,
                        wireName(item),
                        buildingPos,
                        ticksLeft
                )
        );
    }

    /** One queued item, to one player only: the answer to that player's REQUEST_PRODUCTION_SYNC. */
    public static void syncProductionTo(ServerPlayer player, BlockPos buildingPos, ProductionItem item, float ticksLeft) {
        PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player),
                new BuildingProductionClientboundPacket(BuildingAction.START_PRODUCTION, wireName(item), buildingPos,
                        ticksLeft));
    }

    /** Server-started production (the repeat queue, the Steward): the client resolves the item by this name. */
    public static void startProduction(String ownerName, BlockPos buildingPos, ProductionItem item) {
        startProduction(ownerName, buildingPos, item, -1);
    }

    /**
     * The name the client's handler resolves an item by: its registry key, or its display name for a custom building's
     * unregistered item ({@code CustomBuildingPlacement.getProductionItem}). The repeat queue used to send the display
     * name for every item, which {@code ResourceLocation.tryParse} rejects ("Villager" is not a valid path), so the
     * client never saw the re-queued unit until it reselected the building.
     */
    public static String wireName(ProductionItem item) {
        ResourceLocation key = ReignOfNetherRegistries.PRODUCTION_ITEM.getKey(item);
        return key != null ? key.toString() : item.getItemName();
    }

    public static void cancelProduction(String ownerName, BlockPos buildingPos, String itemName, boolean frontItem) {
        sendFiltered(ownerName, buildingPos,
                new BuildingProductionClientboundPacket(frontItem
                        ? BuildingAction.CANCEL_PRODUCTION
                        : BuildingAction.CANCEL_BACK_PRODUCTION,
                        itemName,
                        buildingPos
                )
        );
    }

    public static void clearQueue(String ownerName, BlockPos buildingPos) {
        sendFiltered(ownerName, buildingPos,
                new BuildingProductionClientboundPacket(BuildingAction.CLEAR_PRODUCTION, "", buildingPos)
        );
    }

    public static void toggleRepeat(String ownerName, BlockPos buildingPos, boolean on) {
        sendFiltered(ownerName, buildingPos,
                new BuildingProductionClientboundPacket(BuildingAction.TOGGLE_REPEAT, on ? "on" : "off", buildingPos)
        );
    }

    public static void toggleSteward(String ownerName, BlockPos buildingPos, boolean paused) {
        sendFiltered(ownerName, buildingPos,
                new BuildingProductionClientboundPacket(BuildingAction.TOGGLE_STEWARD, paused ? "paused" : "on", buildingPos)
        );
    }

    public static void completeProduction(String ownerName, BlockPos buildingPos, String itemName) {
        sendFiltered(ownerName, buildingPos,
                new BuildingProductionClientboundPacket(BuildingAction.COMPLETE_PRODUCTION, itemName, buildingPos)
        );
    }

    public BuildingProductionClientboundPacket(
            BuildingAction action,
            String itemName,
            BlockPos buildingPos
    ) {
        this.action = action;
        this.itemName = itemName;
        this.buildingPos = buildingPos;
        this.ticksLeft = -1;
    }

    public BuildingProductionClientboundPacket(
            BuildingAction action,
            String itemName,
            BlockPos buildingPos,
            float ticksLeft
    ) {
        this.action = action;
        this.itemName = itemName;
        this.buildingPos = buildingPos;
        this.ticksLeft = ticksLeft;
    }

    public BuildingProductionClientboundPacket(FriendlyByteBuf buffer) {
        this.action = buffer.readEnum(BuildingAction.class);
        this.itemName = buffer.readUtf();
        this.buildingPos = buffer.readBlockPos();
        this.ticksLeft = buffer.readFloat();
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeEnum(this.action);
        buffer.writeUtf(this.itemName);
        buffer.writeBlockPos(this.buildingPos);
        buffer.writeFloat(this.ticksLeft);
    }

    // client-side packet-consuming functions
    public boolean handle(Supplier<NetworkEvent.Context> ctx) {
        final var success = new AtomicBoolean(false);

        ctx.get().enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                BuildingPlacement building = findBuilding(true, this.buildingPos);
                if (building == null) {
                    return;
                }

                ProductionItem productionItem;
                if (building instanceof CustomBuildingPlacement cbp) {
                    productionItem = cbp.getProductionItem(this.itemName);
                } else {
                    productionItem = ReignOfNetherRegistries.PRODUCTION_ITEM.get(ResourceLocation.tryParse(this.itemName));
                }

                switch (action) {
                    case START_PRODUCTION -> {
                        ((ProductionPlacement) building).startProductionItem(
                                productionItem,
                                ticksLeft
                        );
                    }
                    case CANCEL_PRODUCTION -> {
                        ((ProductionPlacement) building).cancelProductionItem(
                                productionItem,
                                true
                        );
                    }
                    case CANCEL_BACK_PRODUCTION -> {
                        ((ProductionPlacement) building).cancelProductionItem(
                                productionItem,
                                false
                        );
                    }
                    case TOGGLE_REPEAT -> {
                        if (building instanceof ProductionPlacement pBuilding)
                            pBuilding.repeatQueue = "on".equals(this.itemName);
                    }
                    case TOGGLE_STEWARD -> {
                        if (building instanceof ProductionPlacement pBuilding)
                            pBuilding.stewardPaused = "paused".equals(this.itemName);
                    }
                    case CLEAR_PRODUCTION -> {
                        if (building instanceof ProductionPlacement pBuilding) {
                            pBuilding.productionQueue.clear();
                        }
                    }
                    case COMPLETE_PRODUCTION -> {
                        if (building instanceof ProductionPlacement pBuilding) {
                            if (!pBuilding.productionQueue.isEmpty()) {
                                for (ActiveProduction pItem : pBuilding.productionQueue) {
                                    if (pItem.item.getItemName().equals(this.itemName) && !pItem.completed) {
                                        pItem.complete(pBuilding);
                                        break;
                                    }
                                }
                                pBuilding.productionQueue.removeIf(pItem -> pItem.completed);
                            }
                        }
                    }
                    default -> { }
                }
                success.set(true);
            });
        });
        ctx.get().setPacketHandled(true);
        return success.get();
    }
}