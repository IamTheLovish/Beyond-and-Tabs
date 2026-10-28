package com.solegendary.reignofnether.hero.wager;

import com.solegendary.reignofnether.hero.FactionHero;
import com.solegendary.reignofnether.hero.PossessionClientEvents;
import com.solegendary.reignofnether.unit.UnitClientEvents;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;

/**
 * Client side of the Wager Book: the toggle's state (from the server), which hero the book is open for, and the pages
 * written so far this session (request and reply) so the open book shows its history.
 */
@OnlyIn(Dist.CLIENT)
public final class WagerClient {
    private WagerClient() { }

    /** The server's debug toggle (/wagerbook): while on, key 1 of a faction hero opens the book. */
    public static volatile boolean enabled = false;

    /** One page of the book as the client saw it. */
    record Page(int heroId, String request, String reply) { }

    static final List<Page> PAGES = new ArrayList<>();
    static String tally = "Asks: 0. Granted: 0. Regretted: 0.";
    static int pagesLeft = -1;

    /** Opens the book for one hero (key 1 on the command card, or while possessing). */
    public static void open(int heroId) {
        Minecraft.getInstance().setScreen(new WagerBookScreen(heroId));
    }

    /** The item: the possessed hero, else the first selected faction hero, else the server picks the nearest. */
    public static void openForSelection() {
        int id = PossessionClientEvents.getPossessedId();
        if (id < 0) {
            for (LivingEntity le : UnitClientEvents.getSelectedUnits())
                if (le instanceof FactionHero) {
                    id = le.getId();
                    break;
                }
        }
        open(id);
    }

    static void onReply(int heroId, String request, String reply, String newTally, int pages) {
        PAGES.add(new Page(heroId, request, reply));
        while (PAGES.size() > 40)
            PAGES.remove(0);
        tally = newTally;
        pagesLeft = pages;
        if (Minecraft.getInstance().screen instanceof WagerBookScreen s)
            s.onReply();
    }
}
