package com.solegendary.reignofnether.building;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.network.PacketAuth;
import com.solegendary.reignofnether.api.ReignOfNetherRegistries;
import com.solegendary.reignofnether.building.buildings.placements.CustomBuildingPlacement;
import com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement;
import com.solegendary.reignofnether.building.production.ActiveProduction;
import com.solegendary.reignofnether.building.production.ProductionItem;
import com.solegendary.reignofnether.hud.HudClientEvents;
import com.solegendary.reignofnether.registrars.PacketHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import javax.annotation.Nullable;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import static com.solegendary.reignofnether.building.BuildingUtils.findBuilding;

public class BuildingProductionServerboundPacket {
    public String itemName; // resource location string of the ProductionItem
    public BlockPos buildingPos; // used to identify the relevant production building
    public BuildingAction action;

    public static void toggleRepeat(net.minecraft.core.BlockPos buildingPos) {
        // several factories of this type selected: Repeat goes to all of them, set to the opposite of the one shown
        // (BuildingBatchServerboundPacket), so a mixed selection ends up all on or all off
        List<ProductionPlacement> group = BuildingClientEvents.getCommandGroup();
        if (group.size() > 1 && HudClientEvents.hudSelectedPlacement instanceof ProductionPlacement shown
                && shown.originPos.equals(buildingPos)) {
            BuildingBatchServerboundPacket.send(BuildingBatchServerboundPacket.Action.SET_REPEAT, "",
                shown.repeatQueue ? 0 : 1, positions(group));
            return;
        }
        PacketHandler.INSTANCE.sendToServer(new BuildingProductionServerboundPacket(BuildingAction.TOGGLE_REPEAT, "", buildingPos));
    }

    public static void toggleSteward(net.minecraft.core.BlockPos buildingPos) {
        PacketHandler.INSTANCE.sendToServer(new BuildingProductionServerboundPacket(BuildingAction.TOGGLE_STEWARD, "", buildingPos));
    }

    private static List<BlockPos> positions(List<ProductionPlacement> group) {
        List<BlockPos> out = new java.util.ArrayList<>(group.size());
        for (ProductionPlacement pp : group)
            out.add(pp.originPos);
        return out;
    }

    /**
     * Whether {@code pp}'s own copy of the button for {@code item} is shown and enabled (an extractor already at
     * Tier 2 hides its refit button). A building with no matching button is left to the server to decide.
     */
    private static boolean buttonAvailable(ProductionPlacement pp, ProductionItem item) {
        if (pp.productionButtons == null)
            return true;
        String name = item.getItemName();
        for (com.solegendary.reignofnether.hud.buttons.Button b : pp.productionButtons)
            if (name != null && name.equals(b.name))
                return !b.isHidden.get() && b.isEnabled.get();
        return true;
    }

    public static void startProduction(ProductionItem item) {
        // BAR multi-building command card: with several of the shown building selected (double-click), one click
        // goes to the whole group in one packet - units round-robin across the factories, per-building refits on
        // every building that can take one. Player-wide research (DISALLOW) still goes to a single building.
        List<ProductionPlacement> group = BuildingClientEvents.getCommandGroup();
        if (group.size() > 1 && item.dupeRule != com.solegendary.reignofnether.building.production.ProdDupeRule.DISALLOW) {
            String name = group.get(0) instanceof CustomBuildingPlacement ? item.getItemName()
                : ReignOfNetherRegistries.PRODUCTION_ITEM.getKey(item) == null ? null
                : ReignOfNetherRegistries.PRODUCTION_ITEM.getKey(item).toString();
            if (name != null) {
                if (item.dupeRule == com.solegendary.reignofnether.building.production.ProdDupeRule.ALLOW) {
                    BuildingBatchServerboundPacket.send(BuildingBatchServerboundPacket.Action.QUEUE, name, 0, positions(group));
                } else {
                    List<ProductionPlacement> able = new java.util.ArrayList<>();
                    for (ProductionPlacement pp : group)
                        if (buttonAvailable(pp, item))
                            able.add(pp);
                    BuildingBatchServerboundPacket.send(BuildingBatchServerboundPacket.Action.APPLY, name, 0, positions(able));
                }
                return;
            }
        }
        BuildingClientEvents.switchHudToIdlestBuilding();
        if (HudClientEvents.hudSelectedPlacement instanceof ProductionPlacement pp) {
            String prodItemName;
            if (pp instanceof CustomBuildingPlacement) {
                prodItemName = item.getItemName();
            } else {
                prodItemName = ReignOfNetherRegistries.PRODUCTION_ITEM.getKey(item).toString();
            }
            if (prodItemName != null) {
                PacketHandler.INSTANCE.sendToServer(new BuildingProductionServerboundPacket(
                        BuildingAction.START_PRODUCTION,
                        prodItemName,
                        pp.originPos));
            }
        }
    }

