package com.solegendary.reignofnether.unit;

public enum UnitAction {
    // if a non-attack unit is asked to attack it will move instead
    // same for if a non-builder unit is asked to build or repair

    NONE, // basically null, but sending null to server crashes packetHandler
    ATTACK,
    ATTACK_BUILDING,
    STOP,
    HOLD,
    PATROL, // walk between here and the ordered point forever; fighters attack-move, workers repair en route
    GUARD, // stick with the clicked unit/building: fighters defend it, workers assist/repair it
    MOVE,
    GARRISON,
    UNGARRISON,
    ATTACK_MOVE, // enacted by attack button + left click on ground
    FOLLOW, // enacted by move button + left click on another entity
    BUILD_REPAIR, // build or repair the building at the targeted blockPos
    ENABLE_AUTOCAST_BUILD_REPAIR,
    DISABLE_AUTOCAST_BUILD_REPAIR,
    FARM, // sets the villager's target gather resource
    TOGGLE_GATHER_TARGET, // cycle between gathering nothing, food, wood or ore
    RETURN_RESOURCES, // drops off resources to the building
    RETURN_RESOURCES_TO_CLOSEST, // drops off resources to the nearest building that accepts resources
    DELETE, // instantly kills this unit
    DISCARD, // instantly removes this unit from the level without any death animation/event
    ENABLE_AUTO_FIND_ENEMIES,
    DISABLE_AUTO_FIND_ENEMIES,

    ATTACK_GROUND,
    // special abilities - these can also be assigned to cursor actions
    EXPLODE,
    CALL_LIGHTNING, // actually not from a unit, but we'll make an exception
    SET_GRAVEYARD_RELEASE_ON,
    SET_GRAVEYARD_RELEASE_OFF,
    TELEPORT,
    MOUNT,
    MOUNT_SPIDER,
    MOUNT_RAVAGER,
    MOUNT_HOGLIN,
    DISMOUNT, // passenger removes itself from vehicle
    EJECT, // vehicle removes itself from passengers
    PROMOTE_ILLAGER,
    ROAR,
    THROW_HEALING_POTION,
    THROW_HEALING_POTION_AUTOCAST_ENABLE,
    THROW_HEALING_POTION_AUTOCAST_DISABLE,
    THROW_WATER_POTION,
    THROW_WATER_POTION_AUTOCAST_ENABLE,
    THROW_WATER_POTION_AUTOCAST_DISABLE,
    THROW_LINGERING_HARMING_POTION,
    THROW_LINGERING_HARMING_POTION_AUTOCAST_ENABLE,
    THROW_LINGERING_HARMING_POTION_AUTOCAST_DISABLE,
    THROW_LINGERING_REGEN_POTION,
    THROW_LINGERING_REGEN_POTION_AUTOCAST_ENABLE,
    THROW_LINGERING_REGEN_POTION_AUTOCAST_DISABLE,
    SET_FANGS_LINE,
    SET_FANGS_CIRCLE,
    CAST_SUMMON_VEXES,
    CAST_SUMMON_VEXES_AUTOCAST_ENABLE,
    CAST_SUMMON_VEXES_AUTOCAST_DISABLE,
    CAST_SONIC_BOOM,
    TOGGLE_SHIELD_RAISE,
    SHOOT_FIREWALL,
    CONNECT_PORTAL,
    DISCONNECT_PORTAL,
    GOTO_PORTAL,
    SACRIFICE,
    SACRIFICE_AUTOCAST_ENABLE,
    SACRIFICE_AUTOCAST_DISABLE,
    WITHER_CLOUD,
    SPIN_WEBS,
    SPIN_WEBS_AUTOCAST_ENABLE,
    SPIN_WEBS_AUTOCAST_DISABLE,
    FEAR,
    FEAR_AUTOCAST_ENABLE,
    FEAR_AUTOCAST_DISABLE,
    POSSESS,
    BLOOD_LUST,
    CALL_TO_ARMS_BUILDING,
    CALL_TO_ARMS_UNIT,
    BACK_TO_WORK_BUILDING,
    BACK_TO_WORK_UNIT,
    CONSUME_SLIME,
    CONSUME_SLIME_AUTOCAST_ENABLE,
    CONSUME_SLIME_AUTOCAST_DISABLE,
    TOGGLE_SPIDER_CLIMBING,
    MILITIA_USE_SWORD,
    MILITIA_USE_BOW,

