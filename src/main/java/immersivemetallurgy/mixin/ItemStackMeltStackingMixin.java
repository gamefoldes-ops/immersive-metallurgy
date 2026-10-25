package immersivemetallurgy.mixin;

import immersivemetallurgy.component.DefectProfile;
import immersivemetallurgy.registry.IMDataComponents;

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
 * What a melt was made of is per-casting information, and 1.21.1 has no way to tell a component "do not block
 * stacking", so without this every ingot cast from a slightly different ratio becomes its own pile. A pile
 * can only share one verdict though, so the composition and the edge are ignored while the defect has to
 * agree on its {@code grade}: light, medium and heavy flawed metal stay in separate stacks, and castings that
 * land in the same band merge instead of littering the inventory one pile per pour.
 */
@Mixin(ItemStack.class)
public class ItemStackMeltStackingMixin {
    @Inject(method = "isSameItemSameComponents(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;)Z",
            at = @At("TAIL"), cancellable = true)
    private static void immersiveMetallurgy$allowMeltStacking(ItemStack one, ItemStack two, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue()) {
            return;
        }
        Set<DataComponentType<?>> perCasting = Set.of(IMDataComponents.ALLOY_RECORD.get(),
                IMDataComponents.DEFECT_PROFILE.get(), IMDataComponents.SHARPNESS.get());
        if (immersiveMetallurgy$equalIgnoring(one, two, perCasting) && gradeOf(one) == gradeOf(two)) {
            cir.setReturnValue(true);
        }
    }

    /** A stack that never went through a bad melt has no profile at all, and is not the same verdict as a lightly flawed one. */
    @Unique
    private static int gradeOf(ItemStack stack) {
        DefectProfile defect = stack.get(IMDataComponents.DEFECT_PROFILE.get());
        return defect == null ? -1 : defect.grade();
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
