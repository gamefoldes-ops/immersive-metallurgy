package immersivemetallurgy.compat.jei;

import immersivemetallurgy.ImmersiveMetallurgy;
import immersivemetallurgy.registry.IMItems;

import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * The one place our four tuning tables are shown. They are recipes only so that they sync and reload, which
 * means nothing else in the game has anywhere to put them: a crucible that pours flawed bronze will say the
 * ratio was wrong, but not what the bands were, how much dirt each rock brings, or what a fault costs a tool.
 *
 * <p>Everything is a slot or a text widget, so JEI's own theme, scrolling and search come for free - and the
 * slots are what make a rule findable by the item it is about, which is how a player actually looks these up.
 */
public class MeltRulesCategory implements IRecipeCategory<MeltRule> {
    public static final RecipeType<MeltRule> TYPE = new RecipeType<>(
            ResourceLocation.fromNamespaceAndPath(ImmersiveMetallurgy.MODID, "melt_rules"), MeltRule.class);

    private static final int WIDTH = 176;
    private static final int HEIGHT = 128;
    private static final int MARGIN = 8;
    private static final int SLOT = 18;
    private static final int PER_ROW = (WIDTH - 2 * MARGIN) / SLOT;

    private final IDrawable icon;
    private final Component title;

    public MeltRulesCategory(IGuiHelper helper) {
        this.icon = helper.createDrawableItemStack(new ItemStack(IMItems.SLAG_DUST.get()));
        this.title = Component.translatable("immersive_metallurgy.jei.melt_rules");
    }

    @Override
    public RecipeType<MeltRule> getRecipeType() {
        return TYPE;
    }

    @Override
    public Component getTitle() {
        return title;
    }

    @Override
    public IDrawable getIcon() {
        return icon;
    }

    @Override
    public int getWidth() {
        return WIDTH;
    }

    @Override
    public int getHeight() {
        return HEIGHT;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, MeltRule rule, IFocusGroup focuses) {
        List<ItemStack> icons = rule.icons();
        for (int i = 0; i < icons.size(); i++) {
            builder.addSlot(RecipeIngredientRole.INPUT, MARGIN + (i % PER_ROW) * SLOT, MARGIN + (i / PER_ROW) * SLOT)
                    .addItemStack(icons.get(i));
        }
    }

    @Override
    public void createRecipeExtras(IRecipeExtrasBuilder extras, MeltRule rule, IFocusGroup focuses) {
        int rows = (rule.icons().size() + PER_ROW - 1) / PER_ROW;
        int top = MARGIN + rows * SLOT + 4;
        extras.addText(new ArrayList<FormattedText>(rule.lines()),
                MARGIN, top, WIDTH - 2 * MARGIN, HEIGHT - top - MARGIN);
    }
}
