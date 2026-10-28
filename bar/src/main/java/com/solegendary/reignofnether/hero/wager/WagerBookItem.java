package com.solegendary.reignofnether.hero.wager;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

/**
 * Lovish's Wager Book as an item (design "The book"): a fat, lopsided ledger in patched leather with a brass die clasp.
 * Using it opens the book for the possessed hero, else the first selected hero, else the holder's nearest hero. The
 * pages themselves live on the hero (WagerState): the book in hand is only the way in.
 */
public class WagerBookItem extends Item {

    public WagerBookItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide())
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> WagerClient::openForSelection);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }
}
