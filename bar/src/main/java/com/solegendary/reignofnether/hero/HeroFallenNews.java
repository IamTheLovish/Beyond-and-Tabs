package com.solegendary.reignofnether.hero;

/** STUB (the red for HeroFallenAlertGameTests): an alert-wide 5 s window, as the alert had - a second hero is silenced. */
public final class HeroFallenNews {
    public static final long SAME_HERO_MS = 5_000;

    public interface Listener {
        void heroFallen(String ownerName, String heroDescriptionId, String heroUuid);
    }

    private long lastAny = Long.MIN_VALUE;

    public boolean admit(String myName, String ownerName, String heroUuid, long nowMs) {
        if (myName == null || !myName.equals(ownerName))
            return false;
        if (lastAny != Long.MIN_VALUE && nowMs - lastAny < SAME_HERO_MS)
            return false;
        lastAny = nowMs;
        return true;
    }
}
