package com.solegendary.reignofnether.unit.units.shared;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement;
import com.solegendary.reignofnether.building.production.IUnitProductionItem;
import com.solegendary.reignofnether.building.production.ProductionItem;
import com.solegendary.reignofnether.building.production.StartProductionButton;
import com.solegendary.reignofnether.building.production.StopProductionButton;
import com.solegendary.reignofnether.hud.buttons.UnitSpawnButton;
import com.solegendary.reignofnether.keybinds.Keybinding;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.resources.ResourceCosts;
import com.solegendary.reignofnether.unit.interfaces.Unit;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Every faction's T1 siege lobber item (design/siege_t1.md): TideProd's unit buttons from one lang key
 * ({@code entity.reignofnether.<id>_unit} plus three tooltips) and the portrait {@code textures/mobheads/<id>.png}, one
 * shared cost (ResourceCosts.SIEGE_LOBBER). The names are constants because EntityRegistrar's production switch needs
 * them as case labels; the seven instances are in ProductionItems. Placeholder names until Itachi 1's pass.
 */
public class SiegeLobberProd extends ProductionItem implements IUnitProductionItem {

    private final String name;
    private final String langKey;      // entity.reignofnether.<x>_unit
    private final int tooltipLines;
    private final ResourceLocation icon;
    private final ResourceCost unitCost;

    public static final String SUNFIRE_MANGONEL = "Sunfire Mangonel";      // Sunforged, Barracks
    public static final String SKULL_HURLER = "Skull Hurler";              // Gravebound, Graveyard
    public static final String BOULDER_CHUCKER = "Boulder Chucker";        // Horde, Military Portal
    public static final String BURL_SLINGER = "Burl Slinger";              // Verdant Court, Grove
    public static final String POWDER_MONKEY = "Powder Monkey";            // Tidewrought, Slipway
    public static final String THUNDERSTONE_MONK = "Thunderstone Monk";    // Stormcallers, Lotus Academy
    public static final String STEAM_LOBBER = "Steam Lobber";              // Brassworks, Machine Shop

    /** @param id the lobber's id: the entity is {@code <id>_unit}, the portrait {@code <id>.png} */
    public SiegeLobberProd(String name, String id, Supplier<? extends EntityType<? extends Unit>> type) {
        this(name, id + "_unit", id, 3, ResourceCosts.SIEGE_LOBBER, type, true);
    }

    private SiegeLobberProd(String name, String entityId, String mobhead, int tooltipLines, ResourceCost cost,
                            Supplier<? extends EntityType<? extends Unit>> type, boolean spawnIndoors) {
        super(cost);
        this.name = name;
        this.langKey = "entity.reignofnether." + entityId;
        this.tooltipLines = tooltipLines;
        this.icon = ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "textures/mobheads/" + mobhead + ".png");
        this.unitCost = cost;
        this.onComplete = (Level level, ProductionPlacement placement) -> {
            if (!level.isClientSide())
                placement.produceUnit((ServerLevel) level, type.get(), placement.ownerName, spawnIndoors);
        };
    }

    public String getItemName() {
        return name;
    }

    private List<FormattedCharSequence> description() {
        List<FormattedCharSequence> lines = new ArrayList<>();
        for (int i = 1; i <= tooltipLines; i++)
            lines.add(Component.translatable(langKey + ".tooltip" + i).getVisualOrderText());
        return lines;
    }

    public UnitSpawnButton getPlaceButton() {
        List<FormattedCharSequence> lines = new ArrayList<>();
        lines.add(Component.translatable(langKey).withStyle(Style.EMPTY.withBold(true)).getVisualOrderText());
        lines.add(FormattedCharSequence.EMPTY);
        lines.addAll(description());
        return new UnitSpawnButton(name, icon, lines);
    }

    public StartProductionButton getStartButton(ProductionPlacement prodBuilding, Keybinding hotkey) {
        List<FormattedCharSequence> lines = new ArrayList<>();
        lines.add(Component.translatable(langKey).withStyle(Style.EMPTY.withBold(true)).getVisualOrderText());
        lines.add(ResourceCosts.getFormattedCost(unitCost));
        lines.add(ResourceCosts.getFormattedPopAndTime(unitCost));
        lines.add(FormattedCharSequence.forward("", Style.EMPTY));
        lines.addAll(description());
        return new StartProductionButton(name, icon, hotkey,
            () -> false,
            () -> canProduce(prodBuilding),
            lines,
            this);
    }

    public StopProductionButton getCancelButton(ProductionPlacement prodBuilding, boolean first) {
        return new StopProductionButton(name, icon, prodBuilding, this, first);
    }
}
