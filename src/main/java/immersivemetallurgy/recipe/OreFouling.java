package immersivemetallurgy.recipe;

import immersivemetallurgy.registry.IMRecipes;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.dries007.tfc.common.recipes.RecipeHelpers;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * How dirty each piece of raw ore is. TFC decides only how much metal an ore yields, and treats every yield as
 * pure, which makes a crucible full of ore indistinguishable from one full of refined metal. This adds the
 * other half of the ore: the gangue that comes with it, as a share of the metal it yields, so smelting
 * straight from rock leaves slag in the melt and a player has to refine it out.
 *
 * @param slagRatio how much slag an ore adds per unit of metal it yields, so 0.25 means a 100 mB yield leaves
 *                  25 mB of slag riding along and the melt is 80% metal
 */
public record OreFouling(List<Entry> entries) implements Recipe<NoopInput> {

    public static final MapCodec<OreFouling> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Entry.CODEC.listOf().fieldOf("entries").forGetter(OreFouling::entries)
    ).apply(instance, OreFouling::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, OreFouling> STREAM_CODEC =
            Entry.STREAM_CODEC.apply(ByteBufCodecs.list()).map(OreFouling::new, OreFouling::entries);

    public static RecipeSerializer<OreFouling> serializer() {
        return new RecipeSerializer<>() {
            @Override
            public MapCodec<OreFouling> codec() {
                return CODEC;
            }

            @Override
            public StreamCodec<RegistryFriendlyByteBuf, OreFouling> streamCodec() {
                return STREAM_CODEC;
            }
        };
    }

    /** The fouling of one melting item, or 0 for anything that is already refined metal. */
    public float slagRatioOf(ItemStack stack) {
        for (Entry entry : entries) {
            if (entry.ingredient().test(stack)) {
                return entry.slagRatio();
            }
        }
        return 0;
    }

    public static OreFouling of(RecipeManager recipes) {
        for (RecipeHolder<OreFouling> holder : RecipeHelpers.getRecipes(recipes, IMRecipes.ORE_FOULING)) {
            return holder.value();
        }
        return null;
    }

    public record Entry(Ingredient ingredient, float slagRatio) {

        public static final Codec<Entry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Ingredient.CODEC.fieldOf("ingredient").forGetter(Entry::ingredient),
                Codec.FLOAT.fieldOf("slag_ratio").forGetter(Entry::slagRatio)
        ).apply(instance, Entry::new));

        public static final StreamCodec<RegistryFriendlyByteBuf, Entry> STREAM_CODEC = StreamCodec.composite(
                Ingredient.CONTENTS_STREAM_CODEC, Entry::ingredient,
                ByteBufCodecs.FLOAT, Entry::slagRatio,
                Entry::new);
    }

    @Override
    public boolean matches(NoopInput input, Level level) {
        return false;
    }

    @Override
    public ItemStack assemble(NoopInput input, HolderLookup.Provider registries) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return false;
    }

    @Override
    public ItemStack getResultItem(HolderLookup.Provider registries) {
        return ItemStack.EMPTY;
    }

    @Override
    public RecipeSerializer<? extends Recipe<NoopInput>> getSerializer() {
        return IMRecipes.ORE_FOULING_SERIALIZER.value();
    }

    @Override
    public RecipeType<? extends Recipe<NoopInput>> getType() {
        return IMRecipes.ORE_FOULING.value();
    }
}
