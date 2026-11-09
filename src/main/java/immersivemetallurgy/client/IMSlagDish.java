package immersivemetallurgy.client;

import immersivemetallurgy.furnace.SlagDish;

import com.mojang.blaze3d.vertex.PoseStack;
import net.dries007.tfc.client.RenderHelpers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

/**
 * What the slag dish shows of itself: metal on the bottom, dirt floating on it.
 *
 * The furnace fills this bowl from two sources at different times — the overflow of the bath runs over the dam
 * whenever the hearth is over-full, slag only once the boil is done with it — and both of them are in it at once.
 * TFC draws what is in the tank, which is the slag; the metal beside it is a count and a fluid on the stack, so
 * its surface is drawn here, and the slag's is lifted to stand above it. A dish with both in it reads as a dish
 * with both in it, which is the whole point: the player sees the heat come over before they see the dirt.
 */
public final class IMSlagDish {
    /** What the dish holds, in millibuckets, and the metal as the fluid it is. */
    public record Contents(int capacityMb, int metalMb, int slagMb, @Nullable FluidStack metal) {
        public boolean hasMetal() {
            return metal != null && metalMb > 0;
        }
    }

    private IMSlagDish() {
    }

    /** The dish a table is holding, or null for any other vessel — which is what TFC's own drawing then paints. */
    @Nullable
    public static Contents read(ItemStack vessel) {
        SlagDish dish = SlagDish.of(vessel);
        if (dish == null) {
            return null;
        }
        return new Contents(dish.capacityMb(), dish.metalMb(), dish.slagMb(), dish.metal());
    }

    /**
     * The height TFC would have picked for a fill of this size: its own `1 + 0.95 × fill`, over sixteen, so the
     * surface of the dish lines up with the surface of every mould in the world rather than with a number of ours.
     */
    public static float level(int mb, int capacityMb) {
        return (1F + 0.95F * ((float) mb / capacityMb)) / 16F;
    }

    /** Where the slag's surface goes: above the metal standing under it, not where the tank alone would put it. */
    public static float slagLevel(Contents dish) {
        return level(dish.metalMb() + dish.slagMb(), dish.capacityMb());
    }

    /** The metal's own surface, drawn under the slag's. */
    public static void drawMetal(PoseStack pose, MultiBufferSource buffers, Contents dish, int light, int overlay) {
        RenderHelpers.renderFluidFace(pose, dish.metal(), buffers, 2F / 16F, 2F / 16F, 14F / 16F, 14F / 16F,
                level(dish.metalMb(), dish.capacityMb()), overlay, light);
    }
}
