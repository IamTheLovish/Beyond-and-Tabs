package com.solegendary.reignofnether.unit;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.ability.Ability;
import com.solegendary.reignofnether.alliance.AlliancesClient;
import com.solegendary.reignofnether.blocks.RangeIndicator;
import com.solegendary.reignofnether.building.BuildingClientEvents;
import com.solegendary.reignofnether.building.BuildingPlaceButton;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.BuildingUtils;
import com.solegendary.reignofnether.building.addon.GarrisonableBuildingAddon;
import com.solegendary.reignofnether.building.buildings.placements.ItemShopPlacement;
import com.solegendary.reignofnether.building.buildings.shared.AbstractBridge;
import com.solegendary.reignofnether.building.buildings.shared.AbstractFarm;
import com.solegendary.reignofnether.building.custombuilding.CustomBuildingClientEvents;
import com.solegendary.reignofnether.building.production.ProductionItems;
import com.solegendary.reignofnether.building.production.QueuePopulation;
import com.solegendary.reignofnether.cursor.CursorClientEvents;
import com.solegendary.reignofnether.faction.Factions;
import com.solegendary.reignofnether.fogofwar.FogOfWarClientEvents;
import com.solegendary.reignofnether.gamerules.GameruleClient;
import com.solegendary.reignofnether.hero.HeroServerboundPacket;
import com.solegendary.reignofnether.hud.HudClientEvents;
import com.solegendary.reignofnether.hud.IdleButtons;
import com.solegendary.reignofnether.hud.InspectView;
import com.solegendary.reignofnether.player.CommanderServerEvents;
import com.solegendary.reignofnether.hud.TextInputClientEvents;
import com.solegendary.reignofnether.hud.effecticons.MobEffectIcon;
import com.solegendary.reignofnether.hud.effecticons.MobEffectIcons;
import com.solegendary.reignofnether.items.ItemClientEvents;
import com.solegendary.reignofnether.items.ItemServerboundPacket;
import com.solegendary.reignofnether.items.ItemUtil;
import com.solegendary.reignofnether.keybinds.Keybindings;
import com.solegendary.reignofnether.minimap.MinimapClientEvents;
import com.solegendary.reignofnether.orthoview.OrthoviewClientEvents;
import com.solegendary.reignofnether.player.OwnerColours;
import com.solegendary.reignofnether.player.PlayerClientEvents;
import com.solegendary.reignofnether.player.PlayerServerboundPacket;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.registrars.MobEffectRegistrar;
import com.solegendary.reignofnether.registrars.PacketHandler;
import com.solegendary.reignofnether.research.ResearchClient;
import com.solegendary.reignofnether.resources.ResourceName;
import com.solegendary.reignofnether.resources.ResourceSources;
import com.solegendary.reignofnether.resources.Resources;
import com.solegendary.reignofnether.sandbox.SandboxClientEvents;
import com.solegendary.reignofnether.tutorial.TutorialClientEvents;
import com.solegendary.reignofnether.unit.command.QueueMode;
import com.solegendary.reignofnether.unit.goals.*;
import com.solegendary.reignofnether.unit.interfaces.*;
import com.solegendary.reignofnether.unit.packets.UnitBatchSyncClientboundPacket;
import com.solegendary.reignofnether.unit.packets.UnitActionServerboundPacket;
import com.solegendary.reignofnether.unit.packets.UnitSyncServerboundPacket;
import com.solegendary.reignofnether.unit.units.monsters.*;
import com.solegendary.reignofnether.unit.units.neutral.PolarBearUnit;
import com.solegendary.reignofnether.unit.units.piglins.BruteUnit;
import com.solegendary.reignofnether.unit.units.piglins.GhastUnit;
import com.solegendary.reignofnether.unit.units.piglins.HeadhunterUnit;
import com.solegendary.reignofnether.unit.units.piglins.HoglinUnit;
import com.solegendary.reignofnether.unit.units.villagers.*;
import com.solegendary.reignofnether.util.ArrayUtil;
import com.solegendary.reignofnether.util.MiscUtil;
import com.solegendary.reignofnether.util.MyMath;
import com.solegendary.reignofnether.util.MyRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.entity.EntityMountEvent;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import org.joml.Vector3d;
import org.lwjgl.glfw.GLFW;

import javax.annotation.Nullable;
import java.awt.*;
import java.util.List;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

import static com.solegendary.reignofnether.building.BuildingClientEvents.getPlayerToBuildingRelationship;
import static com.solegendary.reignofnether.cursor.CursorClientEvents.getPreselectedBlockPos;
import static com.solegendary.reignofnether.hud.HudClientEvents.hudSelectedEntity;
import static com.solegendary.reignofnether.unit.Checkpoint.CHECKPOINT_TICKS_FADE;
import static net.minecraftforge.client.event.RenderLevelStageEvent.Stage.AFTER_CUTOUT_BLOCKS;
import static net.minecraftforge.client.event.RenderLevelStageEvent.Stage.AFTER_ENTITIES;


public class UnitClientEvents {

    private static final Minecraft MC = Minecraft.getInstance();

    // list of vecs used in RenderChunkRegionMixin to replace leaf rendering
    private static final int WINDOW_RADIUS = 5; // size of area to hide leaves
    public static final int WINDOW_UPDATE_TICKS_MAX = 5; // size of area to hide leaves
    public static final List<ArrayList<Vec3>> unitWindowVecs = Collections.synchronizedList(new ArrayList<>());
    public static final List<BlockPos> windowPositions = Collections.synchronizedList(new ArrayList<>());
    public static int windowUpdateTicks = UnitClientEvents.WINDOW_UPDATE_TICKS_MAX;

    // command lines for big selections (see the checkpoint loop in onRenderLevel and UnitQueueLinesClient):
    // past GROUP_LINES_FROM units the per-unit lines fade to GROUP_LINE_ALPHA, past GROUP_LINES_NONE they go
    public static final int GROUP_LINES_FROM = 8, GROUP_LINES_NONE = 40;
    public static final float GROUP_LINE_ALPHA = 0.2f;

    // list of ids that correspond to idle workers - should only be updated from server side
    public static final ArrayList<Integer> idleWorkerIds = new ArrayList<>();
    // the same sync, split (hud/IdleButtons): idle T2 builders (not in idleWorkerIds) and whether the commander is idle
    public static final ArrayList<Integer> idleT2BuilderIds = new ArrayList<>();
    public static volatile boolean commanderIdle = false;

    // units moused over or inside a box select
    private static final ArrayList<LivingEntity> preselectedUnits = new ArrayList<>();
    // units selected by click or box select
    private static final ArrayList<LivingEntity> selectedUnits = new ArrayList<>();
    private static ArrayList<LivingEntity> sortedSelectedUnits = new ArrayList<>();
    private static boolean sortedSelectedUnitsChanged = true;
    // tracking of all existing units
    private static final ArrayList<LivingEntity> allUnits = new ArrayList<>();

    public static final Map<Integer, HashMap<MobEffect, MobEffectIcon>> mobEffectIcons = new ConcurrentHashMap<>();

    @Nullable
    private static UnitActionItem lastClientUAIActioned = null;

    public static ArrayList<LivingEntity> getPreselectedUnits() {
        return preselectedUnits;
    }

    public static ArrayList<LivingEntity> getSelectedUnits() {
        return selectedUnits;
    }

    private static void markSelectedUnitsChanged() {
        sortedSelectedUnitsChanged = true;
    }

    /** Drops every selected unit matching the test and refreshes the sorted view (selection summary Ctrl-click). */
    public static void removeSelectedUnits(Predicate<LivingEntity> test) {
        if (selectedUnits.removeIf(test))
            markSelectedUnitsChanged();
    }



    public static ArrayList<LivingEntity> getSortedSelectedUnits() {
        if (!sortedSelectedUnitsChanged) {
            return sortedSelectedUnits;
        }
        ArrayList<LivingEntity> units = new ArrayList<>(UnitClientEvents.getSelectedUnits());
        units.sort((a, b) -> {
            var isHeroA = a instanceof HeroUnit;
            var isHeroB = b instanceof HeroUnit;
            if (isHeroA && !isHeroB) return -1;
            if (!isHeroA && isHeroB) return 1;
            return HudClientEvents.getModifiedEntityName(a).compareTo(HudClientEvents.getModifiedEntityName(b));
        });
        sortedSelectedUnits = units;
        sortedSelectedUnitsChanged = false;
        return units;
    }

    public static ArrayList<LivingEntity> getAllUnits() {
        return allUnits;
    }

    private static boolean rightClickMoveDeferred = false;
    private static boolean rightClickActionTaken = false;

    /**
     * Is this unit hidden from the local player - a cloaked Shade Ranger or a unit in a Verdant thicket, seen from
     * the enemy side (EntityMixin.isInvisibleTo gives its owner and allies a ghost instead)? Such a unit isn't drawn,
     * so it must not be hoverable, selectable or right-click attackable either: its hitbox would give it away. The
     * server refuses orders on it too (Concealment); this keeps the cursor honest.
     */
    public static boolean isHiddenFromLocalPlayer(LivingEntity e) {
        return e instanceof Unit && e.isInvisible() && MC.player != null && e.isInvisibleTo(MC.player);
    }

    /** Can the cursor or a drag box light this entity at all: seen, not a rider, not a spectator / creative player? */
    public static boolean isPreselectable(LivingEntity unit) {
        if (unit instanceof Player player && (player.isSpectator() || player.isCreative()))
            return false;
        if (!FogOfWarClientEvents.isInBrightChunk(unit))
            return false;
        if (isHiddenFromLocalPlayer(unit))
            return false;
        return !unit.isPassenger();
    }

    public static void addPreselectedUnit(LivingEntity unit) {
        if (!isPreselectable(unit))
            return;
        preselectedUnits.add(unit);
        markSelectedUnitsChanged();
    }
    public static void sortedSelectedUnits() {
        selectedUnits.sort(Comparator.comparing(MiscUtil::getSimpleEntityName));
        selectedUnits.sort(Comparator.comparing(Entity::getId));
    }
    public static void addSelectedUnitNoSort(LivingEntity unit) {
        CursorClientEvents.setLeftClickAction(null);
        // only what the player sees now: lit, not cloaked from this side, not a rider (InspectRules, enemies too)
        if (!InspectRules.mayInspect(FogOfWarClientEvents.isInBrightChunk(unit), isHiddenFromLocalPlayer(unit),
                unit.isPassenger()))
            return;
        selectedUnits.add(unit);
        BuildingClientEvents.clearSelectedBuildings();
        NonUnitClientEvents.isMoveCheckpointGreen = true;
        markSelectedUnitsChanged();
    }
    public static void addSelectedUnit(LivingEntity unit) {
        CursorClientEvents.setLeftClickAction(null);
        // only what the player sees now: lit, not cloaked from this side, not a rider (InspectRules, enemies too)
        if (!InspectRules.mayInspect(FogOfWarClientEvents.isInBrightChunk(unit), isHiddenFromLocalPlayer(unit),
                unit.isPassenger()))
            return;
        selectedUnits.add(unit);
        selectedUnits.sort(Comparator.comparing(MiscUtil::getSimpleEntityName));
        selectedUnits.sort(Comparator.comparing(Entity::getId));
        BuildingClientEvents.clearSelectedBuildings();
        NonUnitClientEvents.isMoveCheckpointGreen = true;
        markSelectedUnitsChanged();
    }
    public static void clearPreselectedUnits() {
        preselectedUnits.clear();
    }
    public static void clearSelectedUnits() {
        selectedUnits.clear();
        NonUnitClientEvents.isMoveCheckpointGreen = true;
    }

    private static long lastLeftClickTime = 0; // to track double clicks

    private static boolean isLeftClickAttack() {
        return CursorClientEvents.getLeftClickAction() == UnitAction.ATTACK;
    }

    @SubscribeEvent
    public static void onEntityMount(EntityMountEvent evt) {
        if (evt.getLevel().isClientSide())
            selectedUnits.removeIf(e -> e.getId() == evt.getEntityMounting().getId());
        markSelectedUnitsChanged();
    }

    // Per-owner population of tracked + virtual units, memoised per client tick (keyed also on the list sizes so a
    // unit joining/leaving mid-tick is reflected). This was recomputed per call - with a List.contains per virtual
    // unit, ie. O(units x virtualUnits) - and is called every frame by the HUD and per production item per tick.
    private static final HashMap<String, Integer> unitPopulationCache = new HashMap<>();
    private static long unitPopulationCacheTick = Long.MIN_VALUE;
    private static int unitPopulationCacheUnits = -1;
    private static int unitPopulationCacheVirtual = -1;

    private static int getUnitPopulation(String playerName) {
        long tick = MC.level != null ? MC.level.getGameTime() : Long.MIN_VALUE;
        if (tick != unitPopulationCacheTick || allUnits.size() != unitPopulationCacheUnits ||
                MinimapClientEvents.virtualUnits.size() != unitPopulationCacheVirtual) {
            unitPopulationCache.clear();
            HashSet<Integer> allUnitIds = new HashSet<>(allUnits.size() * 2);
            for (LivingEntity entity : allUnits) {
                allUnitIds.add(entity.getId());
                if (entity instanceof Unit unit)
                    unitPopulationCache.merge(unit.getOwnerName(), unit.getCost().population, Integer::sum);
            }
            for (VirtualUnit virtualUnit : MinimapClientEvents.virtualUnits) {
                if (!allUnitIds.contains(virtualUnit.id))
                    unitPopulationCache.merge(virtualUnit.ownerName, virtualUnit.population, Integer::sum);
            }
            unitPopulationCacheTick = tick;
            unitPopulationCacheUnits = allUnits.size();
            unitPopulationCacheVirtual = MinimapClientEvents.virtualUnits.size();
        }
        return unitPopulationCache.getOrDefault(playerName, 0);
    }

