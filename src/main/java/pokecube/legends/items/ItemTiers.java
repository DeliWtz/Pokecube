package pokecube.legends.items;

import java.util.List;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.Tag;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.common.ForgeTier;
import net.minecraftforge.common.TierSortingRegistry;
import pokecube.legends.init.ItemInit;

public class ItemTiers
{
    public static final Tag.Named<Block> RAINBOW_WING_TAG = BlockTags.createOptional(new ResourceLocation(
            "pokecube_legends:needs_rainbow_wing_tool"));

    public static final Tier RAINBOW_WING = TierSortingRegistry.registerTier(new ForgeTier(5, 5000, 10, 100, 0,
            ItemTiers.RAINBOW_WING_TAG, () -> Ingredient.of(ItemInit.RAINBOW_WING.get())), new ResourceLocation(
                    "pokecube_legends:rainbow_wing"), List.of(Tiers.DIAMOND), List.of());
}