package immersivemetallurgy.recipe;

import immersivemetallurgy.component.MeltCarries;
import immersivemetallurgy.registry.IMItemModifiers;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.dries007.tfc.common.recipes.outputs.ItemStackModifier;
import net.dries007.tfc.common.recipes.outputs.ItemStackModifierType;
import com.mojang.serialization.MapCodec;

/**
 * The output of a recipe takes the composition and defects of the metal it was made from, the same way
 * TFC's own {@code copy_forging_bonus} passes on a forging bonus. It has to happen here rather than in a
 * crafting event, because the event hands over a copy and the components would never reach the player.
 *
 * <p>Deliberately does <em>not</em> override {@code dependsOnInput()}, which defaults to false: TFC's advanced
 * shaped and shapeless recipes answer {@code isSpecial()} with it, and a special recipe is hidden from JEI and
 * from the recipe book. What this copies is a component of the same item - exactly what copy_forging_bonus
 * does, and that one is not special either. Claiming otherwise cost every metal tool its displayed craft.
 */
public final class CopyMeltModifier implements ItemStackModifier {
    public static final CopyMeltModifier INSTANCE = new CopyMeltModifier();

    public static final MapCodec<CopyMeltModifier> CODEC = MapCodec.unit(INSTANCE);
    public static final StreamCodec<RegistryFriendlyByteBuf, CopyMeltModifier> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public ItemStack apply(ItemStack output, ItemStack input, Context context) {
        MeltCarries.copyInto(input, output);
        return output;
    }

    @Override
    public ItemStackModifierType<?> type() {
        return IMItemModifiers.COPY_MELT.get();
    }
}
