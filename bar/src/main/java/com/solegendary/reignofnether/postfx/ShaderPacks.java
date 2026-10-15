package com.solegendary.reignofnether.postfx;

import com.solegendary.reignofnether.ReignOfNether;

import java.lang.reflect.Method;

/**
 * Whether an Oculus / Iris shader pack is drawing the frame. A pack grades, fogs and tone-maps the image itself, so the
 * world look steps aside instead of grading it twice. Read through Iris's public API ({@code IrisApi.getInstance()
 * .isShaderPackInUse()}) by reflection - no compile dependency. Looked up once; absent, renamed or throwing, the answer
 * is "no pack" (and a broken API is never asked again, so a failure costs one warn line, not one per frame).
 */
final class ShaderPacks {

    private static final String API_CLASS = "net.irisshaders.iris.api.v0.IrisApi";

    private static boolean looked = false;
    private static Object api = null;
    private static Method inUse = null;     // null: no Iris API, or it broke

    private ShaderPacks() {
    }

    static boolean inUse() {
        if (!looked)
            find();
        if (inUse == null)
            return false;
        try {
            return Boolean.TRUE.equals(inUse.invoke(api));
        } catch (Exception | LinkageError e) {
            inUse = null;
            ReignOfNether.LOGGER.warn("World look: the Iris API stopped answering ({}); assuming no shader pack", e.toString());
            return false;
        }
    }

    private static void find() {
        looked = true;
        try {
            // the interface's own Method, not the implementation's: that class need not be public
            Class<?> c = Class.forName(API_CLASS);
            api = c.getMethod("getInstance").invoke(null);
            inUse = api != null ? c.getMethod("isShaderPackInUse") : null;
        } catch (ClassNotFoundException e) {
            // no Oculus / Iris installed: nothing to defer to
        } catch (Exception | LinkageError e) {
            ReignOfNether.LOGGER.warn("World look: Oculus / Iris found, but not its shader-pack query ({}); the pass "
                + "stays on", e.toString());
        }
    }
}
