package com.solegendary.reignofnether.resources;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.solegendary.reignofnether.blocks.BlockUtils;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.BuildingServerEvents;
import com.solegendary.reignofnether.building.BuildingUtils;
import com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement;
import com.solegendary.reignofnether.building.production.ActiveProduction;
import com.solegendary.reignofnether.perf.Sys;
import com.solegendary.reignofnether.player.PlayerServerEvents;
import com.solegendary.reignofnether.player.RTSPlayer;
import com.solegendary.reignofnether.registrars.GameRuleRegistrar;
import com.solegendary.reignofnether.sandbox.SandboxServer;
import com.solegendary.reignofnether.scenario.ScenarioRole;
import com.solegendary.reignofnether.scenario.ScenarioUtils;
import com.solegendary.reignofnether.sounds.SoundAction;
import com.solegendary.reignofnether.sounds.SoundClientboundPacket;
import com.solegendary.reignofnether.tutorial.TutorialServerEvents;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.*;

import static com.solegendary.reignofnether.blocks.BlockUtils.isLogBlock;

public class ResourcesServerEvents {

    // tracks all players' resources
    public static ArrayList<Resources> resourcesList = new ArrayList<>();

    // BAR economy: only Metal (the 'ore' field) and Energy (the 'wood' field) are used; food is always 0.
    // Every mode starts with 1000 metal and 1000 energy, except sandbox. Storage starts at 1500/1500 with the capitol
    // (EconomyServerEvents), so there is room to bank income before the first spend - like BAR.
    public static final int STARTING_METAL = 1000;
    public static final int STARTING_ENERGY = 1000;
    public static final int STARTING_FOOD_TUTORIAL = 0;
    public static final int STARTING_WOOD_TUTORIAL = STARTING_ENERGY;
    public static final int STARTING_ORE_TUTORIAL = STARTING_METAL;
    public static final int STARTING_FOOD_SANDBOX = 999999;
    public static final int STARTING_WOOD_SANDBOX = 999999;
    public static final int STARTING_ORE_SANDBOX = 999999;
    public static final int STARTING_EMERALD_SANDBOX = 999999;
    public static final int STARTING_FOOD = 0;
    public static final int STARTING_WOOD = STARTING_ENERGY;
    public static final int STARTING_ORE = STARTING_METAL;
    public static final int STARTING_FOOD_READIED = 0;
    public static final int STARTING_WOOD_READIED = STARTING_ENERGY;
    public static final int STARTING_ORE_READIED = STARTING_METAL;

    public static final float UNIT_BOUNTY_PERCENT_PER_LOOTING_LEVEL = 0.25f;
    public static final float NEUTRAL_UNIT_BOUNTY_PERCENT = 0.25f;
    public static final float NEUTRAL_BUILDING_BOUNTY_PERCENT = 0.25f;

    // to avoid having to save units too often add on all unit resources here too and just add directly on load
    public static void saveResources(ServerLevel serverLevel) {
        if (serverLevel == null) {
            return;
        }
        ResourcesSaveData data = ResourcesSaveData.getInstance(serverLevel);
        data.resources.clear();
        resourcesList.forEach(r -> {

            // add all unit held resources to resources so we don't have to save unit items
            int unitFood = 0;
            int unitWood = 0;
            int unitOre = 0;
            int unitEmerald = 0;
            for (LivingEntity le : UnitServerEvents.getAllUnits()) {
                if (le instanceof Unit u && u.getOwnerName().equals(r.ownerName)) {
                    Resources unitRes = Resources.getTotalResourcesFromItems(u.getItems());
                    unitFood += unitRes.food;
                    unitWood += unitRes.wood;
                    unitOre += unitRes.ore;
                    unitEmerald += unitRes.emerald;
                }
            }
            // add back what was already spent on production items since they will be cancelled on server shutdown
            // (BAR flow economy: only the spent part of each item has been paid; emeralds are still paid upfront)
            int prodFood = 0;
            int prodWood = 0;
            int prodOre = 0;
            int prodEmerald = 0;
            for (BuildingPlacement building : BuildingServerEvents.getBuildings()) {
                if (building instanceof ProductionPlacement pBuilding && pBuilding.ownerName.equals(r.ownerName)) {
                    for (ActiveProduction item : pBuilding.productionQueue) {
                        prodWood += Math.round(item.energySpent);
                        prodOre += Math.round(item.metalSpent);
                        prodEmerald += item.item.getCost(false, pBuilding.ownerName).emerald;
                    }
                }
            }
            data.resources.add(new Resources(r.ownerName,
                r.food + r.foodToAdd + unitFood + prodFood,
                r.wood + r.woodToAdd + unitWood + prodWood,
                r.ore + r.oreToAdd + unitOre + prodOre,
                r.emerald + r.emeraldToAdd + unitEmerald + prodEmerald
            ));
            //ReignOfNether.LOGGER.info("saved resources in serverevents: " + r.ownerName + "|" + r.food + "|" + r.wood + "|" + r.ore);
        });
        data.save();   // dirty only: the world save writes it (persistence/RtsSaves)
    }

