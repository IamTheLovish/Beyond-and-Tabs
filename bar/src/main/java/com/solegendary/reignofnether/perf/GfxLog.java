package com.solegendary.reignofnether.perf;

import com.mojang.blaze3d.platform.GlUtil;
import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.postfx.ShaderPacks;

import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;

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

    /** Physical RAM in bytes, 0 when the JVM cannot say. */
    public static long physicalRamBytes() {
        try {
            if (java.lang.management.ManagementFactory.getOperatingSystemMXBean() instanceof com.sun.management.OperatingSystemMXBean os)
                return os.getTotalMemorySize();
        } catch (RuntimeException | LinkageError e) {
            // fall through: unknown
        }
        return 0;
    }

    /** "Xmx 8192 MB of 16384 MB RAM (50%)", with a warning at 75% or more. */
    public static String memoryText() {
        return DiagRules.xmxText(Runtime.getRuntime().maxMemory(), physicalRamBytes());
    }

    /** The selected shader pack's name ("" when unknown or none), asked of Iris / Oculus by reflection. */
    public static String shaderPackName() {
        for (String cls : new String[] { "net.irisshaders.iris.Iris", "net.coderbot.iris.Iris" }) {
            try {
                Object r = Class.forName(cls, false, GfxLog.class.getClassLoader()).getMethod("getCurrentPackName").invoke(null);
                if (r != null)
                    return r.toString();
            } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
                // try the next package name
            }
        }
        return "";
    }

    /** "Oculus 1.8.0" / "Iris 1.7.2" for whichever is loaded, "" when neither. */
    public static String shaderModVersion() {
        StringBuilder sb = new StringBuilder();
        try {
            for (String id : new String[] { "oculus", "iris", "embeddium", "rubidium" })
                ModList.get().getModContainerById(id).ifPresent(c ->
                    sb.append(sb.length() > 0 ? ", " : "").append(c.getModInfo().getDisplayName()).append(' ').append(c.getModInfo().getVersion()));
        } catch (RuntimeException | LinkageError e) {
            // unknown
        }
        return sb.toString();
    }

    /** The shader part of [Gfx] and of the report: active / none, pack name, mod versions, errors seen so far. */
    public static String shaderSummary() {
        boolean installed = classPresent("net.irisshaders.iris.api.v0.IrisApi");
        String pack = shaderPackName();
        return (ShaderPacks.inUse() ? "ACTIVE" : installed ? "none selected (Oculus/Iris installed)" : "none (no Oculus/Iris)")
            + (pack.isEmpty() ? "" : " \"" + pack + "\"") + " [" + shaderModVersion() + "]"
            + "; shader/GL errors seen " + ShaderErrors.total();
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
            ReignOfNether.LOGGER.info("[Gfx] {}; shader pack detail: {}", memoryText(), shaderSummary());
        } catch (RuntimeException | LinkageError e) {
            ReignOfNether.LOGGER.warn("[Gfx] could not read the graphics setup: {}", e.toString());
        }
    }
}
