package com.solegendary.reignofnether.hero.wager;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.List;

/**
 * The open Wager Book (design "The book" / "RTS view"): a two-page spread drawn in our own colours - tea-stained
 * parchment in patched leather - with the margin tally on top. The left page is the history (his replies in his own
 * ink, violet), the right page is where the player writes: the last non-empty line is the request, notes above it are
 * kept. Wager casts it (the server decides everything), Esc closes without casting. The cursor stays free and the game
 * is not paused, so the RTS camera keeps running behind the book.
 */
@OnlyIn(Dist.CLIENT)
public class WagerBookScreen extends Screen {

    static final int W = 320, H = 200;
    static final int LEATHER = 0xFF4E3220, PATCH = 0xFF6B4A2E, PAPER = 0xFFE9DCB8, STAIN = 0x22A0703A,
        INK = 0xFF2B2118, HIS_INK = 0xFF5B2A86, FAINT = 0xFF8A7A5C, CLASP = 0xFFC9A23A;

    private final int heroId;
    private MultiLineEditBox page;

    public WagerBookScreen(int heroId) {
        super(Component.translatable("item.reignofnether.wager_book"));
        this.heroId = heroId;
    }

    int left() { return (this.width - W) / 2; }
    int top() { return (this.height - H) / 2; }

    @Override
    protected void init() {
        super.init();
        int x = left() + W / 2 + 8, y = top() + 24;
        String kept = page != null ? page.getValue() : "";
        page = new MultiLineEditBox(this.font, x, y, W / 2 - 18, H - 64,
            Component.literal("Write slowly. I read slowly."), Component.literal("Request"));
        page.setCharacterLimit(WagerServerboundPacket.MAX_TEXT);
        page.setValue(kept);
        addRenderableWidget(page);
        setInitialFocus(page);
        addRenderableWidget(Button.builder(Component.literal("Wager"), b -> wager())
            .bounds(x, top() + H - 34, 70, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Close"), b -> onClose())
            .bounds(x + W / 2 - 18 - 70, top() + H - 34, 70, 20).build());
    }

    void wager() {
        String text = page.getValue();
        if (WagerParser.requestLine(text).isEmpty())
            return;
        WagerServerboundPacket.send(heroId, text);
        page.setValue("");
    }

    /** A reply arrived while the book is open: the history page redraws from WagerClient on the next frame. */
    void onReply() { }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        int x = left(), y = top();
        // the cover, a patch, the two pages, a tea ring, the die-shaped clasp
        g.fill(x - 6, y - 6, x + W + 6, y + H + 6, LEATHER);
        g.fill(x + W - 40, y + H - 2, x + W + 2, y + H + 5, PATCH);
        g.fill(x, y, x + W / 2 - 2, y + H, PAPER);
        g.fill(x + W / 2 + 2, y, x + W, y + H, PAPER);
        g.fill(x + 22, y + H - 52, x + 62, y + H - 12, STAIN);
        g.fill(x + W / 2 - 5, y + H / 2 - 7, x + W / 2 + 5, y + H / 2 + 7, CLASP);
        g.fill(x + W / 2 - 1, y + H / 2 - 1, x + W / 2 + 1, y + H / 2 + 1, LEATHER);

        g.drawString(this.font, WagerClient.tally, x + 8, y + 6, FAINT, false);
        if (WagerClient.pagesLeft >= 0)
            g.drawString(this.font, "Pages: " + WagerClient.pagesLeft, x + W / 2 + 8, y + 6, FAINT, false);
        g.drawString(this.font, "Lovish's Wager Book", x + W / 2 + 8, y + 14, INK, false);

        // the history: newest at the bottom, as many as fit
        List<WagerClient.Page> pages = WagerClient.PAGES;
        int ty = y + H - 10, lw = W / 2 - 16;
        for (int i = pages.size() - 1; i >= 0 && ty > y + 22; i--) {
            WagerClient.Page p = pages.get(i);
            int replyH = this.font.wordWrapHeight(p.reply(), lw);
            int reqH = this.font.wordWrapHeight("\"" + p.request() + "\"", lw);
            ty -= replyH + reqH + 6;
            if (ty < y + 22)
                break;
            g.drawWordWrap(this.font, Component.literal("\"" + p.request() + "\""), x + 8, ty, lw, INK);
            g.drawWordWrap(this.font, Component.literal(p.reply()), x + 8, ty + reqH + 1, lw, HIS_INK);
        }
        if (pages.isEmpty())
            g.drawWordWrap(this.font, Component.literal("Write a request on the right. The last line is the one I read."),
                x + 8, y + 24, lw, FAINT);
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
