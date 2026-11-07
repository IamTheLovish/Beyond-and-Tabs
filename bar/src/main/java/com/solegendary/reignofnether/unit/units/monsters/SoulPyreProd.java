package com.solegendary.reignofnether.unit.units.monsters;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement;
import com.solegendary.reignofnether.building.production.ProductionItem;
import com.solegendary.reignofnether.building.production.StopProductionButton;
import com.solegendary.reignofnether.building.production.IUnitProductionItem;
import com.solegendary.reignofnether.hud.buttons.UnitSpawnButton;
import com.solegendary.reignofnether.keybinds.Keybinding;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.resources.ResourceCosts;
import com.solegendary.reignofnether.building.production.StartProductionButton;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.level.Level;

import java.util.List;

/** The Soul Pyre (Gravebound T2 siege, design/defences_tiers.md): trained at the Dungeon. */
public class SoulPyreProd extends ProductionItem implements IUnitProductionItem {

    public final static String itemName = "Soul Pyre";
    public final static ResourceCost cost = ResourceCosts.SOUL_PYRE;

    public SoulPyreProd() {
        super(cost);
        this.onComplete = (Level level, ProductionPlacement placement) -> {
            if (!level.isClientSide()) {
                placement.produceUnit((ServerLevel) level, EntityRegistrar.SOUL_PYRE_UNIT.get(), placement.ownerName, false);
            }
        };
    }

    public String getItemName() {
        return SoulPyreProd.itemName;
    }

    public UnitSpawnButton getPlaceButton() {
        return new UnitSpawnButton(
                itemName,
                ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "textures/mobheads/soul_pyre.png"),
                List.of(
                        Component.translatable("entity.reignofnether.soul_pyre_unit").withStyle(Style.EMPTY.withBold(true)).getVisualOrderText(),
                        FormattedCharSequence.EMPTY,
                        Component.translatable("entity.reignofnether.soul_pyre_unit.tooltip1").getVisualOrderText(),
                        Component.translatable("entity.reignofnether.soul_pyre_unit.tooltip2").getVisualOrderText()
                )
        );
    }

    public StartProductionButton getStartButton(ProductionPlacement prodBuilding, Keybinding hotkey) {
        return new StartProductionButton(
            SoulPyreProd.itemName,
            ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "textures/mobheads/soul_pyre.png"),
            hotkey,
            () -> false,
            () -> true,
            List.of(
                Component.translatable("entity.reignofnether.soul_pyre_unit").withStyle(Style.EMPTY.withBold(true)).getVisualOrderText(),
                ResourceCosts.getFormattedCost(cost),
                ResourceCosts.getFormattedTime(cost),
                FormattedCharSequence.forward("", Style.EMPTY),
                Component.translatable("entity.reignofnether.soul_pyre_unit.tooltip1").getVisualOrderText(),
                Component.translatable("entity.reignofnether.soul_pyre_unit.tooltip2").getVisualOrderText()
            ),
            this
        );
    }

    public StopProductionButton getCancelButton(ProductionPlacement prodBuilding, boolean first) {
        return new StopProductionButton(
            SoulPyreProd.itemName,
            ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "textures/mobheads/soul_pyre.png"),
            prodBuilding,
            this,
            first
        );
    }
}
