package com.solegendary.reignofnether.hero.wager;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.Mob;

import java.util.ArrayList;
import java.util.List;

/**
 * One hero's Wager Book: pages left, the cooldown, the margin tally and the written pages (request and reply). Kept in
 * the hero's persistent data so it saves with the hero and the book is his memory (design "What he writes back"). A
 * view over that tag: every setter writes straight through, so two views of the same hero always agree.
 */
public final class WagerState {

    public static final String TAG = "bat_wager";
    public static final int PAGE_REGROW_TICKS = 30 * 20;
    public static final int MAX_ENTRIES = 40;

    /** One written page: the player's request and Lovish's reply under it. */
    public record Entry(String request, String reply) { }

    private final CompoundTag tag;

    private WagerState(CompoundTag tag) {
        this.tag = tag;
    }

    /** The hero's book; a hero that never had one gets a full book for its level. */
    public static WagerState of(Mob hero) {
        CompoundTag data = hero.getPersistentData();
        if (!data.contains(TAG, Tag.TAG_COMPOUND)) {
            CompoundTag t = new CompoundTag();
            t.putInt("pages", WagerVerdict.maxPages(Wager.levelOf(hero)));
            t.putLong("regrowAt", hero.level().getGameTime() + PAGE_REGROW_TICKS);
            data.put(TAG, t);
        }
        return new WagerState(data.getCompound(TAG));
    }

    /** A page grows back every 30 s up to the level's maximum (pages are his mana, drawn as the book's thickness). */
    void regrow(long now, int level) {
        int max = WagerVerdict.maxPages(level);
        long at = tag.getLong("regrowAt");
        int pages = pages();
        while (pages < max && now >= at) {
            pages++;
            at += PAGE_REGROW_TICKS;
        }
        if (pages >= max)
            at = now + PAGE_REGROW_TICKS;   // a full book starts its next page's clock when one is spent
        tag.putInt("pages", pages);
        tag.putLong("regrowAt", at);
    }

    public int pages() { return tag.getInt("pages"); }
    public void setPages(int pages) { tag.putInt("pages", Math.max(0, pages)); }
    public long cooldownUntil() { return tag.getLong("cooldownUntil"); }
    public void setCooldownUntil(long tick) { tag.putLong("cooldownUntil", tick); }
    long lastAskAt() { return tag.contains("lastAskAt") ? tag.getLong("lastAskAt") : Long.MIN_VALUE / 2; }
    void setLastAskAt(long tick) { tag.putLong("lastAskAt", tick); }
    String lastTwistIntent() { return tag.getString("lastTwist"); }
    void setLastTwistIntent(String intent) { tag.putString("lastTwist", intent == null ? "" : intent); }

    public int asks() { return tag.getInt("asks"); }
    public int granted() { return tag.getInt("granted"); }
    public int refused() { return tag.getInt("refused"); }
    public int twisted() { return tag.getInt("twisted"); }

    void count(WagerVerdict.Kind kind) {
        tag.putInt("asks", asks() + 1);
        String key = kind == WagerVerdict.Kind.REFUSE ? "refused" : kind == WagerVerdict.Kind.TWIST ? "twisted" : "granted";
        tag.putInt(key, tag.getInt(key) + 1);
    }

    public List<Entry> entries() {
        ListTag list = tag.getList("entries", Tag.TAG_COMPOUND);
        List<Entry> out = new ArrayList<>(list.size());
        for (int i = 0; i < list.size(); i++) {
            CompoundTag e = list.getCompound(i);
            out.add(new Entry(e.getString("req"), e.getString("reply")));
        }
        return out;
    }

    /** Writes a page; the oldest pages are torn out past {@link #MAX_ENTRIES}. */
    void write(String request, String reply) {
        ListTag list = tag.getList("entries", Tag.TAG_COMPOUND);
        CompoundTag e = new CompoundTag();
        e.putString("req", request);
        e.putString("reply", reply);
        list.add(e);
        while (list.size() > MAX_ENTRIES)
            list.remove(0);
        tag.put("entries", list);
    }

    /** The margin's running tally: "Asks: 17. Granted: 9. Regretted: 4." (regretted = twisted). */
    public String tally() {
        return "Asks: " + asks() + ". Granted: " + granted() + ". Regretted: " + twisted() + ".";
    }
}
