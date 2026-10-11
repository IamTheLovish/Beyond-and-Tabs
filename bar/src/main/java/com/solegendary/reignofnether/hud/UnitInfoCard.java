package com.solegendary.reignofnether.hud;

import com.solegendary.reignofnether.faction.FactionTraits;
import com.solegendary.reignofnether.faction.Factions;
import com.solegendary.reignofnether.hud.buttons.AbilityButton;
import com.solegendary.reignofnether.hud.buttons.Button;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.unit.UnitKills;
import com.solegendary.reignofnether.unit.VeterancyServerEvents;
import com.solegendary.reignofnether.unit.interfaces.AttackerUnit;
import com.solegendary.reignofnether.unit.interfaces.HeroUnit;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.interfaces.WorkerUnit;
import com.solegendary.reignofnether.util.MyRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * BAR-style unit info card (HUD pass 2). Two shapes:
 * <ul>
 *   <li>{@link #renderCompact}: drawn next to Reign of Nether's portrait + stats panel when exactly one unit is
 *   selected. The portrait already shows the name, health bar and damage/range/speed, so the card only adds what RoN
 *   lacks: the role line, veterancy stars, metal/energy cost and every ability with its live cooldown.</li>
 *   <li>{@link #renderFull}: the whole card (name, role, health bar with numbers, cost, damage/range/speed, stars,
 *   abilities) for a unit that is only hovered while nothing is selected - there is no portrait then.</li>
 * </ul>
 * Plus {@link #renderGroupSummary}, the one-line total-cost / total-HP strip over RoN's multi-selection grid.
 * Everything is plain 1x font and fill() so it stays crisp at GUI scale 2 and 3; nothing allocates per frame
 * except the ability buttons RoN itself rebuilds every frame.
 */
public final class UnitInfoCard {
    private UnitInfoCard() { }

    private static final Minecraft MC = Minecraft.getInstance();

    public static final int COMPACT_W = 124;   // was 112: four-word ability names were cut off (lovish's clip)
    public static final int FULL_W = 128;
    static final int BG = 0xD0101216, EDGE = 0xFF3A3F46;
    static final int STAR_ON = 0xFFFFD040, STAR_OFF = 0xFF404448;
    static final int KILLS_COLOUR = 0xFFFF9A7A;   // kills: the warm \u2715 line (BAR's kill counter)
    static final int MAX_ABILITY_ROWS = 4;

    public enum Role {
        RAIDER, SKIRMISHER, TANK, ARTILLERY, SUPPORT, WORKER, EXPERIMENTAL, HERO;
        public String label() { return I18n.get("hud.reignofnether.role." + name().toLowerCase(java.util.Locale.ROOT)); }
    }

    /**
     * Roles the stat heuristic would get wrong (a 250 HP ravager is a tank, not an experimental; casters are support
     * even though they can attack). Keyed by class simple name so a unit added later just falls back to the heuristic.
     */
    private static final Map<String, Role> ROLE_TABLE = Map.ofEntries(
        Map.entry("WardenUnit", Role.EXPERIMENTAL), Map.entry("BoneDragonUnit", Role.EXPERIMENTAL),
        Map.entry("WarMammothUnit", Role.EXPERIMENTAL), Map.entry("SunColossusUnit", Role.EXPERIMENTAL),
        Map.entry("WorldTreeWalkerUnit", Role.EXPERIMENTAL), Map.entry("LeviathanUnit", Role.EXPERIMENTAL),
        Map.entry("GhastUnit", Role.ARTILLERY), Map.entry("WildfireUnit", Role.ARTILLERY),
        Map.entry("WitchUnit", Role.SUPPORT), Map.entry("EvokerUnit", Role.SUPPORT),
        Map.entry("EnchanterUnit", Role.SUPPORT), Map.entry("NecromancerUnit", Role.SUPPORT),
        Map.entry("EmbalmerUnit", Role.SUPPORT), Map.entry("WindcallerUnit", Role.SUPPORT),
        Map.entry("PiglinMerchantUnit", Role.SUPPORT),
        Map.entry("RavagerUnit", Role.TANK), Map.entry("IronGolemUnit", Role.TANK), Map.entry("BruteUnit", Role.TANK),
        Map.entry("ArmouredHoglinUnit", Role.TANK), Map.entry("RoyalGuardUnit", Role.TANK),
        Map.entry("PolarBearUnit", Role.TANK), Map.entry("GrizzlyBearUnit", Role.TANK),
        Map.entry("ScoutDogUnit", Role.RAIDER), Map.entry("ScoutCatUnit", Role.RAIDER), Map.entry("SpiderUnit", Role.RAIDER),
        Map.entry("WolfUnit", Role.RAIDER), Map.entry("HoglinUnit", Role.RAIDER), Map.entry("ZoglinUnit", Role.RAIDER),
        Map.entry("BatUnit", Role.RAIDER),
        // Verdant Court: the Treant is a 140 HP tank by design, the Leafblade (50 HP) and the Fox Courier raiders;
        // the Thornbow is a skirmisher by the ranged rule and the Seedshaper a worker
        Map.entry("SentinelTreantUnit", Role.TANK), Map.entry("LeafbladeUnit", Role.RAIDER),
        Map.entry("FoxCourierUnit", Role.RAIDER),
        // Verdant Court T2: the Elder Treant is a 520 HP tank (not an experimental), the Stag Lancer a raider, the
        // Shade Ranger a long-range single-target artillery piece; the Elder Druid falls to the worker rule
        Map.entry("ElderTreantUnit", Role.TANK), Map.entry("StagLancerUnit", Role.RAIDER),
        Map.entry("ShadeRangerUnit", Role.ARTILLERY),
        // the Wisp Choir is a ranged skirmisher (its anti-air bonus is on the damage line), the Bloom Priestess a healer
        Map.entry("WispChoirUnit", Role.SKIRMISHER), Map.entry("BloomPriestessUnit", Role.SUPPORT),
        // Tidewrought: the Reef Guard (80 HP, shield) holds the line, the Bombard Crew is splash artillery, the Tide
        // Priest a non-fighting support, the Cutlass Raider and the Gull Spotter raiders; the Shipwright is a worker
        Map.entry("ReefGuardUnit", Role.TANK), Map.entry("BombardCrewUnit", Role.ARTILLERY),
        Map.entry("TidePriestUnit", Role.SUPPORT), Map.entry("CutlassRaiderUnit", Role.RAIDER),
        Map.entry("GullSpotterUnit", Role.RAIDER),
        // slice 2: the Harpooner and the Flak Swivel are ranged skirmishers (the pull and the anti-air bonus are on the
        // damage line), the Mudskipper Sloop a raider
        Map.entry("HarpoonerUnit", Role.SKIRMISHER), Map.entry("FlakSwivelUnit", Role.SKIRMISHER),
        Map.entry("MudskipperSloopUnit", Role.RAIDER),
        // slice 3 (Drydock): the Kraken Caller is a controller (its grab, not its lash), the Mortar Galley and the Sky
        // Corsair are splash artillery; the Harbourmaster falls to the worker rule
        Map.entry("KrakenCallerUnit", Role.SUPPORT), Map.entry("MortarGalleyUnit", Role.ARTILLERY),
        Map.entry("SkyCorsairUnit", Role.ARTILLERY),
        // Stormcallers: the Jade Guard holds the line (its Mirror answers archers), the Blade Dancer and the Paper Crane
        // are raiders, the Fire Adept and the Wind Monk ranged skirmishers; the Geomancer falls to the worker rule
        Map.entry("JadeGuardUnit", Role.TANK), Map.entry("BladeDancerUnit", Role.RAIDER),
        Map.entry("PaperCraneUnit", Role.RAIDER), Map.entry("FireAdeptUnit", Role.SKIRMISHER),
        Map.entry("WindMonkUnit", Role.SKIRMISHER),
        // slice 2: the Ink Scribe is a controller (its Seal), the Frost Adept a skirmisher, the Rocket Battery artillery
        Map.entry("InkScribeUnit", Role.SUPPORT), Map.entry("FrostAdeptUnit", Role.SKIRMISHER),
        Map.entry("RocketBatteryUnit", Role.ARTILLERY),
        // slice 3 (Observatory): the Thunder Dragon is a ranged (chain) skirmisher, the Earthshaker holds the line, the
        // Gravity Monk and the Mirror Mage are controllers; the Ley Master falls to the worker rule
        Map.entry("ThunderDragonUnit", Role.SKIRMISHER), Map.entry("EarthshakerUnit", Role.TANK),
        Map.entry("GravityMonkUnit", Role.SUPPORT), Map.entry("MirrorMageUnit", Role.SUPPORT),
        // slice 4: the Seer watches, the Lantern Fortress shields (both support), the Phoenix dives (a raider), the
        // Celestial Dragon is the faction's experimental
        Map.entry("SeerUnit", Role.SUPPORT), Map.entry("LanternFortressUnit", Role.SUPPORT),
        Map.entry("PhoenixUnit", Role.RAIDER), Map.entry("CelestialDragonUnit", Role.EXPERIMENTAL)
    );
    // role and faction colour only depend on the unit's class, so work them out once per class
    private static final Map<Class<?>, Role> ROLE_CACHE = new HashMap<>();
    private static final Map<Class<?>, Integer> FACTION_COLOUR_CACHE = new HashMap<>();

    public static Role getRole(LivingEntity le) {
        Role r = ROLE_CACHE.get(le.getClass());
        if (r == null) {
            r = computeRole(le);
            ROLE_CACHE.put(le.getClass(), r);
        }
        return r;
    }

    static Role computeRole(LivingEntity le) {
        if (le instanceof WorkerUnit)
            return Role.WORKER;
        if (le instanceof HeroUnit)
            return Role.HERO;
        Role table = ROLE_TABLE.get(le.getClass().getSimpleName());
        if (table != null)
            return table;
        if (!(le instanceof AttackerUnit au))
            return Role.SUPPORT;
        float range = au.getAttackRange();
        if (range >= 20)
            return Role.ARTILLERY;
        if (range >= 6)
            return Role.SKIRMISHER;
        // melee: base health (not the current, veterancy-boosted value) decides front line vs. fast striker
        return le.getAttributeBaseValue(Attributes.MAX_HEALTH) >= 60 ? Role.TANK : Role.RAIDER;
    }

    /** Villagers blue, monsters green, piglins orange - the role line's colour. */
    public static int getFactionColour(LivingEntity le) {
        Integer c = FACTION_COLOUR_CACHE.get(le.getClass());
        if (c == null) {
            // FactionTraits.accentArgb; non-units and factions without an entry get the neutral grey
            c = le instanceof Unit unit ? FactionTraits.of(Factions.getFaction(unit)).accentArgb
                : FactionTraits.NEUTRAL.accentArgb;
            FACTION_COLOUR_CACHE.put(le.getClass(), c);
        }
        return c;
    }

    // ------------------------------------------------------------------ cards

    /** Card for the single selected unit, beside the portrait. @return its zone (for click-blocking) */
    public static RectZone renderCompact(GuiGraphics gg, int x, int y, int h, LivingEntity le, int mouseX, int mouseY) {
        Font font = MC.font;
        int w = COMPACT_W;
        frame(gg, x, y, w, h);
        int cy = y + 4;
        roleLine(gg, font, x + 4, cy, w - 8, le);
        cy += 11;
        if (le instanceof Unit unit) {
            costLine(gg, font, x + 4, cy, unit);
            cy += 11;
            abilities(gg, font, x + 4, cy, w - 8, y + h - 2, unit, mouseX, mouseY);
        }
        return RectZone.getZoneByLW(x, y, w, h);
    }

    /** The whole card, for a hovered unit when nothing is selected. */
    public static void renderFull(GuiGraphics gg, int x, int y, int h, LivingEntity le, int mouseX, int mouseY) {
        Font font = MC.font;
        int w = FULL_W;
        frame(gg, x, y, w, h);
        int cy = y + 4;
        String name = HudClientEvents.getModifiedEntityName(le).replace("_", " ");
        if (le.hasCustomName() && le.getCustomName() != null)
            name = le.getCustomName().getString();
        if (!name.isEmpty())
            name = Character.toUpperCase(name.charAt(0)) + name.substring(1);
        if (le instanceof Unit u && u.getOwnerName() != null && !u.getOwnerName().isEmpty()
                && MC.player != null && !u.getOwnerName().equals(MC.player.getName().getString()))
            name += " (" + u.getOwnerName() + ")";
        gg.drawString(font, font.plainSubstrByWidth(name, w - 8), x + 4, cy, 0xFFFFFFFF);
        cy += 11;
        roleLine(gg, font, x + 4, cy, w - 8, le);
        cy += 11;
        healthBar(gg, font, x + 4, cy, w - 8, le);
        cy += 13;
        if (le instanceof Unit unit) {
            costLine(gg, font, x + 4, cy, unit);
            cy += 11;
            statLine(gg, font, x + 4, cy, w - 8, unit);
            cy += 11;
            abilities(gg, font, x + 4, cy, w - 8, y + h - 2, unit, mouseX, mouseY);
        }
    }

    /**
     * "x12 [=====HP 340/400=====] M 1200  E 3400  x7" over the multi-selection grid: count, a summed health bar,
     * summed metal/energy cost and the group's kills (UnitKills). Totals are summed straight off the selection list
     * each frame - a loop over a few hundred entities with no allocation beyond the number strings.
     */
    public static void renderGroupSummary(GuiGraphics gg, int x, int y, int maxW, List<LivingEntity> units) {
        Font font = MC.font;
        float hp = 0, maxHp = 0;
        int metal = 0, energy = 0, kills = 0;
        for (int i = 0; i < units.size(); i++) {
            LivingEntity le = units.get(i);
            hp += le.getHealth();
            maxHp += le.getMaxHealth();
            kills += UnitKills.get(le);
            if (le instanceof Unit u) {
                ResourceCost c = u.getCost();
                if (c != null) {
                    metal += c.metal();
                    energy += c.energy();
                }
            }
        }
        int cx = x;
        int right = x + maxW;
        cx = piece(gg, font, "x" + units.size(), cx, y, right, 0xFFFFFFFF);
        // the summed health bar, sized to what is left after the cost and kill pieces
        String m = I18n.get("hud.reignofnether.unit_card.metal", metal);
        String e = I18n.get("hud.reignofnether.unit_card.energy", energy);
        String k = I18n.get("hud.reignofnether.unit_card.kills", kills);
        int tail = font.width(m) + 7 + font.width(e) + 7 + font.width(k);
        int barW = Math.max(40, Math.min(70, right - cx - tail - 7));
        healthBar(gg, font, cx, y - 1, barW, hp, maxHp);
        cx += barW + 7;
        cx = piece(gg, font, m, cx, y, right, EconomyBarRenderer.METAL_FILL);
        cx = piece(gg, font, e, cx, y, right, EconomyBarRenderer.ENERGY_FILL);
        piece(gg, font, k, cx, y, right, KILLS_COLOUR);
    }

    /** A thin group health bar over a selection-type icon (summed HP of that type). */
    public static void renderGroupBar(GuiGraphics gg, int x, int y, int w, float frac) {
        frac = Math.max(0f, Math.min(1f, frac));
        gg.fill(x, y, x + w, y + 2, 0xFF000000);
        gg.fill(x, y, x + Math.round(w * frac), y + 2, healthColour(frac));
    }

    // ------------------------------------------------------------------ pieces

    private static int piece(GuiGraphics gg, Font font, String s, int x, int y, int right, int colour) {
        int w = font.width(s);
        if (x + w > right)
            return right + 1;   // out of room: drop the rest rather than overflow the panel
        gg.drawString(font, s, x, y, colour);
        return x + w + 7;
    }

    private static void frame(GuiGraphics gg, int x, int y, int w, int h) {
        gg.fill(x, y, x + w, y + h, BG);
        gg.fill(x, y, x + w, y + 1, EDGE);
        gg.fill(x, y + h - 1, x + w, y + h, EDGE);
        gg.fill(x, y, x + 1, y + h, EDGE);
        gg.fill(x + w - 1, y, x + w, y + h, EDGE);
    }

    private static void roleLine(GuiGraphics gg, Font font, int x, int y, int w, LivingEntity le) {
        int starsW = VeterancyServerEvents.MAX_RANK * 7;
        String role = font.plainSubstrByWidth(getRole(le).label(), w - starsW - 2);
        gg.drawString(font, role, x, y, getFactionColour(le));
        // veterancy rank as BAR-style stars, right-aligned on the role line
        int rank = VeterancyServerEvents.getSyncedRank(le);
        int sx = x + w - starsW + 1;
        for (int i = 0; i < VeterancyServerEvents.MAX_RANK; i++)
            star(gg, sx + i * 7, y + 1, i < rank ? STAR_ON : STAR_OFF);
    }

    // a 5x5 pixel star: drawn with fill() so it doesn't depend on the font having a star glyph
    private static void star(GuiGraphics gg, int x, int y, int c) {
        gg.fill(x + 2, y, x + 3, y + 1, c);
        gg.fill(x, y + 1, x + 5, y + 3, c);
        gg.fill(x + 1, y + 3, x + 4, y + 4, c);
        gg.fill(x, y + 4, x + 2, y + 5, c);
        gg.fill(x + 3, y + 4, x + 5, y + 5, c);
    }

    private static void costLine(GuiGraphics gg, Font font, int x, int y, Unit unit) {
        ResourceCost c = unit.getCost();
        int metal = c == null ? 0 : c.metal(), energy = c == null ? 0 : c.energy();
        String m = I18n.get("hud.reignofnether.unit_card.metal", metal);
        gg.drawString(font, m, x, y, EconomyBarRenderer.METAL_FILL);
        String e = I18n.get("hud.reignofnether.unit_card.energy", energy);
        int ex = x + font.width(m) + 8;
        gg.drawString(font, e, ex, y, EconomyBarRenderer.ENERGY_FILL);
        // BAR's kill counter, after the cost (only once the unit has one)
        int kills = UnitKills.get((LivingEntity) unit);
        if (kills > 0)
            gg.drawString(font, I18n.get("hud.reignofnether.unit_card.kills", kills), ex + font.width(e) + 8, y, KILLS_COLOUR);
    }

    private static void statLine(GuiGraphics gg, Font font, int x, int y, int w, Unit unit) {
        int cx = x, right = x + w;
        if (unit instanceof AttackerUnit au) {
            double dmg = au.getUnitAttackDamage();
            if (!(unit instanceof WorkerUnit))
                dmg += AttackerUnit.getWeaponDamageModifier(au);
            cx = piece(gg, font, I18n.get("hud.reignofnether.unit_card.damage", Math.round(dmg)), cx, y, right, 0xFFFF7A6A);
            cx = piece(gg, font, I18n.get("hud.reignofnether.unit_card.range", (int) au.getAttackRange()), cx, y, right, 0xFFE0E0E0);
        }
        LivingEntity le = (LivingEntity) unit;
        if (le.getAttribute(Attributes.MOVEMENT_SPEED) != null) {
            int ms = (int) (unit.getMovementSpeed() * unit.getSpeedModifier() * 101);   // same scale as the stats panel
            piece(gg, font, I18n.get("hud.reignofnether.unit_card.speed", ms), cx, y, right, 0xFF9AD0FF);
        }
    }

    private static void healthBar(GuiGraphics gg, Font font, int x, int y, int w, LivingEntity le) {
        healthBar(gg, font, x, y, w, le.getHealth() + le.getAbsorptionAmount(), le.getMaxHealth());
    }

    private static void healthBar(GuiGraphics gg, Font font, int x, int y, int w, float hp, float maxHp) {
        float max = Math.max(1f, maxHp);
        float frac = Math.min(1f, hp / max);
        gg.fill(x, y, x + w, y + 11, 0xFF000000);
        gg.fill(x + 1, y + 1, x + w - 1, y + 10, 0xFF2A2E33);
        gg.fill(x + 1, y + 1, x + 1 + Math.round((w - 2) * frac), y + 10, healthColour(frac));
        String s = (int) Math.ceil(hp) + " / " + (int) max;
        gg.drawString(font, s, x + w / 2 - font.width(s) / 2, y + 2, 0xFFFFFFFF, true);
    }

    static int healthColour(float frac) {
        if (frac > 0.6f) return 0xFF52C850;
        if (frac > 0.3f) return 0xFFE0C040;
        return 0xFFE04838;
    }

    /** One row per ability: icon, name, and "Ready" or the seconds left (BAR shows cooldowns on the card too). */
    private static void abilities(GuiGraphics gg, Font font, int x, int y, int w, int bottom, Unit unit,
                                  int mouseX, int mouseY) {
        List<Button> buttons = unit.getAbilityButtons();
        int rows = 0;
        for (int i = 0; i < buttons.size() && rows < MAX_ABILITY_ROWS && y + 9 <= bottom; i++) {
            if (!(buttons.get(i) instanceof AbilityButton ab) || ab.ability == null || ab.isHidden.get())
                continue;
            if (ab.iconResource != null)
                MyRenderer.renderIcon(gg, ab.iconResource, x, y, 8);
            String cdStr;
            int cdColour;
            float cd = ab.ability.getCooldown(unit);
            if (ab.ability.cooldownMax <= 0 || ab.ability.isOffCooldown(unit)) {
                cdStr = I18n.get("hud.reignofnether.unit_card.ready");
                cdColour = 0xFF7CE07A;
            } else {
                cdStr = (int) Math.ceil(cd / 20f) + "s";   // cooldowns are kept in ticks
                cdColour = 0xFFFFB050;
            }
            int cdW = font.width(cdStr);
            // the name shrinks to fit the column instead of being cut ("Tentacle G.." in lovish's clip)
            String name = ab.name == null ? "" : ab.name;
            int avail = w - 12 - cdW - 4, nameW = font.width(name);
            if (nameW <= avail) {
                gg.drawString(font, name, x + 11, y, 0xFFDDDDDD);
            } else {
                float sc = Math.max(0.6f, avail / (float) nameW);
                gg.pose().pushPose();
                gg.pose().translate(x + 11, y + (1 - sc) * 4, 0);
                gg.pose().scale(sc, sc, 1f);
                gg.drawString(font, sc * nameW <= avail ? name : font.plainSubstrByWidth(name, (int) (avail / sc)), 0, 0, 0xFFDDDDDD);
                gg.pose().popPose();
            }
            gg.drawString(font, cdStr, x + w - cdW, y, cdColour);
            if (mouseX >= x && mouseX < x + w && mouseY >= y - 1 && mouseY < y + 9 && ab.tooltipLines != null)
                HudClientEvents.deferTooltip(ab.tooltipLines, mouseX, mouseY);
            y += 10;
            rows++;
        }
    }
}
