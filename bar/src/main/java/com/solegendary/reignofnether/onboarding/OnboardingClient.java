package com.solegendary.reignofnether.onboarding;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.api.ReignOfNetherRegistries;
import com.solegendary.reignofnether.bot.BotPlayer;
import com.solegendary.reignofnether.building.Building;
import com.solegendary.reignofnether.config.ReignOfNetherClientConfigs;
import com.solegendary.reignofnether.faction.Faction;
import com.solegendary.reignofnether.guiscreen.TopdownGui;
import com.solegendary.reignofnether.keybinds.Keybindings;
import com.solegendary.reignofnether.orthoview.OrthoviewClientEvents;
import com.solegendary.reignofnether.player.PlayerClientEvents;
import com.solegendary.reignofnether.player.RTSPlayer;
import com.solegendary.reignofnether.unit.UnitClientEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * Client side of the "First Battle" tutorial (design/onboarding.md):
 * <ul>
 *   <li>the title screen entry ("First Battle" beside Skirmish, and a "?" for the controls sheet);</li>
 *   <li>the launch: a fresh world per tutorial, like Skirmish (QuickBattle), then the RTS view and the START packet;
 *       Resume re-opens the last tutorial world and the server sends the saved step back;</li>
 *   <li>watching for the camera and selection steps, which only the client can see, and reporting them;</li>
 *   <li>the step panel (top left, under the control groups: what to do, step n of m, Continue / Skip step / Quit,
 *       collapsible) and the world highlight (a ring of light at the metal patch, the wrecks, a commander).</li>
 * </ul>
 * Nothing here runs outside a tutorial except the title-screen hook. While one runs, the per-tick work is a few
 * float compares and, every fourth tick, a dozen particles; the panel's text is wrapped once per step.
 */
public class OnboardingClient {

    static final Minecraft MC = Minecraft.getInstance();
    static final String WORLD_DIR_PREFIX = "beyondandtabs_tutorial_";
    static final String WORLD_NAME_PREFIX = "Beyond and Tabs - First Battle ";

    // what the server last told us
    private static OnboardingStep step = null;
    private static BlockPos highlight = null;
    private static boolean inProgress = false;
    private static String enemyName = "";
    private static boolean reported = false;
    private static boolean finishing = false;
    private static boolean collapsed = false;
    private static String endMessage = "";
    private static int endTicks = 0;

    // launch / resume
    private static int launchPhase = 0;   // 0 idle, 1 waiting for the world, 2 send START
    private static int launchWait = 0;
    private static int launchFaction = 0;
    private static int enterRtsIn = -1;

    // client-step watchers (reset on every step)
    private static float lastZoom = -1;
    private static double lastX, lastZ;
    private static float lastYaw, lastPitch;
    private static float zoomStart = -1;
    private static double panAccum = 0, rotAccum = 0;
    private static int tick = 0;

    // panel geometry this frame (for clicks) and the wrapped text cache
    private static int px, py, pw, ph;
    private static int b1x, b2x, by, bw, bh, cx, cy;
    private static OnboardingStep wrappedFor = null;
    private static int wrappedWidth = -1;
    private static final List<FormattedCharSequence> wrapped = new ArrayList<>();

    public static boolean isTutorialActive() {
        return step != null;
    }

    public static OnboardingStep currentStep() {
        return step;
    }

    // ------------------------------------------------------------------ title screen

    /**
     * "First Battle" takes the left half of the Skirmish row (Skirmish keeps the right half), so a new player sees it
     * first and the gothic lettering above stays clear (design/splash.md: nothing above height/4 + 22). A "?" to the
     * left of the row opens the controls sheet.
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onTitleInit(ScreenEvent.Init.Post evt) {
        if (!(evt.getScreen() instanceof TitleScreen title))
            return;
        int rowY = title.height / 4 + 22;
        int left = title.width / 2 - 100;
        for (GuiEventListener l : evt.getListenersList())
            if (l instanceof Button b && b.getMessage().getContents() instanceof TranslatableContents tc
                    && "quickbattle.reignofnether.button".equals(tc.getKey())) {
                rowY = b.getY();
                left = b.getX();
                b.setWidth(98);
                b.setX(left + 102);
            }
        Button first = Button.builder(Component.translatable("onboarding.reignofnether.button"),
            b -> MC.setScreen(new FirstBattleScreen(title))).bounds(left, rowY, 98, 20).build();
        first.setTooltip(Tooltip.create(Component.translatable("onboarding.reignofnether.button.tooltip")));
        evt.addListener(first);
        Button controls = Button.builder(Component.literal("?"), b -> MC.setScreen(new ControlsReferenceScreen(title)))
            .bounds(left - 24, rowY, 20, 20).build();
        controls.setTooltip(Tooltip.create(Component.translatable("onboarding.reignofnether.controls.button.tooltip")));
        evt.addListener(controls);
    }

    // ------------------------------------------------------------------ launch / resume

    /** The world folder of the last unfinished tutorial, if it still exists. */
    static String resumableWorld() {
        String dir = ReignOfNetherClientConfigs.ONBOARDING_WORLD.get();
        if (dir == null || dir.isEmpty())
            return null;
        try {
            return MC.getLevelSource().levelExists(dir) ? dir : null;
        } catch (Exception e) {
            return null;
        }
    }

