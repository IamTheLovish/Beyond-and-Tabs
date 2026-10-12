package com.solegendary.reignofnether.onboarding;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.ability.Ability;
import com.solegendary.reignofnether.ability.abilities.CommanderDGun;
import com.solegendary.reignofnether.api.ReignOfNetherRegistries;
import com.solegendary.reignofnether.bot.BotPlayer;
import com.solegendary.reignofnether.bot.BotServerEvents;
import com.solegendary.reignofnether.building.Building;
import com.solegendary.reignofnether.building.BuildingBlock;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.BuildingServerEvents;
import com.solegendary.reignofnether.building.Buildings;
import com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement;
import com.solegendary.reignofnether.faction.Faction;
import com.solegendary.reignofnether.faction.Factions;
import com.solegendary.reignofnether.matchstart.SkirmishServerboundPacket;
import com.solegendary.reignofnether.player.CommanderServerEvents;
import com.solegendary.reignofnether.player.PlayerServerEvents;
import com.solegendary.reignofnether.player.RTSPlayer;
import com.solegendary.reignofnether.resources.MetalPatches;
import com.solegendary.reignofnether.resources.Resources;
import com.solegendary.reignofnether.resources.ResourcesServerEvents;
import com.solegendary.reignofnether.resources.WreckServerEvents;
import com.solegendary.reignofnether.startpos.BattlefieldSetup;
import com.solegendary.reignofnether.unit.UnitAction;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.interfaces.AttackerUnit;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.interfaces.WorkerUnit;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server side of the "First Battle" tutorial (design/onboarding.md): lays out the scenario (the student's readied
 * start, a passive sparring bot a short walk away, a small arena), owns each student's step and finishes it when the
 * real thing happens in the world.
 *
 * Cost: nothing at all outside a tutorial (every handler returns on an empty session map). Inside one, a poll every
 * {@link #POLL_TICKS} ticks walks that student's buildings and the unit list once - a tutorial is a 1v1 with a
 * handful of units, never an 8v8. Packets go out only when the step, its highlight or its status changes.
 */
public class OnboardingServer {

    static final int POLL_TICKS = 10;
    /** Sparring bot distance from the student's base: a short walk, inside the small arena. */
    static final int ENEMY_MIN_R = 80, ENEMY_MAX_R = 110;
    static final int ARENA_RADIUS = 230;   // the skirmish lobby's "small"
    static final String ENEMY_BASE_NAME = "Rival";
    static final int WRECKS = 6;
    static final float WRECK_METAL = 60f;

    static final Map<String, OnboardingSession> sessions = new ConcurrentHashMap<>();
    private static MinecraftServer server = null;

    public static OnboardingSession session(String student) {
        return student == null ? null : sessions.get(student);
    }

    public static boolean isActive() {
        return !sessions.isEmpty();
    }

    /** Whether this bot name is a First Battle sparring partner (its brain must stay passive). */
    public static boolean isSparringBot(String name) {
        if (name == null || name.isEmpty())
            return false;
        for (OnboardingSession s : sessions.values())
            if (name.equals(s.enemy))
                return true;
        return false;
    }

    // ------------------------------------------------------------------ lifecycle

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent evt) {
        server = evt.getServer();
        sessions.clear();
        try {
            sessions.putAll(OnboardingSaveData.get(server).sessions);
        } catch (Exception e) {
            ReignOfNether.LOGGER.error("[Onboarding] could not read the tutorial save", e);
        }
        keepSparringBotsPassive();
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent evt) {
        sessions.clear();
        server = null;
    }

    @SubscribeEvent
    public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent evt) {
        if (!(evt.getEntity() instanceof ServerPlayer sp))
            return;
        OnboardingSession s = sessions.get(sp.getName().getString());
        if (s != null)
            OnboardingClientboundPacket.step(sp, s);
    }

    private static void save() {
        if (server == null)
            return;
        try {
            OnboardingSaveData data = OnboardingSaveData.get(server);
            data.sessions.clear();
            data.sessions.putAll(sessions);
            data.setDirty();
        } catch (Exception e) {
            ReignOfNether.LOGGER.error("[Onboarding] could not save the tutorial", e);
        }
    }

    /**
     * The bot loop rebuilds a brain only when it is missing, so after a reload it would give the sparring bot a real
     * (building, attacking) brain. Put the passive one back first - at server start and, should the bot loop have run
     * first, on every tick (a map lookup per session).
     */
    static void keepSparringBotsPassive() {
        for (OnboardingSession s : sessions.values()) {
            if (s.enemy.isEmpty() || BotServerEvents.brains.get(s.enemy) instanceof PassiveBot)
                continue;
            RTSPlayer bot = PlayerServerEvents.getRTSPlayer(s.enemy);
            if (bot != null)
                BotServerEvents.brains.put(s.enemy, new PassiveBot(s.enemy, bot.faction, s.enemyHome));
        }
    }

    // ------------------------------------------------------------------ scenario

    /**
     * Title screen -> First Battle -> Start: the student's readied start (capitol foundations, commander, workers) in a
     * small arena, and a passive sparring bot of another faction ~100 blocks away with its own capitol. A student who
     * already has a session just gets it back (Resume).
     */
    static void start(ServerPlayer sp, int factionCode) {
        String name = sp.getName().getString();
        OnboardingSession existing = sessions.get(name);
        if (existing != null) {
            OnboardingClientboundPacket.step(sp, existing);
            return;
        }
        if (PlayerServerEvents.isRTSPlayer(name)) {
            sp.sendSystemMessage(Component.translatable("onboarding.reignofnether.already_in_match"));
            return;
        }
        ServerLevel level = sp.serverLevel();
        Random rng = new Random();
        Faction mine = SkirmishServerboundPacket.factionOf(factionCode, rng);
        Faction foe = sparringFaction(mine);

        BattlefieldSetup.arenaRadiusOverride = ARENA_RADIUS;
        BattlefieldSetup.richnessOverride = 1.0f;
        // fast pace: buildings and units finish while the lesson is still on screen
        com.solegendary.reignofnether.resources.EconomyPace.setMatchCode(2);

        int gx = sp.getBlockX(), gz = sp.getBlockZ();
        int gy = MetalPatches.solidTop(level, gx, gz);
        BlockPos home = new BlockPos(gx, gy, gz);
        PlayerServerEvents.startRTS(sp.getId(), new Vec3(gx + 0.5, gy, gz + 0.5), mine, 1);
        if (!PlayerServerEvents.isRTSPlayer(name)) {
            sp.sendSystemMessage(Component.translatable("onboarding.reignofnether.start_failed"));
            return;
        }
        Vec3 enemyPos = enemySite(level, home);
        String enemy = freeEnemyName();
        BlockPos enemyHome = setupSparringBot(level, enemy, foe, enemyPos);

        OnboardingSession s = new OnboardingSession(name);
        s.enemy = enemy;
        s.home = home;
        s.enemyHome = enemyHome;
        sessions.put(name, s);
        save();
        OnboardingClientboundPacket.step(sp, s);
        ReignOfNether.LOGGER.info("[Onboarding] {} started the First Battle as {} against {} ({})", name,
            mine.getName(), enemy, foe.getName());
    }

    /** The sparring partner plays a faction other than the student's: Gravebound, or the Kingdom against them. */
    public static Faction sparringFaction(Faction mine) {
        return Factions.MONSTERS.equals(mine) ? Factions.VILLAGERS : Factions.MONSTERS;
    }

    static String freeEnemyName() {
        String n = ENEMY_BASE_NAME;
        for (int i = 2; PlayerServerEvents.isRTSPlayer(n) || isSparringBot(n); i++)
            n = ENEMY_BASE_NAME + " " + i;
        return n;
    }

    /**
     * Starts the sparring bot through the normal bot start (commander and workers, held chunks, the same battlefield
     * as the student since both arrive in the same tick), gives it the passive brain and lays its capitol, which
     * builds itself. Returns the bot's home.
     */
    public static BlockPos setupSparringBot(ServerLevel level, String enemy, Faction foe, Vec3 pos) {
        BlockPos home = BlockPos.containing(pos);
        PlayerServerEvents.startRTSBot(level, enemy, pos, foe, 0);
        BotServerEvents.brains.put(enemy, new PassiveBot(enemy, foe, home));
        Building capitol = ReignOfNetherRegistries.BUILDING.get(foe.capitolBuilding);
        if (capitol != null) {
            ArrayList<BuildingBlock> blocks = capitol.getRelativeBlockData(level);
            BlockPos origin = PlayerServerEvents.getBuildingOriginPos(home.offset(0, 0, 6), blocks);
            BuildingServerEvents.placeBuilding(capitol, origin, Rotation.NONE, enemy, new int[0], false, false, true, true);
        }
        return home;
    }

    /**
     * Where the sparring bot goes: {@link #ENEMY_MIN_R}-{@link #ENEMY_MAX_R} blocks from the student in one of 16
     * directions, on dry ground that is fairly flat around it, nearest the middle of the range when several fit. Falls
     * back to due east at the middle distance.
     */
    public static Vec3 enemySite(ServerLevel level, BlockPos home) {
        Vec3 best = null;
        double bestScore = -1e18;
        int mid = (ENEMY_MIN_R + ENEMY_MAX_R) / 2;
        for (int r = ENEMY_MIN_R; r <= ENEMY_MAX_R; r += 15)
            for (int k = 0; k < 16; k++) {
                double a = Math.PI * 2 * k / 16;
                int x = home.getX() + (int) Math.round(Math.cos(a) * r);
                int z = home.getZ() + (int) Math.round(Math.sin(a) * r);
                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                if (!level.getBlockState(new BlockPos(x, y - 1, z)).getFluidState().isEmpty())
                    continue;
                int minY = y, maxY = y;
                boolean wet = false;
                for (int dx = -8; dx <= 8; dx += 8)
                    for (int dz = -8; dz <= 8; dz += 8) {
                        int yy = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x + dx, z + dz);
                        minY = Math.min(minY, yy);
                        maxY = Math.max(maxY, yy);
                        if (!level.getBlockState(new BlockPos(x + dx, yy - 1, z + dz)).getFluidState().isEmpty())
                            wet = true;
                    }
                if (wet || maxY - minY > 8)
                    continue;
                double score = -(maxY - minY) * 10 - Math.abs(r - mid);
                if (score > bestScore) {
                    bestScore = score;
                    best = new Vec3(x + 0.5, y, z + 0.5);
                }
            }
        if (best == null) {
            int x = home.getX() + mid, z = home.getZ();
            best = new Vec3(x + 0.5, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z + 0.5);
        }
        return best;
    }

    /** The RECLAIM step's wreck field: a few heaps between the student's base and the enemy, 14 blocks out. */
    public static BlockPos spawnWreckField(ServerLevel level, BlockPos home, BlockPos toward, Faction look) {
        double dx = toward.getX() - home.getX(), dz = toward.getZ() - home.getZ();
        double len = Math.max(1, Math.sqrt(dx * dx + dz * dz));
        int cx = home.getX() + (int) Math.round(dx / len * 14), cz = home.getZ() + (int) Math.round(dz / len * 14);
        for (int i = 0; i < WRECKS; i++) {
            double a = Math.PI * 2 * i / WRECKS;
            int x = cx + (int) Math.round(Math.cos(a) * 2.5), z = cz + (int) Math.round(Math.sin(a) * 2.5);
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            WreckServerEvents.spawnWreck(level, x + 0.5, y, z + 0.5, WRECK_METAL, look);
        }
        return new BlockPos(cx, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, cx, cz), cz);
    }

    // ------------------------------------------------------------------ client requests

    static void onClientReport(ServerPlayer sp, int stepOrdinal) {
        OnboardingSession s = sessions.get(sp.getName().getString());
        if (s != null && s.clientReport(OnboardingStep.byOrdinal(stepOrdinal)))
            stepChanged(s);
    }

    static void onSkip(ServerPlayer sp, int stepOrdinal) {
        OnboardingSession s = sessions.get(sp.getName().getString());
        // a double click must not skip two steps: the skip names the step the player saw
        if (s != null && s.step.ordinal() == stepOrdinal && s.skip())
            stepChanged(s);
    }

    /** Quit: the session ends (the match and the bot stay, it is a normal skirmish from here). */
    static void onQuit(ServerPlayer sp) {
        OnboardingSession s = sessions.remove(sp.getName().getString());
        if (s != null) {
            save();
            OnboardingClientboundPacket.end(sp, "onboarding.reignofnether.quit");
        }
    }

    // ------------------------------------------------------------------ step detection

    /**
     * Hook from UnitActionServerboundPacket.accept: an order the student gave (after the ownership filter). MOVE,
     * ATTACK_MOVE and the line formation finish here.
     */
    public static void onOrder(String owner, UnitAction action, int unitCount, BlockPos target) {
        if (sessions.isEmpty() || owner == null)
            return;
        OnboardingSession s = sessions.get(owner);
        if (s == null || s.step.kind != OnboardingStep.Kind.SERVER)
            return;
        long tick = server != null ? server.overworld().getGameTime() : 0;
        if (s.onOrder(action, unitCount, target, tick))
            stepChanged(s);
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent evt) {
        if (evt.phase != TickEvent.Phase.END || sessions.isEmpty() || evt.getServer() == null)
            return;
        keepSparringBotsPassive();
        ServerLevel level = evt.getServer().overworld();
        if (level.getGameTime() % POLL_TICKS != 0)
            return;
        for (OnboardingSession s : sessions.values())
            poll(level, s);
    }

    /** One look at the world for this student: the step's setup on entry, then its completion check. */
    public static void poll(ServerLevel level, OnboardingSession s) {
        // the student lost (their commander fell): the tutorial ends with them
        if (s.step != OnboardingStep.DONE && s.step != OnboardingStep.WELCOME && !PlayerServerEvents.isRTSPlayer(s.student)
                && PlayerServerEvents.serverLevel != null && sessions.get(s.student) == s) {
            sessions.remove(s.student);
            save();
            ServerPlayer sp = player(s.student);
            if (sp != null)
                OnboardingClientboundPacket.end(sp, "onboarding.reignofnether.defeated");
            return;
        }
        if (!s.stepPrepared) {
            s.stepPrepared = true;
            prepare(level, s);
        }
        BlockPos oldHighlight = s.highlight;
        boolean oldProgress = s.inProgress;
        boolean done = check(level, s);
        if (done) {
            stepChanged(s);
        } else if (s.inProgress != oldProgress || movedFar(oldHighlight, s.highlight)) {
            ServerPlayer sp = player(s.student);
            if (sp != null)
                OnboardingClientboundPacket.step(sp, s);
        }
    }

    private static boolean movedFar(BlockPos a, BlockPos b) {
        if (a == null || b == null)
            return a != b;
        return a.distSqr(b) > 4;
    }

    /** Whatever the step needs in the world before the student can do it. */
    public static void prepare(ServerLevel level, OnboardingSession s) {
        switch (s.step) {
            case EXTRACTOR -> topUp(s.student, 400, 400);
            case ENERGY, LAB -> topUp(s.student, 700, 700);
            case TRAIN -> topUp(s.student, 800, 800);
            case RECLAIM -> {
                RTSPlayer foe = PlayerServerEvents.getRTSPlayer(s.enemy);
                s.highlight = spawnWreckField(level, s.home, s.enemyHome, foe != null ? foe.faction : Factions.MONSTERS);
            }
            case DGUN -> topUp(s.student, 0, CommanderDGun.ENERGY_COST * 3);
            default -> { }
        }
    }

    /** Raises the student's metal / energy to at least these amounts (nothing is taken away). */
    static void topUp(String owner, int metal, int energy) {
        for (Resources r : ResourcesServerEvents.resourcesList)
            if (r.ownerName.equals(owner)) {
                int dm = Math.max(0, metal - r.ore), de = Math.max(0, energy - r.wood);
                if (dm > 0 || de > 0)
                    ResourcesServerEvents.addSubtractResources(new Resources(owner, 0, de, dm));
                return;
            }
    }

    /** The step's completion check. True when it finished (the session has already advanced). */
    public static boolean check(ServerLevel level, OnboardingSession s) {
        switch (s.step) {
            case SELECT, DGUN -> {
                LivingEntity com = commanderOf(s.student);
                if (com != null)
                    s.highlight = com.blockPosition();
                if (s.step == OnboardingStep.DGUN && com instanceof Unit u && dgunFired(u)) {
                    s.advance();
                    return true;
                }
                return false;
            }
            case EXTRACTOR -> {
                if (s.highlight == null)
                    s.highlight = nearestPatch(level, s.home);
                return checkBuilding(s, BuildingServerEvents.getBuildings(), Need.EXTRACTOR);
            }
            case ENERGY -> {
                return checkBuilding(s, BuildingServerEvents.getBuildings(), Need.ENERGY);
            }
            case LAB -> {
                return checkBuilding(s, BuildingServerEvents.getBuildings(), Need.LAB);
            }
            case TRAIN -> {
                return s.onFighterCount(countFighters(s.student));
            }
            case RECLAIM -> {
                for (LivingEntity le : UnitServerEvents.getAllUnits())
                    if (le.isAlive() && le instanceof Unit u && s.student.equals(u.getOwnerName())
                            && WreckServerEvents.isReclaiming(le)) {
                        s.advance();
                        return true;
                    }
                return false;
            }
            case KILL_COMMANDER -> {
                LivingEntity com = commanderOf(s.enemy);
                if (com != null)
                    s.highlight = com.blockPosition();
                return s.onEnemyCommander(com != null, PlayerServerEvents.isRTSPlayer(s.enemy));
            }
            default -> {
                return false;
            }
        }
    }

    public enum Need { EXTRACTOR, ENERGY, LAB }

    /**
     * EXTRACTOR / ENERGY / LAB: one of the student's buildings of that kind is finished. One placed but still going up
     * marks the step in progress (the panel says so instead of looking stuck).
     */
    public static boolean checkBuilding(OnboardingSession s, Iterable<BuildingPlacement> buildings, Need need) {
        boolean placed = false;
        for (BuildingPlacement bp : buildings) {
            if (bp.isDestroyedServerside || !s.student.equals(bp.ownerName) || !matches(bp, need))
                continue;
            if (bp.isBuilt) {
                s.advance();
                return true;
            }
            placed = true;
        }
        s.inProgress = placed;
        return false;
    }

    /**
     * What counts, faction-neutral: an extractor makes metal (T1 or T2), an energy building makes energy and no metal,
     * a lab is a non-capitol production building that makes neither (the faction's T1 lab, or any later one). The
     * Horde's Basic Portal counts too - it trains nothing until refitted, which the TRAIN step's text says.
     */
    public static boolean matches(BuildingPlacement bp, Need need) {
        Building b = bp.getBuilding();
        if (b == null || b.isCapitol)
            return false;
        return switch (need) {
            case EXTRACTOR -> b.getMetalIncome() > 0;
            case ENERGY -> b.getEnergyIncome() > 0 && b.getMetalIncome() <= 0;
            case LAB -> bp instanceof ProductionPlacement && b.getMetalIncome() <= 0 && b.getEnergyIncome() <= 0
                && (isKitLab(b) || b == Buildings.PORTAL_MILITARY || b == Buildings.PORTAL_BASIC || producesUnits(bp));
        };
    }

    private static boolean isKitLab(Building b) {
        for (Faction f : ReignOfNetherRegistries.FACTIONS) {
            BotPlayer.Kit kit = BotPlayer.kitFor(f);
            if (kit != null && (kit.armyBuilding() == b || kit.t2Lab() == b))
                return true;
        }
        return false;
    }

    // a production building outside the bot kits (a faction added later): one that lists anything at all
    private static boolean producesUnits(BuildingPlacement bp) {
        return bp.getBuilding() instanceof com.solegendary.reignofnether.building.production.ProductionBuilding pb
            && pb.productions != null && !pb.productions.get().isEmpty();
    }

    /** Fighters (attackers that are neither workers nor the commander) the student owns. */
    public static int countFighters(String owner) {
        int n = 0;
        for (LivingEntity le : UnitServerEvents.getAllUnits())
            if (le.isAlive() && le instanceof Unit u && owner.equals(u.getOwnerName()) && le instanceof AttackerUnit
                    && !(le instanceof WorkerUnit) && !CommanderServerEvents.isCommander(le))
                n++;
        return n;
    }

    public static LivingEntity commanderOf(String owner) {
        if (owner == null || owner.isEmpty())
            return null;
        for (LivingEntity le : UnitServerEvents.getAllUnits())
            if (le.isAlive() && le instanceof Unit u && owner.equals(u.getOwnerName()) && CommanderServerEvents.isCommander(le))
                return le;
        return null;
    }

    /** The D-gun has been fired: it is on cooldown (a refused shot - no energy - sets none). */
    public static boolean dgunFired(Unit commander) {
        if (commander.getAbilities() == null)
            return false;
        for (Ability a : commander.getAbilities().get())
            if (a instanceof CommanderDGun && a.getCooldown(commander) > 0)
                return true;
        return false;
    }

    static BlockPos nearestPatch(ServerLevel level, BlockPos home) {
        BlockPos best = null;
        double bestD = Double.MAX_VALUE;
        for (BlockPos p : MetalPatches.getPatches(level)) {
            double d = p.distSqr(home);
            // the patch the capitol sits on (if any) is no lesson: skip anything within the base footprint
            if (d < 36)
                continue;
            if (d < bestD) {
                bestD = d;
                best = p;
            }
        }
        return best;
    }

    static ServerPlayer player(String name) {
        for (ServerPlayer sp : PlayerServerEvents.players)
            if (sp.getName().getString().equals(name))
                return sp;
        return null;
    }

    /** After any step change: save (resume picks it up) and tell the student. */
    static void stepChanged(OnboardingSession s) {
        save();
        ServerPlayer sp = player(s.student);
        if (sp != null)
            OnboardingClientboundPacket.step(sp, s);
    }

    /** Names of sessions (debugging, tests). */
    public static List<String> students() {
        return new ArrayList<>(sessions.keySet());
    }
}
