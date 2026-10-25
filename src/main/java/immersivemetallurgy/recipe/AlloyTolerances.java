package immersivemetallurgy.recipe;

import immersivemetallurgy.registry.IMRecipes;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;

import java.util.List;

/**
 * The single tuning sheet for forgiving melting: one data file, one rule per alloy we have decided to
 * support. It travels as a recipe because that is the only datapack structure Minecraft syncs to the
 * client and reloads with {@code /reload}, and the crucible result is recomputed client side as well.
 */
public record AlloyTolerances(List<Rule> rules) implements Recipe<NoopInput> {

    public static final MapCodec<AlloyTolerances> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Rule.CODEC.listOf().fieldOf("rules").forGetter(AlloyTolerances::rules)
    ).apply(instance, AlloyTolerances::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, AlloyTolerances> STREAM_CODEC =
            Rule.STREAM_CODEC.apply(ByteBufCodecs.list()).map(AlloyTolerances::new, AlloyTolerances::rules);

    public static RecipeSerializer<AlloyTolerances> serializer() {
        return new RecipeSerializer<>() {
            @Override
            public MapCodec<AlloyTolerances> codec() {
                return CODEC;
            }

            @Override
            public StreamCodec<RegistryFriendlyByteBuf, AlloyTolerances> streamCodec() {
                return STREAM_CODEC;
            }
        };
    }

    /**
     * @param alloy         the clean alloy fluid whose TFC bands this rule widens
     * @param flawedResult  what to pour when the melt only fits inside the widened bands
     * @param tolerance     absolute fraction of metal mass a single component may miss its band by
     * @param slagTolerance slag share that still counts as clean
     * @param maxSlag       slag share above which the melt is not this alloy at all
     */
    public record Rule(Fluid alloy,
                       Fluid flawedResult,
                       double tolerance,
                       double slagTolerance,
                       double maxSlag) {

        public static final Codec<Rule> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                BuiltInRegistries.FLUID.byNameCodec().fieldOf("alloy").forGetter(Rule::alloy),
                BuiltInRegistries.FLUID.byNameCodec().fieldOf("flawed_result").forGetter(Rule::flawedResult),
                Codec.DOUBLE.fieldOf("tolerance").forGetter(Rule::tolerance),
                Codec.DOUBLE.fieldOf("slag_tolerance").forGetter(Rule::slagTolerance),
                Codec.DOUBLE.fieldOf("max_slag").forGetter(Rule::maxSlag)
        ).apply(instance, Rule::new));

        public static final StreamCodec<RegistryFriendlyByteBuf, Rule> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.registry(Registries.FLUID), Rule::alloy,
                ByteBufCodecs.registry(Registries.FLUID), Rule::flawedResult,
                ByteBufCodecs.DOUBLE, Rule::tolerance,
                ByteBufCodecs.DOUBLE, Rule::slagTolerance,
                ByteBufCodecs.DOUBLE, Rule::maxSlag,
                Rule::new);
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
        return IMRecipes.ALLOY_TOLERANCE_SERIALIZER.value();
    }

    @Override
    public RecipeType<? extends Recipe<NoopInput>> getType() {
        return IMRecipes.ALLOY_TOLERANCE.value();
    }
}
