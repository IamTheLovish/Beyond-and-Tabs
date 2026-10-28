package com.solegendary.reignofnether.unit.modelling.motion;

import com.solegendary.reignofnether.keybinds.Keybindings;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.lwjgl.glfw.GLFW;

/**
 * The living army's A/B switch for judging it (design/living_army.md, section 4): Numpad 0 (rebindable, "Living army
 * on / off") or the client command `/livingarmy on|off` flips it live, on this client only. The config's
 * `living_army` stays the lasting setting; this is for comparing in the parade scene.
 */
@OnlyIn(Dist.CLIENT)
public final class LivingArmyToggle {

    private LivingArmyToggle() {}

    private static void set(boolean on) {
        AnimStates.setToggle(on);
        if (Minecraft.getInstance().player != null)
            Minecraft.getInstance().player.displayClientMessage(
                Component.literal("Living army " + (on ? "ON" : "OFF")), true);
    }

    @SubscribeEvent
    public static void onKey(InputEvent.Key evt) {
        if (evt.getAction() != GLFW.GLFW_PRESS || Minecraft.getInstance().screen != null)
            return;
        if (evt.getKey() == Keybindings.livingArmyToggle.getKey())
            set(!AnimStates.toggle());
    }

    @SubscribeEvent
    public static void onClientCommands(RegisterClientCommandsEvent evt) {
        evt.getDispatcher().register(Commands.literal("livingarmy")
            .then(Commands.literal("on").executes(c -> {
                set(true);
                return 1;
            }))
            .then(Commands.literal("off").executes(c -> {
                set(false);
                return 1;
            })));
    }
}
