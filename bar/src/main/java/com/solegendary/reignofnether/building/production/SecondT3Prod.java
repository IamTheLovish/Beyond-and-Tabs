package com.solegendary.reignofnether.building.production;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement;
import com.solegendary.reignofnether.hud.buttons.UnitSpawnButton;
import com.solegendary.reignofnether.keybinds.Keybinding;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.resources.ResourceCosts;
import com.solegendary.reignofnether.unit.T3Cap;
import com.solegendary.reignofnether.unit.T3ServerEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;
import java.util.function.Supplier;

/**
 * The second Tier 3 of a faction (second-t3-design.md, slice T3b-1), on slot 5 (T) of the faction's T3 lab.
 * <p>
 * <b>Frame only.</b> The real second unit does not exist yet (slices T3b-2..8), so this item is a PLACEHOLDER: it
 * trains the faction's existing T3 body under its own id and name ("&lt;faction&gt; T3b (placeholder)"). The body is
 * tagged {@link T3Cap#TAG_SECOND} on spawn, which is the only thing that tells a "T3b" apart from the first T3 for the
 * alive cap ({@link T3Cap}: one alive of EACH type per player, queued ones counted). When a real unit lands, swap the
 * entity supplier here; the slot, cap, cost and announcement stay.
 * <p>
 * Gate (Tier 3 research) = the first T3 item's own gate, asked through it, so the two cannot drift. Cost = its own
 * config entry ({@code ResourceCosts.*_T3B}, same default as the first T3).
 */
public class SecondT3Prod extends ProductionItem implements IUnitProductionItem {

    private final String name;
    private final ProductionItem first;
    private final ResourceCost unitCost;
    private final ResourceLocation icon;
    /** Lang key of the real unit's name (tooltips are {@code .tooltip1}/{@code .tooltip2}), null while this is a placeholder. */
    @Nullable private final String langKey;

    public SecondT3Prod(String name, String mobhead, ResourceCost cost, ProductionItem first,
                        Supplier<? extends EntityType<? extends Unit>> type) {
        this(name, mobhead, cost, first, type, null);
    }

    /**
     * A REAL second T3 (slice T3b-2..8): its own entity type, so no {@link T3Cap#TAG_SECOND} tag (the cap counts by type)
     * and no announcement here (the unit announces itself under its own name, as every T3 body does).
     */
    public SecondT3Prod(String name, String mobhead, ResourceCost cost, ProductionItem first,
                        Supplier<? extends EntityType<? extends Unit>> type, @Nullable String langKey) {
        super(cost);
        this.langKey = langKey;
        this.name = name;
        this.first = first;
        this.unitCost = cost;
        this.icon = ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "textures/mobheads/" + mobhead + ".png");
        this.onComplete = (Level level, ProductionPlacement placement) -> {
            if (level.isClientSide())
                return;
            Entity e = placement.produceUnit((ServerLevel) level, type.get(), placement.ownerName, false);
            if (e == null || langKey != null)
                return;   // a real unit: counted by its own type, announces itself
            e.getPersistentData().putBoolean(T3Cap.TAG_SECOND, true);
            // the body would announce itself under the FIRST T3's name (wrong unit), so announce under this item's
            // name and mark the body as announced (every T3 body uses this key)
            e.getPersistentData().putBoolean("bt_t3_announced", true);
            T3ServerEvents.announceFielded(placement.ownerName, Component.literal(name));
        };
    }

    public String getItemName() {
        return name;
    }

    /** The first T3 item this one pairs with (its Tier 3 gate, its entity type, its alive cap). */
    public ProductionItem first() {
        return first;
    }

    @Nullable
    @Override
    public String getProduceErrorMsg(ProductionPlacement pp) {
        return first.getProduceErrorMsg(pp);
    }

    private List<FormattedCharSequence> description() {
        if (langKey != null)
            return List.of(
                Component.translatable(langKey).withStyle(Style.EMPTY.withBold(true)).getVisualOrderText(),
                FormattedCharSequence.EMPTY,
                Component.translatable(langKey + ".tooltip1").getVisualOrderText(),
                Component.translatable(langKey + ".tooltip2").getVisualOrderText());
        return List.of(
            Component.literal(name).withStyle(Style.EMPTY.withBold(true)).getVisualOrderText(),
            FormattedCharSequence.EMPTY,
            Component.translatable("entity.reignofnether.second_t3_placeholder").getVisualOrderText());
    }

    public UnitSpawnButton getPlaceButton() {
        return new UnitSpawnButton(name, icon, description());
    }

    public StartProductionButton getStartButton(ProductionPlacement prodBuilding, Keybinding hotkey) {
        return new StartProductionButton(
            name, icon, hotkey,
            () -> false,
            () -> canProduce(prodBuilding),
            langKey != null
                ? List.of(
                    Component.translatable(langKey).withStyle(Style.EMPTY.withBold(true)).getVisualOrderText(),
                    ResourceCosts.getFormattedCost(unitCost),
                    ResourceCosts.getFormattedPopAndTime(unitCost),
                    FormattedCharSequence.EMPTY,
                    Component.translatable(langKey + ".tooltip1").getVisualOrderText(),
                    Component.translatable(langKey + ".tooltip2").getVisualOrderText())
                : List.of(
                    Component.literal(name).withStyle(Style.EMPTY.withBold(true)).getVisualOrderText(),
                    ResourceCosts.getFormattedCost(unitCost),
                    ResourceCosts.getFormattedPopAndTime(unitCost),
                    FormattedCharSequence.EMPTY,
                    Component.translatable("entity.reignofnether.second_t3_placeholder").getVisualOrderText()),
            this);
    }

    public StopProductionButton getCancelButton(ProductionPlacement prodBuilding, boolean first) {
        return new StopProductionButton(name, icon, prodBuilding, this, first);
    }
}