    @SubscribeEvent
    public static void loadResources(ServerStartedEvent evt) {
        ServerLevel level = evt.getServer().getLevel(Level.OVERWORLD);

        if (level != null) {
            ResourcesSaveData data = ResourcesSaveData.getInstance(level);
            resourcesList.clear();
            resourcesList.addAll(data.resources);

            //ReignOfNether.LOGGER.info("saved " + data.resources.size() + " resources in serverevents");
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent evt) {
        if (evt.phase != TickEvent.Phase.END)
            return;
        // BAR flow economy: income, stall calculation and client sync
        int sysEconomy = Sys.begin(Sys.ECONOMY);
        try {
            EconomyServerEvents.onServerTickEnd();
        } finally {
            Sys.end(sysEconomy);
        }
        // resources reach the disk with Minecraft's world save (persistence/RtsSaves, plan 014), not every 30 s
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent evt) {
        ServerLevel level = evt.getServer().getLevel(Level.OVERWORLD);
        if (level != null) {
            saveResources(level);
        }
    }

    public static void resetResources(String playerName, boolean readiedStart) {
        for (Resources resources : resourcesList) {
            if (resources.ownerName.equals(playerName)) {
                if (TutorialServerEvents.isEnabled()) {
                    resources.food = STARTING_FOOD_TUTORIAL;
                    resources.wood = STARTING_WOOD_TUTORIAL;
                    resources.ore = STARTING_ORE_TUTORIAL;
                } else if (SandboxServer.isSandboxPlayer(playerName)) {
                    resources.food = STARTING_FOOD_SANDBOX;
                    resources.wood = STARTING_WOOD_SANDBOX;
                    resources.ore = STARTING_ORE_SANDBOX;
                    resources.emerald = STARTING_EMERALD_SANDBOX;
                } else if (readiedStart) {
                    resources.food = STARTING_FOOD_READIED;
                    resources.wood = STARTING_WOOD_READIED;
                    resources.ore = STARTING_ORE_READIED;
                } else {
                    resources.food = STARTING_FOOD;
                    resources.wood = STARTING_WOOD;
                    resources.ore = STARTING_ORE;
                }
                ResourcesClientboundPacket.syncResources(resourcesList);
                break;
            }
        }
    }

    public static void addSubtractResources(Resources resourcesToAdd) {
        for (Resources resources : resourcesList) {
            if (resources.ownerName.equals(resourcesToAdd.ownerName)) {
                // change serverside instantly
                resources.changeInstantly(resourcesToAdd.food, resourcesToAdd.wood, resourcesToAdd.ore, resourcesToAdd.emerald);
                // change clientside over time
                ResourcesClientboundPacket.addSubtractResources(new Resources(resourcesToAdd.ownerName,
                    resourcesToAdd.food,
                    resourcesToAdd.wood,
                    resourcesToAdd.ore,
                    resourcesToAdd.emerald
                ));
            }
        }
    }

    public static boolean canAfford(String ownerName, ResourceName resourceName, int cost) {
        if (cost <= 0) {
            return true;
        }
        for (Resources resources : ResourcesServerEvents.resourcesList)
            if (resources.ownerName.equals(ownerName)) {
                switch (resourceName) {
                    case FOOD -> {
                        return resources.food >= cost;
                    }
                    case WOOD -> {
                        return resources.wood >= cost;
                    }
                    case ORE -> {
                        return resources.ore >= cost;
                    }
                    case EMERALD -> {
                        return resources.emerald >= cost;
                    }
                }
            }
        return false;
    }

    @SubscribeEvent
    public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent evt) {
        String playerName = evt.getEntity().getName().getString();
        ResourcesClientboundPacket.syncResources(resourcesList);
    }

