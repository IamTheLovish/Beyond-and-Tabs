package com.solegendary.reignofnether.hero;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.ability.AbilityClientboundPacket;
import com.solegendary.reignofnether.ability.HeroAbility;
import com.solegendary.reignofnether.cursor.CursorClientEvents;
import com.solegendary.reignofnether.hud.buttons.AbilityButton;
import com.solegendary.reignofnether.hud.buttons.Button;
import com.solegendary.reignofnether.keybinds.Keybinding;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.unit.Relationship;
import com.solegendary.reignofnether.unit.UnitAction;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.interfaces.HeroUnit;
import com.solegendary.reignofnether.unit.interfaces.Unit;

import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

import static com.solegendary.reignofnether.unit.UnitClientEvents.sendUnitCommand;
import static com.solegendary.reignofnether.util.MiscUtil.fcs;

/**
 * One of a faction hero's four abilities (design/hero_mode.md section 5), driven by its {@link HeroKit.AbilitySpec}: three
 * ranks bought with boon ability points, abilities 1-3 learnable from level 1/3/5 (rank r needs level 2r-1) and the
 * team-utility fourth from level 6 (then 8 and 10). Each hero slot has one order ({@link UnitAction#HERO_ABILITY_1}..4)
 * so a selection of one hero needs no per-ability action.
 * <p>
 * Casting works the same from the command card, a bot and a possessing player's keys 1-4 (all go through
 * UnitActionItem): a SELF ability fires on the spot; an aimed one hands the hero's shared cast goal
 * ({@link FactionHero#castGoal}) the target, the hero walks into range, winds up for the spec's channel ticks and the
 * effect lands. Cooldown and mana are only spent when the effect actually did something.
 */
public class FactionHeroAbility extends HeroAbility {

    public final HeroKit.AbilitySpec spec;
    public final int slot;

    public static UnitAction actionFor(int slot) {
        return switch (slot) {
            case 0 -> UnitAction.HERO_ABILITY_1;
            case 1 -> UnitAction.HERO_ABILITY_2;
            case 2 -> UnitAction.HERO_ABILITY_3;
            default -> UnitAction.HERO_ABILITY_4;
        };
    }

    public FactionHeroAbility(HeroKit.AbilitySpec spec, int slot) {
        super(3, spec.manaCost, actionFor(slot), spec.cooldownSeconds[0] * ResourceCost.TICKS_PER_SECOND,
            spec.target == HeroKit.Target.SELF ? 0 : spec.range[0], spec.radius, spec.target != HeroKit.Target.SELF);
        this.spec = spec;
        this.slot = slot;
        this.showRangeCircle = spec.target != HeroKit.Target.SELF;
        this.showRadiusCircle = spec.radius > 0;
    }

    @Override
    public int getLevelRequirement(HeroUnit hero) {
        int rank = getRank(hero);
        return slot < 3 ? rank * 2 + 1 : 6 + rank * 2;
    }

    @Override
    public boolean rankUp(HeroUnit hero) {
        if (super.rankUp(hero)) {
            updateStatsForRank(hero);
            return true;
        }
        return false;
    }

    @Override
    public void updateStatsForRank(HeroUnit hero) {
        int i = Math.max(0, Math.min(2, getRank(hero) - 1));
        cooldownMax = spec.cooldownSeconds[i] * ResourceCost.TICKS_PER_SECOND;
        if (spec.target != HeroKit.Target.SELF)
            range = spec.range[i];
    }

    @Override
    public boolean isCasting(Unit unit) {
        return unit instanceof FactionHero fh && fh.heroCore().castGoalAbility == this && fh.castGoal().isCasting();
    }

    // ---- casting (server) ----

    @Override
    public void use(Level level, Unit unitUsing, LivingEntity targetEntity) {
        if (level.isClientSide() || !(unitUsing instanceof FactionHero fh) || getRank(fh) <= 0)
            return;
        Mob mob = (Mob) unitUsing;
        if (spec.target == HeroKit.Target.SELF) {
            castNow(fh, null, mob.position());
            return;
        }
        if (targetEntity == null || !targetEntity.isAlive())
            return;
        if (spec.target == HeroKit.Target.ENEMY && !isEnemy(unitUsing, targetEntity))
            return;
        fh.heroCore().startCast(fh, this, targetEntity, null);
    }

    @Override
    public void use(Level level, Unit unitUsing, BlockPos targetBp) {
        if (level.isClientSide() || !(unitUsing instanceof FactionHero fh) || getRank(fh) <= 0 || targetBp == null)
            return;
        Mob mob = (Mob) unitUsing;
        if (spec.target == HeroKit.Target.SELF) {
            castNow(fh, null, mob.position());
            return;
        }
        if (!level.getWorldBorder().isWithinBounds(targetBp))
            return;
        if (spec.target == HeroKit.Target.ENEMY) {
            // aimed at the ground (a possessed hero's crosshair, a concealed target): the nearest enemy by the spot
            LivingEntity near = HeroFx.nearestEnemy((ServerLevel) level, mob, Vec3.atCenterOf(targetBp), 2.5);
            if (near != null)
                fh.heroCore().startCast(fh, this, near, null);
            return;
        }
        fh.heroCore().startCast(fh, this, null, targetBp);
    }

    static boolean isEnemy(Unit unit, LivingEntity e) {
        return UnitServerEvents.getUnitToEntityRelationship(unit, e) == Relationship.HOSTILE;
    }

