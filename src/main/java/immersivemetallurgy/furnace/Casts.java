package immersivemetallurgy.furnace;

import immersivemetallurgy.component.MeltCarries;
import immersivemetallurgy.registry.IMAlloys;
import immersivemetallurgy.registry.IMFluids;
import immersivemetallurgy.registry.IMItems;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.List;

/**
 * What slag becomes once it has left the bath: dust, named for the metal it was lifted out of.
 *
 * The metal half of the furnace's output is not here at all, because it does not go through us — the tap fills
 * TFC's own mold on TFC's own table, and the cast comes out of TFC's casting recipe with our defect record
 * already stamped on it by the mixin that hooks that recipe.
 */
public final class Casts {
    private Casts() {
    }

    /**
     * The dust one unit of slag makes, named for the metal it was lifted out of, because what dust is good for
     * depends on what is still hanging in it. The bath's own answer names that metal, so the dust and the ingots
     * poured beside it can never disagree.
     */
    public static ItemStack dust(FluidStack bath, int count) {
        IMAlloys metal = bath.isEmpty() ? null : IMFluids.alloyOf(bath.getFluid());
        ItemStack stack = new ItemStack(metal == null ? IMItems.SLAG_DUST.get() : IMItems.slagDust(metal), count);
        MeltCarries.copyInto(bath, stack);
        return stack;
    }

    /**
     * The same amount as the item can actually be carried in: dust stacks to sixteen, so a full plate of sixty-four
     * units is four stacks and not one stack the game has to refuse, truncate or save in a shape nothing else reads.
     */
    public static List<ItemStack> split(ItemStack stack) {
        List<ItemStack> stacks = new ArrayList<>();
        int left = stack.getCount();
        while (left > 0) {
            int take = Math.min(stack.getMaxStackSize(), left);
            stacks.add(stack.copyWithCount(take));
            left -= take;
        }
        return stacks;
    }
}