    // hero abilities
    BLOOD_MOON,
    INSOMNIA_CURSE,
    RAISE_DEAD,
    SOUL_SIPHON_HEAL,
    SOUL_SIPHON_UPDATE,
    ENABLE_SOUL_SIPHON_PASSIVE,
    DISBLE_SOUL_SIPHON_PASSIVE,
    FANCY_FEAST,
    ENABLE_GREED_IS_GOOD_PASSIVE,
    DISABLE_GREED_IS_GOOD_PASSIVE,
    LOOT_EXPLOSION,
    THROW_TNT,
    AVATAR,
    MACE_SLAM,
    TAUNTING_CRY,

    CHILLING_SCREECH,
    FROSTBLINK,
    BLIZZARD,
    MOLTEN_BOMB,
    SCORCHING_GAZE,
    SOULS_AFLAME,
    CIVIL_ENCHANTMENT,
    CIVIL_ENCHANTMENT_AUTOCAST_ENABLE,
    CIVIL_ENCHANTMENT_AUTOCAST_DISABLE,
    MARTIAL_ENCHANTMENT,
    MARTIAL_ENCHANTMENT_AUTOCAST_ENABLE,
    MARTIAL_ENCHANTMENT_AUTOCAST_DISABLE,
    PROTECTIVE_ENCHANTMENT,
    PROTECTIVE_ENCHANTMENT_AUTOCAST_ENABLE,
    PROTECTIVE_ENCHANTMENT_AUTOCAST_DISABLE,
    MARCH_OF_PROGRESS_TOGGLE,
    MARCH_OF_PROGRESS_SET,
    TOGGLE_FLYING,

    ENCHANT_MULTISHOT,
    ENCHANT_MAIMING,
    ENCHANT_SHARPNESS,
    ENCHANT_VIGOR,
    ENCHANT_GUST,
    ENCHANT_QUICKCHARGE,

    SELL_FOOD,
    SELL_WOOD,
    SELL_ORE,
    BUY_FOOD,
    BUY_WOOD,
    BUY_ORE,

    EQUIP_LEATHER_ARMOR,
    EQUIP_CHAINMAIL_ARMOR,

    BEACON_HASTE,
    BEACON_RESISTANCE,
    BEACON_WEALTH,
    BEACON_STRENGTH,
    BEACON_REGENERATION,

    STARTRTS_VILLAGERS,
    STARTRTS_MONSTERS,
    STARTRTS_PIGLINS,

    SET_ATTACK_COUNT, // for marauders

    DEBUG1,
    DEBUG2,

