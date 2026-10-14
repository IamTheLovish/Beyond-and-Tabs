package com.solegendary.reignofnether.commands.lang;

import com.solegendary.reignofnether.ability.Ability;
import com.solegendary.reignofnether.api.ReignOfNetherRegistries;
import com.solegendary.reignofnether.building.Building;
import com.solegendary.reignofnether.building.BuildingClientEvents;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.BuildingServerboundPacket;
import com.solegendary.reignofnether.building.BuildingUtils;
import com.solegendary.reignofnether.building.BuildingValidators;
import com.solegendary.reignofnether.commands.lang.Intent.Group;
import com.solegendary.reignofnether.commands.lang.Intent.Place;
import com.solegendary.reignofnether.cursor.CursorClientEvents;
import com.solegendary.reignofnether.hud.HudClientEvents;
import com.solegendary.reignofnether.keybinds.Keybindings;
import com.solegendary.reignofnether.orthoview.OrthoviewClientEvents;
import com.solegendary.reignofnether.unit.Relationship;
import com.solegendary.reignofnether.unit.UnitAction;
import com.solegendary.reignofnether.unit.UnitClientEvents;
import com.solegendary.reignofnether.unit.command.QueueMode;
import com.solegendary.reignofnether.unit.interfaces.AttackerUnit;
import com.solegendary.reignofnether.unit.interfaces.HeroUnit;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.interfaces.WorkerUnit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Turns parsed {@link Intent}s into the calls the hotkeys make (claude/voice-commands.md): the group becomes the
 * selection, the place becomes a block position, the verb becomes the UnitAction packet with the clause's queue
 * mode. Buildings go through the usual ghost when no place is named and are placed at once when one is. Returns a
 * short line for the HUD caption, so the player sees what was done ("12 workers: stop, then build Sun Spire").
 */
public final class IntentExecutor {
    private IntentExecutor() { }

    private static final Minecraft MC = Minecraft.getInstance();
    /** "units here": the radius round the cursor. */
    public static final double HERE_RADIUS = 6.0;
    /** "north of the capitol": one building width. */
    public static final int DIRECTION_STEP = 12;

    public static String execute(List<Intent> intents) {
        if (MC.level == null || MC.player == null || intents.isEmpty())
            return "";
        StringBuilder said = new StringBuilder();
        for (Intent intent : intents) {
            String line = execute(intent);
            if (line == null)
                continue;
            if (said.length() > 0)
                said.append(intent.queued() ? ", then " : "; ");
            said.append(line);
        }
        return said.toString();
    }

