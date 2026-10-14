package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.api.ReignOfNetherRegistries;
import com.solegendary.reignofnether.building.production.ProductionItems;
import com.solegendary.reignofnether.faction.FactionTraits;
import com.solegendary.reignofnether.faction.Factions;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.research.ResearchServerEvents;
import com.solegendary.reignofnether.unit.UpgradeVisuals;
import com.solegendary.reignofnether.unit.packets.UnitBatchSyncClientboundPacket;
import com.solegendary.reignofnether.unit.packets.UnitBatchSyncClientboundPacket.Entry;
import com.solegendary.reignofnether.unit.packets.UnitBatchSyncClientboundPacket.Field;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * unit/UpgradeVisuals: the one-byte mask of researched looks a unit carries. The registry -> mask computation is
 * pure, so it runs against a fake research set; the real research hook is checked on a spawned unit; and the batched
 * unit sync carries the byte through its codec at one byte per unit.
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class UpgradeVisualsGameTests {

    static final String ARENA = SkirmishGameTests.ARENA;

    private static ResourceLocation key(String path) {
        return ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, path);
    }

    /** Registry -> mask: each unit gets exactly the bits of the research that applies to it, and only when researched. */
    @GameTest(template = ARENA)
    public static void upgrade_mask_follows_the_registry(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        List<String> problems = new ArrayList<>();
        // the registry itself: every row names a real research item and a single bit of one byte, and the three
        // kinds each own at least one bit, none shared
        for (UpgradeVisuals.Entry e : UpgradeVisuals.ENTRIES) {
            if (!ReignOfNetherRegistries.PRODUCTION_ITEM.containsKey(e.research()))
                problems.add(e.note() + ": research " + e.research() + " is not a registered production item");
            if (Integer.bitCount(e.bit()) != 1 || e.bit() > 0xFF)
                problems.add(e.note() + ": bit " + e.bit() + " is not one bit of a byte");
            if ((UpgradeVisuals.kindMask(e.kind()) & e.bit()) == 0)
                problems.add(e.note() + ": its bit is not in its kind's mask");
        }
        for (UpgradeVisuals.Kind k : UpgradeVisuals.Kind.values()) {
            if (UpgradeVisuals.kindMask(k) == 0)
                problems.add("kind " + k + " has no registered bit (the proof wiring should cover all three)");
            for (UpgradeVisuals.Kind o : UpgradeVisuals.Kind.values())
                if (o != k && (UpgradeVisuals.kindMask(k) & UpgradeVisuals.kindMask(o)) != 0)
                    problems.add("kinds " + k + " and " + o + " share a bit");
        }
        if (!UpgradeVisuals.has(UpgradeVisuals.BIT_GLOW, UpgradeVisuals.Kind.GLOW)
                || UpgradeVisuals.has(UpgradeVisuals.BIT_GLOW, UpgradeVisuals.Kind.TRIM)
                || UpgradeVisuals.has(0, UpgradeVisuals.Kind.GLOW))
            problems.add("has(mask, kind) does not read the kind's bits");

        LivingEntity pillager = EntityRegistrar.PILLAGER_UNIT.get().create(level);
        LivingEntity brute = EntityRegistrar.BRUTE_UNIT.get().create(level);
        LivingEntity golem = EntityRegistrar.IRON_GOLEM_UNIT.get().create(level);
        if (pillager == null || brute == null || golem == null) {
            helper.fail("could not create the test units");
            return;
        }
        Set<ResourceLocation> done = new HashSet<>();
        if (UpgradeVisuals.compute(pillager, done::contains) != 0 || UpgradeVisuals.compute(brute, done::contains) != 0
                || UpgradeVisuals.compute(golem, done::contains) != 0)
            problems.add("a unit shows an upgrade with nothing researched");
        done.add(key("pillager_crossbows"));
        if (UpgradeVisuals.compute(pillager, done::contains) != UpgradeVisuals.BIT_GLOW)
            problems.add("Multishot Crossbows should light the Pillager's glow bit, got " + UpgradeVisuals.compute(pillager, done::contains));
        if (UpgradeVisuals.compute(brute, done::contains) != 0 || UpgradeVisuals.compute(golem, done::contains) != 0)
            problems.add("Multishot Crossbows leaked onto a Brute or a Golem");
        done.add(key("brute_shields"));
        done.add(key("golem_smithing"));
        if (UpgradeVisuals.compute(brute, done::contains) != UpgradeVisuals.BIT_TRIM)
            problems.add("Shield Tactics should set the Brute's trim bit, got " + UpgradeVisuals.compute(brute, done::contains));
        if (UpgradeVisuals.compute(golem, done::contains) != UpgradeVisuals.BIT_SHAPE)
            problems.add("Golem Smithing should set the Golem's shape bit, got " + UpgradeVisuals.compute(golem, done::contains));
        if (UpgradeVisuals.compute(pillager, done::contains) != UpgradeVisuals.BIT_GLOW)
            problems.add("the Pillager's mask changed with research that is not its own");
        // the faction glow colours the layers and the icon notch use: every live faction has one, all different
        Set<Integer> seen = new HashSet<>();
        for (var f : List.of(Factions.VILLAGERS, Factions.MONSTERS, Factions.PIGLINS, Factions.VERDANT_COURT,
                Factions.TIDEWROUGHT, Factions.STORMCALLERS, Factions.BRASSWORKS)) {
            int glow = FactionTraits.of(f).upgradeGlow;
            if (glow == 0 || !seen.add(glow))
                problems.add(f.getName() + " has upgrade glow " + Integer.toHexString(glow) + (glow != 0 ? " (shared)" : ""));
        }
        if (FactionTraits.NEUTRAL.upgradeGlow != 0)
            problems.add("neutral should have no glow colour");
        if (!problems.isEmpty())
            helper.fail(String.join("; ", problems));
        else
            helper.succeed();
    }

    /** The real hook: a spawned unit's stored mask follows its owner's research as it is added and removed. */
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void upgrade_mask_follows_real_research_on_a_spawned_unit(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = "gametest_upgrade_vis";
        var pillager = EntityRegistrar.PILLAGER_UNIT.get().create(level);
        if (pillager == null) {
            helper.fail("could not create a pillager unit");
            return;
        }
        BlockPos at = helper.absolutePos(new BlockPos(2, 2, 2));
        pillager.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
        pillager.setOwnerName(owner);
        ResearchServerEvents.removeAllResearchFor(owner);
        level.addFreshEntity(pillager);
        helper.runAfterDelay(2, () -> {
            List<String> problems = new ArrayList<>();
            try {
                if (pillager.getUpgradeMask() != 0)
                    problems.add("fresh unit with no research has mask " + pillager.getUpgradeMask());
                ResearchServerEvents.addResearch(owner, ProductionItems.RESEARCH_PILLAGER_CROSSBOWS);
                if (pillager.getUpgradeMask() != UpgradeVisuals.BIT_GLOW)
                    problems.add("after Multishot Crossbows the mask is " + pillager.getUpgradeMask() + ", expected " + UpgradeVisuals.BIT_GLOW);
                ResearchServerEvents.addResearch(owner, ProductionItems.RESEARCH_BRUTE_SHIELDS);
                if (pillager.getUpgradeMask() != UpgradeVisuals.BIT_GLOW)
                    problems.add("Shield Tactics changed a Pillager's mask to " + pillager.getUpgradeMask());
                ResearchServerEvents.removeResearch(owner, key("pillager_crossbows"));
                if (pillager.getUpgradeMask() != 0)
                    problems.add("after the research was removed the mask is still " + pillager.getUpgradeMask());
                // and the batcher's capture path recomputes rather than trusting the stored byte
                pillager.setUpgradeMask(0xFF);
                if (UpgradeVisuals.refresh(pillager) != 0 || pillager.getUpgradeMask() != 0)
                    problems.add("refresh did not recompute a stale stored mask");
            } finally {
                ResearchServerEvents.removeAllResearchFor(owner);
                pillager.discard();
            }
            if (!problems.isEmpty())
                helper.fail(String.join("; ", problems));
            else
                helper.succeed();
        });
    }

    /** The batched unit sync carries the byte: present only when the field is set, one byte of payload, 0..255. */
    @GameTest(template = ARENA)
    public static void batch_sync_carries_the_upgrade_byte(GameTestHelper helper) {
        List<Entry> sent = new ArrayList<>();
        Entry only = new Entry(7);
        only.mask = Field.UPGRADES.bit;
        only.upgrades = 0b101;
        sent.add(only);
        Entry withStats = new Entry(8);
        withStats.mask = Field.STATS.bit | Field.UPGRADES.bit;
        withStats.health = 12f;
        withStats.ownerName = "lovish";
        withStats.upgrades = 0xFF;   // the top bit must survive as unsigned
        sent.add(withStats);
        Entry none = new Entry(9);
        none.mask = Field.ANCHOR.bit;
        none.upgrades = 0b11;   // not flagged: must not travel
        sent.add(none);

        var buf = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        try {
            new UnitBatchSyncClientboundPacket(List.of(only)).encode(buf);
            // count (1) + id (1) + field mask (1: bit 6 fits a varint byte) + the upgrade byte (1)
            if (buf.readableBytes() != 4) {
                helper.fail("an UPGRADES-only entry should cost one byte of payload: packet is " + buf.readableBytes() + " bytes, expected 4");
                return;
            }
            buf.clear();
            new UnitBatchSyncClientboundPacket(sent).encode(buf);
            var got = new UnitBatchSyncClientboundPacket(buf).getEntries();
            if (got.size() != 3) {
                helper.fail("decoded " + got.size() + " entries, sent 3");
                return;
            }
            Entry a = got.get(0), b = got.get(1), c = got.get(2);
            if (a.id != 7 || !a.has(Field.UPGRADES) || a.upgrades != 0b101 || a.has(Field.STATS)) {
                helper.fail("entry 7 came back as mask " + a.mask + " upgrades " + a.upgrades);
                return;
            }
            if (b.id != 8 || !b.has(Field.UPGRADES) || b.upgrades != 0xFF || !b.has(Field.STATS) || b.health != 12f
                    || !"lovish".equals(b.ownerName)) {
                helper.fail("entry 8 came back as mask " + b.mask + " upgrades " + b.upgrades + " health " + b.health);
                return;
            }
            if (c.id != 9 || c.has(Field.UPGRADES) || c.upgrades != 0 || !c.has(Field.ANCHOR) || c.anchor != null) {
                helper.fail("entry 9 came back as mask " + c.mask + " upgrades " + c.upgrades);
                return;
            }
            if (buf.readableBytes() != 0) {
                helper.fail(buf.readableBytes() + " bytes left after decoding");
                return;
            }
        } finally {
            buf.release();
        }
        helper.succeed();
    }
}
