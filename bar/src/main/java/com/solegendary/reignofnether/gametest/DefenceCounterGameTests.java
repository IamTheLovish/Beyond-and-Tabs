package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.building.buildings.defence.DefenceTower;
import com.solegendary.reignofnether.building.production.ProductionItems;
import com.solegendary.reignofnether.faction.Faction;
import com.solegendary.reignofnether.faction.Factions;
import com.solegendary.reignofnether.unit.UnitTier;
import com.solegendary.reignofnether.unit.interfaces.AttackerUnit;
import com.solegendary.reignofnether.unit.interfaces.Unit;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Every defence has a counter at its own stage (lovish, Nov 5: "There needs to be counter play to what were
 * developing"; "if someone overcommits on defenses that can get punished by the other playing advancing in technology";
 * BAR's Sheldon, a T2 artillery that out-ranges the T1 gauntlet). design/defences_tiers.md holds the ladder.
 * <p>
 * Read off the code, not a hand list: every trainable unit of every live faction is built off-world and asked its
 * reach (getAttackRange), whether it may hit a building (canAttackBuildings), whether it flies, and its tier
 * (UnitTier.of). A unit counters a tower kind when it can hurt it from where the tower cannot answer: a ground unit
 * with more reach than the tower's range, or a flyer against a tower that never shoots air; an air-only tower is
 * answered by any ground unit that hits buildings. The counter must be of the tower's tier or below (a capitol tower:
 * T1; a T1-lab or Tier 2 tower: T2). The anti-T3 emplacement is out of scope (experimentals out-stat it by design).
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class DefenceCounterGameTests {

    static final String ARENA = SkirmishGameTests.ARENA;

    /** One trainable unit, as the audit sees it. */
    record Shooter(String id, int tier, float reach, boolean hitsBuildings, boolean flies) { }

    /** The highest unit tier a counter to this tower kind may need. */
    static int counterTier(DefenceTower.Kind kind) {
        return switch (kind.gate) {
            case CAPITOL -> 1;
            case T1_LAB, TIER_2 -> 2;
            case TIER_3 -> 3;
        };
    }

    /** Can this unit hurt a tower of this kind from where the tower cannot hurt it? */
    static boolean counters(Shooter s, DefenceTower.Kind kind) {
        if (!s.hitsBuildings() || s.tier() > counterTier(kind))
            return false;
        if (kind.airOnly)
            return !s.flies();
        if (s.flies())
            return !kind.hitsAir;
        return s.reach() > kind.range;
    }

    @GameTest(template = ARENA)
    public static void every_faction_can_outreach_each_of_its_t1_and_t2_towers(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Map<String, List<Shooter>> byFaction = new LinkedHashMap<>();
        List<String> problems = new ArrayList<>();
        for (EntityType<?> type : ForgeRegistries.ENTITY_TYPES) {
            ResourceLocation key = ForgeRegistries.ENTITY_TYPES.getKey(type);
            if (key == null || !ReignOfNether.MOD_ID.equals(key.getNamespace()))
                continue;
            Entity e = null;
            try {
                e = type.create(level);
                if (!(e instanceof Mob mob) || !(e instanceof Unit unit) || !(e instanceof AttackerUnit au))
                    continue;
                @SuppressWarnings("unchecked")
                var prod = ProductionItems.getProductionItem((EntityType<? extends Mob>) type);
                Faction faction = Factions.getFaction(unit);
                if (prod == null || !Factions.isLive(faction))
                    continue;
                byFaction.computeIfAbsent(faction.getName(), f -> new ArrayList<>()).add(new Shooter(key.getPath(),
                    UnitTier.of(mob), au.getAttackRange(), au.canAttackBuildings(), unit.isFlyingUnit()));
            } catch (Exception ex) {
                problems.add(key.getPath() + ": threw " + ex);
            } finally {
                if (e != null)
                    e.discard();
            }
        }
        if (byFaction.size() < 6)
            problems.add("setup: only " + byFaction.size() + " live factions with trainable units: " + byFaction.keySet());
        for (var entry : byFaction.entrySet()) {
            for (DefenceTower.Kind kind : DefenceTower.Kind.values()) {
                if (kind.gate == DefenceTower.Gate.TIER_3)
                    continue;
                List<String> answers = new ArrayList<>();
                float best = 0;
                for (Shooter s : entry.getValue()) {
                    if (counters(s, kind))
                        answers.add(s.id());
                    if (s.hitsBuildings() && !s.flies() && s.tier() <= counterTier(kind))
                        best = Math.max(best, s.reach());
                }
                ReignOfNether.LOGGER.info("[Counters] GameTest {} vs {} (range {}, T{} counters): {}", entry.getKey(), kind,
                    kind.range, counterTier(kind), answers.isEmpty() ? "NONE" : String.join(" ", answers));
                if (answers.isEmpty())
                    problems.add(entry.getKey() + " has no T" + counterTier(kind) + "-or-lower answer to " + kind + " (range "
                        + kind.range + (kind.hitsAir ? ", hits air" : ", ground only") + "): its longest building-hitting ground reach is "
                        + String.format(Locale.ROOT, "%.0f", best) + " and no flyer of that tier hits buildings");
            }
        }
        if (problems.isEmpty())
            helper.succeed();
        else
            helper.fail(problems.size() + " gap(s): " + String.join("; ", problems));
    }
}
