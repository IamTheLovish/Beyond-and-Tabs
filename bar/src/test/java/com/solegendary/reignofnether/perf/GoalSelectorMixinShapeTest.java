package com.solegendary.reignofnether.perf;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The [Goals] hook allocates nothing per goal call while [Sys] is off (#470 review r1, item 2). Mixin hands every
 * {@code @Inject} handler a new CallbackInfo / CallbackInfoReturnable on each call, measuring or not; a {@code @Redirect}
 * handler gets only the call's own arguments. So the hook is four redirects in GoalSelector, no inject, no callback
 * object - and nothing else hooks WrappedGoal's per-call methods with an inject.
 */
class GoalSelectorMixinShapeTest {

    private static final String MIXIN = "src/main/java/com/solegendary/reignofnether/mixin/";

    private static Path path(String file) {
        Path p = Path.of(file);
        return Files.exists(p) ? p : Path.of("bar/" + file);   // run from bar/ or from the repo root
    }

    @Test
    void theGoalHookIsFourRedirectsAndNoCallbackObject() throws IOException {
        // the code only: the javadoc explains why there is no inject and no CallbackInfo
        String src = Files.readString(path(MIXIN + "goals/GoalSelectorMixin.java"))
                .replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("//[^\\n]*", "");
        assertEquals(4, src.split("@Redirect\\(", -1).length - 1, "canContinueToUse, canUse, start, tick");
        assertFalse(src.contains("@Inject"), "an inject handler is handed a new CallbackInfo per call");
        assertFalse(src.contains("CallbackInfo"), "a callback object per goal call");
        assertEquals(4, src.split("if \\(started != GoalTimes\\.OFF\\)", -1).length - 1,
                "the off path stops at the stamp: no getGoal, no map");
    }

    @Test
    void theMixinConfigListsItAndNothingInjectsIntoWrappedGoal() throws IOException {
        String config = Files.readString(path("src/main/resources/reignofnether.mixins.json"));
        assertTrue(config.contains("\"goals.GoalSelectorMixin\""));
        assertFalse(config.contains("WrappedGoalMixin"));
        try (Stream<Path> files = Files.walk(path(MIXIN))) {
            files.filter(f -> f.toString().endsWith(".java")).forEach(f -> {
                try {
                    String s = Files.readString(f);
                    assertFalse(s.contains("@Mixin(WrappedGoal.class)") && s.contains("@Inject"),
                            f + " injects into WrappedGoal: a CallbackInfo per goal call");
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        }
    }
}
