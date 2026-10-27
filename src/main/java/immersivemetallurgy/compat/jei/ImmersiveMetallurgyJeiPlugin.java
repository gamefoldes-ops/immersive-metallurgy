package immersivemetallurgy.compat.jei;

import immersivemetallurgy.ImmersiveMetallurgy;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

/**
 * JEI's window onto the numbers this mod tunes. Nothing here is required by the game: JEI finds the plugin by
 * annotation, and without it installed this class is never loaded.
 */
@JeiPlugin
public class ImmersiveMetallurgyJeiPlugin implements IModPlugin {
    private static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath(ImmersiveMetallurgy.MODID, "melt_rules");

    @Override
    public ResourceLocation getPluginUid() {
        return UID;
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        registration.addRecipeCategories(new MeltRulesCategory(registration.getJeiHelpers().getGuiHelper()));
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        // The tables are recipes, so they are only there once a world has been loaded and synced them.
        if (Minecraft.getInstance().level == null) {
            return;
        }
        registration.addRecipes(MeltRulesCategory.TYPE, MeltRules.of(Minecraft.getInstance().level.getRecipeManager()));
    }
}
