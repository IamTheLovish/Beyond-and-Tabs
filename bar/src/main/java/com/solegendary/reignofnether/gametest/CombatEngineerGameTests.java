package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.ability.Ability;
import com.solegendary.reignofnether.bot.BotCombatEngineers;
import com.solegendary.reignofnether.bot.BotPlayer;
import com.solegendary.reignofnether.building.Building;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.BuildingServerEvents;
import com.solegendary.reignofnether.building.BuildingUtils;
import com.solegendary.reignofnether.building.Buildings;
import com.solegendary.reignofnether.building.buildings.defence.Defences;
import com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement;
import com.solegendary.reignofnether.building.production.ProductionBuilding;
import com.solegendary.reignofnether.building.production.ProductionItem;
import com.solegendary.reignofnether.building.production.ProductionItems;
import com.solegendary.reignofnether.faction.Faction;
import com.solegendary.reignofnether.faction.Factions;
import com.solegendary.reignofnether.keybinds.Keybindings;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.research.ResearchServerEvents;
import com.solegendary.reignofnether.resources.EconomyPace;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.resources.ResourceCosts;
import com.solegendary.reignofnether.resources.Resources;
import com.solegendary.reignofnether.resources.ResourcesServerEvents;
import com.solegendary.reignofnether.resources.WreckServerEvents;
import com.solegendary.reignofnether.unit.CombatEngineers;
import com.solegendary.reignofnether.unit.UnitGrid;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.interfaces.CombatEngineer;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.interfaces.WorkerUnit;
import com.solegendary.reignofnether.unit.units.engineers.BonesetterUnit;
import com.solegendary.reignofnether.unit.units.engineers.CombatEngineerProd;
import com.solegendary.reignofnether.unit.units.engineers.FieldMasonUnit;
import com.solegendary.reignofnether.unit.units.engineers.JadeMenderUnit;
import com.solegendary.reignofnether.unit.units.engineers.PioneerUnit;
import com.solegendary.reignofnether.unit.units.engineers.RootwardenUnit;
import com.solegendary.reignofnether.unit.units.engineers.SalvagerUnit;
import com.solegendary.reignofnether.unit.units.engineers.WreckerUnit;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Headless tests of the seven combat engineers (design/combat_engineers.md): each trains at its faction's T2 lab (and
 * only with Tier 2), reclaims wrecks at its build power, field-repairs a hurt friend and never a foe or itself, may start
 * field defences only (and assist anything), and really builds one. The bots' engineer rules
 * (bot/BotCombatEngineers) are checked on their own pieces.
 * <p>
 * Their own holder next to SkirmishGameTests (same arena template, same openGround helper): unique owners per test,
 * nothing counted globally, placements built with getNewBuildingPlacement and never registered (one real placement,
 * removed again), every unit, wreck, pool and research cleaned up in a finally.
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class CombatEngineerGameTests {

    static final String ARENA = SkirmishGameTests.ARENA;

    /** One faction's engineer: its lab, item, entity type, cost and health. */
    record Kit(Faction faction, Building lab, ProductionItem item, EntityType<? extends Mob> type, ResourceCost cost,
               float maxHealth) { }

    static List<Kit> kits() {
        return List.of(
            new Kit(Factions.VILLAGERS, Buildings.ARCANE_TOWER, ProductionItems.FIELD_MASON, EntityRegistrar.FIELD_MASON_UNIT.get(),
                ResourceCosts.FIELD_MASON, FieldMasonUnit.maxHealth),
            new Kit(Factions.MONSTERS, Buildings.DUNGEON, ProductionItems.BONESETTER, EntityRegistrar.BONESETTER_UNIT.get(),
                ResourceCosts.BONESETTER, BonesetterUnit.maxHealth),
            new Kit(Factions.PIGLINS, Buildings.FLAME_SANCTUARY, ProductionItems.WRECKER, EntityRegistrar.WRECKER_UNIT.get(),
                ResourceCosts.WRECKER, WreckerUnit.maxHealth),
            new Kit(Factions.VERDANT_COURT, Buildings.CIRCLE_OF_ELDERS, ProductionItems.ROOTWARDEN, EntityRegistrar.ROOTWARDEN_UNIT.get(),
                ResourceCosts.ROOTWARDEN, RootwardenUnit.maxHealth),
            new Kit(Factions.TIDEWROUGHT, Buildings.DRYDOCK, ProductionItems.SALVAGER, EntityRegistrar.SALVAGER_UNIT.get(),
                ResourceCosts.SALVAGER, SalvagerUnit.maxHealth),
            new Kit(Factions.STORMCALLERS, Buildings.CELESTIAL_OBSERVATORY, ProductionItems.JADE_MENDER, EntityRegistrar.JADE_MENDER_UNIT.get(),
                ResourceCosts.JADE_MENDER, JadeMenderUnit.maxHealth),
            new Kit(Factions.BRASSWORKS, Buildings.ASSEMBLY_WORKS, ProductionItems.PIONEER, EntityRegistrar.PIONEER_UNIT.get(),
                ResourceCosts.PIONEER, PioneerUnit.maxHealth));
    }

    static String owner(GameTestHelper helper, String tag) {
        return "gametest_eng_" + tag + "_" + helper.absolutePos(BlockPos.ZERO).asLong();
    }

    /** A unit of {@code type} owned by {@code owner}, feet at the cell {@code at}, added to the level; null if it can't be made. */
    static Mob spawn(ServerLevel level, EntityType<? extends Mob> type, String owner, BlockPos at, List<Entity> made) {
        Mob m = type.create(level);
        if (m == null)
            return null;
        m.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
        if (m instanceof Unit u)
            u.setOwnerName(owner);
        level.addFreshEntity(m);
        made.add(m);
        return m;
    }

    /** A resource pool for a test owner (energy pays a Mending Rite; metal is what reclaim and Tear Down pay into). */
    static Resources pool(String owner, int energy, List<Resources> pools) {
        Resources r = new Resources(owner, 0, energy, 0);
        ResourcesServerEvents.resourcesList.add(r);
        pools.add(r);
        return r;
    }

    static void cleanUp(List<Entity> made, List<Resources> pools, List<String> owners) {
        for (Entity e : made)
            if (!e.isRemoved())
                e.discard();
        ResourcesServerEvents.resourcesList.removeAll(pools);
        for (String o : owners)
            ResearchServerEvents.removeAllResearchFor(o);
    }

    static void finish(GameTestHelper helper, List<String> problems) {
        if (problems.isEmpty())
            helper.succeed();
        else
            helper.fail(String.join("; ", problems));
    }

    /** The unit of {@code type} owned by {@code owner} that a placement just produced: near it, else anywhere. */
    static Mob findProduced(ServerLevel level, BuildingPlacement pp, EntityType<?> type, String owner) {
        AABB box = new AABB(pp.minCorner, pp.maxCorner.offset(1, 1, 1)).inflate(24);
        for (Mob m : level.getEntitiesOfClass(Mob.class, box))
            if (m.getType() == type && m instanceof Unit u && owner.equals(u.getOwnerName()))
                return m;
        // the box only sees entity-loaded chunks; the unit list sees every joined unit
        for (LivingEntity le : UnitServerEvents.getAllUnits())
            if (le instanceof Mob m && m.getType() == type && m instanceof Unit u && owner.equals(u.getOwnerName()))
                return m;
        return null;
    }

    /** A built placement of {@code b} for {@code owner} with its blocks stamped into the world (health is real). */
    static BuildingPlacement stampedBuilt(ServerLevel level, Building b, BlockPos origin, String owner) {
        BuildingPlacement bp = BuildingUtils.getNewBuildingPlacement(b, level, origin, Rotation.NONE, owner, false);
        DefenceGameTests.stamp(level, bp);
        bp.initPlacedBlocks();
        bp.isBuilt = true;
        return bp;
    }

    // ---------------------------------------------------------------------------------------------------------------
    // registration and training
    // ---------------------------------------------------------------------------------------------------------------

    /**
     * Each faction's engineer is trained at its T2 lab (the bot kit's T2 lab too): its item is a CombatEngineerProd, names
     * an entity registered to the faction, is refused on the server without Tier 2 and queued with it, and completing it
     * (the real onComplete -> produceUnit path) makes an owned combat engineer worker with 1.5 build power, the cost
     * ResourceCosts gives it, its health, and its faction ability on V. The bots train the same item. Every lab sits at
     * the same spot in this arena and is never registered; each engineer is removed before the next.
     */
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void combat_engineers_train_at_their_t2_labs(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        List<String> problems = new ArrayList<>(), owners = new ArrayList<>();
        List<Entity> made = new ArrayList<>();
        List<Resources> pools = new ArrayList<>();
        int i = 0;
        try {
            for (Kit kit : kits()) {
                i++;
                String tag = kit.item().getItemName();
                String owner = owner(helper, "train" + i);
                owners.add(owner);
                if (!(kit.lab() instanceof ProductionBuilding lab) || !lab.productions.get().contains(kit.item())) {
                    problems.add(tag + ": " + kit.lab().name + " does not train it");
                    continue;
                }
                if (!kit.faction().equals(kit.lab().getFaction()))
                    problems.add(tag + ": " + kit.lab().name + " is not a " + kit.faction().getName() + " building");
                if (!(kit.item() instanceof CombatEngineerProd))
                    problems.add(tag + ": its item skips the shared Tier 2 gate (not a CombatEngineerProd)");
                if (!kit.faction().key.equals(Factions.ENTITY_FACTION.get(EntityType.getKey(kit.type()))))
                    problems.add(tag + ": its entity is not registered to " + kit.faction().getName());
                if (EntityRegistrar.getEntityType(kit.item().getItemName()) != kit.type())
                    problems.add(tag + ": its item name does not resolve to its entity type");
                if (BotCombatEngineers.engineerFor(kit.faction()) != kit.item())
                    problems.add(tag + ": the " + kit.faction().getName() + " bots would train " + BotCombatEngineers.engineerFor(kit.faction()));
                BotPlayer.Kit botKit = BotPlayer.kitFor(kit.faction());
                if (botKit == null || botKit.t2Lab() != kit.lab())
                    problems.add(tag + ": the bot's T2 lab is not " + kit.lab().name);
                BlockPos corner = helper.absolutePos(BlockPos.ZERO);
                BuildingPlacement bp = BuildingUtils.getNewBuildingPlacement(kit.lab(), level,
                    new BlockPos(corner.getX() + 2, corner.getY() + 1, corner.getZ() + 3), Rotation.NONE, owner, false);
                if (!(bp instanceof ProductionPlacement pp)) {
                    problems.add(tag + ": " + kit.lab().name + " is not a production placement");
                    continue;
                }
                pp.isBuilt = true;
                pool(owner, 0, pools);
                ResearchServerEvents.removeAllResearchFor(owner);
                if (kit.item().canProduce(pp))
                    problems.add(tag + ": can be trained without Tier 2");
                if (pp.startProductionItem(kit.item()) || !pp.productionQueue.isEmpty())
                    problems.add(tag + ": the server queued it without Tier 2");
                pp.productionQueue.clear();
                ResearchServerEvents.addResearch(owner, ProductionItems.RESEARCH_TIER_2);
                if (!pp.startProductionItem(kit.item()))
                    problems.add(tag + ": refused with Tier 2: " + kit.item().getProduceErrorMsg(pp));
                pp.productionQueue.clear();
                kit.item().onComplete.accept(level, pp);   // the real completion path: onComplete -> produceUnit
                Mob unit = findProduced(level, pp, kit.type(), owner);
                if (unit == null) {
                    problems.add(tag + ": completing the item produced no unit");
                    continue;
                }
                made.add(unit);
                if (!(unit instanceof CombatEngineer ce) || !(unit instanceof WorkerUnit w) || !(unit instanceof Unit u)) {
                    problems.add(tag + ": not a combat engineer worker");
                    continue;
                }
                if (Math.abs(w.getBuildPower() - CombatEngineers.BUILD_POWER) > 0.001f)
                    problems.add(tag + ": build power " + w.getBuildPower() + ", not " + CombatEngineers.BUILD_POWER);
                ResourceCost c = u.getCost();
                if (c == null || c.metal() != kit.cost().metal() || c.energy() != kit.cost().energy() || c.population != kit.cost().population)
                    problems.add(tag + ": its cost is not its ResourceCosts entry");
                if (Math.abs(unit.getMaxHealth() - kit.maxHealth()) > 0.5f)
                    problems.add(tag + ": max health " + unit.getMaxHealth() + ", expected " + kit.maxHealth());
                unit.discard();
            }
            finish(helper, problems);
        } finally {
            cleanUp(made, pools, owners);
        }
    }

    /**
     * AC-1 (builder abilities removed, 2026-10-26): no builder or combat engineer in any faction has an ability of its
     * own - only its building / placement buttons (which are not abilities). Checked on the seven combat engineers and on
     * every worker, constructor or builder-adjacent unit that used to carry one (Villager, Zombie Villager, Bonewright,
     * Embalmer, Seedshaper, Elder Druid, Shipwright, Harbourmaster, Geomancer, Ley Master, Tinker).
     */
    @GameTest(template = ARENA)
    public static void ac1_builders_and_engineers_have_no_abilities(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        List<EntityType<? extends Mob>> types = new ArrayList<>();
        for (Kit kit : kits())
            types.add(kit.type());
        types.add(EntityRegistrar.VILLAGER_UNIT.get());
        types.add(EntityRegistrar.ZOMBIE_VILLAGER_UNIT.get());
        types.add(EntityRegistrar.BONEWRIGHT_UNIT.get());
        types.add(EntityRegistrar.EMBALMER_UNIT.get());
        types.add(EntityRegistrar.SEEDSHAPER_UNIT.get());
        types.add(EntityRegistrar.ELDER_DRUID_UNIT.get());
        types.add(EntityRegistrar.SHIPWRIGHT_UNIT.get());
        types.add(EntityRegistrar.HARBOURMASTER_UNIT.get());
        types.add(EntityRegistrar.GEOMANCER_UNIT.get());
        types.add(EntityRegistrar.LEY_MASTER_UNIT.get());
        types.add(EntityRegistrar.TINKER_UNIT.get());
        List<String> problems = new ArrayList<>();
        for (EntityType<? extends Mob> type : types) {
            Mob m = type.create(level);
            if (!(m instanceof Unit u)) {
                problems.add(type + " could not be created as a unit");
                continue;
            }
            var abilities = u.getAbilities();
            if (abilities != null && !abilities.get().isEmpty())
                problems.add(type + " still has abilities: " + abilities.get().stream().map(a -> a.getClass().getSimpleName()).toList());
            m.discard();
        }
        finish(helper, problems);
    }

    /**
     * What the client needs ships in the mod file: each engineer's HD skin and its team texture (tools/gen_hd_skins.py,
     * drawn by TeamColourLayer), its portrait (textures/mobheads, the selection / queue / control-group icon), its name
     * and four tooltip lines, and every HUD message the engineers' gate and abilities can show. A typo would otherwise
     * only show in game, as a missing texture or a raw lang key.
     */
    @GameTest(template = ARENA)
    public static void combat_engineer_skins_portraits_and_lang_ship_in_the_mod(GameTestHelper helper) {
        List<String> missing = new ArrayList<>();
        var lang = SkirmishGameTests.enUsLang();
        for (String id : List.of("field_mason", "bonesetter", "wrecker", "rootwarden", "salvager", "jade_mender", "pioneer")) {
            for (String file : List.of(id + "_unit.png", id + "_unit_team.png"))
                if (!SkirmishGameTests.modFileHas("assets", ReignOfNether.MOD_ID, "textures", "entities", file))
                    missing.add("textures/entities/" + file);
            if (!SkirmishGameTests.modFileHas("assets", ReignOfNether.MOD_ID, "textures", "mobheads", id + ".png"))
                missing.add("textures/mobheads/" + id + ".png");
            String key = "entity.reignofnether." + id + "_unit";
            for (String k : List.of(key, key + ".tooltip1", key + ".tooltip2", key + ".tooltip3", key + ".tooltip4"))
                if (!lang.has(k))
                    missing.add("lang " + k);
        }
        for (String k : List.of(CombatEngineers.MSG_FIELD_DEFENCES_ONLY, CombatEngineerProd.NEEDS_TIER_2))
            if (!lang.has(k))
                missing.add("lang " + k);
        if (!missing.isEmpty())
            helper.fail("missing from the mod file: " + missing);
        helper.succeed();
    }

    // ---------------------------------------------------------------------------------------------------------------
    // the shared worker rules: reclaim, field repair, what they may build
    // ---------------------------------------------------------------------------------------------------------------

    /**
     * Each engineer standing at a wreck strips it at its build power (1.5 x a worker's 5 metal a second, times its
     * faction's reclaim trait and the match pace) into its owner's pool: one reclaim step of 2 s, measured around the
     * call. Seven engineers in a row, each a block from its own wreck (the nearest one is the one a worker strips).
     */
    @GameTest(template = ARENA, timeoutTicks = 60)
    public static void combat_engineers_reclaim_wrecks_at_their_build_power(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        SkirmishGameTests.openGround(helper, 0, 0, 15, 15);
        List<String> problems = new ArrayList<>(), owners = new ArrayList<>();
        List<Entity> made = new ArrayList<>();
        List<Resources> pools = new ArrayList<>();
        List<Kit> kits = kits();
        List<Mob> engineers = new ArrayList<>();
        List<Entity> wrecks = new ArrayList<>();
        List<Resources> theirPools = new ArrayList<>();
        for (int i = 0; i < kits.size(); i++) {
            Kit kit = kits.get(i);
            String owner = owner(helper, "reclaim" + i);
            owners.add(owner);
            theirPools.add(pool(owner, 0, pools));
            Mob eng = spawn(level, kit.type(), owner, helper.absolutePos(new BlockPos(1 + 2 * i, 2, 4)), made);
            if (eng == null) {
                cleanUp(made, pools, owners);
                helper.fail("could not create the " + kit.item().getItemName());
                return;
            }
            engineers.add(eng);
        }
        // a few ticks on, once the engineers are in the unit list reclaim walks; the wrecks are made in the same tick
        // as the step, so the wreck cap (oldest go first when a busy test server has many) can't take one meanwhile
        helper.runAfterDelay(5, () -> {
            try {
                for (int i = 0; i < kits.size(); i++) {
                    BlockPos w = helper.absolutePos(new BlockPos(1 + 2 * i, 2, 5));
                    Entity wreck = WreckServerEvents.spawnWreck(level, w.getX() + 0.5, w.getY(), w.getZ() + 0.5, 200f, kits.get(i).faction());
                    if (wreck == null) {
                        helper.fail("could not create the wreck for " + kits.get(i).item().getItemName());
                        return;
                    }
                    made.add(wreck);
                    wrecks.add(wreck);
                }
                float[] before = new float[wrecks.size()], poolBefore = new float[wrecks.size()];
                for (int i = 0; i < wrecks.size(); i++) {
                    before[i] = WreckServerEvents.metalOf(wrecks.get(i));
                    poolBefore[i] = theirPools.get(i).getMetal();
                }
                WreckServerEvents.tickReclaim(level, 2f);
                for (int i = 0; i < wrecks.size(); i++) {
                    Mob eng = engineers.get(i);
                    String tag = kits.get(i).item().getItemName();
                    Entity wreck = wrecks.get(i);
                    float after = wreck.isRemoved() ? 0 : WreckServerEvents.metalOf(wreck);
                    float drop = before[i] - after, gain = theirPools.get(i).getMetal() - poolBefore[i];
                    float expect = WreckServerEvents.RECLAIM_PER_POWER * ((WorkerUnit) eng).getBuildPower()
                        * WreckServerEvents.factionReclaim((Unit) eng)
                        * EconomyPace.of(((Unit) eng).getOwnerName()) * 2f;
                    if (Math.abs(drop - expect) > 0.05f)
                        problems.add(tag + ": stripped " + drop + " metal in 2 s, expected " + expect);
                    if (Math.abs(gain - drop) > 0.05f)
                        problems.add(tag + ": its pool got " + gain + " of the " + drop + " metal stripped");
                    float workerRate = WreckServerEvents.RECLAIM_PER_POWER * 1f * WreckServerEvents.factionReclaim((Unit) eng)
                        * EconomyPace.of(((Unit) eng).getOwnerName()) * 2f;
                    if (drop <= workerRate)
                        problems.add(tag + ": reclaims no faster than a 1-build-power worker (" + drop + " <= " + workerRate + ")");
                }
                finish(helper, problems);
            } finally {
                cleanUp(made, pools, owners);
            }
        });
    }

    /**
     * Field repair, through the engineer's own once-a-second step (tickFieldRepair, phase forced): an idle engineer mends
     * the hurt friendly unit in reach by 2 HP per build power (3 HP) - never a more hurt foe beside it, never itself.
     * Seven groups side by side; each has its own owners, so no group's engineer counts another group's units as friends.
     */
    @GameTest(template = ARENA, timeoutTicks = 60)
    public static void combat_engineers_field_repair_friends_not_foes_or_themselves(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        SkirmishGameTests.openGround(helper, 0, 0, 15, 15);
        List<String> problems = new ArrayList<>(), owners = new ArrayList<>();
        List<Entity> made = new ArrayList<>();
        List<Kit> kits = kits();
        List<Mob[]> groups = new ArrayList<>();
        var villager = EntityRegistrar.VILLAGER_UNIT.get();   // aggro 0: neither side starts a fight in the test
        for (int i = 0; i < kits.size(); i++) {
            String owner = owner(helper, "repair" + i), foeOwner = owner + "_foe";
            owners.add(owner);
            Mob eng = spawn(level, kits.get(i).type(), owner, helper.absolutePos(new BlockPos(1 + 2 * i, 2, 2)), made);
            Mob ally = spawn(level, villager, owner, helper.absolutePos(new BlockPos(1 + 2 * i, 2, 4)), made);
            Mob foe = spawn(level, villager, foeOwner, helper.absolutePos(new BlockPos(1 + 2 * i, 2, 3)), made);
            if (eng == null || ally == null || foe == null) {
                cleanUp(made, List.of(), owners);
                helper.fail("could not create the field repair units for " + kits.get(i).item().getItemName());
                return;
            }
            groups.add(new Mob[] { eng, ally, foe });
        }
        UnitGrid.invalidate();
        helper.runAfterDelay(5, () -> {
            try {
                UnitGrid.invalidate();
                for (int i = 0; i < groups.size(); i++) {
                    Mob eng = groups.get(i)[0], ally = groups.get(i)[1], foe = groups.get(i)[2];
                    String tag = kits.get(i).item().getItemName();
                    eng.setHealth(eng.getMaxHealth() - 20);      // the most hurt of all: still never its own patient
                    foe.setHealth(foe.getMaxHealth() - 15);
                    ally.setHealth(ally.getMaxHealth() - 10);
                    float e0 = eng.getHealth(), a0 = ally.getHealth(), f0 = foe.getHealth();
                    eng.tickCount = Math.floorMod(-eng.getId(), CombatEngineers.REPAIR_PERIOD_TICKS);   // its repair second
                    float healed = CombatEngineers.tickFieldRepair(eng);
                    float expect = Math.min(10, ((WorkerUnit) eng).getBuildPower() * CombatEngineers.REPAIR_HP_PER_POWER);
                    if (Math.abs(healed - expect) > 0.01f || Math.abs(ally.getHealth() - a0 - expect) > 0.01f)
                        problems.add(tag + ": mended " + healed + " (the friend +" + (ally.getHealth() - a0) + "), expected " + expect);
                    if (foe.getHealth() != f0)
                        problems.add(tag + ": mended a foe");
                    if (eng.getHealth() != e0)
                        problems.add(tag + ": mended itself");
                }
                finish(helper, problems);
            } finally {
                cleanUp(made, List.of(), owners);
            }
        });
    }

    /**
     * What an engineer may start: every field defence of its faction - wall, gate, both traps, the anti-raid tower - but
     * not the anti-T3 emplacement, an extractor, a wind generator, a lab or its own T2 lab (the server gate, and the real
     * placement path refuses them before any terrain check). With a worker among the builders anything goes (the worker
     * places, the engineer assists).
     */
    @GameTest(template = ARENA, timeoutTicks = 40)
    public static void combat_engineers_start_field_defences_only(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        SkirmishGameTests.openGround(helper, 0, 0, 15, 15);
        List<String> problems = new ArrayList<>(), owners = new ArrayList<>();
        List<Entity> made = new ArrayList<>();
        List<Kit> kits = kits();
        try {
            for (int i = 0; i < kits.size(); i++) {
                Kit kit = kits.get(i);
                String tag = kit.item().getItemName();
                String owner = owner(helper, "build" + i);
                owners.add(owner);
                Mob eng = spawn(level, kit.type(), owner, helper.absolutePos(new BlockPos(1 + 2 * i, 2, 2)), made);
                Mob worker = spawn(level, EntityRegistrar.VILLAGER_UNIT.get(), owner, helper.absolutePos(new BlockPos(1 + 2 * i, 2, 4)), made);
                if (eng == null || worker == null) {
                    helper.fail("could not create the builders for " + tag);
                    return;
                }
                int[] engOnly = { eng.getId() }, mixed = { eng.getId(), worker.getId() };
                Defences.Set set = Defences.of(kit.faction());
                if (set == null) {
                    problems.add(tag + ": " + kit.faction().getName() + " has no field defences");
                    continue;
                }
                for (Building b : set.all()) {
                    boolean want = b != set.antiT3();
                    if (CombatEngineers.mayStart(b) != want || CombatEngineers.buildersMayPlace(level, b, engOnly) != want)
                        problems.add(tag + ": " + b.name + (want ? " refused" : " allowed (the anti-T3 emplacement is not a field defence)"));
                }
                BotPlayer.Kit botKit = BotPlayer.kitFor(kit.faction());
                List<Building> forbidden = new ArrayList<>(List.of(kit.lab(), set.antiT3()));
                if (botKit != null)
                    for (Building b : new Building[] { botKit.extractor(), botKit.wind(), botKit.armyBuilding() })
                        if (b != null)
                            forbidden.add(b);
                for (Building b : forbidden) {
                    if (CombatEngineers.mayStart(b) || CombatEngineers.buildersMayPlace(level, b, engOnly))
                        problems.add(tag + ": may start " + b.name + " on its own");
                    if (!CombatEngineers.buildersMayPlace(level, b, mixed))
                        problems.add(tag + ": refused " + b.name + " with a worker among the builders (assist is free)");
                }
                if (botKit != null && botKit.armyBuilding() != null) {
                    var placed = BuildingServerEvents.placeBuilding(botKit.armyBuilding(), helper.absolutePos(new BlockPos(8, 1, 8)),
                        Rotation.NONE, owner, engOnly, false, false, false, true);
                    if (placed != null) {
                        BuildingServerEvents.getBuildings().remove(placed);
                        problems.add(tag + ": placeBuilding accepted " + botKit.armyBuilding().name + " from an engineer alone");
                    }
                }
            }
            finish(helper, problems);
        } finally {
            cleanUp(made, List.of(), owners);
        }
    }

    /** Air over a stone floor at {@code y0}, {@code r} blocks round (x, z), {@code h} high - or all air with {@code clear}. */
    static void skyPad(ServerLevel level, int x, int y0, int z, int r, int h, boolean clear) {
        for (int dx = -r; dx <= r; dx++)
            for (int dz = -r; dz <= r; dz++)
                for (int y = 0; y <= h; y++)
                    level.setBlock(new BlockPos(x + dx, y0 + y, z + dz),
                        (y == 0 && !clear ? net.minecraft.world.level.block.Blocks.STONE : net.minecraft.world.level.block.Blocks.AIR)
                            .defaultBlockState(), 2);
    }

    static final int PAD_Y = 200, PAD_STEP = 15, PAD_R = 5, PAD_H = 12;

    /**
     * Each engineer really builds a field defence: alone, it places its faction's anti-raid tower through the real
     * placement path (accepted, the engineer put on the site) and within a few seconds the site's flow construction has
     * advanced on its build power. Seven stone pads stacked high in this test's own column (the arena is too low for a
     * tower; the capitol tests build up there too). The only registered placements in this holder: removed again and
     * the pads cleared in the finally.
     */
    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void combat_engineers_build_their_anti_raid_towers(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos base = helper.absolutePos(new BlockPos(8, 1, 8));
        final int cx = base.getX(), cz = base.getZ();
        List<Kit> kits = kits();
        List<String> problems = new ArrayList<>(), owners = new ArrayList<>();
        List<Entity> made = new ArrayList<>();
        List<BuildingPlacement> placed = new ArrayList<>();
        Runnable undo = () -> {
            for (BuildingPlacement bp : placed) {
                BuildingServerEvents.getBuildings().remove(bp);
                bp.forceChunk(false);
            }
            cleanUp(made, List.of(), owners);
            for (int i = 0; i < kits.size(); i++)
                skyPad(level, cx, PAD_Y + PAD_STEP * i, cz, PAD_R, PAD_H, true);
        };
        List<String> names = new ArrayList<>();
        for (int i = 0; i < kits.size(); i++) {
            Kit kit = kits.get(i);
            int y0 = PAD_Y + PAD_STEP * i;
            skyPad(level, cx, y0, cz, PAD_R, PAD_H, false);
            String owner = owner(helper, "tower" + i);
            owners.add(owner);
            Mob eng = spawn(level, kit.type(), owner, new BlockPos(cx - 4, y0 + 1, cz), made);
            Defences.Set set = Defences.of(kit.faction());
            if (eng == null || set == null) {
                undo.run();
                helper.fail("could not create the " + kit.item().getItemName() + " or find its faction's defences");
                return;
            }
            BuildingPlacement bp = BuildingServerEvents.placeBuilding(set.raidTower(), new BlockPos(cx - 1, y0, cz - 1),
                Rotation.NONE, owner, new int[] { eng.getId() }, false, false, false, true);
            String tag = kit.item().getItemName() + " (" + set.raidTower().name + ")";
            if (bp == null) {
                problems.add(tag + ": placeBuilding refused the engineer's own anti-raid tower");
                continue;
            }
            placed.add(bp);
            names.add(tag);
            if (((WorkerUnit) eng).getBuildRepairGoal().getBuildingTarget() != bp)
                problems.add(tag + ": the engineer was not put on its own site");
        }
        // 4 s: long enough to walk up and start, far too short to finish (a finished tower would arm itself)
        helper.runAfterDelay(80, () -> {
            try {
                for (int i = 0; i < placed.size(); i++)
                    if (placed.get(i).buildProgress <= 0)
                        problems.add(names.get(i) + ": no progress in 4 s with the engineer on it");
                finish(helper, problems);
            } finally {
                undo.run();
            }
        });
    }

    // ---------------------------------------------------------------------------------------------------------------
    // the bots
    // ---------------------------------------------------------------------------------------------------------------

    /**
     * The bots' engineer rules (bot/BotCombatEngineers): engineers are taken out of the worker list (so the economy
     * never hands one an extractor); a medium bot with an army of six and Tier 2 queues one engineer at its T2 lab and no
     * second; a hard bot wants two; an easy bot, a bot without Tier 2 and a bot with a small army none. The T2 lab is a
     * placement that is never registered; the "army" is a list of six references (only its size is read).
     */
    @GameTest(template = ARENA, timeoutTicks = 40)
    public static void bots_pull_engineers_from_workers_and_train_a_few(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        List<String> problems = new ArrayList<>(), owners = new ArrayList<>();
        List<Entity> made = new ArrayList<>();
        List<Resources> pools = new ArrayList<>();
        BlockPos home = helper.absolutePos(new BlockPos(8, 2, 8));
        try {
            int i = 0;
            for (Kit kit : kits()) {
                i++;
                String tag = kit.item().getItemName();
                String name = owner(helper, "bot" + i);
                owners.add(name);
                pool(name, 0, pools);
                Mob eng = spawn(level, kit.type(), name, helper.absolutePos(new BlockPos(2, 2, 2)), made);
                Mob worker = spawn(level, EntityRegistrar.VILLAGER_UNIT.get(), name, helper.absolutePos(new BlockPos(4, 2, 2)), made);
                if (eng == null || worker == null) {
                    helper.fail("could not create the bot's units for " + tag);
                    return;
                }
                List<LivingEntity> workers = new ArrayList<>(List.of(worker, eng)), engineers = new ArrayList<>();
                BotCombatEngineers.takeEngineers(workers, engineers);
                if (workers.size() != 1 || workers.get(0) != worker || engineers.size() != 1 || engineers.get(0) != eng)
                    problems.add(tag + ": the engineer was not taken out of the worker list");
                BuildingPlacement bp = BuildingUtils.getNewBuildingPlacement(kit.lab(), level,
                    helper.absolutePos(new BlockPos(2, 1, 3)), Rotation.NONE, name, false);
                if (!(bp instanceof ProductionPlacement lab)) {
                    problems.add(tag + ": " + kit.lab().name + " is not a production placement");
                    continue;
                }
                lab.isBuilt = true;
                List<BuildingPlacement> buildings = List.of(lab);
                List<LivingEntity> army = Collections.nCopies(6, worker), small = Collections.nCopies(5, worker);
                BotPlayer medium = new BotPlayer(name, kit.faction(), BotPlayer.Difficulty.MEDIUM, home);
                BotPlayer hard = new BotPlayer(name, kit.faction(), BotPlayer.Difficulty.HARD, home);
                BotPlayer easy = new BotPlayer(name, kit.faction(), BotPlayer.Difficulty.EASY, home);
                ResearchServerEvents.removeAllResearchFor(name);
                BotCombatEngineers.train(medium, 0, army, buildings);
                if (!lab.productionQueue.isEmpty())
                    problems.add(tag + ": a bot without Tier 2 queued an engineer");
                ResearchServerEvents.addResearch(name, ProductionItems.RESEARCH_TIER_2);
                BotCombatEngineers.train(medium, 0, small, buildings);
                BotCombatEngineers.train(easy, 0, army, buildings);
                if (!lab.productionQueue.isEmpty())
                    problems.add(tag + ": an easy bot or one with five fighters queued an engineer");
                BotCombatEngineers.train(medium, 0, army, buildings);
                BotCombatEngineers.train(medium, 0, army, buildings);
                if (queued(lab, kit.item()) != 1)
                    problems.add(tag + ": a medium bot queued " + queued(lab, kit.item()) + " engineers, expected 1");
                BotCombatEngineers.train(hard, 0, army, buildings);
                BotCombatEngineers.train(hard, 0, army, buildings);
                if (queued(lab, kit.item()) != 2)
                    problems.add(tag + ": a hard bot ended with " + queued(lab, kit.item()) + " engineers queued, expected 2");
                lab.productionQueue.clear();
            }
            finish(helper, problems);
        } finally {
            cleanUp(made, pools, owners);
        }
    }

    static int queued(ProductionPlacement pp, ProductionItem item) {
        int n = 0;
        for (var ap : pp.productionQueue)
            if (ap.item == item)
                n++;
        return n;
    }
}
