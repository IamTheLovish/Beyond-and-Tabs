package com.solegendary.reignofnether.unit.interfaces;

/**
 * Marker: this unit's anti-air is wind (design/stormcallers_plan.md section 3.6) - the Stormcallers' Wind Monk, the
 * Sunforged Windcaller's gust, the Verdant Wisp Choir and the Tidewrought Flak Swivel. Read only by the Celestial
 * Dragon's grounding counter (slice 4: a LivingHurtEvent handler on that one entity type), so the marker costs nothing
 * anywhere else. Every faction should field at least one marked unit before the dragon ships.
 */
public interface WindAntiAir {
}
