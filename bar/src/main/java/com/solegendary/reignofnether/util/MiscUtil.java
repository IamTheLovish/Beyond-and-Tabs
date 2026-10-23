package com.solegendary.reignofnether.util;


import com.mojang.datafixers.util.Pair;
import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.ability.heroAbilities.enchanter.ProtectiveEnchantment;
import com.solegendary.reignofnether.ability.heroAbilities.necromancer.BloodMoon;
import com.solegendary.reignofnether.alliance.AlliancesClient;
import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.blocks.BlockClientEvents;
import com.solegendary.reignofnether.blocks.WraithSnowLayerBlock;
import com.solegendary.reignofnether.building.*;
import com.solegendary.reignofnether.building.addon.GarrisonableBuildingAddon;
import com.solegendary.reignofnether.building.buildings.placements.CustomBuildingPlacement;
import com.solegendary.reignofnether.building.buildings.shared.AbstractBridge;
import com.solegendary.reignofnether.cursor.CursorClientEvents;
import com.solegendary.reignofnether.faction.Faction;
import com.solegendary.reignofnether.faction.Factions;
import com.solegendary.reignofnether.keybinds.Keybindings;
import com.solegendary.reignofnether.nether.NetherBlocks;
import com.solegendary.reignofnether.orthoview.OrthoviewClientEvents;
import com.solegendary.reignofnether.registrars.*;
import com.solegendary.reignofnether.blocks.NightCircleMode;
import com.solegendary.reignofnether.unit.Checkpoint;
import com.solegendary.reignofnether.unit.NonUnitServerEvents;
import com.solegendary.reignofnether.unit.Relationship;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.goals.AbstractMeleeAttackUnitGoal;
import com.solegendary.reignofnether.unit.interfaces.AttackerUnit;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.units.monsters.BoggedUnit;
import com.solegendary.reignofnether.unit.units.monsters.PhantomSummon;
import com.solegendary.reignofnether.unit.units.monsters.WraithUnit;
import com.solegendary.reignofnether.unit.units.neutral.BeeUnit;
import com.solegendary.reignofnether.unit.units.piglins.GhastUnit;
import com.solegendary.reignofnether.unit.units.piglins.WitherSkeletonUnit;
import com.solegendary.reignofnether.unit.units.villagers.VillagerUnit;
import com.solegendary.reignofnether.unit.units.villagers.VillagerUnitProfession;
import com.solegendary.reignofnether.unit.units.villagers.WindcallerUnit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.AbstractFish;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.apache.commons.lang3.text.WordUtils;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector3d;
import org.lwjgl.glfw.GLFW;

import java.util.*;
import java.util.List;
import java.util.function.Predicate;

import javax.annotation.Nullable;

import static com.solegendary.reignofnether.blocks.BlockUtils.isLeafBlock;
import static com.solegendary.reignofnether.blocks.BlockUtils.isLogBlock;
import static net.minecraft.util.Mth.cos;
import static net.minecraft.util.Mth.sin;
import static net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.MULTIPLY_BASE;
import static net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.MULTIPLY_TOTAL;


public class MiscUtil {

    private static final Random RANDOM = new Random();

    private static final int[] DYE_COLORS = {
            0xF0F0F0, 0xEB8844, 0xC354CD, 0x6689D3,
            0xDECF2A, 0x41CD34, 0xD88198, 0x434343,
            0xABABAB, 0x287697, 0x7B2FBE, 0x253193,
            0x51301A, 0x3B511A, 0xB3312C, 0x1E1B1B
    };

    public static void shootFirework(Level level, Vec3 vec3) {
        CompoundTag explosion = new CompoundTag();
        int color = DYE_COLORS[level.random.nextInt(DYE_COLORS.length)];
        explosion.put("Colors", new IntArrayTag(new int[]{color}));
        explosion.putByte("Type", (byte) 0);
        ListTag explosions = new ListTag();
        explosions.add(explosion);
        CompoundTag explosionsAndFlight = new CompoundTag();
        explosionsAndFlight.put("Explosions", explosions);
        explosionsAndFlight.putByte("Flight", (byte) 0b1);
        CompoundTag fireworks = new CompoundTag();
        fireworks.put("Fireworks", explosionsAndFlight);
        ItemStack itemStack = new ItemStack(Items.FIREWORK_ROCKET);
        itemStack.setTag(fireworks);
        FireworkRocketEntity entity = new FireworkRocketEntity(level, null, vec3.x, vec3.y(), vec3.z, itemStack);
        level.addFreshEntity(entity);
        entity.moveTo(vec3);
    }

    public static void doRandomFireworkExplosion(Level level, Vec3 vec3) {
        CompoundTag explosion = new CompoundTag();
        int color = DYE_COLORS[level.random.nextInt(DYE_COLORS.length)];
        explosion.put("Colors", new IntArrayTag(new int[]{color}));
        byte type = (byte) level.random.nextInt(5);
        explosion.putByte("Type", type);
        ListTag explosions = new ListTag();
        explosions.add(explosion);
        CompoundTag explosionsAndFlight = new CompoundTag();
        explosionsAndFlight.put("Explosions", explosions);
        explosionsAndFlight.putByte("Flight", (byte) 0b1);
        CompoundTag fireworks = new CompoundTag();
        fireworks.put("Fireworks", explosionsAndFlight);
        ItemStack itemStack = new ItemStack(Items.FIREWORK_ROCKET);
        itemStack.setTag(fireworks);
        FireworkRocketEntity entity = new FireworkRocketEntity(level, null, vec3.x, vec3.y(), vec3.z, itemStack);
        level.addFreshEntity(entity);
        entity.moveTo(vec3);
        entity.lifetime = 0;
    }

    // prevent flying mobs from floating above trees and buildings (or they're effectively unreachable)
    // also used to move the camera Y pos up and down to prevent clipping inside of blocks
    public static boolean isGroundBlock(Level level, BlockPos bp) {
        BlockState bs = level.getBlockState(bp);
        if (isLogBlock(bs) || isLeafBlock(bs) || bs.isAir() ||
                BuildingUtils.isPosInsideAnyBuilding(level.isClientSide(), bp))
            return false;
        return true;
    }

    public static void addUnitCheckpoint(Unit unit, BlockPos blockPos, boolean green) {
        if (((Entity) unit).level().isClientSide()) {
            if (!Keybindings.shiftMod.isDown())
                unit.getCheckpoints().clear();
            unit.getCheckpoints().add(new Checkpoint(blockPos, green));
        }
    }

