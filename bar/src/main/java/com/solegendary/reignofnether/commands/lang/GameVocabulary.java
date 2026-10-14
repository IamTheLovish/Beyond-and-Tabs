package com.solegendary.reignofnether.commands.lang;

import com.solegendary.reignofnether.ability.Ability;
import com.solegendary.reignofnether.api.ReignOfNetherRegistries;
import com.solegendary.reignofnether.building.Building;
import com.solegendary.reignofnether.building.buildings.shared.AbstractBridge;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.unit.UnitClientEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The real {@link Vocabulary}, built on the client from the registries and the lang file: every unit type's display
 * name, every building's name, the ability names on the player's own units, and a few spoken synonyms per faction
 * ("anti-air" -> the AA turret the player can build). Rebuilt on demand (the abilities depend on what is owned).
 */
public final class GameVocabulary {
    private GameVocabulary() { }

    /** Spoken word -> the building structure-name fragments it may mean, first match that exists wins. */
    private static final Map<String, List<String>> BUILDING_SYNONYMS = new HashMap<>();
    static {
        BUILDING_SYNONYMS.put("anti-air", List.of("sun spire", "aa", "anti air", "flak", "ward spire", "spire", "tower"));
        BUILDING_SYNONYMS.put("aa", List.of("sun spire", "anti air", "flak", "spire", "tower"));
        BUILDING_SYNONYMS.put("mex", List.of("metal extractor", "extractor"));
        BUILDING_SYNONYMS.put("extractor", List.of("metal extractor"));
        BUILDING_SYNONYMS.put("windmill", List.of("wind generator", "generator"));
        BUILDING_SYNONYMS.put("wind", List.of("wind generator"));
        BUILDING_SYNONYMS.put("house", List.of("house", "pocket", "hut"));
        BUILDING_SYNONYMS.put("farm", List.of("farm"));
        BUILDING_SYNONYMS.put("factory", List.of("barracks", "graveyard", "portal", "grove", "slipway", "lotus academy", "assembly works"));
        BUILDING_SYNONYMS.put("lab", List.of("laboratory", "lab", "arcane tower", "dungeon", "flame sanctuary", "circle of elders", "drydock", "observatory", "machine shop"));
        BUILDING_SYNONYMS.put("storage", List.of("stockpile", "vault", "storage"));
        BUILDING_SYNONYMS.put("converter", List.of("converter"));
        BUILDING_SYNONYMS.put("turret", List.of("watchtower", "tower", "turret", "spire"));
    }

    public static Vocabulary build() {
        Vocabulary v = new Vocabulary();
        // unit types: the display name of every registered unit entity
        List<String> units = new ArrayList<>();
        for (EntityType<?> type : ForgeRegistries.ENTITY_TYPES) {
            var key = ForgeRegistries.ENTITY_TYPES.getKey(type);
            if (key == null || !key.getNamespace().equals(com.solegendary.reignofnether.ReignOfNether.MOD_ID) || !key.getPath().endsWith("_unit"))
                continue;
            units.add(stripFaction(I18n.get(type.getDescriptionId())));
        }
        v.units(units);
        // buildings the player's faction (or anyone) can build: by display name, with the synonyms resolved against
        // what exists for this faction so "anti-air" means our turret and not someone else's
        List<String> buildings = new ArrayList<>();
        List<String> own = new ArrayList<>();   // lower-case names of the player's faction's buildings
        var faction = com.solegendary.reignofnether.player.PlayerClientEvents.getFaction();
        for (Building b : ReignOfNetherRegistries.BUILDING) {
            if (b instanceof AbstractBridge || b.name == null)
                continue;
            buildings.add(b.name);
            if (faction == null || faction == com.solegendary.reignofnether.faction.Factions.NONE || b.getFaction() == faction)
                own.add(b.name.toLowerCase(Locale.ROOT));
        }
        v.buildings(buildings);
        for (var e : BUILDING_SYNONYMS.entrySet()) {
            String target = null;
            for (String frag : e.getValue()) {
                for (String name : own)
                    if (name.contains(frag)) {
                        target = name;
                        break;
                    }
                if (target != null)
                    break;
            }
            if (target != null)
                v.synonym(e.getKey(), target);
        }
        // abilities: the names on the buttons of the player's own units
        List<String> abilities = new ArrayList<>();
        for (LivingEntity le : UnitClientEvents.getAllUnits())
            if (le instanceof Unit u && UnitClientEvents.getPlayerToEntityRelationship(le) == com.solegendary.reignofnether.unit.Relationship.OWNED)
                for (Ability a : u.getAbilities().get()) {
                    var button = a.getButton(u);
                    if (button != null && button.name != null && !abilities.contains(button.name))
                        abilities.add(button.name);
                }
        v.abilities(abilities);
        return v;
    }

    /** "Sunforged Halberdier" is said as "halberdier": the faction prefix is dropped when a name carries one. */
    static String stripFaction(String name) {
        String n = name.trim();
        for (String f : new String[] { "Sunforged ", "Gravebound ", "Horde ", "Verdant ", "Tidewrought ", "Stormcaller ", "Brassworks " })
            if (n.startsWith(f))
                return n.substring(f.length());
        return n;
    }

    /** Kept for the game test: the entity type registered for a unit's display name, or null. */
    public static EntityType<?> unitType(String displayName) {
        for (EntityType<?> type : ForgeRegistries.ENTITY_TYPES) {
            var key = ForgeRegistries.ENTITY_TYPES.getKey(type);
            if (key != null && key.getPath().endsWith("_unit") && stripFaction(I18n.get(type.getDescriptionId())).equalsIgnoreCase(displayName))
                return type;
        }
        return EntityRegistrar.getEntityType(displayName);
    }
}
