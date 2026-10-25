package immersivemetallurgy.recipe;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeInput;

/** Input for recipes that are data carriers rather than crafting operations. */
public record NoopInput() implements RecipeInput {
    public static final NoopInput INSTANCE = new NoopInput();

    @Override
    public ItemStack getItem(int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public int size() {
        return 0;
    }
}