    public static void addUnitCheckpoint(Unit unit, int entityId, boolean green) {
        Level level = ((Entity) unit).level();
        if (level.isClientSide()) {
            if (!Keybindings.shiftMod.isDown())
                unit.getCheckpoints().clear();
            unit.getCheckpoints().add(new Checkpoint(level.getEntity(entityId), green));
        }
    }

    // returns a list of all BlockPos values between two points
    public static List<BlockPos> getLine2D(BlockPos start, BlockPos end) {
        List<BlockPos> result = new ArrayList<>();

        int x0 = start.getX();
        int z0 = start.getZ();
        int x1 = end.getX();
        int z1 = end.getZ();

        int dx = Math.abs(x1 - x0);
        int dz = Math.abs(z1 - z0);

        int sx = x0 < x1 ? 1 : -1;
        int sz = z0 < z1 ? 1 : -1;

        int err = dx - dz;

        while (true) {
            result.add(new BlockPos(x0, 0, z0));

            if (x0 == x1 && z0 == z1) break;

            int e2 = 2 * err;
            if (e2 > -dz) {
                err -= dz;
                x0 += sx;
            }
            if (e2 < dx) {
                err += dx;
                z0 += sz;
            }
        }
        return result;
    }

    // excludes trees and buildings
    public static BlockPos getHighestGroundBlock(Level level, BlockPos blockPos) {
        int y = level.getMaxBuildHeight() - 1;   // top block (getHeight() is the column size, not a y: the battlefield is 176 high from y 48)
        BlockState bs;
        BlockPos bp;
        do {
            bp = new BlockPos(blockPos.getX(), y, blockPos.getZ());
            bs = level.getBlockState(bp);
            y -= 1;
        } while ((bs.isAir() ||
                BuildingUtils.isPosInsideAnyBuilding(level.isClientSide, bp) ||
                bs.getBlock() == Blocks.LIGHT ||
                bs.getBlock() == Blocks.STRUCTURE_VOID ||
                (!isSolidBlocking(level, bp) &&
                        bs.getFluidState().isEmpty()) ||
                bs.is(BlockTags.LEAVES) ||
                bs.is(BlockTags.LOGS) || bs.is(BlockTags.PLANKS)) && y > level.getMinBuildHeight() + 1);
        return new BlockPos(blockPos.getX(), y, blockPos.getZ());
    }

    public static BlockPos getHighestNonAirBlock(Level level, BlockPos blockPos, boolean ignoreLeaves, boolean ignoreStructureVoid) {
        int y = level.getMaxBuildHeight() - 1;   // top block (getHeight() is the column size, not a y: the battlefield is 176 high from y 48)
        BlockState bs;
        BlockPos bp;
        do {
            bp = new BlockPos(blockPos.getX(), y, blockPos.getZ());
            bs = level.getBlockState(bp);
            if (!ignoreStructureVoid && bs.getBlock() == Blocks.STRUCTURE_VOID) {
                break;
            }
            y -= 1;
        } while ((bs.isAir() ||
                bs.getBlock() == Blocks.LIGHT ||
                (!isSolidBlocking(level, bp) && bs.getFluidState().isEmpty()) ||
                (ignoreLeaves && bs.is(BlockTags.LEAVES))) && y > level.getMinBuildHeight() + 1);
        return new BlockPos(blockPos.getX(), y, blockPos.getZ());
    }

    public static BlockPos getHighestNonAirBlock(Level level, BlockPos blockPos, boolean ignoreLeaves) {
        return getHighestNonAirBlock(level, blockPos, ignoreLeaves, true);
    }

    public static BlockPos getHighestNonAirBlock(Level level, BlockPos blockPos) {
        return getHighestNonAirBlock(level, blockPos, false);
    }

    public static boolean listContainsObjectValue(List<Object> objs, String obj) {
        for (Object o : objs) {
            if (!o.equals(obj)) continue;
            return true;
        }
        return false;
    }

    public static boolean isLeftClickDown(Minecraft MC) {
        return GLFW.glfwGetMouseButton(MC.getWindow().getWindow(), GLFW.GLFW_MOUSE_BUTTON_1) == GLFW.GLFW_PRESS;
    }

    public static boolean isRightClickDown(Minecraft MC) {
        return GLFW.glfwGetMouseButton(MC.getWindow().getWindow(), GLFW.GLFW_MOUSE_BUTTON_2) == GLFW.GLFW_PRESS;
    }

    // converts a 2d screen position to a 3d world position while in ortho view
    public static Vector3d screenPosToWorldPos(Minecraft MC, int mouseX, int mouseY) {
        if (MC.player == null) {
            return new Vector3d(0, 0, 0);
        }
        int winWidth = MC.getWindow().getGuiScaledWidth();
        int winHeight = MC.getWindow().getGuiScaledHeight();

        // at winHeight=240, zoom=10, screen is 20 blocks high, so PTB=240/20=24
        float pixelsToBlocks = winHeight / OrthoviewClientEvents.getZoom();

        // make mouse coordinate origin centre of screen
        float x = (mouseX - (float) winWidth / 2) / pixelsToBlocks;
        float y = 0;
        float z = (mouseY - (float) winHeight / 2) / pixelsToBlocks;

        double camRotYRads = Math.toRadians(OrthoviewClientEvents.getCamRotY());
        z = z / (float) (Math.sin(camRotYRads));

        Vec2 XZRotated = MyMath.rotateCoords(x, z, OrthoviewClientEvents.getCamRotX());

        // for some reason position is off by some y coord so just move it down manually
        return new Vector3d(
                MC.player.xo - XZRotated.x,
                MC.player.yo + y + 1.5f,
                MC.player.zo - XZRotated.y
        );
    }

    // distance to dropoff point but with more lenient Y range
    public static boolean isMobInRangeOfPos(BlockPos pos, LivingEntity mob, float range) {
        Vec2 pos2d = new Vec2(pos.getX() + 0.5f, pos.getZ() + 0.5f);
        Vec2 mob2d = new Vec2((float) mob.getX(), (float) mob.getZ());

        return pos.distToCenterSqr(mob.getX(), mob.getY(), mob.getZ()) < range * range ||
                (pos2d.distanceToSqr(mob2d) < range * range && (pos.getY() - mob.getY()) < 16);
    }

