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

/** The Oriflamme Elephant (T2 beast, design/big_units.md): trained at its faction's T2 lab. */
public class OriflammeElephantProd extends ProductionItem implements IUnitProductionItem {

    public final static String itemName = "Oriflamme Elephant";
    public final static ResourceCost cost = ResourceCosts.ORIFLAMME_ELEPHANT;

    public OriflammeElephantProd() {
        super(cost);
        this.onComplete = (Level level, ProductionPlacement placement) -> {
            if (!level.isClientSide()) {
                placement.produceUnit((ServerLevel) level, EntityRegistrar.ORIFLAMME_ELEPHANT_UNIT.get(), placement.ownerName, false);
            }
        };
    }

    public String getItemName() {
        return OriflammeElephantProd.itemName;
    }

    public UnitSpawnButton getPlaceButton() {
        return new UnitSpawnButton(
                itemName,
                ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "textures/mobheads/oriflamme_elephant.png"),
                List.of(
                        Component.translatable("entity.reignofnether.oriflamme_elephant_unit").withStyle(Style.EMPTY.withBold(true)).getVisualOrderText(),
                        FormattedCharSequence.EMPTY,
                        Component.translatable("entity.reignofnether.oriflamme_elephant_unit.tooltip1").getVisualOrderText(),
                        Component.translatable("entity.reignofnether.oriflamme_elephant_unit.tooltip2").getVisualOrderText()
                )
        );
    }

    public StartProductionButton getStartButton(ProductionPlacement prodBuilding, Keybinding hotkey) {
        return new StartProductionButton(
            OriflammeElephantProd.itemName,
            ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "textures/mobheads/oriflamme_elephant.png"),
            hotkey,
            () -> false,
            () -> true,
            List.of(
                Component.translatable("entity.reignofnether.oriflamme_elephant_unit").withStyle(Style.EMPTY.withBold(true)).getVisualOrderText(),
                ResourceCosts.getFormattedCost(cost),
                ResourceCosts.getFormattedTime(cost),
                FormattedCharSequence.forward("", Style.EMPTY),
                Component.translatable("entity.reignofnether.oriflamme_elephant_unit.tooltip1").getVisualOrderText(),
                Component.translatable("entity.reignofnether.oriflamme_elephant_unit.tooltip2").getVisualOrderText()
            ),
            this
        );
    }

    public StopProductionButton getCancelButton(ProductionPlacement prodBuilding, boolean first) {
        return new StopProductionButton(
            OriflammeElephantProd.itemName,
            ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "textures/mobheads/oriflamme_elephant.png"),
            prodBuilding,
            this,
            first
        );
    }
}
