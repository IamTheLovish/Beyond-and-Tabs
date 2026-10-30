package com.solegendary.reignofnether.building.production;

import com.solegendary.reignofnether.building.BuildingUtils;
import com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement;
import com.solegendary.reignofnether.gamerules.GameruleClient;
import com.solegendary.reignofnether.hero.HeroKit;
import com.solegendary.reignofnether.hero.HeroKits;
import com.solegendary.reignofnether.hero.HeroTypes;
import com.solegendary.reignofnether.keybinds.Keybinding;
import com.solegendary.reignofnether.registrars.GameRuleRegistrar;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.unit.HeroUnitSave;
import com.solegendary.reignofnether.unit.interfaces.HeroUnit;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.solegendary.reignofnether.util.MiscUtil.fcs;

public abstract class HeroProductionItem extends ProductionItem {

    public final ResourceLocation iconRl;
    public final String itemName;

    public HeroProductionItem(ResourceCost cost, String itemName, ResourceLocation iconRl) {
        super(cost);
        this.dupeRule = ProdDupeRule.DISALLOW;
        this.onComplete = (Level level, ProductionPlacement placement) -> {
            if (!level.isClientSide()) {
                if (canProduce(placement)) {
                    placement.produceUnit((ServerLevel) level, getHeroEntityType(), placement.ownerName, true);
                }
            }
        };
        this.itemName = itemName;
        this.iconRl = iconRl;
    }

    @Override
    @Nullable
    public String getProduceErrorMsg(ProductionPlacement pp) {
        if (pp.level.isClientSide()) return null;

        if (heroOwned(pp.level.isClientSide(), pp.ownerName))
            return "hud.hero.reignofnether.error.duplicate";

        int allowedHeroes = pp.level.getGameRules().getInt(GameRuleRegistrar.ALLOWED_HEROES);
        // without a castle only one T1-lab hero; a T2-lab hero (H13) skips that clamp and does not count toward it
        if (!isT2LabHero() && !BuildingUtils.castleOwned(pp.level.isClientSide(), pp.ownerName)
                && t1LabHeroesCounted(pp, this) >= Math.min(1, allowedHeroes))
            return "hud.hero.reignofnether.error.too_many_heroes";
        if (heroesCountedAgainstCap(pp, this) >= allowedHeroes)
            return "hud.hero.reignofnether.error.too_many_heroes";

        return null;
    }

    public String getItemName() {
        return itemName;
    }

    /**
     * The owner's heroes (alive, fallen or in training) that count against the hero cap - leaving out the one this lab
     * is finishing right now. ActiveProduction.complete runs onComplete while the finished item still heads the lab's
     * queue (ProductionPlacement.tick removes it afterwards), so it counted itself as "a hero in training": with the
     * usual cap of one, every hero was refused at the door and its cost was lost (lovish's Dawn Paladin, Oct 16 -
     * HeroGameTests.hero_spawns_when_its_training_finishes).
     */
    public static int heroesCountedAgainstCap(ProductionPlacement pp, ProductionItem item) {
        int n = HeroUnit.getNumHeroesOwnedOrInTraining(pp.level.isClientSide(), pp.ownerName);
        return finishing(pp, item) ? n - 1 : n;
    }

    /** Whether item is in pp's queue with no ticks left (being completed) and pp is a registered placement (so
     *  HeroUnit.getHeroesInTraining counted it). Its first entry, not only the head: behind a paused unit the queue step
     *  finishes the first item that is not paused (ProductionPlacement.tickProductionQueue, #228). */
    public static boolean finishing(ProductionPlacement pp, ProductionItem item) {
        ActiveProduction entry = null;
        for (ActiveProduction a : pp.productionQueue)
            if (a.item == item) {
                entry = a;
                break;
            }
        if (entry == null || entry.ticksLeft > 0)
            return false;
        return (pp.level.isClientSide() ? com.solegendary.reignofnether.building.BuildingClientEvents.getBuildings()
            : com.solegendary.reignofnether.building.BuildingServerEvents.getBuildings()).contains(pp);
    }

    protected boolean heroOwned(boolean isClientside, String ownerName) {
        String heroName = getHeroEntityType().getDescriptionId();
        return !HeroUnit.getHeroes(isClientside, ownerName, heroName).isEmpty() ||
                HeroUnit.getFallenHero(isClientside, ownerName, heroName) != null;
    }

    /** Like heroesCountedAgainstCap, but only T1-lab heroes: the castle clamp limits these to one (H13). */
    public static int t1LabHeroesCounted(ProductionPlacement pp, ProductionItem item) {
        int n = t1LabHeroCount(pp.level.isClientSide(), pp.ownerName);
        return finishing(pp, item) && !(item instanceof HeroProductionItem h && h.isT2LabHero()) ? n - 1 : n;
    }

    /** The owner's heroes alive, fallen or in training, leaving out T2-lab heroes. */
    public static int t1LabHeroCount(boolean isClientside, String ownerName) {
        Set<String> t2 = new HashSet<>();
        for (HeroKit kit : HeroKits.T2_LAB)
            t2.add(HeroTypes.heroTypeOf(kit).getDescriptionId());
        int n = 0;
        for (HeroUnit h : HeroUnit.getHeroes(isClientside, ownerName))
            if (!t2.contains(((LivingEntity) h).getType().getDescriptionId())) n++;
        for (HeroUnitSave s : HeroUnit.getFallenHeroes(isClientside, ownerName))
            if (!t2.contains(s.name)) n++;
        for (ActiveProduction p : HeroUnit.getHeroesInTraining(isClientside, ownerName))
            if (!(p.item instanceof HeroProductionItem h && h.isT2LabHero())) n++;
        return n;
    }

    /** A hero trained at a T2 lab (H13): the lab is gate enough, so the second-hero castle clamp does not apply. */
    protected boolean isT2LabHero() {
        return false;
    }

    // can't make this a member as we can't refer to other registered objects at init time
    protected EntityType<? extends HeroUnit> getHeroEntityType() {
        return null;
    }

    public StopProductionButton getCancelButton(ProductionPlacement prodBuilding, boolean first) {
        return new StopProductionButton(
                getItemName(),
                iconRl,
                prodBuilding,
                this,
                first
        );
    }

    public StartProductionButton getStartButton(ProductionPlacement prodBuilding, Keybinding hotkey, List<FormattedCharSequence> fcs) {
        return new StartProductionButton(
                itemName,
                iconRl,
                hotkey,
                () -> itemIsBeingProduced(prodBuilding.ownerName) ||
                        heroOwned(prodBuilding.level.isClientSide(), prodBuilding.ownerName) ||
                        HeroUnit.getNumHeroesOwnedOrInTraining(true, prodBuilding.ownerName) >= GameruleClient.allowedHeroes,
                () -> t1LabHeroCount(true, prodBuilding.ownerName) == 0 ||
                        isT2LabHero() || BuildingUtils.castleOwned(true, prodBuilding.ownerName),
                fcs,
                this
        );
    }

    protected List<FormattedCharSequence> getAdditionalHeroTooltips() {
        if (GameruleClient.allowedHeroes == 1) {
            return List.of(
                    fcs(""),
                    fcs(I18n.get("units.reignofnether.hero_production.allowed_heroes_1"))
            );
        } else if (GameruleClient.allowedHeroes >= 2) {
            return List.of(
                    fcs(""),
                    fcs(I18n.get("units.reignofnether.hero_production.allowed_heroes_2")),
                    fcs(I18n.get("units.reignofnether.hero_production.unlock_hero_2"))
            );
        } else {
            return List.of();
        }
    }
}
