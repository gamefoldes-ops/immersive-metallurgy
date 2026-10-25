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

/**
 * What it costs to skim slag off a crucible, and what makes that cost disappear. Skimming dirty melt by
 * hand drags metal out with it, because a viscous slag carries droplets of the metal floating in it; a
 * flux makes the slag thin and cohesive, so it comes off in one piece and leaves the metal behind. That is
 * the whole difference the numbers below encode.
 *
 * @param slagShare        how much of the slag lying in the crucible one skim takes, without flux
 * @param metalLoss        how much of the metal one skim takes with it, without flux
 * @param dustPerMilliBucket slag millilitres per Slag Dust item handed over
 * @param bowlBreakChance  chance a ceramic bowl does not survive one skim
 * @param flux             what has to be in the crucible for the clean version of the skim
 */
public record SlagSkimming(float slagShare, float metalLoss, float dustPerMilliBucket, float bowlBreakChance,
                           Ingredient flux) implements Recipe<NoopInput> {

    public static final MapCodec<SlagSkimming> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.FLOAT.fieldOf("slag_share").forGetter(SlagSkimming::slagShare),
            Codec.FLOAT.fieldOf("metal_loss").forGetter(SlagSkimming::metalLoss),
            Codec.FLOAT.fieldOf("dust_per_milli_bucket").forGetter(SlagSkimming::dustPerMilliBucket),
            Codec.FLOAT.fieldOf("bowl_break_chance").forGetter(SlagSkimming::bowlBreakChance),
            Ingredient.CODEC.fieldOf("flux").forGetter(SlagSkimming::flux)
    ).apply(instance, SlagSkimming::new));

    public static RecipeSerializer<SlagSkimming> serializer() {
        return new RecipeSerializer<>() {
            @Override
            public MapCodec<SlagSkimming> codec() {
                return CODEC;
            }

            @Override
            public StreamCodec<RegistryFriendlyByteBuf, SlagSkimming> streamCodec() {
                return STREAM_CODEC;
            }
        };
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, SlagSkimming> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.FLOAT, SlagSkimming::slagShare,
            ByteBufCodecs.FLOAT, SlagSkimming::metalLoss,
            ByteBufCodecs.FLOAT, SlagSkimming::dustPerMilliBucket,
            ByteBufCodecs.FLOAT, SlagSkimming::bowlBreakChance,
            Ingredient.CONTENTS_STREAM_CODEC, SlagSkimming::flux,
            SlagSkimming::new);

    public static SlagSkimming of(RecipeManager recipes) {
        for (RecipeHolder<SlagSkimming> holder : RecipeHelpers.getRecipes(recipes, IMRecipes.SLAG_SKIMMING)) {
            return holder.value();
        }
        return null;
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
        return IMRecipes.SLAG_SKIMMING_SERIALIZER.value();
    }

    @Override
    public RecipeType<? extends Recipe<NoopInput>> getType() {
        return IMRecipes.SLAG_SKIMMING.value();
    }
}
