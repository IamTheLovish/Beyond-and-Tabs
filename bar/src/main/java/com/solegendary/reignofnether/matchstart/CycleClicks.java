package com.solegendary.reignofnether.matchstart;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Right click steps a cycling button backward (tester, Oct 26; left click still goes forward). Vanilla CycleButtons
 * take only the left button, so the lobby screens hand right clicks here first: a CycleButton under the mouse steps
 * back (its wheel-up step, which wraps and fires the change), and a {@link Back} button (a plain button that cycles in
 * its own press, like the team "T1") runs its back action.
 */
public final class CycleClicks {

    private CycleClicks() {}

    /** A button that cycles on press and has a matching step back for right click. */
    public static final class Back extends Button {
        private final Runnable back;

        public Back(int x, int y, int w, int h, Component label, OnPress forward, Runnable back) {
            super(x, y, w, h, label, forward, DEFAULT_NARRATION);
            this.back = back;
        }

        void stepBack() {
            back.run();
        }
    }

    /** True when a right click ({@code button} 1) stepped a cycling button under the mouse back. */
    public static boolean rightClick(List<? extends GuiEventListener> children, double mx, double my, int button) {
        if (button != 1)
            return false;
        for (GuiEventListener c : children) {
            if (!(c instanceof AbstractWidget w) || !w.visible || !w.active || !w.isMouseOver(mx, my))
                continue;
            if (w instanceof CycleButton<?> cb) {
                cb.mouseScrolled(mx, my, 1.0);   // vanilla: wheel up = one value back, wrapping
            } else if (w instanceof Back b) {
                b.stepBack();
            } else {
                continue;
            }
            w.playDownSound(Minecraft.getInstance().getSoundManager());
            return true;
        }
        return false;
    }
}