    @Nullable
    static String execute(Intent intent) {
        List<LivingEntity> group = resolve(intent.group());
        QueueMode mode = intent.queued() ? QueueMode.APPEND : QueueMode.REPLACE;
        String who = describe(intent.group(), group.size());
        if (intent.verb() == Intent.Verb.FOCUS) {
            Vec3 c = centroid(group);
            if (c == null) {
                BlockPos cap = capitolPos();
                if (cap == null)
                    return who + ": nothing to look at";
                c = Vec3.atCenterOf(cap);
            }
            OrthoviewClientEvents.centreCameraOnPos(c);
            return "camera to " + who;
        }
        if (group.isEmpty())
            return who + ": nobody";
        select(group);
        int[] ids = group.stream().mapToInt(LivingEntity::getId).toArray();
        switch (intent.verb()) {
            case SELECT -> { return "selected " + who; }
            case SAVE_GROUP -> {
                int n = Integer.parseInt(intent.object());
                if (n < 0 || n >= HudClientEvents.controlGroups.size() || n >= Keybindings.nums.length)
                    return "no group " + n;
                HudClientEvents.controlGroups.get(n).saveFromSelected(Keybindings.nums[n], true);
                return who + " saved as group " + n;
            }
            case STOP -> { send(UnitAction.STOP, ids, BlockPos.ZERO, mode); return who + ": stop"; }
            case HOLD -> { send(UnitAction.HOLD, ids, BlockPos.ZERO, mode); return who + ": hold"; }
            case MOVE, ATTACK_MOVE, PATROL, RETREAT -> {
                BlockPos at = place(intent.place(), group, intent.verb() == Intent.Verb.RETREAT ? Place.Kind.HOME : Place.Kind.HERE);
                if (at == null)
                    return who + ": nowhere to go";
                UnitAction action = switch (intent.verb()) {
                    case ATTACK_MOVE -> UnitAction.ATTACK_MOVE;
                    case PATROL -> UnitAction.PATROL;
                    default -> UnitAction.MOVE;
                };
                send(action, ids, at, mode);
                return who + ": " + intent.verb().name().toLowerCase(Locale.ROOT).replace('_', ' ') + " " + where(intent.place());
            }
            case GUARD -> {
                BuildingPlacement target = intent.place().kind() == Place.Kind.AT_CAPITOL || intent.place().kind() == Place.Kind.HOME
                    ? capitol() : nearestOwnedBuilding(place(intent.place(), group, Place.Kind.HERE));
                if (target == null)
                    return who + ": nothing to guard";
                UnitClientEvents.sendUnitCommandManual(UnitAction.GUARD, -1, ids, BlockPos.ZERO, target.originPos, mode);
                return who + ": guard " + target.getBuilding().name;
            }
            case REPAIR -> {
                BuildingPlacement target = intent.place().kind() == Place.Kind.AT_CAPITOL || intent.place().kind() == Place.Kind.HOME
                    ? capitol() : nearestDamagedBuilding(place(intent.place(), group, Place.Kind.NEAR_YOU));
                if (target == null)
                    return who + ": nothing to repair";
                UnitClientEvents.sendUnitCommandManual(UnitAction.BUILD_REPAIR, -1, ids, target.originPos, target.originPos, mode);
                return who + ": repair " + target.getBuilding().name;
            }
            case RECLAIM -> {
                BlockPos at = place(intent.place(), group, Place.Kind.HERE);
                if (at == null)
                    return who + ": nowhere to reclaim";
                send(UnitAction.RECLAIM, ids, at, mode);
                return who + ": reclaim " + where(intent.place());
            }
            case GATHER -> {
                BlockPos at = place(intent.place(), group, Place.Kind.NEAR_YOU);
                send(UnitAction.FARM, ids, at == null ? BlockPos.ZERO : at, mode);
                return who + ": gather" + (intent.object() == null ? "" : " " + intent.object());
            }
            case USE_ABILITY, AUTOCAST_ON, AUTOCAST_OFF -> {
                Ability ability = ability(group, intent.object());
                if (ability == null)
                    return who + ": no " + intent.object();
                UnitAction action = switch (intent.verb()) {
                    case AUTOCAST_ON -> ability.autocastEnableAction;
                    case AUTOCAST_OFF -> ability.autocastDisableAction;
                    default -> ability.action;
                };
                if (action == null)
                    return who + ": " + intent.object() + " cannot be " + (intent.verb() == Intent.Verb.USE_ABILITY ? "used that way" : "autocast");
                BlockPos at = intent.place().kind() == Place.Kind.NONE ? BlockPos.ZERO : place(intent.place(), group, Place.Kind.HERE);
                send(action, ids, at == null ? BlockPos.ZERO : at, mode);
                return who + ": " + intent.verb().name().toLowerCase(Locale.ROOT).replace('_', ' ') + " " + intent.object();
            }
            case BUILD -> {
                Building building = building(intent.object());
                if (building == null)
                    return who + ": no building called " + intent.object();
                int[] workers = group.stream().filter(le -> le instanceof WorkerUnit).mapToInt(LivingEntity::getId).toArray();
                if (workers.length == 0)
                    return who + ": no workers to build " + building.name;
                if (intent.place().kind() == Place.Kind.NONE) {
                    BuildingClientEvents.setBuildingToPlace(building);   // the usual ghost: the player clicks where
                    return who + ": " + building.name + " - click where";
                }
                BlockPos at = place(intent.place(), group, Place.Kind.HERE);
                if (at == null)
                    return who + ": nowhere to build";
                BuildingClientEvents.setBuildingToPlace(building);   // sets the ghost's dimensions and rotation
                BlockPos origin = BuildingUtils.getBuildingOriginPos(at, false, BuildingClientEvents.getBuildingRotation(),
                    BuildingClientEvents.getBuildingDimensions());
                String err = BuildingValidators.getPlacementValidityError(MC.level, building, origin, MC.player.getName().getString(),
                    BuildingClientEvents.getBuildingRotation(), false, false, true);
                if (err != null)
                    return who + ": cannot build " + building.name + " " + where(intent.place()) + " (" + I18n.get(err) + ") - click where";
                BuildingServerboundPacket.placeBuilding(building, origin, BuildingClientEvents.getBuildingRotation(),
                    MC.player.getName().getString(), workers, false);
                BuildingClientEvents.setBuildingToPlace(null);
                return who + ": build " + building.name + " " + where(intent.place());
            }
            default -> { return null; }
        }
    }

