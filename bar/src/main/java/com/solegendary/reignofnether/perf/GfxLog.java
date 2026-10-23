package com.solegendary.reignofnether.perf;

import com.mojang.blaze3d.platform.GlUtil;
import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.postfx.ShaderPacks;

import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.Locale;

/**
 * One {@code [Gfx]} line when the player joins a world: the GPU, the window, the render distance and whether an
 * Oculus / Iris shader pack is on (Oct 22: "the game feels choppy" is a different problem on an integrated GPU than on
 * a discrete one, and latest.log never said which the player had). tools/lag_report.py reads it. No dependency on
 * Oculus or Iris: the pack is found by class presence and ShaderPacks' reflection.
 */
public final class GfxLog {
    private GfxLog() { }

    /** Words in GL_RENDERER / GL_VENDOR that mark an integrated GPU. Lower case. Mirrored in tools/lag_report.py. */
    private static final String[] INTEGRATED_MARKERS = { "intel", "radeon(tm) graphics", "radeon(tm) 8", "radeon(tm) 7",
        "radeon(tm) 6", "radeon graphics", "890m", "880m", "780m", "760m", "740m", "680m", "660m", "vega 8", "vega 7",
        "vega 6", "vega 3", "uhd graphics", "iris xe", "iris plus", "iris pro", "apple m", "llvmpipe" };

    /** True when the GL renderer / vendor strings look like an integrated (or software) GPU. Pure; unit-tested. */
    public static boolean looksIntegrated(String renderer, String vendor) {
        String s = ((renderer == null ? "" : renderer) + " " + (vendor == null ? "" : vendor)).toLowerCase(Locale.ROOT);
        for (String m : INTEGRATED_MARKERS)
            if (s.contains(m))
                return true;
        return false;
    }

    private static boolean classPresent(String name) {
        try {
            Class.forName(name, false, GfxLog.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError e) {
            return false;
        }
    }

    @SubscribeEvent
    public static void onLogin(ClientPlayerNetworkEvent.LoggingIn evt) {
        try {
            Minecraft mc = Minecraft.getInstance();
            String renderer = GlUtil.getRenderer(), vendor = GlUtil.getVendor();
            boolean installed = classPresent("net.irisshaders.iris.api.v0.IrisApi");
            ReignOfNether.LOGGER.info("[Gfx] renderer \"{}\", vendor \"{}\"{}; window {}x{}, render distance {}; shader pack {}",
                renderer, vendor, looksIntegrated(renderer, vendor) ? " (looks like an integrated GPU)" : "",
                mc.getWindow().getWidth(), mc.getWindow().getHeight(), mc.options.renderDistance().get(),
                ShaderPacks.inUse() ? "ACTIVE" : installed ? "none selected (Oculus/Iris installed)" : "none (no Oculus/Iris)");
        } catch (RuntimeException | LinkageError e) {
            ReignOfNether.LOGGER.warn("[Gfx] could not read the graphics setup: {}", e.toString());
        }
    }
}