    // returns a random order of orthogonally adjacent blocks
    public static ArrayList<BlockPos> findAdjacentBlocks(BlockPos originPos, Predicate<BlockPos> condition) {
        ArrayList<BlockPos> adjBps = new ArrayList<>();
        ArrayList<BlockPos> retBps = new ArrayList<>();

        adjBps.add(originPos.above());
        adjBps.add(originPos.below());
        adjBps.add(originPos.north());
        adjBps.add(originPos.south());
        adjBps.add(originPos.east());
        adjBps.add(originPos.west());

        Collections.shuffle(adjBps);
        for (BlockPos bp : adjBps)
            if (condition.test(bp))
                retBps.add(bp);
        return retBps;
    }

    public static LivingEntity findClosestAttackableEntity(Mob unitMob, float range, ServerLevel level) {
        Vector3d unitPosition = new Vector3d(unitMob.position().x, unitMob.position().y, unitMob.position().z);
        var pos = new Vec3(unitPosition.x, unitPosition.y, unitPosition.z);
        boolean neutralAggro = unitMob.level().getGameRules().getRule(GameRuleRegistrar.NEUTRAL_AGGRO).get();
        AABB aabb = new AABB(
                unitPosition.x - range,
                unitPosition.y - range,
                unitPosition.z - range,
                unitPosition.x + range,
                unitPosition.y + range,
                unitPosition.z + range
        );
        var rawEntities = level.getEntitiesOfClass(LivingEntity.class, aabb);
        boolean isMelee = unitMob instanceof AttackerUnit aUnit && aUnit.getAttackGoal() instanceof AbstractMeleeAttackUnitGoal;
        // Hot path in big battles (every attacker, every few ticks). Same result as before, cheaper:
        //  - entities outside the sphere (the cube's corners) are dropped up front; every pass below required
        //    distance <= range anyway, so they could never be returned;
        //  - each sort key is computed once (it used to be recomputed, with a sqrt, inside every comparison).
        // List.sort is stable, so ties keep the same relative order as the original sort.
        record Candidate(LivingEntity entity, double key) {}
        ArrayList<Candidate> candidates = new ArrayList<>(rawEntities.size());
        for (LivingEntity e : rawEntities) {
            double dist = e.position().distanceTo(pos);
            if (dist > range)
                continue;
            boolean isMeleeAgainstFlyer = isMelee && e instanceof Unit unit && unit.isFlyingUnit();
            // deprioritise over actual enemy units
            if (e instanceof PhantomSummon || (e instanceof Unit unit && unit.isScout()) || isMeleeAgainstFlyer || (e instanceof BeeUnit))
                dist += 100;
            candidates.add(new Candidate(e, dist));
        }
        candidates.sort(Comparator.comparingDouble(Candidate::key));
        ArrayList<LivingEntity> entities = new ArrayList<>(candidates.size());
        for (Candidate c : candidates)
            entities.add(c.entity());

        // Determine priority effect filter for specific unit types
        Predicate<LivingEntity> priorityFilter = null;
        if (unitMob instanceof BoggedUnit) {
            priorityFilter = e -> !e.hasEffect(MobEffects.POISON);
        } else if (unitMob instanceof WraithUnit) {
            priorityFilter = e -> !e.hasEffect(MobEffectRegistrar.FEARFUL.get());
        } else if (unitMob instanceof WitherSkeletonUnit) {
            priorityFilter = e -> e.hasEffect(MobEffects.WITHER);
        } else if (unitMob instanceof WindcallerUnit) {
            priorityFilter = e -> !e.hasEffect(MobEffects.LEVITATION);
        } else if (unitMob instanceof com.solegendary.reignofnether.unit.units.verdant.WispChoirUnit) {
            // anti-air: a flyer in range beats a closer walker
            priorityFilter = com.solegendary.reignofnether.unit.units.verdant.WispChoirUnit::isFlyer;
        } else if (unitMob instanceof com.solegendary.reignofnether.unit.units.tide.FlakSwivelUnit
                || unitMob instanceof com.solegendary.reignofnether.unit.units.storm.WindMonkUnit
                || unitMob instanceof com.solegendary.reignofnether.unit.units.brass.FlakCartUnit) {
            // the Tidewrought's, the Stormcallers' and the Brassworks' T1 anti-air, same rule
            priorityFilter = com.solegendary.reignofnether.unit.units.verdant.WispChoirUnit::isFlyer;
        }

        Vec3 unitVec = new Vec3(unitPosition.x, unitPosition.y, unitPosition.z);

        Predicate<LivingEntity> baseFilter = e ->
                e.position().distanceTo(unitVec) <= range &&
                e.level().getWorldBorder().isWithinBounds(e.blockPosition());

        // Try priority pass first, then fall back to base filter
        List<Predicate<LivingEntity>> passes = priorityFilter != null
                ? List.of(baseFilter.and(priorityFilter), baseFilter)
                : List.of(baseFilter);

        for (Predicate<LivingEntity> filter : passes) {
            for (LivingEntity entity : entities) {
                if (filter.test(entity) &&
                        isIdleOrMoveAttackable(unitMob, entity, neutralAggro) &&
                        hasLineOfSightForAttacks(unitMob, entity) &&
                        !(entity instanceof Unit unit && unit.isGarrisoned()) &&
                        // cloaked, under cover, tunnelling or diving: one rule (Concealment.isConcealed), so a new way
                        // to hide is never forgotten here (the Leviathan's Deep Dive was, when the three were listed by hand)
                        !com.solegendary.reignofnether.unit.Concealment.isConcealed(entity)) {
                    return entity;
                }
            }
        }
        return null;
    }