    private static final QueuePopulation queuePopulation = new QueuePopulation();

    public static int getCurrentPopulation(String playerName) {
        if (MC.level == null)
            return 0;
        // the queue part is memoised per owner per client tick too (QueuePopulation: any queue change recounts at once)
        return getUnitPopulation(playerName)
            + queuePopulation.of(playerName, BuildingClientEvents.getBuildings(), true, MC.level.getGameTime(), true);
    }

    public static void sendUnitCommandManual(String playerName, UnitAction action, int unitId, int[] unitIds,
                                             BlockPos preselectedBlockPos, BlockPos selectedBuildingPos) {
        if (MC.player != null && playerName.equals(MC.player.getName().getString()))
            sendUnitCommandManual(action, unitId, unitIds, preselectedBlockPos, selectedBuildingPos);
    }

    public static void sendUnitCommandManual(UnitAction action, int unitId, int[] unitIds,
                                             BlockPos preselectedBlockPos) {
        sendUnitCommandManual(action, unitId, unitIds, preselectedBlockPos, new BlockPos(0,0,0));
    }

    public static void sendUnitCommandManual(UnitAction action, int unitId, int[] unitIds,
                                             BlockPos preselectedBlockPos, boolean clientside, boolean serverside) {
        sendUnitCommandManual(action, unitId, unitIds, preselectedBlockPos,
                new BlockPos(0,0,0), clientside, serverside);
    }

    public static void sendUnitCommandManual(UnitAction action, int unitId, int[] unitIds) {
        sendUnitCommandManual(action, unitId, unitIds,
                new BlockPos(0,0,0),
                new BlockPos(0,0,0));
    }

    public static void sendUnitCommandManual(UnitAction action, int[] unitIds) {
        sendUnitCommandManual(action, -1, unitIds,
                new BlockPos(0,0,0),
                new BlockPos(0,0,0));
    }

    public static void sendUnitCommandManual(UnitAction action, int unitId, int[] unitIds,
                                             BlockPos preselectedBlockPos, BlockPos selectedBuildingPos) {
        if (MC.player != null) {
            UnitActionItem actionItem = new UnitActionItem(
                MC.player.getName().getString(),
                action, unitId, unitIds,
                preselectedBlockPos,
                selectedBuildingPos
            );
            // prevent spam clicking the same action repeatedly
            if (!actionItem.equals(lastClientUAIActioned)) {
                actionItem.action(MC.level);
                lastClientUAIActioned = actionItem;
            }

            PacketHandler.INSTANCE.sendToServer(new UnitActionServerboundPacket(
                MC.player.getName().getString(),
                action, unitId, unitIds,
                preselectedBlockPos,
                selectedBuildingPos,
                queueModeFromKeys()
            ));
        }
    }

    /** The same order with an explicit queue mode (the command language's "stop AND build": the second is appended). */
    public static void sendUnitCommandManual(UnitAction action, int unitId, int[] unitIds,
                                             BlockPos preselectedBlockPos, BlockPos selectedBuildingPos,
                                             com.solegendary.reignofnether.unit.command.QueueMode queueMode) {
        if (MC.player != null) {
            UnitActionItem actionItem = new UnitActionItem(MC.player.getName().getString(), action, unitId, unitIds,
                preselectedBlockPos, selectedBuildingPos);
            if (!actionItem.equals(lastClientUAIActioned)) {
                actionItem.action(MC.level);
                lastClientUAIActioned = actionItem;
            }
            PacketHandler.INSTANCE.sendToServer(new UnitActionServerboundPacket(MC.player.getName().getString(),
                action, unitId, unitIds, preselectedBlockPos, selectedBuildingPos, queueMode));
        }
    }

    /**
     * How an order meets each unit's command queue on the server (BAR): Shift appends it, Ctrl puts it at the front
     * (the interrupted order resumes after it), a plain click replaces the queue. Ctrl+right-DRAG stays the area
     * command (AreaCommandClientEvents); a Ctrl click is free for this.
     */
    static QueueMode queueModeFromKeys() {
        return QueueEditClientEvents.queueModeFromKeys();
    }

    public static void sendUnitCommandManual(UnitAction action, int unitId, int[] unitIds,
                                             BlockPos preselectedBlockPos, BlockPos selectedBuildingPos,
                                             boolean clientside, boolean serverside) {
        if (MC.player != null) {
            if (clientside) {
                UnitActionItem actionItem = new UnitActionItem(
                        MC.player.getName().getString(),
                        action, unitId, unitIds,
                        preselectedBlockPos,
                        selectedBuildingPos
                );
                actionItem.action(MC.level);
            }
            if (serverside) {
                PacketHandler.INSTANCE.sendToServer(new UnitActionServerboundPacket(
                        MC.player.getName().getString(),
                        action, unitId, unitIds,
                        preselectedBlockPos,
                        selectedBuildingPos,
                        queueModeFromKeys()
                ));
            }
        }
    }

    public static void sendUnitCommand(UnitAction action) {
        sendUnitCommand(action, null);
    }

    public static void sendUnitCommand(UnitAction action, Predicate<LivingEntity> includeUnit) {
        sendUnitCommandAt(action, includeUnit, getPreselectedBlockPos());
    }

    /** sendUnitCommand aimed at {@code bp} instead of the cursor's picked block (a blueprint ghost's centre). */
    public static void sendUnitCommandAt(UnitAction action, Predicate<LivingEntity> includeUnit, BlockPos bp) {

        if (action.name().toLowerCase().contains("startrts")) {
            if (action == UnitAction.STARTRTS_VILLAGERS) {
                PlayerServerboundPacket.startRTS(Factions.VILLAGERS, (double) bp.getX(), (double) bp.getY(), (double) bp.getZ());
            } else if (action == UnitAction.STARTRTS_MONSTERS) {
                PlayerServerboundPacket.startRTS(Factions.MONSTERS, (double) bp.getX(), (double) bp.getY(), (double) bp.getZ());
            } else if (action == UnitAction.STARTRTS_PIGLINS) {
                PlayerServerboundPacket.startRTS(Factions.PIGLINS, (double) bp.getX(), (double) bp.getY(), (double) bp.getZ());
            }
            return;
        }
        else if (action.name().toLowerCase().contains("sandbox_spawn")) {
            if (action == UnitAction.STARTRTS_VILLAGERS) {
                PlayerServerboundPacket.startRTS(Factions.VILLAGERS, (double) bp.getX(), (double) bp.getY(), (double) bp.getZ());
            } else if (action == UnitAction.STARTRTS_MONSTERS) {
                PlayerServerboundPacket.startRTS(Factions.MONSTERS, (double) bp.getX(), (double) bp.getY(), (double) bp.getZ());
            } else if (action == UnitAction.STARTRTS_PIGLINS) {
                PlayerServerboundPacket.startRTS(Factions.PIGLINS, (double) bp.getX(), (double) bp.getY(), (double) bp.getZ());
            }
            return;
        }

        if (MC.player != null) {
            var selUnits = new LinkedList<LivingEntity>();
            int targetEntityId = !preselectedUnits.isEmpty() ? preselectedUnits.get(0).getId() : -1;
            String senderName = MC.player.getName().getString();
            boolean senderSandbox = UnitActionItem.isSandboxOwner(MC.level, senderName);
            loop:
            for (LivingEntity livingEntity : selectedUnits) {
                // an inspected enemy / neutral unit is never named in an order (the server drops it too: PacketAuth)
                if (livingEntity instanceof Unit u && !UnitActionItem.mayCommand(MC.level, senderName, u, senderSandbox))
                    continue;
                if (livingEntity instanceof Unit unit) {
                    for (Ability ability : unit.getAbilities().get()) {
                        if (ability.isCasting(unit) && ability.oneClickOneUse && ability.action == action) continue loop;
                    }
                } else if (!NonUnitClientEvents.canControlAllMobs()) {
                    continue;
                }
                if (includeUnit == null || includeUnit.test(livingEntity))
                    selUnits.add(livingEntity);
            }
            String playerName = MC.player.getName().getString();

            UnitActionItem actionItem = new UnitActionItem(
                playerName,
                action,
                targetEntityId,
                ArrayUtil.livingEntityListToIdArray(selUnits),
                bp,
                HudClientEvents.hudSelectedPlacement != null ? HudClientEvents.hudSelectedPlacement.originPos : new BlockPos(0,0,0)
            );
            actionItem.action(MC.level);

            PacketHandler.INSTANCE.sendToServer(new UnitActionServerboundPacket(
                playerName,
                action,
                targetEntityId,
                ArrayUtil.livingEntityListToIdArray(selUnits),
                bp,
                HudClientEvents.hudSelectedPlacement != null ? HudClientEvents.hudSelectedPlacement.originPos : new BlockPos(0,0,0),
                queueModeFromKeys()
            ));
        }
    }

    private static void resolveMoveAction() {
        if (CursorClientEvents.isRightClickDown() && !selectedUnits.isEmpty()) {
            rightClickMoveDeferred = true;
            return;
        }
        doResolveMoveAction();
    }

    private static void resolveMoveActionDeferred() {
        if (!selectedUnits.isEmpty()) {
            rightClickMoveDeferred = true;
            return;
        }
        doResolveMoveAction();
    }

    private static void doResolveMoveAction() {
        // open shop
        if (ItemClientEvents.ENABLED && HudClientEvents.hudSelectedEntity instanceof Unit unit && unit.getItemGoal() != null &&
                BuildingClientEvents.getPreselectedBuilding() instanceof ItemShopPlacement itemShop && MC.player != null &&
                itemShop.isBuilt && !itemShop.getStockedItems().isEmpty()) {
            unit.getCheckpoints().clear();
            unit.getCheckpoints().add(new Checkpoint(BuildingClientEvents.getPreselectedBuilding().centrePos, true));

            ItemServerboundPacket.openShop(
                    HudClientEvents.hudSelectedEntity.getId(),
                    BuildingClientEvents.getPreselectedBuilding().originPos
            );
            return;
        }
        // pickup item
        else if (HudClientEvents.hudSelectedEntity instanceof Unit unit && unit.getItemGoal() != null &&
                !ItemClientEvents.getPreselectedItems().isEmpty() && MC.player != null) {
            unit.getCheckpoints().clear();
            unit.getCheckpoints().add(new Checkpoint(ItemClientEvents.getPreselectedItems().get(0), true));

            if (ItemClientEvents.ENABLED) {
                ItemServerboundPacket.pickup(
                        HudClientEvents.hudSelectedEntity.getId(),
                        ItemClientEvents.getPreselectedItems().get(0).getId()
                );
                return;
            }
        }
        // follow friendly unit
        if (preselectedUnits.size() == 1 && !targetingSelf()) {
            LivingEntity clicked = preselectedUnits.get(0);
            Relationship rel = getPlayerToEntityRelationship(clicked);
            if (hudSelectedEntity instanceof WitchUnit) {
                sendUnitCommand(UnitAction.THROW_LINGERING_REGEN_POTION);
            } else if (GuardServerEvents.rightClickGuards(selectedUnits, clicked,
                    rel == Relationship.OWNED || rel == Relationship.FRIENDLY)) {
                // builders + a friendly builder under the cursor: assist it (BAR guard), not just walk behind it
                sendUnitCommand(UnitAction.GUARD);
            } else {
                sendUnitCommand(UnitAction.FOLLOW);
            }
        }
        // move to ground pos (disabled during camera manip)
        else if (!Keybindings.altMod.isDown() && !selectedUnits.isEmpty() && MC.level != null) {
            sendUnitCommand(UnitAction.MOVE);
        }
    }

    public static ResourceName getSelectedUnitResourceTarget() {
        Entity entity = hudSelectedEntity;
        if (entity instanceof WorkerUnit workerUnit)
            return workerUnit.getGatherResourceGoal().getTargetResourceName();
        return ResourceName.NONE;
    }

    public static void syncOwnerName(int entityId, String ownerName) {
        for (LivingEntity entity : allUnits)
            if (entity.getId() == entityId && MC.level != null)
                if (entity instanceof Unit unit)
                    unit.setOwnerName(ownerName);
    }

    public static void syncScenarioRoleIndex(int entityId, int scenarioRoleIndex) {
        for (LivingEntity entity : allUnits)
            if (entity.getId() == entityId && MC.level != null)
                if (entity instanceof Unit unit)
                    unit.setScenarioRoleIndex(scenarioRoleIndex);
    }

    /**
     * Update data on a unit from serverside, mainly to ensure unit HUD data is up-to-date
     * Only try to update health and pos if out of view
     */
    public static void syncUnitStats(int entityId, float health, float absorb, Vec3 pos, String ownerName, int population) {
        if (MC.level == null)
            return;
        LivingEntity found = null;
        for (LivingEntity entity : allUnits) {
            if (entity.getId() == entityId) {
                found = entity;
                break;
            }
        }
        applyUnitStats(found, entityId, health, absorb, pos, ownerName, population);
    }

