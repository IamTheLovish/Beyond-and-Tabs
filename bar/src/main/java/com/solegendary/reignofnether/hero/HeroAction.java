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
    HERO_SELECTED,
    // the sender's "Auto-learn hero abilities" setting (ReignOfNetherClientConfigs.HERO_AUTO_LEARN): unitId unused
    AUTO_LEARN_ON,
    AUTO_LEARN_OFF,
    // the sender opened / closed their hero's rank-up menu (auto-learn waits while it is open): unitId = the hero
    RANK_MENU_OPEN,
    RANK_MENU_CLOSED
}
