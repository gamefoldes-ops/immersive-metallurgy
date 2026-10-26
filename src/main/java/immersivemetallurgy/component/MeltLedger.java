package immersivemetallurgy.component;

import io.netty.buffer.ByteBuf;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.level.material.Fluid;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.ArrayList;
import java.util.List;

/**
 * What the fluids inside one container were made of. The alloy mixin keeps that table beside a container's
 * alloy, and a container that saves itself as an entity gets it back through the alloy's own NBT; a crucible
 * that travels as a block item does not, because TFC writes that path through {@code FluidAlloy}'s codec,
 * which has no room for it. So the table leaves the block item as this component and comes back into the alloy
 * when the item is placed again.
 */
public record MeltLedger(List<Entry> entries) {
    public static final Codec<MeltLedger> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Entry.CODEC.listOf().fieldOf("entries").forGetter(MeltLedger::entries)
    ).apply(instance, MeltLedger::new));

    public static final StreamCodec<ByteBuf, MeltLedger> STREAM_CODEC = ByteBufCodecs.fromCodec(CODEC);

    public MeltLedger {
        entries = List.copyOf(entries);
    }

    public static MeltLedger of(Object2ObjectMap<Fluid, AlloyRecord> compositions) {
        List<Entry> entries = new ArrayList<>();
        for (Object2ObjectMap.Entry<Fluid, AlloyRecord> entry : compositions.object2ObjectEntrySet()) {
            entries.add(new Entry(entry.getKey(), entry.getValue()));
        }
        return entries.isEmpty() ? null : new MeltLedger(entries);
    }

    public void applyTo(Object2ObjectMap<Fluid, AlloyRecord> compositions) {
        for (Entry entry : entries) {
            compositions.put(entry.fluid(), entry.record());
        }
    }

    public record Entry(Fluid fluid, AlloyRecord record) {

        public static final Codec<Entry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                BuiltInRegistries.FLUID.byNameCodec().fieldOf("fluid").forGetter(Entry::fluid),
                AlloyRecord.CODEC.fieldOf("record").forGetter(Entry::record)
        ).apply(instance, Entry::new));
    }
}
