package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.ability.abilities.RaiseDead;
import com.solegendary.reignofnether.api.ReignOfNetherRegistries;
import com.solegendary.reignofnether.building.Building;
import com.solegendary.reignofnether.building.buildings.placements.GraveyardPlacement;
import com.solegendary.reignofnether.building.production.ProductionItems;
import com.solegendary.reignofnether.faction.Factions;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.resources.ResourceCosts;
import com.solegendary.reignofnether.resources.Resources;
import com.solegendary.reignofnether.resources.ResourcesServerEvents;
import com.solegendary.reignofnether.resources.WreckServerEvents;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.units.monsters.ZombieUnit;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * lovish, Oct 29: "there should be no population in this game" - "like no max population". Production is never held
 * back by population: there is no cap to set (the old gamerule {@code maxPopulation} is gone), and a cap set the old
 * way (an old save's stored value, or {@code /gamerule maxPopulation}) stops nothing. Units still have a population
 * cost: it is only counted (the HUD shows the count). Written before the code.
 *
 * The one limit that stays is a summon's own: at most 8 Lich Regent ghouls alive per player (lovish, Oct 29).
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class NoMaxPopulationGameTests {

    static final String ARENA = SkirmishGameTests.ARENA;

    /** Whether a gamerule of this name is registered at all. */
    private static boolean gameruleExists(String name) {
        boolean[] found = {false};
        GameRules.visitGameRuleTypes(new GameRules.GameRuleTypeVisitor() {
            @Override
            public <T extends GameRules.Value<T>> void visit(GameRules.Key<T> key, GameRules.Type<T> type) {
                if (key.getId().equals(name))
                    found[0] = true;
            }
        });
        return found[0];
    }

    /** The stored value of a gamerule by name, or null when there is no such rule. */
    private static String gameruleValue(ServerLevel level, String name) {
        String[] value = {null};
        GameRules.visitGameRuleTypes(new GameRules.GameRuleTypeVisitor() {
            @Override
            public <T extends GameRules.Value<T>> void visit(GameRules.Key<T> key, GameRules.Type<T> type) {
                if (key.getId().equals(name))
                    value[0] = level.getGameRules().getRule(key).serialize();
            }
        });
        return value[0];
    }

    /** A command as the server runs it (permission 4, no chat output); a rule that does not exist simply fails. */
    private static void command(ServerLevel level, String cmd) {
        MinecraftServer server = level.getServer();
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), cmd);
    }

    /**
     * Production goes past any old cap while resources are there. The cap is set the way a host or an old save did,
     * {@code /gamerule maxPopulation} at the owner's current population (no room at all under the old rule); then a
     * plain graveyard (an ordinary lab) and an overflowing Level 2 one each take all twelve Zombies, and every one walks
     * out - none refused, none banked as a skull. Runs in one tick (the queue is ticked by hand) and puts the rule
     * back whatever happens, so nothing leaks into another test.
     */
    @GameTest(template = ARENA)
    public static void no_max_population_production_goes_past_an_old_cap(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        List<String> problems = new ArrayList<>();
        if (gameruleExists("maxPopulation"))
            problems.add("the gamerule maxPopulation still exists (lovish: no max population)");
        String before = gameruleValue(level, "maxPopulation");
        try {
            for (int code : new int[] {0, 2}) {
                String owner = "gametest_nomaxpop_" + code + "_" + helper.absolutePos(BlockPos.ZERO).asLong();
                Resources pool = new Resources(owner, 0, 100000, 100000);
                ResourcesServerEvents.resourcesList.add(pool);
                GraveyardPlacement gy = null;
                List<ZombieUnit> raised = new ArrayList<>();
                try {
                    gy = GraveyardGameTests.stampGraveyard(helper, owner, code);
                    if (gy == null)
                        return;
                    command(level, "gamerule maxPopulation " + UnitServerEvents.getCurrentPopulation(owner));
                    int accepted = 0;
                    for (int i = 0; i < GraveyardGameTests.QUEUED; i++)
                        if (gy.startProductionItem(ProductionItems.ZOMBIE))
                            accepted++;
                    int ticks = GraveyardGameTests.runQueue(gy, 40000);
                    raised = GraveyardGameTests.zombiesOf(level, gy, owner);
                    String where = (code == 0 ? "plain graveyard" : "Level 2 graveyard") + ": ";
                    if (accepted != GraveyardGameTests.QUEUED)
                        problems.add(where + "accepted " + accepted + " of " + GraveyardGameTests.QUEUED
                            + " Zombies past the old cap, with resources");
                    if (!gy.productionQueue.isEmpty())
                        problems.add(where + "the queue stalled with " + gy.productionQueue.size() + " left after "
                            + ticks + " ticks");
                    if (raised.size() != accepted)
                        problems.add(where + raised.size() + " Zombies walked out of " + accepted + " accepted");
                    if (gy.getTotalSkulls() != 0)
                        problems.add(where + gy.getTotalSkulls() + " Zombies were banked as skulls instead of walking out");
                } finally {
                    if (before != null)
                        command(level, "gamerule maxPopulation " + before);
                    for (ZombieUnit z : raised)
                        z.discard();
                    for (ZombieUnit z : GraveyardGameTests.zombiesOf(level, gy, owner))
                        z.discard();
                    GraveyardGameTests.clear(level, gy);
                    ResourcesServerEvents.resourcesList.remove(pool);
                }
            }
        } finally {
            if (before != null)
                command(level, "gamerule maxPopulation " + before);
        }
        if (problems.isEmpty())
            helper.succeed();
        else
            helper.fail(String.join("; ", problems));
    }

    /**
     * lovish, Oct 29: "beyond all reason doesnt use supply or unit cap". No building provides population any more -
     * capitols, portals, the Sculk Catalyst and the old houses keep their other roles (energy, production) and lose
     * only the supply line. Every registered building of every faction, as costed from the config.
     */
    @GameTest(template = ARENA)
    public static void no_max_population_no_building_provides_population(GameTestHelper helper) {
        List<String> supplying = new ArrayList<>();
        int seen = 0;
        for (Building b : ReignOfNetherRegistries.BUILDING) {
            seen++;
            if (b.cost != null && b.cost.population != 0)
                supplying.add(b.name + " (" + b.cost.population + ")");
        }
        if (seen == 0)
            helper.fail("setup: no buildings registered");
        else if (!supplying.isEmpty())
            helper.fail(supplying.size() + " buildings still provide population: " + String.join(", ", supplying));
        else
            helper.succeed();
    }

    /**
     * lovish, Oct 29: at most 8 Lich Regent ghouls alive per player (no lifetime) - with population no limit at all,
     * this is their only one, and bots cast Raise Dead on cooldown. Ten wrecks of one ghoul's metal each round the
     * caster: casts raise 3, 3, 2 and then nothing; the refused cast drains no wreck, and the ability (the path the bot
     * takes too) sets no cooldown. One ghoul gone, the next cast raises exactly one. (Proven red first on PR #374.)
     */
    @GameTest(template = ARENA, timeoutTicks = 40)
    public static void raise_dead_ghouls_cap_at_8_per_player(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = "gametest_ghoulcap_" + helper.absolutePos(BlockPos.ZERO).asLong();
        Mob lich = HeroGameTests.spawnUnit(helper, EntityRegistrar.VINDICATOR_UNIT.get(), owner, 8, 8);
        if (lich == null) {
            helper.fail("could not spawn the caster");
            return;
        }
        lich.setNoAi(true);
        float each = ResourceCosts.ZOMBIE.metal();   // one ghoul empties one wreck, so no cast can come back to it
        List<Entity> wrecks = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            double a = i * Math.PI * 2 / 10;
            BlockPos w = helper.absolutePos(new BlockPos(8, 2, 8));
            Entity wreck = WreckServerEvents.spawnWreck(level, w.getX() + 0.5 + Math.cos(a) * 5, w.getY(),
                w.getZ() + 0.5 + Math.sin(a) * 5, each, Factions.MONSTERS);
            if (wreck != null)
                wrecks.add(wreck);
        }
        helper.runAfterDelay(2, () -> {
            List<String> problems = new ArrayList<>();
            try {
                if (RaiseDead.MAX_GHOULS != 8)
                    problems.add("lovish set 8 ghouls per player, MAX_GHOULS is " + RaiseDead.MAX_GHOULS);
                if (wrecks.size() != 10)
                    problems.add("setup: " + wrecks.size() + " of 10 wrecks spawned");
                int[] casts = new int[4];
                for (int i = 0; i < casts.length; i++)
                    casts[i] = RaiseDead.raise(level, lich, owner);
                List<ZombieUnit> ghouls = new ArrayList<>();
                for (ZombieUnit z : level.getEntitiesOfClass(ZombieUnit.class, new AABB(lich.blockPosition()).inflate(12)))
                    if (z.isAlive() && owner.equals(z.getOwnerName()))
                        ghouls.add(z);
                int total = casts[0] + casts[1] + casts[2] + casts[3];
                if (total != 8 || casts[3] != 0)
                    problems.add("casts raised " + casts[0] + ", " + casts[1] + ", " + casts[2] + ", " + casts[3]
                        + " ghouls (" + ghouls.size() + " alive), expected 3, 3, 2, 0: at most 8 per player");
                if (RaiseDead.liveGhouls(owner) != ghouls.size())
                    problems.add("liveGhouls says " + RaiseDead.liveGhouls(owner) + ", " + ghouls.size() + " stand");
                int full = 0;
                for (Entity w : wrecks)
                    if (!w.isRemoved() && WreckServerEvents.metalOf(w) >= each - 0.01f)
                        full++;
                if (full != wrecks.size() - Math.min(total, wrecks.size()))
                    problems.add(full + " wrecks are untouched after " + total + " ghouls: a refused raise drained one");
                // the ability's own path (the bot casts it on cooldown): refused at the cap, no cooldown
                RaiseDead ability = new RaiseDead();
                ability.use(level, (Unit) lich, lich.blockPosition());
                if (((Unit) lich).getCooldown(ability) > 0)
                    problems.add("Raise Dead at the cap still set its cooldown (a refused cast)");
                // one gone: room for exactly one more
                if (!ghouls.isEmpty()) {
                    ghouls.get(0).discard();
                    int again = RaiseDead.raise(level, lich, owner);
                    if (again != 1)
                        problems.add("with one ghoul gone the next cast raised " + again + ", expected 1");
                }
            } finally {
                AABB box = new AABB(helper.absolutePos(new BlockPos(-4, -2, -4)), helper.absolutePos(new BlockPos(20, 20, 20)));
                for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box))
                    if (e instanceof Unit u && owner.equals(u.getOwnerName()))
                        e.discard();
                for (Entity w : wrecks)
                    w.discard();
            }
            if (problems.isEmpty())
                helper.succeed();
            else
                helper.fail(String.join("; ", problems));
        });
    }
}
