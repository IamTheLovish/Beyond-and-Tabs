package com.solegendary.reignofnether.unit;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.research.ResearchServerEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.packets.UnitBatchSyncClientboundPacket;
import com.solegendary.reignofnether.unit.units.piglins.BruteUnit;
import com.solegendary.reignofnether.unit.units.villagers.IronGolemUnit;
import com.solegendary.reignofnether.unit.units.villagers.MilitiaUnit;
import com.solegendary.reignofnether.unit.units.villagers.VindicatorUnit;
import com.solegendary.reignofnether.unit.units.villagers.PillagerUnit;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

/**
 * Which completed research a unit shows on its body (lovish's rule: "unit upgrades give visual changes to the units -
 * change shape or have their weapon glow, whatever makes sense for the faction"). Stats are untouched: this is the
 * look only.
 *
 * The server computes a one-byte mask per unit from its owner's research ({@link ResearchServerEvents}) through the
 * {@link #ENTRIES registry} below, stores it on the entity ({@link Unit#getUpgradeMask}) and syncs it as the
 * UPGRADES field of the batched unit sync (one byte per unit, only when it changes). It is recomputed every sync
 * (UnitSyncBatcher.capture - cheap: a handful of entries, and research is a short list), when research completes
 * ({@link #onResearchChanged}) and when a unit spawns, so an owner change or a reloaded save never leave it stale.
 *
 * A bit is a visual slot, not a research: {@link Kind#GLOW} relights the weapon (UpgradeGlowLayer), {@link Kind#TRIM}
 * bolts cubes onto a bone (UpgradeTrimLayer), {@link Kind#SHAPE} shows the parts a model tagged {@code "upgrade"} in
 * the box DSL (design/models/README.md; the new bodies read it). Two research items may share a bit when they apply to
 * different units, which is why the registry is a list of (research, bit, kind, applies-to) rather than one bit per
 * research: eight bits are enough for a while. An entry's bit may not already belong to another kind (checked once,
 * at class init, so the game test catches a bad entry).
 *
 * Server-safe: nothing here touches a client class; the layers live in unit/modelling/layers.
 */
public final class UpgradeVisuals {
    private UpgradeVisuals() {}

    /** Entity persistent-data key of the mask (both sides; the client's copy is what the layers read). */
    public static final String KEY = "bt_upgrades";

    public enum Kind { GLOW, TRIM, SHAPE }

    // the visual slots: one byte, bits 0-2 are the first glow / trim / shape, bits 3-7 are spare (a second tier of the
    // same kind - "GLOW_2" - would take bit 3 and so on; the layers OR every bit of their kind)
    public static final int BIT_GLOW = 1;
    public static final int BIT_TRIM = 1 << 1;
    public static final int BIT_SHAPE = 1 << 2;

    /** One registry row: when the owner has {@code research}, every unit {@code applies} accepts carries {@code bit}. */
    public record Entry(ResourceLocation research, int bit, Kind kind, Predicate<Object> applies, String note) {
        public Entry {
            if (bit <= 0 || bit > 0xFF || Integer.bitCount(bit) != 1)
                throw new IllegalArgumentException("upgrade visual bit must be a single bit of one byte, got " + bit);
        }
    }

