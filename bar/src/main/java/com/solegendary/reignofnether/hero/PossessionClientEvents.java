package com.solegendary.reignofnether.hero;

import com.mojang.blaze3d.platform.InputConstants;
import com.solegendary.reignofnether.ability.Ability;
import com.solegendary.reignofnether.alliance.AlliancesClient;
import com.solegendary.reignofnether.hud.HudClientEvents;
import com.solegendary.reignofnether.hud.buttons.Button;
import com.solegendary.reignofnether.keybinds.Keybinding;
import com.solegendary.reignofnether.keybinds.Keybindings;
import com.solegendary.reignofnether.orthoview.OrthoviewClientEvents;
import com.solegendary.reignofnether.unit.UnitClientEvents;
import com.solegendary.reignofnether.unit.interfaces.HeroUnit;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.util.MiscUtil;

import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.RenderNameTagEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Possession, client side (design/hero_mode.md, section 2): the camera inside the possessed mob, the input sent to
 * the server, and the Silica-style HUD - "UNIT FEED: <unit>" top right, HP / hero XP / four ability slots at the
 * bottom, a crosshair, a circular radar top left and the commander's current objective with a waypoint.
 *
 * The RTS session is left running underneath: the RTS view stays "on" (OrthoviewClientEvents.enabled) but
 * OrthoviewClientEvents.isEnabled() answers false while possessing, so every RTS camera / HUD / fog hook steps
 * aside on its own, and nothing has to be torn down and rebuilt on the way back. Locally the RTS screen is just
 * closed (the server keeps the player spectating), the client camera entity becomes the mob, and the player's own
 * (mouse-turned) rotation is copied onto the mob every frame, so looking around is instant even though movement is
 * the server's.
 *
 * Controls are the vanilla Minecraft bindings (WASD, Space, Shift, left click, 1-4, F5), so they follow whatever the
 * player set in Controls; V also toggles the over-the-shoulder view (60% boards have no F5), and Backspace / Esc ->
 * Return to command goes back to the RTS view.
 */
public class PossessionClientEvents {

    private static final Minecraft MC = Minecraft.getInstance();

    static final int RADAR_R = 40;
    static final float RADAR_RANGE = 48f;
    static final long OBJECTIVE_MS = 60_000;
    static final int GOLD = 0xFFFFD27A, DIM = 0xFFB0B0B0, PANEL = 0xA0101418;

    private static int possessedId = -1;
    private static int missingTicks = 0;
    private static boolean heroRole = false;
    /** unit id -> who possesses it (any player's), for the ally marker */
    private static final Map<Integer, String> possessors = new HashMap<>();

    private static PossessionServer.Objective objective = PossessionServer.Objective.NONE;
    private static double objX, objY, objZ;
    private static int objTarget = -1;
    private static long objSetAt = 0;

    // last input sent (only changes and a heartbeat go to the server)
    private static int lastF, lastS, sinceSent;
    private static boolean lastJump, lastDesc, vWasDown;
    private static float lastYaw, lastPitch;
    private static int attackHeldTicks;
    private static int clientTicks;

    // the possessed unit's ability icons, built once per possession and refreshed now and then
    private static final List<Ability> slotAbilities = new ArrayList<>(4);
    private static final List<Button> slotButtons = new ArrayList<>(4);
    private static int slotsAge = 0;

    public static boolean isPossessing() {
        return possessedId >= 0;
    }

    public static int getPossessedId() {
        return possessedId;
    }

    public static boolean isHeroRole() {
        return heroRole;
    }

    /** Who possesses {@code unitId} ("" when nobody). */
    public static String possessorOf(int unitId) {
        String s = possessors.get(unitId);
        return s == null ? "" : s;
    }

    static LivingEntity possessed() {
        if (possessedId < 0 || MC.level == null)
            return null;
        Entity e = MC.level.getEntity(possessedId);
        return e instanceof LivingEntity le ? le : null;
    }

    // ---- server messages ----