    // does not cover explicit attack commands
    private static boolean isIdleOrMoveAttackable(Mob unitMob, LivingEntity targetEntity, boolean neutralAggro) {
        Relationship rs = Relationship.NEUTRAL;
        if (unitMob instanceof Unit) {
            rs = UnitServerEvents.getUnitToEntityRelationship((Unit) unitMob, targetEntity);

            // don't aggro against blood moon enemies as a ghast so that buildings don't get friendly fired
            if (targetEntity instanceof Unit targetUnit &&
                    targetUnit.getOwnerName().equals(BloodMoon.ENEMY_NAME) &&
                    unitMob instanceof GhastUnit)
                return false;

            // don't target vanilla units of the same faction
            if (!(targetEntity instanceof Unit) && NonUnitServerEvents.getNonUnitFaction(targetEntity).equals(Factions.getFaction((Unit) unitMob)))
                return false;
        }

        if (targetEntity instanceof Player player && (player.isCreative() || player.isSpectator()))
            return false;

        // If the relationship is FRIENDLY, do not allow the attack
        if (rs == Relationship.FRIENDLY)
            return false;

        // Prevents certain attacks based on specific unit and goal conditions
        if (targetEntity instanceof Unit unit &&
                unit.isFlyingUnit() &&
                unitMob instanceof AttackerUnit attackerUnit &&
                attackerUnit.getAttackGoal() instanceof AbstractMeleeAttackUnitGoal) {
            return false;
        }
        boolean isPassiveNonUnit = !(targetEntity instanceof Unit) &&
                (targetEntity instanceof Animal || targetEntity instanceof AbstractFish || targetEntity instanceof Villager);



        // Checks if neutral units can be attacked based on neutralAggro flag and other conditions
        boolean canAttackNeutral =
                rs == Relationship.NEUTRAL && neutralAggro &&
                        !(targetEntity instanceof Vex) &&
                        !(targetEntity instanceof ArmorStand) &&
                        !(targetEntity instanceof PhantomSummon) &&
                        !isPassiveNonUnit;

        return (rs == Relationship.HOSTILE || canAttackNeutral) &&
                targetEntity.getId() != unitMob.getId();
    }


    /** Calls of {@link #findClosestAttackableBuilding} so far (the [Stress] benchmark checks its idle units really scan). */
    public static long attackableBuildingScans = 0;

    /**
     * The closest building within {@code range} (to its attack spot, getClosestGroundPos) that this unit may pick a
     * fight with by itself; on a tie the one earlier in the building list. Every idle attacker asks this every 4 ticks
     * when no enemy unit is in reach, so it reads only the BuildingGrid cells its reach touches, and skips a building
     * whose footprint ring is already farther than the best so far before the relationship check and the footprint
     * walk (block reads). It used to do both for every building on the map (Oct 21 perf plan 3.1): ~1,000 enemy
     * buildings meant ~1-2 ms per call, ~25 calls per tick per 100 idle units.
     */
    public static BuildingPlacement findClosestAttackableBuilding(Mob unitMob, float range) {
        attackableBuildingScans++;
        boolean clientSide = unitMob.level().isClientSide();
        if (BuildingGrid.legacyScans)
            return BuildingScansLegacy.findClosestAttackableBuilding(unitMob, range, BuildingUtils.getBuildingsList(clientSide));
        return findClosestAttackableBuilding(unitMob, range, BuildingGrid.of(clientSide));
    }

    /** {@link #findClosestAttackableBuilding(Mob, float)} over the given grid (the GameTests pass their own). */
    public static BuildingPlacement findClosestAttackableBuilding(Mob unitMob, float range, BuildingGrid grid) {
        ClosestAttackableBuilding search = new ClosestAttackableBuilding(unitMob, range);
        BlockPos at = search.at;
        // the attack spot is at most GROUND_RING outside the footprint, and must be closer than range
        int reach = (int) Math.min(4096, Math.ceil(range)) + BuildingGrid.GROUND_RING + 1;
        grid.forEachNear(at.getX() - reach, at.getZ() - reach, at.getX() + reach, at.getZ() + reach, search);
        return search.best;
    }

    /** One findClosestAttackableBuilding search: the old loop's body, for candidates in any order (ties by list index). */
    private static final class ClosestAttackableBuilding implements BuildingGrid.Visitor {
        final Mob unitMob;
        final BlockPos at;
        double closestDist;
        BuildingPlacement best = null;
        int bestIndex = Integer.MAX_VALUE;

        ClosestAttackableBuilding(Mob unitMob, float range) {
            this.unitMob = unitMob;
            this.at = unitMob.blockPosition();
            this.closestDist = range;
        }

        @Override
        public void visit(BuildingPlacement building, int index) {
            // getClosestGroundPos returns a spot in the ring GROUND_RING round the footprint, at the footprint's lowest
            // y: the distance to that box is a floor for the distance to the spot, with no block reads
            double floor = Math.sqrt(gapSq(at.getX(), building.minCorner.getX() - BuildingGrid.GROUND_RING, building.maxCorner.getX() + BuildingGrid.GROUND_RING)
                + gapSq(at.getY(), building.minCorner.getY(), building.minCorner.getY())
                + gapSq(at.getZ(), building.minCorner.getZ() - BuildingGrid.GROUND_RING, building.maxCorner.getZ() + BuildingGrid.GROUND_RING));
            if (floor > closestDist)
                return;
            // Check if the building is attackable, taking into account the relationship
            if (!isBuildingAutoAttackable(unitMob, building) || building.getBuilding() instanceof AbstractBridge)
                return;
            BlockPos attackPos = building.getClosestGroundPos(at, 1);
            double dist = Math.sqrt(at.distSqr(attackPos));
            // the old list-order loop kept the first of equally close buildings: so does the lower list index here
            if (dist < closestDist || (dist == closestDist && best != null && index < bestIndex)) {
                closestDist = dist;
                best = building;
                bestIndex = index;
            }
        }

        private static double gapSq(int p, int lo, int hi) {
            double g = p < lo ? (double) lo - p : p > hi ? (double) p - hi : 0;
            return g * g;
        }
    }

    /**
     * The enemy building AttackerUnit.attackMoveNearestEnemyBuilding marches on: the closest (centre to the unit) that
     * is not its own, not an ally's, not neutral and attackable; on a tie the one earlier in the list, as the old
     * stable sort gave it. Through BuildingGrid's ring search instead of a copy, filter and sort of the whole list.
     */
    @Nullable
    public static BuildingPlacement nearestEnemyBuildingForAttackMove(Unit unit) {
        boolean clientSide = ((Entity) unit).level().isClientSide();
        if (BuildingGrid.legacyScans)
            return BuildingScansLegacy.nearestEnemyBuildingForAttackMove(unit, BuildingUtils.getBuildingsList(clientSide));
        return nearestEnemyBuildingForAttackMove(unit, BuildingGrid.of(clientSide));
    }