    /**
     * Runs the effect now if the ability is ready (rank, cooldown, mana - re-checked, the wind-up may have taken a while)
     * and pays for it when it worked. Public for the bot and the game tests.
     */
    public boolean castNow(FactionHero fh, LivingEntity target, Vec3 at) {
        Mob mob = (Mob) fh;
        if (!(mob.level() instanceof ServerLevel sl))
            return false;
        int rank = getRank(fh);
        if (rank <= 0 || !isOffCooldown(fh) || fh.getMana() < manaCost || !mob.isAlive())
            return false;
        boolean worked;
        try {
            worked = spec.effect.cast(new HeroKit.Cast(sl, mob, spec, rank, target, at));
        } catch (RuntimeException ex) {
            ReignOfNether.LOGGER.error("hero ability {} failed", spec.id, ex);
            worked = false;
        }
        if (worked) {
            AbilityClientboundPacket.sendSetCooldownPacket(mob.getId(), action, cooldownMax);
            if (manaCost > 0)
                fh.setMana(fh.getMana() - manaCost);
        }
        return worked;
    }

    // ---- HUD (client) ----

    public String nameKey() { return "abilities.reignofnether.hero." + spec.id; }

    public ResourceLocation iconRl() {
        return ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "textures/icons/abilities/" + spec.icon + ".png");
    }

    static String num(float v) {
        return v == Math.round(v) ? String.valueOf(Math.round(v)) : String.valueOf(Math.round(v * 100) / 100f);
    }

    @Override
    public AbilityButton getButton(Keybinding hotkey, Unit unit) {
        if (!(unit instanceof HeroUnit hero))
            return null;
        if (slot == 0 && com.solegendary.reignofnether.hero.wager.WagerClient.enabled && unit instanceof Mob mob)
            return wagerButton(hotkey, hero, mob.getId());
        boolean aimed = spec.target != HeroKit.Target.SELF;
        AbilityButton button = new AbilityButton(I18n.get(nameKey()),
            iconRl(),
            hotkey,
            () -> aimed && CursorClientEvents.getLeftClickAction() == action,
            () -> getRank(hero) <= 0,
            () -> true,
            () -> {
                if (aimed)
                    CursorClientEvents.setLeftClickAction(action);
                else
                    sendUnitCommand(action);
            },
            null,
            getTooltipLines(hero),
            this,
            hero
        );
        button.innerIconSizeModifier = 1;
        return button;
    }

    /**
     * Lovish's Wager Book on key 1 while the debug toggle (/wagerbook) is on: the button opens the book for this hero
     * instead of casting the slot's ability (hero-abilities-overhaul Q6: "the book is ability key 1").
     */
    private AbilityButton wagerButton(Keybinding hotkey, HeroUnit hero, int heroId) {
        AbilityButton button = new AbilityButton(I18n.get("abilities.reignofnether.hero.wager"),
            ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "textures/item/wager_book.png"),
            hotkey,
            () -> false,
            () -> false,
            () -> true,
            () -> com.solegendary.reignofnether.hero.wager.WagerClient.open(heroId),
            null,
            List.of(fcs(I18n.get("abilities.reignofnether.hero.wager"), true),
                fcs(I18n.get("abilities.reignofnether.hero.wager.desc1")),
                fcs(I18n.get("abilities.reignofnether.hero.wager.desc2"))),
            this,
            hero
        );
        button.innerIconSizeModifier = 1;
        return button;
    }

    @Override
    public Button getRankUpButton(HeroUnit hero) {
        return super.getRankUpButtonProtected(I18n.get(nameKey()), iconRl(), hero);
    }

    @Override
    public List<FormattedCharSequence> getTooltipLines(HeroUnit hero) {
        List<FormattedCharSequence> lines = new ArrayList<>();
        lines.add(fcs(I18n.get(nameKey()) + " " + rankString(hero), true));
        lines.add(fcs(I18n.get("abilities.reignofnether.hero.stats", num(cooldownMax / 20f),
            spec.target == HeroKit.Target.SELF ? "-" : num(range), manaCost)));
        lines.add(fcs(""));
        lines.add(fcs(I18n.get(nameKey() + ".desc1")));
        lines.add(fcs(I18n.get(nameKey() + ".desc2")));
        int r = getRank(hero);
        if (r > 0)
            lines.add(fcs(I18n.get(nameKey() + ".ranks", num(spec.a[r - 1]), num(spec.b[r - 1]))));
        return lines;
    }

    @Override
    public List<FormattedCharSequence> getRankUpTooltipLines(HeroUnit hero) {
        List<FormattedCharSequence> lines = new ArrayList<>();
        lines.add(fcs(I18n.get(nameKey()), true));
        lines.add(fcs(I18n.get("abilities.reignofnether.level_req", getLevelRequirement(hero)), getLevelReqStyle(hero)));
        lines.add(fcs(""));
        lines.add(fcs(I18n.get(nameKey() + ".desc1")));
        lines.add(fcs(I18n.get(nameKey() + ".desc2")));
        lines.add(fcs(""));
        for (int i = 0; i < 3; i++)
            lines.add(fcs(I18n.get("abilities.reignofnether.hero.rank_line", i + 1,
                I18n.get(nameKey() + ".ranks", num(spec.a[i]), num(spec.b[i])),
                spec.cooldownSeconds[i]), getRank(hero) == i));
        return lines;
    }
}