    COMMANDER_ABILITY, // Beyond and Tabs: each faction's commander signature ability (kept last: enums travel by ordinal)
    COMMANDER_DGUN,    // Beyond and Tabs: the commander's D-gun line shot
    COMMANDER_RAISE_DEAD, // Beyond and Tabs: Gravebound commander raises wrecks as Ghouls
    TRAMPLE,              // Beyond and Tabs: War Mammoth charge
    SOLAR_LANCE,          // Beyond and Tabs: Sun Colossus beam
    ROOTQUAKE,            // Beyond and Tabs: Verdant Court World Tree Walker root eruption (T3)
    CRYPT_TIDE,           // Beyond and Tabs: Gravebound Wraith bone-hand root
    WAR_DRUMS,            // Beyond and Tabs: Horde Warlord speed rally
    BASTION_AEGIS,        // Beyond and Tabs: Sunforged Evoker projectile dome
    RECLAIM,              // Beyond and Tabs: walk a worker to one wreck and stay on it until it is stripped (area reclaim)
    TOTEM_OF_THE_PACK,    // Beyond and Tabs: Horde Bonewright wolf totem
    MAGMA_RUPTURE,        // Beyond and Tabs: Horde Blaze ground-crack eruption
    HOLY_BELL,            // Beyond and Tabs: Sunforged Royal Architect reveal pulse
    WITHERING_FOG,        // Beyond and Tabs: Gravebound Bone Dragon lingering fog line
    SUNRISE_SORTIE,       // Beyond and Tabs: Sunforged Lord Marshal line charge
    SOUL_WISPS,           // Beyond and Tabs: Gravebound Embalmer reclaim/harass wisps
    LEAF_DASH,            // Beyond and Tabs: Verdant Court Leafblade gap-closing dash
    AWAKEN_THICKET,       // Beyond and Tabs: Verdant Court Elder Druid temporary treant
    STAG_LEAP,            // Beyond and Tabs: Verdant Court Stag Lancer leaping charge
    BLOOM,                // Beyond and Tabs: Verdant Court Bloom Priestess heal-and-cleanse burst
    PLANT_VINE_SNARE,     // Beyond and Tabs: Verdant Court Seedshaper hidden root trap
    PLANT_THICKET,        // Beyond and Tabs: Verdant Court Seedshaper 3x3 cover thicket (Living Terrain)
    OVERGROWTH,           // Beyond and Tabs: Verdant Court Grove Warden instant thicket ring + root
    RAISE_TIDEPOOL,       // Beyond and Tabs: Tidewrought Tide Priest aimed tidepool (the Tides core)
    FOG_BANK,             // Beyond and Tabs: Tidewrought Admiral sea-fog cloud that hides friendly units
    RAISE_GREAT_TIDEPOOL, // Beyond and Tabs: Tidewrought Harbourmaster's larger, longer tidepool (T2)
    TENTACLE_GRAB,        // Beyond and Tabs: Tidewrought Kraken Caller drag-and-root on one enemy (T2)
    TIDAL_SLAM,           // Beyond and Tabs: Tidewrought Leviathan telegraphed wide-arc wave slam (T3)
    BLINK_STRIKE,         // Beyond and Tabs: Stormcallers Blade Dancer teleport onto an enemy (autocast on archers)
    SEAL,                 // Beyond and Tabs: Stormcallers Ink Scribe silences an enemy unit's abilities
    FROST_FIELD,          // Beyond and Tabs: Stormcallers Frost Adept temporary slowing frost sheet
    WARD,                 // Beyond and Tabs: Stormcallers Thunder Sage channelled projectile/spell ward
    LEY_LINK,             // Beyond and Tabs: Stormcallers Geomancer two-click ley link between own buildings
    MANA_WELL,            // Beyond and Tabs: Stormcallers Ley Master raises a spell-empowering well (T2)
    GROUND_SLAM,          // Beyond and Tabs: Stormcallers Earthshaker telegraphed damaging + rooting line (T2)
    SINGULARITY,          // Beyond and Tabs: Stormcallers Gravity Monk pulls every enemy round a spot together (T2)
    MIRROR_IMAGES,        // Beyond and Tabs: Stormcallers Mirror Mage conjures three harmless decoys of a friend (T2)
    LANTERN_DOME,         // Beyond and Tabs: Stormcallers Lantern Fortress toggles its channelled projectile-soaking dome (T2)
    TEMPEST,              // Beyond and Tabs: Stormcallers Celestial Dragon aims a roaming lightning storm (T3)
    SET_STANCE_HOLD_FIRE,   // Beyond and Tabs: BAR fire state toggles (EngagementStance) - no behaviour reset
    SET_STANCE_RETURN_FIRE,
    SET_STANCE_FIRE_AT_WILL,
    FIELD_UPGRADE_PLATING,  // Beyond and Tabs: Brassworks Tinker rivets brass plating onto a machine (once)
    FIELD_UPGRADE_BARREL,   // Beyond and Tabs: Brassworks Tinker fits a machine with a longer barrel (once)
    DEPLOY_TURRET,          // Beyond and Tabs: Brassworks Guildmaster sets a temporary auto-turret
    PROJECT_SHIELD,         // Beyond and Tabs: Brassworks Shield Projector bubble that follows a friend
    PLANT_CHARGE,           // Beyond and Tabs: Brassworks Sapper timed charge on an enemy building
    BURROW,                 // Beyond and Tabs: Brassworks Drill Burrower tunnels (untargetable) and surfaces behind the line (T2)
    // Beyond and Tabs hero slice 2 (design/hero_mode.md): a faction hero's four ability slots. One order per slot, not per
    // ability - a player has one faction hero, so the slot names the ability (hero/FactionHeroAbility, hero/HeroKits)
    HERO_ABILITY_1,
    HERO_ABILITY_2,
    HERO_ABILITY_3,
    HERO_ABILITY_4,
    EMBARK,                 // Beyond and Tabs: Mossback Bison loads nearby friendly infantry into its grove (T2 beast, design/big_units.md)
    BREACH_CHARGE           // Beyond and Tabs: Gorehorn Rhino aimed lane charge through units, stopped by a building it damages (T2 beast)
}
