package com.solegendary.reignofnether.hud;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.datafixers.util.Pair;
import com.mojang.math.Axis;
import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.ability.abilities.ToggleShield;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.addon.GarrisonableBuildingAddon;
import com.solegendary.reignofnether.healthbars.HealthBarClientEvents;
import com.solegendary.reignofnether.hud.buttons.Button;
import com.solegendary.reignofnether.hud.effecticons.EnchantmentIcon;
import com.solegendary.reignofnether.player.PlayerColors;
import com.solegendary.reignofnether.resources.ResourceSource;
import com.solegendary.reignofnether.resources.ResourceSources;
import com.solegendary.reignofnether.resources.Resources;
import com.solegendary.reignofnether.unit.Relationship;
import com.solegendary.reignofnether.unit.UnitClientEvents;
import com.solegendary.reignofnether.unit.UnitStatType;
import com.solegendary.reignofnether.unit.controls.SlimeJumpMoveControl;
import com.solegendary.reignofnether.unit.interfaces.AttackerUnit;
import com.solegendary.reignofnether.unit.interfaces.HeroUnit;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.interfaces.WorkerUnit;
import com.solegendary.reignofnether.unit.units.monsters.CreeperUnit;
import com.solegendary.reignofnether.unit.units.monsters.SlimeUnit;
import com.solegendary.reignofnether.unit.units.piglins.BruteUnit;
import com.solegendary.reignofnether.util.MiscUtil;
import com.solegendary.reignofnether.util.MyMath;
import com.solegendary.reignofnether.util.MyRenderer;
import net.minecraft.ReportedException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackResources;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.AbstractIllager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BannerItem;
import net.minecraft.world.item.ItemStack;
import org.apache.commons.lang3.text.WordUtils;
import org.joml.Quaternionf;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

import static com.solegendary.reignofnether.hud.TooltipColours.*;
import static com.solegendary.reignofnether.util.MiscUtil.fcs;

// Renders a Unit's portrait including its animated head, name, healthbar, list of stats and UI frames for these

public class PortraitRendererUnit<T extends LivingEntity, M extends EntityModel<T>, R extends LivingEntityRenderer<T, M>> {
    private static final Minecraft MC = Minecraft.getInstance();

    public R renderer;
    public Model model;

    // The selected unit's block (Oct 22, lovish: "the HUD for the units is so large ... units don't need their models
    // to pop up, just a picture of their icon with their stats"): the unit's HUD icon (the mob head the selection strip
    // and the buttons use) in a 32 px frame, its stats in a 2 x 3 grid beside it, one slim health bar with the numbers
    // under both. 100 x 43 px where the model portrait + stats column took 104 x 60 + a health line below. Mirrored by
    // HudLayout.PORTRAIT_W / STATS_W / PORTRAIT_H and tools/hud_layout.py.
    public final int frameWidth = 32;          // the icon frame (square)
    public final int frameHeight = 43;         // icon frame + the health row under it
    public final int statsWidth = 68;          // the stats grid beside the icon
    public final int statsHeight = 32;
    public final int sideWidth = 44;           // the carried-resources frame, and the width ItemClientEvents' inventory takes
    public static final int HEALTH_ROW_H = 10; // 9 px bar + 1 px air; a hero's mana bar adds one more row
    private static final int STAT_ROW_H = 10, STAT_COL_B_X = 33, STAT_ROWS = 3;

    // The old 3D model portrait: only the fallback for an entity with no HUD icon (a player, a mob with no mob head).
    // Flip to false to see which entities still depend on it - none of the faction units should.
    public static boolean MODEL_FALLBACK = true;
    private static final int OLD_FRAME = 60;   // the model portrait's old frame, the scale the offsets below were tuned at

    private final int size = 46;
    private final int offsetX = 31;
    private final int offsetY = 105;
    private final float standardEyeHeight = 1.74f; // height for most humanoid mobs