    // trackedEntity = the allUnits entry with this id, or null if there is none
    private static void applyUnitStats(@Nullable LivingEntity trackedEntity, int entityId, float health, float absorb, Vec3 pos, String ownerName, int population) {
        if (MC.level == null)
            return;
        boolean isLoadedClientside = MC.level.getEntity(entityId) != null;

        if (trackedEntity != null) {
            if (!isLoadedClientside) {
                trackedEntity.setHealth(health);
                trackedEntity.setPos(pos);
            }
            trackedEntity.setAbsorptionAmount(absorb);
            return;
        }
        // if the unit doesn't exist at all clientside, create a VirtualUnit to track its minimap position and population usage
        if (!isLoadedClientside) {
            MinimapClientEvents.syncVirtualUnits(new BlockPos((int) pos.x, (int) pos.y, (int) pos.z), entityId, ownerName, population);
        } else {
            MinimapClientEvents.removeVirtualUnit(entityId);
        }
    }

    public static void syncWorkerUnit(int entityId, boolean isBuilding, boolean isGathering, ResourceName gatherName, BlockPos gatherPos, int gatherTicks) {
        for(LivingEntity entity : allUnits) {
            if (entity.getId() == entityId && MC.level != null) {
                if (entity instanceof WorkerUnit workerUnit) {
                    workerUnit.getBuildRepairGoal().setIsBuildingServerside(isBuilding);
                    workerUnit.getGatherResourceGoal().setIsGatheringServerside(isGathering);
                    workerUnit.getGatherResourceGoal().syncFromServer(gatherName, gatherPos, gatherTicks);
                }
            }
        }
    }

    public static void syncUnitResources(int entityId, Resources res) {
        for(LivingEntity entity : allUnits) {
            if (entity.getId() == entityId && MC.level != null) {
                if (entity instanceof Unit unit) {
                    applyUnitResources(unit, res);
                }
            }
        }
    }

    private static void applyUnitResources(Unit unit, Resources res) {
        unit.getItems().removeIf(i -> !ItemUtil.isEdibleFoodOrDrink(i.getItem()));
        unit.getItems().add(new ItemStack(Items.SUGAR, res.food));
        unit.getItems().add(new ItemStack(Items.STICK, res.wood));
        unit.getItems().add(new ItemStack(Items.STONE, res.ore));
        unit.getItems().add(new ItemStack(Items.EMERALD, res.emerald));
    }

    public static void syncAnchorPos(int entityId, BlockPos bp) {
        for(LivingEntity entity : allUnits) {
            if (entity.getId() == entityId && MC.level != null) {
                if (entity instanceof Unit unit) {
                    unit.setAnchor(bp);
                    break;
                }
            }
        }
    }

    public static void removeAnchorPos(int entityId) {
        for(LivingEntity entity : allUnits) {
            if (entity.getId() == entityId && MC.level != null) {
                if (entity instanceof Unit unit) {
                    unit.setAnchor(null);
                    break;
                }
            }
        }
    }

    /**
     * Apply a UnitBatchSyncClientboundPacket: the same state updates the old per-unit packets made (syncUnitResources,
     * syncUnitStats, syncAnchorPos/removeAnchorPos, makeVillagerVeteran, syncWorkerUnit, ItemClientEvents.syncInventory),
     * but with one id->unit map built per batch instead of a linear scan of allUnits per packet.
     */
    public static void applyBatchSync(List<UnitBatchSyncClientboundPacket.Entry> entries) {
        if (MC.level == null)
            return;
        HashMap<Integer, LivingEntity> byId = new HashMap<>(allUnits.size() * 2);
        for (LivingEntity le : allUnits)
            byId.putIfAbsent(le.getId(), le); // first match, like the old loops
        for (UnitBatchSyncClientboundPacket.Entry e : entries) {
            LivingEntity entity = byId.get(e.id);
            if (e.has(UnitBatchSyncClientboundPacket.Field.RESOURCES) && entity instanceof Unit unit)
                applyUnitResources(unit, new Resources("", e.food, e.wood, e.ore, e.emerald));
            if (e.has(UnitBatchSyncClientboundPacket.Field.STATS))
                applyUnitStats(entity, e.id, e.health, e.absorb, new Vec3(e.x, e.y, e.z), e.ownerName, e.population);
            if (e.has(UnitBatchSyncClientboundPacket.Field.ANCHOR) && entity instanceof Unit unit)
                unit.setAnchor(e.anchor);
            if (e.has(UnitBatchSyncClientboundPacket.Field.VETERAN) && entity instanceof VillagerUnit vUnit)
                vUnit.isVeteran = true;
            if (e.has(UnitBatchSyncClientboundPacket.Field.WORKER) && entity instanceof WorkerUnit workerUnit) {
                BlockPos gatherPos = e.gatherPos == null || e.gatherPos.equals(new BlockPos(0, 0, 0)) ? null : e.gatherPos;
                workerUnit.getBuildRepairGoal().setIsBuildingServerside(e.isBuilding);
                workerUnit.getGatherResourceGoal().setIsGatheringServerside(e.isGathering);
                workerUnit.getGatherResourceGoal().syncFromServer(e.gatherName, gatherPos, e.gatherTicks);
            }
            if (e.has(UnitBatchSyncClientboundPacket.Field.INVENTORY))
                ItemClientEvents.syncInventory(e.id, e.items);
            if (e.has(UnitBatchSyncClientboundPacket.Field.UPGRADES) && entity instanceof Unit unit)
                unit.setUpgradeMask(e.upgrades);
            if (e.has(UnitBatchSyncClientboundPacket.Field.AUTOCAST) && entity instanceof Unit unit)
                com.solegendary.reignofnether.ability.AutocastState.setOverrides(unit, e.autocast);
        }
    }

    // dying (health <= 0, death animation playing) or removed for good (killed/discarded). Entities that merely left
    // render distance (UNLOADED_*) are kept on purpose - see onEntityLeave.
    public static boolean isDeadForSelection(LivingEntity e) {
        return SelectionKeep.shouldDrop(e.isDeadOrDying(), e.getRemovalReason());
    }

