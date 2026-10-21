package com.solegendary.reignofnether.postfx;

import com.solegendary.reignofnether.ReignOfNether;

import java.lang.reflect.Method;

/**
 * Whether an Oculus / Iris shader pack is drawing the frame. A pack grades, fogs and tone-maps the image itself, so the
 * world look steps aside instead of grading it twice. Read through Iris's public API ({@code IrisApi.getInstance()
 * .isShaderPackInUse()}) by reflection - no compile dependency. Looked up once; absent, renamed or throwing, the answer
 * is "no pack" (and a broken API is never asked again, so a failure costs one warn line, not one per frame).
 * {@link #renderingShadowPass()} asks the same API whether the pack's shadow map is being drawn right now: a renderer
 * that draws something translucent (the Bone Dragon's wing sail) skips it there, or the shadow pass draws its quads
 * as solid black rectangles on the ground.
 */
public final class ShaderPacks {

    private static final String API_CLASS = "net.irisshaders.iris.api.v0.IrisApi";

    private static boolean looked = false;
    private static Object api = null;
    private static Method inUse = null;     // null: no Iris API, or it broke
    private static Method shadow = null;    // isRenderingShadowPass, same rules

    private ShaderPacks() {
    }

    public static boolean inUse() {
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

    /** True while an Oculus / Iris shader pack is drawing its shadow map (false without a pack or its API). */
    public static boolean renderingShadowPass() {
        if (!looked)
            find();
        if (shadow == null)
            return false;
        try {
            return Boolean.TRUE.equals(shadow.invoke(api));
        } catch (Exception | LinkageError e) {
            shadow = null;
            ReignOfNether.LOGGER.warn("Shadow pass: the Iris API stopped answering ({}); assuming none", e.toString());
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
            try {
                shadow = api != null ? c.getMethod("isRenderingShadowPass") : null;
            } catch (NoSuchMethodException e) {
                shadow = null;             // an older API without the query: the sail keeps its shadow
            }
        } catch (ClassNotFoundException e) {
            // no Oculus / Iris installed: nothing to defer to
        } catch (Exception | LinkageError e) {
            ReignOfNether.LOGGER.warn("World look: Oculus / Iris found, but not its shader-pack query ({}); the pass "
                + "stays on", e.toString());
        }
    }
}