    // change these randomly every few seconds to make the head look around
    private int lookX = 0;
    private int lookY = 0;
    private int lastLookTargetX = 0;
    private int lastLookTargetY = 0;
    private int lookTargetX = 0;
    private int lookTargetY = 0;
    private int ticksLeft = 0;
    private final int ticksLeftMin = 30;
    private final int ticksLeftMax = 60;
    private final int lookRangeX = 100;
    private final int lookRangeY = 40;

    public final int HERO_Y_OFFSET = 10;   // a hero's block is one row taller (the mana bar under the health bar)

    public PortraitRendererUnit() {
    }

    public void randomiseAnimation(Boolean randomisePos) {
        if (randomisePos) {
            lookX = MyMath.randRangeInt(-lookRangeX, lookRangeX);
            lookY = MyMath.randRangeInt(-lookRangeY, lookRangeY);
        }
        ticksLeft = MyMath.randRangeInt(ticksLeftMin, ticksLeftMax);

        lastLookTargetX = lookTargetX;
        lastLookTargetY = lookTargetY;

        while (Math.abs(lookTargetX - lookX) < lookRangeX / 2)
            lookTargetX = MyMath.randRangeInt(-lookRangeX, lookRangeX);
        while (Math.abs(lookTargetY - lookY) < lookRangeY / 2)
            lookTargetY = MyMath.randRangeInt(-lookRangeY, lookRangeY);
    }

    private static boolean checkedFreshAnimations = false;
    private static boolean isFreshAnimationsInstalled = false;

    private static boolean shouldAnimate() {
        if (!checkedFreshAnimations && HudClientEvents.hudSelectedEntity != null) {
            boolean b = false;
            for (String s : MC.getResourceManager().listPacks().map(PackResources::packId)
                .toList()) {
                if (s.toLowerCase().contains("freshanimations")) {
                    b = true;
                    break;
                }
            }
            isFreshAnimationsInstalled = b;
            checkedFreshAnimations = true;
        }
        return !isFreshAnimationsInstalled ||
                HudClientEvents.hudSelectedEntity instanceof AbstractIllager ||
                HudClientEvents.hudSelectedEntity instanceof HeroUnit;
    }

    public void tickAnimation() {
        ticksLeft -= 1;
        if (ticksLeft <= 0) {
            this.randomiseAnimation(false);
        }

        int lookSpeedX = Math.abs(lastLookTargetX - lookX) / 20;
        int lookSpeedY = Math.abs(lastLookTargetY - lookY) / 20;

        if (lookX < lookTargetX) {
            lookX += lookSpeedX;
        }
        if (lookX > lookTargetX) {
            lookX -= lookSpeedX;
        }
        if (lookY < lookTargetY) {
            lookY += lookSpeedY;
        }
        if (lookY > lookTargetY) {
            lookY -= lookSpeedY;
        }

        if (Math.abs(lookTargetX - lookX) < lookSpeedX) {
            lookX = lookTargetX;
        }
        if (Math.abs(lookTargetY - lookY) < lookSpeedY) {
            lookY = lookTargetY;
        }
    }

    private static final java.util.Map<ResourceLocation, Boolean> ICON_EXISTS = new java.util.HashMap<>();

    private static boolean iconExists(ResourceLocation rl) {
        return ICON_EXISTS.computeIfAbsent(rl, r -> MC.getResourceManager().getResource(r).isPresent());
    }

