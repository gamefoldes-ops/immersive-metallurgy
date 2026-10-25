package com.gamefoldes.immersivemetallurgy.component;

import it.unimi.dsi.fastutil.objects.Object2DoubleMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.level.material.Fluid;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * What a pour was actually made of. TFC reduces a crucible to one fluid, so the moment metal leaves it
 * the recipe that described the melt is gone, and a junk or flawed ingot can never be re-melted into
 * its components. This rides as integer ratios rather than amounts, so it survives being split across
 * units, poured in different quantities, or cast into a mold that holds less than the crucible did.
 */
public record AlloyRecord(List<FluidParts> parts) {

    public static final Codec<AlloyRecord> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            FluidParts.CODEC.listOf().fieldOf("parts").forGetter(AlloyRecord::parts)
    ).apply(instance, AlloyRecord::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, AlloyRecord> STREAM_CODEC =
            FluidParts.STREAM_CODEC.apply(ByteBufCodecs.list()).map(AlloyRecord::new, AlloyRecord::parts);

    public AlloyRecord {
        parts = List.copyOf(parts);
    }

    /** Reduces the crucible's contents to the smallest whole-number ratio that describes them. */
    public static AlloyRecord fromContent(Object2DoubleMap<Fluid> content) {
        List<FluidParts> parts = new ArrayList<>();
        int gcd = 0;
        for (Object2DoubleMap.Entry<Fluid> entry : content.object2DoubleEntrySet()) {
            long amount = Math.round(entry.getDoubleValue());
            if (amount <= 0) {
                continue;
            }
            parts.add(new FluidParts(entry.getKey(), (int) amount));
            gcd = gcd == 0 ? (int) amount : gcd(gcd, (int) amount);
        }
        if (parts.isEmpty()) {
            return null;
        }
        if (gcd > 1) {
            int divisor = gcd;
            parts.replaceAll(part -> new FluidParts(part.fluid(), part.parts() / divisor));
        }
        return new AlloyRecord(parts);
    }

    public int totalParts() {
        int total = 0;
        for (FluidParts part : parts) {
            total += part.parts();
        }
        return total;
    }

    /**
     * Splits an amount of metal back into the fluids it was made of. The whole remainder goes to the
     * largest part so the pieces always add up to exactly the amount that came in.
     */
    public void expand(int amount, BiConsumer<Fluid, Integer> sink) {
        int total = totalParts();
        if (total <= 0) {
            return;
        }
        int largest = 0;
        int handedOut = 0;
        for (int index = 1; index < parts.size(); index++) {
            if (parts.get(index).parts() > parts.get(largest).parts()) {
                largest = index;
            }
        }
        for (int index = 0; index < parts.size(); index++) {
            FluidParts part = parts.get(index);
            if (index == largest) {
                continue;
            }
            int share = (int) ((long) amount * part.parts() / total);
            handedOut += share;
            if (share > 0) {
                sink.accept(part.fluid(), share);
            }
        }
        FluidParts biggest = parts.get(largest);
        int rest = amount - handedOut;
        if (rest > 0) {
            sink.accept(biggest.fluid(), rest);
        }
    }

    public boolean isEmpty() {
        return parts.isEmpty();
    }

    private static int gcd(int a, int b) {
        while (b != 0) {
            int step = a % b;
            a = b;
            b = step;
        }
        return a;
    }

    public record FluidParts(Fluid fluid, int parts) {

        public static final Codec<FluidParts> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                BuiltInRegistries.FLUID.byNameCodec().fieldOf("fluid").forGetter(FluidParts::fluid),
                Codec.INT.fieldOf("parts").forGetter(FluidParts::parts)
        ).apply(instance, FluidParts::new));

        public static final StreamCodec<RegistryFriendlyByteBuf, FluidParts> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.registry(Registries.FLUID), FluidParts::fluid,
                ByteBufCodecs.VAR_INT, FluidParts::parts,
                FluidParts::new);
    }
}
