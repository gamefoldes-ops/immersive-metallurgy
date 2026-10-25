package immersivemetallurgy.registry;

import immersivemetallurgy.ImmersiveMetallurgy;
import immersivemetallurgy.recipe.AlloyTolerances;
import immersivemetallurgy.recipe.FlawProfiles;
import immersivemetallurgy.recipe.OreFouling;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Our own recipe type for the numbers that steer forgiving melting. It is a recipe only because
 * that is the one datapack structure Minecraft syncs to the client and reloads with {@code /reload},
 * and the crucible result is recomputed client side every time the contents change.
 */
public final class IMRecipes {
    public static final ResourceLocation ALLOY_TOLERANCE_ID = ResourceLocation.fromNamespaceAndPath(ImmersiveMetallurgy.MODID, "alloy_tolerance");
    public static final ResourceLocation FLAW_PROFILE_ID = ResourceLocation.fromNamespaceAndPath(ImmersiveMetallurgy.MODID, "flaw_profile");
    public static final ResourceLocation ORE_FOULING_ID = ResourceLocation.fromNamespaceAndPath(ImmersiveMetallurgy.MODID, "ore_fouling");

    public static final DeferredRegister<RecipeType<?>> RECIPE_TYPES = DeferredRegister.create(Registries.RECIPE_TYPE, ImmersiveMetallurgy.MODID);
    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS = DeferredRegister.create(Registries.RECIPE_SERIALIZER, ImmersiveMetallurgy.MODID);

    public static final DeferredHolder<RecipeType<?>, RecipeType<AlloyTolerances>> ALLOY_TOLERANCE =
            RECIPE_TYPES.register("alloy_tolerance", () -> RecipeType.simple(ALLOY_TOLERANCE_ID));
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<AlloyTolerances>> ALLOY_TOLERANCE_SERIALIZER =
            RECIPE_SERIALIZERS.register("alloy_tolerance", AlloyTolerances::serializer);

    public static final DeferredHolder<RecipeType<?>, RecipeType<FlawProfiles>> FLAW_PROFILE =
            RECIPE_TYPES.register("flaw_profile", () -> RecipeType.simple(FLAW_PROFILE_ID));
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<FlawProfiles>> FLAW_PROFILE_SERIALIZER =
            RECIPE_SERIALIZERS.register("flaw_profile", FlawProfiles::serializer);

    public static final DeferredHolder<RecipeType<?>, RecipeType<OreFouling>> ORE_FOULING =
            RECIPE_TYPES.register("ore_fouling", () -> RecipeType.simple(ORE_FOULING_ID));
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<OreFouling>> ORE_FOULING_SERIALIZER =
            RECIPE_SERIALIZERS.register("ore_fouling", OreFouling::serializer);

    private IMRecipes() {
    }

    public static void register(IEventBus modEventBus) {
        RECIPE_TYPES.register(modEventBus);
        RECIPE_SERIALIZERS.register(modEventBus);
    }
}
