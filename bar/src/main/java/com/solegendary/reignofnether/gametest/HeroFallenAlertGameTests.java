package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.hero.FallenHeroClientboundPacket;
import com.solegendary.reignofnether.hero.HeroFallenNews;
import com.solegendary.reignofnether.items.UnitInventory;
import com.solegendary.reignofnether.unit.HeroUnitSave;

import net.minecraft.core.NonNullList;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * The "Hero fallen" alert (backlog 10, review round 1 on #508): the per-hero news rule and the packet's wiring, both
 * on the common side (HeroFallenNews, FallenHeroClientboundPacket.deliver), so the server-side test run reaches them;
 * the client only adds the warm-up gate, the line and the chord.
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class HeroFallenAlertGameTests {

    static final String ARENA = SkirmishGameTests.ARENA;

    /**
     * Two heroes falling 1 s apart are both news - the alert-wide 5 s cooldown silenced the second, line and chord.
     * The same hero's packet again within HeroFallenNews.SAME_HERO_MS is one piece of news; another player's hero none.
     */
    @GameTest(template = ARENA, timeoutTicks = 20)
    public static void two_heroes_falling_one_second_apart_are_both_news(GameTestHelper helper) {
        HeroFallenNews news = new HeroFallenNews();
        List<String> problems = new ArrayList<>();
        long t0 = 1_000_000L;
        if (!news.admit("gt_hero_owner", "gt_hero_owner", "hero-a", t0))
            problems.add("the first hero's fall was not news");
        if (!news.admit("gt_hero_owner", "gt_hero_owner", "hero-b", t0 + 1_000))
            problems.add("a second hero falling 1 s after the first was not news");
        if (news.admit("gt_hero_owner", "gt_hero_owner", "hero-a", t0 + 2_000))
            problems.add("the same hero's packet again within " + HeroFallenNews.SAME_HERO_MS + " ms was news twice");
        if (!news.admit("gt_hero_owner", "gt_hero_owner", "hero-a", t0 + 1_000 + HeroFallenNews.SAME_HERO_MS))
            problems.add("the same hero falling again after " + HeroFallenNews.SAME_HERO_MS + " ms was not news");
        if (news.admit("gt_hero_owner", "gt_someone_else", "hero-c", t0 + 20_000))
            problems.add("another player's hero was news");
        if (problems.isEmpty())
            helper.succeed();
        else
            helper.fail(String.join("; ", problems));
    }

    /**
     * The packet's client delivery reaches both receivers: the fallen-heroes roster (revival at the lab) and the alert,
     * with the hero's owner, entity id and uuid. Review round 1: the alert's call could be deleted with every test green.
     */
    @GameTest(template = ARENA, timeoutTicks = 20)
    public static void a_fallen_hero_packet_reaches_the_roster_and_the_alert(GameTestHelper helper) {
        NonNullList<ItemStack> items = NonNullList.withSize(UnitInventory.MAX_INVENTORY_SIZE, ItemStack.EMPTY);
        HeroUnitSave save = new HeroUnitSave("gt-hero-uuid", "entity.reignofnether.dawn_paladin_unit", "gt_hero_owner",
            350, 1, 0, 1, 0, 0, 0, items);
        FallenHeroClientboundPacket packet = new FallenHeroClientboundPacket(save);
        List<HeroUnitSave> roster = new ArrayList<>();
        List<String> alerts = new ArrayList<>();
        packet.deliver(roster::add, (owner, hero, uuid) -> alerts.add(owner + "|" + hero + "|" + uuid));
        List<String> problems = new ArrayList<>();
        if (roster.size() != 1)
            problems.add("the roster got " + roster.size() + " heroes, expected 1");
        else if (!"gt-hero-uuid".equals(roster.get(0).uuid) || roster.get(0).experience != 350)
            problems.add("the roster got the wrong hero (" + roster.get(0).uuid + ", xp " + roster.get(0).experience + ")");
        if (!alerts.equals(List.of("gt_hero_owner|entity.reignofnether.dawn_paladin_unit|gt-hero-uuid")))
            problems.add("the alert got " + alerts + ", expected one fall of gt_hero_owner's dawn paladin gt-hero-uuid");
        if (problems.isEmpty())
            helper.succeed();
        else
            helper.fail(String.join("; ", problems));
    }
}