    public static void onPacket(PossessionClientboundPacket p) {
        switch (p.kind) {
            case STATE -> {
                if (p.text.isEmpty())
                    possessors.remove(p.unitId);
                else
                    possessors.put(p.unitId, p.text);
            }
            case START -> begin(p.unitId);
            case END -> {
                end();
                if (!p.text.isEmpty())
                    HudClientEvents.showTemporaryMessage(p.text, 120);
            }
            case OBJECTIVE -> {
                PossessionServer.Objective[] all = PossessionServer.Objective.values();
                objective = p.objective >= 0 && p.objective < all.length ? all[p.objective] : PossessionServer.Objective.NONE;
                objX = p.x;
                objY = p.y;
                objZ = p.z;
                objTarget = p.targetId;
                objSetAt = System.currentTimeMillis();
            }
            case ROLE -> heroRole = !p.text.isEmpty();
        }
    }

    static void begin(int unitId) {
        if (MC.player == null || MC.level == null)
            return;
        Entity e = MC.level.getEntity(unitId);
        if (!(e instanceof LivingEntity)) {
            PossessionServerboundPacket.release();
            return;
        }
        possessedId = unitId;
        missingTicks = 0;
        objective = PossessionServer.Objective.NONE;
        // close the RTS screen locally only: the server keeps this player's RTS session (spectating) as it is, and
        // TopdownGuiClientEvents reopens the screen once isEnabled() is true again
        if (MC.screen != null)
            MC.setScreen(null);
        MC.player.setYRot(e.getYRot());
        MC.player.yRotO = e.getYRot();
        MC.player.setXRot(0);
        MC.player.xRotO = 0;
        lookThrough(e);
        MC.options.setCameraType(CameraType.FIRST_PERSON);
        MC.mouseHandler.grabMouse();
        sinceSent = 99;
        slotsAge = 0;
        rebuildSlots(e);
    }

    private static void lookThrough(Entity e) {
        MC.setCameraEntity(e);
        // no creeper / spider / enderman vision filter: you are a soldier, not a spectator
        MC.gameRenderer.shutdownEffect();
    }

    /** Back to the RTS view, with the camera over the unit we just left. */
    static void end() {
        if (possessedId < 0)
            return;
        LivingEntity mob = possessed();
        possessedId = -1;
        objective = PossessionServer.Objective.NONE;
        attackHeldTicks = 0;
        if (MC.player == null)
            return;
        MC.setCameraEntity(MC.player);
        MC.options.setCameraType(CameraType.FIRST_PERSON);
        if (OrthoviewClientEvents.enabled && mob != null) {
            // back up to the RTS height first, then centre: the centring keeps the camera's height
            MC.player.setPos(mob.getX(), Math.max(OrthoviewClientEvents.orthoviewPlayerBaseY, mob.getY() + 20), mob.getZ());
            OrthoviewClientEvents.centreCameraOnPosInstant(mob.position());
        }
    }

    /** Leave at once (the server's END follows and finds nothing left to undo). */
    public static void requestRelease() {
        end();
        PossessionServerboundPacket.release();
    }

    /**
     * The RTS/Minecraft toggle (Backspace, F12) was pressed. Possessing: go back. In the RTS view with exactly one
     * unit selected: possess it; a Hero-role player with nothing selected: possess the team's hero. True when it was
     * used for possession (the old view toggle must not also run).
     */
    public static boolean onToggleKey() {
        if (isPossessing()) {
            requestRelease();
            return true;
        }
        if (!OrthoviewClientEvents.isEnabled())
            return false;
        List<LivingEntity> sel = UnitClientEvents.getSelectedUnits();
        if (sel.size() == 1 && sel.get(0) instanceof Unit) {
            PossessionServerboundPacket.possess(sel.get(0).getId());
            return true;
        }
        if (heroRole && sel.isEmpty()) {
            PossessionServerboundPacket.possess(-1);
            return true;
        }
        return false;
    }

    // ---- input ----

    private static boolean down(KeyMapping km) {
        if (km.isDown())
            return true;
        InputConstants.Key key = km.getKey();
        return key.getType() == InputConstants.Type.KEYSYM && key.getValue() > 0
            && InputConstants.isKeyDown(MC.getWindow().getWindow(), key.getValue());
    }

    private static void drain(KeyMapping km) {
        while (km.consumeClick()) { }
    }