    private static final int VIS_CHECK_TICKS_MAX = 10;
    private static int ticksToNextVisCheck = VIS_CHECK_TICKS_MAX;
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent evt) {
        if (evt.phase != TickEvent.Phase.END)
            return;

        ticksToNextVisCheck -= 1;

        // drop dead/killed units from the selection every tick: the server's leave packet only arrives after the
        // death animation (or never, if the packet races the kill), which left a "0.0/30" blank portrait in the
        // panel. Non-capturing method ref = no per-tick allocation; the lists are tiny anyway.
        if (!selectedUnits.isEmpty() && selectedUnits.removeIf(UnitClientEvents::isDeadForSelection))
            markSelectedUnitsChanged();
        if (!preselectedUnits.isEmpty())
            preselectedUnits.removeIf(UnitClientEvents::isDeadForSelection);

        if (ticksToNextVisCheck <= 0) {
            ticksToNextVisCheck = VIS_CHECK_TICKS_MAX;

            // prevent selection of units out of view
            // (an enemy that went under cover - cloak / thicket - drops out of the selection the same way)
            if (selectedUnits.removeIf(e -> !InspectRules.mayInspect(FogOfWarClientEvents.isInBrightChunk(e),
                    isHiddenFromLocalPlayer(e), false)))
                markSelectedUnitsChanged();
        }

        // calculate vecs used to hide leaf blocks around units
        if (MC.player != null && OrthoviewClientEvents.hideLeavesMethod == OrthoviewClientEvents.LeafHideMethod.AROUND_UNITS_AND_CURSOR &&
            OrthoviewClientEvents.isEnabled()) {

            synchronized (windowPositions) {
                windowPositions.clear();
                UnitClientEvents.getAllUnits().forEach(u -> {
                    if (FogOfWarClientEvents.isInBrightChunk(u))
                        windowPositions.add(u.getOnPos());
                });
                BlockPos cursorBp = CursorClientEvents.getPreselectedBlockPos();
                windowPositions.add(cursorBp);

                synchronized (unitWindowVecs) {
                    unitWindowVecs.clear();
                    windowPositions.forEach(bp -> {
                        float dist = Math.max(120, OrthoviewClientEvents.getZoom() * 2);
                        if (bp.distSqr(MC.player.getOnPos()) < (dist * dist))
                            unitWindowVecs.add(MyMath.prepIsPointInsideRect3d(Minecraft.getInstance(),
                                    new Vector3d(bp.getX() - WINDOW_RADIUS, bp.getY(), bp.getZ() - WINDOW_RADIUS), // tl
                                    new Vector3d(bp.getX() - WINDOW_RADIUS, bp.getY(), bp.getZ() + WINDOW_RADIUS), // bl
                                    new Vector3d(bp.getX() + WINDOW_RADIUS, bp.getY(), bp.getZ() + WINDOW_RADIUS)  // br
                            ));
                    });
                    // visible buildings get a window sized to their footprint: a base built in a forest (bots
                    // often are) was hidden under the canopy while its units showed through (clip 15-51). Few
                    // dozen at most, and only those in view range, so the leaf test in RenderChunkRegionMixin
                    // stays cheap.
                    float bDist = Math.max(120, OrthoviewClientEvents.getZoom() * 2);
                    for (com.solegendary.reignofnether.building.BuildingPlacement b
                            : com.solegendary.reignofnether.building.BuildingClientEvents.getBuildings()) {
                        if (b.minCorner == null || b.maxCorner == null || b.centrePos == null)
                            continue;
                        BlockPos c = b.centrePos;
                        if (c.distSqr(MC.player.getOnPos()) >= bDist * bDist || !FogOfWarClientEvents.isInBrightChunk(c))
                            continue;
                        int r = Math.max(WINDOW_RADIUS, Math.max(b.maxCorner.getX() - b.minCorner.getX(),
                            b.maxCorner.getZ() - b.minCorner.getZ()) / 2 + 2);
                        int by = b.minCorner.getY();
                        windowPositions.add(new BlockPos(c.getX(), by, c.getZ()));
                        unitWindowVecs.add(MyMath.prepIsPointInsideRect3d(Minecraft.getInstance(),
                                new Vector3d(c.getX() - r, by, c.getZ() - r),
                                new Vector3d(c.getX() - r, by, c.getZ() + r),
                                new Vector3d(c.getX() + r, by, c.getZ() + r)));
                    }
                }
            }
        } else {
            synchronized (windowPositions) {
                windowPositions.clear();
            }
            synchronized (unitWindowVecs) {
                unitWindowVecs.clear();
            }
        }
        markSelectedUnitsChanged();
    }

    // entity ids start over in the next world: fog-site ghosts kept per worker id must not outlive this one
    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut evt) {
        com.solegendary.reignofnether.unit.goals.ExploreBuildLocationGoal.forgetAllGhosts();
    }

    @SubscribeEvent
    public static void onEntityLeaveEvent(EntityLeaveLevelEvent evt) {
        try {
            synchronized (idleWorkerIds) {
                idleWorkerIds.removeIf(id -> id == evt.getEntity().getId());
                idleT2BuilderIds.removeIf(id -> id == evt.getEntity().getId());
            }
        } catch (Exception e) {
            ReignOfNether.LOGGER.warn("Error while trying to remove an idleWorkerId");
        }
    }

    /**
     * Clientside entities will join and leave based on render distance, but we want to keep entities tracked at all times
     * Therefore, only remove entities if they leave serverside via UnitClientboundPacket.
     */
    public static void onEntityLeave(int entityId) {
        com.solegendary.reignofnether.unit.goals.ExploreBuildLocationGoal.forgetGhosts(entityId);
        selectedUnits.removeIf(e -> e.getId() == entityId);
        //System.out.println("selectedUnits removed entity: " + entityId);
        preselectedUnits.removeIf(e -> e.getId() == entityId);
        //System.out.println("preselectedUnits removed entity: " + entityId);
        allUnits.removeIf(e -> e.getId() == entityId);
        //System.out.println("allUnits removed entity: " + entityId);
        MinimapClientEvents.removeVirtualUnit(entityId);
        markSelectedUnitsChanged();
    }
    /**
     * Add and update entities from clientside action
     */

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent evt) {
        Entity entity = evt.getEntity();

        if (entity instanceof Unit unit && evt.getLevel().isClientSide) {
            TutorialClientEvents.updateStage();

            if (selectedUnits.removeIf(e -> e.getId() == entity.getId()))
                selectedUnits.add((LivingEntity) entity);
            if (preselectedUnits.removeIf(e -> e.getId() == entity.getId()))
                preselectedUnits.add((LivingEntity) entity);
            allUnits.removeIf(e -> e.getId() == entity.getId());
            allUnits.add((LivingEntity) entity);

            unit.initialiseGoals(); // for clientside data tracking - server automatically does this via registerGoals();
            unit.setupEquipmentAndUpgradesClient();

            UnitSyncServerboundPacket.requestSyncAbilities(entity.getId());

            if (entity instanceof HeroUnit)
                HeroServerboundPacket.requestHeroSync(entity.getId());
        }
        markSelectedUnitsChanged();
        MinimapClientEvents.removeVirtualUnit(entity.getId());
    }

    // BAR: with Attack (or Move) armed, a left-DRAG lays a line formation just like a right-drag move, every slot
    // getting ATTACK_MOVE (or MOVE); a plain click still attack-moves / moves to the spot. lovish: "I can't use
    // formation when using attack command". The order is decided on release, so the press only records the start.
    private static boolean leftLinePending = false;
    private static UnitAction leftLineAction = UnitAction.MOVE;
    private static BlockPos leftLineStartBp = null;
    private static double leftLineDownX, leftLineDownY;
    private static final double LEFT_LINE_DRAG_PX = 6;
    // the order a right-drag formation (and a deferred right click) issues: ATTACK_MOVE when Attack was armed
    private static UnitAction rightFormationAction = UnitAction.MOVE;
    // the left press was a command, not a selection: CursorClientEvents starts no box under it
    private static boolean leftPressWasCommand = false;

    public static boolean isLeftPressCommand() {
        return leftPressWasCommand;
    }

    // a plain left click on empty ground deselects (ClickDeselect, playtest Oct 20 item 16): decided at the press,
    // before BuildingClientEvents (also a Pre handler) places a ghost or selects a building, completed at the release
    private static boolean leftPressMayDeselect = false;
    private static double leftPressX = -1, leftPressY = -1;

    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.HIGHEST)
    public static void onLeftPressPre(ScreenEvent.MouseButtonPressed.Pre evt) {
        if (evt.getButton() != GLFW.GLFW_MOUSE_BUTTON_1)
            return;
        leftPressMayDeselect = OrthoviewClientEvents.isEnabled() && MC.level != null && ClickDeselect.pressMayDeselect(
            !selectedUnits.isEmpty() || !BuildingClientEvents.getSelectedBuildings().isEmpty(),
            HudClientEvents.isPointOverHud((int) evt.getMouseX(), (int) evt.getMouseY()),
            CursorClientEvents.getLeftClickAction() != null || ItemClientEvents.hasLeftClickAction(),
            BuildingClientEvents.getBuildingToPlace() != null,
            !preselectedUnits.isEmpty(),
            BuildingClientEvents.getPreselectedBuilding() != null,
            Keybindings.shiftMod.isDown(), Keybindings.ctrlMod.isDown(), Keybindings.altMod.isDown());
        leftPressX = evt.getMouseX();
        leftPressY = evt.getMouseY();
    }

    /** The order the formation being dragged will issue (the preview draws attack lines red). */
    public static UnitAction getFormationAction() {
        return leftLinePending ? leftLineAction : rightFormationAction;
    }

    @SuppressWarnings("SequencedCollectionMethodCanBeUsed")
    @SubscribeEvent
    public static void onMouseClick(ScreenEvent.MouseButtonPressed.Post evt) {
        if (!OrthoviewClientEvents.isEnabled()) return;
        if (MC.level == null) return;

        // prevent clicking behind HUDs
        if (HudClientEvents.isMouseOverAnyButtonOrHud()) {
            CursorClientEvents.setLeftClickAction(null);
            return;
        }
        // what was armed when the button went down: Shift keeps Attack / Move armed for the next queued order (BAR)
        UnitAction armed = CursorClientEvents.getLeftClickAction();
        if (evt.getButton() == GLFW.GLFW_MOUSE_BUTTON_1)
            leftPressWasCommand = armed != null && !selectedUnits.isEmpty();
        if (evt.getButton() == GLFW.GLFW_MOUSE_BUTTON_2)
            rightFormationAction = (armed == UnitAction.ATTACK || armed == UnitAction.ATTACK_MOVE) && !selectedUnits.isEmpty()
                ? UnitAction.ATTACK_MOVE : UnitAction.MOVE;

        // Can only detect clicks client side but only see and modify goals serverside so produce entity queues here
        // and consume in onWorldTick; we also can't add entities directly as they will not have goals populated
        if (evt.getButton() == GLFW.GLFW_MOUSE_BUTTON_1) {

            if (preselectedUnits.size() == 1 && preselectedUnits.get(0) instanceof ScoutDogUnit &&
                getSelectedUnits().size() == 1 && getSelectedUnits().get(0) instanceof ScoutDogUnit dogUnit &&
                getPlayerToEntityRelationship(dogUnit) == Relationship.OWNED)
                dogUnit.pet();
            if (preselectedUnits.size() == 1 && preselectedUnits.get(0) instanceof ScoutCatUnit &&
                getSelectedUnits().size() == 1 && getSelectedUnits().get(0) instanceof ScoutCatUnit catUnit &&
                getPlayerToEntityRelationship(catUnit) == Relationship.OWNED)
                catUnit.pet();

            if (!selectedUnits.isEmpty() && CursorClientEvents.getLeftClickAction() == UnitAction.PATROL) {
                sendUnitCommand(UnitAction.PATROL);
            }
            else if (!selectedUnits.isEmpty() && CursorClientEvents.getLeftClickAction() == UnitAction.ATTACK_MOVE) {
                // Fight (F) armed: whatever is under the cursor, the group fights its way to that spot; a drag before
                // release lays a fight line (BAR's F)
                beginLeftLine(UnitAction.ATTACK_MOVE, evt.getMouseX(), evt.getMouseY());
            }
            else if (!selectedUnits.isEmpty() && CursorClientEvents.getLeftClickAction() == UnitAction.GUARD) {
                // needs something to guard: a unit under the cursor or a building at the clicked spot
                if (!preselectedUnits.isEmpty() || BuildingClientEvents.getPreselectedBuilding() != null)
                    sendUnitCommand(UnitAction.GUARD);
                CursorClientEvents.setLeftClickAction(null);
            }
            else if (!selectedUnits.isEmpty() && isLeftClickAttack()) {
                // A + left click -> force attack single unit (even if friendly)
                if (preselectedUnits.size() == 1 && !targetingSelf()) {
                    sendUnitCommand(UnitAction.ATTACK);
                }
                // A + left click -> force attack building (even if friendly)
                else if (BuildingClientEvents.getPreselectedBuilding() != null)
                    sendUnitCommand(UnitAction.ATTACK_BUILDING);
                // A + left click -> attack move ground; a drag before release makes it an attack-move line
                else
                    beginLeftLine(UnitAction.ATTACK_MOVE, evt.getMouseX(), evt.getMouseY());
            }

            // select all nearby units of the same type when the same unit is double-clicked
            // only works for owned units
            else if (selectedUnits.size() == 1 && MC.level != null && !Keybindings.shiftMod.isDown() &&
                    ((System.currentTimeMillis() - lastLeftClickTime) < com.solegendary.reignofnether.config.ReignOfNetherClientConfigs.DOUBLE_CLICK_MS.get() || Keybindings.ctrlMod.isDown()) &&
                     !preselectedUnits.isEmpty() && selectedUnits.contains(preselectedUnits.get(0))) {

                lastLeftClickTime = 0;
                LivingEntity selectedUnit = selectedUnits.get(0);
                List<? extends LivingEntity> nearbyEntities = MiscUtil.getEntitiesWithinRange(
                        new Vector3d(selectedUnit.position().x, selectedUnit.position().y, selectedUnit.position().z),
                        OrthoviewClientEvents.getZoom(),
                        selectedUnits.get(0).getClass(),
                        MC.level
                );
                if (getPlayerToEntityRelationship(selectedUnit) == Relationship.OWNED ||
                        NonUnitClientEvents.canControlAllMobs() ||
                        AlliancesClient.canControlAlly(selectedUnit)) {

                    for (LivingEntity entity : nearbyEntities) {
                        boolean bothVillagers = entity instanceof VillagerUnit &&
                                                selectedUnit instanceof VillagerUnit;
                        boolean sameProfession = entity instanceof VillagerUnit vUnit1 &&
                                                selectedUnit instanceof VillagerUnit vUnit2 &&
                                                vUnit1.getUnitProfession() == vUnit2.getUnitProfession();
                        boolean garrisoned1 = selectedUnit instanceof Unit unit1 && GarrisonableBuildingAddon.getGarrison(unit1) != null;
                        boolean garrisoned2 = entity instanceof Unit unit2 && GarrisonableBuildingAddon.getGarrison(unit2) != null;
                        boolean garrionStatusMatches = (garrisoned1 && garrisoned2) || (!garrisoned1 && !garrisoned2);
                        if (entity == selectedUnit) continue;
                        if ((getPlayerToEntityRelationship(entity) == Relationship.OWNED ||
                                NonUnitClientEvents.canControlAllMobs() ||
                                AlliancesClient.canControlAlly(entity)) &&
                                (!bothVillagers || sameProfession) && garrionStatusMatches) {
                            addSelectedUnit(entity);
                        }
                    }
                    HudClientEvents.setLowestCdHudEntity();
                }
            }
            // move on left click (M armed); a drag before release makes it a move line
            else if (CursorClientEvents.getLeftClickAction() == UnitAction.MOVE && !selectedUnits.isEmpty())
                beginLeftLine(UnitAction.MOVE, evt.getMouseX(), evt.getMouseY());
            else if (CursorClientEvents.getLeftClickAction() == UnitAction.MOVE)
                resolveMoveAction();
            // resolve any other abilities not explicitly covered here
            else if (CursorClientEvents.getLeftClickAction() != null && MC.player != null) {
                sendUnitCommand(CursorClientEvents.getLeftClickAction());
            }
            /*else if (
                    !selectedUnits.isEmpty() &&
                    BuildingClientEvents.getPreselectedBuilding() == null &&
                    preselectedUnits.isEmpty() &&
                    !BuildingClientEvents.isBuilt
            ) {
                clearSelectedUnits();
            }*/

            // left click -> select a single unit (BAR, lovish Oct 14 "control each individual unit"):
            //   plain click = just this unit; Shift+click = add it to the group (never drops it - a Shift that
            //   toggled used to throw units OUT of a group mid-fight); Ctrl+click on a unit of a group of 2+ = drop
            //   just that unit (a lone selected unit + Ctrl+click still selects all of its type, above)
            else if (preselectedUnits.size() == 1 && !isLeftClickAttack()) {
                boolean deselected = false;

                if (Keybindings.ctrlMod.isDown() && selectedUnits.size() > 1) {
                    deselected = selectedUnits.removeIf(id -> id.equals(preselectedUnits.get(0)));
                    if (deselected && !selectedUnits.isEmpty())
                        HudClientEvents.setHudSelectedEntity(selectedUnits.get(0));
                }

                if (Keybindings.shiftMod.isDown() && !deselected && !selectedUnits.contains(preselectedUnits.get(0)) &&
                    ((preselectedUnits.get(0) instanceof Unit && getPlayerToEntityRelationship(preselectedUnits.get(0)) == Relationship.OWNED) ||
                    AlliancesClient.canControlAlly(preselectedUnits.get(0)) ||
                    NonUnitClientEvents.canControlAllMobs())) {
                        addSelectedUnit(preselectedUnits.get(0));
                }
                else if (!deselected && !Keybindings.shiftMod.isDown() && !Keybindings.ctrlMod.isDown() &&
                        CursorClientEvents.getLeftClickAction() == null &&
                        !ItemClientEvents.hasLeftClickAction()) { // select a single unit - this should be the only code path that allows you to select a non-owned unit
                    clearSelectedUnits();
                    addSelectedUnit(preselectedUnits.get(0));
                }
            }
            // deselect any non-owned units if we managed to select them with owned units
            // and disallow selecting > 1 non-owned unit or the client player
            if (selectedUnits.size() > 1) {
                final int selSize = selectedUnits.size();
                selectedUnits.removeIf(e -> !InspectView.keepInSelection(selSize, e) || e.getId() == MC.player.getId());
            }
            lastLeftClickTime = System.currentTimeMillis();
        }
        else if (evt.getButton() == GLFW.GLFW_MOUSE_BUTTON_2) {
            //UnitClientEvents.sendUnitCommand(UnitAction.DEBUG1);

            if (BuildingClientEvents.getBuildingToPlace() != null) {
                BuildingClientEvents.setBuildingToPlace(null);
                return;
            }
			if (MinimapClientEvents.isPointInsideMinimap(evt.getMouseX(), evt.getMouseY()))
				return;

            rightClickActionTaken = false;
            if (!selectedUnits.isEmpty()) {
                BuildingPlacement preSelBuilding = BuildingClientEvents.getPreselectedBuilding();
                BuildingPlacement ghost;   // a friendly blueprint ghost under the cursor, looked up only if reached below

                // right click -> mount friendly unit
                UnitAction mountAction = null;
                if (preselectedUnits.size() == 1) {
                    mountAction = getMountAction(hudSelectedEntity, preselectedUnits.get(0));
                }
                if (mountAction != null) {
                    sendUnitCommand(mountAction);
                    rightClickActionTaken = true;
                }
                // right click -> garrison friendly building
                else if (preSelBuilding != null && preSelBuilding.getBuilding().hasActiveAddon(GarrisonableBuildingAddon.class) &&
                        hudSelectedEntity instanceof RangedAttackerUnit &&
                        hudSelectedEntity instanceof Unit unit && unit.canGarrison() &&
                        preSelBuilding.ownerName.equals(unit.getOwnerName())) {
                    sendUnitCommand(UnitAction.GARRISON);
                    rightClickActionTaken = true;
                }
                // right click -> attack unfriendly unit
                else if (preselectedUnits.size() == 1 &&
                    !targetingSelf() &&
                    (hudSelectedEntity instanceof Unit || NonUnitClientEvents.canAttack(hudSelectedEntity)) &&
                    ((GameruleClient.neutralAggro && getPlayerToEntityRelationship(preselectedUnits.get(0)) == Relationship.NEUTRAL) ||
                    getPlayerToEntityRelationship(preselectedUnits.get(0)) == Relationship.HOSTILE ||
                     ResourceSources.isHuntableAnimal(preselectedUnits.get(0)))) {

                     if (hudSelectedEntity instanceof WitchUnit witchUnit) {
                         sendUnitCommand(UnitAction.THROW_LINGERING_HARMING_POTION);
                     } else {
                         sendUnitCommand(UnitAction.ATTACK);
                     }
                     rightClickActionTaken = true;
                }
                // right click -> attack unfriendly building
                else if (hudSelectedEntity instanceof AttackerUnit &&
                        (preSelBuilding != null) &&
                        !preSelBuilding.getBuilding().invulnerable &&
                        !(preSelBuilding.getBuilding() instanceof AbstractBridge) &&
                        ((GameruleClient.neutralAggro && getPlayerToBuildingRelationship(preSelBuilding) == Relationship.NEUTRAL) ||
                        getPlayerToBuildingRelationship(preSelBuilding) == Relationship.HOSTILE)) {
                    sendUnitCommand(UnitAction.ATTACK_BUILDING);
                    rightClickActionTaken = true;
                }
                // right click -> return resources
                else if (hudSelectedEntity instanceof Unit unit &&
                        unit.getReturnResourcesGoal() != null &&
                        Resources.getTotalResourcesFromItems(unit.getItems()).getTotalValue() > 0 &&
                        preSelBuilding != null && preSelBuilding.getBuilding().canAcceptResources && preSelBuilding.isBuilt &&
                        unit.getOwnerName().equals(preSelBuilding.ownerName)) {
                    sendUnitCommand(UnitAction.RETURN_RESOURCES);
                    rightClickActionTaken = true;
                }
                // right click -> build or repair preselected building
                else if (hudSelectedEntity instanceof WorkerUnit && preSelBuilding != null &&
                        (getPlayerToBuildingRelationship(preSelBuilding) == Relationship.OWNED || AlliancesClient.canControlAlly(hudSelectedEntity)) ||
                        (preSelBuilding != null && preSelBuilding.getBuilding() instanceof AbstractBridge)) {

                    if (preSelBuilding.getBuilding() instanceof AbstractFarm && preSelBuilding.isBuilt)
                        sendUnitCommand(UnitAction.FARM);
                    else if (BuildingUtils.isBuildingBuildable(true, preSelBuilding))
                        sendUnitCommand(UnitAction.BUILD_REPAIR);
                    else
                        resolveMoveActionDeferred();
                }
                // right click -> send the builder to build (or assist) a friendly blueprint ghost (building/Blueprints).
                // The ghost has no blocks, so the picked block is the ground behind/under it: the order is aimed at
                // the ghost's centre (inside its box: the server's Blueprints.findAt matches it, and the builder walks to the middle, not
                // the corner origin, which can be off a pad edge). Shift queues it like any order.
                else if (hudSelectedEntity instanceof WorkerUnit && preSelBuilding == null
                        && (ghost = com.solegendary.reignofnether.building.BlueprintClientEvents.ghostUnderCursor()) != null) {
                    sendUnitCommandAt(UnitAction.BUILD_REPAIR, null, ghost.centrePos);
                    rightClickActionTaken = true;
                }
                // right click -> follow friendly unit or go to preselected blockPos
                else
                    resolveMoveActionDeferred();
            }
        }
        // clear all cursor actions - except while a left line is pending (decided on release), and Attack / Move
        // under Shift, which stay armed so the next click queues another order (BAR)
        boolean keepArmed = leftLinePending || (Keybindings.shiftMod.isDown()
            && (armed == UnitAction.ATTACK || armed == UnitAction.ATTACK_MOVE || armed == UnitAction.MOVE) && !selectedUnits.isEmpty());
        if (!keepArmed)
            CursorClientEvents.setLeftClickAction(null);
        markSelectedUnitsChanged();
    }

    private static void beginLeftLine(UnitAction action, double mouseX, double mouseY) {
        leftLinePending = true;
        leftLineAction = action;
        leftLineStartBp = getPreselectedBlockPos();
        leftLineDownX = mouseX;
        leftLineDownY = mouseY;
    }

    static ArrayList<LivingEntity> actionableSelectedUnits() {
        ArrayList<LivingEntity> actionableUnits = new ArrayList<>();
        for (LivingEntity unit : getSelectedUnits()) {
            if ((getPlayerToEntityRelationship(unit) == Relationship.OWNED ||
                    NonUnitClientEvents.canControlAllMobs() ||
                    AlliancesClient.canControlAlly(unit)) && unit instanceof Unit) {
                actionableUnits.add(unit);
            }
        }
        return actionableUnits;
    }

    @SubscribeEvent
    public static void onMouseDrag(ScreenEvent.MouseDragged.Pre evt) {
        if (!OrthoviewClientEvents.isEnabled() || MC.level == null)
            return;

        if (Keybindings.altMod.isDown())
            return;

        // left-drag with Attack / Move armed: the same line formation as a right-drag
        if (leftLinePending && evt.getMouseButton() == GLFW.GLFW_MOUSE_BUTTON_1) {
            double dx = evt.getMouseX() - leftLineDownX, dy = evt.getMouseY() - leftLineDownY;
            if (!FormationDragMove.isDragging() && dx * dx + dy * dy < LEFT_LINE_DRAG_PX * LEFT_LINE_DRAG_PX)
                return;
            int n = actionableSelectedUnits().size();
            if (n == 0)
                return;
            if (!FormationDragMove.isDragging())
                FormationDragMove.startDrag(leftLineStartBp);
            FormationDragMove.updateDrag(CursorClientEvents.getPreselectedBlockPos(), n, MC.level);
            return;
        }

        if (!CursorClientEvents.isRightDragActive())
            return;

        // don't allow formation drag if an attack command was issued on right-click press
        if (rightClickActionTaken)
            return;

        ArrayList<LivingEntity> selUnits = getSelectedUnits();
        if (selUnits.isEmpty())
            return;

        BlockPos currentBp = CursorClientEvents.getPreselectedBlockPos();
        ArrayList<LivingEntity> actionableUnits = new ArrayList<>();
        for (LivingEntity unit : selUnits) {
            if ((getPlayerToEntityRelationship(unit) == Relationship.OWNED ||
                    NonUnitClientEvents.canControlAllMobs() ||
                    AlliancesClient.canControlAlly(unit)) && unit instanceof Unit) {
                actionableUnits.add(unit);
            }
        }

        if (actionableUnits.isEmpty())
            return;

        // Ctrl + right-drag: area reclaim / repair (workers) or area attack (fighters) circle instead of a formation line
        if (AreaCommandClientEvents.onRightDrag()) {
            if (FormationDragMove.isDragging())
                FormationDragMove.cancelDrag();
            return;
        }

        if (!FormationDragMove.isDragging()) {
            FormationDragMove.startDrag(CursorClientEvents.getRightClickStartBp());
        }

        FormationDragMove.updateDrag(currentBp, actionableUnits.size(), MC.level);
    }

    @SubscribeEvent
    public static void onMouseRelease(ScreenEvent.MouseButtonReleased.Post evt) {
        if (!OrthoviewClientEvents.isEnabled() || MC.level == null)
            return;

        if (evt.getButton() == GLFW.GLFW_MOUSE_BUTTON_1) {
            leftPressWasCommand = false;
            if (ClickDeselect.releaseDeselects(leftPressMayDeselect, leftPressX, leftPressY, evt.getMouseX(), evt.getMouseY())) {
                clearSelectedUnits();
                BuildingClientEvents.clearSelectedBuildings();
            }
            leftPressMayDeselect = false;
            if (leftLinePending) {
                leftLinePending = false;
                boolean queue = Keybindings.shiftMod.isDown();
                if (FormationDragMove.isDragging())
                    resolveFormationMove(actionableSelectedUnits(), queue, leftLineAction);
                else if (leftLineAction == UnitAction.ATTACK_MOVE)
                    sendUnitCommand(UnitAction.ATTACK_MOVE);
                else
                    resolveMoveAction();
                if (!queue)
                    CursorClientEvents.setLeftClickAction(null);
            }
        }

        if (evt.getButton() == GLFW.GLFW_MOUSE_BUTTON_2) {
		if (MinimapClientEvents.minimapRightClickDown)
			return;
            if (AreaCommandClientEvents.isActive()) {
                AreaCommandClientEvents.onRightRelease();
                FormationDragMove.cancelDrag();
                rightClickMoveDeferred = false;   // the drag was the command; no move order on top
                return;
            }
            if (Keybindings.altMod.isDown() && FormationDragMove.isDragging()) {
                FormationDragMove.cancelDrag();
            }
            boolean wasFormationDrag = FormationDragMove.isDragging();
            if (wasFormationDrag) {
                ArrayList<LivingEntity> selUnits = getSelectedUnits();
                ArrayList<LivingEntity> actionableUnits = new ArrayList<>();
                for (LivingEntity unit : selUnits) {
                    if ((getPlayerToEntityRelationship(unit) == Relationship.OWNED ||
                            NonUnitClientEvents.canControlAllMobs() ||
                            AlliancesClient.canControlAlly(unit)) && unit instanceof Unit) {
                        actionableUnits.add(unit);
                    }
                }
                resolveFormationMove(actionableUnits, Keybindings.shiftMod.isDown(), rightFormationAction);
            }
            if (rightClickMoveDeferred) {
                rightClickMoveDeferred = false;
                if (!wasFormationDrag && rightFormationAction == UnitAction.ATTACK_MOVE) {
                    sendUnitCommand(UnitAction.ATTACK_MOVE);   // right click with Attack armed: attack-move there
                } else if (!wasFormationDrag) {
                    doResolveMoveAction();
                }
            }
        }
    }

    /**
     * One order per formation slot: MOVE for a plain right-drag, ATTACK_MOVE when Attack was armed (left- or
     * right-drag). Shift queues each slot's order behind what the unit is doing, as a single shift-order does.
     */
    private static void resolveFormationMove(ArrayList<LivingEntity> units, boolean queueOrders, UnitAction action) {
        if (MC.player == null || MC.level == null) return;

        List<com.mojang.datafixers.util.Pair<LivingEntity, BlockPos>> pairs = FormationDragMove.endDrag(units);
        String playerName = MC.player.getName().getString();

        for (var pair : pairs) {
            LivingEntity le = pair.getFirst();
            BlockPos targetBp = pair.getSecond();
            if (le instanceof Unit unit) {
                int[] singleUnitId = new int[]{le.getId()};

                if (!queueOrders) {
                    new UnitActionItem(
                        playerName,
                        action, -1, singleUnitId,
                        targetBp,
                        new BlockPos(0, 0, 0)
                    ).action(MC.level);
                } else {
                    MiscUtil.addUnitCheckpoint(unit, targetBp, true);
                }

                PacketHandler.INSTANCE.sendToServer(new UnitActionServerboundPacket(
                    playerName,
                    action, -1, singleUnitId,
                    targetBp,
                    new BlockPos(0, 0, 0),
                    queueOrders
                ));
            }
        }
    }


    /**
     * The white (red for an attack target) outline round each selected and preselected unit, once per unit. Selected
     * units first, then the preselected ones not already drawn (the old HashSet union drew each once too).
     */
    private static void drawSelectionOutlines(PoseStack poseStack, VertexConsumer vc, @Nullable Frustum frustum,
                                              List<LivingEntity> selectedUnits, List<LivingEntity> preselectedUnits) {
        // evaluate conditions that will remain constant during the rendering stage
        boolean isMouseOverAnyButtonOrHud = HudClientEvents.isMouseOverAnyButtonOrHud();
        boolean isLeftClickAttack = isLeftClickAttack();
        boolean targetingSelf = targetingSelf();
        boolean isRightClickDown = MiscUtil.isRightClickDown(MC);
        // membership by set, not List.contains: with 500 selected units the old loop made 250,000 compares a frame
        PRESELECTED_IDS.clear();
        for (LivingEntity entity : preselectedUnits)
            PRESELECTED_IDS.add(entity.getId());
        OUTLINED_IDS.clear();
        MyRenderer.pushCameraRelative(poseStack);
        try {
            for (LivingEntity entity : selectedUnits)
                if (OUTLINED_IDS.add(entity.getId()))
                    drawSelectionOutline(poseStack, vc, frustum, entity, true, PRESELECTED_IDS.contains(entity.getId()),
                            isMouseOverAnyButtonOrHud, isLeftClickAttack, targetingSelf, isRightClickDown);
            for (LivingEntity entity : preselectedUnits)
                if (OUTLINED_IDS.add(entity.getId()))
                    drawSelectionOutline(poseStack, vc, frustum, entity, false, true,
                            isMouseOverAnyButtonOrHud, isLeftClickAttack, targetingSelf, isRightClickDown);
        } finally {
            poseStack.popPose();
        }
    }

    private static void drawSelectionOutline(PoseStack poseStack, VertexConsumer vc, @Nullable Frustum frustum,
                                             LivingEntity entity, boolean isSelected, boolean isPreselected,
                                             boolean isMouseOverAnyButtonOrHud, boolean isLeftClickAttack,
                                             boolean targetingSelf, boolean isRightClickDown) {
        if (entity.isRemoved())
            return;   // kept selected after leaving tracking range (SelectionKeep): not drawn
        AABB entityAABB = entity.getBoundingBox();
        if (entity instanceof Unit unit)
            entityAABB = unit.getInflatedSelectionBox();
        else if (entity instanceof Chicken)
            entityAABB = entityAABB.inflate(0.2f, 0, 0.2f);   // (a setMaxY here discarded its result: the box was never raised)
        if (frustum != null && !frustum.isVisible(entityAABB))
            return;
        if (!FogOfWarClientEvents.isInBrightChunk(entity))
            return;
        if (isPreselected && isLeftClickAttack && !targetingSelf && !isMouseOverAnyButtonOrHud)
            MyRenderer.drawLineBoxOutlineOnlyInCameraSpace(poseStack, vc, entityAABB, 1.0f, 0.3f, 0.3f, 1.0f, false);
        else if (isSelected)
            MyRenderer.drawLineBoxOutlineOnlyInCameraSpace(poseStack, vc, entityAABB, 1.0f, 1.0f, 1.0f, 1.0f, false);
        else if (isPreselected && !isMouseOverAnyButtonOrHud)
            MyRenderer.drawLineBoxOutlineOnlyInCameraSpace(poseStack, vc, entityAABB, 1.0f, 1.0f, 1.0f, isRightClickDown ? 1.0f : 0.5f, false);
    }

    /**
     * The underline under every unit in sight: its owner's colour (grey for a non-player owner), and in the RTS view a
     * white flat outline on top, opaque under selected units. In lovish's Oct 21 match this loop ran over all ~600 units
     * every frame - also in strategic view, where the icons cover it (perf plan R2). Now:
     * <ul>
     * <li>a unit outside the level renderer's frustum is skipped before anything is looked up;</li>
     * <li>in strategic view a unit drawn as an icon is skipped: the icon carries the team colour and, for a selected
     *     unit, a white selection ring (StrategicViewClientEvents.drawUnitIcon). A big unit that still draws as a model
     *     there (StrategicViewClientEvents.keepsModel) keeps its underline, as the selection plates do;</li>
     * <li>colours are looked up once per owner per frame (OwnerColours), the selection is an id set, and the camera
     *     translation is pushed once for the loop instead of once per box.</li>
     * </ul>
     * The outlines are still written through {@code vcNoDepthTest} and the bottoms through the LINES_UNDER_ENTITIES
     * buffer, in the same order as before: in vanilla both are the BufferSource's one shared builder, so both end up in
     * the LINES_UNDER_ENTITIES batch, and a batching mod that splits them (ImmediatelyFast) sees what it saw before.
     */
    private static void drawUnderlines(PoseStack poseStack, VertexConsumer vcNoDepthTest, @Nullable Frustum frustum,
                                       List<LivingEntity> selectedUnits) {
        SELECTED_IDS.clear();
        for (LivingEntity selectedUnit : selectedUnits)
            SELECTED_IDS.add(selectedUnit.getId());
        UNDERLINE_COLOURS.clear();
        var vc = MC.renderBuffers().bufferSource().getBuffer(MyRenderer.LINES_UNDER_ENTITIES);
        boolean ortho = OrthoviewClientEvents.isEnabled();
        boolean strategic = com.solegendary.reignofnether.orthoview.StrategicViewClientEvents.isStrategicView();

        MyRenderer.pushCameraRelative(poseStack);
        try {
            for (LivingEntity entity : allUnits) {
                if (entity.isPassenger())
                    continue;
                if (strategic && !com.solegendary.reignofnether.orthoview.StrategicViewClientEvents.keepsModel(entity))
                    continue;
                AABB entityAABB = entity instanceof Unit unit ? unit.getInflatedSelectionBox() : entity.getBoundingBox();
                if (frustum != null && !frustum.isVisible(entityAABB))
                    continue;
                if (!FogOfWarClientEvents.isInBrightChunk(entity))
                    continue;

                float alpha = SELECTED_IDS.contains(entity.getId()) ? 1.0f : 0.5f;
                // draw only the bottom of the outline boxes
                entityAABB = entityAABB.setMaxY(entityAABB.minY);
                int rgb = entity instanceof Unit owned ? UNDERLINE_COLOURS.unit(owned.getOwnerName()) : 0xFFFFFF;
                float r = ((rgb >> 16) & 0xFF) / 255.0f;
                float g = ((rgb >> 8) & 0xFF) / 255.0f;
                float b = (rgb & 0xFF) / 255.0f;

                // always-shown highlights to indicate unit relationships
                if (ortho)
                    MyRenderer.drawLineBoxOutlineOnlyInCameraSpace(poseStack, vcNoDepthTest, entityAABB, 1.0f, 1.0f, 1.0f, alpha, true);
                MyRenderer.drawLineBoxInCameraSpace(poseStack, vc, entityAABB, r, g, b, 0.5f);
            }
        } finally {
            poseStack.popPose();
        }
        MinimapClientEvents.highlightNeutralFogUnits(poseStack, vc);
        MC.renderBuffers().bufferSource().endBatch(MyRenderer.LINES_UNDER_ENTITIES);
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent evt) {
        if (com.solegendary.reignofnether.postfx.ShaderPacks.skipOverlays()) return;   // shader pack shadow map: overlays cast no shadow
        int sys = com.solegendary.reignofnether.perf.FrameParts.begin(com.solegendary.reignofnether.perf.FrameParts.UNIT_OVERLAYS);
        try {
            renderLevel(evt);
        } finally {
            com.solegendary.reignofnether.perf.FrameParts.end(sys);
        }
    }

    // renderLevel's per-frame scratch (client render thread only), reused so neither a big selection nor the army costs a
    // set or a colour object per frame. Ids, not entities: the old sets were HashSet<Entity> / HashSet<Integer>, and an
    // Entity is equal by id - the same answers.
    private static final IntOpenHashSet SELECTED_IDS = new IntOpenHashSet();
    private static final IntOpenHashSet PRESELECTED_IDS = new IntOpenHashSet();
    private static final IntOpenHashSet OUTLINED_IDS = new IntOpenHashSet();
    private static final OwnerColours UNDERLINE_COLOURS = new OwnerColours();

    private static void renderLevel(RenderLevelStageEvent evt) {
        if (MC.level == null)
            return;
        if (evt.getStage() == AFTER_CUTOUT_BLOCKS ||
            evt.getStage() == AFTER_ENTITIES) {
            ArrayList<LivingEntity> selectedUnits = getSelectedUnits();
            ArrayList<LivingEntity> preselectedUnits = getPreselectedUnits();
            // the level renderer's own culling frustum, which already culls the unit bodies: a unit off screen costs a
            // few plane tests here instead of a fog lookup, two AABBs, a colour lookup and its lines (perf plan R2)
            Frustum frustum = evt.getFrustum();

            var vcNoDepthTest = MC.renderBuffers().bufferSource().getBuffer(MyRenderer.LINES_NO_DEPTH_TEST);

            if (evt.getStage() == AFTER_ENTITIES) {
                // draw outlines on all (pre)selected units but only draw once per unit based on conditions
                // don't render preselection outlines if mousing over HUD
                if (OrthoviewClientEvents.isEnabled() && !(selectedUnits.isEmpty() && preselectedUnits.isEmpty()))
                    drawSelectionOutlines(evt.getPoseStack(), vcNoDepthTest, frustum, selectedUnits, preselectedUnits);
            } else if (evt.getStage() == AFTER_CUTOUT_BLOCKS) {
                if (MinimapClientEvents.shouldUnderline())
                    drawUnderlines(evt.getPoseStack(), vcNoDepthTest, frustum, selectedUnits);
            }

            // render items in front of face for eating units
            for (LivingEntity entity : getAllUnits()) {
                if (entity instanceof Unit unit && unit.isEatingFood()) {
                    MyRenderer.renderItemInFrontOfEntityFace(evt.getPoseStack(), entity, evt.getPartialTick(), new ItemStack(unit.getFoodBeingEaten()));
                }
            }
        }

        if (OrthoviewClientEvents.isEnabled() && evt.getStage() == AFTER_ENTITIES) {
            // BAR command queue lines (server-synced shift-queue), drawn before the checkpoint buffers below are
            // fetched so switching render types can't end a batch they still write to
            UnitQueueLinesClient.render(evt.getPoseStack(), evt.getPartialTick());
            VertexConsumer vertexConsumerLine = MC.renderBuffers().bufferSource().getBuffer(RenderType.LINE_STRIP);
            ResourceLocation rl = ResourceLocation.parse("forge:textures/white.png");
            VertexConsumer vertexConsumerEntityTranslucent = MC.renderBuffers().bufferSource().getBuffer(RenderType.entityTranslucent(rl));
            // draw unit checkpoints. Clip study Oct 13 (b6; 21-24-35 0:17, 21-37-00 0:30): a 100-unit move drew a
            // solid green beam of per-unit lines across the screen. Past GROUP_LINES_FROM selected units the
            // per-unit lines drop to GROUP_LINE_ALPHA (none at all past GROUP_LINES_NONE) and one full line runs
            // from the selection's centroid to the centroid of its targets instead. The centroids are summed in
            // plain doubles as the loop goes, so the group line costs no allocation.
            int nSel = getSelectedUnits().size();
            boolean grouped = nSel > GROUP_LINES_FROM;
            float perUnit = !grouped ? 1f : nSel > GROUP_LINES_NONE ? 0f : GROUP_LINE_ALPHA;
            double gsx = 0, gsy = 0, gsz = 0, gtx = 0, gty = 0, gtz = 0;
            int gn = 0, gTicks = 0;
            boolean gGreen = true;
            for (LivingEntity entity : getSelectedUnits()) {
                if (entity.isRemoved()) continue;   // kept selected after leaving tracking range: nothing to draw
                if (entity instanceof Unit unit) {
                    float entityYOffset1 = 1.74f - ((LivingEntity) unit).getEyeHeight() - 1;
                    Vec3 firstPos = ((LivingEntity) unit).getEyePosition().add(0, entityYOffset1,0);
                    Vec3 lastPos = firstPos;
                    boolean firstCp = true;

                    for (Checkpoint cp : unit.getCheckpoints()) {
                        int ticksUnderFade = Math.min(cp.ticksLeft, CHECKPOINT_TICKS_FADE);
                        if (grouped && firstCp) {
                            firstCp = false;
                            Vec3 t = cp.getPos();
                            gsx += firstPos.x; gsy += firstPos.y; gsz += firstPos.z;
                            gtx += t.x; gty += t.y; gtz += t.z;
                            gn++;
                            gTicks = Math.max(gTicks, ticksUnderFade);
                            gGreen = cp.isGreen;
                        }
                        if (perUnit <= 0f)
                            continue;
                        float a = ((float) ticksUnderFade / (float) CHECKPOINT_TICKS_FADE) * 0.5f * perUnit;
                        if (cp.isForEntity()) {
                            MyRenderer.drawLine(evt.getPoseStack(), vertexConsumerLine, lastPos, cp.getPos(), cp.isGreen ? 0 : 1, cp.isGreen ? 1 : 0, 0, a);
                            lastPos = cp.getPos();
                        } else {
                            MyRenderer.drawLine(evt.getPoseStack(), vertexConsumerLine, lastPos, cp.getPos(), cp.isGreen ? 0 : 1, cp.isGreen ? 1 : 0, 0, a);
                            if (MiscUtil.isSnowLayerBlock(MC.level.getBlockState(cp.bp.offset(0,1,0)).getBlock())) {
                                AABB aabb = new AABB(cp.bp);
                                aabb = aabb.setMaxY(aabb.maxY + 0.13f);
                                MyRenderer.drawSolidBox(
                                        evt.getPoseStack(),
                                        vertexConsumerEntityTranslucent,
                                        aabb,
                                        Direction.UP,
                                        cp.isGreen ? 0 : 1,
                                        cp.isGreen ? 1 : 0,
                                        0,
                                        a,
                                        ResourceLocation.parse("forge:textures/white.png")
                                );
                            } else {
                                MyRenderer.drawBlockFace(evt.getPoseStack(), vertexConsumerEntityTranslucent, Direction.UP, cp.bp, cp.isGreen ? 0 : 1, cp.isGreen ? 1 : 0, 0, a);
                            }
                            lastPos = cp.getPos();
                        }
                    }

                    // draw anchor pos
                    if (SandboxClientEvents.isSandboxPlayer() && unit.getAnchor() != null && !unit.getAnchor().equals(new BlockPos(0,0,0))) {
                        BlockPos ap = unit.getAnchor();
                        float a = MiscUtil.getOscillatingFloat(0.25f, 0.75f);
                        Vec3 apVec3 = new Vec3(ap.getX() + 0.5f, ap.getY() + 1.0f, ap.getZ() + 0.5f);
                        MyRenderer.drawLine(evt.getPoseStack(), vertexConsumerLine, firstPos, apVec3, 1, 1, 0, a);

                        if (MiscUtil.isSnowLayerBlock(MC.level.getBlockState(ap.offset(0,1,0)).getBlock())) {
                            AABB aabb = new AABB(ap);
                            aabb = aabb.setMaxY(aabb.maxY + 0.13f);
                            MyRenderer.drawSolidBox(
                                    evt.getPoseStack(),
                                    vertexConsumerEntityTranslucent,
                                    aabb,
                                    Direction.UP,
                                    1, 1, 0, a,
                                    ResourceLocation.parse("forge:textures/white.png")
                            );
                        } else {
                            MyRenderer.drawBlockFace(evt.getPoseStack(), vertexConsumerEntityTranslucent, Direction.UP, ap, 1, 1, 0, a);
                        }
                    }

                    // draw mounted units' target lines
                    if (entity.isVehicle()) {
                        BlockPos blockTarget = null;
                        if (entity.getFirstPassenger() instanceof RangedAttackerUnit rau &&
                            rau.getRangedAttackGroundGoal() != null &&
                            rau.getRangedAttackGroundGoal().getGroundTarget() != null) {
                            blockTarget = rau.getRangedAttackGroundGoal().getGroundTarget();
                        }
                        float a = MiscUtil.getOscillatingFloat(0.25f, 0.75f);

                        if (blockTarget != null) {
                            Vec3 btVec3 = new Vec3(blockTarget.getX() + 0.5f, blockTarget.getY() + 1.0f, blockTarget.getZ() + 0.5f);
                            MyRenderer.drawLine(evt.getPoseStack(), vertexConsumerLine, firstPos, btVec3, 1, 0, 0, a);
                            if (MiscUtil.isSnowLayerBlock(MC.level.getBlockState(blockTarget.offset(0,1,0)).getBlock())) {
                                AABB aabb = new AABB(blockTarget);
                                aabb = aabb.setMaxY(aabb.maxY + 0.13f);
                                MyRenderer.drawSolidBox(
                                        evt.getPoseStack(),
                                        vertexConsumerEntityTranslucent,
                                        aabb,
                                        Direction.UP,
                                        1, 0, 0, MiscUtil.getOscillatingFloat(0.25f, 0.75f),
                                        ResourceLocation.parse("forge:textures/white.png")
                                );
                            }  else {
                                MyRenderer.drawBlockFace(evt.getPoseStack(), vertexConsumerEntityTranslucent, Direction.UP, blockTarget, 1, 0, 0, a);
                            }
                        }
                    }
                }
            }

            if (grouped && gn > 0) {
                float a = ((float) gTicks / (float) CHECKPOINT_TICKS_FADE) * 0.8f;
                MyRenderer.drawLine(evt.getPoseStack(), gsx / gn, gsy / gn, gsz / gn, gtx / gn, gty / gn, gtz / gn,
                        gGreen ? 0 : 1, gGreen ? 1 : 0, 0, a);
            }

            if (FormationDragMove.isDragging()) {
                VertexConsumer vertexConsumerLineFd = MC.renderBuffers().bufferSource().getBuffer(RenderType.LINES);
                VertexConsumer vertexConsumerEntityTranslucentFd = MC.renderBuffers().bufferSource().getBuffer(RenderType.entityTranslucent(ResourceLocation.parse("forge:textures/white.png")));
                float a = 0.5f;

                // an attack-move line is red, a move line green, so an armed Attack reads before the release
                boolean attackLine = getFormationAction() == UnitAction.ATTACK_MOVE;
                float fr = attackLine ? 1f : 0f, fg = attackLine ? 0.25f : 1f;
                Vec3 lineStart = FormationDragMove.getLineStart();
                Vec3 lineEnd = FormationDragMove.getLineEnd();
                MyRenderer.drawLine(evt.getPoseStack(), vertexConsumerLineFd, lineStart, lineEnd, fr, fg, 0, a);

                for (BlockPos bp : FormationDragMove.getFormationTargets()) {
                    if (MiscUtil.isSnowLayerBlock(MC.level.getBlockState(bp.offset(0, 1, 0)).getBlock())) {
                        AABB aabb = new AABB(bp);
                        aabb = aabb.setMaxY(aabb.maxY + 0.13f);
                        MyRenderer.drawSolidBox(
                                evt.getPoseStack(),
                                vertexConsumerEntityTranslucentFd,
                                aabb,
                                Direction.UP,
                                fr,
                                fg,
                                0,
                                a,
                                ResourceLocation.parse("forge:textures/white.png")
                        );
                    } else {
                        MyRenderer.drawBlockFace(evt.getPoseStack(), vertexConsumerEntityTranslucentFd, Direction.UP, bp, fr, fg, 0, a);
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public static void onButtonPress(ScreenEvent.KeyPressed.Pre evt) {
        if (TextInputClientEvents.isAnyInputFocused())
            return;
        if (evt.getKeyCode() == GLFW.GLFW_KEY_DELETE || (evt.getKeyCode() == GLFW.GLFW_KEY_D && Keybindings.altMod.isDown() && Keybindings.ctrlMod.isDown())) {
            boolean isSandboxPlayer = MC.player != null && SandboxClientEvents.isSandboxPlayer(MC.player.getName().getString());
            LivingEntity entity = hudSelectedEntity;
            if ((entity != null && getPlayerToEntityRelationship(entity) == Relationship.OWNED || isSandboxPlayer) &&
                    !(entity instanceof CreeperUnit)) {
                if (entity != null && !entity.hasEffect(MobEffectRegistrar.PARTIALLY_POSSESSED.get())) {
                    sendUnitCommand(UnitAction.DELETE);
                }
            }
        }
    }

    public static boolean targetingSelf() {
        return selectedUnits.size() == 1 &&
                preselectedUnits.size() == 1 &&
                selectedUnits.get(0).equals(preselectedUnits.get(0));
    }

    public static Relationship getPlayerToPlayerRelationship(String ownerName) {
        if (MC.level != null && MC.player != null) {
            if (ownerName == null || ownerName.isBlank()) {
                return Relationship.NEUTRAL;
            }

            String playerName = MC.player.getName().getString();
            if (playerName.equals(ownerName)) {
                return Relationship.OWNED;
            } else if (AlliancesClient.isAllied(playerName, ownerName)) {
                return Relationship.FRIENDLY;
            } else {
                return Relationship.HOSTILE;
            }
        }

        // If the world or player is null, return NEUTRAL
        return Relationship.NEUTRAL;
    }
    public static Relationship getPlayerToEntityRelationship(LivingEntity entity) {
        if (MC.level != null && MC.player != null) {
            String playerName = MC.player.getName().getString();

            // Check if the entity is a Unit with no owner (neutral)
            if (entity instanceof Unit unit && unit.getOwnerName().isBlank()) {
                return Relationship.NEUTRAL;
            }

            // If the entity is a player, default to hostile unless further alliance checks are needed
            if (entity instanceof Player playerEntity) {
                String entityName = playerEntity.getName().getString();

                if (playerName.equals(entityName)) {
                    return Relationship.OWNED;
                } else if (AlliancesClient.isAllied(playerName, entityName)) {
                    return Relationship.FRIENDLY;
                } else {
                    return Relationship.HOSTILE;
                }
            }

            // Check if the entity is not a Unit (e.g., an NPC or neutral entity)
            if (!(entity instanceof Unit)) {
                return Relationship.NEUTRAL;
            }

            // For Units, check ownership and alliance (the one rule the inspect tests check)
            String ownerName = ((Unit) entity).getOwnerName();
            return InspectRules.relation(playerName, ownerName, AlliancesClient.isAllied(playerName, ownerName));
        }

        // If the world or player is null, return NEUTRAL
        return Relationship.NEUTRAL;
    }

    public static void syncConvertedUnits(String ownerName, int[] oldUnitIds, int[] newUnitIds) {

        for (int i = 0; i < oldUnitIds.length; i++) {
            if (MC.level == null)
                break;

            Entity oldEntity = MC.level.getEntity(oldUnitIds[i]);
            Entity newEntity = MC.level.getEntity(newUnitIds[i]);

            if (oldEntity instanceof Unit oldUnit &&
                newEntity instanceof Unit newUnit) {

                // retain selections
                int j = i;
                if (selectedUnits.removeIf(e -> e.getId() == oldUnitIds[j]))
                    selectedUnits.add((LivingEntity) newEntity);

                // retain control groups
                HudClientEvents.convertControlGroups(oldUnitIds, newUnitIds);

                if (oldUnit.getTargetGoal().getTarget() != null)
                    sendUnitCommandManual(
                        UnitAction.ATTACK,
                        oldUnit.getTargetGoal().getTarget().getId(),
                        new int[] { newEntity.getId() }
                    );
                if (oldUnit.getFollowTarget() != null)
                    sendUnitCommandManual(
                        UnitAction.FOLLOW,
                        oldUnit.getFollowTarget().getId(),
                        new int[] { newEntity.getId() }
                    );
                if (oldUnit.getMoveGoal().getMoveTarget() != null)
                    sendUnitCommandManual(
                        UnitAction.MOVE, -1,
                        new int[] { newEntity.getId() },
                        oldUnit.getMoveGoal().getMoveTarget()
                    );
                if (oldUnit.getReturnResourcesGoal() != null &&
                    oldUnit.getReturnResourcesGoal().getBuildingTarget() != null)
                    sendUnitCommandManual(
                        UnitAction.RETURN_RESOURCES, -1,
                        new int[] { newEntity.getId() },
                        oldUnit.getReturnResourcesGoal().getBuildingTarget().originPos,
                        new BlockPos(0,0,0)
                    );
            }
            if (oldEntity instanceof AttackerUnit oldAUnit &&
                newEntity instanceof AttackerUnit newAUnit) {

                if (oldAUnit.getAttackMoveTarget() != null)
                    sendUnitCommandManual(
                        UnitAction.ATTACK_MOVE, -1,
                        new int[] { newEntity.getId() },
                        oldAUnit.getAttackMoveTarget()
                    );
            }
        }
        // for some reason if we don't discard here first the vehicle also gets discarded
        if (MC.level != null) {
            for (int id : oldUnitIds) {
                Entity e = MC.level.getEntity(id);
                if (e instanceof ConvertableUnit cUnit)
                    cUnit.setShouldDiscard(true);
            }
        }
        sendUnitCommandManual(UnitAction.DISCARD, oldUnitIds);
        markSelectedUnitsChanged();
    }

    public static void syncUnitAnimation(UnitAnimationAction animAction, boolean startAnimation, int entityId, int targetId,
                                         BlockPos buildingBp) {
        for (LivingEntity entity : getAllUnits()) {
            if (entity instanceof EvokerUnit eUnit && eUnit.getId() == entityId) {
                if (eUnit.getCastFangsGoal() != null) {
                    if (startAnimation)
                        eUnit.getCastFangsGoal().startCasting();
                    else
                        eUnit.getCastFangsGoal().stop();
                }
            } else if (entity instanceof WardenUnit wUnit && wUnit.getId() == entityId) {
                if (wUnit.getSonicBoomGoal() != null) {
                    if (startAnimation)
                        wUnit.startSonicBoomAnimation();
                    else
                        wUnit.stopSonicBoomAnimation();
                }
            } else if (entity instanceof GhastUnit gUnit && gUnit.getId() == entityId && startAnimation) {
                gUnit.showShootingFace();
            } else if (entity instanceof BruteUnit bUnit && bUnit.getId() == entityId) {
                bUnit.setHoldingUpShield(startAnimation);
            } else if (entity instanceof WorkerUnit wUnit && entity instanceof AttackerUnit aUnit && entity.getId() == entityId) {
                if (startAnimation && MC.level != null) {
                    if (entity instanceof VillagerUnit vUnit && vUnit.getUnitProfession() == VillagerUnitProfession.HUNTER && vUnit.isVeteran())
                        entity.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_SWORD));
                    else
                        entity.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.WOODEN_SWORD));

                    aUnit.setUnitAttackTarget((LivingEntity) MC.level.getEntity(targetId)); // set itself as a target just for animation purposes, doesn't tick clientside anyway
                } else {
                    entity.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.AIR));
                    aUnit.setUnitAttackTarget(null);
                }
            } else if ((entity instanceof VindicatorUnit || entity instanceof MilitiaUnit) && entity.getId() == entityId) {
                if (startAnimation && MC.level != null) {
                    if (targetId > 0) {
                        ((AttackerUnit) entity).setUnitAttackTarget((LivingEntity) MC.level.getEntity(targetId)); // set itself as a target just for animation purposes, doesn't tick clientside anyway
                    } else {
                        ((AttackerUnit) entity).setAttackBuildingTarget(buildingBp);
                    }
                } else {
                    ((AttackerUnit) entity).setUnitAttackTarget(null);
                    ((MeleeAttackBuildingGoal) ((AttackerUnit) entity).getAttackBuildingGoal()).stopAttacking();
                }
            }
        }
    }

    public static void playKeyframeAnimation(UnitAnimationAction animAction, int entityId) {
        for (LivingEntity entity : getAllUnits()) {
            if (entity instanceof KeyframeAnimated kfa && entity.getId() == entityId) {
                kfa.playSingleAnimation(animAction);
                return;
            }
        }
    }

    // usually used for attacking buildings
    public static void playAttackAnimation(int entityId) {
        for (LivingEntity entity : getAllUnits()) {
            if (entity.getId() == entityId) {
                if (entity instanceof IronGolemUnit ||
                    entity instanceof HoglinUnit ||
                    entity instanceof ZoglinUnit ||
                    entity instanceof RavagerUnit ||
                    entity instanceof WardenUnit) {
                    entity.handleEntityEvent((byte) 4);
                } else if (entity instanceof PolarBearUnit polarBearUnit) {
                    polarBearUnit.doAttackAnimationAndSound();
                }
            }
        }
    }

    public static void syncIdleWorkers(int[] idleWorkerIds) {
        if (MC.level == null)
            return;

        UnitClientEvents.idleWorkerIds.clear();
        idleT2BuilderIds.clear();
        commanderIdle = false;
        List<LivingEntity> idle = new ArrayList<>();
        for (int id : idleWorkerIds) {
            for (LivingEntity entity : getAllUnits()) {
                if (entity.getId() == id &&
                    entity instanceof WorkerUnit unit &&
                    getPlayerToEntityRelationship(entity) == Relationship.OWNED)
                    idle.add(entity);
            }
        }
        // each idle builder shows in exactly one button: commander, T2 builder or plain worker
        IdleButtons.Split<LivingEntity> split = IdleButtons.split(idle,
            CommanderServerEvents::looksLikeCommander, T2Workers::isT2Worker);
        for (LivingEntity e : split.workers)
            UnitClientEvents.idleWorkerIds.add(e.getId());
        for (LivingEntity e : split.t2Builders)
            idleT2BuilderIds.add(e.getId());
        commanderIdle = !split.commanders.isEmpty();
    }

    /** The own commander as the client knows it (the translated name is the tag), or null when there is none alive. */
    @Nullable
    public static LivingEntity getOwnCommander() {
        for (LivingEntity entity : getAllUnits())
            if (entity.isAlive() && CommanderServerEvents.looksLikeCommander(entity) &&
                getPlayerToEntityRelationship(entity) == Relationship.OWNED)
                return entity;
        return null;
    }

    public static void makeVillagerVeteran(int unitId) {
        for (LivingEntity entity : getAllUnits())
            if (entity instanceof VillagerUnit vUnit && unitId == entity.getId())
                vUnit.isVeteran = true;
    }

    // used only for right click mounting shortcut
    @Nullable
    public static UnitAction getMountAction(LivingEntity passenger, LivingEntity vehicle) {
        if (!(passenger instanceof Unit) || !(vehicle instanceof Unit))
            return null;
        if (!((Unit) passenger).getOwnerName().equals(((Unit) vehicle).getOwnerName()))
            return null;
        if ((hudSelectedEntity instanceof PillagerUnit || hudSelectedEntity instanceof EvokerUnit) && vehicle instanceof RavagerUnit &&
            ResearchClient.hasResearch(ProductionItems.RESEARCH_RAVAGER_CAVALRY))
            return UnitAction.MOUNT_RAVAGER;
        if (hudSelectedEntity instanceof HeadhunterUnit && vehicle instanceof HoglinUnit &&
            ResearchClient.hasResearch(ProductionItems.RESEARCH_HOGLIN_CAVALRY))
            return UnitAction.MOUNT_HOGLIN;
        if (hudSelectedEntity instanceof Unit && hudSelectedEntity instanceof AbstractSkeleton && vehicle instanceof SpiderUnit &&
            ResearchClient.hasResearch(ProductionItems.RESEARCH_SPIDER_JOCKEYS))
            return UnitAction.MOUNT_SPIDER;
        return null;
    }

    /**
     * Your own units whose centre is inside the screen and pass the filter (the selection strip's double-click:
     * every unit of the hovered type in view). Same frustum test as getMilitaryUnitsOnScreen.
     */
    public static List<LivingEntity> getOwnedUnitsOnScreen(java.util.function.Predicate<LivingEntity> filter) {
        ArrayList<Vec3> uvwpFull = MyMath.prepIsPointInsideRect3d(MC,
                0, 0,
                0, MC.getWindow().getGuiScaledHeight(),
                MC.getWindow().getGuiScaledWidth(), MC.getWindow().getGuiScaledHeight()
        );
        ArrayList<LivingEntity> units = new ArrayList<>();
        for (LivingEntity entity : MiscUtil.getEntitiesWithinRange(CursorClientEvents.getCursorWorldPos(), 100, LivingEntity.class, MC.level)) {
            if (entity instanceof Unit && entity.isAlive() &&
                    getPlayerToEntityRelationship(entity) == Relationship.OWNED &&
                    filter.test(entity) &&
                    MyMath.isPointInsideRect3d(uvwpFull, entity.getBoundingBox().getCenter()))
                units.add(entity);
        }
        return units;
    }

    public static List<LivingEntity> getMilitaryUnitsOnScreen() {
        ArrayList<Vec3> uvwpFull = MyMath.prepIsPointInsideRect3d(MC,
                0, 0, // top left
                0, MC.getWindow().getGuiScaledHeight(), // bottom left
                MC.getWindow().getGuiScaledWidth(), MC.getWindow().getGuiScaledHeight() // bottom right
        );

        ArrayList<LivingEntity> units = new ArrayList<>();
        for (LivingEntity entity : MiscUtil.getEntitiesWithinRange(CursorClientEvents.getCursorWorldPos(), 100, LivingEntity.class, MC.level)) {
            if (MyMath.isPointInsideRect3d(uvwpFull, entity.getBoundingBox().getCenter()) &&
                    MC.player != null &&
                    entity.getId() != MC.player.getId() &&
                    entity instanceof Unit unit &&
                    !(entity instanceof WorkerUnit) &&
                    entity instanceof AttackerUnit &&
                    !unit.isScout() &&
                    GarrisonableBuildingAddon.getGarrison(unit) == null &&
                    getPlayerToEntityRelationship(entity) == Relationship.OWNED
            )
                units.add(entity);
        }
        return units;
    }

    public static void syncUnitEatingFood(int unitId, int itemId) {
        for (LivingEntity entity : getAllUnits()) {
            if (unitId == entity.getId() && entity instanceof Unit unit) {
                unit.getItems().add(new ItemStack(BuiltInRegistries.ITEM.byId(itemId)));
                break;
            }
        }
    }

    @SubscribeEvent
    public static void onMobEffectAdded(MobEffectEvent.Added evt) {
        LivingEntity entity = evt.getEntity();
        if (!(entity instanceof Unit))
            return;

        MobEffectInstance mei = evt.getEffectInstance();

        synchronized (mobEffectIcons) {
            // add/update mob effect icon
            mobEffectIcons
                    .computeIfAbsent(entity.getId(), id -> new HashMap<>())
                    .put(mei.getEffect(), MobEffectIcons.getIcon(mei));
        }
        if (entity instanceof RangeIndicator ri)
            ri.updateHighlightBps(entity.level());
    }

    @SubscribeEvent
    public static void onMobEffectRemoved(MobEffectEvent.Remove evt) {
        LivingEntity entity = evt.getEntity();
        if (!(entity instanceof Unit))
            return;

        removeMobEffectIcon(entity.getId(), evt.getEffect());
        if (entity instanceof RangeIndicator ri)
            ri.updateHighlightBps(entity.level());
    }

    // Without this, icons stay behind when an effect simply runs out
    @SubscribeEvent
    public static void onMobEffectExpired(MobEffectEvent.Expired evt) {
        LivingEntity entity = evt.getEntity();
        MobEffectInstance mei = evt.getEffectInstance();
        if (!(entity instanceof Unit) || mei == null)
            return;

        removeMobEffectIcon(entity.getId(), mei.getEffect());
        if (entity instanceof RangeIndicator ri)
            ri.updateHighlightBps(entity.level());
    }

    private static void removeMobEffectIcon(int entityId, MobEffect effect) {
        synchronized (mobEffectIcons) {
            // remove mob effect icon
            HashMap<MobEffect, MobEffectIcon> icons = mobEffectIcons.get(entityId);
            if (icons != null) {
                icons.remove(effect);
                if (icons.isEmpty())
                    mobEffectIcons.remove(entityId);
            }
        }
    }

    public static void syncMobEffect(int entityId, int effectId, int amplifier, int duration) {
        MobEffect effect = MobEffect.byId(effectId);
        if (effect == null)
            return;

        for (LivingEntity entity : getAllUnits()) {
            if (entityId == entity.getId() && entity instanceof Unit) {
                if (duration > 0) {
                    entity.addEffect(new MobEffectInstance(effect, duration, amplifier));
                } else if (entity.getEffect(effect) != null) {
                    entity.removeEffect(effect);
                }
            }
        }
    }

    /*
    @SubscribeEvent
    public static void onButtonPress2(ScreenEvent.KeyPressed.Pre evt) {
        if (evt.getKeyCode() == GLFW.GLFW_KEY_P) {
            if (AFTER_SKY.equals(stage)) {
                stage = AFTER_SOLID_BLOCKS;
            } else if (AFTER_SOLID_BLOCKS.equals(stage)) {
                stage = AFTER_CUTOUT_MIPPED_BLOCKS_BLOCKS;
            } else if (AFTER_CUTOUT_MIPPED_BLOCKS_BLOCKS.equals(stage)) {
                stage = AFTER_CUTOUT_BLOCKS;
            } else if (AFTER_CUTOUT_BLOCKS.equals(stage)) {
                stage = AFTER_ENTITIES;
            } else if (AFTER_ENTITIES.equals(stage)) {
                stage = AFTER_BLOCK_ENTITIES;
            } else if (AFTER_BLOCK_ENTITIES.equals(stage)) {
                stage = AFTER_TRANSLUCENT_BLOCKS;
            } else if (AFTER_TRANSLUCENT_BLOCKS.equals(stage)) {
                stage = AFTER_TRIPWIRE_BLOCKS;
            } else if (AFTER_TRIPWIRE_BLOCKS.equals(stage)) {
                stage = AFTER_PARTICLES;
            } else if (AFTER_PARTICLES.equals(stage)) {
                stage = AFTER_WEATHER;
            } else if (AFTER_WEATHER.equals(stage)) {
                stage = AFTER_LEVEL;
            } else if (AFTER_LEVEL.equals(stage)) {
                stage = AFTER_SKY;
            }
        }
    }

    @SubscribeEvent
    public static void onRenderOverLay(RenderGuiOverlayEvent.Pre evt) {
        MiscUtil.drawDebugStrings(evt.getGuiGraphics(), MC.font, new String[] {
            "stage: " + stage.toString(),
        });
    }
     */

    /*
    public static int yOffset = 0;
    public static int scale = 0;

    @SubscribeEvent
    public static void onButtonPress2(ScreenEvent.KeyPressed.Pre evt) {
        if (evt.getKeyCode() == GLFW.GLFW_KEY_LEFT || evt.getKeyCode() == GLFW.GLFW_KEY_RIGHT) {
            int sign = evt.getKeyCode() == GLFW.GLFW_KEY_LEFT ? -1 : 1;
            yOffset += sign;
        }
        else if (evt.getKeyCode() == GLFW.GLFW_KEY_UP || evt.getKeyCode() == GLFW.GLFW_KEY_DOWN) {
            int sign = evt.getKeyCode() == GLFW.GLFW_KEY_UP ? -1 : 1;
            scale += sign;
        }
    }

    @SubscribeEvent
    public static void onRenderOverLay(RenderGuiOverlayEvent.Pre evt) {
        MiscUtil.drawDebugStrings(evt.getGuiGraphics(), MC.font, new String[] {
                "yOffset: " +  yOffset,
                "scale: " + scale,
        });
    }
     */

    /*
    @SubscribeEvent
    public static void onKeyPress(ScreenEvent.KeyPressed.Pre evt) {

        if (!getSelectedUnits().isEmpty() && getSelectedUnits().get(0) instanceof SlimeUnit slime) {
            if (evt.getKeyCode() == GLFW.GLFW_KEY_W) {
                slime.sideAngleX += 90;
            } else if (evt.getKeyCode() == GLFW.GLFW_KEY_A) {
                slime.sideAngleY -= 90;
            } else if (evt.getKeyCode() == GLFW.GLFW_KEY_S) {
                slime.sideAngleX -= 90;
            } else if (evt.getKeyCode() == GLFW.GLFW_KEY_D) {
                slime.sideAngleY += 90;
            }
        }*/


        /*
        if (Keybindings.altMod.isDown() && evt.getKeyCode() == GLFW.GLFW_KEY_SPACE && !getAllUnits().isEmpty()) {
            PacketHandler.INSTANCE.sendToServer(new UnitActionServerboundPacket(
                    "",
                    UnitAction.DEBUG1, 0, new int[]{0},
                    new BlockPos(0,0,0),
                    new BlockPos(0,0,0)
            ));
        }
        if (Keybindings.ctrlMod.isDown() && evt.getKeyCode() == GLFW.GLFW_KEY_SPACE && !getAllUnits().isEmpty()) {
            PacketHandler.INSTANCE.sendToServer(new UnitActionServerboundPacket(
                    "",
                    UnitAction.DEBUG2, 0, new int[]{0},
                    new BlockPos(0,0,0),
                    new BlockPos(0,0,0)
            ));
        }


    }*/

    /*
    public static int pitch = 0;
    public static int yaw = 0;

    @SubscribeEvent
    public static void onButtonPress2(ScreenEvent.KeyPressed.Pre evt) {
        if (evt.getKeyCode() == GLFW.GLFW_KEY_LEFT || evt.getKeyCode() == GLFW.GLFW_KEY_RIGHT) {
            int sign = evt.getKeyCode() == GLFW.GLFW_KEY_LEFT ? -1 : 1;
            yaw += sign;
        }
        else if (evt.getKeyCode() == GLFW.GLFW_KEY_UP || evt.getKeyCode() == GLFW.GLFW_KEY_DOWN) {
            int sign = evt.getKeyCode() == GLFW.GLFW_KEY_UP ? -1 : 1;
            pitch += sign;
        }
    }

     */
    /*
    @SubscribeEvent
    public static void onRenderOverLay(RenderGuiOverlayEvent.Pre evt) {
        if (!getSelectedUnits().isEmpty() && getSelectedUnits().get(0) instanceof SlimeUnit slime) {
            MiscUtil.drawDebugStrings(evt.getGuiGraphics(), MC.font, new String[]{
                    "rollAngle: " + slime.rollAngle,
                    "sideAngleX: " + slime.sideAngleX,
                    "sideAngleY: " + slime.sideAngleY,
            });
        }
    }
     */
}
