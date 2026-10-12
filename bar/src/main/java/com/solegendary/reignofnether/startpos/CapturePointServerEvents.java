package com.solegendary.reignofnether.startpos;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.player.PlayerPalette;
import com.solegendary.reignofnether.player.PlayerServerEvents;
import com.solegendary.reignofnether.player.RTSPlayer;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.resources.EconomyServerEvents;
import com.solegendary.reignofnether.resources.MetalPatches;
import com.solegendary.reignofnether.resources.Resources;
import com.solegendary.reignofnether.resources.ResourcesServerEvents;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Capturable neutral buildings (lovish, Oct 10: "buildings that can be captured that can provide helpful benefits
 * or units or an advantage"). The battlefield stamps a few small ruins between the bases; a player takes one by
 * keeping units inside {@link #RADIUS} blocks with no enemy units there, over {@link #CAPTURE_SECONDS} seconds.
 * An enemy standing alone on an owned site first drains it back to neutral, then takes it. Kinds:
 * <ul>
 *   <li><b>Ancient Mine</b> - +{@link #MINE_METAL} metal/s to the owner.</li>
 *   <li><b>Ley Shrine</b> - +{@link #SHRINE_ENERGY} energy/s to the owner.</li>
 *   <li><b>Mercenary Camp</b> - a hired wolf joins the owner every {@link #CAMP_INTERVAL_SECONDS} s, at most
 *       {@link #CAMP_MAX} alive at once.</li>
 * </ul>
 * Pre-made maps (design/maps.md) add their own kinds: <b>Sunken Bell</b> ({@link #BELL_SIGHT} blocks of vision),
 * <b>Watchtower</b> ({@link #WATCHTOWER_SIGHT}), <b>Ancient Forge</b> (research and upgrades {@link #FORGE_DISCOUNT}
 * cheaper), <b>Lava Vent</b> (+{@link #VENT_ENERGY} energy/s, geothermal), <b>Oasis</b> (heals the owner's units
 * within {@link #OASIS_RADIUS}) and <b>Forsaken Garrison</b> (musters the map's own recruit for its holder, see
 * {@link #muster}). The random-terrain ring only ever uses the first three.
 * Each site is a vanilla marker entity (invisible, saved with the world) carrying its kind, owner, progress, claimant,
 * camp timer and garrison muster in its persistent data, so all of it survives a save and reload with the marker's
 * chunk and {@link #onJoin} re-registers it; the ruin itself is ordinary blocks in the map's {@link Style}, and its
 * crown wool takes the owner's colour. Every unit a site gives carries {@link #RECRUIT_TAG} and its tie (camp wolves
 * {@link #KEY_HIRED}, garrison recruits {@link #KEY_RAISED_BY}), so a reload re-counts them against their cap instead
 * of letting the site hand out a fresh set. Once a second, cheap.
 */
public class CapturePointServerEvents {

    public static final String TAG = "bt_capture_point";
    // public for the game test (save and reload a site)
    public static final String KEY_KIND ="bt_cp_kind", KEY_OWNER = "bt_cp_owner", KEY_PROGRESS = "bt_cp_progress",
        KEY_CLAIMANT = "bt_cp_claimant", KEY_CAMP_TIMER = "bt_cp_camp";
    /** On a hired wolf: the player its camp hired it for (persistent data, so it is saved with the wolf). */
    public static final String KEY_HIRED = "bt_cp_hired";

    public static final double RADIUS = 6;
    public static final int CAPTURE_SECONDS = 20;
    public static final float MINE_METAL = 3f;
    public static final float SHRINE_ENERGY = 30f;
    public static final int CAMP_INTERVAL_SECONDS = 60;
    public static final int CAMP_MAX = 3;

    // appended only: a site's kind is saved by ordinal and travels by ordinal
    public enum Kind { MINE, SHRINE, CAMP, BELL, WATCHTOWER, FORGE, VENT, OASIS, GARRISON }

    /**
     * How a site's ruin is built: the default worn stone, a Nether fortress ruin (Soulfire Deltas) or an End obsidian
     * pillar (Chorus Isles). Only placement reads it - the blocks are saved with the world - except that it picks a
     * garrison's recruit, which the marker then keeps ({@link #KEY_RECRUIT_TYPE}).
     */
    public enum Style { RUIN, NETHER_BRICK, OBSIDIAN }

    /** The kinds the random-terrain battlefield rotates through (the generic sites). */
    static final Kind[] RANDOM_KINDS = { Kind.MINE, Kind.SHRINE, Kind.CAMP };

    public static final int BELL_SIGHT = 40;
    public static final int WATCHTOWER_SIGHT = 64;   // FogOfWarServerEvents.MAX_SIGHT_BLOCKS
    public static final float FORGE_DISCOUNT = 0.25f;
    public static final float VENT_ENERGY = 45f;
    public static final double OASIS_RADIUS = 12;
    public static final float OASIS_HEAL = 2f;   // hp per second per unit

    /**
     * Forsaken Garrison (design/maps.md section 3, lovish's vision #12 "abandoned structures that give units over time
     * while held"): one recruit every GARRISON_SECONDS while held and uncontested, at most GARRISON_MAX of its
     * recruits alive for the holder; while neutral it banks up to GARRISON_BANK for whoever takes it.
     */
    public static final int GARRISON_SECONDS = 90;
    public static final int GARRISON_MAX = 2;
    public static final int GARRISON_BANK = 2;
    public static final String KEY_MUSTER = "bt_cp_muster", KEY_BANK = "bt_cp_bank", KEY_RECRUIT_TYPE = "bt_cp_recruit_type";
    /** On a garrison recruit: the UUID of the site that raised it (persistent, so a reload re-counts it). */
    public static final String KEY_RAISED_BY = "bt_cp_raised_by";
    /**
     * Scoreboard tag on every unit a site gives (camp wolves, garrison recruits). onJoin runs for every entity that
     * ever joins; checking this tag first spares Forge's getPersistentData(), which creates (and from then on saves)
     * an empty ForgeData tag on every entity it is asked about (R2-10, the R2-03 fix's pattern).
     */
    public static final String RECRUIT_TAG = "bt_cp_recruit";

    private static final List<Entity> points = new ArrayList<>();
    private static final Map<String, List<Entity>> hired = new HashMap<>();
    /** Garrison recruits by the UUID of the site that raised them. */
    private static final Map<java.util.UUID, List<Entity>> recruits = new HashMap<>();
    /** Sites already reported for an unusable recruit type (log once, not once a second). */
    private static final java.util.Set<java.util.UUID> badRecruitWarned = new java.util.HashSet<>();
    private static final List<LivingEntity> scratch = new ArrayList<>();   // UnitGrid query results, reused

    public static List<Entity> getPoints() { return points; }
    public static String ownerOf(Entity p) { return p.getPersistentData().getString(KEY_OWNER); }
    public static float progressOf(Entity p) { return p.getPersistentData().getFloat(KEY_PROGRESS); }
    // Kind.values() clones its array on every call; kindOf runs per site per call of the fog, the tick and every
    // production step's forge discount (CR-12)
    private static final Kind[] KINDS = Kind.values();

    public static Kind kindOf(Entity p) {
        int k = p.getPersistentData().getInt(KEY_KIND);
        return KINDS[Math.max(0, Math.min(KINDS.length - 1, k))];
    }
    public static String claimantOf(Entity p) { return p.getPersistentData().getString(KEY_CLAIMANT); }

    /** Fog-of-war vision a site gives its owner (FogOfWarServerEvents), 0 for kinds that give none. */
    public static int sightOf(Entity p) {
        return switch (kindOf(p)) {
            case BELL -> BELL_SIGHT;
            case WATCHTOWER -> WATCHTOWER_SIGHT;
            default -> 0;
        };
    }

    /**
     * Cost multiplier for {@code owner}'s research and upgrades (ProductionItem): 1 - FORGE_DISCOUNT while they or
     * an ally hold an Ancient Forge, else 1. A handful of sites, so a scan per production tick is cheap.
     */
    public static float researchCostMultiplier(String owner) {
        if (owner == null || owner.isEmpty() || points.isEmpty())
            return 1f;
        for (Entity p : points) {
            if (p.isRemoved() || kindOf(p) != Kind.FORGE)
                continue;
            String o = ownerOf(p);
            if (!o.isEmpty() && (o.equals(owner) || AlliancesServerEvents.isAllied(o, owner)))
                return 1f - FORGE_DISCOUNT;
        }
        return 1f;
    }

    // ------------------------------------------------------------------------------------------ placement

    /** Builds a ruin of the given kind on the ground at (x, z) and registers its marker. */
    public static Entity place(ServerLevel level, int x, int z, Kind kind) {
        return place(level, x, z, kind, Style.RUIN);
    }

    /**
     * Builds a ruin of the given kind and style on the ground at (x, z) and registers its marker. Every style keeps
     * the same footprint (a 5x5 plinth, posts on its corners, a centre column) and the owner's wool crown three above
     * the ground on the centre column, where {@link #setOwner} recolours it.
     */
    public static Entity place(ServerLevel level, int x, int z, Kind kind, Style style) {
        int y = MetalPatches.solidTop(level, x, z);
        BlockPos base = new BlockPos(x, y, z);
        Block stone = switch (kind) {
            case MINE -> Blocks.COBBLED_DEEPSLATE;
            case SHRINE -> Blocks.MOSSY_STONE_BRICKS;
            case CAMP -> Blocks.SPRUCE_PLANKS;
            case BELL -> Blocks.PRISMARINE_BRICKS;
            case WATCHTOWER -> Blocks.STONE_BRICKS;
            case FORGE -> Blocks.POLISHED_BLACKSTONE_BRICKS;
            case VENT -> Blocks.BASALT;
            case OASIS -> Blocks.SMOOTH_SANDSTONE;
            case GARRISON -> Blocks.MOSSY_COBBLESTONE;
        };
        Block accent = switch (kind) {
            case MINE -> Blocks.RAW_IRON_BLOCK;
            case SHRINE -> Blocks.AMETHYST_BLOCK;
            case CAMP -> Blocks.HAY_BLOCK;
            case BELL -> Blocks.SEA_LANTERN;
            case WATCHTOWER -> Blocks.CHISELED_STONE_BRICKS;
            case FORGE -> Blocks.ANVIL;
            case VENT -> Blocks.MAGMA_BLOCK;
            case OASIS -> Blocks.MOSS_BLOCK;
            // a garrison shows whose bones it raises: a skeleton skull, a wither skull in the fortress, the dragon's
            // head on the End's pillar
            case GARRISON -> style == Style.NETHER_BRICK ? Blocks.WITHER_SKELETON_SKULL
                : style == Style.OBSIDIAN ? Blocks.DRAGON_HEAD : Blocks.SKELETON_SKULL;
        };
        if (style == Style.OBSIDIAN && kind != Kind.GARRISON)
            accent = Blocks.CRYING_OBSIDIAN;   // every End pillar glows the same; only the garrison wears the head
        // the style's plinth, corner stones and posts; the kind keeps its centre column and accent (the FORGE on
        // Soulfire Deltas is still blackstone and anvil, inside a nether-brick ruin)
        Block plinth, corner, post, lamp, cap;
        int postHeight;
        switch (style) {
            case NETHER_BRICK -> {
                plinth = Blocks.NETHER_BRICKS;
                corner = Blocks.CRACKED_NETHER_BRICKS;
                post = Blocks.NETHER_BRICK_FENCE;
                lamp = Blocks.SOUL_LANTERN;
                cap = Blocks.RED_NETHER_BRICKS;
                postHeight = 2;
            }
            case OBSIDIAN -> {
                plinth = Blocks.END_STONE_BRICKS;
                corner = Blocks.PURPUR_PILLAR;
                post = Blocks.OBSIDIAN;
                lamp = Blocks.END_ROD;
                cap = Blocks.OBSIDIAN;
                postHeight = 3;
            }
            default -> {
                plinth = stone;
                corner = Blocks.CRACKED_STONE_BRICKS;
                post = Blocks.STONE_BRICK_WALL;
                lamp = Blocks.LANTERN;
                cap = null;
                postHeight = 1;
            }
        }
        // a 5x5 worn plinth, four short posts with a light, a centre column with the kind's accent and a wool crown
        for (int dx = -2; dx <= 2; dx++)
            for (int dz = -2; dz <= 2; dz++) {
                level.setBlock(base.offset(dx, 0, dz), (Math.abs(dx) == 2 && Math.abs(dz) == 2 ? corner : plinth).defaultBlockState(), 3);
                for (int up = 1; up <= 4; up++)
                    if (!level.getBlockState(base.offset(dx, up, dz)).isAir())
                        level.setBlock(base.offset(dx, up, dz), Blocks.AIR.defaultBlockState(), 3);
            }
        for (int[] c : new int[][] { { -2, -2 }, { 2, -2 }, { -2, 2 }, { 2, 2 } }) {
            for (int up = 1; up <= postHeight; up++)
                level.setBlock(base.offset(c[0], up, c[1]), post.defaultBlockState(), 3);
            level.setBlock(base.offset(c[0], postHeight + 1, c[1]), lamp.defaultBlockState(), 3);
        }
        level.setBlock(base.offset(0, 1, 0), (style == Style.OBSIDIAN ? Blocks.OBSIDIAN : stone).defaultBlockState(), 3);
        level.setBlock(base.offset(0, 2, 0), accent.defaultBlockState(), 3);
        level.setBlock(base.offset(0, 3, 0), Blocks.WHITE_WOOL.defaultBlockState(), 3);
        // the realm styles stand taller than the plain ruin, so they read as landmarks from the RTS camera: a
        // nether-brick cap over the crown, or the End's obsidian pillar with a light on top
        if (cap != null) {
            int top = style == Style.OBSIDIAN ? 6 : 4;
            for (int up = 4; up <= top; up++)
                level.setBlock(base.offset(0, up, 0), cap.defaultBlockState(), 3);
            level.setBlock(base.offset(0, top + 1, 0), lamp.defaultBlockState(), 3);
        }

        Entity marker = EntityType.MARKER.create(level);
        if (marker == null)
            return null;
        marker.moveTo(x + 0.5, y + 1, z + 0.5);
        marker.addTag(TAG);
        marker.getPersistentData().putInt(KEY_KIND, kind.ordinal());
        marker.getPersistentData().putString(KEY_OWNER, "");
        if (kind == Kind.GARRISON)
            marker.getPersistentData().putString(KEY_RECRUIT_TYPE, EntityType.getKey(recruitFor(style)).toString());
        level.addFreshEntity(marker);   // onJoin registers it
        return marker;
    }

    /**
     * The unit a garrison of this style raises: the Nether fortress's wither skeleton, the End's enderman (a neutral
     * unit, made to serve any side), an illager vindicator from the plain ruin. All melee T1/T2-weight units, so two
     * of them are a forward squad, not an army (design/maps.md section 3).
     */
    public static EntityType<?> recruitFor(Style style) {
        return switch (style) {
            case NETHER_BRICK -> EntityRegistrar.WITHER_SKELETON_UNIT.get();
            case OBSIDIAN -> EntityRegistrar.ENDERMAN_UNIT.get();
            default -> EntityRegistrar.VINDICATOR_UNIT.get();
        };
    }

    /** Called by the battlefield: a handful of sites on a ring between the bases and the middle. */
    public static void stampFor(ServerLevel level, List<BlockPos> bases, float cx, float cz, float ringR) {
        int n = Math.max(3, bases.size() + 2);
        float r = Math.max(30, ringR * 0.7f);
        double offset = Math.PI / Math.max(1, bases.size()) / 2;   // between the lanes, not on them
        for (int i = 0; i < n; i++) {
            double a = Math.PI * 2 * i / n + offset;
            Kind kind = RANDOM_KINDS[i % RANDOM_KINDS.length];
            place(level, (int) (cx + Math.cos(a) * r), (int) (cz + Math.sin(a) * r), kind);
        }
        ReignOfNether.LOGGER.info("[CapturePoints] placed {} neutral sites at radius {}", n, (int) r);
    }

    // ------------------------------------------------------------------------------------------ lifecycle

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent evt) {
        if (evt.getLevel().isClientSide())
            return;
        Entity e = evt.getEntity();
        // scoreboard tags first: only sites and the units they gave ever touch the persistent data (R2-10)
        java.util.Set<String> tags = e.getTags();
        if (tags.contains(TAG)) {
            if (!points.contains(e))
                points.add(e);
        } else if (tags.contains(RECRUIT_TAG)) {
            if (e.getPersistentData().contains(KEY_RAISED_BY))
                reenlist(e);
            else
                rehire(e);
        } else if (e instanceof com.solegendary.reignofnether.unit.units.neutral.WolfUnit
                && e.getPersistentData().contains(KEY_HIRED)) {
            rehire(e);   // a wolf hired before RECRUIT_TAG existed (older saves)
        }
    }

    /** A garrison recruit (re)loaded from disk counts against its site's cap again. Public for the game test. */
    public static void reenlist(Entity recruit) {
        String site = recruit.getPersistentData().getString(KEY_RAISED_BY);
        java.util.UUID id;
        try {
            id = java.util.UUID.fromString(site);
        } catch (IllegalArgumentException ex) {
            ReignOfNether.LOGGER.warn("[CapturePoints] recruit {} names no garrison ('{}'); it stays a free unit", recruit, site);
            return;
        }
        List<Entity> list = recruits.computeIfAbsent(id, k -> new ArrayList<>());
        // the same recruit by UUID: an entity object replaced by its reloaded copy is swapped, not counted twice
        list.removeIf(r -> r == recruit || r.getUUID().equals(recruit.getUUID()));
        list.add(recruit);
    }

    /** Living recruits of this garrison that serve {@code owner} (the cap is per holder). Public for the game test. */
    public static int recruitsAlive(Entity site, String owner) {
        List<Entity> list = recruits.get(site.getUUID());
        if (list == null)
            return 0;
        list.removeIf(r -> r.isRemoved() || !r.isAlive());
        int n = 0;
        for (Entity r : list)
            if (r instanceof Unit u && owner.equals(u.getOwnerName()))
                n++;
        return n;
    }

    /** The recruits a garrison has raised that still live (any owner): for the game test's cleanup. */
    public static List<Entity> recruitsOf(Entity site) {
        List<Entity> list = recruits.get(site.getUUID());
        return list == null ? List.of() : List.copyOf(list);
    }

    /** A hired wolf (re)loaded from disk counts against its employer's camp cap again. Public for the game test. */
    public static void rehire(Entity wolf) {
        String owner = wolf.getPersistentData().getString(KEY_HIRED);
        if (owner.isEmpty())
            return;
        List<Entity> mine = hired.computeIfAbsent(owner, k -> new ArrayList<>());
        // the same wolf by UUID: an entity object replaced by its reloaded copy is swapped, not counted twice
        mine.removeIf(h -> h == wolf || h.getUUID().equals(wolf.getUUID()));
        mine.add(wolf);
    }

    /** Living hired wolves the camps track for this player (game test). */
    public static int hiredCount(String owner) {
        List<Entity> mine = hired.get(owner);
        if (mine == null)
            return 0;
        int n = 0;
        for (Entity e : mine)
            if (!e.isRemoved() && e.isAlive())
                n++;
        return n;
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent evt) {
        points.clear();
        hired.clear();
        recruits.clear();
        badRecruitWarned.clear();
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent evt) {
        if (evt.phase != TickEvent.Phase.END || evt.getServer() == null)
            return;
        ServerLevel level = evt.getServer().overworld();
        if (level.getGameTime() % 20 != 0)
            return;
        // clients can't see the marker entities, so they get the site list (for the strategic-view flags) every
        // 5 s - also when empty, so a client never keeps flags from a previous map - and right after a change of owner
        if (ownersDirty || progressDirty || level.getGameTime() % SYNC_TICKS == 0) {
            ownersDirty = false;
            progressDirty = false;
            points.removeIf(Entity::isRemoved);
            CapturePointsClientboundPacket.sendAll(points);
        }
        if (points.isEmpty())
            return;
        tick(level, 1f);
    }

    private static final int SYNC_TICKS = 100;
    private static boolean ownersDirty = false;
    private static boolean progressDirty = false;

    /** One step of capture + benefits covering {@code seconds}. Public for the game test. */
    public static void tick(ServerLevel level, float seconds) {
        points.removeIf(Entity::isRemoved);
        for (Entity p : points)
            if (p.level() == level)
                tickPoint(level, p, seconds);
    }

    static void tickPoint(ServerLevel level, Entity p, float seconds) {
        // who is standing on it? one "side" = a player and their allies
        String side = null;
        boolean contested = false;
        double r2 = RADIUS * RADIUS;
        // only the units in the grid cells round the site, not every unit on the map per site
        for (LivingEntity le : com.solegendary.reignofnether.unit.UnitGrid.near(level, p.getX(), p.getZ(), RADIUS, scratch)) {
            if (!(le instanceof Unit u) || !le.isAlive() || le.distanceToSqr(p) > r2)
                continue;
            String o = u.getOwnerName();
            if (o == null || o.isEmpty())
                continue;
            if (side == null)
                side = o;
            else if (!side.equals(o) && !AlliancesServerEvents.isAllied(side, o))
                contested = true;
        }
        String owner = ownerOf(p);
        float step = seconds / CAPTURE_SECONDS;
        if (side != null && !contested) {
            boolean friendly = !owner.isEmpty() && (owner.equals(side) || AlliancesServerEvents.isAllied(owner, side));
            if (!friendly) {
                float prog = progressOf(p);
                if (!owner.isEmpty()) {   // drain the enemy's hold first
                    // remembered as the claimant, so the map can say who is neutralizing it
                    p.getPersistentData().putString(KEY_CLAIMANT, side);
                    prog -= step;
                    if (prog <= 0) {
                        setOwner(level, p, "");
                        prog = 0;
                    }
                } else {
                    String claimant = p.getPersistentData().getString(KEY_CLAIMANT);
                    if (!side.equals(claimant)) {   // a new claimant starts over
                        p.getPersistentData().putString(KEY_CLAIMANT, side);
                        prog = 0;
                    }
                    prog += step;
                    if (prog >= 1) {
                        setOwner(level, p, side);
                        prog = 1;
                    }
                }
                p.getPersistentData().putFloat(KEY_PROGRESS, prog);
                progressDirty = true;   // the map labels show "Capturing x%" live
                level.sendParticles(ParticleTypes.ENCHANT, p.getX(), p.getY() + 2, p.getZ(), 6, 0.6, 0.6, 0.6, 0.2);
            } else if (progressOf(p) < 1) {   // owner standing on a dented hold restores it
                p.getPersistentData().putFloat(KEY_PROGRESS, Math.min(1, progressOf(p) + step));
                progressDirty = true;
            }
        }
        owner = ownerOf(p);
        if (kindOf(p) == Kind.GARRISON) {
            // an enemy of the holder on the site - fighting for it, or draining it alone - holds the muster
            boolean paused = side != null && (contested || !(owner.equals(side) || AlliancesServerEvents.isAllied(owner, side)));
            muster(level, p, owner, paused, seconds);
        }
        if (!owner.isEmpty())
            reward(level, p, owner, seconds);
    }

    /**
     * The Forsaken Garrison's muster (design/maps.md section 3). Neutral: it charges and banks up to GARRISON_BANK
     * recruits for whoever takes it (lovish's "neutral camps grow": ignoring it costs you). Held: one recruit every
     * GARRISON_SECONDS while no enemy stands on it, as long as fewer than GARRISON_MAX of its recruits serve the
     * holder; at the cap the next one waits, ready, for a free place. Paced like production (EconomyPace).
     */
    static void muster(ServerLevel level, Entity p, String owner, boolean paused, float seconds) {
        net.minecraft.nbt.CompoundTag pd = p.getPersistentData();
        float charge = pd.getFloat(KEY_MUSTER);
        if (owner.isEmpty()) {
            int bank = pd.getInt(KEY_BANK);
            if (bank >= GARRISON_BANK)
                return;
            charge += seconds * com.solegendary.reignofnether.resources.EconomyPace.of(null);
            if (charge >= GARRISON_SECONDS) {
                bank++;
                charge = bank >= GARRISON_BANK ? 0 : charge - GARRISON_SECONDS;
                pd.putInt(KEY_BANK, bank);
                progressDirty = true;   // the label's "n waiting"
            }
            pd.putFloat(KEY_MUSTER, charge);
            return;
        }
        if (paused)
            return;
        charge = Math.min(GARRISON_SECONDS, charge + seconds * com.solegendary.reignofnether.resources.EconomyPace.of(owner));
        if (charge >= GARRISON_SECONDS && recruitsAlive(p, owner) < GARRISON_MAX && raise(level, p, owner) != null)
            charge = 0;
        pd.putFloat(KEY_MUSTER, charge);
    }

    /** Around the ruin, just off its plinth: where recruits form up, in turn. */
    private static final int[][] RAISE_OFFSETS = { { 3, 0 }, { -3, 0 }, { 0, 3 }, { 0, -3 } };

    /**
     * Raises one recruit of the garrison's own type beside the ruin for {@code owner}. Null - logged once per site -
     * when the type the marker names is not a unit (a renamed entity in an old save).
     */
    static Entity raise(ServerLevel level, Entity site, String owner) {
        String type = site.getPersistentData().getString(KEY_RECRUIT_TYPE);
        EntityType<?> recruitType = EntityType.byString(type).orElse(null);
        Entity e = recruitType == null ? null : recruitType.create(level);
        if (!(e instanceof Unit u)) {
            if (badRecruitWarned.add(site.getUUID()))
                ReignOfNether.LOGGER.warn("[CapturePoints] the garrison at {} cannot raise '{}': not a unit type",
                    site.blockPosition(), type);
            return null;
        }
        List<Entity> mine = recruits.computeIfAbsent(site.getUUID(), k -> new ArrayList<>());
        int[] off = RAISE_OFFSETS[mine.size() % RAISE_OFFSETS.length];
        int x = site.getBlockX() + off[0], z = site.getBlockZ() + off[1];
        e.moveTo(x + 0.5, MetalPatches.solidTop(level, x, z) + 1, z + 0.5, level.getRandom().nextFloat() * 360f, 0);
        u.setOwnerName(owner);
        e.addTag(RECRUIT_TAG);
        e.getPersistentData().putString(KEY_RAISED_BY, site.getUUID().toString());
        // listed before it joins: the join event (onJoin -> reenlist) then finds it by identity instead of adding a
        // second entry that would count one recruit twice against the cap
        mine.add(e);
        if (!level.addFreshEntity(e)) {
            mine.remove(e);
            ReignOfNether.LOGGER.warn("[CapturePoints] the garrison at {} could not place its recruit", site.blockPosition());
            return null;
        }
        level.sendParticles(ParticleTypes.SOUL, e.getX(), e.getY() + 1, e.getZ(), 12, 0.4, 0.8, 0.4, 0.02);
        level.playSound(null, site.blockPosition(), SoundEvents.EVOKER_PREPARE_SUMMON, SoundSource.NEUTRAL, 1.5f, 0.8f);
        progressDirty = true;
        return e;
    }

    static void setOwner(ServerLevel level, Entity p, String owner) {
        p.getPersistentData().putString(KEY_OWNER, owner);
        ownersDirty = true;   // resync clients on the next second
        if (kindOf(p) == Kind.GARRISON) {
            // a new holder (or neutral) musters from scratch; a capture releases what the garrison banked
            p.getPersistentData().putFloat(KEY_MUSTER, 0);
            if (!owner.isEmpty()) {
                int bank = p.getPersistentData().getInt(KEY_BANK);
                p.getPersistentData().putInt(KEY_BANK, 0);
                for (int i = 0; i < bank && recruitsAlive(p, owner) < GARRISON_MAX; i++)
                    raise(level, p, owner);
            }
        }
        BlockPos crown = BlockPos.containing(p.getX(), p.getY() + 2, p.getZ());
        level.setBlock(crown, woolFor(owner), 3);
        level.playSound(null, crown, owner.isEmpty() ? SoundEvents.BEACON_DEACTIVATE : SoundEvents.BEACON_ACTIVATE,
            SoundSource.NEUTRAL, 2f, 1f);
        if (!owner.isEmpty())
            PlayerServerEvents.sendMessageToAllPlayers("server.reignofnether.capture_taken", false, owner,
                net.minecraft.network.chat.Component.translatable("capture.reignofnether." + kindOf(p).name().toLowerCase()));
        // hero slice 2: taking a site pays the taker's team heroes standing near it (hero/HeroProgression)
        if (!owner.isEmpty())
            com.solegendary.reignofnether.hero.HeroProgression.onCapture(level, p, owner);
    }

    static BlockState woolFor(String owner) {
        if (owner.isEmpty())
            return Blocks.WHITE_WOOL.defaultBlockState();
        for (RTSPlayer rp : PlayerServerEvents.rtsPlayers)
            if (rp.name.equals(owner)) {
                PlayerPalette.Entry e = PlayerPalette.byMapColorId(rp.startPosColorId);
                DyeColor dye = e == null ? DyeColor.RED : DyeColor.byName(e.name(), DyeColor.RED);
                Block wool = switch (dye) {
                    case ORANGE -> Blocks.ORANGE_WOOL; case MAGENTA -> Blocks.MAGENTA_WOOL; case LIGHT_BLUE -> Blocks.LIGHT_BLUE_WOOL;
                    case YELLOW -> Blocks.YELLOW_WOOL; case LIME -> Blocks.LIME_WOOL; case PINK -> Blocks.PINK_WOOL;
                    case GRAY -> Blocks.GRAY_WOOL; case LIGHT_GRAY -> Blocks.LIGHT_GRAY_WOOL; case CYAN -> Blocks.CYAN_WOOL;
                    case PURPLE -> Blocks.PURPLE_WOOL; case BLUE -> Blocks.BLUE_WOOL; case BROWN -> Blocks.BROWN_WOOL;
                    case GREEN -> Blocks.GREEN_WOOL; case BLACK -> Blocks.BLACK_WOOL; case WHITE -> Blocks.WHITE_WOOL;
                    default -> Blocks.RED_WOOL;
                };
                return wool.defaultBlockState();
            }
        return Blocks.RED_WOOL.defaultBlockState();
    }

    static void reward(ServerLevel level, Entity p, String owner, float seconds) {
        // capture-site income runs at the match's economy pace like every other income (EconomyPace); the camp's
        // hire timer is not income and keeps real time
        float paced = seconds * com.solegendary.reignofnether.resources.EconomyPace.of(owner);
        // hero slice 2: holding a site trickles XP to the owner's team heroes near it (hero/HeroProgression)
        com.solegendary.reignofnether.hero.HeroProgression.onHold(level, p, owner, seconds);
        switch (kindOf(p)) {
            case MINE -> EconomyServerEvents.addReclaimedMetal(owner, MINE_METAL * paced);
            case SHRINE -> {
                var eco = EconomyServerEvents.getEconomy(owner);
                for (Resources res : ResourcesServerEvents.resourcesList)
                    if (res.ownerName.equals(owner) && res.getEnergy() < eco.energyStorage)
                        res.addEnergy(Math.min(SHRINE_ENERGY * paced, eco.energyStorage - res.getEnergy()));
            }
            case VENT -> {   // geothermal: the shrine's payout, stronger, on a lava vent
                var eco = EconomyServerEvents.getEconomy(owner);
                for (Resources res : ResourcesServerEvents.resourcesList)
                    if (res.ownerName.equals(owner) && res.getEnergy() < eco.energyStorage)
                        res.addEnergy(Math.min(VENT_ENERGY * paced, eco.energyStorage - res.getEnergy()));
            }
            case OASIS -> {   // a forward repair point: the owner's (and allies') hurt units nearby mend
                double r2 = OASIS_RADIUS * OASIS_RADIUS;
                for (LivingEntity le : com.solegendary.reignofnether.unit.UnitGrid.near(level, p.getX(), p.getZ(), OASIS_RADIUS, scratch)) {
                    if (!(le instanceof Unit u) || !le.isAlive() || le.distanceToSqr(p) > r2 || le.getHealth() >= le.getMaxHealth())
                        continue;
                    String o = u.getOwnerName();
                    if (o != null && (o.equals(owner) || AlliancesServerEvents.isAllied(o, owner)))
                        le.heal(OASIS_HEAL * seconds);
                }
            }
            case CAMP -> {
                int t = p.getPersistentData().getInt(KEY_CAMP_TIMER) + (int) seconds;

                if (t >= CAMP_INTERVAL_SECONDS) {
                    t = 0;
                    List<Entity> mine = hired.computeIfAbsent(owner, k -> new ArrayList<>());
                    mine.removeIf(e -> e.isRemoved() || !e.isAlive());
                    if (mine.size() < CAMP_MAX) {
                        Entity wolf = UnitServerEvents.spawnMob(EntityRegistrar.WOLF_UNIT.get(), level,
                            BlockPos.containing(p.getX(), p.getY() - 1, p.getZ() + 3), owner);
                        if (wolf != null) {
                            wolf.getPersistentData().putString(KEY_HIRED, owner);
                            wolf.addTag(RECRUIT_TAG);   // saved with the wolf: onJoin knows it without its ForgeData
                            mine.add(wolf);
                        }
                    }
                }
                p.getPersistentData().putInt(KEY_CAMP_TIMER, t);
            }
        }
    }
}
