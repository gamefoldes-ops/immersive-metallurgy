package immersivemetallurgy.item;

import immersivemetallurgy.registry.IMDataComponents;

import net.dries007.tfc.common.component.size.IItemSize;
import net.dries007.tfc.common.component.size.Size;
import net.dries007.tfc.common.component.size.Weight;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * The lump a shaft furnace lifts off its slag dish: the metal that ran over the dam, with the dirt it picked up
 * on the way. What it weighs and how much room it takes are not written in a table — they are the metal sitting
 * inside it, which {@code skimming_mb} already records. TFC reads size and weight straight off an item that
 * implements {@link IItemSize} ({@code ItemSizeManager.get} asks the item first and calls these with the real
 * stack), so the burden, the tooltip and the container fit all follow the amount: a dribble carries like a
 * pebble, a full dish like a boulder.
 *
 * The tiers step with millibuckets of metal, anchored so a single ingot's worth reads small and light and a
 * dish's worth (sixteen hundred) reads huge and very heavy. An empty default — the stack TFC sizes the item
 * from on reload — lands on the bottom step, which is what the item's base stack size is set to match.
 */
public final class SkimmingsItem extends Item implements IItemSize {
    public SkimmingsItem(Properties properties) {
        super(properties);
    }

    @Override
    public Size getSize(ItemStack stack) {
        int mb = metalMb(stack);
        if (mb < 300) {
            return Size.SMALL;
        }
        if (mb < 600) {
            return Size.NORMAL;
        }
        if (mb < 1000) {
            return Size.LARGE;
        }
        if (mb < 1400) {
            return Size.VERY_LARGE;
        }
        return Size.HUGE;
    }

    @Override
    public Weight getWeight(ItemStack stack) {
        int mb = metalMb(stack);
        if (mb < 300) {
            return Weight.LIGHT;
        }
        if (mb < 600) {
            return Weight.MEDIUM;
        }
        if (mb < 1000) {
            return Weight.HEAVY;
        }
        return Weight.VERY_HEAVY;
    }

    /** The metal in this lump, or zero for the empty stack TFC sizes the item from on reload. */
    private static int metalMb(ItemStack stack) {
        Integer mb = stack.get(IMDataComponents.SKIMMING_MB.get());
        return mb == null || mb < 0 ? 0 : mb;
    }
}
