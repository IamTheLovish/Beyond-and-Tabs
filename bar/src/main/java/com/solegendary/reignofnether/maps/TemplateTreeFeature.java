package com.solegendary.reignofnether.maps;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;

import java.util.List;

/**
 * One of our tree templates (TreeTemplates), picked from the configured feature's list, turned a random quarter and
 * mirrored half the time. The feature behind every battle-map tree: the battlefield biome places it through
 * data/reignofnether/worldgen/placed_feature/trees_battlefield.json, the pre-made maps through
 * MapBuilder.paletteFor(preset).tree() - so a shape change in tools/gen_trees.py reaches every map at once.
 * Grows only on soil (BlockTags.DIRT, grass included), like a sapling.
 */
public class TemplateTreeFeature extends Feature<TemplateTreeFeature.Config> {

    /** The template names under structures/trees/ (repeat a name to weight it). */
    public record Config(List<String> templates) implements FeatureConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.listOf().fieldOf("templates").forGetter(Config::templates)
        ).apply(i, Config::new));
    }

    public TemplateTreeFeature(Codec<Config> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<Config> ctx) {
        WorldGenLevel level = ctx.level();
        BlockPos origin = ctx.origin();
        RandomSource random = ctx.random();
        List<String> names = ctx.config().templates();
        if (names.isEmpty() || !level.getBlockState(origin.below()).is(BlockTags.DIRT))
            return false;
        if (!level.getBlockState(origin).isAir() && !level.getBlockState(origin).canBeReplaced())
            return false;
        TreeTemplates.Template t = TreeTemplates.load(names.get(random.nextInt(names.size())));
        if (t == null)
            return false;
        return TreeTemplates.place(level, t, origin, random.nextInt(4), random.nextBoolean()) > 0;
    }
}
