package com.solegendary.reignofnether.hero;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement;
import com.solegendary.reignofnether.building.production.HeroProductionItem;
import com.solegendary.reignofnether.building.production.StartProductionButton;
import com.solegendary.reignofnether.keybinds.Keybinding;
import com.solegendary.reignofnether.resources.ResourceCosts;
import com.solegendary.reignofnether.unit.interfaces.HeroUnit;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Trains a faction hero at its faction's T1 army lab (Reign of Nether's {@link HeroProductionItem}: one of each hero
 * per player, the allowedHeroes gamerule, a castle-tier building for a second hero). One class for the seven heroes,
 * parameterised by kit and entity type.
 * <p>
 * Deliberately not an IUnitProductionItem: that interface only feeds the sandbox spawn menu, and the labs' tests count
 * their trainable units through it; a hero is not part of a lab's unit roster (Factions registers the hero type to the
 * faction directly).
 */
public class HeroProd extends HeroProductionItem {

    private static final Map<String, HeroProd> BY_KIT = new HashMap<>();

    public final HeroKit kit;
    private final Supplier<? extends EntityType<? extends HeroUnit>> type;

    public HeroProd(HeroKit kit, Supplier<? extends EntityType<? extends HeroUnit>> type) {
        super(kit.cost, kit.itemName, ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "textures/mobheads/" + kit.id + ".png"));
        this.kit = kit;
        this.type = type;
        this.onComplete = (Level level, ProductionPlacement placement) -> {
            if (!level.isClientSide() && canProduce(placement))
                placement.produceUnit((ServerLevel) level, getHeroEntityType(), placement.ownerName, true);
        };
        BY_KIT.put(kit.id, this);
    }

    public static HeroProd forKit(HeroKit kit) {
        return kit == null ? null : BY_KIT.get(kit.id);
    }

    @Override
    protected EntityType<? extends HeroUnit> getHeroEntityType() {
        return type.get();
    }

    public StartProductionButton getStartButton(ProductionPlacement prodBuilding, Keybinding hotkey) {
        String key = "entity.reignofnether." + kit.entityId();
        List<FormattedCharSequence> tooltips = new ArrayList<>(List.of(
            Component.translatable(key).append(Component.literal(" (")).append(Component.translatable("hud.units.reignofnether.hero"))
                .append(Component.literal(")")).withStyle(Style.EMPTY.withBold(true)).getVisualOrderText(),
            ResourceCosts.getFormattedCost(defaultCost),
            ResourceCosts.getFormattedPopAndTime(defaultCost),
            FormattedCharSequence.forward("", Style.EMPTY),
            Component.translatable(key + ".tooltip1").getVisualOrderText(),
            Component.translatable(key + ".tooltip2").getVisualOrderText()
        ));
        tooltips.addAll(getAdditionalHeroTooltips());
        return super.getStartButton(prodBuilding, hotkey, tooltips);
    }
}
