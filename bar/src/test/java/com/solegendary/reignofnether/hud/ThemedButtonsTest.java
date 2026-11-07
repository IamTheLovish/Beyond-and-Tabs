package com.solegendary.reignofnether.hud;

import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** PR #268 review: the button theme must not restyle a checkbox, or it would lose its tick (e.g. ConfigCheckbox). */
class ThemedButtonsTest {

    @Test
    void aCheckboxKeepsItsOwnLook() {
        assertFalse(GothicText.themed(new Checkbox(0, 0, 20, 20, Component.literal("Edge scroll"), true)));
    }
}