    // ---- groups ----

    static List<LivingEntity> resolve(Group group) {
        List<LivingEntity> out = new ArrayList<>();
        switch (group.kind()) {
            case SELECTION -> out.addAll(UnitClientEvents.getSelectedUnits());
            case CONTROL_GROUP -> {
                int n = group.number();
                if (n >= 0 && n < HudClientEvents.controlGroups.size())
                    for (int id : HudClientEvents.controlGroups.get(n).entityIds)
                        if (MC.level.getEntity(id) instanceof LivingEntity le && le.isAlive())
                            out.add(le);
            }
            case UNITS_HERE -> {
                BlockPos c = CursorClientEvents.getPreselectedBlockPos();
                for (LivingEntity le : owned())
                    if (le.distanceToSqr(c.getX() + 0.5, le.getY(), c.getZ() + 0.5) <= HERE_RADIUS * HERE_RADIUS)
                        out.add(le);
            }
            default -> {
                for (LivingEntity le : owned()) {
                    boolean worker = le instanceof WorkerUnit;
                    boolean fighter = le instanceof AttackerUnit && !worker;
                    boolean take = switch (group.kind()) {
                        case ALL_WORKERS, WORKERS -> worker;
                        case IDLE_WORKERS -> worker && ((Unit) le).isIdle();
                        case SOLDIERS -> fighter;
                        case EVERYONE -> true;
                        case HERO -> le instanceof HeroUnit;
                        case UNIT_TYPE -> GameVocabulary.stripFaction(le.getType().getDescription().getString()).toLowerCase(Locale.ROOT).equals(group.name());
                        default -> false;
                    };
                    if (take)
                        out.add(le);
                }
            }
        }
        return out;
    }

    private static List<LivingEntity> owned() {
        List<LivingEntity> out = new ArrayList<>();
        for (LivingEntity le : UnitClientEvents.getAllUnits())
            if (le instanceof Unit && le.isAlive() && UnitClientEvents.getPlayerToEntityRelationship(le) == Relationship.OWNED)
                out.add(le);
        return out;
    }

    private static void select(List<LivingEntity> group) {
        UnitClientEvents.clearSelectedUnits();
        BuildingClientEvents.clearSelectedBuildings();
        for (LivingEntity le : group)
            UnitClientEvents.addSelectedUnitNoSort(le);
        UnitClientEvents.sortedSelectedUnits();
    }

    static String describe(Group g, int n) {
        return switch (g.kind()) {
            case CONTROL_GROUP -> "group " + g.number() + " (" + n + ")";
            case UNIT_TYPE -> n + " " + (n == 1 ? g.name() : Vocabulary.plural(g.name()));
            case HERO -> "the hero";
            case SELECTION -> n + " selected";
            default -> n + " " + g.kind().name().toLowerCase(Locale.ROOT).replace('_', ' ');
        };
    }

    // ---- places ----

    @Nullable
    static BlockPos place(Place place, List<LivingEntity> group, Place.Kind fallback) {
        Place.Kind kind = place.kind() == Place.Kind.NONE ? fallback : place.kind();
        return switch (kind) {
            case HERE, THERE -> ground(CursorClientEvents.getPreselectedBlockPos());   // "there" = the ping, when there is one; the cursor until then
            case NEAR_YOU -> {
                Vec3 c = centroid(group);
                yield c == null ? null : ground(BlockPos.containing(c));
            }
            case AT_CAPITOL, HOME -> capitolPos();
            case AT_FRONT -> {
                Vec3 c = centroid(group);
                LivingEntity foe = nearestHostile(c == null ? MC.player.position() : c);
                yield foe == null ? null : ground(foe.blockPosition());
            }
            case DIRECTION_OF -> {
                BlockPos ref = switch (place.reference()) {
                    case "capitol", "home" -> capitolPos();
                    case "group" -> {
                        Vec3 c = centroid(group);
                        yield c == null ? null : BlockPos.containing(c);
                    }
                    case "front" -> {
                        Vec3 c = centroid(group);
                        LivingEntity foe = nearestHostile(c == null ? MC.player.position() : c);
                        yield foe == null ? null : foe.blockPosition();
                    }
                    default -> CursorClientEvents.getPreselectedBlockPos();
                };
                if (ref == null)
                    yield null;
                BlockPos moved = switch (place.direction()) {
                    case "north" -> ref.offset(0, 0, -DIRECTION_STEP);
                    case "south" -> ref.offset(0, 0, DIRECTION_STEP);
                    case "east" -> ref.offset(DIRECTION_STEP, 0, 0);
                    default -> ref.offset(-DIRECTION_STEP, 0, 0);
                };
                yield ground(moved);
            }
            default -> null;
        };
    }