    /** First Battle -> Start: a fresh world (an old tutorial never bleeds in), then the scenario on the server. */
    static void launch(int factionCode) {
        launchFaction = factionCode;
        launchPhase = 1;
        launchWait = 0;
        int n = 1;
        while (MC.getLevelSource().levelExists(WORLD_DIR_PREFIX + n))
            n++;
        String dir = WORLD_DIR_PREFIX + n;
        ReignOfNetherClientConfigs.ONBOARDING_WORLD.set(dir);
        GameRules rules = new GameRules();
        rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
        rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, null);   // no rain over the lesson
        LevelSettings settings = new LevelSettings(WORLD_NAME_PREFIX + n, GameType.CREATIVE, false,
            Difficulty.PEACEFUL, true, rules, WorldDataConfiguration.DEFAULT);
        MC.createWorldOpenFlows().createFreshLevel(dir, settings,
            com.solegendary.reignofnether.startpos.ArenaStructureClearing.skirmishWorldOptions(WorldOptions.randomSeed()),
            OnboardingClient::battlefield);
    }

    /** The Battlefield preset Skirmish uses (gentle grassland), vanilla terrain as the fallback. */
    static net.minecraft.world.level.levelgen.WorldDimensions battlefield(net.minecraft.core.RegistryAccess access) {
        try {
            var presets = access.registryOrThrow(net.minecraft.core.registries.Registries.WORLD_PRESET);
            var preset = presets.get(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.WORLD_PRESET,
                ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "battlefield")));
            if (preset != null)
                return preset.createWorldDimensions();
        } catch (Exception e) {
            ReignOfNether.LOGGER.error("[Onboarding] battlefield preset unavailable, using vanilla terrain", e);
        }
        return WorldPresets.createNormalWorldDimensions(access);
    }

    /** Resume: open the saved tutorial world; the server sends the saved step on login. */
    static void resume(net.minecraft.client.gui.screens.Screen from) {
        String dir = resumableWorld();
        if (dir == null)
            return;
        launchPhase = 0;
        MC.createWorldOpenFlows().loadLevel(from, dir);
    }

    // ------------------------------------------------------------------ packets

    static void onPacket(OnboardingClientboundPacket p) {
        switch (p.kind) {
            case STEP -> {
                OnboardingStep s = OnboardingStep.byOrdinal(p.step);
                if (s != step) {
                    reported = false;
                    resetWatchers();
                }
                if (finishing)
                    return;
                step = s;
                highlight = p.hasHighlight ? p.highlight : null;
                inProgress = p.inProgress;
                enemyName = p.text;
                if (s == OnboardingStep.DONE)
                    ReignOfNetherClientConfigs.ONBOARDING_WORLD.set("");   // finished: nothing left to resume
                // a resumed tutorial: put the RTS view back on once the world has settled
                if (!OrthoviewClientEvents.isEnabled() && launchPhase == 0 && enterRtsIn < 0)
                    enterRtsIn = 30;
            }
            case END -> {
                boolean wasFinishing = finishing;
                step = null;
                highlight = null;
                finishing = false;
                if (!wasFinishing) {
                    endMessage = I18n.get(p.text);
                    endTicks = 20 * 8;
                }
                ReignOfNetherClientConfigs.ONBOARDING_WORLD.set("");
            }
        }
    }

    private static void resetWatchers() {
        lastZoom = -1;
        zoomStart = -1;
        panAccum = 0;
        rotAccum = 0;
    }

    // ------------------------------------------------------------------ ticking

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent evt) {
        if (evt.phase != TickEvent.Phase.END)
            return;
        if (endTicks > 0)
            endTicks--;
        if (MC.player == null || MC.level == null)
            return;
        tickLaunch();
        if (step == null)
            return;
        tick++;
        if (!reported && step.kind == OnboardingStep.Kind.CLIENT && OrthoviewClientEvents.isEnabled() && clientStepDone()) {
            reported = true;
            OnboardingServerboundPacket.report(step);
        }
        if (highlight != null && tick % 4 == 0)
            spawnHighlight(highlight);
    }

    private static void tickLaunch() {
        // like QuickBattle: the RTS view itself is a screen, any other screen (pause, chat) holds us
        boolean free = MC.screen == null || MC.screen instanceof TopdownGui;
        if (enterRtsIn >= 0 && free && --enterRtsIn < 0 && !OrthoviewClientEvents.isEnabled())
            OrthoviewClientEvents.toggleEnable();
        if (launchPhase == 0 || !free)
            return;
        // only ever in the tutorial world just made (a failed creation must not start a tutorial in some other world)
        var sp = MC.getSingleplayerServer();
        if (sp == null || !sp.getWorldData().getLevelName().startsWith(WORLD_NAME_PREFIX)) {
            launchPhase = 0;
            return;
        }
        launchWait++;
        if (launchPhase == 1 && launchWait > 40) {
            if (!OrthoviewClientEvents.isEnabled())
                OrthoviewClientEvents.toggleEnable();
            launchPhase = 2;
            launchWait = 0;
        } else if (launchPhase == 2 && launchWait > 20) {
            OnboardingServerboundPacket.start(launchFaction);
            launchPhase = 0;
        }
    }

    /**
     * The camera and selection steps. Pan and rotation are summed only over ticks where the zoom held still, so the
     * zoom-to-cursor drift of the previous step doesn't finish the pan step by itself. Any way of doing it counts
     * (keys, edges, middle-drag) - a Mac mouse has no middle button, and the step is about knowing you can.
     */
    private static boolean clientStepDone() {
        float zoom = OrthoviewClientEvents.getZoom();
        double x = MC.player.getX(), z = MC.player.getZ();
        float yaw = OrthoviewClientEvents.getCamRotX(), pitch = OrthoviewClientEvents.getCamRotY();
        if (lastZoom < 0) {
            lastZoom = zoom;
            zoomStart = zoom;
            lastX = x;
            lastZ = z;
            lastYaw = yaw;
            lastPitch = pitch;
            return false;
        }
        boolean zoomSteady = Math.abs(zoom - lastZoom) <= lastZoom * 0.01f;
        if (zoomSteady) {
            panAccum += Math.sqrt((x - lastX) * (x - lastX) + (z - lastZ) * (z - lastZ));
            rotAccum += Math.abs(Mth.wrapDegrees(yaw - lastYaw)) + Math.abs(pitch - lastPitch);
        }
        lastZoom = zoom;
        lastX = x;
        lastZ = z;
        lastYaw = yaw;
        lastPitch = pitch;
        String me = MC.player.getName().getString();
        return switch (step) {
            case ZOOM -> zoom > zoomStart * 1.35f || zoom < zoomStart * 0.74f;
            case PAN -> panAccum > 12;
            case ROTATE -> rotAccum > 25;
            case OVERVIEW -> OrthoviewClientEvents.isOverviewActive();
            case SELECT -> ownedSelected(me) == 1;
            case BOX_SELECT -> ownedSelected(me) >= 2;
            default -> false;
        };
    }

    private static int ownedSelected(String me) {
        int n = 0;
        for (LivingEntity le : UnitClientEvents.getSelectedUnits())
            if (le instanceof Unit u && me.equals(u.getOwnerName()))
                n++;
        return n;
    }

    /** A turning ring of light with a short column at its centre: readable from straight above and from an angle. */
    private static void spawnHighlight(BlockPos at) {
        double t = (tick % 80) / 80.0 * Math.PI * 2;
        double cxw = at.getX() + 0.5, czw = at.getZ() + 0.5, y = at.getY() + 1.2;
        for (int i = 0; i < 8; i++) {
            double a = t + Math.PI * 2 * i / 8;
            MC.level.addParticle(ParticleTypes.END_ROD, cxw + Math.cos(a) * 3.0, y, czw + Math.sin(a) * 3.0, 0, 0.01, 0);
        }
        MC.level.addParticle(ParticleTypes.END_ROD, cxw, y + (tick % 16) * 0.5, czw, 0, 0.05, 0);
    }

    // ------------------------------------------------------------------ panel

    /** Arguments for the step's body text: the player's actual keys and their faction's building names. */
    private static Object[] bodyArgs(OnboardingStep s) {
        BotPlayer.Kit kit = ownKit();
        return switch (s) {
            case ZOOM -> new Object[] { Keybindings.zoomIn.getDisplayName(), Keybindings.zoomOut.getDisplayName() };
            case PAN -> new Object[] { Keybindings.panUp.getDisplayName() + Keybindings.panLeft.getDisplayName()
                + Keybindings.panDown.getDisplayName() + Keybindings.panRight.getDisplayName() };
            case ROTATE -> new Object[] { Keybindings.reset.getDisplayName() };
            case OVERVIEW -> new Object[] { Keybindings.overview.getDisplayName() };
            case EXTRACTOR -> new Object[] { kit != null ? buildingName(kit.extractor()) : "Metal Extractor" };
            case ENERGY -> new Object[] { kit != null ? buildingName(kit.wind()) : "Wind Generator" };
            case LAB, TRAIN -> new Object[] { kit != null ? buildingName(kit.armyBuilding()) : "lab" };
            case ATTACK_MOVE -> new Object[] { Keybindings.fight.getDisplayName() };
            case KILL_COMMANDER -> new Object[] { enemyName };
            case DONE -> new Object[] { Keybindings.statsTable.getDisplayName(), Keybindings.toggleCamera.getDisplayName() };
            default -> new Object[0];
        };
    }

    private static BotPlayer.Kit ownKit() {
        RTSPlayer p = PlayerClientEvents.getRTSPlayer();
        Faction f = p != null ? p.faction : null;
        return f == null ? null : BotPlayer.kitFor(f);
    }

    /** The name the build button shows (buildings.&lt;faction&gt;.&lt;ns&gt;.&lt;path&gt;), else the building's own name. */
    static String buildingName(Building b) {
        if (b == null)
            return "?";
        ResourceLocation key = ReignOfNetherRegistries.BUILDING.getKey(b);
        if (key != null && b.getFaction() != null) {
            String k = "buildings." + b.getFaction().getName() + "." + key.getNamespace() + "." + key.getPath();
            if (I18n.exists(k))
                return I18n.get(k);
        }
        return b.name == null ? "?" : I18n.get(b.name);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRender(ScreenEvent.Render.Post evt) {
        if (!(evt.getScreen() instanceof TopdownGui) || !OrthoviewClientEvents.isEnabled())
            return;
        GuiGraphics g = evt.getGuiGraphics();
        Font font = MC.font;
        int sw = evt.getScreen().width;
        if (step == null) {
            pw = 0;
            if (endTicks > 0 && !endMessage.isEmpty()) {
                int w = Math.min(font.width(endMessage) + 12, sw - 8);
                OnboardingUi.panel(g, 4, 44, 4 + w, 60, Math.min(1f, endTicks / 20f));
                g.drawString(font, font.plainSubstrByWidth(endMessage, w - 10), 9, 48,
                    OnboardingUi.withAlpha(OnboardingUi.TEXT, Math.min(1f, endTicks / 20f)), false);
            }
            return;
        }
        int mx = (int) evt.getMouseX(), my = (int) evt.getMouseY();
        px = 4;
        py = 44;
        pw = Mth.clamp(sw / 3, 150, 214);
        int inner = pw - 12;
        if (wrappedFor != step || wrappedWidth != inner) {
            wrapped.clear();
            wrapped.addAll(font.split(Component.literal(I18n.get(step.bodyKey(), bodyArgs(step))), inner));
            wrappedFor = step;
            wrappedWidth = inner;
        }
        String header = I18n.get("onboarding.reignofnether.panel.header");
        String count = step == OnboardingStep.WELCOME || step == OnboardingStep.DONE ? ""
            : step.number() + " / " + OnboardingStep.total();
        String title = I18n.get(step.titleKey());
        if (collapsed) {
            ph = 26;
            OnboardingUi.panel(g, px, py, px + pw, py + ph, 1f);
            g.drawString(font, header, px + 6, py + 4, OnboardingUi.GOLD_HOT, false);
            g.drawString(font, font.plainSubstrByWidth(title, pw - 12), px + 6, py + 14, OnboardingUi.TEXT_HOT, false);
        } else {
            bh = 14;
            ph = 6 + 10 + 11 + 5 + wrapped.size() * 10 + (inProgress ? 11 : 0) + 6 + bh + 6;
            OnboardingUi.panel(g, px, py, px + pw, py + ph, 1f);
            int y = py + 5;
            g.drawString(font, header, px + 6, y, OnboardingUi.GOLD_HOT, false);
            g.drawString(font, count, px + pw - 18 - font.width(count), y, OnboardingUi.TEXT_DIM, false);
            y += 10;
            g.drawString(font, font.plainSubstrByWidth(title, pw - 12), px + 6, y, OnboardingUi.TEXT_HOT, false);
            y += 11;
            OnboardingUi.rule(g, px + 6, px + pw - 6, y);
            y += 5;
            for (FormattedCharSequence line : wrapped) {
                g.drawString(font, line, px + 6, y, OnboardingUi.TEXT, false);
                y += 10;
            }
            if (inProgress) {
                g.drawString(font, I18n.get("onboarding.reignofnether.panel.building"), px + 6, y, OnboardingUi.WARN, false);
                y += 11;
            }
            y += 6;
            by = y;
            bw = (pw - 16) / 2;
            b1x = px + 6;
            b2x = px + 10 + bw;
            boolean info = step.kind == OnboardingStep.Kind.INFO;
            String primary = I18n.get(step == OnboardingStep.DONE ? "onboarding.reignofnether.panel.finish"
                : info ? "onboarding.reignofnether.panel.continue" : "onboarding.reignofnether.panel.skip");
            OnboardingUi.button(g, font, b1x, by, bw, bh, primary, OnboardingUi.inside(mx, my, b1x, by, bw, bh), true);
            if (step != OnboardingStep.DONE)
                OnboardingUi.button(g, font, b2x, by, bw, bh, I18n.get("onboarding.reignofnether.panel.quit"),
                    OnboardingUi.inside(mx, my, b2x, by, bw, bh), true);
        }
        // collapse / expand chevron in the header's corner
        cx = px + pw - 13;
        cy = py + 3;
        boolean hot = OnboardingUi.inside(mx, my, cx, cy, 10, 10);
        g.drawString(font, collapsed ? "+" : "-", cx + 3, cy + 1, hot ? OnboardingUi.GOLD_HOT : OnboardingUi.GOLD, false);
    }

    /** Clicks on the panel are the panel's: never a selection box or an order underneath. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onMouse(ScreenEvent.MouseButtonPressed.Pre evt) {
        if (step == null || pw == 0 || !(evt.getScreen() instanceof TopdownGui) || !OrthoviewClientEvents.isEnabled())
            return;
        double mx = evt.getMouseX(), my = evt.getMouseY();
        if (!OnboardingUi.inside(mx, my, px, py, pw, ph))
            return;
        evt.setCanceled(true);
        if (evt.getButton() != GLFW.GLFW_MOUSE_BUTTON_1)
            return;
        if (OnboardingUi.inside(mx, my, cx, cy, 10, 10)) {
            collapsed = !collapsed;
            return;
        }
        if (collapsed)
            return;
        if (OnboardingUi.inside(mx, my, b1x, by, bw, bh)) {
            if (step == OnboardingStep.DONE) {
                finishing = true;
                step = null;
                OnboardingServerboundPacket.quit();
            } else if (step.kind == OnboardingStep.Kind.INFO) {
                OnboardingServerboundPacket.report(step);
            } else {
                OnboardingServerboundPacket.skip(step);
            }
        } else if (step != OnboardingStep.DONE && OnboardingUi.inside(mx, my, b2x, by, bw, bh)) {
            OnboardingServerboundPacket.quit();
        }
    }

    /** Whether the cursor is over the step panel (for anything that wants to keep clear of it). */
    public static boolean isMouseOverPanel(double mx, double my) {
        return step != null && pw > 0 && OnboardingUi.inside(mx, my, px, py, pw, ph);
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut evt) {
        step = null;
        highlight = null;
        finishing = false;
        enterRtsIn = -1;
        launchPhase = 0;
        endTicks = 0;
        wrappedFor = null;
        resetWatchers();
    }
}
