package com.solegendary.reignofnether.hero;

import com.solegendary.reignofnether.hud.HudClientEvents;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Keeps the server's rank-up menu hold alive (HeroBot.RANK_MENU_HOLD_TICKS): while the menu is up on the selected hero,
 * RANK_MENU_OPEN goes again every 5 s. Once the hero is no longer selected (or the client is gone) the refreshes stop
 * and the hold lapses by itself, so auto-learn is never held back by a menu nobody is looking at.
 */
@OnlyIn(Dist.CLIENT)
public final class HeroRankMenuClient {
    private HeroRankMenuClient() { }

    private static final int REFRESH_TICKS = 5 * 20;
    private static int ticks = 0;

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent evt) {
        if (evt.phase != TickEvent.Phase.END || ++ticks < REFRESH_TICKS)
            return;
        ticks = 0;
        if (HudClientEvents.hudSelectedEntity instanceof FactionHero hero && hero.isRankUpMenuOpen())
            HeroServerboundPacket.sendRankMenu(HudClientEvents.hudSelectedEntity.getId(), true);
    }
}
