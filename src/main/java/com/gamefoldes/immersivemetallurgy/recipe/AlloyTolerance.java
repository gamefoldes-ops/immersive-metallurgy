package com.gamefoldes.immersivemetallurgy.recipe;

import com.gamefoldes.immersivemetallurgy.registry.IMRecipes;

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

/**
 * How far a melt may drift from one of TFC's alloy bands before we stop calling it that alloy, and
 * what to pour out when it drifts. All of it is placeholder tuning, which is why it lives in a
 * datapack file instead of in the matcher.
 *
 * @param alloy         the clean alloy fluid whose TFC bands this rule widens
 * @param flawedResult  what to pour when the melt only fits inside the widened bands
 * @param tolerance     absolute fraction of metal mass a single component may miss its band by
 * @param slagTolerance slag share that still counts as clean
 * @param maxSlag       slag share above which the melt is not this alloy at all
 */
public record AlloyTolerance(Fluid alloy,
                             Fluid flawedResult,
                             double tolerance,
                             double slagTolerance,
                             double maxSlag) implements Recipe<NoopInput> {

    public static final MapCodec<AlloyTolerance> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            BuiltInRegistries.FLUID.byNameCodec().fieldOf("alloy").forGetter(AlloyTolerance::alloy),
            BuiltInRegistries.FLUID.byNameCodec().fieldOf("flawed_result").forGetter(AlloyTolerance::flawedResult),
            Codec.DOUBLE.optionalFieldOf("tolerance", 0.06D).forGetter(AlloyTolerance::tolerance),
            Codec.DOUBLE.optionalFieldOf("slag_tolerance", 0.02D).forGetter(AlloyTolerance::slagTolerance),
            Codec.DOUBLE.optionalFieldOf("max_slag", 0.25D).forGetter(AlloyTolerance::maxSlag)
    ).apply(instance, AlloyTolerance::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, AlloyTolerance> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.registry(Registries.FLUID), AlloyTolerance::alloy,
            ByteBufCodecs.registry(Registries.FLUID), AlloyTolerance::flawedResult,
            ByteBufCodecs.DOUBLE, AlloyTolerance::tolerance,
            ByteBufCodecs.DOUBLE, AlloyTolerance::slagTolerance,
            ByteBufCodecs.DOUBLE, AlloyTolerance::maxSlag,
            AlloyTolerance::new);

    public static RecipeSerializer<AlloyTolerance> serializer() {
        return new RecipeSerializer<>() {
            @Override
            public MapCodec<AlloyTolerance> codec() {
                return CODEC;
            }

            @Override
            public StreamCodec<RegistryFriendlyByteBuf, AlloyTolerance> streamCodec() {
                return STREAM_CODEC;
            }
        };
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