    public static void cancelProduction(BlockPos buildingPos, ProductionItem item, boolean frontItem) {
        String prodItemName;
        if (HudClientEvents.hudSelectedPlacement instanceof ProductionPlacement pp &&
                pp instanceof CustomBuildingPlacement) {
            prodItemName = item.getItemName();
        } else {
            prodItemName = ReignOfNetherRegistries.PRODUCTION_ITEM.getKey(item).toString();
        }
        if (prodItemName != null) {
            PacketHandler.INSTANCE.sendToServer(new BuildingProductionServerboundPacket(
                    frontItem ? BuildingAction.CANCEL_PRODUCTION : BuildingAction.CANCEL_BACK_PRODUCTION,
                    prodItemName, buildingPos));
        }
    }

    public static void requestSync(BlockPos buildingPos) {
        PacketHandler.INSTANCE.sendToServer(new BuildingProductionServerboundPacket(
                BuildingAction.REQUEST_PRODUCTION_SYNC, "", buildingPos));
    }

    public BuildingProductionServerboundPacket(BuildingAction action, String itemName, BlockPos buildingPos) {
        this.action = action;
        this.itemName = itemName;
        this.buildingPos = buildingPos;
    }

    public BuildingProductionServerboundPacket(FriendlyByteBuf buffer) {
        this.action = buffer.readEnum(BuildingAction.class);
        this.itemName = buffer.readUtf();
        this.buildingPos = buffer.readBlockPos();
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeEnum(this.action);
        buffer.writeUtf(this.itemName);
        buffer.writeBlockPos(this.buildingPos);
    }

    // server-side packet-consuming functions
    public boolean handle(Supplier<NetworkEvent.Context> ctx) {
        final var success = new AtomicBoolean(false);
        ctx.get().enqueueWork(() -> {
            BuildingPlacement building = findBuilding(false, this.buildingPos);
            if (building == null)
                return;

            ServerPlayer player = ctx.get().getSender();
            if (player == null) {
                ReignOfNether.LOGGER.warn("Sender for unit action packet was null");
                success.set(false);
                return;
            }
            // the owner, an ally with control or a sandbox player - not "anyone while somebody is in sandbox", and
            // the ally test now names the building's owner (it asked about "" and so never matched)
            else if (!PacketAuth.mayActFor(player, building.ownerName)
                    || !PacketAuth.allow(player, "production", 200, 400)) {

                ReignOfNether.LOGGER.warn("BuildingProductionServerboundPacket: Tried to process packet from " + player.getName() + " for " + building.ownerName);
                success.set(false);
                return;
            }
            if (building instanceof ProductionPlacement pBuilding) {
                if (this.action == BuildingAction.TOGGLE_REPEAT) {
                    pBuilding.setRepeatQueue(!pBuilding.repeatQueue);
                    BuildingProductionClientboundPacket.toggleRepeat(pBuilding.ownerName, buildingPos, pBuilding.repeatQueue);
                } else if (this.action == BuildingAction.TOGGLE_STEWARD) {
                    pBuilding.setStewardPaused(!pBuilding.stewardPaused);
                    BuildingProductionClientboundPacket.toggleSteward(pBuilding.ownerName, buildingPos, pBuilding.stewardPaused);
                } else if (this.action == BuildingAction.REQUEST_PRODUCTION_SYNC) {
                    for (ActiveProduction activeProd : pBuilding.productionQueue) {
                        BuildingProductionClientboundPacket.startProduction(
                                pBuilding.ownerName,
                                buildingPos,
                                activeProd.item,
                                activeProd.ticksLeft
                        );
                    }
                } else {
                    ProductionItem productionItem;
                    if (pBuilding instanceof CustomBuildingPlacement cbp) {
                        productionItem = cbp.getProductionItem(this.itemName);
                    } else {
                        productionItem = ReignOfNetherRegistries.PRODUCTION_ITEM.get(ResourceLocation.tryParse(this.itemName));
                    }
                    if (productionItem != null) {
                        switch (this.action) {
                            case START_PRODUCTION -> {
                                boolean prodSuccess = pBuilding.startProductionItem(productionItem);
                                if (prodSuccess)
                                    BuildingProductionClientboundPacket.startProduction(pBuilding.ownerName, buildingPos, this.itemName);
                            }
                            case CANCEL_PRODUCTION -> {
                                boolean cancelSuccess = pBuilding.cancelProductionItem(productionItem, true);
                                if (cancelSuccess || pBuilding.productionQueue.isEmpty())
                                    BuildingProductionClientboundPacket.cancelProduction(pBuilding.ownerName, buildingPos, this.itemName, true);
                            }
                            case CANCEL_BACK_PRODUCTION -> {
                                boolean cancelSuccess = pBuilding.cancelProductionItem(productionItem, false);
                                if (cancelSuccess || pBuilding.productionQueue.isEmpty())
                                    BuildingProductionClientboundPacket.cancelProduction(pBuilding.ownerName, buildingPos, this.itemName, false);
                            }
                            default -> { }
                        }
                    }
                }
            }
            ReignOfNether.LOGGER.info("[Building] {} performed {} (itemName: {}, pos: {})", player.getName(), this.action, this.itemName, this.buildingPos);
            success.set(true);
        });
        ctx.get().setPacketHandled(true);
        return success.get();
    }
}