    private static ResourceLocation mobHead(String iconName, String suffix) {
        return ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "textures/mobheads/" + iconName + suffix + ".png");
    }

    /**
     * The entity's HUD icon, the same mob head the selection strip draws (a mounted unit gets the "_half" head so the
     * rider shows behind it, as in the strip), or null when there is none.
     */
    public static ResourceLocation iconFor(LivingEntity entity) {
        if (entity == null || entity instanceof Player)
            return null;
        String name = MiscUtil.getEntityIconName(entity);
        ResourceLocation rl = mobHead(name, entity.isVehicle() && entity instanceof Unit ? "_half" : "");
        return iconExists(rl) ? rl : null;
    }

    /** True when the panel can be drawn for this entity: it has an icon, or the model fallback has a model to draw. */
    public boolean canRender(LivingEntity entity) {
        return iconFor(entity) != null || (MODEL_FALLBACK && model != null && renderer != null);
    }

    /** Pixels the whole block takes above the screen's bottom edge (a hero's extra mana row included). */
    public int blockHeight(LivingEntity entity) {
        return frameHeight + (entity instanceof HeroUnit ? HERO_Y_OFFSET : 0);
    }

    // Render the unit's block including:
    // - the icon in its frame (the old animated 3D head only when there is no icon)
    // - the name above it, the passive-effect icons above that
    // - the slim health bar with its numbers (a hero's mana bar under it)
    // Must be called from DrawScreenEvent
    public RectZone render(GuiGraphics guiGraphics, String name, int x, int y, int mouseX, int mouseY, LivingEntity entity) {
        boolean hero = entity instanceof HeroUnit;
        if (hero)
            y -= HERO_Y_OFFSET;   // the block grows upward: its mana row sits under the health row

        Relationship rs = UnitClientEvents.getPlayerToEntityRelationship(entity);
        int bgCol = PlayerColors.getPlayerPortraitDisplayColorHex(entity instanceof Unit u ? u.getOwnerName() : null);
        if (entity instanceof Player player) {
            bgCol = 0xA0000000 | PlayerColors.getPlayerAllianceColorHex(player.getName().getString());
        }
        MyRenderer.renderFrameWithBg(guiGraphics, x, y, frameWidth, frameWidth, bgCol);

        ResourceLocation icon = iconFor(entity);
        if (icon != null) {
            guiGraphics.pose().pushPose();
            guiGraphics.pose().translate(0, 0, 1);
            if (entity.isVehicle() && entity instanceof Unit && entity.getFirstPassenger() != null) {
                ResourceLocation rider = mobHead(MiscUtil.getEntityIconName(entity.getFirstPassenger()), "");
                if (iconExists(rider))
                    MyRenderer.renderIcon(guiGraphics, rider, x + 2, y + 2, frameWidth - 4);
            }
            MyRenderer.renderIcon(guiGraphics, icon, x + 2, y + 2, frameWidth - 4);
            guiGraphics.pose().popPose();
        } else if (MODEL_FALLBACK) {
            renderModelFallback(guiGraphics, x, y, entity);
        }

        // name (unless a player, since their nametag will be rendered anyway); the hero's sits above its experience bar
        String ownerLine = null;
        if (rs != Relationship.OWNED && entity instanceof Unit unit && unit.getOwnerName().length() > 0) {
            ownerLine = unit.getOwnerName();
        }
        name = WordUtils.capitalize(name);
        int nameY = hero ? y - 15 : y - 9;
        int passiveY = hero ? y - 24 : y - 16;
        int nameX = x;
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(0, 0, 2000);
        if (entity instanceof Unit unit) {
            for (Button passiveIcon : unit.getPassiveIcons()) {
                passiveIcon.render(guiGraphics, nameX - 2, passiveY, mouseX, mouseY);
                if (passiveIcon.isMouseOver(mouseX, mouseY))
                    passiveIcon.renderTooltip(guiGraphics, mouseX, mouseY);
                nameX += EnchantmentIcon.ICON_SIZE * 2;
            }
            if (!unit.getPassiveIcons().isEmpty())
                nameX += EnchantmentIcon.ICON_SIZE / 2;
        }
        if (!(entity instanceof Player)) {
            guiGraphics.drawString(Minecraft.getInstance().font, name, nameX + 4, nameY, WHITE);
            if (ownerLine != null)
                PortraitRendererBuilding.drawOwnerLine(guiGraphics, ownerLine, nameX + 4, nameY, 200);
        }
        guiGraphics.pose().popPose();

        // the health bar spans the icon and the stats beside it; the numbers sit on it
        int rowW = frameWidth + statsWidth;
        int rowY = y + frameWidth + 1;
        float cx = x + rowW / 2f;
        HealthBarClientEvents.renderForEntity(guiGraphics.pose(), entity, cx, rowY, rowW, HealthBarClientEvents.RenderMode.GUI_INFO);
        HealthBarClientEvents.renderAbsorbForEntity(guiGraphics.pose(), entity, cx, rowY, rowW, HealthBarClientEvents.RenderMode.GUI_INFO);

        float health = entity.getHealth() + entity.getAbsorptionAmount();
        String healthText = health >= 1 ? String.valueOf((int) health) : String.valueOf(health).substring(0, 3);
        float maxHealth = entity.getMaxHealth();
        if (entity.getAbsorptionAmount() > 0)
            maxHealth += MiscUtil.getMaxAbsorptionAmount(entity);
        healthText += "/" + ((int) maxHealth);
        barText(guiGraphics, healthText, (int) cx, rowY);

        if (entity instanceof HeroUnit heroUnit) {
            int manaY = rowY + HEALTH_ROW_H;
            HealthBarClientEvents.renderManaForEntity(guiGraphics.pose(), heroUnit, cx, manaY, rowW,
                    HealthBarClientEvents.RenderMode.GUI_INFO);
            float mana = heroUnit.getMana();
            String manaText = mana >= 1 ? String.valueOf((int) mana) : String.valueOf(mana).substring(0, 3);
            manaText += "/" + (int) heroUnit.getMaxMana();
            barText(guiGraphics, manaText, (int) cx, manaY);
        }

        return RectZone.getZoneByLW(x, y, frameWidth, frameHeight + (hero ? HERO_Y_OFFSET : 0));
    }

    /** The numbers on a 9 px bar: centred, shadowed, layered above the bar's quads. */
    private static void barText(GuiGraphics guiGraphics, String text, int centreX, int barY) {
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(0, 0, 10);
        guiGraphics.drawCenteredString(MC.font, text, centreX, barY + 1, WHITE);
        guiGraphics.pose().popPose();
    }

    // The old model portrait, scaled from its 60 px frame into the 32 px icon frame; only for an entity with no icon
    private void renderModelFallback(GuiGraphics guiGraphics, int x, int y, LivingEntity entity) {
        float k = frameWidth / (float) OLD_FRAME;
        Pair<Integer, Integer> yAndScaleOffsets = PortraitRendererModifiers.getPortraitRendererModifiers(entity);
        int drawX = x + Math.round(offsetX * k);
        int drawY = y + Math.round((entity.getEyeHeight() / standardEyeHeight * offsetY + yAndScaleOffsets.getFirst()) * k);
        int sizeFinal = Math.round((size + yAndScaleOffsets.getSecond()) * k);

        boolean hasBanner = false;
        ItemStack bannerStack = entity.getItemBySlot(EquipmentSlot.HEAD);
        if (bannerStack.getItem() instanceof BannerItem) {
            hasBanner = true;
            entity.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
        }
        // clipped to the frame's inside: long weapons were drawn across the HUD beside the portrait (audit Oct 15,
        // item 14); finally - a failed draw must not leave the HUD clipped
        guiGraphics.enableScissor(x + 2, y + 2, x + frameWidth - 2, y + frameWidth - 2);
        try {
            drawEntityOnScreen(guiGraphics.pose(), entity, drawX, drawY, sizeFinal);
        } finally {
            guiGraphics.disableScissor();
        }
        if (hasBanner)
            entity.setItemSlot(EquipmentSlot.HEAD, bannerStack);
    }

    private class RenderedStat {
        public ResourceLocation icon;
        public String text;
        public UnitStatType type;
        public int colour = WHITE;
        public int textXOffset = 0;

        public RenderedStat(ResourceLocation icon, String text, UnitStatType type) {
            this.icon = icon;
            this.text = text;
            this.type = type;
        }
        public RenderedStat(ResourceLocation icon, String text, UnitStatType type, int colour, int xOffset) {
            this.icon = icon;
            this.text = text;
            this.type = type;
            this.colour = colour;
            this.textXOffset = xOffset;
        }
    }

    public RectZone renderStats(GuiGraphics guiGraphics, int x, int y, int mouseX, int mouseY, Unit unit) {
        boolean hero = unit instanceof HeroUnit;
        if (hero)
            y -= HERO_Y_OFFSET;   // the same shift render() makes: icon and stats stay level
        MyRenderer.renderFrameWithBg(guiGraphics, x, y, statsWidth, statsHeight, 0xA0000000);

        ArrayList<RenderedStat> renderedStats = new ArrayList<>();

        if (unit instanceof AttackerUnit attackerUnit) {
            double atkDmg = attackerUnit.getUnitAttackDamage() + AttackerUnit.getWeaponDamageModifier(attackerUnit);
            if (unit instanceof CreeperUnit cUnit && cUnit.isPowered()) {
                atkDmg *= CreeperUnit.CHARGED_DAMAGE_MULT;
            }
            if (unit instanceof WorkerUnit) {
                atkDmg = (int) attackerUnit.getUnitAttackDamage();
            }
            double atkDmgRounded = Math.round(atkDmg);
            String atkStr;
            if (Math.abs(atkDmgRounded - atkDmg) < 0.1d) {
                atkStr = String.valueOf((int) atkDmgRounded); // attacks per second
            } else {
                DecimalFormat df1 = new DecimalFormat("###.#");
                atkStr = String.valueOf(df1.format(atkDmg)); // attacks per second
            }
            if (unit instanceof Mob mob && mob.isVehicle() && mob.getFirstPassenger() instanceof AttackerUnit passenger) {
                atkStr += "+" + (int) passenger.getUnitAttackDamage();
            }

            int damageTooltipColour = attackerUnit.getDamageTooltipColour();
            float attackDmg = attackerUnit.getUnitAttackDamage();
            float baseAttackDmg = attackerUnit.getBaseUnitAttackDamage();
            if (attackDmg < baseAttackDmg)
                damageTooltipColour = RED;
            else if (attackDmg > baseAttackDmg)
                damageTooltipColour = GREEN;

            renderedStats.add(new RenderedStat(
                    ResourceLocation.fromNamespaceAndPath("reignofnether", "textures/icons/items/sword.png"),
                    atkStr,
                    UnitStatType.ATTACK_DAMAGE,
                    damageTooltipColour,
                    0
            ));
            DecimalFormat df2 = new DecimalFormat("###.##");

            int atkSpdColour = LIGHT_BLUE;
            float attacksPerSecond = Math.round(attackerUnit.getNonBaseAttacksPerSecond() * 100f) / 100f;
            float baseAttacksPerSecond = Math.round(attackerUnit.getBaseAttacksPerSecond() * 100f) / 100f;

            if (attacksPerSecond > baseAttacksPerSecond) {
                atkSpdColour = GREEN;
            } else if (attacksPerSecond < baseAttacksPerSecond) {
                atkSpdColour = RED;
            }
            renderedStats.add(new RenderedStat(
                    ResourceLocation.fromNamespaceAndPath("reignofnether","textures/icons/items/sparkler.png"),
                    String.valueOf(df2.format(attackerUnit.getAttacksPerSecond())),
                    UnitStatType.ATTACK_SPEED,
                    atkSpdColour,
                    0
            ));
            String rangeStr;
            BuildingPlacement garrPlacement = GarrisonableBuildingAddon.getGarrison(unit);
            GarrisonableBuildingAddon garr = garrPlacement != null ? garrPlacement.getBuilding().getActiveAddon(GarrisonableBuildingAddon.class) : null;
            if (garr != null) {
                rangeStr = String.valueOf(garr.getAttackRange());
            } else {
                rangeStr = String.valueOf((int) (attackerUnit.getAttackRange()));
            }
            renderedStats.add(new RenderedStat(
                    ResourceLocation.fromNamespaceAndPath("reignofnether","textures/icons/items/bow.png"),
                    rangeStr,
                    UnitStatType.RANGE,
                    attackerUnit.hasBonusRange() ? GREEN : WHITE,
                    0
            ));
        }

        int armourColor = WHITE;
        double physicalArmour = unit.getUnitPhysicalArmorPercentage();
        double rangedArmour = unit.getUnitRangedArmorPercentage();
        double magicArmour = unit.getUnitMagicArmorPercentage();
        double resistArmour = unit.getUnitResistPercentage();
        double highestArmour = 0;

        String armourStr = "0%";
        int nonZeroArmourTypes = 0;

        if (physicalArmour != 0) {
            armourStr = (int) (physicalArmour * 100) + "%";
            armourColor = GREEN;
            if (physicalArmour > highestArmour) highestArmour = physicalArmour;
            nonZeroArmourTypes += 1;
        }
        if (rangedArmour != 0) {
            armourStr = (int) (rangedArmour * 100) + "%";
            armourColor = YELLOW;
            if (rangedArmour > highestArmour) highestArmour = rangedArmour;
            nonZeroArmourTypes += 1;
        }
        if (resistArmour != 0) {
            armourStr = (int) (resistArmour * 100) + "%";
            armourColor = LIGHT_BLUE;
            if (resistArmour > highestArmour) highestArmour = resistArmour;
            nonZeroArmourTypes += 1;
        }
        if (magicArmour != 0 && resistArmour <= 0) { // resistArmour includes magicArmour
            armourStr = (int) (magicArmour * 100) + "%";
            armourColor = DARK_BLUE;
            if (magicArmour > highestArmour) highestArmour = magicArmour;
            nonZeroArmourTypes += 1;
        }
        if (nonZeroArmourTypes > 1 && highestArmour > 0) {
            armourStr = (int) (highestArmour * 100) + "%+";
            armourColor = LIGHT_BLUE;
        }
        if (armourStr.contains("-")) {
            armourColor = RED;
        }

        int xOffset = 0;
        if (armourStr.endsWith("+")) xOffset = -3;
        if (armourStr.startsWith("-")) xOffset = -4;

        renderedStats.add(new RenderedStat(
                ResourceLocation.fromNamespaceAndPath("reignofnether", "textures/icons/items/chestplate.png"),
                armourStr,
                UnitStatType.ARMOUR,
                armourColor,
                xOffset
        ));

        if (((LivingEntity) unit).getAttribute(Attributes.MOVEMENT_SPEED) != null) {
            float ms = unit.getMovementSpeed() * unit.getSpeedModifier();
            int msInt = (int) (ms * 101);
            if (unit instanceof SlimeUnit slimeUnit && slimeUnit.isUsingJumpingMovement()) {
                msInt /= SlimeJumpMoveControl.MOVESPEED_MULTIPLIER;
            }
            int msColour = WHITE;
            double msBase = ((LivingEntity) unit).getAttributeBaseValue(Attributes.MOVEMENT_SPEED);
            if (msBase < ms) {
                msColour = GREEN;
            } else if (msBase > ms) {
                msColour = RED;
            }
            renderedStats.add(new RenderedStat(
                    ResourceLocation.fromNamespaceAndPath("reignofnether","textures/icons/items/boots.png"),
                    String.valueOf(msInt),
                    UnitStatType.MOVEMENT_SPEED,
                    msColour,
                    0
            ));
        }

        if (unit instanceof HeroUnit heroUnit) {
            // the level leads the grid: text only, no icon (it used to be drawn over the top-left of the column)
            renderedStats.add(0, new RenderedStat(null,
                    I18n.get("hud.hero.reignofnether.level.short", heroUnit.getHeroLevel()),
                    null, WHITE, 0));
        }

        // two columns of three, filled down the first then the second: attack, attack speed and range (or the level
        // first on a hero) on the left, armour and speed on the right
        for (int i = 0; i < renderedStats.size(); i++) {
            RenderedStat renderedStat = renderedStats.get(i);
            int col = i / STAT_ROWS, row = i % STAT_ROWS;
            int cellX = x + 4 + col * STAT_COL_B_X;
            int cellY = y + 2 + row * STAT_ROW_H;
            int cellW = col == 0 ? STAT_COL_B_X - 1 : statsWidth - 4 - STAT_COL_B_X;
            int textX = cellX;
            if (renderedStat.icon != null) {
                MyRenderer.renderIcon(guiGraphics, renderedStat.icon, cellX, cellY, 8);
                textX = cellX + 10;
            }

            boolean isMouseOver = renderedStat.type != null &&
                    mouseX >= cellX - 1 && mouseY >= cellY - 1 &&
                    mouseX < cellX + cellW && mouseY < cellY + 9;
            if (isMouseOver) {
                guiGraphics.fill(cellX - 1, cellY - 1, cellX + cellW, cellY + 9, 0x32FFFFFF);
                MyRenderer.renderTooltip(guiGraphics, unit.getStatTooltip(renderedStat.type), mouseX, mouseY);
            }
            guiGraphics.drawString(Minecraft.getInstance().font, renderedStat.text, textX + renderedStat.textXOffset, cellY, renderedStat.colour);
        }
        return RectZone.getZoneByLW(x, y, statsWidth, statsHeight);
    }

    public RectZone renderHeroLevelAndExp(GuiGraphics guiGraphics, int x, int y, int mouseX, int mouseY, HeroUnit heroUnit) {
        int width = 101;
        int height = 5;

        y -= HERO_Y_OFFSET;

        ResourceLocation expBarEmptyRl = ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "textures/hud/experience_bar_empty.png");
        RenderSystem.setShaderTexture(0, expBarEmptyRl);
        guiGraphics.blit(expBarEmptyRl,
                x, y, 0,
                0,0, // where on texture to start drawing from
                width, height, // dimensions of blit texture
                width, height // size of texture itself (if < dimensions, texture is repeated)
        );
        ResourceLocation expBarFullRl = ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "textures/hud/experience_bar_full.png");
        RenderSystem.setShaderTexture(0, expBarFullRl);
        float expPercent = (float) heroUnit.getExpOnCurrentLevel() / heroUnit.getExpToNextlevel();
        if (heroUnit.getHeroLevel() >= HeroUnit.MAX_LEVEL)
            expPercent = 1.0f;
        guiGraphics.blit(expBarFullRl,
                x, y, 0,
                0,0, // where on texture to start drawing from
                Math.round((float) width * expPercent), height, // dimensions of blit texture
                width, height // size of texture itself (if < dimensions, texture is repeated)
        );
        return RectZone.getZoneByLW(x, y-2, width, height+4);
    }


    public RectZone renderResourcesHeld(GuiGraphics guiGraphics, int x, int y, Unit unit) {
        Resources resources = Resources.getTotalResourcesFromItems(unit.getItems());
        return renderResourcesHeld(guiGraphics, x, y, resources, Unit.atMaxResources(unit));
    }

    public RectZone renderResourcesHeld(GuiGraphics guiGraphics, int x, int y, Animal animal) {
        Resources resources = new Resources("",0,0,0,0);
        for (ItemStack itemStack : ResourceSources.getFoodItemsFromAnimal(animal)) {
            ResourceSource res = ResourceSources.getFromItem(itemStack.getItem());
            if (res != null)
                resources.food += res.resourceValue * itemStack.getCount();
        }
        return renderResourcesHeld(guiGraphics, x, y, resources, false);
    }

    public RectZone renderResourcesHeld(GuiGraphics guiGraphics, int x, int y, Resources resources, boolean redText) {
        MyRenderer.renderFrameWithBg(guiGraphics, x, y, sideWidth, statsHeight, 0xA0000000);

        int blitXIcon = x + 5;
        int blitYIcon = y + 2;   // three rows of 10 fit the 32 px frame, level with the stats grid

        // prep strings/icons to render
        List<ResourceLocation> textureStatIcons = List.of(ResourceLocation.fromNamespaceAndPath("reignofnether",
                "textures/icons/items/wheat.png"
            ),
            ResourceLocation.fromNamespaceAndPath("reignofnether", "textures/icons/items/wood.png"),
            ResourceLocation.fromNamespaceAndPath("reignofnether", "textures/icons/items/iron_ore.png")
        );

        List<String> statStrings = List.of(String.valueOf(resources.food),
            String.valueOf(resources.wood),
            String.valueOf(resources.ore)
        );

        // render based on prepped strings/icons
        for (int i = 0; i < statStrings.size(); i++) {

            if (!statStrings.get(i).equals("0")) {
                MyRenderer.renderIcon(guiGraphics, textureStatIcons.get(i), blitXIcon, blitYIcon, 8);
                guiGraphics.drawString(
                    Minecraft.getInstance().font,
                    statStrings.get(i),
                    blitXIcon + 11,
                    blitYIcon,
                    redText ? RED : WHITE
                );
                blitYIcon += 10;
            }
        }
        return RectZone.getZoneByLW(x, y, sideWidth, statsHeight);
    }

    private void drawEntityOnScreen(PoseStack poseStack, LivingEntity entity, int x, int y, int size) {
        float f = (float) Math.atan(-lookX / 40F);
        float g = (float) Math.atan(-lookY / 40F);
        PoseStack poseStackModel = RenderSystem.getModelViewStack();
        poseStackModel.pushPose();
        poseStackModel.translate(x, y, 1050.0D);
        poseStackModel.scale(1.0F, 1.0F, -1.0F);
        RenderSystem.applyModelViewMatrix();
        poseStack.pushPose();
        poseStack.translate(0.0D, 0.0D, 10.0D);
        poseStack.scale((float) size, (float) size, (float) size);
        float h = entity.yBodyRot; // bodyYaw;
        float i = entity.getYRot(); // getYaw();
        float j = entity.getXRot(); // getPitch();
        float k = entity.yHeadRotO; // prevHeadYaw;
        float l = entity.yHeadRot; // headYaw;
        Quaternionf quaternion = Axis.ZP.rotationDegrees(180.0F);
        Quaternionf quaternion2 = Axis.XP.rotationDegrees(g * 20.0F);
        quaternion.mul(quaternion2);
        poseStack.mulPose(quaternion);
        if (shouldAnimate()) {
            entity.yBodyRot = 180.0F + f * 20.0F;
            entity.setYRot(180.0F + f * 40.0F);
            entity.setXRot(-g * 20.0F);
            entity.yHeadRot = entity.getYRot();
            entity.yHeadRotO = entity.getYRot();
        }
        Lighting.setupForEntityInInventory();
        EntityRenderDispatcher entityrenderdispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        entityrenderdispatcher.setRenderShadow(false);
        quaternion2.conjugate();
        entityrenderdispatcher.overrideCameraOrientation(quaternion2);

        // for some reason this snippet causes drawLineBox to draw lines in completely wrong locations while in
        // spectator mode
        RenderSystem.runAsFancy(() -> {
            try {
                MultiBufferSource.BufferSource immediate = Minecraft.getInstance().renderBuffers().bufferSource();
                // hide the floating name tag (e.g. "Commander") inside the portrait - the HUD already prints the name
                boolean nameVisible = entity.isCustomNameVisible();
                entity.setCustomNameVisible(false);
                try {
                    entityrenderdispatcher.render(entity, 0.0D, 0.0D, 0.0D, 0.0F, 1.0F, poseStack, immediate, 15728880);
                } finally {
                    entity.setCustomNameVisible(nameVisible);
                }
                immediate.endBatch();
            } catch (ReportedException e) {
                //System.out.println("Caught reportedException: " + e);
            }
        });
        entityrenderdispatcher.setRenderShadow(true);

        if (shouldAnimate()) {
            entity.yBodyRot = h;
            entity.setYRot(i);
            entity.setXRot(j);
            entity.yHeadRotO = k;
            entity.yHeadRot = l;
        }
        poseStackModel.popPose();
        poseStack.popPose();
        RenderSystem.applyModelViewMatrix();
        Lighting.setupFor3DItems();
    }
}
