package com.solegendary.reignofnether.onboarding;

/**
 * The game's first-launch and progress-noise rules, kept free of client classes so FirstLaunchGameTests can check
 * them headless (Lane C tester, Oct 20: "disable achievements for the mod as well as the accessibility menu opening by
 * default", "set the game to open to full screen at the start").
 */
public final class FirstLaunch {

    private FirstLaunch() {}

    /** Advancement and recipe toasts are dropped; every other toast shows. */
    public static boolean hideToast(boolean advancement, boolean recipe) {
        return advancement || recipe;
    }

    public static final int DEFAULT_GUI_SCALE = 3;

    /**
     * GUI scale 3 once per install (menus, lobby and RTS view read better): only on a real install, only once, and
     * only while the player's scale is still Auto (0) - a scale they chose is never overridden.
     */
    public static boolean setDefaultGuiScale(boolean production, boolean alreadyApplied, int currentGuiScale) {
        return production && !alreadyApplied && currentGuiScale == 0;
    }

    /**
     * Full screen once, on the first launch of a player's install: never in a dev run (runClient, the CI and local
     * check harnesses, which would otherwise cover the developer's main monitor), never again once applied, so a
     * player who goes windowed stays windowed.
     */
    public static boolean goFullscreen(boolean production, boolean alreadyApplied, boolean alreadyFullscreen) {
        return production && !alreadyApplied && !alreadyFullscreen;
    }
}
