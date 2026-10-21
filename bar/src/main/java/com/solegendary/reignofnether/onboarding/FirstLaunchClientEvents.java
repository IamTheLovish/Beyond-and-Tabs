package com.solegendary.reignofnether.onboarding;

import com.solegendary.reignofnether.config.ReignOfNetherClientConfigs;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.loading.FMLEnvironment;

/**
 * First-launch defaults (FirstLaunch): vanilla's Accessibility onboarding screen is skipped straight to the title
 * (its options stay in Options > Accessibility), and a player's first launch goes full screen once.
 */
public class FirstLaunchClientEvents {

    private static final Minecraft MC = Minecraft.getInstance();

    @SubscribeEvent
    public static void onScreenOpen(ScreenEvent.Opening evt) {
        if (!(evt.getScreen() instanceof AccessibilityOnboardingScreen))
            return;
        MC.options.onboardAccessibility = false;
        MC.options.save();
        evt.setNewScreen(new TitleScreen(true));
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent evt) {
        if (evt.phase != TickEvent.Phase.END || !(MC.screen instanceof TitleScreen)
                || ReignOfNetherClientConfigs.FIRST_LAUNCH_DONE.get())
            return;
        if (FirstLaunch.goFullscreen(FMLEnvironment.production, false, MC.getWindow().isFullscreen())) {
            MC.options.fullscreen().set(true);
            MC.getWindow().toggleFullScreen();
            MC.options.save();
        }
        // a dev run marks nothing, so a tester's real install still gets its first launch
        if (FMLEnvironment.production)
            ReignOfNetherClientConfigs.FIRST_LAUNCH_DONE.set(true);
    }
}
