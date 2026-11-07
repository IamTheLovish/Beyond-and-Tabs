package com.solegendary.reignofnether.unit.units.villagers;

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

/** The Sunlance Trebuchet (Sunforged T2 siege, design/defences_tiers.md): trained at the Arcane Tower. */
public class SunlanceTrebuchetProd extends ProductionItem implements IUnitProductionItem {

    public final static String itemName = "Sunlance Trebuchet";
    public final static ResourceCost cost = ResourceCosts.SUNLANCE_TREBUCHET;

    public SunlanceTrebuchetProd() {
        super(cost);
        this.onComplete = (Level level, ProductionPlacement placement) -> {
            if (!level.isClientSide()) {
                placement.produceUnit((ServerLevel) level, EntityRegistrar.SUNLANCE_TREBUCHET_UNIT.get(), placement.ownerName, false);
            }
        };
    }

    public String getItemName() {
        return SunlanceTrebuchetProd.itemName;
    }

    public UnitSpawnButton getPlaceButton() {
        return new UnitSpawnButton(
                itemName,
                ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "textures/mobheads/sunlance_trebuchet.png"),
                List.of(
                        Component.translatable("entity.reignofnether.sunlance_trebuchet_unit").withStyle(Style.EMPTY.withBold(true)).getVisualOrderText(),
                        FormattedCharSequence.EMPTY,
                        Component.translatable("entity.reignofnether.sunlance_trebuchet_unit.tooltip1").getVisualOrderText(),
                        Component.translatable("entity.reignofnether.sunlance_trebuchet_unit.tooltip2").getVisualOrderText()
                )
        );
    }

    public StartProductionButton getStartButton(ProductionPlacement prodBuilding, Keybinding hotkey) {
        return new StartProductionButton(
            SunlanceTrebuchetProd.itemName,
            ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "textures/mobheads/sunlance_trebuchet.png"),
            hotkey,
            () -> false,
            () -> true,
            List.of(
                Component.translatable("entity.reignofnether.sunlance_trebuchet_unit").withStyle(Style.EMPTY.withBold(true)).getVisualOrderText(),
                ResourceCosts.getFormattedCost(cost),
                ResourceCosts.getFormattedTime(cost),
                FormattedCharSequence.forward("", Style.EMPTY),
                Component.translatable("entity.reignofnether.sunlance_trebuchet_unit.tooltip1").getVisualOrderText(),
                Component.translatable("entity.reignofnether.sunlance_trebuchet_unit.tooltip2").getVisualOrderText()
            ),
            this
        );
    }

    public StopProductionButton getCancelButton(ProductionPlacement prodBuilding, boolean first) {
        return new StopProductionButton(
            SunlanceTrebuchetProd.itemName,
            ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "textures/mobheads/sunlance_trebuchet.png"),
            prodBuilding,
            this,
            first
        );
    }
}
