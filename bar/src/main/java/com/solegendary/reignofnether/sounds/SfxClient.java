package com.solegendary.reignofnether.sounds;

import com.solegendary.reignofnether.barfx.BarFx;
import com.solegendary.reignofnether.barfx.BarFxClient;
import com.solegendary.reignofnether.config.ReignOfNetherClientConfigs;
import com.solegendary.reignofnether.faction.Faction;
import com.solegendary.reignofnether.faction.FactionTraits;
import com.solegendary.reignofnether.fogofwar.FogOfWarClientEvents;
import com.solegendary.reignofnether.registrars.SoundRegistrar;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Client playback of the mod's sound effects (sounds.Sfx, design/audio.md).
 *
 * <p><b>Where sounds come from.</b> {@link #onFx} sees every BarFx event the client receives (BarFxClient.ingest):
 * shots by weapon class, melee and building hits, faction deaths, building collapses, builders' nanolathe beams
 * (the construction "loop"), reclaim beams, and the SFX events the server sends for discrete moments. A few client
 * hooks call in directly (a building finishing, the match starting, the start-position phase).
 *
 * <p><b>Nothing gets worse with army size</b> (lovish). Three layers keep a 200-unit fight from turning into noise:
 * each sound has a minimum gap between plays (Sfx.minGapTicks), each category has a per-tick budget (a fight of any
 * size plays at most a handful of weapon sounds a tick), and each category tracks how many sounds it was asked for
 * lately: the busier it is, the quieter each one plays, so the overall level stays roughly flat as the fight grows.
 * When a category is busy, far events are skipped first.
 *
 * <p><b>Distance.</b> Volume falls off linearly from the "ear" (the middle of the screen in the RTS view, else the
 * camera) - full within NEAR blocks, silent at the category's reach, which grows with the RTS zoom. Minecraft's own
 * attenuation is only 16 blocks, so each sound is placed at a stand-in point a few blocks from the camera in the true
 * direction: it pans correctly while the volume is ours.
 *
 * <p>Allocation: one SimpleSoundInstance per played sound (bounded by the budgets); the checks before allocate nothing.
 */
@OnlyIn(Dist.CLIENT)
public final class SfxClient {

    private SfxClient() { }

    private static final Minecraft MC = Minecraft.getInstance();
    private static final RandomSource RANDOM = RandomSource.create();
    private static final Sfx[] ALL = Sfx.values();
    private static final Sfx.Category[] CATS = Sfx.Category.values();

    // per-sound: game tick of the last play
    private static final long[] lastPlay = new long[ALL.length];
    // per-category: sounds played this tick, sounds asked for this tick, and a smoothed "asked per tick"
    private static final int[] playedThisTick = new int[CATS.length];
    private static final int[] askedThisTick = new int[CATS.length];
    private static final float[] busy = new float[CATS.length];
    private static long tick = Long.MIN_VALUE;

    /** Most new sounds a category may start in one tick (UNITS, BUILDINGS, ABILITIES, ANNOUNCER, UI). */
    private static final int[] BUDGET = { 3, 2, 3, 4, 4 };
    /** Reach (blocks from the ear) at RTS zoom 0 / first person; the RTS zoom adds to it. */
    private static final float[] REACH = { 36, 48, 56, 72, 72 };
    private static final float NEAR = 10;
    /** Above this smoothed request rate a category plays each sound quieter (1/sqrt of the excess). */
    private static final float CALM_RATE = 1.5f;
    // how far from the camera the stand-in point sits (well inside Minecraft's 16-block attenuation)
    private static final float PROXY_DIST = 3f;

    private static final float[] ear = new float[2];

    // ------------------------------------------------------------------ BarFx events

    /** Every BarFx event the client receives. Must never throw (BarFxClient keeps drawing after us). */
    public static void onFx(BarFx.Event e) {
        try {
            switch (e.type) {
                case BarFx.SFX -> {
                    Sfx s = Sfx.byId(e.kind & 0xFF);
                    if (s == null)
                        return;
                    if ((e.flags & BarFx.SFX_GLOBAL) != 0) {
                        playGlobal(s, e.a, e.b);
                        onGlobalMoment(s);
                    } else
                        playAt(s, e.x, e.y, e.z, e.a, e.b);
                }
                case BarFx.SHOT -> {
                    Sfx s = BarFx.shotDetail(e.flags) == BarFx.W_GUN ? Sfx.SHOT_GUN : shotSound(e.kind);
                    if (s != null)
                        playAt(s, e.x, e.y, e.z, 1f, 1f);
                }
                case BarFx.IMPACT -> {
                    Sfx s = BarFx.shotDetail(e.flags) == BarFx.AT_BUILDING ? Sfx.HIT_BUILDING
                        : e.kind == BarFx.K_MELEE ? Sfx.HIT_MELEE : e.kind == BarFx.K_HEAVY_MELEE ? Sfx.HIT_HEAVY : null;
                    if (s != null)
                        playAt(s, e.x, e.y, e.z, Math.min(1f, 0.6f + e.b * 0.25f), 1f);
                }
                case BarFx.DEATH -> {
                    int tier = BarFx.deathTier(e.flags);
                    if (tier >= 3)
                        playAt(Sfx.DEATH_LARGE, e.x, e.y, e.z, 1f, 1f);
                    else if (tier > 0) {
                        Sfx s = Sfx.death(BarFx.deathFaction(e.flags));
                        if (s != null)   // a T2 body dies a little deeper and louder than a T1
                            playAt(s, e.x, e.y, e.z, tier == 2 ? 1f : 0.8f, tier == 2 ? 0.85f : 1f);
                    }
                }
                case BarFx.COLLAPSE -> {
                    Sfx s = Sfx.destroyed(BarFx.collapseFaction(e.flags));
                    playAt(s != null ? s : Sfx.DEATH_LARGE, e.x, e.y, e.z, Math.min(1f, 0.6f + e.a / 10f), 1f);
                }
                case BarFx.NANO -> {
                    // flags = the beam's life: a builder's beam lives NANO_INTERVAL + 3, a reclaim/tree beam a full
                    // second-long step (22); longer standing links (ley lines) have their own chime and stay silent
                    int life = e.flags & 0xFF;
                    if (life <= BarFx.NANO_INTERVAL + 4)
                        playAt(Sfx.nano(e.kind), e.x, e.y, e.z, 1f, 1f);
                    else if (life <= 24)
                        playAt(Sfx.RECLAIM_TICK, e.x, e.y, e.z, 1f, 1f);
                }
                default -> { }
            }
        } catch (Exception ignored) { }
    }

    static Sfx shotSound(byte kind) {
        return switch (kind) {
            case BarFx.K_ARROW -> Sfx.SHOT_BOW;
            case BarFx.K_FIREBALL -> Sfx.SHOT_FIRE;
            case BarFx.K_BIG_FIREBALL, BarFx.K_TNT -> Sfx.SHOT_HEAVY;
            case BarFx.K_MAGIC -> Sfx.SHOT_MAGIC;
            case BarFx.K_ROCKET -> Sfx.SHOT_ROCKET;
            case BarFx.K_THROWN, BarFx.K_POTION -> Sfx.SHOT_THROWN;
            default -> null;   // sonic, melee swings, other: their own vanilla sounds already say it
        };
    }

    // global moments that also have an announcer line
    private static void onGlobalMoment(Sfx s) {
        switch (s) {
            case GAMEENDER_START -> com.solegendary.reignofnether.hud.NotificationClientEvents.onGameEnderMoment("game_ender_started");
            case GAMEENDER_AIM -> com.solegendary.reignofnether.hud.NotificationClientEvents.onGameEnderMoment("game_ender_aimed");
            case GAMEENDER_INTERCEPT -> com.solegendary.reignofnether.hud.NotificationClientEvents.onGameEnderMoment("game_ender_intercepted");
            default -> { }
        }
    }

    // ------------------------------------------------------------------ client hooks

    /** A building finished (BuildingPlacement.onBuilt, client side; the caller already skipped stale placements). */
    public static void onBuildingBuilt(Faction faction, BlockPos centre) {
        if (faction == null || centre == null)
            return;
        Sfx s = Sfx.built(FactionTraits.of(faction).deathDebris);
        if (s != null)
            playAt(s, centre.getX() + 0.5f, centre.getY() + 0.5f, centre.getZ() + 0.5f, 1f, 1f);
    }

    /** The match started for us (NotificationClientEvents.onGameStart): our faction's short fanfare. */
    public static void onMatchStart(Faction faction) {
        if (faction == null || !stingersOn())
            return;
        Sfx s = Sfx.stinger(FactionTraits.of(faction).deathDebris);
        if (s != null)
            playGlobal(s, 1f, 1f);
    }

    /** Start-position phase (MapClient.onState): began, ended, a pick moved, the last seconds of the countdown. */
    public static void onStartPhase(boolean wasActive, boolean active, int secondsLeft, int lastSecondsLeft,
                                    boolean picksChanged) {
        if (!wasActive && active) {
            playGlobal(Sfx.STARTPOS_BEGIN, 1f, 1f);
            com.solegendary.reignofnether.hud.NotificationClientEvents.onStartPositions();
        } else if (wasActive && !active)
            playGlobal(Sfx.STARTPOS_END, 1f, 1f);
        else if (active) {
            if (picksChanged)
                playGlobal(Sfx.STARTPOS_PLACE, 1f, 1f);
            if (secondsLeft != lastSecondsLeft && secondsLeft > 0 && secondsLeft <= 5)
                playGlobal(Sfx.STARTPOS_TICK, 1f, secondsLeft == 1 ? 1.25f : 1f);
        }
    }

    // ------------------------------------------------------------------ playback

    private static boolean stingersOn() {
        try {
            return ReignOfNetherClientConfigs.MUSIC_STINGERS.get();
        } catch (IllegalStateException e) {
            return true;
        }
    }

    /** The category's volume from the client config, 0..1 (Announcer shares the announcer voice's slider). */
    public static float categoryVolume(Sfx.Category c) {
        try {
            int v = switch (c) {
                case UNITS -> ReignOfNetherClientConfigs.SFX_UNITS_VOLUME.get();
                case BUILDINGS -> ReignOfNetherClientConfigs.SFX_BUILDINGS_VOLUME.get();
                case ABILITIES -> ReignOfNetherClientConfigs.SFX_ABILITIES_VOLUME.get();
                case ANNOUNCER -> ReignOfNetherClientConfigs.VOICE_VOLUME.get();
                case UI -> ReignOfNetherClientConfigs.SFX_UI_VOLUME.get();
            };
            return Math.max(0, Math.min(100, v)) / 100f;
        } catch (IllegalStateException e) {   // config not loaded yet
            return 1f;
        }
    }

    private static SoundSource sourceOf(Sfx s) {
        return switch (s.category) {
            case UNITS -> SoundSource.NEUTRAL;
            case BUILDINGS -> SoundSource.BLOCKS;
            case ABILITIES -> SoundSource.PLAYERS;
            // match stingers follow the music slider; everything else spoken/announced follows master like the voice
            case ANNOUNCER -> s.key.startsWith("stinger_") ? SoundSource.MUSIC : SoundSource.MASTER;
            case UI -> SoundSource.MASTER;
        };
    }

    /** Rolls the per-tick counters over when the game tick changed; decays the busy rate per tick elapsed. */
    private static long rollTick() {
        long now = MC.level != null ? MC.level.getGameTime() : 0;
        if (now != tick) {
            long steps = tick == Long.MIN_VALUE ? 1 : Math.max(1, Math.min(40, now - tick));
            for (int c = 0; c < CATS.length; c++) {
                float b = busy[c] * 0.9f + askedThisTick[c] * 0.1f;
                for (long i = 1; i < steps; i++)
                    b *= 0.9f;
                busy[c] = b;
                askedThisTick[c] = 0;
                playedThisTick[c] = 0;
            }
            tick = now;
        }
        return now;
    }

    /** Gap and budget; true if the sound may play now (and books it). */
    private static boolean admit(Sfx s, long now, float dist) {
        int c = s.category.ordinal();
        askedThisTick[c]++;
        long last = lastPlay[s.ordinal()];
        if (last != 0 && now >= last && now - last < s.minGapTicks)
            return false;
        if (playedThisTick[c] >= BUDGET[c])
            return false;
        // busy: the far half of the reach only gets a turn when the category is calm
        if (busy[c] > CALM_RATE * 2 && dist > NEAR * 2.5f)
            return false;
        playedThisTick[c]++;
        lastPlay[s.ordinal()] = now == 0 ? 1 : now;
        return true;
    }

    private static float crowdScale(Sfx s) {
        float b = busy[s.category.ordinal()];
        return b <= CALM_RATE ? 1f : (float) Math.sqrt(CALM_RATE / b);
    }

    /** Plays a sound at a world position, if it is in earshot and not hidden by fog. */
    public static void playAt(Sfx s, float x, float y, float z, float volume, float pitch) {
        if (s == null || MC.level == null || MC.player == null)
            return;
        float catVol = categoryVolume(s.category);
        if (catVol <= 0)
            return;
        long now = rollTick();
        BarFxClient.earAt(y, ear);
        float dx = x - ear[0], dz = z - ear[1];
        float dist = (float) Math.sqrt(dx * dx + dz * dz);
        float reach = REACH[s.category.ordinal()] + BarFxClient.earZoom() * 0.5f;
        if (dist >= reach)
            return;
        if (!admit(s, now, dist))
            return;
        // fog last: it allocates a BlockPos, and most events never get this far
        if (!FogOfWarClientEvents.isInBrightChunk(BlockPos.containing(x, y, z)))
            return;
        float falloff = dist <= NEAR ? 1f : 1f - (dist - NEAR) / (reach - NEAR);
        float vol = s.volume * volume * catVol * falloff * crowdScale(s);
        if (vol <= 0.01f)
            return;
        SoundEvent ev = SoundRegistrar.sfx(s);
        if (ev == null)
            return;
        // the stand-in point: from the camera, PROXY_DIST blocks towards the true direction of the sound
        Vec3 cam = MC.gameRenderer.getMainCamera().getPosition();
        double px = cam.x, py = cam.y, pz = cam.z;
        if (dist > 0.5f) {
            px += dx / dist * PROXY_DIST;
            pz += dz / dist * PROXY_DIST;
        }
        MC.getSoundManager().play(new SimpleSoundInstance(ev.getLocation(), sourceOf(s), vol, jitter(pitch), RANDOM,
            false, 0, SoundInstance.Attenuation.LINEAR, px, py, pz, false));
    }

    /** Plays a sound for this player only, without position (match moments, UI). */
    public static void playGlobal(Sfx s, float volume, float pitch) {
        if (s == null || MC.player == null)
            return;
        float catVol = categoryVolume(s.category);
        if (catVol <= 0)
            return;
        long now = rollTick();
        long last = lastPlay[s.ordinal()];
        if (last != 0 && now >= last && now - last < s.minGapTicks)
            return;
        lastPlay[s.ordinal()] = now == 0 ? 1 : now;
        SoundEvent ev = SoundRegistrar.sfx(s);
        if (ev == null)
            return;
        MC.getSoundManager().play(new SimpleSoundInstance(ev.getLocation(), sourceOf(s), s.volume * volume * catVol,
            pitch, RANDOM, false, 0, SoundInstance.Attenuation.NONE, 0, 0, 0, true));
    }

    // a hair of pitch drift on top of the file variations: a volley never sounds like one sample on repeat
    private static float jitter(float pitch) {
        return pitch * (0.96f + RANDOM.nextFloat() * 0.08f);
    }

    /** Logout / new world: forget gaps and busy rates (game time restarts). */
    public static void reset() {
        java.util.Arrays.fill(lastPlay, 0);
        java.util.Arrays.fill(busy, 0);
        tick = Long.MIN_VALUE;
    }
}
