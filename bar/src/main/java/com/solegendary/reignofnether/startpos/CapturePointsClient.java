package com.solegendary.reignofnether.startpos;

import java.util.List;

/**
 * Clientside copy of the capture sites (position, kind, owner). The sites themselves are vanilla marker entities,
 * which never reach clients, so the server syncs this small list instead; the strategic view reads it to draw a
 * flag glyph on each site.
 */
public final class CapturePointsClient {
    private CapturePointsClient() { }

    /**
     * {@code progress}: capture progress 0..1 (towards {@code claimant} while neutral, the owner's hold while owned);
     * {@code claimant}: who last stood on it alone (capturing, or neutralizing an owned site). For the map labels.
     * A Forsaken Garrison also sends {@code muster} (0..100, towards its holder's next recruit) and {@code banked}
     * (recruits waiting while it is neutral); every other kind sends 0 for both.
     */
    public record Site(int x, int y, int z, int kind, String owner, float progress, String claimant, int muster, int banked) { }


    // replaced wholesale on every sync, so readers on the render thread can iterate without locking
    private static volatile List<Site> sites = List.of();

    public static List<Site> getSites() {
        return sites;
    }

    public static void sync(List<Site> newSites) {
        // owner changes of our own sites become "site captured / lost" notifications
        com.solegendary.reignofnether.hud.NotificationClientEvents.onCaptureSitesSynced(sites, newSites);
        sites = List.copyOf(newSites);
    }

    public static void clear() {
        sites = List.of();
    }
}
