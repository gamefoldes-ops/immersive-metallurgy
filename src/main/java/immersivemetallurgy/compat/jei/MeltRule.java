package immersivemetallurgy.compat.jei;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.ArrayList;
import java.util.List;

/**
 * One row of one of our tuning tables, in the shape JEI can show: the items the row is about, which is also
 * what makes it findable when a player looks one of them up, and the lines that say what the table does.
 *
 * <p>The four tables are recipes only so that they sync to the client and reload with the world, which leaves
 * them with no screen of their own: a crucible that pours flawed bronze will say the ratio was wrong, but not
 * what the bands were, how much dirt each kind of rock brings, or what a fault costs the tool made from it.
 */
public record MeltRule(List<ItemStack> icons, List<Component> lines) {
    public MeltRule {
        icons = List.copyOf(icons);
        lines = List.copyOf(lines);
    }

    public static Builder builder(Component title, Component firstLine) {
        return new Builder(title, firstLine);
    }

    /** Accumulates the items of several entries into one row, because they are all the same number. */
    public static final class Builder {
        private final List<ItemStack> icons = new ArrayList<>();
        private final List<Component> lines = new ArrayList<>();

        private Builder(Component title, Component firstLine) {
            lines.add(title);
            lines.add(firstLine);
        }

        public Builder icons(Ingredient ingredient, int room) {
            for (ItemStack stack : ingredient.getItems()) {
                if (icons.size() >= room) {
                    return this;
                }
                icons.add(stack);
            }
            return this;
        }

        public MeltRule build() {
            return new MeltRule(icons, lines);
        }
    }
}
