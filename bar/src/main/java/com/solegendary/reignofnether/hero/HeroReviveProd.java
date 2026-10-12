package com.solegendary.reignofnether.hero;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.building.production.ReviveHeroProductionItem;
import com.solegendary.reignofnether.unit.interfaces.HeroUnit;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Revives a fallen faction hero at its level, ranks and items (Reign of Nether's {@link ReviveHeroProductionItem}: the
 * cost scales with the level, HeroUnit.getReviveCost). Shown on the same lab slot as the hero itself, only while it is
 * dead.
 */
public class HeroReviveProd extends ReviveHeroProductionItem {

    private static final Map<String, HeroReviveProd> BY_KIT = new HashMap<>();

    public final HeroKit kit;
    private final Supplier<? extends EntityType<? extends HeroUnit>> type;

    public HeroReviveProd(HeroKit kit, Supplier<? extends EntityType<? extends HeroUnit>> type) {
        super("Revive " + kit.itemName,
            ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "textures/mobheads/" + kit.id + ".png"),
            "entity.reignofnether." + kit.entityId() + ".revive");
        this.kit = kit;
        this.type = type;
        BY_KIT.put(kit.id, this);
    }

    public static HeroReviveProd forKit(HeroKit kit) {
        return kit == null ? null : BY_KIT.get(kit.id);
    }

    @Override
    protected EntityType<? extends HeroUnit> getHeroEntityType() {
        return type.get();
    }
}
