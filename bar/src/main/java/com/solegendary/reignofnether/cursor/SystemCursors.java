package com.solegendary.reignofnether.cursor;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWImage;
import org.lwjgl.system.MemoryUtil;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

// hardware (OS) cursors for the RTS view, built once from the same PNGs the virtual cursor draws: one GLFW cursor
// per sprite, scaled by the GUI scale so it matches the old on-screen size, destroyed when the scale changes, the
// setting goes off or the player leaves the world (GLFW frees the rest at shutdown). Client render thread only.
public final class SystemCursors {
    private SystemCursors() {}

    private static final Map<ResourceLocation, Long> CURSORS = new HashMap<>();
    private static final int SPRITE = 16;
    private static int builtScale = -1;
    private static ResourceLocation current = null;

    // makes this sprite the OS cursor of the window; hotspot in 16x16 sprite pixels
    public static void use(long window, ResourceLocation sprite, int hotX, int hotY) {
        int scale = Math.max(1, Math.min(4, Minecraft.getInstance().getWindow().getGuiScale() > 0
                ? (int) Minecraft.getInstance().getWindow().getGuiScale() : 1));
        if (scale != builtScale) {
            dispose(window);
            builtScale = scale;
        }
        if (sprite.equals(current)) return;
        Long handle = CURSORS.get(sprite);
        if (handle == null) {
            handle = create(sprite, scale, hotX, hotY);
            if (handle == null) return; // image failed to load: the OS arrow stays
            CURSORS.put(sprite, handle);
        }
        GLFW.glfwSetCursor(window, handle);
        current = sprite;
    }

    // back to the OS arrow, keeping the cursors made (leaving the RTS view)
    public static void reset(long window) {
        if (current == null) return;
        GLFW.glfwSetCursor(window, 0L);
        current = null;
    }

    // back to the OS arrow and free every cursor made
    public static void dispose(long window) {
        if (CURSORS.isEmpty() && current == null) return;
        GLFW.glfwSetCursor(window, 0L);
        for (long h : CURSORS.values())
            GLFW.glfwDestroyCursor(h);
        CURSORS.clear();
        current = null;
        builtScale = -1;
    }

    private static Long create(ResourceLocation sprite, int scale, int hotX, int hotY) {
        try (InputStream in = Minecraft.getInstance().getResourceManager().open(sprite);
             NativeImage img = NativeImage.read(in)) {
            int w = img.getWidth(), h = img.getHeight();
            int k = scale;
            int ow = w * k, oh = h * k;
            ByteBuffer buf = MemoryUtil.memAlloc(ow * oh * 4);
            try {
                for (int y = 0; y < oh; y++) {
                    for (int x = 0; x < ow; x++) {
                        int abgr = img.getPixelRGBA(x / k, y / k);
                        buf.put((byte) (abgr & 0xFF));          // R
                        buf.put((byte) ((abgr >> 8) & 0xFF));   // G
                        buf.put((byte) ((abgr >> 16) & 0xFF));  // B
                        buf.put((byte) ((abgr >>> 24) & 0xFF)); // A
                    }
                }
                buf.flip();
                GLFWImage glfw = GLFWImage.malloc();
                try {
                    glfw.set(ow, oh, buf);
                    long c = GLFW.glfwCreateCursor(glfw, hotX * k, hotY * k);
                    return c == 0L ? null : c;
                } finally {
                    glfw.free();
                }
            } finally {
                MemoryUtil.memFree(buf);
            }
        } catch (Exception | LinkageError e) {
            CursorDiagClient.event("cursor-image-failed", sprite + " " + e);
            return null;
        }
    }
}
