package immersivemetallurgy.mixin;

import immersivemetallurgy.ImmersiveMetallurgy;
import immersivemetallurgy.registry.IMAlloys;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.dries007.tfc.common.items.TFCItems;
import net.dries007.tfc.util.Metal;
import net.dries007.tfc.util.MetalItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.HashMap;
import java.util.Map;

/**
 * TFC works out what metal an item is by reading the last segment of its registry name, so every one of our
 * ingots was inferred to be the metal "flawed" - which is what the startup log complained about, and which
 * is also what an ingot pile groups its entries by. Our ingots are one metal cast badly, not a metal of their
 * own, so they answer as their metal's clean item does: a pile then holds clean and flawed bronze together,
 * and the stacks themselves keep their own identity and defect.
 */
@Mixin(MetalItem.class)
public class MetalItemFlawedIngotMixin {
    @Unique
    private static Map<Item, MetalItem> immersiveMetallurgy$asClean = Map.of();

    @Inject(method = "getOrUnknown(Lnet/minecraft/world/item/ItemStack;)Lnet/dries007/tfc/util/MetalItem;",
            at = @At("HEAD"), cancellable = true)
    private static void immersiveMetallurgy$answerAsTheirMetal(ItemStack stack, CallbackInfoReturnable<MetalItem> cir) {
        if (immersiveMetallurgy$asClean.isEmpty()) {
            immersiveMetallurgy$asClean = immersiveMetallurgy$build();
        }
        MetalItem metalItem = immersiveMetallurgy$asClean.get(stack.getItem());
        if (metalItem != null) {
            cir.setReturnValue(metalItem);
        }
    }

    @Unique
    private static Map<Item, MetalItem> immersiveMetallurgy$build() {
        Map<Item, MetalItem> byItem = new HashMap<>();
        for (IMAlloys alloy : IMAlloys.ALL) {
            for (Metal.ItemType type : new Metal.ItemType[]{Metal.ItemType.INGOT, Metal.ItemType.DOUBLE_INGOT}) {
                if (type == Metal.ItemType.DOUBLE_INGOT && !alloy.doubleIngot()) {
                    continue;
                }
                Item clean = cleanItem(alloy.key(), type);
                if (clean == null) {
                    continue;
                }
                Item flawed = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(ImmersiveMetallurgy.MODID,
                        type == Metal.ItemType.INGOT ? alloy.flawedIngotPath() : alloy.flawedDoubleIngotPath()));
                if (flawed != Items.AIR) {
                    byItem.put(flawed, MetalItem.getOrUnknown(new ItemStack(clean)));
                }
            }
        }
        return byItem;
    }

    @Unique
    private static Item cleanItem(String key, Metal.ItemType type) {
        for (Metal metal : Metal.values()) {
            if (!metal.getSerializedName().equals(key)) {
                continue;
            }
            Map<Metal.ItemType, TFCItems.ItemId> parts = TFCItems.METAL_ITEMS.get(metal);
            TFCItems.ItemId id = parts == null ? null : parts.get(type);
            return id == null ? null : id.asItem();
        }
        return null;
    }
}
