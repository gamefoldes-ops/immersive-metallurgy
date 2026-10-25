package com.gamefoldes.immersivemetallurgy.mixin;

import com.gamefoldes.immersivemetallurgy.registry.IMDataComponents;

import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * What a melt was made of and how flawed it is are per-casting information, and 1.21.1 has no way to tell a
 * component "do not block stacking", so without this every ingot cast from a slightly different ratio becomes
 * its own pile. Letting them merge is honest here rather than a loss: a stack renders one colour, has one
 * durability ceiling and one edge, so whatever it was made of, one casting had to win - and it is the one
 * the stack already held.
 */
@Mixin(ItemStack.class)
public class ItemStackMeltStackingMixin {
    @Inject(method = "isSameItemSameComponents(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;)Z",
            at = @At("TAIL"), cancellable = true)
    private static void immersiveMetallurgy$allowMeltStacking(ItemStack one, ItemStack two, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue()) {
            return;
        }
        Set<DataComponentType<?>> meltData = Set.of(IMDataComponents.ALLOY_RECORD.get(),
                IMDataComponents.DEFECT_PROFILE.get(), IMDataComponents.SHARPNESS.get());
        if (immersiveMetallurgy$equalIgnoring(one, two, meltData)) {
            cir.setReturnValue(true);
        }
    }

    @Unique
    private static boolean immersiveMetallurgy$equalIgnoring(ItemStack one, ItemStack two, Set<DataComponentType<?>> ignored) {
        if (!one.is(two.getItem())) {
            return false;
        }
        DataComponentPatch first = one.getComponentsPatch();
        DataComponentPatch second = two.getComponentsPatch();
        Set<DataComponentType<?>> compared = new HashSet<>();
        for (var entry : first.entrySet()) {
            DataComponentType<?> type = entry.getKey();
            if (ignored.contains(type)) {
                continue;
            }
            compared.add(type);
            if (!Objects.equals(entry.getValue(), second.get(type))) {
                return false;
            }
        }
        for (var entry : second.entrySet()) {
            if (!ignored.contains(entry.getKey()) && !compared.contains(entry.getKey())) {
                return false;
            }
        }
        return true;
    }
}