    /** {@link #nearestEnemyBuildingForAttackMove(Unit)} over the given grid (the GameTests pass their own). */
    @Nullable
    public static BuildingPlacement nearestEnemyBuildingForAttackMove(Unit unit, BuildingGrid grid) {
        String owner = unit.getOwnerName();
        Vec3 p = ((Entity) unit).position();
        Predicate<BuildingPlacement> enemy = b -> !owner.equals(b.ownerName) &&
            !AlliancesServerEvents.isAllied(owner, b.ownerName) &&
            !b.ownerName.isBlank() &&
            b.isAttackable();
        // a position off the number line (NaN/infinite) makes every key equal: the old stable sort kept list order
        if (!Double.isFinite(p.x) || !Double.isFinite(p.y) || !Double.isFinite(p.z))
            return grid.firstMatching(enemy);
        return grid.nearestByCentre(p.x, p.y, p.z, 0.5, Double.POSITIVE_INFINITY,
            b -> b.centrePos.distToCenterSqr(p), enemy);
    }

    // neutral -> neutral ❌
    // owned -> neutral ✔ (if neutral aggro on)
    // neutral -> owned ✔ (if neutral aggro on)
    // owned -> owned ✔ (if hostile)
    // public for BuildingScansLegacy (the pre-grid reference scan)
    public static boolean isBuildingAutoAttackable(Mob unitMob, BuildingPlacement building) {
        if (!building.isAttackable())
            return false;
        if (building instanceof CustomBuildingPlacement cb && !cb.getBuilding().drawAggro)
            return false;
        // walls and gates: units walk round them; only siege, heavies and experimentals pick a fight with one
        // (design/defences.md). Traps are never targets while hidden (TrapPlacement.isAttackable)
        if ((building.getBuilding() instanceof com.solegendary.reignofnether.building.buildings.defence.WallSegment
                || building.getBuilding() instanceof com.solegendary.reignofnether.building.buildings.defence.WallGate)
                && !com.solegendary.reignofnether.building.buildings.defence.WallSegment.drawsAggroFrom(unitMob))
            return false;

        Relationship relationship = UnitServerEvents.getUnitToBuildingRelationship((Unit) unitMob, building);

        if (relationship == Relationship.FRIENDLY) {
            return false;
        }
        boolean neutralAggro = unitMob.level().getGameRules().getRule(GameRuleRegistrar.NEUTRAL_AGGRO).get();

        if (relationship == Relationship.NEUTRAL && neutralAggro)
            return true;

        return relationship == Relationship.HOSTILE;
    }


    private static boolean hasLineOfSightForAttacks(Mob mob, LivingEntity targetEntity) {
        return mob.hasLineOfSight(targetEntity) || mob instanceof GhastUnit ||
                (mob instanceof Unit unit && GarrisonableBuildingAddon.getGarrison((Unit) mob) != null);
    }

    public static <T extends Entity> List<T> getEntitiesWithinRange(Vec3 pos, float range, Class<T> entityType, Level level) {
        return getEntitiesWithinRange(new Vector3d(pos.x, pos.y, pos.z), range, entityType, level);
    }

    public static <T extends Entity> List<T> getEntitiesWithinRange(Vector3d pos, float range, Class<T> entityType, Level level) {
        AABB aabb = new AABB(
                pos.x - range,
                pos.y - range,
                pos.z - range,
                pos.x + range,
                pos.y + range,
                pos.z + range
        );

        if (level != null) {
            List<T> entities = level.getEntitiesOfClass(entityType, aabb);
            List<T> entitiesInRange = new ArrayList<>();

            for (Entity entity : entities)
                if (entity.position().distanceTo(new Vec3(pos.x, pos.y, pos.z)) <= range &&
                        entity.level().getWorldBorder().isWithinBounds(entity.blockPosition()))
                    entitiesInRange.add((T) entity);

            return entitiesInRange;
        } else
            return new ArrayList<>();
    }

    public static <T extends Entity> List<T> getEntitiesWithinAABB(AABB aabb, Class<T> entityType, Level level) {
        if (level != null) {
            List<T> entities = level.getEntitiesOfClass(entityType, aabb);
            List<T> entitiesInRange = new ArrayList<>();

            for (Entity entity : entities)
                if (entity.level().getWorldBorder().isWithinBounds(entity.blockPosition()))
                    entitiesInRange.add((T) entity);

            return entitiesInRange;
        } else
            return new ArrayList<>();
    }


    // accepts a list of strings to draw at the top left to track debug data
    //MiscUtil.drawDebugStrings(evt.getMatrixStack(), MC.font, new String[] {
    //});
    public static void drawDebugStrings(GuiGraphics guiGraphics, Font font, String[] strings) {
        int y = 200 - (strings.length * 10);
        for (String str : strings) {
            guiGraphics.drawString(font, str, 0, y, 0xFFFFFF);
            y += 10;
        }
    }

    public static Relationship getClientsideRelationship(String playerName1, String playerName2) {
        if (playerName1.isEmpty() || playerName2.isEmpty())
            return Relationship.NEUTRAL;
        else if (playerName1.equals(playerName2))
            return Relationship.OWNED;
        else if (AlliancesClient.isAllied(playerName1, playerName2))
            return Relationship.FRIENDLY;
        else
            return Relationship.HOSTILE;
    }

    // lightens or darkens a hex RGB value
    /** Blends {@code t} of colour {@code b} into {@code a} (both 0xRRGGBB). */
    public static int mixHexRGB(int a, int b, float t) {
        int r = Math.round(((a >> 16) & 0xFF) * (1 - t) + ((b >> 16) & 0xFF) * t);
        int g = Math.round(((a >> 8) & 0xFF) * (1 - t) + ((b >> 8) & 0xFF) * t);
        int bl = Math.round((a & 0xFF) * (1 - t) + (b & 0xFF) * t);
        return (r << 16) | (g << 8) | bl;
    }

    public static int shadeHexRGB(int col, float mult) {
        int red = (col >> 16) & 0xFF;
        int green = (col >> 8) & 0xFF;
        int blue = (col) & 0xFF;

        if (mult > 1) { // prevent colours going > 255 (0xFF)
            red = Math.min(Math.round(red * mult), 0xFF);
            green = Math.min(Math.round(green * mult), 0xFF);
            blue = Math.min(Math.round(blue * mult), 0xFF);
        } else { // prevent colours going < 0
            red = Math.max(Math.round(red * mult), 0);
            green = Math.max(Math.round(green * mult), 0);
            blue = Math.max(Math.round(blue * mult), 0);
        }
        return (red << 16) | (green << 8) | (blue);
    }

    // convert col from RGB -> BGR (for some reason setPixelRGBA reads them backwards)
    public static int reverseHexRGB(int col) {
        int red = (col >> 16) & 0xFF;
        int green = (col >> 8) & 0xFF;
        int blue = (col) & 0xFF;

        return (blue << 16) | (green << 8) | (red);
    }

