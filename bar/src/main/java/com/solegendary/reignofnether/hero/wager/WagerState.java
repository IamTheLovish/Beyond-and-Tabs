package com.solegendary.reignofnether.hero.wager;

import net.minecraft.world.entity.Mob;

import java.util.List;

/**
 * One hero's Wager Book: pages left, the cooldown, the margin tally and the written pages (request and reply). Kept in
 * the hero's persistent data so it saves with the hero and the book is his memory (design "What he writes back").
 */
public final class WagerState {

    public static final String TAG = "bat_wager";
    public static final int PAGE_REGROW_TICKS = 30 * 20;
    public static final int MAX_ENTRIES = 40;

    /** One written page: the player's request and Lovish's reply under it. */
    public record Entry(String request, String reply) { }

    private WagerState() { }

    public static WagerState of(Mob hero) {
        return new WagerState();
    }

    public int pages() { return 0; }
    public void setPages(int pages) { }
    public long cooldownUntil() { return 0; }
    public void setCooldownUntil(long tick) { }
    public int asks() { return 0; }
    public int granted() { return 0; }
    public int refused() { return 0; }
    public int twisted() { return 0; }
    public List<Entry> entries() { return List.of(); }

    /** The margin's running tally: "Asks: 17. Granted: 9. Regretted: 4." (regretted = twisted). */
    public String tally() { return ""; }
}
