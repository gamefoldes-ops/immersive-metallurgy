package immersivemetallurgy.component;

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
import java.util.Comparator;
import java.util.List;

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
        List<FluidParts> ordered = new ArrayList<>(parts);
        ordered.sort(Comparator.comparingInt(FluidParts::parts).reversed());
        parts = List.copyOf(ordered);
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
     * Splits an amount of metal back over the fluids it was made of, keeping the ratio exact and letting
     * the caller sum repeated pours.
     */
    public void addTo(double amount, Object2DoubleMap<Fluid> sink) {
        int total = totalParts();
        if (total <= 0) {
            return;
        }
        for (FluidParts part : parts) {
            sink.mergeDouble(part.fluid(), amount * part.parts() / total, Double::sum);
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