    // get a float that ranges between 0-1 (1 cycle per periodMs milliseconds) based on the system clock
// used for oscillating an alpha value to make a rendered object pulse
    public static float getOscillatingFloat(double min, double max) {
        return getOscillatingFloat(min, max, 0, 1000);
    }

    public static float getOscillatingFloat(double min, double max, long timeOffset) {
        return getOscillatingFloat(min, max, timeOffset, 1000);
    }

    public static float getOscillatingFloat(double min, double max, long timeOffset, long periodMs) {
        long ms = System.currentTimeMillis() + timeOffset;
        double t = ms % periodMs; // position within the current cycle, 0..periodMs
        double half = periodMs / 2.0;
        double quarter = periodMs / 4.0;
        double msOsc = (Math.abs(t - half) / quarter) - 1; // +-1 along linear scale
        msOsc = (Math.asin(msOsc) / Math.PI) + 0.5d; // 0-1 along sin scale
        msOsc *= (max - min);
        msOsc += min;
        return (float) msOsc;
    }

    public static double round(double value, int precision) {
        int scale = (int) Math.pow(10, precision);
        return (double) Math.round(value * scale) / scale;
    }

    // gets the unit vector in the direction of player facing (same as camera)
    // calcs from https://stackoverflow.com/questions/65897792/3d-vector-coordinates-from-x-and-y-rotation
    public static Vector3d getPlayerLookVector(Minecraft MC) {
        if (MC.player == null)
            return new Vector3d(0, 0, 0);
        float a = (float) Math.toRadians(MC.player.getYRot());
        float b = (float) Math.toRadians(MC.player.getXRot());
        return new Vector3d(-cos(b) * sin(a), -sin(b), cos(b) * cos(a));
    }

    // get the world position of the centre of the screen (as though the cursor was over it)
    public static Vec3 getOrthoviewCentreWorldPos(Minecraft MC) {
        Vector3d centrePosd = MiscUtil.screenPosToWorldPos(MC,
                MC.getWindow().getGuiScaledWidth() / 2,
                MC.getWindow().getGuiScaledHeight() / 2
        );
        Vector3d lookVector = MiscUtil.getPlayerLookVector(MC);
        Vector3d cursorWorldPosNear = MyMath.addVector3d(centrePosd, lookVector, -200);
        Vector3d cursorWorldPosFar = MyMath.addVector3d(centrePosd, lookVector, 200);
        return CursorClientEvents.getRefinedCursorWorldPos(cursorWorldPosNear, cursorWorldPosFar);
    }

    public static Set<BlockPos> getRangeIndicatorCircleBlocks(BlockPos centrePos, int radius, Level level) {
        return getRangeIndicatorCircleBlocks(centrePos, radius, level, false);
    }

    // get the tops of all blocks which are of at a certain horizontal distance away from the centrePos
    public static Set<BlockPos> getRangeIndicatorCircleBlocks(BlockPos centrePos, int radius, Level level, boolean isNightSource) {
        if (radius <= 0)
            return Set.of();

        Set<BlockPos> circleBps;
        if (BlockClientEvents.nightCircleMode == NightCircleMode.NO_OVERLAPS && isNightSource)
            circleBps = MiscUtil.CircleUtil.getCircleWithCulledOverlaps(centrePos, radius, BlockClientEvents.nightSourceOrigins);
        else
            circleBps = MiscUtil.CircleUtil.getCircle(centrePos, radius);

        return new HashSet<>(getHeightAdjustedBlockPoses(level, circleBps.stream().toList()));
    }

    // like getRangeIndicatorCircleBlocks but returns ALL blocks in the circle, not just on the edge
    public static Set<BlockPos> getRangeIndicatorFilledCircleBlocks(BlockPos centrePos, int radius, Level level) {
        if (radius <= 0)
            return Set.of();

        ArrayList<BlockPos> bps = new ArrayList<>();

        for (int x = -radius; x < radius; x++) {
            for (int z = -radius; z < radius; z++) {
                BlockPos bp = new BlockPos(centrePos.getX() + x, centrePos.getY(), centrePos.getZ() + z);
                if (bp.distToCenterSqr(centrePos.getX(), centrePos.getY(), centrePos.getZ()) < radius * radius) {
                    bps.add(bp);
                }
            }
        }
        return new HashSet<>(getHeightAdjustedBlockPoses(level, bps));
    }

