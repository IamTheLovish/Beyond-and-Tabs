package com.solegendary.reignofnether.unit.modelling.renderers;

import com.mojang.blaze3d.vertex.PoseStack;
import com.solegendary.reignofnether.unit.units.brass.RifleSquadUnit;
import com.solegendary.reignofnether.unit.units.villagers.VindicatorUnit;
import com.solegendary.reignofnether.unit.units.villagers.WindcallerUnit;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.SkeletonRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * The seven faction heroes (hero slice 2, design/hero_mode.md): each is drawn with its body's renderer in its own skin
 * (tools/gen_hero_skins.py, deterministic) and a tenth bigger than the unit it is built on, so a hero reads as a hero
 * in a crowd from the RTS camera.
 */
@OnlyIn(Dist.CLIENT)
public final class HeroRenderers {
    private HeroRenderers() { }

    public static final float HERO_SCALE = 1.1f;

    static ResourceLocation tex(String id) {
        return ResourceLocation.fromNamespaceAndPath("reignofnether", "textures/entities/" + id + "_unit.png");
    }

    /** Sunforged: the Halberdier's illager body in gilded plate. */
    public static class DawnPaladin extends VindicatorUnitRenderer {
        private static final ResourceLocation TEXTURE = tex("dawn_paladin");
        public DawnPaladin(EntityRendererProvider.Context context) { super(context); }
        @Override public ResourceLocation getTextureLocation(VindicatorUnit unit) { return TEXTURE; }
        @Override protected void scale(VindicatorUnit unit, PoseStack pose, float partialTicks) {
            super.scale(unit, pose, partialTicks);
            pose.scale(HERO_SCALE, HERO_SCALE, HERO_SCALE);
        }
    }

    /** Tidewrought: the Cutlass Raider's body in a captain's coat. */
    public static class CorsairCaptain extends VindicatorUnitRenderer {
        private static final ResourceLocation TEXTURE = tex("corsair_captain");
        public CorsairCaptain(EntityRendererProvider.Context context) { super(context); }
        @Override public ResourceLocation getTextureLocation(VindicatorUnit unit) { return TEXTURE; }
        @Override protected void scale(VindicatorUnit unit, PoseStack pose, float partialTicks) {
            super.scale(unit, pose, partialTicks);
            pose.scale(HERO_SCALE, HERO_SCALE, HERO_SCALE);
        }
    }

    /** Gravebound: the skeleton archer body in grave-robes (the Stray's frost layer left off). */
    public static class Gravecaller extends SkeletonRenderer {
        private static final ResourceLocation TEXTURE = tex("gravecaller");
        public Gravecaller(EntityRendererProvider.Context context) { super(context); }
        @Override public ResourceLocation getTextureLocation(AbstractSkeleton entity) { return TEXTURE; }
        @Override protected void scale(AbstractSkeleton entity, PoseStack pose, float partialTicks) {
            super.scale(entity, pose, partialTicks);
            pose.scale(HERO_SCALE, HERO_SCALE, HERO_SCALE);
        }
    }

    /** Verdant Court: the Thornbow's elven archer body in a bark-and-leaf mantle. */
    public static class ThornheartDruid extends ThornbowRenderer {
        private static final ResourceLocation TEXTURE = tex("thornheart_druid");
        public ThornheartDruid(EntityRendererProvider.Context context) { super(context); }
        @Override public ResourceLocation getTextureLocation(AbstractSkeleton entity) { return TEXTURE; }
        @Override protected void scale(AbstractSkeleton entity, PoseStack pose, float partialTicks) {
            super.scale(entity, pose, partialTicks);
            pose.scale(HERO_SCALE, HERO_SCALE, HERO_SCALE);
        }
    }

    /** Horde: the Brute's piglin body in a war-chief's gold and furs. */
    public static class HordeWarchief extends PiglinUnitRenderer {
        private static final ResourceLocation TEXTURE = tex("horde_warchief");
        public HordeWarchief(EntityRendererProvider.Context context) { super(context); }
        @Override public ResourceLocation getTextureLocation(Mob entity) { return TEXTURE; }
        @Override protected void scale(Mob entity, PoseStack pose, float partialTicks) {
            super.scale(entity, pose, partialTicks);
            pose.scale(HERO_SCALE, HERO_SCALE, HERO_SCALE);
        }
    }

    /** Horde T2-lab hero: PLACEHOLDER body - the Warchief's piglin model and skin (real Ash Shaman body comes later). */
    public static class AshShaman extends PiglinUnitRenderer {
        private static final ResourceLocation TEXTURE = tex("horde_warchief");   // placeholder skin
        public AshShaman(EntityRendererProvider.Context context) { super(context); }
        @Override public ResourceLocation getTextureLocation(Mob entity) { return TEXTURE; }
        @Override protected void scale(Mob entity, PoseStack pose, float partialTicks) {
            super.scale(entity, pose, partialTicks);
            pose.scale(HERO_SCALE, HERO_SCALE, HERO_SCALE);
        }
    }

    /** Stormcallers: the Windcaller's body (and its cloud) in sky-silk and lantern gold. */
    public static class StormWitch extends WindcallerRenderer {
        private static final ResourceLocation TEXTURE = tex("storm_witch");
        public StormWitch(EntityRendererProvider.Context context) { super(context); }
        @Override public ResourceLocation getTextureLocation(WindcallerUnit unit) { return TEXTURE; }
        @Override protected void scale(WindcallerUnit unit, PoseStack pose, float partialTicks) {
            super.scale(unit, pose, partialTicks);
            pose.scale(HERO_SCALE, HERO_SCALE, HERO_SCALE);
        }
    }

    /** Brassworks: the Rifle Squad's dwarven body in a guild master's brass harness. */
    public static class ArchEngineer extends RifleSquadRenderer {
        private static final ResourceLocation TEXTURE = tex("arch_engineer");
        public ArchEngineer(EntityRendererProvider.Context context) { super(context); }
        @Override public ResourceLocation getTextureLocation(RifleSquadUnit unit) { return TEXTURE; }
        @Override protected void scale(RifleSquadUnit unit, PoseStack pose, float partialTicks) {
            super.scale(unit, pose, partialTicks);
            pose.scale(HERO_SCALE, HERO_SCALE, HERO_SCALE);
        }
    }
}