    static String where(Place p) {
        return switch (p.kind()) {
            case NONE -> "";
            case DIRECTION_OF -> p.direction() + " of " + p.reference();
            default -> p.kind().name().toLowerCase(Locale.ROOT).replace('_', ' ');
        };
    }

    private static BlockPos ground(BlockPos bp) {
        Level level = MC.level;
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bp.getX(), bp.getZ());
        return new BlockPos(bp.getX(), y, bp.getZ());
    }

    @Nullable
    static Vec3 centroid(List<LivingEntity> group) {
        if (group.isEmpty())
            return null;
        double x = 0, y = 0, z = 0;
        for (LivingEntity le : group) {
            x += le.getX();
            y += le.getY();
            z += le.getZ();
        }
        return new Vec3(x / group.size(), y / group.size(), z / group.size());
    }

    @Nullable
    static BuildingPlacement capitol() {
        for (BuildingPlacement bp : BuildingClientEvents.getBuildings())
            if (bp.isCapitol && BuildingClientEvents.getPlayerToBuildingRelationship(bp) == Relationship.OWNED)
                return bp;
        return null;
    }

    @Nullable
    static BlockPos capitolPos() {
        BuildingPlacement c = capitol();
        return c == null ? null : ground(c.centrePos);
    }

    @Nullable
    private static BuildingPlacement nearestOwnedBuilding(@Nullable BlockPos near) {
        return nearestBuilding(near, false);
    }

    @Nullable
    private static BuildingPlacement nearestDamagedBuilding(@Nullable BlockPos near) {
        BuildingPlacement b = nearestBuilding(near, true);
        return b != null ? b : nearestBuilding(near, false);
    }

    @Nullable
    private static BuildingPlacement nearestBuilding(@Nullable BlockPos near, boolean damagedOnly) {
        if (near == null)
            return null;
        BuildingPlacement best = null;
        double bestD = Double.MAX_VALUE;
        for (BuildingPlacement bp : BuildingClientEvents.getBuildings()) {
            if (BuildingClientEvents.getPlayerToBuildingRelationship(bp) != Relationship.OWNED)
                continue;
            if (damagedOnly && (bp.isBuilt && bp.getHealth() >= bp.getMaxHealth()))
                continue;
            double d = bp.centrePos.distSqr(near);
            if (d < bestD) {
                bestD = d;
                best = bp;
            }
        }
        return best;
    }

    @Nullable
    private static LivingEntity nearestHostile(Vec3 from) {
        LivingEntity best = null;
        double bestD = Double.MAX_VALUE;
        for (LivingEntity le : UnitClientEvents.getAllUnits()) {
            if (!(le instanceof Unit) || !le.isAlive() || UnitClientEvents.getPlayerToEntityRelationship(le) != Relationship.HOSTILE)
                continue;
            double d = le.position().distanceToSqr(from);
            if (d < bestD) {
                bestD = d;
                best = le;
            }
        }
        return best;
    }

    // ---- things ----

    @Nullable
    static Building building(String name) {
        for (Building b : ReignOfNetherRegistries.BUILDING)
            if (b.name != null && b.name.toLowerCase(Locale.ROOT).equals(name))
                return b;
        return null;
    }

    @Nullable
    private static Ability ability(List<LivingEntity> group, String name) {
        for (LivingEntity le : group)
            if (le instanceof Unit u)
                for (Ability a : u.getAbilities().get()) {
                    var button = a.getButton(u);
                    if (button != null && button.name != null && button.name.toLowerCase(Locale.ROOT).equals(name))
                        return a;
                }
        return null;
    }

    private static void send(UnitAction action, int[] ids, BlockPos at, QueueMode mode) {
        UnitClientEvents.sendUnitCommandManual(action, -1, ids, at, BlockPos.ZERO, mode);
    }
}
