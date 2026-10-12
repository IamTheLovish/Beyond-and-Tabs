package com.solegendary.reignofnether.hero;

import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.unit.interfaces.HeroUnit;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;

import javax.annotation.Nullable;

/** The faction heroes' entity types by kit, and by production item name (EntityRegistrar.getEntityType). */
public final class HeroTypes {
    private HeroTypes() { }

    @Nullable
    public static EntityType<? extends Mob> typeOf(HeroKit kit) {
        if (kit == null)
            return null;
        return switch (kit.id) {
            case "dawn_paladin" -> EntityRegistrar.DAWN_PALADIN_UNIT.get();
            case "gravecaller" -> EntityRegistrar.GRAVECALLER_UNIT.get();
            case "horde_warchief" -> EntityRegistrar.HORDE_WARCHIEF_UNIT.get();
            case "thornheart_druid" -> EntityRegistrar.THORNHEART_DRUID_UNIT.get();
            case "corsair_captain" -> EntityRegistrar.CORSAIR_CAPTAIN_UNIT.get();
            case "storm_witch" -> EntityRegistrar.STORM_WITCH_UNIT.get();
            case "arch_engineer" -> EntityRegistrar.ARCH_ENGINEER_UNIT.get();
            default -> null;
        };
    }

    @SuppressWarnings("unchecked")
    @Nullable
    public static EntityType<? extends HeroUnit> heroTypeOf(HeroKit kit) {
        return (EntityType<? extends HeroUnit>) (EntityType<?>) typeOf(kit);
    }

    @Nullable
    public static EntityType<? extends Mob> byItemName(String itemName) {
        if (itemName == null)
            return null;
        for (HeroKit kit : HeroKits.ALL)
            if (kit.itemName.equals(itemName))
                return typeOf(kit);
        return null;
    }
}
