package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.ability.Ability;
import com.solegendary.reignofnether.ability.AutoCaster;
import com.solegendary.reignofnether.ability.AutoRule;
import com.solegendary.reignofnether.ability.AutocastState;
import com.solegendary.reignofnether.ability.abilities.BloodTotem;
import com.solegendary.reignofnether.ability.abilities.Empower;
import com.solegendary.reignofnether.ability.abilities.Hex;
import com.solegendary.reignofnether.ability.abilities.SpiritPuppet;
import com.solegendary.reignofnether.bot.BotPlayer;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.BuildingUtils;
import com.solegendary.reignofnether.building.Buildings;
import com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement;
import com.solegendary.reignofnether.building.production.ProductionItems;
import com.solegendary.reignofnether.essence.Essence;
import com.solegendary.reignofnether.essence.EssenceServerEvents;
import com.solegendary.reignofnether.faction.Factions;
import com.solegendary.reignofnether.hero.PossessionServer;
import com.solegendary.reignofnether.match.MatchRoster;
import com.solegendary.reignofnether.player.RTSPlayer;
import com.solegendary.reignofnether.mana.SealServerEvents;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.registrars.MobEffectRegistrar;
import com.solegendary.reignofnether.research.ResearchServerEvents;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.resources.ResourceCosts;
import com.solegendary.reignofnether.resources.Resources;
import com.solegendary.reignofnether.resources.ResourcesServerEvents;
import com.solegendary.reignofnether.resources.WreckServerEvents;
import com.solegendary.reignofnether.unit.SelectionKeep;
import com.solegendary.reignofnether.unit.UnitAction;
import com.solegendary.reignofnether.unit.UnitActionItem;
import com.solegendary.reignofnether.unit.UnitTier;
import com.solegendary.reignofnether.unit.interfaces.AttackerUnit;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.units.piglins.BloodTotemUnit;
import com.solegendary.reignofnether.unit.units.piglins.SpiritPuppets;
import com.solegendary.reignofnether.unit.units.piglins.VoodooShamanUnit;
import com.solegendary.reignofnether.util.MiscUtil;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Essence casters PR 2, the Horde's caster (design/essence_casters.md, lovish approved draft 2 on Oct 29, with the
 * coordinator's Revision 1): the <b>Voodoo Shaman</b> (placeholder name), trained at the Flame Sanctuary, an essence
 * carrier with three essence abilities and the shared Empower. Written before the code, from the approved numbers:
 * <ul>
 *   <li>Hex (5 essence, 12 s): one enemy unit of tier 1-2 can neither attack nor cast for 4 s; a T3 target, an ally or a
 *       short caster is refused with nothing spent and no cooldown;</li>
 *   <li>Blood Totem (10 essence, 25 s): a 100 HP totem enemies can target, for 15 s; allies within 8 attack 25% faster,
 *       enemies within 8 move 15% slower; the totem is a summon (its death drops no essence);</li>
 *   <li>Spirit Puppet (20 essence, 60 s): a channel - one enemy T1 unit is the caster's owner's for 15 s, then goes back
 *       with its orders cleared; it goes back at once if the shaman is hit, stunned or more than 16 blocks from it;
 *       refused on T2+; a puppet that dies drops essence as any unit does.</li>
 * </ul>
 * Every cast goes through the order path a click takes ({@link UnitActionItem}), so the cooldown gate is the real one.
 * Every callback is scheduled up front; the early ones record (never throw), the last one judges and cleans up in a
 * finally, whatever the verdict. The shaman's autocasts are switched off so only the test casts.
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class VoodooShamanGameTests {

    static final String ARENA = SkirmishGameTests.ARENA;
    static final String BATCH = "caster_horde";

    // the cost and health this PR chose (the PR body says why; kept when lovish moved the essence casters to T1, Oct 29):
    // the Bloom Priestess's price
    static final int METAL = 175, ENERGY = 220, POP = 3;
    static final float HEALTH = 60f;

    private static String owner(GameTestHelper helper, String what) {
        return "gametest_voodoo_" + what + "_" + helper.absolutePos(BlockPos.ZERO).asLong();
    }

    private static void finish(GameTestHelper helper, List<String> problems) {
        if (problems.isEmpty())
            helper.succeed();
        else
            helper.fail(String.join("; ", problems));
    }

    /** A unit that stands where it is put (no AI), added to {@code made} for the cleanup. */
    private static Mob still(GameTestHelper helper, EntityType<? extends Mob> type, String owner, int x, int z, List<Entity> made) {
        Mob m = HeroGameTests.spawnUnit(helper, type, owner, x, z);
        if (m != null) {
            m.setNoAi(true);
            made.add(m);
        }
        return m;
    }

    /** A shaman holding {@code essence}, its autocasts off (only the test casts). */
    private static Mob shaman(GameTestHelper helper, String owner, int x, int z, int essence, List<Entity> made) {
        Mob m = still(helper, EntityRegistrar.VOODOO_SHAMAN_UNIT.get(), owner, x, z, made);
        if (m instanceof Unit u) {
            for (Ability a : u.getAbilities().get())
                a.setAutocast(false, u);
            if (essence > 0)
                Essence.add(m, essence);
        }
        return m;
    }

    @SuppressWarnings("unchecked")
    private static <T extends Ability> T ability(Mob caster, Class<T> type) {
        if (caster instanceof Unit u)
            for (Ability a : u.getAbilities().get())
                if (type.isInstance(a))
                    return (T) a;
        return null;
    }

    /** The order a click on a unit sends. */
    private static void castAt(GameTestHelper helper, Mob caster, UnitAction action, LivingEntity target) {
        new UnitActionItem(((Unit) caster).getOwnerName(), action, target.getId(), new int[] { caster.getId() },
            target.blockPosition(), BlockPos.ZERO).action(helper.getLevel());
    }

    /** The order a click on the ground sends (the block clicked, relative to the arena). */
    private static void castAtGround(GameTestHelper helper, Mob caster, UnitAction action, BlockPos rel) {
        new UnitActionItem(((Unit) caster).getOwnerName(), action, -1, new int[] { caster.getId() },
            helper.absolutePos(rel), BlockPos.ZERO).action(helper.getLevel());
    }

    /**
     * What a Spirit Puppet click met, for a failure message: the beaten-puppeteer test once found a take not done (green
     * 40840131301, 1 run in 7) with no word of why.
     */
    private static String takeState(GameTestHelper helper, Mob caster, Mob target) {
        ServerLevel level = helper.getLevel();
        return "in level " + (level.getEntity(caster.getId()) == caster) + "/" + (level.getEntity(target.getId()) == target)
            + ", alive " + caster.isAlive() + "/" + target.isAlive()
            + ", distance " + String.format("%.2f", caster.distanceTo(target))
            + ", essence " + Essence.get(caster) + ", cooldown " + cooldown(caster, ability(caster, SpiritPuppet.class))
            + ", sealed " + com.solegendary.reignofnether.mana.SealServerEvents.isSealed(caster)
            + ", stunned " + (caster.hasEffect(MobEffectRegistrar.STUN.get()) || caster.hasEffect(MobEffectRegistrar.FREEZE.get()))
            + ", target owner " + ((Unit) target).getOwnerName() + ", may take " + SpiritPuppets.mayTake(target)
            + ", puppet " + SpiritPuppets.isPuppet(target) + ", tier " + com.solegendary.reignofnether.unit.UnitTier.of(target);
    }

    private static float cooldown(Mob caster, Ability a) {
        return a == null ? 0 : ((Unit) caster).getCooldown(a);
    }

    private static int effectTicks(LivingEntity e, net.minecraft.world.effect.MobEffect effect) {
        MobEffectInstance mei = e.getEffect(effect);
        return mei == null ? 0 : mei.getDuration();
    }

    private static List<BloodTotemUnit> totemsNear(ServerLevel level, Vec3 at, double r) {
        return level.getEntitiesOfClass(BloodTotemUnit.class, new AABB(at, at).inflate(r), Entity::isAlive);
    }

    private static List<Entity> wispsNear(Vec3 at, double r) {
        List<Entity> found = new ArrayList<>();
        for (Entity w : EssenceServerEvents.getWisps())
            if (!w.isRemoved() && w.position().distanceToSqr(at) <= r * r)
                found.add(w);
        return found;
    }

    /**
     * The cleanup every test's last callback runs in its finally: its units, the totems standing in its arena (made by a
     * cast, possibly after the early callback last looked), any puppet record left, and the wisps and wrecks at its spots.
     */
    private static void cleanUp(GameTestHelper helper, List<Entity> made, List<Vec3> spots) {
        AABB arena = new AABB(helper.absolutePos(BlockPos.ZERO)).expandTowards(16, 6, 16).inflate(2);
        for (BloodTotemUnit t : helper.getLevel().getEntitiesOfClass(BloodTotemUnit.class, arena))
            if (!made.contains(t))
                made.add(t);
        for (Entity e : made) {
            if (e instanceof LivingEntity le && SpiritPuppets.isPuppet(le))
                SpiritPuppets.giveBack(le);
            if (!e.isRemoved())
                e.discard();
        }
        for (Vec3 at : spots) {
            wispsNear(at, 2.5).forEach(Entity::discard);
            for (Entity w : WreckServerEvents.getWrecks())
                if (!w.isRemoved() && w.position().distanceToSqr(at) < 2.5 * 2.5)
                    w.discard();
        }
    }

    /** A spawn failed: clean up what was made and fail at once. */
    private static void spawnFailed(GameTestHelper helper, List<Entity> made, String what) {
        cleanUp(helper, made, List.of());
        helper.fail("could not spawn " + what);
    }

    // ------------------------------------------------------------------------------------------------ the unit

    /**
     * lovish (Oct 29): "essence casters should be tier 1 like rez bots". The shaman is trained at the Horde's T1 army
     * building, the Military Portal, not the T2 lab (the Flame Sanctuary): the portal queues it with no Tier 2 research,
     * and it is a tier 1 unit. Its item names an entity registered to the Horde, and completing it (the real onComplete ->
     * produceUnit path) makes an owned shaman with the chosen cost and health. It is an essence carrier and carries Hex,
     * Blood Totem, Spirit Puppet and Empower.
     */
    @GameTest(template = ARENA, timeoutTicks = 60, batch = BATCH)
    public static void voodoo_shaman_trains_at_the_military_portal_at_tier_1_as_an_essence_carrier(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        List<String> problems = new ArrayList<>();
        List<Entity> made = new ArrayList<>();
        String owner = owner(helper, "train");
        // a pool for the test owner, as the engineers' training test has (BAR queues with 0 resources)
        Resources pool = new Resources(owner, 0, 0, 0);
        ResourcesServerEvents.resourcesList.add(pool);
        try {
            if (!Buildings.PORTAL_MILITARY.productions.get().contains(ProductionItems.VOODOO_SHAMAN))
                problems.add("the Military Portal (the Horde's T1 army building) does not train the Voodoo Shaman");
            if (Buildings.FLAME_SANCTUARY.productions.get().contains(ProductionItems.VOODOO_SHAMAN))
                problems.add("the Flame Sanctuary (the Horde's T2 lab) still trains the Voodoo Shaman");
            if (!Factions.PIGLINS.key.equals(Factions.ENTITY_FACTION.get(EntityType.getKey(EntityRegistrar.VOODOO_SHAMAN_UNIT.get()))))
                problems.add("the shaman's entity is not registered to the Horde");
            if (EntityRegistrar.getEntityType(ProductionItems.VOODOO_SHAMAN.getItemName()) != EntityRegistrar.VOODOO_SHAMAN_UNIT.get())
                problems.add("the shaman's item name does not resolve to its entity type");
            ResourceCost c = ResourceCosts.VOODOO_SHAMAN;
            if (c.metal() != METAL || c.energy() != ENERGY || c.population != POP)
                problems.add("the shaman costs " + c.metal() + " metal / " + c.energy() + " energy / " + c.population
                    + " pop, the PR chose " + METAL + " / " + ENERGY + " / " + POP);
            BlockPos corner = helper.absolutePos(BlockPos.ZERO);
            BuildingPlacement bp = BuildingUtils.getNewBuildingPlacement(Buildings.PORTAL_MILITARY, level,
                new BlockPos(corner.getX() + 2, corner.getY() + 1, corner.getZ() + 3), Rotation.NONE, owner, false);
            if (!(bp instanceof ProductionPlacement pp)) {
                problems.add("the Military Portal is not a production placement");
            } else {
                pp.isBuilt = true;
                // no Tier 2 research: a T1 unit
                if (!pp.startProductionItem(ProductionItems.VOODOO_SHAMAN))
                    problems.add("the portal refused the shaman without Tier 2: " + ProductionItems.VOODOO_SHAMAN.getProduceErrorMsg(pp));
                pp.productionQueue.clear();
                ProductionItems.VOODOO_SHAMAN.onComplete.accept(level, pp);
                Mob unit = CombatEngineerGameTests.findProduced(level, pp, EntityRegistrar.VOODOO_SHAMAN_UNIT.get(), owner);
                if (unit == null) {
                    problems.add("completing the item produced no shaman");
                } else {
                    made.add(unit);
                    if (!(unit instanceof VoodooShamanUnit))
                        problems.add("the produced unit is a " + unit.getClass().getSimpleName());
                    if (!(unit instanceof Unit u) || !owner.equals(u.getOwnerName()))
                        problems.add("the produced shaman is not the portal owner's");
                    else if (u.getCost() != ResourceCosts.VOODOO_SHAMAN)
                        problems.add("the shaman's getCost is not ResourceCosts.VOODOO_SHAMAN");
                    if (UnitTier.of(unit) != 1)
                        problems.add("the shaman is tier " + UnitTier.of(unit) + ", expected 1");
                    if (Math.abs(unit.getMaxHealth() - HEALTH) > 0.5f)
                        problems.add("the shaman has " + unit.getMaxHealth() + " max health, the PR chose " + HEALTH);
                    if (!Essence.isCarrier(unit))
                        problems.add("the shaman is not an essence carrier");
                    if (ability(unit, Hex.class) == null)
                        problems.add("no Hex");
                    if (ability(unit, BloodTotem.class) == null)
                        problems.add("no Blood Totem");
                    if (ability(unit, SpiritPuppet.class) == null)
                        problems.add("no Spirit Puppet");
                    if (ability(unit, Empower.class) == null)
                        problems.add("no Empower (every essence caster shares it)");
                }
            }
        } catch (RuntimeException ex) {
            problems.add("threw " + ex);
        } finally {
            cleanUp(helper, made, List.of());
            ResourcesServerEvents.resourcesList.remove(pool);
            ResearchServerEvents.removeAllResearchFor(owner);
        }
        finish(helper, problems);
    }

    /** The shaman gathers the wisps near it like any carrier: a 4-essence wisp 5 blocks off flies in. */
    @GameTest(template = ARENA, timeoutTicks = 120, batch = BATCH)
    public static void voodoo_shaman_gathers_essence_wisps(GameTestHelper helper) {
        List<Entity> made = new ArrayList<>();
        Mob caster = shaman(helper, owner(helper, "gather"), 3, 3, 0, made);
        if (caster == null) {
            spawnFailed(helper, made, "the shaman");
            return;
        }
        Entity wisp = EssenceServerEvents.spawnWisp(helper.getLevel(), caster.getX() + 5, caster.getY() + 1, caster.getZ(), 4);
        if (wisp != null)
            made.add(wisp);
        helper.runAfterDelay(100, () -> {
            List<String> problems = new ArrayList<>();
            try {
                if (wisp == null)
                    problems.add("spawnWisp made no wisp");
                if (!Essence.isCarrier(caster))
                    problems.add("the shaman is not an essence carrier");
                if (Essence.get(caster) != 4)
                    problems.add("the shaman holds " + Essence.get(caster) + " essence, expected the wisp's 4");
            } finally {
                cleanUp(helper, made, List.of());
            }
            finish(helper, problems);
        });
    }

    // ------------------------------------------------------------------------------------------------ Hex

    /**
     * Hex on a T1 enemy (a Vindicator) and on a T2 one (a Wither Skeleton): each is disarmed and sealed for 4 s - its
     * melee blow deals nothing where an unhexed twin's lands, and SealServerEvents calls it sealed - and both are free
     * again after the 4 s. Each cast costs 5 essence and starts the 12 s cooldown.
     */
    @GameTest(template = ARENA, timeoutTicks = 140, batch = BATCH)
    public static void voodoo_hex_disarms_and_seals_a_t1_or_t2_enemy_for_4_seconds(GameTestHelper helper) {
        List<Entity> made = new ArrayList<>();
        String a = owner(helper, "hexa"), b = owner(helper, "hexb");
        Mob casterA = shaman(helper, a, 3, 3, 10, made);
        Mob casterB = shaman(helper, a, 3, 12, 10, made);
        Mob t1 = still(helper, EntityRegistrar.VINDICATOR_UNIT.get(), b, 6, 3, made);
        Mob t2 = still(helper, EntityRegistrar.WITHER_SKELETON_UNIT.get(), b, 6, 12, made);
        Mob twin = still(helper, EntityRegistrar.VINDICATOR_UNIT.get(), b, 12, 3, made);
        Mob victim1 = still(helper, EntityRegistrar.VILLAGER_UNIT.get(), a, 7, 4, made);
        Mob victim2 = still(helper, EntityRegistrar.VILLAGER_UNIT.get(), a, 13, 4, made);
        if (made.size() < 7) {
            spawnFailed(helper, made, "the shamans, the enemies and the victims");
            return;
        }
        List<String> early = new ArrayList<>();
        float[] hp = new float[2];
        helper.runAfterDelay(3, () -> {
            try {
                if (Hex.ESSENCE_COST != 5 || Hex.CD_SECONDS != 12 || Hex.DURATION_SECONDS != 4)
                    early.add("lovish set Hex at 5 essence / 12 s / 4 s, it has " + Hex.ESSENCE_COST + " / " + Hex.CD_SECONDS + " / " + Hex.DURATION_SECONDS);
                if (UnitTier.of(t1) != 1 || UnitTier.of(t2) != 2)
                    early.add("the Vindicator is tier " + UnitTier.of(t1) + " and the Wither Skeleton tier " + UnitTier.of(t2) + ", expected 1 and 2");
                castAt(helper, casterA, UnitAction.HEX, t1);
                castAt(helper, casterB, UnitAction.HEX, t2);
                for (Mob[] p : new Mob[][] { { casterA, t1 }, { casterB, t2 } }) {
                    String name = EntityType.getKey(p[1].getType()).getPath();
                    if (Essence.get(p[0]) != 5)
                        early.add("hexing the " + name + " left " + Essence.get(p[0]) + " essence, expected 10 - 5 = 5");
                    if (Math.abs(cooldown(p[0], ability(p[0], Hex.class)) - Hex.CD_SECONDS * 20) > 2)
                        early.add("hexing the " + name + " set a " + cooldown(p[0], ability(p[0], Hex.class)) + "-tick cooldown, expected 240");
                    if (!p[1].hasEffect(MobEffectRegistrar.DISARM.get()) || !SealServerEvents.isSealed(p[1]) || !Hex.isHexed(p[1]))
                        early.add("the hexed " + name + " is not disarmed, sealed and marked hexed");
                    else if (Math.abs(effectTicks(p[1], MobEffectRegistrar.DISARM.get()) - Hex.DURATION_SECONDS * 20) > 3)
                        early.add("the hexed " + name + " is disarmed for " + effectTicks(p[1], MobEffectRegistrar.DISARM.get()) + " ticks, expected 80");
                }
                hp[0] = victim1.getHealth();
                hp[1] = victim2.getHealth();
                t1.doHurtTarget(victim1);     // the hexed one swings...
                twin.doHurtTarget(victim2);   // ...and its unhexed twin
                if (victim1.getHealth() < hp[0])
                    early.add("the hexed Vindicator's blow dealt " + (hp[0] - victim1.getHealth()) + " damage");
                if (victim2.getHealth() >= hp[1])
                    early.add("the unhexed twin's blow dealt no damage either: the test lost its point");
            } catch (RuntimeException ex) {
                early.add("threw " + ex);
            }
        });
        helper.runAfterDelay(3 + Hex.DURATION_SECONDS * 20 + 10, () -> {
            List<String> problems = new ArrayList<>(early);
            try {
                for (Mob m : new Mob[] { t1, t2 })
                    if (m.hasEffect(MobEffectRegistrar.DISARM.get()) || SealServerEvents.isSealed(m) || Hex.isHexed(m))
                        problems.add("the " + EntityType.getKey(m.getType()).getPath() + " is still hexed after 4 s");
            } finally {
                cleanUp(helper, made, List.of());
            }
            finish(helper, problems);
        });
    }

    /**
     * Hex is refused - nothing spent, no cooldown, no hex - on a T3 enemy (a War Mammoth), on an ally, and with 4 of the
     * 5 essence; and once cast, a second Hex inside the 12 s cooldown does nothing.
     */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = BATCH)
    public static void voodoo_hex_refuses_t3_allies_short_essence_and_its_cooldown(GameTestHelper helper) {
        List<Entity> made = new ArrayList<>();
        String a = owner(helper, "hexra"), b = owner(helper, "hexrb");
        Mob rich = shaman(helper, a, 3, 3, 10, made);
        Mob poor = shaman(helper, a, 3, 13, 4, made);
        Mob t3 = still(helper, EntityRegistrar.WAR_MAMMOTH_UNIT.get(), b, 9, 7, made);   // in reach: refused for its tier
        Mob ally = still(helper, EntityRegistrar.VINDICATOR_UNIT.get(), a, 1, 6, made);
        Mob foe1 = still(helper, EntityRegistrar.VINDICATOR_UNIT.get(), b, 5, 1, made);
        Mob foe2 = still(helper, EntityRegistrar.VINDICATOR_UNIT.get(), b, 6, 13, made);
        if (made.size() < 6) {
            spawnFailed(helper, made, "the shamans and their targets");
            return;
        }
        helper.runAfterDelay(3, () -> {
            List<String> problems = new ArrayList<>();
            try {
                Hex hex = ability(rich, Hex.class);
                if (hex == null) {
                    problems.add("the shaman has no Hex");
                } else {
                    if (UnitTier.of(t3) != 3)
                        problems.add("the War Mammoth is tier " + UnitTier.of(t3) + ", expected 3");
                    castAt(helper, rich, UnitAction.HEX, t3);
                    castAt(helper, rich, UnitAction.HEX, ally);
                    if (Essence.get(rich) != 10 || cooldown(rich, hex) > 0)
                        problems.add("a refused Hex (T3 / ally) cost " + (10 - Essence.get(rich)) + " essence and set a " + cooldown(rich, hex) + "-tick cooldown");
                    if (Hex.isHexed(t3) || Hex.isHexed(ally))
                        problems.add("a T3 enemy or an ally was hexed");
                    castAt(helper, poor, UnitAction.HEX, foe2);
                    if (Essence.get(poor) != 4 || cooldown(poor, ability(poor, Hex.class)) > 0 || Hex.isHexed(foe2))
                        problems.add("a shaman with 4 essence hexed (left " + Essence.get(poor) + ", cooldown " + cooldown(poor, ability(poor, Hex.class)) + ")");
                    castAt(helper, rich, UnitAction.HEX, foe1);
                    if (Essence.get(rich) != 5 || !Hex.isHexed(foe1))
                        problems.add("the valid Hex after the refusals did not land (essence " + Essence.get(rich) + ")");
                    foe1.removeEffect(MobEffectRegistrar.HEXED.get());
                    castAt(helper, rich, UnitAction.HEX, foe1);
                    if (Essence.get(rich) != 5 || Hex.isHexed(foe1))
                        problems.add("a second Hex inside the 12 s cooldown was cast (essence " + Essence.get(rich) + ")");
                }
            } catch (RuntimeException ex) {
                problems.add("threw " + ex);
            } finally {
                cleanUp(helper, made, List.of());
            }
            finish(helper, problems);
        });
    }

    // ------------------------------------------------------------------------------------------------ Blood Totem

    /**
     * Blood Totem: 10 essence and a 25 s cooldown raise a 100 HP totem at the clicked spot; within 8 blocks of it an ally
     * attacks 25% faster and an enemy moves 15% slower, each against an identical unit 11+ blocks away that is untouched.
     * An enemy may target the totem and its blow lands.
     */
    @GameTest(template = ARENA, timeoutTicks = 60, batch = BATCH)
    public static void voodoo_blood_totem_speeds_allies_and_slows_enemies_within_8(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        // open ground (stone at y 1, air above): the arena's grass layer sits at helper y 2, the units' feet, so a click
        // on y 1 would put the totem's spot inside it - green 40840131301 refused every cast (nothing spent, no totem)
        SkirmishGameTests.openGround(helper, 0, 0, 15, 15);
        List<Entity> made = new ArrayList<>();
        String a = owner(helper, "tota"), b = owner(helper, "totb");
        Mob caster = shaman(helper, a, 2, 2, 12, made);
        Mob allyIn = still(helper, EntityRegistrar.VINDICATOR_UNIT.get(), a, 5, 4, made);
        Mob allyOut = still(helper, EntityRegistrar.VINDICATOR_UNIT.get(), a, 14, 4, made);
        Mob foeIn = still(helper, EntityRegistrar.VINDICATOR_UNIT.get(), b, 2, 9, made);
        Mob foeOut = still(helper, EntityRegistrar.VINDICATOR_UNIT.get(), b, 14, 12, made);
        if (made.size() < 5) {
            spawnFailed(helper, made, "the shaman and the four units");
            return;
        }
        BlockPos ground = new BlockPos(2, 1, 4);
        Vec3 spot = Vec3.atBottomCenterOf(helper.absolutePos(ground.above()));
        List<String> early = new ArrayList<>();
        helper.runAfterDelay(2, () -> {
            try {
                if (BloodTotem.ESSENCE_COST != 10 || BloodTotem.CD_SECONDS != 25 || BloodTotem.DURATION_SECONDS != 15 || BloodTotem.RADIUS != 8)
                    early.add("lovish set Blood Totem at 10 essence / 25 s / 15 s / 8 blocks, it has " + BloodTotem.ESSENCE_COST + " / "
                        + BloodTotem.CD_SECONDS + " / " + BloodTotem.DURATION_SECONDS + " / " + BloodTotem.RADIUS);
                castAtGround(helper, caster, UnitAction.BLOOD_TOTEM, ground);
                if (Essence.get(caster) != 2)
                    early.add("the totem left " + Essence.get(caster) + " essence, expected 12 - 10 = 2");
                if (Math.abs(cooldown(caster, ability(caster, BloodTotem.class)) - BloodTotem.CD_SECONDS * 20) > 2)
                    early.add("the totem set a " + cooldown(caster, ability(caster, BloodTotem.class)) + "-tick cooldown, expected 500");
                List<BloodTotemUnit> totems = totemsNear(level, spot, 2);
                if (totems.size() != 1)
                    early.add(totems.size() + " totems stand at the clicked spot, expected 1");
                else {
                    made.add(totems.get(0));
                    if (!a.equals(totems.get(0).getOwnerName()))
                        early.add("the totem is not the shaman's owner's");
                    if (!Essence.isSummoned(totems.get(0)))
                        early.add("the totem is not marked summoned");
                    if (Math.abs(totems.get(0).getMaxHealth() - 100f) > 0.5f)
                        early.add("the totem has " + totems.get(0).getMaxHealth() + " max health, Revision 1 set 100");
                }
            } catch (RuntimeException ex) {
                early.add("threw " + ex);
            }
        });
        helper.runAfterDelay(30, () -> {
            List<String> problems = new ArrayList<>(early);
            try {
                float in = ((AttackerUnit) allyIn).getAttacksPerSecond(), out = ((AttackerUnit) allyOut).getAttacksPerSecond();
                if (out <= 0 || Math.abs(in / out - 1.25f) > 0.01f)
                    problems.add("the ally within 8 attacks " + in + "/s against " + out + "/s outside, expected x1.25");
                if (!allyIn.hasEffect(MobEffectRegistrar.BLOOD_FRENZY.get()) || allyOut.hasEffect(MobEffectRegistrar.BLOOD_FRENZY.get()))
                    problems.add("the frenzy is on the wrong allies (in " + allyIn.hasEffect(MobEffectRegistrar.BLOOD_FRENZY.get())
                        + ", out " + allyOut.hasEffect(MobEffectRegistrar.BLOOD_FRENZY.get()) + ")");
                double sIn = foeIn.getAttributeValue(Attributes.MOVEMENT_SPEED), sOut = foeOut.getAttributeValue(Attributes.MOVEMENT_SPEED);
                if (sOut <= 0 || Math.abs(sIn / sOut - 0.85) > 0.01)
                    problems.add("the enemy within 8 moves at " + sIn + " against " + sOut + " outside, expected x0.85");
                if (foeIn.hasEffect(MobEffectRegistrar.BLOOD_FRENZY.get()) || allyIn.hasEffect(MobEffectRegistrar.BLOOD_TOTEM_SLOW.get()))
                    problems.add("the totem buffed an enemy or slowed an ally");
                List<BloodTotemUnit> totems = totemsNear(level, spot, 2);
                if (!totems.isEmpty()) {
                    BloodTotemUnit totem = totems.get(0);
                    if (!MiscUtil.isIdleOrMoveAttackable(foeIn, totem, false))
                        problems.add("an enemy may not target the totem");
                    float before = totem.getHealth();
                    foeIn.doHurtTarget(totem);
                    if (totem.getHealth() >= before)
                        problems.add("an enemy's blow left the totem at " + totem.getHealth() + " of " + before);
                }
            } finally {
                cleanUp(helper, made, List.of());
            }
            finish(helper, problems);
        });
    }

    /**
     * The totem stands 15 s, then falls - and, a summon, leaves no essence wisp; a second Blood Totem with 9 essence is
     * refused with nothing spent and no cooldown.
     */
    @GameTest(template = ARENA, timeoutTicks = 360, batch = BATCH)
    public static void voodoo_blood_totem_lasts_15_seconds_drops_no_essence_and_refuses_when_short(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        // open ground (stone at y 1, air above): the arena's grass layer sits at helper y 2, the units' feet, so a click
        // on y 1 would put the totem's spot inside it - green 40840131301 refused every cast (nothing spent, no totem)
        SkirmishGameTests.openGround(helper, 0, 0, 15, 15);
        List<Entity> made = new ArrayList<>();
        String a = owner(helper, "totl");
        Mob caster = shaman(helper, a, 3, 3, 10, made);
        Mob poor = shaman(helper, a, 12, 12, 9, made);
        if (made.size() < 2) {
            spawnFailed(helper, made, "the shamans");
            return;
        }
        BlockPos ground = new BlockPos(6, 1, 6), poorGround = new BlockPos(12, 1, 9);
        Vec3 spot = Vec3.atBottomCenterOf(helper.absolutePos(ground.above()));
        Vec3 poorSpot = Vec3.atBottomCenterOf(helper.absolutePos(poorGround.above()));
        List<String> early = new ArrayList<>();
        helper.runAfterDelay(2, () -> {
            try {
                castAtGround(helper, caster, UnitAction.BLOOD_TOTEM, ground);
                castAtGround(helper, poor, UnitAction.BLOOD_TOTEM, poorGround);
                List<BloodTotemUnit> totems = totemsNear(level, spot, 2);
                made.addAll(totems);
                if (totems.size() != 1)
                    early.add(totems.size() + " totems after the cast, expected 1");
                if (Essence.get(poor) != 9 || cooldown(poor, ability(poor, BloodTotem.class)) > 0 || !totemsNear(level, poorSpot, 2).isEmpty())
                    early.add("a shaman with 9 essence raised a totem (left " + Essence.get(poor) + ")");
            } catch (RuntimeException ex) {
                early.add("threw " + ex);
            }
        });
        helper.runAfterDelay(2 + BloodTotem.DURATION_SECONDS * 20 - 20, () -> {
            if (totemsNear(level, spot, 2).size() != 1)
                early.add("the totem fell before 14 s");
        });
        helper.runAfterDelay(2 + BloodTotem.DURATION_SECONDS * 20 + 25, () -> {
            List<String> problems = new ArrayList<>(early);
            try {
                if (!totemsNear(level, spot, 2).isEmpty())
                    problems.add("the totem still stands after 15 s");
                List<Entity> wisps = wispsNear(spot, 2);
                if (!wisps.isEmpty())
                    problems.add("the fallen totem left " + wisps.size() + " essence wisp(s): a summon must drop none");
            } finally {
                cleanUp(helper, made, List.of(spot));
            }
            finish(helper, problems);
        });
    }

    // ------------------------------------------------------------------------------------------------ Spirit Puppet

    /**
     * Spirit Puppet on a T1 enemy (a Vindicator): 20 essence and a 60 s cooldown make it the caster's owner's for 15 s -
     * its old orders dropped, its old owner unable to order it, the new one able - and then it goes back to its owner with
     * whatever the puppeteer ordered cleared and no puppet record left. The shaman stands 4 blocks off, unhurt, unstunned:
     * the channel holds the whole 15 s.
     */
    @GameTest(template = ARENA, timeoutTicks = 360, batch = BATCH)
    public static void voodoo_spirit_puppet_controls_a_t1_enemy_for_15_seconds_then_returns_it(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        List<Entity> made = new ArrayList<>();
        String a = owner(helper, "pupa"), b = owner(helper, "pupb");
        Mob caster = shaman(helper, a, 3, 3, 20, made);
        Mob foe = still(helper, EntityRegistrar.VINDICATOR_UNIT.get(), b, 7, 3, made);
        if (made.size() < 2) {
            spawnFailed(helper, made, "the shaman and the enemy");
            return;
        }
        BlockPos bOrder = helper.absolutePos(new BlockPos(12, 2, 12)), aOrder = helper.absolutePos(new BlockPos(2, 2, 12));
        List<String> early = new ArrayList<>();
        helper.runAfterDelay(2, () -> {
            try {
                if (SpiritPuppet.ESSENCE_COST != 20 || SpiritPuppet.CD_SECONDS != 60 || SpiritPuppet.DURATION_SECONDS != 15)
                    early.add("lovish set Spirit Puppet at 20 essence / 60 s / 15 s, it has " + SpiritPuppet.ESSENCE_COST + " / "
                        + SpiritPuppet.CD_SECONDS + " / " + SpiritPuppet.DURATION_SECONDS);
                ((Unit) foe).getMoveGoal().setMoveTarget(bOrder);   // its owner's order, under way
                castAt(helper, caster, UnitAction.SPIRIT_PUPPET, foe);
                if (Essence.get(caster) != 0)
                    early.add("the puppet left " + Essence.get(caster) + " essence, expected 20 - 20 = 0");
                if (Math.abs(cooldown(caster, ability(caster, SpiritPuppet.class)) - SpiritPuppet.CD_SECONDS * 20) > 2)
                    early.add("the puppet set a " + cooldown(caster, ability(caster, SpiritPuppet.class)) + "-tick cooldown, expected 1200");
                if (!a.equals(((Unit) foe).getOwnerName()))
                    early.add("the puppet still belongs to " + ((Unit) foe).getOwnerName());
                if (!SpiritPuppets.isPuppet(foe) || !b.equals(SpiritPuppets.originalOwner(foe)))
                    early.add("the puppet's original owner is not recorded (" + SpiritPuppets.originalOwner(foe) + ")");
                if (((Unit) foe).getMoveGoal().getMoveTarget() != null)
                    early.add("the puppet kept its old owner's move order");
                if (UnitActionItem.mayCommand(level, b, (Unit) foe, false))
                    early.add("its old owner can still order the puppet");
                if (!UnitActionItem.mayCommand(level, a, (Unit) foe, false))
                    early.add("the puppeteer cannot order the puppet");
                ((Unit) foe).getMoveGoal().setMoveTarget(aOrder);   // the puppeteer's order
            } catch (RuntimeException ex) {
                early.add("threw " + ex);
            }
        });
        helper.runAfterDelay(2 + SpiritPuppet.DURATION_SECONDS * 20 - 20, () -> {
            if (!a.equals(((Unit) foe).getOwnerName()))
                early.add("the puppet went back before 14 s (the channel broke with nothing breaking it)");
        });
        helper.runAfterDelay(2 + SpiritPuppet.DURATION_SECONDS * 20 + 20, () -> {
            List<String> problems = new ArrayList<>(early);
            try {
                if (!b.equals(((Unit) foe).getOwnerName()))
                    problems.add("after 15 s the puppet belongs to " + ((Unit) foe).getOwnerName() + ", not its owner");
                if (SpiritPuppets.isPuppet(foe))
                    problems.add("the returned unit still carries a puppet record");
                if (((Unit) foe).getMoveGoal().getMoveTarget() != null)
                    problems.add("the returned unit kept the puppeteer's move order");
                if (!UnitActionItem.mayCommand(level, b, (Unit) foe, false) || UnitActionItem.mayCommand(level, a, (Unit) foe, false))
                    problems.add("the order rights did not go back to the owner");
                if (!foe.isAlive())
                    problems.add("the puppet died");
            } finally {
                cleanUp(helper, made, List.of());
            }
            finish(helper, problems);
        });
    }

    /**
     * Spirit Puppet is a channel (Revision 1): three shamans each take a T1 enemy, then one is hit, one is stunned and one
     * walks 17 blocks from its puppet - each puppet goes back to its owner at once (orders cleared, record gone), well
     * before the 15 s.
     */
    @GameTest(template = ARENA, timeoutTicks = 60, batch = BATCH)
    public static void voodoo_spirit_puppet_breaks_when_the_shaman_is_hit_stunned_or_16_blocks_away(GameTestHelper helper) {
        List<Entity> made = new ArrayList<>();
        String a = owner(helper, "pupca"), b = owner(helper, "pupcb");
        Mob hit = shaman(helper, a, 2, 2, 20, made);
        Mob stunned = shaman(helper, a, 2, 7, 20, made);
        Mob walker = shaman(helper, a, 2, 12, 20, made);
        Mob foeHit = still(helper, EntityRegistrar.VINDICATOR_UNIT.get(), b, 6, 2, made);
        Mob foeStunned = still(helper, EntityRegistrar.VINDICATOR_UNIT.get(), b, 6, 7, made);
        Mob foeWalker = still(helper, EntityRegistrar.VINDICATOR_UNIT.get(), b, 6, 12, made);
        if (made.size() < 6) {
            spawnFailed(helper, made, "the shamans and the enemies");
            return;
        }
        Mob[][] pairs = { { hit, foeHit }, { stunned, foeStunned }, { walker, foeWalker } };
        String[] how = { "hit", "stunned", "17 blocks away" };
        List<String> early = new ArrayList<>();
        helper.runAfterDelay(2, () -> {
            try {
                for (int i = 0; i < pairs.length; i++) {
                    castAt(helper, pairs[i][0], UnitAction.SPIRIT_PUPPET, pairs[i][1]);
                    if (!a.equals(((Unit) pairs[i][1]).getOwnerName()))
                        early.add("the " + how[i] + " shaman's Spirit Puppet did not take its Vindicator");
                }
            } catch (RuntimeException ex) {
                early.add("threw " + ex);
            }
        });
        helper.runAfterDelay(6, () -> {
            try {
                hit.hurt(hit.damageSources().generic(), 2f);
                stunned.addEffect(new MobEffectInstance(MobEffectRegistrar.STUN.get(), 40));
                walker.teleportTo(foeWalker.getX() + 17, walker.getY(), walker.getZ());
            } catch (RuntimeException ex) {
                early.add("threw " + ex);
            }
        });
        helper.runAfterDelay(12, () -> {
            List<String> problems = new ArrayList<>(early);
            try {
                if (SpiritPuppet.CHANNEL_RANGE != 16)
                    problems.add("Revision 1 breaks the channel beyond 16 blocks, CHANNEL_RANGE is " + SpiritPuppet.CHANNEL_RANGE);
                for (int i = 0; i < pairs.length; i++) {
                    Mob foe = pairs[i][1];
                    if (!b.equals(((Unit) foe).getOwnerName()))
                        problems.add("the puppet of the " + how[i] + " shaman is still " + ((Unit) foe).getOwnerName() + "'s");
                    if (SpiritPuppets.isPuppet(foe) || SpiritPuppets.tracks(foe))
                        problems.add("the puppet of the " + how[i] + " shaman still has a puppet record");
                }
            } finally {
                cleanUp(helper, made, List.of());
            }
            finish(helper, problems);
        });
    }

    /**
     * Review round 1, B1 (the take side): a puppet the puppeteer drives in first person (a headless possession, as the
     * artillery tests claim one) is let go when it goes back - the possession ends with the hand-back, so the 15 s steal
     * cannot become permanent control. The channel is broken early (the shaman is hit) to hand it back.
     */
    @GameTest(template = ARENA, timeoutTicks = 60, batch = BATCH)
    public static void voodoo_spirit_puppet_hand_back_ends_the_puppeteers_possession(GameTestHelper helper) {
        List<Entity> made = new ArrayList<>();
        String a = owner(helper, "pupposa"), b = owner(helper, "pupposb");
        UUID driver = UUID.randomUUID();
        Mob caster = shaman(helper, a, 3, 3, 20, made);
        Mob foe = still(helper, EntityRegistrar.VINDICATOR_UNIT.get(), b, 7, 3, made);
        if (made.size() < 2) {
            spawnFailed(helper, made, "the shaman and the enemy");
            return;
        }
        List<String> early = new ArrayList<>();
        helper.runAfterDelay(2, () -> {
            try {
                castAt(helper, caster, UnitAction.SPIRIT_PUPPET, foe);
                if (!a.equals(((Unit) foe).getOwnerName()))
                    early.add("Spirit Puppet did not take the Vindicator");
            } catch (RuntimeException ex) {
                early.add("threw " + ex);
            }
        });
        helper.runAfterDelay(3, () -> {
            try {
                String why = PossessionServer.claim(helper.getLevel(), a, driver, foe.getId(), true);
                if (why != null)
                    early.add("the puppeteer could not possess its puppet: " + why);
                else if (!PossessionServer.isPossessed(foe))
                    early.add("the claim left the puppet unpossessed");
            } catch (RuntimeException ex) {
                early.add("threw " + ex);
            }
        });
        helper.runAfterDelay(6, () -> {
            try {
                caster.hurt(caster.damageSources().generic(), 2f);   // breaks the channel: the puppet goes back
            } catch (RuntimeException ex) {
                early.add("threw " + ex);
            }
        });
        helper.runAfterDelay(10, () -> {
            List<String> problems = new ArrayList<>(early);
            try {
                if (!b.equals(((Unit) foe).getOwnerName()))
                    problems.add("the puppet did not go back to its owner (" + ((Unit) foe).getOwnerName() + ")");
                if (PossessionServer.isPossessed(foe) || PossessionServer.of(driver) != null)
                    problems.add("the puppeteer still drives the returned unit in first person");
            } finally {
                PossessionServer.release(driver, null);
                cleanUp(helper, made, List.of());
            }
            finish(helper, problems);
        });
    }

    /**
     * Review round 1, B2: a puppet whose owner is beaten during the channel (MatchRoster.finish) is not handed back to a
     * finished side, where nobody could order it: it is stripped (owner "") and killed, as MatchOutcome's strip does, and
     * its record goes. The channel is broken early (the shaman is hit).
     */
    @GameTest(template = ARENA, timeoutTicks = 60, batch = BATCH)
    public static void voodoo_spirit_puppet_of_a_beaten_owner_is_stripped_not_returned(GameTestHelper helper) {
        List<Entity> made = new ArrayList<>();
        String a = owner(helper, "pupdefa"), b = owner(helper, "pupdefb");
        RTSPlayer beaten = RTSPlayer.getNewScenarioPlayer(b, Factions.VILLAGERS, -9000, -1);
        Mob caster = shaman(helper, a, 3, 3, 20, made);
        Mob foe = still(helper, EntityRegistrar.VINDICATOR_UNIT.get(), b, 7, 3, made);
        if (made.size() < 2) {
            spawnFailed(helper, made, "the shaman and the enemy");
            return;
        }
        List<Vec3> spots = new ArrayList<>();
        List<String> early = new ArrayList<>();
        helper.runAfterDelay(2, () -> {
            try {
                castAt(helper, caster, UnitAction.SPIRIT_PUPPET, foe);
                if (!a.equals(((Unit) foe).getOwnerName()))
                    early.add("Spirit Puppet did not take the Vindicator");
                MatchRoster.finish(beaten);   // its owner is beaten while the puppet is the puppeteer's
                if (!MatchRoster.isFinished(b))
                    early.add("MatchRoster.finish did not mark the owner finished: the test lost its point");
            } catch (RuntimeException ex) {
                early.add("threw " + ex);
            }
        });
        helper.runAfterDelay(5, () -> {
            try {
                spots.add(foe.position());
                caster.hurt(caster.damageSources().generic(), 2f);   // breaks the channel
            } catch (RuntimeException ex) {
                early.add("threw " + ex);
            }
        });
        helper.runAfterDelay(9, () -> {
            List<String> problems = new ArrayList<>(early);
            try {
                if (foe.isAlive() && !foe.isRemoved())
                    problems.add("the beaten owner's puppet is still alive, owned by '" + ((Unit) foe).getOwnerName() + "'");
                if (b.equals(((Unit) foe).getOwnerName()))
                    problems.add("the puppet was handed back to a beaten side");
                if (SpiritPuppets.isPuppet(foe) || SpiritPuppets.tracks(foe))
                    problems.add("the stripped puppet still has a puppet record");
            } finally {
                MatchRoster.FINISHED.remove(beaten);
                cleanUp(helper, made, spots);
            }
            finish(helper, problems);
        });
    }

    /**
     * Review round 1, B1 (the other side) and the refusal list: Spirit Puppet is refused - nothing spent, no cooldown, the
     * owner unchanged - on a unit its owner drives in first person, on a rider and on the unit it rides, and on an enemy
     * summon (a Blood Totem); Hex refuses the summon too. A plain T1 enemy is then taken (the control).
     */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = BATCH)
    public static void voodoo_spirit_puppet_and_hex_refuse_possessed_mounted_and_summoned_targets(GameTestHelper helper) {
        List<Entity> made = new ArrayList<>();
        String a = owner(helper, "puprefa"), b = owner(helper, "puprefb");
        UUID driver = UUID.randomUUID();
        Mob caster = shaman(helper, a, 3, 3, 25, made);
        Mob driven = still(helper, EntityRegistrar.VINDICATOR_UNIT.get(), b, 6, 1, made);
        Mob mount = still(helper, EntityRegistrar.VINDICATOR_UNIT.get(), b, 7, 5, made);
        Mob rider = still(helper, EntityRegistrar.VINDICATOR_UNIT.get(), b, 7, 5, made);
        Mob totem = still(helper, EntityRegistrar.BLOOD_TOTEM_UNIT.get(), b, 3, 7, made);
        Mob plain = still(helper, EntityRegistrar.VINDICATOR_UNIT.get(), b, 1, 6, made);   // the control: a valid take
        if (made.size() < 6) {
            spawnFailed(helper, made, "the shaman and the targets");
            return;
        }
        Essence.markSummoned(totem);   // as BloodTotem.cast marks it
        helper.runAfterDelay(3, () -> {
            List<String> problems = new ArrayList<>();
            try {
                String why = PossessionServer.claim(helper.getLevel(), b, driver, driven.getId(), true);
                if (why != null)
                    problems.add("its owner could not possess the Vindicator: " + why);
                if (!rider.startRiding(mount, true))
                    problems.add("the rider could not mount: the test lost its point");
                SpiritPuppet sp = ability(caster, SpiritPuppet.class);
                Hex hex = ability(caster, Hex.class);
                Mob[] targets = { driven, rider, mount, totem };
                String[] what = { "a possessed unit", "a rider", "a unit carrying a rider", "a summoned totem" };
                for (int i = 0; i < targets.length; i++) {
                    castAt(helper, caster, UnitAction.SPIRIT_PUPPET, targets[i]);
                    if (Essence.get(caster) != 25 || cooldown(caster, sp) > 0 || !b.equals(((Unit) targets[i]).getOwnerName()))
                        problems.add("Spirit Puppet took " + what[i] + " (essence " + Essence.get(caster) + ", owner "
                            + ((Unit) targets[i]).getOwnerName() + ")");
                }
                castAt(helper, caster, UnitAction.HEX, totem);
                if (Essence.get(caster) != 25 || cooldown(caster, hex) > 0 || Hex.isHexed(totem))
                    problems.add("Hex hexed a summoned totem (essence " + Essence.get(caster) + ")");
                castAt(helper, caster, UnitAction.SPIRIT_PUPPET, plain);
                if (!a.equals(((Unit) plain).getOwnerName()) || Essence.get(caster) != 5)
                    problems.add("after the refusals a plain T1 enemy was not taken either (essence " + Essence.get(caster)
                        + "): the refusals prove nothing");
            } catch (RuntimeException ex) {
                problems.add("threw " + ex);
            } finally {
                PossessionServer.release(driver, null);
                rider.stopRiding();
                cleanUp(helper, made, List.of());
            }
            finish(helper, problems);
        });
    }

    /**
     * Review round 2, B2: when the puppeteer's side is beaten, MatchOutcome.defeat hands its puppets back before it strips
     * the side (the strip takes every unit the side owns at that moment). SpiritPuppets.giveBackHeldBy, the call defeat
     * makes, returns the puppet to its owner alive, its record gone; another puppeteer's puppet is left alone.
     * The takes go through the rule (SpiritPuppet.cast), not a click: an order finds its target by id (level.getEntity),
     * and on a loaded runner the Vindicators' chunk is not always entity-ticking at tick 2 - green 40840131301 and
     * 40952359012 each lost both takes once in 7-8 runs ("in level true/false"), the test's own setup, not its point.
     */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = BATCH)
    public static void voodoo_spirit_puppets_of_a_beaten_puppeteer_go_back_before_its_strip(GameTestHelper helper) {
        List<Entity> made = new ArrayList<>();
        String a = owner(helper, "puplosa"), b = owner(helper, "puplosb"), c = owner(helper, "puplosc");
        Mob beaten = shaman(helper, a, 3, 3, 20, made);
        Mob other = shaman(helper, c, 3, 12, 20, made);
        Mob foe = still(helper, EntityRegistrar.VINDICATOR_UNIT.get(), b, 7, 3, made);
        Mob foe2 = still(helper, EntityRegistrar.VINDICATOR_UNIT.get(), b, 7, 12, made);
        if (made.size() < 4) {
            spawnFailed(helper, made, "the shamans and the enemies");
            return;
        }
        helper.runAfterDelay(2, () -> {
            List<String> problems = new ArrayList<>();
            try {
                SpiritPuppet.cast(helper.getLevel(), beaten, a, foe, true);
                if (!a.equals(((Unit) foe).getOwnerName()))
                    problems.add("the beaten puppeteer's take failed, the test lost its point (" + takeState(helper, beaten, foe) + ")");
                SpiritPuppet.cast(helper.getLevel(), other, c, foe2, true);
                boolean took2 = c.equals(((Unit) foe2).getOwnerName());
                if (!took2)
                    problems.add("the other puppeteer's take failed, the test lost its point (" + takeState(helper, other, foe2) + ")");
                SpiritPuppets.giveBackHeldBy(a);
                if (!b.equals(((Unit) foe).getOwnerName()) || !foe.isAlive())
                    problems.add("the beaten puppeteer's puppet is " + ((Unit) foe).getOwnerName() + "'s (alive " + foe.isAlive()
                        + "), not back with its owner");
                if (SpiritPuppets.isPuppet(foe) || SpiritPuppets.tracks(foe))
                    problems.add("the returned puppet still has a record");
                if (took2 && (!c.equals(((Unit) foe2).getOwnerName()) || !SpiritPuppets.isPuppet(foe2)))
                    problems.add("another puppeteer's puppet was given back too");
            } catch (RuntimeException ex) {
                problems.add("threw " + ex);
            } finally {
                cleanUp(helper, made, List.of());
            }
            finish(helper, problems);
        });
    }

    /**
     * Spirit Puppet is refused - nothing spent, no cooldown, no change of owner - on a T2 enemy (a Wither Skeleton) and
     * with 19 of the 20 essence; a puppet killed while it is controlled drops its essence wisp as any unit does (a
     * Vindicator: 95 metal, 3 essence) and leaves no puppet record behind.
     */
    @GameTest(template = ARENA, timeoutTicks = 60, batch = BATCH)
    public static void voodoo_spirit_puppet_refuses_t2_and_short_essence_and_a_dead_puppet_drops_essence(GameTestHelper helper) {
        List<Entity> made = new ArrayList<>();
        String a = owner(helper, "pupra"), b = owner(helper, "puprb");
        Mob rich = shaman(helper, a, 3, 3, 20, made);
        Mob poor = shaman(helper, a, 3, 12, 19, made);
        Mob t2 = still(helper, EntityRegistrar.WITHER_SKELETON_UNIT.get(), b, 7, 6, made);
        Mob foe = still(helper, EntityRegistrar.VINDICATOR_UNIT.get(), b, 6, 2, made);
        Mob foe2 = still(helper, EntityRegistrar.VINDICATOR_UNIT.get(), b, 6, 12, made);
        if (made.size() < 5) {
            spawnFailed(helper, made, "the shamans and their targets");
            return;
        }
        List<Vec3> spots = new ArrayList<>();
        List<String> early = new ArrayList<>();
        int want = Essence.essenceFor(((Unit) foe).getCost().metal());
        helper.runAfterDelay(2, () -> {
            try {
                SpiritPuppet sp = ability(rich, SpiritPuppet.class);
                castAt(helper, rich, UnitAction.SPIRIT_PUPPET, t2);
                if (Essence.get(rich) != 20 || cooldown(rich, sp) > 0 || !b.equals(((Unit) t2).getOwnerName()))
                    early.add("Spirit Puppet took a T2 Wither Skeleton (essence " + Essence.get(rich) + ", owner " + ((Unit) t2).getOwnerName() + ")");
                castAt(helper, poor, UnitAction.SPIRIT_PUPPET, foe2);
                if (Essence.get(poor) != 19 || cooldown(poor, ability(poor, SpiritPuppet.class)) > 0 || !b.equals(((Unit) foe2).getOwnerName()))
                    early.add("a shaman with 19 essence took a puppet");
                castAt(helper, rich, UnitAction.SPIRIT_PUPPET, foe);
                if (!a.equals(((Unit) foe).getOwnerName()))
                    early.add("the valid Spirit Puppet did not take the Vindicator");
                spots.add(foe.position());
                foe.kill();
                // counted at once: the puppeteer, an essence carrier 3 blocks off with room, pulls the wisp in within a few
                // ticks (green 40840131301 found 0 wisps at tick 8, every run)
                List<Entity> wisps = wispsNear(spots.get(0), 1.5);
                if (wisps.size() != 1)
                    early.add("the dead puppet left " + wisps.size() + " wisps, expected 1");
                else if (EssenceServerEvents.valueOf(wisps.get(0)) != want)
                    early.add("the dead puppet's wisp is worth " + EssenceServerEvents.valueOf(wisps.get(0)) + ", expected " + want);
            } catch (RuntimeException ex) {
                early.add("threw " + ex);
            }
        });
        helper.runAfterDelay(8, () -> {
            List<String> problems = new ArrayList<>(early);
            try {
                if (SpiritPuppets.isPuppet(foe) || SpiritPuppets.tracks(foe))
                    problems.add("the dead puppet is still tracked");
            } finally {
                cleanUp(helper, made, spots);
            }
            finish(helper, problems);
        });
    }

    /**
     * lovish (Oct 29): the bots build and use every caster. The Horde bot kit trains the shaman with its T1 army (the
     * Military Portal), not its T2 army; a fresh
     * shaman autocasts Hex and Blood Totem (Spirit Puppet and Empower stay manual), and one AutoCaster look by a shaman
     * holding 10 essence hexes the enemy beside it. A shaman short of the essence does not try (no order, so its attack
     * target is kept) - the AutoCaster's order would reset it before the refusal.
     */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = BATCH)
    public static void voodoo_bots_train_the_shaman_and_it_autocasts_hex_only_with_the_essence(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        List<Entity> made = new ArrayList<>();
        String a = owner(helper, "bota"), b = owner(helper, "botb");
        Mob rich = still(helper, EntityRegistrar.VOODOO_SHAMAN_UNIT.get(), a, 3, 3, made);
        Mob poor = still(helper, EntityRegistrar.VOODOO_SHAMAN_UNIT.get(), a, 3, 12, made);
        Mob foe = still(helper, EntityRegistrar.VINDICATOR_UNIT.get(), b, 7, 3, made);
        Mob foe2 = still(helper, EntityRegistrar.VINDICATOR_UNIT.get(), b, 7, 12, made);
        if (made.size() < 4) {
            spawnFailed(helper, made, "the shamans and the enemies");
            return;
        }
        helper.runAfterDelay(3, () -> {
            List<String> problems = new ArrayList<>();
            try {
                BotPlayer.Kit kit = BotPlayer.kitFor(Factions.PIGLINS);
                if (kit == null || !kit.army().contains(ProductionItems.VOODOO_SHAMAN))
                    problems.add("the Horde bot kit does not train the Voodoo Shaman in its T1 army");
                if (kit != null && kit.t2Army().contains(ProductionItems.VOODOO_SHAMAN))
                    problems.add("the Horde bot kit still trains the Voodoo Shaman in its T2 army");
                // BotPlayer.think puts every non-worker AttackerUnit in its army list: the shaman marches with the attack
                // waves (casters that are not attackers need the "healers follow the army" line; this one does not)
                if (!(rich instanceof AttackerUnit) || rich instanceof com.solegendary.reignofnether.unit.interfaces.WorkerUnit)
                    problems.add("the shaman is not a non-worker attacker: a bot's army list would never hold it");
                Unit ru = (Unit) rich;
                Hex hex = ability(rich, Hex.class);
                BloodTotem totem = ability(rich, BloodTotem.class);
                SpiritPuppet puppet = ability(rich, SpiritPuppet.class);
                Empower empower = ability(rich, Empower.class);
                if (hex == null || totem == null || puppet == null || empower == null) {
                    problems.add("the shaman lacks an ability");
                } else {
                    if (!AutocastState.isOn(ru, hex) || !AutocastState.isOn(ru, totem))
                        problems.add("a fresh shaman does not autocast Hex and Blood Totem");
                    if (puppet.getAutoRule() != AutoRule.MANUAL || empower.getAutoRule() != AutoRule.MANUAL)
                        problems.add("Spirit Puppet or Empower is autocast (" + puppet.getAutoRule() + ", " + empower.getAutoRule() + ")");
                    Essence.add(rich, 10);
                    Unit.fullResetBehaviours(ru);   // AutoCaster.look skips a unit walking a move order
                    AutoCaster.look(level, rich, ru);
                    if (!Hex.isHexed(foe) || Essence.get(rich) != 5)
                        problems.add("one AutoCaster look did not hex the enemy beside the shaman (essence " + Essence.get(rich) + ")");
                    Essence.add(poor, 4);
                    Unit.fullResetBehaviours((Unit) poor);
                    ((Unit) poor).getTargetGoal().setTarget(foe2);
                    boolean cast = AutoCaster.look(level, poor, (Unit) poor);
                    if (cast || Hex.isHexed(foe2) || Essence.get(poor) != 4)
                        problems.add("a shaman with 4 essence autocast (essence " + Essence.get(poor) + ")");
                    if (((Unit) poor).getTargetGoal().getTarget() != foe2)
                        problems.add("a short shaman's look dropped its attack target");
                }
            } catch (RuntimeException ex) {
                problems.add("threw " + ex);
            } finally {
                cleanUp(helper, made, List.of());
            }
            finish(helper, problems);
        });
    }

    /**
     * lovish (Oct 31, "Count as support"): the shaman marches with a bot's army, but it does not count toward the army a
     * bot must field before Tier 2 (BotTuning.t2MinArmy), like every other healer and caster - none of them is an
     * AttackerUnit, so the army list never held them. Both gates that read the army are pinned: the research gate
     * (t2Ready, through t2ArmyOut, its patience path included) and the easy bot's T1 pause for the T2 lab (wantsT2Save).
     */
    @GameTest(template = ARENA, timeoutTicks = 20, batch = BATCH)
    public static void voodoo_shaman_counts_as_support_not_army_toward_a_bots_tier_2_minimum(GameTestHelper helper) {
        List<Entity> made = new ArrayList<>();
        String a = owner(helper, "bota");
        Mob shaman = still(helper, EntityRegistrar.VOODOO_SHAMAN_UNIT.get(), a, 3, 3, made);
        Mob brute = still(helper, EntityRegistrar.BRUTE_UNIT.get(), a, 6, 3, made);
        if (made.size() < 2) {
            spawnFailed(helper, made, "the shaman and the brute");
            return;
        }
        helper.runAfterDelay(2, () -> {
            List<String> problems = new ArrayList<>();
            try {
                int fighters = BotPlayer.t2Fighters(List.of(shaman, brute));
                if (fighters != 1)
                    problems.add("a shaman and a brute count as " + fighters + " fighters toward the Tier 2 minimum, wanted 1 (the brute)");
                if (BotPlayer.t2Fighters(List.of(brute, brute)) != 2)
                    problems.add("two brutes do not count as 2 fighters toward the Tier 2 minimum");
                // t2MinArmy 2, earliest minute 5: minute 7 wants 2 fighters, minute 11 (patience) wants 1
                if (BotPlayer.t2ArmyOut(7, List.of(shaman, brute), 5, 2))
                    problems.add("the Tier 2 research gate opens for a shaman and a brute with t2MinArmy 2");
                if (!BotPlayer.t2ArmyOut(7, List.of(brute, brute), 5, 2))
                    problems.add("the Tier 2 research gate stays shut for two brutes with t2MinArmy 2");
                if (BotPlayer.t2ArmyOut(11, List.of(shaman), 5, 2))
                    problems.add("the Tier 2 research gate's patience path opens for a lone shaman (half of t2MinArmy 2)");
                if (!BotPlayer.t2ArmyOut(11, List.of(brute), 5, 2))
                    problems.add("the Tier 2 research gate's patience path stays shut for a lone brute (half of t2MinArmy 2)");
                if (BotPlayer.wantsT2Save(true, true, false, false, List.of(shaman, brute), 2))
                    problems.add("an easy bot with a shaman and a brute stops queuing T1 for its T2 lab with t2MinArmy 2");
                if (!BotPlayer.wantsT2Save(true, true, false, false, List.of(brute, brute), 2))
                    problems.add("an easy bot with two brutes does not hold its T1 queues for its T2 lab with t2MinArmy 2");
            } catch (RuntimeException ex) {
                problems.add("threw " + ex);
            } finally {
                cleanUp(helper, made, List.of());
            }
            finish(helper, problems);
        });
    }

    /**
     * A selected unit whose owner changes (a puppet taken, a puppet given back) drops out of the selection on the client:
     * the rule UnitClientEvents runs every tick for each selected unit.
     */
    @GameTest(template = ARENA, timeoutTicks = 20, batch = BATCH)
    public static void voodoo_selection_drops_a_unit_whose_owner_changed(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        if (!SelectionKeep.ownerFlipped("alice", "bob"))
            problems.add("a unit that went from alice to bob stays selected");
        if (SelectionKeep.ownerFlipped("alice", "alice"))
            problems.add("a unit whose owner did not change is dropped");
        if (SelectionKeep.ownerFlipped(null, "alice"))
            problems.add("a unit seen for the first time is dropped");
        finish(helper, problems);
    }
}