    // given a 2d set of blockPoses, adjust them so they are of the topmost ground block within 3 blocks
    private static List<BlockPos> getHeightAdjustedBlockPoses(Level level, List<BlockPos> bps) {
        ArrayList<BlockPos> returnBps = new ArrayList<>();
        for (BlockPos bp : bps) {
            for (int i = 0; i < 3; i++) {
                int x = bp.getX();
                int z = bp.getZ();
                if (i == 1)
                    x += 1;
                else if (i == 2)
                    z += 1;

                int groundY = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) - 1;
                BlockPos topBp = new BlockPos(x, groundY, z);
                returnBps.add(topBp);

                int y = 1;
                if (level.getBlockState(topBp).getBlock() instanceof LeavesBlock) {
                    BlockPos bottomBp;
                    BlockState bs;
                    do {
                        bottomBp = topBp.offset(0, -y, 0);
                        bs = level.getBlockState(bottomBp);
                        y += 1;
                    } while (y < 30 && (bs.getBlock() instanceof LeavesBlock || !bs.isSolid()));
                    if (!level.getBlockState(bottomBp.above()).isSolid())
                        returnBps.add(bottomBp);
                }
            }
        }
        return returnBps;
    }

    public static class CircleUtil {

        private static final Map<Integer, Set<BlockPos>> circleCache = new HashMap<>();

        private static final int HASH_GRID_SIZE = 5;

        private static final Map<String, Set<BlockPos>> spatialHashMap = new HashMap<>();

        private static String getHashKey(BlockPos point) {
            int x = point.getX() / HASH_GRID_SIZE;
            int z = point.getZ() / HASH_GRID_SIZE;
            return x + ":" + z;
        }

        private static void addPointToSpatialHashMap(BlockPos point, String hashKey) {
            spatialHashMap.putIfAbsent(hashKey, new HashSet<>());
            spatialHashMap.get(hashKey).add(point);
        }

        public static Set<BlockPos> getCircleWithCulledOverlaps(BlockPos center, int radius, List<Pair<BlockPos, Integer>> overlapSources) {
            if (radius <= 0)
                return new HashSet<>();

            // skip rendering entirely if we are fully inside another circle
            if (BlockClientEvents.nightCircleMode == NightCircleMode.NO_OVERLAPS) {
                for (Pair<BlockPos, Integer> os : overlapSources) {
                    Vec2 centre1 = new Vec2(center.getX(), center.getZ());
                    Vec2 centre2 = new Vec2(os.getFirst().getX(), os.getFirst().getZ());
                    int overlapRange = os.getSecond();
                    if (!center.equals(os.getFirst()) && radius < overlapRange && centre1.distanceToSqr(centre2) < radius * radius)
                        return Set.of();
                }
            }
            Set<BlockPos> circleBps = getCircle(center, radius);

            for (Pair<BlockPos, Integer> os : overlapSources) {
                circleBps.removeIf(bp -> {
                    Vec2 centre1 = new Vec2(bp.getX(), bp.getZ());
                    Vec2 centre2 = new Vec2(os.getFirst().getX(), os.getFirst().getZ());
                    int range = os.getSecond();
                    return !center.equals(os.getFirst()) && centre1.distanceToSqr(centre2) < range * range;
                });
            }
            return circleBps;
        }

        public static Set<BlockPos> getCircle(BlockPos center, int radius) {
            if (radius <= 0)
                return new HashSet<>();

            if (!circleCache.containsKey(radius)) {
                circleCache.put(radius, computeCircleEdge(radius));
            }

            Set<BlockPos> cachedCircle = circleCache.get(radius);
            Set<BlockPos> translatedCircle = new HashSet<>(cachedCircle.size());

            int cx = center.getX();
            int cy = center.getY();
            int cz = center.getZ();

            for (BlockPos pos : cachedCircle) {
                translatedCircle.add(new BlockPos(cx + pos.getX(), cy, cz + pos.getZ()));
            }

            return translatedCircle;
        }

        private static Set<BlockPos> computeCircleEdge(int radius) {
            Set<BlockPos> circleBlocks = new HashSet<>(8 * radius);

            int x = radius;
            int z = 0;
            int decisionOver2 = 1 - x;

            while (x >= z) {
                addSymmetricPoints(circleBlocks, x, z);
                z++;
                if (decisionOver2 <= 0) {
                    decisionOver2 += 2 * z + 1;
                } else {
                    x--;
                    decisionOver2 += 2 * (z - x) + 1;
                }
            }

            return circleBlocks;
        }

        private static void addSymmetricPoints(Set<BlockPos> circleBlocks, int x, int z) {
            circleBlocks.add(new BlockPos(x, 0, z));
            circleBlocks.add(new BlockPos(-x, 0, z));
            circleBlocks.add(new BlockPos(x, 0, -z));
            circleBlocks.add(new BlockPos(-x, 0, -z));

            if (x != z) {
                circleBlocks.add(new BlockPos(z, 0, x));
                circleBlocks.add(new BlockPos(-z, 0, x));
                circleBlocks.add(new BlockPos(z, 0, -x));
                circleBlocks.add(new BlockPos(-z, 0, -x));
            }
        }
    }

    public static FormattedCharSequence fcs(String string) {
        return FormattedCharSequence.forward(string, Style.EMPTY);
    }

    public static FormattedCharSequence fcsIcons(String string) {
        return FormattedCharSequence.forward(string, MyRenderer.iconStyle);
    }

    public static FormattedCharSequence fcs(String string, boolean bold) {
        return FormattedCharSequence.forward(string, bold ? Style.EMPTY.withBold(true) : Style.EMPTY);
    }

    public static FormattedCharSequence fcs(String string, Style style) {
        return FormattedCharSequence.forward(string, style);
    }

    public static boolean isSolidBlocking(Level level, BlockPos bp) {
        BlockState bs = level.getBlockState(bp);
        return !bs.getCollisionShape(level, bp).isEmpty() && bs.isSolid();
    }

    public static String capitaliseAndSpace(String str) {
        String spacedStr = str.replace('_', ' ');
        spacedStr = spacedStr.replace('-', ' ');
        spacedStr = spacedStr.replace('.', ' ');
        return WordUtils.capitalize(spacedStr);
    }

    public static float getMaxAbsorptionAmount(LivingEntity entity) {
        int fortifyingLvl = entity.getItemBySlot(EquipmentSlot.CHEST).getEnchantmentLevel(EnchantmentRegistrar.FORTYIFYING.get());
        if (fortifyingLvl > 0) {
            return fortifyingLvl * ProtectiveEnchantment.MAX_ABSORB_HP_PER_FORTIFYING_LEVEL;
        }
        MobEffectInstance mei = entity.getEffect(MobEffects.ABSORPTION);
        if (mei != null) {
            return (mei.getAmplifier() + 1) * 4.0f;
        }
        return entity.getAbsorptionAmount();
    }

    // eg. Zombie -> entity.reignofnether.zombie
    public static String getEntityIconName(Entity entity) {
        return entity.getType().getDescriptionId()
                .replace("entity.minecraft.", "")
                .replace("entity.reignofnether.", "")
                .replace("_unit", "")
                .toLowerCase();
    }

    // eg. Zombie
    public static String getSimpleEntityName(Entity entity) {
        if (entity instanceof PhantomSummon)
            return "Phantom";

        if (entity instanceof Unit) {
            if (entity.hasCustomName()) {
                return entity.getType()
                        .getDescription()
                        .getString();
            } else {
                return entity.getName()
                        .getString();
            }
        } else if (entity != null) {
            return entity.getName().getString().toLowerCase();
        }
        return "";
    }

    public static boolean isOnNetherTerrain(LivingEntity le) {
        if (le instanceof Unit unit && unit.isFlyingUnit()) {
            BlockPos groundPos = getHighestNonAirBlock(le.level(), le.getOnPos(), false);
            return NetherBlocks.isNetherBlock(le.level(), groundPos);
        }
        return (le.getVehicle() != null && NetherBlocks.isNetherBlock(le.level(), le.getVehicle().getOnPos())) ||
                (NetherBlocks.isNetherBlock(le.level(), le.getOnPos()));
    }

    public static void runServerCommand(MinecraftServer server, String command) {
        server.getCommands().performPrefixedCommand(
                server.createCommandSourceStack(),
                command
        );
    }

    public static void runPlayerCommand(ServerPlayer player, String command) {
        player.server.getCommands().performPrefixedCommand(
                player.createCommandSourceStack(),
                command
        );
    }

    public static ResourceLocation getTextureForBlock(@NotNull Block block) {
        if (block == Blocks.COMMAND_BLOCK)
            return ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "textures/icons/blocks/command_block_back.png");
        BlockState defaultState = block.defaultBlockState();
        BakedModel model = Minecraft.getInstance().getBlockRenderer().getBlockModel(defaultState);
        TextureAtlasSprite sprite = model.getParticleIcon();
        String path = sprite.contents().name().getPath();
        return ResourceLocation.fromNamespaceAndPath("minecraft", "textures/" + path + ".png");
    }

    public static boolean canWearChristmasHat(LivingEntity entity) {
        boolean isFarmer = entity instanceof VillagerUnit vUnit && vUnit.getUnitProfession() == VillagerUnitProfession.FARMER;
        return List.of(
                EntityRegistrar.VILLAGER_UNIT.get(),
                EntityRegistrar.VINDICATOR_UNIT.get(),
                EntityRegistrar.EVOKER_UNIT.get(),
                EntityRegistrar.MILITIA_UNIT.get(),
                EntityRegistrar.PILLAGER_UNIT.get(),
                EntityRegistrar.ZOMBIE_VILLAGER_UNIT.get(),
                EntityRegistrar.ZOMBIE_UNIT.get(),
                EntityRegistrar.HUSK_UNIT.get(),
                EntityRegistrar.DROWNED_UNIT.get(),
                EntityRegistrar.SKELETON_UNIT.get(),
                EntityRegistrar.STRAY_UNIT.get(),
                EntityRegistrar.BOGGED_UNIT.get(),
                EntityRegistrar.GRUNT_UNIT.get(),
                EntityRegistrar.BRUTE_UNIT.get(),
                EntityRegistrar.HEADHUNTER_UNIT.get(),
                EntityRegistrar.WITHER_SKELETON_UNIT.get()
        ).contains(entity.getType()) && !entity.hasItemInSlot(EquipmentSlot.HEAD) && !isFarmer;
    }

    public static boolean isChristmasSeason() {
        Calendar calendar = Calendar.getInstance();
        return calendar.get(Calendar.MONTH) + 1 == 12 && calendar.get(Calendar.DATE) >= 19 && calendar.get(Calendar.DATE) <= 26;
    }

    public static boolean isNewYearsSeason() {
        Calendar calendar = Calendar.getInstance();
        return (calendar.get(Calendar.MONTH) + 1 == 12 && calendar.get(Calendar.DATE) == 31) ||
                (calendar.get(Calendar.MONTH) + 1 == 1 && calendar.get(Calendar.DATE) == 1);
    }
    
    public static boolean isConnected() {
        return Minecraft.getInstance().getConnection() != null;
    }

    public static boolean isSnowLayerBlock(Block block) {
        return block instanceof WraithSnowLayerBlock ||
                block instanceof SnowLayerBlock;
    }

    public static String getFactionName(Faction faction) {
        return I18n.get(String.format("hud.faction.reignofnether.%s", faction.getName()));
    }

    private static final Map<Integer, com.solegendary.reignofnether.player.PlayerPalette.Entry> COLOR_MAP = new HashMap<>();

    static {
        for (com.solegendary.reignofnether.player.PlayerPalette.Entry e : com.solegendary.reignofnether.player.PlayerPalette.ENTRIES) {
            COLOR_MAP.put(e.mapColorId(), e);
            COLOR_MAP.put(e.hex(), e);
        }
    }

    public static String getColorName(int colorIdOrHex, boolean english) {
        com.solegendary.reignofnether.player.PlayerPalette.Entry entry = COLOR_MAP.get(colorIdOrHex);
        if (entry == null)
            entry = com.solegendary.reignofnether.player.PlayerPalette.byMapColorId(MapColor.SNOW.id);
        return english ? entry.name() : I18n.get(String.format("color.reignofnether.%s", entry.name()));
    }

    public static boolean isMagicDamage(DamageSource source) {
        return source.is(DamageTypeTags.WITCH_RESISTANT_TO) || source.is(DamageTypes.ON_FIRE);
    }

    public static <T> T getRandomItem(List<T> list) {
        if (list == null || list.isEmpty()) {
            throw new IllegalArgumentException("List must not be null or empty");
        }
        return list.get(RANDOM.nextInt(list.size()));
    }

    public static <T> T getNextItem(List<T> list, T object) {
        if (list == null || list.isEmpty()) {
            throw new IllegalArgumentException("List must not be null or empty");
        }
        int index = list.indexOf(object);
        return index == -1 ? list.get(0) : list.get((index + 1) % list.size());
    }

    public static <T> T getLastItem(List<T> list, T object) {
        if (list == null || list.isEmpty()) {
            throw new IllegalArgumentException("List must not be null or empty");
        }
        int index = list.indexOf(object);
        return index == -1 ? list.get(0) : list.get((index - 1 + list.size()) % list.size());
    }

    public static String getAttrString(Attribute attr, AttributeModifier modifier) {
        String descId = attr.getDescriptionId();
        boolean isMoveSpeed = attr == Attributes.MOVEMENT_SPEED;
        if (isMoveSpeed) {
            descId = "attribute.reignofnether.tooltip.movement_speed";
        }
        String attrName = Component.translatable(descId).getString();

        boolean isPercentStat = List.of(
                AttributeRegistrar.EVASION_CHANCE.get(),
                AttributeRegistrar.CRITICAL_HIT_CHANCE.get(),
                AttributeRegistrar.EXPLOSIVE_HIT_CHANCE.get(),
                AttributeRegistrar.BUILDING_DAMAGE_BONUS.get(),
                AttributeRegistrar.LIFESTEAL.get(),
                AttributeRegistrar.MANA_ON_HIT.get()
        ).contains(attr);

        String valueStr;
        if (List.of(MULTIPLY_BASE, MULTIPLY_TOTAL).contains(modifier.getOperation()) || isPercentStat) {
            valueStr = formatSigned(modifier.getAmount() * 100) + "%";
        } else {
            valueStr = formatSigned(isMoveSpeed ? modifier.getAmount() * 100 : modifier.getAmount());
        }
        return valueStr + " " + attrName;
    }

    // drops trailing ".0" on whole numbers, always shows a sign
    public static String formatSigned(double value) {
        String num = (value == Math.floor(value))
                ? String.valueOf((int) value)
                : String.valueOf(value);
        return (value >= 0 ? "+" : "") + num;
    }
}