    public static void assignResources(String playerName) {
        resourcesList.removeIf(r -> r.ownerName.equals(playerName));
        Resources resources;
        if (TutorialServerEvents.isEnabled()) {
            resources = new Resources(playerName,
                    STARTING_FOOD_TUTORIAL,
                    STARTING_WOOD_TUTORIAL,
                    STARTING_ORE_TUTORIAL
            );
        } else {
            resources = new Resources(playerName, STARTING_FOOD, STARTING_WOOD, STARTING_ORE);
        }
        resourcesList.add(resources);
        ResourcesClientboundPacket.syncResources(resourcesList);
    }

    public static void assignScenarioResources(RTSPlayer rtsPlayer) {
        ScenarioRole role = ScenarioUtils.getScenarioRole(false, rtsPlayer.scenarioRoleIndex);
        resourcesList.removeIf(r -> r.ownerName.equals(rtsPlayer.name));
        Resources resources;
        resources = role == null ?
                new Resources(rtsPlayer.name, 0,0,0) :
                new Resources(rtsPlayer.name, role.startingResources.food, role.startingResources.wood, role.startingResources.ore);
        resourcesList.add(resources);
        ResourcesClientboundPacket.syncResources(resourcesList);
    }

    // prevent vanilla growth mechanics because they're slow and random, see FarmPlacement instead
    @SubscribeEvent
    public static void onCropGrow(BlockEvent.CropGrowEvent.Pre evt) {
        if (BuildingUtils.isPosInsideAnyBuilding(evt.getLevel().isClientSide(), evt.getPos()))
            evt.setResult(Event.Result.DENY);
    }

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent evt) {
        if (evt.getEntity() instanceof ItemEntity ie &&
                (ie.getItem().getItem() == Items.POTATO || ie.getItem().getItem() == Items.CARROT) &&
                BuildingUtils.isPosInsideAnyBuilding(false, ie.getOnPos())) {
            evt.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onPlayerBlockBreak(BlockEvent.BreakEvent evt) {
        if (BuildingUtils.isPosInsideAnyBuilding(false, evt.getPos())) {
            evt.setCanceled(true);
            if (evt.getLevel() instanceof ServerLevel serverLevel) {
                serverLevel.setBlockAndUpdate(evt.getPos(), Blocks.AIR.defaultBlockState());
            }
        }

        if (isLogBlock(evt.getState()) && !BuildingUtils.isPosInsideAnyBuilding(false, evt.getPos())) {
            fellAdjacentLogs(evt.getPos(), (Level) evt.getLevel());
        }
    }

    /**
     * The 17 neighbours a felled log takes with it: the four sides and four diagonals on its own level, the block above
     * and that block's eight neighbours - a trunk, its branches and a leaning crown, never anything below.
     */
    private static final int[][] FELL_NEIGHBOURS = {
        {1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}, {1, 0, 1}, {1, 0, -1}, {-1, 0, 1}, {-1, 0, -1},
        {0, 1, 0}, {1, 1, 0}, {-1, 1, 0}, {0, 1, 1}, {0, 1, -1}, {1, 1, 1}, {1, 1, -1}, {-1, 1, 1}, {-1, 1, -1},
    };

    /**
     * A log at {@code bp} was cut: every log reachable from it through {@link #FELL_NEIGHBOURS} (same level or above,
     * never below) becomes its falling block, so no tree is left standing on air; a reachable log that has no falling
     * block or stands inside a building is left as it is but still carries the walk on. The cut block itself is the
     * caller's (the gather goal destroys it; a player's break event removes it right after).
     * <p>
     * Walked breadth-first with a set, once per block: the recursive version it replaces walked every log twice (it
     * recursed from each neighbour, then again after the if), checked "seen" with a List scan (quadratic over a big
     * trunk) and had no depth bound, so a long log structure could have overflowed the stack.
     */
    public static void fellAdjacentLogs(BlockPos bp, Level level) {
        if (SandboxServer.isAnyoneASandboxPlayer())
            return;
        if (!level.getGameRules().getRule(GameRuleRegistrar.LOG_FALLING).get())
            return;

        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> open = new ArrayDeque<>();
        seen.add(bp);
        open.add(bp);
        while (!open.isEmpty()) {
            BlockPos at = open.poll();
            for (int[] d : FELL_NEIGHBOURS) {
                BlockPos next = at.offset(d[0], d[1], d[2]);
                if (seen.contains(next))
                    continue;
                BlockState bs = level.getBlockState(next);
                if (!isLogBlock(bs))
                    continue;
                seen.add(next);
                open.add(next);
                BlockState falling = BlockUtils.getFallingLog(bs);
                if (falling != null && !BuildingUtils.isPosInsideAnyBuilding(level.isClientSide(), next))
                    level.setBlockAndUpdate(next, falling);
            }
        }
    }

    @SubscribeEvent
    public static void onRegisterCommand(RegisterCommandsEvent evt) {

        evt.getDispatcher().register(Commands.literal("sendfood")
            .then(Commands.argument("player", EntityArgument.player())
            .then(Commands.argument("amount", IntegerArgumentType.integer(1, Integer.MAX_VALUE))
            .executes((command) -> trySendingResources(command, ResourceName.FOOD)))));

        evt.getDispatcher().register(Commands.literal("sendwood")
            .then(Commands.argument("player", EntityArgument.player())
            .then(Commands.argument("amount", IntegerArgumentType.integer(1, Integer.MAX_VALUE))
            .executes((command) -> trySendingResources(command, ResourceName.WOOD)))));

        evt.getDispatcher().register(Commands.literal("sendore")
            .then(Commands.argument("player", EntityArgument.player())
            .then(Commands.argument("amount", IntegerArgumentType.integer(1, Integer.MAX_VALUE))
            .executes((command) -> trySendingResources(command, ResourceName.ORE)))));

        evt.getDispatcher().register(Commands.literal("sendemerald")
            .then(Commands.argument("player", EntityArgument.player())
            .then(Commands.argument("amount", IntegerArgumentType.integer(1, Integer.MAX_VALUE))
            .executes((command) -> trySendingResources(command, ResourceName.EMERALD)))));
    }

    public static int trySendingResources(CommandContext<CommandSourceStack> context, ResourceName resourceName) throws CommandSyntaxException {
        Player sendingPlayer = context.getSource().getPlayer();
        Player receivingPlayer = EntityArgument.getPlayer(context, "player");
        int amount = IntegerArgumentType.getInteger(context, "amount");
        if (sendingPlayer == null) {
            return 0;
        } else {
            String sendingPlayerName = sendingPlayer.getName().getString();
            String receivingPlayerName = receivingPlayer.getName().getString();
            return trySendingResources(sendingPlayerName, receivingPlayerName, resourceName, amount);
        }
    }

    // send resources including any available when you don't have enough
    public static void trySendingAnyResources(String receivingPlayerName, Resources sentResources) {
        Resources res = null;
        for (Resources resources : resourcesList)
            if (resources.ownerName.equals(sentResources.ownerName))
                res = resources;
        if (res != null) {
            int foodAmount = Math.min(res.food, sentResources.food);
            if (foodAmount > 0)
                trySendingResources(sentResources.ownerName,receivingPlayerName, ResourceName.FOOD, foodAmount);
            int woodAmount = Math.min(res.wood, sentResources.wood);
            if (woodAmount > 0)
                trySendingResources(sentResources.ownerName,receivingPlayerName, ResourceName.WOOD, woodAmount);
            int oreAmount = Math.min(res.ore, sentResources.ore);
            if (oreAmount > 0)
                trySendingResources(sentResources.ownerName,receivingPlayerName, ResourceName.ORE, oreAmount);
            int emeraldAmount = Math.min(res.emerald, sentResources.emerald);
            if (emeraldAmount > 0)   // was oreAmount: emeralds were never sent without metal, and 0 was sent with it
                trySendingResources(sentResources.ownerName,receivingPlayerName, ResourceName.EMERALD, emeraldAmount);

            if (sentResources.getTotalValue() > 0)
                SoundClientboundPacket.playSoundForPlayer(SoundAction.CHAT, receivingPlayerName);
        }
    }

    public static int trySendingResources(String sendingPlayerName, String receivingPlayerName, ResourceName resourceName, int amount) {
        Resources res = null;
        for (Resources resources : resourcesList)
            if (resources.ownerName.equals(sendingPlayerName))
                res = resources;
        if (res == null)
            return 0;

        if (sendingPlayerName.equals(receivingPlayerName)) {
            PlayerServerEvents.sendMessageToPlayer(sendingPlayerName, "server.resources.reignofnether.sending_to_self");
            return 0;
        } else if (!PlayerServerEvents.isRTSPlayer(receivingPlayerName)) {
            PlayerServerEvents.sendMessageToPlayer(sendingPlayerName, "server.resources.reignofnether.not_rts_player");
            return 0;
        } else if (!canAfford(sendingPlayerName, resourceName, amount)) {
            ResourcesClientboundPacket.warnInsufficientResources(sendingPlayerName,
                    resourceName == ResourceName.FOOD,
                    resourceName == ResourceName.WOOD,
                    resourceName == ResourceName.ORE,
                    resourceName == ResourceName.EMERALD
            );
            return 0;
        } else {
            addSubtractResources(new Resources(sendingPlayerName,
                    resourceName == ResourceName.FOOD ? -amount : 0,
                    resourceName == ResourceName.WOOD ? -amount : 0,
                    resourceName == ResourceName.ORE ? -amount : 0,
                    resourceName == ResourceName.EMERALD ? -amount : 0
            ));
            addSubtractResources(new Resources(receivingPlayerName,
                    resourceName == ResourceName.FOOD ? amount : 0,
                    resourceName == ResourceName.WOOD ? amount : 0,
                    resourceName == ResourceName.ORE ? amount : 0,
                    resourceName == ResourceName.EMERALD ? amount : 0
            ));
            switch (resourceName) {
                case FOOD -> {
                    PlayerServerEvents.sendMessageToPlayer(sendingPlayerName, "server.resources.reignofnether.sent_food", false, amount, receivingPlayerName);
                    PlayerServerEvents.sendMessageToPlayer(receivingPlayerName, "server.resources.reignofnether.received_food", false, amount, sendingPlayerName);
                }
                case WOOD -> {
                    PlayerServerEvents.sendMessageToPlayer(sendingPlayerName, "server.resources.reignofnether.sent_wood", false, amount, receivingPlayerName);
                    PlayerServerEvents.sendMessageToPlayer(receivingPlayerName, "server.resources.reignofnether.received_wood", false, amount, sendingPlayerName);
                }
                case ORE -> {
                    PlayerServerEvents.sendMessageToPlayer(sendingPlayerName, "server.resources.reignofnether.sent_ore", false, amount, receivingPlayerName);
                    PlayerServerEvents.sendMessageToPlayer(receivingPlayerName, "server.resources.reignofnether.received_ore", false, amount, sendingPlayerName);
                }
                case EMERALD -> {
                    PlayerServerEvents.sendMessageToPlayer(sendingPlayerName, "server.resources.reignofnether.sent_emerald", false, amount, receivingPlayerName);
                    PlayerServerEvents.sendMessageToPlayer(receivingPlayerName, "server.resources.reignofnether.received_emerald", false, amount, sendingPlayerName);
                }
            }
            return 1;
        }
    }
}
