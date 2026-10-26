package immersivemetallurgy.mixin;

import immersivemetallurgy.component.MeltCarries;
import immersivemetallurgy.registry.IMDataComponents;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.item.crafting.ShapedRecipe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

/**
 * Vanilla crafting has no output modifiers, so anything assembled from flawed metal parts in a crafting
 * table would come out clean: the anvil and the weld already carry the melt forward through their own
 * recipes and mixins, and this closes the same hole for TFC's sheet-and-rod objects - tuyeres, bars,
 * buckets, shields, trapdoors, armour plates, grates, anvils and lamps.
 *
 * Only objects that are themselves metal take the pour's identity. A barrel built with a bad nail is a
 * barrel, not a flawed one, and an unguarded copy would have said otherwise.
 */
@Mixin({ShapedRecipe.class, ShapelessRecipe.class})
public class VanillaCraftingMeltMixin {
    @Inject(method = "assemble(Lnet/minecraft/world/item/crafting/CraftingInput;Lnet/minecraft/core/HolderLookup$Provider;)Lnet/minecraft/world/item/ItemStack;",
            at = @At("TAIL"))
    private void immersiveMetallurgy$carryMelt(CraftingInput input, HolderLookup.Provider registries, CallbackInfoReturnable<ItemStack> cir) {
        ItemStack result = cir.getReturnValue();
        if (!isMetal(result.getItem())) {            return;
        }
        List<ItemStack> parts = new ArrayList<>(input.size());
        boolean remembers = false;
        for (int slot = 0; slot < input.size(); slot++) {
            ItemStack part = input.getItem(slot);
            parts.add(part);
            remembers |= part.has(IMDataComponents.ALLOY_RECORD.get()) || part.has(IMDataComponents.DEFECT_PROFILE.get());
        }
        if (remembers) {
            MeltCarries.mergeWorstInto(parts, result);
        }
    }

    @Unique
    private static boolean isMetal(Item item) {
        return BuiltInRegistries.ITEM.getKey(item).getPath().startsWith("metal/");
    }
}
