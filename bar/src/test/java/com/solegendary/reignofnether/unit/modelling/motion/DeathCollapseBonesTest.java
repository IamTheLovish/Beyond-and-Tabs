package com.solegendary.reignofnether.unit.modelling.motion;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The collapse writes its bones in the living army's layout (Motion's bone ids and channels), so MotionPass.place puts
 * them on any body the living army already knows - the two lists must never drift apart.
 */
class DeathCollapseBonesTest {

    @Test
    void theCollapseUsesMotionsBonesAndChannels() {
        assertEquals(Motion.BODY, DeathCollapse.BODY);
        assertEquals(Motion.HEAD, DeathCollapse.HEAD);
        assertEquals(Motion.ARM_R, DeathCollapse.ARM_R);
        assertEquals(Motion.ARM_L, DeathCollapse.ARM_L);
        assertEquals(Motion.LEG_R, DeathCollapse.LEG_R);
        assertEquals(Motion.LEG_L, DeathCollapse.LEG_L);
        assertEquals(Motion.ARMS, DeathCollapse.ARMS);
        assertEquals(Motion.BONES, DeathCollapse.BONES);
        assertEquals(Motion.CH, DeathCollapse.CH);
        assertEquals(Motion.OUT, DeathCollapse.OUT);
        assertEquals(Motion.RX, DeathCollapse.RX);
        assertEquals(Motion.RZ, DeathCollapse.RZ);
    }
}
