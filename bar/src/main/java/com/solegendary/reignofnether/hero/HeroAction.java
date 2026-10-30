package com.solegendary.reignofnether.hero;

public enum HeroAction {
    SET_EXPERIENCE,
    SET_SKILL_POINTS,
    SET_ABILITY_RANK,
    SET_CHARGES,
    SET_MANA,
    SET_MAX_MANA,
    ACTIVATE_ABILITY_CLIENTSIDE,
    DEACTIVATE_ABILITY_CLIENTSIDE,
    REQUEST_SYNC,
    // the sender's Hero AI switch (ReignOfNetherClientConfigs.HERO_AI): unitId unused
    HERO_AI_ON,
    HERO_AI_OFF,
    // the sender selected their hero (D030: a select hands a fleeing hero back to the player's order): unitId = the hero
    HERO_SELECTED
}
