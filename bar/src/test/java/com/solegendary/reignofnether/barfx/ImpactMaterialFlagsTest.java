package com.solegendary.reignofnether.barfx;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Combat feel, part 2: a hit's material rides in the IMPACT event's spare detail bit (sparks for metal on metal, dust
 * or debris otherwise), so nothing new goes on the wire and the old readers (the faction tint, the building-hit
 * sound) read exactly what they read before.
 */
class ImpactMaterialFlagsTest {

    @Test
    void whereMetalAndFactionAllReadBack() {
        byte[] wheres = {BarFx.AT_GROUND, BarFx.AT_UNIT, BarFx.AT_BUILDING};
        for (byte where : wheres)
            for (int metal = 0; metal < 2; metal++)
                for (byte f = 0; f <= BarFx.FACTION_MASK; f++) {
                    byte flags = BarFx.impactFlags(where, metal == 1, f);
                    String what = "where " + where + " metal " + metal + " faction " + f;
                    assertEquals(where, BarFx.impactWhere(flags), what);
                    assertEquals(metal == 1, BarFx.impactMetal(flags), what);
                    assertEquals(f, BarFx.shotFaction(flags), what);
                }
    }

    @Test
    void anUnmarkedImpactIsTheOldEncodingBitForBit() {
        for (byte f = 0; f <= BarFx.FACTION_MASK; f++)
            assertEquals(BarFx.shotFlags(BarFx.AT_UNIT, f), BarFx.impactFlags(BarFx.AT_UNIT, false, f));
        // the sound reader compares shotDetail with AT_BUILDING: a metal unit hit must never read as a building hit
        assertEquals(false, BarFx.shotDetail(BarFx.impactFlags(BarFx.AT_UNIT, true, BarFx.F_BRASS)) == BarFx.AT_BUILDING);
    }
}
