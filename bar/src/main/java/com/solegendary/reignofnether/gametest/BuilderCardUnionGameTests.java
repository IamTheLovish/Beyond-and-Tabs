package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.hud.BuilderCardUnion;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * The mixed-builder command card (BuilderCardUnion; tester: "multiple builders including different types ... it only
 * sends one"): several builder types selected = their build menus together, hudSelectedEntity's first, no building
 * twice, an added button whose key the card already uses shown without a key, one type unchanged. Pure rule, no world.
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class BuilderCardUnionGameTests {

    /** A card button: building id (null = an ability) and key (NO_KEY = none). */
    record B(String id, int key) { }

    static final int NO = BuilderCardUnion.NO_KEY;

    static List<BuilderCardUnion.Added<B>> added(List<B> primary, List<List<B>> others, Set<Integer> reserved) {
        return BuilderCardUnion.added(primary, others, B::id, B::key, reserved);
    }

    static List<String> ids(List<BuilderCardUnion.Added<B>> added) {
        List<String> out = new ArrayList<>();
        for (BuilderCardUnion.Added<B> a : added)
            out.add(a.item().id());
        return out;
    }

    static void expect(GameTestHelper helper, Object want, Object got, String what) {
        if (want.equals(got))
            helper.succeed();
        else
            helper.fail(what + ": want " + want + ", got " + got);
    }

    @GameTest(template = SkirmishGameTests.ARENA, timeoutTicks = 20)
    public static void builder_card_union_two_types_give_the_union_in_order(GameTestHelper helper) {
        List<B> worker = List.of(new B(null, 81), new B("house", 1), new B("farm", 2));
        List<B> architect = List.of(new B("castle", 10), new B("house", 1), new B("tower", 11));
        List<B> engineer = List.of(new B("wall", 20), new B("castle", 10));
        expect(helper, List.of("castle", "tower", "wall"),
            ids(added(worker, List.of(architect, engineer), Set.of())), "added buildings in selection order");
    }

    @GameTest(template = SkirmishGameTests.ARENA, timeoutTicks = 20)
    public static void builder_card_union_never_adds_a_building_twice(GameTestHelper helper) {
        List<B> worker = List.of(new B("house", 1), new B("farm", 2));
        List<B> a = List.of(new B("farm", NO), new B("mill", NO), new B("mill", NO));
        List<B> b = List.of(new B("mill", 5), new B("house", 1));
        expect(helper, List.of("mill"), ids(added(worker, List.of(a, b), Set.of())), "deduplicated buildings");
    }

    @GameTest(template = SkirmishGameTests.ARENA, timeoutTicks = 20)
    public static void builder_card_union_a_clashing_key_leaves_the_added_button_keyless(GameTestHelper helper) {
        List<B> worker = List.of(new B(null, 81), new B("house", 1));
        // castle clashes with the worker's house (1), tower with an order key (90), wall keeps 7, gate then clashes
        // with the wall added before it, mill had no key
        List<B> other = List.of(new B("castle", 1), new B("tower", 90), new B("wall", 7), new B("gate", 7),
            new B("mill", NO), new B("forge", 81));
        List<BuilderCardUnion.Added<B>> got = added(worker, List.of(other), Set.of(90));
        List<String> keeps = new ArrayList<>();
        for (BuilderCardUnion.Added<B> a : got)
            keeps.add(a.item().id() + "=" + a.keepsKey());
        expect(helper, List.of("castle=false", "tower=false", "wall=true", "gate=false", "mill=false", "forge=false"),
            keeps, "who keeps its key");
    }

    @GameTest(template = SkirmishGameTests.ARENA, timeoutTicks = 20)
    public static void builder_card_union_a_single_type_is_unchanged(GameTestHelper helper) {
        List<B> worker = List.of(new B(null, 81), new B("house", 1), new B("farm", 2));
        expect(helper, List.of(), ids(added(worker, List.of(), Set.of())), "nothing added for one builder type");
    }
}
