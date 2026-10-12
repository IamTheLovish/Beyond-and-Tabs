package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.ability.Ability;
import com.solegendary.reignofnether.ability.abilities.BoneGraft;
import com.solegendary.reignofnether.ability.abilities.Bulwark;
import com.solegendary.reignofnether.ability.abilities.MendingRite;
import com.solegendary.reignofnether.ability.abilities.Overdrive;
import com.solegendary.reignofnether.ability.abilities.Rootwalk;
import com.solegendary.reignofnether.ability.abilities.SalvageHook;
import com.solegendary.reignofnether.ability.abilities.TearDown;
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
 * field defences only (and assist anything), really builds one, and its faction ability does what it says - with its
 * counter (a Bulwark survives a Tear Down; a hurt Rootwarden or Jade Mender breaks its channel). The bots' engineer rules
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
                Ability a = ce.getEngineerAbility();
                if (a == null || u.getAbilities() == null || !u.getAbilities().get().contains(a)) {
                    problems.add(tag + ": its faction ability is not in its ability list");
                } else {
                    int idx = u.getAbilities().get().indexOf(a);
                    if (u.getAbilities().effectiveHotkeys().get(idx) != Keybindings.hotkey1)
                        problems.add(tag + ": its ability is not on V (hotkey1)");
                }
                unit.discard();
            }
            finish(helper, problems);
        } finally {
            cleanUp(made, pools, owners);
        }
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
        for (String k : List.of(CombatEngineers.MSG_FIELD_DEFENCES_ONLY, CombatEngineerProd.NEEDS_TIER_2, Bulwark.MSG_BAD_TARGET,
                Bulwark.MSG_TOO_FAR, BoneGraft.MSG_NO_WRECK, BoneGraft.MSG_NO_TARGET, TearDown.MSG_BAD_TARGET,
                Rootwalk.MSG_BAD_TARGET, Rootwalk.MSG_INTERRUPTED, SalvageHook.MSG_NO_WRECK, MendingRite.MSG_BAD_TARGET,
                MendingRite.MSG_BROKEN))
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
                        * com.solegendary.reignofnether.ability.abilities.SoulWisps.reclaimMultiplier(eng)
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

    /**
     * An engineer really builds a field defence: a Field Mason alone places the Sunforged anti-raid tower through the
     * real placement path (accepted, the Mason assigned to it) and within a few seconds the site's flow construction
     * has advanced on its build power. The one placement that is registered: removed again in the finally.
     */
    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void field_mason_builds_an_anti_raid_tower(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        SkirmishGameTests.openGround(helper, 0, 0, 15, 15);
        String owner = owner(helper, "raidtower");
        List<Entity> made = new ArrayList<>();
        Mob mason = spawn(level, EntityRegistrar.FIELD_MASON_UNIT.get(), owner, helper.absolutePos(new BlockPos(3, 2, 7)), made);
        if (mason == null) {
            helper.fail("could not create the Field Mason");
            return;
        }
        var tower = Defences.VILLAGERS.raidTower();
        BuildingPlacement placed = BuildingServerEvents.placeBuilding(tower, helper.absolutePos(new BlockPos(6, 1, 6)),
            Rotation.NONE, owner, new int[] { mason.getId() }, false, false, false, true);
        if (placed == null) {
            cleanUp(made, List.of(), List.of(owner));
            helper.fail("placeBuilding refused the " + tower.name + " from a Field Mason");
            return;
        }
        // (helper.fail throws: the checks are collected and reported once the placement is removed again)
        List<String> problems = new ArrayList<>();
        if (((WorkerUnit) mason).getBuildRepairGoal().getBuildingTarget() != placed)
            problems.add("the Field Mason was not put on its own site");
        // 4 s: long enough to walk up and start, far too short to finish (a finished tower would arm itself)
        helper.runAfterDelay(80, () -> {
            try {
                if (placed.buildProgress <= 0)
                    problems.add("the " + tower.name + " made no progress in 4 s with a Field Mason on it");
                finish(helper, problems);
            } finally {
                BuildingServerEvents.getBuildings().remove(placed);
                placed.forceChunk(false);
                cleanUp(made, List.of(), List.of(owner));
            }
        });
    }

    // ---------------------------------------------------------------------------------------------------------------
    // the seven abilities
    // ---------------------------------------------------------------------------------------------------------------

    /**
     * Field Mason's Bulwark against the Wrecker's Tear Down (each the other's counter): a braced friendly wall takes half
     * damage, so a Tear Down that rips an unbraced segment down (refunding half its metal to the Wrecker's side) leaves
     * the braced one standing; the brace ends when the Mason leaves its tether. Bulwark refuses an enemy building; Tear
     * Down refuses its own side's wall, a tower, and anything beyond reach.
     */
    @GameTest(template = ARENA, timeoutTicks = 40)
    public static void bulwark_halves_damage_and_survives_a_tear_down(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        SkirmishGameTests.openGround(helper, 0, 0, 15, 15);
        String owner = owner(helper, "bulwark"), foe = owner + "_foe";
        List<String> problems = new ArrayList<>();
        List<Entity> made = new ArrayList<>();
        List<Resources> pools = new ArrayList<>();
        try {
            Resources foePool = pool(foe, 0, pools);
            var wallType = Defences.VILLAGERS.wall();
            BuildingPlacement braced = stampedBuilt(level, wallType, helper.absolutePos(new BlockPos(3, 1, 3)), owner);
            BuildingPlacement bare = stampedBuilt(level, wallType, helper.absolutePos(new BlockPos(11, 1, 3)), owner);
            BuildingPlacement foeWall = stampedBuilt(level, Defences.PIGLINS.wall(), helper.absolutePos(new BlockPos(3, 1, 11)), foe);
            BuildingPlacement tower = BuildingUtils.getNewBuildingPlacement(Defences.VILLAGERS.raidTower(), level,
                helper.absolutePos(new BlockPos(11, 1, 11)), Rotation.NONE, owner, false);
            tower.isBuilt = true;
            Mob mason = spawn(level, EntityRegistrar.FIELD_MASON_UNIT.get(), owner, helper.absolutePos(new BlockPos(3, 2, 8)), made);
            Mob wrecker = spawn(level, EntityRegistrar.WRECKER_UNIT.get(), foe, helper.absolutePos(new BlockPos(3, 2, 6)), made);
            if (mason == null || wrecker == null) {
                helper.fail("could not create the Field Mason or the Wrecker");
                return;
            }
            if (Bulwark.brace(level, mason, owner, foeWall, true) != null)
                problems.add("Bulwark braced an enemy wall");
            if (Bulwark.brace(level, mason, owner, braced, true) == null)
                problems.add("Bulwark refused a friendly wall in reach");
            if (Bulwark.damageMultiplier(braced) != Bulwark.DAMAGE_MULT || Bulwark.damageMultiplier(bare) != 1f)
                problems.add("the braced wall takes x" + Bulwark.damageMultiplier(braced) + ", the bare one x" + Bulwark.damageMultiplier(bare));
            // Tear Down on the braced segment: hurt, still standing
            int hp0 = braced.getHealth();
            if (!TearDown.tearDown(level, wrecker, foe, braced, true))
                problems.add("Tear Down refused an enemy wall in reach");
            if (braced.shouldBeDestroyed() || braced.getHealth() <= 0 || braced.getHealth() >= hp0)
                problems.add("a braced wall should survive a Tear Down, hurt (health " + hp0 + " -> " + braced.getHealth() + ")");
            if (foePool.getMetal() > 0.01f)
                problems.add("a Tear Down that brought nothing down refunded metal");
            // ...and on the bare one: down, half its metal back to the Wrecker's side
            wrecker.teleportTo(bare.centrePos.getX() + 0.5, wrecker.getY(), bare.maxCorner.getZ() + 2.5);
            if (!TearDown.tearDown(level, wrecker, foe, bare, true))
                problems.add("Tear Down refused the second enemy wall in reach");
            if (!bare.shouldBeDestroyed())
                problems.add("Tear Down left an unbraced wall standing (health " + bare.getHealth() + ")");
            float refund = wallType.cost.metal() * TearDown.REFUND_SHARE;
            if (Math.abs(foePool.getMetal() - refund) > 0.51f)
                problems.add("Tear Down refunded " + foePool.getMetal() + " metal, expected " + refund);
            // what it refuses
            if (TearDown.tearDown(level, wrecker, foe, foeWall, true))
                problems.add("Tear Down took its own side's wall");
            wrecker.teleportTo(tower.centrePos.getX() + 0.5, wrecker.getY(), tower.minCorner.getZ() - 1.5);
            if (TearDown.tearDown(level, wrecker, foe, tower, true))
                problems.add("Tear Down took an anti-raid tower (only walls, gates and traps)");
            // from beside the tower the braced wall is far out of reach
            if (TearDown.tearDown(level, wrecker, foe, braced, true))
                problems.add("Tear Down reached a wall " + (int) CombatEngineers.distanceToBuilding(wrecker, braced) + " blocks away");
            // the tether: the Mason walks off and the brace is gone
            mason.teleportTo(helper.absolutePos(new BlockPos(15, 2, 15)).getX() + 0.5, mason.getY(),
                helper.absolutePos(new BlockPos(15, 2, 15)).getZ() + 0.5);
            if (CombatEngineers.distanceToBuilding(mason, braced) <= Bulwark.TETHER)
                problems.add("test setup: the Mason did not leave the tether");
            else if (Bulwark.braceOn(braced) != null || Bulwark.damageMultiplier(braced) != 1f)
                problems.add("the brace held with the Mason " + (int) CombatEngineers.distanceToBuilding(mason, braced) + " blocks away");
            finish(helper, problems);
        } finally {
            cleanUp(made, pools, List.of(owner, foe));
        }
    }

    /**
     * Bonesetter's Bone Graft: a wreck in reach is torn apart to mend a hurt friend - 2 HP per metal, never more than it
     * is missing, the rest left in the wreck - and without a wreck in reach the graft is refused (nothing mended).
     */
    @GameTest(template = ARENA, timeoutTicks = 40)
    public static void bone_graft_turns_a_wreck_into_health(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        SkirmishGameTests.openGround(helper, 0, 0, 15, 15);
        String owner = owner(helper, "graft");
        List<String> problems = new ArrayList<>();
        List<Entity> made = new ArrayList<>();
        try {
            // mid-arena: the patient (a Halberdier, aggro 10) must have no other test's unit in reach to go after
            Mob setter = spawn(level, EntityRegistrar.BONESETTER_UNIT.get(), owner, helper.absolutePos(new BlockPos(7, 2, 7)), made);
            Mob patient = spawn(level, EntityRegistrar.VINDICATOR_UNIT.get(), owner, helper.absolutePos(new BlockPos(9, 2, 7)), made);
            BlockPos w = helper.absolutePos(new BlockPos(7, 2, 12));   // in Bone Graft's reach, out of reclaim reach
            Entity wreck = WreckServerEvents.spawnWreck(level, w.getX() + 0.5, w.getY(), w.getZ() + 0.5, 30f, Factions.VILLAGERS);
            if (setter == null || patient == null || wreck == null) {
                helper.fail("could not create the Bonesetter, its patient or the wreck");
                return;
            }
            made.add(wreck);
            if (!(((CombatEngineer) setter).getEngineerAbility() instanceof BoneGraft))
                problems.add("the Bonesetter's ability is not Bone Graft");
            patient.setHealth(patient.getMaxHealth() - 40);
            float missing = patient.getMaxHealth() - patient.getHealth(), h0 = patient.getHealth();
            float metal0 = WreckServerEvents.metalOf(wreck);
            float expectMetal = Math.min(Math.min(BoneGraft.MAX_METAL, metal0), missing / BoneGraft.HP_PER_METAL);
            float hp = BoneGraft.graft(level, setter, owner, patient, true);
            if (Math.abs(hp - expectMetal * BoneGraft.HP_PER_METAL) > 0.01f || Math.abs(patient.getHealth() - h0 - hp) > 0.01f)
                problems.add("Bone Graft mended " + hp + " (patient +" + (patient.getHealth() - h0) + "), expected " + expectMetal * BoneGraft.HP_PER_METAL);
            float left = wreck.isRemoved() ? 0 : WreckServerEvents.metalOf(wreck);
            if (Math.abs(metal0 - left - expectMetal) > 0.01f)
                problems.add("Bone Graft took " + (metal0 - left) + " metal from the wreck, expected " + expectMetal);
            // no wreck in reach: refused, nothing mended
            wreck.discard();
            patient.setHealth(patient.getMaxHealth() - 10);
            float h1 = patient.getHealth();
            if (BoneGraft.graft(level, setter, owner, patient, true) != 0 || patient.getHealth() != h1)
                problems.add("Bone Graft mended without a wreck in reach");
            finish(helper, problems);
        } finally {
            cleanUp(made, List.of(), List.of(owner));
        }
    }

    /**
     * Rootwarden's Rootwalk: it sinks and, after SINK_TICKS, rises beside the own building it aimed at; a second
     * Rootwarden hurt while sinking stays where it was (the walk fails - its counter). Refused on an enemy building and
     * on one beyond reach.
     */
    @GameTest(template = ARENA, timeoutTicks = 120)
    public static void rootwalk_rises_beside_a_building_unless_hurt(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        SkirmishGameTests.openGround(helper, 0, 0, 15, 15);
        String owner = owner(helper, "rootwalk"), foe = owner + "_foe";
        List<Entity> made = new ArrayList<>();
        BuildingPlacement home = BuildingUtils.getNewBuildingPlacement(Defences.VERDANT.wall(), level,
            helper.absolutePos(new BlockPos(12, 1, 12)), Rotation.NONE, owner, false);
        home.isBuilt = true;
        BuildingPlacement enemy = BuildingUtils.getNewBuildingPlacement(Defences.VERDANT.wall(), level,
            helper.absolutePos(new BlockPos(12, 1, 2)), Rotation.NONE, foe, false);
        BuildingPlacement far = BuildingUtils.getNewBuildingPlacement(Defences.VERDANT.wall(), level,
            helper.absolutePos(new BlockPos(70, 1, 70)), Rotation.NONE, owner, false);   // coordinates only: never stamped
        Mob a = spawn(level, EntityRegistrar.ROOTWARDEN_UNIT.get(), owner, helper.absolutePos(new BlockPos(2, 2, 2)), made);
        Mob b = spawn(level, EntityRegistrar.ROOTWARDEN_UNIT.get(), owner, helper.absolutePos(new BlockPos(2, 2, 10)), made);
        if (a == null || b == null) {
            cleanUp(made, List.of(), List.of(owner));
            helper.fail("could not create the Rootwardens");
            return;
        }
        List<String> problems = new ArrayList<>();
        if (Rootwalk.begin(level, a, owner, enemy, null, true) != null)
            problems.add("Rootwalk began toward an enemy building");
        if (Rootwalk.begin(level, a, owner, far, null, true) != null)
            problems.add("Rootwalk began toward a building " + (int) CombatEngineers.distanceToBuilding(a, far) + " blocks away");
        if (Rootwalk.begin(level, a, owner, home, null, true) == null || Rootwalk.begin(level, b, owner, home, null, true) == null) {
            cleanUp(made, List.of(), List.of(owner));
            helper.fail("Rootwalk refused an own building in reach");
            return;
        }
        double bx = b.getX(), bz = b.getZ();
        helper.runAfterDelay(5, () -> b.hurt(level.damageSources().generic(), 2f));
        helper.runAfterDelay(Rootwalk.SINK_TICKS + 10, () -> {
            try {
                if (Rootwalk.walkOf(a) != null)
                    problems.add("the walk is still running after " + (Rootwalk.SINK_TICKS + 10) + " ticks");
                if (CombatEngineers.distanceToBuilding(a, home) > 2.5)
                    problems.add("the Rootwarden did not rise beside its building (" + CombatEngineers.distanceToBuilding(a, home) + " away)");
                if (Rootwalk.walkOf(b) != null)
                    problems.add("the hurt Rootwarden is still sinking");
                if (Math.hypot(b.getX() - bx, b.getZ() - bz) > 1.5)
                    problems.add("the Rootwarden hurt while sinking still walked through the roots");
                finish(helper, problems);
            } finally {
                cleanUp(made, List.of(), List.of(owner));
            }
        });
    }

    /**
     * Salvager's Salvage Hook: a wreck 14 blocks off is hooked and, REEL_TICKS later, lies at the Salvager's feet (and is
     * off the line); a click on empty ground is refused; the ability cast through use() starts its cooldown.
     */
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void salvage_hook_reels_a_wreck_to_the_salvager(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        SkirmishGameTests.openGround(helper, 0, 0, 15, 15);
        String owner = owner(helper, "salvage");
        List<Entity> made = new ArrayList<>();
        Mob salvager = spawn(level, EntityRegistrar.SALVAGER_UNIT.get(), owner, helper.absolutePos(new BlockPos(2, 2, 2)), made);
        BlockPos w1 = helper.absolutePos(new BlockPos(12, 2, 12)), w2 = helper.absolutePos(new BlockPos(12, 2, 2));
        Entity far = WreckServerEvents.spawnWreck(level, w1.getX() + 0.5, w1.getY(), w1.getZ() + 0.5, 500f, Factions.TIDEWROUGHT);
        Entity second = WreckServerEvents.spawnWreck(level, w2.getX() + 0.5, w2.getY(), w2.getZ() + 0.5, 500f, Factions.TIDEWROUGHT);
        if (salvager == null || far == null || second == null) {
            cleanUp(made, List.of(), List.of(owner));
            helper.fail("could not create the Salvager or the wrecks");
            return;
        }
        made.add(far);
        made.add(second);
        List<String> problems = new ArrayList<>();
        if (SalvageHook.hook(level, salvager, owner, helper.absolutePos(new BlockPos(2, 2, 12)), true) != null)
            problems.add("Salvage Hook hooked something on empty ground");
        if (SalvageHook.hook(level, salvager, owner, far.blockPosition(), true) == null || !SalvageHook.isOnLine(far)) {
            cleanUp(made, List.of(), List.of(owner));
            helper.fail("Salvage Hook refused a wreck in reach");
            return;
        }
        helper.runAfterDelay(SalvageHook.REEL_TICKS + 5, () -> {
            try {
                if (far.isRemoved() || far.distanceTo(salvager) > 2.5)
                    problems.add("the hooked wreck is not at the Salvager's feet (" + (far.isRemoved() ? "gone" : far.distanceTo(salvager) + " away") + ")");
                if (SalvageHook.isOnLine(far))
                    problems.add("the wreck is still on the line after the reel");
                Ability hook = ((CombatEngineer) salvager).getEngineerAbility();
                if (!(hook instanceof SalvageHook)) {
                    problems.add("the Salvager's ability is not Salvage Hook");
                } else {
                    hook.use(level, (Unit) salvager, second.blockPosition());
                    if (!SalvageHook.isOnLine(second) || hook.isOffCooldown((Unit) salvager))
                        problems.add("casting Salvage Hook through use() did not hook the wreck and start its cooldown");
                }
                finish(helper, problems);
            } finally {
                cleanUp(made, List.of(), List.of(owner));
            }
        });
    }

    /**
     * Jade Mender's Mending Rite: paid from the energy pool, it heals a hurt friend over time; hurting the Mender breaks
     * it (its counter). Refused while its owner has no pool, on the Mender itself and on a friend at full health.
     */
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void mending_rite_heals_until_the_mender_is_hurt(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        SkirmishGameTests.openGround(helper, 0, 0, 15, 15);
        String owner = owner(helper, "rite");
        List<Entity> made = new ArrayList<>();
        List<Resources> pools = new ArrayList<>();
        // mid-arena: the patient (a Halberdier, aggro 10) must have no other test's unit in reach to go after
        Mob mender = spawn(level, EntityRegistrar.JADE_MENDER_UNIT.get(), owner, helper.absolutePos(new BlockPos(6, 2, 7)), made);
        Mob patient = spawn(level, EntityRegistrar.VINDICATOR_UNIT.get(), owner, helper.absolutePos(new BlockPos(9, 2, 7)), made);
        Mob whole = spawn(level, EntityRegistrar.VILLAGER_UNIT.get(), owner, helper.absolutePos(new BlockPos(6, 2, 10)), made);
        if (mender == null || patient == null || whole == null) {
            cleanUp(made, pools, List.of(owner));
            helper.fail("could not create the Mending Rite units");
            return;
        }
        List<String> problems = new ArrayList<>();
        patient.setHealth(patient.getMaxHealth() - 40);
        // no pool yet: the first second can't be paid
        if (MendingRite.begin(level, mender, owner, patient, true) != null)
            problems.add("Mending Rite began for an owner without an energy pool");
        Resources res = pool(owner, 500, pools);
        if (MendingRite.begin(level, mender, owner, mender, true) != null)
            problems.add("Mending Rite began on the Mender itself");
        if (MendingRite.begin(level, mender, owner, whole, true) != null)
            problems.add("Mending Rite began on a friend at full health");
        float energy0 = res.getEnergy(), h0 = patient.getHealth();
        MendingRite.Rite rite = MendingRite.begin(level, mender, owner, patient, true);
        if (rite == null) {
            cleanUp(made, pools, List.of(owner));
            helper.fail("Mending Rite refused a hurt friend in reach with energy in the pool");
            return;
        }
        if (res.getEnergy() >= energy0)
            problems.add("Mending Rite did not pay its first second (energy " + energy0 + " -> " + res.getEnergy() + ")");
        helper.runAfterDelay(30, () -> {
            if (rite.healed <= 0 || patient.getHealth() <= h0)
                problems.add("the rite healed nothing in 1.5 s (healed " + rite.healed + ")");
            if (MendingRite.riteOf(mender) == null)
                problems.add("the rite stopped by itself with the patient still hurt");
            mender.hurt(level.damageSources().generic(), 1f);
        });
        helper.runAfterDelay(34, () -> {
            try {
                if (MendingRite.riteOf(mender) != null)
                    problems.add("the rite held with the Mender hurt");
                finish(helper, problems);
            } finally {
                cleanUp(made, pools, List.of(owner));
            }
        });
    }

    /**
     * Pioneer's Overdrive: x3 build power while the rig drives (and a second start is refused), none at all while it
     * vents (no restart either), the plain 1.5 once it has vented - and reclaim reads it (a driving Pioneer strips x3, a
     * venting one nothing). The rig's clock is placed by starting it in the past; the cast through use() starts the
     * ability's cooldown.
     */
    @GameTest(template = ARENA, timeoutTicks = 60)
    public static void overdrive_triples_build_power_then_vents(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        SkirmishGameTests.openGround(helper, 0, 0, 15, 15);
        String owner = owner(helper, "overdrive");
        List<Entity> made = new ArrayList<>();
        List<Resources> pools = new ArrayList<>();
        pool(owner, 0, pools);
        var type = EntityRegistrar.PIONEER_UNIT.get();
        Mob m1 = spawn(level, type, owner, helper.absolutePos(new BlockPos(2, 2, 2)), made);
        Mob m2 = spawn(level, type, owner, helper.absolutePos(new BlockPos(10, 2, 2)), made);
        Mob m3 = spawn(level, type, owner, helper.absolutePos(new BlockPos(2, 2, 12)), made);
        Mob m4 = spawn(level, type, owner, helper.absolutePos(new BlockPos(10, 2, 12)), made);
        if (!(m1 instanceof PioneerUnit p1) || !(m2 instanceof PioneerUnit p2) || !(m3 instanceof PioneerUnit p3)
                || !(m4 instanceof PioneerUnit p4)) {
            cleanUp(made, pools, List.of(owner));
            helper.fail("could not create the Pioneers");
            return;
        }
        List<String> problems = new ArrayList<>();
        long now = level.getGameTime();
        float base = CombatEngineers.BUILD_POWER;
        if (!p1.startOverdrive(now) || !p1.isOverdriven() || Math.abs(p1.getBuildPower() - base * Overdrive.POWER_MULT) > 0.001f)
            problems.add("a driving Pioneer has " + p1.getBuildPower() + " build power, expected " + base * Overdrive.POWER_MULT);
        if (p1.startOverdrive(now))
            problems.add("the rig restarted while still driving");
        if (!p2.startOverdrive(now - Overdrive.DRIVE_TICKS) || !p2.isVenting() || p2.getBuildPower() != 0f)
            problems.add("a venting Pioneer has " + p2.getBuildPower() + " build power, expected none");
        if (p2.startOverdrive(now))
            problems.add("the rig restarted while venting");
        if (!p3.startOverdrive(now - Overdrive.DRIVE_TICKS - Overdrive.VENT_TICKS) || p3.isOverdriven() || p3.isVenting()
                || Math.abs(p3.getBuildPower() - base) > 0.001f)
            problems.add("a vented Pioneer has " + p3.getBuildPower() + " build power, expected " + base);
        if (!p3.startOverdrive(now))
            problems.add("the rig refused to start again once vented");
        // a few ticks on (the units have joined the unit list reclaim walks; the drive and the vent are still on)
        helper.runAfterDelay(5, () -> {
            try {
                // reclaim reads build power: x3 while driving, nothing while venting (a wreck beside each of the two;
                // made in this tick, so the wreck cap can't take one before the step)
                BlockPos at1 = helper.absolutePos(new BlockPos(2, 2, 3)), at2 = helper.absolutePos(new BlockPos(10, 2, 3));
                Entity w1 = WreckServerEvents.spawnWreck(level, at1.getX() + 0.5, at1.getY(), at1.getZ() + 0.5, 200f, Factions.BRASSWORKS);
                Entity w2 = WreckServerEvents.spawnWreck(level, at2.getX() + 0.5, at2.getY(), at2.getZ() + 0.5, 200f, Factions.BRASSWORKS);
                if (w1 == null || w2 == null) {
                    helper.fail("could not create the wrecks");
                    return;
                }
                made.add(w1);
                made.add(w2);
                float b1 = WreckServerEvents.metalOf(w1), b2 = WreckServerEvents.metalOf(w2);
                WreckServerEvents.tickReclaim(level, 1f);
                float d1 = b1 - (w1.isRemoved() ? 0 : WreckServerEvents.metalOf(w1));
                float d2 = b2 - (w2.isRemoved() ? 0 : WreckServerEvents.metalOf(w2));
                float expect = WreckServerEvents.RECLAIM_PER_POWER * base * Overdrive.POWER_MULT * EconomyPace.of(owner);
                if (Math.abs(d1 - expect) > 0.05f)
                    problems.add("a driving Pioneer stripped " + d1 + " metal in 1 s, expected " + expect);
                if (d2 > 0.001f)
                    problems.add("a venting Pioneer stripped " + d2 + " metal");
                Ability a = p4.getEngineerAbility();
                if (!(a instanceof Overdrive)) {
                    problems.add("the Pioneer's ability is not Overdrive");
                } else {
                    a.use(level, p4, p4.blockPosition());
                    if (!p4.isOverdriven() || a.isOffCooldown(p4))
                        problems.add("casting Overdrive through use() did not open the rig and start its cooldown");
                }
                finish(helper, problems);
            } finally {
                cleanUp(made, pools, List.of(owner));
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
