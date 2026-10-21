package com.solegendary.reignofnether.building.production;

import com.solegendary.reignofnether.hud.buttons.Button;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.unit.UnitStats;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import com.solegendary.reignofnether.unit.interfaces.WorkerUnit;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Adds a unit's stats to its factory button's tooltip (#38 item 39, BAR's build menu): the one rule that covers every
 * unit production item, so the ~120 Prod classes keep only their name, cost and lore. Called from
 * {@link ProductionItemList#getButtons}; the numbers come from the unit's registered attributes
 * ({@link UnitStats}), never a copy.
 */
public final class UnitStatTooltip {
    private UnitStatTooltip() {}

    /** The entity type a unit production item trains, or null for research, upgrades and other non-unit items. */
    @Nullable
    public static EntityType<?> unitTypeOf(ProductionItem item) {
        if (!(item instanceof IUnitProductionItem || item instanceof HeroProductionItem
            || item instanceof GraveyardUnitProductionItem))
            return null;
        String name = item.getItemName();
        return name == null ? null : EntityRegistrar.getEntityType(name);
    }

    private static final Map<EntityType<?>, Boolean> WORKERS = new ConcurrentHashMap<>();

    /** Whether the unit type is a worker (WorkerUnit): found once per type from an unspawned prototype. */
    public static boolean isWorker(EntityType<?> type, Level level) {
        return WORKERS.computeIfAbsent(type, t -> {
            Entity proto = t.create(level);
            boolean worker = proto instanceof WorkerUnit;
            if (proto != null)
                proto.discard();
            return worker;
        });
    }

    /** The stats a unit type shows: its attributes, minus the token attack of a worker; null for an unknown type. */
    public static UnitStats statsOf(EntityType<?> type, Level level) {
        UnitStats stats = UnitStats.of(type);
        return stats != null && isWorker(type, level) ? stats.withoutAttack() : stats;
    }

    /** The stat lines this item's button should show (empty for a non-unit item). */
    public static List<Component> statLines(ProductionItem item, Level level) {
        EntityType<?> type = unitTypeOf(item);
        UnitStats stats = type == null ? null : statsOf(type, level);
        return stats == null ? List.of() : stats.lines();
    }

    /**
     * Returns {@code lines} with the stats inserted after the cost block (the first blank line the Prod classes all
     * leave before the lore), or appended when there is none.
     */
    public static List<FormattedCharSequence> withStats(List<FormattedCharSequence> lines, List<Component> stats) {
        if (stats.isEmpty())
            return lines;
        int at = lines.size();
        for (int i = 0; i < lines.size(); i++) {
            if (plain(lines.get(i)).isEmpty()) {
                at = i;
                break;
            }
        }
        List<FormattedCharSequence> out = new ArrayList<>(lines.subList(0, at));
        for (Component c : stats)
            out.add(c.getVisualOrderText());
        if (at < lines.size())
            out.addAll(lines.subList(at, lines.size()));
        return out;
    }

    private static String plain(FormattedCharSequence seq) {
        StringBuilder sb = new StringBuilder();
        seq.accept((i, style, cp) -> {
            sb.appendCodePoint(cp);
            return true;
        });
        return sb.toString().trim();
    }

    /** Adds the stats to {@code button}'s tooltip when {@code item} trains a unit. */
    public static void apply(Button button, ProductionItem item, Level level) {
        if (button.tooltipLines != null)
            button.tooltipLines = withStats(button.tooltipLines, statLines(item, level));
    }
}
