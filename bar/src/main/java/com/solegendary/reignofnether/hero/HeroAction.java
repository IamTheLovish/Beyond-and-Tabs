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
    HERO_AI_OFF
}