    private static ResourceLocation research(String path) {
        return ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, path);
    }

    /**
     * The registry. Proof wiring for the three kinds; per-unit art for the new bodies comes with them.
     * The research keys are the ProductionItems registry names (ProductionItems.register), kept as locations so this
     * class never initialises the production items (whose buttons reference client classes).
     */
    public static final List<Entry> ENTRIES = Collections.unmodifiableList(new ArrayList<>(List.of(
        // Multishot Crossbows: the Pillagers' crossbows glow in the Sunforged gold
        new Entry(research("pillager_crossbows"), BIT_GLOW, Kind.GLOW, u -> u instanceof PillagerUnit,
            "Pillager crossbow glows"),
        // Shield Tactics: the Brutes wear the Horde's bolted plate (UpgradeTrimLayer catalogue)
        new Entry(research("brute_shields"), BIT_TRIM, Kind.TRIM, u -> u instanceof BruteUnit,
            "Brute bolted plate"),
        // Golem Smithing: the shape slot, shown by the parts a golem body tags "upgrade" in the box DSL once the new
        // bodies land (today the vanilla golem renderer has no tagged parts, so only the strategic icon notch shows it)
        new Entry(research("golem_smithing"), BIT_SHAPE, Kind.SHAPE, u -> u instanceof IronGolemUnit,
            "Golem smithed plates (model DSL upgrade tag)"),
        // Diamond Axes: the Halberdier's halberd tempered - the blade gilded, the sun struck on it (its body's
        // "tempered" parts, HalberdierRenderer)
        new Entry(research("vindicator_axes"), BIT_SHAPE, Kind.SHAPE, u -> u != null && u.getClass() == VindicatorUnit.class,
            "Halberdier tempered halberd (model DSL upgrade tag)"),
        // Militia Bows: the Squire Runner carries the bow slung across his back and a quiver at the hip (its body's
        // "bows" parts, SquireRunnerRenderer); the temporary militia's villager body has no tagged parts
        new Entry(research("militia_bows"), BIT_SHAPE, Kind.SHAPE, u -> u instanceof MilitiaUnit,
            "Squire Runner slung bow + quiver (model DSL upgrade tag)")
    )));

    // every bit registered under a kind, so a layer asks "any glow bit?" with one AND
    private static final int[] KIND_MASKS = new int[Kind.values().length];
    static {
        for (Entry e : ENTRIES) {
            for (Kind k : Kind.values())
                if (k != e.kind() && (KIND_MASKS[k.ordinal()] & e.bit()) != 0)
                    throw new IllegalStateException("upgrade visual bit " + e.bit() + " is both " + k + " and " + e.kind());
            KIND_MASKS[e.kind().ordinal()] |= e.bit();
        }
    }

    /** The union of the bits registered for a kind (0 if none). */
    public static int kindMask(Kind kind) {
        return KIND_MASKS[kind.ordinal()];
    }

    /** True if any bit of {@code kind} is set in {@code mask}: what a layer checks before drawing. */
    public static boolean has(int mask, Kind kind) {
        return (mask & KIND_MASKS[kind.ordinal()]) != 0;
    }

    /**
     * The pure part: the mask of {@code unit} given a research oracle. {@code hasResearch} is asked once per entry
     * that applies to the unit. Public for the game test, which feeds it a fake research set.
     */
    public static int compute(Object unit, Predicate<ResourceLocation> hasResearch) {
        int mask = 0;
        for (Entry e : ENTRIES)
            if ((mask & e.bit()) == 0 && e.applies().test(unit) && hasResearch.test(e.research()))
                mask |= e.bit();
        return mask & 0xFF;
    }

    /** The mask of a unit from its owner's research on the server (0 for an unowned unit). */
    public static int compute(Unit unit) {
        String owner = unit.getOwnerName();
        if (owner == null || owner.isEmpty())
            return 0;
        return compute(unit, r -> ResearchServerEvents.playerHasResearch(owner, r));
    }

    /** Recomputes and stores a unit's mask on the server; returns the new mask. */
    public static int refresh(LivingEntity le) {
        if (!(le instanceof Unit unit))
            return 0;
        int mask = compute(unit);
        if (mask != unit.getUpgradeMask()) {
            unit.setUpgradeMask(mask);
            UnitSyncBatcher.invalidate(le.getId(), UnitBatchSyncClientboundPacket.Field.UPGRADES);
        }
        return mask;
    }

    /** Research completed (or was removed) for a player: every unit of theirs re-reads its mask at once. */
    public static void onResearchChanged(String playerName) {
        if (playerName == null || playerName.isEmpty())
            return;
        for (LivingEntity le : UnitServerEvents.getAllUnits())
            if (le instanceof Unit unit && playerName.equals(unit.getOwnerName()))
                refresh(le);
    }
}