    // pre-tick: before vanilla handles the key mappings, so a spectator's attack / hotbar / inventory keys never
    // reach their spectator meaning (left click would start spectating an entity, 1-9 open the teleport menu)
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent evt) {
        if (evt.phase != TickEvent.Phase.START)
            return;
        clientTicks++;
        if (possessedId < 0)
            return;
        if (MC.player == null || MC.level == null) {
            possessedId = -1;
            return;
        }
        LivingEntity mob = possessed();
        if (mob == null || mob.isRemoved()) {
            // not streamed in yet, or gone: give the server a moment to tell us, then leave on our own
            if (++missingTicks > 40)
                requestRelease();
            return;
        }
        missingTicks = 0;
        if (MC.getCameraEntity() != mob)
            lookThrough(mob);
        float yaw = MC.player.getYRot(), pitch = MC.player.getXRot();

        // clicks and number keys
        boolean attacked = false;
        while (MC.options.keyAttack.consumeClick())
            attacked = true;
        if (down(MC.options.keyAttack))
            attackHeldTicks++;
        else
            attackHeldTicks = 0;
        // a held button keeps swinging; the server's cooldown decides the actual pace
        if (attacked || (attackHeldTicks > 0 && attackHeldTicks % 4 == 0))
            PossessionServerboundPacket.attack(possessedId, yaw, pitch);
        for (int i = 0; i < MC.options.keyHotbarSlots.length; i++) {
            boolean used = false;
            while (MC.options.keyHotbarSlots[i].consumeClick())
                used = true;
            if (used && i < 4)
                PossessionServerboundPacket.ability(possessedId, i, yaw, pitch);
        }
        drain(MC.options.keyUse);
        drain(MC.options.keyInventory);
        drain(MC.options.keySwapOffhand);
        drain(MC.options.keyDrop);
        drain(MC.options.keyPickItem);
        drain(MC.options.keyAdvancements);
        // V: over the shoulder and back (F5 still cycles through vanilla's three views)
        boolean v = InputConstants.isKeyDown(MC.getWindow().getWindow(), GLFW.GLFW_KEY_V);
        if (v && !vWasDown && MC.screen == null)
            MC.options.setCameraType(MC.options.getCameraType().isFirstPerson() ? CameraType.THIRD_PERSON_BACK : CameraType.FIRST_PERSON);
        vWasDown = v;

        // movement: only changes, plus a heartbeat every half second (the server stops a unit after 2 s of silence)
        int f = (down(MC.options.keyUp) ? 1 : 0) - (down(MC.options.keyDown) ? 1 : 0);
        int s = (down(MC.options.keyLeft) ? 1 : 0) - (down(MC.options.keyRight) ? 1 : 0);
        boolean jump = down(MC.options.keyJump), desc = down(MC.options.keyShift);
        if (MC.screen != null) {   // chat or pause open: stand still
            f = 0;
            s = 0;
            jump = false;
            desc = false;
        }
        boolean changed = f != lastF || s != lastS || jump != lastJump || desc != lastDesc
            || Math.abs(Mth.wrapDegrees(yaw - lastYaw)) > 1.5f || Math.abs(pitch - lastPitch) > 1.5f;
        if (changed || ++sinceSent >= 10) {
            PossessionServerboundPacket.input(possessedId, f, s, yaw, pitch, jump, desc);
            lastF = f;
            lastS = s;
            lastJump = jump;
            lastDesc = desc;
            lastYaw = yaw;
            lastPitch = pitch;
            sinceSent = 0;
        }

        if (++slotsAge >= 100) {
            slotsAge = 0;
            rebuildSlots(mob);
        }
        tickObjective(mob);
    }

    // the player's own movement keys must not fly the (invisible, spectating) player around meanwhile
    @SubscribeEvent
    public static void onMovementInput(MovementInputUpdateEvent evt) {
        if (possessedId < 0)
            return;
        var in = evt.getInput();
        in.forwardImpulse = 0;
        in.leftImpulse = 0;
        in.up = false;
        in.down = false;
        in.left = false;
        in.right = false;
        in.jumping = false;
        in.shiftKeyDown = false;
    }

    // every frame, before the world is drawn: the mob looks where the mouse points, with no server round trip
    @SubscribeEvent
    public static void onRenderTick(TickEvent.RenderTickEvent evt) {
        if (evt.phase != TickEvent.Phase.START || possessedId < 0 || MC.player == null)
            return;
        LivingEntity mob = possessed();
        if (mob == null)
            return;
        float yaw = MC.player.getYRot(), yawO = MC.player.yRotO;
        mob.setYRot(yaw);
        mob.yRotO = yawO;
        mob.setXRot(MC.player.getXRot());
        mob.xRotO = MC.player.xRotO;
        mob.yHeadRot = yaw;
        mob.yHeadRotO = yawO;
        mob.yBodyRot = yaw;
        mob.yBodyRotO = yawO;
    }

    // ---- objectives ----

    static void tickObjective(LivingEntity mob) {
        if (objective == PossessionServer.Objective.NONE)
            return;
        if (System.currentTimeMillis() - objSetAt > OBJECTIVE_MS) {
            objective = PossessionServer.Objective.NONE;
            return;
        }
        if (objTarget >= 0) {
            Entity t = MC.level.getEntity(objTarget);
            if (t != null && t.isAlive()) {
                objX = t.getX();
                objY = t.getY();
                objZ = t.getZ();
            } else if (t != null) {
                objective = PossessionServer.Objective.NONE;   // the target died: done
                return;
            }
        }
        if (Double.isNaN(objY))
            objY = MC.level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(objX), Mth.floor(objZ));
        double dx = objX - mob.getX(), dz = objZ - mob.getZ();
        boolean arrives = objective == PossessionServer.Objective.MOVE || objective == PossessionServer.Objective.PING;
        if (arrives && dx * dx + dz * dz < 16) {
            objective = PossessionServer.Objective.NONE;
            return;
        }
        // the waypoint in the world: a short column of light, a few particles every few ticks
        if (clientTicks % 4 == 0)
            for (int k = 0; k < 4; k++)
                MC.level.addParticle(ParticleTypes.END_ROD, objX, objY + 0.5 + k * 1.5 + MC.level.random.nextFloat(),
                    objZ, 0, 0.04, 0);
    }

    static String objectiveText() {
        return switch (objective) {
            case MOVE -> "MOVE TO THE TARGET LOCATION";
            case ATTACK -> "ATTACK THE TARGET";
            case DESTROY -> "DESTROY THE BUILDING";
            case PROTECT -> "PROTECT THE TARGET";
            case PING -> "PING: GO HERE";
            default -> "";
        };
    }

    // ---- HUD ----

    static void rebuildSlots(Entity e) {
        slotAbilities.clear();
        slotButtons.clear();
        if (!(e instanceof Unit unit))
            return;
        Keybinding[] keys = { Keybindings.abilitySlot1, Keybindings.abilitySlot2, Keybindings.abilitySlot3, Keybindings.abilitySlot4 };
        for (int i = 0; i < 4; i++) {
            Ability a = PossessionServer.abilityInSlot(unit, i);
            if (a == null)
                break;
            slotAbilities.add(a);
            Button b = null;
            try {
                b = a.getButton(keys[i], unit);
            } catch (RuntimeException ignored) {
                // a button that needs RTS state we don't have here: the slot shows without its icon
            }
            slotButtons.add(b);
        }
    }

    // the vanilla crosshair only shows over entities in spectator mode: ours is always there instead
    @SubscribeEvent
    public static void onRenderOverlay(RenderGuiOverlayEvent.Pre evt) {
        if (possessedId >= 0 && evt.getOverlay().id().equals(VanillaGuiOverlay.CROSSHAIR.id()))
            evt.setCanceled(true);
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post evt) {
        if (possessedId < 0 || MC.player == null || MC.options.hideGui)
            return;
        LivingEntity mob = possessed();
        if (mob == null)
            return;
        GuiGraphics gg = evt.getGuiGraphics();
        Font font = MC.font;
        int w = gg.guiWidth(), h = gg.guiHeight();

        // crosshair
        int cx = w / 2, cy = h / 2;
        gg.fill(cx - 4, cy, cx + 5, cy + 1, 0xE0FFFFFF);
        gg.fill(cx, cy - 4, cx + 1, cy + 5, 0xE0FFFFFF);

        // UNIT FEED, top right (Silica)
        String name = MiscUtil.getSimpleEntityName(mob).replace("_", " ").toUpperCase();
        String feed = "UNIT FEED: " + name;
        String hint = "[" + Keybindings.toggleCamera.getDisplayName() + "] RETURN TO COMMAND";
        int fw = Math.max(font.width(feed), font.width(hint));
        gg.fill(w - fw - 14, 4, w - 4, 30, PANEL);
        gg.drawString(font, feed, w - font.width(feed) - 9, 8, GOLD, false);
        gg.drawString(font, hint, w - font.width(hint) - 9, 19, DIM, false);

        drawVitals(gg, font, mob, w, h);
        drawRadar(gg, font, mob);
        drawObjective(gg, font, mob, w);
    }

    static void drawVitals(GuiGraphics gg, Font font, LivingEntity mob, int w, int h) {
        int barW = 182, x = w / 2 - barW / 2, y = h - 34;
        float hp = mob.getHealth(), max = Math.max(1, mob.getMaxHealth());
        gg.fill(x - 1, y - 1, x + barW + 1, y + 9, 0xFF000000);
        gg.fill(x, y, x + barW, y + 8, 0xFF3A1010);
        int fillW = Math.round(barW * Mth.clamp(hp / max, 0, 1));
        gg.fill(x, y, x + fillW, y + 8, hp / max > 0.35f ? 0xFF4CC24C : 0xFFD24A3A);
        String hpText = Math.round(hp) + " / " + Math.round(max);
        gg.drawString(font, hpText, w / 2 - font.width(hpText) / 2, y, 0xFFFFFFFF, true);
        if (mob instanceof HeroUnit hero) {
            int need = hero.getExpToNextlevel();
            float frac = need <= 0 ? 1 : Mth.clamp(hero.getExpOnCurrentLevel() / (float) need, 0, 1);
            gg.fill(x, y + 10, x + barW, y + 12, 0xFF201830);
            gg.fill(x, y + 10, x + Math.round(barW * frac), y + 12, 0xFFB06CFF);
            String lvl = "LVL " + hero.getHeroLevel();
            gg.drawString(font, lvl, x - font.width(lvl) - 4, y, 0xFFB06CFF, true);
        }
        // ability slots 1-4 above the bar
        int n = slotAbilities.size();
        if (n == 0 || !(mob instanceof Unit unit))
            return;
        int slot = 22, sx = w / 2 - (n * slot) / 2, sy = y - slot - 6;
        for (int i = 0; i < n; i++) {
            int bx = sx + i * slot;
            gg.fill(bx, sy, bx + slot - 2, sy + slot - 2, 0xC0000000);
            Button b = slotButtons.get(i);
            if (b != null && b.iconResource != null)
                gg.blit(b.iconResource, bx + 2, sy + 2, 0, 0, 16, 16, 16, 16);
            Ability a = slotAbilities.get(i);
            float cd = a.cooldownMax > 0 ? Mth.clamp(unit.getCooldown(a) / a.cooldownMax, 0, 1) : 0;
            if (cd > 0)
                gg.fill(bx, sy, bx + slot - 2, sy + Math.round((slot - 2) * cd), 0xA0000000);
            gg.drawString(font, String.valueOf(i + 1), bx + 1, sy + 1, 0xFFFFFFFF, true);
        }
    }

    static void drawRadar(GuiGraphics gg, Font font, LivingEntity mob) {
        int r = RADAR_R, cx = 8 + r, cy = 8 + r;
        for (int dy = -r - 1; dy <= r + 1; dy++) {
            int half = (int) Math.sqrt((r + 1) * (r + 1) - dy * dy);
            gg.fill(cx - half, cy + dy, cx + half + 1, cy + dy + 1, 0xC03A3F46);
        }
        for (int dy = -r; dy <= r; dy++) {
            int half = (int) Math.sqrt(r * r - dy * dy);
            gg.fill(cx - half, cy + dy, cx + half + 1, cy + dy + 1, 0xC0101418);
        }
        float yawR = MC.player.getYRot() * Mth.DEG_TO_RAD;
        // forward (-sin, cos) is up on the radar, right (-cos, -sin) is right
        float fx = -Mth.sin(yawR), fz = Mth.cos(yawR), rx = -Mth.cos(yawR), rz = -Mth.sin(yawR);
        float scale = r / RADAR_RANGE;
        String me = MC.player.getName().getString();
        List<LivingEntity> units = UnitClientEvents.getAllUnits();
        for (int i = 0; i < units.size(); i++) {
            LivingEntity le = units.get(i);
            if (le == mob || !(le instanceof Unit u))
                continue;
            double dx = le.getX() - mob.getX(), dz = le.getZ() - mob.getZ();
            if (dx * dx + dz * dz > RADAR_RANGE * RADAR_RANGE)
                continue;
            int px = cx + Math.round((float) (dx * rx + dz * rz) * scale);
            int py = cy - Math.round((float) (dx * fx + dz * fz) * scale);
            String owner = u.getOwnerName();
            int col = owner == null || owner.isBlank() ? 0xFFA0A0A0
                : owner.equals(me) ? 0xFF55FF55
                : AlliancesClient.isAllied(me, owner) ? 0xFF5599FF : 0xFFFF5555;
            gg.fill(px - 1, py - 1, px + 1, py + 1, col);
        }
        gg.fill(cx - 1, cy - 1, cx + 2, cy + 2, 0xFFFFFFFF);   // you
        // north on the rim: north is -Z
        int nx = cx + Math.round(-rz * (r - 5)), ny = cy - Math.round(-fz * (r - 5));
        gg.drawString(font, "N", nx - 2, ny - 4, DIM, false);
        if (objective != PossessionServer.Objective.NONE) {
            double dx = objX - mob.getX(), dz = objZ - mob.getZ();
            float ox = (float) (dx * rx + dz * rz) * scale, oy = (float) (dx * fx + dz * fz) * scale;
            float len = Mth.sqrt(ox * ox + oy * oy);
            if (len > r - 3) {   // out of range: pinned to the rim, pointing the way
                ox *= (r - 3) / len;
                oy *= (r - 3) / len;
            }
            int px = cx + Math.round(ox), py = cy - Math.round(oy);
            gg.fill(px - 2, py - 2, px + 3, py + 3, 0xFFFFD000);
        }
    }

    static void drawObjective(GuiGraphics gg, Font font, LivingEntity mob, int w) {
        if (objective == PossessionServer.Objective.NONE)
            return;
        double dx = objX - mob.getX(), dz = objZ - mob.getZ();
        int dist = (int) Math.round(Math.sqrt(dx * dx + dz * dz));
        float yawR = MC.player.getYRot() * Mth.DEG_TO_RAD;
        double ahead = dx * -Mth.sin(yawR) + dz * Mth.cos(yawR);
        double right = dx * -Mth.cos(yawR) + dz * -Mth.sin(yawR);
        double ang = Math.toDegrees(Math.atan2(right, ahead));
        String arrow = Math.abs(ang) < 30 ? "^" : Math.abs(ang) > 150 ? "v" : ang > 0 ? ">" : "<";
        String title = objectiveText();
        String sub = arrow + "  " + dist + " m  " + arrow;
        int bw = Math.max(font.width(title), font.width(sub)) + 16;
        gg.fill(w / 2 - bw / 2, 6, w / 2 + bw / 2, 32, PANEL);
        gg.fill(w / 2 - bw / 2, 6, w / 2 + bw / 2, 7, 0xFFFFD000);
        gg.drawString(font, title, w / 2 - font.width(title) / 2, 10, 0xFFFFD000, false);
        gg.drawString(font, sub, w / 2 - font.width(sub) / 2, 21, 0xFFFFFFFF, false);
    }

    // ---- allies see who drives a unit ----

    @SubscribeEvent
    public static void onNameTag(RenderNameTagEvent evt) {
        if (possessors.isEmpty() || MC.player == null)
            return;
        Entity e = evt.getEntity();
        String who = possessors.get(e.getId());
        if (who == null || e == MC.getCameraEntity() || !(e instanceof Unit u))
            return;
        String me = MC.player.getName().getString();
        String owner = u.getOwnerName();
        if (!who.equals(me) && !me.equals(owner) && !AlliancesClient.isAllied(me, owner))
            return;
        evt.setContent(Component.literal("★ " + who));
        evt.setResult(Event.Result.ALLOW);
    }

    // Esc while possessing: "Return to command" on the pause screen
    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post evt) {
        if (possessedId < 0 || !(evt.getScreen() instanceof PauseScreen screen))
            return;
        evt.addListener(net.minecraft.client.gui.components.Button.builder(Component.literal("Return to command"), b -> {
            MC.setScreen(null);
            requestRelease();
        }).bounds(screen.width / 2 - 102, Math.max(4, screen.height / 4 - 16), 204, 20).build());
    }

    @SubscribeEvent
    public static void onClientLogout(ClientPlayerNetworkEvent.LoggingOut evt) {
        possessedId = -1;
        heroRole = false;
        possessors.clear();
        objective = PossessionServer.Objective.NONE;
    }
}
