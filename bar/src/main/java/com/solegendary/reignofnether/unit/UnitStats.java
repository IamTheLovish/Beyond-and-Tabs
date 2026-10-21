package com.solegendary.reignofnether.unit;

import com.solegendary.reignofnether.hud.TooltipColours;
import com.solegendary.reignofnether.registrars.AttributeRegistrar;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.DefaultAttributes;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The base combat stats of a unit TYPE, read from the attributes the type is registered with (the same
 * {@code createAttributes()} the spawned unit starts from), plus the rule for how a factory button prints them
 * (#38 item 39: BAR's build menu shows a unit's stats before you buy it). No client imports, so the headless game
 * test can build and check the exact lines the tooltip shows; the labels are lang keys, resolved at render time.
 *
 * Scales match the unit card (hud/UnitInfoCard.statLine): damage rounded, range in blocks, speed x101.
 * A stat the unit doesn't have is left out: a worker (attack damage 0) shows no damage or range line.
 */
public record UnitStats(float health, float armour, float damage, float attacksPerSecond, float range, float speed) {

    public enum Kind { HEALTH, ARMOUR, DAMAGE, RANGE, SPEED }

    /** speed attribute -> the number the unit card and stats panel show */
    public static final float SPEED_SCALE = 101f;

    /** The stats of {@code type}, or null when it has no registered attributes. */
    @Nullable
    public static UnitStats of(@Nullable EntityType<?> type) {
        if (type == null)
            return null;
        @SuppressWarnings("unchecked")
        EntityType<? extends net.minecraft.world.entity.LivingEntity> living =
            (EntityType<? extends net.minecraft.world.entity.LivingEntity>) type;
        if (!DefaultAttributes.hasSupplier(living))
            return null;
        AttributeSupplier a = DefaultAttributes.getSupplier(living);
        return new UnitStats(
            base(a, Attributes.MAX_HEALTH),
            base(a, Attributes.ARMOR),
            base(a, AttributeRegistrar.ATTACK_DAMAGE.get()),
            base(a, AttributeRegistrar.ATTACKS_PER_SECOND.get()),
            base(a, AttributeRegistrar.ATTACK_RANGE.get()),
            base(a, Attributes.MOVEMENT_SPEED));
    }

    private static float base(AttributeSupplier a, Attribute attr) {
        return a.hasAttribute(attr) ? (float) a.getBaseValue(attr) : 0f;
    }

    /** The same stats for a worker: its token self-defence blow is not a stat worth showing. */
    public UnitStats withoutAttack() {
        return new UnitStats(health, armour, 0f, 0f, 0f, speed);
    }

    public boolean attacks() {
        return damage > 0f && attacksPerSecond > 0f;
    }

    /** Damage per second (per-hit damage x attacks per second), 0 when the unit doesn't attack. */
    public float dps() {
        return attacks() ? damage * attacksPerSecond : 0f;
    }

    /** The stats this unit has, in display order. */
    public List<Kind> kinds() {
        List<Kind> kinds = new ArrayList<>();
        if (health > 0f)
            kinds.add(Kind.HEALTH);
        if (armour > 0f)
            kinds.add(Kind.ARMOUR);
        if (attacks()) {
            kinds.add(Kind.DAMAGE);
            kinds.add(Kind.RANGE);
        }
        if (speed > 0f)
            kinds.add(Kind.SPEED);
        return kinds;
    }

    /** One decimal at most, none for a whole number ("6", "2.5"). */
    static String num(float v) {
        return Math.abs(v - Math.round(v)) < 0.05f ? Integer.toString(Math.round(v))
            : String.format(Locale.ROOT, "%.1f", v);
    }

    private MutableComponent part(Kind kind) {
        return switch (kind) {
            case HEALTH -> coloured(Component.translatable("hud.reignofnether.unit_stats.health", num(health)), TooltipColours.GREEN);
            case ARMOUR -> coloured(Component.translatable("hud.reignofnether.unit_stats.armour", num(armour)), TooltipColours.WHITE);
            case DAMAGE -> coloured(Component.translatable("hud.reignofnether.unit_stats.damage", num(damage), num(dps())), TooltipColours.RED);
            case RANGE -> coloured(Component.translatable("hud.reignofnether.unit_stats.range", num(range)), TooltipColours.YELLOW);
            case SPEED -> coloured(Component.translatable("hud.reignofnether.unit_stats.speed", Integer.toString((int) (speed * SPEED_SCALE))), TooltipColours.LIGHT_BLUE);
        };
    }

    private static MutableComponent coloured(MutableComponent c, int argb) {
        return c.withStyle(Style.EMPTY.withColor(argb & 0xFFFFFF));
    }

    /**
     * The tooltip lines: survivability first (HP, armour), then what it does to others and how it moves. At most two
     * lines, so the tooltip stays compact; an empty list when the unit has none of these stats.
     */
    public List<Component> lines() {
        MutableComponent defence = Component.empty();
        MutableComponent offence = Component.empty();
        for (Kind k : kinds()) {
            MutableComponent target = (k == Kind.HEALTH || k == Kind.ARMOUR) ? defence : offence;
            if (!target.getSiblings().isEmpty())
                target.append("   ");
            target.append(part(k));
        }
        List<Component> lines = new ArrayList<>();
        if (!defence.getSiblings().isEmpty())
            lines.add(defence);
        if (!offence.getSiblings().isEmpty())
            lines.add(offence);
        return lines;
    }
}
