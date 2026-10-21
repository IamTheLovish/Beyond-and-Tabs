package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.ability.Ability;
import com.solegendary.reignofnether.ability.abilities.AttackGround;
import com.solegendary.reignofnether.ability.abilities.AwakenThicket;
import com.solegendary.reignofnether.ability.abilities.DeepDive;
import com.solegendary.reignofnether.ability.abilities.GrowGrove;
import com.solegendary.reignofnether.ability.abilities.Trample;
import com.solegendary.reignofnether.building.buildings.defence.DefenceTower;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.unit.Concealment;
import com.solegendary.reignofnether.unit.T3ServerEvents;
import com.solegendary.reignofnether.unit.TitanMass;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.units.brass.IronLeviathanEngineUnit;
import com.solegendary.reignofnether.unit.units.monsters.BoneDragonUnit;
import com.solegendary.reignofnether.unit.units.piglins.WarMammothUnit;
import com.solegendary.reignofnether.unit.units.storm.CelestialDragonUnit;
import com.solegendary.reignofnether.unit.units.tide.LeviathanUnit;
import com.solegendary.reignofnether.unit.units.tide.MortarGalleyUnit;
import com.solegendary.reignofnether.unit.units.verdant.WorldTreeWalkerUnit;
import com.solegendary.reignofnether.unit.units.villagers.SunColossusUnit;
import com.solegendary.reignofnether.unit.units.villagers.VindicatorUnit;
import com.solegendary.reignofnether.unit.units.piglins.MarauderUnit;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * Headless tests of the T3 roles (claude/t3-roles.md): each experimental's job, its moment and the feel rules shared
 * by every big body. Own holder next to BigUnitGameTests (same arena template, unique owners, everything inside the
 * test's own arena); every test runs in parallel with the rest.
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class T3RoleGameTests {

    static final String ARENA = SkirmishGameTests.ARENA;

    static String owner(GameTestHelper helper, String tag) {
        return "gametest_t3_" + tag + "_" + helper.absolutePos(BlockPos.ZERO).asLong();
    }

    static Mob spawn(GameTestHelper helper, EntityType<? extends Mob> type, String owner, double x, double z) {
        return BigUnitGameTests.spawn(helper, type, owner, x, z);
    }

    static void finish(GameTestHelper helper, List<String> problems) {
        if (problems.isEmpty())
            helper.succeed();
        else
            helper.fail(String.join("; ", problems));
    }

    // ---- the roster: numbers and roles -----------------------------------------------------------------------------

    /**
     * The roster's rules: no two T3s share an HP total (lovish, Oct 20); no ground T3 slower than a heavy line (0.22)
     * except the summoner and the artillery, whose job is to stay back; the flanker outruns line infantry and is
     * caught by raiders; the flyers are the fastest; and the Mammoth really walks at its own speed (RavagerUnit's
     * aiStep used to reset it to the Ravager's every tick).
     */
    @GameTest(template = ARENA, timeoutTicks = 40)
    public static void t3_roster_has_distinct_health_and_marches_with_the_army(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        List<String> problems = new ArrayList<>();
        float[] hp = { SunColossusUnit.maxHealth, WarMammothUnit.maxHealth, BoneDragonUnit.maxHealth, WorldTreeWalkerUnit.maxHealth,
            LeviathanUnit.maxHealth, CelestialDragonUnit.maxHealth, IronLeviathanEngineUnit.maxHealth };
        String[] names = { "Sun Colossus", "War Mammoth", "Bone Dragon", "World Tree Walker", "Leviathan", "Celestial Dragon", "Iron Leviathan Engine" };
        for (int i = 0; i < hp.length; i++)
            for (int j = i + 1; j < hp.length; j++)
                if (hp[i] == hp[j])
                    problems.add(names[i] + " and " + names[j] + " share " + hp[i] + " HP");
        float line = VindicatorUnit.movementSpeed, raider = MarauderUnit.movementSpeed, heavyLine = 0.22f;
        if (WarMammothUnit.movementSpeed <= line || WarMammothUnit.movementSpeed >= raider)
            problems.add("the flanker should outrun line infantry (" + line + ") and be caught by raiders (" + raider + "): " + WarMammothUnit.movementSpeed);
        if (SunColossusUnit.movementSpeed < heavyLine || LeviathanUnit.movementSpeed < heavyLine)
            problems.add("the anchor and the amphibious flanker must march with a heavy line (>= " + heavyLine + "): "
                + SunColossusUnit.movementSpeed + " / " + LeviathanUnit.movementSpeed);
        if (BoneDragonUnit.movementSpeed < 0.28f || CelestialDragonUnit.movementSpeed < 0.28f)
            problems.add("the flyers should be the fastest T3s");
        if (WorldTreeWalkerUnit.movementSpeed >= heavyLine || IronLeviathanEngineUnit.movementSpeed >= heavyLine)
            problems.add("the summoner and the artillery stay back: they are the slow ones by design");
        var mammoth = EntityRegistrar.WAR_MAMMOTH_UNIT.get().create(level);
        if (mammoth == null) {
            helper.fail("could not create the War Mammoth");
            return;
        }
        try {
            mammoth.aiStep();   // the Ravager's and RavagerUnit's aiStep rewrite the base speed: the Mammoth's own must win
            double base = mammoth.getAttributeBaseValue(Attributes.MOVEMENT_SPEED);
            if (Math.abs(base - WarMammothUnit.movementSpeed) > 1.0e-4)
                problems.add("after an aiStep the Mammoth walks at " + base + " instead of its own " + WarMammothUnit.movementSpeed);
            if (mammoth.getAbilities().get().stream().noneMatch(a -> a instanceof Trample t && t.canTargetEntities && t.range >= WarMammothUnit.TRAMPLE_LENGTH))
                problems.add("Trample should be an aimed charge with the lane's reach");
        } finally {
            mammoth.discard();
        }
        finish(helper, problems);
    }

    // ---- mass: the right of way ------------------------------------------------------------------------------------

    /**
     * TitanMass: every ground T3 is unpushable and shoves a small body aside in its own push pass, while a body of its
     * own size is pushed as vanilla pushes; the small body's own pass (vanilla Entity.push) moves itself, never the titan.
     */
    @GameTest(template = ARENA, timeoutTicks = 40)
    public static void t3_titans_have_the_right_of_way(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        List<String> problems = new ArrayList<>();
        List<Mob> all = new ArrayList<>();
        try {
            for (var type : List.of(EntityRegistrar.WAR_MAMMOTH_UNIT.get(), EntityRegistrar.SUN_COLOSSUS_UNIT.get(),
                    EntityRegistrar.WORLD_TREE_WALKER_UNIT.get(), EntityRegistrar.LEVIATHAN_UNIT.get(), EntityRegistrar.IRON_LEVIATHAN_ENGINE_UNIT.get())) {
                Mob titan = type.create(level);
                if (titan == null) {
                    problems.add("could not create " + type.getDescriptionId());
                    continue;
                }
                all.add(titan);
                if (titan.isPushable())
                    problems.add(type.getDescriptionId() + " is pushable");
            }
            if (all.isEmpty() || !(all.get(0) instanceof WarMammothUnit mammoth)) {
                helper.fail("could not create the War Mammoth");
                return;
            }
            // a Vindicator, not a villager: workers are unpushable by their own rule, so vanilla's push leaves them be
            Mob small = EntityRegistrar.VINDICATOR_UNIT.get().create(level);
            Mob peer = EntityRegistrar.SUN_COLOSSUS_UNIT.get().create(level);
            if (small == null || peer == null) {
                helper.fail("could not create the push test bodies");
                return;
            }
            all.add(small);
            all.add(peer);
            BlockPos p = helper.absolutePos(new BlockPos(8, 2, 8));
            mammoth.moveTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, 0, 0);
            small.moveTo(p.getX() + 1.5, p.getY(), p.getZ() + 0.5, 0, 0);
            peer.moveTo(p.getX() + 2.5, p.getY(), p.getZ() + 0.5, 0, 0);
            if (!TitanMass.isSmallBody(mammoth, small) || TitanMass.isSmallBody(mammoth, peer))
                problems.add("a Vindicator is a small body to a Mammoth, a Colossus is not");
            // the titan's own pass: the small body is shoved, the titan does not move
            mammoth.setDeltaMovement(Vec3.ZERO);
            small.setDeltaMovement(Vec3.ZERO);
            if (!TitanMass.doPush(mammoth, small))
                problems.add("the Mammoth's push pass should shove a small body");
            double shoved = small.getDeltaMovement().horizontalDistance();
            if (shoved < TitanMass.SHOVE * 0.9 || shoved > TitanMass.SHOVE * 1.1)
                problems.add("a shoved small body should move " + TitanMass.SHOVE + " a tick, moved " + shoved);
            if (mammoth.getDeltaMovement().horizontalDistanceSqr() > 1.0e-8)
                problems.add("the Mammoth moved while shoving");
            // the small body's own pass (vanilla): it steps away, the titan stays
            small.setDeltaMovement(Vec3.ZERO);
            small.push(mammoth);
            if (small.getDeltaMovement().horizontalDistanceSqr() <= 0)
                problems.add("vanilla's push should still move the small body away from the titan");
            if (mammoth.getDeltaMovement().horizontalDistanceSqr() > 1.0e-8)
                problems.add("a small body's push pass moved the Mammoth");
            // a peer: vanilla's push between them (the Colossus is unpushable too, so neither moves - and nothing is shoved)
            if (TitanMass.doPush(mammoth, peer))
                problems.add("a body of the titan's own size must not be shoved");
        } finally {
            for (Mob m : all)
                m.discard();
        }
        finish(helper, problems);
    }

    // ---- the Flanker ------------------------------------------------------------------------------------------------

    /**
     * War Mammoth: Trample waits out its wind-up (nobody hurt, no movement), then charges the lane to the aimed spot -
     * every enemy on the way is hit once, a friend on it and an enemy off it are not, and the Mammoth travels; a wall
     * stops a second charge short, and a charge never starts on top of one already running.
     */
    @GameTest(template = ARENA, timeoutTicks = 60)
    public static void war_mammoth_trample_charges_the_lane_and_a_wall_stops_it(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = owner(helper, "mammoth"), foeOwner = owner + "_foe";
        List<String> problems = new ArrayList<>();
        SkirmishGameTests.openGround(helper, 1, 2, 14, 14);
        var vind = EntityRegistrar.VINDICATOR_UNIT.get();
        var mammoth = (WarMammothUnit) spawn(helper, EntityRegistrar.WAR_MAMMOTH_UNIT.get(), owner, 3.5, 8.5);
        Mob foeA = spawn(helper, vind, foeOwner, 7.5, 8.5), foeB = spawn(helper, vind, foeOwner, 10.5, 8.5);
        Mob friend = spawn(helper, vind, owner, 9.5, 8.5), off = spawn(helper, vind, foeOwner, 7.5, 13.5);
        List<Mob> all = new ArrayList<>();
        for (Mob m : new Mob[] { mammoth, foeA, foeB, friend, off })
            if (m != null)
                all.add(m);
        try {
            if (all.size() != 5) {
                helper.fail("could not create the Trample test units");
                return;
            }
            for (Mob m : all)
                if (m instanceof Unit u)
                    u.setHoldPosition(true);   // nobody wanders during the charge
            float a0 = foeA.getHealth(), b0 = foeB.getHealth(), f0 = friend.getHealth(), o0 = off.getHealth();
            double x0 = mammoth.getX();
            Vec3 aim = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(12, 2, 8)));
            if (!mammoth.startTrample(aim))
                problems.add("the charge did not start");
            if (mammoth.startTrample(aim))
                problems.add("a second charge started while charging");
            for (int t = 0; t < WarMammothUnit.TRAMPLE_WINDUP_TICKS; t++)
                mammoth.tickTrample(level);
            if (foeA.getHealth() < a0 || mammoth.getX() - x0 > 0.5)
                problems.add("the Mammoth moved or hit during its wind-up");
            for (int t = 0; t < 40 && mammoth.isTrampling(); t++)
                mammoth.tickTrample(level);
            if (mammoth.isTrampling())
                problems.add("the charge should have ended");
            if (foeA.getHealth() >= a0 || foeB.getHealth() >= b0)
                problems.add("the charge should hit both enemies in the lane (hits " + mammoth.lastTrampleHits + ")");
            if (mammoth.lastTrampleHits != 2)
                problems.add("each enemy in the lane is hit once: " + mammoth.lastTrampleHits + " hits");
            if (friend.getHealth() < f0)
                problems.add("the charge hit a friend");
            if (off.getHealth() < o0)
                problems.add("the charge hit an enemy off the lane");
            if (mammoth.getX() - x0 < 6)
                problems.add("the Mammoth should have charged down the lane (moved " + (mammoth.getX() - x0) + ")");
            // a wall line is the flanker's counter: a second Mammoth's charge stops at it
            for (int y = 2; y <= 5; y++)
                for (int z = 4; z <= 13; z++)
                    level.setBlock(helper.absolutePos(new BlockPos(9, y, z)), Blocks.STONE.defaultBlockState(), 3);
            var mammoth2 = (WarMammothUnit) spawn(helper, EntityRegistrar.WAR_MAMMOTH_UNIT.get(), owner, 3.5, 8.5);
            if (mammoth2 == null) {
                helper.fail("could not create the second Mammoth");
                return;
            }
            all.add(mammoth2);
            mammoth2.startTrample(aim);
            for (int t = 0; t < 40 && mammoth2.isTrampling(); t++)
                mammoth2.tickTrample(level);
            double wallX = helper.absolutePos(new BlockPos(9, 2, 8)).getX();
            if (mammoth2.getX() + mammoth2.getBbWidth() * 0.5 > wallX + 0.05)
                problems.add("the wall should stop the charge (Mammoth at " + mammoth2.getX() + ", wall at " + wallX + ")");
            if (mammoth2.isTrampling())
                problems.add("a charge that hit a wall should end");
        } finally {
            for (Mob m : all)
                m.discard();
        }
        finish(helper, problems);
    }

    // ---- the Anchor -------------------------------------------------------------------------------------------------

    /**
     * Sun Colossus: the Dawn Aegis - a friend within its radius takes x0.8 on a real hit against the same hit on a
     * friend outside it; an enemy inside it and the Colossus itself get nothing.
     */
    @GameTest(template = ARENA, timeoutTicks = 60)
    public static void sun_colossus_aegis_shields_friends_near_it(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = owner(helper, "aegis"), foeOwner = owner + "_foe";
        List<String> problems = new ArrayList<>();
        var vill = EntityRegistrar.VILLAGER_UNIT.get();
        var colossus = (SunColossusUnit) spawn(helper, EntityRegistrar.SUN_COLOSSUS_UNIT.get(), owner, 4.5, 4.5);
        Mob near = spawn(helper, vill, owner, 7.5, 6.5), far = spawn(helper, vill, owner, 4.5, 14.5);
        Mob foeNear = spawn(helper, vill, foeOwner, 6.5, 2.5), foeFar = spawn(helper, vill, foeOwner, 13.5, 14.5);
        List<Mob> all = new ArrayList<>();
        for (Mob m : new Mob[] { colossus, near, far, foeNear, foeFar })
            if (m != null)
                all.add(m);
        try {
            if (all.size() != 5) {
                helper.fail("could not create the aegis test units");
                return;
            }
            T3ServerEvents.trackColossus(colossus);
            if (!T3ServerEvents.underAegis(near))
                problems.add("a friend " + near.distanceTo(colossus) + " blocks from the Colossus should be under its aegis");
            if (T3ServerEvents.underAegis(far) || T3ServerEvents.underAegis(foeNear) || T3ServerEvents.underAegis(colossus))
                problems.add("a friend outside the aegis, an enemy inside it and the Colossus itself must get nothing");
            if (T3ServerEvents.aegisTakenMult(near) != SunColossusUnit.AEGIS_TAKEN_MULT)
                problems.add("the aegis should give " + SunColossusUnit.AEGIS_TAKEN_MULT + ", gives " + T3ServerEvents.aegisTakenMult(near));
            // real hits: the same enemy type strikes a friend inside and one outside
            float n0 = near.getHealth(), f0 = far.getHealth();
            near.hurt(level.damageSources().mobAttack(foeFar), 10f);
            far.hurt(level.damageSources().mobAttack(foeFar), 10f);
            float lostNear = n0 - near.getHealth(), lostFar = f0 - far.getHealth();
            if (lostFar <= 0 || lostNear > lostFar * 0.85f || lostNear < lostFar * 0.75f)
                problems.add("a friend under the aegis should take ~80% damage (" + lostNear + " vs " + lostFar + ")");
            float c0 = colossus.getHealth();
            colossus.hurt(level.damageSources().generic(), 10f);
            if (Math.abs(c0 - colossus.getHealth() - 10f) > 0.5f)
                problems.add("the Colossus itself should take full damage, took " + (c0 - colossus.getHealth()));
        } finally {
            for (Mob m : all)
                m.discard();
        }
        finish(helper, problems);
    }

    // ---- the Air striker --------------------------------------------------------------------------------------------

    /**
     * Bone Dragon: a breath hits every enemy inside its cone (and withers them), never a friend in it, an enemy behind
     * the dragon or one off the cone's angle; the damage is its attack damage, x1.3 at night.
     */
    @GameTest(template = ARENA, timeoutTicks = 60)
    public static void bone_dragon_breath_withers_the_cone(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = owner(helper, "breath"), foeOwner = owner + "_foe";
        List<String> problems = new ArrayList<>();
        SkirmishGameTests.openGround(helper, 1, 1, 14, 14);
        var vind = EntityRegistrar.VINDICATOR_UNIT.get();
        var dragon = (BoneDragonUnit) spawn(helper, EntityRegistrar.BONE_DRAGON_UNIT.get(), owner, 3.5, 8.5);
        Mob inA = spawn(helper, vind, foeOwner, 8.5, 8.5), inB = spawn(helper, vind, foeOwner, 11.5, 10.0);
        Mob friend = spawn(helper, vind, owner, 9.5, 7.5), behind = spawn(helper, vind, foeOwner, 1.5, 8.5);
        Mob offAngle = spawn(helper, vind, foeOwner, 6.5, 13.5);
        List<Mob> all = new ArrayList<>();
        for (Mob m : new Mob[] { dragon, inA, inB, friend, behind, offAngle })
            if (m != null)
                all.add(m);
        try {
            if (all.size() != 6) {
                helper.fail("could not create the breath test units");
                return;
            }
            dragon.setPos(dragon.getX(), dragon.getY() + 4, dragon.getZ());   // in the air over the line
            float a0 = inA.getHealth(), b0 = inB.getHealth(), f0 = friend.getHealth(), be0 = behind.getHealth(), o0 = offAngle.getHealth();
            int hit = dragon.breathe(level, new Vec3(1, 0, 0));
            if (hit != 2)
                problems.add("the breath should hit the two enemies in its cone, hit " + hit);
            if (inA.getHealth() >= a0 || inB.getHealth() >= b0)
                problems.add("an enemy in the cone was not hurt");
            if (!inA.hasEffect(MobEffects.WITHER) || !inB.hasEffect(MobEffects.WITHER))
                problems.add("the breath should wither what it burns");
            if (friend.getHealth() < f0)
                problems.add("the breath hit a friend in the cone");
            if (behind.getHealth() < be0 || offAngle.getHealth() < o0)
                problems.add("the breath reached an enemy behind the dragon or off its cone");
            float expected = BoneDragonUnit.attackDamage * (dragon.isNightFor() ? BoneDragonUnit.NIGHT_DAMAGE_MULT : 1f);
            if (Math.abs(dragon.getUnitAttackDamage() - expected) > 0.01f)
                problems.add("the breath's damage should be " + expected + " (night: " + dragon.isNightFor() + "), is " + dragon.getUnitAttackDamage());
            if (BoneDragonUnit.attackRange > WitheringFogRange())
                problems.add("the breath's reach must not exceed the Withering Fog's aim");
        } finally {
            for (Mob m : all)
                m.discard();
        }
        finish(helper, problems);
    }

    static int WitheringFogRange() {
        return com.solegendary.reignofnether.ability.abilities.WitheringFog.RANGE;
    }

    // ---- the Air caster ---------------------------------------------------------------------------------------------

    /**
     * Celestial Dragon: a sky bolt hits its target for the full bolt and chains to the nearest CHAIN_MAX other enemies
     * within CHAIN_RADIUS for half of it - never a friend beside the target, never an enemy out of the chain's reach.
     */
    @GameTest(template = ARENA, timeoutTicks = 60)
    public static void celestial_dragon_bolt_chains_through_the_group(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = owner(helper, "chain"), foeOwner = owner + "_foe";
        List<String> problems = new ArrayList<>();
        SkirmishGameTests.openGround(helper, 1, 1, 14, 14);
        var vind = EntityRegistrar.VINDICATOR_UNIT.get();
        var dragon = (CelestialDragonUnit) spawn(helper, EntityRegistrar.CELESTIAL_DRAGON_UNIT.get(), owner, 3.5, 8.5);
        Mob target = spawn(helper, vind, foeOwner, 9.5, 8.5);
        List<Mob> links = new ArrayList<>();
        for (double[] at : new double[][] { { 11.5, 8.5 }, { 9.5, 11.5 }, { 13.5, 8.5 }, { 9.5, 4.3 } })
            links.add(spawn(helper, vind, foeOwner, at[0], at[1]));
        Mob friend = spawn(helper, vind, owner, 10.5, 9.5), farFoe = spawn(helper, vind, foeOwner, 9.5, 14.9);
        List<Mob> all = new ArrayList<>();
        for (Mob m : new Mob[] { dragon, target, friend, farFoe })
            if (m != null)
                all.add(m);
        for (Mob m : links)
            if (m != null)
                all.add(m);
        try {
            if (all.size() != 8) {
                helper.fail("could not create the chain test units");
                return;
            }
            dragon.setPos(dragon.getX(), dragon.getY() + 4, dragon.getZ());
            float t0 = target.getHealth(), f0 = friend.getHealth(), far0 = farFoe.getHealth();
            float[] l0 = new float[links.size()];
            for (int i = 0; i < links.size(); i++)
                l0[i] = links.get(i).getHealth();
            if (!dragon.strike(target))
                problems.add("the bolt did not hurt its target");
            float dealt = t0 - target.getHealth();
            if (Math.abs(dealt - CelestialDragonUnit.attackDamage) > 0.5f)
                problems.add("the bolt should deal " + CelestialDragonUnit.attackDamage + " to its target, dealt " + dealt);
            if (dragon.lastChainHits != CelestialDragonUnit.CHAIN_MAX)
                problems.add("the chain should reach " + CelestialDragonUnit.CHAIN_MAX + " enemies, reached " + dragon.lastChainHits);
            int hurtLinks = 0;
            for (int i = 0; i < links.size(); i++) {
                float lost = l0[i] - links.get(i).getHealth();
                if (lost > 0) {
                    hurtLinks++;
                    if (Math.abs(lost - CelestialDragonUnit.attackDamage * CelestialDragonUnit.CHAIN_FRACTION) > 0.5f)
                        problems.add("a chained enemy should take half the bolt, took " + lost);
                }
            }
            if (hurtLinks != CelestialDragonUnit.CHAIN_MAX)
                problems.add("exactly " + CelestialDragonUnit.CHAIN_MAX + " of the four enemies in reach should be chained, " + hurtLinks + " were");
            if (friend.getHealth() < f0)
                problems.add("the chain jumped to a friend");
            if (farFoe.getHealth() < far0)
                problems.add("the chain reached an enemy out of its radius");
        } finally {
            for (Mob m : all)
                m.discard();
        }
        finish(helper, problems);
    }

    // ---- the Summoner -----------------------------------------------------------------------------------------------

    /**
     * World Tree Walker: Grow Grove grows two of its owner's treants round it (awakened: free, temporary), heals a hurt
     * friend in its shade on the bloom, caps at four, and the whole grove sinks back when the walker goes.
     */
    @GameTest(template = ARENA, timeoutTicks = 60)
    public static void world_tree_walker_grows_a_grove_that_withers_with_it(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = owner(helper, "grove");
        List<String> problems = new ArrayList<>();
        SkirmishGameTests.openGround(helper, 1, 1, 14, 14);
        var walker = (WorldTreeWalkerUnit) spawn(helper, EntityRegistrar.WORLD_TREE_WALKER_UNIT.get(), owner, 8.5, 8.5);
        Mob friend = spawn(helper, EntityRegistrar.VINDICATOR_UNIT.get(), owner, 10.5, 8.5);
        List<Mob> all = new ArrayList<>();
        List<LivingEntity> grown = new ArrayList<>();
        for (Mob m : new Mob[] { walker, friend })
            if (m != null)
                all.add(m);
        try {
            if (all.size() != 2) {
                helper.fail("could not create the grove test units");
                return;
            }
            if (walker.getAbilities().get().stream().noneMatch(a -> a instanceof GrowGrove))
                problems.add("the walker has no Grow Grove");
            friend.setHealth(friend.getMaxHealth() - 30f);
            float h0 = friend.getHealth();
            int grew = walker.growGrove(level);
            if (grew != WorldTreeWalkerUnit.TREANTS_PER_GROVE || walker.liveTreants() != grew)
                problems.add("a bloom should grow " + WorldTreeWalkerUnit.TREANTS_PER_GROVE + " treants, grew " + grew + " (" + walker.liveTreants() + " live)");
            if (Math.abs(friend.getHealth() - h0 - WorldTreeWalkerUnit.BLOOM_HEAL) > 0.01f)
                problems.add("the bloom should heal a hurt friend in the shade by " + WorldTreeWalkerUnit.BLOOM_HEAL + ", healed " + (friend.getHealth() - h0));
            // the server's unit list sees a spawn at once (the level's entity sections only on the next tick - CI 5d68bff3)
            grownTreants(owner, walker, grown);
            if (grown.size() != grew)
                problems.add("the grown treants should be awakened treants of the walker's owner, within its grove: found " + grown.size());
            walker.growGrove(level);
            if (walker.liveTreants() != WorldTreeWalkerUnit.TREANT_CAP)
                problems.add("a second bloom should fill the grove to " + WorldTreeWalkerUnit.TREANT_CAP + ", has " + walker.liveTreants());
            if (walker.growGrove(level) != 0)
                problems.add("a full grove grew more");
            grown.clear();
            grownTreants(owner, walker, grown);
            if (grown.size() != WorldTreeWalkerUnit.TREANT_CAP)
                problems.add("the full grove should stand round the walker: found " + grown.size());
            // the grove goes with the walker
            walker.discard();
            all.remove(walker);
            for (LivingEntity le : grown)
                if (!le.isRemoved())
                    problems.add("a treant outlived its walker");
        } finally {
            for (Mob m : all)
                m.discard();
            for (LivingEntity le : grown)
                if (!le.isRemoved())
                    le.discard();
        }
        finish(helper, problems);
    }

    /** Adds {@code owner}'s live awakened treants within 10 blocks of the walker to {@code out}. */
    static void grownTreants(String owner, Mob walker, List<LivingEntity> out) {
        for (LivingEntity le : com.solegendary.reignofnether.unit.UnitServerEvents.getAllUnits())
            if (le.isAlive() && AwakenThicket.isAwakened(le) && le instanceof Unit u && owner.equals(u.getOwnerName())
                    && le.distanceToSqr(walker) <= 10 * 10)
                out.add(le);
    }

    // ---- the Amphibious flanker -------------------------------------------------------------------------------------

    /**
     * Leviathan: Deep Dive refuses on dry land; from the water it goes under (hidden from enemies, not from its owner,
     * unhurt), and after its travel it surfaces at the water's edge nearest the aimed spot with a slam that hurts the
     * enemy standing there; a save made mid-dive loads on the surface. A dive never starts on top of another.
     */
    @GameTest(template = ARENA, timeoutTicks = 140)
    public static void leviathan_deep_dive_surfaces_by_the_water_with_a_slam(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = owner(helper, "dive"), foeOwner = owner + "_foe";
        SkirmishGameTests.openGround(helper, 0, 0, 15, 15);
        // a sunken pool on the west side: water at x 2..5, z 6..10 in the stone floor (a basin, so it never spreads)
        for (int x = 2; x <= 5; x++)
            for (int z = 6; z <= 10; z++) {
                level.setBlock(helper.absolutePos(new BlockPos(x, 0, z)), Blocks.STONE.defaultBlockState(), 3);
                level.setBlock(helper.absolutePos(new BlockPos(x, 1, z)), Blocks.WATER.defaultBlockState(), 3);
            }
        var lev = EntityRegistrar.LEVIATHAN_UNIT.get().create(level);
        var dry = EntityRegistrar.LEVIATHAN_UNIT.get().create(level);
        var foe = EntityRegistrar.VINDICATOR_UNIT.get().create(level);
        if (lev == null || dry == null || foe == null) {
            helper.fail("could not create the dive test units");
            return;
        }
        BlockPos a = helper.absolutePos(new BlockPos(3, 1, 8)), land = helper.absolutePos(new BlockPos(7, 2, 8)), aim = helper.absolutePos(new BlockPos(12, 2, 8));
        lev.moveTo(a.getX() + 1.0, a.getY(), a.getZ() + 0.5, -90, 0);   // its feet in the pool, its 3.5-wide body inside it
        foe.moveTo(land.getX() + 0.5, land.getY(), land.getZ() + 0.5, 90, 0);
        lev.setOwnerName(owner);
        dry.setOwnerName(owner);
        foe.setOwnerName(foeOwner);
        foe.setHoldPosition(true);
        level.addFreshEntity(lev);
        level.addFreshEntity(foe);
        float[] foeBefore = { 0 };
        helper.runAfterDelay(2, () -> {
            List<String> problems = new ArrayList<>();
            try {
                if (lev.getAbilities().get().stream().noneMatch(ab -> ab instanceof DeepDive))
                    problems.add("the Leviathan has no Deep Dive");
                // dry land: never a dive (the body is never added, so it is dry by construction)
                dry.moveTo(aim.getX() + 0.5, aim.getY(), aim.getZ() + 0.5, 0, 0);
                if (dry.startDive(Vec3.atBottomCenterOf(a)))
                    problems.add("a Leviathan on dry land dived");
                foeBefore[0] = foe.getHealth();
                // back to its feet in the pool: two ticks of FloatGoal may have bobbed it up toward the surface
                lev.moveTo(a.getX() + 1.0, a.getY(), a.getZ() + 0.5, -90, 0);
                lev.setDeltaMovement(Vec3.ZERO);
                if (!lev.isInTheWater())
                    problems.add("the Leviathan standing in the pool should count as in the water");
                if (!lev.startDive(Vec3.atBottomCenterOf(aim)))
                    problems.add("the Leviathan in the water refused to dive toward a spot 9 blocks off");
                if (lev.startDive(Vec3.atBottomCenterOf(aim)))
                    problems.add("a diving Leviathan started a second dive");
                if (!LeviathanUnit.isSubmerged(lev) || !Concealment.isHiddenFrom(lev, foeOwner))
                    problems.add("a diving Leviathan should be hidden from its enemies");
                if (Concealment.isHiddenFrom(lev, owner))
                    problems.add("a diving Leviathan should stay visible to its owner");
                float hp = lev.getHealth();
                lev.hurt(level.damageSources().generic(), 10f);
                lev.hurt(level.damageSources().mobAttack(foe), 10f);
                if (lev.getHealth() < hp)
                    problems.add("a diving Leviathan took damage: " + hp + " -> " + lev.getHealth());
                // a save mid-dive comes back on the surface
                CompoundTag saved = lev.saveWithoutId(new CompoundTag());
                var loaded = EntityRegistrar.LEVIATHAN_UNIT.get().create(level);
                if (loaded != null) {
                    loaded.load(saved);
                    if (LeviathanUnit.isSubmerged(loaded) || loaded.isInvisible())
                        problems.add("a Leviathan saved mid-dive should load on the surface, visible");
                    loaded.discard();
                }
                if (!problems.isEmpty()) {
                    helper.fail(String.join("; ", problems));
                }
            } catch (RuntimeException ex) {
                lev.discard();
                foe.discard();
                dry.discard();
                throw ex;
            }
        });
        helper.runAfterDelay(2 + LeviathanUnit.DIVE_MIN_TICKS + 8, () -> {
            List<String> problems = new ArrayList<>();
            try {
                if (LeviathanUnit.isSubmerged(lev) || lev.isInvisible())
                    problems.add("the Leviathan should have surfaced");
                if (Concealment.isHiddenFrom(lev, foeOwner))
                    problems.add("a surfaced Leviathan should be targetable again");
                double dx = lev.getX() - (land.getX() + 0.5), dz = lev.getZ() - (land.getZ() + 0.5);
                if (dx * dx + dz * dz > 2.5 * 2.5)
                    problems.add("the Leviathan should surface at the water's edge nearest the spot (" + land.getX() + ".5), rose at " + lev.getX() + " / " + lev.getZ());
                if (foe.getHealth() >= foeBefore[0])
                    problems.add("the enemy at the surfacing spot was not slammed (hits " + lev.lastSurfaceHits + ")");
                finish(helper, problems);
            } finally {
                lev.discard();
                foe.discard();
                dry.discard();
            }
        });
    }

    // ---- the Siege artillery ----------------------------------------------------------------------------------------

    /**
     * Iron Leviathan Engine: the longest gun on the field - past every tower, past the Mortar Galley, and past its own
     * eyes; Attack Ground reaches as far as the cannon; the unit's attribute says the same; it has the right of way.
     */
    @GameTest(template = ARENA, timeoutTicks = 40)
    public static void iron_leviathan_engine_outranges_every_tower(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        List<String> problems = new ArrayList<>();
        for (DefenceTower.Kind kind : DefenceTower.Kind.values())
            if (IronLeviathanEngineUnit.attackRange <= kind.range)
                problems.add("the engine's cannon (" + IronLeviathanEngineUnit.attackRange + ") does not outrange the " + kind + " tower (" + kind.range + ")");
        if (IronLeviathanEngineUnit.attackRange <= MortarGalleyUnit.attackRange)
            problems.add("siege artillery should outrange the Mortar Galley (" + MortarGalleyUnit.attackRange + ")");
        if (IronLeviathanEngineUnit.SIGHT_RANGE >= IronLeviathanEngineUnit.attackRange)
            problems.add("the engine should reveal less far than it shoots");
        var engine = EntityRegistrar.IRON_LEVIATHAN_ENGINE_UNIT.get().create(level);
        if (engine == null) {
            helper.fail("could not create the engine");
            return;
        }
        try {
            if (Math.abs(engine.getAttackRange() - IronLeviathanEngineUnit.attackRange) > 0.01f)
                problems.add("the engine's attack range attribute is " + engine.getAttackRange());
            boolean ground = false;
            for (Ability ab : engine.getAbilities().get())
                if (ab instanceof AttackGround && Math.abs(ab.range - IronLeviathanEngineUnit.attackRange) < 0.01f)
                    ground = true;
            if (!ground)
                problems.add("Attack Ground should reach as far as the cannon");
            if (engine.isPushable())
                problems.add("the engine should have the right of way");
        } finally {
            engine.discard();
        }
        finish(helper, problems);
    }
}
