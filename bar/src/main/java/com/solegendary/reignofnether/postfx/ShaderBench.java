package com.solegendary.reignofnether.postfx;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.solegendary.reignofnether.ReignOfNether;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.Arrays;
import java.util.Locale;

/**
 * Frame times over a fixed number of drawn frames, for the shader pack's cost (design/own_shader_pack.md, "Frame
 * cost"): one {@code [ShaderBench]} line with p50 / p95 / mean, whether a shader pack drew them and the window size.
 * Two ways in: the eyes tour holds a fixed camera and calls {@link #start} (CI: a ratio between lanes - the runner
 * draws in software, so its milliseconds mean nothing for a real GPU), and {@code /rts-shaderbench [frames]}, which a
 * player runs standing still, once with the pack on and once with it off (Oculus's K): the two lines are the pack's
 * cost on that PC. Counted by drawn frames (RenderTickEvent), never wall-clock seconds. A frame cap or vsync hides the
 * cost (at 240 Hz both runs sit at 4.17 ms), so the line says which were on and the command asks for them off.
 */
public final class ShaderBench {

    public static final int COMMAND_FRAMES = 600;

    private static String label = null;     // null: not measuring
    private static long[] nanos = new long[0];
    private static int have = 0;
    private static long lastStart = 0;
    public static volatile String lastLine = null;

    private ShaderBench() {
    }

    /** Measure the next {@code frames} drawn frames under {@code label}; replaces a measurement still running. */
    public static void start(String label, int frames) {
        ShaderBench.label = label;
        nanos = new long[Math.max(frames, 2)];
        have = 0;
        lastStart = 0;
        lastLine = null;
    }

    public static boolean running() {
        return label != null;
    }

    @SubscribeEvent
    public static void onRenderTick(TickEvent.RenderTickEvent evt) {
        if (evt.phase != TickEvent.Phase.START || label == null)
            return;
        long now = System.nanoTime();
        if (lastStart != 0)
            nanos[have++] = now - lastStart;
        lastStart = now;
        if (have == nanos.length)
            finish();
    }

    private static void finish() {
        long[] sorted = nanos.clone();
        Arrays.sort(sorted);
        double mean = Arrays.stream(sorted).average().orElse(0) / 1e6;
        Minecraft mc = Minecraft.getInstance();
        String line = String.format(Locale.ROOT, "[ShaderBench] %s: %d frames, p50 %.2f ms, p95 %.2f ms, mean %.2f ms; "
                + "shader pack %s; %dx%d; fps cap %s, vsync %s", label, sorted.length, sorted[sorted.length / 2] / 1e6,
            sorted[(int) Math.min(sorted.length - 1, Math.ceil(sorted.length * 0.95) - 1)] / 1e6, mean,
            ShaderPacks.inUse() ? "on" : "off", mc.getWindow().getWidth(), mc.getWindow().getHeight(),
            mc.options.framerateLimit().get() >= 260 ? "none" : mc.options.framerateLimit().get(),
            mc.options.enableVsync().get() ? "on" : "off");
        ReignOfNether.LOGGER.info(line);
        lastLine = line;
        label = null;
        if (mc.player != null && mc.level != null && !mc.options.hideGui)
            mc.player.displayClientMessage(Component.literal(line.substring("[ShaderBench] ".length())), false);
    }

    @SubscribeEvent
    public static void onRegisterCommand(RegisterClientCommandsEvent evt) {
        evt.getDispatcher().register(Commands.literal("rts-shaderbench")
            .executes(ctx -> begin(COMMAND_FRAMES))
            .then(Commands.argument("frames", IntegerArgumentType.integer(60, 6000))
                .executes(ctx -> begin(IntegerArgumentType.getInteger(ctx, "frames")))));
    }

    private static int begin(int frames) {
        start("command", frames);
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null)
            mc.player.displayClientMessage(Component.literal("Shader bench: hold still for " + frames
                + " frames, the result goes to the chat and the log. For a true number set Max Framerate to "
                + "Unlimited and VSync off first (Video Settings): a cap hides the pack's cost. Run it once with the "
                + "shader pack on and once off (K), from the same spot."), false);
        return Command.SINGLE_SUCCESS;
    }
}
