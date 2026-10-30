package com.solegendary.reignofnether.unit.units.monsters;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement;
import com.solegendary.reignofnether.building.production.ProductionItem;
import com.solegendary.reignofnether.building.production.StartProductionButton;
import com.solegendary.reignofnether.building.production.StopProductionButton;
import com.solegendary.reignofnether.building.production.IUnitProductionItem;
import com.solegendary.reignofnether.hud.buttons.UnitSpawnButton;
import com.solegendary.reignofnether.keybinds.Keybinding;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.resources.ResourceCosts;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.level.Level;

import java.util.List;

/** The Gravebound essence caster, trained at the Graveyard (tier 1: lovish Oct 29; design/essence_casters.md). */
public class TombcallerProd extends ProductionItem implements IUnitProductionItem {

    public final static String itemName = "Tombcaller";
    public final static ResourceCost cost = ResourceCosts.TOMBCALLER;

    public TombcallerProd() {
        super(cost);
        this.onComplete = (Level level, ProductionPlacement placement) -> {
            if (!level.isClientSide())
                placement.produceUnit((ServerLevel) level, EntityRegistrar.TOMBCALLER_UNIT.get(), placement.ownerName, true);
        };
    }

    public String getItemName() {
        return TombcallerProd.itemName;
    }

    public UnitSpawnButton getPlaceButton() {
        return new UnitSpawnButton(
                itemName,
                ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "textures/mobheads/tombcaller.png"),
                List.of(
                        Component.translatable("entity.reignofnether.tombcaller_unit").withStyle(Style.EMPTY.withBold(true)).getVisualOrderText(),
                        FormattedCharSequence.EMPTY,
                        Component.translatable("entity.reignofnether.tombcaller_unit.tooltip1").getVisualOrderText(),
                        Component.translatable("entity.reignofnether.tombcaller_unit.tooltip2").getVisualOrderText(),
                        FormattedCharSequence.EMPTY,
                        Component.translatable("entity.reignofnether.tombcaller_unit.tooltip3").getVisualOrderText()
                )
        );
    }

    public StartProductionButton getStartButton(ProductionPlacement prodBuilding, Keybinding hotkey) {
        return new StartProductionButton(
            TombcallerProd.itemName,
            ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "textures/mobheads/tombcaller.png"),
            hotkey,
            () -> false,
            () -> true,
            List.of(
                Component.translatable("entity.reignofnether.tombcaller_unit").withStyle(Style.EMPTY.withBold(true)).getVisualOrderText(),
                ResourceCosts.getFormattedCost(cost),
                ResourceCosts.getFormattedTime(cost),
                FormattedCharSequence.forward("", Style.EMPTY),
                Component.translatable("entity.reignofnether.tombcaller_unit.tooltip1").getVisualOrderText(),
                Component.translatable("entity.reignofnether.tombcaller_unit.tooltip2").getVisualOrderText(),
                FormattedCharSequence.forward("", Style.EMPTY),
                Component.translatable("entity.reignofnether.tombcaller_unit.tooltip3").getVisualOrderText()
            ),
            this
        );
    }

    public StopProductionButton getCancelButton(ProductionPlacement prodBuilding, boolean first) {
        return new StopProductionButton(
            TombcallerProd.itemName,
            ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "textures/mobheads/tombcaller.png"),
            prodBuilding,
            this,
            first
        );
    }
}
